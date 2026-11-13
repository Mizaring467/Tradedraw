# R30_PREP_STATE.md — Checkpoint FASE 0/3-prep (READ-ONLY)

**Cierre:** 2026-11-13T12:26:30Z UTC. **Agente:** DeepInvestigator prep `076b98ad`.
**Restricción cumplida:** 100% read-only. Cero taps, cero `am start/force-stop`, cero `settings put`, cero `appops set`, cero escrituras run-as, cero instalación, cero armado, cero commits. Única escritura: los 4 entregables de esta ronda en `loops/r30_live/` (+1 screencap de evidencia).

## Entregables producidos

| Archivo | Contenido |
|---|---|
| `BASELINE_DEVICE_R30.md` | Snapshot VERIFICADO 12:13–12:23Z: procesos, bridge, a11y, appops, APKs, batería, reloj, repo, supervisor |
| `R30_STARTUP_PLAN.md` | Plan de arranque seguro paso a paso (PASO 0–8) con FAIL-CLOSED por paso, verificación de identidad, riesgo HyperOS de la reinstall, vía overlay por UI, guard DEMO antes de armar |
| `R30_WATCHDOG_RUNBOOK.md` | Runbook FASE 4: 12 chequeos con comando exacto, ciclo 30 s, máquina de estados CONNECTED/DEGRADED/DISCONNECTED/RECOVERING/VERIFYING, diálogos Binomo, recuperación, no-duplicación, escalado y desarme exacto |
| `baseline_screen_1216Z.png` | Screencap del baseline (Termux en foreground, 1220x2712) |

## Hallazgos clave (resumen ejecutivo)

