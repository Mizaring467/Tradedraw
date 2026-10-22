# INFORME FINAL DE CIERRE — LOOP R23K

**Ruta:** `workflows/rounds/R23K_FINAL_REPORT.md`
**Rama:** `r10-night` · **Build medida declarada:** `8cdb61f` (versionCode 19, `1.9-r23h-exhaustion`)
**HEAD del recálculo:** `190f596` · **Preregistro vinculante:** `workflows/PREREGISTRO_R23K.md`
**Fecha de cierre:** 2026-10-22
**Fuente única de cifras:** `workflows/r24_tasks/A1_result.md` y `loops/r24_verify/A1_cubes.json`
(recálculo independiente del VALIDADOR sobre `trades.csv` primarios, semilla bootstrap 20261019,
10.000 remuestreos). Ninguna cifra de este informe procede de los informes ventana por ventana.

---

## 1. VEREDICTO DE CIERRE — CUBO 2

> [!CAUTION] **El Cubo 2 es EXPLORATORIO, no confirmatorio.**
> Su preregistro (`38e54e8`, 2026-10-19 01:22:57) es **posterior** a W04 (`9442c1f`, 2026-10-18 20:08:26)
> y a W07 (`ab5ddcd`, 2026-10-18 21:46:41). El propio preregistro declara en su línea 16:
> «Cualquier modificación posterior a la primera ventana medida **invalida el ciclo completo**».
> Con su propia regla, 8 de 19 ventanas quedan fuera de cobertura preregistrada.
> Añadido por la auditoría independiente (`workflows/r24_tasks/AUDIT_A_result.md`, punto 13, FAIL).
> **Consecuencia:** el Cubo 2 no puede citarse como evidencia confirmatoria de nada — ni de edge
> ni de ausencia de edge. Lo que sí sobrevive es el argumento de **futilidad aritmética** (§5),
> que no depende del diseño muestral.

> SIN EVIDENCIA DE EDGE (WR cercano al breakeven; punto estimado negativo; N insuficiente para concluir)

El `verdict` textual emitido por `recompute_cubes.py` para el Cubo 2 es
`NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)`. **El texto es falso en su
primera condición** (N=60 no es < 40) por un defecto cosmético del ternario en
`recompute_cubes.py:652-661`. La conclusión de fondo —no hay edge demostrado— coincide con el
veredicto literal de cierre transcrito arriba.

---

## 2. CUBO 2 — DESEMPEÑO AGREGADO (W04–W22)

> [!WARNING] El Cubo 2 **NO es una muestra homogénea de un solo ejecutable.** W04–W06 corrieron en
> la build `aac1df3`; W07–W22 en la build `8cdb61f` (campo `gitSha` de cada `LIVE_REPORT.md`).
> Entre ambas cambiaron `TradingEngine.kt` (+62/−28) y `AdaptiveLearningEngine.kt` (+162/−68):
> refactor a funciones puras con constantes intactas (`ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f`,
> tolerancia S/R `±0.10f`, incrementos de `callPower` idénticos), pero **ejecutables distintos**.
> Las cifras siguientes son el agregado de las 19 ventanas; ver §8.1 y §9 para el alcance exacto.

| Métrica | Valor |
| :--- | :--- |
| N total | **60** |
| Resueltos | 59 |
| W / L / VOID | **30 / 29 / 1** |
| Win rate sobre resueltos | **50,85 %** |
| Wilson 95 % | **[38,44 % ; 63,16 %]** |
| Breakeven (payout 83 %) | **54,64 %** |
| EV en R | **−0,0710** [−0,3002 ; +0,1567] |
| EV en COP | **−7.116,67** [−30.033,15 ; +15.649,92] |
| P&L derivado de balances | **−427.000,32 COP** |
| Max drawdown | **1.260.999,68 COP** (3,1729 %) |

