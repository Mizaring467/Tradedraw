# R23B — ITERACIÓN 2: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base:** `b5eaf79`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Objetivo:** Inyectar la acción (BUY/SELL) en los eventos `ORDER_STATE` para que el validador la extraiga de forma determinística.

---

## 1. Resumen de Cambios

### A. `RiskManager.kt`
1. `setOrderState`: prefijo `pendingTradeAction?.name` en el detalle del evento.
2. `recordTradeSent`: `pendingTradeAction = action` **movido antes** de `setOrderState(ENVIADA, ...)`.
3. `markOrderUnprocessed`: `pendingTradeAction` **preservada** a través de `clearPendingTrade()`.

### B. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `13` | **`14`** |
| `versionName` | `"1.4-r23-yolo-iter1"` | **`"1.4-r23-yolo-iter2"`** |
| `BUILD_ID` | `"R23-YOLO-ITER1"` | **`"R23-YOLO-ITER2"`** |

### C. `RiskManagerTest.kt`
+5 tests nuevos. Ver §4.

---

## 2. La especificación dejaba DOS huecos que la implementación literal no cubría

REQ 1 pide añadir el prefijo en `setOrderState` y espera estos formatos:

```
"CONFIRMADA BUY saldo movido"
"ENVIADA BUY tap_exitoso"     <- hueco 1
"EXPIRADA BUY ..."            <- hueco 2
```

Verifiqué **los 4 sitios** que llaman a `setOrderState` y en dos de ellos el prefijo **no habría
aparecido con la implementación literal**, porque la acción se asigna o se borra en el momento
equivocado:

### Hueco 1 — `recordTradeSent`: la acción se asignaba DESPUÉS (líneas 687-688)

```kotlin
setOrderState(OrderState.ENVIADA, "tap despachado")   // aquí pendingTradeAction aún es null
pendingTradeAction = action                            // ...y se asigna aquí
```
El evento `ENVIADA` —el que registra el **despacho del tap**— habría salido sin acción.
**Corregido:** invertido el orden. El evento ahora es `ENVIADA BUY tap despachado`.

### Hueco 2 — `markOrderUnprocessed`: `clearPendingTrade()` borra la acción antes del evento

```kotlin
clearPendingTrade()          // pone pendingTradeAction = null
setOrderState(NO_PROCESADA, "ventana agotada sin movimiento de saldo")
```
Como el comentario de ese mismo bloque explica, los **ids** ya se preservaban por esta razón exacta
tras el `clear`. La acción no recibía el mismo tratamiento, así que el evento que registra el
**FALLO de envío** habría salido sin dirección — justo el caso que más interesa auditar.
**Corregido:** se preserva igual que los ids.

**Nota:** la spec cita el estado `EXPIRADA`, que no existe en el enum (`OrderState` tiene
`NINGUNA / ENVIADA / CONFIRMADA / NO_PROCESADA`); el equivalente real es `NO_PROCESADA`. Lo implementé
sobre el estado real.

---

## 3. Corrección a la especificación: `TradeEventLog.snapshot()` no existe

REQ 3 pide comprobar los eventos con `TradeEventLog.snapshot()`. Esa función **no existe**: la API real
es `recent(max)` / `lastOf(kind)` / `toJson(max)`. Usé `lastOf(ORDER_STATE)`.

Hay además una trampa que anoté en el código: **`recent()` devuelve los eventos MÁS RECIENTES PRIMERO**
(documentado en su KDoc), así que `recent().lastOrNull { ... }` devuelve el **más antiguo**, no el
último. Mi primera versión del test cometió ese error y falló; queda documentado para que no se repita.

---

## 4. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain --rerun-tasks` → **BUILD SUCCESSFUL in 50s**

| Métrica | Valor |
|---|---|
| Clases de test | 43 |
| **Tests ejecutados** | **426** |
| Fallos | **0** |
| Errores | **0** |

