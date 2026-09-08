package com.example.tradedraw

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vista de dibujo profesional con motor de colisiones avanzado.
 * Permite edición por nodos individuales (redimensionar) y desplazamiento de cuerpo.
 */
class CustomDrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var shapes = ArrayList<DrawShape>()
    private val undoneShapes = ArrayList<DrawShape>()
    
    private var currentTool = TradingTool.SELECT_TOUCH
    private var activeDrawingColor = Color.parseColor("#00FF00")
    private var currentStrokeWidth = 7f

    private var isCanvasVisible = true

    private var currentShape: DrawShape? = null
    private var selectedShape: DrawShape? = null

    // Puntos pendientes para herramientas multi-punto (CHANNEL y TRIANGLE -> 3 toques)
    private val tapPoints = ArrayList<PointF>()
    private var previewPoint: PointF? = null
    private var pendingLabelText = ""

    // Animación de marcador de clic del bot
    private var clickMarkerPoint: PointF? = null
    private val clickMarkerPaint = Paint().apply {
        color = Color.parseColor("#38bdf8")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    fun triggerClickAnimation(x: Float, y: Float) {
        clickMarkerPoint = PointF(x, y)
        invalidate()
        postDelayed({
            clickMarkerPoint = null
            invalidate()
        }, 800)
    }

    fun setLabelText(text: String) { this.pendingLabelText = text; invalidate() }

    var onShapesChange: (() -> Unit)? = null
    // Callback cuando el usuario arrastra manualmente una línea del bot
    var onBotShapeDragged: ((key: String) -> Unit)? = null

    private fun notifyShapesChange() { onShapesChange?.invoke() }

    private fun requiredPoints(tool: TradingTool): Int = when (tool) {
        TradingTool.TRIANGLE, TradingTool.CHANNEL -> 3
        else -> 0
    }

    private fun isMultiPointTool(tool: TradingTool): Boolean = requiredPoints(tool) > 0
    
    private enum class DragMode { NONE, START, END, THIRD, BODY }
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var isEraserDragging = false

    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val dashPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
    private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val handleStrokePaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val shapePaint = Paint().apply { isAntiAlias = true; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    // Componentes de renderizado elegante estilo TradingView para Fibonacci Retracement
    private data class FibLevel(val ratio: Float, val label: String, val color: Int, val fillAlpha: Int = 22)
    private val fibLevels = listOf(
        FibLevel(0.000f, "0 (0.0%)", Color.parseColor("#787b86"), 0),
        FibLevel(0.236f, "0.236 (23.6%)", Color.parseColor("#f23645"), 24),
        FibLevel(0.382f, "0.382 (38.2%)", Color.parseColor("#ff9800"), 24),
        FibLevel(0.500f, "0.5 (50.0%)", Color.parseColor("#4caf50"), 24),
        FibLevel(0.618f, "0.618 (61.8%)", Color.parseColor("#089981"), 42), // Golden Pocket destacado
        FibLevel(0.786f, "0.786 (78.6%)", Color.parseColor("#00bcd4"), 24),
        FibLevel(1.000f, "1 (100.0%)", Color.parseColor("#787b86"), 24)
    )
    private val fibFillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val fibLinePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; isAntiAlias = true }
    private val fibTrendPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = Color.parseColor("#94a3b8")
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        isAntiAlias = true
    }
    private val fibTextPaint = Paint().apply {
        textSize = 21f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val fibBadgePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(195, 15, 23, 42)
        isAntiAlias = true
    }

    var onToolChanged: ((TradingTool) -> Unit)? = null

    fun setTool(tool: TradingTool) {
        this.currentTool = tool
        tapPoints.clear()
        previewPoint = null
        currentShape = null
        if (tool != TradingTool.SELECT_TOUCH) deselectAll()
        onToolChanged?.invoke(tool)
        invalidate()
    }

    fun setColor(color: Int) {
        this.activeDrawingColor = color
        selectedShape?.let { 
            if (it.tool != TradingTool.SUPPORT_LINE && it.tool != TradingTool.RESISTANCE_LINE && 
                it.tool != TradingTool.LONG_POSITION && it.tool != TradingTool.SHORT_POSITION) {
                it.color = color 
            }
        }
        invalidate()
    }

    fun toggleCanvasVisibility() {
        isCanvasVisible = !isCanvasVisible
        if (!isCanvasVisible) deselectAll()
        invalidate()
    }

    fun isCanvasVisible(): Boolean = isCanvasVisible
    fun getShapes(): List<DrawShape> = shapes
    fun setShapes(newShapes: List<DrawShape>) { this.shapes = ArrayList(newShapes); undoneShapes.clear(); invalidate() }

    fun addOrUpdateBotShape(key: String, shape: DrawShape) {
        shape.isBotDrawn = true
        shape.labelText = key
        val idx = shapes.indexOfFirst { it.isBotDrawn && it.labelText == key }
        if (idx >= 0) {
            shapes[idx] = shape
        } else {
            shapes.add(shape)
        }
        invalidate()
    }

    fun clearBotShapes() {
        shapes.removeAll { it.isBotDrawn }
        invalidate()
    }

    fun getSupportResistanceYLevels(): Pair<List<Float>, List<Float>> {
        val supports = shapes.filter { it.tool == TradingTool.SUPPORT_LINE }.map { it.startY }
        val resistances = shapes.filter { it.tool == TradingTool.RESISTANCE_LINE }.map { it.startY }
        return Pair(supports, resistances)
    }

    private fun deselectAll() { shapes.forEach { it.isSelected = false }; selectedShape = null }

    fun undo() { if (shapes.isNotEmpty()) { undoneShapes.add(shapes.removeAt(shapes.size - 1)); deselectAll(); invalidate(); notifyShapesChange() } }
    fun redo() { if (undoneShapes.isNotEmpty()) { shapes.add(undoneShapes.removeAt(undoneShapes.size - 1)); deselectAll(); invalidate(); notifyShapesChange() } }
    
    fun deleteSelectedOrLast() {
        if (selectedShape != null) {
            val s = selectedShape!!
            shapes.remove(s); undoneShapes.add(s); selectedShape = null; invalidate(); notifyShapesChange()
        } else if (shapes.isNotEmpty()) {
            undo()
        }
    }

    fun clearCanvas() { shapes.clear(); undoneShapes.clear(); currentShape = null; selectedShape = null; invalidate(); notifyShapesChange() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCanvasVisible) return
        for (shape in shapes) {
            drawGenericShape(canvas, shape)
            if (shape.isSelected) drawHandles(canvas, shape)
        }
        currentShape?.let { drawGenericShape(canvas, it) }
        drawPendingMultiPointPreview(canvas)
        clickMarkerPoint?.let { pt ->
            canvas.drawCircle(pt.x, pt.y, 45f, clickMarkerPaint)
            canvas.drawCircle(pt.x, pt.y, 12f, handlePaint)
        }
    }

    private fun drawPendingMultiPointPreview(canvas: Canvas) {
        if (!isMultiPointTool(currentTool) || tapPoints.isEmpty()) return
        val paint = Paint().apply {
            color = activeDrawingColor; strokeWidth = currentStrokeWidth; isAntiAlias = true
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
        }
        val path = Path()
        for (i in tapPoints.indices) {
            val p = tapPoints[i]
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            canvas.drawCircle(p.x, p.y, 8f, handlePaint)
        }
        previewPoint?.let { p ->
            if (tapPoints.isNotEmpty()) path.lineTo(p.x, p.y)
            canvas.drawCircle(p.x, p.y, 8f, handlePaint)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawHandles(canvas: Canvas, shape: DrawShape) {
        val radius = 25f
        if (shape.tool == TradingTool.FREE_BRUSH) {
            val b = RectF(); shape.createPath().computeBounds(b, true)
            canvas.drawCircle(b.left, b.top, radius, handlePaint)
            canvas.drawCircle(b.left, b.top, radius, handleStrokePaint)
            canvas.drawCircle(b.right, b.bottom, radius, handlePaint)
            canvas.drawCircle(b.right, b.bottom, radius, handleStrokePaint)
        } else {
            canvas.drawCircle(shape.startX, shape.startY, radius, handlePaint)
            canvas.drawCircle(shape.startX, shape.startY, radius, handleStrokePaint)
            canvas.drawCircle(shape.endX, shape.endY, radius, handlePaint)
            canvas.drawCircle(shape.endX, shape.endY, radius, handleStrokePaint)
            if (shape.tool == TradingTool.TRIANGLE || shape.tool == TradingTool.CHANNEL) {
                canvas.drawCircle(shape.thirdX, shape.thirdY, radius, handlePaint)
                canvas.drawCircle(shape.thirdX, shape.thirdY, radius, handleStrokePaint)
            }
        }
    }

    private fun drawGenericShape(canvas: Canvas, shape: DrawShape) {
        val paint = shapePaint
        paint.color = shape.color
        paint.strokeWidth = shape.strokeWidth
        paint.style = if (shape.tool == TradingTool.LONG_POSITION || shape.tool == TradingTool.SHORT_POSITION) Paint.Style.FILL else Paint.Style.STROKE
        when (shape.tool) {
            TradingTool.FREE_BRUSH -> canvas.drawPath(shape.createPath(), paint)
            TradingTool.TREND_LINE -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
            TradingTool.SUPPORT_LINE -> {
                canvas.drawLine(0f, shape.startY, width.toFloat(), shape.startY, paint)
                val label = if (shape.isManuallyLocked) "SOPORTE [Manual]" else if (shape.isBotDrawn) "SOPORTE [IA]" else "SOPORTE"
                canvas.drawText(label, 25f, shape.startY - 15f, textPaint)
            }
            TradingTool.RESISTANCE_LINE -> {
                canvas.drawLine(0f, shape.startY, width.toFloat(), shape.startY, paint)
                val label = if (shape.isManuallyLocked) "RESISTENCIA [Manual]" else if (shape.isBotDrawn) "RESISTENCIA [IA]" else "RESISTENCIA"
                canvas.drawText(label, 25f, shape.startY - 15f, textPaint)
            }
            TradingTool.RECTANGLE -> canvas.drawRect(Math.min(shape.startX, shape.endX), Math.min(shape.startY, shape.endY), Math.max(shape.startX, shape.endX), Math.max(shape.startY, shape.endY), paint)
            TradingTool.FIB_RETRACEMENT -> drawFibonacci(canvas, shape, paint)
            TradingTool.LONG_POSITION -> drawPosition(canvas, shape, isLong = true)
            TradingTool.SHORT_POSITION -> drawPosition(canvas, shape, isLong = false)
            TradingTool.HORIZONTAL_LINE -> canvas.drawLine(0f, shape.startY, width.toFloat(), shape.startY, paint)
            TradingTool.VERTICAL_LINE -> canvas.drawLine(shape.startX, 0f, shape.startX, height.toFloat(), paint)
            TradingTool.RAY -> drawRay(canvas, shape, paint)
            TradingTool.MEASURE -> drawMeasure(canvas, shape, paint)
            TradingTool.TEXT_LABEL -> canvas.drawText(shape.text.ifEmpty { "TEXTO" }, shape.startX, shape.startY, textPaint)
            TradingTool.CIRCLE -> drawCircleTool(canvas, shape, paint)
            TradingTool.TRIANGLE -> drawTriangle(canvas, shape, paint)
            TradingTool.ZONE -> drawZone(canvas, shape, paint)
            TradingTool.CHANNEL -> drawChannel(canvas, shape, paint)
            TradingTool.STRIKE_PRICE_LINE -> drawStrikePriceLine(canvas, shape)
            else -> {}
        }
    }

    private fun drawStrikePriceLine(canvas: Canvas, shape: DrawShape) {
        val y = shape.startY
        val isCall = shape.labelText.contains("CALL", ignoreCase = true) || shape.labelText.contains("COMPRA", ignoreCase = true)
        val isITM = shape.color == Color.GREEN || shape.labelText.contains("ITM", ignoreCase = true)

        val linePaint = Paint().apply {
            color = if (isITM) Color.parseColor("#22c55e") else Color.parseColor("#ef4444")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            isAntiAlias = true
        }
        canvas.drawLine(0f, y, width.toFloat(), y, linePaint)

        val badgeBg = Paint().apply {
            color = if (isITM) Color.parseColor("#E615803d") else Color.parseColor("#E6b91c1c")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val labelText = if (isCall) {
            if (isITM) "▲ CALL [ITM +$$$]" else "▲ CALL [OTM $0]"
        } else {
            if (isITM) "▼ PUT [ITM +$$$]" else "▼ PUT [OTM $0]"
        }

        val badgeWidth = textPaint.measureText(labelText) + 24f
        val badgeHeight = 36f
        val badgeLeft = (width * 0.65f).coerceAtMost(width - badgeWidth - 20f)
        val badgeTop = y - badgeHeight - 4f

        canvas.drawRoundRect(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight, 8f, 8f, badgeBg)
        canvas.drawText(labelText, badgeLeft + 12f, badgeTop + 24f, textPaint)
    }

    private fun drawRay(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val dx = shape.endX - shape.startX
        val dy = shape.endY - shape.startY
        if (dx == 0f && dy == 0f) return
        // Extender la línea mucho más allá del punto final para ir 'hasta el borde'
        val scale = Math.max(width, height) * 3f
        val endX = shape.endX + dx * scale
        val endY = shape.endY + dy * scale
        canvas.drawLine(shape.startX, shape.startY, endX, endY, paint)
    }

    private fun drawMeasure(canvas: Canvas, shape: DrawShape, paint: Paint) {
        canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
        val dX = Math.abs(shape.endX - shape.startX).toInt()
        val dY = Math.abs(shape.endY - shape.startY).toInt()
        val pctH = if (height > 0) (Math.abs(shape.endY - shape.startY) / height.toFloat() * 100).toInt() else 0
        canvas.drawText("ΔX ${dX}px · ΔY ${dY}px · ${pctH}%", (shape.startX + shape.endX) / 2, (shape.startY + shape.endY) / 2, textPaint)
    }

    private fun drawCircleTool(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val centerX = shape.startX; val centerY = shape.startY
        val r = Math.sqrt((shape.endX - centerX) * (shape.endX - centerX) + (shape.endY - centerY) * (shape.endY - centerY).toDouble()).toFloat()
        canvas.drawCircle(centerX, centerY, r, paint)
    }

    private fun drawTriangle(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val path = Path()
        path.moveTo(shape.startX, shape.startY)
        path.lineTo(shape.endX, shape.endY)
        path.lineTo(shape.thirdX, shape.thirdY)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawZone(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val l = Math.min(shape.startX, shape.endX); val r = Math.max(shape.startX, shape.endX)
        val t = Math.min(shape.startY, shape.endY); val b = Math.max(shape.startY, shape.endY)
        val fill = Paint(paint).apply { style = Paint.Style.FILL; color = (paint.color and 0x00FFFFFF) or 0x40000000.toInt() }
        canvas.drawRect(l, t, r, b, fill)
        canvas.drawRect(l, t, r, b, paint)
    }

    private fun drawChannel(canvas: Canvas, shape: DrawShape, paint: Paint) {
        // Línea base A(0)->B(1)
        canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
        // Línea paralela pasando por C(2): mover el vector (B-A) a C
        val dx = shape.endX - shape.startX
        val dy = shape.endY - shape.startY
        val p2x = shape.thirdX - dx
        val p2y = shape.thirdY - dy
        canvas.drawLine(shape.thirdX, shape.thirdY, p2x, p2y, paint)
    }

    private fun drawFibonacci(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val minX = Math.min(shape.startX, shape.endX)
        val maxX = Math.max(shape.startX, shape.endX)
        val left = if (maxX - minX < 30f) 0f else minX
        val right = if (maxX - minX < 30f) width.toFloat() else maxX
        val diffY = shape.endY - shape.startY

        // 1. Bandas de relleno translúcidas estilo TradingView entre niveles consecutivos
        for (i in 0 until fibLevels.size - 1) {
            val lvlCurr = fibLevels[i]
            val lvlNext = fibLevels[i + 1]
            val yCurr = shape.startY + diffY * lvlCurr.ratio
            val yNext = shape.startY + diffY * lvlNext.ratio
            val top = Math.min(yCurr, yNext)
            val bottom = Math.max(yCurr, yNext)

            val baseColor = lvlNext.color
            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)
            fibFillPaint.color = Color.argb(lvlNext.fillAlpha, r, g, b)
            canvas.drawRect(left, top, right, bottom, fibFillPaint)
        }

        // 2. Línea de tendencia diagonal sutil que conecta el punto de anclaje inicial y final
        canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, fibTrendPaint)

        // 3. Líneas de nivel horizontales nítidas y badges con valores estilo TradingView
        val badgeHeight = 28f
        val badgePaddingH = 10f
        val cornerRadius = 6f

        for (lvl in fibLevels) {
            val levelY = shape.startY + diffY * lvl.ratio
            fibLinePaint.color = lvl.color
            canvas.drawLine(left, levelY, right, levelY, fibLinePaint)

            val text = lvl.label
            val textWidth = fibTextPaint.measureText(text)
            val badgeWidth = textWidth + badgePaddingH * 2
            val badgeLeft = (right - badgeWidth - 6f).coerceAtLeast(left + 6f)
            val badgeTop = levelY - badgeHeight / 2f

            canvas.drawRoundRect(
                badgeLeft,
                badgeTop,
                badgeLeft + badgeWidth,
                badgeTop + badgeHeight,
                cornerRadius,
                cornerRadius,
                fibBadgePaint
            )

            fibTextPaint.color = lvl.color
            val textX = badgeLeft + badgePaddingH
            val textY = levelY - ((fibTextPaint.descent() + fibTextPaint.ascent()) / 2f)
            canvas.drawText(text, textX, textY, fibTextPaint)
        }
    }

    private fun drawPosition(canvas: Canvas, shape: DrawShape, isLong: Boolean) {
        val midY = shape.startY; val diffY = Math.abs(shape.endY - shape.startY)
        val left = Math.min(shape.startX, shape.endX); val right = Math.max(shape.startX, shape.endX)
        val pProfit = Paint().apply { style = Paint.Style.FILL; color = Color.argb(130, 34, 197, 94); isAntiAlias = true }
        val pLoss = Paint().apply { style = Paint.Style.FILL; color = Color.argb(130, 239, 68, 68); isAntiAlias = true }
        if (isLong) {
            canvas.drawRect(left, midY - diffY, right, midY, pProfit)
            canvas.drawRect(left, midY, right, midY + diffY, pLoss)
        } else {
            canvas.drawRect(left, midY - diffY, right, midY, pLoss)
            canvas.drawRect(left, midY, right, midY + diffY, pProfit)
        }
        canvas.drawLine(left, midY, right, midY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCanvasVisible) return false
        val x = event.x; val y = event.y

        if (isMultiPointTool(currentTool)) {
            return handleMultiPointTouch(event, x, y)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (handleAutoSelection(x, y)) {
                    // Seleccionado o manipulando nodo
                } else if (currentTool == TradingTool.ERASER_TOUCH) {
                    isEraserDragging = true
                    handleEraserDown(x, y)
                } else if (selectedShape == null) {
                    // Solo dibujar si NO hay nada seleccionado para evitar trazos accidentales
                    undoneShapes.clear()
                    val colorToUse = when (currentTool) {
                        TradingTool.SUPPORT_LINE -> Color.parseColor("#ef4444") // Soporte = ROJO
                        TradingTool.RESISTANCE_LINE -> Color.parseColor("#22c55e") // Resistencia = VERDE
                        else -> activeDrawingColor
                    }
                    currentShape = DrawShape(currentTool, x, y, x, y, color = colorToUse, strokeWidth = currentStrokeWidth)
                    if (currentTool == TradingTool.FREE_BRUSH) currentShape?.pathPoints?.add(PointF(x, y))
                    if (currentTool == TradingTool.TEXT_LABEL) currentShape?.text = pendingLabelText
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == TradingTool.ERASER_TOUCH && isEraserDragging) {
                    handleEraserMove(x, y)
                } else if (selectedShape != null && dragMode != DragMode.NONE) {
                    handleSelectionMove(x, y)
                } else {
                    currentShape?.let { 
                        it.endX = x; it.endY = y
                        if (it.tool == TradingTool.FREE_BRUSH) it.pathPoints.add(PointF(x, y))
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                isEraserDragging = false
                // Detectar si se arrastró una línea del bot para bloquearla
                if (selectedShape != null && selectedShape!!.isBotDrawn && dragMode != DragMode.NONE) {
                    onBotShapeDragged?.invoke(selectedShape!!.labelText)
                }
                if (selectedShape == null && currentShape != null) {
                    val finishedShape = currentShape!!
                    val dx = Math.abs(finishedShape.endX - finishedShape.startX)
                    val dy = Math.abs(finishedShape.endY - finishedShape.startY)
                    val isSingleClickTool = finishedShape.tool == TradingTool.SUPPORT_LINE ||
                            finishedShape.tool == TradingTool.RESISTANCE_LINE ||
                            finishedShape.tool == TradingTool.HORIZONTAL_LINE ||
                            finishedShape.tool == TradingTool.VERTICAL_LINE ||
                            finishedShape.tool == TradingTool.TEXT_LABEL

                    if (isSingleClickTool || dx > 15f || dy > 15f || finishedShape.tool == TradingTool.FREE_BRUSH) {
                        shapes.add(finishedShape)
                        notifyShapesChange()

                        // Comportamiento TradingView: Seleccionar inmediatamente el objeto creado
                        // y pasar a modo SELECT_TOUCH para permitir mover o redimensionar sin duplicar
                        if (finishedShape.tool != TradingTool.FREE_BRUSH) {
                            deselectAll()
                            finishedShape.isSelected = true
                            selectedShape = finishedShape
                            setTool(TradingTool.SELECT_TOUCH)
                        }
                    }
                    currentShape = null
                }
                dragMode = DragMode.NONE
            }
        }
        lastX = x; lastY = y
        invalidate(); return true
    }

    /**
     * Manejo de herramientas multi-punto (CHANNEL, TRIANGLE): toca por punto.
     * Cada toque (ACTION_UP) añade un punto; con los puntos necesarios se crea la figura.
     */
    private fun handleMultiPointTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                // Preview: mostrar el punto en construcción siguiendo el dedo
                if (tapPoints.isNotEmpty()) previewPoint = PointF(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                tapPoints.add(PointF(x, y))
                previewPoint = null
                invalidate()
                if (tapPoints.size >= requiredPoints(currentTool)) {
                    val shape = buildMultiPointShape()
                    if (shape != null) {
                        shapes.add(shape)
                        notifyShapesChange()
                        deselectAll()
                        shape.isSelected = true
                        selectedShape = shape
                        setTool(TradingTool.SELECT_TOUCH)
                    }
                    tapPoints.clear()
                    previewPoint = null
                    invalidate()
                }
            }
        }
        lastX = x; lastY = y
        return true
    }

    private fun buildMultiPointShape(): DrawShape? {
        if (tapPoints.isEmpty()) return null
        val colorToUse = when (currentTool) {
            TradingTool.SUPPORT_LINE -> Color.parseColor("#ef4444") // Soporte = ROJO
            TradingTool.RESISTANCE_LINE -> Color.parseColor("#22c55e") // Resistencia = VERDE
            else -> activeDrawingColor
        }
        val p0 = tapPoints[0]
        val p1 = if (tapPoints.size > 1) tapPoints[1] else p0
        val p2 = if (tapPoints.size > 2) tapPoints[2] else p1
        return DrawShape(
            tool = currentTool,
            startX = p0.x, startY = p0.y,
            endX = p1.x, endY = p1.y,
            thirdX = p2.x, thirdY = p2.y,
            color = colorToUse,
            strokeWidth = currentStrokeWidth
        )
    }

    private fun handleAutoSelection(x: Float, y: Float): Boolean {
        // 1. Probar PRIMERO los nodos o cuerpo del objeto ya seleccionado
        // Si el usuario toca un nodo de control (START/END/THIRD), SIEMPRE permitir arrastrar
        selectedShape?.let { s ->
            if (s.tool != TradingTool.FREE_BRUSH) {
                if (isNear(x, y, s.startX, s.startY)) { dragMode = DragMode.START; return true }
                if (isNear(x, y, s.endX, s.endY)) { dragMode = DragMode.END; return true }
                if ((s.tool == TradingTool.TRIANGLE || s.tool == TradingTool.CHANNEL) && isNear(x, y, s.thirdX, s.thirdY)) {
                    dragMode = DragMode.THIRD
                    return true
                }
            }
            // Probar cuerpo para Mover
            if (isHit(x, y, s)) { dragMode = DragMode.BODY; return true }
        }

        // 2. Comprobar si se tocó un nodo de control o cuerpo de alguna figura existente
        for (i in shapes.size - 1 downTo 0) {
            val s = shapes[i]
            if (s.tool != TradingTool.FREE_BRUSH) {
                if (isNear(x, y, s.startX, s.startY)) {
                    deselectAll()
                    s.isSelected = true
                    selectedShape = s
                    setTool(TradingTool.SELECT_TOUCH)
                    dragMode = DragMode.START
                    return true
                }
                if (isNear(x, y, s.endX, s.endY)) {
                    deselectAll()
                    s.isSelected = true
                    selectedShape = s
                    setTool(TradingTool.SELECT_TOUCH)
                    dragMode = DragMode.END
                    return true
                }
                if ((s.tool == TradingTool.TRIANGLE || s.tool == TradingTool.CHANNEL) && isNear(x, y, s.thirdX, s.thirdY)) {
                    deselectAll()
                    s.isSelected = true
                    selectedShape = s
                    setTool(TradingTool.SELECT_TOUCH)
                    dragMode = DragMode.THIRD
                    return true
                }
            }

            // Si la herramienta activa es ELEGIR (SELECT_TOUCH) o se toca directo la figura
            if ((currentTool == TradingTool.SELECT_TOUCH || isHit(x, y, s)) && s.tool != TradingTool.FREE_BRUSH) {
                deselectAll()
                s.isSelected = true
                selectedShape = s
                setTool(TradingTool.SELECT_TOUCH)
                dragMode = DragMode.BODY
                return true
            }
        }

        // 3. Si se toca vacío estando en SELECT_TOUCH, deseleccionar
        if (currentTool == TradingTool.SELECT_TOUCH && selectedShape != null) {
            deselectAll()
            return true
        }
        return false
    }

    private fun handleSelectionMove(x: Float, y: Float) {
        val dx = x - lastX; val dy = y - lastY
        selectedShape?.let { s ->
            when (dragMode) {
                DragMode.START -> { s.startX = x; s.startY = y }
                DragMode.END -> { s.endX = x; s.endY = y }
                DragMode.THIRD -> { s.thirdX = x; s.thirdY = y }
                DragMode.BODY -> {
                    if (s.tool == TradingTool.FREE_BRUSH) s.pathPoints.forEach { it.x += dx; it.y += dy }
                    else {
                        s.startX += dx; s.startY += dy
                        s.endX += dx; s.endY += dy
                        if (s.tool == TradingTool.TRIANGLE || s.tool == TradingTool.CHANNEL) { s.thirdX += dx; s.thirdY += dy }
                    }
                }
                DragMode.NONE -> {}
            }
        }
    }

    private fun handleEraserDown(x: Float, y: Float) {
        for (i in shapes.size - 1 downTo 0) { if (isHit(x, y, shapes[i])) { shapes.removeAt(i); notifyShapesChange(); break } }
    }

    private fun handleEraserMove(x: Float, y: Float) {
        var changed = false
        for (i in shapes.size - 1 downTo 0) {
            if (isHit(x, y, shapes[i])) { shapes.removeAt(i); changed = true }
        }
        if (changed) notifyShapesChange()
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float) = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0)) < 85

    private fun isHit(x: Float, y: Float, s: DrawShape): Boolean {
        val t = 60f
        return when (s.tool) {
            TradingTool.FREE_BRUSH -> { val b = RectF(); s.createPath().computeBounds(b, true); b.inset(-t, -t); b.contains(x, y) }
            TradingTool.TREND_LINE, TradingTool.RAY -> distToSegment(x, y, s.startX, s.startY, s.endX, s.endY) < t
            TradingTool.SUPPORT_LINE, TradingTool.RESISTANCE_LINE, TradingTool.HORIZONTAL_LINE -> Math.abs(y - s.startY) < t
            TradingTool.VERTICAL_LINE -> Math.abs(x - s.startX) < t
            TradingTool.FIB_RETRACEMENT -> y in (Math.min(s.startY, s.endY) - t)..(Math.max(s.startY, s.endY) + t) && x in (Math.min(s.startX, s.endX) - t)..(Math.max(s.startX, s.endX) + t)
            TradingTool.RECTANGLE, TradingTool.LONG_POSITION, TradingTool.SHORT_POSITION, TradingTool.ZONE -> {
                val l = Math.min(s.startX, s.endX) - t; val r = Math.max(s.startX, s.endX) + t
                val m = s.startY; val d = Math.abs(s.endY - s.startY)
                x in l..r && y in (m - d - t)..(m + d + t)
            }
            TradingTool.CIRCLE -> {
                val r = Math.sqrt((s.endX - s.startX) * (s.endX - s.startX) + (s.endY - s.startY) * (s.endY - s.startY).toDouble()).toFloat()
                Math.abs(Math.sqrt((x - s.startX) * (x - s.startX) + (y - s.startY) * (y - s.startY).toDouble()).toFloat() - r) < t
            }
            TradingTool.TRIANGLE, TradingTool.CHANNEL ->
                distToSegment(x, y, s.startX, s.startY, s.endX, s.endY) < t ||
                distToSegment(x, y, s.endX, s.endY, s.thirdX, s.thirdY) < t ||
                distToSegment(x, y, s.thirdX, s.thirdY, s.startX, s.startY) < t
            TradingTool.MEASURE -> distToSegment(x, y, s.startX, s.startY, s.endX, s.endY) < t
            else -> isNear(x, y, s.startX, s.startY) || isNear(x, y, s.endX, s.endY)
        }
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Double {
        val l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        if (l2 == 0.0f) return Math.sqrt(((px - x1) * (px - x1) + (py - y1) * (py - y1)).toDouble())
        var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
        t = Math.max(0f, Math.min(1f, t))
        val dx = px - (x1 + t * (x2 - x1))
        val dy = py - (y1 + t * (y2 - y1))
        return Math.sqrt((dx * dx + dy * dy).toDouble())
    }
}
