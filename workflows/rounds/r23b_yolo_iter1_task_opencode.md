# R23B — VALIDACIÓN LIVE ITERACIÓN 1 (MODO YOLO EN CUENTA DEMO)

**Destinatario:** OpenCode (`w1:pT`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-15  
**Objetivo:** Validar empíricamente en hardware real (Xiaomi Redmi A1) el desbloqueo de límites de sesión en YOLO (`sessionFloor`, `Trailing Profit Lock`, `stopLossStreak`) implementado por Hermes en el commit `ba75268`.

---

## 1. CONTEXTO Y ESTADO DEL SISTEMA

- **Fase:** R23B — Iteración 1.
- **Commit Candidato:** `ba75268`.
- **APK Instalada y Activa:**
  - `versionCode = 13`
  - `versionName = "1.4-r23-yolo-iter1"`
  - `BUILD_ID = "R23-YOLO-ITER1"`
  - `gitSha = "ba75268"`
- **Conectividad:**
  - Enlace WiFi activo y autorizado: `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`.
  - Port forwarding configurado: `adb -s "[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555" forward tcp:8080 tcp:8080`.
  - Endpoint local: `http://127.0.0.1:8080/status`.
- **Estado Actual del Motor:**
  - `mode = "AUTONOMOUS"`
  - `submode = "YOLO"` (ahora expuesto explícitamente en el JSON de `/status`)
  - `isDemoAccount = true` (Fail-closed obligatorio)
  - Saldo actual: $38.320.737,28\text{ COP}$
  - `effectiveStake = 100000.0`
- **Hipótesis a Probar:** Al omitir `sessionFloor` y `Trailing Profit Lock` en YOLO, el 74,3% de vetos que paralizaron el baseline desaparece, permitiendo que las señales cualificadas alcancen los taps en cuenta DEMO.

---

## 2. INSTRUCCIONES DE EJECUCIÓN (20 MINUTOS)

1. **Directorio de Salida:**
   Asegurar existencia de:
   ```text
   loops/r23_yolo/iter_1/
   ```

2. **Comando Exacto de Monitoreo:**
   Ejecutar con codificación UTF-8:
   ```bash
   $env:PYTHONIOENCODING="utf-8"
   python -u scripts/validation/live_monitor_r23_autonomous.py `
     --iter 1 `
     --out-dir loops/r23_yolo/iter_1 `
     --duration-min 20 `
     --interval-sec 10 `
     --expected-sha ba75268 `
     --expected-build-id R23-YOLO-ITER1 `
     --expected-version-code 13
   ```

3. **Descarga Post-Ventana de Ticks y Eventos:**
   Al finalizar la ventana de 20 minutos, descargar los buffers crudos antes de cualquier reinicio:
   ```bash
   curl.exe -s http://127.0.0.1:8080/events > loops/r23_yolo/iter_1/events.jsonl
   curl.exe -s http://127.0.0.1:8080/ticks > loops/r23_yolo/iter_1/ticks.jsonl
   ```

---

## 3. CHECKLIST Y CONTROLES CRÍTICOS DE VALIDACIÓN

### A. Seguridad Absoluta
- `isDemoAccount == true` en el 100% de las muestras registradas (0 operaciones reales).
- `submode == "YOLO"` reportado en `/status` a lo largo de toda la ventana.

### B. Efecto Esperado de la Hipótesis
- Los vetos `RISK_LIMIT Trailing Profit Lock` y `Stop Loss Sesion Alcanzado` deben ser **0** en YOLO (en el baseline representaron el 74,3% de los vetos).
- La frecuencia de órdenes/taps en DEMO debe ser estrictamente observable (> 0 si las condiciones técnicas de mercado lo habilitan).

### C. Controles Negativos (Guards que NO Deben Desaparecer)
- `ADAPTIVE_BLOCK` debe seguir bloqueando anti-patrones de aprendizaje (~25% de vetos).
- `Stop Equity Absoluto`: Debe mantenerse activo si el capital llegara a bajar de $20.000.000\text{ COP}$.
- `Anti-Tilt`: Pausa de 35s tras fallo de martingala debe respetarse.
- `Cooldown YOLO`: Pausa entre operaciones consecutivas.
- `Veto V1` y `V2`, ventana sniper y regla de 1 orden por vela intactas.

### D. Métricas de Riesgo Nuevo
- Máxima racha de pérdidas consecutivas observada.
- Máximo drawdown registrado desde el balance pico de la sesión.

---

## 4. ENTREGABLES REQUERIDOS

1. `loops/r23_yolo/iter_1/LIVE_REPORT.md`: Métricas completas de la ventana (muestras, señales, taps, trades W/L, saldo inicial/final, distribución de vetos).
2. `loops/r23_yolo/iter_1/VALIDATOR_FINDINGS.md`: Análisis comparativo frente al baseline de R23B y frente a R23A (Conservative), evaluación de guards residuales y recomendación de veredicto (`KEEP` / `REVERT` / `MODIFY`).
3. Notificar a AGY (`w1:p1`) en cuanto concluya la prueba.
