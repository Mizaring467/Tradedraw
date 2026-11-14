# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 3 (CUENTA DEMO)

**Fecha:** 2026-10-16 10:24:36 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=15, buildId=R23C-PHASE3, gitSha=b08459e  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (116 muestras de telemetría)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 116 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,342,735.36 COP, saldo final = 38,242,736.64 COP (Δ = -99,998.72 COP).
3. **Ejecución Autónoma:** 2 taps despachados en la ventana (acumulado histórico del proceso: 8). 1 operaciones autónomas registradas.
4. **Identidad de Build:** La APK en ejecución reportó versionCode=15, buildId="R23C-PHASE3", gitSha="b08459e".
5. **Decisiones Observadas:** Se registraron 118 decisiones con `decision_id` determinista.
6. **Paridad Live ↔ Replay:** 1 exact matches de 1 pareadas (100.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 811
- Señales generadas: 300
- Decisiones registradas: 118
- Operaciones DEMO ejecutadas: 1
- Resoluciones: W=0, L=0 (WR = 0.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23c_phase3_validation/window_03\live_samples.jsonl`
- Muestras de `/status`: `loops/r23c_phase3_validation/window_03\status_samples.jsonl`
- Eventos de decisión: `loops/r23c_phase3_validation/window_03\events.jsonl`
- Decisiones con `decision_id`: `loops/r23c_phase3_validation/window_03\decisions.csv`
- Operaciones ejecutadas: `loops/r23c_phase3_validation/window_03\trades.csv`
- Ticks capturados en crudo: `loops/r23c_phase3_validation/window_03\ticks_live.csv`
- Comparación de replay: `loops/r23c_phase3_validation/window_03\replay_comparison.json` y `loops/r23c_phase3_validation/window_03\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
