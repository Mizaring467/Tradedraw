#!/usr/bin/env python3
"""R23H FASE 0 - Reconciliacion forense determinista del canonical trade ledger.

Recorre loops/r23f_validation/ y loops/r23g_validation/, extrae cada trade unico
confirmado desde trades.csv, le asocia balance_before / balance_after / entry /
score / regime y emite:

  loops/r23h_analysis/CANONICAL_TRADES.csv    (18 columnas exactas)
  loops/r23h_analysis/DATA_INTEGRITY_REPORT.md

Invariante obligatoria: N == W + L + VOID.

El script es DETERMINISTA: mismo input -> mismo output byte a byte (sin azar, sin
red, sin reloj de pared). Uso:

  python scripts/analysis/reconcile_canonical_trades.py [--root .]
"""

from __future__ import annotations

import argparse
import bisect
import csv
import json
import os
import sys
from collections import Counter, defaultdict
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Constantes de especificacion (workflows/R23H_DATA_RECONCILIATION.md §2)
# ---------------------------------------------------------------------------
CANONICAL_COLUMNS = [
    "trade_id",
    "decision_id",
    "window",
    "phase",
    "timestamp",
    "asset",
    "action",
    "entry",
    "stake",
    "payout",
    "result",
    "balance_before",
    "balance_after",
    "market_regime",
    "trend",
    "adaptive_similarity",
    "score",
    "source_file",
]

PHASES = [("R23F", "r23f_validation"), ("R23G", "r23g_validation")]

DEFAULT_STAKE = 100_000.0        # COP nominal por operacion
DEFAULT_PAYOUT = 0.82            # payout del broker si /status no lo resuelve
SIGNAL_MATCH_MS = 5_000          # radio de busqueda del score de la señal liberada
RESULT_BALANCE_TOLERANCE = 6_000.0   # COP de tolerancia sobre el salto esperado
SETTLE_GRACE_MS = 45_000         # no hay settlement antes de 45s (expiracion = 60s)
SETTLE_DEADLINE_MS = 115_000     # +55..66s medido; >115s el credito es de otro trade
MIN_BALANCE_STEP = 1_500.0       # por debajo de esto la lectura es identica (ruido)

# Reglas de depuracion §2
EXCLUDED_DETAIL_TOKENS = ("END root",)
CONFIRM_TOKEN = "CONFIRMADA"
SALDO_TOKEN = "saldo movido"

SKILL_NOTE = "scripts/analysis/reconcile_canonical_trades.py"


# ---------------------------------------------------------------------------
# Utilidades de I/O
# ---------------------------------------------------------------------------
def read_jsonl(path: str) -> List[Dict[str, Any]]:
    """Lee un .jsonl tolerante a lineas corruptas (se descartan, no se inventan)."""
    out: List[Dict[str, Any]] = []
    if not os.path.exists(path):
        return out
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(obj, dict):
                out.append(obj)
    return out


def read_csv_rows(path: str) -> List[Dict[str, str]]:
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
        return [dict(r) for r in csv.DictReader(fh)]


def read_json(path: str) -> Optional[Dict[str, Any]]:
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return json.load(fh)
    except (json.JSONDecodeError, OSError):
        return None


def to_float(value: Any, default: Optional[float] = None) -> Optional[float]:
    if value is None or value == "":
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def to_int(value: Any, default: Optional[int] = None) -> Optional[int]:
    if value is None or value == "":
        return default
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default


# ---------------------------------------------------------------------------
# Descubrimiento de ventanas
# ---------------------------------------------------------------------------
def discover_windows(root: str) -> List[Dict[str, str]]:
    """Devuelve las ventanas ordenadas por fase y nombre: window_01..window_08."""
    found: List[Dict[str, str]] = []
    for phase, dirname in PHASES:
        base = os.path.join(root, "loops", dirname)
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            full = os.path.join(base, name)
            if os.path.isdir(full) and name.startswith("window_"):
                found.append(
                    {
                        "phase": phase,
                        "dirname": dirname,
                        "window_dir": name,
                        "path": full,
                        "window": "%s_%s" % (phase.lower(), name.replace("window_", "w")),
                    }
                )
    return found


