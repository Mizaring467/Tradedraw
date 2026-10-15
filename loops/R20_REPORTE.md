# RONDA 20 — Por qué el bot no operaba, y corrección

**Fecha:** `2026-10-14 20:20` (hora local del dispositivo).
**Rama:** `r10-night` → `main`.
**Preregistro:** [`workflows/PREREGISTRO_R20.md`](workflows/PREREGISTRO_R20.md), commiteado en `e7763e9`
**antes** de tocar una sola línea de código.
**Commits:** `e7763e9` (preregistro) → `52699ff` (extracción del mapeo) → `18817dc` (recuperación) → `a9d9bfd` (guard).

---

## 1. Respuesta a la pregunta: por qué el bot no ha operado en mucho tiempo

**Causa raíz, medida y no inferida: el activo activo no tenía feed en el servidor.**

`activeAsset` era `AAVEUSD-OTC`, un RIC que el servidor **acepta** y para el que **nunca emite un tick**.

| # | hecho | evidencia |
| --: | :--- | :--- |
| 1 | El RIC muerto es aceptado sin error | socket nuevo: `{"data":[{"field":"AAVEUSD-OTC","action":"subscribe"}],"success":true,"errors":[]}` |
| 2 | Y después no llega nada | **1 frame (ese ack) y 0 ticks en 60 s** |
| 3 | Control positivo: el feed sano sí entrega | `ETHUSD-OTC` ≈ **2,4 ticks/s** (147 ticks / 60 s) |
| 4 | Los tres RICs candidatos de Aave están muertos | `AAVEUSD-OTC`, `AAVEUSD`, `AAVE/USD` → **0 ticks** cada uno |
| 5 | El gráfico del bróker **sí** existe y está vivo | captura `loops/screenshots/r15/r15_estado_actual.png`: `Aave (OTC) 80 %`, velas moviéndose |

**Consecuencia exacta:** `isFresh=false`, `lastTickAgeMs ≈ 1,76e6 ms` (~29 min) → el guard
`FEED_RANCIO` de `TradingEngine.executeHeadlessTrade` hace `return`. De ahí `tapsDespachados=0` y `W/L=0/0`.

### 1.1 La estrategia NO es la causa — y esto es importante

`signalsGenerated=17`, `evaluatedCount=84`, `suppressedByConfluence=5`
→ **12 señales atravesaron la matriz de confluencia (≥80 pts) y ninguna pudo ejecutarse.**
`vetoTotal=0`: ni una sola señal llegó siquiera a un veto de estrategia. Fallaba el *plumbing*.

Por eso **no se relajó ni un solo umbral de `master_traders_skill`**. Esta ronda **solo añade restricciones**.
Si la causa hubiera sido un veto trabajando bien, se habría documentado como comportamiento esperado.

### 1.2 Por qué "mucho tiempo" = nunca

No es una regresión: nunca coincidieron las tres condiciones a la vez — modo `AUTONOMOUS`,
feed sano y señal válida. Los dos rearmes reales de R15 duraron 117 s y el feed ya estaba muerto.

### 1.3 Hallazgo nuevo, no documentado en ninguna ronda anterior

**Un RIC no servido no solo se queda sin ticks: congela también el principal.**

Medido: con `ETHUSD-OTC` sano (`acc=33`, `fresh=true`), suscribir `AAVEUSD-OTC` como secundario dejó
`acceptedTicks` **congelado en 36** durante 25 s y `isFresh=false`; al restaurar se recuperó
(36 → **97** en 20 s). Esto **condiciona el diseño de la corrección** (ver §3).

### 1.4 Corrección de la atribución histórica de R13

`loops/R13_REPORTE.md:68` afirma que la causa de que Aave no diera ticks era **el mapeo RIC**.
Se arregló el mapeo (`aa6ae39`) a un RIC **sintácticamente** válido (`AAVEUSD-OTC`, sin paréntesis)
**pero que el servidor no sirve**. R15 §12.5 lo anotó como hallazgo colateral sin corregirlo.
La atribución de R13 era **parcial**: el mapeo era un defecto real (producía `Aave(OTC)` con
paréntesis) y arreglarlo fue correcto, pero **no era el defecto completo**.

