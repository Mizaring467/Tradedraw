package com.example.tradedraw

/**
 * Minimal causal admission state for the research replay.
 *
 * Mirrors the ordering that matters for replay correctness:
 * - a veto does not consume the candle or create cooldown;
 * - only an accepted/opened trade consumes the candle;
 * - settlement can clear the in-flight trade before the same tick is evaluated
 *   for a new strategy signal;
 * - cooldown starts from the accepted entry, never from a rejected candidate.
 *
 * This is research infrastructure only. It does not change production strategy
 * thresholds, broker execution or risk policy.
 */
internal class CausalReplayGate(
    private val tradeDurationMs: Long,
    private val cooldownMs: Long,
) {
    init {
        require(tradeDurationMs > 0)
        require(cooldownMs >= 0)
    }

    private var openEntryTs: Long? = null
    private var blockedUntilTs: Long = 0L
    private var lastExecutedCandleMinute: Long = -1L

    val hasOpenTrade: Boolean
        get() = openEntryTs != null

    fun canConsider(timestampMs: Long, candleMinute: Long, externalCooldownUntilTs: Long = 0L): Boolean =
        openEntryTs == null &&
            timestampMs >= blockedUntilTs &&
            timestampMs >= externalCooldownUntilTs &&
            candleMinute != lastExecutedCandleMinute

    /**
     * Must only be called after all research/policy vetoes have accepted the signal.
     * A rejected candidate intentionally performs no state mutation.
     */
    fun accept(timestampMs: Long, candleMinute: Long, externalCooldownUntilTs: Long = 0L) {
        check(canConsider(timestampMs, candleMinute, externalCooldownUntilTs)) {
            "CAUSAL_REPLAY_ACCEPT_WHILE_BLOCKED"
        }
        openEntryTs = timestampMs
        lastExecutedCandleMinute = candleMinute
        blockedUntilTs = timestampMs + tradeDurationMs + cooldownMs
    }

    fun isSettlementDue(timestampMs: Long): Boolean =
        openEntryTs?.let { timestampMs - it >= tradeDurationMs } == true

    /**
     * Production onMarketTick settles SHADOW before feeding the same tick to the
     * strategy engine. Clearing here first allows that same tick to be considered
     * afterwards when no cooldown remains.
     */
    fun settle(timestampMs: Long) {
        check(isSettlementDue(timestampMs)) { "CAUSAL_REPLAY_SETTLEMENT_NOT_DUE" }
        openEntryTs = null
    }

    /** End-of-stream closure for research NO_DATA. It does not fabricate an outcome. */
    fun closeWithoutSettlement() {
        openEntryTs = null
    }
}
