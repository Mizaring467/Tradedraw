# BASELINE DEVICE — R30 LIVE DEMO VALIDATION (FASE 0, READ-ONLY)

**Snapshot tomado:** 2026-11-13 **12:13–12:23 UTC** (Termux hora `date -u`; verificado).
**Método:** 100% lectura (ps, dumpsys, settings get, appops get, curl, run-as cat, git). **Cero escrituras, cero taps, cero am start.**
**Agente:** DeepInvestigator prep `076b98ad`. No se tocó `loops/nights_2026-11-13/` (solo lectura).

---

## 1. HALLAZGO PRINCIPAL (cambia el punto de partida del LIVE RUN)

**Ambas apps están MUERTAS en el baseline.** El estado "Binomo corriendo desde ~05:05Z" del turno nocturno ya NO es válido.

```
$ adb shell pidof com.example.tradedraw   → (vacío, exit 1)
$ adb shell pidof com.marketly.trading    → (vacío, exit 1)
```

**Causa raíz (logcat -b events, `am_kill`):**

```
11-13 06:47:54.907  am_kill : [0,22325,com.example.tradedraw,200,SwipeUpClean,128832]
11-13 06:50:50.584  am_kill : [0,21856,com.marketly.trading,400,SwipeUpClean,391020]
```

Device-local 06:47:54 / 06:50:50 (-0500) = **11:47:54Z / 11:50:50Z**. `SwipeUpClean` = gesto de limpieza de recientes **humano** (el owner deslizó ambas apps fuera de recientes ~25 min antes de este baseline). No hubo crash, no hubo ANR, no hubo acción de agente. Consistente con la ventana previa al merge de DeepCoder (el owner despejó el dispositivo).

Consecuencias directas:

- El plan de arranque NO parte de "apps corriendo": parte de **cero procesos** (más limpio, de hecho).
- **Accesibilidad des-enlazada**: `settings get secure accessibility_enabled` = **`0`** (antes 1). TradeDraw aparece en `Enabled services` PERO también en **`Crashed services`** en `dumpsys accessibility` — el patrón HyperOS documentado en `td_supervisor.sh` ("des-enlaza la accesibilidad cuando el proceso muere, aunque el setting siga listando el servicio"). `accessibility_enabled=0` hace que ni siquiera esté habilitada nominalmente: **rehabilitación obligatoria en el arranque** (settings put ya documentado en el plan).
- Overlay appops `SYSTEM_ALERT_WINDOW` de TradeDraw: **allow** (persistente; última emisión +7h40m antes ≈ 04:35Z, coincide con el stop del motor). No hay que re-pedir permiso de overlay.
- Motor: última línea de `files/mode_changes.jsonl` = `AUTONOMOUS → DISABLED` (stop 04:35:31Z vía `engine:stop()`). Estado persistido: DISABLED. ✓ (sin ningún re-arme desde entonces).

## 2. Procesos (ps con STIME/ETIME)

| Paquete | PID | STIME | ETIME | Estado |
|---|---|---|---|---|
| com.example.tradedraw | — | — | — | **MUERTO** (am_proc_died 11:47:54Z) |
| com.marketly.trading | — | — | — | **MUERTO** (am_proc_died 11:50:50Z) |
| af.shizuku.plus.api | 4179 | — | vivo | cliente Shizuku (no usado por TD) |
| shizuku_plus_server (uid shell) | 19540 | — | vivo | servidor Shizuku activo |

(Sin root; `ps -o STIME,ETIME` sobre pidof vacío no aplica. Forense vía `logcat -b events am_kill/am_proc_died`.)

## 3. Bridge HTTP — estado exacto

| Vía | Comando | Resultado |
|---|---|---|
| IPv6 loopback (vía del turno nocturno) | `curl --noproxy '*' -s -m 5 "http://[::1]:8080/status"` | **vacío — exit code 7** (connection refused) |
| Forward existente tcp:18080 | `curl --noproxy '*' "http://127.0.0.1:18080/status"` | **vacío — exit 52** (conecta con el forward, nadie escucha detrás) |

