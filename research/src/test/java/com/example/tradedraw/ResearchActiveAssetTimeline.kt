package com.example.tradedraw

import org.json.JSONObject
import java.io.File

/**
 * Versioned, research-only active-asset timeline.
 *
 * The manifest is generated from append-only prospective ASSET observations.
 * Lookup is causal: only observations at or before the query are eligible, and
 * staleness/conflicts resolve to UNKNOWN rather than inference.
 */
internal class ResearchActiveAssetTimeline private constructor(
    val sourceJournalFinalSha256: String,
    val collectorSha256: String,
    val codeSha: String,
    val maxAgeMs: Long,
    private val observations: List<Observation>,
) {
    data class Observation(
        val atMs: Long,
        val ric: String,
        val quality: String,
    )

    enum class Status {
        KNOWN_CAUSAL_OBSERVATION,
        UNKNOWN_NO_PRIOR_OBSERVATION,
        UNKNOWN_STALE_OBSERVATION,
        UNKNOWN_CONFLICT_AT_TIMESTAMP,
    }

    data class Lookup(
        val status: Status,
        val ric: String?,
        val observationAtMs: Long?,
        val ageMs: Long?,
    )

    fun lookup(timestampMs: Long): Lookup {
        require(timestampMs >= 0L) { "INVALID_QUERY_TIMESTAMP" }

        val eligible = observations.filter { it.atMs <= timestampMs }
        if (eligible.isEmpty()) {
            return Lookup(Status.UNKNOWN_NO_PRIOR_OBSERVATION, null, null, null)
        }

        val latestAt = eligible.maxOf { it.atMs }
        val latest = eligible.filter { it.atMs == latestAt }
        val rics = latest.map { it.ric }.distinct()
        val age = timestampMs - latestAt
        if (rics.size != 1) {
            return Lookup(Status.UNKNOWN_CONFLICT_AT_TIMESTAMP, null, latestAt, age)
        }
        if (age > maxAgeMs) {
            return Lookup(Status.UNKNOWN_STALE_OBSERVATION, null, latestAt, age)
        }
        return Lookup(Status.KNOWN_CAUSAL_OBSERVATION, rics.single(), latestAt, age)
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

        fun fromManifest(file: File): ResearchActiveAssetTimeline {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            require(root.getInt("schema_version") == SCHEMA_VERSION) {
                "UNSUPPORTED_ACTIVE_ASSET_TIMELINE_SCHEMA"
            }
            val sourceHash = root.getString("source_journal_final_sha256")
            val collectorHash = root.getString("collector_sha256")
            val codeSha = root.getString("code_sha")
            require(SHA256.matches(sourceHash)) { "INVALID_SOURCE_JOURNAL_SHA256" }
            require(SHA256.matches(collectorHash)) { "INVALID_COLLECTOR_SHA256" }
            require(codeSha.isNotBlank()) { "CODE_SHA_REQUIRED" }

            val maxAge = root.getLong("max_age_ms")
            require(maxAge >= 0L) { "INVALID_MAX_AGE" }

            val raw = root.getJSONArray("observations")
            val observations = buildList {
                for (i in 0 until raw.length()) {
                    val row = raw.getJSONObject(i)
                    val atMs = row.getLong("atMs")
                    val ric = row.getString("ric").trim()
                    val quality = row.getString("quality")
                    require(atMs >= 0L) { "INVALID_OBSERVATION_TIMESTAMP" }
                    require(ric.isNotBlank()) { "OBSERVATION_RIC_REQUIRED" }
                    require(quality == "RUNTIME_SCREEN_ASSET_OBSERVED") {
                        "UNSUPPORTED_OBSERVATION_QUALITY"
                    }
                    add(Observation(atMs, ric, quality))
                }
            }
            return ResearchActiveAssetTimeline(
                sourceJournalFinalSha256 = sourceHash.lowercase(),
                collectorSha256 = collectorHash.lowercase(),
                codeSha = codeSha,
                maxAgeMs = maxAge,
                observations = observations,
            )
        }
    }
}
