# ROUND 04 — Arquitectura objetivo · TradeDraw R29-A

**Fecha:** 2026-11-09T08:45Z · **Ejecutado por:** DeepInvestigator (subagente de arquitectura, sesión Antigravity visible)
**Alcance:** DISEÑO. Sin crear módulos Gradle, sin tocar código, sin mover archivos. Única escritura: este archivo. Sin app/Gradle/ADB/AUTONOMOUS/broker/commits. Sin secretos impresos.
**Método:** insumos R2/R3 como hechos establecidos (no repetidos) + verificación directa puntual de: lista de clases, `OrderState` (`RiskManager.kt:272`), `AutoTradeMode` (`TradingEngine.kt:27-31`), `TradeEventLog.Kind` (11 tipos, `TradeEventLog.kt:36-100`), `HealthSupervisor.State/Snapshot/Report` (`health/HealthSupervisor.kt:42-101`), `generateDeterministicDecisionId` (`TradingEngine.kt:918-920`), `tradeId = UUID.randomUUID()` (`TradingEngine.kt:2524`), estáticos de ATAS (`ATAS:17-105`), endpoints del bridge (28, `TradeDrawHttpBridge.kt:131-514`).
**Convención:** **VERIFICADO** = código actual citado. **PROPUESTA** = diseño nuevo para R29-B; ninguna PROPUESTA contradice un hecho VERIFICADO.

---

## 1. Principios de diseño adaptados al proyecto real [PROPUESTA, acotada por VERIFICADO]

El proyecto es un monolito Android JVM: 1 módulo (`:app`), 47 clases Kotlin (19.241 LOC; R2), 0 interfaces (`grep "interface "` main → 0, R3 §1), sin DI (sin Hilt/Dagger), sin Coroutines (loops con `Handler`/`Thread`/`Executors`), OkHttp 4.12, 55 clases de test JVM (565 tests). La arquitectura se adapta a eso, no al revés:

1. **Inyección sin framework.** El único patrón de inyección que ya funciona en el código es wiring por constructores + lambdas + `ActiveInstance<T>` (`OverlayService.kt:339-366` para Market Data; `ActiveInstance.kt`, 86 LOC, genérica JDK-pura). Todo puerto nuevo usa ese estilo. Sin Hilt, sin ServiceLocator global nuevo (eso sería re-crear los estáticos con otro nombre).
2. **Sin Coroutines.** Las fronteras no introducen dispatchers ni suspend; los puertos definen callbacks/lambdas como el wiring existente.
3. **Testabilidad JVM como criterio de frontera.** Un dominio está bien extraído solo si puede instanciarse en un test JVM sin Android. Tres precedentes ya lo demuestran: `HealthSupervisor` con `Snapshot` inmutable (`HealthSupervisorTest`, 20 testcases), `RiskManager(null)` (`RiskManagerTest.kt:13`), `SyntheticCandleEngine` solo en replay (`ReplayBacktestTest.kt:268-271`).
4. **Semántica congelada = contrato, no detalle de implementación.** El orden de la cascada de 13 vetos, los esquemas CSV (29 col. trades / 11 col. shadow), el algoritmo de `decisionId` y los parámetros estratégicos son parte de la superficie contractual; migrarlos equivale a cambiarlos (regla 15).
5. **Modularizar por masa de código existente, no por nombres bonitos.** Dominio sin código hoy = no se crea módulo (regla 14). De los 12 dominios propuestos se derivan **11** (§3): 2 fusiones justificadas por evidencia, 1 división exigida por D-6.
6. **Los estáticos mueren al final.** Primero puertos con impl por defecto que delega al estático (aditivo, riesgo 0); el estático se elimina solo cuando su último consumidor migró. Así cada fase es reversible.
7. **Paridad como aceptación.** Cada fase se gating con la suite vigente (565 tests) + tests de equivalencia nuevos que AÑADEN verificación, nunca reemplazan expectativas congeladas.

---

## 2. Estados, eventos e identificadores (base común) [VERIFICADO salvo nota]

- **OrderState** (`RiskManager.kt:272`): `NINGUNA, ENVIADA, CONFIRMADA, NO_PROCESADA` — máquina de estados del Risk Engine; transiciones journaleadas como `TradeEventLog.Kind.ORDER_STATE`.
- **AutoTradeMode** (`TradingEngine.kt:27-31`): `AUTONOMOUS, SEMIAUTOMATIC, DISABLED, SHADOW` — 4 valores (el enunciado menciona 3; SEMIAUTOMATIC existe y es la rama pasiva `:277-287`). Viviría en Core; el punto único de despacho por modo es `syntheticCandleEngine.onSignalGenerated` (`TradingEngine.kt:275-289`, R3 §2.1b).
- **HealthState** (`health/HealthSupervisor.kt:43`): `READY, DEGRADED, BLOCKED` + `DataSource` (WEBSOCKET_PURO vigente) + `Snapshot` (14 campos) + `Report.toJson()` — ya es un contrato de facto puro; Observability lo publica, nadie lo consume para bloquear trading.
- **WebSocketState** (`MarketTick.kt:35`): `DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING/ERROR/UNAUTHORIZED` — estado de Market Data.
- **TradeEventLog.Kind** (11, `TradeEventLog.kt:36-100`): `VETO, ORDER_STATE, TAP, ASSET, PAYOUT_DISCREPANTE, FEED_MUDO, INOPERABLE, MODE_CHANGE, FEED_SIN_TICKS, SHADOW_DECISION, MANUAL_INCREMENT` — el bus de eventos existente; anillo en memoria (`MODE_CAPACIDAD=100` dedicado a MODE_CHANGE). Mapa a dominios: VETO/TAP/ORDER_STATE→Execution·Risk; ASSET/PAYOUT_DISCREPANTE→Adapters(sensor); FEED_MUDO/FEED_SIN_TICKS/INOPERABLE→Market Data·Strategy; MODE_CHANGE/MANUAL_INCREMENT→Core(orquestación); SHADOW_DECISION→Simulation.
- **Identificadores.** `decisionId`: **determinista** — `UUID.nameUUIDFromBytes("${ric}_${nonceMs}_${ACTION}")` (`TradingEngine.kt:918-920`); algoritmo congelado (la paridad live↔replay depende de él; límite documentado: colisión mismo activo+ms+acción aceptada). `tradeId`: **NO es determinista** — `UUID.randomUUID()` por orden real (`TradingEngine.kt:2524`) y `null` en SHADOW (contrato fijado por `ShadowModeTest.kt:48-62`). El enunciado de la ronda dice "decisionId/tradeId deterministas ya existentes": eso es correcto solo para `decisionId` — se corrige aquí para que R29-B no diseñe sobre un supuesto falso.

