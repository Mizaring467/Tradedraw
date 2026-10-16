# TAREA OPENCODE: VALIDACIÓN R23D — VENTANA 01 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-16  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p6` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Build congelada:** `versionCode = 16` | `versionName = "1.6-r23d-telemetry"` | `BUILD_ID = "R23D-TELEMETRY"` | gitSha candidato `4aec1e5` (embebido `5b2245f`)  
**Directorio de salida:** `loops/r23d_validation/window_01/`  
**Duración nominal:** 20,0 minutos  

---

## 1. REGLA ABSOLUTA DE SEGURIDAD
- **100% CUENTA DEMO (`isDemoAccount == true`).**
- Si en cualquier instante `isDemoAccount != true`: aborto fail-closed inmediato.
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).

---

## 2. PRE-VUELO Y ACTIVACIÓN
1. Asegurar ADB forward:
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
2. Verificar salud en `/status`:
   - `health.state == "READY"`
   - `isDemoAccount == true`
   - `activeAsset == "Z-CRY/IDX"`
   - `versionCode == 16` y `buildId == "R23D-TELEMETRY"`
3. Activar Submodo YOLO:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"
   ```
   Confirmar en `/status`: `"submode": "YOLO"`.
4. Activar Modo AUTONOMOUS:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"
   ```
   Confirmar en `/status`: `"mode": "AUTONOMOUS"`.

---

## 3. EJECUCIÓN DEL MONITOR EN VIVO
Ejecutar el monitor autónomo instrumentado:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 1 \
  --expected-version-code 16 \
  --expected-build-id "R23D-TELEMETRY" \
  --out-dir loops/r23d_validation/window_01
```

*Nota:* Si al expirar los 20 min hay un trade en curso o pendiente de liquidación, el monitor activará automáticamente el nuevo `SETTLEMENT_GRACE_PERIOD` (hasta 75s) para capturar su resolución sin dejar el trade huérfano.

---

## 4. POST-EJECUCIÓN Y SEGURIDAD
Al concluir la captura del monitor:
1. Desactivar modo de inmediato:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   curl -s -X POST "http://127.0.0.1:8080/submode?name=CONSERVATIVE"
   ```
2. Verificar artefactos generados en `loops/r23d_validation/window_01/`:
   - `samples.jsonl` (verificar presencia de `raw_balance`, `validated_balance`, `balance_quality`)
   - `status_samples.jsonl`
   - `events.jsonl`
   - `decisions.csv` (verificar columna `score` con extracción real de confluencia o vacía)
   - `trades.csv`
   - `ticks_live.csv` (captura acumulada continua)
   - `window_metadata.json` (`nominal_window_end_ts`, `actual_capture_end_ts`, `settlement_extra_seconds`)
   - `replay_journal.csv`, `replay_comparison.json`, `replay_comparison.md`
   - `LIVE_REPORT.md`
3. Redactar el reporte formal de la ventana en:
   `loops/r23d_validation/window_01/WINDOW_REPORT.md`
   siguiendo la estructura estándar:
   - Identidad de build y hardware.
   - Resultados ($N, W, L, \text{VOID}, WR\%$, payout, $WR_{BE}$).
   - Rachas (secuencia de resoluciones, max win, max loss).
   - Balance y Drawdown (saldo inicial, final validado, $\Delta$, drawdown validado y desglose si hubo outliers de telemetría).
   - Distribución direccional (BUY vs SELL y régimen de mercado).
   - Paridad Live ↔ Replay (conteo de pares, matches exactos y tasa).
   - Hallazgos de telemetría (efectividad del settlement grace y del cooldown en YOLO).
4. Commitear los artefactos y el reporte:
   ```bash
   git add loops/r23d_validation/window_01/
   git commit -m "docs(r23d): reporte y artefactos de ventana 01 de validacion yolo"
   ```
5. Notificar a AGY (`w1:p1`) para consolidación acumulada y pase a ventana 02.
