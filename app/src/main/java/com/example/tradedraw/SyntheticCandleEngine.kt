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

    /**
     * Caché del resultado de isChoppinessDetected() — se invalida SOLO al cerrar una nueva vela.
     * Elimina el recálculo costoso O(n) en cada frame del HUD.
     */
    @Volatile
    var cachedChoppiness: Boolean = false
        private set

    // Caché de S/R y pivotes para evitar allocations y recálculos O(n) por tick
    private val cachedPivotHighs = ArrayList<Double>(80)
    private val cachedPivotLows = ArrayList<Double>(80)
    private var cachedHighestPivotHigh: Double? = null
    private var cachedLowestPivotLow: Double? = null
    private var cachedAtr: Double = 0.0001
    @Volatile
    private var pivotsCacheDirty: Boolean = true
    private var lastTrendCalcTime: Long = 0L

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
                // Actualizar caché de choppiness y marcar pivotes sucios UNA SOLA VEZ al cerrar la vela
                // (evita O(n) synchronized y recálculo de fractales en cada tick del motor)
                cachedChoppiness = isChoppinessDetected()
                pivotsCacheDirty = true
                lastTrendCalcTime = 0L
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
     * Rango de salida: 0.0 a 100.0. Zero-allocation para no generar presión en GC.
     */
    fun calculateTickRsi(period: Int = 14): Double {
        synchronized(recentTickPrices) {
            val total = recentTickPrices.size
            if (total < 4) return 50.0
            val n = Math.min(period, total - 1)
            val startIdx = total - 1 - n
            var gains = 0.0
            var losses = 0.0
            for (i in startIdx until total - 1) {
                val diff = recentTickPrices[i + 1] - recentTickPrices[i]
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
     * Calcula el Choppiness Index (CHOP) sobre una ventana de n velas cerradas.
     * CHOP = 100 * LOG10( SUM(TrueRange_n) / (MaxHigh_n - MinLow_n) ) / LOG10(n)
     * Valores >= 61.8 indican mercado en consolidación / rango sucio.
     * Valores <= 38.2 indican tendencia definida.
     */
    fun calculateChoppinessIndex(period: Int = 14): Double {
        synchronized(closedCandles) {
            if (closedCandles.size < period) return 50.0
            val sample = closedCandles.takeLast(period)
            var sumTr = 0.0
            for (i in sample.indices) {
                val current = sample[i]
                val prevClose = if (i > 0) sample[i - 1].close else current.open
                val tr = maxOf(
                    current.high - current.low,
                    Math.abs(current.high - prevClose),
                    Math.abs(current.low - prevClose)
                )
                sumTr += tr
            }
            val maxHigh = sample.maxOf { it.high }
            val minLow = sample.minOf { it.low }
            val span = maxHigh - minLow
            if (span <= 0.0 || sumTr <= 0.0) return 100.0
            val ratio = sumTr / span
            if (ratio <= 0.0) return 50.0
            val chop = 100.0 * (Math.log10(ratio) / Math.log10(period.toDouble()))
            return chop.coerceIn(0.0, 100.0)
        }
    }

    /**
     * Filtro Anti-Choppy / Micro-Rango Cuantitativo:
     * Detecta condiciones de consolidación extrema, compresión y mercado muerto:
     * 1. Micro-rango estático real (< 0.05%) con alternancia errática de ticks (mercado congelado/sin spread).
     * 2. Caída drástica de volatilidad respecto al promedio (< 40% del ATR) combinada con alternancia.
     * 3. Cluster de Indecisión: dojis solapados con cuerpos diminutos (< 28% o < 15% en YOLO) y CHOP elevado.
     * En modo YOLO: permite operar setups de rebote en rango / S&R y solo protege contra micro-rango estático sin spread.
     */
    fun isChoppinessDetected(): Boolean {
        synchronized(closedCandles) {
            if (closedCandles.size < 4) return false

            val isYolo = (subMode == AutonomousSubMode.YOLO)

            // 1. Micro-rango estático (< 0.05%) con alternancia errática de ticks
            val isStaticMicro = isMicroRange(5, MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT)
            val alternatingTicks = if (isYolo) 10 else 8
            val isAlternating = isTickAlternatingWithoutDirection(alternatingTicks)

            // Colapso de micro-rango real: si hay micro-rango estático con alternancia de ticks, es choppiness indiscutible
            if (isStaticMicro && isAlternating) return true

            // En modo YOLO: sólo vetar si hay micro-rango estático indiscutible con alternancia o cluster extremo de dojis planos
            if (isYolo) {
                val recentSample = closedCandles.takeLast(minOf(5, closedCandles.size))
                val avgBodyRatio = recentSample.map { it.bodyRatio }.average()
                val isFlatDojiSpam = avgBodyRatio < 0.15 && isAlternating
                return isStaticMicro || isFlatDojiSpam
            }

            // Modo CONSERVADOR:
            // 2. Caída relativa drástica de volatilidad combinada con alternancia errática de ticks
            val avgRange = closedCandles.takeLast(20).map { it.range }.average()
            val recent5Max = closedCandles.takeLast(5).maxOf { it.high }
            val recent5Min = closedCandles.takeLast(5).minOf { it.low }
            val recentRange = recent5Max - recent5Min
            val thresholdRatio = 0.40
            val isRelativeDrop = avgRange > 0.0 && recentRange < (avgRange * thresholdRatio)

            // 3. Cluster de Indecisión: dojis solapados (cuerpo promedio diminuto < 28% y CHOP elevado >= 58.0 o compresión de rango)
            val recentSample = closedCandles.takeLast(minOf(5, closedCandles.size))
            val avgBodyRatio = recentSample.map { it.bodyRatio }.average()
            val period = minOf(14, closedCandles.size)
            val chop = if (period >= 8) calculateChoppinessIndex(period) else 50.0

            val isDojiNoiseCluster = avgBodyRatio < 0.28 && (chop >= 58.0 || (avgRange > 0.0 && recentRange <= avgRange * 0.70))
            if (isDojiNoiseCluster) return true

            // Micro-rango estático absoluto o caída relativa con alternancia
            return isStaticMicro || (isRelativeDrop && isAlternating)
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
     * Utiliza caché de pivotes y ATR para eliminar allocations por tick y garantizar máxima fluidez.
     */
    private fun recalculateSupportResistance(tick: MarketTick? = null) {
        val currentPrice = tick?.price ?: currentCandle?.close ?: lastTickPrice
        if (currentPrice <= 0.0) return

        synchronized(closedCandles) {
            if (pivotsCacheDirty || cachedPivotHighs.isEmpty()) {
                cachedAtr = calculateAtr(14)
                cachedPivotHighs.clear()
                cachedPivotLows.clear()
                val candles = closedCandles.takeLast(40)

                // Detección de fractales/pivotes locales (mínimo 3 velas: c[i-1], c[i], c[i+1])
                if (candles.size >= 3) {
                    for (i in 1 until candles.size - 1) {
                        val prev = candles[i - 1]
                        val curr = candles[i]
                        val next = candles[i + 1]

                        if (curr.high >= prev.high && curr.high >= next.high) {
                            cachedPivotHighs.add(curr.high)
                        }
                        if (curr.low <= prev.low && curr.low <= next.low) {
                            cachedPivotLows.add(curr.low)
                        }
                    }
                }

                // Incluir extremos absolutos del bloque para garantizar niveles marco
                val maxHigh = candles.maxOfOrNull { it.high }
                val minLow = candles.minOfOrNull { it.low }
                if (maxHigh != null) cachedPivotHighs.add(maxHigh)
                if (minLow != null) cachedPivotLows.add(minLow)

                // Incluir extremos de velas cerradas históricas (excluyendo la vela previa inmediata candles.last()
                // para no distorsionar el cálculo de espacio libre hacia S/R cuando el precio está despegando)
                val candleCountToSample = if (candles.size > 1) candles.size - 1 else candles.size
                for (i in 0 until candleCountToSample) {
                    val c = candles[i]
                    cachedPivotHighs.add(c.high)
                    cachedPivotLows.add(c.low)
                }

                if (cachedPivotHighs.isEmpty() || cachedPivotLows.isEmpty()) {
                    synchronized(recentTickPrices) {
                        if (recentTickPrices.isNotEmpty()) {
                            val maxP = recentTickPrices.maxOrNull() ?: currentPrice
                            val minP = recentTickPrices.minOrNull() ?: currentPrice
                            cachedPivotHighs.add(maxP)
                            cachedPivotLows.add(minP)
                        } else {
                            cachedPivotHighs.add(currentPrice + cachedAtr)
                            cachedPivotLows.add(currentPrice - cachedAtr)
                        }
                    }
                }

                cachedHighestPivotHigh = cachedPivotHighs.maxOrNull()
                cachedLowestPivotLow = cachedPivotLows.minOrNull()
                pivotsCacheDirty = false
            }

            val atr = cachedAtr
            var minResAbove = Double.MAX_VALUE
            var maxSupBelow = Double.MIN_VALUE

            for (i in 0 until cachedPivotHighs.size) {
                val p = cachedPivotHighs[i]
                if (p > currentPrice && p < minResAbove) {
                    minResAbove = p
                }
            }
            for (i in 0 until cachedPivotLows.size) {
                val p = cachedPivotLows[i]
                if (p < currentPrice && p > maxSupBelow) {
                    maxSupBelow = p
                }
            }

            if (minResAbove != Double.MAX_VALUE) {
                dynamicResistancePrice = minResAbove
            } else {
                // Breakout alcista / nuevo ATH: la resistencia está proyectada por encima del precio actual
                dynamicResistancePrice = currentPrice + (atr * 1.5).coerceAtLeast(0.0001)
            }

            if (maxSupBelow != Double.MIN_VALUE) {
                dynamicSupportPrice = maxSupBelow
            } else {
                // Breakdown bajista / nuevo ATL: el soporte está proyectado por debajo del precio actual
                dynamicSupportPrice = currentPrice - (atr * 1.5).coerceAtLeast(0.0001)
            }

            // Principio de Polaridad Dinámica Cuantitativa:
            // Si el precio superó el máximo pivote previo (Breakout Alcista), ese pivote roto actúa como nuevo soporte dinámico
            val highPivot = cachedHighestPivotHigh
            val lowPivot = cachedLowestPivotLow
            if (highPivot != null && currentPrice >= highPivot && highPivot > dynamicSupportPrice) {
                dynamicSupportPrice = highPivot
            }
            // Si el precio perforó el mínimo pivote previo (Breakdown Bajista), ese pivote roto actúa como nueva resistencia dinámica
            if (lowPivot != null && currentPrice <= lowPivot && lowPivot < dynamicResistancePrice) {
                dynamicResistancePrice = lowPivot
            }

            // Normalización por ATR independiente:
            val atrNorm = (atr * 2.0).coerceAtLeast(0.0001)

            val rawDistToSupport = Math.abs(currentPrice - dynamicSupportPrice)
            val rawDistToResistance = Math.abs(dynamicResistancePrice - currentPrice)

            distanceToSupportRatio = (rawDistToSupport / atrNorm).toFloat().coerceIn(0f, 1f)
            distanceToResistanceRatio = (rawDistToResistance / atrNorm).toFloat().coerceIn(0f, 1f)
        }
    }

    /**
     * Calcula la pendiente de regresión lineal (OLS) sobre una serie de valores secuenciales.
     * Retorna la tasa de cambio promedio por periodo (Delta P / elemento).
     */
    fun calculateLinearRegressionSlope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        for (i in 0 until n) {
            val x = i.toDouble()
            val y = values[i]
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
        }

        val denominator = (n * sumX2) - (sumX * sumX)
        if (Math.abs(denominator) < 1e-12) return 0.0

        return ((n * sumXY) - (sumX * sumY)) / denominator
    }

    /**
     * Calcula la pendiente de tendencia normalizada por ATR sobre las últimas velas.
     * Slope normalizado = (Slope OLS / ATR).
     * Invariante ante escala de precio y volatilidad del activo.
     */
    fun calculateNormalizedTrendSlope(sampleCount: Int = 10): Double {
        synchronized(closedCandles) {
            val sample = mutableListOf<SyntheticCandle>()
            sample.addAll(closedCandles.takeLast(sampleCount - 1))
            currentCandle?.let { sample.add(it) }

            if (sample.size < 2) return 0.0

            val closes = sample.map { it.close }
            val slope = calculateLinearRegressionSlope(closes)
            val atr = calculateAtr(14)

            return if (atr > 0.0) slope / atr else 0.0
        }
    }

    private fun recalculateTrend(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTrendCalcTime < 1000L) return
        lastTrendCalcTime = now

        synchronized(closedCandles) {
            val sample = mutableListOf<SyntheticCandle>()
            sample.addAll(closedCandles.takeLast(9))
            currentCandle?.let { sample.add(it) }

            // Si hay pocas velas en memoria (< 3), evaluar ticks recientes con OLS
            if (sample.size < 3) {
                synchronized(recentTickPrices) {
                    if (recentTickPrices.size >= 10) {
                        val tickList = recentTickPrices.takeLast(30).toList()
                        val tickSlope = calculateLinearRegressionSlope(tickList)
                        val tickAtr = calculateAtr(14)
                        val normTickSlope = if (tickAtr > 0.0) tickSlope / (tickAtr / 60.0) else 0.0
                        detectedTrend = when {
                            normTickSlope >= 0.12 -> TrendDirection.UPTREND
                            normTickSlope <= -0.12 -> TrendDirection.DOWNTREND
                            else -> visionTrend ?: TrendDirection.SIDEWAYS
                        }
                    } else {
                        detectedTrend = visionTrend ?: TrendDirection.SIDEWAYS
                    }
                }
                return
            }

            val closes = sample.map { it.close }
            val slope = calculateLinearRegressionSlope(closes)
            val atr = calculateAtr(14)
            val normSlope = if (atr > 0.0) slope / atr else 0.0

            val vTrend = visionTrend

            when {
                // Tendencia alcista contundente por pendiente normalizada respecto a ATR
                normSlope >= 0.12 -> {
                    if (vTrend == TrendDirection.DOWNTREND && normSlope < 0.20) {
                        detectedTrend = TrendDirection.SIDEWAYS
                    } else {
                        detectedTrend = TrendDirection.UPTREND
                    }
                }
                // Tendencia bajista contundente por pendiente normalizada respecto a ATR
                normSlope <= -0.12 -> {
                    if (vTrend == TrendDirection.UPTREND && normSlope > -0.20) {
                        detectedTrend = TrendDirection.SIDEWAYS
                    } else {
                        detectedTrend = TrendDirection.DOWNTREND
                    }
                }
                else -> {
                    // En ausencia de pendiente estadística significativa, el mercado está en rango
                    detectedTrend = TrendDirection.SIDEWAYS
                }
            }
        }
    }

    private fun evaluateSniperOpportunity(tick: MarketTick) {
        val sec = tick.candleSecond

        // Ventana de entrada sniper quirúrgica en cambio de vela (:57s a :05s)
        val inWindow = sec in 57..59 || sec in 0..5
        if (!inWindow) return

        val isYolo = (subMode == AutonomousSubMode.YOLO)
        val isChoppy = isChoppinessDetected()

        // En modo Conservador: si hay choppiness (micro-rango estático, dojis solapados o caída con alternancia), suprimir
        // En modo YOLO: solo suprimir si hay colapso extremo de micro-rango (<0.05% con alternancia)
        if (!isYolo && isChoppy) {
            Log.d(TAG, "Oportunidad Sniper Headless suprimida por choppiness/dojis en modo conservador")
            return
        }

        val isExtremeMicroCompression = isMicroRange(5, MarketTickFilters.MAX_CHOPPY_RANGE_PERCENT) &&
                isTickAlternatingWithoutDirection(if (isYolo) 10 else 8)
        if (isExtremeMicroCompression) {
            Log.d(TAG, "Oportunidad Sniper Headless suprimida por micro-rango estático (<0.05%) sin volumen ni desplazamiento")
            return
        }

        val distToSupport = distanceToSupportRatio
        val distToResistance = distanceToResistanceRatio

        synchronized(closedCandles) {
            if (closedCandles.size < 3) return

            val prev = closedCandles.last()

            // Conteo estricto de racha de velas cerradas consecutivas del mismo color
            val consecutiveGreenCandles = closedCandles.takeLast(10).takeLastWhile { it.isGreen }.size
            val consecutiveRedCandles = closedCandles.takeLast(10).takeLastWhile { it.isRed }.size

            // ⚠️ VETO UNIVERSAL ANTI-AGOTAMIENTO PARABÓLICO:
            // Prohibido buscar continuación de compras (CALL) si ya van >= 3 velas verdes consecutivas sin retroceso
            val isBullishExhausted = consecutiveGreenCandles >= 3
            // Prohibido buscar continuación de ventas (PUT) si ya van >= 3 velas rojas consecutivas sin retroceso
            val isBearishExhausted = consecutiveRedCandles >= 3

            // 1. ESTRATEGIA MT_REJECTION (Rechazo en S/R con Mechas Claras)
            val supZoneLimit = if (isYolo) 0.28f else 0.24f
            val wickLimit = if (isYolo) 0.30f else 0.38f
            val bodyLimit = if (isYolo) 0.54f else 0.45f

            // CALL: Rechazo en Soporte con mecha inferior y sin impulso bajista
            if (distToSupport <= supZoneLimit && prev.lowerWickRatio >= wickLimit && prev.bodyRatio <= bodyLimit && !tick.isBearishImpulse && syntheticTickRsi <= 75.0) {
                val wickPct = (prev.lowerWickRatio * 100).toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REJECTION: Rechazo en Soporte (Mecha: $wickPct% | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // PUT: Rechazo en Resistencia con mecha superior y sin impulso alcista
            val resZoneLimit = if (isYolo) 0.28f else 0.24f
            if (distToResistance <= resZoneLimit && prev.upperWickRatio >= wickLimit && prev.bodyRatio <= bodyLimit && !tick.isBullishImpulse && syntheticTickRsi >= 25.0) {
                val wickPct = (prev.upperWickRatio * 100).toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REJECTION: Rechazo en Resistencia (Mecha: $wickPct% | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 1b. ESTRATEGIA MT_CONFIRM_BOUNCE (Rebote Confirmado en S/R · 2da Vela de Giro)
            val confirmDistLimit = if (isYolo) 0.34f else 0.28f
            val confirmFreeSpace = if (isYolo) 0.32f else 0.38f
            val cAnte = if (closedCandles.size >= 2) closedCandles[closedCandles.size - 2] else null
            val cAnteLower = cAnte?.lowerWickRatio ?: 0f
            val cAnteUpper = cAnte?.upperWickRatio ?: 0f

            // CALL: Confirmación de rebote alcista (vela previa verde tras testeo de soporte, despegue con espacio libre a resistencia)
            val isBounceCallSetup = prev.isGreen && consecutiveGreenCandles == 1 &&
                    distToSupport in 0.04f..confirmDistLimit && distToResistance >= confirmFreeSpace &&
                    (cAnteLower >= 0.18f || prev.lowerWickRatio >= 0.18f || cAnte?.isRed == true)
            if (isBounceCallSetup && (tick.isBullishImpulse || consecutiveUpTicks >= 1 || tick.velocity > 0f) && !tick.isBearishImpulse && syntheticTickRsi <= 72.0) {
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_CONFIRM_BOUNCE: Rebote Confirmado en Soporte (2da Vela Giro | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // PUT: Confirmación de rechazo bajista (vela previa roja tras testeo de resistencia, despegue con espacio libre a soporte)
            val isBouncePutSetup = prev.isRed && consecutiveRedCandles == 1 &&
                    distToResistance in 0.04f..confirmDistLimit && distToSupport >= confirmFreeSpace &&
                    (cAnteUpper >= 0.18f || prev.upperWickRatio >= 0.18f || cAnte?.isGreen == true)
            if (isBouncePutSetup && (tick.isBearishImpulse || consecutiveDownTicks >= 1 || tick.velocity < 0f) && !tick.isBullishImpulse && syntheticTickRsi >= 28.0) {
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_CONFIRM_BOUNCE: Rechazo Confirmado en Resistencia (2da Vela Giro | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 2. ESTRATEGIA MT_3_VELAS_AGOT (Agotamiento Cuantitativo de 3 Velas en S/R)
            val c1 = closedCandles[closedCandles.size - 3]
            val c2 = closedCandles[closedCandles.size - 2]
            val c3 = closedCandles[closedCandles.size - 1]

            val agotDecayRatio = if (isYolo) 0.65f else 0.55f
            val agotDistLimit = if (isYolo) 0.28f else 0.25f

            // Agotamiento bajista en soporte: 3 velas rojas con decaimiento -> Reversión CALL
            val is3RedExhaustion = c1.isRed && c2.isRed && c3.isRed &&
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * agotDecayRatio) && distToSupport <= agotDistLimit
            if (is3RedExhaustion && !tick.isBearishImpulse && syntheticTickRsi <= 70.0) {
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Rojas en Soporte (C3 <= ${(agotDecayRatio * 100).toInt()}% C1 | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            // Agotamiento alcista en resistencia: 3 velas verdes con decaimiento -> Reversión PUT
            val is3GreenExhaustion = c1.isGreen && c2.isGreen && c3.isGreen &&
                    (c1.body > c2.body && c2.body > c3.body) && (c3.body <= c1.body * agotDecayRatio) && distToResistance <= agotDistLimit
            if (is3GreenExhaustion && !tick.isBullishImpulse && syntheticTickRsi >= 30.0) {
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_3_VELAS_AGOT: Agotamiento 3 Velas Verdes en Resistencia (C3 <= ${(agotDecayRatio * 100).toInt()}% C1 | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // 3. ESTRATEGIA MT_REVERSAL_EXTREMA (Reversión por Sobreextensión en Zonas Clave)
            val revZoneLimit = if (isYolo) 0.26f else 0.22f
            val revRsiLow = if (isYolo) 25.0 else 22.0
            val revRsiHigh = if (isYolo) 75.0 else 78.0
            val revTicksNeeded = if (isYolo) 3 else 4

            if (isBearishOverextended && distToSupport <= revZoneLimit && (syntheticTickRsi <= revRsiLow || consecutiveDownTicks >= revTicksNeeded) && !tick.isBearishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToSupport * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.BUY,
                    "🎯 MT_REVERSAL: Sobreventa Extrema en Soporte (RSI: $rsiInt | Dist S: $distPct% | ⏱ ${sec}s) -> CALL"
                )
                return
            }

            if (isBullishOverextended && distToResistance <= revZoneLimit && (syntheticTickRsi >= revRsiHigh || consecutiveUpTicks >= revTicksNeeded) && !tick.isBullishImpulse) {
                val rsiInt = syntheticTickRsi.toInt()
                val distPct = (distToResistance * 100).toInt()
                onSignalGenerated?.invoke(
                    TradeAction.SELL,
                    "🎯 MT_REVERSAL: Sobrecompra Extrema en Resistencia (RSI: $rsiInt | Dist R: $distPct% | ⏱ ${sec}s) -> PUT"
                )
                return
            }

            // Las estrategias de continuación y breakout requieren que el mercado NO esté en choppiness/dojis
            // (a menos que estemos en modo YOLO donde se permite operar impulso con confirmación de tick)
            val allowTrendAndBreakouts = isYolo || !isChoppy

            // 4. ESTRATEGIA MT_CHOQUE_PULLBACK (Breakout + Retest en primeros segundos :00-:05)
            if (allowTrendAndBreakouts && sec in 0..5) {
                val breakoutBodyRatio = if (isYolo) 0.42f else 0.50f
                val breakoutWickRatio = if (isYolo) 0.25f else 0.20f
                // Breakout alcista solo es válido en etapas tempranas (máximo 2 velas verdes), nunca en racha sobreextendida
                val isBreakoutBullish = !isBullishExhausted && consecutiveGreenCandles <= 2 &&
                        prev.isGreen && prev.bodyRatio >= breakoutBodyRatio && prev.upperWickRatio <= breakoutWickRatio && distToSupport in 0.05f..0.32f
                if (isBreakoutBullish && (tick.isBullishImpulse || consecutiveUpTicks >= 1) && !tick.isBearishImpulse && syntheticTickRsi <= 75.0) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_CHOQUE_PULLBACK: Retest de Breakout Alcista (⏱ ${sec}s) -> CALL"
                    )
                    return
                }

                // Breakout bajista solo es válido en etapas tempranas (máximo 2 velas rojas), nunca en racha sobreextendida
                val isBreakoutBearish = !isBearishExhausted && consecutiveRedCandles <= 2 &&
                        prev.isRed && prev.bodyRatio >= breakoutBodyRatio && prev.lowerWickRatio <= breakoutWickRatio && distToResistance in 0.05f..0.32f
                if (isBreakoutBearish && (tick.isBearishImpulse || consecutiveDownTicks >= 1) && !tick.isBullishImpulse && syntheticTickRsi >= 25.0) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_CHOQUE_PULLBACK: Retest de Breakout Bajista (⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }

            // 5. ESTRATEGIA MT_PULLBACK_TREND (Continuación de Tendencia tras Retroceso SANO con Espacio Libre >= 30% a S/R)
            val trendFreeSpace = if (isYolo) 0.22f else 0.30f

            if (allowTrendAndBreakouts && detectedTrend == TrendDirection.UPTREND && !isBullishExhausted && distToResistance >= trendFreeSpace && syntheticTickRsi <= 78.0) {
                // Retroceso sano: cuerpo rojo contenido o mecha de absorción inferior, nunca en plena racha verde sobreextendida
                val isHealthyPullback = (prev.isRed && prev.bodyRatio <= 0.48f) || prev.lowerWickRatio >= 0.22f || consecutiveDownTicks in 1..2
                val turningUp = (consecutiveUpTicks >= (if (isYolo) 1 else 2) || tick.isBullishImpulse) && tick.velocity > 0f
                if (isHealthyPullback && turningUp) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_PULLBACK: Continuación Alcista tras Retroceso Sano (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                    )
                    return
                }
            }

            if (allowTrendAndBreakouts && detectedTrend == TrendDirection.DOWNTREND && !isBearishExhausted && distToSupport >= trendFreeSpace && syntheticTickRsi >= 22.0) {
                // Retroceso sano: cuerpo verde contenido o mecha de absorción superior, nunca en plena racha roja sobreextendida
                val isHealthyPullback = (prev.isGreen && prev.bodyRatio <= 0.48f) || prev.upperWickRatio >= 0.22f || consecutiveUpTicks in 1..2
                val turningDown = (consecutiveDownTicks >= (if (isYolo) 1 else 2) || tick.isBearishImpulse) && tick.velocity < 0f
                if (isHealthyPullback && turningDown) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_PULLBACK: Continuación Bajista tras Retroceso Sano (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }

            // 6. ESTRATEGIA MT_MOMENTUM_TREND (Impulso Direccional Temprano - estrictamente 1 a 2 velas de racha)
            val momentumBodyRatio = if (isYolo) 0.32f else 0.40f

            if (allowTrendAndBreakouts && detectedTrend == TrendDirection.UPTREND && !isBullishExhausted && consecutiveGreenCandles in 1..2 &&
                distToResistance >= trendFreeSpace && prev.isGreen && prev.bodyRatio >= momentumBodyRatio && syntheticTickRsi <= 75.0) {
                val strongGreenMomentum = (tick.isBullishImpulse || consecutiveUpTicks >= 1 || tick.velocity > 0f) && !tick.isBearishImpulse
                if (strongGreenMomentum) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_MOMENTUM: Impulso Alcista Temprano (Dist R: ${(distToResistance*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                    )
                    return
                }
            }

            if (allowTrendAndBreakouts && detectedTrend == TrendDirection.DOWNTREND && !isBearishExhausted && consecutiveRedCandles in 1..2 &&
                distToSupport >= trendFreeSpace && prev.isRed && prev.bodyRatio >= momentumBodyRatio && syntheticTickRsi >= 25.0) {
                val strongRedMomentum = (tick.isBearishImpulse || consecutiveDownTicks >= 1 || tick.velocity < 0f) && !tick.isBullishImpulse
                if (strongRedMomentum) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_MOMENTUM: Impulso Bajista Temprano (Dist S: ${(distToSupport*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }

            // 7. ESTRATEGIA MT_RANGE_BOUNCE (Rebote en Rango Lateral / Sideways)
            if (detectedTrend == TrendDirection.SIDEWAYS) {
                val atr = calculateAtr(14)
                val channelSpan = dynamicResistancePrice - dynamicSupportPrice
                val minSpanMultiplier = if (isYolo) 1.2 else 1.5
                val hasTradableChannel = atr > 0.0 && channelSpan >= (atr * minSpanMultiplier)

                val rangeWickRatio = if (isYolo) 0.22f else 0.28f
                val rangeDistLimit = if (isYolo) 0.25f else 0.22f

                // Rebote en soporte: requiere canal operable, mecha de rechazo inferior y empuje alcista activo
                if (hasTradableChannel && !isBullishExhausted && consecutiveGreenCandles <= 1 &&
                    distToSupport <= rangeDistLimit && prev.lowerWickRatio >= rangeWickRatio && syntheticTickRsi <= 65.0 &&
                    (tick.isBullishImpulse || consecutiveUpTicks >= (if (isYolo) 1 else 2)) && tick.velocity > 0f) {
                    onSignalGenerated?.invoke(
                        TradeAction.BUY,
                        "🎯 MT_RANGE: Rebote en Soporte Lateral (Dist S: ${(distToSupport*100).toInt()}% | Mecha: ${(prev.lowerWickRatio*100).toInt()}% | ⏱ ${sec}s) -> CALL"
                    )
                    return
                }

                // Rechazo en resistencia: requiere canal operable, mecha de rechazo superior y empuje bajista activo
                if (hasTradableChannel && !isBearishExhausted && consecutiveRedCandles <= 1 &&
                    distToResistance <= rangeDistLimit && prev.upperWickRatio >= rangeWickRatio && syntheticTickRsi >= 35.0 &&
                    (tick.isBearishImpulse || consecutiveDownTicks >= (if (isYolo) 1 else 2)) && tick.velocity < 0f) {
                    onSignalGenerated?.invoke(
                        TradeAction.SELL,
                        "🎯 MT_RANGE: Rechazo en Resistencia Lateral (Dist R: ${(distToResistance*100).toInt()}% | Mecha: ${(prev.upperWickRatio*100).toInt()}% | ⏱ ${sec}s) -> PUT"
                    )
                    return
                }
            }
        }
    }
}