El límite inferior de Wilson (38,44 %) queda **16,20 pp por debajo** del breakeven. El punto
estimado (50,85 %) también está por debajo. El intervalo cruza el breakeven: la muestra no descarta
ni confirma ventaja.

### 2.1 Sensibilidades

| Sensibilidad | N | res | W / L | WR | Wilson 95 % | Δ COP |
| :--- | :-: | :-: | :-: | :-: | :--- | :--- |
| Cubo 2 sin W04 | 52 | 51 | 23 / 28 | **45,10 %** | [32,27 % ; 58,62 %] | **−900.999,68** |
| Solo W07+ (post-congelado) | 43 | 42 | 17 / 25 | **40,48 %** | [27,04 % ; 55,51 %] | **−1.094.999,04** |
| 2.ª mitad cronológica | 36 | 35 | 14 / 21 | **40,00 %** | [25,55 % ; 56,43 %] | **−944.000,00** |

Las tres sensibilidades **empeoran** el punto estimado respecto al cubo completo. Quitar la mejor
ventana (W04) baja el WR 5,75 pp. El criterio B2 del preregistro (exigir Wilson inferior > breakeven
también en «sin W04») falla en ambos escenarios.

### 2.2 Dirección

| Dirección | N | W / L | WR | Wilson 95 % | Δ COP |
| :--- | :-: | :-: | :-: | :--- | :--- |
| BUY | 33 | 16 / 17 | **48,48 %** | [32,50 % ; 64,78 %] | **−383.001,60** |
| SELL | 27 | 14 / 12 | **53,85 %** | [35,46 % ; 71,24 %] | **−43.998,72** |

Ninguna dirección supera el breakeven en punto estimado. BUY es la peor y concentra el 89,7 % de la
pérdida (−383.001,60 de −427.000,32 COP).

---

## 3. CUBO 1 — INTEGRIDAD Y PARIDAD (W07–W22)

| Métrica | Valor |
| :--- | :--- |
| N / resueltos | 43 / 42 (1 VOID) |
| W / L | 17 / 25 |
| WR | **40,48 %** [27,04 % ; 55,51 %] |
| P&L de balances | **−1.094.999,04 COP** |
| Pares formales replay | **22** (20 matches, 2 divergencias) |
| Tasa de match | **90,91 %** [72,19 % ; 97,47 %] → **PASS** (criterio R23J: ≥10 pares y ≥90 %) |
| **Cobertura real** | **22 / 80 = 27,50 %** (Live 43 ∪ Replay 59, intersección 22) |

**El PASS de paridad no es cobertura.** El criterio de R23J mide acuerdo sobre los pares que sí se
formaron; el 72,5 % restante de las decisiones Live∪Replay no tiene contraparte. El preregistro
(`§4.2`) establece que la cobertura **se reporta junto al veredicto pero no lo modifica**: el PASS
de v1 se mantiene y no se recalifica retroactivamente. El umbral `≥ 70 %` es el criterio **B5(iii)**,
fijado a priori para el **arnés v2 / Cubo R24**; con 27,50 % el arnés v1 queda muy por debajo de ese
estándar, lo que se traslada como requisito de diseño a R24, no como reprobación de v1.

### 3.1 Dirección (Cubo 1)

| Dirección | N | W / L | WR |
| :--- | :-: | :-: | :-: |
| BUY | 23 | 8 / 15 | **34,78 %** |
| SELL | 20 | 9 / 10 | **47,37 %** |

---

## 4. CUBO 3 Y CONSOLIDADO (INFORMATIVO)

| Cubo | N | W / L / V | WR | Wilson 95 % | Δ COP |
| :--- | :-: | :-: | :-: | :--- | :--- |
| Cubo 3 (W01–W03) | 11 | 7 / 4 / 0 | **63,64 %** | [35,38 % ; 84,83 %] | **+178.001,92** |
| **Total W01–W22** | **71** | 37 / 33 / 1 | **52,86 %** | [41,32 % ; 64,10 %] | **−248.998,40** |

