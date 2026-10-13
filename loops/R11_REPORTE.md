# Ronda 11 — Coherencia HUD ↔ feed WS, integridad de datos y strike real

**Rama:** `main` · **Rango:** `d4ef3a9..HEAD` (7 commits) · **Fecha:** 2026-10-13
**Restricción de la ronda:** NO se cambian estrategias ni umbrales de veto. Solo integridad de datos y coherencia del HUD.
**Cumplimiento:** ningún umbral (`CHOP > 61.8`, `DojiRatio ≥ 0.35`, confluencia ≥ 80 pts, ventana `:01..:05`/`:58..:03`, veto `:06..:57`, quarter-Kelly 1.0–3.5 %) fue modificado. Los dos cambios que rozan la decisión de trading están marcados con **⚠ TOCA DECISIÓN** y explicados.

---

## Resumen ejecutivo

| Tarea | Resultado | Commit |
| :--- | :--- | :--- |
| T1 — Integridad del feed (parser fail-closed por activo) | **CAUSA RAÍZ CONFIRMADA y corregida** | `37f6d32` |
| T2 — Auditoría de contaminación entre activos (impacto en R9/R10) | **CONFIRMADA la vía, NO MEDIBLE en los datos guardados** | `ebe3681` |
| T3 — Strike y liquidación en dominio de precio (ITM/OTM real) | **CAUSA RAÍZ CONFIRMADA y corregida** · **⚠ TOCA DECISIÓN** | `d54ec03` |
| T4 — HUD sin datos inventados (instantánea inmutable) | **CAUSA RAÍZ CONFIRMADA y corregida** | `267ad0f` |
| T5 — Micro-velocidad: signo coherente y unidad comparable | **CAUSA RAÍZ CONFIRMADA y corregida** (2 iteraciones) | `267ad0f`, `7ace8c3` |
| T6 — Countdown real y P&L real | **CONFIRMADO y corregido** | `267ad0f` |
| T7 — Invariantes de coherencia del HUD | **CONFIRMADO y corregido** (contradicción nueva hallada en dispositivo) | `f30c530` |
| T8 — Layout del HUD, plegado y solapamiento | **CONFIRMADO**; corregido en 2 iteraciones, **la 2ª pendiente de verificación visual** | `f752077`, `2ffebe9` |
| T9 — Verificación cruzada (solo informe, sin cambios) | **Hallazgos abajo** | — |

**Tests:** `217 passed, 0 failed, 2 skipped` (16 clases). Baseline antes de R11: 164 tests.
**Build:** `assembleDebug --no-daemon --offline --no-configuration-cache` → `BUILD SUCCESSFUL`.
**Instalado y verificado en dispositivo:** POCO X6 Pro `UGU8KFUKM7E6CE8D`.
**Informe del probador:** `loops/screenshots/r11/REPORTE_TESTER.md` (28 capturas).

> **Nota de verificación:** los puntos T3, T4 (caso "feed no fresco") y T6b (total resuelto ≠ 0) quedaron **NO VERIFICABLES en dispositivo** por no haberse abierto ninguna operación en la sesión de prueba (`hasPendingTrade: false`, `signalsGenerated: 0` durante ~40 min). Están **cubiertos por tests unitarios**, no por observación visual. El fix de T8 (2ª iteración) quedó **sin confirmar en pantalla** porque el dispositivo se desconectó de ADB. Se declara explícitamente en lugar de darlo por bueno.

---

## T1 — Integridad del feed WebSocket

### Causa raíz: CONFIRMADA

El parser de `processIncomingMessage` aceptaba un `rate` sin comprobar a qué activo pertenecía. Con el feed multi-activo de Binomo (`{"data":[{"assets":[{...,"ric":"...","rate":...}]}]}`) el HUD llegó a mostrar el precio de OTRO activo sobre el gráfico de Crypto IDX.

Evidencia en dispositivo (capturas `before_01.png` y `before_02.png`, 720×1600, separadas ~35 s):

```
WS Activo (Crypto IDX): 641.867319   →  1.16603      (salto de escala)
Micro-Velocidad: +10922.404297/s ▲ Impulso Alcista
              →  -13403.387/s ▲ Impulso Alcista       (signo y flecha opuestos)
W: 0 | L: 1 | T: 2
```

Un `1.16603` no puede ser Crypto IDX (cotiza ~641). Y `±10 000/s` es exactamente el delta que produce mezclar dos escalas (641 − 1.166 ≈ 640) dividido por el intervalo de tick.

### Corrección

- `TickFrameParser.kt` (**nuevo**, puro y sin estado): `parse(raw, activeAsset)` exige coincidencia exacta de `ric` (case-insensitive). Un frame multi-activo que no contenga el activo propio devuelve `FrameAnalysis(tick = null, hadForeignAsset = true)`. Un frame sin ningún `ric` solo se acepta si contiene **exactamente un** precio anónimo distinto. El fallback por regex queda restringido al bloque `{...}` que contiene `"ric":"<activo>"`. Profundidad máxima 8.
- `BinomoWebSocketClient.kt`: estado **por activo** (`lastPriceAsset`, `lastPrice`, `lastPriceTimeMs`, `smoothedVelocity`). Al cambiar de activo se resetea el estado de velocidad en lugar de derivar contra el precio anterior. Contadores visibles `rejectedForeignAssetTicks` / `rejectedSanityJumpTicks`. Guarda de salto implausible `MAX_TICK_JUMP_RATIO = 0.05` (5 %).
- HUD: la línea de diagnóstico añade `⛔ ajenos:N saltos:N` cuando hay descartes, para que la guarda sea auditable sin logcat.
- `TickFrameParserTest.kt` (**nuevo**): 12 tests.

