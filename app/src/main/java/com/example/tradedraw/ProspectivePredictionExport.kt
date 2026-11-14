package com.example.tradedraw

import com.example.tradedraw.simulation.OpenSimulatedTrade
import org.json.JSONObject

/** Read-only projection of an OPEN durable simulation. Never reconstruct predictions from results. */
object ProspectivePredictionExport {
    fun json(open: OpenSimulatedTrade, observerGitSha: String): JSONObject = JSONObject()
        .put("decisionId", open.decisionId)
        .put("timestamp", open.entryTimestampMs)
        .put("asset", open.asset)
        .put("strategyVersion", "${open.metadata.strategy}@r29c-frozen-4fdb646")
        .put("action", open.action.name)
        .put("confidence", open.metadata.confidence)
        .put("reason", open.metadata.reason)
        .put("regime", "UNKNOWN_NOT_PERSISTED_AT_DECISION")
        .put("observed_payout", JSONObject.NULL)
        .put("git_sha", observerGitSha)
        .put("entry_price", open.entryPrice)
        .put("source", "DURABLE_OPEN_SHADOW")
        .put("sha_attribution", "OBSERVER_BUILD_REQUIRES_CONTINUOUS_CAPTURE_EPOCH")
}