Delta: **421 → 426** (+5). Sin regresiones.
BuildConfig: `VERSION_CODE=14`, `VERSION_NAME="1.4-r23-yolo-iter2"`, `BUILD_ID="R23-YOLO-ITER2"`, `GIT_SHA="b5eaf79"`.

> Durante el desarrollo fallaron 2 de mis tests nuevos y **los 2 eran errores de los tests**, no del
> código: (a) el `recent().lastOrNull` descrito arriba; (b) `setOrderState` retorna temprano si el
> estado no cambia, así que el test de compatibilidad regresiva no emitía evento alguno. Corregidos
> ambos; el código de producción no se tocó por estos fallos.

---

## 5. Cobertura nueva

| Test | Qué congela |
|---|---|
| `testSetOrderStateIncludesPendingTradeAction` | `"CONFIRMADA BUY saldo movido"` |
| `testSetOrderStateIncludesPendingTradeActionSell` | `"CONFIRMADA SELL saldo movido"` |
| `testSetOrderStateWithoutPendingActionKeepsLegacyFormat` | compatibilidad regresiva: `"NINGUNA sin operacion en curso"`, sin espacio doble |
| `testOrderStateEventChainCarriesActionEndToEnd` | **hueco 1**: la cadena ENVIADA→CONFIRMADA lleva acción en AMBOS eventos |
| `testOrderStateUnprocessedCarriesActionDespiteCleanup` | **hueco 2**: `NO_PROCESADA SELL` a pesar del `clearPendingTrade` |

---

## 6. Verificación de que el parser obtiene BUY/SELL (no `UNKNOWN`)

Simulé la extracción que hace `live_monitor_r23_autonomous.py` (buscando `BUY`/`CALL` y `SELL`/`PUT`
en el detalle) contra el formato viejo y el nuevo:

```
VIEJO  CONFIRMADA saldo movido             -> UNKNOWN   <- el defecto reportado
NUEVO  CONFIRMADA BUY saldo movido         -> BUY
NUEVO  CONFIRMADA SELL saldo movido        -> SELL
NUEVO  ENVIADA BUY tap despachado          -> BUY
NUEVO  NO_PROCESADA SELL ventana agotada   -> SELL
NUEVO  NINGUNA sin operacion en curso      -> UNKNOWN   <- correcto: en reposo no hay orden
```

`UNKNOWN` queda **solo** en el caso correcto (estado de reposo, sin operación). Los tres eventos de una
orden real (ENVIADA, CONFIRMADA, NO_PROCESADA) ahora traen la dirección.

---

## 7. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=14`, `versionName="1.4-r23-yolo-iter2"`, `buildId="R23-YOLO-ITER2"`.

**Criterio de éxito de la iteración:** `trades.csv` debe dejar de tener `action = UNKNOWN`. Cada fila de
trade debe resolver a `BUY` o `SELL` por el detalle del evento.

**Formatos esperados en `/events`:**
```
"ENVIADA BUY tap despachado"
"CONFIRMADA BUY saldo movido"
"NO_PROCESADA BUY ventana agotada sin movimiento de saldo"
```

**Regresión a vigilar:** el evento de reposo sigue siendo `"NINGUNA sin operacion en curso"` (sin
prefijo, sin espacio doble). Si ahí apareciera una acción, significaría que `pendingTradeAction` no se
está limpiando al cerrar la operación.

**Sin cambios de comportamiento en la operativa:** esta iteración es **solo telemetría**. No toca
guards, umbrales, el desbloqueo de YOLO de la iteración 1, ni el guard DEMO fail-closed. La ventana
debe reproducir la operativa de la iteración 1 (6-7 trades / 20 min) con la acción ya legible.

---

## 8. Handoff a AGY

**Pendiente de AGY:** build APK, ADB install (`220733SL`), activar YOLO (`/submode?name=YOLO`),
validación live ~20 min.

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time. Anclas inequívocas: `versionCode=14` + `buildId="R23-YOLO-ITER2"`.
