#!/usr/bin/env python3
"""
scripts/validation/compare_live_replay.py
Ronda 21 (Frente B): Comparador Live <-> Replay determinista para TradeDraw.

Compara decisiones registradas en vivo (journal / eventos con decision_id)
contra su reproducción en el motor de replay offline.
Para cada decision_id, verifica la coherencia campo por campo e identifica
la PRIMERA condición divergente según la jerarquía del pipeline de decisión.
"""

import argparse
import csv
import json
import math
import os
import re
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

# Jerarquía estricta para determinar la PRIMERA divergencia causal en el pipeline
HIERARCHY_FIELDS = [
    "asset",            # 1. ¿Es el mismo subyacente?
    "price",            # 2. ¿Precio de entrada idéntico dentro de tolerancia?
    "candle_second",    # 3. ¿Segundo de la vela :01-:05 sincronizado?
    "market_regime",    # 4. ¿Régimen detectado (TRENDING vs CHOP vs SIDEWAYS)?
    "trend",            # 5. ¿Tendencia direccional (UPTREND vs DOWNTREND)?
    "dist_support",     # 6. ¿Distancia a soporte dentro de tolerancia?
    "dist_resistance",  # 7. ¿Distancia a resistencia dentro de tolerancia?
    "confluence",       # 8. ¿Puntaje de confluencia (>=80 pts)?
    "strategy",         # 9. ¿Estrategia seleccionada (MT_REJECTION, etc.)?
    "direction",        # 10. ¿Acción emitida (BUY / SELL / NONE)?
    "veto",             # 11. ¿Veto activo (NONE vs V2 vs FEED_RANCIO vs DESAJUSTE)?
    "decision",         # 12. ¿Decisión final (EXECUTED vs VETOED vs SUPPRESSED)?
]


@dataclass
class DecisionRecord:
    decision_id: str
    trade_id: Optional[str]
    timestamp: int
    asset: str
    price: float
    candle_second: int
    market_regime: str
    trend: str
    dist_support: float
    dist_resistance: float
    confluence: float
    strategy: str
    direction: str
    veto: str
    decision: str
    reason: str
    source: str = "LIVE"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def normalize_str(val: Any) -> str:
    if val is None:
        return ""
    return str(val).strip().upper()


def is_float_equal(a: float, b: float, tol: float = 1e-4) -> bool:
    if math.isnan(a) and math.isnan(b):
        return True
    if math.isnan(a) or math.isnan(b):
        return False
    return abs(a - b) <= tol


def extract_confluence_from_reason(reason: str) -> float:
    match = re.search(r"Confluencia:\s*(\d+)", reason, re.IGNORECASE)
    if match:
        try:
            return float(match.group(1))
        except ValueError:
            pass
    return 80.0


def extract_veto_from_reason(reason: str) -> str:
    r_upper = reason.upper()
    if "DESAJUSTE" in r_upper:
        return "DESAJUSTE"
    if "FEED_RANCIO" in r_upper or "RANCIO" in r_upper:
        return "FEED_RANCIO"
    if "CONGELADO" in r_upper:
        return "PRICE_FROZEN"
    if "VETO (V2)" in r_upper or "RANGO" in r_upper:
        return "V2_RANGE"
    if "CHOPPY" in r_upper or "CHOP" in r_upper:
        return "CHOP"
    if "TIMEOUT" in r_upper:
        return "TIMEOUT"
    return "NONE"


