# REPORTE DE IMPLEMENTACIÓN — R23E FASE 0 Y FASE 3 (HERMES / `w1:p4`)

**Fecha:** 2026-10-16
**Rama:** `r10-night`
**SHA base declarado en la spec:** `98656f9`
**SHA del código probado (commit del paso 3-4):** `08073c3`
**SHA embebido en la APK compilada:** `08073c3` ✔ (coincide exactamente con el commit probado)
**Build:** `versionCode=17`, `versionName=1.7-r23e-shadow`, `BUILD_ID=R23E-SHADOW`
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)

---

## 0. DIAGNÓSTICO EN UNA LÍNEA

Los 3 requerimientos quedaron implementados y verificados (445 tests Kotlin + 21 Python verdes, pipeline contrafactual probado end-to-end con CSV real), la APK v17 se instaló y **certifica la identidad de build correcta en runtime**; el `health.state == READY` del paso 8 **no se pudo alcanzar** por una condición de entorno (Binomo no está instalado en el dispositivo), que detallo en §5 con su diagnóstico.

---

## 1. REQUERIMIENTOS IMPLEMENTADOS

| Archivo | Qué cambió |
|---|---|
| `TradingEngine.kt` (§REQ1) | Nuevos `internal fun` de archivo `extractAdaptiveSimPct()` y `buildAdaptiveBlockDetail()`. El emit site de L2305 ahora publica `ADAPTIVE_BLOCK [sim:NN%] [Confluencia: NNpts] <razon>`. **La lógica de trading del guard no se tocó.** |
| `scripts/validation/live_monitor_r23_autonomous.py` (§REQ2) | Pipeline contrafactual completo: `extract_adaptive_block_fields`, `find_nearest_tick_price`, `dedupe_counterfactual`, `resolve_counterfactual`, `counterfactual_ev`, `counterfactual_id`, `bucket_label`, `registrar_contrafactuales()`; volcado de `counterfactual_adaptive_block.csv` y sección 2B en `LIVE_REPORT.md`. |
| `AdaptiveBlockTelemetryTest.kt` (§REQ3, nuevo) | 5 tests del formato/contrato del `detail`. |
| `tests/test_counterfactual_adaptive_block.py` (§REQ3, nuevo) | 21 tests: dedupe, resolución WIN/LOSS/VOID, EV, precedencia de ticks, esquema CSV, buckets. |

### REQ 1 — diseño del enriquecimiento

`buildAdaptiveBlockDetail(reason, signalReason)` compone `ADAPTIVE_BLOCK` + `[sim:NN%]` (extraído del `reason` del `AdaptiveDecision.Block`) + `[Confluencia: NNpts]` (extraído del `reasonDescription` de la señal) + la razón original.

**Ambos prefijos son opcionales y se OMITEN cuando el dato no existe.** Es deliberado: `AdaptiveLearningEngine` tiene tres ramas de `Block` y solo la de anti-patrón por similitud mide similitud; las de continuación/reversión no. Escribir un `0` por defecto haría que el consumidor contara "similitud medida y nula", envenenando la segmentación por buckets del preregistro §8.3.

### REQ 2 — diseño del pipeline contrafactual

- **Detección:** se recorre `observed_decisions` buscando `kind == VETO` con `ADAPTIVE_BLOCK` en el detail.
- **Deduplicación (ambas reglas de §6, por dirección):** ventana de ocupación de 60 s + máximo 1 por vela de 60 s. Implementadas en `dedupe_counterfactual()`, que recibe DOS diccionarios por dirección.
- **Resolución a 60 s:** `P_exit` del tick más cercano a `decision_ts + 60000` dentro de `accumulated_ticks`; WIN/LOSS/VOID según el criterio exacto del preregistro §5; `theoretical_ev` = `+0.82×100000` / `-100000` / `0`.
- **Payout:** se usa el **real** (`feed.payoutResolved`) y se cae al 0.82 de referencia si no llega. El EV del reporte es el nominal del preregistro.
- **Salida:** `counterfactual_adaptive_block.csv` con las 15 columnas obligatorias, más §2B en el reporte con `N_blocked_evaluated`, `N_cf_deduplicated`, `CF_W/L/VOID`, `CF_WR`, `CF_EV` y la segmentación por buckets de similitud y de score.

---

## 2. DESVIACIONES NUMERADAS

