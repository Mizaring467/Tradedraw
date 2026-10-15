# R23 — ITERACIÓN 3: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base Iter 3:** `fd74445`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Hipótesis:** `decision_id` determinista en el motor vivo y en el de replay → paridad Live ↔ Replay evaluable.

---

## 1. Resumen de Cambios

### A. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `7` | **`8`** |
| `versionName` | `"1.4-r23-iter2"` | **`"1.4-r23-iter3"`** |
| `BUILD_ID` | `"R23-ITER2"` | **`"R23-ITER3"`** |
| `GIT_SHA` / `BUILD_TIMESTAMP` | dinámicos | **sin cambios** |

### B. `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
1. **`generateDeterministicDecisionId(asset, signalTimestampMs, action)`** en `companion object`,
   junto a `RANGO_RELATIVO_MINIMO` (que sigue en `1e-5`, sin tocar).
2. **Callback `syntheticCandleEngine.onSignalGenerated`** (línea ~224): `UUID.randomUUID()`
   sustituido por la función determinista.
3. La clave lleva **el MILISEGUNDO del tick que disparó la señal** (`latestMarketTick.timestampMs`),
   no un reloj leído dentro del callback — ver §3.

### C. `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`
- `SignalSnapshot` y `SimulatedTrade` cargan `assetRic` / `decisionId`.
- `decisionId` se calcula con **la misma función** del motor vivo.
- `writeJournalCsv`: cabecera con `decision_id` (penúltima columna, antes de `source`) y valor
  emitido en cada fila.

### D. `app/src/test/java/com/example/tradedraw/DeterministicDecisionIdTest.kt` (nuevo)
8 tests. Ver §5.

---

## 2. DESVIACIÓN PRINCIPAL: la clave de la spec tenía dos defectos, medidos

La spec pedía `UUID.nameUUIDFromBytes("$asset-$epochSecond-$action")`. Lo medí contra las **17 filas
reales** de `loops/r23/iter_2/decisions.csv` antes de escribirlo:

```
CLAVE VIEJA (asset|seg|action)     filas= 17  unicas=  9  colisiones=8
CLAVE NUEVA (ric|ms|action)        filas= 17  unicas= 17  colisiones=0
```

Esa clave habría **fusionado 8 de 17 decisiones**, empeorando el emparejamiento que la iteración
debe arreglar. Dos causas, ambas corregidas:

### 2.1. El activo se nombra distinto en cada lado

- **Vivo:** `latestObservedAsset` = nombre **visible de pantalla** → `"Crypto IDX"`.
- **Replay:** el **RIC del feed** (`MarketTick.asset` ← `rawTick.ric`) → `"Z-CRY/IDX"`.

Hashear cada cadena tal cual da **ids distintos para la misma decisión**:
```
det("Crypto IDX", 1792095485, "NONE") → ccb8cd02-de9a-37b1-9193-b5155d2c2858
det("Z-CRY/IDX",  1792095485, "NONE") → 584ea954-f90a-38a8-aeb7-734edd7c933e
```
**Corrección:** el activo se normaliza a RIC con `AssetRicMapper.ricParaActivoVisible` antes de
hashear. Comprobé que ese mapeo es **idempotente sobre sus propias 16 salidas** (`Z-CRY/IDX`,
`ETHUSD-OTC`, `XAU/USD`, `Z-EUR/USD2`, …): un RIC ya canónico se devuelve igual, así que el replay
puede volver a pasarlo sin desviarse. Es el único punto del código donde mapeo pantalla→RIC ya
existía y probado (`AssetRicMapperTest`).

### 2.2. Resolución de segundo: los ticks de una misma vela colisionan

El motor evalúa la misma vela en ticks sucesivos; varios vetos comparten `(activo, segundo, acción)`.
Los 8 solapes de arriba son exactamente eso.
**Corrección:** la clave lleva **milisegundos**. Para no romper la firma que pide la spec, la función
**acepta segundos o milisegundos**: si recibe un valor `< 1e11` lo trata como segundos y lo escala
(`testSegundosYMilisegundosDelMismoInstanteDanElMismoId` lo fija).

### 2.3. Uso del ts del tick en vez de reloj en el callback

La spec escribía `val nowSec = System.currentTimeMillis() / 1000L` **dentro** del callback. Eso
desalinea el id respecto al instante real de la señal: el replay usa el `ts` del tick de entrada, y
el vivo un reloj leído después. Uso `latestMarketTick?.timestampMs`, que es el dato que el replay
también tiene. Fallback `System.currentTimeMillis()` si no hay tick.

