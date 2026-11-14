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

3. **Los objetos `replay` de los 59 pares tienen `trade_id` vacío (59/59).** Solo traen `decision_id`, y en **10 de los 22 pares evaluables el `decision_id` de live ≠ el de replay**. Tampoco coinciden `timestamp` ni `candle_second` (delta 1.288–5.916 ms, mediana 2.902,5 ms). Es decir: **el emparejamiento live↔replay no es verificable por ID ni por timestamp exacto**; se apoya en proximidad temporal. Esto no invalida el conteo de pares pero sí impide auditar la correspondencia.

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

Desglose exacto `status × mecanismo` del Cubo 1 (leído del campo `matched_by` que escribe el propio comparador en `compare_live_replay.py:459`, no inferido):

| status | `matched_by` | Pares |
| :--- | :--- | :-: |
| `MATCH` | `decision_id` | **12** |
| `MATCH` | `temporal` | **8** |
| `DIVERGENT` | `temporal` | **2** (W08 `fc7bbff7` vs `932c27eb`; W11 `f832ecec` vs `11515505`; ambas BUY/SELL, causa `action`) |
| `DIVERGENT` | `decision_id` | **0** |

**Los 12 pares por `decision_id` son todos `MATCH`.** El par `DIVERGENT` por ID (`548730e7`, W06) **pertenece al Cubo 2**, no al Cubo 1 (`CUBO1_WINDOWS` = W07–W22, `recompute_cubes.py:61-62`), y su causa es `candle_second` (ambos lados BUY), no `action`.

**Desambiguación:** para los 22 pares, el número de filas de replay candidatas dentro de ±6000 ms con el mismo `asset` es **1 en todos los casos** (`cands_in_tol=1`). No hay pares ambiguos: el fallback temporal no "elige" entre varios candidatos.

**Tabla de deltas temporales** `Δ = ts_replay − ts_live` (ms). Negativo = el replay marca la decisión **antes** que el live. Las filas W04–W06 se incluyen solo como contexto (son **Cubo 2**, no Cubo 1):