- Ningún proceso escucha en 8080 dentro del dispositivo (`ss -tln` sin permisos netlink vía adb shell; un único listener ajeno: `127.0.0.1:18080` dentro del DEVICE, que es el destino del forward, no el bridge).
- **Forwards ADB activos:** `tcp:18080 → tcp:8080` (host→device) y `tcp:9222 → localabstract:webview_devtools_remote_13998` (devtools HUÉRFANO: el pid 13998 era Binomo muerto; regenerar al relanzar).
- El supervisor espera `[::1]:8080`; con el bridge atado a `InetAddress.getLoopbackAddress()` esa vía funcionó en el turno nocturno **con el supervisor vivo manteniendo el forward `tcp:8080`**. Hoy NO existe forward `tcp:8080` y el supervisor está parado → para que `[::1]:8080` funcione de nuevo hay que restaurar el forward (`adb forward tcp:8080 tcp:8080`) o usar `tcp:18080` (ya presente).
- **Contrato del bridge verificado en código** (`app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`, 1147 líneas):
  - Bind estrictamente loopback, puerto por defecto **8080** (líneas 27, 42-44).
  - `GET /ping` → `{"status":"ok","app":"TradeDraw","port":8080}` (l.130).
  - `GET /status` → JSON con `versionCode`, `versionName`, `gitSha`, `buildId`, **`isDemoAccount`** (l.763), `mode`, `submode`, `accessibilityActive`, `balance`, `health`, `feed.lastTickAgeMs`, `feed.connected`, `feed.isFresh`, `risk.orderState` (l.756-873). **No existe campo top-level `"state"`** (el supervisor grepea `"state":"[A-Z]+"` y loguea STATE vacío — cosmético).
  - `GET/POST /mode?name=<MODE>` → `setTradingMode(m, "bridge:/mode")` (l.205-215). Acepta cualquier `AutoTradeMode`.
  - **Línea 87: CUALQUIER request al bridge llama `tradingEngine.noteBridgeHeartbeat()`** → alimenta el watchdog R26. `AUTONOMOUS_HEARTBEAT_TIMEOUT_MS = 10*60*1000` (TradingEngine.kt:857): **AUTONOMOUS armado por bridge caduca a los 10 min sin polling; armado por HUD no caduca** (confirmado: el armado HUD del 04:32Z NO caducó).
  - `POST /trade` con guard de modo: DISABLED/SHADOW → 403 + VETO (l.150-163, 1106-1120).
  - Otros endpoints: `/events /mode_log /mode_journal /ticks /shadow_journal /adaptive_state /stats /win /loss /reset_stats /resume /sync_stats /strategy /submode /asset /calibrate /hud /overlay /click /swipe /simulation /dispatch_recovery /diag_multiric /diag_inoperable /frame /nodes`.
  - **Guard DEMO en motor** (TradingEngine.kt:1707-1730): en `executeHeadlessTrade`, si `mode==AUTONOMOUS` y `!puedeOperarAutonomoEnDemo(brokerRuntime.getBrokerState().isDemoAccount)` → VETO `SEGURIDAD_NO_DEMO cuenta_real_bloqueada` + `setTradingMode(DISABLED, "seguridad:no-demo")`. `puedeOperarAutonomoEnDemo(esDemo) = esDemo` (l.923). **Fail-closed verificado en código.**

## 4. Accesibilidad

```
$ settings get secure enabled_accessibility_services
com.omkar.voiceflow/com.omkar.voiceflow.VoiceFlowAccessibilityService:com.example.tradedraw/com.example.tradedraw.AutoTradeAccessibilityService
$ settings get secure accessibility_enabled
0
```

`dumpsys accessibility`: `Enabled services: {...TradeDraw...}` / `Bound services: {}` / **`Crashed services: {...TradeDraw...}`**. Al 12:14Z: **registrada pero NO habilitada (0) y NO bound**.

## 5. Overlay (appops)

```
$ appops get com.example.tradedraw SYSTEM_ALERT_WINDOW
SYSTEM_ALERT_WINDOW: allow; time=+7h38m35s ago; duration=+21ms
$ appops get com.marketly.trading SYSTEM_ALERT_WINDOW
SYSTEM_ALERT_WINDOW: default; rejectTime+7h7m8s ago
```

TradeDraw **allow** persistente (el permiso no se pierde con el kill). Binomo en default/reject (irrelevante: no necesita overlay).

## 6. Versiones e identidad de APKs instaladas

