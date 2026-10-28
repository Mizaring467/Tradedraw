# TAREA OPENCODE: RECOLECCIÓN HOMOGÉNEA R23J — VENTANA 10 LIMPIA (CUENTA DEMO)
## FASE C: EJECUCIÓN LIVE DE 20 MINUTOS Y ANÁLISIS DE PARIDAD CON COMPARADOR CONGELADO
## EQUIPO AUTORIZADO: AGY (Orquestador), CLAUDE CODE (Auditor), OPENCODE (Validador), HERMES (Implementador)

**Fecha:** 2026-10-18  
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
- `balance inicial registrado = 39,103,736.32 COP DEMO`  
**Directorio de salida:** `loops/r23j_validation/window_10_clean`  
**Duración nominal:** 20.0 minutos  
**Comparador Congelado:** `scripts/validation/compare_live_replay.py` (SHA-256: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`)

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

## 3. EJECUCIÓN DEL MONITOR EN VIVO (20 MINUTOS)
Ejecutar el monitor autónomo con poda estricta:
```powershell
python scripts/validation/live_monitor_r23_autonomous.py `
  --duration-min 20.0 `
  --iter 10 `
  --expected-sha 8cdb61f `
  --expected-version-code 19 `
  --expected-build-id "R23H-BUY-EXHAUSTION" `
  --out-dir loops/r23j_validation/window_10_clean
```

---

## 4. CIERRE Y POST-PROCESAMIENTO OBLIGATORIO
Al finalizar los 20 minutos (y settlement grace):
1. Desactivar modo autónomo:
   ```powershell
   curl.exe -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   ```
2. Ejecutar Replay con `BACKTEST_SUBMODE=YOLO`:
   ```powershell
   $env:BACKTEST_SUBMODE="YOLO"
   $env:REPLAY_TICKS_FILE="loops/r23j_validation/window_10_clean/ticks_live.csv"
   $env:REPLAY_JOURNAL_OUT="loops/r23j_validation/window_10_clean/replay_journal.csv"
   ./gradlew testDebugUnitTest --tests *ReplayBacktestTest*
   ```
3. Ejecutar comparador congelado formal:
   ```powershell
   python scripts/validation/compare_live_replay.py `
     --live-journal loops/r23j_validation/window_10_clean/trades.csv `
     --replay-journal loops/r23j_validation/window_10_clean/replay_journal.csv `
     --mode trades `
     --temporal-tolerance-ms 6000 `
     --output-md loops/r23j_validation/window_10_clean/REPLAY_PARITY_REPORT.md `
     --output-json loops/r23j_validation/window_10_clean/replay_parity.json
   ```
4. Generar reporte independiente de validación en `loops/r23j_validation/window_10_clean/WINDOW_REPORT.md`:
   - N, W, L, VOID, Win Rate, balance inicial, final, delta neto ($N = W + L + VOID$, discrepancia $0.00).
   - Conteo de bloqueos (ADAPTIVE_BLOCK, RISK_LIMIT, VETO_BUY_EXHAUSTION_CLIMAX, etc.).
   - Dictamen de paridad bajo Preregistro R23J §2.
   - Desglose BUY y SELL.
   - Acumulado en los 3 cubos pre-registrados.
