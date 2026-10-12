TAREA — RONDA 10 · PASO 4 LÍNEA BASE DE RUIDO (agy)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). PROHIBIDO tocar app/src/main/**.
Permitido: app/src/test/**, scripts/**, data/**. NO hagas commit ni push.

=== Objetivo ===
Medir la TASA DE FALSOS POSITIVOS del propio instrumento: si alimentamos el mismo motor con series
SINTÉTICAS (sin estructura), ¿cuántas veces el IC de Wilson daría "edge" por puro azar?

=== 4A · scripts/backtest/null_baseline.py (NUEVO) ===
Genera K=100 series sintéticas a partir de los ticks ETH PREVIOS a T0 (data/ticks/ETHUSD-OTC_2026-10-12.csv
filtrado a ts < T0=1791791190823):
- Método: **barajado de retornos por bloques** (block bootstrap), preservando la volatilidad y los
  intervalos entre ticks reales. NO inventes una GBM: reusa los ΔP reales reordenados.
  Sugerencia: bloques de ~30 ticks; reordena los bloques y reconstruye la serie de precios desde el
  primer precio real. Mantén los ts (para que los huecos se preserven).
- Escribe cada serie en data/null/eth_null_<k>.csv con formato `ts,ric,price`.
- Incluye `--self-test` (aserciones: K series, mismo nº de ticks, misma media de retornos ≈ 0).

=== 4B · ReplayBacktestTest con BACKTEST_DIR ===
Extiende el test para aceptar la variable de entorno `$BACKTEST_DIR`: procesa TODOS los CSV de ese
directorio en UNA sola JVM y escribe un CSV combinado (columna extra `source` = nombre del archivo).
NO dupliques lógica de replay: reutiliza la misma ruta que usa para un CSV único.

=== 4C · Cálculo de la tasa de falsos positivos ===
Con las 100 series nulas:
- Corre el replay sobre `$BACKTEST_DIR=data/null` (con el manejo de huecos ya implementado).
- Para cada serie, calcula N y el LCL de Wilson 95% del WR.
- **Tasa de falsos positivos** = fracción de series con **LCL(Wilson 95%) > 0.5556 y N >= 200**.
  (Si ninguna serie llega a N>=200, repórtalo: es un resultado válido y relevante.)
- Reporta también el WR agrupado bajo el nulo (media y rango) — SOLO sobre data/null, NO sobre la confirmación.
- Compara con el WR real PREVIO a T0 (desarrollo), que sí puedes medir.
Añade esto como `scripts/backtest/null_baseline.py --report` o un script `null_report.py` (tú decides,
documenta el uso).

=== VERIFICACIÓN ===
1. python scripts/backtest/null_baseline.py --self-test → OK
2. Genera las 100 series en data/null/ (reporta cuántas y su tamaño)
3. Corre el replay combinado y reporta N por serie, cuántas alcanzan N>=200, y la tasa de FP.

=== ENTREGABLE (español, máx 25 líneas) ===
- Archivos creados/modificados
- 100 series generadas (verificación: mismo nº ticks, media de retornos)
- N por serie (min/mediana/max), cuántas con N>=200
- TASA DE FALSOS POSITIVOS (P(LCL Wilson > 0.5556 | N>=200) bajo el nulo)
- WR real de desarrollo (pre-T0) vs WR bajo el nulo
- Confirmación: app/src/main intacto
