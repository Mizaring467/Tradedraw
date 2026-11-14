package com.example.tradedraw

import com.example.tradedraw.health.HealthSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R27-H / Loop 3 — separación entre el diagnóstico de frescura de balance y el guard del motor.
 *
 * Hay **dos** preguntas distintas que usan el mismo dato (`lastBalanceReadMs`) con umbrales
 * distintos a propósito:
 *
 * | Pregunta | Dónde | Umbral | Qué decide |
 * | :--- | :--- | :--- | :--- |
 * | ¿El balance está fresco para **diagnóstico**? | `HealthSupervisor` | 10 000 ms | Solo informar `BALANCE_STALE` en `GET /status`. **No** decide operar |
 * | ¿El balance está fresco para **atribuir un empate**? | `TradingEngine.isBalanceFresh` | 3000 ms | Si un TIE se contabiliza como void (`TIE`) o queda como incierto (`TIE_INCIERTO`) |
 *
 * El umbral diagnóstico es más holgado porque el escaneo pasivo de saldo corre cada 5000 ms: exigir
 * 3000 ms marcaría como rancio un dato normal. Eso **no** debilita el guard del motor, que sigue
 * siendo el estricto y es el único que toca contabilidad.
 *
 * Estas pruebas fijan ambas fronteras por separado para que un cambio futuro en una no pueda
 * arrastrar la otra sin que un test lo note.
 */
class BalanceFreshnessSeparationTest {

    private lateinit var supervisor: HealthSupervisor
    private val ahora = 1_000_000L

    @org.junit.Before
    fun setUp() {
        supervisor = HealthSupervisor()
    }

    private fun diagnostico(edadMs: Long, balance: Double = 50_000.0) =
        supervisor.evaluateFromSnapshot(
            HealthSupervisor.Snapshot(
                balance = balance,
                lastBalanceReadMs = ahora - edadMs
            ),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

    // ---------- El guard del motor no se movió ----------

    @Test
    fun `guard del motor sigue en 3000 ms y no se toco`() {
        val t0 = 0L
        assertTrue("2999 ms es fresco",
            TradingEngine.isBalanceFresh(true, t0 + 1, t0 + 3000))
        assertFalse("3000 ms ya no lo es (estrictamente menor)",
            TradingEngine.isBalanceFresh(true, t0, t0 + 3000))
        assertFalse("4000 ms no es fresco",
            TradingEngine.isBalanceFresh(true, t0, t0 + 4000))
    }

    @Test
    fun `guard del motor exige instancia disponible`() {
        assertFalse("Sin servicio no hay frescura posible",
            TradingEngine.isBalanceFresh(false, 0L, 1000L))
        assertTrue(TradingEngine.isBalanceFresh(true, 0L, 2999L))
    }

    @Test
    fun `una ventana diagnotica holgada no implica ventana contable holgada`() {
        // 8 s: el diagnóstico lo acepta (dentro de 10 s), el motor NO (fuera de 3 s).
        val edad = 8_000L
        val rep = diagnostico(edad)

        assertEquals("El diagnóstico no marca rancio", HealthSupervisor.State.READY, rep.state)
        assertFalse(rep.reasons.contains("BALANCE_STALE"))

        assertFalse("Pero el motor sigue siendo estricto",
            TradingEngine.isBalanceFresh(true, ahora - edad, ahora))
    }

    @Test
    fun `un dato valido para el motor tambien lo es para el diagnostico`() {
        // La implicación válida es en un solo sentido: motor ⇒ diagnóstico, nunca al revés.
        val edad = 1_000L
        val rep = diagnostico(edad)

        assertTrue(TradingEngine.isBalanceFresh(true, ahora - edad, ahora))
        assertEquals(HealthSupervisor.State.READY, rep.state)
    }

    // ---------- Límites temporales del diagnóstico ----------

    @Test
    fun `diagnostico en el borde exacto de la ventana sigue fresco`() {
        val rep = diagnostico(HealthSupervisor.BALANCE_FRESH_WINDOW_MS)
        assertFalse(rep.reasons.contains("BALANCE_STALE"))
    }

    @Test
    fun `diagnostico un ms por encima de la ventana marca rancio`() {
        val rep = diagnostico(HealthSupervisor.BALANCE_FRESH_WINDOW_MS + 1L)

        assertEquals(HealthSupervisor.State.DEGRADED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_STALE"))
    }

    @Test
    fun `la ventana diagnostica cubre al menos dos ciclos de escaneo pasivo`() {
        // Garantiza que el margen no se reduzca por debajo de la cadencia real (5000 ms).
        assertTrue(
            "La ventana debe superar la cadencia de escaneo",
            HealthSupervisor.BALANCE_FRESH_WINDOW_MS > HealthSupervisor.BALANCE_SCAN_CADENCE_MS
        )
        assertTrue(
            "Y dar al menos un ciclo de margen",
            HealthSupervisor.BALANCE_FRESH_WINDOW_MS >= 2 * HealthSupervisor.BALANCE_SCAN_CADENCE_MS
        )
    }

    // ---------- Valores ausentes y límites ----------

    @Test
    fun `lectura nunca realizada se reporta ausente, no rancio`() {
        val rep = supervisor.evaluateFromSnapshot(
            HealthSupervisor.Snapshot(balance = 0.0, lastBalanceReadMs = 0L),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_UNREADABLE: AUSENTE"))
        assertFalse("Ausente no es rancio", rep.reasons.contains("BALANCE_STALE"))
    }

    @Test
    fun `lectura realizada con valor cero se reporta invalido`() {
        val rep = supervisor.evaluateFromSnapshot(
            HealthSupervisor.Snapshot(balance = 0.0, lastBalanceReadMs = ahora - 100L),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

        assertTrue(rep.reasons.contains("BALANCE_UNREADABLE: INVALIDO"))
    }

    @Test
    fun `balance negativo se trata como invalido`() {
        val rep = supervisor.evaluateFromSnapshot(
            HealthSupervisor.Snapshot(balance = -1.0, lastBalanceReadMs = ahora - 100L),
            HealthSupervisor.DataSource.WEBSOCKET_PURO,
            ahora
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("BALANCE_UNREADABLE: INVALIDO"))
    }

    @Test
    fun `sin dato no se emite ademas la degradacion de rancio`() {
        // Evita doble reporte de la misma causa.
        val rep = diagnostico(0L, balance = 0.0)
        assertFalse(rep.reasons.contains("BALANCE_STALE"))
    }

    @Test
    fun `un balance grande y fresco no dispara ninguna alerta`() {
        val rep = diagnostico(500L, balance = 36_865_735.68)

        assertEquals(HealthSupervisor.State.READY, rep.state)
        assertTrue(rep.reasons.isEmpty())
    }

    // ---------- El diagnóstico no participa en decisiones ----------

    @Test
    fun `el estado degradado por balance es solo informativo`() {
        // Documenta la garantía estructural: DEGRADED se emite cuando no hay bloqueos, y el
        // consumidor único (GET /status) no altera el modo. Se fija aquí para que un futuro
        // cambio que haga que el motor consulte el health rompa este contrato de forma visible.
        val rep = diagnostico(HealthSupervisor.BALANCE_FRESH_WINDOW_MS + 1L)

        assertEquals(HealthSupervisor.State.DEGRADED, rep.state)
        assertTrue(rep.reasons.all { it == "BALANCE_STALE" })
    }
}
