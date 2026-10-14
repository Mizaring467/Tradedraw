# Ronda 13 — Informe

**Árbol verificado:** `f016dbd` · árbol `app/` = `52cfcc6c16a5a45b90518209451291f04bb0cef6`
**Suite:** 283 tests, 0 fallos, 2 omitidos, 23 clases · XML regenerados 2026-10-14 00:00:21
(`cleanTest` + `--rerun-tasks`, hora real; no se reutilizó caché de Gradle)
**APK:** `app-debug.apk` 13.712.119 bytes, instalado por ADB Wi-Fi (`Success`)
**Batería:** inicio 100 % · final 96 % (el teléfono no carga de forma fiable)

---

## 0. Cambio operativo: ADB Wi-Fi

El trabajo se hizo sobre **`192.168.1.196:5555`** con `adb forward tcp:8080 tcp:8080` y
`http://127.0.0.1:8080`, como pedía el enunciado.

> **Erratum (adenda 5):** el enunciado afirmaba que el serial USB `UGU8KFUKM7E6CE8D` "ya no
> existe". **Es incorrecto**: tras el reinicio del teléfono el dispositivo reapareció como
> `UGU8KFUKM7E6CE8D device` y responde con normalidad. Lo que ocurría al abrir R13 es que no
> estaba **enumerado**, no que hubiera desaparecido. **Ambos transportes funcionan**; se usó
> Wi-Fi por indicación expresa.

**logcat estaba roto durante la ronda y NO era el transporte** (causa raíz confirmada):
`init.svc.logd = stopped` y `/dev/log*` no existe. `start logd` → `Must be root`; `adb root` →
`adbd cannot run as root in production builds`. Fallaba igual por USB y por Wi-Fi. Por eso A5 y
la adenda 3 (buffer en memoria + `/events`) no son un extra: eran la única observabilidad
posible.

> **Y se recupera con un reinicio (adenda 5):** tras reiniciar el teléfono, `init.svc.logd =
> running` y `logcat -d` devuelve entradas reales. **`logd` no estaba roto de fábrica: estaba
> caído.** Ver adenda 5, donde se detalla y se declara que la causa sigue **sin atribuir**.

---

## 1. Erratum R12

### A1 — Modo autónomo · RESUELTO

**De dónde salieron los +59.998,72.** Del journal del dispositivo
(`/data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv`, 6.456 bytes,
2026-10-13 22:15), cuyas tres últimas filas son:

| Hora | Resultado | base_balance | settled_balance | diff |
| :--- | :--- | ---: | ---: | ---: |
| 22:10:03 | WIN | 37.914.736,64 | 37.994.736,64 | **+80.000,00** |
| 22:13:04 | WIN | 37.994.736,64 | 38.074.736,64 | **+80.000,00** |
| 22:15:17 | LOSS | 38.074.736,64 | 37.974.735,36 | **−100.001,28** |

`80.000 + 80.000 − 100.001,28 = +59.998,72`, exactamente el saldo medido al abrir R13
(`37.974.735,36`). Las tres son `AUTO_ADAPTIVE / CONSERVATIVE`, precios 948,755 / 949,424 /
949,091 → activo ~949 (ETHUSD-OTC), no IDX. Patrón 62 s las WIN y 72 s el LOSS.

**Persistencia tras reiniciar: NO persiste.** `TradingEngine.mode` es una propiedad **solo en
memoria** (`TradingEngine.kt:49`), sin lectura ni escritura en `SharedPreferences`. Medido en
dispositivo: se puso `AUTONOMOUS` (`/status` → `AUTONOMOUS`), `force-stop` + relanzado → modo
`DISABLED`. Se declara, no se cambia.

**Corrección de la sección "Seguridad" de R12.** Afirmaba que la ronda había cerrado en
`DISABLED` y que eso probaba que el bot no operaba entre sesiones. Ambas cosas son falsas como
garantía: el modo lo cambió el usuario a propósito y **el modo sobrevive mientras el proceso
vive**, así que el cierre en `DISABLED` depende de que alguien lo ponga, no de que el sistema
lo garantice. El reinicio sí devuelve `DISABLED`; dentro de la misma sesión, no.

**Estado al cierre de esta ronda:** `DISABLED` (por §4). Ver §4 de este informe: el usuario lo
había dejado en `AUTONOMOUS`.

### A2 — Activo sin ticks · RESUELTO

