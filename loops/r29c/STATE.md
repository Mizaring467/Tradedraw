# R29-C state — 2026-11-12T01:09:51.187607+00:00

## 2026-11-12 — causal replay scheduling checkpoint

HEAD base for this cut: a414cbf. Production strategy/risk/execution bytes remain untouched. The diagnostic Kotlin replay now settles open SHADOW-equivalent trades causally on the first eligible same-asset tick before feeding that tick to the strategy engine. Adaptive/policy vetoes are evaluated before the research gate consumes candle/cooldown state, eliminating the previously documented phantom occupancy after a veto. SHADOW-simulated outcomes no longer mutate replay loss-signature memory because production SHADOW does not call AdaptiveLearningEngine.recordTradeOpened/recordTradeOutcome for simulated trades.

Directed research tests added: veto-without-cooldown, settlement-before-same-tick-candidate, configured cooldown, NO_DATA at end-of-stream and independent gap cooldown. Versioned CI fixture comparison against a414cbf: same 6 deterministic decision IDs and same 4 WIN / 2 LOSS outcomes; no result cherry-picking. tick_velocity output now carries actual tick velocity rather than entry price.

Validation: research suite PASS; impact-aware fast gate PASS; dedicated Kotlin replay PASS; clean full gate 681 tests / 87 classes / 0 failures / 0 errors / 0 skips, Python 87 PASS, replay smoke PASS and APK PASS.

CAUSAL_SCHEDULING_PASS=true. FULL_REPLAY_PARITY remains false. Remaining parity blockers: complete production ExecutionPolicy/context equivalence, global multi-asset portfolio/single-flight semantics, device quote/fill latency, and runtime/prospective source evidence. DATA_PASS remains false and no edge inference is authorized. Exact next research action: introduce a research execution-policy adapter that invokes the actual frozen ExecutionPolicy against explicitly versioned replay context; prove its veto ordering without inventing unavailable device state.

Current round: C0 IN_PROGRESS. HEAD: 4fdb6466d5005499789d801d64916ea9c15421a3. Initial tree clean, r10-night.
Last approved commit/CI: not independently reverified; historical baseline ab2b73b push43014306435/PR43014313683 success per R29-B STATE.
Completed rounds: none. Failed gates: none evaluated. Blockers: provenance and parity not yet audited. Approved research datasets: none. Experiments: none executed.
Exact next action: run reproducible byte-hashed inventory over all dataset formats excluding git/build/caches; inspect capture producers and identify lineage before approving any dataset.
Baseline claimed by prior round: JVM658/classes84/0F/0E/0skips, Python47, replay/APK PASS; must remeasure after code changes.

## 2026-11-11 — C0/C1/C2 checkpoint
C0 inventory completed for enumerated extensions:945 files/42,960,299bytes. C1 dependency audit:46 exact-copy groups/119 CSV overlap pairs; causal parents incomplete. C2 immutable catalog945 plus118 source manifests;0 VALID_FOR_RESEARCH,400 diagnostics,543 insufficient metadata,2 fixtures.
HEAD remains4fdb646. Base CI independently verified:43025318940/43025307877 SUCCESS. Completed rounds:C0,C1(initial conservative lineage),C2. Failed gates:DATA_PASS=false; REPLAY_PARITY_PASS=false on uncorrected harness.
C3 diagnostic expiry audit executed for all37 tick sources, not a strategy experiment. Three red regressions observed (3/3 fail) before correction. Recorded exact-entry replay:5 WIN→LOSS,1 LOSS→WIN,1 TRUE_TIE→WIN,7 NO_DATA changes. All affected legacy claims INVALID_UNDER_RUNTIME_SEMANTICS.
Approved datasets:none. Edge experiments:none. Exact next action:commit inventory cut separately; correct research-only timestamp/expiry/missing-data paths and fail closed adaptive preload; add parity tests and re-run clean full gates. Keep production strategies unchanged.

## 2026-11-11 — C3/C4 checkpoint
Research correction local gate: clean full suite674tests/85classes/0F/0E/0skips;APK built and installed Success. Initial aapt2 environment failure retained; LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib resolves it. Python59PASS includes prospective work not yet in this cut.
C3 audit complete with unresolved conceptual invalidity explicitly documented. C4 helper settlement parity PASS (11actual replay/ledger/SHADOW boundary tests); full replay execution parity FAIL, statistical claims STOPPED.
C5 source registry authoritative version2:14modes/44production files, all byte-identical to4fdb646. Version1 missed COMBINED and is preserved as superseded failed extraction, never used for experiments.
Current HEAD b6b6e8b (inventory cut). Last approved CI remains base4fdb646 runs43025318940/43025307877. No new CI approval inherited.
Exact next action:commit backtest corrections independently; finish read-only open-SHADOW prediction export and hash-chained prospective collector, then clean full gates/install/runtime verification; prospective live cohort must be preregistered and source-qualified. Approved research datasets0;edge experiments0.