**D1 — El score de confluencia NO llega al evento `ADAPTIVE_BLOCK` en el path headless (limitación real, no evitable desde REQ1).**
Verificado empíricamente (§3.6). `executeHeadlessTrade(action, reasonDescription)` recibe la `reason`, no la `reason` enriquecida: el marcador `[Confluencia: Npts]` lo añade `SyntheticCandleEngine.dispatchSniperSignal` justo antes de invocar `onSignalGenerated(action, enrichedReason)`, y esa razón enriquecida se usa como texto del trade, **no se reasigna a `reasonDescription`**. Consecuencia: en las filas contrafactuales el campo `score` saldrá **vacío** y la columna `score` del CSV será `N/A` en la segmentación, igual que en R23D. Corregirlo exige propagar la razón enriquecida por el pipeline de señales (cambio de instrumentación, no de estrategia) y **no lo hice unilateralmente** por tocar el camino de la señal. La infraestructura de REQ1/REQ2 está lista para consumirlo en cuanto se propague: el regex y el bucket ya existen y están testeados.

**D2 — Tolerancia explícita en la búsqueda del tick de entrada (±4000 ms).**
La spec dice "el tick más cercano a `t_entry + 60s`" para la salida pero no fija tolerancia para la **entrada**. Añadí `COUNTERFACTUAL_ENTRY_TOLERANCE_MS = 4000`: los ticks de Z-CRY/IDX llegan a ~2,4/s (~417 ms), así que ±4 s cubre ~10 ticks y sigue siendo "el precio en el instante de la decisión". Sin tolerancia, un hueco del feed dejaría la fila sin precio de entrada. En empate se prefiere el tick **anterior** (el único que existía en el instante de la decisión: sin look-ahead).

**D3 — Payout real en lugar del 0.82 fijo para valorar el EV de cada fila.**
La spec fija `b = 0.82` como referencia. Usé `feed.payoutResolved` cuando está disponible (es el payout realmente observado en el broker) y caí al 0.82 nominal si no llega. El EV del reporte es el nominal del preregistro, así que la comparación con `WR_BE = 54.95%` sigue siendo válida.

**D4 — Sufijo `COUNTERFACTUAL_WIN/LOSS/VOID` (prefijado) en lugar de `WIN/LOSS/VOID`.**
La spec usa ambas formas; el preregistro §5 escribe `COUNTERFACTUAL_WIN`. Adopté la forma larga para que la columna `result` sea autoexplicativa en el CSV.

**D5 — El bloque contrafactual se añadió DESPUÉS de la escritura de `window_metadata.json`, no antes.**
Aditivo: el JSON de metadata de R23D queda intacto y el CSV es un artefacto nuevo. No altera ningún campo pedido.

---

## 3. VERIFICACIÓN

### 3.1 Tests Kotlin — suite completa (con `--rerun-tasks`)

```
./gradlew.bat testDebugUnitTest --rerun-tasks --console=plain
```
- `BUILD SUCCESSFUL in 1m 2s` — **25 actionable tasks: 25 executed** (sin caché)
- **TESTS=445 · FAILURES=0 · ERRORS=0**
- `AdaptiveBlockTelemetryTest`: `tests="5" skipped="0" failures="0" errors="0"`

**Delta:** 440 previos (R23D Fase 1) → **445** (+5 nuevos). 0 fallos, 0 errores.

### 3.2 Tests Python

```
python -m unittest tests.test_counterfactual_adaptive_block -v
```
**Ran 21 tests — OK (0 fallos).**

### 3.3 Verificación NEGATIVA del test nuevo

Se inyectó el defecto a propósito (`val sim = extractAdaptiveSimPct(reason) ?: 0`, es decir, fabricar un `0` cuando no hay medición):

```
AdaptiveBlockTelemetryTest > detailSinSimilitudNiConfluencia_omiteAmbosPrefijos FAILED
5 tests completed, 1 failed  ·  BUILD FAILED in 16s
```

Árbol restaurado, verificado con `grep` que la marca de inyección desapareció (0 ocurrencias) y suite limpia reconfirmada (445/0/0, §3.1 ya es post-restauración).

### 3.4 Prueba de integración end-to-end del pipeline contrafactual

Se encadenó el **productor** (formato que emite `buildAdaptiveBlockDetail`) con el **consumidor** (funciones del monitor) sobre 300 ticks sintéticos y 7 vetos `ADAPTIVE_BLOCK` con solapamientos deliberados:

```
bloqueados=7  deduplicados=2  registrados=4  resueltos=4
CF_W=2  CF_L=2  CF_VOID=0  CF_WR=50.0%
CF_EV total=-36,000.00 COP
```

