package com.example.tradedraw

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 20: guard de desajuste entre el activo del FEED y el visible en PANTALLA.
 *
 * Por que existe. La recuperacion de activo sin feed puede dejar el feed apuntando a un RIC
 * DISTINTO del grafico abierto en el broker. Medido: con el grafico en `Aave (OTC)`, el feed sano
 * disponible es `ETHUSD-OTC`. Sin este guard el motor decidiria CALL/PUT con el precio de un
 * subyacente y pulsaria los botones del grafico de OTRO.
 *
 * Los umbrales y el caracter fail-closed estan fijados en `workflows/PREREGISTRO_R20.md` §4.2
 * ANTES de escribir este codigo. Estos tests fijan el comportamiento, no lo disenan.
 */
class AssetMismatchGuardTest {

    private fun veto(pantalla: String, feed: String) =
        TradingEngine.motivoVetoPorDesajusteDeActivo(pantalla, feed)

    // ---- B.1: si el activo del feed != el observado en pantalla, se veta ----

    @Test
    fun `feed en un activo y grafico en otro produce veto`() {
        // Este es EL caso que motiva el guard: el grafico del broker esta en Aave (OTC) y la
        // recuperacion dejo el feed en ETHUSD-OTC, que es el unico que entrega ticks.
        val motivo = veto("Aave (OTC)", "ETHUSD-OTC")
        assertNotNull("Debe vetar cuando el feed sirve otro activo que el visible", motivo)
        assertTrue("El motivo debe nombrar el activo del feed: $motivo", motivo!!.contains("ETHUSD-OTC"))
        assertTrue("El motivo debe nombrar lo visible: $motivo", motivo.contains("Aave (OTC)"))
    }

    @Test
    fun `el mismo activo en distinta forma no produce veto`() {
        // La pantalla da el nombre VISIBLE y el feed el RIC: hay que mapear antes de comparar.
        // Sin el mapeo, esta comprobacion daria desajuste SIEMPRE y el bot no dispararia nunca.
        assertNull("Aave (OTC) debe equivaler a AAVEUSD-OTC", veto("Aave (OTC)", "AAVEUSD-OTC"))
        assertNull("Ethereum (OTC) debe equivaler a ETHUSD-OTC", veto("Ethereum (OTC)", "ETHUSD-OTC"))
        assertNull("Crypto IDX debe equivaler a Z-CRY/IDX", veto("Crypto IDX", "Z-CRY/IDX"))
        assertNull("Bitcoin (OTC) debe equivaler a BTCUSD-OTC", veto("Bitcoin (OTC)", "BTCUSD-OTC"))
    }

    @Test
    fun `la comparacion es insensible a mayusculas y a espacios sobrantes`() {
        assertNull(veto("  aave (otc)  ", "aaveusd-otc"))
        assertNull(veto("Aave (OTC)", "  AAVEUSD-OTC  "))
    }

    // ---- B.3: control positivo, el guard NO interviene cuando coinciden ----

    @Test
    fun `pantalla y feed coincidentes no producen veto`() {
        // Control positivo del criterio B.3: con el grafico y el feed alineados, el guard tiene
        // que quedarse quieto. Si vetara aqui, bloquearia disparos legitimos y el criterio de
        // falsacion 3 del preregistro obligaria a revertir.
        assertNull(veto("ETHUSD-OTC", "ETHUSD-OTC"))
        assertNull(veto("Z-CRY/IDX", "Z-CRY/IDX"))
    }

    // ---- B.4: fail-closed ----

    @Test
    fun `sin activo observado en pantalla se veta cuando el feed si es conocido`() {
        // Fail-closed: la duda no autoriza. Si el feed sirve un activo pero la pantalla no lo
        // confirma, no se puede saber sobre que grafico se pulsaria.
        val motivo = veto("", "ETHUSD-OTC")
        assertNotNull("Sin dato de pantalla y con feed conocido debe vetar", motivo)
        assertTrue("El motivo debe marcar la pantalla como desconocida: $motivo", motivo!!.contains("DESCONOCIDA"))
    }

    @Test
    fun `sin activo de feed no se veta porque no hay nada que comparar`() {
        // No es una relajacion del fail-closed: si el cliente no tiene activo, el guard de feed
        // rancio y el veto V2 ya vetan la operacion. Vetar aqui solo anadiria un motivo ciego.
        assertNull(veto("Aave (OTC)", ""))
        assertNull(veto("", ""))
    }

    // ---- El motivo debe ser auditable ----

    @Test
    fun `el motivo del veto incluye el ric derivado y el del feed para poder auditarlo`() {
        val motivo = veto("Aave (OTC)", "ETHUSD-OTC")
        assertNotNull(motivo)
        // Debe nombrar los dos RIC: sin ellos, `/events` no permitiria reconstruir que paso.
        assertTrue("Falta el RIC derivado de pantalla: $motivo", motivo!!.contains("AAVEUSD-OTC"))
        assertTrue("Falta el RIC del feed: $motivo", motivo.contains("ETHUSD-OTC"))
    }
}
