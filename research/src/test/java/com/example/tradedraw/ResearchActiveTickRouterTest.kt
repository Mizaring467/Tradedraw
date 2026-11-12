package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResearchActiveTickRouterTest {
    private fun fixtureTimeline(): ResearchActiveAssetTimeline {
        val start = File(System.getProperty("user.dir"))
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "data/fixtures/research/active_asset_timeline_v1.json").isFile }
            ?: error("RESEARCH_FIXTURE_ROOT_NOT_FOUND")
        return ResearchActiveAssetTimeline.fromManifest(
            File(root, "data/fixtures/research/active_asset_timeline_v1.json")
        )
    }

    @Test
    fun onlyTickMatchingCausalActiveAssetIsAccepted() {
        val router = ResearchActiveTickRouter(fixtureTimeline())
        val accepted = router.route(ResearchActiveTickRouter.Tick(1_500, "A", 100.0))
        assertTrue(accepted is ResearchActiveTickRouter.Routed.Accepted)
        assertFalse((accepted as ResearchActiveTickRouter.Routed.Accepted).assetChanged)

        assertEquals(
            ResearchActiveTickRouter.RejectReason.ACTIVE_ASSET_MISMATCH,
            (router.route(ResearchActiveTickRouter.Tick(1_600, "B", 101.0)) as ResearchActiveTickRouter.Routed.Rejected).reason,
        )
    }

    @Test
    fun switchIsReportedOnlyWhenFirstAcceptedTickOfNewAssetArrives() {
        val router = ResearchActiveTickRouter(fixtureTimeline())
        router.route(ResearchActiveTickRouter.Tick(1_500, "A", 100.0))
        val switched = router.route(ResearchActiveTickRouter.Tick(2_100, "B", 200.0))
        assertTrue((switched as ResearchActiveTickRouter.Routed.Accepted).assetChanged)
        val same = router.route(ResearchActiveTickRouter.Tick(2_200, "B", 201.0))
        assertFalse((same as ResearchActiveTickRouter.Routed.Accepted).assetChanged)
    }

    @Test
    fun unknownTimelineAndConflictsFailClosed() {
        val router = ResearchActiveTickRouter(fixtureTimeline())
        assertEquals(
            ResearchActiveTickRouter.RejectReason.ACTIVE_ASSET_UNKNOWN,
            (router.route(ResearchActiveTickRouter.Tick(999, "A", 1.0)) as ResearchActiveTickRouter.Routed.Rejected).reason,
        )
        assertEquals(
            ResearchActiveTickRouter.RejectReason.ACTIVE_ASSET_UNKNOWN,
            (router.route(ResearchActiveTickRouter.Tick(3_000, "C", 1.0)) as ResearchActiveTickRouter.Routed.Rejected).reason,
        )
    }

    @Test
    fun acceptedStreamCannotMoveBackwardsInTime() {
        val router = ResearchActiveTickRouter(fixtureTimeline())
        router.route(ResearchActiveTickRouter.Tick(1_500, "A", 1.0))
        assertEquals(
            ResearchActiveTickRouter.RejectReason.OUT_OF_ORDER_TICK,
            (router.route(ResearchActiveTickRouter.Tick(1_400, "A", 1.0)) as ResearchActiveTickRouter.Routed.Rejected).reason,
        )
    }
}
