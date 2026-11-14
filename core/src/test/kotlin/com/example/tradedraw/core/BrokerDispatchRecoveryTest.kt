package com.example.tradedraw.core

import org.junit.Assert.*
import org.junit.Test

class BrokerDispatchRecoveryTest {
    @Test fun restartRestoresMetadataBlocksReplayAndRequiresExplicitFreshDemoEvidence() {
        val store=MemoryPendingDispatchStore(); var calls=0
        val transport=object:OrderGateway {
            override fun dispatch(request:OrderRequest,onResult:(OrderReceipt)->Unit) { calls++;onResult(OrderReceipt.Failure("uncertain")) }
        }
        val request=OrderRequest(TradeAction.BUY,1f,2f,"decision","trade",recoveryContext=BrokerRecoveryContext(1000L,1000.0,100.0,0.85f,"reason"))
        SingleFlightOrderGateway(transport,store).dispatch(request) { }
        val recovered=SingleFlightOrderGateway(transport,store)
        assertTrue(recovered.isRecovered()); assertTrue(recovered.requiresReview())
        assertEquals(request.recoveryContext,recovered.reservation()?.context)
        recovered.dispatch(request) { assertTrue(it is OrderReceipt.Failure) }
        recovered.dispatch(request.copy(tradeId="other")) { assertTrue(it is OrderReceipt.Vetoed) }
        assertEquals(1,calls)
        val evidence=BrokerDispatchEvidence("trade","history-reference",BrokerRecoveredOutcome.CLOSED_WIN,100_000L)
        assertFalse(recovered.reconcileWithBrokerEvidence(evidence,false))
        assertFalse(recovered.reconcileWithBrokerEvidence(evidence.copy(requestId="wrong"),true))
        assertFalse(recovered.reconcileWithBrokerEvidence(evidence.copy(brokerReference=""),true))
        assertTrue(recovered.reconcileWithBrokerEvidence(evidence,true))
        assertNull(recovered.pendingRequestId())
        val again=SingleFlightOrderGateway(transport,store)
        assertEquals(evidence,again.lastEvidence())
        assertTrue(again.reconcileWithBrokerEvidence(evidence,true)) // durable idempotent acknowledgement
        assertEquals(1,calls)
    }
    @Test fun absenceOfBalanceOrEarlyNotPlacedAttestationCannotClearAReservation() {
        val store=MemoryPendingDispatchStore()
        val flight=SingleFlightOrderGateway(object:OrderGateway { override fun dispatch(request:OrderRequest,onResult:(OrderReceipt)->Unit) {} },store)
        flight.dispatch(OrderRequest(TradeAction.SELL,1f,2f,tradeId="id",recoveryContext=BrokerRecoveryContext(1000,1000.0,100.0,0.85f,"reason"))) { }
        assertFalse(flight.reconcileWithBrokerEvidence(BrokerDispatchEvidence("id","empty-history",BrokerRecoveredOutcome.NOT_PLACED,2000),true))
        assertEquals("id",flight.pendingRequestId())
        assertTrue(flight.reconcileWithBrokerEvidence(BrokerDispatchEvidence("id","empty-history",BrokerRecoveredOutcome.NOT_PLACED,121_000),true))
    }
}
