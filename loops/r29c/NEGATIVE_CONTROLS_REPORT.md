# NEGATIVE_CONTROLS_REPORT — 2026-11-12T01:09:51.187607+00:00

PENDING. Not executed; no evidence gate approved.

## 2026-11-12T01:27:40.975463+00:00 — gate outcome

C7 NOT_RUN_GATED, no comparisons/p-values. RandomStrategy,CoinFlip,AlwaysBuy,AlwaysSell and reversed-signal control are preregistered requirements, not production strategies. They must share dataset hashes, chronological exposure windows, entry admissibility, same-asset expiry/latency model, missingness rules, warmup and payout scenarios with TradeDraw. AlwaysBuy/AlwaysSell cannot cherry-pick different sessions. CoinFlip direction uses independent fixed seeds on matched opportunities. Random participation must define its opportunity clock and participation rate before outcomes. Reversal is diagnostic, not an automatically promoted strategy.

The existing null_baseline block permutation is synthetic and retains empirical drift; no generated row counts as observed market evidence. No claim that TradeDraw beats trivial controls is supported.
