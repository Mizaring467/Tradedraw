# TAREA OPENCODE: VALIDACIÓN R23D — VENTANA 07 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-16  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p7` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Base git:** `d3ec889`  
**Build congelada:** `versionCode = 16` | `versionName = "1.6-r23d-telemetry"` | `BUILD_ID = "R23D-TELEMETRY"`  
**Directorio de salida:** `loops/r23d_validation/window_07/`  
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
  --iter 7 \
  --expected-version-code 16 \
  --expected-build-id "R23D-TELEMETRY" \
  --out-dir loops/r23d_validation/window_07
```

---

## 4. POST-EJECUCIÓN Y SEGURIDAD
Al concluir la captura del monitor:
1. Desactivar modo de inmediato:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   curl -s -X POST "http://127.0.0.1:8080/submode?name=CONSERVATIVE"
   ```
2. Verificar artefactos generados en `loops/r23d_validation/window_07/`:
   - `samples.jsonl`
   - `status_samples.jsonl`
   - `events.jsonl`
   - `decisions.csv`
   - `trades.csv`
   - `ticks_live.csv`
   - `window_metadata.json`
   - `replay_journal.csv`, `replay_comparison.json`, `replay_comparison.md`
   - `LIVE_REPORT.md`
3. Redactar el reporte formal de la ventana en:
   `loops/r23d_validation/window_07/WINDOW_REPORT.md`
   incluyendo la tabla de resultados acumulados consolidados (Ventanas 01 a 07).
4. Commitear los artefactos y el reporte:
   ```bash
   git add loops/r23d_validation/window_07/
   git commit -m "docs(r23d): reporte y artefactos de ventana 07 de validacion yolo"
   ```
5. Notificar a AGY (`w1:p1`) para consolidación acumulada y pase a ventana 08.
