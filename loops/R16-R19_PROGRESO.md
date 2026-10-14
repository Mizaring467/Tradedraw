# Progreso R16–R19

Fichero de reanudación. Si la sesión se reinicia, léelo y continúa donde quedó.

**Base:** `af71997` (`HEAD == origin/main`) · **Rama:** `r10-night`
**Inicio de misión:** `2026-10-14 13:56` · **Batería inicial:** 74 % (cargando, `status: 2`)

---

## INCIDENTE REGISTRADO AL ABRIR LA MISIÓN (13:57)

Al leer el estado inicial, `mode = AUTONOMOUS` sin que esta sesión lo hubiera puesto.

**Evidencia medida:**

| hora | evento (logcat, buffer previo al reinicio del servicio) |
| :--- | :--- |
| 13:38:58 | `MODE_CHANGE de=NINGUNO a=DISABLED origen=onCreate(servicio recreado)` |
| 13:39:47 | `MODE_CHANGE de=DISABLED a=AUTONOMOUS` origen `overlayCommandReceiver.onReceive:153` — **control positivo de R16-A (root)** |
| 13:39:58 | `MODE_CHANGE de=AUTONOMOUS a=DISABLED` origen `TradeDrawHttpBridge.handleClient:169` — reseteo de R16-A |
| **13:51:02** | **`MODE_CHANGE de=DISABLED a=AUTONOMOUS` origen `OverlayService.showAutonomousSubModeDialog$lambda$36:855`** |

**Contexto reconstruido del logcat en la ventana 13:50:52–13:51:02:**

| hora | línea |
| :--- | :--- |
| 13:50:52.018 | `START u0 {act=android.intent.action.MAIN cat=[LAUNCHER] flg=0x10000000 pkg=com.example.tradedraw cmp=.../.MainActivity} from uid 10080` |
| 13:50:52.181 | foco: `miui.global.packageinstaller/...GlobalPackageInstallerActivity` → `null` |
| 13:50:53.138 | foco → `com.example.tradedraw/.MainActivity` |
| 13:50:55.867 | **`miuibooster onScroll package name: com.example.tradedraw`** ← gesto de scroll real |
| 13:50:57.444 | `MainActivity` pierde el foco |
| 13:50:58.184 | foco → `com.marketly.trading/...TradingActivity` (Binomo) |
| 13:50:59.899 → 13:51:01.161 | foco encadenado entre 3 ventanas overlay de `com.example.tradedraw` (apertura/cierre de diálogos) |
| 13:51:02 | rearme a `AUTONOMOUS` |

`uid 10080` = **`com.miui.global.packageinstaller`**. Es decir: el instalador MIUI, que quedó
abierto con el botón «Abrir» tras el `adb install` de R16-A (13:38), **lanzó `MainActivity` a las
13:50:52 desde su propia UI**.

`showAutonomousSubModeDialog` (`OverlayService.kt:843-874`) sólo se alcanza desde
`showModeDialog` (`:821-841`), y el rearme exige **dos selecciones del usuario** (ítem del menú de
modo → ítem del submenú). No hay ninguna ruta de código que la invoque sola.

**Interpretación, con la disciplina de atribución exigida:** hay **indicio fuerte de interacción
humana** (botón «Abrir» del instalador pulsado + `onScroll` detectado en el paquete + dos
selecciones de diálogo), no un rearme espontáneo. **NO ATRIBUIDO con certeza**: no se puede
descartar desde aquí una inyección de gestos por una app de terceros con accesibilidad. Lo que sí
se puede afirmar:

- **No fue el vector del broadcast**: el `ORIGEN` es la ruta de UI, no `onReceive`.
- **No hubo operación**: `orderState=NINGUNA`, `hasPendingTrade=false`, `tapsDespachados=0`,
  `balance=37.974.735,36` idéntico al cierre de R13/R14/R15.
- **No hubo discrepancia de balance** → no se clasifica como INCIDENTE de trading.

**Acción tomada:** devuelto a `DISABLED` a las 13:57:50 vía `/mode` y verificado. Sin taps, sin
`/trade`. Registrado aquí por ser exactamente el fenómeno bajo investigación.

