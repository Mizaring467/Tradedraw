#!/usr/bin/env python3
"""
Analisis de segmentacion: existe algun subconjunto de la operativa con ventaja real?

Unifica todos los journals del repo (mas el pull del dispositivo si esta), deduplica
por timestamp, y mide el win rate por cada dimension de contexto disponible,
cruzando SIEMPRE con el intervalo de Wilson 95% y con correccion por comparaciones
multiples (Benjamini-Hochberg + Bonferroni).

Este analisis es de SOLO LECTURA sobre los CSV: nunca los modifica.

Uso:
    python scripts/edge_analysis.py
"""
import sys
import csv
import io
import os
import math
import itertools
from collections import Counter, defaultdict
from datetime import datetime

# Estadistica autocontenida: NO se importa de analyze_journal.py.
#
# Motivo: `wilson_interval` no existe en el HEAD del repo (llego con un diff
# local de otra sesion) y el encargo exige no reescribir ese archivo. Copiar
# aqui las dos funciones matematicas deja este script independiente de la
# version de analyze_journal.py que haya en disco.
# Formulas identicas a analyze_journal.py para que los numeros coincidan.

# z de la normal estandar para un IC del 95% (dos colas).
Z95 = 1.959963984540054


def to_float(value, default=0.0):
    """El CSV puede traer numeros sucios o vacios; nunca reventar por eso."""
    try:
        return float(str(value).strip())
    except (ValueError, TypeError):
        return default


def wilson_interval(wins, n, z=Z95):
    """
    Intervalo de confianza de Wilson al 95% para una proporcion binomial.
    Solo `math`. Devuelve (limite_inferior, limite_superior) en %.
    """
    if n <= 0:
        return 0.0, 0.0
    p = wins / n
    z2 = z * z
    denom = 1.0 + z2 / n
    centro = (p + z2 / (2 * n)) / denom
    margen = (z / denom) * math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n))
    lo = max(0.0, centro - margen)
    hi = min(1.0, centro + margen)
    return 100.0 * lo, 100.0 * hi

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Payout medido sobre el journal real -> 1/(1+payout).
PAYOUT = 0.8091
BREAKEVEN_WR = 100.0 / (1.0 + PAYOUT)

# Minimo de trades para que un segmento deje de ser anecdotico.
MIN_N = 30

# Esperado por el usuario para validar que el parsing no tiene bug.
WR_GLOBAL_ESPERADO = 42.0

# Fuentes: (ruta, encoding). El UTF-16 es un export viejo del dispositivo.
FUENTES = [
    ("loops/journal/trade_journal_device_all.csv", "utf-8"),
    ("loops/journal/trade_journal_live.csv", "utf-8"),
    ("loops/journal/live/trade_journal_live.csv", "utf-8"),
    ("scratch/device_journal_live.csv", "utf-8"),
    ("scratch/journal_live.csv", "utf-8"),
    ("scratch/journal_device.csv", "utf-16"),
    ("scratch/device_pull/tj_export.csv", "utf-8"),
]

# Esquema canonico de 13 columnas (el logger v1.3 instalado escribia esto).
COLS_CANONICAS = [
    "timestamp", "iso_date", "strategy", "submode", "action", "confidence",
    "price_y", "stake", "base_balance", "result", "settled_balance",
    "duration_sec", "reason",
]

# Columnas de contexto que el encargo pide pero que NINGUN csv contiene.
CTX_AUSENTE = [
    "trend", "dist_support", "dist_resistance", "tick_velocity",
    "impulse", "market_regime", "candle_second", "adaptive_status",
]


def cargar_fuente(path, enc):
    """Lee un journal y devuelve (header, filas). Vacio si no existe o no parsea."""
    if not os.path.exists(path):
        return None, []
    with open(path, "r", encoding=enc, errors="replace", newline="") as fh:
        texto = fh.read()
    # Salta lineas de basura previas al header real.
    lineas = [l for l in texto.splitlines() if l.strip()]
    if not lineas:
        return None, []
    reader = csv.DictReader(io.StringIO("\n".join(lineas)))
    if reader.fieldnames is None:
        return None, []
    filas = [r for r in reader if (r.get("iso_date") or "").strip()]
    return list(reader.fieldnames), filas


