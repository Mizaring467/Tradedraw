# TradeDraw · R29-B — STATE

| Campo | Valor |
| :--- | :--- |
| Fase actual | F5 — Gradle Multi-Module Split & Módulos desacoplados |
| Tarea actual | T-5.1 (:core), T-5.2 (:market, :persistence), T-5.3 (:strategy, :risk, :execution, :simulation), T-5.4 (:observability, :ui, :adapters, :research) |
| HEAD | `48653e7` (commit F4 en `r10-night`) |
| Último Gate | **Gate G4: APROBADO** (ver `loops/r29b/ROUND_04_F4_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (`7a3b008`), P-2 (`0ec5369`), P-3 (`/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | EN PROCESO · F0, F2, F1, F3 y F4 completadas, iniciando F5 |

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
| **F4: Simulation Engine** | **APROBADA** | Ver `ROUND_04_F4_REPORT.md` |
| T-4.1 | COMPLETADA | `simulation/SimClock` + `SimLedger` (balance simulado, puros JVM). |
| T-4.2 | COMPLETADA | `SimulatedOrderGateway: OrderGateway` reutilizando `ExecutionPolicy` y resolución a 60s. |
| T-4.3 | COMPLETADA | SHADOW delega en `SimulatedOrderGateway` tras flag apagado por defecto; esquema `shadow_journal` v2 versionado con lector tolerante. |
| **Gate G4** | **APROBADO** | JVM 626/0/0, Python 43/43 OK, 3 nuevas suites verdes (`SimLedgerTest`, `SimulatedOrderGatewayTest`, `ShadowJournalSchemaV2Test`). |
| **F5: Split Modular** | **EN CURSO** | |
| T-5.1 | COMPLETADA | Módulo `:core` puro JVM creado e incluido en Gradle. 16 archivos core movidos a `core/`. Zero dependencias Android/OkHttp. JVM 626/0/0, Python 43/43, assembleDebug OK. |
| T-5.2..4 | PENDIENTE | Módulos desacoplados y compilación multi-módulo. |
| **Gate G5** | PENDIENTE | `assembleDebug` + suite de tests desde la raíz. |

## Próxima acción
- Avanzar con T-5.2 a T-5.4 hacia Gate G5.
