package com.example.tradedraw

import org.junit.Assert.assertThrows
import org.junit.Test

class ReplayResearchGuardsTest {
    @Test
    fun singleAssetAndEmptyInputsAreAllowed() {
        ReplayResearchGuards.requireSingleActiveAsset(emptyList())
        ReplayResearchGuards.requireSingleActiveAsset(listOf("A", "A", " A "))
    }

    @Test
    fun mixedAssetsFailClosedWithoutSelectionTimeline() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ReplayResearchGuards.requireSingleActiveAsset(listOf("A", "B", "A"))
        }
        assert(error.message == "MIXED_ASSETS_REQUIRES_ACTIVE_ASSET_TIMELINE")
    }
}
