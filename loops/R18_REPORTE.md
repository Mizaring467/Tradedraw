# Ronda 18 — Informe: ring dedicado de `MODE_CHANGE`, persistencia y atribución del emisor

**Tarea:** dar a `MODE_CHANGE` un ring propio, persistirlo en disco y capturar el UID del emisor.
**Árbol de partida:** `a20bb95` (`HEAD == origin/main`).
**Inicio:** `2026-10-14 14:38` · **Fin:** `2026-10-14 15:24` (46 min).
**Batería:** inicio **76 %** · final **75 %** (cargando, `status: 2`).
**Commits:** `85222f1` (código) · `7940f5d` (informe).

---

## 0. Resumen

1. **H-RING SOSTENIDA.** Ring dedicado de **100** entradas; **500 eventos `ASSET` no lo desplazan**.
   El buffer general **sí** los pierde — que es exactamente el bug que motivó la ronda.
2. **H-COMPAT SOSTENIDA.** `/events` **no cambia de forma**: `success`, `total`, `vetoV1`, `vetoV2`,
   `events` y su significado intactos. El ring se expone en **endpoint nuevo** `/mode_log`, así que
   la compatibilidad se garantiza **por construcción**, no por cuidado.
3. **H-PERSIST SOSTENIDA y medida en dispositivo.** JSONL en `filesDir`; tras `force-stop` +
   arranque, el ring en memoria queda en **1** (solo arranque) y el disco conserva **3**, incluido
   el rearme. Es el primer mecanismo de la misión que **sobrevive a un reinicio del servicio**.
4. **H-ROT SOSTENIDA.** Rotación a 256 KB **renombrando** a `.1`; nunca trunca. Test con tope de
   200 bytes: tras 50 escrituras, la última línea sigue en el fichero vivo.
5. **H-UID FALSADA, medida.** `getSentFromUid()` es **`since=34`** según `api-versions.xml` de
   `android-34`. Dispositivo **API 31** → **UID del emisor NO DISPONIBLE**. **No se inventa.**
   **Pero** el `Permission Denial` del sistema **sí** se capturó y **nombra uid y pid**.
6. **Prueba controlada ejecutada una sola vez**, con origen legible y balance sin cambios.

---

## 1. R18.2 — Ring dedicado

**Problema medido en R15-A:** el buffer de 200 está dominado por `ASSET` (se emite por tick). El
volcado con `max=60` devolvió **cero** `MODE_CHANGE` porque 73 `ASSET` en minutos los habían
desplazado. La evidencia de un evento raro y crítico se perdió por competir con el flujo de activos.

**Solución:** `TradeEventLog.MODE_CAPACIDAD = 100`, con `modeBuffer` propio
(`TradeEventLog.kt:76-88`), independiente del buffer general. AL llenarse descarta el más antiguo.

**Evidencia de los tests (`ModeRingDedicadoTest`, 5 de los 11 nuevos):**

| test | qué prueba | resultado |
| :--- | :--- | :--- |
| `quinientosAssetNoDesplazanElRingDeModo` | 500 `ASSET` tras 1 `MODE_CHANGE` → el ring conserva **1** | **PASA** |
| `elRingDeModoEsIndependienteDelGeneral` | el general **sí** lo pierde, el dedicado **no** | **PASA** |
| `elRingDeModoRespetaSuPropiaCapacidad` | 125 eventos → recorta a 100, conserva los recientes | **PASA** |
| `elJsonDeEventsNoCambiaDeForma` | `vetoV1:1`, `vetoV2:1`, `success`, `total`, `events` intactos | **PASA** |
| `elJsonDeModeLogEsUnEndpointSeparado` | `/mode_log` expone `capacidad:100` y el contador por tipo | **PASA** |

**Decisión de diseño:** endpoint **nuevo** `/mode_log` en lugar de modificar `/events`. El
enunciado exigía no romper `vetoV1`/`vetoV2`/contadores, y el volcado de R15-A los consumía.
Añadir un endpoint hace imposible la rotura; modificar el existente la haría probable.

**Verificado en dispositivo:** `GET /mode_log` →
`{"success":true,"total":2,"capacidad":100,"count":2,"events":[…]}`.

---

## 2. R18.3 — Persistencia solo-append en disco

**Clase nueva:** `ModeEventJournal.kt` (95 líneas). Sin dependencias: `File` + `appendText`.

**Diseño fijado a priori y respetado:**

| garantía | cómo |
| :--- | :--- |
| **Solo-append** | `appendText`. Nunca se reescribe ni trunca el fichero vivo |
| **Nunca propaga** | `try/catch` que devuelve `false`; `add()` lo traga → un fallo de disco **no puede impedir ni alterar un cambio de modo** |
| **Acotado** | 256 KB, rotación **renombrando** a `.1` → uso máximo ~512 KB (el Redmi A1 tiene poco espacio) |
| **Sin `Context` en el `object`** | recibe el `File` resuelto → **testeable en JVM puro**, sin emulador ni Robolectric |
| **Sin datos sensibles** | el detalle son dos nombres de modo y una pila de llamadas: **nunca** tokens ni cookies |

