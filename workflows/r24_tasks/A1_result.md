# A1 · VALIDACIÓN INDEPENDIENTE — RECÁLCULO CUBO 2 (R24)

**Agente:** validador (subagente)
**Fecha:** 2026-10-22
**Rama:** `r10-night`
**SHA HEAD usado:** `190f596a229c76b54a086a3164e28b503e3017eb`
**Build declarada:** `8cdb61f` (versionCode 19, versionName `1.9-r23h-exhaustion`) — verificada ancestro de HEAD.
**Python:** 3.12 (`python`), sin Gradle, sin ADB, sin tocar el teléfono.

---

## 0. Comandos exactos ejecutados

```powershell
rtk git rev-parse HEAD
# 190f596a229c76b54a086a3164e28b503e3017eb

rtk git branch --show-current
# r10-night

rtk git merge-base --is-ancestor 8cdb61f HEAD   # exit 0 → ES ANCESTRO
rtk git log -1 --format="%H %s" 8cdb61f
# 8cdb61fe038a431dd063667ce740b262dca0e5d6 feat(r23j): replay evalua ADAPTIVE_BLOCK y reproducibilidad retrospectiva B2

rtk git log -n 5 --oneline -- scripts/validation/recompute_cubes.py
# 190f596 R23K: Checkpoint reproducible W22 ...
# d39f723 R23K: Checkpoint reproducible W21 ...
# b84b1a1 R23K: Checkpoint reproducible W17-W20 ...
# 5f3c177 R23K: Pase de Ventana 16 ...
# 1a5f2d0 r23k: registro formal de ventana 15 ...

rtk git diff --stat d39f723 190f596 -- scripts/validation/recompute_cubes.py
# 1 file changed, 8 insertions(+), 7 deletions(-)

rtk git status --short scripts/validation/recompute_cubes.py
# (vacío) → sin cambios sin commitear

python scripts/validation/recompute_cubes.py --test
python scripts/validation/recompute_cubes.py --root . --out-md loops/r24_verify/A1_cubes.md --out-json loops/r24_verify/A1_cubes.json
python loops/r24_verify/a1_independent.py
```

---

## 1. Integridad del script canónico

`git diff d39f723..190f596` sobre `recompute_cubes.py` — **la lógica no cambió**. Los 8 insertos / 7 borrados son exclusivamente:

| Cambio | Naturaleza |
| :--- | :--- |
| `+ ("W22", "loops/r23k_validation/window_22", 0.83)` en `CANONICAL_WINDOWS` | alta de ventana (dato) |
| `CUBO1_WINDOWS` pasa de `...W21` a `...W21, W22` | alta de ventana (dato) |
| 4 literales de comentario/título `W21` → `W22` en `generate_markdown_report` | cosmético |

Ninguna fórmula (Wilson, breakeven, bootstrap, drawdown, agregación) fue modificada. `git status` del archivo: limpio.

**Conclusión: script íntegro respecto a su último commit relevante.**

---

## 2. Salida cruda del script

```
=== INICIANDO RECÁLCULO INDEPENDIENTE DE CUBOS (R23K) ===
Directorio raíz: C:\Users\heidy\Tradedraw
Trades únicos confirmados y reconciliados: 71
JSON exportado exitosamente en: C:\Users\heidy\Tradedraw\loops/r24_verify/A1_cubes.json
Informe Markdown exportado exitosamente en: C:\Users\heidy\Tradedraw\loops/r24_verify/A1_cubes.md
=== RECÁLCULO COMPLETADO CON ÉXITO ===
```

(stdout completo en `loops/r24_verify/A1_stdout.txt`; artefactos en `loops/r24_verify/A1_cubes.{md,json}`)

### 2.1 CUBO 2 (W04–W22 + border trade W10)