**(a) Por qué Aave no entregaba ticks.** **Era el mapeo RIC, no el parser ni el feed.**
Aave no tenía rama propia, así que caía al `else`, que hacía `assetCandidate.replace(" ", "")`
→ **`Aave(OTC)` conservando los paréntesis**: un RIC que el feed no reconoce. Confirmado con
frames crudos de `/status`: el feed **solo emite el RIC que se ha suscrito** (en la captura
solo aparecía `"ric":"Z-CRY/IDX"`, ninguna mención de Aave). Las tramas extracto:

```json
{"data":[{"assets":[{"rate":641.867310565,"ric":"Z-CRY/IDX"}],"action":"assets"}],"success":true}
```

Arreglado en `aa6ae39`: rama `AAVE` explícita, nombres completos (`BITCOIN`, `CARDANO`,
`LITECOIN`, `RIPPLE`, `POLKADOT`, `CHAINLINK`, `DOGECOIN`, `ETHEREUM`) porque los símbolos
cortos no están contenidos en ellos, `ORO` en español, y un `else` que deriva `<BASE>USD-OTC`
limpiando paréntesis y sufijos.

**(b) El camino que pintaba `px/s` e "Impulso Alcista" sin tick.** **Estaba en el layout, no
en el código.** `layout_trading_hud.xml:305` tenía como `android:text` la cadena de diseño
`⚡ Micro-Velocidad: +0.038 px/s ▲ Impulso Alcista`. Con `latestTick == null` el HUD no entra
en ninguna rama de actualización, así que **quedaba en pantalla un valor inventado (+0.038),
una unidad que T5 eliminó (px/s) y una dirección (Alcista) sin ningún tick detrás**. Es
exactamente lo de la captura. Corregido en `2b5c185`.

**(c) HUD.** `e9ff639`: con socket `CONNECTED` pero sin tick fresco, el HUD declara
**`🔴 SIN TICKS · <activo> sin feed (socket vivo)`** en ámbar, en vez de `... (0ms)` — que
simulaba una latencia de cero cuando no había ninguna lectura.

**(d) Veto con feed mudo.** Ya existía: el guard de feed rancio al inicio de
`executeHeadlessTrade` (`tickAgeMs == null || tickAgeMs >= FEED_MAX_AGE_MS || !isFeedFresh()`)
cubre el caso, porque sin tick la edad es `null` o supera el umbral. **No se añadió un veto
nuevo: habría duplicado el existente.** Lo que faltaba era que fuese **observable**; ahora
registra el activo y la edad en `/events`.

**(e) Test.** `TradeEventLogTest` + `AssetRicMappingTest` (3 casos, incluido el invariante de
que ningún RIC puede contener paréntesis ni espacios, que es el fallo exacto de Aave).

### A3 — Payout, método 1 · REFUTADO Y RETIRADO

`payoutFromPercentLabel` es **`null` de forma persistente** en el dispositivo. Medido en
`/status`: `payoutFromButtonRatio = 0.83`, `payoutFromPercentLabel = null`,
`payoutResolved = 0.83`.

**No es implementable de forma fiable**: Binomo expone el porcentaje en un nodo distinto del
nombre del activo y **no hay relación padre/hermano explotable**. Dos intentos de observarlo:
`uiautomator dump` de la pantalla de trading devuelve **árbol vacío** (Binomo lo bloquea), y el
dump de R11 conservado en el repo **tampoco contiene** ni el nombre del activo ni el
porcentaje. No se inventa una heurística sobre una jerarquía que no se puede observar.

Decisión: se mantiene el intento en el mismo nodo (es gratis y cubre que el broker vuelva a
fusionarlos), pero el código **declara explícitamente que el método 1 no está validado**, y si
alguna vez produjera valor el cruce se registra como `PAYOUT_DISCREPANTE` en `/events`. Así
`payoutDiscrepant` deja de ser inerte: o hay dos lecturas que contrastar, o consta que solo hay
una. **Se retira la afirmación de "dos vías validadas"**: solo el método 2 (ratio del botón)
está confirmado.

### A4 — Causa del tap fallido · **CORREGIDO: la causa raíz que declaré era FALSA**

> **Erratum dentro de la propia R13.** En la primera versión de este informe declaré
> "CAUSA RAÍZ CONFIRMADA: carrera del bypass del HUD". **Era incorrecto.** Al ir a cerrar la
> adenda 4 medí la geometría real de las ventanas y la hipótesis **se cae**. Se deja el relato
> completo porque el error es instructivo: tenía un mecanismo plausible, una latencia medida y
> una firma de journal que encajaba, y **aun así era falso** por no haber comprobado una
> precondición.

**Lo que creí.** `temporarilyBypassHUD` dejaba el HUD no-táctil **350 ms fijos** y lo
restauraba por temporizador; el tap (`su -c input tap`) tarda más que eso, así que el HUD
volvía a ser táctil antes de que el gesto llegara al broker y se comía el toque.

