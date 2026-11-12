# BOOTSTRAP_REPORT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:27:40.975463+00:00 — gate outcome

C12 NOT_RUN_GATED. No accepted trade sequence, so WR,EV,profit,max drawdown and loss-streak P5/P25/P50/P75/P95 are UNESTIMATED. Bootstrapping synthetic/duplicate samples would not repair provenance or replay invalidity.

Prospective design: at least10000 replicates, seed290012; resample contiguous daily/session blocks jointly across strategies/assets where simultaneous exposure creates dependence. Prespecify block rule before outcomes; report autocorrelation and sensitivity to adjacent block lengths, without picking the most favorable. If too few independent blocks, report insufficient uncertainty estimation. Include NO_DATA rate and candidate-outcome missingness bounds rather than treating missing results as losses/ties. Report percentile distribution of all required metrics, not a point estimate.