| Métrica | Valor | Fuente |
| :--- | :--- | :--- |
| N total | **60** | `A1_cubes.json → cubes.cubo_2.n` |
| Resueltos | 59 | `cubo_2.resolved` |
| W / L / VOID | **30 / 29 / 1** | `cubo_2.wins/losses/voids` |
| WR sobre resueltos | **50,85 %** | `cubo_2.win_rate = 0.5085` |
| Wilson 95 % | **[38,44 %, 63,16 %]** | `cubo_2.wilson_ci_95` |
| Breakeven (payout 83 %) | **54,64 %** | `cubo_2.breakeven = 0.5464` |
| EV en R | **−0,0710** [IC95 −0,3002, +0,1567] | `cubo_2.ev_r` |
| EV en COP | −7.116,67 [−30.033,15, +15.649,92] | `cubo_2.ev_cop` |
| P&L derivado de balances | **−427.000,32 COP** | `cubo_2.total_delta_cop` |
| Max DD | 1.260.999,68 COP (3,1729 %) | `cubo_2.max_drawdown` |
| Veredicto | `NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)` | `cubo_2.verdict` |

### 2.2 CUBO 1 (W07–W22)

| Métrica | Valor |
| :--- | :--- |
| N / resueltos | 43 / 42 |
| W / L / VOID | 17 / 25 / 1 |
| WR | 40,48 % |
| Wilson 95 % | [27,04 %, 55,51 %] |
| Breakeven (83 %) | 54,64 % |
| EV R | −0,2544 [−0,5095, +0,0195] |
| P&L balances | −1.094.999,04 COP |
| Paridad replay | **22 pares, 20 matches, 2 divergencias → 90,91 %, Wilson [72,19 %, 97,47 %], PASS** |

### 2.3 CUBO 3 (W01–W03) y total

- CUBO 3: N=11, 7W/4L, WR 63,64 %, Wilson [35,38 %, 84,83 %], +178.001,92 COP.
- Total consolidado W01–W22: N=71, 37W/33L/1V, WR 52,86 %, Wilson [41,32 %, 64,10 %], −248.998,40 COP.

### 2.4 Sensibilidades

| Sensibilidad | N | res | W | L | V | WR | Wilson 95 % | EV R | Δ COP |
| :--- | :-: | :-: | :-: | :-: | :-: | :-: | :--- | :--- | :--- |
| Cubo 2 sin W04 | 52 | 51 | 23 | 28 | 1 | 45,10 % | [32,27 %, 58,62 %] | −0,1731 | −900.999,68 |
| Solo W07+ (post-congelado) | 43 | 42 | 17 | 25 | 1 | 40,48 % | [27,04 %, 55,51 %] | −0,2544 | −1.094.999,04 |
| 2.ª mitad cronológica | 36 | 35 | 14 | 21 | 1 | 40,00 % | [25,55 %, 56,43 %] | −0,2619 | −944.000,00 |

### 2.5 BUY vs SELL (Cubo 2)

| Dirección | N | W | L | WR | Wilson 95 % | Δ COP |
| :--- | :-: | :-: | :-: | :-: | :--- | :--- |
| BUY | 33 | 16 | 17 | 48,48 % | [32,50 %, 64,78 %] | −383.001,60 |
| SELL | 27 | 14 | 12 | 53,85 % | [35,46 %, 71,24 %] | −43.998,72 |

(Cubo 1: BUY 23 → 8W/15L = 34,78 %, [18,81 %, 55,11 %], −840.000,00 COP; SELL 20 → 9W/10L = 47,37 %, [27,33 %, 68,29 %], −254.999,04 COP.)

---

## 3. Prueba sintética independiente (dataset inventado)

Dataset de 5 trades, WR calculable a mano = 3/5 = 0,60; deltas 3×(+83.000) + 2×(−100.000) = +49.000 COP; EV R = (0,83·3 − 1,0·2)/5 = 0,098.

