# Arquitectura Real de TradeDraw (Migración a Headless)

Este documento describe la arquitectura actual de TradeDraw, la cual ha migrado de un sistema basado en captura de pantalla y análisis de visión (HSV) hacia una ejecución "Headless" mucho más precisa basada en un WebSocket nativo.

## 1. El Flujo Headless (Actual)

El bot ya no depende de tomar capturas (screencaps) para decidir operaciones en Binomo. El flujo exacto de información es:

1. **`BinomoWebSocketClient`**: Mantiene una conexión WSS en segundo plano con el broker, recibiendo el feed de precios en crudo (ticks).
2. **`MarketTick`**: Estructura de datos inmutable que representa cada movimiento del precio.
3. **`SyntheticCandleEngine`**: Agrupa los `MarketTick` en velas sintéticas temporales. Calcula indicadores en tiempo real (RSI sintético) y delimita rangos de Soporte y Resistencia (`distS`, `distR`).
4. **`TradingEngine.executeHeadlessTrade`**: Recibe la señal sintética y evalúa la acción del precio.
5. **Cascada de Vetos de `TradingEngine`**: *(Ver sección 3)*.
6. **`RiskManager`**: Si la señal sobrevive los vetos, se evalúa contra las reglas de capital (cooldown, stop loss por rachas, niveles de martingala).
7. **`AdaptiveLearningEngine`**: Realiza ajustes de hiperparámetros sobre la marcha basándose en el historial de operaciones similares.
8. **`AutoTradeAccessibilityService`**: Finalmente, utiliza `dispatchGesture` para simular el clic táctil en la pantalla en el dispositivo físico/emulador.

## 2. Capas de la Era de Visión (Estado Actual)

La arquitectura antigua documentada previamente ha cambiado drásticamente:

- **Activos Vestigiales (Ya no guían el bot principal)**:
  - `ScreenCaptureManager.kt` y `VisionAnalyzer.kt`: Aún existen en el código y pueden usarse como fallback visual o para el modo de depuración manual, pero **ya no generan** la señal primaria que dispara el `AutoTradeAccessibilityService` bajo condiciones normales.
- **Activos Evolucionados**:
  - `OverlayService.kt` y `CustomDrawingView.kt`: Siguen siendo cruciales, pero ahora renderizan figuras técnicas inyectadas programáticamente (`AutoDrawEngine.kt`) a partir de la data del WebSocket (ej. la línea del precio y cajas de RSI).

## 3. Orden Exacto de la Cascada de Vetos

Cuando `SyntheticCandleEngine` genera una señal (CALL o PUT), la señal entra a una ventana crítica de timing y es evaluada en este orden estricto dentro de `TradingEngine.kt`:

1. **Veto por Choppiness/Micro-Rango**: Se aborta si el RSI o la varianza de los ticks indica un mercado lateral inoperable (cuerpo < 15px).
2. **Veto por Timing (Ventana de Ejecución)**: Bloquea operaciones si el segundo del reloj no está en la ventana de disparo (usualmente se busca el segundo `:58` al `:03`).
3. **Vetos Duros por Soporte/Resistencia Invertida**:
   - `Prohibido vender sobre Soporte` (Riesgo de rebote): Corta señales PUT si `distS <= 0.15f`.
   - `Prohibido comprar sobre Resistencia` (Riesgo de rechazo): Corta señales CALL si `distR <= 0.15f`.
4. **Vetos de Continuación (Agotamiento)**:
   - Frena operaciones en la dirección de la tendencia si se detectan "3 Velas" de agotamiento contra un nivel S/R.
5. **Vetos de Mecha de Rechazo**: Cancela rupturas si la vela anterior dejó mechas >= 45% contra el nivel.
6. **Vetos de Racha Sobreextendida**: Filtra operaciones en la dirección de la tendencia si hay >=4 velas consecutivas previas.
7. **Filtro de Riesgo (RiskManager)**: El último paso antes de operar. Corta por Martingala máxima, Stop Loss diario alcanzado o límite de Cooldown.

*Nota para desarrolladores: Debido a que la invariante geométrica de `SyntheticCandleEngine` asegura que `distS + distR == 1.0`, los Vetos Duros (paso 3) que operan contra `distR <= 0.15` y `distS <= 0.15` pueden hacer que estrategias de reversión sean inalcanzables si la condición de veto choca aritméticamente con el trigger de la señal.*

## 4. Tabla de Nuevos Archivos Principales

| Archivo | Responsabilidad |
| :--- | :--- |
| **`AdaptiveLearningEngine.kt`** | Almacena y cruza historiales de Win/Loss de la sesión para optimizar las tolerancias (S/R) de entradas futuras (aprendizaje por refuerzo ligero). |
| **`AgentChatOverlay.kt`** | Interfaz UI flotante para interacción directa con el subsistema de agentes. |
| **`AutonomousAgentController.kt`** | Orquestador de agentes de la app; despacha comandos y coordina qué subsistema toma el control. |
| **`BinomoAuthActivity.kt`** | Actividad dedicada a manejar flujos de autenticación o inyección de tokens para el WebSocket. |
| **`BinomoWebSocketClient.kt`** | Cliente WSS asíncrono para ingestar ticks de precios de activos en tiempo real sin polling HTTP ni visión. |
| **`ChartViewportController.kt`** | Administra la sincronización del viewport del gráfico nativo para mapear precios WebSocket a coordenadas (X, Y) del Canvas de dibujo. |
| **`DebugVisualizer.kt`** | Reemplazo del overlay antiguo; dibuja sobre el Canvas datos puros (ticks, RSI, SR sintéticos) para depurar el motor Headless. |
| **`ModelPickerDialog.kt`** | Diálogo UI para seleccionar los endpoints remotos (OpenAI, OmniRoute) en caso de requerir verificación de IA. |
| **`TradeDrawHttpBridge.kt`** | Intermediario para llamadas REST, independiente del WSS, usado para sincronización de balances o reportes. |
| **`TradeJournalLogger.kt`** | Registra de forma estructurada cada señal, veto y resultado en disco para análisis offline de estrategias (DeepSeek/Hermes). |
| **`TradeLearningEngine.kt`** | Sub-motor para procesar logs de `TradeJournalLogger` y retroalimentar a `AdaptiveLearningEngine`. |
