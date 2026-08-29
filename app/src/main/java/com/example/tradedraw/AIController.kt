package com.example.tradedraw

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log

class AIController {

    private var isAutoTrading = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = 0L

    // Este delay asegura que no saturemos al procesador analizando 60 frames por segundo.
    // Analizaremos, por ejemplo, 1 frame cada 1 segundo (1000ms).
    private val PROCESS_INTERVAL_MS = 1000L

    fun toggleAutoTrade(enabled: Boolean) {
        isAutoTrading = enabled
        if (enabled) {
            Log.d("AIController", "AutoTrading START")
        } else {
            Log.d("AIController", "AutoTrading STOP")
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
        // Por ahora, simulamos una decisión aleatoria o loggeamos que se está procesando.

        Log.d("AIController", "Analizando frame de ${bitmap.width}x${bitmap.height}...")

        // ==============================================================
        // MOCK DE DECISIÓN DE LA IA:
        // ==============================================================
        // val decision = Math.random()
        // if (decision > 0.95) { // 5% de probabilidad de comprar (Sube)
        //     executeTrade(TradeAction.BUY)
        // } else if (decision < 0.05) { // 5% de probabilidad de vender (Baja)
        //     executeTrade(TradeAction.SELL)
        // }
    }

    private fun executeTrade(action: TradeAction) {
        Log.d("AIController", "Ejecutando acción de Trading: $action")

        val accessibilityService = AutoTradeAccessibilityService.instance
        if (accessibilityService != null) {
            // Nota: Las coordenadas dependerán de la resolución del dispositivo y de la posición
            // de los botones en la app de Binomo. En el futuro, la IA podría devolver las
            // coordenadas exactas o se puede usar un sistema de reconocimiento de UI.

            val (x, y) = when (action) {
                TradeAction.BUY -> Pair(800f, 1500f) // Coordenadas MOCK para "Sube" (Verde)
                TradeAction.SELL -> Pair(300f, 1500f) // Coordenadas MOCK para "Baja" (Rojo)
            }

            accessibilityService.performClickAt(x, y)
        } else {
            Log.e("AIController", "Error: AutoTradeAccessibilityService no está conectado.")
        }
    }
}

enum class TradeAction {
    BUY, SELL
}
