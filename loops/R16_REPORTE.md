# Ronda 16 — Informe: formalización del fix `af71997` y verificación del tooling

**Tarea:** formalizar retroactivamente el cierre de los receptores exportados, verificar el impacto
sobre el tooling y documentar los límites del fix.
**Árbol de partida:** `af71997` (`HEAD == origin/main`, árbol limpio).
**Inicio:** `2026-10-14 13:56` · **Fin:** `2026-10-14 14:16` (20 min).
**Batería:** inicio **74 %** (cargando, `status: 2`) · final **75 %**.
**Commits:** `875dbcb` (preregistro retroactivo) · ver SHA de cierre al pie.

---

## 0. Resumen en cinco líneas

1. **`af71997` queda formalizado** con preregistro retroactivo honesto: el fix se hizo **antes** de
   preregistrarse, fuera del alcance de `PREREGISTRO_R15` §5, y eso se declara sin adornos.
2. **Los 4 criterios de éxito se cumplen**: `adb shell` (UID 2000) bloqueado dirigido e implícito,
   `root` como control positivo, cero emisores internos, cero cambios de decisión de trading.
3. **El tooling no se rompió porque nunca funcionó**: los dos únicos emisores del repo eran
   **inertes ya antes del parche**.
4. **El fix NO cierra a `root`, Magisk, Shizuku ni Termux-`su`** — medido, no supuesto. El emisor
   del rearme de R14 **sigue NO ATRIBUIDO**, y los privilegiados son candidatos no descartados.
5. **`main` local avanzada** a `origin/main` sin forzar (era ancestro). `R15_REPORTE.md` enmendado
   con fecha, sin reescribir el cuerpo.

---

## 0-bis. INCIDENTE registrado al abrir la misión — `13:51:02`

Antes de ejecutar nada, el estado inicial ya era `mode = AUTONOMOUS`. Se registra aquí por ser
exactamente el fenómeno bajo investigación.

| hora | evento |
| :--- | :--- |
| 13:38:58 | `MODE_CHANGE de=NINGUNO a=DISABLED origen=onCreate(servicio recreado)` |
| 13:39:47 | `→ AUTONOMOUS` origen `overlayCommandReceiver.onReceive:153` — **control positivo de R16-A** |
| 13:39:58 | `→ DISABLED` origen `TradeDrawHttpBridge.handleClient:169` — reseteo de R16-A |
| **13:51:02** | **`→ AUTONOMOUS` origen `OverlayService.showAutonomousSubModeDialog$lambda$36:855`** |

**Reconstrucción del logcat (13:50:52–13:51:02):**

| hora | línea |
| :--- | :--- |
| 13:50:52.018 | `START u0 {act=MAIN cat=[LAUNCHER] flg=0x10000000 pkg=com.example.tradedraw cmp=.../.MainActivity} from uid 10080` |
| 13:50:52.181 | foco: `miui.global.packageinstaller/...GlobalPackageInstallerActivity` → `null` |
| 13:50:53.138 | foco → `com.example.tradedraw/.MainActivity` |
| 13:50:55.867 | **`miuibooster onScroll package name: com.example.tradedraw`** ← gesto de scroll real |
| 13:50:57.444 | `MainActivity` pierde foco |
| 13:50:58.184 | foco → `com.marketly.trading/...TradingActivity` (Binomo) |
| 13:50:59.9–13:51:01.2 | foco encadenado entre 3 ventanas overlay (apertura/cierre de diálogos) |
| **13:51:02** | **rearme** |

`uid 10080` = `com.miui.global.packageinstaller`. El instalador MIUI, abierto con el botón «Abrir»
tras el `adb install` de R16-A (13:38), lanzó `MainActivity` a las 13:50:52.

**Atribución:** `showAutonomousSubModeDialog` (`OverlayService.kt:843-874`) sólo se alcanza desde
`showModeDialog` (`:821-841`), y el rearme exige **dos selecciones del usuario** (modo → submodo).
**No existe ruta de código que la invoque sola.** Hay **indicio fuerte de interacción humana**
(botón «Abrir» + `onScroll` + dos selecciones). **NO ATRIBUIDO con certeza**: no se descarta
inyección de gestos por una app con accesibilidad.

**Lo que sí se afirma:** no fue el vector del broadcast (el `ORIGEN` es UI, no `onReceive`); no
hubo operación (`orderState=NINGUNA`, `hasPendingTrade=false`, `tapsDespachados=0`,
`balance=37.974.735,36` idéntico al cierre previo); **no hay discrepancia de balance** → no procede
clasificarlo como INCIDENTE de trading.

**Acción:** devuelto a `DISABLED` a las 13:57:50 y verificado.

**SUPUESTO adoptado para el resto de la misión:** tras un `adb install`, el instalador MIUI queda
abierto y puede auto-lanzar la app. Se cerrará explícitamente
(`am force-stop com.miui.global.packageinstaller`) antes de toda medición pasiva, o el teléfono no
está desatendido.

