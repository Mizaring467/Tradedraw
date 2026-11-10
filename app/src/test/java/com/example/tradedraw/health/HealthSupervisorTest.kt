package com.example.tradedraw.health

import com.example.tradedraw.AutoTradeAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ronda 27: matriz de pruebas de la máquina de estados de salud.
 *
 * R26 reportó tres bloqueos (SCREEN_FEED_MISMATCH, ACCESSIBILITY_DOWN, BALANCE_UNREADABLE) que
 * en el modo de datos vigente (WebSocket puro) eran falsos positivos estructurales. Estas pruebas
 * fijan el comportamiento esperado de cada escenario de la matriz R27.
 *
 * Se usa [HealthSupervisor.evaluateFromSnapshot] (función pura) porque la suite anterior solo
 * podía pasar `wsClient = null` — `BinomoWebSocketClient` exige `Context` — y por tanto nunca
 * llegó a ejercitar `READY` ni `DEGRADED`.
 */
class HealthSupervisorTest {

    private lateinit var supervisor: HealthSupervisor

    /** Ahora de referencia para todos los tests de frescura. */
    private val ahora = 100_000L

    private fun evaluar(
        snapshot: HealthSupervisor.Snapshot,
        source: HealthSupervisor.DataSource = HealthSupervisor.DataSource.WEBSOCKET_PURO,
        nowMs: Long = ahora
    ) = supervisor.evaluateFromSnapshot(snapshot, source, nowMs)

    @Before
    fun setUp() {
        supervisor = HealthSupervisor()
        AutoTradeAccessibilityService.latestObservedAsset = ""
        AutoTradeAccessibilityService.latestObservedBalance = 0.0
        AutoTradeAccessibilityService.lastBalanceReadMs = 0L
    }

    // ---------- 1. Accesibilidad ----------

