# Ronda 12 — Erratum de R11, operaciones fantasma, payout real y activo por defecto

**Rama:** `main` · **Árbol de código verificado:** `app/` = `391049b4a6d1b85bc8e8a7268bbdb02b359bca9e` · **Fecha:** 2026-10-13
**Rango de la ronda:** `926dc50..c50fbfd`

> **Por qué se ancla al árbol y no a un SHA de commit (lección del defecto A4 que esta ronda corrige):** citar como "SHA final" un commit que deja de ser `HEAD` en cuanto se commitea el propio informe reproduce exactamente el error A4 (verificación anterior al último commit). El hash del árbol `app/` sí es estable: identifica **el código** con independencia de commits posteriores de documentación. La suite se ejecutó sobre `ed88df3`, y `git rev-parse ed88df3:app` = `git rev-parse c50fbfd:app` = `391049b4`, con **0 ficheros de `app/`** distintos entre ambos: la verificación aplica a cualquier commit con ese árbol. Commits de cierre: `ed88df3` (informe + evidencia), `c50fbfd` (SHA/hora), este (anclaje al árbol).
**Restricción respetada:** no se relajó ningún umbral de `master_traders_skill`. `workflows/PREREGISTRO_R10.md` **no se modificó**: los umbrales nuevos viven en `workflows/PREREGISTRO_R12.md`, creado en esta ronda.
**Balance:** 38.014.735,00 → **37.914.736,64** (−99.998,36). Cuenta **demo**. Explicado en "Seguridad" al final: una operación LOSS durante la verificación en dispositivo.
**Auto-trading:** **DISABLED** al cierre, confirmado por HTTP. `hasPendingTrade: false`.

---

## Erratum R11

Seis correcciones. En cada una: qué estaba mal y qué dato lo corrige.

### A1 — El riesgo #1 se contradecía con T3, y la conclusión estaba sobredimensionada

**Qué estaba mal.** El riesgo #1 daba por no fiable **todo** el histórico anterior a `d54ec03` y justificaba reiniciar el conteo "desde cero" como si el defecto estuviera probado en todas las filas. T3 había demostrado lo contrario: la liquidación por precio existía como *fallback*, y la ruta normal era el saldo. Las dos afirmaciones no podían ser verdad a la vez.

**Qué dato lo corrige.** Journal real del dispositivo, **21 filas de datos** (columnas: `timestamp, iso_date, strategy, submode, action, confidence, price_y, stake, base_balance, result, settled_balance, duration_sec, trend, dist_support, dist_resistance, tick_velocity, impulse, market_regime, candle_second, adaptive_status, reason`). `journalError = ''`.

| Método de liquidación | Filas |
| :--- | ---: |
| `saldo: pérdida confirmada` | 11 |
| `saldo: ganancia acreditada` | 5 |
| **Total resuelto por saldo** | **16** |
| Resuelto por fallback de precio/píxel | **0** |
| `sin operacion: orden no procesada` (TIE) | 5 |

Cruce resultado ↔ método: `LOSS × saldo = 11`, `WIN × saldo = 5`, `TIE × sin operacion = 5`. **Cero filas se resolvieron por el fallback de precio que T3 arregló.**

De las 10 filas con `price_y` "fuera de escala 600-700", **7 son ETHUSD-OTC legítimas** (`22:48:04` 1047,02 WIN; `22:52:15` 1048,52 LOSS; `23:19:15` 1040,54 LOSS; `23:35:13` 1038,94 LOSS; `23:38:17` 1038,95 LOSS; `01:18:05` 1048,995 WIN; `01:26:15` 1049,543 LOSS — todas con `vel = 0.00` salvo `23:35:13` con 0,19). **Solo 3 están contaminadas** (`12:59:18` 1.166300 vel −1189,74 TIE; `13:15:14` 1.166290 vel −7813,43 LOSS; `13:20:19` 0.992470 vel 5934,03 LOSS).

**El subconjunto demostradamente no fiable son 8 filas de 21**: 3 contaminadas + 5 TIE. **No todo el histórico.**