**Por qué es falso.** El bypass **solo se aplica si el punto del tap cae dentro del HUD**
(`AutoTradeAccessibilityService.kt:151`):

```kotlin
if (overlay != null && overlay.isPointInsideHUD(x, y)) {
    overlay.temporarilyBypassHUD(350L) { executeRootTap(x, y, onResult) }
} else {
    executeRootTap(x, y, onResult)   // ← camino real: SIN bypass
}
```

Y el punto del tap **no cae dentro del HUD**. Medido en el dispositivo
(`dumpsys window windows`, 720x1600, `SurfaceOrientation: 0`):

| Ventana | Frame | Táctil |
| :--- | :--- | :--- |
| HUD (`624xwrap`, `y=762`) | `[96,812] – [720,1302]` | sí |
| Lienzo a pantalla completa | `[0,50] – [720,1504]` | **no** (`FLAG_NOT_TOUCHABLE`) |
| Botón COMPRA (Portrait/Binomo: `w*0.347, h*0.838`) | **(249,8 · 1340,8)** | — |
| Botón VENTA (`h*0.844`) | **(249,8 · 1350,4)** | — |

El borde inferior del HUD está en **y = 1302**; los botones están en **y ≈ 1341-1350**.
**Ningún punto de tap cae dentro del HUD** (comprobado para ambos). Por tanto
`isPointInsideHUD` es `false`, se toma la rama `else`, **no hay bypass y no hay carrera**.
La latencia de 350 ms frente a la del tap es irrelevante para este fallo: los dos caminos
llaman a `executeRootTap` igual.

**La medición que refuta la hipótesis la hice yo mismo, y es peor de lo que había supuesto:**
30 taps bajo carga real dieron **255-1205 ms (mediana ≈ 500 ms)**, con **24 de 30 por encima de
350 ms** y `loadavg` subiendo de 15,57 a 20,27. Es decir, la carrera era **más** probable de lo
que dije, y precisamente por eso el argumento se vuelve insostenible: con 80 % de taps
rebasando el temporizador, la carrera **no** puede producir solo un 8 % de TIE. Si el bypass se
aplicara de verdad a estos taps, la tasa de fallo debería ser muchísimo mayor.

**Qué queda en pie de A4 (y sigue siendo un arreglo válido).** El cambio de
`temporarilyBypassHUD` a cierre por evento (`restoreHudTouchable()` en el `finally`) es
**correcto y se mantiene**: elimina una carrera real **para los taps que sí caen dentro del
HUD** (cualquier activo/orientación/perfil donde el HUD se solape con el botón, o si el HUD se
arrastra encima — es arrastrable y su `y` se acota a `maxH = altura − hudH − 80`, o sea que
**puede** llegar a cubrir el botón). Pero **no es la explicación de los 17 TIE**, porque en la
configuración medida el bypass ni se activa.

**Causa de los 17 TIE: SIGUE SIN DETERMINAR.** Lo que sí queda establecido, con datos:

| Hipótesis | Veredicto | Evidencia |
| :--- | :--- | :--- |
| H1: tap fuera de la ventana de compra | **REFUTADA** | los TIE están **100 % dentro** de `:58–:03`/`:01–:05` (`cs = 1,1,1,2,2`); los LOSS tienen 15,4 % fuera |
| Cadencia/intervalo como causa | **REFUTADA** | separación entre operaciones de los TIE 118-121 s, idéntica a WIN y LOSS |
| Ventana/importe/expiración | **REFUTADA** | `stake` idéntico (1,0-2,0) en los tres grupos; `duration_sec` solapado (TIE 57-63, WIN 54-63, LOSS 54-72) |
| **Overlay/HUD comiéndose el toque** | **REFUTADA** | el HUD termina en y=1302 y el tap va a y≈1341; `isPointInsideHUD` = `false`, no hay bypass |
| Inanición de recursos (adenda 4) | **SIN EVIDENCIA** | ver adenda 2: 0 muertes de procesos, 0 ANR, 0 OOM, 739 MB disponibles |
| Broker no procesa el toque | **NO VERIFICABLE HOY** | exigiría la sesión en vivo que el usuario no autorizó (B2) |

La instrumentación mínima para cerrarlo **ya está puesta** en R13 y es lo que faltaba:
`TradeEventLog` registra cada tap con **coordenadas, hora y duración** (`/events`), y
`orderState` distingue `ENVIADA` de `NO_PROCESADA`. Con eso, el próximo TIE real se podrá
leer como "tap despachado con éxito en (x,y) a las T, duración D, y aun así el saldo no se
movió" — que apunta al broker, no al overlay. **Sin esa observación no se declara causa.**

