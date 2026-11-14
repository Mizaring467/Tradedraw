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

R24 TRACK B — B1..B4 (SOLO INFRAESTRUCTURA, ESTRATEGIA CONGELADA). El barrido W05-W11 demostro que
el replay v2 no podia reproducir `ADAPTIVE_BLOCK` (0% de 57 casos) ni la compuerta `V1` (0% de 12)
porque le faltaban tres piezas de INFRAESTRUCTURA, no de politica:

  B1. Memoria de anti-patrones REAL, cargada desde `adaptive_learning_state.json` del telefono
      (`--adaptive-state` / `ADAPTIVE_STATE_JSON`) y purgada con las MISMAS reglas de `loadState`
      (firmas genericas en el 50% y TTL de 24 h medidas contra el INICIO DE LA VENTANA, no contra
      el reloj de pared: el replay tiene que ser determinista). La memoria solo es visible para
      ticks con `ts >= ts_de_la_firma`; una firma futura NUNCA entra en la decision (anti
      look-ahead). Ademas crece en el mismo punto que en vivo: al LIQUIDAR, no al entrar.
  B2. Settlement y cooldown. `lastTradeResolutionTime` se actualiza cuando el Live resolvio la
      operacion (ledger de settlements: `--settlements`, journal o transiciones
      `hasPendingTrade true->false` de `live_samples.jsonl`), no 60 s despues de la entrada. El
      cooldown se cuenta desde ese instante, con el mismo `minSpacingMs` del motor.
  B3. Compuerta V1: port literal de `HudCoherence.actionConflict` con el `srStatus` y el `plan` de
      `getEngineReasoning`, colocada en el MISMO punto del pipeline que en el Live.
  B4. Tests deterministas en `scripts/validation/test_replay_v2_guards.py` (+ vectores en
      `--self-test`) para memoria inicial, settlement, cooldown, V1, orden de guards y ausencia de
      look-ahead.

ORDEN DE GUARDS (espejo de `executeHeadlessTrade`, y esta es la parte que estaba mal):
  0. ventana sniper
  1. cooldown post-RESOLUCION                     (lastTradeResolutionTime)
  2. VETO_BUY_EXHAUSTION_CLIMAX                   (politica, antes del adaptativo)
  3. ADAPTIVE_BLOCK                               (memoria real)
  4. RISK_LIMIT operacion abierta                 (riskManager.canExecuteTrade)
  5. V1 exclusividad S/R <-> plan                 (HudCoherence.actionConflict)
  6. entrada

La operacion abierta se evalua DESPUES del adaptativo, porque en el motor real
`riskManager.canExecuteTrade` es el paso 6 del headless y no el guard de cooldown. Confundir ambos
es lo que convertia 23 `ADAPTIVE_BLOCK` reales en `RISK_LIMIT`/`COOLDOWN` en el replay.

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
# B1 — MEMORIA DE ANTI-PATRONES REAL (espejo de `AdaptiveLearningEngine.loadState`)
# ---------------------------------------------------------------------------

# Reglas de purga de `loadState` (AdaptiveLearningEngine.kt:369-390). Firmas "genericas" (las que se
# quedaron atrapadas en el 50% por datos nulos) y TTL de 24 h.
MEMORIA_PURGA_RATIO_50 = 0.02
MEMORIA_TTL_MS = 24 * 60 * 60 * 1000


def firma_desde_loss_signature(item: Dict[str, Any]) -> Dict[str, Any]:
    """
    Convierte una entrada de `lossSignatures` (JSON del telefono) a la firma interna del replay.

    Mismos campos que `TradeContextSignature` y mismo mapeo que `ReplayBacktestTest`:
    `distSup -> ds`, `distRes -> dr`, `bullishImp/bearishImp -> bull/bear`, `strat -> strategy`,
    `sec -> candle_second`, `tickVelNorm -> vel`. `ts` se conserva porque gobierna la visibilidad
    temporal (anti look-ahead).
    """
    return {
        "action": str(item.get("action", "BUY")),
        "trend": str(item.get("trend", "SIDEWAYS")),
        "ds": float(item.get("distSup", 0.0) or 0.0),
        "dr": float(item.get("distRes", 0.0) or 0.0),
        "vel": float(item.get("tickVelNorm", 0.0) or 0.0),
        "bull": bool(item.get("bullishImp", False)),
        "bear": bool(item.get("bearishImp", False)),
        "sideways": bool(item.get("sideways", False)),
        "tight": bool(item.get("tight", False)),
        "strategy": str(item.get("strat", "AUTO_ADAPTIVE")),
        "candle_second": int(item.get("sec", 0) or 0),
        "ts": int(item.get("ts", 0) or 0),
    }


def firma_es_generica(firma: Dict[str, Any]) -> bool:
    """
    Firma atrapada en el 50/50: `loadState` la descarta porque no describe ningun contexto real.
    Se mira `ds` y `dr` con la MISMA tolerancia de 0.02.
    """
    return (
        abs(firma["ds"] - 0.50) < MEMORIA_PURGA_RATIO_50
        and abs(firma["dr"] - 0.50) < MEMORIA_PURGA_RATIO_50
    )


def cargar_memoria_adaptativa(
    ruta: str, ventana_inicio_ms: int, ttl_ms: int = MEMORIA_TTL_MS
) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
    """
    Carga `adaptive_learning_state.json` y devuelve las firmas que el motor TENIA al abrir la ventana.

    TRES GARANTIAS, en este orden:

      1. **Misma purga que el Live**: se descartan las firmas genericas (ds y dr dentro de ±0.02 de
         0.50) y las caducadas por TTL de 24 h. El TTL se mide contra `ventana_inicio_ms`, NO contra
         el reloj de pared: con `time.time()` el resultado cambiaria entre corridas y el replay
         dejaria de ser determinista.
      2. **Sin look-ahead**: se descartan las firmas con `ts > ventana_inicio_ms`. Un state exportado
         DESPUES de la ventana puede contener perdidas ocurridas durante ella; cargarlas seria
         regalarle al replay informacion del futuro.
      3. **Sin reconstruccion artificial**: si el JSON no trae `lossSignatures`, se devuelve lista
         vacia y el conteo lo declara. NO se fabrican firmas a partir de los trades del propio log.

    Devuelve (firmas, estadisticas) con `cargadas`, `purgadas_50`, `purgadas_ttl`, `purgadas_futuro`.
    """
    stats = {"cargadas": 0, "purgadas_50": 0, "purgadas_ttl": 0, "purgadas_futuro": 0}
    firmas: List[Dict[str, Any]] = []
    if not ruta or not os.path.exists(ruta):
        return firmas, stats
    try:
        with open(ruta, "r", encoding="utf-8") as f:
            raiz = json.load(f)
    except (OSError, json.JSONDecodeError):
        return firmas, stats
    array = raiz.get("lossSignatures") or []
    for item in array:
        try:
            firma = firma_desde_loss_signature(item)
        except (TypeError, ValueError):
            continue
        if firma["ts"] > ventana_inicio_ms:
            stats["purgadas_futuro"] += 1
            continue
        if firma_es_generica(firma):
            stats["purgadas_50"] += 1
            continue
        if ventana_inicio_ms - firma["ts"] > ttl_ms:
            stats["purgadas_ttl"] += 1
            continue
        firmas.append(firma)
    stats["cargadas"] = len(firmas)
    return firmas, stats


