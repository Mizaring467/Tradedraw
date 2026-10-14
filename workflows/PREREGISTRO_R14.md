# PREREGISTRO Ronda 14

**Estado:** umbrales y diseño fijados ANTES de escribir el código de esta ronda.
**Fecha:** 2026-10-14
**Árbol de partida:** `5d0fe3f` (árbol `app/` = `289b1f2e`), 286 tests en verde.

> Este documento fija decisiones **a priori**. Cuando algo se cambie después, se anota aquí
> como enmienda fechada y con el motivo. `PREREGISTRO_R10.md` y `PREREGISTRO_R12.md` **no se
> tocan**; este fichero no modifica ningún umbral de trading existente.

---

## 0. Regla que gobierna ambos frentes

**Nada de lo que se añade en R14 puede cambiar una decisión de trading.** El objetivo de A y B
es *medir* y *avisar*. Concretamente:

- No se toca la ventana sniper, ni CHOP, ni DojiRatio, ni la confluencia ≥ 80, ni el
  dimensionamiento quarter-Kelly.
- Los vetos V1 y V2 mantienen sus umbrales exactos. El detector de B **reutiliza** la métrica de
  V2 (`rango relativo en ventana de 16 ticks`) pero **no la sustituye ni la relaja**.
- `N` sigue siendo **0** operaciones supervisadas con estos cambios. Nada de esto crea edge ni
  puede usarse para justificar un edge.

---

## A. Frente A — TIE y latencia

### A1. H-ROLLOVER: hipótesis y criterio de falsación

**Hipótesis (de la revisión de R13):** el broker ignora o rechaza taps durante los primeros
~2 s tras el cambio de vela, y como la mediana del tap es ~500 ms, el toque cae justo ahí.

**Contraste:** tabla TIE/no-TIE por segundo de vela en todos los journals con `candle_second`,
Fisher exacto unilateral, e intervalo de confianza de la diferencia de proporciones.

**Umbral de decisión declarado a priori:** la hipótesis se considerará **sostenida** si el
intervalo de confianza al 95 % de `P(cs≤2 | TIE) − P(cs≤2 | no-TIE)` **excluye el 0** y el
signo es el esperado, usando **solo los journals del dispositivo de la misma generación**
(mismo esquema de 21 columnas y periodo comparable). Journals con `candle_second` de otra
generación o escalas incompatibles se excluyen y se declaran.

**Advertencia obligatoria, fijada ahora:** el análisis es **post hoc y exploratorio**. Los
cortes `cs≤1/2/3/5` se eligen *después* de ver los datos, la N de TIE es ~5 por journal, y el
resultado **no** es una prueba confirmatoria. Se reportará como indicio, nunca como causa.

### A2. Instrumentación (esquema solo-append)

Campos nuevos en el journal, **añadidos al final** del `HEADER` para que los lectores viejos
sigan funcionando con `csv.DictReader` (que tolera columnas extra y claves ausentes):

| campo | significado |
| :--- | :--- |
| `signal_ts` | instante en que el motor decide la señal (epoch ms) |
| `tap_start_ts` | instante en que arranca el tap |
| `tap_end_ts` | instante en que termina el tap |
| `candle_second_at_tap_end` | segundo de vela **medido al terminar el tap**, no al decidir |
| `price_at_signal` | precio del tick en el instante de la señal |
| `price_at_tap_end` | precio del tick en el instante de fin del tap |

Los seis campos son **opcionales**: si no hay dato se escribe vacío, nunca un 0 que parezca una
medición. El texto `Tiempo para comprar` del broker **no** se añade salvo que el servicio de
accesibilidad pueda leerlo; si no puede, se declara NO VERIFICADO en el informe.

Los mismos instantes van al evento `TAP` de `/events`.

### A3. Variante B de timing — flag `entryEarliestSecond`

**Diseño del experimento para una futura sesión supervisada (NO se ejecuta en R14):**

- **Variante A (control):** ventana actual `:01–:05` (comportamiento de hoy).
- **Variante B (tratamiento):** no despachar el tap antes de `cs ≥ 3`, es decir `:03–:08`.
- **Asignación:** alternando operaciones (A,B,A,B…) para equilibrar hora, régimen y activo.
- **Métrica principal:** tasa de TIE = `TIE / (WIN + LOSS + TIE)`.
- **Métricas secundarias:** WIN/(WIN+LOSS), latencia del tap, `cs` real al terminar el tap.
- **Tamaño muestral:** con una tasa base de TIE ≈ 8 % y buscando detectar una reducción a la
  mitad (8 % → 4 %), con α=0,05 bilateral y potencia 0,80, se necesitan **≈ 380 operaciones por
  brazo** (≈ 760 en total). Se declara aquí para que nadie decida parar al ver 20.
