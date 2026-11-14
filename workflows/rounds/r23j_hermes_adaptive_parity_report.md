# R23J FASE B — PARIDAD `ADAPTIVE_BLOCK` LIVE ↔ REPLAY

**Fecha:** 2026-10-18
**Ejecutor:** HERMES (`w1:pA`)
**Commit base:** `47a34f4` (B1) — el informe se emite antes del commit de B2/B3
**Estrategia:** CONGELADA — `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD` sigue en `0.99f`

---

## 1. VEREDICTO EN UNA LÍNEA

B1 está hecho y verificado. B2 da **4,35% de reproducción** (2 de 46 instantes de bloqueo), y la
causa raíz **no es la lógica de bloqueo** sino que el replay ni siquiera llega a evaluar las señales
que el motor vivo evaluó: **0,2 señales/minuto contra 2,9–4,5**.

**El número es retrospectivo y no independiente.** Se midió sobre las mismas ventanas que se usaron
para construir la memoria de anti-patrones, y con el estado persistido del teléfono. No es una
validación out-of-sample.

---

## 2. B1 — EXTRACCIÓN A LÓGICA PURA COMPARTIDA

### 2.1 Qué se extrajo

Cuatro funciones top-level en `AdaptiveLearningEngine.kt`, copiadas **literalmente**:

| Función | Contenido | Constantes |
|---|---|---|
| `similitudDeFirmas(a, b)` | Similitud ponderada entre firmas | 0.20 / 0.20 / 0.30 / 0.15 / 0.15, divisor 4.0 |
| `contextoEstrictamenteCoincidente(a, b)` | Compuerta H6 | tolerancia S/R 0.10 |
| `motivoBloqueoPorAntiPatron(cand, perdida, sim)` | La decisión de bloquear | umbral desde el companion |
| `mejorCoincidenciaEnMemoria(cand, memoria)` | Búsqueda del anti-patrón | últimas 20, máximo estricto `>` |

Los métodos de instancia (`isTightlyMatchingContext`, `calculateSignatureSimilarity`) quedaron como
**delegación de una línea**. No hay lógica duplicada en ningún sitio.

### 2.2 Tests: 11 casos, 49.608 combinaciones

```
similitudCoincideConElOraculoEnTodaLaRejilla   OK   46.080 combinaciones
compuertaCoincideConElOraculo                  OK    3.528 combinaciones
laFuncionEsDeterministaYNoGuardaEstado         OK
noBloqueaSinMemoriaDePerdidas                  OK
noBloqueaPorDebajoDelUmbral                    OK
bloqueaEnElUmbralExactoConContextoEstricto     OK
noBloqueaSiElContextoNoEsEstrictamenteCoincidente OK
laBusquedaSoloMiraLasUltimasVeintePerdidas     OK
laBusquedaDevuelveElMaximoEstricto             OK
conMemoriaVaciaLaSimilitudEsCero               OK
elUmbralSigueEn099                             OK
```

Los oráculos son copias literales del código **original**, no llaman a las funciones bajo prueba.

**Verificado por inyección:** cambiar el peso de acción de `0.20f` a `0.19f` hace fallar
`similitudCoincideConElOraculoEnTodaLaRejilla`. Los tests cazan el defecto.

### 2.3 Un detalle que casi se cuela

Mi primera versión evaluaba el bloqueo **dentro del callback de señal**, que se dispara durante
`onNewTick`. En ese punto ningún trade está resuelto todavía, así que la memoria de pérdidas está
siempre vacía y el bloqueo sería **0% por construcción**, no por divergencia real. Lo moví al bucle
de resolución, donde la memoria sí tiene el estado que tendría en vivo.

---

## 3. B2 — REPRODUCCIÓN RETROSPECTIVA

### 3.1 Método

1. Replay con memoria **vacía** → 0 bloqueos en ambas ventanas.
2. Replay con la memoria **real del teléfono** (`files/adaptive_learning_state.json`, 19 firmas) →
   2 bloqueos en W05, 1 en W06.
3. Comparación por instante con tolerancia de 6 s.

La memoria viva **persiste entre ventanas dentro del mismo archivo JSON**: contiene firmas de días
anteriores. Un replay que arranque vacío no puede reproducir un bloqueo que depende de pérdidas de
hace días. Por eso añadí `ADAPTIVE_STATE_JSON` como variable de entorno opcional del arnés: sin ella
el replay arranca vacío y la comparación no significa nada.

### 3.2 Resultado exacto

| Ventana | Filas `ADAPTIVE_BLOCK` live | Instantes distintos (dedupe 1 s) | Bloqueos replay | Reproducidos | Tasa |
|---|---|---|---|---|---|
| **W05** | 50 | 35 | 2 | 1 | **2,86%** |
| **W06** | 20 | 11 | 1 | 1 | **9,09%** |
| **Consolidado** | **70** | **46** | **3** | **2** | **4,35%** |

**No ajusté nada para forzar el 100%.** El replay con el umbral intacto produce 3 bloqueos donde el
live produjo 46 instantes.

### 3.3 Por qué las filas live no llevan acción

