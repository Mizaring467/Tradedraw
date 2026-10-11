TAREA — RONDA 3 · AUDITORÍA DE CAMBIOS (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin. Audita y reporta.

=== Cambios a auditar (Ronda 3) ===
1. TradingEngine.getEngineReasoning() (aprox. L1117-1215):
   - Ahora la tendencia/patrón/probabilidad/plan se derivan SOLO de `syntheticCandleEngine`
     (motor WebSocket), NO de `latestAnalysisResult` (visión retirada/stale).
   - `trendNow = wsTrend` (antes: `if (analysis != null && analysis.trend != SIDEWAYS) analysis.trend else wsTrend`).
   - Textos de plan cambiados de ":58s - :01s"/":58s - :03s" a "ventana :01s - :05s".
2. OverlayService (aprox. L1716): el badge `hud_trend_badge` usa `trendDir = wsTrend` (antes mezclaba analysis).
3. AutoTradeAccessibilityService.autHealAccessibility(): ahora hace toggle real
   (enabled_accessibility_services='' ; accessibility_enabled 0 ; sleep 1 ; re-registrar + enabled 1).
4. AutoTradeAccessibilityService.dispatchClickGesture(): Path ahora hace moveTo(x,y) + lineTo(x,y).

=== Contexto ===
- La visión (ScreenCaptureManager/VisionAnalyzer) fue RETIRADA por batería; en Headless la única fuente es el WS.
- Evidencia viva: HUD mostraba "ALCISTA" mientras /status decía SIDEWAYS (divergencia). Tras el fix, HUD y /status coinciden (ambos DOWNTREND/BAJISTA).
- El tap por `adb input tap` en (250,1340) SÍ coloca orden en Binomo; con dispatchGesture quedaba VOID.

=== Preguntas ===
A) ¿Es correcto y suficiente derivar TODO el HUD del motor WS en modo Headless? ¿Algún caso donde
   `analysis` siga siendo necesario (p.ej. vision en modo no-headless)?
B) ¿El plan ":01s - :05s" es coherente con la ventana real de ejecución (sec in 1..5)? ¿Coincide con master_traders_skill?
C) ¿El toggle real de accesibilidad puede causar efectos adversos (parpadeo, doble registro)?
D) ¿moveTo+lineTo es la forma correcta de emitir un tap determinista, o hay riesgo de interpretarse como swipe?

ENTREGABLE (español, máx 25 líneas): veredicto PASS/FAIL por cambio + riesgos + correcciones si aplica.
