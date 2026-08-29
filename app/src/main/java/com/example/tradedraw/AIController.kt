package com.example.tradedraw

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class AIController(private val context: Context) {

    private var isAutoTrading = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = 0L
    private var framesAnalyzedCount = 0

    // Este delay asegura que no saturemos al procesador analizando 60 frames por segundo.
    // Analizaremos, por ejemplo, 1 frame cada 1 segundo (1000ms).
    private val PROCESS_INTERVAL_MS = 1000L

    fun toggleAutoTrade(enabled: Boolean) {
        isAutoTrading = enabled
        framesAnalyzedCount = 0
        if (enabled) {
            Log.d("AIController", "AutoTrading START")
            Toast.makeText(context, "IA Activada: Observando gráfico...", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("AIController", "AutoTrading STOP")
            Toast.makeText(context, "IA Detenida", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAutoTradingEnabled(): Boolean = isAutoTrading

    fun onNewFrame(bitmap: Bitmap) {
        if (!isAutoTrading) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime < PROCESS_INTERVAL_MS) {
            return // Skip frame para no saturar
        }
        lastFrameTime = currentTime

        processImageWithAI(bitmap)
    }

    private fun processImageWithAI(bitmap: Bitmap) {
        // AQUÍ VA LA INTEGRACIÓN CON LA IA (OpenAI Vision, TensorFlow Lite, etc.)
        // La idea es enviar el `bitmap` (o redimensionarlo antes) al modelo.

        framesAnalyzedCount++

        if (framesAnalyzedCount % 5 == 0) {
            // Feedback visual para que el usuario sepa que la app sí está haciendo algo
            Toast.makeText(context, "IA: Analizando frame ($framesAnalyzedCount)...", Toast.LENGTH_SHORT).show()
        }

        Log.d("AIController", "Analizando frame de ${bitmap.width}x${bitmap.height}...")

        // ==============================================================
        // MOCK DE DECISIÓN DE LA IA:
        // ==============================================================
        val decision = Math.random()
        if (decision > 0.95) { // 5% de probabilidad de comprar (Sube)
             executeTrade(TradeAction.BUY)
        } else if (decision < 0.05) { // 5% de probabilidad de vender (Baja)
             executeTrade(TradeAction.SELL)
        }
    }

    private fun executeTrade(action: TradeAction) {
        Log.d("AIController", "Ejecutando acción de Trading: $action")

        val actionText = if (action == TradeAction.BUY) "COMPRAR (Sube)" else "VENDER (Baja)"
        Toast.makeText(context, "IA DECIDIÓ: $actionText", Toast.LENGTH_LONG).show()

        val accessibilityService = AutoTradeAccessibilityService.instance
        if (accessibilityService != null) {
            // Coordenadas aproximadas basadas en la resolución del dispositivo (Binomo típico)
            val screenMetrics = context.resources.displayMetrics
            val w = screenMetrics.widthPixels.toFloat()
            val h = screenMetrics.heightPixels.toFloat()

            // Suponiendo botones en la esquina inferior izquierda
            val (x, y) = when (action) {
                TradeAction.BUY -> Pair(w * 0.25f, h * 0.85f)
                TradeAction.SELL -> Pair(w * 0.75f, h * 0.85f)
            }

            accessibilityService.performClickAt(x, y)
        } else {
            Toast.makeText(context, "Por favor habilita el Servicio de Accesibilidad", Toast.LENGTH_LONG).show()
            Log.e("AIController", "Error: AutoTradeAccessibilityService no está conectado.")
        }
    }
}

enum class TradeAction {
    BUY, SELL
}