```
n 5 W 3 L 2 WR 0.6
esperado a mano WR=3/5=0.6  delta=83000*3-100000*2= 49000
delta obtenido 49000.0
wilson [0.2307, 0.8824] mano wilson(3,5)= [0.2307, 0.8824]
EVr 0.098 mano 0.09799999999999995
BUY {'n': 3, 'wins': 2, 'losses': 1, 'win_rate': 0.6667, 'wilson_ci_95': [0.2077, 0.9385], 'delta_cop': 66000.0}
SELL {'n': 2, 'wins': 1, 'losses': 1, 'win_rate': 0.5, 'wilson_ci_95': [0.0945, 0.9055], 'delta_cop': -17000.0}
DD {'abs_cop': 117000.0, 'pct': 10.0343}
```

**El script reproduce exactamente los valores calculables a mano.** Además su propia batería `--test` da 6/6 PASS (Wilson exacto en 3 casos, breakeven, bootstrap con semilla, drawdown, agregación, partición de cubos).

---

## 4. ¿Es alcanzable "Wilson inferior > 54,64 %" con N=80?

Cálculo propio (no del script), `wilson(k,n,z=1.959963984540054)`.

Estado actual Cubo 2: **W=30, resueltos R=59**, WR 50,85 %, Wilson inf 38,44 %.

### Escenario pedido: N=80 con 20 trades extra todos ganadores
- 59 + 20 = **N=79**, W = 50 → WR 63,29 %, **Wilson inf = 52,28 %** < 54,64 % → **NO alcanza** (faltan 2,36 pp).
- Si se fuerza N=80 exacto (59 + 21): W=51 → WR 63,75 %, **Wilson inf = 52,81 %** → tampoco.

### Cuántos aciertos hacen falta con N=80
| k (wins) | WR | Wilson inf | ¿> 54,64 %? |
| :-: | :-: | :-: | :-: |
| 50 | 62,50 % | 51,55 % | NO |
| 51 | 63,75 % | 52,81 % | NO |
| 52 | 65,00 % | 54,08 % | NO |
| **53** | **66,25 %** | **55,36 %** | **SÍ** |

Con N=80 fijo haría falta pasar de 30 a **53 wins**, es decir **+23 aciertos**, pero solo hay **21 huecos** (80 − 59). **Matemáticamente imposible con N=80.**

### Cuántos extra hacen falta sin tope de N
Con 30 wins / 59 resueltos y racha perfecta de aciertos:
- +20 → 50/79 = 63,29 %, inf 52,28 % → NO
- +21 → 51/80 = 63,75 %, inf 52,81 % → NO
- +24 → 54/83, inf ~54,6 % → NO (borde)
- **+25 → 55/84 = 65,48 %, inf 54,83 % → SÍ (primer k que cumple)**

**Conclusión: el criterio "Wilson inferior > 54,64 % con N=80" es INALCANZABLE.** Requiere o bien 53/80 (imposible por aritmética de huecos) o bien 25 aciertos consecutivos sin tope de N (N=84).

---

## 5. Cobertura del Cubo 1

Definición del encargo: `pares / (trades Live ∪ Replay)`.

Suma de los `summary` de los 16 `replay_comparison.json` del Cubo 1:

```
W07 3 4 1 | W08 4 8 4 | W09 2 2 1 | W10 0 1 0 | W11 2 7 1 | W12 1 2 0 | W13 5 6 5 | W14 1 3 1
W15 0 3 0 | W16 3 2 1 | W17 1 5 0 | W18 5 3 2 | W19 4 4 3 | W20 6 4 1 | W21 5 3 2 | W22 1 2 0
SUM live 43  replay 59  paired 22  missing_live 37  missing_replay 21
```

| Cálculo | Valor |
| :--- | :--- |
| Pares evaluables (MATCH + DIVERGENT) | **22** |
| Live únicos | 43 |
| Replay únicos | 59 |
| Unión (43 + 59 − 22 emparejados) | **80** |
| **Cobertura = 22 / 80** | **27,50 %** |
| Cobertura sobre Live solo = 22 / 43 | 51,16 % |
| Cobertura por `decision_id` (43 ∪ 59 = 90, intersección 12) | 22 / 90 = 24,44 % |

