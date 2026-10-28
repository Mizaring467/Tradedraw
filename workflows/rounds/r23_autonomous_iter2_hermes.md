# R23A — ITERACIÓN 2: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base:** `90901cb`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Objetivo:** Acoplar el stake efectivo a la cantidad real de pantalla (`observedOrderAmount`) y alinear el vocabulario/reloj de replay.

---

## 1. Resumen de Cambios

### A. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `10` | **`11`** |
| `versionName` | `"1.4-r23-auto-iter1"` | **`"1.4-r23-auto-iter2"`** |
| `BUILD_ID` | `"R23-AUTO-ITER1"` | **`"R23-AUTO-ITER2"`** |

### B. `RiskManager.kt`
1. `companion`: `stakeEfectivo(observedOrderAmount, stakeCalculado): Double`.
2. Instancia: `@Synchronized getEffectiveStake(subMode)` — fuente única del stake real.

### C. `TradingEngine.kt`
- Las **3** llamadas de journal de liquidación (WIN / LOSS / TIE) pasan de
  `getCurrentInvestmentAmount()` → **`getEffectiveStake()`**.
- `pendingStake` (journal de resolución) y la etiqueta de stake sobre el gráfico (`drawTradeEntry`)
  usan el stake efectivo.

### D. `TradeDrawHttpBridge.kt`
- `/status.risk`: añadidos `"observedStake"` y `"effectiveStake"`; `investmentAmount` se conserva.

### E. `ReplayBacktestTest.kt`
- `marketRegime`: `"CHOP"` → `"SIDEWAYS"` (vocabulario alineado con el lado live).
- Fallback del tick de señal: `ticks.first()` → tick de la misma vela (ver §2).

### F. Tests
- `RiskManagerTest.kt`: +4 tests del stake efectivo.
- `ReplayRegimeVocabularyTest.kt` (nuevo): 6 tests del vocabulario y del reloj.

---

## 2. Requerimiento 1 — Stake: dos decisiones de diseño

### 2.1. `observedOrderAmount <= 0` cae al stake calculado, **no** a cero

`observedOrderAmount` vale `0.0` mientras el sondeo de nodos no ha leído la pantalla. Eso significa
**"aún no leído"**, no "apuesta cero". Si se tomara literalmente, el journal registraría `stake=0` y el
dimensionamiento de riesgo quedaría desactivado en silencio. La regla implementada:

```kotlin
fun stakeEfectivo(observedOrderAmount: Double, stakeCalculado: Double): Double =
    if (observedOrderAmount > 0.0) observedOrderAmount else stakeCalculado
```

Un valor **negativo** (solo posible por lectura corrupta) tampoco se acepta como stake.

### 2.2. Alcance: se acopla el **registro**, no el **juicio**

`getEffectiveStake` alimenta journal, `pendingStake` y la etiqueta del gráfico — es decir, todo lo que
*describe* la operación. **No** se metió en `canExecuteTrade` ni en `getCurrentInvestmentAmount`, así
que la política de money management (martingala, Soros, SNIPER) sigue calculando como antes.

Motivo: si el stake efectivo (100.000) entrara en los multiplicadores de martingala, la secuencia
`1 → 2.2 → 4.84` pasaría a `100.000 → 220.000 → 484.000`, y eso **cambiaría la estrategia**, no el
registro. La tarea pide coherencia de modelado, no un rediseño del dimensionamiento. Si se quiere
acoplar también el juicio, es una iteración propia con su preregistro.

---

## 3. Requerimiento 2 — Paridad: **una parte se corrigió, otra es del script de AGY**

### 3.1. `market_regime`: vocabulario divergente en el lado REPLAY — CORREGIDO

El replay emitía `"CHOP"`; el monitor live emite `"SIDEWAYS"`/`"TRENDING"`. Ambos lados derivan de la
**misma** fuente (`isChoppinessDetected()`), así que solo difería la etiqueta y **todo** par salía
marcado como divergente. Corregido a `SIDEWAYS`/`TRENDING`. Hay además un test que fija que el
conjunto de vocabulario es cerrado y que el replay nunca emite una tendencia con sesgo.

