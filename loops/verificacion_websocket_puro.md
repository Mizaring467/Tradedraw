# Verificación independiente — Modo WebSocket Puro (TradeDraw)

**Rol:** subagente `probador` (verificación independiente, no arregla nada)
**Repo:** `C:\Users\heidy\Tradedraw`
**Fecha:** 2026-09-16
**Alcance:** confirmar que la captura de pantalla (MediaProjection / `ScreenCaptureManager` / `VisionAnalyzer`) quedó desconectada del flujo de decisión y ejecución, que el feed WebSocket es la fuente única, y que existe un guard de frescura fail-closed que no se puede saltar.

---

# ITERACIÓN 2 — VEREDICTO: PASA

> La iteración 1 emitió FALLA por un único criterio incumplido: el comentario `ponytail:` no estaba en `ScreenCaptureManager.kt`. Corregido y re-verificado en la sección 7. El cuerpo de las secciones 1-6 se conserva como evidencia de la iteración 1.

---

## 1. Compilación y tests

### 1.1 Tests (rerun completo, sin caché)

Comando exacto:

```
cd C:\Users\heidy\Tradedraw
$env:GRADLE_OPTS="-Dkotlin.compiler.execution.strategy=in-process"
.\gradlew testDebugUnitTest --rerun-tasks --no-configuration-cache
```

Salida relevante:

```
> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/SyntheticCandleEngineTest.kt:142:46 Variable 'lastSignalAction' initializer is redundant
w: file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/SyntheticCandleEngineTest.kt:507:13 Variable 'signalEmittedReason' is assigned but never accessed

> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 6m 19s
23 actionable tasks: 23 executed
```

**Nota sobre exit code:** el proceso `pwsh` reportó `[exit code: 1]` pese a `BUILD SUCCESSFUL`. Causa verificada: `pwsh` marca `NativeCommandError` cuando Gradle escribe advertencias por stderr (p. ej. `Warning: SDK processing...`). El veredicto real de Gradle es **BUILD SUCCESSFUL** y el exit code del wrapper de Gradle fue **0** en la ejecución no piped (`===EXITCODE:0===`). Se distingue explícitamente exit code del shell del veredicto de Gradle.

### 1.2 Resumen por suite

Leído de `app\build\test-results\testDebugUnitTest\*.xml` (atributos `tests`, `failures`, `errors`), tras el rerun:

| Suite | tests | failures | errors |
| :--- | ---: | ---: | ---: |
| `BinomoWebSocketClientTest` | 5 | 0 | 0 |
| `ChartViewportControllerTest` | 6 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 |
| `RiskManagerTest` | 24 | 0 | 0 |
| `ScreenCaptureManagerTest` | 3 | 0 | 0 |
| `SyntheticCandleEngineTest` | 23 | 0 | 0 |
| `TradingEngineTest` | 24 | 0 | 0 |
| `VisualHarnessTest` | 4 | 0 | 0 |
| **TOTAL** | **90** | **0** | **0** |

### 1.3 APK

Comando:

```
cd C:\Users\heidy\Tradedraw
$env:GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m"
.\gradlew assembleDebug --no-daemon --no-configuration-cache
```

Salida: `BUILD SUCCESSFUL in 4m 23s`

Artefacto: `app\build\outputs\apk\debug\app-debug.apk`, 12 994 356 bytes, `LastWriteTime = 16/09/2026 23:29:44` (posterior al build exitoso).

**Incidencias de entorno durante el build (NO imputables al cambio):**

- `BUILD FAILED` por **OOM del JVM**: `There is insufficient memory for the Java Runtime Environment to continue. Native memory allocation (malloc) failed to allocate 207856 bytes. Error detail: Chunk::new`. RAM libre en el host: 690 MB de 6 203 712 KB totales. Mitigado bajando el heap a `-Xmx1536m` y matando daemons Java previos.
- `NoSuchFileException: ...\META-INF\app_debug.kotlin_module` — caché de compilación Kotlin corrupta por un `clean assembleDebug` interrumpido por el OOM. Mitigado borrando `app\build\tmp\kotlin-classes`, `build\kotlin` y `.kotlin`.
- `Daemon compilation failed: Connection to the Kotlin daemon has been unexpectedly lost` (una vez).
- Tras limpiar la caché: **BUILD SUCCESSFUL**. Ninguno de estos fallos es un error de código.

---

## 2. Inspección de código

### 2.1 Guard de frescura en `executeHeadlessTrade` — PRESENTE, no saltable

`app\src\main\java\com\example\tradedraw\TradingEngine.kt`

```
1249:     fun executeHeadlessTrade(action: TradeAction, reasonDescription: String) {
1250:         // GUARD DE FRESCURA DEL FEED (fail-closed, fuente única = WebSocket).
1251:         // Sin tick reciente (<5000 ms) o sin conexión, se veta la operación: no se degrada
1252:         // a ninguna otra fuente (la captura de pantalla fue retirada por consumo de batería).
1253:         val tickAgeMs = latestMarketTick?.let { System.currentTimeMillis() - it.timestampMs }
1254:         if (tickAgeMs == null || tickAgeMs >= FEED_MAX_AGE_MS || !isFeedFresh()) {
1255:             Log.w(
1256:                 "TradingEngine",
1257:                 "⛔ Headless Trade $action VETADO por feed rancio: " +
1258:                     "ageMs=${tickAgeMs ?: -1} (umbral=${FEED_MAX_AGE_MS}ms) " +
1259:                     "fresh=${isFeedFresh()} → no se degrada a otra fuente"
1260:             )
1261:             return
1262:         }
```

