# PREREGISTRO R24 — VALIDACIÓN DE INSTRUMENTACIÓN, ESTRATEGIA CONGELADA

**Fecha de fijación:** 2026-10-22
**Rama:** `r10-night`
**Commit del repositorio al fijarse:** `96427d0` (el commit que introduce este documento es
posterior y solo toca `workflows/`; `app/` sigue byte-idéntico a `8cdb61f`)
**Build bajo medición:** `8cdb61f` (versionCode 19, versionName `1.9-r23h-exhaustion`)
**Redactado por:** WORKER (B0)
**Comparador congelado:** `scripts/validation/compare_live_replay.py`, SHA-256
`4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` — el mismo del
`PREREGISTRO_R23K.md` §1.1. Si el SHA en disco difiere al abrir la primera ventana, el cubo **no
arranca**.
**Estado:** VIGENTE — escrito ANTES de ejecutar cualquier ventana del Cubo R24

---

## 0. REGLA DE ORO

Este documento se fija **antes** de observar un solo dato nuevo de R24. Su función es impedir que
criterios, tolerancias o ventanas se ajusten a posteriori. Cualquier modificación posterior a la
primera ventana medida **invalida el ciclo completo** y debe declararse como tal, con el número de
ventanas afectadas.

---

## 1. OBJETIVO

> **VALIDAR INSTRUMENTACIÓN. NO demostrar edge.**

La estrategia se declara **CONGELADA** en la lógica de `8cdb61f` (versionCode 19). Este cubo **no
puede probar ni refutar edge**: su muestra no se dimensiona para eso y su criterio de éxito no mira
la win rate. Solo verifica que la telemetría **mide lo que dice medir**.

Cualquier reporte de R24 que presente WR, IC de Wilson o EV como hallazgo de edge está **fuera de
alcance** y se considera inválido. Esas cifras se publican, si se publican, con la etiqueta
`INFORMATIVO — FUERA DE ALCANCE DE R24`.

---

## 2. CONTEXTO HEREDADO

Cifras del **VALIDADOR** (recálculo independiente), fuente `loops/r24_verify/A1_cubes.json`.
Build `8cdb61f`, versionCode 19.

### 2.1 Cubo 2 — CERRADO

| Métrica | Valor |
| :--- | :--- |
| `N` | 60 |
| `W / L / VOID` | 30 / 29 / 1 |
| Win rate | 50,85 % (30/59) |
| IC Wilson 95 % | [38,44 ; 63,16] |
| Breakeven (payout 83 %) | 54,64 % |
| `EV` | −0,0710 R |
| Δ balances | −427.000,32 COP |

**Veredicto: SIN EVIDENCIA DE EDGE.** Cerrado por inalcanzabilidad matemática del criterio
preregistrado (enmienda de futilidad en `PREREGISTRO_R23K.md`), no por el resultado observado.

**Veredicto literal del VALIDADOR** (campo `cubes.cubo_2.verdict`, transcrito sin retocar):

> `NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)`

Ambas frases conviven: el VALIDADOR dice «no concluyente» porque su regla mecánica compara `N`
contra 40; el cierre del cubo se decide por la enmienda de futilidad. Ninguna de las dos es
«hay edge». R24 **no** reinterpreta este veredicto ni lo recalifica.

### 2.2 Cubo 1 — CERRADO

| Métrica | Valor | Fuente |
| :--- | :--- | :--- |
| Pares formales evaluables | 22 | `A1_cubes.json` → `cubes.cubo_1.replay_parity.formal_pairs` |
| Tasa de match | 90,91 % (20/22) | `A1_cubes.json` → `replay_parity.match_rate` |
| Cobertura | 27,50 % (22/80) | `PREREGISTRO_R23K.md` §A3.3 (el JSON **no** expone cobertura) |

PASS en el borde, sin regla de parada preregistrada, cobertura baja. **No se usa como evidencia de
validación de la estrategia** (enmienda de alcance en `PREREGISTRO_R23K.md`).

### 2.3 Separación de cubos

El **Cubo R24 es NUEVO y SEPARADO**, sobre SHA distinto. Queda **prohibido**:

- sumar trades del Cubo R24 al Cubo 2 para alcanzar cualquier `N`;
- mezclar Cubo 2, Cubo 1 o Cubo E (exploratorio) en un consolidado de R24;
- recalificar retroactivamente ventanas de cubos cerrados con los criterios de este documento.

Un reporte que mezcle cubos se considera inválido y se rehace.

---

## 3. CRITERIOS DE VALIDACIÓN V1–V5

Cada criterio se evalúa **por ventana** y **acumulado**, con resultado binario `SÍ` / `NO`.
El veredicto global de instrumentación es `SÍ` solo si **todos** los criterios aplicables dan `SÍ`
en **todas** las ventanas.

### V1 — CONTADORES

**Definición.** `Δ(risk.totalWins + risk.totalLosses)` leído de `/status` entre el inicio y el cierre
de la ventana == número de trades con **resultado derivado de la cadena de saldos** (`WIN` + `LOSS`,
excluyendo `VOID`) del ledger de esa misma ventana.

**Rutas exactas (verificadas en `8cdb61f`).**
- `/status` → `risk.totalWins`, `risk.totalLosses` (`TradeDrawHttpBridge.kt` §payload de riesgo).
  `risk.totalVoids` **no** se lee: no está en el payload.
- Ledger: `loops/<cubo_R24>/window_NN/trades.csv` (mismo esquema que el ya verificado en
  `loops/r23k_validation/window_NN/trades.csv`). **`trades.csv` NO tiene columna `result`.**
  Cabecera real: `trade_id,decision_id,timestamp_ms,asset,action,detail,balance_at_trade`.
- El resultado se **DERIVA** de la cadena de saldos en
  `scripts/validation/recompute_cubes.py:244-292`:
  - crédito `> 150.000 COP` dentro de `+35 s .. +120 s` post-despacho → `WIN` (líneas 266-269, 274-278);
  - movimiento de **`+100.000 COP`** (±5.000, devolución del stake) en esa misma ventana → `VOID`
    (líneas 270-272, 279-283; el `delta` es **positivo**, no un débito);
  - si no hay ninguno de los dos → **`LOSS` por defecto** (línea 285).
  Resuelto = `WIN` o `LOSS`.

**Criterio.** Cero discrepancias en **TODAS** las ventanas. Cualquier discrepancia, de cualquier
magnitud y en cualquier ventana, hace el criterio `NO`. No hay tolerancia de ±1.

**Regla de no-evaluabilidad (obligatoria).** V1 debe registrar explícitamente cuántos trades de la
ventana caen en la rama **`LOSS` por defecto sin evidencia de débito, crédito ni void**
(`recompute_cubes.py:284-292`; el caso sin evidencia es el `else` interno de las líneas 289-292:
`credit_step is None and void_step is None and debit_step is None`). **Un `LOSS` con `debit_step`
registrado NO cuenta**: ese es un resultado con evidencia. Si el conteo de trades sin evidencia es
`> 0` en cualquier ventana, V1 se reporta **`NO EVALUABLE`**, nunca `PASS`: el resultado del trade
es desconocido y la comparación contra los contadores de `/status` no mide lo que dice medir.

**Nota de alcance.** La corrección de idempotencia de liquidación (B1, §5.b) puede alterar
contadores. Se declara como cambio con **POSIBLE IMPACTO** en V1 y se reporta por separado el
efecto medido.

### V2 — S/R EXPORTADOS

**Definición.** Proporción de filas de `decisions.csv` que traen las dos columnas S/R con valor
numérico, parseable y no vacío.

**Nombres de columna.** Hoy `decisions.csv` **no tiene** columnas S/R (cabecera real en `8cdb61f`:
`decision_id,trade_id,timestamp,asset,price_at_signal,candle_second,market_regime,trend,action,score,action_plan,kind,detail,veto_reason`).
Las añade B1 (§5.a). Los nombres exactos se fijan **en el commit de B1** y se declaran en
`workflows/`; **no pueden cambiar** después de la primera ventana. Se adoptan
`distanceToSupportRatio` y `distanceToResistanceRatio` (los del `data class TradeContextSignature`
en `AdaptiveLearningEngine.kt`), en snake_case o camelCase según lo que B1 commitee, sin renombrar
en caliente.

