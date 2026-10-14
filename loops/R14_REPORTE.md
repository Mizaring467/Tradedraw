# Ronda 14 — Informe

**Tarea:** frente A (TIE y latencia) + frente B (sugeridor de "mercado inoperable").
**Modo:** desatendido — el usuario no estaba disponible; **no se hizo ninguna pregunta**.
**Inicio de ronda:** `2026-10-14 01:20:22`
**Fin de ronda:** *(se completa en el cierre)*
**Batería:** inicio **85 %** · *(final en el cierre)*
**Dispositivo:** `192.168.1.225:5555` → `device`, accesible durante toda la ronda.

**Árbol de partida:** `5d0fe3f` (`HEAD == origin/main`, árbol limpio).
**Serie de commits de la ronda:** `579e78a` (preregistro + A4) · `c454fe9` (A2) · `308ff31` (A3) ·
*(y los de B1 y cierre)*.

> **Regla que gobernó la ronda:** nada de lo añadido cambia una decisión de trading. `N` sigue
> siendo **0** operaciones supervisadas con estos cambios. La ronda **no operó**: el bot estuvo en
> `DISABLED` (ver §7, donde además se reporta que **al abrir la ronda estaba en `AUTONOMOUS`**).

---

## 0. Resumen en cinco líneas

1. **A1 (H-ROLLOVER): indicio, no causa.** En los journals del dispositivo los TIE caen en
   `cs ≤ 2` y los no-TIE no, pero N=5 en el journal principal y el análisis es **post hoc**.
2. **A4 (latencia): factor menor.** La latencia de tap cambia el signo del resultado en
   **1,3-2,8 %** de los instantes, frente al **~5 pp** que hace falta para el breakeven.
3. **A2: instrumentado.** El journal guarda ya `signal_ts`, `tap_start_ts`, `tap_end_ts`,
   `candle_second_at_tap_end`, `price_at_signal`, `price_at_tap_end`, en esquema **solo-append**.
4. **B1: detector hecho y calibrado con datos reales** (K=0,25, N=30 s → 3 avisos en 23 h);
   **avisa, nunca cambia de activo ni nombra candidato** (B2b, por B0 no concluyente).
5. **A3: flag `entryEarliestSecond` con el comportamiento actual por defecto, sin activar.**

---

## 1. A1 — Contraste de H-ROLLOVER

**Hipótesis contrastada:** el broker ignora o rechaza taps en los primeros ~2 s tras el cambio de
vela, y con una mediana de tap de ~500 ms el toque cae justo ahí.

### 1.1 Disponibilidad de `candle_second`

| fichero | filas | columnas | `candle_second` |
| :--- | ---: | ---: | :--- |
| `.tmp_dev14.csv` (dispositivo, extraído por `run-as`) | 26 | 21 | **SÍ** |
| `data/r8_journal_dev.csv` | 8 | 21 | **SÍ** |
| `workflows/rounds/r10_journal_vivo.csv` | 12 | 21 | **SÍ** |
| `workflows/rounds/r4_journal_full.csv` | 102 | 21 | **SÍ (campo no fiable, ver 1.3)** |
| `workflows/rounds/r6_journal.csv` | 3 | 21 | SÍ (sin TIE) |
| `data/null_combined_trades.csv` | 3.309 | 22 | SÍ (sin TIE) |

**Sin `candle_second`** (no aportan a H-ROLLOVER; esquema de 13 columnas):
`LOSS_DIAGNOSTIC_DATA.csv` (212), `loops/journal/trade_journal_device_all.csv` (212),
`loops/journal/trade_journal_live.csv` (17), `loops/journal/live/trade_journal_live.csv` (35),
`scratch/device_journal_live.csv` (212), `scratch/journal_device.csv` (214),
`scratch/journal_live.csv` (218), `scratch/journal_v2.csv` (220), `scratch/tj_check.csv` (223),
`scratch/tj_new.csv` (223), `scratch/tj_now.csv` (223), `scratch/device_pull/tj_export.csv` (223),
`scratch/device_pull/trade_journal_20260917.csv` (223).

Estos 17 TIE del dataset grande **no se pueden reconciliar con `candle_second`**: el campo no
existe en ese esquema. Se declara, no se estima.

