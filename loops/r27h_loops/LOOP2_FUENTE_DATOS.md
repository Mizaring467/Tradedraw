# LOOP 2 — Fuente de datos del HealthSupervisor

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin commits)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED

---

## 1. Objetivo

Eliminar la dependencia implícita del valor por defecto de `HealthSupervisor.evaluate()` cuando
el componente llamador conoce la fuente real.

---

## 2. Hallazgo

| # | Ubicación | Hallazgo |
| :--- | :--- | :--- |
| H1 | `health/HealthSupervisor.kt:214` | `evaluate(..., dataSource: DataSource = DataSource.WEBSOCKET_PURO)` — valor por defecto |
| H2 | `TradeDrawHttpBridge.kt:619` | Invocaba `evaluate(...)` **sin pasar `dataSource`** |
| H3 | `OverlayService.kt` | **No existía** ninguna propiedad que dijera "¿estamos capturando pantalla?" |

### Cómo se determina realmente la fuente (verificado)

| Evidencia | Resultado |
| :--- | :--- |
| `OverlayService.kt:238` | `startTradeDrawForeground(hasMediaProjection = false)` — literal, sin condición |
| `ScreenCaptureManager(` en `app/src/main` | **0 coincidencias** — nunca se instancia |
| `hasMediaProjection` como campo | **No existía**; solo era un parámetro de función |
| `OverlayService.kt:240` | Aunque llegue un `dataIntent`, se ignora y se registra un warning |

**Conclusión:** hoy la fuente es `WEBSOCKET_PURO` y el valor por defecto *casualmente* acierta. Pero
el diagnóstico mentiría en silencio si alguien reactivara la captura, porque nadie declararía el
cambio. Ese es el defecto real: no el valor, sino la **ausencia de un punto de verdad explícito**.

---

## 3. Decisión técnica

1. **`OverlayService` es el poseedor autoritativo** — es quien llama a `startTradeDrawForeground` y
   quien sabría si hay un `MediaProjection`.
2. Se añade `var dataSource` (público, `private set`) y `setDataSource(...)` (`internal`).
3. Se fija explícitamente en `onStartCommand`, junto a la decisión de `hasMediaProjection`.
4. `TradeDrawHttpBridge` **pasa la fuente explícitamente**.
5. **No se infiere pantalla.** El valor por defecto sigue siendo `WEBSOCKET_PURO`: si alguien olvida
   declarar la fuente, el peor caso es "no evaluable", nunca un bloqueo espurio por desajuste.

### Cambios

| Archivo | Cambio |
| :--- | :--- |
| `OverlayService.kt` | `+ var dataSource`, `+ setDataSource(...)`, `+ setDataSource(WEBSOCKET_PURO)` en `onStartCommand` |
| `TradeDrawHttpBridge.kt` | `+ dataSource = overlayService.dataSource` con comentario |

**NO se tocó:** la rama `if (dataIntent != null)` que ignora MediaProjection, el parámetro
`hasMediaProjection` de `startTradeDrawForeground`, ni `HealthSupervisor` (que ya aceptaba el
parámetro desde R27-H).

---

## 4. Pruebas

`app/src/test/java/com/example/tradedraw/HealthDataSourceTest.kt` — **12 pruebas, 0 fallos.**

| Fuente | Pruebas |
| :--- | :--- |
| `WEBSOCKET_PURO` | no evalúa mismatch · ignora activo visible · json declara la fuente |
| `WEBSOCKET_CON_PANTALLA` | acepta coincidencia · veta desajuste real · veta pantalla desconocida · json |
| `SOLO_PANTALLA` | declara no evaluable sin feed · compara con feed conectado · json |
| Contrato | `hayPantalla` correcto en las tres · el default es seguro |

Comando y resultado real:
```
./gradlew testDebugUnitTest --tests '*HealthDataSourceTest*' --no-daemon --no-configuration-cache
→ BUILD SUCCESSFUL · tests="12" failures="0" errors="0"
```

---

## 5. `git diff --check`

Sin avisos en `TradeDrawHttpBridge.kt`. `OverlayService.kt` emite avisos de *trailing whitespace*
porque el archivo es **CRLF íntegro (2466/2466 líneas)** mientras el resto del repo es LF. Es una
inconsistencia **preexistente** que no se normaliza aquí: reescribir 2466 líneas para cambiar los
saltos de línea sería un cambio masivo fuera del alcance de un loop mínimo, y ensuciaría la
revisión del diff real (33 líneas).

---

## 6. Limitaciones de validación

| Aspecto | Estado |
| :--- | :--- |
| Las tres fuentes, lógica pura | **VERIFICADO** (12 tests) |
| Que el dispositivo esté en `WEBSOCKET_PURO` | **VERIFICADO por código** (`:238` literal, 0 instanciaciones de `ScreenCaptureManager`) |
| Que `/status` emita la fuente correcta tras instalar | **BLOQUEADO** — el APK instalado es el de R27-H, no contiene este cambio, y no se instala sin autorización |
| Cambio real de fuente con MediaProjection vivo | **NO PROBADO** — exigiría solicitar capura de pantalla, prohibido |

**No se declara validado en dispositivo.**

---

## 7. Siguiente acción recomendada

Con autorización: instalar y comprobar que `GET /status` sigue devolviendo
`"dataSource":"WEBSOCKET_PURO"`, ahora leído de `overlayService.dataSource` en vez del default.
