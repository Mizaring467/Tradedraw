TAREA — RONDA 1 · TESTER DE DISPOSITIVO (hermes)

Dispositivo: UGU8KFUKM7E6CE8D (Redmi 220733SL, Android 12, ROOT vía su).
Repo: C:\Users\heidy\Tradedraw
App: com.example.tradedraw (ya instalada). Broker: com.marketly.trading (Binomo Demo).

Objetivo: verificar el estado REAL de runtime y producir evidencia. NO edites código Kotlin.

=== Pasos ===
1. Estado de accesibilidad:
   adb -s UGU8KFUKM7E6CE8D shell "dumpsys accessibility | grep -E 'Bound services|Enabled services'"
   (es intermitente en MIUI; reporta si está enlazado o no)
2. Telemetría del motor (HTTP bridge ya reenviado en tcp:8080):
   curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   Reporta: mode, accessibilityActive, balance, risk.totalWins/totalLosses, feed.isFresh, candleCount.
3. Foco en pantalla:
   adb -s UGU8KFUKM7E6CE8D shell "dumpsys window | grep mCurrentFocus"
4. Logcat del motor (últimas 200 líneas, filtra):
   adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | grep -Ei "SyntheticCandleEngine|TradingEngine|congelado|choppiness|Anti-Choppy|timing sniper|Headless"
5. Captura de pantalla de evidencia:
   adb -s UGU8KFUKM7E6CE8D exec-out screencap -p > .agents/loop/r1_hermes_device.png
   (o usa artemis mobile_get_device_state screenshot)

=== Contexto ===
La sesión de 25 min previa dio 0 operaciones. Queremos saber si el motor EVALÚA señales
pero las veta (log de vetos) o si está completamente inerte (sin logs de engine).

ENTREGABLE (español, máx 25 líneas):
- Estado accesibilidad (enlazado/no) y balance leído
- Veredicto: motor ACTIVO evaluando / INERTE / VETANDO todo
- Extracto literal de logcat con las razones de veto (si existen)
- Ruta de la captura de evidencia
- Cualquier crash o excepción
