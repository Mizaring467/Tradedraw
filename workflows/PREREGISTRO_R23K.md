# PREREGISTRO R23K — CUBO 2, CRITERIO DE EDGE Y ARNÉS v2

**Fecha de fijación:** 2026-10-19
**Rama:** `r10-night`
**Commit del repositorio al fijarse:** `8bf175c`
**Build bajo medición:** `8cdb61f` (versionCode 19)
**Redactado por:** HERMES (`w1:pA` / Implementador)
**Estado:** VIGENTE — escrito ANTES de ejecutar cualquier ventana nueva de R23K

---

## 0. REGLA DE ORO

Este documento se fija **antes** de observar los resultados de las ventanas de R23K. Su función es
impedir que los criterios de éxito se ajusten a posteriori. Cualquier modificación posterior a la
primera ventana medida **invalida el ciclo completo** y obliga a declararlo como tal en el reporte de
cierre, con el número de ventanas afectadas.

La lección viene de R23I: allí el comparador se modificó **después** de ver que W06 daba 50% de
paridad, y el reporte se emitió como si el PASS fuera propiedad del sistema y no de una herramienta
recién ajustada. Ese error costó dos ciclos de auditoría.

---

## 1. B1 — CONTINUIDAD DEL CUBO 2 Y ANÁLISIS CONFIRMATORIO ÚNICO

### 1.1 Definición del cubo

| Parámetro | Valor |
|---|---|
| **Cubo** | 2 (Homogéneo) |
| **Build inmutable** | `8cdb61f` — versionCode 19, versionName `1.9-r23h-exhaustion` |
| **Verificación de inmutabilidad** | `git diff --stat 8cdb61f HEAD -- app/` debe salir **vacío** |
| **Configuración** | `AUTONOMOUS` / `YOLO`, cooldown 8000 ms |
| **Activo** | `Z-CRY/IDX` (Crypto IDX) |
| **Cuenta** | DEMO |
| **Stake** | 100.000 COP |
| **Payout** | 0.83 (breakeven 54.64%) |
| **Dispositivo** | `UGU8KFUKM7E6CE8D`, broker `com.marketly.trading` en primer plano |
| **Comparador congelado** | `scripts/validation/compare_live_replay.py` — SHA-256 `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` |
| **Monitor** | v2 (gracia de liquidación + `DISABLED` al cierre nominal), commit `dcce480` |

**Nota sobre el build.** El repositorio está en `8bf175c`, posterior a `8cdb61f`, pero **ningún commit
intermedio toca `app/`**: son documentación, el Monitor v2 y el script contrafactual. El binario
instalado sigue siendo el código de `8cdb61f` y así se verifica en `/status`. Si en algún momento
aparece un cambio en `app/`, el Cubo 2 se cierra y nace un cubo nuevo.

### 1.2 Condición de parada

El cubo continúa hasta cumplir **la primera** de estas dos condiciones:

1. `N_homogéneo = 80` trades confirmados.
2. Agotamiento del límite de tiempo de la sesión (**T0+6h30 de captura efectiva**, corte duro a
   **T0+7h**).

### 1.3 Análisis confirmatorio ÚNICO

Se fija **un solo** análisis confirmatorio, y se ejecuta **solo** al alcanzar `N = 80`.

**Todos los análisis intermedios ventana por ventana son estrictamente informativos.** Se publican
para trazabilidad, y expresamente:

- **NO** autorizan decisión operativa alguna.
- **NO** autorizan detención anticipada.
- **NO** autorizan cambio de configuración, umbral o build.
- **NO** se acumulan como evidencia parcial hacia el veredicto. Ocho ventanas informativas no son
  ocho tests: son ocho vistazos a la misma muestra creciente.

### 1.4 Prohibición anti-p-hacking

Queda prohibido detener el bucle por conveniencia de resultado. En concreto:

- **No** se para al alcanzar un WR favorable.
- **No** se para al alcanzar un intervalo de Wilson que cruce el breakeven en la dirección deseada.
- **No** se para tras una racha de pérdidas "para no empeorar el número".
- **No** se reinicia el cubo tras una ventana mala.

