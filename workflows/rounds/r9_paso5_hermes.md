TAREA — RONDA 9 · PASO 5 VALIDACIÓN EN DISPOSITIVO (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
APK recién instalado con FIX-A (price_y sin truncar) y FIX-B (TIE_INCIERTO). Activo: ETHUSD-OTC.

=== OBJETIVO: validar EJECUCIÓN (NO medir edge) ===
Esta sesión NO decide estrategia. Solo verifica que los 2 fixes funcionan en runtime real.

=== PASOS ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
2. Sesión de ~20 min muestreando /status cada 60s (balance, totalWins/Losses, hasPendingTrade,
   accessibilityActive, activeAsset).
3. El logcat YA se captura en streaming en: loops/journal/live/r9_logcat_stream.log
   Al final, extrae líneas "ENTRADA despachada".
4. VALIDAR FIX-A: revisa el journal interno y confirma que la columna price_y tiene decimales
   (p.ej. 1052.2225) y NO está truncada a 2 decimales:
   adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv'"
5. VALIDAR FIX-B: si ocurre un TIE, revisa si es "TIE" (saldo fresco) o "TIE_INCIERTO" (saldo no fresco).
   Registra /status.accessibilityActive en el momento de la liquidación.
6. Reportar el PAYOUT REAL de ETHUSD-OTC en Binomo (mira la pantalla; suele ser 80%).
   El breakeven = 1/(1+payout). Necesario para evaluate.py --payout.
7. Captura: .agents/loop/r9_final.png

=== ENTREGABLE (español, máx 30 líneas) ===
- activeAsset confirmado y accessibilityActive
- Nº de trades y WIN/LOSS/TIE/TIE_INCIERTO
- FIX-A: ¿price_y se guarda con decimales? cita una fila del journal
- FIX-B: ¿apareció TIE_INCIERTO? ¿accessibilityActive era false en ese momento?
- PAYOUT real de ETHUSD-OTC (y breakeven calculado)
- Balance inicial vs final
- Veredicto: ¿los 2 fixes funcionan en runtime?
- Rutas de evidencia
NO edites código Kotlin.
