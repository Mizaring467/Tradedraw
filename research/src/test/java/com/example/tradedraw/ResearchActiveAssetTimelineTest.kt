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

    @Test
    fun sharedGoldenContractMatchesKotlinLookup() {
        val start = File(System.getProperty("user.dir"))
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "data/fixtures/research/active_asset_timeline_v1.json").isFile }
            ?: error("RESEARCH_FIXTURE_ROOT_NOT_FOUND")
        val manifestFile = File(root, "data/fixtures/research/active_asset_timeline_v1.json")
        val casesFile = File(root, "data/fixtures/research/active_asset_timeline_v1_cases.json")
        val timeline = ResearchActiveAssetTimeline.fromManifest(manifestFile)
        val cases = org.json.JSONArray(casesFile.readText(Charsets.UTF_8))
        for (i in 0 until cases.length()) {
            val expected = cases.getJSONObject(i)
            val actual = timeline.lookup(expected.getLong("timestamp_ms"))
            assertEquals(expected.getString("status"), actual.status.name)
            if (expected.isNull("ric")) assertEquals(null, actual.ric) else assertEquals(expected.getString("ric"), actual.ric)
            if (expected.isNull("observation_at_ms")) assertEquals(null, actual.observationAtMs) else assertEquals(expected.getLong("observation_at_ms"), actual.observationAtMs)
            if (expected.isNull("age_ms")) assertEquals(null, actual.ageMs) else assertEquals(expected.getLong("age_ms"), actual.ageMs)
        }
    }

}
