# Ronda 16-A — Informe: cierre de los receptores de comando exportados

**Tarea:** cerrar los dos receptores de broadcast exportados que R15-A identificó y reprodujo.
**Árbol de partida:** `afa296c` (`HEAD == origin/main`, árbol limpio).
**Inicio:** `2026-10-14 13:24:00` · **Fin:** `2026-10-14 13:52:00` (28 min).
**Preregistro:** [`workflows/PREREGISTRO_R16_A.md`](../workflows/PREREGISTRO_R16_A.md).
**Batería:** inicio **72 %** · final **73 %**.

---

## 0. Resumen en cinco líneas

1. **Los dos receptores exportados quedan cerrados** con un permiso de **firma** exigido al emisor.
2. **La solución obvia habría sido un no-op**: el dispositivo es **API 31** y
   `RECEIVER_NOT_EXPORTED` existe **sólo desde API 33**; la rama `else` habría dejado el receptor
   exportado justo en el teléfono a proteger.
3. **Verificado en dispositivo con control positivo**: `adb shell` ya **no** rearma el bot; `root`
   **sí**, y eso demuestra que el receptor sigue vivo y que lo único que bloquea es el permiso.
4. **314 tests, 0 fallos.** Cero cambios en lógica de trading. `N` de operaciones supervisadas = 0.
5. **`BinomoAuthActivity` exportada se declara fuera de alcance** y se eleva a R17: captura el
   `authtoken` de Binomo en un `WebView`.

---

## 1. El parche

### 1.1 Permiso de firma en el manifest

```xml
<permission
    android:name="com.example.tradedraw.permission.INTERNAL_COMMAND"
    android:protectionLevel="signature" />
```

### 1.2 Registro con el permiso exigido al emisor

Los dos receptores pasan de la sobrecarga de 3 argumentos con `RECEIVER_EXPORTED` a la de
**4 argumentos** con permiso:

```kotlin
registerReceiver(
    receiver, filter,
    "com.example.tradedraw.permission.INTERNAL_COMMAND",
    null
)
```

| archivo | línea del registro | acciones | antes |
| :--- | :--- | :--- | :--- |
| `OverlayService.kt` | `:325` | `AUTO`, `YOLO`, `SEMI`, `STOP`, `TEST`, `TEST_SELL`, … | `RECEIVER_EXPORTED` |
| `AutoTradeAccessibilityService.kt` | `:341` | `TAP`, `SWIPE`, `BACK` | `RECEIVER_EXPORTED` |

**Inventario tras el parche: cero `RECEIVER_EXPORTED` en todo el código.**

### 1.3 El hallazgo que cambió la solución

El propio informe de R15-A recomendó `RECEIVER_NOT_EXPORTED`. **Esa recomendación era errónea para
este dispositivo.** Medido:

```
adb shell getprop ro.build.version.sdk   →  31
adb shell getprop ro.build.version.release → 12
```

