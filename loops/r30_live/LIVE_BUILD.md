# R30 LIVE_BUILD checkpoint — FASE 1 + FASE 2 COMPLETAS (candidato INSTALADO y verificado)

**Ronda**: F0+F1 — DeepCoder build `41032e6a` · 2026-11-13T12:14Z–12:34Z UTC
**Ronda 2 (FASE 2)** — DeepCoder build `41032e6a` · 2026-11-13T12:56Z–13:01Z UTC
**Estado**: ✅ FASE 1 completa (candidato registrado) + ✅ FASE 2 completa (instalado y verificado on-device). Device tocado SOLO para: push a /data/local/tmp + `pm install -r -d` + lecturas de verificación. Cero taps, cero `am start`, cero arranque de overlay/Binomo, cero cambios de modo. `am force-stop` jamás usado.

## ⚠️ app-debug.apk = NO CONFIABLE para R30 (anotado 2026-11-13T13:00Z)
`app/build/outputs/apk/debug/app-debug.apk` sha256 `dfc39a48af6ea3167b70c85fc3512a61f7a29dfd5f72f99d9702920fa78ec282` (mtime 12:41:29Z, POSTERIOR al cierre de la ronda F0+F1 12:34Z) **NO está registrado en este documento y NO fue construido por la ronda F0+F1 registrada aquí** (ningún proceso gradle/daemon del agente seguía activo al cierre; se reportó como sin trabajos en vuelo). Análisis forense: badging idéntico (versionCode 22 / 1.12-r30-live-fusion), **13/14 entradas del zip byte-idénticas al candidato `46b75620…`; única diferencia `classes11.dex` (CRC 7c691c0f→18ddbb38, mismo tamaño 555504)** ⇒ rebuild del mismo árbol `c9e52e9`. [ACTUALIZADO 13:18Z — revisión round-2] La diferencia NO es no-determinismo del dex: es la constante `BUILD_TIMESTAMP` de BuildConfig (`System.currentTimeMillis()` por diseño de gradle, app/build.gradle:31) — diff determinista, sin ningún cambio de código (verificado: 297/297 strings del pool idénticas; única clase con digest distinto = BuildConfig; los 3 rangos de bytes = checksum/SHA-1 del header del dex + el long BUILD_TIMESTAMP + el digest self-referencial de BuildConfig). El rebuild queda totalmente explicado; sigue NO CONFIABLE por no estar registrado en la ronda. **NO usar para R30. El binario oficial de la misión es y sigue siendo `loops/r30_live/candidate_c9e52e9_v22.apk` sha256 `46b756201b1ac719708827ecb4f08b0e018bd591dae7db3676a3bba5b54870bf` — el instalado on-device (verificado abajo) es ese.**

## LIVE_BUILD_ID (candidato)
```
commit:      c9e52e94125b0e51a8b31c44c5e8f88f8c5fb0de  (c9e52e9)
merge:       8bd172f69e1e914976c70ffbf48b9348ad7362d0  (fusión 451787f)
branch:      r10-night (pusheado a origin: 861a717..c9e52e9)
apk:         loops/r30_live/candidate_c9e52e9_v22.apk
apk_sha256:  46b756201b1ac719708827ecb4f08b0e018bd591dae7db3676a3bba5b54870bf
versionName: 1.12-r30-live-fusion      versionCode: 22
BUILD_ID:    R30-LIVE-FUSION        GIT_SHA (BuildConfig, expuesto en /status): c9e52e9
timestamp:   2026-11-13T12:29Z (fin assembleDebug)
```

## FASE 0 — estado real (verificado 12:14–12:18Z)
- `r10-night` @ `861a717` = `origin/r10-night`; árbol limpio salvo no-rastreados `loops/nights_2026-11-13/` (NO tocado, NO commiteado) y `loops/r30_live/`.
- 6 worktrees registrados; `Tradedraw-frontend` @ `451787f` [assistant/frontend-brokers-agent]. Sin procesos gradle/kotlin activos al inicio.
- Dispositivo Poco X6 Pro (adb `emulator-5554`, TCP): TradeDraw versionCode=21 versionName=`1.11-r27h-activeinstance-atomic` en `/data/app/~~PC8l5XcFzKaOY98WV071Kg==/com.example.tradedraw-MHIKD-CEtVtNnmn1dYlTQw==/base.apk` — SIN CAMBIOS desde cierre nocturno (build 451787f, sha 3164d971… según turno nocturno).
- Agent Nexus consultado (AGENTS.md, Dashboard, ficha TradeDraw). Sin trabajo concurrente visible en el árbol principal.

