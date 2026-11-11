package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

class BrokerRecoveryRiskIntegrationTest {
    @Test fun recoveredReservationRemainsInRiskUntilBrokerHistoryClosesIt() {
        val risk=RiskManager(null)
        val reservation=DispatchReservation("trade","decision",TradeAction.BUY,BrokerRecoveryContext(1000L,1000.0,100.0,0.85f,"recovered"))
        risk.holdForBrokerReconciliation(reservation)
        assertTrue(risk.requiresBrokerReconciliation);assertTrue(risk.hasPendingTrade)
        assertEquals(1000L,risk.pendingTradeStartTime)
        assertFalse(risk.settleManual("WITHOUT_EVIDENCE",true))
        assertFalse(risk.settleTradeOnce("trade",1000L,true))
        assertEquals(0,risk.totalWins)
        assertTrue(risk.applyBrokerRecovery("trade",BrokerRecoveredOutcome.CLOSED_WIN))
        assertFalse(risk.applyBrokerRecovery("trade",BrokerRecoveredOutcome.CLOSED_WIN))
        assertEquals(1,risk.totalWins)
        assertFalse(risk.hasPendingTrade);assertFalse(risk.requiresBrokerReconciliation)
    }
}
