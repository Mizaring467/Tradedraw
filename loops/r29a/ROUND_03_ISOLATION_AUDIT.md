# ROUND 03 — Auditoría de aislamiento · TradeDraw R29-A

**Fecha:** 2026-11-09T07:38–08:10Z · **Ejecutado por:** DeepInvestigator (subagente de aislamiento, sesión Antigravity visible)
**Alcance:** solo lectura. Única escritura: este archivo. Sin ejecutar app/Gradle/ADB, sin AUTONOMOUS, sin broker, sin commits, sin secretos impresos. `git status --porcelain` = 22 entradas idénticas al inicio y al cierre de la ronda (sin edición concurrente) [VERIFICADO].
**Método:** lectura directa + `grep -rn` con conteos reproducibles + re-agregado de XML de tests con Python + verificación negativa (grep de ausencias). Todas las cifras heredadas de R1/R2 recalculadas antes de reetiquetarse (D-5).
**Insumos:** R1 (`ROUND_01_GIT_AUDIT.md`), R2 (`ROUND_02_COMPONENT_INVENTORY.md`), R27-H L1 (`ActiveInstance`), R28-B (bloqueo de aislamiento). No se repite el inventario de R2; este informe asume sus rutas.

---

## 1. Matriz de aislamiento por dominio

Convención: **REAL** = existe frontera estructural (paquete/interfaz/inyección) que permite sustituir o probar el dominio de forma independiente. **NOMINAL** = el dominio existe como nombre/clase pero comparte proceso, estado estático y llamadas directas con los demás; extraerlo exige editar a los vecinos.

| Dominio | Componentes | Separación | Evidencia (ruta:línea) |
| :--- | :--- | :--- | :--- |
| **Android UI** | `MainActivity`, `BinomoAuthActivity`, `AgentChatOverlay`, `ModelPickerDialog`, vistas HUD (`CustomDrawingView`, `ColorWheelView`, `AutoDrawEngine`, layouts) | **NOMINAL** — sin paquete `ui/` (47 clases en un paquete plano + `health/`); la UI construye el grafo entero y escribe prefs que consume Market Data. `AgentChatOverlay` recibe `TradingEngine` por constructor y lo muta (`strategy`, `setTradingMode`, `riskManager.correctStats`) | `AgentChatOverlay.kt:46-49` (ctor `tradingEngine: TradingEngine`); `:365-371` (muta estrategia); `OverlayService.kt:285-366` (construye RiskManager+Engine+WS+Bridge+Health); `BinomoAuthActivity.kt:108-111` (UI escribe `ws_endpoint_url` que lee el WS); grep: sin subpaquete `ui/` salvo `ui/theme` Compose |
| **AccessibilityService** (ATAS) | `AutoTradeAccessibilityService` (930 LOC) | **NOMINAL** — clase Android real (frontera del SO), pero su `companion object` es el bus de estado global del sistema: 11 archivos consumen sus estáticos (26 refs a `.instance`, 14 a `latestObservedBalance`, 10 a `latestObservedAsset`, 4 a `isDemoAccount`, 4 a `isSyntheticOrOTC`, 4 a `observedPayout`…). Es a la vez sensor (lee UI del broker), actuador (taps) y almacén de estado (demo/balance/activo/payout): tres roles sin separar | `AutoTradeAccessibilityService.kt:17-120` (companion con estado); conteo grep `AutoTradeAccessibilityService\.` externo: 87 referencias en 11 archivos; `RiskManager.kt:724-725` y `TradingEngine.kt:2550` consumen sus estáticos directamente |
| **TradingEngine** | `TradingEngine.kt` (2.970 LOC) | **NOMINAL** — clase inyectable en teoría (ctor con 5 dependencias, `TradingEngine.kt:40-46`), pero dentro crea 6 subcomponentes con `new` (ChartViewportController :47, AutonomousAgentController :48, VisionAnalyzer :203, SyntheticCandleEngine, AdaptiveLearningEngine, MarketInoperabilityDetector), lee estáticos de ATAS (`:2550`, `:2183`, `:2675-2678`) y de OverlayService (`:2049` `OverlayService.instance?.showManualFallbackAlert`), y dibuja en la vista HUD (`drawingView.triggerClickAnimation` `:2030`, `:2564`). Dependencia circular Engine ↔ OverlayService (estático `instance`). Estrategia + vetos + ejecución + liquidación + journals viven en ESTA clase | `TradingEngine.kt:40-48`, `:203`, `:2550`, `:2675-2681`, `:2049`; ciclo confirmado en `OverlayService.kt:297` (construcción) vs `TradingEngine.kt:2049` (consumo inverso del servicio) |
| **Strategy Engine** | `evaluateStrategySignalWithReason` (TradingEngine `:1061-1553`), `SyntheticCandleEngine` (1.540 LOC), `AdaptiveLearningEngine` (575), `MarketRegimeClassifier` (object) | **NOMINAL → PARCIALMENTE REAL** — la lógica de 14 estrategias es un `when` de ~490 líneas DENTRO de TradingEngine (no hay clase de estrategia ni interfaz). El enum vive en `AutoDrawEngine.kt:4-22` (¡una clase de dibujo!). Lo único realmente separable es `SyntheticCandleEngine` (velas/RSI/filtros de tick): se instancia sola en el replay JVM sin Android (`ReplayBacktestTest.kt:269-271`) | `TradingEngine.kt:1061-1553` (when monolítico); `AutoDrawEngine.kt:4-22` (enum en clase de dibujo); `TradingEngine.kt:196-200` (setter propaga a `syntheticCandleEngine.currentStrategy`); `ReplayBacktestTest.kt:268-271` (uso autónomo del motor de velas) |
| **Risk Engine** | `RiskManager.kt` (1.341 LOC) | **NOMINAL** — clase propia, instanciable sin Android (`RiskManager(null)` en `RiskManagerTest.kt:13`), pero en producción depende de estáticos de ATAS (balance en 5 puntos `:724/:934/:1108/:1144/:1331`, `isDemoAccount :725`, `observedOrderAmount :741`, `classifyAsset :875`) y su configuración la muta la UI vía setters públicos (`OverlayService.kt:1079` `riskManager.martingaleEnabled = …`). La política y la percepción están acopladas por estáticos, no por datos inyectados | `RiskManager.kt:724-725`, `:741`, `:875`; `OverlayService.kt:1079-1080`; `RiskManagerTest.kt:13` (vía de aislamiento solo para tests) |
| **Market Data** | `BinomoWebSocketClient` (869 LOC), `TickFrameParser`, `PayoutReader`, `MarketTickFilters` | **PARCIALMENTE REAL** — clase propia con wiring por listeners en OverlayService (`onTickListener` → `engine.onMarketTick`, `:341-356`); proveedores de frescura inyectados como lambdas (`feedFreshnessProvider`, `priceFrozenProvider`, `priceRangeRatioProvider`, `binomoWebSocketClientRef`). PERO: token/endpoint vienen de prefs escritas por la UI (`ws_auth_token` `BinomoWebSocketClient.kt:205`; `ws_endpoint_url` `:25` ← escrito por `BinomoAuthActivity.kt:110`), escribe en `TradeEventLog`, y referencia `OverlayService.instance` (HUD). Es el ÚNICO dominio con un punto de sustitución natural (la URL por prefs) | `OverlayService.kt:339-366` (wiring completo); `BinomoWebSocketClient.kt:22-26`, `:205-215`; `BinomoAuthActivity.kt:108-111`; `TradingEngine.kt:355` (`FEED_MAX_AGE_MS = BinomoWebSocketClient.DEFAULT_FRESHNESS_MS`, acoplamiento a constante) |
| **Ejecución** | `ATAS.performClick` (`:190`), `ATAS.executeRootTap` (`:291`), `ATAS.performClickAt` (`:523`), shell root (`su -c input tap` `:323`), bridge `/trade`/`/click`/`/swipe` | **NO EXISTE** — 0 interfaces en todo `app/src/main/java` (grep `interface ` → 0 coincidencias); no hay `OrderGateway`/`Broker`/`Executor` (re-verificado negativamente). Tres primitivas de tap + shell root viven como estáticos/métodos de ATAS, llamados desde 4 fuentes (engine `:2550`/`:2013`, bridge `:152`/`:416`, broadcasts `ATAS:397`, HUD-TEST `OverlayService:209/:214`). El guard demo está duplicado 3× dentro de ATAS (`:200/:302/:532`) y 1× en el engine (`:2183`) con alcances distintos | grep `interface ` main = 0; `AutoTradeAccessibilityService.kt:190/:291/:523/:323/:270`; `TradeDrawHttpBridge.kt:141-159/:410-421`; `TradingEngine.kt:2550/:2013` |
| **Simulación** | Modo `SHADOW` (TradingEngine `:2493-2520`), `shadow_journal.csv` | **NOMINAL** — SHADOW es una rama de 28 líneas dentro de `executeHeadlessTrade` que registra y hace `return`; NO simula el ciclo de órdenes (ver §2.1). No existe simulador de colocación/confirmación/liquidación (confirma bloqueo R28-B) | `TradingEngine.kt:2493-2520`; ausencia de simulador: grep `gateway|executor|OrderSimulator` → 0 relevantes |
| **Backtesting** | `ReplayBacktestTest` (633 LOC, JVM), `ReplayLatencyModeTest`, `LiveReplayParityTraceabilityTest`, `validation_engine/` (7 módulos Python), `scripts/backtest/*.py` | **PARCIALMENTE REAL** — vive fuera de la app (test + scripts), reutiliza SOLO funciones puras del motor (`calcularPoderesDeMercado` `TradingEngine.kt:2903`, `motivoVetoPorAgotamientoDeCompra` `:2961`, `motivoBloqueoPorAntiPatron`/`mejorCoincidenciaEnMemoria` `AdaptiveLearningEngine.kt:540/:561`, `generateDeterministicDecisionId` `:918-920`) y re-implementa en el propio test la orquestación (ventanas, cooldowns, 1-orden-por-vela). NO ejecuta `TradingEngine` (grep `TradingEngine(` en test → 0; solo funciones estáticas) | `ReplayBacktestTest.kt:268-336` (orquestación duplicada), `:300-308` (funciones puras), `:466` (id determinista); grep negativo de instanciación |
| **Persistencia** | 7 almacenes `SharedPreferences`, `TradeJournalLogger` (object), `TradeEventLog` (object), `ModeEventJournal`, `adaptive_learning_state.json` | **NOMINAL** — sin Room/DataStore/SQLite/EncryptedSharedPreferences (grep → 0). Cada almacén tiene 1-3 escritores; `TradeDraw_WSConfig` cruza capas (UI escribe token/URL, Market Data lee). Los journals son objetos estáticos globales usados por 6 capas (`TradeEventLog.add` en ATAS, WS, Overlay, RiskManager, Engine, Bridge — 32 llamadas). Token de sesión en claro + `allowBackup=true` (heredado de R2, re-confirmado) | `getSharedPreferences` grep (§2.7 de R2, re-verificado); `TradeEventLog.add`: 32 llamadas en 6 archivos; `TradeJournalLogger.kt:104-112` (doble raíz interna/externa) |

