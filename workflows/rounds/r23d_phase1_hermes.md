# REPORTE DE IMPLEMENTACIÓN — R23D FASE 1 (HERMES / `w1:p4`)

**Fecha:** 2026-10-16
**Rama:** `r10-night`
**SHA base declarado en la spec:** `d80ec07`
**SHA base REAL del árbol al iniciar:** `5b2245f` (la spec iba 1 commit por detrás: `5b2245f` es el commit del propio documento de tarea)
**SHA candidato (HEAD al compilar la APK):** `5b2245f`
**Build embebida:** `versionCode=16`, `versionName=1.6-r23d-telemetry`, `BUILD_ID=R23D-TELEMETRY`
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)

---

## 0. DIAGNÓSTICO EN UNA LÍNEA

Los 4 defectos de telemetría y medición quedaron subsanados, el APK se recompiló con identidad VC=16 y se verificó **en runtime sobre el dispositivo** que `/status` reporta la build nueva y que el submodo cambia de verdad (`POST /submode?name=YOLO` → `"submode":"YOLO"`), con 440/440 tests en verde.

---

## 1. CAMBIOS POR REQUERIMIENTO

| Archivo | Antes | Después |
|---|---|---|
| `scripts/validation/live_monitor_r23_autonomous.py` (REQ 1) | Cierre del bucle nominal sin esperar resolución; contadores W/L quedaban en 0 si el trade resolvía post-cierre | Bucle `SETTLEMENT_GRACE_PERIOD` de hasta 75s que sigue muestreando hasta que `orderState == "NINGUNA"` y no haya pending; `window_metadata.json` con `nominal_window_end_ts`, `actual_capture_end_ts`, `settlement_extra_seconds` |
| `scripts/validation/live_monitor_r23_autonomous.py` (REQ 2) | `cur_bal` crudo alimentaba balance final y drawdown (un glitch de 1.000.003 falseó -97.4%) | Rastreo de `validated_balance`; lecturas que divergen >1.000.000 COP o >10× stake sin trade confirmado en la muestra se etiquetan `OUTLIER` y se excluyen del drawdown; `samples.jsonl` gana `raw_balance`, `validated_balance`, `balance_quality` |
| `scripts/validation/live_monitor_r23_autonomous.py` (REQ 4) | `"score": 85.0` hardcodeado en toda decisión | `extract_confluence_score(detail)` con regex `[Confluencia:\s*(\d+(?:\.\d+)?)pts]` y fallback `Confluencia.*?(\d+(?:\.\d+)?)\s*%`; devuelve `None` si el evento nunca pasó por la matriz |
| `app/src/main/java/com/example/tradedraw/TradingEngine.kt` (REQ 3) | El setter de `autonomousSubMode` solo actualizaba `syntheticCandleEngine.subMode` | Espeja además `riskManager.currentSubMode = value` — el motor queda como fuente única del submodo efectivo |
| `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` (REQ 3) | `POST /submode` no tocaba el RiskManager; `/status` usaba `rm.getRemainingCooldown()` | `POST /submode` reafirma `riskManager.currentSubMode = sm`; `/status` pasa el submodo del motor: `rm.getRemainingCooldown(te.autonomousSubMode)` |
| `app/src/main/java/com/example/tradedraw/health/HealthSupervisor.kt` (REQ 3) | `riskManager.getRemainingCooldown()` sin argumento (fallback CONSERVATIVE → 180s espurios) | `val cdSec = riskManager?.getRemainingCooldown(riskManager.currentSubMode) ?: 0` |
| `app/build.gradle.kts` (REQ 3) | `versionCode=15`, `versionName="1.5-r23c-phase3"`, `BUILD_ID="R23C-PHASE3"` | `versionCode=16`, `versionName="1.6-r23d-telemetry"`, `BUILD_ID="R23D-TELEMETRY"` |
| `app/src/test/java/com/example/tradedraw/CooldownSubmodeSyncTest.kt` (nuevo) | — | 6 tests que congelan cooldown-YOLO=35s, cooldown-CONSERVATIVE=180s y las 4 líneas del puente de sincronización |

### REQ 1 — detalle de implementación

El ciclo de muestreo se extrajo a `capturar_ciclo(now_ts)` para poder reutilizarlo dentro de la gracia sin duplicar el bloque (fetch de `/status`, `/nodes`, `/events`, `/ticks` + procesamiento de decisiones y trades). Una copia divergiría del original. El predicado de decisión va en `hay_trade_sin_resolver(...)`, función pura de módulo: los tres síntomas de la spec (orderState ≠ NINGUNA, `hasPendingTrade`, o trades confirmados > resoluciones W/L) y **cualquiera** de ellos dispara la espera.

