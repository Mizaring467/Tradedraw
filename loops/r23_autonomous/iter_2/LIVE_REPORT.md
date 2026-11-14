# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 2 (CUENTA DEMO)

**Fecha:** 2026-10-16 01:27:12 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=11, buildId=R23-AUTO-ITER2, gitSha=  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (95 muestras de telemetría)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 95 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,164,736.00 COP, saldo final = 38,164,736.00 COP (Δ = +0.00 COP).
3. **Ejecución Autónoma:** 12 taps despachados a la interfaz DEMO. 0 operaciones autónomas registradas.
4. **Identidad de Build:** La APK en ejecución reportó versionCode=11, buildId="R23-AUTO-ITER2".
5. **Decisiones Observadas:** Se registraron 79 decisiones con `decision_id` determinista.
6. **Paridad Live ↔ Replay:** 0 exact matches de 2 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 1558
- Señales generadas: 405
- Decisiones registradas: 79
- Operaciones DEMO ejecutadas: 0
- Resoluciones: W=0, L=0 (WR = 0.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23_autonomous/iter_2\live_samples.jsonl`
- Muestras de `/status`: `loops/r23_autonomous/iter_2\status_samples.jsonl`
- Eventos de decisión: `loops/r23_autonomous/iter_2\events.jsonl`
- Decisiones con `decision_id`: `loops/r23_autonomous/iter_2\decisions.csv`
- Operaciones ejecutadas: `loops/r23_autonomous/iter_2\trades.csv`
- Ticks capturados en crudo: `loops/r23_autonomous/iter_2\ticks_live.csv`
- Comparación de replay: `loops/r23_autonomous/iter_2\replay_comparison.json` y `loops/r23_autonomous/iter_2\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
