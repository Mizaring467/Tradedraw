# R23 — ITERACIÓN 2: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base Iter 2:** `2ef2b72`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Hipótesis:** endpoint `/shadow_journal` + `volatilidadSuficiente` en `/status.feed`, bump a `versionCode=7` (`1.4-r23-iter2`).

---

## 1. Resumen de Cambios

### A. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `6` | **`7`** |
| `versionName` | `"1.4-r23-iter1"` | **`"1.4-r23-iter2"`** |
| `BUILD_ID` | `"R23-ITER1"` | **`"R23-ITER2"`** |
| `GIT_SHA` / `BUILD_TIMESTAMP` | dinámicos | **sin cambios** (extracción Iter 1 conservada) |

### B. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`

1. **Endpoint `/shadow_journal`** — nueva ruta en el `when (path)`:
   - `max` opcional, `params["max"]?.toIntOrNull() ?: 50`, acotado a `[1, 2000]`.
   - Lee `shadow_journal.csv` **solo lectura** desde el directorio de auditoría.
   - Archivo ausente/vacío → `200 {"success":true,"total":0,"totalEnArchivo":0,"lines":[]}`.
   - Presente → `200 {"success":true,"total":N,"totalEnArchivo":M,"lines":[...]}` con cada
     línea cruda del CSV escapada.
2. **`volatilidadSuficiente` en `/status.feed`**:
   ```kotlin
   "volatilidadSuficiente": ${if (ws != null) ws.recentPriceRangeRatio() >= TradingEngine.RANGO_RELATIVO_MINIMO else false},
   ```
3. Helpers nuevos a nivel de archivo (testables sin Android):
   - `internal fun buildShadowJournalJson(lines, totalEnArchivo)`
   - `internal fun escapeJsonForJournal(str)`
   - `private fun readShadowJournalTail(max): Pair<List<String>, Int>`

### C. `app/src/test/java/com/example/tradedraw/ShadowJournalEndpointTest.kt` (nuevo)
5 tests que congelan el contrato del endpoint. Ver §4.

---

## 2. DOS DESVIACIONES RESPECTO A LA ESPECIFICACIÓN (justificadas)

### 2.1. Resolución de directorio: se prueban AMBAS rutas, no solo la externa

La spec decía "usar `overlayService.getExternalFilesDir(null)` o almacenamiento interno de respaldo,
**tal como lo hace `TradeJournalLogger`**". El logger (`resolveDirs`, línea 103) elige
**externa si es escribible, interna si no** — es decir, puede escribir en la **interna** aunque la
externa exista. Una implementación que solo leyera la externa devolvería `total:0` con decisiones
reales en disco.

Implementado: se comprueban las dos rutas y se prefiere la que **contenga realmente el archivo**:

```kotlin
val interno = java.io.File(overlayService.filesDir, "TradeDraw_Audits")
val externo = overlayService.getExternalFilesDir(null)?.let { java.io.File(it, "TradeDraw_Audits") }
val archivo = listOfNotNull(externo, interno)
    .firstOrNull { java.io.File(it, "shadow_journal.csv").isFile }
    ?.let { java.io.File(it, "shadow_journal.csv") }
    ?: return emptyList<String>() to 0
```

### 2.2. Campo añadido `totalEnArchivo`

La spec fija `total: N` con las líneas devueltas. Pero con `?max=50` sobre un journal de 300 líneas,
`total=50` es indistinguible de "hay exactamente 50 decisiones". Añadido `totalEnArchivo` (conteo
real en disco, previo al recorte) **sin alterar `total`**, para que el validador distinga ambas
situaciones. Es estrictamente aditivo: el contrato de la spec queda intacto.

---

## 3. Restricciones Verificadas

- `TradingEngine.RANGO_RELATIVO_MINIMO` sigue siendo `1e-5` — **solo se lee y se compara**, nunca se escribe.
- Vetos V1/V2 y guard de desajuste de activo: **sin tocar** (diff no los roza).
- Despacho de taps y fail-closed de SHADOW MODE: **sin tocar**. `/shadow_journal` es de solo lectura
  y no tiene rama POST ni efecto sobre el estado del motor.
- El único endpoint que ejecuta taps sigue siendo `/trade`, sin cambios.

