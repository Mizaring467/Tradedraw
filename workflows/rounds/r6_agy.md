TAREA — RONDA 6 · AUDITORÍA DE EDGE ACUMULADO (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código. Analiza y reporta.

=== Contexto ===
H10 (nuevo): el journal ahora registra el SEGUNDO REAL DE ENTRADA en la columna `candle_second`
(antes registraba el de liquidación, sesgando el análisis). Buffer de logcat subido a 16 MiB.

=== Tu tarea ===
1. Extrae el journal interno del dispositivo:
   adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv'"
   guárdalo en workflows/rounds/r6_journal.csv
2. Analiza (excluye TIE del cálculo de WR):
   - WR global, WR por action (BUY/SELL), WR por trend, WR por candle_second (de ENTRADA ahora),
     WR por market_regime (CHOP/TRENDING), WR por dist_support/dist_resistance buckets.
   - Verifica: ¿la columna candle_second ahora está en 1..5 (entrada) o sigue en valores >5?
3. Compara con el análisis previo (workflows/rounds/r4_journal_full.csv, N=75) para ver si el WR
   mejoró tras R2/R3/H10.

=== ENTREGABLE (español, máx 30 líneas) ===
- Tabla de WR por dimensión (con N)
- ¿H10 corrigió la columna candle_second? evidencia
- ¿El WR mejoró vs r4? (compara)
- Subconjuntos con WR >= 55% y N >= 8
- 2-3 recomendaciones accionables