- **Fail-closed real:** `tickAgeMs == null` (sin tick nunca) veta. `tickAgeMs >= 5000` veta. `!isFeedFresh()` veta. No hay rama alternativa que continúe la ejecución.
- **Sin degradación a otra fuente:** el guard no consulta `latestAnalysisResult` ni ningún camino de visión; el `return` es incondicional ante fallo.
- **Umbral único:** `TradingEngine.kt:161` → `private val FEED_MAX_AGE_MS = BinomoWebSocketClient.DEFAULT_FRESHNESS_MS`; `BinomoWebSocketClient.kt:470` → `const val DEFAULT_FRESHNESS_MS = 5000L`.
- **Proveedor fail-closed:** `TradingEngine.kt:166` → `fun isFeedFresh(): Boolean = feedFreshnessProvider?.invoke() ?: false`. Sin proveedor (null) devuelve `false` → veta. Inyectado en `OverlayService.kt:361` → `tradingEngine.feedFreshnessProvider = { wsClient.isFeedFresh() }`.

**¿Se puede saltar el guard?** El guard es la **primera sentencia ejecutable** del cuerpo de `executeHeadlessTrade` (líneas 1250-1262, antes de cualquier cómputo de coordenadas). El clic headless en `TradingEngine.kt:1391` (`accessibility.performClickAt(x, y)`) está **dentro** de la misma función (rango 1249 → 1407, delimitado por la siguiente declaración `private fun checkHeadlessTradeResolution` en 1408), por debajo del guard, sin ningún camino que lo alcance sin pasar por la línea 1254. Los demás `performClickAt` del archivo están en funciones distintas: `1161` y `1199`/`1203` en `executeAutonomousTrade`/`testAccessibilityClicks`, y `1391` en headless. **Conclusión estática: el guard no es saltable por ningún camino dentro de `executeHeadlessTrade`.**

### 2.2 `effectiveAnalysis` sintético, sin visión

```
1295:         // El análisis se construye SIEMPRE desde el motor sintético de ticks WebSocket.
1296:         // Ya no se reutiliza latestAnalysisResult (visión): sus mechas/S-R/choppiness son
1297:         // ceros en modo WebSocket puro y vetaban señales sobre datos nulos.
1298:         val distSup = syntheticCandleEngine.distanceToSupportRatio
1299:         val distRes = syntheticCandleEngine.distanceToResistanceRatio
1300:         val effectiveAnalysis = VisionAnalysisResult(
1301:             trend = syntheticCandleEngine.detectedTrend,
1302:             distanceToSupportRatio = distSup,
1303:             distanceToResistanceRatio = distRes,
```

Ya **no** hay `latestAnalysisResult ?: ...`. Grep de `latestAnalysisResult` en `TradingEngine.kt`: líneas 78 (declaración), 197 (asignación), 822, 940 (usos en ramas con visión) y 1296 (solo comentario). Ninguna lectura dentro del rango 1249-1407. **Regla cumplida.**

### 2.3 `BinomoWebSocketClient` — API pública y referencias

`app\src\main\java\com\example\tradedraw\BinomoWebSocketClient.kt`

```
 57:     private val isConnectedFlag = AtomicBoolean(false)
 67:     @get:JvmName("isConnected")
 68:     val isConnected: Boolean get() = isConnectedFlag.get()
 83:     val lastTickAgeMs: Long
 84:         get() {
 85:             val ts = latestTick?.timestampMs ?: return Long.MAX_VALUE
 86:             return (System.currentTimeMillis() - ts).coerceAtLeast(0L)
 87:         }
 84:     fun isFeedFresh(freshnessThresholdMs: Long = DEFAULT_FRESHNESS_MS): Boolean =
 85:         isConnected && lastTickAgeMs < freshnessThresholdMs
470:         const val DEFAULT_FRESHNESS_MS = 5000L
```

- `isConnected` (público, solo lectura) e `isFeedFresh()` existen. `lastTickAgeMs` existe.
- Renombrado correcto: `isConnectedFlag` se usa en 103, 127, 134, 170, 190, 197, 212 (`.set(...)`/`.get()` internos). **No queda ninguna referencia rota** a la antigua `AtomicBoolean isConnected`. Grep global de `isConnected` en `*.kt` sólo muestra: las nuevas líneas 67/68/85, usos legítimos de `WebSocketState.DISCONNECTED` (distinto símbolo), `MainActivity.kt:94` (variable local de accesibilidad, homónima e independiente) y `TradeDrawHttpBridge.kt:223` (`ws?.isConnected`, la nueva propiedad).
- `@get:JvmName("isConnected")` preserva el nombre JVM para consumidores Java/reflexión.

### 2.4 Bloque `"feed"` en `/status`

`app\src\main\java\com\example\tradedraw\TradeDrawHttpBridge.kt`