**Cifras del acoplamiento estático (recalculadas, VERIFICADO):**
- `AutoTradeAccessibilityService.*` consumido por **11 archivos** (coincide con R2): ActiveInstance (solo doc), AgentChatOverlay, AssetRicMapper (solo comentario), AutonomousAgentController, ChartViewportController, MainActivity, OverlayService, RiskManager, TradeDrawHttpBridge, TradingEngine, health/HealthSupervisor. Detalle: `.instance` ×26, `.latestObservedBalance` ×14, `.latestObservedAsset` ×10, `.observedPayout` ×6, `.observedOrderAmount` ×4, `.isSyntheticOrOTC` ×4, `.isDemoAccount` ×4, `.performClick` ×3, `.lastBalanceReadMs` ×2, `.classifyAsset` ×2, otros ×9.
- `OverlayService.instance` consumido por **9 archivos** (R2 decía 14 para "OverlayService": correcto a referencias totales, el fan-in de archivos es 9) — 39 referencias.
- **0 interfaces** en `app/src/main/java` (todo el código de producción) [VERIFICADO — `grep -rn "interface " app/src/main/java/` → sin coincidencias].
- Ciclos estáticos: TradingEngine ↔ OverlayService (`instance` en ambos sentidos), TradingEngine/OverlayService/RiskManager/Bridge → ATAS (estáticos) y ATAS → OverlayService (`instance`, `updateHUDView`).

---

## 2. Investigaciones obligatorias

### 2.1 — Investigación 1: ¿Aislamiento simulación ↔ ejecución? — **NO EXISTE COMO MÓDULO; SHADOW ES SOLO REGISTRO** [VERIFICADO]

**(a) ¿Qué hace exactamente SHADOW?** Rama `if (mode == AutoTradeMode.SHADOW)` en `executeHeadlessTrade` (`TradingEngine.kt:2493-2520`). Código completo de la rama:

```kotlin
if (mode == AutoTradeMode.SHADOW) {
    shadowOrdersGenerated++                                          // :2494
    val currentPrice = latestMarketTick?.price ?: 0.0
    Log.i("TradingEngine", "👻 [SHADOW MODE] Orden simulada autorizada: …")   // :2496
    TradeEventLog.add(TradeEventLog.Kind.SHADOW_DECISION, …)         // :2497-2502
    TradeJournalLogger.logShadowTrade(…, reason = "SHADOW_AUTORIZADA: pasó todos los vetos")  // :2503-2514
    synchronized(this) { lastExecutedCandleEpochMinute = currentCandleEpochMinute }  // :2515-2517
    return                                                           // :2520
}
```

