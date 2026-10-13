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

    // Ronda 11 (T3): el strike vive en DOMINIO DE PRECIO. Antes se guardaba la Y del tap y se
    // comparaba con Y de visión, lo que producía una línea punteada a cientos de píxeles del
    // precio real y un estado ITM/OTM imposible (PUT [ITM] con el precio por encima del strike).
    private var currentEntryPrice: Double = 0.0
    private var currentEntryAction: TradeAction? = null

    // Stake y payout vigentes para estimar el P&L real en la etiqueta.
    private var currentStake: Double = 0.0
    private var currentPayoutRate: Double = 0.0

    /**
     * Traza la línea del STRIKE (precio de entrada) para opciones binarias.
     *
     * @param entryPrice precio real del broker en el momento del disparo (dominio de precio).
     * @param screenWidth ancho del lienzo.
     * @param stake capital arriesgado (para el P&L estimado).
     * @param payoutRate tasa de payout configurada (para el P&L estimado).
     */
    fun drawTradeEntry(
        action: TradeAction,
        entryPrice: Double,
        screenWidth: Float,
        stake: Double = 0.0,
        payoutRate: Double = 0.0
    ) {
        currentEntryPrice = entryPrice
        currentEntryAction = action
        currentStake = stake
        currentPayoutRate = payoutRate
        // El strike es de otro activo si cambia la escala: se recalibra el viewport.
        PriceViewportMapper.reset()
        renderStrike(action, TradeMoneyness.TIE)
    }

    /**
     * Actualiza el estado ITM/OTM comparando el PRECIO actual contra el precio de entrada.
     * Devuelve el moneyness calculado para que el llamador pueda etiquetar el HUD con la misma
     * fuente de verdad (una sola comparación en el sistema).
     */
    fun updateTradeEntryLiveStatus(livePrice: Double): TradeMoneyness {
        val action = currentEntryAction ?: return TradeMoneyness.TIE
        if (currentEntryPrice <= 0.0) return TradeMoneyness.TIE

        val moneyness = StrikeCalculator.moneyness(action, currentEntryPrice, livePrice)
        renderStrike(action, moneyness)
        return moneyness
    }

    private fun renderStrike(action: TradeAction, moneyness: TradeMoneyness) {
        val label = StrikeCalculator.strikeLabel(
            action = action,
            moneyness = moneyness,
            entryPrice = currentEntryPrice,
            pnl = StrikeCalculator.estimatedPnl(moneyness, currentStake, currentPayoutRate)
        )
        val color = if (moneyness == TradeMoneyness.ITM) Color.GREEN else Color.RED

        // Rango visible reciente del motor sintético: fallback de proyección cuando no hay
        // calibración real del eje (Binomo no expone el eje de precios a accesibilidad).
        val synthetic = OverlayService.instance?.tradingEngine?.syntheticCandleEngine
        if (synthetic != null) {
            val (lo, hi) = synthetic.recentPriceBounds()
            if (hi > lo) {
                PriceViewportMapper.setVisiblePriceRange(lo, hi, 0f, drawingView.height.toFloat())
            }
        }

        // El precio de entrada puede caer fuera del viewport visible: en ese caso se ancla al borde
        // con flecha en vez de dibujar la línea en una Y inventada.
        val geometry = StrikeCalculator.strikeGeometry(
            entryPrice = currentEntryPrice,
            priceToY = { PriceViewportMapper.priceToY(it) },
            viewportTop = 0f,
            viewportBottom = drawingView.height.toFloat()
        )
        val y = geometry?.y ?: (drawingView.height * 0.5f)
        val arrow = geometry?.arrow ?: ""
        val finalLabel = if (arrow.isEmpty()) label else "$arrow $label"

        drawingView.addOrUpdateBotShape(
            "BOT_STRIKE_PRICE",
            DrawShape(
                tool = TradingTool.STRIKE_PRICE_LINE,
                startX = 0f,
                startY = y,
                endX = drawingView.width.toFloat().coerceAtLeast(1f),
                endY = y,
                color = color,
                strokeWidth = 4f,
                labelText = finalLabel
            )
        )
    }

    fun clearTradeEntry() {
        currentEntryPrice = 0.0
        currentEntryAction = null
        currentStake = 0.0
        currentPayoutRate = 0.0
        val shapes = drawingView.getShapes().toMutableList()
        shapes.removeAll { it.labelText.contains("BOT_STRIKE_PRICE") || it.tool == TradingTool.STRIKE_PRICE_LINE }
        drawingView.setShapes(shapes)
    }

    fun clearAutoDrawings() {
        clearTradeEntry()
        drawingView.clearBotShapes()
    }
}
