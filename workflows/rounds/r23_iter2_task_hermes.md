# TAREA HERMES — R23 ITERACIÓN 2: ENDPOINT /shadow_journal Y VOLATILIDAD_SUFICIENTE EN /status

**Fecha:** 2026-10-15  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p6`)  
**Rama:** `r10-night`  
**SHA Base Iter 2:** `2ef2b72`  

---

## 1. Contexto y Evidencia Previa (Iteración 1)
En la Iteración 1 se validó en vivo (KEEP) la trazabilidad de build y el reporte de rango en `/status`.
El validador independiente (OpenCode) auditó 116 muestras durante 20 minutos con 0 taps y balance inmutable.
Sin embargo, OpenCode identificó que las decisiones en SHADOW MODE (`shadow_journal.csv`) están confinadas al almacenamiento del dispositivo y no pueden ser leídas directamente por HTTP para auditoría sin extraerlas por ADB, además de que el validador requiere verificar de inmediato si un activo cumple la condición de volatilidad sin calcular ratios en cliente.

---

## 2. Hipótesis de la Iteración 2
"Dotar a `TradeDrawHttpBridge` del endpoint `/shadow_journal` (lectura directa desde disco del `shadow_journal.csv` con tail configurable `?max=50`) y exponer en `/status.feed` el booleano `volatilidadSuficiente` (`rangoRelativoActual >= rangoRelativoMinimo`), junto con el bump a `versionCode=7` (`1.4-r23-iter2`), proporciona al validador y al pipeline de pruebas acceso directo y verificable en tiempo real a las decisiones persistidas en disco, sin alterar ningún umbral de trading ni relajar las defensas del sistema."

---

## 3. Especificación Técnica de Implementación

### 3.1. `app/build.gradle.kts`
- Incrementar `versionCode`: `6` → `7`
- Actualizar `versionName`: `"1.4-r23-iter1"` → `"1.4-r23-iter2"`
- Actualizar `buildConfigField`:
  - `BUILD_ID`: `"R23-ITER2"`
  - Mantener la extracción dinámica de `GIT_SHA` y `BUILD_TIMESTAMP` introducida en la Iteración 1.

### 3.2. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
1. **Endpoint `/shadow_journal`**:
   - En el bloque `when (path)` de peticiones HTTP, agregar la ruta `"/shadow_journal"`.
   - Parámetro opcional: `max` (default `50`, parseado con `toIntOrNull() ?: 50`).
   - Leer el archivo `shadow_journal.csv` desde el directorio de la aplicación (usando `overlayService.getExternalFilesDir(null)` o almacenamiento interno de respaldo, tal como lo hace `TradeJournalLogger`).
   - Si el archivo no existe o está vacío, responder 200 con `{"success":true,"total":0,"lines":[]}`.
   - Si existe, tomar las últimas `max` líneas (excluyendo o incluyendo cabecera según corresponda) y responder JSON:
     `{"success":true,"total":N,"lines":["line1","line2",...]}` asegurando escapar comillas o caracteres especiales de cada línea.
2. **Campo `volatilidadSuficiente` en `/status`**:
   - En el método `buildStatusJson()`, dentro del objeto `"feed"`, agregar:
     ```json
     "volatilidadSuficiente": ${if (ws != null) ws.recentPriceRangeRatio() >= TradingEngine.RANGO_RELATIVO_MINIMO else false},
     ```

### 3.3. Restricciones Absolutas
- **PROHIBIDO** modificar el valor numérico de `TradingEngine.RANGO_RELATIVO_MINIMO` (`1e-5`).
- **PROHIBIDO** alterar las condiciones de veto V1, V2, o desajuste de activo.
- **PROHIBIDO** modificar el despacho de taps o la protección fail-closed de SHADOW MODE.

---

## 4. Criterios de Éxito y Verificación
1. Compilación exitosa: `./gradlew.bat testDebugUnitTest --console=plain` (345+ tests PASS, 0 fallos).
2. Generar reporte técnico en `workflows/rounds/r23_iter2_hermes.md` indicando:
   - Archivos modificados y diff resumido.
   - Resultado de los unit tests.
   - SHA candidate.
   - Comportamiento esperado para la validación de OpenCode.
3. Notificar a AGY vía Herdr: `herdr agent prompt agy "Hermes completó la implementación de R23 Iteración 2..."`
