# R23A — ITERACIÓN 3: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base:** `c446406`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Objetivo:** Erradicar la regresión JSON de `/status` (RFC 8259) y blindarla con un test de parsing estricto.

---

## 1. La regresión era mía. Confirmada y corregida

El hallazgo de OpenCode es correcto y la causa es mía: en la Iteración 2 metí 5 líneas de comentario
`//` **dentro** del literal multilínea que construye el JSON de `/status`. En Kotlin esos `//` no son
comentarios, son texto del string, y salían literalmente por el HTTP. Ninguna suite lo cazó porque
**ningún test parseaba el documento completo**: los tests del bridge comprobaban campos sueltos.

No fue un descuido del commit, fue una ceguera de la suite. Eso es lo que arregla el test nuevo.

---

## 2. Resumen de Cambios

### A. `TradeDrawHttpBridge.kt`
- **Eliminados** los 5 comentarios `//` de dentro del literal de `buildStatusJson()`.
- La explicación se movió a un KDoc **sobre la función**, fuera del literal, con la regla explícita:
  *"este string NO admite comentarios `//` dentro"* y el motivo (RFC 8259).
- Deliberadamente **no** se añadió un campo `_comment` en el JSON: sería válido, pero ensuciaría el
  contrato que consumen los validadores.

### B. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `11` | **`12`** |
| `versionName` | `"1.4-r23-auto-iter2"` | **`"1.4-r23-auto-iter3"`** |
| `BUILD_ID` | `"R23-AUTO-ITER2"` | **`"R23-AUTO-ITER3"`** |

### C. `StatusJsonStrictSyntaxTest.kt` (nuevo, 5 tests)

---

## 3. El test: parsea el JSON, no busca el síntoma

La tarea pedía un test que valide sintaxis estricta. Un test que buscara el texto `//` habría cubierto
**la instancia** que ya ocurrió, no la clase de problema. Este test:

1. **Extrae el literal** `"""..."""` que `buildStatusJson()` devuelve, directamente del fuente.
2. **Sustituye** las interpolaciones `${...}` / `$var` por valores JSON válidos
   (`"${...}"` → `"P"`, `${...}` → `0`; el orden importa o una comilla de cierre se come el resto).
3. **Lo mete en un parser real** (`org.json.JSONObject`). Falla ante cualquier cosa que invalide el
   documento: comentarios, comas colgantes, llaves sin cerrar, comillas sin escapar.

**Por qué se lee el fuente y no se llama a `buildStatusJson()`:** es `private` de instancia y
`TradeDrawHttpBridge` exige un `OverlayService` (Android) en el constructor — no hay forma de invocarla
en un test JVM de unidad. Extraer el literal es el sustituto honesto: prueba exactamente el texto que se
emitirá, que es donde vivía el fallo. Mismo enfoque que `ModeTransitionAuditTest`, que ya audita el
fuente para sus invariantes estructurales.

| Test | Qué cubre |
|---|---|
| `elStatusJsonParseaConUnParserEstricto` | **el documento completo** entra en un parser real |
| `elStatusJsonNoLlevaComentariosDentroDelLiteral` | `//` en el literal (ignora `://` para no marcar URLs) |
| `elStatusExponeObservedStakeYEffectiveStakeComoNumeros` | existen y son **números**, no cadenas |
| `elStatusConservaLosCamposDeTrazabilidadDeBuild` | `gitSha`/`buildId`/`versionCode`/`versionName`/`isDemoAccount` |
| `ningunOtroLiteralJsonDelPuenteLlevaComentarios` | **audita TODOS** los literales JSON del archivo, no solo `/status` |

---

## 4. VERIFICACIÓN NEGATIVA: comprobé que el test sí caza la regresión

No basta con que un test pase; hay que demostrar que falla cuando debe. **Inyecté la regresión a
propósito** (repuse un comentario `//` dentro del literal) y corrí solo esa suite:

```
5 tests completed, 5 failed
BUILD FAILED
```

Los 5 tests detectaron el defecto. Restaurado el archivo (verificado: 0 ocurrencias de la marca de
inyección) y re-ejecutado en limpio.

---

## 5. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain --rerun-tasks` → **BUILD SUCCESSFUL in 1m 4s**

| Métrica | Valor |
|---|---|
| Clases de test | 43 |
| **Tests ejecutados** | **414** |
| Fallos | **0** |
| Errores | **0** |

Delta: **409 → 414** (+5). Sin regresiones.
BuildConfig: `VERSION_CODE=12`, `VERSION_NAME="1.4-r23-auto-iter3"`, `BUILD_ID="R23-AUTO-ITER3"`, `GIT_SHA="c446406"`.

> Con `--rerun-tasks` explícito, para descartar que el resultado viniera de caché de Gradle.

---

## 6. Verificación con un parser EXTERNO (como lo haría OpenCode)

No me fié solo del test Kotlin: extraje el literal y lo pasé por `json.loads` de Python, que es
exactamente el parser que fallaba.

```
json.loads OK  -> claves raiz: 19
risk.observedStake = 0 | effectiveStake = 0
gitSha = P | buildId = P | isDemoAccount = 0
comentarios // dentro del literal: []
```

El documento parsea, los campos nuevos están en su sitio y no queda ningún `//` en el literal.

---

## 7. Auditoría de alcance: ¿había más casos?

Antes de corregir, barrí **todo** `TradeDrawHttpBridge.kt` buscando `//` dentro de cualquier literal
`"""`. Resultado: las 5 líneas reportadas por OpenCode y **ninguna más**. Tanto `/ticks` como
`/shadow_journal` construyen su JSON con `joinToString` sobre listas, no con literales multilínea, así
que no tenían el problema. Aun así, el test `ningunOtroLiteralJsonDelPuenteLlevaComentarios` queda
auditando el archivo completo para que un caso futuro en cualquier endpoint se detecte solo.

---

## 8. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=12`, `versionName="1.4-r23-auto-iter3"`, `buildId="R23-AUTO-ITER3"`.

**Verificación de la corrección (la que fallaba):**
```
curl -s http://127.0.0.1:8080/status | python -c "import sys,json; json.load(sys.stdin); print('OK')"
```
Debe imprimir `OK`. En la iteración 2 este mismo comando fallaba con
`Expecting property name enclosed in double quotes`.

**Criterio de regresión cerrada:** el monitor de 20 min debe completar la ventana **sin un solo
`UnicodeDecodeError`/`JSONDecodeError`** al leer `/status`, y sus contadores de muestras deben ser
coherentes con la duración (no deben faltar muestras por parseos fallidos).

**Campos que deben seguir intactos en `/status.risk`:** `observedStake = 100000.0`,
`effectiveStake = 100000.0`, `investmentAmount` sin cambios — el fix es de formato, no de valores.

**Guards:** sin tocar. `isDemoAccount` fail-closed y las 3 puertas de tap siguen exactamente como en la
iteración 2 (que dio KEEP con 95/95 muestras en DEMO).

---

## 9. Handoff a AGY

**Pendiente de AGY:** build APK, ADB install (`220733SL`), validación live ~20 min con OpenCode.

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time. Anclas inequívocas: `versionCode=12` + `buildId="R23-AUTO-ITER3"`.
