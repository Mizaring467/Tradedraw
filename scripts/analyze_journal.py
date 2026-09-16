#!/usr/bin/env python3
"""
Analizador de metricas del loop de rentabilidad de TradeDraw.

Lee el trade_journal.csv (21 columnas) exportado del dispositivo y calcula las
metricas que deciden si un cambio se commitea o se revierte.

Uso:
    python scripts/analyze_journal.py <ruta_al_csv> [--baseline]

Sin --baseline imprime el analisis completo de la ventana.
Con --baseline compara contra el baseline fijado del loop.
"""
import sys
import csv
import argparse
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


def analyze(path, compare_baseline):
    rows = load_rows(path)
    if not rows:
        raise SystemExit("ERROR: el CSV no tiene filas de datos.")

    counts = Counter()
    stake_table = defaultdict(Counter)
    flagged = []
    draws = []
    results_seq = []

    for row in rows:
        effective, declared, flags, delta, base, stake = classify(row)
        counts[effective] += 1
        results_seq.append(effective)
        stake_table[stake][effective] += 1
        draws.append(delta)
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
    print("=" * w)
    print(f"Operaciones totales : {total}")
    print(f"  WIN  : {win}")
    print(f"  LOSS : {loss}")
    print(f"  TIE  : {tie}   ({100.0 * tie / total:.1f}% del total)")
    print("-" * w)
    print(f"Winrate sobre total   : {wr_total:.1f}%")
    print(f"Winrate sobre resueltos: {wr_resolved:.1f}%")
    print(f"Umbral de break-even  : {BREAKEVEN_WR:.1f}%  (payout {BASELINE['payout']:.0%})")
    gap = BREAKEVEN_WR - wr_resolved
    print(f"BRECHA para empatar   : {gap:+.1f} puntos porcentuales")
    print("-" * w)
    print(f"Saldo neto de la ventana : {net:+,.2f} COP")
    print(f"  (inicio {start_bal:,.2f} -> fin {end_bal:,.2f})")
    print(f"Peor racha perdedora     : {streak}")
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
    print(f"{'Accion':>7} | {'n':>3} | {'WIN':>4} | {'LOSS':>5} | {'TIE':>4} | {'WR':>7}")
    print("-" * 46)
    by_action = defaultdict(Counter)
    for row in rows:
        effective, _, _, _, _, _ = classify(row)
        by_action[(row.get("action") or "?").strip().upper()][effective] += 1
    for action in sorted(by_action):
        c = by_action[action]
        w_, l_, t_ = c["WIN"], c["LOSS"], c["TIE"]
        n_ = w_ + l_ + t_
        r_ = w_ + l_
        wr = 100.0 * w_ / r_ if r_ else 0.0
        alerta = ""
        if r_ >= 5 and wr < 45.0:
            alerta = "  <-- POR DEBAJO DEL AZAR"
        print(f"{action:>7} | {n_:>3} | {w_:>4} | {l_:>5} | {t_:>4} | {wr:>6.1f}%{alerta}")

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


def main():
    ap = argparse.ArgumentParser(description="Metricas del loop de rentabilidad TradeDraw.")
    ap.add_argument("csv", help="Ruta al trade_journal.csv exportado.")
    ap.add_argument("--baseline", action="store_true",
                    help="Compara contra el baseline fijado del loop.")
    args = ap.parse_args()
    analyze(args.csv, args.baseline)


if __name__ == "__main__":
    main()