### 1.2 Agregado de los journals del dispositivo

`dispositivo26 + r8_journal_dev + r10_journal_vivo + r6_journal` → **TIE=9, WIN=14, LOSS=26**.

| `cs` | TIE | no-TIE |
| ---: | ---: | ---: |
| 1 | 7 | 15 |
| 2 | 2 | 0 |
| 3 | 0 | 7 |
| 4 | 0 | 2 |
| 5 | 0 | 8 |
| 10 | 0 | 4 |
| 12 | 0 | 4 |

Fisher exacto unilateral:

| corte | a (TIE≤c) | b (TIE>c) | c (noTIE≤c) | d (noTIE>c) | p | OR |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 7 | 2 | 15 | 25 | **0,0336** | **5,83** |
| 2 | 9 | 0 | 15 | 25 | **0,00064** | ∞ |
| 3 | 9 | 0 | 22 | 18 | **0,0098** | ∞ |
| 5 | 9 | 0 | 32 | 8 | 0,171 | ∞ |

**Por fuente** (el agregado esconde la fragilidad):

| fuente | TIE | corte 2 → p | P(cs≤2\|TIE) | P(cs≤2\|noTIE) |
| :--- | ---: | ---: | ---: | ---: |
| `dispositivo26` | 5 | 0,0304 | 1,000 | 0,429 |
| `r8_journal_dev` | 2 | 0,2143 | 1,000 | 0,333 |
| `r10_journal_vivo` | 2 | 0,2273 | 1,000 | 0,400 |

**Conclusión honesta.** El patrón **se repite en las tres fuentes con el mismo signo**, y el
agregado es significativo. Pero:

- **N es minúsculo.** El journal principal tiene 5 TIE; los otros dos, 2 cada uno. Individualmente
  ninguno pasa el umbral.
- Los cortes `cs≤1/2/3/5` se eligieron **después** de ver los datos. Con cuatro cortes mirados, un
  p≈0,03 aislado no sobrevive a ninguna corrección por comparaciones múltiples.
- `P(cs≤2|TIE)=1,000` en las tres fuentes es sospechosamente perfecto y con N=5/2/2 puede ser
  perfectamente azar.

**Veredicto: INDICIO, no causa.** Es **exploratorio y post hoc**, tal como se fijó a priori en
`PREREGISTRO_R14.md`. **No autoriza a cambiar la ventana de entrada.** Lo que sí justifica es la
instrumentación de A2, que es la que permitirá contrastarlo con datos reales en lugar de con 9 filas.

### 1.3 `r4_journal_full` rompe el patrón y se EXCLUYE

Este fichero (102 filas, 21 columnas) parece el más valioso por tamaño, y por eso hay que
declarar por qué **no** se usa: **su campo `candle_second` no es el segundo del reloj.**

| evidencia | valor medido |
| :--- | :--- |
| `candle_second` de los WIN | n=37, rango **[0,9]**, mediana 4 |
| `candle_second` de los LOSS | n=38, rango **[9,18]**, mediana 12 |
| solape | **solo en `cs=9`** |
| WIN con `cs>9` | **0 de 37** |
| LOSS con `cs<9` | **0 de 38** |

Ningún proceso de precio produce esa separación: es una **partición determinista por resultado**,
es decir, el campo fue derivado del resultado (o de otro criterio desconocido), no medido al
entrar. Además sus TIE son **bimodales** por `duration_sec` (65 s con `cs` 3-9; 75 s con `cs` 11-19).

**Queda excluido de H-ROLLOVER** y se declara aquí para que no se reintroduzca por su tamaño.

### 1.4 `duration_sec` NO es una duración medida — confirmado

En **todas** las fuentes: WIN = `62`, LOSS = `72`, TIE = `75`, sin excepción (en `r4` el TIE
presenta además `65`). Es un **artefacto del método de liquidación** (timeout de 75 s sin
movimiento de saldo), no una medición del tiempo del trade.

**Revisión de refutaciones de R13 que dependían de él:** R13 usó `duration_sec` para refutar la
hipótesis de cadencia (TIE 118-121 s idéntica a WIN/LOSS). Esa refutación se apoyaba en las
**marcas de tiempo del journal** (`timestamp`/`iso_date`), no en `duration_sec`, así que **sigue en
pie**. No se ha encontrado ninguna otra refutación de R13 que dependiera de este campo.