`decisions.csv` registra las filas `VETO` con `action = NONE` (50 de 50 en W05, 20 de 20 en W06). La
acción sobre la que se vetó **no está en el CSV**; sólo aparece dentro del texto de `detail`. Por eso
la comparación se hizo por instante y no por `(instante, acción)`. Es una carencia de telemetría, no
un defecto del motor.

---

## 4. LA BRECHA RESIDUAL — DIAGNÓSTICO

### 4.1 El hallazgo que importa

| Ventana | Señales evaluadas por el live | Señales que el replay llega a evaluar |
|---|---|---|
| W05 | 63 (2,9/min) | **5 (0,2/min)** |
| W06 | 99 (4,5/min) | **4 (0,2/min)** |

El replay evalúa **1 de cada 13–25 señales** que el motor vivo evaluó. Aunque la lógica de bloqueo
fuera bit a bit idéntica, la tasa de reproducción no podría pasar de ~8%: **no hay donde enganchar
los bloqueos que faltan.**

### 4.2 Por qué el replay emite tan pocas señales

No es una sola causa y no las separé todas. Las que sí están verificadas:

1. **`1 orden por vela`** (`lastExecutedCandleMinute`). El motor vivo reevalúa la señal en cada tick
   dentro de la ventana sniper; el replay la colapsa a una por minuto. Los 70 bloqueos vivos
   ocurren en ráfagas de 2–11 repeticiones sobre el **mismo** instante, que el replay nunca genera.
2. **`blockedUntilTs` de 60 s + cooldown YOLO de 8 s.** Tras la primera operación el replay queda
   ciego 68 s, mientras el vivo seguía evaluando y vetando.
3. **Ventana sniper `:00–:05` y `:58–:59`.** Sólo 7 de 60 segundos son evaluables en el replay.
4. **La memoria del replay crece sólo con SUS trades** (3–4 por ventana), así que casi nunca alcanza
   la similitud 0.99f contra una pérdida previa. El vivo tiene 19 firmas acumuladas de días atrás.

La causa (4) la resolví con `ADAPTIVE_STATE_JSON`. Las causas (1)–(3) son **de diseño del arnés de
replay**, no del motor, y corregirlas es un cambio de infraestructura de otro alcance: tocar el
arnés altera qué trades simula, lo que a su vez cambia el journal que consume el comparador
congelado por SHA-256 en el preregistro. **No lo hice**, porque invalidaría el ciclo.

### 4.3 Trades live que quedan sin par

En W05: 3 trades live, 3 del replay, 1 par. En W06: 3 live, 3 replay, 2 pares. Los huérfanos no son
bloqueos: son trades reales que el replay no reproduce por las causas (1)–(2) — distinta fase de
cooldown y distinta densidad de señales. Es el mismo mecanismo que documenté en R23H al analizar el
A/B del veto por agotamiento: un veto local re-fasea la ventana entera.

---

## 5. QUÉ SE PUEDE AFIRMAR Y QUÉ NO

**Se puede afirmar:**

- La lógica de `ADAPTIVE_BLOCK` es **la misma** en Live y Replay, y está demostrado con 49.608
  combinaciones contra oráculos independientes.
- El replay **ahora evalúa** `ADAPTIVE_BLOCK`; antes no lo hacía en absoluto.
- Con la memoria persistida del teléfono, el replay reproduce bloqueos reales (2 de 3 intentos).
- El umbral `0.99f` y todos los pesos están intactos.

**No se puede afirmar:**

- Que la paridad de `ADAPTIVE_BLOCK` sea del 4,35% **como propiedad del sistema**. Ese número mide
  sobre todo la densidad de señales del arnés, no la fidelidad de la lógica.
- Que el replay reproduzca la operativa de las ventanas. Reproduce **algunas** decisiones, con una
  fase distinta.
- Nada de esto es out-of-sample: memoria y ventanas son las mismas.

---

## 6. VERIFICACIÓN

```
./gradlew.bat testDebugUnitTest
  -> EXIT=0
  -> TESTS=479  FAILURES=0  ERRORS=0  SKIPPED=2  VERDE
```

479 = 468 previos + 11 de `AdaptiveBlockParityTest`. Los 2 skipped son `ReplayBacktestTest` con
`assumeTrue`, preexistentes.

`AdaptiveLearningEngine.kt` — `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f` sin cambios.
`RiskManager.kt` sin tocar. Ningún umbral de estrategia modificado.

---

## 7. RECOMENDACIÓN PARA AGY

1. **No leer el 4,35% como fallo de paridad.** Es un artefacto de densidad de señales. La pregunta
   correcta es «¿el replay bloquea cuando debería?», y la respuesta medida es «sí, cuando llega a
   evaluar».
2. **Para subir la reproducción hay que cambiar el arnés** (evaluar en cada tick dentro de la
   ventana sniper, no una vez por vela). Eso altera el journal y **exige re-congelar el comparador
   con un SHA nuevo y re-medir todas las ventanas**. Es un ciclo propio, no un parche de R23J.
3. **Mientras tanto**, la comparación honesta de paridad sigue siendo la del preregistro: pares
   formales, no bloqueos.
4. **Telemetría pendiente:** que `decisions.csv` registre la acción vetada en las filas `VETO` en
   lugar de `NONE`. Sin eso, cualquier análisis de bloqueos por dirección es imposible.