**NO toca decisión de trading**: descarta datos corruptos que antes entraban al motor. No relaja ni añade vetos.

### Evidencia en vivo tras la corrección

`GET /status` con el feed conectado, sesión en curso:

```
activeAsset       = Z-CRY/IDX
ws_active_asset   = Z-CRY/IDX      (coinciden: no hay deriva de activo)
socketTicks       = 2217
rawSocketMessages = 2218           (2217/2218 aceptados por el parser)
tickSource        = socket
lastTickAgeMs     = 168
rangeRatio        = 2.4927e-10
priceFrozen       = False
journalError      = ''
```

El HUD correspondiente (`after_21.png`) muestra `WS Activo (Crypto IDX): 641.867315 (4ms) | Headless` **sin** la etiqueta `⛔ ajenos:N saltos:N`, es decir **0 ticks descartados por activo ajeno y 0 por salto implausible** en 2217 ticks. La guarda no rechaza datos sanos.

---

## T2 — Auditoría de contaminación entre activos (impacto en R9/R10)

Entregables: `scripts/r11_contamination_audit.py`, `workflows/rounds/r11_contamination_audit.json` (commit `ebe3681`).

### Resultado: la vía está confirmada, el daño concreto NO es medible en los datos guardados

- **126 181 ticks** auditados repartidos en **5 feeds únicos** distintos: **0** ticks contaminados por cambio de `ric` y **0** por salto > 5 %.
- **Ningún feed almacenado tiene escala ~1.166.** El escenario exacto observado en pantalla no existe en los datos persistidos, así que su efecto sobre R9/R10 no se puede cuantificar hacia atrás: solo se puede afirmar que la guarda de T1 lo impide a partir de ahora.
- **3 343 filas con `tick_velocity == price_y`** son un **artefacto de logging** (la columna `price_y` del journal almacena PRECIOS reales — p. ej. `1121.50`, `1265.00` —, no coordenadas Y). Velocidad absurda genuina: **0**.
- **Wilson:** con payout 0.8091 (break-even 55.28 %), **ningún corte alcanza el break-even por cota inferior**, antes ni después de excluir las filas expuestas. La conclusión "sin edge demostrado" **no cambia**.
- **Conjunto de confirmación post-T0: N = 15 ≪ 200** preregistrados → **no evaluable**. No se puede declarar nada sobre el efecto de la corrección con esa muestra.

**Lectura honesta:** T2 no exonera ni condena a R9/R10. Dice que la contaminación era posible y está cerrada, y que los datos guardados no permiten medir su impacto. Cualquier afirmación más fuerte sería inventada.

---

## T3 — Strike y liquidación en dominio de precio · **⚠ TOCA DECISIÓN DE TRADING**

### Causa raíz: CONFIRMADA

El strike se representaba como **coordenada Y en píxeles**, no como precio. `PendingTradeSnapshot.entryY: Float`. La resolución de la operación comparaba píxeles de la línea con píxeles del precio, con el signo invertido respecto a la convención de pantalla (Y crece hacia abajo). Consecuencia visible: chip `▼ PUT [ITM +$$$]` con el precio **por encima** del strike — imposible en una PUT.

### Corrección

- `StrikeCalculator.kt` (**nuevo**): `TradeMoneyness { ITM, OTM, TIE }`, `moneyness(action, entryPrice, currentPrice)` **estricto** (empate nunca gana), `estimatedPnl`, `strikeLabel`, `formatPrice`, `formatMoney`, `strikeGeometry`. Todo con `Locale.US`.
- `PriceViewportMapper`: calibración punto a punto y, en su defecto, proyección del rango de precios reciente sobre la zona visible del gráfico.
- `TradingEngine`: `entryY: Float` → `entryPrice: Double`. Ambas rutas (visión y headless) resuelven con `StrikeCalculator.moneyness`.
- `AutoDrawEngine.drawTradeEntry(action, entryPrice: Double, ...)`, `updateTradeEntryLiveStatus(livePrice: Double): TradeMoneyness`.
- `CustomDrawingView`: la etiqueta del strike se pinta literal desde `shape.labelText`; se eliminaron los literales `+$$$` y `$0` que se mostraban cuando no había dato.
- `StrikeCalculatorTest.kt` (**nuevo**): 18 tests.

### ⚠ TOCA DECISIÓN DE TRADING — leer con atención

La **resolución de la operación** es parte del bucle de decisión: determina si la operación se contabiliza como WIN o LOSS y, con ello, la racha, la martingala y el sizing.