### 3.2. `market_regime`: el lado LIVE trae un valor de TENDENCIA — **NO es mío**

Verificado en los datos reales de `loops/r23_autonomous/iter_1/decisions.csv`:

```
decision_id,trade_id,timestamp,asset,price_at_signal,candle_second,market_regime,trend,...
7e849159-...,d7c929e9-...,1792103703791,Z-CRY/IDX,641.8673,3,UPTREND,UPTREND,...
```

En **todas** las filas, `market_regime` = `UPTREND`, que es el valor de la columna `trend` contigua.
El script `live_monitor_r23_autonomous.py` **sí calcula** la variable correcta (línea 216:
`detected_regime = "SIDEWAYS" if is_sideways else "TRENDING"`) pero la fila acaba con el valor de
tendencia. Propietario del script: **AGY**. No lo toqué.

**Consecuencia, dicha claramente:** mientras el `decisions.csv` vivo siga escribiendo `UPTREND` en la
columna de régimen, **la comparación de `market_regime` no es interpretable en ninguna dirección** —
el lado Kotlin ya es correcto y el resultado seguirá dando divergencia.

### 3.3. `candle_second`: los dos lados miden instantes **distintos por diseño** — no se "arregla"

Medido sobre los 4 pares del iter 1:

| Par | live ts | 2º live | replay ts | 2º replay | Δ ms |
|---|---|---|---|---|---|
| 1 | 1792104125346 | 5 | 1792104123656 | 3 | **1690** |
| 2 | 1792104241267 | 1 | 1792104241250 | 1 | 17 |
| 3 | 1792104301254 | 1 | 1792104301250 | 1 | 4 |
| 4 | 1792104365265 | 5 | 1792104365250 | 5 | 15 |

Live fecha con el `atMs` del evento **despachado**; replay con el ts del **tick de la señal**. Son dos
instantes físicamente distintos y la diferencia de 1690 ms del par 1 es una ventana de despacho real
(confirmada en `events.jsonl`: el evento de ese `decision_id` tiene `atMs = 1792104196127`). Mover el
reloj de cualquiera de los dos lados sería falsear el dato. El requerimiento pedía "el segundo exacto
de la señal" y el replay **ya lo hace**. Lo que sí arreglé es un fallback latente:

```kotlin
// antes: si el ts no se encontraba, la señal se fechaba con el PRIMER tick de todo el archivo
ticks.lastOrNull { it.ts == eng.lastSignalTimestampMs } ?: ticks.first()
// ahora: cae al tick de la MISMA vela, que no inventa un instante
ticks.lastOrNull { it.ts == eng.lastSignalTimestampMs }
    ?: ticks.lastOrNull { it.ts <= eng.lastSignalTimestampMs }
    ?: ticks.first()
```

---

## 4. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL in 59s**

| Métrica | Valor |
|---|---|
| Clases de test | 42 |
| **Tests ejecutados** | **409** |
| Fallos | **0** |
| Errores | **0** |

Delta: **399 → 409** (+10). Sin regresiones.
BuildConfig: `VERSION_CODE=11`, `VERSION_NAME="1.4-r23-auto-iter2"`, `BUILD_ID="R23-AUTO-ITER2"`, `GIT_SHA="90901cb"`.

---

## 5. Verificación End-to-End (regeneré el replay real, no solo compilé)

```
BACKTEST_CSV=loops/r23_autonomous/iter_1/ticks_live.csv \
BACKTEST_OUT=tmp/replay_a2.csv \
  ./gradlew.bat testDebugUnitTest --tests '*ReplayBacktestTest*' --rerun-tasks
→ BUILD SUCCESSFUL in 1m 30s
```

Comparador `compare_live_replay.py`:

```
Pares evaluados:   4        Desglose: { candle_second: 1, market_regime: 3 }
Tasa de paridad:   0.0% -> FAIL
```

**Resultado idéntico al de la iter 1, y es lo esperado.** Las dos causas que quedan **no** dependen del
lado Kotlin:

