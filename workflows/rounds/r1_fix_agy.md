TAREA — RONDA 1 · IMPLEMENTACIÓN DE CORRECCIONES (agy / antigravity cli)

Repo: C:\Users\heidy\Tradedraw  (rama main). Kotlin, comentarios en español.
Contexto: la sesión de 25 min dio 0 operaciones. Auditoría (agy) y tester (hermes) confirmaron 2 causas raíz.
Aplica SOLO estas 3 correcciones quirúrgicas. NO toques nada más.

=== CORRECCIÓN 1 — SyntheticCandleEngine.kt (~L907-911): veto anti-chop selectivo ===
Actual:
```kotlin
        // En modo Conservador o Francotirador: si hay choppiness real (cluster de dojis/ruido), suprimir
        if (!isYolo && isChoppy) {
            Log.d(TAG, "Oportunidad Sniper Headless suprimida por choppiness/dojis")
            return
        }
```
Cambiar a que el veto NO mate las reversiones/rebotes autorizados por master_traders_skill.
Permitir pasar a los setups de reversión/rebote S/R incluso con chop; vetar solo continuación/tendencia:
```kotlin
        // Veto anti-chop selectivo: la skill veta continuación/tendencia con CHOP alto, pero AUTORIZA
        // reversiones/rebotes en S/R (MT_REJECTION, MT_RANGE, MT_3_VELAS_AGOT, MT_FALSE_BREAKOUT).
        if (!isYolo && isChoppy) {
            val reversalContext = distanceToSupportRatio < 0.22f || distanceToResistanceRatio < 0.22f ||
                    tickZScore60 <= -2.0 || tickZScore60 >= 2.0
            if (!reversalContext) {
                Log.d(TAG, "Oportunidad Sniper Headless suprimida por choppiness/dojis (sin contexto de reversión S/R)")
                return
            }
            Log.d(TAG, "Chop presente pero hay contexto de reversión S/R: se permite evaluar (governance)"
        }
```
(Usa las funciones/propiedades ya existentes: distanceToSupportRatio, distanceToResistanceRatio, tickZScore60.)

=== CORRECCIÓN 2 — SyntheticCandleEngine.kt (~L161-174): preservar TFI en la apertura ===
Actual reset (L161-166) pone `tickFlowImbalance = 0.0f` al cambiar de minuto. Como la ventana de
entrada es :01-:03 y el TFI sólo acumula en :40-:59, en la apertura SIEMPRE vale 0.0f y QUANT_CRYPTO nunca dispara.
Cambio: NO poner a cero el TFI publicado al cambiar de minuto; conservar la última lectura de la vela anterior
(la de :40-:59) para poder decidir en :01-:03:
```kotlin
        if (candleMinute != lastRecordedCandleMinute) {
            windowUpTicksCount = 0
            windowDownTicksCount = 0
            lastRecordedCandleMinute = candleMinute
            // NO resetear tickFlowImbalance: se preserva la lectura de cierre (:40-:59) de la vela previa
            // para evaluar la continuación en la apertura (:01-:03) sin quedar ciego en 0.0f.
        }
```

=== CORRECCIÓN 3 — Ampliar la ventana de entrada a :01-:05 ===
La ventana `1..3` (3s) es demasiado estrecha con ticks a 400-1000 ms y rompe la confluencia.
La skill autoriza el "Sniper Pullback :01-:05". Cambiar a `1..5` en:
- MarketTick.kt: `isStrictTimingWindow` -> `candleSecond in 1..5`; `isTimingVetoed` -> `candleSecond !in 1..5`.
- MarketTickFilters: `isStrictTimingWindow(second) -> second in 1..5`; `isTimingVetoed(second) -> second !in 1..5`.
- CandleTimeframe.kt: `isSniperTimingWindow` y `isStandardTimingWindow` -> `s in 1..5`.
- TradingEngine.kt: la ventana headless `val inWindow = ...` (~L1651) -> `sec in 1..5`; y en la ruta local (`isStrictTimingWindow = sec in ...` y `isTimingVetoed = sec !in ...`) usar `1..5`.
- VisionAnalyzer.kt (~L712-713): `isStrictTimingWindow = candleSecond in 1..5`; `isTimingVetoed = candleSecond !in 1..5`.
- Mantener VisionAnalyzer.kt `candleSecond: Int = 2` (dentro de 1..5).

=== VERIFICACIÓN OBLIGATORIA ===
1. Ajusta los tests que asuman `1..3` a `1..5` (TradingEngineTest, SyntheticCandleEngineTest, VisualHarnessTest).
   Si un test usaba segundo 4/5 como "fuera de ventana", cámbialo a 6/7.
2. Corre:
   .\gradlew.bat testDebugUnitTest --no-daemon
   .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
3. NO hagas commit ni push. NO instales (lo hace el tester hermes).

=== ENTREGABLE (español, máx 25 líneas) ===
- Archivos modificados (ruta + 1 línea)
- Salida BUILD SUCCESSFUL/FAILED + conteo de tests
- Tests ajustados y por qué
- Bloqueos exactos si algo no compila
