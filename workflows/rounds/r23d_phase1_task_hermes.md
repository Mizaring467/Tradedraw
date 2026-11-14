# TAREA HERMES: FASE R23D — CORRECCIÓN DE TELEMETRÍA, TOOLING Y DESPLIEGUE (BUILD VC=16)

**Fecha:** 2026-10-16  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** HERMES (`w1:p4` / Implementador)  
**Rama:** `r10-night`  
**Base git:** `d80ec07`  

---

## 1. OBJETIVO GENERAL
Subsanar con precisión quirúrgica los 4 defectos de telemetría y medición identificados al cierre de R23C Fase 3, compilar y verificar el APK con bump técnico a `versionCode = 16`, e instalarlo en el Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`).

**REGLA DE ESTRATEGIA:** La lógica de señales y filtros en `SyntheticCandleEngine.kt` permanece **ESTRICTAMENTE CONGELADA E INTACTA** (SELL endurecido con confluencia $\ge 80$, mechas $45\%/40\%$, BUY en $75/30\%/30\%$).

---

## 2. ESPECIFICACIÓN DETALLADA DE CAMBIOS

### REQ 1: Settlement Grace Period en Monitor Live (`scripts/validation/live_monitor_r23_autonomous.py`)
- **Problema:** En W03 un trade ejecutado en el último minuto resolvió post-cierre de ventana, dejando contadores en W=0/L=0 y obligando a conciliar manualmente.
- **Implementación:**
  - Al alcanzarse el tiempo nominal (`now_ts >= end_time`):
    - Comprobar si hay trade no resuelto:
      - `cur_order_state != "NINGUNA"`
      - O `status.get("risk", {}).get("hasPendingTrade", False) == True`
      - O si los trades confirmados en la ventana superan los eventos resueltos (`len(observed_trades) > (cur_wins - initial_wins + cur_losses - initial_losses)`).
    - Si se cumple la condición:
      - Entrar en bucle `SETTLEMENT_GRACE_PERIOD` con timeout de hasta **75 segundos**.
      - Imprimir en consola: `[SETTLEMENT GRACE] Esperando resolución de trade en curso (máx 75s)...`.
      - Mantener el muestreo normal (`/status`, `/events`, `/ticks`) cada `args.interval_sec` hasta que `cur_order_state == "NINGUNA"` Y no haya pending trade (o expire el timeout).
  - Almacenar en la metadata y reporte:
    - `nominal_window_end_ts`
    - `actual_capture_end_ts`
    - `settlement_extra_seconds`

### REQ 2: Filtro de Outliers en Balance y Drawdown Limpio (`scripts/validation/live_monitor_r23_autonomous.py`)
- **Problema:** En W03 una muestra aislada reportó `1.000.003,00` por un glitch momentáneo de telemetría, falseando un drawdown de -97.4%.
- **Implementación:**
  - Mantener un rastreo del balance validado (`validated_balance`):
    - Si una lectura de balance `cur_bal` diverge del último balance válido por más de **1.000.000 COP** (o $> 10 \times \text{stake}$) sin mediar un trade confirmado en esa misma muestra:
      - Etiquetar la muestra con `"balance_quality": "OUTLIER"`.
      - Mantener `validated_balance = last_valid_balance`.
      - Registrar warning en consola: `[TELEMETRY WARNING] Outlier de balance detectado: {cur_bal:,.2f} -> ignorado en drawdown`.
    - En lecturas normales:
      - Etiquetar `"balance_quality": "VALID"`.
      - `validated_balance = cur_bal`.
  - Guardar en `samples.jsonl`: `raw_balance`, `validated_balance`, `balance_quality`.
  - Calcular el drawdown y balance final sobre la serie de `validated_balance`.
  - En el reporte formal, incluir conteo de outliers filtrados si ocurrieran.

### REQ 3: Sincronización de Cooldown en Android (`RiskManager.kt`, `TradingEngine.kt`, `HealthSupervisor.kt`, `TradeDrawHttpBridge.kt`)
- **Problema:** `RiskManager.currentSubMode` tiene por defecto `CONSERVATIVE` y nunca se actualizaba al cambiar a `YOLO`. Como `HealthSupervisor.kt:105` y `TradeDrawHttpBridge.kt:624` llaman a `getRemainingCooldown()` sin argumentos, calculaban con el cooldown de pérdidas de CONSERVATIVE (180s) en vez de YOLO (35s), publicando `COOLDOWN_ACTIVE: 178s` espuriamente.
- **Implementación:**
  1. En `TradingEngine.kt`:
     - En el setter o punto de cambio de `autonomousSubMode`: actualizar siempre `riskManager.currentSubMode = autonomousSubMode`.
  2. En `TradeDrawHttpBridge.kt`:
     - Al procesar `POST /submode`:
       ```kotlin
       val sm = AutonomousSubMode.valueOf(subStr)
       overlayService.tradingEngine.autonomousSubMode = sm
       overlayService.riskManager.currentSubMode = sm
       ```
     - En endpoint `/status` (línea 624):
       ```kotlin
       "cooldownRemainingSec": ${rm.getRemainingCooldown(te.autonomousSubMode)}
       ```
  3. En `HealthSupervisor.kt` (línea 105):
     - Usar `riskManager.currentSubMode` (o método equivalente) para que consulte el submodo efectivo:
       ```kotlin
       val cdSec = riskManager?.getRemainingCooldown(riskManager.currentSubMode) ?: 0
       if (cdSec > 0) {
           degradedReasons.add("COOLDOWN_ACTIVE: ${cdSec}s")
       }
       ```
  4. En `app/build.gradle.kts`:
     - `versionCode = 16`
     - `versionName = "1.6-r23d-telemetry"`
     - `buildConfigField("String", "BUILD_ID", "\"R23D-TELEMETRY\"")`

### REQ 4: Extracción de Score y Factores de Confluencia en Monitor Live (`scripts/validation/live_monitor_r23_autonomous.py`)
- **Problema:** Línea 278 fija `"score": 85.0` hardcodeado en `observed_decisions`.
- **Implementación:**
  - Extraer mediante regex sobre `detail` el puntaje real:
    - Patrón: `r"\[Confluencia:\s*(\d+(?:\.\d+)?)pts\]"` o `r"Confluencia.*?(\d+)%"`.
    - Si existe coincidencia: asignar dicho valor numérico (e.g. 75.0, 80.0, 85.0).
    - Si no existe: asignar `None` o `0.0`.
  - Asegurar que `decisions.csv` refleje el score real.

---

## 3. TESTS Y VERIFICACIÓN OBLIGATORIA
1. **Test Unitario en Kotlin:**
   - Crear un test en `RiskManagerTest.kt` (o archivo dedicado) que verifique:
     - Con `currentLossStreak = 1` y submodo `YOLO`: `getRemainingCooldown()` o `getRemainingCooldown(YOLO)` debe basarse en 35s.
     - Con `currentLossStreak = 1` y submodo `CONSERVATIVE`: debe basarse en 180s.
     - Cambiar `TradingEngine.autonomousSubMode = YOLO` actualiza coherentemente `riskManager.currentSubMode`.
2. **Ejecución de Suite Completa:**
   - `./gradlew.bat testDebugUnitTest --console=plain`
   - Los 434 tests anteriores + nuevos tests deben pasar con **0 fallos**.
3. **Compilación e Instalación:**
   - `./gradlew.bat assembleDebug`
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **Verificación en Runtime:**
   - Abrir TradeDraw y Binomo en Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`).
   - Consultar `http://localhost:8080/status` vía ADB forward (`adb forward tcp:8080 tcp:8080`).
   - Verificar: `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`, `isDemoAccount = true`.
   - Probar cambiar submodo a `YOLO` vía `POST /submode?name=YOLO` y verificar que `/status` retorne `"submode": "YOLO"`.
   - Dejar submodo en `CONSERVATIVE` y modo `DISABLED` para entrega limpia a OpenCode.

---

## 4. ENTREGABLES
- Commit en rama `r10-night` con todos los cambios y tests.
- Reporte de implementación en `workflows/rounds/r23d_phase1_hermes.md`.
- Confirmación de APK instalado y verificado en runtime.
