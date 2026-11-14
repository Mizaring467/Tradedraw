# PREREGISTRO R23J — CONGELACIÓN DE INFRAESTRUCTURA Y CRITERIOS DE VEREDICTO

**Fecha de fijación:** 2026-10-18
**Rama:** `r10-night`
**Commit base al momento del preregistro:** `aac1df3`
**Autor:** HERMES (`w1:pA` / Implementador)
**Estado:** VIGENTE — escrito ANTES de medir cualquier ventana R23J

---

## 0. PROPÓSITO Y REGLA DE ORO

Este documento se escribe **antes** de observar los resultados de cualquier ventana del ciclo R23J.
Su única función es impedir que los criterios de éxito se ajusten a posteriori para que los datos ya
obtenidos parezcan concluyentes. Los defectos metodológicos de R23I nacieron exactamente de eso: el
comparador se modificó **después** de ver que W06 daba 50% de paridad, y el reporte se emitió como si
el PASS fuera una propiedad del sistema y no de una herramienta recién ajustada.

**Si algo de este documento se cambia después de ver resultados, el ciclo entero queda invalidado**
y debe declararse como tal en el reporte de cierre.

---

## 1. COMPARADOR CONGELADO

| Campo | Valor |
|---|---|
| Ruta | `scripts/validation/compare_live_replay.py` |
| SHA-256 | `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F` |
| Verificación | `sha256sum scripts/validation/compare_live_replay.py` |

**Regla:** cualquier cambio posterior en ese archivo —una línea, un docstring, un caso de test—
**invalida todas las ventanas medidas con él**. Si hay que corregir el comparador, se abre un ciclo
nuevo (`R23K` o el que corresponda), se re-congela con un SHA nuevo y **se vuelven a medir todas las
ventanas desde cero**. No se permite mezclar ventanas medidas con SHA distintos en un mismo
consolidado.

**Alcance de la congelación.** El SHA cubre el archivo completo, incluido su `self_test`. Eso es
deliberado: el self-test es la puerta que impide que el comparador se vuelva permisivo sin que nadie
lo note, así que relajar un caso de test cuenta como cambiar el comparador.

**Nota de transparencia sobre el estado congelado.** El comparador que se congela aquí **incluye el
ajuste post hoc de R23I** (absorción de latencia en `candle_second`) y la tolerancia de 6000 ms. No es
la versión original. Por eso el veredicto de R23I sobre paridad se declara NO CONCLUYENTE y por eso
las ventanas de R23J son las primeras que se medirán con una herramienta fijada de antemano.

---

## 2. CRITERIO DE PASS DE PARIDAD LIVE ↔ REPLAY

### 2.1 Regla

Una ventana declara `PARIDAD = PASS` **si y solo si** se cumplen las dos condiciones:

1. **`pares_formales >= 10`** — la ventana aporta al menos 10 pares formalmente emparejados.
2. **`tasa_match >= 90%`** — de esos pares, al menos el 90% son `MATCH`.

### 2.2 Definición de «par formal»

Un par es formal cuando el comparador lo clasifica como `MATCH` o `DIVERGENT`, es decir, cuando ambas
órdenes existen y se emparejaron por:

- **`decision_id` exacto**, o
- **fallback temporal** con mismo `asset` y `|Δt| <= 6000 ms`.

Los estados `MISSING_LIVE` y `MISSING_REPLAY` **no son pares** y no entran en el denominador de la
tasa. Quedan reportados aparte como órdenes huérfanas.

### 2.3 Veredictos posibles

| Veredicto | Condición |
|---|---|
| `PASS` | `pares_formales >= 10` **y** `tasa_match >= 90%` |
| `FAIL` | `pares_formales >= 10` **y** `tasa_match < 90%` |
| `NO CONCLUYENTE` | `pares_formales < 10` |

**`NO CONCLUYENTE` no es un aprobado blando.** Es la declaración de que la ventana no tiene potencia
para decidir nada. Se puede acumular con otras ventanas del **mismo SHA** hasta llegar a 10 pares,
pero nunca se puede presentar una ventana de 2 pares como evidencia de paridad, que es exactamente el
error que produjo el `PASS` de W06 en R23I.

### 2.4 Advertencia sobre la métrica heredada

El comparador calcula `pass = tasa >= 90.0` **solo sobre los pares formados**, sin mirar cuántas
órdenes quedaron huérfanas. Una ventana con 2 pares perfectos y 5 huérfanas da `pass = True` con el
71% de las órdenes sin contraparte. **El veredicto de este preregistro NO es el campo `pass` del
JSON**: exige además `pares_formales >= 10` y el conteo de huérfanas se reporta siempre junto al
veredicto. No se modifica el comparador para corregir esto — está congelado (§1). La corrección se
aplica al leer el resultado, que es donde corresponde.

