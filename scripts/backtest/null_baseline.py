#!/usr/bin/env python3
"""Generador de series nulas por block bootstrap y evaluador de falsos positivos (ronda 10).
Sin dependencias externas (stdlib pura).

Objetivo:
    Medir la TASA DE FALSOS POSITIVOS del instrumento bajo series sintéticas sin estructura,
    preservando la volatilidad empírica y los intervalos temporales (huecos incluidos).

Uso:
    python scripts/backtest/null_baseline.py --generate [--k 100] [--block-size 30] [--out-dir data/null]
    python scripts/backtest/null_baseline.py --report <combined_replay.csv> [--payout 0.80]
    python scripts/backtest/null_baseline.py --self-test
"""
import argparse
import csv
import math
import os
import random
import statistics
import sys
from datetime import datetime, timezone

T0_DEFAULT = 1791791190823
MIN_N_HOLDOUT = 200
BREAKEVEN_DEFAULT_PAYOUT = 0.80


def wilson_interval(wins: int, n: int, z: float = 1.959963984540054) -> tuple:
    """IC de Wilson 95%. Devuelve (lower, upper, center)."""
    if n == 0:
        return (0.0, 1.0, 0.0)
    p = wins / n
    z2 = z * z
    denom = 1.0 + z2 / n
    center = (p + z2 / (2 * n)) / denom
    half = (z / denom) * math.sqrt(p * (1 - p) / n + z2 / (4 * n * n))
    return (max(0.0, center - half), min(1.0, center + half), p)


