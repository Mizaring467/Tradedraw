# TAREA CLAUDE CODE — TRACK A: AUDITORÍA PREVIA DE PRE-VUELO (REVALIDACIÓN LIMPIA R23H)

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Principal)  
**Misión:** Auditar exclusivamente la preparación y homogenización antes de lanzar la nueva ventana DEMO limpia.

---

## CONTEXTO
Vamos a ejecutar una nueva ventana DEMO limpia (`window_04_clean`) para demostrar la paridad Live ↔ Replay bajo exactamente la misma versión, configuración, submodo y política.

Consulta el manifiesto en:
`workflows/R23H_REVALIDATION_MANIFEST.md`

---

## 7 COMPROBACIONES OBLIGATORIAS (§6 PROMPT MAESTRO)
Claude Code debe comprobar y certificar con evidencia técnica:
1. **Correspondencia de Commit:** ¿El build Live (en runtime /status) y el código de Replay corresponden al mismo commit/lógica? ¿Se requiere commit de los cambios de infraestructura antes de ejecutar?
2. **Políticas Idénticas:** ¿Las políticas defensivas (especialmente `VETO_BUY_EXHAUSTION_CLIMAX`) están unificadas entre Live y Replay mediante `calcularPoderesDeMercado()`?
3. **Submodo Idéntico:** ¿Está asegurado que tanto Live como Replay corran estrictamente en `YOLO` (cooldown 8,000 ms)?
4. **Delimitación Limpia de Dataset:** ¿La poda implementada en `live_monitor_r23_autonomous.py` garantiza que `min(timestamp) >= window_start` en `ticks_live.csv`?
5. **Correlación de IDs:** ¿Los `decision_id` generados por el motor vivo y por Replay son correlacionables matemáticamente?
6. **Definición Correcta del Comparador:** ¿`compare_live_replay.py` utiliza la tolerancia correcta (6,000 ms) y la semántica adecuada de `--mode trades`?
7. **Ausencia de Estado Residual:** ¿El entorno y el nuevo directorio `loops/r23h_validation/window_04_clean/` están completamente limpios de artefactos previos?

---

## REGLAS
- NO modifiques código.
- NO modifiques la estrategia.
- Entrega tu veredicto inequívoco:
  **`READY`** o **`NOT READY`**
  con justificación y evidencia técnica.
- Si es `NOT READY`, especifica exactamente qué acción mínima falta.

Entrega tu reporte en `workflows/rounds/R23H_CLAUDE_PREFLIGHT_AUDIT.md`.