1. **Ambas apps MUERTAS al baseline** (TradeDraw 11:47:54Z, Binomo 11:50:50Z) por `SwipeUpClean` — gesto humano de recientes, sin crash ni acción de agente. El encargo decía "Binomo corriendo desde ~05:05Z": **caducado**. El arranque parte de cero procesos.
2. **Accesibilidad rota por HyperOS tras el kill:** `accessibility_enabled=0` (antes 1), servicio en `Crashed services` a pesar de estar listado en el setting. Rehabilitación con settings put obligatoria en el arranque; el bind real se verifica con `dumpsys accessibility` (Bound, no Crashed).
3. **r10-night avanzó DURANTE el baseline:** `861a717` → **`8bd172f`** (merge 451787f→r10-night, 12:22:55Z). DeepCoder en fase de build (sin APK nuevo aún). El candidato será ≥ este merge.
4. **Bridge CAÍDO confirmado** (curl `[::1]:8080` exit 7; `127.0.0.1:18080` exit 52 — el forward 18080 existe pero el listener device no). Sin root, el bridge solo alcanzable vía `adb forward` (18080 ya presente, o crear tcp:8080 para la receta del supervisor).
5. **Supervisor PARADO** (desde 11-07; autostart `.disabled`). **PELIGRO:** su default `AUTO_REARM=1` re-arma AUTONOMOUS solo tras recuperación — durante el LIVE RUN arrancarlo solo con `AUTO_REARM=0` o no arrancarlo.
6. **Contrato del bridge verificado en fuente** (TradeDrawHttpBridge.kt): loopback:8080, `/status` con `isDemoAccount/mode/feed.lastTickAgeMs/health`, `/mode?name=` para armar/desarmar, **heartbeat R26 en TODA request** (AUTONOMOUS por bridge caduca a 10 min; por HUD no caduca), `/trade` con guard DISABLED/SHADOW→403.
7. **Guard DEMO fail-closed verificado en código**: TradingEngine.kt:1707-1730 — cualquier trade en AUTONOMOUS con `isDemoAccount=false` → VETO `SEGURIDAD_NO_DEMO` + auto-DISABLED. Es la red de seguridad definitiva; la verificación pre-armado (≤120 s) sigue siendo obligatoria.
8. **Overlay solo por UI**: OverlayService `exported=false`; botón real "🚀 INICIAR TRADE PRO (WebSocket 0ms)" (btn_headless); fallback de coordenadas (610,1738) verificado 11-06 en pantalla 1220x2712.
9. **Batería sin cargador** (88%, descargando) y **load 14.25** por builds en vuelo — riesgo de kills de HyperOS por ahorro durante la sesión larga; monitorear en el watchdog (chequeo #12).
10. **Drift reloj <1 s**; dispositivo UTC-5 (logcat local +5h = UTC); espacio 145G; Android 16 / HyperOS V816.

## Hallazgos clave (resumen ejecutivo)

11. **Candidato construido DURANTE esta prep (12:25Z):** merge `8bd172f` + registry v4 + gradle OK. **versionCode 22 / versionName `1.12-r30-live-fusion`** (bump de identidad hecho por DeepCoder ⇒ verificación inequívoca post-install). **Home UI NUEVA:** botón de overlay = **"Iniciar con WebSocket"** (`btn_headless`, home_strings.xml:19); "Abrir herramientas flotantes" (`R.id.boton`) OCULTO por código (MainActivity.kt:50); nuevo botón "Vincular Binomo" (`btn_link_binomo`, flujo de credenciales — prohibido tocar); **fallback (610,1738) del supervisor ya no es válido** (layout re-hecho con ScrollView+cards). Rondas de hallazgo coincidente: LIVE_BUILD.md §"Hallazgo de integración para FASE 3".
12. **FALLA CONOCIDA del supervisor a corregir en FASE 3** (no bloquea la misión, supervisor está parado): su Paso 4 grepea `INICIAR OVERLAY` → 0 matches con el layout nuevo → caerá al fallback obsoleto (610,1738). Re-verificar grep + coordenadas con dump fresco antes de cualquier uso del supervisor.

## Huecos y pendientes (para el siguiente agente)

- **Identidad del APK candidato:** pendiente hasta que DeepCoder publique build; el PASO 1 del STARTUP_PLAN define el protocolo (push+install -r -d+sha256 on-device+cross-check con /status gitSha/buildId). LIVE_BUILD_ID sin registrar aún en MISSION_STATE_R30.md.
- **`uiautomator dump` sobre Binomo:** no re-testeado en esta ronda (requeriría abrir Binomo = fuera de alcance read-only). Hipótesis vigente: falla; el runbook lo prohibe y usa screencap+/status.
- **Conservación del bind de accesibilidad tras `pm install -r`:** no verificable sin instalar. El plan lo trata como riesgo (comprobar settings+dumpsys tras install, receta de re-bind lista).
- **Transporte IPv6 `[::1]:8080` desde Termux:** hoy falla sin forward tcp:8080 (exit 7). No probado con forward activo (crear el forward sería acción, no lectura). El plan ofrece Opción A (18080, ya activa) y Opción B.
- **`lastTickSource`/valores de feed en vivo:** no observables con apps muertas; el runbook usa umbrales del código (FEED_MAX_AGE_MS=5 s) — validar empíricamente en FASE 3.
- **Contenido real de los diálogos de error de Binomo:** clasificación del runbook §4 basada en patrones conocidos; los textos exactos se verificarán con screencaps en vivo.

## Estado al cierre (una línea por componente)

- TradeDraw: MUERTO · Binomo: MUERTO · Bridge: DOWN (sin listener) · A11y: setting listado, enabled=0, crashed · Overlay appops: allow (persistente) · Supervisor: DOWN (AUTO_REARM=1 peligroso) · Forward: 18080 vivo, 9222 huérfano, 8080 inexistente · APK TD: vc21 3164d971… (a reemplazar) · Binomo: 8.5.0 intacta · Repo: r10-night @8bd172f (merge hecho), 2 worktrees WIP ajenos intactos · Batería 88% sin AC · Drift <1 s.

## Estado del dispositivo al cierre de esta ronda (hallazgos 12:48Z)

**Fuente:** revisiones en vivo de 12:48Z del mismo hilo (ronda 3, prep). Estos hallazgos **obsolec-en parcialmente el baseline 12:13–12:23Z y la línea "Estado al cierre" anterior**: el dispositivo cambió bajo nosotros entre rondas.

- **Accesibilidad REACTIVADA en vivo por actor externo:** `accessibility_enabled=1` (el baseline lo dejaba en 0). No fue acción de esta ronda → el owner (u otro actor) intervino en el dispositivo. No asumir que el estado siga igual al arrancar FASE 3: re-verificar `settings get secure accessibility_enabled` + `dumpsys accessibility` (Bound vs Crashed) en el PASO 2 del STARTUP_PLAN igualmente.
- **TradeDraw VIVO** (pid **21110**) — el baseline lo tenía muerto (11:47:54Z). Alguien/palgo lo relanzó. El arranque de FASE 3 ya no parte de cero procesos para TradeDraw.
- **Binomo MUERTO** — sigue el estado del baseline (11:50:50Z). FASE 3 deberá relanzarlo (PASO 6).
- **Bridge DOWN** — sin listener (sin /status accesible por ningún transporte). Coincide con apps muertas/overlay no iniciado.
- **Batería 81%, sin cargador, ≈ −12%/h** (88%→81% en ~35-40 min de observación intermitente). Cadencia de descarga coherente con build en vuelo + screen-on.
- **Kill de HyperOS a `health` (systemhealth) por CPU a las 12:19:58Z** — HyperOS está matando procesos por ahorro/CPU en este dispositivo AHORA; refuerza el riesgo documentado en hallazgo #9 del baseline.

**⚠️ ADVERTENCIA de batería para FASE 3/4:** con −12%/h sin cargador, una sesión live de 2-3 h parte de 81% y entra en zona de riesgo (<20%, umbral de escalado del watchdog #12) antes de agotar el plan si no se conecta alimentación. **Se recomienda conectar cargador ANTES de armar** (PASO 7). Si el owner no está presente en ese momento, el coordinador debe decidir explícitamente: (a) posponer el armado hasta tener cargador, o (b) arrancar la sesión con 81% y duración limitada — en ese caso el watchdog debe vigilar la batería con prioridad y el plan de cierre (PASO 8) debe dispararse por batería, no solo por reloj. FAIL-CLOSED sugerido: no iniciar armado con batería <60% sin cargador conectado.

---

# Ronda 4 — 2026-11-13T14:22Z — WATCHDOG materializado como ejecutable

**Agente:** prep investigator (mismo hilo). **Entregable:** `scripts/r30_watchdog.sh` (662 líneas, VERIFICADO `bash -n` OK, chmod +x, **SIN rastrear** en git — el commit vendrá después con evidencia; HEAD intacto `691647d`).

## Qué implementa (mapa runbook → código)

- **Transporte:** PRIMARIO `http://127.0.0.1:18080` con FALLBACK `http://[::1]:8080` por ciclo (`fetch_status`, líneas 214-225); registro `transport` en estado. Greps de `/status` con `[[:space:]]*` (`jstr`/`jbool`/`jnum`, :227-231) — espaciado inconsistente del JSON (TradeDrawHttpBridge.kt:211 vs :765).
- **Máquina de estados (runbook §3):** CONNECTED/DEGRADED/DISCONNECTED/RECOVERING/VERIFYING; CONNECTED→DISCONNECTED exige 2 ciclos malos consecutivos (anti-flap); VERIFYING→CONNECTED solo tras un ciclo completo sano (contenido + WS fresco + focus + DEMO). Batería: level cada ciclo; < `--battery-min` (def 25) → ESCALATE_BATTERY: sin `--allow-disarm` escribe marcador `PAUSE_REQUESTED_*.json` en evidence (la pausa la decide el coordinador); con `--allow-disarm` hace el ÚNICO POST posible: `POST /mode?name=DISABLED` + verificación inmediata.
- **Recuperación (runbook §4/§5):** screencap+timestamp a `loops/r30_live/watchdog_evidence/` ante DISCONNECTED; diálogo clasificado SOLO con etiqueta whitelisteada (Reintentar/Reconectar/Reconnect/Retry + «reintentar conexión», insensible a mayúsculas) en el atributo `text=` de un nodo `<node>` cuyo `package=` coincida y con bounds únicos (jamás OK/Aceptar/Continuar; jamás tap a ciego; dump ilegible → UNKNOWN → ESCALATE sin tocar). Ramas: TD muerto→`am start`+re-tap `btn_headless` con dump fresco de UI PROPIA; Binomo muerto→`monkey`; bridge caído con procesos vivos→observa 1 ciclo (BRIDGE_HUNG) antes de ESCALATE. Backoff `min(2^(n-1)×base,300 s)`, máx `--max-recover` (def 3)/incidente. Tras recuperación: GET `/dispatch_recovery` SOLO lectura; jamás re-dispatch ni re-arm (idempotencia §6).
- **Telemetría (TELEMETRY_SCHEMA.md v1.0.0):** `watchdog_state.json` heartbeat cada ciclo (mtime=latido) con contadores `reconexiones_ok/reconexiones_fail/tiempo_desconectado_acum_s/ciclos`; append `watchdog_events.jsonl` con `event_id` UUID5 (namespace `6b1f2a55-0d4e-4a17-9d0e-2b6c8a11f0e1`, implementación bash pura con paridad verificada vs `python3 uuid.uuid5`), `timestamp_utc`+`timestamp_local` (-05:00), prev/new state, `verification_result` VERIFIED|UNVERIFIED|FAILED, `source{file,line}`. `component=WATCHDOG` (el §7 del schema reserva `supervisor`/`ws_transport` — constante editable en :90-93 si el coordinador prefiere el enum del schema).
- **Deducción del POST /mode (fuente leída, citada en cabecera del script):** TradeDrawHttpBridge.kt:205-214 (`"/mode" -> { params["name"] ?: params["mode"] … setTradingMode(m, "bridge:/mode") }`), query parseada en :74-88, body POST fusionado a `params` en :103-125 vía `parseBridgeBody` (:1138-1146, JSON o form-urlencoded); `source` NO es elegible por el cliente (fijo `bridge:/mode`, :209). TradingEngine.kt:108-133: `setTradingMode` arma con `bridge=true` solo si source empieza con `bridge:` (:125-126) y DESARMA con DISABLED (:129-131) ⇒ **un POST AUTONOMOUS desde el watchdog RE-ARMARÍA el motor; por eso su único POST es `name=DISABLED`**. Llamada exacta: `curl --noproxy '*' -s -m 5 -w '%{http_code}' -X POST -o <tmp> "http://127.0.0.1:18080/mode?name=DISABLED"` (fallback `http://[::1]:8080/mode?name=DISABLED`), verificar con `/status` → `"mode":"DISABLED"`.

## Validación offline (harness v2.5, `$TMPDIR`=/data/data/com.termux/files/usr/tmp — NOTA: `/tmp` literal no es escribible en Termux)

`bash /data/data/com.termux/files/usr/tmp/r30_watchdog_harness/wd_harness.sh` → **PASS=43 FAIL=0, exit 0** (log preservado: [watchdog_harness_v2.5_2026-11-13.log](watchdog_harness_v2.5_2026-11-13.log)). Fake adb/curl exportados por nombre con PATH purgado (safebin sin adb/curl — guard contra dispositivo real). Escenarios:

| Escenario | Setup | Resultado (VERIFICADO) |
|---|---|---|
| A estable | todo sano | CONNECTED estable, 2 ciclos, 0 taps, sin WS_DISCONNECT, transport primario, JSONL válido, HEALTH_CHECK presente |
| B bridge caído | ambos transportes 000 | DISCONNECTED, escalate=true motivo BRIDGE_HUNG, WS_DISCONNECT, 0 taps |
| C Binomo muerto | broker pid vacío, WS false | monkey → RECOVERY_OK → VERIFYING → CONNECTED, reconexiones_ok=1, 0 taps, broker_pid=31337 |
| D diálogo Reintentar | retry, WS false | tap ÚNICO (610,1450) → RECOVERY_TAP → VERIFYING → CONNECTED, reconexiones_ok=1 |
| E diálogo desconocido | diálogo de otro paquete | ESCALATE DIALOGO_DESCONOCIDO, 0 taps, sin RECOVERY_TAP |
| F batería sin disarm | 18% < 25 | marcador PAUSE_REQUESTED, SIN POST (DISARMED=0), evento batería |
| G batería con --allow-disarm | 18% < 25 | POST /mode?name=DISABLED verificado (modo fake→DISABLED, DISARMED=1, endpoint en evento, last_status.mode=DISABLED) |
| H fallback transporte | 18080 caído, 8080 vivo | CONNECTED vía `http://[::1]:8080` |

Cadena D completa (eventos reales del harness): `WS_DISCONNECT (DEGRADED→DISCONNECTED) → RECOVERY_TAP (RECOVERING, 610 1450 "Reintentar") → RECOVERY_OK (RECOVERING→VERIFYING, VERIFIED) → HEALTH_CHECK (VERIFYING→CONNECTED, VERIFIED)`.

**Bugs encontrados y corregidos durante la ronda** (todos en harness/watchdog, validados antes de cerrar): (1) `classify_dialogue` contaba bounds de todo el documento cuando el dump era una sola línea (XML anidado) → falsos UNKNOWN/ambiguo; corregido extrayendo nodos `<node>` y aplicando la whitelist solo al atributo `text=` (test aislado 4/4). (2) `uuid5` printf con `%s` extra corrompía UUIDs. (3) `set -u`: variables S_* sin inicializar abortaban el daemon con bridge caído en ciclo 1. (4) Harness v2.3: fakes eludidos (exportados como `fake_adb` en vez de `adb` — bash enlaza por nombre; impacto: solo GETs read-only contra bridge real caído, adb con serial inexistente, 7 screencaps de 0 bytes; SIN mutaciones — fake_state.env sin cambios). (5) `run_scenario` filtraba TICK=/WS=/flags a `$9/$10` de `reset_fake` → cuerpos `/status` fake corruptos. (6) fake adb no registraba TAP_ARG cuando la línea no existía (sed no crea). shellcheck NO instalado en Termux (validación = `bash -n` + harness + validador JSONL/UUID5 en python3 dentro del harness).

**Estado:** script y harness listos; el daemon lo lanzará el coordinador tras READY de FASE 3. NO ejecutado contra el dispositivo (solo el incidente read-only del harness v1 descrito arriba); NO commiteado (`git status` → `?? scripts/r30_watchdog.sh`).

---

# Ronda 5 — 2026-11-13T16:12Z — RATIFICACIÓN Y CIERRE R4 + prep de lanzamiento del daemon

**Agente:** prep investigator (mismo hilo, `076b98ad`). **Alcance:** verificación independiente del cierre de la Ronda 4 y preparación (SIN ejecutar) del lanzamiento del daemon WATCHDOG. Device NO tocado (shade del owner abierta): solo 2 sondeos pasivos (`dumpsys battery`/`dumpsys window`) + 1 `curl GET /status` fallido (HTTP 000, sin listener). Cero commits.

## Verificación independiente (todo VERIFICADO)
1. **Commit `3ebd478abc9400662c7efe9a4fdce451e3e13254`** = HEAD → r10-night = **origin/r10-night** (push confirmado; fecha 11:02:19 -0500 = 16:02:19Z; "watchdog r30: gate health.state (F1-F5) — desviación F1 ratificada con evidencia"). `git status` sin modificaciones rastreadas; `scripts/r30_watchdog.sh` quedó commiteado en `3ebd478` (solo sin-rastrear: `loops/…` evidencia).
2. **Harness v2.6 60/60 re-verificado EN VIVO:** `bash /data/data/com.termux/files/usr/tmp/r30_watchdog_harness/wd_harness.sh` → `VEREDICTO: PASS=60 FAIL=0`, exit 0 (escenario K del gate de salud PASS, taps=0). Coincide con la validación independiente del coordinador.
3. **Gate F1 contra captura real:** `status_t2.json` → `"health": {"state":"READY","reasons":[],"sinceMs":1794575830172,"dataSource":"WEBSOCKET_PURO","notEvaluable":["SCREEN_FEED_MISMATCH"]}`; **0 ocurrencias de "CLEAR"** en el documento. El gate `S_ORDER_OK == "READY"` (script :247/:541/:590) matchea la forma capturada. Fuente del CLEAR: `/dispatch_recovery` (TradeDrawHttpBridge.kt:247-280, CLEAR en :277) — ortogonal al `/status`, como ratificó el coordinador. HealthSupervisor.kt en `app/src/main/java/com/example/tradedraw/health/` (:42, enum State{READY,DEGRADED,BLOCKED}).

## Decisiones de política R4 (transcripción del coordinador — normativas)
1. F1 RATIFICADO como implementado: gate `health.state == READY`; SIN gate sobre `/dispatch_recovery`.
2. BLOCKED vs DEGRADED: NO diferenciar — ventana 60 s única (60/60). Decisión documentada, no gap.
3. F2 parcial (solo `isDemoAccount`): ACEPTADO; limitación conocida documentada.
4. Doble crédito F4 en cortes recuperados: CONOCIDA (solo telemetría, sin fix).
5. Bookkeeping: este checkpoint registra el estado final de ronda; **Agent Nexus lo hace el coordinador al cierre**.

## Lanzamiento del daemon WATCHDOG (PREPARADO — NO ejecutado; pegar al READY de FASE 3)

### Precondiciones — CORREGIDO en Ronda 6 (2026-11-13T16:28Z)
**(a) Transporte: modo por defecto del arranque real = "solo-18080".** El fallback `[::1]:8080` NO es viable hoy: no existe forward `tcp:8080` y sin él `curl http://[::1]:8080/status` termina en exit 7 (sin listener local en 8080; VERIFICADO 16:16:38Z y re-verificado 16:30:00Z junto con `adb forward --list` = solo `tcp:18080` + huérfano 9222). Activar el fallback es OPT-IN y requiere crear explícitamente el forward: `adb forward tcp:8080 tcp:8080` (mutación; la ejecuta quien lance el daemon, no este checkpoint). Hasta entonces el arranque opera solo con el primario `http://127.0.0.1:18080` (forward vivo, VERIFICADO) y el escenario H del harness (fallback 8080) queda como capacidad latente, no como expectativa operativa.
**(b) Batería/AC: se mide con `dumpsys battery` AL MOMENTO del gate, jamás asumida de sondeos previos.** Evidencia de la dinámica (LIVE_BLOCKER sondeos 8-9): AC true + 86% a las 16:03:55Z → AC false + 85% a las 16:16:38Z (owner retiró el cargador entre ambas) → AC false + 80% a las 16:23:57Z (≈ −18%/h). Un gate que reutilizara "AC true de 16:03:55Z" habría armado con el cargador ya retirado.

Precondiciones históricas de Ronda 5 (conservadas para trazabilidad; supersedidas por (a)/(b)): cargador conectado (AC true; level 89% a las 16:05:30Z), focus=launcher/home sin shade, `/status` accesible (aún HTTP 000: apps muertas), forwards `tcp:18080` + `tcp:8080` activos (afirmación de R5 — **corregida en R6: `tcp:8080` no existe como forward**; 9222 huérfano preexistente, irrelevante).

```bash
# (0) opcional: smoke-test de 1 ciclo antes del daemon:
cd /data/data/com.termux/files/home/Tradedraw && bash scripts/r30_watchdog.sh --once --interval 1 && cat loops/r30_live/watchdog_state.json

# (1) lanzar daemon 30 s, SIN --allow-disarm (el desarme lo decide el coordinador):
cd /data/data/com.termux/files/home/Tradedraw && \
nohup setsid bash scripts/r30_watchdog.sh --serial emulator-5554 --interval 30 \
  > loops/r30_live/watchdog_daemon.log 2>&1 < /dev/null & \
echo "watchdog pid=$!"

# (2) verificación de latido (~45 s después del paso 1):
sleep 45; pgrep -af 'r30_watchdog.sh --serial' | grep -v grep; \
stat -c 'mtime_state=%y' loops/r30_live/watchdog_state.json; date -u '+now_utc=%Y-%m-%dT%H:%M:%SZ'; \
python3 - <<'EOF'
import json
s = json.load(open('loops/r30_live/watchdog_state.json'))
print('state=%s cycle=%s transport=%s battery=%s escalate=%s' % (s['state'], s['cycle'], s['transport'], s['battery'], s['escalate']))
lines = [l for l in open('loops/r30_live/watchdog_events.jsonl').read().splitlines() if l.strip()]
for l in lines: json.loads(l)
print('JSONL OK: %d eventos' % len(lines))
EOF
# Criterio de éxito: pid vivo; mtime_state < ~35 s respecto de now_utc;
# state=CONNECTED/DEGRADED (bridge arriba) o DISCONNECTED (entorno caído, sin taps);
# JSONL válido.

# (3) parada limpia (trap INT/TERM → on_exit escribe estado final):
pkill -INT -f 'r30_watchdog.sh --serial emulator-5554'
```

Notas operativas: (a) `--serial emulator-5554` = serial real conectado (coincide con el default; explícito por seguridad). (b) Los contadores reanudan desde el `watchdog_state.json` existente (continuidad por diseño); borrar el state file ANTES del arranque si se quiere sesión limpia. (c) `--allow-disarm` deliberadamente ausente. (d) **Sondeo manual del bridge NO es read-only perfecto:** cada `GET /status` alimenta el heartbeat R26 (AUTONOMOUS por bridge caduca a 10 min sin polls) — durante sesión live, no sondear /status manualmente más allá del watchdog sin coordinarlo.