Si el límite de tiempo se agota antes de `N=80`, el resultado se reporta como **`NO CONCLUYENTE
(Muestra insuficiente, N < 80)`** y **no** se reinterpreta como si hubiera alcanzado el umbral.

### 1.5 Punto de partida declarado

Datos conocidos **antes** de fijar este documento, para que no parezcan hallazgos posteriores:

| Concepto | Valor |
|---|---|
| Trades confirmados en Cubo 2 (W04–W11) | **29** |
| Ventanas ya capturadas | 8 (W04, W05, W06, W07, W08, W09, W10, W11) |
| Faltan para `N=80` | **51 trades** |
| Ritmo observado | 29 trades / 8 ventanas ≈ 3,6 por ventana |
| Ventanas estimadas restantes | ≈ **14** |

Si el ritmo se mantiene, `N=80` exige del orden de 22 ventanas totales. El tope de tiempo se fijó
sabiendo esto: **es posible que la sesión termine antes de llegar a 80**, y en ese caso el veredicto
es `NO CONCLUYENTE`, no un aprobado por proximidad.

---

## 2. B2 — CRITERIO VINCULANTE DE EDGE

### 2.1 Regla

Solo se declarará **`Edge Sostenido`** si se cumplen **simultáneamente** las dos condiciones:

```
(1) lim_inferior_Wilson95(WR) > breakeven   en el análisis confirmatorio (N = 80)
(2) lim_inferior_Wilson95(WR) > breakeven   en la sensibilidad "Cubo 2 sin W04"
```

Si las dos discrepan, **rige el criterio más conservador**: se toma el límite inferior **menor** de
los dos escenarios y se compara contra el breakeven. No se elige el escenario que conviene.

**Breakeven vigente:** `1 / (1 + 0.83) = 54.6448%`.

### 2.2 Por qué "sin W04"

W04 es la ventana con mejor resultado del cubo (8 trades, 7 wins, WR 87,50%). Excluirla es la prueba
de robustez estándar: si el edge desaparece al quitar la mejor ventana, no era un edge, era esa
ventana. La sensibilidad se declara **a priori**, no se elige después de ver los datos.

### 2.3 Exigencia aritmética que conviene conocer de antemano

Con `N = 80`, el límite inferior de Wilson al 95% **solo supera el 54,64% si `W >= 53`**, es decir
**WR >= 66,25%**. La tabla completa, calculada antes de medir:

| N | W mínimo | WR mínimo exigido |
|---|---|---|
| 20 | 16 | 80,00% |
| 29 | 22 | 75,86% |
| 40 | 29 | 72,50% |
| 60 | 41 | 68,33% |
| **80** | **53** | **66,25%** |
| 100 | 65 | 65,00% |
| 150 | 94 | 62,67% |
| 200 | 124 | 62,00% |

Esto se publica **antes** de medir para que nadie confunda un 60% con un edge: con N=80, un 60% tiene
su límite inferior en torno al 49%, holgadamente por debajo del breakeven.

### 2.4 Lenguaje prohibido con N < 80

Mientras `N_homogéneo < 80`, queda prohibido en cualquier reporte, tabla o mensaje:

- «certificado», «certificada», «certificadas»
- «contundente», «contundencia»
- «edge», «edge confirmado», «edge demostrado», «ventaja sostenida»

Se permite describir los números («WR 66,7% con N=3») siempre que se acompañen de la etiqueta
`INFORMATIVO — NO CONCLUYENTE (N < 80)`.

---

## 3. B3 — AMPLIACIÓN DEL TOPE DE VENTANAS

Se amplía el tope de R23J (8 ventanas) a: **sin tope de ventanas, únicamente tope de tiempo por
sesión** (T0+6h30 de captura efectiva, corte duro T0+7h).

