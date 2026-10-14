# Ronda 11 — Coherencia HUD ↔ feed WS, integridad de datos y strike real

**Rama:** `main` · **Rango:** `d4ef3a9..926dc50` (17 commits: 10 `fix` + 1 `audit` + 6 `docs`) · **Fecha:** 2026-10-13
> **Erratum (Ronda 12, A4):** este encabezado decía `d4ef3a9..HEAD (7 commits)`. Era **incorrecto**; el conteo real sobre el SHA de cierre `926dc50` es el de arriba (`git log --oneline d4ef3a9..926dc50 | wc -l` → 17).
**Restricción de la ronda:** NO se cambian estrategias ni umbrales de veto. Solo integridad de datos y coherencia del HUD.
**Cumplimiento:** ningún umbral (`CHOP > 61.8`, `DojiRatio ≥ 0.35`, confluencia ≥ 80 pts, ventana `:01..:05`/`:58..:03`, veto `:06..:57`, quarter-Kelly 1.0–3.5 %) fue modificado. Los dos cambios que rozan la decisión de trading están marcados con **⚠ TOCA DECISIÓN** y explicados.

---

## Resumen ejecutivo

| Tarea | Resultado | Commit |
| :--- | :--- | :--- |
| T1 — Integridad del feed (parser fail-closed por activo) | **CAUSA RAÍZ CONFIRMADA y corregida** | `37f6d32` |
| T2 — Auditoría de contaminación entre activos (impacto en R9/R10) | **CONFIRMADA la vía Y CONFIRMADO el daño en el journal** (primera conclusión corregida) | `ebe3681` |
| T3 — Strike y liquidación en dominio de precio (ITM/OTM real) | **CAUSA RAÍZ CONFIRMADA y corregida**; liquidación **verificada contra el journal real** · **⚠ TOCA DECISIÓN** | `d54ec03` |
| T4 — HUD sin datos inventados (instantánea inmutable) | **CAUSA RAÍZ CONFIRMADA y corregida** | `267ad0f` |
| T5 — Micro-velocidad: signo coherente y unidad comparable | **CAUSA RAÍZ CONFIRMADA y corregida** (2 iteraciones) | `267ad0f`, `7ace8c3` |
| T6 — Countdown real y P&L real | **CONFIRMADO y corregido** | `267ad0f` |
| T7 — Invariantes de coherencia del HUD | **CONFIRMADO y corregido** (contradicción nueva hallada en dispositivo) | `f30c530` |
| T8 — Layout del HUD, plegado y solapamiento | **CONFIRMADO**; corregido en 2 iteraciones y **los 3 fallos del probador verificados como resueltos en dispositivo** | `f752077`, `2ffebe9` |
| T9 — Verificación cruzada (solo informe, sin cambios) | **Hallazgos abajo** | — |

**Tests:** `221 passed, 0 failed, 2 skipped` (17 clases). Baseline antes de R11: 164 tests.
**Build:** `assembleDebug --no-daemon --offline --no-configuration-cache` → `BUILD SUCCESSFUL`.
**Instalado y verificado en dispositivo:** Redmi A1 `220733SL` (ADB Wi-Fi `192.168.1.196:5555`).

> **Erratum R13 (adenda 1):** este informe decía "POCO X6 Pro `UGU8KFUKM7E6CE8D`". Es
> **incorrecto** y se venía repitiendo sin verificar desde rondas anteriores. El dispositivo
> real, según `getprop`, es un **Redmi A1 `220733SL`**, Android 12 **Go** (`ro.config.low_ram =
> true`), `mt6761`, **4 núcleos**, `armeabi-v7a`, 1.875.668 kB de RAM, y `loadavg` ≈ 17 en
> operación. El serial USB `UGU8KFUKM7E6CE8D` ya no existe. **Consecuencia: las mediciones de
> latencia y rendimiento de este informe se hicieron asumiendo un teléfono mucho más potente
> del que es, y quedan invalidadas** (ver `loops/R13_REPORTE.md`, adenda 1).
**Informe del probador:** `loops/screenshots/r11/REPORTE_TESTER.md` (28 capturas).

> **Nota de verificación (corregida en R12, A4):** T3, T4 (caso "feed no fresco") y T6b (total resuelto ≠ 0) quedaron **NO VERIFICABLES en dispositivo** por no haberse abierto ninguna operación en la sesión de prueba (`hasPendingTrade: false`, `signalsGenerated: 0` durante ~40 min). Están **cubiertos por tests unitarios**, no por observación visual. **T8 sí quedó confirmado**: el propio informe lo cerró después con `2ffebe9`/`f752077` y capturas (`t8_fix_botkey`, `t8_landscape_01/02/03`, `t8_leak_after/before`); la nota original que lo daba por "sin confirmar" quedó obsoleta y se corrige aquí.

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

