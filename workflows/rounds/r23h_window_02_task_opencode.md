# TAREA OPENCODE: VALIDACIÓN R23H — VENTANA 02 DE 3 (CUENTA DEMO)

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:pB` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Commit Auditado y Aprobado:** `aac1df3`  
**Build Instalada y Verificada:**  
- `versionCode = 19`  
- `versionName = "1.9-r23h-exhaustion"`  
- `BUILD_ID = "R23H-BUY-EXHAUSTION"`  
- `gitSha = "aac1df3"`  
- `isDemoAccount = true`  
**Directorio de salida:** `loops/r23h_validation/window_02/`  
**Duración nominal:** 20.0 minutos  

---

## 1. REGLA ABSOLUTA DE SEGURIDAD
- **100% CUENTA DEMO (`isDemoAccount == true`).**
- Si en cualquier lectura periódica `isDemoAccount != true`: ABORTO FAIL-CLOSED INMEDIATO.
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).

---

## 2. PRE-VUELO Y VERIFICACIÓN
1. Asegurar ADB forward:
   ```bash
   & "C:\Users\heidy\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s UGU8KFUKM7E6CE8D forward tcp:8080 tcp:8080
   ```
2. Verificar en `http://localhost:8080/status`:
   - `buildId == "R23H-BUY-EXHAUSTION"`
   - `gitSha == "aac1df3"`
   - `versionCode == 19`
   - `isDemoAccount == true`
   - `health.state == "READY"`
3. Asegurar primer plano de Binomo (`com.marketly.trading`):
   ```bash
   & "C:\Users\heidy\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s UGU8KFUKM7E6CE8D shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1
   ```
4. Activar Submodo YOLO:
   ```bash
   curl.exe -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"
   ```
5. Activar Modo AUTONOMOUS:
   ```bash
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"
   ```
   Confirmar en `/status`: `"mode": "AUTONOMOUS"`, `"submode": "YOLO"`.

---

## 3. EJECUCIÓN DEL MONITOR EN VIVO
Ejecutar el monitor autónomo de 20 minutos:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 2 \
  --expected-version-code 19 \
  --expected-build-id "R23H-BUY-EXHAUSTION" \
  --expected-sha "aac1df3" \
  --out-dir loops/r23h_validation/window_02
```

---

## 4. AUDITORÍA POST-VENTANA Y ARTEFACTOS
Al finalizar los 20 minutos (con settlement grace):
1. Desactivar modo autónomo:
   ```bash
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   ```
2. Validar integridad contable trade por trade:
   - Cumplimiento de la ecuación canónica: $N = W + L + VOID$.
   - Ninguna mezcla de contadores globales con trades de ventana.
   - Conteo y desglose de vetos `VETO_BUY_EXHAUSTION_CLIMAX`.
   - Conteo de vetos `ADAPTIVE_BLOCK`.
3. Generar en `loops/r23h_validation/window_02/`:
   - `WINDOW_REPORT.md`
   - `LIVE_REPORT.md`
   - `window_metadata.json`