def cargar_settlements(ruta: str) -> List[Dict[str, Any]]:
    """
    B2 — LEDGER DE LIQUIDACIONES REALES.

    Formato aceptado (CSV, cabecera obligatoria): `ts,result` y opcionales `action`, `entry_ts`.
      - `ts`       instante en que el LIVE resolvio la operacion (`lastTradeResolutionTime`).
      - `result`   `WIN` | `LOSS` | `TIE` (mismo vocabulario que el journal).
      - `action`   direccion de la orden resuelta, si se conoce.
      - `entry_ts` instante de entrada, opcional. Si viene, la firma de la perdida se activa en `ts`;
                   si no viene, la perdida no puede alimentar la memoria (no hay firma que meter).

    Sin este ledger, el cooldown del replay tendria que inventarse un instante de resolucion
    (`entrada + 60 s`), que es exactamente la reconstruccion artificial que B2 elimina.
    """
    salida: List[Dict[str, Any]] = []
    if not ruta or not os.path.exists(ruta):
        return salida
    with open(ruta, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            try:
                ts = int(row["ts"])
                resultado = str(row["result"]).strip().upper()
            except (KeyError, TypeError, ValueError):
                continue
            if resultado not in ("WIN", "LOSS", "TIE"):
                continue
            salida.append(
                {
                    "ts": ts,
                    "result": resultado,
                    "action": (row.get("action") or "").strip().upper(),
                    "entry_ts": int(row["entry_ts"]) if (row.get("entry_ts") or "").strip().isdigit() else 0,
                }
            )
    salida.sort(key=lambda s: s["ts"])
    return salida


def settlements_desde_muestras(muestras: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Deriva liquidaciones de las transiciones `hasPendingTrade true -> false` de `live_samples.jsonl`.

    QUE ES Y QUE NO ES. Es el instante en que el MONITOR vio desaparecer la orden pendiente, que es
    la mejor aproximacion disponible a `lastTradeResolutionTime` cuando no hay journal. **No trae
    resultado**: el estado de la orden sale (`NINGUNA`) pero no si gano o perdio, asi que estas
    entradas NO alimentan la memoria de anti-patrones (marcadas `result = "UNKNOWN"`). Se usan solo
    para el reloj de cooldown, que es lo unico que necesitan.

    Se ignoran las transiciones con `orderState = NO_PROCESADA`: esa orden nunca llego al broker, no
    hubo liquidacion y en el motor real no se toca `lastTradeResolutionTime`.
    """
    salida: List[Dict[str, Any]] = []
    previo: Optional[bool] = None
    for m in muestras:
        st = m.get("risk") or {}
        pendiente = bool(st.get("hasPendingTrade", False))
        estado = str(st.get("orderState", "")).upper()
        if previo is True and not pendiente:
            if estado == "NO_PROCESADA":
                pass
            else:
                salida.append(
                    {"ts": m["ts"], "result": "UNKNOWN", "action": "", "entry_ts": 0,
                     "fuente": "live_samples"}
                )
        previo = pendiente
    return salida


# ---------------------------------------------------------------------------
# B3 — COMPUERTA V1 (espejo de `HudCoherence.actionConflict` + `getEngineReasoning`)
# ---------------------------------------------------------------------------

SR_TOUCH_LIMIT = 0.15            # HudCoherence.TOUCH_RATIO_LIMIT / getEngineReasoning
SR_NEAR_LIMIT = 0.30             # "Cerca de Soporte/Resistencia"


def sr_status_desde_distancias(dist_sup: float, dist_res: float, hay_niveles: bool) -> str:
    """
    Port literal del bloque "3. Estado del Filtro S/R" de `getEngineReasoning` (TradingEngine.kt).

    `hay_niveles` es el `sup > 0.0 && res > 0.0 && tick != null` del original. Sin niveles el Live
    pinta un guion y esa cadena NO contiene "CALL Optimo" ni "PUT Optimo", asi que no puede disparar
    V1. Se usan caracteres literales (no secuencias de escape) para que el texto sea comparable con
    el del Live byte a byte.
    """
    if not hay_niveles:
        return "—"
    if dist_sup <= SR_TOUCH_LIMIT:
        return "🟢 Toque Directo en Soporte [CALL Óptimo]"
    if dist_res <= SR_TOUCH_LIMIT:
        return "🔴 Toque Directo en Resistencia [PUT Óptimo]"
    if dist_sup <= SR_NEAR_LIMIT:
        return f"🟢 Cerca de Soporte ({int(dist_sup * 100)}%)"
    if dist_res <= SR_NEAR_LIMIT:
        return f"🔴 Cerca de Resistencia ({int(dist_res * 100)}%)"
    return "⚪ Centro del Canal S/R (Neutral)"


def action_plan_desde_estado(
    trend: str,
    dist_sup: float,
    dist_res: float,
    accion_orden_abierta: str,
    restante_seg: int,
    choppy: bool,
    submode: str,
) -> str:
    """
    Port literal del bloque "4. Plan de Accion Tactico" de `getEngineReasoning`, mismo orden de ramas.

    La cuenta atras (`Hasta exp: Ns`) se recibe ya calculada: es prosa, no entra en la compuerta,
    pero la direccion de la orden abierta SI, y es la unica rama donde el plan puede contradecir al
    S/R sin que el mercado lo haga.
    """
    if accion_orden_abierta:
        act = "CALL ▲" if accion_orden_abierta == "BUY" else "PUT ▼"
        return f"⏳ Trade Activo: {act} · Hasta exp: {restante_seg}s"
    if choppy and submode != "YOLO":
        if dist_sup <= 0.25:
            return (
                "🛡️ Anti-Chop Activo · Cerca de Soporte "
                f"({int(dist_sup * 100)}%): Buscando Mecha Rechazo CALL"
            )
        if dist_res <= 0.25:
            return (
                "🛡️ Anti-Chop Activo · Cerca de Resistencia "
                f"({int(dist_res * 100)}%): Buscando Mecha Rechazo PUT"
            )
        return (
            "🛡️ Filtro Anti-Chop Activo: Consolidación/CHOP elevado. "
            "Esperando extremos S/R o ruptura."
        )
    if trend == "DOWNTREND" and dist_sup <= SR_TOUCH_LIMIT:
        return "⛔ Venta vetada sobre soporte: esperando rebote o ruptura confirmada."
    if trend == "UPTREND" and dist_res <= SR_TOUCH_LIMIT:
        return "⛔ Compra vetada bajo resistencia: esperando rechazo o ruptura confirmada."
    if trend == "UPTREND":
        return "🎯 Plan: Buscar retroceso leve a soporte para ejecutar CALL en la ventana :01s - :05s."
    if trend == "DOWNTREND":
        return "🎯 Plan: Buscar retroceso leve a resistencia para ejecutar PUT en la ventana :01s - :05s."
    if dist_sup <= SR_NEAR_LIMIT:
        return (
            f"🎯 Plan: Cerca de Soporte ({int(dist_sup * 100)}%). "
            "Esperando gatillo CALL en la ventana :01s - :05s."
        )
    if dist_res <= SR_NEAR_LIMIT:
        return (
            f"🎯 Plan: Cerca de Resistencia ({int(dist_res * 100)}%). "
            "Esperando gatillo PUT en la ventana :01s - :05s."
        )
    return "🎯 Plan: Esperando confirmación de nivel S/R o impulso direccional de ticks."


def accion_conflictiva(sr_status: str, action_plan: str) -> bool:
    """
    Port literal de `HudCoherence.actionConflict` (HudCoherence.kt:75).

    True cuando el filtro S/R y el plan piden direcciones OPUESTAS. Se replica con las MISMAS
    comparaciones `contains(..., ignoreCase = true)` y la misma exclusion (`!planWantsCall` /
    `!planWantsPut`), porque relajar cualquiera de las dos cambia el conjunto de vetos.
    """
    s = sr_status.lower()
    p = action_plan.lower()
    sr_wants_call = "call \u00f3ptimo" in s
    sr_wants_put = "put \u00f3ptimo" in s
    plan_wants_put = "put" in p
    plan_wants_call = "call" in p
    if sr_wants_call and plan_wants_put and not plan_wants_call:
        return True
    if sr_wants_put and plan_wants_call and not plan_wants_put:
        return True
    return False


def motivo_veto_v1(sr_status: str, action_plan: str) -> Optional[str]:
    """
    `detail` del veto V1 con el formato EXACTO que emite `executeHeadlessTrade`:

        V1 SR="<srStatus>" PLAN="<actionPlan>"

    El comparador v2 clasifica como `V1` cualquier `detail` que empiece por "V1" o lleve " V1", asi
    que mantener el prefijo es parte del contrato del instrumento, no cosmetico.
    """
    if not accion_conflictiva(sr_status, action_plan):
        return None
    return f'V1 SR="{sr_status}" PLAN="{action_plan}"'


# ---------------------------------------------------------------------------
# MÁQUINA DE ESTADOS MÍNIMA
# ---------------------------------------------------------------------------


class MaquinaEstados:
    """
    Estado mínimo que determina si una señal puede entrar: orden abierta, cooldown y memoria.

    R24 TRACK B. Las tres piezas que faltaban, y qué NO se hace en ninguna de ellas:

      - **Memoria (B1)**: arranca con las firmas REALES del telefono, ya purgadas como en `loadState`
        y filtradas por `ts <= inicio de ventana`. Crece SOLO al liquidar (no al entrar) y SOLO con
        perdidas. Nunca se fabrican firmas a partir del propio log.
      - **Settlement (B2)**: `ultima_resolucion_ms` se fija con el instante que reporta el LEDGER de
        liquidaciones (`--settlements`, journal o transiciones de `live_samples.jsonl`). Si no hay
        ledger, el cooldown NO se modela (`cooldown_modelado = False`) en vez de inventarse con
        `entrada + 60 s`: una reconstruccion artificial del reloj es justo lo que B2 elimina.
      - **V1 (B3)**: la compuerta se evalúa en el punto del pipeline que fija el Live, y solo cuando
        hay evidencia real de `srStatus`/`actionPlan`. Con distancias placeholder no se emite veto.
    """

    def __init__(
        self,
        submode: str = "YOLO",
        memoria_inicial: Optional[List[Dict[str, Any]]] = None,
        settlements: Optional[List[Dict[str, Any]]] = None,
        distancias_reales: bool = False,
    ):
        self.submode = submode
        self.orden_abierta_hasta_ms: int = 0      # fin de la operación en curso
        self.ultima_resolucion_ms: int = 0
        self.memoria_perdidas: List[Dict[str, Any]] = list(memoria_inicial or [])
        self.trades_abiertos: List[Dict[str, Any]] = []
        # Ledger de liquidaciones pendientes de aplicar, ordenado por ts. `cooldown_modelado` es
        # True en cuanto existe al menos una fuente real de resolucion.
        self._settlements: List[Dict[str, Any]] = sorted(
            settlements or [], key=lambda s: s["ts"]
        )
        self._sett_idx: int = 0
        self.cooldown_modelado: bool = bool(self._settlements)
        self.racha_perdidas: int = 0
        self.distancias_reales: bool = distancias_reales
        self.contadores: Dict[str, int] = {
            "settlements_aplicados": 0,
            "perdidas_agregadas_a_memoria": 0,
            "entradas": 0,
        }

    # -- cooldown ---------------------------------------------------------
    def cooldown_ms(self, ahora_ms: int) -> int:
        """
        `minSpacingMs` de `executeHeadlessTrade`:
            YOLO -> 8000 ; currentLossStreak > 0 -> 35000 ; resto -> 12000.

        La racha de perdidas se reconstruye del PROPIO ledger aplicado (WIN resetea, LOSS suma), que
        es el mismo estado que mantiene `RiskManager` al liquidar. Sin ledger la racha queda en 0 y
        el valor no se usa porque el cooldown no se modela.
        """
        if self.submode == "YOLO":
            return YOLO_COOLDOWN_MS
        if self.racha_perdidas > 0:
            return CONSERVATIVE_COOLDOWN_MS
        return DEFAULT_COOLDOWN_MS

    def motivo_cooldown(self, ahora_ms: int) -> Optional[str]:
        """Guard 1: cooldown post-RESOLUCION. `COOLDOWN <ms>ms`, como el Live."""
        if not self.cooldown_modelado or self.ultima_resolucion_ms <= 0:
            return None
        desde = ahora_ms - self.ultima_resolucion_ms
        if desde < 0 or desde >= self.cooldown_ms(ahora_ms):
            return None
        return f"COOLDOWN {desde}ms"

    def motivo_orden_abierta(self, ahora_ms: int) -> Optional[str]:
        """Guard RISK_LIMIT: `riskManager.hasPendingTrade` -> 'Operación abierta en curso (Ns)'."""
        if ahora_ms < self.orden_abierta_hasta_ms:
            restante = (self.orden_abierta_hasta_ms - ahora_ms) / 1000.0
            return f"RISK_LIMIT Operacio\u0301n abierta en curso ({restante:.0f}s)"
        return None

    # -- settlement -------------------------------------------------------
    def aplicar_settlements(self, ahora_ms: int) -> None:
        """
        Aplica las liquidaciones cuyo `ts` ya ocurrió. Efectos, en el mismo orden que el Live:

          1. `lastTradeResolutionTime = ts` para CUALQUIER liquidacion observada (con o sin resultado: el
         monitor ve el instante por la desaparicion de `hasPendingTrade`, y el motor fija ese reloj en
         el mismo bloque).
      2. Cierre de la operación abierta que corresponde a esa liquidación.
      3. Si fue LOSS y hay firma, se agrega a la memoria con `ts` de liquidacion (visibilidad).

        Una liquidacion `UNKNOWN` (derivada de `live_samples`) mueve el reloj de cooldown pero NO
        toca la memoria ni la racha de perdidas: el monitor no sabe si gano o perdio y suponerlo
        seria inventar evidencia.
        """
        while self._sett_idx < len(self._settlements) and self._settlements[self._sett_idx]["ts"] <= ahora_ms:
            s = self._settlements[self._sett_idx]
            self._sett_idx += 1
            self.contadores["settlements_aplicados"] += 1
            # El RELOJ de cooldown se mueve con cualquier liquidacion OBSERVADA, tenga o no resultado:
            # `lastTradeResolutionTime` se fija en el bloque de settlement del motor, y el monitor ve
            # ese instante por la desaparicion de `hasPendingTrade`. La RACHA, en cambio, exige
            # desenlace conocido: sumar una perdida que no se midio seria inventar evidencia.
            self.ultima_resolucion_ms = max(self.ultima_resolucion_ms, s["ts"])
            self.cooldown_modelado = True
            if s["result"] in ("WIN", "LOSS"):
                if s["result"] == "WIN":
                    self.racha_perdidas = 0
                else:
                    self.racha_perdidas += 1
            elif s["result"] == "TIE":
                # `recordTradeVoid` no toca `currentLossStreak`: un empate deja la racha como estaba.
                pass

            # Cerrar la operacion abierta que esta liquidacion resuelve: la que tenga `entrada_ms`
            # <= ts mas cercana. Si el ledger trae `entry_ts`, manda ese valor.
            alvo = None
            for t in self.trades_abiertos:
                if s.get("entry_ts") and t["entrada_ms"] == s["entry_ts"]:
                    alvo = t
                    break
            if alvo is None:
                candidatos = [t for t in self.trades_abiertos if t["entrada_ms"] <= s["ts"]]
                if candidatos:
                    alvo = max(candidatos, key=lambda t: t["entrada_ms"])
            if alvo is not None:
                self.trades_abiertos.remove(alvo)
                self.orden_abierta_hasta_ms = min(self.orden_abierta_hasta_ms, s["ts"])
                if s["result"] == "LOSS" and alvo.get("firma"):
                    firma = dict(alvo["firma"])
                    firma["ts"] = s["ts"]      # visible desde la liquidacion, no desde la entrada
                    self.memoria_perdidas.append(firma)
                    self.contadores["perdidas_agregadas_a_memoria"] += 1

    def cerrar_vencidas(self, ahora_ms: int) -> None:
        """
        Cierra operaciones cuyo horizonte de 60 s ya paso y que el ledger no reporto.

        NO fija `ultima_resolucion_ms`: sin liquidacion observada, fijar el reloj de cooldown a
        `entrada + 60 s` seria reconstruir el futuro (B2). El conteo queda en `vencidas_sin_settlement`
        para que el informe pueda declararlo.
        """
        pendientes = []
        for t in self.trades_abiertos:
            if ahora_ms >= t["entrada_ms"] + TRADE_DURATION_MS:
                self.contadores["vencidas_sin_settlement"] = (
                    self.contadores.get("vencidas_sin_settlement", 0) + 1
                )
            else:
                pendientes.append(t)
        self.trades_abiertos = pendientes
        if self.trades_abiertos:
            self.orden_abierta_hasta_ms = max(t["entrada_ms"] + TRADE_DURATION_MS for t in self.trades_abiertos)
        else:
            self.orden_abierta_hasta_ms = 0

    def avanzar(self, ahora_ms: int) -> None:
        """Un solo punto de avance del reloj: settlement primero, después vencimientos."""
        self.aplicar_settlements(ahora_ms)
        self.cerrar_vencidas(ahora_ms)

    # -- memoria ----------------------------------------------------------
    def memoria_visible(self, ahora_ms: int) -> List[Dict[str, Any]]:
        """
        Firmas que el motor PODIA conocer en `ahora_ms`, en orden de llegada.

        Es la garantía anti look-ahead de B1: una firma cuya liquidación ocurre después del tick que
        se está evaluando no participa en la decisión. Sin este filtro, la memoria del replay crece
        con el futuro y `ADAPTIVE_BLOCK` dispara antes que en el Live.
        """
        return [f for f in self.memoria_perdidas if int(f.get("ts", 0)) <= ahora_ms][-20:]

    # -- entrada ----------------------------------------------------------
    def registrar_entrada(self, ahora_ms: int, firma: Dict[str, Any]) -> None:
        self.orden_abierta_hasta_ms = ahora_ms + TRADE_DURATION_MS
        self.trades_abiertos.append({"entrada_ms": ahora_ms, "firma": firma})
        self.contadores["entradas"] += 1


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
    sr_status: Optional[str] = None,
    action_plan: Optional[str] = None,
    hay_niveles_sr: bool = False,
) -> Dict[str, Any]:
    """
    Evalúa UN tick y devuelve la fila de decisión, con el esquema exacto de `decisions.csv`.

    Orden de guards, espejo LITERAL de `executeHeadlessTrade` (TradingEngine.kt:2264-2400):

      0. ventana de entrada        -> FUERA_VENTANA (el motor ni evalúa)
      1. COOLDOWN post-resolución  -> `lastTradeResolutionTime` (B2)
      2. VETO_BUY_EXHAUSTION_CLIMAX (política, ANTES del adaptativo)
      3. ADAPTIVE_BLOCK            -> memoria real (B1)
      4. RISK_LIMIT operación abierta
      5. V1 exclusividad S/R<->plan (B3)
      6. entrada

    El orden importa y es la causa de que el barrido W05-W11 diera 0% en `ADAPTIVE_BLOCK`: el v2
    evaluaba primero la operación en curso, así que un tick con trade abierto salía como RISK_LIMIT
    aunque el Live lo hubiera vetado por anti-patrón en el mismo instante. En el motor real el
    cooldown es el PRIMER guard y `canExecuteTrade` (operación abierta) viene después del adaptativo.

    `sr_status`/`action_plan`: si el llamador los aporta (evidencia real del HUD o reconstrucción
    desde distancias reales), la compuerta V1 se evalúa con esos textos. Si no, V1 no se evalúa y la
    fila lo declara — no se inventa el texto para poder vetar.
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

    # 0. Fuera de la ventana de entrada el motor no evalúa: no hay decisión que registrar.
    if not en_ventana_sniper(ts):
        fila["kind"] = "SKIP"
        fila["action"] = "NONE"
        fila["detail"] = "FUERA_VENTANA_SNIPER"
        fila["veto_reason"] = "NONE"
        return fila

    # 1. Cooldown post-RESOLUCION (B2). Primero de la cadena, como en el Live.
    motivo_cd = estado.motivo_cooldown(ts)
    if motivo_cd:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_cd
        return fila

    # 2. Veto de agotamiento (política, antes del adaptativo a propósito).
    motivo_agot = motivo_veto_por_agotamiento(accion, trend, call_power)
    if motivo_agot:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_agot
        return fila

    # 3. Anti-patrón adaptativo (B1). La memoria se consulta SOLO con las firmas ya visibles en `ts`.
    firma = _firma(
        action=accion, trend=trend, ds=dist_sup, dr=dist_res, vel=tick["velocity"],
        bull=tick["bull"], bear=tick["bear"],
        sideways=(trend == "SIDEWAYS"), tight=False,
        strategy=strategy, candle_second=sec,
    )
    firma["ts"] = ts
    sim_max, coincidencia = mejor_coincidencia_en_memoria(firma, estado.memoria_visible(ts))
    motivo_adapt = motivo_bloqueo_por_antipatron(firma, coincidencia, sim_max)
    if motivo_adapt:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_adapt
        return fila

    # 4. `riskManager.canExecuteTrade`: operación abierta en curso.
    motivo_riesgo = estado.motivo_orden_abierta(ts)
    if motivo_riesgo:
        fila["kind"] = "VETO"
        fila["detail"] = motivo_riesgo
        return fila

    # 5. V1: exclusividad mutua S/R <-> plan (B3), con los textos del Live.
    if sr_status is not None and action_plan is not None:
        motivo_v1 = motivo_veto_v1(sr_status, action_plan)
        if motivo_v1:
            fila["kind"] = "VETO"
            fila["detail"] = motivo_v1
            return fila

    # 6. Entrada.
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
                    # B2: el estado de `risk` es lo que permite derivar las transiciones
                    # `hasPendingTrade` (liquidaciones observadas por el monitor).
                    "risk": (d.get("status") or {}).get("risk") or {},
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
    memoria_inicial: Optional[List[Dict[str, Any]]] = None,
    settlements: Optional[List[Dict[str, Any]]] = None,
    contexto_v1: Optional[List[Dict[str, Any]]] = None,
) -> str:
    """
    Procesa la serie completa y escribe `decisions_replay.csv` (+ `replay_v2_stats.json`).

    `muestras` (de `live_samples.jsonl`) aporta la tendencia observada por el motor. `memoria_inicial`
    (B1) son las firmas de anti-patrones que el telefono tenia al abrir la ventana; `settlements` (B2)
    es el ledger de liquidaciones reales. `contexto_v1` (B3) aporta `dist_sup`/`dist_res` reales por
    instante: sin el, las distancias son placeholder 0.50/0.50 y la compuerta V1 NO se evalua
    (declarado en el sidecar de estadisticas), porque con distancias inventadas el S/R siempre sale
    "Centro del Canal" y V1 jamas dispararia. Ese silencio se reporta, no se disimula.
    """
    estado = MaquinaEstados(
        submode=submode,
        memoria_inicial=memoria_inicial,
        settlements=settlements,
        distancias_reales=bool(contexto_v1),
    )
    filas: List[Dict[str, Any]] = []
    dist_sup, dist_res, rsi = 0.50, 0.50, 50.0
    ticks_sin_contexto = 0
    v1_vetos = 0
    adapt_vetos = 0
    cooldown_vetos = 0

    for tick in ticks:
        # Un solo punto de avance del reloj: settlement (B2) primero, vencimientos despues.
        estado.avanzar(tick["ts"])
        trend_tick = trend
        d_sup, d_res = dist_sup, dist_res
        if muestras:
            ctx = contexto_en(muestras, tick["ts"])
            if ctx is None:
                ticks_sin_contexto += 1
            else:
                trend_tick = ctx["trend"]
        if contexto_v1:
            ctx1 = contexto_en(contexto_v1, tick["ts"])
            if ctx1 is not None:
                trend_tick = ctx1["trend"]
                d_sup, d_res = ctx1["ds"], ctx1["dr"]

        fila = procesar_tick_v2(
            tick, estado, trend_tick, d_sup, d_res, rsi, accion=accion,
            hay_niveles_sr=bool(contexto_v1),
        )
        if fila["kind"] == "VETO":
            det = fila["detail"]
            if det.startswith("V1"):
                v1_vetos += 1
            elif det.startswith("ADAPTIVE_BLOCK"):
                adapt_vetos += 1
            elif det.startswith("COOLDOWN"):
                cooldown_vetos += 1
        filas.append(fila)

    if muestras and ticks_sin_contexto:
        print(f"AVISO: {ticks_sin_contexto} ticks sin muestra de contexto asociada.")

    os.makedirs(out_dir, exist_ok=True)
    ruta = os.path.join(out_dir, "decisions_replay.csv")
    with open(ruta, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=ESQUEMA_DECISIONS)
        w.writeheader()
        for fila in filas:
            w.writerow(fila)

    # Sidecar de trazabilidad: sin estos numeros, un 0% de ADAPTIVE_BLOCK es indistinguible de
    # "memoria vacia" y un 0% de V1 de "compuerta no evaluada". El comparador no los necesita; el
    # auditor si.
    stats = {
        "ticks": len(ticks),
        "filas": len(filas),
        "submode": submode,
        "memoria_inicial_firmas": len(memoria_inicial or []),
        "settlements_en_ledger": len(settlements or []),
        "cooldown_modelado": estado.cooldown_modelado,
        "v1_evaluado": bool(contexto_v1),
        "vetos_adaptive_block": adapt_vetos,
        "vetos_v1": v1_vetos,
        "vetos_cooldown": cooldown_vetos,
        "contadores_estado": estado.contadores,
    }
    with open(os.path.join(out_dir, "replay_v2_stats.json"), "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2, sort_keys=True)
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

    # =====================================================================
    # R24 TRACK B — vectores de INFRAESTRUCTURA (B1..B4)
    # =====================================================================
    def ts_seg(seg: int, minuto: int = 0) -> int:
        """Timestamp en el segundo pedido, verificado antes de usarlo."""
        t = inicio_min + minuto * 60_000 + seg * 1000
        assert (t // 1000) % 60 == seg
        return t

    def tk(t: int, vel: float = 0.0, bull: bool = False, bear: bool = False) -> Dict[str, Any]:
        return {"ts": t, "ric": "Z-CRY/IDX", "price": 100.0, "velocity": vel, "bull": bull, "bear": bear}

    def f_perdida(ts: int, **kw) -> Dict[str, Any]:
        base = dict(action="BUY", trend="SIDEWAYS", ds=0.30, dr=0.70, vel=0.0, bull=False,
                    bear=False, sideways=True, tight=False, strategy="AUTO_ADAPTIVE",
                    candle_second=4)
        base.update(kw)
        f = _firma(**base)
        f["ts"] = ts
        return f

    def evaluar(estado, t, **kw):
        return procesar_tick_v2(tk(t, kw.pop("vel", 0.0), kw.pop("bull", False), kw.pop("bear", False)),
                                estado, kw.pop("trend", "SIDEWAYS"), kw.pop("ds", 0.30),
                                kw.pop("dr", 0.70), 50.0, **kw)

    # --- B1: memoria real
    e = MaquinaEstados()
    check("B1 sin memoria no hay ADAPTIVE_BLOCK", evaluar(e, ts_seg(4))["kind"] == "ORDER_STATE")
    e = MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(1, -1))])
    fila = evaluar(e, ts_seg(4))
    check("B1 con memoria real bloquea", (fila["detail"] or "").startswith("ADAPTIVE_BLOCK"), fila["detail"])
    check(
        "B1 firma futura no participa (anti look-ahead)",
        evaluar(MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(4) + 1)]), ts_seg(4))["kind"] == "ORDER_STATE",
    )
    check(
        "B1 memoria aplica el recorte de 20 firmas",
        len(MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(0) - i) for i in range(30)])
            .memoria_visible(ts_seg(0))) == 20,
    )
    # Purga de `loadState`: generica solo si AMBOS ratios estan en el 50%.
    check("B1 purga exige ds Y dr en el 50%",
          firma_es_generica({"ds": 0.50, "dr": 0.50})
          and not firma_es_generica({"ds": 0.50, "dr": 0.90})
          and not firma_es_generica({"ds": 0.90, "dr": 0.50}))

    # --- B2: settlement y cooldown
    t_res = ts_seg(10)
    e = MaquinaEstados(settlements=[{"ts": t_res, "result": "WIN", "action": "BUY", "entry_ts": 0}])
    check("B2 sin aplicar no hay cooldown", e.motivo_cooldown(t_res) is None)
    e.avanzar(t_res)
    check("B2 cooldown arranca en la liquidacion",
          e.motivo_cooldown(t_res + 1) == f"COOLDOWN 1ms")
    check("B2 YOLO libera exactamente a los 8000ms",
          e.motivo_cooldown(t_res + YOLO_COOLDOWN_MS - 1) is not None
          and e.motivo_cooldown(t_res + YOLO_COOLDOWN_MS) is None)
    e2 = MaquinaEstados()
    evaluar(e2, ts_seg(4))
    e2.avanzar(ts_seg(4) + TRADE_DURATION_MS)
    check("B2 sin ledger no se reconstruye el cooldown",
          not e2.cooldown_modelado and e2.ultima_resolucion_ms == 0
          and e2.motivo_cooldown(ts_seg(4) + TRADE_DURATION_MS + 1) is None)
    e3 = MaquinaEstados(submode="CONSERVATIVE", settlements=[
        {"ts": ts_seg(0), "result": "LOSS", "action": "BUY", "entry_ts": 0}])
    e3.aplicar_settlements(ts_seg(0))
    check("B2 racha de perdidas del ledger -> cooldown conservador",
          e3.racha_perdidas == 1 and e3.cooldown_ms(ts_seg(0)) == CONSERVATIVE_COOLDOWN_MS)

    # --- B3: compuerta V1
    sr_put = "\U0001F534 Toque Directo en Resistencia [PUT \u00d3ptimo]"
    sr_call = "\U0001F7E2 Toque Directo en Soporte [CALL \u00d3ptimo]"
    plan_call_abajo = ("\U0001F3AF Plan: Cerca de Soporte (22%). "
                       "Esperando gatillo CALL en la ventana :01s - :05s.")
    plan_put_arriba = ("\U0001F3AF Plan: Buscar retroceso leve a resistencia para ejecutar PUT "
                       "en la ventana :01s - :05s.")
    plan_call_arriba = ("\U0001F3AF Plan: Buscar retroceso leve a soporte para ejecutar CALL "
                        "en la ventana :01s - :05s.")
    check("B3 conflicto S/R<->plan dispara", accion_conflictiva(sr_put, plan_call_abajo))
    check("B3 conflicto inverso dispara", accion_conflictiva(sr_call, plan_put_arriba))
    check("B3 direcciones coincidentes no disparan",
          not accion_conflictiva(sr_call, plan_call_arriba))
    check("B3 neutral no dispara",
          not accion_conflictiva("\u26aa Centro del Canal S/R (Neutral)", plan_call_abajo))
    check("B3 plan con ambas direcciones no dispara",
          not accion_conflictiva(sr_put, "Plan: CALL o PUT, esperando ruptura"))
    check("B3 detail replica el formato del motor",
          motivo_veto_v1(sr_put, plan_call_abajo) == f'V1 SR="{sr_put}" PLAN="{plan_call_abajo}"')
    check("B3 cortes del srStatus (0.15 y 0.30)",
          sr_status_desde_distancias(0.15, 0.9, True) == sr_call
          and sr_status_desde_distancias(0.30, 0.9, True).startswith("\U0001F7E2 Cerca de Soporte")
          and sr_status_desde_distancias(0.31, 0.9, True).startswith("\u26aa"))
    check("B3 anti-chop del plan solo fuera de YOLO",
          "Anti-Chop" not in action_plan_desde_estado("SIDEWAYS", 0.20, 0.9, "", 0, True, "YOLO")
          and "Anti-Chop" in action_plan_desde_estado("SIDEWAYS", 0.20, 0.9, "", 0, True, "CONSERVATIVE"))

    # --- B4: orden de guards (cada par aislado)
    t_cd = ts_seg(0)
    e = MaquinaEstados(settlements=[{"ts": t_cd, "result": "WIN", "action": "BUY", "entry_ts": 0}])
    e.avanzar(t_cd)
    fila = evaluar(e, t_cd + 1000, trend="UPTREND", ds=0.10, dr=0.90, bull=True)
    check("B4 cooldown gana al veto de agotamiento", (fila["detail"] or "").startswith("COOLDOWN"), fila["detail"])
    e = MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(1, -1), trend="UPTREND", ds=0.10, dr=0.90,
                                                   bull=True, sideways=False)])
    fila = evaluar(e, ts_seg(4), trend="UPTREND", ds=0.10, dr=0.90, bull=True)
    check("B4 agotamiento gana al ADAPTIVE_BLOCK",
          (fila["detail"] or "").startswith("VETO_BUY_EXHAUSTION_CLIMAX"), fila["detail"])
    e = MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(1, -1))])
    evaluar(e, ts_seg(4))
    fila = evaluar(e, ts_seg(4) + 1000)
    check("B4 ADAPTIVE_BLOCK gana a la operacion abierta",
          (fila["detail"] or "").startswith("ADAPTIVE_BLOCK"), fila["detail"])
    e = MaquinaEstados()
    evaluar(e, ts_seg(4))
    fila = evaluar(e, ts_seg(4) + 1000, sr_status=sr_put, action_plan=plan_call_abajo)
    check("B4 operacion abierta gana a V1", (fila["detail"] or "").startswith("RISK_LIMIT"), fila["detail"])
    e = MaquinaEstados()
    fila = evaluar(e, ts_seg(4), sr_status=sr_put, action_plan=plan_call_abajo)
    check("B4 V1 gana a la entrada", fila["kind"] == "VETO" and not e.trades_abiertos)
    e = MaquinaEstados(memoria_inicial=[f_perdida(ts_seg(1, -1))])
    fila = evaluar(e, ts_seg(30), sr_status=sr_put, action_plan=plan_call_abajo)
    check("B4 fuera de ventana gana a todos", fila["kind"] == "SKIP")

    print("-" * 70)
    if fallos:
        print(f"RESULTADO: FALLA ({len(fallos)})")
        return 1
    print("RESULTADO: PASS")
    return 0


