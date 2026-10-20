# R24 — FASE 3: EVALUACIÓN DE CORRECCIÓN (TRACK B · W19)

**Ejecutor:** HERMES (`w1:pA`, Infraestructura y Desarrollo)
**Fecha:** 2026-10-19
**SHA base:** `34f8fdb` · **APK congelada:** `8cdb61f` (`versionCode=19`)
**Dictámenes evaluados:** CLAUDE CODE `PASS_WITH_LIMITATION` · OPENCODE `CONSISTENT`
**Ventana:** `loops/r23k_validation/window_19/`

---

## 0. VEREDICTO

**INTERVENCIÓN PARCIAL, CONFIRMANDO NO INTERVENCIÓN SOBRE `app/` Y SOBRE LA ESTRATEGIA R23K.**

| Objeto | Decisión | Motivo |
|---|---|---|
| `app/` · APK `8cdb61f` | **NO INTERVENCIÓN** | Congelamiento de Fase 3. La doble resolución es real pero queda como **deuda técnica R24**. |
| Estrategia R23K (umbrales, vetos, stake, confluencia) | **NO INTERVENCIÓN** | Ni una línea tocada. Verificado en §5. |
| `trades.csv` de W19 (ledger) | **NO INTERVENCIÓN** | Es evidencia del hardware. No se corrige: se audita. |
| `recompute_cubes.py` | **SÍ — intervenido** | Tenía W19 **fuera** del Cubo 1 mientras el ledger ya lo contaba. Publicaba `N=16` con 59 trades. |
| `live_monitor_r23_autonomous.py` | **SÍ — intervenido (3 líneas)** | El reporte presentaba el delta de contadores como si fueran trades. Ese es el canal del falso 5.º trade. |

El hallazgo de Claude/OpenCode es **correcto y lo reproduje**. Pero el encargo pregunta si hace falta intervención de tooling: **sí, y en un punto que ninguna de las dos auditorías detectó**.

---

## 1. REPRODUCCIÓN DEL HALLAZGO (antes de opinar)

Regla: un informe ajeno es una hipótesis. Lo verifiqué contra `status_samples.jsonl` sin leer sus conclusiones.

```
muestra   0  W=6 L=2  pending=False order=NINGUNA  dup=3  err=''
muestra  25  W=6 L=3  pending=False order=NINGUNA  dup=3  err=''
muestra  33  W=8 L=3  pending=False order=NINGUNA  dup=4  err='DUPLICADO RECHAZADO: 1792439641823'
muestra  47  W=9 L=3  pending=False order=NINGUNA  dup=4  err=''
muestra  57  W=9 L=4  pending=False order=NINGUNA  dup=4  err=''
```

- Salto `W 6→8` **en un solo trade** (muestra 33), con `journalDuplicatesRejected 3→4` y el error de idempotencia **en la misma muestra**. La correlación es exacta, no inferida.
- 4 filas en `trades.csv` ⇒ 4 trades reales. Contadores ⇒ 5 resoluciones. **Discrepancia +1 confirmada.**

**Mecanismo.** `TradingEngine.kt` tiene dos rutas de liquidación y **ambas** comparten el mismo patrón:

```kotlin
// TradingEngine.kt:637-641 (ruta de frame)  y  :2720-2727 (ruta headless/WS)
val resolved = riskManager.resolvePendingTradeWin()   // false si ya no hay trade pendiente
if (!resolved) {
    riskManager.recordTradeWin(force = true)          // <-- incrementa INCONDICIONALMENTE
}
```

`resolvePendingTradeWin()` devuelve `false` cuando `hasPendingTrade` ya es `false` (`RiskManager.kt:883-887`). El `else` interpreta "ya estaba resuelto" como "hay que forzarlo", y `recordTradeWin(force=true)` salta la guarda de `hasPendingTrade` (`RiskManager.kt:810-812`) e incrementa `totalWins` igual.

**El `AtomicBoolean isTradeResolving` NO cubre este caso.** En `:615` y `:2649` se libera con `set(false)` en varios puntos — incluido `:1943` y `:2464`, que son las rutas de ENVÍO (`recordTradeSent`). Un `isTradeResolving.set(false)` en la ruta de despacho reabre la ventana justo cuando la ruta de liquidación la está usando. No es un guard de sección crítica: es un flag de reentrada con dos escritores.

