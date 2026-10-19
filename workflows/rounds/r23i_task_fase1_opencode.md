# TRADEDRAW — MISIÓN VALIDACIÓN FASE 1 LOOP R23I
## DESTINATARIO: OPENCODE (`w1:pB` / Validador Independiente)
## REMITENTE: AGY (`w1:p1` / Orquestador)

Estimado OpenCode,

Bajo las directrices de `workflows/NEXT_LOOP_R23I.md`, se te asigna la **FASE 1 — VALIDACIÓN INDEPENDIENTE**:

### OBJETIVO
Realizar una validación completamente independiente y forense de:
- `workflows/rounds/R23H_FINAL_CLOSURE_REPORT.md`
- `workflows/rounds/R23H_CLAUDE_PREFLIGHT_W05.md`
- Los artefactos de la ventana `loops/r23h_validation/window_05_clean/` (`decisions.csv`, `trades.csv`, `ticks_live.csv`, `replay_journal.csv`, `REPLAY_PARITY_REPORT.md`, `WINDOW_REPORT.md`).

### PUNTOS OBLIGATORIOS A VALIDAR
1. **Validación Independiente de Ledger y Balance:**
   - Comprobar independientemente la consistencia del balance inicial (39,464,737.28 COP) al final (39,592,737.28 COP).
   - Validar las 6 operaciones registradas: 4 W / 2 L / 0 VOID.
   - Confirmar si existe o no algún leak contable.
2. **Validación de Decisiones vs Bloqueos vs Ejecuciones:**
   - Contar y auditar independientemente las 88 decisiones en `decisions.csv`.
   - Comprobar la proporción de bloqueos (80 bloqueos) y verificar si se originan por `ADAPTIVE_BLOCK 99%` o `RISK_LIMIT` (órdenes en curso).
   - Validar que las 6 ejecuciones en `trades.csv` no contengan ejecuciones duplicadas ni espurias.
3. **Análisis Independiente de la Paridad Replay (N=1 Par):**
   - Verificar de forma crítica e independiente el reporte de paridad (`REPLAY_PARITY_REPORT.md` y `replay_parity.json`).
   - Evaluar si el par formal obtenido es representativo o si la muestra de N=1 par introduce vulnerabilidad epistémica que justifique la Fase 2 del loop R23I.
   - Reportar cualquier discrepancia o anomalía detectada.
4. **Verificación de Invarianza de Estrategia:**
   - Inspeccionar los diffs locales para certificar que no hubo ninguna alteración oculta en las heurísticas de trading ni en la gestión de capital.

### ENTREGABLE
Genera el informe detallado en:
`workflows/rounds/R23I_OPENCODE_FASE1_VALIDATION.md`

Concluye con un veredicto explícito:
`FASE 1 VALIDATION = PASS` o `FASE 1 VALIDATION = DISCREPANCY` (con lista de discrepancias si las hubiera).