El fallback de visión comparaba un valor Y (que en headless es **siempre 0**) contra 0 con el signo invertido: **nunca disparaba de forma significativa**. Ahora resuelve por precio real. Por tanto, **las operaciones pueden empezar a resolverse en casos en los que antes no se resolvían (o se resolvían mal)**.

Lo que **NO** cambia: ningún umbral de entrada, ningún veto, ni la confluencia. Lo que **SÍ** cambia: la contabilidad de resultados. Si en R9/R10 hubo operaciones mal resueltas, sus estadísticas están afectadas y **no se corrigen retroactivamente** en esta ronda.

### Limitación conocida (simplificación deliberada)

El eje de precios del gráfico de Binomo **no está expuesto en el árbol de accesibilidad de Android**. Verificado con `adb shell uiautomator dump --compressed /sdcard/bk.xml` + `pull` (29 108 bytes): los textos presentes son `Cuenta Demo`, `Col$37,885,736.96`, `Crypto IDX`, `83%`, `1m`, `Cantidad`, `Col$100,000`, `Hora`, `13:43`, `Reintentar`. **Ninguna etiqueta de precio.** El eje es visible en pantalla (captura: `641.86732200000`, `641.86731800000`, `641.8673173000`…) pero no consultable por accesibilidad.

Por tanto la calibración exacta precio→Y es **imposible** en modo headless. `PriceViewportMapper` cae a la proyección del rango reciente. Está marcado en el código como simplificación `ponytail:` con su ruta de mejora (OCR del eje por visión). Consecuencia: la **posición vertical** de la línea de strike es una aproximación; el **cálculo** de ITM/OTM y el P&L no dependen de ella, usan precios.

---

## T4 — HUD sin datos inventados

### Causa raíz: CONFIRMADA

`OverlayService` pintaba racha, termómetro y filtro S/R desde `tradingEngine.latestAnalysisResult`, que proviene de la **ruta de visión (retirada por batería)**. En modo WebSocket puro ese objeto queda con los valores por defecto: `signalPowerCall = 50`, `signalPowerPut = 50`, `streakBadge` vacío. **El HUD mostraba un 50/50 y una racha que nadie había medido**, presentados con el mismo aspecto que un dato real.

### Corrección

- `HudSnapshot.kt` (**nuevo**, inmutable): el motor construye la instantánea con la fuente viva (feed WS + motor sintético + `RiskManager`); `OverlayService` **solo pinta**. Los campos de medida son **nullables**: sin dato se pinta `—`, nunca un valor por defecto disfrazado.
- `hasFreshTick` es la puerta: sin tick fresco no hay termómetro, ni racha, ni velocidad viva.
- `getEngineReasoning()` deriva el análisis del tick **actual** en vez de `latestAnalysisResult`; el filtro S/R usa **solo** el motor sintético.
- `HudSnapshotTest.kt` (**nuevo**): invariantes (sin tick fresco no hay medida; `velocidadIsLive`; termómetro que suma 100; PUT sobre strike es OTM; sin strike no hay ITM/OTM).

**NO toca decisión de trading**: el termómetro ya venía del motor sintético en la ruta de decisión. Aquí solo se deja de mostrar el 50/50 de la visión **al usuario**.

---

## T5 — Micro-velocidad

### Causa raíz: CONFIRMADA — dos defectos

**1) Signo y flecha en contradicción.** La etiqueta venía de `tick.isBullishImpulse` (campo de la ruta de visión) y el número de `tick.velocity`. Resultado en pantalla: `-13403.387/s ▲ Impulso Alcista` — flecha alcista, color rojo, número negativo.

**2) Unidad no comparable.** Se mostraban unidades absolutas por segundo: `IDX ≈ 641` frente a `SOL ≈ 32`. Un mismo umbral no puede servir a ambos.

### Corrección (dos iteraciones — ver *Errores* abajo)

- `TickVelocityLabel.kt` (**nuevo**, puro): `of(velocity)` deriva la dirección **siempre** del signo de la velocidad medida; `format(velocity, priceRef)` da porcentaje del precio por segundo con **notación adaptativa** (fijo por encima de 0.01 %/s, científico por debajo). `Neutro` **solo** con velocidad exactamente cero.
- El HUD ya no tiene umbral fijo ni lógica de decaimiento propia.
- `TickVelocityLabelTest.kt` (**nuevo**): 6 tests, incluido el formato con `Locale.US`.

Evidencia tras la corrección (`after_21.png`): `⚡ Micro-Velocidad: -1.18e-08 %/s ▼ Bajista` — signo, flecha y color coinciden.

---

## T6 — Countdown real y P&L real

### Confirmado

