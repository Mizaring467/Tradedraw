package com.example.tradedraw

import android.graphics.Color
import android.graphics.PointF

enum class AutoTradeStrategy {
    SUPPORT_RESISTANCE,
    CANDLE_PATTERNS,
    TREND_FOLLOWING,
    COMBINED
}

class AutoDrawEngine(private val drawingView: CustomDrawingView) {

    /**
     * Dibuja o actualiza automáticamente las herramientas técnicas de TradeDraw
     * en base al análisis de visión actual.
     */
    fun updateTechnicalDrawings(strategy: AutoTradeStrategy, result: VisionAnalysisResult) {
        when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                result.lowestPoint?.let { low ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_SUPPORT",
                        DrawShape(
                            tool = TradingTool.SUPPORT_LINE,
                            startX = 0f,
                            startY = low.y,
                            endX = 0f,
                            endY = low.y,
                            color = Color.RED,
                            strokeWidth = 6f
                        )
                    )
                }
                result.highestPoint?.let { high ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_RESISTANCE",
                        DrawShape(
                            tool = TradingTool.RESISTANCE_LINE,
                            startX = 0f,
                            startY = high.y,
                            endX = 0f,
                            endY = high.y,
                            color = Color.GREEN,
                            strokeWidth = 6f
                        )
                    )
                }
            }
            AutoTradeStrategy.TREND_FOLLOWING -> {
                if (result.lowestPoint != null && result.highestPoint != null) {
                    val isUp = result.trend == TrendDirection.UPTREND
                    val startP = if (isUp) result.lowestPoint else result.highestPoint
                    val endP = if (isUp) result.highestPoint else result.lowestPoint
                    drawingView.addOrUpdateBotShape(
                        "BOT_TREND_RAY",
                        DrawShape(
                            tool = TradingTool.RAY,
                            startX = startP.x,
                            startY = startP.y,
                            endX = endP.x,
                            endY = endP.y,
                            color = if (isUp) Color.CYAN else Color.MAGENTA,
                            strokeWidth = 5f
                        )
                    )
                }
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                if (result.lowestPoint != null && result.highestPoint != null) {
                    drawingView.addOrUpdateBotShape(
                        "BOT_FIBO",
                        DrawShape(
                            tool = TradingTool.FIB_RETRACEMENT,
                            startX = result.lowestPoint.x,
                            startY = result.lowestPoint.y,
                            endX = result.highestPoint.x,
                            endY = result.highestPoint.y,
                            color = Color.YELLOW,
                            strokeWidth = 4f
                        )
                    )
                }
            }
            AutoTradeStrategy.COMBINED -> {
                // Soportes y resistencias + Rayo de tendencia
                result.lowestPoint?.let { low ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_SUPPORT",
                        DrawShape(
                            tool = TradingTool.SUPPORT_LINE,
                            startX = 0f,
                            startY = low.y,
                            endX = 0f,
                            endY = low.y,
                            color = Color.RED,
                            strokeWidth = 6f
                        )
                    )
                }
                result.highestPoint?.let { high ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_RESISTANCE",
                        DrawShape(
                            tool = TradingTool.RESISTANCE_LINE,
                            startX = 0f,
                            startY = high.y,
                            endX = 0f,
                            endY = high.y,
                            color = Color.GREEN,
                            strokeWidth = 6f
                        )
                    )
                }
            }
        }
    }

    /**
     * Traza una caja de posición LONG o SHORT en el punto de entrada exacto.
     */
    fun drawTradeEntry(action: TradeAction, currentPriceY: Float, screenWidth: Float) {
        val entryX = screenWidth * 0.75f
        val boxWidth = screenWidth * 0.18f
        val boxHeight = 120f

        val tool = if (action == TradeAction.BUY) TradingTool.LONG_POSITION else TradingTool.SHORT_POSITION
        drawingView.addOrUpdateBotShape(
            "BOT_TRADE_ENTRY",
            DrawShape(
                tool = tool,
                startX = entryX - boxWidth,
                startY = currentPriceY,
                endX = entryX + boxWidth,
                endY = currentPriceY + if (action == TradeAction.BUY) -boxHeight else boxHeight,
                color = if (action == TradeAction.BUY) Color.GREEN else Color.RED,
                strokeWidth = 4f
            )
        )
    }

    fun clearAutoDrawings() {
        drawingView.clearBotShapes()
    }
}