El journal **ya contenía** la firma del fallo: las 17 filas TIE llevan
`ORDEN NO PROCESADA (Diff=0.0 -> Saldo inalterado)` con `base_balance == settled_balance`
exacto. Eso dice **qué** pasó (el saldo no se movió), no **por qué**.

**`NINGUNA/IDLE` añadido.** `clearPendingTrade()` dejaba `NO_PROCESADA`, así que el HUD y
`/status` **etiquetaban el reposo como fallo**. Ahora reposo = `NINGUNA` y el fallo sigue
siendo distinguible. `markOrderUnprocessed` limpia primero y marca después, porque si no el
`clear` pisaba el `NO_PROCESADA` y el fallo se perdía. Verificado en dispositivo:
`orderState = NINGUNA` con `hasPendingTrade = false`.

### A5 — Observabilidad de vetos · RESUELTO

`TradeEventLog` (buffer circular en memoria, 200 eventos) + **`GET /events`** + contadores en
`/status`: `vetoV1`, `vetoV2`, `vetoTotal`, `ultimoVeto`, `tapsDespachados`.

Detalle de diseño que importa: los vetos se indexan por el **primer token** del motivo
(`V1`, `V2`, …), **no por el texto completo**. El detalle incluye valores medidos
(`V2 rango=1.2e-09 …`), así que indexar por el texto entero habría dejado cada veto como clave
distinta y **los contadores inertes** (cada uno a 1). Lo detectó un test, no la revisión
manual.

**Tasa esperada de V2 en ETH ≈ 2,2 %** añadida a `PREREGISTRO_R12.md` (34.245 ventanas,
ventana de 16 ticks), para poder detectar desviaciones.

### A6 — Texto del reporte · CORREGIDO

**(a) Hora de la suite.** R12 declaró "Suite re-ejecutada 20:36:56" mientras los XML en disco
eran de las 20:07:31: Gradle la dio por vigente. Aquí se ha usado `cleanTest` +
`--rerun-tasks` y se verifica que **los 23 XML se regeneraron en el mismo instante**
(2026-10-14 00:00:21), 12 s antes de leerlos. La hora declarada es la real.

**(b) Datasets 21 vs 212 · RECONCILIADOS.** Son **esquemas y periodos distintos**, y R12 los
mezcló sin declararlo:

| Dataset | Filas | Columnas | Rango | Esquema |
| :--- | ---: | ---: | :--- | :--- |
| `trade_journal_device_all.csv` | 212 | 13 | 2026-09-10 → 09-12 | sin `candle_second`, sin `tick_velocity`, sin `impulse` |
| journal del dispositivo | 26 | 21 | 2026-10-11 → 10-13 | con `candle_second`, `tick_velocity`, `impulse`, `market_regime` |

**La columna `candle_second` NO EXISTE en el dataset de 212 filas**, así que H1 no era
evaluable sobre él; se evaluó sobre el de 21 columnas, que sí la tiene. Esa es la razón de que
"21" y "212" no fuesen intercambiables. El dataset de 212 filas contiene 3 fuentes solapadas
(`device_all` 212 + `live` 17 + `live2` 35 = 264), con **212 timestamps únicos**.

---

## 2. Trabajo nuevo

### B1 — Seguridad del bridge HTTP · APLICADO Y VERIFICADO

**Antes:** `ServerSocket(port)` se enlazaba a `[::]` (todas las interfaces). Con el teléfono en
Wi-Fi, cualquiera en la red podía leer `/status` y, peor, disparar **`/trade`, que ejecuta taps
reales contra el broker**.

**Después:** el socket se enlaza explícitamente a loopback
(`InetSocketAddress(InetAddress.getLoopbackAddress(), port)`), y el acceso se hace **siempre**
por `adb forward`.

**Verificado en dispositivo, no declarado:**

| Prueba | Resultado |
| :--- | :--- |
| `curl http://192.168.1.196:8080/status` (LAN) | **sin respuesta** — no expuesto |
| `curl http://127.0.0.1:8080/status` (por `adb forward`) | **HTTP 200**, 3.843 bytes |
| `curl http://127.0.0.1:8080/events` | `{"success":true,"total":0,"vetoV1":0,"vetoV2":0,"events":[]}` |

`AGENTS.md` actualizado con el nuevo modo de acceso, y con la advertencia de usar
`127.0.0.1:8080`, **nunca la IP del teléfono**.

### B2 — Prueba de campo supervisada · NO VERIFICADO

**Preguntado al usuario. Respuesta: no autorizada** (opción A: declarar NO VERIFICADO).

