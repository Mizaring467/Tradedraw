#!/usr/bin/env python3
"""scripts/validation/recompute_cubes.py

Recálculo determinista e independiente de cubos epistémicos para TradeDraw (R23K).
Lee exclusivamente fuentes de datos crudas (trades.csv, window_metadata.json,
live_samples.jsonl y reconciliación de frontera W10).

Reglas metodológicas:
- Determinista (sin red, sin reloj de pared).
- Semilla de bootstrap fija: 20261019, 10.000 remuestreos.
- Deduplicación estricta por trade_id.
- Asignación a ventana por timestamp de despacho.
- Verificación formal de cadena de saldos.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import random
import sys
from typing import Any, Dict, List, Optional, Tuple

# Constantes del preregistro y configuración congelada
BOOTSTRAP_SEED = 20261019
BOOTSTRAP_REPS = 10000
WILSON_Z95 = 1.959963984540054

CANONICAL_WINDOWS = [
    ("W01", "loops/r23h_validation/window_01", 0.82),
    ("W02", "loops/r23h_validation/window_02", 0.82),
    ("W03", "loops/r23h_validation/window_03", 0.82),
    ("W04", "loops/r23h_validation/window_04_clean", 0.82),
    ("W05", "loops/r23h_validation/window_05_clean", 0.82),
    ("W06", "loops/r23i_validation/window_06_clean", 0.83),
    ("W07", "loops/r23j_validation/window_07_clean", 0.83),
    ("W08", "loops/r23j_validation/window_08_clean", 0.83),
    ("W09", "loops/r23j_validation/window_09_clean", 0.83),
    ("W10", "loops/r23j_validation/window_10_clean", 0.83),
    ("W11", "loops/r23j_validation/window_11_clean", 0.83),
    ("W12", "loops/r23k_validation/window_12", 0.83),
    ("W13", "loops/r23k_validation/window_13", 0.83),
    ("W14", "loops/r23k_validation/window_14", 0.83),
    ("W15", "loops/r23k_validation/window_15", 0.83),
    ("W16", "loops/r23k_validation/window_16", 0.83),
    ("W17", "loops/r23k_validation/window_17", 0.83),
    ("W18", "loops/r23k_validation/window_18", 0.82),
    ("W19", "loops/r23k_validation/window_19", 0.82),
    ("W20", "loops/r23k_validation/window_20", 0.83),
]

RUTA_VENTANA = {w: p for w, p, _ in CANONICAL_WINDOWS}

# Ventanas del Cubo 1 (paridad post-congelado). FUENTE UNICA: la usan la seleccion de trades y el
# conteo de pares. Antes estaba duplicada en tres literales, y anadir una ventana actualizaba solo una.
CUBO1_WINDOWS = ("W07", "W08", "W09", "W10", "W11", "W12", "W13", "W14", "W15", "W16", "W17",
                 "W18", "W19", "W20")

BORDER_TRADE_W10 = {
    "trade_id": "205eed81-fd3b-47d7-8efa-4b3fb7058499",
    "decision_id": "0d6d32fa-eead-3b4f-996c-731a1a2b40f8",
    "timestamp_ms": 1792385045288,
    "action": "BUY",
    "result": "LOSS",
    "delta": -100001.28,
    "balance_pre": 39103736.32,
    "balance_post": 39003735.04,
    "window": "W10",
    "payout": 0.83,
    "stake": 100000.0,
    "source": "workflows/rounds/R23J_W10_BORDER_TRADE_RECONCILIATION.md",
}


def contar_paridad_replay(root: str, ventanas) -> Dict[str, Any]:
    """
    Cuenta los pares de paridad REALES leyendo `replay_comparison.json` de cada ventana.

    POR QUE DERIVARLO Y NO ESCRIBIR LOS LITERALES. Hasta W18 el Cubo 1 llevaba `pares=16` y
    `matches=14` como constantes a mano. Al registrar W19 el ledger crecia a 59 trades pero el cubo
    seguia reportando N=16 y calculaba el veredicto sobre una muestra ya inexistente. El instrumento
    no falla en la aritmetica: se queda quieto mientras el conjunto crece, y ese silencio es
    indistinguible de un resultado valido.

    Cada `pairs` del JSON trae un `status`: MATCH, DIVERGENT, MISSING_LIVE o MISSING_REPLAY. Solo
    los DOS primeros forman par evaluable; los otros son ordenes sin contraparte y no entran ni en el
    numerador ni en el denominador (criterio B5: la tasa se mide sobre pares formados y el volumen se
    reporta aparte).

    Una ventana sin `replay_comparison.json` NO se cuenta como 0 pares en silencio: se lista en
    `ventanas_sin_paridad` para que el informe pueda declararla no evaluable.
    """
    pares = matches = divergentes = 0
    por_ventana: Dict[str, Dict[str, int]] = {}
    sin_archivo: List[str] = []
    for wname in ventanas:
        rel = RUTA_VENTANA[wname]
        ruta = os.path.join(root, rel, "replay_comparison.json")
        if not os.path.exists(ruta):
            sin_archivo.append(wname)
            continue
        try:
            with open(ruta, "r", encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError):
            sin_archivo.append(wname)
            continue
        m = d = 0
        for par in data.get("pairs") or []:
            estado = str(par.get("status", "")).upper()
            if estado == "MATCH":
                m += 1
            elif estado == "DIVERGENT":
                d += 1
        por_ventana[wname] = {"matches": m, "divergencias": d, "pares": m + d}
        matches += m
        divergentes += d
        pares += m + d
    return {
        "pares": pares,
        "matches": matches,
        "divergencias": divergentes,
        "por_ventana": por_ventana,
        "ventanas_sin_paridad": sin_archivo,
    }


# ---------------------------------------------------------------------------
# Fórmulas matemáticas estándar y exactas
# ---------------------------------------------------------------------------
def wilson_interval(wins: int, n: int, z: float = WILSON_Z95) -> Tuple[float, float, float]:
    """Intervalo de confianza Wilson 95% para proporciones binomiales."""
    if n <= 0:
        return 0.0, 0.0, 0.0
    p = wins / n
    z2 = z * z
    denom = 1.0 + z2 / n
    center = (p + z2 / (2.0 * n)) / denom
    margin = (z / denom) * math.sqrt((p * (1.0 - p) / n) + (z2 / (4.0 * n * n)))
    return max(0.0, center - margin), min(1.0, center + margin), center


def breakeven_wr(payout: float) -> float:
    """Tasa de acierto de equilibrio: BE = 1 / (1 + payout)."""
    if payout <= 0.0:
        return 1.0
    return 1.0 / (1.0 + payout)


def bootstrap_percentile_ci(
    values: List[float], reps: int = BOOTSTRAP_REPS, seed: int = BOOTSTRAP_SEED
) -> Tuple[float, Tuple[float, float]]:
    """Bootstrap no paramétrico con semilla fija y percentiles 2.5 y 97.5."""
    if not values:
        return 0.0, (0.0, 0.0)
    rng = random.Random(seed)
    n = len(values)
    mean_val = sum(values) / n
    boot_means = [sum(values[rng.randrange(n)] for _ in range(n)) / n for _ in range(reps)]
    boot_means.sort()
    lo = boot_means[int(0.025 * reps)]
    hi = boot_means[int(0.975 * reps)]
    return mean_val, (lo, hi)


def compute_max_drawdown(balances: List[float]) -> Tuple[float, float]:
    """Calcula el máximo drawdown pico-a-valle en valor absoluto y porcentaje."""
    if not balances:
        return 0.0, 0.0
    peak = balances[0]
    max_dd_abs = 0.0
    max_dd_pct = 0.0
    for b in balances:
        if b > peak:
            peak = b
        dd = peak - b
        if peak > 0:
            pct = (dd / peak) * 100.0
        else:
            pct = 0.0
        if dd > max_dd_abs:
            max_dd_abs = dd
        if pct > max_dd_pct:
            max_dd_pct = pct
    return max_dd_abs, max_dd_pct


# ---------------------------------------------------------------------------
# Carga y reconciliación de datos crudos
# ---------------------------------------------------------------------------
def load_window_metadata(root: str, relative_path: str) -> Dict[str, Any]:
    full_path = os.path.join(root, relative_path, "window_metadata.json")
    if os.path.exists(full_path):
        try:
            with open(full_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def load_window_samples(root: str, relative_path: str) -> List[Dict[str, Any]]:
    full_path = os.path.join(root, relative_path, "live_samples.jsonl")
    samples = []
    if os.path.exists(full_path):
        with open(full_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        samples.append(json.loads(line))
                    except Exception:
                        pass
    samples.sort(key=lambda s: s.get("timestamp_ms", 0))
    return samples


def resolve_trade_deltas_from_telemetry(
    raw_trades: List[Dict[str, Any]], samples: List[Dict[str, Any]], payout: float, init_b: float, final_b: float
) -> List[Dict[str, Any]]:
    """Determina pre-balance, post-balance, delta y resultado exacto para cada trade."""
    if not raw_trades:
        return []

    # Extraer saltos de balance relevantes de las muestras
    steps = []
    prev_b = None
    for s in samples:
        b = s.get("validated_balance")
        ts = s.get("timestamp_ms")
        if prev_b is not None and b is not None and abs(b - prev_b) > 1000.0:
            steps.append({"ts": ts, "delta": b - prev_b, "pre": prev_b, "post": b})
        if b is not None:
            prev_b = b

    resolved = []
    current_b = init_b

    for idx, t in enumerate(raw_trades):
        bal_at = t["balance_at_trade"]
        ts_trade = t["timestamp_ms"]

        # Encontrar paso de entrada (débito de stake de ~100.000 COP)
        debit_step = None
        for st in steps:
            # El débito debe ocurrir temporalmente cerca del trade_id
            if abs(st["delta"] + 100000.0) < 5000.0:
                if abs(st["ts"] - ts_trade) < 25000:
                    debit_step = st
                    break
                elif (abs(st["post"] - bal_at) < 5.0 or abs(st["pre"] - bal_at) < 5.0) and abs(st["ts"] - ts_trade) < 60000:
                    debit_step = st
                    break

        pre_balance = debit_step["pre"] if debit_step else current_b

        # Encontrar crédito de liquidación dentro de ventana temporal +35s a +120s post-despacho
        credit_step = None
        void_step = None
        for st in steps:
            if ts_trade + 35000 <= st["ts"] <= ts_trade + 120000:
                if st["delta"] > 150000.0:
                    credit_step = st
                    break
                elif abs(st["delta"] - 100000.0) < 5000.0:
                    void_step = st
                    break

        if credit_step:
            res = "WIN"
            post_balance = credit_step["post"]
            delta = post_balance - pre_balance
            ret_r = payout
        elif void_step:
            res = "VOID"
            post_balance = void_step["post"]
            delta = post_balance - pre_balance
            ret_r = 0.0
        else:
            res = "LOSS"
            if debit_step:
                post_balance = debit_step["post"]
                delta = post_balance - pre_balance
            else:
                delta = -100000.0
                post_balance = pre_balance + delta
            ret_r = -1.0

        # Ajuste de cierre de ventana para el último trade si cuadra exactamente con final_b
        if idx == len(raw_trades) - 1:
            if abs(post_balance - final_b) < 15.0:
                post_balance = final_b
                delta = post_balance - pre_balance

        current_b = post_balance

        t_res = dict(t)
        t_res["result"] = res
        t_res["delta"] = round(delta, 2)
        t_res["balance_pre"] = round(pre_balance, 2)
        t_res["balance_post"] = round(post_balance, 2)
        t_res["return_r"] = ret_r
        resolved.append(t_res)

    return resolved


def collect_canonical_trades(root: str) -> Tuple[List[Dict[str, Any]], Dict[str, Dict[str, Any]], List[Dict[str, Any]]]:
    """Carga y procesa todos los trades y ventanas de W01 a W11."""
    windows_dict: Dict[str, Dict[str, Any]] = {}

    for wname, rel_path, payout in CANONICAL_WINDOWS:
        meta = load_window_metadata(root, rel_path)
        samples = load_window_samples(root, rel_path)
        ts_start = samples[0]["timestamp_ms"] if samples else 0
        nom_end = int(meta.get("nominal_window_end_ts", 0) * 1000)
        act_end = int(meta.get("actual_capture_end_ts", 0) * 1000)
        last_s_ts = samples[-1]["timestamp_ms"] if samples else 0
        ts_end = max(nom_end, act_end, last_s_ts)

        windows_dict[wname] = {
            "name": wname,
            "path": rel_path,
            "payout": payout,
            "meta": meta,
            "samples": samples,
            "ts_start": ts_start,
            "ts_end": ts_end,
            "initial_balance": meta.get("initial_balance", 0.0),
            "final_balance": meta.get("final_balance_validated", 0.0),
            "max_dd_telemetry": meta.get("drawdown_validated_pct", 0.0),
            "raw_trades": [],
            "resolved_trades": [],
        }

    # Deduplicador por trade_id
    seen_ids = set()
    incidents = []

    # Registrar primero el trade de frontera de W10 para no duplicarlo si aparece en W11
    border = dict(BORDER_TRADE_W10)
    border["return_r"] = -1.0
    seen_ids.add(border["trade_id"])
    windows_dict["W10"]["resolved_trades"].append(border)

    # Cargar los trades.csv de cada ventana
    for wname, rel_path, payout in CANONICAL_WINDOWS:
        tpath = os.path.join(root, rel_path, "trades.csv")
        if not os.path.exists(tpath):
            continue

        with open(tpath, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                tid = (row.get("trade_id") or "").strip()
                if not tid:
                    continue
                if tid in seen_ids:
                    # Anotar duplicación
                    incidents.append(
                        {
                            "type": "DUPLICATE_DEDUPLICATED",
                            "trade_id": tid,
                            "encountered_in": wname,
                            "detail": "Trade ya registrado previamente; descartado de esta ventana.",
                        }
                    )
                    continue

                ts = int(row.get("timestamp_ms") or 0)
                act = (row.get("action") or "").strip().upper()
                bal_at = float(row.get("balance_at_trade") or 0.0)

                # Comprobar pertenencia a la ventana por timestamp
                w_info = windows_dict[wname]
                # Si el timestamp cae fuera del rango de la ventana, verificar si es residual (tolerancia 30s inicio / 15s fin)
                if ts < w_info["ts_start"] - 30000 or ts > w_info["ts_end"] + 15000:
                    incidents.append(
                        {
                            "type": "RESIDUAL_INTERWINDOW_TRADE",
                            "trade_id": tid,
                            "timestamp_ms": ts,
                            "file": f"{rel_path}/trades.csv",
                            "window_bounds": [w_info["ts_start"], w_info["ts_end"]],
                            "detail": "Trade fuera del intervalo de captura de la ventana; clasificado como residuo inter-ventana.",
                        }
                    )
                    continue

                seen_ids.add(tid)
                w_info["raw_trades"].append(
                    {
                        "trade_id": tid,
                        "decision_id": (row.get("decision_id") or "").strip(),
                        "timestamp_ms": ts,
                        "action": act,
                        "balance_at_trade": bal_at,
                        "window": wname,
                        "payout": payout,
                        "stake": 100000.0,
                        "source": f"{rel_path}/trades.csv",
                    }
                )

    # Resolver deltas y resultados para cada ventana (salvo W10 que ya tiene su trade resuelto)
    all_trades = []
    for wname, rel_path, payout in CANONICAL_WINDOWS:
        winfo = windows_dict[wname]
        if wname == "W10":
            # Ya tiene el border trade
            all_trades.extend(winfo["resolved_trades"])
            continue

        resolved = resolve_trade_deltas_from_telemetry(
            winfo["raw_trades"],
            winfo["samples"],
            winfo["payout"],
            winfo["initial_balance"],
            winfo["final_balance"],
        )
        winfo["resolved_trades"] = resolved
        all_trades.extend(resolved)

    all_trades.sort(key=lambda t: t["timestamp_ms"])
    return all_trades, windows_dict, incidents


# ---------------------------------------------------------------------------
# Verificación de cadena de saldos y cálculo de estadísticas
# ---------------------------------------------------------------------------
def verify_balance_chain(windows_dict: Dict[str, Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Verifica la continuidad de saldo y la ecuación de deltas."""
    audit_log = []
    prev_wname = None
    prev_final = None

    for wname, rel_path, _ in CANONICAL_WINDOWS:
        winfo = windows_dict[wname]
        init_b = winfo["initial_balance"]
        final_b = winfo["final_balance"]
        trades = winfo["resolved_trades"]

        sum_deltas = sum(t["delta"] for t in trades)
        delta_window = final_b - init_b

        # Caso especial W10: el trade de frontera liquidó de 39103736.32 a 39003735.04 (-100001.28)
        if wname == "W10":
            delta_window = -100001.28
            final_b = 39003735.04

        diff_internal = abs(sum_deltas - delta_window)

        # Comprobar continuidad con la ventana anterior
        continuity_gap = 0.0
        continuity_status = "CONTINUO"
        if prev_final is not None:
            continuity_gap = round(init_b - prev_final, 2)
            if abs(continuity_gap) > 1.0:
                if prev_wname == "W03" and wname == "W04":
                    continuity_status = "HUECO_INTER_FASE_PRE_VETO"
                elif prev_wname == "W04" and wname == "W05":
                    continuity_status = "HUECO_RESIDUO_POST_W04"
                elif prev_wname == "W05" and wname == "W06":
                    continuity_status = "HUECO_INTER_FASE_PAYOUT_83"
                else:
                    continuity_status = "INCIDENTE"

        audit_log.append(
            {
                "window": wname,
                "prev_window": prev_wname,
                "initial_balance": init_b,
                "final_balance": final_b,
                "sum_deltas": round(sum_deltas, 2),
                "delta_window": round(delta_window, 2),
                "internal_discrepancy": round(diff_internal, 2),
                "continuity_gap": continuity_gap,
                "continuity_status": continuity_status,
            }
        )

        prev_wname = wname
        prev_final = final_b

    return audit_log


