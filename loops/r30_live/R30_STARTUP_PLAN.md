# R30 STARTUP PLAN — ARRANQUE SEGURO DEL ENTORNO REAL (FASE 3, para ejecutar en la siguiente ronda)

**Autor:** DeepInvestigator prep `076b98ad` — 2026-11-13T12:25Z.
**Entrada:** [BASELINE_DEVICE_R30.md](./BASELINE_DEVICE_R30.md). **Salida esperada:** entorno vivo con bridge UP, DEMO positivo verificado, motor aún en DISABLED, listo para FASE 5 (armado).
**Premisa crítica del baseline:** ambas apps MUERTAS (SwipeUpClean 11:47/11:50Z), `accessibility_enabled=0`, supervisor parado, bridge sin listener, sin root. Arrancamos desde cero procesos.
**Regla de oro:** cada paso tiene criterio FAIL-CLOSED; si falla, detenerse y registrar. Jamás armar AUTONOMOUS sin DEMO positivo del instante (≤120 s).

**Convención de horas:** el dispositivo está en UTC-5 (`date` device = `-0500`); drift <1 s con Termux. Todos los timestamps de logcat son device-local: **UTC = local + 5 h**.

---

## ESTADO OBJETIVO (post-condición del plan)

```
pidof com.example.tradedraw ≠ ∅   y   OverlayService foreground (START_FOREGROUND reciente)
accessibility: TradeDraw en "Bound services" y accessibility_enabled=1
bridge: /status HTTP 200 con gitSha = sha del APK instalado
isDemoAccount:true (leído del árbol de accesibilidad, tick ≤120 s)
Binomo foreground en pantalla «Cuenta Demo» (screencap como evidencia)
mode: DISABLED (aún sin armar)
```

---

## PASO 0 — Pre-chequeo (host Termux, 2 min)

```
adb devices                                   # debe listar emulator-5554 (device)
adb shell getprop ro.product.model            # 2311DRK48G
adb shell pidof com.example.tradedraw com.marketly.trading   # esperado: vacío (baseline)
adb shell dumpsys battery | grep -E 'level|AC powered'
adb forward --list                            # anotar estado: tcp:18080 y tcp:9222 (huérfano)
```

**FAIL-CLOSED:** si `adb devices` no muestra el dispositivo → abortar (no hay transporte).
**Decisión de transporte del bridge (documentar cuál se usa):**
- Opción A (recomendada, host-side): `adb forward tcp:18080 tcp:8080` ya existe → consumir vía `http://127.0.0.1:18080/...`.
- Opción B (receta supervisor/nocturna): `adb forward tcp:8080 tcp:8080` → consumir vía `http://[::1]:8080/...` (IPv6 loopback dentro del device).
- Elegir UNA y anotarla; ambos destinos son el mismo listener loopback:8080 de la app.

## PASO 1 — Instalación del APK candidato

> **Candidato (LIVE_BUILD.md, 12:25–12:27Z):** merge `8bd172f` + registry v4 (`r30-frozen-8bd172f`, parameter_hash `07e4911c…`, único cambio v3→v4: `BinomoWebSocketClient.kt` transporte WS) + suites 136 python OK + gradle `BUILD SUCCESSFUL` (12:25Z, exit 0). versionCode **21→22**, versionName **`1.12-r30-live-fusion`**. APK en `app/build/outputs/apk/debug/app-debug.apk` (12:25Z; aún sin commitear/push/CI al cierre de esta prep — **no instalar hasta que LIVE_BUILD.md registre el sha256 final y el commit**).

```bash
adb push app-debug.apk /data/local/tmp/td_candidate.apk
adb install -r -d /data/local/tmp/td_candidate.apk
```

**Verificación de identidad (obligatoria, on-device):**
```bash
adb shell dumpsys package com.example.tradedraw | grep -E 'versionName|versionCode|lastUpdateTime'
adb shell pm path com.example.tradedraw          # ruta nueva de base.apk
adb shell sha256sum $(adb shell pm path com.example.tradedraw | sed 's/package://' | tr -d '\r')
```
- Comparar el sha256 con el del candidato (registrar en MISSION_STATE_R30 → LIVE_BUILD_ID junto con commit/branch/versionName/versionCode/timestamp).
- versionName esperado: **`1.12-r30-live-fusion`**; versionCode esperado: **22**.
- Verificar además `gitSha`/`buildId`/`buildTimestamp` que expondrá `/status` en el PASO 5 (cross-check APK↔runtime; gitSha esperado = commit final de la ronda F1, no necesariamente 8bd172f).
**FAIL-CLOSED:** sha256 no coincide con el candidato → NO continuar; reinstalar o abortar. `install` con error → abortar y registrar salida exacta. **Si el sha256/commit final aún no está en LIVE_BUILD.md → esperar a DeepCoder (jamás instalar un APK sin identidad registrada).**