---

## 1. R16.1 — Preregistro retroactivo

[`workflows/PREREGISTRO_R16.md`](../workflows/PREREGISTRO_R16.md) (`875dbcb`). Declara:

- El fix se implementó `13:24-13:38`, **en la misma sesión que R15-A**, y se pusheó sin preregistro
  ni informe propios.
- Estaba **fuera** de `PREREGISTRO_R15` §5, que lo declaró explícitamente fuera de alcance.
- `PREREGISTRO_R16_A.md` **sí** existió antes de compilar el parche definitivo, con sus 4
  hipótesis y falsaciones. No fue improvisado.
- **Criterio de éxito fijado a priori** para la parte prospectiva (la verificación de esta ronda):
  (a) UID 2000 bloqueado dirigido e implícito; (b) root sigue funcionando como control positivo;
  (c) cero emisores internos; (d) cero cambios de decisión de trading.
- Regla de atribución explícita: si (b) falla, (a) es un **falso positivo** (receptor muerto).

---

## 2. R16.2 — Verificación del tooling

### 2.1 Inventario completo de emisores

Búsqueda de `am broadcast` en `*.py *.ps1 *.sh *.bat *.cmd *.md *.kt *.yml *.yaml`, excluyendo
`build/` y `.git/`:

| archivo:línea | comando | ¿ejecutable? | ¿funcionaba antes? |
| :--- | :--- | :--- | :--- |
| `test_master_trader_suite.py:65` | `am broadcast -a …CMD --es command TEST_PULSE` | **sí** | **NO** — el receptor lee `getStringExtra("action")`, no `command` |
| `docs/superpowers/plans/2026-09-04-…md:754` | `am broadcast -a com.example.tradedraw.TEST` | no (doc) | **NO** — esa acción **nunca tuvo receptor** |
| `loops/R15_REPORTE.md`, `loops/R16_A_REPORTE.md`, `workflows/*.md` | documentación de las pruebas | no | — |

### 2.2 Por qué no hay regresión

**Ambos emisores eran inertes ya antes del parche**, por razones independientes del permiso:

1. **`TEST_PULSE`**: el receptor lee el extra **`action`** (`OverlayService.kt:148`); el script envía
   `command`. El extra que llega no existe → `action = ""` → ningún `when` casa. Además `TEST_PULSE`
   **no está entre las acciones soportadas**.
2. **`com.example.tradedraw.TEST`**: esa acción no aparece registrada en ningún receptor del código
   ni declarada en el manifest (que **no tiene ningún `<receiver>`**). Acción huérfana de un plan
   antiguo.

**Medición en dispositivo (UID 2000, tras el parche):**

| comando | salida | efecto |
| :--- | :--- | :--- |
| `--es command TEST_PULSE` | `Broadcast completed: result=0` | ninguno (como antes) |
| `--es action STOP` | `Broadcast completed: result=0` | ninguno |

> **Nota metodológica importante:** `result=0` **no** significa «el comando se ejecutó». El sistema
> devuelve ese valor aunque **ningún** receptor reciba el broadcast. Comprobar el resultado del
> comando es **inútil**; hay que comprobar el **efecto** (`/status`). Queda documentado en
> `AGENTS.md` §8.2-bis porque es una trampa fácil de repetir.

### 2.3 Actualización de `AGENTS.md`

Añadida **§8.2-bis** con: el aviso exigido («los broadcasts de comando ahora exigen el permiso
signature; desde adb shell no llegan; usar `su -c` y SOLO para pruebas controladas»), la tabla de
las dos acciones afectadas, la advertencia de que `result=0` no prueba efecto, la razón por la que
**no** debe usarse `RECEIVER_NOT_EXPORTED` (API 31 vs API 33) y la constancia de que los dos
emisores históricos eran inertes.

---

## 3. R16.3 — Límite de alcance: qué NO cierra el fix

**Medido, no supuesto.** `root` (UID 0) **sigue** rearmando el bot:

```
su -c 'am broadcast -a com.example.tradedraw.CMD --es action AUTO'
→ mode: AUTONOMOUS        ← CONTROL POSITIVO, sigue funcionando
```

Por tanto **no** quedan cubiertos:

| vector | presente en el dispositivo | ¿puede rearmar? |
| :--- | :--- | :--- |
| Apps de terceros **sin privilegios** | — | **NO** (cerrado) |
| `root` / `su` | `com.topjohnwu.magisk` | **SÍ** |
| Shizuku | `moe.shizuku.privileged.api` | **SÍ** (actúa con shell o root) |
| Termux con `su` | `com.termux` | **SÍ** |
| `adbd` con root | no disponible (`ro.debuggable=0`) | n/a |

**Consecuencia para la investigación:** el emisor del rearme de R14 **sigue NO ATRIBUIDO**, y entre
los candidatos **no descartados** están precisamente los privilegiados.

