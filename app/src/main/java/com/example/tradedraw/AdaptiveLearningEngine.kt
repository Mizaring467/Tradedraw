package com.example.tradedraw

import android.util.Log

/**
 * Representa la firma contextual de mercado capturada en el milisegundo exacto en que se ejecuta una operación.
 */
data class TradeContextSignature(
    val action: TradeAction,
    val trend: TrendDirection,
    val distanceToSupportRatio: Float,
    val distanceToResistanceRatio: Float,
    val isNearSupportZone: Boolean,
    val isNearResistanceZone: Boolean,
    val tickVelocityNormalized: Float,
    val isBullishImpulse: Boolean,
    val isBearishImpulse: Boolean,
    val isDojiOrLowVolume: Boolean,
    val isConsolidationTight: Boolean,
    val isMarketSideways: Boolean,
    val strategyName: String,
    val candleSecond: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val isContinuationTrade: Boolean = false,
    val isReversionTrade: Boolean = false
)

/**
 * Decisión adaptativa emitida por el motor de autoaprendizaje antes de autorizar o vetar una operación.
 */
sealed class AdaptiveDecision {
    data class Allow(val action: TradeAction, val confidenceModifier: Float = 1.0f, val reason: String = "") : AdaptiveDecision()
    data class Block(val reason: String) : AdaptiveDecision()
    data class Invert(val invertedAction: TradeAction, val reason: String) : AdaptiveDecision()
}

/**
 * Motor de Autoaprendizaje Adaptativo de Errores (AdaptiveLearningEngine).
 * 
 * Memoriza las firmas contextuales de operaciones perdedoras, detecta recurrencias de anti-patrones en el mercado
 * y adapta dinámicamente los pesos entre Continuación y Reversión en Soportes/Resistencias para auto-corregir la operativa.
 */
class AdaptiveLearningEngine {

    private val TAG = "AdaptiveLearningEngine"

    // Memoria de firmas perdedoras (anti-patrones)
    private val lossSignatures = ArrayList<TradeContextSignature>(64)
    
    // Firma pendiente de la operación actualmente abierta
    private var pendingTradeSignature: TradeContextSignature? = null

    // Estadísticas de adaptación
    var consecutiveContinuationLosses: Int = 0
        private set
    var consecutiveReversionLosses: Int = 0
        private set
    var totalBlockedAntiPatterns: Int = 0
        private set
    var totalInvertedAntiPatterns: Int = 0
        private set

    // Pesos adaptativos dinámicos (1.0 = neutral)
    var reversionBonusWeight: Float = 1.0f
        private set
    var continuationPenaltyWeight: Float = 1.0f
        private set

    /**
     * Construye y registra la firma contextual de una orden al ser despachada.
     */
    fun recordTradeOpened(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        tick: MarketTick?,
        strategyName: String
    ) {
        val inUptrend = analysis.trend == TrendDirection.UPTREND
        val inDowntrend = analysis.trend == TrendDirection.DOWNTREND

        val isContinuation = (action == TradeAction.BUY && inUptrend) || (action == TradeAction.SELL && inDowntrend)
        val isReversion = (action == TradeAction.BUY && inDowntrend) || (action == TradeAction.SELL && inUptrend) ||
                          analysis.isRejectionCall || analysis.isRejectionPut || analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut

        val sig = TradeContextSignature(
            action = action,
            trend = analysis.trend,
            distanceToSupportRatio = analysis.distanceToSupportRatio,
            distanceToResistanceRatio = analysis.distanceToResistanceRatio,
            isNearSupportZone = analysis.isNearSupportZone,
            isNearResistanceZone = analysis.isNearResistanceZone,
            tickVelocityNormalized = analysis.tickVelocityNormalized,
            isBullishImpulse = analysis.isBullishImpulse || (tick?.isBullishImpulse == true),
            isBearishImpulse = analysis.isBearishImpulse || (tick?.isBearishImpulse == true),
            isDojiOrLowVolume = analysis.isDojiOrLowVolume,
            isConsolidationTight = analysis.isConsolidationTight,
            isMarketSideways = analysis.isMarketSideways,
            strategyName = strategyName,
            candleSecond = analysis.candleSecond,
            timestampMs = System.currentTimeMillis(),
            isContinuationTrade = isContinuation,
            isReversionTrade = isReversion
        )
        pendingTradeSignature = sig
    }

