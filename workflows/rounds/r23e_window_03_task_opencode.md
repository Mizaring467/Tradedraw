# TAREA OPENCODE: VALIDACIÓN R23E — VENTANA 03 DE 06 (CUENTA DEMO)

**Fecha:** 2026-10-16  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p7` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Base git:** `5a568a7`  
**Build congelada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `BUILD_ID = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Directorio de salida:** `loops/r23e_adaptive_audit/window_03/`  
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
   - `versionCode == 17` y `buildId == "R23E-SHADOW"`
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
Ejecutar el monitor autónomo instrumentado con pipeline contrafactual shadow:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 3 \
  --expected-version-code 17 \
  --expected-build-id "R23E-SHADOW" \
  --out-dir loops/r23e_adaptive_audit/window_03
```

---

## 4. VERIFICACIONES CRÍTICAS DE AUDITORÍA (OPENCODE)
1. **Los contrafactuales NUNCA generan taps:** `tapsDespachados` solo se incrementa ante trades reales aceptados.
2. **Cero impacto en balance:** Los contrafactuales no alteran el saldo de la cuenta DEMO.
3. **Cero impacto en contadores reales:** `totalWins` y `totalLosses` del motor no se modifican por eventos contrafactuales.
4. **Cero creación de `trade_id` real:** Los contrafactuales no aparecen en `trades.csv` real; residen exclusivamente en `counterfactual_adaptive_block.csv`.
5. **Deduplicación:** No deben existir contrafactuales solapados dentro de la ventana de 60s por dirección.
6. **Resolución contrafactual válida:** Cada contrafactual en `counterfactual_adaptive_block.csv` debe tener `entry_price`, `exit_price` de tick real, `result` (`COUNTERFACTUAL_WIN/LOSS/VOID`) y `theoretical_ev`.
7. **Población temprana del CSV:** Confirmar durante los primeros 2–3 minutos que `counterfactual_adaptive_block.csv` está registrando filas y no queda en 0.

---

## 5. POST-EJECUCIÓN Y SEGURIDAD
Al concluir la captura del monitor:
1. Desactivar modo de inmediato:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   curl -s -X POST "http://127.0.0.1:8080/submode?name=CONSERVATIVE"
   ```
2. Verificar artefactos generados en `loops/r23e_adaptive_audit/window_03/`:
   - `counterfactual_adaptive_block.csv`
   - `trades.csv`
   - `decisions.csv`
   - `status_samples.jsonl`
   - `live_samples.jsonl`
   - `events.jsonl`
   - `ticks_live.csv`
   - `window_metadata.json`
   - `LIVE_REPORT.md`
3. Redactar el reporte formal de la ventana en:
   `loops/r23e_adaptive_audit/window_03/WINDOW_REPORT.md`
   incluyendo la tabla comparativa consolidada acumulada (Ventanas 01, 02 y 03).
4. Commitear los artefactos y el reporte:
   ```bash
   git add loops/r23e_adaptive_audit/window_03/
   git commit -m "docs(r23e): reporte y artefactos de ventana 03 de auditoria contrafactual yolo"
   ```
5. Notificar a AGY (`w1:p1`) para consolidación acumulada y pase a ventana 04.
