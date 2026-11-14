# TAREA CLAUDE CODE — FASE 1: PREFLIGHT FINAL R23H / WINDOW_05_CLEAN

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Principal)  
**Misión:** Auditoría previa de certificación técnica para la ejecución de la ventana final `window_05_clean`.

---

## CONDICIONES A CONFIRMAR OBLIGATORIAMENTE

Antes de autorizar el inicio de la ventana `window_05_clean`:

1. **Git SHA y Build Instalado:**
   - Inspeccionar el binario instalado en el dispositivo Redmi A1 (`dumpsys package com.example.tradedraw`).
   - Confirmar `versionCode = 19`, `versionName = "1.9-r23h-exhaustion"`, `buildId = "R23H-BUY-EXHAUSTION"`, `gitSha = "aac1df3"`.

2. **Paridad de Especificación Live ↔ Replay:**
   Confirmar que Live y Replay consumirán exactamente:
   - Mismo RIC: `Z-CRY/IDX` (nominal Crypto IDX).
   - Mismo dataset: `ticks_live.csv` con poda de borde `ts >= window_start_ts_ms`.
   - Mismo submodo: `YOLO` (en Live y `BACKTEST_SUBMODE=YOLO` en Replay).
   - Mismas políticas defensivas: replicación activa de `motivoVetoPorAgotamientoDeCompra`.
   - Misma fórmula: uso compartido e inmutable de `calcularPoderesDeMercado()`.
   - Mismo veto: `VETO_BUY_EXHAUSTION_CLIMAX`.
   - Mismo cooldown: 8,000 ms (`YOLO_COOLDOWN_MS`).
   - Mismo criterio temporal: `temporal_tolerance_ms = 6000` ms en `compare_live_replay.py --mode trades`.

3. **Aislamiento de Directorio `window_05_clean`:**
   - Confirmar que `loops/r23h_validation/window_05_clean/` no existe previamente en disco y parte de cero estado residual.

4. **Salvaguarda DEMO:**
   - Confirmar en `http://127.0.0.1:8080/status` que `isDemoAccount == true`.
   - Confirmar que cualquier cuenta real está sujeta a veto fail-closed.

5. **Ausencia de Estado Heredado:**
   - Confirmar que la nueva ventana no arrastra logs, ticks ni trades de `window_01`, `window_02`, `window_03` ni `window_04_clean`.

6. **Estrategia Estrictamente Congelada:**
   - Confirmar que los cambios en el working tree son exclusivamente de infraestructura (`TradingEngine.kt` extracción interna pura, `ReplayBacktestTest.kt`, `compare_live_replay.py`, `live_monitor_r23_autonomous.py`, suite de tests de trazabilidad).
   - Cero modificaciones a BUY/SELL, thresholds, pesos, confluencia o risk management.

---

## ENTREGABLE

Generar el reporte formal en:
`workflows/rounds/R23H_CLAUDE_PREFLIGHT_W05.md`

Emitir como veredicto inequívoco:

`PREFLIGHT = READY`

o

`PREFLIGHT = BLOCKED` (con las causas detalladas).