    /**
     * Procesa la liquidación del trade (Win o Loss) y actualiza los patrones aprendidos.
     */
    fun recordTradeOutcome(isWin: Boolean) {
        val sig = pendingTradeSignature ?: return
        pendingTradeSignature = null

        if (isWin) {
            // Recompensa / Reducción gradual de penalizaciones
            if (sig.isContinuationTrade && consecutiveContinuationLosses > 0) {
                consecutiveContinuationLosses = (consecutiveContinuationLosses - 1).coerceAtLeast(0)
            }
            if (sig.isReversionTrade && consecutiveReversionLosses > 0) {
                consecutiveReversionLosses = (consecutiveReversionLosses - 1).coerceAtLeast(0)
            }
            recalculateAdaptiveWeights()
            Log.d(TAG, "Trade Ganado: Pesos adaptativos normalizados (ContLosses=$consecutiveContinuationLosses, RevLosses=$consecutiveReversionLosses)")
        } else {
            // Registro del anti-patrón en memoria de errores
            synchronized(lossSignatures) {
                if (lossSignatures.size >= 50) {
                    lossSignatures.removeAt(0)
                }
                lossSignatures.add(sig)
            }

            if (sig.isContinuationTrade) {
                consecutiveContinuationLosses++
                consecutiveReversionLosses = 0
            } else if (sig.isReversionTrade) {
                consecutiveReversionLosses++
                consecutiveContinuationLosses = 0
            }

            recalculateAdaptiveWeights()
            Log.w(TAG, "⚠️ Trade Perdido registrado en Memoria de Anti-Patrones: " +
                    "Acción=${sig.action}, Tendencia=${sig.trend}, DistSup=${sig.distanceToSupportRatio}, " +
                    "DistRes=${sig.distanceToResistanceRatio}, Vel=${sig.tickVelocityNormalized}. " +
                    "ContLosses=$consecutiveContinuationLosses, RevLosses=$consecutiveReversionLosses")
        }
    }

    private fun recalculateAdaptiveWeights() {
        // Si hay pérdidas consecutivas en continuación (ej: comprando techos o vendiendo pisos en tendencia),
        // penalizar continuación y potenciar reversión en S/R
        if (consecutiveContinuationLosses >= 2) {
            continuationPenaltyWeight = (1.0f - (consecutiveContinuationLosses * 0.25f)).coerceIn(0.20f, 1.0f)
            reversionBonusWeight = (1.0f + (consecutiveContinuationLosses * 0.40f)).coerceIn(1.0f, 2.2f)
        } else if (consecutiveReversionLosses >= 2) {
            // Si hay pérdidas consecutivas en reversión (operando contra una tendencia violenta),
            // penalizar reversiones prematuras y favorecer continuación con momentum
            reversionBonusWeight = (1.0f - (consecutiveReversionLosses * 0.25f)).coerceIn(0.20f, 1.0f)
            continuationPenaltyWeight = (1.0f + (consecutiveReversionLosses * 0.30f)).coerceIn(1.0f, 2.0f)
        } else {
            reversionBonusWeight = 1.0f
            continuationPenaltyWeight = 1.0f
        }
    }

