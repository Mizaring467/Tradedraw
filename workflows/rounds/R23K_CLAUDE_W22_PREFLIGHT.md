# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 22 (W22)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 220733SL (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-20 19:40:00 -05:00 (2026-10-21 00:40:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w22_output.txt` y telemetría in vivo (`adb forward tcp:8080`, `/status`, `/nodes`)  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 22)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA (13 PUNTOS)

| # | Parámetro Auditado | Requisito Previo Preregistro | Medición en Hardware Real | Veredicto |
|---|---|---|---|:---:|
| **01** | **Conectividad ADB** | Dispositivo Xiaomi online + forward 8080 | Xiaomi Redmi A1 220733SL (`UGU8KFUKM7E6CE8D`) online, port forward tcp:8080 operativo | **PASS** |
| **02** | **Batería y Temperatura** | Nivel $\ge 20\%$, temperatura $< 48.0^\circ\text{C}$ | `level: 100%`, `scale: 100`, `temp: 32.2°C` (`temperature: 322`), `4236 mV`, `health: 2` (good) | **PASS** |
| **03** | **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **04** | **Seguridad de Cuenta (/status)** | `isDemoAccount == true` (estricto) | `/status`: `"isDemoAccount": true` ($0.00 COP reales comprometidos) | **PASS** |
| **05** | **Seguridad de Cuenta (/nodes)** | `isDemoAccount == true` en nodos UI | `/nodes`: `"isDemoAccount": true`, `"balance": 3.8276736E7`, `"activeAsset": "Crypto IDX"` | **PASS** |
| **06** | **Saldo Inicial Certificado** | Continuidad exacta cierre W21 | `38,276,736.00 COP DEMO` | **PASS** |
| **07** | **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) vs W21 | **$0.00 COP** ($|38,276,736.00 - 38,276,736.00| = 0.00\text{ COP}$) | **PASS** |
| **08** | **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity` (Focus activo) | **PASS** |
| **09** | **Feed Websocket Fresco** | Conectado, tickAge < 3000 ms, activo | `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 71`, `ticks: 165` | **PASS** |
| **10** | **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false`, `hasConfirmedTrade: false` | **PASS** |
| **11** | **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []`, `sinceMs: 1792543072853` | **PASS** |
| **12** | **Estrategia y Vetos Congelados** | `AUTO_ADAPTIVE` + Veto Agotamiento | `"strategy": "AUTO_ADAPTIVE"`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **13** | **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 100% de capacidad nominal (`level: 100`, `scale: 100`, `status: 3`, `health: 2` GOOD, `present: true`, `technology: Li-poly`).
- Voltaje de celda: 4236 mV.
- Térmico bajo control estricto: $32.2^\circ\text{C}$ (`temperature: 322` décimas de grado Celsius), margen holgado $< 48.0^\circ\text{C}$ (delta de seguridad de $15.8^\circ\text{C}$).

### 2.2 Foco de Interfaz y Broker
- Ventana focal activa en el dispositivo:
  `Window{74216d1 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Broker Binomo desplegado en primer plano con UI lista para operaciones autónomas en cuenta DEMO.

### 2.3 Seguridad de Fondos y Validación Cruzada
- Endpoint `/status`:
  `"isDemoAccount": true`, `"balance": 3.8276736E7`.
- Endpoint `/nodes`:
  `"isDemoAccount": true`, `"activeAsset": "Crypto IDX"`, `"isSyntheticOrOTC": true`, `"balance": 3.8276736E7`, `"observedOrderAmount": 100000.0`.
- Certificación redundante: 0.00 COP reales comprometidos, 100% fondos de prueba DEMO.

### 2.4 Cadena de Saldos y Continuidad Inter-Ventana
- `balance`: `3.8276736E7` $\implies$ **38,276,736.00 COP DEMO**.
- Coincidencia exacta al centavo con el saldo final auditado y certificado de la Ventana 21 (`workflows/rounds/R23K_CLAUDE_W21_AUDIT.md`).
- Discrepancia contable inter-ventana: **$0.00 COP**.
- Estado operativo del motor: `mode: DISABLED`, `submode: CONSERVATIVE`, `orderState: NINGUNA`, `hasPendingTrade: false`.
- Feed websocket: `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 71 ms`, `socketTicks: 165`, ticks aceptados: 165.
- Comparador offline inmutable v1: `scripts/validation/compare_live_replay.py` preserva su hash SHA-256 congelado `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f`.

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se emite dictamen formal **PASS (READY)** para la Ventana 22:
1. Las condiciones de hardware, sistema operativo, app Android `8cdb61f` y broker cumplen al 100% los 13 criterios pre-registrados de R23K.
2. Cuenta DEMO confirmada de forma redundante tanto en `/status` como en `/nodes`.
3. Cadena contable sellada con continuidad exacta al centavo frente al cierre canónico de W21 ($0.00 COP de discrepancia).
4. Se autoriza formalmente la apertura y ejecución de la Ventana 22 bajo el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
