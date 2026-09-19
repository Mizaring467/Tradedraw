# AGENTS.md Â· TradeDraw Development Guide

Este documento proporciona el contexto completo, la arquitectura tÃ©cnica, las reglas de desarrollo y los procedimientos de compilaciÃ³n de **TradeDraw** para cualquier agente de cÃ³digo autÃ³nomo o asÃ­ncrono (**Jules, Hermes, DeepSeek Harness, OpenCode, Claude Code, etc.**).

---

## âš¡ 1. Regla de Oro para Agentes (Flujo Directo a `main`)

1. **NO abrir Pull Requests innecesarios.** A menos que el usuario lo pida explÃ­citamente, realiza los cambios, haz commit y push **DIRECTAMENTE a la rama `main`**.
2. **CI/CD Automatizado:** Cada push a `main` activa el pipeline de GitHub Actions (`.github/workflows/android-build.yml`), el cual compila automÃ¡ticamente el APK de depuraciÃ³n:
   - **Enlace de Descarga del APK:** `https://github.com/Mizaring467/Tradedraw/actions` âž” Artefacto: **`TradeDraw-debug`** (`app-debug.apk`).
3. **Entrega al Usuario:** Al finalizar tu tarea, proporciona siempre el enlace de GitHub Actions confirmando que el APK estÃ¡ listo para instalarse.

---

## ðŸ“± 2. PropÃ³sito y Dominio del Proyecto

**TradeDraw** es una aplicaciÃ³n mÃ³vil nativa para Android diseÃ±ada para anÃ¡lisis tÃ©cnico cuantitativo y auto-trading en tiempo real sobre brokers de opciones binarias y trading mÃ³vil (**Binomo, Quotex, PocketOption, TradingView**).

- **MecÃ¡nica de Opciones Binarias:**
  - Las operaciones son a tiempo de expiraciÃ³n fijo (generalmente 1 minuto / 60 segundos).
  - No existe Stop Loss ni Take Profit en distancia de pips; solo importa si el precio al vencimiento cierra estrictamente por encima (`CALL / SUBE`) o por debajo (`PUT / BAJA`) del precio de entrada (*Strike Price*).
  - El sistema utiliza la herramienta visual nativa `STRIKE_PRICE_LINE` (lÃ­nea punteada que cambia dinÃ¡micamente entre **Verde ITM** y **Rojo OTM** en tiempo real).
- **Activos Principales:** Crypto IDX (Binomo), Pares de divisas OTC (CHF/JPY OTC, EUR/USD OTC), Criptomonedas (BTC, SOL).

---

## ðŸ› ï¸ 3. Stack TecnolÃ³gico y ConfiguraciÃ³n de Build

- **Lenguaje:** Kotlin 1.9.24.
- **Plataforma:** Android nativo, SDK de compilaciÃ³n 34 (Android 14), `minSdk 24` (Android 7.0+).
- **Build System:** Gradle 9.5 (Kotlin DSL), Android Gradle Plugin (AGP) 8.5.2, Java 17 (Temurin).
- **Comando de CompilaciÃ³n:**
  ```bash
  ./gradlew assembleDebug --no-daemon --no-configuration-cache
  ```
  *(Nota crÃ­tica: `configuration-cache` debe permanecer deshabilitado por incompatibilidad entre Gradle 9.5 y el plugin Kotlin 1.9.24).*

---

## ðŸ›ï¸ 4. Arquitectura Modular del CÃ³digo

