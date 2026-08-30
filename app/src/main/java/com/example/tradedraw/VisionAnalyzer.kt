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
     * Analiza el gráfico en Binomo / Brokers (Tema Oscuro en Horizontal o Vertical).
     * Delimita la zona real de velas excluyendo barras de herramientas, botones y encabezados.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height

        // Delimitación precisa de la zona del gráfico de velas:
        // Excluir encabezado superior (20%), botones inferiores (20%) y botones de Sube/Baja a la derecha (22%)
        val startX = (w * 0.08f).toInt().coerceAtLeast(0)
        val endX = (w * 0.78f).toInt().coerceAtMost(w - 1)
        val startY = (h * 0.22f).toInt().coerceAtLeast(0)
        val endY = (h * 0.78f).toInt().coerceAtMost(h - 1)

        var minPriceY = Float.MAX_VALUE // Menor Y = Mayor precio (Resistencia)
        var maxPriceY = Float.MIN_VALUE // Mayor Y = Menor precio (Soporte)
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        var totalGreenPixels = 0
        var totalRedPixels = 0

        val stepX = ((endX - startX) / 28).coerceIn(6, 18)
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        // Analizar columnas de derecha (más reciente) a izquierda (antiguo)
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

                // Descartar fondo oscuro (#131722, #181c27, etc.)
                if (value < 0.22f || (sat < 0.22f && value < 0.65f)) {
                    continue
                }

                // Detección de Verde en velas Binomo (Hue 75° a 170°)
                if (hue in 75f..170f && sat > 0.25f && value > 0.30f) {
                    greenCount++
                    totalGreenPixels++
                    if (firstCandleY == -1) firstCandleY = y
                    lastCandleY = y
                }
                // Detección de Rojo en velas Binomo (Hue 340°-360° o 0°-28°)
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

                // Calcular picos dentro de la zona válida
                if (firstCandleY in startY..endY && firstCandleY.toFloat() < minPriceY) {
                    minPriceY = firstCandleY.toFloat()
                    highestX = x.toFloat()
                }
                if (lastCandleY in startY..endY && lastCandleY.toFloat() > maxPriceY) {
                    maxPriceY = lastCandleY.toFloat()
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

        // Tendencia según la posición de máximos y mínimos
        val trend = if (minPriceY < Float.MAX_VALUE && maxPriceY > Float.MIN_VALUE) {
            if (highestX > lowestX) TrendDirection.UPTREND else TrendDirection.DOWNTREND
        } else {
            TrendDirection.SIDEWAYS
        }

        // Proximidad a soportes y resistencias
        val threshold = 35f
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold }
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold }

        val isHammer = consecutive >= 2 && lastType == CandleType.RED
        val isEngulfing = candleTypes.size >= 2 && candleTypes[0] != candleTypes[1]

        // Asegurar que siempre existan puntos coherentes de Soporte (abajo) y Resistencia (arriba)
        val resistanceY = if (minPriceY < Float.MAX_VALUE) minPriceY else (startY + (endY - startY) * 0.25f)
        val supportY = if (maxPriceY > Float.MIN_VALUE) maxPriceY else (startY + (endY - startY) * 0.75f)

        val highPoint = PointF(if (highestX > 0) highestX else (startX + endX) * 0.5f, resistanceY)
        val lowPoint = PointF(if (lowestX > 0) lowestX else (startX + endX) * 0.5f, supportY)

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

    /**
     * Detecta si en la pantalla apareció el banner de resultado (Win / Loss) de Binomo / Broker.
     * En Binomo, al expirar la operación se muestra un popup / banner con el resultado:
     * - Ganancia: Banner / Texto verde brillante (+$$$)
     * - Pérdida: Banner / Texto rojo ($0.00 / pérdida)
     */
    fun detectTradeOutcome(bitmap: Bitmap): TradeOutcome? {
        val w = bitmap.width
        val h = bitmap.height

        // Zona central y lateral derecha donde aparecen los resultados de expiración
        val startX = (w * 0.30f).toInt().coerceAtLeast(0)
        val endX = (w * 0.95f).toInt().coerceAtMost(w - 1)
        val startY = (h * 0.15f).toInt().coerceAtLeast(0)
        val endY = (h * 0.85f).toInt().coerceAtMost(h - 1)

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

        // Umbral para confirmar detección de banner de resultado
        return when {
            greenBannerPixels >= 25 -> TradeOutcome.WIN
            redBannerPixels >= 25 -> TradeOutcome.LOSS
            else -> null
        }
    }
}