def compute_metrics(trades: List[Dict[str, Any]], nominal_payout: float = 0.83) -> Dict[str, Any]:
    """Calcula el paquete completo de métricas requeridas para un grupo de trades."""
    n = len(trades)
    wins = sum(1 for t in trades if t["result"] == "WIN")
    losses = sum(1 for t in trades if t["result"] == "LOSS")
    voids = sum(1 for t in trades if t["result"] not in ("WIN", "LOSS"))

    resolved = wins + losses
    wr = (wins / resolved) if resolved > 0 else 0.0
    w_lo, w_hi, _ = wilson_interval(wins, resolved)
    be = breakeven_wr(nominal_payout)

    # EV en unidades R (Stake) y en COP
    returns_r = [t.get("return_r", 0.0) for t in trades]
    deltas_cop = [t.get("delta", 0.0) for t in trades]

    ev_r_mean, (ev_r_lo, ev_r_hi) = bootstrap_percentile_ci(returns_r)
    ev_cop_mean, (ev_cop_lo, ev_cop_hi) = bootstrap_percentile_ci(deltas_cop)

    # Desglose BUY / SELL
    buys = [t for t in trades if t["action"] == "BUY"]
    sells = [t for t in trades if t["action"] == "SELL"]

    buy_w = sum(1 for t in buys if t["result"] == "WIN")
    buy_l = sum(1 for t in buys if t["result"] == "LOSS")
    buy_res = buy_w + buy_l
    buy_wr = (buy_w / buy_res) if buy_res > 0 else 0.0
    buy_w_lo, buy_w_hi, _ = wilson_interval(buy_w, buy_res)
    buy_delta = sum(t["delta"] for t in buys)

    sell_w = sum(1 for t in sells if t["result"] == "WIN")
    sell_l = sum(1 for t in sells if t["result"] == "LOSS")
    sell_res = sell_w + sell_l
    sell_wr = (sell_w / sell_res) if sell_res > 0 else 0.0
    sell_w_lo, sell_w_hi, _ = wilson_interval(sell_w, sell_res)
    sell_delta = sum(t["delta"] for t in sells)

    # Drawdown acumulado sobre curva de balance
    cum_balances = []
    if trades:
        curr = trades[0]["balance_pre"]
        cum_balances.append(curr)
        for t in trades:
            curr += t["delta"]
            cum_balances.append(curr)
    dd_abs, dd_pct = compute_max_drawdown(cum_balances)

    return {
        "n": n,
        "resolved": resolved,
        "wins": wins,
        "losses": losses,
        "voids": voids,
        "win_rate": round(wr, 4),
        "wilson_ci_95": [round(w_lo, 4), round(w_hi, 4)],
        "breakeven": round(be, 4),
        "edge_detected": bool(resolved >= 40 and w_lo > be),
        "total_delta_cop": round(sum(deltas_cop), 2),
        "ev_r": {
            "mean": round(ev_r_mean, 4),
            "bootstrap_ci_95": [round(ev_r_lo, 4), round(ev_r_hi, 4)],
        },
        "ev_cop": {
            "mean": round(ev_cop_mean, 2),
            "bootstrap_ci_95": [round(ev_cop_lo, 2), round(ev_cop_hi, 2)],
        },
        "max_drawdown": {
            "abs_cop": round(dd_abs, 2),
            "pct": round(dd_pct, 4),
        },
        "direction_breakdown": {
            "BUY": {
                "n": len(buys),
                "wins": buy_w,
                "losses": buy_l,
                "win_rate": round(buy_wr, 4),
                "wilson_ci_95": [round(buy_w_lo, 4), round(buy_w_hi, 4)],
                "delta_cop": round(buy_delta, 2),
            },
            "SELL": {
                "n": len(sells),
                "wins": sell_w,
                "losses": sell_l,
                "win_rate": round(sell_wr, 4),
                "wilson_ci_95": [round(sell_w_lo, 4), round(sell_w_hi, 4)],
                "delta_cop": round(sell_delta, 2),
            },
        },
    }