**Corregido** en `loops/R11_REPORTE.md`: riesgo #1 reescrito a "Media (era Alta)" con estos números; el `## Cierre` reescrito; y el erratum añadido de que el informe decía "4 operaciones con `price_y` de otra escala" cuando **son 3** (la cuarta, `13:10:19`, tiene `price_y` correcto `641.867310` y solo la velocidad contaminada — manifestación distinta del mismo defecto T1).

**Reiniciar el conteo desde cero:** se mantiene, pero **justificado como precaución conservadora**, no como defecto probado: cambian el activo por defecto, las guardas de escala y se añaden dos vetos, así que las muestras no son comparables. **N = 0** declarado desde `5228bd2`.

**Instrumentación propuesta (no implementada).** El journal **no tiene columna de método**: hoy solo se infiere del texto libre de `reason`. Añadir una columna rompería el parseo de los ficheros históricos de 21 columnas. La vía que no rompe nada es **codificar el método dentro del campo `result` ya existente** (`WIN_SALDO`, `LOSS_SALDO`, `TIE_FANTASMA`), que es texto libre y el parser no valida contra un enumerado. **Propuesto, no implementado**: cambiar el formato del journal afecta a la contabilidad de confirmación y merece su propio preregistro.

### A2 — "No hay forma de leer el payout real desde la app" era falso

**Qué estaba mal.** El riesgo #2 lo afirmaba. El HUD calculaba el P&L con `sorosPayoutRate` (**0.85**, `RiskManager.kt:174`) mientras Binomo mostraba **82 %** y luego **83 %**.

**Qué dato lo corrige.** El payout **sí** es legible por accesibilidad, por **dos** vías: el texto `Crypto IDX 82%` lleva el porcentaje en el **mismo nodo** que el nombre del activo, y los botones CALL/PUT muestran `Col$182.000,00` sobre `Cantidad Col$100.000` ⇒ `182000/100000 − 1 = 0.82`.

El dato **ya estaba llegando al código y se tiraba**: `extractAssetCandidate` (`AutoTradeAccessibilityService.kt:77`) hacía `clean.replace(Regex("(?i)\\s*\\d{1,3}%\\s*"), "")` para quedarse con el nombre y **descartaba el porcentaje**.

**Implementado** (`PayoutReader.kt`, nuevo, objeto puro testeable): los dos métodos por separado, validación de plausibilidad 0.50–1.50, cruce con tolerancia de 2 puntos, marca `discrepant` si no cuadran, y preferencia por la etiqueta declarada por el broker sobre la deducción aritmética. Sin lectura válida se usa el configurado **y el P&L se rotula `(est.)`**.

**Verificado en dispositivo:** `payoutFromPercentLabel = null`, `payoutFromButtonRatio = 0.8200000000000001`, `payoutResolved = 0.82`, `payoutConfigured = 0.8500000238418579`. **La desviación de ~3 puntos era real y está corregida.** `/status` expone los dos métodos por separado para poder auditarlos desde fuera. T9.3 y riesgo #2 corregidos.

**Prueba adicional de que la lectura es VIVA y no un valor fijado:** en una comprobación posterior el broker mostraba **80 %** y el lector devolvía `payoutResolved = 0.8` / `payoutFromButtonRatio = 0.8`, mientras `payoutConfigured` seguía en `0.85`. Es decir: el valor leído **sigue al broker cuando este cambia** (82 % → 80 %) y no es una constante disfrazada. Es exactamente el comportamiento que exige el criterio "el payout leído coincide con el broker".

### A3 — Evidencia citada no estaba en git

**Qué estaba mal.** El informe citaba capturas `after_*` que no estaban commiteadas, más `*.pid`, logs de runtime y `heartbeat.txt`. La evidencia no era verificable por un tercero.