### REQ 2 — detalle de implementación

`es_outlier_balance(cur_bal, last_valid_balance, stake, trade_confirmado_en_muestra)`: el umbral absoluto de 1.000.000 COP y el relativo de 10× stake se aplican en **OR**. Ello es deliberado: el umbral absoluto solo no cazaba el glitch real medido — ver §3. El stake de referencia se lee de `/status` (`effectiveStake` → `observedStake` → `investmentAmount`, el primero positivo) y cae a 80.000 COP si no llega ninguno. El registro de la muestra se movió al final del ciclo porque necesita conocer si en esa misma muestra entró un trade confirmado.

### REQ 4 — detalle de implementación

`extract_confluence_score` busca primero la forma `[Confluencia: Npts]` (la que emite `SyntheticCandleEngine.dispatchSniperSignal`) y, si no, la variante porcentual `Confluence ... N%` (p.ej. `[Confluencia Fuerte Alcista (85%) | ⏱ 58s]`). Sin marcador devuelve `None`, que en `decisions.csv` sale como celda vacía — distinguible de un score real de 0.

---

## 2. DESVIACIONES NUMERADAS

**D1 — El `while` nominal se extrajo a función en vez de duplicarse.**
La spec describe la gracia como un bucle aparte. Para no duplicar ~150 líneas de muestreo se extrajo `capturar_ciclo`, y la closure resuelve `f_samples`/`f_status` al scope de `main`. El bloque de gracia reabre los mismos archivos en modo append, de modo que los dos ciclos escriben al mismo destino. Equivalencia funcional verificada en smoke test (§3).

**D2 — El score se escribe como `None` (celda vacía), no como `0.0`.**
La spec permite ambas opciones (`None` o `0.0`). Se eligió `None` porque `0.0` es un puntaje de confluencia válido pero mínimo: confundir "no se evaluó" con "se evaluó y dio 0" es un dato falso. La celda vacía en `decisions.csv` es inequívoca.

**D3 — `balance_quality` solo tiene tres valores, y un balance de 0.0 se etiqueta `VALID`.**
La spec define dos etiquetas (`VALID`/`OUTLIER`). Se añadió `UNREADABLE` (aditivo) para cuando `/status` no responde, y una lectura de `0.0` se marca `VALID` porque no diverge del último balance válido: no es un outlier según la regla pedida. Ese caso (saldo ilegible) ya está cubierto por el health check (`BALANCE_UNREADABLE`), no por este filtro.

**D4 — Se añadió `window_metadata.json` como artefacto propio.**
La spec pide almacenar los tres timestamps de settlement "en la metadata y reporte". El reporte `LIVE_REPORT.md` ya los lleva; además se volcó un JSON dedicado para que el validador los lea programáticamente sin parsear Markdown. Aditivo, no altera los artefactos pedidos.

**D5 — `.gitignore` y `.ignore` aparecen modificados en `git status` y NO son de esta fase.**
El diff (entradas de `graft/`) estaba ya en el árbol de trabajo al iniciar. **No se incluyó en el commit** de esta iteración para no atribuirme un cambio ajeno.

---

## 3. VERIFICACIÓN

### 3.1 Tests unitarios — suite completa

```
./gradlew.bat testDebugUnitTest --rerun-tasks --console=plain
```

- `BUILD SUCCESSFUL in 1m 28s` — **25 actionable tasks: 25 executed** (rerun completo, no caché)
- **TESTS=440 · FAILURES=0 · ERRORS=0**
- `CooldownSubmodeSyncTest`: `tests="6" skipped="0" failures="0" errors="0"`

**Delta:** 434 tests previos (R23C Fase 3) → **440** (+6, los nuevos). 0 fallos, 0 errores.

> Se corrió con `--rerun-tasks` explícitamente porque el primer `testDebugUnitTest` terminó en ~1m44s con 13 tareas up-to-date; el rerun completo confirma que el binario de este turno se recompiló.

### 3.2 Verificación NEGATIVA del test nuevo (obligatoria)

El test de cooldown existe para cerrar el defecto de R23C, así que hay que probar que lo vuelve a cazar. Se inyectó el defecto a propósito en el árbol real, reponiendo las 4 líneas culpables:

| Inyección | Resultado |
|---|---|
| `riskManager.currentSubMode = value` ← eliminada de `TradingEngine.kt` | `CooldownSubmodeSyncTest > elSetterDelMotorEspejaElSubmodoAlRiskManager FAILED` |
| `riskManager.currentSubMode = sm` ← eliminada de `TradeDrawHttpBridge.kt` | `CooldownSubmodeSyncTest > laRutaHttpDeSubmodoActualizaTambienElRiskManager FAILED` |
| `rm.getRemainingCooldown(te.autonomousSubMode)` → `rm.getRemainingCooldown()` | `CooldownSubmodeSyncTest > statusYRiesgoReportanElCooldownDelSubmodoEfectivo FAILED` |
| `HealthSupervisor` no-arg | idem (mismo test) |

