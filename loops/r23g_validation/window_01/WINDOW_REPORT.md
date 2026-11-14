# INFORME DE AUDITORÍA: R23G — VENTANA 01 DE 10 (CUENTA DEMO)

**Fecha:** 2026-10-17 03:24:13 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (45 muestras de telemetría periódica)  
**Cierre Nominal / Real:** 1792207320 / 1792207404 (gracia de settlement: 84.1s)  
**Veredicto Global:** **PASS OPERATIVO / STOP DE SEGURIDAD POR HARDWARE USB**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (45 de 45 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial: **38,421,736.96 COP**.
   - Saldo final validado: **38,221,736.96 COP** (Δ = -200,000.00 COP, -0.52%).
   - Drawdown máximo validado en la ventana: **0.52%** (ampliamente dentro del límite de 3.5%).
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real:**
   - Se ejecutaron **2 trades autónomos reales** durante los 20 minutos de la ventana:
     - Trade 1: `b50a3800-06ed-412a-8765-65baa0bcc315` (BUY) $\rightarrow$ **LOSS** (-100,001.28 COP).
     - Trade 2: `fe869c31-08e4-4ea4-966e-ba5536517cc3` (BUY) $\rightarrow$ **LOSS** (-100,000.00 COP).
   - Wins: 0 | Losses: 2 | WR: **0.00%**.
   - Taps despachados en la ventana: 4 taps.
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 37 vetos de `ADAPTIVE_BLOCK` observados, 100% correspondientes a similitud $\ge 99.0\%$ (`[sim:99%]`). Cero bloqueos observados en el bucket 98%.
   - Se resolvieron 3 eventos contrafactuales a 60s: **0 Wins / 3 Losses** ($WR_{\text{cf}} = \mathbf{0.00\%}$, $EV_{\text{cf}} = \mathbf{-300,000.00\text{ COP}}$).
   - El escudo al 99% salvó a la cuenta de encajar -300,000 COP adicionales en pérdidas.
5. **Incidencia de Conectividad al Cierre:**
   - Inmediatamente tras la expiración del settlement grace de la ventana 01, se detectó desconexión física del cable USB del Xiaomi Redmi A1 hacia el PC host (`Redmi A1 Present: False` en PnP). Requiere reconexión física del cable antes de lanzar la Ventana 02.

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (VENTANA 01)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `b50a3800-06ed-412a...` | 1792206304247 | **BUY** | 38,321,735.68 | **LOSS** | -100,001.28 COP |
| **2** | `fe869c31-08e4-4ea4...` | 1792206486195 | **BUY** | 38,221,736.96 | **LOSS** | -100,000.00 COP |

*(Nota: El registro `3417933c-888e...` presente en el ring-buffer del engine correspondía al último trade de la ventana 03 de R23F previo al inicio de esta ventana).*

---

## 3. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES VETADOS (VENTANA 01)

| Métrica | Órdenes Reales Aceptadas (Liberadas) | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK` 99%) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | **2 trades confirmados** | 37 vetos / **3 contrafactuales resueltos** |
| **Wins ($W$)** | **0 Wins** | **0 Wins** |
| **Losses ($L$)** | **2 Losses** | **3 Losses** |
| **Win Rate ($WR$)** | **0.00%** | **0.00%** |
| **Breakeven Teórico** | 54.95% ($b \approx 0.82$) | 54.95% |
| **Expectativa ($EV$)** | -200,000.00 COP | **-300,000.00 COP (Pérdidas Evitadas)** |
| **Taps Despachados** | **4 taps** | **0 taps (100% aislados)** |
| **Similitud Promedio** | **< 99.0% (Bucket 98% liberado)** | **100% en Bucket 99.0%** |

---

## 4. ESTADÍSTICA ACUMULADA SEGÚN PREREGISTRO R23G

De acuerdo con el preregistro de R23G, se desglosan los tres paneles independientes:

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01)** | 2 | 0 | 2 | **0.00%** | -200,000.00 COP | -200,000 COP | [0.00%, 65.79%] |
| **R23F + R23G Combinado** | 17 | 9 | 8 | **52.94%** | -61,998.08 COP | -62,000 COP | [30.96%, 73.84%] |

### Distribución Direccional Acumulada R23G (Nueva Muestra):
- `BUY`: $N=2$, $0\text{ W} / 2\text{ L}$, $WR = 0.00\%$, $\Delta = -200,000$ COP.
- `SELL`: $N=0$.

---

## 5. REPRODUCIBILIDAD LIVE $\leftrightarrow$ REPLAY
- Ticks capturados en crudo: **3,249 ticks**.
- Tasa de paridad exacta en el comparador de trades: **100.0%** (1/1 pares coincidentes). Veredicto Replay: **PASS**.

---

## 6. CONCLUSIÓN Y ESTADO ACTUAL
- **Veredicto:** **VENTANA 01 COMPLETADA Y AUDITADA.**
- **Estado del Hardware:** Requiere reconexión física del cable USB del Xiaomi Redmi A1 para restaurar el bridge ADB y continuar con la **Ventana 02**.