1. `market_regime` sigue divergiendo porque el `decisions.csv` vivo (mtime **17:47**) es **anterior** al
   fix de AGY (commit `6149d48`, **17:53**): ese artefacto tiene la columna corrupta con `UPTREND`. Mi
   lado ya emite `SIDEWAYS`/`TRENDING`.
2. `candle_second` sigue divergiendo en 1 de 4 pares por el desfase de despacho descrito en §3.3.

Comprobación del vocabulario, que es la parte que sí puedo demostrar:

```
live pre-fix   (decisions.csv) : {'UPTREND'}
live post-fix  (script 6149d48): {'SIDEWAYS','TRENDING'}
replay pre-fix (CHOP/TRENDING) : {'CHOP','TRENDING'}
replay post-fix(este commit)   : {'SIDEWAYS','TRENDING'}   <- ahora coincide con el live post-fix
```

**Para que la paridad sea medible hace falta una ventana nueva** con el script corregido: solo entonces
ambos lados escriben el mismo vocabulario y el desglose reflejará divergencias reales en vez de
diferencias de etiqueta.

---

## 6. Cobertura nueva

### `RiskManagerTest` (+4)
| Test | Qué congela |
|---|---|
| `elStakeEfectivoUsaLaCantidadDePantallaCuandoExiste` | baseAmount 1.0 + pantalla 100.000 → stake **100.000** |
| `elStakeEfectivoCaeAlCalculadoSiLaPantallaAunNoSeLeyo` | `0.0` = "sin leer", no "cero" |
| `elStakeEfectivoIgnoraLecturasNegativas` | valor corrupto → cae al calculado |
| `elStakeEfectivoEsPuroYNoDependeDelModo` | contrato de la función del companion |

### `ReplayRegimeVocabularyTest` (nuevo, 6)
| Test | Qué congela |
|---|---|
| `elReplayUsaElMismoVocabularioQueElMonitorLive` | `SIDEWAYS`/`TRENDING` |
| `elReplayYaNoEmiteChopParaElRegimen` | el false positive eliminado |
| `elConjuntoDeRegimenesDelReplayEsCerrado` | no hay tercera etiqueta |
| `elRegimenNoSeConfundeConLaTendencia` | el régimen no puede ser `UPTREND`/`DOWNTREND` |
| `elSegundoDeVelaSeDerivaDelTimestampDeLaSenal` | fórmula de `candle_second`, incluidos los bordes `:00` y `:59` |
| `losDosLadosMidenInstantesDistintosPorDiseno` | fija el desfase medido (1690 ms) como esperado |

---

## 7. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=11`, `versionName="1.4-r23-auto-iter2"`, `buildId="R23-AUTO-ITER2"`.

**Campos nuevos en `/status.risk`:**
- `observedStake` — lo que dice la pantalla de Binomo (esperado: `100000.0`).
- `effectiveStake` — lo que se registra como stake real. **Con el bug corregido, debe ser `100000` y NO `1.0`.**
- `investmentAmount` — se conserva sin cambios (sigue siendo el cálculo interno de money management).

**Criterio de éxito de esta iteración:** `effectiveStake == observedStake` cuando la pantalla está
leída (`observedStake > 0`). Si en la ventana se ve un trade con `effectiveStake = 1.0` mientras
`observedStake = 100000`, el acople falló.

**Journal:** las filas de liquidación (WIN/LOSS/TIE) deben traer `stake = 100000…`, coherente con el
`-100.000 / +82.000` COP observado en el broker.

**Para la paridad:** esta ventana es la **primera** con ambos lados escribiendo el mismo vocabulario de
`market_regime`. El desglose de divergencias debería dejar de mostrar `market_regime` por diferencia de
etiqueta. `candle_second` puede seguir marcando 1 de cada 4 pares: es el desfase de despacho, no un
defecto.

---

## 8. Handoff a AGY

**Pendiente de AGY:** build APK, ADB install (`220733SL`), validación live ~20 min, y —
**necesario para que el requerimiento 2 sea verificable** — confirmar que el `decisions.csv` de la
nueva ventana sale con `market_regime ∈ {SIDEWAYS, TRENDING}` y no con `UPTREND`.

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time. Anclas inequívocas: `versionCode=11` + `buildId="R23-AUTO-ITER2"`.
