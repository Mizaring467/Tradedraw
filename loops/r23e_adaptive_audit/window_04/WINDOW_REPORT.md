# INFORME DE AUDITORÍA: R23E — VENTANA 04 DE 06 (CUENTA DEMO)

**Fecha:** 2026-10-17 00:10:00 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `buildId = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (99 muestras de telemetría a ~10s)  
**Cierre Nominal / Real:** 1792195792 / 1792195803 (settlement grace ejecutado: 11.8s)  
**Veredicto Global:** **PASS IMPECABLE (100% DE CRITERIOS AUDITADOS CUMPLIDOS)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (99 de 99 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance e Inmunidad:**
   - Saldo inicial: **38,283,735.04 COP**.
   - Saldo final validado: **38,283,735.04 COP** (Δ = +0.00 COP).
   - Drawdown máximo validado: **0.00%**.
   - Outliers de telemetría filtrados: 0.
3. **Ejecución y Aislamiento de Taps:**
   - Taps despachados durante la ventana: **0 taps** (acumulado histórico congelado en 2 taps procedentes de W01).
   - Operaciones reales ejecutadas: 0 trades (`trades.csv` con 0 filas de datos).
4. **Validación del Pipeline Contrafactual Shadow:**
   - Se evaluaron 58 decisiones con veto `ADAPTIVE_BLOCK`.
   - Se deduplicaron ráfagas y se registraron **6 operaciones contrafactuales shadow independientes**.
   - 5 operaciones contrafactuales fueron resueltas contra los 4,920 ticks de mercado en vivo (`ticks_live.csv`).
   - 1 operación quedó como pendiente/VOID por proximidad al corte nominal.
   - Resultado: **2 WINS / 3 LOSSES** $\implies$ **$WR_{\text{cf}} = 40.00\%$**, $EV = -134,000.00$ COP.

---

## 2. TABLA COMPARATIVA: TRADES REALES VS. CONTRAFACTUALES (VENTANA 04)

| Métrica | Órdenes Reales Aceptadas | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | 0 | 58 decisiones / **6 contrafactuales únicos** |
| **Taps Despachados** | **0** | **0 (Cero taps absolutos)** |
| **Impacto en Saldo** | **+0.00 COP** | **0.00 COP (Cero impacto en balance)** |
| **Wins ($W$)** | 0 | **2** (CF_WIN) |
| **Losses ($L$)** | 0 | **3** (CF_LOSS) |
| **Sin Resolver / Pendientes** | 0 | **1** (al corte final) |
| **Win Rate ($WR$)** | **N/A** | **40.00%** (Breakeven ref: 54.64%) |
| **EV Teórico ($EV$)** | **+0.00 COP** | **-134,000.00 COP** |
| **Estado en `trades.csv`** | 0 registros | **0 registros (Aislamiento verificado)** |

---

## 3. AUDITORÍA CRÍTICA CONTRA LOS 6 CRITERIOS OBLIGATORIOS

| Criterio | Requisito | Estado | Evidencia Observada en W04 |
| :--- | :--- | :---: | :--- |
| **1. Cero Taps Contrafactuales** | Los vetos nunca generan taps; `tapsDespachados` solo sube en órdenes reales. | **PASS** | `tapsDespachados = 2` constante en las 99 muestras (Δtaps = 0). Cero taps por vetos. |
| **2. Cero Impacto en Balance** | Los contrafactuales no alteran el saldo de la cuenta DEMO. | **PASS** | Saldo inicial 38,283,735.04 = Saldo final 38,283,735.04 (Δ = +0.00 COP). Drawdown = 0.00%. |
| **3. Cero Impacto en Contadores** | `totalWins` y `totalLosses` del motor no se modifican por contrafactuales. | **PASS** | Contadores del motor permanecieron en W=0, L=1 durante toda la ventana. |
| **4. Aislamiento de `trade_id`** | Los contrafactuales no aparecen en `trades.csv`. | **PASS** | `trades.csv` contiene únicamente la cabecera (0 filas de trades reales). |
| **5. Deduplicación por Vela/Dirección** | No permitir solapamientos en 60s por dirección. | **PASS** | 52 eventos solapados en ráfaga fueron correctamente deduplicados; solo 6 pasaron al CSV. |
| **6. Resolución Contrafactual Válida** | Cada contrafactual con `entry_price`, `exit_price`, `result` y `theoretical_ev`. | **PASS** | 5 resueltos con ticks reales a 8 decimales; 2 WIN (+83,000 COP c/u), 3 LOSS (-100,000 COP c/u). |

---

## 4. DESGLOSE DEL PIPELINE CONTRAFACTUAL SHADOW (W04)

### A. Registro Individual (`counterfactual_adaptive_block.csv`)
1. **CF-fb8d53de... (1792194304329):** `BUY`  | Entry: 641.867301645 | Exit: 641.867301455 | **LOSS** | Score: 95 pts | Sim: 98% | EV: -100,000 COP
2. **CF-8b935cc0... (1792195081109):** `SELL` | Entry: 641.867301125 | Exit: 641.867301700 | **LOSS** | Score: 95 pts | Sim: 99% | EV: -100,000 COP
3. **CF-5dac18f5... (1792195325513):** `BUY`  | Entry: 641.867302765 | Exit: 641.867302600 | **LOSS** | Score: 95 pts | Sim: 98% | EV: -100,000 COP
4. **CF-f42316ba... (1792195445909):** `BUY`  | Entry: 641.867302490 | Exit: 641.867303180 | **WIN**  | Score: 95 pts | Sim: 99% | EV: +83,000 COP
5. **CF-b67601f7... (1792195563099):** `BUY`  | Entry: 641.867303795 | Exit: 641.867304575 | **WIN**  | Score: 95 pts | Sim: 99% | EV: +83,000 COP
6. **CF-61c800e2... (1792195741906):** `BUY`  | Entry: 641.867304490 | Exit: [Sin Tick]     | **PEND** | Score: 82 pts | Sim: 98% | EV: 0.0 COP

---

## 5. TABLA COMPARATIVA CONSOLIDADA ACUMULADA (W01 + W02 + W03 + W04)

| Métrica Consolidada | W01 | W02 | W03 | W04 | **Acumulado (W01 a W04)** |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Tiempo de Auditoría** | 20.0 min | 20.0 min | 20.0 min | 20.0 min | **80.0 min (373 muestras)** |
| **Ticks de Mercado** | 4,324 | 4,494 | 4,875 | 4,920 | **18,613 ticks** |
| **Decisiones Evaluadas** | 81 | 114 | 60 | 58 | **313 decisiones** |
| **Vetos `ADAPTIVE_BLOCK`** | 75 | 89 | 60 | 58 | **282 vetos** |
| **Contrafactuales Deduplicados**| 9 | 8 | 8 | 6 | **31 contrafactuales** |
| **Contrafactuales Resueltos ($N$)**| **8** | **7** | **7** | **5** | **27 resueltos** (4 voids/pendientes) |
| **CF Wins ($W_{\text{cf}}$)** | 4 | 4 | 4 | 2 | **14 Wins** |
| **CF Losses ($L_{\text{cf}}$)** | 4 | 3 | 3 | 3 | **13 Losses** |
| **CF Win Rate ($WR_{\text{cf}}$)** | **50.00%** | **57.14%** | **57.14%** | **40.00%** | **51.85%** |
| **Breakeven de Referencia** | 54.95% | 54.95% | 54.64% | 54.64% | **~54.80%** |
| **EV Teórico Contrafactual** | -72,000 COP | +28,000 COP | +32,000 COP | -134,000 COP | **-146,000.00 COP** |
| **Taps Reales Despachados** | 2 (W01) | 0 | 0 | 0 | **2 taps (0 por contrafactuales)** |
| **Saldo Real DEMO** | -18.001,92 COP | +0,00 COP | +0,00 COP | +0,00 COP | **-18.001,92 COP (Inmune)** |

---

## 6. SEGMENTACIÓN ACUMULADA (N = 27 RESUELTOS)

### A. Por Dirección
- **SELL:** $N = 6$ resueltos $\implies$ **3 W / 3 L (WR = 50.00%)** $\implies$ EV Teórico: **-54,000 COP**.
- **BUY:** $N = 21$ resueltos $\implies$ **11 W / 10 L (WR = 52.38%)** $\implies$ EV Teórico: **-92,000 COP**.

### B. Por Similitud
- **Similitud 98%:** $N = 11$ resueltos $\implies$ **7 W / 4 L (WR = 63.64%)** $\implies$ EV Teórico: **+194,000 COP** (Por encima de breakeven 54.80%).
- **Similitud 99%:** $N = 16$ resueltos $\implies$ **7 W / 9 L (WR = 43.75%)** $\implies$ EV Teórico: **-340,000 COP** (Claramente por debajo de breakeven).

---

## 7. OBSERVACIÓN CLAVE DE PROGRESO DE CAMPAÑA
La acumulación ha alcanzado $N = 27$ contrafactuales resueltos tras 4 ventanas (80 minutos).  
Para alcanzar la meta de $N \ge 50$ o completar el cupo máximo de 6 ventanas, restan **2 ventanas (Ventanas 05 y 06)**.
