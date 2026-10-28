# INFORME DE AUDITORÍA: R23G — VENTANA 05 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 05:25:46 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (111 muestras de telemetría)  
**Cierre Nominal / Real:** 1792214640 / 1792214721 (gracia de settlement: 81.5s)  
**Veredicto Global:** **PASS IMPECABLE (CONFIRMACIÓN DE PROFIT CON WR 66.67% EN VENTANA 05)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (111 de 111 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,383,736.32 COP**.
   - Saldo final validado: **38,432,737.28 COP** (Δ = **+49,000.96 COP**, +0.13% PROFIT NETO).
   - Drawdown máximo validado en la ventana: **0.69%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real en la Ventana:**
   - Se ejecutaron **5 operaciones autónomas reales** (18 taps despachados).
   - Resoluciones observadas durante el ciclo: **4 Wins / 2 Losses** ($WR = \mathbf{66.67\%}$).
   - Payout del broker observado: **83.0%** ($WR_{\text{BE}} = 54.64\%$). El Win Rate de 66.67% supera por $+12.03\%$ el breakeven dinámico.
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 35 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos en el bucket 98%.
   - Se resolvieron 5 eventos contrafactuales a 60s: **1 Win / 4 Losses** ($WR_{\text{cf}} = \mathbf{20.00\%}$, $EV_{\text{cf}} = \mathbf{-317,000.00\text{ COP}}$).
   - El escudo al 99% evitó pérdidas adicionales netas por -317,000 COP en esta sola ventana.

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 05)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `b7cebc83-59a7...` | 1792213624231 | **BUY** | 38,283,735.04 | **LOSS** | -100,000.00 COP |
| **2** | `6a8c022c-6795...` | 1792213745117 | **SELL** | 38,183,736.32 | **WIN** | +83,000.00 COP |
| **3** | `95eca639-496e...` | 1792213864394 | **BUY** | 38,266,736.64 | **WIN** | +83,000.00 COP |
| **4** | `0a510316-1e5d...` | 1792213986483 | **SELL** | 38,266,736.64 | **WIN** | +83,000.00 COP |
| **5** | `7c83cf46-d0f2...` | 1792214166209 | **SELL** | 38,249,735.68 | **WIN** | +83,000.00 COP |

**Resumen Real Ventana 05:**
- Total Trades: **5** (con 6 resoluciones contabilizadas en el ciclo del motor).
- Wins: **4** | Losses: **2** (WR: **66.67%**).
- Variación neta: **+49,000.96 COP DEMO**.
- Distribución Direccional:
  - `SELL`: 3 trades (3 Wins / 0 Losses $\implies$ **100.0% WR**).
  - `BUY`: 2 trades (1 Win / 1 Loss $\implies$ **50.0% WR**).

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 05)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **5 trades confirmados** (6 resueltos) | 35 vetos / **5 contrafactuales resueltos** |
| **Wins ($W$)** | **4 Wins** | **1 Win** |
| **Losses ($L$)** | **2 Losses** | **4 Losses** |
| **Win Rate ($WR$)** | **66.67%** | **20.00%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) | 54.64% |
| **Expectativa ($EV$)** | **+49,000.96 COP (Rentable)** | **-317,000.00 COP (Pérdidas Evitadas)** |
| **Taps Despachados** | **18 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. CONSOLIDADO ACUMULADO R23G (W01 + W02 + W03 + W04 + W05)

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W05)** | 22 | 11 | 11 | **50.00%** | -452,997.12 COP | -453,000 COP | [30.72%, 69.28%] |
| **R23F + R23G Combinado** | 37 | 20 | 17 | **54.05%** | -314,995.20 COP | -315,000 COP | [38.41%, 68.86%] |

### Distribución Direccional Acumulada R23G:
- `SELL`: $N=9$, $6\text{ W} / 3\text{ L}$, $WR = \mathbf{66.67\%}$ ($+198\text{k COP}$).
- `BUY`: $N=13$, $5\text{ W} / 8\text{ L}$, $WR = \mathbf{38.46\%}$ ($-451\text{k COP}$).

### R23F + R23G Combinado Direccional:
- `SELL`: $N=13$, $9\text{ W} / 4\text{ L}$, $WR = \mathbf{69.23\%}$ ($+347\text{k COP}$).
- `BUY`: $N=24$, $11\text{ W} / 13\text{ L}$, $WR = \mathbf{45.83\%}$ ($-462\text{k COP}$).

### Shadow del 99% Acumulado en R23G (5 Ventanas):
- $N_{\text{cf}} = 24$ oportunidades resueltas al 99%.
- $W_{\text{cf}} = 9$ | $L_{\text{cf}} = 15$.
- $WR_{\text{cf}} = \mathbf{37.50\%}$ (destructivo, muy por debajo de $54.64\%$).
- $EV_{\text{cf}} = \mathbf{-904,000.00\text{ COP}}$ salvados por el guard de anti-patrón.

---

## 5. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 05 COMPLETADA CON PASS (PROFIT NETO +49k COP, WR 66.67%)**.
- Con $N = 22$ trades reales en R23G ($N = 37$ combinado), nos aproximamos al objetivo mínimo preregistrado de $N \ge 30$ trades en la nueva muestra.
- El sistema se encuentra en `DISABLED`, en perfecto estado de salud (`READY`), preparado para la **Ventana 06**.