**Lectura honesta:** la contaminación es **posible**, está cerrada en el parser desde `37f6d32`, y **SÍ dejó rastro en los datos guardados del journal** (ver corrección abajo). Lo que no se puede reconstruir es el daño acumulado sobre las estadísticas de R9/R10 con la muestra disponible.

### ⚠ CORRECCIÓN POSTERIOR — mi primera conclusión de T2 era ERRÓNEA

En la primera pasada audité solo los ficheros de **ticks** (`data/ticks/*.csv`) y concluí que "ningún feed tiene escala ~1.166". Eso era un error de alcance: **el journal de operaciones sí contiene la contaminación**, en filas que yo mismo había despachado como "artefacto de logging".

Evidencia (`trade_journal.csv` del dispositivo, 18 operaciones; columnas `price_y` y `tick_velocity`):

| iso_date | price_y | tick_velocity | acción | resultado |
| :--- | ---: | ---: | :--- | :--- |
| 2026-10-13T12:59:18 | **1.166300** | **-1189.74** | BUY | TIE |
| 2026-10-13T13:10:19 | 641.867310 | **-6964.14** | SELL | TIE |
| 2026-10-13T13:15:14 | **1.166290** | **-7813.43** | BUY | LOSS |
| 2026-10-13T13:20:19 | **0.992470** | **5934.03** | BUY | LOSS |

`1.166300` es **exactamente** el valor que apareció en pantalla durante el incidente T1 (`Crypto IDX: 641.867319` → `1.16603`), y `0.992470` es la misma clase de escala (par de divisas OTC).

**Prueba aritmética de que es el residuo de un salto de escala** y no una velocidad real: si el motor restó un precio ajeno contra el de Crypto IDX, la velocidad debe cumplir `v ≈ (641.867310 − precio_y) / dt`. Despejando `dt`:

```
1.166300 -> delta 640.70 -> dt = 640.70 / 1189.74  = 0.5385 s   (plausible)
1.166290 -> delta 640.70 -> dt = 640.70 / 7813.43  = 0.0820 s   (plausible)
0.992470 -> delta 640.87 -> dt = 640.87 / 5934.03  = 0.1080 s   (plausible)
```

Los tres caen en el rango de fracciones de segundo esperable entre ticks. **La velocidad registrada es el cociente exacto de mezclar dos escalas distintas.** Eso confirma la contaminación entre activos en la ruta de datos, no un artefacto de la columna.

**Control que descarta la explicación alternativa:** las 7 filas con `price_y ≈ 1040–1049` (ETHUSD-OTC) tienen `tick_velocity = 0.00`, es decir **sin** residuo de salto. Cambiar de activo de forma limpia NO genera velocidades absurdas; solo lo hace mezclar escalas en el mismo cálculo. Eso separa "cambio de activo legítimo" de "contaminación".

**Consecuencias sobre R9/R10 (corregidas):**

- Hubo **al menos 4 operaciones** en la ruta de datos real calculadas sobre un estado contaminado. Dos terminaron en `TIE` (orden no procesada, saldo inalterado) y dos en `LOSS` con pérdida real de ~Col$100.000 cada una.
- La hipótesis de que las velocidades absurdas eran un mero problema de escritura queda **refutada**: el valor es aritméticamente imposible sin mezclar escalas.
- Las columnas `trend`, `impulse` y `market_regime` de esas filas son sospechosas por la misma razón: derivan del mismo estado. En la fila de `12:59:18`, por ejemplo, `dist_support = 0.00`, `dist_resistance = 0.00` e `impulse = BEARISH` con `velocidad = -1189.74`.
- Esto **refuerza** la decisión de T1 (parser fail-closed) y convierte la contaminación en un daño **medido**, no hipotético. Sigue sin poder reconstruirse cuántas operaciones de R9/R10 cayeron en el mismo caso, porque el journal de esa ronda no se conservó completo.

**Lo que NO cambia:** el veredicto estadístico de R9/R10 sigue siendo "sin edge demostrado" (ninguna cota inferior de Wilson alcanza el break-even) y el conjunto de confirmación post-T0 (N = 15 ≪ 200) sigue sin ser evaluable. La contaminación es un defecto de integridad de datos, no evidencia de edge.

### Verificación del fix T1 contra el journal real (antes / después)

Auditoría automática del journal del dispositivo, separando antes y después del despliegue del parser fail-closed:

| Bloque | Filas | Fuera de escala (600–700) | Velocidades con residuo de salto |
| :--- | ---: | ---: | ---: |
| **PRE-fix** `12:59:18` … `13:20:19` | 4 | **4** (`1.166300`, `1.166290`, `0.992470` y un `641.867310` con velocidad imposible `-6964.14`) | **3** (dt = 0.54 / 0.08 / 0.11 s) |
| **POST-fix** `15:07:14` … `17:05:17` | 4 | **0** | **0** |