| Antes | Ahora |
| :--- | :--- |
| `Liquidando en vela :00s` (literal fijo, sin relación con ningún instante) | `⏳ … · Hasta exp: Ns` (cuenta atrás real desde la apertura) |
| `▼ PUT [ITM +$$$]` | `Strike <precio> · ITM ✓ / OTM ✗ · P&L ±$x.xx` |
| `W: 0 \| L: 1 \| T: 2` (`T` = nº de operaciones) | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0` (total resuelto real) |
| `⏱️ [1m] :19s` | `⏱️ Vela [1m] :31s` (rotulado como **reloj de vela**) |
| 3 líneas redundantes de "Operación en curso" | **1** línea |

P&L calculado con el payout **realmente configurado** (`RiskManager.sorosPayoutRate`), no con un 83 % asumido. El timer de vela y la expiración de la operación se distinguen explícitamente para que no se confundan.

**NO toca decisión de trading**: presentación de estado.

---

## T7 — Invariantes de coherencia · **⚠ TOCA DECISIÓN (indirectamente)**

### Confirmado — y se encontró una contradicción NUEVA en dispositivo

`HudCoherence.kt` (**nuevo**, puro) define qué es coherente:

- `isIncoherent`: tendencia `ALCISTA` con termómetro de mayoría `PUT` (o la simétrica); posición abierta contra la tendencia declarada. El HUD muestra `⚠ contra-tendencia`.
- `srStatusIsCoherent`: `Toque Directo` exige estar dentro del 15 % del canal.
- `actionConflict`: el filtro S/R y el plan de acción no pueden pedir direcciones opuestas.

**Contradicción nueva encontrada al desplegar T4/T5 en dispositivo** (`after_18.png`):

```
🛡️ Filtro S/R: Toque Directo en Soporte [CALL Óptimo]
🛡️ Anti-Chop Activo · Cerca de Resistencia (7%): Buscando Mecha Rechazo PUT
```

Dos **métricas distintas con el mismo nombre**. `getEngineReasoning` calculaba la distancia como **posición dentro del canal** — `(precio − soporte) / (resistencia − soporte)` — mientras el plan de acción, los vetos y `SyntheticCandleEngine.distanceToSupportRatio` la calculan **dividiendo por el ATR**. Con ATR pequeño sobre un canal amplio, la primera da 5 % y la segunda 75 %: **el mismo precio está "pegado al soporte" y "pegado a la resistencia" a la vez.**

Cuantificado con datos reales del dispositivo (`/status`):

```
dynamicSupportPrice   = 641.8673174
dynamicResistancePrice= 641.8673186531249
ancho de canal        = 1.25e-06      →  2e-9 del precio
recentPriceRangeRatio = 4.518e-10     (60 ticks)
```

En **Crypto IDX** el feed tiene un paso de tick sub-micro: un canal de 1.25e-06 se cierra y se abre constantemente, y cualquier división por él es ruido.

### Corrección

- `getEngineReasoning` usa **solo** `distanceToSupportRatio` / `distanceToResistanceRatio` del motor sintético — **los mismos ratios que deciden el trading** — con orden por cercanía real (toque soporte → toque resistencia → cerca soporte → cerca resistencia → neutral). Se eliminaron las sufijos `% canal`, que etiquetaban una unidad engañosa.
- `OverlayService` marca `⚠ CONFLICTO S/R↔PLAN` en naranja en la línea del plan.
- `HudSnapshotTest`: 2 casos nuevos (contradicción CALL/PUT detectada, CALL/CALL coherente).

### Verificación en dispositivo tras la corrección

`after_21.png` — combinación **coherente**:

```
🛡️ Filtro S/R: 🔴 Toque Directo en Resistencia [PUT Óptimo]
🎯 Plan: Cerca de Resistencia (14%). Esperando gatillo PUT en la ventana :01s - :05s.
📊 Tendencia: LATERAL / RANGO          Prob: 62%
[38% CALL | 62% PUT]
```

Filtro y plan piden **PUT** los dos; sin prefijo `⚠ CONFLICTO S/R↔PLAN`. Los dos porcentajes suman 100 y el `Prob: 62%` casa con la rama de tendencia lateral cerca de un extremo (no con un `86 %` de tendencia direccional), que es lo coherente con `LATERAL / RANGO`.

`after_19.png` — el caso opuesto, también coherente:

```
🛡️ Filtro S/R: 🔴 Toque Directo en Resistencia [PUT Óptimo]
⛔ Compra vetada bajo resistencia: esperando rechazo o ruptura confirmada.
📈 Tendencia: ALCISTA                  Prob: 86%
[76% CALL | 24% PUT]
```

Aquí el veto de compra y el filtro de resistencia **se refuerzan** en vez de contradecirse, que es justo el comportamiento que faltaba antes.

### ⚠ TOCA DECISIÓN DE TRADING — matiz

La línea de filtro S/R es **informativa**: el motor nunca la usó para decidir. Los ratios ATR que ahora se muestran **son los que ya gobernaban** vetos y confluencia. **No se relajó ni endureció ningún umbral.** El efecto es que el HUD deja de mostrar una segunda métrica paralela que contradecía al motor.

---

## T8 — Layout del HUD

### Causa raíz: CONFIRMADA — y el probador encontró que seguía fallando

La ventana se creaba con `WRAP_CONTENT`; el contenedor de detalles mide 260 dp fijos; con el offset `x = 50` la ventana terminaba **fuera del borde derecho** y el **botón de plegado (22 dp) quedaba inalcanzable**. Evidencia de `dumpsys window`: `mAttrs={(600,278)(wrapxwrap) … }` con `Requested w=108 h=1176`.

### Primera corrección (commit `f752077`)

| Problema | Corrección |
| :--- | :--- |
| Ventana desbordada | Ancho fijo a `pantalla − 2×24 dp`; `hud_root` a `match_parent` → los `TextView` envuelven texto |
| Plegado fuera de pantalla | Botón de 22 dp → 32 dp |
| Fondo demasiado transparente | `#B3070A14` (70 %) → `#E6070A14` (90 %) |
| Tapaba la barra de herramientas y el chip del strike | Con operación abierta el HUD se muestra **plegado** por defecto |
| Toast sobre los botones SUBE/BAJA | Toast sustituido por confirmación **en el propio botón** |

