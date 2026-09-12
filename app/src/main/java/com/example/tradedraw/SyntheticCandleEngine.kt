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
    private val recentTickPrices = ArrayDeque<Double>(120)

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

        // Registrar tick en buffer rodante para análisis instantáneo de momentum y medias
        synchronized(recentTickPrices) {
            if (recentTickPrices.size >= 120) {
                recentTickPrices.removeFirst()
            }
            recentTickPrices.addLast(tick.price)
        }

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

        // Recalcular S/R y tendencia en cada tick para respuesta inmediata en HUD y estrategia
        recalculateSupportResistance(tick)
        recalculateTrend(tick)

        // Evaluar señales cuantitativas en la ventana sniper (:59s - :01s)
        evaluateSniperOpportunity(tick)
    }

    private fun recalculateSupportResistance(tick: MarketTick? = null) {
        synchronized(closedCandles) {
            val allHighs = mutableListOf<Double>()
            val allLows = mutableListOf<Double>()

            // Incluir velas cerradas recientes
            val sample = closedCandles.takeLast(30)
            sample.forEach {
                allHighs.add(it.high)
                allLows.add(it.low)
            }

            // Incluir vela activa
            currentCandle?.let {
                allHighs.add(it.high)
                allLows.add(it.low)
            }

            // Incluir buffer de ticks recientes si hay pocas velas
            if (allHighs.isEmpty() && tick != null && tick.price > 0.0) {
                synchronized(recentTickPrices) {
                    if (recentTickPrices.isNotEmpty()) {
                        allHighs.add(recentTickPrices.maxOrNull() ?: tick.price)
                        allLows.add(recentTickPrices.minOrNull() ?: tick.price)
                    } else {
                        allHighs.add(tick.price)
                        allLows.add(tick.price)
                    }
                }
            }

            if (allHighs.isNotEmpty() && allLows.isNotEmpty()) {
                var maxHigh = allHighs.maxOrNull() ?: 0.0
                var minLow = allLows.minOrNull() ?: 0.0

                // Si el rango es demasiado estrecho o idéntico, expandir levemente para evitar S == R
                if (maxHigh <= minLow || (maxHigh - minLow) < (minLow * 0.0001)) {
                    val base = if (minLow > 0.0) minLow else (tick?.price ?: 1.0)
                    maxHigh = base * 1.0004
                    minLow = base * 0.9996
                }

                dynamicResistancePrice = maxHigh
                dynamicSupportPrice = minLow
            }
        }
    }

    private fun recalculateTrend(tick: MarketTick? = null) {
        synchronized(closedCandles) {
            val allCandles = mutableListOf<SyntheticCandle>()
            allCandles.addAll(closedCandles.takeLast(6))
            currentCandle?.let { allCandles.add(it) }

            if (allCandles.size >= 2) {
                val greenCount = allCandles.count { it.isGreen }
                val redCount = allCandles.count { it.isRed }
                val firstOpen = allCandles.first().open
                val latestClose = allCandles.last().close
                val netDiff = latestClose - firstOpen

                // Rango relativo de movimiento
                val refPrice = if (firstOpen > 0.0) firstOpen else 1.0
                val pctChange = netDiff / refPrice

                when {
                    // Tendencia Alcista Clara: Mayoría verdes o avance neto sustancial
                    greenCount > redCount && pctChange > -0.00005 -> {
                        detectedTrend = TrendDirection.UPTREND
                        return
                    }
                    pctChange > 0.0001 -> {
                        detectedTrend = TrendDirection.UPTREND
                        return
                    }
                    // Tendencia Bajista Clara: Mayoría rojas o retroceso neto sustancial
                    redCount > greenCount && pctChange < 0.00005 -> {
                        detectedTrend = TrendDirection.DOWNTREND
                        return
                    }
                    pctChange < -0.0001 -> {
                        detectedTrend = TrendDirection.DOWNTREND
                        return
                    }
                }
            }

            // Análisis por media móvil de ticks recientes
            synchronized(recentTickPrices) {
                if (recentTickPrices.size >= 10) {
                    val prices = recentTickPrices.toList()
                    val fastSample = prices.takeLast(prices.size / 3)
                    val slowSample = prices.take(prices.size / 3)

                    val fastAvg = fastSample.average()
                    val slowAvg = slowSample.average()
                    val diff = fastAvg - slowAvg
                    val base = if (slowAvg > 0.0) slowAvg else 1.0
                    val relDiff = diff / base

                    detectedTrend = when {
                        relDiff > 0.00003 -> TrendDirection.UPTREND
                        relDiff < -0.00003 -> TrendDirection.DOWNTREND
                        else -> {
                            val active = currentCandle
                            if (active != null && active.range > 0.00001) {
                                if (active.isGreen && active.bodyRatio > 0.35f) TrendDirection.UPTREND
                                else if (active.isRed && active.bodyRatio > 0.35f) TrendDirection.DOWNTREND
                                else TrendDirection.SIDEWAYS
                            } else {
                                TrendDirection.SIDEWAYS
                            }
                        }
                    }
                    return
                }
            }

            // Fallback por estado instantáneo del micro-tick
            val current = currentCandle
            detectedTrend = when {
                current != null && current.isGreen && current.close > current.open -> TrendDirection.UPTREND
                current != null && current.isRed && current.close < current.open -> TrendDirection.DOWNTREND
                tick?.isBullishImpulse == true -> TrendDirection.UPTREND
                tick?.isBearishImpulse == true -> TrendDirection.DOWNTREND
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
            // Arranque inmediato sin warmup: si hay menos de 3 velas cerradas, operar por micro-impulso instantáneo o flujo de vela
            if (closedCandles.size < 3) {
                val activeCandle = currentCandle
                when {
                    tick.isBullishImpulse || detectedTrend == TrendDirection.UPTREND -> {
                        onSignalGenerated?.invoke(TradeAction.BUY, "🚀 Sniper Headless [Flujo Alcista / Ticks | ⏱ ${sec}s] -> CALL")
                        return
                    }
                    tick.isBearishImpulse || detectedTrend == TrendDirection.DOWNTREND -> {
                        onSignalGenerated?.invoke(TradeAction.SELL, "🚀 Sniper Headless [Flujo Bajista / Ticks | ⏱ ${sec}s] -> PUT")
                        return
                    }
                    activeCandle != null -> {
                        if (activeCandle.close >= activeCandle.open) {
                            onSignalGenerated?.invoke(TradeAction.BUY, "🚀 Sniper Headless [Flujo Vela Actual Verde | ⏱ ${sec}s] -> CALL")
                        } else {
                            onSignalGenerated?.invoke(TradeAction.SELL, "🚀 Sniper Headless [Flujo Vela Actual Roja | ⏱ ${sec}s] -> PUT")
                        }
                        return
                    }
                }
                return
            }
            val prev = closedCandles.last()

            // Estrategia 1: MT_REJECTION (Rechazo de mecha contra S/R con confirmación de velocidad)
            if (distToSupport <= 0.20f && prev.lowerWickRatio >= 0.30f && !tick.isBearishImpulse) {
                onSignalGenerated?.invoke(TradeAction.BUY, "Rechazo alcista en Soporte (${(distToSupport * 100).toInt()}% dist) + Mecha ${(prev.lowerWickRatio * 100).toInt()}% -> CALL")
                return
            }

            if (distToResistance <= 0.20f && prev.upperWickRatio >= 0.30f && !tick.isBullishImpulse) {
                onSignalGenerated?.invoke(TradeAction.SELL, "Rechazo bajista en Resistencia (${(distToResistance * 100).toInt()}% dist) + Mecha ${(prev.upperWickRatio * 100).toInt()}% -> PUT")
                return
            }

            // Estrategia 2: MT_3_VELAS_AGOTAMIENTO (3 velas del mismo color reduciendo cuerpo)
            if (closedCandles.size >= 3) {
                val c1 = closedCandles[closedCandles.size - 3]
                val c2 = closedCandles[closedCandles.size - 2]
                val c3 = closedCandles[closedCandles.size - 1]

                val is3RedExhaustion = c1.isRed && c2.isRed && c3.isRed &&
                        (c1.body >= c2.body && c2.body >= c3.body) && distToSupport <= 0.30f

                if (is3RedExhaustion && !tick.isBearishImpulse) {
                    onSignalGenerated?.invoke(TradeAction.BUY, "Agotamiento 3 Velas Rojas sobre Soporte -> Reversión CALL")
                    return
                }

                val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                        (c1.body >= c2.body && c2.body >= c3.body) && distToResistance <= 0.30f

                if (is3GreenExhaustion && !tick.isBullishImpulse) {
                    onSignalGenerated?.invoke(TradeAction.SELL, "Agotamiento 3 Velas Verdes sobre Resistencia -> Reversión PUT")
                    return
                }
            }

            // Estrategia 3: Continuación de Tendencia Pura
            if (detectedTrend == TrendDirection.UPTREND && !tick.isBearishImpulse) {
                onSignalGenerated?.invoke(TradeAction.BUY, "🚀 Continuación de Tendencia Alcista [WS Sniper | ⏱ ${sec}s] -> CALL")
                return
            } else if (detectedTrend == TrendDirection.DOWNTREND && !tick.isBullishImpulse) {
                onSignalGenerated?.invoke(TradeAction.SELL, "🚀 Continuación de Tendencia Bajista [WS Sniper | ⏱ ${sec}s] -> PUT")
                return
            }
        }
    }
}

