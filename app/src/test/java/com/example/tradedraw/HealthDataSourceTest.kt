package com.example.tradedraw

import com.example.tradedraw.health.HealthSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R27-H / Loop 2 — las tres fuentes de datos del HealthSupervisor.
 *
 * Hallazgo auditado: `HealthSupervisor.evaluate()` usaba `WEBSOCKET_PURO` como valor por defecto y
 * `TradeDrawHttpBridge` lo invocaba sin pasar la fuente. El diagnóstico acertaba por casualidad
 * (hoy la captura está desactivada), pero habría mentido en cuanto cambiara la configuración.
 *
 * Estas pruebas fijan el comportamiento de cada fuente. **No** demuestran que el dispositivo esté
 * en una fuente concreta: eso se lee de `/status` en el dispositivo real.
 */
class HealthDataSourceTest {

    private lateinit var supervisor: HealthSupervisor
    private val ahora = 100_000L

    @Before
    fun setUp() {
        supervisor = HealthSupervisor()
    }

    private fun snapshot(
        latestObservedAsset: String = "",
        activeAsset: String = "Z-CRY/IDX",
        wsConnected: Boolean = true
    ) = HealthSupervisor.Snapshot(
        accesibilidadEnlazada = true,
        wsConnected = wsConnected,
        activeAsset = activeAsset,
        latestObservedAsset = latestObservedAsset,
        balance = 50_000.0,
        lastBalanceReadMs = ahora - 500L
    )

    // ---------- WEBSOCKET_PURO ----------

    @Test
    fun `websocket puro no evalua screen feed mismatch`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = ""),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue(rep.notEvaluable.contains("SCREEN_FEED_MISMATCH"))
        assertFalse("No debe inventar un desajuste sin pantalla",
            rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
    }

    @Test
    fun `websocket puro ignora el activo visible aunque este presente`() {
        // En este modo no hay contrato de pantalla: el dato visible no debe usarse para vetar.
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = "BTC/USD"),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue(rep.notEvaluable.contains("SCREEN_FEED_MISMATCH"))
    }

    @Test
    fun `websocket puro reporta la fuente en el json`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(), HealthSupervisor.DataSource.WEBSOCKET_PURO, ahora
        )

        assertTrue(rep.toJson().contains("\"dataSource\":\"WEBSOCKET_PURO\""))
    }

    // ---------- WEBSOCKET_CON_PANTALLA ----------

    @Test
    fun `websocket con pantalla evalua y acepta coincidencia`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = "Crypto IDX", activeAsset = "Z-CRY/IDX"),
            HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA,
            ahora
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue("Con pantalla real la señal sí se evalúa", rep.notEvaluable.isEmpty())
    }

    @Test
    fun `websocket con pantalla veta un desajuste real`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = "BTC/USD", activeAsset = "Z-CRY/IDX"),
            HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
    }

    @Test
    fun `websocket con pantalla veta si la pantalla es desconocida`() {
        // Fail-closed del contrato original: con pantalla activa pero sin dato visible, se veta.
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = ""),
            HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
    }

    @Test
    fun `websocket con pantalla reporta la fuente en el json`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = "Crypto IDX"),
            HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA,
            ahora
        )

        assertTrue(rep.toJson().contains("\"dataSource\":\"WEBSOCKET_CON_PANTALLA\""))
    }

    // ---------- SOLO_PANTALLA ----------

    @Test
    fun `solo pantalla declara el mismatch no evaluable sin feed`() {
        // Sin feed no hay mitad "feed" del contrato: no se puede comparar.
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(wsConnected = false, latestObservedAsset = "Crypto IDX"),
            HealthSupervisor.DataSource.SOLO_PANTALLA,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("WS_DISCONNECTED"))
        assertTrue("Sin feed la comparación no procede",
            rep.notEvaluable.contains("SCREEN_FEED_MISMATCH"))
    }

    @Test
    fun `solo pantalla con feed conectado si compara`() {
        // La fuente SOLO_PANTALLA tiene pantalla, así que con feed disponible sí evalúa.
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(latestObservedAsset = "BTC/USD", activeAsset = "Z-CRY/IDX"),
            HealthSupervisor.DataSource.SOLO_PANTALLA,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
    }

    @Test
    fun `solo pantalla reporta la fuente en el json`() {
        val rep = supervisor.evaluateFromSnapshot(
            snapshot(wsConnected = false), HealthSupervisor.DataSource.SOLO_PANTALLA, ahora
        )

        assertTrue(rep.toJson().contains("\"dataSource\":\"SOLO_PANTALLA\""))
    }

    // ---------- Contrato de la enumeración ----------

    @Test
    fun `solo websocket puro carece de pantalla`() {
        assertFalse(HealthSupervisor.DataSource.WEBSOCKET_PURO.hayPantalla)
        assertTrue(HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA.hayPantalla)
        assertTrue(HealthSupervisor.DataSource.SOLO_PANTALLA.hayPantalla)
    }

    @Test
    fun `el valor por defecto es websocket puro y sigue siendo seguro`() {
        // El default no debe inferir pantalla. Si alguien olvida pasar la fuente, el peor caso
        // admisible es "no evaluable", nunca un bloqueo espurio por desajuste.
        val rep = supervisor.evaluateFromSnapshot(snapshot(latestObservedAsset = "BTC/USD"))
        assertEquals(HealthSupervisor.DataSource.WEBSOCKET_PURO, rep.dataSource)
        assertTrue(rep.notEvaluable.contains("SCREEN_FEED_MISMATCH"))
    }
}
