# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 21 (W21)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-20 16:21:00 -05:00 (21:21:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w21_output.txt` y telemetría in vivo (`adb forward tcp:8080`, `/status`, `/nodes`)  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 21)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA (13 PUNTOS)

| # | Parámetro Auditado | Requisito Previo Preregistro | Medición en Hardware Real | Veredicto |
|---|---|---|---|:---:|
| **01** | **Conectividad ADB** | Dispositivo Xiaomi online + forward 8080 | `UGU8KFUKM7E6CE8D` conectado (`device`), port forward tcp:8080 operativo | **PASS** |
| **02** | **Batería y Temperatura** | Nivel $\ge 20\%$ USB, $< 48.0^\circ\text{C}$ | `level: 100%`, `USB: true`, `status: 5` (full), `temp: 32.8°C`, `4366 mV` | **PASS** |
| **03** | **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **04** | **Seguridad de Cuenta (/status)** | `isDemoAccount == true` (estricto) | `/status`: `"isDemoAccount": true` ($0.00 COP reales comprometidos) | **PASS** |
| **05** | **Seguridad de Cuenta (/nodes)** | `isDemoAccount == true` en nodos UI | `/nodes`: `"isDemoAccount": true`, `"balance": 3.859373568E7` | **PASS** |
| **06** | **Saldo Inicial Certificado** | Continuidad exacta cierre W20 | `38,593,735.68 COP DEMO` | **PASS** |
| **07** | **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) vs W20 | **$0.00 COP** ($|38,593,735.68 - 38,593,735.68| = 0.00\text{ COP}$) | **PASS** |
| **08** | **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **09** | **Feed Websocket Fresco** | Conectado, tickAge < 3000 ms, activo | `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 91` | **PASS** |
| **10** | **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false` | **PASS** |
| **11** | **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []`, `sinceMs: 1792531220912` | **PASS** |
| **12** | **Estrategia y Vetos Congelados** | `AUTO_ADAPTIVE` + Veto Agotamiento | `"strategy": "AUTO_ADAPTIVE"`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **13** | **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 100% con alimentación USB activa (`scale: 100`, `status: 5` full, `level: 100`, `USB powered: true`).
- Voltaje nominal: 4366 mV.
- Térmico bajo control estricto: $32.8^\circ\text{C}$ (`temperature: 328` décimas de grado, margen holgado $< 48.0^\circ\text{C}$).

### 2.2 Foco de Interfaz y Broker
- Ventana focal activa en el dispositivo:
  `Window{45938f7 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Broker Binomo en pantalla completa y listo para operaciones autónomas DEMO.

### 2.3 Seguridad de Fondos y Validación Cruzada
- Endpoint `/status`:
  `"isDemoAccount": true`, `"balance": 3.859373568E7`.
- Endpoint `/nodes`:
  `"isDemoAccount": true`, `"activeAsset": "Crypto IDX"`, `"isSyntheticOrOTC": true`, `"balance": 3.859373568E7`, `"observedOrderAmount": 100000.0`.
- Cero fondos reales expuestos ($0.00 COP reales).

### 2.4 Cadena de Saldos y Continuidad Inter-Ventana
- `balance`: `3.859373568E7` $\implies$ **38,593,735.68 COP DEMO**.
- Coincidencia exacta al centavo con el saldo final auditado y certificado de la Ventana 20 (`workflows/rounds/R23K_CLAUDE_W20_AUDIT.md`).
- Discrepancia inter-ventana: **$0.00 COP**.
- Estado operativo seguro: `mode: DISABLED`, `orderState: NINGUNA`, `hasPendingTrade: false`.
- Feed websocket: `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 91 ms`, `socketTicks: 7`.
- Comparador v1 congelado: `scripts/validation/compare_live_replay.py` conserva su hash SHA-256 inmutable `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f`.

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se emite dictamen formal **PASS (READY)** para la Ventana 21:
1. Las condiciones de hardware, sistema operativo, app Android `8cdb61f` y broker cumplen al 100% los 13 criterios pre-registrados de R23K.
2. Cuenta DEMO confirmada de forma redundante en `/status` y `/nodes`.
3. Cadena de saldos sellada con continuidad exacta al centavo frente al cierre canónico de W20 ($0.00 COP de discrepancia).
4. Se autoriza formalmente la apertura y ejecución de la Ventana 21 limpia con el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