**Lección operativa (SUPUESTO a partir de ahora):** tras un `adb install`, el instalador MIUI
permanece abierto y puede auto-lanzar la app por sí solo o por un toque. **Cerrar el instalador
explícitamente** (`am force-stop com.miui.global.packageinstaller`) antes de cualquier medición
pasiva, o el teléfono deja de estar desatendido.

---

## R16 · Formalizar el fix af71997

| sub-paso | estado | SHA | hora | batería |
| :--- | :--- | :--- | :--- | :--- |
| R16.0 Reconocimiento + incidente | **HECHO** | — | 13:57 | 74 % (cargando) |
| R16.1 Preregistro retroactivo | **HECHO** | `875dbcb` | 14:03 | 74 % |
| R16.2 Verificación de tooling | **HECHO** | `8950947` | 14:12 | 75 % |
| R16.3 Limitación de root + enmienda R15 | **HECHO** | `8950947` | 14:12 | 75 % |
| R16.4 Higiene git | **HECHO** | — | 14:14 | 75 % |
| R16.5 Informe + push | **HECHO** | `8950947` | 14:16 | 75 % |

## R17 · Auditoría BinomoAuthActivity

| sub-paso | estado | SHA | hora | batería |
| :--- | :--- | :--- | :--- | :--- |
| R17.1 Preregistro | **HECHO** | `9199d13` | 14:20 | 75 % |
| R17.2 Auditoría actividad + superficie exported | **HECHO** | `9199d13` | 14:28 | 76 % |
| R17.3 `/mode` del bridge | **HECHO** | `9199d13` | 14:28 | 76 % |
| R17.4 Parche propuesto (sin aplicar) | **HECHO** | `9199d13` | 14:31 | 76 % |
| R17.5 Informe + push | **HECHO** | `9199d13` | 14:34 | 76 % |

**Hallazgos R17:** H-INTENT FALSADA (cero `getIntent`) · **H-LEAK SOSTENIDA** (3 logs con token en
claro: `:256`, `:322`, `:352`) · H-REDIRECT FALSADA (URL constante `:161`) · H-BRIDGE SOSTENIDA
parcial (riesgo BAJO) · H-MODE SOSTENIDA (`/mode` sin auth, solo loopback). Único `exported="true"`
injustificado: `BinomoAuthActivity`. Parche en `scratch/proposals/`, **no aplicado**.

## R18 · MODE_CHANGE persistente + atribución

| sub-paso | estado | SHA | hora | batería |
| :--- | :--- | :--- | :--- | :--- |
| R18.1 Preregistro | **HECHO** | `85222f1` | 14:40 | 76 % |
| R18.2 Ring dedicado | **HECHO** | `85222f1` | 15:10 | 77 % |
| R18.3 Persistencia JSONL | **HECHO** | `85222f1` | 15:10 | 77 % |
| R18.4 UID del emisor | **HECHO** — NO DISPONIBLE en API 31 (`since=34`), pero `Permission Denial` capturado con uid/pid | `85222f1` | 15:08 | 77 % |
| R18.5 Prueba controlada + reinicio | **HECHO** — rearme sobrevive al reinicio (disco=3, ring=1) | `85222f1` | 15:18 | 75 % |
| R18.6 Informe + push | **HECHO** | `d8ea309` | 15:24 | 75 % |

**Resultado R18:** 325 tests, 0 fallos (+11). `/mode_log` (ring dedicado 100) y `/mode_journal`
(disco) verificados en dispositivo. `getSentFromUid()` es `since=34` → **NO DISPONIBLE en API 31**;
el `Permission Denial` sí capturado, con contraste medido UID 2000 (denegado) vs UID 0 (aceptado).
`balance=0.0` al cierre es **valor no leído** tras reinicio sin abrir la pantalla, **no** discrepancia.

## R19 · Observación pasiva larga

### INCIDENTE 2 — segundo rearme por ruta UI (`14:26:11`)

Al preparar R19 (a las `14:26`), `mode` **volvió a estar en `AUTONOMOUS`**, sin que este agente lo
pusiera. **Por primera vez hay atribución completa, gracias al diario de R18.**

**Cadena medida (`/mode_journal`, disco, 5 entradas):**

