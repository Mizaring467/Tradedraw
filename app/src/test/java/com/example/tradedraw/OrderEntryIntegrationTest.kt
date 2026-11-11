package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

/** Actual production gateway chain, with only transport and durable storage doubled. */
class OrderEntryIntegrationTest {
    private class Transport(override val name: String, val available: Boolean) : TapTransport {
        var calls = 0
        var callback: ((OrderReceipt) -> Unit)? = null
        override fun isAvailable() = available
        override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
            calls++; callback = onResult
        }
    }
    @Test fun accountTransitionsAndModesGuardTheActualTransport() {
        var now = 1000L
        val guard = StandardDemoAccountGuard({ now }, 100L)
        val root = Transport("ROOT", false)
        val gesture = Transport("GESTURE", true)
        val direct = Transport("DIRECT", true)
        val gateway = SingleFlightOrderGateway(DefaultOrderGateway(guard,
            rootTransport = root, gestureTransport = gesture, bridgeTransport = direct))
        var id = 0
        fun submit(mode: AutoTradeMode = AutoTradeMode.AUTONOMOUS): OrderReceipt? {
            var result: OrderReceipt? = null
            gateway.dispatch(OrderRequest(TradeAction.BUY, 1f, 2f, tradeId = "r${id++}", mode = mode)) { result = it }
            return result
        }
        assertTrue(submit() is OrderReceipt.Vetoed) // unknown
        guard.recordAccountObservation(AccountType.DEMO, now)
        assertTrue(submit(AutoTradeMode.DISABLED) is OrderReceipt.Vetoed)
        assertTrue(submit(AutoTradeMode.SHADOW) is OrderReceipt.Vetoed)
        now += 101
        assertTrue(submit() is OrderReceipt.Vetoed) // stale
        guard.recordAccountObservation(AccountType.REAL, now)
        assertTrue(submit() is OrderReceipt.Vetoed)
        guard.reset()
        assertTrue(submit() is OrderReceipt.Vetoed) // disconnect / restart
        assertEquals(0, gesture.calls)
        guard.recordAccountObservation(AccountType.DEMO, now)
        assertNull(submit(AutoTradeMode.SEMIAUTOMATIC)) // async
        assertEquals(1, gesture.calls)
        gesture.callback!!(OrderReceipt.Failure("timeout", "GESTURE"))
        gesture.callback!!(OrderReceipt.Success("GESTURE", 400, 2)) // late duplicate ignored
        assertEquals(0, root.calls)
        assertEquals(0, direct.calls)
        assertTrue(submit() is OrderReceipt.Vetoed) // uncertain, no second tap
        assertEquals(1, gesture.calls)
    }
    @Test fun lateCallbackProducesExactlyOneResultWithoutTransportFallback() {
        val guard = StandardDemoAccountGuard()
        guard.recordAccountObservation(AccountType.DEMO)
        val root = Transport("ROOT", true)
        val gesture = Transport("GESTURE", true)
        val gateway = DefaultOrderGateway(guard, rootTransport = root, gestureTransport = gesture)
        val receipts = mutableListOf<OrderReceipt>()
        gateway.dispatch(OrderRequest(TradeAction.SELL, 1f, 2f)) { receipts.add(it) }
        root.callback!!(OrderReceipt.Failure("unknown"))
        root.callback!!(OrderReceipt.Success("ROOT", 700, 3))
        assertEquals(1, receipts.size)
        assertEquals(0, gesture.calls)
    }
}