**Motivo declarado a priori:** el límite de 8 ventanas produce `N ≈ 29`, y con esa muestra el
intervalo de Wilson mide ~35 puntos de ancho. Es aritméticamente incapaz de resolver la pregunta:
sirve para descartar catástrofes, no para detectar un edge de 10 puntos sobre el breakeven. El tope se
amplía por **potencia estadística insuficiente**, no porque el resultado de R23J haya sido
insatisfactorio.

**Riesgo asumido y declarado:** ampliar el número de ventanas aumenta la exposición a que el tope de
tiempo corte antes de `N=80`, produciendo otro `NO CONCLUYENTE`. Se acepta ese desenlace como válido
de antemano.

---

## 4. B4 — PARIDAD DEL ARNÉS v1 (INMUTABLE + COMPLEMENTO)

### 4.1 Criterio R23J, sin cambios

```
PARIDAD = PASS  <=>  pares_formales >= 10  Y  tasa_match >= 90%
```

Esto **no se altera retroactivamente**. Las ventanas ya medidas conservan el veredicto que se emitió
bajo esta regla. No se recalifican con criterios nuevos.

### 4.2 Métrica complementaria: Cobertura

```
Cobertura = Pares Formales / (Trades Live ∪ Trades Replay)
```

Es el número que el criterio de R23J deja fuera: el `pass` solo mira los pares formados, así que una
ventana con 2 pares perfectos y 5 órdenes huérfanas daba PASS con el 71% de las órdenes sin
contraparte.

**La Cobertura se reporta siempre junto al veredicto de paridad, pero no lo modifica.** Cambiar el
criterio ahora sería ajuste post hoc. Se incorpora como información para el ciclo siguiente.

### 4.3 Divergencias de ACCIÓN, segregadas

Se reporta por separado el conteo de pares donde la acción difiere (`Live BUY` vs `Replay SELL` o
viceversa). Una divergencia de acción no es un problema de emparejamiento: es una diferencia real en
lo que el motor decidió, y mezclarla con las divergencias de segundo (`candle_second`) oculta el
diagnóstico.

---

## 5. B5 — CRITERIOS A PRIORI PARA EL ARNÉS v2 (CUBO R24)

Estos criterios se fijan **antes de construir** el arnés v2, para que no se diseñe el arnés a la
medida del resultado que se quiera obtener.

### 5.1 Unidad de comparación

**Decisión tick a tick en ventana sniper.** No un trade agregado por vela: cada evaluación del motor
sobre cada tick dentro de `:00–:05` y `:58–:59`, con su resultado (entrada, bloqueo con tipo, o
abstención).

### 5.2 Los tres criterios

| # | Criterio | Umbral |
|---|---|---|
| (i) | Reproducción de **entradas** | `>= 90%`, sobre `>= 20` trades Live, misma acción dentro de `±6 s` |
| (ii) | Reproducción del **TIPO de bloqueo** | `>= 90%` sobre `>= 200` decisiones comunes |
| (iii) | **Cobertura** de decisiones Live con contraparte en Replay | `>= 70%` |

Tipos de bloqueo a distinguir: `ADAPTIVE_BLOCK`, `VETO_BUY_EXHAUSTION_CLIMAX`, `V1`, `V2`,
`RISK_LIMIT`.

**Por qué (ii) exige 200 y (i) solo 20.** Reproducir el tipo de bloqueo es una clasificación
multi-clase sobre eventos frecuentes (la mayoría de decisiones son bloqueos, no entradas): 200
decisiones comunes dan precisión suficiente para un 90%. Reproducir entradas es más exigente por
evento pero mucho más raro: 20 trades Live es lo que hay, y el umbral del 90% sobre 18/20 ya admite
dos fallos.

### 5.3 Etiquetado obligatorio de la reejecución retrospectiva

La reejecución sobre W05–W11 se etiqueta **obligatoriamente** como:

> **«retrospectiva y no independiente»**

Motivo: esas ventanas ya se usaron para construir la memoria de anti-patrones del teléfono y para
calibrar el propio arnés. Un arnés v2 que "reproduzca" W05–W11 está parcialmente ajustado a ellas.
La validación real del arnés v2 es su comportamiento en ventanas **posteriores** a su construcción.

