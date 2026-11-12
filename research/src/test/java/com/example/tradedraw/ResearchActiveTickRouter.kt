package com.example.tradedraw

/**
 * Research-only causal router from a mixed tick stream to the asset that was
 * actually observed active on screen. It never interpolates future state.
 */
internal class ResearchActiveTickRouter(
    private val timeline: ResearchActiveAssetTimeline,
) {
    data class Tick(
        val timestampMs: Long,
        val asset: String,
        val price: Double,
    )

    enum class RejectReason {
        ACTIVE_ASSET_UNKNOWN,
        ACTIVE_ASSET_MISMATCH,
        OUT_OF_ORDER_TICK,
    }

    sealed class Routed {
        data class Accepted(
            val tick: Tick,
            val activeAsset: String,
            val assetChanged: Boolean,
        ) : Routed()

        data class Rejected(
            val tick: Tick,
            val reason: RejectReason,
        ) : Routed()
    }

    private var lastSeenTimestampMs: Long = -1L
    private var lastActiveAsset: String? = null

    fun route(tick: Tick): Routed {
        require(tick.timestampMs >= 0L) { "INVALID_TICK_TIMESTAMP" }
        require(tick.asset.isNotBlank()) { "TICK_ASSET_REQUIRED" }
        require(tick.price.isFinite() && tick.price > 0.0) { "INVALID_TICK_PRICE" }

        if (lastSeenTimestampMs >= 0L && tick.timestampMs < lastSeenTimestampMs) {
            return Routed.Rejected(tick, RejectReason.OUT_OF_ORDER_TICK)
        }
        lastSeenTimestampMs = tick.timestampMs

        val lookup = timeline.lookup(tick.timestampMs)
        val active = lookup.ric
            ?: return Routed.Rejected(tick, RejectReason.ACTIVE_ASSET_UNKNOWN)
        if (!active.equals(tick.asset, ignoreCase = true)) {
            return Routed.Rejected(tick, RejectReason.ACTIVE_ASSET_MISMATCH)
        }

        val changed = lastActiveAsset?.equals(active, ignoreCase = true) == false
        lastActiveAsset = active
        return Routed.Accepted(tick, active, changed)
    }
}