## FASE 1 — fusión 451787f → r10-night
- **HEAD antes**: `861a717c0e857d2b9db0d2d4bf34dac334dec8d7` · **merge**: `8bd172f…` (--no-ff, ort, SIN conflictos) · **HEAD final**: `c9e52e9…` (bump versión + registry v4 + test→v4).
- Incorporados exactamente los 11 archivos de `293c932..451787f` (+433/−309). Merge-base `293c932`; sets de archivos de ambas ramas disjuntos.
- **Preservados intactos** (sha256 verificado post-merge): `BrokerPresenceGateTransport.kt` `798c5bf2…`, `BrokerProcessPresenceSensor.kt` `0142cfbd…`, `BrokerPresenceGateTest.kt` `83335c9b…`; `BROKER_APP_ABSENT` preflight en `AndroidOrderComposition.kt`/`BrokerDetector.kt` sin cambios.
- `TradingEngine.kt` y `TradeDrawHttpBridge.kt`: sin cambios (rama frontend no los toca).

## Preflight de colisiones (documentación del EXIT)
- `scripts/ci/worktree_overlap.py` reactivo → **EXIT 2**: única colisión directa vs `~/Tradedraw-binomo-ws` [assistant/binomo-ws-fix] (`BinomoAuthActivity.kt`, `BinomoWebSocketClient.kt`).
- Justificación (precedente `loops/nights_2026-11-13/CI_REPAIR_STATE.md`): (1) colisión por divergencia ya commiteada + WIP ajeno; la fusión opera en el checkout principal y no puede modificar el disco de otro worktree; (2) WIP ajeno inactivo: mtimes 2026-11-12 08:46:13Z (≈27 h), sin gradle vivo, sin commits nuevos pusheados; (3) la misión ordena integrar 451787f; ningún archivo de otro worktree fue tocado.
- Modo preventivo con claim `--claim loops/r30_live` → **EXIT 0** (claim despejado contra los 5 worktrees).
- Contra los otros 4 worktrees: 0 colisiones de archivo.

## Registro congelado — decisión v4 (evidencia)
- La fusión cambia bytes de `market/.../BinomoWebSocketClient.kt` ⇒ v3 quedaría obsoleto para el test (precedente v2→v3).
- **v3 NUNCA sobrescrito** (inmutable). Nuevo `scripts/research/freeze_registry_v4.py` (análogo exacto: BASE=8bd172f, VERSION=`r30-frozen-8bd172f`, OUTPUT=v4, assert v3→v4 cambia SOLO el transporte WS, write_once, anti-drift post-snapshot).
- Ejecutado 12:22:05Z → **FROZEN_V4: 14 modes × 44 sources**; `parameter_hash` = `07e4911c8fc3a6d92d7a8577a16c6192b3d1200153fc88d2da6985131a4316b0`; `changed_from_v3` = **[BinomoWebSocketClient.kt] ÚNICO**.
- Verificación cruzada: ids 14/14 == v3; **0 cambios en `strategy/`** (9 fuentes byte-idénticas); 0 cambios en core/risk/execution/TradingEngine. 14 estrategias congeladas INTACTAS.
- `tests/test_r29c_inventory.py` apuntado a v4 (commit `c9e52e9`); v3 queda como evidencia histórica.

## Suites ejecutadas (números reales)
| Suite | Resultado | Fuente |
|---|---|---|
| `python3 -m unittest tests.test_r29c_inventory` | **4/4 OK** | 12:22:20Z |
| `python3 -m unittest discover -s tests` | **136/136 OK**, exit 0 | 12:22:30Z |
| Gradle `testDebugUnitTest` + `:core:test` + `:simulation:test` + `assembleDebug` @ merge tree | **BUILD SUCCESSFUL 2m2s**, exit 0 | 12:24–12:26Z |
| Ídem @ commit final `c9e52e9` | **BUILD SUCCESSFUL 1m54s**, exit 0 | 12:27–12:29Z |
| JVM total (JUnit XML, 11 módulos) | **680 tests, 0 fail, 0 err, 0 skip** (app 359, strategy 92, risk 71, market 47, observability 29, ui 33, adapters 25, execution 10, simulation 8, core 6) | 12:29:48Z |
| En el build del árbol final re-ejecutó `:app:testDebugUnitTest` (359); adapters+market+app ejecutaron en el build @8bd172f del mismo contenido base | UP-TO-DATE = resultados del mismo contenido | gradle_r30_final.log |

