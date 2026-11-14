# R27 · CAUSAS RAÍZ — Bloqueos de health reportados por R26

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD de partida:** `c934835`
**Dispositivo de trabajo:** `duchamp` / Android 16 / SDK 36 (NO es el Redmi A1 de R26)
**Modo:** DEMO / DISABLED en todo momento. Sin órdenes. Sin tocar el broker.

---

## 0. Discrepancias del enunciado con el código real (VERIFICADO)

El encargo cita archivos y líneas que no corresponden al árbol actual:

| Enunciado | Realidad verificada |
| :--- | :--- |
| `MainActivity.kt` líneas 88, 237–240, 946 | `MainActivity.kt` tiene **169 líneas**. Las líneas 88, 237–240 y 946 están en **`OverlayService.kt`** |
| "Inicialización de `ScreenCaptureManager`" | `ScreenCaptureManager` **no se instancia en ningún punto**. Está deshabilitado por decisión de producto (`OverlayService.kt:87-92`) |
| "dispositivo POCO X6 Pro" | `ro.product.device=duchamp`, SDK 36. R26 se certificó en Redmi A1 `220733SL`, Android 12 |

Las tres líneas citadas son exactamente las que documentan esa desactivación:
`OverlayService.kt:87` (declaración del deshabilitado), `:237` (`hasMediaProjection = false`),
`:946` (no se pide MediaProjection al cambiar de modo).

**Implicación:** el enunciado asume que existe un feed de captura de pantalla. No existe. El
sistema usa **WebSocket puro**. Eso convierte `SCREEN_FEED_MISMATCH` en un falso positivo por
diseño, no en una avería.

---

## A. SCREEN_FEED_MISMATCH

### Causa raíz

`HealthSupervisor.evaluate()` (`health/HealthSupervisor.kt:79-85`) evalúa el desajuste
**sin mirar el modo de datos**:

```kotlin
val pantalla = AutoTradeAccessibilityService.latestObservedAsset.trim()
val feed = wsClient?.activeAsset?.trim() ?: ""
val mismatch = TradingEngine.motivoVetoPorDesajusteDeActivo(pantalla, feed)
if (mismatch != null) blockedReasons.add("SCREEN_FEED_MISMATCH: $mismatch")
```

`motivoVetoPorDesajusteDeActivo` (`TradingEngine.kt:948-959`) es **fail-closed por diseño**
(PREREGISTRO_R20, criterio B.4): si el feed sirve un activo conocido y la pantalla no lo
confirma, veta. Es correcto **para decidir trading**, porque sin pantalla el bot tampoco puede
pulsar.

Pero en **modo WebSocket puro** no hay captura de pantalla, así que `latestObservedAsset` solo
se llena si el servicio de accesibilidad está leyendo la pantalla del broker. Si la accesibilidad
no está enlazada o el broker no está en primer plano, `latestObservedAsset` queda vacío y la
función devuelve `"DESAJUSTE pantalla=DESCONOCIDA feed=..."`.

**Resultado: el health siempre reporta `SCREEN_FEED_MISMATCH` en modo WebSocket, aunque el feed
esté sano.** Es una incompatibilidad entre el contrato del guard (pantalla vs feed) y el modo de
captura configurado (ninguna), tal y como el enunciado sospechaba.

### Qué NO se hace

No se toca `motivoVetoPorDesajusteDeActivo`: sigue vetando en el motor cuando hay pantalla.
El parche es solo en la **capa de diagnóstico**, que no decide operaciones.

### Clasificación

**Fallo real de coherencia de diagnóstico**, no de seguridad. No relaja ningún guard de trading.

---

## B. ACCESSIBILITY_DOWN

### Causas raíz (dos, independientes)

**B.1 · Confusión entre "habilitado en Settings" y "enlazado"**

`HealthSupervisor` usa `accessibilityService == null` como único criterio, donde el valor por
defecto es `AutoTradeAccessibilityService.instance`. Ese campo estático se pone a `this` en
`onServiceConnected` (`:386`), en `onRebind` (`:423`) y se limpia en `onDestroy` (`:467`).

