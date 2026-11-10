# TradeDraw · R29-B — STATE

| Campo | Valor |
| :--- | :--- |
| Fase actual | F1 — Contratos P0 Aditivos |
| Tarea actual | T-1.1 (EventSink), T-1.2 (BalanceSource dual-read), T-1.3 (CredentialPorts), T-1.4 (CapitalPolicy) |
| HEAD | `bc1ebb5` (commit F0 en `r10-night`) |
| Último Gate | **Gate G2: APROBADO** (ver `loops/r29b/ROUND_02_F2_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (`7a3b008`), P-2 (`0ec5369`), P-3 (`/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | EN PROCESO · F0 y F2 completadas, iniciando F1 |

## Historial de Tareas y Fases

| Fase / Tarea | Estado | Evidencia / Notas |
| :--- | :--- | :--- |
| **F0: Preparación** | **APROBADA** | Ver `ROUND_00_F0_REPORT.md` (Commit `bc1ebb5`) |
| T-0.1 | COMPLETADA | P-1 resuelto en `7a3b008`. Decisión OD-6 = opción A. |
| T-0.2 | COMPLETADA | P-2 publicado en `0ec5369`. CI verde en push `42757465313` y PR `42757470092`. |
| T-0.3 | COMPLETADA | 5 fixtures golden en `data/fixtures/golden/` + `TESTING_STRATEGY.md` §7. |
| **Gate G0** | **APROBADO** | JVM 573/0/0, Python 43/43 OK, CI verde, fixtures creados. |
| **F2: Core Puro** | **APROBADA** | Ver `ROUND_02_F2_REPORT.md` |
| T-2.1 | COMPLETADA | Paquete `com.example.tradedraw.core` con 9 tipos canónicos y compatibilidad vía `CoreTypeAliases.kt`. |
| T-2.2 | COMPLETADA | `DecisionIds.kt` con delegación companion en `TradingEngine`. Paridad de IDs verificada. |
| **Gate G2** | **APROBADO** | JVM 574/0/0, Python 43/43 OK, CRLF de OverlayService intacto, git diff -w limpio. |
| **F1: Contratos P0** | **EN CURSO** | |
| T-1.1 | PENDIENTE | `core/EventSink.kt` + `RingBufferEventSink` delegando en `TradeEventLog`. |
| T-1.2 | PENDIENTE | `core/BalanceSource.kt` + dual-read con estático `ATAS`. |
| T-1.3 | PENDIENTE | `core/CredentialPorts.kt` (`CredentialProvider` / `CredentialSink`). |
| T-1.4 | PENDIENTE | `core/CapitalPolicy.kt` delegando en `RiskManager.canExecuteTrade`. |
| **Gate G1** | PENDIENTE | Suite completa verde + tests de equivalencia F1 + dual-read 0 divergencias. |

## Próxima acción
- Commitear y pushear los cambios verificados de F2 a `r10-night`.
- Ejecutar F1: T-1.1, T-1.2, T-1.3, T-1.4 y validar Gate G1.