## PASO 2 — Rehabilitar accesibilidad (obligatoria: baseline la dejó en 0)

**Riesgo HyperOS documentado:** `pm install -r` NO borra la entrada del servicio en `enabled_accessibility_services`, PERO en este dispositivo HyperOS dejó `accessibility_enabled=0` y el servicio en `Crashed services` tras la muerte del proceso (baseline §4). No se puede asumir que una reinstall conserve el bind. **Comprobación post-install obligatoria:**

```bash
adb shell settings get secure accessibility_enabled            # ¿sigue en 0 tras install?
adb shell settings get secure enabled_accessibility_services   # ¿sigue listado?
```

- Si `accessibility_enabled=0` o el servicio no está listado → receta verificada del supervisor (settings out→in, misma secuencia que usó td_supervisor el 11-06/11-07):

```bash
adb shell settings put secure enabled_accessibility_services "com.omkar.voiceflow/.VoiceFlowAccessibilityService:af.shizuku.plus.api/af.shizuku.manager.automation.AICorePlusService"
sleep 3
adb shell settings put secure enabled_accessibility_services "com.omkar.voiceflow/.VoiceFlowAccessibilityService:af.shizuku.plus.api/af.shizuku.manager.automation.AICorePlusService:com.example.tradedraw/com.example.tradedraw.AutoTradeAccessibilityService"
adb shell settings put secure accessibility_enabled 1
```

- Verificar bind REAL (no basta el setting): `adb shell dumpsys accessibility | grep -E 'Bound services|Crashed services'` → TradeDraw en **Bound**, ausente de Crashed.
**FAIL-CLOSED:** si tras 2 intentos de re-bind no aparece en Bound → NO arrancar overlay (el motor queda ciego y el guard de UI no funciona). Abortar; alternativa: que el owner active el servicio manualmente en Ajustes → Accesibilidad → TradeDraw.

## PASO 3 — Lanzar TradeDraw (MainActivity)

```bash
adb shell am start -n com.example.tradedraw/.MainActivity     # launcher resuelto en baseline §11
sleep 3
adb shell dumpsys window | grep mCurrentFocus                  # esperado: com.example.tradedraw/...MainActivity
adb -s emulator-5554 exec-out screencap -p > r30_live/step3_main.png
```

**FAIL-CLOSED:** focus ≠ MainActivity → registrar screencap, revisar si HyperOS mostró un diálogo de arranque; reintentar 1 vez; si persiste, abortar.

## PASO 4 — Arrancar el overlay DESDE LA UI (única vía: exported=false)

> **⚠️ ACTUALIZADO 12:28Z tras el merge `8bd172f`:** la MainActivity del candidato es una home NUEVA (ScrollView + cards, strings en `home_strings.xml`). El botón de overlay sigue siendo **`btn_headless`** pero su etiqueta cambió: **"Iniciar con WebSocket"** (`home_strings.xml:19`). El botón "Abrir herramientas flotantes" (`R.id.boton`) existe en el layout pero MainActivity lo oculta (`visibility = GONE`, MainActivity.kt:50). **La etiqueta antigua "🚀 INICIAR TRADE PRO (WebSocket 0ms)" ya NO existe en el candidato** (solo en el APK vc21 actualmente instalado, que será reemplazado). Hallazgo coincidente documentado por DeepCoder en LIVE_BUILD.md §integración (P0 para el supervisor, ver abajo).

**Por qué:** `OverlayService` es `exported=false` (AndroidManifest.xml:54-57) → un `am startservice` desde shell falla. La vía es tocar el botón real de la app.

1. Overlay ya concedido: `appops get com.example.tradedraw SYSTEM_ALERT_WINDOW` = allow (baseline §5; persiste al kill).
2. Botón: **"Iniciar con WebSocket"** (`btn_headless`). Requisito: `broker_selector` en "Binomo" (default) — el botón solo está enabled si `BrokerCatalog.canStart("binomo")==true` (siempre true; Binomo instalado).
3. Localizar el botón: la UI propia de TradeDraw SÍ es legible por uiautomator (verificado por td_supervisor el 11-06 sobre el layout ANTIGUO; re-verificar sobre el nuevo):
```bash
adb shell uiautomator dump /sdcard/td_r30.xml && adb shell cat /sdcard/td_r30.xml | grep -o 'Iniciar con WebSocket[^<]*'
# extraer bounds="[x1,y1][x2,y2]" → centro = ((x1+x2)/2, (y1+y2)/2)
```
   **⚠️ El fallback (610,1738) del supervisor NO es válido con el layout nuevo** (posiciones distintas: el layout es un ScrollView con cards; el botón está tras 3 tarjetas). PROHIBIDO reutilizar coordenadas antiguas: derivar SIEMPRE del dump fresco; si el dump falla sobre la home nueva → screencap + pedir al coordinador posición confirmada visualmente antes de tap.
