# R21 — REPORTE DE CONSOLIDACIÓN MULTI-AGENTE (HERDR)

**Fecha:** 2026-10-15  
**Orquestador Principal:** AGY (`w1:p1`)  
**Agente Colaborador:** HERMES (`w1:p6`)  
**Workspace Herdr:** `w1`  
**Dispositivo Físico:** Redmi A1 (`UGU8KFUKM7E6CE8D` / `192.168.1.225:5555`)  
**SHA Base:** `4d433ed`  
**SHA Final:** `8fe142f`  
**Veredicto General:** **OBJETIVOS CUMPLIDOS AL 100% — PIPELINE TRAZABLE, SEGURO Y REPRODUCIBLE**

---

## 1. Resumen Ejecutivo de la Misión R21

La Ronda 21 (`R21`) tuvo como objetivo estricto transformar el pipeline de TradeDraw en una arquitectura **100% trazable, reproducible, observable y formalmente aislada**:
$$\text{DATOS} \longrightarrow \text{SEÑAL} \longrightarrow \text{DECISIÓN} \longrightarrow \text{EJECUCIÓN} \longrightarrow \text{CONFIRMACIÓN} \longrightarrow \text{LIQUIDACIÓN} \longrightarrow \text{JOURNAL}$$

Bajo el esquema de coordinación con mutex de archivos en [workflows/R21_COORDINATION.md](file:///C:/Users/heidy/Tradedraw/workflows/R21_COORDINATION.md), AGY lideró el núcleo Kotlin y arquitectura, mientras que HERMES ejecutó las auditorías forenses, herramientas Python de validación y el harness de integración continua.

---

## 2. Métricas Clave y Comparativa

| Dimensión | R20 (Base) | R21 (Final) | Delta / Estado |
|---|---|---|---|
| **Suite de Tests Unitarios** | 345 tests (32 clases) | **366 tests (37 clases)** | **+21 tests (+5 clases)** |
| **Fallos / Errores en Tests** | 0 fallos / 0 errores | **0 fallos / 0 errores** | **100% verde** |
| **Reproducibilidad en Worktree** | No automatizada en CI | **PASS (152.12s, 366 tests limpios)** | Certificado reproducible |
| **Trazabilidad de Intento** | Timestamps sueltos | **`decision_id` & `trade_id` end-to-end** | 29 columnas journal |
| **Comparador Live ↔ Replay** | No existía | **`compare_live_replay.py` determinista** | Jerarquía de 12 campos |
| **Replay con Latencia Real** | Solo Replay Ideal | **`ReplayLatencyModeTest` (Ideal vs Observed)** | Sin look-ahead bias |
| **Health Supervisor** | Guards dispersos | **`HealthSupervisor` (READY/DEGRADED/BLOCKED)** | Expuesto en `/status` |
| **Modo Shadow** | No existía formalmente | **`AutoTradeMode.SHADOW` + `shadow_journal`** | Cero riesgo de saldo |
| **Sanitización de Tokens** | Token en logcat posible | **Enmascaramiento irreversible `maskSecret`** | 100% Sanitizado (C1) |
| **Vetos Desajuste Pantalla↔Feed** | 61 vetos | **148 vetos activos** | **0 taps despachados** |
| **Balance Cuenta Demo** | 38.054.735,36 COP | **38.054.735,36 COP** | **0 céntimos alterados** |
| **Estado Final del Bot** | AUTONOMOUS | **`DISABLED` (Fail-Closed)** | `orderState=NINGUNA` |

---

## 3. Estado de Cada Frente

### Fase 0: Auditoría Forense de Balance (+80.000 COP)
- **Dictamen:** **B) NO ATRIBUIDO AL BOT PERO ACOTADO**.
- **Evidencia Forense:**
  - TradeDraw descartado de forma terminante: 0 taps despachados, W/L 0/0, journal sin registros durante todo el 14-Oct-2026.
  - El guard pantalla↔feed bloqueó de forma ininterrumpida (61 intentos en R20, 148 en R21).
  - Ventana temporal acotada: entre 16:34:45 COT y 19:13:00 COT del 14-Oct-2026.
  - El monto (+80.000 COP) corresponde matemáticamente a 1 trade WIN en Demo con stake base de 100.000 COP y 80% de payout ($100.000 \times 0.80 = +80.000$ netos), correlacionado con manipulación humana en la UI de Binomo.
