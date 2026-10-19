# TAREA OPENCODE: RECOLECCIÓN HOMOGÉNEA R23K — VENTANA 15 LIMPIA (CUENTA DEMO)
## FASE D: EJECUCIÓN LIVE DE 20 MINUTOS CON MONITOR V2 (CIERRE ORDENADO Y GRACIA DE LIQUIDACIÓN)
## EQUIPO AUTORIZADO: AGY (Orquestador), CLAUDE CODE (Auditor), OPENCODE (Validador), HERMES (Implementador)

**Fecha:** 2026-10-19  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:pB` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Commit de Build Identificada:** `8cdb61f`  
**Build Instalada y Verificada:**  
- `versionCode = 19`  
- `versionName = "1.9-r23h-exhaustion"`  
- `BUILD_ID = "R23H-BUY-EXHAUSTION"`  
- `gitSha = "8cdb61f"`  
- `isDemoAccount = true`  
- `balance inicial certificado = 38,751,736.32 COP DEMO` (certificado en R23K_CLAUDE_W15_PREFLIGHT.md)  
**Directorio de salida:** `loops/r23k_validation/window_15`  
**Duración nominal:** 20.0 minutos  
**Monitor Activo:** `scripts/validation/live_monitor_r23_autonomous.py` (Monitor v2 con cierre ordenado y gracia de liquidación hasta 150s)

---

## 1. REGLA ABSOLUTA DE SEGURIDAD
- **100% CUENTA DEMO (`isDemoAccount == true`).**
- Si en cualquier lectura periódica `isDemoAccount != true`: ABORTO FAIL-CLOSED INMEDIATO (`mode=DISABLED`).
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).
- Condición de parada de emergencia: Drawdown > 3.5%, batería < 20% sin cargar, discrepancia contable.

---

## 2. ACTIVACIÓN Y DISPARO DE MODO
1. Asegurar primer plano de broker:
   ```powershell
   adb -s UGU8KFUKM7E6CE8D shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1
   ```
2. Activar Submodo YOLO:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"
   ```
3. Activar Modo AUTONOMOUS:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"
   ```
4. Confirmar en `/status`: `"mode": "AUTONOMOUS"`, `"submode": "YOLO"`, `"health": {"state":"READY"}`.

---

## 3. EJECUCIÓN DEL MONITOR EN VIVO V2 (20 MINUTOS + GRACIA)
Ejecutar el monitor autónomo v2:
```powershell
python scripts/validation/live_monitor_r23_autonomous.py `
  --duration-min 20.0 `
  --iter 15 `
  --expected-sha 8cdb61f `
  --expected-version-code 19 `
  --expected-build-id "R23H-BUY-EXHAUSTION" `
  --out-dir loops/r23k_validation/window_15
```

---

## 4. VERIFICACIÓN POST-CIERRE
Al finalizar el monitor:
1. Confirmar que el monitor haya pasado a `mode=DISABLED` al cierre nominal y esperado la resolución limpia de órdenes activas (`grace_wait_ms`).
2. Verificar que existan en `loops/r23k_validation/window_15/`:
   - `trades.csv`
   - `decisions.csv`
   - `live_samples.jsonl`
   - `status_samples.jsonl`
   - `events.jsonl`
   - `mode_log.json`
   - `LIVE_REPORT.md`
3. Extraer métricas: N trades, Wins, Losses, balance final exacto, delta neto y `grace_wait_ms`.
4. Reportar a AGY para auditoría independiente de Claude Code (`w1:pC`).