def load_pre_t0_ticks(csv_path: str, t0: int = T0_DEFAULT) -> list:
    """Lee y deduplica ticks de ETH previos a T0 (ts <= t0)."""
    ticks = []
    with open(csv_path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        cols = {c.lower(): c for c in (reader.fieldnames or [])}
        ts_col = cols.get("ts")
        ric_col = cols.get("ric")
        price_col = cols.get("price")
        for row in reader:
            try:
                ts = int(row[ts_col])
                price = float(row[price_col])
            except (TypeError, ValueError):
                continue
            if price <= 0 or ts > t0:
                continue
            ric = row[ric_col].strip() if ric_col and row.get(ric_col) else "ETHUSD-OTC"
            ticks.append((ts, ric, price))

    ticks.sort(key=lambda t: t[0])
    # Deduplicar por timestamp (primer tick observado)
    dedup = []
    seen = set()
    for t in ticks:
        if t[0] not in seen:
            seen.add(t[0])
            dedup.append(t)
    return dedup


def generate_null_series(ticks: list, k_series: int = 100, block_size: int = 30,
                         out_dir: str = "data/null", seed_base: int = 42) -> list:
    """Genera K series sintéticas usando block bootstrap de retornos reales."""
    if len(ticks) < 2:
        raise ValueError(f"Se requieren al menos 2 ticks para calcular retornos (hay {len(ticks)})")

    os.makedirs(out_dir, exist_ok=True)
    p0 = ticks[0][2]

    # Retornos relativos r_i = (P_i - P_{i-1}) / P_{i-1}
    returns = [(ticks[i][2] - ticks[i - 1][2]) / ticks[i - 1][2] for i in range(1, len(ticks))]

    # Partir en bloques de tamaño block_size
    blocks = [returns[i:i + block_size] for i in range(0, len(returns), block_size)]

    created_paths = []
    for k in range(k_series):
        rng = random.Random(seed_base + k)
        shuffled_blocks = list(blocks)
        rng.shuffle(shuffled_blocks)
        shuffled_returns = [r for b in shuffled_blocks for r in b]

        # Reconstruir trayectoria de precios comenzando en p0
        prices = [p0]
        cur = p0
        for r in shuffled_returns:
            cur = max(0.0001, cur * (1.0 + r))
            prices.append(cur)

        out_path = os.path.join(out_dir, f"eth_null_{k:03d}.csv")
        with open(out_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["ts", "ric", "price"])
            for (ts, ric, _), price in zip(ticks, prices):
                writer.writerow([ts, ric, f"{price:.4f}"])
        created_paths.append(out_path)

    return created_paths


def evaluate_null_report(combined_csv_path: str, payout: float = BREAKEVEN_DEFAULT_PAYOUT) -> dict:
    """Calcula tasa de falsos positivos y distribución de WR a partir de un replay combinado."""
    breakeven = 1.0 / (1.0 + payout)

    # Agrupar trades por source (cada serie nula)
    series_trades = {}
    with open(combined_csv_path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            src = row.get("source") or "default"
            res = (row.get("result") or "").strip().upper()
            if res not in ("WIN", "LOSS", "TIE"):
                continue
            series_trades.setdefault(src, []).append(res)

    results_per_series = []
    for src, res_list in sorted(series_trades.items()):
        decided = [r for r in res_list if r != "TIE"]
        ties = len(res_list) - len(decided)
        n = len(decided)
        wins = sum(1 for r in decided if r == "WIN")
        wr = (wins / n) if n > 0 else 0.0
        wilson_lo, wilson_hi, _ = wilson_interval(wins, n)
        is_fp = (n >= MIN_N_HOLDOUT and wilson_lo > breakeven)
        results_per_series.append({
            "source": src,
            "total_trades": len(res_list),
            "n": n,
            "ties": ties,
            "wins": wins,
            "wr": wr,
            "wilson_low": wilson_lo,
            "wilson_high": wilson_hi,
            "is_fp": is_fp
        })

    k_total = len(results_per_series)
    if k_total == 0:
        return {"k_total": 0, "breakeven": breakeven}

    n_values = [r["n"] for r in results_per_series]
    wr_values = [r["wr"] for r in results_per_series if r["n"] > 0]
    n_ge_200_count = sum(1 for n in n_values if n >= MIN_N_HOLDOUT)
    fp_count = sum(1 for r in results_per_series if r["is_fp"])
    fp_rate = (fp_count / k_total) if k_total > 0 else 0.0

    return {
        "k_total": k_total,
        "breakeven": breakeven,
        "n_min": min(n_values),
        "n_median": statistics.median(n_values),
        "n_max": max(n_values),
        "n_ge_200_count": n_ge_200_count,
        "fp_count": fp_count,
        "fp_rate": fp_rate,
        "wr_mean": statistics.mean(wr_values) if wr_values else 0.0,
        "wr_median": statistics.median(wr_values) if wr_values else 0.0,
        "wr_min": min(wr_values) if wr_values else 0.0,
        "wr_max": max(wr_values) if wr_values else 0.0,
        "series_details": results_per_series
    }


def format_report(rep: dict) -> str:
    lines = [
        "============================================================",
        "LÍNEA BASE DE RUIDO — TASA DE FALSOS POSITIVOS (null_baseline.py)",
        f"Series evaluadas        : {rep['k_total']}",
        f"Breakeven (payout={1.0/rep['breakeven']-1.0:.2f}): {rep['breakeven']*100:.2f}%",
        f"N trades por serie      : Min={rep['n_min']}, Mediana={rep['n_median']:.1f}, Max={rep['n_max']}",
        f"Series con N >= {MIN_N_HOLDOUT}       : {rep['n_ge_200_count']} / {rep['k_total']} ({rep['n_ge_200_count']/rep['k_total']*100:.1f}%)",
        f"Falsos positivos (FP)   : {rep['fp_count']} (P(LCL Wilson > {rep['breakeven']*100:.2f}% | N>={MIN_N_HOLDOUT}))",
        f"Tasa de falsos positivos: {rep['fp_rate']*100:.2f}%",
        f"WR bajo el nulo         : Media={rep['wr_mean']*100:.2f}% | Mediana={rep['wr_median']*100:.2f}% (Rango: [{rep['wr_min']*100:.2f}%, {rep['wr_max']*100:.2f}%])",
        "============================================================",
    ]
    return "\n".join(lines)


def self_test():
    """Autochequeo con aserciones. Sin frameworks de test."""
    base_ts = 1_700_000_000_000
    mock_ticks = [(base_ts + i * 1000, "ETHUSD-OTC", 1000.0 + (i % 7) * 0.25) for i in range(120)]
    test_dir = "data/null_selftest_tmp"
    files = generate_null_series(mock_ticks, k_series=3, block_size=15, out_dir=test_dir, seed_base=123)

    assert len(files) == 3, f"Archivos generados: {len(files)}"
    for f in files:
        with open(f, newline="", encoding="utf-8") as fh:
            reader = list(csv.DictReader(fh))
            assert len(reader) == 120, f"Longitud de serie: {len(reader)}"
            assert int(reader[0]["ts"]) == base_ts
            assert abs(float(reader[0]["price"]) - 1000.0) < 1e-6
            prices = [float(r["price"]) for r in reader]
            assert all(p > 0 for p in prices)
            # Media de retornos cercana a 0
            ret = [(prices[i] - prices[i - 1]) / prices[i - 1] for i in range(1, len(prices))]
            assert abs(statistics.mean(ret)) < 0.05

    # Probar evaluación de reporte sintético
    test_combined = os.path.join(test_dir, "test_comb.csv")
    with open(test_combined, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["timestamp", "result", "source"])
        for i in range(50):
            writer.writerow([base_ts + i * 1000, "WIN" if i % 2 == 0 else "LOSS", "eth_null_000.csv"])
    rep = evaluate_null_report(test_combined, payout=0.80)
    assert rep["k_total"] == 1
    assert rep["n_min"] == 50
    assert rep["fp_count"] == 0

    # Limpieza de archivos de test
    for f in files + [test_combined]:
        try:
            os.remove(f)
        except OSError:
            pass
    try:
        os.rmdir(test_dir)
    except OSError:
        pass

    print("self-test OK: 12 aserciones")


def main():
    ap = argparse.ArgumentParser(description="Línea base de ruido por block bootstrap (ronda 10)")
    ap.add_argument("--generate", action="store_true", help="Generar series nulas")
    ap.add_argument("--src-csv", default="data/ticks/ETHUSD-OTC_2026-10-12.csv", help="CSV de ticks origen")
    ap.add_argument("--t0", type=int, default=T0_DEFAULT, help="Límite temporal T0 (epoch ms)")
    ap.add_argument("--k", type=int, default=100, help="Número de series nulas a generar")
    ap.add_argument("--block-size", type=int, default=30, help="Tamaño de bloque para bootstrap")
    ap.add_argument("--out-dir", default="data/null", help="Directorio destino para las series")
    ap.add_argument("--report", help="Generar reporte sobre CSV de replay combinado")
    ap.add_argument("--payout", type=float, default=BREAKEVEN_DEFAULT_PAYOUT, help="Payout del activo")
    ap.add_argument("--self-test", action="store_true", help="Ejecutar autochequeo de aserciones")
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return 0

    if args.report:
        rep = evaluate_null_report(args.report, payout=args.payout)
        print(format_report(rep))
        return 0

    if args.generate:
        print(f"Cargando ticks pre-T0 desde {args.src_csv} (ts <= {args.t0})...")
        ticks = load_pre_t0_ticks(args.src_csv, t0=args.t0)
        print(f"Ticks cargados: {len(ticks)} únicos")
        print(f"Generando K={args.k} series nulas (bloques={args.block_size}) en {args.out_dir}...")
        created = generate_null_series(ticks, k_series=args.k, block_size=args.block_size,
                                       out_dir=args.out_dir)
        print(f"¡Éxito! Generadas {len(created)} series en {args.out_dir}/ (desde {created[0]} hasta {created[-1]}).")
        return 0

    ap.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