**NO VERIFICADO:** no se ha observado una orden real recorriendo `ENVIADA →
CONFIRMADA/NO_PROCESADA`, ni el chip de strike con operación abierta. Motivo declarado por el
usuario: la batería (96 %, y el teléfono ya no carga) hace que una sesión en vivo sea el peor
uso del margen. La lógica de los cuatro estados está cubierta por tests (`OrderConfirmationTest`
12 casos, `OrderStateReposoTest` 2) y por `/status`, pero **eso no sustituye la observación en
vivo**.

---

## 3. Adenda

### Adenda 1 — Dispositivo real · CORREGIDO

**No es un "POCO X6 Pro".** Datos de `getprop`:

| Propiedad | Valor real |
| :--- | :--- |
| `ro.product.model` | **`220733SL`** |
| `ro.product.marketname` | **Redmi A1** |
| Android | **12** (Go Edition, `ro.config.low_ram = true`) |
| SoC | `mt6761`, **4 núcleos**, `armeabi-v7a` |
| RAM | 1.875.668 kB (~400 MB de swap en uso) |
| `loadavg` | **17,10 / 16,75 / 16,35** (hasta 20,27 bajo ráfaga de taps) |

Corregido en `AGENTS.md` (sección 8 reescrita) y en este informe. **Revisadas las afirmaciones
de latencia**: el MCP se anunciaba con "< 10 ms vs ~400 ms de `adb shell input tap`", pero la
medida real de `su -c input tap` es **255-1205 ms (mediana ≈ 500 ms, 30 muestras bajo carga
real)** — no los 141-327 ms de la primera medición, que se hizo con el sistema más ocioso. El
dispositivo tiene **un orden de magnitud menos de CPU** de lo que se venía suponiendo. Las
declaraciones de rendimiento de R11/R12 hechas sobre esa base quedan invalidadas.

### Adenda 2 — logd y presión de memoria · MEDIDO, SIN EVIDENCIA DE KILLS

**Primera medición** (el estado del dispositivo al abrir la ronda):

| Medida | Valor |
| :--- | :--- |
| `init.svc.logd` | `stopped` |
| `/dev/log*` | **no existe** |
| `init.svc.lmkd` | `running` |
| `ro.lmk.kill_timeout_ms` | `100` |
| `persist.sys.lmk.reportkills` | `true` |
| `MemTotal` / `MemFree` / `MemAvailable` | 1.875.668 / 99.996 / **739.048** kB |
| `SwapTotal` / `SwapFree` | 2.097.148 / 1.685.756 kB |
| `com.example.tradedraw` | RSS 153.584 kB, swap 2.880 kB |
| `com.marketly.trading` | RSS 225.460 kB, swap 40.352 kB |
| `com.android.systemui` | RSS 124.908 kB, swap 17.532 kB |

Esa medición sola **no probaba nada sobre kills**: sólo describía memoria disponible *después*.
Lo que la adenda pedía era evidencia de reinicios de procesos y de kills. Se midió con
`logcat -b events`, que es la fuente autoritativa de `ActivityManager` (`dmesg` no es legible
sin root en esta build):

| Comprobación | Resultado |
| :--- | :--- |
| `am_kill` en la ventana (00:08:02 → 00:12:51) | **7**, y **las 7 con motivo `empty #N`** (reciclado de caché) |
| Procesos matados por esos `am_kill` | `cellbroadcastreceiver`, `chrome`, `providers.calendar`, `vending`, `vending:background`, `permissioncontroller`, `android.process.acore` — **ninguno nuestro** |
| `am_proc_died` de app / Binomo / systemui | **0** |
| `am_anr` / `am_crash` / `OutOfMemory` / `FATAL` | **0** |
| Reinicios del servicio de accesibilidad (rebind/enable) | **0** |
| `am_proc_start` de nuestros procesos | 2: Binomo (arranque normal) y un sandbox de WebView |
| `dumpsys meminfo` (RSS) | `com.marketly.trading` 318.831K · `system` 193.345K · `gms` 184.948K · `systemui` 155.533K · **`com.example.tradedraw` 145.245K** |

**Conclusión, ahora con evidencia en vez de con memoria disponible:** **no hay indicios de que
el sistema esté matando procesos por memoria.** Ningún proceso bajo vigilancia murió, ningún
ANR, ningún OOM, y los únicos `am_kill` son reciclado de caché por política (`empty #N`), no
presión de memoria (`low memory` aparecería con ese motivo). `MemAvailable` de 680-739 MB sobre
1,8 GB y swap mayormente libre son coherentes con eso.

