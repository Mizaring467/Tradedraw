#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R23K C2 — REPLAY TICK-A-TICK v2.

QUE ES. Un motor de replay que procesa CADA tick del log del socket y emite una fila de decisión
por tick, con el mismo esquema que `decisions.csv` del lado Live. El objetivo no es predecir
resultados: es que el comparador v2 pueda alinear decisión contra decisión en el mismo instante, en
vez de comparar trades agregados por vela (que es lo que hacía el arnés v1 y lo que producía el
0-50% de paridad).

DIFERENCIA CON EL ARNÉS v1. El v1 emite UNA señal por vela (`lastExecutedCandleMinute`) y simula el
trade a 60 s. Eso colapsa 5-10 decisiones Live en una y hace imposible comparar. El v2 emite lo que
el motor realmente hace: evalúa en cada tick y decide entrar, vetar por agotamiento, bloquear por
anti-patrón, bloquear por riesgo o abstenerse.

FUNCIONES PURAS REPLICADAS. Se portan de Kotlin, copiadas literalmente, las mismas que usa el Live:
  - `calcularPoderesDeMercado`   (TradingEngine.kt)
  - `motivoVetoPorAgotamientoDeCompra` (TradingEngine.kt)
  - `similitudDeFirmas`, `contextoEstrictamenteCoincidente`,
    `motivoBloqueoPorAntiPatron`, `mejorCoincidenciaEnMemoria` (AdaptiveLearningEngine.kt)

Cualquier divergencia entre estas copias y las de Kotlin es un DEFECTO. Por eso el script incluye
`--self-test` con vectores dorados tomados de los tests Kotlin equivalentes.

DETERMINISMO. Mismo log de ticks + mismo estado inicial -> mismas filas, byte a byte. Sin red, sin
azar, sin reloj de pared.

Uso:
    python scripts/validation/replay_v2_tick.py --ticks <ticks_live.csv> --out <dir>
    python scripts/validation/replay_v2_tick.py --self-test