```
220:         // Fuente única de datos: feed WebSocket del broker (la captura de pantalla fue retirada por consumo de batería).
221:         val ws = overlayService.binomoWebSocketClient
222:         val wsAge = ws?.lastTickAgeMs ?: Long.MAX_VALUE
223:         val wsConnected = ws?.isConnected ?: false
224:         val wsAsset = ws?.activeAsset ?: ""
225:         val wsFresh = ws?.isFeedFresh() ?: false
226:         // Antigüedad infinita no es serializable en JSON: se emite como -1 (sin tick recibido nunca).
227:         val wsAgeJson = if (wsAge == Long.MAX_VALUE) -1L else wsAge
...
256:             "activeSignal": $signalJson,
257:             "feed": {
258:                 "source": "websocket",
259:                 "lastTickAgeMs": $wsAgeJson,
260:                 "activeAsset": "${escapeJson(wsAsset)}",
261:                 "ws_active_asset": "${escapeJson(wsAsset)}",
262:                 "connected": $wsConnected,
263:                 "isFresh": $wsFresh
264:             }
265:         }""".trimIndent()
```

Contiene los cuatro elementos pedidos: antigüedad del último tick en ms (`lastTickAgeMs`), activo suscrito (`activeAsset` / alias `ws_active_asset`), conexión viva (`connected`) y `isFresh`.

**Validación sintáctica del JSON:** se extrajo la plantilla multilínea completa (líneas 229-265) sustituyendo los interpoladores por literales representativos (`mode`, `strat`, `bal`, `signalJson = null`, `wsAgeJson = 1234`, `wsAsset = "BTC/USD"`, `wsConnected = true`, `wsFresh = true`) y se parseó con `ConvertFrom-Json`:

```
JSON VALIDO. feed.isFresh=True feed.lastTickAgeMs=1234 feed.connected=True feed.source=websocket feed.activeAsset=BTC/USD
```

Llaves y comas correctas; la coma añadida tras `"activeSignal": $signalJson` (línea 256) cierra bien la secuencia previa al nuevo objeto `"feed"`; **sin comas colgantes** (el último miembro del objeto `feed` y del objeto raíz no lleva coma). `Long.MAX_VALUE` no se serializa crudo: se traduce a `-1`.

### 2.5 `OverlayService` — arranque sin grabación

`app\src\main\java\com\example\tradedraw\OverlayService.kt`

```
299:         // Modo WebSocket Puro SIEMPRE activo: la captura de pantalla está deshabilitada por decisión
300:         // de producto (consume batería del teléfono muy rápido y lo ralentiza). Aunque llegue un
301:         // dataIntent de MediaProjection, se ignora y nunca se arranca ScreenCaptureManager.
302:         startTradeDrawForeground(hasMediaProjection = false)
303:         if (dataIntent != null) {
304:             Log.w("TradeDraw", "DataIntent de MediaProjection recibido pero IGNORADO (captura deshabilitada por batería)")
305:         }
306:         Log.d("TradeDraw", "Modo Headless activo (WebSocket puro, 0% consumo de pantalla)")
```

- `hasMediaProjection = false` **incondicional**: no hay rama que pase `true`. El `dataIntent` se lee pero se descarta con un `Log.w`.
- **Rutas residuales buscadas** (`ScreenCaptureManager(`, `startCapture`, `refreshVirtualDisplay`):
  - `OverlayService.kt:54` → `private var screenCaptureManager: ScreenCaptureManager? = null` (campo declarado, nunca asignado; solo se usa en el `?.destroy()` de la línea 2010).
  - `OverlayService.kt:2010` → `screenCaptureManager?.destroy()` (no-op, el campo siempre es null).
  - **Ninguna invocación de `startCapture(...)` ni de `refreshVirtualDisplay()`** en `OverlayService.kt`. La anterior llamada en `onConfigurationChanged` (antes línea 388) fue reemplazada por el comentario `// Sin VirtualDisplay que refrescar: la captura está deshabilitada.` (línea ~383).
  - `OverlayService.kt:911-915`: el toggle de modo ya no arranca captura; sólo muestra el Toast `"Modo: ${newMode.name} (WebSocket puro)"`.
  - `OverlayService.kt:1511` → `val isHeadless = true` (fijo) y `val frames = 0L`.
  - Las únicas menciones restantes de `ScreenCaptureManager(`/`startCapture`/`refreshVirtualDisplay` están **dentro de `ScreenCaptureManager.kt`** (su propia definición, líneas 29, 159, 419) — la clase existe pero queda huérfana.
- Coherente con `MainActivity`: ya no hay solicitud de MediaProjection (ver 2.6).

### 2.6 `MainActivity` — sin MediaProjection

`app\src\main\java\com\example\tradedraw\MainActivity.kt`

```
124:         // Modo WebSocket Puro: NO se solicita MediaProjection (la grabación de pantalla agota
126:         launchService(null)
129:     private fun launchService(data: Intent?) {
```

- El `import android.media.projection.MediaProjectionManager` fue eliminado; `REQUEST_MEDIA_PROJECTION = 100` eliminado; el `startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), ...)` eliminado; la rama `requestCode == REQUEST_MEDIA_PROJECTION` en `onActivityResult` eliminada.
- `launchService(data: Intent?)` acepta null y sólo añade el extra si no es null; se invoca con `null`. Toasts actualizados a "Modo WebSocket Puro (sin grabación)". **Cumplido.**

