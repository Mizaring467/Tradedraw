package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import java.util.Locale

/**
 * Candidato a señal de trading para multiplexado multi-activo (FASE 5 R31).
 */
data class CandidateSignal(
    val asset: String,
    val action: TradeAction,
    val confidence: Float,
    val confluenceScore: Int,
    val payoutRate: Double,
    val marketRegime: String = "TRENDING",
    val distanceToSrRatio: Float = 0.5f,
    val tickAgeMs: Long = 0L,
    val reasonDescription: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Resultado de la selección competitiva de candidatos.
 */
data class RankingResult(
    val winner: CandidateSignal?,
    val winnerScore: Double,
    val superseded: List<SupersededCandidate>
)

data class SupersededCandidate(
    val candidate: CandidateSignal,
    val score: Double,
    val reason: String
)

/**
 * Motor determinista de ranking y selección de mejor candidato multi-activo.
 * Implementa ponderación objetiva de 5 factores con desempate jerárquico determinista.
 */
class CandidateRankingEngine(
    val weightConfidence: Double = 0.35,
    val weightConfluence: Double = 0.25,
    val weightPayout: Double = 0.15,
    val weightRegime: Double = 0.15,
    val weightFreshness: Double = 0.10
) {

    /**
     * Calcula la puntuación cuantitativa compuesta de un candidato en [0.0, 1.0].
     */
    fun computeScore(candidate: CandidateSignal): Double {
        // 1. Confianza normalizada [0.0, 1.0]
        val c = candidate.confidence.coerceIn(0.0f, 1.0f).toDouble()

        // 2. Confluencia normalizada (80 pts = 0.8, 100 pts = 1.0)
        val k = (candidate.confluenceScore.coerceIn(0, 100).toDouble()) / 100.0

        // 3. Payout normalizado: base 70%, max 90%
        val p = ((candidate.payoutRate - 0.70) / 0.20).coerceIn(0.0, 1.0)

        // 4. Calidad del régimen de mercado
        val r = when (candidate.marketRegime.uppercase()) {
            "TRENDING", "UPTREND", "DOWNTREND" -> 1.0
            "NORMAL" -> 0.7
            "VOLATILE" -> 0.5
            "SIDEWAYS" -> 0.3
            "CHOP" -> 0.0
            else -> 0.5
        }

        // 5. Frescura del tick (penalización a partir de 250ms, 0 a 1000ms)
        val f = (1.0 - (candidate.tickAgeMs.toDouble() / 1000.0)).coerceIn(0.0, 1.0)

        return (weightConfidence * c) +
                (weightConfluence * k) +
                (weightPayout * p) +
                (weightRegime * r) +
                (weightFreshness * f)
    }

    /**
     * Evalúa una lista de candidatos concurrentes y selecciona el BEST_VALID_CANDIDATE.
     * En caso de empate cuantitativo (diferencia < 1e-4), desempata en orden determinista:
     * 1. Mayor payout del broker
     * 2. Menor latencia de tick (tickAgeMs)
     * 3. Orden lexicográfico del símbolo del activo
     */
    fun selectBestCandidate(
        candidates: List<CandidateSignal>,
        hasPendingTrade: Boolean = false
    ): RankingResult {
        if (candidates.isEmpty()) {
            return RankingResult(winner = null, winnerScore = 0.0, superseded = emptyList())
        }

        // Si hay una operación abierta, la regla MAX_PENDING_TRADES = 1 veta a todos los candidatos
        if (hasPendingTrade) {
            val superseded = candidates.map {
                SupersededCandidate(
                    candidate = it,
                    score = computeScore(it),
                    reason = "PENDING_TRADE:active_trade_in_flight"
                )
            }
            return RankingResult(winner = null, winnerScore = 0.0, superseded = superseded)
        }

        // Calificar todos los candidatos
        val scored = candidates.map { cand ->
            val score = computeScore(cand)
            cand to score
        }

        // Ordenar con comparador determinista
        val comparator = Comparator<Pair<CandidateSignal, Double>> { a, b ->
            val scoreDiff = b.second.compareTo(a.second)
            if (Math.abs(b.second - a.second) > 1e-4) {
                scoreDiff
            } else {
                // Desempate 1: mayor payout
                val payoutDiff = b.first.payoutRate.compareTo(a.first.payoutRate)
                if (payoutDiff != 0) {
                    payoutDiff
                } else {
                    // Desempate 2: menor latencia de tick
                    val latencyDiff = a.first.tickAgeMs.compareTo(b.first.tickAgeMs)
                    if (latencyDiff != 0) {
                        latencyDiff
                    } else {
                        // Desempate 3: símbolo determinista
                        a.first.asset.compareTo(b.first.asset)
                    }
                }
            }
        }

        val sorted = scored.sortedWith(comparator)
        val winnerPair = sorted.first()
        val winner = winnerPair.first
        val winnerScore = winnerPair.second

        val superseded = sorted.drop(1).map { (cand, score) ->
            SupersededCandidate(
                candidate = cand,
                score = score,
                reason = "RANKING_SUPERSEDED:selected_${winner.asset}"
            )
        }

        return RankingResult(
            winner = winner,
            winnerScore = winnerScore,
            superseded = superseded
        )
    }
}
