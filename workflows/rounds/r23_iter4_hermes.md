# R23 — ITERACIÓN 4: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base Iter 4:** `59bdeb6`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Hipótesis:** `GET /ticks` (buffer circular de ticks validados) habilita la reproducción replay determinista → paridad Live ↔ Replay medible.

---

## 1. Resumen de Cambios

### A. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `8` | **`9`** |
| `versionName` | `"1.4-r23-iter3"` | **`"1.4-r23-iter4"`** |
| `BUILD_ID` | `"R23-ITER3"` | **`"R23-ITER4"`** |
| `GIT_SHA` / `BUILD_TIMESTAMP` | dinámicos | **sin cambios** |

### B. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
1. **`TickRingBuffer`** (nueva `internal class` a nivel de archivo): circular, `synchronized`, capacidad
   fija, `Entry(ts, ric, price)`, `record/tail/clear/size/totalRecibidos`.
2. **`buildTicksJson(ticks, totalEnBuffer)`** y **`buildTicksCsv(ticks)`** (funciones `internal` puras,
   testeables sin Android).
3. **Endpoint `GET /ticks`** en el `when (path)`: `?max` (default 500, acotado `[1,2000]`) y
   `?format=json|csv`.
4. **`sendCsvResponse`**: `Content-Type: text/plain; charset=UTF-8` para la rama CSV.

### C. `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
- `internal val tickBuffer = TickRingBuffer(TICK_BUFFER_CAPACITY)` + constante `TICK_BUFFER_CAPACITY = 2000`.
- **Alimentación en `onMarketTick`**, justo tras `latestMarketTick = tick`.

### D. `app/src/test/java/com/example/tradedraw/TicksEndpointTest.kt` (nuevo)
10 tests. Ver §5.

---

## 2. DECISIONES DE DISEÑO (verificadas, no supuestas)

### 2.1. Dónde se alimenta el buffer: `TradingEngine.onMarketTick`, no el WebSocket

La spec decía "en cada tick válido recibido por el WebSocket (`onMarketTick` / `onNewMarketTick`)".
Esos dos puntos **no son equivalentes**:

| Punto | Qué pasa ahí |
|---|---|
| `BinomoWebSocketClient.emitTick` / `onTickListener` | el tick ya pasó frescura y salto implausible, **pero todavía no** el guard de activo/desajuste del motor |
| **`TradingEngine.onMarketTick`** | el tick ya pasó TODOS los filtros y es el que realmente entra al motor |

Elegí `onMarketTick`: es el único punto donde "tick válido" significa lo mismo que "tick que el motor
vio". Registrar antes volcaría ticks que el motor descartó y el replay no podría reproducir nada —
justo el problema que la iteración debe cerrar. Ventaja extra: `MarketTick.asset` ya es el **RIC**, así
que el CSV alimenta el replay sin pasar por el mapeo nombre-visible→RIC.

### 2.2. Sin redondeo del precio (lo más importante de esta iteración)

El formato obvio para un CSV sería `%.6f`. **Lo medí y destruye el dato:**

```
precio Z-CRY/IDX      = 641.867302
delta por tick        = 641.867302 * 2.547e-9 = 1.6348e-06
%.6f de p             = 641.867302
%.6f de p + delta     = 641.867304   ← a 6 decimales cambia, pero...
```

El paso por tick del índice (~1,6e-06 sobre ~641,87) vive en el **sexto decimal**, y el rango relativo
que R23 investiga (2,5e-09) es dos órdenes menor. Un `%.6f` colapsa ticks consecutivos al mismo texto y
borra precisamente la anomalía que se quiere reproducir. Se usa el **round-trip exacto de Kotlin**
(`Double.toString`), que además coincide con lo que `loadTicks` del replay ya sabe leer
(`toDoubleOrNull`). Verificado end-to-end con el parser idéntico al del replay: 2 ticks parseados, la
variación sub-micro preservada.

### 2.3. Capacidad fija de 2000 (tope, no histórico)

2000 entradas ≈ 20-30 min a 1-2 tick/s = exactamente la ventana de validación de R23. Es un tope
**fijo a propósito**: el buffer vive en un `Service` permanente y sin tope sería una fuga de memoria.

### 2.4. `totalEnBuffer` (aditivo)

Mismo precedente ya aceptado en la Iter 2 (`/shadow_journal`): con `?max=500` sobre 2000 en buffer,
`total` solo no distingue "no hay ticks" de "hay más de los que pediste". Campo **aditivo**, no altera
el significado de `total`.

### 2.5. Se rechazan precios no finitos

Un `NaN`/`Infinity` incrustado en el JSON lo invalidaría (JSON no los admite). El feed ya los filtra,
pero el buffer no debe ser el punto por donde se cuelen. Se descartan y **no** cuentan como recibidos.

---

## 3. Restricciones Verificadas