---

## 3. Los 12 dominios evaluados → 11 dominios objetivo [PROPUESTA sobre hechos VERIFICADOS]

| # | Dominio propuesto | Veredicto | Evidencia decisiva |
| :-- | :--- | :--- | :--- |
| 1 | Core Domain | **SÍ** (núcleo compartido + orquestación) | Tipos puros ya existen (`MarketTick` 197 LOC, `ActiveInstance`, enums, `AssetRicMapper`); el residuo orquestador de TradingEngine le da contenido |
| 2 | Market Data | **SÍ** — el dominio MÁS real hoy | Único con sustitución natural (URL por prefs) y wiring inyectado (`OverlayService.kt:339-366`, R3 §1) |
| 3 | Research Engine | **SÍ, fusionado con 7** | Vive FUERA de la APK: `validation_engine/` (7 módulos Python) + `scripts/backtest/*.py` + replay JVM |
| 4 | Strategy Engine | **SÍ, con interior congelado** | `when` de ~490 líneas en `TradingEngine.kt:1061-1553` + `SyntheticCandleEngine` (emisor de señales `:275-289`) + `MarketRegimeClassifier` + `AdaptiveLearningEngine` |
| 5 | Simulation Engine | **SÍ** — semilla SHADOW | Rama `TradingEngine.kt:2493-2518` (R3 §2.1); `SimulatedOrderGateway` ausente (R28-B) |
| 6 | Risk Engine | **SÍ** | `RiskManager` 1.341 LOC, instanciable sin Android (`RiskManagerTest.kt:13`); acoplado a estáticos ATAS en 5 puntos |
| 7 | Backtesting Engine | **FUSIONADO en 3** | Replay JVM (`ReplayBacktestTest` 633 LOC) reutiliza las MISMAS funciones puras y los MISMOS CSV de ticks que la validación Python; dos módulos separarían un flujo único (R3 §2.8) |
| 8 | Analytics Engine | **FUSIONADO en 9** | 0 clases propias hoy: `/stats` `/adaptive_state` y agregación viven dispersas en el bridge (`TradeDrawHttpBridge.kt:375-391`) y scripts; crear módulo vacío viola regla 14 |
| 9 | Observability | **SÍ** (+Analytics) | `TradeEventLog` (32 llamadas/6 archivos), `ModeEventJournal`, `CrashLogger`, `HealthSupervisor` — patrón de acceso homogéneo |
| 10 | Android UI / Dashboard | **SÍ** | `OverlayService` 2.466 LOC + 12 vistas/activities; la UI construye el grafo entero hoy (`OverlayService.kt:285-366`) |
| 11 | Adapters | **SÍ, DIVIDIDO** | ATAS concentra 3 roles (sensor/actuador/estado, R3 §1); D-6 exige frontera de ejecución propia |
| 12 | Persistence | **SÍ** | 7 almacenes prefs + 3 journals en archivo; 5/7 encapsulados, `TradeDraw_WSConfig` es bus UI↔MarketData (R3 §2.7) |

**División:** Adapters → **Adapters** (E/S de operador y sensado del broker) + **Execution** (frontera de despacho, D-6). Sin ella, el OrderGateway quedaría enterrado en el módulo de E/S y la semilla de simulación no tendría contraparte limpia.

**Mapa final (11):** `core` · `market` · `strategy` · `risk` · `execution` · `simulation` · `research` (ex 3+7, JVM/Python, fuera de la APK) · `observability` (ex 8+9) · `ui` · `adapters` · `persistence`.

### 3.1 Contratos y límites por dominio [PROPUESTA]

Regla de dependencias global: **core ← nada; market/strategy/risk/simulation/observability/persistence ← core; execution ← core(+risk vía puerto de política); adapters ← execution·market·core; ui ← core·adapters (composition root); research ← core (solo JVM).** Ciclos prohibidos; los ciclos actuales Engine↔OverlayService y →ATAS se rompen con los puertos de §5.