`Context.RECEIVER_NOT_EXPORTED` se añadió en **API 33**. El patrón habitual:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
} else {
    registerReceiver(r, f)      // en API 31 sigue EXPORTADO
}
```

habría compilado sin errores, pasado los 314 tests y **dejado el bot rearmable** en el teléfono de
pruebas. Es el modo de fallo más peligroso posible aquí: un arreglo que se declara arreglado y no
arregla nada. Se documenta con precisión para que la recomendación de R15-A no se aplique a ciegas
en un dispositivo antiguo.

La sobrecarga de 4 argumentos **existe desde API 1** y restringe la emisión a las apps firmadas con
la misma clave en **todas** las versiones.

---

## 2. Hipótesis y resultado

| # | hipótesis | resultado |
| :--- | :--- | :--- |
| **H1** | El parche cierra el vector de rearme por broadcast | **SOSTENIDA** (ver §3) |
| **H2** | El parche cierra el vector de taps | **SOSTENIDA por el mismo mecanismo**, con límite declarado (ver §4) |
| **H3** | El parche no rompe el registro de los receptores | **SOSTENIDA** (ver §5) |
| **H4** | El parche no altera ninguna decisión de trading | **SOSTENIDA**: cero cambios en `TradingEngine`, `RiskManager`, umbrales, ventanas o vetos |

---

## 3. H1 — El vector de rearme, medido

### 3.1 Antes del parche (R15-A, mismo dispositivo)

```
adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO
→ mode: AUTONOMOUS        ← REARME
```

### 3.2 Después del parche

| emisor | comando | resultado |
| :--- | :--- | :--- |
| `adb shell` (UID 2000), dirigido `-p` | `--es action AUTO` | `Broadcast completed: result=0` → **`mode: DISABLED`** |
| `adb shell` (UID 2000), **implícito** (sin `-p`) | `--es action AUTO` | `Broadcast completed: result=0` → **`mode: DISABLED`** |
| **`root`** (UID 0) — **control positivo** | `--es action AUTO` | **`mode: AUTONOMOUS`** |

**El control positivo es la pieza que hace concluyente la medición.** Sin él, un
`Broadcast completed: result=0` sin efecto podría significar «el receptor está muerto» o «el
servicio no está corriendo». Con él queda demostrado que:

1. El receptor **sigue registrado y funcionando** (root lo activa).
2. El broadcast **sigue llegando** al sistema (`result=0` en los tres casos).
3. Lo único que cambia el resultado es **el permiso del emisor**.

Es decir: el vector por el que **cualquier app instalada** rearmaba el bot ha quedado cerrado,
mientras que el operador privilegiado conserva el control.

### 3.3 Sobre `adb shell` como emisor

`adb shell` corre como **UID 2000** y **no** recibe permisos `signature` declarados por la app, por
eso deja de poder emitir. Esto **no** es una regresión del tooling: es exactamente el objetivo.
El preregistro §3 anticipó que `adb` podría conservar acceso; **no lo conserva**, lo cual es mejor
de lo previsto.

### 3.4 Reversibilidad para el operador

`root` conserva la capacidad de emitir todos los comandos. Si en el futuro el tooling necesita
volver a usar `adb broadcast`, la vía soportada es `su -c 'am broadcast …'`. Se documenta en el
informe para que nadie «arregle» el permiso pensando que está roto.

---

## 4. H2 — El vector de taps

**Mecanismo:** `AutoTradeAccessibilityService.commandReceiver` se registra con **el mismo permiso de
firma**, y el cambio es idéntico. La prueba de que el permiso se exige y se aplica es la de §3:
**el mismo emisor `adb shell` deja de poder emitir** sobre un receptor registrado con ese permiso.

**Límite declarado, honesto:** **no se ejecutó un broadcast `TAP` para verificar el bloqueo por
efecto**, porque un `TAP` que sí se entregara **ejecutaría un tap real contra el broker**, y el
enunciado lo prohíbe expresamente. La verificación es por **mecanismo compartido + control
positivo en el receptor gemelo**, no por explotación directa. Es una inferencia sólida (mismo
permiso, mismo patrón de registro, mismo resultado medido en el otro receptor), pero se declara
como inferencia, no como medición directa.

**Efecto neto:** un bot en `DISABLED` deja de ser manipulable por otra app. Antes, cualquier app
instalada podía provocar `performClickAt` en las coordenadas calibradas, esquivando el modo, los
vetos y `RiskManager`.

---

## 5. H3 — El parche no rompe nada

| comprobación | resultado |
| :--- | :--- |
| `OverlayService` arranca tras instalar el parche | **SÍ** — `ServiceRecord{dc89dce u0 com.example.tradedraw/.OverlayService}` |
| Bridge HTTP responde | **SÍ** — `/ping` → `{"status":"ok","app":"TradeDraw","port":8080}` |
| `logcat` con `Error registrando` | **0 coincidencias** |
| Ruta de la app que emita estos broadcasts (`sendBroadcast`) | **ninguna** en todo el código |
| Rutas de la UI/chat que cambian el modo | **intactas** (exigen toque humano) |

El último punto es el que justifica que el cierre sea seguro: **la app nunca emite estos
broadcasts**. Sólo los consumía desde fuera. Cerrarlos no puede romper un flujo interno, porque no
existe tal flujo.

**Nota sobre `test_master_trader_suite.py:65`:** ese script emite `TEST_PULSE` con `--es command`, y
el receptor lee `getStringExtra("action")`. **Nunca hizo nada**, ni antes ni ahora. No es una
regresión.

---

## 6. Verificación

| comprobación | resultado |
| :--- | :--- |
| Suite completa, `cleanTest --rerun-tasks` | **314 tests, 0 fallos, 0 errores, 2 omitidos, 28 clases XML** |
| `assembleDebug` | **BUILD SUCCESSFUL** in 2m 7s, `app-debug.apk` 13.720.445 bytes (`13:38:09`) |
| Instalación | `adb install -r` → **`Success`** |
| Verificación en dispositivo | **SÍ** — H1 con control positivo (§3.2) |
| `master_traders_skill` intacta | **SÍ** — ningún umbral, ventana, veto ni dimensionamiento tocado |
| `N` de operaciones supervisadas | **0** |
| Balance | `37.974.735,36` — idéntico a R13, R14 y R15 |

---

## 7. NO VERIFICADO

1. **El bloqueo de `TAP`/`SWIPE` no se probó por efecto** (§4): hacerlo habría disparado un tap
   real. Inferido por mecanismo compartido.
2. **El emisor del rearme de R14 sigue NO ATRIBUIDO.** Este parche cierra la puerta; no identifica
   quién la usó. El emisor pudo ser una app de terceros, `root`, o un `adb` de una sesión anterior.
3. **`BinomoAuthActivity` exportada sin filtro** no se corrigió (fuera de alcance declarado). Es
   lanzable por terceros, abre un `WebView` sobre `binomo.com/es/trading`, captura el `authtoken`
   por cookie, cabecera `authtoken=` y `localStorage`, y lo guarda en
   `SharedPreferences("ws_auth_token")`. **Puede ser una vía de fuga de credenciales.**
4. **No se comprobó la emisión desde una app de terceros real.** La restricción por permiso de
   firma es una garantía de la plataforma, verificada aquí por el rechazo a `UID 2000` y la
   aceptación a `UID 0`; no por el despliegue de un APK hostil.
5. **`RECEIVER_NOT_EXPORTED` no se usa en absoluto**, a propósito: no existe en API 31. Si el
   proyecto sube `minSdk` a 33, podría simplificarse a un solo argumento, pero el permiso de firma
   sigue siendo válido y suficiente en todas las versiones.

---

## 8. Recomendaciones para R17

1. **PRIORITARIO — revisar `BinomoAuthActivity` (`AndroidManifest.xml:36-41`).** Es la siguiente
   superficie abierta: `exported="true"` sin `intent-filter`, y maneja el `authtoken` del broker.
   Requiere decidir si `exported` puede pasar a `false` (se lanza internamente desde
   `MainActivity.kt:69` y `OverlayService.kt:1874`, ambas con `Intent` explícito, así que
   probablemente sí).
2. **Persistir el modo con su procedencia.** `TradingEngine.mode` sigue siendo sólo memoria.
3. **Buffer de eventos propio para `MODE_CHANGE`**, separado de `ASSET`.
4. **Auditar el resto de la superficie de `exported`.** Tras este parche quedan tres:
   `MainActivity` (launcher, necesario), `BinomoAuthActivity` (punto 1) y
   `AutoTradeAccessibilityService` (protegido por `BIND_ACCESSIBILITY_SERVICE`, correcto).
5. **Considerar subir `minSdk` a 33** en el futuro para poder usar el flag estándar, pero **no es
   necesario**: el permiso de firma cubre API 24+.

---

## 9. Estado al cierre

| campo | valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | **`false`** |
| `tapsDespachados` | `0` |
| `balance` | `37.974.735,36` (idéntico a R13/R14/R15 → **0 operaciones**) |
| Batería final | **73 %** |
| Receptor de rearme por broadcast | **cerrado** (permiso de firma) |
| Receptor de taps por broadcast | **cerrado** (mismo permiso) |
