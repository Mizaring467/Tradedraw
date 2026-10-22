# PREREGISTRO R24 — VALIDACIÓN DE INSTRUMENTACIÓN, ESTRATEGIA CONGELADA

**Fecha de fijación:** 2026-10-22
**Rama:** `r10-night`
**Commit del repositorio al fijarse:** `96427d0`
**Build bajo medición:** `8cdb61f` (versionCode 19, versionName `1.9-r23h-exhaustion`)
**Redactado por:** WORKER (B0)
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

### 2.2 Cubo 1 — CERRADO

| Métrica | Valor |
| :--- | :--- |
| Pares formales evaluables | 22 |
| Tasa de match | 90,91 % (20/22) |
| Cobertura | 27,50 % (22/80) |

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

**Definición.** `Δ(totalWins + totalLosses)` leído de `/status` entre el inicio y el cierre de la
ventana == número de trades **resueltos** del ledger de esa misma ventana.

**Criterio.** Cero discrepancias en **TODAS** las ventanas. Cualquier discrepancia, de cualquier
magnitud y en cualquier ventana, hace el criterio `NO`. No hay tolerancia de ±1.

**Nota de alcance.** La corrección de idempotencia de liquidación (B1, §5.b) puede alterar
contadores. Se declara como cambio con **POSIBLE IMPACTO** en V1 y se reporta por separado el
efecto medido.

### V2 — S/R EXPORTADOS

**Definición.** Proporción de filas de `decisions.csv` que traen `distanceToSupportRatio` y
`distanceToResistanceRatio` con valor numérico, parseable y no vacío.

**Criterio.** `>= 99 %` de las filas. Se cuenta sobre el total de filas de la ventana, no sobre un
subconjunto filtrado.

### V3 — MEMORIA ADAPTATIVA

**Definición.** Volcado parseable de la memoria adaptativa al cierre de **CADA** ventana, con
conteo de firmas coherente: nº de firmas del volcado == nº declarado por `/status` o por el motor.

**Criterio.** Volcado presente, parseable y coherente en **todas** las ventanas. Un volcado ausente
o un conteo que no cuadra hace el criterio `NO`.

### V4 — NO_PROCESADA

**Definición.** Cada ocurrencia de decisión `NO_PROCESADA` registrada con causa clasificada, o
marcada explícitamente como `NO ATRIBUIDA`. Se registra además la **duración máxima** de la
condición.

**Criterio.** 100 % de las ocurrencias con causa asignada o marcadas `NO ATRIBUIDA`. Las `NO
ATRIBUIDA` no invalidan por sí solas, pero se reportan con su conteo y duración máxima. Ocurrencia
sin registro = `NO`.

### V5 — PARIDAD v2 (criterios B5 de R23K)

Se evalúa **UNA SOLA VEZ**, al final del Cubo R24. No se evalúa por ventana ni se re-ejecuta.

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
- Columnas S/R (`distanceToSupportRatio`, `distanceToResistanceRatio`) **al final** de
  `decisions.csv`.
- Volcado de memoria adaptativa al cierre de ventana.
- Diagnóstico de `NO_PROCESADA` (causa + duración).

Append-only: no se reordenan ni se renombran columnas existentes; no se altera semántica de
columnas previas.

**(b) Corrección de idempotencia de liquidación (B1).**
Declarada como cambio con **POSIBLE IMPACTO** en contadores (V1). Se mide y se reporta su efecto.

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

---

## 8. FIRMA

## FIRMA

`PENDIENTE: firma del AUDITOR (PASS-INDEPENDIENTE) antes de escribir código R24 (B1)`
