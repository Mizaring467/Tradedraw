package com.example.tradedraw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log

enum class CandleType {
    GREEN, RED, DOJI
}

enum class TrendDirection {
    UPTREND, DOWNTREND, SIDEWAYS
}

enum class TradeOutcome {
    WIN, LOSS
}

data class CandleData(
    val type: CandleType,
    val x: Float,
    val topY: Float,
    val bottomY: Float,
    val bodyTopY: Float,
    val bodyBottomY: Float,
    val totalHeight: Float,
    val bodyHeight: Float,
    val topWickRatio: Float,
    val bottomWickRatio: Float
)

typealias AnalysisResult = VisionAnalysisResult

data class VisionAnalysisResult(
    val currentPriceY: Float = 0f,
    val highestPoint: PointF? = null,
    val lowestPoint: PointF? = null,
    val trend: TrendDirection = TrendDirection.SIDEWAYS,
    val lastCandles: List<CandleType> = emptyList(),
    val candleList: List<CandleData> = emptyList(),
    val consecutiveCount: Int = 0,
    val streakBadge: String = "",
    val signalPowerCall: Int = 50,
    val signalPowerPut: Int = 50,
    val isMarketSideways: Boolean = false,
    val isHammer: Boolean = false,
    val isEngulfing: Boolean = false,
    val isEngulfingCall: Boolean = false,
    val isEngulfingPut: Boolean = false,
    val isFalseBreakoutCall: Boolean = false,
    val isFalseBreakoutPut: Boolean = false,
    val isRejectionCall: Boolean = false,
    val isRejectionPut: Boolean = false,
    val touchesSupport: Boolean = false,
    val touchesResistance: Boolean = false,
    val hasTopRejectionWick: Boolean = false,
    val hasBottomRejectionWick: Boolean = false,
    val isChoqueCall: Boolean = false,
    val isChoquePut: Boolean = false,
    val isChoquePullbackCall: Boolean = false,
    val isChoquePullbackPut: Boolean = false,
    val is3VelasCall: Boolean = false,
    val is3VelasPut: Boolean = false,
    val isExhaustion3CandlesCall: Boolean = false,
    val isExhaustion3CandlesPut: Boolean = false,
    val dynamicResistanceY: Float = 0f,
    val dynamicSupportY: Float = 0f,
    val greenPixelsDetected: Int = 0,
    val redPixelsDetected: Int = 0,
    val diagnosticSummary: String = "",
    val isCallSignal: Boolean = false,
    val isPutSignal: Boolean = false,
    val signalScore: Int = 50,
    val candleSecond: Int = ((System.currentTimeMillis() / 1000) % 60).toInt(),
    val isSniperTimingWindow: Boolean = false,
    val isLateTimingForbidden: Boolean = false,
    val isPullbackSniperCall: Boolean = false,
    val isPullbackSniperPut: Boolean = false,
    val isConsolidationTight: Boolean = false,
    val confluenceScoreCall: Int = 50,
    val confluenceScorePut: Int = 50,
    val latestCandleX: Float = 0f,
    val isChartOffCenterRight: Boolean = false,
    val isChartOffCenterLeft: Boolean = false,
    val isPriceNearBottom: Boolean = false,
    val isPriceNearTop: Boolean = false,
    val hasStrongMomentumDown: Boolean = false,
    val hasStrongMomentumUp: Boolean = false,
    val distanceToSupportRatio: Float = if (Math.abs(dynamicSupportY - dynamicResistanceY) > 1f) {
        (Math.abs(currentPriceY - dynamicSupportY) / Math.abs(dynamicSupportY - dynamicResistanceY)).coerceIn(0f, 1f)
    } else 0.5f,
    val distanceToResistanceRatio: Float = if (Math.abs(dynamicSupportY - dynamicResistanceY) > 1f) {
        (Math.abs(currentPriceY - dynamicResistanceY) / Math.abs(dynamicSupportY - dynamicResistanceY)).coerceIn(0f, 1f)
    } else 0.5f,
    val isNearSupportZone: Boolean = touchesSupport || distanceToSupportRatio < 0.15f,
    val isNearResistanceZone: Boolean = touchesResistance || distanceToResistanceRatio < 0.15f
)

class VisionAnalyzer {

    private val hsvBuffer = FloatArray(3)

    /**
     * Crea una instancia de CandleData calculando automáticamente proporciones de mechas y alturas.
     */
    fun createCandle(
        type: CandleType,
        x: Float,
        topY: Float,
        bottomY: Float,
        bodyTopY: Float,
        bodyBottomY: Float
    ): CandleData {
        val totalH = (bottomY - topY).coerceAtLeast(1f)
        val bodyH = (bodyBottomY - bodyTopY).coerceAtLeast(1f)
        val topWick = (bodyTopY - topY).coerceAtLeast(0f)
        val bottomWick = (bottomY - bodyBottomY).coerceAtLeast(0f)
        return CandleData(
            type = type,
            x = x,
            topY = topY,
            bottomY = bottomY,
            bodyTopY = bodyTopY,
            bodyBottomY = bodyBottomY,
            totalHeight = totalH,
            bodyHeight = bodyH,
            topWickRatio = topWick / totalH,
            bottomWickRatio = bottomWick / totalH
        )
    }

