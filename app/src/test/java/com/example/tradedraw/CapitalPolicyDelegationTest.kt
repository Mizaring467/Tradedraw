package com.example.tradedraw

import com.example.tradedraw.core.AutonomousSubMode
import com.example.tradedraw.core.CapitalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * T-1.4: Validación de delegación de [CapitalPolicy] en [RiskManager.canExecuteTrade].
 */
class CapitalPolicyDelegationTest {

    @Before
    fun setUp() {
        AutoTradeAccessibilityService.latestObservedBalance = 20_000_000.0
        AutoTradeAccessibilityService.isDemoAccount = true
    }

    @Test
    fun delegacionDeCapitalPolicyCoincideConEvaluacionDirecta() {
        val rm = RiskManager()
        val policy: CapitalPolicy = rm

        val direct = rm.canExecuteTrade(AutonomousSubMode.CONSERVATIVE, 0.85f)
        val viaPolicy = policy.canExecuteTrade(AutonomousSubMode.CONSERVATIVE, 0.85f)

        assertEquals(direct.first, viaPolicy.first)
        assertEquals(direct.second, viaPolicy.second)
    }

    @Test
    fun vetoPorEquityBajoEmiteMensajeEsperado() {
        AutoTradeAccessibilityService.latestObservedBalance = 19_000_000.0 // Demo pero bajo suelo absoluto 20M
        val rm = RiskManager()
        val policy: CapitalPolicy = rm

        val (allowed, reason) = policy.canExecuteTrade(AutonomousSubMode.CONSERVATIVE, 0.85f)
        assertFalse("Debe vetar por equity insuficiente", allowed)
        assertEquals("Stop Equity Absoluto Alcanzado ( < )", reason)
    }
}