---

## 2. LAS DOS AUDITORÍAS SON CORRECTAS, CON DOS IMPRECISIONES

### 2.1 Error de aritmética en el P&L (ambas auditorías)

| Concepto | Valor |
|---|---|
| P&L acumulado W01..W18 (verificado) | **+438.000,64** COP |
| W19 neto | **−36.001,28** COP |
| **Total real** | **+401.999,36** COP |
| Claude §7 y OpenCode §3 dicen | +402.000,64 COP |
| **Desvío** | **−1,28 COP** |

No es redondeo: `438.000,64 − 36.001,28 = 401.999,36` exacto, y la suma directa del ledger da lo mismo. El valor 402.000,64 es el de **W18** reutilizado. **Discrepancia contable acumulada ≠ $0,00** en el texto: el ledger está bien, la cifra publicada no.

### 2.2 La "limitation" está infravalorada en su alcance

Ambos informes la tratan como un contador inflado sin efecto material. Reproduje los contadores y **hay efecto material sobre la política de stake**, no solo sobre el reporte:

```
m 0  streak=1  mart=[M1 | $2.0]
m25  streak=2  mart=[M2 | $2.0]
m33  streak=0  mart=[M0 | $1.0]   <- la doble resolución hace WIN y RESETEA la racha
```

`recordTradeWins` hace `currentLossStreak = 0` (`RiskManager.kt:832`). La carrera en el Trade 2 **borró una racha de 2 pérdidas** que el motor debía estar arrastrando. Con `martingaleEnabled = true` (default, `RiskManager.kt:159`), `getCurrentInvestmentAmount` dimensiona por `currentLossStreak` (`:943-952`): el reset cambió el stake del trade siguiente.

**En W19 no se materializó** porque `observedStake = 100.000` (el operador tiene el monto fijo en pantalla) y `getEffectiveStake` prioriza la cantidad observada sobre la calculada. Pero eso es una **coincidencia de configuración, no una propiedad del código**. Con el stake en automático, la carrera habría dimensionado el trade 3 con M0 en vez de M2.

Esto **no cambia el dictamen** (4 trades, 2W/2L, −36.001,28 COP, gap $0,00 son correctos) pero **sí cambia la severidad de la deuda**: no es cosmética.

---

## 3. INTERVENCIÓN 1 — `recompute_cubes.py` (el hueco real)

### 3.1 El defecto, medido

```
CANONICAL_WINDOWS ... W18          -> 55 trades
W19 presente en el ledger?          -> False
cubo_2 / cubo_1                     -> 44 / 27 trades
paridad Cubo 1                      -> N=16, 14 matches, 87.50%
```

W19 **entraba en el ledger** (55→59) pero **no en ningún cubo**: la lista de ventanas estaba **duplicada en tres literales** (`CANONICAL_WINDOWS`, `cubo1_trades`, `cubo2_trades`) y la paridad se calculaba sobre **dos constantes escritas a mano**:

```python
c1_pares_formales = 16      # literal
c1_matches = 14             # literal
```

Ese es el modo de fallo: **el instrumento no se equivoca de aritmética, se queda quieto mientras el conjunto crece**. Y un `N` congelado produce un veredicto (`FAIL 87.50%`) que parece medición y es memoria.

### 3.2 La corrección

| Antes | Después |
|---|---|
| `pares=16`, `matches=14` literales | `contar_paridad_replay()` lee los `replay_comparison.json` reales y cuenta por `status` |
| tres listas de ventanas duplicadas | `CUBO1_WINDOWS` como **fuente única**; Cubo 2 = `("W04","W05","W06") + CUBO1_WINDOWS` |
| ventana sin JSON contada como 0 pares | se declara en `ventanas_sin_paridad` |
| `por_ventana` invisible | expuesto en el JSON de salida |

**Criterio de conteo (congelado, criterio B5):** solo `MATCH` y `DIVERGENT` forman par evaluable. `MISSING_LIVE` / `MISSING_REPLAY` son órdenes sin contraparte y no entran ni en numerador ni en denominador.