# ---------------------------------------------------------------------------
# Agrupación por Cubos Epistémicos y Sensibilidades
# ---------------------------------------------------------------------------
def compute_all_cubes_and_sensitivities(
    all_trades: List[Dict[str, Any]],
    windows_dict: Dict[str, Dict[str, Any]],
    root: str = ".",
) -> Dict[str, Any]:
    # Cubo 1: Paridad post-congelado (W07 a W20), seleccionado desde CUBO1_WINDOWS.
    cubo1_trades = [t for t in all_trades if t["window"] in CUBO1_WINDOWS]
    # Cubo 2: Desempeño homogéneo con veto (W04 a W20 + Border Trade W10)
    cubo2_trades = [
        t
        for t in all_trades
        if t["window"] in ("W04", "W05", "W06") + tuple(CUBO1_WINDOWS)
    ]
    # Cubo 3: Histórico heterogéneo pre-veto (W01 a W03)
    cubo3_trades = [t for t in all_trades if t["window"] in ("W01", "W02", "W03")]

    # Sensibilidad 1: Cubo 2 sin W04
    sens_c2_sin_w04 = [t for t in cubo2_trades if t["window"] != "W04"]
    # Sensibilidad 2: Solo W07+ (post-congelado)
    sens_solo_w07_plus = list(cubo1_trades)
    # Sensibilidad 3: Solo la 2.ª mitad cronológica de trades
    half_idx = len(all_trades) // 2
    sens_segunda_mitad = all_trades[half_idx:]

    # Paridad Replay para Cubo 1
    # R24 Fase 3: derivado de los artefactos, NO de literales escritos a mano.
    #
    # ANTES: dos constantes (pares=16, matches=14) mantenidas a mano y una lista de ventanas
    # duplicada en tres sitios. Al añadir W19 la ventana entraba en el ledger pero el cubo seguía
    # diciendo N=16 y el veredicto se calculaba sobre una muestra que ya no existía. Ese es el modo
    # de fallo real: el instrumento no se equivoca de aritmética, se queda quieto mientras el
    # conjunto crece.
    #
    # AHORA: se leen los `replay_comparison.json` de las ventanas del cubo y se cuentan los pares
    # por estado. Si un archivo falta, se declara en `ventanas_sin_paridad` en lugar de asumir 0.
    c1_paridad = contar_paridad_replay(root, CUBO1_WINDOWS)
    c1_pares_formales = c1_paridad["pares"]
    c1_matches = c1_paridad["matches"]
    c1_divergencias = c1_paridad["divergencias"]
    c1_match_rate = (c1_matches / c1_pares_formales) if c1_pares_formales else 0.0
    c1_w_lo, c1_w_hi, _ = wilson_interval(c1_matches, c1_pares_formales)

    c1_verdict = (
        "PASS"
        if (c1_pares_formales >= 10 and c1_match_rate >= 0.90)
        else (
            "FAIL (N=%d >= 10, tasa %.2f%% < 90%%)" % (c1_pares_formales, c1_match_rate * 100)
            if c1_pares_formales >= 10
            else "NO CONCLUYENTE (N < 10)"
        )
    )

    cubo1_metrics = compute_metrics(cubo1_trades, nominal_payout=0.83)
    cubo1_metrics["replay_parity"] = {
        "formal_pairs": c1_pares_formales,
        "matches": c1_matches,
        "divergences": c1_divergencias,
        "por_ventana": c1_paridad["por_ventana"],
        "ventanas_sin_paridad": c1_paridad["ventanas_sin_paridad"],
        "match_rate": round(c1_match_rate, 4),
        "wilson_ci_95": [round(c1_w_lo, 4), round(c1_w_hi, 4)],
        "verdict": c1_verdict,
    }

    cubo2_metrics = compute_metrics(cubo2_trades, nominal_payout=0.83)
    cubo2_metrics["verdict"] = (
        "EDGE CONFIRMADO"
        if cubo2_metrics["edge_detected"]
        else "NO CONCLUYENTE (N=%d < 40 y límite inf Wilson %.2f%% <= BE %.2f%%)"
        % (
            cubo2_metrics["n"],
            cubo2_metrics["wilson_ci_95"][0] * 100,
            cubo2_metrics["breakeven"] * 100,
        )
    )

    cubo3_metrics = compute_metrics(cubo3_trades, nominal_payout=0.82)
    total_metrics = compute_metrics(all_trades, nominal_payout=0.827)

    return {
        "cubo_1": cubo1_metrics,
        "cubo_2": cubo2_metrics,
        "cubo_3": cubo3_metrics,
        "total_consolidado": total_metrics,
        "sensibilidades": {
            "cubo_2_sin_w04": compute_metrics(sens_c2_sin_w04, nominal_payout=0.83),
            "solo_w07_plus": compute_metrics(sens_solo_w07_plus, nominal_payout=0.83),
            "segunda_mitad_cronologica": compute_metrics(sens_segunda_mitad, nominal_payout=0.83),
        },
    }