**Conclusión: SHADOW simula SOLO la decisión, NO el ciclo de órdenes.** No hay colocación, ni confirmación, ni liquidación, ni slippage, ni resultado WIN/LOSS, ni saldo simulado. El `return` de `:2520` precede a todo el bloque de ejecución real: `recordTradeSent` (`:2529`), `recordTradeOpened` (`~:2544`), tap (`:2550`), animación (`:2564`). El R28-B queda re-confirmado línea a línea sobre el código actual.

**(b) ¿Dónde se decide el modo?** Punto único de despacho de señales: el callback `syntheticCandleEngine.onSignalGenerated` (`TradingEngine.kt:275-289`): `AUTONOMOUS || SHADOW → executeHeadlessTrade` (`:275-277`), `SEMIAUTOMATIC → señal pasiva sin tap` (`:277-287`). El discriminador final es `mode` leído dentro de `executeHeadlessTrade` (`:2183` guard demo solo para AUTONOMOUS; `:2493` rama SHADOW). El modo se fija vía `setTradingMode` (`:96-117`, setter privado del campo, con journal de origen).

**(c) ¿Puede SHADOW alcanzar el tap?** **NO por esta ruta.** El `return` de `:2520` es anterior a toda primitiva de tap y no existe ningún otro punto donde `mode == SHADOW` habilite despacho. [VERIFICADO — los 9 chequeos de modo en el archivo: `:219, :275, :277, :1723, :1732, :1929, :1945, :2183, :2493`; ninguno despacha en SHADOW.]

**PERO:** la vía `/trade` del bridge ignora el modo por completo (no consulta `tradingEngine.mode`), así que un `/trade` HTTP durante SHADOW despacha un tap real (§2.4). SHADOW no alcanza el tap *por decisión del motor*; el motor no es el único puerto de ejecución.

**(d) ¿Estadísticas separadas?** **SÍ, por construcción — con UNA fuga de estado compartido:**
- Contador dedicado `shadowOrdersGenerated` (`TradingEngine.kt:416-418`), expuesto como `shadowOrders` en `GET /stats` (`TradeDrawHttpBridge.kt:686`), separado de `totalWins/totalLosses` de RiskManager.
- Journal separado: `logShadowTrade` escribe `shadow_journal.csv` con header propio de 11 columnas (`TradeJournalLogger.kt:227-228`: `timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason`), sin stake/saldo/resultado; el journal real (`logTrade`, `:128-175`) escribe el CSV de trades con 29 columnas (`:22`). Evento `SHADOW_DECISION` con `decisionId` y `tradeId = null` (contrato fijado por `ShadowModeTest.kt:48-62`: "SHADOW nunca debe generar trade_id").
- `RiskManager` intacto: `shadowModeNoContaminaRiskManager` (`ShadowModeTest.kt:96-112`) verifica `orderState == NINGUNA`, `hasPendingTrade == false`, W/L en 0. Coherente: la rama SHADOW hace `return` antes de `recordTradeSent`.
- **Fuga encontrada (hallazgo nuevo R3):** SHADOW **sí muta `lastExecutedCandleEpochMinute`** (`:2515-2517`, "1 orden por vela máx" `:2299-2306`). Una decisión shadow CONSUME la vela para la ejecución real: si el motor pasa de SHADOW a AUTONOMOUS en la misma vela, el trade real queda vetado por `VELA_YA_OPERADA`. Es estado de rate-limit de EJECUCIÓN compartido con la SIMULACIÓN — frontera no limpia. Etiqueta: VERIFICADO (código), impacto HIPÓTESIS (no medido en dispositivo).

### 2.2 — Investigación 2: Cuenta demo ≠ sandbox — **"DEMO" ES UNA ETIQUETA LEÍDA DEL BROKER, NO UN ENTORNO** [VERIFICADO]

**Cadena completa de qué alimenta `isDemoAccount`:**

1. **Default:** `var isDemoAccount: Boolean = true` (`ATAS:65`) — si NADA se lee, el sistema ASUME demo.
2. **Única fuente de escritura:** heurística de TEXTO del árbol de accesibilidad, en dos lugares:
   - `traverseScreenContext` (`ATAS:701-707`): `lower.contains("cuenta real") || lower == "real"` → `false`; `lower.contains("cuenta demo") || lower == "demo"` → `true`.
   - `findBalanceInNode` (`ATAS:841-847`): idéntica heurística sobre el mismo recorrido.
   Solo se ejecutan en eventos `typeWindowStateChanged` (único tipo suscrito, `accessibility_service_config.xml:3`) con throttle de 5 s (`ATAS:468-472`).
3. **El feed es REAL siempre:** `wss://ws.binomo.com/` default (`BinomoWebSocketClient.kt:25`), autenticado con el token de sesión real de 36 caracteres capturado por el WebView de login (`ws_auth_token`, `BinomoWebSocketClient.kt:205`, escrito por `BinomoAuthActivity.kt:385-386`). El protocolo WS no transporta ningún flag demo/real que el código lea (grep `demo|practice` en BinomoWebSocketClient → 0). Los taps golpean la app real (`com.marketly.trading`) con coordenadas calibradas (`CalibrationManager`).
4. **Segunda fuente de verdad:** `RiskManager.kt:724-725`: `val isDemo = currentBal >= 1_000_000.0 || (AutoTradeAccessibilityService.isDemoAccount && currentBal > 500_000.0)` — el SALDO también decide la rama de política (real: suelo 4.000 COP y control de stake; demo: `absoluteEquityFloor` default **20.000.000 COP**, `RiskManager.kt:129-131`). Dualidad reconocida por el propio código (`TradingEngine.kt:2172-2177`: "Dos fuentes de verdad para el mismo criterio divergen con el tiempo").

**Guards fail-closed y su alcance real:**
- Engine (solo `mode == AUTONOMOUS`): `:2183-2196` + `puedeOperarAutonomoEnDemo(esDemo) = esDemo` (`:869`, función pura fail-closed fijada por test `AutonomousSafetyAndAssetScaleTest.kt:126-127`). Veto → `DISABLED` + evento `SEGURIDAD_NO_DEMO`.
- Tap-level (las 3 primitivas): `puedeDespacharTap(isDemoAccount) = isDemoAccount` (`ATAS:78`) en `performClick` `:200`, `executeRootTap` `:302`, `performClickAt` `:532`.

