# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 1 (CUENTA DEMO)

**Fecha:** 2026-10-15 22:48:24 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=10, buildId=R23-AUTO-ITER1, gitSha=7a37032  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (117 muestras de telemetría)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 117 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,054,735.36 COP, saldo final = 38,036,736.00 COP (Δ = -17,999.36 COP).
3. **Ejecución Autónoma:** 4 taps despachados a la interfaz DEMO. 2 operaciones autónomas registradas.
4. **Identidad de Build:** La APK en ejecución reportó versionCode=10, buildId="R23-AUTO-ITER1".
5. **Decisiones Observadas:** Se registraron 28 decisiones con `decision_id` determinista.
6. **Paridad Live ↔ Replay:** 0 exact matches de 4 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 259
- Señales generadas: 28
- Decisiones registradas: 28
- Operaciones DEMO ejecutadas: 2
- Resoluciones: W=1, L=1 (WR = 50.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23_autonomous/iter_1\live_samples.jsonl`
- Muestras de `/status`: `loops/r23_autonomous/iter_1\status_samples.jsonl`
- Eventos de decisión: `loops/r23_autonomous/iter_1\events.jsonl`
- Decisiones con `decision_id`: `loops/r23_autonomous/iter_1\decisions.csv`
- Operaciones ejecutadas: `loops/r23_autonomous/iter_1\trades.csv`
- Ticks capturados en crudo: `loops/r23_autonomous/iter_1\ticks_live.csv`
- Comparación de replay: `loops/r23_autonomous/iter_1\replay_comparison.json` y `loops/r23_autonomous/iter_1\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