def clasificar(row):
    """
    Resultado efectivo por delta de saldo, igual que analyze_journal.classify:
    un WIN declarado sin movimiento de saldo es en realidad un TIE.
    """
    declarado = (row.get("result") or "").strip().upper()
    delta = to_float(row.get("settled_balance")) - to_float(row.get("base_balance"))
    if abs(delta) < 2.0:
        return "TIE", declarado, delta
    return ("WIN" if delta > 0 else "LOSS"), declarado, delta


def wilson(w, n):
    """IC de Wilson 95% para w aciertos de n, devuelto en %."""
    return wilson_interval(w, n)


def wald_p(w, n, p0=BREAKEVEN_WR / 100.0):
    """
    p-valor de una cola (H1: p > break-even) por aproximacion normal.
    Solo `math`: erfc de la normal estandar.
    """
    if n <= 0:
        return 1.0
    p = w / n
    se = math.sqrt(p0 * (1.0 - p0) / n)
    if se == 0:
        return 1.0
    z = (p - p0) / se
    # P(Z > z) = 0.5 * erfc(z / sqrt(2))
    return 0.5 * math.erfc(z / math.sqrt(2.0))


def benjamini_hochberg(pvals):
    """
    Benjamini-Hochberg: devuelve los p ajustados (mismo orden de entrada).
    Controla la tasa de falsos descubrimientos (FDR) sobre m contrastes.
    """
    m = len(pvals)
    if m == 0:
        return []
    orden = sorted(range(m), key=lambda i: pvals[i])
    ajustado = [0.0] * m
    previo = 1.0
    # Recorre de mayor a menor p para forzar monotonía.
    for rango, idx in enumerate(reversed(orden), start=1):
        k = m - rango + 1          # posicion 1-based en orden ascendente
        val = pvals[idx] * m / k
        previo = min(previo, val)
        ajustado[idx] = min(1.0, previo)
    return ajustado


def tramo(valor, cortes):
    """Etiqueta de tramo para un valor continuo."""
    for lo, hi, etq in cortes:
        if lo <= valor < hi:
            return etq
    return cortes[-1][2]


def hhmm(iso):
    """Hora HH:MM de un iso_date, o '??' si no parsea."""
    try:
        return iso[11:16]
    except Exception:
        return "??"


def unificar(fuentes):
    """Concatena y deduplica por timestamp. Devuelve (filas, traza_de_fuentes)."""
    traza = []
    vistas = {}          # timestamp -> fila
    duplicados = 0
    colisiones = 0       # mismo timestamp con contenido distinto: NO debe pasar
    for path, enc in fuentes:
        hdr, filas = cargar_fuente(path, enc)
        if hdr is None:
            traza.append((path, "AUSENTE", 0, 0))
            continue
        faltan = [c for c in COLS_CANONICAS if c not in hdr]
        if faltan:
            traza.append((path, "ESQUEMA_DISTINTO", len(filas), 0))
            continue
        nuevos = 0
        for f in filas:
            ts = (f.get("timestamp") or "").strip()
            clave = ts if ts else (f.get("iso_date") or "") + (f.get("reason") or "")
            if clave in vistas:
                prev = vistas[clave]
                # Un timestamp repetido con contenido distinto seria una colision
                # real (dos trades distintos colapsados) y falsearia la muestra.
                firma = lambda r: (r.get("iso_date"), r.get("action"),
                                   r.get("result"), r.get("reason"))
                if firma(prev) != firma(f):
                    colisiones += 1
                duplicados += 1
                continue
            vistas[clave] = f
            nuevos += 1
        traza.append((path, "OK", len(filas), nuevos))
    return list(vistas.values()), traza, duplicados, colisiones