| Ventana | n | min | mediana | max | Mecanismo |
| :--- | :-: | :-: | :-: | :-: | :--- |
| W04 (Cubo 2) | 1 | −1152 | −1152 | −1152 | temporal |
| W05 (Cubo 2) | 1 | −5698 | −5698 | −5698 | temporal |
| W06 (Cubo 2) | 2 | −4283 | −4069,0 | −3855 | 1 ID + 1 temporal |
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
python -c "import json,io;d=json.load(io.open('loops/r23k_validation/window_13/replay_comparison.json',encoding='utf-8'));print(d['summary']);[print(p['status'],p['matched_by'],p['live']['decision_id'],p['replay']['decision_id'],p['replay']['timestamp']-p['live']['timestamp']) for p in d['pairs'] if p.get('live') and p.get('replay')]"
```

Sin cambios en ningún otro archivo del repositorio.

### A1b.4 · Correcciones a A1b.1–A1b.2 y evidencia nueva

**C1 · El desglose `MATCH`/`DIVERGENT` por mecanismo estaba mal.** A1b.2 afirmaba «de los 10 temporales, 1 es DIVERGENT (W11)» y «de los 12 por ID, 1 es DIVERGENT (W06)». Ambas frases son falsas:

- Hay **2** `DIVERGENT` temporales en el Cubo 1: W08 (`fc7bbff7` BUY vs `932c27eb` SELL) y W11 (`f832ecec` BUY vs `11515505` SELL). La propia tabla de A1b.2 ya decía «W08 … 2 ID + 2 temporal» con 1 divergencia, contradiciendo el texto.
- El `DIVERGENT` por ID es W06, que **no está en el Cubo 1** (`CUBO1_WINDOWS` = W07–W22). Su causa es `candle_second`, no `action`:

```
$ python -c "... divergence_breakdown ..."
W06_clean: {"candle_second": 1}   live BUY / replay BUY   first_divergence=candle_second
W08_clean: {"action": 1}          live BUY / replay SELL
W11_clean: {"action": 1}          live BUY / replay SELL
```

**Consecuencia:** los 12 pares anclados por `decision_id` son **12/12 `MATCH`** (100 %). El «12/22 por ID» es por tanto un subconjunto limpio, no una muestra con 1 fallo. La cifra de cobertura anclada (12/80 = 15,0 %) se mantiene; la de «acuerdo sobre pares formados» sube de 20/22 a 20/22 igual, pero la lectura «el ID también diverge» desaparece del Cubo 1.

**C2 · Verificación por re-ejecución, no por inferencia.** Se re-ejecutó el comparador congelado (`scripts/validation/compare_live_replay.py`, HEAD) sobre las 19 ventanas, `--mode trades`, con los `trades.csv`/`replay_journal.csv` versionados:

| Ventana | Artefacto commitado (paired/match/div) | Re-ejecución (paired/match/div) | ¿Igual? |
| :--- | :-: | :-: | :-: |
| W04–W05, W07, W09–W10, W12, W14–W15, W16–W22 | idénticos | idénticos | **SÍ** |
| **W06** | 2 / **1** / **1** | 2 / **2** / **0** | **NO** |

**W06 es el único artefacto no reproducible con el comparador congelado.** Causa localizada: `577a9fa` (2026-10-18 20:04:18 −0500 = **2026-10-19 01:04:18 UTC**) subió `temporal_tolerance_ms` de 3000 a 6000 **y** añadió la exención `_latencia_explica_segundo_de_vela` (`compare_live_replay.py:317-370, 446-448`). Ejecutando la versión **anterior** (`a48e944`) con `--temporal-tolerance-ms 6000` sobre W06 se obtiene exactamente `2/1/1` → **el artefacto de W06 lo generó el comparador previo a la congelación**, no el congelado.

**Pero la procedencia real de W04–W06 es más amplia que «solo W06», y se demuestra por firma, no por el triple resumen.** Comparando la lista completa de pares `(status, matched_by, delta_ms)` del artefacto commiteado contra la salida de `a48e944 --temporal-tolerance-ms 6000`:

| Ventana | Pares commiteados | `a48e944 @ 6000` | Firma idéntica |
| :--- | :-: | :-: | :-: |
| W04_clean | 15 (1 MATCH temporal −1152) | 15 | **SÍ** |
| W05_clean | 8 (1 MATCH temporal −5698) | 8 | **SÍ** |
| W06_clean | 5 (1 MATCH temporal −3855, 1 DIVERGENT decision_id −4283) | 5 | **SÍ** |

Los tres son equivalentes a la salida del comparador **pre-congelación** con 6000 ms explícito. La distinción con el congelado solo importa en W06 (donde el congelado da `2/2/0`); en W04 y W05 ambos comparadores coinciden a 6000 ms.

**Y el 6000 ms no pudo salir del pipeline automático.** El monitor nunca pasa el flag: `live_monitor_r23.py` construye el `cmd` con solo `--live-journal`, `--output-json`, `--output-md` y opcionalmente `--replay-journal` (`live_monitor_r23.py:283-290` en HEAD; mismo patrón en `957ce13`, `08073c3`, `0f3672d`, `dcce480`, `577a9fa`). No aparece `--temporal-tolerance-ms` en ninguna revisión. Por tanto los artefactos de W04–W06 se **regeneraron a mano** con un flag no-default; los `WINDOW_REPORT.md` de W04 y W05 lo declaran explícitamente («`temporal_tolerance_ms = 6000`»).

Consecuencia sobre el Cubo 2: de sus 3 divergencias, **1 (W06) es un artefacto de una versión anterior del comparador** y las otras 2 (W08, W11) son `action` reales. Re-ejecutando todo W04–W22 con el comparador congelado: **26 pares, 24 MATCH, 2 DIVERGENT, 92,31 %**; los artefactos commiteados dan **26 / 23 / 3 / 88,46 %**.

**C3 · Sensibilidad a la tolerancia — medida, no estimada.** A1b.2 dejó abierto «cuántos pares caerían con 3000 ms». Re-ejecutado con el comparador congelado:

| Tolerancia | Pares Cubo 1 | MATCH | DIVERGENT | Paridad | `pass >= 90 %` |
| :--- | :-: | :-: | :-: | :--- | :-: |
| 3000 ms | **16** | 11 | 5 | **68,75 %** | FAIL |
| 3500 ms | 17 | 13 | 4 | 76,47 % | FAIL |
| 4000 ms | 20 | 16 | 4 | 80,00 % | FAIL |
| 4500 ms | 21 | 19 | 2 | 90,48 % | PASS |
| 5000 ms | 21 | 19 | 2 | 90,48 % | PASS |
| 5500 ms | 21 | 19 | 2 | 90,48 % | PASS |
| 6000 ms | **22** | 20 | 2 | **90,91 %** | PASS |

Los **6 pares que aparecen** al pasar de 3000 a 6000 ms, con su desenlace real: W08 (+1 `MATCH` temporal −3648), W11 (+1 **`DIVERGENT`** temporal −3856), W13 (+2 `MATCH` temporal −3690 y −5916), W21 (+2 `MATCH` temporal −3441 y −4079). Es decir **5 `MATCH` y 1 `DIVERGENT`**, no 6 rescates: el de W11 empeora la tasa. Además W08 ya tenía un `DIVERGENT` temporal a −2637 que **persiste** a 6000 ms (el par nuevo se añade, no sustituye). **Con 3000 ms el Cubo 1 daría 16 pares / 68,75 %** y el criterio `pass = tasa >= 90 %` (`compare_live_replay.py:491`) habría dado FAIL.

**Matiz importante: el PASS no es exclusivo de 6000 ms.** El punto de cruce está entre 4000 y 4500 ms: a partir de 4500 ms la paridad ya es 90,48 % y el criterio se cumple. Es decir, el resultado es robusto en la banda 4500–6000 ms y frágil por debajo de 4000. La lectura correcta no es «el PASS depende de un umbral arbitrario de 6000», sino «el PASS depende de un umbral ≥ 4500 ms, y el preregistro no fija ese valor». La diferencia entre 4500 y 6000 es de 1 par (W13 a −5916).

**Hueco del preregistro: prohíbe ajustar la tolerancia pero nunca fija su valor.** `PREREGISTRO_R23K.md` solo menciona la tolerancia en `:318-319` («Ajustar tolerancias … a mitad de cubo») y en el hash del comparador (`:40`, `:339`). No hay ninguna línea que declare `temporal_tolerance_ms = 6000` como el valor del ciclo. Buscando `toleran|3000|6000` en las 341 líneas del cuerpo preregistrado solo salen esas tres. Por tanto el criterio de emparejamiento del Cubo 1 **nunca quedó preregistrado**: se heredó del default del comparador congelado. Eso hace que la sensibilidad 68,75 % ↔ 90,91 % sea una decisión no documentada, aunque `577a9fa` sea anterior a W07.

**Precisión sobre el orden temporal (corregida).** El umbral de 6000 ms **no es post hoc para el Cubo 1**. Cronología verificada, toda en UTC para evitar el desfase −0500 de los commits:

| Evento | UTC |
| :--- | :--- |
| W04 (primera ventana del Cubo 2) | 2026-10-18 20:06:35 |
| W05 | 2026-10-18 22:08:31 |
| W06 | 2026-10-19 00:05:38 |
| **`577a9fa`** (3000 → 6000 ms + exención de latencia) | 2026-10-19 **01:04:18** |
| W07 (primera ventana del Cubo 1) | 2026-10-19 02:27:46 |
| W08 / W09 / W10 / W11 | 03:28:34 / 04:04:09 / 04:44:36 / 05:28:09 |
| **Preregistro `38e54e8`** | 2026-10-19 **06:22:57** |
| W12 … W22 | 07:08:18 (19-oct) … 01:09:07 (21-oct) |

`577a9fa` (01:04:18 UTC) precede a **todas** las ventanas del Cubo 1 (W07 arranca 1 h 23 min después). Dentro del Cubo 1 el umbral es por tanto **a priori**, y el 68,75 % a 3000 ms es **sensibilidad legítima**, no evidencia de manipulación. La prohibición de `PREREGISTRO_R23K.md:318-319` («ajustar tolerancias a mitad de cubo») **no se viola en el Cubo 1**.

**El defecto real es el orden del preregistro, no la tolerancia.** `38e54e8` (06:22:57 UTC del 19-oct) es **posterior a W04–W11** (20:06 UTC 18-oct → 05:28 UTC 19-oct): 8 de las 19 ventanas del Cubo 2 se capturaron **antes** de que existiera el preregistro que las declara a priori. Solo W12–W22 están cubiertas. Y en el Cubo 2 el cambio de tolerancia **sí** cae a mitad de cubo: W04–W06 se midieron con el comparador de 3000 ms y W07–W22 con el de 6000 ms, así que la serie del Cubo 2 mezcla dos criterios de emparejamiento (esto es lo que explica el artefacto de W06 documentado en C2).

**C4 · La build congelada sí explica W07–W22 (identidad de código, no solo autorreporte).** A1b.1 solo citaba el `gitSha` declarado en `LIVE_REPORT.md`. Verificado por diff:

```
$ git diff --name-only 8cdb61f 190f596 -- app/src/main      # (vacío)
$ git diff --stat 8cdb61f 190f596 -- app/                   # (vacío)
```

**Entre `8cdb61f` y HEAD no cambió ni una línea de `app/`.** Por tanto W07–W22 (cuyo `LIVE_REPORT` declara `gitSha=8cdb61f`) se ejecutaron sobre el mismo código que hay hoy en HEAD, y el desfase es solo de versionado de artefactos. `aac1df3` → `8cdb61f` sí toca ambos archivos de decisión, pero **es refactor puro** (verificado, ver abajo):

```
AdaptiveLearningEngine.kt | 230 +++++-----   (extracción de `mejorCoincidenciaEnMemoria`)
TradingEngine.kt          |  90 ++++--      (extracción de `calcularPoderesDeMercado`)
+ 4 archivos de test nuevos (1206 insertions, 97 deletions)
```

`TradingEngine.kt` pasa de calcular `callPower`/`putPower` inline a llamar `calcularPoderesDeMercado(...)` con los mismos literales (`50`, `68`/`32`, `+10`, `+8`, `+6`, `0.25f`, `0.20f`, `62`/`38`, `coerceIn(15, 95)`). `AdaptiveLearningEngine.kt` mueve el recorrido de `lossSignatures.takeLast(20)` a `mejorCoincidenciaEnMemoria(...)`, que reproduce el mismo bucle `if (sim > similitudMaxima)` con el mismo `takeLast(20)`.

**Corrección de una afirmación previa de esta adenda (falsa).** A1b.4 dijo antes que `AdaptiveLearningEngine` «además introduce el umbral 0.99f». **Es falso.** El umbral ya existía en `aac1df3`:

```
$ git show aac1df3:app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt | Select-String "0.99f|ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD"
        const val ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f
        // R23F: umbral de similitud elevado a 0.99f tras auditoría contrafactual R23E.
