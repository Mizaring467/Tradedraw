# AGENTS.md · TradeDraw Development Guide

Este documento proporciona el contexto completo, la arquitectura técnica, las reglas de desarrollo y los procedimientos de compilación de **TradeDraw** para cualquier agente de código autónomo o asíncrono (**Jules, Hermes, DeepSeek Harness, OpenCode, Claude Code, etc.**).

---

## ⚡ 1. Regla de Oro para Agentes (Flujo Directo a `main`)

1. **NO abrir Pull Requests innecesarios.** A menos que el usuario lo pida explícitamente, realiza los cambios, haz commit y push **DIRECTAMENTE a la rama `main`**.
2. **CI/CD Automatizado:** Cada push a `main` activa el pipeline de GitHub Actions (`.github/workflows/android-build.yml`), el cual compila automáticamente el APK de depuración:
   - **Enlace de Descarga del APK:** `https://github.com/Mizaring467/Tradedraw/actions` ➔ Artefacto: **`TradeDraw-debug`** (`app-debug.apk`).
3. **Entrega al Usuario:** Al finalizar tu tarea, proporciona siempre el enlace de GitHub Actions confirmando que el APK está listo para instalarse.

---

## 📱 2. Propósito y Dominio del Proyecto

**TradeDraw** es una aplicación móvil nativa para Android diseñada para análisis técnico cuantitativo y auto-trading en tiempo real sobre brokers de opciones binarias y trading móvil (**Binomo, Quotex, PocketOption, TradingView**).

- **Mecánica de Opciones Binarias:**
  - Las operaciones son a tiempo de expiración fijo (generalmente 1 minuto / 60 segundos).
  - No existe Stop Loss ni Take Profit en distancia de pips; solo importa si el precio al vencimiento cierra estrictamente por encima (`CALL / SUBE`) o por debajo (`PUT / BAJA`) del precio de entrada (*Strike Price*).
  - El sistema utiliza la herramienta visual nativa `STRIKE_PRICE_LINE` (línea punteada que cambia dinámicamente entre **Verde ITM** y **Rojo OTM** en tiempo real).
- **Activos Principales:** Crypto IDX (Binomo), Pares de divisas OTC (CHF/JPY OTC, EUR/USD OTC), Criptomonedas (BTC, SOL).

---

## 🛠️ 3. Stack Tecnológico y Configuración de Build

- **Lenguaje:** Kotlin 1.9.24.
- **Plataforma:** Android nativo, SDK de compilación 34 (Android 14), `minSdk 24` (Android 7.0+).
- **Build System:** Gradle 9.5 (Kotlin DSL), Android Gradle Plugin (AGP) 8.5.2, Java 17 (Temurin).
- **Comando de Compilación:**
  ```bash
  ./gradlew assembleDebug --no-daemon --no-configuration-cache
  ```
  *(Nota crítica: `configuration-cache` debe permanecer deshabilitado por incompatibilidad entre Gradle 9.5 y el plugin Kotlin 1.9.24).*

---

## 🏛️ 4. Arquitectura Modular del Código

