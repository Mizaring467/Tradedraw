# ROUND 04 — Informe de Fase F4 (Simulation Engine) & Gate G4

**Fecha:** 2026-11-10 · **Ejecutor:** AGY (Sesión visible / Solo Routine) · **Rama:** `r10-night`  
**Referencia:** `loops/r29a/ROUND_06_MIGRATION_PLAN.md` §5 (F4: T-4.1 a T-4.3), `td-r29b-mission.md`

---

## 1. Resumen Ejecutivo

En la Fase F4 se implementó el **motor de simulación (Simulation Engine)** para el modo SHADOW y pruebas deterministas sin tocar brokers reales:

- **T-4.1 (SimClock y SimLedger):** Se crearon contratos e implementaciones puras en `simulation/SimClock.kt` (`ManualSimClock`, `SystemSimClock`) y `simulation/SimLedger.kt` (`SimLedger`), permitiendo la apertura, resolución a 60s, liquidación de PnL con payout y métricas agregadas (winRate, balance simulado, conteos de WIN/LOSS/TIE) en JVM pura.
- **T-4.2 (SimulatedOrderGateway):** Se implementó `simulation/SimulatedOrderGateway: OrderGateway` reutilizando las políticas de ejecución y desacoplado de hardware. Proporciona llenado inmediato al precio del tick actual, encolado de órdenes pendientes y resolución determinista tras 60 segundos de expiración con eventos `SHADOW_DECISION` y `TRADE_RESULT`.
- **T-4.3 (SHADOW con Ciclo Simulado & Esquema v2 Versionado - Decisión P-4 Aprobada):**
  - Se introdujo el esquema **v2 versionado** en `persistence/ShadowJournal.kt` y `TradeJournalLogger.kt`.
  - Invariante de compatibilidad hacia atrás: El esquema v1 de 11 columnas permanece inalterado; el esquema v2 añade 4 columnas al final (`schema_version,sim_result,sim_exit_price,sim_balance`).
  - Lector tolerante en `ShadowJournal`: interpreta tanto filas históricas v1 de 11 columnas como nuevas filas v2 de 15 columnas.
  - En `TradingEngine`, la delegación de SHADOW hacia `SimulatedOrderGateway` se gobierna mediante el flag `shadowSimulationEnabled = false` (apagado por defecto conforme a la autorización del propietario y de la misión). Con el flag apagado, el comportamiento es exactamente idéntico al actual (log-and-return con cabecera v1).

---

## 2. Evidencia de Verificación y Suites de Prueba

### 2.1 Nuevas Suites de Prueba de F4
- `SimLedgerTest`: 5 tests unitarios verificando saldo inicial, trade ganador con payout, trade perdedor con deducción de stake, trade en empate y avance de reloj simulado.
- `SimulatedOrderGatewayTest`: 3 tests unitarios validando despacho simulado, resolución a 60s con cálculo de PnL y rechazo en modo DISABLED.
- `ShadowJournalSchemaV2Test`: 4 tests validando invariante de no-reordenamiento entre v1 y v2, tolerancia de filas v1 en parser, lectura de campos simulados v2 y roundtrip de formateo.
- `ShadowModeTest`: 4 tests existentes verificando que el modo SHADOW no contamina `RiskManager` ni genera `trade_id` real.
- `ModeTransitionAuditTest`: Verificado limpio sin asignaciones directas de modo.

### 2.2 Resultados del Gate G4
- **Suite JVM Completa:**
  - Comando: `BACKTEST_CSV=loops/r23c_phase3_validation/window_01/ticks_live.csv BACKTEST_OUT=$(mktemp) ./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
  - Total test cases: **626**
  - Fallos: **0**
  - Errores: **0**
  - Omitidos: **0**
  - Archivos XML de reporte: **70**
- **Suite Python:**
  - Comando: `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=.:validation_engine python -m unittest discover -s tests`
  - Total tests: **43**
  - Resultado: **OK** (0.394s)
- **Integridad CRLF:** `OverlayService.kt` preservado al 100% (2468 líneas CRLF, 0 LF).

---

## 3. Veredicto

**GATE G4: APROBADO.**  
Simulation Engine implementado y validado sin afectar el flujo productivo. Se procede a comitear F4 en `r10-night` e iniciar la Fase F5 (Gradle Multi-Module Split & Refactor de Arquitectura).
