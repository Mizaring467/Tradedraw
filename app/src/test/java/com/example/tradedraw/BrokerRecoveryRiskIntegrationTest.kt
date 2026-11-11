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
        risk.clearPendingTrade()
        risk.recordTradeWins(force=true)
        risk.recordTradeLosses(force=true)
        risk.recordTradeVoid()
        assertTrue(risk.hasPendingTrade)
        assertEquals(0,risk.totalLosses)
        assertEquals(0,risk.totalVoids)
        assertEquals(0,risk.totalWins)
        assertTrue(risk.applyBrokerRecovery("trade",BrokerRecoveredOutcome.CLOSED_WIN))
        assertFalse(risk.applyBrokerRecovery("trade",BrokerRecoveredOutcome.CLOSED_WIN))
        assertEquals(1,risk.totalWins)
        assertFalse(risk.hasPendingTrade);assertFalse(risk.requiresBrokerReconciliation)
    }
    @Test fun confirmationKeepsDurableReservationUntilSettlementAndRestartRestoresHold() {
        val store=MemoryPendingDispatchStore()
        var dispatches=0
        val transport=object:OrderGateway {
            override fun dispatch(request:OrderRequest,onResult:(OrderReceipt)->Unit) {
                dispatches++;onResult(OrderReceipt.Success("TEST",1,1))
            }
        }
        val flight=SingleFlightOrderGateway(transport,store)
        val request=OrderRequest(TradeAction.BUY,1f,2f,"d","t",recoveryContext=BrokerRecoveryContext(1000,1000.0,100.0,0.85f,"test"))
        val risk=RiskManager(null)
        risk.onPendingTradeSettled={ flight.reconcile(it) }
        risk.recordTradeSent(TradeAction.BUY,100.0,baseBalance=1000.0,decisionId="d",tradeId="t")
        flight.dispatch(request) { }
        risk.markOrderConfirmed()
        assertEquals("t",flight.pendingRequestId())
        val recovered=SingleFlightOrderGateway(transport,store)
        val restartedRisk=RiskManager(null)
        restartedRisk.holdForBrokerReconciliation(recovered.reservation()!!)
        assertTrue(restartedRisk.requiresBrokerReconciliation)
        recovered.dispatch(request) { }
        recovered.dispatch(request.copy(tradeId="other")) { assertTrue(it is OrderReceipt.Vetoed) }
        assertEquals(1,dispatches)
        assertFalse(restartedRisk.settleTradeOnce("t",1000,true))
        // Original live instance: a real result, rather than confirmation/reset, releases the reservation.
        assertTrue(risk.settleTradeOnce("t",risk.pendingTradeStartTime,true))
        assertNull(flight.pendingRequestId())
        assertEquals(1,risk.totalWins)
        assertFalse(risk.settleTradeOnce("t",1000,true))
    }
}
