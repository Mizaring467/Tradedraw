package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.simulation.OpenSimulatedTrade
import com.example.tradedraw.simulation.SimMetadata
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProspectivePredictionExportTest {
    private fun exactPolicyContext(): String = JSONObject()
        .put("action", "BUY")
        .put("mode", "SHADOW")
        .put("autonomousSubMode", "CONSERVATIVE")
        .put("strategy", "AUTO_ADAPTIVE")
        .put("currentDecisionId", "dec")
        .put("tickAgeMs", 20L)
        .put("isFeedFresh", true)
        .put("feedMaxAgeMs", 5000L)
        .put("observedAsset", "Crypto IDX")
        .put("feedAsset", "Z-CRY/IDX")
        .put("rangoRelativo", 0.001)
        .put("isPriceFrozen", false)
        .put("isSyntheticOrOtc", true)
        .put("sec", 1)
        .put("isChoppinessDetected", false)
        .put("reasonDescription", "original")
        .put("lastTradeResolutionTime", 0L)
        .put("currentLossStreak", 0)
        .put("currentTimeMs", 10_000L)
        .put("trend", "UPTREND")
        .put("callPower", 82)
        .put(
            "adaptiveDecision",
            JSONObject()
                .put("kind", "INVERT")
                .put("action", "SELL")
                .put("confidenceModifier", 1.15)
                .put("reason", "invert")
        )
        .put(
            "capitalPolicyCanExecute",
            JSONObject()
                .put("allowed", true)
                .put("reason", "ok")
        )
        .put("srFilterStatus", "SUPPORT")
        .put("actionPlan", "CALL")
        .put("currentCandleEpochMinute", 166L)
        .put("isCandleAlreadyExecuted", false)
        .toString()

    @Test fun exportsActualOpenDecisionIdentityAndMetadata() {
        val open = OpenSimulatedTrade(
            "trade", "dec", TradeAction.SELL, 641.867249075, 10.0,
            1793077444955L, "Z-CRY/IDX",
            SimMetadata("MT_REJECTION", "CONSERVATIVE", 85f, "DOWNTREND", "original reason")
        )
        val json = ProspectivePredictionExport.json(open, "frozen-build")
        assertEquals(open.decisionId, json.getString("decisionId"))
        assertEquals(open.entryTimestampMs, json.getLong("timestamp"))
        assertEquals(open.entryPrice, json.getDouble("entry_price"), 0.0)
        assertEquals(open.asset, json.getString("asset"))
        assertEquals("SELL", json.getString("action"))
        assertEquals("original reason", json.getString("reason"))
        assertEquals("MT_REJECTION@r29c-frozen-4fdb646", json.getString("strategyVersion"))
        assertEquals("r29c-frozen-4fdb646", json.getString("base_strategy_version"))
        assertEquals("frozen-build", json.getString("git_sha"))
        assertFalse(json.has("result"))
    }

    @Test fun neverInventsObservedPayoutOrDecisionRegime() {
        val open = OpenSimulatedTrade("trade", "dec", TradeAction.BUY, 100.0, 1.0, 10000L, "A")
        val json = ProspectivePredictionExport.json(open, "observer")
        assertTrue(json.isNull("observed_payout"))
        assertEquals("UNKNOWN_NOT_PERSISTED_AT_DECISION", json.getString("regime"))
        assertEquals("OBSERVER_BUILD_REQUIRES_CONTINUOUS_CAPTURE_EPOCH", json.getString("sha_attribution"))
        assertFalse(json.has("policy_context"))
    }

    @Test fun partialPersistedContextIsNeverUpgradedToExactEvidence() {
        val partial = """{"action":"BUY","currentDecisionId":"dec","reasonDescription":"original"}"""
        val open = OpenSimulatedTrade(
            "trade", "dec", TradeAction.SELL, 100.0, 1.0, 10000L, "A",
            SimMetadata(
                strategy = "AUTO_ADAPTIVE",
                submode = "CONSERVATIVE",
                confidence = 0.9f,
                trend = "DOWNTREND",
                reason = "final inverted reason",
                strategyVersion = "r29c-frozen-4fdb646",
                policyContextJson = partial
            )
        )

        val json = ProspectivePredictionExport.json(open, "observer-build")
        assertFalse(json.has("policy_context"))
        assertFalse(json.has("policy_context_quality"))
        assertFalse(json.has("policy_context_version"))
    }

    @Test fun exportsFrozenStrategyVersionAndValidatedExactPolicyContext() {
        val open = OpenSimulatedTrade(
            "trade", "dec", TradeAction.SELL, 100.0, 1.0, 10000L, "A",
            SimMetadata(
                strategy = "AUTO_ADAPTIVE",
                submode = "CONSERVATIVE",
                confidence = 0.9f,
                trend = "DOWNTREND",
                reason = "final inverted reason",
                strategyVersion = "r29c-instrumented-policy-v1",
                policyContextJson = exactPolicyContext()
            )
        )

        val json = ProspectivePredictionExport.json(open, "observer-build")
        assertEquals("AUTO_ADAPTIVE@r29c-instrumented-policy-v1", json.getString("strategyVersion"))
        assertEquals("r29c-frozen-4fdb646", json.getString("base_strategy_version"))
        assertEquals("SELL", json.getString("action"))
        assertEquals("BUY", json.getJSONObject("policy_context").getString("action"))
        assertEquals(
            ExecutionPolicyEvidenceCapture.QUALITY,
            json.getString("policy_context_quality")
        )
        assertEquals(
            ExecutionPolicyEvidenceCapture.VERSION,
            json.getInt("policy_context_version")
        )
    }
}