El consolidado **no es evidencia**: mezcla cubos heterogéneos (§5.4 del preregistro lo prohíbe para
cualquier veredicto). Su breakeven propio es **54,73 %** (payout mixto 82/83 %), y el intervalo
inferior (41,32 %) queda **13,41 pp** por debajo. Se publica solo por trazabilidad contable.

---

## 5. INALCANZABILIDAD DEL CRITERIO CON N=80

Cálculo propio del orquestador, no del script. Con `W = 30` y 59 resueltos, subir a `N = 80` deja
**21 huecos** (80 − 59). Aunque los 21 fueran aciertos: `W = 51 / 80`, WR 63,75 %,
**Wilson inferior = 52,81 % < 54,64 %**. Para superar el breakeven con N=80 harían falta **53 wins**,
es decir 23 aciertos más sobre 21 huecos: **aritméticamente imposible**.

Sin tope de N y con racha perfecta hacen falta **25 aciertos consecutivos** (55/84 → Wilson inferior
54,83 %). El criterio B2 del preregistro es, en el estado actual del cubo, **inalcanzable**; no es
cuestión de seguir midiendo.

---

## 6. ESTADO POR FASE — W01 A W22

Cada fila declara la ventana, su cubo, los trades confirmados y **dónde está la evidencia
primaria**. Las cifras de W/L son las del recálculo de A1 (`workflows/r24_tasks/A1_result.md` §2),
no las de los informes ventana por ventana.

