TAREA — RONDA 4 · IMPLEMENTACIÓN R2/R3 (agy / antigravity cli)

Repo: C:\Users\heidy\Tradedraw (rama main). Kotlin, comentarios en español. NO hagas commit.
Aplica SOLO estas 2 correcciones. NO toques nada más.

=== CONTEXTO (auditoría r4_agy.md ya hecha) ===
Journal real N=75 (sin TIE): WR global 49.3%. Subconjuntos con ventaja:
  - Entrada :01-:05s  -> WR 100% (N=28)  [ya garantizado por H1 ventana, NO tocar]
  - SELL + distResist 0.16-0.35 -> WR 77.8% (N=9)
  - Canal medio (distS y distR en 0.16-0.35) -> WR 60.6% (N=33)
  - UPTREND+BUY -> WR 55.6% (N=18)
Perdedores: operar contra-tendencia y/o pegado a un extremo (dist <= 0.15).

=== CORRECCIÓN R2 — Filtro Anti-Contra-Tendencia estricto ===
Archivo: app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt
En `dispatchSniperSignal` (aprox L880), ANTES de calcular la confluencia, añade un veto:
si la acción es CONTRARIA a la tendencia detectada fuerte, descartar.
```kotlin
        // R2 (Ronda 4): anti-contra-tendencia estricto. Operar contra un flujo direccional
        // confirmado degrada el WR (UPTREND+SELL y DOWNTREND+BUY son los peores buckets).
        // Se permite la contra-tendencia SOLO si el setup es de reversión explícita en un
        // extremo (razón con REJECTION/BOUNCE/AGOT/REVERSAL/FALSE_BREAKOUT/ENGULFING/RANGE/Clímax).
        val isReversalSetup = reason.contains("REJECTION") || reason.contains("BOUNCE") ||
                reason.contains("AGOT") || reason.contains("REVERSAL") ||
                reason.contains("FALSE_BREAKOUT") || reason.contains("ENGULFING") ||
                reason.contains("RANGE") || reason.contains("Clímax")
        val isCounterTrend =
            (action == TradeAction.BUY && detectedTrend == TrendDirection.DOWNTREND) ||
            (action == TradeAction.SELL && detectedTrend == TrendDirection.UPTREND)
        if (isCounterTrend && !isReversalSetup) {
            suppressedByConfluence++
            android.util.Log.d(TAG, "⛔ R2 veto contra-tendencia: $action contra $detectedTrend sin setup de reversión ($reason)")
            return false
        }
```
(Usa el TAG ya existente; si no existe `suppressedByConfluence`, usa los contadores ya definidos.)

=== CORRECCIÓN R3 — Confirmación de canal medio (evitar extremos pegados) ===
Archivo: app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt
En `dispatchSniperSignal`, tras el cálculo `val score = calculateConfluenceScore(...)`:
para los setups de CONTINUACIÓN/MOMENTUM (NO reversión), exigir que el precio NO esté pegado
a un extremo (dist <= 0.15) en la dirección del movimiento. Si lo está, degradar/vetar:
```kotlin
        // R3 (Ronda 4): los toques DIRECTOS a extremos (dist<=0.15) en continuación sufren
        // falsos rompimientos (WR bajo). Se exige zona media (0.16-0.35) para momentum/continuación.
        if (!isReversalSetup) {
            val stuckAtEdge = if (action == TradeAction.BUY) distanceToResistanceRatio <= 0.15f
                              else distanceToSupportRatio <= 0.15f
            if (stuckAtEdge) {
                suppressedByConfluence++
                android.util.Log.d(TAG, "⛔ R3 veto extremo pegado: $action con obstáculo a ${(if (action==TradeAction.BUY) distanceToResistanceRatio else distanceToSupportRatio)*100}% ($reason)")
                return false
            }
        }
```

=== VERIFICACIÓN OBLIGATORIA ===
1. Balance de llaves {} correcto en SyntheticCandleEngine.kt.
2. .\gradlew.bat testDebugUnitTest --no-daemon
3. .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
   (si algún test falla porque esperaba una señal contra-tendencia, actualízalo y explica por qué)
4. NO hagas commit ni install.

=== ENTREGABLE (español, máx 25 líneas) ===
- Archivos modificados (ruta + 1 línea)
- Salida BUILD SUCCESSFUL/FAILED + conteo de tests
- Tests ajustados y por qué
- Bloqueos exactos si algo no compila
