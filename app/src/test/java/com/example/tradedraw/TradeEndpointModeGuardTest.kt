package com.example.tradedraw

import com.example.tradedraw.core.AutoTradeMode
import com.example.tradedraw.core.TradeAction
import org.junit.Assert.*
import org.junit.Test

class TradeEndpointModeGuardTest {

    @Test
    fun disabledRechazaDespachoCon403YEventoVeto() {
        val decision = evaluateTradeEndpointModeGuard(AutoTradeMode.DISABLED, TradeAction.BUY)
        assertFalse("DISABLED no debe permitir despacho", decision.allowed)
        assertEquals(403, decision.statusCode)
        assertNotNull(decision.errorMessage)
        assertEquals("TRADE_ENDPOINT_MODE_REJECTED mode=DISABLED action=BUY", decision.eventMessage)
    }

    @Test
    fun shadowRechazaDespachoCon403YEventoVeto() {
        val decision = evaluateTradeEndpointModeGuard(AutoTradeMode.SHADOW, TradeAction.SELL)
        assertFalse("SHADOW no debe permitir despacho al broker", decision.allowed)
        assertEquals(403, decision.statusCode)
        assertNotNull(decision.errorMessage)
        assertEquals("TRADE_ENDPOINT_MODE_REJECTED mode=SHADOW action=SELL", decision.eventMessage)
    }

    @Test
    fun autonomousPermiteDespacho() {
        val decision = evaluateTradeEndpointModeGuard(AutoTradeMode.AUTONOMOUS, TradeAction.BUY)
        assertTrue("AUTONOMOUS debe permitir despacho", decision.allowed)
        assertEquals(200, decision.statusCode)
        assertNull(decision.errorMessage)
        assertNull(decision.eventMessage)
    }

    @Test
    fun semiautomaticPermiteDespacho() {
        val decision = evaluateTradeEndpointModeGuard(AutoTradeMode.SEMIAUTOMATIC, TradeAction.SELL)
        assertTrue("SEMIAUTOMATIC debe permitir despacho", decision.allowed)
        assertEquals(200, decision.statusCode)
        assertNull(decision.errorMessage)
        assertNull(decision.eventMessage)
    }
}