**¿Qué pasa con una cuenta REAL si la heurística falla?** Trazado exacto:
- Si la UI del broker no muestra nunca un nodo con texto "cuenta real"/"real" (idioma no español — el código SOLO matchea español y la palabra exacta `real`—, label no renderizado, o accesibilidad caída), `isDemoAccount` conserva su valor anterior o el default `true`.
- Con `isDemoAccount == true` sobre una cuenta real: (1) los 4 guards de tap PASAN (fail-open de facto); (2) el guard del engine pasa; (3) `RiskManager` toma la rama demo: `absoluteEquityFloor` default 20M COP → una cuenta real con saldo < 20M COP queda bloqueada por `Stop Equity Absoluto` (`RiskManager.kt:727-730`) — protección accidental por umbral alto; una cuenta real con saldo ≥ 20M COP operaría con política demo completa (martingala demo, suelos de sesión 100K vs 8K, `:772`).
- La vía `/trade`/`/click` del bridge no tiene NINGUNA de las capas del engine: solo el guard de tap (`puedeDespacharTap`), que depende de la misma heurística fallida.
**Etiqueta: PARCIALMENTE VERIFICADO** — la cadena de código es VERIFICADA; el escenario de fallo es análisis de código (no hay registro de un fallo real de heurística; no se ejecutó nada).

### 2.3 — Investigación 3: `dispatchGesture()` — frontera no abstraíble tal como está

**Todas las llamadas (grep completo sobre main):**
| # | Llamada | Línea | Ruta que la alcanza |
| :-- | :--- | :--- | :--- |
| 1 | `dispatchGesture(gesture, GestureResultCallback, null)` | `ATAS:576` (dentro de `dispatchClickGesture` `:548`) | `performClickAt` (`:523`) ← engine fallback `:232`, bridge `/trade` `:152`, broadcast TAP `:397`, TEST/TEST_SELL `OverlayService:209/:214`, `testAccessibilityClicks` `TradingEngine:2102/:2106` |
| 2 | `dispatchGesture(gesture, GestureResultCallback, null)` | `ATAS:621` (swipe) | `performSwipe` ← bridge `/swipe`, broadcast SWIPE |

**Por qué NO es abstraíble tal como está el código:**
1. **Es un método de instancia de `AccessibilityService`** (API del SO): solo existe mientras el sistema tiene el servicio enlazado; la referencia viaja por el estático `instance` (`ATAS:37-39`), no por inyección. Abstraer exige introducir una interfaz + proveedor de la instancia, y tocar los 26 puntos que leen `instance`.
2. **La vía primaria ni siquiera es `dispatchGesture`:** `performClick` (`ATAS:190-243`) documenta que "el root tap es la vía PRIMARIA y la accesibilidad el fallback" (`:211-216`) porque Binomo/MIUI filtra los gestos sintéticos. Con root: `hasRootAccess()` (`:246-260`, cacheado vía `su -c id` `:270`) → `executeRootTap` (`:291`) → `Runtime.exec(arrayOf("su","-c","input tap x y"))` (`:323`) — un PROCESO de shell, no un gesto. Con instancia nula: `executeRootTap` + `autoHealAccessibility()` (`:238-243`), que re-registra el servicio por root con toggle real de `settings put secure` (`ATAS:379-390`). **Una abstracción que solo envuelva `dispatchGesture` quedaría incompleta** (coherente con D-6).
3. **El estado que la rodea es global:** el callback de resultado toca `OverlayService.instance` (`restoreHudTouchable` `:360`), estampa timestamps estáticos de latencia (`:281-284`), y el guard previo lee `isDemoAccount` estático. Extraer el gesto sin extraer ese estado no cambia el acoplamiento.
4. **Cuatro fuentes de petición de tap convergen sin tipo común:** engine (`:2550`/`:2013`), bridge HTTP (`:152`, `:416`), broadcasts firmados (`ATAS:393-400`), UI de prueba (`OverlayService:209/:214`). Ninguna comparte una firma de "orden": el engine pasa `decisionId/tradeId`; el bridge `/trade` no pasa nada.

**Relación con la ruta root-tap (PRIMARIA):** `performClick` → guard demo (`:200`) → `if (hasRootAccess())` (`:215`) → root-tap con bypass temporal del HUD (`:220-227`, `temporarilyBypassHUD(350ms)`) → `finally { restoreHudTouchable() }` (`:353-356`). Con accesibilidad caída sigue operativa (`:238-243` + `autoHealAccessibility`). El timeout de 300 ms (`TradingEngine.kt:2047` fallback manual) aplica solo a la ruta de gesto; la ruta root no lo usa. [VERIFICADO — lectura `ATAS:190-398`.]

### 2.4 — Investigación 4 (E1 de R2): `/trade` — vía de orden sin cascada

**Trazado `/trade` (bridge `TradeDrawHttpBridge.kt:141-159`):**
```
GET /trade?action=BUY|SELL
  → coords desde CalibrationManager (o defaults 200/600, 600/600)      :146-151
  → access = AutoTradeAccessibilityService.instance                     :145
  → access.performClickAt(x, y)                                          :152
      → guard: puedeDespacharTap(isDemoAccount)                          ATAS:532  (ÚNICO veto)
      → bypass HUD si el punto cae dentro                                ATAS:538-542
      → dispatchClickGesture → dispatchGesture                           ATAS:548→576
```
**Vetos que NO se evalúan (comparado con `executeHeadlessTrade`):** guard demo de engine (`:2183`, además limitado a AUTONOMOUS), feed rancio (`:2198-2222`), precio congelado V2 (`:2224-2260`), congelación WS (`:2263-2275`), desajuste activo pantalla↔feed (`:2285-2296`), vela ya operada (`:2298-2306`), veto francotirador (`:2309-2317`), ventana timing (`:2330-2336`), anti-choppy (`:2337-2344`), cooldown (`:2346-2360`), agotamiento de compra (`:2375-2384`), bloqueo adaptativo (`:2387-2405`), `riskManager.canExecuteTrade` (`:2424-2441`), conflicto S/R↔Plan V1 (`:2449-2464`). **Cero vetos de estrategia/feed/riesgo.** Tampoco consulta `mode`: funciona igual en DISABLED, SEMIAUTOMATIC o SHADOW (hallazgo: SHADOW no protege esta puerta).

**¿Con cero accesibilidad? `instance == null` → HTTP 503** (`:153-156` "Servicio de Accesibilidad inactivo"). `/trade` **NO** usa root como fallback: requiere el servicio vivo. PERO el endpoint hermano **`/click` (`:410-421`) llama `AutoTradeAccessibilityService.performClick` (estático)**, que sí ejecuta **root-tap primario sin instancia** (`ATAS:230-243`) y **auto-heal** (`:242`). Es decir: la pareja `/trade`+`/click` del bridge constituye una vía de orden completa **con cero vetos de estrategia/feed/riesgo, sin confirmación humana y sin requerir el servicio de accesibilidad** (vía root). El único veto aguas abajo de ambas es `puedeDespacharTap(isDemoAccount)` — la heurística de §2.2.

