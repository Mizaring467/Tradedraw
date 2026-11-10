# ROUND 02 — Informe F2 (Extracción de Core Puro y Gate G2) · TradeDraw R29-B

**Fecha:** 2026-11-10T05:19Z · **Ejecutado por:** AGY (Sesión visible obligatoria / Solo Routine)
**Rama:** `r10-night`

---

## 1. Objetivos de F2
1. **T-2.1:** Crear paquete `com.example.tradedraw.core` y mover SIN editar cuerpos:
   - `MarketTick.kt` (data class `MarketTick`, enum `WebSocketState`, object `MarketTickFilters`, función `formatDynamicPrice`)
   - `CandleTimeframe.kt` (enum `CandleTimeframe`)
   - `AssetRicMapper.kt` (object `AssetRicMapper`)
   - `ActiveInstance.kt` (clase `ActiveInstance<T>`)
   - `AutoTradeMode.kt` (enum `AutoTradeMode`)
   - `TradeAction.kt` (enum `TradeAction`)
   - `AutonomousSubMode.kt` (enum `AutonomousSubMode`)
   - `OrderState.kt` (enum `OrderState`)
   - `AutoTradeStrategy.kt` (enum `AutoTradeStrategy` de 14 estrategias)
   - Mantener retrocompatibilidad transparente vía `CoreTypeAliases.kt` en `com.example.tradedraw`.
2. **T-2.2:** Extraer `generateDeterministicDecisionId` a `core/DecisionIds.kt` manteniendo función delegadora idéntica en `TradingEngine.Companion`.
3. **Gate G2:** Suite completa verde, paridad de IDs demostrada y `git diff -w` estrictamente restringido a imports, moves y delegaciones sin tocar lógica de negocio.

---

## 2. Evidencia de Ejecución

### 2.1 Suite JVM en Termux
- **Comando:** `BACKTEST_CSV=loops/r23c_phase3_validation/window_01/ticks_live.csv BACKTEST_OUT=$(mktemp) ./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
- **Resultado:** `BUILD SUCCESSFUL in 2m 26s`.
- **Parseo de XMLs (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):**
  - Archivos XML: 58
  - Total tests: 574 (573 previos + 1 nuevo test de paridad `paridadEntreTradingEngineYCoreDecisionIds`)
  - Fallos: 0
  - Errores: 0
  - Skipped: 0

### 2.2 Suite Python
- **Comando:** `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=.:validation_engine python -m unittest discover -s tests`
- **Resultado:** `Ran 43 tests in 0.317s` · `OK`.

### 2.3 Preservación de Invariantes y CRLF
- `OverlayService.kt`: Verificado con python `CRLF=2466, LF=0` (invariante de fin de línea preservado).
- `TradingEngine.kt`: Algoritmos, estrategias y cascada de 13 vetos intactos (0 diffs en cuerpos).
- Paridad de IDs: `DeterministicDecisionIdTest` aprueba paridad byte-a-byte entre `TradingEngine.generateDeterministicDecisionId` y `DecisionIds.generateDeterministicDecisionId`.

---

## 3. Dictamen de Gate G2
**GATE G2: APROBADO**
- Tipos de core desacoplados en `com.example.tradedraw.core`.
- `DecisionIds` operativo con delegación transparente.
- 574 tests JVM + 43 tests Python en verde.
- Listo para avanzar a **F1 (Contratos P0 Aditivos)**: `EventSink` (T-1.1), `BalanceSource` con dual-read (T-1.2), `CredentialProvider/Sink` (T-1.3) y `CapitalPolicy` (T-1.4).
