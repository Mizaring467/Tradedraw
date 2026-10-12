TAREA — RONDA 10 · PASO 2 DATOS Y REPLAY ROBUSTO (agy)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). PROHIBIDO tocar app/src/main/**.
Permitido: app/src/test/**, scripts/**. Kotlin en comentarios español.
NO hagas commit ni push (lo hace el orquestador).

=== 2A · scripts/backtest/data_quality.py (NUEVO) ===
Herramienta de solo lectura sobre un CSV de ticks (formato `ts,ric,price`). Reporta:
- ticks totales, rango temporal, horas activas
- ticks/hora (media)
- timestamps duplicados (cuántos)
- timestamps no monótonos (cuántos)
- huecos > 10 s (cuántos y el mayor)
- cobertura = (ticks observados) / (ticks esperados a la tasa mediana en el rango)
Uso: `python scripts/backtest/data_quality.py <csv> [--ric ETHUSD-OTC]`
Incluye `--self-test` con aserciones (como evaluate.py).
Ejecútalo sobre data/ticks/ETHUSD-OTC_2026-10-12.csv (datos previos a T0) y reporta la salida.

=== 2B · Replay robusto a huecos (ReplayBacktestTest.kt y replay.py) ===
Implementa la regla del preregistro en AMBOS:
- Si entre el tick i y el i+1 hay un HUECO > 10 s: **reiniciar el motor** (nueva instancia de
  SyntheticCandleEngine) y **descartar cualquier entrada durante los 120 s posteriores** al final del hueco.
- **Deduplicar** timestamps: si dos ticks comparten el mismo ts, conservar uno (el primero).
- En ReplayBacktestTest: recuerda que NO puedes tocar app/src/main; el reinicio es re-crear el objeto
  `SyntheticCandleEngine()` y re-suscribir el `onSignalGenerated` en el test.

=== 2C · Modo --solo-conteo ===
En `scripts/backtest/replay.py` y `scripts/backtest/evaluate.py` añade `--solo-conteo`:
- Imprime SOLO: N de trades, tramos descartados por huecos, y calidad de datos (duplicados/huecos).
- NUNCA imprime WR, EV, PnL ni Wilson. (Esto protege la confirmación; el orquestador lo usará durante la noche.)

=== VERIFICACIÓN ===
1. `python scripts/backtest/data_quality.py --self-test` → OK
2. `python scripts/backtest/evaluate.py --self-test` → OK (sigue pasando)
3. Replay sobre ETH (previo a T0): reporta cuántos trades hay AHORA con manejo de huecos vs antes (19).

=== ENTREGABLE (español, máx 25 líneas) ===
- Archivos creados/modificados
- Salida de data_quality.py sobre ETH (previo a T0): ticks, ticks/h, duplicados, huecos>10s, cobertura
- Trades en ETH con manejo de huecos (antes 19) 
- Confirmación de --solo-conteo (que NO imprime WR/EV/PnL)
- Self-tests OK
