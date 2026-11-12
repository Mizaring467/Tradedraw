package com.example.tradedraw

import com.example.tradedraw.core.AutoTradeMode
import com.example.tradedraw.core.AutoTradeStrategy
import com.example.tradedraw.core.AutonomousSubMode
import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.execution.ExecutionContext
import com.example.tradedraw.execution.ExecutionPolicy
import com.example.tradedraw.execution.ExecutionPolicyHooks
import com.example.tradedraw.execution.ExecutionPolicyResult
import org.junit.Assert.*
import org.junit.Test

class ResearchExecutionPolicyAdapterTest {
    private fun policy(): ExecutionPolicy = ExecutionPolicy(object : ExecutionPolicyHooks {
        override fun rangeThreshold(asset: String): Double =
            if (asset.contains("IDX", ignoreCase = true)) 1e-11 else 1e-5

        override fun assetMismatch(activoPantallaVisible: String, activoFeed: String): String? =
            if (activoPantallaVisible == activoFeed) null else "ASSET_MISMATCH"

        override fun adaptiveDetail(reason: String, signal: String): String =
            "ADAPTIVE_BLOCK $reason $signal"

        override fun buyExhaustion(action: TradeAction, trend: TrendDirection, power: Int): String? = null

        override fun actionConflict(srStatus: String, actionPlan: String): Boolean = false
    })

    private fun context(
        sec: Int = 2,
        tickAgeMs: Long? = 0L,
        isFeedFresh: Boolean = true,
        observedAsset: String = "Z-CRY/IDX",
        feedAsset: String = "Z-CRY/IDX",
        isCandleAlreadyExecuted: Boolean = false,
    ) = ExecutionContext(
        action = TradeAction.SELL,
        mode = AutoTradeMode.SHADOW,
        autonomousSubMode = AutonomousSubMode.CONSERVATIVE,
        strategy = AutoTradeStrategy.AUTO_ADAPTIVE,
        currentDecisionId = "research-policy-test",
        tickAgeMs = tickAgeMs,
        isFeedFresh = isFeedFresh,
        observedAsset = observedAsset,
        feedAsset = feedAsset,
        rangoRelativo = 1e-6,
        isPriceFrozen = false,
        isSyntheticOrOtc = true,
        sec = sec,
        isChoppinessDetected = false,
        reasonDescription = "MT_REJECTION fixture",
        lastTradeResolutionTime = 0L,
        currentLossStreak = 0,
        currentTimeMs = 120_000L,
        trend = TrendDirection.DOWNTREND,
        callPower = 20,
        adaptiveDecision = AdaptiveDecision.Allow(TradeAction.SELL),
        capitalPolicyCanExecute = true to "",
        srFilterStatus = "",
        actionPlan = "",
        currentCandleEpochMinute = 2L,
        isCandleAlreadyExecuted = isCandleAlreadyExecuted,
    )

    private fun qualities(value: PolicyEvidenceQuality) =
        PolicyContextGroup.entries.associateWith { value }

    @Test
    fun historicalEvaluationFailsClosedWhenRiskEvidenceIsUnknown() {
        val quality = qualities(PolicyEvidenceQuality.CAUSALLY_DERIVED).toMutableMap()
        quality[PolicyContextGroup.CAPITAL_RISK_STATE] = PolicyEvidenceQuality.UNKNOWN

        val result = ResearchExecutionPolicyAdapter(policy()).evaluateHistorical(
            ResearchPolicyEvidence(context(), quality)
        )

        assertTrue(result is ResearchPolicyEvaluation.InsufficientContext)
        assertEquals(
            setOf(PolicyContextGroup.CAPITAL_RISK_STATE),
            (result as ResearchPolicyEvaluation.InsufficientContext).unresolved,
        )
    }

    @Test
    fun controlledFixtureMayExerciseActualPolicyButIsLabelledNonHistorical() {
        val result = ResearchExecutionPolicyAdapter(policy()).evaluateControlledFixture(
            ResearchPolicyEvidence(context(sec = 30), qualities(PolicyEvidenceQuality.CONTROLLED_FIXTURE))
        )

        assertTrue(result is ResearchPolicyEvaluation.Evaluated)
        result as ResearchPolicyEvaluation.Evaluated
        assertTrue(result.controlledFixture)
        val veto = result.result as ExecutionPolicyResult.Vetoed
        assertEquals(7, veto.vetoIndex)
        assertEquals("FUERA_DE_VENTANA sec=30", veto.message)
    }

    @Test
    fun actualProductionPolicyStillShortCircuitsAtEarliestVeto() {
        val result = ResearchExecutionPolicyAdapter(policy()).evaluateControlledFixture(
            ResearchPolicyEvidence(
                context(sec = 30, tickAgeMs = null, isFeedFresh = false, isCandleAlreadyExecuted = true),
                qualities(PolicyEvidenceQuality.CONTROLLED_FIXTURE),
            )
        ) as ResearchPolicyEvaluation.Evaluated

        val veto = result.result as ExecutionPolicyResult.Vetoed
        assertEquals(1, veto.vetoIndex)
        assertTrue(veto.message.startsWith("FEED_RANCIO"))
    }

    @Test
    fun fullyCausalEvidenceInvokesActualPolicyWithoutControlledFixtureLabel() {
        val result = ResearchExecutionPolicyAdapter(policy()).evaluateHistorical(
            ResearchPolicyEvidence(context(), qualities(PolicyEvidenceQuality.CAUSALLY_DERIVED))
        )

        assertTrue(result is ResearchPolicyEvaluation.Evaluated)
        result as ResearchPolicyEvaluation.Evaluated
        assertFalse(result.controlledFixture)
        assertTrue(result.result is ExecutionPolicyResult.Allowed)
    }

    @Test
    fun controlledFixtureStillRejectsAnyUnknownContextFamily() {
        val quality = qualities(PolicyEvidenceQuality.CONTROLLED_FIXTURE).toMutableMap()
        quality[PolicyContextGroup.FEED_IDENTITY_FRESHNESS] = PolicyEvidenceQuality.UNKNOWN

        val result = ResearchExecutionPolicyAdapter(policy()).evaluateControlledFixture(
            ResearchPolicyEvidence(context(), quality)
        )

        assertTrue(result is ResearchPolicyEvaluation.InsufficientContext)
    }
}
