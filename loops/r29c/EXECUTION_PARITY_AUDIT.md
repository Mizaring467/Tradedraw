# EXECUTION_PARITY_AUDIT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:24:08.313630+00:00 — audited findings

Canonical contract: entry=accepted quote with exact asset and event timestamp t0; expiry=t0+60000ms. Exit=first arrival accepted by per-asset monotonic watermark with timestamp>=expiry. Invalid/nonfinite/nonpositive prices and duplicate/older event times are ignored. Exact RIC, no unsafe alias conflation. WIN iff BUY exit>entry or SELL exit<entry. LOSS iff opposite strict comparison. TRUE_TIE iff an eligible exit exists and price equals entry exactly. NO_DATA iff no eligible exit in the available horizon; durable runtime remains pending. NO_DATA is not an economic result. Large gap does not change the current SimLedger contract, but leaves broker equivalence UNKNOWN.

Actual ReplayBacktestTest private helpers, actual SimLedger and actual ShadowSimulation/SimulatedOrderGateway are compared in SettlementParityTest. Eleven tests cover exact expiry,1ms before,1ms after,large gap,duplicate first-observation wins,out-of-order first-arrival settlement,different-asset same timestamp,other-asset-only,no future tick,equal price and SELL. Python boundary/mixed-file/arrival-order tests exercise its actual code. Wire runtime TIE maps to canonical TRUE_TIE without changing production serialization.

SETTLEMENT_HELPER_PARITY_PASS=true after674-test clean suite. FULL_REPLAY_EXECUTION_PARITY_PASS=false: candidate scheduling/adaptive feedback, global portfolio single-flight and actual ExecutionPolicy/quote latency are not equivalent (see validity audit). Therefore final REPLAY_PARITY_PASS=false; do not promote helper parity to full infrastructure validity. No production strategy code or guard was changed.

## 2026-11-12 — causal scheduler correction

The research harness no longer generates every candidate first and settles them in a second pass. It now follows the production ordering that matters for SHADOW causality: an incoming same-asset tick settles an open simulated trade first; only then is that same tick fed into `SyntheticCandleEngine`. A rejected adaptive candidate does not consume the candle or create a phantom trade/cooldown; the causal gate is mutated only after candidate acceptance. End-of-stream open trades become `NO_DATA` without a future scan.

SHADOW runtime does not feed simulated outcomes into `AdaptiveLearningEngine`; therefore the causal replay keeps any validated preload loss-signature memory fixed during a SHADOW-equivalent run instead of learning from simulated losses. Signal snapshots now retain actual tick velocity/impulse rather than writing entry price into the `tick_velocity` column.

Directed `CausalReplayGateTest` cases verify veto-without-cooldown, settlement-before-same-tick-candidate, configured cooldown, end-of-stream NO_DATA and external gap cooldown. On the versioned CI fixture `loops/r23c_phase3_validation/window_01/ticks_live.csv`, old two-pass and new causal scheduler produced the same six deterministic decision IDs and the same 4 WIN / 2 LOSS outcomes. This is evidence that the structural correction did not cherry-pick the fixture outcome.

CAUSAL_SCHEDULING_PASS=true for the single-asset diagnostic path. FULL_REPLAY_EXECUTION_PARITY_PASS remains false: the harness still does not invoke the complete production `ExecutionPolicy` with its live context, does not prove global multi-asset portfolio/single-flight parity, and does not model device quote/fill latency. REPLAY_PARITY_PASS therefore remains false.


## 2026-11-11 — policy/context and multi-asset parity hardening

The research harness now imports and executes the production ExecutionPolicy rather than maintaining a second research copy of the veto cascade. ResearchExecutionPolicyAdapter labels every required context group as OBSERVED, CAUSALLY_DERIVED, CONTROLLED_FIXTURE or UNKNOWN. Historical evaluation is permitted only when all groups are causal/observed; otherwise it returns INSUFFICIENT_CONTEXT and does not manufacture a policy verdict. Controlled fixtures may exercise the production policy for ordering/regression tests but are never market evidence.

Prospective policy coverage is now machine-audited. The collector distinguishes precommitted prediction fields, post-decision/pre-outcome /status observations, and still-unresolved fields. The latter include inputs not exposed at the exact policy call (for example adaptive decision, capital policy decision, exact S/R plan/candle limiter state and screen identity where unavailable). A status snapshot therefore remains partial evidence, never exact decision context.

