#!/usr/bin/env python3
"""Cross-reference 31 BUY trades from CANONICAL_TRADES.csv with all validation artifacts:
- decisions.csv
- live_samples.jsonl / status_samples.jsonl (callPower, putPower)
- replay_journal.csv (pattern, mecha, dist_support, dist_resistance, score)
- ticks_live.csv
"""

import csv
import json
import os
import re

ROOT = "C:/Users/heidy/Tradedraw"
CANONICAL_CSV = os.path.join(ROOT, "loops/r23h_analysis/CANONICAL_TRADES.csv")

# Load CANONICAL_TRADES
buy_trades = []
with open(CANONICAL_CSV, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        if row["action"] == "BUY":
            buy_trades.append(row)

print(f"Total BUY trades in canonical: {len(buy_trades)}")

# Mapping window name to folder
# window is like r23f_w01 -> loops/r23f_validation/window_01
def get_window_dir(win_str):
    parts = win_str.split("_")
    phase = parts[0] # r23f or r23g
    w_num = parts[1].replace("w", "") # 01
    return os.path.join(ROOT, f"loops/{phase}_validation/window_{w_num}")

results = []

for t in buy_trades:
    trade_id = t["trade_id"]
    dec_id = t["decision_id"]
    win = t["window"]
    ts = int(t["timestamp"])
    res = t["result"]
    trend_can = t["trend"]
    score_can = t["score"]
    wdir = get_window_dir(win)

    # 1. Look up in decisions.csv
    dec_file = os.path.join(wdir, "decisions.csv")
    dec_row = None
    if os.path.exists(dec_file):
        with open(dec_file, "r", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                if dec_id and r.get("decision_id") == dec_id:
                    dec_row = r
                    break
                elif trade_id and r.get("trade_id") == trade_id:
                    dec_row = r
                    break

    # 2. Look up in replay_journal.csv
    replay_file = os.path.join(wdir, "replay_journal.csv")
    replay_row = None
    if os.path.exists(replay_file):
        with open(replay_file, "r", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                if dec_id and r.get("decision_id") == dec_id:
                    replay_row = r
                    break
                # Or closest timestamp within 5s
                try:
                    r_ts = int(r.get("timestamp", 0))
                    if abs(r_ts - ts) <= 5000 and r.get("action") == "BUY":
                        replay_row = r
                        break
                except:
                    pass

    # 3. Look up in live_samples.jsonl or status_samples.jsonl
    # We want sample closest to trade timestamp (before or at trade)
    samples_file = os.path.join(wdir, "live_samples.jsonl")
    if not os.path.exists(samples_file):
        samples_file = os.path.join(wdir, "status_samples.jsonl")

    closest_sample = None
    min_dt = float("inf")
    if os.path.exists(samples_file):
        with open(samples_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    s = json.loads(line)
                    s_ts = s.get("timestamp_ms", 0)
                    dt = abs(s_ts - ts)
                    if dt < min_dt:
                        min_dt = dt
                        closest_sample = s
                except:
                    pass

    call_power = None
    put_power = None
    sample_trend = None
    if closest_sample:
        analysis = closest_sample.get("status", {}).get("analysis", {})
        if not analysis:
            analysis = closest_sample.get("analysis", {})
        call_power = analysis.get("callPower")
        put_power = analysis.get("putPower")
        sample_trend = analysis.get("trend")

    # Parse replay_row reason
    pattern = None
    mecha = None
    confluence = None
    if replay_row:
        reason = replay_row.get("reason", "")
        # e.g. "🎯 MT_REJECTION: Rechazo en Soporte (Mecha: 69% | Dist S: 1% | ⏱ 1s) -> CALL [Confluencia: 75pts]"
        m_pat = re.search(r"🎯\s*(\w+):", reason)
        if m_pat:
            pattern = m_pat.group(1)
        m_mech = re.search(r"Mecha:\s*(\d+)%", reason)
        if m_mech:
            mecha = int(m_mech.group(1))
        m_conf = re.search(r"Confluencia:\s*(\d+)pts", reason)
        if m_conf:
            confluence = int(m_conf.group(1))

    # Fallback for confluence from canonical score or decisions score
    if confluence is None and score_can:
        try:
            confluence = int(float(score_can))
        except:
            pass
    if confluence is None and dec_row and dec_row.get("score"):
        try:
            confluence = int(float(dec_row["score"]))
        except:
            pass

    # Fallback for trend
    final_trend = trend_can or sample_trend
    if dec_row and dec_row.get("trend"):
        final_trend = dec_row["trend"]
    if replay_row and replay_row.get("trend"):
        final_trend = replay_row["trend"]

    results.append({
        "trade_id": trade_id,
        "window": win,
        "ts": ts,
        "result": res,
        "trend": final_trend,
        "confluence": confluence,
        "pattern": pattern,
        "mecha": mecha,
        "call_power": call_power,
        "put_power": put_power,
        "dt_sample_sec": round(min_dt / 1000.0, 1) if min_dt != float("inf") else None,
        "replay_found": replay_row is not None,
        "dec_found": dec_row is not None,
    })

# Print table
print("\n--- DETAILED EXTRACTED FEATURES FOR 31 BUY TRADES ---")
print(f"{'#':<2} | {'trade_id'[:8]:<8} | {'win':<8} | {'res':<4} | {'trend':<10} | {'conf':<5} | {'pat':<15} | {'mecha':<5} | {'callP':<5} | {'putP':<5}")
print("-" * 75)
for i, r in enumerate(results, 1):
    print(f"{i:<2} | {r['trade_id'][:8]:<8} | {r['window']:<8} | {r['result']:<4} | {str(r['trend']):<10} | {str(r['confluence']):<5} | {str(r['pattern']):<15} | {str(r['mecha']):<5} | {str(r['call_power']):<5} | {str(r['put_power']):<5}")

# Save json for detailed processing
with open(os.path.join(ROOT, "scratch/buy_features.json"), "w", encoding="utf-8") as f:
    json.dump(results, f, indent=2)

print("\nSaved scratch/buy_features.json")