4. `adb shell input tap CX CY`; esperar 5-7 s. Toast esperado: "Herramientas iniciadas sin grabación de pantalla". (En el candidato, MainActivity NO se cierra tras iniciar el servicio — ya no hay `finish()`.)
5. Verificación multi-superficie del overlay vivo:
```bash
adb shell pidof com.example.tradedraw                                  # vivo
adb shell appops get com.example.tradedraw START_FOREGROUND            # time reciente
adb shell dumpsys activity services com.example.tradedraw | grep -i OverlayService
adb shell screencap -p /sdcard/td_r30_hud.png && adb pull /sdcard/td_r30_hud.png r30_live/step4_hud.png   # HUD visible
```
**FAIL-CLOSED:** si uiautomator no encuentra el botón Y el screencap no muestra la MainActivity → abortar. Si el tap no levanta START_FOREGROUND nuevo → abortar. Si uiautomator falla incluso sobre la UI propia (comportamiento nuevo, contradiría el 11-06) → usar el fallback (610,1738) UNA vez; si falla, abortar (sin taps a ciego repetidos).
**Riesgo de coordenadas:** el fallback (610,1738) fue verificado el 2026-11-06 en esta misma pantalla 1220x2712; confirmar con screencap antes de usarlo. Un tap fallido aquí no abre broker ni arma nada (peligro bajo), pero un tap a ciego repetido puede pulsar controles indeseados de la propia app.

## PASO 4b — Verificar validez/frescura de la sesión de Binomo

> **⚠️ Corregido ronda 3 (13:00Z):** el token de sesión **EXISTE** (verificado en esta prep, en `TradeDraw_WSConfig.xml` del APK vc21 hoy instalado; sobrevive a `pm install -r` y a kills de proceso). Este paso ya NO busca "si hay token" sino **verificar validez/frescura** de la sesión que representa. La validez real (que el broker acepte el token y emita ticks) solo queda demostrada con apps vivas en FASE 3 — desde shell read-only no hay forma de validarlo contra el broker.

La home del candidato añade **"Vincular Binomo"** (`btn_link_binomo`): abre `BinomoAuthActivity` y guarda token en `SharedPreferencesCredentialStore` (prefs `TradeDraw_WSConfig`: claves `ws_auth_token`, `ws_device_id`, `ws_endpoint_url`, `ws_cookie_header`). **El puente no debe tocar este botón** (escribe credenciales y lanza UI de auth de broker = zona prohibida read-only). Comprobaciones sin tocar nada:

```bash
# Lectura de credenciales de sesión (solo lectura de prefs de la app, via run-as).
# Prefs correctas: TradeDraw_WSConfig (SharedPreferencesCredentialStore), NO TradeDraw_Home.
adb shell run-as com.example.tradedraw cat shared_prefs/TradeDraw_WSConfig.xml 2>/dev/null
# Verificar: ws_auth_token no vacío (token presente). Opcional: longitud/fecha observable
# como pista de frescura, pero la VALIDEZ solo se demuestra con apps vivas en FASE 3.
```

**Corrección E-4 (nota, ronda 3):** el armado por **HUD** no alimenta el heartbeat de 10 min (`ui:showAutonomousSubModeDialog` no pasa por el bridge ⇒ sin heartbeat). El guard de despacho sigue vivo: `isDemoConfirmedFresh(120 s)` (OrderGateway.kt:10) bloquea todo trade sin DEMO confirmado ≤120 s, y el VETO `SEGURIDAD_NO_DEMO` + auto-DISABLED (TradingEngine.kt:1707-1730) permanecen como última red. Se **mantiene la recomendación de armado por bridge** (PASO 7): su caducidad de 10 min es una red de seguridad adicional.

**FAIL-CLOSED:** `ws_auth_token` vacío/ausente en `TradeDraw_WSConfig.xml` → NO armar; escalar al coordinador (el owner debe hacer "Vincular Binomo" manualmente antes de FASE 5). Token presente pero **inválido/caducado ante el broker** → se manifestará en FASE 3 como feed sin ticks (`lastTickAgeMs` alto) o /status sin balance: escalar (posible re-login humano necesario).

