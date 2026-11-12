package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchMultiAssetSchedulerTest {
    private fun candidate(
        ts: Long,
        asset: String,
        id: String,
        action: TradeAction = TradeAction.BUY,
    ) = ResearchMultiAssetScheduler.Candidate(
        timestampMs = ts,
        asset = asset,
        action = action,
        decisionId = "decision-" + id,
        tradeId = "trade-" + id,
    )

    @Test
    fun globalSingleFlightBlocksSecondAssetWhileFirstTradeIsOpen() {
        val scheduler = ResearchMultiAssetScheduler(
            activeAssetAt = { ts -> if (ts < 1_000) "A" else "B" }
        )

        assertTrue(scheduler.evaluate(candidate(0, "A", "a")) is ResearchMultiAssetScheduler.Decision.Accepted)
        val second = scheduler.evaluate(candidate(1_000, "B", "b"))

        assertEquals(
            ResearchMultiAssetScheduler.Decision.Vetoed("ORDER_PENDING_RECONCILIATION"),
            second,
        )
        assertEquals("trade-a", scheduler.pendingTradeId())
        assertEquals(1, scheduler.transportDispatchCount())
    }

    @Test
    fun expiryAtSameTimestampReleasesSlotBeforeNextCandidate() {
        val scheduler = ResearchMultiAssetScheduler(
            activeAssetAt = { ts -> if (ts < 60_000) "A" else "B" }
        )

        assertTrue(scheduler.evaluate(candidate(0, "A", "a")) is ResearchMultiAssetScheduler.Decision.Accepted)
        val next = scheduler.evaluate(candidate(60_000, "B", "b"))

        assertTrue(next is ResearchMultiAssetScheduler.Decision.Accepted)
        assertEquals("trade-b", scheduler.pendingTradeId())
        assertEquals(2, scheduler.transportDispatchCount())
    }

    @Test
    fun unknownOrMismatchedAssetNeverConsumesGlobalSlot() {
        val unknown = ResearchMultiAssetScheduler(activeAssetAt = { null })
        assertEquals(
            ResearchMultiAssetScheduler.Decision.Vetoed("ACTIVE_ASSET_UNKNOWN"),
            unknown.evaluate(candidate(0, "A", "a")),
        )
        assertNull(unknown.pendingTradeId())
        assertEquals(0, unknown.transportDispatchCount())

        val mismatch = ResearchMultiAssetScheduler(activeAssetAt = { "B" })
        assertEquals(
            ResearchMultiAssetScheduler.Decision.Vetoed("ACTIVE_ASSET_MISMATCH"),
            mismatch.evaluate(candidate(0, "A", "a")),
        )
        assertNull(mismatch.pendingTradeId())
        assertEquals(0, mismatch.transportDispatchCount())
    }

    @Test
    fun candidateBeforeExpiryDoesNotReleaseReservation() {
        val scheduler = ResearchMultiAssetScheduler(activeAssetAt = { "A" })
        scheduler.evaluate(candidate(0, "A", "a"))

        assertNull(scheduler.settleDueBeforeCandidate(59_999))
        assertEquals("trade-a", scheduler.pendingTradeId())
        assertEquals(
            candidate(0, "A", "a"),
            scheduler.settleDueBeforeCandidate(60_000),
        )
        assertNull(scheduler.pendingTradeId())
    }
}