**`logd` parado se queda como síntoma sin causa raíz atribuida.** Lo único nuevo es que
**vuelve con un reinicio** (adenda 5), así que no está roto de fábrica: algo lo mata o impide su
arranque durante el uso. **No se atribuye a presión de memoria**, porque la evidencia de kills
dice lo contrario. Instrumentar el instante de la caída (no el estado posterior) es lo que
faltaría.

### Adenda 3 — `/events` · IMPLEMENTADO

Buffer circular en memoria (200 eventos) accesible por el bridge. Registra: vetos con motivo,
transiciones de `orderState`, **taps despachados (coordenadas, duración y resultado)** y
**cambios de activo** (`f016dbd`, con el nombre visible y el RIC resultante en la misma línea,
que es lo que hacía falta para diagnosticar A2a). Verificado en dispositivo con JSON válido.

### Adenda 4 — Inanición como hipótesis de A4 · CONTRASTADA Y REFUTADA

Se midió la **cola** de la latencia del tap, que es lo que decide si una carrera de temporizador
es alcanzable, con 30 muestras bajo carga real:

| Estadístico | Valor (30 muestras) |
| :--- | :--- |
| Rango | **255 – 1205 ms** |
| Mediana | **≈ 500 ms** |
| Por encima de 350 ms (el temporizador viejo) | **24 de 30 (80 %)** |
| `loadavg` antes → después | 15,57 → **20,27** |

**Esto refuta la inanición como causa de los 17 TIE por una vía inesperada: refuta la propia
hipótesis de la carrera.** Con un 80 % de taps rebasando el temporizador, una carrera
overlay/tap habría producido una tasa de fallo muy superior al 8 % observado. Y con la
geometría de A4 (el HUD no cubre el botón) la carrera ni siquiera se activa. La inanición de
recursos **no sostiene** la tasa de TIE: no hay presión de memoria (adenda 2), no hay kills, no
hay ANR, y la separación entre operaciones de los TIE es la más corta de los tres grupos, no la
más larga. Lo que la latencia sí demuestra es que **cualquier temporizador corto en este
dispositivo es frágil**, que es la lección que se aplicó en `restoreHudTouchable`.

### Adenda 5 — Reinicio del teléfono · EJECUTADO, `logd` VUELVE

El usuario **autorizó** reiniciar el teléfono al final de la ronda. Ejecutado tras dejar el
modo en `DISABLED`.

**Resultado: `logd` SÍ vuelve tras el reinicio, y `logcat` funciona.**

| Momento | `init.svc.logd` | `logcat -d` |
| :--- | :--- | :--- |
| Antes del reinicio | `stopped` | `Logcat read failure: No such file or directory` |
| **Después del reinicio** | **`running`** | **devuelve entradas reales** |

Salida real de `logcat -d -t 5` tras el reinicio (recortada):

```
--------- beginning of main
10-14 00:09:23.012   773   848 D AALLightSensor: enable Sensor FAIL! 
10-14 00:09:23.145  1762  6385 I NearbySharing: Network state changed: ...
10-14 00:09:24.016   773   848 D AALLightSensor: get SensorManager SUCCESS!
```

**Qué significa.** `logd` no estaba roto de fábrica: **estaba caído**, y el reinicio lo levanta.
Por tanto la causa no es la build (`user`, `ro.debuggable=0`), porque eso no cambia al
reiniciar. **La causa es que algo lo mata o impide su arranque durante el funcionamiento**, no
que no pueda existir. Esto **refuta** parcialmente la conclusión de R13 de que "no se puede
levantar sin root": no se puede levantar **en caliente**, pero sí vuelve con un reinicio.

**Nota sobre el cable.** Al reiniciar, el dispositivo USB reapareció solo como
`UGU8KFUKM7E6CE8D device`. `adb tcpip 5555` **no** sobrevivió al reinicio (el
`192.168.1.196:5555` quedó en `offline` y hubo que reactivarlo), así que hizo falta el cable un
momento, exactamente como se había avisado al usuario.

**Y la IP había cambiado.** `wlan0` estaba en **`192.168.1.225/24`**, no en `192.168.1.196`.
El teléfono tomó otra concesión DHCP al reiniciar. Se reconectó con `adb connect
192.168.1.225:5555` → `device`, y **el cable ya se puede desconectar**.
`adb -s 192.168.1.225:5555` es la dirección válida a partir de ahora. Esto es precisamente el
escenario para el que existe el procedimiento de §8.0 de `AGENTS.md`: la IP cambió sin avisar,
y la respuesta correcta era medirla (`ip -f inet addr show wlan0`), no improvisar.

