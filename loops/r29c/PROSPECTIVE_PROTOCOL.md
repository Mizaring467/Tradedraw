# PROSPECTIVE_PROTOCOL — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:27:40.975463+00:00 — gate outcome

C17: implementation added for append-only prospective capture without changing strategy logic. Read-only GET /simulation exports pendingDecisions from actual durable OPEN trades (not settled journal rows), preserving decisionId,event timestamp,exact asset,action,confidence,original reason,entry price and version r29c-frozen-4fdb646. Unknown regime is explicitly UNKNOWN_NOT_PERSISTED_AT_DECISION and observed_payout=null; simulation configured payout is never advertised as observed. git_sha is the observing APK build, with an explicit continuous-epoch attribution requirement: only openings after capture start on an unchanged build may qualify. Probe/TEST assets are rejected from research.

Collector: python scripts/research/prospective.py capture --journal <new-epoch.jsonl> --samples <fixed-count> --interval2. It only GETs127.0.0.1:8080 through adb forward, never broker endpoints or mode controls. Poll order is status→open predictions→events→ticks so a canonical precommit precedes outcome capture. A decision must arrive before its60s expiry, matching the preregistered build and an active SHADOW simulation. Old/recovered openings before epoch start are not silently relabeled. Legacy SHADOW_DECISION events lack canonical fields and are retained as UNSCORABLE_LEGACY_DECISION. All transport/errors are preserved.

Each record is append-only with sequence,previous SHA-256,receipt timestamp,kind,payload and hash; flock protects concurrent appends and fsync persists before return. Corrections are new records. Same decision/payload is idempotent; changed prediction is rejected and retained. A valid chain detects edits but is not an external timestamp authority: seal final digest/count in git/independent storage periodically. Never rewrite raw capture to fix errors.

Settlement is a later append-only SETTLEMENT_CANDIDATE with same asset,entry+60000 cutoff,exit timestamp/price and derived result. It stays unverified until reconciled with complete raw accepted ticks and durable runtime settlement. No future tick remains pending/NO_DATA at a prespecified observation cutoff, never TIE. Duplicate/conflicting settlements cannot overwrite prior ones. Imported candidates do NOT increase approved performance metrics automatically.

Before a genuine cohort: exclusive-create immutable experiment manifest with current code SHA,collector hash,registry hash,source definitions,account/feed identity,UTC start/end and fixed30-calendar-day horizon; no optional stopping. Pin raw files by rolling hashes. Record all decisions/vetoes/rejections; ensure device/broker/feed clocks measured and buffers cannot overflow between polls. A capture gap is a failure record, not a skipped period. Hypothetical payout0.70/0.75/0.80/0.85 remains separate from observed payout. Primary acceptance needs>=200decided AND enough independent blocks,dependence-aware lower expectancy CI>0 and corrected advantage over matched controls; missingness/discrepancy sensitivity must not reverse conclusion. This is a research threshold, not authorization for real money.

Current implementation intentionally leaves statistical adjudication manual/fail-closed. It does not infer regime at entry from future status or claim broker-fill equivalence. Full prospective validation requires actual feed decisions and runtime reconciliation; deterministic probes can validate capture plumbing only.

## 2026-11-12T01:40:57.549260+00:00 — implementation/run clarification

Correct CLI syntax uses a space: --interval 2. Open-decision GET projection is now implemented, so a structured event is no longer required for that route. A fresh continuous epoch and unchanged observer-build identity remain mandatory; old recovered orders remain excluded. The pre-registered smoke diagnostic failed with12transport errors,not a prospective market result. This collector is a bounded diagnostic implementation; long-term storage/rotation and independently sealed checkpoints must be validated before an unattended30-day cohort. No long capture was started.


## 2026-11-11 — policy-context and active-asset evidence extension

The append-only collector now records two additional evidence classes without issuing control/broker requests:

1. POLICY_CONTEXT_OBSERVATION: a read-only /status snapshot associated with an already precommitted decision. Fields carry explicit quality labels (PRECOMMITTED_PREDICTION, CAUSALLY_DERIVED_FROM_DECISION_TIMESTAMP, POST_DECISION_PRE_OUTCOME_OBSERVED). The record always states exact_decision_context=false unless a future source can prove otherwise. Missing fields remain enumerated instead of receiving defaults.

2. ACTIVE_ASSET_OBSERVATION: parsed only from runtime ASSET events with their own atMs, visibleAsset and mapped ric. Events older than the capture epoch are rejected; duplicates are idempotent. The timeline summary reports observed assets/timestamps and temporal regressions.

Important limitation: an ACTIVE_ASSET_OBSERVATION is a causal screen observation, not proof that the same asset remained selected over an unobserved gap. The collector therefore exposes supports_mixed_asset_replay=false. Future work must define a conservative interval join that marks uncovered periods UNKNOWN; it must never backfill an earlier interval using a later asset event.

These extensions improve the quality of future SHADOW cohorts but do not alter the C19 verdict. No prospective performance statistic becomes approved merely because more context was captured.


## 2026-11-11 — causal interval semantics for future cohorts

Active-asset observations can now be joined to a decision/tick timestamp conservatively. The join uses only the latest observation at or before the query time and requires a preregistered max_age_ms. It returns UNKNOWN for no prior observation, stale evidence, conflicting same-timestamp RICs, or invalid record timestamps. The join never deletes an unknown record and never uses a future ASSET observation to infer the past.

For any future prospective cohort intended to support multi-asset replay, the experiment manifest must therefore pin:
- the active-asset observation source and parser version;
- max_age_ms before capture begins;
- handling of conflicts/gaps as UNKNOWN;
- the code SHA and collector SHA;
- the exact timeline file hash after sealing.

A screen-asset point plus this causal join is stronger than retrospective asset inference, but it still does not prove continuous selection across an unobserved gap. Mixed-asset performance statistics remain gated until the Kotlin strategy replay consumes the same versioned timeline and the global production SingleFlight scheduler.


## 2026-11-11 — capture epoch isolation requirement

A timeline intended for mixed-asset replay must come from exactly one completed capture epoch. The collector now emits a unique `epoch_id` at `CAPTURE_EPOCH_START`, propagates it to transport/error/active-asset evidence, and seals `CAPTURE_EPOCH_END`.

Export rules:
- legacy journals with no epoch markers remain readable for backward-compatible diagnostics;
- one COMPLETE epoch is selected automatically;
- more than one COMPLETE epoch is ambiguous and fails with `NO_UNIQUE_COMPLETE_CAPTURE_EPOCH` unless `--epoch-id` is supplied;
- an explicit missing or incomplete epoch fails closed;
- asset-observation deduplication is scoped by `(epoch_id, atMs, ric, visibleAsset)`;
- the timeline manifest records `capture_epoch_id` and the selected epoch's terminal hash/code SHA.

Therefore separate capture sessions cannot be concatenated merely because they share a build SHA. A future cohort must preregister its epoch/timeline selection and preserve the exported manifest hash before any replay or scorecard interpretation.