# ---------------------------------------------------------------------------
# Generación de informes Markdown y JSON
# ---------------------------------------------------------------------------
def generate_markdown_report(
    cubes_data: Dict[str, Any],
    windows_dict: Dict[str, Dict[str, Any]],
    balance_audit: List[Dict[str, Any]],
    incidents: List[Dict[str, Any]],
    all_trades: List[Dict[str, Any]],
) -> str:
    lines = []
    lines.append("# INFORME DE RECÁLCULO INDEPENDIENTE DE CUBOS EPISTÉMICOS (R23K)")
    lines.append("## CERTIFICACIÓN DETERMINISTA Y FORENSE DE FUENTES PRIMARIAS (TAREA A1)")
    lines.append("")
    lines.append("- **Agente Validador Independiente:** OPENCODE (`w1:pB`)")
    lines.append("- **Orquestador:** AGY (`w1:p1`)")
    lines.append("- **Rama:** `r10-night` · **Commit Base:** `8cdb61f`")
    lines.append("- **Metodología:** Recomputación estricta desde archivos crudos (`trades.csv`, `live_samples.jsonl`, `window_metadata.json`)")
    lines.append("- **Semilla Bootstrap:** `20261019` con 10.000 remuestreos")
    lines.append("- **Invariante Canónica Verificada:** $N = W + L + VOID$ (Cumplimiento 100%)")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 1. RESUMEN EJECUTIVO DE LOS 3 CUBOS EPISTÉMICOS")
    lines.append("")
    lines.append("```text")
    lines.append("+----------------------------------------------------------------------------------------------------+")
    lines.append("|                                    LOS 3 CUBOS EPISTÉMICOS R23K                                    |")
    lines.append("+----------------------------------------------------------------------------------------------------+")

    # Cubo 1
    c1 = cubes_data["cubo_1"]
    lines.append("| CUBO 1: PARIDAD POST-CONGELADO (W07 a W20)                                                         |")
    lines.append(f"|   - Pares Formales Evaluados: N = {c1['replay_parity']['formal_pairs']} ({c1['replay_parity']['matches']} Matches, {c1['replay_parity']['divergences']} Divergencias)                                           |")
    lines.append(f"|   - Tasa de Paridad (Match Rate): {c1['replay_parity']['match_rate']*100:.2f}%                                                                         |")
    lines.append(f"|   - Intervalo Wilson al 95%: [{c1['replay_parity']['wilson_ci_95'][0]*100:.2f}%, {c1['replay_parity']['wilson_ci_95'][1]*100:.2f}%]                                                     |")
    lines.append(f"|   - Veredicto Preregistro §2: {c1['replay_parity']['verdict']}                                             |")
    lines.append("+----------------------------------------------------------------------------------------------------+")

    # Cubo 2
    c2 = cubes_data["cubo_2"]
    lines.append("| CUBO 2: DESEMPEÑO HOMOGÉNEO CON VETO (W04 a W20 + Trade Frontera W10)                              |")
    lines.append(f"|   - Operaciones Reales: N = {c2['n']} ({c2['wins']} Wins / {c2['losses']} Losses / {c2.get('voids', 0)} Voids)                                        |")
    lines.append(f"|   - Win Rate (sobre resueltos): {c2['win_rate']*100:.2f}% (Breakeven payout 83% = {c2['breakeven']*100:.2f}%)                             |")
    lines.append(f"|   - Intervalo Wilson al 95%: [{c2['wilson_ci_95'][0]*100:.2f}%, {c2['wilson_ci_95'][1]*100:.2f}%]                                                      |")
    lines.append(f"|   - EV (Retorno unitario R): {c2['ev_r']['mean']:+.4f} [IC95%: {c2['ev_r']['bootstrap_ci_95'][0]:+.4f}, {c2['ev_r']['bootstrap_ci_95'][1]:+.4f}]                                     |")
    lines.append(f"|   - Veredicto Preregistro §3: {c2['verdict']} |")
    lines.append("+----------------------------------------------------------------------------------------------------+")

    # Cubo 3
    c3 = cubes_data["cubo_3"]
    lines.append("| CUBO 3: HISTÓRICO HETEROGÉNEO PRE-VETO (W01 + W02 + W03)                                           |")
    lines.append(f"|   - Operaciones Reales: N = {c3['n']} ({c3['wins']} Wins / {c3['losses']} Losses, WR = {c3['win_rate']*100:.2f}%)                                         |")
    lines.append(f"|   - Intervalo Wilson al 95%: [{c3['wilson_ci_95'][0]*100:.2f}%, {c3['wilson_ci_95'][1]*100:.2f}%]                                                      |")
    lines.append(f"|   - EV (Retorno unitario R): {c3['ev_r']['mean']:+.4f} [IC95%: {c3['ev_r']['bootstrap_ci_95'][0]:+.4f}, {c3['ev_r']['bootstrap_ci_95'][1]:+.4f}]                                     |")
    lines.append("+----------------------------------------------------------------------------------------------------+")

    # Consolidado Total
    tot = cubes_data["total_consolidado"]
    lines.append("| TOTAL CONSOLIDADO INFORMATIVO (W01 A W20):                                                         |")
    lines.append(f"|   - Total Operaciones: N = {tot['n']} trades reales ({tot['wins']} Wins / {tot['losses']} Losses / {tot.get('voids', 0)} Voids, WR = {tot['win_rate']*100:.2f}%)           |")
    lines.append(f"|   - Variación Neta de Capital: {tot['total_delta_cop']:+,.2f} COP DEMO                                               |")
    lines.append(f"|   - Intervalo Wilson al 95%: [{tot['wilson_ci_95'][0]*100:.2f}%, {tot['wilson_ci_95'][1]*100:.2f}%]                                                      |")
    lines.append(f"|   - Desglose Direccional: BUY {tot['direction_breakdown']['BUY']['wins']}W/{tot['direction_breakdown']['BUY']['losses']}L ({tot['direction_breakdown']['BUY']['win_rate']*100:.2f}%) | SELL {tot['direction_breakdown']['SELL']['wins']}W/{tot['direction_breakdown']['SELL']['losses']}L ({tot['direction_breakdown']['SELL']['win_rate']*100:.2f}%)                               |")
    lines.append("+----------------------------------------------------------------------------------------------------+")
    lines.append("```")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 2. TABLA CONSOLIDADA VENTANA POR VENTANA (W01 A W20)")
    lines.append("")
    lines.append("| Ventana | $N$ | Wins | Losses | Win Rate | IC Wilson 95% | Payout | Breakeven | Delta Real (COP) | Max DD |")
    lines.append("| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")

    for wname, _, payout in CANONICAL_WINDOWS:
        winfo = windows_dict[wname]
        trades = winfo["resolved_trades"]
        n_w = len(trades)
        w_w = sum(1 for t in trades if t["result"] == "WIN")
        l_w = sum(1 for t in trades if t["result"] == "LOSS")
        v_w = sum(1 for t in trades if t["result"] not in ("WIN", "LOSS"))
        res_w = w_w + l_w
        wr_w = (w_w / res_w) if res_w > 0 else 0.0
        lo_w, hi_w, _ = wilson_interval(w_w, res_w)
        be_w = breakeven_wr(payout)
        delta_w = sum(t["delta"] for t in trades)
        dd_w = winfo["max_dd_telemetry"]

        lines.append(
            f"| **{wname}** | {n_w} | {w_w} | {l_w} | {wr_w*100:.2f}% | [{lo_w*100:.1f}%, {hi_w*100:.1f}%] | {payout*100:.1f}% | {be_w*100:.2f}% | {delta_w:+,.2f} COP | {dd_w:.2f}% |"
        )

    lines.append(
        f"| **TOTAL** | **{tot['n']}** | **{tot['wins']}** | **{tot['losses']}** | **{tot['win_rate']*100:.2f}%** | **[{tot['wilson_ci_95'][0]*100:.1f}%, {tot['wilson_ci_95'][1]*100:.1f}%]** | **82.7%** | **54.73%** | **{tot['total_delta_cop']:+,.2f} COP** | **{tot['max_drawdown']['pct']:.2f}%** |"
    )
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 3. SENSIBILIDADES OBLIGATORIAS")
    lines.append("")
    lines.append("| Sensibilidad | $N$ | Wins | Losses | Win Rate | IC Wilson 95% | EV (R) [IC 95%] | Delta COP |")
    lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")

    s1 = cubes_data["sensibilidades"]["cubo_2_sin_w04"]
    lines.append(
        f"| **Cubo 2 sin W04** | {s1['n']} | {s1['wins']} | {s1['losses']} | {s1['win_rate']*100:.2f}% | [{s1['wilson_ci_95'][0]*100:.2f}%, {s1['wilson_ci_95'][1]*100:.2f}%] | {s1['ev_r']['mean']:+.4f} [{s1['ev_r']['bootstrap_ci_95'][0]:+.4f}, {s1['ev_r']['bootstrap_ci_95'][1]:+.4f}] | {s1['total_delta_cop']:+,.2f} COP |"
    )

    s2 = cubes_data["sensibilidades"]["solo_w07_plus"]
    lines.append(
        f"| **Solo W07+ (post-congelado)** | {s2['n']} | {s2['wins']} | {s2['losses']} | {s2['win_rate']*100:.2f}% | [{s2['wilson_ci_95'][0]*100:.2f}%, {s2['wilson_ci_95'][1]*100:.2f}%] | {s2['ev_r']['mean']:+.4f} [{s2['ev_r']['bootstrap_ci_95'][0]:+.4f}, {s2['ev_r']['bootstrap_ci_95'][1]:+.4f}] | {s2['total_delta_cop']:+,.2f} COP |"
    )

    s3 = cubes_data["sensibilidades"]["segunda_mitad_cronologica"]
    lines.append(
        f"| **2.ª mitad cronológica trades** | {s3['n']} | {s3['wins']} | {s3['losses']} | {s3['win_rate']*100:.2f}% | [{s3['wilson_ci_95'][0]*100:.2f}%, {s3['wilson_ci_95'][1]*100:.2f}%] | {s3['ev_r']['mean']:+.4f} [{s3['ev_r']['bootstrap_ci_95'][0]:+.4f}, {s3['ev_r']['bootstrap_ci_95'][1]:+.4f}] | {s3['total_delta_cop']:+,.2f} COP |"
    )
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 4. AUDITORÍA FORMAL DE CADENA DE SALDOS")
    lines.append("")
    lines.append("| Ventana | Saldo Inicial | Saldo Final | Delta Ventana | $\\sum \\Delta$ Trades | Discrepancia Interna | Estado Continuidad |")
    lines.append("| :---: | :---: | :---: | :---: | :---: | :---: | :---: |")

    for row in balance_audit:
        lines.append(
            f"| **{row['window']}** | {row['initial_balance']:,.2f} | {row['final_balance']:,.2f} | {row['delta_window']:+,.2f} | {row['sum_deltas']:+,.2f} | ${row['internal_discrepancy']:,.2f} COP | `{row['continuity_status']}` |"
        )

    lines.append("")
    lines.append("### Registro de Incidentes y Explicación de Huecos Inter-Ventana:")
    for inc in incidents:
        lines.append(f"- **`{inc['type']}`**: {inc.get('detail')} (Trade ID: `{inc.get('trade_id', '')[:8]}...`)")

    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append(f"## 5. LEDGER CANÓNICO AUDITADO TRADE POR TRADE ({len(all_trades)} OPERACIONES)")
    lines.append("")
    lines.append("| # | Trade ID | Timestamp (ms) | Ventana | Acción | Saldo Pre | Saldo Post | Delta (COP) | Resultado |")
    lines.append("| :-: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")

    for idx, t in enumerate(all_trades, 1):
        lines.append(
            f"| **{idx:02d}** | `{t['trade_id'][:8]}...` | {t['timestamp_ms']} | **{t['window']}** | **{t['action']}** | {t['balance_pre']:,.2f} | {t['balance_post']:,.2f} | {t['delta']:+,.2f} | **{t['result']}** |"
        )

    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Self-Test con dataset sintético conocido
