package com.example.tradedraw

/**
 * Research-only guard: a mixed-asset tick file does not tell us which broker tab
 * was actually selected at each instant. Splitting it into independent portfolios
 * would bypass the runtime's global single-flight semantics and can over-count trades.
 */
internal object ReplayResearchGuards {
    fun requireSingleActiveAsset(assets: Iterable<String>) {
        val distinct = assets.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2).toList()
        require(distinct.size <= 1) {
            "MIXED_ASSETS_REQUIRES_ACTIVE_ASSET_TIMELINE"
        }
    }
}