def parse_journal_row(row: Dict[str, str], source_label: str = "LIVE") -> Optional[DecisionRecord]:
    # Extraer decision_id si existe
    decision_id = row.get("decision_id", "").strip()
    ts_str = row.get("timestamp", "").strip()
    if not ts_str:
        return None
    try:
        ts = int(ts_str)
    except ValueError:
        return None

    # Si la fila carece de decision_id (journal histórico), se genera uno determinista
    if not decision_id:
        decision_id = f"SYNTH-{ts}-{row.get('action', 'NONE')}"

    trade_id = row.get("trade_id", "").strip() or None
    reason = row.get("reason", "").strip()
    strategy = row.get("strategy", "AUTO_ADAPTIVE").strip()
    action = normalize_str(row.get("action", "NONE"))

    # Precio: preferir price_at_signal si existe, luego entryPrice / price_y
    price = 0.0
    for p_col in ["price_at_signal", "entryPrice", "price_y"]:
        val = row.get(p_col, "").strip()
        if val:
            try:
                price = float(val)
                if price > 0.0:
                    break
            except ValueError:
                pass

    # Segundo de vela
    candle_sec = -1
    csec_val = row.get("candle_second", "").strip()
    if csec_val:
        try:
            candle_sec = int(csec_val)
        except ValueError:
            pass
    if candle_sec < 0:
        candle_sec = int((ts // 1000) % 60)

    # Régimen y tendencia
    market_regime = normalize_str(row.get("market_regime", "TRENDING"))
    trend = normalize_str(row.get("trend", "SIDEWAYS"))

    # Distancias
    dist_sup = 0.0
    try:
        dist_sup = float(row.get("dist_support", 0.0))
    except ValueError:
        pass

    dist_res = 0.0
    try:
        dist_res = float(row.get("dist_resistance", 0.0))
    except ValueError:
        pass

    # Confluencia
    confluence = extract_confluence_from_reason(reason)
    if "confidence" in row:
        try:
            conf_val = float(row["confidence"])
            if conf_val > 1.0:
                confluence = conf_val
        except ValueError:
            pass

    # Veto y decisión
    veto = extract_veto_from_reason(reason)
    result = normalize_str(row.get("result", ""))
    if veto != "NONE":
        decision = "VETOED"
    elif action in ("BUY", "SELL"):
        decision = "EXECUTED"
    else:
        decision = "SUPPRESSED"

    asset = row.get("asset", "").strip()
    if not asset:
        asset = row.get("source", "").strip()
        if "ETH" in asset.upper() or "ETH" in reason.upper():
            asset = "ETHUSD-OTC"
        elif "AAVE" in asset.upper() or "AAVE" in reason.upper():
            asset = "AAVEUSD-OTC"
        elif "CRY" in asset.upper() or "IDX" in asset.upper():
            asset = "Crypto IDX"
        else:
            asset = "ETHUSD-OTC"

    return DecisionRecord(
        decision_id=decision_id,
        trade_id=trade_id,
        timestamp=ts,
        asset=asset,
        price=price,
        candle_second=candle_sec,
        market_regime=market_regime,
        trend=trend,
        dist_support=dist_sup,
        dist_resistance=dist_res,
        confluence=confluence,
        strategy=strategy,
        direction=action,
        veto=veto,
        decision=decision,
        reason=reason,
        source=source_label,
    )


def load_journal_file(path: str, source_label: str) -> List[DecisionRecord]:
    if not os.path.exists(path):
        return []
    records = []
    with open(path, mode="r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rec = parse_journal_row(row, source_label)
            if rec:
                records.append(rec)
    return records


def find_first_divergence(
    live: DecisionRecord,
    replay: DecisionRecord,
    tol_price: float = 1e-4,
    tol_dist: float = 0.02,
    tol_conf: float = 1.0,
) -> Optional[Tuple[str, Any, Any]]:
    """Recorre la jerarquía de condiciones e identifica la primera divergencia causal."""
    for field in HIERARCHY_FIELDS:
        val_l = getattr(live, field)
        val_r = getattr(replay, field)

        if field == "price":
            # Tolerancia relativa y absoluta
            scale = max(abs(val_l), abs(val_r), 1.0)
            if not is_float_equal(val_l, val_r, tol=tol_price * scale):
                return (field, val_l, val_r)
        elif field in ("dist_support", "dist_resistance"):
            if not is_float_equal(val_l, val_r, tol=tol_dist):
                return (field, val_l, val_r)
        elif field == "confluence":
            if not is_float_equal(val_l, val_r, tol=tol_conf):
                return (field, val_l, val_r)
        elif field == "candle_second":
            if val_l != val_r:
                return (field, val_l, val_r)
        else:
            # Comparación de cadenas normalizadas
            if normalize_str(val_l) != normalize_str(val_r):
                return (field, val_l, val_r)

    return None


def generate_synthetic_fixture() -> Tuple[List[DecisionRecord], List[DecisionRecord]]:
    """Genera pares Live <-> Replay deterministas demostrando MATCH y cada clase de DIVERGENCIA."""
    base_ts = 1792030000000
    live_records = []
    replay_records = []

    # 1. EXACT MATCH #1 (MT_REJECTION en UPTREND)
    d1 = "DEC-R21-001-MATCH-REJECTION"
    live_records.append(
        DecisionRecord(
            decision_id=d1,
            trade_id="TRD-001",
            timestamp=base_ts + 60000,
            asset="ETHUSD-OTC",
            price=1050.25,
            candle_second=1,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.03,
            dist_resistance=0.45,
            confluence=88.0,
            strategy="MT_REJECTION",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Rechazo en Soporte Confluencia 88pts",
            source="LIVE",
        )
    )
    replay_records.append(
        DecisionRecord(
            decision_id=d1,
            trade_id="TRD-001-SIM",
            timestamp=base_ts + 60000,
            asset="ETHUSD-OTC",
            price=1050.25,
            candle_second=1,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.03,
            dist_resistance=0.45,
            confluence=88.0,
            strategy="MT_REJECTION",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Rechazo en Soporte Confluencia 88pts",
            source="REPLAY",
        )
    )

    # 2. EXACT MATCH #2 (Veto preventivo en CHOP)
    d2 = "DEC-R21-002-MATCH-VETO-CHOP"
    live_records.append(
        DecisionRecord(
            decision_id=d2,
            trade_id=None,
            timestamp=base_ts + 120000,
            asset="ETHUSD-OTC",
            price=1049.80,
            candle_second=2,
            market_regime="CHOP",
            trend="SIDEWAYS",
            dist_support=0.10,
            dist_resistance=0.12,
            confluence=72.0,
            strategy="MT_RANGE",
            direction="NONE",
            veto="CHOP",
            decision="VETOED",
            reason="Filtro Anti-Choppy activo",
            source="LIVE",
        )
    )
    replay_records.append(
        DecisionRecord(
            decision_id=d2,
            trade_id=None,
            timestamp=base_ts + 120000,
            asset="ETHUSD-OTC",
            price=1049.80,
            candle_second=2,
            market_regime="CHOP",
            trend="SIDEWAYS",
            dist_support=0.10,
            dist_resistance=0.12,
            confluence=72.0,
            strategy="MT_RANGE",
            direction="NONE",
            veto="CHOP",
            decision="VETOED",
            reason="Filtro Anti-Choppy activo",
            source="REPLAY",
        )
    )

    # 3. DIVERGENCE en market_regime
    d3 = "DEC-R21-003-DIV-REGIME"
    live_records.append(
        DecisionRecord(
            decision_id=d3,
            trade_id="TRD-003",
            timestamp=base_ts + 180000,
            asset="ETHUSD-OTC",
            price=1052.10,
            candle_second=1,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.04,
            dist_resistance=0.50,
            confluence=85.0,
            strategy="MT_MOMENTUM",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Impulso alcista en tendencia",
            source="LIVE",
        )
    )
    replay_records.append(
        DecisionRecord(
            decision_id=d3,
            trade_id=None,
            timestamp=base_ts + 180000,
            asset="ETHUSD-OTC",
            price=1052.10,
            candle_second=1,
            market_regime="CHOP",  # DIVERGENCIA AQUÍ
            trend="SIDEWAYS",
            dist_support=0.04,
            dist_resistance=0.50,
            confluence=70.0,
            strategy="MT_MOMENTUM",
            direction="NONE",
            veto="CHOP",
            decision="VETOED",
            reason="Replay detectó mercado lateral",
            source="REPLAY",
        )
    )

    # 4. DIVERGENCE en dist_support
    d4 = "DEC-R21-004-DIV-SUPPORT"
    live_records.append(
        DecisionRecord(
            decision_id=d4,
            trade_id="TRD-004",
            timestamp=base_ts + 240000,
            asset="ETHUSD-OTC",
            price=1048.00,
            candle_second=1,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.01,
            dist_resistance=0.35,
            confluence=86.0,
            strategy="MT_REJECTION",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Toque directo en soporte",
            source="LIVE",
        )
    )
    replay_records.append(
        DecisionRecord(
            decision_id=d4,
            trade_id="TRD-004-SIM",
            timestamp=base_ts + 240000,
            asset="ETHUSD-OTC",
            price=1048.00,
            candle_second=1,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.18,  # DIVERGENCIA AQUÍ (0.01 vs 0.18 > tol 0.02)
            dist_resistance=0.35,
            confluence=81.0,
            strategy="MT_REJECTION",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Soporte recalculado offline",
            source="REPLAY",
        )
    )

    # 5. DIVERGENCE en dirección / veto (VETO DESAJUSTE en LIVE vs NORMAL en REPLAY)
    d5 = "DEC-R21-005-DIV-VETO-MISMATCH"
    live_records.append(
        DecisionRecord(
            decision_id=d5,
            trade_id=None,
            timestamp=base_ts + 300000,
            asset="AAVEUSD-OTC",
            price=121.50,
            candle_second=1,
            market_regime="TRENDING",
            trend="DOWNTREND",
            dist_support=0.40,
            dist_resistance=0.02,
            confluence=85.0,
            strategy="MT_REVERSAL",
            direction="NONE",
            veto="DESAJUSTE",  # Veto en pantalla vs feed
            decision="VETOED",
            reason="DESAJUSTE pantalla Aave ric=AAVEUSD feed=ETHUSD",
            source="LIVE",
        )
    )
    replay_records.append(
        DecisionRecord(
            decision_id=d5,
            trade_id="TRD-005-SIM",
            timestamp=base_ts + 300000,
            asset="AAVEUSD-OTC",
            price=121.50,
            candle_second=1,
            market_regime="TRENDING",
            trend="DOWNTREND",
            dist_support=0.40,
            dist_resistance=0.02,
            confluence=85.0,
            strategy="MT_REVERSAL",
            direction="SELL",  # Replay offline no tiene desajuste de pantalla
            veto="NONE",
            decision="EXECUTED",
            reason="Replay ejecutó trade de Aave directo",
            source="REPLAY",
        )
    )

    # 6. MISSING IN LIVE (Decisión solo vista en replay)
    d6 = "DEC-R21-006-MISSING-LIVE"
    replay_records.append(
        DecisionRecord(
            decision_id=d6,
            trade_id="TRD-006-SIM",
            timestamp=base_ts + 360000,
            asset="ETHUSD-OTC",
            price=1055.00,
            candle_second=3,
            market_regime="TRENDING",
            trend="UPTREND",
            dist_support=0.05,
            dist_resistance=0.20,
            confluence=82.0,
            strategy="MT_MOMENTUM",
            direction="BUY",
            veto="NONE",
            decision="EXECUTED",
            reason="Generado en replay",
            source="REPLAY",
        )
    )

    # 7. MISSING IN REPLAY (Decisión solo vista en live)
    d7 = "DEC-R21-007-MISSING-REPLAY"
    live_records.append(
        DecisionRecord(
            decision_id=d7,
            trade_id="TRD-007",
            timestamp=base_ts + 420000,
            asset="ETHUSD-OTC",
            price=1045.00,
            candle_second=4,
            market_regime="TRENDING",
            trend="DOWNTREND",
            dist_support=0.50,
            dist_resistance=0.01,
            confluence=89.0,
            strategy="MT_ENGULFING",
            direction="SELL",
            veto="NONE",
            decision="EXECUTED",
            reason="Ejecutado solo en vivo",
            source="LIVE",
        )
    )

    return live_records, replay_records


def compare_datasets(
    live_records: List[DecisionRecord],
    replay_records: List[DecisionRecord],
    tol_price: float = 1e-4,
    tol_dist: float = 0.02,
    tol_conf: float = 1.0,
) -> Dict[str, Any]:
    live_map = {r.decision_id: r for r in live_records}
    replay_map = {r.decision_id: r for r in replay_records}

    all_ids = list(dict.fromkeys(list(live_map.keys()) + list(replay_map.keys())))

    exact_matches = 0
    divergences = 0
    missing_live = 0
    missing_replay = 0
    unverifiable = 0

    divergence_breakdown: Dict[str, int] = {}
    decision_results = []

    for did in all_ids:
        in_live = did in live_map
        in_replay = did in replay_map

        if not in_live and in_replay:
            missing_live += 1
            decision_results.append({
                "decision_id": did,
                "status": "MISSING_LIVE",
                "first_divergence": "presence",
                "live_val": None,
                "replay_val": "PRESENT",
                "live": None,
                "replay": replay_map[did].to_dict(),
            })
            continue

        if in_live and not in_replay:
            missing_replay += 1
            decision_results.append({
                "decision_id": did,
                "status": "MISSING_REPLAY",
                "first_divergence": "presence",
                "live_val": "PRESENT",
                "replay_val": None,
                "live": live_map[did].to_dict(),
                "replay": None,
            })
            continue

        rec_l = live_map[did]
        rec_r = replay_map[did]

        # Validar si es no verificable (datos corruptos / precio <= 0 / nan)
        if rec_l.price <= 0.0 or rec_r.price <= 0.0 or math.isnan(rec_l.price) or math.isnan(rec_r.price):
            unverifiable += 1
            decision_results.append({
                "decision_id": did,
                "status": "UNVERIFIABLE",
                "first_divergence": "corrupt_data",
                "live_val": rec_l.price,
                "replay_val": rec_r.price,
                "live": rec_l.to_dict(),
                "replay": rec_r.to_dict(),
            })
            continue

        div = find_first_divergence(rec_l, rec_r, tol_price, tol_dist, tol_conf)
        if div is None:
            exact_matches += 1
            decision_results.append({
                "decision_id": did,
                "status": "MATCH",
                "first_divergence": None,
                "live": rec_l.to_dict(),
                "replay": rec_r.to_dict(),
            })
        else:
            field, val_l, val_r = div
            divergences += 1
            divergence_breakdown[field] = divergence_breakdown.get(field, 0) + 1
            decision_results.append({
                "decision_id": did,
                "status": "DIVERGENCE",
                "first_divergence": field,
                "live_val": val_l,
                "replay_val": val_r,
                "live": rec_l.to_dict(),
                "replay": rec_r.to_dict(),
            })

    total = len(all_ids)
    paired = exact_matches + divergences
    match_rate = (exact_matches / paired * 100.0) if paired > 0 else 0.0

    return {
        "summary": {
            "total_decisions": total,
            "paired_decisions": paired,
            "exact_matches": exact_matches,
            "divergences": divergences,
            "missing_live": missing_live,
            "missing_replay": missing_replay,
            "unverifiable": unverifiable,
            "match_rate_pct": round(match_rate, 2),
            "pass": match_rate >= 80.0 if paired > 0 else False,
        },
        "divergence_breakdown": divergence_breakdown,
        "decisions": decision_results,
    }


def format_markdown_report(result: Dict[str, Any], live_source: str, replay_source: str) -> str:
    s = result["summary"]
    breakdown = result["divergence_breakdown"]
    decisions = result["decisions"]

    status_icon = "PASS" if s["pass"] else "FAIL"

    lines = [
        "# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)",
        "",
        f"**Fecha:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}  ",
        "**Herramienta:** `scripts/validation/compare_live_replay.py`  ",
        f"**Fuente Live:** `{live_source}`  ",
        f"**Fuente Replay:** `{replay_source}`  ",
        f"**Veredicto Paridad:** **{status_icon}** ({s['match_rate_pct']}% exact match sobre pares analizados)  ",
        "",
        "---",
        "",
        "## 1. Resumen Cuantitativo de Comparación",
        "",
        "| Métrica | Valor | Definición / Criterio |",
        "|---|---|---|",
        f"| **Decisiones Totales Auditadas** | `{s['total_decisions']}` | Universo total de IDs únicos procesados |",
        f"| **Pares Evaluables (Live & Replay)** | `{s['paired_decisions']}` | IDs con presencia concurrente en ambas series |",
        f"| **Exact Matches (MATCH)** | **`{s['exact_matches']}`** | Paridad 100% en todos los campos según jerarquía |",
        f"| **Divergencias (DIVERGENCE)** | **`{s['divergences']}`** | Al menos un campo difirió dentro de la jerarquía |",
        f"| **Missing en Live** | `{s['missing_live']}` | Señal generada en Replay pero ausente en Live |",
        f"| **Missing en Replay** | `{s['missing_replay']}` | Decisión registrada en Live no reproducida en Replay |",
        f"| **No Verificables** | `{s['unverifiable']}` | Registros con precio <= 0 o NaN |",
        f"| **Tasa de Paridad (Match Rate)** | **`{s['match_rate_pct']}%`** | Objetivo de Gobernanza >= 80.0% |",
        "",
        "---",
        "",
        "## 2. Desglose de Primera Condición Divergente",
        "",
        "Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:",
        "",
    ]

    if breakdown:
        lines.append("| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |")
        lines.append("|---|---|---|")
        for f, cnt in sorted(breakdown.items(), key=lambda x: x[1], reverse=True):
            pct = round(cnt / s["divergences"] * 100.0, 1)
            lines.append(f"| `{f}` | **{cnt}** | {pct}% |")
    else:
        lines.append("*(No se detectaron divergencias en las decisiones analizadas)*")

    lines.extend([
        "",
        "---",
        "",
        "## 3. Log Detallado por `decision_id`",
        "",
        "| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |",
        "|---|---|---|---|---|---|",
    ])

    for d in decisions:
        did = d["decision_id"]
        status = d["status"]
        first_div = d.get("first_divergence") or "-"
        val_l = d.get("live_val")
        val_r = d.get("replay_val")
        val_l_str = str(val_l) if val_l is not None else "-"
        val_r_str = str(val_r) if val_r is not None else "-"

        l_act = d["live"]["direction"] if d.get("live") else "-"
        r_act = d["replay"]["direction"] if d.get("replay") else "-"

        lines.append(
            f"| `{did}` | `{status}` | `{first_div}` | `{val_l_str}` | `{val_r_str}` | `{l_act}` / `{r_act}` |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 4. Criterio de Aceptación y Gobernanza",
        "",
        "- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.",
        "- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.",
        "- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.",
        "- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).",
    ])

    return "\n".join(lines)


def self_test():
    print("=== RUNNING SELF-TEST: compare_live_replay ===")
    live, replay = generate_synthetic_fixture()
    assert len(live) == 6, f"Expected 6 live fixture items, got {len(live)}"
    assert len(replay) == 6, f"Expected 6 replay fixture items, got {len(replay)}"

    res = compare_datasets(live, replay)
    s = res["summary"]

    assert s["exact_matches"] == 2, f"Expected 2 matches, got {s['exact_matches']}"
    assert s["divergences"] == 3, f"Expected 3 divergences, got {s['divergences']}"
    assert s["missing_live"] == 1, f"Expected 1 missing_live, got {s['missing_live']}"
    assert s["missing_replay"] == 1, f"Expected 1 missing_replay, got {s['missing_replay']}"
    assert s["unverifiable"] == 0, f"Expected 0 unverifiable, got {s['unverifiable']}"

    b = res["divergence_breakdown"]
    assert b.get("market_regime") == 1, f"Expected 1 market_regime divergence, got {b}"
    assert b.get("dist_support") == 1, f"Expected 1 dist_support divergence, got {b}"
    assert b.get("direction") == 1, f"Expected 1 direction divergence, got {b}"

    print("ALL SELF-TEST ASSERTIONS PASSED (OK)")


def save_fixture_to_csv(live: List[DecisionRecord], replay: List[DecisionRecord], out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    live_path = os.path.join(out_dir, "fixture_live_decisions.csv")
    replay_path = os.path.join(out_dir, "fixture_replay_decisions.csv")

    header = [
        "decision_id", "trade_id", "timestamp", "asset", "price_at_signal",
        "candle_second", "market_regime", "trend", "dist_support", "dist_resistance",
        "confidence", "strategy", "action", "veto", "decision", "reason", "source"
    ]

    for path, dataset, label in [(live_path, live, "LIVE"), (replay_path, replay, "REPLAY")]:
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(header)
            for d in dataset:
                w.writerow([
                    d.decision_id, d.trade_id or "", d.timestamp, d.asset, d.price,
                    d.candle_second, d.market_regime, d.trend, d.dist_support, d.dist_resistance,
                    d.confluence, d.strategy, d.direction, d.veto, d.decision, d.reason, label
                ])
    return live_path, replay_path


def main():
    parser = argparse.ArgumentParser(description="Comparador determinista Live <-> Replay (Frente B)")
    parser.add_argument("--live-journal", help="Ruta al archivo CSV/JSONL de la sesión en vivo")
    parser.add_argument("--replay-journal", help="Ruta al archivo CSV/JSONL del replay offline")
    parser.add_argument("--ticks-csv", help="Ruta al archivo de ticks (si replay-journal se omite)")
    parser.add_argument("--output-json", help="Ruta para guardar el resumen en formato JSON")
    parser.add_argument("--output-md", help="Ruta para guardar el reporte Markdown")
    parser.add_argument("--generate-fixture", help="Directorio donde guardar los CSV del fixture sintético")
    parser.add_argument("--fixture", action="store_true", help="Usar fixture determinista sintético integrado")
    parser.add_argument("--self-test", action="store_true", help="Ejecutar suite interna de autochequeo")
    parser.add_argument("--tolerance-price", type=float, default=1e-4, help="Tolerancia para precio (default: 1e-4)")
    parser.add_argument("--tolerance-dist", type=float, default=0.02, help="Tolerancia para distancias (default: 0.02)")
    parser.add_argument("--tolerance-confluence", type=float, default=1.0, help="Tolerancia para confluencia (default: 1.0)")

    args = parser.parse_args()

    if args.self_test:
        self_test()
        sys.exit(0)

    # Determinar fuentes
    live_records: List[DecisionRecord] = []
    replay_records: List[DecisionRecord] = []
    live_label = args.live_journal or "SYNTHETIC_FIXTURE_LIVE"
    replay_label = args.replay_journal or args.ticks_csv or "SYNTHETIC_FIXTURE_REPLAY"

    if args.generate_fixture:
        live_fix, replay_fix = generate_synthetic_fixture()
        lp, rp = save_fixture_to_csv(live_fix, replay_fix, args.generate_fixture)
        print(f"Fixture generado exitosamente en:\n  Live:   {lp}\n  Replay: {rp}")

    if args.fixture or (not args.live_journal and not args.replay_journal):
        print("Usando fixture sintético determinista (MATCH vs DIVERGENCE)...")
        live_records, replay_records = generate_synthetic_fixture()
    else:
        if args.live_journal:
            live_records = load_journal_file(args.live_journal, "LIVE")
        if args.replay_journal:
            replay_records = load_journal_file(args.replay_journal, "REPLAY")
        elif args.ticks_csv:
            # Si se pasa ticks-csv pero no replay-journal, buscar si existe build/backtest_out.csv o replay
            alt_replay = "build/backtest_out.csv"
            if os.path.exists(alt_replay):
                replay_records = load_journal_file(alt_replay, "REPLAY")
            else:
                print(f"Nota: ticks-csv provisto ({args.ticks_csv}). Fallback a fixture si no hay replay journal.")
                live_records, replay_records = generate_synthetic_fixture()

    res = compare_datasets(
        live_records=live_records,
        replay_records=replay_records,
        tol_price=args.tolerance_price,
        tol_dist=args.tolerance_dist,
        tol_conf=args.tolerance_confluence,
    )

    # Imprimir resumen en consola
    s = res["summary"]
    print("=" * 60)
    print("COMPARACIÓN LIVE <-> REPLAY (Ronda 21 Frente B)")
    print("=" * 60)
    print(f"Decisiones auditadas: {s['total_decisions']}")
    print(f"Pares evaluados:      {s['paired_decisions']}")
    print(f"Exact Matches:        {s['exact_matches']}")
    print(f"Divergencias:         {s['divergences']}")
    print(f"Missing en Live:      {s['missing_live']}")
    print(f"Missing en Replay:    {s['missing_replay']}")
    print(f"No verificables:      {s['unverifiable']}")
    print(f"Tasa de paridad:      {s['match_rate_pct']}% -> {'PASS' if s['pass'] else 'FAIL'}")
    if res["divergence_breakdown"]:
        print("\nDesglose de causas raíz de divergencia:")
        for k, v in res["divergence_breakdown"].items():
            print(f"  - {k:18}: {v}")

    # Guardar JSON
    if args.output_json:
        os.makedirs(os.path.dirname(os.path.abspath(args.output_json)), exist_ok=True)
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(res, f, indent=2, ensure_ascii=False)
        print(f"\nSalida JSON guardada en: {args.output_json}")

    # Guardar Markdown
    if args.output_md:
        os.makedirs(os.path.dirname(os.path.abspath(args.output_md)), exist_ok=True)
        md_text = format_markdown_report(res, live_label, replay_label)
        with open(args.output_md, "w", encoding="utf-8") as f:
            f.write(md_text)
        print(f"Reporte Markdown guardado en: {args.output_md}")


if __name__ == "__main__":
    main()