**¿Usan los botones CALL/PUT del HUD la misma vía?** Sí — `performClickAt` (ruta de gesto, guard demo únicamente). Llamadas reales localizadas (la cita "ATAS:529" de R2 es el COMENTARIO que lo declara, `ATAS:523-535`): (a) broadcast `CMD action=TEST/TEST_SELL` → `OverlayService.kt:209/:214`; (b) submenú IA "TEST CLIC" → `tradingEngine.testAccessibilityClicks()` (`OverlayService.kt:667-670`) → `performClickAt(buyX,buyY)` y `performClickAt(sellX,sellY)` (`TradingEngine.kt:2102/:2106`); (c) bridge `/trade` (`:152`); (d) receptor TAP firmado (`ATAS:397`). **No existe un par de botones CALL/PUT dedicados a operar en el HUD**: la "alerta de fallback manual" (`layout_trading_hud.xml:235-257`) es solo texto (dos `TextView`, sin click listener — `OverlayService.kt:950-968` solo gestiona visibilidad). Etiqueta: VERIFICADO con corrección menor a la descripción de R2 (los "botones CALL/PUT" son acciones de prueba/endpoint, no controles de trading del HUD).

### 2.5 — Investigación 5 (E2 de R2): ¿`TREND_FOLLOWING` y `COMBINED` son seleccionables? — **SÍ, por 5 vías** [VERIFICADO]

| Vía | Evidencia | ¿Alcanza TREND_FOLLOWING? | ¿Alcanza COMBINED? |
| :-- | :--- | :--- | :--- |
| Bridge `/strategy?name=X` | `TradeDrawHttpBridge.kt:159-167`: `AutoTradeStrategy.valueOf(stratName)` — acepta CUALQUIER nombre del enum | Sí (`name=TREND_FOLLOWING`) | Sí (`name=COMBINED`) |
| Diálogo HUD "Seleccionar Estrategia" | `OverlayService.kt:995-1031`: índice 9 → `TREND_FOLLOWING`, `else → COMBINED`; lista visible "10. Seguidor de Tendencia", "11. Doble Confirmación" (`:1000-1001`) | Sí (opción 10) | Sí (opción 11 y default del `else`) |
| Broadcast `CMD action=STRATEGY&name=X` | `OverlayService.kt:236-245`: `valueOf(stratName)` | Sí | Sí |
| Chat del agente | `AgentChatOverlay.kt:369-371`: comando `STRAT_TREND` → `strategy = TREND_FOLLOWING` | Sí | No (no hay comando para COMBINED) |
| Persistencia | `saved_strategy` en `TradeDraw_Config` (`OverlayService.kt:313-320` lee; `:1018-1021` escribe desde el diálogo) | Sí (sobrevive recreación del servicio) | Sí |

**Ramas implementadas (no son código muerto):** `TREND_FOLLOWING` → `TradingEngine.kt:1430-1449` (continuación con veto de extremo de rango) y `:1824` (etiqueta de dibujo); `COMBINED` → `:1451-1460` (confirmación S/R) y `:1827`; gating de indicadores en `SyntheticCandleEngine.kt:1147/1156` (`allowAll` para COMBINED, `allowTrend` para TREND_FOLLOWING). Etiqueta HUD: "Tendencia"/"Combinada" (`OverlayService.kt:1769-1770`). **Respuesta a R2: seleccionables de forma directa; la ruta AUTO_ADAPTIVE es solo una de las formas de activar su lógica** (en AUTO_ADAPTIVE el `when` de estrategias se recorre vía sub-evaluaciones, `TradingEngine.kt:1480` usa `TREND_FOLLOWING` como condición de continuación).

### 2.6 — Investigación 6: Insumos del guard alterados por el working tree (heredado de R1) — impactos sobre `isBalanceFresh`

**El guard:** `isBalanceFresh(instanceAvailable, lastReadMs, nowMs) = instanceAvailable && nowMs - lastReadMs < 3000L` (`TradingEngine.kt:925-926`, función pura fijada por `BalanceFreshnessSeparationTest.kt:53-64`). **Único consumidor en producción:** la liquidación/atribución de empates, `checkHeadlessTradeResolution` (`:2675-2679`): `saldoFresco` decide si un TIE se journala como `TIE` (void contable, `recordTieVoidIfFresh` `:928-930`) o `TIE_INCIERTO` (sin void, `:922-924`). **NO se consulta antes de colocar órdenes** — la colocación usa `RiskManager.canExecuteTrade` (sin frescura de balance de 3 s; su umbral de lectura es otro). Este punto ya lo separó R27-H L3 con test.

**Cambio C.1 (working tree, `ATAS:481-486`):** la ruta pasiva (`onAccessibilityEvent`, eventos de cambio de ventana con throttle 5 s, `:468-472`) ahora estampa `lastBalanceReadMs = System.currentTimeMillis()` cuando lee saldo; antes no lo hacía y la frescura solo podían darla las lecturas activas (`readCurrentBalance()` estampa en `:651`).

**Escenario unbind→rebind (con el cambio L1, `ATAS:496-505` + `ActiveInstance.kt`):**
- Durante la ventana unbind→rebind, `instance == null` (detach atómico `ActiveInstance.release`, `ActiveInstance.kt:73-86`) → `isBalanceFresh(false, …) = false` SIEMPRE, aunque `lastBalanceReadMs` sea reciente (el timestamp NO se resetea en detach; conserva el último valor). Antes de R27-H: `instance` sobrevivía al unbind apuntando a un servicio desenlazado → `instanceAvailable == true` → la frescura dependía solo del reloj.
- **Impacto neto en liquidación:** en ventanas de unbind, un empate se clasifica `TIE_INCIERTO` (sin void) en vez de `TIE` (void). Cambio conservador en contabilidad (no cuenta voids sin evidencia fresca), pero CAMBIA el resultado del journal respecto al comportamiento pre-R27-H en ese escenario. `LiquidationIdempotencyTest`/`JournalLatencySchemaTest` no cubren la combinación unbind+TIE (gap, ver §5).
- **Rebind:** `onRebind → attach` (`ATAS:455-458`) restaura `instance`; la frescura vuelve a depender del último estampado (pasivo o activo).