### Límite conocido y aceptado
Dos decisiones del **mismo activo, mismo milisegundo y misma acción** comparten id. A 1-2 ticks/s el
caso es prácticamente imposible, y el comparador agrupa por id (no por posición), así que una
colisión pierde una fila, no corrompe el emparejamiento. Documentado en el KDoc en vez de inventar un
contador de secuencia, que **no** sería reproducible entre los dos motores.

---

## 3. Restricciones Verificadas

- `RANGO_RELATIVO_MINIMO` sigue en `1e-5` — el diff no lo toca.
- Guards, filtros de confluencia y condiciones de veto: **sin tocar**.
- Fail-closed de SHADOW MODE: **sin tocar**. `deterministicDecisionId` es una función pura; no
  abre órdenes ni taps. El único cambio en el callback es el origen del id.
- `decision_id` del journal principal (`TradeJournalLogger.HEADER`) y del SHADOW: **sin tocar** —
  siguen con sus columnas; la iteración solo añade la columna al CSV de replay.

---

## 4. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain`
```
BUILD SUCCESSFUL in 54s
25 actionable tasks: 13 executed, 12 up-to-date
```

| Métrica | Valor |
|---|---|
| Clases de test | 39 |
| **Tests ejecutados** | **379** |
| Fallos | **0** |
| Errores | **0** |

Delta: **371 → 379** (+8). Sin regresiones.

BuildConfig generado: `VERSION_CODE=8`, `VERSION_NAME="1.4-r23-iter3"`, `BUILD_ID="R23-ITER3"`,
`GIT_SHA="fd74445"`.

---

## 5. Cobertura nueva (`DeterministicDecisionIdTest`)

| Test | Qué congela |
|---|---|
| `esEstableEntreLlamadas` | pureza: mismo input → mismo UUID |
| `elMismoNombreVisibleYElMismoRicProducenElMismoId` | **el fix de 2.1**: `"Crypto IDX"` y `"Z-CRY/IDX"` → mismo id |
| `laNormalizacionEsIdempotenteSobreRics` | normalizar un RIC ya canónico no lo cambia |
| `segundosYMilisegundosDelMismoInstanteDanElMismoId` | compatibilidad de firma de la spec |
| `ticksDistintosDeLaMismaVelaNoColisionan` | **el fix de 2.2** |
| `idYAccionDistintosDanIdsDistintos` | discriminación por activo y por acción |
| `laAccionNoDistingueMayusculasNiEspacios` | normalización de la acción |
| `esV3YNoV4ComoElIdAleatorioAnterior` | el id es UUID v3, no v4: nadie reintrodujo el random |

---

## 6. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=8`, `versionName="1.4-r23-iter3"`, `buildId="R23-ITER3"`.

**Paridad Live ↔ Replay (lo que esta iteración habilita):**
- Los `decision_id` de `/events` deben ser ahora **UUID v3** (`...-3xxx-...`) y **estables**: la
  misma decisión debe reproducir el mismo id si la ventana se reejecuta.
- `compare_live_replay.py` debe pasar de `NO_PAIRED_DECISIONS` (0 pares) a **pares > 0**, siempre
  que el CSV de replay se genere con el motor actualizado (el replay emite la misma clave).
- **Cuidado con el artefacto de datos:** `compare_live_replay.py` genera `SYNTH-<ts>-<action>` para
  filas sin `decision_id` (journal histórico). Esos ids **nunca** emparejarán con el formato nuevo;
  la comparación real debe hacerse sobre las decisiones nuevas de esta ventana.
- El `asset` de los ids nuevos sale **normalizado a RIC**, así que el comparador verá
  `Z-CRY/IDX` en ambos lados aunque `/status.feed.activeAsset` siga reportando el nombre visible.

**Control negativo sugerido:** las decisiones con `volatilidadSuficiente=false` deben seguir dando
`VETO V2` (correlación 9/9 de la Iter 2, sin cambios). El determinismo del id **no** relaja el veto:
si ahora entraran decisiones que antes se vetaban, es un hallazgo.

---

## 7. Handoff a AGY

**Pendiente de AGY:** compilar APK, ADB install (`220733SL` / `UGU8KFUKM7E6CE8D`), validación live
~20 min con OpenCode, y actualizar `R23_AUTOMEJORA_COORDINATION.md` (reservado a AGY, no lo toqué).

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time de Gradle: la APK compilada ahora embolsará `8c…` (commit
> de esta iteración), no `fd74445`. Anclas inequívocas: `versionCode=8` + `buildId="R23-ITER3"`.