Verificado: `mAttrs={(48,50)(624xwrap) …}` — 624 px sobre 720 px, con 48 px de margen. **El ancho quedó bien** (el tester lo confirma: `x=96 + 624 = 720` exacto, texto largo envuelve bien).

### El probador encontró 3 fallos adicionales — todos con la MISMA causa raíz

El probador rechazó T8 con evidencia reproducible. Los tres fallos:

1. **`hud_btn_collapse` no responde a los toques.** 6 coordenadas probadas (`666,813`; `663,813`; `658,813`; `672,813`; `650,892`; `597,893`); `/hud` siguió devolviendo `"hudCollapsed":false`.
2. **Con cabecera larga (`[PAUSA FRANCOTIRADOR]`, submodo SNIPER) el chip ▼ desaparece** de pantalla.
3. **Los toques en la banda de cabecera se filtran a Binomo**: abrieron el panel del Agente IA, el **menú de timeframe, cambiando el gráfico a 5m**, y `MainActivity`. El cambio de timeframe invalida el análisis de velas en curso.

**Contraste decisivo que aisló la causa:** arrastrar el panel **sí** funcionaba (`mAttrs` `(96,711)` → `(96,793)`) y el plegado por HTTP **sí** funcionaba (`tester_05_collapsed_http.png`). Luego la lógica de plegado estaba bien y el fallo era del **disparador táctil**, concretamente que el chip estaba **fuera del área de la ventana**.

**Causa raíz única:** `hud_header_line1` medía `wrap_content`. Con etiquetas largas el contenido crecía más allá del ancho fijo de 624 px y empujaba los botones de la derecha fuera de la pantalla. Un chip fuera de la ventana (a) no se ve, (b) no recibe toques, y (c) esos toques los recibe la app de debajo. **Los 3 fallos son el mismo bug con 3 síntomas.**

### Segunda corrección (commit `2ffebe9`)

- `hud_header_line1`: `wrap_content` → `match_parent`.
- Etiquetas (`mode`, `strategy`, `streak`, `timer`) agrupadas en un contenedor interno con `layout_weight = 1`, `maxLines = 1` y `ellipsize = end`: el texto se **recorta con elipsis** y nunca empuja a los botones.
- `hud_strategy` con peso propio para absorber el recorte.
- Botón de plegado a **36×32 dp**, **el último** de la fila y en un contenedor **sin peso**: no puede ser comprimido ni desplazado por el texto.

> ⚠️ **Estado: corregido y compilado, PENDIENTE DE VERIFICACIÓN VISUAL EN DISPOSITIVO.** El teléfono se desconectó de ADB al cerrar el probador (`device 'UGU8KFUKM7E6CE8D' not found`) y no ha vuelto a responder por WiFi. Compila (`BUILD SUCCESSFUL`) y los 217 tests siguen verdes, pero **el fix de T8 no está confirmado en pantalla**. Es el único punto de la ronda sin evidencia visual de cierre.

---

## T9 — Verificación cruzada (solo informe, sin cambios)

**1. Stake del HUD frente a la cantidad del broker.**
`HUD: [M0 | $1.0]` / `[M0 | $1.0]` frente a `Cantidad Col$100,000` en Binomo. Explicación confirmada en código: el stake del HUD sale de `RiskManager.getCurrentInvestmentAmount()` en **unidades internas de la app**, y `observedOrderAmount` (`Col$100,000`) se usa **solo como comprobación de seguridad** en `canExecuteTrade`. El bot **no empuja** el importe a la interfaz del broker; el importe lo fija la configuración de Binomo. **Confirmado: no es un bug, es una discrepancia de presentación.** Riesgo: un operador puede creer que la app controla el importe y no es así.

**2. Desfase de reloj.**
`date` del dispositivo `15:01:07` frente al host `15:01:08` → **≈1 s**. El `Vela [1m] :59s` del HUD concordaba con `Hasta expiración 00:57` de Binomo dentro de ±2 s, que es lo esperable siendo **relojes distintos** (ciclo de vela frente a expiración de la orden). No se detectó desincronización.

**2b. Verificación del reloj de vela, medida (no estimada).**
Serie de 3 capturas con la hora del dispositivo anotada en el momento de cada una. El reloj de vela del HUD debe cumplir `HUD = 60 − segundo`:

| Captura | Hora dispositivo | Segundo | HUD `Vela [1m]` | Esperado | Desfase |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `clock_01.png` | 15:42:05 | 05 | `:56s` | 55 | +1 s |
| `clock_03.png` | 15:42:26 | 26 | `:35s` | 34 | +1 s |

Desfase **constante de 1 s**, que es el retardo de refresco del bucle del HUD (throttle de 400 ms más el ciclo de pintado). **No hay deriva acumulada**: el offset es el mismo en dos muestras separadas 21 s. El contador de vela es correcto.

**2c. El detector de conflicto T7 dispara en producción.**
`clock_03.png` captura el aviso funcionando con datos reales:

```
🛡️ Filtro S/R: 🔴 Toque Directo en Resistencia [PUT Óptimo]
⚠ CONFLICTO S/R↔PLAN · 🛡️ Anti-Chop Activo · Cerca de Soporte (15%): Buscando Mecha Rechazo CALL
[86% CALL | 14% PUT]
```

El filtro pide PUT (precio en resistencia) y el plan anti-chop pide CALL (precio cerca de soporte). **Es una contradicción real del motor sintético**, no un problema de presentación: las dos ramas consultan `distanceToResistanceRatio` y `distanceToSupportRatio` y ambas se declaran satisfechas a la vez. En **Crypto IDX**, con un canal de ~1.25e-06 sobre un precio de 641.867 (2e-9 relativo), ambas distancias pueden caer por debajo de su umbral simultáneamente porque el ATR normalizador es del mismo orden que el ruido del propio feed. El HUD ahora lo **declara** en vez de mostrar los dos consejos opuestos como si fueran compatibles.

**Esto NO se corrige en R11**: tocar los umbrales de proximidad S/R sería cambiar la lógica de decisión, expresamente excluido por el enunciado de la ronda. Queda como **riesgo #4** (ver abajo), agravado: no es que la métrica sea inestable, es que **el filtro anti-chop puede pedir la dirección contraria al filtro S/R y ambos llegar a la fase de disparo**.

**3. Payout asumido.**
`RiskManager.sorosPayoutRate` por defecto **0.85**, no 0.83. `Binomo` mostró `82%` en la cabecera del activo. Por tanto el P&L estimado del HUD puede diferir del liquidado real entre 0.82 y 0.85 según el activo. **El P&L del HUD ahora usa el valor configurado**, que es la mejor estimación disponible, pero **no es una lectura del broker**: no hay forma de leer el payout real desde la app. Riesgo pendiente abajo.

**4. Columna `price_y` del journal.**
Almacena **precios** (`1121.50`, `1265.00`), no coordenadas Y. El nombre de la columna induce a error y complicó la auditoría T2 (de ahí las 3 343 filas falsas con `tick_velocity == price_y`). **No se renombra en esta ronda** para no romper el análisis histórico; queda anotado.

---

## Errores cometidos durante la ronda (transparencia)

1. **T5, primer arreglo defectuoso.** Introduje un umbral de **0.5 bps/s** que es **inalcanzable** en Crypto IDX: el activo cotiza ~641.867317 con rango relativo de **4.5e-10** en 60 ticks, es decir **4.5e-6 bps** — cuatro órdenes de magnitud por debajo del umbral. El indicador quedó clavado en `-0.0 bps/s ━ Neutro` durante toda la sesión. **Detectado en dispositivo**, no en los tests: los tests usaban valores de velocidad plausibles para un activo normal. Corregido en `7ace8c3` extrayendo `TickVelocityLabel` con formato adaptativo y un test que fija el caso sub-micro.
2. **Bug de precedencia en el ancho de la ventana.** `.coerceAtLeast()` sobre `(HUD_SIDE_MARGIN_DP * density).toInt()` en lugar de sobre la resta completa → la ventana quedó en **240 px** en vez de 624 px. Detectado leyendo `dumpsys window`, no a ojo.
3. **Test con expectativa incorrecta.** `0.641/641×100 = 0.10 %/s` cae en formato fijo, no científico. Corregido el test, no el código.
4. Herramienta: `Set-Content` de PowerShell destrozó los acentos UTF-8 en `BinomoWebSocketClient.kt` (`ConexiÃ³n`). Revertido con `git checkout --` y reaplicado con la herramienta de edición. **Nunca usar `Set-Content` sobre fuentes Kotlin.**

---

## Riesgos pendientes

