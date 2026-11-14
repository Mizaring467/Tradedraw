package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.simulation.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Infrastructure fixtures only. Tests actual replay helpers against actual durable SHADOW/ledger. */
class SettlementParityTest {
    private fun checkCase(future: List<ReplayBacktestTest.RawTick>, expected: String, action: TradeAction = TradeAction.BUY) {
        val entry = ReplayBacktestTest.RawTick(10_000, "A", 100.0)
        val ledger = SimLedger()
        ledger.openTrade("sim", "dec", action, entry.price, 1.0, entry.ts, entry.ric)
        val shadowLedger = SimLedger()
        val clock = ManualSimClock(entry.ts)
        val journal = mutableListOf<SettledSimulatedTrade>()
        val shadow = ShadowSimulation(SimulatedOrderGateway(shadowLedger, clock), SimulationResultJournal { journal.add(it); true })
        shadow.enabled = true
        shadow.open(OrderRequest(action, 0f, 0f, decisionId = "shadow", mode = AutoTradeMode.SHADOW),
            MarketTick(asset = entry.ric, price = entry.price, timestampMs = entry.ts)) { assertTrue(it is OrderReceipt.Success) }
        for (tick in future) {
            ledger.onTick(tick.ric, tick.price, tick.ts)
            clock.set(tick.ts)
            shadow.onTick(MarketTick(asset = tick.ric, price = tick.price, timestampMs = tick.ts))
        }
        fun canonical(s: SettledSimulatedTrade?) = s?.result?.let { if (it == "TIE") "TRUE_TIE" else it } ?: "NO_DATA"
        assertEquals(expected, canonical(ledger.getSettledTrades().singleOrNull()))
        assertEquals(expected, canonical(shadowLedger.getSettledTrades().singleOrNull()))
        assertEquals(shadowLedger.getSettledTrades().size, journal.size)
        val harness = ReplayBacktestTest()
        val file = File.createTempFile("r29c-parity", ".csv")
        try {
            file.writeText("ts,ric,price\n" + (listOf(entry) + future).joinToString("\n") { "${it.ts},${it.ric},${it.price}" })
            val load = harness.javaClass.getDeclaredMethod("loadTicks", File::class.java).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val ticks = (load.invoke(harness, file) as List<ReplayBacktestTest.RawTick>).filter { it.ric == entry.ric }
            val lookup = harness.javaClass.getDeclaredMethod("priceAt", List::class.java, java.lang.Long.TYPE).apply { isAccessible = true }
            val price = lookup.invoke(harness, ticks, entry.ts + 60_000) as Double?
            val resolve = harness.javaClass.getDeclaredMethod("resolveTrade", java.lang.Double.TYPE, java.lang.Double::class.java, TradeAction::class.java).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val result = (resolve.invoke(harness, entry.price, price, action) as Pair<String, String>).first
            assertEquals(expected, if (result == "TIE") "TRUE_TIE" else result)
        } finally { file.delete() }
    }
    private fun t(ts: Long, price: Double = 101.0, asset: String = "A") = ReplayBacktestTest.RawTick(ts, asset, price)
    @Test fun exactlyAtExpiry() = checkCase(listOf(t(70_000)), "WIN")
    @Test fun oneMillisecondBefore() = checkCase(listOf(t(69_999)), "NO_DATA")
    @Test fun oneMillisecondAfter() = checkCase(listOf(t(70_001)), "WIN")
    @Test fun largeGap() = checkCase(listOf(t(700_000)), "WIN")
    @Test fun duplicateTickFirstObservationWins() = checkCase(listOf(t(70_000), t(70_000, 99.0)), "WIN")
    @Test fun outOfOrderDoesNotReviseSettlement() = checkCase(listOf(t(70_001, 99.0), t(70_000)), "LOSS")
    @Test fun otherAssetAtSameTimestampCannotSettle() = checkCase(listOf(t(70_000, 1.0, "B"), t(70_000)), "WIN")
    @Test fun otherAssetAloneIsNoData() = checkCase(listOf(t(70_000, 1.0, "B")), "NO_DATA")
    @Test fun samePriceIsTrueTie() = checkCase(listOf(t(70_000, 100.0)), "TRUE_TIE")
    @Test fun noFutureTickIsNoData() = checkCase(emptyList(), "NO_DATA")
    @Test fun sellDirection() = checkCase(listOf(t(70_000, 99.0)), "WIN", TradeAction.SELL)
}