CSV real producido (extracto):

```csv
counterfactual_id,decision_id,asset,action,decision_ts,entry_price,expiry_ts,exit_price,result,score,similarity,market_regime,trend,payout,theoretical_ev
CF-dec-00-1792000000000,dec-00,Z-CRY/IDX,BUY,1792000000000,641.8672997,1792000060000,641.8673297,COUNTERFACTUAL_WIN,85.0,98.0,TRENDING,UPTREND,0.82,82000.0
CF-dec-02-1792000062000,dec-02,Z-CRY/IDX,BUY,1792000062000,641.867331,1792000122000,641.867361,COUNTERFACTUAL_WIN,82.0,96.0,TRENDING,UPTREND,0.82,82000.0
CF-dec-03-1792000070000,dec-03,Z-CRY/IDX,SELL,1792000070000,641.867335,1792000130000,641.867365,COUNTERFACTUAL_LOSS,80.0,94.0,TRENDING,UPTREND,0.82,-100000.0
```

Los 2 deduplicados y el `dec-08` sin dirección (`action="NONE"`) quedaron fuera correctamente. Precios a 8 decimales, sin pérdida de precisión al pasar por el CSV.

### 3.5 Verificación de que `sim` extrae el valor real de los datos de R23D

Sobre un `detail` real de las ventanas R23D (`Anti-patrón perdedor recurrente (99% similitud en contexto S/R=16%)`): el extractor devuelve `99`, y el detail enriquecido resultante (`ADAPTIVE_BLOCK [sim:99%] ...`) es leído de vuelta por el regex del consumidor Python como `99`. **La cadena productor→consumidor de la similitud está cerrada con datos reales.** Existen **118 eventos `ADAPTIVE_BLOCK`** en las ventanas R23D, todos con similitud 98-99%.

### 3.6 Evidencia del hallazgo D1 (score ausente)

`buildAdaptiveBlockDetail()` se invoca con `reasonDescription` (la del caller `onSignalGenerated`), y el marcador `[Confluencia: Npts]` **no está presente ahí**: lo añade `dispatchSniperSignal` DESPUÉS. Nada en el código reasigna `reasonDescription` con la razón enriquecida. Por eso el prefijo `[Confluencia:]` se omitirá en la telemetría real hasta que se propague (D1).

### 3.7 Fase 0 — identidad de build embebida en la APK

`BuildConfig.java` generado:
```java
public static final int VERSION_CODE = 17;
public static final String VERSION_NAME = "1.7-r23e-shadow";
public static final String BUILD_ID = "R23E-SHADOW";
public static final String GIT_SHA = "08073c3";
```

Verificado **dentro de los 6 `classes*.dex` del APK** (no por fecha):
```
R23E-SHADOW        -> 2
1.7-r23e-shadow    -> 2
08073c3            -> 2
R23D-TELEMETRY     -> 0   ← la build anterior NO quedó embebida
1.6-r23d-telemetry -> 0
```

### 3.8 Instalación

```
adb install -r app/build/outputs/apk/debug/app-debug.apk  →  Success
```

### 3.9 Verificación en runtime `/status` (paso 8)

| Campo pedido por el paso 8 | Valor observado | ✔/✘ |
|---|---|---|
| `versionCode == 17` | `17` | ✔ |
| `versionName == "1.7-r23e-shadow"` | `1.7-r23e-shadow` | ✔ |
| `buildId == "R23E-SHADOW"` | `R23E-SHADOW` | ✔ |
| `gitSha` coincide con el commit (`08073c3`) | `08073c3` | ✔ |
| `isDemoAccount == true` | `true` | ✔ |
| `health.state == "READY"` | `BLOCKED` | ✘ (ver §5) |

---

## 4. RESTRICCIONES VERIFICADAS

- **La lógica de trading del guard no se modificó.** El diff de `TradingEngine.kt` toca únicamente el literal del `detail` del evento y añade dos funciones nuevas al final del archivo. El umbral `highestSimilarity >= 0.90f`, la compuerta `isTightlyMatchingContext`, `AdaptiveDecision.Block` y el `return` que impide el tap siguen idénticos.
- **`AdaptiveLearningEngine.kt` intacto** (`git status` no lo lista).
- **Cero taps nuevos:** el pipeline contrafactual es de solo lectura; no hay ninguna ruta de escritura al broker ni efecto sobre `RiskManager` o saldo.
- **Guard DEMO fail-closed intacto:** `isDemoAccount = true` verificado en runtime.
- **Curriculum de tests previos intacto:** los 440 anteriores pasan sin tocarlos; los nuevos van en archivos aparte.