- **core** — Resp.: tipos de valor, enums compartidos, IDs, `ActiveInstance`, orquestación residual (modo, ciclo tick→señal→despacho, watchdog). No hace: estrategia (delega), vetos (delega en Execution), I/O. Contratos: `MarketTick` (ya existe), `AutoTradeMode` (ya), `DecisionIds.generateDeterministicDecisionId(asset, signalTsMs, action): String` (ya, congelado), `OrderRequest(decisionId, tradeId, action, x, y, assetRic, candleEpochMinute, mode)`, `OrderReceipt(accepted: Boolean, transport: TapTransportKind, error: String?)`, `BrokerStateView(balance: Double, lastBalanceReadMs: Long, instanceAvailable: Boolean, isDemoAccount: Boolean, observedAsset: String, observedPayout)`. Dep. permitidas: JDK. Prohibidas: Android, OkHttp, todo dominio. Eventos: MODE_CHANGE, MANUAL_INCREMENT.
- **market** — Resp.: conexión WS, parseo de tramas, suscripción multi-RIC, frescura/congelación, payout observado. No hace: decisiones, taps, HUD. Contratos: `MarketDataFeed.subscribe(ric): TickStream`; `TickStream` (listener `onTick(MarketTick)`, estilo actual `onTickListener`); `FeedHealth.isFeedFresh(): Boolean` / `isPriceFrozen(): Boolean` / `recentPriceRangeRatio(): Double` (los providers-lambda que hoy consume el motor, `TradingEngine.kt:364`). Dep.: core, OkHttp, persistence(CredentialProvider). Prohibidas: execution, ui, ATAS. Eventos: FEED_MUDO, FEED_SIN_TICKS. Estado: WebSocketState.
- **strategy** — Resp.: velas sintéticas/indicadores, clasificación de régimen, evaluación de las 14 estrategias, filtros de timing, aprendizaje adaptativo (veto en cascada). No hace: despacho, riesgo de capital, journals. Contratos: `SignalSource` (el actual `onSignalGenerated` de `SyntheticCandleEngine`), `StrategyEvaluator.evaluate(AnalysisContext): SignalDecision` (seam P2 — el `when` congelado NO se reorganiza), `AdaptiveBlockProvider` (bloqueo adaptativo que la cascada consulta). Dep.: core, observability(EventSink). Prohibidas: execution, ui, adapters. Eventos: INOPERABLE (compartido con Market Data). **Interior congelado (reglas 1-2):** bodies de estrategias, umbrales, `MarketTickFilters` (ventana :01-:05) — solo se mueven de paquete, nunca se editan.
- **risk** — Resp.: política de capital (suelo equity, stop sesión, trailing, cooldowns, martingala/Soros, Sniper), máquina OrderState, idempotencia de liquidación. No hace: taps, percepción (deja de leer estáticos ATAS), UI. Contratos: `CapitalPolicy.canExecuteTrade(subMode, confidence): (Boolean, motivo)` (firma actual `RiskManager.kt:714`), `OrderStateMachine` (OrderState ya existente), `BalanceSource.read(): BalanceReading` (puerto que reemplaza las 5 lecturas estáticas `RiskManager.kt:724/:934/:1108/:1144/:1331`). Dep.: core, persistence(sus propias prefs). Prohibidas: adapters (estáticos ATAS), ui. Eventos: ORDER_STATE.
- **execution** — Resp.: frontera de despacho (D-6): política de vetos en orden congelado, guard demo unificado, rate-limit de vela, selección de transporte. No hace: decidir señal (viene de strategy), valorar riesgo (delega en `CapitalPolicy`), simular. Contratos: `OrderGateway.dispatch(OrderRequest): OrderReceipt`; `TapTransport` con 3 impls (§4); `ExecutionPolicy.evaluate(ExecContext): VetoResult` — cadena congelada de 13 vetos (`TradingEngine.kt:2178-2470`); `CandleRateLimiter` (`lastExecutedCandleEpochMinute`, relocado por D-7). Dep.: core, risk(puerto CapitalPolicy), market(puertos FeedHealth). Prohibidas: ui, adapters (los transports los implementa adapters). Eventos: VETO, TAP.
- **simulation** — Resp.: ciclo de orden simulado (colocación/confirmación/liquidación sintéticas) para SHADOW y research; journals simulados. No hace: taps (cero dependencia con Adapters — invariante ya garantizada por `return :2518`, R3 §2.1c), mutar RiskManager (contrato `ShadowModeTest.kt:96-112`). Contratos: `SimulatedOrderGateway: OrderGateway` (impl alternativa del MISMO puerto — sin cascada duplicada, R3 §2.8c), `SimClock`, `SimLedger` (balance simulado, patrón `ReplayBacktestTest.kt:380-381`). Dep.: core, execution(ExecutionPolicy/OrderGateway), persistence(ShadowJournal). Prohibidas: adapters, ATAS, ui. Eventos: SHADOW_DECISION (con `tradeId=null`, congelado).
- **research** (ex Research+Backtesting) — Resp.: replay de ticks CSV, validación estadística (Wilson CI, no-look-ahead, out-of-sample), paridad live↔replay, calidad de datos. No hace: operar, tocar la APK en runtime. Componentes: harness replay (a extraer de `ReplayBacktestTest` a módulo JVM), `validation_engine/` (7 módulos Python), `scripts/backtest|validation|analysis`. Contratos: reutiliza `calcularPoderesDeMercado`, `motivoVetoPorAgotamientoDeCompra`, `motivoBloqueoPorAntiPatron`, `generateDeterministicDecisionId` (las 4 funciones puras ya compartidas, R3 §2.8a) + `ExecutionPolicy` extraída. Dep.: core (JVM) + **strategy (solo funciones puras, ver E5)**. Prohibidas: Android. **Nota E5 (revisión):** las funciones puras `motivoBloqueoPorAntiPatron`/`mejorCoincidenciaEnMemoria` viven en `AdaptiveLearningEngine` (dominio strategy, `:540`/`:561`) — con la regla "research ← core" de §3.1 esto sería una contradicción el primer día de F2/F5. Resolver: (a) mover esas 2 funciones puras a core, o (b) regla explícita "research ← core + strategy(solo funciones puras)".
- **observability** (ex Observability+Analytics) — Resp.: anillo de eventos, journals de modo, health, crash log, agregación `/stats` `/adaptive_state`. No hace: bloquear trading (HealthSupervisor es solo diagnóstico), escribir esquemas de trade. Contratos: `EventSink.add(Event)` (TradeEventLog tras interfaz; `Kind` congelado), `HealthReportSource` (Snapshot/Report ya puros), `JournalReader` (para `/events /mode_journal /shadow_journal` del bridge). Dep.: core, persistence(archivos). Prohibidas: execution(decisiones), ui.
- **ui** — Resp.: HUD, actividades, chat IA-overlay, dibujo, configuración. No hace: decidir ni despachar (deja de mutar `strategy`/`setTradingMode` directamente — hoy `AgentChatOverlay.kt:365-371` muta el motor; pasa por `ModeController`). Contratos: `HudPort` (showManualFallbackAlert, restoreHudTouchable, drawTradeEntry — los usos actuales de `OverlayService.instance` desde motor/ATAS, `TE:2049`/`ATAS:360`), `ModeController.set(mode, origin)` (unifica `setTradingMode:96-117`, `/mode`, diálogo, chat — auditable vía MODE_CHANGE+ModeEventJournal). Dep.: core, adapters(composition root). Prohibidas: risk/strategy directos (vía contratos core).
- **adapters** — Resp.: ATAS (sensor UI del broker + transports de tap), bridge HTTP loopback (28 endpoints), AIClient, BrokerDetector, CalibrationManager. No hace: política (delega en execution), estado de negocio (los estáticos migran, §5). Contratos: impls de `TapTransport`, `BrokerSensor` (alimenta `BrokerStateView` desde `onAccessibilityEvent`), `OperatorApi` (los 28 endpoints, incluyendo el re-enrutado de `/trade` por D-7). Dep.: execution, market, core. Prohibidas: simulation.
- **persistence** — Resp.: 7 almacenes prefs, CSVs de journal, JSON adaptativo, sync interno→externo. No hace: cifrar (aplazado, §7), interpretar datos. Contratos: `CredentialProvider/CredentialSink` (`ws_auth_token`, `ws_endpoint_url` — parte el bus `TradeDraw_WSConfig`, R3 §2.7), `JournalWriter` (Trade/Shadow/Mode; esquemas congelados), `SettingsStore` por almacén (5/7 ya mono-propietario). Dep.: core. Prohibidas: todo dominio de lógica.