**Por qué es el comportamiento deseado:** un control positivo que falla significaría que el
mecanismo no funciona. Que root conserve acceso es correcto: es el operador. El objetivo del fix era
cerrar el vector por el que **cualquier app instalada** podía rearmar el bot o **inyectar taps
reales**, que es el que estaba demostrado.

**Enmienda aplicada a `loops/R15_REPORTE.md`** (ENMIENDA 1, fechada, al final, sin reescribir el
cuerpo): §5 y §8.1 quedaron desactualizadas; se corrigen ambas, incluida la recomendación errónea de
`RECEIVER_NOT_EXPORTED`.

---

## 4. R16.4 — Higiene git

```
git merge-base --is-ancestor main origin/main   → exit 0 (main ES ancestro)
git branch -f main origin/main                  → sin forzar
main        = af719974bb18318a225540203381f925895440a8
origin/main = af719974bb18318a225540203381f925895440a8
```

`main` local estaba en `f829734` (atrasada) y se avanzó a `origin/main` **sin `--force`**, porque la
condición de ancestro se cumplía. Sin desvío que reportar.

---

## 5. Verificación

| comprobación | resultado |
| :--- | :--- |
| Criterio (a) UID 2000 bloqueado, dirigido | **CUMPLIDO** |
| Criterio (a) UID 2000 bloqueado, implícito | **CUMPLIDO** |
| Criterio (b) root sigue funcionando | **CUMPLIDO** (control positivo → receptor vivo) |
| Criterio (c) cero emisores internos (`sendBroadcast`) | **CUMPLIDO** — 0 coincidencias |
| Criterio (d) cero cambios de decisión de trading | **CUMPLIDO** — `af71997` no toca `TradingEngine` ni `RiskManager` |
| Suite completa | **314 tests, 0 fallos, 0 errores, 2 omitidos** (línea base intacta) |
| `assembleDebug` | **BUILD SUCCESSFUL** (`af71997`), `app-debug.apk` instalado `Success` |
| `master_traders_skill` intacta | **SÍ** |
| `N` de operaciones supervisadas | **0** |
| Balance | `37.974.735,36` — idéntico a R13/R14/R15 |
| `main` local == `origin/main` | **SÍ** |

**Esta ronda no modifica código de producción** (sólo `AGENTS.md`, que es documentación). No
requiere nueva compilación; la suite citada corresponde al árbol de `af71997`, que sigue siendo el
árbol efectivo de la app instalada.

---

## 6. NO VERIFICADO

1. **El bloqueo de `TAP`/`SWIPE` no se probó por efecto.** Hacerlo habría disparado un tap real
   contra el broker (prohibido). Se infiere del **mecanismo compartido** (mismo permiso, mismo
   patrón de registro) y del control positivo medido en el receptor gemelo. Es inferencia sólida,
   **no medición directa**.
2. **El emisor del rearme de R14 sigue NO ATRIBUIDO.**
3. **No se desplegó un APK hostil** para comprobar el rechazo a una app de terceros real. La
   garantía es de plataforma, verificada por el rechazo a UID 2000 y la aceptación a UID 0.
4. **El incidente de las 13:51:02 no se atribuye con certeza** (humano vs inyección de gestos).
5. **`BinomoAuthActivity` sigue `exported="true"`** — fuera de alcance de R16, se audita en R17.
6. **`/mode` del bridge sigue aceptando `AUTONOMOUS`** — se audita en R17.
7. **No se comprobó si Shizuku o Termux tienen rutas activas** hacia estos broadcasts; se declaran
   como vectores posibles por su capacidad, no por evidencia de uso.

---

## 7. Recomendaciones para R17

1. **Auditar `BinomoAuthActivity`** (`exported="true"`, sin filtro, maneja el `authtoken`). Es la
   siguiente superficie abierta y la única con riesgo de fuga de credenciales. **Es la tarea ya
   asignada a R17.**
2. **Auditar `/mode` del bridge** como puerta de rearme por loopback.
3. **Registrar el UID/PID del emisor** en el `onReceive`. En API 31 no hay
   `getSentFromUid()` (API 34+); la alternativa es capturar el `Permission Denial` del sistema.
   **Asignado a R18.**
4. **Separar `MODE_CHANGE` del buffer de 200** para que `ASSET` no lo desplace. **Asignado a R18.**
5. **Persistir los cambios de modo en disco** para que sobrevivan a la rotación. **Asignado a R18.**
6. **Observación pasiva ≥ 2 h** con el vector cerrado. **Asignado a R19.**
7. **Considerar subir `minSdk` a 33** en el futuro para poder usar el flag estándar; no es
   necesario, el permiso de firma cubre API 24+.

---

## 8. Estado al cierre

| campo | valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | **`false`** |
| `tapsDespachados` | `0` |
| `balance` | `37.974.735,36` (idéntico a R13/R14/R15 → **0 operaciones**) |
| Batería final | **75 %** (cargando) |
| `main` local == `origin/main` | **SÍ** (`af71997`) |
| Veredicto | **PASS-AUTOR** (verificación independiente pendiente: agy/hermes) |
