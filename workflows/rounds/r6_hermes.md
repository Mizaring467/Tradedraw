TAREA — RONDA 6 · SUPERVISIÓN DE SESIÓN + TELEMETRÍA H10 (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Sesión de 25 min EN CURSO. APK con:
- H10: el journal ahora guarda el SEGUNDO REAL DE ENTRADA (no el de liquidación).
- Buffer de logcat subido a 16 MiB (ya no rota en segundos).
- R2/R3 y fix del journal activos.

=== PASOS ===
1. Espera y muestrea /status cada 60s durante ~18 min:
   curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   -> balance, engine.signalsGenerated/evaluatedCount, totalWins/totalLosses, hasPendingTrade.
2. El logcat YA se está capturando en streaming en:
   loops/journal/live/r6_logcat_stream.log  (filtra TradingEngine/TradeDraw/TradeJournalLogger)
   Lee ese archivo al final y extrae TODAS las líneas "ENTRADA despachada" (segundo real de entrada).
3. Journal interno (fuente de verdad):
   adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv'"
   -> verifica que la columna candle_second ahora tenga valores 1..5 (entrada), NO de liquidación.
4. Captura: .agents/loop/r6_final.png

=== ENTREGABLE (español, máx 35 líneas) ===
- Tabla: trade | segundo de entrada (H9) | segundo en journal (H10) | resultado
- Nº trades y WIN/LOSS/TIE
- ¿La columna candle_second del journal coincide con el segundo real de entrada? (esto valida H10)
- Balance inicial vs final
- ¿Quedaron TIEs?
- Rutas de evidencia
NO edites código Kotlin.
