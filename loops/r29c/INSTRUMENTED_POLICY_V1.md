# R29-C · Instrumented Policy Context v1

## Scope

Branch-only research instrumentation. This cut is **not** the frozen production
strategy version and is **not** authorized as a real-money release.

- Strategy version: `r29c-instrumented-policy-v1`
- Base strategy version: `r29c-frozen-4fdb646`
- Instrumentation parent on `r10-night`: `21b0a55`
- Purpose: persist exact pre-outcome inputs actually consumed by
  `ExecutionPolicy` for prospective SHADOW evidence.

## Frozen-source boundary

The immutable registry contains 44 production sources. The instrumentation
registry verifies that exactly one differs:

`app/src/main/java/com/example/tradedraw/TradingEngine.kt`

The following remain byte-identical to the frozen registry:

- `execution/.../ExecutionPolicy.kt`
- `strategy/.../FrozenStrategySignals.kt`
- the other 41 registered production sources.

The original `strategy_registry_v2.json` is preserved unchanged. The separate
registry is:

`loops/r29c/evidence/strategy_registry_instrumented_policy_v1.json`

## Behavior

The policy observer:

1. reuses the same `ExecutionContext`;
2. wraps already-lazy suppliers without re-evaluating them;
3. returns the exact adaptive/capital/SR values it observes;
4. records buy-exhaustion inputs immediately before the original call;
5. persists evidence only after `ExecutionPolicyResult.Allowed`;
6. persists exact context only in SHADOW simulation;
7. does not change policy thresholds, veto order, strategy rules, broker guards,
   or real-money permissions.

## Evidence schema

Exact evidence is accepted only when all 27 required fields are present.
Parseable but partial JSON is not upgraded to exact evidence.

Quality:

`EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS`

Schema version:

`1`

The Kotlin producer and Python research consumer have an automated contract test
that requires the exact same 27-field schema and quality/version tokens.

## Durable format

`FileSimSnapshotStore` writes TDS2 and remains backward-compatible with TDS1.

TDS2 adds:

- `strategyVersion`
- `policyContextJson`

Legacy TDS1 snapshots load with the prior default strategy version and no exact
policy context.

## Versioning

Prospective export identifies both:

- `strategyVersion = <strategy>@r29c-instrumented-policy-v1`
- `base_strategy_version = r29c-frozen-4fdb646`

Therefore evidence from this build cannot be silently mixed with the frozen
4fdb646 cohort.

## Gates

Directed equivalence gate:

- `VetoCascadeEquivalenceTest`: PASS
- `ExecutionPolicyEvidenceCaptureTest`: PASS
- `ProspectivePredictionExportTest`: PASS
- `FileSimSnapshotStoreV2Test`: PASS

Instrumentation Python gate:

- ordinary tests: 137 PASS
- frozen-source guard: exactly one expected rejection, `TradingEngine.kt`
- instrumentation registry: PASS
- Kotlin/Python exact-schema contract: PASS

Clean multimodule gate with versioned replay fixture:

- Gradle tests: **712**
- classes: **94**
- failures: **0**
- errors: **0**
- skips: **0**
- `:app:assembleDebug`: PASS
- APK SHA-256:
  `5a4989aba1a005aaf2020ed4f84dc96540a3b4395b452ab7c7417b8420861c52`

## Research interpretation

This cut improves causal observability. It does **not** demonstrate trading edge.

R29-C remains:

`INSUFFICIENT_EVIDENCE`

This build may be used only for a separately identified prospective SHADOW
capture epoch after runtime identity is verified. Historical observations must
not be retroactively upgraded to exact policy-context evidence.