| Fase | Cubo | Ruta de evidencia primaria | Auditoría | N | W / L / V |
| :---: | :---: | :--- | :--- | :-: | :-: |
| W01 | 3 | `loops/r23h_validation/window_01/trades.csv` | `workflows/rounds/R23G_FINAL.md` | 4 | 3 / 1 / 0 |
| W02 | 3 | `loops/r23h_validation/window_02/trades.csv` | `workflows/rounds/R23G_FINAL.md` | 3 | 2 / 1 / 0 |
| W03 | 3 | `loops/r23h_validation/window_03/trades.csv` | `workflows/rounds/R23G_FINAL.md` | 4 | 2 / 2 / 0 |
| W04 | 2 | `loops/r23h_validation/window_04_clean/trades.csv` | `workflows/rounds/R23H_MASTER_FINAL_REPORT.md` | 8 | 7 / 1 / 0 |
| W05 | 2 | `loops/r23h_validation/window_05_clean/trades.csv` | `workflows/rounds/R23H_CLAUDE_PREFLIGHT_W05.md` | 6 | 4 / 2 / 0 |
| W06 | 2 | `loops/r23i_validation/window_06_clean/trades.csv` | `workflows/rounds/R23I_STATUS_REPORT.md` | 3 | 2 / 1 / 0 |
| W07 | 1 y 2 | `loops/r23j_validation/window_07_clean/trades.csv` | `workflows/rounds/R23J_CLAUDE_W07_AUDIT.md` | 3 | 2 / 1 / 0 |
| W08 | 1 y 2 | `loops/r23j_validation/window_08_clean/trades.csv` | `workflows/rounds/R23J_CLAUDE_W08_AUDIT.md` | 4 | 1 / 3 / 0 |
| W09 | 1 y 2 | `loops/r23j_validation/window_09_clean/trades.csv` | `workflows/rounds/R23J_CLAUDE_W09_AUDIT.md` | 2 | 1 / 1 / 0 |
| W10 | 1 y 2 | `loops/r23j_validation/window_10_clean/trades.csv` + trade frontera | `workflows/rounds/R23J_W10_BORDER_TRADE_RECONCILIATION.md` | 1 | 0 / 1 / 0 |
| W11 | 1 y 2 | `loops/r23j_validation/window_11_clean/trades.csv` | `workflows/rounds/R23J_CLAUDE_W11_AUDIT.md` | 1 | 1 / 0 / 0 |
| W12 | 1 y 2 | `loops/r23k_validation/window_12/trades.csv` | `workflows/rounds/R23K_CLAUDE_W12_AUDIT.md` | 1 | 0 / 1 / 0 |
| W13 | 1 y 2 | `loops/r23k_validation/window_13/trades.csv` | `workflows/rounds/R23K_CLAUDE_W13_AUDIT.md` | 5 | 2 / 3 / 0 |
| W14 | 1 y 2 | `loops/r23k_validation/window_14/trades.csv` | `workflows/rounds/R23K_CLAUDE_W14_AUDIT.md` | 1 | 0 / 1 / 0 |
| W15 | 1 y 2 | `loops/r23k_validation/window_15/trades.csv` (0 filas) | `workflows/rounds/R23K_CLAUDE_W15_AUDIT.md` | 0 | 0 / 0 / 0 |
| W16 | 1 y 2 | `loops/r23k_validation/window_16/trades.csv` | `workflows/rounds/R23K_CLAUDE_W16_AUDIT.md` | 3 | 1 / 1 / 1 |
| W17 | 1 y 2 | `loops/r23k_validation/window_17/trades.csv` | `workflows/rounds/R23K_CLAUDE_W17_AUDIT.md` | 1 | 1 / 0 / 0 |
| W18 | 1 y 2 | `loops/r23k_validation/window_18/trades.csv` | `workflows/rounds/R23K_CLAUDE_W18_AUDIT.md` | 5 | 3 / 2 / 0 |
| W19 | 1 y 2 | `loops/r23k_validation/window_19/trades.csv` | `workflows/rounds/R23K_CLAUDE_W19_AUDIT.md` + `R23K_OPENCODE_W19_CROSSCHECK.md` | 4 | 2 / 2 / 0 |
| W20 | 1 y 2 | `loops/r23k_validation/window_20/trades.csv` | `workflows/rounds/R23K_CLAUDE_W20_AUDIT.md` + `R23K_OPENCODE_W20_CROSSCHECK.md` | 6 | 2 / 4 / 0 |
| W21 | 1 y 2 | `loops/r23k_validation/window_21/trades.csv` | `workflows/rounds/R23K_CLAUDE_W21_AUDIT.md` + `R23K_OPENCODE_W21_CROSSCHECK.md` | 5 | 1 / 4 / 0 |
| W22 | 1 y 2 | `loops/r23k_validation/window_22/trades.csv` | `workflows/rounds/R23K_CLAUDE_W22_AUDIT.md` + `R23K_OPENCODE_W22_CROSSCHECK.md` | 1 | 0 / 1 / 0 |

Notas de la tabla:

- **W01–W03 no cuentan para N del Cubo 2** (Cubo 3 histórico, §6.1 del preregistro).
- **W10 aporta 1 trade al Cubo 2 vía el trade de frontera** despachado 7 s antes del fin nominal y
  liquidado fuera de la captura. Ver `R23J_W10_BORDER_TRADE_RECONCILIATION.md`.
- **W15 tiene 0 trades**: `trades.csv` con 0 filas, delta 0,00 COP. Se conserva en el cubo como
  ventana vacía, no se descarta.
- Los `LIVE_REPORT.md` de cada ventana están en la misma carpeta que su `trades.csv`.
- Bitácora cronológica completa de las 22 ventanas: `loops/R23K_PROGRESO.md`.

---

## 7. LO CORREGIDO DURANTE R23K

