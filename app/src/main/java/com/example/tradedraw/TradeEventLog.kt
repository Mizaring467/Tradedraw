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

    /**
     * Ronda 18: capacidad del ring DEDICADO a [Kind.MODE_CHANGE].
     *
     * MOTIVO. El ring compartido de [CAPACIDAD] esta dominado por [Kind.ASSET] (se emite por
     * tick). En R15-A el volcado con `max=60` devolvio CERO `MODE_CHANGE` porque 73 `ASSET` en
     * minutos los habian desplazado, y la evidencia de modo casi se perdio. Un cambio de modo es
     * raro y critico: no debe competir por espacio con el flujo de activos.
     */
    const val MODE_CAPACIDAD = 100

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
        MODE_CHANGE,
        /**
         * Ronda 20: el activo activo no entrego NI UN tick y el cliente volvio al ultimo RIC que
         * si los entrego.
         *
         * Existe porque `subscribe` ACEPTADO no prueba que haya datos: medido con `AAVEUSD-OTC`,
         * el servidor responde `success:true` y despues 0 ticks en 60 s. Sin este evento, volver
         * a un activo sano seria indistinguible de un cambio de activo pedido por el usuario.
         */
        FEED_SIN_TICKS,
        /**
         * Ronda 21 (Frente E): decisión autorizada en modo SHADOW (cero taps, cero impacto en balance).
         */
        SHADOW_DECISION,
        /**
         * R24/B1.c: incremento MANUAL del marcador desde una herramienta de operador.
         *
         * Existe para que el validador pueda RESTAR del lado `/status` los incrementos que no
         * provienen de una liquidacion del motor. Sin esta etiqueta, un `/win` manual incrementa
         * el contador sin dejar fila en el journal y cualquier contraste contra el ledger da
         * discrepancia por diseno. El detalle lleva el origen: `[MANUAL_BRIDGE]` o `[MANUAL_HUD]`.
         *
         * Tipo propio y no `ORDER_STATE` a proposito: reutilizar el existente contaminaria su
         * contador y romperia la lectura de transiciones de orden.
         */
        MANUAL_INCREMENT
    }

    data class Event(
        val atMs: Long,
        val kind: Kind,
        val detail: String,
        val decisionId: String? = null,
        val tradeId: String? = null
    ) {
        /** Representacion estable para el bridge, sin `Locale` implicito. */
        fun toJson(): String {
            val sb = StringBuilder()
            sb.append("{\"atMs\":").append(atMs)
                .append(",\"kind\":\"").append(kind.name).append("\"")
                .append(",\"detail\":\"").append(detail.replace("\\", "").replace("\"", "'")).append("\"")
            if (!decisionId.isNullOrBlank()) {
                sb.append(",\"decisionId\":\"").append(decisionId.replace("\\", "").replace("\"", "'")).append("\"")
            }
            if (!tradeId.isNullOrBlank()) {
                sb.append(",\"tradeId\":\"").append(tradeId.replace("\\", "").replace("\"", "'")).append("\"")
            }
            sb.append("}")
            return sb.toString()
        }
    }

    private val buffer = ArrayDeque<Event>(CAPACIDAD)

    /**
     * Ronda 18: ring DEDICADO e INDEPENDIENTE para [Kind.MODE_CHANGE].
     *
     * No comparte espacio con el buffer general, asi que 500 eventos `ASSET` no lo desplazan.
     * Sigue siendo un ring: al llenarse descarta el mas antiguo, que es lo correcto para
     * observar una sesion viva.
     */
    private val modeBuffer = ArrayDeque<Event>(MODE_CAPACIDAD)

    /**
     * Ronda 18: sumidero de persistencia solo-append. Se inyecta desde fuera para no arrastrar
     * `Context` a este `object` y para poder testear la persistencia en JVM puro.
     *
     * Recibe la linea ya formateada. Si lanza, [add] lo traga: un fallo de disco NUNCA puede
     * impedir ni alterar un cambio de modo.
     */
    @Volatile
    var persistSink: ((String) -> Unit)? = null

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
    fun add(
        kind: Kind,
        detail: String,
        decisionId: String? = null,
        tradeId: String? = null
    ) {
        if (buffer.size >= CAPACIDAD) {
            buffer.removeFirst()
        }
        val ev = Event(System.currentTimeMillis(), kind, detail, decisionId, tradeId)
        buffer.addLast(ev)
        val clave = if (kind == Kind.VETO) "VETO:" + detail.trim().substringBefore(' ') else kind.name
        counters[clave] = (counters[clave] ?: 0) + 1

        // Ronda 18: el ring dedicado y la persistencia solo aplican a MODE_CHANGE. Es un evento
        // raro: el coste de la rama es despreciable y no anade trabajo al camino caliente (ASSET).
        if (kind == Kind.MODE_CHANGE) {
            if (modeBuffer.size >= MODE_CAPACIDAD) {
                modeBuffer.removeFirst()
            }
            modeBuffer.addLast(ev)
            try {
                persistSink?.invoke(ev.toJson())
            } catch (e: Exception) {
                // Un fallo de disco no puede impedir un cambio de modo ni cambiar su semantica.
            }
        }
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
        modeBuffer.clear()
    }

    /**
     * Ronda 18: eventos de modo mas recientes primero, del ring DEDICADO.
     *
     * Independiente de [recent]: sobrevive a que el buffer general se llene de `ASSET`.
     */
    @Synchronized
    fun recentModes(max: Int = MODE_CAPACIDAD): List<Event> =
        modeBuffer.toList().asReversed().take(max.coerceIn(1, MODE_CAPACIDAD))

    /**
     * Ronda 18: JSON para `/mode_log`. Es un endpoint NUEVO a proposito: asi la compatibilidad
     * de `/events` (campos `success`, `total`, `vetoV1`, `vetoV2`, `events` y su significado) se
     * garantiza por construccion y no por cuidado.
     */
    @Synchronized
    fun modesToJson(max: Int = MODE_CAPACIDAD): String {
        val lista = recentModes(max).joinToString(",") { it.toJson() }
        return String.format(
            Locale.US,
            "{\"success\":true,\"total\":%d,\"capacidad\":%d,\"count\":%d,\"events\":[%s]}",
            modeBuffer.size, MODE_CAPACIDAD, countOf(Kind.MODE_CHANGE), lista
        )
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
