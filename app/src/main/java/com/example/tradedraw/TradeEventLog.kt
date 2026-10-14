package com.example.tradedraw

import java.util.Locale

/**
 * Ronda 13 (adenda, A4/A5): buffer circular EN MEMORIA de eventos de ejecucion.
 *
 * MOTIVO. `logcat` no es una fuente de evidencia en este dispositivo: `init.svc.logd` esta
 * `stopped` y no se puede levantar sin root (build `user`, `ro.debuggable=0`). Se comprobo
 * que falla igual por USB y por ADB Wi-Fi, asi que no era el transporte. Consecuencia: los
 * vetos V1/V2 y las transiciones de estado NO eran observables, y R12 no pudo demostrar ni
 * una sola vez que un veto se disparara ni por que una orden no se registro.
 *
 * Este buffer sustituye a `logcat` como fuente de evidencia observable desde el bridge
 * (`/events`), sin depender de root ni del sistema de logging.
 *
 * Es PURAMENTE en memoria y de tamano fijo: no crece, no se persiste y no toca el journal
 * (cuya contabilidad de confirmacion no debe alterarse). Perderlo al reiniciar es correcto:
 * describe una sesion viva, no un historico.
 */
object TradeEventLog {

    /** Numero maximo de eventos conservados. ~200 como pide el enunciado. */
    const val CAPACIDAD = 200

    enum class Kind {
        /** Un veto del motor impidio despachar la entrada. */
        VETO,
        /** Transicion de `RiskManager.OrderState`. */
        ORDER_STATE,
        /** Tap despachado al broker (antes de saber si registro la orden). */
        TAP,
        /** Cambio de activo observado por accesibilidad o por el feed. */
        ASSET,
        /** El payout leido y el deducido del boton no concuerdan. */
        PAYOUT_DISCREPANTE,
        /** Se detecto un activo activo sin feed (A2c). */
        FEED_MUDO,
        /**
         * Ronda 14 (B1): el mercado del activo activo lleva N segundos sin rango suficiente.
         * Es un AVISO, no una decision: el bot nunca cambia de activo por esto.
         */
        INOPERABLE,
        /**
         * Ronda 15-A: transicion del modo de trading (`TradingEngine.mode`).
         *
         * Existe para atribuir el rearme espontaneo a `AUTONOMOUS` observado dos veces en R14.
         * El detalle lleva el modo anterior, el nuevo y el ORIGEN (pila de llamadas), que es lo
         * que permite distinguir una ruta automatica de un toque humano.
         *
         * Es PURA observabilidad: registrar el cambio no altera el cambio.
         */
        MODE_CHANGE
    }

    data class Event(
        val atMs: Long,
        val kind: Kind,
        val detail: String
    ) {
        /** Representacion estable para el bridge, sin `Locale` implicito. */
        fun toJson(): String = String.format(
            Locale.US,
            "{\"atMs\":%d,\"kind\":\"%s\",\"detail\":\"%s\"}",
            atMs, kind.name, detail.replace("\\", "").replace("\"", "'")
        )
    }

    private val buffer = ArrayDeque<Event>(CAPACIDAD)

    /** Contadores por tipo: sobreviven al recorte del buffer, que es lo que exige A5. */
    private val counters = LinkedHashMap<String, Int>()

    /**
     * Registra un evento. `detail` debe ser corto y NO contener datos sensibles:
     * se expone por HTTP.
     *
     * Para [Kind.VETO] el contador se indexa por el PRIMER TOKEN del detalle (V1, V2,
     * FEED_RANCIO, CONGELADO...), no por el texto completo. Es imprescindible: los detalles
     * incluyen valores medidos (`"V2 rango=1.2e-09 ..."`), asi que indexar por el texto entero
     * haria que cada veto fuera una clave distinta y los contadores de A5 quedarian inertes
     * (cada uno a 1). El primer token es la etiqueta estable del motivo.
     */
    @Synchronized
    fun add(kind: Kind, detail: String) {
        if (buffer.size >= CAPACIDAD) {
            buffer.removeFirst()
        }
        buffer.addLast(Event(System.currentTimeMillis(), kind, detail))
        val clave = if (kind == Kind.VETO) "VETO:" + detail.trim().substringBefore(' ') else kind.name
        counters[clave] = (counters[clave] ?: 0) + 1
    }

    /**
     * Cuenta eventos de un tipo. Para vetos, `motivo` filtra por la etiqueta del motivo
     * (primer token del detalle: `V1`, `V2`, ...), que es como se distinguen V1 y V2 (A5).
     */
    @Synchronized
    fun countOf(kind: Kind, motivo: String? = null): Int {
        if (kind == Kind.VETO) {
            return if (motivo == null) {
                counters.entries.filter { it.key.startsWith("VETO:") }.sumOf { it.value }
            } else {
                counters["VETO:$motivo"] ?: 0
            }
        }
        return counters[kind.name] ?: 0
    }

    /** Ultimo evento de un tipo, o null. Se usa para "ultimo motivo". */
    @Synchronized
    fun lastOf(kind: Kind): Event? = buffer.lastOrNull { it.kind == kind }

    /** Copia de los eventos mas recientes primero. Inmutable para el llamante. */
    @Synchronized
    fun recent(max: Int = CAPACIDAD): List<Event> =
        buffer.toList().asReversed().take(max.coerceIn(1, CAPACIDAD))

    @Synchronized
    fun clear() {
        buffer.clear()
        counters.clear()
    }

    /** JSON para `/events`. */
    @Synchronized
    fun toJson(max: Int = 60): String {
        val lista = recent(max).joinToString(",") { it.toJson() }
        val vetosV1 = countOf(Kind.VETO, "V1")
        val vetosV2 = countOf(Kind.VETO, "V2")
        return String.format(
            Locale.US,
            "{\"success\":true,\"total\":%d,\"vetoV1\":%d,\"vetoV2\":%d,\"events\":[%s]}",
            buffer.size, vetosV1, vetosV2, lista
        )
    }
}
