# PREREGISTRO RONDA 21 — Trazabilidad End-to-End, Fiabilidad, Replay Determinista y Seguridad

**Fecha:** 2026-10-14  
**Rama:** `r10-night` → `main`  
**Árbol base:** `4d433ed` (HEAD verificado)  
**Orquestador:** AGY (`w1:p1`)  
**Colaborador:** HERMES (`w1:p6`)  

> ⚡ **Este documento se redacta e incorpora ANTES de aplicar modificaciones de comportamiento en el código.**  
> Regla de gobernanza: ningún criterio de evaluación se relaja ni se altera a posteriori. Separación rigurosa de `FACT`, `EVIDENCE`, `HYPOTHESIS` y `MISSING PROOF`.

---

## 1. Alcance de la Ronda 21

La Ronda 21 establece la infraestructura técnica que garantiza que cada ciclo:  
`DATOS → SEÑAL → DECISIÓN → EJECUCIÓN → CONFIRMACIÓN → LIQUIDACIÓN → JOURNAL`  
sea 100% trazable, reproducible, auditable y coherente, tanto en vivo como en simulación offline.

### Frentes Principales:
- **Fase 0:** Auditoría forense Read-Only de la variación de balance no atribuida (+80.000 COP) observada en R20.
- **Frente A:** Identificadores únicos End-to-End (`decision_id` y `trade_id`) propagados por todo el ciclo de vida, persistidos de forma retrocompatible en journal y expuestos en `/events`.
- **Frente B:** Comparador automático Live ↔ Replay (`scripts/validation/compare_live_replay.py`) con detección determinista de la primera divergencia.
- **Frente C:** Replay con latencia real observada (`OBSERVED_LATENCY`) basado en telemetría real (`signal_ts`, `tap_start_ts`, `tap_end_ts`).
- **Frente D:** `HealthSupervisor` unificado (`READY`, `DEGRADED`, `BLOCKED`) con agregación de razones técnicas fail-closed sin duplicar fuentes de verdad.
- **Frente E:** `SHADOW MODE` formal: evaluación real de ticks sin taps, sin confirmación ficticia, sin contaminar W/L real.
- **Frente F:** Seguridad C1: Sanitización total de credenciales y tokens en logs (`tokenPresent=true`).
- **Frente G:** Verificación de reproducibilidad de commits desde worktrees temporales limpios (`verify_clean_worktree`).
- **Frente H:** Propuesta de arquitectura para Event Ledger append-only.

---

## 2. Invariantes Absolutos de Gobernanza

| # | Invariante | Regla |
|---|---|---|
| **G.1** | **Entorno de Operación** | Todo trabajo se mantiene estrictamente en cuenta DEMO o simulación de investigación. Prohibido operar o preparar despliegue en REAL. |
| **G.2** | **Gestión de Capital** | Prohibido modificar stake para perseguir rentabilidad, martingale, anti-martingale, Soros o alterar reglas de Quarter-Kelly (1.0% - 3.5%). |
| **G.3** | **Estrategia Inviolable** | No relajar umbrales de `master_traders_skill` (Rechazo ≥45%, Cuerpo ≤40%, S/R ≤4 px, ventana :06-:57 veto total, CHOP > 61.8, confluencia ≥80 pts). No alterar BUY/SELL para buscar WR. |
| **G.4** | **Integridad de Datos** | Prohibido inventar causas o suposiciones para datos inexplicados. Prohibido borrar o sobreescribir journals históricos. |
| **G.5** | **Principio Fail-Closed** | Ante cualquier inconsistencia técnica (feed rancio, desconexión, desajuste pantalla/feed, accesibilidad caída), el sistema se bloquea y veta cualquier ejecución. |
| **G.6** | **Seguridad de Credenciales** | Ningún token, cookie sensible o credencial de sesión debe registrarse en Logcat, archivos en disco o endpoints HTTP. |
| **G.7** | **Estado al Cierre** | Tras cualquier sesión o prueba en dispositivo: `mode = DISABLED`, `hasPendingTrade = false`, `orderState = NINGUNA`. |

---

## 3. Criterios de Aceptación por Frente (Fijados a Priori)

### 3.0 Fase 0: Auditoría Balance +80.000 COP (Hermes)
- **C0.1:** Auditoría 100% READ-ONLY. No se ejecutan taps ni se modifican estados en el teléfono.
- **C0.2:** Inspección de mtime de archivos, `MODE_CHANGE` JSONL, `/mode_log`, `/events`, contadores y journals.
- **C0.3:** Entregable `workflows/rounds/r21_balance_incident_hermes.md` con clasificación formal: `A) ATRIBUIDO`, `B) NO ATRIBUIDO PERO ACOTADO` o `C) EVIDENCIA INSUFICIENTE`.