**Indicio (no conclusión).** Que `logd` muera con el uso y vuelva en cada arranque es
**compatible** con presión de memoria, pero **las mediciones de la adenda 2 no la respaldan**:
`MemAvailable` era de 739 MB de 1,8 GB (39 % libre) y la app tenía solo 2.880 kB en swap. Sin
una medición **en el momento** en que `logd` muere, la causa sigue **sin atribuir**. Se
declara como observación, no como causa raíz.

**Observación lateral que corrige §0.** El serial USB `UGU8KFUKM7E6CE8D` **no había
desaparecido**: existe y funciona. Lo que ocurría es que en el momento de abrir R13 el
dispositivo no estaba enumerado por USB. Se accedió por Wi-Fi como pidió el enunciado, pero
afirmar que el serial "ya no existe" era incorrecto; ambos transportes funcionan.

---

## 4. Criterios de aceptación

| Criterio | Estado | Evidencia |
| :--- | :--- | :--- |
| `/status` y HUD coherentes cuando un activo no entrega ticks | **PASA** | RIC corregido (`aa6ae39`); HUD `SIN TICKS` (`e9ff639`); `px/s` retirado del layout (`2b5c185`) |
| Método 1 del payout funcional **o retirado** | **PASA (retirado)** | declarado no validado; `payoutFromPercentLabel = null` medido; solo el método 2 confirmado |
| Contadores de vetos visibles | **PASA** | `/status` → `vetoV1`, `vetoV2`, `vetoTotal`, `ultimoVeto`; `/events` con motivo |
| Bridge inaccesible desde la LAN | **PASA** | LAN sin respuesta; loopback 200 |
| `mode = DISABLED` al cierre | **PASA** | `DISABLED` confirmado por `/status`, también tras el reinicio; `hasPendingTrade: false` |
| Batería reportada al inicio y al final | **PASA** | 100 % → 93 % |
| Suite completa sobre el árbol final con hora real | **PASA** | 283/0/2, XML 00:00:21, `cleanTest --rerun-tasks` |
| `assembleDebug` + instalación por ADB Wi-Fi | **PASA** | `Success` |
| B1 verificado en dispositivo | **PASA** | tabla de §2 |
| **A4: causa de los 17 TIE identificada** | **NO PASA (sin determinar)** | mi hipótesis quedó **refutada** por la geometría medida; ver A4 y NO VERIFICADO |
| B2 (orden real en vivo) | **NO VERIFICADO** | no autorizado por el usuario |

**Ninguno de los criterios que el enunciado marcaba como obligatorios falla.** El que no pasa
—identificar la causa de los 17 TIE— **no estaba en la lista de criterios de aceptación** de §5;
A4 pedía determinar la causa *«o, si no es determinable, proponer la instrumentación mínima y
añadirla»*. Eso segundo **sí se hizo** (`/events` con coordenadas, hora y duración de cada tap;
`orderState` distingue `ENVIADA` de `NO_PROCESADA`). Se declara aquí porque un NO PASA no debe
esconderse detrás de un criterio distinto.

**Balance: 37.974.735,36 al abrir y al cerrar — sin cambios.** La ronda **no ejecutó ninguna
operación**: el modo estuvo en `DISABLED` durante el trabajo y las tres operaciones del journal
(`+80.000`, `+80.000`, `−100.001,28`) son **anteriores** a esta ronda (2026-10-13 22:10–22:15,
antes de abrir R13), atribuidas al `AUTONOMOUS` que el usuario activó. Al releer `/status`
justo tras el reinicio el saldo apareció como `0.0`: es el arranque en frío, antes de que la
accesibilidad leyera el saldo de Binomo (la captura de ese momento ya mostraba
`Col$37.974.735` en pantalla). Con el árbol asentado, `/status` devuelve `37974735.36`.

---

## 5. Modo al cierre — contradicción declarada

**El usuario dejó el modo en `AUTONOMOUS` a propósito** (§1/A1) y pidió no desactivarlo ni
preguntar por ello. Pero §4 de la propia ronda exige **`mode = DISABLED` al cierre** y no
dejar el bot operando entre sesiones.

**Se aplica §4** (es la restricción de seguridad, y prevalece sobre la comodidad de dejar el
estado como estaba). Al cierre de esta ronda el modo queda en **`DISABLED`**, confirmado por
`/status`. Queda constancia de que fue el agente quien lo cambió, y de por qué.

**El cambio se hizo con el teléfono vigilado y no había operación en curso.** Antes de
desactivarlo se comprobó `hasPendingTrade: false` y `orderState: NINGUNA`, de modo que el cambio
de modo no interrumpió ninguna orden abierta. Después del reinicio (adenda 5) se revalidó:
`mode: DISABLED`, `hasPendingTrade: false`, `balance` sin cambios.

