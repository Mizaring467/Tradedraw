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
    val isHammer: Boolean,
    val isEngulfing: Boolean,
    val touchesSupport: Boolean,
    val touchesResistance: Boolean,
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
     * Extrae geometría de velas para las estrategias de Acción del Precio de Master Traders.
     */
    fun analyzeChart(
        bitmap: Bitmap,
        supportLinesY: List<Float>,
        resistanceLinesY: List<Float>
    ): VisionAnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        val isLandscape = w > h

        // Delimitación adaptativa de la zona del gráfico de velas
        val startX: Int
        val endX: Int
        val startY: Int
        val endY: Int

        if (isLandscape) {
            // Horizontal: Gráfico a la izquierda/centro (X: 8% a 78%), excluyendo botones Sube/Baja (X > 78%), tabs y barra inferior
            startX = (w * 0.08f).toInt().coerceAtLeast(0)
            endX = (w * 0.78f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.18f).toInt().coerceAtLeast(0)
            endY = (h * 0.78f).toInt().coerceAtMost(h - 1)
        } else {
            // Vertical: Gráfico en la zona superior/media (Y: 14% a 58%)
            startX = (w * 0.05f).toInt().coerceAtLeast(0)
            endX = (w * 0.95f).toInt().coerceAtMost(w - 1)
            startY = (h * 0.14f).toInt().coerceAtLeast(0)
            endY = (h * 0.58f).toInt().coerceAtMost(h - 1)
        }

        var minPriceY = Float.MAX_VALUE // Menor Y = Mayor precio (Resistencia)
        var maxPriceY = Float.MIN_VALUE // Mayor Y = Menor precio (Soporte)
        var highestX = 0f
        var lowestX = 0f
        var latestPriceY = (startY + endY) / 2f

        var totalGreenPixels = 0
        var totalRedPixels = 0

        val stepX = ((endX - startX) / 30).coerceIn(6, 20)
        val candleList = mutableListOf<CandleData>()
        val candleTypes = mutableListOf<CandleType>()
        var foundLatestPrice = false

        // Analizar columnas de derecha (más reciente) a izquierda (más antigua)
        for (x in endX downTo startX step stepX) {
            var greenCount = 0
            var redCount = 0
            var firstCandleY = -1
            var lastCandleY = -1
            var wickTopY = -1
            var wickBottomY = -1

            for (y in startY..endY step 3) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsvBuffer)
                val hue = hsvBuffer[0]
                val sat = hsvBuffer[1]
                val value = hsvBuffer[2]

                // Detección de mechas (líneas finas grises/blancas o colores atenuados)
                if (value > 0.40f && sat < 0.25f) {
                    if (wickTopY == -1) wickTopY = y
                    wickBottomY = y
                }

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
                    if (wickTopY == -1) wickTopY = y
                    wickBottomY = y
                }
                // Detección de Rojo en velas Binomo (Hue 340°-360° o 0°-28°)
                else if ((hue >= 340f || hue <= 28f) && sat > 0.25f && value > 0.30f) {
                    redCount++
                    totalRedPixels++
                    if (firstCandleY == -1) firstCandleY = y
                    lastCandleY = y
                    if (wickTopY == -1) wickTopY = y
                    wickBottomY = y
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

                val bodyTop = firstCandleY.toFloat()
                val bodyBottom = lastCandleY.toFloat()
                val topY = if (wickTopY != -1 && wickTopY < firstCandleY) wickTopY.toFloat() else bodyTop
                val bottomY = if (wickBottomY != -1 && wickBottomY > lastCandleY) wickBottomY.toFloat() else bodyBottom

                val totalH = (bottomY - topY).coerceAtLeast(1f)
                val bodyH = (bodyBottom - bodyTop).coerceAtLeast(1f)
                val topWick = (bodyTop - topY).coerceAtLeast(0f)
                val bottomWick = (bottomY - bodyBottom).coerceAtLeast(0f)

                val candleData = CandleData(
                    type = type,
                    x = x.toFloat(),
                    topY = topY,
                    bottomY = bottomY,
                    bodyTopY = bodyTop,
                    bodyBottomY = bodyBottom,
                    totalHeight = totalH,
                    bodyHeight = bodyH,
                    topWickRatio = topWick / totalH,
                    bottomWickRatio = bottomWick / totalH
                )
                candleList.add(candleData)

                if (!foundLatestPrice && lastCandleY != -1) {
                    latestPriceY = ((firstCandleY + lastCandleY) / 2f)
                    foundLatestPrice = true
                }

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

        // Tendencia general
        val trend = if (minPriceY < Float.MAX_VALUE && maxPriceY > Float.MIN_VALUE) {
            if (highestX > lowestX) TrendDirection.UPTREND else TrendDirection.DOWNTREND
        } else {
            TrendDirection.SIDEWAYS
        }

        // Proximidad a soportes y resistencias
        val threshold = 38f
        val touchesSupport = supportLinesY.any { Math.abs(latestPriceY - it) < threshold }
        val touchesResistance = resistanceLinesY.any { Math.abs(latestPriceY - it) < threshold }

        // --- PATRONES MASTER TRADERS ---
        val lastCandle = candleList.firstOrNull()

        // 1. Mecha de rechazo: Mecha >= 40% del rango total de la vela
        val hasTopRejection = lastCandle != null && lastCandle.topWickRatio >= 0.40f
        val hasBottomRejection = lastCandle != null && lastCandle.bottomWickRatio >= 0.40f

        // 2. Choque y Pullback:
        // Si el precio actual está testeando el nivel roto de las velas previas
        var isChoqueCall = false
        var isChoquePut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0] // vela actual
            val c1 = candleList[1] // vela anterior
            val c2 = candleList[2] // vela previa

            // Breakout alcista con retest a soporte roto
            if (c2.bodyBottomY > c1.bodyTopY && Math.abs(c0.bottomY - c2.bodyTopY) < threshold) {
                isChoqueCall = true
            }
            // Breakout bajista con retest a resistencia rota
            if (c2.bodyTopY < c1.bodyBottomY && Math.abs(c0.topY - c2.bodyBottomY) < threshold) {
                isChoquePut = true
            }
        }

        // 3. Patrón 3 Velas y Agotamiento:
        // 3 velas consecutivas del mismo color con cuerpos decrecientes (cuerpo[0] < cuerpo[1] < cuerpo[2])
        var isExhaustionCall = false
        var isExhaustionPut = false
        if (candleList.size >= 3) {
            val c0 = candleList[0]
            val c1 = candleList[1]
            val c2 = candleList[2]
            val allRed = c0.type == CandleType.RED && c1.type == CandleType.RED && c2.type == CandleType.RED
            val allGreen = c0.type == CandleType.GREEN && c1.type == CandleType.GREEN && c2.type == CandleType.GREEN
            val decayingBodies = c0.bodyHeight <= c1.bodyHeight * 0.90f && c1.bodyHeight <= c2.bodyHeight * 0.95f

            if (allRed && decayingBodies) {
                isExhaustionCall = true // 3 rojas agotadas -> COMPRA en 4ª vela
            } else if (allGreen && decayingBodies) {
                isExhaustionPut = true // 3 verdes agotadas -> VENTA en 4ª vela
            }
        }

        val isHammer = lastCandle != null && (lastCandle.bottomWickRatio >= 0.50f && lastCandle.bodyHeight < lastCandle.totalHeight * 0.35f)
        val isEngulfing = candleList.size >= 2 && candleList[0].type != candleList[1].type && candleList[0].bodyHeight > candleList[1].bodyHeight * 1.2f

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

        val orientStr = if (isLandscape) "Horiz" else "Vert"
        val diag = "[$orientStr] Velas: ${candleTypes.size} (V:$gCount R:$rCount) | $trendStr | Racha: $consecutive ${lastType.name}"

        return VisionAnalysisResult(
            currentPriceY = latestPriceY,
            highestPoint = highPoint,
            lowestPoint = lowPoint,
            trend = trend,
            lastCandles = candleTypes,
            candleList = candleList,
            consecutiveCount = consecutive,
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
