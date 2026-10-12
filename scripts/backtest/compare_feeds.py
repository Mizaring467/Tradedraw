"""Compara la volatilidad real de dos CSV de ticks. Read-only, stdlib pura.

Uso: python scripts/backtest/compare_feeds.py <a.csv> <b.csv> ...
"""
import csv
import sys


def stats(path):
    rows = list(csv.DictReader(open(path, encoding="utf-8-sig")))
    prices = [float(r["price"]) for r in rows]
    times = [int(r["ts"]) for r in rows]
    span = (times[-1] - times[0]) / 1000.0
    lo, hi = min(prices), max(prices)
    # Rango por vela de 60s: la métrica que realmente ve el motor.
    per_candle = {}
    for t, p in zip(times, prices):
        per_candle.setdefault(t // 60000, []).append(p)
    ranges = [(max(v) - min(v)) / min(v) for v in per_candle.values() if len(v) >= 3]
    return {
        "path": path, "n": len(prices), "span": span,
        "lo": lo, "hi": hi, "abs": hi - lo, "rel": (hi - lo) / lo,
        "tps": len(prices) / span if span else 0.0,
        "candles": len(per_candle),
        "max_candle_rel": max(ranges) if ranges else 0.0,
        "mean_candle_rel": sum(ranges) / len(ranges) if ranges else 0.0,
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("uso: compare_feeds.py <a.csv> <b.csv> ...")
    print(f"{'archivo':38} {'ticks':>6} {'span':>7} {'t/s':>6} {'rango_rel':>12} {'vela_max':>11} {'vela_media':>11}")
    for p in sys.argv[1:]:
        s = stats(p)
        name = s["path"].split("\\")[-1].split("/")[-1]
        print(f"{name:38} {s['n']:>6} {s['span']:>6.0f}s {s['tps']:>6.2f} "
              f"{s['rel']:>12.4e} {s['max_candle_rel']:>11.4e} {s['mean_candle_rel']:>11.4e}")
