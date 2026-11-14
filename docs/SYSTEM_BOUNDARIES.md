# Límites del Sistema — TradeDraw · R29-A

**Estado:** verificado estáticamente (lectura de código, Rondas 0–4). **No se ejecutó nada** en esta
fase de auditoría: ninguna afirmación de comportamiento runtime proviene de ejecución en dispositivo.
**Fase:** R29-A (2026-11-09) · **Rama:** `r10-night` @ `c934835`
**Fuentes:** [R3 aislamiento](../loops/r29a/ROUND_03_ISOLATION_AUDIT.md) (principal),
[R2 inventario](../loops/r29a/ROUND_02_COMPONENT_INVENTORY.md),
[R4 arquitectura objetivo](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md).

Convención: **REAL** = existe frontera estructural (paquete/interfaz/inyección/wiring) que permite
sustituir o probar el dominio de forma independiente. **NOMINAL** = el dominio existe como nombre o
clase, pero comparte proceso, estado estático y llamadas directas con los demás. Citas `ruta:línea`
verificadas en el código del working tree (HEAD `c934835` + trabajo R27/R28 sin commit).

---

## 1. Qué está aislado de verdad (REAL o parcialmente real)

| Dominio | Componentes | Por qué es real (evidencia) |
| :--- | :--- | :--- |
| **Market Data** | `BinomoWebSocketClient` (869 LOC), `TickFrameParser`, `PayoutReader`, `MarketTickFilters` | ÚNICO dominio con wiring por inyección y punto de sustitución natural: listeners `onTick` + providers-lambda de frescura cableados en `OverlayService.kt:339-366`; URL del broker configurable por prefs (`ws_endpoint_url`, `BinomoWebSocketClient.kt:25`). PERO su token/endpoint vienen de prefs escritas por la UI (`BinomoAuthActivity.kt:108-111`) — ver §4. |
| **Backtesting / Research** | `ReplayBacktestTest` (633 LOC, JVM), `validation_engine/` (7 módulos Python), `scripts/backtest/*.py` | Vive FUERA de la APK, re-ejecutable sin Android: instancia `SyntheticCandleEngine` solo (`ReplayBacktestTest.kt:268-271`) y reutiliza 4 funciones puras del motor (`calcularPoderesDeMercado` `TradingEngine.kt:2903`, `motivoVetoPorAgotamientoDeCompra` `:2961`, `motivoBloqueoPorAntiPatron`/`mejorCoincidenciaEnMemoria` `AdaptiveLearningEngine.kt:540/:561`, `generateDeterministicDecisionId` `:918-920`). No instancia `TradingEngine` (grep → 0). |
| **Health (diagnóstico puro)** | `health/HealthSupervisor.kt` (260 LOC) | Máquina de estados READY/DEGRADED/BLOCKED sobre `Snapshot` inmutable de 14 campos (`:68-84`) evaluada con `evaluateFromSnapshot` pura (testeable JVM sin Android, 20 testcases). **Solo alimenta `GET /status`** (`OverlayService.kt:114`, `TradeDrawHttpBridge.kt:619`); ningún guard de trading lo consume. Umbrales: balance obsoleto diagnóstico = 10 s, separado del guard del motor de 3 s (`BalanceFreshnessSeparationTest`). |

Un cuarto caso fronterizo: `RiskManager` es instanciable sin Android (`RiskManager(null)`,
`RiskManagerTest.kt:13`) — separable en test, pero en producción lee 5 puntos de estáticos de ATAS
(`:724/:934/:1108/:1144/:1331`) → aislamiento de test real, aislamiento de producción NOMINAL.

## 2. Qué es solo nominal (7 dominios)

