# R23H FASE 2 — CORRECCIÓN DE INFRAESTRUCTURA DE TRAZABILIDAD Y PARIDAD LIVE ↔ REPLAY

**Fecha:** 2026-10-18
**Ejecutor:** HERMES (`w1:pA`)
**Documento de misión:** `workflows/rounds/r23h_task_hermes_parity_fix.md`
**Estrategia:** CONGELADA — verificada con test dorado, no solo declarada

---

## 1. VEREDICTO

Los 4 cambios de infraestructura están implementados y la suite está **verde (466 tests, 0 fallos)**.

Pero hay dos desviaciones del guion que hay que leer antes que nada:

1. **El match rate de W03 subió de 0,0% a 50,0%, no a 100%.** La causa raíz que quedaba no era la tolerancia temporal ni el podado de ticks: es que el replay de W03 se había corrido con `CONSERVATIVE` cuando la ventana viva fue `YOLO`. Con el submode correcto y el veto replicado, quedan 4 trades live que el replay no reproduce.
2. **El cambio 1 se implementó distinto a como estaba especificado**, porque la especificación no compilaba. Detalle en §3.

Ningún valor de estrategia cambió. Lo demuestro en §6 con un test de valores dorados, no con una afirmación.

---

## 2. ARCHIVOS MODIFICADOS

| Archivo | Δ | Qué |
|---|---|---|
| `app/src/main/java/com/example/tradedraw/TradingEngine.kt` | +90/−34 | Extracción de `calcularPoderesDeMercado` (refactor mecánico) |
| `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt` | +30/−3 | Veto de clímax replicado en el arnés |
| `app/src/test/java/com/example/tradedraw/LiveReplayParityTraceabilityTest.kt` | nuevo, 300 líneas | Suite de trazabilidad (6 tests) |
| `scripts/validation/live_monitor_r23_autonomous.py` | +25/−6 | Poda de ticks pre-ventana |
| `scripts/validation/compare_live_replay.py` | +13/−4 | Tolerancia temporal 3000 → 6000 ms (función **y** CLI) |

---

## 3. DESVIACIÓN DEL CAMBIO 1 (el especificado no compilaba)

La especificación pedía:

```kotlin
motivoVetoPorAgotamientoDeCompra(
    action = action,
    trend = eng.detectedTrend.name,   // <-- String
    callPower = eng.signalPowerCall    // <-- no existe
)
```

Tres problemas reales:

1. El primer parámetro es `TrendDirection`, no `String`. `eng.detectedTrend.name` no compila.
2. `SyntheticCandleEngine` **no tiene** `signalPowerCall`. El `callPower` se calculaba dentro de
   `TradingEngine.buildAnalysisFromSyntheticEngine`, que es `private` y necesita `MarketTick`.
3. `TradingEngine.motivoVetoPorAgotamientoDeCompra` es una función *top-level* del archivo, no un
   miembro: `TradingEngine.` delante no resuelve.

**Solución aplicada.** El cálculo de `callPower` se extrajo de `buildAnalysisFromSyntheticEngine` a
una función pura `calcularPoderesDeMercado(...)`, copiada **literalmente**, misma secuencia de
constantes y mismo orden (`coerceIn` antes de normalizar a 100). El motor real y el arnés de replay
ahora llaman a la MISMA función, así que no pueden divergir.

La alternativa barata —duplicar la fórmula dentro de `ReplayBacktestTest`— habría garantizado
divergencia silenciosa al primer ajuste de estrategia: exactamente el tipo de defecto que esta fase
existe para eliminar.

---

## 4. LOS CUATRO CAMBIOS

### Cambio 1 — Arnés de replay
Veto replicado dentro del callback de señal, **antes** de marcar la vela como operada:

```kotlin
val (callPowerEnSenal, _) = calcularPoderesDeMercado(
    trend = eng.detectedTrend,
    distSupportRatio = eng.distanceToSupportRatio,
    distResistanceRatio = eng.distanceToResistanceRatio,
    rsi = eng.syntheticTickRsi,
    isBullishImpulse = tickImpulseBullish,
    isBearishImpulse = tickImpulseBearish
)
if (motivoVetoPorAgotamientoDeCompra(action, eng.detectedTrend, callPowerEnSenal) != null) {
    return@senal   // no simula trade; NO marca la vela
}
```

