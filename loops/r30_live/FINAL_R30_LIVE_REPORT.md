# FINAL_R30_LIVE_REPORT — ESQUELETO (se llenará con evidencia de R30_LIVE_SESSION_001+)

> **Estado:** PLANTILLA — prohibido llenar cualquier sección sin evidencia
> nueva de operaciones EN VIVO durante R30. Veredictos válidos:
> EVIDENCE_NEGATIVE / INSUFFICIENT_EVIDENCE / PROMISING_BUT_UNPROVEN /
> STATISTICALLY_SUPPORTED.
> **Pregunta central:** ¿Qué ocurrió realmente cuando TradeDraw estuvo
> funcionando autónomamente en el celular (cuenta DEMO)?

## A. Ejecución de la misión
- LIVE_BUILD_ID: `R30-LIVE-FUSION` (commit `c9e52e9`, v22, sha256 `46b75620…`).
- FASE 0-4: (estado de cada fase, timestamps).
- R30_LIVE_SESSION_001: timestamp exacto de armado AUTONOMOUS (PENDIENTE).
- Timeline de sesiones y builds (cada fix técnico → nueva sesión).

## B. Operaciones (evidencia principal)
- Tabla por operación según OBSERVER_TEMPLATE_R30.md.
- **Prohibido declarar éxito con 0 operaciones nuevas.**

## C. Integridad de la evidencia
- Duplicados, timestamps, cruces engine↔broker, huecos, sesgo de selección.
- Auditor independiente (distinto del analista) con derecho a contradecir.

## D. Rendimiento
- WIN/LOSS/TIE, win rate + IC Wilson 95%, EV = p(win)×payout − p(loss).
- PnL neto, drawdown, rachas, distribución, estabilidad entre sesiones.

## E. Conectividad
- Ticks, tickAge, latencias, desconexiones (watchdog_events.jsonl).

## F. Recuperación
- Incidentes, tiempo de recuperación, % éxito, ordenState tras recuperación,
  reconciliación /dispatch_recovery (incluye prueba controlada FASE 11 si aplica).

## G. Errores y clasificación (FASE 8)
- Categorías: A-INFRA, B-WS/RED, C-UI/A11Y, D-PERSIST, E-BRIDGE,
  F-ENGINE, G-ESTRATEGIA, H-RUIDO. Bug técnico ⇒ pausa + rebuild + nueva sesión.

## H. Comparación con R29
- Qué evidencia nueva aporta R30 que R29 no tenía.

## I. Hipótesis y limitaciones
- Potencia estadística real (n), costos, latencia, decisiones de política
  (p.ej. gate health.state READY, doble crédito F4, solo-18080).

## J. Veredicto y próxima ronda
- Veredicto de la lista válida, justificado SOLO con datos de B-D.
- Propuesta concreta para la siguiente iteración (registro preregistrado
  ANTES de la próxima sesión si se propone validar estrategia).

---
*Nota de proceso: no optimizar win rate; evaluar EV. No ajustar estrategia
con los mismos datos que se usarán para validarla.*
