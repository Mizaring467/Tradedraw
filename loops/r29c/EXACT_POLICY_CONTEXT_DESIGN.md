# R29-C · Exact Pre-Outcome Policy Context — Versioned Instrumentation Design

## Decision

The current frozen production version MUST NOT be modified to capture exact ExecutionPolicy inputs.
TradingEngine.kt and ExecutionPolicy.kt are hashed by the current immutable strategy registry.
Changing either file creates a new source version even when the intended change is telemetry-only.

Current prospective evidence remains conservative:
- post-decision /status snapshots are observations, not exact decision context;
- top-level action/reason describe the final prediction and may differ after adaptive inversion;
- missing exact inputs must remain missing rather than reconstructed retrospectively.

## Why the exact seam is blocked

TradingEngine.executeHeadlessTrade creates the ExecutionContext and passes four lazy inputs into ExecutionPolicy.evaluate:
1. buy-exhaustion trend/call-power;
2. adaptive decision;
3. capital-policy decision;
4. S/R filter status plus action plan.

On an Allowed path all four are consumed before dispatch, but the exact values are not persisted.
No non-frozen adapter currently receives the complete effective policy input set.
Reflection or post-hoc reconstruction is rejected because it would weaken causal evidence.

## Approved future design — NEW VERSION ONLY

1. Add a read-only pre-outcome observer at the policy boundary.
2. Observe already-consumed lazy supplier values; never evaluate a supplier twice or change veto ordering.
3. Persist canonical JSON containing the full 27-field effective policy input set, including ORIGINAL action, currentDecisionId and reasonDescription.
4. Keep top-level prospective fields as the FINAL prediction after adaptive allow/invert.
5. Use a backward-compatible durable snapshot version; old snapshots must remain readable.
6. Export quality EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS and version 1.
7. Register a new immutable StrategyVersion/source-hash set; never overwrite the current frozen registry.
8. Prove behavioral equivalence against the current frozen version before admitting the new version to a prospective cohort.

## Gate

Until that version exists, FULL_REPLAY_EXECUTION_PARITY_PASS remains false.
Research must distinguish POLICY_CONTEXT_EXACT, POLICY_CONTEXT_COMPLETE_LEGACY_OR_UNSEALED and POLICY_CONTEXT_INCOMPLETE.
No historical or prospective record may be upgraded to EXACT by inference.
