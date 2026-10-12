# Ronda 9 · Correcciones de medición + backtest fiel — Reporte del orquestador (opencode)

**Firma de cada PASS:** codex (implementación) → **agy (auditoría, Pasos 4 y 7)** → **hermes (dispositivo, Paso 5)**. Ningún autor firmó su propio PASS.

## Qué se hizo

**Paso 0 · Higiene git (opencode).** `r8-backtest` vs `main` = solo `scripts/`, `data/`, `workflows/rounds/` (0 archivos de app). Integrado a `main` con squash → commit `0e95d18` (push `3d3059e..0e95d18`).

**Paso 1 · FIX-A price_y (codex).** `TradeJournalLogger.buildLine` usa `%s` + `formatDynamicPrice(priceY.toDouble())`; los otros `%.2f` intactos. Commit `8ea9690`.

**Paso 2 · FIX-B TIE_INCIERTO (codex).** `TradingEngine`: `tieJournalResult` distingue TIE (saldo fresco <3000 ms) de `TIE_INCIERTO`; `recordTieVoidIfFresh` NO contabiliza cuando es incierto. `AutoTradeAccessibilityService.lastBalanceReadMs` se actualiza solo al leer del servicio real.

**Paso 3 · Backtest fiel (codex → agy).** `ReplayBacktestTest.kt` (JVM, lee CSV por `$BACKTEST_CSV`, 11 estrategias vía AUTO_ADAPTIVE, confluencia real ≥80, 1 orden/vela, expiración 60s). Refinado por agy (guard por vela): 10 → 5 señales sin duplicados en la muestra corta.

**Paso 4 · Auditoría (agy).** PASS en A/B/C/D/E. 168 tests, 0 fallos. Grep de constantes: **0 cambios en umbrales/señales/indicadores**; lista de archivos de estrategia tocados = VACÍA.

**Paso 5 · Dispositivo (hermes).** FIX-A ✅ en runtime: journal guarda `price_y` = `1048.994995`, `1049.542969` (ya no trunca). FIX-B ⚠ **no ejercitable**: 0 TIE, 0 TIE_INCIERTO (accesibilidad siempre activa). H9/H10 alineados (entradas segundo=1,3). **Payout real ETHUSD-OTC = 80% → breakeven 0.5556.**

**Paso 6 · Congelado (opencode).** Tag **`r9-measure`** en `8ea9690` (push `main --tags`). En paralelo, grabación de ticks 240 min: ETHUSD-OTC 5533, SOLUSD-OTC 5223, BTCUSD-OTC 7758.

**Paso 7 · Primera medición (opencode ejecuta, agy interpreta).** Backtest sobre 5533 ticks de ETH (19 trades, 0 TIE) + `evaluate.py --payout 0.80`:

| Conjunto | N | W/L | WR | Wilson 95% | EV | MaxDD | Racha máx |
|---|---|---|---|---|---|---|---|
| Desarrollo (70%) | 13 | 5/8 | 38.46% | [17.71%, 64.48%] | −0.31 | 4.80 | 3 |
| **Holdout (30%)** | **6** | 3/3 | **50.00%** | **[18.76%, 81.24%]** | −0.10 | 2.20 | 2 |

## Criterio de éxito (fijado antes de medir)
Holdout **N≥200** con **Wilson LCL > 1/(1+payout) = 55.56%**. → **NO CUMPLIDO.**

## Veredicto (firmado por agy)
**SIN EDGE DEMOSTRADO.** Holdout N=6 ≪ 200; Wilson LCL 18.76% está 36.8 pts por debajo del breakeven; EV negativo no rechaza la hipótesis nula. `git log r9-measure..HEAD` vacío → sin contaminación del holdout. **No se avanza a dinero real ni a más ajustes de estrategia sobre este holdout.**

## Qué NO se pudo determinar
- FIX-B sin ejercitar en positivo (0 TIE en la ventana; requiere un caso real de desconexión de accesibilidad para validarlo).
- Muestra insuficiente para edge: se requieren **~667 trades totales (~194.000 ticks ≈ 71 h de captura pasiva a ~2.640 ticks/h)** para N≥200 en holdout.

## Instrucción de continuación
Recolectar ticks de ETHUSD-OTC (y SOL/BTC OTC) en sesiones largas **sin tocar código de estrategia** hasta alcanzar N≥200 de holdout. Desde el tag `r9-measure` no se modifica estrategia, umbrales ni señales.
