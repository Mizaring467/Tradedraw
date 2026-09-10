package com.example.tradedraw

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Controlador de Navegación y Acomodado Gestual del Gráfico (Binomo):
 * - Detecta cuando el gráfico o el precio se han desfasado hacia los bordes.
 * - Simula arrastres táctiles naturales (swipes suaves) para recentrar el gráfico.
 * - Respeta salvaguardas estrictas: nunca mueve durante un trade activo ni en la ventana sniper.
 */
class ChartViewportController(
    private val context: Context? = null,
    private val riskManager: RiskManager? = null
) {
    companion object {
        private const val TAG = "ChartViewport"
        const val MIN_SWIPE_COOLDOWN_MS = 20000L // Mínimo 20s entre auto-ajustes
        const val GESTURE_DURATION_MS = 320L
    }

    @Volatile
    var lastAdjustmentTime: Long = 0L
        internal set

    @Volatile
    var isEnabled: Boolean = true

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Evalúa si es seguro ejecutar un gesto de desplazamiento sobre la pantalla.
     */
    fun canPerformGesture(candleSecond: Int = ((System.currentTimeMillis() / 1000) % 60).toInt()): Pair<Boolean, String> {
        if (!isEnabled) {
            return Pair(false, "Controlador de viewport desactivado")
        }
        if (riskManager?.hasPendingTrade == true) {
            return Pair(false, "Operación abierta en curso")
        }
        // Veto durante ventana sniper (:50-:59 y :00-:06) para no interferir con disparos
        if (candleSecond in 50..59 || candleSecond in 0..6) {
            return Pair(false, "Ventana sniper crítica (${candleSecond}s)")
        }
        val elapsed = System.currentTimeMillis() - lastAdjustmentTime
        if (elapsed < MIN_SWIPE_COOLDOWN_MS) {
            val waitSec = ((MIN_SWIPE_COOLDOWN_MS - elapsed) / 1000) + 1
            return Pair(false, "Cooldown de acomodado (${waitSec}s restantes)")
        }
        return Pair(true, "Seguro para acomodar")
    }

    /**
     * Analiza el resultado de visión y decide si ejecutar un arrastre correctivo.
     * Retorna true si se ejecutó un gesto de ajuste.
     */
    fun checkAndAdjustViewport(
        analysis: VisionAnalysisResult,
        screenW: Int,
        screenH: Int
    ): Boolean {
        val (canGesture, reason) = canPerformGesture()
        if (!canGesture) {
            Log.d(TAG, "checkAndAdjustViewport omitido: $reason")
            return false
        }

        val service = AutoTradeAccessibilityService.instance ?: return false
        val w = screenW.toFloat()
        val h = screenH.toFloat()

        // 1. Gráfico demasiado corrido a la derecha (Vela actual pegada al margen derecho)
        // Deslizar suavemente de derecha a izquierda para dar margen por delante
        if (analysis.isChartOffCenterRight) {
            val startX = w * 0.62f
            val endX = w * 0.38f
            val y = h * 0.50f
            performSwipeWithFeedback(service, startX, y, endX, y, "↔️ Recentrado: Gráfico corrido a la derecha")
            return true
        }

        // 2. Gráfico demasiado corrido a la izquierda (Vela actual antes del centro)
        // Deslizar suavemente de izquierda a derecha para traer la acción reciente
        if (analysis.isChartOffCenterLeft) {
            val startX = w * 0.38f
            val endX = w * 0.62f
            val y = h * 0.50f
            performSwipeWithFeedback(service, startX, y, endX, y, "↔️ Recentrado: Gráfico atrasado en el pasado")
            return true
        }

        // 3. Precio demasiado bajo (cerca del HUD / borde inferior)
        // Deslizar hacia arriba para subir el gráfico al centro
        if (analysis.isPriceNearBottom) {
            val x = w * 0.45f
            val startY = h * 0.55f
            val endY = h * 0.42f
            performSwipeWithFeedback(service, x, startY, x, endY, "↕️ Recentrado vertical: Precio cerca del borde inferior")
            return true
        }

        // 4. Precio demasiado alto (cerca de pestañas / borde superior)
        // Deslizar hacia abajo para bajar el gráfico al centro
        if (analysis.isPriceNearTop) {
            val x = w * 0.45f
            val startY = h * 0.42f
            val endY = h * 0.55f
            performSwipeWithFeedback(service, x, startY, x, endY, "↕️ Recentrado vertical: Precio cerca del borde superior")
            return true
        }

        return false
    }

    /**
     * Recentra manualmente el gráfico a petición del usuario o del chat sin esperar condiciones de desvío.
     */
    fun forceRecenter(screenW: Int, screenH: Int, reasonDescription: String = "Petición manual"): Boolean {
        if (riskManager?.hasPendingTrade == true) {
            notifyUser("⚠️ No se puede mover el gráfico: Hay una operación abierta en curso")
            return false
        }
        val service = AutoTradeAccessibilityService.instance
        if (service == null) {
            notifyUser("⚠️ Accesibilidad inactiva para mover el gráfico")
            return false
        }

        val w = screenW.toFloat()
        val h = screenH.toFloat()
        val startX = w * 0.60f
        val endX = w * 0.40f
        val y = h * 0.50f
        performSwipeWithFeedback(service, startX, y, endX, y, "✥ Gráfico re-centrado ($reasonDescription)")
        return true
    }

    private fun performSwipeWithFeedback(
        service: AutoTradeAccessibilityService,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        description: String
    ) {
        lastAdjustmentTime = System.currentTimeMillis()
        Log.i(TAG, "Ejecutando swipe de ajuste de ($x1, $y1) a ($x2, $y2): $description")
        service.performSwipe(x1, y1, x2, y2, GESTURE_DURATION_MS)

        mainHandler.post {
            OverlayService.instance?.drawingView?.triggerClickAnimation(x1, y1)
            notifyUser("🤖 AGENTE: $description")
        }
    }

    private fun notifyUser(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}
