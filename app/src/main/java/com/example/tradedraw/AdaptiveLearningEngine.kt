package com.example.tradedraw

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
 * Cuenta con persistencia completa en almacenamiento local de Android.
 */
class AdaptiveLearningEngine {

    private val TAG = "AdaptiveLearningEngine"
    private val STATE_FILE_NAME = "adaptive_learning_state.json"

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
    fun recordTradeOutcome(isWin: Boolean, context: Context? = null) {
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

        if (context != null) {
            saveState(context)
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
        strategyName: String,
    ): AdaptiveDecision {
        // En modo YOLO la operativa es continua (no se apaga por límites de sesión),
        // pero DEBE aplicar el 100% de los filtros de prudencia, vetos de racha y anti-patrones.

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
     * Guarda el estado completo de firmas y pesos aprendidos en archivo JSON local de la aplicación.
     */
    fun saveState(context: Context) {
        try {
            val root = JSONObject()
            root.put("consecutiveContinuationLosses", consecutiveContinuationLosses)
            root.put("consecutiveReversionLosses", consecutiveReversionLosses)
            root.put("totalBlockedAntiPatterns", totalBlockedAntiPatterns)
            root.put("totalInvertedAntiPatterns", totalInvertedAntiPatterns)
            root.put("reversionBonusWeight", reversionBonusWeight.toDouble())
            root.put("continuationPenaltyWeight", continuationPenaltyWeight.toDouble())

            val array = JSONArray()
            synchronized(lossSignatures) {
                for (sig in lossSignatures) {
                    val item = JSONObject()
                    item.put("action", sig.action.name)
                    item.put("trend", sig.trend.name)
                    item.put("distSup", sig.distanceToSupportRatio.toDouble())
                    item.put("distRes", sig.distanceToResistanceRatio.toDouble())
                    item.put("nearSup", sig.isNearSupportZone)
                    item.put("nearRes", sig.isNearResistanceZone)
                    item.put("tickVelNorm", sig.tickVelocityNormalized.toDouble())
                    item.put("bullishImp", sig.isBullishImpulse)
                    item.put("bearishImp", sig.isBearishImpulse)
                    item.put("doji", sig.isDojiOrLowVolume)
                    item.put("tight", sig.isConsolidationTight)
                    item.put("sideways", sig.isMarketSideways)
                    item.put("strat", sig.strategyName)
                    item.put("sec", sig.candleSecond)
                    item.put("ts", sig.timestampMs)
                    item.put("continuation", sig.isContinuationTrade)
                    item.put("reversion", sig.isReversionTrade)
                    array.put(item)
                }
            }
            root.put("lossSignatures", array)

            val file = File(context.filesDir, STATE_FILE_NAME)
            file.writeText(root.toString())
            Log.d(TAG, "Estado de autoaprendizaje persistido exitosamente (${array.length()} firmas guardadas)")
        } catch (e: Exception) {
            Log.e(TAG, "Error persistiendo estado de autoaprendizaje", e)
        }
    }

    /**
     * Carga el estado de firmas y pesos aprendidos desde archivo JSON local.
     */
    fun loadState(context: Context) {
        try {
            val file = File(context.filesDir, STATE_FILE_NAME)
            if (!file.exists()) return

            val text = file.readText()
            if (text.isBlank()) return

            val root = JSONObject(text)
            consecutiveContinuationLosses = root.optInt("consecutiveContinuationLosses", 0)
            consecutiveReversionLosses = root.optInt("consecutiveReversionLosses", 0)
            totalBlockedAntiPatterns = root.optInt("totalBlockedAntiPatterns", 0)
            totalInvertedAntiPatterns = root.optInt("totalInvertedAntiPatterns", 0)
            reversionBonusWeight = root.optDouble("reversionBonusWeight", 1.0).toFloat()
            continuationPenaltyWeight = root.optDouble("continuationPenaltyWeight", 1.0).toFloat()

            val array = root.optJSONArray("lossSignatures")
            val now = System.currentTimeMillis()
            val maxTtlMs = 24L * 60L * 60L * 1000L // 24 horas
            if (array != null) {
                synchronized(lossSignatures) {
                    lossSignatures.clear()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val action = try { TradeAction.valueOf(item.getString("action")) } catch (e: Exception) { TradeAction.BUY }
                        val trend = try { TrendDirection.valueOf(item.getString("trend")) } catch (e: Exception) { TrendDirection.SIDEWAYS }
                        val distSup = item.optDouble("distSup", 0.5).toFloat()
                        val distRes = item.optDouble("distRes", 0.5).toFloat()
                        val ts = item.optLong("ts", now)

                        // Purgar firmas genéricas atrapadas en el 50% y firmas expiradas por TTL de 24h
                        if (Math.abs(distSup - 0.50f) < 0.02f && Math.abs(distRes - 0.50f) < 0.02f) {
                            continue
                        }
                        if (now - ts > maxTtlMs) {
                            continue
                        }

                        val sig = TradeContextSignature(
                            action = action,
                            trend = trend,
                            distanceToSupportRatio = distSup,
                            distanceToResistanceRatio = distRes,
                            isNearSupportZone = item.optBoolean("nearSup", false),
                            isNearResistanceZone = item.optBoolean("nearRes", false),
                            tickVelocityNormalized = item.optDouble("tickVelNorm", 0.0).toFloat(),
                            isBullishImpulse = item.optBoolean("bullishImp", false),
                            isBearishImpulse = item.optBoolean("bearishImp", false),
                            isDojiOrLowVolume = item.optBoolean("doji", false),
                            isConsolidationTight = item.optBoolean("tight", false),
                            isMarketSideways = item.optBoolean("sideways", false),
                            strategyName = item.optString("strat", "AUTO_ADAPTIVE"),
                            candleSecond = item.optInt("sec", 0),
                            timestampMs = ts,
                            isContinuationTrade = item.optBoolean("continuation", false),
                            isReversionTrade = item.optBoolean("reversion", false)
                        )
                        lossSignatures.add(sig)
                    }
                }
            }
            Log.d(TAG, "Estado de autoaprendizaje restaurado: ${lossSignatures.size} firmas válidas cargadas (purgadas firmas 50% y expiradas)")
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando estado de autoaprendizaje", e)
        }
    }

    /**
     * Resetea el historial de aprendizaje si el usuario reinicia la sesión.
     */
    fun reset(context: Context? = null) {
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
        if (context != null) {
            saveState(context)
        }
    }
}