Cifra recomendada: **22/80 = 27,5 %** (unión por aritmética de summary; los `decision_id` de replay vienen vacíos o distintos, ver §6, así que la unión por ID está inflada).

---

## 6. Verificación aritmética independiente (mi código, no el script)

`loops/r24_verify/a1_independent.py` — recorre `trades.csv` crudos por ventana y recomputa Wilson/cobertura.

```
W04: 8  W05: 7  W06: 3  W07: 3  W08: 4  W09: 2  W10: 0  W11: 2  W12: 1  W13: 5
W14: 1  W15: 0  W16: 3  W17: 1  W18: 5  W19: 4  W20: 6  W21: 5  W22: 1
suma filas cubo2: 61   unicos: 61   duplicados: []
```

Conteo crudo 61 filas vs 60 en el ledger del script: la diferencia es el trade `77015885…` de `window_05_clean/trades.csv`, clasificado `RESIDUAL_INTERWINDOW_TRADE` (timestamp 1792353967459 fuera de `[1792360082133, 1792361288057]`). Consistente.

Cadena de saldos: **las 22 ventanas dan `internal_discrepancy = 0.00`** y `sum_deltas == delta_window` en todas. Continuidad: W04/W05/W06 marcan huecos inter-fase (`-300.001,28`, `+82.001,92`, `-387.000,32`); de W07 a W22 todo `CONTINUO` con gap 0,00.

Reconciliación P&L:
- Cubo 2: suma de deltas = −427.000,32 COP; suma de `delta_window` W04..W22 = −427.000,32 COP. **Cuadra.**
- Cubo 1: suma de deltas = −1.094.999,04 COP; `final(W22) − initial(W07)` = 38.176.737,28 − 39.271.736,32 = −1.094.999,04 COP. **Cuadra.**

---

## DISCREPANCIAS

1. **`verdict` del Cubo 2 tiene el texto de la rama equivocada.** `A1_cubes.json → cubes.cubo_2.verdict` dice `"NO CONCLUYENTE (N=60 < 40 ...)"` cuando N=60 **no** es < 40. El ternario en `recompute_cubes.py:652-661` imprime el mensaje de "N insuficiente" siempre que `edge_detected` sea falso, sin comprobar la condición. La conclusión (no hay edge) es correcta, pero el motivo impreso es falso. Cosmético, no afecta cifras.

2. **Cobertura del Cubo 1 depende de la definición; la cifra "oficial" no es la del encargo.** El script solo reporta `formal_pairs = 22` y `match_rate = 90,91 %`; **no calcula cobertura**. La cobertura pedida (pares / Live ∪ Replay) es **27,5 %**, muy por debajo del 90,91 % de match rate. Riesgo de que el 90,91 % se lea como cobertura cuando mide otra cosa (acuerdo sobre los pares que sí se formaron).

3. **Los objetos `replay` de los 59 pares tienen `trade_id` vacío (59/59).** Solo traen `decision_id`, y en **10 de los 22 pares evaluables el `decision_id` de live ≠ el de replay**. Tampoco coinciden `timestamp` ni `candle_second` (delta 1.288–5.916 ms, mediana 2.924 ms). Es decir: **el emparejamiento live↔replay no es verificable por ID ni por timestamp exacto**; se apoya en proximidad temporal. Esto no invalida el conteo de pares pero sí impide auditar la correspondencia.

4. **`8cdb61f` (build declarada) NO contiene los datos del Cubo 2 completo.** En ese commit solo existen W01–W11 (`r23h`/`r23i`/`r23j`); `loops/r23k_validation/` **no existe** (`git cat-file -e 8cdb61f:loops/r23k_validation/window_22/trades.csv` → *exists on disk, but not in '8cdb61f'*). W12–W22 entraron en `b84b1a1`/`d39f723`/`190f596`. Los `trades.csv` de W04/W05/W06 sí son idénticos en `8cdb61f` (8/7/3 filas). **Consecuencia: el recálculo del Cubo 2 completo no puede atribuirse a la build `8cdb61f`**; mezcla datos de la build con ventanas posteriores.

