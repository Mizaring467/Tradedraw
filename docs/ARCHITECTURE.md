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

---

## 5. Actualización R29-A (2026-11-09) — Arquitectura REAL verificada

> Sección añadida de forma aditiva por la Ronda 5 de R29-A. El contenido anterior (§1–4) se
> conserva: describe correctamente el flujo headless, pero es PARCIAL (R2 §4.9: omite health,
> journals, bridge de 28 endpoints y el estado estático global). Lo que sigue es la arquitectura
> medida sobre el código (HEAD `c934835` + trabajo R27/R28 sin commit). Fuente de verdad completa:
> [`loops/r29a/ROUND_02_COMPONENT_INVENTORY.md`](../loops/r29a/ROUND_02_COMPONENT_INVENTORY.md) y
> [`loops/r29a/ROUND_03_ISOLATION_AUDIT.md`](../loops/r29a/ROUND_03_ISOLATION_AUDIT.md).

### 5.1 Forma del sistema (VERIFICADO)

- **Monolito Android de 1 módulo Gradle** (`:app`; `settings.gradle.kts`): 47 clases Kotlin
  (19.241 LOC) en un paquete plano + `health/` + `ui/theme/`. Stack: AGP 8.5.2, Kotlin 1.9.24,
  OkHttp 4.12.0. **Sin Room/DataStore/Hilt/Retrofit/Coroutines**; loops con `Handler`/`Thread`/
  `Executors`. **0 interfaces** en todo el código de producción (grep → 0).
- **Estado estático global como columna vertebral**: el `companion object` de
  `AutoTradeAccessibilityService` es consumido por **11 archivos** (88 refs: `.instance` ×26,
  `.latestObservedBalance` ×14, `.latestObservedAsset` ×10…); `OverlayService.instance` por 9
  archivos (39 refs). Ciclos: TradingEngine ↔ OverlayService y → ATAS.
- **Núcleo**: `TradingEngine` (2.970 LOC — decisión + cascada de vetos + ejecución + liquidación +
  journals) y `AutoTradeAccessibilityService` (930 LOC — sensor UI del broker + actuador de taps +
  almacén de estado: tres roles sin separar).
- **3 vías de tap real + root como PRIMARIA** (el propio código lo declara, `ATAS:211-216`):
  shell root (`su -c input tap`, exec en `ATAS:323` — la cita ":327" de R2 apunta a un comentario
  posterior; operativa incluso con accesibilidad caída), gesto de accesibilidad
  (`dispatchGesture`, `ATAS:576`, fallback) y tap directo bridge/HUD (`performClickAt`,
  `ATAS:523`). Sin frontera de ejecución: no existe `OrderGateway` (re-verificado, R28-B + R3).
- **Bridge HTTP loopback de 28 endpoints** (`TradeDrawHttpBridge.kt:130-530`, sin auth —
  `docs/SECURITY_MODEL.md` §2–3); **SHADOW decision-only** (registra la decisión y hace `return`
  antes de toda primitiva de tap, `TE:2493-2518`; no simula el ciclo de órdenes).
- Detalle completo de límites, contratos de datos y pruebas: `docs/SYSTEM_BOUNDARIES.md`,
  `docs/DATA_CONTRACTS.md`, `docs/TESTING_STRATEGY.md`, `docs/SECURITY_MODEL.md` (creados en
  Ronda 5).

### 5.2 Arquitectura objetivo (R29-A) — 11 dominios [PROPUESTA]

El diseño completo (contratos por dominio, mapa de estáticos→puertos, 7 fases de migración con
riesgo regla-15, matriz de trazabilidad de las 47 clases) está en
[`loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md`](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md)
— **aquí solo el resumen** (el detalle NO se duplica):

- **Dominios (11):** `core` · `market` · `strategy` · `risk` · `execution` · `simulation` ·
  `research` (incluye backtesting; JVM/Python, fuera de la APK) · `observability` (incluye
  analytics) · `ui` · `adapters` · `persistence`.
- **Contratos P0** (los 5 prerrequisitos de toda frontera): `OrderGateway.dispatch(OrderRequest):
  OrderReceipt` + `TapTransport`×3, `ExecutionPolicy` (cascada congelada veto-a-veto),
  `BalanceSource`, `EventSink`, `CredentialProvider/CredentialSink`.
- **Fases F0–F6** con criterio de aceptación y rollback por fase; riesgo máximo en F3 (frontera de
  ejecución) — punto de STOP si algún guard cambia de orden o alcance.
- **No-migrables (reglas 1-2/15):** interior de las 14 estrategias, orden de la cascada de 13
  vetos, esquemas CSV (29/11 col.), algoritmo de `decisionId`, heurística demo/real, root-tap y
  auto-heal, defaults de RiskManager.
- **Estado:** PROPUESTA-R29-B. Nada de esto existe hoy (0 interfaces); R29-A no implementó código.