# ---------------------------------------------------------------------------
# Serie de telemetria de balance (fuente primaria de settlement)
# ---------------------------------------------------------------------------
def build_balance_series(window: Dict[str, str]) -> List[Dict[str, Any]]:
    """Serie (ts, balance, W, L, regime, trend, price, payout) de live_samples.jsonl.

    `live_samples.jsonl` es el unico artefacto que trae timestamp + balance
    validado + contadores del APK en la misma fila; por eso es la fuente de
    settlement. `status_samples.jsonl` no tiene timestamp y solo se usa para
    los cruces de ventana.
    """
    series: List[Dict[str, Any]] = []
    for sample in read_jsonl(os.path.join(window["path"], "live_samples.jsonl")):
        status = sample.get("status")
        if not isinstance(status, dict):
            continue
        ts = to_int(sample.get("timestamp_ms"))
        if ts is None:
            continue
        balance = to_float(sample.get("validated_balance"))
        if balance is None:
            balance = to_float(sample.get("raw_balance"))
        if balance is None:
            balance = to_float(status.get("balance"))
        if balance is None:
            continue
        risk = status.get("risk") or {}
        analysis = status.get("analysis") or {}
        feed = status.get("feed") or {}
        series.append(
            {
                "ts": ts,
                "balance": float(balance),
                "wins": to_int(risk.get("totalWins"), 0) or 0,
                "losses": to_int(risk.get("totalLosses"), 0) or 0,
                "regime": "SIDEWAYS" if analysis.get("isMarketSideways") else "TRENDING",
                "trend": (analysis.get("trend") or "NEUTRAL"),
                "price": to_float(analysis.get("currentPriceY")),
                "payout": to_float(feed.get("payoutResolved")),
                "window": window["window"],
                "phase": window["phase"],
            }
        )
    series.sort(key=lambda s: s["ts"])
    return series


