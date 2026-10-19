# MISIÓN HERMES: CORRECCIÓN MÍNIMA DE INFRAESTRUCTURA DE TRAZABILIDAD Y PARIDAD LIVE ↔ REPLAY

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** HERMES (`w1:pA` / Implementador)  
**Estado:** AUTORIZADO (Causa raíz demostrada y confirmada por Claude Code y OpenCode)

---

## 1. REGLA ABSOLUTA DE CONGELACIÓN
**LA ESTRATEGIA ESTÁ 100% CONGELADA.**
- NO modificar: lógica BUY, lógica SELL, thresholds, `callPower`, `VETO_BUY_EXHAUSTION_CLIMAX`, `ADAPTIVE_BLOCK`, YOLO, stake, payout, risk management, o filtros de mercado.
- Modificar EXCLUSIVAMENTE infraestructura de:
  - Arnés de replay (`ReplayBacktestTest.kt`).
  - Podado de ticks de inicio de ventana en monitor (`live_monitor_r23_autonomous.py`).
  - Tolerancia temporal en comparador (`compare_live_replay.py`).
  - Suite de tests deterministas de trazabilidad.

---

## 2. CAMBIOS A IMPLEMENTAR (FILOSOFÍA PONYTAIL: MÍNIMOS Y PRECISOS)

### Cambio 1: Arnés de Replay (`app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`)
En `runBacktestOnStrategy`:
1. Aplicar `TradingEngine.motivoVetoPorAgotamientoDeCompra` a la señal antes de convertirla en orden simulada:
   ```kotlin
   val motivoAgotamiento = TradingEngine.motivoVetoPorAgotamientoDeCompra(
       action = action,
       trend = eng.detectedTrend.name,
       callPower = eng.signalPowerCall
   )
   if (motivoAgotamiento != null) {
       // La señal fue vetada por política de clímax: no se simula trade ni se bloquea la vela
       return@onSignalGenerated
   }
   ```
2. Esto asegura que Replay no dispare trades en señales vetadas por clímax en :01 y permita evaluar el tick posterior de la misma vela.

### Cambio 2: Podado de Ticks en Monitor (`scripts/validation/live_monitor_r23_autonomous.py`)
En el volcado final de `out_ticks_csv`:
Filtrar los ticks acumulados para que solo se exporten los que tengan `ts >= window_start_ts_ms` (o el timestamp de inicio de monitoreo de la ventana), eliminando los ticks residuales pre-ventana del búfer APK.

### Cambio 3: Comparador Live ↔ Replay (`scripts/validation/compare_live_replay.py`)
En `compare_trades`:
Ajustar `temporal_tolerance_ms: int = 6000` (en vez de 3000 ms) para absorber la latencia física de actualización y lectura de saldo en hardware Android Go (~4.5s–5.0s).

### Cambio 4: Suite de Tests Deterministas (§ FASE 5)
Crear `app/src/test/java/com/example/tradedraw/LiveReplayParityTraceabilityTest.kt` conteniendo:
- **TEST 1 — TRADE REAL:** Una operación Live `decision -> trade -> outcome` es localizada en Replay.
- **TEST 2 — DECISIÓN BLOQUEADA:** Una decisión bloqueada `decision -> BLOCKED` (ej. clímax de compra) NO aparece como trade simulado en Replay.
- **TEST 3 — IDENTIDAD:** Dos decisiones diferentes (distinto segundo/tick o acción) no colisionan en `decision_id`.
- **TEST 4 — DUPLICACIÓN:** Procesar dos veces el mismo evento no genera una identidad ambigua.
- **TEST 5 — PARIDAD:** Un conjunto de eventos Live y Replay emparejan con veredicto `PASS` y match_rate = 100%.

---

## 3. VERIFICACIÓN REQUERIDA
1. Ejecutar `./gradlew testDebugUnitTest` y verificar que la suite completa pase (459+ tests verdes).
2. Probar offline la re-comparación de W03 para verificar que el match rate ya no sea 0.0%.
3. Entregar: archivos modificados, diff exacto, resultado de tests y confirmación de que la estrategia NO fue alterada.