```

Idéntico en `8cdb61f` (línea 61); lo único que cambia es que el comentario se traslada a KDoc. Extracción de todos los literales numéricos de ambos archivos en ambas builds:

| Archivo | `aac1df3` | `8cdb61f` |
| :--- | :--- | :--- |
| `AdaptiveLearningEngine.kt` | `0.02f 0.10f 0.15f 0.20f 0.22f 0.25f 0.30f 0.35f 0.40f 0.50f 0.99f 1.0f 2.0f 2.2f 4.0f` | **idéntico** |
| `TradingEngine.kt` | `0.0f 0.0001f 0.05f 0.08f 0.1f 0.12f 0.15f 0.20f 0.25f 0.30f 0.40f 0.55f 0.735f 0.75f 0.80f 0.838f 0.844f 0.85f 0.865f 0.881f 0.90f 0.903f 0.95f 0.99f 1.0f 1.15f` | **idéntico** |

**Conclusión:** `aac1df3`→`8cdb61f` es **refactor puro en ambos archivos**, sin cambio de comportamiento de decisión. No se ha demostrado ninguna diferencia de comportamiento entre las dos builds; por tanto W04–W06 y W07–W22 son el mismo motor en lo que a lógica de trading respecta. Son binarios distintos (y eso basta para que `LIVE_REPORT` muestre `gitSha` diferentes), pero funcionalmente equivalentes en las constantes y ramas que deciden. La afirmación «dos builds» de A1b.1 sigue siendo correcta en sentido literal —dos artefactos APK distintos— pero **no implica dos comportamientos**.

**Único cambio real entre las dos builds: acentos en los strings emitidos.** `aac1df3` emite `"Anti-patrón perdedor recurrente…"`; `8cdb61f` emite `"Anti-patron perdedor recurrente…"` (también `inversión`→`inversion`, `pérdida`→`perdida`). No afecta a la decisión porque los consumidores son acento-insensibles: `extractAdaptiveSimPct` casa con la regex `(\d{1,3})\s*%\s*similitud` (`TradingEngine.kt:2775`) y el monitor filtra por la subcadena `"ADAPTIVE_BLOCK"` en mayúsculas. Así que «refactor puro» vale para **comportamiento de decisión**, no para identidad byte a byte de la telemetría. Queda declarado.

**Verificación del comparador congelado (hash).** El SHA-256 del blob es el mismo en `8cdb61f`, `577a9fa`, HEAD y el árbol de trabajo, y coincide con el preregistrado:

```
$ sha256(git show <sha>:scripts/validation/compare_live_replay.py)
8cdb61f  4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f
577a9fa  4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f
190f596  4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f
a48e944  558153ed7386ba9b685fd0a886a3b3d5e1c86e69aed621104443a7a9dbdea942   (pre-congelación)
```

`PREREGISTRO_R23K.md:40` y `:339` declaran `4cb5082e…` como hash del comparador congelado. **La afirmación de integridad del comparador es verificable y correcta.** El comparador que produjo los artefactos de W04–W06 (`a48e944`) es un binario distinto, no el congelado.

**C5 · `r23j_validation` sí existe en `8cdb61f`, pero solo con el manifiesto.** A1b.1 dijo «`r23j_validation/window_07_clean` … `window_11_clean` → 128 (no existe)». Correcto para los `trades.csv`, pero conviene precisar el estado real del árbol en ese commit:

```
loops/r23h_validation → 53 archivos (incluye window_01..05_clean/trades.csv)
loops/r23i_validation → 12 archivos (incluye window_06_clean/trades.csv)
loops/r23j_validation →  1 archivo  (solo EXCLUDED_ARTIFACTS_MANIFEST.json)
loops/r23k_validation →  0 archivos
```

`trades.csv` presentes en `8cdb61f`: W01, W02, W03, W04, W05, W06 — **exactamente los seis de las builds `aac1df3`**. Los hashes coinciden con el árbol de trabajo (`git rev-parse 8cdb61f:<ruta>` == `git hash-object <ruta>` para W04/W05/W06), así que esos datos no se reescribieron después.

**C6 · Precisión sobre la relación de commits.** `9442c1f` (que añade W04–W06) es **ancestro** de `8cdb61f` (`git merge-base --is-ancestor 9442c1f 8cdb61f` → exit 0, 4 commits de distancia). Los otros 13 commits que añaden datos son **descendientes** de `8cdb61f` (exit 1 en la comprobación inversa). Es decir: la build congelada precede a la evidencia de W07–W22, no al contrario.

**Conclusión corregida (una línea):** el Cubo 2 se reparte en **14 commits** y **2 builds** (`aac1df3` W04–W06, `8cdb61f` W07–W22); el código de `8cdb61f` es idéntico al de HEAD, así que W07–W22 son atribuibles a la build congelada aunque sus `trades.csv` se versionaran después; `aac1df3`→`8cdb61f` es **refactor puro** (mismas constantes, mismas ramas: no hay diferencia de comportamiento demostrada entre las dos builds); y **los tres artefactos de paridad de W04–W06 los generó el comparador pre-congelación `a48e944` con 6000 ms explícito**, no el congelado.

**C7 · El preregistro admite por escrito que W04–W11 ya estaban capturadas.** No hace falta inferirlo de los timestamps: `PREREGISTRO_R23K.md` lo declara en su propia tabla de punto de partida (§1.5):

```
:87: | Trades confirmados en Cubo 2 (W04–W11) | **29** |
:88: | Ventanas ya capturadas | 8 (W04, W05, W06, W07, W08, W09, W10, W11) |
:259: | Cubo 2 (Homogéneo) | W04…W11 + nuevas de esta sesión | Abierto | único que cuenta para N=80 |
```

Y `:274-278` reconoce la diferencia de arnés: «Las ventanas W04–W11 se capturaron **antes** del Monitor v2… el Monitor v1 no tenía `grace_wait_ms` ni transición a `DISABLED`». La regla de invalidación está en `:16`: «Cualquier modificación posterior a la primera ventana medida **invalida el ciclo completo**». Aplicando la regla del propio documento, el análisis confirmatorio del Cubo 2 queda invalidado por construcción: 8 de sus 19 ventanas preceden al preregistro que las declara a priori.

La enmienda `8e2d149` (2026-10-22) añade 169 líneas de «ENMIENDA DE FUTILIDAD · CIERRE DEL CUBO 2» y se etiqueta a sí misma como «post hoc» y «no modifica el cuerpo preregistrado (líneas 1-341)». **No legitima retroactivamente W04–W11.** El defecto de orden se mantiene.

Nota: `loops/r24_verify/A1_cubes.md:25` conserva el título generado automáticamente "DESEMPEÑO HOMOGÉNEO"; el Cubo 2 NO es homogéneo (dos builds: aac1df3 W04–W06, 8cdb61f W07–W22). El título del artefacto generado no se edita a mano; prevalece el informe.

---

## A1c · IMPLICACIONES PARA EL CIERRE

### A1c.1 Defectos que invalidan o degradan el Cubo 2

| # | Defecto | Evidencia | Efecto |
| :-: | :--- | :--- | :--- |
| **(a)** | **Preregistro posterior a 8 de las 19 ventanas.** El documento que declara las ventanas «a priori» se fijó después de medirlas. | `PREREGISTRO_R23K.md:88` («Ventanas ya capturadas \| 8 (W04…W11)»), `:87` (29 trades ya confirmados), `:259` (Cubo 2 = W04…W11 + nuevas). Cronología UTC: W04 2026-10-18 20:06:35 → W11 2026-10-19 05:28:09; preregistro `38e54e8` 2026-10-19 **06:22:57**. Regla propia en `:16`: «Cualquier modificación posterior a la primera ventana medida **invalida el ciclo completo**». | **Invalida por construcción.** No es un defecto de datos: es el documento el que se autodescalifica. |
| **(b)** | **La tolerancia temporal nunca se preregistró**, y 3 artefactos de paridad se generaron con un flag manual no auditado. | `PREREGISTRO_R23K.md` solo menciona tolerancia en `:318-319` (prohibición de ajustarla) y en el hash del comparador (`:40`, `:339`). Ningún valor. Ningún monitor pasa `--temporal-tolerance-ms`: `scripts/validation/live_monitor_r23.py:283-290` y `live_monitor_r23_autonomous.py:1514-1519` construyen el `cmd` sin el flag. Los `WINDOW_REPORT.md` de W04 (`:39`) y W05 (`:36`) declaran `temporal_tolerance_ms = 6000` explícito → regeneración manual. | **Degrada el Cubo 1 y contamina el Cubo 2.** Barrido medido: 3000 ms → 16 pares / 68,75 % **FAIL**; 4500 ms → 21/19/2 90,48 % PASS; 6000 ms → 22/20/2 90,91 % PASS. El PASS depende de un umbral **≥ 4500 ms que el preregistro no fija**. |
| **(c)** | **Dos binarios en el mismo cubo.** | W04–W06 con `gitSha=aac1df3`; W07–W22 con `gitSha=8cdb61f`. `git diff --stat 8cdb61f 190f596 -- app/` → vacío (HEAD idéntico a la build congelada). `aac1df3→8cdb61f` es refactor puro: mismas constantes en ambos archivos (`0.99f`, `0.02f`…`4.0f` en `AdaptiveLearningEngine.kt`; `0.735f`, `0.838f`, `0.844f`… en `TradingEngine.kt`), mismas ramas. | **Degrada, no invalida.** Dos APK distintos rompen la homogeneidad declarada (`:259` «Cubo 2 (Homogéneo)»), pero no se demostró diferencia de comportamiento de decisión. Único cambio real: acentos en strings de telemetría (`Anti-patrón`→`Anti-patron`), inocuo para los consumidores. |
| **(d)** | **W06 es irreproducible con el comparador congelado.** | Con `a48e944` + 6000 ms → `2/2/0`. Con el congelado `4cb5082e…` → `2/1/1` (`DIVERGENT decision_id −4283`). `loops/r23i_validation/window_06_clean/replay_comparison_report.md:8` dice `FAIL` y `REPLAY_PARITY_REPORT.md:8` dice `PASS` — dos veredictos contradictorios en el mismo directorio. | **Degrada.** De las 3 divergencias del Cubo 2, 1 (W06) es artefacto de herramienta; quedan 2 reales (W08, W11). Re-ejecutado todo con el congelado: **26 pares / 24 MATCH / 2 DIVERGENT = 92,31 %**, frente a 26/23/3 = 88,46 % de los artefactos commiteados. |

**Defecto transversal que agrava los cuatro:** la serie del Cubo 2 mezcla **dos criterios de emparejamiento** (W04–W06 a 3000 ms con `a48e944`; W07–W22 a 6000 ms con `4cb5082e`) y **dos arneses** (Monitor v1 sin `grace_wait_ms` ni `DISABLED` para W04–W11; Monitor v2 desde `dcce480`), reconocido por el propio documento en `:274-278`. La prohibición `:318-319` («ajustar tolerancias a mitad de cubo») **se viola materialmente en el Cubo 2**, aunque no en el Cubo 1 (donde `577a9fa` precede a W07).

### A1c.2 Clasificación: ¿el Cubo 2 sostiene el veredicto «SIN EVIDENCIA DE EDGE»?

**No. Un cubo invalidado por su propio preregistro no puede sostener nada — ni una afirmación ni una negación.** La invalidez de (a) no es un problema de potencia estadística ni de calidad de datos: es un fallo de orden inferencial. El documento que define el criterio de éxito existe después de 8 de las 19 ventanas que dice gobernar. Cualquier lectura del Cubo 2 como «análisis confirmatorio» —incluida la lectura negativa— hereda ese defecto. Simétricamente: tampoco se puede concluir «hay edge», que es precisamente lo que la regla de oro (`:14-17`) pretendía impedir.

**Pero el veredicto de futilidad es aritmético y sobrevive.** La demostración de `PREREGISTRO_R23K.md:377-404` no usa la paridad, ni la tolerancia, ni el orden del preregistro, ni la build. Usa tres números del ledger congelado y una identidad algebraica:

- `W = 30`, resueltos = 59, `N = 60`; tope `N = 80` → 20 huecos.
- Mejor caso absoluto `50/79 = 63,29 %` → `Wilson_inf = 52,28 %` < `BE = 54,64 %`.
- Contabilidad más generosa (VOID como win + 21 huecos como aciertos): `51/80 = 63,75 %` → `Wilson_inf = 52,81 %`, igualmente por debajo.
- Para cruzar el BE con `N = 80` harían falta 53 wins: 23 sobre los 30 actuales, con solo 20 huecos. **Imposible por aritmética de huecos.**

Ninguno de los cuatro defectos (a)–(d) toca esos números. (a) no cambia `W`, (b) no cambia `W`, (c) no cambia `W`, (d) afecta paridad y no balance. Y las sensibilidades de `A2.4` (sin W04 → 45,10 %; solo W07+ → 40,48 %; 2.ª mitad → 40,00 % / 41,38 %) **empeoran** el punto estimado, así que el agregado no está sostenido por un subconjunto favorable.

**Formulación correcta, que es la que debe ir al informe R24:** no «el Cubo 2 demuestra ausencia de edge», sino «**el ledger observado, con `W = 30` sobre 59 resueltos, tiene un techo aritmético de `Wilson_inf = 52,28 %` a `N = 80`, por debajo del breakeven 54,64 %; el diseño que produjo ese ledger está invalidado por su propio preregistro y por tanto no puede presentarse como análisis confirmatorio de nada.**» La futilidad es una **cota sobre los datos**; la invalidez es un **defecto del diseño**. Son dos afirmaciones independientes y ambas ciertas; mezclarlas en un solo veredicto es lo que hay que evitar.

**Riesgo residual que debe declararse:** la cota depende de la exactitud de `W/L/VOID = 30/29/1`. Esa contabilidad viene del mismo arnés mixto (v1/v2) cuyo `grace_wait_ms` ausente en W04–W11 hace «menos confiable» la liquidación al borde de ventana, según `:276-277`. Si alguna liquidación de W04–W11 está mal atribuida, el techo se mueve. No se ha auditado trade a trade; **la cota es tan sólida como el ledger, y el ledger no está auditado.**

### A1c.3 Qué debe hacer R24 distinto

1. **Preregistro commiteado y hasheado ANTES de la primera ventana del cubo nuevo**, con `temporal_tolerance_ms`, `--tolerance-price`, `--tolerance-dist`, `N`, payout y umbrales **fijados por valor numérico**, no por referencia al default de una herramienta.
2. **Un solo binario por cubo**: `sha256` del APK + `gitSha` registrados en el `window_metadata.json` de cada ventana, y rechazo automático de la ventana si no coincide con el declarado.
3. **Un solo arnés desde la primera ventana**: Monitor v2+ únicamente; ninguna ventana heredada de un ciclo anterior entra en el cubo nuevo, ni siquiera «porque comparte build».
4. **Comparador congelado por hash y ejecutado solo por el pipeline**: cero invocaciones manuales con flags; un comando regenera todos los artefactos de paridad y el `sha256` del script queda en el reporte.
5. **Criterio de cierre decidido a priori**: `N` fijo o regla de futilidad aritmética declarada antes de medir; prohibida cualquier enmienda post hoc que documente por qué el criterio no se alcanzó.