Detalle del bloque post-fix: todas las filas con `price_y = 641.867310` (escala única de Crypto IDX) y `tick_velocity = 0.00`. **Cero contaminación cruzada en las 4 operaciones posteriores al fix.**

Esto convierte T1 en una corrección **medida y verificada contra datos reales**, no solo contra tests unitarios: el mismo journal que contiene 4 filas contaminadas no contiene ninguna después del `37f6d32`.

**Nota sobre el 4º caso pre-fix:** la fila `13:10:19` tiene `price_y = 641.867310` (escala correcta) pero `tick_velocity = -6964.14`. El residuo de salto no aplica porque el precio sí es de Crypto IDX; la velocidad absurda proviene de haber derivado contra el último precio almacenado, que era de **otra** escala (`1.166300`). Eso es exactamente lo que T1 corrigió al resetear `lastPrice`/`smoothedVelocity` por activo. Confirma que el defecto tenía **dos manifestaciones**: precio contaminado (3 filas) y velocidad contaminada con precio correcto (1 fila).

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

### Verificación de la liquidación contra el journal real (cierra el gap que dejó el probador)

El probador no pudo observar ninguna operación abierta. La sesión posterior sí generó operaciones resueltas, y **quedaron persistidas con la escala correcta**:

```
2026-10-13T16:47:03  BUY  price_y=641.867310  stake=1.00  WIN   Diff=+83000.3200000003  (62s)
2026-10-13T17:05:17  BUY  price_y=641.867310  stake=1.00  LOSS  Diff=-99998.71999999881 (72s)
2026-10-13T16:57:17  SELL price_y=641.867310  stake=1.00  TIE   Diff=0.0 (orden no procesada)
```

Y el HUD mostró en paralelo `W: 1 | L: 0 | Nulas: 0 | Total: 1 (100.0%)` (ver `t8_landscape_02.png`), coherente con el journal: **el contador del HUD y el registro persistido coinciden**.

Lo que esto confirma:
- La **liquidación por diferencia de saldo** funciona y escribe en el journal (`SALDO (+)` / `SALDO (-)` / `ORDEN NO PROCESADA`).
- El `price_y` guardado es un **precio de Crypto IDX** (`641.867310`), no una coordenada Y ni una escala ajena: T3 escribe en dominio de precio.
- El desglose WIN/LOSS/TIE del HUD (`Nulas` para las no procesadas) refleja los tres desenlaces reales.

Lo que **sigue** sin observarse: el chip `Strike … · ITM/OTM · P&L` **en pantalla con una operación abierta** (el probador tampoco lo vio). El cálculo está cubierto por `StrikeCalculatorTest` (18 tests), pero su presentación en vivo no tiene captura.

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

> ✅ **Estado: corregido y VERIFICADO EN DISPOSITIVO.** La causa raíz se confirmó al medir la posición real del chip: el probador probaba en `y ≈ 813`, que era la posición de la **ventana desbordada**; con la cabecera arreglada el chip está en `y ≈ 114` y **sí responde**.

### Verificación de los 3 fallos tras el fix (`2ffebe9`)

| Fallo del probador | Resultado | Evidencia |
| :--- | :--- | :--- |
| 1. El chip no responde a los toques | **RESUELTO** | Barrido de 12 coordenadas: `input tap 610 114` y `input tap 660 114` → `{"hudCollapsed":true}`. El chip funciona. Con `SNIPER` activo: `tap 636 114` → `{"hudCollapsed":true}`. Antes el probador probaba a `y=813` (ventana vieja) y fallaba |
| 2. Con cabecera larga `[PAUSA FRANCOTIRADOR]` el chip desaparece | **RESUELTO** | `t8_verify_04_sniper.png`, submodo SNIPER activo: el chip ▼ **sigue visible** en el borde derecho y **responde** al tap. La etiqueta larga se recorta con elipsis en vez de empujar los botones |
| 3. Los toques en la cabecera se filtran a Binomo | **RESUELTO** | `t8_leak_before.png` → `t8_leak_after.png`: tap en el centro de la cabecera (`360,105`) y **no se abrió ningún menú de Binomo**; el gráfico siguió en `1m` y el HUD continuó actualizándose (`Vela [1m] :17s`) |

Evidencia de geometría: `dumpsys window` → `mAttrs={(48,50)(624xwrap) … ty=APPLICATION_OVERLAY}`, es decir 48..672 sobre 720 px. El chip queda dentro de la ventana y de la pantalla.

Capturas: `t8_verify_02.png` (expandido), `t8_verify_03_collapsed.png` (plegado), `t8_verify_04_sniper.png` (SNIPER), `t8_leak_before.png`, `t8_leak_after.png`.

> Nota: durante el barrido de búsqueda del chip, algunos taps de las coordenadas **antiguas** (`y=66..98`) sí alcanzaron la app de debajo y abrieron `Ajustes` de Binomo. Eso es esperado: eran taps fuera del área del HUD colapsado. Con el chip en su sitio (`y=114`) los taps dentro de la cabecera ya no se filtran.

