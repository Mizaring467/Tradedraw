# EXECUTION_PARITY_AUDIT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:24:08.313630+00:00 — audited findings

Canonical contract: entry=accepted quote with exact asset and event timestamp t0; expiry=t0+60000ms. Exit=first arrival accepted by per-asset monotonic watermark with timestamp>=expiry. Invalid/nonfinite/nonpositive prices and duplicate/older event times are ignored. Exact RIC, no unsafe alias conflation. WIN iff BUY exit>entry or SELL exit<entry. LOSS iff opposite strict comparison. TRUE_TIE iff an eligible exit exists and price equals entry exactly. NO_DATA iff no eligible exit in the available horizon; durable runtime remains pending. NO_DATA is not an economic result. Large gap does not change the current SimLedger contract, but leaves broker equivalence UNKNOWN.

Actual ReplayBacktestTest private helpers, actual SimLedger and actual ShadowSimulation/SimulatedOrderGateway are compared in SettlementParityTest. Eleven tests cover exact expiry,1ms before,1ms after,large gap,duplicate first-observation wins,out-of-order first-arrival settlement,different-asset same timestamp,other-asset-only,no future tick,equal price and SELL. Python boundary/mixed-file/arrival-order tests exercise its actual code. Wire runtime TIE maps to canonical TRUE_TIE without changing production serialization.

SETTLEMENT_HELPER_PARITY_PASS=true after674-test clean suite. FULL_REPLAY_EXECUTION_PARITY_PASS=false: candidate scheduling/adaptive feedback, global portfolio single-flight and actual ExecutionPolicy/quote latency are not equivalent (see validity audit). Therefore final REPLAY_PARITY_PASS=false; do not promote helper parity to full infrastructure validity. No production strategy code or guard was changed.
