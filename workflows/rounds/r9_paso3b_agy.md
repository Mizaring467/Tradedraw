TAREA — RONDA 9 · REFINAR REPLAY (fidelidad 1 orden/vela) + BUILD/INSTALL (agy)

Repo: C:\Users\heidy\Tradedraw (rama main). Kotlin.
PASO 3b: solo TOCA el archivo de test ReplayBacktestTest.kt (NO producción).
PASO 5 (build/install): lo hará el orquestador; tú solo compila y reporta.

=== PROBLEMA DETECTADO ===
El replay genera señales DUPLICADAS en el mismo minuto (trade #3/4/5 en 00:48:0x; #6/7/8/9 en 00:54:0x).
El motor real aplica `lastExecutedCandleEpochMinute` (1 orden por vela) en `executeHeadlessTrade`
(TradingEngine.kt ~L1629). El replay llama `onNewTick` sin ese guard, así que sobre-cuenta señales.

=== FIX (solo en ReplayBacktestTest.kt) ===
En el loop de reproducción, cuando `onSignalGenerated` dispare una señal, IGNORA las señales
adicionales cuyo `timestampMs / 60000` (mismo minuto de vela) ya haya producido una entrada.
Es decir, replica el guard real: `if (minutoVela == ultimoMinutoOperado) no registrar`.
NO cambies el motor ni `SyntheticCandleEngine`. Solo el test.

=== VERIFICACIÓN ===
1. .\gradlew.bat testDebugUnitTest --no-daemon
2. Re-ejecuta el replay sobre data/ticks/ETHUSD-OTC_2026-10-12.csv:
   $env:BACKTEST_CSV="data/ticks/ETHUSD-OTC_2026-10-12.csv"
   .\gradlew.bat testDebugUnitTest --tests "*ReplayBacktestTest*" --no-daemon --rerun-tasks
   Reporta el nº de trades AHORA (debe ser <= 10, sin duplicados en el mismo minuto).

=== ENTREGABLE (español, máx 15 líneas) ===
- Cambio aplicado en ReplayBacktestTest.kt (1-2 líneas)
- Nº de señales antes (10) vs después
- BUILD SUCCESSFUL/FAILED + conteo de tests
