# TAREA OPENCODE: VALIDACIÓN R23F — VENTANA 01 DE 03 (CUENTA DEMO)

**Fecha:** 2026-10-17  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:p7` / Validador Live Independiente)  
**Rama:** `r10-night`  
**Base git:** `2752d61`  
**Build congelada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `BUILD_ID = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Directorio de salida:** `loops/r23f_validation/window_01/`  
**Duración nominal:** 20.0 minutos  

---

## 1. REGLA ABSOLUTA DE SEGURIDAD
- **100% CUENTA DEMO (`isDemoAccount == true`).**
- Si en cualquier instante `isDemoAccount != true`: aborto fail-closed inmediato.
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).
- Cero alteraciones en cuenta real.

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
Ejecutar el monitor autónomo instrumentado con pipeline contrafactual shadow:
```bash
python scripts/validation/live_monitor_r23_autonomous.py \
  --duration-min 20.0 \
  --iter 1 \
  --expected-version-code 18 \
  --expected-build-id "R23F-ADAPTIVE" \
  --expected-sha "ac6b0b2" \
  --out-dir loops/r23f_validation/window_01
```

---

## 4. VERIFICACIONES CRÍTICAS DE AUDITORÍA (OPENCODE)
1. **Verificación de Liberación de Señales al 98%:**
   - Comprobar que las señales que antes se vetaban con `ADAPTIVE_BLOCK [sim:98%]` ahora se autorizan y despachan taps (`tapsDespachados > 0`).
   - Cero vetos de anti-patrón `[sim:98%]` observados.
2. **Verificación de Retención de Veto al 99%:**
   - Comprobar que las señales cuasi-idénticas (`[sim:99%]` o `[sim:100%]`) continúan siendo vetadas por `ADAPTIVE_BLOCK`.
3. **Métricas Reales de Trading:**
   - Contabilizar trades reales confirmados ($N$, $W$, $L$, $WR$).
   - Variación de balance DEMO ($\Delta\text{ bal}$) y Drawdown.
4. **Pipeline Contrafactual Shadow:**
   - Seguir registrando los vetos residuales en `counterfactual_adaptive_block.csv` para auditar qué bloqueó el motor en 99%.

---

## 5. POST-EJECUCIÓN Y SEGURIDAD
Al concluir la captura del monitor (o tras el settlement grace period):
1. Desactivar modo de inmediato:
   ```bash
   curl -s -X POST "http://127.0.0.1:8080/mode?name=DISABLED"
   curl -s -X POST "http://127.0.0.1:8080/submode?name=CONSERVATIVE"
   ```
2. Verificar artefactos generados en `loops/r23f_validation/window_01/`.
3. Redactar el reporte formal de la ventana en:
   `loops/r23f_validation/window_01/WINDOW_REPORT.md`
4. Commitear artefactos y reportes.
5. Notificar a AGY para el control de la siguiente ventana (Ventana 02).
