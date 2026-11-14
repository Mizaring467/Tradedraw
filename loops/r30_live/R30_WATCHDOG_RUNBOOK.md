# R30 WATCHDOG RUNBOOK — LIVE WATCHDOG (FASE 4, agente dedicado)

**Autor:** DeepInvestigator prep `076b98ad` — 2026-11-13T12:26Z.
**Público:** el agente del LIVE WATCHDOG que el coordinador asignará en FASE 4, exclusivo durante la sesión.
**Entradas:** [BASELINE_DEVICE_R30.md](./BASELINE_DEVICE_R30.md) (contrato del bridge), [R30_STARTUP_PLAN.md](./R30_STARTUP_PLAN.md) (PASO 7 armado).
**Regla de oro:** nunca tap a ciego en diálogo desconocido; nunca reenviar decision_id/trade_id tras corte; DEMO positivo ≤120 s antes de cualquier re-arme.

---

## 1. Qué monitorear y con qué comando exacto (un ciclo = una pasada)

El watchdog mantiene UN transport elegido en el arranque: **Opción A `tcp:18080` (recomendada y verificada desde Termux)**; Opción B `tcp:8080`+`[::1]` SOLO si el supervisor crea ese forward (ver STARTUP_PLAN PASO 0 y la nota de transporte al final de esta sección). Todos los comandos con `adb -s emulator-5554`.

| # | Chequeo | Comando exacto | Esperado |
|---|---|---|---|
| 1 | **Bridge vivo** | `curl --noproxy '*' -s -m 5 -w '%{http_code}' "http://127.0.0.1:18080/status" -o /tmp/td_status.json` | HTTP 200, `app":"TradeDraw"` |
| 2 | **Identidad runtime** | `grep -oE '"(versionCode|versionName|gitSha|buildId)":[[:space:]]*[^,}]*' /tmp/td_status.json` | == identidad del LIVE_BUILD_ID (no cambia nunca en sesión) |
| 3 | **Modo** | `grep -oE '"mode":[[:space:]]*"[A-Z]+"' /tmp/td_status.json` | `AUTONOMOUS` (o `DISABLED` si coordinador pausó) |
| 4 | **DEMO** | `grep -oE '"isDemoAccount":[[:space:]]*(true|false)' /tmp/td_status.json` | `true` SIEMPRE; un solo `false` → desarme inmediato + escalar |
| 5 | **Proceso vivo** | `adb shell pidof com.example.tradedraw` | no vacío |
| 6 | **Binomo vivo** | `adb shell pidof com.marketly.trading` | no vacío |
| 7 | **Ventana activa** | `adb shell dumpsys window \| grep mCurrentFocus` | `com.marketly.trading/...` en operación; `com.example.tradedraw` solo si humano |
| 8 | **Feed fresco (tick)** | `grep -oE '"lastTickAgeMs":[[:space:]]*[0-9]+' /tmp/td_status.json` | < 5000 (el motor usa FEED_MAX_AGE_MS=5 s como frontera de frescura) |
| 9 | **Feed conectado** | `grep -oE '"connected":[[:space:]]*(true|false)' /tmp/td_status.json` | `true` |
| 10 | **Modo journal (disco)** | `adb shell run-as com.example.tradedraw tail -n 1 files/mode_changes.jsonl` | coincide con modo de /status; sin `SEGURIDAD_NO_DEMO` |
| 11 | **Order state / health state** | `grep -oE '"orderState":[[:space:]]*"[A-Z_]+"' /tmp/td_status.json` y `grep -oE '"state":[[:space:]]*"[A-Z]+"' /tmp/td_status.json` | `orderState` es TELEMETRÍA del engine (NINGUNA/ENVIADA/CONFIRMADA/NO_PROCESADA, core/OrderState.kt — coherente con ciclo esperado, p.ej. NINGUNA→ENVIADA→CONFIRMADA→NINGUNA). El gate de SALUD del watchdog usa `"state"` (health.state de HealthSupervisor.kt:42: READY/DEGRADED/BLOCKED; capturas reales status_t1|t2.json: `"health":{"state":"READY"}`). NOTA: `"CLEAR"`/`"AWAITING_BROKER_CONFIRMATION"`/`"UNCERTAIN"` NO existen en /status — son del endpoint `/dispatch_recovery` (TradeDrawHttpBridge.kt:277). |
| 12 | **Batería** | `adb shell dumpsys battery \| grep -E 'level|AC powered'` — patrón SIN escape interno (ver nota) | level > 20; si no, escalar |

