# Lineage and independence — 2026-11-12T01:12:35.216162+00:00

46 byte-identical groups and 119 CSV record-overlap pairs. Exhaustive edges: evidence/c0/exact_duplicates_v1.json and record_overlaps_v1.json. Shared records prove dependence, not causal direction; do not sum these files as independent observations.

Verified producer route at base 4fdb646: broker websocket → app MarketTick buffer → HTTP /ticks → scripts/validation/live_monitor_r23_autonomous.py accumulated_ticks → ticks_live.csv. Monitor source lines1440–1495 emits decisions/trades/ticks. Initial buffer data are pruned at window start; monitor timestamp-only dictionary loses simultaneous assets before export. Historical exact generator invocation/git SHA is not present for every file: UNRESOLVED, not invented. Per-window live_samples.status.gitSha supplies observed APK identity where available.

live_samples.jsonl → status_samples.jsonl (status projection; same underlying observations); samples/status → decisions/trades extraction; ticks + frozen engine + optional adaptive snapshot → replay_journal; paired live/replay records → replay_comparison JSON; samples → inferred settlement transitions; decisions/ticks → counterfactuals; canonical ledgers → cubes. These transformations cannot increase independent N.

Exact script/parent candidates and content hashes appear in manifests. Unknown parents remain unknown. Compare_live_replay.save_fixture_to_csv (lines1088+) proves the r21 fixture pair is generated test input; it is never market evidence. Null_baseline.generate_null_series shuffles return blocks and rounds prices; this is a synthetic permutation, not independent real data or a calibrated zero-drift null. Original raw ticks referenced in old data/ticks reports are absent in this checkout.

## 2026-11-12T01:44:08.775852+00:00 — authoritative provenance revision v2

Final adversarial metadata review found5standalone status snapshots classified too broadly by payload schema,including a test status payload and4R26snapshot files. Their exact paths/hashes/overrides are sealed in evidence/provenance_review_v2.json. Schema alone does not establish device-capture origin. Classification is now UNKNOWN/INSUFFICIENT_METADATA for those5;raw bytes unchanged. Prior manifests/catalog remain immutable as superseded history. Authoritative catalog:datasets/manifests/catalog.v2.json (with five per-dataset v2 manifests);remaining rows retain v1 metadata.

Current main945-object totals:395VALID_FOR_DIAGNOSTICS_ONLY,548INSUFFICIENT_METADATA,2FIXTURE_ONLY;classifications111BROKER_DEVICE_CAPTURE,237REPORT_DERIVED,47REPLAY_DERIVED,548UNKNOWN,2FIXTURE. Supplemental27text logs remain non-research. Research-approved datasets still0;statistical/verdict gates unchanged. Earlier400/543/116 totals describe superseded v1 only. This metadata correction introduces no source-code/strategy change and no new experiment.
