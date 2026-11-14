# INFORME DE AUDITORÍA C — VERIFICACIÓN DE EVIDENCIA R26

**Fecha de auditoría:** 2026-10-28  
**Auditor:** OPENCODE (sesión independiente de solo lectura)  
**Objetivo:** Verificar la integridad, coherencia temporal y validez de la evidencia documental y de telemetría de la ronda R26 en el repositorio `TradeDraw`.  
**Alcance:** Inspección estricta de solo lectura sobre el repositorio (`/data/data/com.termux/files/home/Tradedraw`) y generación del informe exclusivamente en `/data/data/com.termux/files/home/dsh-deleg/r26/AUDIT_C_R26.md`.

---

## 1. Verificación de Commits y Presencia del Watchdog en HEAD

### 1.1 Commits auditados (`f97abfa`, `f491ca4`, `75f005e`)

```bash
git show --name-status --oneline f97abfa
```
**Salida observada:**
```
f97abfa test(r26): verifica que una victoria no duplica el contador (7->8, nunca 7->9)
M	app/src/test/java/com/example/tradedraw/LiquidationIdempotencyTest.kt
A	loops/SIMULATION_VALIDITY_AUDIT.md
M	tests/test_code_audit_defects.py
```
*Archivos tocados:* 3 archivos (2 de test, 1 de auditoría previa).

---

```bash
git show --name-status --oneline f491ca4
```
**Salida observada:**
```
f491ca4 feat(r26): caducidad de AUTONOMOUS armado por bridge sin heartbeat
M	app/build.gradle.kts
A	app/src/main/java/com/example/tradedraw/AutonomousWatchdog.kt
M	app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt
M	app/src/main/java/com/example/tradedraw/TradingEngine.kt
A	app/src/test/java/com/example/tradedraw/AutonomousWatchdogTest.kt
```
*Archivos tocados:* 5 archivos (implementación del watchdog, wiring en bridge y motor, build gradle y test unitario).

---

```bash
git show --name-status --oneline 75f005e
```
**Salida observada:**
```
75f005e docs(r26): certifica la caducidad de AUTONOMOUS y cierra el pendiente de R25
A	loops/r26_validation/P0_baseline.json
A	loops/r26_validation/P0_mode_log.json
A	loops/r26_validation/P1_mode_log.json
A	loops/r26_validation/P1_mode_log_pre.json
A	loops/r26_validation/P1_status_final.json
A	loops/r26_validation/P1_t0.txt
A	loops/r26_validation/P1_timeline.txt
A	loops/r26_validation/P2_beats.txt
A	loops/r26_validation/P2_mode_log.json
A	loops/r26_validation/P2_status_final.json
A	loops/r26_validation/P2_t0.txt
A	loops/r26_validation/P3_cierre.json
A	loops/r26_validation/R26_REPORTE.md
A	loops/r26_validation/device_metadata_raw.txt
A	scripts/validation/r26_p1_silence.sh
A	scripts/validation/r26_p2_heartbeat.sh
A	workflows/PREREGISTRO_R26.md
```
*Archivos tocados:* 17 archivos (artefactos de evidencia, scripts de arnés, preregistro e informe de cierre).

---

### 1.2 Presencia del Watchdog en HEAD

```bash
ls -la app/src/main/java/com/example/tradedraw/AutonomousWatchdog.kt \
       app/src/main/java/com/example/tradedraw/TradingEngine.kt \
       app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt
```
**Salida observada:**
```
-rw------- 1 u0_a610 u0_a610   2241 Oct 28 02:57 app/src/main/java/com/example/tradedraw/AutonomousWatchdog.kt
-rw------- 1 u0_a610 u0_a610  57027 Oct 28 02:58 app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt
-rw------- 1 u0_a610 u0_a610 175343 Oct 28 02:58 app/src/main/java/com/example/tradedraw/TradingEngine.kt
```
**Confirmación:** Los tres componentes existen en HEAD:
- `AutonomousWatchdog.kt`: Implementa la clase `AutonomousWatchdog` con política pura (umbral por defecto 10 min, método `recordContact(nowMs)`, `arm(source, nowMs)`, `disarm()`, y `shouldExpire(nowMs)`).
- `TradingEngine.kt`: Instancia `autonomousWatchdog`, tick periódico de 30 s (`autonomousWatchdogTick`), y desarme automático a `DISABLED` con origen `engine:autonomous-expiry`.
- `TradeDrawHttpBridge.kt`: Enruta las llamadas `/ping` y peticiones HTTP registrando `TradingEngine.instance?.recordWatchdogContact()`.