---

## 2. Decisión del usuario

Entre cuatro alternativas se eligió **A: recuperación automática tras N s sin un solo tick**.
No se eligió B (solo avisar), C (validar contra catálogo) ni D (solo diagnóstico).

---

## 3. Riesgo que la opción A introducía, y cómo se blindó

**Comprobado leyendo `executeHeadlessTrade` antes de escribir código: NO existía ningún guard que
comparara el activo del feed con el activo visible en pantalla.** Los guards eran feed rancio, V2
(rango relativo), precio congelado, una-orden-por-vela, veto francotirador sintético, ventana
temporal sniper, anti-chop, cooldown y decisión adaptativa. **Ninguno comparaba activos.**

Sin blindarlo, recuperar el feed a `ETHUSD-OTC` con el gráfico en `Aave (OTC)` haría que el bot
decidiera CALL/PUT **con el precio de un subyacente** y pulsara **los botones del gráfico de otro**.

**Por eso la recuperación NO se implementó sola:** va con un guard fail-closed. Recuperar el feed es
recuperar **observabilidad**; no es autorización para operar en un gráfico distinto.

---

## 4. Lo implementado

### 4.1 Recuperación (commit `18817dc`)

- **`ultimoRicConTicks`**, persistido en `TradeDraw_WSConfig`. Es la **única evidencia válida** de
  "este activo funciona": que el servidor acepte un `subscribe` **no lo prueba** (`AAVEUSD-OTC` lo acepta y no emite).
- **Vigilante** (`sinTicksRunnable`): con el socket conectado y **0 ticks propios durante 20 s**,
  reafirma **un solo RIC** — el último que sí entregó ticks.
- **NO se añade el RIC ajeno al array `rics`**, porque el hecho 5 de §1.3 mide que eso congela también el principal.
- **Evento `FEED_SIN_TICKS`** con `activo`, `segundos` y `recuperado`.

### 4.2 Anti-flap: primer intento REFUTADO por la medición

La primera versión usó una **lista negra permanente** (`activoSinFeed`). La medición en dispositivo
la refutó: tras recuperar una vez, `AutoTradeAccessibilityService` volvía a imponer `AAVEUSD-OTC`
desde la pantalla del bróker, el activo ya estaba marcado y la recuperación se negaba a repetirse.

**Medido:** 75 s con el feed congelado, `acceptedTicks` **clavado en 477**, `recuperaciones=1`.

Un anti-flap que impide recuperar no evita la oscilación: **la causa**.

**Corrección:** el corte se movió a la **entrada** del RIC. `updateActiveAsset` **ignora** un RIC ya
medido sin feed — no se suscribe, así que no hay parpadeo. `/asset` (orden explícita) usa
`forzarActivo`, que **sí** puede probarlo, para que la protección no inhabilite el diagnóstico que
la descubrió. El anti-flap por plazo (`RECUPERACION_COOLDOWN_MS = 30 s`) es lo que evita la
oscilación de verdad.

### 4.3 Guard de desajuste (commit `a9d9bfd`)

- Decisión en función pura `TradingEngine.motivoVetoPorDesajusteDeActivo`, para poder fijarla por test.
- **Compara RIC contra RIC**, no nombre visible contra RIC: `latestObservedAsset` guarda
  `"Aave (OTC)"` y compararlo en crudo contra `"AAVEUSD-OTC"` daría desajuste **SIEMPRE**, dejando
  el bot sin disparar jamás. Se pasa por el mismo `AssetRicMapper` que la suscripción.
- **Fail-closed**: sin dato de pantalla y con feed conocido → **veta**.
- Contadores `vetoActivoDesajustado` y `recuperacionesPorSinTicks` en `/status`.