El proyecto estÃ¡ estructurado en mÃ³dulos desacoplados bajo el paquete `com.example.tradedraw`:

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ 1. OVERLAY & UI LAYER (OverlayService, CustomDrawingView)                   â”‚
â”‚    - 4 Ventanas flotantes TYPE_APPLICATION_OVERLAY con FLAG_NOT_FOCUSABLE   â”‚
â”‚    - Canvas de dibujo con herramientas TradingView y Strike Price ITM/OTM    â”‚
â”‚    - HUD de Trading con temporizador 60s, termÃ³metro % CALL/PUT y Martingalaâ”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ 2. SCREEN VISION & CAPTURE LAYER (ScreenCaptureManager, VisionAnalyzer)     â”‚
â”‚    - MediaProjection continuo en HandlerThread (Android 10 - 14)            â”‚
â”‚    - DetecciÃ³n HSV de velas verdes/rojas, mechas y extremos S/R             â”‚
â”‚    - DetecciÃ³n automÃ¡tica de resultado de trade (Win/Loss por banner)       â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ 3. TRADING, STRATEGY & AUTO-DRAW (TradingEngine, AutoDrawEngine)            â”‚
â”‚    - Modos: AUTÃ“NOMO, SEMIAUTOMÃTICO, DESACTIVADO                           â”‚
â”‚    - Estrategias Master Traders: Rejection Wicks, Choque/Pullback, 3-Velas  â”‚
â”‚    - Trazado dinÃ¡mico de figuras tÃ©cnicas en CustomDrawingView              â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ 4. RISK & CALIBRATION LAYER (RiskManager, CalibrationManager, Access. Serv) â”‚
â”‚    - RiskManager: Stop Loss por racha, Take Profit, Cooldown, Martingala    â”‚
â”‚    - CalibrationManager: Pines arrastrables (SUBE / BAJA) por orientaciÃ³n   â”‚
â”‚    - AutoTradeAccessibilityService: Clics simulados con dispatchGesture     â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ 5. REMOTE AI & DIAGNOSTICS (AIClient, CrashLogger)                          â”‚
â”‚    - Cliente HTTP compatible con OpenAI / OmniRoute (/v1/chat/completions)  â”‚
â”‚    - Fallback automÃ¡tico a visiÃ³n local si la IA remota no responde         â”‚
â”‚    - CrashLogger con persistencia en SharedPreferences y toast de arranque  â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

---

### DescripciÃ³n Detallada de Archivos Clave:

| Archivo | Responsabilidad |
| :--- | :--- |
| **`OverlayService.kt`** | Servicio Foreground maestro (`MEDIA_PROJECTION` + `SPECIAL_USE`). Administra las 4 ventanas: Lienzo (`canvasView`), Burbuja (`menuView`), SubmenÃº lateral (`submenuWindowView`) y HUD (`hudView`). Controla z-order con `bringMenuToFront()` y ciclo de vida de rotaciÃ³n con `onConfigurationChanged`. |
| **`CustomDrawingView.kt`** | Lienzo tÃ¡ctil transparente sobre pantalla completa. Soporta trazo libre, lÃ­neas de tendencia, rayos, horizontales/verticales, canales paralelos, triÃ¡ngulos, cÃ­rculos, zonas con relleno, mediciÃ³n en px/%, selector libre de color (`ColorWheelView.kt`) y `STRIKE_PRICE_LINE`. |
| **`ScreenCaptureManager.kt`** | Captura continua a 1 frame/segundo usando `MediaProjection` y `ImageReader` en un `HandlerThread` dedicado. Recrea automÃ¡ticamente el `VirtualDisplay` ante rotaciones de pantalla (`refreshVirtualDisplay()`). |
| **`VisionAnalyzer.kt`** | Analizador de visiÃ³n por color HSV para temas oscuros de Binomo/Quotex. Delimita zonas de grÃ¡fico diferenciadas para **Landscape** (`X: 8%-76%, Y: 28%-74%`) y **Portrait** (`X: 6%-94%, Y: 30%-64%`). Extrae geometrÃ­a de velas (`CandleData`), racha, fuerza de seÃ±al (% CALL vs % PUT), y detecta banners de Win/Loss (`detectTradeOutcome()`). |
| **`TradingEngine.kt`** | Motor principal de decisiÃ³n. Ejecuta las estrategias de acciÃ³n del precio (Master Traders y clÃ¡sicas) y coordina el flujo: evalÃºa IA remota vÃ­a `AIClient` (si estÃ¡ activa) o reglas locales de `VisionAnalyzer`, ejecuta clics vÃ­a `AutoTradeAccessibilityService`, bloquea trades dobles con `riskManager.hasPendingTrade` y activa el auto-dibujo. |
| **`AutoDrawEngine.kt`** | Genera y actualiza figuras programÃ¡ticas con `isBotDrawn = true` sobre el lienzo (Soportes rojos, Resistencias verdes, cÃ­rculos de rechazo, rayos de tendencia y `STRIKE_PRICE_LINE` dinÃ¡mico). |
| **`RiskManager.kt`** | Control de gestiÃ³n de riesgo: Stop Loss por racha de pÃ©rdidas, Take Profit por victorias objetivo, Cooldown en segundos, niveles de Martingala (`M0`, `M1`, `M2`) y cÃ¡lculo de montos sugeridos. |
| **`CalibrationManager.kt`** | Sistema de calibraciÃ³n interactivo con pines circulares compactos (`38dp` con mira central) para `SUBE` y `BAJA`. Guarda coordenadas independientes por orientaciÃ³n (`_land_` y `_port_`) en `SharedPreferences`. |
| **`AutoTradeAccessibilityService.kt`** | Servicio de accesibilidad de Android que ejecuta gestos tÃ¡ctiles reales en las coordenadas calibradas de Binomo mediante `dispatchGesture()`. |
| **`AIClient.kt`** | Conector HTTP con endpoints de visiÃ³n compatibles con OpenAI / OmniRoute. Redimensiona imÃ¡genes a 640px en JPEG base64 y procesa respuestas JSON estructuradas (`action`, `confidence`, `reason`). |
| **`MainActivity.kt`** | Actividad inicial que verifica permisos (`SYSTEM_ALERT_WINDOW`, `ACCESSIBILITY`, `MEDIA_PROJECTION`), muestra el estado en vivo de accesibilidad y lanza el `OverlayService`. |

