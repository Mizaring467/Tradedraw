# AUDITORÍA TÉCNICA DE PREFLIGHT — R23H / WINDOW_05_CLEAN
## R23H_CLAUDE_PREFLIGHT_W05

**Fecha:** 2026-10-18  
**Auditor Principal:** CLAUDE CODE (`w1:pC`)  
**Destinatario:** AGY (`w1:p1` / Orquestador)  
**Documento de Referencia:** `workflows/rounds/r23h_phase1_preflight_claude.md`  

---

## VEREDICTO FORMAL: **`PREFLIGHT = READY`**

El entorno, el dispositivo físico de ejecución (Xiaomi Redmi A1), los arneses de telemetría y el arnés de Replay cumplen estrictamente los 6 puntos de certificación previa. Se autoriza el inicio y ejecución de la ventana final `window_05_clean`.

---

## AUDITORÍA DETALLADA DE LAS 6 CONDICIONES DE PREFLIGHT

### 1. Git SHA y Build Instalado
- **Evidencia en Runtime del Dispositivo:**
  - `app`: `TradeDraw`
  - `versionCode`: `19`
  - `versionName`: `"1.9-r23h-exhaustion"`
  - `buildId`: `"R23H-BUY-EXHAUSTION"`
  - `gitSha`: `"aac1df3"`
  - `target`: Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)
- **Evidencia en Código Fuente (`app/build.gradle.kts`):**
  - Línea 14: `versionCode = 19`
  - Línea 15: `versionName = "1.9-r23h-exhaustion"`
  - Línea 29: `buildConfigField("String", "GIT_SHA", "\"$gitSha\"")` (resuelve a `aac1df3`)
  - Línea 30: `buildConfigField("String", "BUILD_ID", "\"R23H-BUY-EXHAUSTION\"")`
- **Trazabilidad:** El binario APK desplegado en el dispositivo físico coincide al 100% con el commit base auditado `aac1df3`.
- **Dictamen:** **CONFIRMADO / READY**

---

### 2. Paridad de Especificación Live ↔ Replay
Live y Replay consumen exactamente las mismas definiciones matemáticas y operativas:
- **RIC:** `Z-CRY/IDX` (activo visible nominal "Crypto IDX"). Mapeo determinista vía `AssetRicMapper.ricParaActivoVisible` en Live (`TradingEngine.kt:851`) y Replay (`ReplayBacktestTest.kt:324`).
- **Dataset:** `ticks_live.csv` con poda de borde activa `ts >= window_start_ts_ms` (`live_monitor_r23_autonomous.py:957-974`). Cero contaminación con ticks pre-ventana.
- **Submodo:** `YOLO` en Live y `BACKTEST_SUBMODE=YOLO` inyectado automáticamente en Replay (`live_monitor_r23_autonomous.py:1018` y `ReplayBacktestTest.kt:159`).
- **Políticas Defensivas:** Replicación activa de `motivoVetoPorAgotamientoDeCompra` en `ReplayBacktestTest.kt:209` antes de simular el trade, liberando la vela sin marcarla como operada al igual que Live.
- **Fórmula Compartida:** Invocación a la función pura internal `calcularPoderesDeMercado()` (`TradingEngine.kt:2817`) por ambas vías.
- **Veto:** `VETO_BUY_EXHAUSTION_CLIMAX` con umbral `callPower >= 70%` en `UPTREND` (`TradingEngine.kt:2875-2884`).
- **Cooldown:** Idéntico a `8,000 ms` (`TradingEngine.kt:2288` e `ReplayBacktestTest.kt:473`).
- **Tolerancia Temporal:** `temporal_tolerance_ms = 6000` ms en `compare_live_replay.py:320` y `--temporal-tolerance-ms default=6000` en CLI (línea 1043) en `--mode trades`.
- **Dictamen:** **CONFIRMADO / READY**

---

### 3. Aislamiento de Directorio `window_05_clean`
- **Inspección en Disco:**
  - El directorio `loops/r23h_validation/window_05_clean/` **no existe** previamente en el sistema de archivos (0 archivos, 0 residuos).
  - Al iniciar la ventana, `live_monitor_r23_autonomous.py:325` creará la ruta limpia `loops/r23h_validation/window_05_clean/`.
  - Todas las estructuras de captura en memoria (`observed_decisions`, `observed_trades`, `accumulated_ticks`, `seen_trade_ids`) se instancian vacías en $t_0$.