    /**
     * Evalúa si una señal candidata coincide con un anti-patrón perdedor previo.
     * Si coincide estrechamente, bloquea la orden o invierte la acción para proteger el capital.
     */
    fun evaluateSignalSuitability(
        candidateAction: TradeAction,
        analysis: VisionAnalysisResult,
        tick: MarketTick?,
        strategyName: String
    ): AdaptiveDecision {
        val inUptrend = analysis.trend == TrendDirection.UPTREND
        val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
        val isContinuation = (candidateAction == TradeAction.BUY && inUptrend) || (candidateAction == TradeAction.SELL && inDowntrend)
        val isReversion = (candidateAction == TradeAction.BUY && inDowntrend) || (candidateAction == TradeAction.SELL && inUptrend) ||
                          analysis.isRejectionCall || analysis.isRejectionPut || analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut

        // 1. Filtro por sesgo adaptativo de pérdidas consecutivas
        if (isContinuation && consecutiveContinuationLosses >= 2) {
            // Bloquear continuación si estamos peligrosamente cerca de la barrera opuesta
            if ((candidateAction == TradeAction.BUY && analysis.distanceToResistanceRatio < 0.22f) ||
                (candidateAction == TradeAction.SELL && analysis.distanceToSupportRatio < 0.22f)) {
                totalBlockedAntiPatterns++
                return AdaptiveDecision.Block(
                    "⛔ Autoaprendizaje: Bloqueada continuación ($consecutiveContinuationLosses pérdidas previas) contra nivel S/R extremo"
                )
            }
        }

        if (isReversion && consecutiveReversionLosses >= 2) {
            // Bloquear reversión si el mercado tiene fuerte momentum direccional
            if ((candidateAction == TradeAction.BUY && (analysis.hasStrongMomentumDown || analysis.isBearishImpulse)) ||
                (candidateAction == TradeAction.SELL && (analysis.hasStrongMomentumUp || analysis.isBullishImpulse))) {
                totalBlockedAntiPatterns++
                return AdaptiveDecision.Block(
                    "⛔ Autoaprendizaje: Bloqueada reversión contra momentum violento ($consecutiveReversionLosses pérdidas previas)"
                )
            }
        }

        // 2. Comparación de similitud con memoria de Anti-Patrones
        val candidateSignature = TradeContextSignature(
            action = candidateAction,
            trend = analysis.trend,
            distanceToSupportRatio = analysis.distanceToSupportRatio,
            distanceToResistanceRatio = analysis.distanceToResistanceRatio,
            isNearSupportZone = analysis.isNearSupportZone,
            isNearResistanceZone = analysis.isNearResistanceZone,
            tickVelocityNormalized = analysis.tickVelocityNormalized,
            isBullishImpulse = analysis.isBullishImpulse || (tick?.isBullishImpulse == true),
            isBearishImpulse = analysis.isBearishImpulse || (tick?.isBearishImpulse == true),
            isDojiOrLowVolume = analysis.isDojiOrLowVolume,
            isConsolidationTight = analysis.isConsolidationTight,
            isMarketSideways = analysis.isMarketSideways,
            strategyName = strategyName,
            candleSecond = analysis.candleSecond,
            timestampMs = System.currentTimeMillis(),
            isContinuationTrade = isContinuation,
            isReversionTrade = isReversion
        )

        var highestSimilarity = 0f
        var matchingLossSig: TradeContextSignature? = null

        synchronized(lossSignatures) {
            val recentLosses = lossSignatures.takeLast(20)
            for (loss in recentLosses) {
                val sim = calculateSignatureSimilarity(candidateSignature, loss)
                if (sim > highestSimilarity) {
                    highestSimilarity = sim
                    matchingLossSig = loss
                }
            }
        }

        if (highestSimilarity >= 0.82f && matchingLossSig != null) {
            val loss = matchingLossSig!!
            val simPct = (highestSimilarity * 100).toInt()

            // Si el anti-patrón fue una trampa en S/R o rechazo falso que se fue en contra, invertir la señal
            val canInvert = (analysis.isNearSupportZone || analysis.isNearResistanceZone) &&
                    (loss.action == candidateAction) &&
                    (analysis.distanceToSupportRatio < 0.15f || analysis.distanceToResistanceRatio < 0.15f)

            if (canInvert) {
                val inverted = if (candidateAction == TradeAction.BUY) TradeAction.SELL else TradeAction.BUY
                totalInvertedAntiPatterns++
                Log.w(TAG, "🔄 Autoaprendizaje: Anti-patrón idéntico ($simPct% sim). Invirtiendo señal $candidateAction -> $inverted")
                return AdaptiveDecision.Invert(
                    invertedAction = inverted,
                    reason = "Anti-patrón perdedor detectado ($simPct% similitud en ${loss.strategyName}) -> Señal invertida a $inverted"
                )
            } else {
                totalBlockedAntiPatterns++
                Log.w(TAG, "⛔ Autoaprendizaje: Anti-patrón perdedor bloqueado ($simPct% similitud con pérdida previa en ${loss.strategyName})")
                return AdaptiveDecision.Block(
                    "Anti-patrón perdedor recurrente ($simPct% similitud en contexto S/R=${(loss.distanceToSupportRatio * 100).toInt()}%)"
                )
            }
        }

        val modifier = if (isReversion) reversionBonusWeight else continuationPenaltyWeight
        return AdaptiveDecision.Allow(
            action = candidateAction,
            confidenceModifier = modifier,
            reason = "Aprobado por Autoaprendizaje (Modificador=$modifier)"
        )
    }

    /**
     * Calcula la distancia/similitud matemática entre dos firmas contextuales (0.0 = totalmente opuestas, 1.0 = idénticas).
     */
    private fun calculateSignatureSimilarity(a: TradeContextSignature, b: TradeContextSignature): Float {
        var score = 0f

        // 1. Misma acción (0.20 peso)
        if (a.action == b.action) score += 0.20f

        // 2. Misma tendencia (0.20 peso)
        if (a.trend == b.trend) score += 0.20f

        // 3. Proximidad a Soporte/Resistencia (0.30 peso)
        val distSupDiff = Math.abs(a.distanceToSupportRatio - b.distanceToSupportRatio)
        val distResDiff = Math.abs(a.distanceToResistanceRatio - b.distanceToResistanceRatio)
        val srSimilarity = ((1.0f - distSupDiff) + (1.0f - distResDiff)) / 2.0f
        score += (srSimilarity.coerceIn(0f, 1f) * 0.30f)

        // 4. Velocidad e impulso de tick (0.15 peso)
        if (a.isBullishImpulse == b.isBullishImpulse && a.isBearishImpulse == b.isBearishImpulse) {
            score += 0.15f
        } else {
            val velDiff = Math.abs(a.tickVelocityNormalized - b.tickVelocityNormalized)
            val velSim = (1.0f - (velDiff / 4.0f)).coerceIn(0f, 1f)
            score += (velSim * 0.15f)
        }

        // 5. Régimen de mercado lateral / micro-rango (0.15 peso)
        if (a.isMarketSideways == b.isMarketSideways && a.isConsolidationTight == b.isConsolidationTight) {
            score += 0.15f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Resetea el historial de aprendizaje si el usuario reinicia la sesión.
     */
    fun reset() {
        synchronized(lossSignatures) {
            lossSignatures.clear()
        }
        pendingTradeSignature = null
        consecutiveContinuationLosses = 0
        consecutiveReversionLosses = 0
        reversionBonusWeight = 1.0f
        continuationPenaltyWeight = 1.0f
        totalBlockedAntiPatterns = 0
        totalInvertedAntiPatterns = 0
    }
}
