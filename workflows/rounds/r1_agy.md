TAREA — RONDA 1 · AUDITORÍA CUANTITATIVA (agy)

Repo: C:\Users\heidy\Tradedraw  (rama main)
Contexto: se aplicaron H1+H2+H3 y la sesión de 25 min en Demo dio EXACTAMENTE 0 operaciones.
Debes AUDITAR (no editar código) y responder en español, máx 30 líneas.

=== Cambios a auditar ===
1) H1 timing: ventana de entrada ahora es `sec in 1..3` en todas partes
   (MarketTickFilters, MarketTick, TradingEngine, CandleTimeframe, VisionAnalyzer).
   Archivos: app/src/main/java/com/example/tradedraw/TradingEngine.kt (inWindow ~L1651),
   MarketTick.kt, CandleTimeframe.kt, VisionAnalyzer.kt (~L710).
2) H2 veto anti-chop sin bypass YOLO:
   TradingEngine.kt `if (syntheticCandleEngine.isChoppinessDetected() && !isRejectionOrBounce)` (~L1663)
   y `isFavorable` (~L1206) ya no tiene rama YOLO.
3) H3 sin inversión adaptativa: AdaptiveLearningEngine.evaluateSignalSuitability (~L258)
   ahora BLOQUEA en vez de invertir.

=== Evidencia a leer ===
- .agents/skills/master_traders_skill/SKILL.md  (gobernanza letra por letra)
- LOSS_ANALYSIS_REPORT.md  y  FUTURE_HYPOTHESES.md (H1/H2/H3 esperados)
- loops/journal/live/live_session_25m_report.json  (sesión 0 trades)
- loops/journal/live/monitor_stdout.log  (heartbeats)
- app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt
  (isChoppinessDetected ~L370, isMicroRange ~L298).

=== Preguntas concretas ===
A) ¿Por qué 0 operaciones en 25 min? Hipótesis a verificar: Crypto IDX (Z-CRY/IDX) es una
   línea casi plana (recentPriceRangeRatio ~4e-10); ¿el veto anti-chop H2 (isChoppinessDetected)
   o el guard de PRECIO CONGELADO en TradingEngine ~L1615 están vetando TODO?
B) ¿H1 (`sec in 1..3`) deja 3 segundos de ventana por vela? ¿Es suficiente o demasiado estrecho?
C) ¿H2 contradice master_traders_skill en activos sintéticos IDX? La skill manda vetar chop REAL,
   no una línea plana sin spread.
D) ¿El cambio de default `candleSecond = 0` -> `2` en VisionAnalyzer.kt es correcto/seguro?

ENTREGABLE: veredicto PASS/FAIL por H1, H2, H3 + causa raíz de las 0 operaciones + 1-3
correcciones concretas (archivo + línea) para que el implementador las aplique. Sin editar código.
