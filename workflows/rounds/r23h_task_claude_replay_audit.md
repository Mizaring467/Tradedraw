# MISIÓN CLAUDE CODE: AUDITORÍA PARIDAD LIVE ↔ REPLAY R23H W03

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Principal)  
**Objetivo:** Auditar exclusivamente la falla de paridad Live ↔ Replay de R23H W03.

---

## CONTEXTO DEL PROBLEMA
En `loops/r23h_validation/window_03/`:
- `trades.csv` (Live): 4 trades confirmados.
- `replay_journal.csv` (Replay): 8 registros.
- `replay_comparison_report.md`: Pares = 0, Exact matches = 0, Match rate = 0.0%, Veredicto = FAIL.
- Live posee `trade_id` y `decision_id`.
- Replay posee `decision_id`, pero no hay cruce de pares en `compare_live_replay.py --mode trades`.

---

## PREGUNTA CRÍTICA PRIORITARIA
Debes responder explícitamente y con evidencia técnica irrefutable:
> ¿Los 8 registros Replay son 8 operaciones que deberían haber sido ejecutadas en Live, o son 8 decisiones/candidatos de los cuales solamente 4 terminaron siendo ejecuciones reales?

---

## ALCANCE DE LA AUDITORÍA
Inspecciona cómo se generan:
- `decision_id`
- `trade_id`
- timestamps
- candle/tick identity
- señales
- ejecuciones
- outcomes

tanto en Live (`TradingEngine.kt`, `SyntheticCandleEngine.kt`, `live_monitor_r23_autonomous.py`, `decisions.csv`, `trades.csv`) como en Replay (`ReplayBacktestTest.kt`, `replay.py`, `replay_journal.csv`) y en el comparador (`scripts/validation/compare_live_replay.py`).

Determina por qué existen 4 trades Live y 8 registros Replay sin ningún par.

---

## REGLAS
- NO modifiques código.
- NO modifiques la estrategia.
- NO hagas refactors.
- NO investigues problemas no relacionados.

---

## FORMATO DE ENTREGA
Escribe tu reporte formal en `workflows/rounds/R23H_CLAUDE_REPLAY_PARITY_AUDIT.md` con:
1. Respuesta explícita a la Pregunta Crítica.
2. Causa raíz técnica.
3. Archivo, función y línea donde se origina la discrepancia.
4. Ejemplo concreto de Live (`decision_id`, `trade_id`, timestamp, acción).
5. Registro Replay correspondiente o explicación técnica de por qué no existe.
6. Demostración de la cadena: `decision → candidate → execution → outcome`.
7. Corrección mínima propuesta (sin tocar la estrategia de trading).