**Corregido en `74bbbc2`:** `.gitignore` ampliado (`loops/journal/live/*.pid`, `*_stdout.log`, `*_stderr.log`, `*_stream.log`, `data/ticks/heartbeat.txt`, `data/ticks/replay_*.csv`); las **69 capturas citadas** versionadas; las **4 mayores de 1 MB** reducidas o excluidas (`after_04`/`after_05` excluidas por no estar citadas; `after_18` recortado a 720×540 = 315 KB; `tester_27_state` reescalado al 70 % = 652 KB); `r9_logcat_stream.log` revertido con `git checkout`; `data/ticks/*` no subido. Dos citas rotas de `REPORTE_TESTER.md` corregidas (`tester_28_relaunch.png` retirada por no existir; `zoom_12_sniper.png` → `zoom_tester_12_sniper.png`).

**Residuo declarado (medido con `git ls-files --others --exclude-standard` al cierre):** **36 PNG** no citados por ningún documento, y **nada más** — el CSV y los dos dumps de runtime pasaron al `.gitignore`, y `zoom_tester_12_sniper.png` se versionó al detectarse que `REPORTE_TESTER.md` lo citaba (`ed88df3`). Los 36 PNG **no se commitean a propósito**: no son evidencia de nada y engordarían el repositorio. No se borran porque son material de origen de rondas anteriores; se declaran aquí en lugar de silenciarlos. **`git status` queda limpio salvo este residuo declarado.**

### A4 — Encabezado y nota de verificación desactualizados; última corrida anterior al último commit

**Qué estaba mal.** (a) El encabezado decía `(7 commits)`; (b) la nota de verificación daba T8 por "sin confirmar en pantalla", pero el propio informe lo cerró después; (c) los tests de las 17:37 eran **anteriores** al último commit de código `487f9fc` (17:53).

**Qué dato lo corrige.** `git log --oneline d4ef3a9..926dc50` → **17 commits** (10 `fix` + 1 `audit` + 6 `docs`), no 7. Corregido el encabezado y la nota (T8 **sí** quedó confirmado con `2ffebe9`/`f752077` y capturas `t8_fix_botkey`, `t8_landscape_01/02/03`, `t8_leak_after/before`).

**Suite re-ejecutada sobre el SHA final:**

| Dato | Valor |
| :--- | :--- |
| **Árbol de código verificado** | `app/` = `391049b4a6d1b85bc8e8a7268bbdb02b359bca9e` (idéntico en `ed88df3` y `c50fbfd`; 0 ficheros de `app/` entre ambos) |
| Commit sobre el que se ejecutó | `ed88df325230fcd7d1ec8027a93cea6223937758` |
| Hora de fin | 2026-10-13 20:36:56 |
| Resultado | **271 passed, 0 failed, 2 skipped (20 clases)** |
| Comando | `gradlew testDebugUnitTest assembleDebug --no-daemon --offline --no-configuration-cache --max-workers=1` |
| Build | `BUILD SUCCESSFUL` |

Baseline antes de R12: 221 tests, 17 clases. Ahora 271 tests, 20 clases (+50 tests).

### A5 — El HUD quedaba invisible tras reiniciar

**Qué estaba mal.** `hudVisible:false` tras reiniciar y había que activarlo por HTTP. `hud_collapsed` y `hud_alpha` sí se persistían; la visibilidad no.

**Qué dato lo corrige.** Tenía **dos** causas, y la segunda apareció al verificar la primera:

1. `setupHUDWindow` no leía `hud_visible` de `TradeDraw_HUDConfig` (solo `hud_alpha` y `hud_collapsed`).
2. **`setupHUDWindow()` terminaba con `v.visibility = View.GONE` e `isHudVisible = false` incondicionales** (`OverlayService.kt:1329-1330`), pisando el valor restaurado unas líneas antes. La preferencia se guardaba y se leía, pero nunca llegaba a aplicarse. Lo demuestra la propia verificación: `hud_visible = true` **en disco** y `hudVisible:false` **en `/status`** tras reiniciar.

**Corregido**, con helper `hudPrefs()` centralizado. **Verificado en dispositivo:**

```
prefs en disco : <boolean name="hud_visible" value="true" />
tras reiniciar : {"success":true,"hudVisible":true,"hudCollapsed":false}
```

