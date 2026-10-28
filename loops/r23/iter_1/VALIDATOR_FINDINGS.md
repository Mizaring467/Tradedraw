# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23 ITERACIÓN 1 (SHADOW MODE)

**Fecha:** 2026-10-15 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Rama/Commit auditado:** `main` @ `f5f9eaf`
**Build:** versionCode=6, versionName=1.4-r23-iter1, buildId=R23-ITER1, gitSha=f5f9eaf
**Dispositivo:** Redmi A1 (`220733SL`), Android 12, serial ADB `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Ventana:** 20,0 min · intervalo 10 s · 116 muestras de `/status`

---

## 1. VEREDICTO

**KEEP** — Sin regresión de seguridad. El binario candidato es apto para continuar el bucle.

| Criterio | Resultado | Evidencia |
|---|---|---|
| Modo permaneció en SHADOW | ✅ | `mode=SHADOW` inicio y fin de ventana |
| `tapsDespachados == 0` (estricto) | ✅ | 100 % de las 116 muestras |
| Balance inmutable | ✅ | 38.054.735,36 → 38.054.735,36 COP (discrepancia 0,00) |
| Identidad de build en runtime | ✅ | versionCode/gitSha/buildId coinciden con lo esperado |
| Órdenes shadow aprobadas | 0 | 140 decisiones, todas vetadas por V2_RANGE |
| Salud | ✅ | `READY` de principio a fin, sin transiciones a estado degradado |

**Nota sobre el bucle cerrado:** R23 expone `rangoRelativoActual` en `/status` y lo usa como
criterio de veto V2. En Crypto IDX (Z-CRY/IDX) el rango medido osciló entre 1,09e-10 y 6,62e-10,
siempre muy por debajo del umbral 1,00e-05, por lo que **el 100 % de las 140 señales fueron vetadas
y ninguna llegó a fase de `SHADOW_ORDER`**. La telemetría de rango es observable, pero la ruta de
aprobación shadow **no quedó ejercitada** en esta ventana (falta de prueba, no defecto).

---

## 2. HALLAZGO BLOQUEANTE (tooling) — Paridad Live↔Replay NO MEDIBLE

El monitor computa y adjunta el bloque de paridad a `LIVE_REPORT.md`, pero el informe generado
declara `0 exact matches de 0 pareadas (100,0 % de paridad)`. **Ese 100 % es un artefacto de
división 0/0 y no está respaldado por ninguna comparación real.**

### Causa raíz (4 defectos encadenados, reproducible)

1. **Argumentos incompatibles.** `live_monitor_r23.py` (líneas 250-255) invoca al comparador con
   `--live-csv / --out-json / --out-md`, pero `compare_live_replay.py` solo acepta
   `--live-journal / --output-json / --output-md`. Resultado: `exit code 2`
   (`unrecognized arguments`), `stderr` no capturado, y **ningún fixture de paridad generado por el monitor**.

   Evidencia (ejecutado por el validador):
   ```
   compare_live_replay.py: error: unrecognized arguments: --live-csv ... --out-json ... --out-md ...
   EXIT=2
   ```

2. **Sin fuente de replay.** Aun corrigiendo los nombres, el monitor nunca pasa `--replay-journal`
   ni `--ticks-csv` al comparador → `replay_records = []` → las 140 decisiones en vivo resultan
   `MISSING_REPLAY` → `match_rate = 0 %`.

3. **IDs incompatibles.** Las decisiones en vivo usan UUID v4 (p. ej. `ba4aa203-…`). Los journals de
   replay existentes (`data/ticks/replay_eth_live.csv`, `data/ticks/replay_trades.csv`,
   `app/build/backtest_out.csv`) **carecen de columna `decision_id`** y son de R21/R22 sobre
   ETHUSD-OTC. El comparador les asigna IDs sintéticos `SYNTH-<ts>-<action>` que **jamás** casan con
   los UUID → paridad por `decision_id` estructuralmente imposible con estas fuentes.

4. **Esquema de columnas incompatible.** El CSV live del monitor expone `detail` (no `reason`) y no
   expone `strategy`; el comparador extrae confluencia/veto de `reason` y estrategia de `strategy`
   → degrada a defaults (`Confluencia 80`, `veto NONE`).

### Evidencia del fixture correctivo

Ejecución del comparador con los **argumentos correctos** por parte del validador:
```
Decisiones auditadas: 140
Pares evaluados:      0
Exact Matches:        0
Missing en Replay:    140
Tasa de paridad:      0.0% -> FAIL
```
Fixtures generados correctivamente: `replay_comparison.json`, `replay_comparison_report.md`.

### Impacto
El "Reporte de Validación Live" afirma una paridad del 100 % que no existe. Bajo gobernanza de
bucle cerrado, esto es una **falsa señal de conformidad**: el criterio de paridad ≥ 80 % no fue
evaluado y, con las fuentes actuales, no puede serlo.

---

## 3. RECOMENDACIÓN

- **Binario:** KEEP (seguridad intacta).
- **Tooling R23:** antes de la iteración 2, alinear la invocación del comparador
  (`--live-journal/--output-json/--output-md`, añadir `--replay-journal` o `--ticks-csv`) **y**
  dotar al journal de replay de columna `decision_id` con los mismos UUID (o de un mapeo
  determinista). Mientras no se resuelva, tratar el bloque "Paridad Live↔Replay" del
  `LIVE_REPORT.md` como **NO CONCLUYENTE**.

---

## 4. ANEXO — Artefactos auditados

| Artefacto | Estado |
|---|---|
| `loops/r23/iter_1/live_samples.jsonl` | ✅ 116 muestras |
| `loops/r23/iter_1/status_samples.jsonl` | ✅ 116 volcados de `/status` |
| `loops/r23/iter_1/events.jsonl` | ✅ presente |
| `loops/r23/iter_1/decision_log.json` | — (no generado por el monitor) |
| `loops/r23/iter_1/decisions.csv` | ✅ 141 líneas (1 cabecera + 140 decisiones, 140 IDs únicos) |
| `loops/r23/iter_1/mode_log.json` | ✅ presente |
| `loops/r23/iter_1/LIVE_REPORT.md` | ✅ generado (verdicto KEEP) — bloque de paridad marcado NO CONCLUYENTE |
| `loops/r23/iter_1/replay_comparison.json` | ⚠️ generado correctivamente por el validador |
| `loops/r23/iter_1/replay_comparison_report.md` | ⚠️ generado correctivamente por el validador |
| `loops/r23/iter_1/monitor_stdout.log` | ✅ traza completa del monitor |
