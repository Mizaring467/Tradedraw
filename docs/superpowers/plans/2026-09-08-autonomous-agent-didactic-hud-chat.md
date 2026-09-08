# Agente Autónomo Integral, HUD Móvil Didáctico y Chat en Vivo en TradeDraw

Transformar el bot de TradeDraw en un agente autónomo de trading capaz de auto-gestionarse (controlar la app y el broker, auto-corregir datos y métricas en el HUD), dotar al HUD de libertad de movimiento total confinado a pantalla sin obstruir operaciones, enriquecerlo con explicaciones didácticas en tiempo real (tendencia detectada, jugada planeada) y permitir al usuario comunicarse con el bot vía chat interactivo en vivo.

## User Review Required

> [!IMPORTANT]
> El HUD podrá ser movido libremente por toda la pantalla (incluyendo la zona inferior). Para evitar que tape los botones de Binomo al momento de un clic, se implementará un bypass táctil momentáneo (`FLAG_NOT_TOUCHABLE` durante el despacho del gesto) o un desplazamiento preventivo si coincide exactamente con el botón objetivo.

> [!NOTE]
> El chat en vivo del agente utilizará la configuración actual de `AIClient` conectada a OmniRoute/OpenAI (`http://localhost:20128/v1` con el modelo activo como `gemini-3.7-flash-low`).

## Proposed Changes

---

### Componente 1: Movilidad Total del HUD Confinado a Pantalla

Permite desplazar el HUD a cualquier coordenada visible sin recortes artificiales, garantizando que nunca se pierda fuera de la pantalla ni bloquee los clics hacia Binomo.

#### [MODIFY] [OverlayService.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt)
- Ajustar `ACTION_MOVE` para permitir rango completo: `p.x.coerceIn(0, screenW - hudW)` y `p.y.coerceIn(0, screenH - hudH)`.
- Ajustar `toggleHUDVisibility()` para validar únicamente que no esté fuera de la pantalla física.
- Añadir método `temporarilyBypassHUD(durationMs: Long)` para volver el HUD no-táctil por 150ms durante taps de trading en caso de que esté encima del botón de Binomo.

#### [MODIFY] [AutoTradeAccessibilityService.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt)
- Antes de despachar un gesto en `performClickAt(x, y)`, comprobar si las coordenadas colisionan con el HUD visible y activar el bypass táctil temporal para que el clic atraviese limpiamente hacia Binomo.

---

### Componente 2: Agente Autónomo con Auto-Corrección y Control de App

Crea un cerebro autónomo que toma decisiones sobre la app y el broker, reconcilia datos del HUD con la realidad financiera y se adapta sin requerir autorización constante.

#### [NEW] [AutonomousAgentController.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AutonomousAgentController.kt)
- Loop de supervisión autónoma activa en segundo plano (cada 2 segundos).
- **Auto-corrección de saldo y HUD**: Reconcilia discrepancias de saldo real en Binomo vs el marcador W/L. Si Binomo acreditó saldo sin trade contado, registra automáticamente victoria; si se debitó saldo fuera de control, registra pérdida y ajusta Martingala.
- **Control de la app TradeDraw**:
  - Auto-rotación de estrategia si una racha de derrotas alcanza 2 seguidas (pasa de `AUTO_ADAPTIVE` a `TREND_FOLLOWING` o refuerza filtros).
  - Recalibración autónoma de soportes y resistencias (`unlockAllLines()`) si el precio rompió el rango.
  - Reanudación automática post-cooldown sin requerir pulsaciones manuales del usuario.

#### [MODIFY] [TradingEngine.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt)
- Integrar `AutonomousAgentController` en el ciclo de vida del motor.
- Exponer hooks para que el agente autónomo pueda alterar modos, estrategias y ejecutar diagnósticos.

---

### Componente 3: HUD Didáctico y Explicativo

Hace que el bot enseñe y comunique lo que está viendo y planeando hacer.

#### [MODIFY] [layout_trading_hud.xml](file:///C:/Users/heidy/Tradedraw/app/src/main/res/layout/layout_trading_hud.xml)
- Incorporar sección didáctica:
  - `hud_trend_badge`: Icono + Dirección + Porcentaje de fuerza (ej. `🟢 Alcista Fuerte 82%`).
  - `hud_trend_reason`: Explicación didáctica simple (ej. `Precio sobre EMA 20 con 3 velas verdes`).
  - `hud_planned_action`: Jugada planeada (ej. `🎯 Esperando retroceso a soporte 641.86 para entrar CALL al :58s`).
  - `hud_btn_open_chat`: Botón con icono de mensaje (`💬`) para abrir el chat con el bot.

#### [MODIFY] [OverlayService.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt)
- Enriquecer `updateHUDView()` para actualizar dinámicamente las etiquetas didácticas con el razonamiento del `VisionAnalyzer` y la IA.

---

### Componente 4: Chat Interactivo Flotante con el Agente

Permite al usuario chatear en vivo con el bot, hacerle preguntas y darle órdenes en lenguaje natural.

#### [NEW] [layout_agent_chat.xml](file:///C:/Users/heidy/Tradedraw/app/src/main/res/layout/layout_agent_chat.xml)
- Ventana flotante estilo consola moderna semi-transparente.
- Cabecera con estado del agente (`🤖 TradeDraw Agent | En Vivo`), botón minimizar y cerrar.
- `RecyclerView` con burbujas de conversación (usuario vs agente).
- Chips de acción rápida: `¿Por qué no operas?`, `¿Qué ves en el gráfico?`, `Modo Conservador`, `Recalcular niveles`.
- `EditText` y botón de envío.

#### [NEW] [item_agent_chat_message.xml](file:///C:/Users/heidy/Tradedraw/app/src/main/res/layout/item_agent_chat_message.xml)
- Diseño de burbuja de mensaje con marcas de tiempo y estilo neón/dark.

#### [MODIFY] [AIClient.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AIClient.kt)
- Implementar `sendChatMessage(userText: String, contextData: MarketContext, onResult: (String, AgentCommand?) -> Unit)`.
- Inyectar al modelo el contexto en vivo: saldo actual, precio, tendencia, S/R, W/L, Martingala y motivo de la última decisión.
- Soporte para emitir comandos que la app ejecute de inmediato (cambiar modo, cambiar estrategia, pausar, resetear).

#### [NEW] [AgentChatOverlay.kt](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AgentChatOverlay.kt)
- Controlador de la ventana flotante de chat mediante `WindowManager`.
- Gestión del historial de mensajes y ejecución de comandos devueltos por la IA.

---

## Verification Plan

### Pruebas Automatizadas y Compilación
- Compilación del proyecto completo:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
  ```

### Pruebas en Vivo en el Dispositivo (POCO X6 Pro)
1. **Movilidad 360° del HUD:** Arrastrar el HUD a la esquina superior izquierda, centro, esquina inferior derecha y sobre los botones de Binomo; comprobar que no se salga de pantalla y que los clics de trading no se bloqueen.
2. **Auto-Corrección del Agente:** Verificar en logcat que `AutonomousAgentController` reconcilie saldo y contadores automáticamente.
3. **HUD Didáctico:** Verificar que el HUD muestre claramente la tendencia detectada, su porcentaje de fuerza y la condición faltante para operar.
4. **Chat en Vivo:** Abrir el chat tocando el botón `💬`, enviar preguntas ("¿Qué ves ahora?", "¿Por qué no operas?") y órdenes ("Cambia a semiautomático"), verificando la respuesta en lenguaje natural y la ejecución del comando.