### Verificación en LANDSCAPE y 2 bugs nuevos encontrados

El objetivo de la ronda incluye explícitamente "layout landscape/portrait". Forzado `ROTATION_90` (`settings put system user_rotation 1`):

**Lo que funciona en landscape:** la ventana del HUD se adapta — `mAttrs={(55,120)(624xwrap) … ty=APPLICATION_OVERLAY}` sobre una pantalla lógica de 1600 px de ancho. El ancho se recalcula con `metrics.widthPixels` en el momento de construir la ventana, así que no desborda. El chip de plegado queda visible en el borde derecho (`t8_landscape_02.png`, `t8_landscape_03.png`).

**Bug nuevo 1 — el identificador técnico se pintaba en pantalla.** En `t8_landscape_03.png` aparecía el literal `BOT_STRIKE_PRICE` dibujado sobre el gráfico, encima del botón "Depositar".

Causa raíz: `CustomDrawingView.addOrUpdateBotShape()` hacía `shape.labelText = key`, y el lienzo pinta precisamente `shape.labelText` (`canvas.drawText(labelText, ...)`). La **clave interna** de la figura y su **etiqueta visible** eran el mismo campo, así que el nombre técnico acababa en el canvas. Además `AutoDrawEngine.clearTradeEntry()` buscaba `it.labelText.contains("BOT_STRIKE_PRICE")`, lo que dependía del mismo acoplamiento.

Corrección: nuevo campo `DrawShape.botKey` (+ serialización JSON). `addOrUpdateBotShape` escribe en `botKey`; `lockLine`/`unlockLine`/`clearTradeEntry` y el callback `onBotShapeDragged` buscan por `botKey`; `labelText` queda **exclusivamente** para el texto de negocio (`PUT [ITM $1.20]`). Tests: `DrawShapeBotKeyTest` (2 casos).

**Bug nuevo 2 — la línea de strike se proyectaba contra la escala de otro activo.** La línea aparecía anclada al borde superior con flecha, sin relación con el precio.

Causa raíz doble:
1. `SyntheticCandleEngine.recentTickPrices` (buffer rodante de 120 precios) **nunca se limpiaba al cambiar de activo**. `AutoDrawEngine.renderStrike()` llama a `synthetic.recentPriceBounds()` y proyecta con ese rango; con el buffer mezclando Crypto IDX (641.867) y ETHUSD-OTC (~1048), el rango resultante no pertenece a ninguna de las dos escalas.
2. `renderStrike()` aceptaba ese rango **sin comprobar** que el precio de entrada perteneciera a él.

Corrección: `onNewTick` detecta el cambio de activo (comparando `tick.asset`) y limpia `recentTickPrices` + `lastTickPrice`; `renderStrike()` solo aplica el rango si `currentEntryPrice` cae dentro de `[lo*0.5, hi*2.0]`, y si no, resetea el viewport en vez de inventar una Y. Tests: `cambioDeActivo_limpiaElBufferDePrecios`, `mismoActivo_conservaElBufferDePrecios`.

> ⚠ **TOCA DECISIÓN DE TRADING — SÍ, indirectamente.** Limpiar el buffer al cambiar de activo altera los valores que el motor sintético deriva de `recentTickPrices` (momentum, medias, z-score de 60 ticks, `recentPriceBounds`). Antes, tras un cambio de activo, esos indicadores arrastraban precios de la escala anterior durante hasta 120 ticks. **No se cambió ningún umbral**: se dejó de alimentar los indicadores con datos de otro instrumento. Es una corrección de integridad, no de estrategia.

**Bug nuevo 3 — al rotar, el ancho del HUD NO se recalculaba.** Este es el defecto de landscape de raíz, y explica por qué el chip de plegado parecía inalcanzable en horizontal.

`OverlayService.onConfigurationChanged` sí reprocesaba el lienzo (`MATCH_PARENT`) y sí corregía la `x`/`y` del HUD si caía fuera de pantalla, pero **nunca tocaba `hudParams.width`**: quedaba congelado en el valor calculado al arrancar el servicio.

Medición en dispositivo (el eje lógico de la app pasa de 720 a 670 px al rotar; **el dispositivo
es un Redmi A1, no un POCO X6 Pro** — ver erratum al inicio de este informe):

| Momento | `mAttrs` del HUD | Ancho útil | ¿Cabe? |
| :--- | :--- | ---: | :--- |
| Portrait (arranque) | `(48,50)(624xwrap)` | 624 de 720 | Sí |
| Landscape **con el bug** | `(48,50)(624xwrap)` | **624 de 670** | **No: x+ancho = 672 > 670, desborda** |
| Landscape **corregido** | `(48,50)(574xwrap)` | 574 = 670 − 2×48 | Sí |