5. **W15 tiene `trades.csv` con 0 filas y el ledger le asigna 0 trades** (delta 0,00, DD 0 %). Coherente, pero implica que el Cubo 1 (43 trades) y el Cubo 2 (60) incluyen una ventana vacía — el "N" efectivo por ventana es muy desigual (W20 = 6, W15 = 0).

6. **`cubo_1.n = 43` incluye 1 VOID** (W16, `e9a3fd70…`, delta 0,00). `resolved = 42`. Cualquier lectura de "N=43" como tamaño de muestra binomial está inflada en 1.

---

## Archivos generados

- `loops/r24_verify/A1_cubes.json` — salida JSON completa del script
- `loops/r24_verify/A1_cubes.md` — informe Markdown del script
- `loops/r24_verify/A1_stdout.txt` — stdout crudo
- `loops/r24_verify/a1_independent.py` — mi verificación independiente

---

## A1b · ADENDA

**Fecha:** 2026-10-22 · **Alcance:** cierre de DISCREPANCIA #4 y DISCREPANCIA #3. Sin Gradle, sin ADB, sin tocar el teléfono.

### A1b.1 · Procedencia real de cada ventana del Cubo 2 (DISCREPANCIA #4)

Rutas canónicas tomadas de `scripts/validation/recompute_cubes.py:32-54` (`CANONICAL_WINDOWS`). Comando por ventana:

```powershell
git log --diff-filter=A --format="%h|%ad|%s" --date=short -- <ruta>/trades.csv
```

| Ventana | Ruta canónica | Commit que AÑADE `trades.csv` | Fecha | Filas |
| :--- | :--- | :--- | :--- | :-: |
| W04 | `loops/r23h_validation/window_04_clean` | `9442c1f` | 2026-10-18 | 8 |
| W05 | `loops/r23h_validation/window_05_clean` | `9442c1f` | 2026-10-18 | 7 |
| W06 | `loops/r23i_validation/window_06_clean` | `9442c1f` | 2026-10-18 | 3 |
| W07 | `loops/r23j_validation/window_07_clean` | `ab5ddcd` | 2026-10-18 | 3 |
| W08 | `loops/r23j_validation/window_08_clean` | `a8fb9a1` | 2026-10-18 | 4 |
| W09 | `loops/r23j_validation/window_09_clean` | `46907c6` | 2026-10-18 | 2 |
| W10 | `loops/r23j_validation/window_10_clean` | `e2ae626` | 2026-10-18 | 0 |
| W11 | `loops/r23j_validation/window_11_clean` | `16b2167` | 2026-10-19 | 2 |
| W12 | `loops/r23k_validation/window_12` | `eaeff51` | 2026-10-19 | 1 |
| W13 | `loops/r23k_validation/window_13` | `db12a6f` | 2026-10-19 | 5 |
| W14 | `loops/r23k_validation/window_14` | `79c6046` | 2026-10-19 | 1 |
| W15 | `loops/r23k_validation/window_15` | `1a5f2d0` | 2026-10-19 | 0 |
| W16 | `loops/r23k_validation/window_16` | `5f3c177` | 2026-10-19 | 3 |
| W17 | `loops/r23k_validation/window_17` | `b84b1a1` | 2026-10-20 | 1 |
| W18 | `loops/r23k_validation/window_18` | `b84b1a1` | 2026-10-20 | 5 |
| W19 | `loops/r23k_validation/window_19` | `b84b1a1` | 2026-10-20 | 4 |
| W20 | `loops/r23k_validation/window_20` | `b84b1a1` | 2026-10-20 | 6 |
| W21 | `loops/r23k_validation/window_21` | `d39f723` | 2026-10-20 | 5 |
| W22 | `loops/r23k_validation/window_22` | `190f596` | 2026-10-20 | 1 |

