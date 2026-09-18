#!/usr/bin/env python3
"""
Analizador de metricas del loop de rentabilidad de TradeDraw.

Lee el trade_journal.csv (21 columnas) exportado del dispositivo y calcula las
metricas que deciden si un cambio se commitea o se revierte.

Uso:
    python scripts/analyze_journal.py [ruta_al_csv] [--baseline] [desde]

`desde` es una fecha AAAA-MM-DD opcional: analiza solo los trades desde esa
fecha en adelante (modo comparativo de ventanas, para aislar la fase de
validacion del historico contaminado).

Sin --baseline imprime el analisis completo de la ventana.
Con --baseline compara contra el baseline fijado del loop.
Con `desde` recorta la muestra antes de calcular todo lo demas.
"""
import sys
import csv
import math
import re
import argparse
from datetime import datetime
from collections import Counter, defaultdict

# La consola de Windows usa cp1252 y revienta con caracteres no ASCII.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Baseline fijado en loops/RENTABILITY_LOOP.md — sesion 2026-09-10, 35 trades.
BASELINE = {
    "trades": 35,
    "win": 9,
    "loss": 21,
    "tie": 5,
    "wr_total": 25.7,
    "wr_resolved": 30.0,
    "net_cop": -795003.0,
    "max_loss_streak": 10,
    "payout": 0.83,   # Crypto IDX en Binomo: 83% medido en la UI (CHF/JPY OTC: 80%)
}

# Break-even para payout 0.83 -> 1 / (1 + 0.83)
BREAKEVEN_WR = 100.0 / (1.0 + BASELINE["payout"])

# CSV real por defecto del dispositivo. Es evidencia de SOLO LECTURA.
CSV_POR_DEFECTO = "loops/journal/trade_journal_device_all.csv"
CSV_VIEJO = "loops/journal/trade_journal.csv"

# Minimo de trades para declarar edge con la muestra actual.
MIN_TRADES_EDGE = 300

# z de la normal estandar para un IC del 95% (dos colas).
Z95 = 1.959963984540054

# El broker escribe el monto acreditado/debitado dentro del texto de `reason`.
RE_DIFF = re.compile(r"Diff=\s*([+-]?\d+(?:[.,]\d+)?)")


def to_float(value, default=0.0):
    """El CSV puede traer numeros sucios o vacios; nunca reventar por eso."""
    try:
        return float(str(value).strip())
    except (ValueError, TypeError):
        return default


def load_rows(path):
    with open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
        # Salta lineas de basura previas al header real.
        reader = csv.DictReader(fh)
        if reader.fieldnames is None:
            raise SystemExit(f"ERROR: {path} esta vacio o no tiene header.")
        required = {"result", "stake", "base_balance", "settled_balance"}
        missing = required - set(reader.fieldnames)
        if missing:
            raise SystemExit(
                f"ERROR: faltan columnas {sorted(missing)}.\n"
                f"Header encontrado: {reader.fieldnames}\n"
                "Si el CSV tiene 13 columnas es de una version vieja del logger:\n"
                "reinstala el APK actual y vuelve a exportar."
            )
        return list(reader)


def balance_delta(row):
    """Diferencia real de saldo. Los TIE/VOID dan 0.0."""
    return to_float(row.get("settled_balance")) - to_float(row.get("base_balance"))


def classify(row):
    """
    Reclasifica el resultado usando el delta de saldo en vez de confiar en el
    campo `result`. Esto detecta el bug CR-3: una fila marcada WIN con saldo
    corrupto (base=1.00) tiene un delta absurdo.

    Convencion: `stake` en el CSV es una unidad normalizada (1.0 / 2.0), NO un
    monto en COP. Por eso el delta en COP no se puede validar contra el stake
    sin conocer el monto base de la orden. La unica validacion de cordura
    fiable es la de balance absurdo.
    """
    declared = (row.get("result") or "").strip().upper()
    base = to_float(row.get("base_balance"))
    settled = to_float(row.get("settled_balance"))
    delta = settled - base
    stake = to_float(row.get("stake"), 1.0)

    flags = []
    # Un balance real de la cuenta esta en decenas de millones de COP.
    if base < 1_000_000 or settled < 1_000_000:
        flags.append("BALANCE_CORRUPTO")
    # Declarado resuelto pero el saldo no se movio: en realidad fue un VOID.
    if declared in ("WIN", "LOSS") and abs(delta) < 2.0:
        flags.append("DECLARADO_SIN_MOVIMIENTO")

    if abs(delta) < 2.0:
        effective = "TIE"
    elif delta > 0:
        effective = "WIN"
    else:
        effective = "LOSS"
    return effective, declared, flags, delta, base, stake


