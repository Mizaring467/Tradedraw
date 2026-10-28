# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 18 (W18)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-19 14:00:00 -05:00 (19:00:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w18_output.txt`  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 18)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA

| Parámetro | Requisito Previo | Medición en Hardware Real | Veredicto |
|---|---|---|:---:|
| **Conectividad ADB** | Dispositivo online + forward 8080 | Conexión activa, port forward tcp:8080 operativo | **PASS** |
| **Nivel de Batería** | Nivel $\ge 20\%$ y alimentación activa | `level: 100%`, `USB powered: true`, `status: 5` (full) | **PASS** |
| **Temperatura Batería** | $< 48.0^\circ\text{C}$ (operación segura) | `temperature: 330` ($33.0^\circ\text{C}$), `voltage: 4338 mV` | **PASS** |
| **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **Seguridad de Fondos** | `isDemoAccount == true` (estricto) | `isDemoAccount: true` ($0.00 COP reales comprometidos) | **PASS** |
| **Estado Operativo** | `mode == DISABLED` al arranque | `mode: "DISABLED"`, `submode: "CONSERVATIVE"` | **PASS** |
| **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false` | **PASS** |
| **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []` | **PASS** |
| **Saldo Inicial Certificado** | Continuidad exacta cierre W17 | `38,817,735.68 COP DEMO` | **PASS** |
| **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) vs W17 | **$0.00 COP** ($38,817,735.68 - 38,817,735.68) | **PASS** |
| **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |
| **Feed Websocket Fresco** | Conectado, tickAge < 3000 ms, activo | `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 287` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 100% con alimentación USB activa (`scale: 100`, `status: 5`, `level: 100`).
- Voltaje nominal: 4338 mV.
- Térmico óptimo: $33.0^\circ\text{C}$ (amplio margen seguro $< 48.0^\circ\text{C}$).

### 2.2 Foco de Interfaz y Broker
- Ventana focal activa en el dispositivo:
  `Window{fcedd08 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Broker Binomo en pantalla completa y listo para operaciones autónomas DEMO.

### 2.3 Estado de la Aplicación y Cadena de Saldos
- `balance`: `3.881773568E7` $\implies$ **38,817,735.68 COP DEMO**.
- Coincidencia exacta al centavo con el saldo final auditado de la Ventana 17 (`workflows/rounds/R23K_CLAUDE_W17_AUDIT.md`).
- Discrepancia inter-ventana: **$0.00 COP**.
- Estado seguro: `mode: DISABLED`, `orderState: NINGUNA`, `hasPendingTrade: false`.
- Feed websocket: `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 287 ms`, `socketTicks: 661`.
- Comparador determinista v1 congelado: `scripts/validation/compare_live_replay.py` conserva su hash SHA-256 inmutable `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f`.

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se emite dictamen **PASS (READY)** para la Ventana 18:
1. Las condiciones de hardware, sistema operativo, app Android `8cdb61f` y broker cumplen al 100% los criterios pre-registrados de R23K.
2. Cuenta DEMO confirmada sin exposición de capital real.
3. Cadena de saldos continua y sellada con cero discrepancia frente a W17.
4. Se autoriza la apertura y ejecución de la Ventana 18 limpia con el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