### 2.7 `RiskManager.kt` — no modificado

```
> git diff --stat -- app/src/main/java/com/example/tradedraw/RiskManager.kt
(sin salida)
```

**Cumplido.** (Nota: `RiskManagerTest.kt` sigue existiendo y pasa 24/24.)

### 2.8 `VisionAnalysisResult` — conservado

`app\src\main\java\com\example\tradedraw\VisionAnalyzer.kt:37` → `data class VisionAnalysisResult(` sigue presente y es usada como contenedor del análisis sintético en `TradingEngine.kt:1300`. **Cumplido.**

### 2.9 `ScreenCaptureManager.kt` — existe, pero el comentario exigido está en otro archivo

- `app\src\main\java\com\example\tradedraw\ScreenCaptureManager.kt` **existe** (472 líneas).
- El encargo pedía que quedase "marcado con un comentario `ponytail:` que nombre el techo y la vía de mejora" en `ScreenCaptureManager.kt`. Grep de `ponytail` en ese archivo: **0 coincidencias**. Su encabezado (líneas 23-28) sigue siendo el docstring original ("Gestor de captura de pantalla continuo y optimizado con Modo Híbrido: Fast ROI..."), sin mención a que la clase está muerta.
- El comentario `ponytail:` sí existe, pero en **`OverlayService.kt:55-60`**:

```
55:     // ponytail: ScreenCaptureManager queda DESHABILITADO por decisión de producto (la grabación de
56:     // pantalla agota la batería y ralentiza el teléfono). No se instancia ni se pide MediaProjection.
57:     // Techo: la clase y los permisos siguen en el proyecto sin uso alguno.
58:     // Mejora: borrar ScreenCaptureManager.kt, ScreenCaptureManagerTest.kt, el permiso
59:     // FOREGROUND_SERVICE_MEDIA_PROJECTION en AndroidManifest.xml, la solicitud en MainActivity y
60:     // las ramas `hasMediaProjection` cuando se confirme que la visión no volverá.
```

Contiene techo y vía de mejora (correctos y bien redactados), pero está en el consumidor, no en el archivo señalado.

### 2.10 `VisionAnalyzerTest.kt` — retirado

```
> Test-Path app\src\test\java\com\example\tradedraw\VisionAnalyzerTest.kt
False
```

Y aparece en `git diff --cached --stat` como `1 file changed, 431 deletions(-)` (borrado preparado/staged). **Cumplido.**

### 2.11 Balance de llaves `{}`

| Archivo | HEAD | worktree | diff |
| :--- | :--- | :--- | :--- |
| `BinomoWebSocketClient.kt` | open=97 close=96 **diff=+1** | open=99 close=98 **diff=+1** | invariante |
| `MainActivity.kt` | 35/35 diff=0 | 31/31 diff=0 | 0 |
| `OverlayService.kt` | 507/507 diff=0 | 499/499 diff=0 | 0 |
| `TradeDrawHttpBridge.kt` | 85/85 diff=0 | 88/88 diff=0 | 0 |
| `TradingEngine.kt` | 345/345 diff=0 | 350/350 diff=0 | 0 |

El desbalance crudo de +1 en `BinomoWebSocketClient.kt` **ya existía en HEAD** (97/96 antes vs 99/98 después): el cambio añadió exactamente 2 `{` y 2 `}` (el bloque `companion object` y los cuerpos de las nuevas propiedades), preservando el delta. Causa del desbalance: llaves escapadas dentro de literales Regex —

- worktree, líneas 360-361
- HEAD, líneas 339-340 (mismas expresiones, desplazadas por las líneas nuevas):

```kotlin
val activeBlock = Regex("""\{[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(payload)?.value
    ?: Regex("""\{[^{}]*"rate"\s*:\s*[0-9.]+[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(payload)?.value
```

4 llaves escapadas (`\{` ×2, `\}` ×2) más 4 llaves de bloque reales (`[^{}]` ×4 en los dos regex) explican el +1 crudo del regex `\{`. **El cambio NO introdujo el desbalance.**

---

## 3. Estado del repo

```
> git status --short
+ Staged: 1 files
   app/src/test/java/com/example/tradedraw/VisionAnalyzerTest.kt
~ Modified: 7 files
   app/src/main/java/com/example/tradedraw/BinomoWebSocketClient.kt
   app/src/main/java/com/example/tradedraw/MainActivity.kt
   app/src/main/java/com/example/tradedraw/OverlayService.kt
   app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt
   app/src/main/java/com/example/tradedraw/TradingEngine.kt
   loops/MONITORING_REPORT_20MIN.md
   scripts/analyze_journal.py
? Untracked: 17 files (journals, logs de monitoreo, scratch/, screenshots/)
```

```
> git diff --stat
 7 files changed, 468 insertions(+), 109 deletions(-)

> git diff --cached --stat
 app/src/test/java/com/example/tradedraw/VisionAnalyzerTest.kt | 431 ---------------------
 1 file changed, 431 deletions(-)
```

