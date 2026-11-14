# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 14 (W14)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-19 02:48:00 -05:00 (07:48:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w14_output.txt`  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 14)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA

| Parámetro | Requisito Previo | Medición en Hardware Real | Veredicto |
|---|---|---|:---:|
| **Conectividad ADB** | Dispositivo online + forward 8080 | Conexión activa, port forward tcp:8080 operativo | **PASS** |
| **Nivel de Batería** | Nivel $\ge 20\%$ y cargando | `level: 98%`, `USB powered: true`, `status: 2` (cargando) | **PASS** |
| **Temperatura Batería** | $< 48.0^\circ\text{C}$ (operación segura) | `temperature: 457` ($45.7^\circ\text{C}$), `voltage: 4292 mV` | **PASS** |
| **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **Seguridad de Fondos** | `isDemoAccount == true` (estricto) | `isDemoAccount: true` ($0.00 COP reales comprometidos) | **PASS** |
| **Estado Operativo** | `mode == DISABLED` al arranque | `mode: "DISABLED"`, `submode: "YOLO"` | **PASS** |
| **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false` | **PASS** |
| **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []` | **PASS** |
| **Saldo Inicial Certificado** | Continuidad exacta cierre W13 | `38,851,737.60 COP DEMO` | **PASS** |
| **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) | **$0.00 COP** ($38,851,737.60 - 38,851,737.60) | **PASS** |
| **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 98% cargando USB (`status: 2`, `level: 98`, `scale: 100`).
- Voltaje nominal estable en 4292 mV.
- Temperatura controlada: $45.7^\circ\text{C}$ (margen térmico seguro $< 48.0^\circ\text{C}$).

### 2.2 Foco de Interfaz y Broker
- Ventana focal activa en el dispositivo:
  `Window{7f6be15 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Broker Binomo preparado en pantalla para la captura continua de gráficos y despacho de taps.

### 2.3 Estado de la Aplicación y Cadena de Saldos
- `balance`: `3.88517376E7` $\implies$ **38,851,737.60 COP DEMO**.
- Coincidencia exacta al centavo con el saldo final auditado de la Ventana 13 (`workflows/rounds/R23K_CLAUDE_W13_AUDIT.md`).
- Discrepancia inter-ventana: **$0.00 COP**.
- Estado inerte y seguro: `mode: DISABLED`, `orderState: NINGUNA`, `hasPendingTrade: false`.
- Feed websocket: `activeAsset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 46`.

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se emite dictamen **PASS (READY)** para la Ventana 14:
1. El hardware real, la app Android `8cdb61f` y el broker cumplen íntegramente con los requisitos del Preregistro R23K.
2. Cuenta DEMO verificada al 100%.
3. Se autoriza la apertura formal de la Ventana 14 limpia con el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
