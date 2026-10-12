TAREA — RONDA 10 · PASO 2 AUDITORÍA (hermes)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). NO edites código. Audita y firma.

=== Contexto ===
agy implementó el Paso 2 (herramientas de medición). Como agy es el AUTOR, tú (hermes) auditas.

=== Revisa el diff (git diff r9-measure) sobre SOLO estos archivos ===
1. scripts/backtest/data_quality.py (NUEVO)
2. scripts/backtest/replay.py (modificado: huecos >10s -> reinicio + veto 120s; dedup; --solo-conteo)
3. scripts/backtest/evaluate.py (modificado: --solo-conteo)
4. app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt (modificado: misma regla de huecos)

=== Verificaciones (firma PASS/FAIL por cada una) ===
A) REGLA 1: `git diff r9-measure -- app/src/main` debe estar VACÍO. (comando literal)
B) HUECOS: ¿el replay reinicia el motor tras un hueco >10 s y descarta entradas en los 120 s posteriores?
   Cita las líneas exactas en replay.py y ReplayBacktestTest.kt.
C) DEDUP: ¿se deduplican timestamps en ambos? cita.
D) --solo-conteo: corre `python scripts/backtest/evaluate.py <csv> --solo-conteo` con un CSV de trades
   existente y DEMUESTRA que la salida NO contiene WR/EV/PnL/Wilson. Pega la salida.
E) Self-tests: corre los 3 y confirma OK:
   python scripts/backtest/data_quality.py --self-test
   python scripts/backtest/evaluate.py --self-test
   python scripts/backtest/replay.py --self-test
F) ¿Existe build.gradle.kts o algún archivo en app/ que NO deba tocarse? Confirma que agy no tocó
   app/build.gradle.kts ni app/src/main.

=== ENTREGABLE (español, máx 20 líneas) ===
- PASS/FAIL por punto A-F con evidencia
- Copia literal de la salida de --solo-conteo (D)
- Cualquier archivo modificado fuera de lo permitido (debe estar VACÍO)