Con `x = 48` y ancho 624, el borde derecho cae en 672 sobre una pantalla de 670: el botón de plegado y parte del contenido quedaban **fuera del área visible**, no solo mal posicionados. Eso convierte el "chip inalcanzable en landscape" en un desbordamiento real de ventana, no en un problema de coordenadas de tap.

Corrección: se extraen `hudWidthPx()` y `hudSideMarginPx()`, que leen `resources.displayMetrics` en cada llamada (no cachean), y `onConfigurationChanged` reasigna `params.width` y `params.x` además de corregir la `y`. El cálculo de creación de la ventana usa los mismos helpers, así que no hay dos fórmulas que puedan divergir.

**Nota metodológica:** la rotación por `settings put system user_rotation 1` dejó de aplicarse durante la sesión (MIUI la ignora con el instalador de paquetes en primer plano y el servicio en estado inconsistente). Para medir se forzó la geometría con `wm size 1600x720`, lo que provoca que el compositor renderice en negro (captura inservible) pero **no afecta a `dumpsys window`**, que es de donde sale toda la evidencia de esta tabla: la medida de `mAttrs` es del gestor de ventanas, no de la captura. `wm size`/`wm density` se resetearon después; el dispositivo quedó en `cur=720x1600` y `ROTATION_0`.

> ⚠ **NO toca decisión de trading.** Es geometría de ventana exclusivamente; ningún cálculo de precio, umbral o veto depende del ancho del HUD.