    @Test
    fun `accesibilidad conectada produce HEALTH correcto`() {
        // Escenario: todo sano, balance recién leído.
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                accesibilidadEnlazada = true,
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 1_000L
            )
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue("Sin razones de bloqueo", rep.reasons.isEmpty())
    }

    @Test
    fun `accesibilidad desconectada reporta ACCESSIBILITY_DOWN`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(accesibilidadEnlazada = false)
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("ACCESSIBILITY_DOWN"))
    }

    @Test
    fun `accesibilidad reconectada recupera el estado`() {
        val caido = evaluar(HealthSupervisor.Snapshot(accesibilidadEnlazada = false))
        assertEquals(HealthSupervisor.State.BLOCKED, caido.state)

        val recuperado = evaluar(
            HealthSupervisor.Snapshot(
                accesibilidadEnlazada = true,
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 500L
            ),
            nowMs = ahora + 1_000L
        )

        assertEquals(HealthSupervisor.State.READY, recuperado.state)
        assertFalse("No debe quedar rastro del bloqueo previo",
            recuperado.reasons.contains("ACCESSIBILITY_DOWN"))
    }

    // ---------- 2. Screen feed ----------

    @Test
    fun `screen feed disponible con pantalla produce HEALTH correcto`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                latestObservedAsset = "Crypto IDX",
                activeAsset = "Z-CRY/IDX",
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 500L
            ),
            source = HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue("Con pantalla real la señal SÍ se evalúa", rep.notEvaluable.isEmpty())
    }

    @Test
    fun `screen feed ausente en modo websocket no genera falso bloqueo`() {
        // Este es el falso positivo que R26 reportó como SCREEN_FEED_MISMATCH.
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                latestObservedAsset = "",           // no hay pantalla que leer
                activeAsset = "Z-CRY/IDX",
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 500L
            ),
            source = HealthSupervisor.DataSource.WEBSOCKET_PURO
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertFalse("No debe bloquear por una comparación imposible",
            rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
        assertTrue("Debe declararse no evaluable",
            rep.notEvaluable.contains("SCREEN_FEED_MISMATCH"))
    }

    @Test
    fun `desajuste real con pantalla activa sigue vetando`() {
        // El guard del motor NO se relaja: si hay pantalla y no coincide, bloquea.
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                latestObservedAsset = "BTC/USD",
                activeAsset = "Z-CRY/IDX",
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 500L
            ),
            source = HealthSupervisor.DataSource.WEBSOCKET_CON_PANTALLA
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.any { it.startsWith("SCREEN_FEED_MISMATCH") })
    }

    // ---------- 3. Balance ----------

    @Test
    fun `balance valido y fresco no bloquea`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(balance = 50_000.0, lastBalanceReadMs = ahora - 100L)
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
    }

    @Test
    fun `balance ausente reporta BALANCE_UNREADABLE AUSENTE`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(balance = 0.0, lastBalanceReadMs = 0L)
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_UNREADABLE: AUSENTE"))
    }

    @Test
    fun `balance obsoleto bloquea por antiguedad, no por ilegible`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                balance = 50_000.0,
                lastBalanceReadMs = ahora - (HealthSupervisor.BALANCE_FRESH_WINDOW_MS + 1L)
            )
        )

        // Hay dato: no es ilegible. Es viejo: degrada.
        assertEquals(HealthSupervisor.State.DEGRADED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_STALE"))
        assertFalse("Un dato obsoleto no es un dato ausente",
            rep.reasons.any { it.startsWith("BALANCE_UNREADABLE") })
    }

    @Test
    fun `balance invalido bloquea por validacion`() {
        // Se leyó algo (hay marca) pero el valor utilizable es <= 0.
        val rep = evaluar(
            HealthSupervisor.Snapshot(balance = 0.0, lastBalanceReadMs = ahora - 100L)
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_UNREADABLE: INVALIDO"))
    }

    @Test
    fun `sin accesibilidad el balance no se duplica como ilegible`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(accesibilidadEnlazada = false, balance = 0.0, lastBalanceReadMs = 0L)
        )

        assertTrue(rep.reasons.contains("ACCESSIBILITY_DOWN"))
        assertFalse("Sin fuente no se declara ilegible; ya está la causa raíz",
            rep.reasons.any { it.startsWith("BALANCE_UNREADABLE") })
    }

    @Test
    fun `balance dentro de la cadencia de escaneo no se reporta rancio`() {
        // Regresión de C.2: cadencia real 5000 ms. Un dato de hace 5 s debe contar como fresco.
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                balance = 50_000.0,
                lastBalanceReadMs = ahora - HealthSupervisor.BALANCE_SCAN_CADENCE_MS
            )
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertFalse(rep.reasons.contains("BALANCE_STALE"))
    }

    // ---------- 4. Feed / conexión ----------

    @Test
    fun `perdida de conexion bloquea de forma segura`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                wsConnected = false,
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 500L
            )
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("WS_DISCONNECTED"))
    }

    @Test
    fun `recuperacion de conexion no arrastra estados obsoletos`() {
        val caido = evaluar(
            HealthSupervisor.Snapshot(wsConnected = false, balance = 50_000.0, lastBalanceReadMs = ahora - 500L)
        )
        assertEquals(HealthSupervisor.State.BLOCKED, caido.state)

        val vuelto = evaluar(
            HealthSupervisor.Snapshot(wsConnected = true, balance = 50_000.0, lastBalanceReadMs = ahora + 500L),
            nowMs = ahora + 1_000L
        )

        assertEquals(HealthSupervisor.State.READY, vuelto.state)
        assertFalse(vuelto.reasons.contains("WS_DISCONNECTED"))
    }

    @Test
    fun `feed rancio y precio congelado bloquean`() {
        val rancio = evaluar(HealthSupervisor.Snapshot(feedFresh = false))
        assertTrue(rancio.reasons.contains("FEED_STALE"))

        val congelado = evaluar(HealthSupervisor.Snapshot(priceFrozen = true))
        assertTrue(congelado.reasons.contains("PRICE_FROZEN"))

        val sinTicks = evaluar(HealthSupervisor.Snapshot(socketTicks = 0L))
        assertTrue(sinTicks.reasons.contains("NO_TICKS"))
    }

    // ---------- 5. Degradaciones ----------

    @Test
    fun `cooldown y payout degradan sin bloquear`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 100L,
                cooldownSec = 42,
                payoutDiscrepant = true
            )
        )

        assertEquals(HealthSupervisor.State.DEGRADED, rep.state)
        assertTrue(rep.reasons.contains("COOLDOWN_ACTIVE: 42s"))
        assertTrue(rep.reasons.contains("PAYOUT_DISCREPANT"))
    }

    @Test
    fun `orden pendiente bloquea`() {
        val rep = evaluar(
            HealthSupervisor.Snapshot(
                balance = 50_000.0,
                lastBalanceReadMs = ahora - 100L,
                hasPendingTrade = true
            )
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("ORDER_PENDING"))
    }

    // ---------- 6. Contrato del reporte ----------

    @Test
    fun `json incluye dataSource y notEvaluable`() {
        val rep = HealthSupervisor.Report(
            state = HealthSupervisor.State.BLOCKED,
            reasons = listOf("FEED_STALE"),
            sinceMs = 1700000000000L,
            dataSource = HealthSupervisor.DataSource.WEBSOCKET_PURO,
            notEvaluable = listOf("SCREEN_FEED_MISMATCH")
        )

        val json = rep.toJson()
        assertTrue(json.contains("\"state\":\"BLOCKED\""))
        assertTrue(json.contains("\"reasons\":[\"FEED_STALE\"]"))
        assertTrue(json.contains("\"sinceMs\":1700000000000"))
        assertTrue(json.contains("\"dataSource\":\"WEBSOCKET_PURO\""))
        assertTrue(json.contains("\"notEvaluable\":[\"SCREEN_FEED_MISMATCH\"]"))
    }

    @Test
    fun `sinceMs permanece invariable mientras el estado no cambie`() {
        supervisor.resetForTests(HealthSupervisor.State.BLOCKED, initialSinceMs = 5_000L)

        val rep = evaluar(
            HealthSupervisor.Snapshot(accesibilidadEnlazada = false),
            nowMs = 8_000L
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertEquals("sinceMs no debe cambiar si el estado se mantiene", 5_000L, rep.sinceMs)
    }

    @Test
    fun `sinceMs se reinicia al cambiar de estado`() {
        supervisor.resetForTests(HealthSupervisor.State.BLOCKED, initialSinceMs = 5_000L)

        val rep = evaluar(
            HealthSupervisor.Snapshot(balance = 50_000.0, lastBalanceReadMs = 9_900L),
            nowMs = 10_000L
        )

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertEquals(10_000L, rep.sinceMs)
    }
}