Captura: `loops/screenshots/r12/r12_after_a5a6.png`.

### A6 — Legibilidad del HUD sin resolver

**Qué estaba mal.** R11 separó el alfa del fondo (aplicándolo al drawable del panel y no a la vista), pero con el panel translúcido **las velas y los ejes se seguían viendo a través del texto**. Verificado en la captura real: en `after_21.png` la línea de velas atraviesa `Tendencia: LATERAL / RANGO` y `Patrón: RANGO S/R`.

**Qué dato lo corrige.** Separar el alfa era necesario pero **no suficiente**: el problema no es el fondo, es que el texto no tiene contraste propio. Subir el alfa del panel reintroduciría el problema que R11 resolvió.

**Implementado**: `applyHudTextLegibility()` recorre el árbol del HUD y aplica **sombra negra a cada `TextView`**. El panel puede ser tan transparente como quiera el usuario sin perder legibilidad, y no hay que tocar layout ni slider.

**Verificado en dispositivo:** captura `r12_after_a5a6.png` con el panel al **20 %** de alfa (frente al 70 % de R11) y el texto perfectamente legible sobre las velas.

---

## Trabajo nuevo

### B1 — Operaciones fantasma: causa raíz CONFIRMADA

**Hipótesis del enunciado: CONFIRMADA.** La fila TIE correspondía a una operación cuyo tap no llegó. Además queda cuantificada.

**Causa raíz.** `executeHeadlessTrade` llamaba a `riskManager.recordTradeSent(...)` (`TradingEngine.kt:1942`) **antes** de que el tap se confirmara. `recordTradeSent` marcaba `hasPendingTrade = true` incondicionalmente. Resultado: el HUD mostraba `Trade Activo: PUT ▼` con el broker en `Col$0`, y 60 s después, al no haberse movido el saldo, la operación se cerraba como `ORDEN NO PROCESADA`. **El sistema no distinguía "he enviado el tap" de "el broker ha registrado la orden".**

**Evidencia medida** (journal del dispositivo, 212 filas únicas):

| Resultado | Filas | % | Movimiento de saldo |
| :--- | ---: | ---: | :--- |
| TIE | 17 | 8,0 % | **exactamente 0 en las 17** |
| LOSS | 114 | 53,8 % | ≈ −100.000 |
| WIN | 81 | 38,2 % | ≈ +83.000 |

Rachas de TIE consecutivos: **9 rachas**, la **más larga de 5** (detalle: 5, 1, 4, 1, 1, 1, 1, 2, 1).

**Dos razones por las que no puede ser un empate de precio:**

1. En opciones binarias **el stake se debita al abrir**. Un empate con reembolso también habría movido el saldo. Saldo intacto durante toda la operación ⇒ **la orden no existió**.
2. Un empate exacto cinco veces seguidas es inviable: con un generoso `p = 0,02` sale `3,2e-9`. La racha de 5 abarca además varios minutos con el saldo congelado en 44.925.809.

Las 17 filas TIE comparten la firma exacta (`diff = 0.0`), lo que descarta la casualidad.

**Implementado — tres estados** (`RiskManager.OrderState`):

| Estado | Significado | HUD |
| :--- | :--- | :--- |
| `ENVIADA` | tap despachado, sin prueba de registro | `⏳ ENVIADA CALL/PUT · esperando confirmación del broker…` (ámbar) |
| `CONFIRMADA` | el saldo se movió: la orden existe | `🤖 CALL · Strike … · ITM ✓ · P&L …` |
| `NO_PROCESADA` | se agotó la ventana sin movimiento | se cierra el pendiente como VOID de envío |

`hasConfirmedTrade` exige `CONFIRMADA`. **El HUD nunca muestra "Trade Activo" sin confirmación.** `markOrderUnprocessed()` cierra el pendiente, lo que además **libera al motor** en vez de dejarlo bloqueado tras una orden que nunca existió. Lógica pura y testeable: `isOrderConfirmed` / `isOrderUnprocessed`, con margen de 1 COP para el ruido de lectura y fail-closed si el saldo no es legible. `/status` expone `orderState` y `hasConfirmedTrade`.

