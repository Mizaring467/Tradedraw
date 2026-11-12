package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResearchActiveAssetTimelineTest {
    private fun manifest(
        maxAgeMs: Long = 1_000L,
        observations: String = """
            [
              {"atMs":1000,"ric":"A","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"},
              {"atMs":2000,"ric":"B","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"}
            ]
        """.trimIndent(),
    ): File {
        val file = File.createTempFile("active-asset-", ".json")
        file.writeText(
            """
            {
              "schema_version":1,
              "source_journal_final_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "collector_sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
              "code_sha":"deadbeef",
              "max_age_ms":$maxAgeMs,
              "observations":$observations
            }
            """.trimIndent()
        )
        file.deleteOnExit()
        return file
    }

    @Test
    fun manifestLookupIsCausalAndAgeBounded() {
        val timeline = ResearchActiveAssetTimeline.fromManifest(manifest())

        assertEquals(
            ResearchActiveAssetTimeline.Status.UNKNOWN_NO_PRIOR_OBSERVATION,
            timeline.lookup(999).status,
        )
        assertEquals("A", timeline.lookup(1_500).ric)
        assertEquals("B", timeline.lookup(2_500).ric)
        assertEquals(
            ResearchActiveAssetTimeline.Status.UNKNOWN_STALE_OBSERVATION,
            timeline.lookup(3_001).status,
        )
    }

    @Test
    fun conflictingSameTimestampRicsRemainUnknown() {
        val timeline = ResearchActiveAssetTimeline.fromManifest(
            manifest(
                observations = """
                    [
                      {"atMs":1000,"ric":"A","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"},
                      {"atMs":1000,"ric":"B","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"}
                    ]
                """.trimIndent()
            )
        )
        assertEquals(
            ResearchActiveAssetTimeline.Status.UNKNOWN_CONFLICT_AT_TIMESTAMP,
            timeline.lookup(1_000).status,
        )
    }

    @Test
    fun invalidManifestIdentityFailsClosed() {
        val file = manifest()
        file.writeText(file.readText().replace("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "not-a-hash"))
        assertThrows(IllegalArgumentException::class.java) {
            ResearchActiveAssetTimeline.fromManifest(file)
        }
    }

    @Test
    fun timelineCanDriveProductionSingleFlightSchedulerWithoutFutureInference() {
        val timeline = ResearchActiveAssetTimeline.fromManifest(manifest(maxAgeMs = 1_000L))
        val scheduler = ResearchMultiAssetScheduler(
            activeAssetAt = { ts ->
                timeline.lookup(ts).takeIf {
                    it.status == ResearchActiveAssetTimeline.Status.KNOWN_CAUSAL_OBSERVATION
                }?.ric
            }
        )
        fun c(ts: Long, asset: String, id: String) =
            ResearchMultiAssetScheduler.Candidate(
                ts, asset, TradeAction.BUY, "d-" + id, "t-" + id
            )

        assertTrue(scheduler.evaluate(c(1_000, "A", "a")) is ResearchMultiAssetScheduler.Decision.Accepted)
        assertEquals(
            ResearchMultiAssetScheduler.Decision.Vetoed("ACTIVE_ASSET_UNKNOWN"),
            scheduler.evaluate(c(61_000, "B", "b")),
        )
    }
}