---

## 🎯 5. Habilidad Obligatoria: `master_traders_skill` (Gobernanza Letra por Letra)

Todo agente autónomo o asistente de desarrollo que intervenga en **TradeDraw** tiene la obligación categórica de seguir **letra por letra** las reglas, fórmulas, filtros temporales y gestión de capital definidas en `master_traders_skill` (ubicada en `.agents/skills/master_traders_skill/SKILL.md` y en la configuración global de Antigravity).

### 5.1 Principio de Ventaja Cuantitativa
- **Punto de Equilibrio (Breakeven):** $p_{\text{BE}} = \frac{1}{1 + b}$ (54.95% para payout del 82%).
- **Objetivo Matemático:** Mantener un win rate entre el **62% y 73%** mediante confluencia estricta.

### 5.2 Estrategias Nucleares de Acción del Precio Pura (60s)
1. **`MT_REJECTION` (Mechas de Rechazo en S/R):**
   - Rango total mínimo: $R_{\text{total}} \ge 12\text{ px}$.
   - Ratio de mecha de rechazo: $\text{Ratio}_{\text{wick}} \ge 0.45$ ($\ge 45\%$ del rango total).
   - Proporción de cuerpo real: $\frac{B}{R_{\text{total}}} \le 0.40$ ($\le 40\%$).
   - Tolerancia a zona S/R o nivel institucional redondo (`.000`, `.500`, `.200`, `.800`): $\le 4\text{ px}$.
   - En CALL: mecha inferior $\ge 0.45$, cuerpo cierra protegido por encima del soporte.
   - En PUT: mecha superior $\ge 0.45$, cuerpo cierra protegido por debajo de la resistencia.
2. **`MT_3_VELAS_AGOT` (Agotamiento Cuantitativo de 3 Velas):**
   - Paridad cromática estricta: $\text{Color}(C_1) = \text{Color}(C_2) = \text{Color}(C_3)$ (3 rojas o 3 verdes seguidas).
   - Decaimiento monótono: $B_1 > B_2 > B_3$.
   - Contracción cuantitativa: $B_3 \le 0.45 \cdot B_1$ y $B_2 \le 0.80 \cdot B_1$.
   - Proximidad a nivel S/R en $C_3$: $\le 10\text{ px}$.
   - Invalidación (*Falling Knife*): Veto si $B_3 \ge B_2$ o rango sumado $> 3.5\times$ ATR sin mecha opuesta. Entrada a reversión en apertura de 4ª vela.
