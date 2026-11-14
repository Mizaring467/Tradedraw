package com.example.tradedraw

import com.example.tradedraw.core.MemoryPendingDispatchStore
import com.example.tradedraw.core.OrderGateway
import com.example.tradedraw.core.OrderReceipt
import com.example.tradedraw.core.OrderRequest
import com.example.tradedraw.core.SingleFlightOrderGateway
import com.example.tradedraw.core.TradeAction

/**
 * Research-only global portfolio scheduler.
 *
 * It deliberately reuses production [SingleFlightOrderGateway] instead of
 * reimplementing the global reservation rule. Strategy state remains outside
 * this class; this seam only answers whether a causally attributed candidate
 * could occupy the single production order slot at its event timestamp.
 */
internal class ResearchMultiAssetScheduler(
    private val activeAssetAt: (Long) -> String?,
    private val expiryMs: Long = 60_000L,
) {
    init {
        require(expiryMs > 0) { "INVALID_EXPIRY" }
    }

    data class Candidate(
        val timestampMs: Long,
        val asset: String,
        val action: TradeAction,
        val decisionId: String,
        val tradeId: String,
    )

    sealed class Decision {
        data class Accepted(val candidate: Candidate) : Decision()
        data class Vetoed(val reason: String) : Decision()
    }

    private data class OpenResearchTrade(
        val candidate: Candidate,
        val expiresAtMs: Long,
    )

    private val store = MemoryPendingDispatchStore()
    private var transportCalls = 0
    private val delegate = object : OrderGateway {
        override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
            transportCalls += 1
            onResult(
                OrderReceipt.Success(
                    transport = "RESEARCH_SIMULATED_ACCEPT",
                    latencyMs = 0L,
                    candleSecond = 0,
                )
            )
        }
    }
    private val flight = SingleFlightOrderGateway(delegate, store)
    private var open: OpenResearchTrade? = null

    fun evaluate(candidate: Candidate): Decision {
        require(candidate.timestampMs >= 0) { "INVALID_TIMESTAMP" }
        require(candidate.asset.isNotBlank()) { "ASSET_REQUIRED" }
        require(candidate.decisionId.isNotBlank()) { "DECISION_ID_REQUIRED" }
        require(candidate.tradeId.isNotBlank()) { "TRADE_ID_REQUIRED" }

        val observed = activeAssetAt(candidate.timestampMs)
            ?: return Decision.Vetoed("ACTIVE_ASSET_UNKNOWN")
        if (!observed.equals(candidate.asset, ignoreCase = true)) {
            return Decision.Vetoed("ACTIVE_ASSET_MISMATCH")
        }

        var receipt: OrderReceipt? = null
        flight.dispatch(
            OrderRequest(
                action = candidate.action,
                x = 0f,
                y = 0f,
                decisionId = candidate.decisionId,
                tradeId = candidate.tradeId,
            )
        ) { result -> receipt = result }

        return when (val result = receipt) {
            is OrderReceipt.Success -> {
                open = OpenResearchTrade(candidate, candidate.timestampMs + expiryMs)
                Decision.Accepted(candidate)
            }
            is OrderReceipt.Vetoed -> Decision.Vetoed(result.reason)
            is OrderReceipt.Failure -> Decision.Vetoed("TRANSPORT_FAILURE:" + result.reason)
            null -> Decision.Vetoed("NO_SYNCHRONOUS_RESEARCH_RECEIPT")
        }
    }

    /**
     * Mirror durable SHADOW settlement eligibility: only a tick from the SAME
     * asset at/after expiry may release the global slot. A tick from another
     * active asset must leave the pending trade reserved.
     */
    fun onMarketTick(timestampMs: Long, asset: String): Candidate? {
        require(timestampMs >= 0L) { "INVALID_TIMESTAMP" }
        require(asset.isNotBlank()) { "ASSET_REQUIRED" }
        val current = open ?: return null
        if (!current.candidate.asset.equals(asset, ignoreCase = true)) return null
        if (timestampMs < current.expiresAtMs) return null
        check(flight.reconcile(current.candidate.tradeId)) {
            "RESEARCH_SINGLE_FLIGHT_RECONCILE_FAILED"
        }
        open = null
        return current.candidate
    }

    fun pendingTradeId(): String? = flight.pendingRequestId()
    fun transportDispatchCount(): Int = transportCalls
}
