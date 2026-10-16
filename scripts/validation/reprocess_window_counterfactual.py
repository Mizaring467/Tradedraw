#!/usr/bin/env python3
"""
scripts/validation/reprocess_window_counterfactual.py

Reconstrucción post-hoc de `counterfactual_adaptive_block.csv` para una ventana ya ejecutada.
Aplica exactamente la misma lógica de deduplicación, resolución contra ticks y cálculo de EV
que `live_monitor_r23_autonomous.py`.

Uso:
    python scripts/validation/reprocess_window_counterfactual.py --window-dir loops/r23e_adaptive_audit/window_01
"""

import argparse
import csv
import json
import os
import sys
from typing import Dict, Any, List

sys.path.insert(0, os.path.dirname(__file__))
import live_monitor_r23_autonomous as mon


def reprocess_window(window_dir: str):
    print(f"[*] Reprocesando ventana en: {window_dir}")
    ticks_csv = os.path.join(window_dir, "ticks_live.csv")
    if not os.path.exists(ticks_csv):
        ticks_csv = os.path.join(window_dir, "ticks.csv")
    if not os.path.exists(ticks_csv):
        print(f"[!] Error: No se encontró ticks_live.csv ni ticks.csv en {window_dir}")
        return

    # 1. Cargar ticks
    accumulated_ticks: Dict[int, str] = {}
    with open(ticks_csv, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        for row in reader:
            if len(row) >= 3:
                try:
                    ts = int(row[0])
                    asset = row[1]
                    price = float(row[2])
                    accumulated_ticks[ts] = f"{asset},{price}"
                except ValueError:
                    continue
    print(f"[+] Ticks cargados: {len(accumulated_ticks)}")

    # 2. Cargar status samples para asociar lastSignalAction por timestamp
    status_samples_path = os.path.join(window_dir, "status_samples.jsonl")
    signal_actions: Dict[int, str] = {}
    if os.path.exists(status_samples_path):
        with open(status_samples_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    d = json.loads(line)
                    engine = d.get("engine", {})
                    sig_ts = engine.get("lastSignalTimestampMs")
                    sig_act = engine.get("lastSignalAction")
                    if sig_ts and sig_act in ("BUY", "SELL"):
                        signal_actions[int(sig_ts)] = sig_act
                except Exception:
                    continue
    print(f"[+] Señales únicas de engine en status_samples: {len(signal_actions)}")

    # 3. Cargar eventos VETO
    events_path = os.path.join(window_dir, "events.jsonl")
    decisions_csv = os.path.join(window_dir, "decisions.csv")
    
    events_to_process = []
    if os.path.exists(events_path):
        with open(events_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                    if ev.get("kind") == "VETO" and "ADAPTIVE_BLOCK" in (ev.get("detail") or "").upper():
                        events_to_process.append(ev)
                except Exception:
                    continue
    print(f"[+] Eventos ADAPTIVE_BLOCK encontrados: {len(events_to_process)}")

    # Ordenar por atMs ascendente
    events_to_process.sort(key=lambda x: int(x.get("atMs", 0)))

    # 4. Deduplicación y resolución contrafactual
    cf_open_until: Dict[str, int] = {}
    cf_last_candle: Dict[str, int] = {}
    cf_seen_did = set()
    accepted_cf: List[Dict[str, Any]] = []

    for ev in events_to_process:
        did = ev.get("decisionId", "")
        if did in cf_seen_did:
            continue
        cf_seen_did.add(did)

        detail = ev.get("detail", "")
        at_ms = int(ev.get("atMs", 0))

        # Encontrar la acción más cercana de signal_actions (dentro de 10s)
        action = mon.extract_action(detail)
        if action not in ("BUY", "SELL"):
            # Buscar en signal_actions el sig_ts más cercano <= at_ms o dentro de 1000ms
            best_sig_ts = None
            min_diff = 10000
            for sig_ts, sig_act in signal_actions.items():
                diff = abs(sig_ts - at_ms)
                if diff < min_diff:
                    min_diff = diff
                    best_sig_ts = sig_ts
            if best_sig_ts is not None and min_diff <= 10000:
                action = signal_actions[best_sig_ts]

        if not mon.dedupe_counterfactual(action, at_ms, cf_open_until, cf_last_candle):
            continue

        campos = mon.extract_adaptive_block_fields(detail)
        cf_open_until[action] = at_ms + mon.COUNTERFACTUAL_OCCUPANCY_MS
        cf_last_candle[action] = at_ms // mon.COUNTERFACTUAL_HORIZON_MS

        entry_price = mon.find_nearest_tick_price(accumulated_ticks, at_ms)
        expiry_ts = at_ms + mon.COUNTERFACTUAL_HORIZON_MS
        exit_price = mon.find_nearest_tick_price(accumulated_ticks, expiry_ts)

        result = "COUNTERFACTUAL_VOID"
        ev_theoretical = 0.0
        if entry_price is not None and exit_price is not None:
            result = mon.resolve_counterfactual(action, entry_price, exit_price)
            ev_theoretical = mon.counterfactual_ev(result, mon.COUNTERFACTUAL_PAYOUT)

        accepted_cf.append({
            "counterfactual_id": mon.counterfactual_id(did, at_ms),
            "decision_id": did,
            "asset": "Z-CRY/IDX",
            "action": action,
            "decision_ts": at_ms,
            "entry_price": f"{entry_price:.8f}" if entry_price is not None else "",
            "expiry_ts": expiry_ts,
            "exit_price": f"{exit_price:.8f}" if exit_price is not None else "",
            "result": result,
            "score": str(campos["score"]) if campos["score"] is not None else "",
            "similarity": str(campos["similarity"]) if campos["similarity"] is not None else "",
            "market_regime": "TRENDING",
            "trend": "UPTREND" if action == "BUY" else "DOWNTREND",
            "payout": mon.COUNTERFACTUAL_PAYOUT,
            "theoretical_ev": ev_theoretical,
        })

    # 5. Volcar a CSV
    out_csv = os.path.join(window_dir, "counterfactual_adaptive_block.csv")
    with open(out_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=mon.COUNTERFACTUAL_FIELDS)
        writer.writeheader()
        writer.writerows(accepted_cf)

    print(f"[+] Guardado: {out_csv} ({len(accepted_cf)} contrafactuales deduplicados)")

    # 6. Estadísticas
    w_count = sum(1 for c in accepted_cf if c["result"] == "COUNTERFACTUAL_WIN")
    l_count = sum(1 for c in accepted_cf if c["result"] == "COUNTERFACTUAL_LOSS")
    v_count = sum(1 for c in accepted_cf if c["result"] == "COUNTERFACTUAL_VOID")
    total_valid = w_count + l_count
    wr = (w_count / total_valid * 100.0) if total_valid > 0 else 0.0
    ev_total = sum(c["theoretical_ev"] for c in accepted_cf)

    print("\n" + "=" * 50)
    print("RESUMEN DE REPROCESAMIENTO:")
    print(f"Total Contrafactuales: {len(accepted_cf)}")
    print(f"Wins: {w_count} | Losses: {l_count} | Voids: {v_count}")
    print(f"Win Rate Contrafactual: {wr:.2f}% (Breakeven ref: 54.95%)")
    print(f"EV Teórico Acumulado: {ev_total:+,.2f} COP")
    print("=" * 50)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--window-dir", required=True, help="Directorio de la ventana")
    args = parser.parse_args()
    reprocess_window(args.window_dir)
