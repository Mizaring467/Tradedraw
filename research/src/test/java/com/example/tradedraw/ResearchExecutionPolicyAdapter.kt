package com.example.tradedraw

import com.example.tradedraw.execution.ExecutionContext
import com.example.tradedraw.execution.ExecutionPolicy
import com.example.tradedraw.execution.ExecutionPolicyResult

/**
 * Evidence gate for using the production [ExecutionPolicy] from research.
 *
 * Historical replay must never fill unavailable device/broker fields with
 * convenient defaults just to obtain an Allowed result. The context object may
 * contain placeholders while a group is UNKNOWN, but [evaluateHistorical]
 * refuses to call production policy until every consumed context family is
 * backed by observed or causally-derived evidence.
 */
internal enum class PolicyEvidenceQuality {
    OBSERVED,
    CAUSALLY_DERIVED,
    CONTROLLED_FIXTURE,
    UNKNOWN,
}

internal enum class PolicyContextGroup {
    FEED_IDENTITY_FRESHNESS,
    MARKET_STATE,
    STRATEGY_TIMING,
    CLOCK_AND_COOLDOWN,
    ADAPTIVE_STATE,
    CAPITAL_RISK_STATE,
    SR_PLAN_STATE,
}

internal data class ResearchPolicyEvidence(
    val context: ExecutionContext,
    val quality: Map<PolicyContextGroup, PolicyEvidenceQuality>,
) {
    fun unresolvedForHistorical(): Set<PolicyContextGroup> =
        PolicyContextGroup.entries
            .filterTo(linkedSetOf()) { group ->
                when (quality[group] ?: PolicyEvidenceQuality.UNKNOWN) {
                    PolicyEvidenceQuality.OBSERVED,
                    PolicyEvidenceQuality.CAUSALLY_DERIVED -> false
                    PolicyEvidenceQuality.CONTROLLED_FIXTURE,
                    PolicyEvidenceQuality.UNKNOWN -> true
                }
            }

    fun unresolvedForControlledFixture(): Set<PolicyContextGroup> =
        PolicyContextGroup.entries
            .filterTo(linkedSetOf()) { group ->
                (quality[group] ?: PolicyEvidenceQuality.UNKNOWN) == PolicyEvidenceQuality.UNKNOWN
            }
}

internal sealed class ResearchPolicyEvaluation {
    data class InsufficientContext(
        val unresolved: Set<PolicyContextGroup>,
    ) : ResearchPolicyEvaluation()

    data class Evaluated(
        val result: ExecutionPolicyResult,
        val controlledFixture: Boolean,
    ) : ResearchPolicyEvaluation()
}

internal class ResearchExecutionPolicyAdapter(
    private val productionPolicy: ExecutionPolicy,
) {
    fun evaluateHistorical(evidence: ResearchPolicyEvidence): ResearchPolicyEvaluation {
        val unresolved = evidence.unresolvedForHistorical()
        if (unresolved.isNotEmpty()) {
            return ResearchPolicyEvaluation.InsufficientContext(unresolved)
        }
        return ResearchPolicyEvaluation.Evaluated(
            result = productionPolicy.evaluate(evidence.context),
            controlledFixture = false,
        )
    }

    /**
     * Controlled fixtures are useful for proving ordering/contract parity but
     * never count as historical market evidence.
     */
    fun evaluateControlledFixture(evidence: ResearchPolicyEvidence): ResearchPolicyEvaluation {
        val unresolved = evidence.unresolvedForControlledFixture()
        if (unresolved.isNotEmpty()) {
            return ResearchPolicyEvaluation.InsufficientContext(unresolved)
        }
        return ResearchPolicyEvaluation.Evaluated(
            result = productionPolicy.evaluate(evidence.context),
            controlledFixture = true,
        )
    }
}
