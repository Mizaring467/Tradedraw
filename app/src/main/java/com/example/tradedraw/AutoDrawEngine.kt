package com.example.tradedraw

import android.graphics.Color
import android.graphics.PointF

enum class AutoTradeStrategy {
    SUPPORT_RESISTANCE,
    CANDLE_PATTERNS,
    TREND_FOLLOWING,
    COMBINED,
    MT_REJECTION,           // Master Traders: Mechas de Rechazo en S/R
    MT_CHOQUE_PULLBACK,     // Master Traders: Choque de Máximos/Mínimos (Breakout-Retest)
    MT_3_VELAS_AGOTAMIENTO, // Master Traders: Patrón 3 Velas y Agotamiento de Tendencia
    MT_MASTER_COMBO         // Master Traders: Combo Acción del Precio (Mayor Probabilidad)
}

class AutoDrawEngine(private val drawingView: CustomDrawingView) {

    /**
     * Dibuja o actualiza automáticamente las herramientas técnicas de TradeDraw
     * en base al análisis de visión actual y la estrategia seleccionada.
     */
    fun updateTechnicalDrawings(strategy: AutoTradeStrategy, result: VisionAnalysisResult) {
        when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE, AutoTradeStrategy.MT_REJECTION -> {
                result.lowestPoint?.let { low ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_SUPPORT",
                        DrawShape(
                            tool = TradingTool.SUPPORT_LINE,
                            startX = 0f,
                            startY = low.y,
                            endX = 0f,
                            endY = low.y,
                            color = Color.parseColor("#ef4444"), // Soporte = ROJO
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
                            color = Color.parseColor("#22c55e"), // Resistencia = VERDE
                            strokeWidth = 6f
                        )
                    )
                }
                if (strategy == AutoTradeStrategy.MT_REJECTION && (result.hasTopRejectionWick || result.hasBottomRejectionWick)) {
                    val last = result.candleList.firstOrNull()
                    if (last != null) {
                        drawingView.addOrUpdateBotShape(
                            "BOT_REJECTION_CIRCLE",
                            DrawShape(
                                tool = TradingTool.CIRCLE,
                                startX = last.x,
                                startY = if (result.hasTopRejectionWick) last.topY else last.bottomY,
                                endX = last.x + 25f,
                                endY = (if (result.hasTopRejectionWick) last.topY else last.bottomY) + 25f,
                                color = if (result.hasBottomRejectionWick) Color.GREEN else Color.RED,
                                strokeWidth = 4f
                            )
                        )
                    }
                }
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                // Trazar nivel de choque / pullback con un rayo horizontal
                if (result.candleList.size >= 2) {
                    val brokenLevelY = result.candleList[1].bodyTopY
                    drawingView.addOrUpdateBotShape(
                        "BOT_CHOQUE_LEVEL",
                        DrawShape(
                            tool = TradingTool.HORIZONTAL_LINE,
                            startX = 0f,
                            startY = brokenLevelY,
                            endX = 0f,
                            endY = brokenLevelY,
                            color = Color.parseColor("#38bdf8"),
                            strokeWidth = 5f
                        )
                    )
                }
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                if (result.lowestPoint != null && result.highestPoint != null) {
                    drawingView.addOrUpdateBotShape(
                        "BOT_EXHAUSTION_ZONE",
                        DrawShape(
                            tool = TradingTool.ZONE,
                            startX = result.highestPoint.x - 60f,
                            startY = result.highestPoint.y,
                            endX = result.highestPoint.x + 60f,
                            endY = result.lowestPoint.y,
                            color = Color.parseColor("#fb923c"),
                            strokeWidth = 3f
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
            AutoTradeStrategy.COMBINED, AutoTradeStrategy.MT_MASTER_COMBO -> {
                result.lowestPoint?.let { low ->
                    drawingView.addOrUpdateBotShape(
                        "BOT_SUPPORT",
                        DrawShape(
                            tool = TradingTool.SUPPORT_LINE,
                            startX = 0f,
                            startY = low.y,
                            endX = 0f,
                            endY = low.y,
                            color = Color.parseColor("#ef4444"), // Soporte = ROJO
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
                            color = Color.parseColor("#22c55e"), // Resistencia = VERDE
                            strokeWidth = 6f
                        )
                    )
                }
            }
        }
    }

    private var currentEntryY: Float = 0f
    private var currentEntryAction: TradeAction? = null

    /**
     * Traza la línea de precio de entrada (Strike Price) para opciones binarias.
     * Reemplaza definitivamente la caja de Forex/StopLoss por una línea limpia ITM/OTM.
     */
    fun drawTradeEntry(action: TradeAction, entryPriceY: Float, screenWidth: Float) {
        currentEntryY = entryPriceY
        currentEntryAction = action

        val label = if (action == TradeAction.BUY) "▲ CALL [ITM]" else "▼ PUT [ITM]"
        drawingView.addOrUpdateBotShape(
            "BOT_STRIKE_PRICE",
            DrawShape(
                tool = TradingTool.STRIKE_PRICE_LINE,
                startX = 0f,
                startY = entryPriceY,
                endX = screenWidth,
                endY = entryPriceY,
                color = Color.GREEN,
                strokeWidth = 4f,
                labelText = label
            )
        )
    }

    /**
     * Actualiza en tiempo real el estado ITM (Ganando - Verde) u OTM (Perdiendo - Rojo) del Strike Price.
     */
    fun updateTradeEntryLiveStatus(livePriceY: Float) {
        val action = currentEntryAction ?: return
        if (currentEntryY <= 0f) return

        // En coordenadas de pantalla: Menor Y = Mayor Precio (Subió)
        val isITM = if (action == TradeAction.BUY) {
            livePriceY <= currentEntryY // En CALL gana si el precio actual está más arriba (menor Y)
        } else {
            livePriceY >= currentEntryY // En PUT gana si el precio actual está más abajo (mayor Y)
        }

        val actionName = if (action == TradeAction.BUY) "CALL" else "PUT"
        val statusTag = if (isITM) "▲ $actionName [ITM]" else "▼ $actionName [OTM]"
        val color = if (isITM) Color.GREEN else Color.RED

        drawingView.addOrUpdateBotShape(
            "BOT_STRIKE_PRICE",
            DrawShape(
                tool = TradingTool.STRIKE_PRICE_LINE,
                startX = 0f,
                startY = currentEntryY,
                endX = 0f,
                endY = currentEntryY,
                color = color,
                strokeWidth = 4f,
                labelText = statusTag
            )
        )
    }

    fun clearTradeEntry() {
        currentEntryY = 0f
        currentEntryAction = null
        val shapes = drawingView.getShapes().toMutableList()
        shapes.removeAll { it.labelText.contains("BOT_STRIKE_PRICE") || it.tool == TradingTool.STRIKE_PRICE_LINE }
        drawingView.setShapes(shapes)
    }

    fun clearAutoDrawings() {
        clearTradeEntry()
        drawingView.clearBotShapes()
    }
}
