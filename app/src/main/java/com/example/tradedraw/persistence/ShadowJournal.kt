package com.example.tradedraw.persistence

import java.util.Locale

data class ShadowJournalEntry(
    val timestamp: Long,
    val isoDate: String,
    val strategy: String,
    val submode: String,
    val action: String,
    val confidence: Float,
    val priceY: Float,
    val trend: String,
    val candleSecond: Int,
    val decisionId: String,
    val reason: String,
    val schemaVersion: Int = 1,
    val simResult: String? = null,
    val simExitPrice: Double? = null,
    val simBalance: Double? = null,
    val entryPrice: Double? = null
)

/**
 * Esquema y parser versionado de shadow_journal (v1 y v2).
 *
 * Contrato OD-4 / P-4:
 * - v1: 11 columnas originales congeladas.
 * - v2: 15 columnas con schema_version, sim_result, sim_exit_price, sim_balance agregadas al final.
 * - Lector tolerante: lee tanto archivos v1 de 11 columnas como v2 de 15 columnas sin reordenar columnas previas.
 */
object ShadowJournal {
    const val HEADER_V1 = "timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason\n"
    const val HEADER_V2 = "timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason,schema_version,sim_result,sim_exit_price,sim_balance\n"
    const val SHADOW_JOURNAL_NAME = "shadow_journal.csv"

    fun formatLineV1(entry: ShadowJournalEntry): String {
        return String.format(
            Locale.US,
            "%d,%s,%s,%s,%s,%.2f,%.4f,%s,%d,%s,\"%s\"\n",
            entry.timestamp, entry.isoDate, entry.strategy, entry.submode, entry.action,
            entry.confidence, entry.priceY, entry.trend, entry.candleSecond,
            entry.decisionId.trim().replace(",", ""), entry.reason.replace(",", ";").take(140)
        )
    }

    fun formatLineV2(entry: ShadowJournalEntry): String {
        fun clean(value: String) = value.replace(",", ";").replace("\n", " ").replace("\r", " ").replace('"', '\'')
        val fields = listOf(entry.timestamp.toString(), clean(entry.isoDate), clean(entry.strategy), clean(entry.submode),
            clean(entry.action), entry.confidence.toString(), (entry.entryPrice ?: entry.priceY.toDouble()).toString(),
            clean(entry.trend), entry.candleSecond.toString(), clean(entry.decisionId), "\"${clean(entry.reason)}\"",
            "2", clean(entry.simResult.orEmpty()), entry.simExitPrice?.toString().orEmpty(), entry.simBalance?.toString().orEmpty())
        return fields.joinToString(",") + "\n"
    }

    fun parseLine(line: String): ShadowJournalEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("timestamp,")) return null
        val parts = trimmed.split(",")
        if (parts.size < 11) return null
        return try {
            val ts = parts[0].toLongOrNull() ?: return null
            val iso = parts[1]
            val strat = parts[2]
            val sub = parts[3]
            val act = parts[4]
            val conf = parts[5].toFloatOrNull() ?: 0f
            val py = parts[6].toFloatOrNull() ?: 0f
            val tr = parts[7]
            val cs = parts[8].toIntOrNull() ?: 0
            val did = parts[9]
            val rsn = parts[10].trim('\"')

            if (parts.size >= 15 && parts[11].toIntOrNull() == 2) {
                ShadowJournalEntry(
                    timestamp = ts,
                    isoDate = iso,
                    strategy = strat,
                    submode = sub,
                    action = act,
                    confidence = conf,
                    priceY = py,
                    trend = tr,
                    candleSecond = cs,
                    decisionId = did,
                    reason = rsn,
                    schemaVersion = 2,
                    simResult = parts[12].ifBlank { null },
                    simExitPrice = parts[13].toDoubleOrNull(),
                    simBalance = parts[14].toDoubleOrNull(),
                    entryPrice = parts[6].toDoubleOrNull()
                )
            } else {
                ShadowJournalEntry(
                    timestamp = ts,
                    isoDate = iso,
                    strategy = strat,
                    submode = sub,
                    action = act,
                    confidence = conf,
                    priceY = py,
                    trend = tr,
                    candleSecond = cs,
                    decisionId = did,
                    reason = rsn,
                    schemaVersion = 1
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
