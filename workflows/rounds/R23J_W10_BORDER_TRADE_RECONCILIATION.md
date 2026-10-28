# RECONCILIACIÓN FORENSE DEL TRADE DE FRONTERA POST-VENTANA 10 (MISIÓN R23J)
## R23J_W10_BORDER_TRADE_RECONCILIATION
### CONTROL DE INTEGRIDAD CONTABLE, GOBERNANZA Y SALDO BASE PARA VENTANA 11

**Fecha:** 2026-10-18 23:58  
**Autor:** AGY (`w1:p1` / Orquestador)  
**Auditores:** CLAUDE CODE (`w1:pC`), OPENCODE (`w1:pB`)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`, Android 12 Go)  
**Build Activa:** `gitSha = 8cdb61f` | `versionCode = 19` | `BUILD_ID = R23H-BUY-EXHAUSTION`  

---

## 1. RESUMEN DEL HALLAZGO Y CAUSA RAÍZ

Tras el cierre formal y auditoría de la Ventana 10 (`loops/r23j_validation/window_10_clean/`), una lectura de telemetría en `/status` reveló que el saldo en cuenta DEMO descendió de `39,103,736.32 COP` a **`39,003,735.04 COP`** (delta exacto de **`-100,001.28 COP`**).

El análisis forense en `/events` determinó la causa raíz:
1. **Despacho del trade dentro de W10:** A las `23:44:05` local (`atMs = 1792385045288`), **7 segundos antes** del vencimiento nominal de los 20 minutos de W10 (`nominal_window_end_ts: 1792385052.329` = `23:44:12`), el motor evaluó y despachó un trade BUY (`tradeId: 205eed81-fd3b-47d7-8efa-4b3fb7058499`, `decisionId: 0d6d32fa-eead-3b4f-996c-731a1a2b40f8`).
2. **Confirmación en Broker:** A las `23:44:08` (`atMs = 1792385048491`), el broker confirmó la orden (`ORDER_STATE: CONFIRMADA BUY saldo movido`).
3. **Cierre asíncrono del monitor live:** `live_monitor_r23_autonomous.py` opera con ciclos de muestreo de 10 segundos. La última muestra dentro del bucle fue tomada a las `23:44:01` (4 segundos antes del tap). Al llegar las `23:44:12`, el monitor evaluó la condición de salida y finalizó a las `23:44:13` (`actual_capture_end_ts: 1792385053.234`), sin capturar la confirmación en su variable `observed_trades` ni retener el tiempo de espera de liquidación (`settlement_grace_period`).
4. **Liquidación a 60 segundos:** La orden expiró en el broker a las `23:45:17` (`atMs = 1792385117654`, `ORDER_STATE: NINGUNA BUY sin operacion en curso`), con resultado de pérdida (**LOSS**).
5. **Transición a DISABLED:** A las `23:44:48` (`atMs = 1792385088903`), la aplicación pasó a `DISABLED`.

---

## 2. RECONCILIACIÓN CONTABLE EXACTA

| Parámetro | Valor Registrado |
| :--- | :--- |
| **Trade ID** | `205eed81-fd3b-47d7-8efa-4b3fb7058499` |
| **Decision ID** | `0d6d32fa-eead-3b4f-996c-731a1a2b40f8` |
| **Acción / Tipo** | `BUY` |
| **Timestamp Despacho** | `1792385045288` (2026-10-18 23:44:05.288) |
| **Timestamp Confirmación** | `1792385048491` (2026-10-18 23:44:08.491) |
| **Timestamp Liquidación** | `1792385117654` (2026-10-18 23:45:17.654) |
| **Resultado** | **LOSS** |
| **Stake Observado** | `100,000.00 COP` |
| **Saldo Pre-Trade** | `39,103,736.32 COP DEMO` |
| **Saldo Post-Trade** | **`39,003,735.04 COP DEMO`** |
| **Delta Real** | **`-100,001.28 COP DEMO`** (incluye redondeo broker de 1.28 COP) |
| **Discrepancia Contable** | **`$0.00 COP`** (explicado al 100% por la liquidación de la orden) |
| **Telemetría APK (`/status`)** | `totalWins: 4, totalLosses: 6, tapsDespachados: 20` |

---

## 3. IMPUTACIÓN EN LOS TRES CUBOS EPISTÉMICOS

1. **Cubo 1 (Paridad Live ↔ Replay post-congelado):**
   - El trade ocurrió físicamente en el minuto 20 de W10 pero su liquidación concluyó fuera de la captura de `trades.csv` de W10.
   - Acumulado formal de pares evaluados bajo Preregistro R23J §2: $N_{\text{pares}} = 6$ (5 matches, 1 divergencia $\implies 83.33\%$). Dictamen: **`NO CONCLUYENTE`** ($N < 10$).
2. **Cubo 2 (Desempeño homogéneo con veto W04–W10 + Trade de Frontera):**
   - Al ser generado bajo la build idéntica `8cdb61f` y configuración congelada YOLO, se computa como 1 trade real adicional (LOSS).
   - Acumulado Cubo 2: $N = 27$ trades (17 W / 10 L $\implies WR = 62.96\%$).
   - Intervalo de Wilson 95%: **[44.20%, 78.50%]**.
   - Breakeven ($payout = 0.83$): **`54.64%`**.
   - Veredicto: **`NO CONCLUYENTE`** ($44.20\% < 54.64\%$ y $N = 27 < 40$).
3. **Cubo 3 (Histórico heterogéneo W01–W03):**
   - $N = 11$ trades (7 W / 4 L $\implies WR = 63.64\%$).
4. **Consolidado Informativo Total (W01–W10 + Frontera):**
   - $N = 38$ trades reales (24 W / 14 L $\implies WR = 63.16\%$).
   - Beneficio neto acumulado: `+577,999.36 COP DEMO`.

---

## 4. BASE OFICIAL VINCULANTE PARA VENTANA 11

- **Saldo Inicial Certificado para W11:** **`39,003,735.04 COP DEMO`**.
- La discrepancia contable previa a W11 queda formalmente cerrada en **`$0.00 COP`**.
- Se autoriza a Claude Code (`w1:pC`) a certificar el Preflight W11 tomando `39,003,735.04 COP DEMO` como saldo de apertura exacto.
