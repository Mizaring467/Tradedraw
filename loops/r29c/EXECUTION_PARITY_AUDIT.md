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