**Escenario frescura con datos pasivos:** el estampado pasivo está limitado por (a) el throttle de 5 s y (b) la cadencia real de eventos `typeWindowStateChanged` — que el broker solo emite al cambiar de ventana/pantalla, NO periódicamente. Entre eventos, `lastBalanceReadMs` envejece sin más. En la práctica de una orden abierta, la frescura al liquidar depende de que algo haya leído saldo en los últimos 3 s: la propia cadena de confirmación lee activamente (`checkOrderConfirmation` → `readCurrentBalance()` `:2597-2598` → estampa `:651`), y el loop de resolución también (`:2680-2681`), pero el `saldoFresco` de `:2675-2679` se calcula ANTES de esa lectura, con el estampado PREVIO. Con datos solo pasivos (sin lecturas activas previas), lo habitual será NO-fresco → `TIE_INCIERTO`; con una confirmación activa reciente, fresco → `TIE`. **Conclusión:** el working tree amplía la población de casos que alcanzan `TIE` (antes: prácticamente ninguno sin lectura activa; ahora: cualquiera con evento pasivo ≤3 s previo). Es exactamente el efecto buscado por R27 (eliminar `BALANCE_NOT_FRESH` espurio), pero aplicado al insumo del guard de liquidación: **el working tree cambia la distribución TIE vs TIE_INCIERTO observable en el journal.** Etiqueta: VERIFICADO a nivel de código; la distribución real requiere ejecución (NO VERIFICADO en dispositivo). SOLO documentado, nada modificado (regla 15).

### 2.7 — Investigación 7: `SharedPreferences` compartidos — ¿frontera o bus de acoplamiento?

**Mapa real escritor↔lector (grep `getSharedPreferences` + setters, VERIFICADO):**

| Almacén | Escritores (capa) | Lectores (capa) | Veredicto |
| :--- | :--- | :--- | :--- |
| `TradeDraw_WSConfig` (6 usos) | **UI** (`BinomoAuthActivity:109/:125/:357/:385` — token, URL, cookie, deviceId), **Market Data** (`BinomoWebSocketClient:205-215` setters) | `BinomoWebSocketClient` (`:22`), `MainActivity:103` (estado del botón), `BinomoAuthActivity` | **BUS DE ACOPLAMIENTO UI↔MarketData**: la UI escribe el endpoint/token que el WS consume; sin interfaz ni contrato, la clave es el contrato (`ws_auth_token`, `ws_endpoint_url`) |
| `TradeDraw_RiskConfig` (1 uso) | Solo `RiskManager` (`:17`, 17 claves vía setters `:85-185`) | Solo `RiskManager` | **Frontera encapsulada** (un solo propietario), pero los setters son públicos y la UI los invoca (`OverlayService:1079` martingala; `showRiskConfigDialog`) — el acoplamiento vive en los setters, no en el archivo |
| `TradeDraw_HUDConfig` (4) | Solo `OverlayService` (`:67/:1158/:1216/:1601`) | Solo `OverlayService` | Frontera local a UI |
| `TradeDraw_Config` (2) | Solo `OverlayService` (`:1018-1021`, `saved_strategy`) | Solo `OverlayService` (`:313-315`) | Frontera local; **inconsistencia menor**: la estrategia fijada por `/strategy` o chat NO se persiste (solo la del diálogo) |
| `TradeDraw_Calibration` (1) | Solo `CalibrationManager` (`:49`) | Solo `CalibrationManager` | Frontera encapsulada |
| `TradeDraw_AIConfig` (1) | Solo `AIClient` (`:29`) | Solo `AIClient` | Frontera encapsulada |
| `TradeDraw_Templates` (1) | Solo `TemplateManager` (`:13`) | Solo `TemplateManager` | Frontera encapsulada |

**Conclusión:** 5 de 7 almacenes son fronteras encapsuladas de un solo propietario; **`TradeDraw_WSConfig` es un bus de acoplamiento entre UI y Market Data** (la única infraestructura compartida de verdad), y los archivos de journals (`filesDir/TradeDraw_Audits`, `adaptive_learning_state.json`, journal de modos) son el segundo plano compartido: los escribe `TradeJournalLogger`/`AdaptiveLearningEngine`/`ModeEventJournal` (objects estáticos) y los lee el bridge para `/status`, `/shadow_journal`, `/adaptive_state`, `/mode_journal` (`TradeDrawHttpBridge.kt:211-229`, `:818-843`). El token viaja en claro en este bus y `allowBackup=true` lo expone a backups (R2 riesgo 6, re-confirmado: grep EncryptedSharedPreferences → 0).

### 2.8 — Investigación 8: Backtesting — técnica de aislamiento y causa de los 2 skipped

**(a) ¿`ReplayBacktestTest` ejecuta el MISMO TradingEngine?** **NO.** Ejecuta `SyntheticCandleEngine` directamente (`ReplayBacktestTest.kt:268-271`) alimentado con ticks CSV (`ts,ric,price`), y re-implementa en el propio test la orquestación de señales: ventana sniper (`:288-289`), cooldowns por submodo (`:214`, constantes `:624-631`), 1 orden por vela (`:318`), bloqueo por operación abierta (`:320`), huecos de feed >10 s con reinicio (`:343-349`). Lo que SÍ reutiliza del motor son **funciones puras**: `calcularPoderesDeMercado` (`:300` → `TradingEngine.kt:2903`), `motivoVetoPorAgotamientoDeCompra` (`:308` → `:2961`), `motivoBloqueoPorAntiPatron`/`mejorCoincidenciaEnMemoria` (`:404-405` → `AdaptiveLearningEngine.kt:540/:561`), `generateDeterministicDecisionId` (`:466` → `TradingEngine.kt:918-920`). grep `TradingEngine(` en test → 0 instanciaciones.

**(b) ¿Cómo neutraliza la ejecución?** Ni mock de ATAS ni modo ni flag: **por omisión estructural** — el harness jamás toca `TradingEngine`, `ATAS`, bridge ni broker; el "trade" es un registro local con balance simulado (`var balance = 1000.0; val stake = 1.0`, `:380-381`), y el veredicto se resuelve comparando precio de entrada vs tick a +60 s (`priceAt` `:117-132`, `resolveTrade` `:137-146`; fills instantáneos sin slippage declarados en `:21`). Los resultados se escriben a CSV con el MISMO esquema del journal (`:486`) y `decision_id` determinista (`:466`) para la paridad live↔replay.

**(c) ¿Base viable para el futuro Simulation Engine?** **PARCIALMENTE** — lo reutilizable son las funciones puras y el esquema de journal/id; la orquestación duplicada (ventanas, cooldowns, vetos re-escritos en el test) es evidencia del coste de NO tener la frontera: cada cambio del cascade del motor obligó a parches de paridad en el test (los comentarios `:285-299`, `:456-461` documentan correcciones sucesivas de divergencia 0%→paridad). Para R29-B, un `SimulatedOrderGateway` debería extraer la cascada a política reutilizable en lugar de duplicarla (ver §4).

