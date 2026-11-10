# ROUND 03 — Informe de Fase F3 (Frontera de Ejecución) & Gate G3

**Fecha:** 2026-11-10 · **Ejecutor:** AGY (Sesión visible / Solo Routine) · **Rama:** `r10-night`  
**Referencia:** `loops/r29a/ROUND_06_MIGRATION_PLAN.md` §3, §5 (F3: T-3.1 a T-3.5), `td-r29b-mission.md`

---

## 1. Resumen Ejecutivo

En la Fase F3 se formalizó la **frontera de ejecución (D-6/D-7)** desacoplando la lógica de despacho táctil, resolución de coordenadas y evaluación de políticas de veto, sin alterar los cuerpos de las estrategias ni romper la compatibilidad con Android ni los contratos de runtime:

- **T-3.1 (Seam Coordenadas E4):** Se creó el contrato puro `core/TapCoordinateResolver` e implementación `CalibrationTapCoordinateResolver` que delega en `CalibrationManager` cuando está calibrado y mantiene idénticos los valores por defecto (Landscape: 0.881/0.735 para BUY y 0.881/0.844 para SELL; Portrait: 0.25/0.903 para BUY y 0.75/0.903 para SELL) con sanitización estricta Y < 0.80h a 0.838h.
- **T-3.2 (ExecutionPolicy & Cascada de 13 Vetos):** Se extrajo `execution/ExecutionPolicy` reproduciendo con fidelidad byte-a-byte la secuencia estricta de 13 vetos congelados en el inventario T-3.0 (§3 del plan de migración). Se documentó el carve-out formal del sitio `:1867` (`handleSignal`).
- **T-3.3 (OrderGateway & 3 TapTransports):** Se introdujo `core/OrderGateway`, `OrderRequest`, `OrderReceipt`, y salvaguarda positiva fresca de cuenta DEMO (`DemoAccountGuard` / `StandardDemoAccountGuard`). Se construyó `DefaultOrderGateway` orquestando los 3 transportes (`RootTapTransport`, `GestureTapTransport`, `BridgeDirectTransport`) preservando la prioridad root->gesto, timeout de 300 ms, bypass HUD de 350 ms y `restoreHudTouchable`.
- **T-3.4 (CandleRateLimiter):** Se implementó `execution/CandleRateLimiter` administrando `lastExecutedCandleEpochMinute` con flag `separateShadowRateLimit = false` por defecto (comportamiento idéntico actual) y compatibilidad total vía propiedad delegada en `TradingEngine`.
- **T-3.5 (/trade por Modo - Decisión P-3 Aprobada):** En `TradeDrawHttpBridge.kt`, el endpoint `/trade` consulta el modo activo. Modos `DISABLED` y `SHADOW` rechazan explícitamente el despacho al broker con HTTP 403 y emiten el evento `TradeEventLog.Kind.VETO` (`TRADE_ENDPOINT_MODE_REJECTED`). Modos `AUTONOMOUS` y `SEMIAUTOMATIC` pasan a través de las coordenadas calibradas y la guarda de despacho.

---

## 2. Auditoría de Vías Alternativas de Despacho (/click, Gestos y Root)

Conforme al requerimiento de la misión, se auditó exhaustivamente la superficie de emisión de taps en el código:

1. **Endpoint `/trade` (HTTP Bridge):**
   - *Propósito:* Despacho de órdenes de trading directas desde automatizaciones externas.
   - *Guarda:* Protegido con `evaluateTradeEndpointModeGuard`. En `DISABLED` y `SHADOW` retorna 403 inmediato y emite evento VETO. En modos habilitados (`AUTONOMOUS`/`SEMIAUTOMATIC`), la llamada pasa a través de la salvaguarda `puedeDespacharTap(isDemoAccount)`.
2. **Endpoint `/click` (HTTP Bridge):**
   - *Propósito:* Depuración y pruebas táctiles de interfaz (taps arbitrarios de calibración en pantalla).
   - *Guarda:* Llama a `AutoTradeAccessibilityService.performClick(x, y)` que verifica `puedeDespacharTap(isDemoAccount)`. Si la cuenta no es DEMO, el toque es bloqueado con veto `SEGURIDAD_CLICK_NO_DEMO`.
3. **Botones de Prueba del Menú Overlay (`TEST` y `TEST_SELL` en `OverlayService:207-217`):**
   - *Propósito:* Pulsación de prueba de los botones calibrados desde la burbuja flotante.
   - *Guarda:* Pasan por `AutoTradeAccessibilityService.performClickAt(x, y)` que ejecuta el guard fail-closed `puedeDespacharTap(isDemoAccount)`.
4. **Despacho Autónomo (`executeHeadlessTrade` en `TradingEngine:2537`):**
   - *Propósito:* Despacho autónomo del motor de trading.
   - *Guarda:* Doble protección: `puedeOperarAutonomoEnDemo(isDemoAccount)` previo a la cascada de vetos, y `puedeDespacharTap(isDemoAccount)` al invocar `performClick`.
5. **Ruta ROOT (`executeRootTap`):**
   - *Propósito:* Inyección de toque mediante `su -c input tap`.
   - *Guarda:* Incorpora la tercera puerta de seguridad en `:306` (`if (!puedeDespacharTap(isDemoAccount)) return`).

**Conclusión de la Auditoría:** No existe ninguna vía de despacho de órdenes o toques que pueda saltarse la guarda fail-closed de cuenta DEMO sin autorización explícita. Los gestos táctiles normales de la interfaz de Android sobre el overlay siguen operando libremente sin afectación.

---

## 3. Evidencia de Verificación y Suites de Prueba

### 3.1 Nuevas Suites de Prueba de F3
- `TapCoordinateResolverTest`: 3 tests verificando exactitud de coordenadas y sanitización en vertical.
- `CandleRateLimiterTest`: 3 tests validando flag off (SHADOW consume vela) vs flag on (SHADOW independiente) y reset.
- `TapTransportParityTest`: 6 tests verificando rechazo en DISABLED/SHADOW, bloqueo por cuenta desconocida/expirada, bloqueo de cuenta REAL, transiciones y cascada de transportes.
- `TradeEndpointModeGuardTest`: 4 tests verificando contratos 403/200 del endpoint `/trade` por modo.
- `VetoCascadeEquivalenceTest`: 15 tests validando los 13 vetos en orden estricto, mensajes byte-a-byte, precedencia y caso permitido.

### 3.2 Resultados del Gate G3
- **Suite JVM Completa:**
  - Comando: `BACKTEST_CSV=loops/r23c_phase3_validation/window_01/ticks_live.csv BACKTEST_OUT=$(mktemp) ./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
  - Total test cases: **614**
  - Fallos: **0**
  - Errores: **0**
  - Omitidos: **0**
  - Archivos XML de reporte: **67**
- **Suite Python:**
  - Comando: `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=.:validation_engine python -m unittest discover -s tests`
  - Total tests: **43**
  - Resultado: **OK** (0.313s)
- **Integridad CRLF:** `OverlayService.kt` preservado al 100% (2468 líneas CRLF, 0 LF).

---

## 4. Veredicto

**GATE G3: APROBADO.**  
Frontera de ejecución completada conforme al plan. Se procede a comitear F3 en `r10-night` e iniciar la Fase F4 (Simulation Engine).
