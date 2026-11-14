package com.example.tradedraw

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Taxonomía cerrada de vetos para perfilado cuantitativo de throughput (FASE 1 R31).
 */
enum class VetoCategory {
    TIMING,
    COOLDOWN,
    CANDLE_ALREADY_EXECUTED,
    ADAPTIVE_BLOCK,
    CHOP,
    RISK_LIMIT,
    ASSET_MISMATCH,
    BROKER_ABSENT,
    WS_STALE,
    NO_DEMO,
    PENDING_TRADE,
    SR_CONFLICT,
    BUY_EXHAUSTION,
    OTHER
}

/**
 * Registro de una evaluación individual de señal/oportunidad.
 */
data class EvaluationRecord(
    val timestampMs: Long = System.currentTimeMillis(),
    val asset: String,
    val strategy: String,
    val submode: String,
    val action: String,
    val confidence: Float,
    val candleSecond: Int,
    val marketRegime: String,
    val signalScore: Int,
    val vetoIndex: Int,
    val vetoReason: String,
    val vetoCategory: VetoCategory?,
    val allowed: Boolean
) {
    fun toIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }
}

/**
 * Perfilador en tiempo real de vetos y embudo de conversión (Veto Profiler).
 */
class VetoProfiler(
    private val maxRecentRecords: Int = 1000
) {
    companion object {
        val INSTANCE = VetoProfiler()

        fun classifyVeto(vetoIndex: Int, vetoMessage: String): VetoCategory {
            val upper = vetoMessage.uppercase()
            return when {
                upper.contains("FUERA_DE_VENTANA") || upper.contains("TIMING") -> VetoCategory.TIMING
                upper.contains("COOLDOWN") || upper.contains("ANTI-TILT") || upper.contains("PAUSA") -> VetoCategory.COOLDOWN
                upper.contains("VELA_YA_OPERADA") || upper.contains("CANDLE") -> VetoCategory.CANDLE_ALREADY_EXECUTED
                upper.contains("AUTOAPRENDIZAJE") || upper.contains("ANTI-PATRON") || upper.contains("ADAPTIVE") -> VetoCategory.ADAPTIVE_BLOCK
                upper.contains("CHOP") || upper.contains("CONGELADO") || upper.contains("RANGO=") -> VetoCategory.CHOP
                upper.contains("RISK_LIMIT") || upper.contains("STOP LOSS") || upper.contains("TAKE PROFIT") || upper.contains("LÍMITE") -> VetoCategory.RISK_LIMIT
                upper.contains("DESAJUSTE") || upper.contains("ASSET_MISMATCH") || upper.contains("PANTALLA") -> VetoCategory.ASSET_MISMATCH
                upper.contains("FEED_MUDO") || upper.contains("BROKER_ABSENT") -> VetoCategory.BROKER_ABSENT
                upper.contains("FEED_RANCIO") || upper.contains("WS_STALE") -> VetoCategory.WS_STALE
                upper.contains("NO_DEMO") || upper.contains("SEGURIDAD_NO_DEMO") || upper.contains("CUENTA_REAL") -> VetoCategory.NO_DEMO
                upper.contains("PENDING_TRADE") || upper.contains("RECONCILIACIÓN") || upper.contains("ENVIADA") -> VetoCategory.PENDING_TRADE
                upper.contains("SR=") || upper.contains("CONFLICTO S/R") -> VetoCategory.SR_CONFLICT
                upper.contains("AGOTAMIENTO") || upper.contains("BUY_EXHAUSTION") || upper.contains("CLÍMAX") -> VetoCategory.BUY_EXHAUSTION
                else -> when (vetoIndex) {
                    1 -> VetoCategory.WS_STALE
                    2, 3 -> VetoCategory.CHOP
                    4 -> VetoCategory.ASSET_MISMATCH
                    5 -> VetoCategory.CANDLE_ALREADY_EXECUTED
                    7 -> VetoCategory.TIMING
                    8 -> VetoCategory.CHOP
                    9 -> VetoCategory.COOLDOWN
                    10 -> VetoCategory.BUY_EXHAUSTION
                    11 -> VetoCategory.ADAPTIVE_BLOCK
                    12 -> VetoCategory.RISK_LIMIT
                    13 -> VetoCategory.SR_CONFLICT
                    else -> VetoCategory.OTHER
                }
            }
        }
    }

    // Contadores del embudo
    private val totalTicks = AtomicLong(0L)
    private val totalEvaluations = AtomicLong(0L)
    private val totalCandidateSignals = AtomicLong(0L)
    private val passedTiming = AtomicLong(0L)
    private val passedStrategy = AtomicLong(0L)
    private val passedAdaptive = AtomicLong(0L)
    private val passedRisk = AtomicLong(0L)
    private val totalAuthorized = AtomicLong(0L)
    private val totalTapsDispatched = AtomicLong(0L)
    private val totalSettled = AtomicLong(0L)

    // Mapa de vetos por categoría
    private val vetoCounts = ConcurrentHashMap<VetoCategory, AtomicLong>()

    // Búfer circular de registros recientes
    private val recentRecords = java.util.concurrent.ConcurrentLinkedDeque<EvaluationRecord>()

    @Volatile
    var lastTradeTimestampMs: Long = System.currentTimeMillis()

    init {
        for (cat in VetoCategory.values()) {
            vetoCounts[cat] = AtomicLong(0L)
        }
    }

    fun recordTick() {
        totalTicks.incrementAndGet()
    }

    fun recordEvaluationTick() {
        totalEvaluations.incrementAndGet()
    }

    fun recordCandidateSignal() {
        totalCandidateSignals.incrementAndGet()
    }

    fun recordEvaluation(
        asset: String,
        strategy: String,
        submode: String,
        action: String,
        confidence: Float,
        candleSecond: Int,
        marketRegime: String,
        signalScore: Int,
        vetoIndex: Int,
        vetoReason: String,
        allowed: Boolean,
        category: VetoCategory? = null
    ): EvaluationRecord {
        val cands = totalCandidateSignals.incrementAndGet()
        // Invariante monótona del funnel: totalEvaluations >= totalCandidateSignals
        if (totalEvaluations.get() < cands) {
            totalEvaluations.set(cands)
        }

        val effectiveCategory = if (allowed) null else (category ?: classifyVeto(vetoIndex, vetoReason))

        if (allowed) {
            passedTiming.incrementAndGet()
            passedStrategy.incrementAndGet()
            passedAdaptive.incrementAndGet()
            passedRisk.incrementAndGet()
            totalAuthorized.incrementAndGet()
        } else if (effectiveCategory != null) {
            vetoCounts[effectiveCategory]?.incrementAndGet()

            val failedAtTiming = (effectiveCategory == VetoCategory.TIMING)
            val failedAtStrategy = (effectiveCategory == VetoCategory.CHOP ||
                    effectiveCategory == VetoCategory.BUY_EXHAUSTION ||
                    effectiveCategory == VetoCategory.SR_CONFLICT ||
                    effectiveCategory == VetoCategory.ASSET_MISMATCH)
            val failedAtAdaptive = (effectiveCategory == VetoCategory.ADAPTIVE_BLOCK)

            if (!failedAtTiming) {
                passedTiming.incrementAndGet()
                if (!failedAtStrategy) {
                    passedStrategy.incrementAndGet()
                    if (!failedAtAdaptive) {
                        passedAdaptive.incrementAndGet()
                    }
                }
            }
        }

        val record = EvaluationRecord(
            timestampMs = System.currentTimeMillis(),
            asset = asset,
            strategy = strategy,
            submode = submode,
            action = action,
            confidence = confidence,
            candleSecond = candleSecond,
            marketRegime = marketRegime,
            signalScore = signalScore,
            vetoIndex = vetoIndex,
            vetoReason = vetoReason,
            vetoCategory = effectiveCategory,
            allowed = allowed
        )

        recentRecords.addLast(record)
        while (recentRecords.size > maxRecentRecords) {
            recentRecords.pollFirst()
        }

        return record
    }

    fun recordTapDispatched() {
        totalTapsDispatched.incrementAndGet()
    }

    fun recordTradeSettled() {
        totalSettled.incrementAndGet()
        lastTradeTimestampMs = System.currentTimeMillis()
    }

    fun getVetoCount(cat: VetoCategory): Long = vetoCounts[cat]?.get() ?: 0L

    fun getVetoCountsSnapshot(): Map<VetoCategory, Long> =
        vetoCounts.mapValues { it.value.get() }

    fun buildVetoStatsJson(): String {
        val evals = totalEvaluations.get()
        val cands = totalCandidateSignals.get()
        val auth = totalAuthorized.get()
        val convRate = if (cands > 0) (auth.toDouble() / cands.toDouble()) else 0.0

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"evaluations\": $evals,\n")
        sb.append("  \"candidate_signals\": $cands,\n")
        sb.append("  \"authorized\": $auth,\n")
        sb.append(String.format(Locale.US, "  \"conversion_rate\": %.4f,\n", convRate))
        sb.append("  \"vetos\": {\n")
        val cats = VetoCategory.values()
        cats.forEachIndexed { idx, cat ->
            val count = vetoCounts[cat]?.get() ?: 0L
            val comma = if (idx < cats.size - 1) "," else ""
            sb.append("    \"${cat.name}\": $count$comma\n")
        }
        sb.append("  },\n")
        sb.append("  \"funnel\": {\n")
        val ticks = totalTicks.get().coerceAtLeast(evals)
        val pTiming = passedTiming.get()
        val pStrategy = passedStrategy.get()
        val pAdaptive = passedAdaptive.get()
        val pRisk = passedRisk.get()
        val taps = totalTapsDispatched.get()
        val settled = totalSettled.get()

        val evalRate = if (ticks > 0) evals.toDouble() / ticks.toDouble() else 0.0
        val candRate = if (evals > 0) cands.toDouble() / evals.toDouble() else 0.0
        val timingRate = if (cands > 0) pTiming.toDouble() / cands.toDouble() else 0.0
        val stratRate = if (pTiming > 0) pStrategy.toDouble() / pTiming.toDouble() else 0.0
        val adaptRate = if (pStrategy > 0) pAdaptive.toDouble() / pStrategy.toDouble() else 0.0
        val riskRate = if (pAdaptive > 0) pRisk.toDouble() / pAdaptive.toDouble() else 0.0
        val execRate = if (auth > 0) taps.toDouble() / auth.toDouble() else 0.0

        sb.append("    \"total_ticks\": $ticks,\n")
        sb.append("    \"evaluations\": $evals,\n")
        sb.append("    \"candidate_signals\": $cands,\n")
        sb.append("    \"passed_timing\": $pTiming,\n")
        sb.append("    \"passed_strategy\": $pStrategy,\n")
        sb.append("    \"passed_adaptive\": $pAdaptive,\n")
        sb.append("    \"passed_risk\": $pRisk,\n")
        sb.append("    \"authorized\": $auth,\n")
        sb.append("    \"taps_dispatched\": $taps,\n")
        sb.append("    \"settled\": $settled,\n")
        sb.append(String.format(Locale.US, "    \"evaluation_rate\": %.4f,\n", evalRate))
        sb.append(String.format(Locale.US, "    \"candidate_rate\": %.4f,\n", candRate))
        sb.append(String.format(Locale.US, "    \"timing_pass_rate\": %.4f,\n", timingRate))
        sb.append(String.format(Locale.US, "    \"strategy_pass_rate\": %.4f,\n", stratRate))
        sb.append(String.format(Locale.US, "    \"adaptive_pass_rate\": %.4f,\n", adaptRate))
        sb.append(String.format(Locale.US, "    \"risk_pass_rate\": %.4f,\n", riskRate))
        sb.append(String.format(Locale.US, "    \"execution_rate\": %.4f\n", execRate))
        sb.append("  }\n")
        sb.append("}")
        return sb.toString()
    }

    fun buildNoTradeDiagnosticJson(
        remainingCooldownSeconds: Int,
        entryWindow: String = ":01-:05",
        hasPendingTrade: Boolean,
        isWsHealthy: Boolean,
        demoAccountStatus: String
    ): String {
        val now = System.currentTimeMillis()
        val durationSec = (now - lastTradeTimestampMs) / 1000L

        // Calcular vetos recientes desde el último trade
        val recentSinceLastTrade = recentRecords.filter { it.timestampMs >= lastTradeTimestampMs }
        val evalsSince = recentSinceLastTrade.size
        val candsSince = recentSinceLastTrade.count { it.vetoCategory != null || it.allowed }

        val categoryCounts = mutableMapOf<VetoCategory, Int>()
        for (rec in recentSinceLastTrade) {
            val cat = rec.vetoCategory ?: continue
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }

        val totalVetoedSince = categoryCounts.values.sum().coerceAtLeast(1)
        val sortedBlockers = categoryCounts.entries.sortedByDescending { it.value }

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"no_trade_duration_seconds\": $durationSec,\n")
        sb.append("  \"evaluations_since_last_trade\": $evalsSince,\n")
        sb.append("  \"candidate_signals_since_last_trade\": $candsSince,\n")
        sb.append("  \"top_blockers\": {\n")

        if (sortedBlockers.isEmpty()) {
            sb.append("    \"NONE\": \"0.0%\"\n")
        } else {
            sortedBlockers.take(5).forEachIndexed { index, entry ->
                val pct = (entry.value.toDouble() / totalVetoedSince.toDouble()) * 100.0
                val comma = if (index < minOf(sortedBlockers.size, 5) - 1) "," else ""
                sb.append(String.format(Locale.US, "    \"%s\": \"%.1f%%\"%s\n", entry.key.name, pct, comma))
            }
        }
        sb.append("  },\n")
        sb.append("  \"remaining_cooldown_seconds\": $remainingCooldownSeconds,\n")
        sb.append("  \"entry_window\": \"$entryWindow\",\n")
        sb.append("  \"pending_trade\": $hasPendingTrade,\n")
        sb.append("  \"ws_status\": \"${if (isWsHealthy) "healthy" else "degraded"}\",\n")
        sb.append("  \"demo_status\": \"$demoAccountStatus\"\n")
        sb.append("}")
        return sb.toString()
    }

    fun persistToFile(destFile: File) {
        try {
            destFile.parentFile?.mkdirs()
            destFile.writeText(buildVetoStatsJson())
        } catch (_: Exception) {}
    }

    fun reset() {
        totalTicks.set(0L)
        totalEvaluations.set(0L)
        totalCandidateSignals.set(0L)
        passedTiming.set(0L)
        passedStrategy.set(0L)
        passedAdaptive.set(0L)
        passedRisk.set(0L)
        totalAuthorized.set(0L)
        totalTapsDispatched.set(0L)
        totalSettled.set(0L)
        for (v in vetoCounts.values) {
            v.set(0L)
        }
        recentRecords.clear()
        lastTradeTimestampMs = System.currentTimeMillis()
    }
}