## 2026-11-11 — C17 instrumentation checkpoint
HEAD563a269 (research correction committed). Local gate for prospective read-only endpoint+collector:676tests/86classes/0F/0E/0skips,clean APK PASS;Python59PASS. No production strategies/guards changed. C5 frozen44file hash verification still PASS.
C6/C7–C16 historical statistics NOT_RUN_GATED (DATA=false,fullREPLAY_PARITY=false);reports contain hypotheses/protocols only. C17 pending decision projection and append-only capture implemented;C18 fail-closed scorecard implemented,0approvedsettlements.
Runtime installation of prior research cut succeeded, but current session device evidence differs from historic root device assumptions:emulator-5554 reports2311DRK48G;display Dozing,black captures;bridgeunavailable;private service start denied;su unavailable. Do not declare runtimePASS or trade.
Exact next action:commit prospective instrumentation independently,clean build/install exact source commit,attempt safe in-app startup and read-only capture under preregistered diagnostic manifest;push onlyr10-night and verify push/PR CI. Finish C19 with honest failed/unevaluated gates.

## 2026-11-12T01:35:52.739881+00:00 — exact source candidate checkpoint
Current round:C19 evidence gate / runtime verification attempt. HEAD b5378fc910b06168eb9fd10ef977641b1199d677, pushed only to r10-night. Last approved push CI43135219044 SUCCESS; existing PR7 CI43135228011 pending, not approved yet. Exact-source clean suite676/86/0F/0E/0skips and APK PASS. Python59PASS; all972 raw/source inventory hashes unchanged.
Completed:C0,C1 conservative,C2,C3 audit,C4 helper only,C5 frozen,C17 implementation,C18 scorecard. Failed:DATA/fullREPLAY_PARITY;C7–C16 NOT_RUN_GATED. Approved datasets0;edge experiments0;pre-registered transport diagnostic R29C-PLUMBING-20261111-001 not yet executed.
ADB server restart recovered emulator-5554; now attempting exact-source install and app-UID service startup. Exact next action:finish bounded runtime/diagnostic attempts,record outcomes including errors,wait both CI,append final tester/evidence report and publish documentation checkpoint.

## 2026-11-12T01:40:57.549260+00:00 — final reproducible checkpoint (supersedes earlier state sections)

Current round:C19 evidence adjudication. Overall status:INSUFFICIENT_EVIDENCE. HEAD reference:refs/heads/r10-night (resolve git rev-parse HEAD; documentation-only child of the source commit follows this checkpoint). Exact validated implementation HEAD:b5378fc910b06168eb9fd10ef977641b1199d677. Last approved source commit:b5378fc;pushCI43135219044SUCCESS and existingPR7CI43135228011SUCCESS;both independently checked. Downloaded CI XML676/86/0F/0E/0skips,replay present;localclean same totals,Python59,exactAPK installedSuccess.

Completed:C0inventory972files(main945+text27),C1conservative independence/lineage,C2immutable catalogs/manifests,C3adversarial audit,C4helper settlement parity,C5frozen14modes/44source hashes,C17open-SHADOW export+append-only collector/protocol,C18fail-closed scorecard,C19evidence gate. FullC4failed;C6historical experiment entry blocked;C7–C16NOT_RUN_GATED.

Failed gates:TECHNICAL=false(runtime),DATA=false,FULL_REPLAY_PARITY=false,STATISTICAL=false(not evaluated),ROBUSTNESS=false(not evaluated),PROSPECTIVE=false. Approved research datasets:none. Edge experiments:none. Executed diagnostic:R29C-PLUMBING-20261111-001,preregistered immutable manifest,12capturedtransport failures/0predictions/0settlements;hash chain sealed in result manifest. It contributes no market N. No datasets/prompts/periods/losses deleted. All972 original hashes rechecked unchanged.

Blockers:data provenance/source-clock/warmup/independence;two-phase speculative replay scheduling/adaptive/policy parity;no virgin historical holdout;live app runtime source unavailable. Device did not provide a valid bridge status;displayDozing/black images;foreground service start denied from shell and app UID,su absent. ADBrecovery attempted;exactAPK install succeeded,but final battery queries timed out (initial48%,finalUNKNOWN). No further device tests planned without restored access. No broker orders,REAL account or guards weakened. R29-B remains OPEN for its separate fresh-DEMO external settlement proof.

Exact next action for continuation:inspect git status and this checkpoint;verify both final documentation CI before approving that child. Then restore app-side foreground/runtime source through the actual UI/authorized harness (human unlock if required),measure battery/source identity,verify GET /simulation pendingDecisions. Independently replace the diagnostic replay two-pass scheduler with a causal event loop using unchanged frozen strategies and actual policy,prove full parity,then preregister a NEW prospective cohort before any prediction/outcome. Do not execute C7–C16inference until DATA and fullPARITY pass. Current available historical windows cannot be called virgin holdout.

