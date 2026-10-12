#!/usr/bin/env python3
"""Backtester por reproducción de ticks. Sin dependencias externas (stdlib pura).

Lee ticks grabados por scripts/ticks/record_ticks.js (ts,ric,price), reconstruye
las velas sintéticas de 1 minuto igual que SyntheticCandleEngine.kt y resuelve el
veredicto a 60s. Escribe trades simulados con el MISMO formato del journal del bot.

ALCANCE Y FIDELIDAD (leer antes de confiar en la salida)
  SÍ se replica:
    - Agrupación de ticks en velas por `(ts / durationMs) * durationMs`
      [SyntheticCandleEngine.kt:134-136], M1=60000ms [CandleTimeframe.kt:6], con
      open/high/low/close y cierre al cambiar de minuto [SyntheticCandleEngine.kt:187-217].
    - Geometría de vela: range, body, upperWick, lowerWick y sus ratios
      [SyntheticCandleEngine.kt:8-25].
    - Conteo de rachas de ticks asc/desc con epsilon = precio * 5e-7
      [SyntheticCandleEngine.kt:139-155].
    - Ventana temporal de disparo (:01 a :05) [MarketTick.kt:85].
    - Veredicto por precio entrada-vs-cierre a 60s
      [TradingEngine.kt:1853-1872], significancia > 1e-5.

  NO se replica (requiere el motor Kotlin, ver más abajo):
    - Las 11 estrategias MT_* del motor: QUANT_CRYPTO, MT_REJECTION,
      MT_CONFIRM_BOUNCE, MT_3_VELAS_AGOT, MT_REVERSAL, MT_CHOQUE_PULLBACK,
      MT_ENGULFING, MT_FALSE_BREAKOUT, MT_PULLBACK, MT_MOMENTUM, MT_RANGE
      [SyntheticCandleEngine.kt:1004-1377]. Dependen de S/R dinámico, pivotes
      fractales, ATR(14), choppiness y regresión lineal.
    - calculateConfluenceScore [SyntheticCandleEngine.kt:781-874] y su umbral
      `if (isYolo) 75 else 80` [SyntheticCandleEngine.kt:911].
    - El gate de disparo real: es la rama de SALDO de Binomo, no la de precio
      [TradingEngine.kt:1841-1852]. En backtest no hay broker.
    - El límite de 1 orden por vela [TradingEngine.kt:1641-1647] y el cooldown
      por racha (8000/12000/35000 ms) [TradingEngine.kt:1684-1692].

  Por eso replay.py trabaja en DOS MODOS:
    1. --mode price  (por defecto): genera una entrada por cada vela cuyo primer
       tick caiga en la ventana :01-:05, con dirección según el tick. Mide el
       VEREDICTO, no la estrategia. Es una cota superior optimista.
    2. --mode signals --signals <csv>: usa señales ya emitidas por el bot
       (ts,action) y solo resuelve el veredicto. Modo válido para evaluar una
       estrategia real.

POR QUÉ EL MOTOR KOTLIN NO SE EJECUTA AQUÍ
  `SyntheticCandleEngine` es Kotlin puro: su único import Android es
  `android.util.Log` [SyntheticCandleEngine.kt:3], y el proyecto ya ejecuta tests
  JVM sobre él con `unitTests.isReturnDefaultValues = true`
  [app/build.gradle.kts:47-49]. Ya existe incluso un test que inyecta ticks a
  `onNewTick` para recorrer tendencias: `testJournalReplayTrendChanges()`
  [SyntheticCandleEngineTest.kt:406-451].
  SÍ es viable ejecutar el motor real fuera del dispositivo. No se hace en esta
  ronda porque exigiría añadir un test de replay en app/src/test/, y la ronda 8
  restringe los archivos nuevos a scripts/backtest/ y data/ticks/.
  Opción más fiel (una sola pieza a añadir):
    app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt que lea un CSV
    de ticks por variable de entorno, llame a SyntheticCandleEngine.onNewTick y
    escriba el journal de trades simulados; se ejecutaría con
    `gradlew testDebugUnitTest --tests '*ReplayBacktestTest*'`.
  Esa ruta usa el motor real con sus 11 estrategias y la confluencia real, y es
  la única que permite atribuir edge a la estrategia en vez de al veredicto.

Uso:
    python scripts/backtest/replay.py data/ticks/Z-CRY_IDX_2026-10-12.csv
    python scripts/backtest/replay.py data/ticks/*.csv --out trades_sim.csv
    python scripts/backtest/replay.py ticks.csv --mode signals --signals senales.csv
    python scripts/backtest/replay.py --self-test
"""
import argparse
import csv
import glob
import sys
from datetime import datetime, timezone

