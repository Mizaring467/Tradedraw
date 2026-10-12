# PREREGISTRO · Ronda 10 (nocturna)

**Fecha de preregistro:** 2026-10-12 02:46:30 (America/Bogota)
**T0 (epoch ms):** 1791791190823
**Versión de estrategia congelada:** `r9-measure` (commit 8ea9690). Rama de trabajo: `r10-night`.
**Activo primario:** ETHUSD-OTC (payout real 0.80 → **breakeven = 1/(1+0.80) = 55.56%**).
**Activos de réplica (solo datos, no decisión):** BTCUSD-OTC, SOLUSD-OTC.

## H0 (hipótesis nula)
El win rate real es **≤ 1/(1+payout)** = 55.56% con payout 0.80. Es decir: no hay ventaja sobre el azar.

## Regla de decisión (fijada ANTES de medir)
- **Desarrollo:** ticks anteriores a T0 (ya existentes). Permiten análisis exploratorio.
- **Confirmación:** ticks posteriores a T0. **No se calcula WR/EV/PnL/Wilson sobre la confirmación** hasta que
  `--solo-conteo` indique **N ≥ 200 trades** en el conjunto de confirmación.
- Cuando N_confirmación ≥ 200, se evalúa **una sola vez**:
  - Si el **límite inferior del IC de Wilson 95% > 55.56%** → **edge provisional**.
  - Si no → **sin edge**.
- El resultado de esa evaluación es definitivo para esta confirmación; no se re-ejecuta ni se "reintenta".

## Reglas de integridad
- Ninguna racha de pérdidas (en vivo o en backtest) justifica cambiar la estrategia.
- Cualquier cambio de estrategia, umbral o señal exige: **tag nuevo + preregistro nuevo + confirmación nueva**
  desde un T0' nuevo. El conjunto de confirmación actual queda invalidado.
- Prohibido tocar `app/src/main/**` durante R10. Herramientas de medición solo en `app/src/test/**` y `scripts/**`.
- El holdout/confirmación no se usa para calibrar. Cero sintonización post-T0.
- Solo cuenta DEMO.

## Manejo de datos
- Hueco > 10 s → reiniciar el motor del replay y **descartar entradas en los 120 s posteriores**.
- Timestamps duplicados → deduplicar.
- Ticks crudos no se versionan (ver `.gitignore`).

## Responsabilidades de firma
- Código de medición: lo implementa **agy**, lo **audita hermes** (diff + tests).
- Docs de opencode: los **audita agy**.
- Ningún autor firma su propio PASS.