**NO VERIFICADO en dispositivo:** no se observó una orden entrando en estado `ENVIADA` y pasando a `CONFIRMADA`/`NO_PROCESADA` en vivo. La lógica está cubierta por 12 tests unitarios; la prueba de campo queda pendiente. **No se declara PASA.**

### B2 — Decisión Crypto IDX: opción C, elegida por el usuario

**Datos medidos** (rango relativo del día completo en `data/ticks/`):

| Activo | n | Precio ref. | Rango/vela 60 ticks | Rango relativo | ¿Guardas muerden? |
| :--- | ---: | ---: | ---: | ---: | :--- |
| BTCUSD-OTC | 48.910 | 100.914,1940 | 1,877e+01 | 1,144e-02 | No |
| **ETHUSD-OTC** | 34.260 | **1.072,4260** | **9,430e-01** | **4,164e-02** | **No** |
| SOLUSD-OTC | 32.228 | 32,1150 | 6,000e-02 | 4,665e-02 | No |
| Z-CRY/IDX | 458 | 641,8673 | 3,950e-07 | 2,547e-09 | **Sí** |

Z-CRY/IDX tiene **8 órdenes de magnitud** menos rango relativo que ETHUSD-OTC. Con payout real 0,82 el breakeven es `1/(1+0,82) = 54,95 %`: operar un activo cuyo movimiento cabe en el error de redondeo no es una estrategia.

**`workflows/PREREGISTRO_R12.md` creado**, con los umbrales fijados **antes** de implementar y antes de ver resultados. `PREREGISTRO_R10.md` intacto. **N = 0** declarado desde `5228bd2`.

**Veto V1 — exclusividad mutua S/R ↔ anti-chop.** `HudCoherence.actionConflict` **ya detectaba** la contradicción y se usaba **únicamente para pintar el aviso** `⚠ CONFLICTO S/R↔PLAN` en el HUD (evidencia `clock_03.png`); **la orden se disparaba igual**. Ahora veta antes de despachar la entrada. Sin umbrales nuevos: se reutiliza la detección existente sin relajarla.

**Veto V2 — feed sin volatilidad medible.** Existía un veto por precio congelado con una **excepción explícita para activos `IDX`** (`!currentObservedAsset.contains("IDX")`), que **anulaba el veto justo en el único activo que lo necesitaba**. Se sustituye por un veto **por condición de feed, no por nombre**: `rangoRelativo < 1e-5`. Sin proveedor de rango el motor usa el centinela −1.0, fuera del rango de veto: **no se veta por una medida inexistente**.

**NO VERIFICADO en dispositivo:** la salida de logcat del dispositivo está rota (`Logcat read failure: No such file or directory`), así que no se pudo capturar el mensaje `⛔ VETADO (V2)` durante la ventana autónoma. Cubierto por 10 tests unitarios, incluido el texto **exacto** del caso real de R11 y el valor congelado del umbral. La medición en vivo **sí** apoya la decisión: con la app en `Z-CRY/IDX`, `/status` reportaba `recentPriceRangeRatio = 2,4927e-10`, cuatro órdenes de magnitud por debajo del umbral de 1e-5. **El veto clasificaría ese feed como no operable.**

### B3 — Auditoría de divisiones por rango / ATR / canal

Se auditó toda división cuyo denominador dependa de rango, ATR o ancho de canal. **Tres guardas eran absolutas y aplastaban la escala pequeña**, verificado numéricamente (no por lectura):

| Guarda | Antes | En Z-CRY/IDX (rango real 4e-7) | Después |
| :--- | :--- | :--- | :--- |
| `SyntheticCandle.range` (`:16`) | `coerceAtLeast(0.00001)` | sustituía el rango por **×25-63**; mecha real del 50 % se leía **0,008-0,10** vs umbral 0,45 | relativo a `close` (1e-12) |
| `calculateTickZScore` (`:287`) | `stdDev > 0.000001` | stdDev real ~1e-7 ⇒ **z = 0,0 SIEMPRE** | relativo a `mean` (1e-12) |
| `atrNorm` (`:618`) | `coerceAtLeast(1e-7)` | colapsaba **ambos** ratios de distancia a 1.0 ⇒ `channelWidth >= 0.35` siempre verdadero | relativo a `currentPrice` (1e-12) |

