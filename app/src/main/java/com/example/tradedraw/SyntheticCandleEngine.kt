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
enum class LevelType { SUPPORT, RESISTANCE, BOTH }
data class PriceLevel(var price: Double, var touches: Int, var type: LevelType)

class SyntheticCandleEngine {

    private val TAG = "SyntheticCandleEngine"

    private var currentCandle: SyntheticCandle? = null
    val closedCandles = ArrayList<SyntheticCandle>(64)
    private val recentTickPrices = ArrayDeque<Double>(120)
    private var lastTickPrice: Double = 0.0

    // Conteo de ticks sin retroceso
    var consecutiveUpTicks: Int = 0
        private set
    var consecutiveDownTicks: Int = 0
        private set
    var lastCompletedUpStreak: Int = 0
        private set
    var lastCompletedDownStreak: Int = 0
        private set

    // RSI sintético de ticks (0.0 a 100.0)
    var syntheticTickRsi: Double = 50.0
        private set

    // Proximidad a Soporte/Resistencia dinámicos independientes (normalizados por ATR, 0.0 = en el nivel)
    var distanceToResistanceRatio: Float = 0.5f
        private set
    var distanceToSupportRatio: Float = 0.5f
        private set

    // Flags cuantitativos de sobreextensión
    var isBullishOverextended: Boolean = false
        private set
    var isBearishOverextended: Boolean = false
        private set

    var dynamicSupportPrice: Double = 0.0
        private set
    var dynamicResistancePrice: Double = 0.0
        private set
    var detectedTrend: TrendDirection = TrendDirection.SIDEWAYS
        private set

    var visionTrend: TrendDirection? = null
        private set

    fun updateVisionTrend(trend: TrendDirection) {
        visionTrend = trend
    }

    var subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE

    var onSignalGenerated: ((TradeAction, String) -> Unit)? = null

    /**
     * Procesa cada micro-tick entrante desde el WebSocket.
     */
    fun onNewTick(tick: MarketTick) {
        val candleMinute = (tick.timestampMs / 60000L) * 60000L
        val active = currentCandle

        // 1. Conteo de ticks sin retroceso (momentum instantáneo)
        if (lastTickPrice > 0.0) {
            val delta = tick.price - lastTickPrice
            val epsilon = lastTickPrice * 0.0000005
            if (delta > epsilon) {
                if (consecutiveDownTicks > 0) {
                    lastCompletedDownStreak = consecutiveDownTicks
                }
                consecutiveUpTicks++
                consecutiveDownTicks = 0
            } else if (delta < -epsilon) {
                if (consecutiveUpTicks > 0) {
                    lastCompletedUpStreak = consecutiveUpTicks
                }
                consecutiveDownTicks++
                consecutiveUpTicks = 0
            }
        }
        lastTickPrice = tick.price

        // 2. Registrar tick en buffer rodante para análisis instantáneo de momentum y medias
        synchronized(recentTickPrices) {
            if (recentTickPrices.size >= 120) {
                recentTickPrices.removeFirst()
            }
            recentTickPrices.addLast(tick.price)
        }

        // 3. Calcular RSI sintético de ticks (período de 14 ticks)
        syntheticTickRsi = calculateTickRsi(14)

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

        // 4. Recalcular S/R dinámico y ratios de distancia independientes
        recalculateSupportResistance(tick)

        // 5. Evaluar estado cuantitativo de sobreextensión
        updateOverextensionStatus(tick)

        // 6. Recalcular tendencia en cada tick para respuesta inmediata en HUD y estrategia
        recalculateTrend()

        // 7. Evaluar señales cuantitativas en la ventana sniper (:57s - :05s)
        evaluateSniperOpportunity(tick)
    }