| # | Riesgo | Severidad | Mitigación |
| :--- | :--- | :--- | :--- |
| 1 | **Contabilidad histórica de R9/R10 posiblemente afectada** por la resolución de operaciones en dominio de píxel (T3). No se corrige retroactivamente. | **Alta** | Tratar todo `WIN/LOSS` anterior a `d54ec03` como no fiable. Re-empezar la cuenta de confirmación desde cero. |
| 2 | **P&L del HUD es una estimación** con payout configurado (0.85) frente al real de Binomo (82 % en pantalla). No hay lectura del payout desde la app. | Media | Mostrar el P&L como aproximado, o leer el payout por visión/OCR. |
| 3 | **Posición vertical de la línea de strike aproximada** en headless: el eje de precios no está en el árbol de accesibilidad (verificado, 29 108 bytes de dump sin etiquetas de precio). | Media | OCR del eje por visión. Afecta **solo al dibujo**; el cálculo ITM/OTM y el P&L usan precios. |
| 4 | **Crypto IDX tiene un feed de rango sub-micro** (2.5e-10 relativo medido en vivo). Cualquier métrica normalizada por el ancho de canal (T7) o por bps (T5) es inestable en ese activo. **Agravado tras la medición de T9:** el filtro S/R y el filtro anti-chop pueden declararse satisfechos A LA VEZ pidiendo direcciones opuestas (`clock_03.png`), porque las dos distancias caen bajo su umbral simultáneamente al ser el ATR normalizador del orden del ruido del feed. | **Alta** | Ya corregido en T5/T7 **a nivel de presentación**: el HUD lo declara con `⚠ CONFLICTO S/R↔PLAN`. **La contradicción en el motor NO se corrige** (exigiría tocar umbrales de proximidad, excluido por el enunciado de R11). Antes de confiar en Crypto IDX para decisiones reales: auditar todas las divisiones por rango/ATR del motor y añadir un veto de exclusividad mutua entre ambos filtros. |
| 5 | **Muestra insuficiente para validar.** Conjunto de confirmación post-T0: N = 15 ≪ 200 preregistrados. | Alta | No se puede afirmar que la corrección mejore el rendimiento. Cualquier conclusión de edge sigue siendo "no demostrado". |
| 6 | **Columna `price_y` del journal contiene precios.** Nombre engañoso que ya causó una falsa alarma en T2. | Baja | Documentado; renombrar en una migración futura, no ahora. |
| 7 | **Discrepancia stake HUD ($1.0) vs broker (Col$100,000)** sin explicar en la interfaz. | Baja | Etiquetar el stake como "unidades internas" o eliminarlo del HUD. |
| 8 | El `Vela [1m]` del HUD y el `Hasta expiración` de Binomo son relojes **distintos**; con desfase de reloj de ~1 s pueden parecer contradictorios. | Baja | Ya rotulados de forma distinta. Medido: desfase constante de 1 s, sin deriva. |
| 9 | **Toques en la cabecera del HUD se filtraban a Binomo** y cambiaban el entorno (abrieron el menú de timeframe y dejaron el gráfico en 5m, además del panel del Agente IA y `MainActivity`). Causa: contenido desbordado fuera del área de la ventana. | **Alta** | Corregido en `2ffebe9` (cabecera con peso + elipsis). **Sin re-verificar en dispositivo.** Mientras no se confirme, cualquier toque cerca de la cabecera del HUD puede alterar el gráfico y **invalidar el análisis de velas en curso**. |
| 10 | **El aviso `⚠ CONFLICTO S/R↔PLAN` solo etiqueta, no veta.** No hay comprobación de que el conflicto impida el disparo. | **Alta** | Añadir un veto real (exclusividad mutua entre filtro S/R y anti-chop) antes de operar en real. Cae fuera del enunciado de R11 (tocaría umbrales). |
| 11 | **`hud_alpha = 0.33` deja pasar el gráfico de Binomo sobre las líneas de micro-velocidad y stats.** El drawable del panel es 90 % opaco (`#E6070A14`), pero el alpha global se aplica también al **texto**. | Media | Separar la opacidad del fondo de la del texto, o fijar un mínimo de alfa para el texto. El tester lo documentó con capturas. |
| 12 | **Sin verificación visual de T3, T4-parcial y T6b-parcial.** No hubo operación abierta en la sesión de prueba, así que la línea de strike/ITM/OTM/P&L nunca se pintó. | **Alta** | Repetir la sesión de prueba consiguiendo al menos una operación abierta. Están cubiertos por tests unitarios (`StrikeCalculatorTest`, `HudSnapshotTest`), pero **no observados en pantalla**. |

---

## Criterios de aceptación

| Criterio | Estado | Evidencia |
| :--- | :--- | :--- |
| Sin contaminación entre activos | **PASA** | `TickFrameParserTest` (12 tests) + contador `⛔ ajenos:` en HUD + auditoría T2 (126 181 ticks, 0 contaminados) |
| Micro-velocidad con signo y flecha coherentes | **PASA** | `after_21.png`: `-1.18e-08 %/s ▼ Bajista`; `TickVelocityLabelTest` (6 tests) |
| Countdown real, sin `Liquidando en vela :00s` | **PASA** | `after_21.png`: `Vela [1m] :31s` |
| P&L real, no `+$$$` | **PASA** | Código + `StrikeCalculatorTest` (18 tests) |
| Stats con total resuelto | **PASA** | `after_21.png`: `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0` |
| HUD sin contradicciones S/R↔plan | **PASA** | `after_21.png`: `Toque Directo en Resistencia [PUT Óptimo]` + `gatillo PUT`; `actionConflict` en tests |
| HUD cabe en pantalla y el plegado es visible | **PASA el ancho · el disparador táctil FALLÓ y está corregido, sin re-verificar** | `dumpsys window`: `(48,50)(624xwrap)` sobre 720 px (ancho OK). El probador probó que el chip no respondía a 6 coordenadas y halló la causa (cabecera sin peso); el fix compila y pasa tests, pero **no hay captura de cierre** |
| Sin datos inventados sin tick fresco | **PASA (parcial)** | `HudSnapshotTest` cubre el caso; en dispositivo no se pudo provocar caída del feed (nunca se degradó) |
| Tests verdes | **PASA** | `217 passed, 0 failed, 2 skipped` |
| Build limpio | **PASA** | `BUILD SUCCESSFUL` |
| Verificación visual en dispositivo | **PASA con salvedades** | `loops/screenshots/r11/` + `REPORTE_TESTER.md`. T3, T4-parcial y T6b-parcial: NO VERIFICABLES (sin operación abierta). T8-2ª iteración: pendiente |