Cada `trades.csv` tiene **exactamente 1 commit** que lo toca (`git log --format=%h -- <ruta>/trades.csv` = 1 en las 19 ventanas): no hay reescrituras posteriores. Suma de filas = **61**, consistente con el conteo crudo de §6 (60 en ledger + 1 `RESIDUAL_INTERWINDOW_TRADE`).

**Verificación de presencia en `8cdb61f`** (`git cat-file -e 8cdb61f:<ruta>/trades.csv`):

| Ruta | exit |
| :--- | :-: |
| `window_01`, `window_02`, `window_03`, `window_04_clean`, `window_05_clean` (r23h) | 0 |
| `r23i_validation/window_06_clean` | 0 |
| `r23j_validation/window_07_clean` … `window_11_clean` | **128** (no existe) |
| `r23k_validation/window_12` … `window_22` | **128** (no existe) |

Matiz importante: `9442c1f` (2026-10-18 20:08:26 -0500) es **anterior** a `8cdb61f` (2026-10-18 20:49:43 -0500), por eso W04–W06 sí están dentro. W07 se añadió en `ab5ddcd` (21:46:41), **después** de congelar la build.

**Build declarada por ventana** (`LIVE_REPORT.md`, campo `gitSha=`):

| Ventanas | `gitSha` declarado |
| :--- | :--- |
| W01–W06 (`r23h`/`r23i`) | `aac1df3` (versionCode 19, buildId `R23H-BUY-EXHAUSTION`) |
| W07–W22 (`r23j`/`r23k`) | `8cdb61f` (mismo versionCode/buildId) |

`aac1df3` es ancestro de `8cdb61f` (`git merge-base --is-ancestor` exit 0; 10 commits entre ambos).

**Conclusión (una línea):** el Cubo 2 **no** es atribuible a una sola build ni a un solo commit — sus datos entran en **14 commits distintos** (`9442c1f`, `ab5ddcd`, `a8fb9a1`, `46907c6`, `e2ae626`, `16b2167`, `eaeff51`, `db12a6f`, `79c6046`, `1a5f2d0`, `5f3c177`, `b84b1a1`, `d39f723`, `190f596`) y provienen de **dos builds** según su propio `LIVE_REPORT.md` (`aac1df3` para W04–W06, `8cdb61f` para W07–W22); además `8cdb61f` **no contiene** ninguno de los `trades.csv` de W07–W22, así que la evidencia de la build congelada se versionó después de congelarla.

### A1b.2 · Emparejamiento live↔replay de los 22 pares del Cubo 1 (DISCREPANCIA #3)

Fuente: los 16 `replay_comparison.json` de W07–W22, campo `pairs[]` (solo los que tienen `live` y `replay` no nulos). Total **22 pares**, `status` = 20 `MATCH` + 2 `DIVERGENT`.

**Causa raíz del `trade_id` vacío (no es un dato perdido, es un literal):** `scripts/validation/compare_live_replay.py:298` escribe `"trade_id": ""` fijo al parsear `replay_journal.csv`. El CSV de replay **no tiene columna `trade_id`** en ninguna de las 16 ventanas (verificado: `has_col trade_id = False`; 59/59 filas sin el campo). Es decir, el replay nunca pudo aportar `trade_id`.

**Recuento por mecanismo de emparejamiento** (reconstruido aplicando la regla real de `compare_live_replay.py:404-418`: 1º `decision_id` exacto, 2º fallback temporal con mismo `asset` y `|dt| ≤ 6000 ms`):

| Mecanismo | Pares | % |
| :--- | :-: | :-: |
| Por `decision_id` exacto | **12** | 54,5 % |
| Solo por proximidad temporal (≤ 6000 ms) | **10** | 45,5 % |
| **Total** | **22** | 100 % |

De los 10 temporales, **1 es `DIVERGENT`** (W11, `f832ecec` vs `11515505`) y 9 son `MATCH`. De los 12 por ID, **1 es `DIVERGENT`** (W06, `548730e7` — mismo ID, acción distinta).

