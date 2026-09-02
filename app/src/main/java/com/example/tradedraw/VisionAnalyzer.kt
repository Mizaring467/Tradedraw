package com.example.tradedraw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF

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

data class VisionAnalysisResult(
    val currentPriceY: Float,
    val highestPoint: PointF?,
    val lowestPoint: PointF?,
    val trend: TrendDirection,
    val lastCandles: List<CandleType>,
    val candleList: List<CandleData> = emptyList(),
    val consecutiveCount: Int,
    val streakBadge: String = "",
    val signalPowerCall: Int = 50,
    val signalPowerPut: Int = 50,
    val isMarketSideways: Boolean = false,
    val isHammer: Boolean = false,
    val isEngulfing: Boolean = false,
    val touchesSupport: Boolean = false,
    val touchesResistance: Boolean = false,
    val hasTopRejectionWick: Boolean = false,
    val hasBottomRejectionWick: Boolean = false,
    val isChoquePullbackCall: Boolean = false,
    val isChoquePullbackPut: Boolean = false,
    val isExhaustion3CandlesCall: Boolean = false,
    val isExhaustion3CandlesPut: Boolean = false,
    val greenPixelsDetected: Int = 0,
    val redPixelsDetected: Int = 0,
    val diagnosticSummary: String = ""
)

class VisionAnalyzer {

    private val hsvBuffer = FloatArray(3)

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
        val isLandscape = w > h

        // Delimitación estricta de la zona de velas reales:
        val startX: Int
        val endX: Int
        val startY: Int
        val endY: Int

        if (isLandscape) {
            // Horizontal (Landscape 2400x1080):
            // - X: 5% a 82% (excluye botones Sube/Baja a la derecha en X > 85%)
            // - Y: 15% a 78% (abarca la totalidad del área de velas de Binomo)
            startX = (w * 0.05f).toInt().coerceAtLeast(0)
            endX = (w * 0.82f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.15f).toInt().coerceAtLeast(0)
            endY = (h * 0.78f).toInt().coerceAtMost(h - 1)
        } else {
            // Vertical (Portrait 1080x2400):
            // - X: 6% a 94%
            // - Y: 28% a 72% (excluye saldo/tabs Y < 28% y botones inferiores Y > 73%)
            startX = (w * 0.06f).toInt().coerceAtLeast(0)
            endX = (w * 0.94f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.28f).toInt().coerceAtLeast(0)
            endY = (h * 0.72f).toInt().coerceAtMost(h - 1)
        }

        var minPriceY = Float.MAX_VALUE // Menor Y = Mayor precio (Resistencia / Techo)
        var maxPriceY = Float.MIN_VALUE // Mayor Y = Menor precio (Soporte / Piso)
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        var totalGreenPixels = 0
        var totalRedPixels = 0

        // Paso fino de escaneo (3px a 5px) para capturar todas las velas delgadas de Binomo
        val stepX = ((endX - startX) / 180).coerceIn(3, 5)
        val candleList = mutableListOf<CandleData>()
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        // Analizar columnas de derecha (más reciente) a izquierda (más antigua)
        for (x in endX downTo startX step stepX) {
            var greenCount = 0
            var redCount = 0
            var bodyFirstY = -1
            var bodyLastY = -1

            // 1. Paso: Buscar cuerpo de vela (Verde o Rojo) con umbrales HSV relajados
            for (y in startY..endY step 2) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Descartar fondo oscuro
                if (value < 0.18f || (sat < 0.18f && value < 0.60f)) {
                    continue
                }

                // Detección de Verde en velas Binomo (Hue 65° a 180°)
                if (hue in 65f..180f && sat > 0.16f && value > 0.18f) {
                    greenCount++
                    totalGreenPixels++
                    if (bodyFirstY == -1) bodyFirstY = y
                    bodyLastY = y
                }
                // Detección de Rojo en velas Binomo (Hue 330°-360° o 0°-35°)
                else if ((hue >= 330f || hue <= 35f) && sat > 0.16f && value > 0.18f) {
                    redCount++
                    totalRedPixels++
                    if (bodyFirstY == -1) bodyFirstY = y
                    bodyLastY = y
                }
            }

            // Validar si la columna contiene una vela real (mínimo 3 muestras)
            if (greenCount >= 3 || redCount >= 3) {
                val type = if (greenCount > redCount * 1.15f) {
                    CandleType.GREEN
                } else if (redCount > greenCount * 1.15f) {
                    CandleType.RED
                } else {
                    CandleType.DOJI
                }
                candleTypes.add(type)

                val bodyTop = bodyFirstY.toFloat()
                val bodyBottom = bodyLastY.toFloat()

                // 2. Paso: Buscar mechas ÚNICAMENTE a 40px alrededor del cuerpo confirmado
                var wickTopY = bodyTop
                var wickBottomY = bodyBottom

                val wickScanTop = (bodyFirstY - 40).coerceAtLeast(startY)
                for (y in bodyFirstY downTo wickScanTop step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]
                    if (value > 0.28f && (sat > 0.15f || sat < 0.15f)) {
                        wickTopY = y.toFloat()
                    } else if (value < 0.18f) {
                        break
                    }
                }

