package com.example.tradedraw

import android.content.Context
import com.example.tradedraw.core.*
import org.json.JSONObject

class PersistentPendingDispatchStore(context: Context) : PendingDispatchStore {
    private val prefs = context.getSharedPreferences("TradeDraw_Dispatch", Context.MODE_PRIVATE)
    override fun wasDispatched(requestId: String) = requestId in prefs.getStringSet("dispatchedIds", emptySet()).orEmpty()
    override fun remember(requestId: String) {
        val ids = prefs.getStringSet("dispatchedIds", emptySet()).orEmpty().toMutableSet().apply { add(requestId) }
        check(prefs.edit().putStringSet("dispatchedIds", ids).commit())
    }
    override fun load(): String? = prefs.getString("pendingRequestId", null)
    override fun save(requestId: String?) {
        val edit = prefs.edit().putString("pendingRequestId", requestId)
        if (requestId == null) edit.remove("reservation").remove("reviewReason")
        check(edit.commit()) { "Dispatch state write failed" }
    }
    override fun reserve(request: OrderRequest) {
        val id = requireNotNull(request.tradeId ?: request.decisionId)
        val ids = prefs.getStringSet("dispatchedIds", emptySet()).orEmpty().toMutableSet().apply { add(id) }
        val json = JSONObject().put("requestId", id).put("decisionId", request.decisionId ?: "").put("action", request.action.name)
        request.recoveryContext?.let { c -> json.put("createdAtMs", c.createdAtMs).put("baseBalance", c.baseBalance)
            .put("entryPrice", c.entryPrice).put("confidence", c.confidence).put("reason", c.reason) }
        check(prefs.edit().putString("pendingRequestId", id).putString("reservation", json.toString())
            .putStringSet("dispatchedIds", ids).commit()) { "Dispatch reservation write failed" }
    }
    override fun reservation(): DispatchReservation? {
        val id = load() ?: return null
        return runCatching {
            val j = JSONObject(prefs.getString("reservation", "{}") ?: "{}")
            val c = if (j.has("createdAtMs")) BrokerRecoveryContext(j.getLong("createdAtMs"), j.getDouble("baseBalance"),
                j.getDouble("entryPrice"), j.getDouble("confidence").toFloat(), j.optString("reason")) else null
            DispatchReservation(id, j.optString("decisionId"), TradeAction.valueOf(j.optString("action", "BUY")), c)
        }.getOrElse { DispatchReservation(id) } // Historical ID-only reservation remains blocked/visible.
    }
    override fun writeReceipt(requestId: String, receipt: OrderReceipt) {
        val j = JSONObject()
        when (receipt) {
            is OrderReceipt.Success -> j.put("type", "success").put("transport", receipt.transport).put("latency", receipt.latencyMs).put("second", receipt.candleSecond)
            is OrderReceipt.Failure -> j.put("type", "failure").put("reason", receipt.reason).put("uncertain", receipt.requiresReconciliation)
            is OrderReceipt.Vetoed -> j.put("type", "veto").put("reason", receipt.reason)
        }
        check(prefs.edit().putString("receipt:$requestId", j.toString()).commit())
    }
    override fun readReceipt(requestId: String): OrderReceipt? = runCatching {
        val raw = prefs.getString("receipt:$requestId", null) ?: return null
        val j = JSONObject(raw)
        when (j.getString("type")) {
            "success" -> OrderReceipt.Success(j.getString("transport"), j.getLong("latency"), j.getInt("second"))
            "failure" -> OrderReceipt.Failure(j.getString("reason"), requiresReconciliation = j.getBoolean("uncertain"))
            else -> OrderReceipt.Vetoed(j.getString("reason"))
        }
    }.getOrNull()
    override fun reviewReason(): String? = prefs.getString("reviewReason", null)
    override fun setReviewReason(reason: String?) { check(prefs.edit().putString("reviewReason", reason).commit()) }
    override fun lastEvidence(): BrokerDispatchEvidence? = runCatching {
        val raw = prefs.getString("lastEvidence", null) ?: return null
        val j = JSONObject(raw)
        BrokerDispatchEvidence(j.getString("id"), j.getString("reference"), BrokerRecoveredOutcome.valueOf(j.getString("outcome")), j.getLong("observedAtMs"))
    }.getOrNull()
    override fun resolve(evidence: BrokerDispatchEvidence) {
        val j = JSONObject().put("id", evidence.requestId).put("reference", evidence.brokerReference)
            .put("outcome", evidence.outcome.name).put("observedAtMs", evidence.observedAtMs).put("source", evidence.source)
        check(prefs.edit().putString("lastEvidence", j.toString()).remove("pendingRequestId").remove("reservation").remove("reviewReason").commit())
    }
}
