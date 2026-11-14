# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 2 (CUENTA DEMO)

**Fecha:** 2026-10-16 17:38:04 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=16, buildId=R23D-TELEMETRY, gitSha=5b2245f  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (98 muestras de telemetría)  
**Cierre Nominal / Real:** 1792172257 / 1792172261 (gracia de settlement: 4.8s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 98 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,473,735.68 COP, saldo final VALIDADO = 38,373,736.96 COP (Δ = -99,998.72 COP). Drawdown validado: 0.26%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 2 taps despachados en la ventana (acumulado histórico del proceso: 16). 1 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=16, buildId="R23D-TELEMETRY", gitSha="5b2245f".
6. **Decisiones Observadas:** Se registraron 76 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 0 exact matches de 1 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 896
- Señales generadas: 187
- Decisiones registradas: 76
- Operaciones DEMO ejecutadas: 1
- Resoluciones: W=0, L=1 (WR = 0.0%)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23d_validation/window_02\live_samples.jsonl`
- Muestras de `/status`: `loops/r23d_validation/window_02\status_samples.jsonl`
- Eventos de decisión: `loops/r23d_validation/window_02\events.jsonl`
- Decisiones con `decision_id`: `loops/r23d_validation/window_02\decisions.csv`
- Operaciones ejecutadas: `loops/r23d_validation/window_02\trades.csv`
- Ticks capturados en crudo: `loops/r23d_validation/window_02\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23d_validation/window_02\window_metadata.json`
- Comparación de replay: `loops/r23d_validation/window_02\replay_comparison.json` y `loops/r23d_validation/window_02\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