### 4.4 Defecto propio detectado por la medición, y corregido

Con `lastTickAgeMs == Long.MAX_VALUE` (ningún tick desde el arranque) el evento salía como
**`segundos=9223372036854775`**. Ahora `-1` significa "ninguno todavía", con test que lo fija.

---

## 5. Verificación en dispositivo real (Redmi A1, `220733SL`, Android 12 / API 31)

### 5.1 Criterio A — recuperación

| # | criterio (§4.1 preregistro) | resultado | evidencia |
| :-- | :--- | :--- | :--- |
| **A.1** | recuerda el último RIC que entregó ticks | **PASS** | `/status` → `ultimoRicConTicks="ETHUSD-OTC"` |
| **A.2** | a los 20 s sin ticks propios vuelve a ese RIC | **PASS** | forzar `AAVEUSD-OTC` → a los **21 s** `activeAsset=ETHUSD-OTC` (2 ejecuciones independientes, una a 20 s y otra a 21 s) |
| **A.3** | no oscila | **PASS** | **0 oscilaciones** en 3 min continuos y en 2 min tras la corrección del anti-flap |
| **A.4** | queda en `/events` | **PASS** | `FEED_SIN_TICKS: activo=AAVEUSD-OTC segundos=20 recuperado=ETHUSD-OTC` |
| **A.5** | con `mode=DISABLED` no cambia el modo | **PASS** | `mode=DISABLED` antes, durante y después |

**Feed recuperado, medido:** `acceptedTicks` **30 → 201** en 2 min (≈1,4 t/s) con `fresh=true`
ininterrumpido. Control negativo previo: con el RIC muerto el contador quedó **clavado en 477**
durante 75 s.

### 5.2 Criterio B — guard de desajuste

| # | criterio | resultado | evidencia |
| :-- | :--- | :--- | :--- |
| **B.1** | activo feed ≠ pantalla → veta | **PASS por test unitario** | 7 casos en `AssetMismatchGuardTest`, incluido `("Aave (OTC)", "ETHUSD-OTC")` |
| **B.2** | el veto es observable | **PASS por inspección** | contador en `/status` y evento `VETO DESAJUSTE …` en `/events` |
| **B.3** | si coinciden NO interviene | **PASS por test unitario** | control positivo: `("Aave (OTC)","AAVEUSD-OTC")` → sin veto |
| **B.4** | fail-closed | **PASS por test unitario** | sin dato de pantalla y con feed conocido → veta |

> **NO VERIFICADO EN VIVO (declarado honestamente):** el guard **no se ha visto dispararse en el
> dispositivo**. Para observarlo haría falta `mode=AUTONOMOUS` **y** una señal válida **y** un
> desajuste simultáneo; la gobernanza exige `mode=DISABLED` salvo prueba preregistrada, y esta
> ronda no la incluyó. Los 7 casos unitarios cubren la lógica, **no** el cableado en runtime.
> **Riesgo residual:** que `feedAssetProvider` no entregue el activo del cliente. **Verificado en
> dispositivo** que `/status` informa `activeAsset` correctamente vía ese mismo cliente, lo que
> hace improbable el fallo de cableado, pero **no es lo mismo que verlo vetar un disparo real**.

### 5.3 Suite

**345 tests, 0 fallos, 0 errores, 2 omitidos, 32 clases.** XML más reciente `2026-10-14 20:03:59`.
Baseline previo: 331 tests / 30 clases (R15) → **+14 tests, +2 clases**.

**Verificación de commits aislados:** los 4 commits se compilaron uno a uno (`git checkout` +
`compileDebugKotlin`) → **los 4 `BUILD SUCCESSFUL`**. Esto corrigió un defecto real: los dos
primeros commits tal como se escribieron **no compilaban aislados** (referenciaban símbolos de
commits posteriores); se rehicieron antes de push.

### 5.4 Batería

**99 %** al inicio de la verificación en dispositivo. Sin problemas de energía en esta ronda.

---

