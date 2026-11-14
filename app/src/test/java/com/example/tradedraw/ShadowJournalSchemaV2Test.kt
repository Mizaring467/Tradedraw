package com.example.tradedraw

import com.example.tradedraw.persistence.ShadowJournal
import com.example.tradedraw.persistence.ShadowJournalEntry
import org.junit.Assert.*
import org.junit.Test

class ShadowJournalSchemaV2Test {

    @Test
    fun cabecerasV1YV2MantienenInvarianteDeNoReordenamiento() {
        val h1 = ShadowJournal.HEADER_V1.trim().split(",")
        val h2 = ShadowJournal.HEADER_V2.trim().split(",")

        assertEquals(11, h1.size)
        assertEquals(15, h2.size)

        // Los primeros 11 campos de v2 deben coincidir exactamente con v1
        assertEquals(h1, h2.take(11))

        // Los 4 campos agregados en v2
        assertEquals(listOf("schema_version", "sim_result", "sim_exit_price", "sim_balance"), h2.takeLast(4))
    }

    @Test
    fun parserToleraLineasV1SinRomper() {
        val lineaV1 = "1750000000000,2026-10-15T10:00:00,MT_REJECTION,CONSERVATIVE,BUY,0.90,2500.2500,UPTREND,59,dec-123,\"Prueba V1\""
        val entry = ShadowJournal.parseLine(lineaV1)

        assertNotNull(entry)
        assertEquals(1750000000000L, entry?.timestamp)
        assertEquals("MT_REJECTION", entry?.strategy)
        assertEquals("BUY", entry?.action)
        assertEquals(1, entry?.schemaVersion)
        assertNull(entry?.simResult)
        assertNull(entry?.simExitPrice)
        assertNull(entry?.simBalance)
    }

    @Test
    fun parserLeeLineasV2ConCamposSimulados() {
        val lineaV2 = "1750000000000,2026-10-15T10:00:00,MT_REJECTION,CONSERVATIVE,BUY,0.90,2500.2500,UPTREND,59,dec-123,\"Prueba V2\",2,WIN,2505.0000,1016.40"
        val entry = ShadowJournal.parseLine(lineaV2)

        assertNotNull(entry)
        assertEquals(1750000000000L, entry?.timestamp)
        assertEquals("MT_REJECTION", entry?.strategy)
        assertEquals("BUY", entry?.action)
        assertEquals(2, entry?.schemaVersion)
        assertEquals("WIN", entry?.simResult)
        assertEquals(2505.0, entry?.simExitPrice ?: 0.0, 0.001)
        assertEquals(1016.40, entry?.simBalance ?: 0.0, 0.001)
    }

    @Test
    fun roundtripFormatoV1YV2() {
        val entryV1 = ShadowJournalEntry(
            timestamp = 1750000000000L,
            isoDate = "2026-10-15T10:00:00",
            strategy = "MT_REJECTION",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.90f,
            priceY = 2500.25f,
            trend = "UPTREND",
            candleSecond = 59,
            decisionId = "dec-1",
            reason = "V1 entry",
            schemaVersion = 1
        )
        val formattedV1 = ShadowJournal.formatLineV1(entryV1)
        assertEquals(11, formattedV1.trim().split(",").size)

        val entryV2 = entryV1.copy(
            schemaVersion = 2,
            simResult = "LOSS",
            simExitPrice = 2498.0,
            simBalance = 980.0
        )
        val formattedV2 = ShadowJournal.formatLineV2(entryV2)
        val colsV2 = formattedV2.trim().split(",")
        assertEquals(15, colsV2.size)
        assertEquals("2", colsV2[11])
        assertEquals("LOSS", colsV2[12])
    }
}
