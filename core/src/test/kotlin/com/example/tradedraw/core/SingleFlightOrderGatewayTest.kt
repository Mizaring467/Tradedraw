package com.example.tradedraw.core

import org.junit.Assert.*
import org.junit.Test

class SingleFlightOrderGatewayTest {
    private val request = OrderRequest(TradeAction.BUY, 1f, 2f, "decision", "trade")
    @Test fun uncertainDeliveryBlocksRetriesNewIdsAndRestart() {
        val store = MemoryPendingDispatchStore()
        var calls = 0
        var callback: ((OrderReceipt) -> Unit)? = null
        val transport = object : OrderGateway {
            override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
                calls++; callback = onResult
            }
        }
        val gateway = SingleFlightOrderGateway(transport, store)
        val results = mutableListOf<OrderReceipt>()
        gateway.dispatch(request) { results.add(it) }
        gateway.dispatch(request.copy(tradeId = "other")) { assertTrue(it is OrderReceipt.Vetoed) }
        callback!!(OrderReceipt.Failure("timeout"))
        callback!!(OrderReceipt.Success("late", 500, 2))
        assertEquals(1, results.size)
        gateway.dispatch(request) { assertEquals(results.single(), it) }
        val restarted = SingleFlightOrderGateway(transport, store)
        restarted.dispatch(request.copy(tradeId = "new")) { assertTrue(it is OrderReceipt.Vetoed) }
        assertEquals(1, calls)
        assertFalse(restarted.reconcile("wrong"))
        assertTrue(restarted.reconcile("trade"))
        restarted.dispatch(request.copy(tradeId = "new")) { }
        assertEquals(2, calls)
    }
    @Test fun definiteRejectionReleasesReservation() {
        var calls = 0
        val gateway = SingleFlightOrderGateway(object : OrderGateway {
            override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
                calls++; onResult(OrderReceipt.Vetoed("REAL"))
            }
        })
        gateway.dispatch(request) { }
        gateway.dispatch(request.copy(tradeId = "second")) { }
        assertEquals(2, calls)
        assertNull(gateway.pendingRequestId())
    }
    @Test fun persistenceFailurePreventsTransport() {
        val gateway = SingleFlightOrderGateway(object : OrderGateway {
            override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) = error("must not dispatch")
        }, object : PendingDispatchStore {
            override fun load(): String? = null
            override fun save(requestId: String?) = error("disk full")
        })
        gateway.dispatch(request) { assertEquals(OrderReceipt.Vetoed("DISPATCH_PERSISTENCE_FAILED"), it) }
    }
}
