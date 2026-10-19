# TAREA HERMES — R23 ITERACIÓN 3: DETERMINISMO EN decision_id Y PARIDAD REPLAY

**Fecha:** 2026-10-15  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p6`)  
**Rama:** `r10-night`  
**SHA Base Iter 3:** `fd74445`  

---

## 1. Contexto y Evidencia Previa (Iteración 2)
En la Iteración 2, OpenCode auditó la ventana de 20 minutos con veredicto **KEEP**:
- Balance 100% inmutable (`38.054.735,36 COP`), 0 taps, salud `READY`.
- Control negativo validado: `volatilidadSuficiente=false` correlacionó con el 100% de los vetos V2 (9/9).
- Endpoint `/shadow_journal` verificado operativo en disco.
- **Hallazgo Bloqueante de Tooling:** La paridad Live ↔ Replay resultó "NO EVALUABLE" (0 pares) porque `decisionId` en vivo se genera como `UUID.randomUUID()` aleatorio mientras que el motor de replay carece de `decision_id` o genera claves sintéticas incomparables.

---

## 2. Hipótesis de la Iteración 3
"Generar `decision_id` de forma determinista y reproducible (`UUID.nameUUIDFromBytes("$asset-$epochSecond-$action".toByteArray()).toString()`) tanto en el motor en vivo (`TradingEngine.kt`) como en el motor de replay (`ReplayBacktestTest.kt`), junto con el bump a `versionCode=8` (`1.4-r23-iter3`, `BUILD_ID="R23-ITER3"`), garantiza que una misma señal generada bajo los mismos datos en vivo y en offline comparta idéntico `decision_id`, haciendo que el comparador Live ↔ Replay pueda emparejar decisiones y medir la paridad de forma objetiva y determinista."

---

## 3. Especificación Técnica de Implementación

### 3.1. `app/build.gradle.kts`
- Incrementar `versionCode`: `7` → `8`
- Actualizar `versionName`: `"1.4-r23-iter2"` → `"1.4-r23-iter3"`
- Actualizar `buildConfigField`:
  - `BUILD_ID`: `"R23-ITER3"`
  - Mantener `GIT_SHA` y `BUILD_TIMESTAMP` dinámicos.

### 3.2. `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
- En el callback `syntheticCandleEngine.onSignalGenerated = { action, reason ->` (línea ~224):
  Reemplazar la generación aleatoria de `decisionId` por una función determinista:
  ```kotlin
  val nowSec = System.currentTimeMillis() / 1000L
  val assetName = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
  val decisionId = generateDeterministicDecisionId(assetName, nowSec, action.name)
  ```
- Implementar la función de utilidad (o método de clase/companion):
  ```kotlin
  fun generateDeterministicDecisionId(asset: String, epochSecond: Long, action: String): String {
      val key = "${asset.trim()}_${epochSecond}_${action.trim().uppercase()}"
      return java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
  }
  ```

### 3.3. `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`
- Actualizar `writeJournalCsv`:
  - Incluir `decision_id` en la cabecera CSV:
    `timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason,decision_id,source\n`
  - En la escritura de cada fila, calcular el `decision_id` determinista usando la misma lógica:
    `val decId = TradingEngine.generateDeterministicDecisionId(raw.ric, entryTick.ts / 1000L, action.name)`
    e insertarlo en la columna correspondiente.

### 3.4. Restricciones Absolutas
- **PROHIBIDO** modificar `RANGO_RELATIVO_MINIMO = 1e-5`.
- **PROHIBIDO** relajar guards, filtros de confluencia, o condiciones de veto.
- **PROHIBIDO** alterar la seguridad fail-closed de SHADOW MODE (0 taps, balance inmutable).

---

## 4. Criterios de Éxito y Verificación
1. Compilación exitosa: `./gradlew.bat testDebugUnitTest --console=plain` (371+ tests PASS, 0 fallos).
2. Reporte en `workflows/rounds/r23_iter3_hermes.md`.
3. Notificar a AGY en Herdr (`w1:p1`): `herdr agent prompt w1:p1 "Hermes completó la implementación de R23 Iteración 3..."`
