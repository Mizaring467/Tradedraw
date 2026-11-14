# LEAKAGE_AUDIT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:24:08.313630+00:00 — audited findings

Adaptive snapshot leakage was possible: no timestamp cutoff, missing asset attribution and no time when outcome became known. New research guard rejects future/unknown state and tests deliberately contaminated snapshots. There is no claim that existing historical files are clean.

Temporal separation: all prior R23–R25 windows were already collected, inspected, used for parity debugging or strategy evolution before this freeze. They are not virgin holdout, even if evaluate.py renames their last30% holdout. No unseen historical segment is sealed. Current production source is later than these windows, creating development-selection/data-mining risk.

Dependence:46 exact-copy groups and119 overlapping CSV pairs; live/status projections, inferred settlements, counterfactuals and recomputed cubes share parents. No independent sample total may sum those artifacts. Source clock/feed delay remain unknown. The initial monitor used a timestamp-keyed map and pruned initial history. Its counterfactual nearest-tick resolver can cross expiry; do not call these verified outcomes.

Control model leakage: null_baseline.py shuffles nonoverlapping return blocks WITHOUT replacement, retains empirical drift and within-block dependence, and rounds output4 decimals. It is not a bootstrap-with-replacement distribution and does not prove a no-edge null. evaluate.py Wilson lower bound assumes Bernoulli-like independence, does not adjust multiplicity, and cannot create virgin OOS by splitting inspected data. No p-values or confidence levels for edge are approved.