## PASO 5 — Verificar bridge

```bash
# Con transporte A: (el forward 18080 ya existe)
curl --noproxy '*' -s -m 5 http://127.0.0.1:18080/ping
# Cross-check del runtime contra el APK instalado:
curl --noproxy '*' -s -m 5 http://127.0.0.1:18080/status | python3 -m json.tool > r30_live/step5_status.json
```
- `/ping` esperado: `{"status":"ok","app":"TradeDraw","port":8080}`.
- `/status`: verificar `versionCode==22`, `versionName=="1.12-r30-live-fusion"`, `gitSha`/`buildId` = identidad del PASO 1; `accessibilityActive:true`; `mode:"DISABLED"`; `isDemoAccount:false` (aún, sin Binomo); `balance:0`.
**FAIL-CLOSED:** `/ping` vacío/timeout tras 3 reintentos espaciados 5 s → abortar (revisar que el proceso vivo es el servicio y no solo la Activity). Si `gitSha` del /status ≠ sha del APK → IDENTIDAD ROTA: desarmar (si estuviera armado: `curl .../mode?name=DISABLED`) y abortar.

## PASO 6 — Arrancar Binomo y verificar «Cuenta Demo» FÍSICAMENTE

```bash
adb shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1   # vía del supervisor, verificada 11-06/11-07
sleep 8
adb shell dumpsys window | grep mCurrentFocus     # esperado: com.marketly.trading/... (AppIcon.Default task)
adb -s emulator-5554 exec-out screencap -p > r30_live/step6_binomo.png
```

**uiautomator está PROHIBIDO sobre Binomo** (anti-automatización: "could not get idle state" — checkpoint del turno nocturno; hipótesis vigente). Verificación de la cuenta DEMO:

1. **Screencap** (evidencia visual primaria) → verificar banner "Cuenta Demo" / balance de demo. Guardar como `step6_binomo_demo.png`.
2. **Lectura del árbol de accesibilidad:** una vez el servicio de TradeDraw esté bound y Binomo en foreground, los eventos TYPE_WINDOW_STATE_CHANGED del servicio leen balance/cuenta (mecanismo del propio bot: leyendo `AutoTradeAccessibilityService.isDemoAccount`). Esperar 10-15 s y consultar:
```bash
curl --noproxy '*' -s http://127.0.0.1:18080/status | grep -E 'isDemoAccount|balance'
```
   Esperado: `isDemoAccount:true` (leído por el servicio desde la UI real de Binomo) y `balance>0` (si Binomo la expone en pantalla).
3. **Guard antes de armar (FASE 5):** exigir `isDemoAccount:true` con observación ≤120 s (la ventana que usa el motor; baseline §3). Si el balance leído es ~1e6 o el screencap no muestra "Demo" de forma inequívoca → tratar como REAL: FAIL-CLOSED.
4. **Fuente de verdad del guard:** `isDemoAccount` lo actualiza el servicio de accesibilidad observando la UI de Binomo (no hay flag de settings ni de bridge que lo fije). Si el servicio no está bound, `isDemoAccount` permanece `false` → el guard del motor bloqueará AUTONOMOUS (fail-closed) pero la sesión no puede operar: NO es condición de arranque válida.
**FAIL-CLOSED:** focus ≠ Binomo → reintentar monkey 1 vez; si falla, abortar. Screencap sin evidencia "Demo" inequívoca → NO armar; escalado al coordinador.
**Si uiautomator sobre Binomo volviera a ser necesario:** está documentado que falla; no insistir. Alternativa de lectura: dumpsys accessibility events / el propio /status del bridge (camino 2).

## PASO 7 — (SOLO FASE 5, OTRO AGENTE) Armado AUTONOMOUS

**Requisito previo (guard DEMO positivo):** `isDemoAccount:true` con observación ≤120 s + balance de demo observado + Binomo foreground + feed vivo (`feed.connected:true`, `feed.isFresh:true`, `lastTickAgeMs` bajo). Ver TODOS antes de armar.

**Dos vías (documentadas con sus riesgos):**

