package com.example.tradedraw.simulation

import com.example.tradedraw.core.*

fun interface SimulationResultJournal { fun append(result: SettledSimulatedTrade): Boolean }

/** Production lifecycle boundary shared by engine and controlled simulation entry. Never dispatches broker taps. */
class ShadowSimulation(val gateway: SimulatedOrderGateway, private val journal: SimulationResultJournal) {
    @Volatile var enabled = false
    @Volatile var lastError: String? = null; private set
    @Synchronized fun open(request: OrderRequest, tick: MarketTick, metadata: SimMetadata = SimMetadata(), onResult: (OrderReceipt) -> Unit) {
        if (!enabled || request.mode != AutoTradeMode.SHADOW) { onResult(OrderReceipt.Vetoed("SHADOW_SIMULATION_DISABLED")); return }
        if (tick.asset.isBlank() || gateway.simClock.nowMs() - tick.timestampMs !in 0L..5000L) { onResult(OrderReceipt.Vetoed("SIM_QUOTE_STALE")); return }
        gateway.dispatchAt(request, tick.price, tick.timestampMs, tick.asset, metadata, onResult)
    }
    @Synchronized fun onTick(tick: MarketTick): List<SettledSimulatedTrade> {
        return try {
            val settled = gateway.onNewTick(tick.price, tick.timestampMs, tick.asset)
            flushResults(); settled
        } catch (e: Exception) { lastError = e.message ?: "SIM_STORAGE_FAILURE"; emptyList() }
    }
    /** Called on startup and every tick, even when disabled or after a mode change. */
    @Synchronized fun flushResults() {
        try {
            for (result in gateway.simLedger.pendingJournal()) {
                if (!journal.append(result)) { lastError = "SIM_JOURNAL_WRITE_FAILED"; return }
                gateway.simLedger.acknowledgeJournal(result.tradeId)
            }
            lastError = null
        } catch (e: Exception) { lastError = e.message ?: "SIM_OUTBOX_FAILURE" }
    }
}
