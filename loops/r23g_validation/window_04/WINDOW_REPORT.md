# INFORME DE AUDITORÍA: R23G — VENTANA 04 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 04:59:55 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (97 muestras de telemetría periódica)  
**Cierre Nominal / Real:** 1792213154 / 1792213171 (gracia de settlement: 16.9s)  
**Veredicto Global:** **PASS OPERATIVO (VENTANA 04 CERRADA CON ÉXITO)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (97 de 97 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,434,736.64 COP**.
   - Saldo final validado: **38,383,736.32 COP** (Δ = -51,000.32 COP, -0.13%).
   - Drawdown máximo validado en la ventana: **1.04%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real en la Ventana:**
   - Se ejecutaron **7 operaciones autónomas reales** (14 taps despachados).
   - Resoluciones observadas: **3 Wins / 4 Losses** ($WR = \mathbf{42.86\%}$).
   - Payout del broker observado: **83.0%** ($WR_{\text{BE}} = 54.64\%$).
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 54 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos en el bucket 98%.
   - Se resolvieron 8 eventos contrafactuales a 60s: **4 Wins / 4 Losses** ($WR_{\text{cf}} = \mathbf{50.00\%}$, $EV_{\text{cf}} = \mathbf{-68,000.00\text{ COP}}$).
   - El escudo al 99% continuó evitando pérdidas netas (-68,000 COP).

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 04)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `a211bdfc-2eaf...` | 1792211943831 | **BUY** | 38,434,736.64 | **WIN** | +83,000.00 COP |
| **2** | `094cb9da-b361...` | 1792212064688 | **BUY** | 38,517,736.96 | **LOSS** | -100,000.00 COP |
| **3** | `b5465942-b515...` | 1792212423194 | **SELL** | 38,417,735.68 | **LOSS** | -100,000.00 COP |
| **4** | `3e7c8246-b4eb...` | 1792212544110 | **SELL** | 38,417,735.68 | **LOSS** | -100,000.00 COP |
| **5** | `abda4ca8-8e5c...` | 1792212666131 | **BUY** | 38,217,735.68 | **WIN** | +83,000.00 COP |
| **6** | `9860af90-3bf6...` | 1792212904283 | **SELL** | 38,300,736.00 | **WIN** | +83,000.00 COP |
| **7** | `f1f33034-4107...` | 1792213085049 | **SELL** | 38,383,736.32 | **LOSS** | -100,000.00 COP |

**Resumen Real Ventana 04:**
- Total Trades: **7**
- Wins: **3** | Losses: **4**
- Win Rate Real: **42.86%**
- Payout: **83.0%**

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 04)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **7 trades confirmados** | 54 vetos / **8 contrafactuales resueltos** |
| **Wins ($W$)** | **3 Wins** | **4 Wins** |
| **Losses ($L$)** | **4 Losses** | **4 Losses** |
| **Win Rate ($WR$)** | **42.86%** | **50.00%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) | 54.64% |
| **Expectativa ($EV$)** | -51,000.00 COP | **-68,000.00 COP (Pérdidas Evitadas)** |
| **Taps Despachados** | **14 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. CONSOLIDADO ACUMULADO R23G (W01 + W02 + W03 + W04)

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W04)** | 17 | 7 | 10 | **41.18%** | -501,998.08 COP | -502,000 COP | [21.61%, 64.00%] |
| **R23F + R23G Combinado** | 32 | 16 | 16 | **50.00%** | -363,996.16 COP | -364,000 COP | [33.64%, 66.36%] |

### Distribución Direccional Acumulada R23G (Nueva Muestra):
- `SELL`: $N=6$, $3\text{ W} / 3\text{ L}$, $WR = \mathbf{50.00\%}$, $-51,000$ COP.
- `BUY`: $N=11$, $4\text{ W} / 7\text{ L}$, $WR = \mathbf{36.36\%}$, $-451,000$ COP.

### R23F + R23G Combinado Direccional:
- `SELL`: $N=10$, $6\text{ W} / 4\text{ L}$, $WR = \mathbf{60.00\%}$ ($+198\text{k COP}$).
- `BUY`: $N=22$, $10\text{ W} / 12\text{ L}$, $WR = \mathbf{45.45\%}$ ($-562\text{k COP}$).

### Shadow del 99% Acumulado en R23G:
- $N_{\text{cf}} = 19$ oportunidades resueltas.
- $W_{\text{cf}} = 8$ | $L_{\text{cf}} = 11$.
- $WR_{\text{cf}} = \mathbf{42.11\%}$ (ampliamente inferior a $WR_{\text{BE}} = 54.64\%$).
- $EV_{\text{cf}} = \mathbf{-587,000.00\text{ COP}}$ (salvados por el guard).

---

## 5. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 04 COMPLETADA CON PASS OPERATIVO**.
- El sistema se encuentra en `DISABLED`, en perfecto estado de salud (`READY`), preparado para la siguiente ventana de validación prolongada.
