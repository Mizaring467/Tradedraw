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

    var onSignalGenerated: ((TradeAction, String) -> Unit)? = null

    /**
     * Procesa cada micro-tick entrante desde el WebSocket.
     */
    fun onNewTick(tick: MarketTick) {
        val candleMinute = (tick.timestampMs / 60000L) * 60000L
        val active = currentCandle

        // 1. Conteo cuantitativo de ticks sin retroceso
        if (lastTickPrice > 0.0) {
            val delta = tick.price - lastTickPrice
            val epsilon = lastTickPrice * 0.000002
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
     * Si el rango de las últimas velas es inferior al 0.05% Y los ticks alternan sin dirección clara.
     */
    fun isChoppinessDetected(): Boolean {
        val isMicro = isMicroRange(5, MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT)
        val isAlternating = isTickAlternatingWithoutDirection(10)
        return isMicro && isAlternating
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
        val sec = tick.candleSecond
        val isStrictSniperWindow = tick.isStrictTimingWindow // :58 a :03
        val isTimingVetoed = tick.isTimingVetoed // :15 a :55

        // Veto de timing estricto y ventana :58-:03
        if (isTimingVetoed || !isStrictSniperWindow) return

        // Filtro Anti-Choppy / Micro-rango (<0.05% con ticks alternantes)
        if (isChoppinessDetected()) {
            Log.d(TAG, "Oportunidad Sniper Headless SUPRIMIDA por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
            return
        }

        val distToSupport = distanceToSupportRatio
        val distToResistance = distanceToResistanceRatio

        synchronized(closedCandles) {
            if (closedCandles.size < 3) return

            val prev = closedCandles.last()

            // 1. ESTRATEGIA MT_REJECTION (Mecha de Rechazo Institucional >= 40% en Soporte o Resistencia)
            // CALL: Rechazo en Soporte con mecha inferior prominente y confirmación de ticks
            if (distToSupport <= 0.18f && prev.lowerWickRatio >= 0.40f && consecutiveUpTicks >= 2 && !tick.isBearishImpulse) {
                val wickPct = (prev.lowerWickRatio * 100).toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REJECTION: Rechazo en Soporte (Mecha: $wickPct% | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // PUT: Rechazo en Resistencia con mecha superior prominente y confirmación de ticks
            if (distToResistance <= 0.18f && prev.upperWickRatio >= 0.40f && consecutiveDownTicks >= 2 && !tick.isBullishImpulse) {
                val wickPct = (prev.upperWickRatio * 100).toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REJECTION: Rechazo en Resistencia (Mecha: $wickPct% | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 2. ESTRATEGIA MT_3_VELAS_AGOT (Agotamiento de 3 Velas en S/R con Contracción c3 < c2 < c1)
            val c1 = closedCandles[closedCandles.size - 3]
            val c2 = closedCandles[closedCandles.size - 2]
            val c3 = closedCandles[closedCandles.size - 1]

            // Agotamiento bajista en soporte: 3 velas rojas decrecientes llegando al soporte -> Reversión CALL
            val is3RedExhaustion = c1.isRed && c2.isRed && c3.isRed &&
                    (c1.body > c2.body && c2.body > c3.body) &&
                    c3.body <= (c1.body * 0.45f) && distToSupport <= 0.22f
            if (is3RedExhaustion && consecutiveUpTicks >= 2 && !tick.isBearishImpulse) {
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Rojas en Soporte (c3 < 45% c1 | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // Agotamiento alcista en resistencia: 3 velas verdes decrecientes llegando a la resistencia -> Reversión PUT
            val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                    (c1.body > c2.body && c2.body > c3.body) &&
                    c3.body <= (c1.body * 0.45f) && distToResistance <= 0.22f
            if (is3GreenExhaustion && consecutiveDownTicks >= 2 && !tick.isBullishImpulse) {
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Verdes en Resistencia (c3 < 45% c1 | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 3. ESTRATEGIA MT_REVERSAL_EXTREMA (Reversión por Sobreextensión Severa en Zonas Clave)
            if (isBearishOverextended && distToSupport <= 0.12f && syntheticTickRsi <= 20.0 && consecutiveUpTicks >= 3 && prev.lowerWickRatio >= 0.25f) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REVERSAL: Sobreventa Extrema en Soporte (RSI: $rsiInt | Ticks Up: $consecutiveUpTicks | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            if (isBullishOverextended && distToResistance <= 0.12f && syntheticTickRsi >= 80.0 && consecutiveDownTicks >= 3 && prev.upperWickRatio >= 0.25f) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REVERSAL: Sobrecompra Extrema en Resistencia (RSI: $rsiInt | Ticks Down: $consecutiveDownTicks | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 4. ESTRATEGIA MT_PULLBACK_TREND (Continuación de Tendencia con Retroceso Confirmado)
            if (detectedTrend == TrendDirection.UPTREND && !tick.isBearishImpulse && distToResistance >= 0.30f && distToSupport in 0.12f..0.50f && prev.lowerWickRatio >= 0.20f && consecutiveUpTicks >= 2) {
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_PULLBACK: Continuación Alcista tras Retroceso a Soporte (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            if (detectedTrend == TrendDirection.DOWNTREND && !tick.isBullishImpulse && distToSupport >= 0.30f && distToResistance in 0.12f..0.50f && prev.upperWickRatio >= 0.20f && consecutiveDownTicks >= 2) {
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_PULLBACK: Continuación Bajista tras Retroceso a Resistencia (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                )
                return
            }
        }
    }
}
