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

## âš¡ 8. AutomatizaciÃ³n y AuditorÃ­a del Dispositivo (ADB sobre Wi-Fi)

### 8.0 Dispositivo real y acceso (corregido en Ronda 13)

> **El dispositivo NO es un "POCO X6 Pro".** AfirmaciÃ³n repetida en rondas anteriores sin
> verificarla. Datos reales de `getprop`:
>
> | Propiedad | Valor real |
> | :--- | :--- |
> | `ro.product.model` | **`220733SL`** |
> | `ro.product.marketname` | **Redmi A1** |
> | Android | **12** (Go Edition, `ro.config.low_ram = true`) |
> | SoC | `mt6761`, **4 nÃºcleos**, `armeabi-v7a` |
> | RAM | 1.875.668 kB totales, ~71 MB libres, ~400 MB de swap en uso |
>
> **Esto invalida cualquier afirmaciÃ³n de latencia o rendimiento hecha asumiendo un telÃ©fono
> potente.** `loadavg` ronda **16-20** con la app corriendo: hay un orden de magnitud menos de
> CPU de lo supuesto. Medido en R13 con 30 muestras bajo carga real, un `su -c input tap` tarda
> **255-1205 ms (mediana â‰ˆ 500 ms)**, con el **80 % por encima de 350 ms**, frente a los ~10 ms
> que se declaraban. **Cualquier temporizador corto es frÃ¡gil aquÃ­**: ir con failsafe y, mejor,
> cerrarse por evento (asÃ­ se corrigiÃ³ el bypass del HUD con `restoreHudTouchable`).
>
> **GeometrÃ­a medida (720x1600, Portrait, `SurfaceOrientation: 0`)** â€” Ãºtil para saber quÃ©
> ventana recibe un toque. HUD: frame `[96,812]-[720,1302]`, tÃ¡ctil. Lienzo a pantalla completa:
> `[0,50]-[720,1504]`, **no tÃ¡ctil** (`FLAG_NOT_TOUCHABLE`). Botones de Binomo
> (`w*0.347, h*0.838` / `h*0.844`): **(249,8 Â· 1340,8)** y **(249,8 Â· 1350,4)**. **El HUD no
> cubre los botones**, asÃ­ que `isPointInsideHUD` es `false` y el bypass del HUD **no se aplica**
> en la configuraciÃ³n por defecto.

**Serial ADB Wi-Fi: `192.168.1.225:5555`** (Ronda 13, tras el reinicio del telÃ©fono; antes
`192.168.1.196`, que **ya no vale**). **La IP la asigna el DHCP y CAMBIA al reiniciar.**

> **CorrecciÃ³n (R13/adenda 5):** el enunciado de R13 afirmaba que el serial USB
> `UGU8KFUKM7E6CE8D` "ya no existe". **Es falso**: tras reiniciar el telÃ©fono reapareciÃ³ como
> `UGU8KFUKM7E6CE8D device` y responde con normalidad. Lo que pasaba al abrir R13 es que **no
> estaba enumerado**, no que hubiera desaparecido. **Ambos transportes funcionan**; usa Wi-Fi
> salvo que necesites reactivar `adbd` TCP, que sÃ­ requiere el cable un momento.

```bash
adb devices                              # debe listar 192.168.1.225:5555  device
adb connect 192.168.1.225:5555           # si no aparece
adb -s 192.168.1.225:5555 forward tcp:8080 tcp:8080
```

**Si el telÃ©fono se reinicia, la IP probablemente cambie. No improvisar:**

1. Conectar el cable un momento y localizar el dispositivo por USB
   (`adb devices` â†’ `UGU8KFUKM7E6CE8D`).
2. `adb -s UGU8KFUKM7E6CE8D tcpip 5555` â€” **no sobrevive al reinicio**, hay que repetirlo.
3. **Medir la IP real** en vez de suponerla: `adb -s UGU8KFUKM7E6CE8D shell ip -f inet addr show wlan0`.
4. `adb connect <IP>:5555` y **avisar al usuario de cuÃ¡ndo puede desconectar el cable**.