**Archivos cambiados de código Kotlin (producción + test): 6** — `BinomoWebSocketClient.kt`, `MainActivity.kt`, `OverlayService.kt`, `TradeDrawHttpBridge.kt`, `TradingEngine.kt`, y el borrado de `VisionAnalyzerTest.kt`. Sin contar artefactos de monitoreo (`loops/MONITORING_REPORT_20MIN.md`, `scripts/analyze_journal.py`).

**Push: NO realizado.**

```
> git log origin/main..HEAD --oneline
(sin salida)
> git rev-list --count origin/main..HEAD
0
```

`HEAD` coincide con `origin/main`; el reflog muestra el último commit `02e2e8f feat(quant): motor microestructura crypto idx y fix tendencia hud` sin actividad de push posterior. **Cumplido: 0 commits nuevos empujados.**

---

## 4. Prueba del guard (intento de refutación)

**Método:** análisis del orden de sentencias y de todos los `performClickAt` del archivo, ya que la prueba dinámica requiere un dispositivo Android con Accesibilidad activa.

Evidencia:

```
> Select-String TradingEngine.kt -Pattern "performClickAt"
1161:             accessibility.performClickAt(x, y)      // executeAutonomousTrade
1199:         accessibility.performClickAt(buyX, buyY)     // testAccessibilityClicks
1203:             accessibility.performClickAt(sellX, sellY) // testAccessibilityClicks (handler.postDelayed)
1391:             accessibility.performClickAt(x, y)      // executeHeadlessTrade
```

Rango de `executeHeadlessTrade`: 1249 → 1407 (la siguiente declaración de función es `private fun checkHeadlessTradeResolution(tick: MarketTick)` en 1408). El único `performClickAt` de ese rango es el 1391. Entre el guard (1254-1262) y el 1391, todos los `return` tempranos son **adicionales** (ventana sniper 1268-1271, filtro anti-choppy 1277-1280, cooldown 1289-1292) y ninguno restaura el flujo ni rodea el guard.

**Resultado: no encontré ningún camino estático que ejecute `accessibility.performClickAt(...)` desde `executeHeadlessTrade` sin haber pasado por el guard de la línea 1254.** No hay `try/catch` que envuelva el guard, ni `goto`-equivalente, ni invocación reflejada.

**Los `performClickAt` de las líneas 1161 y 1199/1203 pertenecen a otras funciones** (`executeAutonomousTrade`, `testAccessibilityClicks`). No están cubiertos por el guard: `executeAutonomousTrade` es la ruta con visión (ahora sin frames) y `testAccessibilityClicks` es la prueba manual de calibración invocable desde el HUD. Ambas pueden pulsar sin verificar frescura del feed.

---

## 5. Qué NO se pudo probar

1. **Comportamiento en runtime con dispositivo Android.** No se ejecutó `adb install`/`am start` ni observación en dispositivo; la prueba del guard es estática (orden de sentencias + análisis de todos los puntos de clic), no dinámica. Queda sin verificar empíricamente: que el guard efectivamente vete cuando `latestTick` envejece en un teléfono real, y que `isFeedFresh()` devuelva `false` cuando el WebSocket cae.
2. **Respuesta real de `/status` en runtime.** El JSON se validó reconstruyendo la plantilla con literales representativos y parseándola; no se consultó `http://<device>:8080/status` contra la app corriendo, ni se comprobó `escapeJson(wsAsset)` con activos que contengan comillas o barras.
3. **Que `OverlayService` no arranque `ScreenCaptureManager` en alguna ruta no cubierta por el grep** (p. ej. reflexión). El análisis cubre todas las coincidencias textuales de `ScreenCaptureManager(`, `startCapture`, `refreshVirtualDisplay`, `MediaProjection` y `screenCaptureManager`; no se hizo análisis de bytecode.
4. **`executeAutonomousTrade` (líneas ~1075-1174)** sigue teniendo un `performClickAt` sin guard de frescura. No se determinó si esa ruta puede alcanzarse en modo WebSocket puro (depende de si algún llamador la invoca sin frames); sólo se constató que existe y que no está protegida.
5. **Estabilidad de los tests:** los 90 tests pasan en el rerun limpio, pero el entorno tiene sólo ~690 MB de RAM libre y ya provocó dos OOM del JVM y un `DaemonCrashedException`. Un fallo de build en este host puede ser de entorno, no de código.

---

## 6. Resumen de hallazgos

