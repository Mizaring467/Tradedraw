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

### A4 — Causa del tap fallido · CAUSA RAÍZ CONFIRMADA

**Es una carrera del bypass del HUD.** `temporarilyBypassHUD` dejaba el HUD no-táctil
**350 ms fijos** y lo restauraba por temporizador. Pero el tap real (`su -c input tap`) tarda,
**medido en el dispositivo**, **141–327 ms (mediana 222 ms, 10 muestras)** y **no tiene cota
superior** con `loadavg` ≈ 17. Cuando el tap tardaba más que el temporizador, el HUD volvía a
ser táctil **antes** de que el gesto llegara al broker: el overlay se comía el toque y la orden
quedaba sin registrar — exactamente la firma `ORDEN NO PROCESADA (Diff=0.0 → Saldo
inalterado)` de los 17 TIE.

Arreglo (`f8129fb` + `e9ff639`): el bypass lo cierra `restoreHudTouchable()` en el `finally`
del tap; el temporizador queda solo como **failsafe** (1.500 ms), y `restoreHudTouchable`
respeta la visibilidad real del HUD (antes forzaba `VISIBLE`).

**Hipótesis contrastadas con datos, no supuestas:**

| Hipótesis | Veredicto | Evidencia |
| :--- | :--- | :--- |
| H1: tap fuera de la ventana de compra | **REFUTADA** | los TIE están **100 % dentro** de `:58–:03`/`:01–:05` (`cs = 1,1,1,2,2`); los LOSS tienen 15,4 % fuera |
| Cadencia/intervalo como causa | **REFUTADA** | separación entre operaciones de los TIE 118–121 s, idéntica a WIN y LOSS |
| Inanición de recursos (adenda 4) | **SIN EVIDENCIA** | ver adenda 2 |
| Ventana/importe/expiración | **REFUTADA** | `stake` idéntico (1,0–2,0) en los tres grupos; `duration_sec` solapado (TIE 57–63, WIN 54–63, LOSS 54–72) |
| Overlay interceptando el toque | **CONFIRMADA** | es la causa raíz de arriba |

El journal **ya contenía la instrumentación** que se pedía: las 17 filas TIE llevan
`ORDEN NO PROCESADA (Diff=0.0 -> Saldo inalterado)` con `base_balance == settled_balance`
exacto. No hizo falta añadir instrumentación; faltaba leerla.

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
| `loadavg` | **17,10 / 16,75 / 16,35** |

Corregido en `AGENTS.md` (sección 8 reescrita) y en este informe. **Revisadas las afirmaciones
de latencia**: el MCP se anunciaba con "< 10 ms vs ~400 ms de `adb shell input tap`", pero la
medida real de `su -c input tap` es **141–327 ms** y el dispositivo tiene **un orden de
magnitud menos de CPU** de lo que se venía suponiendo. Las declaraciones de rendimiento de
R11/R12 hechas sobre esa base quedan invalidadas.

### Adenda 2 — logd y presión de memoria · MEDIDO, SIN EVIDENCIA DE KILLS

| Medida | Valor |
| :--- | :--- |
| `init.svc.logd` | `stopped` |
| `/dev/log*` | **no existe** |
| `init.svc.lmkd` | `running` |
| `ro.lmk.kill_timeout_ms` | `100` |
| `persist.sys.lmk.reportkills` | `true` |
| `MemTotal` / `MemFree` / `MemAvailable` | 1.875.668 / 99.996 / **739.048** kB |
| `SwapTotal` / `SwapFree` | 2.097.148 / 1.685.756 kB |
| `com.example.tradedraw` (pid 21653) | RSS 153.584 kB, swap 2.880 kB |
| `com.marketly.trading` (pid 11404) | RSS 225.460 kB, swap 40.352 kB |
| `com.android.systemui` (pid 1421) | RSS 124.908 kB, swap 17.532 kB |

**No hay indicios de que el sistema esté matando procesos por memoria**: `MemAvailable` es de
**739 MB** sobre 1,8 GB (39 % disponible), el swap está mayormente libre y la app lleva solo
2.880 kB en swap. `lmkd` está corriendo con umbrales por defecto, pero no hay evidencia de que
haya actuado. **`logd` parado se queda como síntoma sin causa raíz atribuida** — no se
atribuye a presión de memoria sin datos que lo respalden. Esto también responde a la
**hipótesis de inanición de recursos de A4**: sin evidencia de presión de memoria, no sostiene
la tasa de TIE.

### Adenda 3 — `/events` · IMPLEMENTADO

Buffer circular en memoria (200 eventos) accesible por el bridge. Registra: vetos con motivo,
transiciones de `orderState`, **taps despachados (coordenadas, duración y resultado)** y
**cambios de activo** (`f016dbd`, con el nombre visible y el RIC resultante en la misma línea,
que es lo que hacía falta para diagnosticar A2a). Verificado en dispositivo con JSON válido.

### Adenda 4 — Inanición como hipótesis de A4 · CONTRASTADA

Incluida explícitamente en la tabla de A4. **Sin evidencia**: no hay presión de memoria
(739 MB disponibles) ni correlación con la carga en los 17 TIE (su separación entre
operaciones es la más corta de los tres grupos, no la más larga). No se declara causa sin
datos.

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
| B2 (orden real en vivo) | **NO VERIFICADO** | no autorizado por el usuario |

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
- **El arreglo de A4 no se ha visto resolver un VOID real.** La causa raíz y el arreglo se
  sostienen sobre la latencia medida (141–327 ms contra un bypass de 350 ms) y sobre la firma
  del journal, pero **no hay una observación directa de una orden que antes fallaba y ahora se
  registre**, porque eso exige exactamente la sesión en vivo que no se autorizó. Sin ella, el
  arreglo queda **no confirmado en campo**.
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
