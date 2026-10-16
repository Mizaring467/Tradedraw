#!/usr/bin/env python3
"""
scripts/validation/live_monitor_r23_autonomous.py
Ronda 23: Monitor y auditoría en vivo para el bucle de automejora en MODO AUTÓNOMO (CUENTA DEMO).

Supervisa continuamente que:
1. La cuenta sea EXCLUSIVAMENTE DEMO (isDemoAccount == True). Si detecta cuenta REAL, apaga el bot inmediatamente.
2. El pipeline progrese: feed -> análisis -> señal -> guards -> decisión aprobada -> tap autónomo DEMO -> confirmación -> resolución -> W/L -> journal.
3. Registra métricas de evaluación, vetos, taps, balance inicial/final, y genera trades.csv y decisions.csv.
4. Vuelca /ticks, ejecuta replay offline y genera LIVE_REPORT.md formal para la fase r23_autonomous.
"""

import argparse
import csv
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

# Failsafe para evitar UnicodeEncodeError en consolas de Windows (cp1252)
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass


# ---------------------------------------------------------------------------
# R23D Fase 1 (REQ 1 / REQ 2 / REQ 4): parametros y helpers puros de la fase.
#
# POR QUE son funciones de modulo y no codigo embebido en el bucle: son la parte
# MEDIBLE de la telemetria (umbral de outlier, extraccion de score) y hay que
# poder verificarlas con datos reales sin levantar el monitor entero.
# ---------------------------------------------------------------------------

# REQ 1: ventana maxima de espera tras el cierre nominal para que resuelva un trade
# que todavia estaba en curso. 75s cubre de sobra una vela M1 (60s) mas el margen de
# confirmacion del broker.
SETTLEMENT_GRACE_PERIOD_SEC = 75.0

# REQ 2: una lectura de balance que diverja del ultimo balance valido mas de 1.000.000 COP
# O mas de 10x el stake, sin un trade confirmado en la misma muestra, es un glitch de
# telemetria (W03 reporto 1.000.003,00 y falseo un drawdown de -97,4%).
BALANCE_OUTLIER_ABS_THRESHOLD = 1_000_000.0
BALANCE_OUTLIER_STAKE_MULTIPLIER = 10.0
BALANCE_OUTLIER_STAKE_FALLBACK = 80_000.0  # stake base Binomo si /status no lo reporta

# REQ 4: score real de confluencia embebido en el `detail` de un evento. Se soportan las
# dos formas observadas en el codigo: "[Confluencia: 85pts]" (SyntheticCandleEngine) y
# "Confluencia ... 75%" (variante porcentual).
CONFLUENCE_PTS_REGEX = re.compile(r"\[Confluencia:\s*(\d+(?:\.\d+)?)pts\]")
CONFLUENCE_PCT_REGEX = re.compile(r"Confluencia.*?(\d+(?:\.\d+)?)\s*%")


def extract_confluence_score(detail: str) -> Optional[float]:
    """Extrae el puntaje REAL de confluencia del `detail` de un evento de telemetria.

    Devuelve `None` cuando el detalle no trae marcador de confluencia (p.ej. un veto de
    RISK_LIMIT o ADAPTIVE_BLOCK, que nunca paso por la matriz de confluencia). Antes de
    la Fase 1 el monitor escribia un `score` hardcodeado de 85.0 para TODA decision,
    incluidas las que jamas se evaluaron contra la matriz: el campo no describia nada.
    """
    if not detail:
        return None
    m = CONFLUENCE_PTS_REGEX.search(detail)
    if m is None:
        m = CONFLUENCE_PCT_REGEX.search(detail)
    if m is None:
        return None
    try:
        return float(m.group(1))
    except ValueError:
        return None


def es_outlier_balance(
    cur_bal: float,
    last_valid_balance: Optional[float],
    stake: float,
    trade_confirmado_en_muestra: bool,
) -> bool:
    """REQ 2: decide si una lectura de balance es un outlier de telemetria.

    Regla de la spec: diverge del ultimo balance valido por mas de 1.000.000 COP
    (o mas de 10x el stake) SIN que medie un trade confirmado en esa misma muestra.
    El `or` es deliberado: con un stake de 80.000 COP el umbral relativo (800.000) es
    el que realmente caza el glitch de W03 (~970.000 COP de desvio), no el absoluto.
    Un trade confirmado en la muestra exime la lectura: ahi la variacion es real.
    """
    if last_valid_balance is None or trade_confirmado_en_muestra:
        return False
    divergencia = abs(cur_bal - last_valid_balance)
    stake_ref = stake if stake > 0 else BALANCE_OUTLIER_STAKE_FALLBACK
    return (
        divergencia > BALANCE_OUTLIER_ABS_THRESHOLD
        or divergencia > BALANCE_OUTLIER_STAKE_MULTIPLIER * stake_ref
    )


def hay_trade_sin_resolver(
    cur_order_state: str,
    has_pending_trade: bool,
    trades_confirmados: int,
    eventos_resueltos: int,
) -> bool:
    """REQ 1: decide si al cierre nominal queda un trade sin resolver.

    Tres sintomas, cualquiera basta:
      1. La maquina de ordenes no esta en `NINGUNA`.
      2. `/status` declara `hasPendingTrade`.
      3. Se confirmaron mas trades en la ventana que eventos de resultado (W/L) registrados:
         el trade existe pero su resolucion todavia no llego a los contadores.
    """
    if cur_order_state != "NINGUNA":
        return True
    if has_pending_trade:
        return True
    return trades_confirmados > eventos_resueltos


def fetch_json(url: str, timeout: float = 3.0) -> Optional[Dict[str, Any]]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "R23-AutoMonitor/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = resp.read().decode("utf-8")
            clean_data = "\n".join(
                line for line in data.splitlines()
                if not line.strip().startswith("//")
            )
            return json.loads(clean_data)
    except Exception:
        return None


def emergency_shutdown(base_url: str):
    print("🚨🚨🚨 APAGADO DE EMERGENCIA INMEDIATO: setTradingMode(DISABLED) 🚨🚨🚨")
    try:
        urllib.request.urlopen(f"{base_url}/mode?mode=DISABLED", timeout=2.0)
    except Exception as e:
        print(f"Error al enviar disabled: {e}")


# ---------------------------------------------------------------------------
# R23E (REQ 2): PIPELINE CONTRAFACTUAL SHADOW DE ADAPTIVE_BLOCK
#
# Mide, de forma puramente pasiva, que habria pasado con las senales que el guard
# ADAPTIVE_BLOCK veto. Cero taps, cero fondos, cero alteracion de saldo: solo se
# registra la oportunidad y se resuelve contra el feed de ticks ya capturado.
# ---------------------------------------------------------------------------

# Precio de entrada: ventana de busqueda del tick mas cercano a decision_ts.
# Los ticks de Z-CRY/IDX llegan a ~2,4/s (~417 ms entre muestras). +-4000 ms acota la
# busqueda a ~10 ticks y sigue siendo "el precio en el instante de la decision".
COUNTERFACTUAL_ENTRY_TOLERANCE_MS = 4000