| # | Severidad | Ubicación | Hallazgo |
| :--- | :--- | :--- | :--- |
| 1 | bloqueante (contra el encargo) | `ScreenCaptureManager.kt` (archivo completo, sin `ponytail:`) vs `OverlayService.kt:55-60` | El comentario `ponytail:` con techo y vía de mejora **no está en `ScreenCaptureManager.kt`**, está en `OverlayService.kt`. El archivo señalado por el encargo conserva su docstring original y no indica que la clase quedó muerta. |
| 2 | menor | `TradingEngine.kt:1161`, `1199`, `1203` | `executeAutonomousTrade` y `testAccessibilityClicks` pulsan sin guard de frescura. No es la ruta headless, pero contradice el espíritu de "fuente única vetada por frescura" si alguna se alcanza. |
| 3 | menor | `AndroidManifest.xml:8,48` | Persisten `FOREGROUND_SERVICE_MEDIA_PROJECTION` y `foregroundServiceType="...|mediaProjection"` sin uso. Documentado en el propio `ponytail:` como pendiente de limpieza. |
| 4 | menor | `OverlayService.kt:54`, `2010` | Campo `screenCaptureManager` declarado y nunca asignado; el `?.destroy()` es código muerto. |
| 5 | informativo | `ScreenCaptureManager.kt:23-28` | Docstring describe activamente la captura "continua y optimizada" — engañoso para quien lea el archivo. |
| 6 | informativo | `scripts/analyze_journal.py`, `loops/MONITORING_REPORT_20MIN.md` | Cambios fuera del alcance del encargo (265+/77+ líneas). No verificados como parte de esta tarea. |
| 7 | informativo | entorno | 690 MB RAM libre causa OOM intermitente del JVM de Gradle; hay un `hs_err_pid103036.log` en la raíz del repo. |

Resto de las reglas del encargo verificadas **correctamente cumplidas**: guard presente y fail-closed, `effectiveAnalysis` sintético, API pública del WS con renombrado sin referencias rotas, bloque `"feed"` en `/status` con JSON válido, arranque sin MediaProjection, `MainActivity` sin solicitud, `RiskManager` intacto, `VisionAnalysisResult` conservado, `ScreenCaptureManager.kt` existente, `VisionAnalyzerTest.kt` retirado, balances de llaves invariantes, 90/90 tests verdes, `assembleDebug` correcto y sin push.

---

# 7. ITERACIÓN 2 — re-verificación

**Contexto:** el agente implementador corrigió el hallazgo bloqueante 1 y los residuales 3 y 4, y decidió no tocar el hallazgo 2 pidiendo refutación si procedía.

## 7.1 Hallazgo 1 (bloqueante) — CORREGIDO

`app\src\main\java\com\example\tradedraw\ScreenCaptureManager.kt:23-42` (docstring de clase ampliado):

```
23: /**
24:  * Gestor de captura de pantalla continuo y optimizado con Modo Híbrido:
...
29:  * ponytail: CLASE DESHABILITADA por decisión de producto. La captura de pantalla agota la batería
30:  * del teléfono muy rápido y lo ralentiza, así que el modo "WebSocket Puro" (WebSocket + SyntheticCandleEngine)
31:  * es el único modo de operación: nada en el flujo de decisión ni de ejecución vuelve a instanciar esta clase
32:  * ni a solicitar MediaProjection (ver OverlayService.onStartCommand y MainActivity.startFloatingService).
33:  *
34:  * Techo: la clase, su test, el permiso FOREGROUND_SERVICE_MEDIA_PROJECTION y el foregroundServiceType
35:  * `mediaProjection` siguen en el proyecto sin uso alguno, más el campo `screenCaptureManager` de
36:  * OverlayService que nunca se asigna.
37:  *
38:  * Mejora: cuando se confirme que la visión no vuelve, borrar ScreenCaptureManager.kt,
39:  * ScreenCaptureManagerTest.kt, el permiso y el serviceType en AndroidManifest.xml, el campo
40:  * `screenCaptureManager` de OverlayService con su `destroy()` no-op, y el parámetro
41:  * `hasMediaProjection` de startTradeDrawForeground.
42:  */
```

Grep de `ponytail` en `ScreenCaptureManager.kt`: **1 coincidencia, línea 29** (antes 0). El bloque nombra explícitamente **Techo** (34) y **Mejora** (38), con vía de mejora concreta y accionable. **Criterio cumplido.**

Nota informativa: el texto del techo quedó parcialmente obsoleto dentro de la misma iteración — menciona el permiso `FOREGROUND_SERVICE_MEDIA_PROJECTION`, el `foregroundServiceType mediaProjection` y el campo `screenCaptureManager` como pendientes, pero los tres fueron eliminados en esta misma corrección (7.2 y 7.3). El comentario es preciso sobre el estado previo, no sobre el actual. No bloquea; conviene podarlo en una próxima pasada.

## 7.2 Hallazgo 4 (campo muerto) — CORREGIDO

Eliminado `private var screenCaptureManager: ScreenCaptureManager? = null`. Diff de `OverlayService.kt`:

```
-    private var screenCaptureManager: ScreenCaptureManager? = null
```

Eliminado el `destroy()` no-op del `onDestroy`; `OverlayService.kt:2010` ahora es un comentario:

```
2010:         // Sin ScreenCaptureManager que destruir: la captura está deshabilitada.
```

**Verificación de ausencia de referencias al identificador como variable:** grep de `screenCaptureManager` en `app\src\main\java\com\example\tradedraw\*.kt`, filtrando comentarios:

- `OverlayService.kt` — **solo en comentarios** (líneas 54, 57, 60, 2010). Cero usos como expresión.
- `ScreenCaptureManager.kt` — la única aparición en "código" es la firma `class ScreenCaptureManager(` (línea 43) y las cadenas de log `"ScreenCaptureManager"` (105, 112, 115, 242, 244, 424, 478). Son el nombre de la **clase**, no la variable. Correcto.

Tampoco queda ninguna llamada a `startCapture` ni `refreshVirtualDisplay` fuera del propio archivo de la clase. **Criterio cumplido.**

