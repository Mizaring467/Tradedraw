#!/usr/bin/env python3
"""
scripts/validation/live_monitor_r22.py
Ronda 22: Monitor y auditoría en vivo en SHADOW MODE.

Supervisa continuamente /status, /events, /nodes y /mode_log durante la sesión R22,
valida la inmutabilidad de balance, conteo de taps=0, propagación de decision_id,
transiciones de health y exporta las decisiones recolectadas para el comparador Live <-> Replay.
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.request
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional


def fetch_json(url: str, timeout: float = 3.0) -> Optional[Dict[str, Any]]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "R22-LiveMonitor/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = resp.read().decode("utf-8")
            # Tolerar comentarios // dentro del JSON (como los de TradeDrawHttpBridge.kt:594)
            clean_data = "\n".join(
                line for line in data.splitlines()
                if not line.strip().startswith("//")
            )
            return json.loads(clean_data)
    except Exception as e:
        return None


def main():
    parser = argparse.ArgumentParser(description="Monitor Live R22 SHADOW MODE")
    parser.add_argument("--duration-min", type=float, default=15.0, help="Duración del monitoreo en minutos (default: 15.0)")
    parser.add_argument("--interval-sec", type=float, default=10.0, help="Intervalo de sondeo en segundos (default: 10.0)")
    parser.add_argument("--out-jsonl", default="loops/r22_live_samples.jsonl", help="Ruta para volcar las muestras JSONL")
    parser.add_argument("--out-decisions-csv", default="data/fixtures/r22_live_decisions.csv", help="Ruta para guardar decisiones")
    args = parser.parse_args()

    os.makedirs(os.path.dirname(os.path.abspath(args.out_jsonl)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.out_decisions_csv)), exist_ok=True)

    base_url = "http://127.0.0.1:8080"
    start_time = time.time()
    end_time = start_time + (args.duration_min * 60.0)

    print("=" * 70)
    print("INICIANDO MONITOR DE AUDITORÍA LIVE R22 (SHADOW MODE)")
    print("=" * 70)
    print(f"Duración configurada: {args.duration_min} minutos")
    print(f"Intervalo de sondeo:  {args.interval_sec} segundos")
    print(f"Destino muestras:     {args.out_jsonl}")
    print(f"Destino decisiones:   {args.out_decisions_csv}")
    print("=" * 70)

    sample_count = 0
    bridge_failures = 0
    initial_balance: Optional[float] = None
    cur_bal: float = 0.0
    balance_discrepancies = 0
    taps_detected = 0
    observed_decisions: Dict[str, Dict[str, Any]] = {}
    health_states_seen = set()

    with open(args.out_jsonl, "a", encoding="utf-8") as jsonl_f:
        while time.time() < end_time:
            now_ts = time.time()
            elapsed_sec = int(now_ts - start_time)
            rem_sec = int(end_time - now_ts)

            status = fetch_json(f"{base_url}/status")
            events_data = fetch_json(f"{base_url}/events?max=100")
            nodes = fetch_json(f"{base_url}/nodes")

            sample_record: Dict[str, Any] = {
                "sample_idx": sample_count + 1,
                "timestamp_ms": int(now_ts * 1000),
                "elapsed_sec": elapsed_sec,
                "status_ok": status is not None,
                "status": status,
                "events_count": len(events_data.get("events", [])) if events_data else 0,
                "nodes": nodes,
            }
            jsonl_f.write(json.dumps(sample_record, ensure_ascii=False) + "\n")
            jsonl_f.flush()

            if status is None:
                bridge_failures += 1
                print(f"[{elapsed_sec:4d}s] ALERTA: Fallo al consultar /status (total fallos={bridge_failures})")
            else:
                sample_count += 1
                cur_bal = status.get("balance", 0.0)
                if initial_balance is None and cur_bal > 0.0:
                    initial_balance = cur_bal

                if initial_balance is not None and abs(cur_bal - initial_balance) > 0.01:
                    balance_discrepancies += 1
                    print(f"[{elapsed_sec:4d}s] ⚠️ CRÍTICO: Variación de balance detectada: {cur_bal} != {initial_balance}")

                risk = status.get("risk", {})
                cur_taps = risk.get("tapsDespachados", 0)
                if cur_taps > 0:
                    taps_detected = max(taps_detected, cur_taps)
                    print(f"[{elapsed_sec:4d}s] ⚠️ CRÍTICO: Taps despachados detectados: {cur_taps}")

                health = status.get("health", {})
                h_state = health.get("state", "UNKNOWN")
                health_states_seen.add(h_state)

                mode = status.get("mode", "UNKNOWN")
                feed = status.get("feed", {})
                asset = feed.get("activeAsset", "")
                ticks = feed.get("acceptedTicks", 0)
                is_fresh = feed.get("isFresh", False)
                age_ms = feed.get("lastTickAgeMs", -1)
                shadow_orders = risk.get("shadowOrders", 0)
                veto_total = risk.get("vetoTotal", 0)
                last_veto = risk.get("ultimoVeto", "")

                # Extraer decisiones desde /events
                if events_data and "events" in events_data:
                    for ev in events_data["events"]:
                        did = ev.get("decisionId")
                        kind = ev.get("kind")
                        if did and did not in observed_decisions:
                            detail = ev.get("detail", "")
                            at_ms = ev.get("atMs", int(now_ts * 1000))

                            # Determinar acción y dirección
                            action = "NONE"
                            if "BUY" in detail.upper():
                                action = "BUY"
                            elif "SELL" in detail.upper() or "PUT" in detail.upper():
                                action = "SELL"

                            veto_kind = "NONE"
                            if kind == "VETO":
                                if "DESAJUSTE" in detail.upper():
                                    veto_kind = "DESAJUSTE"
                                elif "V2" in detail.upper():
                                    veto_kind = "V2_RANGE"
                                elif "FEED_RANCIO" in detail.upper():
                                    veto_kind = "FEED_RANCIO"
                                else:
                                    veto_kind = "VETO"

                            observed_decisions[did] = {
                                "decision_id": did,
                                "trade_id": ev.get("tradeId", ""),
                                "timestamp": at_ms,
                                "asset": asset or "Z-CRY/IDX",
                                "price_at_signal": status.get("analysis", {}).get("currentPriceY", 0.0),
                                "candle_second": (at_ms // 1000) % 60,
                                "market_regime": "CHOP" if "CHOP" in detail.upper() or "V2" in detail.upper() else "TRENDING",
                                "trend": status.get("analysis", {}).get("trend", "SIDEWAYS"),
                                "dist_support": 0.0,
                                "dist_resistance": 0.0,
                                "confidence": 85.0,
                                "strategy": status.get("strategy", "AUTO_ADAPTIVE"),
                                "action": action,
                                "veto": veto_kind,
                                "decision": "VETOED" if kind == "VETO" else ("EXECUTED" if kind == "SHADOW_DECISION" else "SUPPRESSED"),
                                "reason": detail,
                                "source": "LIVE_SHADOW",
                            }
                            print(f"[{elapsed_sec:4d}s] 🎯 Decisión capturada: ID={did[:8]}... kind={kind} detail={detail[:60]}")

                # Línea de estado compacta cada 30s o si hubo decisión nueva
                if sample_count % 3 == 0:
                    print(
                        f"[{elapsed_sec:4d}s | {rem_sec:4d}s rem] "
                        f"modo={mode} health={h_state} activo={asset} ticks={ticks} "
                        f"age={age_ms}ms fresh={is_fresh} saldo={cur_bal:,.2f} taps={cur_taps} "
                        f"shadowOrders={shadow_orders} vetos={veto_total} (decisiones={len(observed_decisions)})"
                    )

            time.sleep(args.interval_sec)

    print("=" * 70)
    print("MONITOREO FINALIZADO CON ÉXITO")
    print("=" * 70)
    print(f"Muestras recolectadas:     {sample_count}")
    print(f"Fallos de bridge:          {bridge_failures}")
    print(f"Saldo inicial:             {initial_balance:,.2f} COP")
    print(f"Saldo final:               {cur_bal:,.2f} COP")
    print(f"Discrepancias de balance:  {balance_discrepancies}")
    print(f"Taps totales despachados:  {taps_detected}")
    print(f"Estados Health observados: {list(health_states_seen)}")
    print(f"Decisiones capturadas:     {len(observed_decisions)}")

    # Guardar decisiones a CSV para comparador Live <-> Replay
    if observed_decisions:
        header = [
            "decision_id", "trade_id", "timestamp", "asset", "price_at_signal",
            "candle_second", "market_regime", "trend", "dist_support", "dist_resistance",
            "confidence", "strategy", "action", "veto", "decision", "reason", "source"
        ]
        with open(args.out_decisions_csv, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(header)
            for d in observed_decisions.values():
                w.writerow([
                    d["decision_id"], d["trade_id"], d["timestamp"], d["asset"], d["price_at_signal"],
                    d["candle_second"], d["market_regime"], d["trend"], d["dist_support"], d["dist_resistance"],
                    d["confidence"], d["strategy"], d["action"], d["veto"], d["decision"], d["reason"], d["source"]
                ])
        print(f"Decisiones guardadas en CSV: {args.out_decisions_csv}")
    else:
        print("Aviso: No se generaron decisiones con decision_id durante la ventana.")


if __name__ == "__main__":
    main()
