TAREA — RONDA 4 · TESTER EN VIVO + TELEMETRÍA DE TIMING (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Estado preparado: AUTONOMOUS / CONSERVATIVE, accesibilidad activa, Binomo al frente,
bridge tcp:8080, balance ~39.26M COP. APK recién instalado con telemetría nueva.
NO edites código Kotlin.

=== NOVEDAD H9 ===
El motor ahora emite en logcat: "🎯 ENTRADA despachada | <ACTION> | segundo=<sec> | estrategia=..."
y "➡️ executeHeadlessTrade INICIO". Con esto se mide el SEGUNDO REAL de entrada.

=== Pasos ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
2. Lanza la sesión de 25 min (timeout >= 1700000 ms): python scripts\monitor_25m_session.py
3. En paralelo, MUESTREA cada 20s: /status -> engine.{signalsGenerated,evaluatedCount,suppressedByConfluence},
   balance, trend. Guarda en .agents/loop/r4_engine_samples.jsonl
4. Al terminar, EXTRAE del logcat los segundos de entrada reales:
   adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /C:"ENTRADA despachada"
   y también "Tap primario vía ROOT", "BOT OPERÓ".
5. Verifica TIEs: adb -s UGU8KFUKM7E6CE8D shell "cat /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"
   (los nuevos trades deben tener result WIN/LOSS, NO TIE).
6. Captura de evidencia: .agents/loop/r4_final.png

=== ENTREGABLE (español, máx 30 líneas) ===
- engine.* inicial vs final (signalsGenerated, evaluatedCount, suppressedByConfluence)
- Nº de trades de la sesión y resultado (WIN/LOSS/TIE) del report json
- Segundos de entrada reales detectados (lista de "segundo=X")
- ¿Quedaron TIEs? (sí/no + cuántos)
- Balance inicial vs final
- Rutas de evidencia
