TAREA — RONDA 9 · PASO 3 BACKTEST FIEL (codex)

Repo: C:\Users\heidy\Tradedraw (rama main). Kotlin, comentarios en español.
NO modifiques código de producción. Solo CREA un archivo de test nuevo.
NO hagas commit, push ni install.

=== OBJETIVO ===
Crear `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt` que:
1. Lea un CSV de ticks REAL (mismo formato que data/ticks/*.csv): cabecera `ts,ric,price`.
2. La RUTA del CSV llega por variable de entorno `BACKTEST_CSV` (si no existe, el test se auto-salta
   con `org.junit.Assume.assumeTrue(...)` para no romper la suite normal).
3. Reproduzca los ticks llamando `SyntheticCandleEngine.onNewTick(tick)` en orden cronológico,
   construyendo `MarketTick(asset="ETHUSD-OTC", price=..., timestampMs=ts)`.
4. Ejecute las 11 estrategias: como el motor las habilita según `currentStrategy`, itera el test sobre
   `AutoTradeStrategy.values()` o, si es más fiel, usa AUTO_ADAPTIVE (que activa allowAll) y REPORTALO.
5. Capture cada señal vía `engine.onSignalGenerated = { action, reason -> ... }` y aplique la confluencia
   REAL (el propio dispatchSniperSignal ya la aplica con `if (isYolo) 75 else 80`).
6. Resuelva cada señal como WIN/LOSS usando el precio de ENTRADA vs el precio N segundos después
   (expiración: `CandleTimeframe.M1.seconds` = 60s), con la misma convención que Binomo:
   CALL gana si precio_final > precio_entrada; PUT si precio_final < precio_entrada; empate exacto = TIE.
7. Escriba un journal simulado en un archivo temporal con el MISMO esquema que el journal real
   (cabecera de `TradeJournalLogger.HEADER`: timestamp,iso_date,strategy,submode,action,confidence,
   price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,
   tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason).
   La ruta de salida también por variable de entorno `BACKTEST_OUT` (default: build/backtest_out.csv).

=== RESTRICCIONES ===
- El motor SÍ corre en test JVM (`unitTests.isReturnDefaultValues = true` ya está). No toques el build.
- NO cambies el motor ni sus umbrales. Es un test de SOLO LECTURA sobre el motor.
- Documenta en el propio archivo: (a) qué estrategia(s) usaste, (b) cómo resolviste el resultado,
   (c) supuestos (p.ej. expiración 60s, sin slippage).

=== VERIFICACIÓN ===
1. Corre con el CSV de ETH ya existente:
   `$env:BACKTEST_CSV="data/ticks/ETHUSD-OTC_2026-10-12.csv"` y
   `.\gradlew.bat testDebugUnitTest --tests "*ReplayBacktestTest*" --no-daemon`
2. Reporta cuántas señales generó y el CSV de salida.
3. ADEMÁS, corre el replay en Python sobre el MISMO CSV para contrastar:
   `python scripts/backtest/replay.py data/ticks/ETHUSD-OTC_2026-10-12.csv`
   (revisa su --help para los flags exactos). Reporta en qué difiere del test Kotlin (nº de señales).

=== ENTREGABLE (español, máx 25 líneas) ===
- Ruta del archivo de test creado
- Estrategia(s) usada(s) y cómo resolviste WIN/LOSS
- Nº de señales del test Kotlin vs replay.py (diferencia)
- Ruta del CSV simulado de salida
- Supuestos declarados
- Bloqueos si algo no compila o no hay datos suficientes