---

## 4. Las 3 vías de tap y el guard demo cuadruplicado [VERIFICADO + PROPUESTA D-6]

Hecho (R3 §2.3/§2.4): tres primitivas + 4 fuentes de petición sin tipo común; guard demo en 4 sitios con alcances distintos; `/trade` ignora el modo (D-7).

| Vía | Código actual | Guard demo actual | Transporte objetivo |
| :-- | :--- | :--- | :--- |
| Root shell (PRIMARIA) | `executeRootTap` `su -c input tap` (`ATAS:327`); opera con accesibilidad caída (`:238-243` + autoHeal) | `puedeDespacharTap` `:302` | `RootTapTransport` |
| Gesto accesibilidad (fallback) | `dispatchGesture` (`ATAS:576`), solo con servicio enlazado | `:200` (y `:532` vía performClickAt) | `GestureTapTransport` |
| Tap directo bridge/HUD | `performClickAt` (`ATAS:523`) ← `/trade` `:152`, `/click` `:416`, broadcasts `:397`, test UI | `:532` | mismos transports vía `OrderGateway` |
| Guard del motor | `puedeOperarAutonomoEnDemo` (`TradingEngine.kt:2183-2196`) — SOLO AUTONOMOUS | 4º | `ExecutionPolicy` (paso 1 de la cadena) |

**PROPUESTA (D-6):** `OrderGateway` único con `dispatch(OrderRequest): OrderReceipt`; el gateway elige transporte (root→gesto, conservando el orden de preferencia documentado `ATAS:211-216`) y aplica UNA sola `ExecutionPolicy` que encadena: guard demo (con el alcance de cada call-site preservado vía campo `origin: Engine|Bridge|Broadcast|HudTest` — la unificación de alcances es un cambio de comportamiento y se hace al final, §6 F3) + cascada congelada + `CandleRateLimiter`. **D-7:** `/trade` pasa a consultar el modo (`DISABLED/SHADOW → rechazo explícito con evento`) y el rate-limit de vela vive en `ExecutionPolicy`, no en el motor. Pendiente de autorización explícita porque AÑADE vetos a una vía que hoy no los tiene (regla 6/15: cambio de comportamiento deliberado, documentado como tal).

---

## 5. Mapa de estáticos globales → mecanismo objetivo [VERIFICADO fan-in + PROPUESTA mecanismo]