**Consecuencia en Crypto IDX: el motor estaba estructuralmente ciego.** `MT_REJECTION` (umbral `wickLimit ≥ 0,45`) y `bodyRatio ≥ 0,50` **no podían dispararse nunca**, y `QUANT_CRYPTO` (exige `|z| ≥ 1,9`) estaba muerta. No era un problema de umbrales: era aritmética.

**En ETHUSD-OTC las guardas no muerden** (rango real 0,943 y sd real 0,270, frente a suelos de 1e-5 y 1e-6). Por tanto **la corrección no cambia ninguna decisión sobre el activo por defecto**: es defensa en profundidad, no un cambio de estrategia. **No lleva marca ⚠ TOCA DECISIÓN.**

**CHOP (`sumTr / span`) examinado y NO modificado.** Con `span → 0`, `sumTr ≥ span` siempre, así que CHOP → 100 = veto. Es la **dirección segura** y matemáticamente correcta: ante un feed que se aplana, vetar es lo prudente. Cambiarlo sin medir sería empeorar.

**Reloj de vela y `coerceIn`:** se revisaron las demás divisiones (`:697`, `:703-704`, `:714`, `:732`, `:744`, `StrikeCalculator.kt:60,109`, `VisionAnalyzer.kt:369`, `MarketTick.kt`) y **todas tienen guarda relativa al precio o dominio de píxeles correcto**. `StrikeCalculator.kt:109-110` mantiene una guarda absoluta (`> 1e-9`) en la pendiente `dy/dp`, pero la Y resultante está clampeada (`:229-233`), así que no produce geometría inválida: **frágil, no erróneo**. Se reporta.

**Tests:** 6 casos nuevos que fijan la escala microscópica medida en dispositivo, incluido que el rango **no** se reescala, que una mecha del 50 % supera 0,45, que una vela degenerada no produce `NaN`/infinito y que **ETHUSD-OTC no se ve afectado**.

### B4 — Auditoría de estado por activo

T1 arregló el parser y T3 el buffer de `SyntheticCandleEngine`. Se revisó el resto de acumuladores. **Dos hallazgos claramente erróneos, corregidos:**

1. **`BinomoWebSocketClient.updateActiveAsset` no limpiaba `recentTicks`.** Reseteaba `lastPrice`, `lastPriceTimeMs` y `smoothedVelocity` (`:40-42`) pero no el buffer. Durante los siguientes **16 ticks** la ventana mezclaba escalas (1,166 de un par FX junto a 641,86 de IDX), así que `isPriceFrozen()` y `recentPriceRangeRatio()` veían un rango artificial enorme y **el veto anti-línea-plana quedaba DESACTIVADO justo tras cada cambio de activo**, que es cuando más hace falta. **Es la misma familia de fallo que T1/T3 y tenía el mismo efecto: envenenar la medida con dos escalas.**

2. **`SyntheticCandleEngine` solo limpiaba `recentTickPrices`.** Nuevo `resetAccumulatorsForAssetChange()` limpia además: las 4 rachas de ticks, `tickFlowImbalance`, los contadores de ventana por minuto, los 4 flags de clímax/continuación, `currentCandle` (el primer tick del activo nuevo actualizaba high/low de una vela de **otra escala**, produciendo mechas absurdas), `lastRecordedCandleMinute`, los pivotes cacheados, `cachedAtr` y `cachedChoppiness` (calculada sobre el activo anterior y solo refrescada al cerrar vela: **hasta 60 s de valor contaminado vetando o dejando de vetar**).