def main():
    print("=" * 78)
    print("ANALISIS DE EDGE POR SEGMENTACION - TradeDraw")
    print("=" * 78)

    # ---------- 1. Esquema y unificacion ----------
    print("\n[1] ESQUEMA DECLARADO POR EL LOGGER (TradeJournalLogger.kt)")
    print("    21 columnas esperadas: las 13 canonicas + 8 de contexto.")
    print("    Columnas de contexto buscadas:")
    print("      " + ", ".join(CTX_AUSENTE))

    filas, traza, duplicados, colisiones = unificar(FUENTES)

    print("\n[2] FUENTES Y DEDUPLICACION (por `timestamp`)")
    print(f"    {'archivo':<46} {'estado':<16} {'leidas':>7} {'nuevas':>7}")
    print("    " + "-" * 76)
    for path, estado, leidas, nuevas in traza:
        print(f"    {path:<46} {estado:<16} {leidas:>7} {nuevas:>7}")

    presentes = set(filas[0].keys()) if filas else set()
    ausentes = [c for c in CTX_AUSENTE if c not in presentes]
    print(f"\n    Columnas de contexto presentes en el dataset: "
          f"{len(CTX_AUSENTE) - len(ausentes)}/{len(CTX_AUSENTE)}")
    if ausentes:
        print(f"    AUSENTES (no analizables): {', '.join(ausentes)}")

    if not filas:
        raise SystemExit("ERROR: no hay filas tras unificar.")
    print(f"\n    Trades unificados: {len(filas)}  (duplicados descartados: {duplicados})")
    print(f"    Colisiones de timestamp (contenido distinto): {colisiones}"
          f"  {'OK' if colisiones == 0 else '<-- REVISAR: trades distintos colapsados'}")

    # Enriquecimiento por fila.
    for f in filas:
        f["_efectivo"], f["_declarado"], f["_delta"] = clasificar(f)
        f["_stake"] = to_float(f.get("stake"), 1.0)
        f["_conf"] = to_float(f.get("confidence"))
        f["_dur"] = to_float(f.get("duration_sec"))
        f["_hora"] = (f.get("iso_date") or "")[11:13]

    res = Counter(f["_efectivo"] for f in filas)
    resueltos = [f for f in filas if f["_efectivo"] in ("WIN", "LOSS")]
    n_res = len(resueltos)
    n_win = sum(1 for f in resueltos if f["_efectivo"] == "WIN")
    wr = 100.0 * n_win / n_res if n_res else 0.0
    lo, hi = wilson(n_win, n_res)

    fechas = sorted({(f.get("iso_date") or "")[:10] for f in filas})
    print(f"\n    Rango de fechas: {fechas[0]} -> {fechas[-1]}  ({len(fechas)} dias)")
    print(f"    Conteo por resultado: WIN={res['WIN']} LOSS={res['LOSS']} TIE={res['TIE']}")

    print("\n[3] VALIDACION DEL PARSING")
    # Control: reproducir el 42.0% ya medido sobre la fuente historica original.
    hdr_ref, base = cargar_fuente("loops/journal/trade_journal_device_all.csv", "utf-8")
    base_res = []
    for f in base:
        e, _, _ = clasificar(f)
        if e in ("WIN", "LOSS"):
            base_res.append(e)
    n_ref = len(base_res)
    w_ref = base_res.count("WIN")
    wr_ref = 100.0 * w_ref / n_ref if n_ref else 0.0
    lo_ref, hi_ref = wilson(w_ref, n_ref)
    print(f"    CONTROL -- solo loops/journal/trade_journal_device_all.csv")
    print(f"      n={n_ref}  WR={wr_ref:.1f}%  IC95 [{lo_ref:.1f},{hi_ref:.1f}]")
    print(f"      esperado por el usuario: 42.0% (n=193) IC95 [35.2,49.0]")
    cuadra = abs(wr_ref - WR_GLOBAL_ESPERADO) <= 0.1 and n_ref == 193
    print(f"      VEREDICTO: {'CUADRA EXACTO' if cuadra else 'NO CUADRA'}")

    print(f"\n    Dataset UNIFICADO (todas las fuentes deduplicadas)")
    print(f"      n={n_res}  WR={wr:.1f}%  IC95 [{lo:.1f},{hi:.1f}]")
    print(f"      +{n_res - n_ref} trades resueltos nuevos respecto al control")
    print(f"      diferencia de WR: {wr - wr_ref:+.1f} puntos")
    if not cuadra:
        raise SystemExit("ERROR: el control no reproduce el 42.0% — hay bug de parsing.")
    print(f"\n    Break-even exigido  : {BREAKEVEN_WR:.1f}%  (payout {PAYOUT:.4f})")
    print(f"    Margen del global   : {wr - BREAKEVEN_WR:+.1f} puntos")

    # ---------- 2. Dimensiones ----------
    dims = {}
    dims["action"] = lambda f: (f.get("action") or "?").strip().upper()
    dims["strategy"] = lambda f: (f.get("strategy") or "?").strip()
    dims["submode"] = lambda f: (f.get("submode") or "?").strip()
    dims["stake"] = lambda f: f"{f['_stake']:.2f}"
    dims["confidence"] = lambda f: tramo(f["_conf"], [
        (0.0, 0.60, "conf<0.60"), (0.60, 0.70, "conf 0.60-0.70"),
        (0.70, 0.80, "conf 0.70-0.80"), (0.80, 0.90, "conf 0.80-0.90"),
        (0.90, 99.0, "conf>=0.90"),
    ])
    dims["duration_sec"] = lambda f: tramo(f["_dur"], [
        (0.0, 60.0, "dur<60s"), (60.0, 62.0, "dur 60-61s"),
        (62.0, 70.0, "dur 62-69s"), (70.0, 1e9, "dur>=70s"),
    ])
    dims["hora_del_dia"] = lambda f: f"{f['_hora']}h" if f["_hora"].isdigit() else "??"
    dims["hora_por_tercios"] = lambda f: (
        tramo(int(f["_hora"]), [(0, 8, "madrugada 00-07"), (8, 16, "manana 08-15"),
                                (16, 24, "tarde 16-23")])
        if f["_hora"].isdigit() else "??")
    dims["dia"] = lambda f: (f.get("iso_date") or "")[:10]

    # Segmentos simples + combinaciones de dos dimensiones.
    segmentos = []          # (etiqueta, wins, n)
    for nombre, fn in dims.items():
        g = defaultdict(lambda: [0, 0])
        for f in resueltos:
            k = fn(f)
            g[k][1] += 1
            if f["_efectivo"] == "WIN":
                g[k][0] += 1
        for k, (w, n) in g.items():
            segmentos.append((f"{nombre}={k}", w, n))

    pares = list(itertools.combinations(sorted(dims), 2))
    for a, b in pares:
        g = defaultdict(lambda: [0, 0])
        for f in resueltos:
            k = (dims[a](f), dims[b](f))
            g[k][1] += 1
            if f["_efectivo"] == "WIN":
                g[k][0] += 1
        for k, (w, n) in g.items():
            segmentos.append((f"{a}={k[0]} x {b}={k[1]}", w, n))

    # ---------- 3. Correccion por comparaciones multiples ----------
    evaluables = [(et, w, n) for et, w, n in segmentos if n >= 5]
    pvals = [wald_p(w, n) for _, w, n in evaluables]
    m = len(pvals)
    bh = benjamini_hochberg(pvals)
    alpha = 0.05
    bonf = alpha / m if m else 0.0

    filas_res = []
    for i, (et, w, n) in enumerate(evaluables):
        p = pvals[i]
        pa = bh[i]
        l, h = wilson(w, n)
        filas_res.append({
            "et": et, "w": w, "n": n, "wr": 100.0 * w / n,
            "lo": l, "hi": h, "p": p, "p_bh": pa,
            "bh_ok": pa < alpha and w / n > BREAKEVEN_WR / 100.0,
            "bonf_ok": p < bonf and w / n > BREAKEVEN_WR / 100.0,
        })

    print("\n[4] CONTRASTES REALIZADOS")
    print(f"    Segmentos simples + combinaciones de 2 dimensiones : {len(segmentos)}")
    print(f"    Con n>=5 (evaluables estadisticamente)             : {m}")
    print(f"    Correccion Bonferroni (alpha/m)                    : p < {bonf:.3e}")
    print(f"    Correccion Benjamini-Hochberg (FDR 5%)             : p_ajustado < {alpha}")

    # ---------- 4. Tabla de mejores segmentos ----------
    filas_res.sort(key=lambda r: (-r["wr"], -r["n"]))
    print("\n[5] SEGMENTOS ORDENADOS POR WR (los 25 mejores)")
    print(f"    {'segmento':<48} {'n':>4} {'WR':>7} {'IC95 Wilson':>17} {'p':>9} {'p_BH':>8} {'ver':>5}")
    print("    " + "-" * 104)
    for r in filas_res[:25]:
        marca = "n<30" if r["n"] < MIN_N else ("BH" if r["bh_ok"] else "no")
        print(f"    {r['et'][:48]:<48} {r['n']:>4} {r['wr']:>6.1f}% "
              f"[{r['lo']:>6.1f},{r['hi']:>6.1f}] {r['p']:>9.2e} {r['p_bh']:>8.3f} {marca:>5}")

    # ---------- 5. Los que sobreviven ----------
    con_n = [r for r in filas_res if r["n"] >= MIN_N]
    sobreviven = [r for r in con_n if r["bh_ok"]]
    sobreviven_bonf = [r for r in con_n if r["bonf_ok"]]

    print(f"\n[6] SEGMENTOS CON n>={MIN_N} (no anecdoticos)")
    print(f"    Total con n>={MIN_N}                : {len(con_n)}")
    print(f"    Sobreviven a Benjamini-Hochberg  : {len(sobreviven)}")
    print(f"    Sobreviven a Bonferroni          : {len(sobreviven_bonf)}")

    mejores_n = sorted(con_n, key=lambda r: -r["wr"])[:10]
    if mejores_n:
        print(f"\n    Top-10 por WR entre los n>={MIN_N}:")
        print(f"    {'segmento':<48} {'n':>4} {'WR':>7} {'IC95 Wilson':>17} {'p_BH':>8} {'BH?':>5}")
        print("    " + "-" * 96)
        for r in mejores_n:
            print(f"    {r['et'][:48]:<48} {r['n']:>4} {r['wr']:>6.1f}% "
                  f"[{r['lo']:>6.1f},{r['hi']:>6.1f}] {r['p_bh']:>8.3f} "
                  f"{'SI' if r['bh_ok'] else 'no':>5}")
        print("\n    Ningun limite INFERIOR del IC alcanza el break-even:  "
              f"{sum(1 for r in con_n if r['lo'] > BREAKEVEN_WR)}/{len(con_n)}")

    # ---------- 6. El lado enfermo: SELL ----------
    print("\n[7] DETALLE DEL LADO SELL vs BUY")
    for lado in ("BUY", "SELL"):
        sub = [f for f in resueltos if (f.get("action") or "").upper() == lado]
        if not sub:
            continue
        w = sum(1 for f in sub if f["_efectivo"] == "WIN")
        l, h = wilson(w, len(sub))
        print(f"    {lado:<5} n={len(sub):>4} WR={100.0 * w / len(sub):>5.1f}% "
              f"IC95 [{l:>5.1f},{h:>5.1f}]  p={wald_p(w, len(sub)):.2e}")

    # ---------- 7. Veredicto ----------
    print("\n" + "=" * 78)
    print("VEREDICTO")
    print("=" * 78)
    print(f"  Break-even exigido                         : {BREAKEVEN_WR:.1f}%")
    print(f"  WR global unificado                        : {wr:.1f}%  IC95 [{lo:.1f},{hi:.1f}]")
    print(f"  Limite inferior del IC global vs break-even: {lo - BREAKEVEN_WR:+.1f} puntos")
    print(f"  Segmentos evaluados (m)                    : {m}")
    print(f"  Segmentos con n>={MIN_N}                        : {len(con_n)}")
    print(f"  Supervivientes a correccion multiple       : "
          f"{len(sobreviven)} (BH) / {len(sobreviven_bonf)} (Bonferroni)")

    if not sobreviven and not sobreviven_bonf:
        print("\n  NO EXISTE EDGE IDENTIFICABLE CON LOS DATOS ACTUALES.")
        print("  Ningun segmento supera el break-even de forma estadisticamente")
        print("  defendible tras corregir por comparaciones multiples.")
    else:
        print("\n  ATENCION: hay segmentos que sobreviven. Revisar [6].")

    if ausentes:
        print(f"\n  LIMITACION CRITICA: {len(ausentes)}/{len(CTX_AUSENTE)} ejes de contexto")
        print("  del encargo NO EXISTEN en ningun journal del disco ni del dispositivo:")
        print(f"    {', '.join(ausentes)}")
        print("  El APK v1.3 instalado escribe 13 columnas; el esquema de 21")
        print("  (commit 4169f38) no esta compilado en el dispositivo.")
    print("=" * 78)


if __name__ == "__main__":
    main()