Detalle que costó encontrarlo: los flags de impulso se asignan **antes** de `onNewTick`, porque el
callback se dispara dentro de esa llamada. Asignarlos después hacía que el veto leyera el impulso
del tick anterior.

### Cambio 2 — Poda de ticks
Se filtra en el volcado, no en la captura: el bucle necesita la cola pre-ventana para
`find_nearest_tick_price` de los contrafactuales. Poda con `ts >= int(start_time * 1000)` y reporta
cuántos descartó.

### Cambio 3 — Tolerancia temporal
`temporal_tolerance_ms: int = 6000` en `compare_trades`. **Hallazgo:** el CLI tenía su propio
`--temporal-tolerance-ms` con default `3000` independiente de la función. Cambiar solo la función no
habría cambiado nada en las corridas reales. Se alinearon ambos.

### Cambio 4 — Suite de trazabilidad
`LiveReplayParityTraceabilityTest` — 6 tests (los 5 pedidos + 1 de congelación):

| Test | Qué fija |
|---|---|
| `test1_tradeRealSeLocalizaEnReplay` | decision → trade → outcome se localiza; match por `decision_id` con 4,6 s de latencia |
| `test2_decisionBloqueadaNoApareceComoTradeEnReplay` | decisión vetada no empareja contra trade inexistente |
| `test3_decisionesDistintasNoColisionan` | 4 tuplas distintas → 4 ids distintos |
| `test3b_elIdEsEstableAnteSegundosYMilisegundos` | seg y ms dan la misma identidad |
| `test4_eventoDuplicadoNoGeneraIdentidadAmbigua` | 3 filas del mismo evento → 1; el replay no se consume dos veces |
| `test5_conjuntoParitarioDaPassYMatchRate100` | 5/5 emparejados, match_rate 100%, PASS |
| `test6_laExtraccionNoAlteroNingunValorDeLaFormula` | valores dorados de la fórmula congelada |

---

## 5. HALLAZGO NO PEDIDO: W03 SE HABÍA COMPARADO CON EL SUBMODE EQUIVOCADO

El `replay_journal.csv` archivado de W03 dice `submode=YOLO`. Mi primera corrida usó el default del
arnés, `CONSERVATIVE` (cooldown 35 s en vez de 8 s), y produjo **3 trades** donde el journal
archivado tenía 12. Comparar esos 3 contra los 6 live daba 0% de paridad y una conclusión falsa.
Con `BACKTEST_SUBMODE=YOLO`: **5 trades**, parea 2, matchea 1.

Esto significa que **una parte del 0% histórico de W03 no era divergencia live↔replay sino una
corrida de replay con parámetros distintos a los de la ventana**. Es un defecto de procedimiento, no
de código, y no estaba en la lista de la misión. No lo corregí en el monitor (sería otro cambio de
infraestructura no autorizado); lo dejo señalado.

---

## 6. PRUEBA DE CONGELACIÓN (no es una declaración, es un test)

`test6` fija valores calculados a mano sobre la fórmula **original**, antes del refactor:

```
UPTREND  distSup 0.10  rsi 50  impulso alcista  →  92 / 8
UPTREND  sin refuerzos                          →  68 / 32
DOWNTREND distRes 0.10 rsi 45  impulso bajista  →  8 / 92
SIDEWAYS distSup 0.20                           →  62 / 38
SIDEWAYS sin niveles cercanos                   →  50 / 50
invariante: CALL + PUT == 100 en 3 regímenes × 4 RSI
```

Si alguien mueve una constante, cae. Y el test documenta un detalle contraintuitivo que **no** se
"corrigió": el `coerceIn(15,95)` corre antes de la normalización, así que el complemento puede salir
en 8. Esa es la conducta congelada. Arreglarla habría sido un cambio de estrategia encubierto.

