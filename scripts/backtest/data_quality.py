#!/usr/bin/env python3
"""Auditoría de calidad de datos sobre CSV de ticks (ts,ric,price).
Sin dependencias externas (stdlib pura).

Reporta:
- ticks totales, rango temporal, horas activas
- ticks/hora (media)
- timestamps duplicados (cuántos)
- timestamps no monótonos (cuántos)
- huecos > 10 s (cuántos y el mayor)
- cobertura = (ticks observados) / (ticks esperados a la tasa mediana en el rango)

Uso:
    python scripts/backtest/data_quality.py <csv> [--ric ETHUSD-OTC] [--t0 <epoch_ms>] [--until-t0]
    python scripts/backtest/data_quality.py --self-test
"""
import argparse
import csv
import os
import statistics
import sys
from datetime import datetime, timezone


def analyze_ticks(ticks: list) -> dict:
    """Analiza una lista de tuplas (ts_ms, ric, price) y calcula métricas de calidad."""
    if not ticks:
        return {
            "total_ticks": 0,
            "ric": "N/A",
            "time_range_iso": "N/A",
            "total_hours": 0.0,
            "active_hours": 0.0,
            "ticks_per_hour_mean": 0.0,
            "ticks_per_hour_active": 0.0,
            "duplicates": 0,
            "non_monotonic": 0,
            "gaps_gt_10s_count": 0,
            "max_gap_s": 0.0,
            "median_dt_ms": 0.0,
            "expected_ticks": 0,
            "coverage_pct": 0.0,
        }

    total_ticks = len(ticks)
    ric = ticks[0][1] if ticks else "N/A"

    # Detección de no monotonía en el orden de lectura original
    non_monotonic = 0
    for i in range(1, total_ticks):
        if ticks[i][0] < ticks[i - 1][0]:
            non_monotonic += 1

    # Ordenar por timestamp para análisis de intervalos y duplicados
    sorted_ticks = sorted(ticks, key=lambda t: t[0])

    # Conteo de duplicados y conservación del primero por ts
    duplicates = 0
    dedup_ticks = []
    prev_ts = None
    for t in sorted_ticks:
        if t[0] == prev_ts:
            duplicates += 1
        else:
            dedup_ticks.append(t)
            prev_ts = t[0]

    min_ts = dedup_ticks[0][0]
    max_ts = dedup_ticks[-1][0]
    total_span_ms = max_ts - min_ts
    total_hours = total_span_ms / 3600000.0 if total_span_ms > 0 else 0.0

    iso_start = datetime.fromtimestamp(min_ts / 1000.0, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    iso_end = datetime.fromtimestamp(max_ts / 1000.0, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    time_range_iso = f"{iso_start} -> {iso_end}"

    # Análisis de intervalos entre ticks consecutivos únicos
    dts = []
    gaps_gt_10s = []
    for i in range(1, len(dedup_ticks)):
        dt = dedup_ticks[i][0] - dedup_ticks[i - 1][0]
        dts.append(dt)
        if dt > 10000:
            gaps_gt_10s.append(dt)

    gaps_count = len(gaps_gt_10s)
    max_gap_s = (max(gaps_gt_10s) / 1000.0) if gaps_gt_10s else 0.0
    total_gap_time_ms = sum(gaps_gt_10s)
    active_time_ms = max(0, total_span_ms - total_gap_time_ms)
    active_hours = active_time_ms / 3600000.0 if active_time_ms > 0 else total_hours

    ticks_per_hour_mean = (total_ticks / total_hours) if total_hours > 0 else float(total_ticks)
    ticks_per_hour_active = (total_ticks / active_hours) if active_hours > 0 else float(total_ticks)

    # Tasa mediana entre ticks normales (<= 10s) para estimar cobertura
    normal_dts = [d for d in dts if 0 < d <= 10000]
    if normal_dts:
        median_dt_ms = float(statistics.median(normal_dts))
        expected_ticks = int(round(total_span_ms / median_dt_ms)) if median_dt_ms > 0 else total_ticks
        coverage = (len(dedup_ticks) / expected_ticks) if expected_ticks > 0 else 1.0
        coverage_pct = min(100.0, max(0.0, coverage * 100.0))
    else:
        median_dt_ms = 1000.0
        expected_ticks = total_ticks
        coverage_pct = 100.0

    return {
        "total_ticks": total_ticks,
        "unique_ticks": len(dedup_ticks),
        "ric": ric,
        "time_range_iso": time_range_iso,
        "min_ts": min_ts,
        "max_ts": max_ts,
        "total_hours": total_hours,
        "active_hours": active_hours,
        "ticks_per_hour_mean": ticks_per_hour_mean,
        "ticks_per_hour_active": ticks_per_hour_active,
        "duplicates": duplicates,
        "non_monotonic": non_monotonic,
        "gaps_gt_10s_count": gaps_count,
        "max_gap_s": max_gap_s,
        "median_dt_ms": median_dt_ms,
        "expected_ticks": expected_ticks,
        "coverage_pct": coverage_pct,
    }


def load_csv_ticks(path: str, filter_ric: str = None, until_ts: int = None) -> list:
    """Lee CSV con cabecera ts,ric,price y devuelve lista de tuplas (ts, ric, price)."""
    ticks = []
    with open(path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None:
            raise SystemExit(f"CSV vacío: {path}")
        cols = {c.lower(): c for c in reader.fieldnames}
        ts_col = cols.get("ts")
        ric_col = cols.get("ric")
        price_col = cols.get("price")
        if not ts_col or not price_col:
            raise SystemExit(f"CSV debe contener columnas 'ts' y 'price': {path}")

        for row in reader:
            try:
                ts = int(row[ts_col])
                price = float(row[price_col])
            except (ValueError, TypeError):
                continue
            ric = row[ric_col].strip() if ric_col and row.get(ric_col) else "N/A"
            if filter_ric and ric.upper() != filter_ric.upper():
                continue
            if until_ts is not None and ts > until_ts:
                continue
            ticks.append((ts, ric, price))
    return ticks


def format_report(res: dict, csv_name: str) -> str:
    lines = [
        "============================================================",
        "CALIDAD DE DATOS (data_quality.py)",
        f"Archivo            : {csv_name}",
        f"Activo (RIC)       : {res['ric']}",
        f"Ticks totales      : {res['total_ticks']:,} (Únicos: {res['unique_ticks']:,})",
        f"Rango temporal     : {res['time_range_iso']}",
        f"Horas totales      : {res['total_hours']:.2f} h (Horas activas: {res['active_hours']:.2f} h)",
        f"Ticks/hora (media) : {res['ticks_per_hour_mean']:.1f} (activa: {res['ticks_per_hour_active']:.1f})",
        f"TS duplicados      : {res['duplicates']}",
        f"TS no monótonos    : {res['non_monotonic']}",
        f"Huecos > 10 s      : {res['gaps_gt_10s_count']} (Mayor hueco: {res['max_gap_s']:.1f} s / {res['max_gap_s']/60.0:.2f} min)",
        f"Intervalo mediano  : {res['median_dt_ms']:.1f} ms",
        f"Ticks esperados    : {res['expected_ticks']:,}",
        f"Cobertura          : {res['coverage_pct']:.2f}%",
        "============================================================",
    ]
    return "\n".join(lines)


def self_test():
    """Autochequeo con datos controlados. Sin dependencias externas."""
    base_ts = 1_700_000_000_000
    # Construcción de serie de prueba:
    # 50 ticks a paso 1000ms
    # 1 duplicado (mismo ts que el #49)
    # 1 hueco de 25s (25000ms)
    # 50 ticks a paso 1000ms
    # 1 tick desordenado (no monótono) insertado antes del final
    test_ticks = []
    cur_ts = base_ts
    for i in range(50):
        test_ticks.append((cur_ts, "TEST-ASSET", 100.0 + i))
        cur_ts += 1000

    # Duplicado
    test_ticks.append((cur_ts - 1000, "TEST-ASSET", 149.0))

    # Hueco > 10 s (25 s)
    cur_ts += 25000
    for i in range(50):
        test_ticks.append((cur_ts, "TEST-ASSET", 200.0 + i))
        cur_ts += 1000

    # Tick no monótono
    test_ticks.append((base_ts + 500, "TEST-ASSET", 99.0))

    res = analyze_ticks(test_ticks)

    assert res["total_ticks"] == 102, f"Total ticks: {res['total_ticks']}"
    assert res["duplicates"] == 1, f"Duplicados: {res['duplicates']}"
    assert res["non_monotonic"] == 1, f"No monótonos: {res['non_monotonic']}"
    assert res["gaps_gt_10s_count"] == 1, f"Huecos > 10s: {res['gaps_gt_10s_count']}"
    assert abs(res["max_gap_s"] - 26.0) < 1e-6, f"Max gap: {res['max_gap_s']}"
    assert res["median_dt_ms"] == 1000.0, f"Median dt: {res['median_dt_ms']}"
    assert 0.0 < res["coverage_pct"] < 100.0, f"Cobertura: {res['coverage_pct']}"

    # Serie vacía no explota
    empty_res = analyze_ticks([])
    assert empty_res["total_ticks"] == 0

    print("self-test OK: 8 aserciones")


def main():
    ap = argparse.ArgumentParser(description="Auditoría de calidad de datos sobre CSV de ticks (ronda 10)")
    ap.add_argument("csv", nargs="?", help="Ruta al CSV de ticks (ts,ric,price)")
    ap.add_argument("--ric", default=None, help="Filtrar por RIC específico (ej. ETHUSD-OTC)")
    ap.add_argument("--t0", type=int, default=None, help="Filtrar ticks hasta timestamp epoch ms")
    ap.add_argument("--until-t0", action="store_true", help="Leer data/T0.txt y filtrar ticks hasta T0")
    ap.add_argument("--self-test", action="store_true", help="Ejecutar autochequeo de aserciones")
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return 0

    if not args.csv:
        ap.error("falta el CSV de ticks (o usa --self-test)")

    until_ts = args.t0
    if args.until_t0 and until_ts is None:
        t0_path = "data/T0.txt"
        if not os.path.exists(t0_path):
            t0_path = os.path.join(os.path.dirname(__file__), "..", "..", "data", "T0.txt")
        if os.path.exists(t0_path):
            with open(t0_path, encoding="utf-8") as f:
                content = f.read().strip()
                if content.isdigit():
                    until_ts = int(content)

    ticks = load_csv_ticks(args.csv, filter_ric=args.ric, until_ts=until_ts)
    report_dict = analyze_ticks(ticks)
    print(format_report(report_dict, args.csv))
    return 0


if __name__ == "__main__":
    sys.exit(main())
