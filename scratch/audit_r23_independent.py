import os
import json
import csv
from collections import defaultdict

def audit():
    phases = [
        ("r23f", r"C:\Users\heidy\Tradedraw\loops\r23f_validation"),
        ("r23g", r"C:\Users\heidy\Tradedraw\loops\r23g_validation")
    ]

    all_windows = []

    for phase_name, base_dir in phases:
        for w in sorted(os.listdir(base_dir)):
            wp = os.path.join(base_dir, w)
            if not os.path.isdir(wp):
                continue

            w_info = {
                "phase": phase_name.upper(),
                "window": w,
                "path": wp,
                "trades": [],
                "meta": {},
                "live_report": "",
                "status_samples": []
            }

            meta_p = os.path.join(wp, "window_metadata.json")
            if os.path.exists(meta_p):
                with open(meta_p, "r", encoding="utf-8") as f:
                    w_info["meta"] = json.load(f)

            trades_p = os.path.join(wp, "trades.csv")
            if os.path.exists(trades_p):
                with open(trades_p, "r", encoding="utf-8") as f:
                    r = csv.DictReader(f)
                    for row in r:
                        row["phase"] = phase_name.upper()
                        row["window"] = w
                        w_info["trades"].append(row)

            live_rep_p = os.path.join(wp, "LIVE_REPORT.md")
            if os.path.exists(live_rep_p):
                with open(live_rep_p, "r", encoding="utf-8") as f:
                    w_info["live_report"] = f.read()

            status_p = os.path.join(wp, "status_samples.jsonl")
            if os.path.exists(status_p):
                with open(status_p, "r", encoding="utf-8") as f:
                    for line in f:
                        if line.strip():
                            try:
                                w_info["status_samples"].append(json.loads(line.strip()))
                            except Exception:
                                pass

            all_windows.append(w_info)

    print("=== SUMMARY OF ALL WINDOWS ===")
    total_trades_csv = 0
    total_w_rep = 0
    total_l_rep = 0

    for w in all_windows:
        # Extract W, L from live_report
        rep_w = None
        rep_l = None
        for line in w["live_report"].splitlines():
            if "Resoluciones: W=" in line:
                # e.g. - Resoluciones: W=2, L=2 (WR = 50.0%)
                parts = line.split("W=")[1].split(",")
                rep_w = int(parts[0].strip())
                rep_l = int(parts[1].split("L=")[1].split("(")[0].strip())
                break

        n_csv = len(w["trades"])
        total_trades_csv += n_csv
        if rep_w is not None:
            total_w_rep += rep_w
            total_l_rep += rep_l

        print(f"[{w['phase']} {w['window']}] trades.csv={n_csv} | meta_confirmed={w['meta'].get('trades_confirmed')} | Rep W={rep_w}, L={rep_l}, sum(W+L)={None if rep_w is None else rep_w+rep_l}")

    print(f"\nTOTAL TRADES IN CSV: {total_trades_csv}")
    print(f"TOTAL REPORTED W: {total_w_rep}, L: {total_l_rep}, sum: {total_w_rep + total_l_rep}")

if __name__ == "__main__":
    audit()