| Vía | Llamada | Caducidad | Riesgo |
|---|---|---|---|
| **Bridge (recomendada para control de sesión)** | `curl --noproxy '*' "http://127.0.0.1:18080/mode?name=AUTONOMOUS"` | **Caduca a 10 min sin polling** (watchdog R26: cualquier request al bridge alimenta el heartbeat; TradingEngine.kt:857 `AUTONOMOUS_HEARTBEAT_TIMEOUT_MS=10min`) | Si el watchdog cae sin desarmar, el motor queda armado sin supervisión; si el polling se detiene, se desarma solo (seguro, pero corta la sesión) |
| **HUD (menú del overlay)** | tap del humano/agente en el HUD → diálogo de submodo (yolo) | **NO caduca** (armado por ui:showAutonomousSubModeDialog no alimenta watchdog) | Sin desarme automático: cualquier caída del watchdog deja el motor armado indefinidamente |

- **Recomendación:** armado por BRIDGE para que la caducidad de 10 min actúe como red de seguridad adicional, y que el watchdog de FASE 4 polling cada ≤60 s lo mantenga vivo deliberadamente. El armado por HUD solo si el bridge está caído y con confirmación explícita del coordinador.
- **Tras armar:** verificar `/status` → `"mode":"AUTONOMOUS"`, `submode` esperado, y `lastModeChangeSource` conteniendo `bridge:/mode` (o el origen HUD). Anotar timestamp exacto en MISSION_STATE_R30 → R30_LIVE_SESSION_001.
- **Guard post-armado:** dentro de los primeros 30 s, comprobar que si el broker no es DEMO el motor se auto-desarma (VETO `SEGURIDAD_NO_DEMO` visible en `/events?max=20`).
- **Desarme de emergencia (aprenderlo de memoria):**
```bash
curl --noproxy '*' "http://127.0.0.1:18080/mode?name=DISABLED"
# verificar inmediatamente: /status → "mode":"DISABLED"
```

## PASO 8 — Cierre de sesión (final del LIVE RUN)

```bash
curl --noproxy '*' "http://127.0.0.1:18080/mode?name=DISABLED"    # verificar 200 + mode DISABLED
```
Dejar el entorno como el baseline: motor DISABLED; apps pueden quedar vivas o no (documentar estado final). NO iniciar supervisor con AUTO_REARM=1 (baseline §9: re-arma solo).

---

## TABLA RESUMEN FAIL-CLOSED

| Paso | Condición de aborto | Acción |
|---|---|---|
| 0 | adb sin dispositivo | abortar; sin transporte no hay misión |
| 1 | sha256 del APK ≠ candidato; install falla | abortar; no operar con binario no verificado |
| 2 | a11y no en Bound tras 2 re-binds | abortar; pedir activación manual del owner |
| 3 | focus ≠ MainActivity tras 1 reintento | abortar; screencap de diagnóstico |
| 4 | botón no localizable; tap sin START_FOREGROUND | abortar; sin overlay no hay bridge ni motor |
| 5 | /ping caído; gitSha/status ≠ APK | abortar; identidad rota |
| 6 | focus ≠ Binomo; sin evidencia "Demo" inequívoca | NO armar; escalar; tratar como REAL |
| 7 | guard DEMO ≤120 s no satisfecho; feed no fresco | NO armar; esperar o abortar |
| 8 | desarme sin confirmación 200/DISABLED | reintentar 1 vez; si falla, kill suave del proceso (run-as) y escalar |

---

## Correcciones ronda 3 — 2026-11-13T13:00Z

**Fuente:** revisión del mismo hilo (ronda 3, prep — correcciones a los entregables propios de la ronda 1). Sin cambios de comportamiento fuera de lo listado:

1. **PASO 4b reescrito:** la lectura de credenciales apunta ahora a `run-as cat shared_prefs/TradeDraw_WSConfig.xml` (prefs de `SharedPreferencesCredentialStore`; claves `ws_auth_token`, `ws_device_id`, `ws_endpoint_url`, `ws_cookie_header`) en lugar de `TradeDraw_Home.xml` (era el prefs equivocado). Token verificado EXISTENTE: el paso reformulado como «verificar validez/frescura de la sesión»; la validez solo se demuestra con apps vivas en FASE 3. Añadida la corrección E-4 como nota (armado HUD no alimenta heartbeat de 10 min pero `isDemoConfirmedFresh` 120 s sigue vivo; se mantiene armado por bridge).
2. **Transporte documentado:** usar `127.0.0.1:18080` (forward `tcp:18080` ya existente). `[::1]:8080` no funciona desde Termux sin crear el forward `tcp:8080` (verificado en baseline: exit 7). Opción B solo si el supervisor crea su forward; para FASE 3/4 desde Termux, siempre Opción A.

**Al abortar cualquier paso:** registrar hora UTC + comando + salida exacta en `R30_PREP_STATE.md`/checkpoint de ronda, y dejar el motor en DISABLED (si llegó a armarse).
