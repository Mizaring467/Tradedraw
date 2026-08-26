package com.example.tradedraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ColorWheelView(context: Context) : View(context) {
    var selectedColor: Int = Color.GREEN
        private set
    var onColorChanged: ((Int) -> Unit)? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        centerX = width / 2f
        centerY = height / 2f
        radius = min(width, height) * 0.43f
        if (radius <= 0f) return

        wheelPaint.shader = SweepGradient(centerX, centerY, intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
        ), null)
        canvas.drawCircle(centerX, centerY, radius, wheelPaint)

        overlayPaint.shader = LinearGradient(
            centerX - radius, centerY, centerX + radius, centerY,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, overlayPaint)

        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        val angle = Math.toRadians((hsv[0] - 90f).toDouble())
        val markerRadius = radius * hsv[1]
        val x = centerX + cos(angle).toFloat() * markerRadius
        val y = centerY + sin(angle).toFloat() * markerRadius
        canvas.drawCircle(x, y, 10f, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distance = min(radius, kotlin.math.sqrt(dx * dx + dy * dy))
        var hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (hue < 0f) hue += 360f
        val saturation = (distance / radius).coerceIn(0f, 1f)
        val hsv = floatArrayOf(hue, saturation, 1f)
        selectedColor = Color.HSVToColor(hsv)
        onColorChanged?.invoke(selectedColor)
        invalidate()
        return true
    }
}
