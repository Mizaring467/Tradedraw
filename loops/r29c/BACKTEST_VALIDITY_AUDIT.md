# BACKTEST_VALIDITY_AUDIT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:24:08.313630+00:00 — audited findings

Verdict: INVALID_FOR_EDGE_INFERENCE. Technical settlement corrections do not repair all execution-policy discrepancies.

C3-A: Legacy ReplayBacktestTest.loadTicks, replay.load_ticks, data_quality.analyze_ticks and null_baseline.load_pre_t0_ticks used timestamp-only keys. Actual 37 tick sources: zero cross-asset collisions, all Z-CRY/IDX; one repeated timestamp in a tail response. The failing pre-change Kotlin test had A/B at the same timestamp and a duplicate A: expected2, actual1. Correct identity is exact broker RIC+timestamp because no source sequence exists; first observation wins. Same-asset conflicting equal-time prices cannot be independently sequenced. Research Kotlin now preserves both assets, rejects invalid numbers and ignores stale arrivals per asset. Python replay rejects mixed-asset files before stripping RIC, rather than combining state. data_quality/null_baseline remain legacy single-asset diagnostic tools and must not receive a mixed file.

C3-B: First eligible same-asset future tick replaces last tick<=expiry. Exhaustive source-by-source output: evidence/expiry_audit_v1.json. Diagnostic grid entries at every accepted tick (BUY and SELL) are NOT strategy trades. Grid:3043 WIN→LOSS,3043 LOSS→WIN;622 WIN→TRUE_TIE,622 LOSS→TRUE_TIE;559 TRUE_TIE→WIN,559 TRUE_TIE→LOSS;10800 action-grid outcomes became NO_DATA. These rows include repeated/overlapping captures and must NOT be added as independent N. Among stored replay rows with exact raw entry matches:5 WIN→LOSS,1 LOSS→WIN,1 TRUE_TIE→WIN,7 NO_DATA;259 unchanged WIN/LOSS. Missing exact-entry rows remain explicitly unverified in JSON. All affected historical edge claims are INVALID_UNDER_RUNTIME_SEMANTICS, not erased.

C3-C: Legacy null exit was TIE; worse, last<=expiry could return a stale entry price when no future tick existed. Corrected replay emits NO_DATA; equal valid prices alone mean true tie (wire TIE, canonical TRUE_TIE). Missing outcomes do not create PnL or adaptive losses. evaluate.py now fails closed on NO_DATA or unknown outcome rather than silently discarding it; TRUE_TIE maps to its supported tie representation. It is still NOT the approved R29-C inferential evaluator.

C3-D: ADAPTIVE_STATE_JSON previously accepted any lossSignatures with no window/asset/knowledge-time check and swallowed errors. It now requires snapshot captured_at_ms before window start; every loss must have ts, settled_at_ms no earlier than entry+60000 and no later than snapshot, and matching asset. Unknown metadata rejects the run. Deliberately future snapshot and future-known loss are rejection-tested. No historical preload is approved by this change.

C3-E: Date(entryTick.ts) is event-time formatting, not Date() wall time. UTC formatting now explicit. SyntheticCandleEngine.recalculateTrend uses event tick timestamp on its only call from onNewTick; fallback System.currentTimeMillis remains in frozen source. FrozenStrategySignals and AdaptiveLearningEngine also have wall-clock paths. The replay calls pure AdaptiveReplayPort matching, with explicit signature time0, not recordTradeOpened/recordTradeResult. No blanket clock-purity claim: non-positive timestamps/fallback routes and future adapters remain gated. Prospective capture's wall clock records live receipt time and is not a historical replay clock.

C3-F: Kotlin runs each exact RIC in an independent candle/adaptive/settlement context. This is independent diagnostic portfolios, NOT a proof of global multi-asset single-flight capital parity. Python replay rejects multi-asset sources. Tests cover equal timestamp/different asset and out-of-order arrivals. Existing Python replay_v2_tick and monitor/counterfactual paths still have global state/nearest-tick assumptions; their reports cannot certify the frozen strategy.

C3-G execution assumptions:

| Assumption | Classification | Limitation |
|---|---|---|
| Instant fill | OPTIMISTIC | broker acknowledgement and rejected fills absent |
| Exact entry tick price | OPTIMISTIC | quote is not guaranteed strike |
| Fixed expiry entry+60000 | UNKNOWN for broker, REALISTIC for simulation contract | broker expiry/purchase cutoff unverified |
| Zero execution latency | OPTIMISTIC | device delay not modelled |
| Zero slippage | OPTIMISTIC | no strike reconciliation |
| No rejected orders | OPTIMISTIC | no broker response model |
| Missing future ticks | UNKNOWN | explicit NO_DATA, never financial tie |
| First post-expiry tick after large gap | REALISTIC for SimLedger, UNKNOWN for broker | stale marking/actual fixing time can differ |
| Payout0.82 in old replay | UNKNOWN | a configured assumption, not observed historical payout |
| Feed delay | UNKNOWN | receipt/exchange timestamp mapping unverified |
| Clock skew | UNKNOWN | no historical synchronization evidence |

Additional invalidity: pendingSignals are generated with speculative occupied-time gating before adaptive veto evaluation in a second pass. The candidate schedule can diverge from runtime after a veto or late settlement. Confidence is hardcoded0.85, velocity output was entry price, historical price_y was rounded to2 decimals despite tiny CryptoIDX moves, and adaptive contextual fields are neutral proxies. Entry price output precision now preserved, but other proxies remain explicitly diagnostic. Historical warmup pruning changes state. Full live ExecutionPolicy is not invoked by ReplayBacktestTest. Statistical inference remains STOPPED; a green JUnit suite does not validate these assumptions.

Test evidence:3 intentional red regressions before corrections (red-regressions.log, local ignored log); later settlement fixtures pass. Clean full gate674/85/0F/0E/0skips and APK PASS after correcting Termux library path. These are infrastructure results, never market edge.
