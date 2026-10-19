# MISIÓN OPENCODE: VALIDACIÓN INDEPENDIENTE PARIDAD LIVE ↔ REPLAY R23H W03

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:pB` / Validador Independiente)  
**Objetivo:** Validación empírica e independiente de datos entre Live y Replay en R23H W03.

---

## CONTEXTO
En `loops/r23h_validation/window_03/`:
- Live reporta 4 trades ejecutados en `trades.csv`.
- Replay reporta 8 operaciones simuladas en `replay_journal.csv`.
- `replay_comparison.json` arrojó 0 pares emparejados (0.0% match rate, FAIL).

---

## REGLAS
- NO modifiques la estrategia.
- NO modifiques código aún.
- Entrega hallazgos independientes con base en los datos crudos.

---

## TAREAS DE VALIDACIÓN INDEPENDIENTE
1. Comprobar la cantidad real de operaciones Live (`trades.csv` vs `decisions.csv` con `ORDER_STATE`).
2. Comprobar la cantidad y naturaleza de los 8 eventos en `replay_journal.csv`.
3. Cruzar los `decision_id` de `replay_journal.csv` contra `decisions.csv` de Live: ¿existen esos IDs en las decisiones Live? ¿Qué estado tuvieron en Live (VETOED vs EXECUTED)?
4. Identificar qué pasó con los 4 trades reales de Live en Replay: ¿fueron bloqueados en Replay, o cayeron en ventanas temporales distintas?
5. Revisar la lógica de emparejamiento de `scripts/validation/compare_live_replay.py`: ¿por qué el fallback temporal (|Δts| <= 3000ms) y el cruce por `decision_id` dieron 0 pares?
6. Evaluar consistencia de balances y salvaguardas (Cuenta DEMO intacta).

---

## FORMATO DE ENTREGA
Escribe tu reporte en `loops/r23h_analysis/OPENCODE_REPLAY_PARITY_AUDIT.md` con tablas comparativas y veredicto independiente.
