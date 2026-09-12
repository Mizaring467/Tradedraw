package com.example.tradedraw

import android.util.Log

/**
 * Representa una vela de 1 minuto construida matemáticamente a partir del stream continuo de ticks del WebSocket.
 */
data class SyntheticCandle(
    val openTimeMs: Long,
    val open: Double,
    var high: Double,
    var low: Double,
    var close: Double,
    var isClosed: Boolean = false
) {
    val range: Double get() = (high - low).coerceAtLeast(0.00001)
    val body: Double get() = Math.abs(close - open)
    val isGreen: Boolean get() = close >= open
    val isRed: Boolean get() = close < open
    val upperWick: Double get() = if (isGreen) (high - close).coerceAtLeast(0.0) else (high - open).coerceAtLeast(0.0)
    val lowerWick: Double get() = if (isGreen) (open - low).coerceAtLeast(0.0) else (close - low).coerceAtLeast(0.0)
    val upperWickRatio: Float get() = (upperWick / range).toFloat()
    val lowerWickRatio: Float get() = (lowerWick / range).toFloat()
    val bodyRatio: Float get() = (body / range).toFloat()
}

/**
 * Motor cuantitativo Headless: procesa ticks de WebSocket, construye velas de 1m, calcula
 * soportes/resistencias y evalúa estrategias de trading sin requerir captura de pantalla.
 */
class SyntheticCandleEngine {

    private val TAG = "SyntheticCandleEngine"

    private var currentCandle: SyntheticCandle? = null
    val closedCandles = ArrayList<SyntheticCandle>(64)

    var dynamicSupportPrice: Double = 0.0
        private set
    var dynamicResistancePrice: Double = 0.0
        private set
    var detectedTrend: TrendDirection = TrendDirection.SIDEWAYS
        private set

    var onSignalGenerated: ((TradeAction, String) -> Unit)? = null

    /**
     * Procesa cada micro-tick entrante desde el WebSocket.
     */
    fun onNewTick(tick: MarketTick) {
        val candleMinute = (tick.timestampMs / 60000L) * 60000L
        val active = currentCandle

        if (active == null || active.openTimeMs != candleMinute) {
            // Cierre de la vela anterior si existía
            active?.let { prev ->
                prev.isClosed = true
                synchronized(closedCandles) {
                    if (closedCandles.size >= 60) {
                        closedCandles.removeAt(0)
                    }
                    closedCandles.add(prev)
                }
                recalculateSupportResistance()
                recalculateTrend()
            }

            // Inicio de nueva vela de 1 minuto
            currentCandle = SyntheticCandle(
                openTimeMs = candleMinute,
                open = tick.price,
                high = tick.price,
                low = tick.price,
                close = tick.price
            )
        } else {
            // Actualizar extremos de la vela en formación
            if (tick.price > active.high) active.high = tick.price
            if (tick.price < active.low) active.low = tick.price
            active.close = tick.price
        }

        // Evaluar señales cuantitativas en la ventana sniper (:59s - :01s)
        evaluateSniperOpportunity(tick)
    }

    private fun recalculateSupportResistance() {
        synchronized(closedCandles) {
            if (closedCandles.size < 3) return
            val sample = closedCandles.takeLast(30)
            dynamicResistancePrice = sample.maxOf { it.high }
            dynamicSupportPrice = sample.minOf { it.low }
        }
    }

    private fun recalculateTrend() {
        synchronized(closedCandles) {
            if (closedCandles.size < 5) {
                detectedTrend = TrendDirection.SIDEWAYS
                return
            }
            val last5 = closedCandles.takeLast(5)
            val closes = last5.map { it.close }
            val isAscending = closes.zipWithNext().all { it.first <= it.second }
            val isDescending = closes.zipWithNext().all { it.first >= it.second }

            detectedTrend = when {
                isAscending -> TrendDirection.UPTREND
                isDescending -> TrendDirection.DOWNTREND
                else -> TrendDirection.SIDEWAYS
            }
        }
    }

    private fun evaluateSniperOpportunity(tick: MarketTick) {
        val sec = ((tick.timestampMs / 1000L) % 60L).toInt()
        val isSniperWindow = sec == 59 || sec == 0 || sec == 1
        if (!isSniperWindow) return

        val srRange = (dynamicResistancePrice - dynamicSupportPrice).coerceAtLeast(0.0001)
        val distToSupport = ((tick.price - dynamicSupportPrice) / srRange).toFloat().coerceIn(0f, 1f)
        val distToResistance = ((dynamicResistancePrice - tick.price) / srRange).toFloat().coerceIn(0f, 1f)

        synchronized(closedCandles) {
            if (closedCandles.size < 3) return
            val prev = closedCandles.last()

            // Estrategia 1: MT_REJECTION (Rechazo de mecha contra S/R con confirmación de velocidad)
            if (distToSupport <= 0.15f && prev.lowerWickRatio >= 0.35f && !tick.isBearishImpulse) {
                onSignalGenerated?.invoke(TradeAction.BUY, "Rechazo alcista en Soporte (${(distToSupport * 100).toInt()}% dist) + Mecha ${ (prev.lowerWickRatio * 100).toInt()}%")
                return
            }

            if (distToResistance <= 0.15f && prev.upperWickRatio >= 0.35f && !tick.isBullishImpulse) {
                onSignalGenerated?.invoke(TradeAction.SELL, "Rechazo bajista en Resistencia (${(distToResistance * 100).toInt()}% dist) + Mecha ${ (prev.upperWickRatio * 100).toInt()}%")
                return
            }

            // Estrategia 2: MT_3_VELAS_AGOTAMIENTO (3 velas del mismo color reduciendo cuerpo)
            if (closedCandles.size >= 3) {
                val c1 = closedCandles[closedCandles.size - 3]
                val c2 = closedCandles[closedCandles.size - 2]
                val c3 = closedCandles[closedCandles.size - 1]

                val is3RedExhaustion = c1.isRed && c2.isRed && c3.isRed &&
                        (c1.body >= c2.body && c2.body >= c3.body) && distToSupport <= 0.25f

                if (is3RedExhaustion && !tick.isBearishImpulse) {
                    onSignalGenerated?.invoke(TradeAction.BUY, "Agotamiento 3 Velas Rojas sobre Soporte -> Reversión CALL")
                    return
                }

                val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                        (c1.body >= c2.body && c2.body >= c3.body) && distToResistance <= 0.25f

                if (is3GreenExhaustion && !tick.isBullishImpulse) {
                    onSignalGenerated?.invoke(TradeAction.SELL, "Agotamiento 3 Velas Verdes sobre Resistencia -> Reversión PUT")
                    return
                }
            }
        }
    }
}
