# R23C — FASE 3: VALIDACIÓN LIVE · VENTANA 01 DE 03 (YOLO ENDURECIDO)

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C — Fase 3 (Validación de Refinamiento YOLO con Filtros Asimétricos SELL)  
**Ventana:** 01 de 03 (Serie de Validación Fase 3)  
**Objetivo:** Medir el impacto empírico en cuenta DEMO del endurecimiento selectivo de ventas (SELL) en submodo `YOLO`:
1. Confluencia mínima para SELL: 80 pts (BUY mantiene 75 pts).
2. Mecha contra-tendencia para SELL: ≥ 45% en UPTREND (BUY mantiene 30% en DOWNTREND).
3. Mecha de rechazo en `MT_REJECTION`: ≥ 40% para PUT/SELL (CALL/BUY mantiene 30%).

---

## 1. REGLA INMUTABLE DE CONFIGURACIÓN
- **Código congelado:** Prohibido modificar código, umbrales o stake durante la ventana.
- **APK Activa en Redmi A1:**
  - `versionCode = 15`
  - `BUILD_ID = "R23C-PHASE3"`
- **Modo:** `AUTONOMOUS` | **Submodo:** `YOLO`
- **Seguridad:** `isDemoAccount == true` estricto en el 100% de las muestras. ABORTAR de inmediato si deja de ser demo.
- **Activo:** `Z-CRY/IDX`.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   ```text
   loops/r23c_phase3_validation/window_01/
   ```

2. **Comando de Monitoreo:**
   Ejecutar con `PYTHONIOENCODING=utf-8`:
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 1 `
     --out-dir loops/r23c_phase3_validation/window_01 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-build-id R23C-PHASE3 `
     --expected-version-code 15
   ```

3. **Descarga Post-Ventana:**
   Al concluir los 20 minutos:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23c_phase3_validation/window_01/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23c_phase3_validation/window_01/ticks.jsonl
   ```

---

## 3. ENTREGABLE REQUERIDO: `WINDOW_REPORT.md`

Generar en `loops/r23c_phase3_validation/window_01/WINDOW_REPORT.md` con las 6 secciones estándar:
1. **RESULTADOS:** $N$ (resoluciones W/L reales en trades confirmados con delta de saldo), $W$, $L$, $VOID$, $WR$, Payout, $WR_{BE} = 54.95\%$.
2. **RACHAS:** `max_win_streak`, `max_loss_streak`, secuencia ordenada (e.g. W,L,W...).
3. **BALANCE:** Saldo inicial, saldo final, $\Delta\text{ COP}$, max drawdown de la ventana.
4. **DISTRIBUCIÓN Y EFECTO DEL ENDURECIMIENTO:**
   - Desglose BUY vs SELL: Conteo, victorias, derrotas y WR direccional.
   - Comparativa vs Fase 1 baseline: ¿Se redujo la proporción excesiva de SELL (era 60.9%)? ¿Mejoró el WR de SELL (era 42.86%)?
   - Régimen predominante de mercado y activo.
5. **SEGURIDAD Y TELEMETRÍA:** Verificación DEMO 100%, recuento de vetos (`ADAPTIVE_BLOCK`, `OPERACIÓN EN CURSO`, `COOLDOWN`), 0 `UNKNOWN`, 0 `END root`. Nivel máximo de `COOLDOWN_ACTIVE`.
6. **BUILD:** `versionCode = 15`, `BUILD_ID = "R23C-PHASE3"`, SHA verificado.

---

## 4. NOTIFICACIÓN
Al terminar, notificar a AGY en `w1:p1` para comparar contra el baseline de Fase 1 y evaluar si se mantiene el submodo YOLO calibrado.