### 8.1 Bridge HTTP: SOLO loopback (Ronda 13, B1)

`:8080` **ya no escucha en la LAN.** Antes `ServerSocket(port)` se enlazaba a `[::]`, asÃ­ que
cualquiera en la misma red Wi-Fi podÃ­a leer `/status` y, peor, disparar `/trade`, que ejecuta
taps reales contra el broker. Ahora el socket se enlaza explÃcitamente a loopback y el acceso
se hace **siempre** por `adb forward`:

```bash
adb -s 192.168.1.225:5555 forward tcp:8080 tcp:8080
curl http://127.0.0.1:8080/status         # funciona
curl http://192.168.1.225:8080/status     # NO debe responder (verificado)
```

**USO OBLIGATORIO de `127.0.0.1:8080`, nunca la IP del telÃ©fono.** La IP del telÃ©fono sirve
solo para ADB, no para el bridge.

### 8.2 Observabilidad sin `logcat` (Ronda 13)

**`logcat` NO funciona en este dispositivo y no es un problema de transporte.** Causa raÃz
confirmada: `init.svc.logd = stopped` y `/dev/log*` no existe; `start logd` responde
`Must be root` y `adb root` responde `adbd cannot run as root in production builds`
(`ro.build.type=user`, `ro.debuggable=0`). Falla igual por USB y por Wi-Fi.

> **Pero VUELVE con un reinicio (R13/adenda 5).** Tras reiniciar el telÃ©fono,
> `init.svc.logd = running` y `logcat -d` devuelve entradas reales. **`logd` no estÃ¡ roto de
> fÃ¡brica: se cae durante el uso.** La causa sigue **sin atribuir**; lo medido descarta presiÃ³n
> de memoria (`MemAvailable` 680-739 MB, **0** `am_kill` por memoria, las 7 de la ventana con
> motivo `empty #N`, **0** ANR/OOM, **0** muertes de la app, Binomo o SystemUI). **Si necesitas
> `logcat`, reinicia; no pierdas tiempo intentando levantarlo en caliente.**

Consecuencia mientras estÃ© caído: **los vetos y las transiciones de estado no son observables**,
y por eso R12 no pudo demostrar ni una sola vez que un veto se disparara. La observabilidad
pasa a depender de:

- **`GET /events`** â€” buffer circular en memoria (200 eventos) con vetos y su motivo,
  transiciones de `orderState`, taps despachados (coordenadas, duraciÃ³n, resultado) y cambios
  de activo.
- **`/status`** â€” contadores agregados: `vetoV1`, `vetoV2`, `vetoTotal`, `ultimoVeto`,
  `tapsDespachados`.

### 8.2-bis Broadcasts de comando: exigen permiso `signature` (Ronda 16)

> **Desde `af71997` los broadcasts de comando exigen el permiso `signature`
> `com.example.tradedraw.permission.INTERNAL_COMMAND`. Desde `adb shell` NO llegan; usar
> `su -c` y SOLO para pruebas controladas.**

Afecta a **ambos** receptores dinámicos:

| acción | receptor | efecto |
| :--- | :--- | :--- |
| `com.example.tradedraw.CMD` (extras `action=AUTO/YOLO/SEMI/STOP/...`) | `OverlayService.overlayCommandReceiver` | `AUTO`/`YOLO` **rearman el motor a `AUTONOMOUS`** |
| `com.example.tradedraw.TAP` / `.SWIPE` / `.BACK` | `AutoTradeAccessibilityService.commandReceiver` | **ejecutan taps y gestos reales** vía `dispatchGesture` |

**Consecuencias prácticas para cualquier agente de este proyecto:**

- `adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO` **ya no hace nada**.
  Devuelve `Broadcast completed: result=0` **sin error**, así que **no** sirve comprobar el
  resultado del comando: hay que comprobar el **efecto** (`/status` → `mode`).
- La vía que sigue funcionando es **`su -c 'am broadcast ...'`** (UID 0). Requiere root, que este
  dispositivo tiene (Magisk).