3. **`onAssetUpdatedListener` nunca se asignaba** — se declaraba (`:47`) y se invocaba (`:554`), pero `grep` de una asignación daba **0 resultados**. No existía por tanto ningún punto central de limpieza por activo. Ahora `OverlayService` lo conecta a `TradingEngine.onAssetChanged()`, que limpia la señal vigente, el motivo y el último análisis. **No toca rachas, contadores de sesión ni el trade pendiente**: son de la **cuenta**, no del activo, y deben sobrevivir por diseño.

**NO se resetea `closedCandles`** deliberadamente: se recorta solo (tope 60) y vaciarlo obligaría a recalcular pivotes/ATR desde cero, dejando el motor ciego más tiempo del necesario. **Reportado como pendiente conocido en lugar de cambiarlo sin medir.**

**Reportado y NO corregido** (no claramente erróneo, o de impacto no medido):
- `AdaptiveLearningEngine.lossSignatures` e historial persisten entre activos; `TradeContextSignature` **no tiene campo `asset`** e `isTightlyMatchingContext` compara ratios normalizados por ATR que pueden colisionar entre activos distintos. Persiste en disco con TTL 24 h (afecta a `isTightlyMatchingContext`, no a la decisión inmediata).
- `TradeLearningEngine.history` — **código dormido**, sin llamadas en `main`.
- `AutoDrawEngine.manuallyLockedLines` usa claves genéricas `"SOPORTE"`/`"RESISTENCIA"` **sin activo**.
- Correcto por diseño, **no se toca**: `totalWins/Losses`, `sessionPeakBalance`, `sessionStartBalance`, `currentSorosStep`, `completedSorosCycles`, `voids`, `lastTradeTime`, rachas de resultados, config persistida.

**Tests:** 5 casos nuevos, incluida la **contraprueba** de que el mismo activo **no** resetea nada (si no, el motor no acumularía).

### B5 — Chip de strike en vivo: NO VERIFICADO

**No se pudo verificar.** `/trade` (`TradeDrawHttpBridge.kt:128-144`) solo ejecuta `access.performClickAt(x, y)`: **no arma el estado pendiente**, así que el HUD nunca entra en modo operación por esa vía. El modo autónomo se dejó en pausa por seguridad y **el enunciado prohíbe añadir mecanismos para forzar operaciones**. Decisión consultada al usuario, que eligió declararlo NO VERIFICADO en lugar de ampliar el alcance o dejar el bot operando.

