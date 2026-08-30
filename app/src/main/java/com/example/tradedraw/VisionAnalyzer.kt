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
    val touchesResistance: Boolean
)

class VisionAnalyzer {

    /**
     * Analiza el frame capturado del broker para extraer datos de acción del precio.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height

        // Zona del gráfico: ignorar márgenes superior (20%) e inferior (15%), y barras de herramientas
        val startX = (w * 0.15f).toInt().coerceAtLeast(0)
        val endX = (w * 0.90f).toInt().coerceAtMost(w - 1)
        val startY = (h * 0.15f).toInt().coerceAtLeast(0)
        val endY = (h * 0.85f).toInt().coerceAtMost(h - 1)

        var minPriceY = Float.MAX_VALUE
        var maxPriceY = Float.MIN_VALUE
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        // Muestreo de columnas de derecha a izquierda para detectar las últimas velas
        val stepX = ((endX - startX) / 24).coerceAtLeast(6)
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        for (x in endX downTo startX step stepX) {
            var greenCount = 0
            var redCount = 0
            var firstY = -1
            var lastY = -1

            for (y in startY..endY step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Detección de color verde de vela (verde predominante sobre rojo/azul)
                if (g > 110 && g > r * 1.25f && g > b * 1.15f) {
                    greenCount++
                    if (firstY == -1) firstY = y
                    lastY = y
                }
                // Detección de color rojo de vela (rojo predominante)
                else if (r > 120 && r > g * 1.30f && r > b * 1.25f) {
                    redCount++
                    if (firstY == -1) firstY = y
                    lastY = y
                }
            }

            if (greenCount > 3 || redCount > 3) {
                val type = if (greenCount > redCount * 1.2f) {
                    CandleType.GREEN
                } else if (redCount > greenCount * 1.2f) {
                    CandleType.RED
                } else {
                    CandleType.DOJI
                }
                candleTypes.add(type)

                if (!foundLatestPrice && lastY != -1) {
                    latestPriceY = ((firstY + lastY) / 2f)
                    foundLatestPrice = true
                }

                if (firstY != -1 && firstY.toFloat() < minPriceY) {
                    minPriceY = firstY.toFloat()
                    highestX = x.toFloat()
                }
                if (lastY != -1 && lastY.toFloat() > maxPriceY) {
                    maxPriceY = lastY.toFloat()
                    lowestX = x.toFloat()
                }
            }
        }

        // Determinar rachas consecutivas
        val lastType = candleTypes.firstOrNull() ?: CandleType.DOJI
        var consecutive = 0
        for (c in candleTypes) {
            if (c == lastType && c != CandleType.DOJI) {
                consecutive++
            } else {
                break
            }
        }

        // Detección de tendencia general según la inclinación de los extremos
        val trend = if (minPriceY < Float.MAX_VALUE && maxPriceY > Float.MIN_VALUE) {
            if (highestX > lowestX) TrendDirection.UPTREND else TrendDirection.DOWNTREND
        } else {
            TrendDirection.SIDEWAYS
        }

        // Colisión con soportes y resistencias trazados
        val threshold = 35f
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold }
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold }

        // Patrón martillo básico o envolvente
        val isHammer = consecutive >= 2 && lastType == CandleType.RED
        val isEngulfing = candleTypes.size >= 2 && candleTypes[0] != candleTypes[1]

        val highPoint = if (minPriceY < Float.MAX_VALUE) PointF(highestX, minPriceY) else null
        val lowPoint = if (maxPriceY > Float.MIN_VALUE) PointF(lowestX, maxPriceY) else null

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
            touchesResistance = touchesResistance
        )
    }
}
