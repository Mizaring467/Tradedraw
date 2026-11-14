# R23B — VALIDACIÓN LIVE BASELINE (MODO YOLO EN CUENTA DEMO)

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-15  
**Objetivo:** Medir el baseline puro del modo YOLO en cuenta DEMO durante una ventana de 20 minutos en hardware real (Redmi A1).  

---

## 1. CONTEXTO Y ESTADO DEL SISTEMA

- **Fase:** R23B (Loop de Mejora Controlada con Modo YOLO en Cuenta DEMO).
- **Submodo:** `YOLO` activado en el dispositivo vía `/submode?name=YOLO`.
- **Modo Global:** `AUTONOMOUS` activo.
- **Auditoría Técnica:** Documentada en `workflows/R23B_YOLO_AUDIT.md`.
- **Regla Absoluta de Seguridad:** `isDemoAccount == true` fail-closed. CERO órdenes reales.
- **APK Instalada:** `versionCode = 12`, `BUILD_ID = R23-AUTO-ITER3`, SHA `bd7ef4e`.
- **Saldo Inicial de Referencia:** $38.320.737,28\text{ COP}$.
- **Stake Observado y Efectivo:** $100.000\text{ COP}$.

---

## 2. INSTRUCCIONES PARA OPENCODE

1. **Directorio de Trabajo de la Ronda:**
   Crear el directorio si no existe:
   ```text
   loops/r23_yolo/baseline/
   ```

2. **Ejecutar Monitor de Validación Live (20 Minutos):**
   Ejecutar con `PYTHONIOENCODING=utf-8` en terminal:
   ```bash
   python -u scripts/live_monitor_r23_autonomous.py --duration 1200 --interval 10 --out-dir loops/r23_yolo/baseline
   ```

3. **Verificaciones Durante y Post-Ventana:**
   - **Seguridad Demo:** Confirmar que el 100% de las muestras tengan `isDemoAccount == true`.
   - **Tasa de Señales y Trades:** Medir cuántas señales generó el motor y cuántas superaron los filtros para convertirse en taps físicos.
   - **Distribución de Vetos:** Cuantificar los motivos de veto en el log de eventos (`ADAPTIVE_BLOCK`, `COOLDOWN`, `RISK_LIMIT`, `CHOP`, `CONFLUENCIA`).
   - **Evolución del Balance:** Registrar delta de balance en COP, trades ganados, perdidos y Win Rate.
   - **Paridad Replay:** Ejecutar `ReplayBacktestTest` si aplica y registrar divergencias reales vs espurias.

4. **Entregables Requeridos:**
   - `loops/r23_yolo/baseline/LIVE_REPORT.md`: Reporte cuantitativo de la ventana con métricas completas.
   - `loops/r23_yolo/baseline/VALIDATOR_FINDINGS.md`: Hallazgos analíticos, comparación contra `AUTONOMOUS CONSERVATIVE` (R23A), y recomendaciones para la Hipótesis 1.
   - Notificar a AGY (`w1:p1`) al completar la validación.