### 5.4 Separación de cubos

Todo nuevo congelamiento —SHA del comparador v2 y SHA de una build nueva— **abre un Cubo R24
separado**. Jamás se mezcla con el Cubo 2 de `8cdb61f`:

- Los trades del Cubo 2 no entran en el consolidado de R24.
- Los trades de R24 no se suman al Cubo 2 para alcanzar `N=80`.
- Un reporte que mezcle cubos se considera inválido y se rehace.

---

## 6. B6 — IMPUTACIÓN DE VENTANAS Y MONITOR v2

### 6.1 Asignación de cubos

| Cubo | Ventanas | Estado | Uso |
|---|---|---|---|
| **Cubo 3 (Histórico)** | W01, W02, W03 | Cerrado | Referencia histórica. **No** cuenta para `N` |
| **Cubo 2 (Homogéneo)** | W04, W05, W06, W07, W08, W09, W10, W11 + nuevas de esta sesión | Abierto | Único que cuenta para `N=80` |

**Criterio de pertenencia:** un trade pertenece a la ventana donde se **despachó**, no donde liquidó.
Un trade despachado a `<= nominal_window_end_ts` y liquidado durante la gracia pertenece a esa
ventana.

### 6.2 Monitor v2

Activo **desde la primera ventana posterior al cierre de Fase A** (commit `dcce480`). Sus garantías:

- `DISABLED` ordenado al cierre nominal, antes de esperar.
- Gracia de liquidación con tope de 150 s.
- `grace_wait_ms` y `grace_resultado` en `window_metadata.json`.
- Nunca lanza excepción por bridge caído: reintentos acotados y salida segura.

Las ventanas W04–W11 se capturaron **antes** del Monitor v2. Se aceptan en el Cubo 2 porque comparten
build, configuración y estrategia, pero se declara la diferencia de arnés: el Monitor v1 no tenía
`grace_wait_ms` ni transición a `DISABLED`, así que su liquidación al borde de ventana es menos
confiable. Si una ventana vieja muestra trades sin resolver al cierre, se marca y no se descarta en
silencio.

### 6.3 Condición de arranque verificada

Antes de la primera ventana nueva se verifica, con medición y no de memoria:

| Requisito | Cómo se comprueba |
|---|---|
| `git diff --stat 8cdb61f HEAD -- app/` vacío | binario inmutable |
| `/status.gitSha == 8cdb61f` | coincide con el build |
| `/status.isDemoAccount == true` | cuenta DEMO |
| `/status.health.state == READY`, `reasons == []` | sistema operativo |
| `com.marketly.trading` en primer plano | el veto de pantalla no bloqueará |
| `/status.mode == AUTONOMOUS`, `submode == YOLO` | configuración congelada |
| `sha256sum compare_live_replay.py` == el SHA de §1.1 | comparador congelado |

**Estado al fijar este documento:** `/status` reporta `health = DEGRADED` con
`reasons = ["BALANCE_NOT_FRESH"]` — el bridge lleva minutos sin refrescar el saldo (la app estuvo
inactiva). **No es un fallo del build**: el feed está conectado y fresco. Se resuelve devolviendo el
broker al primer plano y refrescando antes de la primera captura. La primera ventana **no arranca**
hasta que `health == READY`.

---

## 7. TABLA DE PARADA Y VEREDICTOS POSIBLES

| Condición al cierre | Veredicto de edge |
|---|---|
| `N = 80` y IC inferior > breakeven en **ambos** escenarios | `Edge Sostenido` |
| `N = 80` y IC inferior > breakeven solo en uno | `NO CONCLUYENTE (discrepancia entre escenarios)` — rige el conservador |
| `N = 80` y IC inferior <= breakeven | `Sin edge demostrado` |
| `N < 80` por tope de tiempo | `NO CONCLUYENTE (Muestra insuficiente, N < 80)` |

Ninguna fila autoriza a escribir «certificado» ni «contundente».

