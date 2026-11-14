package com.example.tradedraw

import com.example.tradedraw.core.EventSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-1.1: Equivalencia entre [EventSink] ([RingBufferEventSink]) y [TradeEventLog].
 */
class EventSinkEquivalenceTest {

    private val sink: EventSink = RingBufferEventSink()

    @Before
    fun setUp() {
        TradeEventLog.clear()
    }

    @Test
    fun emisionViaSinkLlegaAlBufferPrincipal() {
        val decisionId = "00000000-0000-0000-0000-000000000001"
        sink.add(TradeEventLog.Kind.VETO, "TEST_VETO motivo_prueba", decisionId = decisionId)

        val ev = TradeEventLog.lastOf(TradeEventLog.Kind.VETO)
        assertEquals("TEST_VETO motivo_prueba", ev?.detail)
        assertEquals(decisionId, ev?.decisionId)
        assertEquals(1, TradeEventLog.countOf(TradeEventLog.Kind.VETO))
    }

    @Test
    fun emisionDeModeChangeLlegaAlRingDedicado() {
        sink.add(TradeEventLog.Kind.MODE_CHANGE, "DISABLED -> AUTONOMOUS")

        val modes = TradeEventLog.recentModes(10)
        assertEquals(1, modes.size)
        assertEquals("DISABLED -> AUTONOMOUS", modes[0].detail)
    }

    @Test
    fun emisionConStringKindMapeaCorrectamente() {
        sink.add("TAP", "START (100.0,200.0)")

        val ev = TradeEventLog.lastOf(TradeEventLog.Kind.TAP)
        assertEquals("START (100.0,200.0)", ev?.detail)
        assertEquals(1, TradeEventLog.countOf(TradeEventLog.Kind.TAP))
    }
}
