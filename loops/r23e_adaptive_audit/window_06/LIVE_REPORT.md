# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 6 (CUENTA DEMO)

**Fecha:** 2026-10-17 00:56:39 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=17, buildId=R23E-SHADOW, gitSha=08073c3  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (98 muestras de telemetría)  
**Cierre Nominal / Real:** 1792198567 / 1792198576 (gracia de settlement: 8.7s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 98 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,283,735.04 COP, saldo final VALIDADO = 38,283,735.04 COP (Δ = +0.00 COP). Drawdown validado: 0.00%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 0 taps despachados en la ventana (acumulado histórico del proceso: 2). 0 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=17, buildId="R23E-SHADOW", gitSha="08073c3".
6. **Decisiones Observadas:** Se registraron 69 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** NO EVALUABLE (0 pares evaluados, 0 elementos sin replay correspondiente).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 2274
- Señales generadas: 613
- Decisiones registradas: 69
- Operaciones DEMO ejecutadas: 0
- Resoluciones: W=0, L=0 (WR = 0.0%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 42
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 37
- Contrafactuales registrados: 5
- Resueltos: 5 (CF_W=2, CF_L=3, CF_VOID=0)
- **CF_WR = 40.00%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -26,800.00 COP** | CF_EV total = -134,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=5 W=2 L=3 VOID=0 WR=40.0%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [75,79]pts: n=1 W=1 L=0 VOID=0 WR=100.0%
  - [80,84]pts: n=1 W=0 L=1 VOID=0 WR=0.0%
  - [85,100]pts: n=3 W=1 L=2 VOID=0 WR=33.3%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23e_adaptive_audit/window_06\live_samples.jsonl`
- Muestras de `/status`: `loops/r23e_adaptive_audit/window_06\status_samples.jsonl`
- Eventos de decisión: `loops/r23e_adaptive_audit/window_06\events.jsonl`
- Decisiones con `decision_id`: `loops/r23e_adaptive_audit/window_06\decisions.csv`
- Operaciones ejecutadas: `loops/r23e_adaptive_audit/window_06\trades.csv`
- Ticks capturados en crudo: `loops/r23e_adaptive_audit/window_06\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23e_adaptive_audit/window_06\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23e_adaptive_audit/window_06\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23e_adaptive_audit/window_06\replay_comparison.json` y `loops/r23e_adaptive_audit/window_06\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