| Corrección | Qué estaba mal | Dónde queda registrado |
| :--- | :--- | :--- |
| **Incidente de agregación R23J-01** | La suma manual del texto de `R23J_CLAUDE_W11_AUDIT.md` contó 13 pérdidas donde había 14 (omitió el trade de frontera W10). Cubo 2 pasaba de N=27 a **N=28** y el WR de 66,67 % a **64,29 %**; el total W01–W11 de N=38 a **N=39** y el delta de +760.002,56 a **+660.001,28 COP**. | `workflows/rounds/R23K_CLAUDE_A2_A3_AUDIT.md` §1.1; enmiendas fechadas en `R23J_CLAUDE_W11_AUDIT.md`, `loops/R23J_PROGRESO.md`, `R23J_FINAL_REPORT.md` |
| **Afirmación causal retirada** | «31 vetos evitaron 3 pérdidas severas» no tenía contrafactual auditado. Los 31 eran micro-ticks de 4 señales únicas de vela. Retractado formalmente. | `R23K_CLAUDE_A2_A3_AUDIT.md` §1.1 y §2.1 |
| **Retorno acumulado corregido** | +1,95 % era el número publicado; el real sobre saldo inicial W01 es **+1,69 %**. | `R23K_CLAUDE_A2_A3_AUDIT.md` §1.1 |
| **Monitor v2 (gracia de liquidación)** | El Monitor v1 cerraba sin esperar la liquidación del trade despachado al borde de ventana: así se perdió el trade de frontera de W10. v2 ordena `DISABLED` al fin nominal, espera hasta 150 s y registra `grace_wait_ms`. | `scripts/validation/live_monitor_r23_autonomous.py`; `R23K_TASK_A5_HERMES.md`; `loops/R23K_PROGRESO.md` (fila A5) |
| **Recálculo determinista sustituye la suma manual** | Toda cifra de cubo se recalcula con `scripts/validation/recompute_cubes.py` desde `trades.csv`, semilla bootstrap 20261019, 10.000 remuestreos. | `workflows/rounds/R23K_CUBES_RECOMPUTED.md` / `.json`; `loops/r24_verify/A1_cubes.json` |
| **Arnés v2: memoria real y reloj de cooldown** | El replay v2 no cargaba `adaptive_learning_state.json` ni modelaba el reloj de liquidación, así que `ADAPTIVE_BLOCK` era imposible de reproducir (0 %). Se conectó la memoria real con filtro anti look-ahead y se inyectaron settlements del ledger. | `workflows/rounds/R23K_C4_PARIDAD_V2_INFORME.md` §5; `scripts/validation/replay_v2_tick.py` |
| **Paridad v2 declarada no concluyente** | El sweep W05–W11 se etiquetó explícitamente **retrospectivo y no independiente**; no se usó para declarar el arnés listo. | `R23K_C4_PARIDAD_V2_INFORME.md` §1 y §4 |
| **Cobertura separada del match rate** | El PASS de paridad (90,91 %) se reportaba sin decir que cubre 22 de 80 decisiones. Se añadió la métrica de cobertura como complemento que **no** recalifica el veredicto. | `workflows/PREREGISTRO_R23K.md` §4.2; §3 de este informe |
| **Trade fuera de ventana (R24)** | Trade `8226652e-6326-4c00-b805-7d79fba1c33a` despachado con `mode=AUTONOMOUS` reactivado desde la UI del overlay, fuera de toda ventana. No se borra del journal; se etiqueta FUERA DE VENTANA y no entra en ningún cubo. | `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md` |

---

## 8. RIESGOS RESIDUALES