Final report:FINAL_REPORT.md;formal tester:TESTER_REPORT.md;machine booleans:evidence/evidence_gate_v1.json. Visible-session policy complied with;no delegated/headless agent substituted the work.

## 2026-11-12T01:44:08.775852+00:00 — authoritative provenance revision v2

Final adversarial metadata review found5standalone status snapshots classified too broadly by payload schema,including a test status payload and4R26snapshot files. Their exact paths/hashes/overrides are sealed in evidence/provenance_review_v2.json. Schema alone does not establish device-capture origin. Classification is now UNKNOWN/INSUFFICIENT_METADATA for those5;raw bytes unchanged. Prior manifests/catalog remain immutable as superseded history. Authoritative catalog:datasets/manifests/catalog.v2.json (with five per-dataset v2 manifests);remaining rows retain v1 metadata.

Current main945-object totals:395VALID_FOR_DIAGNOSTICS_ONLY,548INSUFFICIENT_METADATA,2FIXTURE_ONLY;classifications111BROKER_DEVICE_CAPTURE,237REPORT_DERIVED,47REPLAY_DERIVED,548UNKNOWN,2FIXTURE. Supplemental27text logs remain non-research. Research-approved datasets still0;statistical/verdict gates unchanged. Earlier400/543/116 totals describe superseded v1 only. This metadata correction introduces no source-code/strategy change and no new experiment.

HEAD at review17e9eae921da2d390468dee72536b8c5181b5b1c;HEAD_ref refs/heads/r10-night resolves the subsequent documentation-only revision. Validated implementation and last approved code CI remainb5378fc/43135219044/43135228011. Next action:publish conservative provenance overlay,verify both final documentation CI,then resume only after data/full-parity/runtime blockers above are resolved.


## 2026-11-11 — parity hardening continuation (authoritative next-action update)

R29-C remains **INSUFFICIENT_EVIDENCE**. This continuation does not change frozen production strategy/risk logic and does not promote any historical dataset to research-approved status.

Research parity hardening completed after the causal scheduler checkpoint:
- The research module now invokes the actual frozen ExecutionPolicy through ResearchExecutionPolicyAdapter for controlled/causal fixtures. Historical evaluation fails closed when required context is UNKNOWN or CONTROLLED_FIXTURE instead of fabricating device state.
- Prospective scorecards now audit exact ExecutionContext coverage. Existing predictions are correctly POLICY_CONTEXT_INCOMPLETE; no default values are synthesized to claim parity.
- Read-only /status snapshots can be sealed as PARTIAL_PRE_OUTCOME_OBSERVATION linked to a decision. They remain explicitly exact_decision_context=false; missing adaptive/capital/SR/candle-limiter inputs stay unresolved.
- Kotlin replay now fails closed on mixed-asset tick files without a causal active-asset timeline (MIXED_ASSETS_REQUIRES_ACTIVE_ASSET_TIMELINE) instead of summing independent per-asset portfolios that would bypass production's global single-flight semantics. Python replay already rejected mixed assets.
- Prospective capture now records causal ASSET screen observations from /events as an append-only active-asset timeline. These points are not interpolated and do not yet authorize mixed-asset replay.

Validation evidence:
- ExecutionPolicy adapter cut: clean full gate 686 tests / 88 classes / 0 failures / 0 errors / 0 skips, Python87, replay smoke PASS, APK PASS; main CI push/PR green.
- Mixed-asset fail-closed cut: clean full gate 688 / 89 / 0F / 0E / 0 skips, Python94, replay smoke PASS, APK PASS; main CI push/PR green.
- Active-asset timeline cut: Python99 PASS locally; main CI pending at the moment this checkpoint is prepared and must be green before this documentation is approved.

FULL_REPLAY_EXECUTION_PARITY_PASS=false remains authoritative. The remaining technical gap is narrower but still material: prove exact decision-time policy context, define causal active-asset intervals without future interpolation, then implement a global single-flight multi-asset replay scheduler only for datasets that contain that evidence. DATA/STATISTICAL/ROBUSTNESS/PROSPECTIVE edge gates remain false or unevaluated. No real-money inference is authorized.

Exact next action: wait for active-asset timeline CI; then specify and test a causal interval join (decision/tick -> active asset) that returns UNKNOWN on uncovered gaps. Only after that join is proven may a multi-asset global single-flight replay be implemented. Do not fall back to independent per-asset portfolios.


## 2026-11-11 — parity hardening validation update

This section supersedes the pending-CI sentence in the previous parity-hardening checkpoint.

