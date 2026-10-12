# Ronda 10 (nocturna, 5 h) · Validar el instrumento de medición — Reporte del orquestador (opencode)

**Ventana:** 2026-10-12 02:46:30 → 07:50 (~5 h). **T0 = 1791791190823.** Rama `r10-night`. Estrategia congelada `r9-measure` (8ea9690). **Regla 1 cumplida: `git diff r9-measure -- app/src/main` = VACÍO** (producción intacta).

## Qué se hizo (con firmas)
| Paso | Trabajo | Autor | Firma PASS |
|---|---|---|---|
| 1 | Preregistro `workflows/PREREGISTRO_R10.md`, `data/T0.txt`, `.gitignore` (CSV de ticks), rama r10-night, grabador+supervisor | opencode | **agy** (PASS; `app/src/main` vacío confirmado) |
| 2 | `data_quality.py`, replay robusto a huecos (>10 s → reinicio + veto 120 s), dedup, `--solo-conteo` en replay.py/evaluate.py | agy | **hermes** (A–F PASS; self-tests 8/17/22 OK; `--solo-conteo` sin WR/EV/PnL) |
| 3a | Entradas vivas del journal | **BLOQUEADO parcial** | — |
| 3b | Paridad replay vs bot vivo | hermes | **100% (1/1) — n=1, baja potencia (honesto)** |
| 4 | `null_baseline.py` (K=100 series por block-bootstrap) + `$BACKTEST_DIR` | agy | **hermes** (A–E PASS; C con salvedad) |
| 5 | Supervisión horaria (6 líneas en `r10_progress.md`) | hermes | exit 0 |

Commits: `da15cc9` (herramientas+preregistro) · HEAD final del reporte. Tag **`eval-r10`**.

## Mediciones (solo las permitidas)
- **Tasa de falsos positivos del nulo:** **0.00%** (0/100 series con LCL Wilson > 55.56% | N≥200). **Salvedad:** ninguna serie alcanzó N≥200 (N min/med/max = 21/32,5/43), así que el umbral **no se pudo ejercitar** → FP=0% es válido pero **inconcluyente**.
- **WR de desarrollo (pre-T0, ETH):** 38.46% (Dev N=13) / 42.11% (total N=19). **Cae dentro de la distribución nula** (media 47.57%, rango [28.57%, 62.50%]) → **consistente con "sin edge"**, no lo demuestra ni lo refuta.
- **Paridad replay↔vivo:** 100% pero **n=1** (3 de 7 entradas sin cobertura; el replay emitió en 1 de 4 velas cubiertas).

## Recolección (ticks/h reales)
- **ETHUSD-OTC** ~4.650 ticks/h · 23.929 post-T0 · **N proyectado ≈ 82**
- **BTCUSD-OTC** 34.267 post-T0 · **SOLUSD-OTC** 22.364 post-T0
- **Huecos >10 s (ETH):** 3 → **4** (1 nuevo entre 06:07 y 07:07; pico inicial de 3.700 s). Cobertura previa 78.9%.
- Reinicios del supervisor: **0**. Heartbeat sano en las 6 muestras.

## BLOQUEADOS
1. **Paso 3a/3b parcial:** 3 de 7 entradas vivas caen antes del primer tick grabado (hueco inicial) → no replayables. Paridad medible = n=1.
2. **N≥200 NO alcanzado:** 23.929 ticks post-T0 ≈ **82 trades proyectados** en 5 h. Faltan **~12 h** de captura pasiva para cruzar el umbral de confirmación.

## Decisión de mañana
- **NO se calculó WR/EV/Wilson de la confirmación** (N<200): la confirmación sigue **abierta**, se evalúa una sola vez al cruzar N≥200.
- **NO hay base para afirmar edge** (ni falta de edge) sobre la confirmación: muestra insuficiente. El desarrollo cae dentro del nulo.
- Continuar recolección pasiva de ticks ETH (y BTC/SOL como réplica) **sin tocar estrategia**; relanzar el supervisor por tramos (`scripts/ticks/supervisor.ps1 -Minutes 300`) o varias noches hasta N≥200.
- Revisar la cobertura del grabador (hueco nuevo) antes de confiar en conteos de señales.
- La evaluación final debe usar el tag **`eval-r10`**.
