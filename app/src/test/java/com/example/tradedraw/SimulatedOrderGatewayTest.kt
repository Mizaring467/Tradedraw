package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.simulation.ManualSimClock
import com.example.tradedraw.simulation.SimLedger
import com.example.tradedraw.simulation.SimulatedOrderGateway
import org.junit.Assert.*
import org.junit.Test

class SimulatedOrderGatewayTest {

    private class RecordingEventSink : EventSink {
        val events = mutableListOf<String>()
        override fun add(kind: String, detail: String, decisionId: String?, tradeId: String?) {
            events.add("$kind:$detail")
        }
    }

    @Test
    fun despachoSimuladoGeneraOrderReceiptSuccessYRegistraEvento() {
        val clock = ManualSimClock(10_000L)
        val ledger = SimLedger(initialBalance = 1000.0)
        val eventSink = RecordingEventSink()

        val gateway = SimulatedOrderGateway(
            simLedger = ledger,
            simClock = clock,
            eventSink = eventSink,
            currentPriceSupplier = { 2500.50 }
        )

        var receipt: OrderReceipt? = null
        gateway.dispatch(
            OrderRequest(
                action = TradeAction.BUY,
                x = 100f,
                y = 200f,
                decisionId = "dec-sim-1",
                tradeId = "tr-sim-1",
                mode = AutoTradeMode.SHADOW
            )
        ) {
            receipt = it
        }

        assertTrue(receipt is OrderReceipt.Success)
        val success = receipt as OrderReceipt.Success
        assertEquals("SIMULATED", success.transport)
        assertEquals(0L, success.latencyMs)
        assertEquals(10, success.candleSecond)
        assertEquals(1, gateway.getPendingTradesCount())
        assertTrue(eventSink.events.any { it.contains("SHADOW_DECISION:SIMULATED_ORDER BUY @ 2500.5") })
    }

    @Test
    fun resolucionDeOrdenesPendientesA60Segundos() {
        val clock = ManualSimClock(10_000L)
        val ledger = SimLedger(initialBalance = 1000.0, payoutRate = 0.82)
        val eventSink = RecordingEventSink()

        val gateway = SimulatedOrderGateway(
            simLedger = ledger,
            simClock = clock,
            eventSink = eventSink,
            currentPriceSupplier = { 2500.0 },
            defaultStake = 20.0
        )

        gateway.dispatch(
            OrderRequest(
                action = TradeAction.BUY,
                x = 100f,
                y = 200f,
                decisionId = "dec-1",
                tradeId = "tr-1",
                mode = AutoTradeMode.SHADOW
            )
        ) {}

        // 1. Tick antes de los 60s (a los 30s) -> ninguna orden debe resolverse
        val earlyResolved = gateway.onNewTick(2505.0, tickTimestampMs = 40_000L)
        assertTrue(earlyResolved.isEmpty())
        assertEquals(1, gateway.getPendingTradesCount())

        // 2. Tick a los 71s (>= 60s) -> debe resolverse como WIN (BUY 2500 -> 2505)
        val resolved = gateway.onNewTick(2505.0, tickTimestampMs = 71_000L)
        assertEquals(1, resolved.size)
        assertEquals("WIN", resolved[0].result)
        assertEquals(0, gateway.getPendingTradesCount())
        assertEquals(1016.4, ledger.currentBalance, 0.001) // 1000 + (20 * 0.82) = 1016.4
        assertTrue(eventSink.events.any { it.contains("SIM_SETTLED BUY WIN @ 2505.0 PnL=16.40") })
    }

    @Test
    fun rechazoEnModoDisabled() {
        val gateway = SimulatedOrderGateway()
        var receipt: OrderReceipt? = null
        gateway.dispatch(
            OrderRequest(
                action = TradeAction.BUY,
                x = 100f,
                y = 200f,
                mode = AutoTradeMode.DISABLED
            )
        ) {
            receipt = it
        }

        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals("TRADE_DISPATCH_REJECTED_MODE_DISABLED", (receipt as OrderReceipt.Vetoed).reason)
        assertEquals(0, gateway.getPendingTradesCount())
    }
}
