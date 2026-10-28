# R21 — Coordinación y Reservas de Archivos Multi-Agente (Herdr)

**Fecha:** 2026-10-14  
**Workspace Herdr:** `w1`  
**Orquestador:** `agy` (`w1:p1`)  
**Colaborador:** `hermes` (`w1:p6`)  
**SHA Base:** `4d433ed`

---

## 1. Regla de Mutex de Archivos

1. **Ningún agente puede editar un archivo reservado por el otro agente.**
2. Antes de editar cualquier archivo, el agente debe registrar la reserva en la tabla inferior y verificar `git status`.
3. Al terminar la tarea o liberar archivos, el estado se actualiza a `LIBERADO` o `MERGED`.
4. Si Hermes detecta un defecto en archivos Kotlin reservados por AGY, **NO lo edita**: lo documenta y se lo reporta a AGY vía `herdr agent prompt agy "<hallazgo + evidencia + archivo/línea>"`.

---

## 2. Tabla de Reservas y Asignaciones

| Frente | Responsable | Archivos reservados | Estado | SHA base |
|---|---|---|---|---|
| **Fase 0: Auditoría Balance +80.000 (Read-Only)** | `hermes` | `workflows/rounds/r21_balance_incident_hermes.md` | **CERRADO (Dictamen B: No atribuido pero acotado)** | `4d433ed` |
| **Frente A: Decision ID / Trade ID End-to-End** | `agy` | `app/src/main/java/com/example/tradedraw/TradingEngine.kt`, `app/src/main/java/com/example/tradedraw/RiskManager.kt`, `app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt`, `app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt`, `app/src/main/java/com/example/tradedraw/TradeEventLog.kt`, `app/src/test/java/com/example/tradedraw/TradeIdEndToEndTest.kt`, `app/src/test/java/com/example/tradedraw/JournalLatencySchemaTest.kt` | **IMPLEMENTADO Y VALIDADO (Tests en verde)** | `4d433ed` |
| **Frente B: Comparador Live ↔ Replay** | `hermes` | `scripts/validation/compare_live_replay.py`, `data/fixtures/*`, `workflows/rounds/r21_replay_comparison*` | **IMPLEMENTADO Y VALIDADO** | `4d433ed` |
| **Frente C: Replay con Latencia Real** | `agy` | `app/src/test/java/com/example/tradedraw/ReplayLatencyModeTest.kt` | **IMPLEMENTADO Y VALIDADO (Tests en verde)** | `4d433ed` |
| **Frente D: Health Supervisor Unificado** | `agy` | `app/src/main/java/com/example/tradedraw/health/*`, `app/src/main/java/com/example/tradedraw/OverlayService.kt`, `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`, `app/src/test/java/com/example/tradedraw/health/*` | **IMPLEMENTADO Y VALIDADO (Tests en verde)** | `4d433ed` |
| **Frente E: Shadow Mode Formal** | `agy` | `app/src/main/java/com/example/tradedraw/TradingEngine.kt`, `app/src/main/java/com/example/tradedraw/OverlayService.kt`, `app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt`, `app/src/test/java/com/example/tradedraw/ShadowModeTest.kt` | **IMPLEMENTADO Y VALIDADO (Tests en verde)** | `4d433ed` |
| **Frente F: Seguridad (C1 Token Sanitization)** | `agy` | `app/src/main/java/com/example/tradedraw/BinomoAuthActivity.kt`, `app/src/test/java/com/example/tradedraw/BinomoAuthSanitizationTest.kt` | **IMPLEMENTADO Y VALIDADO (Tests en verde)** | `4d433ed` |
| **Frente G: Reproducibilidad Commit (Worktree)** | `hermes` | `scripts/ci/verify_clean_worktree.py`, `workflows/rounds/r21_worktree_reproducibility_report.md` | **EN VALIDACIÓN FINAL** | `4d433ed` |
| **Frente H: Event Ledger Append-Only** | `agy` | Integrado en `TradeEventLog.kt` y `TradeJournalLogger.kt` | **IMPLEMENTADO Y VALIDADO** | `4d433ed` |

---

## 3. Estado de Frentes Activos

- **Fase 0 (Hermes):** Auditoría estricta read-only del cambio de balance `37.974.735,36 -> 38.054.735,36` (+80.000 COP). Dictamen B: No atribuido pero acotado a trade manual/UI humana externa (0 taps del bot, 61 vetos activos, W/L 0/0).
- **Frente A (AGY):** `decision_id` y `trade_id` implementados y propagados en todo el pipeline; 29 columnas en journal con retrocompatibilidad.
- **Frente B (Hermes):** Comparador determinista jerárquico `scripts/validation/compare_live_replay.py` operativo con reporte JSON y MD.
- **Frente C (AGY):** Replay con latencia real (`IDEAL` vs `OBSERVED_LATENCY` primer tick >= tap_end) en `ReplayLatencyModeTest.kt`.
- **Frente D (AGY):** `HealthSupervisor` unificado (`READY`, `DEGRADED`, `BLOCKED`) integrado en motor, overlay y `/status`.
- **Frente E (AGY):** `AutoTradeMode.SHADOW` formal con persistencia aislada en `shadow_journal.csv`, cero taps y cero alteración de saldo.
- **Frente F (AGY):** Enmascaramiento irreversible `maskSecret` en `BinomoAuthActivity.kt` garantizando CERO tokens en logcat.
- **Frente G (Hermes):** Script `scripts/ci/verify_clean_worktree.py` listo para verificación tras commit reproducible.