| Estático | Consumo actual | Mecanismo objetivo | Orden | Riesgo regla-15 |
| :-- | :--- | :--- | :-- | :--- |
| `ATAS.classifyAsset`/`AssetClassification` | puro, 2 refs | mover a core sin cambios | 1 | Nulo (función pura) |
| `TradeEventLog` (object) | 32 llamadas/6 archivos | `EventSink` inyectado; impl = anillo actual; `Kind` congelado | 2 | Bajo: es "PURA observabilidad" (doc en `TradeEventLog.kt`); tests `TradeEventLogTest`, `ModeChangeEventTest` |
| `ATAS.latestObservedBalance/lastBalanceReadMs/instance` | 14+2 refs; alimenta `isBalanceFresh` (3 s) y RiskManager (5 puntos) | `BalanceSource` puerto; impl delega al estático; Risk y motor lo reciben por ctor | 3 | **Medio-alto**: es insumo de guards (liquidación TIE). Mitigación: dual-read con log de equivalencia durante 1 fase (patrón R27-H) + `BalanceFreshnessSeparationTest` |
| `ATAS.isDemoAccount` (heurística UI, default true) | 4 refs + guard cuadruplicado | `DemoGuardPolicy` con única fuente inyectada; heurística intacta detrás | 4 | **Alto**: fail-open de facto (R3 §2.2). Solo envolver; reescribir la heurística = prohibido corto plazo |
| `ATAS.performClick/executeRootTap/performClickAt` | refs desde 4 fuentes | `TapTransport` impls tras `OrderGateway` | 5 | **Alto**: timeout 300 ms, bypass HUD, restoreHudTouchable deben replicarse exacto; tests `TradeIdEndToEndTest`, `AutonomousSafetyAndAssetScaleTest` |
| `OverlayService.instance` | 39 refs/9 archivos | `HudPort` (interfaz en core, impl en ui). **Ampliado por revisión (E2)** — consumo real verificado: motor/ATAS (`TE:2050` — antes citado :2049, `emitHapticWarning` — y `ATAS:360`), **RiskManager ×6** (`:543/:1059/:1097/:1133/:1164/:1234`, todos `updateHUDView` desde transiciones de OrderState → escrituras risk→ui), **BinomoWebSocketClient :91** (`instance?.tradingEngine?.onAssetChanged()` → arista market→core→ui), **ChartViewportController :154** (`drawingView.triggerClickAnimation`). F6 solo es seguro si HudPort es visible para risk/market/core o si esos 8+ call-sites se re-rutan. **Falta también en el mapa**: `ATAS.instance` ×26 (el miembro estático mayor; cómo bridge/HUD alcanzan `performClickAt` a nivel instancia) y nota de disposal de listeners: `ATAS.onAssetUpdatedListener` (asignado en `OverlayService:364`) y `onBalanceUpdatedListener` (muerto, R3) sin nota de liberación | 6 | Medio: solo UI → **revisado (E2): cruzado (risk×6, market×1)** |
| Resto estáticos ATAS (asset, payout, orderAmount, OTC) | ~25 refs | `BrokerStateView` alimentado por `BrokerSensor` | 7 | Medio |
| Prefs `TradeDraw_WSConfig` (bus UI↔WS) | 6 usos | `CredentialProvider`(lectura, market) + `CredentialSink`(escritura, ui); archivo y claves intactos | 8 | Bajo (esquema congelado); cifrado aparte, NO corto plazo |
| Journals estáticos (`TradeJournalLogger`, `ModeEventJournal`, `adaptive_learning_state.json`) | 6 capas lectoras | `JournalWriter/JournalReader` tras interfaz; esquemas CSV congelados | 9 | Bajo si esquema intacto; comparador live↔replay como test de paridad |

---

## 6. Estrategia de migración incremental por fases [PROPUESTA]

| Fase | Alcance | Riesgo regla-15 (¿altera comportamiento? ¿cómo se evita/prueba?) | Criterio de aceptación | Rollback |
| :-- | :--- | :--- | :--- | :--- |
| F0 Preparación | CI ejecuta `testDebugUnitTest`; decidir destino del working tree R27/R28 sin commit (bloquea todo refactor de archivos tocados) | No toca src | CI verde en `r10-night` con 565 tests | revert yml |
| F1 Puertos aditivos | `EventSink`, `BalanceSource`, `CredentialProvider` como interfaces con impl-delegado-a-estático; consumidores por ctor con default = estático | No: delegación pura. Prueba: suite completa + test nuevo de equivalencia dual-read de balance | 565 tests + N nuevos en verde | borrar interfaces (nada dependía de ellas) |
| F2 Extracción de core puro | mover `MarketTick`+`MarketTickFilters`, enums, `AssetRicMapper`, `ActiveInstance`, `DecisionIds` a paquete `core/` (mismo módulo) | No: movimientos + imports; CRLF de archivos tocados preservado. Prueba: suite + compilación | suite verde; sin diff semántico (`git diff -w` solo imports) | revert de movimientos |
| F3 Frontera de ejecución | `OrderGateway`+`TapTransport`×3+`ExecutionPolicy` (cascada en orden congelado)+`CandleRateLimiter` (D-7)+`DemoGuardPolicy` con alcances preservados por `origin` + **seam de coordenadas E4** (`TapCoordinateResolver` o CalibrationManager→core — sin él, la extracción deja sin especificar quién resuelve x/y) | **SÍ potencialmente** — STOP aquí si algún guard cambia de orden/alcance. Mitigación: cascada extraída veto-a-veto con test de equivalencia por veto (cobertura parcial ya existente: `PreregistroR12VetosTest`, `PriceFrozenDetectionTest`, `AssetMismatchGuardTest`…). **Derivación del conteo (revisión): 13 vetos = 14 sitios `Kind.VETO` en :2178-2470 menos el guard demo** — R29-B debe enumerar veto por veto (nombre, línea, mensaje de evento) ANTES de extraer | suite verde + equivalencia veto-a-veto + `ShadowModeTest` intacto | flag: gateway por defecto = impl estática actual |
| F4 Simulation Engine | `SimulatedOrderGateway` consume `ExecutionPolicy` (sin duplicar cascada); SHADOW delega; rate-limit sim local (D-7) | **SÍ por diseño**: SHADOW pasaría de "log y return" a ciclo simulado → cambia `shadow_journal.csv` (consumidores: validadores) → **requiere autorización + versión de esquema**. Alternativa conservadora: ciclo sim tras flag, log-only por defecto | `ShadowModeTest` ampliado; paridad de decisión sim vs real en fixtures r23* | flag off = comportamiento actual |
| F5 Split Gradle | `:core :market :strategy :risk :execution :simulation :observability :persistence :ui :adapters` + `research` JVM | No (build config); riesgo de errores de dependencia → `api()/implementation()` explícitos | assembleDebug + suite desde raíz | settings.gradle a 1 módulo |
| F6 Baja de estáticos | eliminar `OverlayService.instance`→`HudPort`; último estático ATAS | No si F1-F3 se completaron | grep estáticos de negocio = 0; suite verde | revert por consumidor |

