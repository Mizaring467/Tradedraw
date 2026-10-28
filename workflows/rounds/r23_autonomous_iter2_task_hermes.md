# TAREA PARA HERMES: R23A ITERACIÓN 2 — ACOPLAMIENTO DE ESCALA MONETARIA (STAKE COP) Y PARIDAD DETERMINISTA

## 1. Contexto y Veredicto Previo
- **R23A Iteración 1:** Veredicto formal **KEEP**.
- **Logro Histórico:** El pipeline autónomo operó de principio a fin en cuenta **DEMO** (`isDemoAccount == true` en 117/117 muestras, cero cuentas reales, fail-closed verificado).
- **Métricas:** 28 señales, 4 taps, 2 trades confirmados (W=1, L=1, payout 82%), saldo DEMO varió legítimamente de `38.054.735,36` a `38.036.736,00 COP` ($\Delta -17.999,36$ COP). Trailing Profit Lock bloqueó nuevas órdenes asegurando el pico de ganancia.
- **Paridad de Activo:** 100% resuelta (`live.asset == replay.asset == Z-CRY/IDX`).

---

## 2. Hallazgos Auditados por OpenCode a Resolver

### A. HALLAZGO CRÍTICO (Modelado de Capital): Desacoplamiento de Stake
- `/status.risk` reportó `investmentAmount = 1.0` (y martingala `[M0 | $1.0]`), pero la UI de Binomo tenía configurado en pantalla `observedOrderAmount = 100.000,0` COP.
- Como el bot pulsa directamente los botones BUY/SELL de Binomo sin alterar el campo de texto de inversión, el broker debitó y liquidó con base en **100.000 COP por operación** (pérdida de -100.000 COP, ganancia de +82.000 COP).
- En cuenta DEMO no hubo riesgo financiero, pero el modelado de riesgo y los registros de journal quedaron desacoplados de la física del trade.

### B. HALLAZGO DE TOOLING (Ya resuelto por AGY):
- El crash por `UnicodeEncodeError` (`✓`), la asignación de `market_regime` (`TRENDING` vs `UPTREND`) y la extracción de `action` en `trades.csv` ya fueron corregidos por AGY en `scripts/validation/live_monitor_r23_autonomous.py` (commit `6149d48`).

---

## 3. Especificación Técnica para HERMES (Iteración 2)

### Requerimiento 1: Sincronización de Stake con `observedOrderAmount` en `RiskManager` y `TradingEngine`
1. En `RiskManager.kt`:
   - Permitir que cuando `AutoTradeAccessibilityService.observedOrderAmount > 0.0`, el stake efectivo utilizado para validaciones de riesgo, dimensionamiento y journal pueda reflejar o sincronizarse con la inversión real del broker (`observedOrderAmount`, ej. 100.000 COP).
   - En `/status.risk` (vía `TradeDrawHttpBridge.kt`), exponer explícitamente:
     * `"observedStake": observedOrderAmount` (el valor leído en pantalla de Binomo).
     * `"effectiveStake": ...` (el monto real que se asume para la orden).
2. En `TradeJournalLogger.kt` / `TradingEngine.kt`:
   - Cuando se registre un trade (o se envíe a `recordTradeSent`), registrar el stake real operado (`observedOrderAmount` si es > 0, fallback a `getCurrentInvestmentAmount()`).

### Requerimiento 2: Paridad y Sincronización de Reloj en Replay
1. En `ReplayBacktestTest.kt`:
   - Asegurar que `market_regime` reporte `TRENDING` o `SIDEWAYS` de forma coherente con `syntheticCandleEngine.isChoppinessDetected()`.
   - Asegurar que `candle_second` emitido en el journal de replay use el segundo exacto de la señal.

### Requerimiento 3: Bump de Versión y Tests Unitarios
1. `app/build.gradle.kts`:
   - `versionCode 10 -> 11`
   - `versionName = "1.4-r23-auto-iter2"`
   - `BUILD_ID = "R23-AUTO-ITER2"`
2. Tests unitarios:
   - Crear o ampliar tests en `AutonomousSafetyAndAssetScaleTest.kt` o `RiskManagerTest.kt` verificando la coherencia entre `observedOrderAmount` y el stake efectivo del motor.
   - Ejecutar `./gradlew.bat testDebugUnitTest` y asegurar 0 fallos.

---

## 4. Restricciones de Implementación
- **Fail-Closed DEMO Intacto:** NUNCA relajar el guard `puedeOperarAutonomoEnDemo` ni las tres puertas de accesibilidad (`performClick`, `performClickAt`, `executeRootTap`).
- **Minimalismo Ponytail:** Resolver con el menor diff posible sin sobreingeniería.
- **Reporte:** Documentar los cambios y tests en `workflows/rounds/r23_autonomous_iter2_hermes.md`.