---

## 3. CRITERIO DE DESEMPEÑO DE LA ESTRATEGIA

Se evalúa en **Fase C**, sobre la muestra homogénea acumulada.

### 3.1 Regla

```
edge_sostenido  <=>  N_homogeneo >= 40  Y  limite_inferior_Wilson_95(WR) > breakeven

en cualquier otro caso:  DESEMPEÑO = NO CONCLUYENTE
```

### 3.2 Parámetros

| Parámetro | Valor | Origen |
|---|---|---|
| `N_homogeneo` mínimo | 40 | fijado aquí, antes de medir |
| Intervalo de confianza | Wilson, 95%, una cola inferior | método estándar para proporciones con N pequeño |
| `breakeven` | `1 / (1 + payout)` | payout 0.82 → 54.95%; payout 0.83 → 54.64% |
| Estadístico | límite **inferior** del IC, no la WR puntual | evita declarar edge por suerte de muestra |

### 3.3 Por qué el límite inferior y no la WR

Con N=28 y WR=71.43% (el consolidado de R23I), el intervalo de Wilson al 95% va aproximadamente de
53% a 84%. El límite inferior **queda por debajo del breakeven**, así que ese 71.43% **no** sostiene
un edge: es compatible con una estrategia sin ventaja. Ese es precisamente el error que la enmienda
R23J corrigió al declarar el desempeño NO CONCLUYENTE. Con `N >= 40` y el mismo WR, el intervalo se
estrecha y el límite inferior puede superar el breakeven; ahí sí habría algo que afirmar.

### 3.4 Homogeneidad

Cuentan solo ventanas que compartan: misma política de estrategia (veto `BUY_EXHAUSTION_CLIMAX`
activo), mismo comparador (§1) y ejecución en cuenta DEMO. Una ventana con política distinta **no se
suma**; se reporta aparte.

---

## 4. PROHIBICIONES

1. **Ajustar el comparador tras ver resultados de una ventana.** Invalida el ciclo (§1).
2. **Ajustar tolerancias** (`--temporal-tolerance-ms`, `--tolerance-price`, `--tolerance-dist`) tras
   ver resultados. Se fijan antes de la primera ventana y no se mueven.
3. **Ajustar umbrales de estrategia** — `callPower >= 70`, `ADAPTIVE_BLOCK 0.99f`, stake, payout,
   ventana sniper — para mejorar el número observado. La estrategia está congelada desde R23H.
4. **Presentar una ventana con `pares_formales < 10` como evidencia de paridad**, en cualquier forma:
   tabla, porcentaje, o frase del tipo «paridad perfecta».
5. **Declarar edge con la WR puntual** ignorando el intervalo de confianza.
6. **Mezclar en un consolidado ventanas medidas con SHA distintos del comparador.**

Cualquier violación se anota en el reporte de cierre del ciclo con la ventana afectada y el veredicto
que corresponda, sin excepción.

---

## 5. ESTADO DEL ARTE AL MOMENTO DE FIJAR ESTE PREREGISTRO

Datos ya conocidos antes de escribir este documento (declarados para que no parezcan descubrimientos
posteriores):

- W06_clean (R23I): 3 trades live, 4 replay, **2 pares formales**, 2 MATCH. Bajo el criterio de §2
  esto es **`NO CONCLUYENTE`** (2 < 10), no un PASS.
- Consolidado R23I: N=28 homogéneo, WR 71.43%, breakeven 54.80%. Bajo §3 es **`NO CONCLUYENTE`**
  (28 < 40 y el límite inferior de Wilson no supera el breakeven).
- Las modificaciones de infraestructura de R23H/R23I (arnés de replay, poda de ticks, tolerancia
  6000 ms, absorción de latencia) están implementadas y se congelan con este documento.

Ninguno de estos números se ha usado para calibrar los umbrales de §2 y §3: los umbrales (10 pares,
90%, N=40, Wilson 95%) se fijaron por criterio metodológico, no por conveniencia sobre esta muestra.

---

## 6. FIRMA Y VIGENCIA

Este preregistro entra en vigor al commitearse. Cualquier reporte de R23J debe citarlo y declarar
explícitamente, para cada ventana, los tres números: `pares_formales`, `tasa_match` y `órdenes
huérfanas`. Sin esos tres números juntos, un veredicto de paridad no es válido.

**SHA-256 del comparador en vigor:**
`4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`
