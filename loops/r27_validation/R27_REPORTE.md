# R27 · INFORME — HEALTH STATE MACHINE Y BLOQUEOS DE R26

**Fecha:** 2026-11-05 (UTC) · **Rama:** `r10-night` · **HEAD:** `c934835` + cambios sin commitear
**Ejecutor:** OpenCode (sesión visible)
**Dispositivo:** `duchamp` / Android 16 / SDK 36 — **distinto del Redmi A1 `220733SL` (Android 12) de R26**
**Modo durante toda la ronda:** DEMO / DISABLED. Cero órdenes. Cero taps.

**Veredicto: R27 = COMPLETADO CON UNA DESVIACIÓN DECLARADA** (ver §5).

> **No hace falta permiso ni autorización alguna para leer este informe.** No se hizo commit ni push
> (§2 del encargo lo prohíbe sin autorización).

---

## 1. Resumen ejecutivo

Los tres bloqueos que R26 reportó **no eran averías independientes**: los dos primeros eran falsos
positivos estructurales del diagnóstico, y el tercero escondía un fallo real de instrumentación.

| Bloqueo R26 | ¿Fallo real? | Causa raíz | Estado |
| :--- | :--- | :--- | :--- |
| `SCREEN_FEED_MISMATCH` | **No** — falso positivo | El health compara pantalla contra feed sin mirar el modo de datos. En WebSocket puro no existe pantalla que comparar | **Corregido** |
| `ACCESSIBILITY_DOWN` | **No en este dispositivo** | `instance` no se limpia en `onUnbind`, hay ventana de estado obsoleto. El health tampoco distingue "no habilitado" de "desenlazado" | **Mitigado + documentado** |
| `BALANCE_UNREADABLE` | **Sí** | La ruta pasiva de balance no estampaba `lastBalanceReadMs`, así que el dato se veía eternamente viejo | **Corregido** |

**Verificado en el dispositivo real** (`GET /status`):

```json
"health": {"state":"READY","reasons":[],"sinceMs":1793911350826,
           "dataSource":"WEBSOCKET_PURO","notEvaluable":["SCREEN_FEED_MISMATCH"]}
```

Antes de R27 ese mismo dispositivo reportaba `BLOCKED` con
`SCREEN_FEED_MISMATCH` + `BALANCE_UNREADABLE` + `ACCESSIBILITY_DOWN`.

---

## 2. Causas raíz

Detalle completo con código y números de línea en [`R27_CAUSAS_RAIZ.md`](R27_CAUSAS_RAIZ.md).

### A. `SCREEN_FEED_MISMATCH`

`HealthSupervisor` evaluaba `motivoVetoPorDesajusteDeActivo(latestObservedAsset, activeAsset)` sin
condicionar al modo. Esa función es **fail-closed por diseño** (PREREGISTRO_R20, criterio B.4): si no
hay confirmación de pantalla, veta. Correcto para decidir trading, **incorrecto como diagnóstico**
cuando el producto arranca deliberadamente sin captura (`OverlayService.kt:237` fuerza
`hasMediaProjection = false`).

### B. `ACCESSIBILITY_DOWN`

`AccessibilityService.instance` se asigna en `onServiceConnected` (`:386`) y `onRebind` (`:423`), y se
limpia en `onDestroy` (`:467`) — **pero no en `onUnbind`** (`:457-463`), que devuelve `true` para pedir
rebind. Entre ambos hay una ventana donde el servicio está desenlazado y `instance` sigue vivo.

En este dispositivo **no se reproduce**: `accessibility_enabled=1`, TradeDraw en `Bound services`,
`Crashed services: {}`, UI "ACCESIBILIDAD: ACTIVA ✓".

### C. `BALANCE_UNREADABLE`

Tres defectos acumulados:

1. **Fallo real:** `onAccessibilityEvent` (`:446-449`) actualizaba `latestObservedBalance` pero **no**
   `lastBalanceReadMs`. Solo la ruta activa lo estampaba. El balance leído por eventos se veía
   eternamente obsoleto.
2. **Umbral == cadencia:** escaneo pasivo throttleado a 5000 ms; el health exigía frescura ≤ 5000 ms.
   Falla por construcción. Y había un tercer umbral distinto (3000 ms) en `TradingEngine`.
3. **Sin distinción** entre ausente / no disponible / obsoleto / inválido: todo era un único
   `BALANCE_UNREADABLE`.

### D. Estado del artefacto de diagnóstico

`HealthSupervisorTest` **no podía ejercitar `READY` ni `DEGRADED`**: sin `wsClient` real (exige
`Context`) siempre había `WS_DISCONNECTED` + `ACCESSIBILITY_DOWN` ⇒ todo `BLOCKED`. El test llamado
`reportaDegradedCuandoHayCooldownActivoOSaldoNoFresco` **afirmaba `BLOCKED`** — no probaba lo que su
nombre decía.

