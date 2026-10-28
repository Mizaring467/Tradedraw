# TAREA OPENCODE: VALIDACIÓN R23G — VENTANA 05 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p7` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Base git:** `103b87d`  
**Build congelada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `BUILD_ID = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Directorio de salida:** `loops/r23g_validation/window_05/`  
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
   adb -s UGU8KFUKM7E6CE8D forward tcp:8080 tcp:8080
   ```
2. Verificar salud en `/status`:
   - `health.state == "READY"`
   - `isDemoAccount == true`
   - `activeAsset == "Z-CRY/IDX"`
   - `versionCode == 18` y `buildId == "R23F-ADAPTIVE"`
3. Confirmar Submodo YOLO:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"
   ```
4. Confirmar Modo AUTONOMOUS:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"
   ```

---

## 3. EJECUCIÓN DEL MONITOR EN VIVO
Ejecutar el monitor autónomo:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 5 \
  --expected-version-code 18 \
  --expected-build-id "R23F-ADAPTIVE" \
  --expected-sha "ac6b0b2" \
  --out-dir loops/r23g_validation/window_05
```

---

## 4. VERIFICACIONES CRÍTICAS DE AUDITORÍA (OPENCODE)
1. Monitoreo de trades reales confirmados ($N$, $W$, $L$, $WR$).
2. Cero vetos de anti-patrón en bucket 98%.
3. Seguimiento contrafactual pasivo del bucket 99% en `counterfactual_adaptive_block.csv`.
4. Verificación de balance y descarte de outliers telemétricos.
5. Control de drawdown dentro del límite pre-registrado ($\le 3.5\%$).
6. Live $\leftrightarrow$ Replay comparison.
