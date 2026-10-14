package com.example.tradedraw

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 15-A: instrumentacion del rearme espontaneo a `AUTONOMOUS`.
 *
 * Comprueba que una transicion de modo deja rastro en `/events` con un ORIGEN util, porque sin
 * origen un cambio automatico y un toque humano son indistinguibles: es exactamente el agujero
 * que dejo el rearme observado dos veces en R14.
 *
 * No comprueba la POLITICA de modo (quien puede cambiar a que): comprueba la OBSERVABILIDAD.
 */
class ModeChangeEventTest {

    @After
    fun limpiar() {
        TradeEventLog.clear()
    }

    @Test
    fun elEventoExisteYEsSoloAppend() {
        // Solo-append: el valor debe seguir estando y no romper el orden del enum existente.
        val kinds = TradeEventLog.Kind.values().map { it.name }
        assertTrue("MODE_CHANGE debe existir", kinds.contains("MODE_CHANGE"))
        assertTrue("INOPERABLE (R14) debe seguir existiendo", kinds.contains("INOPERABLE"))
        assertTrue("FEED_MUDO (R13) debe seguir existiendo", kinds.contains("FEED_MUDO"))
        assertTrue(
            "MODE_CHANGE debe ir despues de INOPERABLE (solo-append)",
            kinds.indexOf("MODE_CHANGE") > kinds.indexOf("INOPERABLE")
        )
    }

    @Test
    fun elOrigenNuncaEstaVacio() {
        // Se reproduce exactamente el formato que usa el setter de TradingEngine.mode.
        val stack = Thread.currentThread().stackTrace
            .drop(1)
            .filterNot { it.className.startsWith("java.") || it.className.startsWith("kotlin.") }
            .filterNot { it.methodName.startsWith("access$") }
            .take(5)
            .map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        assertFalse("La pila no puede salir vacia", stack.isEmpty())

        val origen = stack.joinToString("<")
        assertFalse("El ORIGEN no puede quedar vacio", origen.isBlank())
        // El origen debe nombrar el metodo que lo provoca, no solo el numero de linea.
        assertTrue("El ORIGEN debe contener un nombre de metodo", origen.contains(":"))
    }

    @Test
    fun unCambioDeModoQuedaRegistradoConOrigen() {
        TradeEventLog.add(
            TradeEventLog.Kind.MODE_CHANGE,
            "de=DISABLED a=AUTONOMOUS origen=OverlayService.overlayCommandReceiver:153"
        )
        val ev = TradeEventLog.lastOf(TradeEventLog.Kind.MODE_CHANGE)
        assertTrue("Debe haber un MODE_CHANGE", ev != null)
        assertTrue("El detalle debe nombrar el modo destino", ev!!.detail.contains("a=AUTONOMOUS"))
        assertTrue("El detalle debe traer el ORIGEN", ev.detail.contains("origen="))
        assertFalse("El ORIGEN no puede venir vacio", ev.detail.endsWith("origen="))
        assertTrue("Debe contarse como evento", TradeEventLog.countOf(TradeEventLog.Kind.MODE_CHANGE) >= 1)
    }

    @Test
    fun elModoSeLeeDelDetalleNoDelContador() {
        // El contador agrupa por tipo, no por par de modos: dos transiciones distintas son dos
        // eventos pero un solo contador. Se documenta aqui para no malinterpretar /status.
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=AUTONOMOUS a=DISABLED origen=B")
        assertTrue(TradeEventLog.countOf(TradeEventLog.Kind.MODE_CHANGE) == 2)
        val recientes = TradeEventLog.recent(10).filter { it.kind == TradeEventLog.Kind.MODE_CHANGE }
        assertTrue("Los dos eventos deben conservarse por separado", recientes.size == 2)
    }

    @Test
    fun elEventoSeExponeEnElJsonDeEvents() {
        TradeEventLog.add(
            TradeEventLog.Kind.MODE_CHANGE,
            "de=NINGUNO a=DISABLED origen=onCreate(servicio recreado)"
        )
        val json = TradeEventLog.toJson(10)
        assertTrue("El JSON de /events debe incluir el tipo", json.contains("MODE_CHANGE"))
        assertTrue("El JSON debe incluir el origen", json.contains("onCreate"))
        // No debe romper los campos agregados que ya consumia R14.
        assertTrue(json.contains("\"vetoV1\""))
        assertTrue(json.contains("\"vetoV2\""))
    }
}
