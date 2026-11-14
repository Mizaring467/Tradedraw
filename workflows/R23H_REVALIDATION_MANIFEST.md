# MANIFIESTO DE REVALIDACIÓN DEMO LIMPIA R23H
## R23H_REVALIDATION_MANIFEST

**Fecha:** 2026-10-18  
**Orquestador:** AGY (`w1:p1`)  
**Auditor de Pre-vuelo:** CLAUDE CODE (`w1:pC`)  
**Validador Independiente:** OPENCODE (`w1:pB`)  
**Implementador:** HERMES (`w1:pA`)  
**Propósito:** Garantizar que la nueva ventana Live y su Replay se ejecuten bajo una configuración 100% idéntica, homogénea y reproducible.

---

### 1. IDENTIFICACIÓN Y TRAZABILIDAD DE BUILD
- **Rama Git:** `r10-night`
- **Commit Base Auditado:** `aac1df3`
- **Estado de Trabajo Local:** Cambios de infraestructura de trazabilidad implementados por Hermes (466 tests pasando).
- **versionCode:** 19
- **versionName:** `1.9-r23h-exhaustion`
- **BUILD_ID:** `R23H-BUY-EXHAUSTION`
- **Target Device:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)

---

### 2. CONFIGURACIÓN OPERATIVA Y ENTORNO
- **Entorno Broker:** Cuenta DEMO exclusiva (`isDemoAccount == true`, fail-closed).
- **Activo Nominal / Pantalla:** `Crypto IDX`
- **Activo RIC Feed:** `Z-CRY/IDX`
- **Modo:** `AUTONOMOUS`
- **Submodo:** `YOLO` (Cooldown post-resolución: 8,000 ms, ventana sniper :01–:05).
- **Estrategia Activa:** `AUTO_ADAPTIVE` (11 patrones MT_*, confluencia mínima 75/80 pts).
- **Veto R23H Activo:** `VETO_BUY_EXHAUSTION_CLIMAX` (vetar BUY en UPTREND cuando `callPower >= 70%`).
- **Filtro Adaptativo:** `ADAPTIVE_BLOCK` activo con umbral de similitud `0.99f`.
- **Stake / Payout:** 100,000 COP DEMO (1.0% Soros) / Payout nominal 82%.

---

### 3. VENTANA DE VALIDACIÓN PREVISTA
- **Window ID:** `window_04_clean`
- **Directorio de salida:** `loops/r23h_validation/window_04_clean/`
- **Duración nominal:** 20.0 minutos + 75s settlement grace.
- **Protocolo de delimitación temporal:** Poda estricta de ticks con `ts >= window_start_ts_ms`. Cero ticks residuales de ventanas previas.
- **Cero estado residual:** Directorio completamente nuevo, contadores iniciales calibrados al segundo 0 del inicio.

---

### 4. CONFIGURACIÓN DEL ARNÉS DE REPLAY Y COMPARADOR
- **Replay Runner:** `com.example.tradedraw.ReplayBacktestTest` vía `./gradlew.bat testDebugUnitTest`
- **Submodo Replay:** `BACKTEST_SUBMODE=YOLO` (Cooldown 8,000 ms).
- **Políticas Replicadas:** `calcularPoderesDeMercado()` + `motivoVetoPorAgotamientoDeCompra` (congelada).
- **Tolerancia Temporal Comparator:** `temporal_tolerance_ms = 6000` ms (absorbe latencia de confirmación en Android Go).
- **Modo de Comparación:** `--mode trades` (Cruza órdenes confirmadas contra órdenes simuladas por identidad determinista y fallback temporal).
