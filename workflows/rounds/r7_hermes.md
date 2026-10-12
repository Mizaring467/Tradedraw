TAREA — RONDA 7 · TESTER EN ACTIVO VOLÁTIL (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Sesión de 25 min EN CURSO. **CAMBIO MAYOR: el activo migró de `Z-CRY/IDX` (línea muerta) a
`ETHUSD-OTC` (Ethereum OTC, volatilidad real ×160.000).** Esto es lo que hay que validar.

Estado: modo AUTONOMOUS / CONSERVATIVE, accesibilidad activa, Binomo en "Ethereum (OTC)", bridge tcp:8080.

=== OBJETIVO ===
Medir si con un ACTIVO VOLÁTIL REAL el motor:
(a) genera MÁS señales (frecuencia),
(b) EJECUTA órdenes reales (root tap + bypass HUD),
(c) registra en el journal interno (fix DSH),
(d) tiene mejor WR que sobre la línea muerta.

=== PASOS ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   (anota activeAsset, recentPriceRangeRatio, engine.signalsGenerated/evaluatedCount, balance)
2. Muestrea cada 60s durante ~18 min: balance, signalsGenerated, evaluatedCount, suppressedByConfluence,
   totalWins/totalLosses, hasPendingTrade.
3. Al final, extrae del logcat los segundos de entrada:
   adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /C:"ENTRADA despachada"
4. Journal interno:
   adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv'"
5. Report json: loops/journal/live/live_session_25m_report.json
6. Captura: .agents/loop/r7_final.png

=== ENTREGABLE (español, máx 35 líneas) ===
- Confirmación del activo activo (ETHUSD-OTC) y su recentPriceRangeRatio
- engine.* inicial vs final: ¿cuántas señales? ¿cuántas evaluadas? ¿cuántas suprimidas?
- Nº de trades ejecutados y WIN/LOSS/TIE (del journal interno y report)
- Comparación de FRECUENCIA vs rondas anteriores sobre Z-CRY/IDX (que daban ~1-4 señales/25min)
- ¿El journal interno registró los trades de hoy?
- Balance inicial vs final
- Veredicto: ¿el activo volátil desbloquea frecuencia/edge?
- Rutas de evidencia
NO edites código Kotlin.
