TAREA — RONDA 10 · PASO 3b + AUDITORÍA PASO 4 (hermes)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). NO edites código. Ejecuta y firma.

=== 3b · PARIDAD REPLAY vs BOT VIVO ===
Contexto: hay 7 entradas vivas de ETH en el journal (guardadas en workflows/rounds/r10_journal_vivo.csv).
De ellas, SOLO 3 tienen ticks que las cubran (las de ~04:35, 04:38, 06:18, 06:26 UTC); las 3 más antiguas
(03:48, 03:52, 04:19 UTC) caen antes del primer tick grabado → NO evaluables (ya documentado).

Tarea:
1. Ejecuta el replay sobre el rango cubierto (usa ReplayBacktestTest con $BACKTEST_CSV apuntando a
   data/ticks/ETHUSD-OTC_2026-10-12.csv, o replay.py).
2. Compara: para cada entrada viva COBERTURADA, ¿el replay genera una señal en la MISMA vela y MISMO lado?
   Usa el CSV combinado que produce el test (app/build/...csv) o el de replay.py.
3. % de PARIDAD = (entradas coincidentes) / (entradas coberturadas). >=80% = OK.
4. Si <80%, documenta la brecha (latencia de tap, alineación de expiración) SIN tocar la estrategia.

=== AUDITORÍA PASO 4 (agy es autor, tú firmas) ===
Revisa scripts/backtest/null_baseline.py + el replay con $BACKTEST_DIR:
A) ¿El bootstrap baraja RETORNOS por bloques preservando volatilidad e intervalos? cita líneas.
B) ¿Genera exactamente 100 series en data/null/? verifícalo con Get-ChildItem.
C) ¿El cálculo de FP es P(LCL Wilson 95% > 0.5556 | N>=200) sobre data/null? cita.
D) ¿Se abstiene de tocar la confirmación (solo usa pre-T0)? 
E) `git diff r9-measure -- app/src/main` debe estar VACÍO.

=== ENTREGABLE (español, máx 22 líneas) ===
- Tabla paridad: entrada viva | lado | lado replay | ¿coincide? | resultado
- % de paridad y veredicto (>=80% OK / BLOQUEADO si faltan datos)
- PASS/FAIL de A-E de la auditoría del Paso 4
