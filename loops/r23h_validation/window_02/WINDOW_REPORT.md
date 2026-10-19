# INFORME DE AUDITORÍA: R23H — VENTANA 02 DE 3 (CUENTA DEMO)
## VALIDACIÓN LIVE DEL PARCHE BUY EXHAUSTION CLIMAX

**Fecha:** 2026-10-18 00:28:01 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:pB`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 19` | `versionName = "1.9-r23h-exhaustion"` | `buildId = "R23H-BUY-EXHAUSTION"` | `gitSha = "aac1df3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (92 muestras periódicas)  
**Cierre Nominal / Real:** 1792283259 / 1792283263 (gracia de settlement: 3.6s)  
**Veredicto Global:** **PASS OPERATIVO RIGUROSO (66.67% WR REAL | +64,000.00 COP DEMO)**  

---

## 1. RESUMEN EJECUTIVO Y AUDITORÍA DE SEGURIDAD

1. **Seguridad DEMO Inquebrantable:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (92 de 92 lecturas periódicas).
   - Fondos reales comprometidos: **$0.00 COP (0.0%)**. Cero cuentas reales expuestas.
2. **Dinámica de Balance y Preservación de Capital:**
   - Saldo inicial al arrancar W02: **39,179,735.04 COP DEMO** (estrictamente continuo con el cierre de W01).
   - Saldo final validado tras liquidación: **39,243,735.04 COP DEMO**.
   - **Variación Neta de Capital:** **+64,000.00 COP DEMO** (+0.16%).
   - Drawdown máximo validado en la ventana: **0.67%** (límite pre-registrado: 3.5%).
   - Outliers de telemetría: 0 filtrados.
3. **Depuración Forense de Fila Residual en `trades.csv`:**
   - La fila 1 en `trades.csv` (`daa48cfa-fd53-4709-8300-0e1f63185cea`, ts: `1792280644113`) corresponde al último trade de la **Ventana 01** que permanecía en el ring-buffer de `/events` del APK.
   - Ocurrió 23.6 minutos antes del arranque de W02. En W02 **no despachó taps** (los taps subieron de 8 a 14, exactamente 6 taps para 3 órdenes) y **no movió saldo**.
   - Queda formalmente clasificada como **DUPLICADO INTER-VENTANA DE BUFFER** y purgada del conteo activo de W02.
4. **Ecuación Canónica de Reconciliación en Ventana ($N = W + L + VOID$):**
   - **Total de Órdenes Reales en W02 ($N$):** **3 trades**.
   - **Victorias ($W$):** **2 Wins** (Trade 3 SELL, Trade 4 SELL).
   - **Derrotas ($L$):** **1 Loss** (Trade 2 BUY).
   - **Nulos ($VOID$):** **0 VOID**.
   - **Cumplimiento Canónico:** $3 = 2 + 1 + 0$ (**100% exacto**).
   - **Win Rate Real de Ventana:** **66.67%** (frente a breakeven de 54.95%).
5. **Efectividad del Guard `VETO_BUY_EXHAUSTION_CLIMAX`:**
   - **25 vetos activos registrados:**
     - 18 vetos en `UPTREND` con `callPower = 86%`.
     - 7 vetos en `UPTREND` con `callPower = 78%`.
   - Continúa impidiendo que el motor compre tardíamente en techos de agotamiento.
6. **Auditoría del Escudo `ADAPTIVE_BLOCK` (99%):**
   - 25 decisiones bloqueadas por similitud $\ge 99.0\%$. Cero vetos en el bucket 98%.
   - Contrafactual shadow a 60s: 3 resueltos (2 Wins / 1 Loss).

---

## 2. AUDITORÍA FORENSE TRADE POR TRADE (CANONICAL LEDGER W02)

| # | Trade ID | Decision ID | Timestamp (ms) | Acción | Balance Pre-Trade | Balance Post-Entrada | Balance Liquidado | Delta Balance Real | Resultado | Clasificación |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| *[0]* | `daa48cfa-fd53...` | `8a57a25e...` | 1792280644113 | *SELL* | 39,179,735.04 | 39,179,735.04 | 39,179,735.04 | $0.00 COP$ | — | *Residual W01 (Excluido)* |
| **1** | `1f8ab63b-f399-439d-8027-a48c0c2f7db5` | `3cf1896f...` | 1792282204776 | **BUY** | 39,179,735.04 | 39,079,736.32 | 39,079,736.32 | **-99,998.72 COP** | **LOSS** | Real W02 |
| **2** | `20d30e05-d87f-4217-94e2-67ad9da1f45c` | `1f9078dc...` | 1792282324774 | **SELL** | 39,079,736.32 | 38,979,737.60 | 39,161,735.68 | **+81,999.36 COP** | **WIN** | Real W02 |
| **3** | `1a26df52-5446-432d-aaaf-bb5b1c5d06a4` | `0bccc051...` | 1792282624088 | **SELL** | 39,161,735.68 | 39,061,736.96 | 39,243,735.04 | **+81,999.36 COP** | **WIN** | Real W02 |

### Verificación Aritmética de Balance:
$$\Delta\text{ Balance Teórico} = 2 \times (+81,999.36) + 1 \times (-99,998.72) = +163,998.72 - 99,998.72 = \mathbf{+64,000.00\text{ COP}}$$
$$\Delta\text{ Balance Real Medido} = 39,243,735.04 - 39,179,735.04 = \mathbf{+64,000.00\text{ COP}}$$
**Discrepancia aritmética:** **$0.00 COP (Cero absoluto)**.

---

## 3. AUDITORÍA CONTABLE: CONTADORES APK VS. HARDWARE REAL

1. **Taps Despachados:**
   - Inicio de W02: 8 taps acumulados.
   - Fin de W02: 14 taps acumulados.
   - Taps reales ejecutados en W02: **6 taps** (exactamente 2 taps por orden confirmada $\implies \mathbf{3\text{ órdenes reales}}$).
2. **Contadores de Resolución del APK:**
   - Al cierre, el APK reportó `W/L=3/1` (4 resoluciones registradas por el monitor al contabilizar la fila residual de segundo 0).
   - La realidad física del balance demuestra que sólo hubo 3 operaciones operativas en W02 ($2\text{ W} / 1\text{ L}$).

---

## 4. ESTADÍSTICA CONSOLIDADA ACUMULADA: FASE R23H (W01 + W02)

| Ventana | $N_{\text{real}}$ | Wins ($W$) | Losses ($L$) | Win Rate ($WR$) | Payout | Breakeven ($WR_{\text{BE}}$) | Delta Balance | Max Drawdown |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **W01** | 4 | 3 | 1 | **75.00%** | 82.0% | 54.95% | +148,999.68 COP | 0.89% |
| **W02** | 3 | 2 | 1 | **66.67%** | 82.0% | 54.95% | +64,000.00 COP | 0.67% |
| **TOTAL R23H** | **7** | **5** | **2** | **71.43%** | **82.0%** | **54.95%** | **+212,999.68 COP** | **0.89%** |

### Desglose Direccional R23H (W01 + W02):
- **`SELL`:** $N = 4$ ($3\text{ Wins} / 1\text{ Loss} \implies \mathbf{75.00\% WR}$, $+145,998.08$ COP).
- **`BUY`:** $N = 3$ ($2\text{ Wins} / 1\text{ Loss} \implies \mathbf{66.67\% WR}$, $+67,001.60$ COP).
- **Conclusión Estratégica:** En R23G, `BUY` acumuló 22 pérdidas con un desastroso 37.1% de WR. En R23H, gracias a los 64 vetos acumulados de `VETO_BUY_EXHAUSTION_CLIMAX` (39 en W01 + 25 en W02), `BUY` opera por encima de breakeven con un **66.67% de Win Rate**.

---

## 5. CONCLUSIÓN Y ESTADO DEL SISTEMA

1. **Validación Positiva Sostenida:** La campaña R23H acumula dos ventanas consecutivas con rentabilidad neta positiva (+213k COP) y Win Rate global del 71.43%.
2. **Protección Fail-Closed:** 100% DEMO sin anomalías de cuenta real ni outliers.
3. **Estado Actual:** El motor ha sido cambiado a `DISABLED` y se encuentra en estado `READY` para la **Ventana 03** (cierre de campaña R23H).