### 3.1 Frente A: `decision_id` y `trade_id` End-to-End (AGY)
- **A.1:** `decision_id` monotónico o UUID generado en el instante en que el motor evalúa una decisión candidata.
- **A.2:** `decision_id` se propaga obligatoriamente por `SIGNAL`, `DECISION`, `VETO` (si ocurre), `DISPATCH`, `TAP_START`, `TAP_END`.
- **A.3:** `trade_id` se genera únicamente si la orden es efectivamente despachada y se propaga por `ENVIADA`, `CONFIRMADA`, `NO_PROCESADA`, `WIN`, `LOSS`, `TIE`, `SETTLEMENT`, `JOURNAL`.
- **A.4:** Los vetos registran `decision_id` pero **NUNCA** generan `trade_id`.
- **A.5:** Retrocompatibilidad de Journal: los journals CSV leen filas antiguas sin ID sin error; las nuevas filas incorporan `decision_id` y `trade_id` al final.
- **A.6:** Suite de tests unitarios dedicada verifica generación, unicidad, propagación e idempotencia.

### 3.2 Frente B: Comparador Live ↔ Replay (Hermes)
- **B.1:** Herramienta `scripts/validation/compare_live_replay.py` toma un archivo de sesión real y reproduce los ticks por el motor offline.
- **B.2:** Para cada `decision_id`, compara campos: timestamp, activo, precio, segundo de vela, régimen, confluencia, estrategia, veto y decisión.
- **B.3:** Si hay divergencia, identifica y reporta explícitamente el **primer campo divergente** (ej. `market_regime`, `dist_support`, etc.).
- **B.4:** Genera reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).

### 3.3 Frente C: Replay con Latencia Real Observada (AGY / Hermes)
- **C.1:** Mantiene intacto el replay ideal (`entry = tick de decisión`).
- **C.2:** Incorpora modo `OBSERVED_LATENCY` donde la entrada se resuelve en el primer tick con `ts >= tap_end_ts` medido.
- **C.3:** Compara métricas entre Replay Ideal vs Replay Latente (número de señales, flip rate, discrepancias) sin alterar la estrategia.

### 3.4 Frente D: HealthSupervisor Unificado (AGY)
- **D.1:** Capa agregadora que clasifica el estado técnico del sistema en `READY`, `DEGRADED`, `BLOCKED`.
- **D.2:** `BLOCKED` reporta lista estructurada de razones: `FEED_STALE`, `NO_TICKS`, `SCREEN_FEED_MISMATCH`, `ACCESSIBILITY_DOWN`, etc.
- **D.3:** Expone en `/status`: `health.state`, `health.reasons[]`, `health.sinceMs`.
- **D.4:** No duplica lógica de decisión ni interfiere en la generación de señales BUY/SELL.

### 3.5 Frente E: Shadow Mode Formal (AGY / Hermes)
- **E.1:** Procesa ticks reales de WebSocket y ejecuta análisis en tiempo real.
- **E.2:** Genera `decision_id` y registra la acción hipotética en log/journal dedicado de shadow.
- **E.3:** Garantía absoluta: 0 llamadas a `dispatchGesture`, 0 taps, 0 interacción con Binomo, balance intacto.
- **E.4:** Métricas shadow separadas 100% de métricas de ejecución real.

### 3.6 Frente F: Seguridad C1 - Sanitización de Tokens (AGY)
- **F.1:** Eliminar logs de token completo (`Log.d(TAG, "Sesión vinculada: token=$token ...")` en `BinomoAuthActivity.kt`).
- **F.2:** Sustituir por bandera booleana segura (`tokenPresent=true`) y longitud si aplica.
- **F.3:** Auditar superficie de riesgo (C1/C2/C3) sin modificar C2/C3 sin preregistro específico.

### 3.7 Frente G: Reproducibilidad en Clean Worktree (Hermes)
- **G.1:** Script que clona/crea un git worktree limpio temporal para el SHA a validar.
- **G.2:** Ejecuta build, tests unitarios y replay fixture en dicho árbol aislado.
- **G.3:** Falla de inmediato si hay archivos necesarios no commiteados o diferencias de compilación.
- **G.4:** Limpieza automática y segura del worktree temporal sin afectar el espacio de trabajo activo.

---

## 4. Plan de Mitigación de Riesgos y Rollback

- **Riesgo 1:** Conflicto de edición concurrente entre agentes.  
  *Mitigación:* Regla estricta de exclusión mutua en `workflows/R21_COORDINATION.md`. AGY gestiona archivos Kotlin; Hermes gestiona auditoría y scripts de validación externos.
- **Riesgo 2:** Corrupción de formato en el journal existente.  
  *Mitigación:* Los tests unitarios validarán lectura de líneas históricas pre-R21 (sin IDs) y escritura con IDs append-only.
- **Riesgo 3:** Falso positivo en replay por diferencias en redondeo de timestamps.  
  *Mitigación:* Mapeo determinista y tolerancia documentada en milisegundos de sincronización.
- **Rollback:** Cada frente se incorpora mediante commits atómicos y testeados. Si cualquier frente falla la verificación en worktree limpio, se revierte dicho commit específico sin afectar el resto.
