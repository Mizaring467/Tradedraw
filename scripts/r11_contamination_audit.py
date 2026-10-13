#!/usr/bin/env python3
"""
R11-T2: auditoria de IMPACTO HISTORICO del bug de contaminacion entre activos
en BinomoWebSocketClient.kt (processIncomingMessage aceptaba el PRIMER activo
con rate>0 y el estado de velocidad era global, no por activo).

SOLO LECTURA sobre data/ y loops/journal/: nunca escribe ni modifica un CSV.

Define contaminacion de ticks con tres criterios:
  (i)   el `ric` cambia dentro del mismo fichero (mezcla de activos en un feed);
  (ii)  salto relativo de precio > UMBRAL_SALTO entre ticks consecutivos del
        mismo fichero (firma del delta cruzado entre escalas distintas);
  (iii) solape temporal entre ficheros de activos DISTINTOS con escala de
        precio incompatible (ratio de medianas >= UMBRAL_ESCALA). Se reporta
        aparte porque es ventana de exposicion, no tick corrupto per se.

Un trade esta EXPUESTO si su ventana [timestamp, timestamp+duration_sec]
contiene un tick marcado por (i)/(ii) del mismo activo, o cae dentro de una
ventana de solape (iii) de su activo.

Wilson 95% copiado de scripts/analyze_journal.py (formula identica) para que
los numeros sean comparables con R9/R10. Cero dependencias nuevas.

Uso:
    python scripts/r11_contamination_audit.py
    python scripts/r11_contamination_audit.py --json salida.json
"""
import argparse
import bisect
import csv
import glob
import json
import math
import os
import sys
from datetime import datetime, timezone

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- Umbrales (explicitos, no magicos) ---
UMBRAL_SALTO = 0.05      # 5% de salto relativo entre ticks consecutivos
# Escala "incompatible": mezclar dos activos cuyo ratio de medianas sea >= 10x.
# Justificacion: un solo tick cruzado entre ambas escalas ya produce un delta
# relativo >= 900%, o sea >= 180x el umbral de corrupcion del 5%. Por debajo de
# 10x (p.ej. ETH 1039 vs Z-CRY/IDX 641, ratio 1.6) el delta cruzado es pequeno y
# NO explica velocidades de 1e4/s, asi que no se marca como incompatible.
UMBRAL_ESCALA = 10.0
UMBRAL_VEL_ABS = 100.0   # |tick_velocity| absurda en unidades/s
UMBRAL_VEL_REL = 0.01    # |tick_velocity|/price > 1% por segundo

Z95 = 1.959963984540054
PAYOUT = 0.8091
BREAKEVEN_WR = 100.0 / (1.0 + PAYOUT)
# T0 del PREREGISTRO_R10.md (2026-10-12 02:46:30 America/Bogota). Solo lectura:
# el fichero no se toca. Se usa para reproducir el conjunto de confirmacion.
T0_R10_MS = 1791791190823


def wilson_interval(wins, n, z=Z95):
    """Identica a scripts/analyze_journal.py:135. Devuelve (lo, hi) en %."""
    if n <= 0:
        return 0.0, 0.0
    p = wins / n
    z2 = z * z
    denom = 1.0 + z2 / n
    centro = (p + z2 / (2 * n)) / denom
    margen = (z / denom) * math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n))
    return 100.0 * max(0.0, centro - margen), 100.0 * min(1.0, centro + margen)


def to_float(value, default=0.0):
    try:
        return float(str(value).strip())
    except (ValueError, TypeError):
        return default


def ts_a_iso(ms):
    try:
        return datetime.fromtimestamp(ms / 1000.0, timezone.utc).strftime("%Y-%m-%d %H:%M")
    except (OverflowError, OSError, ValueError):
        return "?"


