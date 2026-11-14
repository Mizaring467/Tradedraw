# TAREA PARA HERMES: R23A ITERACIÓN 3 — CORRECCIÓN DE REGRESIÓN JSON EN /STATUS (RFC 8259)

## 1. Contexto y Veredicto Previo
- **R23A Iteración 2:** Veredicto formal **KEEP**.
- **Seguridad Incondicional:** 95/95 muestras en cuenta DEMO (`isDemoAccount == true`), cero cuentas reales, fail-closed activo.
- **Fixes Previos Verificados:**
  * Unicode en Windows resuelto (comando literal ejecutó 20 min continuos sin crasheos).
  * `market_regime` resuelto (79/79 decisiones con régimen real `TRENDING`, desalineación con `trend` corregida).
  * `candle_second` sincronizado (1=1).
  * Acoplamiento de stake COP verificado (`observedStake = 100000.0` y `effectiveStake = 100000.0`).
  * Saldo preservado: `38.164.736,00 COP` (+110k COP ganados en la ronda, guards activos protegiendo el pico).

---

## 2. Hallazgo Crítico a Resolver (Regresión Binaria)

### REGRESIÓN DE FORMATO JSON EN `/status`
En `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` (líneas 607 a 611):
Los comentarios explicativos de la Iteración 2 fueron introducidos DENTRO del template string multilínea (`""" ... """`) que construye la respuesta JSON de `/status`:
```kotlin
                // R23A Iter 2: desacoplamiento de escala monetaria detectado en la iter 1.
                // `observedStake` es lo que dice la pantalla de Binomo; `effectiveStake` es lo que el
                // broker realmente debita y con lo que se registra el journal...
                "observedStake": ${rm.observedStake},
                "effectiveStake": ${rm.getEffectiveStake()},
```
Al estar dentro de un string multilínea, los comentarios `//` se emiten literalmente sobre el flujo HTTP.
Conforme a la especificación estándar **RFC 8259**, JSON prohíbe comentarios. Los parsers estándar (`json.loads` en Python, `ConvertFrom-Json` en PowerShell, `jq`, etc.) fallan con:
```text
Expecting property name enclosed in double quotes: line 36 column 17
```
Esto es una regresión del fix histórico `a212673`.

---

## 3. Especificación Técnica para HERMES (Iteración 3)

### Requerimiento 1: Erradicar comentarios dentro del template JSON de `TradeDrawHttpBridge.kt`
1. En `TradeDrawHttpBridge.kt`:
   - Mover los comentarios explicativos FUERA del string literal multilínea `"""`, colocándolos como comentarios normales de Kotlin antes de la construcción del string, o eliminarlos del bloque de texto.
   - Asegurar que no quede ningún `//` ni `/* */` dentro de ninguna cadena JSON emitida por el puente HTTP (`/status`, `/ticks`, `/nodes`, etc.).

### Requerimiento 2: Test Unitario de Validación Estricta de JSON
1. Crear un test unitario en `app/src/test/java/com/example/tradedraw/StatusJsonStrictSyntaxTest.kt` (o en `ShadowJournalEndpointTest.kt` / `TicksEndpointTest.kt`):
   - Invocar `buildStatusJson(...)` (o mock equivalente del endpoint) y verificar que `org.json.JSONObject(statusJson)` o `com.google.gson.JsonParser.parseString(statusJson)` parsee exitosamente sin excepciones de sintaxis.
   - Validar que contenga las claves `"observedStake"` y `"effectiveStake"` como números válidos.

### Requerimiento 3: Bump de Versión y Verificación de Tests
1. `app/build.gradle.kts`:
   - `versionCode 11 -> 12`
   - `versionName = "1.4-r23-auto-iter3"`
   - `BUILD_ID = "R23-AUTO-ITER3"`
2. Ejecutar `./gradlew.bat testDebugUnitTest` y verificar 0 fallos (409+ tests PASS).

---

## 4. Restricciones de Implementación
- **No relajar guards:** Mantener intacto el guard `isDemoAccount` fail-closed y las 3 puertas de tap.
- **Reporte:** Documentar los cambios en `workflows/rounds/r23_autonomous_iter3_hermes.md`.
