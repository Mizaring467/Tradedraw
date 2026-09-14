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

    // Proximidad a Soporte/Resistencia dinámicos (0.0 = en el nivel, 1.0 = en el extremo opuesto)
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

        // 4. Recalcular S/R dinámico y ratios de distancia
        recalculateSupportResistance(tick)

        // 5. Evaluar estado cuantitativo de sobreextensión
        updateOverextensionStatus(tick)

        // 6. Recalcular tendencia en cada tick para respuesta inmediata en HUD y estrategia
        recalculateTrend(tick)

        // 7. Evaluar señales cuantitativas en la ventana sniper (:59s - :01s)
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
     * En submodo YOLO se desactiva para priorizar operativa continua.
     */
    fun isChoppinessDetected(): Boolean {
        if (subMode == AutonomousSubMode.YOLO) return false
        synchronized(closedCandles) {
            if (closedCandles.size < 4) return false
            val isStaticMicro = isMicroRange(5, MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT)
            val avgRange = closedCandles.takeLast(20).map { it.range }.average()
            val recent5Max = closedCandles.takeLast(5).maxOf { it.high }
            val recent5Min = closedCandles.takeLast(5).minOf { it.low }
            val recentRange = recent5Max - recent5Min
            val isRelativeDrop = avgRange > 0.0 && recentRange < (avgRange * 0.40)
            return (isStaticMicro || isRelativeDrop) && isTickAlternatingWithoutDirection(8)
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

                if (tick != null && tick.price > 0.0) {
                    val srRange = (maxHigh - minLow).coerceAtLeast(0.00001)
                    distanceToSupportRatio = ((tick.price - minLow) / srRange).toFloat().coerceIn(0f, 1f)
                    distanceToResistanceRatio = ((maxHigh - tick.price) / srRange).toFloat().coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun recalculateTrend(tick: MarketTick? = null) {
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
        val isYolo = (subMode == AutonomousSubMode.YOLO)

        // Ventana de entrada sniper en cambio de vela:
        // En YOLO: :55s a :12s (18s alrededor de la apertura para capturar oportunidades sin congelar al bot)
        // En Conservador: :57s a :04s (8s sniper exacto)
        val inWindow = if (isYolo) {
            sec in 55..59 || sec in 0..12
        } else {
            sec in 57..59 || sec in 0..4
        }
        if (!inWindow) return

        // Filtro Anti-Choppy (solo si el mercado está 100% muerto y no estamos en YOLO)
        if (isChoppinessDetected()) {
            Log.d(TAG, "Oportunidad Sniper Headless suprimida por micro-rango estático sin volumen")
            return
        }

        val distToSupport = distanceToSupportRatio
        val distToResistance = distanceToResistanceRatio

        synchronized(closedCandles) {
            if (closedCandles.size < 3) return

            val prev = closedCandles.last()

            // 1. ESTRATEGIA MT_REJECTION (Mecha de Rechazo Institucional >= 38% y cuerpo <= 45% en S/R)
            // CALL: Rechazo en Soporte (zona <= 30%) con mecha inferior y sin impulso bajista
            if (distToSupport <= 0.30f && prev.lowerWickRatio >= 0.38f && prev.bodyRatio <= 0.45f && !tick.isBearishImpulse) {
                val wickPct = (prev.lowerWickRatio * 100).toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REJECTION: Rechazo en Soporte (Mecha: $wickPct% | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // PUT: Rechazo en Resistencia (zona <= 30%) con mecha superior y sin impulso alcista
            if (distToResistance <= 0.30f && prev.upperWickRatio >= 0.38f && prev.bodyRatio <= 0.45f && !tick.isBullishImpulse) {
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
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * 0.55f) && distToSupport <= 0.30f
            if (is3RedExhaustion && !tick.isBearishImpulse) {
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Rojas en Soporte (C3 <= 55% C1 | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // Agotamiento alcista en resistencia: 3 velas verdes con decaimiento estricto C3 <= 55% C1 -> Reversión PUT
            val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * 0.55f) && distToResistance <= 0.30f
            if (is3GreenExhaustion && !tick.isBullishImpulse) {
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Verdes en Resistencia (C3 <= 55% C1 | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 3. ESTRATEGIA MT_REVERSAL_EXTREMA (Reversión por Sobreextensión en Zonas Clave)
            if (isBearishOverextended && distToSupport <= 0.28f && (syntheticTickRsi <= 25.0 || consecutiveDownTicks >= 4) && !tick.isBearishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REVERSAL: Sobreventa Extrema en Soporte (RSI: $rsiInt | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            if (isBullishOverextended && distToResistance <= 0.28f && (syntheticTickRsi >= 75.0 || consecutiveUpTicks >= 4) && !tick.isBullishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REVERSAL: Sobrecompra Extrema en Resistencia (RSI: $rsiInt | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 4. ESTRATEGIA MT_CHOQUE_PULLBACK (Breakout + Retest en primeros segundos :01-:06)
            if (sec in 1..6) {
                val isBreakoutBullish = prev.isGreen && prev.bodyRatio >= 0.50f && prev.upperWickRatio <= 0.20f && distToSupport in 0.10f..0.30f
                if (isBreakoutBullish && (tick.isBullishImpulse || consecutiveUpTicks >= 1) && !tick.isBearishImpulse) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_CHOQUE_PULLBACK: Retest de Breakout Alcista (⏱ ${sec}s) -> CALL"
                    )
                    return
                }

                val isBreakoutBearish = prev.isRed && prev.bodyRatio >= 0.50f && prev.lowerWickRatio <= 0.20f && distToResistance in 0.10f..0.30f
                if (isBreakoutBearish && (tick.isBearishImpulse || consecutiveDownTicks >= 1) && !tick.isBullishImpulse) {
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

            // 5. ESTRATEGIA MT_PULLBACK_TREND (Continuación de Tendencia tras Retroceso SANO con Espacio Libre >= 25% a S/R)
            if (sameColorStreak < 3) {
                if (detectedTrend == TrendDirection.UPTREND && !tick.isBearishImpulse && distToResistance >= 0.25f) {
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

                if (detectedTrend == TrendDirection.DOWNTREND && !tick.isBullishImpulse && distToSupport >= 0.25f) {
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
                if (detectedTrend == TrendDirection.UPTREND && distToResistance >= 0.28f && prev.isGreen && prev.bodyRatio >= 0.40f) {
                    val strongGreenMomentum = (tick.isBullishImpulse || consecutiveUpTicks >= 1 || tick.velocity > 0f) && !tick.isBearishImpulse
                    if (strongGreenMomentum) {
                        onSignalGenerated?.invoke(
                            TradeAction.BUY,
                            "🎯 MT_MOMENTUM: Impulso Alcista Temprano (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                        )
                        return
                    }
                }

                if (detectedTrend == TrendDirection.DOWNTREND && distToSupport >= 0.28f && prev.isRed && prev.bodyRatio >= 0.40f) {
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
                if (distToSupport <= 0.30f && (tick.isBullishImpulse || consecutiveUpTicks >= 1 || !tick.isBearishImpulse)) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_RANGE: Rebote en Soporte Lateral (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                    )
                    return
                }
                if (distToResistance <= 0.30f && (tick.isBearishImpulse || consecutiveDownTicks >= 1 || !tick.isBullishImpulse)) {
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