Hay una **ventana real de inconsistencia**: `onUnbind` (`:457-463`) devuelve `true` (pide rebind)
pero **no** limpia `instance`. Entre `onUnbind` y `onRebind` el servicio está desenlazado y
`instance` sigue apuntando a un objeto vivo, así que el health reporta accesibilidad OK con el
servicio caído. Es el "estado obsoleto" que el enunciado pide investigar.

**B.2 · El health no distingue por qué falta**

`ACCESSIBILITY_DOWN` se emite igual si el servicio nunca se activó, si se desenlazó, o si el
usuario lo desactivó. No hay forma de diferenciar recuperación posible de configuración rota.

### Verificado en el dispositivo (2026-11-05)

`settings get secure accessibility_enabled` → `1`; `TradeDraw` en `Bound services`;
`Crashed services: {}`; la UI muestra "ACCESIBILIDAD: ACTIVA ✓". **En este dispositivo la
accesibilidad está operativa**, por lo que `ACCESSIBILITY_DOWN` de R26 no se reproduce aquí.

### Hallazgo colateral verificado (HyperOS)

`am force-stop com.example.tradedraw` **revoca** el enlace de accesibilidad y reescribe
`enabled_accessibility_services` sin TradeDraw. Documentado en Agent Nexus. No es un bug de la
app, pero invalida cualquier prueba que reinicie la app por force-stop.

---

## C. BALANCE_UNREADABLE

### Causas raíz (tres)

**C.1 · `lastBalanceReadMs` no se actualiza en la ruta pasiva (FALLO REAL)**

`onAccessibilityEvent` (`:446-449`) actualiza `latestObservedBalance` pero **no** `lastBalanceReadMs`:

```kotlin
if (finalBal != null && finalBal > 0.0) {
    latestObservedBalance = finalBal
    onBalanceUpdatedListener?.invoke(finalBal)
    // falta: lastBalanceReadMs = System.currentTimeMillis()
}
```

Solo la ruta activa `readCurrentBalance()` (`:607`) estampa la marca. Consecuencia: el balance se
lee por eventos (cada 5 s) pero el health lo considera eternamente obsoleto →
`BALANCE_NOT_FRESH` permanente aunque el dato sea correcto y recién leído.

**C.2 · El umbral de frescura duplica la cadencia de escaneo (FALLO REAL)**

- El escaneo pasivo está throttleado a **5000 ms** (`:441`).
- El health exige frescura de **≤ 5000 ms** (`HealthSupervisor.kt:112-113`).

Con cadencia == umbral, el chequeo falla de forma intermitente por construcción (a los 5000 ms
exactos deja de ser fresco). Además hay un **tercer umbral** distinto en
`TradingEngine.isBalanceFresh` (**3000 ms**, `TradingEngine.kt:925`). Tres números para lo mismo.

**C.3 · No hay distinción entre ausente, inválido, obsoleto y no disponible**

`balance <= 0.0` → `BALANCE_UNREADABLE`, sin separar:
- ausente (nunca se leyó),
- no disponible (accesibilidad caída, no hay fuente),
- obsoleto (se leyó, pero hace demasiado),
- inválido (lectura anómala descartada por el anti-glitch de `:602`).

### Convención de validación existente (no se cambia)

`readCurrentBalance()` descarta lecturas `< 500_000` si el saldo previo era `> 1_000_000`
(anti-glitch) y devuelve el valor previo. `balance <= 0.0` es "sin lectura". Se mantiene.

---

## D. HEALTH STATE MACHINE

### Flujo reconstruido (VERIFICADO)

```
Device State (modo, accesibilidad, feed WS)
        │
        ├─ AutoTradeAccessibilityService.instance  ─┐
        ├─ latestObservedAsset / Balance / lastBalanceReadMs
        │                                            │
        ├─ BinomoWebSocketClient (isConnected, isFeedFresh,       ├──► HealthSupervisor.evaluate()
        │    socketTicks, isPriceFrozen, activoSinFeed, activeAsset) │        │
        │                                            │        ▼
        └�� RiskManager (hasPendingTrade, cooldown) ─┘   Report(state, reasons, sinceMs)
                                                                 │
                                                                 ▼
                                                    TradeDrawHttpBridge GET /status → "health"
```

### Consumidor real

**Un solo consumidor: `TradeDrawHttpBridge.kt:619` → `GET /status`.**

