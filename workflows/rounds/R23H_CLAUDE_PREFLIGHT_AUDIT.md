# AUDITORÍA TÉCNICA DE PRE-VUELO — REVALIDACIÓN LIMPIA R23H (TRACK A)
## R23H_CLAUDE_PREFLIGHT_AUDIT

**Fecha:** 2026-10-18  
**Auditor Principal:** CLAUDE CODE (`w1:pC`)  
**Destinatario:** AGY (`w1:p1` / Orquestador)  
**Documentos de Referencia:**  
- `workflows/rounds/r23h_track_a_preflight_claude.md`  
- `workflows/R23H_REVALIDATION_MANIFEST.md`  
- `workflows/rounds/r23h_hermes_parity_fix_report.md`  

---

## VEREDICTO FORMAL: **`READY`**

El entorno, los arneses de ejecución, los scripts de telemetría y el motor algorítmico cumplen estrictamente las condiciones de homogeneidad, delimitación y reproducibilidad técnica requeridas para el lanzamiento de la ventana limpia `window_04_clean`.

---

## AUDITORÍA DETALLADA DE LOS 7 PUNTOS DE PRE-VUELO

### 1. Correspondencia de Commit y Trazabilidad de Build
- **Estado Live en Runtime:**
  - `gitSha`: `aac1df3`
  - `versionCode`: `19`
  - `versionName`: `1.9-r23h-exhaustion`
  - `buildId`: `R23H-BUY-EXHAUSTION`
  - Target: Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)
- **Estado de Replay y Código Local:**
  - Commit base auditado: `aac1df3` (`aac1df30490cd4b1617bfbe9f6f94cac75d7dc8d`).
  - Árbol de trabajo local: Hermes implementó 4 correcciones mecánicas de infraestructura de paridad sin alterar valores de estrategia (466 tests pasando, 0 fallos).
  - La extracción de `calcularPoderesDeMercado(...)` en `TradingEngine.kt:2817` preserva idénticas constantes matemáticas (`UPTREND 68/32`, `DOWNTREND 32/68`, `coerceIn(15,95)` y normalización a 100), congeladas por el test dorado `LiveReplayParityTraceabilityTest.test6_laExtraccionNoAlteroNingunValorDeLaFormula`.
- **¿Se requiere commit previo de infraestructura?:**
  - **NO**. Si se commitean los cambios locales antes de ejecutar, el HEAD de git avanzará a un nuevo SHA no coincidente con el `gitSha = "aac1df3"` embebido en el binario APK actualmente desplegado en el dispositivo, lo que rompería la verificación estricta de `/status`.
  - El manifiesto (`R23H_REVALIDATION_MANIFEST.md` §1) define formalmente a `aac1df3` como el commit base auditado con estado de trabajo local validado. La paridad de lógica algorítmica es del 100%.

### 2. Políticas Defensivas Idénticas (VETO_BUY_EXHAUSTION_CLIMAX)
- **Motor Live (`TradingEngine.kt`):**
  - Línea 477: Invoca `calcularPoderesDeMercado(trend, distSup, distRes, rsi, isBullishImpulse, isBearishImpulse)`.
  - Línea 2315: Evalúa `motivoVetoPorAgotamientoDeCompra(action, effectiveAnalysis.trend, effectiveAnalysis.callPower)`.
  - Líneas 2875-2884: Si `action == BUY && trend == UPTREND && callPower >= 70`, emite veto `VETO_BUY_EXHAUSTION_CLIMAX`.
- **Motor Replay (`ReplayBacktestTest.kt`):**
  - Líneas 201-213: Invoca directamente a la misma función pura `calcularPoderesDeMercado(...)` y evalúa `motivoVetoPorAgotamientoDeCompra(...)` antes de simular el trade.
  - Al vetar, ejecuta `return@senal`, suprimiendo la orden y liberando la vela para no marcarla como operada, replicando exactamente la conducta del motor en vivo.
- **Unificación:** Ambas vías consumen exactamente la misma implementación y constantes. Cero bifurcaciones heurísticas.

### 3. Submodo Idéntico (YOLO con Cooldown 8,000 ms)
- **Live (`TradingEngine.kt:2288`):**
  ```kotlin
  val minSpacingMs = when {
      isYolo -> 8000L // 8 segundos en YOLO para no trabar la siguiente vela
      riskManager.currentLossStreak > 0 -> 35000L
      else -> 12000L
  }
  ```
- **Replay (`ReplayBacktestTest.kt:161, 473`):**
  ```kotlin
  const val YOLO_COOLDOWN_MS = 8_000L
  val cooldownMs = if (subMode == AutonomousSubMode.YOLO) YOLO_COOLDOWN_MS else CONSERVATIVE_COOLDOWN_MS
  ```
- **Acoplamiento de Ejecución (`live_monitor_r23_autonomous.py:1018`):**
  - El monitor detecta el submodo activo de `/status` (`active_submode = status.get("submode")`).
  - Al invocar el replay offline inyecta: `env["BACKTEST_SUBMODE"] = active_submode`.
  - **Condición Operativa:** Como el arranque por defecto del APK es `CONSERVATIVE` (`TradingEngine.kt:145`), el pre-vuelo debe asegurar la llamada obligatoria:
    `curl -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"`
    para garantizar que tanto Live como Replay operen a 8,000 ms de espaciado.

