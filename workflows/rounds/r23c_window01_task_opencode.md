# R23C — VALIDACIÓN ESTADÍSTICA YOLO · VENTANA 01 DE 06

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C (Validación Estadística de YOLO en Cuenta DEMO)  
**Ventana:** 01 de 06  
**Objetivo:** Acumular trades confirmados con resolución W/L bajo el baseline técnico congelado `ac079fd`, verificando la dirección de órdenes y el comportamiento de rachas en cuenta DEMO.

---

## 1. REGLA INMUTABLE DE CONFIGURACIÓN
- **Código congelado:** Prohibido modificar código, umbrales, scoring o stake.
- **APK Activa en Redmi A1:**
  - `versionCode = 14`
  - `BUILD_ID = "R23-YOLO-ITER2"`
  - `gitSha = "ac079fd"`
- **Modo:** `AUTONOMOUS` | **Submodo:** `YOLO`
- **Seguridad:** `isDemoAccount == true` estricto en el 100% de las muestras.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   ```text
   loops/r23c_yolo_validation/window_01/
   ```

2. **Comando de Monitoreo:**
   Ejecutar con `PYTHONIOENCODING=utf-8`:
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 1 `
     --out-dir loops/r23c_yolo_validation/window_01 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-sha ac079fd `
     --expected-build-id R23-YOLO-ITER2 `
     --expected-version-code 14
   ```

3. **Descarga Post-Ventana:**
   Al concluir los 20 minutos:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23c_yolo_validation/window_01/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23c_yolo_validation/window_01/ticks.jsonl
   ```

---

## 3. ENTREGABLE REQUERIDO: `WINDOW_REPORT.md`

Generar en `loops/r23c_yolo_validation/window_01/WINDOW_REPORT.md` con la siguiente estructura exacta:

### 1. RESULTADOS
- $N$ (Trades confirmados con resolución W/L en esta ventana; NO contar taps sin saldo ni eventos de reposo)
- $W$ (Ganadas)
- $L$ (Perdidas)
- $VOID$ (Empates o anuladas)
- $WR$ (Win Rate de la ventana)
- Payout observado promedio (e.g. 82%)
- Break-Even Win Rate ($WR_{BE} = 1 / (1 + \text{payout})$)

### 2. RACHAS
- Racha máxima de victorias en la ventana (`max_win_streak`)
- Racha máxima de derrotas en la ventana (`max_loss_streak`)
- Si hubo racha $\ge 5$ victorias: detallar timestamps, activo, dirección y score de cada trade.

### 3. BALANCE
- Saldo inicial de la ventana
- Saldo final de la ventana
- $\Delta$ Balance neto (COP)
- Máximo drawdown observado en la ventana (pico vs valle en COP y en %)

### 4. DISTRIBUCIÓN
- BUY count y BUY WR
- SELL count y SELL WR
- Régimen de mercado predominante (TRENDING / SIDEWAYS)
- Activo operado

### 5. SEGURIDAD Y TELEMETRÍA
- Verificación DEMO: $X / X$ muestras con `isDemoAccount == true` (0 cuentas reales)
- Conteo de vetos `ADAPTIVE_BLOCK`, `COOLDOWN`, `OPERACIÓN EN CURSO`
- Conteo de `action = UNKNOWN` en `trades.csv` (debe ser 0)
- Exclusión de eventos de tap `END root` de la tabla de trades

### 6. BUILD
- `gitSha`: `ac079fd`
- `versionCode`: 14
- `buildId`: `R23-YOLO-ITER2`

---

## 4. NOTIFICACIÓN
Al terminar, notificar a AGY en `w1:p1` con el resumen cuantitativo para actualizar `AGGREGATE_REPORT.md`.
