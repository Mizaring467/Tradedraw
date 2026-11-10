# TradeDraw · R29-B — STATE

| Campo | Valor |
| :--- | :--- |
| Fase actual | F2 — Extracción de core puro |
| Tarea actual | T-2.1 (movimientos a core/) y T-2.2 (DecisionIds) |
| HEAD | `0ec5369` (rama `r10-night`) |
| Último Gate | **Gate G0: APROBADO** (ver `loops/r29b/ROUND_00_F0_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (commit baseline `7a3b008`), P-2 (CI tests `0ec5369`), P-3 (re-ruta `/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | EN PROCESO · F0 completada, iniciando F2 |

## Historial de Tareas y Fases

| Fase / Tarea | Estado | Evidencia / Notas |
| :--- | :--- | :--- |
| **F0: Preparación** | **APROBADA** | Ver `ROUND_00_F0_REPORT.md` |
| T-0.1 | COMPLETADA | P-1 resuelto en `7a3b008`. Decisión OD-6 = opción A. |
| T-0.2 | COMPLETADA | P-2 publicado en `0ec5369`. CI verde en push `42757465313` y PR `42757470092`. |
| T-0.3 | COMPLETADA | 5 fixtures golden en `data/fixtures/golden/` + `TESTING_STRATEGY.md` §7. |
| **Gate G0** | **APROBADO** | JVM 573/0/0, Python 43/43 OK, CI verde, fixtures creados. |
| **F2: Core Puro** | **EN CURSO** | |
| T-2.1 | PENDIENTE | Mover a `core/` sin editar cuerpos: MarketTick, AutoTradeMode, TradeAction, AutonomousSubMode, OrderState, 14 estrategias enum, CandleTimeframe, AssetRicMapper, ActiveInstance. |
| T-2.2 | PENDIENTE | Extraer `generateDeterministicDecisionId` a `core/DecisionIds.kt` con delegación en `TradingEngine`. |
| **Gate G2** | PENDIENTE | Suite completa verde + paridad de IDs en tests. |

## Próxima acción
- Ejecutar T-2.1 y T-2.2 (extracción de core puro).
- Verificar suite JVM (573 tests) y Gate G2.
