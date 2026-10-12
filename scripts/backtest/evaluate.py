#!/usr/bin/env python3
"""Evaluador estadístico de trades. Sin dependencias externas (stdlib pura).

Calcula N, WR, IC de Wilson 95%, EV con payout configurable, máximo drawdown y
racha máxima de pérdidas, y parte la muestra en 70% desarrollo / 30% holdout
respetando el orden cronológico.

Criterio de ventaja FIJADO ANTES DE MEDIR (no se toca):
  Una versión del bot solo tiene ventaja demostrada si, en el holdout con N>=200,
  el límite INFERIOR del IC de Wilson 95% supera el breakeven 1/(1+b).

Uso:
    python scripts/backtest/evaluate.py <trades.csv> [--payout 0.83]
                                        [--result-col result] [--time-col ts]
                                        [--min-n 200]
    python scripts/backtest/evaluate.py <trades.csv> --self-test

El CSV puede ser el journal del bot (columna `result` con WIN/LOSS/TIE y
`timestamp` en epoch ms) o la salida de replay.py. Los TIE se excluyen del
cálculo de WR y se reportan aparte.
"""
import argparse
import csv
import math
import sys
from datetime import datetime, timezone

# Criterio fijado en el enunciado de la ronda 8. NO modificar.
BREAKEVEN_DEFAULT_PAYOUT = 0.83
MIN_N_HOLDOUT = 200
HOLDOUT_SPLIT = 0.70


def wilson_interval(wins: int, n: int, z: float = 1.959963984540054) -> tuple:
    """IC de Wilson 95% para una proporción. Devuelve (lower, upper, center).

    Wilson se usa en vez del IC normal porque con N pequeño y proporciones
    extremas el IC normal produce límites fuera de [0,1] y subestima la
    incertidumbre. Ecuación estándar de Wilson score interval.
    """
    if n == 0:
        return (0.0, 1.0, 0.0)
    p = wins / n
    z2 = z * z
    denom = 1.0 + z2 / n
    center = (p + z2 / (2 * n)) / denom
    half = (z / denom) * math.sqrt(p * (1 - p) / n + z2 / (4 * n * n))
    return (max(0.0, center - half), min(1.0, center + half), p)


def max_drawdown(profits: list) -> float:
    """Máximo drawdown en unidades de stake sobre la curva de capital acumulada.

    Cada trade aporta +payout si gana y -1 si pierde. Devuelve la mayor caída
    pico-a-valle de la curva acumulada (valor positivo).
    """
    equity = 0.0
    peak = 0.0
    worst = 0.0
    for pnl in profits:
        equity += pnl
        if equity > peak:
            peak = equity
        dd = peak - equity
        if dd > worst:
            worst = dd
    return worst


def max_loss_streak(results: list) -> int:
    """Racha máxima de pérdidas consecutivas (los TIE se ignoran, no rompen la racha)."""
    worst = 0
    cur = 0
    for r in results:
        if r == "LOSS":
            cur += 1
            worst = max(worst, cur)
        elif r == "WIN":
            cur = 0
        # TIE: ni suma ni reinicia — no es un resultado de la apuesta.
    return worst


def expected_value(win_rate: float, payout: float) -> float:
    """EV por unidad apostada: p*b - (1-p). Positivo => ventaja bruta."""
    return win_rate * payout - (1 - win_rate)


def breakeven_win_rate(payout: float) -> float:
    """p* = 1/(1+b). Con b=0.83 => 0.546448..."""
    return 1.0 / (1.0 + payout)


def load_trades(path: str, result_col: str, time_col: str) -> list:
    """Lee el CSV y devuelve [(orden, resultado)] solo con WIN/LOSS, en orden cronológico."""
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None:
            raise SystemExit(f"CSV vacío o sin cabecera: {path}")
        missing = [c for c in (result_col, time_col) if c not in reader.fieldnames]
        if missing:
            raise SystemExit(
                f"Faltan columnas {missing} en {path}. Cabecera: {reader.fieldnames}"
            )
        for i, row in enumerate(reader):
            result = (row.get(result_col) or "").strip().upper()
            if result not in ("WIN", "LOSS", "TIE"):
                continue
            rows.append((row.get(time_col) or "", i, result))

    def sort_key(item):
        raw = item[0]
        try:
            return (0, float(raw))
        except (TypeError, ValueError):
            return (1, raw)

    rows.sort(key=sort_key)
    ties = sum(1 for r in rows if r[2] == "TIE")
    decided = [r[2] for r in rows if r[2] != "TIE"]
    return decided, ties


def score_block(results: list, payout: float) -> dict:
    """Métricas de un bloque de resultados ya filtrado (sin TIE)."""
    n = len(results)
    wins = sum(1 for r in results if r == "WIN")
    losses = n - wins
    p = wins / n if n else 0.0
    lo, hi, _ = wilson_interval(wins, n)
    profits = [payout if r == "WIN" else -1.0 for r in results]
    return {
        "n": n,
        "wins": wins,
        "losses": losses,
        "wr": p,
        "wilson_low": lo,
        "wilson_high": hi,
        "ev": expected_value(p, payout),
        "max_drawdown": max_drawdown(profits),
        "max_loss_streak": max_loss_streak(results),
        "pnl": sum(profits),
    }