**Criterio.** `>= 99 %` de las filas. Se cuenta sobre el total de filas de la ventana, no sobre un
subconjunto filtrado. Las filas anteriores a la primera ventana R24 no cuentan.

### V3 — MEMORIA ADAPTATIVA

**Definición.** Volcado parseable de la memoria adaptativa al cierre de **CADA** ventana, con
conteo de firmas coherente: nº de firmas del volcado == nº declarado por la fuente de referencia.

**Estado actual (verificado en `8cdb61f`).** `/status` **no** publica conteo de firmas: el payload
de `TradeDrawHttpBridge` no expone ningún campo `adaptive*`/`signature*`. La memoria vive en
`AdaptiveLearningEngine.lossSignatures` (tope 50, `ArrayList`, serializada por `saveState`).
Por tanto B1 (§5.a) debe publicar **ambos**: el volcado y el contador en `/status`. Si B1 no
publica el contador, V3 se reporta `NO EVALUABLE` — **no** se sustituye por una estimación.

**Criterio.** Volcado presente, parseable y coherente en **todas** las ventanas. Un volcado ausente
o un conteo que no cuadra hace el criterio `NO`.

### V4 — NO_PROCESADA

**Definición.** Cada ocurrencia de `RiskManager.OrderState.NO_PROCESADA` registrada con causa
clasificada, o marcada explícitamente como `NO ATRIBUIDA`. Se registra además la **duración
máxima** de la condición.

**Estado actual (verificado en `8cdb61f`).** La única causa emitida es el literal fijo
`"ventana agotada sin movimiento de saldo"` (`RiskManager.markOrderUnprocessed`). No hay
taxonomía ni duración. B1 (§5.a) añade el diagnóstico.

**Taxonomía de causas, cerrada a priori** (B1 debe emitir exactamente estas etiquetas; añadir una
nueva después de la primera ventana invalida el criterio):

| Etiqueta | Significado |
| :--- | :--- |
| `SIN_MOVIMIENTO_SALDO` | ventana agotada, el saldo no se movió (causa actual) |
| `TAP_NO_DESPACHADO` | no se despachó el gesto de accesibilidad |
| `TAP_RECHAZADO_BROKER` | el gesto se despachó y el broker no registró orden |
| `FEED_MUDO` | sin ticks frescos durante la ventana de envío |
| `NO ATRIBUIDA` | ocurrencia registrada sin causa determinable |

**Criterio.** 100 % de las ocurrencias con causa asignada o marcadas `NO ATRIBUIDA`. Las `NO
ATRIBUIDA` no invalidan por sí solas, pero se reportan con su conteo y duración máxima. Ocurrencia
sin registro = `NO`.

### V5 — PARIDAD v2 (criterios B5 de R23K)

Se evalúa **UNA SOLA VEZ**, al final del Cubo R24. No se evalúa por ventana ni se re-ejecuta.

**Arnés congelado.** `scripts/validation/compare_live_replay.py`, SHA-256
`4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` (verificado en disco al
redactar este documento). **Una sola tolerancia: `±6000 ms`**, la del comparador congelado. Se
prohíbe reevaluar con `3000 ms` o cualquier otro valor, y se prohíbe reportar la sensibilidad
como resultado de V5 — es exactamente el error del Cubo 2 (§6.2).

| # | Criterio | Umbral |
| :--- | :--- | :--- |
| (i) | Reproducción de **entradas** | `>= 90 %`, sobre `>= 20` trades Live, misma acción dentro de `±6 s` |
| (ii) | Reproducción del **TIPO de bloqueo** | `>= 90 %` sobre `>= 200` decisiones comunes |
| (iii) | **Cobertura** de decisiones Live con contraparte en Replay | `>= 70 %` |

Tipos de bloqueo distinguidos: `ADAPTIVE_BLOCK`, `VETO_BUY_EXHAUSTION_CLIMAX`, `V1`, `V2`,
`RISK_LIMIT`.

**Si no se alcanzan los mínimos de muestra** (`>= 20` trades Live, `>= 200` decisiones comunes):
veredicto `NO EVALUABLE`. **PROHIBIDO extender ventanas por resultado.**

---

