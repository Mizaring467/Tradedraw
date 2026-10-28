# R26 · INFORME — ANOMALÍA DEL CONTADOR VERIFICADA + CADUCIDAD DE `AUTONOMOUS` CERTIFICADA

**Fecha:** 2026-10-27 (America/Bogota) · **Rama:** `r10-night`
**Commits:** `f97abfa` (verificación de la anomalía) · `f491ca4` (caducidad de AUTONOMOUS) · este informe
**Build certificada:** `f491ca4` · versionCode **20** · `1.10-r26-autonomous-expiry` · `R26-AUTONOMOUS-EXPIRY`
**APK:** SHA-256 `e534359179c8dfb6d9658480e2f599e3e4f3bfa5898b2f1b07a828bd2933465e`
**Dispositivo:** Redmi A1 `220733SL` (Android 12) · `192.168.1.196:5555` · DEMO en todas las muestras
**Preregistro:** [`workflows/PREREGISTRO_R26.md`](../../workflows/PREREGISTRO_R26.md), fijado antes de armar la primera ventana

---

## 1. Reconstrucción de loops (estado real, no narrado)

| Loop | Qué cerró | Veredicto documentado |
| :--- | :--- | :--- |
| R11–R15 | Medición en vivo, observabilidad, latencia, calibración | Reportes por ronda en `loops/R1x_REPORTE.md` |
| R16–R20 | **Arena de anomalías** | Aislada. **No mezclable** con evidencia nueva (instrucción vigente) |
| R21 | Pipeline trazable y reproducible | **PASS** (`loops/R21_REPORTE.md`) |
| R22 | Instalación ADB + runtime en vivo | **PASS**, capital preservado (`loops/R22_REPORTE.md`) |
| R23J | OPERATIVO y CONTABLE | **PASS operativo**; paridad y desempeño **NO CONCLUYENTE** |
| R23K | Cierre del Cubo 2, higiene, informe | **PASS** de fases; defecto histórico: preregistro posterior a 8/19 ventanas |
| R24 | Fase A (auditoría), B (4 fixes), C/D (ventanas) | A **PASS-AUTOR** (firma independiente **REVOCADA**); B PASS; C/D **NO EVALUABLE** por transporte |
| R25 | Cubo de instrumentación (3 ventanas) | **V1–V4 PASS**; **V5 NO EVALUABLE** por muestra (2 trades live vs ≥20). Cierre `48509ca` |
| **R26** | **Anomalía 7→9 verificada + caducidad de `AUTONOMOUS`** | Este informe |

**Punto exacto donde se retomó:** R25 cerrado, con dos pendientes estructurales declarados en
`R25_CIERRE.md` §5: (a) caducidad de `AUTONOMOUS` sin heartbeat — *decisión 6 de R24, tres rondas sin
implementar*; (b) V5 exige ≥ 20 trades live. R26 ataca (a) y **no** toca (b).

---

## 2. Coordinación con otro agente (mismo punto de trabajo)

Al abrir la sesión, `git status` mostraba dos pruebas **sin commitear** modificadas a las 21:53–21:54, y
el log de Hermes (`~/.hermes/logs/agent.log`, sesión `20261026_212817_4059e1`) mostraba un turno
cerrado a las **21:54:49** trabajando exactamente esta anomalía. Verificado antes de tocar nada:

- El turno de Hermes había **terminado** (`finish_reason=stop`) y su revisión de fondo cerró a las 21:55:49. No había escritura concurrente.
- Su trabajo consistía en: (i) un test JVM en `LiquidationIdempotencyTest` y (ii) dos casos Python con modelos del código antiguo/nuevo.

**Qué hice con ese trabajo:** no lo reescribí. Lo **adopté, lo compilé y lo ejecuté** contra el código
real, y lo commiteé en `f97abfa` con el crédito y con una corrección de encuadre (§3.3). Después seguí
por el pendiente estructural (a), que ningún agente estaba tocando.

---

## 3. Anomalía reportada: ¿una victoria duplicaba el contador (7→9)?

### 3.1 Causa raíz, con código y SHA