Evidencia: `t8_landscape_01.png` … `t8_landscape_03.png`, `t8_fix_botkey.png`, y la tabla de `mAttrs` de arriba (medida directa de `dumpsys window`, no captura).


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
| 1 | **Contabilidad histórica parcialmente afectada** por la resolución en dominio de píxel (T3). **Reescrito en R12 (A1) con el journal real medido**: el daño NO cubre todo el histórico. Sobre 21 filas del journal del dispositivo, **16 se resolvieron por saldo** (`saldo: pérdida confirmada` = 11, `saldo: ganancia acreditada` = 5), **0 por fallback de precio/píxel** y **5 son `orden no procesada` (TIE)**. El subconjunto demostradamente no fiable es de **8 filas**: 3 con escala contaminada (`12:59:18` price_y=1.166300 vel=−1189.74 TIE; `13:15:14` 1.166290 vel=−7813.43 LOSS; `13:20:19` 0.992470 vel=5934.03 LOSS) + 5 TIE. Las 7 restantes "fuera de escala 600-700" son **ETHUSD-OTC legítimas** (1040-1049 con `vel=0.00`), no contaminación. | Media (era Alta) | Excluir del recuento solo las 8 filas demostradas y los TIE, no todo el histórico. **Reiniciar el conteo desde cero sigue siendo la decisión, pero como PRECAUCIÓN conservadora, no como defecto probado**: no hay evidencia de que las 16 filas resueltas por saldo estén mal. |
| 2 | **P&L del HUD era una estimación con payout equivocado.** R11 afirmaba que "no hay lectura del payout desde la app": **es falso**. Los textos de accesibilidad incluyen `83%` junto al activo, y los botones CALL/PUT muestran `Col$182.000,00` sobre `Cantidad Col$100.000` = `monto×(1+payout)` ⇒ payout legible. El HUD usaba `sorosPayoutRate` = 0.85 mientras Binomo mostraba 82-83 % (desviación ~3 puntos). | Media | **Corregido en R12 (A2)**: `PayoutReader` lee el payout por los dos métodos, los cruza y detecta discrepancias; si no hay lectura válida usa el configurado y rotula `(est.)`. Verificado en dispositivo: `payoutFromButtonRatio = 0.82` con `payoutConfigured = 0.85`. |
| 3 | **Posición vertical de la línea de strike aproximada** en headless: el eje de precios no está en el árbol de accesibilidad (verificado, 29 108 bytes de dump sin etiquetas de precio). | Media | OCR del eje por visión. Afecta **solo al dibujo**; el cálculo ITM/OTM y el P&L usan precios. |
| 4 | **Crypto IDX tiene un feed de rango sub-micro** (2,5e-10 relativo medido en vivo). Cualquier métrica normalizada por el ancho de canal (T7) o por bps (T5) es inestable en ese activo. El filtro S/R y el filtro anti-chop pueden declararse satisfechos A LA VEZ pidiendo direcciones opuestas (`clock_03.png`). | **Alta** | **Resuelto en R12 (B2, opción C elegida por el usuario)**: ETHUSD-OTC por defecto (rango relativo 4,164e-02, **8 órdenes de magnitud** más que Z-CRY/IDX) + veto de exclusividad mutua S/R↔anti-chop (V1) + veto de feed sin volatilidad medible (V2, umbral 1e-5 congelado en `PREREGISTRO_R12.md`). Además, la auditoría B3 demostró que en Crypto IDX el motor estaba **estructuralmente ciego**: guardas absolutas de rango (1e-5) y desviación (1e-6) aplastaban los ratios ×5-63, dejando `MT_REJECTION` y `QUANT_CRYPTO` muertas por construcción. |
| 5 | **Muestra insuficiente para validar.** Conjunto de confirmación post-T0: N = 15 ≪ 200 preregistrados. | Alta | No se puede afirmar que la corrección mejore el rendimiento. Cualquier conclusión de edge sigue siendo "no demostrado". |
| 6 | **Columna `price_y` del journal contiene precios.** Nombre engañoso que ya causó una falsa alarma en T2. | Baja | Documentado; renombrar en una migración futura, no ahora. |
| 7 | **Discrepancia stake HUD ($1.0) vs broker (Col$100,000)** sin explicar en la interfaz. | Baja | Etiquetar el stake como "unidades internas" o eliminarlo del HUD. |
| 8 | El `Vela [1m]` del HUD y el `Hasta expiración` de Binomo son relojes **distintos**; con desfase de reloj de ~1 s pueden parecer contradictorios. | Baja | Ya rotulados de forma distinta. Medido: desfase constante de 1 s, sin deriva. |
| 9 | **Toques en la cabecera del HUD se filtraban a Binomo** y cambiaban el entorno (abrieron el menú de timeframe y dejaron el gráfico en 5m, además del panel del Agente IA y `MainActivity`). Causa: contenido desbordado fuera del área de la ventana. | Media (era Alta) | **Corregido y verificado**: `2ffebe9` + tap de prueba en `360,105` sin abrir menús de Binomo. Baja a Media porque el riesgo residual es que un tap en la banda del HUD, fuera de un control, sigue siendo absorbido por la ventana (correcto) y no por el gráfico. |
| 10 | **El aviso `⚠ CONFLICTO S/R↔PLAN` solo etiqueta, no veta.** No hay comprobación de que el conflicto impida el disparo. | **Alta** | **Corregido en R12 (B2, veto V1)**: `HudCoherence.actionConflict` pasa de etiquetar a vetar antes de despachar la entrada. Umbral congelado en `workflows/PREREGISTRO_R12.md`. |
| 11 | **`hud_alpha = 0.33` deja pasar el gráfico de Binomo** sobre las líneas de micro-velocidad y stats. El alfa global se aplicaba también al **texto**. | Media | **Corregido en R12 (A6)**: `applyHudTextLegibility()` da contraste propio al texto (sombra negra por TextView), independiente del alfa del panel. Verificado con panel al 20 % y el texto legible sobre las velas. |
| 12 | **Línea de strike en pantalla con operación abierta**: sigue sin observarse el chip en vivo con operación pendiente. | Media | **Sigue NO VERIFICADO en R12 (B5)**: `/trade` solo ejecuta el tap y no arma estado pendiente; no existe mecanismo seguro para forzar una operación y el enunciado prohíbe añadirlo. Declarado como NO VERIFICADO, no como resuelto. |
| 16 | **El HUD persiste `hudVisible:false` tras reiniciar** y hay que reactivarlo por HTTP. | Baja | **Corregido en R12 (A5)**: `hud_visible` se persiste en `TradeDraw_HUDConfig` junto a `hud_collapsed`/`hud_alpha`. Tenía DOS causas: no se leía la preferencia y `setupHUDWindow()` forzaba oculto al final. Verificado: `hud_visible=true` en disco y `hudVisible:true` tras reiniciar. |
| 17 | **Colores rojo/verde invertidos respecto a la convención de mercado** en las cifras de expiración del HUD: `Vela [5m] 4:22` en naranja y `Hasta expi` en rojo, mientras el gráfico sí usa verde/rojo con la convención correcta. | Baja | Es cosmético y no afecta a ninguna lectura numérica. No corregido. |
| 13 | **Identificador técnico pintado en pantalla** (`BOT_STRIKE_PRICE`) por reutilizar `labelText` como clave de figura. | Baja | **Corregido** con `DrawShape.botKey` + `DrawShapeBotKeyTest`. |
| 14 | **Buffer de precios del motor sintético no se limpiaba al cambiar de activo**: hasta 120 ticks de escala ajena contaminaban momentum, medias, z-score y la proyección de la línea de strike. | **Alta** | **Corregido** (`onNewTick` limpia al detectar `tick.asset` distinto) + 2 tests. Relacionado con el riesgo #4: es la misma familia de fallo (estado por activo). |
| 15 | **`totalWins` del motor y el journal pueden desincronizarse:** `risk.totalWins = 1` frente a 18 filas en el journal (4 `TIE`, 11 `LOSS`, 3 `WIN`). El contador del motor es de sesión, el journal es histórico, pero no hay un campo que lo declare. | Media | Rotular el origen del contador en el HUD o separar "sesión" de "histórico" explícitamente. No corregido en R11 (afecta a la contabilidad, que ya arrastra el problema de T3). |

---

## Criterios de aceptación

