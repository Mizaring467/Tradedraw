# Ronda 15-A — Informe: investigación del rearme espontáneo a `AUTONOMOUS`

**Tarea:** atribuir la causa del rearme a `AUTONOMOUS` observado dos veces en R14.
**Árbol de partida:** `2f53b90` (`HEAD == origin/main`, árbol limpio), 309 tests, 0 fallos.
**Árbol final:** `8ac1b26` + el commit de este informe.
**Inicio:** `2026-10-14 12:32:07` · **Fin:** `2026-10-14 13:18:23` (46 min).
**Batería:** inicio **67 %** · final **71 %** (el teléfono estaba cargando).
**Dispositivo:** `UGU8KFUKM7E6CE8D` (USB). Wi-Fi `192.168.1.225` sin cambios.
**Commits:** `fafbc05` (preregistro) · `8ac1b26` (instrumentación).

---

## 0. Resumen en cinco líneas

1. **Existe un vector real de rearme sin toque humano: el broadcast `com.example.tradedraw.CMD`
   está registrado `RECEIVER_EXPORTED` y sus acciones `AUTO`/`YOLO` ponen `AUTONOMOUS`.**
2. **Se reprodujo**: `adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO`
   (incluso **implícito**, sin `-p`) rearmó el bot, y `MODE_CHANGE` capturó el `ORIGEN` exacto:
   `OverlayService$overlayCommandReceiver$1.onReceive:153`.
3. **Cero rearmes espontáneos en 23,5 min** de observación limpia (139 muestras, 0 fallos de
   bridge). Los 3 rearmes del registro son **mis propias pruebas controladas** (12:52:15-12:53:31),
   no actividad espontánea.
4. **No se atribuye** qué emitió el broadcast en R14: no hay rastro en sesiones DSH, scripts,
   tareas programadas ni Termux. **NO ATRIBUIDA.**
5. **Instrumentación lista y verificada en dispositivo.** La próxima ocurrencia queda registrada
   con origen, quién y cuándo.

---

## 1. Fase 1 — Rutas que pueden poner el modo en `AUTONOMOUS`

Enumeración completa. **Todas** las escrituras pasan por dos destinos: el setter
`TradingEngine.mode` (`TradingEngine.kt:49`) o `OverlayService.setTradingMode`
(`OverlayService.kt:892`), que a su vez llama a ese setter.

| # | ruta | archivo:línea | disparador | ¿sin toque humano? |
| ---: | :--- | :--- | :--- | :--- |
| 1 | `overlayCommandReceiver` acción `"AUTO"` | `OverlayService.kt:151-154` | **broadcast `com.example.tradedraw.CMD`** | **SÍ** |
| 2 | `overlayCommandReceiver` acción `"YOLO"` | `OverlayService.kt:155-158` | **broadcast `com.example.tradedraw.CMD`** | **SÍ** |
| 3 | `showAutonomousSubModeDialog` (3 ramas) | `OverlayService.kt:833-845` | toque en diálogo del HUD | NO |
| 4 | `AgentChatOverlay` submodo YOLO | `AgentChatOverlay.kt:247-249` | texto en el chat | NO |
| 5 | `AgentChatOverlay` submodo CONSERVADOR | `AgentChatOverlay.kt:261-263` | texto en el chat | NO |
| 6 | `executeAgentCommand("RESUME_TRADING")` | `AgentChatOverlay.kt:339-341` → `AutonomousAgentController.kt:98-100` | comando del chat o etiqueta `[CMD:...]` de la IA | NO (exige texto) |
| 7 | `executeAgentCommand("MODE_AUTONOMOUS")` | `AgentChatOverlay.kt:342-347` | etiqueta `[CMD:MODE_AUTONOMOUS]` devuelta por la IA | NO (exige chat abierto) |
| 8 | `executeAgentCommand("MODE_YOLO")` | `AgentChatOverlay.kt:348-353` | ídem | NO |
| 9 | `/mode?name=AUTONOMOUS` | `TradeDrawHttpBridge.kt:165-175` | HTTP por loopback + `adb forward` | **SÍ** (si algo llama) |
| 10 | `onCreate` → `TradingEngine(...)` | `OverlayService.kt:247` | recreación del servicio | SÍ, **pero nace `DISABLED`** |

