# TAREA HERMES — R23 ITERACIÓN 4: ENDPOINT /ticks Y REPRODUCCIÓN REPLAY DETERMINISTA

**Fecha:** 2026-10-15  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p6`)  
**Rama:** `r10-night`  
**SHA Base Iter 4:** `59bdeb6`  

---

## 1. Contexto y Evidencia Previa (Iteración 3)
En la Iteración 3, OpenCode auditó la ventana de 20 minutos con veredicto **KEEP**:
- Balance 100% inmutable (`38.054.735,36 COP`), 0 taps, salud `READY`.
- `decision_id` determinista verificado al 100%: 37/37 UUID v3 válidos y únicos, 0 colisiones intra-segundo.
- Control negativo validado: 37/37 vetos V2 legítimos (`volatilidadSuficiente=false`).
- **Hallazgo Pendiente:** La paridad Live ↔ Replay siguió en "NO EVALUABLE" (0 pares) porque no existe una fuente de ticks de la sesión viva para que el motor de replay offline reproduzca exactamente los mismos datos y genere el journal pareado con los mismos `decision_id`.

---

## 2. Hipótesis de la Iteración 4
"Dotar a `TradeDrawHttpBridge` del endpoint `/ticks` (buffer circular de los últimos N ticks recibidos por el feed con `ts`, `ric`, `price`) permite al pipeline de validación extraer los ticks idénticos que entraron al dispositivo durante la ventana en vivo, ejecutar el replay offline determinista (`ReplayBacktestTest`) y obtener por primera vez una tasa de paridad cuantitativa Live ↔ Replay medible y objetiva."

---

## 3. Especificación Técnica de Implementación

### 3.1. `app/build.gradle.kts`
- Incrementar `versionCode`: `8` → `9`
- Actualizar `versionName`: `"1.4-r23-iter3"` → `"1.4-r23-iter4"`
- Actualizar `buildConfigField`:
  - `BUILD_ID`: `"R23-ITER4"`
  - Mantener `GIT_SHA` y `BUILD_TIMESTAMP` dinámicos.

### 3.2. Buffer de Ticks en Memoria y Endpoint `/ticks` en `TradeDrawHttpBridge.kt`
1. **Buffer de Ticks**:
   - En memoria, circular, thread-safe, capacidad fija de hasta 2000 ticks.
   - Alimentado en cada tick válido recibido por el WebSocket (`onMarketTick` / `onNewMarketTick`).
   - Cada entrada almacena: `ts` (Long), `ric` (String normalizado), `price` (Double).
2. **Endpoint `GET /ticks`**:
   - Parámetro opcional: `max` (default `500`, acotado a `[1, 2000]`).
   - Parámetro opcional: `format` (`json` por defecto, o `csv`).
   - Si `format=csv`: responde `text/plain` o CSV con cabecera `ts,ric,price\n` y las líneas correspondientes.
   - Si `format=json`: responde JSON:
     `{"success":true,"total":N,"ticks":[{"ts":1792095485676,"ric":"Z-CRY/IDX","price":641.867302},...]}`
   - Solo lectura: no altera estado, no toca guards ni trading.

### 3.3. Restricciones Absolutas
- **PROHIBIDO** modificar `RANGO_RELATIVO_MINIMO = 1e-5`.
- **PROHIBIDO** alterar las reglas de confluencia, guards o condiciones de veto.
- **PROHIBIDO** modificar la protección fail-closed de SHADOW MODE (0 taps, balance intacto).

---

## 4. Criterios de Éxito y Verificación
1. Compilación exitosa: `./gradlew.bat testDebugUnitTest --console=plain` (379+ tests PASS, 0 fallos).
2. Nuevos tests en `TicksEndpointTest.kt` validando el endpoint `/ticks` y su formato CSV/JSON.
3. Reporte en `workflows/rounds/r23_iter4_hermes.md`.
4. Notificar a AGY en Herdr (`w1:p1`): `herdr agent prompt w1:p1 "Hermes completó la implementación de R23 Iteración 4..."`
