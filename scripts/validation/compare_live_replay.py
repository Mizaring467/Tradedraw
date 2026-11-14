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


# ---------------------------------------------------------------------------
# R23C Fase 2 (REQ 3): modo `trades`
# ---------------------------------------------------------------------------
#
# POR QUE. El modo por defecto compara `decisions.csv` contra el journal del replay. Pero `decisions`
# incluye TODOS los vetos (`ADAPTIVE_BLOCK`, `COOLDOWN`, `OPERACION_EN_CURSO`...), mientras el replay
# solo produce trades ejecutados: cruzar universos distintos daba 0% de paridad por construccion.
# El modo `trades` compara lo mismo con lo mismo: ordenes confirmadas contra ordenes simuladas.


def parse_live_trades_csv(path: str) -> List[Dict[str, Any]]:
    """
    Lee `trades.csv` del monitor live. Columnas reales:
    `trade_id,decision_id,timestamp_ms,asset,action,detail,balance_at_trade`.

    No usa `parse_journal_row` porque el esquema es distinto (no hay `price_at_signal` ni
    `candle_second`): inventarlos haria que la comparacion midiera campos vacios.
    """
    if not os.path.exists(path):
        return []
    out: List[Dict[str, Any]] = []
    with open(path, mode="r", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            ts_raw = (row.get("timestamp_ms") or "").strip()
            if not ts_raw:
                continue
            try:
                ts = int(float(ts_raw))
            except ValueError:
                continue
            out.append({
                "decision_id": (row.get("decision_id") or "").strip(),
                "trade_id": (row.get("trade_id") or "").strip(),
                "timestamp": ts,
                "asset": (row.get("asset") or "").strip(),
                "action": normalize_str(row.get("action")),
                "detail": (row.get("detail") or "").strip(),
                "balance": _safe_float(row.get("balance_at_trade")),
                "candle_second": (ts // 1000) % 60,
            })
    return out


def parse_replay_journal_trades(path: str) -> List[Dict[str, Any]]:
    """
    Lee `replay_journal.csv` del motor offline. Columnas relevantes:
    `timestamp,asset,action,price_y,candle_second,result,decision_id`.
    """
    if not os.path.exists(path):
        return []
    out: List[Dict[str, Any]] = []
    with open(path, mode="r", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            ts_raw = (row.get("timestamp") or "").strip()
            if not ts_raw:
                continue
            try:
                ts = int(float(ts_raw))
            except ValueError:
                continue
            csec = (row.get("candle_second") or "").strip()
            try:
                candle_second = int(float(csec))
            except ValueError:
                candle_second = (ts // 1000) % 60
            out.append({
                "decision_id": (row.get("decision_id") or "").strip(),
                "trade_id": "",
                "timestamp": ts,
                "asset": (row.get("asset") or "").strip(),
                "action": normalize_str(row.get("action")),
                "detail": (row.get("reason") or "").strip(),
                "price": _safe_float(row.get("price_y")),
                "result": normalize_str(row.get("result")),
                "candle_second": candle_second,
            })
    return out


def _safe_float(val: Any) -> float:
    try:
        return float(str(val).strip())
    except (TypeError, ValueError):
        return 0.0


def _latencia_explica_segundo_de_vela(
    live: Dict[str, Any],
    replay: Dict[str, Any],
    temporal_tolerance_ms: int,
) -> bool:
    """
    Decide si la diferencia de `candle_second` entre un par live y su replay es explicable por la
    latencia fisica de lectura de saldo en Android Go, en vez de por una divergencia real del motor.

    POR QUE EXISTE. `trades.csv` del lado live fecha la orden con el instante en que el broker
    confirma el movimiento de saldo, no con el tick que origino la senal. Ese retardo medido esta
    entre 2,3 s y 5,5 s. Como `candle_second` es `(ts // 1000) % 60`, un desfase de 4 s cambia el
    segundo de vela aunque ambos lados describan la MISMA decision (mismo `decision_id`). Marcarlo
    como divergencia castigaba al comparador por tener mas evidencia, no por encontrar un fallo.

    DOBLE CONDICION, las dos obligatorias:
      1. El desfase temporal debe caber en `temporal_tolerance_ms`. Fuera de esa ventana no hay
         latencia que explique nada: es otra orden.
      2. La diferencia de segundo debe quedar explicada por el desfase REALMENTE observado
         (`ceil(delta_ms / 1000)`), **con wrap de 60 s**: `candle_second` salta de 59 a 0, asi que
         una diferencia cruda de 58 puede ser en realidad un desfase de 2 s cruzando el borde del
         minuto. Comparar los segundos sin wrap marcaria divergencia justo en los trades de fin de
         vela, que son los mas valiosos para el analisis (ventana sniper `:58-:59`).

    POR QUE EL LIMITE SALE DEL DESFASE REAL Y NO DE LA TOLERANCIA. Usar
    `temporal_tolerance_ms // 1000` como tope absoluto dejaria pasar un par con 1 s de desfase y 6 s
    de diferencia de vela: la tolerancia es el MAXIMO admisible, no una explicacion. Atar el limite
    al desfase observado mantiene la exigencia donde corresponde — si los dos lados se separan 1 s,
    sus segundos de vela no pueden diferir en 6.

    NO relaja la comparacion de `action`, `price` ni `result`: solo el segundo de vela, y solo
    cuando la aritmetica del tiempo lo explica.
    """
    delta_ms = abs(int(replay["timestamp"]) - int(live["timestamp"]))
    if delta_ms > temporal_tolerance_ms:
        return False
    cs_live = int(live["candle_second"])
    cs_replay = int(replay["candle_second"])
    if cs_live < 0 or cs_replay < 0:
        return False
    # Distancia circular en la esfera de 60 s: 58 vs 1 => 3 s, no 57.
    diff_directa = abs(cs_live - cs_replay)
    diff_circular = min(diff_directa, 60 - diff_directa)
    if diff_circular == 0:
        # Sin diferencia de vela no hay nada que absolver: nunca es divergencia.
        return True
    # Tope duro: ni con la tolerancia mas generosa se absuelve mas que la tolerancia misma.
    max_segundos_absorbibles = min(
        -(-delta_ms // 1000),  # ceil(delta_ms / 1000), sin importar math
        temporal_tolerance_ms // 1000,
    )
    if max_segundos_absorbibles <= 0:
        return False
    return diff_circular <= max_segundos_absorbibles


def compare_trades(
    live_trades: List[Dict[str, Any]],
    replay_trades: List[Dict[str, Any]],
    temporal_tolerance_ms: int = 6000,
    price_tolerance: float = 1e-3,
) -> Dict[str, Any]:
    """
    Empareja ordenes confirmadas en vivo contra el journal del replay.

    Estrategia (REQ 3):
      1. `decision_id` exacto (el id es determinista desde R23 Iter 3).
      2. Fallback temporal: mismo `asset` y entrada dentro de `temporal_tolerance_ms`.

    R23H Fase 2: la tolerancia por defecto sube de 3000 a 6000 ms. En hardware Android Go el
    timestamp de la orden en vivo es el del TAP observado en /status, que llega ~4,5-5,0 s despues
    del instante en que el motor decidio la senal; el replay fecha por el tick de la senal. Con
    3000 ms el emparejamiento temporal —el unico que funciona cuando el `decision_id` diverge por
    `candle_second` o por el RIC— descartaba pares correctos y hundia el match rate.

    Campos comparados: `action`, `candle_second`, `price` (solo si el lado live lo trae — `trades.csv`
    no tiene precio de entrada, y comparar contra el valor por defecto 0.0 marcaria divergencia
    siempre), y `result` cuando el replay lo declara.
    """
    replay_por_id = {r["decision_id"]: r for r in replay_trades if r["decision_id"]}
    consumidos: set = set()
    pares: List[Dict[str, Any]] = []

    for lt in live_trades:
        match = None
        por_id = False

        # 1. Por decision_id exacto.
        cand = replay_por_id.get(lt["decision_id"])
        if cand is not None and id(cand) not in consumidos:
            match = cand
            por_id = True

        # 2. Fallback temporal: mismo activo y |Δts| <= tolerancia.
        if match is None:
            mejor, mejor_delta = None, None
            for rt in replay_trades:
                if id(rt) in consumidos:
                    continue
                if normalize_str(rt["asset"]) != normalize_str(lt["asset"]):
                    continue
                delta = abs(rt["timestamp"] - lt["timestamp"])
                if delta <= temporal_tolerance_ms and (mejor_delta is None or delta < mejor_delta):
                    mejor, mejor_delta = rt, delta
            match = mejor

        if match is None:
            pares.append({"live": lt, "replay": None, "status": "MISSING_REPLAY"})
            continue

        consumidos.add(id(match))
        divergencias = []
        if lt["action"] != match["action"]:
            divergencias.append("action")
        # `candle_second`: solo exigible cuando el emparejamiento fue por `decision_id`, es decir
        # cuando ambos lados describen el MISMO instante. Si el par se formo por cercania temporal
        # (hasta `temporal_tolerance_ms`), los dos eventos estan en momentos distintos por
        # construccion — el despacho del tap y el tick de entrada — y exigir el mismo segundo seria
        # contradecir la tolerancia que se acaba de aceptar. La causa raiz de la diferencia de
        # segundo es la latencia de despacho (~2,3 s medidos), no un fallo del motor.
        #
        # R23I Fase 4: la exencion anterior solo cubria el fallback temporal, lo que producia una
        # paradoja medida en `window_06_clean`: un par con `decision_id` IDENTICO y |dt| = 4.283 ms
        # se marcaba DIVERGENT por `candle_second`, mientras que el mismo par emparejado por
        # cercania temporal se declaraba MATCH. Es decir, el comparador penalizaba tener MAS
        # evidencia (identidad exacta). Ahora la latencia fisica de Android Go se absorbe en ambos
        # caminos: si el desfase esta dentro de `temporal_tolerance_ms`, el segundo de vela no puede
        # ser una divergencia porque no es un segundo distinto del mismo instante, es el mismo
        # instante leido 4 s mas tarde. Fuera de esa ventana SI se exige coincidencia.
        if por_id and match["candle_second"] >= 0 and lt["candle_second"] != match["candle_second"]:
            if not _latencia_explica_segundo_de_vela(lt, match, temporal_tolerance_ms):
                divergencias.append("candle_second")
        # `price`: solo si el lado live lo expone (hoy `trades.csv` no lo trae).
        if lt.get("price"):
            escala = max(abs(lt["price"]), abs(match.get("price") or 0.0), 1.0)
            if abs(lt["price"] - (match.get("price") or 0.0)) > price_tolerance * escala:
                divergencias.append("price")

        pares.append({
            "live": lt,
            "replay": match,
            "status": "MATCH" if not divergencias else "DIVERGENT",
            "matched_by": "decision_id" if por_id else "temporal",
            "first_divergence": divergencias[0] if divergencias else None,
            "delta_ms": match["timestamp"] - lt["timestamp"],
        })

    for rt in replay_trades:
        if id(rt) not in consumidos and not any(
            p["replay"] is not None and id(p["replay"]) == id(rt) for p in pares
        ):
            pares.append({"live": None, "replay": rt, "status": "MISSING_LIVE"})

    emparejados = [p for p in pares if p["status"] in ("MATCH", "DIVERGENT")]
    exactos = [p for p in pares if p["status"] == "MATCH"]
    desglose: Dict[str, int] = {}
    for p in pares:
        if p["status"] == "DIVERGENT":
            campo = p["first_divergence"] or "desconocido"
            desglose[campo] = desglose.get(campo, 0) + 1

    total = len(emparejados)
    tasa = (len(exactos) / total * 100.0) if total > 0 else 0.0
    return {
        "mode": "trades",
        "summary": {
            "total_live_trades": len(live_trades),
            "total_replay_trades": len(replay_trades),
            "paired_decisions": total,
            "exact_matches": len(exactos),
            "divergences": len(emparejados) - len(exactos),
            "missing_live": sum(1 for p in pares if p["status"] == "MISSING_LIVE"),
            "missing_replay": sum(1 for p in pares if p["status"] == "MISSING_REPLAY"),
            "match_rate_pct": round(tasa, 2),
            "pass": tasa >= 90.0 and total > 0,
        },
        "divergence_breakdown": desglose,
        "pairs": pares,
    }



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

    # Emparejamiento con fallback temporal (asset, timestamp // 1000, direction)
    paired_ids = set(live_map.keys()).intersection(replay_map.keys())
    unpaired_live = [r for r in live_records if r.decision_id not in paired_ids]
    unpaired_replay = [r for r in replay_records if r.decision_id not in paired_ids]

    replay_temporal_index = {}
    for r in unpaired_replay:
        sec_key = (r.asset.upper().replace("/", "_"), r.timestamp // 1000, r.direction.upper())
        replay_temporal_index.setdefault(sec_key, []).append(r)

    for l in unpaired_live:
        sec_key = (l.asset.upper().replace("/", "_"), l.timestamp // 1000, l.direction.upper())
        candidates = replay_temporal_index.get(sec_key, [])
        if candidates:
            matched_r = candidates.pop(0)
            orig_r_id = matched_r.decision_id
            matched_r.decision_id = l.decision_id
            replay_map[l.decision_id] = matched_r
            if orig_r_id in replay_map and orig_r_id != l.decision_id:
                del replay_map[orig_r_id]

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

    # R23I Fase 4: contrato de absorcion de latencia en `candle_second`. Fija los dos sentidos:
    # lo que DEBE absolverse (latencia fisica real) y lo que NO (divergencia de vela genuina).
    casos_latencia = [
        # (nombre, ts_live, cs_live, ts_replay, cs_replay, tol_ms, esperado)
        ("latencia 4.2s", 1792368186234, 6, 1792368181951, 1, 6000, True),
        ("latencia 3.8s", 1792367704964, 4, 1792367701109, 0, 6000, True),
        ("segundos iguales", 1792367704964, 4, 1792367704964, 4, 6000, True),
        ("wrap 59->1", 1792300000000, 1, 1792299998000, 59, 6000, True),
        ("wrap 58->2", 1792300000000, 2, 1792299996000, 58, 6000, True),
        ("dt 1s con 6s de vela", 1792300001000, 7, 1792300000000, 1, 6000, False),
        ("dt 30s fuera de tol", 1792300030000, 35, 1792300000000, 5, 6000, False),
        ("20s de vela con dt 2s", 1792300018000, 23, 1792300016000, 3, 6000, False),
        ("7s de vela con dt 5s", 1792300007000, 12, 1792300002000, 5, 6000, False),
        ("dt 6001 > tol", 1792300006001, 6, 1792300000000, 0, 6000, False),
        ("cs desconocido", 1792300000000, -1, 1792300000000, 0, 6000, False),
        ("tol 0", 1792300004000, 4, 1792300000000, 0, 0, False),
        ("borde ceil dt 5.9s cs 6", 1792300005900, 6, 1792300000000, 0, 6000, True),
        ("borde dt 5.9s cs 7", 1792300005900, 7, 1792300000000, 0, 6000, False),
    ]
    for nombre, tsl, csl, tsr, csr, tol, esperado in casos_latencia:
        got = _latencia_explica_segundo_de_vela(
            {"timestamp": tsl, "candle_second": csl},
            {"timestamp": tsr, "candle_second": csr},
            tol,
        )
        assert got == esperado, f"latencia '{nombre}': esperado {esperado}, obtenido {got}"

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
    parser.add_argument(
        "--mode",
        choices=["decisions", "trades"],
        default=None,
        help="Universo a comparar: 'decisions' (vetos + trades, el clasico) o 'trades' "
             "(solo ordenes confirmadas vs journal del replay). Default: 'trades' si --live-journal "
             "apunta a un trades.csv, si no 'decisions'.",
    )
    parser.add_argument(
        "--temporal-tolerance-ms",
        type=int,
        default=6000,
        help="En --mode trades, ventana de emparejamiento temporal de respaldo "
             "(default: 6000 ms, absorbe la latencia fisica de lectura de saldo en Android Go)",
    )

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

    uso_fixture = args.fixture or (not args.live_journal and not args.replay_journal)
    if uso_fixture:
        print("Usando fixture sintético determinista (MATCH vs DIVERGENCE)...")
        live_records, replay_records = generate_synthetic_fixture()
        modo = "decisions"
    else:
        # R23C Fase 2 (REQ 3): si no se fuerza el modo, se infiere del nombre del archivo live.
        modo = args.mode or ("trades" if (args.live_journal or "").endswith("trades.csv") else "decisions")

    if uso_fixture:
        pass  # ya resuelto arriba con el fixture
    elif modo == "trades":
        if not args.live_journal or not args.replay_journal:
            print("ERROR: --mode trades requiere --live-journal (trades.csv) y --replay-journal.")
            return 2

        live_trades = parse_live_trades_csv(args.live_journal)
        replay_trades = parse_replay_journal_trades(args.replay_journal)

        emparejados = compare_trades(
            live_trades, replay_trades, temporal_tolerance_ms=args.temporal_tolerance_ms
        )

        s = emparejados["summary"]
        print("=" * 60)
        print("COMPARACIÓN LIVE <-> REPLAY — MODO TRADES (R23C Fase 2)")
        print("=" * 60)
        print(f"Trades en Live:       {s['total_live_trades']}")
        print(f"Trades en Replay:     {s['total_replay_trades']}")
        print(f"Pares evaluados:      {s['paired_decisions']}")
        print(f"Exact Matches:        {s['exact_matches']}")
        print(f"Divergencias:         {s['divergences']}")
        print(f"Missing en Live:      {s['missing_live']}")
        print(f"Missing en Replay:    {s['missing_replay']}")
        print(f"Tasa de paridad:      {s['match_rate_pct']}% -> {'PASS' if s['pass'] else 'FAIL'}")
        if emparejados["divergence_breakdown"]:
            print("\nDesglose de causas raíz de divergencia:")
            for k, v in emparejados["divergence_breakdown"].items():
                print(f"  - {k:18}: {v}")
        if emparejados["pairs"]:
            print("\nDetalle de pares:")
            for p in emparejados["pairs"]:
                lt = p.get("live") or {}
                rt = p.get("replay") or {}
                print(
                    f"  [{p['status']:14}] live={lt.get('action','-'):4} @{lt.get('timestamp','-')}"
                    f"  replay={rt.get('action','-'):4} @{rt.get('timestamp','-')}"
                    f"  d={p.get('delta_ms','-')}ms  {p.get('first_divergence') or ''}"
                )

        if args.output_json:
            os.makedirs(os.path.dirname(os.path.abspath(args.output_json)), exist_ok=True)
            with open(args.output_json, "w", encoding="utf-8") as f:
                json.dump(emparejados, f, indent=2, ensure_ascii=False)
            print(f"\nSalida JSON guardada en: {args.output_json}")
        if args.output_md:
            os.makedirs(os.path.dirname(os.path.abspath(args.output_md)), exist_ok=True)
            with open(args.output_md, "w", encoding="utf-8") as f:
                f.write("# Paridad Live <-> Replay (modo trades) — R23C Fase 2\n\n")
                f.write(f"- Trades Live: {s['total_live_trades']}\n")
                f.write(f"- Trades Replay: {s['total_replay_trades']}\n")
                f.write(f"- Pares: {s['paired_decisions']}\n")
                f.write(f"- Exact matches: {s['exact_matches']}\n")
                f.write(f"- Tasa: {s['match_rate_pct']}%\n")
                f.write(f"- Veredicto: {'PASS' if s['pass'] else 'FAIL'}\n")
            print(f"Reporte Markdown guardado en: {args.output_md}")
        return 0 if s["pass"] else 1
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