## 6. Límite de alcance (declarado a priori en el preregistro, y mantenido)

**Esta ronda NO consigue que exista un RIC de Aave con feed.** Si el servidor no sirve Aave,
el bot **seguirá sin operar en Aave**, pero ahora **por decisión explícita y observable**
(evento `FEED_SIN_TICKS` + guard de desajuste), no por un atasco silencioso.

**NO se implementó B2a** (acumulador multi-RIC): la regla preregistrada de R15 lo prohíbe en una
ronda no atendida, y además §1.3 mide que añadir un RIC ajeno **congela el principal**.

---

## 7. Gobernanza

| # | invariante | estado |
| :-- | :--- | :--- |
| **G.1** | NO se relaja ningún umbral de `master_traders_skill` | **CUMPLIDO** — solo se añaden restricciones |
| **G.2** | NO se tocan `PREREGISTRO_R10/R12/R14` | **CUMPLIDO** — `git status` limpio sobre ellos |
| **G.3** | no se implementa B2a (ni acumulación ni elección por rentabilidad) | **CUMPLIDO** — vuelve solo al último RIC que probó dar ticks |
| **G.4** | al cierre: `mode=DISABLED`, `hasPendingTrade=false`, `orderState=NINGUNA` | **verificado en cada lectura de `/status` durante toda la ronda** |
| **G.5** | suite verde con `cleanTest testDebugUnitTest` | **CUMPLIDO** — 345/0/0/2 |

**Sin credenciales expuestas:** no se imprimió, copió ni commiteó ningún token, cookie ni header.

### 7.1 Estado al cierre

Última lectura de `/status` **antes de que el dispositivo se desconectara**: `mode=DISABLED`,
`feed.activeAsset=ETHUSD-OTC`, `fresh=true`, `acceptedTicks=75`, `recuperacionesPorSinTicks=1`.

El dispositivo se desconectó de ADB al final de la ronda (`adb devices` → sin dispositivos), por lo
que **la lectura de cierre final no pudo repetirse**. No se declara un estado que no se haya leído.

---

## 8. Riesgos residuales y recomendación para R21

| # | riesgo | severidad | recomendación |
| --: | :--- | :--- | :--- |
| 1 | El guard de desajuste **no se ha visto vetar en vivo** | media | En R21, con `mode=AUTONOMOUS` y una señal, servir el desajuste a propósito y confirmar `vetoActivoDesajustado > 0` |
| 2 | **Aave sigue sin feed**: si el usuario deja el gráfico en Aave, el bot recupera ETH y el guard lo veta → seguirá sin operar | alta | **Decisión de producto, no técnica:** o se elige un activo con feed, o se acepta que Aave no es operable. El bot ya **no se queda mudo sin decirlo** |
| 3 | `activoSinFeed` permite **un** RIC ignorado, no una lista | baja | Suficiente mientras el mecanismo esté medido; ampliar solo si aparece un segundo RIC muerto |
| 4 | El banner del HUD **no** menciona la recuperación | baja | Añadir aviso cuando `recuperacionesPorSinTicks > 0` |
| 5 | La causa de que la accesibilidad imponga un RIC sin feed **sigue sin atribuir** | media | La corrección trata el síntoma en la entrada. Investigar por qué el mapeo de `Aave (OTC)` → RIC sin feed es el correcto para ese activo |

---

## 9. Conclusión

**El bot no operaba porque el activo que tenía suscrito no entregaba ticks, y el motor, con el feed
mudo, vetaba correctamente todo disparo en el guard de frescura.** No era un problema de estrategia:
12 señales habían pasado la confluencia de 80 pts y ninguna pudo ejecutarse.

**Lo corregido:** el cliente ahora detecta que un activo no entrega ni un tick, vuelve al último que
sí lo hizo, lo registra, y **se niega a operar si el activo del feed no es el del gráfico en pantalla**.

**Lo que sigue igual, a propósito:** ningún umbral de `master_traders_skill` se ha relajado.