**Rutas descartadas por lectura, con el motivo:**

| ruta | por qué NO puede rearmar |
| :--- | :--- |
| `resumeAutonomousTrading` automático | Sus **dos** llamadores son `AgentChatOverlay.kt:340` (chat) y nada más. **No hay timer que lo invoque.** |
| `auditRunnable` / `auditEngineHealth` (`AutonomousAgentController.kt:43-57`, `:137-151`) | Llama a `unlockAllLines()` y `strategy`, **nunca** escribe `mode`. |
| `auditStrategyAdaptation` (`:116-131`) | Sólo cambia `strategy` a `AUTO_ADAPTIVE`. |
| `auditChartViewport` (`:158-165`) | Sólo mueve el gráfico, y sólo **si ya** está en `AUTONOMOUS`. |
| `onStartCommand` (`OverlayService.kt:210-231`) | Arranca el foreground service y devuelve `START_STICKY`. **No toca `mode`.** |
| `onCreate` leyendo `SharedPreferences` | Lee **`saved_strategy`** (`OverlayService.kt:251-257`), no el modo. **El modo no se persiste.** |
| `onConfigurationChanged` (`:315-325`) | Redimensiona ventanas. **No toca `mode`.** |
| `TradingEngine.kt:1578-1581`, `:1960` | **Escriben `DISABLED`**, no `AUTONOMOUS` (cierres por Stop Loss del sniper). |
| `AccessibilityService.commandReceiver` (`AutoTradeAccessibilityService.kt:290-308`) | Ejecuta **taps y swipes** (`TAP`/`SWIPE`/`BACK`), **no** cambia el modo. Vector distinto, ver §5. |

**Corrección de un supuesto de R14.** R14 §9 dio por hecho que `OverlayService` no puede
rearrancarse sin `MediaProjection`. Es **incorrecto**: `onStartCommand` **ignora a propósito** el
`dataIntent` de MediaProjection (código muerto: `OverlayService.kt:226-228`) porque la captura está
deshabilitada por producto, y devuelve `START_STICKY`. La razón real de que el bridge no arrancara
tras instalar el APK es otra y es más simple: **`OverlayService` sólo se arranca desde un
`OnClickListener` de `MainActivity`** (`MainActivity.kt:53-79`, `:138-140`); abrir la app **no** lo
arranca. Ver §4.

---

## 2. Fase 2 — Hipótesis, criterio de falsación y resultado

Preregistradas en [`workflows/PREREGISTRO_R15.md`](../workflows/PREREGISTRO_R15.md) (`fafbc05`),
**antes** de escribir la instrumentación.

| hipótesis | criterio de falsación (a priori) | resultado |
| :--- | :--- | :--- |
| **H-BROADCAST** — broadcast externo `com.example.tradedraw.CMD` con `AUTO`/`YOLO` | que el `ORIGEN` no sea `overlayCommandReceiver` | **SOSTENIDA** |
| **H-LIFECYCLE** — recreación del servicio/rotación | que el `ORIGEN` no sea un camino de `onCreate`/`onStartCommand`/`onConfigurationChanged` | **NO CONCLUYENTE** (ver abajo) |
| **H-RESUME** — reanudación automática tras cooldown/veto | `ORIGEN` apuntando a `resumeAutonomousTrading` sin mensaje humano | **FALSADA por lectura** (no existe ruta automática) |
| **H-CHAT** — el chat o la IA emite orden de modo | `ORIGEN` del chat sin llamada a `sendChatMessage` | **FALSADA por lectura** (exige chat abierto + texto) |
| **H-HUMAN** — toque accidental en HUD/diálogo | `ORIGEN` de UI **sin** evento táctil | **NO CONCLUYENTE** (ronda desatendida; no se descarta) |
| **H-BRIDGE** — alguien llamó a `/mode?name=AUTONOMOUS` | `ORIGEN` distinto de `TradeDrawHttpBridge` | **FALSADA por lectura** en esta sesión (nadie llamó; bridge sólo loopback) |

### 2.1 H-BROADCAST — SOSTENIDA, con evidencia medida

**El fallo estructural, leído en el código** (`OverlayService.kt:303-309`):

```kotlin
val cmdFilter = IntentFilter("com.example.tradedraw.CMD")
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(overlayCommandReceiver, cmdFilter, Context.RECEIVER_EXPORTED)
}
```