**Corrección ronda 3 — grep de batería:** sin escape de la tubería dentro del patrón (`'level|AC powered'`). En `grep -E` (ERE), `\|` casaría con una barra vertical literal, i.e. buscaría la cadena exacta `level|AC powered`, que no existe en la salida de `dumpsys battery` → el chequeo #12 devolvería vacío siempre. Con alternación real (`level|AC powered`) el grep muestra tanto `level: NN` como las líneas de fuente de alimentación (`AC powered: true/false`).

**Nota transporte (corregida ronda 3):** usar **`http://127.0.0.1:18080`** — el forward `tcp:18080` ya existe y es el transporte verificado desde Termux. **`[::1]:8080` NO funciona desde Termux sin crear el forward `tcp:8080`** (verificado en baseline: exit 7); solo sería válido si el supervisor crea su propio forward. Elegir UNO en el arranque y no cambiarlo en sesión. Cada request al bridge alimenta el heartbeat R26 (caducidad de AUTONOMOUS a 10 min). Con ciclo ≤60 s el armado por bridge se mantiene vivo sin problema. **Si el watchdog deja de poller >10 min, el motor se desarma SOLO (función de seguridad, no fallo).**

**Semántica de códigos de retorno del bridge (ratificada 20:31Z, asimilación R15/R20):** al evaluar el chequeo #1, el `rc` de curl distingue dos fallos distintos:
- **rc=52 (Empty reply):** el forward adb `tcp:18080` está VIVO pero el bridge NO tiene listener al otro lado (proceso de TradeDraw muerto **O** proceso vivo con OverlayService no recreado — caso verificado 20:24:02Z: pid 14410 vivo ∧ rc=52). **NO inferir muerte de proceso desde rc=52 solo** — leer SIEMPRE rc=52 + `pidof com.example.tradedraw` + `mode_changes.jsonl` juntos.
- **rc=7 (Failed to connect):** no hay forward activo o conexión rechazada a nivel TCP (el forward `tcp:18080` desapareció o adb se reinició).
- Regla del gate: la conclusión "proceso muerto" exige pidof vacío; la conclusión "servicio no corrido" exige pidof vivo ∧ rc=52; la conclusión "sin transporte" exige rc=7. Cualquier otra combinación → screencap + escalar.

**Screencap ante anomalía (siempre):**
```bash
adb -s emulator-5554 exec-out screencap -p > r30_live/wd_$(date -u +%H%M%S)_anomaly.png
```
(uiautomator dump está PROHIBIDO como decisión sobre Binomo: anti-automatización, falla con "could not get idle state". Para UI: **screencap PRIMERO (F3: la evidencia se captura a disco ANTES de cualquier decisión; sin PNG no hay decisión ni tap)** + mCurrentFocus + /status; el dump uiautomator queda como best-effort SOLO para clasificar con la whitelist estricta de §4 — sin texto legible → UNKNOWN → ESCALATE, jamás tap).

## 2. Frecuencia recomendada: **ciclo de 30 s** (POLL_S=30)