El proyecto está estructurado en módulos desacoplados bajo el paquete `com.example.tradedraw`:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. OVERLAY & UI LAYER (OverlayService, CustomDrawingView)                   │
│    - 4 Ventanas flotantes TYPE_APPLICATION_OVERLAY con FLAG_NOT_FOCUSABLE   │
│    - Canvas de dibujo con herramientas TradingView y Strike Price ITM/OTM    │
│    - HUD de Trading con temporizador 60s, termómetro % CALL/PUT y Martingala│
├─────────────────────────────────────────────────────────────────────────────┤
│ 2. SCREEN VISION & CAPTURE LAYER (ScreenCaptureManager, VisionAnalyzer)     │
│    - MediaProjection continuo en HandlerThread (Android 10 - 14)            │
│    - Detección HSV de velas verdes/rojas, mechas y extremos S/R             │
│    - Detección automática de resultado de trade (Win/Loss por banner)       │
├─────────────────────────────────────────────────────────────────────────────┤
│ 3. TRADING, STRATEGY & AUTO-DRAW (TradingEngine, AutoDrawEngine)            │
│    - Modos: AUTÓNOMO, SEMIAUTOMÁTICO, DESACTIVADO                           │
│    - Estrategias Master Traders: Rejection Wicks, Choque/Pullback, 3-Velas  │
│    - Trazado dinámico de figuras técnicas en CustomDrawingView              │
├─────────────────────────────────────────────────────────────────────────────┤
│ 4. RISK & CALIBRATION LAYER (RiskManager, CalibrationManager, Access. Serv) │
│    - RiskManager: Stop Loss por racha, Take Profit, Cooldown, Martingala    │
│    - CalibrationManager: Pines arrastrables (SUBE / BAJA) por orientación   │
│    - AutoTradeAccessibilityService: Clics simulados con dispatchGesture     │
├─────────────────────────────────────────────────────────────────────────────┤
│ 5. REMOTE AI & DIAGNOSTICS (AIClient, CrashLogger)                          │
│    - Cliente HTTP compatible con OpenAI / OmniRoute (/v1/chat/completions)  │
│    - Fallback automático a visión local si la IA remota no responde         │
│    - CrashLogger con persistencia en SharedPreferences y toast de arranque  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Descripción Detallada de Archivos Clave:

| Archivo | Responsabilidad |
| :--- | :--- |
| **`OverlayService.kt`** | Servicio Foreground maestro (`MEDIA_PROJECTION` + `SPECIAL_USE`). Administra las 4 ventanas: Lienzo (`canvasView`), Burbuja (`menuView`), Submenú lateral (`submenuWindowView`) y HUD (`hudView`). Controla z-order con `bringMenuToFront()` y ciclo de vida de rotación con `onConfigurationChanged`. |
| **`CustomDrawingView.kt`** | Lienzo táctil transparente sobre pantalla completa. Soporta trazo libre, líneas de tendencia, rayos, horizontales/verticales, canales paralelos, triángulos, círculos, zonas con relleno, medición en px/%, selector libre de color (`ColorWheelView.kt`) y `STRIKE_PRICE_LINE`. |
| **`ScreenCaptureManager.kt`** | Captura continua a 1 frame/segundo usando `MediaProjection` y `ImageReader` en un `HandlerThread` dedicado. Recrea automáticamente el `VirtualDisplay` ante rotaciones de pantalla (`refreshVirtualDisplay()`). |
| **`VisionAnalyzer.kt`** | Analizador de visión por color HSV para temas oscuros de Binomo/Quotex. Delimita zonas de gráfico diferenciadas para **Landscape** (`X: 8%-76%, Y: 28%-74%`) y **Portrait** (`X: 6%-94%, Y: 30%-64%`). Extrae geometría de velas (`CandleData`), racha, fuerza de señal (% CALL vs % PUT), y detecta banners de Win/Loss (`detectTradeOutcome()`). |
| **`TradingEngine.kt`** | Motor principal de decisión. Ejecuta las estrategias de acción del precio (Master Traders y clásicas) y coordina el flujo: evalúa IA remota vía `AIClient` (si está activa) o reglas locales de `VisionAnalyzer`, ejecuta clics vía `AutoTradeAccessibilityService`, bloquea trades dobles con `riskManager.hasPendingTrade` y activa el auto-dibujo. |
| **`AutoDrawEngine.kt`** | Genera y actualiza figuras programáticas con `isBotDrawn = true` sobre el lienzo (Soportes rojos, Resistencias verdes, círculos de rechazo, rayos de tendencia y `STRIKE_PRICE_LINE` dinámico). |
| **`RiskManager.kt`** | Control de gestión de riesgo: Stop Loss por racha de pérdidas, Take Profit por victorias objetivo, Cooldown en segundos, niveles de Martingala (`M0`, `M1`, `M2`) y cálculo de montos sugeridos. |
| **`CalibrationManager.kt`** | Sistema de calibración interactivo con pines circulares compactos (`38dp` con mira central) para `SUBE` y `BAJA`. Guarda coordenadas independientes por orientación (`_land_` y `_port_`) en `SharedPreferences`. |
| **`AutoTradeAccessibilityService.kt`** | Servicio de accesibilidad de Android que ejecuta gestos táctiles reales en las coordenadas calibradas de Binomo mediante `dispatchGesture()`. |
| **`AIClient.kt`** | Conector HTTP con endpoints de visión compatibles con OpenAI / OmniRoute. Redimensiona imágenes a 640px en JPEG base64 y procesa respuestas JSON estructuradas (`action`, `confidence`, `reason`). |
| **`MainActivity.kt`** | Actividad inicial que verifica permisos (`SYSTEM_ALERT_WINDOW`, `ACCESSIBILITY`, `MEDIA_PROJECTION`), muestra el estado en vivo de accesibilidad y lanza el `OverlayService`. |