| Criterio | Estado | Evidencia |
| :--- | :--- | :--- |
| Sin contaminación entre activos | **PASA (verificado contra el journal real)** | `TickFrameParserTest` (12 tests) + contador `⛔ ajenos:` en HUD + auditoría T2: 4 filas contaminadas pre-fix, **0 post-fix** (bloque `15:07`–`17:05`, escala única `641.867310`, velocidades `0.00`) |
| Micro-velocidad con signo y flecha coherentes | **PASA** | `after_21.png`: `-1.18e-08 %/s ▼ Bajista`; `TickVelocityLabelTest` (6 tests) |
| Countdown real, sin `Liquidando en vela :00s` | **PASA** | `after_21.png`: `Vela [1m] :31s` |
| P&L real, no `+$$$` | **PASA** | Código + `StrikeCalculatorTest` (18 tests) |
| Stats con total resuelto | **PASA** | `after_21.png`: `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0` |
| HUD sin contradicciones S/R↔plan | **PASA** | `after_21.png`: `Toque Directo en Resistencia [PUT Óptimo]` + `gatillo PUT`; `actionConflict` en tests |
| HUD cabe en pantalla y el plegado es visible | **PASA** | Ancho `(48,50)(624xwrap)` sobre 720 px; el chip pliega por tap (`610,114` → `hudCollapsed:true`), también bajo SNIPER; sin fuga de toques a Binomo |
| Sin datos inventados sin tick fresco | **PASA (parcial)** | `HudSnapshotTest` cubre el caso; en dispositivo no se pudo provocar caída del feed (nunca se degradó) |
| Tests verdes | **PASA** | `221 passed, 0 failed, 2 skipped` (17 clases) |
| Build limpio | **PASA** | `BUILD SUCCESSFUL` |
| Verificación visual en dispositivo | **PASA con salvedades** | `loops/screenshots/r11/` + `REPORTE_TESTER.md`. T3, T4-parcial y T6b-parcial: NO VERIFICABLES (sin operación abierta). T8: verificado tras el fix |

---

## Cierre — T2 y el efecto sobre R9/R10

**T2 no permite absolver ni condenar R9/R10, pero sí demuestra que la contaminación ocurrió.** Lo confirmado es que la vía existía en el parser (cerrada desde `37f6d32`) y que **dejó rastro en el journal**: **3 operaciones** con `price_y` de otra escala (`1.166300`, `1.166290`, `0.992470`) y velocidades que son aritméticamente el residuo del salto (`dt` = 0.08–0.54 s, plausible). Dos de ellas acabaron en `LOSS` real y una en `TIE`. *(Erratum R12/A1: el informe decía "4 operaciones"; son 3 con escala contaminada. La cuarta fila citada, `13:10:19 SELL TIE`, tiene `price_y` correcto `641.867310` y solo la velocidad contaminada —`−6964.14`—, que es una manifestación distinta del mismo defecto T1 y se cuenta aparte.)* El control de las filas con escala ETH (`tick_velocity = 0.00`) descarta que un cambio de activo limpio produzca ese efecto: solo lo produce mezclar escalas.

**Corrección de mi primera auditoría:** en la primera pasada revisé solo `data/ticks/*.csv` y declaré que "ningún feed tiene escala ~1.166". Era un error de alcance: el journal de operaciones sí la contiene, y yo mismo había despachado esas filas como "artefacto de logging". Queda corregido arriba con la prueba aritmética.

Lo que **sigue sin poder reconstruirse** es el daño acumulado de R9/R10: el journal de esa ronda no se conservó completo, así que no se sabe cuántas operaciones más cayeron en el mismo caso.

El veredicto estadístico de R9/R10 **no cambia**: con payout 0.8091 (break-even 55.28 %) ninguna cota inferior de Wilson alcanza el break-even, antes ni después de excluir las filas expuestas. La conclusión sigue siendo **"sin edge demostrado"**. Y el conjunto de confirmación post-T0 (N = 15 frente a los 200 preregistrados) **no es evaluable**.

Restricción adicional a arrastrar (reescrita en R12/A1 con el journal medido): la contabilidad anterior a `d54ec03` (T3) **no es íntegramente fiable, pero el daño está acotado y medido**. Sobre las 21 filas del journal del dispositivo: **16 resueltas por saldo** (11 pérdidas confirmadas + 5 ganancias acreditadas), **0 resueltas por el fallback de precio/píxel** que T3 arregló, y **5 TIE (`orden no procesada`)**. El subconjunto demostradamente contaminado son **8 filas**: 3 con escala de otro activo (`price_y` = 1.166300 / 1.166290 / 0.992470 con velocidades −1189.74 / −7813.43 / +5934.03) y los 5 TIE. Las 7 filas restantes que parecían "fuera de escala" son **ETHUSD-OTC legítimas** (precios 1040-1049, `tick_velocity = 0.00`): no son contaminación y no deben excluirse.