Además: `git diff AdaptiveLearningEngine.kt` → 0 líneas; `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD =
0.99f` intacto; `RiskManager.kt` sin tocar; SELL sin tocar (lo blinda `sellQuedaIntacto…` de la fase
1, 18 combinaciones).

---

## 7. A/B DEL VETO EN W03 (efecto real, sin maquillar)

Corrí W03 dos veces con el mismo input, YOLO, alternando solo el veto en el arnés:

| | Sin veto | Con veto |
|---|---|---|
| Trades | 12 | 5 |
| W / L | 6 / 6 | 2 / 3 |
| BUY+UPTREND | 7 (3W / 4L) | 0 |

El veto eliminó los 7 BUY+UPTREND (3 ganadoras, 4 perdedoras) — coherente con la hipótesis.

**Pero además desapareció un `SELL DOWNTREND LOSS` y apareció un `BUY SIDEWAYS LOSS`.** No es que el
veto toque SELL. Es consecuencia de la regla "no marcar la vela como operada": al liberar la vela, un
tick posterior toma el turno y **desplaza la fase del cooldown** de toda la ventana. Un veto local
tiene efectos no locales sobre qué señales llegan a ejecutarse.

Esto no invalida el diseño, pero sí limita cómo se puede leer la atribución: en muestras pequeñas,
comparar W/L antes-después le atribuye al veto movimientos que son de re-faseo. Para la fase 3 haría
falta medir sobre la señal vetada y no sobre el resultado agregado de la ventana.

---

## 8. VERIFICACIÓN REQUERIDA — RESULTADOS

### 8.1 Suite completa
```
./gradlew.bat testDebugUnitTest
TESTS=466  FAILURES=0  ERRORS=0  SKIPPED=2 -> VERDE
EXIT=0
```
459 → 466 (+7: 6 de trazabilidad + 1 de congelación). Los 2 skipped son `ReplayBacktestTest`
(`assumeTrue` sobre `BACKTEST_CSV`), preexistentes.

### 8.2 Re-comparación offline de W03
```
Tasa de paridad: 0.0%  ->  50.0%
total_live_trades  = 6
total_replay_trades = 5
paired_decisions   = 2
exact_matches      = 1
divergences        = 1  (candle_second)
missing_replay     = 4
pass               = False
```

**Sigue en FAIL. No llegó a 100%.** Lo que queda:

- **4 `MISSING_REPLAY`**: trades que el motor vivo ejecutó y el replay no reproduce. Son los
  BUY+UPTREND que el veto nuevo elimina del replay — pero **el journal live es de R23G, anterior al
  veto**. Se está comparando un replay con política nueva contra un live con política vieja. Es
  esperado y no es un defecto; la verificación válida exige una ventana nueva con el APK v1.9.
- **1 `DIVERGENT` por `candle_second`**: emparejado por `decision_id`, `candle_second` distinto. El
  comentario del comparador ya reconoce que la divergencia de segundo es latencia de despacho, pero
  solo exime del check cuando el par se formó por cercanía temporal. Con `decision_id` idéntico y
  3.910 ms de separación, el segundo cambia y marca divergencia. Es una inconsistencia del
  comparador, no del motor.

No toqué ninguna de las dos cosas: quedan fuera de los 4 cambios autorizados.

### 8.3 Pruebas de regresión de los scripts
```
python -m py_compile  ->  OK (ambos)
compare_live_replay.py --self-test  ->  ALL SELF-TEST ASSERTIONS PASSED
```

---

## 9. PENDIENTE / RECOMENDADO

1. **Correr una ventana R23H nueva** con el APK v1.9 y comparar contra un replay con la misma
   política. Sin eso, el match rate no es interpretable: se está midiendo un cambio de política.
2. **Alinear `submode` automáticamente**: el monitor debería escribir el submode en
   `window_metadata.json` y el arnés leerlo por defecto.
3. **Revisar la exención de `candle_second`** en `compare_trades`: hoy depende de cómo se formó el
   par, no de si la diferencia es explicable por latencia.
4. **El re-faseo por cooldown** merece una nota en el análisis de la fase 3: la atribución por
   ventana no sirve para un veto que libera velas.