3. **`MT_CHOQUE_PULLBACK` (Polaridad Breakout + Retest):**
   - Ruptura válida: cuerpo fuera de S/R $> 50\%$ y mecha frontal opuesta $< 20\%$.
   - Choque/Retest: contacto exacto $\le 3\text{ px}$ con el nivel roto.
   - Ventana temporal estricta de choque: segundos **:01 a :05** exclusivamente.
4. **`MT_FALSE_BREAKOUT` (Trampa Institucional / Falso Rompimiento):**
   - Falsa penetración de nivel con reingreso y cierre al interior del canal, dejando mecha $\ge 0.35$.

### 5.3 Filtros Anti-Derrotas Obligatorios
- **Ventana Sniper Cronológica:**
  - Entrada estándar: segundos **:58 a :03**.
  - Entrada Sniper Pullback: segundos **:01 a :05** (únicamente con descuento de strike).
  - **VETO TOTAL UNIVERSAL:** Entre los segundos **:06 y :57** queda rotundamente prohibido disparar órdenes.
- **Filtro Anti-Choppiness:**
  - $\text{CHOP} > 61.8 \implies$ Veto total de continuación/tendencia.
  - Ratio de Dojis ($\text{cuerpo} \le 4\text{ px}$) $\ge 0.35 \implies$ Veto por mercado sucio.
  - Alternancia de color ($V \to R \to V \to R$ con desplazamiento $< 2.5\%$) $\implies$ Veto.

### 5.4 Matriz de Confluencia (Umbral $\ge 80$ puntos)
- Sincronización de reloj obligatoria, suma ponderada de factores técnicos. Disparo automatizado únicamente si el puntaje total es $\ge 80\text{ pts}$.

### 5.5 Gestión de Capital Cuantitativa (Quarter-Kelly)
- $f^* = p - \frac{1 - p}{b}$, Operativo: $f_{\text{operativo}} = \min(0.25 \times f^*, 0.035)$.
- Dimensionamiento acotado estrictamente entre el **1.0%** y el **3.5%** del balance total.

---

## ðŸš¨ 6. Reglas CrÃ­ticas de Desarrollo y "Gotchas" Conocidos

1. **Soporte Bidireccional de OrientaciÃ³n (Landscape y Portrait):**
   - Todo componente visual, zona de escaneo y coordenadas de calibraciÃ³n **DEBEN soportar tanto Horizontal como Vertical**.
   - En Landscape, los botones de Binomo se ubican a la derecha (`X: 88%, Y: 72%` para Sube y `X: 88%, Y: 86%` para Baja). En Portrait, se ubican abajo.
2. **Control de Z-Order en Overlay:**
   - La ventana del lienzo (`canvasView`) ocupa pantalla completa. Para evitar que tape los botones de la burbuja o submenÃºs, cualquier actualizaciÃ³n de flags debe re-insertar el menÃº encima usando `bringMenuToFront()`.
3. **Manejo de Hilos (Threading):**
   - El procesamiento de imÃ¡genes y la comunicaciÃ³n de red (`ScreenCaptureManager`, `AIClient`) se ejecutan en hilos secundarios (`HandlerThread` / worker).
   - Cualquier modificaciÃ³n en vistas, HUD o lienzo debe despacharse obligatoriamente al hilo principal con `mainHandler.post { ... }` o `handler.post { ... }`.
4. **ValidaciÃ³n SintÃ¡ctica antes de Commit:**
   - Verifica el balance de llaves `{}` en todos los archivos `.kt` modificados antes de hacer commit.

---

## ðŸ“¦ 7. Procedimiento de VerificaciÃ³n de Cambios