---

## 2. Auditoría de `workflows/PREREGISTRO_R26.md`

### 2.1 Umbral y Predicciones Declaradas
- **Umbral de caducidad:** 10 minutos (600 segundos) de silencio total. Tick evaluador en motor cada 30 segundos, permitiendo una cota máxima teórica de caducidad entre 600 s y 630 s.
- **Predicción P1 (Control positivo):** Armado por el bridge (`source=bridge:/mode`) seguido de 12 minutos de silencio absoluto. Debe caducar automáticamente a `DISABLED` con detalle de origen `engine:autonomous-expiry` entre 600 s y 630 s.
- **Predicción P2 (Control negativo):** Armado por el bridge con heartbeats periódicos cada 2 minutos (120 s) durante 11 minutos. El motor NO debe caducar; debe mantenerse en `AUTONOMOUS` y no generar eventos `engine:autonomous-expiry`.
- **Predicción P3 (Armado por HUD):** Armado desde la interfaz HUD (`source=hud:...`) no debe caducar nunca por inactividad de red/bridge. Se declaró explícitamente en el preregistro que no se probaría en vivo por economía de tiempo, delegándolo al test unitario JVM `AutonomousWatchdogTest`.
- **Predicción P4 (Trade en curso):** Si hay trade abierto al caducar, se corta la exposición desarmando de inmediato sin esperar liquidación.

### 2.2 Coherencia Temporal y Detección de Incoherencias

Comandos de verificación ejecutados:
```bash
stat -c "%y" workflows/PREREGISTRO_R26.md
git log -1 --format="commit %H%nAuthorDate: %ad%nCommitDate: %cd" 75f005e
git log -1 --format="commit %H%nAuthorDate: %ad%nCommitDate: %cd" f491ca4
```
**Salidas observadas:**
```
stat: 2026-10-28 03:00:43.343944002 +0000 (equivalente a 2026-10-27 22:00:43 -05:00)
commit 75f005ee1af5357762ee9d2785e8237482d35d83
AuthorDate: Tue Oct 27 22:26:30 2026 -0500
CommitDate: Tue Oct 27 22:26:30 2026 -0500
```

**Incoherencias temporales identificadas:**
1. **Fijación vs Ejecución Real:** La cabecera de `workflows/PREREGISTRO_R26.md` afirma:
   > `**Fecha de fijación:** 2026-10-27 22:20 (America/Bogota) — escrito **antes** de abrir cualquier ventana de R26`
   Sin embargo, el artefacto `loops/r26_validation/P1_t0.txt` demuestra que la prueba P1 arrancó a las **`2026-10-27T22:01:38-05:00`** (Epoch `1793156498`), y concluyó a las **`22:14:31-05:00`** (`P1_timeline.txt`). Es decir, **P1 se ejecutó y completó antes de la hora declarada de fijación (22:20)**.
2. **Co-compromiso en Git (Preregistro no anticipado en repositorio):** `workflows/PREREGISTRO_R26.md` no existía en ningún commit previo a la ejecución. Fue introducido y commiteado en el commit `75f005e` a las **22:26:30 -05:00**, simultáneamente con los resultados finales de P1, P2 y el informe `R26_REPORTE.md`.
3. **Discrepancia stat vs texto:** La marca temporal del archivo en sistema de archivos (`22:00:43 -05:00`) antecede por ~19 minutos a la hora escrita manualmente en el encabezado del documento (`22:20`).

---

## 3. Auditoría de P1 (Control Positivo: Silencio Total)

### 3.1 Artefactos Inspeccionados
- `P1_t0.txt`:
  ```
  T0_EPOCH=1793156498 T0_LOCAL=2026-10-27T22:01:38-05:00
  ```
- `P1_mode_log.json`:
  ```json
  {"atMs":1793156498172,"kind":"MODE_CHANGE","detail":"de=DISABLED a=AUTONOMOUS source=bridge:/mode origen=TradingEngine.modeChangeOrigin:141<TradeDrawHttpBridge.handleClient:173..."},
  {"atMs":1793157128187,"kind":"MODE_CHANGE","detail":"de=AUTONOMOUS a=DISABLED source=engine:autonomous-expiry origen=TradingEngine.modeChangeOrigin:141<TradingEngine$autonomousWatchdogTick$1.run:223..."}
  ```
