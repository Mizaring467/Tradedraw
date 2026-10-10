TAREA RONDA 1 — TradeDraw (repo C:\Users\heidy\Tradedraw, rama main)

Eres el implementador core del loop. Trabaja SOLO en este repo. Lenguaje: Kotlin (comentarios y mensajes en español). Debes aplicar 3 correcciones cuantitativas (H1, H2, H3) y dejar el APK compilando.

=== H1: ventana de entrada estricta :01–:03 (eliminar :58 y :59) ===
Archivo: app/src/main/java/com/example/tradedraw/TradingEngine.kt
- En la funcion headless (aprox. linea 1651): `val inWindow = if (isSniper) sec in 58..59 else (sec in 58..59 || sec in 0..3)`
  CAMBIAR a que la ventana sea EXCLUSIVAMENTE `sec in 1..3` en todos los modos (incluido SNIPER y YOLO). El segundo :00 tampoco se permite (aun no estabiliza la vela).
- En app/src/main/java/com/example/tradedraw/MarketTick.kt:
  - `isStrictTimingWindow` (propiedad, ~linea 28): cambiar `candleSecond in 57..59 || candleSecond in 0..5` por `candleSecond in 1..3`.
  - `isTimingVetoed` (propiedad, ~linea 32): cambiar `candleSecond in 15..55` por `candleSecond !in 1..3` (veto total fuera de la ventana).
  - En MarketTickFilters: `isStrictTimingWindow(second)` -> `second in 1..3`; `isTimingVetoed(second)` -> `second !in 1..3`.
- Busca CUALQUIER otro literal `58..59`, `in 58`, `57..59`, `0..3` relacionado con disparo de orden en TradingEngine.kt y ajustalo a `1..3`. NO toques textos de HUD ni leyendas.
- Justificacion: las ordenes en :58-:59 cruzaban el segundo :00 y expiraban en la vela siguiente (WR 0%, 23 trades). La nueva ventana garantiza que la nueva vela ya abrio.

=== H2: veto anti-chop incondicional (quitar bypass YOLO) ===
Archivo: app/src/main/java/com/example/tradedraw/TradingEngine.kt
- Linea aprox. 1663: `if (syntheticCandleEngine.isChoppinessDetected() && !isRejectionOrBounce && !isYolo)` -> eliminar `&& !isYolo` para que el veto aplique TAMBIEN en YOLO.
- Linea aprox. 1206: `val isFavorable = !riskManager.hasPendingTrade && (autonomousSubMode == AutonomousSubMode.YOLO || (!syntheticCandleEngine.cachedChoppiness && analysis?.isMarketSideways != true))`
  CAMBIAR a: `val isFavorable = !riskManager.hasPendingTrade && !syntheticCandleEngine.cachedChoppiness && analysis?.isMarketSideways != true` (sin rama YOLO).
- Justificacion: el 100% de las 41 firmas de perdida ocurrieron en SIDEWAYS; YOLO anulaba el filtro (38 perdidas = 33%).

=== H3: eliminar inversion adaptativa (bloquear en vez de invertir) ===
Archivo: app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt
- En `evaluateSignalSuitability` (aprox. lineas 258–276), cuando `canInvert` es true actualmente devuelve `AdaptiveDecision.Invert(...)`. CAMBIAR para que en su lugar devuelva `AdaptiveDecision.Block(...)` con un motivo tipo: "Anti-patron perdedor en S/R: bloqueado (inversion deshabilitada por governance)". Elimina la rama `Invert` y el contador `totalInvertedAntiPatterns++` de este punto (puedes dejar el campo para compatibilidad pero ya no se incrementa).
- En TradingEngine.kt, la rama `is AdaptiveDecision.Invert -> { ... }` (aprox. 1704–1708) puede quedar como codigo inalcanzable; NO la elimines si rompe compilacion, pero la logica ya no producira Invert.
- Justificacion: 2.558 inversiones forzadas vendian en soporte y compraban en resistencia (34–39 perdidas).

=== VERIFICACION OBLIGATORIA ===
1. Balance de llaves `{}` correcto en cada .kt editado.
2. Ejecuta desde C:\Users\heidy\Tradedraw:
   .\gradlew.bat testDebugUnitTest --no-daemon
   Si hay tests que fallan por el cambio de ventana (esperaban :58-:03), actualiza el test para reflejar :01-:03 y explica por que.
3. Ejecuta:
   .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
4. NO hagas commit ni push. NO instales en el dispositivo (eso lo hace el tester).

=== ENTREGABLE ===
Responde en español, maximo 25 lineas:
- Archivos modificados (ruta + resumen de 1 linea por archivo)
- Salida textual de BUILD SUCCESSFUL/FAILED y conteo de tests
- Cualquier test ajustado y su justificacion
- Bloqueos o dudas (si algo no compila, dilo exacto con el error)