---

## 4. Estado de Tests Locales

Comando: `./gradlew.bat testDebugUnitTest --console=plain`

```
BUILD SUCCESSFUL in 59s
25 actionable tasks: 14 executed, 11 up-to-date
```

| Métrica | Valor |
|---|---|
| Clases de test | 38 |
| **Tests ejecutados** | **371** |
| Fallos | **0** |
| Errores | **0** |

Delta vs Iter 1: **366 → 371** (+5, los nuevos de `ShadowJournalEndpointTest`). Sin regresiones.

### Cobertura nueva (`ShadowJournalEndpointTest`)
| Test | Qué congela |
|---|---|
| `archivoVacioDevuelveContratoVacioValido` | caso sin archivo → 200 con `success:true`, `total:0`, `lines:[]` |
| `conservaCabeceraYLineasEnOrden` | cabecera + orden de líneas preservados |
| `escapaComillasDelCampoReasonSinRomperElJson` | el `reason` entrecomillado del CSV no rompe el JSON (`JSONObject` parsea y devuelve la línea intacta) |
| `escapaBackslashYControles` | `\`, `\t`, `\r` escapados; round-trip estable |
| `totalEnArchivoInformaElTamanoRealNoElRecortado` | `total`=devueltas, `totalEnArchivo`=en disco |

---

## 5. Evidencia de BuildConfig Generado

`app/build/generated/source/buildConfig/debug/com/example/tradedraw/BuildConfig.java`:
```
public static final int VERSION_CODE = 7;
public static final String VERSION_NAME = "1.4-r23-iter2";
public static final String BUILD_ID = "R23-ITER2";
public static final Long BUILD_TIMESTAMP = 1792095038984L;
public static final String GIT_SHA = "2ef2b72";
```

---

## 6. Comportamiento Esperado para la Validación de OpenCode

**Verificación de trazabilidad post-instalación (obligatoria):**
`GET /status` debe devolver `versionCode=7`, `versionName="1.4-r23-iter2"`, `buildId="R23-ITER2"`
y `gitSha` = SHA del commit que AGY compila (no el base `2ef2b72`, ver nota §8).

**Endpoint nuevo:**
```
GET /shadow_journal          → últimas 50 líneas
GET /shadow_journal?max=200  → últimas 200 líneas
```
Respuesta: `{"success":true,"total":50,"totalEnArchivo":312,"lines":["timestamp,iso_date,...", "..."]}`
- `totalEnArchivo` > `total` ⇒ hay más decisiones de las devueltas; subir `?max`.
- `totalEnArchivo` == 0 ⇒ SHADOW MODE no ha persistido ninguna decisión todavía.
- Las líneas llevan cabecera incluida y van en orden cronológico ascendente (la última es la más reciente).

**Booleano de volatilidad:**
`/status.feed.volatilidadSuficiente` = `rangoRelativoActual >= rangoRelativoMinimo`.
Combinado con los campos de Iter 1, OpenCode puede ahora leer *sin calcular ratios en cliente*:
`rangoRelativoActual`, `rangoRelativoMinimo` y el veredicto booleano.
`false` cuando no hay cliente WebSocket (no hay activo que medir).

**Control negativo sugerido para OpenCode:** cuando `volatilidadSuficiente=false` y el motor evalúe
una entrada, `/events` debe registrar `VETO V2`. El booleano **expone** la condición del veto V2, no
la sustituye: ambos deben coincidir. Si discrepan, es un hallazgo.

---

## 7. Handoff a AGY

**Pendiente de AGY:** compilar APK, instalar por ADB (`220733SL` / `UGU8KFUKM7E6CE8D`),
iniciar validación live ~20 min con OpenCode y actualizar `R23_AUTOMEJORA_COORDINATION.md`
(archivo reservado a AGY — no lo toqué).

**Notificación:** enviada por Herdr (`herdr agent prompt agy`).

> ⚠️ `GIT_SHA` se captura en **tiempo de configuración de Gradle**, con el HEAD existente al correr
> el build. Tras commitear estos cambios, el SHA embebido pasará a ser el del commit nuevo.
> `versionCode=7` + `buildId="R23-ITER2"` son las anclas inequívocas.