El código **anterior** a los fixes (verificado con `git show 6fc51f4^:...`) tenía tres rutas que
incrementaban el marcador sin registrar ninguna clave de liquidación:

| Ruta | Código previo | Efecto |
| :--- | :--- | :--- |
| HUD WIN | `OverlayService.kt:1271` → `recordTradeWin(force = true)` | 7 → 8 |
| Bridge `/win` | `TradeDrawHttpBridge.kt:374` → `recordTradeWin(force = true)` | 7 → 8 |
| Fallback del motor | `TradingEngine.kt:637-639` → `if (!resolvePendingTradeWin()) recordTradeWin(force = true)` | **8 → 9** |

La ruta manual limpiaba el pendiente pero **no** marcaba el trade como liquidado; dentro del
`handler.post`, `resolvePendingTradeWin()` devolvía `false` y el fallback volvía a sumar. **Esa es la
secuencia 7 → 9.**

### 3.2 Estado actual (código `f491ca4`)

| Ruta | Código actual |
| :--- | :--- |
| HUD | `OverlayService.kt:1275` → `settleManual("MANUAL_HUD", win = true)` |
| Bridge | `TradeDrawHttpBridge.kt:393` → `settleManual("MANUAL_BRIDGE", win = true)` |
| Motor (2 bloques) | `settleTradeOnce(settleTradeId, settleStartMs, win = …)` |
| Fallback activo | `settleTradeOnce` deduplica por `liquidationKey` (`tradeId` o `ts:startMs`) |

Además, la clave se captura **antes** del `post` (`TradingEngine.kt:624-625` y `2692-2693`), que es el
punto donde el fix podía fallar: si se capturara dentro del `post`, la ruta manual ya habría vaciado el
pendiente y la clave sería `ts:0` (sin deduplicación).

### 3.3 Evidencia ejecutada

| Prueba | Resultado |
| :--- | :--- |
| `LiquidationIdempotencyTest` (JVM real, 10 casos, incluye `victoriaNoDuplicaContadorDeSieteANueve`) | **10/10 PASS** — el contador va 7→8 y el segundo intento devuelve `false` |
| Suite Kotlin completa | **513/513 PASS** = 507 de R24/R25 + 1 de la anomalía (`f97abfa`) + 5 del watchdog (`f491ca4`) |
| `tests/test_code_audit_defects.py` | 5/5 PASS |

**Precisión sobre el test Python:** sus clases `OldRiskManagerWithRaceCondition` /
`FixedRiskManagerWithIdempotency` son **modelos escritos a mano** del comportamiento, no ejecuciones de
la APK. Sirven como especificación de regresión, **no** como evidencia del binario; el documento así lo
declara. La evidencia real es el test JVM contra `RiskManager` y el diff de git de §3.1.

**Verificación de cobertura de rutas (lo que un test aislado no cubre):** se auditaron **todas** las
llamadas de `app/src/main` a los incrementos de contador. Las únicas entradas son `settleManual`
(HUD/bridge), `resolvePendingTradeWin/Loss` y `settleTradeOnce` (motor). No queda ninguna ruta que llame
a `recordTradeWin(force = true)` sin clave. `resolvePendingTradeWin/Loss` es seguro porque está
`@Synchronized` y `recordTradeWins` limpia el pendiente dentro del mismo lock.

---

## 4. Pendiente (a): caducidad de `AUTONOMOUS` — implementación

**Defecto:** `AUTONOMOUS` no caducaba. R24/Incidente 2 dejó 3 h 15 min de exposición; R25/W02 dejó
~5 min armado sin red ni sondeo. Ninguno hizo trades, por suerte.

**Solución (`f491ca4`), mínima y acotada:**

| Pieza | Detalle |
| :--- | :--- |
| `AutonomousWatchdog` | Política pura con tiempo inyectado. Solo caduca si se armó desde el bridge. |
| `setTradingMode` | `arm(now, byBridge = source.startsWith("bridge:"))`; `disarm()` al salir. |
| Tick | Cada 30 s; si el silencio ≥ **10 min** → `DISABLED` con `source=engine:autonomous-expiry`. |
| Bridge | Toda petición renueva el heartbeat. |