---

## 🎯 5. Estrategias de Acción del Precio de "Master Traders"

El motor cuenta con las estrategias de acción del precio basadas en la operativa de Master Traders para Binomo:

1. **`MT_MASTER_COMBO` (Recomendado):** Evalúa de forma priorizada: Mechas de Rechazo $\rightarrow$ Choque de Niveles $\rightarrow$ Agotamiento de 3 Velas.
2. **`MT_REJECTION` (Mechas de Rechazo en S/R):** Detecta velas con mecha $\ge 40\%$ del rango total rebotando contra soportes o resistencias.
3. **`MT_CHOQUE_PULLBACK` (Breakout + Retest):** Detecta rompimientos de máximos/mínimos y opera el retest en el punto de contacto exacto con el nivel roto.
4. **`MT_3_VELAS_AGOTAMIENTO`:** Identifica secuencias de 3 velas consecutivas del mismo color con cuerpos decrecientes ($Vela_1 > Vela_2 > Vela_3$) y opera la reversión en la 4ª vela.

---

## 🚨 6. Reglas Críticas de Desarrollo y "Gotchas" Conocidos

1. **Soporte Bidireccional de Orientación (Landscape y Portrait):**
   - Todo componente visual, zona de escaneo y coordenadas de calibración **DEBEN soportar tanto Horizontal como Vertical**.
   - En Landscape, los botones de Binomo se ubican a la derecha (`X: 88%, Y: 72%` para Sube y `X: 88%, Y: 86%` para Baja). En Portrait, se ubican abajo.
2. **Control de Z-Order en Overlay:**
   - La ventana del lienzo (`canvasView`) ocupa pantalla completa. Para evitar que tape los botones de la burbuja o submenús, cualquier actualización de flags debe re-insertar el menú encima usando `bringMenuToFront()`.
3. **Manejo de Hilos (Threading):**
   - El procesamiento de imágenes y la comunicación de red (`ScreenCaptureManager`, `AIClient`) se ejecutan en hilos secundarios (`HandlerThread` / worker).
   - Cualquier modificación en vistas, HUD o lienzo debe despacharse obligatoriamente al hilo principal con `mainHandler.post { ... }` o `handler.post { ... }`.
4. **Validación Sintáctica antes de Commit:**
   - Verifica el balance de llaves `{}` en todos los archivos `.kt` modificados antes de hacer commit.

---

## 📦 7. Procedimiento de Verificación de Cambios

Tras realizar modificaciones en el código:
```bash
# 1. Comprobar estado de archivos
git status --short

# 2. Agregar y commitear con mensaje descriptivo
git add -A
git commit -m "feat/fix: descripción clara del cambio"

# 3. Empujar directamente a main
git push origin main
```
Luego, verifica en la pestaña de **Actions** del repositorio de GitHub que el build haya concluido en verde (✓) y deja el enlace al artefacto descargable para el usuario.
