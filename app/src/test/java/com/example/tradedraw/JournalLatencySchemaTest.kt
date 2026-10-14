package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 14 (A2): contrato de instrumentacion de latencia en el journal.
 *
 * El esquema es SOLO-APPEND. Estos tests fijan las dos mitades del contrato:
 *  1. un lector VIEJO sobre un journal NUEVO sigue obteniendo las 21 columnas de siempre;
 *  2. un lector NUEVO sobre un journal VIEJO no revienta: los campos que no existian se leen
 *     como ausencia, nunca como un 0 que parezca una medicion real.
 */
class JournalLatencySchemaTest {

    private val viejas = listOf("timestamp", "iso_date", "strategy", "submode", "action",
        "confidence", "price_y", "stake", "base_balance", "result", "settled_balance",
        "duration_sec", "trend", "dist_support", "dist_resistance", "tick_velocity",
        "impulse", "market_regime", "candle_second", "adaptive_status", "reason")

    private val nuevas = listOf("signal_ts", "tap_start_ts", "tap_end_ts",
        "candle_second_at_tap_end", "price_at_signal", "price_at_tap_end")

    private fun header(): List<String> =
        TradeJournalLogger.headerForTests().trim().split(",")

    @Test
    fun elEsquemaSoloAnadeColumnasAlFinal() {
        val h = header()
        assertEquals("Las 21 columnas clasicas deben seguir intactas y en el mismo orden",
            viejas, h.take(21))
    }

    @Test
    fun lasColumnasNuevasEstanYNoSeRepiten() {
        val h = header()
        for (n in nuevas) {
            assertTrue("Falta la columna $n", h.contains(n))
        }
        assertEquals("No puede haber columnas duplicadas", h.size, h.distinct().size)
        assertEquals(21 + nuevas.size, h.size)
    }

    @Test
    fun sinMedicionLasCeldasQuedanVaciasNoCero() {
        // El caso peligroso: un trade sin tap medido. Un 0 en `tap_end_ts` o en
        // `candle_second_at_tap_end` se leeria como "tap en el segundo 0", una medicion falsa.
        val linea = TradeJournalLogger.buildLine(
            action = "BUY", result = "WIN", reason = "test", strategy = "MT", submode = "YOLO",
            confidence = 0.9f, priceY = 0f, stake = 1, baseBalance = 100.0, settledBalance = 101.0,
            durationSec = 62L, trend = "UP", distSupportRatio = 0.5f, distResistanceRatio = 0.5f,
            tickVelocity = 0f, impulse = "NEUTRAL", marketRegime = "NORMAL",
            candleSecond = 3, adaptiveStatus = "WIN", latency = TradeJournalLogger.LatencyRecord()
        )
        val cols = linea.trim().split(",")
        // Las 6 ultimas columnas son las nuevas; ninguna puede ser "0".
        val ultimas = cols.takeLast(6)
        assertEquals(6, ultimas.size)
        for (v in ultimas) {
            assertTrue("Celda sin medicion debe ir vacia, no '$v'", v.isEmpty())
        }
    }

    @Test
    fun conMedicionSeEscribenLosInstantes() {
        val linea = TradeJournalLogger.buildLine(
            action = "BUY", result = "WIN", reason = "test", strategy = "MT", submode = "YOLO",
            confidence = 0.9f, priceY = 0f, stake = 1, baseBalance = 100.0, settledBalance = 101.0,
            durationSec = 62L, trend = "UP", distSupportRatio = 0.5f, distResistanceRatio = 0.5f,
            tickVelocity = 0f, impulse = "NEUTRAL", marketRegime = "NORMAL",
            candleSecond = 1, adaptiveStatus = "WIN",
            latency = TradeJournalLogger.LatencyRecord(
                signalTs = 1000L, tapStartTs = 1100L, tapEndTs = 1750L,
                candleSecondAtTapEnd = 2, priceAtSignal = 100.5, priceAtTapEnd = 100.7
            )
        )
        val cols = linea.trim().split(",")
        val ultimas = cols.takeLast(6)
        assertEquals("1000", ultimas[0])
        assertEquals("1100", ultimas[1])
        assertEquals("1750", ultimas[2])
        assertEquals("2", ultimas[3])
        assertTrue("precio de senal", ultimas[4].startsWith("100.5"))
        assertTrue("precio al terminar el tap", ultimas[5].startsWith("100.7"))
    }

    @Test
    fun lectorNuevoSobreJournalViejoNoInventaDatos() {
        // Fila con el esquema de 21 columnas (el que existe hoy en disco).
        val filaVieja = "1700000000000,2026-10-12T00:00:00,MT,YOLO,BUY,0.90,1.0000,1.00,100.00,WIN,101.00,62,UP,0.50,0.50,0.00,NEUTRAL,NORMAL,3,WIN,\"motivo\""
        val cols = filaVieja.split(",")
        assertEquals("La fila vieja tiene 21 columnas", 21, cols.size)
        // Un lector nuevo indexa por header: las columnas 21..26 simplemente no existen.
        val headerNuevo = header()
        val idxSignal = headerNuevo.indexOf("signal_ts")
        assertTrue("El lector nuevo conoce la columna", idxSignal >= 0)
        assertTrue("Pero la fila vieja no la trae: se lee como ausencia",
            idxSignal >= cols.size)
    }
}