- **Dictamen:** **CONFIRMADO / READY**

---

### 4. Salvaguarda DEMO
- **Verificación de Endpoint:**
  - En el endpoint `/status` del dispositivo, `isDemoAccount == true` en el 100% de las lecturas.
- **Mecanismo Fail-Closed:**
  - `live_monitor_r23_autonomous.py:560-574`: Si en cualquier muestra periódica se observa `isDemoAccount == false` o ausencia de confirmación DEMO, el monitor ejecuta de inmediato `emergency_shutdown(base_url)` (`POST /mode?mode=DISABLED`) e interrumpe la ventana.
  - Cero exposición de fondos reales.
- **Dictamen:** **CONFIRMADO / READY**

---

### 5. Ausencia de Estado Heredado
- **Independencia de Ventanas Previas:**
  - Las corridas anteriores (`window_01`, `window_02`, `window_03`, `window_04_clean`) residen estrictamente aisladas en sus propios subdirectorios.
  - La nueva ventana no arrastra balances, contadores de wins/losses, taps acumulados ni ticks de ventanas precedentes.
  - La línea base de balance (`initial_balance`) y de taps (`initial_taps`) se inicializa exclusivamente a partir de la muestra 1 capturada en $t_0$ de la nueva ventana.
- **Dictamen:** **CONFIRMADO / READY**

---

### 6. Estrategia Estrictamente Congelada
- **Inspección de Modificaciones Locales en Working Tree:**
  - Las modificaciones en el árbol de trabajo local corresponden con exclusividad a infraestructura de paridad y trazabilidad:
    1. `TradingEngine.kt`: Extracción de `calcularPoderesDeMercado` como función pura interna (idénticas constantes `68/32`, `32/68`, `coerceIn(15,95)`). Blindado por `LiveReplayParityTraceabilityTest.test6_laExtraccionNoAlteroNingunValorDeLaFormula`.
    2. `ReplayBacktestTest.kt`: Replicación del veto de agotamiento de compra en el callback de señal.
    3. `LiveReplayParityTraceabilityTest.kt`: Suite de 6 tests de trazabilidad y paridad determinista.
    4. `scripts/validation/live_monitor_r23_autonomous.py`: Poda de borde en volcado de ticks.
    5. `scripts/validation/compare_live_replay.py`: Tolerancia temporal ajustada a 6000 ms para absorber latencia de Android Go.
  - **Cero cambios** en señales BUY/SELL, thresholds de confluencia (80 pts), matrices de ponderación, filtros anti-choppiness o parámetros de gestión de riesgo (`RiskManager.kt`).
- **Dictamen:** **CONFIRMADO / READY**

---

## TABLA RESUMEN DE COMPROBACIÓN PREVIA

| # | Condición Requerida | Estado | Evidencia Principal |
|---|---|:---:|---|
| 1 | Git SHA y Build Instalado | **READY** | Build `aac1df3` / `versionCode=19` / `buildId=R23H-BUY-EXHAUSTION` en `/status` |
| 2 | Paridad Live ↔ Replay | **READY** | RIC `Z-CRY/IDX`, fórmula pura compartida, veto replicado, YOLO 8s, tol 6000ms |
| 3 | Aislamiento de Directorio | **READY** | `window_05_clean/` inexistente en disco; parte de cero absoluto |
| 4 | Salvaguarda DEMO | **READY** | `isDemoAccount == true` en runtime; fail-closed `emergency_shutdown` activo |
| 5 | Ausencia de Estado Heredado | **READY** | Inicialización limpia en $t_0$; sin arrastre de W01/W02/W03/W04 |
| 6 | Estrategia Congelada | **READY** | Cambios exclusivamente de infraestructura; cero cambios a reglas cuantitativas |

---

## DICTAMEN FINAL

### **`PREFLIGHT = READY`**

Se autoriza a AGY y OpenCode el lanzamiento operativo de la ventana `window_05_clean`.