Justificación:
- La caducidad del armado por bridge es 10 min ⇒ un ciclo de 30 s da 20 oportunidades de mantener el heartbeat dentro de cada ventana de caducidad (holgado).
- La ventana DEMO del guard es 120 s ⇒ 4 ciclos para detectar un flip a real.
- El motor usa FEED_MAX_AGE_MS=5 s ⇒ 6 ciclos por minuto bastan para detectar feed muerto en <30 s.
- Coste: 120 curl + 120 adb calls/hora — consumo de batería/adb despreciable frente al load actual del dispositivo.
- **Aceleración dinámica:** ciclo 10-15 s cuando el gate de salud `health.state ≠ READY` (trade en curso; ver chequeo #11) o `hasPendingTrade:true`; volver a 30 s tras liquidación confirmada.

## 3. Máquina de estados

```
CONNECTED    : bridge 200 + proceso vivo + Binomo focus + feed fresh + isDemoAccount:true + health.state READY (chequeo #11; gate de salud sobre "state", NO sobre orderState)
DEGRADED     : CONNECTED pero (feed age > 5 s | balance no leído | health.state != READY sostenido >60 s sin liquidación — valores READY/DEGRADED/BLOCKED del HealthSupervisor)
DISCONNECTED : bridge no-200/timeout O pidof vacío (TD o Binomo)
RECOVERING   : protocolo de recuperación en curso (§5)
VERIFYING    : tras recuperación: DEMO + identidad + ventana activa verificados ANTES de declarar CONNECTED
```

Transiciones:
- `CONNECTED → DISCONNECTED`: 2 ciclos consecutivos con bridge fallido o proceso muerto (evitar flap por 1 fallo puntual de curl).
- `DISCONNECTED → RECOVERING`: automático (protocolo §5).
- `RECOVERING → VERIFYING → CONNECTED`: solo tras §6 completo (guard DEMO re-verificado).
- `CONNECTED → DEGRADED → (2 ciclos) → DISCONNECTED` si el síntoma persiste.
- **Cualquier estado + `isDemoAccount:false` → desarme inmediato + escalar.**

## 4. Diálogos de error de Binomo: reconocimiento (prohibido tap a ciego)

Binomo rechaza uiautomator ⇒ **el contenido del diálogo NO es legible por árbol**. Reconocimiento INEQUÍVOCO = screencap leído por el agente (visión) + contexto:

| Evidencia | Diálogo | Acción permitida |
|---|---|---|
| Screencap muestra texto "Reconectar"/"Reconnect" + botón único | reconexión de sesión | tap del botón UNA vez (identificado inequívocamente) |
| "Reintentar"/"Retry" + network error | fallo de red | tap Reintentar UNA vez; si reaparece 2 veces → backoff (§5) |
| Cualquier diálogo sin texto legible en el screencap | **DESCONOCIDO** | **PROHIBIDO tap.** Screencap + escalar al coordinador |
| Balance/área de cuenta no visible, app en blanco | posible crash de WebView | tratar como DISCONNECTED (§5 relanzamiento) |

Reglas:
- Un tap permitido = 1 intento. Repetir solo si el screencap SIGUE mostrando el MISMO diálogo conocido tras 30 s.
- Jamás tap sobre "OK"/"Aceptar"/"Continuar" de un diálogo cuyo efecto se desconozca (puede confirmar conversión a cuenta real, depósito, cierre de sesión, etc.).
- Tras CUALQUIER diálogo: re-verificar `isDemoAccount:true` en el siguiente /status. Un flip a false ⇒ desarme + escalar (posible cambio de cuenta).

## 5. Protocolo de recuperación (DISCONNECTED → RECOVERING)

Orden de verificación y acciones (máx. 3 ciclos de recuperación; luego escalar):

1. **Diagnóstico (30-60 s):** qué falló exactamente:
   - Bridge solo: `pidof` vivo + focus OK → bridge colgado. Esperar 1 ciclo (puede recuperarse solo); si no, recovery del supervisor NO está corriendo ⇒ decidir con coordinador (kill suave via `run-as com.example.tradedraw kill <pid>` está verificado, pero es acción destructiva: requiere OK del coordinador en FASE 4).
2. **Proceso TradeDraw muerto:** la accesibilidad quedará crashed (patrón HyperOS, baseline §4). `adb shell am start -n com.example.tradedraw/.MainActivity` → re-tap del botón de inicio ("**Iniciar con WebSocket**" en el candidato 8bd172f; layout NUEVO: NO reutilizar coordenadas antiguas — localizar SIEMPRE con uiautomator dump fresco sobre la UI propia, que SÍ es legible) → esperar /status 200. Si uiautomator falla sobre la home nueva → screencap + escalar coordenada al coordinador antes de tap (ver PASO 4 del STARTUP_PLAN).
3. **Binomo muerto o sin feed:** `adb shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1` → esperar 8 s → screencap → verificar focus + demo. **Backoff:** tras 2 relanzamientos fallidos, esperar 5 min antes del 3º; máximo 3.
4. **Feed sin ticks (bridge arriba, lastTickAgeMs alto):** verificar focus en Binomo; si Binomo está en diálogo de reconexión (§4) actuar ahí; si Binomo está sano y el feed sigue muerto >2 min → escalar (posible problema WS del broker; no reiniciar el engine por cuenta propia).
5. **Registro de tiempos:** cada recuperación anota: hora inicio, causa, acciones, hora de VERIFYING, hora de CONNECTED. Si hubo trade en vuelo al cortar → activar regla de NO-duplicación (§6) ANTES de re-armar.

**Backoff limitado:** reintento n cuesta `min(2^n × 30 s, 5 min)`. Máximo 3 ciclos de recuperación por incidente; luego estado PAUSED + escalado.

## 6. Regla de NO-duplicación tras corte (idempotencia)

- **Prohibido reenviar** un `requestId`/`decision_id`/`trade_id` ya despachado (aunque el resultado sea incierto). El bridge expone el estado de incertidumbre:
```bash
curl --noproxy '*' -s "http://127.0.0.1:18080/dispatch_recovery"
# campos: pendingRequestId, decisionId, state (CLEAR / UNCERTAIN / AWAITING_BROKER_CONFIRMATION / CONFIRMED_AWAITING_SETTLEMENT), requiredAction
```
- `state=UNCERTAIN` o `pendingRequestId≠∅` ⇒ hay un despacho sin resolver: **esperar liquidación/evidencia del broker**; el único camino de resolución es `POST /dispatch_recovery` con evidencia del historial del broker (`confirmation=BROKER_HISTORY_REVIEWED` + outcome + reference + observedAtMs) — documentado en TradeDrawHttpBridge.kt:247-280. Sin evidencia del historial NO hay reenvío automático.
- Tras un corte con `risk.hasPendingTrade:true` → esperar a que /status muestre `orderState` estable y balance actualizado antes de considerar nueva operativa.
- Anotar en el log del watchdog todos los requestId vistos (del /events o del /dispatch_recovery) para el informe de sesión.

## 7. Criterios de escalado al coordinador (pausa AUTONOMOUS)

Escalar INMEDIATAMENTE (y desarmar si el motor está armado) si:
1. `isDemoAccount:false` en cualquier ciclo (posible cuenta REAL).
2. 2 ciclos seguidos de bridge caído con proceso vivo (motor armado sin supervisión — ventana de 10 min antes de auto-desarme).
3. TradeDraw o Binomo mueren 2 veces en la sesión.
4. Diálogo desconocido en Binomo (§4).
5. `lastTickAgeMs` > 60 000 (feed muerto >1 min con Binomo sano en foreground).
6. Batería < 20% o dispositivo se apaga/lock (keyguard aparece).
7. Cualquier comportamiento no cubierto por este runbook.

**Pausa AUTONOMOUS — llamada exacta y verificación:**
```bash
curl --noproxy '*' -s "http://127.0.0.1:18080/mode?name=DISABLED"
# Verificación INMEDIATA (mismo ciclo):
curl --noproxy '*' -s "http://127.0.0.1:18080/status" | grep -oE '"mode":[[:space:]]*"[A-Z]+"'   # debe dar "mode":"DISABLED" (con o sin espacio tras los dos puntos: el espaciado del JSON es inconsistente campo a campo)
# Doble verificación en disco:
adb shell run-as com.example.tradedraw tail -n 1 files/mode_changes.jsonl   # "a=DISABLED" con source bridge:/mode
```
Si el bridge está caído y el motor armado: **no se puede desarmar por HTTP**; opciones en orden: (a) esperar auto-desarme del watchdog R26 (≤10 min sin heartbeat); (b) `adb shell am force-stop com.example.trading` NO existe → `adb shell am force-stop com.example.tradedraw` (mata proceso; al reiniciar, el motor arranca en DISABLED por diseño — ver mode_changes.jsonl `onCreate(servicio recreado) → DISABLED`); (c) kill suave `run-as kill`. Escalar al coordinador en todos los casos.

**Registro:** cada ciclo fallido/anómalo → línea en el log del watchdog (hora UTC, chequeo, valor, acción). Screencap ante cualquier anomalía visual. El log alimenta `R30_LIVE_SESSION_001` y el informe final.

---

## Correcciones ronda 3 — 2026-11-13T13:01Z

**Fuente:** revisión del mismo hilo (ronda 3, prep — correcciones a los entregables propios de la ronda 1). Sin cambios de comportamiento fuera de lo listado:

1. **Chequeos 2/3/4/8/9/11 y desarme §7:** todos los greps del `/status` usan ahora `':[[:space:]]*'` tras la clave (`'"mode":[[:space:]]*"[A-Z]+"'` etc.). El espaciado del JSON del `/status` es inconsistente campo a campo (verificado en TradeDrawHttpBridge.kt:211 `"mode":"..."` vs :765 `"mode": "..."`); los greps estrictos sin espacio fallarían sobre campos con espacio.
2. **Chequeo #12 (batería):** patrón corregido a `grep -E 'level|AC powered'` — sin escape de la tubería. `'level\|AC powered'` en ERE casaría con la cadena literal `level|AC powered` (inexistente en la salida de dumpsys) → chequeo siempre vacío.
3. **Transporte:** nota §1 corregida: usar `http://127.0.0.1:18080` (forward `tcp:18080` ya existente). `[::1]:8080` no funciona desde Termux sin crear el forward `tcp:8080` (baseline: exit 7).

## Correcciones ronda 4 — 2026-11-13T15:30Z (paquete F1–F5, scripts/ únicamente)

1. **F1 — gate de salud sobre `"state"` (chequeo #11, §3):** el watchdog ya no gatea sobre `orderState` (ese valor queda como TELEMETRÍA en `last_status`; el enum del engine `NINGUNA/ENVIADA/CONFIRMADA/NO_PROCESADA` es el estado real, no un bug). El gate de salud lee `health.state` del `/status`. **Corrección de evidencia sobre el encargo:** el encargo pedía gatear `state == "CLEAR"`, pero `"CLEAR"` NO existe en el `/status`: es un valor del endpoint `/dispatch_recovery` (`TradeDrawHttpBridge.kt:277`, ruta definida en :247). El `/status` emite `health.state` desde `HealthSupervisor.kt:42` (`enum class State { READY, DEGRADED, BLOCKED }`; capturas reales `status_t1.json`/`status_t2.json`: `"health":{"state":"READY"}`). El gate implementado es `health.state == READY`; cualquier otro valor (p.ej. `DEGRADED` sostenido >60 s) activa `ORDER_LATE → DEGRADED` con la misma ventana de 60 s y sin escalar por sí solo (los eventos de telemetría del watchdog dicen "orderState CLEAN (health.state=READY)").
2. **F2 — guard DEMO exige parse no-vacío:** si `isDemoAccount` viene vacío (body truncado, soTimeout=3000), el guard DEMO se OMITE ese ciclo (no cuenta falso positivo demo-flip) y el watchdog marca DEGRADED con evento explícito `parse_degraded`.
3. **F3 — screencap-primero (§1 y rama 4 de recuperación):** la evidencia PNG se captura a disco ANTES de cualquier decisión; si el screencap sale vacío → ESCALATE sin tap. El uiautomator dump queda como best-effort SOLO para clasificar con la whitelist estricta de §4 (sin texto legible → UNKNOWN → ESCALATE; jamás tap).
4. **F4 — dwell DISCONNECTED:** `tiempo_desconectado_acum_s` suma el intervalo real de cada ciclo con `STATE=DISCONNECTED` (no solo al salir del estado), de modo que un corte nunca recuperado acumula tiempo correctamente.
5. **F5 — TMPDIR seguro:** el script fija `TMPDIR="${TMPDIR:-$HOME}"` y hace fallback a `$HOME` si el TMPDIR del entorno no existe o no es escribible (en Termux `/tmp` literal no es escribible).