                val wickScanBottom = (bodyLastY + 40).coerceAtMost(endY)
                for (y in bodyLastY..wickScanBottom step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]
                    if (value > 0.28f && (sat > 0.15f || sat < 0.15f)) {
                        wickBottomY = y.toFloat()
                    } else if (value < 0.18f) {
                        break
                    }
                }

                val totalH = (wickBottomY - wickTopY).coerceAtLeast(1f)
                val bodyH = (bodyBottom - bodyTop).coerceAtLeast(1f)
                val topWick = (bodyTop - wickTopY).coerceAtLeast(0f)
                val bottomWick = (wickBottomY - bodyBottom).coerceAtLeast(0f)

                val candleData = CandleData(
                    type = type,
                    x = x.toFloat(),
                    topY = wickTopY,
                    bottomY = wickBottomY,
                    bodyTopY = bodyTop,
                    bodyBottomY = bodyBottom,
                    totalHeight = totalH,
                    bodyHeight = bodyH,
                    topWickRatio = topWick / totalH,
                    bottomWickRatio = bottomWick / totalH
                )
                candleList.add(candleData)

                if (!foundLatestPrice) {
                    latestPriceY = (bodyTop + bodyBottom) / 2f
                    foundLatestPrice = true
                }

                // Extremos basados exclusivamente en velas reales
                if (wickTopY < minPriceY) {
                    minPriceY = wickTopY
                    highestX = x.toFloat()
                }
                if (wickBottomY > maxPriceY) {
                    maxPriceY = wickBottomY
                    lowestX = x.toFloat()
                }
            }
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

        val streakBadge = if (lastType == CandleType.GREEN) "${consecutive}V 🟢" else if (lastType == CandleType.RED) "${consecutive}R 🔴" else "1D ⚪"

        // Tendencia general
        val trend = if (minPriceY < Float.MAX_VALUE && maxPriceY > Float.MIN_VALUE) {
            if (highestX > lowestX) TrendDirection.UPTREND else TrendDirection.DOWNTREND
        } else {
            TrendDirection.SIDEWAYS
        }

        // S/R calculados estrictamente dentro del rango de velas encontradas:
        // Resistencia = Menor Y (Techo de velas)
        // Soporte = Mayor Y (Suelo de velas)
        val rawResistanceY = if (minPriceY < Float.MAX_VALUE) minPriceY else (startY + (endY - startY) * 0.22f)
        val rawSupportY = if (maxPriceY > Float.MIN_VALUE) maxPriceY else (startY + (endY - startY) * 0.78f)

        // Rompimiento dinámico en vivo
        val currentP = latestPriceY
        val adjustedResistanceY = if (currentP < rawResistanceY) currentP else rawResistanceY
        val adjustedSupportY = if (currentP > rawSupportY) currentP else rawSupportY

        // Garantizar separación mínima proporcional para que NUNCA se monten una sobre otra
        val minSep = (endY - startY) * 0.25f
        var finalResistanceY = adjustedResistanceY
        var finalSupportY = adjustedSupportY

        if (finalSupportY <= finalResistanceY + minSep) {
            val mid = (finalResistanceY + finalSupportY) / 2f
            finalResistanceY = (mid - minSep / 2f).coerceAtLeast(startY.toFloat())
            finalSupportY = (mid + minSep / 2f).coerceAtMost(endY.toFloat())
        }

        val highPoint = PointF(if (highestX > 0) highestX else (startX + endX) * 0.5f, finalResistanceY)
        val lowPoint = PointF(if (lowestX > 0) lowestX else (startX + endX) * 0.5f, finalSupportY)

        // Proximidad a soportes y resistencias (manuales o calculados por el bot)
        val threshold = ((endY - startY) * 0.08f).coerceAtLeast(35f)
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold } || (maxPriceY > Float.MIN_VALUE && Math.abs(latestPriceY - finalSupportY) < threshold)
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold } || (minPriceY < Float.MAX_VALUE && Math.abs(latestPriceY - finalResistanceY) < threshold)

        // --- PATRONES MASTER TRADERS ---
        val lastCandle = candleList.firstOrNull()
        val hasTopRejection = lastCandle != null && lastCandle.topWickRatio >= 0.40f
        val hasBottomRejection = lastCandle != null && lastCandle.bottomWickRatio >= 0.40f

        var isChoqueCall = false
        var isChoquePut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0]
            val c1 = candleList[1]
            val c2 = candleList[2]
            if (c2.bodyBottomY > c1.bodyTopY && Math.abs(c0.bottomY - c2.bodyTopY) < threshold) {
                isChoqueCall = true
            }
            if (c2.bodyTopY < c1.bodyBottomY && Math.abs(c0.topY - c2.bodyBottomY) < threshold) {
                isChoquePut = true
            }
        }

        var isExhaustionCall = false
        var isExhaustionPut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0]
            val c1 = candleList[1]
            val c2 = candleList[2]
            val allRed = c0.type == CandleType.RED && c1.type == CandleType.RED && c2.type == CandleType.RED
            val allGreen = c0.type == CandleType.GREEN && c1.type == CandleType.GREEN && c2.type == CandleType.GREEN
            val decayingBodies = c0.bodyHeight <= c1.bodyHeight * 0.90f && c1.bodyHeight <= c2.bodyHeight * 0.95f

            if (allRed && decayingBodies) isExhaustionCall = true
            else if (allGreen && decayingBodies) isExhaustionPut = true
        }

        val isHammer = lastCandle != null && (lastCandle.bottomWickRatio >= 0.50f && lastCandle.bodyHeight < lastCandle.totalHeight * 0.35f)
        val isEngulfing = candleList.size >= 2 && candleList[0].type != candleList[1].type && candleList[0].bodyHeight > candleList[1].bodyHeight * 1.2f

        // Calcular Fuerza de Señal (Termómetro % CALL vs % PUT)
        var callScore = 50
        var putScore = 50

        if (trend == TrendDirection.UPTREND) callScore += 15 else if (trend == TrendDirection.DOWNTREND) putScore += 15
        if (touchesSupport) callScore += 25
        if (touchesResistance) putScore += 25
        if (hasBottomRejection) callScore += 20
        if (hasTopRejection) putScore += 20
        if (isChoqueCall) callScore += 20
        if (isChoquePut) putScore += 20
        if (isExhaustionCall) callScore += 25
        if (isExhaustionPut) putScore += 25

        val totalScore = (callScore + putScore).coerceAtLeast(1)
        val callPct = ((callScore.toFloat() / totalScore) * 100).toInt().coerceIn(10, 90)
        val putPct = 100 - callPct
        val isSideways = Math.abs(callPct - putPct) < 14

        val gCount = candleTypes.count { it == CandleType.GREEN }
        val rCount = candleTypes.count { it == CandleType.RED }
        val orientStr = if (isLandscape) "Horiz" else "Vert"
        val diag = "[$orientStr] Velas: ${candleTypes.size} (V:$gCount R:$rCount) | Racha: $streakBadge"

        val result = VisionAnalysisResult(
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
            isEngulfing = isEngulfing,
            touchesSupport = touchesSupport,
            touchesResistance = touchesResistance,
            hasTopRejectionWick = hasTopRejection,
            hasBottomRejectionWick = hasBottomRejection,
            isChoquePullbackCall = isChoqueCall,
            isChoquePullbackPut = isChoquePut,
            isExhaustion3CandlesCall = isExhaustionCall,
            isExhaustion3CandlesPut = isExhaustionPut,
            greenPixelsDetected = totalGreenPixels,
            redPixelsDetected = totalRedPixels,
            diagnosticSummary = diag
        )

        // Modo Debug Visual: Guarda captura anotada si está activo
        if (debugModeEnabled && context != null) {
            val scanBounds = RectF(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat())
            DebugVisualizer.saveDebugFrame(
                context, bitmap, result, scanBounds,
                finalResistanceY, finalSupportY, latestPriceY
            )
        }

        return result
    }

    /**
     * Detecta si en la pantalla apareció el banner de resultado (Win / Loss) de Binomo / Broker.
     */
    fun detectTradeOutcome(bitmap: Bitmap): TradeOutcome? {
        val w = bitmap.width
        val h = bitmap.height
        val isLandscape = w > h

        val startX: Int
        val endX: Int
        val startY: Int
        val endY: Int

        if (isLandscape) {
            startX = (w * 0.25f).toInt().coerceAtLeast(0)
            endX = (w * 0.95f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.15f).toInt().coerceAtLeast(0)
            endY = (h * 0.85f).toInt().coerceAtMost(h - 1)
        } else {
            startX = (w * 0.10f).toInt().coerceAtLeast(0)
            endX = (w * 0.90f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.20f).toInt().coerceAtLeast(0)
            endY = (h * 0.70f).toInt().coerceAtMost(h - 1)
        }

        var greenBannerPixels = 0
        var redBannerPixels = 0

        for (x in startX..endX step 6) {
            for (y in startY..endY step 6) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Banner verde intenso de Ganancia (Binomo #00e676 o similar)
                if (hue in 90f..160f && sat > 0.60f && value > 0.60f) {
                    greenBannerPixels++
                }
                // Banner rojo intenso de Pérdida (Binomo #ff1744 o similar)
                else if ((hue >= 345f || hue <= 15f) && sat > 0.60f && value > 0.60f) {
                    redBannerPixels++
                }
            }
        }

        return when {
            greenBannerPixels >= 25 -> TradeOutcome.WIN
            redBannerPixels >= 25 -> TradeOutcome.LOSS
            else -> null
        }
    }
}
