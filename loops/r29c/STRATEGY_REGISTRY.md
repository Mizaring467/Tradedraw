# STRATEGY_REGISTRY — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:24:08.313630+00:00 — audited findings

Authoritative immutable machine registry: evidence/strategy_registry_v2.json. Fourteen public modes, all status FROZEN, version r29c-frozen-4fdb646, base4fdb646. Full-source hash map covers44 Kotlin production files, including actual strategy engine, policy, market/risk/execution and TradingEngine. parameter_hash=970fda5f58ad2814a82541272b0f5e139f03d3727fdea62e25cbafc99f3baf2d. Hash uses canonical JSON of path→full-source-SHA; this conservative superset detects any parameter/code change, not only a regex of numeric constants. File bytes compared against current workspace and base git objects. No changes authorized to these bytes.

Version1 was an incomplete13-mode extraction that missed final enum member COMBINED; retained as failed audit history, superseded by version2 before any experiment. Each public mode composes internal patterns; internal MT_/QUANT_ labels are not independent strategies or additional trades. AI_REMOTE's source is frozen but remote model/provider state is unpinned, so it is not eligible for reproducible local evidence. Future behavior changes require new version/hash and new preregistration.
