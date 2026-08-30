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

data class VisionAnalysisResult(
    val currentPriceY: Float,
    val highestPoint: PointF?,
    val lowestPoint: PointF?,
    val trend: TrendDirection,
    val lastCandles: List<CandleType>,
    val consecutiveCount: Int,
    val isHammer: Boolean,
    val isEngulfing: Boolean,
    val touchesSupport: Boolean,
    val touchesResistance: Boolean,
    val greenPixelsDetected: Int = 0,
    val redPixelsDetected: Int = 0,
    val diagnosticSummary: String = ""
)

class VisionAnalyzer {

    private val hsvBuffer = FloatArray(3)

    /**
     * Analiza el frame capturado del broker (Binomo / Quotex / TradingView en Tema Oscuro)
     * para extraer datos de acción del precio usando segmentación HSV robusta.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height

        // Zona del gráfico: ignorar barra de estado superior (12%) y botones inferiores (12%), y barra lateral derecha (8%)
        val startX = (w * 0.10f).toInt().coerceAtLeast(0)
        val endX = (w * 0.92f).toInt().coerceAtMost(w - 1)
        val startY = (h * 0.12f).toInt().coerceAtLeast(0)
        val endY = (h * 0.88f).toInt().coerceAtMost(h - 1)

        var minPriceY = Float.MAX_VALUE
        var maxPriceY = Float.MIN_VALUE
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        var totalGreenPixels = 0
        var totalRedPixels = 0

        // Muestreo de columnas de derecha a izquierda
        val stepX = ((endX - startX) / 32).coerceIn(6, 20)
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        for (x in endX downTo startX step stepX) {
            var greenCount = 0
            var redCount = 0
            var firstCandleY = -1
            var lastCandleY = -1

            for (y in startY..endY step 3) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Descartar fondo oscuro / grisáceo
                if (value < 0.22f || (sat < 0.20f && value < 0.70f)) {
                    continue
                }

                // Detección de Verde en Binomo / Brokers (Hue 75° a 170°)
                if (hue in 75f..170f && sat > 0.25f && value > 0.30f) {
                    greenCount++
                    totalGreenPixels++
                    if (firstCandleY == -1) firstCandleY = y
                    lastCandleY = y
                }
                // Detección de Rojo en Binomo / Brokers (Hue 340°-360° o 0°-28°)
                else if ((hue >= 340f || hue <= 28f) && sat > 0.25f && value > 0.30f) {
                    redCount++
                    totalRedPixels++
                    if (firstCandleY == -1) firstCandleY = y
                    lastCandleY = y
                }
            }

            if (greenCount >= 3 || redCount >= 3) {
                val type = if (greenCount > redCount * 1.15f) {
                    CandleType.GREEN
                } else if (redCount > greenCount * 1.15f) {
                    CandleType.RED
                } else {
                    CandleType.DOJI
                }
                candleTypes.add(type)

                if (!foundLatestPrice && lastCandleY != -1) {
                    latestPriceY = ((firstCandleY + lastCandleY) / 2f)
                    foundLatestPrice = true
                }

                if (firstCandleY != -1 && firstCandleY.toFloat() < minPriceY) {
                    minPriceY = firstCandleY.toFloat()
                    highestX = x.toFloat()
                }
                if (lastCandleY != -1 && lastCandleY.toFloat() > maxPriceY) {
                    maxPriceY = lastCandleY.toFloat()
                    lowestX = x.toFloat()
                }
            }
        }

        // Determinar rachas consecutivas de la última vela hacia atrás
        val lastType = candleTypes.firstOrNull() ?: CandleType.DOJI
        var consecutive = 0
        for (c in candleTypes) {
            if (c == lastType && c != CandleType.DOJI) {
                consecutive++
            } else {
                break
            }
        }

        // Detección de tendencia general
        val trend = if (minPriceY < Float.MAX_VALUE && maxPriceY > Float.MIN_VALUE) {
            if (highestX > lowestX) TrendDirection.UPTREND else TrendDirection.DOWNTREND
        } else {
            TrendDirection.SIDEWAYS
        }

        // Colisión con soportes y resistencias trazados
        val threshold = 40f
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold }
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold }

        // Patrón martillo básico o envolvente
        val isHammer = consecutive >= 2 && lastType == CandleType.RED
        val isEngulfing = candleTypes.size >= 2 && candleTypes[0] != candleTypes[1]

        val highPoint = if (minPriceY < Float.MAX_VALUE) PointF(highestX, minPriceY) else null
        val lowPoint = if (maxPriceY > Float.MIN_VALUE) PointF(lowestX, maxPriceY) else null

        val gCount = candleTypes.count { it == CandleType.GREEN }
        val rCount = candleTypes.count { it == CandleType.RED }
        val trendStr = when (trend) {
            TrendDirection.UPTREND -> "Alza ↗"
            TrendDirection.DOWNTREND -> "Baja ↘"
            TrendDirection.SIDEWAYS -> "Lateral →"
        }

        val diag = "Velas: ${candleTypes.size} (V:$gCount R:$rCount) | $trendStr | Racha: $consecutive ${lastType.name}"

        return VisionAnalysisResult(
            currentPriceY = latestPriceY,
            highestPoint = highPoint,
            lowestPoint = lowPoint,
            trend = trend,
            lastCandles = candleTypes,
            consecutiveCount = consecutive,
            isHammer = isHammer,
            isEngulfing = isEngulfing,
            touchesSupport = touchesSupport,
            touchesResistance = touchesResistance,
            greenPixelsDetected = totalGreenPixels,
            redPixelsDetected = totalRedPixels,
            diagnosticSummary = diag
        )
    }
}