| Paquete | versionName | versionCode | lastUpdateTime | sha256 base.apk |
|---|---|---|---|---|
| com.example.tradedraw | `1.11-r27h-activeinstance-atomic` | **21** | 2026-11-12 19:48:27 (device) | **`3164d971921c87d3e15e4bd955329eb83a388531d60a695d229ca1762732a7cf`** |
| com.marketly.trading | `8.5.0` | 8050000 | 2026-10-16 12:49:32 | (no calculada; intacta desde 10-16) |

- TradeDraw instalada = **build del worktree frontend @451787f** (confirma checkpoint nocturno). Ruta on-device: `/data/app/~~PC8l5XcFzKaOY98WV071Kg==/com.example.tradedraw-MHIKD-CEtVtNnmn1dYlTQw==/base.apk`.
- **Será REEMPLAZADA en FASE 2** por el APK candidato de DeepCoder (merge 451787f→r10-night).

## 7. Foreground / pantalla / hardware

```
mCurrentFocus=Window{5f26fa5 u0 com.termux/com.termux.app.TermuxActivity}
mFocusedApp=ActivityRecord{63233423 u0 com.termux/.app.TermuxActivity t1653}
mWakefulness=Awake ; mDreamingLockscreen=false   (pantalla encendida, sin keyguard)
wm size 1220x2712 ; wm density 480 (override 381)
```

Batería: **88%**, status 3 (descargando), **AC/USB/wireless: false**, temp 34.2°C, health 2. **RIESGO: sin cargador; HyperOS puede matar por ahorro.** Espacio /data: **145G libres** (69% usado). Modelo `2311DRK48G`, Android **16**, HyperOS **V816**, `adb_enabled=1`.

## 8. Reloj (drift)

Termux 12:16:25.6–12:16:25.9Z vs device epoch `1794572185` (12:16:25Z): **drift < 1 s**. Logcat device-local (-0500) + 5 h = UTC. No se requiere corrección.

## 9. Supervisor / autostart

- `pgrep -af td_supervisor` → solo coincide su propia línea de invocación: **PARADO** (desde 11-07 según checkpoint; log último ciclo 2026-11-07T18:27:41Z).
- Autostart Termux:Boot = `~/.termux/boot/td_supervisor.sh.disabled` (`.disabled` en el nombre ⇒ no arranca al boot).
- Comportamiento verificado leyendo `scripts/td_supervisor.sh` (185 líneas): sondea `[::1]:8080/status` cada POLL_S (30 s), y **con `AUTO_REARM=1` (default) RE-ARMA AUTONOMOUS por bridge tras cualquier recuperación** (l.152-158). Su receta de re-bind de accesibilidad (settings out→in + `accessibility_enabled=1`) y su fallback de tap del botón overlay en (610,1738) están verificados en el log del 11-06/11-07.
- **PELIGRO para el LIVE RUN: si alguien arranca el supervisor con AUTO_REARM=1, re-arma AUTONOMOUS solo, saltándose el guard de verificación DEMO del plan.** Arrancarlo solo con `AUTO_REARM=0` (o no arrancarlo) durante FASE 3.
- Quirk: grepea `"state"` en /status, campo que ya no existe ⇒ loguea `OK <MODE> ` sin estado (cosmético).

## 10. Estado del repo y trabajo concurrente (a las 12:13Z; ACTUALIZADO 12:22Z)

| Worktree | HEAD al baseline | Rama | Estado árbol |
|---|---|---|---|
| `~/Tradedraw` (principal) | `861a717` [r10-night] | r10-night | limpio salvo `loops/nights_2026-11-13/`, `loops/r30_live/` (untracked) |
| `~/Tradedraw-binomo-ws` | `21b0a55` [assistant/binomo-ws-fix] | — | **WIP sucio** (2 M + 2 ??, incl. `BinomoQuoteProtocol.kt`) |
| `~/Tradedraw-feed-health` | `128f97f` [assistant/ws-feed-health] | — | limpio |
| `~/Tradedraw-frontend` | `451787f` [assistant/frontend-brokers-agent] | — | limpio |
| `~/Tradedraw-instrumentation` | `a2c4d03` [assistant/instrumentation-v3] | — | **WIP sucio** (1 M + 2 ??) |
| `~/Tradedraw-wsfix` | `293c932` [assistant/ws-feed-fix] | — | limpio |

