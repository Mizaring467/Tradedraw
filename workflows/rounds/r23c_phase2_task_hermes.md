# R23C — FASE 2: ESPECIFICACIÓN TÉCNICA DE PARIDAD LIVE ↔ REPLAY

**Destinatario:** HERMES (`w1:p6`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C — Fase 2 (Paridad Live-Replay Offline)  
**Objetivo:** Lograr paridad medible ($\ge 30$ decisiones/trades pareados con $\ge 90\%$ exact match rate) entre la operativa real registrada en vivo y el motor de replay offline, eliminando el 0% de cobertura histórico sin modificar la APK ni la estrategia en el teléfono.

---

## 1. REGLA INMUTABLE DE AISLAMIENTO
- **PROHIBIDO TOCAR EL TELÉFONO O LA APK:** El dispositivo Redmi A1 está ejecutando la validación estadística de YOLO con la build congelada `ac079fd`.
- **ÁMBITO EXCLUSIVO DE CAMBIOS:**
  1. `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`
  2. `scripts/validation/compare_live_replay.py`
  3. `scripts/validation/live_monitor_r23_autonomous.py` (solo la invocación del test de replay/comparador, sin alterar la recolección live)
  4. Tests unitarios de verificación (`./gradlew.bat testDebugUnitTest`)

---

## 2. DIAGNÓSTICO DE LAS CAUSAS RAÍZ (GAP ANALYSIS)

Consulta detallada en: `workflows/R23C_REPLAY_GAP_ANALYSIS.md`.

1. **Disparidad de Universos (Decisiones vs Trades):**
   - Live genera ~60-70 filas en `decisions.csv` (la gran mayoría son vetos como `ADAPTIVE_BLOCK`, `COOLDOWN`, `OPERACION_EN_CURSO`), y solo 4-6 filas en `trades.csv`.
   - Replay en `ReplayBacktestTest.kt` únicamente genera `replay_journal.csv` con los trades simulados (2 filas).
   - `compare_live_replay.py` intenta cruzar `decisions.csv` (63 filas) contra `replay_journal.csv` (2 filas), resultando en 61 `missing_replay` y 0% de paridad.
2. **Submodo Hardcoded CONSERVATIVE en Replay:**
   - En `ReplayBacktestTest.kt:151`, `subMode` está fijado por defecto a `CONSERVATIVE`.
   - En vivo se ejecuta en `YOLO` (confluencia 75 pts, mecha 30%, choppiness relajado). Replay suprime casi todas las señales en CONSERVATIVE.
3. **Ausencia de Guards de Runtime en Replay:**
   - Replay no modela la duración de la operación abierta (60s), disparando trades superpuestos si ocurren en minutos distintos, o ignorando el cooldown post-trade (8s en YOLO).
   - Replay no verifica la ventana sniper (:00-:05 o :58-:59) que en vivo filtra cualquier señal fuera de tiempo.
4. **Desfase de Timestamp en `decision_id`:**
   - Si el tick evaluado por live no coincide en milisegundo exacto con el tick de replay tras deduplicación, el hash UUID difiere.

---

## 3. REQUERIMIENTOS DE IMPLEMENTACIÓN (FILOSOFÍA PONYTAIL - CÓDIGO MÍNIMO)

### REQ 1: Soporte de Submodo Configurable en `ReplayBacktestTest.kt`
- Leer variable de entorno `BACKTEST_SUBMODE` (y system property):
  ```kotlin
  val subModeEnv = System.getenv("BACKTEST_SUBMODE") ?: System.getProperty("BACKTEST_SUBMODE") ?: "CONSERVATIVE"
  val subMode = try {
      AutonomousSubMode.valueOf(subModeEnv.trim().uppercase())
  } catch (_: Exception) {
      AutonomousSubMode.CONSERVATIVE
  }
  ```
- Pasar `subMode` a `runBacktestOnStrategy(...)` en `testReplayBacktestAutoAdaptive()`.

### REQ 2: Modelar Guards Críticos de Entrada en `ReplayBacktestTest.kt`
En el bucle de procesamiento de `runBacktestOnStrategy`:
1. **Ventana Sniper:**
   ```kotlin
   val candleSec = ((lastTick.ts / 1000L) % 60L).toInt()
   val inSniperWindow = candleSec in 0..5 || candleSec in 58..59
   if (!inSniperWindow) return@onSignalGenerated // Descartar señal fuera de ventana
   ```
2. **Operación Abierta (Sin solapes de 60s):**
   - Una operación dura 60.000 ms. Mientras `lastTick.ts < currentTradeExpiryTs`, no se puede emitir otra operación (`OPERACION_EN_CURSO`).
3. **Cooldown Post-Trade:**
   - Al expirar la operación, aplicar cooldown según el submodo:
     - `YOLO`: 8.000 ms (o 35.000 ms si hubo racha).
     - `CONSERVATIVE`: 35.000 ms / 180.000 ms.
   - `nextAvailableTradeTs = currentTradeExpiryTs + cooldownMs`.

### REQ 3: Soporte de Emparejamiento por Trades en `compare_live_replay.py`
- Agregar opción `--mode {trades,decisions}` (default: `trades` cuando la fuente live es `trades.csv` o contiene eventos confirmados).
- En `--mode trades`:
  - Emparejar trades de Live (`trades.csv`) contra `replay_journal.csv`.
  - Criterio de emparejamiento:
    - Primero por `decision_id` exacto.
    - Fallback: por cercanía temporal de entrada ($\pm 3.000\text{ ms}$) sobre el mismo activo (`asset`).
  - Comparar campos esenciales: `action` (BUY/SELL), `candle_second`, `price` (dentro de tolerancia), `result` (WIN/LOSS).

### REQ 4: Inyección en `live_monitor_r23_autonomous.py`
- Al ejecutar la sección de verificación replay offline post-ventana:
  - Inyectar `$env:BACKTEST_SUBMODE = "YOLO"` (o el submodo activo obtenido de `/status`).
  - Invocar `compare_live_replay.py --mode trades --live .../trades.csv --replay .../replay_journal.csv`.

---

## 4. VERIFICACIÓN Y CRITERIOS DE ACEPTACIÓN
1. **Tests Unitarios:**
   - `./gradlew.bat testDebugUnitTest` debe compilar limpiamente y pasar todos los tests (426+ tests, 0 fallos).
2. **Prueba Offline con Datos de Ventana Real:**
   - Ejecutar `ReplayBacktestTest` contra `loops/r23c_yolo_validation/window_04/ticks_live.csv` (o convertido desde `ticks.jsonl`) con `BACKTEST_SUBMODE=YOLO`.
   - Correr `compare_live_replay.py` contra `loops/r23c_yolo_validation/window_04/trades.csv`.
   - Demostrar que los trades reales de la ventana 04 son reproducidos fielmente por el motor offline.
3. **Sin Regresiones:** Cero impacto en el runtime de la app Android.

---

## 5. REPORTE ESPERADO
Al concluir, Hermes debe generar:
`workflows/rounds/r23c_phase2_hermes.md`
documentando:
- Archivos modificados y diff resumido.
- Resultados de tests unitarios (`./gradlew.bat testDebugUnitTest`).
- Prueba empírica de paridad offline contra una ventana completada de R23C.
- Veredicto y porcentaje de paridad alcanzado.