---

## 5. PENDIENTE BLOQUEANTE: `health.state` no alcanza READY

**El paso 8 pide `health.state == "READY"` y el dispositivo reporta `BLOCKED`.** Diagnóstico con evidencia:

```
health.reasons = ["SCREEN_FEED_MISMATCH: DESAJUSTE pantalla=DESCONOCIDA feed='Z-CRY/IDX'",
                  "BALANCE_UNREADABLE"]
```

1. **Causa raíz:** Binomo **no está instalado** en el dispositivo. `adb shell pm list packages` solo devuelve `com.example.tradedraw` — ninguna app de Binomo. El feed WebSocket sí funciona (tras lanzar `BinomoAuthActivity` con la sesión persistida, `/status` ya reporta `feed.activeAsset = "Z-CRY/IDX"` y `engine.evaluatedCount` sube), pero la **accesibilidad** no encuentra la UI de Binomo que leer, así que `latestObservedBalance = 0.0` y `latestObservedAsset = ""` → `BALANCE_UNREADABLE` + `SCREEN_FEED_MISMATCH`.
2. **`SCREEN_FEED_MISMATCH` y `BALANCE_UNREADABLE` son condiciones `BLOCKED` del `HealthSupervisor`**, independientes de este cambio. READY es **inalcanzable sin una sesión de Binomo operando en el dispositivo**.
3. Esto es una **condición de entorno, no un defecto del código**: los mismos dos motivos habrían bloqueado la build de R23D. No hay relación con REQ1/REQ2/REQ3.

**Cómo cerrarlo:** con Binomo abierto y sesión activa en el teléfono (el operador humano), `SCREEN_FEED_MISMATCH` y `BALANCE_UNREADABLE` desaparecen y el estado transiciona a READY. Recomiendo que AGY confirme si la app de Binomo debe reinstalarse en el dispositivo antes de autorizar la Fase 4, porque **la captura contrafactual depende del feed de ticks** (que ya funciona) y **no** de la lectura de UI.

**Estado dejado para entrega:** `mode = DISABLED`, `submode = CONSERVATIVE` (verificado tras el POST final).

---

## 6. HANDOFF

- **Paso 3-4:** commit del código probado → `08073c3`.
- **Paso 5-6:** bump a `versionCode=17` / `1.7-r23e-shadow` / `R23E-SHADOW`, compilado con HEAD=`08073c3` → la APK embebe **exactamente** el SHA probado (§3.7). Nota para AGY: el bump de `build.gradle.kts` **no está commiteado** — si lo commiteo ahora, `GIT_SHA` dejaría de coincidir con el commit probado y rompería la regla de trazabilidad del preregistro §10 (mismo criterio que apliqué en R23D).
- **Paso 7-8:** APK v17 instalada y verificada en runtime (§3.8, §3.9). El único campo no conforme es `health.state`, con causa raíz identificada (§5).
- **Paso 9:** este reporte.
- **Paso 10:** notificación a AGY en curso.

### Anclas para el validador (no dependen del commit)

| Ancla | Valor |
|---|---|
| `versionCode` | `17` |
| `buildId` | `R23E-SHADOW` |
| Formato del evento | `ADAPTIVE_BLOCK [sim:NN%] <razon>` |
| CSV de la ventana | `counterfactual_adaptive_block.csv` |
| Sección del reporte | §2B (CF_WR / CF_EV / buckets) |
| `result` posibles | `COUNTERFACTUAL_WIN` / `_LOSS` / `_VOID` |

### Advertencia para OpenCode (Fase 4)

1. **El campo `score` saldrá vacío** en `counterfactual_adaptive_block.csv` hasta que se propague la razón enriquecida por el pipeline de señales (D1). La segmentación por score de §8.4 saldrá `N/A`; **la de similitud sí funcionará** (98-99% en R23D).
2. **`health.state` será `BLOCKED`** mientras Binomo no esté operando en el dispositivo (§5). Esto **no impide** la captura contrafactual, que se alimenta del feed de ticks.
3. La muestra mínima del preregistro es `N ≥ 50`; con la deduplicación por dirección aplicada, la tasa de vetos de R23D (848/876) deja margen sobrado, pero conviene que la primera ventana sirva para verificar que el CSV se puebla antes de lanzar la campaña completa.
