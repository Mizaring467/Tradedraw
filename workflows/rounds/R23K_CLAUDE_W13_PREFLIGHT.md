# CERTIFICACIÓN PREFLIGHT INDEPENDIENTE — VENTANA 13 (W13)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Dispositivo:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Fecha:** 2026-10-19 02:20:00 -05:00 (07:20:00 UTC)  
**Evidencia Primaria:** `scripts/preflight_w13_output.txt`  
**Dictamen Formal:** PASS (READY · AUTORIZADA APERTURA DE VENTANA 13)

---

## 1. MATRIZ DE PRE-VUELO Y REQUISITOS DE APERTURA

| Parámetro | Requisito Previo | Medición en Hardware Real | Veredicto |
|---|---|---|:---:|
| **Conectividad ADB** | Dispositivo online + forward 8080 | Conexión activa, port forward tcp:8080 operativo | **PASS** |
| **Nivel de Batería** | Nivel $\ge 20\%$ y cargando | `level: 98%`, `USB powered: true`, `status: 2` (cargando) | **PASS** |
| **Temperatura Batería** | $< 48.0^\circ\text{C}$ (operación segura) | `temperature: 456` ($45.6^\circ\text{C}$), `voltage: 4295 mV` | **PASS** |
| **Foco de Pantalla** | Broker Binomo en primer plano | `com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **Identidad de Build** | `gitSha == 8cdb61f`, `vCode == 19` | `gitSha: "8cdb61f"`, `versionCode: 19`, `buildId: "R23H-BUY-EXHAUSTION"` | **PASS** |
| **Seguridad de Fondos** | `isDemoAccount == true` (estricto) | `isDemoAccount: true` ($0.00 COP reales comprometidos) | **PASS** |
| **Estado Operativo** | `mode == DISABLED` al arranque | `mode: "DISABLED"`, `submode: "YOLO"` | **PASS** |
| **Estado de Órdenes** | Sin trades colgados | `orderState: "NINGUNA"`, `hasPendingTrade: false` | **PASS** |
| **Salud del Motor** | `health.state == READY`, `reasons == []` | `state: "READY"`, `reasons: []` | **PASS** |
| **Saldo Inicial Certificado** | Continuidad exacta cierre W12 | `38,985,735.68 COP DEMO` | **PASS** |
| **Discrepancia Inter-Ventana** | Cero centavos ($0.00 COP) | **$0.00 COP** ($38,985,735.68 - 38,985,735.68) | **PASS** |
| **Comparador v1 Congelado** | Invarianza hash SHA-256 | `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` | **PASS** |

---

## 2. ANÁLISIS DE TELEMETRÍA Y AMBIENTE OPERATIVO

### 2.1 Hardware y Alimentación
- Batería al 98% con carga USB activa (`scale: 100`, `status: 2`).
- Voltaje estable en 4295 mV.
- Térmico bajo control ($45.6^\circ\text{C}$), dentro de márgenes térmicos tras 12 ventanas acumuladas.

### 2.2 Foco y Broker
- Ventana focal activa en el administrador de ventanas de Android:
  `Window{7f6be15 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`.
- Interfaz gráfica del broker lista para capturas de pantalla y simulación de taps en DEMO.

### 2.3 Estado de la App y Cadena de Saldos
- `balance`: `3.898573568E7` $\implies$ **38,985,735.68 COP DEMO**.
- Coincidencia perfecta al centavo con el saldo final auditado de la Ventana 12 (`R23K_CLAUDE_W12_AUDIT.md`).
- Discrepancia inter-ventana: **$0.00 COP**.
- Motor en estado inerte seguro (`mode: DISABLED`, `orderState: NINGUNA`, `hasPendingTrade: false`).
- Feed websocket conectado (`ws_active_asset: "Z-CRY/IDX"`, `connected: true`, `isFresh: true`, `lastTickAgeMs: 171`).

---

## 3. VEREDICTO FORMAL DE PREFLIGHT

Se declara veredicto **PASS (READY)** para la Ventana 13:
1. Las condiciones de hardware, sistema operativo, aplicación Android y broker cumplen con el 100% de los criterios del Preregistro R23K.
2. La cuenta DEMO se encuentra rigurosamente asegurada.
3. Se autoriza la apertura y ejecución de la Ventana 13 limpia en hardware real bajo el monitor autónomo v2 (`live_monitor_r23_autonomous.py`).