    /**
     * Agrupa y fusiona columnas de escaneo adyacentes que corresponden a una misma vela física.
     * Esto evita que una vela ancha de 12-25px genere múltiples velas duplicadas en candleList.
     */
    fun clusterAndMergeCandleColumns(rawColumns: List<CandleData>, minSpacingPx: Float = 10f): List<CandleData> {
        if (rawColumns.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<CandleData>>()
        var currentCluster = mutableListOf<CandleData>()

        for (col in rawColumns) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(col)
            } else {
                val lastInCluster = currentCluster.last()
                val dist = Math.abs(col.x - lastInCluster.x)
                val sameColor = col.type == lastInCluster.type
                // Solapamiento vertical de cuerpo para asegurar que es la misma vela
                val bodyOverlap = col.bodyTopY < lastInCluster.bodyBottomY + 10f && col.bodyBottomY > lastInCluster.bodyTopY - 10f

                if (dist <= minSpacingPx && sameColor && bodyOverlap) {
                    currentCluster.add(col)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(col)
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        return clusters.map { group ->
            val dominantType = group.first().type
            val avgX = group.map { it.x }.average().toFloat()
            val minTopY = group.minOf { it.topY }
            val maxBottomY = group.maxOf { it.bottomY }
            val avgBodyTopY = group.map { it.bodyTopY }.average().toFloat()
            val avgBodyBottomY = group.map { it.bodyBottomY }.average().toFloat()

            createCandle(
                type = dominantType,
                x = avgX,
                topY = minTopY,
                bottomY = maxBottomY,
                bodyTopY = avgBodyTopY,
                bodyBottomY = avgBodyBottomY
            )
        }
    }

    /**
     * Identifica niveles fractales de soporte y resistencia basados en Swing Highs y Swing Lows (N=2).
     */
    fun detectFractalLevels(candles: List<CandleData>): Pair<List<Float>, List<Float>> {
        val swingHighs = mutableListOf<Float>()
        val swingLows = mutableListOf<Float>()

        if (candles.size < 5) {
            val tops = candles.map { it.topY }
            val bottoms = candles.map { it.bottomY }
            return Pair(
                bottoms.maxOrNull()?.let { listOf(it) } ?: emptyList(),
                tops.minOrNull()?.let { listOf(it) } ?: emptyList()
            )
        }

        // Pivotes con ventana de 2 a cada lado
        for (i in 2 until candles.size - 2) {
            val current = candles[i]
            // Swing High (Resistencia / Techo -> menor Y en coordenadas de pantalla)
            val isSwingHigh = current.topY <= candles[i - 1].topY &&
                    current.topY <= candles[i - 2].topY &&
                    current.topY <= candles[i + 1].topY &&
                    current.topY <= candles[i + 2].topY

            if (isSwingHigh) {
                swingHighs.add(current.topY)
            }

            // Swing Low (Soporte / Piso -> mayor Y en coordenadas de pantalla)
            val isSwingLow = current.bottomY >= candles[i - 1].bottomY &&
                    current.bottomY >= candles[i - 2].bottomY &&
                    current.bottomY >= candles[i + 1].bottomY &&
                    current.bottomY >= candles[i + 2].bottomY

            if (isSwingLow) {
                swingLows.add(current.bottomY)
            }
        }

        return Pair(swingLows, swingHighs)
    }

    /**
     * Evalúa si una longitud de corrida vertical cumple con el filtro de ruido (>= 6px).
     * Cada paso de muestreo es de 2px, por lo que 3 muestras equivalen a 6px.
     */
    fun isValidContiguousRun(sampleCount: Int, stepSizePx: Int = 2): Boolean {
        return (sampleCount * stepSizePx) >= 6
    }

    /**
     * Evalúa todos los patrones y estrategias de Master Trader directamente a partir de una lista de velas.
     * Permite pruebas unitarias puras sin depender de Bitmaps de Android.
     */
    fun evaluateCandlePatterns(
        candleList: List<CandleData>,
        supportLinesY: List<Float> = emptyList(),
        resistanceLinesY: List<Float> = emptyList(),
        startY: Float = 0f,
        endY: Float = 1000f,
        startX: Float = 0f,
        endX: Float = 1000f,
        totalGreenPixels: Int = 0,
        totalRedPixels: Int = 0,
        isLandscape: Boolean = true
    ): VisionAnalysisResult {
        var minPriceY = Float.MAX_VALUE // Menor Y = Mayor precio (Resistencia / Techo)
        var maxPriceY = Float.MIN_VALUE // Mayor Y = Menor precio (Soporte / Piso)
        var highestX = (startX + endX) * 0.5f
        var lowestX = (startX + endX) * 0.5f
        var latestPriceY = (startY + endY) / 2f

        val candleTypes = candleList.map { it.type }

       if (candleList.isNotEmpty()) {
            val historicalCandles = if (candleList.size >= 3) candleList.drop(1) else candleList

            // Detección institucional por Fractales (Swing Highs y Swing Lows)
            val (fractalSupports, fractalResistances) = detectFractalLevels(historicalCandles)
            val confirmedTops = historicalCandles.map { it.topY }
            val confirmedBottoms = historicalCandles.map { it.bottomY }

            minPriceY = fractalResistances.minOrNull() ?: (confirmedTops.minOrNull() ?: (startY + (endY - startY) * 0.25f))
            maxPriceY = fractalSupports.maxOrNull() ?: (confirmedBottoms.maxOrNull() ?: (startY + (endY - startY) * 0.75f))

            highestX = historicalCandles.find { it.topY == minPriceY }?.x ?: ((startX + endX) * 0.5f)
            lowestX = historicalCandles.find { it.bottomY == maxPriceY }?.x ?: ((startX + endX) * 0.5f)

            latestPriceY = (candleList.first().bodyTopY + candleList.first().bodyBottomY) / 2f
       }

        // Racha consecutiva de la última vela hacia atrás
        val lastType = candleTypes.firstOrNull() ?: CandleType.DOJI
        var consecutive = 0
        for (c in candleTypes) {
            if (c == lastType && c != CandleType.DOJI) {
                consecutive++
            } else {
                break
            }
        }

        val streakBadge = when (lastType) {
            CandleType.GREEN -> "${consecutive}V 🟢"
            CandleType.RED -> "${consecutive}R 🔴"
            else -> "1D ⚪"
        }

        // Tendencia institucional basada en medias móviles de velas recientes (8-12 velas) y pendiente
        val chartHeight = (endY - startY).coerceAtLeast(100f)
        val trend = if (candleList.size >= 3) {
            val sampleSize = candleList.size.coerceAtMost(10)
            val sample = candleList.take(sampleSize)

            // Determinar si candleList está ordenada de derecha a izquierda (más reciente en index 0) o de izquierda a derecha
            val isOrderedRightToLeft = sample.size >= 2 && sample.first().x >= sample.last().x
            val candleNewest = if (isOrderedRightToLeft) sample.first() else sample.last()
            val candleOldest = if (isOrderedRightToLeft) sample.last() else sample.first()

            val priceNewest = if (candleNewest.type == CandleType.GREEN) candleNewest.bodyTopY else candleNewest.bodyBottomY
            val priceOldest = if (candleOldest.type == CandleType.GREEN) candleOldest.bodyTopY else candleOldest.bodyBottomY

            val redCount = sample.count { it.type == CandleType.RED }
            val greenCount = sample.count { it.type == CandleType.GREEN }

            // Micro-tendencia de las 4 velas más recientes para detectar consolidación plana inmediata
            val microSample = sample.take(4)
            val microNewest = if (isOrderedRightToLeft) microSample.first() else microSample.last()
            val microOldest = if (isOrderedRightToLeft) microSample.last() else microSample.first()
            val microChange = (if (microNewest.type == CandleType.GREEN) microNewest.bodyTopY else microNewest.bodyBottomY) -
                              (if (microOldest.type == CandleType.GREEN) microOldest.bodyTopY else microOldest.bodyBottomY)
            val isMicroFlat = Math.abs(microChange) < (chartHeight * 0.025f)

            // Pendiente temporal (precio más reciente vs más antiguo: menor Y = precio más alto)
            val netPriceChange = priceNewest - priceOldest // > 0 significa que el precio cayó hacia mayor Y
            val minMoveThreshold = (chartHeight * 0.015f).coerceIn(8f, 25f)

            when {
                // Si la micro-tendencia inmediata está completamente estancada con alternancia y sin desplazamiento neto
                isMicroFlat && microSample.size >= 4 && (microSample.count { it.type == CandleType.GREEN } == microSample.count { it.type == CandleType.RED }) && Math.abs(netPriceChange) < minMoveThreshold -> TrendDirection.SIDEWAYS
                // Caída bajista: precio nuevo cayó (mayor Y) con mayoría de rojas o inclinación fuerte
                netPriceChange > minMoveThreshold && redCount >= greenCount -> TrendDirection.DOWNTREND
                netPriceChange > minMoveThreshold * 2.0f -> TrendDirection.DOWNTREND
                // Subida alcista: precio nuevo subió (menor Y) con mayoría de verdes o inclinación fuerte
                netPriceChange < -minMoveThreshold && greenCount >= redCount -> TrendDirection.UPTREND
                netPriceChange < -minMoveThreshold * 2.0f -> TrendDirection.UPTREND
                highestX > lowestX && greenCount > redCount + 1 -> TrendDirection.UPTREND
                lowestX > highestX && redCount > greenCount + 1 -> TrendDirection.DOWNTREND
                else -> TrendDirection.SIDEWAYS
            }
        } else {
            TrendDirection.SIDEWAYS
        }

        // S/R calculados estrictamente dentro del rango de velas encontradas:
        // Resistencia = Menor Y (Techo de velas)
        // Soporte = Mayor Y (Suelo de velas)
        var finalResistanceY = if (minPriceY < Float.MAX_VALUE) minPriceY else (startY + (endY - startY) * 0.20f)
        var finalSupportY = if (maxPriceY > Float.MIN_VALUE) maxPriceY else (startY + (endY - startY) * 0.80f)

        // Garantizar separación mínima proporcional para que NUNCA se monten una sobre otra
        val minSep = (endY - startY) * 0.12f
        if (finalSupportY <= finalResistanceY + minSep) {
            val mid = (finalResistanceY + finalSupportY) / 2f
            finalResistanceY = (mid - minSep / 2f).coerceAtLeast(startY)
            finalSupportY = (mid + minSep / 2f).coerceAtMost(endY)
        }

        val highPoint = PointF().apply {
            x = highestX
            y = finalResistanceY
        }
        val lowPoint = PointF().apply {
            x = lowestX
            y = finalSupportY
        }

        // Nivel de soporte y resistencia efectivos
        var effectiveSupportY = supportLinesY.minByOrNull { Math.abs(latestPriceY - it) } ?: finalSupportY
        var effectiveResistanceY = resistanceLinesY.minByOrNull { Math.abs(latestPriceY - it) } ?: finalResistanceY

        // Principio de Polaridad Dinámica S/R:
        // - Breakout Alcista / ATH: la resistencia previa rota actúa como soporte dinámico,
        //   y la nueva resistencia se fija en el techo real absoluto de las velas.
        // - Breakdown Bajista / ATL: el soporte previo roto actúa como resistencia dinámica,
        //   y el nuevo soporte se fija en el piso real absoluto de las velas.
        if (candleList.isNotEmpty() && latestPriceY < finalResistanceY) {
            effectiveSupportY = finalResistanceY
            effectiveResistanceY = candleList.map { it.topY }.minOrNull() ?: finalResistanceY
        } else if (candleList.isNotEmpty() && latestPriceY > finalSupportY) {
            effectiveResistanceY = finalSupportY
            effectiveSupportY = candleList.map { it.bottomY }.maxOrNull() ?: finalSupportY
        }

        // Proximidad estricta a soportes y resistencias reales (validación de zona institucional)
        val threshold = ((endY - startY) * 0.035f).coerceIn(12f, 28f)
        val lastCandle = candleList.firstOrNull()

        // 1. Mechas de Rechazo Significativas (Rejection Wicks >= 40% del rango total de la vela)
        val hasTopRejection = lastCandle != null && lastCandle.topWickRatio >= 0.40f
        val hasBottomRejection = lastCandle != null && lastCandle.bottomWickRatio >= 0.40f

        val isNearSupportLevel = supportLinesY.any { Math.abs(latestPriceY - it) <= threshold || (lastCandle != null && Math.abs(lastCandle.bottomY - it) <= threshold) } ||
                Math.abs(latestPriceY - effectiveSupportY) <= threshold ||
                (lastCandle != null && Math.abs(lastCandle.bottomY - effectiveSupportY) <= threshold)

        val isNearResistanceLevel = resistanceLinesY.any { Math.abs(latestPriceY - it) <= threshold || (lastCandle != null && Math.abs(lastCandle.topY - it) <= threshold) } ||
                Math.abs(latestPriceY - effectiveResistanceY) <= threshold ||
                (lastCandle != null && Math.abs(lastCandle.topY - effectiveResistanceY) <= threshold)

        val touchesSupport = isNearSupportLevel && (hasBottomRejection || lastType == CandleType.GREEN)
        val touchesResistance = isNearResistanceLevel && (hasTopRejection || lastType == CandleType.RED)

        // Rechazo VÁLIDO: Exige que la mecha ocurra SOBRE o tocando el soporte/resistencia, nunca en vacío
        val isRejectionCall = hasBottomRejection && isNearSupportLevel
        val isRejectionPut = hasTopRejection && isNearResistanceLevel

        // 2. Choque de Máximos y Mínimos (Breakout + Retest con confirmación)
        var isChoqueCall = false
        var isChoquePut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0]
            val c1 = candleList[1]
            val c2 = candleList[2]
            // Breakout alcista previo retesteado por c0 con absorción compradora
            val touchesBreakoutSupport = Math.abs(c0.bottomY - c2.bodyTopY) <= threshold || Math.abs(c0.bottomY - c1.bodyTopY) <= threshold
            if (c2.bodyBottomY > c1.bodyTopY && touchesBreakoutSupport && (c0.bottomWickRatio >= 0.15f || c0.type == CandleType.GREEN)) {
                isChoqueCall = true
            }
            // Breakout bajista previo retesteado por c0 con absorción vendedora
            val touchesBreakoutResistance = Math.abs(c0.topY - c2.bodyBottomY) <= threshold || Math.abs(c0.topY - c1.bodyBottomY) <= threshold
            if (c2.bodyTopY < c1.bodyBottomY && touchesBreakoutResistance && (c0.topWickRatio >= 0.15f || c0.type == CandleType.RED)) {
                isChoquePut = true
            }
        }

        // 3. Patrón 3 Velas y Agotamiento de Tendencia (V1 > V2 > V3 con V3 < 0.4*V1)
        var isExhaustionCall = false
        var isExhaustionPut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0] // Vela más reciente (V3)
            val c1 = candleList[1] // Vela media (V2)
            val c2 = candleList[2] // Vela inicial (V1)

