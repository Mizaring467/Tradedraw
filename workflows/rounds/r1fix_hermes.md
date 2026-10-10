TAREA — RONDA 1-FIX · TESTER EN VIVO (hermes)

Dispositivo: UGU8KFUKM7E6CE8D (Redmi, Android 12, ROOT). Repo: C:\Users\heidy\Tradedraw
App ya instalada y configurada (modo AUTONOMOUS, submode CONSERVATIVE, accesibilidad activa,
Binomo Demo al frente, bridge en tcp:8080, balance ~39.33M COP). NO edites código Kotlin.

Objetivo: ejecutar y supervisar una sesión de MONITOREO DE 25 MINUTOS y emitir reporte con evidencia.

=== Pasos ===
1. Verifica el estado inicial:
   adb -s UGU8KFUKM7E6CE8D forward tcp:8080 tcp:8080
   curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   (reporta mode, accessibilityActive, balance, totalWins/totalLosses, isFresh)
2. Lanza la sesión de 25 min (bloqueante):
   python scripts\monitor_25m_session.py
   Ejecútalo con timeout amplio (>= 1700000 ms). El script guarda:
   - loops/journal/live/monitor_stdout.log
   - loops/journal/live/live_session_25m_report.json
   - capturas live_25m_*.png
3. Durante/tras la sesión, captura el logcat del motor filtrado:
   adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /I "SyntheticCandleEngine TradingEngine MT_REJECTION MT_RANGE QUANT_CRYPTO choppiness congela Headless timing"
   Busca señales de: evaluaciones, vetos y DISPAROS.
4. Captura de evidencia final:
   adb -s UGU8KFUKM7E6CE8D exec-out screencap -p > .agents/loop/r1fix_hermes_final.png

=== Objetivo de la verificación ===
Tras las correcciones (chop selectivo, TFI preservado, ventana :01-:05), el motor DEBE poder
disparar al menos 1 operación si hay setup válido. Reporta si disparó o no.

ENTREGABLE (español, máx 25 líneas):
- Estado inicial y final de /status (balance, wins, losses)
- Nº de operaciones de la sesión y resultado (WIN/LOSS/VOID) del report json
- ¿Hubo disparo táctil? (evidencia en logcat: "Gesto táctil despachado" o "Tap ejecutado")
- Extracto literal de logcat con razones de veto o disparo
- Rutas de evidencia (json, png)
- Veredicto: el motor OPERA / sigue en 0 trades / error
