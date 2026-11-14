# TRADEDRAW — TAREA IMPLEMENTADOR HERMES: FIX DE INFRAESTRUCTURA COMPARADOR (FASE 4 LOOP R23I)
## DESTINATARIO: HERMES (`w1:pA` / Implementador)
## REMITENTE: AGY (`w1:p1` / Orquestador)

Estimado Hermes,

Durante la Fase 2 del loop R23I (`window_06_clean`), OpenCode y AGY identificaron un **defecto reproducible de infraestructura en el comparador** `scripts/validation/compare_live_replay.py`:

### DIAGNÓSTICO DEL DEFECTO
En `compare_live_replay.py:381-382`:
```python
if por_id and match["candle_second"] >= 0 and lt["candle_second"] != match["candle_second"]:
    divergencias.append("candle_second")
```
- Cuando un par se empareja por **`decision_id` idéntico** (`por_id == True`), ambos lados representan exactamente la misma decisión de trading generada por el motor.
- Sin embargo, en Live `trades.csv` registra el timestamp de la confirmación física de saldo del broker en el teléfono (Android Go), lo que introduce entre 2 y 5 segundos de retardo respecto al tick de la señal en Replay.
- Como `lt["candle_second"] = (ts // 1000) % 60`, en Live el segundo resulta ser e.g. 6 (`cs=6`), mientras que en Replay es el segundo de la señal e.g. 1 (`cs=1`).
- La línea 381 marca artificialmente `candle_second` como divergencia a pesar de que $|\Delta t| \le 6,000$ ms y de que el `decision_id` es idéntico. Paradójicamente, si el par se empareja por fallback temporal (`por_id == False`), la línea 381 no se evalúa y se declara `MATCH`.

### TAREA DE IMPLEMENTACIÓN (SOLO INFRAESTRUCTURA)
1. Modificar `scripts/validation/compare_live_replay.py`:
   - En la verificación de `candle_second`, permitir que la diferencia $|lt["candle_second"] - match["candle_second"]|$ sea absorbida si el desfase temporal está dentro de la latencia física aceptada ($|\Delta t| \le temporal\_tolerance\_ms$ o $|cs_{live} - cs_{replay}| \le (temporal\_tolerance\_ms // 1000)$ considerando el wrap de 60s).
   - O bien, si `por_id == True` y $|\Delta t| \le temporal\_tolerance\_ms$, no penalizar la latencia física de Android Go como divergencia de `candle_second` si la vela M1 es la misma o congruente.
2. Comprobar que los tests de infraestructura sigan pasando:
   ```powershell
   ./gradlew testDebugUnitTest --tests *LiveReplayParityTraceabilityTest*
   ```
3. Re-ejecutar el comparador sobre `loops/r23i_validation/window_06_clean`:
   ```powershell
   python scripts/validation/compare_live_replay.py `
     --live-dir loops/r23i_validation/window_06_clean `
     --replay-journal loops/r23i_validation/window_06_clean/replay_journal.csv `
     --mode trades `
     --temporal-tolerance-ms 6000 `
     --out-report loops/r23i_validation/window_06_clean/REPLAY_PARITY_REPORT.md
   ```
4. **REGLA ABSOLUTA:** Estrategia 100% congelada. CERO cambios en `TradingEngine.kt`, `RiskManager.kt`, señales, confluencia o umbrales.
5. Reportar el diff exacto y resultado en `workflows/rounds/r23i_hermes_infra_fix_report.md`.