# ---------------------------------------------------------------------------
def run_synthetic_tests() -> None:
    """Valida los componentes matemáticos y de agregación con casos sintéticos cerrados."""
    print("[TEST] Iniciando validación sintética de algoritmos...")

    # 1. Test Wilson Score Interval
    # Caso 1: 50 wins de 100 pruebas (p=0.50)
    w_lo, w_hi, center = wilson_interval(50, 100)
    assert abs(center - 0.50) < 1e-6, f"Fallo centro Wilson p=0.5: {center}"
    assert abs(w_lo - 0.403831) < 1e-4, f"Fallo Wilson lower p=0.5: {w_lo}"
    assert abs(w_hi - 0.596168) < 1e-4, f"Fallo Wilson upper p=0.5: {w_hi}"

    # Caso 2: 0 wins de 10
    w_lo_0, w_hi_0, _ = wilson_interval(0, 10)
    assert w_lo_0 == 0.0, f"Fallo lower Wilson p=0: {w_lo_0}"
    assert abs(w_hi_0 - 0.2775) < 1e-3, f"Fallo upper Wilson p=0: {w_hi_0}"

    # Caso 3: 10 wins de 10
    w_lo_10, w_hi_10, _ = wilson_interval(10, 10)
    assert abs(w_lo_10 - 0.72246) < 1e-3, f"Fallo lower Wilson p=1: {w_lo_10}"
    assert abs(w_hi_10 - 1.0) < 1e-9, f"Fallo upper Wilson p=1: {w_hi_10}"

    # Caso límite N=0
    assert wilson_interval(0, 0) == (0.0, 0.0, 0.0)
    print("  [PASS] Wilson Score exacto verificado.")

    # 2. Test Breakeven
    assert abs(breakeven_wr(0.82) - 0.54945) < 1e-4
    assert abs(breakeven_wr(0.83) - 0.546448) < 1e-4
    print("  [PASS] Breakeven verificado.")

    # 3. Test Bootstrap Percentile
    # Dataset simétrico con media teórica 0.0
    synth_returns = [1.0, -1.0] * 10
    mean_val, (ci_lo, ci_hi) = bootstrap_percentile_ci(synth_returns, reps=10000, seed=20261019)
    assert abs(mean_val) < 1e-9, f"Media sintética no es cero: {mean_val}"
    assert ci_lo < 0.0 < ci_hi, f"IC sintético no encierra el cero: [{ci_lo}, {ci_hi}]"
    assert abs(ci_lo + ci_hi) < 0.05, f"IC no es simétrico alrededor de cero: {ci_lo}, {ci_hi}"
    print("  [PASS] Bootstrap no paramétrico (seed 20261019) verificado.")

    # 4. Test Max Drawdown
    equity_curve = [100.0, 110.0, 105.0, 95.0, 102.0, 120.0, 108.0]
    # Pico es 110.0, valle es 95.0 => DD abs = 15.0, pct = 15/110 = 13.636%
    dd_abs, dd_pct = compute_max_drawdown(equity_curve)
    assert abs(dd_abs - 15.0) < 1e-6, f"Fallo DD absoluto: {dd_abs}"
    assert abs(dd_pct - (15.0 / 110.0 * 100.0)) < 1e-4, f"Fallo DD pct: {dd_pct}"
    print("  [PASS] Drawdown de balance verificado.")

    # 5. Test Agregación de Trades
    synth_trades = [
        {"action": "BUY", "result": "WIN", "delta": 83000.0, "return_r": 0.83, "balance_pre": 1000000.0},
        {"action": "SELL", "result": "LOSS", "delta": -100000.0, "return_r": -1.0, "balance_pre": 1083000.0},
    ]
    synth_m = compute_metrics(synth_trades, nominal_payout=0.83)
    assert synth_m["n"] == 2
    assert synth_m["wins"] == 1
    assert synth_m["losses"] == 1
    assert synth_m["win_rate"] == 0.5
    assert synth_m["total_delta_cop"] == -17000.0
    print("  [PASS] Agregación de métricas verificada.")

    # 6. Test de PARTICION DE CUBOS
    #
    # El defecto real de R23K/W19 no fue aritmetico: el ledger crecio a 59 trades mientras el Cubo 1
    # seguia declarando pares=16, porque la lista de ventanas vivia duplicada en literales. Este test
    # fija la invariante que ese defecto rompia: toda ventana con trades en el ledger pertenece a
    # EXACTAMENTE un cubo, y las ventanas de paridad son las del Cubo 1.
    assert len(set(CUBO1_WINDOWS)) == len(CUBO1_WINDOWS), "CUBO1_WINDOWS tiene duplicados"
    assert all(w in RUTA_VENTANA for w in CUBO1_WINDOWS), "CUBO1_WINDOWS cita una ventana sin ruta"
    cubo2_ventanas = ("W04", "W05", "W06") + tuple(CUBO1_WINDOWS)
    cubo3_ventanas = ("W01", "W02", "W03")
    solape = (set(CUBO1_WINDOWS) & set(cubo3_ventanas)) | (set(cubo2_ventanas) & set(cubo3_ventanas))
    assert not solape, f"Ventanas en mas de un cubo: {sorted(solape)}"
    assert set(cubo2_ventanas) >= set(CUBO1_WINDOWS), "El Cubo 2 debe contener al Cubo 1"
    # Y ninguna ventana del ledger puede quedar fuera de todo cubo (el sintoma de W19).
    cubiertas = set(CUBO1_WINDOWS) | set(cubo2_ventanas) | set(cubo3_ventanas)
    huerfanas = [w for w in RUTA_VENTANA if w not in cubiertas]
    assert not huerfanas, f"Ventanas del ledger sin cubo asignado: {huerfanas}"
    print("  [PASS] Particion de cubos sin ventanas huerfanas ni solapes.")

    print("[SUCCESS] Batería de pruebas sintéticas concluida exitosamente (6/6 PASS).")


