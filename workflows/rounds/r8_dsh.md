# Ronda 8 · DSH — Rigor de medición y backtest

Rama `r8-backtest` (desde `main` @ `8660ee1`), tag `baseline-r7` antes de tocar nada. Nuevos: `scripts/ticks/`, `scripts/backtest/`, `data/ticks/`.
`BinomoWebSocketClient.kt`/`TradeDrawHttpBridge.kt` NO tocados. Cero cambios a umbrales, señales o estrategia.

**A) TIE (solo diagnóstico).** Los 2 TIE (21:43:17, 22:06:18): SELL en Z-CRY/IDX, `candle_second=1`, `Diff=0.0` a 75s y
`base_balance=38425735.68` idéntico; el WIN de 22:48 parte del mismo valor → saldo inmóvil 65 min, ninguna orden
registrada. El tap sí ocurre: `⚡ Tap ejecutado con éxito vía ROOT en (249.84, 1340.8)`.
Mecanismo (logcat rotó: 1er ts `22:40:16`, no cubre los TIE): `23:26.716 AutoTradeAccessibilityService desconectado.
Intentando auto-heal vía ROOT...` tras `23:17.040 ... unbinded transitoriamente`. `autoHealAccessibility()`
(`AutoTradeAccessibilityService.kt:212-227`) hace toggle real de `enabled_accessibility_services` y MIUI rechaza el
restart (`MIUILOG- Reject RestartService`). Con `instance == null` la lectura cae al caché (`:465`) y `baseBal` usa el
mismo (`TradingEngine.kt:1775`) → diff 0.0 exacto y TIE falso sin orden perdida.
**No determinado**: cuál de los 2 mecanismos causó esos TIE. Verificable con `-G 16M` vivo y `/status.accessibilityActive`
en `false` al liquidar. Parche propuesto (NO aplicado): `TradingEngine.kt:1841` exigir saldo fresco
(`instance != null && age<3000ms`); si no, `TIE_INCIERTO` no contabilizado.

**B) `price_y` — truncamiento de registro, NO stale.** WS vivo `641.86728657`…`682` (36 rates, 27 únicos) ·
`/status.currentPriceY` `641.8673` · journal `641.87` (5 filas). `price_y = pendingEntry` (`TradingEngine.kt:1901`)
sí es el tick real (`:1780`); se destruye al escribir por `%.2f` en `TradeJournalLogger.kt:258`. **Bug confirmado.**
Existe `formatDynamicPrice()` (`MarketTick.kt:159-166`). Fix mínimo: 1 línea en `buildLine`, `%.2f` → `%s` + `formatDynamicPrice(priceY)`.

**C) `scripts/ticks/record_ticks.js`.** Read-only vía `run-as`; `data/ticks/<ric>_<fecha>.csv` (append, `ts,ric,price`).
12s→25 ticks; 3 min: ETHUSD-OTC 223/177s, Z-CRY_IDX 458/849s. `compare_feeds.py`: rango vela-media ETHUSD-OTC **5.44e-4**
vs Z-CRY/IDX **7.17e-10** (factor ~10⁶).

**D) `scripts/backtest/replay.py`.** Self-test `OK: 18 aserciones`; `--mode price` (veredicto) y `--mode signals` (señales).
**El motor Kotlin SÍ corre fuera del dispositivo**: solo importa `android.util.Log` y `build.gradle.kts:47-49` ya usa
`unitTests.isReturnDefaultValues = true` (`SyntheticCandleEngineTest.kt:406`); no se hizo por la restricción de carpetas.
**Opción más fiel (1 archivo)**: `app/src/test/.../ReplayBacktestTest.kt` que lea el CSV por variable de entorno, llame
`SyntheticCandleEngine.onNewTick` y escriba el journal simulado — única ruta con las 11 estrategias y confluencia real
(`if (isYolo) 75 else 80`, `:911`).

**E) `scripts/backtest/evaluate.py`.** Self-test `OK: 17 aserciones`. Wilson 95%, EV (`--payout` 0.83), drawdown, racha
máxima, split 70/30, `p*=1/(1+b)=54.6448%`. Ramas: N=300@62% → holdout 54.15% → sin edge; N=900@72% → 66.98% → ventaja.
Journal del dispositivo: N=7 sin TIE, WR 28.57%, holdout N=2 → sin edge, muestra insuficiente.

**Verificar agy:** 1) TIE con logcat vivo + `accessibilityActive` al liquidar (A). 2) Firmar `%.2f`
(`TradeJournalLogger.kt:258`) (B). 3) Correr `ReplayBacktestTest.kt` si se autoriza y contrastarlo con `replay.py` (D). 4) Que N≥200 y `p*=54.6448%` sigan intactos (E).
**Sin edge demostrado.** No se avanza a dinero real.