- **No sobrescribí nada.** Los WIP sucios de `binomo-ws` e `instrumentation` son trabajo ajeno preservado tal cual.
- **ACTUALIZACIÓN DENTRO DE LA VENTANA DEL BASELINE (12:22:55Z):** el principal pasó de `861a717` a **`8bd172f`** `merge: integrate assistant/frontend-brokers-agent (451787f) into r10-night` — DeepCoder completó la fusión y estaba en fase de build (sin outputs nuevos aún en `app/build/outputs/`; el APK local más reciente sigue siendo del 11-11 20:33). **El HEAD del merge `8bd172f` es el ancestro del APK candidato.**
- 0 commits en la última hora antes del merge; 0 modificaciones en `loops/nights_2026-11-13/` tras las 11:00Z ⇒ sin interferencias con este baseline.
- Carga del sistema: load avg 14.25 (compilación u otros agentes en vuelo — esperable).

## 11. Componentes para el arranque (verificados para FASE 3)

> **⚠️ ACTUALIZACIÓN 12:28Z:** durante este baseline, DeepCoder fusionó y construyó el candidato (merge `8bd172f`, **versionCode 22, versionName `1.12-r30-live-fusion`**, APK nuevo 12:25Z). La MainActivity del candidato es una **home NUEVA** (ScrollView+cards, `home_strings.xml`): botón overlay = **"Iniciar con WebSocket"** (`btn_headless`; la etiqueta "🚀 INICIAR TRADE PRO" es del APK VIEJO vc21); botón "Abrir herramientas flotantes" (`R.id.boton`) OCULTO en código (MainActivity.kt:50); nuevo botón "Vincular Binomo" (flujo de credenciales). Las líneas siguientes describen el APK VIEJO instalado (vc21) salvo esta nota.

- Launcher TradeDraw: `com.example.tradedraw/.MainActivity` (`cmd package resolve-activity`).
- Launcher Binomo: `com.marketly.trading/AppIcon.Default`.
- Botón de overlay (APK VIEJO vc21): `btn_headless` = **"🚀 INICIAR TRADE PRO (WebSocket 0ms)"** (activity_main.xml:59). `MainActivity` hace `startForegroundService(... OverlayService, EXTRA_HEADLESS_MODE=true)` + `finish()`. **(APK NUEVO: etiqueta "Iniciar con WebSocket", sin finish(), requiere `BrokerCatalog.canStart` + ver PASO 4 del STARTUP_PLAN).**
- `OverlayService` **exported=false** (AndroidManifest.xml:54-57, foregroundServiceType specialUse) ⇒ **imposible arrancarlo por shell; solo vía UI de la app** (tap del botón) o vía servicio de accesibilidad propio.
- Shizuku presente y vivo (server uid shell + cliente API) — disponible como transporte alternativo, no requerido por el plan.

## 12. Discrepancias respecto al encargo/checkpoint

| Dato del encargo | Estado a las 12:13Z | Evidencia |
|---|---|---|
| "Binomo corriendo (pid desde ~05:05Z)" | **CADUCADO** — muerto 11:50:50Z | `am_kill ... SwipeUpClean` |
| "Accesibilidad activa a las 06:31Z" | **CADUCADO** — `accessibility_enabled=0`, crashed | `settings get`, `dumpsys accessibility` |
| "Bridge CAÍDO" | Confirmado (exit 7 por `[::1]:8080`; sin forward tcp:8080) | curl |
| "Supervisor parado" | Confirmado | pgrep + log |
| "r10-night @861a717" | Verdadero al baseline; **ya sucesor: merge `8bd172f`** (12:22:55Z) | git log -1 |
| "APK instalado = 451787f (3164d971…, vc21)" | Confirmado sin cambios | sha256 on-device |
| "run-as solo lectura funciona" | Confirmado (lectura de files/ OK) | run-as cat |
| "`uiautomator dump` falla sobre Binomo" | No re-testeado (no se abrió Binomo) — se respeta como hipótesis vigente | — |

## 13. Logs disponibles

- `logcat -b events`: buffer vivo con `am_kill/am_proc_died` desde 11-12 20:59Z (sin rotation todavía; auditd lo satura).
- `logcat -d` (main): cubre 11-13 06:50Z+ (incluye los bindService de Binomo 06:50Z y el SwipeUpClean).
- Nota nocturna: `init.svc.logd=stopped` en este dispositivo tras reinicios de logd — el buffer actual es suficiente para el forense de hoy.