**Desambiguación:** para los 22 pares, el número de filas de replay candidatas dentro de ±6000 ms con el mismo `asset` es **1 en todos los casos** (`cands_in_tol=1`). No hay pares ambiguos: el fallback temporal no "elige" entre varios candidatos.

**Tabla de deltas temporales** `Δ = ts_replay − ts_live` (ms). Negativo = el replay marca la decisión **antes** que el live.

| Ventana | n | min | mediana | max | Mecanismo |
| :--- | :-: | :-: | :-: | :-: | :--- |
| W04 | 1 | −1152 | −1152 | −1152 | temporal |
| W05 | 1 | −5698 | −5698 | −5698 | temporal |
| W06 | 2 | −4283 | −4069,0 | −3855 | 1 ID + 1 temporal |
| W07 | 1 | −2881 | −2881 | −2881 | temporal |
| W08 | 4 | −3648 | −2351,0 | −2040 | 2 ID + 2 temporal |
| W09 | 1 | −1787 | −1787 | −1787 | ID |
| W11 | 1 | −3856 | −3856 | −3856 | temporal |
| W13 | 5 | −5916 | −2041,0 | −1308 | 2 ID + 3 temporal |
| W14 | 1 | −1690 | −1690 | −1690 | ID |
| W16 | 1 | −1288 | −1288 | −1288 | temporal |
| W18 | 2 | −4337 | −3738,0 | −3139 | ID |
| W19 | 3 | −3681 | −2924,0 | −2701 | ID |
| W20 | 1 | −4246 | −4246 | −4246 | ID |
| W21 | 2 | −4079 | −3760,0 | −3441 | temporal |
| **Cubo 1 (22 pares)** | **22** | **−5916** | **−2902,5** | **−1288** | 12 ID / 10 temporal |

**Observaciones duras:**

1. **Los 22 deltas son negativos.** El replay siempre adelanta al live entre 1,288 s y 5,916 s. Un sesgo de signo único en 22/22 no es ruido simétrico: apunta a que el `timestamp` del replay es el instante de **evaluación de la vela** y el del live el de **confirmación de la orden** (que siempre llega después).
2. **Margen sobre la tolerancia: 84 ms.** El delta absoluto mayor es 5,916 ms contra un límite de 6,000 ms (`compare_live_replay.py:376`). Un solo par más lento habría caído fuera del emparejamiento temporal y el conteo de pares habría bajado.
3. **El emparejamiento por ID tampoco es verificable de forma independiente**, porque el `decision_id` de replay sale de `replay_journal.csv` (generado por el mismo pipeline) y en 10/22 no coincide con el de live. La coincidencia en 12/22 sí es evidencia de que el ID se propaga cuando el replay se ejecutó sobre el mismo tick.
4. **Consecuencia sobre la cobertura:** 22/80 = 27,50 % sigue siendo correcto, pero de esos 22, solo **12 (15,0 % de la unión) tienen anclaje por identificador**; los otros 10 (12,5 %) descansan únicamente en proximidad temporal con tolerancia de 6 s.

### A1b.3 · Reproducibilidad de los números de esta adenda

```powershell
# A1b.1
git log --diff-filter=A --format="%h|%ad|%s" --date=short -- loops/r23k_validation/window_22/trades.csv
git cat-file -e 8cdb61f:loops/r23j_validation/window_11_clean/trades.csv   # exit 128
git cat-file -e 8cdb61f:loops/r23i_validation/window_06_clean/trades.csv   # exit 0
# A1b.2
python -c "import json,io;d=json.load(io.open('loops/r23k_validation/window_13/replay_comparison.json',encoding='utf-8'));print(d['summary']);[print(p['status'],p['live']['decision_id'],p['replay']['decision_id'],p['replay']['timestamp']-p['live']['timestamp']) for p in d['pairs'] if p.get('live') and p.get('replay')]"
```

Sin cambios en ningún otro archivo del repositorio.
