TAREA — RONDA 5 · TESTER EN VIVO (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Sesión de 25 min EN CURSO (no la interrumpas). APK recién instalado con:
- Fix de journal (DSH): fallback a almacenamiento interno cuando el externo hereda UID viejo.
- R2 anti-contra-tendencia, R3 canal medio, H9 telemetría de timing de entrada.

=== OBJETIVO ESPECIAL: validar el fix del JOURNAL ===
Contexto: el directorio externo /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/
tiene UID u0_a158 (instalación vieja) mientras la app corre como u0_a179 -> EACCES.
El fix debía hacer fallback al almacenamiento INTERNO (/data/data/com.example.tradedraw/files/TradeDraw_Audits/).

=== PASOS ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
2. Espera a que ocurran trades (timeout >= 1500000 ms). Cada 60s muestrea /status
   (balance, engine.signalsGenerated, totalWins, totalLosses, hasPendingTrade).
3. TELÉMETRÍA DE ENTRADA (H9):
   adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /C:"ENTRADA despachada"
   -> captura "segundo=X" y la razón (MT_ENGULFING, QUANT_CRYPTO, etc.)
4. VERIFICAR EL JOURNAL (clave de esta ronda):
   a) Externo: adb -s UGU8KFUKM7E6CE8D shell "ls -la /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/"
   b) Interno (necesita root): adb -s UGU8KFUKM7E6CE8D shell "su -c 'ls -la /data/data/com.example.tradedraw/files/TradeDraw_Audits/ 2>/dev/null'"
   c) Contenido interno: adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv 2>/dev/null'"
   d) logcat: adb -s UGU8KFUKM7E6CE8D logcat -d 2>&1 | findstr /I "TradeJournalLogger FALLO registrar sincroniz"
   Reporta: ¿el journal se escribió? ¿en externo o interno? ¿aparecen las trazas de fallback?
5. Captura final: .agents/loop/r5_final.png

=== ENTREGABLE (español, máx 35 líneas) ===
- Nº de trades y WIN/LOSS/TIE de la sesión
- Segundos de entrada reales (de H9 logcat)
- JOURNAL: ¿se escribió el CSV? ¿en qué ruta? ¿hubo fallback? cita la traza literal
- Balance inicial vs final
- ¿Quedaron TIEs?
- Veredicto: ¿el fix del journal de DSH funciona en dispositivo?
- Rutas de evidencia
NO edites código Kotlin.