- **Reglas de parada:** parar solo por (a) alcanzar N, (b) stop-loss diario del propio
  `RiskManager`, o (c) fallo técnico. **Prohibido parar "porque va bien" o "porque va mal".**
- **Lo que NO se concluirá:** esto **no** prueba edge, no prueba que B sea mejor en WIN rate, y
  no autoriza a adoptar B. Solo mide la tasa de órdenes no registradas.

**Implementación en R14:** el flag `entryEarliestSecond` existe con **el valor actual por
defecto** (`1`), de modo que **el comportamiento medido hoy no cambia**. **No se activa.**

> **DECISIÓN POR DEFECTO — pendiente de confirmar.** A3 cambia *timing de ejecución*, que la
> tarea marca como sensible. Como el usuario duerme y no puede confirmar, se implementa el flag
> con el valor por defecto actual y **sin activarlo**. No hay cambio de comportamiento.

### A4. Deriva de precio por latencia (offline)

Medición sobre `data/ticks/{ETH,BTC,SOL}USD-OTC_2026-10-12.csv`. **Métrica principal y
declarada a priori: tasa de volteo**, es decir, la fracción de instantes de señal en los que el
signo del resultado (`precio(t+60s) − precio(entrada)`) cambia al retrasar la entrada de `t` a
`t + latencia`, con la **misma** liquidación. Se mide a 0,5 s, 1,0 s y 1,2 s.

**Criterio declarado a priori:** si la tasa de volteo a 1,0 s es del orden del **1-3 %**, la
latencia es un factor menor frente al margen necesario para el breakeven (≈ +5 pp). Si fuera
> 10 %, sería un factor dominante. El resultado se reportará como tabla + conclusión prudente.

### A5. Fe de erratas menores

(a) Reconciliar los tres valores de batería de R13. (b) Nota sobre la contradicción
`AUTONOMOUS`/`DISABLED` al cierre.

---

## B. Frente B — Sugeridor de "mercado inoperable"

### B0. Viabilidad multi-activo — **resuelto por código, no concluyente en dispositivo**

Lo establecido leyendo el código (fuente autoritativa, sin operar):

- El protocolo de suscripción usa `{"action":"subscribe","rics":[...]}` — la clave es **`rics`,
  en plural, un array**. La forma del mensaje admite varios RIC, y
  `BinomoWebSocketClient.subscribeToAsset()` envía ambos dialectos (`rics` y los topics
  Phoenix `asset:<ric>` / `rates`).
- **Las tramas multi-activo existen de verdad**: `TickFrameParser` tiene una rama explícita para
  `candidates.size > 1`, y `TickFrameParserTest` reproduce el incidente R11 en el que llegó el
  precio de otro activo (1.16603 de `EUR/USD` junto a 641.86 de `Z-CRY/IDX`). Eso solo puede
  ocurrir si el servidor **emite precios de activos no suscritos** en alguna trama.
- Por tanto la pregunta "¿aparecen precios de otros activos?" está **contestada que sí**.

Lo que **no** se puede concluir sin operar: si suscribirse a RICs adicionales **rompe** el
stream del activo actual. Eso exigiría modificar la suscripción y observar el feed, que es una
intervención sobre el bot en modo desatendido.

**Regla de decisión aplicada** (fijada a priori en el enunciado): *no viable **o no
concluyente** → B2b*. **La respuesta es NO CONCLUYENTE → se implementa B2b.**

> **DECISIÓN POR DEFECTO — pendiente de confirmar.** Se descarta B2a (acumulador de candidatos,
> que requeriría suscribir RICs adicionales) por precaución: no se altera la suscripción del
> feed cuando no se puede verificar que el stream sobreviva.

### B1. Umbrales del detector — elegidos **antes** del código

**Qué se midió primero** (`data/ticks`, 12-oct, ETH/BTC/SOL/Z-CRY) y por qué se descartaron dos
diseños:

| diseño | umbral | resultado medido | veredicto |
| :--- | :--- | :--- | :--- |
| Rango relativo con umbral **absoluto** | `1e-6` | racha máx **0,3 min** (ETH), **0,1 min** (BTC): nunca se sostiene | descartado |
| Rango relativo con umbral **absoluto** | `1e-3` | sí hay rachas, pero ETH "inoperable" **95,5 %** del tiempo y BTC **99,9 %** | descartado |
| **Precio estancado** (sin cambio de precio) | `≥1 min` | con feed vivo, racha máx **33 s** (ETH), **5 s** (BTC): **nunca** se dispara | descartado |
| **Auto-relativo** `R(t) < K·M(t)` | — | escalas comparables entre activos, sin constantes por activo | **ADOPTADO** |

El "estancamiento" de 61,7 min que aparecía en ETH era un **hueco de feed** (intervalo entre
ticks de 3.700 s), no mercado muerto: es `FEED_MUDO`, caso ya instrumentado aparte. Los
huecos de feed **no** cuentan como estancamiento.

**Definición adoptada:**

- `R(t)` = rango relativo de los últimos **16 ticks** = `(max − min) / último`. La misma métrica
  que el veto V2 de R12.
- `M(t)` = **mediana de `R` en los 60 minutos previos del mismo activo** (mínimo 16 muestras).
  Es una referencia auto-relativa: no hay constante por activo.
- **Inoperable** si `R(t) < K · M(t)` de forma **sostenida `N` segundos**, con el feed vivo
  (intervalo entre ticks ≤ 5 s).
- **Valores elegidos: `K = 0.25`, `N = 30 s`.**

**Tasa esperada de avisos, medida con esos valores en el día de referencia:**

| activo | avisos/hora |
| :--- | ---: |
| ETHUSD-OTC | 0,12 |
| BTCUSD-OTC | 0,14 |
| SOLUSD-OTC | 0,00 |
| Z-CRY/IDX | 0,00 (ventana corta, 0,24 h) |
| **agregado** | **0,26** |

Se declararon a priori como "razonables" **< 1 aviso/hora agregado** y "ruidosos" > 1,5. Con
`K=0.25, N=30s` el agregado es 0,26/h: dentro del rango razonable. La alternativa
`K=0.5, N=15s` daba **37,6/h** (ruidoso) y se descarta.

**Histéresis, enfriamiento y anti-spam (fijados ahora):**

- **Entrada:** 30 s sostenidos bajo `K·M`.
- **Salida:** el aviso se apaga cuando `R(t) ≥ K·M` de forma sostenida 10 s. La asimetría
  30 s/10 s es la histéresis: evita parpadeo.
- **Enfriamiento:** tras apagar, **120 s** sin poder volver a encender.
- **Anti-spam adicional:** máximo **1 aviso visible cada 5 min** por activo, aunque el detector
  oscile.
- **Feed no fresco:** si `isFresh = false` o `tickAgeMs == null`, el detector **no** declara
  inoperable: eso es `FEED_MUDO`, un problema distinto. No se mezclan.

### B2b. Salida (elegida por B0 no concluyente)

- Banner en el HUD: `⚠ Mercado inoperable en <activo> (<N>s sin rango). Considera cambiar de gráfico`.
  **Sin nombrar ningún activo candidato**, porque no hay acumulador de candidatos.
- Contador `inoperableCount` en `/status`.
- Evento `INOPERABLE` en `/events`.
- **El bot NUNCA cambia de activo por su cuenta.** El aviso es solo texto y contadores.
- Un activo **sin ticks** (como Aave en R13) tampoco puede ser "operable": se declara inoperable
  por `FEED_MUDO`, no por este detector, y **jamás se sugiere**.

### B3. Tests comprometidos

1. Detector con series sintéticas: rango constante ⇒ inoperable; rango amplio ⇒ operable.
2. **Histéresis:** no enciende antes de 30 s; no apaga antes de 10 s.
3. **No-spam:** una oscilación rápida no produce más de un aviso por ventana de enfriamiento.
4. **Replay real de ETH** con los ticks de `data/ticks`.
5. **Activo sin ticks no se sugiere**: el texto del aviso nunca nombra un candidato.
6. Retrocompatibilidad del journal: leer con un lector viejo un journal con las columnas nuevas
   y viceversa.

---

## Enmiendas

*(ninguna todavía)*