### 3.3 Verificación de la corrección

El conteo derivado **coincide exactamente** con el recuento independiente que hice a mano ventana por ventana desde los 13 `replay_comparison.json`:

```
derivado a mano:  pares=19  matches=17  div=2  -> 89.47%
calculado por el instrumento: pares=19  matches=17  div=2  -> 89.47%   COINCIDEN
```

Desglose por ventana (nuevo, trazable):

| W07 | W08 | W09 | W10 | W11 | W12 | W13 | W14 | W15 | W16 | W17 | W18 | W19 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1/1 | 3/4 | 1/1 | 0 | 0/1 | 0 | 5/5 | 1/1 | 0 | 1/1 | 0 | 2/2 | **3/3** |

### 3.4 Verificación por inyección

| Defecto inyectado | Resultado |
|---|---|
| Quitar W19 de `CUBO1_WINDOWS` (reproduce el bug original) | **CAZA** — `AssertionError: Ventanas del ledger sin cubo asignado: ['W19']` |
| Contar `MISSING_*` como divergencia | **CAZA** — pares 19→62, tasa 89,47%→27,42% (detectable) |

Test nuevo permanente (§`run_synthetic_tests`, caso 6): ninguna ventana del ledger puede quedar **huérfana** ni en dos cubos, y el Cubo 2 debe contener al Cubo 1. La suite pasa de **5/5 a 6/6**.

---

## 4. INTERVENCIÓN 2 — `live_monitor_r23_autonomous.py` (el canal del falso 5.º trade)

### 4.1 El defecto

`LIVE_REPORT.md` se calculaba así:

```python
new_wins = cur_wins - initial_wins         # delta de contadores de /status
new_losses = cur_losses - initial_losses
total_trades = new_wins + new_losses
...
- Resoluciones: W={new_wins}, L={new_losses} (WR = {wr:.1f}%)
```

Es decir: **presentaba la telemetría del runtime como si fuera el conteo de trades**, en la misma sección donde ya escribía `len(observed_trades)` (el ledger). De ahí el `W=3, L=2` conviviendo con `4 operaciones` en el mismo documento, y de ahí que los cubos estuvieran a punto de sumar 5.

### 4.2 La corrección (3 líneas, aditiva)

Se reportan **las dos medidas etiquetadas** y una línea de conciliación que **declara la discrepancia** en vez de promediarla:

```
- Operaciones DEMO ejecutadas (ledger `trades.csv`, FUENTE CONTABLE): 4
- Delta de contadores de la APK (`totalWins`/`totalLosses`, TELEMETRIA de runtime): W=3, L=2 (WR = 60.0%)
- Conciliacion contadores vs ledger: AVISO: DISCREPANCIA de +1 resolucion(es) -> los cubos usan el
  ledger; los contadores pueden inflarse por doble resolucion concurrente en la APK (deuda R24).
```

Verificado renderizando con las variables reales de W19: detecta `+1` correctamente.

**No se corrige el contador. No se toca la app.** Solo se impide que el contador inflado **se publique como cifra contable**, y se hace visible cuando difiere. `git diff --numstat` → `3 añadidas / 2 borradas`.

---

## 5. CONGELAMIENTO VERIFICADO (no afirmado)

| Restricción | Prueba |
|---|---|
| **`app/` intacto** | `git status --short -- app/` → **0 líneas**. `git diff --stat -- app/` → **vacío**. `git diff --stat -- app/src/main/` → **vacío**. |
| **APK no recompilada** | `app-debug.apk` mtime = **2026-10-18_20:59:21**, anterior a esta sesión. No se corrió Gradle. |
| **Estrategia R23K intacta** | Cero cambios en `app/src/main/`. Los 4 archivos que toqué son `scripts/validation/*.py` + los 2 artefactos que esos scripts generan. |
| **`trades.csv` de W19 intacto** | No se editó: es evidencia. Los 4 trades se leen tal cual. |
| **Preregistro intacto** | No se tocó umbral, peso, veto, stake ni la compuerta de confluencia. |
| **Umbrales de los scripts** | `WILSON_Z95`, `breakeven`, `BORDER_TRADE_W10` sin cambios (verificado por extracto). |