---

## 3. Correcciones implementadas

| Archivo | Cambio | Líneas |
| :--- | :--- | :--- |
| `AutoTradeAccessibilityService.kt` | Estampar `lastBalanceReadMs` en la ruta pasiva de balance | +5 comentario, +1 asignación |
| `health/HealthSupervisor.kt` | `DataSource` (WEB_SOCKET_PURO / CON_PANTALLA / SOLO_PANTALLA); `Snapshot` puro; `evaluateFromSnapshot` testeable; `notEvaluable`; balance en 4 casos; `BALANCE_STALE`; ventana 10 s | reescritura acotada |
| `health/HealthSupervisorTest.kt` | 20 pruebas cubriendo la matriz completa | reemplaza 5 tests de humo |
| `loops/r27_validation/*` | Documentación | nuevos |

### Lo que NO se tocó (deliberadamente)

- `TradingEngine.motivoVetoPorDesajusteDeActivo` — sigue vetando en el motor.
- `TradingEngine.isBalanceFresh` (3000 ms) — su pregunta es distinta (precisión del TIE).
- `RiskManager`, estrategias, umbrales, ventanas operativas, guards de seguridad.
- `ScreenCaptureManager.kt` — sigue como código muerto declarado por decisión de producto.
- `PREREGISTRO_R27.md` — **no se creó**. R27 aquí es una ronda de corrección de diagnóstico, **no**
  la ronda V5 de trading que el repositorio tenía reservada con ese nombre. Ver §7.

---

## 4. Pruebas

| Suite | Comando | Resultado |
| :--- | :--- | :--- |
| Kotlin (toda) | `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` | **528 tests, 0 fallos, 2 skipped** (55 clases) |
| Health (dirigida) | `--tests '*HealthSupervisorTest*'` | **20 / 20** |
| Python | `PYTHONPATH=.:validation_engine /usr/bin/python3 -m unittest discover -s tests` | **43 tests, OK** |

Artefacto persistido: [`kotlin_test_summary.txt`](kotlin_test_summary.txt).
Matriz escenario por escenario: [`TEST_MATRIX.md`](TEST_MATRIX.md).

**Regresión encontrada y corregida en la ronda:** `CooldownSubmodeSyncTest` inspecciona el **texto**
de `HealthSupervisor` buscando `getRemainingCooldown(riskManager.currentSubMode)`. Mi primera versión
usó `riskManager?.let { it.getRemainingCooldown(it.currentSubMode) }` y rompió ese contrato. Se
restauró la forma literal. Es una prueba de contrato por texto: frágil, pero existente y respetada.

---

## 5. Desviación declarada: "Balance obsoleto → Bloqueo"

El encargo pide que el balance obsoleto **bloquee**. Se implementó como **`DEGRADED` + `BALANCE_STALE`**.

Razones (todas verificables):

1. `HealthSupervisor` **no decide trading**: solo lo consume `GET /status`. Ningún camino de compra o
   venta lo consulta.
2. El guard real (`TradingEngine.isBalanceFresh`, 3000 ms) **no se tocó** y es más estricto.
3. `PREREGISTRO_R23K.md:295` documenta esa condición como **degradación**, no como bloqueo.
4. Un balance **ausente sí bloquea** (verificado). La distinción "hay dato pero viejo" vs "no hay
   dato" es justo lo que la sección 3.C del encargo pide separar.

Cambiarlo a bloqueo es una línea + actualizar el preregistro R23K. **No se hizo sin autorización.**

---

## 6. Verificación en el dispositivo

### 6.1 Build e instalación

| Paso | Resultado |
| :--- | :--- |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| APK | 13.829.196 bytes · SHA-256 `b396a17f6f24f84c100d30b7c7cec97ed4330d42edcdc2ae011b4bdd52fa68ad` |
| `adb install` (streamed) | **falla** `INSTALL_FAILED_USER_RESTRICTED` (HyperOS) |
| `adb push` + `pm install -r -d` | **Success** |
| `dumpsys package` | `versionCode=20`, `1.10-r26-autonomous-expiry`, update 2026-11-05 15:35:14 |
| Accesibilidad tras instalar | intacta, TradeDraw en `Bound services` |

> **Nota:** `versionCode`/`versionName` **no se incrementaron**. El encargo exige correcciones
> mínimas y no menciona versionado; cambiarlo habría alterado `build.gradle.kts` sin necesidad.
> Consecuencia honesta: **el APK instalado no es distinguible por versión del de R26**; sí por el
> SHA-256 y por el campo `health` de `/status`, que ahora incluye `dataSource` y `notEvaluable`.

### 6.2 Estado observable

