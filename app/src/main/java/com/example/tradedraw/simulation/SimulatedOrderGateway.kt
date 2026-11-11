package com.example.tradedraw.simulation

import com.example.tradedraw.core.*
import java.util.UUID

class SimulatedOrderGateway(val simLedger: SimLedger = SimLedger(), val simClock: SimClock = SystemSimClock(),
    val eventSink: EventSink = NoOpEventSink(), var currentPriceSupplier: () -> Double = { 0.0 }, var defaultStake: Double = 10.0) : OrderGateway {
    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) =
        dispatchAt(request, currentPriceSupplier(), simClock.nowMs(), "", SimMetadata(), onResult)

    @Synchronized fun dispatchAt(request: OrderRequest, price: Double, timestamp: Long, asset: String,
        metadata: SimMetadata, onResult: (OrderReceipt) -> Unit) {
        if (request.mode != AutoTradeMode.SHADOW) {
            onResult(OrderReceipt.Vetoed("TRADE_DISPATCH_REJECTED_MODE_${request.mode}")); return
        }
        val decisionId = request.decisionId ?: request.tradeId?.let { "decision-$it" } ?: UUID.randomUUID().toString()
        val tradeId = request.tradeId ?: "sim-$decisionId"
        val receipt = try {
            val (old, settled) = simLedger.findByIds(tradeId, decisionId)
            if (settled != null) {
                require(settled.action == request.action && settled.asset == asset && settled.stake == defaultStake) { "SIM_RETRY_CONFLICT" }
            } else {
                simLedger.openTrade(tradeId, decisionId, request.action, price, defaultStake, timestamp, asset, metadata)
            }
            if (old == null && settled == null) runCatching { eventSink.add("SHADOW_DECISION", "SIMULATED_ORDER ${request.action} @ $price stake=$defaultStake", decisionId, tradeId) }
            OrderReceipt.Success("SIMULATED", 0L, ((timestamp / 1000L) % 60).toInt())
        } catch (e: Exception) {
            val persisted = runCatching { simLedger.findByIds(tradeId, decisionId) }.getOrNull()
            if (persisted?.first != null || persisted?.second != null) OrderReceipt.Success("SIMULATED", 0L, ((timestamp / 1000L) % 60).toInt())
            else OrderReceipt.Vetoed(e.message ?: "SIM_PERSISTENCE_FAILED")
        }
        onResult(receipt)
    }
    fun onNewTick(tickPrice: Double, tickTimestampMs: Long = simClock.nowMs(), asset: String = ""): List<SettledSimulatedTrade> {
        val results = simLedger.onTick(asset, tickPrice, tickTimestampMs)
        results.forEach { s -> eventSink.add("TRADE_RESULT", "SIM_SETTLED ${s.action} ${s.result} @ ${s.exitPrice} PnL=%.2f".format(java.util.Locale.US, s.settledBalance-s.baseBalance), s.decisionId, s.tradeId) }
        return results
    }
    fun getPendingTradesCount() = simLedger.getOpenTrades().size
}