## CI (GitHub Actions)
- Push `861a717..c9e52e9` → dos runs por diseño del workflow (push + pull_request release): run **43425767943** (push) CANCELADO por la regla `concurrency: cancel-in-progress` del propio workflow (duplicado del mismo SHA); run **43425776924** (pull_request «release: r10-night → main») = run autoritativo → **conclusión `success`, `gh run watch --exit-status` exit 0** (12:34Z). Precedente idéntico: CI verde nocturna 43381855820 era pull_request.

## Identidad de build — justificación del bump
- versionCode 21→22 y versionName `1.12-r30-live-fusion`: el APK instalado hoy es v21 de `451787f`; sin bump, `dumpsys` no distinguiría candidatos. Verificado: única referencia de tests a versionCode comprueba presencia, no valor fijo. `BUILD_ID` → `R30-LIVE-FUSION` (la cadena `R26-AUTONOMOUS-EXPIRY` solo queda en docs históricas `workflows/PREREGISTRO_R26.md`, cero dependencias de código).
- Confirmado en el binario candidato: aapt2 badging `versionCode='22' versionName='1.12-r30-live-fusion'`; BuildConfig `GIT_SHA="c9e52e9"`, `BUILD_ID="R30-LIVE-FUSION"`.
- FASE 2 podrá verificar la identidad por TRES vías independientes: versionCode 22 (dumpsys), GIT_SHA c9e52e9 + BUILD_ID R30-LIVE-FUSION (/status del bridge), sha256 del base.apk == `46b75620…`.

## ⚠️ Hallazgo de integración para FASE 3 (P0 pre-arranque, NO bloquea FASE 1/2)
- El botón de arranque de overlay SIGUE EXISTIENDO en la nueva home (`@+id/boton`, label «Abrir herramientas flotantes»); `OverlayService`, manifest y binding intactos.
- `scripts/td_supervisor.sh` Paso 4 localiza el botón grepeando `INICIAR OVERLAY` → ya no coincide → caerá al fallback fijo (610,1738, verificado contra el layout ANTIGUO sin ScrollView). Riesgo de tap a control equivocado en una recuperación.
- Acción propuesta (FASE 3, con dump real de la home nueva): actualizar grep a `flotantes`/`resource-id=boton` y re-verificar coords. NO editado en esta ronda para no especular sin evidencia on-device. Supervisor `.disabled` — sin riesgo inmediato.

## FASE 2 — NO ejecutada en la ronda F0+F1 (por diseño)
Instalación, arranque, DEMO-positivo, AUTONOMOUS, WATCHDOG, trades: pendientes de la siguiente ronda tras verificación del coordinador de este candidato. Nada instalado; ningún tap; ningún cambio de modo del motor; cero operaciones.

---

# FASE 2 — COMPLETADA (ronda 2, registro definitivo)

## LIVE_BUILD_ID definitivo (INSTALADO)
```
LIVE_BUILD_ID:   R30-LIVE-FUSION-v22-46b75620
commit:          c9e52e94125b0e51a8b31c44c5e8f88f8c5fb0de (c9e52e9, r10-night)
apk fuente:      loops/r30_live/candidate_c9e52e9_v22.apk
apk_sha256:      46b756201b1ac719708827ecb4f08b0e018bd591dae7db3676a3bba5b54870bf
versionName:     1.12-r30-live-fusion      versionCode: 22
BUILD_ID:        R30-LIVE-FUSION        GIT_SHA (BuildConfig): c9e52e9
install_ts:      2026-11-13T12:59:41Z (pm install -r -d → "Success")
dispositivo:     adb emulator-5554 (Poco X6 Pro, TCP)
base.apk:        /data/app/~~gd4NGIhflESrTKzTdwIKJw==/com.example.tradedraw-gkxHvqJ1myKt9PgNVyJu2g==/base.apk
base.apk sha256: 46b756201b1ac719708827ecb4f08b0e018bd591dae7db3676a3bba5b54870bf  ← IDÉNTICO al candidato
staging:         /data/local/tmp/r30candidate.apk (sha verificado in-situ antes de instalar; dejado como artefacto de reparación)
```