- `P1_timeline.txt`:
  ```
  T_FINAL=2026-10-27T22:14:31-05:00
  T_STATUS=2026-10-27T22:14:32-05:00
  ```
- `P1_status_final.json`:
  `mode: "DISABLED"`, `health.state: "BLOCKED"`, `isDemoAccount: true`.

### 3.2 Recálculo del Silencio Real
- **Instante de armado (T0):** `1793156498172` ms (22:01:38.172 -05:00).
- **Instante del evento `engine:autonomous-expiry`:** `1793157128187` ms (22:12:08.187 -05:00).
- **Delta real medido:**
  $$\Delta = 1793157128187 - 1793156498172 = 630015\text{ ms} = 630.015\text{ s} \ (10\text{ min } 30.015\text{ s})$$

### 3.3 Comparación con `R26_REPORTE.md`
- `R26_REPORTE.md` afirma:
  > `Caducidad: 22:12:08 (atMs=1793157128187, delta = 630.015 s = 10 min 30 s). Tope preregistrado: entre 600 s y 630 s ... Exacto en el segundo tick disponible.`
- **Conclusión P1:** El cálculo matemático reportado es **exacto y verificado** al milisegundo contra el log de eventos del motor. El sondeo final (`22:14:31`) se realizó con margen de seguridad de 12 min 53 s para no contaminar el intervalo de prueba.

---

## 4. Auditoría de P2 (Control Negativo: Heartbeat Activo)

### 4.1 Artefactos Inspeccionados
- `P2_t0.txt`: `P2_T0_EPOCH=1793157280 P2_T0_LOCAL=2026-10-27T22:14:40-05:00`
- `P2_beats.txt`:
  ```
  beat_1={"status":"ok","app":"TradeDraw","port":8080} t=1793157403
  beat_2={"status":"ok","app":"TradeDraw","port":8080} t=1793157524
  beat_3={"status":"ok","app":"TradeDraw","port":8080} t=1793157644
  beat_4={"status":"ok","app":"TradeDraw","port":8080} t=1793157764
  beat_5={"status":"ok","app":"TradeDraw","port":8080} t=1793157884
  beat_6={"status":"ok","app":"TradeDraw","port":8080} t=1793157945
  T_FINAL=2026-10-27T22:25:45-05:00
  ```
- `P2_status_final.json`: `"mode": "AUTONOMOUS"`, `"submode": "CONSERVATIVE"`.
- `P2_mode_log.json`: Muestra únicamente 4 eventos acumulados en el ciclo de vida del proceso; el último es el armado de P2 a las `1793157280752`.

### 4.2 Cadencia Real de Heartbeat
- Beat 1: $1793157403 - 1793157280 = 123\text{ s}$
- Beat 2: $1793157524 - 1793157403 = 121\text{ s}$
- Beat 3: $1793157644 - 1793157524 = 120\text{ s}$
- Beat 4: $1793157764 - 1793157644 = 120\text{ s}$
- Beat 5: $1793157884 - 1793157764 = 120\text{ s}$
- Beat 6: $1793157945 - 1793157884 = 61\text{ s}$
- **Duración total P2:** $1793157945 - 1793157280 = 665\text{ s} = 11\text{ min } 05\text{ s}$.

### 4.3 Modo Final y Ausencia de Caducidad
- **Modo en `P2_status_final.json`:** `"mode": "AUTONOMOUS"` constante.
- **Eventos `engine:autonomous-expiry` en `P2_mode_log.json` durante P2:** **0 ocurrencias**.
- **Conclusión P2:** Los pings en intervalos entre 61 s y 123 s mantuvieron reseteado el watchdog, confirmando que la caducidad no se basa en tiempo transcurrido desde el armado, sino en inactividad de contacto.

---

## 5. Auditoría de `P3_cierre.json` (Modo y Estado de Riesgo)