### 1.5 Latencia de tap contra el precio

Cuantificada por primera vez en **A4** (§3). Hasta R13 sólo se sabía que el tap tarda
255-1205 ms (mediana ≈500 ms); no se había medido qué le hace eso al precio.

---

## 2. A2 — Instrumentación de latencia (esquema solo-append)

**Problema:** el journal guardaba sólo el segundo de vela de la **decisión**. Con taps de
255-1205 ms, el broker puede ver el toque en **otro** segundo de vela. Es exactamente el dato que
faltaba para investigar los TIE, y también la instrumentación mínima que pedía A4 de R13.

**Añadido al final del `HEADER`** (nunca reordenado, nunca borrado):

| campo | significado |
| :--- | :--- |
| `signal_ts` | instante en que el motor decide la señal (epoch ms) |
| `tap_start_ts` | instante en que arranca el tap |
| `tap_end_ts` | instante en que termina el tap |
| `candle_second_at_tap_end` | segundo de vela **medido al terminar el tap** |
| `price_at_signal` | precio del tick en el instante de la señal |
| `price_at_tap_end` | precio del tick al terminar el tap |

**Decisión de diseño declarada:** cuando no hay medición la celda se escribe **VACÍA**, nunca `0`.
Un `0` en `candle_second_at_tap_end` se leería como "tap en el segundo 0", una medición falsa.

**Retrocompatibilidad, ambas direcciones:**
- lector viejo sobre journal nuevo → conserva las 21 columnas clásicas (`csv.DictReader` ignora
  las extra);
- lector nuevo sobre journal viejo → los campos nuevos se leen como **ausencia**.

**Los mismos instantes van al evento `TAP` de `/events`:**
`root (x,y) exit= durMs= bypassHud= startTs= endTs= csTapEnd=`.

**Texto `Tiempo para comprar`:** **NO SE AÑADIÓ — NO VERIFICADO.** No existe ninguna referencia a
esa cadena en el código (`grep` sobre `*.kt`: 0 coincidencias), así que no consta que el servicio
de accesibilidad pueda leerla. Se declara en lugar de suponerlo.

**Tests:** `JournalLatencySchemaTest`, **5 casos**, 0 fallos (§5).

---

## 3. A4 — Deriva de precio por latencia (offline)

Artefacto: [`workflows/R14_A4_deriva_latencia.md`](../workflows/R14_A4_deriva_latencia.md).

**Métrica principal, declarada a priori: tasa de volteo** — fracción de instantes de señal en que
el signo del resultado (`precio(t+60s) − precio(entrada)`) cambia al retrasar la entrada de `t` a
`t + latencia`, liquidando ambos en el **mismo** `t + 60 s`. Aísla el efecto de la latencia sobre
el precio de entrada.

| activo | latencia | N señales | volteos | tasa | IC95 % |
| :--- | ---: | ---: | ---: | ---: | :--- |
| ETHUSD-OTC | 0,5 s | 34.247 | 473 | **1,381 %** | [1,258; 1,505] |
| ETHUSD-OTC | 1,0 s | 34.247 | 592 | **1,729 %** | [1,591; 1,867] |
| ETHUSD-OTC | 1,2 s | 34.247 | 928 | **2,710 %** | [2,538; 2,882] |
| BTCUSD-OTC | 0,5 s | 48.908 | 517 | **1,057 %** | [0,966; 1,148] |
| BTCUSD-OTC | 1,0 s | 48.908 | 734 | **1,501 %** | [1,393; 1,609] |
| BTCUSD-OTC | 1,2 s | 48.908 | 1.350 | **2,760 %** | [2,615; 2,905] |
| SOLUSD-OTC | 0,5 s | 30.170 | 282 | **0,935 %** | [0,826; 1,043] |
| SOLUSD-OTC | 1,0 s | 30.170 | 385 | **1,276 %** | [1,149; 1,403] |
| SOLUSD-OTC | 1,2 s | 30.170 | 575 | **1,906 %** | [1,752; 2,060] |

**Deriva absoluta en la ventana de latencia**, comparada con la tenencia real de 60 s:

| activo | \|ΔP\| mediana a 1,0 s | \|ΔP\| p90 a 1,0 s | \|ΔP\| mediana a 60 s | P(ΔP=0) a 1,0 s |
| :--- | ---: | ---: | ---: | ---: |
| ETHUSD-OTC | 0,0000 | 0,1600 | 0,6465 | 75,6 % |
| BTCUSD-OTC | 0,0105 | 3,3971 | 15,5690 | 32,7 % |
| SOLUSD-OTC | 0,0000 | 0,0100 | 0,0400 | 81,9 % |

**Conclusión prudente, que el criterio a priori permite afirmar:** la tasa de volteo a 1,0 s es
del orden del **1-3 %**, es decir **muy por debajo del ~5 pp** (+4,95 pp con payout 0,82) que hace
falta para cruzar el punto de equilibrio. **La latencia es un factor menor, no dominante**: no
explica por sí sola ningún resultado y no se le puede atribuir un edge ni una pérdida de edge.

**Lo que este análisis NO dice** (se declara porque la tentación es leerlo de más):

- Usa **ticks de feed**, no precios de ejecución del broker. Asume que el precio de entrada es el
  del feed en ese instante.
- Es **un solo día** (12-oct) y **un régimen**. No hay evidencia de variación entre días.
- La tasa de volteo **no es** el impacto sobre el P&L: mide cambio de signo, no magnitud. Quien
  quisiera convertirla en P&L necesitaría además la distribución de pagos, que no se modela aquí.
- **No hay teléfono en este análisis**: es enteramente offline, así que no está afectado por la
  latencia real del dispositivo (que es mayor que la simulada porque incluye el tap completo).

---

## 4. A3 — Flag `entryEarliestSecond`

**Estado: implementado con el comportamiento actual por defecto. NO ACTIVADO.**

- El default es **`1`**, que reproduce **exactamente** el `sec in 1..5` de todas las rondas
  anteriores. **No hay ningún cambio de comportamiento.**
- Sólo el **inicio** de la ventana es configurable; el fin (`:05`) **no se mueve**. Si se moviera,
  la variante B dejaría de ser comparable con la A y el experimento quedaría invalidado.
- Todos los puntos de decisión existentes pasan ahora por `MarketTickFilters.isInEntryWindow`:
  `TradingEngine` (3 sitios), `VisionAnalyzer`, `CandleTimeframe` y `MarketTick.isStrictTimingWindow`.
- **7 tests** (`EntryWindowFlagTest`) fallan si alguien mueve el default a 3 "para probar".

**Experimento preregistrado** para una futura sesión supervisada (en `PREREGISTRO_R14.md`):
variante A (`:01-:05`) vs B (`:03-:08`), alternando operaciones, métrica principal = tasa de TIE,
**≈380 operaciones por brazo** para detectar 8 %→4 % con α=0,05 y potencia 0,80, reglas de parada
que prohíben parar "porque va bien", y la advertencia de que **no prueba edge**.

> **DECISIÓN POR DEFECTO — pendiente de confirmar.** A3 toca el timing de ejecución, que la tarea
> marca como sensible. Sin usuario disponible: se implementa el flag **con el valor por defecto
> actual** y **sin activarlo**. No hay cambio de comportamiento que confirmar, sólo el diseño.

---

## 5. A5 — Fe de erratas

**(a) Los tres valores de batería de R13 (90 / 93 / 96 %).** No es una contradicción: son **tres
momentos distintos** del mismo documento.

| valor | dónde aparece | qué mide |
| ---: | :--- | :--- |
| **100 % → 90 %** | cabecera de `R13_REPORTE.md` | batería medida al abrir y cerrar la ronda |
| **93 %** | tabla de criterios de aceptación, §4 | el **final**, anotado al rellenar la tabla |
| **96 %** | §3, adenda 4 | una medición **intermedia**, durante el trabajo |

El valor correcto como balance de ronda es **100 % → 90 %** (cabecera), y la tabla de §4 debería
decir 90 %, no 93 %. La diferencia 93 vs 90 es de **mediciones repetidas con la pantalla encendida**
entre el cierre del trabajo y la redacción del informe. **Erratum de R13: §4 debe leerse 90 %.**
No se edita el informe de R13 (queda como histórico); se consigna aquí.