---

## 8. PROHIBICIONES

1. Ajustar el comparador tras ver resultados de una ventana.
2. Ajustar tolerancias (`--temporal-tolerance-ms`, `--tolerance-price`, `--tolerance-dist`) a mitad
   de cubo.
3. Ajustar umbrales de estrategia — `callPower >= 70`, `ADAPTIVE_BLOCK 0.99f`, stake, payout,
   ventana sniper.
4. Parar el bucle por conveniencia de resultado (§1.4).
5. Declarar edge con la WR puntual ignorando el intervalo de confianza.
6. Mezclar cubos en un consolidado.
7. Presentar un análisis informativo como confirmatorio.
8. Recalificar retroactivamente ventanas ya medidas con criterios nuevos.

---

## 9. FIRMA Y VIGENCIA

Este preregistro entra en vigor al commitearse. Todo reporte de R23K debe citarlo y declarar, para
cada ventana: `N` acumulado del cubo, `W`, `L`, `VOID`, WR, IC de Wilson, veredicto de paridad
(`pares_formales`, `tasa_match`), **Cobertura** y conteo de divergencias de acción.

Sin esos números juntos, un veredicto no es válido.

**SHA-256 del comparador en vigor:**
`4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f`

**Build congelado:** `8cdb61f` (versionCode 19)

---

## ENMIENDA DE FUTILIDAD · 2026-10-22 · CIERRE DEL CUBO 2

> Enmienda **post hoc**. No modifica el cuerpo preregistrado (líneas 1-341). No altera ningún
> criterio, umbral, ventana, stake ni regla de decisión. Solo documenta por qué el criterio
> preregistrado del Cubo 2 es **matemáticamente inalcanzable** con los datos existentes, y por
> qué el cierre se decide por esa inalcanzabilidad y no por el resultado observado.

### A2.1 Cifras del script

Fuente: `loops/r24_verify/A1_cubes.json` (recálculo independiente del VALIDADOR, script
`loops/r24_verify/a1_independent.py`; no usa `recompute_cubes.py`). Build `8cdb61f`
(versionCode 19). `git diff --stat 8cdb61f 190f596 -- app/` devuelve **0 archivos cambiados**:
`app/` es byte-idéntico, por lo que el cubo **sí es atribuible** a la build `8cdb61f`. Lo que
faltaba en ese commit eran los artefactos de evidencia de W12-W22, no el ejecutable.

| Métrica | Valor |
| :--- | :--- |
| `N` | 60 |
| Resueltos (`W+L`) | 59 |
| `W / L / VOID` | 30 / 29 / 1 |
| Win rate | 50,85 % (30/59) |
| IC Wilson 95 % | [38,44 ; 63,16] |
| Breakeven (payout 83 %) | 54,64 % |
| `EV` | −0,0710 R, IC bootstrap 95 % [−0,3002 ; +0,1567] |
| P&L balances | −427.000,32 COP |
| Max drawdown | 1.260.999,68 COP (3,1729 %) |

> Nota sobre el breakeven: el `BE` preregistrado (54,6448 % = `1/(1+0,83)`) es el del payout
> dominante. El cubo mezcla payouts: 37 trades a 0,83 y 23 a 0,82 (BE 54,9451 %). Usar el `BE`
> único del preregistro es la lectura **favorable**; con la mezcla real el umbral efectivo es algo
> más alto, de modo que la inalcanzabilidad demostrada abajo se mantiene y se refuerza.

### A2.2 Demostración de inalcanzabilidad

Cálculo aritmético explícito sobre el estado congelado `W = 30`, `resueltos = 59`:

1. **Tope de `N = 80`.** El estado congelado es `N = 60` (59 resueltos + 1 `VOID`). Faltan
   `80 − 60 = 20` trades para llegar a `N = 80`. Aunque los 20 fueran aciertos, el máximo
   alcanzable es `30 + 20 = 50` wins sobre 79 resueltos.
