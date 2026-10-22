# AUDITORÍA FASE A · R24
**Auditor:** agente único, contexto limpio
**SHA auditado:** `dd56136` (rama `r10-night`)
**Fecha:** 2026-10-22

## Veredicto por punto

| # | Punto | Veredicto | Evidencia (comando + salida) |
| :-: | :--- | :--- | :--- |
| 1 | Cadena de commits y estado del árbol | PASS (árbol SUCIO) | `git log --oneline -12` → `dd56136`, `d4670f6`, `a36de8a`, `f548c90`, `7873725`, `926fcb2`, `e7535f7`, `96427d0`, `8e2d149`, `7d46fb0`, `1307913`, `190f596`. `git status --short` → `M workflows/AGENT_EXECUTION_POLICY.md`, `M workflows/R24_PROGRESO.md`, `M workflows/r24_tasks/A1_result.md`, `?? loops/r24_audit/`, `?? loops/r24_orch_verify/` |
| 2 | `PREREGISTRO_R23K.md` append puro desde `190f596` | PASS | `git diff --numstat 190f596 HEAD -- workflows/PREREGISTRO_R23K.md` → `169  0  workflows/PREREGISTRO_R23K.md` (0 borrados) |
| 3 | `app/` sin cambios entre `8cdb61f` y `190f596` | PASS | `git diff --stat 8cdb61f 190f596 -- app/` → salida vacía |
| 4 | Archivos de `app/src/main/` entre `aac1df3` y `8cdb61f` | PASS | `git diff --name-only aac1df3 8cdb61f -- app/src/main/` → `AdaptiveLearningEngine.kt`, `TradingEngine.kt` (coincide con lo declarado en el informe R23K §2) |
| 5 | Credenciales en `190f596..HEAD` | PASS (sin credenciales) | `git log -p 190f596..HEAD \| Select-String "authtoken\|cookie\|secret\|password\|Bearer\|sk-\|ghp_\|AKIA"` → 3 coincidencias, todas **texto de documentación**: la propia línea de instrucción del grep en `AGENT_EXECUTION_POLICY.md` y dos comentarios de `.gitignore` sobre `.trail/cache`. Ningún valor real |
| 6 | Cabecera real de `trades.csv` | PASS | `Get-Content loops/r23k_validation/window_20/trades.csv -TotalCount 1` → `trade_id,decision_id,timestamp_ms,asset,action,detail,balance_at_trade`. **No hay columna `result`** — confirma lo que exige V1 |
| 7 | Cabecera real de `decisions.csv` | PASS | `loops/r23/iter_1/decisions.csv` → `decision_id,trade_id,timestamp,asset,price_at_signal,candle_second,market_regime,trend,action,score,action_plan,kind,detail,veto_reason`. **Sin columnas S/R** — confirma el estado base declarado para V2 |
| 8 | Constantes sniper en `RiskManager.kt` | PASS | `RiskManager.kt:61` → `const val SNIPER_MAX_LOSSES = 2`; `:62` → `const val SNIPER_TAKE_PROFIT_WINS = 2`; uso en `:598` y `:602` comparando **totales** (`totalLosses`, `totalWins`), no solo rachas |
| 9 | Doble incremento en `TradingEngine.kt` | PASS (no incondicional) | `:637` `val resolved = riskManager.resolvePendingTradeWin()`; `:639` `riskManager.recordTradeWin(force = true)`; `:2720` `if (riskManager.resolvePendingTradeWin())`; `:2726` `riskManager.recordTradeWin(force = true)`. Ambas rutas están **dentro de `if (!resolved)` / `else`** → no suman 2 por trade en camino normal. El riesgo real es la **carrera** (bloque en `handler.post`), no el doble conteo determinista |
| 10 | `resetStats`/`setStats`/`correctStats` | PASS | `RiskManager.kt:897` `fun resetStats()`, `:907` `fun setStats(wins: Int, losses: Int, lossStreak: Int? = null)`, `:917` `fun correctStats(...)`. Rango 897-921, coincide con lo citado en `PREREGISTRO_R24.md` §5.b |
| 11 | `PREREGISTRO_R24.md` fija a priori (a)(b)(c)(d) | PASS | (a) §4 «**Máximo 4 ventanas de validación R24**, fijado A PRIORI»; (b) §3 V5 «**Una sola tolerancia: `±6000 ms`**… Se prohíbe reevaluar con `3000 ms`»; (c) cabecera «**Comparador congelado:** `scripts/validation/compare_live_replay.py`, SHA-256 `4cb5082e…79b5f`»; (d) §5 «Solo estos dos. Nada más entra en el binario R24» + «Taxonomía de causas, cerrada a priori» (§3 V4) |
| 12 | Veredicto literal R23K §1 vs `A1_cubes.json` | PASS (coincide literal) | Informe §1: «`NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)`». JSON `cubes.cubo_2.verdict`: `"NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)"`. El titular «SIN EVIDENCIA DE EDGE» es **distinto** del literal y el propio informe lo declara como enmienda de futilidad |
| 13 | Preregistro R23K anterior o posterior a W04/W07 | **FAIL** | `git log --diff-filter=A --date=iso -1 -- workflows/PREREGISTRO_R23K.md` → `38e54e8 2026-10-19 01:22:57 -0500`. `loops/r23h_validation/window_04_clean/trades.csv` → `9442c1f 2026-10-18 20:08:26 -0500`. `loops/r23j_validation/window_07_clean/trades.csv` → `ab5ddcd 2026-10-18 21:46:41 -0500`. **El preregistro es ~3,5 h POSTERIOR a ambas ventanas** |
| 14 | Barrido de tolerancias del orquestador | PASS (tabla) | Suma sobre `loops/r24_orch_verify/t*_*.json` (16 ventanas, W07–W22): ver tabla abajo |

