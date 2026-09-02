package com.example.tradedraw

import android.content.Context
import android.graphics.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugVisualizer {

    private var lastSaveTime = 0L
    private const val SAVE_INTERVAL_MS = 2000L // Máximo 1 frame cada 2 segundos para no saturar almacenamiento

    fun saveDebugFrame(
        context: Context,
        originalBitmap: Bitmap,
        analysis: VisionAnalysisResult,
        scanBounds: RectF,
        detectedResistanceY: Float,
        detectedSupportY: Float,
        currentPriceY: Float
    ) {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime < SAVE_INTERVAL_MS) return
        lastSaveTime = now

        try {
            val debugBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(debugBitmap)
            val w = debugBitmap.width.toFloat()
            val h = debugBitmap.height.toFloat()

            // 1. Dibujar rectángulo de zona escaneada (Amarillo)
            val zonePaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            canvas.drawRect(scanBounds, zonePaint)

            // 2. Dibujar velas detectadas (puntos verdes y rojos sobre los cuerpos)
            val greenDotPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
            val redDotPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
            val candleBarPaint = Paint().apply { strokeWidth = 3f; style = Paint.Style.STROKE }

            analysis.candleList.forEach { candle ->
                val dotPaint = if (candle.type == CandleType.GREEN) greenDotPaint else redDotPaint
                candleBarPaint.color = if (candle.type == CandleType.GREEN) Color.GREEN else Color.RED

                // Línea vertical del rango de vela
                canvas.drawLine(candle.x, candle.topY, candle.x, candle.bottomY, candleBarPaint)
                // Punto en el cuerpo
                canvas.drawCircle(candle.x, (candle.bodyTopY + candle.bodyBottomY) / 2f, 4f, dotPaint)
            }

            // 3. Línea de Resistencia calculada (Cian brillante)
            val resistPaint = Paint().apply {
                color = Color.CYAN
                strokeWidth = 5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            canvas.drawLine(0f, detectedResistanceY, w, detectedResistanceY, resistPaint)

            // 4. Línea de Soporte calculada (Naranja brillante)
            val supportPaint = Paint().apply {
                color = Color.parseColor("#fb923c")
                strokeWidth = 5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            canvas.drawLine(0f, detectedSupportY, w, detectedSupportY, supportPaint)

            // 5. Línea de Precio Actual detectado (Blanco punteado)
            val pricePaint = Paint().apply {
                color = Color.WHITE
                strokeWidth = 3f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            canvas.drawLine(0f, currentPriceY, w, currentPriceY, pricePaint)

            // 6. Tarjeta de texto de diagnóstico en la esquina superior
            val bgCardPaint = Paint().apply {
                color = Color.argb(220, 10, 10, 15)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(20f, 20f, 620f, 260f, 12f, 12f, bgCardPaint)

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val isLand = w > h
            val orientStr = if (isLand) "HORIZONTAL (Landscape)" else "VERTICAL (Portrait)"
            canvas.drawText("🛠️ TRADEDRAW DEBUG [${orientStr}]", 35f, 55f, textPaint.apply { color = Color.YELLOW })
            canvas.drawText("Velas detectadas: ${analysis.candleList.size} | ${analysis.streakBadge}", 35f, 90f, textPaint.apply { color = Color.WHITE })
            canvas.drawText("Resistencia Y: ${detectedResistanceY.toInt()}px (Techo)", 35f, 125f, textPaint.apply { color = Color.CYAN })
            canvas.drawText("Soporte Y: ${detectedSupportY.toInt()}px (Suelo)", 35f, 160f, textPaint.apply { color = Color.parseColor("#fb923c") })
            canvas.drawText("Precio Actual Y: ${currentPriceY.toInt()}px", 35f, 195f, textPaint.apply { color = Color.WHITE })
            canvas.drawText("Zona: Y[${scanBounds.top.toInt()}..${scanBounds.bottom.toInt()}] X[${scanBounds.left.toInt()}..${scanBounds.right.toInt()}]", 35f, 230f, textPaint.apply { color = Color.GRAY })

            // 7. Guardar en almacenamiento público o privado
            val targetDir = File("/storage/emulated/0/TradeDraw")
            val dir = if (targetDir.exists() || targetDir.mkdirs()) {
                targetDir
            } else {
                File(context.getExternalFilesDir(null), "TradeDraw_Debug").apply { if (!exists()) mkdirs() }
            }

            val timeStamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "DEBUG_${if (isLand) "HORIZ" else "VERT"}_$timeStamp.jpg")

            FileOutputStream(file).use { out ->
                debugBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d("DebugVisualizer", "Frame guardado: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("DebugVisualizer", "Error guardando frame de debug", e)
        }
    }
}
