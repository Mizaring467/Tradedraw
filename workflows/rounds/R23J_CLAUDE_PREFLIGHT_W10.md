# AUDITORÍA TÉCNICA DE PREFLIGHT — VENTANA 10 LIMPIA (LOOP R23J)
## R23J_CLAUDE_PREFLIGHT_W10

**Fecha:** 2026-10-18  
**Auditor Principal:** CLAUDE CODE (`w1:pC`)  
**Destinatario:** AGY (`w1:p1` / Orquestador)  
**Validador Live:** OPENCODE (`w1:pB`)  
**Objeto:** Auditoría de certificación pre-vuelo para la apertura de la Ventana 10 (`window_10_clean`, 20 min en cuenta DEMO) en el dispositivo Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`).

---

## VEREDICTO FORMAL: **`PREFLIGHT W10 = READY`**

Todos los parámetros de hardware, batería, build desplegada (`8cdb61f`), telemetría de `/status`, aplicación en primer plano, comparador congelado y directorio de aislamiento cumplen rigurosamente el 100% de los criterios pre-registrados. Se autoriza formalmente la apertura y ejecución de la **Ventana 10**.

---

## 1. CHECKLIST DETALLADA DE PREVUELO

| # | Parámetro Auditado | Requisito Pre-registrado | Valor Observado en Runtime | Dictamen |
| :-: | :--- | :--- | :--- | :---: |
| **1** | **Dispositivo ADB** | Conectado y autorizado | `UGU8KFUKM7E6CE8D` (`device`, `ice_global`, `220733SL`) | **PASS** |
| **2** | **Nivel de Batería** | $\ge 20\%$ y en carga | **`87%`** (`USB powered: true`, `status: 2 Charging`) | **PASS** |
| **3** | **Git SHA Build** | `8cdb61f` exacto | **`8cdb61f`** | **PASS** |
| **4** | **Versión de Build** | `versionCode == 19` | `versionCode: 19`, `buildId: R23H-BUY-EXHAUSTION` | **PASS** |
| **5** | **Tipo de Cuenta** | `isDemoAccount == true` (Mandatorio) | **`true`** (100% DEMO, fondos reales $0.00 COP) | **PASS** |
| **6** | **Estado de Salud** | `health.state == READY` | **`"READY"`** (`reasons: []`) | **PASS** |
| **7** | **Modo de Operación** | `mode == DISABLED` | **`"DISABLED"`** | **PASS** |
| **8** | **Submodo de Operación** | `submode == YOLO` | **`"YOLO"`** | **PASS** |
| **9** | **Primer Plano (Foreground)** | `com.marketly.trading` activo | `com.marketly.trading.modules.trading.TradingActivity` | **PASS** |
| **10** | **Comparador Congelado** | SHA-256 == `4CB5...4B5F` | **`4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`** | **PASS** |
| **11** | **Directorio Aislado** | `window_10_clean/` preparado limpio | Preparado, 0 archivos residuales | **PASS** |
| **12** | **Balance Inicial Registrado** | Saldo exacto registrado | **`39,103,736.32 COP DEMO`** | **PASS** |

---

## 2. EVIDENCIA TÉCNICA DE VERIFICACIÓN

### 2.1 Hardware y Alimentación (`adb devices` / `dumpsys battery`)
- Dispositivo: `UGU8KFUKM7E6CE8D` Xiaomi Redmi A1 (`220733SL`, Android 12 Go).
- Batería: Nivel **`87%`** ($\ge 20\%$), alimentación USB activa (`USB powered: true`, `status: 2 Charging`).
- Voltaje: $4,214\text{ mV}$ | Temperatura: $35.1^\circ\text{C}$ (parámetros eléctricos y térmicos estables).

### 2.2 Telemetría Viva de la Aplicación (`curl -s http://127.0.0.1:8080/status`)
```json
{
  "app": "TradeDraw",
  "gitSha": "8cdb61f",
  "versionCode": 19,
  "versionName": "1.9-r23h-exhaustion",
  "buildId": "R23H-BUY-EXHAUSTION",
  "isDemoAccount": true,
  "health": {
    "state": "READY",
    "reasons": []
  },
  "mode": "DISABLED",
  "submode": "YOLO",
  "strategy": "AUTO_ADAPTIVE",
  "balance": 39103736.32,
  "accessibilityActive": true,
  "feed": {
    "activeAsset": "Z-CRY/IDX",
    "connected": true,
    "isFresh": true,
    "volatilidadSuficiente": true,
    "payoutResolved": 0.83,
    "segundosSinTicks": 0
  }
}
```

### 2.3 Broker en Primer Plano (`dumpsys window`)
- `mCurrentFocus=Window{6816fb8 u0 com.marketly.trading/com.marketly.trading.modules.trading.TradingActivity}`
- `mFocusedApp=ActivityRecord{7987059 u0 com.marketly.trading/.modules.trading.TradingActivity t711}`
- Broker visible, activo y listo para recepción de eventos de accesibilidad.

### 2.4 Hash Inmutable del Comparador Live ↔ Replay
- Archivo: `scripts/validation/compare_live_replay.py`
- Hash medido SHA-256: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`
- Hash congelado en `workflows/PREREGISTRO_R23J.md` §1: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`
- Invarianza: **Exacta (cero modificaciones)**.

### 2.5 Aislamiento del Directorio de Ejecución
- Directorio: `loops/r23j_validation/window_10_clean/`
- Estado: Creado, vacío, listo para recolección pura de telemetría y logs.

### 2.6 Registro Oficial del Balance Inicial
- **Balance Inicial Oficial W10:** **`39,103,736.32 COP DEMO`**