## 7.3 Hallazgo 3 (manifest) — CORREGIDO

Diff de `app\src\main\AndroidManifest.xml`:

```
@@ -5,7 +5,6 @@
-    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
...
@@ -45,7 +44,7 @@
-            android:foregroundServiceType="specialUse|mediaProjection" />
+            android:foregroundServiceType="specialUse" />
```

**Validación XML real** (`System.Xml.XmlDocument.Load` sobre el archivo, con namespace `android`):

```
MANIFEST XML VALIDO
---PERMISOS---
android.permission.SYSTEM_ALERT_WINDOW
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.VIBRATE
android.permission.WAKE_LOCK
---SERVICIOS---
svc=.OverlayService type=specialUse
svc=.AutoTradeAccessibilityService type=
```

- XML **válido**, parseado correctamente.
- **Cero** `MEDIA_PROJECTION` como permiso.
- `OverlayService` con `foregroundServiceType="specialUse"` únicamente; **cero** `mediaProjection`.

**Criterio cumplido.**

## 7.4 Hallazgo 2 — el razonamiento del implementador se SOSTIENE (no refutado)

Verifiqué la cadena de llamadas de forma independiente.

**Rama `executeAutonomousTrade` — CONFIRMADO INALCANZABLE.**

```
> Select-String -Path app\src\main\java\com\example\tradedraw\*.kt -Pattern "onNewFrame"
TradingEngine.kt  186    fun onNewFrame(bitmap: Bitmap) {
```

`onNewFrame` aparece **una sola vez en todo el árbol**: su propia declaración. **No tiene ningún llamador.** El implementador citó el llamador como `onNewFrame:353`, pero la línea 353 es la invocación de `handleSignal` **dentro** del cuerpo de `onNewFrame`, no una llamada a `onNewFrame`. Precisión aparte, la conclusión se refuerza: al no existir llamador de `onNewFrame`, la cadena completa queda muerta.

Cadena verificada, cada eslabón con un único punto de entrada:

| Eslabón | Declaración | Único llamador | Estado |
| :--- | :--- | :--- | :--- |
| `onNewFrame(bitmap)` | `TradingEngine.kt:186` | **ninguno** | inalcanzable |
| `handleSignal(...)` | `TradingEngine.kt:1016` | `TradingEngine.kt:353` (dentro de `onNewFrame`) | inalcanzable |
| `executeAutonomousTrade(...)` | `TradingEngine.kt:1114` | `TradingEngine.kt:1110` (dentro de `handleSignal`) | inalcanzable |
| `visionAnalyzer.findBrokerButtonCoordinates(...)` | `TradingEngine.kt:1126` | dentro de `executeAutonomousTrade` | inalcanzable |

Confirmación colateral: el encargo previo ya había eliminado la única invocación de producción de `onNewFrame` (`OverlayService.scm.startCapture { ... tradingEngine.onNewFrame(bitmap) }`, visible en el diff de 7.2 como líneas borradas). Sin captura de frames, la rama con visión no tiene entrada.

**Rama `testAccessibilityClicks` — DE ACUERDO CON NO GUARDARLA.**

```
OverlayService.kt:647    tradingEngine.testAccessibilityClicks()
TradingEngine.kt:1179    fun testAccessibilityClicks() {
```

Único llamador: el botón "TEST CLIC" del overlay. Es una comprobación manual y explícita del usuario sobre la Accesibilidad, no una operación de trading: no abre posición, no registra riesgo (`riskManager.recordTradeSent` no se invoca) y no consulta señales. Añadirle el guard de frescura lo vetaría precisamente en el escenario en que el usuario necesita diagnosticar (feed caído). **Acepto el razonamiento.**

**¿Existe alguna ruta real de trading que llegue a un clic sin guard?** Busqué todas las invocaciones de `performClickAt` en el árbol:

```
TradingEngine.kt:1161    dentro de executeAutonomousTrade  → inalcanzable (7.4)
TradingEngine.kt:1199    dentro de testAccessibilityClicks → prueba manual, sin trading
TradingEngine.kt:1203    dentro de testAccessibilityClicks → prueba manual, sin trading
TradingEngine.kt:1391    dentro de executeHeadlessTrade   → protegido por el guard de la línea 1254
OverlayService.kt:252/257 → clics de TEST SUBE / TEST BAJA (comando manual del overlay)
```

**No encontré ninguna ruta de trading alcanzable que ejecute un clic sin haber pasado por el guard.** El hallazgo 2 queda degradado a **informativo**, sin acción requerida.

## 7.5 Build, tests y estado del repo

Comando del encargo, ejecutado:

```
cd C:\Users\heidy\Tradedraw
$env:GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m"
.\gradlew testDebugUnitTest assembleDebug --no-daemon --no-configuration-cache
→ BUILD SUCCESSFUL in 53s
```

Como esa ejecución fue mayormente `UP-TO-DATE`, la **repetí con `--rerun-tasks`** para obtener evidencia fresca y no reutilizar artefactos de la iteración 1:

```
$env:GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m -Dkotlin.compiler.execution.strategy=in-process"
.\gradlew testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --no-configuration-cache
→ BUILD SUCCESSFUL in 4m 40s
```

