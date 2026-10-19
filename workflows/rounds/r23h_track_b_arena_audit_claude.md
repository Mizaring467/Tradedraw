# TAREA CLAUDE CODE — TRACK B: AUDITORÍA FORENSE DE HALLAZGOS ARENA.AI

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Principal)  
**Misión:** Auditoría matemática y forense independiente de los 5 puntos reportados por Arena.AI (§12 Prompt Maestro).

---

## LOS 5 PUNTOS A AUDITAR OBLIGATORIAMENTE

### A. KILL SWITCH
- Reportado: `Kill switch = 40.000.000 COP` y `balance ≈ 38.054.735 COP`.
- Determinar:
  - Definición y ubicación exacta en el código de `RiskManager.kt` o `TradingEngine.kt`.
  - ¿Sigue vigente ese umbral o fue una variable histórica/scoreboard obsoleto?
  - ¿Cuál es el piso de equity real y cómo opera en cuenta DEMO?

### B. BALANCE NO ATRIBUIDO
- Reportado: Aprox. `+80.000 COP` sin operación aparente correspondiente del bot.
- Determinar:
  - ¿En qué ventana o timestamp ocurrió?
  - Inspeccionar si fue: operación manual, liquidación asíncrona de broker (delay en acreditación de W anterior), ajuste de broker, o salto de lectura OCR.
  - Reconstruir la serie temporal con evidencia de `status_samples.jsonl` y `trades.csv`.

### C. B1 — STOP LOSS STREAK
- Reportado: `racha máxima = 10 pérdidas` mientras `stopLossStreak = 3`.
- Determinar:
  - Código real de `RiskManager.kt` que gobierna `stopLossStreak` y `currentLossStreak`.
  - ¿Cuándo se activa, cómo se resetea y qué efecto produce (`COOLDOWN` vs `DISABLED`)?
  - ¿A qué versiones pertenecen las rachas de pérdidas históricas y si el mecanismo estaba activo en esas ejecuciones?

### D. HISTORIAL 195 / 212 TRADES
- Reportado: 212 trades (81W / 114L / 17 VOID), 195 trades resueltos $\implies$ 41.54% WR.
- Determinar:
  - Inspeccionar el dataset histórico completo: ¿está compuesto por mezclas de versiones viejas (pre-v1.8), estrategias experimentales sin filtros, o es un ledger canónico?
  - ¿Cuántos trades pertenecen a versiones obsoletas previas a `VETO_BUY_EXHAUSTION_CLIMAX` y `ADAPTIVE_BLOCK 99%`?

### E. AUDITORÍA MATEMÁTICA DE P-VALUE
- Reportado: `p-value = 1.0000` y simultáneamente *"significativamente por debajo de breakeven"*.
- Determinar:
  - Recalcular desde primeros principios estadísticos:
    - $H_0: p \le p_{\text{breakeven}}$ vs $H_1: p > p_{\text{breakeven}}$ (test unilateral superior)
    - O $H_0: p \ge p_{\text{breakeven}}$ vs $H_1: p < p_{\text{breakeven}}$ (test unilateral inferior)
    - Con $p_{\text{breakeven}} = 1 / (1 + 0.82) = 54.95\%$.
    - Para $W = 81, L = 114, N = 195$ ($WR = 41.54\%$):
      Calcular $Z$-score exacto y p-value para ambas colas.
    - Explicar la contradicción matemática o el error de convención en el reporte de Arena.

---

## REGLAS
- NO modifiques código ni la estrategia.
- Entrega tu reporte en `workflows/rounds/R23H_CLAUDE_TRACK_B_ARENA_AUDIT.md`.