## 4. REGLA DE PARADA

**Máximo 4 ventanas de validación R24**, fijado A PRIORI.

- No se para antes por resultado favorable.
- No se extiende por resultado desfavorable.
- No se reinicia el cubo tras una ventana mala.
- Si al llegar a 4 ventanas V5 no es evaluable, el veredicto es `NO EVALUABLE` y así se reporta.

---

## 5. CAMBIOS DE CÓDIGO PERMITIDOS EN EL APK R24 (LISTA CERRADA)

Solo estos dos. Nada más entra en el binario R24.

**(a) Telemetría append-only.**
- Columnas S/R **al final** de `decisions.csv` (nombres fijados en el commit de B1, §3 V2).
- Volcado de memoria adaptativa al cierre de ventana **y** contador de firmas expuesto en
  `/status` (requisito de V3, §3).
- Diagnóstico de `NO_PROCESADA`: causa según la taxonomía cerrada de §3 V4 + duración de la
  condición.

Append-only: no se reordenan ni se renombran columnas existentes; no se altera semántica de
columnas previas. Añadir campos nuevos al JSON de `/status` es aditivo y no rompe consumidores.

**(b) Corrección de idempotencia de liquidación (B1).**
Declarada como cambio con **POSIBLE IMPACTO** en contadores (V1). Se mide y se reporta su efecto.

**Requisito explícito de B1 — subconteo `LOSS` sin evidencia.** B1 debe publicar en `/status` un
campo nuevo, `risk.tradesLossSinEvidencia` (o nombre equivalente, fijado en el commit de B1 y
declarado en `workflows/`; no puede renombrarse después de la primera ventana), contador
**acumulado** de trades clasificados `LOSS` sin `credit_step`, sin `void_step` y sin `debit_step`
— exactamente la rama `else` interna de `recompute_cubes.py:289-292` (§3 V1).

Sin este campo, V1 **no puede distinguir «0 discrepancias» de «no medido»**: la ausencia de
discrepancias sería indistinguible de la ausencia de la medición que las detecta.

Se declara que este campo es **TELEMETRÍA** (append-only, solo lectura): no altera ninguna
decisión, no toca señales, vetos, umbrales, stakes ni `RiskManager` (§5, Estrategia CONGELADA).

### Estrategia CONGELADA

Prohibido tocar: señales, ponderaciones, vetos, umbrales (`callPower`, `ADAPTIVE_BLOCK`, ventana
sniper), stakes, `RiskManager`, `TradingEngine` en su lógica de decisión.

---

## 6. DEBILIDADES CONOCIDAS DEL DISEÑO

Se declaran, no se ocultan.

1. **Preregistro tardío del ciclo anterior.** El preregistro R23K (`38e54e8`, 19-oct 01:22:57) es
   **POSTERIOR** a W04–W11: **8 de 19** ventanas del Cubo 2 se capturaron antes de que existiera el
   preregistro.
2. **Comparador cambiado a mitad de cubo.** En el Cubo 2 el criterio de emparejamiento cambia a
   mitad: W04–W06 con comparador de **3000 ms**, W07–W22 con **6000 ms**. El artefacto de W06
   (2/1/1) **NO se reproduce** con el comparador congelado (2/2/0).
3. **Paridad dependiente del umbral.** La paridad del Cubo 1 depende del umbral: **68,75 % a
   3000 ms** vs **90,91 % a 6000 ms**.
4. **Dos binarios en un cubo.** El Cubo 2 mezcla dos binarios (`aac1df3` W04–W06, `8cdb61f`
   W07–W22), aunque con constantes de decisión idénticas (refactor puro verificado).
5. **El veredicto de cierre del Cubo 2 no es el literal del VALIDADOR.** El JSON dice
   `NO CONCLUYENTE (N=60 < 40 ...)`; el cierre por «SIN EVIDENCIA DE EDGE» viene de la enmienda de
   futilidad, no del script. Se declara para que nadie cite el JSON como si dijera lo segundo.
6. **Los mínimos de V5 pueden ser inalcanzables en 4 ventanas.** El Cubo 2 necesitó 19 ventanas
   para 60 trades; 4 ventanas difícilmente dan 20 trades Live y 200 decisiones comunes. El
   resultado más probable de V5 es `NO EVALUABLE` y **eso es un resultado válido**, no un fallo a
   corregir extendiendo el cubo. Se fija aquí para que no se "arregle" después.