**(d) Causa documental de los 2 skipped** [VERIFICADO — XML + código]:
- XML on-disk `TEST-com.example.tradedraw.ReplayBacktestTest.xml` (timestamp `2026-11-05T23:58:49.339Z`): `tests="2" skipped="2" failures="0" errors="0"`. Es el único XML con skipped en todo `testDebugUnitTest` (grep `skipped="2"` → 1 archivo). Re-agregado total de 58 XML vigentes: **573 tests, 2 skipped, 0 fallos/errores** (coincide con R2 §5; los 3 XML obsoletos de clases inexistentes siguen presentes: `AssetRicMappingTest`, `HudGeometriaTest`, `OrderStateReposoTest`).
- Causa test 1: `testReplayBacktestAutoAdaptive` (`ReplayBacktestTest.kt:515-519`) → `assumeTrue("BACKTEST_CSV o BACKTEST_DIR debe estar definido", !dirEnv.isNullOrBlank() || !csvEnv.isNullOrBlank())` (`:519`, import `org.junit.Assume.assumeTrue` `:4`). Stack del XML: `AssumptionViolatedException` en `ReplayBacktestTest.kt:519`.
- Causa test 2: `testReplayBacktestAllStrategiesReport` (`:588-593`) → `assumeTrue("BACKTEST_CSV no está definida: omitiendo reporte multiespecífico", !csvEnv.isNullOrBlank())` (`:591`). Stack: `:591`.
- **Resumen:** la suite insignia de replay se saltó porque la última corrida registrada (2026-11-05, lote de R28) no exportó `BACKTEST_CSV`/`BACKTEST_DIR`. Insumos compatibles SÍ existen en el repo: 34 CSV `ts,ric,price` en `loops/r23{c,d}_validation/window_*/ticks_live.csv` (formato exacto que espera `loadTicks`, `:85-112`); bajo `data/` solo hay journals de salida (`replay_eth.csv`, `replay_trades.csv`, 4 líneas, formato journal — no entrada). La suite se ejecutaría exportando `BACKTEST_DIR=<loops/r23c_phase3_validation>`; no se ejecutó aquí (prohibido regenerar artefactos).

### 2.9 — Investigación 9: Re-verificaciones menores de R2

**(a) CRLF de `OverlayService.kt`** [VERIFICADO]: `grep -c $'\r'` = **2466** = `wc -l` (2466 líneas) → 100% CRLF íntegro, coincide con R1 §8.4 / R2 §2.3. Sin `.gitattributes` (R28A).

**(b) "+800 líneas de tests" (R1 §8.1)** — **recalculado, cifra heredada IMPRECISA**:
- Test modificado en working tree: `HealthSupervisorTest.kt` numstat **+277/−52**.
- Tests sin rastrear: `ActiveInstanceTest.kt` 424 + `BalanceFreshnessSeparationTest.kt` 184 + `HealthDataSourceTest.kt` 187 = **795**.
- Total LOC de test añadidas por el trabajo R27/R28 sin commit: **277 + 795 = 1.072**. El "~800" de R1 solo cuadra con la lectura restringida a los archivos sin rastrear (795 ≈ 800); contando el diff, la cifra real es **1.072**. Etiqueta: VERIFICADO el recálculo; R1 queda corregido por este informe (no se edita R1 — regla 7).

**(c) "15 rutas sin rastrear" (R1 §8.1 y §6.1 "~15 rutas")** — **recalculado, cifra heredada IMPRECISA**:
- `git status --porcelain | grep "^??" | wc -l` = **13 rutas top-level** (4 archivos Kotlin de src + `docs/agents/` + 4 directorios de loops R27/R28 + `loops/r29a/` + 3 archivos de supervisor).
- Contando archivos individuales dentro de los directorios sin rastrear: **30 archivos**.
- "15" no coincide con ninguna de las dos métricas (13 paths / 30 files). Cifra correcta para R29-B: **13 rutas (30 archivos)**. Etiqueta: VERIFICADO el recálculo.

---

## 3. Límites demostrables vs NO demostrables (insumo G3)

**Demostrables por código (VERIFICADO, no requieren ejecución):**
1. SHADOW termina en decisión: `return` en `TradingEngine.kt:2520` anterior a toda primitiva de tap; contador y journal separados; RiskManager intacto (test `ShadowModeTest`).
2. SHADOW consume la vela real (`:2515-2517`) — estado de rate-limit compartido con ejecución.
3. `/trade` sin cascada de vetos ni `canExecuteTrade` ni consulta de modo; único guard = heurística demo (`bridge:141-159` + `ATAS:523-545`).
4. `/click` usa `performClick` → root-tap primario → operativo sin servicio de accesibilidad (`bridge:416` + `ATAS:215-243`).
5. `dispatchGesture` solo existe como método de `AccessibilityService`; la vía primaria de tap es shell root (`su -c input tap`, `ATAS:323`); no hay interfaz de ejecución (0 interfaces en main).
6. `isDemoAccount` se alimenta SOLO de texto de la UI del broker (`ATAS:701-707`, `:841-847`); el feed es el broker real con token real; no hay entorno sandbox en ningún punto de la cadena.
7. `isBalanceFresh` (3 s) solo gobierna la atribución de TIE en liquidación, no la colocación (`:2675-2679`).
8. Los 2 skipped de `ReplayBacktestTest` son `AssumptionViolatedException` por env vars ausentes (`:519`, `:591`; XML 2026-11-05).
9. `TREND_FOLLOWING`/`COMBINED` seleccionables por 5 vías (§2.5).

**NO demostrables sin ejecución (quedan como HYPÓTESIS/PENDIENTE para R29-B):**
1. Si `dispatchGesture` es realmente filtrado por Binomo/MIUI en este dispositivo (afirmado por comentarios `ATAS:211-216`, no reproducible aquí).
2. Si la heurística demo/real falla en la práctica con cuentas reales o idiomas no españoles (no hay registro en journals; análisis de código en §2.2).
3. La distribución real TIE vs TIE_INCIERTO con el working tree (requiere sesión en dispositivo).
4. El timing real de unbind→rebind de MIUI y su efecto sobre la liquidación (ActiveInstance lo hace atómico, pero la ventana existe).
5. Paridad live↔replay de la suite insignia (skipped en la última corrida registrada).
6. Que el auto-heal (`ATAS:379-390`) re-registre el servicio en MIUI actual (toggle documentado, no ejecutado).

---

## 4. Implicaciones concretas para la frontera `execution/` de R29-B