Orden justificado: observabilidad (riesgo bajo) → core puro → ejecución (el objetivo D-6) → simulación (depende de F3) → split físico → limpieza. Los estáticos de ATAS se degradan por etapas, nunca big-bang.

---

## 7. Qué NO se migra (nunca o corto plazo) [PROPUESTA justificada]

1. **Interior de las 14 estrategias y sus parámetros** (reglas 1-2): `evaluateStrategySignalWithReason` `:1061-1553`, umbrales, `MarketTickFilters` — solo seam de delegación (P2); el enum puede salir de `AutoDrawEngine.kt:4-22` (clase de dibujo) por movimiento puro.
2. **Orden de la cascada de 13 vetos** (`:2178-2470`): cadena congelada; reordenar = cambiar comportamiento (regla 15).
3. **Esquemas CSV de journals** (29/11 columnas) y **algoritmo de `decisionId`**: los comparadores live↔replay y la paridad dependen de ellos.
4. **Heurística demo/real** (`ATAS:701-707/:841-847`, default true): crítica y única fuente; solo se envuelve.
5. **Ruta root-tap y autoHeal** (`ATAS:238-243, 285-358, 379-390`): comportamiento específico de dispositivo (MIUI) no reproducible en auditoría (R3 §3, no-demostrables); envolver, no reescribir.
6. **Valores por defecto de RiskManager** (`absoluteEquityFloor` 20M COP `:131`, YOLO `:746-760`): parámetros congelados.
7. **Token en prefs sin cifrar**: cambiar a EncryptedSharedPreferences altera runtime/backups; corto plazo NO (solo documentado, R2 riesgo 6).
8. **VisionAnalyzer** (1.252 LOC legacy, `onNewFrame` sin llamadores): NO migrar; congelar in situ (regla 7 prohíbe eliminarlo).
9. **Composition root en OverlayService** (corto plazo): reemplazar el wiring implica re-orquestar todo; solo se extrae `HudPort`.

---

## 8. Matriz de trazabilidad clase → dominio [VERIFICADO ubicación; PROPUESTA destino]

| Clase actual (LOC) | Dominio objetivo | Nota |
| :-- | :-- | :-- |
| TradingEngine (2970) | core (orquestación) → split F3: cascada→execution, `when`→strategy | **AMBIGUA estructural**: es 3 dominios hoy |
| OverlayService (2466) | ui | composition root hoy |
| SyntheticCandleEngine (1540) | strategy | emisor de señales; JVM-instantiable |
| VisionAnalyzer (1252) | — NO MIGRAR | legacy sin llamadores (`onNewFrame`), R2 §2.4 |
| RiskManager (1341) | risk | `RiskManager(null)` ya aísla |
| TradeDrawHttpBridge (985) | adapters | 28 endpoints; `/trade` re-ruta por D-7 |
| AutoTradeAccessibilityService (930) | adapters (sensor+transports) | estado estático → puertos §5 |
| BinomoWebSocketClient (869) | market | |
| CustomDrawingView (701) | ui | |
| AdaptiveLearningEngine (575) | strategy | veto en cascada; no a research |
| BinomoAuthActivity (400) | ui | escritura de token → CredentialSink |
| AgentChatOverlay (388) | ui | mutación del motor → ModeController |
| ModelPickerDialog (222) | ui | diálogo sobre overlay (misma familia que AgentChatOverlay) — **añadido por revisión (E1), faltaba en la matriz original** |
| TradeJournalLogger (385) | persistence | esquemas congelados |
| AIClient (363) | adapters | IA remota |
| CalibrationManager (343) | adapters | **AMBIGUA + seam E4**: coordenadas de tap; queda junto a transports. **PERO** `TradingEngine` (core/orquestación) resuelve coordenadas DENTRO de la cola de la cascada (`getRealScreenDimensions` `:1971-1972`/`:2462`; `getBuyCoordinates/getSellCoordinates` `:2465-2467`) — violación core→adapters con las reglas de §3.1. F3 exige (a) reclasificarla a core (matemática con prefs, testeable JVM al estilo `RiskManager(null)`) o (b) puerto P1 `TapCoordinateResolver` (interfaz core, impl adapters, inyectado al gateway). Sin esto, la extracción F3 tiene un seam sin especificar justo en el límite de despacho que D-6 ataca |
| health/HealthSupervisor (260) | observability | ya contrato puro |
| TradeEventLog (247) | observability | |
| StrikeCalculator (235) | ui | geometría de strikes |
| MarketInoperabilityDetector (216) | strategy | **AMBIGUA**: calidad de feed vs veto; va con vetos |
| MarketTick (197) | core | incluye WebSocketState |
| AutoDrawEngine (195) | ui | **AMBIGUA**: hospeda el enum de estrategias (movido a strategy/core en F2) |
| AutonomousAgentController (174) | core | |
| MainActivity (169) | ui | |
| ChartViewportController (164) | ui → **revisar (E3)** | **AMBIGUA (E3)**: su ctor toma `riskManager: RiskManager?` (`:17`) y lee `hasPendingTrade` (`:41`/`:122`) — contradice la prohibición ui→risk. Opción A: reclasificar a core (lógica de anotación de gráfico que lee estado de riesgo vía puerto). Opción B: puerto de solo-lectura `RiskStateView` en core. Decidir en R29-B F2 |
| MarketRegimeClassifier (153) | strategy | |
| TickFrameParser (152) | market | |
| TradeLearningEngine (129) | strategy | |
| DebugVisualizer (129) | ui | |
| DrawShape (108) | ui | |
| PayoutReader (103) | market | |
| ActiveInstance (86) | core | |
| ModeEventJournal (84) | observability | |
| HudCoherence (84) | ui | |
| HudSnapshot (75) | ui | |
| ColorWheelView (75) | ui | |
| AssetRicMapper (66) | core | |
| TemplateManager (64) | ui | |
| CrashLogger (56) | observability | |
| AutonomousWatchdog (55) | core | |
| TickVelocityLabel (51) | core | **AMBIGUA**: presentación pura usada por HUD y journals |
| BrokerDetector (31) | adapters | |
| TradingTool (28) | ui | enum de herramientas de dibujo |
| CandleTimeframe (25) | core | |
| ui/theme Color/Theme/Type | ui | 3 archivos |
| **Python/scripts** | | |
| validation_engine/ (7 módulos), tests/ (43 `def test_`), scripts/backtest, scripts/validation, scripts/analysis, run_validation_pipeline.py | research | fuera de la APK |
| ReplayBacktestTest, ReplayLatencyModeTest, LiveReplayParityTraceabilityTest, ReplayRegimeVocabularyTest | research (harness JVM) | reutiliza 4 funciones puras (R3 §2.8a) |
| fast_trader_cli.py, scripts/td_supervisor.sh, _probe_*.js, scripts/ticks/record_ticks.js | adapters/ops | tooling de operador/sondas; no módulo |