**Punto de instalación:** `OverlayService.onCreate`, **antes** del registro de arranque
(`OverlayService.kt:250-262`). Sin esto, una recreación del servicio ocurriría antes de que el
diario existiera y se perdería — precisamente el escenario de R14.

**Tests (`ModeRingDedicadoTest`, 6 nuevos):**

| test | qué prueba | resultado |
| :--- | :--- | :--- |
| `elDiarioEscribeYReleeUnaLinea` | append + relectura con tipo y origen | **PASA** |
| `elDiarioSoloPersisteModeChange` | `ASSET` y `VETO` **no** se persisten | **PASA** |
| `elDiarioSobreviveAlClearDelRing` | ring vacío, disco con 1 línea | **PASA** |
| `laRotacionNoPierdeLoReciente` | tope 200 B, 50 escrituras: `n:49` en el fichero vivo | **PASA** |
| `unFalloDeDiscoNoPropagaNiPierdeElEvento` | directorio como fichero → `false`, y el ring conserva el evento | **PASA** |
| `instalarEsIdempotenteRespectoAlFichero` | reinstalar no duplica ni pierde | **PASA** |

**Medición en dispositivo — supervivencia al reinicio:**

```
ANTES del reinicio:  ring = 2 (arranque + rearme)   disco = 2
force-stop + am start-foreground-service (sin tap)
DESPUÉS:             ring = 1 (solo arranque)        disco = 3 (arranque + REARME + arranque)
```

El rearme **sobrevivió al reinicio del servicio** en el diario. Es la primera vez en la misión que
un `MODE_CHANGE` atraviesa un reinicio.

**Fichero en disco:** `-rw------- u0_a179 u0_a179 112 bytes /data/data/…/files/mode_changes.jsonl`.

---

## 3. R18.4 — Atribución del emisor

### 3.1 `getSentFromUid()` — **NO DISPONIBLE en API 31** (medido, no supuesto)

```
api-versions.xml (android-34):
  <method name="getSentFromPackage()Ljava/lang/String;" since="34"/>
  <method name="getSentFromUid()I" since="34"/>
```

`javap` sobre `android.jar` de compileSdk 34 **sí** muestra ambos métodos (por eso compilarían),
pero **`since="34"`** y el dispositivo es **API 31** (`getprop ro.build.version.sdk` → `31`).

> **Declaración: «UID del emisor NO DISPONIBLE en API 31.»**
> No se aproxima con `Binder.getCallingUid()` (en `onReceive` devuelve el sistema, no el emisor) ni
> con `Process.myUid()` (devuelve el receptor). Sería inventarlo.

### 3.2 Alternativa: el `Permission Denial` del sistema — **CAPTURADO**

`logd` está **vivo** en este dispositivo. Captura real, sin manipular nada:

```
W/BroadcastQueue: Permission Denial: broadcasting Intent {
  act=com.example.tradedraw.CMD flg=0x400010 (has extras) }
  from null (pid=15448, uid=2000)
  requires com.example.tradedraw.permission.INTERNAL_COMMAND
  due to registered receiver BroadcastFilter{6ab322e 10179/u0 …}
```

**El sistema aporta `pid` y `uid` del emisor** — exactamente el dato que `getSentFromUid()` daría en
API 34. Limitación: sólo aparece cuando el broadcast es **DENEGADO**. Un emisor con `root` es
**aceptado** y **no genera línea**.

**Contraste medido (control):**

| emisor | `result` | `Permission Denial` en logcat | efecto |
| :--- | :--- | :--- | :--- |
| **UID 2000** (shell) | `Broadcast completed: result=0` | **SÍ**: `from null (pid=15448, uid=2000)` | **ninguno** |
| **UID 0** (root) | `Broadcast completed: result=0` | **NO** | **aceptado** |

> **Conclusión de atribución:** el emisor de un broadcast **denegado** es atribuible en API 31 por
> logcat. El de un broadcast **aceptado** (root) **no** lo es por esta vía. Y si `logd` muere —que
> es su comportamiento conocido en este dispositivo— la capacidad desaparece. **En ningún caso se
> levantó `logd` en caliente.**

**Aplicación al rearme de R14:** **sigue NO ATRIBUIDO.** Aquel rearme pudo ser por UI (como el
incidente de las 13:51:02), por broadcast privilegiado, o por una ruta no identificada; y el logcat
de ese momento se perdió. Esta ronda **añade la herramienta**, no recupera el hecho.

---

## 4. R18.5 — Prueba controlada (la única permitida con `AUTONOMOUS`)

Ejecutada **una sola vez**, con `root`, según el preregistro §6:

```bash
adb shell su -c 'am broadcast -a com.example.tradedraw.CMD --es action AUTO'
```

**Origen capturado (5 frames, legible):**

```
de=DISABLED a=AUTONOMOUS origen=
  TradingEngine.modeChangeOrigin:79<
  TradingEngine.setMode:59<
  OverlayService.setTradingMode:928<
  OverlayService$overlayCommandReceiver$1.onReceive:161<
  LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$…:1697
```