Resumen desde `app\build\test-results\testDebugUnitTest\*.xml` (rerun forzado):

| Suite | tests | failures | errors |
| :--- | ---: | ---: | ---: |
| BinomoWebSocketClientTest | 5 | 0 | 0 |
| ChartViewportControllerTest | 6 | 0 | 0 |
| ExampleUnitTest | 1 | 0 | 0 |
| RiskManagerTest | 24 | 0 | 0 |
| ScreenCaptureManagerTest | 3 | 0 | 0 |
| SyntheticCandleEngineTest | 23 | 0 | 0 |
| TradingEngineTest | 24 | 0 | 0 |
| VisualHarnessTest | 4 | 0 | 0 |
| **TOTAL** | **90** | **0** | **0** |

**APK:** confirmado. `app\build\outputs\apk\debug\app-debug.apk`

- Tu cifra: `13 302 795` bytes, `16/09/2026 23:37:38` → **verificada exacta** en la primera lectura (artefacto de tu ejecución).
- Tras mi rerun forzado: `13 302 780` bytes, `16/09/2026 23:44:45` (regenerado; la diferencia de 15 bytes es normal entre builds).

**Estado del repo:**

```
> git rev-list --count origin/main..HEAD
0
> git log origin/main..HEAD --oneline
(sin salida)
> git diff --stat -- app/src/main/java/com/example/tradedraw/RiskManager.kt
(sin salida)
```

Sin push (0 commits adelante de `origin/main`) y `RiskManager.kt` intacto. **Ambos criterios cumplidos.**

**Balance de llaves** (recomprobado tras los cambios):

| Archivo | diff `{`−`}` |
| :--- | ---: |
| BinomoWebSocketClient.kt | +1 (preexistente, llaves escapadas Regex 360-361) |
| MainActivity.kt | 0 |
| OverlayService.kt | 0 |
| TradeDrawHttpBridge.kt | 0 |
| TradingEngine.kt | 0 |
| ScreenCaptureManager.kt | 0 |

Sin regresión. El +1 sigue siendo el mismo desbalance preexistente en HEAD, ya explicado en 2.11.

## 7.6 Hallazgos de la iteración 2

| # | Severidad | Ubicación | Hallazgo |
| :--- | :--- | :--- | :--- |
| 1 | informativo | `ScreenCaptureManager.kt:34-36, 39-40` | El texto del `ponytail:` quedó parcialmente desactualizado: lista como pendientes el permiso, el `serviceType` y el campo `screenCaptureManager` que fueron eliminados en esta misma iteración. Podar cuando convenga; no bloquea. |
| 2 | informativo | `TradingEngine.kt:186, 1016, 1114` | Cadena muerta `onNewFrame` → `handleSignal` → `executeAutonomousTrade` conservada sin llamadores. Código inalcanzable, sin riesgo, candidato a borrado futuro. |
| 3 | informativo | `ScreenCaptureManager.kt` (archivo completo) | Clase huérfana (486 líneas, diff de llaves 0) más `ScreenCaptureManagerTest.kt` (3 tests verdes). Conservados a propósito; el `ponytail:` los documenta. |
| 4 | informativo | entorno | Los fallos de build de la iteración 1 (OOM de JVM con ~690 MB libres, `DaemonCrashedException`, caché Kotlin corrupta) no reaparecieron en la iteración 2 usando `-Xmx1536m` y matando daemons previos. |

**Ningún hallazgo bloqueante o menor abierto.**

## 7.7 Qué sigue sin poder probarse (heredado de la iteración 1)

1. **Runtime en dispositivo Android.** El guard de frescura se verificó estáticamente (orden de sentencias + análisis de todos los puntos de clic), no dinámicamente. Sin `adb install` ni observación real. En particular: que el guard vete efectivamente cuando el tick envejece en un teléfono real, y que `isFeedFresh()` devuelva `false` con el WebSocket caído.
2. **Respuesta real de `/status` en vivo.** El JSON se validó reconstruyendo la plantilla y parseándola; no se consultó el endpoint contra la app corriendo, ni se probó `escapeJson` con activos que contengan comillas o barras.
3. **Análisis de bytecode** para rutas no textuales (reflexión) hacia `ScreenCaptureManager`.
4. **Arranque real del overlay** con el manifest ya sin `mediaProjection`: no se comprobó en dispositivo que `startForeground` con `specialUse` no lance excepción tras quitar el serviceType. El build y el `processDebugManifest` pasan, lo que es evidencia fuerte pero no runtime.

## 7.8 Veredicto de la iteración 2

**PASA.** Los tres puntos corregidos (1, 3, 4) están verificados con evidencia de línea y de parseo. El razonamiento del implementador sobre el hallazgo 2 queda **aceptado y reforzado** por mi propia verificación de la cadena de llamadas (el implementador fue incluso conservador: `onNewFrame` no tiene llamador alguno, la cadena entera está muerta). Build y tests: 90/90 verdes sobre rerun forzado, `assembleDebug` correcto. Sin push, `RiskManager.kt` intacto, balances de llaves sin regresión.

No quedan hallazgos bloqueantes ni menores abiertos. Los cuatro hallazgos de esta iteración son informativos y ninguno requiere acción para aceptar el objetivo.