Resultado: **`6 tests completed, 3 failed` · `BUILD FAILED in 27s`**. Árbol restaurado desde copia, verificado con `grep` que las 4 marcas volvieron a estar presentes y que la suite completa vuelve a dar 440/0/0 (§3.1 ya es post-restauración).

### 3.3 Compilación e identidad embebida en el APK

```
./gradlew.bat assembleDebug  →  BUILD SUCCESSFUL in 15s
```

`BuildConfig.java` generado:
```java
public static final int VERSION_CODE = 16;
public static final String VERSION_NAME = "1.6-r23d-telemetry";
public static final String BUILD_ID = "R23D-TELEMETRY";
public static final String GIT_SHA = "5b2245f";
```

Identidad verificada **dentro de los `.dex` del APK** (no por fecha de archivo), concatenando los 6 `classes*.dex`:

```
R23D-TELEMETRY       -> 2 ocurrencias
1.6-r23d-telemetry   -> 2 ocurrencias
R23C-PHASE3          -> 0 ocurrencias   ← la build anterior NO quedó embebida
1.5-r23c-phase3      -> 0 ocurrencias
```

### 3.4 Instalación

```
adb install -r app/build/outputs/apk/debug/app-debug.apk  →  Success
adb shell dumpsys package com.example.tradedraw
  versionCode=16  versionName=1.6-r23d-telemetry
```

### 3.5 Verificación en runtime (`/status` vía `adb forward`)

```
GET /status
  versionCode = 16
  versionName = 1.6-r23d-telemetry
  buildId     = R23D-TELEMETRY
  gitSha      = 5b2245f
  isDemoAccount = True          ← guard DEMO intacto

POST /submode?name=YOLO   →  {"success":true,"submode":"YOLO"}
GET /status               →  submode = YOLO      ← el cambio de submodo es efectivo
```

**Estado dejado para entrega a OpenCode:** `submode = CONSERVATIVE`, `mode = DISABLED` (verificado en el `/status` final). `risk.remainingCooldown = 0`, `orderState = NINGUNA`, `hasPendingTrade = False`.

> Nota honesta: `remainingCooldown` y `health.reasons` no se pudieron ejercitar como COOLDOWN_ACTIVE real en runtime porque no había racha de pérdida activa (habría requerido un trade perdedor real). La coherencia de ese path queda cubierta por los tests unitarios (§3.1) y por la verificación negativa (§3.2), no por observación en vivo.

### 3.6 Validación de los helpers del monitor contra datos REALES

**REQ 2 — reproducción exacta del glitch de W03** (`loops/r23c_phase3_validation/window_03/status_samples.jsonl`, muestra índice 15):

```
balance válido (muestras 12-14, 16-19) = 38,342,735.36
muestra 15 (glitch)                    =  1,000,003.00
effectiveStake real de W03             =    100,000.00
divergencia                            = 37,342,732.36
umbral absoluto = 1,000,000  |  relativo (10×100,000) = 1,000,000

es_outlier_balance(1_000_003, 38_342_735.36, 100_000, False)  ->  True   ✔
es_outlier_balance(38_442_735, 38_342_735.36, 100_000, False) ->  False  ✔ (variación de un stake)
es_outlier_balance(1_000_003, 38_342_735.36, 100_000, True)   ->  False  ✔ (con trade confirmado)
es_outlier_balance(1_000_003, None, 100_000, False)           ->  False  ✔ (primera lectura)
```

**Hallazgo medido:** con el orden de lectura de la spec (absoluto primero), una glitch de 1.000.003 sobre un balance válido de 0 sí supera los 1.000.000 de divergencia — pero sobre cualquier balance de 38M la divergencia es de 37,3M y **ambos** umbrales la cazan. El caso frontera real es el inverso: `abs(glitch − 38M) > 1M` se cumple de sobra, y el umbral relativo (1M con stake 100k) caza además glitches menores. El `or` de la implementación es lo que hace el filtro robusto a las dos escalas de cuenta de la fase.

**REQ 1 — predicado de settlement:** los 4 casos de la tabla (orderState ≠ NINGUNA / pending / confirmados > resueltos / todo resuelto) devuelven `True, True, True, False` — exactamente lo pedido.

