# Contratos de datos — TradeDraw · R29-A

**Alcance:** contratos de datos que existen HOY (verificados en código del working tree,
HEAD `c934835` + R27/R28 sin commit) y los PROPUESTOS para R29-B, marcados como tales.
**Regla de oro (regla 15 de la misión):** los esquemas y algoritmos congelados no se migran
equivale a no cambiarlos — cualquier extracción debe preservarlos byte a byte.
**Fuentes:** [R2 inventario](../loops/r29a/ROUND_02_COMPONENT_INVENTORY.md),
[R3 aislamiento](../loops/r29a/ROUND_03_ISOLATION_AUDIT.md),
[R4 arquitectura](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md).

---

## 1. Esquemas CSV congelados (journals de trades y shadow)

Escritor único: `TradeJournalLogger` (object) en `filesDir/TradeDraw_Audits/` (con sync a externo).
**Regla declarada en código:** nunca se reordena ni se borra una columna; lectores nuevos toleran
columnas desconocidas (`TradeJournalLogger.kt:20-21`).

| Journal | Esquema (header literal) | Evidencia |
| :--- | :--- | :--- |
| **trades** (29 columnas) | `timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason,signal_ts,tap_start_ts,tap_end_ts,candle_second_at_tap_end,price_at_signal,price_at_tap_end,decision_id,trade_id` | `TradeJournalLogger.kt:22` [VERIFICADO hoy] |
| **shadow** (11 columnas) | `timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason` — sin stake/saldo/resultado; `trade_id` ausente | `TradeJournalLogger.kt:227` [VERIFICADO hoy]; contrato `tradeId=null` en SHADOW fijado por `ShadowModeTest.kt:48-62` |

## 2. Journal de modos

`ModeEventJournal` (84 LOC): journal en archivo de transiciones de modo (origen incluido); alimenta
`/mode_log` y `/mode_journal` (`TradeDrawHttpBridge.kt:211-229`, `:818-843`). Anillo en memoria
dedicado a MODE_CHANGE (`MODE_CAPACIDAD=100`, `TradeEventLog.kt`). Contrato fijado por
`ModeTransitionAuditTest`, `ModeRingDedicadoTest`, `ModeChangeEventTest`.

## 3. TradeEventLog — 11 Kind congelados

Anillo de eventos en memoria (`TradeEventLog.kt`, object), expuesto por `GET /events`; consumido
por 6 capas (32 llamadas, R3 §1). `Kind` congelado (`TradeEventLog.kt:36-100`) [VERIFICADO hoy]:

`VETO, ORDER_STATE, TAP, ASSET, PAYOUT_DISCREPANTE, FEED_MUDO, INOPERABLE, MODE_CHANGE, FEED_SIN_TICKS, SHADOW_DECISION, MANUAL_INCREMENT`

Mapa a dominios objetivo (PROPUESTA-R29-B, R4 §2): VETO/TAP/ORDER_STATE→Execution·Risk;
ASSET/PAYOUT_DISCREPANTE→Adapters(sensor); FEED_MUDO/FEED_SIN_TICKS/INOPERABLE→Market Data·Strategy;
MODE_CHANGE/MANUAL_INCREMENT→Core; SHADOW_DECISION→Simulation.

## 4. Health Snapshot / Report (14 campos + toJson)

`health/HealthSupervisor.kt` [VERIFICADO hoy, `:68-84` + `:87-95`]:

- **`Snapshot`** (14 campos, defaults sanos): `accesibilidadEnlazada, wsConnected, feedFresh,
  socketTicks, priceFrozen, activoSinFeed, activeAsset, latestObservedAsset, balance,
  lastBalanceReadMs, hasPendingTrade, payoutDiscrepant, marketInoperable, cooldownSec`.
- **`Report`**: `state` (READY/DEGRADED/BLOCKED) + `reasons` + `sinceMs` + `dataSource`
  (WEBSOCKET_PURO vigente) + `notEvaluable` + **`toJson()`** (`:93`) — publica `GET /status`
  (sintaxis fijada por `StatusJsonStrictSyntaxTest`). Solo diagnóstico; ningún guard lo consume.

## 5. Formato de ticks CSV (entrada de replay)

`ts,ric,price` — formato esperado por `loadTicks` (`ReplayBacktestTest.kt:85-112`); los insumos
compatibles viven en `loops/r23*/window_*/ticks_live.csv` (34 archivos; distribución por directorio
corregida en R3 §7.2). La suite insignia corrió skipped por env vars ausentes (R3 §2.8d).

## 6. Identificadores: decisionId determinista vs tradeId aleatorio

| ID | Algoritmo | Congelado | Evidencia |
| :--- | :--- | :--- | :--- |
| `decisionId` | **Determinista**: `UUID.nameUUIDFromBytes("${ric}_${nonceMs}_${ACTION}")` — acepta segundos (<1e11) o milisegundos; clave siempre en ms | **SÍ** — la paridad live↔replay depende de él; límite documentado: colisión mismo activo+ms+acción | `TradingEngine.kt:918-920` [VERIFICADO hoy] |
| `tradeId` | **Aleatorio**: `UUID.randomUUID()` por orden real; `null` en SHADOW | Contrato fijado por test | `TradingEngine.kt:2524` [VERIFICADO hoy]; `ShadowModeTest.kt:48-62` |