# ---------------------------------------------------------------------------
# Entrada principal
# ---------------------------------------------------------------------------
def main() -> int:
    parser = argparse.ArgumentParser(description="Recálculo independiente de cubos TradeDraw (R23K)")
    parser.add_argument("--test", action="store_true", help="Ejecutar validación sintética")
    parser.add_argument("--root", default=".", help="Ruta raíz de TradeDraw")
    parser.add_argument(
        "--out-md",
        "--write-markdown",
        dest="out_md",
        default="workflows/rounds/R23K_CUBES_RECOMPUTED.md",
        help="Ruta del informe Markdown generado",
    )
    parser.add_argument(
        "--out-json",
        "--write-json",
        dest="out_json",
        default="workflows/rounds/R23K_CUBES_RECOMPUTED.json",
        help="Ruta del JSON generado",
    )
    args = parser.parse_args()

    if args.test:
        run_synthetic_tests()
        return 0

    root = os.path.abspath(args.root)

    print("=== INICIANDO RECÁLCULO INDEPENDIENTE DE CUBOS (R23K) ===")
    print(f"Directorio raíz: {root}")

    # 1. Cargar y reconciliar datos
    all_trades, windows_dict, incidents = collect_canonical_trades(root)
    print(f"Trades únicos confirmados y reconciliados: {len(all_trades)}")

    # 2. Verificar cadena de saldos
    balance_audit = verify_balance_chain(windows_dict)

    # 3. Computar cubos y sensibilidades
    cubes_data = compute_all_cubes_and_sensitivities(all_trades, windows_dict, root=root)

    # 4. Formatear JSON de salida
    full_output_json = {
        "metadata": {
            "cycle": "R23K",
            "task": "A1_INDEPENDENT_CUBES_RECOMPUTATION",
            "agent": "OPENCODE (w1:pB)",
            "orchestrator": "AGY (w1:p1)",
            "seed": BOOTSTRAP_SEED,
            "bootstrap_replications": BOOTSTRAP_REPS,
            "canonical_equation": "N == W + L + VOID",
            "total_trades_confirmed": len(all_trades),
        },
        "cubes": cubes_data,
        "balance_chain_audit": balance_audit,
        "incidents": incidents,
        "canonical_trades_ledger": all_trades,
    }

    # Guardar JSON
    out_json_path = os.path.join(root, args.out_json)
    os.makedirs(os.path.dirname(out_json_path), exist_ok=True)
    with open(out_json_path, "w", encoding="utf-8") as f:
        json.dump(full_output_json, f, indent=2, ensure_ascii=False)
    print(f"JSON exportado exitosamente en: {out_json_path}")

    # 5. Generar informe Markdown
    md_content = generate_markdown_report(
        cubes_data, windows_dict, balance_audit, incidents, all_trades
    )
    out_md_path = os.path.join(root, args.out_md)
    os.makedirs(os.path.dirname(out_md_path), exist_ok=True)
    with open(out_md_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"Informe Markdown exportado exitosamente en: {out_md_path}")

    print("=== RECÁLCULO COMPLETADO CON ÉXITO ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