    /**
     * Calcula el RSI sintético de ticks sobre la ventana rodante de ticks.
     * Rango de salida: 0.0 a 100.0.
     */
    fun calculateTickRsi(period: Int = 14): Double {
        synchronized(recentTickPrices) {
            val available = recentTickPrices.size - 1
            if (available < 3) return 50.0
            val n = Math.min(period, available)
            val prices = recentTickPrices.takeLast(n + 1)
            var gains = 0.0
            var losses = 0.0
            for (i in 1 until prices.size) {
                val diff = prices[i] - prices[i - 1]
                if (diff > 0.0) {
                    gains += diff
                } else if (diff < 0.0) {
                    losses += -diff
                }
            }
            if (losses == 0.0 && gains == 0.0) return 50.0
            if (losses == 0.0) return 100.0
            if (gains == 0.0) return 0.0
            val avgGain = gains / n
            val avgLoss = losses / n
            val rs = avgGain / avgLoss
            return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
        }
    }

    /**
     * Calcula el rango porcentual entre el máximo y mínimo de las últimas velas.
     */
    fun getRecentCandlesRangePercent(sampleCount: Int = 5): Double {
        synchronized(closedCandles) {
            val sample = mutableListOf<SyntheticCandle>()
            sample.addAll(closedCandles.takeLast(sampleCount))
            currentCandle?.let { sample.add(it) }
            if (sample.isEmpty()) return 0.0
            val maxHigh = sample.maxOf { it.high }
            val minLow = sample.minOf { it.low }
            val refPrice = sample.first().open
            if (refPrice <= 0.0) return 0.0
            return ((maxHigh - minLow) / refPrice) * 100.0
        }
    }

    /**
     * Evalúa si las últimas velas están dentro de un micro-rango (< 0.05%).
     */
    fun isMicroRange(sampleCount: Int = 5, maxRangePercent: Double = MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT): Boolean {
        val rangePct = getRecentCandlesRangePercent(sampleCount)
        return rangePct in 0.0000001..maxRangePercent
    }

    /**
     * Evalúa si los ticks recientes presentan alternancia sin dirección clara.
     */
    fun isTickAlternatingWithoutDirection(sampleSize: Int = 10): Boolean {
        synchronized(recentTickPrices) {
            if (recentTickPrices.size < 4) return false
            val prices = recentTickPrices.takeLast(sampleSize).toList()
            return MarketTickFilters.isTickAlternatingWithoutDirection(prices)
        }
    }

    /**
     * Filtro Anti-Choppy / Micro-Rango Cuantitativo:
     * Detecta micro-rango (< 0.05%) o caída brusca de volatilidad relativa con alternancia de ticks sin dirección.
     * Aplica SIEMPRE para proteger el capital en condiciones de ruido estático.
     */
    fun isChoppinessDetected(): Boolean {
        synchronized(closedCandles) {
            if (closedCandles.size < 4) return false
            val isStaticMicro = isMicroRange(5, MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT)
            val avgRange = closedCandles.takeLast(20).map { it.range }.average()
            val recent5Max = closedCandles.takeLast(5).maxOf { it.high }
            val recent5Min = closedCandles.takeLast(5).minOf { it.low }
            val recentRange = recent5Max - recent5Min
            val thresholdRatio = 0.40
            val isRelativeDrop = avgRange > 0.0 && recentRange < (avgRange * thresholdRatio)
            val alternatingTicks = 8
            return (isStaticMicro || isRelativeDrop) && isTickAlternatingWithoutDirection(alternatingTicks)
        }
    }

    private fun updateOverextensionStatus(tick: MarketTick) {
        val nearRes = distanceToResistanceRatio <= 0.22f || (dynamicResistancePrice > 0.0 && tick.price >= dynamicResistancePrice * 0.9998)
        val rsiOverbought = syntheticTickRsi >= 70.0
        val rsiExtremeOverbought = syntheticTickRsi >= 78.0
        val streakUp = consecutiveUpTicks >= 5 || (lastCompletedUpStreak >= 4 && consecutiveDownTicks <= 2)
        val extremeStreakUp = consecutiveUpTicks >= 8 || lastCompletedUpStreak >= 8

        isBullishOverextended = (nearRes && (rsiOverbought || streakUp)) ||
                (rsiExtremeOverbought && distanceToResistanceRatio <= 0.30f) ||
                extremeStreakUp

        val nearSup = distanceToSupportRatio <= 0.22f || (dynamicSupportPrice > 0.0 && tick.price <= dynamicSupportPrice * 1.0002)
        val rsiOversold = syntheticTickRsi <= 30.0
        val rsiExtremeOversold = syntheticTickRsi <= 22.0
        val streakDown = consecutiveDownTicks >= 5 || (lastCompletedDownStreak >= 4 && consecutiveUpTicks <= 2)
        val extremeStreakDown = consecutiveDownTicks >= 8 || lastCompletedDownStreak >= 8

        isBearishOverextended = (nearSup && (rsiOversold || streakDown)) ||
                (rsiExtremeOversold && distanceToSupportRatio <= 0.30f) ||
                extremeStreakDown
    }