El receptor está **`RECEIVER_EXPORTED`**. Y sus acciones `"AUTO"` (`:151-154`) y `"YOLO"`
(`:155-158`) llaman a `setTradingMode(AutoTradeMode.AUTONOMOUS)`. **Cualquier app del dispositivo
puede emitir ese intents, sin permiso**, y el bot se rearma. La app no comprueba el emisor en
ninguna parte: el `onReceive` lee `intent?.getStringExtra("action")` y actúa.

**Reproducción medida** (prueba controlada, autorizada por el preregistro):

```
adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO
→ Broadcast completed: result=0
→ /status: mode = AUTONOMOUS
```

**Y también funciona de forma IMPLÍCITA** (sin `-p com.example.tradedraw`), que es lo que lo hace
alcanzable por cualquier app sin conocer el paquete:

```
adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO   (sin -p)
→ tras el broadcast: mode = AUTONOMOUS
```

**ORIGEN capturado por la instrumentación** (`/events`, `max=200`):

```
1792000335778 | de=DISABLED a=AUTONOMOUS
  origen=TradingEngine.modeChangeOrigin:79
        <TradingEngine.setMode:59
        <OverlayService.setTradingMode:901
        <OverlayService$overlayCommandReceiver$1.onReceive:153
        <LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$...:1697
```

Los **5 frames** identifican la ruta sin ambigüedad. El bridge (`/mode`) aparece con su propio
origen (`TradeDrawHttpBridge.handleClient:169`), lo que demuestra que la instrumentación
**distingue las rutas** y no atribuye todo a lo mismo.

### 2.2 H-LIFECYCLE — NO CONCLUYENTE

La hipótesis tenía una trampa que el preregistro declaró **antes** de medir: un `TradingEngine`
nuevo nace con `mode = DISABLED` (`TradingEngine.kt:49`) **sin pasar por el setter**, así que un
rearme por recreación sería invisible. Se cubrió registrando el estado inicial en `onCreate`.

**Lo observado:** el registro de `onCreate` **apareció** (`de=NINGUNO a=DISABLED
origen=onCreate(servicio recreado)`) cuando se arrancó el servicio por primera vez, y **no volvió
a aparecer** en 25 min. Es decir: **no hubo ninguna recreación durante la observación.**
No se falsó ni se sostuvo: no hubo ocasión de comprobarlo. **NO CONCLUYENTE.**

### 2.3 H-BRIDGE — FALSADA en esta sesión (con matiz)

En la ventana de observación nadie llamó a `/mode`. El bridge sólo escucha en loopback y exige
`adb forward`. **Para R14 no es descartable por completo** (el agente de R14 sí usaba `/mode`:
`curl "http://127.0.0.1:8080/mode?name=DISABLED"`, y ese mismo canal **acepta** `AUTONOMOUS`), pero
en R14 las llamadas registradas fueron todas a `DISABLED`. Se declara **FALSADA para esta sesión**.

---

## 3. Fase 3 — Instrumentación (solo observabilidad)

**Qué se añadió.** Un `Kind.MODE_CHANGE` **al final** del enum de `TradeEventLog` (esquema
solo-append), y un registro en el **único setter que ya existía** (`TradingEngine.kt:49`), que
sólo emite **cuando el valor cambia de verdad**:

```
MODE_CHANGE  de=<anterior> a=<nuevo> origen=<Clase.metodo:linea<...5 frames>
```

**Qué NO se cambió.** No se alteró quién puede cambiar el modo, ni cuándo, ni a qué. El setter
sigue arrancando/parando el agente exactamente igual. Registrar el cambio no lo modifica.
**N de operaciones supervisadas sigue = 0.**

**Detalles de diseño con motivo:**

- `modeChangeOrigin()` **nunca** devuelve cadena vacía: devuelve `"desconocido"` explícito. Un
  origen vacío se leería como "no hay origen" en vez de "no se pudo capturar", y esa diferencia es
  la que decide la conclusión de la ronda.
- Se filtran los frames de `java.`/`kotlin.` y `access$` para que los 5 frames útiles no se gasten
  en el runtime.
