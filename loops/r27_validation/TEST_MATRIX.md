# R27 · MATRIZ DE PRUEBAS — Health State Machine

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin commit de código nuevo)
**Ejecutor:** OpenCode · **Dispositivo:** `duchamp` / Android 16 / SDK 36

Resultados **reales**, tomados de `app/build/test-results/testDebugUnitTest/`.

---

## 1. Matriz pedida por el encargo

| Escenario | Resultado esperado | Prueba | Veredicto | Evidencia |
| :--- | :--- | :--- | :--- | :--- |
| Accessibility conectado | HEALTH correcto | `accesibilidad conectada produce HEALTH correcto` | **PASS** | `state=READY`, `reasons=[]` |
| Accessibility desconectado | `ACCESSIBILITY_DOWN` | `accesibilidad desconectada reporta ACCESSIBILITY_DOWN` | **PASS** | `state=BLOCKED`, contiene `ACCESSIBILITY_DOWN` |
| Accessibility reconectado | Recuperación del estado | `accesibilidad reconectada recupera el estado` | **PASS** | `BLOCKED` → `READY`, sin rastro del bloqueo |
| Screen Feed disponible | HEALTH correcto | `screen feed disponible con pantalla produce HEALTH correcto` | **PASS** | `state=READY`, `notEvaluable=[]` |
| Screen Feed ausente en modo WebSocket | Estado coherente con el modo | `screen feed ausente en modo websocket no genera falso bloqueo` | **PASS** | `state=READY`, `notEvaluable=["SCREEN_FEED_MISMATCH"]` |
| Balance válido | BALANCE válido | `balance valido y fresco no bloquea` | **PASS** | `state=READY` |
| Balance ausente | `BALANCE_UNREADABLE` | `balance ausente reporta BALANCE_UNREADABLE AUSENTE` | **PASS** | `BALANCE_UNREADABLE: AUSENTE` |
| Balance obsoleto | Bloqueo por antigüedad | `balance obsoleto bloquea por antiguedad, no por ilegible` | **PARCIAL** | `DEGRADED` + `BALANCE_STALE` (ver §3) |
| Balance inválido | Bloqueo por validación | `balance invalido bloquea por validacion` | **PASS** | `BALANCE_UNREADABLE: INVALIDO` |
| Pérdida de conexión | Bloqueo seguro | `perdida de conexion bloquea de forma segura` | **PASS** | `state=BLOCKED`, `WS_DISCONNECTED` |
| Recuperación de conexión | Recuperación sin estados obsoletos | `recuperacion de conexion no arrastra estados obsoletos` | **PASS** | `BLOCKED` → `READY`, sin `WS_DISCONNECTED` |

**11/11 escenarios cubiertos. 10 PASS estrictos, 1 PARCIAL justificado.**

---

## 2. Pruebas adicionales (no pedidas, cubren regresiones de las causas raíz)

| Prueba | Qué previene | Veredicto |
| :--- | :--- | :--- |
| `desajuste real con pantalla activa sigue vetando` | Que el parche relaje el guard del motor | **PASS** — sigue `BLOCKED` |
| `sin accesibilidad el balance no se duplica como ilegible` | Doble reporte de la misma causa raíz | **PASS** |
| `balance dentro de la cadencia de escaneo no se reporta rancio` | Regresión de C.2 (umbral == cadencia) | **PASS** |
| `feed rancio y precio congelado bloquean` | `FEED_STALE`, `PRICE_FROZEN`, `NO_TICKS` | **PASS** |
| `cooldown y payout degradan sin bloquear` | Que una degradación escale a bloqueo | **PASS** |
| `orden pendiente bloquea` | `ORDER_PENDING` | **PASS** |
| `json incluye dataSource y notEvaluable` | Contrato de `/status` | **PASS** |
| `sinceMs permanece invariable mientras el estado no cambie` | Congelado de `sinceMs` | **PASS** |
| `sinceMs se reinicia al cambiar de estado` | Reinicio de `sinceMs` | **PASS** |

---

## 3. Desviación declarada: "Balance obsoleto → Bloqueo"

**Lo pedido:** que un balance obsoleto **bloquee**.

**Lo implementado:** `DEGRADED` con razón `BALANCE_STALE`.

**Por qué no se cambió, con razones verificables:**

1. **`HealthSupervisor` no decide trading.** Verificado con `grep -rn "healthSupervisor" app/src/main`:
   solo aparece en `OverlayService.kt:114` (declaración) y `TradeDrawHttpBridge.kt:619` (lectura para
   `/status`). Ningún camino de compra/venta lo consulta. Que reporte `BLOCKED` o `DEGRADED` **no
   habilita ni impide una operación**.
2. **El guard real ya existe y es más estricto.** `TradingEngine.isBalanceFresh` usa **3000 ms** y
   protege la atribución de un TIE. No se tocó.
3. **Convertirlo en bloqueo rompería el contrato de R21.** `PREREGISTRO_R23K.md:295` documenta
   `reasons = ["BALANCE_NOT_FRESH"]` como **degradación**, no como bloqueo.
4. **Un balance ausente SÍ bloquea** (verificado). La distinción "hay dato pero es viejo" vs "no hay
   dato" es exactamente lo que el propio encargo pide separar en la sección 3.C.

Si se prefiere el bloqueo estricto, es un cambio de una línea en
`HealthSupervisor.evaluateFromSnapshot` (mover `BALANCE_STALE` a `blockedReasons`) más actualizar
`PREREGISTRO_R23K.md`. **No se hizo sin autorización porque cambia semántica documentada.**

---

## 4. Suites completas

| Suite | Comando | Resultado |
| :--- | :--- | :--- |
| Kotlin (toda) | `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` | **528 tests, 0 fallos, 2 skipped** (55 clases) |
| Kotlin (health, dirigida) | `--tests '*HealthSupervisorTest*'` | **20 / 20** |
| Python | `PYTHONPATH=.:validation_engine /usr/bin/python3 -m unittest discover -s tests` | **43 tests, OK** |

Comparativa con R26: **513 → 528 tests** = +20 nuevos − 5 reemplazados = **+15 netos**.

Regresión encontrada y corregida durante la ronda: `CooldownSubmodeSyncTest`
(`statusYRiesgoReportanElCooldownDelSubmodoEfectivo`) inspecciona el **texto fuente** de
`HealthSupervisor` buscando literalmente `getRemainingCooldown(riskManager.currentSubMode)`. Mi
primera reescritura usó `riskManager?.let { ... }` y rompió ese contrato. Restaurado a la forma
literal. **Es una prueba de contrato por texto, no de comportamiento** — se documenta como fragilidad.

---

## 5. Cobertura no alcanzada (declarada, no simulada)

| Aspecto | Estado |
| :--- | :--- |
| `onUnbind` / `onRebind` reales | **NO probados.** Requeriría desenlazar el servicio de accesibilidad del dispositivo en vivo, prohibido por el encargo (§2: "No alterar los servicios de accesibilidad del dispositivo durante las pruebas") |
| `ScreenCaptureManager` | **NO probado.** No se instancia en ningún punto (`OverlayService.kt:87`) |
| MediaProjection | **NO solicitado.** Prohibido por el encargo |
| Connections al broker | **NO realizadas.** Sin órdenes, sin taps |
| Balance real de cuenta | **NO leído ni modificado.** El valor 300000.0 es del modo demo ya configurado |