**Por qué 10 min:** el preregistro R25 exige sondeo de `/status` cada ≤ 2 min; 10 min son 5× esa
cadencia, así que una ventana bien supervisada no puede caducar por error.

**Por qué no caduca el armado por HUD:** el operador acaba de pulsar el botón; caducar esa sesión sería
un cambio de comportamiento no pedido. Es una decisión de alcance, declarada en el preregistro y
cubierta por test.

**Tests:** `AutonomousWatchdogTest` **5/5** (tope exacto, heartbeat que reinicia el contador, armado por
HUD que nunca caduca, desarme, heartbeat sin armado).

---

## 5. Certificación en vivo (build `f491ca4`, preregistro `PREREGISTRO_R26.md`)

### 5.1 P1 · Control positivo — caducidad

- Armado por bridge a las **22:01:38.172** (`source=bridge:/mode`).
- **Cero peticiones al bridge durante 12 min** (solo `input keyevent WAKEUP` por ADB cada 45 s; arnés en `scripts/validation/r26_p1_silence.sh`).
- Caducidad observada a las **22:12:08.187** → **silencio = 630,0 s = 10 min 30 s** (tope 10 min + tick de 30 s). **Dentro de la ventana preregistrada 10–11 min.**
- `/mode_log`:
  `de=AUTONOMOUS a=DISABLED source=engine:autonomous-expiry`
  `origen=TradingEngine.modeChangeOrigin:141<TradingEngine$autonomousWatchdogTick$1.run:223<...`
- Estado final (claves correctas, anidadas bajo `risk`): `mode=DISABLED`, `risk.orderState="NINGUNA"`, `risk.hasPendingTrade=false`, `risk.tapsDespachados=0`, `isDemoAccount=true`.
- **P1 = PASS.** Falsación no alcanzada: no siguió armado y no caducó antes de 10 min.

### 5.2 P2 · Control negativo — no caduca con contacto

- Armado a las **22:14:40.751** (`source=bridge:/mode`).
- Heartbeats `GET /ping` (arnés `scripts/validation/r26_p2_heartbeat.sh`) en los segundos **1793157403, 524, 644, 764, 884, 945** → cadencia real
  **121 s** (los "2 min exactos" del preregistro, con 1 s de deriva del `sleep`).
- A los **11 min 05 s** de armado: `mode=AUTONOMOUS`, `hasPendingTrade=null`.
- `/mode_log` **no** contiene ningún evento `engine:autonomous-expiry` en el intervalo (el único del
  ring es el de P1, `1793157128187`). Total de eventos en el ring: 4.
- El discriminante que importa: a los 10 min de armado (22:24:40) hubo heartbeat a las 22:24:44; si la
  caducidad midiera desde el armado y no desde el último contacto, el motor ya estaría `DISABLED`.
- **P2 = PASS.** Falsación no alcanzada.

### 5.3 Integridad del arnés

- El PID de la app fue **16195 al arrancar y 16195 al cerrar** (observado en consola, no persistido):
  el servicio **no se reinició** durante P1 ni P2. La persistencia se corrobora además con
  `buildTimestamp=1793156419020` idéntico en P0 y P3. La caducidad de P1 no puede atribuirse a una
  recreación del servicio.
- Batería: 56 % al abrir (persistido en `device_metadata_raw.txt`); 52 % **observado en consola** a las
  22:25:58, sin volcado crudo persistido (corregido tras auditoría). Sin aborto por batería.
- Cierre: `GET /mode?name=DISABLED` → `mode=DISABLED`, `risk.orderState="NINGUNA"`,
  `risk.hasPendingTrade=false`, `risk.tapsDespachados=0`, `isDemoAccount=true`, `gitSha=f491ca4`,
  `buildId=R26-AUTONOMOUS-EXPIRY`.
- Actividad del motor durante AUTONOMOUS: `signalsGenerated` 0 → 25 (P1) → 45 (P2) y `tapsDespachados=0`
  en las cuatro capturas: el motor evaluó señales y **no despachó ni una orden** (accesibilidad caída).

### 5.4 P3 · Armado por HUD

No se prueba en vivo (12 min adicionales sin incidente asociado). Cubierto por test JVM. Declarado como
límite de la evidencia, no como PASS.