A second false-parity path was closed: Kotlin replay previously split mixed-asset tick files into independent portfolios and concatenated results. That can create simultaneous trades impossible under production's global single-flight gate. Mixed-asset Kotlin replay now throws MIXED_ASSETS_REQUIRES_ACTIVE_ASSET_TIMELINE, matching the existing Python fail-closed behavior.

Prospective capture additionally seals runtime ASSET events (atMs, visible asset, mapped RIC) as causal screen-asset observations. These points provide the raw material for a future active-asset timeline, but gaps are not interpolated and no interval end is inferred from future observations. Therefore they do **not** yet make mixed-asset replay valid.

Current verdict:
- settlement helper parity: PASS;
- causal single-asset scheduling: PASS;
- production ExecutionPolicy invocation for causally complete/controlled contexts: PASS;
- exact historical policy-context reconstruction: FAIL/INSUFFICIENT_CONTEXT;
- mixed-asset replay without active-asset timeline: deliberately BLOCKED;
- global multi-asset SingleFlight parity: NOT YET IMPLEMENTED;
- device quote/fill latency parity: UNKNOWN.

FULL_REPLAY_EXECUTION_PARITY_PASS=false and REPLAY_PARITY_PASS=false remain unchanged.


## 2026-11-11 — causal interval join and global SingleFlight oracle

A conservative causal join now exists for active-asset evidence. For any query timestamp it may use only the latest prior screen-asset observation and only when its age is within an explicit caller-supplied max_age_ms. There is deliberately no default max age. Queries before the first observation, stale observations, conflicting RICs at the same timestamp, or invalid timestamps remain UNKNOWN. Future observations never backfill the past, and unknown records remain in the output rather than being dropped.

A research-only MultiAsset scheduler now uses the actual production SingleFlightOrderGateway and MemoryPendingDispatchStore. Directed tests prove:
- a second asset is vetoed with ORDER_PENDING_RECONCILIATION while the first trade owns the global slot;
- an expiry at the exact same timestamp is reconciled before evaluating the next candidate;
- UNKNOWN/mismatched active-asset evidence never reaches transport or consumes the slot;
- a pre-expiry candidate cannot release the reservation.

Clean full gate for the scheduler cut: 692 tests / 90 classes / 0 failures / 0 errors / 0 skips, Python104 PASS, replay smoke PASS, APK PASS. This is an oracle for the portfolio invariant, not yet a wired full multi-asset strategy replay.

Remaining parity requirement: provide Kotlin research with a causally versioned active-asset timeline, preserve independent strategy/candle state by asset, and route accepted candidates through this single global scheduler. Until that exists, mixed-asset replay remains deliberately blocked and REPLAY_PARITY_PASS=false.


## 2026-11-11 — mixed-asset replay wiring and epoch isolation

The previously separate causal components are now wired into the Kotlin research replay path. Mixed-asset input remains fail-closed by default; it is accepted only when an explicit `ResearchActiveAssetTimeline` is supplied. Accepted ticks are filtered through `ResearchActiveTickRouter`, asset switches reset synthetic tick velocity/gap state, candidate dispatches pass through one global `ResearchMultiAssetScheduler`, and only a same-asset tick at/after expiry may reconcile the global reservation before the next candidate.

Research safeguards remain conservative:
- no active-asset timeline => `MIXED_ASSETS_REQUIRES_ACTIVE_ASSET_TIMELINE`;
- unknown/stale/conflicting timeline evidence never reaches the strategy engine;
- mixed-asset replay rejects `ADAPTIVE_STATE_JSON` because the legacy adaptive snapshot is not asset-scoped/causally attributable;
- foreign-asset ticks cannot settle or release the pending global slot;
- a timeline-backed integration regression verifies the replay accepts the causal interface without falling back to independent per-asset portfolios.

Prospective timeline export is now capture-epoch isolated. New journals mark `CAPTURE_EPOCH_START/END`; exports use exactly one COMPLETE epoch. Multiple completed epochs require an explicit `--epoch-id`, incomplete epochs are rejected, and active-asset deduplication includes `epoch_id`. Legacy journals without epoch markers remain supported. The exported manifest records `capture_epoch_id`, preventing accidental cross-session timeline mixing.

This closes the research wiring seam, not the evidence gate. `FULL_REPLAY_EXECUTION_PARITY_PASS=false` remains authoritative because exact decision-time production policy context, source-qualified market data, broker/device quote/fill latency and a prospective runtime cohort are still missing. Controlled timeline fixtures and scheduler tests are structural evidence only; they are not trading-performance evidence.