    /**
     * Calcula el Average True Range (ATR) a partir de las velas cerradas disponibles.
     */
    fun calculateAtr(period: Int = 14): Double {
        synchronized(closedCandles) {
            if (closedCandles.isEmpty()) {
                currentCandle?.let { return it.range.coerceAtLeast(0.0001) }
                return 0.0001
            }
            val n = Math.min(period, closedCandles.size)
            val sample = closedCandles.takeLast(n)
            var sumTr = 0.0
            for (i in sample.indices) {
                val current = sample[i]
                val tr = if (i > 0) {
                    val prevClose = sample[i - 1].close
                    maxOf(
                        current.high - current.low,
                        Math.abs(current.high - prevClose),
                        Math.abs(current.low - prevClose)
                    )
                } else {
                    current.high - current.low
                }
                sumTr += tr
            }
            val avgTr = sumTr / n
            return avgTr.coerceAtLeast(0.0001)
        }
    }

    /**
     * Recalcula Soporte y Resistencia independientes basados en fractales/pivotes reales normalizados por ATR.
     * Elimina el acoplamiento artificial donde distToSupport + distToResistance == 1.0.
     */
    private fun recalculateSupportResistance(tick: MarketTick? = null) {
        synchronized(closedCandles) {
            val currentPrice = tick?.price ?: currentCandle?.close ?: lastTickPrice
            if (currentPrice <= 0.0) return

            val atr = calculateAtr(14)
            val candles = closedCandles.takeLast(40)

            val pivotHighs = mutableListOf<Double>()
            val pivotLows = mutableListOf<Double>()

            // Detección de fractales/pivotes locales (mínimo 3 velas: c[i-1], c[i], c[i+1])
            if (candles.size >= 3) {
                for (i in 1 until candles.size - 1) {
                    val prev = candles[i - 1]
                    val curr = candles[i]
                    val next = candles[i + 1]

                    if (curr.high >= prev.high && curr.high >= next.high) {
                        pivotHighs.add(curr.high)
                    }
                    if (curr.low <= prev.low && curr.low <= next.low) {
                        pivotLows.add(curr.low)
                    }
                }
            }

            // Incluir extremos de todas las velas cerradas
            candles.forEach { c ->
                pivotHighs.add(c.high)
                pivotLows.add(c.low)
            }

            if (pivotHighs.isEmpty() || pivotLows.isEmpty()) {
                synchronized(recentTickPrices) {
                    if (recentTickPrices.isNotEmpty()) {
                        pivotHighs.add(recentTickPrices.maxOrNull() ?: currentPrice)
                        pivotLows.add(recentTickPrices.minOrNull() ?: currentPrice)
                    } else {
                        pivotHighs.add(currentPrice + atr)
                        pivotLows.add(currentPrice - atr)
                    }
                }
            }

            // Resistencia independiente: pivote alto más cercano por encima del precio actual o máximo histórico relevante
            val resistancesAbove = pivotHighs.filter { it >= currentPrice }
            dynamicResistancePrice = resistancesAbove.minOrNull() ?: (pivotHighs.maxOrNull() ?: (currentPrice + atr))

            // Soporte independiente: pivote bajo más cercano por debajo del precio actual o mínimo histórico relevante
            val supportsBelow = pivotLows.filter { it <= currentPrice }
            dynamicSupportPrice = supportsBelow.maxOrNull() ?: (pivotLows.minOrNull() ?: (currentPrice - atr))

            // Normalización por ATR independiente:
            val atrNorm = (atr * 2.0).coerceAtLeast(0.0001)

            val rawDistToSupport = Math.abs(currentPrice - dynamicSupportPrice)
            val rawDistToResistance = Math.abs(currentPrice - dynamicResistancePrice)

            distanceToSupportRatio = (rawDistToSupport / atrNorm).toFloat().coerceIn(0f, 1f)
            distanceToResistanceRatio = (rawDistToResistance / atrNorm).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun recalculateTrend() {
        synchronized(closedCandles) {
            val allCandles = mutableListOf<SyntheticCandle>()
            allCandles.addAll(closedCandles.takeLast(8))
            currentCandle?.let { allCandles.add(it) }

            val vTrend = visionTrend

            // Si hay pocas velas sintéticas en memoria (< 4), priorizar la tendencia visual que analiza 30 velas reales del broker
            if (allCandles.size < 4) {
                detectedTrend = vTrend ?: TrendDirection.SIDEWAYS
                return
            }

            val greenCount = allCandles.count { it.isGreen }
            val redCount = allCandles.count { it.isRed }
            val firstOpen = allCandles.first().open
            val latestClose = allCandles.last().close
            val netDiff = latestClose - firstOpen
            val refPrice = if (firstOpen > 0.0) firstOpen else 1.0
            val pctChange = netDiff / refPrice

            when {
                // Tendencia Alcista: Mayoría verdes Y avance neto positivo contundente
                greenCount >= 3 && pctChange > 0.00008 && vTrend != TrendDirection.DOWNTREND -> {
                    detectedTrend = TrendDirection.UPTREND
                }
                // Si la visión del broker ve DOWNTREND, solo permitir UPTREND sintético si hay rompimiento mayoritario (>= 5 verdes)
                greenCount >= 5 && pctChange > 0.00015 -> {
                    detectedTrend = TrendDirection.UPTREND
                }
                // Tendencia Bajista: Mayoría rojas Y retroceso neto negativo contundente
                redCount >= 3 && pctChange < -0.00008 && vTrend != TrendDirection.UPTREND -> {
                    detectedTrend = TrendDirection.DOWNTREND
                }
                // Si la visión del broker ve UPTREND, solo permitir DOWNTREND sintético si hay rompimiento mayoritario (>= 5 rojas)
                redCount >= 5 && pctChange < -0.00015 -> {
                    detectedTrend = TrendDirection.DOWNTREND
                }
                else -> {
                    // Si no hay dominancia estadística clara, apoyarse en la visión del gráfico
                    detectedTrend = vTrend ?: TrendDirection.SIDEWAYS
                }
            }
        }
    }

    private fun evaluateSniperOpportunity(tick: MarketTick) {
        val sec = tick.candleSecond

        // Ventana de entrada sniper quirúrgica en cambio de vela (:57s a :05s)
        val inWindow = sec in 57..59 || sec in 0..5
        if (!inWindow) return

        // Filtro Anti-Choppy Universal (aplica SIEMPRE, incluso en YOLO)
        if (isChoppinessDetected()) {
            Log.d(TAG, "Oportunidad Sniper Headless suprimida por micro-rango estático sin volumen / choppiness")
            return
        }

        val distToSupport = distanceToSupportRatio
        val distToResistance = distanceToResistanceRatio

        synchronized(closedCandles) {
            if (closedCandles.size < 3) return

            val prev = closedCandles.last()

            // 1. ESTRATEGIA MT_REJECTION (Mecha de Rechazo Institucional >= 38% y cuerpo <= 45% en S/R)
            // CALL: Rechazo en Soporte (zona <= 22%) con mecha inferior y sin impulso bajista
            if (distToSupport <= 0.22f && prev.lowerWickRatio >= 0.38f && prev.bodyRatio <= 0.45f && !tick.isBearishImpulse && syntheticTickRsi <= 75.0) {
                val wickPct = (prev.lowerWickRatio * 100).toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REJECTION: Rechazo en Soporte (Mecha: $wickPct% | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // PUT: Rechazo en Resistencia (zona <= 22%) con mecha superior y sin impulso alcista
            if (distToResistance <= 0.22f && prev.upperWickRatio >= 0.38f && prev.bodyRatio <= 0.45f && !tick.isBullishImpulse && syntheticTickRsi >= 25.0) {
                val wickPct = (prev.upperWickRatio * 100).toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REJECTION: Rechazo en Resistencia (Mecha: $wickPct% | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 2. ESTRATEGIA MT_3_VELAS_AGOT (Agotamiento Cuantitativo de 3 Velas en S/R)
            val c1 = closedCandles[closedCandles.size - 3]
            val c2 = closedCandles[closedCandles.size - 2]
            val c3 = closedCandles[closedCandles.size - 1]

            // Agotamiento bajista en soporte: 3 velas rojas con decaimiento estricto C3 <= 55% C1 -> Reversión CALL
            val is3RedExhaustion = c1.isRed && c2.isRed && c3.isRed &&
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * 0.55f) && distToSupport <= 0.25f
            if (is3RedExhaustion && !tick.isBearishImpulse && syntheticTickRsi <= 70.0) {
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Rojas en Soporte (C3 <= 55% C1 | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // Agotamiento alcista en resistencia: 3 velas verdes con decaimiento estricto C3 <= 55% C1 -> Reversión PUT
            val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * 0.55f) && distToResistance <= 0.25f
            if (is3GreenExhaustion && !tick.isBullishImpulse && syntheticTickRsi >= 30.0) {
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Verdes en Resistencia (C3 <= 55% C1 | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 3. ESTRATEGIA MT_REVERSAL_EXTREMA (Reversión por Sobreextensión en Zonas Clave)
            if (isBearishOverextended && distToSupport <= 0.22f && (syntheticTickRsi <= 22.0 || consecutiveDownTicks >= 4) && !tick.isBearishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REVERSAL: Sobreventa Extrema en Soporte (RSI: $rsiInt | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            if (isBullishOverextended && distToResistance <= 0.22f && (syntheticTickRsi >= 78.0 || consecutiveUpTicks >= 4) && !tick.isBullishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REVERSAL: Sobrecompra Extrema en Resistencia (RSI: $rsiInt | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 4. ESTRATEGIA MT_CHOQUE_PULLBACK (Breakout + Retest en primeros segundos :00-:05)
            if (sec in 0..5) {
                val isBreakoutBullish = prev.isGreen && prev.bodyRatio >= 0.50f && prev.upperWickRatio <= 0.20f && distToSupport in 0.05f..0.30f
                if (isBreakoutBullish && (tick.isBullishImpulse || consecutiveUpTicks >= 1) && !tick.isBearishImpulse && syntheticTickRsi <= 75.0) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_CHOQUE_PULLBACK: Retest de Breakout Alcista (⏱ ${sec}s) -> CALL"
                    )
                    return
                }

                val isBreakoutBearish = prev.isRed && prev.bodyRatio >= 0.50f && prev.lowerWickRatio <= 0.20f && distToResistance in 0.05f..0.30f
                if (isBreakoutBearish && (tick.isBearishImpulse || consecutiveDownTicks >= 1) && !tick.isBullishImpulse && syntheticTickRsi >= 25.0) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_CHOQUE_PULLBACK: Retest de Breakout Bajista (⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }

            // Conteo de racha de velas cerradas consecutivas del mismo color para evitar sobreextensión en continuación
            val sameColorStreak = closedCandles.takeLast(4).takeLastWhile { 
                if (detectedTrend == TrendDirection.UPTREND) it.isGreen else it.isRed 
            }.size

            // 5. ESTRATEGIA MT_PULLBACK_TREND (Continuación de Tendencia tras Retroceso SANO con Espacio Libre >= 30% a S/R)
            if (sameColorStreak < 3) {
                if (detectedTrend == TrendDirection.UPTREND && !tick.isBearishImpulse && distToResistance >= 0.30f && syntheticTickRsi <= 78.0) {
                    // Retroceso sano: cuerpo pequeño o mecha de absorción, nunca una vela envolvente bajista masiva
                    val isHealthyPullback = (prev.isRed && prev.bodyRatio <= 0.45f) || prev.lowerWickRatio >= 0.25f || consecutiveDownTicks in 1..2
                    val turningUp = (consecutiveUpTicks >= 2 || tick.isBullishImpulse) && tick.velocity > 0f
                    if (isHealthyPullback && turningUp) {
                        onSignalGenerated?.invoke(
                            TradeAction.BUY,
                            "🎯 MT_PULLBACK: Continuación Alcista tras Retroceso Sano (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                        )
                        return
                    }
                }

                if (detectedTrend == TrendDirection.DOWNTREND && !tick.isBullishImpulse && distToSupport >= 0.30f && syntheticTickRsi >= 22.0) {
                    // Retroceso sano: cuerpo pequeño o mecha de absorción superior, nunca una vela envolvente alcista masiva
                    val isHealthyPullback = (prev.isGreen && prev.bodyRatio <= 0.45f) || prev.upperWickRatio >= 0.25f || consecutiveUpTicks in 1..2
                    val turningDown = (consecutiveDownTicks >= 2 || tick.isBearishImpulse) && tick.velocity < 0f
                    if (isHealthyPullback && turningDown) {
                        onSignalGenerated?.invoke(
                            TradeAction.SELL,
                            "🎯 MT_PULLBACK: Continuación Bajista tras Retroceso Sano (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                        )
                        return
                    }
                }
            }

            // 6. ESTRATEGIA MT_MOMENTUM_TREND (Impulso Direccional Temprano - solo si streak <= 2 y cuerpo sólido)
            if (sameColorStreak <= 2) {
                if (detectedTrend == TrendDirection.UPTREND && distToResistance >= 0.30f && prev.isGreen && prev.bodyRatio >= 0.40f && syntheticTickRsi <= 75.0) {
                    val strongGreenMomentum = (tick.isBullishImpulse || consecutiveUpTicks >= 1 || tick.velocity > 0f) && !tick.isBearishImpulse
                    if (strongGreenMomentum) {
                        onSignalGenerated?.invoke(
                            TradeAction.BUY,
                            "🎯 MT_MOMENTUM: Impulso Alcista Temprano (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                        )
                        return
                    }
                }

                if (detectedTrend == TrendDirection.DOWNTREND && distToSupport >= 0.30f && prev.isRed && prev.bodyRatio >= 0.40f && syntheticTickRsi >= 25.0) {
                    val strongRedMomentum = (tick.isBearishImpulse || consecutiveDownTicks >= 1 || tick.velocity < 0f) && !tick.isBullishImpulse
                    if (strongRedMomentum) {
                        onSignalGenerated?.invoke(
                            TradeAction.SELL,
                            "🎯 MT_MOMENTUM: Impulso Bajista Temprano (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                        )
                        return
                    }
                }
            }

            // 7. ESTRATEGIA MT_RANGE_BOUNCE (Rebote en Rango Lateral / Sideways)
            if (detectedTrend == TrendDirection.SIDEWAYS) {
                val hasHealthyRange = prev.range > 0.0 && (prev.bodyRatio >= 0.12f || prev.range >= 0.0000005)
                if (hasHealthyRange && distToSupport <= 0.22f && syntheticTickRsi <= 65.0 && (tick.isBullishImpulse || consecutiveUpTicks >= 1 || !tick.isBearishImpulse)) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_RANGE: Rebote en Soporte Lateral (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                    )
                    return
                }
                if (hasHealthyRange && distToResistance <= 0.22f && syntheticTickRsi >= 35.0 && (tick.isBearishImpulse || consecutiveDownTicks >= 1 || !tick.isBullishImpulse)) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_RANGE: Rechazo en Resistencia Lateral (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }
        }
    }
}