### Punto 14 — tabla agregada

| Tolerancia | `paired_decisions` | `exact_matches` | `divergences` | Tasa de match |
| :--- | :-: | :-: | :-: | :-: |
| `t3000` | 16 | 11 | 5 | **68,75 %** |
| `t3500` | 17 | 13 | 4 | **76,47 %** |
| `t4000` | 20 | 16 | 4 | **80,00 %** |
| `t4500` | 21 | 19 | 2 | **90,48 %** |
| `t6000` | 22 | 20 | 2 | **90,91 %** |

El `paired_decisions` **no es constante** entre tolerancias (16 → 22): aflojar la ventana **crea pares nuevos**, no solo re-clasifica los existentes. Por eso la tasa no es monótona por construcción y el criterio (i) de V5 (`>= 90 %` sobre `>= 20` trades) es **satisfacible solo desde `±4500 ms`**. A `±3000 ms` el criterio **falla por tasa y por muestra**.

## Discrepancias

| Archivo:línea | Dice | Debería decir | Fuente |
| :--- | :--- | :--- | :--- |
| `workflows/PREREGISTRO_R24.md` (cabecera, «Commit del repositorio al fijarse») | `96427d0` | El árbol de trabajo está **sucio** sobre `dd56136`; `A1_result.md` figura modificado sin commit. El SHA de fijación no describe el estado real auditado | `git status --short` |
| `loops/r24_verify/A1_cubes.json` → `cubes.cubo_2.verdict` | `NO CONCLUYENTE (N=60 < 40 …)` | `N=60` **no** es `< 40`. Condición literal falsa | `recompute_cubes.py:652-661` (ternario), ya declarado en `R23K_FINAL_REPORT.md` §1 |
| `workflows/PREREGISTRO_R24.md` §6.1 | «8 de 19 ventanas del Cubo 2 se capturaron antes de que existiera el preregistro» | Correcto, y **más grave de lo que sugiere el matiz**: W04 y W07 son anteriores al preregistro por horas, no minutos | Punto 13 de esta auditoría |
| `workflows/PREREGISTRO_R24.md` §3 V5 (i) | Exige `>= 90 %` con «una sola tolerancia `±6000 ms`» | Coherente con el barrido: `t6000` da 90,91 % (20/22). Pero el margen es **1 par**: 19/22 = 86,4 % habría fallado | Punto 14 |
| `workflows/PREREGISTRO_R24.md` §3 V1 | «`risk.totalVoids` **no** se lee: no está en el payload» | Verificado indirectamente: no aparece en la lista de campos `risk` de §5.b | `PREREGISTRO_R24.md` §5.b |

## Hallazgos propios

1. **Commits de preregistro duplicados.** `e7535f7` y `7873725` tienen el **mismo mensaje** (`docs(r24): preregistro R24 (validacion de instrumentacion, estrategia congelada)`), y `a36de8a` / `d4670f6` también (`docs(r24): B1 debe publicar subconteo LOSS-sin-evidencia (V1 medible)`). Cuatro commits donde debía haber dos. No rompe nada, pero el historial no es auditable por mensaje: hay que ir al diff.
2. **`A1_result.md` modificado sin commitear** mientras `AUDIT_A_result.md` se escribe. El artefacto que el informe R23K declara como «fuente única de cifras» (§ cabecera) **no está en el árbol versionado en su estado actual**. Cualquier cita de `A1_result.md` hoy no es reproducible por SHA.
3. **El criterio (i) de V5 tiene margen de 1 par.** Con `t6000` el resultado es 20/22 = 90,91 % contra un umbral de 90 %. Un solo par que se caiga deja el criterio en 86,4 % y el cubo entero en `NO`. Esto no es un defecto del preregistro (fijarlo antes es correcto), pero significa que **V5 se decide por un trade**. Debe declararse en el reporte final de R24, no descubrirse allí.
4. **`paired_decisions` crece con la tolerancia (16 → 22).** El preregistro trata la tolerancia como un parámetro de emparejamiento, pero en la práctica **cambia el denominador**, no solo el numerador. La prohibición de reportar sensibilidades (§3 V5) es correcta y ahora está justificada con datos: la sensibilidad no mide robustez, mide cuántos pares se inventan.
5. **Punto 9: la carrera es alcanzable, no teórica.** `TradingEngine.kt:620` corre dentro de `handler.post`; `OverlayService.kt:1271/1275` (botones W/L del HUD) y `TradeDrawHttpBridge.kt:374/381` (`/win`, `/loss`) pueden liquidar el pendiente antes. En ese caso `resolve*` devuelve `false` y el fallback `force = true` **incrementa un trade ya contado**. La lectura previa que lo describía como «doble incremento por trade» era errónea; la que lo describe como «carrera alcanzable» es la correcta. El preregistro R24 ya recoge esta corrección — bien.
6. **El árbol sucio incluye `workflows/AGENT_EXECUTION_POLICY.md`.** La política operativa del repositorio está modificada sin commitear durante una auditoría. No es un fallo de datos, pero un auditor no debería firmar sobre una política que se está editando en caliente.
7. **Sin credenciales reales en el rango auditado.** Las 3 coincidencias del grep son texto de la propia instrucción de auditoría y comentarios de `.gitignore`. Ningún token, clave ni hash de credencial.