Conteo (corregido por revisión, E1): **47/47** = 43 planas (42 + `ModelPickerDialog` añadido) + `health/HealthSupervisor` + 3 `ui/theme/`. La línea original ("44 planas", sin ModelPickerDialog) era aritméticamente inconsistente (44+1+3=48≠47). AMBIGUAS marcadas: CalibrationManager, MarketInoperabilityDetector, TickVelocityLabel, AutoDrawEngine, ChartViewportController (E3) (+TradingEngine como ambigua estructural).

---

## 9. Contratos que NO existen hoy y deben crearse en R29-B [VERIFICADO su ausencia (0 interfaces en main) + PROPUESTA]

**P0** (prerrequisitos de toda frontera; seguridad/paridad):
1. `OrderGateway.dispatch(OrderRequest): OrderReceipt` + `TapTransport` (D-6: 3 transports).
2. `ExecutionPolicy.evaluate(ExecContext): VetoResult` — cascada congelada extraíble + `DemoGuardPolicy` unificada (4 guards hoy).
3. `BalanceSource.read(): BalanceReading` — desacopla Risk/motor de los estáticos ATAS (insumo de guards).
4. `EventSink.add(Event)` — TradeEventLog tras interfaz (desacoplamiento barato, riesgo bajo).
5. `CredentialProvider/CredentialSink` — parte el bus `TradeDraw_WSConfig`.

**P1** (habilitan split físico):
6. `MarketDataFeed.subscribe(ric): TickStream` + `FeedHealth` (formaliza el wiring `OverlayService:339-366`).
7. `JournalWriter/JournalReader` (Trade/Shadow/Mode; esquemas congelados).
8. `HudPort` + `ModeController.set(mode, origin)` (rompe ciclo Engine↔OverlayService; audita modos).
9. `CapitalPolicy.canExecuteTrade(...)` (Risk sin estáticos).
10. `HealthReportSource` (ya casi existe: Snapshot/Report puros).
- **Añadido (E4, revisión):** `TapCoordinateResolver` — puerto P1 (interfaz en core, impl en adapters) para la resolución de coordenadas de tap que hoy hace `TradingEngine` vía `CalibrationManager` dentro de la cascada (`:1971-1972`, `:2462-2467`); alternativa a reclasificar CalibrationManager a core. Decidir en F3.

**P2** (capacidades nuevas, tras P0-P1):
11. `SimulatedOrderGateway` + `SimClock` + `SimLedger` (D-7: rate-limit local de sim).
12. `StrategyEvaluator.evaluate(...)` — seam de delegación sobre el `when` congelado (sin reorganizarlo).
13. `SettingsStore` tipado por almacén (5/7 ya mono-propietario).

---

## 10. Qué domina el Simulation Engine de la semilla SHADOW [VERIFICADO (rama actual) + PROPUESTA (destino)]

- **Domina y hereda:** la semántica de la rama SHADOW (`TradingEngine.kt:2493-2518`): decisión autorizada sin tap; contador `shadowOrdersGenerated` (`:416-418`); `shadow_journal.csv` (11 col., `TradeJournalLogger.kt:227-228`); evento `SHADOW_DECISION` con `tradeId=null` (`ShadowModeTest.kt:48-62`); no toca `RiskManager` (`ShadowModeTest.kt:96-112`).
- **Domina y SEPARA (D-7):** la mutación de `lastExecutedCandleEpochMinute` (`:2515-2517`) — hoy la sim consume la vela real; en el objetivo ese estado vive en `ExecutionPolicy` (ejecución) y la sim lleva su propio rate-limit local.
- **NO domina:** la cascada de vetos (la REUTILIZA vía `ExecutionPolicy` extraída; la duplicación de orquestación en `ReplayBacktestTest` es el antipatrón documentado a no repetir, R3 §2.8c) ni nada de Adapters/ATAS (invariante cero-tap ya verificado: `return :2518` precede a toda primitiva; 9 chequeos de modo sin despacho en SHADOW, R3 §2.1c).
- **Nuevo ciclo simulado (P2):** fill al precio del tick, resolución a +60 s, WIN/LOSS/TIE en ledger propio — patrón `ReplayBacktestTest.resolveTrade` (`:137-146`) — sin slippage declarado y con esquema versionado si cambia el journal (punto de STOP regla-15, F4).