CANDLE_MS = 60_000          # CandleTimeframe.M1
EXPIRY_SEC = 60             # duración de la opción binaria
SNIPER_WINDOW = range(1, 6)  # MarketTick.isStrictTimingWindow: segundo in 1..5
EPSILON_RATIO = 5e-7         # SyntheticCandleEngine.kt:141

JOURNAL_HEADER = [
    "timestamp", "iso_date", "strategy", "submode", "action", "confidence",
    "price_y", "stake", "base_balance", "result", "settled_balance",
    "duration_sec", "trend", "dist_support", "dist_resistance", "tick_velocity",
    "impulse", "market_regime", "candle_second", "adaptive_status", "reason",
]


class Candle:
    """Espejo de SyntheticCandle [SyntheticCandleEngine.kt:8-24]."""

    def __init__(self, open_time_ms, price):
        self.open_time_ms = open_time_ms
        self.open = price
        self.high = price
        self.low = price
        self.close = price
        self.tick_count = 1

    def update(self, price):
        if price > self.high:
            self.high = price
        if price < self.low:
            self.low = price
        self.close = price
        self.tick_count += 1

    @property
    def range(self):
        return max(self.high - self.low, 0.00001)

    @property
    def body(self):
        return abs(self.close - self.open)

    @property
    def is_green(self):
        return self.close >= self.open

    @property
    def upper_wick(self):
        return max(self.high - self.close, 0.0) if self.is_green else max(self.high - self.open, 0.0)

    @property
    def lower_wick(self):
        return max(self.open - self.low, 0.0) if self.is_green else max(self.close - self.low, 0.0)

    @property
    def upper_wick_ratio(self):
        return self.upper_wick / self.range

    @property
    def lower_wick_ratio(self):
        return self.lower_wick / self.range

    @property
    def body_ratio(self):
        return self.body / self.range