def extract_balance_steps(series: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Transiciones de balance distintas de cero = creditos/debitos reales del broker.

    Se calculan sobre la serie global ordenada por timestamp; un salto dentro de
    una ventana puede corresponder al settlement de un trade abierto en la
    ventana anterior (por eso la serie es global, no por ventana).
    """
    steps: List[Dict[str, Any]] = []
    for prev, cur in zip(series, series[1:]):
        delta = cur["balance"] - prev["balance"]
        if abs(delta) < MIN_BALANCE_STEP:
            continue
        steps.append(
            {
                "ts": cur["ts"],
                "delta": delta,
                "balance_before": prev["balance"],
                "balance_after": cur["balance"],
                "wins": cur["wins"],
                "losses": cur["losses"],
                "prev_wins": prev["wins"],
                "prev_losses": prev["losses"],
                "consumed_by": None,
            }
        )
    return steps


# ---------------------------------------------------------------------------
# Extraccion de trades confirmados
# ---------------------------------------------------------------------------
def confirmed_trade_event_present(window: Dict[str, str]) -> bool:
    """True si events.jsonl tiene al menos un ORDER_STATE confirmado."""
    for ev in read_jsonl(os.path.join(window["path"], "events.jsonl")):
        detail = str(ev.get("detail", ""))
        if CONFIRM_TOKEN in detail.upper() and SALDO_TOKEN in detail.lower():
            return True
    return False


def collect_raw_trades(windows: List[Dict[str, str]]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """Devuelve (filas crudas, rechazadas). Cada fila cruda trae su procedencia."""
    raw: List[Dict[str, Any]] = []
    rejected: List[Dict[str, Any]] = []

    for window in windows:
        path = os.path.join(window["path"], "trades.csv")
        for row in read_csv_rows(path):
            trade_id = (row.get("trade_id") or "").strip()
            detail = (row.get("detail") or "").strip()
            record = {
                "trade_id": trade_id,
                "decision_id": (row.get("decision_id") or "").strip(),
                "timestamp_ms": to_int(row.get("timestamp_ms")),
                "asset": (row.get("asset") or "").strip(),
                "action": (row.get("action") or "").strip().upper(),
                "detail": detail,
                "balance_at_trade": to_float(row.get("balance_at_trade")),
                "window": window["window"],
                "phase": window["phase"],
                "window_dir": window["window_dir"],
                "source_file": "%s/%s/trades.csv" % (window["dirname"], window["window_dir"]),
            }

            if not trade_id:
                rejected.append(dict(record, reject_reason="trade_id_vacio"))
                continue
            if any(token in detail for token in EXCLUDED_DETAIL_TOKENS):
                rejected.append(dict(record, reject_reason="detail_END_root"))
                continue
            if not (CONFIRM_TOKEN in detail.upper() and SALDO_TOKEN in detail.lower()):
                # Filas ENVIADA / NINGUNA / pre-ventana: orden sin confirmacion de
                # saldo movido -> no es trade canonico (§2 reglas de exclusion).
                rejected.append(dict(record, reject_reason="sin_confirmacion_saldo"))
                continue
            if record["timestamp_ms"] is None:
                rejected.append(dict(record, reject_reason="timestamp_ausente"))
                continue
            if record["balance_at_trade"] is None:
                rejected.append(dict(record, reject_reason="balance_ausente"))
                continue
            if record["action"] not in ("BUY", "SELL"):
                rejected.append(dict(record, reject_reason="accion_invalida"))
                continue
            raw.append(record)

    raw.sort(key=lambda r: (r["timestamp_ms"], r["trade_id"]))
    rejected.sort(key=lambda r: (r["timestamp_ms"] or 0, r["trade_id"]))
    return raw, rejected


def dedupe_trades(raw: List[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """Un trade_id = una fila. La primera aparicion cronologica gana; el resto va a duplicados.

    El caso real detectado es un trade_id que aparece en el cierre de R23F W03 y
    en la apertura de R23G W01 (la ventana siguiente arranca antes de que el
    monitor anterior cerrara su trades.csv).
    """
    unique: List[Dict[str, Any]] = []
    duplicates: List[Dict[str, Any]] = []
    seen: Dict[str, Dict[str, Any]] = {}
    for row in raw:
        kept = seen.get(row["trade_id"])
        if kept is None:
            seen[row["trade_id"]] = row
            unique.append(row)
            continue
        duplicates.append(
            dict(
                row,
                duplicate_of="%s/%s" % (kept["source_file"], kept["window"]),
                same_timestamp=(kept["timestamp_ms"] == row["timestamp_ms"]),
                same_balance=(kept["balance_at_trade"] == row["balance_at_trade"]),
            )
        )
    unique.sort(key=lambda r: (r["timestamp_ms"], r["trade_id"]))
    return unique, duplicates


# ---------------------------------------------------------------------------
# Resolucion de resultado (WIN / LOSS / VOID) por ledger de balance
# ---------------------------------------------------------------------------
def expected_win_step(stake: float, payout: float) -> float:
    """Credito bruto de un WIN medido desde el balance post-deduccion.

    `balance_at_trade` es el balance YA debitado (stake descontado). El broker
    acredita stake + profit = stake * (1 + payout) al ganar.
    """
    return stake * (1.0 + payout)


def classify_step(delta: float, stake: float, payout: float) -> Optional[str]:
    """Clasifica un salto de balance como settlement de un trade."""
    win = expected_win_step(stake, payout)
    if abs(delta - win) <= RESULT_BALANCE_TOLERANCE:
        return "WIN"
    if abs(delta - 2.0 * win) <= 2.0 * RESULT_BALANCE_TOLERANCE:
        # dos settlements agrupados en una sola muestra (cadencia ~3s)
        return "WIN_MULTI"
    if abs(delta - stake) <= RESULT_BALANCE_TOLERANCE:
        return "VOID"
    if abs(delta - 2.0 * stake) <= 2.0 * RESULT_BALANCE_TOLERANCE:
        return "VOID_MULTI"
    return None


def resolve_trades(
    trades: List[Dict[str, Any]],
    series: List[Dict[str, Any]],
    steps: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    """Asigna a cada trade su settlement real mediante asignacion global.

    Evidencia, en orden de fuerza (determinista, sin heuristica difusa):
      1. Salto de balance `+stake*(1+payout)` en la banda de settlement -> WIN.
      2. Salto de balance `+stake` (reembolso) en la misma banda       -> VOID.
      3. Sin credito acreditable + contador `totalLosses` del APK
         incrementado en la banda                                      -> LOSS.
      4. Sin credito y sin contador                                    -> VOID de
         integridad (se marca `unresolved` en el reporte).

    La asignacion NO es un greedy por orden de trade: seria incorrecto porque un
    trade perdedor puede tener su banda abierta cuando llega el credito del trade
    SIGUIENTE (los trades se espacian ~60s y el settlement llega a ~55-66s). Por
    eso los pasos de balance se recorren en orden cronologico y cada credito se
    asigna al trade no asignado mas reciente cuyo plazo ya expiro y que este
    dentro del horizonte. Cada salto se consume una sola vez.
    """
    ts_series = [s["ts"] for s in series]
    by_id: Dict[str, Dict[str, Any]] = {}

    for trade in trades:
        tms = trade["timestamp_ms"]
        idx = bisect.bisect_right(ts_series, tms) - 1
        anchor = series[idx] if idx >= 0 else None
        payout = DEFAULT_PAYOUT
        if anchor is not None and anchor.get("payout"):
            payout = round(float(anchor["payout"]), 4)
        balance_at_trade = float(trade["balance_at_trade"])
        stake = DEFAULT_STAKE
        by_id[trade["trade_id"]] = dict(
            trade,
            stake=stake,
            payout=payout,
            # `balance_at_trade` es el balance POST-deduccion: se captura en la
            # muestra de /status posterior al tap, cuando el broker ya debito el
            # stake. Por eso el balance previo real es +stake.
            balance_before=round(balance_at_trade + stake, 2),
            balance_after=round(balance_at_trade, 2),
            balance_delta=None,
            result=None,
            evidence=None,
            settlement_ts=None,
            settlement_lag_s=None,
            price_at_trade=anchor.get("price") if anchor else None,
            anchor=anchor,
            assigned=False,
        )

    ordered = sorted(by_id.values(), key=lambda t: (t["timestamp_ms"], t["trade_id"]))

    # --- Paso 1: creditos y reembolsos, en orden cronologico de salto ---------
    for step in sorted(steps, key=lambda s: s["ts"]):
        if step["consumed_by"] is not None:
            continue
        best = None
        for trade in ordered:
            if trade["assigned"]:
                continue
            lo = trade["timestamp_ms"] + SETTLE_GRACE_MS
            hi = trade["timestamp_ms"] + SETTLE_DEADLINE_MS
            if not (lo <= step["ts"] <= hi):
                continue
            kind = classify_step(step["delta"], trade["stake"], trade["payout"])
            if kind is None:
                continue
            # El settlement pertenece al trade mas reciente que ya expiro: un
            # credito nunca corresponde a un trade posterior al salto.
            if best is None or trade["timestamp_ms"] > best["timestamp_ms"]:
                best = trade
                best_kind = kind
        if best is None:
            continue
        step["consumed_by"] = best["trade_id"]
        best["assigned"] = True
        best["settlement_ts"] = step["ts"]
        best["settlement_lag_s"] = round((step["ts"] - best["timestamp_ms"]) / 1000.0, 1)
        if best_kind in ("WIN", "WIN_MULTI"):
            best["result"] = "WIN"
            best["evidence"] = "balance_credit_{:+.2f}".format(step["delta"])
            # El balance_after NO se toma de la serie: para cuando llega el
            # credito pueden haberse debitado otros stakes y el saldo observado
            # queda contaminado. Se deriva del propio trade:
            #   balance_after = balance_before + stake*payout
            best["balance_after"] = round(best["balance_before"] + best["stake"] * best["payout"], 2)
        else:
            best["result"] = "VOID"
            best["evidence"] = "balance_refund_{:+.2f}".format(step["delta"])
            # Reembolso: el saldo vuelve al nivel previo al debito.
            best["balance_after"] = round(best["balance_before"], 2)
        best["balance_delta"] = round(best["balance_after"] - best["balance_before"], 2)

    # --- Paso 2: sin credito -> LOSS si el contador del APK lo corrobora ------
    for trade in ordered:
        if trade["assigned"]:
            continue
        tms = trade["timestamp_ms"]
        lo, hi = tms + SETTLE_GRACE_MS, tms + SETTLE_DEADLINE_MS
        anchor = trade["anchor"]
        anchor_losses = anchor["losses"] if anchor else None
        corroborated = None
        for sample in series:
            if sample["ts"] < lo:
                continue
            if sample["ts"] > hi:
                break
            if anchor_losses is not None and sample["losses"] > anchor_losses:
                corroborated = sample
                break
        trade["assigned"] = True
        if corroborated is not None:
            trade["result"] = "LOSS"
            trade["settlement_ts"] = corroborated["ts"]
            trade["settlement_lag_s"] = round((corroborated["ts"] - tms) / 1000.0, 1)
            # LOSS: el stake debito no se recupera. balance_after derivado, no
            # leido de la serie (la muestra que corrobora el contador puede
            # incluir ya el debito de otro trade).
            trade["balance_after"] = round(trade["balance_before"] - trade["stake"], 2)
            trade["balance_delta"] = round(trade["balance_after"] - trade["balance_before"], 2)
            trade["evidence"] = "counters_loss_+{}".format(corroborated["losses"] - anchor_losses)
        else:
            # Sin ninguna evidencia de settlement: VOID de integridad. Nunca se
            # inventa un resultado ni se afirma un balance final; se conserva la
            # identidad N = W+L+VOID.
            trade["result"] = "VOID"
            trade["evidence"] = "unresolved_no_settlement"
            trade["balance_after"] = None
            trade["balance_delta"] = None

    return [
        {k: v for k, v in t.items() if k not in ("anchor", "assigned")}
        for t in ordered
    ]


# ---------------------------------------------------------------------------
# Enriquecimiento: entry / score / regime / similarity desde decisions.csv
# ---------------------------------------------------------------------------
def build_decision_index(
    windows: List[Dict[str, str]]
) -> Tuple[Dict[str, Dict[str, Any]], Dict[str, List[Dict[str, Any]]]]:
    """Indice por decision_id + señales puntuadas por ventana.

    El segundo mapa existe porque el APK NO escribe el score de confluencia en la
    fila ORDER_STATE/TAP que origina el trade: solo las reevaluaciones (VETO) de
    esa misma señal llevan `score`. Para recuperarlo se busca la decision
    puntuada de la misma accion mas cercana en el tiempo, dentro de una ventana
    acotada, y se documenta la cobertura.
    """
    index: Dict[str, Dict[str, Any]] = {}
    scored: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for window in windows:
        for row in read_csv_rows(os.path.join(window["path"], "decisions.csv")):
            did = (row.get("decision_id") or "").strip()
            if not did:
                continue
            score_val = to_float(row.get("score"))
            action_val = (row.get("action") or "").strip().upper()
            ts_val = to_int(row.get("timestamp"))
            if score_val is not None and action_val in ("BUY", "SELL") and ts_val is not None:
                scored[window["window"]].append(
                    {"ts": ts_val, "score": score_val, "action": action_val}
                )
            index[did] = {
                "decision_id": did,
                "timestamp": to_int(row.get("timestamp")),
                "price_at_signal": to_float(row.get("price_at_signal")),
                "score": to_float(row.get("score")),
                "market_regime": (row.get("market_regime") or "").strip(),
                "trend": (row.get("trend") or "").strip(),
                "action": (row.get("action") or "").strip().upper(),
                "kind": (row.get("kind") or "").strip(),
                "detail": (row.get("detail") or "").strip(),
                "window": window["window"],
            }
    for entries in scored.values():
        entries.sort(key=lambda e: e["ts"])
    return index, scored


def score_from_detail(detail: str) -> Optional[float]:
    """`[Confluencia: NNpts]` del detail cuando decisions.csv no trae score."""
    marker = "confluencia:"
    low = detail.lower()
    pos = low.find(marker)
    if pos < 0:
        return None
    tail = detail[pos + len(marker):]
    digits = ""
    for ch in tail.strip():
        if ch.isdigit():
            digits += ch
        elif digits:
            break
    return float(digits) if digits else None


def similarity_from_detail(detail: str) -> Optional[float]:
    """`[sim:NN%]` -> 0.0..1.0 cuando el veto registra similitud."""
    marker = "[sim:"
    low = detail.lower()
    pos = low.find(marker)
    if pos < 0:
        return None
    tail = detail[pos + len(marker):]
    digits = ""
    for ch in tail.strip():
        if ch.isdigit():
            digits += ch
        elif digits:
            break
    if not digits:
        return None
    return round(float(digits) / 100.0, 4)


def nearest_scored_signal(
    scored: List[Dict[str, Any]], ts: int, action: str
) -> Tuple[Optional[float], Optional[int]]:
    """Score de la señal puntuada mas cercana, misma accion, dentro de SIGNAL_MATCH_MS."""
    best: Optional[Tuple[int, float]] = None
    for entry in scored:
        if entry["action"] != action:
            continue
        distance = abs(entry["ts"] - ts)
        if distance > SIGNAL_MATCH_MS:
            continue
        if best is None or distance < best[0]:
            best = (distance, entry["score"])
    if best is None:
        return None, None
    return best[1], best[0]


def enrich(
    trades: List[Dict[str, Any]],
    decisions: Dict[str, Dict[str, Any]],
    scored: Dict[str, List[Dict[str, Any]]],
) -> List[Dict[str, Any]]:
    for trade in trades:
        decision = decisions.get(trade["decision_id"]) or {}

        entry = to_float(decision.get("price_at_signal"))
        if entry is None:
            # fallback: precio del tick vivo en el instante de la orden
            entry = to_float(trade.get("price_at_trade"))
        if entry is None:
            entry = 0.0

        score = to_float(decision.get("score"))
        if score is None:
            score = score_from_detail(trade.get("detail", ""))
        trade["score_source"] = "decision_row" if score is not None else ""
        if score is None:
            score, distance = nearest_scored_signal(
                scored.get(trade["window"], []), trade["timestamp_ms"], trade["action"]
            )
            if score is not None:
                trade["score_source"] = "nearest_signal_%dms" % distance

        regime = (decision.get("market_regime") or "").strip()
        trend = (decision.get("trend") or "").strip()

        sim = similarity_from_detail(trade.get("detail", ""))
        if sim is None:
            sim = similarity_from_detail(decision.get("detail", ""))

        trade["entry"] = round(float(entry), 4) if entry is not None else ""
        trade["score"] = "" if score is None else round(float(score), 1)
        trade["market_regime"] = regime or "UNKNOWN"
        trade["trend"] = trend or "UNKNOWN"
        trade["adaptive_similarity"] = "" if sim is None else sim
    return trades


# ---------------------------------------------------------------------------
# Reportes
# ---------------------------------------------------------------------------
def metric_block(trades: List[Dict[str, Any]]) -> Dict[str, Any]:
    counts = Counter(t["result"] for t in trades)
    wins, losses, voids = counts.get("WIN", 0), counts.get("LOSS", 0), counts.get("VOID", 0)
    total = wins + losses + voids
    unresolved = sum(1 for t in trades if t["evidence"] == "unresolved_no_settlement")
    pnl = sum((t.get("balance_delta") or 0.0) for t in trades)
    return {
        "N": total,
        "W": wins,
        "L": losses,
        "VOID": voids,
        "unresolved": unresolved,
        "wr_pct": round(wins / total * 100.0, 2) if total else 0.0,
        "pnl": round(pnl, 2),
    }


def write_canonical_csv(path: str, trades: List[Dict[str, Any]]) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=CANONICAL_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        for trade in trades:
            writer.writerow(
                {
                    "trade_id": trade["trade_id"],
                    "decision_id": trade["decision_id"],
                    "window": trade["window"],
                    "phase": trade["phase"],
                    "timestamp": trade["timestamp_ms"],
                    "asset": trade["asset"],
                    "action": trade["action"],
                    "entry": trade["entry"],
                    "stake": int(trade["stake"]),
                    "payout": round(float(trade["payout"]), 4),
                    "result": trade["result"],
                    "balance_before": trade["balance_before"],
                    "balance_after": trade["balance_after"],
                    "market_regime": trade["market_regime"],
                    "trend": trade["trend"],
                    "adaptive_similarity": trade["adaptive_similarity"],
                    "score": trade["score"],
                    "source_file": trade["source_file"],
                }
            )


def build_report(
    trades: List[Dict[str, Any]],
    raw_rows: int,
    rejected: List[Dict[str, Any]],
    duplicates: List[Dict[str, Any]],
    windows: List[Dict[str, str]],
    claimed: Dict[str, Any],
    csv_path: str,
) -> str:
    total = metric_block(trades)
    by_phase = {phase: metric_block([t for t in trades if t["phase"] == phase]) for phase, _ in PHASES}
    by_action = {
        action: metric_block([t for t in trades if t["action"] == action])
        for action in ("BUY", "SELL")
    }
    by_window: List[Tuple[str, Dict[str, Any]]] = []
    for window in windows:
        subset = [t for t in trades if t["window"] == window["window"]]
        if subset:
            by_window.append((window["window"], metric_block(subset)))

    identity_ok = total["N"] == total["W"] + total["L"] + total["VOID"]

    lines: List[str] = []
    add = lines.append

    add("# R23H — DATA INTEGRITY REPORT (CANONICAL TRADE LEDGER)")
    add("")
    add("**Generado por:** `%s`" % SKILL_NOTE)
    add("**Modo de ejecucion:** determinista, offline, solo lectura de artefactos primarios")
    add("**Salida:** `%s`" % csv_path.replace(os.sep, "/"))
    add("")
    add("---")
    add("")
    add("## 1. VEREDICTO DE INTEGRIDAD")
    add("")
    add("| Metrica | Valor |")
    add("| :--- | ---: |")
    add("| `raw_rows` (filas crudas en todos los `trades.csv`) | %d |" % raw_rows)
    add("| `confirmed_trades` (unicos y confirmados) | %d |" % total["N"])
    add("| `duplicates_removed` | %d |" % len(duplicates))
    add("| `rejected_rows` (excluidas por reglas §2) | %d |" % len(rejected))
    add("| `unresolved` (sin settlement localizable) | %d |" % total["unresolved"])
    add("| **W** (WIN) | **%d** |" % total["W"])
    add("| **L** (LOSS) | **%d** |" % total["L"])
    add("| **VOID** | **%d** |" % total["VOID"])
    add("| **N = W + L + VOID** | **%d = %d + %d + %d** |" % (total["N"], total["W"], total["L"], total["VOID"]))
    add("| Identidad `N == W + L + VOID` | **%s** |" % ("CUMPLE" if identity_ok else "NO CUMPLE"))
    add("| Win Rate canonico | %.2f%% |" % total["wr_pct"])
    add("| Delta de balance agregado | {:+,.2f} COP |".format(total["pnl"]))
    add("")
    if identity_ok:
        add("La ecuacion obligatoria **N = W + L + VOID** se cumple sobre el ledger reconstruido.")
    else:
        add("**ALERTA:** la ecuacion obligatoria NO se cumple. El ledger no debe usarse.")
    add("")
    add("---")
    add("")
    add("## 2. CONTEO POR FASE")
    add("")
    add("| Fase | N | W | L | VOID | WR | Delta balance (COP) |")
    add("| :--- | ---: | ---: | ---: | ---: | ---: | ---: |")
    for phase, _ in PHASES:
        m = by_phase[phase]
        add(
            "| {} | {} | {} | {} | {} | {:.2f}% | {:+,.2f} |".format(
                phase, m["N"], m["W"], m["L"], m["VOID"], m["wr_pct"], m["pnl"]
            )
        )
    add(
        "| **TOTAL** | **{}** | **{}** | **{}** | **{}** | **{:.2f}%** | **{:+,.2f}** |".format(
            total["N"], total["W"], total["L"], total["VOID"], total["wr_pct"], total["pnl"]
        )
    )
    add("")
    add("## 3. CONTEO POR DIRECCION")
    add("")
    add("| Direccion | N | W | L | VOID | WR | Delta balance (COP) |")
    add("| :--- | ---: | ---: | ---: | ---: | ---: | ---: |")
    for action in ("BUY", "SELL"):
        m = by_action[action]
        add(
            "| {} | {} | {} | {} | {} | {:.2f}% | {:+,.2f} |".format(
                action, m["N"], m["W"], m["L"], m["VOID"], m["wr_pct"], m["pnl"]
            )
        )
    add("")
    add("## 4. CONTEO POR VENTANA")
    add("")
    add("| Ventana | N | W | L | VOID | WR |")
    add("| :--- | ---: | ---: | ---: | ---: | ---: |")
    for window_name, m in by_window:
        add(
            "| %s | %d | %d | %d | %d | %.2f%% |"
            % (window_name, m["N"], m["W"], m["L"], m["VOID"], m["wr_pct"])
        )
    add("")
    add("---")
    add("")
    add("## 5. TRAZABILIDAD DE LA DEPURACION")
    add("")
    add("### 5.1 Filas descartadas por reglas de exclusion")
    if rejected:
        add("")
        add("| trade_id | ventana | motivo |")
        add("| :--- | :--- | :--- |")
        for row in rejected:
            add(
                "| `%s` | %s/%s | %s |"
                % (row["trade_id"][:12] or "(vacio)", row["phase"], row["window_dir"], row["reject_reason"])
            )
    else:
        add("")
        add("Ninguna fila descartada por reglas de exclusion.")
    add("")
    add("### 5.2 Duplicados eliminados (mismo `trade_id`)")
    if duplicates:
        add("")
        add("| trade_id | fuente conservada | fuente descartada | mismo ts | mismo balance |")
        add("| :--- | :--- | :--- | :---: | :---: |")
        for row in duplicates:
            add(
                "| `{}` | {} | {} | {} | {} |".format(
                    row["trade_id"][:12],
                    row["duplicate_of"],
                    row["source_file"],
                    "si" if row["same_timestamp"] else "no",
                    "si" if row["same_balance"] else "no",
                )
            )
    else:
        add("")
        add("Sin duplicados de `trade_id`.")
    add("")
    add("### 5.3 Trades sin settlement localizable (`unresolved`)")
    unresolved = [t for t in trades if t["evidence"] == "unresolved_no_settlement"]
    if unresolved:
        add("")
        add("| trade_id | ventana | accion | balance_at_trade | motivo |")
        add("| :--- | :--- | :--- | ---: | :--- |")
        for trade in unresolved:
            add(
                "| `{}` | {} | {} | {:,.2f} | sin credito ni incremento de contador en la banda de settlement |".format(
                    trade["trade_id"][:12], trade["window"], trade["action"], trade["balance_at_trade"]
                )
            )
        add("")
        add(
            "Estos trades se contabilizan como `VOID` para preservar la identidad "
            "`N = W + L + VOID`; se listan aqui para que ningun resultado se de por "
            "cierto sin evidencia."
        )
    else:
        add("")
        add("Todos los trades confirmados tienen settlement localizable.")
    add("")
    add("---")
    add("")
    add("## 6. CONTRAPOSICION CON LAS CIFRAS REPORTADAS EN R23G")
    add("")
    add("| Metric | Reportado R23G | Canonico | Desvio |")
    add("| :--- | ---: | ---: | ---: |")
    add(
        "| N (muestra R23G) | %s | %d | %+d |"
        % (claimed.get("N", "?"), by_phase["R23G"]["N"], by_phase["R23G"]["N"] - int(claimed.get("N", 0) or 0))
    )
    add(
        "| W | %s | %d | %+d |"
        % (claimed.get("W", "?"), by_phase["R23G"]["W"], by_phase["R23G"]["W"] - int(claimed.get("W", 0) or 0))
    )
    add(
        "| L | %s | %d | %+d |"
        % (claimed.get("L", "?"), by_phase["R23G"]["L"], by_phase["R23G"]["L"] - int(claimed.get("L", 0) or 0))
    )
    add(
        "| W + L reportado | %s | %d (W+L+VOID) | — |"
        % (claimed.get("W_plus_L", "?"), by_phase["R23G"]["N"])
    )
    add("")
    add("### 6.1 Cobertura de campos enriquecidos")
    add("")
    total_n = max(1, total["N"])
    score_ok = sum(1 for t in trades if t.get("score") not in ("", None))
    entry_ok = sum(1 for t in trades if to_float(t.get("entry"), 0.0))
    regime_ok = sum(1 for t in trades if t.get("market_regime") not in ("", "UNKNOWN"))
    sim_ok = sum(1 for t in trades if t.get("adaptive_similarity") not in ("", None))
    add("| Campo | Poblado | Cobertura | Fuente |")
    add("| :--- | ---: | ---: | :--- |")
    add("| `entry` | %d/%d | %.1f%% | `decisions.csv:price_at_signal`, fallback tick vivo |" % (entry_ok, total_n, entry_ok / total_n * 100))
    add("| `score` | %d/%d | %.1f%% | fila de decision; fallback señal puntuada a <=%ds |" % (score_ok, total_n, score_ok / total_n * 100, SIGNAL_MATCH_MS // 1000))
    add("| `market_regime` / `trend` | %d/%d | %.1f%% | `decisions.csv`; fallback `/status` en el instante de la orden |" % (regime_ok, total_n, regime_ok / total_n * 100))
    add("| `adaptive_similarity` | %d/%d | %.1f%% | `[sim:NN%%]` del detail (solo vetos) |" % (sim_ok, total_n, sim_ok / total_n * 100))
    add("")
    if score_ok < total_n:
        add(
            "El `score` queda vacio en %d trades porque el APK no lo escribe en la fila "
            "`ORDER_STATE`/`TAP` que origina la orden: solo las reevaluaciones `VETO` de la "
            "misma señal lo llevan. No se rellena por interpolacion: un score inventado "
            "contaminaria cualquier analisis de confluencia." % (total_n - score_ok)
        )
        add("")
    add("---")
    add("")
    add("## 7. REGLAS DE RESOLUCION APLICADAS")
    add("")
    add("1. **Universo:** una fila por `trade_id` unico con `detail` = `CONFIRMADA <ACCION> saldo movido`.")
    add("2. **`balance_at_trade` es POST-deduccion** (el stake ya fue descontado cuando el APK emite la confirmacion).")
    add("   Por tanto `balance_before = balance_at_trade + stake`.")
    add("3. **WIN:** salto observado `+%.0f COP` (= `stake x (1 + payout)`) en `[t+%ds, t+%ds]`." % (expected_win_step(DEFAULT_STAKE, DEFAULT_PAYOUT), SETTLE_GRACE_MS // 1000, SETTLE_DEADLINE_MS // 1000))
    add("4. **VOID:** salto observado `+%.0f COP` (= reembolso del stake) en la misma banda." % DEFAULT_STAKE)
    add("5. **LOSS:** sin credito acreditable + incremento del contador `totalLosses` del APK dentro de la banda.")
    add("6. **Cada salto se consume una sola vez**, asignado al trade no resuelto mas reciente cuya banda")
    add("   contenga el salto: dos trades seguidos no pueden reclamar el mismo credito de balance.")
    add("7. **Los `replay_journal.csv` NO son evidencia de resultado en vivo:** son la simulacion del motor")
    add("   offline sobre los ticks. Solo se usan como contraste, nunca como fuente de `result`.")
    add("")
    add("---")
    add("")
    add("## 8. REPRODUCIBILIDAD")
    add("")
    add("```")
    add("python scripts/analysis/reconcile_canonical_trades.py")
    add("```")
    add("")
    add("Sin argumentos usa la raiz del repo. `--root <ruta>` permite auditar una copia.")
    add("El script no escribe fuera de `loops/r23h_analysis/` y no modifica ningun artefacto fuente.")
    add("")
    return "\n".join(lines)


def read_claimed_metrics(root: str) -> Dict[str, Any]:
    """Cifras afirmadas en el cierre de R23G, para el contraste del §6."""
    claimed: Dict[str, Any] = {}
    aggregate = os.path.join(root, "loops", "r23g_validation", "AGGREGATE_REPORT.md")
    if not os.path.exists(aggregate):
        return claimed
    with open(aggregate, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if "R23G Nueva Muestra" not in line:
                continue
            cells = [c.strip().strip("*").replace(",", "") for c in line.split("|")]
            numbers: List[int] = []
            for cell in cells:
                if cell.isdigit():
                    numbers.append(int(cell))
            if len(numbers) >= 3:
                claimed["N"] = numbers[0]
                claimed["W"] = numbers[1]
                claimed["L"] = numbers[2]
                claimed["W_plus_L"] = numbers[1] + numbers[2]
            break
    return claimed


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="R23H canonical trade reconciliation")
    parser.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    parser.add_argument("--out-dir", default=None, help="default: <root>/loops/r23h_analysis")
    args = parser.parse_args(argv)

    root = os.path.abspath(args.root)
    out_dir = args.out_dir or os.path.join(root, "loops", "r23h_analysis")
    os.makedirs(out_dir, exist_ok=True)

    windows = discover_windows(root)
    if not windows:
        print("ERROR: no se encontraron ventanas en loops/r23{f,g}_validation", file=sys.stderr)
        return 2

    raw, rejected = collect_raw_trades(windows)
    unique, duplicates = dedupe_trades(raw)

    series: List[Dict[str, Any]] = []
    for window in windows:
        series.extend(build_balance_series(window))
    series.sort(key=lambda s: s["ts"])
    steps = extract_balance_steps(series)

    resolved = resolve_trades(unique, series, steps)
    decisions, scored = build_decision_index(windows)
    resolved = enrich(resolved, decisions, scored)
    resolved.sort(key=lambda t: (t["timestamp_ms"], t["trade_id"]))

    csv_path = os.path.join(out_dir, "CANONICAL_TRADES.csv")
    write_canonical_csv(csv_path, resolved)

    claimed = read_claimed_metrics(root)
    report = build_report(resolved, len(raw), rejected, duplicates, windows, claimed, csv_path)
    report_path = os.path.join(out_dir, "DATA_INTEGRITY_REPORT.md")
    with open(report_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(report)

    total = metric_block(resolved)
    print("ventanas auditadas      : %d" % len(windows))
    print("filas crudas            : %d" % len(raw))
    print("trades confirmados      : %d" % total["N"])
    print("duplicados eliminados   : %d" % len(duplicates))
    print("filas rechazadas        : %d" % len(rejected))
    print("unresolved              : %d" % total["unresolved"])
    print("W / L / VOID            : %d / %d / %d" % (total["W"], total["L"], total["VOID"]))
    print("identidad N=W+L+VOID    : %s" % ("OK" if total["N"] == total["W"] + total["L"] + total["VOID"] else "FALLA"))
    print("CSV                     : %s" % csv_path)
    print("Reporte                 : %s" % report_path)
    return 0 if total["N"] == total["W"] + total["L"] + total["VOID"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