- **Entregable:** [workflows/rounds/r21_balance_incident_hermes.md](file:///C:/Users/heidy/Tradedraw/workflows/rounds/r21_balance_incident_hermes.md).

---

### Frente A: `decision_id` / `trade_id` End-to-End
- **Diseño:**
  - `decision_id`: Generado mediante UUID v4 monotónico en cuanto el motor evalúa una decisión candidata. Sobrevive y se propaga por: `SIGNAL` $\to$ `DECISION` $\to$ `VETO` $\to$ `DISPATCH` $\to$ `TAP_START` $\to$ `TAP_END`.
  - `trade_id`: Solo se genera si la orden es despachada fácticamente. Sobrevive por: `ENVIADA` $\to$ `CONFIRMADA` $\to$ `NO_PROCESADA` $\to$ `WIN/LOSS/TIE` $\to$ `SETTLEMENT` $\to$ `JOURNAL`.
  - Veto técnico garantiza que **NO** se inventa `trade_id`.
- **Esquema de Journal (29 Columnas):**
  - Incorporadas columnas 28 (`decision_id`) y 29 (`trade_id`) en formato append-only.
  - Retrocompatibilidad absoluta: lectores toleran filas antiguas de 27 columnas y filas nuevas de 29 columnas sin degradación.
- **Validación Unitara:**
  - [app/src/test/java/com/example/tradedraw/TradeIdEndToEndTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/TradeIdEndToEndTest.kt) (6 tests pasando al 100%).
  - [app/src/test/java/com/example/tradedraw/JournalLatencySchemaTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/JournalLatencySchemaTest.kt) adaptado y verificado.

---

### Frente B: Comparador Live ↔ Replay
- **Implementación:** [scripts/validation/compare_live_replay.py](file:///C:/Users/heidy/Tradedraw/scripts/validation/compare_live_replay.py).
- **Características:**
  - Mapeo 1:1 por `decision_id`.
  - Jerarquía de divergencia de 12 campos: `timestamp` $\to$ `asset` $\to$ `price` $\to$ `candle_second` $\to$ `market_regime` $\to$ `trend` $\to$ `dist_support` $\to$ `dist_resistance` $\to$ `confluence` $\to$ `strategy` $\to$ `direction` $\to$ `veto` $\to$ `decision`.
  - Reporte de primera causa raíz causal determinista (`first_divergence`).
- **Resultados de Validación:**
  - Fixtures de prueba: 5 decisiones.
  - Matches exactos: **4 (80.0%)**.
  - Divergencias detectadas: **1 (20.0%)** por `dist_support` fuera de tolerancia ($12.4$ vs $14.1$).
- **Entregables:**
  - [workflows/rounds/r21_replay_comparison_report.md](file:///C:/Users/heidy/Tradedraw/workflows/rounds/r21_replay_comparison_report.md)
  - [workflows/rounds/r21_replay_comparison.json](file:///C:/Users/heidy/Tradedraw/workflows/rounds/r21_replay_comparison.json)

---

### Frente C: Replay con Latencia Real (Ideal vs Observed)
- **Implementación:** [app/src/test/java/com/example/tradedraw/ReplayLatencyModeTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/ReplayLatencyModeTest.kt).
- **Metodología:**
  - `IDEAL`: Entrada en el tick exacto de la decisión.
  - `OBSERVED_LATENCY`: Entrada en el primer tick disponible tras `tap_end_ts` (utilizando la latencia real observada del hardware).
  - Regla determinista estricta sin slippage artificial ni look-ahead bias.
- **Hallazgos:**
  - Cuantificación precisa de deslices de precio por latencia mecánica de toque (~150-350ms en Redmi A1).
  - Permite medir robustez de estrategia frente a micro-variaciones antes del fill real.

---

### Frente D: Health Supervisor Unificado
- **Implementación:** [app/src/main/java/com/example/tradedraw/health/HealthSupervisor.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/health/HealthSupervisor.kt).
- **Estados:** `READY`, `DEGRADED`, `BLOCKED`.
- **Invariantes Evaluados:**
  - Feed stale (`FEED_STALE`), ausencia de ticks (`NO_TICKS`), activo sin feed (`ASSET_NO_FEED`).
  - Mismatch gráfico↔feed (`SCREEN_FEED_MISMATCH`).
  - Servicio de Accesibilidad (`ACCESSIBILITY_DOWN`).
  - Legibilidad de balance (`BALANCE_UNREADABLE`).
  - Congelamiento de precios (`PRICE_FROZEN`).
- **Integración:**
  - Instanciado en `OverlayService` y alimentado periódicamente por el loop de ticks.
  - Expuesto en `/status` bajo el objeto `health`: `{ "state": "BLOCKED", "reasons": ["SCREEN_FEED_MISMATCH", "ASSET_NO_FEED"], "sinceMs": ... }`.
- **Validación Unitara:** [app/src/test/java/com/example/tradedraw/health/HealthSupervisorTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/health/HealthSupervisorTest.kt) (4 tests en verde).

---

### Frente E: Shadow Mode Formal
- **Implementación:**
  - `AutoTradeMode.SHADOW` en [app/src/main/java/com/example/tradedraw/TradingEngine.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt).
  - Integración en HUD en [app/src/main/java/com/example/tradedraw/OverlayService.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt).
  - Persistencia aislada en `shadow_journal.csv` vía [app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt).
- **Garantías de Aislamiento:**
  - Procesa ticks en vivo y ejecuta análisis y vetos idénticos a `AUTONOMOUS`.
  - Genera `decision_id` para trazabilidad de lo que habría hecho.
  - **CERO TAPS:** No invoca `performClickAt()`.
  - **CERO ÓRDENES ENVIADAS:** No muta `hasPendingTrade` ni `orderState`.
  - **CERO CONTAMINACIÓN:** No incrementa contadores W/L ni altera el balance de cuenta.
  - Emite evento `TradeEventLog.Kind.SHADOW_DECISION`.
- **Validación Unitara:** [app/src/test/java/com/example/tradedraw/ShadowModeTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/ShadowModeTest.kt) (4 tests en verde).

---

### Frente F: Seguridad y Sanitización C1 (Token Masking)
- **Implementación:** [app/src/main/java/com/example/tradedraw/BinomoAuthActivity.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/BinomoAuthActivity.kt).
- **Mecanismo:** Función `maskSecret()` irreversible que expone únicamente los últimos 4 caracteres para identificación operativa (e.g., `***abcd`) y retorna `[REDACTED]` si la longitud es inferior a 8 caracteres.
- **Garantía:** CERO tokens o credenciales completos en Logcat, HTTP logs o repositorios.
- **Validación Unitara:** [app/src/test/java/com/example/tradedraw/BinomoAuthSanitizationTest.kt](file:///C:/Users/heidy/Tradedraw/app/src/test/java/com/example/tradedraw/BinomoAuthSanitizationTest.kt) (2 tests en verde).

---

### Frente G: Reproducibilidad en Worktree Limpio (CI Harness)
- **Implementación:** [scripts/ci/verify_clean_worktree.py](file:///C:/Users/heidy/Tradedraw/scripts/ci/verify_clean_worktree.py).
- **Operación:**
  1. Crea un `git worktree` temporal totalmente aislado para el commit especificado.
  2. Detecta y replica `local.properties` (para `sdk.dir` en Windows).
  3. Ejecuta `:app:parseDebugLocalResources` y `:app:testDebugUnitTest`.
  4. Extrae métricas de los archivos XML de JUnit.
  5. Desmantela el worktree de forma atómica y segura mediante `safe_rmtree` (con limpieza de atributos de solo lectura).
- **Resultado:**
  - Veredicto: **PASS (REPRODUCIBLE)**.
  - Suite: **366 tests ejecutados** (0 fallos, 0 errores, 2 omitidos).
  - Tiempo de ejecución: **152.12 s**.
- **Entregable:** [workflows/rounds/r21_worktree_reproducibility_report.md](file:///C:/Users/heidy/Tradedraw/workflows/rounds/r21_worktree_reproducibility_report.md).

---

## 4. Estado Actual del Dispositivo en Vivo (Redmi A1)

Auditoría directa vía endpoint HTTP `/status` (`127.0.0.1:8080`) al cierre de ronda:

```json
{
  "app": "TradeDraw",
  "mode": "DISABLED",
  "lastModeChangeSource": "AUTONOMOUS -> DISABLED | source=bridge:/mode",
  "lastModeChangeTs": 1792034410859,
  "balance": 38054735.36,
  "feed": {
    "source": "websocket",
    "connected": true,
    "isFresh": true,
    "activeAsset": "ETHUSD-OTC",
    "rawSocketMessages": 13705,
    "socketTicks": 13664,
    "ultimoRicConTicks": "ETHUSD-OTC",
    "activoSinFeed": "AAVEUSD-OTC"
  },
  "risk": {
    "totalWins": 0,
    "totalLosses": 0,
    "hasPendingTrade": false,
    "orderState": "NINGUNA",
    "tapsDespachados": 0,
    "vetoTotal": 148,
    "vetoActivoDesajustado": 148,
    "ultimoVeto": "DESAJUSTE pantalla=\"Aave (OTC)\" ric=AAVEUSD-OTC feed=\"ETHUSD-OTC\""
  }
}
```

### Hallazgos del Runtime en Vivo:
1. **Protección Anti-Desajuste Implacable:** En 148 intentos de señal evaluados mientras la pantalla mostraba `Aave (OTC)` y el feed recibía `ETHUSD-OTC`, el guard aplicó **148 vetos**, resultando en **0 taps despachados** y **0 órdenes ejecutadas fuera de activo**.
2. **Inmutabilidad del Balance:** El balance permanece en **38.054.735,36 COP** (cero variación durante toda la operación de TradeDraw en R21).
3. **Parada Segura (Fail-Closed):** El motor ha quedado en estado **`DISABLED`**, con `hasPendingTrade = false` y `orderState = "NINGUNA"`.

---

## 5. Matriz de Evidencia Epistémica

| Declaración | Categoría | Evidencia / Justificación |
|---|---|---|
| "El bot no operó el 14-Oct" | **FACT** | Journal sin filas el 14-Oct, 0 taps despachados, W/L 0/0, 148 vetos activos. |
| "El salto de +80.000 COP fue un trade manual en Binomo" | **EVIDENCE** | Acotado a ventana sin taps del bot; monto coincide exactamente con $100.000 \times 80\%$. |
| "Causa exacta del salto de balance" | **MISSING PROOF** | No existe logcat de toques en la app oficial de Binomo (Android sandbox). Se mantiene abierto en Dictamen B. |
| "decision_id y trade_id se propagan sin inventar IDs" | **FACT** | 6 tests unitarios en `TradeIdEndToEndTest` verifican unicidad y contención ante vetos. |
| "La suite es 100% reproducible sin dependencias ocultas" | **FACT** | `verify_clean_worktree.py` ejecutado en clean worktree: 366 tests verdes en 152s. |
| "El bot quedó en parada segura" | **FACT** | `/status` reporta `mode=DISABLED`, `hasPendingTrade=false`, `orderState=NINGUNA`. |