| hora | transición | origen |
| :--- | :--- | :--- |
| 14:23:11 | `NINGUNO → DISABLED` | `onCreate(servicio recreado)` |
| 14:23:34 | `DISABLED → AUTONOMOUS` | `overlayCommandReceiver.onReceive:161` — **prueba controlada R18.5 (root, la única permitida)** |
| 14:24:20 | `NINGUNO → DISABLED` | `onCreate(servicio recreado)` — reinicio de R18.5 |
| **14:26:11** | **`DISABLED → AUTONOMOUS`** | **`showAutonomousSubModeDialog$lambda$36:869`** — **ruta de UI** |
| 14:27:01 | `AUTONOMOUS → DISABLED` | `TradeDrawHttpBridge.handleClient:169` — reseteo de este agente |

**Contexto de logcat en los 30 s previos (`14:25:43` – `14:26:11`):**

| hora | línea |
| :--- | :--- |
| 14:25:43.791 | `START u0 {act=MAIN cat=[LAUNCHER] flg=0x10200000 cmp=com.marketly.trading/AppIcon.Default bnds=[528,560][696,801]} from uid 10051` — **toque humano en el icono de Binomo** (con `bnds` = coordenadas del icono) |
| 14:25:50.692 | `START u0 {flg=0x4000000 cmp=com.marketly.trading/.modules.trading.TradingActivity} from uid 10151` |
| 14:26:01.438 / 02.524 / 05.894 / 16.204 | `miuibooster onScroll package name: com.marketly.trading` — **gestos de scroll reales** |
| 14:26:08.880 | foco: `Binomo TradingActivity` → `Window{99e301 u0 com.example.tradedraw}` — **apertura de diálogo del overlay** |
| 14:26:10.386 | foco → `Window{50e0f73 u0 com.example.tradedraw}` — **segundo diálogo (submodo)** |
| 14:26:11.843 | foco → vuelve a `Binomo TradingActivity`; diálogo cerrado |
| **14:26:11** | **rearme registrado** |

**Interpretación:** un **humano** abrió Binomo tocando su icono (`bnds` en el `START`), hizo scroll y
abrió **dos** diálogos del overlay en secuencia — el menú de modo y el submenú —, exactamente los
dos toques que `showAutonomousSubModeDialog` exige. El patrón es **idéntico** al incidente 1 de las
`13:51:02` (allí fue el botón «Abrir» del instalador MIUI).

**Atribución:** **indicio fuerte de interacción humana**, no rearme espontáneo.
**NO ATRIBUIDO con certeza absoluta**: sigue sin poder descartarse inyección de gestos por una app
con accesibilidad, y no hay registro de eventos táctiles crudos. Lo que **sí** se afirma:

- El `ORIGEN` es **ruta de UI**, **no** `onReceive` → **no** es el vector del broadcast.
- **No hubo operación**: `orderState=NINGUNA`, `hasPendingTrade=false`, balance sin cambios.
- **No hay discrepancia de balance** → no se clasifica como INCIDENTE de trading.

**Valor de este hallazgo:** demuestra que la instrumentación de R15-A+R18 **funciona**: el diario
atribuyó con precisión un rearme que en R14 habría quedado sin explicación. Y confirma que **la ruta
de UI sigue siendo una puerta funcional** — correcto por diseño (exige toque humano), pero es el
vector que hay que vigilar en R19.

**Acción:** reseteado a `DISABLED` a las `14:27:01` y verificado antes de arrancar la observación.

**Confusor declarado para R19:** hay actividad humana en el dispositivo durante la ventana. Si
aparece un rearme con origen de UI, **no** se contará como espontáneo y se anotará como confusor.

| sub-paso | estado | SHA | hora | batería |
| :--- | :--- | :--- | :--- | :--- |
| R19.1 Preregistro | **HECHO** | pendiente commit | 15:30 | 75 % |
| R19.2 Observación ≥2 h | **EN CURSO** (inicio 14:31) | — | — | — |
| R19.3 logcat en paralelo | **EN CURSO** (`pwsh-136`) | — | — | — |
| R19.4 Progreso cada 10 min | **EN CURSO** (`pwsh-135`) | — | — | — |
| R19.5 Cierre + artefacto | pendiente | — | — | — |

## Resumen final

| paso | estado |
| :--- | :--- |
| `loops/R16-R19_RESUMEN.md` | pendiente |
| Push final + `HEAD == origin/main` | pendiente |