2. **Wilson inferior en ese máximo.** `50/79 = 63,29 %` → límite inferior de Wilson 95 %
   = **52,28 %**, que es **menor** que el breakeven 54,64 %. El criterio preregistrado
   (`Wilson_inf > BE`) **no se cumple ni en el mejor caso posible**. Incluso en la contabilidad
   más generosa —contar el `VOID` como resuelto ganador y los 21 huecos como aciertos— el techo
   es `51/80 = 63,75 %` con `Wilson_inf = 52,81 %`, igualmente por debajo del breakeven.
3. **Wins necesarios con `N = 80`.** Para superar el breakeven por el límite inferior harían
   falta **53 wins** (`66,25 %`, `Wilson_inf = 55,36 %`). Son 23 wins sobre los 30 actuales, pero
   solo hay 20 huecos (21 en la contabilidad más generosa):
   **imposible por aritmética de huecos**, no por calidad de la estrategia.
4. **Sin tope de `N`.** Relajando el tope, harían falta **25 aciertos consecutivos**
   (`55/84`, `Wilson_inf = 54,83 %`) para cruzar el breakeven. La probabilidad de esa racha bajo
   la hipótesis nula `p = BE` es `0,5464^25 ≈ 2,7 × 10⁻⁷`.

Tabla `k → Wilson inferior` con `N = 80`:

| `k` (wins) | WR | Wilson inf 95 % | ¿`> BE` 54,64 %? |
| :--- | :--- | :--- | :--- |
| 50 | 62,50 % | 51,55 % | NO — techo real alcanzable (`50/79` → 52,28 %) |
| 51 | 63,75 % | 52,81 % | NO |
| 52 | 65,00 % | 54,08 % | NO |
| 53 | 66,25 % | 55,36 % | SÍ (inalcanzable: exige 23 wins sobre 30; solo hay 20 huecos) |

### A2.3 Veredicto de cierre

> **SIN EVIDENCIA DE EDGE (WR cercano al breakeven; punto estimado negativo; N insuficiente para concluir).**

### A2.4 Sensibilidades

| Escenario | `N` | `W / L` | WR | Wilson 95 % | Δ balances (COP) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Sin W04 | 52 | 23 / 28 | 45,10 % | [32,27 ; 58,62] | −900.999,68 |
| Solo W07+ | 43 | 17 / 25 | 40,48 % | [27,04 ; 55,51] | −1.094.999,04 |
| 2.ª mitad cronológica (ledger completo, `N = 71`) | 36 | 14 / 21 | 40,00 % | [25,55 ; 56,43] | −944.000,00 |
| 2.ª mitad cronológica (Cubo 2 estricto) | 30 | 12 / 17 | 41,38 % | [25,51 ; 59,26] | −708.999,68 |

Ninguna sensibilidad cruza el breakeven. Las cuatro empeoran el punto estimado respecto al
agregado (50,85 %), lo que descarta que el agregado esté sostenido por un subconjunto favorable.

> Nota de universo: la fila de `N = 36` se calcula sobre el ledger completo de 71 trades, que
> incluye W01-W03 (11 trades fuera del Cubo 2). Se conserva la cifra por trazabilidad con el
> encargo, pero la sensibilidad comparable al cubo es la de `N = 30`. Ambas quedan por debajo del
> breakeven, así que la conclusión no cambia con el universo elegido.

### A2.5 Desglose por dirección

| Dirección | `N` | `W / L` | WR | Wilson 95 % | Δ balances (COP) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUY | 33 | 16 / 17 | 48,48 % | [32,50 ; 64,78] | −383.001,60 |
| SELL | 27 | 14 / 12 | 53,85 % | [35,46 ; 71,24] | −43.998,72 |

Ambos lados por debajo del breakeven en punto estimado. Ningún lado alcanza significancia.

### A2.6 Declaración de no-retroactividad

> **NO se cambió ningún criterio retroactivamente. El cierre se decide por INALCANZABILIDAD
> MATEMÁTICA del criterio a N=80, no por el resultado observado.**

