package com.example.tradedraw

/**
 * R26: caducidad de `AUTONOMOUS` cuando se arma desde el bridge y el host deja de dar senal.
 *
 * DEFECTO QUE CIERRA. `AUTONOMOUS` no caducaba: si el telefono perdia la Wi-Fi (R25/W02),
 * el host se cerraba, o simplemente se dejaba de sondear, el motor seguia armado e invisible
 * indefinidamente. El Incidente 2 de R24 dejo 3 h 15 min de exposicion y el intento 1 de W02
 * (R25) dejo ~5 min armado sin red. No hubo trades en ninguno de los dos, pero por suerte.
 *
 * ALCANCE DELIBERADAMENTE ACOTADO: solo caduca lo que se armo desde el bridge, que es el unico
 * caso en el que la supervision depende de un host externo. Una sesion armada desde el HUD no
 * caduca: el operador esta presente por definicion (acaba de pulsar el boton) y caducarla seria
 * un cambio de comportamiento no pedido.
 *
 * Es una politica pura (sin relojes propios ni Android) para poder probarla en JVM: el tiempo se
 * inyecta en cada llamada.
 */
class AutonomousWatchdog(private val timeoutMs: Long) {

    private var lastContactMs = 0L
    private var armedByBridge = false

    /**
     * Fija el instante de armado. Con `byBridge = false` (HUD) la caducidad queda desactivada y
     * `shouldExpire` siempre devuelve false, hagas el heartbeat que hagas.
     */
    @Synchronized
    fun arm(nowMs: Long, byBridge: Boolean) {
        armedByBridge = byBridge
        lastContactMs = if (byBridge) nowMs else 0L
    }

    /** Cualquier peticion al bridge cuenta como senal de vida del host que supervisa. */
    @Synchronized
    fun heartbeat(nowMs: Long) {
        if (armedByBridge) lastContactMs = nowMs
    }

    @Synchronized
    fun disarm() {
        armedByBridge = false
        lastContactMs = 0L
    }

    /** true solo si estaba armado desde el bridge, hubo al menos un contacto y el silencio llega al tope. */
    @Synchronized
    fun shouldExpire(nowMs: Long): Boolean =
        armedByBridge && lastContactMs > 0L && nowMs - lastContactMs >= timeoutMs

    /** Silencio acumulado desde el ultimo contacto; 0 si no aplica caducidad. */
    @Synchronized
    fun silenceMs(nowMs: Long): Long =
        if (!armedByBridge || lastContactMs <= 0L) 0L else (nowMs - lastContactMs).coerceAtLeast(0L)
}
