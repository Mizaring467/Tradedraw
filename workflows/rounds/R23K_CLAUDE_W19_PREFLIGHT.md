# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 19 (W19)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-19 14:45:00 -05:00 (19:45:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w19_output.txt`  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 19)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA

| # | Parámetro Auditado | Requisito Previo Preregistro | Medición en Hardware Real | Veredicto |
|---|---|---|---|:---:|
| **01** | **Conectividad ADB** | Dispositivo online + forward 8080 | Conexión activa, port forward tcp:8080 operativo | **PASS** |
| **02** | **Batería y Temperatura** | Nivel $\ge 20\%$ USB, $< 48.0^\circ\text{C}$ | `level: 100%`, `USB: true`, `status: 2` (cargando), `temp: 34.6°C`, `4386 mV` | **PASS** |
| **03** | **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **04** | **Seguridad de Cuenta (/status)** | `isDemoAccount == true` (estricto) | `/status`: `"isDemoAccount": true` ($0.00 COP reales comprometidos) | **PASS** |
| **05** | **Seguridad de Cuenta (/nodes)** | `isDemoAccount == true` en nodos UI | `/nodes`: `"isDemoAccount": true`, `"balance": 3.886373632E7` | **PASS** |
| **06** | **Saldo Inicial Certificado** | Continuidad exacta cierre W18 | `38,863,736.32 COP DEMO` | **PASS** |
| **07** | **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) vs W18 | **$0.00 COP** ($38,863,736.32 - 38,863,736.32) | **PASS** |
| **08** | **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **09** | **Feed Websocket Fresco** | Conectado, tickAge < 3000 ms, activo | `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 374` | **PASS** |
| **10** | **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false` | **PASS** |
| **11** | **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []`, `sinceMs: 1792437111569` | **PASS** |
| **12** | **Estrategia y Vetos Congelados** | `AUTO_ADAPTIVE` + Veto Exgotamiento | `"strategy": "AUTO_ADAPTIVE"`, `"ultimoVeto": "VETO_BUY_EXHAUSTION_CLIMAX..."` | **PASS** |
| **13** | **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 100% con alimentación USB activa (`scale: 100`, `status: 2` cargando, `level: 100`).
- Voltaje nominal: 4386 mV.
- Térmico bajo control estricto: $34.6^\circ\text{C}$ (amplio margen seguro $< 48.0^\circ\text{C}$).

### 2.2 Foco de Interfaz y Broker
- Ventana focal activa en el dispositivo:
  `Window{fcedd08 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Broker Binomo en pantalla completa y listo para operaciones autónomas DEMO.

### 2.3 Seguridad de Fondos y Validación Cruzada
- Endpoint `/status`:
  `"isDemoAccount": true`, `"balance": 3.886373632E7`.
- Endpoint `/nodes`:
  `"isDemoAccount": true`, `"activeAsset": "Crypto IDX"`, `"isSyntheticOrOTC": true`, `"balance": 3.886373632E7`.
- Cero fondos reales expuestos ($0.00 COP reales).

### 2.4 Cadena de Saldos y Continuidad Inter-Ventana
- `balance`: `3.886373632E7` $\implies$ **38,863,736.32 COP DEMO**.
- Coincidencia exacta al centavo con el saldo final auditado de la Ventana 18 (`workflows/rounds/R23K_CLAUDE_W18_AUDIT.md`).
- Discrepancia inter-ventana: **$0.00 COP**.
- Estado operativo seguro: `mode: DISABLED`, `orderState: NINGUNA`, `hasPendingTrade: false`.
- Feed websocket: `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 374 ms`, `socketTicks: 7506`.
- Comparador v1 congelado: `scripts/validation/compare_live_replay.py` conserva su hash SHA-256 inmutable `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f`.

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se emite dictamen formal **PASS (READY)** para la Ventana 19:
1. Las condiciones de hardware, sistema operativo, app Android `8cdb61f` y broker cumplen al 100% los criterios pre-registrados de R23K.
2. Cuenta DEMO confirmada de forma redundante en `/status` y `/nodes`.
3. Cadena de saldos sellada con continuidad exacta al centavo frente a W18 ($0.00 COP de discrepancia).
4. Se autoriza la apertura y ejecución de la Ventana 19 limpia con el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
