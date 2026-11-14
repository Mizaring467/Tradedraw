# R23 AUTÓNOMO — ITERACIÓN 1: ESPECIFICACIÓN DE IMPLEMENTACIÓN PARA HERMES

**Para:** HERMES (`w1:p6`)  
**De:** AGY (`w1:p1`) — Orquestador  
**Fecha:** 2026-10-15  
**Rama:** `r10-night`  
**SHA base:** `83b8f95`  
**Objetivo:** Habilitar el pipeline operativo autónomo en CUENTA DEMO mediante clasificación de umbral de volatilidad por tipo de activo (Punto 5 del análisis) y blindaje estricto de seguridad `isDemoAccount == true`.

---

## 1. Contexto y Evidencia que Motiva el Cambio

1. **Evidencia cuantitativa de Iteraciones 1 a 4:**
   - 100% de las decisiones generadas (140 en Iter 1, 17 en Iter 2, 26 en Iter 3, 61 en Iter 4) fueron vetadas por `V2_RANGE`.
   - En `Z-CRY/IDX` (Crypto IDX), el precio oscila en unidades sub-micro (`~1e-6` sobre precio `~641.86`), resultando en un `rangoRelativo` medido en 16 ticks de `1.4e-10` a `6.6e-10`.
   - `TradingEngine.RANGO_RELATIVO_MINIMO` estaba hardcodeado universalmente a `1e-5` (`0.001%`), valor calibrado originalmente para `ETHUSD-OTC` (`4.164e-2`), lo que hace **matemáticamente imposible** que `Crypto IDX` opere jamás en la vida.
   - En contraste, `BinomoWebSocketClient.kt:400` YA contempla esta distinción de activo para `isPriceFrozen`:
     `val threshold = if (activeAsset.contains("IDX", ignoreCase = true)) 1e-11 else frozenRangeRatio`
   - Por tanto, la solución técnica correcta (no bajar guards a ciegas, sino clasificar por familia de activo) consiste en aplicar para activos sintéticos (`IDX`) el umbral `1e-11`, conservando `1e-5` para todos los demás activos.

2. **Seguridad Absoluta (Cuenta DEMO Exclusiva):**
   - El modo `AUTONOMOUS` debe contar con un guard de seguridad fail-closed inviolable: si `isDemoAccount == false`, se aborta el despacho, se veta con `SEGURIDAD_NO_DEMO`, y se apaga el motor a `DISABLED`.
   - `AutoTradeAccessibilityService.performClick` debe abortar de inmediato si `isDemoAccount == false`.
   - `/status` debe exponer `"isDemoAccount": Boolean`.

3. **Corrección de Esquema en Replay (Hallazgo Iter 4):**
   - En `ReplayBacktestTest.kt`, la función `writeJournalCsv` debe incluir la columna `asset` en la cabecera y en cada fila (usando `t.assetRic`). Esto resuelve la divergencia espuria detectada por OpenCode en Iter 4.

---

## 2. Modificaciones Exactas Requeridas

### A. `app/build.gradle.kts`
- Incrementar `versionCode`: `9` -> **`10`**.
- Actualizar `versionName`: `"1.4-r23-iter4"` -> **`"1.4-r23-auto-iter1"`**.
- Actualizar `buildConfigField("String", "BUILD_ID", ...)`: `"R23-ITER4"` -> **`"R23-AUTO-ITER1"`**.

### B. `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
1. En el `companion object`:
   - Añadir constante y función clasificadora:
     ```kotlin
     const val RANGO_RELATIVO_MINIMO_IDX = 1e-11

     fun rangoRelativoMinimoParaActivo(asset: String): Double {
         return if (asset.contains("IDX", ignoreCase = true)) RANGO_RELATIVO_MINIMO_IDX else RANGO_RELATIVO_MINIMO
     }
     ```