def report_block(label: str, m: dict, payout: float, breakeven: float) -> list:
    """Líneas de informe de un bloque. `verdict` se calcula fuera cuando aplica."""
    return [
        f"--- {label} ---",
        f"  N (sin TIE)        : {m['n']}",
        f"  WIN / LOSS         : {m['wins']} / {m['losses']}",
        f"  WR                 : {m['wr']*100:.2f}%",
        f"  IC Wilson 95%      : [{m['wilson_low']*100:.2f}%, {m['wilson_high']*100:.2f}%]",
        f"  Breakeven (payout {payout:.4f}): {breakeven*100:.2f}%",
        f"  EV por unidad      : {m['ev']:+.5f}",
        f"  PnL (unidades)     : {m['pnl']:+.2f}",
        f"  Max drawdown       : {m['max_drawdown']:.2f} unidades de stake",
        f"  Racha máx. pérdidas: {m['max_loss_streak']}",
    ]


def evaluate(path: str, payout: float, result_col: str, time_col: str, min_n: int) -> tuple:
    """Devuelve (lineas_informe, veredicto)."""
    results, ties = load_trades(path, result_col, time_col)
    n_total = len(results)
    if n_total == 0:
        return [f"Sin trades WIN/LOSS en {path} (TIE={ties})"], "SIN DATOS"

    cut = int(n_total * HOLDOUT_SPLIT)
    dev, hold = results[:cut], results[cut:]

    breakeven = breakeven_win_rate(payout)
    lines = [
        f"Archivo      : {path}",
        f"Payout       : {payout:.4f}",
        f"Breakeven    : {breakeven*100:.4f}%",
        f"Trades TIE   : {ties} (excluidos del WR)",
        f"Corte 70/30  : desarrollo={len(dev)}  holdout={len(hold)}",
        "",
    ]

    m_dev = score_block(dev, payout)
    m_hold = score_block(hold, payout)
    lines += report_block("DESARROLLO (70%)", m_dev, payout, breakeven)
    lines.append("")
    lines += report_block("HOLDOUT (30%)", m_hold, payout, breakeven)

    lines.append("")
    if m_hold["n"] < min_n:
        verdict = (
            f"SIN EDGE DEMOSTRADO — muestra insuficiente: holdout N={m_hold['n']} < {min_n}. "
            "No se puede evaluar el criterio."
        )
    elif m_hold["wilson_low"] > breakeven:
        verdict = (
            f"VENTAJA DEMOSTRADA en holdout: límite inferior Wilson "
            f"{m_hold['wilson_low']*100:.2f}% > breakeven {breakeven*100:.2f}% (N={m_hold['n']}). "
            "Requiere firma de verificación independiente (agy)."
        )
    else:
        verdict = (
            f"SIN EDGE DEMOSTRADO — límite inferior Wilson "
            f"{m_hold['wilson_low']*100:.2f}% <= breakeven {breakeven*100:.2f}% (N={m_hold['n']}). "
            "No se avanza a dinero real."
        )

    lines += ["VEREDICTO (automático, sin valor probatorio):", f"  {verdict}"]
    return lines, verdict


def self_test() -> None:
    """Autochequeo con valores calculables a mano. Sin frameworks de test."""
    b = breakeven_win_rate(0.83)
    assert abs(b - 0.5464480874316939) < 1e-12, b

    lo, hi, c = wilson_interval(50, 100)
    assert abs(c - 0.5) < 1e-12
    assert 0.40 < lo < 0.405, lo          # 0.4038... conocido
    assert 0.595 < hi < 0.60, hi

    # N=200, 120 ganados (WR 60%): el límite inferior NO llega al breakeven 54.64%.
    # Este es el caso que demuestra por qué el criterio exige el IC, no el WR puntual.
    lo2, _, _ = wilson_interval(120, 200)
    assert 0.53 < lo2 < 0.531, lo2
    assert lo2 < b, (lo2, b)

    # N=200, 130 ganados (WR 65%): ahora sí supera el breakeven -> ventaja demostrada
    lo2b, _, _ = wilson_interval(130, 200)
    assert lo2b > b, (lo2b, b)

    # N=200, 100 ganados: WR 50% < breakeven -> sin edge
    lo3, _, _ = wilson_interval(100, 200)
    assert lo3 < b, (lo3, b)

    assert max_drawdown([1, -1, -1, -1, 1]) == 3.0
    assert max_drawdown([1, 1, 1]) == 0.0
    assert max_loss_streak(["LOSS", "TIE", "LOSS", "WIN", "LOSS"]) == 2
    assert max_loss_streak(["WIN", "WIN"]) == 0

    assert abs(expected_value(0.5464480874316939, 0.83)) < 1e-12
    assert expected_value(0.60, 0.83) > 0
    assert expected_value(0.40, 0.83) < 0

    # Split cronológico: 10 trades -> 7 dev / 3 holdout
    cut = int(10 * HOLDOUT_SPLIT)
    assert cut == 7

    # Wilson con n=0 no explota
    assert wilson_interval(0, 0) == (0.0, 1.0, 0.0)

    print("self-test OK: 17 aserciones")


def main() -> None:
    ap = argparse.ArgumentParser(description="Evaluador estadístico de trades (ronda 8)")
    ap.add_argument("csv", nargs="?", help="CSV de trades")
    ap.add_argument("--payout", type=float, default=BREAKEVEN_DEFAULT_PAYOUT)
    ap.add_argument("--result-col", default="result")
    ap.add_argument("--time-col", default="timestamp")
    ap.add_argument("--min-n", type=int, default=MIN_N_HOLDOUT,
                    help="N mínimo del holdout para poder emitir veredicto")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return

    if not args.csv:
        ap.error("falta el CSV (o usa --self-test)")

    lines, _ = evaluate(args.csv, args.payout, args.result_col, args.time_col, args.min_n)
    print("\n".join(lines))


if __name__ == "__main__":
    sys.exit(main())