1. **El Cubo 2 ES atribuible a la build `8cdb61f`, pero su linaje de artefactos de evidencia
   está incompleto en ese commit.** Dos hechos distintos que no deben confundirse:
   - **Atribución del comportamiento — VERIFICADA.** `git diff --stat 8cdb61f 190f596 -- app/`
     devuelve **0 archivos**: `app/` es **byte-idéntico** entre ambos commits (y también frente a
     `HEAD`). `versionCode = 19` y `versionName = "1.9-r23h-exhaustion"` son idénticos en los tres
     puntos. El ejecutable que produjo los trades es el de `8cdb61f`; las ventanas posteriores no
     cambiaron el código, solo añadieron documentación y evidencia.
   - **Linaje de artefactos — INCOMPLETO.** `git cat-file -e` confirma que
     `8cdb61f:loops/r23k_validation/` **no existe** en ese commit. La evidencia presente allí es
     W01–W06: `loops/r23h_validation/window_0{1,2,3,04_clean,05_clean}/trades.csv` y
     `loops/r23i_validation/window_06_clean/trades.csv`. `loops/r23j_validation/` contiene
     **únicamente** `EXCLUDED_ARTIFACTS_MANIFEST.json`, es decir W07–W11 tampoco están. Las ventanas
     W07–W22 entraron después (`ab5ddcd` W07, `16b2167` W11, `eaeff51` W12, …, `190f596` W22).
     Los `trades.csv` de W04/W05/W06 sí son idénticos en `8cdb61f` (8/7/3 filas de datos).
   **Consecuencia:** la atribución del **comportamiento** al ejecutable `8cdb61f` queda
   **verificada** por diff vacío en `app/`; lo que no se puede reconstruir desde `8cdb61f` solo es
   el **linaje de los artefactos** de W07–W22, que rompe la reproducibilidad documental desde ese
   commit, no la validez de los trades.
2. **Cobertura de paridad 27,50 %.** El 90,91 % de match se apoya en 22 pares de 80 decisiones. Un
   PASS con tres cuartas partes de la muestra sin contraparte no sostiene ninguna afirmación sobre
   fidelidad del replay.
3. **`mode=AUTONOMOUS` reactivable desde la UI del overlay** sin conocimiento del orquestador. El
   bridge solo puede apagar, no impedir el encendido. Ya produjo un trade fuera de ventana
   (−100.001,28 COP, `R24_RECONCILIACION_FUERA_DE_VENTANA.md`). Mitigación vigente: preflight
   `mode=DISABLED` antes de cada ventana y cierre por bridge.
4. **`verdict` del Cubo 2 con texto falso** (`N=60 < 40`). Cosmético, no altera cifras, pero
   cualquier consumidor automático del JSON hereda una condición de parada mal expresada.
5. **W15 con 0 trades** dentro del Cubo 1 y del Cubo 2. El «N por ventana» es muy desigual
   (W20 = 6, W15 = 0); el cubo no es una muestra homogénea en el tiempo.
6. **`cubo_1.n = 43` incluye 1 VOID**; el tamaño binomial real es 42. Leer N=43 como muestra
   binomial infla el denominador en 1.

---

## 9. NO VERIFICADO

1. **Linaje de artefactos de evidencia en `8cdb61f`.** NO VERIFICADO en cuanto a completitud: el
   commit no contiene `loops/r23k_validation/` ni las ventanas W07–W11; solo W01–W06. La
   **atribución del comportamiento** al ejecutable `8cdb61f` sí está verificada por diff vacío en
   `app/` (`git diff --stat 8cdb61f 190f596 -- app/` → 0 archivos, versionCode/versionName
   idénticos). Ver §8.1. Queda NO VERIFICADO únicamente que `/status.gitSha` se comprobase en cada
   ventana durante la captura: no se re-ejecutó el binario en este cierre.
2. **`verdict` del Cubo 2.** NO VERIFICADO en su literalidad: imprime «N=60 < 40», texto falso por
   el ternario de `recompute_cubes.py:652-661`. La conclusión de fondo sí es correcta.
3. **Emparejamiento Live ↔ Replay por identificador.** NO VERIFICADO: los objetos `replay` tienen
   `trade_id` vacío en **59/59** casos, y el `decision_id` difiere en **10 de los 22** pares. Tampoco
   coinciden `timestamp` ni `candle_second` (delta 1.288–5.916 ms, mediana 2.902,5 ms). El
   emparejamiento se apoya en **proximidad temporal**, no en ID. El conteo de pares es reproducible;
   la correspondencia individual no es auditable.