El criterio preregistrado (`Wilson_inf > BE`) permanece intacto y sin cumplir. Esta enmienda no
lo relaja ni lo sustituye: constata que ningún resultado futuro dentro del marco `N ≤ 80` podría
satisfacerlo, y por tanto el cubo se cierra como fútil en lugar de prolongarse hasta agotar
muestra. La estrategia permanece **CONGELADA**.

Esta enmienda introduce una vía de cierre no contemplada en el preregistro original (futilidad matemática). Se declara como debilidad del diseño preregistrado y NO como criterio nuevo aplicado retroactivamente.

---

## ENMIENDA CUBO 1 · 2026-10-22 · ALCANCE Y COBERTURA

> Enmienda **post hoc** de alcance. No modifica el cuerpo preregistrado (líneas 1-341). No
> reinterpreta el resultado de paridad: lo acota y le retira la condición de evidencia.

### A3.1 Veredicto

> **PASS en el borde, sin regla de parada preregistrada, cobertura baja.**

### A3.2 Cifras de paridad

| Métrica | Valor |
| :--- | :--- |
| Pares formales evaluables | 22 |
| `MATCH` | 20 |
| `DIVERGENT` | 2 |
| Tasa de match | 90,91 % |
| IC Wilson 95 % | [72,19 ; 97,47] |

### A3.3 Cobertura

**Cobertura: 22 / 80 = 27,50 %.**

Universo = Live 43 ∪ Replay 59, con intersección 22 (`43 + 59 − 22 = 80`). Los 80 pares del
universo se reparten en `MATCH` 20, `DIVERGENT` 2, `MISSING_REPLAY` 21 y `MISSING_LIVE` 37.

El 90,91 % mide **acuerdo sobre los pares que llegaron a formarse**, NO cobertura. Son dos
métricas distintas y no intercambiables: un 90,91 % de acuerdo sobre el 27,50 % del universo
deja el 72,50 % restante sin verificar. Presentar el 90,91 % como tasa de acierto del comparador
sobre el total es una lectura incorrecta del dato.

### A3.4 Declaración de alcance

> **Este cubo NO se usa como evidencia de validación de la estrategia.**

El PASS se limita a afirmar que el comparador, en los pares donde pudo emparejar, coincidió en
20 de 22 casos. No afirma nada sobre la calidad de las señales, ni sobre el edge, ni sobre el
resto del universo. La validación de la estrategia descansa exclusivamente en los cubos de
resultado (Cubo 2, Cubo 3), no en la paridad live/replay.

### A3.5 Nota de auditabilidad del emparejamiento

El emparejamiento **no se apoya en identificadores**, sino en proximidad temporal:

1. Los registros `replay` traen `trade_id` vacío en **59 / 59** filas del `replay_journal.csv`
   (el campo no existe como columna; 24 columnas, ninguna `trade_id`). En los 22 pares formales,
   `replay.trade_id` está vacío en **22 / 22**.
2. El `decision_id` es **distinto en 10 / 22 pares** (45,45 %), de modo que tampoco sirve como
   clave de unión.
3. En consecuencia el pareo se sostiene en cercanía de timestamp: delta absoluto entre 1.288 y
   5.916 ms, **mediana 2.902,5 ms** (media 2.974,1 ms, `n = 22`).

> Corrección de cifra: el valor 2.924 ms citado en el encargo es el **12.º valor ordenado** de la
> serie, no la mediana. La mediana de 22 observaciones es el promedio de los valores 11.º y 12.º
> (`(2.881 + 2.924) / 2 = 2.902,5 ms`). Se registra aquí el valor correcto.

Riesgo declarado: un emparejamiento por proximidad temporal sobre deltas de hasta ~5,9 s puede
unir decisiones distintas si el motor emite dos señales en la misma ventana. No se detectó ningún
caso así en la muestra (20 `MATCH` / 2 `DIVERGENT`), pero la cota superior del delta no permite
descartarlo por construcción. Esta es la razón adicional por la que el cubo no se eleva a
evidencia de validación.