**(b) La contradicción `AUTONOMOUS` / `DISABLED` al cierre de R13.** En R13 el usuario dejó
`mode = AUTONOMOUS` a propósito y pidió **no** desactivarlo, pero §4 exigía `DISABLED` al cierre.
**Se aplicó §4**: seguridad prevalece. Se comprobó antes `hasPendingTrade: false` y
`orderState: NINGUNA`. Queda constancia de que el cambio lo hizo el agente contra la preferencia
expresada del usuario, y de que **el modo NO persiste**: `TradingEngine.mode` (`TradingEngine.kt:49`)
es sólo memoria, sin persistencia. Medido en R13: `AUTONOMOUS` → `force-stop` + relanzar →
`DISABLED`. **El usuario debe reactivarlo a mano** cada vez; no hay nada que "recordar".

---

## 6. Frente B — Sugeridor de "mercado inoperable"

### 6.1 B0 — Viabilidad multi-activo: **NO CONCLUYENTE → B2b**

Resuelto leyendo el código (fuente autoritativa, sin operar):

- El protocolo de suscripción usa `{"action":"subscribe","rics":[…]}`. La clave es **`rics`, en
  plural, un array**: la forma del mensaje admite varios RIC por diseño.
- **Las tramas multi-activo existen de verdad.** `TickFrameParser` tiene una rama explícita para
  `candidates.size > 1`, y `TickFrameParserTest` reproduce el incidente R11 en que llegó el precio
  de otro activo (`1.16603` de `EUR/USD` junto a `641.86` de `Z-CRY/IDX`). Eso sólo puede ocurrir
  si el servidor **emite precios de activos no suscritos** en alguna trama.

Por tanto *"¿aparecen precios de otros activos?"* está contestado que **SÍ**. Lo que **no** se
puede concluir sin operar es si **suscribir RICs adicionales rompe el stream** del activo actual:
eso exigiría modificar la suscripción y observar el feed, una intervención sobre el bot en modo
desatendido.

**Regla aplicada** (fijada a priori): *no viable **o no concluyente** → B2b*. **Se implementa B2b.**

> **DECISIÓN POR DEFECTO — pendiente de confirmar.** Se descarta B2a (acumulador de candidatos,
> que exigiría suscribir RICs adicionales) por precaución: no se altera la suscripción del feed
> cuando no se puede verificar que el stream sobreviva.

### 6.2 B1 — Umbrales, fijados **antes** del código

**Dos diseños se midieron y se descartaron con datos** (12-oct, ETH/BTC/SOL/Z-CRY):

| diseño | umbral | resultado medido | veredicto |
| :--- | :--- | :--- | :--- |
| Rango relativo, umbral **absoluto** | `1e-6` | racha máx **0,3 min** (ETH), **0,1 min** (BTC) | descartado |
| Rango relativo, umbral **absoluto** | `1e-3` | ETH "inoperable" **95,5 %** del tiempo, BTC **99,9 %** | descartado |
| **Precio estancado** (sin cambio de precio) | `≥1 min` | con feed vivo, racha máx **33 s** (ETH), **5 s** (BTC) | descartado |
| **Auto-relativo** `R(t) < K·M(t)` | — | sin constantes por activo, comparable entre activos | **ADOPTADO** |

El "estancamiento" de 61,7 min que aparecía en ETH era un **hueco de feed** (3.700 s sin ticks),
no mercado muerto: eso es `FEED_MUDO`, caso ya instrumentado. Los huecos de feed **no** cuentan.

**Definición adoptada:**
- `R(t)` = rango relativo de los últimos **16 ticks** = `(max−min)/último` (misma métrica que el
  veto V2 de R12; **no se sustituye ni se relaja V2**).
- `M(t)` = mediana de `R` en los **60 min previos del propio activo** (mínimo 16 muestras),
  **excluyendo** las muestras de los últimos `N` segundos (ver enmienda 1).
- **Inoperable** si `R(t) < K·M(t)` sostenido `N` segundos con el feed vivo (intervalo ≤ 5 s).
- **`K = 0,25`, `N = 30 s`.**

**Tasa de avisos medida con los valores finales** (`workflows/R14_B1_barrido_umbrales.md`):

