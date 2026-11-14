# Dataset inventory — 2026-11-12T01:12:35.216162+00:00

C0 byte inventory snapshot: 945 files, 42,960,299 bytes. Scanned CSV/JSON/JSONL/NDJSON/Parquet/SQLite/DB/log across repo, including ignored files; excluded git, build, Gradle, Python/node caches, skill/config internals and this audit output. No Parquet/SQLite files found. Raw files read only.

Machine-readable exhaustive fields: [inventory_v1.json](evidence/c0/inventory_v1.json). All 945 objects include ID, hash, bytes, row/schema count, assets, timestamp interval/resolution/order, duplicate/gap/invalid counts and result fields. Unknown fields remain null; logs count physical lines rather than trade samples. Two JVM crash logs contain invalid UTF-8; retained by hash, not treated as market data.

Timestamp GCD is observed resolution, not a claim of clock accuracy; gaps >10s are measured per asset for tick sources. Numeric epoch UTC does not establish broker exchange time. UTF-16 BOM is decoded without modifying originals. JSON response arrays are unwrapped; status samples often have no event timestamp.

Classification requires content plus producer evidence. Initial mechanical snapshot conservatively says UNKNOWN; reviewed classifications and eligibility are separate immutable manifests below. These are not 945 independent datasets.

| Path | Tick rows | Assets | Global timestamp drops | Cross-asset keys lost |
|---|---:|---|---:|---:|
| loops/r23c_phase3_validation/window_01/ticks.jsonl | 500 | Z-CRY/IDX | 0 | 0 |
| loops/r23c_phase3_validation/window_01/ticks_live.csv | 3354 | Z-CRY/IDX | 0 | 0 |
| loops/r23c_phase3_validation/window_02/ticks.jsonl | 500 | Z-CRY/IDX | 1 | 0 |
| loops/r23c_phase3_validation/window_02/ticks_live.csv | 4923 | Z-CRY/IDX | 0 | 0 |
| loops/r23c_phase3_validation/window_03/ticks.jsonl | 500 | Z-CRY/IDX | 0 | 0 |
| loops/r23c_phase3_validation/window_03/ticks_live.csv | 4881 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_01/ticks_live.csv | 5085 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_02/ticks_live.csv | 4901 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_03/ticks_live.csv | 4890 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_04/ticks_live.csv | 2000 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_05/ticks_live.csv | 4965 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_06/ticks_live.csv | 5099 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_07/ticks_live.csv | 4918 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_08/ticks_live.csv | 4891 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_09/ticks_live.csv | 4880 | Z-CRY/IDX | 0 | 0 |
| loops/r23d_validation/window_10/ticks_live.csv | 4907 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_01/ticks_live.csv | 4324 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_02/ticks_live.csv | 4494 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_03/ticks_live.csv | 4875 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_04/ticks_live.csv | 4920 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_05/ticks_live.csv | 3975 | Z-CRY/IDX | 0 | 0 |
| loops/r23e_adaptive_audit/window_06/ticks_live.csv | 4904 | Z-CRY/IDX | 0 | 0 |
| loops/r23f_validation/window_01/ticks_live.csv | 3588 | Z-CRY/IDX | 0 | 0 |
| loops/r23f_validation/window_02/ticks_live.csv | 4871 | Z-CRY/IDX | 0 | 0 |
| loops/r23f_validation/window_03/ticks_live.csv | 4930 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_01/ticks_live.csv | 3249 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_03/ticks_live.csv | 4927 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_04/ticks_live.csv | 4941 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_05/ticks_live.csv | 5076 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_06/ticks_live.csv | 5034 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_07/ticks_live.csv | 2709 | Z-CRY/IDX | 0 | 0 |
| loops/r23g_validation/window_08/ticks_live.csv | 2599 | Z-CRY/IDX | 0 | 0 |
| loops/r24_validation/window_02/ticks_live.csv | 473 | Z-CRY/IDX | 0 | 0 |
| loops/r25_validation/window_01/ticks_live.csv | 2892 | Z-CRY/IDX | 0 | 0 |
| loops/r25_validation/window_02/ticks_live.csv | 2894 | Z-CRY/IDX | 0 | 0 |
| loops/r25_validation/window_02_intento1_invalido/ticks_live.csv | 2838 | Z-CRY/IDX | 0 | 0 |
| loops/r25_validation/window_03/ticks_live.csv | 2879 | Z-CRY/IDX | 0 | 0 |

37 tick-bearing files contain 142,586 rows before independence correction. They contain only Z-CRY/IDX. Observed cross-asset timestamp collisions: 0; absence in this sample does not validate timestamp-only deduplication. The multi-asset regression is necessary.

Dataset acceptance is NOT implied by a matching schema. Current capture producer keyed accumulated_ticks by timestamp and pruned pre-window history; upstream losses cannot be recovered. Historical windows were already examined in R23–R25 and cannot become virgin holdouts.

## 2026-11-11 — supplemental text-log sweep

C0 additionally enumerated27 .txt files (357,002bytes), including heartbeat/status/build/replay outputs that do not use .log extension. Machine snapshot evidence/c0-text/inventory_v1.json records hashes/line counts; none has verified market provenance or research eligibility. Total enumerated source files972; original945-file snapshot remains unchanged. These text records do not add independent samples.

## 2026-11-12T01:44:08.775852+00:00 — authoritative provenance revision v2

Final adversarial metadata review found5standalone status snapshots classified too broadly by payload schema,including a test status payload and4R26snapshot files. Their exact paths/hashes/overrides are sealed in evidence/provenance_review_v2.json. Schema alone does not establish device-capture origin. Classification is now UNKNOWN/INSUFFICIENT_METADATA for those5;raw bytes unchanged. Prior manifests/catalog remain immutable as superseded history. Authoritative catalog:datasets/manifests/catalog.v2.json (with five per-dataset v2 manifests);remaining rows retain v1 metadata.

Current main945-object totals:395VALID_FOR_DIAGNOSTICS_ONLY,548INSUFFICIENT_METADATA,2FIXTURE_ONLY;classifications111BROKER_DEVICE_CAPTURE,237REPORT_DERIVED,47REPLAY_DERIVED,548UNKNOWN,2FIXTURE. Supplemental27text logs remain non-research. Research-approved datasets still0;statistical/verdict gates unchanged. Earlier400/543/116 totals describe superseded v1 only. This metadata correction introduces no source-code/strategy change and no new experiment.
