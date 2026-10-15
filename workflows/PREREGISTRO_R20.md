# PREREGISTRO RONDA 20 — Recuperación de un activo sin feed y blindaje del desajuste pantalla↔feed

**Fecha:** `2026-10-15 07:40` (hora local del dispositivo).
**Rama:** `r10-night` → `main`. **Árbol de partida:** `4352db8`.

> ⚡ **Este documento se escribe y se commitea ANTES de tocar una sola línea de código.**
> Regla de gobernanza del proyecto: ningún criterio nuevo se fija después de ver el resultado.
> El diagnóstico que motiva esta ronda **ya está medido** (§1); lo que se fija aquí es **qué se
> considerará éxito y qué no**.

---

## 1. Diagnóstico medido que motiva la ronda (no es hipótesis)

El bot llevaba sin operar desde al menos el inicio de R15. Causa raíz **medida**, no inferida:

| # | hecho | evidencia |
| --: | :--- | :--- |
| 1 | `activeAsset = AAVEUSD-OTC` y ese RIC **no entrega ni un tick** | socket nuevo: `subscribe` **aceptado** (`{"field":"AAVEUSD-OTC","action":"subscribe"},"success":true,"errors":[]`) y después **1 frame (ese ack) y 0 ticks en 60 s** |
| 2 | El control positivo **sí** entrega | `ETHUSD-OTC` ≈ **2,4 ticks/s** (147 ticks / 60 s, R15 §12.2) |
| 3 | Otros RICs candidatos de Aave **también muertos** | `AAVEUSD`, `AAVE/USD` → **0 ticks** cada uno (20 s por sondeo) |
| 4 | El gráfico del bróker **sí existe y está vivo** | captura `loops/screenshots/r15/r15_estado_actual.png`: `Aave (OTC) 80%`, velas moviéndose, precio 120.73 |
| 5 | **NUEVO — un RIC no servido congela también el principal** | con `ETHUSD-OTC` sano (`acc=33`, `fresh=true`), suscribir `AAVEUSD-OTC` dejó `acc` **congelado en 36** durante 25 s y `isFresh=false`; al restaurar se recuperó (36 → **97** en 20 s) |
| 6 | La accesibilidad **reimpone** `AAVEUSD-OTC` | `ASSET visible='Aave (OTC)' ric='AAVEUSD-OTC'` en `/events`, reiterado; `/asset?name=Z-CRY/IDX` fue **pisado** |

**Consecuencia medida:** `isFresh=false`, `lastTickAgeMs ≈ 1.76e6 ms` (~29 min) → el guard
`FEED_RANCIO` de `TradingEngine.executeHeadlessTrade` (`TradingEngine.kt:1886`) hace `return`.
Por eso `tapsDespachados=0` y `W/L=0/0`.

**La estrategia NO es la causa.** `signalsGenerated=17`, `evaluatedCount=84`,
`suppressedByConfluence=5`: **12 señales atravesaron la matriz de confluencia ≥80 pts y ninguna
pudo ejecutarse**. Lo que falla es el *plumbing*, no el criterio.

**Corrección de R13.** `loops/R13_REPORTE.md:68` afirma que la causa de que Aave no diera ticks era
el mapeo RIC. Se arregló el mapeo (`aa6ae39`) a un RIC **sintácticamente** válido
(`AAVEUSD-OTC`, sin paréntesis) **pero que el servidor no sirve**. R15 §12.5 lo anotó como
hallazgo colateral **sin corregirlo**. Esta ronda lo corrige.

---

## 2. Decisión del usuario (2026-10-15)

Entre cuatro alternativas se elige **A: recuperación automática tras N s sin un solo tick**.
No se elige B (solo avisar), C (validar contra catálogo) ni D (solo diagnóstico).

---

## 3. Riesgo que la opción A introduce, y por qué hay que blindarlo

**Hecho medido que crea el riesgo:** **NO existe ningún guard que compare el activo del feed con
el activo visible en pantalla.** Verificado leyendo `executeHeadlessTrade`
(`TradingEngine.kt:1880-2060`): los guards son feed rancio, V2 (rango relativo), precio congelado,
una orden por vela, veto francotirador sintético, ventana temporal sniper, anti-chop, cooldown y
decisión adaptativa. **Ninguno compara `ws.activeAsset` contra `latestObservedAsset`.**

Por tanto, si A recupera el feed a `ETHUSD-OTC` mientras el gráfico del bróker muestra
`Aave (OTC)`, el bot **decidiría con el precio de ETH y pulsaría los botones del gráfico de Aave**.
En opciones binarias eso es una orden a ciegas sobre otro subyacente.

