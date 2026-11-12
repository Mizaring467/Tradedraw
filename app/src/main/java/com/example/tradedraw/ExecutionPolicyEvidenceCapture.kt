package com.example.tradedraw

import com.example.tradedraw.execution.ExecutionContext
import org.json.JSONObject

/**
 * Read-only evidence collector for values already consumed by ExecutionPolicy.
 * It must never decide, veto, transform, or re-evaluate a supplier.
 */
class ExecutionPolicyEvidenceCapture {
    private var trend: TrendDirection? = null
    private var callPower: Int? = null
    private var adaptive: AdaptiveDecision? = null
    private var capital: Pair<Boolean, String>? = null
    private var srPlan: Pair<String, String>? = null

    fun recordBuyInputs(valueTrend: TrendDirection, valueCallPower: Int) {
        trend = valueTrend
        callPower = valueCallPower
    }

    fun recordAdaptive(value: AdaptiveDecision): AdaptiveDecision =
        value.also { adaptive = it }

    fun recordCapital(value: Pair<Boolean, String>): Pair<Boolean, String> =
        value.also { capital = it }

    fun recordSrPlan(value: Pair<String, String>): Pair<String, String> =
        value.also { srPlan = it }

    fun exactJsonOrNull(ctx: ExecutionContext): String? {
        val resolvedTrend = trend ?: return null
        val resolvedCallPower = callPower ?: return null
        val resolvedAdaptive = adaptive ?: return null
        val resolvedCapital = capital ?: return null
        val resolvedSrPlan = srPlan ?: return null

        return runCatching {
            JSONObject()
                .put("action", ctx.action.name)
                .put("mode", ctx.mode.name)
                .put("autonomousSubMode", ctx.autonomousSubMode.name)
                .put("strategy", ctx.strategy.name)
                .put("currentDecisionId", ctx.currentDecisionId)
                .put("tickAgeMs", ctx.tickAgeMs ?: JSONObject.NULL)
                .put("isFeedFresh", ctx.isFeedFresh)
                .put("feedMaxAgeMs", ctx.feedMaxAgeMs)
                .put("observedAsset", ctx.observedAsset)
                .put("feedAsset", ctx.feedAsset)
                .put("rangoRelativo", ctx.rangoRelativo)
                .put("isPriceFrozen", ctx.isPriceFrozen)
                .put("isSyntheticOrOtc", ctx.isSyntheticOrOtc)
                .put("sec", ctx.sec)
                .put("isChoppinessDetected", ctx.isChoppinessDetected)
                .put("reasonDescription", ctx.reasonDescription)
                .put("lastTradeResolutionTime", ctx.lastTradeResolutionTime)
                .put("currentLossStreak", ctx.currentLossStreak)
                .put("currentTimeMs", ctx.currentTimeMs)
                .put("trend", resolvedTrend.name)
                .put("callPower", resolvedCallPower)
                .put("adaptiveDecision", adaptiveJson(resolvedAdaptive))
                .put(
                    "capitalPolicyCanExecute",
                    JSONObject()
                        .put("allowed", resolvedCapital.first)
                        .put("reason", resolvedCapital.second)
                )
                .put("srFilterStatus", resolvedSrPlan.first)
                .put("actionPlan", resolvedSrPlan.second)
                .put("currentCandleEpochMinute", ctx.currentCandleEpochMinute)
                .put("isCandleAlreadyExecuted", ctx.isCandleAlreadyExecuted)
                .toString()
        }.getOrNull()
    }

    private fun adaptiveJson(value: AdaptiveDecision): JSONObject = when (value) {
        is AdaptiveDecision.Allow -> JSONObject()
            .put("kind", "ALLOW")
            .put("action", value.action.name)
            .put("confidenceModifier", value.confidenceModifier)
            .put("reason", value.reason)
        is AdaptiveDecision.Block -> JSONObject()
            .put("kind", "BLOCK")
            .put("action", JSONObject.NULL)
            .put("confidenceModifier", JSONObject.NULL)
            .put("reason", value.reason)
        is AdaptiveDecision.Invert -> JSONObject()
            .put("kind", "INVERT")
            .put("action", value.invertedAction.name)
            .put("confidenceModifier", 1.15f)
            .put("reason", value.reason)
    }

    companion object {
        const val QUALITY = "EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS"
        const val VERSION = 1

        private val REQUIRED_FIELDS = setOf(
            "action", "mode", "autonomousSubMode", "strategy", "currentDecisionId",
            "tickAgeMs", "isFeedFresh", "feedMaxAgeMs", "observedAsset", "feedAsset",
            "rangoRelativo", "isPriceFrozen", "isSyntheticOrOtc", "sec",
            "isChoppinessDetected", "reasonDescription", "lastTradeResolutionTime",
            "currentLossStreak", "currentTimeMs", "trend", "callPower",
            "adaptiveDecision", "capitalPolicyCanExecute", "srFilterStatus", "actionPlan",
            "currentCandleEpochMinute", "isCandleAlreadyExecuted"
        )

        /**
         * Fail-closed schema check used at the durable export boundary.
         * A parseable/partial JSON object is diagnostic data, not exact policy evidence.
         */
        fun parseExactJsonOrNull(raw: String?): JSONObject? {
            if (raw.isNullOrBlank()) return null
            return runCatching { JSONObject(raw) }.getOrNull()?.takeIf { json ->
                REQUIRED_FIELDS.all(json::has) &&
                    json.optJSONObject("adaptiveDecision") != null &&
                    json.optJSONObject("capitalPolicyCanExecute") != null
            }
        }
    }
}