## Procedimiento ejecutado (sin desviaciones del runbook del coordinador)
1. **Pre-install (read-only, 12:59:33Z)**: instalado v21/`1.11-r27h-activeinstance-atomic`; a11y setting con los 3 servicios (`com.omkar.voiceflow/…`, `af.shizuku.plus.api/…AICorePlusService`, `com.example.tradedraw/…AutoTradeAccessibilityService`); `pidof` vacío; batería 79%.
2. `adb push loops/r30_live/candidate_c9e52e9_v22.apk /data/local/tmp/r30candidate.apk` → `46b75620…` verificado **on-device antes de instalar**.
3. `pm install -r -d /data/local/tmp/r30candidate.apk` → **`Success`** (12:59:41Z).
4. **Verificación post-install (12:59:48Z, tres vías)**:
   - `dumpsys package`: `versionCode=22`, `versionName=1.12-r30-live-fusion`.
   - `pm path` → `sha256sum` del `base.apk` instalado = `46b75620…54870bf` — **identidad byte-exacta con el candidato**.
   - A11y: setting `enabled_accessibility_services` **INTACTO** (mismos 3 servicios, mismo orden); TradeDraw aparece en **Bound services** (Service[label=TradeDraw, TYPE_WINDOW_STATE_CHANGED, timeout 200]) y **SALIÓ de Crashed services** (pre-install estaba listado como crashed; post-install ya no). Re-bind ocurrió por el propio `install -r` — **NO fue necesario el toggle del supervisor** (la receta queda como fallback si alguna futura reinstalación lo pierde).
   - Proceso vivo: PID 26032 (spawn espontáneo del bind de accesibilidad durante la instalación; no hubo `am start` del agente).
5. Batería post: 79% (sin consumo medible). Cero taps, cero overlays, cero Binomo, ningún modo armado.

## Estado del dispositivo tras FASE 2 (para FASE 3)
- TradeDraw v22 `1.12-r30-live-fusion` instalado, identidad verificada, proceso vivo por bind a11y, **ni DEMO-check ni armado ejecutados**.
- Supervisor sigue `.disabled`. No tocar hasta FASE 3.
- Nota de coordinación registrada: `scripts/td_supervisor.sh` aparece MODIFICADO en el árbol (working tree, sin commit) con el fix `btn_headless`/"Iniciar con WebSocket" que responde al hallazgo de integración P0 documentado arriba por la ronda F0+F1 — trabajo de OTRO agente, NO tocado por esta ronda, SIN commit de esta parte.

## 🔁 RONDA DE REVISIÓN (DeepCoder review, 13:00–13:05Z) — verificación independiente + fix quirúrgico del supervisor

**Verificación independiente de la FASE 1 (todo CONFIRMADO):**
- Merge `8bd172f` + `c9e52e9` re-inspeccionados: los 11 archivos frontend de `293c932..451787f` están byte-idénticos a `451787f` en HEAD (`git rev-parse` comparado uno a uno); gate de presencia intacto; `strategy/ core/ risk/ execution/ simulation/ adapters/` sin diff `861a717→c9e52e9`.
- Registry v4 re-verificado por script propio: 616/616 SHA256 registrados == disco; v3→v4 difiere SOLO en `BinomoWebSocketClient.kt` + `parameter_hash`; **v3 intacto (0 líneas de diff)**.
- Suites re-ejecutadas: Python discover **exit 0**; Gradle `:app :strategy :risk :market :adapters :core :simulation + assembleDebug` **BUILD SUCCESSFUL (exit 0, 1m38s)** — recuentos JUnit: app 359, strategy 92, risk 71, market 47, adapters 25, core 6.
- CI: run **43425776924** (pull_request) = **success** (re-confirmado con `gh run list`); push 43425767943 cancelled por concurrency (patrón del workflow, esperado).
- APK candidato re-verificado: badging v22 / `1.12-r30-live-fusion`. **[ERRATUM 13:18Z — revisión round-2] La línea original era FALSA: `sha256 46b75620…` NO es el sha256 de `app-debug.apk`** (en disco: `dfc39a48af6ea316…`; re-calculado hoy). Lo que SÍ es cierto: la reconstrucción local produce identidad idéntica — la única clase compilada que difiere es `BuildConfig` por `BUILD_TIMESTAMP` (gradle `System.currentTimeMillis()`, app/build.gradle:31); 296/297 clases del mapa de digests de `classes11.dex` y 999/1000 entradas del zip son byte-idénticas al candidato. Ver §NO CONFIABLE arriba.
- Worktrees ajenos intactos (`Tradedraw-frontend` sigue en `451787f`, árbol limpio).