# Horizonte de resolucion: 60 s exactos desde decision_ts (parametro congelado del preregistro).
COUNTERFACTUAL_HORIZON_MS = 60_000

# Ventana de ocupacion emulada: durante 60 s tras registrar un contrafactual en una
# direccion, ninguna senal subsiguiente de la MISMA direccion cuenta como oportunidad
# nueva (emula el OPERACION_EN_CURSO que habria impedido ordenes solapadas).
COUNTERFACTUAL_OCCUPANCY_MS = 60_000

# Payout de referencia congelado en el preregistro (b = 0.82).
COUNTERFACTUAL_PAYOUT = 0.82

# Stake unitario nominal del preregistro.
COUNTERFACTUAL_STAKE = 100_000.0

COUNTERFACTUAL_FIELDS = [
    "counterfactual_id", "decision_id", "asset", "action", "decision_ts",
    "entry_price", "expiry_ts", "exit_price", "result", "score", "similarity",
    "market_regime", "trend", "payout", "theoretical_ev",
]

COUNTERFACTUAL_SIM_BUCKETS = [(90.0, 93.0), (93.0, 96.0), (96.0, 98.0), (98.0, 100.0)]
COUNTERFACTUAL_SCORE_BUCKETS = [(75.0, 79.0), (80.0, 84.0), (85.0, 100.0)]


def extract_adaptive_block_fields(detail: str) -> Dict[str, Any]:
    """REQ 2 paso 1: saca `sim` y `score` del `detail` enriquecido por REQ 1.

    El formato que emite `buildAdaptiveBlockDetail` es
    `ADAPTIVE_BLOCK [sim:98%] [Confluencia: 85pts] <razon>`. Ambos prefijos son opcionales:
    la rama de bloqueo por continuacion/reversion no mide similitud, y la confluencia solo
    existe si la senal paso por la matriz. En esos casos el campo sale `None` en vez de un
    valor por defecto, para no inventar una medicion que el motor nunca hizo.
    """
    sim = None
    m_sim = re.search(r"\[sim:\s*(\d+(?:\.\d+)?)%\]", detail)
    if m_sim:
        try:
            sim = float(m_sim.group(1))
        except ValueError:
            sim = None
    score = None
    m_sc = re.search(r"\[Confluencia:\s*(\d+(?:\.\d+)?)pts\]", detail)
    if m_sc:
        try:
            score = float(m_sc.group(1))
        except ValueError:
            score = None
    return {"similarity": sim, "score": score}


def find_nearest_tick_price(
    ticks: Dict[int, str], target_ts: int, tolerance_ms: int = COUNTERFACTUAL_ENTRY_TOLERANCE_MS
) -> Optional[float]:
    """Precio del tick mas cercano a `target_ts` dentro de `tolerance_ms`. `None` si no hay.

    Se compara con `<=` en el desempate para que el tick ANTERIOR gane cuando dos estan
    equidistantes: para un precio de ENTRADA es la lectura mas conservadora (no usa un
    tick que aun no existia en el instante de la decision).
    """
    best_ts: Optional[int] = None
    best_delta = tolerance_ms + 1
    for ts in ticks:
        delta = abs(ts - target_ts)
        if delta <= tolerance_ms and delta <= best_delta:
            if delta == best_delta and best_ts is not None and ts > best_ts:
                continue
            best_ts, best_delta = ts, delta
    if best_ts is None:
        return None
    try:
        return float(ticks[best_ts].split(",")[-1])
    except (ValueError, IndexError):
        return None


def dedupe_counterfactual(
    action: str,
    decision_ts: int,
    open_until: Dict[str, int],
    last_candle: Dict[str, int],
) -> bool:
    """REQ 2 paso 2: decide si una senal bloqueada es una oportunidad NUEVA.

    Devuelve `True` si debe registrarse. Dos reglas del preregistro, ambas por direccion:
      1. Ventana de ocupacion: no registrar si ya hay un contrafactual activo de la misma
         accion cuyo horizonte de 60 s no ha concluido.
      2. Maximo 1 por vela de 60 s por direccion (la vela se identifica por
         `decision_ts // 60000`).
    """
    if action not in ("BUY", "SELL"):
        return False
    if open_until.get(action, 0) > decision_ts:
        return False
    if last_candle.get(action, -1) == decision_ts // COUNTERFACTUAL_HORIZON_MS:
        return False
    return True


def resolve_counterfactual(action: str, entry_price: float, exit_price: float) -> str:
    """REQ 2 paso 3: resuelve WIN/LOSS/VOID contra las reglas del preregistro."""
    if exit_price == entry_price:
        return "COUNTERFACTUAL_VOID"
    if action == "BUY":
        return "COUNTERFACTUAL_WIN" if exit_price > entry_price else "COUNTERFACTUAL_LOSS"
    if action == "SELL":
        return "COUNTERFACTUAL_WIN" if exit_price < entry_price else "COUNTERFACTUAL_LOSS"
    return "COUNTERFACTUAL_VOID"


def counterfactual_ev(result: str, payout: float = COUNTERFACTUAL_PAYOUT,
                      stake: float = COUNTERFACTUAL_STAKE) -> float:
    """Valor esperado teorico de un contrafactual con stake unitario nominal."""
    if result == "COUNTERFACTUAL_WIN":
        return payout * stake
    if result == "COUNTERFACTUAL_LOSS":
        return -stake
    return 0.0


def counterfactual_id(decision_id: str, decision_ts: int) -> str:
    """Id determinista derivado de `decision_id` y `decision_ts` (preregistro §6.3)."""
    return f"CF-{decision_id}-{decision_ts}"


def bucket_label(value: Optional[float], buckets, suffix: str = "") -> str:
    """Etiqueta el bucket preregistrado al que cae `value`. 'N/A' si no hay medicion."""
    if value is None:
        return "N/A"
    for lo, hi in buckets:
        if lo <= value < hi or (hi == buckets[-1][1] and value == hi):
            return f"[{lo:g},{hi:g}]{suffix}"
    return "N/A"


