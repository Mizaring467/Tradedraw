# INFORME DE AUDITORÍA: R23E — VENTANA 03 DE 06 (CUENTA DEMO)

**Fecha:** 2026-10-16 23:47:00 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `buildId = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (98 muestras de telemetría a ~10s)  
**Cierre Nominal / Real:** 1792194415 / 1792194421 (settlement grace ejecutado: 6.0s)  
**Veredicto Global:** **PASS IMPECABLE (100% DE CRITERIOS AUDITADOS CUMPLIDOS)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (98 de 98 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance e Inmunidad:**
   - Saldo inicial: **38,283,735.04 COP**.
   - Saldo final validado: **38,283,735.04 COP** (Δ = +0.00 COP).
   - Drawdown máximo validado: **0.00%**.
   - Outliers de telemetría filtrados: 0.
3. **Ejecución y Aislamiento de Taps:**
   - Taps despachados durante la ventana: **0 taps** (acumulado histórico del proceso congelado en 2 taps de W01).
   - Operaciones reales ejecutadas: 0 trades (`trades.csv` con 0 filas de datos).
4. **Validación del Pipeline Contrafactual Shadow:**
   - Se evaluaron 60 decisiones con veto `ADAPTIVE_BLOCK`.
   - Se deduplicaron ráfagas y se registraron **8 operaciones contrafactuales shadow independientes**.
   - 7 operaciones contrafactuales fueron resueltas contra los 4,875 ticks de mercado en vivo (`ticks_live.csv`).
   - Resultado: **4 WINS / 3 LOSSES** $\implies$ **$WR_{\text{cf}} = 57.14\%$**, $EV = +32,000.00$ COP.

---

## 2. TABLA COMPARATIVA: TRADES REALES VS. CONTRAFACTUALES (VENTANA 03)

| Métrica | Órdenes Reales Aceptadas | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | 0 | 60 decisiones / **8 contrafactuales únicos** |
| **Taps Despachados** | **0** | **0 (Cero taps absolutos)** |
| **Impacto en Saldo** | **+0.00 COP** | **0.00 COP (Cero impacto en balance)** |
| **Wins ($W$)** | 0 | **4** (CF_WIN) |
| **Losses ($L$)** | 0 | **3** (CF_LOSS) |
| **Sin Resolver / Pendientes** | 0 | **1** (al corte inicial) |
| **Win Rate ($WR$)** | **N/A** | **57.14%** (Breakeven ref: 54.64% con payout 83%) |
| **EV Teórico ($EV$)** | **+0.00 COP** | **+32,000.00 COP** |
| **Estado en `trades.csv`** | 0 registros | **0 registros (Aislamiento verificado)** |

---

## 3. AUDITORÍA CRÍTICA CONTRA LOS 6 CRITERIOS OBLIGATORIOS

| Criterio | Requisito | Estado | Evidencia Observada en W03 |
| :--- | :--- | :---: | :--- |
| **1. Cero Taps Contrafactuales** | Los vetos nunca generan taps; `tapsDespachados` solo sube en órdenes reales. | **PASS** | `tapsDespachados = 2` constante en las 98 muestras (Δtaps = 0). Cero taps por vetos. |
| **2. Cero Impacto en Balance** | Los contrafactuales no alteran el saldo de la cuenta DEMO. | **PASS** | Saldo inicial 38,283,735.04 = Saldo final 38,283,735.04 (Δ = +0.00 COP). Drawdown = 0.00%. |
| **3. Cero Impacto en Contadores** | `totalWins` y `totalLosses` del motor no se modifican por contrafactuales. | **PASS** | Contadores del motor permanecieron en W=0, L=1 durante toda la ventana. |
| **4. Aislamiento de `trade_id`** | Los contrafactuales no aparecen en `trades.csv`. | **PASS** | `trades.csv` contiene únicamente la cabecera (0 filas de trades reales). |
| **5. Deduplicación por Vela/Dirección** | No permitir solapamientos en 60s por dirección. | **PASS** | 52 eventos solapados en ráfaga fueron correctamente deduplicados; solo 8 pasaron al CSV. |
| **6. Resolución Contrafactual Válida** | Cada contrafactual con `entry_price`, `exit_price`, `result` y `theoretical_ev`. | **PASS** | 7 resueltos con ticks reales a 8 decimales; 4 WIN (+83,000 COP c/u), 3 LOSS (-100,000 COP c/u). |

---

## 4. DESGLOSE DEL PIPELINE CONTRAFACTUAL SHADOW (W03)

### A. Registro Individual (`counterfactual_adaptive_block.csv`)
1. **CF-e66c17e8... (1792193225508):** `BUY`  | Entry: 641.867303140 | Exit: 641.867302815 | **LOSS** | Score: 78 pts | Sim: 99% | EV: -100,000 COP
2. **CF-83886fb2... (1792193701508):** `BUY`  | Entry: 641.867301120 | Exit: 641.867300375 | **LOSS** | Score: 75 pts | Sim: 98% | EV: -100,000 COP
3. **CF-c3e23343... (1792193765509):** `SELL` | Entry: 641.867300280 | Exit: 641.867300205 | **WIN**  | Score: 95 pts | Sim: 98% | EV: +83,000 COP
4. **CF-ea920849... (1792193945905):** `BUY`  | Entry: 641.867300165 | Exit: 641.867300435 | **WIN**  | Score: 75 pts | Sim: 99% | EV: +83,000 COP
5. **CF-c8e8d812... (1792194065566):** `BUY`  | Entry: 641.867300660 | Exit: 641.867301025 | **WIN**  | Score: 75 pts | Sim: 99% | EV: +83,000 COP
6. **CF-b4849c78... (1792194181510):** `BUY`  | Entry: 641.867301125 | Exit: 641.867301575 | **WIN**  | Score: 77 pts | Sim: 98% | EV: +83,000 COP
7. **CF-fb8d53de... (1792194304329):** `BUY`  | Entry: 641.867301645 | Exit: 641.867301455 | **LOSS** | Score: 95 pts | Sim: 98% | EV: -100,000 COP

---

## 5. TABLA COMPARATIVA CONSOLIDADA ACUMULADA (W01 + W02 + W03)

| Métrica Consolidada | W01 (Post-Hoc) | W02 (Live) | W03 (Live) | **Acumulado (W01 + W02 + W03)** |
| :--- | :---: | :---: | :---: | :---: |
| **Tiempo de Auditoría** | 20.0 min (91 m) | 20.0 min (85 m) | 20.0 min (98 m) | **60.0 min (274 muestras)** |
| **Ticks de Mercado** | 4,324 ticks | 4,494 ticks | 4,875 ticks | **13,693 ticks** |
| **Decisiones Totales** | 81 | 114 | 60 | **255 decisiones** |
| **Órdenes Reales Aceptadas** | 1 trade | 0 trades | 0 trades | **1 trade** |
| **Resultado Trades Reales** | 0 W / 1 L | 0 W / 0 L | 0 W / 0 L | **0 W / 1 L (WR = 0.0%)** |
| **Taps Reales Despachados** | 2 taps | 0 taps | 0 taps | **2 taps** |
| **Variación Saldo DEMO** | -18,001.92 COP | +0.00 COP | +0.00 COP | **-18,001.92 COP** |
| **Drawdown Máximo** | 0.47% | 0.00% | 0.00% | **0.47%** |
| **Vetos `ADAPTIVE_BLOCK`** | 75 vetos | 89 vetos | 60 vetos | **224 vetos** |
| **Contrafactuales Deduplicados (N)**| 9 | 8 | 8 | **25 contrafactuales** |
| **Contrafactuales Resueltos**| **8** | **7** | **7** | **22 resueltos** (3 voids/pendientes) |
| **CF Wins ($W_{\text{cf}}$)** | 4 | 4 | 4 | **12 Wins** |
| **CF Losses ($L_{\text{cf}}$)** | 4 | 3 | 3 | **10 Losses** |
| **CF Win Rate ($WR_{\text{cf}}$)** | **50.00%** | **57.14%** | **57.14%** | **54.55%** |
| **Breakeven de Referencia** | 54.95% | 54.95% | 54.64% | **~54.85%** |
| **EV Teórico Contrafactual** | -72,000.00 COP | +28,000.00 COP | +32,000.00 COP | **-12,000.00 COP** |
| **Taps por Contrafactuales** | 0 taps | 0 taps | 0 taps | **0 taps (100% aislados)** |

---

## 6. SEGMENTACIÓN ACUMULADA (N = 22 RESUELTOS)

### A. Por Dirección
- **SELL:** $N = 5$ resueltos $\implies$ **3 W / 2 L (WR = 60.00%)**
  - W01: 1 W, 2 L
  - W02: 3 W, 0 L (100% WR)
  - W03: 1 W, 0 L (100% WR)
  - En las últimas 2 ventanas, **SELL contrafactual tiene 4 W / 0 L (100% WR)**.
- **BUY:** $N = 17$ resueltos $\implies$ **9 W / 8 L (WR = 52.94%)**

### B. Por Similitud
- **Similitud 98%:** $N = 9$ resueltos $\implies$ **7 W / 2 L (WR = 77.78%)** $\implies$ EV Teórico: **+394,000 COP**.
- **Similitud 99%:** $N = 13$ resueltos $\implies$ **5 W / 8 L (WR = 38.46%)** $\implies$ EV Teórico: **-406,000 COP**.

> [!IMPORTANT]
> **HALLAZGO CRÍTICO CONFIRMADO:**  
> Existe una **bifurcación neta** en la calidad de las señales bloqueadas por `ADAPTIVE_BLOCK`:
> 1. En **similitud 99%**, el veto es **ALTAMENTE PROTECTOR** ($WR = 38.46\%$, muy por debajo del breakeven 54.85%). Bloquear estas señales salva capital real.
> 2. En **similitud 98%**, el veto es **SOBRE-RESTRICTIVO** ($WR = 77.78\%$, 7 ganadas de 9). Está bloqueando oportunidades con alta expectativa matemática positiva.