**Residuo heredado de R11 (riesgo #12), sin cambios.** El chip `Strike · ITM/OTM · P&L` sigue sin capturarse con operación abierta confirmada. Cuando exista una operación real, la captura es directa: `orderState` en `/status` dice si está `CONFIRMADA`.

---

## Criterios de aceptación

| Criterio | Estado | Evidencia |
| :--- | :--- | :--- |
| HUD visible tras reiniciar | **PASA** | `hud_visible=true` en `TradeDraw_HUDConfig` + `hudVisible:true` en `/status` tras `am force-stop` y relanzar. `r12_after_a5a6.png` |
| Payout leído coincide con el broker (82 %/83 %) | **PASA** | `payoutFromButtonRatio = 0.82`, `payoutResolved = 0.82`, `payoutConfigured = 0.85`; `Crypto IDX 82%` visible en `r12_after_a5a6.png`. **Lectura viva confirmada**: con el broker a 80 % el lector devolvió `0.8`, no el configurado. `PayoutReaderTest` (16 tests) |
| P&L sin `(est.)` cuando hay lectura | **PASA** | `PayoutReader.effectivePayout` + test `conLecturaUsaLaDelBrokerYNoLoMarcaComoEstimado` |
| Texto del HUD legible sobre velas | **PASA** | `r12_after_a5a6.png` con panel al **20 %** de alfa y texto legible sobre el gráfico |
| Ninguna orden TIE aparece como `Trade Activo` | **PASA (por lógica y tests)** | `hasConfirmedTrade` exige `CONFIRMADA`; `recordTradeSent` deja `ENVIADA`. `OrderConfirmationTest` (12 tests). **Sin observación en vivo: ver salvedad abajo** |
| Riesgo #1 reescrito con datos | **PASA** | `loops/R11_REPORTE.md`, riesgo #1 y `## Cierre` con las 21 filas del journal |
| `git status` limpio y evidencia versionada | **PASA** | 69 capturas citadas versionadas en `74bbbc2` + `zoom_tester_12_sniper.png` en `ed88df3`; residuo de 36 PNG no citados declarado; `*.pid`/logs/heartbeat/dumps en `.gitignore`; **0 ficheros no-PNG sin commitear** |
| Tests verdes sobre el árbol de código final | **PASA** | árbol `app/`=`391049b4`, 2026-10-13 20:36:56 → **271 passed, 0 failed, 2 skipped (20 clases)** |
| Build limpio | **PASA** | `BUILD SUCCESSFUL` |
| Verificación visual ANTES/DESPUÉS en dispositivo | **PASA (parcial)** | `loops/screenshots/r12/` (captura nueva) + `loops/screenshots/r11/` (antes). POCO X6 Pro `UGU8KFUKM7E6CE8D` |

### NO VERIFICADO — declarado explícitamente, no maquillado

1. **Chip de strike en vivo (B5).** Sin operación abierta confirmada. Causa concreta: `/trade` no arma el pendiente y no hay mecanismo seguro para forzar una operación.
2. **Transición `ENVIADA` → `CONFIRMADA`/`NO_PROCESADA` observada en vivo (B1).** La lógica está cubierta por tests; no se vio una orden recorrer los estados en dispositivo.
3. **Salida de los vetos V1/V2 en logcat (B2).** `logcat` del dispositivo roto (`Logcat read failure: No such file or directory`). Cubierto por tests, y respaldado por la medición en vivo de `recentPriceRangeRatio = 2,49e-10` en Z-CRY/IDX.
4. **Efecto de los vetos sobre el win rate.** Sin muestra: **N = 0** por el reinicio declarado. **No se puede afirmar mejora de rendimiento.**
5. **`closedCandles` conservado entre activos (B4).** Decisión deliberada y documentada, sin medir su impacto real.

---

## Seguridad

- **Auto-trading DISABLED al cierre**, confirmado por HTTP: `{"success":true,"mode":"DISABLED"}`. `hasPendingTrade: false`.
- **Cuenta demo**, stake mínimo. No se operó en real.
- **Balance: 38.014.735,00 → 37.914.736,64 (−99.998,36).** Una operación **LOSS** durante la verificación en dispositivo. Se declara porque el enunciado lo exige: **no hubo operación fantasma ni descontrol** (movimiento de saldo exacto de un LOSS real, que además es prueba de que la liquidación por saldo funciona), pero **sí hubo una pérdida no buscada**. No se dejó el bot operando sin supervisión en ningún momento.

---

## Entrega

**Commits (directo a `main`, sin PR):**

| SHA | Contenido |
| :--- | :--- |
| `74bbbc2` | A3 — `.gitignore`, citas rotas, capturas citadas versionadas |
| `3e2e334` | A2 + A5 + A6 — payout real, HUD visible, legibilidad del texto |
| `35f18c1` | A5 (2ª causa) — `setupHUDWindow` forzaba el HUD oculto |
| `5228bd2` | B2 + B3 — vetos V1/V2, preregistro R12, guardas de escala relativas |
| `09266e2` | B1 — causa raíz de las operaciones fantasma y los tres estados |
| `69b5925` | B4 — reset de acumuladores por activo y hook de cambio de activo |
| `11fa359` | Erratum de R11 (A1, A4, A2, B2/B3/B4) en `R11_REPORTE.md` |
| `ed88df3` | Informe R12 + `zoom_tester_12_sniper.png` (evidencia citada y sin versionar) + `.gitignore` de dumps |
| `c50fbfd` | SHA/hora de la suite final |
| *(este)* | Anclaje de la verificación al árbol `app/`=`391049b4`, inmune a commits de documentación |

**CI:** workflow "Build APK" → artefacto `TradeDraw-debug`.
`https://github.com/Mizaring467/Tradedraw/actions`
