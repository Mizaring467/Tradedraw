# R23C — VALIDACIÓN ESTADÍSTICA YOLO · VENTANA 03 DE 06

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C (Validación Estadística de YOLO en Cuenta DEMO)  
**Ventana:** 03 de 06  
**Objetivo:** Continuar la acumulación sistemática de trades confirmados con resolución W/L bajo el baseline técnico congelado `ac079fd` (meta: $N \ge 30$, acumulado actual: $N=10$).

---

## 1. REGLA INMUTABLE DE CONFIGURACIÓN
- **Código congelado:** Prohibido modificar código, umbrales, scoring o stake.
- **APK Activa en Redmi A1:**
  - `versionCode = 14`
  - `BUILD_ID = "R23-YOLO-ITER2"`
  - `gitSha = "ac079fd"`
- **Modo:** `AUTONOMOUS` | **Submodo:** `YOLO`
- **Seguridad:** `isDemoAccount == true` estricto en el 100% de las muestras.
- **Estado Actual del Saldo:** ~$38.554.736,64\text{ COP}$.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   ```text
   loops/r23c_yolo_validation/window_03/
   ```

2. **Comando de Monitoreo:**
   Ejecutar con `PYTHONIOENCODING=utf-8`:
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 3 `
     --out-dir loops/r23c_yolo_validation/window_03 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-sha ac079fd `
     --expected-build-id R23-YOLO-ITER2 `
     --expected-version-code 14
   ```

3. **Descarga Post-Ventana:**
   Al concluir los 20 minutos:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23c_yolo_validation/window_03/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23c_yolo_validation/window_03/ticks.jsonl
   ```

---

## 3. ENTREGABLE REQUERIDO: `WINDOW_REPORT.md`

Generar en `loops/r23c_yolo_validation/window_03/WINDOW_REPORT.md` con las 6 secciones estándar:
1. **RESULTADOS:** $N$ (resoluciones W/L reales), $W$, $L$, $VOID$, $WR$, Payout, $WR_{BE}$.
2. **RACHAS:** `max_win_streak`, `max_loss_streak`, detalle de racha $\ge 5$ si ocurre.
3. **BALANCE:** Inicial, final, $\Delta$, drawdown de ventana.
4. **DISTRIBUCIÓN:** BUY/SELL count y WR, régimen predominante, activo.
5. **SEGURIDAD Y TELEMETRÍA:** Verificación DEMO, vetos (`ADAPTIVE_BLOCK`, `OPERACIÓN EN CURSO`, `COOLDOWN`), 0 `UNKNOWN`, 0 `END root`.
6. **BUILD:** `ac079fd`, `versionCode = 14`, `R23-YOLO-ITER2`.

---

## 4. NOTIFICACIÓN
Al terminar, notificar a AGY en `w1:p1` para actualizar `AGGREGATE_REPORT.md` y continuar la acumulación.