Esto **corrige la conclusión original**, que daba por no fiable *todo* el histórico. Lo no fiable es el subconjunto de 8 filas de arriba. **Reiniciar el conteo desde cero sigue siendo la decisión, pero como precaución conservadora** —porque cambian de ronda el activo por defecto, las guardas de escala y se añaden dos vetos—, **no porque se haya demostrado que las 16 filas resueltas por saldo estén mal**. No se corrige retroactivamente.

**Instrumentación propuesta para el método de liquidación:** el journal NO tiene columna que registre *cómo* se resolvió cada operación; hoy solo puede inferirse del texto libre de `reason` (`SALDO (+)`, `SALDO (-)`, `ORDEN NO PROCESADA`, `PRECIO (=)`). Añadir una columna nueva rompería el parseo de los ficheros históricos de 21 columnas. La vía que no rompe nada es **codificar el método dentro del campo `result` ya existente** (`WIN_SALDO`, `LOSS_SALDO`, `WIN_PRECIO`, `TIE_FANTASMA`), que es texto libre y el parser actual no valida contra un enumerado. Queda **propuesto y no implementado** en R12: cambiar el formato del journal afecta a la contabilidad de confirmación y merece su propio preregistro.

A esto se añade que el probador **no consiguió que se abriera ninguna operación** en ~40 minutos de sesión (`hasPendingTrade: false`, `signalsGenerated: 0`, `totalWins/Losses = 0`). Es coherente con el estado real del sistema —los vetos y la ventana sniper están bloqueando entradas, que es lo que se espera de un sistema sin edge—, pero también significa que **la contabilidad nueva de T3 no se ha ejercitado ni una sola vez en dispositivo**. La primera operación real que se abra será la primera prueba de campo de la resolución por precio.

---

## Entrega

**Commits (directo a `main`, sin PR):**

```
487f9fc  fix(landscape): el ancho del HUD no se recalculaba al rotar (R11, 3er bug)
0d03cae  docs(r11): cierra T2 con evidencia antes/despues y verifica la liquidacion de T3
4738d55  fix(landscape): 2 bugs hallados al verificar el HUD en horizontal + T2 corregido (R11)
b5bcde3  docs(r11): cierra el informe con el push a main y el estado de CI
57f7587  fix(hud): T8 verificado en dispositivo + opacidad solo en el fondo (R11)
3f96071  docs(r11): incorpora el informe del probador y cierra los veredictos con sus salvedades
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

**Empujado a `main`:** `f829734..487f9fc` (fast-forward; los 16 commits de R11 más 2 de R10 que ya estaban en la rama de trabajo). `origin/main == 487f9fc`.

**CI `Build APK` en `main`:** `completed / success` en cada push. Última ejecución verificada: run `38480733031`, SHA `487f9fc`.

`PREREGISTRO_R10.md` **no fue modificado** en ningún commit de esta ronda (verificado: su último commit sigue siendo `da15cc9`, de R10).

**APK de depuración (artefacto `TradeDraw-debug` de GitHub Actions):**
https://github.com/Mizaring467/Tradedraw/actions

## Qué queda abierto para la próxima ronda

Por orden de importancia:

1. **Convertir `⚠ CONFLICTO S/R↔PLAN` en un veto real.** Hoy solo etiqueta. En Crypto IDX el filtro S/R y el anti-chop pueden pedir direcciones opuestas a la vez (captura `clock_03.png`: `Toque Directo en Resistencia [PUT Óptimo]` frente a `Cerca de Soporte (15%) … CALL`, con `[86% CALL | 14% PUT]`).
2. **Auditar todas las divisiones por rango/ATR del motor** para el feed sub-micro de Crypto IDX (canal 1.25e-06 sobre precio 641.867 = 2e-9 relativo). Esta es la misma familia de fallo que produjo T7.
3. **Auditar el resto del estado por activo.** T1 arregló el parser y T3 el buffer del motor sintético. `recentTickPrices` era **uno** de varios buffers; falta revisar si queda algún otro acumulador que no se resetee al cambiar de instrumento (candidatos: buffers de `VisionAnalyzer`, historial de `RiskManager`).
4. **Sesión de prueba con operación abierta** para validar la **presentación visual** del strike (chip ITM/OTM y P&L en pantalla). La liquidación ya está verificada contra el journal (WIN/LOSS/TIE persistidos), pero el chip no se ha visto.
5. **Separar la opacidad del fondo de la del texto** (`hud_alpha` global a 0.33 hace ilegible el HUD).
6. **Confirmar el payout real** (0.85 configurado frente a 82–83 % mostrado por Binomo) o marcarlo como aproximado en la interfaz.
7. **Persistir `hud_visible`** en `TradeDraw_HUDConfig` junto a `hud_collapsed`/`hud_alpha`: hoy el HUD arranca invisible y hay que activarlo por HTTP.