4. **Inmutabilidad de `app/` entre `8cdb61f` y `190f596`.** VERIFICADO en este cierre:
   `git diff --stat 8cdb61f 190f596 -- app/` → 0 archivos. `versionCode=19` y
   `versionName="1.9-r23h-exhaustion"` idénticos en ambos commits y en el árbol de trabajo.
5. **Edge sostenido.** NO VERIFICADO y, con este diseño, inalcanzable (§5).
6. **Paridad del arnés v2 (Cubo R24).** NO EVALUABLE: el arnés v2 está ciego sin
   `distanceToSupportRatio`/`distanceToResistanceRatio` exportados por el APK y sin volcado de
   `adaptive_learning_state.json` al cierre (`workflows/R24_MANIFEST.md` §6). R24 permanece
   **PREPARADO, NO VIGENTE**.
7. **Ejecución en dispositivo de este cierre.** No se ejecutó Gradle, ni ADB, ni ninguna ventana
   nueva. Este informe es exclusivamente de consolidación documental sobre datos ya capturados.

---

## CIERRE DEL CUBO 2

> **SIN EVIDENCIA DE EDGE (WR cercano al breakeven; punto estimado negativo; N insuficiente para concluir)**

El Cubo 2 se cierra con **N = 60** trades confirmados (W04–W22 + trade de frontera W10), de los
cuales 59 están resueltos y 1 es VOID. El trade fuera de ventana
`8226652e-6326-4c00-b805-7d79fba1c33a` **no** entra en el cubo
(ver [`workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md`](../R24_RECONCILIACION_FUERA_DE_VENTANA.md)).

Las cifras definitivas de cierre (WR 50,85 %, Wilson [38,44 % ; 63,16 %], EV −0,0710 R / −7.116,67 COP,
P&L −427.000,32 COP, Max DD 1.260.999,68 COP) proceden del recálculo del VALIDADOR en
`workflows/r24_tasks/A1_result.md` y `loops/r24_verify/A1_cubes.json`, y están transcritas en §2.
**No se adelantan aquí ni se recalculan por suma manual**: toda cifra del documento queda subordinada
a ese recálculo determinista (semilla bootstrap 20261019, 10.000 remuestreos).

**Condición de cierre aritmética ya establecida:** con W = 30 sobre 59 resueltos, el criterio
«Wilson inferior > breakeven con N = 80» exige 53 aciertos sobre 80, pero solo hay 21 huecos
disponibles (80 − 59). El criterio es **inalcanzable**; no se amplía el cubo (§5).

---

## 10. DECISIÓN DE CIERRE

- **Cubo 2:** cerrado con N=60. Veredicto: **SIN EVIDENCIA DE EDGE** (punto estimado negativo, IC
  cruza el breakeven, N insuficiente). No se amplía: el criterio es inalcanzable con la aritmética
  actual (§5).
- **Cubo 1:** paridad **PASS** bajo el criterio R23J (22 pares, 90,91 %), **cobertura 27,50 %**
  reportada como métrica complementaria. No se recalifica retroactivamente.
- **Cubo R24:** no se abre. Arnés v2 no congelable en su estado actual.
- **Ninguna cifra de este informe autoriza a escribir «edge», «certificado» ni «contundente»**
  (prohibición §2.4 del preregistro).

---

## 11. FUENTES

| Artefacto | Ruta |
| :--- | :--- |
| Recálculo independiente (informe) | `workflows/r24_tasks/A1_result.md` |
| Recálculo independiente (JSON) | `loops/r24_verify/A1_cubes.json` |
| Recálculo independiente (MD del script) | `loops/r24_verify/A1_cubes.md` |
| Verificación aritmética propia | `loops/r24_verify/a1_independent.py` |
| Preregistro vinculante | `workflows/PREREGISTRO_R23K.md` |
| Manifiesto del cubo siguiente | `workflows/R24_MANIFEST.md` |
| Reconciliación fuera de ventana | `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md` |
| Bitácora de ventanas | `loops/R23K_PROGRESO.md` |