---

## 6. Problemas y límites encontrados

1. **Accesibilidad caída tras reinstalar** (`health: ACCESSIBILITY_DOWN`, `WS_DISCONNECTED`,
   `BALANCE_UNREADABLE`). No afecta a esta certificación (la caducidad no depende de visión ni de
   accesibilidad) y de hecho reduce el riesgo de taps. Pero implica que **R26 no mide nada de trading** y
   que el balance sigue ilegible (`0.0`), por lo que la cifra de R25 (36.865.735,68 COP) no se pudo
   reconfirmar en `/status`.
2. **`numpy` depende del intérprete.** El `python3` del PATH (venv `free-claude-code`) **no** tiene
   numpy: bajo él, `discover` da 37 tests y 2 errores de import (`test_quant_model_biases`,
   `test_simulation_validation_engine`). El Python **del sistema** (`/data/data/com.termux/files/usr/bin/python3`)
   sí lo tiene (numpy 2.4.4) y con `PYTHONPATH=.:validation_engine` la suite completa pasa:
   **43 tests, OK**. Es decir: la afirmación "100 % PASS" es cierta solo con el intérprete del sistema;
   con el del PATH es falsa. Queda documentado el comando correcto para que no vuelva a confundirse.
3. **Sin `logcat` fiable**: la evidencia se apoya en `/status` y `/mode_log` (buffers propios), como en
   R13–R25.
4. **V5 sigue NO EVALUABLE**: R26 no acumula muestra (2 trades por ventana ⇒ ≥10 ventanas). No se mezcla
   con R25.
5. **`backups/`** (capturas de sesiones previas) sigue sin versionar y sin regla en `.gitignore`.

---

## 7. Siguiente loop

- **R26 queda cerrado** con P1/P2 como certificación del mecanismo. No se abre una ventana de trading en
  esta ronda.
- **R27 (propuesto, sin preregistrar aún):** acumular ≥ 20 trades live para evaluar V5. Requiere ≥ 10
  ventanas de 20 min con el arnés de R25 (watchdog de `adb forward` + keepalive) y la accesibilidad
  restaurada. Ese es el único pendiente estructural que queda de R25 tras cerrar (a).
- Pendiente menor: restaurar accesibilidad y recalibrar antes de cualquier medición de trading.

---

## 8. Fe de erratas tras auditoría de pares (2026-10-27 22:45–22:55)

Auditorías independientes ejecutadas por los paneles **AGY** (idempotencia), **OpenCode** (evidencia
R26) y **Hermes** (consistencia documental), más la del orquestador. Correcciones aplicadas:

| # | Error detectado | Corrección | Evidencia |
| :--- | :--- | :--- | :--- |
| E-1 | `orderState=null, hasPendingTrade=null` | Son `risk.orderState="NINGUNA"` y `risk.hasPendingTrade=false`; están anidados bajo `risk` | `P1_status_final.json`, `P3_cierre.json` |
| E-2 | "Batería 52 % al cerrar" sin artefacto | Observación de consola; se marca como no persistida | `device_metadata_raw.txt` solo tiene apertura (56 %) |
| E-3 | "PID 16195 al cerrar" sin artefacto | Observación de consola; corroborada por `buildTimestamp` idéntico | `P0` vs `P3` |
| E-4 | Preregistro fechado 22:20 | Fijado realmente a las 22:00:43 (mtime), 55 s antes de P1; commit posterior a las mediciones | `stat`, `P1_t0.txt`, adenda del preregistro |
| E-5 | "513/513" sin XML en disco | La corrida dirigida de AGY borró los XML de la suite completa. Regenerada y **persistida en el repo** | `kotlin_test_summary.txt` (513 tests, 0 fallos, 2 skipped) |
| E-6 | "numpy no instalado" | Solo falta en el venv del PATH; el Python del sistema lo tiene y la suite pasa 43/43 | §6.2 |

**No verificado por esta ronda (y por tanto no afirmado):** estado del dispositivo tras las 22:50 —
el ADB Wi-Fi perdió la ruta (`No route to host`). La última lectura verificada es `P3_cierre.json`
(22:25:58, `DISABLED`).