def cargar_contexto_sr(ruta: str) -> List[Dict[str, Any]]:
    """
    Serie de contexto S/R REAL para la compuerta V1 (B3). CSV con cabecera:

        ts,dist_sup,dist_res[,trend]

    POR QUE HACE FALTA UN ARCHIVO APARTE. `live_samples.jsonl` publica precios de soporte/resistencia
    pero NO los ratios normalizados por ATR que usa el motor (`distanceToSupportRatio`), y recalcularlos
    offline exige replicar la maquina de velas del telefono: medido contra ventanas reales, la
    reconstruccion da `ds~0.99 / dr~1.0` donde el journal del propio dispositivo registra `0.14/0.65`.
    Es decir, reconstruir el ratio en el replay NO es fiel. Si nadie exporta la serie real, la
    compuerta V1 no se evalua y el sidecar lo declara (`v1_evaluado=false`); inventar las distancias
    produciria vetos V1 que el Live nunca emitio.
    """
    salida: List[Dict[str, Any]] = []
    if not ruta or not os.path.exists(ruta):
        return salida
    with open(ruta, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            try:
                salida.append(
                    {
                        "ts": int(row["ts"]),
                        "ds": float(row["dist_sup"]),
                        "dr": float(row["dist_res"]),
                        "trend": (row.get("trend") or "").strip().upper() or None,
                    }
                )
            except (KeyError, TypeError, ValueError):
                continue
    salida.sort(key=lambda m: m["ts"])
    return salida


def settlements_desde_journal(rutas: List[str]) -> List[Dict[str, Any]]:
    """
    Ledger de liquidaciones a partir de journals REALES (`trade_journal_*.csv`).

    POR QUE EL `timestamp` DEL JOURNAL ES EL INSTANTE DE LIQUIDACION. `TradeJournalLogger.logTrade`
    se invoca desde el bloque de settlement, y `buildLine` sella con `System.currentTimeMillis()` en
    ese momento. No es la hora de entrada: la entrada viaja aparte en `tradeStartMs`. Asi que
    `timestamp` ES el `lastTradeResolutionTime` que fija el motor (TradingEngine.kt:660 y :2746).

    `entry_ts` se reconstruye como `timestamp - duration_sec*1000`, que es el `tradeStartMs` que el
    propio journal declaro, y sirve para casar la liquidacion con la operacion correcta.

    Cabeceras aceptadas: la del journal del motor (con `timestamp`,`result`,`action`,`duration_sec`)
    y la del journal de replay v1. Filas sin `result` reconocible se descartan.
    """
    salida: List[Dict[str, Any]] = []
    for ruta in rutas:
        if not ruta or not os.path.exists(ruta):
            continue
        # GUARDA ANTICIRCULAR: `replay_journal.csv` lo produce el PROPIO arnes. Usarlo como ledger de
        # liquidaciones seria comparar el replay consigo mismo y convertir el cooldown en tautologia.
        if os.path.basename(ruta).lower().startswith("replay_"):
            print(f"AVISO: {ruta} es un journal de REPLAY, no del motor. Se ignora como settlement.")
            continue
        with open(ruta, "r", encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                try:
                    ts = int(row["timestamp"])
                except (KeyError, TypeError, ValueError):
                    continue
                resultado = str(row.get("result") or "").strip().upper()
                if resultado not in ("WIN", "LOSS", "TIE"):
                    continue
                try:
                    dur = int(float(row.get("duration_sec") or 0))
                except (TypeError, ValueError):
                    dur = 0
                salida.append(
                    {
                        "ts": ts,
                        "result": resultado,
                        "action": (row.get("action") or "").strip().upper(),
                        "entry_ts": ts - dur * 1000 if dur > 0 else 0,
                        "fuente": os.path.basename(ruta),
                    }
                )
    salida.sort(key=lambda s: s["ts"])
    return salida


def main() -> int:
    parser = argparse.ArgumentParser(description="Replay tick-a-tick v2 (R23K C2 / R24 Track B)")
    parser.add_argument("--ticks", help="Ruta a ticks_live.csv")
    parser.add_argument("--samples", default="", help="Ruta a live_samples.jsonl (contexto real de trend)")
    parser.add_argument("--out", default="", help="Directorio de salida")
    parser.add_argument("--submode", default="YOLO", choices=["YOLO", "CONSERVATIVE"])
    # B1
    parser.add_argument(
        "--adaptive-state", default=os.environ.get("ADAPTIVE_STATE_JSON", ""),
        help="adaptive_learning_state.json del telefono (memoria real de anti-patrones). "
             "Tambien se lee de ADAPTIVE_STATE_JSON.",
    )
    # B2
    parser.add_argument(
        "--settlements", default="",
        help="Ledger de liquidaciones CSV (ts,result[,action,entry_ts]). Sin el, el cooldown no se modela.",
    )
    parser.add_argument(
        "--settlements-journal", nargs="*", default=[],
        help="Uno o mas journals del MOTOR (trade_journal_*.csv) para derivar las liquidaciones reales. "
             "Se ignoran los replay_journal.csv (serian circulares).",
    )
    parser.add_argument(
        "--settlements-from-samples", action="store_true",
        help="Deriva el instante de liquidacion de las transiciones hasPendingTrade de live_samples.jsonl "
             "(solo reloj de cooldown: no trae resultado, no alimenta la memoria).",
    )
    # B3
    parser.add_argument(
        "--sr-context", default="",
        help="CSV ts,dist_sup,dist_res[,trend] con las distancias REALES. Sin el, V1 no se evalua.",
    )
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if not args.ticks:
        parser.error("--ticks es obligatorio (o usa --self-test)")

    out = args.out or os.path.dirname(os.path.abspath(args.ticks))
    ticks = cargar_ticks(args.ticks)
    print(f"Ticks cargados: {len(ticks)}")
    if not ticks:
        print("ERROR: log de ticks vacio.")
        return 2
    ventana_inicio = ticks[0]["ts"]

    muestras = []
    if args.samples:
        muestras = cargar_muestras_contexto(args.samples)
        print(f"Muestras de contexto: {len(muestras)}")
    else:
        print("AVISO: sin --samples, la tendencia se fija en UPTREND. El resultado NO es fiel.")

    # B1 — memoria real, purgada como `loadState` y sin firmas posteriores al inicio de la ventana.
    memoria, stats_mem = ([], {"cargadas": 0, "purgadas_50": 0, "purgadas_ttl": 0, "purgadas_futuro": 0})
    if args.adaptive_state:
        memoria, stats_mem = cargar_memoria_adaptativa(args.adaptive_state, ventana_inicio)
        print(
            "Memoria adaptativa: {cargadas} firmas validas (purgadas: {purgadas_50} en el 50%, "
            "{purgadas_ttl} por TTL 24h, {purgadas_futuro} posteriores al inicio de ventana)".format(**stats_mem)
        )
    else:
        print("AVISO: sin --adaptive-state, el replay arranca SIN memoria: ADAPTIVE_BLOCK no puede disparar.")

    # B2 — ledger de liquidaciones.
    settlements: List[Dict[str, Any]] = []
    if args.settlements:
        settlements = cargar_settlements(args.settlements)
        print(f"Settlements del ledger: {len(settlements)}")
    if args.settlements_from_samples:
        derivados = settlements_desde_muestras(muestras)
        print(f"Settlements derivados de live_samples: {len(derivados)} (sin resultado, solo cooldown)")
        settlements = sorted(settlements + derivados, key=lambda s: s["ts"])
    if args.settlements_journal:
        del_journal = settlements_desde_journal(args.settlements_journal)
        print(f"Settlements derivados de journal del motor: {len(del_journal)}")
        settlements = sorted(settlements + del_journal, key=lambda s: s["ts"])
    if not settlements:
        print("AVISO: sin ledger de liquidaciones el cooldown NO se modela (B2): no se reconstruye con entrada+60s.")

    # B3 — contexto S/R real.
    contexto_v1 = cargar_contexto_sr(args.sr_context)
    if contexto_v1:
        print(f"Contexto S/R real: {len(contexto_v1)} muestras (compuerta V1 activa)")
    else:
        print("AVISO: sin --sr-context la compuerta V1 no se evalua (distancias no reconstruibles offline).")

    ruta = ejecutar_replay(
        ticks, out, submode=args.submode, muestras=muestras or None,
        memoria_inicial=memoria, settlements=settlements, contexto_v1=contexto_v1 or None,
    )
    print(f"Replay escrito en: {ruta}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