---

## 6. NO VERIFICADO

- **B2**: no se observó una orden real recorriendo `ENVIADA → CONFIRMADA/NO_PROCESADA`, ni el
  chip de strike con operación abierta. No autorizado.
- **El arreglo de A4 no resuelve los 17 TIE, y la causa de estos sigue SIN DETERMINAR.** La
  hipótesis que declaré (carrera del bypass del HUD) quedó **refutada** por la geometría medida:
  el HUD termina en y=1302, el tap va a y≈1341, `isPointInsideHUD` es `false` y el bypass no se
  aplica. El cierre por evento se mantiene porque elimina una carrera real para los taps que
  **sí** caen bajo el HUD, pero **no explica el 8 % de órdenes no registradas**. Hacerlo exigiría
  la sesión en vivo que no se autorizó (B2), ahora con la instrumentación ya puesta.
- **El método 1 del payout sigue sin valor en dispositivo.** Se declara retirado, no arreglado.
- **`logd` parado sin causa raíz atribuida.** La adenda 5 demostró que **vuelve con un
  reinicio** (`init.svc.logd = running`, `logcat -d` devuelve entradas reales), así que no está
  roto de fábrica: **algo lo mata o impide su arranque durante el uso**. No se ha medido memoria
  **en el instante en que muere**, y las medidas tomadas después no muestran presión (739 MB
  disponibles). Por tanto la causa **sigue sin atribuir**: ni confirmada ni refutada la
  hipótesis de memoria. Falta instrumentar el momento de la caída, no el estado posterior.
- **El evento `ASSET` de `/events` no se ha visto dispararse** en la verificación: requiere un
  cambio de activo observado por accesibilidad, y el activo ya estaba fijado en la sesión de
  prueba. El endpoint sí está verificado como tal.
- **R11/R12 declararon latencias y rendimiento asumiendo un teléfono potente.** Esas cifras
  quedan invalidadas; no se han re-medido todas en esta ronda.

---

## 7. Commits

| Commit | Contenido |
| :--- | :--- |
| `aa6ae39` | `fix(A2a)`: mapeo RIC cubre AAVE y nombres completos; sin paréntesis |
| `2b5c185` | `fix(A2b)`: el texto `px/s` "Impulso Alcista" era el valor por defecto del layout |
| `86c31f8` | `feat(A5)`: observabilidad sin logcat (`/events`, V1/V2, A2d) — **incluye B1** (mismo fichero) |
| `e9ff639` | `fix(A2c)`: HUD declara `SIN TICKS` en vez de `... (0ms)` — **incluye el bypass de A4** (mismo fichero) |
| `f8129fb` | `fix(A4)`: causa raíz de las órdenes no registradas + estado `NINGUNA` — **incluye A3** (mismo fichero) |
| `5df79ee` | `docs(adenda)`: modelo real Redmi A1, ADB Wi-Fi, bridge en loopback |
| `e03ac03` | `test(R13)`: buffer de eventos, mapeo RIC, estado de reposo |
| `f016dbd` | `feat(adenda3)`: registrar cambios de activo en `/events` |

Tres commits agrupan más de una tarea porque las tareas caían en el **mismo fichero**
(`TradeDrawHttpBridge.kt`, `OverlayService.kt`, `AutoTradeAccessibilityService.kt`) y separarlas
habría exigido commits que no compilan. Se declara aquí en vez de aparentar granularidad.

---

## 8. Entrega

- **CI:** https://github.com/Mizaring467/Tradedraw/actions → artefacto **`TradeDraw-debug`**
  · run verde del árbol final: **`38514679629`** (`7814f34`)
- **SHA del árbol verificado:** `f016dbd` (árbol `app/` = `52cfcc6c16a5a45b90518209451291f04bb0cef6`)
  · último commit de documentación: `0dfdb4b` · `.gitignore`: `7814f34`
- **Evidencia visual:**
  - `loops/screenshots/r13/r13_hud_estado.png` (720x1600, 248.795 bytes) — A2b/A2c en vivo.
  - `loops/screenshots/r13/r13_final_post_reinicio.png` (720x1600, 284.250 bytes) — estado tras
    el reinicio de la adenda 5: `MODO OFF: En Espera`, `Micro-Velocidad: +7,77e-09 %/s ▲
    Alcista` (**unidad `%/s`, no `px/s`**, con tick fresco) y `Col$37.974.735` en pantalla.
- **Dirección ADB vigente:** `192.168.1.225:5555` (la IP cambió al reiniciar; el cable ya se
  puede desconectar).
