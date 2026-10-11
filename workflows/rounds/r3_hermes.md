TAREA — RONDA 3 · TESTER EN VIVO + VALIDACIÓN DE HUD (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Estado preparado: AUTONOMOUS / CONSERVATIVE, accesibilidad activa, Binomo Demo al frente,
bridge tcp:8080, balance ~39.48M COP. NO edites código Kotlin.

=== CAMBIOS A VALIDAR (Ronda 3) ===
1. HUD coherente con Binomo: el HUD ahora deriva tendencia/plan SOLO del motor WebSocket
   (antes mezclaba la visión retirada/stale). El plan ya no dice ":58s" sino "ventana :01s - :05s".
2. Gesto táctil: se añadió lineTo(x,y) al Path del dispatchGesture para taps deterministas.
3. Auto-heal de accesibilidad: ahora hace toggle real (limpia + re-registra).

=== FOCO ESPECIAL: HUD vs BINOMO ===
Durante la sesión, compara REPETIDAMENTE (cada ~60s):
  (a) Lo que muestra el HUD de TradeDraw (tendencia, patrón, % CALL/PUT, plan).
  (b) Lo que muestra Binomo (gráfico real, balance, precio).
  (c) Lo que dice /status (analysis.trend, callPower/putPower, balance, engine.*).
Reporta si HUD y Binomo COINCIDEN o DIVERGEN (tendencia, precio, balance).

=== Pasos ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
2. Lanza la sesión de 25 min (timeout >= 1700000 ms): python scripts\monitor_25m_session.py
3. Muestreo cada 60s: guarda en .agents/loop/r3_hud_samples.jsonl una línea con:
   {ts, status_trend, status_call, status_put, status_balance, hud_trend, hud_plan, binomo_balance, binomo_price}
   Para el HUD usa: adb -s UGU8KFUKM7E6CE8D shell uiautomator dump + leer textos, o screenshots.
4. Al terminar: reporte json + logcat (filtro: Gesto táctil despachado|BOT OPER|Headless|VOID|BOT|despachado|confirmado)
5. Evidencia: .agents/loop/r3_final.png

=== ENTREGABLE (español, máx 35 líneas) ===
- Tabla HUD vs Binomo vs /status (mínimo 5 comparaciones): tendencia, %CALL/PUT, balance, plan
- VEREDICTO de coherencia: ¿el HUD es fiel a Binomo? ¿hay divergencias? cita ejemplos
- Nº de trades ejecutados y resultado (WIN/LOSS/VOID) del report json
- ¿Hubo tap confirmado por accesibilidad o fallback ROOT? (logcat)
- Balance inicial/final
- Rutas de evidencia
