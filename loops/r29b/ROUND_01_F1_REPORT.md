# ROUND 01 — Informe F1 (Contratos P0 Aditivos y Gate G1) · TradeDraw R29-B

**Fecha:** 2026-11-10T05:58Z · **Ejecutado por:** AGY (Sesión visible obligatoria / Solo Routine)
**Rama:** `r10-night`

---

## 1. Objetivos de F1
1. **T-1.1:** Interfaz `core/EventSink.kt` + `RingBufferEventSink` delegando en `TradeEventLog`. Inyección con default estático en consumidores (`TradingEngine`, `RiskManager`, `BinomoWebSocketClient`, `AutoTradeAccessibilityService`, `OverlayService`, `TradeDrawHttpBridge`).
2. **T-1.2:** Interfaz `core/BalanceSource.kt` + `AccessibilityBalanceSource` con protocolo dual-read activo y 0 divergencias contra `ATAS.latestObservedBalance`. Inyección en `RiskManager`.
3. **T-1.3:** `core/CredentialPorts.kt` con interfaces `CredentialProvider` y `CredentialSink` e implementación canónica `SharedPreferencesCredentialStore` sobre `TradeDraw_WSConfig`. Desacoplamiento de `BinomoWebSocketClient`, `MainActivity` y `BinomoAuthActivity`.
4. **T-1.4:** Interfaz `core/CapitalPolicy.kt` implementada por `RiskManager.canExecuteTrade` e inyectada en `TradingEngine`.
5. **Gate G1:** Suite completa verde, 4 tests nuevos de equivalencia y roundtrip en verde, dual-read sin divergencias y paridad de observabilidad.

---

## 2. Evidencia de Ejecución

### 2.1 Suite JVM en Termux
- **Comando:** `BACKTEST_CSV=loops/r23c_phase3_validation/window_01/ticks_live.csv BACKTEST_OUT=$(mktemp) ./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
- **Resultado:** `BUILD SUCCESSFUL in 1m 3s`.
- **Parseo de XMLs (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):**
  - Archivos XML: 62 (58 previos + 4 nuevas clases de prueba F1)
  - Total tests: 583
  - Fallos: 0
  - Errores: 0
  - Skipped: 0

### 2.2 Suite Python
- **Comando:** `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=.:validation_engine python -m unittest discover -s tests`
- **Resultado:** `Ran 43 tests in 0.282s` · `OK`.

### 2.3 Tests Nuevos de F1
- `EventSinkEquivalenceTest`: Verifica que eventos emitidos a través de `EventSink` ingresan al anillo circular en memoria y al ring dedicado de `MODE_CHANGE` preservando detalles e IDs.
- `BalanceSourceDualReadTest`: Verifica que las lecturas por puerto coinciden con el estático con 0 divergencias y que `RiskManager` opera con fuentes mockeadas.
- `CredentialPortRoundtripTest`: Verifica que `SharedPreferencesCredentialStore` escribe y lee con exactitud las claves históricas de `TradeDraw_WSConfig`.
- `CapitalPolicyDelegationTest`: Verifica que `CapitalPolicy.canExecuteTrade` produce idénticos resultados y mensajes de veto que `RiskManager.canExecuteTrade`.

### 2.4 Invariantes y Preservación CRLF
- `OverlayService.kt`: `CRLF=2468, LF=0` (invariante de fin de línea preservado).
- `ShadowModeTest` y `BalanceFreshnessSeparationTest`: Verificados 100% en verde sin modificación.

---

## 3. Dictamen de Gate G1
**GATE G1: APROBADO**
- Contratos P0 aditivos completamente operativos.
- Puertos desacoplados con defaults estáticos no invasivos.
- 583 tests JVM + 43 tests Python en verde.
- Listo para avanzar a **F3 (Frontera de Ejecución - T-3.0 a T-3.5 y Gate G3)**.