---

## Cierre — T2 y el efecto sobre R9/R10

**T2 no permite absolver ni condenar R9/R10.** Lo confirmado es que la vía de contaminación existía en el parser y que está cerrada desde `37f6d32`. Lo **no** confirmado es que esa vía haya causado un daño medible: los 5 feeds almacenados (126 181 ticks) no contienen ni un solo tick contaminado, y ninguno tiene la escala ~1.166 que se vio en pantalla, así que el incidente observado **no quedó registrado** y su impacto no es reconstruible hacia atrás.

El veredicto estadístico de R9/R10 **no cambia**: con payout 0.8091 (break-even 55.28 %) ninguna cota inferior de Wilson alcanza el break-even, antes ni después de excluir las filas expuestas. La conclusión sigue siendo **"sin edge demostrado"**. Y el conjunto de confirmación post-T0 (N = 15 frente a los 200 preregistrados) **no es evaluable**.

Restricción adicional a arrastrar: la contabilidad de resultados anterior a `d54ec03` (T3) **no es fiable**, porque las operaciones se resolvían comparando píxeles con el signo invertido. Eso contamina cualquier recuento de WIN/LOSS previo con efecto directo sobre racha, martingala y sizing. **No se corrige retroactivamente en esta ronda.**

A esto se añade que el probador **no consiguió que se abriera ninguna operación** en ~40 minutos de sesión (`hasPendingTrade: false`, `signalsGenerated: 0`, `totalWins/Losses = 0`). Es coherente con el estado real del sistema —los vetos y la ventana sniper están bloqueando entradas, que es lo que se espera de un sistema sin edge—, pero también significa que **la contabilidad nueva de T3 no se ha ejercitado ni una sola vez en dispositivo**. La primera operación real que se abra será la primera prueba de campo de la resolución por precio.

---

## Entrega

**Commits (directo a `main`, sin PR):**

```
2ffebe9  fix(hud): T8 causa unica de los 3 fallos de layout - cabecera sin peso desbordaba la ventana (R11)
390fc86  docs(r11): medicion del reloj de vela y conflicto S/R real documentado
55d160a  docs(r11): informe final de la ronda 11 con veredictos por tarea, evidencia y riesgos
7ace8c3  fix(hud): T5 etiqueta de micro-velocidad derivada del signo real y formato adaptativo (R11)
f30c530  fix(hud): T7 normalizacion unica de distancia S/R y aviso de conflicto filtro-plan (R11)
f752077  fix(hud): T8 layout del HUD sin desborde, plegado con operacion y sin toast sobre los botones (R11)
267ad0f  fix(hud): T4/T5/T6/T7 HudSnapshot inmutable, coherencia, strike real y countdown (R11)
d54ec03  fix(strike): T3 strike y liquidacion en dominio de precio, ITM/OTM real (R11)
ebe3681  audit(r11): T2 impacto de la contaminacion entre activos sobre R9/R10 (erratum post-hoc)
37f6d32  fix(ws): T1 integridad de datos - parser fail-closed por activo + velocidad/estado por RIC (R11)
```

`PREREGISTRO_R10.md` **no fue modificado** en ningún commit de esta ronda (verificado: su último commit sigue siendo `da15cc9`, de R10).

**APK de depuración (artefacto `TradeDraw-debug` de GitHub Actions):**
https://github.com/Mizaring467/Tradedraw/actions

---

## Qué queda abierto para la próxima ronda

Por orden de importancia:

1. **Verificar en pantalla el fix T8 `2ffebe9`** — sin esto no se sabe si los toques siguen filtrándose a Binomo y alterando el timeframe. Requiere reconectar el dispositivo.
2. **Convertir `⚠ CONFLICTO S/R↔PLAN` en un veto real.** Hoy solo etiqueta. En Crypto IDX el filtro S/R y el anti-chop pueden pedir direcciones opuestas a la vez.
3. **Auditar todas las divisiones por rango/ATR del motor** para el feed sub-micro de Crypto IDX (canal 1.25e-06 sobre precio 641.867 = 2e-9 relativo).
4. **Sesión de prueba con operación abierta** para validar T3 (ITM/OTM y P&L) visualmente.
5. **Separar la opacidad del fondo de la del texto** (`hud_alpha` global a 0.33 hace ilegible el HUD).
6. **Confirmar el payout real** (0.85 configurado frente a 82 % mostrado por Binomo) o marcarlo como aproximado en la interfaz.