            val allRed = c0.type == CandleType.RED && c1.type == CandleType.RED && c2.type == CandleType.RED
            val allGreen = c0.type == CandleType.GREEN && c1.type == CandleType.GREEN && c2.type == CandleType.GREEN

            val v1 = c2.bodyHeight
            val v2 = c1.bodyHeight
            val v3 = c0.bodyHeight

            val decayingBodies = (v1 > v2) && (v2 > v3) && (v3 <= v1 * 0.40f || (v2 <= v1 * 0.70f && v3 <= v2 * 0.60f))

            if (allRed && decayingBodies) isExhaustionCall = true
            else if (allGreen && decayingBodies) isExhaustionPut = true
        }

        // Detección de Impulso Violento / Momentum Acelerado (Falling Knives / Rocket Pumps)
        // 3 velas consecutivas del mismo color donde los cuerpos no decrecen o no hay rechazo (peligro para contra-tendencia)
        var hasStrongMomentumDown = false
        var hasStrongMomentumUp = false
        if (candleList.size >= 3) {
            val c0 = candleList[0]
            val c1 = candleList[1]
            val c2 = candleList[2]
            val allRed = c0.type == CandleType.RED && c1.type == CandleType.RED && c2.type == CandleType.RED
            val allGreen = c0.type == CandleType.GREEN && c1.type == CandleType.GREEN && c2.type == CandleType.GREEN
            val avgHeight = (c0.bodyHeight + c1.bodyHeight + c2.bodyHeight) / 3f
            if (allRed && (c0.bodyHeight >= c1.bodyHeight * 0.85f || avgHeight >= 18f) && c0.bottomWickRatio < 0.35f) {
                hasStrongMomentumDown = true
            }
            if (allGreen && (c0.bodyHeight >= c1.bodyHeight * 0.85f || avgHeight >= 18f) && c0.topWickRatio < 0.35f) {
                hasStrongMomentumUp = true
            }
        }

        val isHammer = lastCandle != null && (lastCandle.bottomWickRatio >= 0.50f && lastCandle.bodyHeight < lastCandle.totalHeight * 0.35f)
        val isEngulfingGeneral = candleList.size >= 2 && candleList[0].type != candleList[1].type && candleList[0].bodyHeight > candleList[1].bodyHeight * 1.15f

        // 4. Patrón Envolvente en Soporte / Resistencia (Cuerpo >= 1.3x cuerpo previo en zona S/R)
        var isEngulfingCall = false
        var isEngulfingPut = false
        if (candleList.size >= 2) {
            val curr = candleList[0]
            val prev = candleList[1]
            if (curr.type == CandleType.GREEN && prev.type == CandleType.RED) {
                val isBodyLarger = curr.bodyHeight >= prev.bodyHeight * 1.30f || (curr.bodyHeight >= prev.bodyHeight * 1.15f && curr.bodyTopY <= prev.bodyTopY)
                val closesAbovePrev = curr.bodyTopY < prev.bodyBottomY
                val isAtSupport = touchesSupport || Math.abs(curr.bottomY - effectiveSupportY) <= 30f || Math.abs(curr.bottomY - finalSupportY) <= 30f || supportLinesY.any { Math.abs(curr.bottomY - it) <= 30f }
                if (isBodyLarger && closesAbovePrev && isAtSupport) {
                    isEngulfingCall = true
                }
            } else if (curr.type == CandleType.RED && prev.type == CandleType.GREEN) {
                val isBodyLarger = curr.bodyHeight >= prev.bodyHeight * 1.30f || (curr.bodyHeight >= prev.bodyHeight * 1.15f && curr.bodyBottomY >= prev.bodyBottomY)
                val closesBelowPrev = curr.bodyBottomY > prev.bodyTopY
                val isAtResistance = touchesResistance || Math.abs(curr.topY - effectiveResistanceY) <= 30f || Math.abs(curr.topY - finalResistanceY) <= 30f || resistanceLinesY.any { Math.abs(curr.topY - it) <= 30f }
                if (isBodyLarger && closesBelowPrev && isAtResistance) {
                    isEngulfingPut = true
                }
            }
        }

        // 5. Patrón Falso Rompimiento / Trampa Institucional (Mecha perfora >= 8px y cuerpo regresa)
        var isFalseBreakoutCall = false
        var isFalseBreakoutPut = false
        if (lastCandle != null) {
            val targetSupport = effectiveSupportY
            val targetResistance = effectiveResistanceY

            // Trampa bajista en Soporte: mecha perfora soporte >= 6px pero cuerpo cierra por encima
            val penetratesSupport = lastCandle.bottomY >= targetSupport + 6f
            val retreatsAboveSupport = lastCandle.bodyBottomY <= targetSupport + 6f
            val hasRejectionRatio = lastCandle.bottomWickRatio >= 0.25f || lastCandle.type == CandleType.GREEN

            if (penetratesSupport && retreatsAboveSupport && hasRejectionRatio) {
                isFalseBreakoutCall = true
            }

            // Trampa alcista en Resistencia: mecha perfora resistencia >= 6px pero cuerpo cierra por debajo
            val penetratesResistance = lastCandle.topY <= targetResistance - 6f
            val retreatsBelowResistance = lastCandle.bodyTopY >= targetResistance - 6f
            val hasTopRejectionRatio = lastCandle.topWickRatio >= 0.25f || lastCandle.type == CandleType.RED

            if (penetratesResistance && retreatsBelowResistance && hasTopRejectionRatio) {
                isFalseBreakoutPut = true
            }
        }

        // 6. Fuerza de Señal (Termómetro % CALL vs % PUT)
        var callScore = 50
        var putScore = 50

        if (trend == TrendDirection.UPTREND) callScore += 15 else if (trend == TrendDirection.DOWNTREND) putScore += 15
        if (touchesSupport) callScore += 25
        if (touchesResistance) putScore += 25
        if (hasBottomRejection) callScore += 20
        if (hasTopRejection) putScore += 20
        if (isRejectionCall) callScore += 15
        if (isRejectionPut) putScore += 15
        if (isChoqueCall) callScore += 20
        if (isChoquePut) putScore += 20
        if (isExhaustionCall) callScore += 25
        if (isExhaustionPut) putScore += 25
        if (isEngulfingCall) callScore += 30
        if (isEngulfingPut) putScore += 30
        if (isFalseBreakoutCall) callScore += 35
        if (isFalseBreakoutPut) putScore += 35

        val totalScore = (callScore + putScore).coerceAtLeast(1)
        val callPct = ((callScore.toFloat() / totalScore) * 100).toInt().coerceIn(10, 90)
        val putPct = 100 - callPct

        // 7. Filtro Anti-Mercado Lateral (Sideways, Dojis & Whipsaw Alternante)
        val recentCandles = candleList.take(10)

        // Detección de Chop / Alternancia de Velas (Whipsaw: ej. V-R-V-R)
        val isAlternatingChop = if (candleList.size >= 4) {
            val last4 = candleList.take(4)
            val alternatingColors = (last4[0].type != last4[1].type && last4[1].type != last4[2].type && last4[2].type != last4[3].type)
            val microDisplacement = Math.abs(last4.first().bodyTopY - last4.last().bodyTopY)
            alternatingColors && microDisplacement < (chartHeight * 0.025f)
        } else false

        val isSidewaysByCandles = if (recentCandles.size >= 4) {
            val avgBodyHeight = recentCandles.map { it.bodyHeight }.average()
            val dojiCount = recentCandles.count {
                it.type == CandleType.DOJI || it.bodyHeight <= 4f || (it.bodyHeight <= it.totalHeight * 0.10f && it.totalHeight >= 8f)
            }
            val dojiRatio = dojiCount.toFloat() / recentCandles.size
            avgBodyHeight < 15.0 || dojiRatio >= 0.50f || isAlternatingChop
        } else false

        // Filtro Cuantitativo de Consolidación Estrecha (tradingview-quantitative)
        val isConsolidationTight = if (recentCandles.size >= 4 && trend == TrendDirection.SIDEWAYS) {
            val sample = recentCandles.take(5)
            val avgBodyHeight = sample.map { it.bodyHeight }.average()
            val highestY = sample.minOf { it.topY }
            val lowestY = sample.maxOf { it.bottomY }
            val rangeHeight = lowestY - highestY
            avgBodyHeight < 12.0 || rangeHeight < (chartHeight * 0.035f) || isAlternatingChop
        } else false

        // En tendencias direccionales confirmadas (UPTREND o DOWNTREND), el mercado NO es lateral
        val isSideways = if (trend != TrendDirection.SIDEWAYS) {
            false
        } else {
            isSidewaysByCandles || isConsolidationTight || isAlternatingChop || (candleList.size >= 4 && Math.abs(callPct - putPct) < 18)
        }

        // Confluencia Multi-Factor Cuantitativa (0-100%)
        var confCall = 40
        var confPut = 40
        if (trend == TrendDirection.UPTREND) confCall += 25
        if (trend == TrendDirection.DOWNTREND) confPut += 25
        if (touchesSupport) confCall += 25
        if (touchesResistance) confPut += 25
        if (isFalseBreakoutCall) confCall += 35 else if (isEngulfingCall) confCall += 30 else if (isRejectionCall || hasBottomRejection) confCall += 20
        if (isFalseBreakoutPut) confPut += 35 else if (isEngulfingPut) confPut += 30 else if (isRejectionPut || hasTopRejection) confPut += 20
        if (candleTypes.firstOrNull() == CandleType.GREEN) confCall += 10
        if (candleTypes.firstOrNull() == CandleType.RED) confPut += 10

        if (isSideways || isConsolidationTight) {
            confCall = (confCall * 0.6f).toInt()
            confPut = (confPut * 0.6f).toInt()
        }
        val finalConfCall = confCall.coerceIn(0, 100)
        val finalConfPut = confPut.coerceIn(0, 100)

        // Micro-Sincronización Reloj Sniper Estricto (00:56-00:59 o 00:00-00:02 anticipado 2s por delay físico)
        val candleSecond = ((System.currentTimeMillis() / 1000) % 60).toInt()
        val isSniperTimingWindow = candleSecond in 56..59 || candleSecond in 0..2
        val isLateTimingForbidden = candleSecond in 3..55

        // Sniping de Mejor Strike (Pullback / Testeo en nivel clave)
        val targetSupport = if (supportLinesY.isNotEmpty()) effectiveSupportY else finalSupportY
        val targetResistance = if (resistanceLinesY.isNotEmpty()) effectiveResistanceY else finalResistanceY
        val isPullbackSniperCall = (touchesSupport || Math.abs(latestPriceY - targetSupport) <= threshold || (isRejectionCall && Math.abs(latestPriceY - targetSupport) <= threshold * 1.4f)) && !isSideways && !isConsolidationTight
        val isPullbackSniperPut = (touchesResistance || Math.abs(latestPriceY - targetResistance) <= threshold || (isRejectionPut && Math.abs(latestPriceY - targetResistance) <= threshold * 1.4f)) && !isSideways && !isConsolidationTight

        val gCount = candleTypes.count { it == CandleType.GREEN }
        val rCount = candleTypes.count { it == CandleType.RED }
        val orientStr = if (isLandscape) "Horiz" else "Vert"
        val diag = "[$orientStr] Velas: ${candleTypes.size} (V:$gCount R:$rCount) | Racha: $streakBadge | ⏱ ${candleSecond}s"

        // Métricas de Viewport y desplazamiento de gráfico
        val latestCandleX = candleList.firstOrNull()?.x ?: ((startX + endX) * 0.5f)
        val chartWidth = (endX - startX).coerceAtLeast(10f)
        val isChartOffCenterRight = candleList.isNotEmpty() && latestCandleX > (startX + chartWidth * 0.88f)
        val isChartOffCenterLeft = candleList.isNotEmpty() && latestCandleX < (startX + chartWidth * 0.45f)
        val isPriceNearBottom = latestPriceY > (endY - chartHeight * 0.08f)
        val isPriceNearTop = latestPriceY < (startY + chartHeight * 0.08f)

        // Ratios relativos al canal S/R y detección de zonas de proximidad con niveles efectivos actualizados
        val channelHeight = Math.abs(effectiveSupportY - effectiveResistanceY).coerceAtLeast(1f)
        val distSupportRatio = (Math.abs(latestPriceY - effectiveSupportY) / channelHeight).coerceIn(0f, 1f)
        val distResistanceRatio = (Math.abs(latestPriceY - effectiveResistanceY) / channelHeight).coerceIn(0f, 1f)
        val isNearSupport = touchesSupport || distSupportRatio < 0.15f || Math.abs(latestPriceY - effectiveSupportY) <= threshold
        val isNearResistance = touchesResistance || distResistanceRatio < 0.15f || Math.abs(latestPriceY - effectiveResistanceY) <= threshold

        return VisionAnalysisResult(
            currentPriceY = latestPriceY,
            highestPoint = highPoint,
            lowestPoint = lowPoint,
            trend = trend,
            lastCandles = candleTypes,
            candleList = candleList,
            consecutiveCount = consecutive,
            streakBadge = streakBadge,
            signalPowerCall = callPct,
            signalPowerPut = putPct,
            isMarketSideways = isSideways,
            isHammer = isHammer,
            isEngulfing = isEngulfingGeneral,
            isEngulfingCall = isEngulfingCall,
            isEngulfingPut = isEngulfingPut,
            isFalseBreakoutCall = isFalseBreakoutCall,
            isFalseBreakoutPut = isFalseBreakoutPut,
            isRejectionCall = isRejectionCall,
            isRejectionPut = isRejectionPut,
            touchesSupport = touchesSupport,
            touchesResistance = touchesResistance,
            hasTopRejectionWick = hasTopRejection,
            hasBottomRejectionWick = hasBottomRejection,
            isChoqueCall = isChoqueCall,
            isChoquePut = isChoquePut,
            isChoquePullbackCall = isChoqueCall,
            isChoquePullbackPut = isChoquePut,
            is3VelasCall = isExhaustionCall,
            is3VelasPut = isExhaustionPut,
            isExhaustion3CandlesCall = isExhaustionCall,
            isExhaustion3CandlesPut = isExhaustionPut,
            dynamicResistanceY = effectiveResistanceY,
            dynamicSupportY = effectiveSupportY,
            greenPixelsDetected = totalGreenPixels,
            redPixelsDetected = totalRedPixels,
            diagnosticSummary = diag,
            candleSecond = candleSecond,
            isSniperTimingWindow = isSniperTimingWindow,
            isLateTimingForbidden = isLateTimingForbidden,
            isPullbackSniperCall = isPullbackSniperCall,
            isPullbackSniperPut = isPullbackSniperPut,
            isConsolidationTight = isConsolidationTight,
            confluenceScoreCall = finalConfCall,
            confluenceScorePut = finalConfPut,
            latestCandleX = latestCandleX,
            isChartOffCenterRight = isChartOffCenterRight,
            isChartOffCenterLeft = isChartOffCenterLeft,
            isPriceNearBottom = isPriceNearBottom,
            isPriceNearTop = isPriceNearTop,
            hasStrongMomentumDown = hasStrongMomentumDown,
            hasStrongMomentumUp = hasStrongMomentumUp,
            distanceToSupportRatio = distSupportRatio,
            distanceToResistanceRatio = distResistanceRatio,
            isNearSupportZone = isNearSupport,
            isNearResistanceZone = isNearResistance
        )
    }

    /**
     * Analiza el gráfico en Binomo / Brokers (Tema Oscuro en Horizontal o Vertical).
     * Delimita con precisión milimétrica la zona de velas reales excluyendo cabeceras, pestañas y toolbars.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>,
        context: Context? = null,
        debugModeEnabled: Boolean = false
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        val rotation = if (context != null) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try { context.display?.rotation } catch (e: Exception) { null } ?: wm?.defaultDisplay?.rotation
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation
            }
        } else null

        val isLandscapeDisplay = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
        val isLandscape = w > h || isLandscapeDisplay

        // Delimitación estricta de la zona de velas reales:
        val startX: Int
        val endX: Int
        val startY: Int
        val endY: Int

        if (isLandscape) {
            // Horizontal (Landscape 2400x1080 / 2712x1220):
            // - X: 6% a 74% (excluye panel de botones Sube/Baja a la derecha en X > 75%)
            // - Y: 20% a 76% (excluye saldo/tabs superiores Y < 20% y barra de tiempo/herramientas Y > 76%)
            startX = (w * 0.06f).toInt().coerceAtLeast(0)
            endX = (w * 0.74f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.20f).toInt().coerceAtLeast(0)
            endY = (h * 0.76f).toInt().coerceAtMost(h - 1)
        } else {
            // Vertical (Portrait 1080x2400 / 1220x2712):
            // - X: 5% a 78% (excluye columna de cotización / precios y badges de compra a la derecha)
            // - Y: 19% a 72% (cubre velas completas excluyendo saldo/tabs superiores y controles inferiores)
            startX = (w * 0.05f).toInt().coerceAtLeast(0)
            endX = (w * 0.78f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.19f).toInt().coerceAtLeast(0)
            endY = (h * 0.72f).toInt().coerceAtMost(h - 1)
        }

        var totalGreenPixels = 0
        var totalRedPixels = 0

        // Paso fino de escaneo (3px a 5px) para capturar todas las velas delgadas de Binomo
        val stepX = ((endX - startX) / 160).coerceIn(3, 5)
        val candleList = mutableListOf<CandleData>()

        // Analizar columnas de derecha (más reciente) a izquierda (más antigua)
        for (x in endX downTo startX step stepX) {
            // 1. Paso: Buscar segmentos verticales continuos de color para descartar medias móviles finas o ruido (>= 6px)
            var currentRunColor = 0 // 1: Verde, 2: Rojo, 0: Ninguno
            var runStartY = -1
            var runCount = 0

            var bestRunColor = 0
            var bestRunStartY = -1
            var bestRunEndY = -1
            var bestRunLength = 0

            for (y in startY..endY step 2) {
                // FILTRO ANTI-OVERLAY FINO: Solo ignorar la línea exacta (±2px) para no perder mechas ni toques en niveles
                if (supportLinesY.any { Math.abs(y - it) <= 2f } || resistanceLinesY.any { Math.abs(y - it) <= 2f }) {
                    continue
                }

                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Descartar fondo oscuro
                if (value < 0.18f || (sat < 0.16f && value < 0.60f)) {
                    if (runCount > 0) {
                        if (runCount > bestRunLength) {
                            bestRunLength = runCount
                            bestRunColor = currentRunColor
                            bestRunStartY = runStartY
                            bestRunEndY = y - 2
                        }
                        runCount = 0
                        currentRunColor = 0
                    }
                    continue
                }

                var pixelColor = 0
                // Verde: Hue 65° a 180° (#00E676 / #22C55E / etc)
                if (hue in 65f..180f && sat > 0.16f && value > 0.18f) {
                    pixelColor = 1
                    totalGreenPixels++
                }
                // Rojo: Hue 330° a 360° y 0° a 35° (#FF5252 / #EF4444 / etc)
                else if ((hue >= 330f || hue <= 35f) && sat > 0.16f && value > 0.18f) {
                    pixelColor = 2
                    totalRedPixels++
                }

                if (pixelColor != 0) {
                    if (currentRunColor == pixelColor) {
                        runCount++
                    } else {
                        if (runCount > bestRunLength) {
                            bestRunLength = runCount
                            bestRunColor = currentRunColor
                            bestRunStartY = runStartY
                            bestRunEndY = y - 2
                        }
                        currentRunColor = pixelColor
                        runStartY = y
                        runCount = 1
                    }
                } else {
                    if (runCount > 0) {
                        if (runCount > bestRunLength) {
                            bestRunLength = runCount
                            bestRunColor = currentRunColor
                            bestRunStartY = runStartY
                            bestRunEndY = y - 2
                        }
                        runCount = 0
                        currentRunColor = 0
                    }
                }
            }

            if (runCount > bestRunLength) {
                bestRunLength = runCount
                bestRunColor = currentRunColor
                bestRunStartY = runStartY
                bestRunEndY = endY
            }

            // Validar si la columna contiene un cuerpo de vela real continuo (mínimo 3 muestras = 6px)
            if (isValidContiguousRun(bestRunLength, 2) && bestRunStartY > 0 && bestRunEndY > bestRunStartY) {
                val isGreen = bestRunColor == 1
                val type = if (isGreen) CandleType.GREEN else CandleType.RED

                val bodyTop = bestRunStartY.toFloat()
                val bodyBottom = bestRunEndY.toFloat()

                // 2. Paso: Buscar mechas del mismo color o mecha clara
                var wickTopY = bodyTop
                var wickBottomY = bodyBottom

                val wickScanTop = (bestRunStartY - 60).coerceAtLeast(startY)
                for (y in bestRunStartY downTo wickScanTop step 2) {
                    if (supportLinesY.any { Math.abs(y - it) <= 2f } || resistanceLinesY.any { Math.abs(y - it) <= 2f }) continue
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val hue = hsvBuffer[0]
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]

                    val isWickPixel = if (isGreen) {
                        (hue in 65f..180f && sat > 0.14f && value > 0.18f) || (value > 0.45f && sat < 0.20f)
                    } else {
                        ((hue >= 330f || hue <= 35f) && sat > 0.14f && value > 0.18f) || (value > 0.45f && sat < 0.20f)
                    }

                    if (isWickPixel) {
                        wickTopY = y.toFloat()
                    } else {
                        break
                    }
                }

                val wickScanBottom = (bestRunEndY + 60).coerceAtMost(endY)
                for (y in bestRunEndY..wickScanBottom step 2) {
                    if (supportLinesY.any { Math.abs(y - it) <= 2f } || resistanceLinesY.any { Math.abs(y - it) <= 2f }) continue
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val hue = hsvBuffer[0]
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]

                    val isWickPixel = if (isGreen) {
                        (hue in 65f..180f && sat > 0.14f && value > 0.18f) || (value > 0.45f && sat < 0.20f)
                    } else {
                        ((hue >= 330f || hue <= 35f) && sat > 0.14f && value > 0.18f) || (value > 0.45f && sat < 0.20f)
                    }

                    if (isWickPixel) {
                        wickBottomY = y.toFloat()
                    } else {
                        break
                    }
                }

                val candleData = createCandle(
                    type = type,
                    x = x.toFloat(),
                    topY = wickTopY,
                    bottomY = wickBottomY,
                    bodyTopY = bodyTop,
                    bodyBottomY = bodyBottom
                )
                candleList.add(candleData)
            }
        }

        // Fusión y agrupamiento horizontal para condensar columnas adyacentes de la misma vela
        val clusteredCandles = clusterAndMergeCandleColumns(candleList, minSpacingPx = stepX * 2.5f)

        // Evaluar patrones técnicos y estrategias Master Trader con las velas reales discretizadas
        val result = evaluateCandlePatterns(
            candleList = clusteredCandles,
            supportLinesY = supportLinesY,
            resistanceLinesY = resistanceLinesY,
            startY = startY.toFloat(),
            endY = endY.toFloat(),
            startX = startX.toFloat(),
            endX = endX.toFloat(),
            totalGreenPixels = totalGreenPixels,
            totalRedPixels = totalRedPixels,
            isLandscape = isLandscape
        )

        // Modo Debug Visual: Guarda captura anotada si está activo
        if (debugModeEnabled && context != null) {
            val scanBounds = RectF(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat())
            val resY = result.dynamicResistanceY
            val supY = result.dynamicSupportY
            DebugVisualizer.saveDebugFrame(
                context, bitmap, result, scanBounds,
                resY, supY, result.currentPriceY
            )
        }

        return result
    }

    /**
     * Detecta visualmente si existe una bandera o marcador de orden activa sobre el gráfico de Binomo.
     * En Binomo, mientras una operación está en curso, aparece una etiqueta o marcador dorado/amarillo
     * o punto distintivo de posición abierta en el gráfico.
     */
    fun hasActiveTradeMarker(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val isLandscape = w > h

        val startX = (w * if (isLandscape) 0.25f else 0.15f).toInt()
        val endX = (w * if (isLandscape) 0.85f else 0.85f).toInt()
        val startY = (h * if (isLandscape) 0.20f else 0.25f).toInt()
        val endY = (h * if (isLandscape) 0.80f else 0.70f).toInt()

        var yellowBadgeCount = 0
        for (x in startX..endX step 2) {
            for (y in startY..endY step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Amarillo / Dorado puro de orden en ejecución de Binomo (Las velas no son amarillas):
                // R > 210, G en 165..240, B < 85
                if (r > 210 && g in 165..240 && b < 85) {
                    yellowBadgeCount++
                    if (yellowBadgeCount >= 15) return true
                }
            }
        }
        return false
    }

    /**
     * Localiza mediante visión en tiempo real el centro exacto del botón SUBE (verde) o BAJA (rojo/coral).
     * Esto evita fallar el clic si las coordenadas calibradas están desfasadas o si el broker se movió.
     */
    fun findBrokerButtonCoordinates(bitmap: Bitmap, isBuy: Boolean): Pair<Float, Float>? {
        val w = bitmap.width
        val h = bitmap.height
        val isLandscape = w > h

        val xMin: Int
        val xMax: Int
        val yMin: Int
        val yMax: Int

        if (isLandscape) {
            // Horizontal: columna derecha
            xMin = (w * 0.78f).toInt().coerceAtLeast(0)
            xMax = (w * 0.98f).toInt().coerceAtMost(w - 1)
            if (isBuy) {
                yMin = (h * 0.64f).toInt().coerceAtLeast(0)
                yMax = (h * 0.83f).toInt().coerceAtMost(h - 1)
            } else {
                yMin = (h * 0.78f).toInt().coerceAtLeast(0)
                yMax = (h * 0.96f).toInt().coerceAtMost(h - 1)
            }
        } else {
            // Vertical: franja inferior exclusiva de los botones de Binomo (centrado limpio en el tercio inferior del botón)
            yMin = (h * 0.89f).toInt().coerceAtLeast(0)
            yMax = (h * 0.95f).toInt().coerceAtMost(h - 1)
            if (isBuy) {
                xMin = (w * 0.08f).toInt().coerceAtLeast(0)
                xMax = (w * 0.42f).toInt().coerceAtMost(w - 1)
            } else {
                xMin = (w * 0.58f).toInt().coerceAtLeast(0)
                xMax = (w * 0.92f).toInt().coerceAtMost(w - 1)
            }
        }

        var sumX = 0L
        var sumY = 0L
        var count = 0
        val step = 4

        for (y in yMin..yMax step step) {
            for (x in xMin..xMax step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (isBuy) {
                    // Botón verde de SUBE (Binomo #10b981 / #00e676 / etc)
                    if (g > 140 && (g - r) > 40 && (g - b) > 10) {
                        sumX += x
                        sumY += y
                        count++
                    }
                } else {
                    // Botón rojo / coral de BAJA (Binomo #ff646c / #ef4444 / etc)
                    if (r > 170 && (r - g) > 50 && (r - b) > 30) {
                        sumX += x
                        sumY += y
                        count++
                    }
                }
            }
        }

        return if (count >= 20) {
            val cx = sumX.toFloat() / count
            val cy = sumY.toFloat() / count
            Log.d("VisionAnalyzer", "Botón ${if (isBuy) "COMPRA" else "VENTA"} detectado por visión en ($cx, $cy) [$count px]")
            Pair(cx, cy)
        } else {
            null
        }
    }

    /**
     * Detección auxiliar de banners (desactivada para priorizar balance real y acción de precio exacta).
     */
    fun detectTradeOutcomeBanner(bitmap: Bitmap): Boolean? {
        return null
    }
}
