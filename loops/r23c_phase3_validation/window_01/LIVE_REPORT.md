# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 1 (CUENTA DEMO)

**Fecha:** 2026-10-16 09:37:16 UTC  
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
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,276,736.00 COP, saldo final = 38,359,736.32 COP (Δ = +83,000.32 COP).
3. **Ejecución Autónoma:** 2 taps despachados en la ventana (acumulado histórico del proceso: 2). 1 operaciones autónomas registradas.
4. **Identidad de Build:** La APK en ejecución reportó versionCode=15, buildId="R23C-PHASE3", gitSha="b08459e".
5. **Decisiones Observadas:** Se registraron 62 decisiones con `decision_id` determinista.
6. **Paridad Live ↔ Replay:** NO EVALUABLE (0 pares evaluados, 1 elementos sin replay correspondiente).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 263
- Señales generadas: 62
- Decisiones registradas: 62
- Operaciones DEMO ejecutadas: 1
- Resoluciones: W=1, L=0 (WR = 100.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23c_phase3_validation/window_01\live_samples.jsonl`
- Muestras de `/status`: `loops/r23c_phase3_validation/window_01\status_samples.jsonl`
- Eventos de decisión: `loops/r23c_phase3_validation/window_01\events.jsonl`
- Decisiones con `decision_id`: `loops/r23c_phase3_validation/window_01\decisions.csv`
- Operaciones ejecutadas: `loops/r23c_phase3_validation/window_01\trades.csv`
- Ticks capturados en crudo: `loops/r23c_phase3_validation/window_01\ticks_live.csv`
- Comparación de replay: `loops/r23c_phase3_validation/window_01\replay_comparison.json` y `loops/r23c_phase3_validation/window_01\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