**Defecto encontrado en el artefacto documental de la ronda anterior (corregido):**
- LIVE_BUILD.md §"Hallazgo de integración" proponía actualizar el grep del supervisor a `flotantes`/`resource-id=boton` (**INCORRECTO**): en la home nueva, `R.id.boton` ("Abrir herramientas flotantes") está **OCULTO** (`visibility=GONE`, MainActivity.kt:50 — coincidente con R30_PREP_STATE.md §11 del agente prep). Tocar ese nodo es imposible y el botón real de arranque del servicio es **`btn_headless` ("Iniciar con WebSocket")**, que envía `EXTRA_HEADLESS_MODE=true`.

**Fix aplicado (`scripts/td_supervisor.sh`, en r10-night):**
1. **Paso 4** ahora localiza por `resource-id="com.example.tradedraw:id/btn_headless"` + texto "Iniciar con WebSocket". El fallback fijo (610,1738) del layout antiguo **se elimina: FAIL-CLOSED** (si la jerarquía no es legible → aborto sin tap + return 1). Justificación: un tap a ciego sobre un ScrollView con cards nuevas puede pulsar controles equivocados (p.ej. "Detener TradeDraw" o el spinner); el tap fallido aborta la recuperación de forma segura y reintenta el ciclo siguiente.
2. **Paso 8** fail-closed: AUTO_REARM=1 solo re-arma AUTONOMOUS si el propio supervisor **vio** `AUTONOMOUS` en el ciclo previo a la caída (`LAST_MODE`/`MODE_AT_RECOVER_START`); DESCONOCIDO/DISABLED/MANUAL **nunca** se escalan (antes: re-armaba a ciegas).

**Pruebas del fix (harness con adb/curl simulados vía funciones exportadas; aislamiento probado: 100% de llamadas registradas como FAKEADB, cero adb real):**
| Escenario | Setup | Resultado |
|---|---|---|
| A | caída, modo previo desconocido, AUTO_REARM=1 | tap en coords localizadas ✓, **0 POSTs de modo** ✓ |
| D' | jerarquía vacía (dump ilegible) | **ABORTO sin tap** (fail-closed) ✓, 0 POSTs ✓ |
| E | AUTONOMOUS visto → caída → dump ilegible | memoria de modo persiste entre reintentos; siempre aborto sin tap ✓ |
| E2 (happy path) | AUTONOMOUS visto → caída → dump OK | tap ✓ → bridge arriba → **exactamente 1 POST** `re-arm -> AUTONOMOUS-SET-OK` ✓ |
| F | MANUAL visto → caída → dump OK | tap ✓ (recuperación funciona), **0 POSTs, sin escalada** ✓ |

**Incidente documentado durante la prueba (remediado):** el primer intento del harness usó PATH-prepend, que el sandbox de Termux NO respeta (`command -v adb` resolvió al adb REAL); un ciclo de recuperación simulado tocó settings de accesibilidad reales y despertó el proceso TradeDraw. **Alcance real: SOLO pasos 1-2 de recover()** — la verificación de re-bind abortó antes de cualquier tap o re-arm (el fail-closed preexistente contuvo el daño). mode_changes.jsonl **sin cambios** (última línea = stop 04:35:31Z nocturno), cero operaciones, cero taps. Baseline restaurado con las recetas del propio supervisor (enabled_accessibility_services = A11Y_IN, accessibility_enabled=0, kill suave del proceso); restauración verificada. Lección registrada en Agent Nexus Errores-Conocidos.

**Identidad del candidato FASE 2 (SIN CAMBIOS):** el APK candidato sigue siendo `loops/r30_live/candidate_c9e52e9_v22.apk` sha256 `46b75620…` (scripts/ no entra en el APK). El commit del fix avanza r10-night HEAD; la verificación de identidad de FASE 2 debe hacerse contra el LIVE_BUILD_ID FIJADO arriba (versionCode 22 + GIT_SHA `c9e52e9` + BUILD_ID `R30-LIVE-FUSION` + sha256 `46b75620…`), **no contra repo HEAD**.
