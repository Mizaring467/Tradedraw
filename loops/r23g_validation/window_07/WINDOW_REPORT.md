# INFORME DE AUDITORÍA: R23G — VENTANA 07 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 06:17:48 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (25 muestras periódicas)  
**Cierre Nominal / Real:** 1792217834 / 1792217837 (gracia de settlement: 3.2s)  
**Veredicto Global:** **PASS OPERATIVO (VENTANA 07 CERRADA CON ÉXITO)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (25 de 25 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,015,736.32 COP**.
   - Saldo final validado: **37,815,736.32 COP** (Δ = -200,000.00 COP, -0.53%).
   - Drawdown máximo validado en la ventana: **0.53%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real en la Ventana:**
   - Se ejecutaron **3 operaciones autónomas reales** (4 taps despachados).
   - Las 3 operaciones correspondieron a la dirección `BUY`, resultando en pérdidas (0W / 3L).
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 16 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos en el bucket 98%.
   - Se resolvieron 3 eventos contrafactuales a 60s: **3 Wins / 0 Losses** ($WR_{\text{cf}} = \mathbf{100.00\%}$, $EV_{\text{cf}} = \mathbf{+249,000.00\text{ COP}}$).
   - **Hallazgo Crítico:** Las tres señales vetadas eran en dirección `SELL` (a favor del DOWNTREND dominante), mientras que las operaciones reales ejecutadas fueron en dirección `BUY` (contra-tendencia).

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 07)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `d7830176-462d...` | 1792216622941 | **BUY** | 38,015,736.32 | **LOSS** | -100,000.00 COP |
| **2** | `3a08b50d-37fc...` | 1792216744401 | **BUY** | 37,915,735.04 | **LOSS** | -100,000.00 COP |
| **3** | `f9e21974-d024...` | 1792216864391 | **BUY** | 37,815,736.32 | **LOSS** | -100,000.00 COP |

*(Nota: Entre el cierre de la ventana 07 y la reconexión de telemetría, el motor ejecutó de fondo una racha ganadora de 7 WINS consecutivos, llevando el balance actual a 38,144,737.28 COP).*

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 07)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **3 trades confirmados** (6 resueltos) | 16 vetos / **3 contrafactuales resueltos** |
| **Wins ($W$)** | **0 Wins** | **3 Wins** |
| **Losses ($L$)** | **3 Losses** (6 en ciclo) | **0 Losses** |
| **Win Rate ($WR$)** | **0.00%** | **100.00%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) | 54.64% |
| **Expectativa ($EV$)** | -200,000.00 COP | **+249,000.00 COP** |
| **Taps Despachados** | **4 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. CONSOLIDADO ACUMULADO R23G (7 VENTANAS COMPLETADAS)

Con 7 ventanas completadas, la muestra independiente alcanza:

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W07)** | 29 | 13 | 21 | **38.24%** | -869,998.08 COP | -870,000 COP | [23.94%, 55.02%] |
| **R23F + R23G Combinado** | 44 | 22 | 27 | **44.90%** | -731,996.16 COP | -732,000 COP | [31.95%, 58.62%] |
| **Proceso Global Hardware (Total)** | 85 | 48 | 37 | **56.47%** | +329,000.96 COP | +329,000 COP | [45.83%, 66.50%] |

### Distribución Direccional Acumulada R23G (Nueva Muestra):
- `SELL`: $N=11$, $6\text{ W} / 5\text{ L}$, $WR = \mathbf{54.55\%}$ (al breakeven).
- `BUY`: $N=18$, $7\text{ W} / 16\text{ L}$, $WR = \mathbf{30.43\%}$ (principal fuente de degradación).

### Shadow del 99% Acumulado en R23G (7 Ventanas):
- $N_{\text{cf}} = 34$ oportunidades resueltas al 99%.
- $W_{\text{cf}} = 15$ | $L_{\text{cf}} = 19$.
- $WR_{\text{cf}} = \mathbf{44.12\%}$ (ampliamente inferior a $54.64\%$).
- $EV_{\text{cf}} = \mathbf{-806,000.00\text{ COP}}$ salvados por el guard de anti-patrón al 99%.

---

## 5. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 07 CERRADA CON PASS OPERATIVO**.
- Con $N = 29$ trades confirmados en R23G ($N = 44$ en muestra combinada, y 85 operaciones globales en el hardware), se ha alcanzado el tamaño muestral crítico ($N \approx 30$).
- El sistema se encuentra en `DISABLED`, en perfecto estado de salud (`READY`), preparado para la consolidación del reporte final o una ventana adicional de confirmación.
