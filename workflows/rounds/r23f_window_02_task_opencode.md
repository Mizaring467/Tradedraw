# TAREA OPENCODE: VALIDACIÓN R23F — VENTANA 02 DE 03 (CUENTA DEMO)

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p7` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Base git:** `1889c2d`  
**Build congelada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `BUILD_ID = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Directorio de salida:** `loops/r23f_validation/window_02/`  
**Duración nominal:** 20.0 minutos  

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
   - `versionCode == 18` y `buildId == "R23F-ADAPTIVE"`
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
Ejecutar el monitor autónomo:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 2 \
  --expected-version-code 18 \
  --expected-build-id "R23F-ADAPTIVE" \
  --expected-sha "ac6b0b2" \
  --out-dir loops/r23f_validation/window_02
```

---

## 4. VERIFICACIONES CRÍTICAS DE AUDITORÍA (OPENCODE)
1. Continuidad de reactividad operativa: monitorear trades reales generados ($N_{\text{real}}$).
2. Verificación de retención de veto al 99%: todos los bloqueos en `counterfactual_adaptive_block.csv` deben reportar `similarity == 99.0%`.
3. Dinámica de balance DEMO, drawdown y outliers.
4. Seguridad absoluta fail-closed.

---

## 5. POST-EJECUCIÓN Y SEGURIDAD
Al concluir la captura del monitor:
1. Desactivar modo de inmediato:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   curl -s -X POST "http://127.0.0.1:8080/submode?name=CONSERVATIVE"
   ```
2. Verificar artefactos generados en `loops/r23f_validation/window_02/`.
3. Redactar el reporte formal de la ventana en `loops/r23f_validation/window_02/WINDOW_REPORT.md`.
4. Commitear artefactos y reportes.
5. Notificar a AGY para el lanzamiento de la Ventana 03 final.