def load_ticks(path):
    """Devuelve [(ts_ms, price)] ordenado por tiempo. Rechaza filas malformadas."""
    ticks = []
    bad = 0
    with open(path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None or "ts" not in reader.fieldnames or "price" not in reader.fieldnames:
            raise SystemExit(f"{path}: se esperaba cabecera 'ts,ric,price', encontrada {reader.fieldnames}")
        for row in reader:
            try:
                ts = int(row["ts"])
                price = float(row["price"])
            except (TypeError, ValueError):
                bad += 1
                continue
            if price <= 0:
                bad += 1
                continue
            ticks.append((ts, price))
    ticks.sort(key=lambda t: t[0])
    if bad:
        print(f"  aviso: {bad} filas descartadas en {path}", file=sys.stderr)
    return ticks


def build_candles(ticks):
    """Agrupa ticks en velas de CANDLE_MS. Espejo de SyntheticCandleEngine.onNewTick."""
    candles = []
    current = None
    for ts, price in ticks:
        bucket = (ts // CANDLE_MS) * CANDLE_MS
        if current is None or current.open_time_ms != bucket:
            if current is not None:
                candles.append(current)
            current = Candle(bucket, price)
        else:
            current.update(price)
    if current is not None:
        candles.append(current)
    return candles


def price_at(ticks, target_ms):
    """Precio del último tick con ts <= target_ms (búsqueda binaria). None si no hay."""
    lo, hi = 0, len(ticks) - 1
    found = None
    while lo <= hi:
        mid = (lo + hi) // 2
        if ticks[mid][0] <= target_ms:
            found = ticks[mid]
            lo = mid + 1
        else:
            hi = mid - 1
    return found[1] if found else None


def resolve(entry_price, exit_price, action):
    """Veredicto a 60s por acción de precio. Espejo de TradingEngine.kt:1853-1872.

    La rama de saldo (TradingEngine.kt:1841-1852) no se replica: en backtest no hay
    broker, así que todo veredicto se resuelve por precio. Un empate exacto es TIE.
    """
    if exit_price is None:
        return "TIE", "TIMEOUT: sin tick posterior a la expiración"
    if exit_price == entry_price:
        return "TIE", f"PRECIO (=) Empate técnico: Entry={entry_price} vs Close={exit_price}"
    won = (exit_price > entry_price) if action == "BUY" else (exit_price < entry_price)
    sign = "+" if won else "-"
    return ("WIN" if won else "LOSS"), f"PRECIO ({sign}) Tick WS: Entry={entry_price} vs Close={exit_price}"


def load_signals(path):
    """Lee señales emitidas: cabecera 'ts,action' (o timestamp/action). Devuelve [(ts, action)]."""
    signals = []
    with open(path, newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        cols = {c.lower(): c for c in (reader.fieldnames or [])}
        ts_col = cols.get("ts") or cols.get("timestamp")
        act_col = cols.get("action")
        if not ts_col or not act_col:
            raise SystemExit(f"{path}: se esperaba 'ts/timestamp' y 'action' en la cabecera")
        for row in reader:
            try:
                ts = int(float(row[ts_col]))
            except (TypeError, ValueError):
                continue
            action = (row[act_col] or "").strip().upper()
            if action in ("BUY", "SELL"):
                signals.append((ts, action))
    signals.sort(key=lambda s: s[0])
    return signals


def simulate_price_mode(ticks, candles):
    """Una entrada por vela, en su primer tick dentro de la ventana :01-:05.

    Dirección tomada del tick (alcista -> BUY). Es una línea base optimista: sirve
    para medir el VEREDICTO, no para atribuir edge a la estrategia.
    """
    signals = []
    for idx, candle in enumerate(candles):
        # Primer tick de esta vela dentro de la ventana sniper.
        in_window = [t for t in ticks if t[0] // CANDLE_MS * CANDLE_MS == candle.open_time_ms
                     and (t[0] // 1000) % 60 in SNIPER_WINDOW]
        if not in_window:
            continue
        entry_ts, entry_price = in_window[0]
        prev_close = candles[idx - 1].close if idx > 0 else None
        if prev_close is None:
            action = "BUY"
        else:
            action = "BUY" if entry_price >= prev_close else "SELL"
        signals.append((entry_ts, action, entry_price))
    return signals


def run(ticks, signals):
    """Resuelve cada señal y devuelve filas con el formato del journal."""
    rows = []
    for entry_ts, action, entry_price in signals:
        exit_price = price_at(ticks, entry_ts + EXPIRY_SEC * 1000)
        result, reason = resolve(entry_price, exit_price, action)
        candle_second = (entry_ts // 1000) % 60
        rows.append({
            "timestamp": entry_ts,
            "iso_date": datetime.fromtimestamp(entry_ts / 1000, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
            "strategy": "REPLAY",
            "submode": "BACKTEST",
            "action": action,
            "confidence": "1.00",
            "price_y": f"{entry_price:.2f}",
            "stake": "1.00",
            "base_balance": "0.00",
            "result": result,
            "settled_balance": "0.00",
            "duration_sec": str(EXPIRY_SEC),
            "trend": "SIDEWAYS",
            "dist_support": "0.50",
            "dist_resistance": "0.50",
            "tick_velocity": "0.00",
            "impulse": "NEUTRAL",
            "market_regime": "REPLAY",
            "candle_second": str(candle_second),
            "adaptive_status": result,
            "reason": reason.replace(",", ";"),
        })
    return rows


def write_csv(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=JOURNAL_HEADER)
        writer.writeheader()
        writer.writerows(rows)


def self_test():
    """Autochequeo con series construidas a mano. Sin frameworks de test."""
    # Dos velas: precios subiendo en la primera, bajando en la segunda.
    base = 1_700_000_000_000
    ticks = []
    for i in range(10):
        ticks.append((base + i * 1000, 100.0 + i * 0.01))
    for i in range(10, 20):
        ticks.append((base + i * 1000, 100.09 - (i - 10) * 0.01))

    candles = build_candles(ticks)
    assert len(candles) == 1, f"20s deben caber en 1 vela, hay {len(candles)}"
    # Geometría: 100.00 -> 100.09 -> 100.00. El mínimo coincide con apertura y cierre,
    # así que low == open; el máximo sí supera la apertura.
    c = candles[0]
    assert c.open == 100.0, c.open
    assert c.close == 100.0, c.close
    assert c.high > c.open, c.high
    assert abs(c.low - 100.0) < 1e-9, c.low
    assert 0.0 <= c.body_ratio <= 1.0
    assert abs(c.upper_wick_ratio + c.lower_wick_ratio + c.body_ratio - 1.0) < 1e-9

    # Frontera de minuto: los cubos se alinean a múltiplos de CANDLE_MS, así que se
    # prueban dos ticks a ambos lados de una frontera real (base ya está desalineada,
    # por eso se construye una base alineada explícitamente).
    aligned = (base // CANDLE_MS) * CANDLE_MS
    split = [(aligned + CANDLE_MS - 1, 50.0), (aligned + CANDLE_MS, 51.0)]
    split_candles = build_candles(split)
    assert len(split_candles) == 2, [c.open_time_ms for c in split_candles]
    assert split_candles[0].open_time_ms == aligned
    assert split_candles[1].open_time_ms == aligned + CANDLE_MS
    assert split_candles[0].close == 50.0 and split_candles[1].open == 51.0

    # price_at
    assert price_at(ticks, base) == 100.0
    assert price_at(ticks, base + 500) == 100.0
    assert price_at(ticks, base - 1) is None
    assert price_at(ticks, base + 19_000) == ticks[-1][1]

    # resolve: BUY gana si el cierre sube, SELL si baja, empate exacto es TIE
    assert resolve(100.0, 101.0, "BUY")[0] == "WIN"
    assert resolve(100.0, 99.0, "BUY")[0] == "LOSS"
    assert resolve(100.0, 99.0, "SELL")[0] == "WIN"
    assert resolve(100.0, 101.0, "SELL")[0] == "LOSS"
    assert resolve(100.0, 100.0, "BUY")[0] == "TIE"
    assert resolve(100.0, None, "BUY")[0] == "TIE"

    # Ventana sniper
    assert (base // 1000) % 60 == 20  # base cae fuera de :01-:05

    # Simulación extremo a extremo sobre datos planos: feed congelado -> TIE
    flat = [(base + i * 1000, 641.8672875) for i in range(120)]
    signals = [(base, "BUY", 641.8672875)]
    rows = run(flat, signals)
    assert rows[0]["result"] == "TIE", rows[0]
    assert rows[0]["price_y"] == "641.87"

    print("self-test OK: 18 aserciones")


def main():
    ap = argparse.ArgumentParser(description="Backtester por reproducción de ticks (ronda 8)")
    ap.add_argument("csv", nargs="*", help="CSV(s) de ticks con cabecera ts,ric,price")
    ap.add_argument("--mode", choices=("price", "signals"), default="price")
    ap.add_argument("--signals", help="CSV de señales (ts,action) para --mode signals")
    ap.add_argument("--out", default="data/ticks/replay_trades.csv")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return

    paths = []
    for pattern in args.csv:
        paths.extend(glob.glob(pattern))
    if not paths:
        ap.error("falta al menos un CSV de ticks (o usa --self-test)")

    all_rows = []
    for path in paths:
        ticks = load_ticks(path)
        if not ticks:
            print(f"  {path}: sin ticks válidos, se omite", file=sys.stderr)
            continue
        candles = build_candles(ticks)
        span_min = (ticks[-1][0] - ticks[0][0]) / 60000.0

        if args.mode == "signals":
            if not args.signals:
                ap.error("--mode signals requiere --signals <csv>")
            price_by_ts = {ts: price for ts, price in ticks}
            signals = [(ts, act, price_by_ts.get(ts, ticks[0][1]))
                       for ts, act in load_signals(args.signals)]
        else:
            signals = simulate_price_mode(ticks, candles)

        rows = run(ticks, signals)
        all_rows.extend(rows)
        print(f"  {path}: ticks={len(ticks)} velas={len(candles)} "
              f"ventana={span_min:.1f}min señales={len(signals)}")

    all_rows.sort(key=lambda r: r["timestamp"])
    write_csv(args.out, all_rows)

    wins = sum(1 for r in all_rows if r["result"] == "WIN")
    decided = sum(1 for r in all_rows if r["result"] != "TIE")
    ties = len(all_rows) - decided
    wr = (wins / decided * 100) if decided else 0.0
    print(f"\nEscrito {args.out}: {len(all_rows)} trades "
          f"(WIN={wins} LOSS={decided - wins} TIE={ties}, WR sin TIE={wr:.2f}%)")
    if args.mode == "price":
        print("AVISO: --mode price no simula la estrategia, solo el veredicto. "
              "No usar su WR para decidir edge.")
    print("Siguiente paso: python scripts/backtest/evaluate.py " + args.out)


if __name__ == "__main__":
    sys.exit(main())