Verificado con `grep -rn "healthSupervisor" app/src/main`: solo `OverlayService.kt:114`
(declaración) y `TradeDrawHttpBridge.kt:619` (lectura). **`HealthSupervisor` NO decide BUY/SELL
ni bloquea el motor**, tal como declara su propio encabezado (`:17`).

Esto acota el riesgo del parche: cambiar el health **no puede** habilitar una operación, porque
ningún camino de trading lo consulta.

### Estados y transiciones

| Estado | Condición |
| :--- | :--- |
| `BLOCKED` | ≥ 1 razón de bloqueo. Tiene precedencia absoluta |
| `DEGRADED` | 0 bloqueos y ≥ 1 degradación |
| `READY` | 0 bloqueos y 0 degradaciones |

Transiciones permitidas: cualquiera de los tres a cualquiera (`when` sin tabla). `sinceMs` se
congela mientras el estado no cambie (`:126-129`).

### Dependencias

| Señal | Bloquea | Degrada |
| :--- | :--- | :--- |
| Accesibilidad (`instance`) | `ACCESSIBILITY_DOWN` | — |
| WS conexión | `WS_DISCONNECTED` | — |
| Feed fresco | `FEED_STALE` | — |
| Ticks | `NO_TICKS` | — |
| Precio | `PRICE_FROZEN` | — |
| Activo sin feed | `ACTIVO_SIN_FEED` | — |
| Pantalla vs feed | `SCREEN_FEED_MISMATCH` | — |
| Balance | `BALANCE_UNREADABLE` | — |
| Orden pendiente | `ORDER_PENDING` | — |
| Payout | — | `PAYOUT_DISCREPANT` |
| Mercado | — | `MARKET_INOPERABLE` |
| Cooldown | — | `COOLDOWN_ACTIVE` |
| Balance obsoleto | — | `BALANCE_NOT_FRESH` |

### Falsos positivos identificados

1. **`SCREEN_FEED_MISMATCH` en modo WebSocket** (sección A) — estructural, siempre presente.
2. **`BALANCE_NOT_FRESH` permanente** (sección C.1) — la marca nunca se actualiza en la ruta pasiva.
3. **`BALANCE_NOT_FRESH` intermitente por umbral == cadencia** (sección C.2).
4. **`ACCESSIBILITY_DOWN` espurio entre `onUnbind` y `onRebind`** (sección B.1).

### Defecto de la suite de tests existente

`HealthSupervisorTest` no puede ejercitar `READY` ni `DEGRADED`: sin `wsClient` real (que exige
`Context`) siempre hay `WS_DISCONNECTED` + `ACCESSIBILITY_DOWN`, así que **todo** da `BLOCKED`.
El test llamado `reportaDegradedCuandoHayCooldownActivoOSaldoNoFresco` **afirma `BLOCKED`**
(`:79`) — no prueba lo que su nombre dice. Son test**s de humo**, no de la máquina de estados.

---

## Alcance del parche propuesto

| Archivo | Cambio | Justificación |
| :--- | :--- | :--- |
| `AutoTradeAccessibilityService.kt` | estampar `lastBalanceReadMs` en la ruta pasiva | C.1, fallo real |
| `health/HealthSupervisor.kt` | conmutar chequeos por `dataSource`; separar `BALANCE_STALE`; ventana de frescura con margen | A, C.2, C.3, D |
| `health/HealthSupervisorTest.kt` | ampliar: cubrir la matriz completa | D (suite actual no prueba READY/DEGRADED) |
| `app/src/test/.../BalanceFreshnessStampTest.kt` | prueba de la regla de frescura | C.1/C.2 |

### Lo que NO se toca

- `TradingEngine.motivoVetoPorDesajusteDeActivo` — sigue vetando en el motor.
- `RiskManager` — sin cambios.
- `TradingEngine.isBalanceFresh` (3000 ms) — su propósito es distinto: precisión del TIE, no
  diagnóstico. Se documenta la diferencia en vez de unificar a la fuerza.
- `ScreenCaptureManager.kt` — queda como está (código muerto declarado, decisión de producto).
- Cualquier umbral, estrategia, ventana operativa o guard de seguridad.
