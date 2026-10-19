# TAREA OPENCODE: REVALIDACIÓN FORMAL R23H — VENTANA 04 LIMPIA (CUENTA DEMO)
## REVALIDACIÓN LIVE ↔ REPLAY (TRACK A)

**Fecha:** 2026-10-18  
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
**Directorio de salida:** `loops/r23h_validation/window_04_clean`  
**Duración nominal:** 20.0 minutos  

---

## 1. REGLA ABSOLUTA DE SEGURIDAD
- **100% CUENTA DEMO (`isDemoAccount == true`).**
- Si en cualquier lectura periódica `isDemoAccount != true`: ABORTO FAIL-CLOSED INMEDIATO.
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).

---

## 2. PRE-VUELO Y ACTIVACIÓN
1. Asegurar ADB forward:
   ```powershell
   & "C:\Users\heidy\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s UGU8KFUKM7E6CE8D forward tcp:8080 tcp:8080
   ```
2. Verificar en `http://localhost:8080/status`:
   - `buildId == "R23H-BUY-EXHAUSTION"`
   - `gitSha == "aac1df3"`
   - `versionCode == 19`
   - `isDemoAccount == true`
   - `health.state == "READY"`
3. Asegurar primer plano de Binomo (`com.marketly.trading`):
   ```powershell
   & "C:\Users\heidy\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s UGU8KFUKM7E6CE8D shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1
   ```
4. Activar Submodo YOLO:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/submode?name=YOLO"
   ```
5. Activar Modo AUTONOMOUS:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=AUTONOMOUS"
   ```
   Confirmar en `/status`: `"mode": "AUTONOMOUS"`, `"submode": "YOLO"`.

---

## 3. EJECUCIÓN DEL MONITOR EN VIVO
Ejecutar el monitor autónomo de 20 minutos:
```powershell
python scripts/validation/live_monitor_r23_autonomous.py `
  --duration-min 20.0 `
  --iter 4 `
  --expected-version-code 19 `
  --expected-build-id "R23H-BUY-EXHAUSTION" `
  --expected-sha "aac1df3" `
  --out-dir loops/r23h_validation/window_04_clean
```

---

## 4. POST-VENTANA Y RECONCILIACIÓN REPLAY
Al finalizar los 20 minutos (y el periodo de settlement grace):
1. Desactivar modo autónomo:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   ```
2. Si el monitor no ejecutó el replay automáticamente o se requiere verificación manual:
   Ejecutar Replay con `BACKTEST_SUBMODE=YOLO`:
   ```powershell
   $env:BACKTEST_SUBMODE="YOLO"
   $env:REPLAY_TICKS_FILE="loops/r23h_validation/window_04_clean/ticks_live.csv"
   $env:REPLAY_JOURNAL_OUT="loops/r23h_validation/window_04_clean/replay_journal.csv"
   ./gradlew testDebugUnitTest --tests *ReplayBacktestTest*
   ```
3. Ejecutar comparador formal:
   ```powershell
   python scripts/validation/compare_live_replay.py `
     --live-dir loops/r23h_validation/window_04_clean `
     --replay-journal loops/r23h_validation/window_04_clean/replay_journal.csv `
     --mode trades `
     --temporal-tolerance-ms 6000 `
     --out-report loops/r23h_validation/window_04_clean/REPLAY_PARITY_REPORT.md
   ```
4. Generar reporte formal de validación y paridad en `loops/r23h_validation/window_04_clean/WINDOW_REPORT.md`.