| Dominio | Estado | Evidencia decisiva |
| :--- | :--- | :--- |
| Android UI | NOMINAL | Sin paquete `ui/` (47 clases en un paquete plano + `health/`); la UI construye el grafo entero (`OverlayService.kt:285-366`) y muta el motor (`AgentChatOverlay.kt:365-371` cambia estrategia/modo). |
| AccessibilityService (ATAS) | NOMINAL | Su `companion object` es el bus de estado global: 88 refs externas en 11 archivos consumen sus estáticos (`.instance` ×26, `.latestObservedBalance` ×14, `.latestObservedAsset` ×10…). Es a la vez sensor, actuador y almacén de estado. |
| TradingEngine | NOMINAL | 2.970 LOC con estrategia + vetos + ejecución + liquidación + journals en una clase; ciclos estáticos Engine ↔ OverlayService y → ATAS (`TE:2049-2050`, `:2550`). |
| Strategy Engine | NOMINAL | Lógica de 14 estrategias = `when` de ~490 líneas DENTRO de TradingEngine (`:1061-1553`); el enum vive en una clase de dibujo (`AutoDrawEngine.kt:4-22`). |
| Risk Engine | NOMINAL | Ver nota en §1: `RiskManager(null)` aísla en test; producción acoplada por estáticos; su config la muta la UI vía setters (`OverlayService.kt:1079`). |
| Persistencia | NOMINAL | Sin Room/DataStore/SQLite/EncryptedSharedPreferences (grep → 0); `TradeDraw_WSConfig` es bus UI↔MarketData; journals = objects estáticos con 32 llamadas en 6 archivos. |
| Simulación | NOMINAL | SHADOW es una rama de ~26 líneas (`TE:2493-2518`) que registra y hace `return`; no existe simulador de órdenes (confirma bloqueo R28-B, `loops/r28b_autonomy/R28B_BLOQUEO_AISLAMIENTO.md`). |

Resumen: **aislamiento nominal en 7/10 dominios; parcialmente real en 3** (Market Data, Backtesting,
Health-diagnóstico). No hay paquetes por dominio: `app/src/main/java/com/example/tradedraw/` plano +
`health/` + `ui/theme/` (Compose).

## 3. Límites NO demostrables sin dispositivo (quedan como hipótesis)

Verificados por análisis de código en R3, pero **no reproducibles en esta auditoría** (R3 §3 —
ninguna afirmación de comportamiento runtime):

1. **Filtrado MIUI de gestos sintéticos**: que `dispatchGesture` sea realmente filtrado por
   Binomo/MIUI (afirmado por comentarios del propio código `ATAS:211-216`, que declaran el root-tap
   como vía primaria). No reproducible sin dispositivo.
2. **Heurística demo en cuentas reales**: si la heurística de texto ("cuenta real"/"cuenta demo",
   `ATAS:701-707/:841-847`, matchea solo español) falla en la práctica con cuentas reales o idiomas
   no españoles. El default `true` la hace fail-open de facto (R3 §2.2).
3. **Distribución real TIE vs TIE_INCIERTO** en liquidación con el working tree (el cambio C.1 de
   R27 amplía la población de casos `TIE`; R3 §2.6). Requiere sesión medida en dispositivo.
4. **Timing real unbind→rebind en MIUI** y su efecto en la liquidación (`ActiveInstance` lo hace
   atómico, pero la ventana existe; R3 §2.6).
5. **Paridad live↔replay** de la suite insignia: `ReplayBacktestTest` corrió skipped en la última
   corrida registrada (env vars `BACKTEST_CSV`/`BACKTEST_DIR` ausentes; R3 §2.8d).
6. **Auto-heal en MIUI actual**: que `autoHealAccessibility` (`ATAS:379-390`) re-registre el
   servicio con `settings put secure` en la versión real de MIUI del dispositivo.

## 4. Las 4 vías de ejecución con sus guards — y qué falta

**Hecho verificado: NO existe frontera de ejecución.** 0 interfaces en todo `app/src/main/java`
(grep → 0, R3 §1). No hay `OrderGateway`/`Broker`/`Executor` (re-verificado negativamente en R2 y
R3). Cuatro fuentes despachan taps reales sin tipo común:

| # | Vía | Código | Guard(s) que pasa | Lo que NO pasa |
| :-- | :--- | :--- | :--- | :--- |
| 1 | **Root shell (PRIMARIA)** | `executeRootTap` → `su -c input tap x y` (`ATAS:323`; R2 citaba ":327" — línea de comentario; corregido en re-verificación de la Ronda 5); opera incluso con accesibilidad caída (`:238-243` + auto-heal) | `puedeDespacharTap` `ATAS:302` | Cascada de vetos, `canExecuteTrade`, modo |
| 2 | **Gesto de accesibilidad** (fallback) | `dispatchGesture` (`ATAS:576`; swipe `:621`) | `puedeDespacharTap` `ATAS:200` (y `:532` vía `performClickAt`) | Ídem |
| 3 | **Tap directo bridge/HUD** | `performClickAt` (`ATAS:523`) ← `/trade` (`bridge:152`), `/click` (`:416`), broadcasts firmados (`ATAS:397`), test UI | `puedeDespacharTap` `ATAS:532` | Ídem |
| §  | **Vía del motor (única con cascada completa)** | `executeHeadlessTrade` → `performClick` (`TE:2550`) | Guard demo del motor (solo-AUTONOMOUS, `TE:2183-2196`) + cascada de 13 vetos (`:2178-2470`) + `RiskManager.canExecuteTrade` (`:2424-2441`) | — |

- El guard demo está **cuadruplicado con alcances distintos**: motor `TE:2183` (solo AUTONOMOUS) +
  taps `ATAS:200/:302/:532` (siempre). Fuente única de `isDemoAccount`: heurística de texto UI,
  default `true` (§3.2).
- **`/trade` ignora el modo**: opera igual en DISABLED, SEMIAUTOMATIC o SHADOW (R3 §2.4); no consulta
  `canExecuteTrade` ni la cascada. **SHADOW no protege esta puerta.** `/click` además no requiere el
  servicio de accesibilidad (vía root `ATAS:230-243`).
- **Rate-limit de vela compartido**: SHADOW consume la vela real (`lastExecutedCandleEpochMinute`,
  `TE:2515-2517`) — estado de ejecución mutado por la simulación (hallazgo R3, D-7).
- **Qué falta:** `OrderGateway.dispatch(OrderRequest): OrderReceipt` + `TapTransport` ×3 +
  `ExecutionPolicy` (cascada en orden congelado) — contratos **PROPUESTA-R29-B** (R4 §4/§9; detalle
  en `loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md` §4/§9, no duplicado aquí).

## 5. Demo ≠ sandbox

**"DEMO" es una etiqueta leída del broker, no un entorno.** [VERIFICADO — R3 §2.2]:

1. **El feed es real siempre**: `wss://ws.binomo.com/` (`BinomoWebSocketClient.kt:25`) autenticado
   con el token de sesión real (36 caracteres) capturado por el WebView de login. El protocolo WS no
   transporta ningún flag demo/real que el código lea (grep `demo|practice` → 0).
2. **`isDemoAccount` solo se alimenta de texto de la UI del broker** (`ATAS:701-707/:841-847`,
   matchea solo español), con default `true` — fail-open si la heurística no observa.
3. **Segunda fuente de verdad**: `RiskManager` infere demo por saldo (`currentBal >= 1_000_000.0`,
   `RiskManager.kt:724-725`) — dualidad reconocida por el propio código (`TE:2167-2177`).
4. Los taps golpean la app real (`com.marketly.trading`) con coordenadas calibradas. No hay sandbox,
   ni simulador de órdenes, ni entorno de prueba en ningún punto de la cadena.

---

*Documento creado por la Ronda 5 de R29-A (edición aditiva; única escritura: este archivo + los 5
documentos listados en [R4](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md) y los informes de
`loops/r29a/`). Etiquetado por claim: VERIFICADO (con ruta:línea) / PARCIALMENTE VERIFICADO /
NO VERIFICADO / PROPUESTA-R29-B.*