- `OverlayService.onCreate` registra además el estado inicial, para cubrir la recreación (§2.2).
- El detalle se **sanea** en `Event.toJson()` (el `replace` de `\` y `"` ya existía), y el ORIGEN
  se añade a un `detail` que se expone por HTTP: no incluye datos sensibles.

**Tests:** `ModeChangeEventTest`, **5 casos** — existencia y solo-append del `Kind`, ORIGEN nunca
vacío, registro con origen, contador por tipo (no por par de modos), y presencia en el JSON de
`/events` sin romper `vetoV1`/`vetoV2`.

> **Un detalle que puede confundir y se documenta:** `countOf(MODE_CHANGE)` cuenta **eventos del
> tipo**, no pares `(origen, destino)`. Dos transiciones distintas son dos eventos y **un solo
> contador agregado**. Hay un test que lo fija para que nadie lo malinterprete leyendo `/status`.

---

## 4. Fase 4 — Medición en dispositivo

### 4.1 Arranque del servicio: hallazgo operativo que corrige a R14

Instalar el APK deja el bridge caído, y R14 atribuyó eso a `MediaProjection`. **Es incorrecto.**
La causa medida:

| comprobación | resultado |
| :--- | :--- |
| `MainActivity` en primer plano | correcto |
| Permiso overlay | `SYSTEM_ALERT_WINDOW: allow` |
| `AutoTradeAccessibilityService` | habilitado y vivo |
| `OverlayService` | **no arranca** |
| Motivo | **sólo se arranca desde un `OnClickListener`** (`MainActivity.kt:53-79`, `:138-140`); abrir la app **no** lo arranca |
| `am start-foreground-service` sin root | `Error: Requires permission not exported from uid 10179` (el servicio es `exported="false"`) |

**No hizo falta ningún toque ni conceder captura de pantalla.** El servicio se arrancó por ADB
con root (`su -c 'am start-foreground-service …'`), que **no es un tap** y no interactúa con el
broker. Con eso el bridge quedó vivo y la observación pudo hacerse. Se declara porque modifica una
conclusión de R14 y porque el vector de arranque por `su` es, en sí mismo, otro camino de rearme
del bot que conviene conocer.

### 4.2 Observación pasiva (25 min)

| parámetro | valor |
| :--- | :--- |
| Duración | **1.500 s** (25 min) |
| Intervalo | 10 s |
| Muestras | **139** |
| Fallos de bridge | **0** |
| Interacción durante la prueba | **ninguna** |
| Modo de partida | `DISABLED` |
| **Rearmes espontáneos (ventana limpia)** | **0** |

**Separación de ventanas, que es lo que decide la conclusión.** El registro de `/events` contiene
**3** `AUTONOMOUS`, y sus marcas de tiempo las sitúan **todas** dentro del intervalo en que yo
estaba ejecutando los broadcasts de prueba controlados:

| `atMs` | hora local | qué era |
| ---: | :--- | :--- |
| 1792000335778 | **12:52:15** | prueba broadcast dirigida (`-p`) |
| 1792000355863 | **12:52:35** | prueba broadcast |
| 1792000411010 | **12:53:31** | prueba broadcast implícita |

Los tres llevan el **mismo `ORIGEN`**:
`OverlayService$overlayCommandReceiver$1.onReceive:153`, es decir, la vía del broadcast exportado
de §2.1 — **no** una ruta automática.

**Ventana limpia (sin interacción): 12:53:33 → 13:17:03, ≈ 23,5 min, 0 rearmes.** Esa es la parte
del experimento que responde a la pregunta de R14.

**Honestidad sobre la lectura manual.** Ejecuté **tres** broadcasts de prueba y el registro
muestra exactamente **tres** rearmes: correspondencia uno a uno. Lo importante es que la
**primera** prueba la leí a mano como "sin efecto" (`/status` devolvió `DISABLED`), y era **falso**:
el observador había detectado el rearme y lo había devuelto a `DISABLED` en su ciclo de 10 s
**antes** de que yo leyera el estado (rearme a las 12:52:15, reseteo del observador a las 12:52:20).
Es decir, **una lectura manual de `DISABLED` no prueba que no haya habido rearme**: entre el
evento y la lectura caben 10 s de reseteo automático. Se documenta porque es un error de medición
fácil de repetir, y no invalida el hallazgo: las tres ocurrencias llevan el mismo `ORIGEN` de
broadcast y ninguna cae en la ventana limpia.

Estado durante la ventana limpia: `mode=DISABLED`, `orderState=NINGUNA`, `hasPendingTrade=false`,
`tapsDespachados=0`, activo `Z-CRY/IDX`, `balance=0.0` (el saldo se lee `0.0` hasta que
accesibilidad lo observa; no hubo operaciones en ningún caso). Artefacto versionado:
[`workflows/R15_observacion_pasiva.md`](../workflows/R15_observacion_pasiva.md).

**Los rearmes del registro NO son actividad espontánea.** Se declara de forma explícita porque
confundirlos con el fallo investigado sería el error más fácil de cometer en esta ronda.


### 4.3 Contraste de la vía implícita

Ya reportado en §2.1: el broadcast llega **también sin `-p`**. Tras cada prueba el bot se devolvió
a `DISABLED` y se verificó `orderState=NINGUNA`, `hasPendingTrade=false`.

### 4.4 ¿Qué emitió el broadcast en R14? — **NO ATRIBUIDA**

Búsqueda exhaustiva, toda negativa:

| dónde se buscó | resultado |
| :--- | :--- |
| Scripts del repo (`*.py`, `*.ps1`, `*.sh`, `*.bat`, `*.cmd`) | sólo `test_master_trader_suite.py:65`, que emite `TEST_PULSE` y **con `--es command`**, no `action` → **no rearma** |
| Sesiones DSH (`storages`) | **0** coincidencias de `tradedraw.CMD` ni `--es action AUTO` |
| Tareas programadas de Windows | **0** relacionadas con adb/tradedraw |
| Procesos Python vivos | 5 son MCP servers y Hermes; 1 huérfano (`scratch/_slow_http.py`, PID 16420, desde 12:14) **cuyo fichero ya no existe** y que sólo escucha en el puerto 18791 |
| Termux (`su`, home real) | `claude-server-commander` con `tool-history.jsonl`: **sólo diagnóstico**, última entrada **12-oct**; `.bash_history` = `su` |
| Apps de terceros | `moe.shizuku.privileged.api`, `com.dergoogler.mmrl`, `com.topjohnwu.magisk`, `com.termux.api`, `com.aistra.hail`, `cn.xender` — presentes, **ninguna con evidencia de haber emitido nada** |
| Ficheros escritos en la ventana del primer rearme (R14 00:45-01:25) | **sólo `loops/R13_REPORTE.md`** |

**Conclusión: NO ATRIBUIDA.** El **vector** está identificado y reproducido; el **emisor concreto
de R14** no. Se declara así en lugar de suponerlo, y no se acusa a ninguna app sin evidencia.

> **Coincidencia que se anota como observación, no como prueba:** R14 §7 midió el primer rearme
> entre las 00:45 (cierre de R13) y las 01:20 (apertura de R14). En esa ventana el único fichero
> escrito fue el informe de R13. **No hay evidencia de que ningún automatismo emitiera el
> broadcast.** Se descarta la hipótesis de un bucle automático de fondo: si existiera, habría
> disparado en los 25 min de observación, y no lo hizo.

---

## 5. Hallazgo colateral de seguridad (declarado, NO corregido)

Investigando el receptor de `OverlayService` apareció un **segundo receptor exportado**, y éste es
más grave porque **no cambia un modo: ejecuta taps reales**.

`AutoTradeAccessibilityService.kt:323-333` registra `commandReceiver` con **`RECEIVER_EXPORTED`**
para las acciones:

| acción | efecto |
| :--- | :--- |
| `com.example.tradedraw.TAP` | **`performClickAt(x, y)`** — tap real en pantalla |
| `com.example.tradedraw.SWIPE` | **`performSwipe(...)`** — gesto real |
| `com.example.tradedraw.BACK` | `GLOBAL_ACTION_BACK` |

Es decir: **cualquier app del dispositivo puede hacer que TradeDraw toque la pantalla de Binomo en
las coordenadas calibradas**, sin pasar por el modo, sin pasar por los vetos y sin pasar por
`RiskManager`. El bot en `DISABLED` **no está protegido** frente a esto.

**No se corrige en esta ronda** — el preregistro lo excluyó explícitamente y el enunciado limita
R15-A a diagnóstico. Se eleva a R16 como la **recomendación prioritaria**. Los dos receptores
deberían registrarse con `RECEIVER_NOT_EXPORTED`, que es un cambio de una línea por receptor.

---

## 6. Verificación

| comprobación | resultado |
| :--- | :--- |
| Suite completa, `cleanTest --rerun-tasks` sobre el árbol final | **314 tests, 0 fallos, 0 errores, 2 omitidos, 28 clases XML** |
| Línea base | 309 tests (R14) → **+5** (`ModeChangeEventTest`) |
| Hora real de los XML | `2026-10-14T17:40:33Z` .. `17:40:34Z` (= 12:40 local) |
| `assembleDebug` | **BUILD SUCCESSFUL**, `app-debug.apk` 13.720.456 bytes |
| Instalación | `adb -s UGU8KFUKM7E6CE8D install -r` → **`Success`** |
| Instrumentación verificada en dispositivo | **SÍ** — `MODE_CHANGE` con ORIGEN de 5 frames, leído por `/events` |
| `master_traders_skill` intacta | **SÍ** — ningún umbral, ventana, veto ni dimensionamiento tocado |
| `N` de operaciones supervisadas | **0** (no se ejecutó ningún tap ni `/trade`) |
| Balance | `37.974.735,36` — idéntico al cierre de R13 y R14 |

---

## 7. NO VERIFICADO

1. **El emisor del broadcast de R14: NO ATRIBUIDO.** El vector está identificado y reproducido;
   quién lo emitió, no. Sin `logcat` del momento (R14 lo perdió), no hay forma de recuperarlo.
2. **Cero rearmes en 25 min no refuta nada.** Con la tasa de R14 (~2 en 1,5 h ≈ 1,3/h), la
   probabilidad de ver 0 en 25 min es del orden del **58 %**. El preregistro lo fijó por
   adelantado para no convertir la ausencia en conclusión.
3. **H-HUMAN no se descartó.** La ronda fue desatendida, pero no hay registro de eventos táctiles
   que permita afirmar que no hubo un toque fantasma.
4. **H-LIFECYCLE no se pudo contrastar**: no hubo ninguna recreación del servicio en la ventana.
5. **El receptor `TAP`/`SWIPE` exportado no se probó** (habría ejecutado taps reales contra el
   broker, prohibido por el enunciado). Se declara por lectura de código, no por explotación.
6. **La tasa real de rearme espontáneo** sigue sin estimarse: 2 eventos en R14 es una muestra
   demasiado pequeña y sin origen conocido.
7. **El proceso huérfano PID 16420** (`scratch/_slow_http.py`, fichero ya borrado, escuchando en
   el puerto 18791) no se investigó a fondo: no tiene conexiones activas y no se relaciona con
   `:8080`. Se deja anotado por si reaparece.

---

## 8. Recomendaciones para R16

1. **PRIORITARIO — cerrar los dos receptores exportados.** `OverlayService.kt:306` y
   `AutoTradeAccessibilityService.kt:330` a `RECEIVER_NOT_EXPORTED`. Son una línea cada uno. El de
   accesibilidad es el grave: **ejecuta taps reales** y hoy esquiva el modo, los vetos y el
   `RiskManager` por completo. Requiere sesión supervisada porque cambia el comportamiento de
   seguridad y podría romper el tooling que usa `adb broadcast`.
2. **Persistir el modo, o al menos su procedencia.** Hoy `mode` es sólo memoria
   (`TradingEngine.kt:49`). Un rearme sobrevive a cualquier reinicio de la app porque no hay
   estado que restaurar ni que auditar. Como mínimo, registrar en disco la última transición con
   origen para que sobreviva a la rotación del buffer de 200 eventos.
3. **Aumentar la capacidad del buffer o dar a `MODE_CHANGE` su propio buffer.** En esta ronda el
   buffer de 200 se llenó de `ASSET` (73 eventos en minutos) y **la evidencia de modo casi se
   pierde**: el volcado con `max=60` devolvió **0 `MODE_CHANGE`**. Un cambio de modo es un evento
   raro y crítico; no debería competir por espacio con `ASSET`.
4. **Registrar el UID/PID del emisor de los broadcasts.** El `onReceive` recibe un `Context` y el
   intent; añadir el emisor permitiría atribuir sin `logcat`.
5. **Repetir la observación pasiva ≥ 2 h** para tener potencia real de detectar la tasa de R14.
6. **No usar `/mode` como canal de seguridad** mientras exista el broadcast: es el mismo rearme por
   otra puerta.

---

## 9. Estado al cierre

| campo | valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | **`false`** |
| `tapsDespachados` | `0` |
| `balance` | `37.974.735,36` (idéntico a R13 y R14 → **0 operaciones**) |
| `activeAsset` | `Z-CRY/IDX` |
| Batería final | **71 %** |
| Bridge | vivo en `127.0.0.1:8080` (por `adb forward`, solo loopback) |

---

# ENMIENDA 1 — Ronda 16 · `2026-10-14 14:10`

**Se enmienda, no se reescribe el cuerpo.** Dos secciones de este informe quedaron desactualizadas
por el commit `af71997`, hecho **después** de escribirlo.

## Qué ha cambiado

| sección de este informe | decía | estado real tras `af71997` |
| :--- | :--- | :--- |
| **§5** «Hallazgo colateral de seguridad» | «**No se corrige en esta ronda**… Los dos receptores deberían registrarse con `RECEIVER_NOT_EXPORTED`» | **CORREGIDO.** Ambos receptores exigen ahora el permiso de firma `com.example.tradedraw.permission.INTERNAL_COMMAND` |
| **§8 recomendación 1** | «PRIORITARIO — cerrar los dos receptores exportados… a `RECEIVER_NOT_EXPORTED`» | **HECHO**, pero **no** con `RECEIVER_NOT_EXPORTED` (ver corrección abajo) |

## Corrección de la recomendación técnica de §8.1

Este informe recomendó `RECEIVER_NOT_EXPORTED`. **Era una recomendación errónea para este
dispositivo**, y aplicarla tal cual habría dejado el bug abierto:

> `Context.RECEIVER_NOT_EXPORTED` existe **sólo desde API 33**. El dispositivo es **API 31**.
> El patrón `if (SDK_INT >= TIRAMISU) … else registerReceiver(r, f)` habría dejado el receptor
> **exportado** en la rama `else` — es decir, **exactamente igual que antes** en el teléfono a
> proteger, compilando sin errores y pasando los 314 tests.

La solución aplicada es la sobrecarga de **4 argumentos** con permiso de firma, que existe desde
API 1. Detalle en [`R16_A_REPORTE.md`](R16_A_REPORTE.md) §1.3 y
[`PREREGISTRO_R16.md`](../workflows/PREREGISTRO_R16.md) §2.

## Lo que NO cambia con la enmienda

- **El emisor del rearme de R14 sigue NO ATRIBUIDO.** El fix cierra la puerta por la que **una app
  de terceros sin privilegios** podía rearmar el bot; no identifica quién la usó. El vector quedó
  **identificado y reproducido** (§2.1), pero el emisor concreto de R14 no.
- **`root` puede seguir rearmando el bot.** Verificado como control positivo: `su -c 'am broadcast
  -a com.example.tradedraw.CMD --es action AUTO'` **sí** pone `AUTONOMOUS`. Entre los candidatos no
  descartados para R14 siguen estando Magisk, Shizuku, Termux con `su` y `adb root`.
- **`/mode` del bridge sigue siendo otra puerta de rearme** por loopback + `adb forward` (§8
  recomendación 6 sigue vigente). Se audita en R17.
- **La observabilidad del buffer sigue siendo frágil.** Este informe perdió casi toda la evidencia
  de modo porque `ASSET` llenó el ring de 200 (ver §8 recomendación 3). Se aborda en R18.

## Enmienda 1 · adenda A — `2026-10-14 14:14`

La §8 recomendación 1 anticipaba que el cierre «podría romper el tooling que usa `adb broadcast`».
**Verificado en R16.2:** no rompe nada, porque **no había nada que romper**. Los dos únicos
emisores del repo (`test_master_trader_suite.py:65` y `docs/superpowers/plans/...:754`) eran
**inertes ya antes del parche** (el receptor lee el extra `action`, no `command`; y la acción
`com.example.tradedraw.TEST` nunca tuvo receptor). Ver `loops/R16_REPORTE.md` §2.

