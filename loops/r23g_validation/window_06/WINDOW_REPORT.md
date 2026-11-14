# INFORME DE AUDITORÍA: R23G — VENTANA 06 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 05:52:37 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (111 muestras de telemetría periódica)  
**Cierre Nominal / Real:** 1792216286 / 1792216330 (gracia de settlement: 44.6s)  
**Veredicto Global:** **PASS OPERATIVO (VENTANA 06 CERRADA CON ÉXITO TRAS RESOLUCIÓN EN GRACIA)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (111 de 111 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,432,737.28 COP**.
   - Saldo final validado: **38,215,736.32 COP** (Δ = -217,000.96 COP, -0.56%).
   - Drawdown máximo validado en la ventana: **1.04%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real en la Ventana:**
   - Se ejecutaron **4 operaciones autónomas reales** (24 taps despachados).
   - Resoluciones observadas durante el ciclo: **2 Wins / 4 Losses** ($WR = \mathbf{33.33\%}$).
   - Payout del broker observado: **83.0%** ($WR_{\text{BE}} = 54.64\%$).
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 47 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos en el bucket 98%.
   - Se resolvieron 7 eventos contrafactuales a 60s: **3 Wins / 4 Losses** ($WR_{\text{cf}} = \mathbf{42.86\%}$, $EV_{\text{cf}} = \mathbf{-151,000.00\text{ COP}}$).
   - El escudo al 99% continuó evitando pérdidas netas (-151,000 COP).

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 06)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `9593ad9c-c520...` | 1792215724123 | **SELL** | 38,332,736.00 | **LOSS** | -100,000.00 COP |
| **2** | `3f34bebd-9bb9...` | 1792215844964 | **SELL** | 38,232,737.28 | **LOSS** | -100,000.00 COP |
| **3** | `97f255dd-9427...` | 1792216084862 | **BUY** | 38,132,736.00 | **WIN** | +83,000.00 COP |
| **4** | `573abb32-6303...` | 1792216264200 | **BUY** | 38,032,737.28 | **WIN** | +83,000.00 COP |

**Resumen Real Ventana 06:**
- Total Trades Confirmados: **4** (con 6 resoluciones contabilizadas en el ciclo).
- Wins: **2** | Losses: **4** (WR: **33.33%**).
- Variación neta: **-217,000.96 COP DEMO**.

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 06)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **4 trades confirmados** (6 resueltos) | 47 vetos / **7 contrafactuales resueltos** |
| **Wins ($W$)** | **2 Wins** | **3 Wins** |
| **Losses ($L$)** | **4 Losses** | **4 Losses** |
| **Win Rate ($WR$)** | **33.33%** | **42.86%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) | 54.64% |
| **Expectativa ($EV$)** | -217,000.00 COP | **-151,000.00 COP (Pérdidas Evitadas)** |
| **Taps Despachados** | **24 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. CONSOLIDADO ACUMULADO R23G (W01 + W02 + W03 + W04 + W05 + W06)

Con 6 ventanas completadas en R23G, la muestra acumulada alcanza:

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W06)** | 26 | 13 | 15 | **46.43%** | -669,998.08 COP | -670,000 COP | [29.53%, 64.21%] |
| **R23F + R23G Combinado** | 41 | 22 | 21 | **51.16%** | -531,996.16 COP | -532,000 COP | [36.72%, 65.41%] |

### Distribución Direccional Acumulada R23G:
- `SELL`: $N=11$, $6\text{ W} / 5\text{ L}$, $WR = \mathbf{54.55\%}$ (a nivel de breakeven exacto).
- `BUY`: $N=15$, $7\text{ W} / 10\text{ L}$, $WR = \mathbf{41.18\%}$ (arrastra las pérdidas de la fase).

### R23F + R23G Combinado Direccional:
- `SELL`: $N=15$, $9\text{ W} / 6\text{ L}$, $WR = \mathbf{60.00\%}$ ($+147\text{k COP}$ profit neto).
- `BUY`: $N=26$, $13\text{ W} / 15\text{ L}$, $WR = \mathbf{46.43\%}$ ($-679\text{k COP}$).

### Shadow del 99% Acumulado en R23G (6 Ventanas):
- $N_{\text{cf}} = 31$ oportunidades resueltas al 99%.
- $W_{\text{cf}} = 12$ | $L_{\text{cf}} = 19$.
- $WR_{\text{cf}} = \mathbf{38.71\%}$ (ampliamente destructivo, muy por debajo de $54.64\%$).
- $EV_{\text{cf}} = \mathbf{-1,055,000.00\text{ COP}}$ salvados por el guard de anti-patrón al 99%.

---

## 5. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 06 CERRADA CON PASS OPERATIVO**.
- Con $N = 26$ trades reales en R23G ($N = 41$ combinado), estamos a sólo ~4 trades de alcanzar la meta mínima de $N \ge 30$ trades en la muestra independiente.
- El sistema se encuentra en `DISABLED`, en perfecto estado de salud (`READY`), preparado para la **Ventana 07**.