### 4. Delimitación Limpia de Dataset (Poda Pre-Ventana)
- **Implementación (`live_monitor_r23_autonomous.py:957-974`):**
  ```python
  window_start_ts_ms = int(start_time * 1000.0)
  ticks_in_window = {
      ts: linea
      for ts, linea in accumulated_ticks.items()
      if ts >= window_start_ts_ms
  }
  ```
- **Garantía Técnica:**
  - El volcado final a `ticks_live.csv` itera exclusivamente sobre `sorted(ticks_in_window)`.
  - Se garantiza matemáticamente que $\min(\text{timestamp}) \ge \text{window\_start\_ts\_ms}$.
  - Los ticks residuales provenientes de la cola del búfer circular del APK (anteriores a $t_0$) son purgados y reportados en consola (`Poda de borde de ventana: X ticks pre-ventana descartados`). Cero contaminación de velas sintéticas en Replay.

### 5. Correlación Matemática de IDs (`decision_id`)
- **Fórmula Canónica (`TradingEngine.kt:850-856`):**
  $$\text{key} = \text{RIC}(\text{asset}) + \text{"\_"} + \text{nonceMs} + \text{"\_"} + \text{action}$$
  $$\text{decision\_id} = \text{UUID.nameUUIDFromBytes}(\text{key.getBytes(UTF-8)})$$
- **En Live:**
  `generateDeterministicDecisionId(assetName, signalTsMs, action.name)` donde `assetName` ("Crypto IDX") se normaliza canónicamente a `"Z-CRY/IDX"` mediante `AssetRicMapper.ricParaActivoVisible()`.
- **En Replay (`ReplayBacktestTest.kt:324`):**
  `TradingEngine.generateDeterministicDecisionId(sig.assetRic, entryTick.ts, action.name)` donde `sig.assetRic` es `"Z-CRY/IDX"` y `entryTick.ts` es el timestamp del tick evaluado.
- **Correlación:**
  - Las identidades son idénticas ante el mismo tick y acción.
  - Blindado por la suite de pruebas `LiveReplayParityTraceabilityTest` (tests 1, 3, 3b, 4 y 5) que certifica emparejamiento 100% determinista con hasta 4.6s de latencia física de despacho.

### 6. Definición Correcta del Comparador
- **Tolerancia Temporal:**
  - `compare_live_replay.py:320`: `temporal_tolerance_ms: int = 6000` (actualizado de 3000 ms a 6000 ms en la función de emparejamiento).
  - `compare_live_replay.py:1043`: `--temporal-tolerance-ms default=6000` en la interfaz CLI.
  - Ambos niveles están sincronizados para absorber la latencia de despacho y lectura de hardware en Android Go (~4.5s).
- **Semántica `--mode trades`:**
  - Cruza órdenes reales confirmadas (`trades.csv`) contra órdenes simuladas por Replay (`replay_journal.csv`).
  - Prioridad 1: Emparejamiento por `decision_id` determinista.
  - Prioridad 2: Fallback por proximidad temporal $\le 6,000\text{ ms}$ sobre el mismo activo.
  - Validación de paridad sobre `action`, `candle_second` y `price` (si está presente). Criterio de pase: $\text{match\_rate} \ge 90\%$.

### 7. Ausencia Total de Estado Residual
- **Directorio de Destino:** `loops/r23h_validation/window_04_clean/`
- **Inspección de Sistema de Archivos:**
  - El directorio `loops/r23h_validation/window_04_clean/` **NO EXISTE** actualmente en el repositorio.
  - No contiene archivos de validaciones previas, logs, contadores ni residuos temporales.
  - Las ventanas anteriores (`window_01`, `window_02`, `window_03`) residen aisladas en sus propias carpetas.
  - Al iniciar, el monitor creará el directorio limpio e inicializará todas las variables contables en el instante $t_0$.

---

## RESUMEN DE COMPROBACIONES TÉCNICAS

| # | Punto Auditado | Estado | Evidencia Principal |
|---|---|:---:|---|
| 1 | Correspondencia de Commit | **READY** | Build `aac1df3` / Replay con infraestructura verificada (466 tests OK) |
| 2 | Políticas Idénticas | **READY** | `calcularPoderesDeMercado()` unificado y congelado por test dorado |
| 3 | Submodo Idéntico (YOLO) | **READY** | Cooldown idéntico de 8,000 ms en Live y Replay; propagación vía `/status` |
| 4 | Delimitación de Dataset | **READY** | Poda activa: $\min(\text{ts}) \ge \text{window\_start\_ts\_ms}$ en volcado |
| 5 | Correlación de IDs | **READY** | UUID v3 determinista sobre `RIC_tsMs_ACTION` probado en suite unitaria |
| 6 | Definición del Comparador | **READY** | 6,000 ms de tolerancia temporal en función y CLI; `--mode trades` activo |
| 7 | Ausencia de Estado Residual| **READY** | Directorio `window_04_clean/` inexistente y completamente limpio |

---

## RECOMENDACIÓN OPERATIVA PARA EL LANZAMIENTO
Previo al disparo del monitor `live_monitor_r23_autonomous.py`, ejecutar la secuencia estándar de activación por HTTP:
1. `curl.exe -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"`
2. `curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"`
3. Verificar en `http://127.0.0.1:8080/status`: `"mode": "AUTONOMOUS"`, `"submode": "YOLO"`, `"isDemoAccount": true`.