"""

import argparse
import csv
import json
import math
import os
import sys
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# PARÁMETROS — congelados, espejo exacto de las constantes Kotlin
# ---------------------------------------------------------------------------

ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99      # AdaptiveLearningEngine.kt:51
VETO_BUY_UPTREND_MAX_CALL_POWER = 70            # TradingEngine.kt
YOLO_COOLDOWN_MS = 8_000                        # TradingEngine.kt: minSpacingMs en YOLO
CONSERVATIVE_COOLDOWN_MS = 35_000               # riskManager.currentLossStreak > 0
DEFAULT_COOLDOWN_MS = 12_000                    # resto de casos
TRADE_DURATION_MS = 60_000                      # vela M1
SNIPER_WINDOWS = ((0, 5), (58, 59))             # ventana sniper :00-:05 y :58-:59

# Tolerancia de búsqueda del tick de señal en el motor Live.
# El motor emite en el tick exacto; el replay usa el mismo, así que 0 en la práctica.
SIGNAL_MATCH_MS = 0

ESQUEMA_DECISIONS = [
    "decision_id", "trade_id", "timestamp", "asset", "price_at_signal",
    "candle_second", "market_regime", "trend", "action", "score",
    "action_plan", "kind", "detail", "veto_reason",
]


# ---------------------------------------------------------------------------
# FUNCIONES PURAS PORTADAS DE KOTLIN
# ---------------------------------------------------------------------------


def calcular_poderes_de_mercado(
    trend: str,
    dist_support_ratio: float,
    dist_resistance_ratio: float,
    rsi: float,
    is_bullish_impulse: bool,
    is_bearish_impulse: bool,
) -> Tuple[int, int]:
    """
    Espejo literal de `calcularPoderesDeMercado` (TradingEngine.kt:2817).

    Mismos valores base (68/32, 32/68, 50/50), mismos incrementos (+10, +8, +6), mismos umbrales
    (`<= 0.25`, `<= 0.20`, rsi en 40..68 y 32..60), mismo `coerceIn(15,95)` y misma normalización a
    100 con el lado dominante mandando.

    OJO con el orden: el `coerceIn` ocurre ANTES de normalizar, así que el complemento puede quedar
    fuera de [15,95] (p.ej. 92/8). Es la conducta congelada en producción y se replica tal cual.

    Devuelve (callPower, putPower).
    """
    call = 50
    put = 50

    if trend == "UPTREND":
        call, put = 68, 32
        if dist_support_ratio <= 0.25:
            call += 10
        if 40.0 <= rsi <= 68.0:
            call += 8
        if is_bullish_impulse:
            call += 6
    elif trend == "DOWNTREND":
        call, put = 32, 68
        if dist_resistance_ratio <= 0.25:
            put += 10
        if 32.0 <= rsi <= 60.0:
            put += 8
        if is_bearish_impulse:
            put += 6
    else:  # SIDEWAYS
        if dist_support_ratio <= 0.20:
            call, put = 62, 38
        elif dist_resistance_ratio <= 0.20:
            call, put = 38, 62

    call = max(15, min(95, call))
    put = max(15, min(95, put))
    if call >= put:
        put = 100 - call
    else:
        call = 100 - put
    return call, put


def motivo_veto_por_agotamiento(action: str, trend: str, call_power: int) -> Optional[str]:
    """
    Espejo literal de `motivoVetoPorAgotamientoDeCompra` (TradingEngine.kt:2875).

    Solo mira BUY. SELL, `ADAPTIVE_BLOCK`, stake y payout quedan fuera por diseño.
    Devuelve el `detail` del veto, o None si la orden sigue adelante.
    """
    if action != "BUY":
        return None
    if trend != "UPTREND":
        return None
    if call_power < VETO_BUY_UPTREND_MAX_CALL_POWER:
        return None
    return (
        f"VETO_BUY_EXHAUSTION_CLIMAX UPTREND con callPower >= 70% (callPower={call_power})"
    )


def _firma(
    action: str,
    trend: str,
    ds: float,
    dr: float,
    vel: float,
    bull: bool,
    bear: bool,
    sideways: bool,
    tight: bool,
    strategy: str,
    candle_second: int,
) -> Dict[str, Any]:
    return {
        "action": action, "trend": trend, "ds": ds, "dr": dr, "vel": vel,
        "bull": bull, "bear": bear, "sideways": sideways, "tight": tight,
        "strategy": strategy, "candle_second": candle_second,
    }


def similitud_de_firmas(a: Dict[str, Any], b: Dict[str, Any]) -> float:
    """
    Espejo literal de `similitudDeFirmas` (AdaptiveLearningEngine.kt:474).

    Pesos: acción 0.20, tendencia 0.20, proximidad S/R 0.30, velocidad/impulso 0.15, régimen 0.15.
    """
    score = 0.0
    if a["action"] == b["action"]:
        score += 0.20
    if a["trend"] == b["trend"]:
        score += 0.20

    ds_diff = abs(a["ds"] - b["ds"])
    dr_diff = abs(a["dr"] - b["dr"])
    sr_sim = ((1.0 - ds_diff) + (1.0 - dr_diff)) / 2.0
    score += max(0.0, min(1.0, sr_sim)) * 0.30

    if a["bull"] == b["bull"] and a["bear"] == b["bear"]:
        score += 0.15
    else:
        vel_diff = abs(a["vel"] - b["vel"])
        vel_sim = max(0.0, min(1.0, 1.0 - (vel_diff / 4.0)))
        score += vel_sim * 0.15

    if a["sideways"] == b["sideways"] and a["tight"] == b["tight"]:
        score += 0.15

    return max(0.0, min(1.0, score))


def contexto_estrictamente_coincidente(a: Dict[str, Any], b: Dict[str, Any]) -> bool:
    """
    Espejo literal de `contextoEstrictamenteCoincidente` (AdaptiveLearningEngine.kt:515).

    Compuerta H6: misma acción, misma tendencia, mismo régimen lateral y S/R dentro de ±0.10.
    """
    if a["action"] != b["action"]:
        return False
    if a["trend"] != b["trend"]:
        return False
    if a["sideways"] != b["sideways"]:
        return False
    return abs(a["ds"] - b["ds"]) <= 0.10 and abs(a["dr"] - b["dr"]) <= 0.10


def motivo_bloqueo_por_antipatron(
    candidata: Dict[str, Any],
    firma_perdedora: Optional[Dict[str, Any]],
    similitud_maxima: float,
) -> Optional[str]:
    """Espejo literal de `motivoBloqueoPorAntiPatron` (AdaptiveLearningEngine.kt:540)."""
    if similitud_maxima < ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD:
        return None
    if firma_perdedora is None:
        return None
    if not contexto_estrictamente_coincidente(candidata, firma_perdedora):
        return None
    sim_pct = int(similitud_maxima * 100)
    return (
        f"ADAPTIVE_BLOCK [sim:{sim_pct}%] Anti-patro\u0301n perdedor recurrente "
        f"({sim_pct}% similitud en contexto S/R={int(firma_perdedora['ds'] * 100)}%) - "
        f"inversio\u0301n deshabilitada"
    )


def mejor_coincidencia_en_memoria(
    candidata: Dict[str, Any], memoria: List[Dict[str, Any]]
) -> Tuple[float, Optional[Dict[str, Any]]]:
    """
    Espejo literal de `mejorCoincidenciaEnMemoria` (AdaptiveLearningEngine.kt:561).

    Solo las últimas 20 pérdidas y máximo ESTRICTO (`>`), no `>=`: ante empate se queda la primera,
    y ese detalle cambia el motivo del bloqueo aunque no la decisión.
    """
    sim_max = 0.0
    mejor = None
    for perdida in memoria[-20:]:
        sim = similitud_de_firmas(candidata, perdida)
        if sim > sim_max:
            sim_max = sim
            mejor = perdida
    return sim_max, mejor


def en_ventana_sniper(timestamp_ms: int) -> bool:
    """`:00-:05` y `:58-:59` de cada minuto, igual que `executeHeadlessTrade`."""
    sec = (timestamp_ms // 1000) % 60
    return any(lo <= sec <= hi for lo, hi in SNIPER_WINDOWS)


# ---------------------------------------------------------------------------
# MÁQUINA DE ESTADOS MÍNIMA
# ---------------------------------------------------------------------------


class MaquinaEstados:
    """
    Estado mínimo que determina si una señal puede entrar: orden abierta, cooldown y memoria.

    Se replica SOLO lo que cambia el veredicto. No se simula el envío real ni el settlement: eso
    depende del broker y no es reproducible offline. Lo que sí se reproduce es la cadena de guards
    que decide entre entrar, vetar o bloquear.
    """

    def __init__(self, submode: str = "YOLO"):
        self.submode = submode
        self.orden_abierta_hasta_ms: int = 0      # 60 s de operación en curso
        self.ultima_resolucion_ms: int = 0
        self.memoria_perdidas: List[Dict[str, Any]] = []
        self.trades_abiertos: List[Dict[str, Any]] = []

    def cooldown_ms(self) -> int:
        if self.submode == "YOLO":
            return YOLO_COOLDOWN_MS
        return CONSERVATIVE_COOLDOWN_MS

    def motivo_bloqueo_riesgo(self, ahora_ms: int) -> Optional[str]:
        """
        Orden de guards de `executeHeadlessTrade`:
          1. operación en curso (60 s desde la entrada)
          2. cooldown post-resolución
        Devuelve el `detail` del veto de riesgo, o None si puede seguir.
        """
        if ahora_ms < self.orden_abierta_hasta_ms:
            restante = (self.orden_abierta_hasta_ms - ahora_ms) / 1000.0
            return f"RISK_LIMIT Operacio\u0301n abierta en curso ({restante:.0f}s)"
        if self.ultima_resolucion_ms > 0:
            desde = ahora_ms - self.ultima_resolucion_ms
            if desde < self.cooldown_ms():
                return f"COOLDOWN {desde}ms"
        return None

    def registrar_entrada(self, ahora_ms: int, firma: Dict[str, Any]) -> None:
        self.orden_abierta_hasta_ms = ahora_ms + TRADE_DURATION_MS
        self.trades_abiertos.append({"entrada_ms": ahora_ms, "firma": firma})

    def resolver_vencidas(self, ahora_ms: int) -> None:
        """Cierra las operaciones cuyo minuto ya venció y alimenta la memoria con las perdedoras."""
        pendientes = []
        for t in self.trades_abiertos:
            if ahora_ms >= t["entrada_ms"] + TRADE_DURATION_MS:
                self.ultima_resolucion_ms = t["entrada_ms"] + TRADE_DURATION_MS
                # El desenlace real necesita el precio a 60 s, que aqui no se conoce: el replay v2
                # mide DECISIONES, no resultados. Se alimenta la memoria solo si el llamador marcó
                # la firma como perdedora (ver `marcar_resultado`).
                if t.get("perdida"):
                    self.memoria_perdidas.append(t["firma"])
            else:
                pendientes.append(t)
        self.trades_abiertos = pendientes


# ---------------------------------------------------------------------------
# MOTOR DE REPLAY
# ---------------------------------------------------------------------------


def cargar_ticks(ruta: str) -> List[Dict[str, Any]]:
    """Lee `ts,ric,price` y añade la velocidad calculada como en el arnés v1."""
    ticks: List[Dict[str, Any]] = []
    with open(ruta, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            try:
                ticks.append({"ts": int(row["ts"]), "ric": row["ric"], "price": float(row["price"])})
            except (KeyError, TypeError, ValueError):
                continue
    ticks.sort(key=lambda t: t["ts"])

    prev_ts, prev_price = 0, 0.0
    for t in ticks:
        dt = (t["ts"] - prev_ts) / 1000.0 if prev_ts and prev_price else 0.0
        vel = ((t["price"] - prev_price) / dt) if dt > 0 else 0.0
        t["velocity"] = vel
        is_idx = "IDX" in t["ric"].upper()
        escala = prev_price if prev_price > 0 else t["price"]
        umbral = 0.000008 if is_idx else escala * 0.00008
        t["bull"] = vel > umbral
        t["bear"] = vel < -umbral
        prev_ts, prev_price = t["ts"], t["price"]
    return ticks


def procesar_tick_v2(
    tick: Dict[str, Any],
    estado: MaquinaEstados,
    trend: str,
    dist_sup: float,
    dist_res: float,
    rsi: float,
    accion: str = "BUY",
    strategy: str = "AUTO_ADAPTIVE",
) -> Dict[str, Any]:
    """
    Evalúa UN tick y devuelve la fila de decisión, con el esquema exacto de `decisions.csv`.

    Orden de guards, espejo de `executeHeadlessTrade`:
      0. ventana sniper (fuera de ella el motor ni evalúa)
      1. operación en curso / cooldown
      2. VETO_BUY_EXHAUSTION_CLIMAX   (antes del adaptativo, a propósito)
      3. ADAPTIVE_BLOCK
      4. entrada

    El orden importa: un veto de política no debe consumir la evaluación del motor adaptativo.
    """
    ts = tick["ts"]
    sec = (ts // 1000) % 60
    call_power, _ = calcular_poderes_de_mercado(
        trend, dist_sup, dist_res, rsi, tick["bull"], tick["bear"]
    )

    fila = {
        "decision_id": f"r2-{ts}-{accion}",
        "trade_id": "",
        "timestamp": ts,
        "asset": tick["ric"],
        "price_at_signal": tick["price"],
        "candle_second": sec,
        "market_regime": "SIDEWAYS" if trend == "SIDEWAYS" else "TRENDING",
        "trend": trend,
        "action": accion,
        "score": "",
        "action_plan": "",
        "kind": "",
        "detail": "",
        "veto_reason": "OTRO",
    }

    # 0. Fuera de la ventana sniper el motor no evalúa: no hay decisión que registrar.
    if not en_ventana_sniper(ts):
        fila["kind"] = "SKIP"
        fila["action"] = "NONE"
        fila["detail"] = "FUERA_VENTANA_SNIPER"
        fila["veto_reason"] = "NONE"
        return fila

    # 1. Guards de riesgo.
    motivo_riesgo = estado.motivo_bloqueo_riesgo(ts)
    if motivo_riesgo:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_riesgo
        return fila

    # 2. Veto de agotamiento (política, antes del adaptativo).
    motivo_agot = motivo_veto_por_agotamiento(accion, trend, call_power)
    if motivo_agot:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_agot
        return fila

    # 3. Anti-patrón adaptativo.
    firma = _firma(
        action=accion, trend=trend, ds=dist_sup, dr=dist_res, vel=tick["velocity"],
        bull=tick["bull"], bear=tick["bear"],
        sideways=(trend == "SIDEWAYS"), tight=False,
        strategy=strategy, candle_second=sec,
    )
    sim_max, coincidencia = mejor_coincidencia_en_memoria(firma, estado.memoria_perdidas)
    motivo_adapt = motivo_bloqueo_por_antipatron(firma, coincidencia, sim_max)
    if motivo_adapt:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_adapt
        return fila

    # 4. Entrada.
    estado.registrar_entrada(ts, firma)
    fila["kind"] = "ORDER_STATE"
    fila["action_plan"] = f"TAP {accion}"
    fila["detail"] = f"ENTRADA {accion} callPower={call_power}"
    fila["veto_reason"] = "NONE"
    return fila


def cargar_muestras_contexto(ruta: str) -> List[Dict[str, Any]]:
    """
    Lee `live_samples.jsonl` y extrae la serie de contexto REAL observada por el motor:
    `(timestamp_ms, trend, callPower, isMarketSideways)`.

    POR QUE. El log de ticks solo trae precio; la tendencia y el callPower los infiere el
    `SyntheticCandleEngine` del telefono y no se pueden recalcular offline sin replicar su
    máquina de velas entera. Pero el monitor SÍ los muestrea cada ~10 s y los guarda. Usar esa
    serie es infinitamente más fiel que suponer un régimen fijo: con una tendencia inventada el
    replay produce 409 vetos de agotamiento seguidos, que es un artefacto del supuesto y no un
    hallazgo.
    """
    muestras: List[Dict[str, Any]] = []
    if not os.path.exists(ruta):
        return muestras
    with open(ruta, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                d = json.loads(line)
            except json.JSONDecodeError:
                continue
            analysis = (d.get("status") or {}).get("analysis") or {}
            ts = d.get("timestamp_ms")
            if ts is None or not analysis:
                continue
            muestras.append(
                {
                    "ts": int(ts),
                    "trend": str(analysis.get("trend", "SIDEWAYS")),
                    "callPower": analysis.get("callPower"),
                    "sideways": bool(analysis.get("isMarketSideways", False)),
                }
            )
    muestras.sort(key=lambda m: m["ts"])
    return muestras


def contexto_en(muestras: List[Dict[str, Any]], ts_ms: int) -> Optional[Dict[str, Any]]:
    """
    Muestra de contexto más cercana a `ts_ms` (búsqueda binaria), o None si no hay.

    No se impone tolerancia máxima: la serie de muestras cubre la ventana completa y el monitor
    muestrea cada 10 s, así que el vecino más cercano está a <=5 s salvo en huecos. Un hueco se
    detecta aguas abajo comparando contra la duración real de la ventana.
    """
    if not muestras:
        return None
    lo, hi = 0, len(muestras) - 1
    while lo < hi:
        mid = (lo + hi) // 2
        if muestras[mid]["ts"] < ts_ms:
            lo = mid + 1
        else:
            hi = mid
    mejor, mejor_d = None, None
    for idx in (lo - 1, lo):
        if 0 <= idx < len(muestras):
            d = abs(muestras[idx]["ts"] - ts_ms)
            if mejor_d is None or d < mejor_d:
                mejor_d, mejor = d, muestras[idx]
    return mejor


def ejecutar_replay(
    ticks: List[Dict[str, Any]],
    out_dir: str,
    submode: str = "YOLO",
    trend: str = "UPTREND",
    accion: str = "BUY",
    muestras: Optional[List[Dict[str, Any]]] = None,
) -> str:
    """
    Procesa la serie completa y escribe `decisions_replay.csv`.

    Si se pasa `muestras` (de `live_samples.jsonl`), la tendencia y el callPower de cada tick salen
    del contexto observado por el motor en el instante más cercano. Si no, se usa el `trend` fijo
    como aproximación gruesa, y el informe debe declararlo: un régimen inventado hace que todos los
    ticks caigan en el mismo bucket y el resultado es un artefacto del supuesto.
    """
    estado = MaquinaEstados(submode=submode)
    filas: List[Dict[str, Any]] = []
    dist_sup, dist_res, rsi = 0.50, 0.50, 50.0
    ticks_sin_contexto = 0

    for tick in ticks:
        estado.resolver_vencidas(tick["ts"])
        trend_tick = trend
        if muestras:
            ctx = contexto_en(muestras, tick["ts"])
            if ctx is None:
                ticks_sin_contexto += 1
            else:
                trend_tick = ctx["trend"]
        filas.append(
            procesar_tick_v2(tick, estado, trend_tick, dist_sup, dist_res, rsi, accion=accion)
        )

    if muestras and ticks_sin_contexto:
        print(f"AVISO: {ticks_sin_contexto} ticks sin muestra de contexto asociada.")

    os.makedirs(out_dir, exist_ok=True)
    ruta = os.path.join(out_dir, "decisions_replay.csv")
    with open(ruta, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=ESQUEMA_DECISIONS)
        w.writeheader()
        for fila in filas:
            w.writerow(fila)
    return ruta


# ---------------------------------------------------------------------------
# SELF-TEST
# ---------------------------------------------------------------------------


def self_test() -> int:
    """Vectores dorados tomados de los tests Kotlin equivalentes."""
    print("=" * 70)
    print("SELF-TEST — replay_v2_tick")
    print("=" * 70)
    fallos = []

    def check(nombre, cond, detalle=""):
        if cond:
            print(f"  [OK]    {nombre}")
        else:
            fallos.append(nombre)
            print(f"  [FALLA] {nombre} {detalle}")

    # --- calcular_poderes_de_mercado: mismos vectores que PoderesDeMercadoEquivalenciaTest
    check(
        "UPTREND con todos los refuerzos -> 92/8",
        calcular_poderes_de_mercado("UPTREND", 0.10, 0.80, 50.0, True, False) == (92, 8),
    )
    check(
        "UPTREND en crudo -> 68/32",
        calcular_poderes_de_mercado("UPTREND", 0.90, 0.90, 90.0, False, False) == (68, 32),
    )
    check(
        "DOWNTREND con todos los refuerzos -> 8/92",
        calcular_poderes_de_mercado("DOWNTREND", 0.90, 0.10, 45.0, False, True) == (8, 92),
    )
    check(
        "SIDEWAYS cerca de soporte -> 62/38",
        calcular_poderes_de_mercado("SIDEWAYS", 0.20, 0.90, 50.0, False, False) == (62, 38),
    )
    check(
        "SIDEWAYS sin niveles cercanos -> 50/50",
        calcular_poderes_de_mercado("SIDEWAYS", 0.90, 0.90, 50.0, False, False) == (50, 50),
    )
    # Call+Put siempre 100
    for trend in ("UPTREND", "DOWNTREND", "SIDEWAYS"):
        for rsi in (10.0, 35.0, 50.0, 75.0):
            c, p = calcular_poderes_de_mercado(trend, 0.15, 0.15, rsi, True, True)
            check(f"CALL+PUT=100 ({trend}, rsi={rsi})", c + p == 100, f"{c}+{p}")

    # --- veto de agotamiento
    check("BUY+UPTREND callPower 70 veta", motivo_veto_por_agotamiento("BUY", "UPTREND", 70) is not None)
    check("BUY+UPTREND callPower 69 NO veta", motivo_veto_por_agotamiento("BUY", "UPTREND", 69) is None)
    check("SELL+UPTREND callPower 95 NO veta", motivo_veto_por_agotamiento("SELL", "UPTREND", 95) is None)
    check("BUY+DOWNTREND callPower 95 NO veta", motivo_veto_por_agotamiento("BUY", "DOWNTREND", 95) is None)
    motivo = motivo_veto_por_agotamiento("BUY", "UPTREND", 86)
    check("detail del veto lleva callPower", "callPower=86" in (motivo or ""), motivo or "")
    check(
        "token indexable del veto",
        (motivo or "").split(" ")[0] == "VETO_BUY_EXHAUSTION_CLIMAX",
        motivo or "",
    )

    # --- similitud
    base = _firma("BUY", "UPTREND", 0.5, 0.5, 0.0, False, False, False, False, "AUTO_ADAPTIVE", 1)
    check("firma consigo misma -> 1.0", abs(similitud_de_firmas(base, base) - 1.0) < 1e-9)
    otra_accion = _firma("SELL", "UPTREND", 0.5, 0.5, 0.0, False, False, False, False, "AUTO_ADAPTIVE", 1)
    s = similitud_de_firmas(base, otra_accion)
    check("distinta accion -> 0.80", abs(s - 0.80) < 1e-9, f"{s}")

    # --- compuerta estricta
    cerca = _firma("BUY", "UPTREND", 0.55, 0.55, 0.0, False, False, False, False, "AUTO_ADAPTIVE", 1)
    lejos = _firma("BUY", "UPTREND", 0.70, 0.50, 0.0, False, False, False, False, "AUTO_ADAPTIVE", 1)
    check("S/R dentro de 0.10 -> coincide", contexto_estrictamente_coincidente(base, cerca))
    check("S/R fuera de 0.10 -> no coincide", not contexto_estrictamente_coincidente(base, lejos))

    # --- bloqueo
    check(
        "sim 0.98 no bloquea",
        motivo_bloqueo_por_antipatron(base, base, 0.98) is None,
    )
    check(
        "sim 0.99 con contexto estricto bloquea",
        motivo_bloqueo_por_antipatron(base, base, 0.99) is not None,
    )
    check(
        "sim 0.995 con contexto NO estricto no bloquea",
        motivo_bloqueo_por_antipatron(base, lejos, 0.995) is None,
    )

    # --- memoria: solo ultimas 20
    memoria = [base] * 5 + [lejos] * 20
    sim_max, _ = mejor_coincidencia_en_memoria(base, memoria)
    check("memoria mira solo las ultimas 20", sim_max < 0.99, f"sim={sim_max}")

    # --- ventana sniper. Los ts se construyen desde el inicio de un minuto exacto para que el
    # segundo sea el que se quiere probar (con un ts arbitrario el % 60 da cualquier cosa).
    inicio_min = (1_000_000_000 // 60_000) * 60_000
    check("ts en :00 -> sniper", en_ventana_sniper(inicio_min))
    check("ts en :03 -> sniper", en_ventana_sniper(inicio_min + 3_000))
    check("ts en :30 -> fuera", not en_ventana_sniper(inicio_min + 30_000))
    check("ts en :59 -> sniper", en_ventana_sniper(inicio_min + 59_000))
    check("ts en :06 -> fuera", not en_ventana_sniper(inicio_min + 6_000))
    check("ts en :57 -> fuera", not en_ventana_sniper(inicio_min + 57_000))

    print("-" * 70)
    if fallos:
        print(f"RESULTADO: FALLA ({len(fallos)})")
        return 1
    print("RESULTADO: PASS")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Replay tick-a-tick v2 (R23K C2)")
    parser.add_argument("--ticks", help="Ruta a ticks_live.csv")
    parser.add_argument("--samples", default="", help="Ruta a live_samples.jsonl (contexto real de trend)")
    parser.add_argument("--out", default="", help="Directorio de salida")
    parser.add_argument("--submode", default="YOLO", choices=["YOLO", "CONSERVATIVE"])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if not args.ticks:
        parser.error("--ticks es obligatorio (o usa --self-test)")

    out = args.out or os.path.dirname(os.path.abspath(args.ticks))
    ticks = cargar_ticks(args.ticks)
    print(f"Ticks cargados: {len(ticks)}")

    muestras = []
    if args.samples:
        muestras = cargar_muestras_contexto(args.samples)
        print(f"Muestras de contexto: {len(muestras)}")
    else:
        print("AVISO: sin --samples, la tendencia se fija en UPTREND. El resultado NO es fiel.")

    ruta = ejecutar_replay(ticks, out, submode=args.submode, muestras=muestras or None)
    print(f"Replay escrito en: {ruta}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