2. En la evaluación de `V2_RANGE` (`onMarketTick` / línea ~2067):
   - Reemplazar el uso estático de `RANGO_RELATIVO_MINIMO` por:
     ```kotlin
     val umbralMinimo = rangoRelativoMinimoParaActivo(currentObservedAsset)
     if (rangoRelativo >= 0.0 && rangoRelativo < umbralMinimo) {
         Log.w(
             "TradingEngine",
             "⚠️ Headless Trade $action VETADO (V2) por feed sin volatilidad medible: " +
                 "rangoRelativo=$rangoRelativo < $umbralMinimo (activo=$currentObservedAsset)"
         )
         TradeEventLog.add(
             TradeEventLog.Kind.VETO,
             "V2 rango=%.3e umbral=%.0e activo=%s".format(
                 java.util.Locale.US, rangoRelativo, umbralMinimo, currentObservedAsset
             ),
             decisionId = currentDecisionId
         )
         return
     }
     ```
3. En `executeHeadlessTrade`:
   - Antes de despachar la orden en `mode == AutoTradeMode.AUTONOMOUS`, verificar:
     ```kotlin
     if (mode == AutoTradeMode.AUTONOMOUS) {
         val isDemo = AutoTradeAccessibilityService.isDemoAccount && 
                      AutoTradeAccessibilityService.latestObservedBalance >= 500_000.0
         if (!isDemo) {
             Log.e("TradingEngine", "⛔ BLOQUEO CRÍTICO DE SEGURIDAD: Cuenta NO DEMO detectada. Abortando trade autónomo.")
             TradeEventLog.add(
                 TradeEventLog.Kind.VETO,
                 "SEGURIDAD_NO_DEMO cuenta_real_bloqueada balance=${AutoTradeAccessibilityService.latestObservedBalance}",
                 decisionId = currentDecisionId
             )
             setTradingMode(AutoTradeMode.DISABLED, "seguridad:no-demo")
             return
         }
     }
     ```

### C. `app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt`
- En `performClick` (antes de llamar a `hasRootAccess()` o `performClickAt`):
  ```kotlin
  if (!isDemoAccount) {
      Log.e("TradeDraw", "⛔ BLOQUEO DE SEGURIDAD: intento de click fuera de cuenta DEMO.")
      TradeEventLog.add(TradeEventLog.Kind.VETO, "SEGURIDAD_CLICK_NO_DEMO")
      onResult?.invoke(false, "BLOQUEO_SEGURIDAD_NO_DEMO")
      return
  }
  ```

### D. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
- En el bloque JSON devuelto por `/status`:
  - Exponer `"isDemoAccount": ${AutoTradeAccessibilityService.isDemoAccount},`
  - En la sección `feed`:
    - `"rangoRelativoMinimo": ${TradingEngine.rangoRelativoMinimoParaActivo(ws?.activeAsset ?: "")},`
    - `"volatilidadSuficiente": ${if (ws != null) ws.recentPriceRangeRatio() >= TradingEngine.rangoRelativoMinimoParaActivo(ws.activeAsset) else false},`

### E. `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`
- En `writeJournalCsv`:
  - En la cabecera, añadir la columna `asset,` (por ejemplo después de `timestamp,` o antes de `strategy,`).
  - En el bucle de filas, emitir el valor `t.assetRic,` para dicha columna.

### F. Unit Tests Nuevos
- Crear `app/src/test/java/com/example/tradedraw/AutonomousSafetyAndAssetScaleTest.kt`:
  1. Test de umbral según activo:
     - `rangoRelativoMinimoParaActivo("Crypto IDX") == 1e-11`
     - `rangoRelativoMinimoParaActivo("Z-CRY/IDX") == 1e-11`
     - `rangoRelativoMinimoParaActivo("ETHUSD-OTC") == 1e-5`
     - `rangoRelativoMinimoParaActivo("BTCUSD-OTC") == 1e-5`
  2. Test de microvolatilidad en `Z-CRY/IDX`:
     - Rango de `2.5e-10` supera `1e-11` -> no vetado.
     - Rango de `0.0` o `5e-12` no supera `1e-11` -> vetado por V2.
  3. Test de seguridad `isDemoAccount`:
     - Cuando `isDemoAccount == false`, `performClick` rechaza la ejecución y no despacha taps.
     - Cuando `isDemoAccount == true`, permite el flujo normal.

---

## 3. Verificación Requerida

1. Ejecutar `./gradlew.bat testDebugUnitTest --console=plain`
   - Verificar que pasen todos los unit tests (389 + nuevos tests = ~395+ tests, 0 fallos).
2. Documentar en `workflows/rounds/r23_autonomous_iter1_hermes.md`.
3. Notificar a AGY en `w1:p1` al completar.
