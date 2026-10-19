# TRADEDRAW — MISIÓN AUDITORÍA FASE 1 LOOP R23I
## DESTINATARIO: CLAUDE CODE (`w1:pC` / Auditor Principal)
## REMITENTE: AGY (`w1:p1` / Orquestador)

Estimado Claude Code,

Bajo las directrices de `workflows/NEXT_LOOP_R23I.md`, se te asigna la **FASE 1 — AUDITORÍA DE CIERRE**:

### OBJETIVO
Realizar una auditoría técnica y forense exhaustiva de:
- `workflows/rounds/R23H_FINAL_CLOSURE_REPORT.md`
- `workflows/rounds/R23H_CLAUDE_PREFLIGHT_W05.md`
- Todos los artefactos de la ventana `loops/r23h_validation/window_05_clean/` (`decisions.csv`, `trades.csv`, `ticks_live.csv`, `replay_journal.csv`, `REPLAY_PARITY_REPORT.md`, `WINDOW_REPORT.md`).

### PUNTOS OBLIGATORIOS A COMPROBAR
1. **Ledger y Balance:**
   - Verificar la ecuación canónica $N = W + L + VOID$ ($6 = 4 + 2 + 0$).
   - Verificar variación de balance ($\Delta = +128,000.00$ COP DEMO) y comprobar que la discrepancia aritmética teórica vs real sea $0.00 COP$.
2. **Coherencia de Decisiones / Bloqueos / Ejecuciones:**
   - Analizar las 88 decisiones en `decisions.csv`.
   - Verificar que los 80 bloqueos correspondan a defensas pasivas legítimas (35 por `ADAPTIVE_BLOCK`, 45 por `RISK_LIMIT` / orden en curso, y vetos por `VETO_BUY_EXHAUSTION_CLIMAX`).
   - Confirmar que las 6 ejecuciones en `trades.csv` se deriven legítimamente de esas decisiones.
3. **Trazabilidad Live ↔ Replay:**
   - Comprobar el dataset de ticks podados (`ticks_live.csv`, 1,567 ticks dentro de ventana).
   - Verificar la simulación en `replay_journal.csv` (2 trades bajo submodo YOLO).
4. **Evaluación Crítica del Único Par Formal ($N=1$):**
   - Auditar por qué de 6 trades Live y 2 trades Replay sólo hubo 1 par evaluado en el comparador formal.
   - Demostrar que el par evaluado (`49140605...`, SELL, WIN) NO fue seleccionado artificialmente ni sesgado.
   - Analizar la naturaleza del trade restante de Replay y los otros 5 trades de Live (por qué no formaron par bajo la tolerancia temporal de 6s o cooldown).
5. **Integridad de la Estrategia:**
   - Comprobar que en el working tree NO existen cambios de estrategia disfrazados de infraestructura (señales BUY/SELL, confluencia 80 pts, veto y risk manager estrictamente intactos).

### ENTREGABLE
Genera el informe detallado en:
`workflows/rounds/R23I_CLAUDE_FASE1_AUDIT.md`

Concluye con un veredicto explícito:
`FASE 1 AUDIT = PASS` o `FASE 1 AUDIT = FAIL` (con desglose de hallazgos).