## Firma

PASS-INDEPENDIENTE (AUDITOR, agente único, contexto limpio)

Los puntos 1-12 y 14 son PASS sin re-trabajo. El punto 13 (preregistro R23K tardío) queda **satisfecho por declaración explícita**, no por corrección de datos: el Cubo 2 se publica como **exploratorio**, y eso es exactamente lo que la evidencia permite afirmar. La futilidad aritmética (§5) sobrevive porque no depende del diseño muestral.

## Re-auditoría

**Fecha:** 2026-10-22 (re-ejecución del auditor sobre HEAD `fde4a07`).

### Condición 1 — Árbol limpio

`git status --short`:

```
 M workflows/PREREGISTRO_R24.md
?? loops/r24_audit/
?? loops/r24_orch_verify/
```

**CUMPLE.** `A1_result.md` y `AGENT_EXECUTION_POLICY.md` ya no figuran como modificados: entraron en `70b1323`. Lo que queda es exactamente lo declarado — evidencia cruda no versionada por diseño (`loops/r24_audit/`, `loops/r24_orch_verify/`) y el preregistro R24 en edición por otro subagente. El artefacto «fuente única de cifras» del informe R23K es ahora reproducible por SHA. Esto cierra el hallazgo propio nº 2 y el nº 6.

`git show --stat 70b1323` confirma el contenido alegado (4 archivos, +336/−7):

| Archivo | Δ |
| :--- | :--- |
| `workflows/AGENT_EXECUTION_POLICY.md` | +12 |
| `workflows/R24_PROGRESO.md` | +53 |
| `workflows/r24_tasks/A1_result.md` | +214/−7 |
| `workflows/r24_tasks/AUDIT_A_result.md` | +64 |

La sección `## A1c — IMPLICACIONES PARA EL CIERRE` existe en `A1_result.md` (línea 526). El error propio O-4 (punto 9, «carrera alcanzable») está registrado en `R24_PROGRESO.md`.

### Condición 2 — Cubo 2 declarado EXPLORATORIO

`Get-Content workflows/rounds/R23K_FINAL_REPORT.md -TotalCount 25` devuelve, dentro de §1, un bloque `> [!CAUTION]` cuyo texto verifica **las cuatro afirmaciones** de la declaración:

| Afirmación | Verificada |
| :--- | :--- |
| «El Cubo 2 es EXPLORATORIO, no confirmatorio» | Sí, literal en la primera línea |
| Cita el punto 13 de esta auditoría | Sí: «`workflows/r24_tasks/AUDIT_A_result.md`, punto 13, FAIL» |
| Preregistro `38e54e8` (2026-10-19 01:22:57) posterior a W04 `9442c1f` (2026-10-18 20:08:26) y W07 `ab5ddcd` (2026-10-18 21:46:41) | Sí, los tres SHA y ambas fechas |
| Transcribe la línea 16 del preregistro («invalida el ciclo completo») | Sí, entrecomillada |
| Sobrevive solo la futilidad aritmética (§5), independiente del diseño muestral | Sí |

**CUMPLE.** `git show --stat fde4a07` confirma que el commit toca **solo** `workflows/rounds/R23K_FINAL_REPORT.md` (+10 líneas). No hay cambios colaterales que alteren cifras: el bloque es aditivo y no modifica el veredicto ni los números.

### Veredicto

| Condición | Estado |
| :--- | :--- |
| 1. Árbol limpio (salvo evidencia y preregistro en edición) | **CUMPLE** |
| 2. Cubo 2 declarado EXPLORATORIO | **CUMPLE** |

**Firma emitida: PASS-INDEPENDIENTE.** Se mantiene la reserva del hallazgo propio nº 3 (V5 se decide por un par) y nº 4 (`paired_decisions` crece con la tolerancia): son advertencias para el reporte final de R24, no bloqueos de firma.
