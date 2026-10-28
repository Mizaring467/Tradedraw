# INFORME DE AUDITORÍA: R23E — VENTANA 06 DE 06 (FINAL, CUENTA DEMO)

**Fecha:** 2026-10-17 00:57:00 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `buildId = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (98 muestras de telemetría)  
**Cierre Nominal / Real:** 1792198567 / 1792198576 (settlement grace ejecutado: 8.7s)  
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
   - Taps despachados durante la ventana: **0 taps** (acumulado histórico congelado en 2 taps procedentes de W01).
   - Operaciones reales ejecutadas: 0 trades (`trades.csv` con 0 filas de datos).
4. **Validación del Pipeline Contrafactual Shadow:**
   - Se evaluaron 69 decisiones con veto `ADAPTIVE_BLOCK`.
   - Se deduplicaron ráfagas y se registraron **5 operaciones contrafactuales shadow independientes**.
   - 5 operaciones contrafactuales fueron resueltas contra los 4,904 ticks de mercado en vivo (`ticks_live.csv`).
   - Resultado: **2 WINS / 3 LOSSES** $\implies$ **$WR_{\text{cf}} = 40.00\%$**, $EV = -134,000.00$ COP.

---

## 2. TABLA COMPARATIVA: TRADES REALES VS. CONTRAFACTUALES (VENTANA 06)

| Métrica | Órdenes Reales Aceptadas | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | 0 | 69 decisiones / **5 contrafactuales únicos** |
| **Taps Despachados** | **0** | **0 (Cero taps absolutos)** |
| **Impacto en Saldo** | **+0.00 COP** | **0.00 COP (Cero impacto en balance)** |
| **Wins ($W$)** | 0 | **2** (CF_WIN) |
| **Losses ($L$)** | 0 | **3** (CF_LOSS) |
| **Sin Resolver / Pendientes** | 0 | **0** |
| **Win Rate ($WR$)** | **N/A** | **40.00%** (Breakeven ref: 54.64%) |
| **EV Teórico ($EV$)** | **+0.00 COP** | **-134,000.00 COP** |
| **Estado en `trades.csv`** | 0 registros | **0 registros (Aislamiento verificado)** |

---

## 3. AUDITORÍA CRÍTICA CONTRA LOS 6 CRITERIOS OBLIGATORIOS

| Criterio | Requisito | Estado | Evidencia Observada en W06 |
| :--- | :--- | :---: | :--- |
| **1. Cero Taps Contrafactuales** | Los vetos nunca generan taps; `tapsDespachados` solo sube en órdenes reales. | **PASS** | `tapsDespachados = 2` constante en las 98 muestras (Δtaps = 0). Cero taps por vetos. |
| **2. Cero Impacto en Balance** | Los contrafactuales no alteran el saldo de la cuenta DEMO. | **PASS** | Saldo inicial 38,283,735.04 = Saldo final 38,283,735.04 (Δ = +0.00 COP). Drawdown = 0.00%. |
| **3. Cero Impacto en Contadores** | `totalWins` y `totalLosses` del motor no se modifican por contrafactuales. | **PASS** | Contadores del motor permanecieron en W=0, L=1 durante toda la ventana. |
| **4. Aislamiento de `trade_id`** | Los contrafactuales no aparecen en `trades.csv`. | **PASS** | `trades.csv` contiene únicamente la cabecera (0 filas de trades reales). |
| **5. Deduplicación por Vela/Dirección** | No permitir solapamientos en 60s por dirección. | **PASS** | 64 eventos solapados en ráfaga fueron correctamente deduplicados; solo 5 pasaron al CSV. |
| **6. Resolución Contrafactual Válida** | Cada contrafactual con `entry_price`, `exit_price`, `result` y `theoretical_ev`. | **PASS** | 5 resueltos con ticks reales a 8 decimales; 2 WIN (+83,000 COP c/u), 3 LOSS (-100,000 COP c/u). |

---

## 4. DESGLOSE DEL PIPELINE CONTRAFACTUAL SHADOW (W06)

### A. Registro Individual (`counterfactual_adaptive_block.csv`)
1. **CF-0078618e... (1792197125915):** `SELL` | Entry: 641.867304555 | Exit: 641.867305125 | **LOSS** | Score: 95 pts | Sim: 99% | EV: -100,000 COP
2. **CF-b449ff2c... (1792197964697):** `BUY`  | Entry: 641.867307340 | Exit: 641.867306940 | **LOSS** | Score: 88 pts | Sim: 99% | EV: -100,000 COP
3. **CF-f53a2928... (1792198025899):** `SELL` | Entry: 641.867306975 | Exit: 641.867307180 | **LOSS** | Score: 83 pts | Sim: 98% | EV: -100,000 COP
4. **CF-2ff3e310... (1792198205905):** `BUY`  | Entry: 641.867307305 | Exit: 641.867307800 | **WIN**  | Score: 75 pts | Sim: 99% | EV: +83,000 COP
5. **CF-83f13053... (1792198325992):** `BUY`  | Entry: 641.867307780 | Exit: 641.867308280 | **WIN**  | Score: 95 pts | Sim: 98% | EV: +83,000 COP
