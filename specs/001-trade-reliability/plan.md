# Implementation Plan: Aumento de Confiabilidad y Robustez de TradeDraw

**Branch**: `001-trade-reliability` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

## Summary
Plan maestro para transformar a TradeDraw en un motor cuantitativo institucional confiable, robusto y preparado para operar con dinero real. El enfoque se divide en cuatro capas críticas:
1. **Capa de Riesgo y Protección**: Freno terminal por racha, suelo absoluto de balance en COP, trailing profit lock y detección de trades VOID/fantasma.
2. **Capa Cuantitativa y Filtros de Señal**: Niveles S/R por fractales/pivotes reales independientes, filtro Choppiness Index (CHOP >= 61.8%) y vetos anti-sobreextensión (ATH/ATL).
3. **Capa de Ejecución y Latencia**: Disparo sniper (:58s-:02s) de ultra-baja latencia (< 30ms) con validación de toque por accesibilidad.
4. **Capa de Verificación y Backtesting**: Pipeline de pruebas unitarias 100% verde y replay histórico sin sesgos.

## Technical Context
- **Lenguaje**: Kotlin 1.9.24 (Android nativo).
- **Plataforma**: Android 14 (compileSdk 34, minSdk 24).
- **Herramientas de Build**: Gradle 9.5, AGP 8.5.2, Java 17 Temurin (`--no-daemon --no-configuration-cache`).
- **Arquitectura**: Headless WebSocket con reconstrucción sintética de velas de 1m, motor de trading reactivo y overlay flotante optimizado.
- **Runtimes Multi-Agente**: Ruflo v3.38.12 (Hermes, Codex, OpenCode, DeepSeek Harness).
- **Dispositivo de Referencia**: POCO X6 Pro (Android 14 / HyperOS, IP `192.168.1.185:5555`).

## Constitution Check
- [x] **Principio I (Preservación de Capital)**: Implementación de parada terminal no reactivable automáticamente y suelo absoluto de equity.
- [x] **Principio II (Ventaja Matemática Real)**: Eliminación de invariantes $distS + distR == 1.0$; sustitución por niveles fractales con toques validados.
- [x] **Principio III (Integridad de Ejecución)**: Clasificación de $0.0 Diff$ como VOID; 3 VOIDs consecutivos detienen el bot.
- [x] **Principio IV (Test-Driven)**: Cada componente de riesgo y filtro matemático respaldado por pruebas unitarias en JUnit.
- [x] **Principio V (CI/CD Directo)**: Push directo a main + instalación instantánea por ADB (`adb install -r`).

## Component Architecture & Modificaciones

### 1. Risk & Capital Safety (`RiskManager.kt`)
- `stopLossStreak`: Evaluación terminal prioritaria. Si `currentLossStreak >= stopLossStreak`, `isTerminalHalted = true` de forma permanente.
- `absoluteEquityFloor`: Parada dura si `currentBalance < absoluteEquityFloor`.
- `recordTradeOutcome()`: Soporte para `TradeOutcome.VOID`. No incrementa racha de pérdidas ni martingala. `consecutiveVoidTrades >= 3` dispara parada técnica.
- `trailingProfitLock`: Protección del 35% de retracement tras alcanzar un nuevo pico de balance en la sesión.

### 2. Quantitative Strategy & Geometry (`SyntheticCandleEngine.kt`)
- Pivotes fractales de 5 períodos: `identifyFractalPivots()` busca altos y bajos relativos.
- Agrupamiento de niveles de soporte y resistencia por densidad de toques (`SupportResistanceLevel(price, touchCount)`).
- Variables independientes: `distanceToResistance` y `distanceToSupport` normalizadas por ATR(14) en lugar del rango estático de la caja.
- Filtro Choppiness Index (CHOP) optimizado y cacheado por vela (`cachedChoppiness`).

### 3. Decision Engine & Execution Timing (`TradingEngine.kt`)
- Ventana Sniper Estricta: Disparo solo entre los segundos :58s y :02s de la vela de 60s.
- Veto Anti-Suicidio:
  * Prohibido comprar (CALL) si el precio está a menos del 15% de la resistencia superior o en ATH.
  * Prohibido vender (PUT) si el precio está a menos del 15% del soporte inferior o en ATL.
- Integración con `cachedChoppiness` para descartar mercados laterales sucios sin consumo excesivo de CPU.

### 4. UI Overlay & Performance (`OverlayService.kt`)
- `hudUpdatePending`: Control de flujo atómico para evitar flooding del Looper de la UI thread.
- Despliegue en tiempo real de badges claros: `[PARADA DURA SL]`, `[SUELO EQUITY]`, `[FILTRO CHOP]`, `[ATH/ATL VETO]`.
- Movimiento fluido del HUD y de la burbuja sin jank durante el arrastre.

## Plan de Verificación Automatizada
1. **Unit Tests (JUnit 4)**:
   - `RiskManagerTest.kt`: Pruebas para parada terminal, suelo absoluto de balance, resultado VOID y trailing profit lock.
   - `SyntheticCandleEngineTest.kt`: Pruebas para independencia de distancias S/R, cálculo de CHOP y rechazo de rango comprimido.
   - `TradingEngineTest.kt`: Pruebas para vetos de proximidad a S/R y ventana sniper.
2. **Pipeline CI**:
   - `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
   - `./gradlew assembleDebug --no-daemon --no-configuration-cache`
3. **Validación en Dispositivo Físico**:
   - Instalación vía ADB en POCO X6 Pro (`192.168.1.185:5555`).
   - Monitoreo en vivo con Artemis / Android-Vision para registrar comportamiento en cuenta Demo de Binomo.