7. **`LOSS` por defecto en el clasificador de resultados.** `scripts/validation/recompute_cubes.py:284-292`
   asigna `LOSS` cuando no encuentra crédito `> 150.000 COP` ni devolución de stake `+100.000 COP`
   en la ventana `+35 s .. +120 s` post-despacho. Un fallo de captura del saldo (frame perdido,
   `MediaProjection` caído, ventana de liquidación fuera de rango) se convierte en **pérdida**. Esto
   sesga el ledger hacia `LOSS` y hace que V1 compare contadores contra un resultado inventado.
   **Contramedida obligatoria:** R24 registra explícitamente cuántos trades caen en la rama
   «`LOSS` por defecto **sin evidencia alguna**» (`credit_step is None and void_step is None and
   debit_step is None`, líneas 289-292); si es `> 0`, V1 se reporta **`NO EVALUABLE`**, no `PASS`
   (§3 V1, regla de no-evaluabilidad).
8. **La ventana `+35 s .. +120 s` asume expiración de 60 s.** El clasificador busca la
   liquidación en esa ventana fija; un trade con **cualquier otra expiración** (30 s, 120 s, 300 s)
   cae **íntegro en `LOSS` por defecto**, sin evidencia, aunque haya ganado. R24 opera a **60 s**
   (estrategia congelada), así que el supuesto se cumple en este cubo, pero queda **declarado**:
   fuera de 60 s el clasificador no es válido y ninguna cifra derivada de él es interpretable.
9. **`recompute_cubes.py` no expone el subconteo `LOSS`-sin-evidencia.** El script clasifica pero
   **no publica** cuántos trades cayeron en la rama sin evidencia (§3 V1, §6.7). V1 **depende de
   instrumentarlo**: sin ese conteo no hay forma de separar «`LOSS` medido» de «`LOSS` inventado»,
   y por tanto V1 no es evaluable. El subconteo debe salir del APK R24 vía
   `risk.tradesLossSinEvidencia` (§5.b) y contrastarse contra el ledger; **no** se estima a mano ni
   se infiere del script congelado.

> **Cifras de §6 verificadas por el ORQUESTADOR** con ejecución propia; artefactos en
> `loops/r24_orch_verify/`. (3000 ms → 16 pares 68,75 %; 6000 ms → 22 pares 90,91 %; W06 fresh
> 2/2/0 vs commiteado 2/1/1; `aac1df3` W04–W06 leído de `LIVE_REPORT.md`.)

### Cómo R24 evita repetirlos

| Error heredado | Contramedida R24 |
| :--- | :--- |
| Preregistro posterior a las ventanas | Este documento se commitea **antes** de la primera ventana |
| Comparador cambiado a mitad de cubo | **Un solo comparador**, congelado antes de la primera ventana |
| Dos binarios en un cubo | **Un solo binario**: `8cdb61f` |
| Tolerancia elegida tras ver datos | Tolerancia **fijada a priori** (`±6 s`, §3 V5) |

---

## 7. PROHIBICIONES

1. Ajustar el arnés, las tolerancias o los criterios **después** de ver resultados.
2. Recalificar retroactivamente ventanas ya medidas con criterios nuevos.
3. Declarar edge con la WR puntual ignorando el intervalo de confianza.
4. Mezclar cubos (R24 con Cubo 2, Cubo 1 o Cubo E).
5. Extender ventanas para alcanzar los mínimos de muestra de V5.
6. Presentar cifras de resultado como hallazgo de edge dentro de R24.
7. Modificar la estrategia (§5, lista cerrada).
8. Reevaluar V5 con una tolerancia distinta de `±6000 ms`, o publicar una tabla de sensibilidad
   de tolerancias como si fuera resultado de V5.
9. Cambiar el SHA-256 del comparador o del build `8cdb61f` dentro del Cubo R24.

---

## FIRMA

`PENDIENTE: firma del AUDITOR (PASS-INDEPENDIENTE) antes de escribir código R24 (B1)`