| activo | avisos en 8 h | avisos/hora |
| :--- | ---: | ---: |
| ETHUSD-OTC | 2 | 0,24 |
| BTCUSD-OTC | 1 | 0,14 |
| SOLUSD-OTC | 0 | 0,00 |
| Z-CRY/IDX | 0 (sólo 0,24 h) | 0,00 |
| **agregado (23,11 h)** | **3** | **0,13** |

Muy por debajo del techo de **1 aviso/hora agregado** declarado a priori como "razonable". La
alternativa `K=0,5, N=20 s` daba **4,6/h** (ruido) y se descarta.

**Histéresis, enfriamiento y anti-spam (fijados antes del código):**
entrada 30 s sostenidos · salida 10 s sostenidos · enfriamiento 120 s tras apagar · y si el feed
no está fresco el detector **no opina** (eso es `FEED_MUDO`, no mercado muerto).

### 6.3 Enmienda al preregistro: guard de la mediana

Al escribir los tests apareció un **bug de corrección**: tal como estaba definido en B1, `M(t)`
incluía las propias muestras del tramo candidato, así que tras unos segundos de rango plano la
mediana caía a 0, `K·M` valía 0 y la condición se volvía imposible **justo cuando el aviso debía
dispararse**. El detector se apagaba solo.

**Corregido:** `M(t)` excluye las muestras de los últimos `N` segundos. Efecto sobre la
calibración: 0,26/h → **0,13/h**. **Se mantienen `K=0,25` y `N=30 s`** (siguen disparando, siguen
siendo los valores preregistrados, y re-optimizar sobre el mismo día sería sobreajuste).
Detalle en la enmienda 1 de `PREREGISTRO_R14.md`.

### 6.4 B2b — Salida implementada

- **Banner HUD:** `⚠ Mercado inoperable en <activo> (<N>s sin rango). Considera cambiar de gráfico`.
- **Contador** en `/status`: `inoperableCount`, `inoperableNow`, `ultimoInoperable`.
- **Evento** `INOPERABLE` en `/events`.
- **El bot NUNCA cambia de activo por su cuenta.** El aviso es texto y contadores, nada más.
- **Sin candidato:** al no haber acumulador (B2a descartado), el aviso **no nombra ningún activo**
  alternativo. Un activo sin ticks (como Aave en R13) **jamás se sugiere**.
- **Garantía estructural, no convención:** el detector no tiene ningún campo con nombre de activo,
  y hay un test que lo comprueba por reflexión.

### 6.5 B2a — NO implementado

Descartado por B0 no concluyente (§6.1). No hay acumulador de candidatos, no se suscribe ningún
RIC adicional y **el motor del activo actual no recibe datos de ningún otro activo**.

### 6.6 B3 — Tests del detector

`MarketInoperabilityDetectorTest`, **11 casos**, 0 fallos: mercado amplio no dispara · rango plano
sostenido sí dispara · histéresis de encendido (29 s no, 31 s sí) · histéresis de recuperación
(5 s no, 30 s sí) · enfriamiento anti-parpadeo · **feed caído no es mercado inoperable** · hueco
largo de feed reinicia la serie · **la referencia no se contamina con el propio estancamiento**
(regresión del bug de §6.3) · **el aviso nunca nombra un activo** · valores por defecto del
preregistro · precio inválido se ignora.

**Replay real:** reimplementación exacta del detector sobre los ticks reales de ETH/BTC/SOL/Z-CRY,
cuyos resultados son la tabla de §6.2.

---

## 7. Modo del bot durante la ronda y hallazgo de apertura

**HALLAZGO:** al abrir la ronda, `GET /status` devolvió **`mode = AUTONOMOUS`**. La tarea exigía
`DISABLED` durante **toda** la ronda, así que se desactivó **de inmediato** (`/mode?name=DISABLED`
→ `{"success":true,"mode":"DISABLED"}`) y se verificó después:

```
mode: DISABLED | orderState: NINGUNA | hasPendingTrade: False | balance: 37974735.36
```

No se sabe cuándo se activó ni si ejecutó alguna operación antes de la comprobación. El saldo es
**idéntico** al del cierre de R13 (`37.974.735,36`), lo que es consistente con **0 operaciones**
ejecutadas, pero no lo demuestra. **Se declara como NO VERIFICADO.** El resto de la ronda
transcurrió en `DISABLED`, con las pruebas de teléfono en modo lectura.