# --------------------------------------------------------------------------
# 1. Carga de feeds de ticks
# --------------------------------------------------------------------------
def cargar_ticks():
    """
    Devuelve {ruta: {"ric": {...}, "ts": [...], "price": [...], "mix": bool}}.
    Solo ficheros con cabecera ts,ric,price. Los journals disfrazados de .csv
    en data/ticks/ (replay_*.csv) se ignoran aqui y se tratan como trades.
    """
    feeds = {}
    patrones = [os.path.join(REPO, "data", "ticks", "*.csv"),
                os.path.join(REPO, "data", "null", "*.csv")]
    # data/null/*.csv son 100 copias byte-identicas de la misma sesion ETH.
    # Se deduplican por huella (n, primer ts, ultimo ts, mediana) para no
    # multiplicar por 100 la ventana de solape.
    for path in sorted(sum((glob.glob(p) for p in patrones), [])):
        try:
            with open(path, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
                reader = csv.reader(fh)
                header = next(reader, None)
                if not header or [h.strip() for h in header[:3]] != ["ts", "ric", "price"]:
                    continue
                ts, price, rics = [], [], set()
                for row in reader:
                    if len(row) < 3:
                        continue
                    t = to_float(row[0], None)
                    p = to_float(row[2], None)
                    if t is None or p is None:
                        continue
                    ts.append(int(t))
                    price.append(p)
                    rics.add(row[1].strip())
        except (OSError, StopIteration):
            continue
        if ts:
            feeds[path] = {"ric": rics, "ts": ts, "price": price}
    # Dedupe de feeds identicos: conserva la ruta mas corta de cada huella.
    vistos = {}
    for path in sorted(feeds, key=len):
        f = feeds[path]
        huella = (tuple(sorted(f["ric"])), len(f["ts"]), f["ts"][0], f["ts"][-1])
        if huella in vistos:
            feeds[path]["_duplicado_de"] = vistos[huella]
        else:
            vistos[huella] = path
    return feeds


def mediana(xs):
    if not xs:
        return 0.0
    s = sorted(xs)
    m = len(s) // 2
    return s[m] if len(s) % 2 else 0.5 * (s[m - 1] + s[m])


# --------------------------------------------------------------------------
# 2. Contaminacion por fichero (criterios i y ii)
# --------------------------------------------------------------------------
def analizar_criterios_i_ii(feeds):
    detalle = {}
    for path, f in feeds.items():
        n = len(f["ts"])
        mix = len(f["ric"]) > 1
        saltos = 0
        peor = 0.0
        for i in range(1, n):
            prev, cur = f["price"][i - 1], f["price"][i]
            if prev == 0:
                continue
            rel = abs(cur - prev) / abs(prev)
            if rel > peor:
                peor = rel
            if rel > UMBRAL_SALTO:
                saltos += 1
        detalle[path] = {
            "fichero": os.path.relpath(path, REPO),
            "activos": sorted(f["ric"]),
            "ticks": n,
            "rango": [ts_a_iso(f["ts"][0]), ts_a_iso(f["ts"][-1])],
            "criterio_i_ric_cambia": mix,
            "criterio_ii_saltos_gt_5pct": saltos,
            "criterio_ii_pct": 100.0 * saltos / n if n else 0.0,
            "peor_salto_relativo_pct": 100.0 * peor,
            "mediana_precio": mediana(f["price"]),
        }
    return detalle


# --------------------------------------------------------------------------
# 3. Solape multi-activo (criterio iii)
# --------------------------------------------------------------------------
def analizar_criterio_iii(feeds):
    """
    Solapes de rango temporal entre ficheros de activos distintos.
    'Incompatible' = ratio de medianas de precio >= UMBRAL_ESCALA.
    """
    unico = {}
    for path, f in feeds.items():
        if len(f["ric"]) != 1:
            continue
        unico[path] = next(iter(f["ric"]))
    rutas = sorted(unico)
    # Acumula intervalos por par de activos; luego se fusionan para no reportar
    # el mismo solape dos veces (los feeds vienen en orden A-B y B-A).
    acumulado = {}
    for a in range(len(rutas)):
        for b in range(a + 1, len(rutas)):
            pa, pb = rutas[a], rutas[b]
            if unico[pa] == unico[pb]:
                continue
            fa, fb = feeds[pa], feeds[pb]
            ini = max(fa["ts"][0], fb["ts"][0])
            fin = min(fa["ts"][-1], fb["ts"][-1])
            if ini >= fin:
                continue
            clave = tuple(sorted((unico[pa], unico[pb])))
            d = acumulado.setdefault(clave, {"intervalos": [], "feeds": []})
            d["intervalos"].append((ini, fin))
            d["feeds"].append((pa, pb, fa, fb))
    solapes = []
    ventanas_por_activo = {}
    for clave, d in sorted(acumulado.items()):
        for ini, fin in fusionar(d["intervalos"]):
            # Escoge el par de feeds que cubre el intervalo fusionado.
            pa, pb, fa, fb = max(
                d["feeds"],
                key=lambda t: min(t[2]["ts"][-1], t[3]["ts"][-1])
                - max(t[2]["ts"][0], t[3]["ts"][0]))
            ma, mb = mediana(fa["price"]), mediana(fb["price"])
            ratio = (max(ma, mb) / min(ma, mb)) if min(ma, mb) > 0 else float("inf")
            incompat = ratio >= UMBRAL_ESCALA
            ticks_a = sum(1 for t in fa["ts"] if ini <= t <= fin)
            ticks_b = sum(1 for t in fb["ts"] if ini <= t <= fin)
            solapes.append({
                "activo_a": clave[0], "activo_b": clave[1],
                "desde": ts_a_iso(ini), "hasta": ts_a_iso(fin),
                "duracion_s": round((fin - ini) / 1000.0, 1),
                "ratio_medianas": round(ratio, 2),
                "escala_incompatible": incompat,
                "ticks_a_en_solape": ticks_a, "ticks_b_en_solape": ticks_b,
            })
            if incompat:
                for ric in clave:
                    ventanas_por_activo.setdefault(ric, []).append((ini, fin))
    return solapes, ventanas_por_activo


def fusionar(ventanas):
    if not ventanas:
        return []
    vs = sorted(ventanas)
    out = [list(vs[0])]
    for lo, hi in vs[1:]:
        if lo <= out[-1][1]:
            out[-1][1] = max(out[-1][1], hi)
        else:
            out.append([lo, hi])
    return [tuple(v) for v in out]


def en_ventanas(ventanas, lo, hi):
    for a, b in ventanas:
        if lo <= b and hi >= a:
            return True
    return False


# --------------------------------------------------------------------------
# 4. Journals / trades
# --------------------------------------------------------------------------
FUENTES_TRADES = [
    "loops/journal/trade_journal_device_all.csv",
    "loops/journal/trade_journal_live.csv",
    "loops/journal/live/trade_journal_live.csv",
    "data/null_combined_trades.csv",
    "data/ticks/replay_eth.csv",
    "data/ticks/replay_eth_live.csv",
    "data/ticks/replay_trades.csv",
    "workflows/rounds/r10_journal_vivo.csv",
]


def cargar_trades():
    filas = []
    for rel in FUENTES_TRADES:
        path = os.path.join(REPO, rel)
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
                for row in csv.DictReader(fh):
                    if not row.get("timestamp"):
                        continue
                    row["_fuente"] = rel
                    filas.append(row)
        except OSError:
            continue
    return filas


# --------------------------------------------------------------------------
# 5. Auditoria de trades
# --------------------------------------------------------------------------
def mapa_activos_por_fichero(feeds):
    """nombre_fichero_minusculas -> ric, para los feeds de un solo activo."""
    mapa = {}
    for path, f in feeds.items():
        if len(f["ric"]) == 1:
            mapa[os.path.basename(path).lower()] = next(iter(f["ric"]))
    return mapa


def resolver_activo(source, mapa):
    """
    La columna `source` de los journals trae el fichero de tick, no el ric.
    Se resuelve contra los ficheros realmente presentes. Sin coincidencia
    devuelve None: no se inventa el activo del trade.
    """
    if not source:
        return None
    clave = os.path.basename(source.strip()).lower()
    if clave in mapa:
        return mapa[clave]
    for nombre, ric in mapa.items():
        if clave.startswith(nombre) or nombre.startswith(clave):
            return ric
    return None


def inferir_activo_por_precio(precio, medianas):
    """
    Fallback para journals viejos sin columna `source`: el activo es el de
    mediana de precio mas cercana dentro del 5%. Empate o fuera de rango -> None.
    """
    if precio <= 0 or not medianas:
        return None
    cands = [(abs(precio / m - 1.0), ric) for ric, m in medianas.items() if m > 0]
    if not cands:
        return None
    err, ric = min(cands)
    return ric if err <= UMBRAL_SALTO else None


def auditar_trades(filas, feeds, detalle_ii, ventanas_iii):
    """
    Marca cada trade: expuesto a tick contaminado (i/ii del mismo activo),
    expuesto a solape (iii), velocidad absurda, resuelto WIN/LOSS.
    """
    # Indice por activo: ticks marcados por criterio (ii) + ventanas de rango.
    idx = {}
    for path, f in feeds.items():
        if len(f["ric"]) != 1:
            continue
        ric = next(iter(f["ric"]))
        d = idx.setdefault(ric, {"ts": [], "rango": []})
        d["rango"].append((f["ts"][0], f["ts"][-1]))
    # Ticks con salto: guardamos su timestamp para localizar la ventana.
    saltos_por_activo = {}
    for path, f in feeds.items():
        rel = os.path.relpath(path, REPO)
        if len(f["ric"]) != 1:
            continue
        ric = next(iter(f["ric"]))
        acc = saltos_por_activo.setdefault(ric, [])
        for i in range(1, len(f["ts"])):
            prev, cur = f["price"][i - 1], f["price"][i]
            if prev == 0:
                continue
            if abs(cur - prev) / abs(prev) > UMBRAL_SALTO:
                acc.append(f["ts"][i])
    for ric in saltos_por_activo:
        saltos_por_activo[ric].sort()

    viii = {k: fusionar(v) for k, v in ventanas_iii.items()}
    # Activos con datos de tick disponibles (global, para saber si es medible).
    activos_con_tick = set(idx)
    mapa_activos = mapa_activos_por_fichero(feeds)
    medianas = {ric: mediana(feeds[p]["price"])
                for p, f in feeds.items() if len(f["ric"]) == 1
                for ric in [next(iter(f["ric"]))]}

    por_fuente = {}
    expuestos_ii = expuestos_iii = 0
    vel_absurda_abs = vel_absurda_rel = vel_total = 0
    vel_artefacto = 0
    ejemplos_vel = []
    # Pares (fuente, WIN/LOSS) para el antes/despues
    stats = {}
    for row in filas:
        fuente = row["_fuente"]
        s = stats.setdefault(fuente, {
            "n": 0, "expuestos": 0, "vel_medibles": 0,
            "win_exp": 0, "n_exp": 0, "win_noexp": 0, "n_noexp": 0,
            "sin_ticks": 0, "vel_abs": 0, "vel_rel": 0,
            "win_t0_exp": 0, "n_t0_exp": 0,
            "win_t0_noexp": 0, "n_t0_noexp": 0,
        })
        s["n"] += 1
        ts = int(to_float(row["timestamp"]))
        dur = to_float(row.get("duration_sec"), 60.0)
        ini, fin = ts, ts + int(max(dur, 0) * 1000)

        # Activo del trade: `source` cuando existe; si no, se infiere por
        # escala de price_y contra la mediana de cada feed (tolerancia 5%).
        # Sin coincidencia queda None y el trade NO se marca expuesto: no se
        # inventa el activo.
        activo = resolver_activo(row.get("source"), mapa_activos)
        if activo is None:
            activo = inferir_activo_por_precio(
                to_float(row.get("price_y"), 0.0), medianas)
        medible = activo is not None

        # (a) tick contaminado del mismo activo dentro de la ventana
        exp_ii = False
        if activo and activo in saltos_por_activo:
            lst = saltos_por_activo[activo]
            j = bisect.bisect_left(lst, ini)
            if j < len(lst) and lst[j] <= fin:
                exp_ii = True
        # (b) solape multi-activo de escala incompatible
        exp_iii = False
        if activo and activo in viii:
            exp_iii = en_ventanas(viii[activo], ini, fin)
        if exp_ii or exp_iii:
            s["expuestos"] += 1
        expuestos_ii += exp_ii
        expuestos_iii += exp_iii

        # (c) velocidad absurda
        raw = row.get("tick_velocity")
        if raw is not None:
            vel = to_float(raw, None)
            precio = to_float(row.get("price_y"), 0.0)
            if vel is not None:
                vel_total += 1
                s["vel_medibles"] += 1
                # Artefacto de logging: en null_combined_trades.csv y
                # replay_eth_live.csv la columna repite price_y, no una
                # velocidad. Se cuenta y se marca para no confundirla con
                # evidencia de contaminacion.
                if abs(vel - precio) < 1e-9:
                    vel_artefacto += 1
                    s["vel_artefacto"] = s.get("vel_artefacto", 0) + 1
                if abs(vel) > UMBRAL_VEL_ABS:
                    vel_absurda_abs += 1
                    s["vel_abs"] += 1
                if precio > 0 and abs(vel) / precio > UMBRAL_VEL_REL:
                    vel_absurda_rel += 1
                    s["vel_rel"] += 1
                if (len(ejemplos_vel) < 5 and precio > 0
                        and abs(vel) / precio > UMBRAL_VEL_REL
                        and abs(vel - precio) > 1e-9):
                    ejemplos_vel.append({
                        "fuente": fuente, "ts": ts_a_iso(ts),
                        "tick_velocity": vel, "price_y": precio,
                        "ratio": round(abs(vel) / precio, 4),
                    })

        res = (row.get("result") or "").strip().upper()
        if res not in ("WIN", "LOSS"):
            continue
        expuesto = exp_ii or exp_iii
        win = res == "WIN"
        if expuesto:
            s["n_exp"] += 1
            s["win_exp"] += 1 if win else 0
        else:
            s["n_noexp"] += 1
            s["win_noexp"] += 1 if win else 0
        # Conjunto de CONFIRMACION R10: solo post-T0 (igual que el preregistro).
        if ts >= T0_R10_MS:
            if expuesto:
                s["n_t0_exp"] += 1
                s["win_t0_exp"] += 1 if win else 0
            else:
                s["n_t0_noexp"] += 1
                s["win_t0_noexp"] += 1 if win else 0
        if not medible:
            s["sin_ticks"] += 1

        por_fuente.setdefault(fuente, {"n": 0, "exp": 0})
        por_fuente[fuente]["n"] += 1
        por_fuente[fuente]["exp"] += 1 if expuesto else 0

    return {
        "stats": stats,
        "expuestos_ii": expuestos_ii,
        "expuestos_iii": expuestos_iii,
        "vel_total": vel_total,
        "vel_absurda_abs": vel_absurda_abs,
        "vel_absurda_rel": vel_absurda_rel,
        "vel_artefacto": vel_artefacto,
        "ejemplos_vel": ejemplos_vel,
        "activos_con_tick": sorted(activos_con_tick),
    }


# --------------------------------------------------------------------------
# 6. Win rate + Wilson antes/despues (por fuente con ticks medibles)
# --------------------------------------------------------------------------
def bloque_wilson(nombre, wins, n):
    if n <= 0:
        return {"fuente": nombre, "n": 0, "wins": 0, "winrate_pct": None,
                "wilson_lo_pct": None, "wilson_hi_pct": None,
                "veredicto": "SIN MUESTRA"}
    wr = 100.0 * wins / n
    lo, hi = wilson_interval(wins, n)
    veredicto = "EDGE PRESENTE" if lo > BREAKEVEN_WR else "SIN EVIDENCIA DE EDGE"
    return {"fuente": nombre, "n": n, "wins": wins, "winrate_pct": round(wr, 2),
            "wilson_lo_pct": round(lo, 2), "wilson_hi_pct": round(hi, 2),
            "breakeven_pct": round(BREAKEVEN_WR, 2), "veredicto": veredicto}


def comparativa_wilson(stats):
    filas = []
    for fuente, s in stats.items():
        if s["n_exp"] + s["n_noexp"] == 0:
            continue
        n_tot = s["n_exp"] + s["n_noexp"]
        w_tot = s["win_exp"] + s["win_noexp"]
        filas.append({
            "fuente": fuente,
            "antes": bloque_wilson(fuente, w_tot, n_tot),
            "despues_excluyendo_expuestos": bloque_wilson(
                fuente, s["win_noexp"], s["n_noexp"]),
            "excluidos": s["n_exp"],
            "sin_ticks_emparejables": s["sin_ticks"],
            # Subconjunto de confirmacion R10 (post-T0), para la pregunta
            # "cambiaria la conclusion de R9/R10 al excluir los expuestos?".
            "r10_confirmacion_antes": bloque_wilson(
                fuente, s["win_t0_exp"] + s["win_t0_noexp"],
                s["n_t0_exp"] + s["n_t0_noexp"]),
            "r10_confirmacion_despues": bloque_wilson(
                fuente, s["win_t0_noexp"], s["n_t0_noexp"]),
            "r10_excluidos": s["n_t0_exp"],
        })
    return filas


def ventana_critica(solapes, ric_objetivo="Z-CRY/IDX"):
    """Ventanas donde Z-CRY/IDX (641.86) solapa con otro activo: el caso del bug."""
    return [s for s in solapes
            if s["escala_incompatible"]
            and ric_objetivo in (s["activo_a"], s["activo_b"])]


def resumen_r10(stats):
    """
    Conjunto de confirmacion R10 agregado (todos los journals, solo post-T0).
    Reproduce la pregunta del encargo: WR + Wilson antes y despues de excluir
    trades expuestos. n<200 => el preregistro dice NO EVALUAR todavia.
    """
    w_a = sum(s["win_t0_exp"] + s["win_t0_noexp"] for s in stats.values())
    n_a = sum(s["n_t0_exp"] + s["n_t0_noexp"] for s in stats.values())
    w_d = sum(s["win_t0_noexp"] for s in stats.values())
    n_d = sum(s["n_t0_noexp"] for s in stats.values())
    return {
        "n_preregistrado_para_evaluar": 200,
        "antes": bloque_wilson("R10 confirmacion", w_a, n_a),
        "despues_excluyendo_expuestos": bloque_wilson("R10 sin expuestos", w_d, n_d),
        "excluidos": n_a - n_d,
        "nota": ("Analisis POST-HOC / erratum. El preregistro R10 exige N>=200 "
                 "antes de evaluar; con N menor esto NO es una evaluacion "
                 "valida de la confirmacion."),
    }


# --------------------------------------------------------------------------
def unicos(feeds):
    """Feeds sin las copias duplicadas (data/null/*.csv)."""
    return {p: f for p, f in feeds.items() if "_duplicado_de" not in f}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", default=None, help="ruta opcional para volcar el JSON")
    args = ap.parse_args()

    feeds_todos = cargar_ticks()
    feeds = unicos(feeds_todos)
    detalle = analizar_criterios_i_ii(feeds)
    solapes, ventanas_iii = analizar_criterio_iii(feeds)

    total_ticks = sum(d["ticks"] for d in detalle.values())
    # Mascara por tick: cada tick se cuenta UNA vez en la union i/ii/iii.
    # (iii) solo marca los ticks DENTRO de la ventana de solape incompatible,
    # no el fichero entero; (i) si marca el fichero entero.
    viii_activo = {k: fusionar(v) for k, v in ventanas_iii.items()}
    ticks_i = ticks_ii = ticks_iii = ticks_union = 0
    for path, f in feeds.items():
        n = len(f["ts"])
        c1 = len(f["ric"]) > 1
        act = next(iter(f["ric"])) if len(f["ric"]) == 1 else None
        vent = viii_activo.get(act, [])
        for i in range(n):
            c2 = False
            if i > 0 and f["price"][i - 1] != 0:
                c2 = abs(f["price"][i] - f["price"][i - 1]) / abs(f["price"][i - 1]) > UMBRAL_SALTO
            c3 = en_ventanas(vent, f["ts"][i], f["ts"][i]) if vent else False
            ticks_i += 1 if c1 else 0
            ticks_ii += 1 if c2 else 0
            ticks_iii += 1 if c3 else 0
            ticks_union += 1 if (c1 or c2 or c3) else 0

    filas = cargar_trades()
    audit = auditar_trades(filas, feeds, detalle, ventanas_iii)
    comparativa = comparativa_wilson(audit["stats"])
    r10 = resumen_r10(audit["stats"])

    resumen = {
        "generado": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "umbrales": {
            "salto_relativo_pct": UMBRAL_SALTO * 100,
            "ratio_escala_incompatible": UMBRAL_ESCALA,
            "velocidad_absurda_abs": UMBRAL_VEL_ABS,
            "velocidad_absurda_rel_pct_s": UMBRAL_VEL_REL * 100,
            "payout": PAYOUT, "breakeven_winrate_pct": round(BREAKEVEN_WR, 2),
        },
        "ticks": {
            "ficheros_con_feed": len(detalle),
            "ficheros_duplicados_ignorados": len(feeds_todos) - len(feeds),
            "total_ticks": total_ticks,
            "criterio_i_ric_cambia": {"ficheros": sum(
                1 for d in detalle.values() if d["criterio_i_ric_cambia"]),
                "ticks": ticks_i, "pct": round(100.0 * ticks_i / total_ticks, 4)
                if total_ticks else 0.0},
            "criterio_ii_saltos_gt_5pct": {"ticks": ticks_ii,
                "pct": round(100.0 * ticks_ii / total_ticks, 4) if total_ticks else 0.0},
            "criterio_iii_solape_escala_incompatible": {"ticks": ticks_iii,
                "pct": round(100.0 * ticks_iii / total_ticks, 4) if total_ticks else 0.0},
            "union_i_ii_iii": {"ticks": ticks_union,
                "pct": round(100.0 * ticks_union / total_ticks, 4) if total_ticks else 0.0},
            "detalle_por_fichero": detalle,
        },
        "solapes_multi_activo": solapes,
        "trades": {
            "total_filas_journal": len(filas),
            "expuestos_criterio_ii": audit["expuestos_ii"],
            "expuestos_criterio_iii": audit["expuestos_iii"],
            "velocidad_medible": audit["vel_total"],
            "velocidad_artefacto_tv_igual_price": audit["vel_artefacto"],
            "velocidad_absurda_mayor_100_uds": audit["vel_absurda_abs"],
            "velocidad_absurda_mayor_1pct_precio": audit["vel_absurda_rel"],
            "ejemplos_velocidad": audit["ejemplos_vel"],
            "activos_con_tick": audit["activos_con_tick"],
        },
        "wilson_antes_despues": comparativa,
        "r10_confirmacion_agregada": r10,
    }

    # ---------------- salida legible ----------------
    w = 78
    print("=" * w)
    print("R11-T2  AUDITORIA DE IMPACTO HISTORICO - CONTAMINACION ENTRE ACTIVOS")
    print("=" * w)
    print(f"Feeds de ticks leidos: {len(detalle)}   "
          f"duplicados ignorados: {len(feeds_todos) - len(feeds)}   "
          f"ticks totales: {total_ticks:,}")
    print("-" * w)
    print("CONTAMINACION DE TICKS")
    print(f"  (i)   ric cambia en el fichero      : {ticks_i:>8,}  ({resumen['ticks']['criterio_i_ric_cambia']['pct']:.4f}%)")
    print(f"  (ii)  salto relativo > 5%           : {ticks_ii:>8,}  ({resumen['ticks']['criterio_ii_saltos_gt_5pct']['pct']:.4f}%)")
    print(f"  (iii) solape escala incompatible    : {ticks_iii:>8,}  ({resumen['ticks']['criterio_iii_solape_escala_incompatible']['pct']:.4f}%)")
    print(f"  UNION i/ii/iii                      : {ticks_union:>8,}  ({resumen['ticks']['union_i_ii_iii']['pct']:.4f}%)")
    print("-" * w)
    print(f"{'fichero':<42} {'ticks':>7} {'(i)':>4} {'(ii)':>6} {'peor%':>9}")
    for d in sorted(detalle.values(), key=lambda x: -x["ticks"]):
        print(f"{d['fichero']:<42} {d['ticks']:>7,} {('SI' if d['criterio_i_ric_cambia'] else 'no'):>4} "
              f"{d['criterio_ii_saltos_gt_5pct']:>6} {d['peor_salto_relativo_pct']:>8.3f}%")
    print("-" * w)
    print("SOLAPES MULTI-ACTIVO (escala incompatible = riesgo de delta cruzado)")
    if not solapes:
        print("  ninguno")
    for s in solapes:
        print(f"  {s['activo_a']} <-> {s['activo_b']}: {s['desde']} -> {s['hasta']} "
              f"({s['duracion_s']}s) ratio_medianas={s['ratio_medianas']} "
              f"incompatible={'SI' if s['escala_incompatible'] else 'no'} "
              f"ticks={s['ticks_a_en_solape']}/{s['ticks_b_en_solape']}")
    print("-" * w)
    print("TRADES")
    print(f"  filas de journal leidas           : {len(filas):,}")
    print(f"  expuestos a tick contaminado (ii) : {audit['expuestos_ii']:,}")
    print(f"  expuestos a solape (iii)          : {audit['expuestos_iii']:,}")
    print(f"  filas con tick_velocity medible   : {audit['vel_total']:,}")
    print(f"    de esas, tick_velocity==price_y : {audit['vel_artefacto']:,} "
          f"(artefacto de logging, NO velocidad)")
    print(f"  |vel| > 100 uds/s                 : {audit['vel_absurda_abs']:,}")
    print(f"  |vel|/price > 1%/s                : {audit['vel_absurda_rel']:,}")
    for e in audit["ejemplos_vel"]:
        print(f"    ej {e['fuente']} {e['ts']}: vel={e['tick_velocity']} price={e['price_y']} ratio={e['ratio']}")
    print("-" * w)
    print(f"WIN RATE + WILSON 95% (break-even {BREAKEVEN_WR:.2f}%)  [POST-HOC / ERRATUM]")
    print(f"{'fuente':<44} {'n':>5} {'WR%':>7} {'IC95%':>18} veredicto")
    for c in comparativa:
        a, b = c["antes"], c["despues_excluyendo_expuestos"]
        fa = a["fuente"][-44:]
        if a["n"]:
            print(f"{fa:<46} {a['n']:>5} {a['winrate_pct']:>7.2f} "
                  f"[{a['wilson_lo_pct']:>6.2f},{a['wilson_hi_pct']:>6.2f}] {a['veredicto']}")
        if b["n"]:
            etq = f"  excl. {c['excluidos']} expuestos"
            print(f"{etq:<46} {b['n']:>5} "
                  f"{b['winrate_pct']:>7.2f} [{b['wilson_lo_pct']:>6.2f},{b['wilson_hi_pct']:>6.2f}] {b['veredicto']}")
        else:
            print(f"{'  excl. expuestos':<46}     0   (sin muestra restante - todos expuestos)")
    crit = ventana_critica(solapes)
    if crit:
        print("-" * w)
        print("VENTANA CRITICA (Z-CRY/IDX ~641.86 solapando con escala incompatible)")
        for s in crit:
            print(f"  {s['activo_a']} <-> {s['activo_b']}: {s['desde']} -> {s['hasta']} "
                  f"ratio={s['ratio_medianas']} ticks={s['ticks_a_en_solape']}/{s['ticks_b_en_solape']}")
    print("-" * w)
    print(f"CONJUNTO DE CONFIRMACION R10 AGREGADO (solo post-T0 {ts_a_iso(T0_R10_MS)})")
    a, b = r10["antes"], r10["despues_excluyendo_expuestos"]
    for etq, blk in (("antes", a), ("excl. expuestos", b)):
        if blk["n"]:
            print(f"  {etq:<18} n={blk['n']:<5} WR={blk['winrate_pct']:>6.2f}%  "
                  f"IC95=[{blk['wilson_lo_pct']:.2f}%, {blk['wilson_hi_pct']:.2f}%]  "
                  f"{blk['veredicto']}")
        else:
            print(f"  {etq:<18} n=0")
    print(f"  excluidos={r10['excluidos']}  "
          f"umbral preregistrado N>={r10['n_preregistrado_para_evaluar']} "
          f"-> {'ALCANZADO' if a['n'] >= 200 else 'NO ALCANZADO (no evaluable)'}")
    print("  Break-even: 55.28% (payout medido 0.8091) / 55.56% (payout 0.80 del prereg).")
    print("  ADVERTENCIA: analisis POST-HOC (erratum), no preregistro.")
    print("=" * w)

    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(resumen, fh, indent=2, ensure_ascii=False)
        print(f"JSON escrito en {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