---

## 11. Límites explícitos de esta ronda y de R29-B

- R29-A R4: cero cambios de código/estructura; este documento es la única escritura.
- R29-B no autorizado aquí a: activar AUTONOMOUS, conectar sim↔broker, operar dinero real, sustituir mecanismos de ejecución, tocar estrategias/parámetros/resultados, commitear el working tree R27/R28 (decisión pendiente del operador, F0).

## 12. Criterios de aceptación de la ronda

- [x] 12 dominios evaluados uno a uno con veredicto y evidencia R2/R3 (§3); 2 fusiones y 1 división justificadas.
- [x] Por dominio: responsabilidades, límites (qué NO hace), contratos con nombre/campos concretos, dependencias permitidas/prohibidas, eventos→`TradeEventLog.Kind`, estados, identificadores (§2, §3.1).
- [x] (a) Matriz de trazabilidad 47 clases + Python/scripts con ambiguas marcadas (§8). (b) Mapa estáticos→objetivo con orden y riesgo (§5). (c) 3 vías de tap + guard cuadruplicado (§4). (d) Semilla SHADOW→Simulation sin tocar ciclo real (§10). (e) Contratos P0/P1/P2 priorizados (§9). (f) 7 fases con riesgo regla-15/aceptación/rollback (§6). (g) No-migrables con justificación (§7).
- [x] Corrección al enunciado documentada: `tradeId` NO es determinista (`TradingEngine.kt:2524`, UUID aleatorio); `AutoTradeMode` tiene 4 valores, no 3.
- [x] Sin módulos vacíos propuestos (Analytics fusionado por ausencia de código propio); sin contradicciones con R2/R3/D-6/D-7; sin secretos; sin commits.

## 13. Veredicto

**RONDA 4 COMPLETADA — DISEÑO APROBADO PARA REVISIÓN.** La arquitectura objetivo de TradeDraw se sostiene sobre el proyecto real: **11 dominios** (Core, Market Data, Strategy, Risk, Execution, Simulation, Research[+Backtesting], Observability[+Analytics], UI, Adapters, Persistence), un único estilo de inyección (constructores + lambdas + `ActiveInstance`, ya probado en Market Data), cero frameworks nuevos, y los estáticos convertidos en puertos por etapas con el riesgo máximo concentrado en F3 (frontera de ejecución, D-6/D-7). Los 5 contratos P0 (`OrderGateway`, `ExecutionPolicy`, `BalanceSource`, `EventSink`, `CredentialProvider/Sink`) son suficientes para iniciar R29-B sin tocar estrategias, esquemas ni parámetros congelados. Bloqueos para R29-B: (1) destino del working tree R27/R28 sin commit (bloquea refactor de archivos tocados), (2) CI sin tests (F0), (3) autorización explícita para los dos cambios de comportamiento planificados: re-ruta de `/trade` por modo (D-7) y evolución de SHADOW a ciclo simulado (F4, esquema de journal versionado).

---

## 14. Nota del supervisor — enmiendas aplicadas de la revisión independiente (09:05Z)

La revisión re-verificó ~40 claims cargantes (todos correctos) y halló 5 defectos corregidos in situ arriba:

- **E1** — Matriz §8 estaba 46/47: faltaba `ModelPickerDialog` (222 LOC) y el conteo "44 planas" era inconsistente (44+1+3=48≠47). Corregido (47/47 = 43 planas + health + 3 theme).
- **E2** — Alcance de `HudPort` subestimado: `OverlayService.instance` no es solo motor/ATAS — RiskManager ×6 (escrituras risk→ui `updateHUDView`), `BinomoWebSocketClient:91` (arista market→core→ui), `ChartViewportController:154`. F6 sería inseguro con el mapa original. Fila §6 ampliada; añadidos `ATAS.instance` ×26 y nota de disposal de listeners (`onAssetUpdatedListener` vivo, `onBalanceUpdatedListener` muerto).
- **E3** — `ChartViewportController` asignada a ui contradice la prohibición ui→risk (ctor toma `RiskManager?` `:17`, lee `hasPendingTrade` `:41/:122`). Marcada AMBIGUA con 2 opciones de resolución.
- **E4** — core→adapters en la ruta de ejecución: `TradingEngine` resuelve coordenadas vía `CalibrationManager` dentro de la cascada (`:1971-1972`, `:2462-2467`). F3 necesita reclasificación o el nuevo puerto P1 `TapCoordinateResolver` (añadido a §9). Seam sin especificar en el punto exacto que D-6 ataca.
- **E5** — Arista research→strategy: las funciones puras `motivoBloqueoPorAntiPatron`/`mejorCoincidenciaEnMemoria` viven en `AdaptiveLearningEngine` (strategy). Regla de dependencias de research corregida con 2 opciones de resolución.
- **Menores:** `showManualFallbackAlert` está en `TE:2050` (no :2049); derivación del conteo de vetos añadida a F3 (13 = 14 sitios `Kind.VETO` − guard demo); entregable de 239 líneas (denso, no defecto); gap pendiente para R29-B: inventario veto-por-veto (nombre, línea, mensaje) antes de extraer la cascada.

**Confirmado por la revisión:** los 2 bloqueadores de R29-B (working tree R27/R28 sin commit; CI sin tests) y los 2 puntos de autorización (re-ruta `/trade` por modo; SHADOW→ciclo simulado) son reales.