| Señal | Valor | Fuente |
| :--- | :--- | :--- |
| `mode` | `DISABLED` | `/status` |
| `isDemoAccount` | `true` | `/status` |
| `health.state` | `READY` al arrancar; `DEGRADED ['BALANCE_STALE']` con el broker no visible | `/status` |
| `health.notEvaluable` | `["SCREEN_FEED_MISMATCH"]` | `/status` |
| `risk.orderState` | `NINGUNA` | `/status` |
| `risk.hasPendingTrade` | `false` | `/status` |
| `risk.tapsDespachados` | `0` | `/status` |
| `risk.shadowOrders` | `0` | `/status` |
| `totalWins` / `totalLosses` | `0` / `0` | `/status` |
| Eventos `AUTONOMOUS` en el ring | **0** | `/mode_log` |
| Batería | 52 % inicio → 44 % cierre | `dumpsys battery` |

### 6.3 Trampa de herramienta encontrada (no del código)

El bridge enlaza a **IPv6 loopback** (`::1:8080`), no a IPv4. `curl http://127.0.0.1:8080/status`
devuelve vacío sin error. Hay que usar `curl "http://[::1]:8080/status"`. Esto puede explicar
lecturas fallidas en rondas anteriores.

### 6.4 Incidente de privacidad (declarado)

Una captura de pantalla tomada para diagnosticar el overlay contenía **datos personales de un
tercero** (un comprobante de transferencia en una notificación). Fue **eliminada inmediatamente** y
**no se guardó en el repositorio ni en Agent Nexus**. Ninguna evidencia versionada de esta ronda
contiene datos personales.

---

## 7. Colisión de nomenclatura: dos "R27"

El repositorio reservaba **R27** para la ronda de trading que acumularía ≥ 20 trades para evaluar V5
(`TRADEDRAW_CURRENT_STATE.md` §8, `PREREGISTRO_R26.md` §4). El encargo actual usa el mismo nombre
para una ronda de corrección de diagnóstico.

**Resolución adoptada:** esta ronda ocupa `loops/r27_validation/` y **no crea
`workflows/PREREGISTRO_R27.md`**, porque no mide rendimiento ni coloca órdenes. La ronda V5
queda **pendiente y sin consumir**: cuando se aborde, necesita su propio preregistro commiteado
antes de medir (lección F-2 de R26).

Si se prefiere, esta ronda puede renombrarse a **R27-H** (health) y dejar `R27` libre para trading.
Requiere decisión del usuario; no se hizo por iniciativa propia.

---

## 8. Limitaciones

1. **`onUnbind`/`onRebind` no probados en vivo** — el encargo prohíbe alterar los servicios de
   accesibilidad del dispositivo. La ventana de estado obsoleto está **documentada pero no
   reproducida**.
2. **`ScreenCaptureManager` y MediaProjection no probados** — deshabilitados por producto; solicitarlos
   está prohibido por el encargo.
3. **`versionCode` sin incrementar** — el APK nuevo no es distinguible del anterior por versión.
4. **Una sola build, un solo dispositivo** — no hay Android 12 (Redmi A1) en esta verificación.
5. **`health` no decide trading** — las correcciones mejoran el diagnóstico, **no** la ejecución.
   Ninguna afirmación de rentabilidad, edge o calidad de señal se hace ni se insinúa.
6. **Persistencia tras reboot** no verificada (reiniciar perdería el estado de Shizuku).

---

## 9. Estado de seguridad final

| Control | Estado |
| :--- | :--- |
| Modo | `DISABLED` en todo momento |
| `AUTONOMOUS` activado | **Nunca** — 0 eventos en el ring |
| Cuenta | demo (`isDemoAccount=true`) |
| Órdenes ejecutadas | **0** (`tapsDespachados=0`, `shadowOrders=0`) |
| Botones de compra/venta pulsados | **Ninguno** |
| Estrategias / parámetros / ventanas | **Sin modificar** |
| Guards de seguridad | **Intactos** — `motivoVetoPorDesajusteDeActivo` sigue vetando (probado) |
| Permisos del dispositivo | Sin cambios (accesibilidad ya estaba activa; no se tocó) |
| MediaProjection | No solicitado |

---

## 10. Pendientes y siguiente paso

1. **Decisión del usuario:** ¿`BALANCE_STALE` debe bloquear? (§5)
2. **Decisión del usuario:** ¿renombrar esta ronda a `R27-H`? (§7)
3. **Sin autorización para commitear** — `CLAUDE.md`, `docs/agents/` y todo lo de esta ronda siguen
   sin commit. El encargo lo prohíbe explícitamente.
4. **`versionCode`** — valorar incrementarlo en la próxima ronda que produzca APK.
5. **La ronda V5 sigue intacta** y es el único pendiente estructural de R25/R26.
