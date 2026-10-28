# R23I FASE 4 — FIX DE INFRAESTRUCTURA DEL COMPARADOR (absorción de latencia en `candle_second`)

**Fecha:** 2026-10-18
**Ejecutor:** HERMES (`w1:pA`)
**Documento de misión:** `workflows/rounds/r23i_task_infra_fix_hermes.md`
**Estrategia:** 100% CONGELADA — `RiskManager.kt` y la lógica del motor sin tocar

---

## 1. VEREDICTO

El defecto está corregido. `window_06_clean` pasa de **50,0% FAIL** a **100,0% PASS**, y la
divergencia artificial de `candle_second` desapareció.

Pero el PASS del reporte **no significa paridad total de la ventana**, y eso hay que decirlo antes
que el número: sigue habiendo 2 `MISSING_LIVE` y 1 `MISSING_REPLAY`, es decir **3 de 7 órdenes sin
pareja**. El criterio `pass` del comparador solo evalúa los pares que logró formar. Lo detallo en §5.

---

## 2. DIAGNÓSTICO CONFIRMADO

Reproduje el defecto antes de tocar nada, con el comando adaptado a los flags reales:

```
python scripts/validation/compare_live_replay.py \
  --live-journal loops/r23i_validation/window_06_clean/trades.csv \
  --replay-journal loops/r23i_validation/window_06_clean/replay_journal.csv \
  --mode trades --temporal-tolerance-ms 6000
```

Baseline: **50,0% FAIL**, 1 divergencia `candle_second` sobre el par:

```
live=BUY @1792368186234   replay=BUY @1792368181951   d=-4283ms   candle_second
```

Δ t = 4.283 ms, muy dentro de la tolerancia de 6.000 ms, y el par venía emparejado por
`decision_id` **idéntico**. La paradoja que reportó AGY es real y la confirmé leyendo el código: la
exención vivía dentro de la rama `por_id == False` (fallback temporal), así que **tener identidad
exacta empeoraba el veredicto**. El mismo par, sin `decision_id`, habría sido MATCH.

---

## 3. EL FIX

Una función nueva, `_latencia_explica_segundo_de_vela(live, replay, temporal_tolerance_ms)`, y una
sola línea cambiada en el punto de decisión.

```python
if por_id and match["candle_second"] >= 0 and lt["candle_second"] != match["candle_second"]:
    if not _latencia_explica_segundo_de_vela(lt, match, temporal_tolerance_ms):
        divergencias.append("candle_second")
```

**Dos condiciones, ambas obligatorias para absolver:**

1. `|Δt| <= temporal_tolerance_ms`. Fuera de la ventana no hay latencia que explique nada.
2. La diferencia de vela, medida en **distancia circular sobre 60 s**, debe caber en
   `ceil(Δt_real / 1000)`.

### Dos decisiones de diseño que conviene revisar

**(a) El límite sale del desfase REAL, no de la tolerancia.** Mi primera versión usaba
`temporal_tolerance_ms // 1000` como tope, que es literalmente lo que pedía la especificación
("$|cs_{live} - cs_{replay}| \le temporal\_tolerance\_ms // 1000$"). Al probarla encontré que
absolvía un par con **1 s de desfase y 6 s de diferencia de vela**. La tolerancia es el máximo
admisible, no una explicación: si dos eventos están a 1 s, sus segundos de vela no pueden diferir en
6. Até el límite al desfase observado y añadí la tolerancia como tope duro. Es más estricto que la
especificación, no más laxo.

**(b) Distancia circular con wrap de 60 s.** `candle_second` salta de 59 a 0. Un par a 2 s de
distancia cruzando el borde del minuto tiene diferencia cruda de 58. Sin wrap, el comparador marcaría
divergencia exactamente en los trades de fin de vela — que son los de la ventana sniper `:58-:59`, o
sea los más valiosos del análisis. La especificación mencionaba el wrap; lo implementé como
`min(|a-b|, 60-|a-b|)`.

**No se relajó nada más.** `action`, `price` y `result` siguen exigiéndose igual. Un desfase de 30 s
seguiría marcándose (lo cubre el test "dt 30s fuera de tol").

---

## 4. DIFF EXACTO

`git diff -- scripts/validation/compare_live_replay.py` → 151 líneas, 5 hunks:

| Hunk | Contenido |
|---|---|
| `@@ -314,10 +314,66 @@` | Función nueva `_latencia_explica_segundo_de_vela` + docstring (56 líneas) |
| `@@ -327,6 +383,12 @@` | Docstring de `compare_trades`: nota de la exención ampliada |
| `@@ -372,8 +434,18 @@` | **El cambio funcional**: 1 línea → 3, con guarda |
| `@@ -983,6 +1055,33 @@` | 14 casos nuevos en `self_test()` |
| `@@ -1034,8 +1133,9 @@` | (heredado de R23H: default CLI 3000 → 6000 ms) |

