package com.example.tradedraw.simulation

import com.example.tradedraw.RingBufferEventSink
import com.example.tradedraw.core.*
import java.util.UUID

/**
 * Pasarela de órdenes simuladas para backtests y modo SHADOW con ciclo de vida completo.
 * Reutiliza las políticas de ejecución sin tocar brokers reales ni pantallas.
 */
class SimulatedOrderGateway(
    val simLedger: SimLedger = SimLedger(),
    val simClock: SimClock = SystemSimClock(),
    val eventSink: EventSink = RingBufferEventSink(),
    var currentPriceSupplier: () -> Double = { 0.0 },
    var defaultStake: Double = 10.0
) : OrderGateway {

    private val pendingTrades = mutableListOf<OpenSimulatedTrade>()

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        if (request.mode == AutoTradeMode.DISABLED) {
            val msg = "TRADE_DISPATCH_REJECTED_MODE_DISABLED"
            eventSink.add(
                "VETO",
                "SIMULATED_GATEWAY_REJECTED mode=${request.mode}",
                decisionId = request.decisionId,
                tradeId = request.tradeId
            )
            onResult(OrderReceipt.Vetoed(msg))
            return
        }

        val now = simClock.nowMs()
        val entryPrice = currentPriceSupplier()
        val tradeId = request.tradeId ?: UUID.randomUUID().toString()
        val decisionId = request.decisionId ?: UUID.randomUUID().toString()
        val candleSec = ((now / 1000L) % 60L).toInt()

        val openTrade = simLedger.openTrade(
            tradeId = tradeId,
            decisionId = decisionId,
            action = request.action,
            entryPrice = entryPrice,
            stake = defaultStake,
            timestampMs = now
        )

        synchronized(pendingTrades) {
            pendingTrades.add(openTrade)
        }

        eventSink.add(
            "SHADOW_DECISION",
            "SIMULATED_ORDER ${request.action} @ $entryPrice stake=$defaultStake",
            decisionId = decisionId,
            tradeId = tradeId
        )

        onResult(OrderReceipt.Success("SIMULATED", latencyMs = 0L, candleSecond = candleSec))
    }

    /**
     * Evalúa la resolución de órdenes pendientes que hayan superado los 60 segundos de expiración.
     */
    fun onNewTick(tickPrice: Double, tickTimestampMs: Long = simClock.nowMs()): List<SettledSimulatedTrade> {
        val resolved = mutableListOf<SettledSimulatedTrade>()
        synchronized(pendingTrades) {
            val iterator = pendingTrades.iterator()
            while (iterator.hasNext()) {
                val open = iterator.next()
                if (tickTimestampMs - open.entryTimestampMs >= 60_000L) {
                    val settled = simLedger.resolveTrade(open.tradeId, tickPrice, tickTimestampMs)
                    if (settled != null) {
                        resolved.add(settled)
                        val pnl = String.format(java.util.Locale.US, "%.2f", settled.settledBalance - settled.baseBalance)
                        eventSink.add(
                            "TRADE_RESULT",
                            "SIM_SETTLED ${settled.action} ${settled.result} @ ${settled.exitPrice} PnL=$pnl",
                            decisionId = settled.decisionId,
                            tradeId = settled.tradeId
                        )
                    }
                    iterator.remove()
                }
            }
        }
        return resolved
    }

    fun getPendingTradesCount(): Int {
        synchronized(pendingTrades) {
            return pendingTrades.size
        }
    }
}