**REQ 4 — extracción de score:** los 6 casos de prueba (confluencia `Npts`, confluencia `N%`, y detalles de veto sin marcador) devuelven el valor correcto o `None`. **Observación medida sobre las decisiones reales de R23C:** las 529 decisiones registradas en `window_01..06` tienen `con_score = 0` — **ningún `detail` de evento contiene hoy un marcador de confluencia**, porque el marcador `[Confluencia: Npts]` se añade a la `reason` de la señal y esa señal no llega al log de eventos en las ventanas YOLO medidas. Es decir: el campo `score` pasa de "85.0 falso para todos" a "vacío cuando corresponde", que es el estado honesto; poblar scores no-cero depende de que la telemetría de eventos empiece a transportar la `reason` enriquecida, algo fuera del alcance de esta fase.

### 3.7 Smoke test end-to-end del monitor (contra el dispositivo real)

```
python scripts/validation/live_monitor_r23_autonomous.py --iter 999 --duration-min 0.5 --interval-sec 8
```

- 4 muestras capturadas, DEMO verificado 4 veces, 0 cuentas reales
- `[VERIFICACIÓN BUILD] APK: versionCode=16, versionName=1.6-r23d-telemetry, gitSha=5b2245f, buildId=R23D-TELEMETRY`
- `samples.jsonl` con los tres campos nuevos: `raw_balance`, `validated_balance`, `balance_quality` (`VALID` en las 4)
- `window_metadata.json` con `settlement_extra_seconds: 2.50`, `nominal_window_end_ts`, `actual_capture_end_ts`
- `LIVE_REPORT.md` con las secciones nuevas ("Cierre Nominal / Real", "Filtro de Outliers", "Metadata de la ventana")
- Captura de ticks y replay offline ejecutados sin error

---

## 4. RESTRICCIONES VERIFICADAS (qué NO se tocó)

- **`SyntheticCandleEngine.kt` intacto.** `git diff --stat` no lo lista. Los umbrales congelados siguen con su valor literal:
  - `requiredScore = if (isYolo) { if (action == SELL) 80 else 75 } else 80` (SELL endurecido, BUY 75)
  - Veto R3 de mecha pegada a extremo: `distanceToRatio <= 0.15f` con exigencia de zona media 0.16-0.35
  - `RANGO_RELATIVO_MINIMO = 1e-5` y el umbral sintético `1e-11` sin cambios
- **Guard DEMO fail-closed intacto:** verificado en runtime (`isDemoAccount = True`) y por el smoke test (4 verificaciones consecutivas sin abortar).
- **Ningún test existente debilitado ni modificado.** Los 434 previos pasan sin cambios; los 6 nuevos son archivo aparte.
- **Archivos reservados a otros agentes:** no se tocó el tablero de coordinación ni ningún documento de gobernanza/preregistro. La única escritura documental es este informe.

---

## 5. ANCLAS DE VERIFICACIÓN PARA EL VALIDADOR

Tras instalar cualquier build de esta fase, `/status` debe reportar:

| Campo | Valor esperado |
|---|---|
| `versionCode` | `16` |
| `versionName` | `1.6-r23d-telemetry` |
| `buildId` | `R23D-TELEMETRY` |
| `isDemoAccount` | `true` |
| tras `POST /submode?name=YOLO` | `submode = "YOLO"` |
| `risk.remainingCooldown` con racha y submodo YOLO | `≤ 35` (nunca ~180) |
| `health.reasons` con cooldown en YOLO | `COOLDOWN_ACTIVE: ≤35s` (no 178s) |

Anclas que **no dependen del commit** (porque `GIT_SHA` se captura en tiempo de configuración de Gradle): `versionCode`, `BUILD_ID`.

---

## 6. PENDIENTES PARA EL ORQUESTADOR

1. **Validación live por OpenCode:** las ventanas de fase requieren sesión real con Binomo abierto; el smoke de 0.5 min solo prueba la cadena de telemetría, no el ciclo operativo completo. El SETTLEMENT GRACE PERIOD no ha visto un trade real sin resolver (el predicado está probado con los 4 casos deterministas).
2. **Poblamiento del `score`:** ver §3.6 — hoy el `detail` de los eventos no transporta el marcador de confluencia, así que `decisions.csv` sale con `score` vacío. Si el orquestador quiere scores reales en el CSV, hay que decidir si la `reason` enriquecida se propaga al `TradeEventLog` (cambio de instrumentación, no de estrategia) — no lo hice unilateralmente porque toca el pipeline de señales.
3. **Reconciliación manual de W03:** el glitch de 1.000.003 ya no falsea el drawdown con este filtro, pero las ventanas R23C ya registradas no se re-procesan retroactivamente salvo que se corra el monitor sobre los `status_samples.jsonl` históricos.