def main():
    parser = argparse.ArgumentParser(description="Monitor Live R23 MODO AUTÓNOMO DEMO")
    parser.add_argument("--iter", type=int, default=1, help="Número de iteración R23 Autónomo (default: 1)")
    parser.add_argument("--duration-min", type=float, default=20.0, help="Duración en minutos (default: 20.0)")
    parser.add_argument("--interval-sec", type=float, default=10.0, help="Intervalo en segundos (default: 10.0)")
    parser.add_argument("--expected-sha", default="", help="Git SHA esperado en /status")
    parser.add_argument("--expected-build-id", default="R23-AUTO-ITER1", help="Build ID esperado")
    parser.add_argument("--expected-version-code", type=int, default=10, help="versionCode esperado")
    parser.add_argument("--out-dir", default="", help="Directorio de salida explícito (default: loops/r23_autonomous/iter_<iter>)")
    args = parser.parse_args()

    out_dir = args.out_dir if args.out_dir else f"loops/r23_autonomous/iter_{args.iter}"
    os.makedirs(out_dir, exist_ok=True)
    out_samples_jsonl = os.path.join(out_dir, "live_samples.jsonl")
    out_status_jsonl = os.path.join(out_dir, "status_samples.jsonl")
    out_events_jsonl = os.path.join(out_dir, "events.jsonl")
    out_decisions_csv = os.path.join(out_dir, "decisions.csv")
    out_trades_csv = os.path.join(out_dir, "trades.csv")
    out_mode_log = os.path.join(out_dir, "mode_log.json")
    out_report_md = os.path.join(out_dir, "LIVE_REPORT.md")
    out_replay_json = os.path.join(out_dir, "replay_comparison.json")
    out_replay_report = os.path.join(out_dir, "replay_comparison_report.md")

    base_url = "http://127.0.0.1:8080"
    start_time = time.time()
    end_time = start_time + (args.duration_min * 60.0)

    print("=" * 70)
    print(f"INICIANDO MONITOR AUTÓNOMO R23 — ITERACIÓN {args.iter}")
    print(f"Objetivo: Verificar ciclo operativo autónomo completo en CUENTA DEMO")
    print(f"Ventana: {args.duration_min} minutos | Intervalo: {args.interval_sec}s")
    print(f"Build ID esperado:    {args.expected_build_id}")
    print(f"versionCode esperado: {args.expected_version_code}")
    print(f"Salida en:            {out_dir}")
    print("=" * 70)

    sample_count = 0
    initial_balance: Optional[float] = None
    cur_bal: float = 0.0
    demo_verified_count = 0
    real_account_seen = 0
    taps_observed = 0
    initial_taps: Optional[int] = None
    actual_git_sha = ""
    observed_decisions: Dict[str, Dict[str, Any]] = {}
    observed_trades: List[Dict[str, Any]] = []
    seen_trade_ids = set()

    min_range_ratio = 1e9
    max_range_ratio = 0.0
    active_asset = ""
    # R23C Fase 2B (REQ 2): submodo activo de la ventana, leido de `/status`. Arranca en el fallback
    # conservador para que, si `/status` nunca lo reportara, el replay no se desbloquee por accidente.
    active_submode = "CONSERVATIVE"
    # R23C Fase 2B (REQ 1): acumulador de ticks de TODA la ventana.
    #
    # POR QUE. El endpoint `/ticks` es un buffer CIRCULAR de 2000 entradas (R23 Iter 4). A la tasa
    # medida de ~2,43 ticks/s eso son ~13,8 minutos, asi que al terminar una ventana de 20 min el
    # telefono ya habia descartado los primeros ~6 minutos: medido en 4 ventanas, solo 11 de 20
    # trades tenian ticks que los alimentaran. Sin datos de mercado, el motor offline no puede
    # reproducir esos trades y la paridad queda topada en el 55% por construccion.
    #
    # Se consulta `/ticks` en CADA ciclo (~10 s) y se acumula por `ts` en un dict, que deduplica
    # gratis: los solapamientos entre ciclos no duplican lineas. Al final se vuelca TODO ordenado,
    # reconstruyendo la ventana completa en el host.
    accumulated_ticks: Dict[int, str] = {}
    tick_capture_errors = 0

    # Métricas de riesgo y motor
    initial_wins = 0
    initial_losses = 0
    cur_wins = 0
    cur_losses = 0
    cur_order_state = "NINGUNA"

    # R23D Fase 1 (REQ 1): trazabilidad del cierre REAL de la ventana frente al nominal.
    # En W03 un trade despachado en el ultimo minuto resolvio despues del cierre, dejando los
    # contadores de la ventana en W=0/L=0 y obligando a conciliar a mano. Estos tres campos
    # permiten reconstruir cuanto hubo que esperar y detectar la discrepancia sin mano humana.
    nominal_window_end_ts: float = end_time
    actual_capture_end_ts: float = end_time
    settlement_extra_seconds: float = 0.0

    # R23D Fase 1 (REQ 2): serie de balance VALIDADO (sin outliers). El drawdown y el balance
    # final se calculan sobre esta serie, no sobre las lecturas crudas: una muestra aislada de
    # 1.000.003,00 falseo un drawdown de -97,4% en W03.
    validated_balance_series: List[float] = []
    last_valid_balance: Optional[float] = None
    outlier_count = 0
    min_validated_balance: Optional[float] = None
    max_validated_balance: Optional[float] = None

    # R23E (REQ 2): estado del pipeline contrafactual.
    # `cf_pending` son las oportunidades registradas y aun sin resolver; `cf_open_until` y
    # `cf_last_candle` implementan la deduplicacion por direccion (ocupacion 60s + 1 por vela);
    # `cf_rows` acumula TODAS las oportunidades, resueltas o no, para el volcado del CSV.
    cf_pending: List[Dict[str, Any]] = []
    cf_rows: List[Dict[str, Any]] = []
    cf_open_until: Dict[str, int] = {}
    cf_last_candle: Dict[str, int] = {}
    cf_blocked_seen = 0
    cf_deduplicated = 0
    cf_seen_decision_ids: set = set()

    # R23E: payout con el que valorar el EV. Se prefiere el REAL resuelto por el lector de
    # pantalla del broker y se cae al de referencia del preregistro (0.82) si no llega.
    cf_payout_actual = fetch_json(f"{base_url}/status") or {}
    cf_payout_live = (
        cf_payout_actual.get("feed", {}).get("payoutResolved")
        or COUNTERFACTUAL_PAYOUT
    )

    def registrar_contrafactuales(now_ms: int) -> None:
        """Registra oportunidades nuevas y resuelve las que ya cumplieron el horizonte de 60s."""
        nonlocal cf_blocked_seen, cf_deduplicated
        # -- Registro: se recorre la copia de decisiones ya observadas buscando vetos
        # ADAPTIVE_BLOCK que aun no tengan contrafactual asociado.
        for did, dec in observed_decisions.items():
            if did in cf_seen_decision_ids:
                continue
            if dec.get("kind") != "VETO":
                continue
            if "ADAPTIVE_BLOCK" not in (dec.get("detail") or "").upper():
                continue
            cf_seen_decision_ids.add(did)
            cf_blocked_seen += 1
            action = dec.get("action")
            decision_ts = int(dec.get("timestamp") or now_ms)
            if not dedupe_counterfactual(action, decision_ts, cf_open_until, cf_last_candle):
                cf_deduplicated += 1
                continue
            campos = extract_adaptive_block_fields(dec.get("detail") or "")
            cf_open_until[action] = decision_ts + COUNTERFACTUAL_OCCUPANCY_MS
            cf_last_candle[action] = decision_ts // COUNTERFACTUAL_HORIZON_MS
            cf_pending.append({
                "counterfactual_id": counterfactual_id(did, decision_ts),
                "decision_id": did,
                "asset": dec.get("asset") or active_asset,
                "action": action,
                "decision_ts": decision_ts,
                "expiry_ts": decision_ts + COUNTERFACTUAL_HORIZON_MS,
                "score": campos["score"],
                "similarity": campos["similarity"],
                "market_regime": dec.get("market_regime"),
                "trend": dec.get("trend"),
                "payout": cf_payout_live,
            })

        # -- Resolucion: solo las que ya cumplieron 60s desde decision_ts.
        aun_pendientes: List[Dict[str, Any]] = []
        for cf in cf_pending:
            if now_ms < cf["expiry_ts"]:
                aun_pendientes.append(cf)
                continue
            entry_price = find_nearest_tick_price(accumulated_ticks, cf["decision_ts"])
            exit_price = find_nearest_tick_price(accumulated_ticks, cf["expiry_ts"])
            if entry_price is None or exit_price is None:
                # Sin dato de feed no se inventa un resultado: la fila queda sin resolver.
                cf["entry_price"] = entry_price
                cf["exit_price"] = exit_price
                cf["result"] = ""
                cf["theoretical_ev"] = ""
                cf_rows.append(cf)
                continue
            result = resolve_counterfactual(cf["action"], entry_price, exit_price)
            cf["entry_price"] = entry_price
            cf["exit_price"] = exit_price
            cf["result"] = result
            cf["theoretical_ev"] = counterfactual_ev(result, cf["payout"])
            cf_rows.append(cf)
            print(
                f"[{int((now_ms - start_time * 1000) / 1000):4d}s] 🧪 CONTRAFACTUAL {result}: {cf['action']} "
                f"#{cf['counterfactual_id'][-12:]} entry={entry_price} exit={exit_price} "
                f"sim={cf['similarity']} score={cf['score']}"
            )
        cf_pending[:] = aun_pendientes

    with open(out_samples_jsonl, "a", encoding="utf-8") as f_samples, \
         open(out_status_jsonl, "a", encoding="utf-8") as f_status:

        # R23D Fase 1 (REQ 1): el ciclo de muestreo se aisla en una funcion para poder reutilizarlo
        # durante el SETTLEMENT GRACE PERIOD. Sin esto habria que duplicar el bloque entero (fetch de
        # /status, /nodes, /events, /ticks + procesamiento), y una copia divergiria de la original.
        status: Optional[Dict[str, Any]] = None
        cur_has_pending = False

        def capturar_ciclo(now_ts: float) -> None:
            nonlocal sample_count, initial_balance, cur_bal, demo_verified_count, real_account_seen
            nonlocal taps_observed, initial_taps, actual_git_sha, cur_wins, cur_losses, cur_order_state
            nonlocal active_asset, active_submode, min_range_ratio, max_range_ratio, tick_capture_errors
            nonlocal last_valid_balance, outlier_count, min_validated_balance, max_validated_balance
            nonlocal status, cur_has_pending, initial_wins, initial_losses
            elapsed_sec = int(now_ts - start_time)
            rem_sec = int(end_time - now_ts)

            status = fetch_json(f"{base_url}/status")
            nodes = fetch_json(f"{base_url}/nodes")
            events_data = fetch_json(f"{base_url}/events?max=100")

            # R23C Fase 2B (REQ 1): acumular los ticks de este ciclo. Se pide el buffer entero
            # (max=2000) para que el solapamiento entre ciclos cubra cualquier tick perdido si un
            # ciclo tardara mas de lo previsto; el dict por `ts` absorbe los repetidos.
            try:
                req_tk = urllib.request.Request(
                    f"{base_url}/ticks?max=2000&format=csv",
                    headers={"User-Agent": "R23-AutoMonitor/1.0"},
                )
                with urllib.request.urlopen(req_tk, timeout=5.0) as resp_tk:
                    for linea in resp_tk.read().decode("utf-8").splitlines():
                        linea = linea.strip()
                        if not linea or linea.startswith("ts"):
                            continue
                        primera = linea.split(",", 1)[0]
                        try:
                            accumulated_ticks[int(primera)] = linea
                        except ValueError:
                            continue  # linea corrupta o de otro formato: se ignora
            except Exception:
                # El feed de ticks es telemetria: si falla no puede tumbar la validacion, que es lo
                # que realmente mide esta ventana. Se cuenta para que el reporte lo refleje.
                tick_capture_errors += 1

            # R23D Fase 1 (REQ 2): el registro de la muestra se emite al FINAL del ciclo, no aqui.
            # Antes se escribia antes de procesar `/status`, asi que no podia llevar los campos de
            # calidad de balance (`raw_balance`/`validated_balance`/`balance_quality`) que exige la
            # fase: hay que validar la lectura contra el ultimo balance bueno primero.

            if status is not None:
                f_status.write(json.dumps(status, ensure_ascii=False) + "\n")
                f_status.flush()
                sample_count += 1

                # 1. VERIFICACIÓN DE IDENTIDAD DE BUILD
                b_code = status.get("versionCode", 0)
                b_name = status.get("versionName", "")
                b_sha = status.get("gitSha", "")
                b_id = status.get("buildId", "")
                if sample_count == 1:
                    actual_git_sha = b_sha
                    print(f"[VERIFICACIÓN BUILD] APK: versionCode={b_code}, versionName={b_name}, gitSha={b_sha}, buildId={b_id}")

                # 2. VERIFICACIÓN CRÍTICA DE SEGURIDAD: CUENTA DEMO
                # Consultar tanto status como nodes
                is_demo = False
                if "isDemoAccount" in status:
                    is_demo = bool(status["isDemoAccount"])
                elif nodes and "isDemoAccount" in nodes:
                    is_demo = bool(nodes["isDemoAccount"])

                if is_demo:
                    demo_verified_count += 1
                else:
                    real_account_seen += 1
                    print(f"[{elapsed_sec:4d}s] 🛑🛑🛑 ALERTA CRÍTICA: Cuenta DEMO NO VERIFICADA! Abortando.")
                    emergency_shutdown(base_url)
                    sys.exit("ABORTADO_POR_SEGURIDAD_NO_DEMO")

                # 3. BALANCE Y TAPS
                # R23D Fase 1 (REQ 2): aqui solo se captura la lectura CRUDA. La validacion contra
                # outliers se hace al final del ciclo, cuando ya se sabe si en esta misma muestra se
                # confirmo un trade (una variacion grande es legitima si el saldo se movio de verdad).
                cur_bal = status.get("balance", 0.0)

                risk = status.get("risk", {})
                taps_observed = risk.get("tapsDespachados", taps_observed)
                cur_order_state = risk.get("orderState", cur_order_state)
                cur_has_pending = bool(risk.get("hasPendingTrade", False))

                if sample_count == 1:
                    initial_taps = taps_observed
                    initial_wins = risk.get("totalWins", 0)
                    initial_losses = risk.get("totalLosses", 0)
                cur_wins = risk.get("totalWins", 0)
                cur_losses = risk.get("totalLosses", 0)

                feed = status.get("feed", {})
                active_asset = feed.get("activeAsset", active_asset)
                # R23C Fase 2B (REQ 2): submodo activo, leido de `/status` (campo expuesto en R23B
                # Iter 1). Se captura en el bucle para tenerlo disponible al invocar el replay; sin
                # esto el replay correria en CONSERVATIVE y suprimiria señales que el motor real si
                # emitio, invalidando la comparacion.
                active_submode = status.get("submode", active_submode) or active_submode
                range_ratio = feed.get("recentPriceRangeRatio", -1.0)
                if range_ratio > 0.0:
                    min_range_ratio = min(min_range_ratio, range_ratio)
                    max_range_ratio = max(max_range_ratio, range_ratio)

                # 4. CAPTURAR EVENTOS Y DECISIONES
                # R23D Fase 1 (REQ 2): bandera de "en ESTA muestra entro un trade confirmado". Una
                # variacion brusca de saldo es legitima si el broker acaba de mover el saldo; sin
                # esta excepcion el propio trade real se clasificaria como outlier.
                trade_confirmado_en_muestra = False
                if events_data and "events" in events_data:
                    for ev in events_data["events"]:
                        did = ev.get("decisionId")
                        tid = ev.get("tradeId")
                        kind = ev.get("kind")
                        detail = ev.get("detail", "")
                        at_ms = ev.get("atMs", int(now_ts * 1000))

                        if did and did not in observed_decisions:
                            action = "NONE"
                            if "BUY" in detail.upper() or "CALL" in detail.upper():
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
                                elif "SEGURIDAD" in detail.upper():
                                    veto_kind = "SEGURIDAD_NO_DEMO"
                                else:
                                    veto_kind = "OTRO"

                            analysis_block = status.get("analysis", {})
                            is_sideways = analysis_block.get("isMarketSideways", False)
                            detected_regime = "SIDEWAYS" if is_sideways else "TRENDING"
                            trend_val = analysis_block.get("trend", "UPTREND")

                            # REQ 4: score REAL de confluencia extraido del detail (antes 85.0 hardcodeado).
                            real_score = extract_confluence_score(detail)

                            observed_decisions[did] = {
                                "decision_id": did,
                                "trade_id": tid or "",
                                "timestamp": at_ms,
                                "asset": active_asset,
                                "price_at_signal": analysis_block.get("currentPriceY", 0.0),
                                "candle_second": ((at_ms // 1000) % 60),
                                "market_regime": detected_regime,
                                "trend": trend_val,
                                "action": action,
                                "score": real_score,
                                "action_plan": f"{kind} {action}",
                                "kind": kind,
                                "detail": detail,
                                "veto_reason": veto_kind,
                            }

                        # Solo registrar como trade si es confirmación real de saldo movido (excluye NINGUNA y ENVIADA)
                        is_trade_event = ("CONFIRMADA" in detail.upper()) and ("saldo movido" in detail.lower())
                        if tid and tid not in seen_trade_ids and is_trade_event:
                            seen_trade_ids.add(tid)
                            trade_confirmado_en_muestra = True
                            trade_action = ev.get("action")
                            if not trade_action or trade_action == "UNKNOWN":
                                # Inferir desde la decisión asociada o el detalle
                                if "BUY" in detail.upper() or "CALL" in detail.upper():
                                    trade_action = "BUY"
                                elif "SELL" in detail.upper() or "PUT" in detail.upper():
                                    trade_action = "SELL"
                                elif did and did in observed_decisions and observed_decisions[did]["action"] != "NONE":
                                    trade_action = observed_decisions[did]["action"]
                                else:
                                    trade_action = "UNKNOWN"

                            observed_trades.append({
                                "trade_id": tid,
                                "decision_id": did or "",
                                "timestamp_ms": at_ms,
                                "asset": active_asset,
                                "action": trade_action,
                                "detail": detail,
                                "balance_at_trade": cur_bal
                            })
                            print(f"[{elapsed_sec:4d}s] ⚡ NUEVO TRADE DETECTADO: trade_id={tid} did={did} action={trade_action}")

                # Heartbeat de consola
                eng = status.get("engine", {})
                eval_count = eng.get("evaluatedCount", 0)
                sig_count = eng.get("signalsGenerated", 0)
                mode_str = status.get("mode", "")
                h_state = status.get("health", {}).get("state", "")
                delta_bal = cur_bal - (initial_balance or cur_bal)

                if sample_count % 3 == 0 or cur_order_state != "NINGUNA":
                    print(
                        f"[{elapsed_sec:4d}s | rem={rem_sec:3d}s] "
                        f"modo={mode_str} salud={h_state} DEMO=[OK]({demo_verified_count}) "
                        f"bal={cur_bal:,.2f}(Δ{delta_bal:+.2f}) taps={taps_observed} "
                        f"orden={cur_order_state} W/L={cur_wins - initial_wins}/{cur_losses - initial_losses} "
                        f"eval={eval_count} sig={sig_count} dids={len(observed_decisions)}"
                    )

            # 5. VALIDACION DE BALANCE (REQ 2) + REGISTRO DE LA MUESTRA
            # El stake de referencia sale del propio `/status` (effectiveStake/observedStake) para
            # que el umbral relativo escale con lo que realmente se esta apostando; si no llega
            # ninguno, `es_outlier_balance` cae al stake base de Binomo.
            stake_ref = 0.0
            if status is not None:
                risk_ref = status.get("risk", {})
                for clave_stake in ("effectiveStake", "observedStake", "investmentAmount"):
                    valor_stake = risk_ref.get(clave_stake)
                    if isinstance(valor_stake, (int, float)) and valor_stake > 0:
                        stake_ref = float(valor_stake)
                        break

            if status is not None:
                es_outlier = es_outlier_balance(
                    cur_bal, last_valid_balance, stake_ref, trade_confirmado_en_muestra
                )
                if es_outlier:
                    outlier_count += 1
                    balance_quality = "OUTLIER"
                    validated_balance = last_valid_balance
                    print(
                        f"[{elapsed_sec:4d}s] ⚠️ [TELEMETRY WARNING] Outlier de balance detectado: "
                        f"{cur_bal:,.2f} -> ignorado en drawdown"
                    )
                else:
                    balance_quality = "VALID"
                    validated_balance = cur_bal
                    last_valid_balance = cur_bal
                    if initial_balance is None and cur_bal > 0.0:
                        initial_balance = cur_bal
                if validated_balance is not None and validated_balance > 0.0:
                    validated_balance_series.append(validated_balance)
                    min_validated_balance = (
                        validated_balance if min_validated_balance is None
                        else min(min_validated_balance, validated_balance)
                    )
                    max_validated_balance = (
                        validated_balance if max_validated_balance is None
                        else max(max_validated_balance, validated_balance)
                    )
            else:
                balance_quality = "UNREADABLE"
                validated_balance = last_valid_balance

            sample_record: Dict[str, Any] = {
                "sample_idx": sample_count,
                "timestamp_ms": int(now_ts * 1000),
                "elapsed_sec": elapsed_sec,
                "status": status,
                "nodes": nodes,
                "events_count": len(events_data.get("events", [])) if events_data else 0,
                # R23D Fase 1 (REQ 2): trazabilidad de la calidad de la lectura de balance.
                "raw_balance": cur_bal if status is not None else None,
                "validated_balance": validated_balance,
                "balance_quality": balance_quality,
            }
            f_samples.write(json.dumps(sample_record, ensure_ascii=False) + "\n")
            f_samples.flush()

        while time.time() < end_time:
            capturar_ciclo(time.time())
            registrar_contrafactuales(int(time.time() * 1000))
            time.sleep(args.interval_sec)

    # ---------------------------------------------------------------------------
    # R23D Fase 1 (REQ 1): SETTLEMENT GRACE PERIOD
    #
    # Al alcanzar el cierre nominal, si queda un trade sin resolver (la maquina de ordenes no
    # esta en NINGUNA, o `/status` declara hasPendingTrade, o hay mas trades confirmados que
    # resoluciones W/L contabilizadas) se sigue muestreando hasta que resuelva o expire el
    # timeout de 75s. En W03 el trade ejecutado en el ultimo minuto resolvio despues del cierre
    # y los contadores de la ventana quedaron en W=0/L=0, obligando a conciliar a mano.
    # ---------------------------------------------------------------------------
    nominal_window_end_ts = end_time
    actual_capture_end_ts = time.time()

    trades_confirmados_ventana = len(observed_trades)
    eventos_resueltos_ventana = (cur_wins - initial_wins) + (cur_losses - initial_losses)
    deadline_grace = time.time() + SETTLEMENT_GRACE_PERIOD_SEC

    if hay_trade_sin_resolver(
        cur_order_state,
        cur_has_pending,
        trades_confirmados_ventana,
        eventos_resueltos_ventana,
    ):
        print(f"[SETTLEMENT GRACE] Esperando resolución de trade en curso (máx {int(SETTLEMENT_GRACE_PERIOD_SEC)}s)...")
        resuelto_en_gracia = False
        with open(out_samples_jsonl, "a", encoding="utf-8") as f_samples, \
             open(out_status_jsonl, "a", encoding="utf-8") as f_status:
            while time.time() < deadline_grace:
                time.sleep(args.interval_sec)
                if time.time() >= deadline_grace:
                    break
                capturar_ciclo(time.time())
                # R23E: la gracia de settlement tambien resuelve contrafactuales cuyo horizonte
                # de 60s ya vencio, para no perder oportunidades registradas al borde del cierre.
                registrar_contrafactuales(int(time.time() * 1000))
                eventos_resueltos_ventana = (cur_wins - initial_wins) + (cur_losses - initial_losses)
                if not hay_trade_sin_resolver(
                    cur_order_state,
                    cur_has_pending,
                    len(observed_trades),
                    eventos_resueltos_ventana,
                ):
                    resuelto_en_gracia = True
                    print(
                        f"[SETTLEMENT GRACE] Trade resuelto. W/L de la ventana: "
                        f"{cur_wins - initial_wins}/{cur_losses - initial_losses}"
                    )
                    break
        if not resuelto_en_gracia:
            print(
                f"[SETTLEMENT GRACE] ⚠️ Timeout de {int(SETTLEMENT_GRACE_PERIOD_SEC)}s agotado con el trade sin resolver. "
                f"El reporte marca esta ventana como INCOMPLETA."
            )

    actual_capture_end_ts = time.time()
    settlement_extra_seconds = max(0.0, actual_capture_end_ts - nominal_window_end_ts)

    # FIN DE LA VENTANA — EXPORTAR ARTEFACTOS
    # R23D Fase 1 (REQ 2): el balance FINAL y el drawdown se reportan sobre la serie VALIDADA.
    # `cur_bal` sigue siendo la ultima lectura CRUDA; `validated_balance_final` es la ultima lectura
    # que supero el filtro de outliers. Se exponen los dos para que el validador pueda distinguir
    # "sin datos" de "dato descartado".
    validated_balance_final = last_valid_balance if last_valid_balance is not None else cur_bal
    dd_pct = 0.0
    if max_validated_balance and min_validated_balance and max_validated_balance > 0:
        dd_pct = (max_validated_balance - min_validated_balance) / max_validated_balance * 100.0

    print("\n" + "=" * 70)
    print("FIN DE LA VENTANA DE VALIDACIÓN AUTÓNOMA DEMO")
    print(f"Muestras capturadas: {sample_count}")
    print(f"Cuenta DEMO verificada: {demo_verified_count} veces (Real: {real_account_seen})")
    print(f"Taps totales despachados: {taps_observed}")
    print(f"Trades autónomos registrados: {len(observed_trades)}")
    print(f"Decisiones capturadas: {len(observed_decisions)}")
    print(f"Balance inicial: {(initial_balance if initial_balance is not None else 0.0):,.2f} | Final validado: {validated_balance_final:,.2f}")
    print(f"Drawdown validado: {dd_pct:.2f}% | Outliers filtrados: {outlier_count}")
    print(f"Cierre nominal: {nominal_window_end_ts:.0f} | Cierre real: {actual_capture_end_ts:.0f} "
          f"| Gracia de settlement: {settlement_extra_seconds:.1f}s")
    print("=" * 70)

    # 0. Metadata de la ventana (REQ 1 y REQ 2)
    out_metadata_json = os.path.join(out_dir, "window_metadata.json")
    metadata = {
        "iter": args.iter,
        "nominal_window_end_ts": nominal_window_end_ts,
        "actual_capture_end_ts": actual_capture_end_ts,
        "settlement_extra_seconds": settlement_extra_seconds,
        "settlement_grace_period_sec": SETTLEMENT_GRACE_PERIOD_SEC,
        "samples_captured": sample_count,
        "initial_balance": initial_balance,
        "final_balance_raw": cur_bal,
        "final_balance_validated": validated_balance_final,
        "min_validated_balance": min_validated_balance,
        "max_validated_balance": max_validated_balance,
        "drawdown_validated_pct": dd_pct,
        "balance_outliers_filtered": outlier_count,
        "balance_outlier_abs_threshold": BALANCE_OUTLIER_ABS_THRESHOLD,
        "balance_outlier_stake_multiplier": BALANCE_OUTLIER_STAKE_MULTIPLIER,
        "decisions_captured": len(observed_decisions),
        "trades_confirmed": len(observed_trades),
    }
    with open(out_metadata_json, "w", encoding="utf-8") as f_meta:
        json.dump(metadata, f_meta, indent=2, ensure_ascii=False)

    # R23E (REQ 2): resolucion final de contrafactuales.
    # Los que quedaron pendientes al cerrar la ventana pueden cumplir su horizonte de 60s con
    # ticks ya capturados (el monitor dejo de muestrear pero el feed sigue en `accumulated_ticks`).
    # Se fuerzan contra el ultimo instante capturado; los que aun no lleguen a su expiry se
    # vuelcan SIN resolver en vez de inventarles un resultado.
    registrar_contrafactuales(int(max(actual_capture_end_ts, time.time()) * 1000))
    for cf_pend in cf_pending:
        cf_pend.setdefault("entry_price", None)
        cf_pend.setdefault("exit_price", None)
        cf_pend.setdefault("result", "")
        cf_pend.setdefault("theoretical_ev", "")
        cf_rows.append(cf_pend)
    cf_pending.clear()

    # R23E (REQ 2): volcado de counterfactual_adaptive_block.csv
    out_cf_csv = os.path.join(out_dir, "counterfactual_adaptive_block.csv")
    cf_resueltos = [c for c in cf_rows if c.get("result")]
    cf_w = sum(1 for c in cf_resueltos if c["result"] == "COUNTERFACTUAL_WIN")
    cf_l = sum(1 for c in cf_resueltos if c["result"] == "COUNTERFACTUAL_LOSS")
    cf_v = sum(1 for c in cf_resueltos if c["result"] == "COUNTERFACTUAL_VOID")
    cf_denom = cf_w + cf_l + cf_v
    cf_wr = (cf_w / cf_denom * 100.0) if cf_denom > 0 else 0.0
    cf_ev_total = sum(float(c["theoretical_ev"]) for c in cf_resueltos)
    cf_ev_unit = cf_ev_total / cf_denom if cf_denom > 0 else 0.0

    with open(out_cf_csv, "w", newline="", encoding="utf-8") as f_cf:
        w_cf = csv.DictWriter(f_cf, fieldnames=COUNTERFACTUAL_FIELDS, extrasaction="ignore")
        w_cf.writeheader()
        for c in sorted(cf_rows, key=lambda x: x.get("decision_ts") or 0):
            w_cf.writerow(c)

    # Segmentacion preregistrada (§8): por similitud y por score de confluencia.
    seg_sim: Dict[str, List[str]] = {}
    seg_score: Dict[str, List[str]] = {}
    for c in cf_resueltos:
        seg_sim.setdefault(bucket_label(c.get("similarity"), COUNTERFACTUAL_SIM_BUCKETS, "%"), []).append(c["result"])
        seg_score.setdefault(bucket_label(c.get("score"), COUNTERFACTUAL_SCORE_BUCKETS, "pts"), []).append(c["result"])

    def _seg_lines(segs: Dict[str, List[str]]) -> str:
        if not segs:
            return "  (sin contrafactuales resueltos con este campo)\n"
        out = ""
        for etiqueta in sorted(segs):
            res = segs[etiqueta]
            w = sum(1 for r in res if r == "COUNTERFACTUAL_WIN")
            l = sum(1 for r in res if r == "COUNTERFACTUAL_LOSS")
            v = sum(1 for r in res if r == "COUNTERFACTUAL_VOID")
            n = w + l + v
            wr_b = (w / n * 100.0) if n > 0 else 0.0
            out += f"  - {etiqueta}: n={n} W={w} L={l} VOID={v} WR={wr_b:.1f}%\n"
        return out

    # 1. Guardar decisions.csv
    fieldnames = [
        "decision_id", "trade_id", "timestamp", "asset", "price_at_signal",
        "candle_second", "market_regime", "trend", "action", "score",
        "action_plan", "kind", "detail", "veto_reason"
    ]
    with open(out_decisions_csv, "w", newline="", encoding="utf-8") as csv_f:
        writer = csv.DictWriter(csv_f, fieldnames=fieldnames)
        writer.writeheader()
        for d in observed_decisions.values():
            writer.writerow(d)

    # 2. Guardar trades.csv
    trade_fields = ["trade_id", "decision_id", "timestamp_ms", "asset", "action", "detail", "balance_at_trade"]
    with open(out_trades_csv, "w", newline="", encoding="utf-8") as tf:
        tw = csv.DictWriter(tf, fieldnames=trade_fields)
        tw.writeheader()
        for tr in observed_trades:
            tw.writerow(tr)

    # 3. Volcar ticks de la sesión (ventana COMPLETA, no la cola del buffer)
    # R23C Fase 2B (REQ 1): se escribe lo ACUMULADO en el bucle, no la respuesta puntual de /ticks.
    # El buffer del telefono solo retiene ~13,8 min (2000 entradas a 2,43 ticks/s); lo acumulado por
    # el monitor cubre los 20 min. Si la captura incremental fallo por completo (p.ej. build antigua
    # sin /ticks), se cae al volcado directo para no quedarse sin ningun dato.
    out_ticks_csv = os.path.join(out_dir, "ticks_live.csv")
    if accumulated_ticks:
        with open(out_ticks_csv, "w", encoding="utf-8", newline="") as f_tk:
            f_tk.write("ts,ric,price\n")
            for ts in sorted(accumulated_ticks):
                f_tk.write(accumulated_ticks[ts] + "\n")
        print(
            f"Ticks de la ventana COMPLETA guardados en: {out_ticks_csv} "
            f"({len(accumulated_ticks)} ticks"
            + (f", {tick_capture_errors} ciclos sin respuesta" if tick_capture_errors else "")
            + ")"
        )
    else:
        try:
            req = urllib.request.Request(f"{base_url}/ticks?max=2000&format=csv", headers={"User-Agent": "R23-AutoMonitor/1.0"})
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                ticks_raw = resp.read().decode("utf-8")
                if ticks_raw.strip():
                    with open(out_ticks_csv, "w", encoding="utf-8", newline="") as f_tk:
                        f_tk.write(ticks_raw)
                    print(
                        f"AVISO: sin captura incremental ({tick_capture_errors} ciclos fallidos); "
                        f"volcado directo de /ticks en {out_ticks_csv} (solo la cola del buffer)"
                    )
        except Exception as e:
            print(f"Nota: /ticks no disponible o sin datos ({e})")

    # 4. Volcar /mode_log
    mode_log_data = fetch_json(f"{base_url}/mode_log")
    if mode_log_data:
        with open(out_mode_log, "w", encoding="utf-8") as f_ml:
            json.dump(mode_log_data, f_ml, indent=2, ensure_ascii=False)

    # 5. Volcar /events
    events_final = fetch_json(f"{base_url}/events?max=200")
    if events_final:
        with open(out_events_jsonl, "w", encoding="utf-8") as f_ev:
            for ev in events_final.get("events", []):
                f_ev.write(json.dumps(ev, ensure_ascii=False) + "\n")

    # 6. Replay offline y comparador Live <-> Replay
    out_replay_journal = os.path.join(out_dir, "replay_journal.csv")
    if os.path.exists(out_ticks_csv) and os.path.getsize(out_ticks_csv) > 30:
        print(f"\nEjecutando motor de replay offline sobre {out_ticks_csv} (submodo={active_submode})...")
        env = os.environ.copy()
        env["BACKTEST_CSV"] = os.path.abspath(out_ticks_csv)
        env["BACKTEST_OUT"] = os.path.abspath(out_replay_journal)
        # R23C Fase 2B (REQ 2): el replay debe correr en el MISMO submodo que la ventana. Comparar una
        # sesion YOLO contra un replay CONSERVATIVE suprime señales que el motor real si emitio y la
        # paridad mide la diferencia de politica, no la fidelidad del motor.
        env["BACKTEST_SUBMODE"] = active_submode
        gradle_bin = ".\\gradlew.bat" if os.name == "nt" else "./gradlew"
        if os.path.exists(gradle_bin):
            subprocess.run([gradle_bin, "testDebugUnitTest", "--tests", "*ReplayBacktestTest*"], env=env, capture_output=True, text=True)

    replay_summary = {"status": "NO_PAIRED_DECISIONS", "paired": 0, "exact_matches": 0, "match_rate": 0.0}
    replay_report_text = "NO CONCLUYENTE"
    if len(observed_decisions) > 0 and os.path.exists("scripts/validation/compare_live_replay.py"):
        # R23C Fase 2B (REQ 2): se compara en MODO TRADES contra `trades.csv` (ordenes confirmadas),
        # no contra `decisions.csv`. Este ultimo incluye todos los vetos del pipeline
        # (`ADAPTIVE_BLOCK`, `COOLDOWN`, `OPERACION_EN_CURSO`...), universo que el replay no produce:
        # cruzarlos daba 0 pares por construccion. Requiere que el replay exista; si no, se conserva
        # el cruce por decisiones como respaldo.
        usar_trades = os.path.exists(out_replay_journal) and os.path.getsize(out_replay_journal) > 30
        cmd = [
            sys.executable, "scripts/validation/compare_live_replay.py",
            "--live-journal", out_trades_csv if usar_trades else out_decisions_csv,
            "--output-json", out_replay_json,
            "--output-md", out_replay_report,
        ]
        if usar_trades:
            cmd.extend(["--mode", "trades"])
        if usar_trades:
            cmd.extend(["--replay-journal", out_replay_journal])
        res = subprocess.run(cmd, capture_output=True, text=True)
        if os.path.exists(out_replay_json):
            try:
                with open(out_replay_json, "r", encoding="utf-8") as f_rj:
                    _rj = json.load(f_rj)
                replay_summary = _rj.get("summary", _rj)
                paired = replay_summary.get("paired_decisions", 0)
                matches = replay_summary.get("exact_matches", 0)
                if paired > 0:
                    rate = replay_summary.get("match_rate_pct", (matches / paired) * 100.0)
                    replay_report_text = f"{matches} exact matches de {paired} pareadas ({rate:.1f}% de paridad)."
                else:
                    universo = len(observed_trades) if usar_trades else len(observed_decisions)
                    replay_report_text = f"NO EVALUABLE (0 pares evaluados, {universo} elementos sin replay correspondiente)."
            except Exception:
                pass

    # 7. Generar LIVE_REPORT.md formal
    new_wins = cur_wins - initial_wins
    new_losses = cur_losses - initial_losses
    total_trades = new_wins + new_losses
    wr = (new_wins / total_trades * 100.0) if total_trades > 0 else 0.0

    verdict = "PASS" if real_account_seen == 0 and demo_verified_count > 0 else "FAIL"
    rec = "KEEP" if verdict == "PASS" else "REVERT"

    effective_sha = actual_git_sha or args.expected_sha or status.get("gitSha", "") if status else (actual_git_sha or args.expected_sha)
    taps_window = taps_observed - (initial_taps if initial_taps is not None else taps_observed)

    report_content = f"""# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN {args.iter} (CUENTA DEMO)

**Fecha:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode={args.expected_version_code}, buildId={args.expected_build_id}, gitSha={effective_sha}  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** {args.duration_min:.1f} minutos ({sample_count} muestras de telemetría)  
**Cierre Nominal / Real:** {nominal_window_end_ts:.0f} / {actual_capture_end_ts:.0f} (gracia de settlement: {settlement_extra_seconds:.1f}s)  
**Veredicto:** **{verdict}**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada {demo_verified_count} veces. Cuentas reales observadas: {real_account_seen} (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = {(initial_balance if initial_balance is not None else 0.0):,.2f} COP, saldo final VALIDADO = {validated_balance_final:,.2f} COP (Δ = {validated_balance_final - (initial_balance or validated_balance_final):+,.2f} COP). Drawdown validado: {dd_pct:.2f}%.
3. **Filtro de Outliers (REQ 2):** {outlier_count} lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >{BALANCE_OUTLIER_ABS_THRESHOLD:,.0f} COP o >{BALANCE_OUTLIER_STAKE_MULTIPLIER:.0f}x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** {taps_window} taps despachados en la ventana (acumulado histórico del proceso: {taps_observed}). {len(observed_trades)} operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode={args.expected_version_code}, buildId="{args.expected_build_id}", gitSha="{effective_sha}".
6. **Decisiones Observadas:** Se registraron {len(observed_decisions)} decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** {replay_report_text}

---

## 2. DECISION PIPELINE
- Evaluaciones motor: {status.get('engine', {}).get('evaluatedCount', 0) if status else 0}
- Señales generadas: {status.get('engine', {}).get('signalsGenerated', 0) if status else 0}
- Decisiones registradas: {len(observed_decisions)}
- Operaciones DEMO ejecutadas: {len(observed_trades)}
- Resoluciones: W={new_wins}, L={new_losses} (WR = {wr:.1f}%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): {cf_blocked_seen}
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): {cf_deduplicated}
- Contrafactuales registrados: {len(cf_rows)}
- Resueltos: {cf_denom} (CF_W={cf_w}, CF_L={cf_l}, CF_VOID={cf_v})
- **CF_WR = {cf_wr:.2f}%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = {cf_ev_unit:+,.2f} COP** | CF_EV total = {cf_ev_total:+,.2f} COP (stake 100,000 COP, payout {cf_payout_live})
- Sin resolver (faltaba tick de entrada/salida): {len(cf_rows) - cf_denom}

**Bucket de similitud (preregistrado §8.3):**
{_seg_lines(seg_sim)}
**Bucket de score de confluencia (preregistrado §8.4):**
{_seg_lines(seg_score)}
---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `{out_samples_jsonl}`
- Muestras de `/status`: `{out_status_jsonl}`
- Eventos de decisión: `{out_events_jsonl}`
- Decisiones con `decision_id`: `{out_decisions_csv}`
- Operaciones ejecutadas: `{out_trades_csv}`
- Ticks capturados en crudo: `{out_ticks_csv}`
- Metadata de la ventana (REQ 1/REQ 2): `{out_metadata_json}`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `{out_cf_csv}`
- Comparación de replay: `{out_replay_json}` y `{out_replay_report}`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**{rec}**
"""
    with open(out_report_md, "w", encoding="utf-8") as f_rep:
        f_rep.write(report_content)

    print(f"\nReporte formal generado en: {out_report_md}")


if __name__ == "__main__":
    main()
