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
    
    private var currentTool = TradingTool.FREE_BRUSH
    private var activeDrawingColor = Color.parseColor("#00FF00")
    private var currentStrokeWidth = 7f

    private var isCanvasVisible = true

    private var currentShape: DrawShape? = null
    private var selectedShape: DrawShape? = null
    
    private enum class DragMode { NONE, START, END, BODY }
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val dashPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
    private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val handleStrokePaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }

    fun setTool(tool: TradingTool) {
        this.currentTool = tool
        if (tool != TradingTool.SELECT_TOUCH) deselectAll()
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

    private fun deselectAll() { shapes.forEach { it.isSelected = false }; selectedShape = null }

    fun undo() { if (shapes.isNotEmpty()) { undoneShapes.add(shapes.removeAt(shapes.size - 1)); deselectAll(); invalidate() } }
    fun redo() { if (undoneShapes.isNotEmpty()) { shapes.add(undoneShapes.removeAt(undoneShapes.size - 1)); deselectAll(); invalidate() } }
    
    fun deleteSelectedOrLast() {
        if (selectedShape != null) {
            val s = selectedShape!!
            shapes.remove(s); undoneShapes.add(s); selectedShape = null; invalidate()
        } else if (shapes.isNotEmpty()) {
            undo()
        }
    }

    fun clearCanvas() { shapes.clear(); undoneShapes.clear(); currentShape = null; selectedShape = null; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCanvasVisible) return
        for (shape in shapes) {
            drawGenericShape(canvas, shape)
            if (shape.isSelected) drawHandles(canvas, shape)
        }
        currentShape?.let { drawGenericShape(canvas, it) }
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
        }
    }

    private fun drawGenericShape(canvas: Canvas, shape: DrawShape) {
        val paint = Paint().apply {
            color = shape.color; strokeWidth = shape.strokeWidth; isAntiAlias = true
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            style = if (shape.tool == TradingTool.LONG_POSITION || shape.tool == TradingTool.SHORT_POSITION) Paint.Style.FILL else Paint.Style.STROKE
        }
        when (shape.tool) {
            TradingTool.FREE_BRUSH -> canvas.drawPath(shape.createPath(), paint)
            TradingTool.TREND_LINE -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
            TradingTool.SUPPORT_LINE -> {
                canvas.drawLine(0f, shape.startY, width.toFloat(), shape.startY, paint)
                canvas.drawText("SUPPORT", 25f, shape.startY - 15f, textPaint)
            }
            TradingTool.RESISTANCE_LINE -> {
                canvas.drawLine(0f, shape.startY, width.toFloat(), shape.startY, paint)
                canvas.drawText("RESISTANCE", 25f, shape.startY - 15f, textPaint)
            }
            TradingTool.RECTANGLE -> canvas.drawRect(Math.min(shape.startX, shape.endX), Math.min(shape.startY, shape.endY), Math.max(shape.startX, shape.endX), Math.max(shape.startY, shape.endY), paint)
            TradingTool.FIB_RETRACEMENT -> drawFibonacci(canvas, shape, paint)
            TradingTool.LONG_POSITION -> drawPosition(canvas, shape, isLong = true)
            TradingTool.SHORT_POSITION -> drawPosition(canvas, shape, isLong = false)
            else -> {}
        }
    }

    private fun drawFibonacci(canvas: Canvas, shape: DrawShape, paint: Paint) {
        val levels = listOf(0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f)
        val labels = listOf("0%", "23.6%", "38.2%", "50%", "61.8%", "78.6%", "100%")
        val diffY = shape.endY - shape.startY
        canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
        dashPaint.color = Color.LTGRAY
        for (i in levels.indices) {
            val levelY = shape.startY + diffY * levels[i]
            canvas.drawLine(0f, levelY, width.toFloat(), levelY, dashPaint)
            canvas.drawText(labels[i], width - 110f, levelY - 10f, textPaint)
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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (handleAutoSelection(x, y)) {
                    // Seleccionado o manipulando nodo
                } else if (currentTool == TradingTool.ERASER_TOUCH) {
                    handleEraserDown(x, y)
                } else if (selectedShape == null) {
                    // Solo dibujar si NO hay nada seleccionado para evitar trazos accidentales
                    undoneShapes.clear()
                    val colorToUse = when (currentTool) {
                        TradingTool.SUPPORT_LINE -> Color.RED
                        TradingTool.RESISTANCE_LINE -> Color.GREEN
                        else -> activeDrawingColor
                    }
                    currentShape = DrawShape(currentTool, x, y, x, y, color = colorToUse, strokeWidth = currentStrokeWidth)
                    if (currentTool == TradingTool.FREE_BRUSH) currentShape?.pathPoints?.add(PointF(x, y))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedShape != null && dragMode != DragMode.NONE) {
                    handleSelectionMove(x, y)
                } else {
                    currentShape?.let { 
                        it.endX = x; it.endY = y
                        if (it.tool == TradingTool.FREE_BRUSH) it.pathPoints.add(PointF(x, y))
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (selectedShape == null) {
                    currentShape?.let { shapes.add(it) }
                    currentShape = null
                }
                dragMode = DragMode.NONE
            }
        }
        lastX = x; lastY = y
        invalidate(); return true
    }

    private fun handleAutoSelection(x: Float, y: Float): Boolean {
        // 1. Probar nodos del objeto ya seleccionado para Redimensionar
        selectedShape?.let { s ->
            if (s.tool != TradingTool.FREE_BRUSH) {
                if (isNear(x, y, s.startX, s.startY)) { dragMode = DragMode.START; return true }
                if (isNear(x, y, s.endX, s.endY)) { dragMode = DragMode.END; return true }
            }
            // Probar cuerpo para Mover
            if (isHit(x, y, s)) { dragMode = DragMode.BODY; return true }
        }

        // 2. Buscar nueva figura para Seleccionar (Prioridad a figuras sobre trazos libres)
        for (i in shapes.size - 1 downTo 0) {
            if (isHit(x, y, shapes[i])) {
                deselectAll()
                shapes[i].isSelected = true
                selectedShape = shapes[i]
                dragMode = DragMode.BODY
                return true
            }
        }
        
        // 3. Si se toca vacío, deseleccionar
        if (selectedShape != null) { deselectAll(); return true }
        return false
    }

    private fun handleSelectionMove(x: Float, y: Float) {
        val dx = x - lastX; val dy = y - lastY
        selectedShape?.let { s ->
            when (dragMode) {
                DragMode.START -> { s.startX = x; s.startY = y }
                DragMode.END -> { s.endX = x; s.endY = y }
                DragMode.BODY -> {
                    if (s.tool == TradingTool.FREE_BRUSH) s.pathPoints.forEach { it.x += dx; it.y += dy }
                    else { s.startX += dx; s.startY += dy; s.endX += dx; s.endY += dy }
                }
                DragMode.NONE -> {}
            }
        }
    }

    private fun handleEraserDown(x: Float, y: Float) {
        for (i in shapes.size - 1 downTo 0) { if (isHit(x, y, shapes[i])) { shapes.removeAt(i); break } }
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float) = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0)) < 85

    private fun isHit(x: Float, y: Float, s: DrawShape): Boolean {
        val t = 60f
        return when (s.tool) {
            TradingTool.FREE_BRUSH -> { val b = RectF(); s.createPath().computeBounds(b, true); b.inset(-t, -t); b.contains(x, y) }
            TradingTool.TREND_LINE -> distToSegment(x, y, s.startX, s.startY, s.endX, s.endY) < t
            TradingTool.SUPPORT_LINE, TradingTool.RESISTANCE_LINE -> Math.abs(y - s.startY) < t
            TradingTool.FIB_RETRACEMENT -> y in (Math.min(s.startY, s.endY) - t)..(Math.max(s.startY, s.endY) + t)
            TradingTool.RECTANGLE, TradingTool.LONG_POSITION, TradingTool.SHORT_POSITION -> {
                val l = Math.min(s.startX, s.endX) - t; val r = Math.max(s.startX, s.endX) + t
                val m = s.startY; val d = Math.abs(s.endY - s.startY)
                x in l..r && y in (m - d - t)..(m + d + t)
            }
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