- `RANGO_RELATIVO_MINIMO` sigue en `1e-5` — el diff no lo toca.
- Guards, confluencia y vetos: **sin tocar**.
- Fail-closed de SHADOW MODE: **sin tocar**. `TickRingBuffer.record` es una escritura en una colección
  en memoria; no abre órdenes, no despacha taps, no toca `RiskManager`.
- `/ticks` es **solo lectura** (`tail`), sin rama POST ni efecto sobre el motor.
- `TICK_BUFFER_CAPACITY` se añade **junto** a `RANGO_RELATIVO_MINIMO` en el `companion object`; no lo
  desplaza ni lo modifica.

---

## 4. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain`
```
BUILD SUCCESSFUL in 16s
```

| Métrica | Valor |
|---|---|
| Clases de test | 40 |
| **Tests ejecutados** | **389** |
| Fallos | **0** |
| Errores | **0** |

Delta: **379 → 389** (+10). Sin regresiones.
BuildConfig: `VERSION_CODE=9`, `VERSION_NAME="1.4-r23-iter4"`, `BUILD_ID="R23-ITER4"`, `GIT_SHA="59bdeb6"`.

> Nota: hubo un fallo intermedio en `csvConservaLaVariacionSubMicroDelIndice`. **Era error de la prueba,
> no del código**: aseveraba el valor decimal del delta, que no es representable en binario (medi­ría el
> error de la aritmética de la prueba). Corregido a round-trip exacto (`assertEquals(..., 0.0)`), que es
> la propiedad que de verdad importa.

---

## 5. Cobertura nueva (`TicksEndpointTest`)

| Test | Qué congela |
|---|---|
| `bufferVacioDevuelveContratoVacioValido` | sin ticks → `success:true`, `total:0`, `ticks:[]` |
| `jsonExponeTsRicYPricePorTick` | estructura `{ts,ric,price}` y valores |
| `csvTieneCabeceraEsperadaPorElReplay` | cabecera exacta `ts,ric,price` + fila |
| `csvConservaLaVariacionSubMicroDelIndice` | **precisión**: ticks consecutivos del índice no colapsan |
| `elBufferEsCircularYDescartaLoMasViejo` | **circularidad**: no crece más allá de la capacidad |
| `tailDevuelveLasUltimasNEnOrdenCronologicoAscendente` | orden que espera `loadTicks` |
| `maxSeAcotaAlRangoValido` | `max` 0/negativo/enorme → `[1, capacity]` |
| `ignoraPreciosNoFinitosParaNoInvalidarElJson` | NaN/Infinity descartados, JSON sigue válido |
| `totalEnBufferDistingueNoHayTicksDeHayMasDeLosPedidos` | 1200 en buffer, `?max=500` → 500/1200 |
| `elCsvQueEmiteElEndpointLoParseaElReplay` | **integración**: el CSV va a disco y lo lee el parser del replay |

---

## 6. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=9`, `versionName="1.4-r23-iter4"`, `buildId="R23-ITER4"`.

**Endpoint nuevo:**
```
GET /ticks                 → JSON, últimos 500
GET /ticks?max=2000        → JSON, todo el buffer
GET /ticks?format=csv      → text/plain, cabecera ts,ric,price
```

**Cierre del hallazgo de la Iter 3 — paridad Live ↔ Replay:**
1. Descargar los ticks de la ventana: `curl "http://127.0.0.1:8080/ticks?max=2000&format=csv" > ticks_live.csv`
2. Correr el replay con ese archivo: `BACKTEST_CSV=ticks_live.csv ./gradlew.bat testDebugUnitTest --tests '*ReplayBacktestTest*'`
3. Comparar con `scripts/validation/compare_live_replay.py`.

**Notas para que la comparación sea válida:**
- El CSV sale **ya en RIC**, así que `asset` coincide en ambos lados sin mapeo.
- Los `decision_id` son UUID v3 deterministas (Iter 3): la misma señal debe dar el mismo id.
- **El buffer es de esta ejecución:** tras reiniciar el servicio queda vacío. El `totalEnBuffer` avisa
  cuántos ticks retiene ahora mismo (máx. 2000 ≈ 20-30 min). Si la ventana viva fue más larga, los
  ticks más antiguos ya se descartaron — descargar **antes** de superar ese tope.
- Sigue vigente el artefacto de `compare_live_replay.py`: genera `SYNTH-<ts>-<action>` para filas sin
  `decision_id`. Comparar solo contra decisiones nuevas.

---

## 7. Handoff a AGY

**Pendiente de AGY:** compilar APK, ADB install (`220733SL` / `UGU8KFUKM7E6CE8D`), validación live
~20 min con OpenCode, y actualizar `R23_AUTOMEJORA_COORDINATION.md` (reservado a AGY, no lo toqué).

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time de Gradle: la APK compilada ahora embolsará el commit de esta
> iteración. Anclas inequívocas: `versionCode=9` + `buildId="R23-ITER4"`.