Validated mainline evidence:
- causal screen-asset timeline: main dd22bb2; push CI 43146260301 SUCCESS; PR CI 43146267260 SUCCESS; Python99 PASS before integration;
- causal active-asset join: main 8e55001; push CI 43146578870 SUCCESS; PR CI 43146586495 SUCCESS; Python104 PASS before integration;
- mixed-asset replay remains fail-closed unless a causal active-asset timeline exists;
- research global portfolio scheduler now reuses production SingleFlightOrderGateway. Local clean gate for this cut: 692 tests / 90 classes / 0 failures / 0 errors / 0 skips, Python104 PASS, replay smoke PASS, APK PASS. Main commit 5ec34fe is published; CI is pending at this checkpoint and must be green before approval.

The scheduler proves the global reservation invariant independently of strategy generation: a second asset cannot occupy another slot while a trade is open, and a trade expiring exactly at an event timestamp releases the slot before that timestamp's next candidate. UNKNOWN or mismatched active-asset evidence never consumes the slot.

This still does not make full multi-asset replay valid. The current remaining seam is data/interface parity: Kotlin replay needs a versioned active-asset timeline input with an explicit preregistered max-age, UNKNOWN on gaps/conflicts, and no future interpolation. Only then should per-asset strategy state feed candidates into the already-proven global SingleFlight scheduler.

FULL_REPLAY_EXECUTION_PARITY_PASS=false remains authoritative. No edge/statistical gate changes.


## 2026-11-11 — mixed-asset replay wiring checkpoint

The orphaned parity worktree was resumed without modifying frozen production strategy/risk/execution code. Kotlin research replay now consumes the versioned causal active-asset timeline when explicitly supplied, routes only causally active ticks, resets per-active-stream velocity state at asset switches, and sends accepted candidates through the already-tested global production SingleFlight-backed research scheduler. Settlement is same-asset and causal; foreign ticks cannot release the reservation. Mixed-asset adaptive preload remains rejected because its legacy provenance is not asset-scoped.

Prospective collection was hardened at the same boundary: capture records carry `epoch_id`; timeline export selects exactly one COMPLETE epoch, requires `--epoch-id` when multiple completed epochs exist, rejects incomplete epochs, scopes asset deduplication by epoch and writes `capture_epoch_id` into the manifest. This prevents two capture sessions from being silently merged.

Validation so far for this resumed cut: Python 112 PASS; forced `:research:testDebugUnitTest --rerun-tasks` PASS (53 tasks executed, proving the modified Kotlin was actually recompiled); impact-aware gate PASS for Python + research + replay smoke (23 diagnostic replay trades, 0 gaps, 0 duplicate timestamps). Full clean multimodule gate is still pending before publication.

Verdict remains unchanged: `DATA_PASS=false`, `FULL_REPLAY_EXECUTION_PARITY_PASS=false`, `STATISTICAL_PASS=false/not evaluated`, and overall `INSUFFICIENT_EVIDENCE`. This cut removes a structural replay seam; it does not authorize edge, profitability or real-money claims.


### Full gate del corte

Certificación final local ejecutada con `python scripts/ci/fast_verify.py --full` sobre working tree limpio de outputs:

- Python: **112 PASS**.
- Gradle clean multimódulo: **704 tests / 92 clases / 0 failures / 0 errors / 0 skipped**.
- `:app:assembleDebug`: PASS dentro del mismo clean gate.
- Replay smoke versionado: **23 trades diagnósticos / 0 gaps >10s / 0 timestamps duplicados**.
- El Gradle full ejecutó 276 tasks (275 ejecutadas, 1 up-to-date); no se reutilizó el falso verde incremental del primer intento.
- Estrategias productivas y parámetros permanecieron sin cambios.

El gate técnico de este corte es PASS. El gate cuantitativo permanece **INSUFFICIENT_EVIDENCE**: estos resultados certifican infraestructura causal/reproducibilidad, no edge.


## 2026-11-11 — prospective cohort isolation checkpoint

Prospective evidence is now scoped end-to-end by capture epoch. `PREDICTION`, `PREDICTION_REJECTED`, policy observations, settlement candidates, transport/error evidence and active-asset observations can be attributed to one epoch. `scorecard()` refuses to combine multiple completed epochs without an explicit selector and exposes `capture_epoch_id` plus `capture_epoch_complete`.

A recovered/old `SHADOW_PREDICTION` event predating the epoch boundary is not admitted to the new cohort. Settlement attribution follows the immutable prediction epoch, so a later reconciliation cannot migrate a decision between cohorts.

Validation: targeted prospective tests **13/13 PASS**; full Python impact gate **134 PASS**; no Android/Gradle/APK work is required because this cut changes only research Python/tests/docs.

Verdict remains `INSUFFICIENT_EVIDENCE`. Cohort integrity improved; DATA/STATISTICAL/PROSPECTIVE evidence gates are not thereby satisfied.
