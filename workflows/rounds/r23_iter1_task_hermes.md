# R23 — TAREA DE IMPLEMENTACIÓN (ITERACIÓN 1) PARA HERMES

**Asignado por:** AGY (Orquestador R23)  
**Implementador:** HERMES (`w1:p6`)  
**Fecha:** 2026-10-15  
**Objetivo de la Iteración:** Trazabilidad de Build en Runtime + Exposición Cuantitativa de `V2_RANGE`  

---

## 1. Contexto y Evidencia
En cumplimiento de la Sección 5 y Sección 7 de la directriz R23:
1. **Sección 5 (Trazabilidad estricta):** OpenCode no puede iniciar la validación live de ~20 minutos hasta poder certificar inequívocamente que `código candidato = APK instalada = proceso actualmente ejecutándose`. Para ello, la APK debe exponer en `/status`:
   - `gitSha`
   - `buildId`
   - `buildTimestamp`
   - `versionCode` (bump a 6)
   - `versionName` (`"1.4-r23-iter1"`)
2. **Sección 7 (Auditoría V2_RANGE):** En R22, 18/18 decisiones fueron vetadas por `V2_RANGE`. Para que OpenCode pueda medir continuamente la distancia del activo al umbral `1e-05` sin alterar ningún guard ni relajar umbrales a ciegas, `/status` debe exponer:
   - `rangoRelativoMinimo`
   - `rangoRelativoActual`

---

## 2. Archivos Reservados para HERMES en esta Tarea
- `app/build.gradle.kts`
- `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
- (Opcional) Tests unitarios en `app/src/test/` si se añade verificación del JSON de status.

*Nota:* AGY tiene reservados `workflows/R23_AUTOMEJORA_COORDINATION.md` y la gestión de ADB/build.

---

## 3. Especificación Técnica de los Cambios

### A. `app/build.gradle.kts`
1. Incrementar `versionCode` de `5` a `6`.
2. Actualizar `versionName` de `"1.4-r22"` a `"1.4-r23-iter1"`.
3. Inyectar `buildConfigField` en `defaultConfig`:
   ```kotlin
   val gitSha = try {
       val stdout = java.io.ByteArrayOutputStream()
       project.exec {
           commandLine("git", "rev-parse", "--short", "HEAD")
           standardOutput = stdout
       }
       stdout.toString().trim()
   } catch (e: Exception) {
       "e3253c0"
   }

   buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
   buildConfigField("String", "BUILD_ID", "\"R23-ITER1\"")
   buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
   ```

### B. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
1. En `buildStatusJson()`, agregar en la raíz del JSON retornado:
   ```kotlin
   "gitSha": "${BuildConfig.GIT_SHA}",
   "buildId": "${BuildConfig.BUILD_ID}",
   "buildTimestamp": ${BuildConfig.BUILD_TIMESTAMP},
   ```
2. En el bloque `"risk"` o `"feed"` de `buildStatusJson()`, incluir la telemetría del rango relativo:
   ```kotlin
   "rangoRelativoMinimo": ${TradingEngine.RANGO_RELATIVO_MINIMO},
   "rangoRelativoActual": ${ws?.recentPriceRangeRatio() ?: -1.0},
   ```
3. Asegurar que el JSON resultante sea estrictamente válido (sin comentarios dentro de las comillas triples de JSON).

---

## 4. Restricciones Absolutas
- **PROHIBIDO** modificar la lógica de trading, órdenes, risk management o guards.
- **PROHIBIDO** relajar `RANGO_RELATIVO_MINIMO` o inventar señales.
- Mantener compatibilidad total con los 345 tests existentes.

---

## 5. Entregables Esperados de HERMES
1. Diff/resumen de cambios aplicados en los archivos.
2. Ejecución local de tests (`./gradlew.bat testDebugUnitTest` o equivalente).
3. Notificación a AGY con SHA/estado listo para que AGY compile, instale por ADB e inicie a OpenCode.
