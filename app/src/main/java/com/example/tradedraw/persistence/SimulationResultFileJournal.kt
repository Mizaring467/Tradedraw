package com.example.tradedraw.persistence

import com.example.tradedraw.simulation.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** v1 is never rewritten. v2 has its own header/file, and idempotent atomic replacement per settlement. */
class SimulationResultFileJournal(private val directory: File) : SimulationResultJournal {
    companion object {
        const val FILE_NAME = "shadow_journal_v2.csv"
        private val locks = ConcurrentHashMap<String, Any>()
    }
    override fun append(result: SettledSimulatedTrade): Boolean {
        val trace = "tradeId=${result.tradeId};asset=${result.asset};entryTs=${result.entryTimestampMs};${result.metadata.reason}"
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(result.exitTimestampMs))
        val entry = ShadowJournalEntry(result.exitTimestampMs, date, result.metadata.strategy, result.metadata.submode,
            result.action.name, result.metadata.confidence, result.entryPrice.toFloat(), result.metadata.trend,
            ((result.entryTimestampMs / 1000) % 60).toInt(), result.decisionId, trace, 2,
            result.result, result.exitPrice, result.settledBalance, result.entryPrice)
        return appendEntry(entry)
    }
    fun appendEntry(entry: ShadowJournalEntry): Boolean = synchronized(locks.getOrPut(directory.absolutePath) { Any() }) {
        require(entry.decisionId.isNotBlank()) { "SIM_JOURNAL_ID_REQUIRED" }
        val canonical = requireNotNull(ShadowJournal.parseLine(ShadowJournal.formatLineV2(entry)))
        val file = File(directory, FILE_NAME)
        val lines = if (file.exists()) file.readLines() else emptyList()
        check(lines.isEmpty() || lines.first() == ShadowJournal.HEADER_V2.trimEnd()) { "SIM_JOURNAL_HEADER_MISMATCH" }
        val existing = lines.drop(1).map { ShadowJournal.parseLine(it) ?: error("SIM_JOURNAL_CORRUPT_ROW") }
        val prior = existing.firstOrNull { it.decisionId == entry.decisionId && it.simResult == entry.simResult }
        if (prior != null) {
            check(prior.timestamp == canonical.timestamp && prior.reason == canonical.reason && prior.simExitPrice == canonical.simExitPrice && prior.simBalance == canonical.simBalance) { "SIM_JOURNAL_ID_CONFLICT" }
            return@synchronized true
        }
        check(directory.isDirectory || directory.mkdirs())
        val tmp = File(file.path + ".new")
        FileOutputStream(tmp).use { stream ->
            val text = if (lines.isEmpty()) ShadowJournal.HEADER_V2 else lines.joinToString("\n") + "\n"
            stream.write((text + ShadowJournal.formatLineV2(entry)).toByteArray(Charsets.UTF_8)); stream.flush(); stream.fd.sync()
        }
        check(tmp.renameTo(file)) { "SIM_JOURNAL_RENAME_FAILED" }
        true
    }
}

/** This is also the bridge's reader; preserves historical rows/headers and reads v2 explicitly. */
class ShadowJournalFilesReader(private val directories: List<File>) {
    fun readTail(max: Int): Pair<List<String>, Int> {
        val files = directories.distinctBy { it.absolutePath }.flatMap { dir ->
            listOf(File(dir, ShadowJournal.SHADOW_JOURNAL_NAME), File(dir, SimulationResultFileJournal.FILE_NAME))
        }.filter { it.isFile }
        val lines = files.flatMap { FileJournalReader(it).readRecentLines(Int.MAX_VALUE) }.filter { it.isNotBlank() }
        return lines.takeLast(max.coerceIn(1, 2000)) to lines.size
    }
    fun readEntries(): List<ShadowJournalEntry> = directories.distinctBy { it.absolutePath }.flatMap { dir ->
        listOf(File(dir, ShadowJournal.SHADOW_JOURNAL_NAME), File(dir, SimulationResultFileJournal.FILE_NAME))
    }.filter { it.isFile }.flatMap { FileJournalReader(it).readRecentLines(Int.MAX_VALUE) }.mapNotNull(ShadowJournal::parseLine)
}