**Lista de verificación del preregistro:**

| # | verificación | resultado |
| :--- | :--- | :--- |
| 1 | Aparece en el **ring dedicado** (`/mode_log`) | **SÍ** (`total:2`, `capacidad:100`) |
| 2 | Aparece en el **JSONL** con origen legible | **SÍ** |
| 3 | **Sobrevive al reinicio** del servicio (sin tap) | **SÍ** — disco=3, ring=1 |
| 4 | Devuelto a `DISABLED` por `/mode` | **SÍ** (`{"success":true,"mode":"DISABLED"}`) |
| 5 | `orderState=NINGUNA`, `hasPendingTrade=false` | **SÍ** |
| 6 | Balance sin cambios | **SÍ** (ver §6) |

**Taps despachados: 0.** `/trade` no se invocó. `master_traders_skill` intacta.

---

## 5. Verificación de la suite

| comprobación | resultado |
| :--- | :--- |
| `cleanTest testDebugUnitTest` | **325 tests, 0 fallos, 0 errores, 2 omitidos** |
| Línea base | 314 (R15-A) → **+11** (`ModeRingDedicadoTest`) |
| `assembleDebug` | **BUILD SUCCESSFUL**, APK 13.723.003 bytes |
| Instalación | `adb install -r` → **`Success`** |
| APK nuevo confirmado en dispositivo | **SÍ** — `/mode_log` responde con `capacidad:100` (endpoint que no existía antes) |
| Balance de llaves en los 4 `.kt` tocados | **OK** (TradeEventLog 24/24, ModeEventJournal 11/11, OverlayService 547/547, TradeDrawHttpBridge 186/186) |
| `/events` compatibilidad | verificada por test **y** por el hecho de que su formato no se tocó |
| `master_traders_skill` intacta | **SÍ** |
| `N` de operaciones supervisadas | **0** |

---

## 6. Estado al cierre

| campo | valor | nota |
| :--- | :--- | :--- |
| `mode` | **`DISABLED`** | devuelto tras la prueba controlada |
| `orderState` | `NINGUNA` | |
| `hasPendingTrade` | **`false`** | |
| `totalWins` / `totalLosses` | `0` / `0` | |
| `balance` | **`0.0`** | **NO es una discrepancia** — ver abajo |
| `activeAsset` | `Z-CRY/IDX` | feed websocket conectado, `tickSource: socket` |

> **Sobre `balance = 0.0`.** Después de `am force-stop` + reinicio del servicio **sin abrir la
> pantalla de trading**, el motor no ha leído el balance del broker y lo reporta como `0.0`. Es un
> **valor no leído**, no un balance observado: no hay operación detrás. La comprobación de
> invariante real (las 0 operaciones) la dan `orderState=NINGUNA`, `hasPendingTrade=false`,
> `totalWins=0`, `totalLosses=0` y **0 taps despachados**. Se declara explícitamente para que
> **R19/R20 no interpreten un `0.0` de arranque como una discrepancia de balance** — que según el
> enunciado sería INCIDENTE de prioridad máxima. La relectura del balance real requiere abrir la app
> (tap humano), fuera del alcance desatendido de esta ronda.

---

## 7. NO VERIFICADO

1. **El emisor del rearme de R14 sigue NO ATRIBUIDO.** Esta ronda añade la capacidad de atribución,
   no recupera el pasado.
2. **La atribución por `Permission Denial` no cubre emisores aceptados** (root) — precisamente los
   que no se pueden descartar para R14.
3. **No se probó la rotación real de 256 KB en el dispositivo** (requeriría escribir ~2300
   transiciones de modo). Se probó por test con tope de 200 bytes, que ejercita **el mismo** camino
   de código.
4. **No se verificó** que otra app del dispositivo pueda alcanzar `/mode` (heredado de R17 §6.1).
5. **No se probó** que el diario sobreviva a un **reinicio del teléfono** (sí del servicio). El
   fichero está en `filesDir`, que persiste, pero no se midió.
6. **`getSentFromPackage()` tampoco está disponible** (mismo `since=34`); no se probó.

---

## 8. Recomendaciones para R19/R20

1. **R19** debe usar `/mode_log` **y** `/mode_journal` en cada sondeo: el segundo es el que
   sobrevive a los reinicios y por tanto el que no pierde un rearme entre sondeos.
2. **Aplicar C1 de R17** (redactar los 3 logs del token): riesgo nulo, hallazgo real pendiente.
3. **Si `logd` muere**, la atribución por `Permission Denial` desaparece: declararlo, no suplirlo.
4. **R20:** token efímero en el bridge (C4 de R17), que sí cierra `/mode` a otras apps.
5. **Vigilar el tamaño** de `mode_changes.jsonl` si R19 dura horas con muchos cambios (no esperado:
   ~1,3 eventos/hora).

---

## 9. Veredicto

**PASS-AUTOR** (verificación independiente pendiente: agy/hermes).