1. **Un solo puerto, tres transportes:** `OrderGateway` (D-6) debe abstraer `dispatchGesture` (ATAS:576), shell root (`su -c input tap`, ATAS:323) y el tap directo de bridge/HUD (`performClickAt`, ATAS:523). Un gateway que solo envuelva el gesto deja fuera la vía primaria real del dispositivo.
2. **El guard demo está cuadruplicado con alcances distintos** (engine `:2183` solo-AUTONOMOUS; taps `:200/:302/:532` siempre): la frontera debe unificarlo como política de ejecución con una sola fuente de `isDemoAccount` inyectada (hoy: heurística UI + saldo en RiskManager, §2.2).
3. **SHADOW como semilla del simulador:** la rama `:2493-2520` es el punto de corte natural — un `OrderGateway` con implementaciones `LiveTapGateway` (taps) y `SimulatedOrderGateway` (colocación/confirmación/liquidación sintéticas, lo que R28-B declaró ausente). SHADOW pasaría de "registrar y return" a "ejecutar el ciclo simulado".
4. **Desacoplar estado de rate-limit:** `lastExecutedCandleEpochMinute` mutado por SHADOW (`:2515-2517`) debe vivir en el gateway/política de ejecución, no en el motor, o la simulación seguirá consumiendo límites reales.
5. **Cascada extraíble sin cambiar orden:** los 13 vetos de `executeHeadlessTrade` (`:2178-2470`) son una secuencia de funciones `Pair<Boolean,String>`/`String?` ya mayormente puras o aislables (`puedeOperarAutonomoEnDemo`, `motivoVetoPorDesajusteDeActivo`, `motivoVetoPorAgotamientoDeCompra`, `canExecuteTrade`) — una cadena de `VetoPolicy` preservando el ORDEN exacto evita el riesgo regla-15 (cualquier reordenación altera el comportamiento observable).
6. **El wiring de Market Data ya es el patrón a replicar:** listeners + providers lambda (`OverlayService.kt:339-366`) demuestran que la inyección es viable sin framework (sin Hilt); la frontera `execution/` puede seguir el mismo estilo.
7. **Persistencia:** los journals (objects estáticos) y `TradeDraw_WSConfig` (bus UI↔WS) son los dos planos compartidos que la modularización debe convertir en contratos explícitos (interfaces de journal + provider de credenciales), sin tocar el esquema CSV congelado (comparador live↔replay).
8. **Backtesting:** reutilizar las 4 funciones puras ya compartidas y extender el patrón a la cascada completa, eliminando la duplicación de orquestación del harness (causa documentada de parches de paridad sucesivos).

---

## 5. Criterios de aceptación de la ronda

- [x] Matriz de aislamiento de los 10 dominios con columna real/nominal y evidencia `ruta:línea`. [VERIFICADO]
- [x] 9 investigaciones respondidas con citas exactas, incluyendo trazado completo de `/trade`, `/click`, root-tap, SHADOW y guards demo. [VERIFICADO / PARCIALMENTE VERIFICADO donde se indica]
- [x] Hallazgo nuevo documentado sin alterar código: SHADOW consume la vela real (`:2515-2517`); `/trade` ignora el modo (opera en SHADOW/DISABLED); 4 primitivas de tap+root; `onBalanceUpdatedListener` declarado y nunca asignado (`ATAS:102`, grep consumidores → 0); cifras heredadas R1 corregidas (1.072 LOC de test, 13 rutas).
- [x] Skips de `ReplayBacktestTest` explicados con XML + línea de `assumeTrue`. [VERIFICADO]
- [x] CRLF re-verificado (2466/2466). [VERIFICADO]
- [x] Límites demostrables vs NO demostrables separados para G3. [VERIFICADO como separación metodológica]
- [x] Sin modificaciones de código; única escritura: este archivo; `git status` idéntico al inicio (22 entradas); sin secretos impresos (token referenciado por clave, nunca por valor).

## 6. Veredicto

**RONDA 3 COMPLETADA — APROBADA.** El aislamiento de TradeDraw es **nominal en 7 de 10 dominios y parcialmente real en 3** (Market Data por wiring de listeners, Backtesting por reutilización de funciones puras fuera de la app, y Persistencia solo como almacenes encapsulados — con dos excepciones que son buses: `TradeDraw_WSConfig` y los journals estáticos). No existe frontera de ejecución: 0 interfaces, 3 primitivas de tap + shell root convergiendo sin tipo común, y el guard demo cuadruplicado con alcances distintos. SHADOW está aislado de la ejecución **por una línea** (`return` :2520) y comparte con ella el rate-limit de vela (`:2515-2517`); no simula el ciclo de órdenes. La seguridad demo/real descansa íntegramente en una heurística de texto UI (`ATAS:701-707/:841-847`) con default `true` (fail-open si la heurística no observa), amortiguada en AUTONOMOUS por el suelo de equity demo de 20M COP — pero no en la vía `/trade`/`/click`, que con heurística fallida operaría en real con cero vetos de estrategia/feed/riesgo. `/trade` requiere accesibilidad; `/click` no (root). E1/E2 resueltas: la vía bridge sin accesibilidad existe vía `/click` (no `/trade`), y `TREND_FOLLOWING`/`COMBINED` son seleccionables por 5 vías. Los 2 skipped son `assumeTrue` por env vars ausentes (no es fallo de código); los insumos de ticks existen en `loops/r23*/window_*/ticks_live.csv` (34 archivos). Insumos R29-B listos: semilla de `SimulatedOrderGateway` (rama SHADOW), catálogo de vetos extraíbles en orden congelado, y el patrón de wiring de Market Data como estilo de inyección. Bloqueos: ninguno.

---

## 7. Nota del supervisor — correcciones del verificador (08:35Z; el cuerpo del informe no se toca)

1. **Cita de línea**: el `return` de SHADOW es **:2518**, no ":2520" (el informe lo repite 4 veces, incluso en su snippet). El bloque termina en :2518; :2520 ya es la lectura de `accessibility`.
2. **Atribución de CSVs**: no existe `loops/r23c_validation`. Distribución real de los 34 `ticks_live.csv`: `r23c_phase3_validation` 3, `r23d_validation` 10, `r23e_adaptive_audit` 6, `r23f_validation` 3, `r23g_validation` 7, `r24_validation` 1, `r25_validation` 4. El total 34 y la sugerencia `BACKTEST_DIR=loops/r23c_phase3_validation` siguen siendo válidos.
3. **Refs de ATAS**: el conteo grep es **88** (excluyendo el propio archivo), no 87; la descomposición por-miembro suma 84 → 4 refs sin desglosar (probablemente `lastTapStartTs/EndTs/CandleSecond` y `DEFAULT_FRESHNESS_MS` en `TE:355`). Inmaterial.
4. **Untracked**: 31 contando este propio informe; cifra móvil por diseño. Trivial: el informe son 242 líneas, no ~250.
5. **Cierre de omisión heredada**: broadcasts TAP/SWIPE/CMD firmados verificados contra el manifiesto: `AndroidManifest.xml:19-21` declara `INTERNAL_COMMAND` con `protectionLevel="signature"`; receptores registrados con ese permiso (`ATAS:444-451`, `OverlayService:382-389`). Claim correcto.