def max_streak(results, target):
    best = cur = 0
    for r in results:
        cur = cur + 1 if r == target else 0
        best = max(best, cur)
    return best


def wilson_interval(wins, n, z=Z95):
    """
    Intervalo de confianza de Wilson al 95% para una proporcion binomial.
    Solo `math`: sin scipy. Devuelve (limite_inferior, limite_superior) en %.

    Wilson se usa en vez del intervalo normal porque con n pequeno y p cerca de
    0 o 1 el normal da limites fuera de [0,100] y subestima la incertidumbre.
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


def parse_diff(reason):
    """Extrae el Diff que el broker reporto en `reason`, o None si no aparece."""
    if not reason:
        return None
    m = RE_DIFF.search(reason)
    if not m:
        return None
    try:
        return float(m.group(1).replace(",", "."))
    except ValueError:
        return None


def filtrar_desde(rows, desde):
    """
    Recorta la muestra a los trades con iso_date >= `desde` (AAAA-MM-DD).
    Comparacion por prefijo de texto: el formato ISO ordena lexicograficamente,
    asi que no hace falta parsear fechas ni arriesgarse con el mojibake.
    """
    if not desde:
        return rows
    return [r for r in rows if (r.get("iso_date") or "")[:10] >= desde]


def drawdown_maximo(saldos):
    """
    Drawdown maximo (caida pico-a-valle) en COP y en % sobre `saldos`.
    Avanza en orden cronologico guardando el pico y la peor caida vista.
    """
    if not saldos:
        return 0.0, 0.0
    pico = saldos[0]
    peor_abs = peor_pct = 0.0
    for s in saldos:
        if s > pico:
            pico = s
        caida = pico - s
        if caida > peor_abs:
            peor_abs = caida
            peor_pct = 100.0 * caida / pico if pico else 0.0
    return peor_abs, peor_pct


def tabla_desglose(titulo, grupos, ancho=46):
    """Imprime un desglose WIN/LOSS/TIE + WR para cada clave de `grupos`."""
    print(f"\n{titulo}")
    print(f"{'Clave':>9} | {'n':>4} | {'WIN':>4} | {'LOSS':>5} | {'TIE':>4} | {'WR':>7}")
    print("-" * ancho)
    for clave in sorted(grupos):
        c = grupos[clave]
        w_, l_, t_ = c["WIN"], c["LOSS"], c["TIE"]
        n_ = w_ + l_ + t_
        r_ = w_ + l_
        wr = 100.0 * w_ / r_ if r_ else 0.0
        print(f"{str(clave):>9} | {n_:>4} | {w_:>4} | {l_:>5} | {t_:>4} | {wr:>6.1f}%")


def analyze(path, compare_baseline, desde=None):
    rows = load_rows(path)
    if not rows:
        raise SystemExit("ERROR: el CSV no tiene filas de datos.")
    rows = filtrar_desde(rows, desde)
    if not rows:
        raise SystemExit(f"ERROR: ningun trade con iso_date >= {desde}.")

    counts = Counter()
    stake_table = defaultdict(Counter)
    by_action = defaultdict(Counter)
    by_submode = defaultdict(Counter)
    by_strategy = defaultdict(Counter)
    by_hour = defaultdict(Counter)
    flagged = []
    results_seq = []
    diffs_pos = []
    diffs_neg = []
    saldos = []

    for row in rows:
        effective, declared, flags, delta, base, stake = classify(row)
        counts[effective] += 1
        results_seq.append(effective)
        stake_table[stake][effective] += 1
        by_action[(row.get("action") or "?").strip().upper()][effective] += 1
        by_submode[(row.get("submode") or "?").strip().upper()][effective] += 1
        by_strategy[(row.get("strategy") or "?").strip().upper()][effective] += 1

        iso = row.get("iso_date") or ""
        by_hour[iso[11:13] if len(iso) >= 13 else "??"][effective] += 1

        settled = to_float(row.get("settled_balance"))
        if settled >= 1_000_000:
            saldos.append(settled)

        d = parse_diff(row.get("reason"))
        if d is not None:
            if d > 0:
                diffs_pos.append(d)
            elif d < 0:
                diffs_neg.append(d)

        if flags:
            flagged.append((row.get("iso_date", "?"), declared, effective, base,
                            to_float(row.get("settled_balance")), flags))

    win = counts["WIN"]
    loss = counts["LOSS"]
    tie = counts["TIE"]
    total = win + loss + tie
    resolved = win + loss
    wr_total = 100.0 * win / total if total else 0.0
    wr_resolved = 100.0 * win / resolved if resolved else 0.0
    streak = max_streak(results_seq, "LOSS")

    # El saldo neto se mide de punta a punta sobre saldos VALIDOS, no sumando
    # deltas: una sola fila corrupta (base=1.00) arruinaria la suma.
    valid_balances = []
    for row in rows:
        settled = to_float(row.get("settled_balance"))
        if settled >= 1_000_000:
            valid_balances.append(settled)
    if valid_balances:
        net = valid_balances[-1] - valid_balances[0]
        start_bal, end_bal = valid_balances[0], valid_balances[-1]
    else:
        net = start_bal = end_bal = 0.0

    w = 64
    print("=" * w)
    print(f"ANALISIS DE VENTANA - {path}")
    if desde:
        print(f"Filtro de ventana     : trades con iso_date >= {desde}")
    print("=" * w)
    print(f"Operaciones totales : {total}")
    print(f"  WIN  : {win}")
    print(f"  LOSS : {loss}")
    print(f"  TIE  : {tie}   ({100.0 * tie / total:.1f}% del total)")
    print("-" * w)
    print(f"Winrate sobre total   : {wr_total:.1f}%")
    print(f"Winrate sobre resueltos: {wr_resolved:.1f}%")

    # Doble lectura: `classify` reclasifica por delta de saldo (detecta el bug
    # CR-3), pero algunos trades se declararon LOSS con Diff=0.0 y el saldo no
    # se movio: para el broker fueron perdidas, para el saldo son TIE. Ambas
    # cifras se muestran para no mentir en ninguna direccion.
    decl = Counter()
    seq_decl = []
    for row in rows:
        d = (row.get("result") or "").strip().upper()
        d = d if d in ("WIN", "LOSS", "TIE") else "?"
        decl[d] += 1
        seq_decl.append(d)
    if decl["LOSS"] != loss or decl["TIE"] != tie:
        wr_decl = 100.0 * decl["WIN"] / (decl["WIN"] + decl["LOSS"]) if (decl["WIN"] + decl["LOSS"]) else 0.0
        print("-" * w)
        print("LECTURA ALTERNATIVA (campo `result` declarado por el bot)")
        print(f"  WIN {decl['WIN']} / LOSS {decl['LOSS']} / TIE {decl['TIE']}"
              f"   -> WR sobre resueltos {wr_decl:.1f}%")
        print(f"  Peor racha perdedora declarada: {max_streak(seq_decl, 'LOSS')}"
              f"   (vs {streak} por delta de saldo)")
        print("  La diferencia son trades marcados LOSS con Diff=0.0 (sin "
              "movimiento de saldo).")

    # --- Payout medido desde el campo `reason` (fuente: el propio broker) ---
    payout_medido = None
    if diffs_pos and diffs_neg:
        gan = sum(diffs_pos) / len(diffs_pos)
        per = sum(diffs_neg) / len(diffs_neg)
        payout_medido = 100.0 * abs(gan) / abs(per)
    print("-" * w)
    print("PAYOUT MEDIDO (extraido del campo `reason` del broker)")
    print(f"  Victorias con Diff+  : {len(diffs_pos)}   media {sum(diffs_pos) / len(diffs_pos):+,.2f} COP" if diffs_pos else "  Victorias con Diff+  : 0")
    print(f"  Derrotas con Diff-   : {len(diffs_neg)}   media {sum(diffs_neg) / len(diffs_neg):+,.2f} COP" if diffs_neg else "  Derrotas con Diff-   : 0")
    if payout_medido:
        be = 100.0 / (1.0 + payout_medido / 100.0)
        print(f"  Payout real medido   : {payout_medido:.2f}%")
        print(f"  Break-even implicado : {be:.1f}%")
    else:
        be = BREAKEVEN_WR
        print("  Payout no medible: falta algun Diff+ o Diff-; se usa el baseline.")
        print(f"  Break-even de baseline: {be:.1f}%  (payout {BASELINE['payout']:.0%})")

    # El IC de Wilson es el criterio de exito: su limite INFERIOR debe superar
    # el break-even. Se calcula sobre trades resueltos (los TIE no deciden).
    lo, hi = wilson_interval(win, resolved)
    print("-" * w)
    print("INTERVALO DE CONFIANZA (Wilson 95%, sobre resueltos)")
    print(f"  n resueltos          : {resolved}")
    print(f"  IC 95%               : [{lo:.1f}%, {hi:.1f}%]")
    print(f"  Break-even           : {be:.1f}%")
    print(f"  Brecha vs break-even : limite inferior {lo - be:+.1f} pp")

    dd_abs, dd_pct = drawdown_maximo(saldos)
    print("-" * w)
    print("RIESGO")
    print(f"  Drawdown maximo      : {dd_abs:+,.2f} COP  ({dd_pct:.1f}% desde el pico)")
    print(f"  Peor racha perdedora : {streak}")
    print(f"  Mejor racha ganadora : {max_streak(results_seq, 'WIN')}")
    pnl_neto = sum(diffs_pos) + sum(diffs_neg)
    print(f"  PnL neto acumulado   : {pnl_neto:+,.2f} COP  (suma de Diff del broker)")
    print(f"  Saldo neto de la ventana : {net:+,.2f} COP")
    print(f"  (inicio {start_bal:,.2f} -> fin {end_bal:,.2f})")
    print("  Nota: el PnL usa el Diff de `reason`, no el delta de saldo: 5 filas")
    print("  traen base_balance=1.00 (corrupto) y envenenarian la suma.")
    print("=" * w)

    print("\nDESGLOSE POR NIVEL DE MARTINGALA")
    print(f"{'Stake':>8} | {'WIN':>4} | {'LOSS':>5} | {'TIE':>4} | {'WR':>7}")
    print("-" * 40)
    for stake in sorted(stake_table):
        c = stake_table[stake]
        w_, l_, t_ = c["WIN"], c["LOSS"], c["TIE"]
        r_ = w_ + l_
        wr = 100.0 * w_ / r_ if r_ else 0.0
        print(f"{stake:>8.2f} | {w_:>4} | {l_:>5} | {t_:>4} | {wr:>6.1f}%")

    if flagged:
        print(f"\nFILAS SOSPECHOSAS ({len(flagged)}) — datos que envenenan el aprendizaje")
        for iso, declared, effective, base, settled, flags in flagged:
            print(f"  {iso} declarado={declared} efectivo={effective} "
                  f"base={base:,.2f} settled={settled:,.2f} -> {','.join(flags)}")
    else:
        print("\nSin filas sospechosas: la telemetria de esta ventana es confiable.")

    # Desglose por accion: es el discriminador que revelo que el lado SELL esta roto.
    print("\nDESGLOSE POR ACCION (BUY vs SELL)")
    print(f"{'Accion':>7} | {'n':>4} | {'WIN':>4} | {'LOSS':>5} | {'TIE':>4} | {'WR':>7}")
    print("-" * 46)
    for action in sorted(by_action):
        c = by_action[action]
        w_, l_, t_ = c["WIN"], c["LOSS"], c["TIE"]
        n_ = w_ + l_ + t_
        r_ = w_ + l_
        wr = 100.0 * w_ / r_ if r_ else 0.0
        alerta = ""
        if r_ >= 5 and wr < 45.0:
            alerta = "  <-- POR DEBAJO DEL AZAR"
        print(f"{action:>7} | {n_:>4} | {w_:>4} | {l_:>5} | {t_:>4} | {wr:>6.1f}%{alerta}")
        # Racha por accion: la racha global puede ocultar una racha local peor.
        seq_acc = [(x, a) for x, a in zip(results_seq, [ (r.get("action") or "?").strip().upper() for r in rows ]) if a == action]
        print(f"{'':>7}   peor racha perdedora del lado {action}: "
              f"{max_streak([x for x, _ in seq_acc], 'LOSS')}")

    # Mismo desglose usando el `result` declarado, para comparar ambas lecturas.
    by_action_decl = defaultdict(Counter)
    for row in rows:
        d = (row.get("result") or "").strip().upper()
        by_action_decl[(row.get("action") or "?").strip().upper()][
            d if d in ("WIN", "LOSS", "TIE") else "?"] += 1
    print(f"\n{'Accion':>7} | (lectura por `result` declarado)")
    print("-" * 46)
    for action in sorted(by_action_decl):
        c = by_action_decl[action]
        w_, l_, t_ = c["WIN"], c["LOSS"], c["TIE"]
        r_ = w_ + l_
        wr = 100.0 * w_ / r_ if r_ else 0.0
        print(f"{action:>7} | n={w_ + l_ + t_:<4} WIN {w_:<3} LOSS {l_:<3} TIE {t_:<3} WR {wr:>5.1f}%")

    tabla_desglose("DESGLOSE POR SUBMODE", by_submode)
    tabla_desglose("DESGLOSE POR STRATEGY", by_strategy)
    tabla_desglose("DESGLOSE POR HORA DEL DIA (iso_date)", by_hour)

    if compare_baseline:
        print("\n" + "=" * w)
        print("COMPARACION CONTRA BASELINE")
        print("=" * w)
        print(f"{'Metrica':<26} | {'Baseline':>12} | {'Ventana':>12} | Veredicto")
        print("-" * w)
        checks = [
            ("Winrate (resueltos)", BASELINE["wr_resolved"], wr_resolved, True),
            ("Saldo neto (COP)", BASELINE["net_cop"], net, True),
            ("Trades fantasma", BASELINE["tie"], tie, False),
            ("Peor racha", BASELINE["max_loss_streak"], streak, False),
        ]
        wr_ok = False
        for name, base_v, cur_v, higher_better in checks:
            better = cur_v > base_v if higher_better else cur_v < base_v
            mark = "MEJOR" if better else "PEOR"
            print(f"{name:<26} | {base_v:>12,.1f} | {cur_v:>12,.1f} | {mark}")
            if name.startswith("Winrate"):
                wr_ok = better
        # El saldo cuenta como avance solo si ademas es positivo.
        net_ok = net > BASELINE["net_cop"] and net >= 0

        print("-" * w)
        print("\nCRITERIO DE COMMIT (ambos deben cumplirse):")
        print(f"  [{'X' if wr_ok else ' '}] Winrate supera la ventana anterior")
        print(f"  [{'X' if net_ok else ' '}] Saldo neto >= 0")
        if wr_ok and net_ok:
            print("\n  >>> AVANCE SIGNIFICATIVO: commit permitido.")
        else:
            print("\n  >>> SIN AVANCE: revertir (git checkout -- .) y registrar como refutado.")

    # --- VEREDICTO --- Criterio explicito: el limite INFERIOR del IC de Wilson
    # debe superar el break-even, y hacen falta al menos MIN_TRADES_EDGE trades.
    print("\n" + "=" * w)
    print("VEREDICTO")
    print("=" * w)
    print(f"  Criterio: limite inferior del IC Wilson 95% ({lo:.1f}%) > break-even ({be:.1f}%)")
    print(f"            y al menos {MIN_TRADES_EDGE} trades (hay {total})")
    print("-" * w)
    if total < MIN_TRADES_EDGE:
        faltan = MIN_TRADES_EDGE - total
        print(f"  VEREDICTO: MUESTRA INSUFICIENTE (n<{MIN_TRADES_EDGE})")
        print(f"  Faltan {faltan} trades para poder declarar edge con este criterio.")
        print(f"  (El IC actual [{lo:.1f}%, {hi:.1f}%] es demasiado ancho para decidir.)")
    elif lo > be:
        print("  VEREDICTO: EDGE PRESENTE")
        print(f"  El peor caso del IC ({lo:.1f}%) sigue por encima del break-even ({be:.1f}%).")
    else:
        print("  VEREDICTO: SIN EVIDENCIA DE EDGE")
        print(f"  El limite inferior del IC ({lo:.1f}%) NO supera el break-even ({be:.1f}%).")
        print(f"  El win rate observado ({wr_resolved:.1f}%) no es distinguible del azar con esta muestra.")
    print("=" * w)


def main():
    ap = argparse.ArgumentParser(description="Metricas del loop de rentabilidad TradeDraw.")
    ap.add_argument("csv", nargs="?", default=None,
                    help=f"Ruta al trade_journal.csv (por defecto {CSV_POR_DEFECTO}).")
    ap.add_argument("--baseline", action="store_true",
                    help="Compara contra el baseline fijado del loop.")
    ap.add_argument("desde", nargs="?", default=None,
                    help="Fecha AAAA-MM-DD: analiza solo los trades desde ahi.")
    args = ap.parse_args()

    path = args.csv
    desde = args.desde
    # Si el primer posicional no es un CSV, es la fecha (permite omitir la ruta).
    if path and path.endswith(".csv") is False:
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", path):
            if desde is not None:
                raise SystemExit("ERROR: se paso la fecha dos veces.")
            desde = path
            path = None
    if path is None:
        import os
        path = CSV_POR_DEFECTO if os.path.exists(CSV_POR_DEFECTO) else CSV_VIEJO

    # Valida el formato Y que la fecha exista (rechaza 2026-13-99).
    if desde:
        try:
            datetime.strptime(desde, "%Y-%m-%d")
        except ValueError:
            raise SystemExit(f"ERROR: fecha invalida '{desde}'. Formato AAAA-MM-DD.")

    analyze(path, args.baseline, desde)


if __name__ == "__main__":
    main()