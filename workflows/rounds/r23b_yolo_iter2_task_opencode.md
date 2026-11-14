# R23B — VALIDACIÓN LIVE ITERACIÓN 2 (MODO YOLO EN CUENTA DEMO)

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-15  
**Objetivo:** Validar en hardware real (Xiaomi Redmi A1) la telemetría determinística de dirección (BUY/SELL) en eventos `ORDER_STATE`, erradicando `action = UNKNOWN` en `trades.csv`, y evaluar la consistencia del rendimiento de YOLO en una segunda ventana de 20 minutos.

---

## 1. CONTEXTO Y ESTADO DEL SISTEMA

- **Fase:** R23B — Iteración 2.
- **Commit Candidato:** `ac079fd`.
- **APK Instalada y Activa:**
  - `versionCode = 14`
  - `versionName = "1.4-r23-yolo-iter2"`
  - `BUILD_ID = "R23-YOLO-ITER2"`
  - `gitSha = "ac079fd"`
- **Conectividad:**
  - Enlace WiFi activo y autorizado: `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`.
  - Port forwarding configurado: `adb -s "[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555" forward tcp:8080 tcp:8080`.
  - Endpoint local: `http://127.0.0.1:8080/status`.
- **Estado Actual del Motor:**
  - `mode = "AUTONOMOUS"`
  - `submode = "YOLO"`
  - `isDemoAccount = true` (Fail-closed obligatorio)
  - Stake efectivo acoplado: $100.000,00\text{ COP}$
- **Hipótesis a Probar:**
  1. Con la inyección de `pendingTradeAction` en `setOrderState` (en `ENVIADA`, `CONFIRMADA` y `NO_PROCESADA`), el 100% de los trades registrados en `trades.csv` tendrán acción determinada (`BUY` o `SELL`), con 0 filas `UNKNOWN`.
  2. Con el filtro de `ORDER_STATE` / `saldo movido` en el monitor, los eventos de tap físico `END root` quedan excluidos de `trades.csv`.
  3. La operatividad y cadencia de YOLO se mantienen consistentes frente a la Iteración 1.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   ```text
   loops/r23_yolo/iter_2/
   ```

2. **Comando de Monitoreo (con codificación UTF-8):**
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 2 `
     --out-dir loops/r23_yolo/iter_2 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-sha ac079fd `
     --expected-build-id R23-YOLO-ITER2 `
     --expected-version-code 14
   ```

3. **Descarga Post-Ventana de Ticks y Eventos:**
   Al concluir los 20 minutos:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23_yolo/iter_2/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23_yolo/iter_2/ticks.jsonl
   ```

---

## 3. CHECKLIST Y CONTROLES CRÍTICOS DE VALIDACIÓN

### A. Seguridad Absoluta
- `isDemoAccount == true` en el 100% de las muestras (0 órdenes reales).
- `submode == "YOLO"` sostenido durante toda la ventana.

### B. Criterio Específico de Éxito de la Iteración 2
- En `loops/r23_yolo/iter_2/trades.csv`:
  - **`action = UNKNOWN` debe ser 0**. Cada trade debe indicar explícitamente `BUY` o `SELL`.
  - No deben figurar eventos `END root (tap)` como trades.
- En `/events`:
  - Los eventos de orden deben tener la firma `"CONFIRMADA BUY saldo movido"`, `"CONFIRMADA SELL saldo movido"` o `"ENVIADA BUY tap despachado"`.
  - El estado de reposo debe mantenerse limpio: `"NINGUNA sin operacion en curso"`.

### C. Controles Negativos
- `ADAPTIVE_BLOCK` debe seguir bloqueando anti-patrones.
- `Cooldown YOLO` (8s) y `Operación abierta en curso` activos para evitar colisiones.

---

## 4. ENTREGABLES REQUERIDOS

1. `loops/r23_yolo/iter_2/LIVE_REPORT.md`: Métricas completas de la ventana (muestras, señales, taps, trades W/L, saldo inicial/final, distribución de acciones BUY/SELL).
2. `loops/r23_yolo/iter_2/VALIDATOR_FINDINGS.md`: Análisis comparativo frente a Iteración 1, validación de la eliminación de `UNKNOWN`, y recomendación de veredicto (`KEEP` / `REVERT` / `MODIFY`).
3. Notificar a AGY (`w1:p1`) al terminar.
