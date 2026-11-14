# FAST_DEMO MODE SPECIFICATION & DATASET GOVERNANCE

## 1. Executive Summary & Purpose
To separate infrastructure engineering verification from quantitative strategy validation, TradeDraw defines two strictly isolated operating profiles:

1. **`VALIDATION_REAL`**: Dedicated strictly to evaluating strategy edge (WIN/LOSS, EV, WR, drawdown). Every filter, cooldown, and timing window is frozen. Its data is permanent scientific evidence.
2. **`FAST_DEMO`**: An exclusive engineering test harness. Its objective is to rapidly exercise and stress-test the physical execution pipeline:
   - Screen accessibility and UI taps
   - Real broker WebSocket subscriptions and price ticks
   - Order dispatch and settlement lifecycle
   - Reconnection routines and watchdog recovery
   - Journaling idempotency and disk persistence

Results generated in `FAST_DEMO` mode **MUST NEVER** be cited or used as evidence of trading profitability or strategy performance.

---

## 2. Hard Governance & Fail-Closed Safeguards

| Safeguard | FAST_DEMO Rule | Implementation / Enforcement |
| :--- | :--- | :--- |
| **Real Money Lock** | **IMPOSSIBLE IN REAL** | If account is not explicitly `DEMO_CONFIRMED`, `FAST_DEMO` disarms immediately to `DISABLED`. Any attempt to start `FAST_DEMO` on real funds throws `SecurityException`. |
| **Stake Limit** | Fixed Minimal Stake ($1 DEMO) | Hard-capped in `RiskManager`; cannot be overridden dynamically. |
| **Pending Trades** | `MAX_PENDING_TRADES = 1` | `SingleFlightOrderGateway` strictly forbids concurrent or overlapping broker taps. |
| **Dataset Isolation** | Writes exclusively to `loops/fast_demo/` | Output files (`trade_journal_fast_demo.csv`, `fast_demo_events.jsonl`) are strictly partitioned. |
| **Audit Tagging** | Mandatory `validation_mode` attribute | Every row and event log explicitly contains `"validation_mode": "FAST_DEMO"`. |

---

## 3. Engineering Parameter Adjustments in FAST_DEMO

In `VALIDATION_REAL`, conservative cooldowns (60s–180s) and strict candle second timing (`:01–:05`) slow execution to ~13–22 trades per hour. To accelerate hardware verification, `FAST_DEMO` modifies only non-strategic technical delays:

| Parameter | `VALIDATION_REAL` Value | `FAST_DEMO` Value | Engineering Rationale |
| :--- | :--- | :--- | :--- |
| **Post-Loss Cooldown** | 60s / 180s | **15 seconds** | Allows rapid cycling of order dispatch without waiting 3 minutes. |
| **Normal Post-Trade Cooldown** | 10 seconds | **5 seconds** | Minimizes idle waiting between resolved positions. |
| **Post-Resolution Spacing** | 35,000 ms | **8,000 ms** | Ensures broker UI has cleared previous order modal before next tap. |
| **Entry Window Restriction** | `:01–:05` (8.3% of minute) | **Relaxed / Every Candle Tick** | Enables high-throughput dispatch verification (30–60 taps/hour). |
| **Adaptive ML Blocking** | Active (threshold 0.99) | **Bypassed or Log-Only** | Prevents ML model from vetoing valid test taps. |

---

## 4. Verification Workflow & File Topology

```text
[TradeDraw Engine]
       │
       ├── Mode == VALIDATION_REAL ──> writes to: loops/r31_validation/trade_journal.csv
       │                               (validation_mode = "VALIDATION_REAL")
       │
       └── Mode == FAST_DEMO       ──> writes to: loops/fast_demo/trade_journal_fast_demo.csv
                                       (validation_mode = "FAST_DEMO")
```

### Analysis Pipeline Isolation:
All quantitative evaluation scripts (`analyze_journal.py`, `edge_analysis.py`, `run_validation_pipeline.py`) enforce a mandatory filter:
```python
if row.get("validation_mode") != "VALIDATION_REAL":
    continue  # Exclude engineering FAST_DEMO rows from statistical edge reports
```

This guarantees complete zero-contamination between infrastructure stress-testing and statistical edge validation.