### Archivos modificados en esta fase

| Archivo | Δ | SHA-256 |
|---|---|---|
| `scripts/validation/recompute_cubes.py` | 121+/19− | `0852d005b6c62d291ecaa008c1d5fd924952638636c922f89fccdff67416d1f4` |
| `scripts/validation/live_monitor_r23_autonomous.py` | 3+/2− | `935c4f85c638a2a22aa8bec01df25e82de6e23317c05b561b03fc712b034c6a5` |
| `workflows/rounds/R23K_CUBES_RECOMPUTED.{md,json}` | regenerado | — |

**No commité.** Queda para tu revisión, igual que el Track B.

---

## 6. CUBOS CON W19 REGISTRADO (cifras corregidas)

| Cubo | N | W | L | V | WR | Δ COP | Wilson 95% |
|---|---|---|---|---|---|---|---|
| Cubo 1 (paridad) | 31 | 14 | 16 | 1 | 46,67% | −444.001,28 | [30,23; 63,86] |
| Cubo 2 (W04–W19) | **48** | **27** | **20** | 1 | **57,45%** | **+223.997,44** | [43,28; 70,49] |
| Cubo 3 (W01–W03) | 11 | 7 | 4 | 0 | 63,64% | +178.001,92 | [35,38; 84,83] |
| **Total (W01–W19)** | **59** | **34** | **24** | 1 | **58,62%** | **+401.999,36** | [45,80; 70,37] |

**Paridad Cubo 1:** N=19, 17 matches, 2 divergencias → **89,47%** → **FAIL** (`< 90%`). El veredicto **no cambia** respecto a lo que declararon las auditorías; lo que cambia es que ahora está calculado sobre la muestra real (19) y no sobre una constante (16). Wilson: `[68,61; 97,06]`.

**Cubo 2 sigue `NO CONCLUYENTE`**: N=48 < 80 y límite inferior de Wilson 43,28% ≤ breakeven 54,64%.

W19 aislado, verificado contra el ledger: **4 trades · 2W/2L · WR 50,00% · −36.001,28 COP · gap intra-ventana $0,00 · gap inter-ventana vs W18 $0,00 · DD 0,68%**.

---

## 7. DEUDA TÉCNICA REGISTRADA PARA R24

### DT-01 · Doble resolución concurrente (severidad ALTA)

