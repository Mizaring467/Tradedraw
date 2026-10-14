# PREREGISTRO R12 — Activo por defecto, vetos de defensa y guardas de escala

**Fecha de fijación:** 2026-10-13 (antes de implementar los vetos y antes de observar
cualquier resultado de la ronda).
**Estado:** umbrales congelados. No se modifican tras ver resultados; cualquier cambio
posterior exige un preregistro nuevo.
**Alcance:** decisiones de motor de esta ronda. NO modifica `workflows/PREREGISTRO_R10.md`.

---

## 1. Decisión B2 tomada por el usuario

**Opción C) ETHUSD-OTC por defecto + dos vetos como defensa.**

Justificación con datos medidos (no con impresiones), rango relativo del día completo en
`data/ticks/`:

| Activo | n ticks | Precio ref. | Rango/vela 60 ticks | Rango relativo día | ¿Guardas muerden? |
| :--- | ---: | ---: | ---: | ---: | :--- |
| BTCUSD-OTC | 48.910 | 100.914,1940 | 1,877e+01 | 1,144e-02 | No |
| **ETHUSD-OTC** | 34.260 | **1.072,4260** | **9,430e-01** | **4,164e-02** | **No** |
| SOLUSD-OTC | 32.228 | 32,1150 | 6,000e-02 | 4,665e-02 | No |
| Z-CRY/IDX | 458 | 641,8673 | 3,950e-07 | 2,547e-09 | **Sí** |

Z-CRY/IDX tiene **8 órdenes de magnitud** menos rango relativo que ETHUSD-OTC. Con
`sorosPayoutRate` real de 0,82, el breakeven es `1/(1+0,82) = 54,95 %`: operar un activo
cuyo movimiento cabe en el error de redondeo no es una estrategia, es una esperanza.

## 2. Umbrales congelados de los dos vetos nuevos

Se fijan aquí **antes** de medir su efecto. Los dos son vetos duros: si se cumplen, la
orden no se dispara.

### Veto V1 — Exclusividad mutua S/R ↔ anti-chop

- **Condición de disparo:** `HudCoherence.actionConflict(srStatus, actionPlan) == true`.
- **Ya existe** como función pura (`HudCoherence.kt:75`) y estaba **solo etiquetando** en el
  HUD (`⚠ CONFLICTO S/R↔PLAN`, evidencia `clock_03.png`). Pasa a **vetar**.
- **Justificación:** el filtro S/R y el plan de acción pidiendo direcciones opuestas es una
  contradicción interna, no una señal débil. Caso real capturado en R11.
- **Umbral:** ninguno nuevo. Se reutiliza la detección existente sin relajarla.

### Veto V2 — Feed sin volatilidad medible

- **Condición de disparo:** `recentPriceRangeRatio() < RANGO_RELATIVO_MINIMO` durante la
  ventana evaluada.
- **`RANGO_RELATIVO_MINIMO = 1e-5`** (0,001 %): por debajo, el activo no ha recorrido en la
  ventana ni una milésima de por mil de su precio; no hay nada que operar.
- **Ventana:** la que ya usa `BinomoWebSocketClient.recentPriceRangeRatio()`, sin cambios.
- **Margen respecto a lo medido:** ETHUSD-OTC opera en 4,16e-02, **tres órdenes de magnitud
  por encima** del umbral. Z-CRY/IDX se queda en 2,5e-09, **cuatro órdenes por debajo**.
  El umbral separa ambos casos con holgura amplia; no es un ajuste fino sobre el ruido.
- **No se aplica a un activo concreto**: es un veto por condición de feed, no por nombre.

## 3. Guardas de escala corregidas (B3) — no tocan decisión sobre ETHUSD-OTC

Ambas guardas eran **absolutas** y solo mordían en activos de escala diminuta. En
ETHUSD-OTC el valor real supera el umbral por 4-5 órdenes de magnitud, así que la
corrección **no cambia ninguna decisión sobre el activo por defecto**: es defensa en
profundidad, no un cambio de estrategia.

| Guarda | Antes | Después | Efecto en ETHUSD-OTC |
| :--- | :--- | :--- | :--- |
| `SyntheticCandle.range` | `coerceAtLeast(0.00001)` | relativo a `close` (1e-12) | Ninguno (rango real 0,94) |
| `calculateTickZScore` | `stdDev > 0.000001` | relativo a `mean` (1e-12) | Ninguno (sd real 0,27) |
| `atrNorm` (canal/ATR) | `coerceAtLeast(1e-7)` | relativo a `currentPrice` | Ninguno |

En Z-CRY/IDX el efecto medido era: mecha de rechazo real del 50 % leída como **0,008-0,10**
frente al umbral 0,45 de `MT_REJECTION` (nunca disparaba) y z-score **siempre 0.0** (mataba
`QUANT_CRYPTO`, que exige |z| ≥ 1,9).

## 4. Reinicio del conteo de confirmación

**N = 0.** El conteo de muestras válidas para confirmar edge se reinicia desde el SHA de
este cambio, y se declara así en el informe. Motivo: cambian el activo por defecto, las
guardas de escala y se añaden dos vetos. Las muestras anteriores no son comparables.

## 5. Lo que este preregistro NO autoriza

- No relaja ninguna tolerancia de `master_traders_skill` (mecha ≥ 0,45, cuerpo ≤ 0,40,
  S/R ≤ 4 px, ventana :58-:03 / :01-:05, veto :06-:57, CHOP > 61,8, DojiRatio ≥ 0,35).
- No autoriza operar en real. Solo cuenta demo y stake mínimo.
- No autoriza dejar el bot operando sin supervisión.