Inspección de las claves de estado en `loops/r26_validation/P3_cierre.json`:
- **Modo final en raíz:** `"mode": "DISABLED"`.
- **Origen de cambio:** `"lastModeChangeSource": "AUTONOMOUS -> DISABLED | source=bridge:/mode"` a las `1793157956972`.
- **Claves correctas de riesgo (bajo objeto `risk`):**
  - `risk.orderState`: `"NINGUNA"`
  - `risk.hasPendingTrade`: `false`
  - `risk.hasConfirmedTrade`: `false`
  - `risk.remainingCooldown`: `0`
  - `risk.tapsDespachados`: `0`
  - `risk.tradesLossSinEvidencia`: `0`
  - `risk.manualWins`: `0`
  - `risk.manualLosses`: `0`

---

## 6. Contradicciones entre `R26_REPORTE.md` y los Artefactos

1. **Ubicación y valor de `orderState` y `hasPendingTrade`:**
   - **En `R26_REPORTE.md`:** Se describe reiteradamente (líneas 134 y 154) como:
     `orderState=null, hasPendingTrade=null`.
   - **En artefactos JSON crudos (`P1_status_final.json`, `P3_cierre.json`):**
     En la raíz del JSON estas claves no existen (evalúan como `undefined` o `null` si se busca en raíz). Sin embargo, bajo la clave correcta `risk`:
     `risk.orderState` es el string literal `"NINGUNA"`, y `risk.hasPendingTrade` es el booleano `false`.
     *Causa:* Quien redactó el informe consultó las propiedades en la raíz en lugar de `status.risk`.
2. **Telemetría de batería al cierre:**
   - **En `R26_REPORTE.md`:** Se afirma: `Batería: 56 % al abrir, 52 % al cerrar`.
   - **En artefactos crudos:** El archivo `device_metadata_raw.txt` únicamente contiene el snapshot de apertura (`level: 56`). No existe ningún volcado crudo (`device_metadata_close.txt` o similar) que respalde documentalmente el dato de 52% al cierre.
3. **PID del proceso Android (16195):**
   - **En `R26_REPORTE.md`:** Se afirma: `El PID de la app fue 16195 al arrancar y 16195 al cerrar: el servicio no se reinició`.
   - **En artefactos crudos:** El endpoint `/status` no expone el PID del proceso de Android ni se guardó un log de `ps` crudo en `loops/r26_validation/`. Aunque la persistencia del proceso se infiere técnicamente por `buildTimestamp` (`1793156419020`) y `sinceMs` (`1793156469719`), el número literal `16195` es una aserción sin artefacto directo en la carpeta.
4. **Taps Despachados:**
   - El reporte afirma 0 taps despachados. Coincide exactamente con `risk.tapsDespachados: 0` en todos los archivos JSON (`P0`, `P1`, `P2`, `P3`).

---

## 7. Declaración de Límites en el Informe

En `loops/r26_validation/R26_REPORTE.md` (§6) se declaran explícita y transparentemente los siguientes límites metodológicos:
1. **P3 no probado en vivo en el dispositivo físico:** Declarado honestamente. El caso de que el armado por HUD nunca debe caducar fue cubierto únicamente en test unitario JVM (`AutonomousWatchdogTest.armadoDesdeElHudNoCaducaNunca`), justificándose por la necesidad de evitar otros 12 min de pantalla encendida y porque el riesgo de exposición provenía exclusivamente del bridge.
2. **Criterio V5 permanece NO EVALUABLE:** Declarado honestamente. Se reconoce que R26 no tocó la estrategia, no acumuló trades live ni reevaluó la paridad live vs replay. El estado del cubo R25 permanece con V1–V4 PASS y V5 NO EVALUABLE por muestra insuficiente.

---

## 8. Veredicto de la Auditoría C

- **Integridad del código:** CONFIRMADA. Los commits `f97abfa`, `f491ca4` y `75f005e` implementan e instrumentan exactamente lo descrito sin residuos ni regresiones.
- **Comportamiento del mecanismo:** CONFIRMADO. P1 caducó a los 630.015 s en silencio total; P2 se mantuvo activo durante 665 s con heartbeats.
- **Incoherencias de proceso detectadas:** Preregistro redactado/fechado con hora posterior (22:20) al inicio real de P1 (22:01:38) y commiteado conjuntamente con los resultados (`75f005e`).
- **Incoherencias de reporte detectadas:** Notación `orderState=null, hasPendingTrade=null` en vez de `risk.orderState="NINGUNA"` y `risk.hasPendingTrade=false`, y ausencia de captura cruda para el 52% de batería.
