# INFORME DE AUDITORÍA: R23E — VENTANA 05 DE 06 (CUENTA DEMO)

**Fecha:** 2026-10-17 00:34:00 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `buildId = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (69 muestras de telemetría)  
**Cierre Nominal / Real:** 1792197153 / 1792197164 (settlement grace ejecutado: 11.4s)  
**Veredicto Global:** **PASS IMPECABLE (100% DE CRITERIOS AUDITADOS CUMPLIDOS)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (69 de 69 lecturas periódicas).
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
   - Se evaluaron 87 decisiones con veto `ADAPTIVE_BLOCK`.
   - Se deduplicaron ráfagas y se registraron **7 operaciones contrafactuales shadow independientes**.
   - 6 operaciones contrafactuales fueron resueltas contra los 3,975 ticks de mercado en vivo (`ticks_live.csv`).
   - 1 operación quedó como pendiente/VOID por proximidad al corte nominal.
   - Resultado: **5 WINS / 1 LOSS** $\implies$ **$WR_{\text{cf}} = 83.33\%$**, $EV = +315,000.00$ COP.

---

## 2. TABLA COMPARATIVA: TRADES REALES VS. CONTRAFACTUALES (VENTANA 05)

| Métrica | Órdenes Reales Aceptadas | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | 0 | 87 decisiones / **7 contrafactuales únicos** |
| **Taps Despachados** | **0** | **0 (Cero taps absolutos)** |
| **Impacto en Saldo** | **+0.00 COP** | **0.00 COP (Cero impacto en balance)** |
| **Wins ($W$)** | 0 | **5** (CF_WIN) |
| **Losses ($L$)** | 0 | **1** (CF_LOSS) |
| **Sin Resolver / Pendientes** | 0 | **1** (al corte final) |
| **Win Rate ($WR$)** | **N/A** | **83.33%** (Breakeven ref: 54.64%) |
| **EV Teórico ($EV$)** | **+0.00 COP** | **+315,000.00 COP** |
| **Estado en `trades.csv`** | 0 registros | **0 registros (Aislamiento verificado)** |

---

## 3. AUDITORÍA CRÍTICA CONTRA LOS 6 CRITERIOS OBLIGATORIOS

| Criterio | Requisito | Estado | Evidencia Observada en W05 |
| :--- | :--- | :---: | :--- |
| **1. Cero Taps Contrafactuales** | Los vetos nunca generan taps; `tapsDespachados` solo sube en órdenes reales. | **PASS** | `tapsDespachados = 2` constante en las 69 muestras (Δtaps = 0). Cero taps por vetos. |
| **2. Cero Impacto en Balance** | Los contrafactuales no alteran el saldo de la cuenta DEMO. | **PASS** | Saldo inicial 38,283,735.04 = Saldo final 38,283,735.04 (Δ = +0.00 COP). Drawdown = 0.00%. |
| **3. Cero Impacto en Contadores** | `totalWins` y `totalLosses` del motor no se modifican por contrafactuales. | **PASS** | Contadores del motor permanecieron en W=0, L=1 durante toda la ventana. |
| **4. Aislamiento de `trade_id`** | Los contrafactuales no aparecen en `trades.csv`. | **PASS** | `trades.csv` contiene únicamente la cabecera (0 filas de trades reales). |
| **5. Deduplicación por Vela/Dirección** | No permitir solapamientos en 60s por dirección. | **PASS** | 80 eventos solapados en ráfaga fueron correctamente deduplicados; solo 7 pasaron al CSV. |
| **6. Resolución Contrafactual Válida** | Cada contrafactual con `entry_price`, `exit_price`, `result` y `theoretical_ev`. | **PASS** | 6 resueltos con ticks reales a 8 decimales; 5 WIN (+83,000 COP c/u), 1 LOSS (-100,000 COP c/u). |

---

## 4. DESGLOSE DEL PIPELINE CONTRAFACTUAL SHADOW (W05)

### A. Registro Individual (`counterfactual_adaptive_block.csv`)
1. **CF-61c800e2... (1792195741906):** `BUY`  | Entry: 641.867304490 | Exit: 641.867304985 | **WIN**  | Score: 82 pts | Sim: 98% | EV: +83,000 COP
2. **CF-d7c3a839... (1792195985907):** `BUY`  | Entry: 641.867305700 | Exit: 641.867305755 | **WIN**  | Score: 95 pts | Sim: 99% | EV: +83,000 COP
3. **CF-b59bee89... (1792196222709):** `BUY`  | Entry: 641.867305680 | Exit: 641.867305365 | **LOSS** | Score: 80 pts | Sim: 99% | EV: -100,000 COP
4. **CF-ca708508... (1792196464310):** `SELL` | Entry: 641.867305380 | Exit: 641.867305010 | **WIN**  | Score: 90 pts | Sim: 99% | EV: +83,000 COP
5. **CF-37b28273... (1792196585904):** `SELL` | Entry: 641.867304735 | Exit: 641.867304630 | **WIN**  | Score: 95 pts | Sim: 98% | EV: +83,000 COP
6. **CF-5ab80703... (1792196645905):** `BUY`  | Entry: 641.867304630 | Exit: 641.867304680 | **WIN**  | Score: 75 pts | Sim: 99% | EV: +83,000 COP
7. **CF-70af4ae5... (1792196765905):** `SELL` | Entry: 641.867305120 | Exit: [Sin Tick]     | **PEND** | Score: 95 pts | Sim: 99% | EV: 0.0 COP

---

## 5. TABLA COMPARATIVA CONSOLIDADA ACUMULADA (W01 a W05)

| Métrica Consolidada | W01 | W02 | W03 | W04 | W05 | **Acumulado (W01 a W05)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Tiempo de Auditoría** | 20 min | 20 min | 20 min | 20 min | 20 min | **100.0 min (442 muestras)** |
| **Ticks de Mercado** | 4,324 | 4,494 | 4,875 | 4,920 | 3,975 | **22,588 ticks** |
| **Decisiones Evaluadas** | 81 | 114 | 60 | 58 | 87 | **400 decisiones** |
| **Vetos `ADAPTIVE_BLOCK`** | 75 | 89 | 60 | 58 | 87 | **369 vetos** |
| **Contrafactuales Deduplicados**| 9 | 8 | 8 | 6 | 7 | **38 contrafactuales** |
| **Contrafactuales Resueltos ($N$)**| **8** | **7** | **7** | **5** | **6** | **33 resueltos** (5 voids/pendientes) |
| **CF Wins ($W_{\text{cf}}$)** | 4 | 4 | 4 | 2 | 5 | **19 Wins** |
| **CF Losses ($L_{\text{cf}}$)** | 4 | 3 | 3 | 3 | 1 | **14 Losses** |
| **CF Win Rate ($WR_{\text{cf}}$)** | **50.00%** | **57.14%** | **57.14%** | **40.00%** | **83.33%** | **57.58%** |
| **Breakeven de Referencia** | 54.95% | 54.95% | 54.64% | 54.64% | 54.64% | **~54.76%** |
| **EV Teórico Contrafactual** | -72k COP | +28k COP | +32k COP | -134k COP | +315k COP | **+169,000.00 COP** |
| **Taps Reales Despachados** | 2 (W01) | 0 | 0 | 0 | 0 | **2 taps (0 por contrafactuales)** |
| **Saldo Real DEMO** | -18k COP | +0 COP | +0 COP | +0 COP | +0 COP | **-18,001.92 COP (Inmune)** |

---

## 6. SEGMENTACIÓN ACUMULADA (N = 33 RESUELTOS)

### A. Por Dirección
- **SELL:** $N = 8$ resueltos $\implies$ **5 W / 3 L (WR = 62.50%)** $\implies$ EV Teórico: **+112,000 COP** (Por encima de breakeven).
- **BUY:** $N = 25$ resueltos $\implies$ **14 W / 11 L (WR = 56.00%)** $\implies$ EV Teórico: **+57,000 COP** (Por encima de breakeven).

### B. Por Similitud
- **Similitud 98%:** $N = 13$ resueltos $\implies$ **9 W / 4 L (WR = 69.23%)** $\implies$ EV Teórico: **+360,000 COP** (Muy por encima de breakeven).
- **Similitud 99%:** $N = 20$ resueltos $\implies$ **10 W / 10 L (WR = 50.00%)** $\implies$ EV Teórico: **-191,000 COP** (Por debajo de breakeven 54.76%).

---

## 7. OBSERVACIÓN CLAVE DE PROGRESO DE CAMPAÑA
La muestra resuelta acumulada ha alcanzado **$N = 33$ contrafactuales**.  
Queda únicamente la **Ventana 06 de 06** para completar el máximo de 6 ventanas predefinido en la spec (120 minutos de auditoría total) y emitir el reporte final consolidado `AGGREGATE_REPORT.md` con el veredicto definitivo.
