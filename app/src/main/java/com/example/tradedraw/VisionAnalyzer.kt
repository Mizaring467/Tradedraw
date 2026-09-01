package com.example.tradedraw

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF

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
     * Delimita con precisión la zona de velas reales excluyendo textos, pestañas y cuadrículas.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        val isLandscape = w > h

        // Delimitación estricta de la zona de velas según broker
        var startX: Int
        var endX: Int
        var startY: Int
        var endY: Int

        val broker = BrokerDetector.currentBroker
        if (broker == Broker.IQ_OPTION) {
            // IQ Option often has charts filling more space and different UI layout
            if (isLandscape) {
                startX = (w * 0.05f).toInt().coerceAtLeast(0)
                endX = (w * 0.80f).toInt().coerceAtMost(w - 1)
                startY = (h * 0.15f).toInt().coerceAtLeast(0)
                endY = (h * 0.85f).toInt().coerceAtMost(h - 1)
            } else {
                startX = (w * 0.05f).toInt().coerceAtLeast(0)
                endX = (w * 0.95f).toInt().coerceAtMost(w - 1)
                startY = (h * 0.20f).toInt().coerceAtLeast(0)
                endY = (h * 0.75f).toInt().coerceAtMost(h - 1)
            }
        } else { // Binomo, Quotex, PocketOption or Unknown default
            if (isLandscape) {
                // Horizontal: Gráfico a la izquierda/centro (X: 8% a 76%), excluyendo botones Sube/Baja a la derecha (X > 78%), tabs y toolbar inferior
                startX = (w * 0.08f).toInt().coerceAtLeast(0)
                endX = (w * 0.76f).toInt().coerceAtMost(w - 1)
                startY = (h * 0.22f).toInt().coerceAtLeast(0)
                endY = (h * 0.80f).toInt().coerceAtMost(h - 1)
            } else {
                // Vertical: Gráfico en la zona media (Y: 28% a 65%), excluyendo saldo/tabs arriba (Y < 28%) e indicadores/botones abajo (Y > 66%)
                startX = (w * 0.06f).toInt().coerceAtLeast(0)
                endX = (w * 0.94f).toInt().coerceAtMost(w - 1)
                startY = (h * 0.28f).toInt().coerceAtLeast(0)
                endY = (h * 0.65f).toInt().coerceAtMost(h - 1)
            }
        }

        var minPriceY = Float.MAX_VALUE // Menor Y = Mayor precio (Resistencia)
        var maxPriceY = Float.MIN_VALUE // Mayor Y = Menor precio (Soporte)
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        var totalGreenPixels = 0
        var totalRedPixels = 0

        val stepX = ((endX - startX) / 32).coerceIn(6, 20)
        val candleList = mutableListOf<CandleData>()
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        // Analizar columnas de derecha (más reciente) a izquierda (más antigua)
        for (x in endX downTo startX step stepX) {
            var greenCount = 0
            var redCount = 0
            var bodyFirstY = -1
            var bodyLastY = -1

            // 1. Paso: Encontrar cuerpo de la vela (Verde o Rojo sólido)
            for (y in startY..endY step 3) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Descartar fondo oscuro
                if (value < 0.24f || (sat < 0.24f && value < 0.70f)) {
                    continue
                }

                // Detección de Verde en velas Binomo (Hue 75° a 170°)
                if (hue in 75f..170f && sat > 0.28f && value > 0.32f) {
                    greenCount++
                    totalGreenPixels++
                    if (bodyFirstY == -1) bodyFirstY = y
                    bodyLastY = y
                }
                // Detección de Rojo en velas Binomo (Hue 340°-360° o 0°-28°)
                else if ((hue >= 340f || hue <= 28f) && sat > 0.28f && value > 0.32f) {
                    redCount++
                    totalRedPixels++
                    if (bodyFirstY == -1) bodyFirstY = y
                    bodyLastY = y
                }
            }

            // Solo si se detectó un cuerpo de vela válido (mínimo 4 muestras)
            if (greenCount >= 4 || redCount >= 4) {
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

                // 2. Paso: Buscar mechas ÚNICAMENTE en la vecindad del cuerpo de la vela (+/- 50px)
                // Esto evita confundir textos de la cabecera o cuadrículas lejanas con mechas
                var wickTopY = bodyTop
                var wickBottomY = bodyBottom

                val wickScanTop = (bodyFirstY - 50).coerceAtLeast(startY)
                for (y in bodyFirstY downTo wickScanTop step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]
                    if (value > 0.35f && (sat > 0.20f || sat < 0.15f)) {
                        wickTopY = y.toFloat()
                    } else if (value < 0.22f) {
                        break
                    }
                }

                val wickScanBottom = (bodyLastY + 50).coerceAtMost(endY)
                for (y in bodyLastY..wickScanBottom step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsvBuffer)
                    val sat = hsvBuffer[1]
                    val value = hsvBuffer[2]
                    if (value > 0.35f && (sat > 0.20f || sat < 0.15f)) {
                        wickBottomY = y.toFloat()
                    } else if (value < 0.22f) {
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

                // Extremos de precios reales basados exclusivamente en velas detectadas
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

        // Proximidad a soportes y resistencias
        val threshold = 35f
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold }
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold }

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

        // S/R calculados estrictamente dentro del rango de velas encontradas
        val resistanceY = if (minPriceY < Float.MAX_VALUE) minPriceY else (startY + (endY - startY) * 0.30f)
        val supportY = if (maxPriceY > Float.MIN_VALUE) maxPriceY else (startY + (endY - startY) * 0.70f)

        val highPoint = PointF(if (highestX > 0) highestX else (startX + endX) * 0.5f, resistanceY)
        val lowPoint = PointF(if (lowestX > 0) lowestX else (startX + endX) * 0.5f, supportY)

        val gCount = candleTypes.count { it == CandleType.GREEN }
        val rCount = candleTypes.count { it == CandleType.RED }
        val orientStr = if (isLandscape) "Horiz" else "Vert"
        val diag = "[$orientStr] Velas: ${candleTypes.size} (V:$gCount R:$rCount) | Racha: $streakBadge"

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
