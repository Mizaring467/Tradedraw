TAREA — RONDA 4B · TESTER/SUPERVISOR DE SESIÓN (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Hay una sesión de 25 min EN CURSO (no la interrumpas). APK con H9 + R2/R3 ya instalado.

=== QUÉ HACER ===
Cada ~60 s durante ~18 min, toma y guarda una muestra:
1. curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   -> anota: balance, engine.signalsGenerated, engine.evaluatedCount, engine.suppressedByConfluence,
      risk.totalWins, risk.totalLosses, risk.hasPendingTrade.
2. adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /C:"ENTRADA despachada"
   -> captura las lineas con "segundo=" (segundo real de disparo).
3. Captura HUD: adb -s UGU8KFUKM7E6CE8D exec-out screencap -p > .agents/loop/r4b_hud_"%n".png
   y describe tendencia/terminometro/balance vs lo que muestra Binomo.
Guarda cada muestra como una linea en .agents/loop/r4b_samples.jsonl

=== AL TERMINAR ===
4. Journal del dispositivo (ultimas 6 lineas):
   adb -s UGU8KFUKM7E6CE8D shell cat /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv
5. Report json: loops/journal/live/live_session_25m_report.json

=== ENTREGABLE (español, máx 35 líneas) ===
- Tabla: segundo de disparo (de "ENTRADA despachada") vs resultado si se conoce
- Nº de trades de la sesión y WIN/LOSS/TIE
- ¿Persisten TIEs? (sí/no + cuántos). ¿El journal del dispositivo registró los trades nuevos?
- Balance inicial vs final
- Coherencia HUD vs Binomo (tendencia, %CALL/PUT, balance) - min 5 comparaciones
- Veredicto: tras R2/R3, ¿mejora la calidad de entradas?
- Rutas de evidencia
NO edites código Kotlin.
