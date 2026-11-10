# TradeDraw · R29-B — STATE

| Campo | Valor |
| :--- | :--- |
| Fase actual | F4 — Simulation Engine (semilla SHADOW, P-4) |
| Tarea actual | T-4.1 (SimClock/SimLedger), T-4.2 (SimulatedOrderGateway), T-4.3 (SHADOW delega + esquema v2) |
| HEAD | Pendiente commit F3 en `r10-night` |
| Último Gate | **Gate G3: APROBADO** (ver `loops/r29b/ROUND_03_F3_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (`7a3b008`), P-2 (`0ec5369`), P-3 (`/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | EN PROCESO · F0, F2, F1 y F3 completadas, iniciando F4 |

## Historial de Tareas y Fases

| Fase / Tarea | Estado | Evidencia / Notas |
| :--- | :--- | :--- |
| **F0: Preparación** | **APROBADA** | Ver `ROUND_00_F0_REPORT.md` (Commit `bc1ebb5`) |
| T-0.1 | COMPLETADA | P-1 resuelto en `7a3b008`. Decisión OD-6 = opción A. |
| T-0.2 | COMPLETADA | P-2 publicado en `0ec5369`. CI verde en push `42757465313` y PR `42757470092`. |
| T-0.3 | COMPLETADA | 5 fixtures golden en `data/fixtures/golden/` + `TESTING_STRATEGY.md` §7. |
| **Gate G0** | **APROBADO** | JVM 573/0/0, Python 43/43 OK, CI verde, fixtures creados. |
| **F2: Core Puro** | **APROBADA** | Ver `ROUND_02_F2_REPORT.md` (Commit `bd84894`) |
| T-2.1 | COMPLETADA | Paquete `com.example.tradedraw.core` con 9 tipos canónicos y compatibilidad vía `CoreTypeAliases.kt`. |
| T-2.2 | COMPLETADA | `DecisionIds.kt` con delegación companion en `TradingEngine`. Paridad de IDs verificada. |
| **Gate G2** | **APROBADO** | JVM 574/0/0, Python 43/43 OK, CRLF de OverlayService intacto, git diff -w limpio. |
| **F1: Contratos P0** | **APROBADA** | Ver `ROUND_01_F1_REPORT.md` |
| T-1.1 | COMPLETADA | `core/EventSink.kt` + `RingBufferEventSink` delegando en `TradeEventLog`. Inyección por ctor en 6 consumidores. |
| T-1.2 | COMPLETADA | `core/BalanceSource.kt` + `AccessibilityBalanceSource` con dual-read (0 divergencias). Inyección en `RiskManager`. |
| T-1.3 | COMPLETADA | `core/CredentialPorts.kt` (`CredentialProvider`, `CredentialSink`, `SharedPreferencesCredentialStore`). |
| T-1.4 | COMPLETADA | `core/CapitalPolicy.kt` implementada por `RiskManager.canExecuteTrade` e inyectada en `TradingEngine`. |
| **Gate G1** | **APROBADO** | JVM 583/0/0, Python 43/43 OK, CRLF de OverlayService intacto, 4 nuevas suites verdes. |
| **F3: Frontera Ejecución** | **APROBADA** | Ver `ROUND_03_F3_REPORT.md` |
| T-3.0 | COMPLETADA | Inventario de 13 vetos congelado en `ROUND_06_MIGRATION_PLAN.md` §3. |
| T-3.1 | COMPLETADA | Seam coordenadas E4 (`TapCoordinateResolver` + `CalibrationTapCoordinateResolver`). |
| T-3.2 | COMPLETADA | `ExecutionPolicy` (13 vetos en orden congelado, mensajes byte-a-byte, flag off por defecto). |
| T-3.3 | COMPLETADA | `OrderGateway` + 3 `TapTransport` (`RootTapTransport`, `GestureTapTransport`, `BridgeDirectTransport`) con guarda DEMO positiva/fresca. |
| T-3.4 | COMPLETADA | `CandleRateLimiter` (D-7 rate limit por vela con separación SHADOW bajo flag). |
| T-3.5 | COMPLETADA | `/trade` por modo (P-3 aprobada: rechazo explícito en DISABLED/SHADOW con evento y HTTP 403). |
| **Gate G3** | **APROBADO** | JVM 614/0/0, Python 43/43 OK, 5 nuevas suites verdes (`TapCoordinateResolverTest`, `CandleRateLimiterTest`, `TapTransportParityTest`, `TradeEndpointModeGuardTest`, `VetoCascadeEquivalenceTest`). |
| **F4: Simulation Engine** | **EN CURSO** | |
| T-4.1 | PENDIENTE | `simulation/SimClock` + `SimLedger` (balance simulado, puros, apagados). |
| T-4.2 | PENDIENTE | `SimulatedOrderGateway: OrderGateway` reutilizando `ExecutionPolicy`. |
| T-4.3 | PENDIENTE | SHADOW delega en `SimulatedOrderGateway` tras flag apagado por defecto; esquema `shadow_journal` v2 versionado. |
| **Gate G4** | PENDIENTE | Suite JVM + ShadowModeTest ampliado + SimulatedOrderGatewayTest + Python contra journal v1/v2. |

## Próxima acción
- Commitear y pushear los cambios verificados de F3 a `r10-night`.
- Ejecutar F4 (T-4.1 a T-4.3) y validar Gate G4.
