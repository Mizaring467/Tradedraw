package com.example.tradedraw

import android.graphics.Color
import android.graphics.PointF

enum class AutoTradeStrategy {
    AUTO_ADAPTIVE,          // Modo Automático Total: Multi-Estrategia Adaptativa (Recomendado)
    MT_MASTER_COMBO,        // Master Traders: Combo Acción del Precio (Mayor Probabilidad)
    MT_FALSE_BREAKOUT,      // Master Traders: Falso Rompimiento / Trampa en S/R
    MT_ENGULFING_SR,        // Master Traders: Patrón Envolvente en S/R
    MT_REJECTION,           // Master Traders: Mechas de Rechazo en S/R
    MT_CHOQUE_PULLBACK,     // Master Traders: Choque de Máximos/Mínimos (Breakout-Retest)
    MT_3_VELAS_AGOTAMIENTO, // Master Traders: Patrón 3 Velas y Agotamiento de Tendencia
    COLOR_TREND,            // Seguidor de Color / Tendencia
    STRIKE_BREAKOUT,        // Ruptura de Strike / Nivel
    AI_REMOTE,              // Análisis Remoto IA
    SUPPORT_RESISTANCE,     // Soportes y Resistencias Clásico
    CANDLE_PATTERNS,        // Patrón de Velas y Martillo
    TREND_FOLLOWING,        // Seguidor de Tendencia
    COMBINED                // Doble Confirmación
}

class AutoDrawEngine(private val drawingView: CustomDrawingView) {

    // Líneas bloqueadas manualmente por el usuario (no se sobreescriben con cada frame)
    private val manuallyLockedLines = mutableSetOf<String>()

    fun lockLine(key: String) {
        manuallyLockedLines.add(key)
        android.util.Log.d("AutoDrawEngine", "Línea bloqueada manualmente: $key")
        // Cambiar etiqueta visual a [Manual]
        val shapes = drawingView.getShapes()
        val shape = shapes.find { it.isBotDrawn && it.labelText == key }
        if (shape != null) {
            shape.isManuallyLocked = true
            drawingView.invalidate()
        }
    }

    fun unlockLine(key: String) {
        manuallyLockedLines.remove(key)
        android.util.Log.d("AutoDrawEngine", "Línea desbloqueada: $key")
        val shapes = drawingView.getShapes()
        val shape = shapes.find { it.isBotDrawn && it.labelText == key }
        if (shape != null) {
            shape.isManuallyLocked = false
            drawingView.invalidate()
        }
    }

    fun unlockAllLines() {
        manuallyLockedLines.clear()
        drawingView.getShapes().filter { it.isBotDrawn }.forEach { it.isManuallyLocked = false }
        drawingView.invalidate()
        android.util.Log.d("AutoDrawEngine", "Todas las líneas desbloqueadas")
    }

    fun hasLockedLines(): Boolean = manuallyLockedLines.isNotEmpty()

    private fun updateBotShapeIfNotLocked(key: String, shape: DrawShape) {
        if (manuallyLockedLines.contains(key)) {
            // No sobreescribir: el usuario la ajustó manualmente
            return
        }
        drawingView.addOrUpdateBotShape(key, shape)
    }

    /**
     * Gestión de trazos automáticos:
     * El análisis de S/R y figuras técnicas se procesa 100% de forma cuantitativa interna
     * en el HUD didáctico, manteniendo el lienzo de pantalla despejado y sin líneas estáticas.
     */
    fun updateTechnicalDrawings() {
        // No generar figuras estáticas automáticas sobre la pantalla.
        // El HUD didáctico muestra la información de S/R, probabilidad y patrones en tiempo real.
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