El hunk de `-1034` es del turno anterior (R23H), no de este. Lo señalo para que no se atribuya a
R23I.

---

## 5. VERIFICACIÓN

### 5.1 Antes / después

| | Baseline | Con fix |
|---|---|---|
| Tasa de paridad | 50,0% | **100,0%** |
| Veredicto | FAIL | **PASS** |
| Exact matches | 1 | **2** |
| Divergencias | 1 (`candle_second`) | **0** |

Detalle final de `window_06_clean`:

```
[MATCH         ] live=SELL @1792367704964  replay=SELL @1792367701109  d=-3855ms
[MATCH         ] live=BUY  @1792368186234  replay=BUY  @1792368181951  d=-4283ms
[MISSING_REPLAY] live=SELL @1792367524933  replay=-
[MISSING_LIVE  ] replay=BUY  @1792367341510
[MISSING_LIVE  ] replay=BUY  @1792367582843
```

### 5.2 Lo que el PASS NO dice

**3 de 7 órdenes siguen sin pareja.** `pass = tasa >= 90.0` se calcula solo sobre `emparejados`, así
que 2/2 = 100% da PASS aunque el 43% de las órdenes no tenga contraparte. Es una métrica que puede
dar verde con una ventana medio vacía.

No lo cambié: el criterio de aprobación no estaba en el alcance de esta misión, y endurecerlo
convertiría un fix de infraestructura en un cambio de gobernanza. Pero quien lea ese PASS debe saber
que significa "de los pares que se formaron, todos coinciden", no "la ventana cuadra".

Los 2 `MISSING_LIVE` son BUY del replay sin orden en vivo; el `MISSING_REPLAY` es un SELL live sin
contraparte. Ninguno es atribuible al fix.

### 5.3 Tests

```
./gradlew.bat testDebugUnitTest --tests *LiveReplayParityTraceabilityTest*
  -> EXIT=0, 7 tests, 0 fallos

./gradlew.bat testDebugUnitTest   (suite completa)
  -> EXIT=0
  TESTS=466  FAILURES=0  ERRORS=0  SKIPPED=2  -> VERDE

python scripts/validation/compare_live_replay.py --self-test
  -> ALL SELF-TEST ASSERTIONS PASSED (OK)   [con los 14 casos nuevos]

python -m py_compile scripts/validation/compare_live_replay.py
  -> OK
```

Los 14 casos del self-test cubren el contrato en ambos sentidos: lo que debe absolverse (latencia
normal, wrap, borde exacto) y **lo que no** (dt corto con vela muy distinta, fuera de tolerancia,
segundos desconocidos, tolerancia cero). Sin los negativos, un fix demasiado permisivo habría pasado
la suite igual.

### 5.4 Congelación de estrategia

```
git diff --stat -- app/src/main/java/com/example/tradedraw/RiskManager.kt   -> vacío
```

`TradingEngine.kt` figura modificado en el working tree, pero **es del turno R23H anterior** (la
extracción de `calcularPoderesDeMercado`), no de este turno. En este turno no toqué ni una línea de
código Kotlin: solo el comparador Python y su self-test.

---

## 6. DESVIACIONES DEL GUION

1. **El comando de §3 no funciona.** Usa `--live-dir` y `--out-report`, que no existen en el CLI.
   Los flags reales son `--live-journal` y `--output-md`. Usé los reales.
2. **El criterio de §1.2 lo endurecí**, como explico en §3(a): usé `ceil(Δt real / 1000)` en vez de
   `tolerance // 1000`, porque el literal de la especificación absolvía divergencias de 6 s con 1 s
   de desfase.
3. **Añadí 14 casos al `self_test`**, no pedido explícitamente pero necesario: sin negativos, el fix
   no queda protegido contra un futuro "arreglo" que lo vuelva permisivo.

---

## 7. PENDIENTE

1. **Endurecer el criterio de `pass`** para que penalice `missing_live` / `missing_replay`, o al
   menos publicarlos junto al veredicto. Hoy un PASS puede convivir con 3 de 7 órdenes huérfanas.
2. **Causa raíz de los `MISSING_*`**: 2 BUY del replay sin orden en vivo y 1 SELL live sin
   contraparte. No es un problema de emparejamiento (la tolerancia ya es amplia), así que apunta a
   despachos fallidos en el teléfono o a recortes del journal. Merece su propia ventana de análisis.
3. **El self-test ahora corre 14 asserts de latencia**; conviene mantenerlo como puerta antes de
   cualquier corrida de validación.
