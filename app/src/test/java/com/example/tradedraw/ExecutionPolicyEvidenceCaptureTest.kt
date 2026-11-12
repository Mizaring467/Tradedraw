package com.example.tradedraw

import com.example.tradedraw.core.AutoTradeMode
import com.example.tradedraw.core.AutoTradeStrategy
import com.example.tradedraw.core.AutonomousSubMode
import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.execution.ExecutionContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExecutionPolicyEvidenceCaptureTest {
    private fun context() = ExecutionContext(
        action = TradeAction.BUY,
        mode = AutoTradeMode.SHADOW,
        autonomousSubMode = AutonomousSubMode.CONSERVATIVE,
        strategy = AutoTradeStrategy.AUTO_ADAPTIVE,
        currentDecisionId = "decision-1",
        tickAgeMs = null,
        isFeedFresh = true,
        feedMaxAgeMs = 5000L,
        observedAsset = "Crypto IDX",
        feedAsset = "Z-CRY/IDX",
        rangoRelativo = 0.0012,
        isPriceFrozen = false,
        isSyntheticOrOtc = true,
        sec = 1,
        isChoppinessDetected = false,
        reasonDescription = "original policy reason",
        lastTradeResolutionTime = 123L,
        currentLossStreak = 1,
        currentTimeMs = 1000L,
        currentCandleEpochMinute = 16L,
        isCandleAlreadyExecuted = false
    )

    @Test fun recordersReturnExactlyTheValueTheyObserve() {
        val capture = ExecutionPolicyEvidenceCapture()
        val adaptive = AdaptiveDecision.Invert(TradeAction.SELL, "invert")
        val capital = true to "capital-ok"
        val sr = "SR_OK" to "PUT"

        assertSame(adaptive, capture.recordAdaptive(adaptive))
        assertSame(capital, capture.recordCapital(capital))
        assertSame(sr, capture.recordSrPlan(sr))
    }

    @Test fun incompleteCaptureIsEvidenceNullNotRuntimeFailure() {
        val capture = ExecutionPolicyEvidenceCapture()
        capture.recordBuyInputs(TrendDirection.UPTREND, 80)
        assertNull(capture.exactJsonOrNull(context()))
    }

    @Test fun exactSnapshotPreservesOriginalActionAcrossAdaptiveInversion() {
        val capture = ExecutionPolicyEvidenceCapture()
        capture.recordBuyInputs(TrendDirection.DOWNTREND, 77)
        capture.recordAdaptive(AdaptiveDecision.Invert(TradeAction.SELL, "invert"))
        capture.recordCapital(true to "")
        capture.recordSrPlan("SR_OK" to "PUT")

        val json = JSONObject(requireNotNull(capture.exactJsonOrNull(context())))
        assertEquals("BUY", json.getString("action"))
        assertEquals("original policy reason", json.getString("reasonDescription"))
        assertTrue(json.isNull("tickAgeMs"))
        assertEquals("DOWNTREND", json.getString("trend"))
        assertEquals(77, json.getInt("callPower"))
        assertEquals("INVERT", json.getJSONObject("adaptiveDecision").getString("kind"))
        assertEquals("SELL", json.getJSONObject("adaptiveDecision").getString("action"))
        assertTrue(json.getJSONObject("capitalPolicyCanExecute").getBoolean("allowed"))
        assertEquals("SR_OK", json.getString("srFilterStatus"))
        assertEquals("PUT", json.getString("actionPlan"))
    }

    @Test fun exactSnapshotContainsAllTwentySevenResearchFields() {
        val capture = ExecutionPolicyEvidenceCapture()
        capture.recordBuyInputs(TrendDirection.UPTREND, 82)
        capture.recordAdaptive(AdaptiveDecision.Allow(TradeAction.BUY, 1.0f, "allow"))
        capture.recordCapital(true to "ok")
        capture.recordSrPlan("SUPPORT" to "CALL")

        val json = JSONObject(requireNotNull(capture.exactJsonOrNull(context())))
        val required = setOf(
            "action", "mode", "autonomousSubMode", "strategy", "currentDecisionId",
            "tickAgeMs", "isFeedFresh", "feedMaxAgeMs", "observedAsset", "feedAsset",
            "rangoRelativo", "isPriceFrozen", "isSyntheticOrOtc", "sec",
            "isChoppinessDetected", "reasonDescription", "lastTradeResolutionTime",
            "currentLossStreak", "currentTimeMs", "trend", "callPower",
            "adaptiveDecision", "capitalPolicyCanExecute", "srFilterStatus", "actionPlan",
            "currentCandleEpochMinute", "isCandleAlreadyExecuted"
        )
        assertEquals(required, json.keySet())
    }
}