**Archivo:** `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
**Líneas:** `637-641` (ruta frame) · `2720-2727` (ruta headless) — **dos call sites, hay que corregir ambos**.

**Defecto:** el `else` de `if (!resolvePendingTradeWin())` invoca `recordTradeWin(force = true)`.

**Por qué es un bug y no una red de seguridad:** `resolvePendingTradeWin()` devuelve `false` en el caso **normal** de "este hilo perdió la carrera" (el otro ya resolvió). El `else` trata "ya resuelto" como "resuélvelo a la fuerza", que es exactamente lo contrario de lo que debe hacer.

**Dos efectos, no uno:**
1. `totalWins` +1 espurio → cifras de reporte infladas (el síntoma que reportaron las auditorías).
2. **`currentLossStreak = 0`** → borra la racha y altera el dimensionamiento de stake si el monto no está fijado en pantalla (el efecto que **no** detectaron).

**Corrección propuesta (no aplicada — congelamiento):**
- Eliminar el `else` con `force=true`; si `resolvePendingTradeWin()` devuelve `false`, la liquidación **ya ocurrió** y el hilo debe salir con `return` tras liberar `isTradeResolving`.
- **Y** separar `isTradeResolving` de las rutas de despacho: hoy `set(false)` en `:1943` y `:2464` (envío) reabre la sección crítica de liquidación. Debe haber **un flag de liquidación** que solo la ruta de settlement toque.
- Test de regresión: dos hilos resolviendo el mismo trade ⇒ `totalWins` incrementa **exactamente 1** y `currentLossStreak` **no** se resetea.

**Riesgo si no se corrige:** contadores inflados en cada ventana con resolución concurrente + stake potencialmente subdimensionado tras una racha. **No afecta la validez contable** de las ventanas ya auditadas (el ledger `trades.csv` es la fuente y es correcto).

### DT-02 · `isTradeResolving` con dos escritores

**Mismo archivo**, `:200` declaración, escrituras en `:615`, `:659`, `:1943`, `:2464`, `:2649`, `:2747`, `:2751`.
Ninguna ruta de envío debería tocar el flag de liquidación. Es la causa raíz de DT-01 y merece su propia iteración.

### DT-03 · `recompute_cubes.py` — lista de ventanas derivada, no manual (severidad MEDIA · **parcialmente resuelto aquí**)

`CUBO1_WINDOWS` ya es fuente única y la paridad se deriva de los artefactos. **Pendiente:** `CANONICAL_WINDOWS` sigue siendo una lista manual con el payout hardcodeado por ventana; debería derivarse de un `window_metadata.json` que declare su propio payout. Hoy añadir una ventana olvidando la lista **reproduce exactamente el bug de W19**. El guard del test 6 lo caza, pero el diseño sigue siendo frágil.

### DT-04 · `window_metadata.json` no declara cosas que el instrumento necesita

Falta el **payout resuelto** (hoy va hardcodeado por ventana en `recompute_cubes.py`) y el **nombre canónico** de la ventana. Sin eso, cada alta de ventana exige editar código.

### DT-05 · El monitor sigue leyendo el ledger, no el journal

`observed_trades` sale de eventos de `/status`, no del `trade_journal` del motor. Funciona, pero significa que **el monitor y el journal pueden discrepar** y nadie lo comprueba. Un cruce `len(observed_trades) == filas del journal` al cierre sería un guard barato.

---

## 8. RECOMENDACIÓN AL ORQUESTADOR

1. **W19 es válida.** Aceptar `N=4 (2W/2L, WR 50,00%)`, saldo final **38.827.735,04 COP DEMO**, gap **$0,00**. La limitation queda **explicada y acotada**, no pendiente.
2. **Aceptar el recálculo de cubos**, pero **corrigiendo la cifra de P&L** en los dos informes de auditoría: **+401.999,36** COP, no +402.000,64. El desvío de 1,28 COP está en el texto, no en el ledger.
3. **Reetiquetar la limitation como ALTA, no cosmética**, por el reset de `currentLossStreak` (§2.2). No cambia ninguna cifra de W19; cambia la prioridad de DT-01.
4. **No reabrir `app/` en esta fase.** DT-01 y DT-02 son iteración propia con su preregistro y su test de concurrencia.
5. **`app/` y `8cdb61f` permanecen congelados.** No se compiló, no se instaló, no se tocó.
6. Autorizado el Preflight de W20 con saldo base **38.827.735,04 COP DEMO**.

---

## 9. ANCLAS INEQUÍVOCAS

```
sha256sum scripts/validation/recompute_cubes.py              -> 0852d005b6c62d291ecaa008c1d5fd924952638636c922f89fccdff67416d1f4
sha256sum scripts/validation/live_monitor_r23_autonomous.py  -> 935c4f85c638a2a22aa8bec01df25e82de6e23317c05b561b03fc712b034c6a5

python scripts/validation/recompute_cubes.py --test           -> SUCCESS (6/6 PASS)
python scripts/validation/live_monitor_r23_autonomous.py --test -> RESULTADO: PASS (4/4 casos)
python scripts/validation/replay_v2_tick.py --self-test        -> RESULTADO: PASS (61 [OK])
python scripts/validation/test_replay_v2_guards.py             -> Ran 54 tests — OK
python scripts/validation/compare_live_replay_v2.py --self-test -> RESULTADO: PASS

git status --short -- app/                                     -> vacio
git diff --stat -- app/                                        -> vacio
ls -la app/build/outputs/apk/debug/app-debug.apk               -> 2026-10-18_20:59:21 (no recompilada)

Cubo 1 paridad: N=19, 17 matches, 2 div, 89.47%, FAIL
Cubo 2: N=48 (27W/20L/1V), WR 57.45%, +223,997.44 COP
Total:  N=59 (34W/24L/1V), WR 58.62%, +401,999.36 COP
```
