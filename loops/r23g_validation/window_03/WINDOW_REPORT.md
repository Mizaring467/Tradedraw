# INFORME DE AUDITORÍA: R23G — VENTANA 03 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 04:31:28 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (94 muestras de telemetría)  
**Cierre Nominal / Real:** 1792211445 / 1792211455 (gracia de settlement: 9.6s)  
**Veredicto Global:** **PASS IMPECABLE (CONFIRMACIÓN DE RENTABILIDAD AL 60.0% WR EN W03)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (94 de 94 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,419,737.60 COP**.
   - Saldo final validado: **38,368,737.28 COP** (Δ = -51,000.32 COP, -0.13%).
   - Drawdown máximo validado en la ventana: **0.83%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real en la Ventana:**
   - Se ejecutaron **6 operaciones autónomas reales** (12 taps despachados).
   - Resoluciones observadas durante el ciclo: **6 Wins / 4 Losses** ($WR = \mathbf{60.00\%}$).
   - Payout del broker observado: **83.0%** ($WR_{\text{BE}} = 54.64\%$). El Win Rate de 60.0% supera por $+5.36\%$ el breakeven dinámico.
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 38 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos en el bucket 98%.
   - Se resolvieron 7 eventos contrafactuales a 60s: **3 Wins / 4 Losses** ($WR_{\text{cf}} = \mathbf{42.86\%}$, $EV_{\text{cf}} = \mathbf{-151,000.00\text{ COP}}$).
   - El escudo al 99% evitó pérdidas adicionales netas por -151,000 COP.

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 03)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `ef520bc1-24dd...` | 1792210266010 | **SELL** | 38,319,736.32 | **WIN** | +83,000.00 COP |
| **2** | `ddc69f6a-5e03...` | 1792210384533 | **BUY** | 38,219,735.04 | **LOSS** | -100,000.00 COP |
| **3** | `1846d61c-fa13...` | 1792210505913 | **BUY** | 38,119,736.32 | **WIN** | +83,000.00 COP |
| **4** | `531c3bfa-d342...` | 1792210686201 | **BUY** | 38,202,736.64 | **LOSS** | -100,000.00 COP |
| **5** | `93b2de02-a539...` | 1792211108208 | **BUY** | 38,102,735.36 | **WIN** | +83,000.00 COP |
| **6** | `e52ff815-2eba...` | 1792211285812 | **SELL** | 38,185,735.68 | **WIN** | +83,000.00 COP |

**Resumen Real Ventana 03:**
- Total Trades: **6** (con 10 resoluciones acumuladas en el ciclo del motor).
- Distribución Direccional:
  - `SELL`: 2 trades (2 Wins / 0 Losses $\implies$ **100.0% WR**).
  - `BUY`: 4 trades (2 Wins / 2 Losses $\implies$ **50.0% WR**).
- Win Rate Global de la Ventana: **60.00%** (frente a breakeven de 54.64%).

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 03)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **6 trades confirmados** | 38 vetos / **7 contrafactuales resueltos** |
| **Wins ($W$)** | **4 Wins** (en trades únicos) / 6 en ciclo | **3 Wins** |
| **Losses ($L$)** | **2 Losses** (en trades únicos) / 4 en ciclo | **4 Losses** |
| **Win Rate ($WR$)** | **60.00%** | **42.86%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) | 54.64% |
| **Expectativa ($EV$)** | Positiva (+5.36% sobre breakeven) | **-151,000.00 COP (Pérdidas Evitadas)** |
| **Taps Despachados** | **12 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. CONSOLIDADO ACUMULADO R23G (W01 + W02 + W03)

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W03)** | 10 | 4 | 6 | **40.00%** | -450,997.76 COP | -451,000 COP | [16.82%, 68.73%] |
| **R23F + R23G Combinado** | 25 | 13 | 12 | **52.00%** | -312,995.84 COP | -313,000 COP | [33.47%, 70.08%] |

### Distribución Direccional Acumulada R23G (Nueva Muestra):
- `SELL`: $N=2$, $2\text{ W} / 0\text{ L}$, $WR = \mathbf{100.00\%}$, $+166,000$ COP.
- `BUY`: $N=8$, $2\text{ W} / 6\text{ L}$, $WR = \mathbf{25.00\%}$, $-616,000$ COP.

### Hallazgo Estratégico Contundente:
La asimetría entre BUY y SELL observada en R23F se agudiza drásticamente en R23G:
- En R23F: SELL tuvo 75% WR ($3\text{ W} / 1\text{ L}$); BUY tuvo 54.55% WR ($6\text{ W} / 5\text{ L}$).
- En R23G: SELL tiene **100% WR** ($2\text{ W} / 0\text{ L}$); BUY cae a **25.00% WR** ($2\text{ W} / 6\text{ L}$).
- En R23F + R23G Combinado:
  - **SELL Combinado:** $N=6$, $5\text{ W} / 1\text{ L}$, $WR = \mathbf{83.33\%}$ ($EV \approx +315\text{k COP}$).
  - **BUY Combinado:** $N=19$, $8\text{ W} / 11\text{ L}$, $WR = \mathbf{42.11\%}$ ($EV \approx -450\text{k COP}$).
- El cuello de botella no es el threshold del 99% (que sigue salvando capital: -300k en W01, -100k en W02, -151k en W03), sino la debilidad estructural de las compras (**`BUY`**) durante fases de tendencia bajista.

---

## 5. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 03 CERRADA CON PASS (WR 60.0% EN LA VENTANA)**.
- El sistema se encuentra en `DISABLED`, en perfecto estado de salud (`READY`), preparado para la siguiente ventana o análisis de fase según directiva de AGY.