Corrección documentada (R4 §2): el enunciado original de la misión hablaba de
"decisionId/tradeId deterministas" — eso es correcto **solo para `decisionId`**.

## 7. Los 7 almacenes SharedPreferences (escritores/lectores)

grep `getSharedPreferences` re-verificado hoy (R3 §2.7 como fuente; conteos de uso de R2 §2.6):

| Almacén | Escritores (capa) | Lectores (capa) | Veredicto |
| :--- | :--- | :--- | :--- |
| `TradeDraw_WSConfig` (6 usos) | UI (`BinomoAuthActivity:109/:125/:357/:385` — token, URL, cookie, deviceId) + Market Data (`BinomoWebSocketClient:205-215`) | `BinomoWebSocketClient:22`, `MainActivity:103` | **BUS de acoplamiento UI↔MarketData** — la clave es el contrato |
| `TradeDraw_RiskConfig` (1) | Solo `RiskManager` (17 claves vía setters `:85-185`) | Solo `RiskManager` | Encapsulado (pero setters invocados por UI, `OverlayService:1079`) |
| `TradeDraw_HUDConfig` (4) | Solo `OverlayService` | Solo `OverlayService` | Encapsulado |
| `TradeDraw_Config` (2) | Solo `OverlayService` (`saved_strategy` `:1018-1021`) | Solo `OverlayService` (`:313-315`) | Encapsulado (la estrategia fijada por `/strategy` o chat NO se persiste) |
| `TradeDraw_Calibration` (1) | Solo `CalibrationManager` | Solo `CalibrationManager` | Encapsulado |
| `TradeDraw_AIConfig` (1) | Solo `AIClient` | Solo `AIClient` | Encapsulado |
| `TradeDraw_Templates` (1) | Solo `TemplateManager` | Solo `TemplateManager` | Encapsulado |

Plano compartido adicional: `filesDir/TradeDraw_Audits/` (CSV §1), `adaptive_learning_state.json`,
journal de modos (§2) — escritos por objects estáticos, leídos por el bridge.
NO existe: Room, DataStore, SQLite, EncryptedSharedPreferences (grep → 0, R3 §2.7).

---

## 8. Contratos PROPUESTOS (PROPUESTA-R29-B — NO existen hoy)

**Ausencia verificada:** 0 interfaces en `app/src/main/java` (grep → 0, R3 §1). Los siguientes
contratos son diseño de [R4 §3.1/§9](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md) para R29-B;
**no se implementó nada aquí**. Resumen orientativo (el detalle normativo es R4):

| Contrato | Propósito | Prioridad |
| :--- | :--- | :--- |
| `OrderGateway.dispatch(OrderRequest): OrderReceipt` + `TapTransport` ×3 (root/gesto/bridge) | Frontera de ejecución D-6 (abstrae las 3 vías de tap reales) | P0 |
| `ExecutionPolicy.evaluate(ExecContext): VetoResult` + `DemoGuardPolicy` | Cascada congelada extraíble veto-a-veto + guard demo unificado (4 sitios hoy) | P0 |
| `BalanceSource.read(): BalanceReading` | Desacopla Risk/motor de los estáticos ATAS (insumo de guards) | P0 |
| `EventSink.add(Event)` | TradeEventLog tras interfaz (`Kind` congelado) | P0 |
| `CredentialProvider` / `CredentialSink` | Parte el bus `TradeDraw_WSConfig` sin cambiar claves ni esquema | P0 |
| `MarketDataFeed.subscribe(ric): TickStream` + `FeedHealth` | Formaliza el wiring actual de Market Data | P1 |
| `JournalWriter` / `JournalReader` | Journals tras interfaz, esquemas CSV congelados | P1 |
| `HudPort` + `ModeController.set(mode, origin)` | Rompe ciclo Engine↔OverlayService; audita modos | P1 |
| `CapitalPolicy.canExecuteTrade(...)` | Risk sin estáticos ATAS | P1 |
| `TapCoordinateResolver` | Resolución de coordenadas hoy dentro de la cascada (seam E4) | P1 |
| `SimulatedOrderGateway` + `SimClock` + `SimLedger` | Ciclo de orden simulado (semilla: rama SHADOW `TE:2493-2518`) | P2 |
| `StrategyEvaluator.evaluate(...)` | Seam de delegación sobre el `when` congelado | P2 |
| `SettingsStore` tipado | 5/7 almacenes ya mono-propietario | P2 |

Restricciones duras para R29-B (R4 §7): interior de las 14 estrategias, orden de la cascada de 13
vetos (`TE:2178-2470`), esquemas CSV (29/11 col.), algoritmo de `decisionId`, heurística demo/real,
ruta root-tap/auto-heal, defaults de RiskManager y el token en prefs (cifrado aplazado) — NO se
migran ni se reescriben.

---

*Documento creado por la Ronda 5 de R29-A. Etiquetado por claim: VERIFICADO (con ruta:línea) /
PARCIALMENTE VERIFICADO / NO VERIFICADO / PROPUESTA-R29-B.*
