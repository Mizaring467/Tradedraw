# TradeDraw · R29-B — STATE

| Campo | Valor |
| :--- | :--- |
| Fase actual | F3 — Frontera de ejecución (D-6/D-7) |
| Tarea actual | T-3.1 (seam coordenadas), T-3.2 (ExecutionPolicy), T-3.3 (OrderGateway), T-3.4 (CandleRateLimiter), T-3.5 (/trade por modo) |
| HEAD | `bd84894` (commit F2 en `r10-night`) |
| Último Gate | **Gate G1: APROBADO** (ver `loops/r29b/ROUND_01_F1_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (`7a3b008`), P-2 (`0ec5369`), P-3 (`/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | EN PROCESO · F0, F2 y F1 completadas, iniciando F3 |

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
| **F3: Frontera Ejecución** | **EN CURSO** | |
| T-3.0 | COMPLETADA | Inventario de 13 vetos congelado en `ROUND_06_MIGRATION_PLAN.md` §3. |
| T-3.1 | PENDIENTE | Seam coordenadas E4 (`TapCoordinateResolver`). |
| T-3.2 | PENDIENTE | `ExecutionPolicy` (13 vetos en orden congelado, mensajes byte-a-byte, flag off por defecto). |
| T-3.3 | PENDIENTE | `OrderGateway` + 3 `TapTransport` (`RootTapTransport`, `GestureTapTransport`, `BridgeDirectTransport`). |
| T-3.4 | PENDIENTE | `CandleRateLimiter` (D-7 rate limit por vela). |
| T-3.5 | PENDIENTE | `/trade` por modo (P-3 aprobada: rechazo explícito en DISABLED/SHADOW con evento). |
| **Gate G3** | PENDIENTE | Suite + VetoCascadeEquivalenceTest + TapTransportParityTest + CandleRateLimiterTest. |

## Próxima acción
- Commitear y pushear los cambios verificados de F1 a `r10-night`.
- Ejecutar F3 (T-3.1 a T-3.5) y validar Gate G3.