Tras realizar modificaciones en el cÃ³digo:
```bash
# 1. Comprobar estado de archivos y compilar
./gradlew assembleDebug --no-daemon --no-configuration-cache

# 2. Instalar de inmediato en el dispositivo conectado vÃ­a ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Agregar y commitear con mensaje descriptivo
git add -A
git commit -m "feat/fix: descripciÃ³n clara del cambio"

# 4. Empujar directamente a main
git push origin main
```
Luego, verifica en la pestaÃ±a de **Actions** del repositorio de GitHub que el build haya concluido en verde (âœ“) y deja el enlace al artefacto descargable para el usuario.

> ⚡ **Regla Estricta y Memoria Permanente:**
> 1. **Siempre al terminar una tarea o cambio**, compila e instala de inmediato la app en el teléfono vía `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
> 2. **El probador (`tester`) DEBE probar la app en el dispositivo** (verificación visual, logs de runtime y flujos clave) y **emitir su reporte formal** con evidencia para validar el avance y continuar con la siguiente iteración.

---

## âš¡ 8. AutomatizaciÃ³n y AuditorÃ­a del Dispositivo con MCP scrcpy (`android-vision`)

Para interactuar con el dispositivo Android (POCO X6 Pro / `10.218.252.45:5555` o IP activa asignada por DHCP en `adb devices`) a alta velocidad y bajo consumo de recursos, este proyecto tiene configurado el servidor MCP **`android-vision`** (basado en `mcp-scrcpy-vision` con soporte de `scrcpy-server` v4.1 y `ffmpeg`):

### Herramientas MCP disponibles (vÃ­a `call_mcp_tool`):
1. **Captura de Pantalla InstantÃ¡nea:**
   - Herramienta: `android.vision.snapshot`
   - ParÃ¡metros: `{ "serial": "10.218.252.45:5555" }`
   - *Ventaja:* Retorna la imagen en ~1s sin recalentar la CPU del telÃ©fono con `screencap` repetidos.
2. **PulsaciÃ³n / Tap Ultra RÃ¡pido:**
   - Herramienta: `android.input.tap`
   - ParÃ¡metros: `{ "x": <coordX>, "y": <coordY>, "serial": "10.218.252.45:5555" }`
   - *Ventaja:* InyecciÃ³n de evento directa (< 10 ms vs ~400 ms de `adb shell input tap`).
3. **Desplazamiento y Gestos:**
   - `android.input.swipe`: `{ "x1": <x1>, "y1": <y1>, "x2": <x2>, "y2": <y2>, "durationMs": <ms> }`
4. **InspecciÃ³n de UI DinÃ¡mica:**
   - `android.ui.dump` / `android.ui.findElement` para inspeccionar jerarquÃ­a de vistas de forma mucho mÃ¡s rÃ¡pida que `uiautomator dump` tradicional.
5. **Streaming en Tiempo Real (H.264):**
   - `android.vision.startStream` / `android.vision.stopStream` para monitoreo continuo mediante resource frames.

> âš ï¸  **Regla para Agentes:** **NO** ejecutes comandos de PowerShell lentos como `adb shell screencap` o `adb shell input tap` salvo que el MCP no responda; utiliza siempre las herramientas del servidor MCP `android-vision` para mÃ¡xima velocidad y estabilidad.

---

## ❓ 9. Formato Obligatorio para Preguntas al Usuario (Ask Questions / Selección Rápida)

> ⚡ **REGLA DE ORO DE INTERACCIÓN**:
> - **CERO preguntas abiertas que requieran que el usuario escriba párrafos o texto extenso.**
> - **Formato Opciones Múltiples:** Siempre formular las preguntas con opciones claras identificadas por letras (`A)`, `B)`, `C)`, `D)`).
> - **Una Pregunta por Turno:** Entregar una sola pregunta a la vez en sesiones interactivas (o grilling) para que el usuario responda escribiendo únicamente una letra (`A`).
> - **Recomendación Destacada:** Siempre indicar `➡️ Opción recomendada: [Letra]` con la justificación técnica resumida, de modo que el usuario pueda simplemente confirmar o elegir otra letra al instante.