**Consecuencia de diseño, fijada a priori:** la recuperación automática **NO se implementa sola**.
Va acompañada de un guard **fail-closed** que veta el disparo cuando el activo del feed no coincide
con el activo observado en pantalla. Recuperar el feed es recuperar **observabilidad**; no es
autorización para operar en un gráfico distinto.

---

## 4. Criterios de aceptación (fijados ANTES de implementar)

### 4.1 Recuperación (A)

| # | criterio | cómo se mide |
| :-- | :--- | :--- |
| **A.1** | El cliente recuerda el **último RIC que entregó ticks** | campo persistido en `TradeDraw_WSConfig`; legible en `/status` |
| **A.2** | Con el socket **conectado** y **0 ticks propios** durante **≥ 20 s**, el cliente vuelve a ese RIC | umbral **20 s**, fijado aquí; el sondeo del §1 da 0 ticks en 60 s frente a 2,4 t/s del sano, así que 20 s ya es concluyente |
| **A.3** | La recuperación **no puede oscilar** (anti-flap) | el RIC declarado sin feed no se reimpone mientras dure el bloqueo |
| **A.4** | La recuperación queda en `/events` | evento `FEED_SIN_TICKS` con `activo`, `segundos` y `recuperado` |
| **A.5** | **Con `mode = DISABLED` no cambia el modo** | `mode` idéntico antes y después |

### 4.2 Guard de coincidencia (blindaje, obligatorio con A)

| # | criterio | cómo se mide |
| :-- | :--- | :--- |
| **B.1** | Si el activo del feed **≠** el activo observado en pantalla, `executeHeadlessTrade` **veta** | guard antes de cualquier click |
| **B.2** | El veto es **observable** | `/status` gana `vetoActivoDesajustado` y `/events` gana `VETO DESAJUSTE …` |
| **B.3** | Si **coinciden**, el guard **no** interviene | control positivo: no aparece ni un veto de este tipo |
| **B.4** | El guard es **fail-closed**: sin dato de pantalla **no** autoriza | si no hay activo observado, **veta** |

### 4.3 Gobernanza (invariantes)

| # | invariante |
| :-- | :--- |
| **G.1** | **NO se relaja ningún umbral de `master_traders_skill`** (ni Rechazo ≥45 %, ni Cuerpo ≤40 %, ni S/R ≤4 px, ni ventana :06-:57, ni CHOP > 61.8, ni confluencia ≥80 pts, ni el dimensionamiento 1-3,5 %). Esta ronda **solo añade restricciones**. |
| **G.2** | **NO se toca** `PREREGISTRO_R10.md`, `PREREGISTRO_R12.md` ni `PREREGISTRO_R14.md`. |
| **G.3** | La sustitución de activo **no** implementa B2a: **no** acumula candidatos ni elige por rentabilidad. Solo vuelve al último RIC que **probó** entregar ticks. |
| **G.4** | Al cierre: `mode = DISABLED`, `hasPendingTrade = false`, `orderState = NINGUNA`, verificado en `/status`. |
| **G.5** | Suite completa verde con `cleanTest testDebugUnitTest --rerun-tasks`; se reportan clases, tests y hora real de los XML. |

### 4.4 Regla de falsación

El criterio se declara **REFUTADO** (y se revierte el cambio, no se reinterpreta) si:

1. la recuperación **no** devuelve el feed a un RIC que antes entregaba ticks, o
2. la recuperación **oscila** entre dos RIC más de una vez en 5 minutos, o
3. el guard de coincidencia **veta** disparos legítimos con pantalla y feed coincidentes (B.3),
4. o la suite entra en rojo por motivos que no se puedan arreglar sin tocar lógica de trading.

**Límite de alcance declarado:** esta ronda **NO consigue que exista un RIC de Aave con feed**.
Si el servidor no sirve Aave, el bot **seguirá sin operar en Aave**, pero ahora **por decisión
explícita y observable**, no por un atasco silencioso. Se declara así en el informe.

---

## 5. Fases

0. Commit de este preregistro.
1. `ultimoRicConTicks` + watchdog de 20 s + anti-flap + evento `FEED_SIN_TICKS`.
2. Guard de desajuste pantalla↔feed + contador + evento.
3. Tests unitarios de ambas piezas.
4. `assembleDebug` + `adb install -r` + verificación en dispositivo real.
5. Informe honesto con lo verificado y lo NO verificado.