---

## 8. Restricciones respetadas

| restricción | estado |
| :--- | :--- |
| Commits pequeños, uno por tarea, directo a `main` | **PASA** |
| Balance de llaves en cada `.kt` modificado | **PASA** (compila) |
| **Nunca `Set-Content` sobre fuentes Kotlin** | **PASA** (tool `edit`/`write`; se detectó y corrigió un BOM en un mensaje de commit) |
| No cambiar umbrales ni vetos existentes | **PASA** (V1/V2 intactos; el flag A3 por defecto = comportamiento actual) |
| Todo umbral nuevo a `PREREGISTRO_R14.md` **antes** del código | **PASA** (commit `579e78a`, anterior a `c454fe9` y `308ff31`) |
| `PREREGISTRO_R10.md` y `R12` no se tocan | **PASA** |
| Estándar de evidencia: SHA / fichero versionado / captura | **PASA** (ver §5 y §9) |
| Suite con `cleanTest` + `--rerun-tasks` sobre el árbol final, hora real | **PASA** |
| Informe `loops/R14_REPORTE.md` | **PASA** (este documento) |

---

## 9. Verificación

**Suite:** `cleanTest testDebugUnitTest --rerun-tasks` sobre el árbol final, sin caché.
**Clases XML: 27** · **tests: 309** · fallos: **0** · errores: **0** · omitidos: **2**.
XML regenerados **2026-10-14 02:42:49** (hora local; los XML registran `07:42:49Z`).
*(R13 cerró con 286 tests en 24 clases; R14 añade **23 tests en 3 clases nuevas**:
`JournalLatencySchemaTest` 5, `EntryWindowFlagTest` 7, `MarketInoperabilityDetectorTest` 11.)*

**APK:** `assembleDebug` + instalación por ADB — *(se completa en el cierre)*.

---

## 10. NO VERIFICADO

1. **Causa de los TIE.** Sigue **sin determinar**. A1 produce un indicio fuerte pero post hoc; A4
   descarta la latencia como factor dominante. La instrumentación de A2 es lo que permitirá
   contrastarlo con datos reales. **No se declara causa raíz** (R13 ya pagó el precio de declarar
   una causa raíz falsa y corregirla en `c83992b`).
2. **Texto `Tiempo para comprar`.** No añadido: no consta que accesibilidad pueda leerlo.
3. **Viabilidad de suscribir RICs adicionales** sin romper el stream (B0). Requiere operar.
4. **Estado del bot antes de la comprobación de apertura (§7).** No se sabe si ejecutó algo.
5. **Banner del detector en el dispositivo.** Requiere instalar el APK y ver disparar el aviso;
   con una tasa de 0,13 avisos/hora en el día de referencia, **verlo una vez en una sesión corta
   es improbable**. Si no se observa, no es un fallo del detector.
6. **Variación entre días** de la tasa de avisos y de la tasa de volteo: **un solo día de datos**.
7. **Z-CRY/IDX** sólo aporta 0,24 h de ticks: su 0 avisos no es informativo.
8. **`null_combined_trades.csv`** (3.309 filas) no tiene TIE y **no aporta** a H-ROLLOVER.
9. **Los 17 TIE del dataset de 13 columnas** no se pueden reconciliar con `candle_second`: el campo
   no existe en ese esquema. Se declara, no se estima.

---

## 11. Recomendaciones para la próxima ronda

1. **Leer el journal instrumentado.** Con A2 en marcha, la próxima sesión real deja
   `candle_second_at_tap_end`, que es el contraste **real** de H-ROLLOVER (nada de post hoc).
2. **No adoptar la variante B** sin ejecutar el experimento preregistrado con sus 380 operaciones
   por brazo. Con 20 operaciones no se decide nada.
3. **B0 con acceso al teléfono** y autorización para tocar la suscripción: es lo único que
   desbloquea B2a y el sugeridor con candidato concreto.
4. **`duration_sec`:** dejar de usarlo como evidencia en cualquier análisis futuro.
5. **Batería de R13:** §4 debe leerse 90 %, no 93 % (§5a).