- **La app NUNCA emite estos broadcasts** (cero `sendBroadcast` en el código): sólo los consumía
  desde fuera. Por eso cerrarlos no rompió ningún flujo interno.
- **NO** sustituir el permiso por `Context.RECEIVER_NOT_EXPORTED`: existe **sólo desde API 33** y
  este dispositivo es **API 31**, así que la rama `else` dejaría el receptor **exportado** otra vez
  (el bug que se acaba de cerrar). El permiso de firma funciona desde API 1.
- Los dos emisores históricos del repo (`test_master_trader_suite.py:65` con `--es command
  TEST_PULSE`, y `docs/superpowers/plans/...:754` con la acción `com.example.tradedraw.TEST`) eran
  **inertes ya antes del parche**: el receptor lee el extra `action`, no `command`, y la acción
  `.TEST` no tenía receptor. No hay regresión.
- Consulta [`loops/R16_A_REPORTE.md`](loops/R16_A_REPORTE.md) para la evidencia medida (control
  positivo con root) y [`workflows/PREREGISTRO_R16.md`](workflows/PREREGISTRO_R16.md) para el
  criterio de éxito y el límite de alcance.

### 8.3 Capturas y comandos: restricciones de este dispositivo

- **PowerShell 5.1: NO usar `adb exec-out screencap -p` con pipe** (corrompe el PNG). Usar
  `screencap -p /sdcard/x.png`, `adb pull`, `adb shell rm`.
- **NO ejecutar `find` sobre `/sdcard/Android/data`**: cuelga la sesiÃ³n.
- `uiautomator dump` **falla a menudo** y en la pantalla de trading de Binomo devuelve un
  Ã¡rbol vacÃo (anti-automatizaciÃ³n). No es un fallo del comando.
- **BaterÃ­a:** el telÃ©fono ya no carga de forma fiable. Medir
  `adb shell dumpsys battery | grep level` **al empezar y al terminar** cada ronda y
  reportarlo. Si baja del 20 %, detener las pruebas y pedir al usuario que lo cargue.
  Minimizar sesiones largas con pantalla encendida y rÃ¡fagas de capturas.
- **No cambiar ajustes del telÃ©fono** (suspensiÃ³n de Wi-Fi, etc.) sin preguntar al usuario.

### 8.4 MCP `android-vision`

El MCP `android-vision` (basado en `mcp-scrcpy-vision`) **no estÃ¡ disponible como herramienta
en todas las sesiones**; en la Ronda 13 no lo estaba. Cuando **no** lo estÃ©, la vÃa es
`adb shell ...` + `adb pull` segÃºn 8.3. Cuando sÃ­ lo estÃ©, su serial debe ser el de 8.0
(`192.168.1.225:5555`), **no** el USB ni las IP retiradas.

| Herramienta MCP | ParÃ¡metros |
| :--- | :--- |
| `android.vision.snapshot` | `{ "serial": "192.168.1.225:5555" }` |
| `android.input.tap` | `{ "x": <x>, "y": <y>, "serial": "192.168.1.225:5555" }` |
| `android.input.swipe` | `{ "x1":, "y1":, "x2":, "y2":, "durationMs": }` |
| `android.ui.dump` / `android.ui.findElement` | jerarquÃ­a de vistas |
| `android.vision.startStream` / `stopStream` | H.264 en vivo |

---

## ❓ 9. Formato Obligatorio para Preguntas al Usuario (Ask Questions / Selección Rápida)

> ⚡ **REGLA DE ORO DE INTERACCIÓN**:
> - **CERO preguntas abiertas que requieran que el usuario escriba párrafos o texto extenso.**
> - **Formato Opciones Múltiples:** Siempre formular las preguntas con opciones claras identificadas por letras (`A)`, `B)`, `C)`, `D)`).
> - **Una Pregunta por Turno:** Entregar una sola pregunta a la vez en sesiones interactivas (o grilling) para que el usuario responda escribiendo únicamente una letra (`A`).
> - **Recomendación Destacada:** Siempre indicar `➡️ Opción recomendada: [Letra]` con la justificación técnica resumida, de modo que el usuario pueda simplemente confirmar o elegir otra letra al instante.
