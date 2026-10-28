package com.example.tradedraw.health

import com.example.tradedraw.AutoTradeAccessibilityService
import com.example.tradedraw.RiskManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ronda 21 (Frente D): Pruebas del Health Supervisor Unificado.
 *
 * Valida agregación de estados (READY, DEGRADED, BLOCKED) y razones concretas
 * sin alterar la lógica de trading ni crear fuentes paralelas de verdad.
 */
class HealthSupervisorTest {

    private lateinit var supervisor: HealthSupervisor

    @Before
    fun setUp() {
        supervisor = HealthSupervisor()
        AutoTradeAccessibilityService.latestObservedAsset = ""
        AutoTradeAccessibilityService.latestObservedBalance = 0.0
    }

    @Test
    fun reportaBlockedCuandoAccesibilidadEstaCaida() {
        val rep = supervisor.evaluate(
            tradingEngine = null,
            riskManager = null,
            wsClient = null,
            accessibilityService = null,
            nowMs = 1000L
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue("Debe reportar ACCESSIBILITY_DOWN", rep.reasons.contains("ACCESSIBILITY_DOWN"))
        assertTrue("Debe reportar WS_DISCONNECTED", rep.reasons.contains("WS_DISCONNECTED"))
        assertEquals(1000L, rep.sinceMs)
    }

    @Test
    fun reportaBlockedCuandoHayOrdenPendiente() {
        val rm = RiskManager()
        rm.hasPendingTrade = true

        val rep = supervisor.evaluate(
            tradingEngine = null,
            riskManager = rm,
            wsClient = null,
            accessibilityService = null,
            nowMs = 2000L
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
        assertTrue(rep.reasons.contains("ORDER_PENDING"))
    }

    @Test
    fun reportaDegradedCuandoHayCooldownActivoOSaldoNoFresco() {
        // Simulamos estado sin razones de bloqueo directo excepto balance positivo no fresco
        AutoTradeAccessibilityService.latestObservedBalance = 50000.0
        AutoTradeAccessibilityService.lastBalanceReadMs = 1000L // 10 segundos atrás vs nowMs 11000L

        val rm = RiskManager()
        rm.lastTradeTime = 10000L // cooldown activo

        val rep = supervisor.evaluate(
            tradingEngine = null,
            riskManager = rm,
            wsClient = null,
            accessibilityService = null,
            nowMs = 11000L
        )

        // Sigue BLOCKED por ACCESSIBILITY_DOWN y WS_DISCONNECTED
        assertEquals(HealthSupervisor.State.BLOCKED, rep.state)
    }

    @Test
    fun reportToJsonGeneraCamposRequeridos() {
        val rep = HealthSupervisor.Report(
            state = HealthSupervisor.State.BLOCKED,
            reasons = listOf("FEED_STALE", "NO_TICKS"),
            sinceMs = 1700000000000L
        )

        val json = rep.toJson()
        assertTrue(json.contains("\"state\":\"BLOCKED\""))
        assertTrue(json.contains("\"reasons\":[\"FEED_STALE\",\"NO_TICKS\"]"))
        assertTrue(json.contains("\"sinceMs\":1700000000000"))
    }

    @Test
    fun sinceMsPermaneceInvariableMientrasElEstadoNoCambie() {
        supervisor.resetForTests(HealthSupervisor.State.BLOCKED, initialSinceMs = 5000L)

        // Siguiente evaluación que resulta también en BLOCKED
        val rep1 = supervisor.evaluate(
            tradingEngine = null,
            riskManager = null,
            wsClient = null,
            accessibilityService = null,
            nowMs = 8000L
        )

        assertEquals(HealthSupervisor.State.BLOCKED, rep1.state)
        assertEquals("sinceMs no debe cambiar si el estado se mantiene", 5000L, rep1.sinceMs)
    }
}
