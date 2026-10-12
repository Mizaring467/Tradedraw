TAREA — RONDA 10 · PASO 3a EXTRACCIÓN DE ENTRADAS VIVAS (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw (rama r10-night).
NO edites código. Solo extrae datos y reporta.

=== Contexto ===
Ronda 10 valida la paridad replay vs bot vivo. Necesito las ENTRADAS VIVAS del journal interno del
dispositivo desde el tag r9-measure (2026-10-11 22:xx). Los ticks grabados cubren desde 2026-10-11 23:30.

=== Pasos ===
1. Extrae el journal interno:
   adb -s UGU8KFUKM7E6CE8D shell "su -c 'cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv'"
   Guárdalo en: workflows/rounds/r10_journal_vivo.csv
2. Filtra SOLO las entradas de ETHUSD-OTC (price_y > 900, es decir ETH; IDX era ~641).
   Columnas relevantes: timestamp,iso_date,action,price_y,result,duration_sec,reason
3. Reporta la TABLA de entradas vivas: hora ISO, lado (BUY/SELL), price_y, resultado.
4. Reporta el RANGO de tiempo cubierto por esas entradas (min y max timestamp).
5. Verifica si hay ticks que cubran ese rango:
   busca en data/ticks/ETHUSD-OTC_2026-10-12.csv los ts del rango (primer y último ts del archivo).
   Usa: python scripts/backtest/data_quality.py data/ticks/ETHUSD-OTC_2026-10-12.csv   (si existe ya)

=== ENTREGABLE (español, máx 20 líneas) ===
- Ruta del CSV extraído
- Tabla de entradas vivas de ETH (hora, lado, price_y, resultado)
- Rango temporal de las entradas
- ¿Hay ticks grabados que cubran ese rango? (sí/no + rangos)
- Si NO hay cobertura → marca BLOQUEADO y sigue.
