# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 5 (CUENTA DEMO)

**Fecha:** 2026-10-16 08:00:50 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=14, buildId=R23-YOLO-ITER2, gitSha=ac079fd  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (117 muestras de telemetría)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 117 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,628,736.00 COP, saldo final = 38,410,736.64 COP (Δ = -217,999.36 COP).
3. **Ejecución Autónoma:** 6 taps despachados en la ventana (acumulado histórico del proceso: 76). 3 operaciones autónomas registradas.
4. **Identidad de Build:** La APK en ejecución reportó versionCode=14, buildId="R23-YOLO-ITER2", gitSha="ac079fd".
5. **Decisiones Observadas:** Se registraron 63 decisiones con `decision_id` determinista.
6. **Paridad Live ↔ Replay:** 0 exact matches de 2 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 2257
- Señales generadas: 729
- Decisiones registradas: 63
- Operaciones DEMO ejecutadas: 3
- Resoluciones: W=0, L=3 (WR = 0.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23c_yolo_validation/window_05\live_samples.jsonl`
- Muestras de `/status`: `loops/r23c_yolo_validation/window_05\status_samples.jsonl`
- Eventos de decisión: `loops/r23c_yolo_validation/window_05\events.jsonl`
- Decisiones con `decision_id`: `loops/r23c_yolo_validation/window_05\decisions.csv`
- Operaciones ejecutadas: `loops/r23c_yolo_validation/window_05\trades.csv`
- Ticks capturados en crudo: `loops/r23c_yolo_validation/window_05\ticks_live.csv`
- Comparación de replay: `loops/r23c_yolo_validation/window_05\replay_comparison.json` y `loops/r23c_yolo_validation/window_05\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
