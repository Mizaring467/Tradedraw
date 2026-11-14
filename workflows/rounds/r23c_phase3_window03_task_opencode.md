# R23C — FASE 3: VALIDACIÓN LIVE · VENTANA 03 DE 03 (FINAL)

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C — Fase 3 (Validación de Refinamiento YOLO con Filtros Asimétricos SELL)  
**Ventana:** 03 de 03 (Cierre de la Serie Fase 3)  
**Objetivo:** Concluir la serie formal de 3 ventanas de validación en cuenta DEMO bajo los filtros asimétricos de Fase 3:
- Confluencia SELL ≥ 80 pts (BUY 75 pts).
- Mecha contra-tendencia SELL ≥ 45% (BUY 30%).
- MT_REJECTION PUT ≥ 40% (CALL 30%).
- Acumulado previo (w01 + w02): $N=3$, $2\text{ W} / 1\text{ L}$ ($WR=66.7\%$), $\Delta +65.999,36\text{ COP}$, proporción SELL reducida a $33.3\%$ (vs $60.9\%$ en Fase 1).

---

## 1. REGLA INMUTABLE DE CONFIGURACIÓN
- **Código congelado:** Prohibido modificar código, umbrales o stake.
- **APK Activa en Redmi A1:**
  - `versionCode = 15`
  - `BUILD_ID = "R23C-PHASE3"`
- **Modo:** `AUTONOMOUS` | **Submodo:** `YOLO`
- **Seguridad:** `isDemoAccount == true` estricto en el 100% de las muestras.
- **Activo:** `Z-CRY/IDX`.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   ```text
   loops/r23c_phase3_validation/window_03/
   ```

2. **Comando de Monitoreo:**
   Ejecutar con `PYTHONIOENCODING=utf-8`:
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 3 `
     --out-dir loops/r23c_phase3_validation/window_03 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-build-id R23C-PHASE3 `
     --expected-version-code 15
   ```

3. **Descarga Post-Ventana:**
   Al concluir los 20 minutos:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23c_phase3_validation/window_03/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23c_phase3_validation/window_03/ticks.jsonl
   ```

---

## 3. ENTREGABLE REQUERIDO: `WINDOW_REPORT.md`

Generar en `loops/r23c_phase3_validation/window_03/WINDOW_REPORT.md` con las secciones estándar:
1. **RESULTADOS W03:** $N$, $W$, $L$, $VOID$, $WR$, Payout, $WR_{BE} = 54.95\%$.
2. **RACHAS:** `max_win_streak`, `max_loss_streak`, secuencia.
3. **BALANCE:** Saldo inicial, saldo final, $\Delta\text{ COP}$, max drawdown.
4. **DISTRIBUCIÓN Y CIERRE ACUMULADO FASE 3 (w01 + w02 + w03):**
   - Desglose consolidado BUY vs SELL: Conteo, victorias, derrotas, WR direccional y proporción de volumen SELL vs baseline Fase 1 ($60.9\%$).
   - ¿El endurecimiento mitigó la pérdida neta observada en SELL?
   - Supresión por confluencia total observada.
5. **SEGURIDAD Y TELEMETRÍA:** Verificación DEMO 100%, recuento de vetos (`ADAPTIVE_BLOCK`, `OPERACIÓN EN CURSO`, `COOLDOWN`), nivel de `COOLDOWN_ACTIVE`.
6. **BUILD Y PARIDAD:** `versionCode = 15`, `BUILD_ID = "R23C-PHASE3"`, resultado del comparador live-replay (fase 2b).

---

## 4. NOTIFICACIÓN
Al terminar, notificar a AGY en `w1:p1` para consolidar el veredicto final de la Fase 3.
