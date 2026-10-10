TAREA — RONDA 2-FIX · TESTER EN VIVO (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Estado preparado: AUTONOMOUS / CONSERVATIVE, accesibilidad activa, Binomo al frente,
bridge tcp:8080, balance ~39.31M COP. NO edites código Kotlin.

=== CAMBIO A VALIDAR (H6) ===
AdaptiveLearningEngine: el veto por "anti-patrón" ahora exige similitud >= 0.90 (antes 0.82) Y
coincidencia ESTRICTA de contexto (misma acción, misma tendencia, mismo régimen lateral y S/R ±0.10).
Objetivo: dejar de bloquear señales con contexto flojo sobre un activo near-static como Z-CRY/IDX.
En la sesión anterior hubo 8 señales generadas y 0 ejecutadas, todas vetadas por este módulo.

=== Pasos ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status (anota engine.* y balance).
2. Lanza la sesión de 25 min (timeout >= 1700000 ms): python scripts\monitor_25m_session.py
3. En paralelo, muestrea /status cada 20s y guarda engine en .agents/loop/r2fix_engine_samples.jsonl
4. Al terminar, reporta engine.* final + report json + busca en logcat
   (filtro: Autoaprendizaje|Anti-patrón|Headless|VETADO|bloqueado|despachado) los vetos/ejecuciones.
5. Captura de evidencia: .agents/loop/r2fix_final.png

=== ENTREGABLE (español, máx 30 líneas) ===
- engine.* inicial vs final (signalsGenerated, evaluatedCount, suppressedByConfluence/Chop)
- Nº de trades ejecutados y resultado (report json) y balance inicial/final
- ¿Hubo ejecución táctica? (logcat: "Gesto táctil despachado" / Toast "BOT OPERÓ")
- ¿Persisten vetos por "Anti-patrón perdedor recurrente"? cita literal si sí
- Veredicto: tras H6, ¿el motor EJECUTA o sigue vetado?
- Rutas de evidencia
