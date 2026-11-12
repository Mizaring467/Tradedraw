package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class CausalReplayGateTest {
    @Test
    fun rejectedCandidateDoesNotCreatePhantomCooldownOrConsumeCandle() {
        val gate = CausalReplayGate(tradeDurationMs = 60_000L, cooldownMs = 35_000L)

        assertTrue(gate.canConsider(timestampMs = 1_000L, candleMinute = 0L))
        // Adaptive/policy veto: do NOT call accept().
        assertTrue(gate.canConsider(timestampMs = 2_000L, candleMinute = 0L))
        assertFalse(gate.hasOpenTrade)
    }

    @Test
    fun acceptedTradeBlocksUntilSettlementAndConfiguredCooldown() {
        val gate = CausalReplayGate(tradeDurationMs = 60_000L, cooldownMs = 35_000L)
        gate.accept(timestampMs = 0L, candleMinute = 0L)

        assertTrue(gate.hasOpenTrade)
        assertFalse(gate.isSettlementDue(59_999L))
        assertTrue(gate.isSettlementDue(60_000L))
        gate.settle(60_000L)

        assertFalse(gate.hasOpenTrade)
        assertFalse(gate.canConsider(timestampMs = 60_000L, candleMinute = 1L))
        assertTrue(gate.canConsider(timestampMs = 95_000L, candleMinute = 1L))
    }

    @Test
    fun settlementCanHappenBeforeSameTickCandidateEvaluation() {
        val gate = CausalReplayGate(tradeDurationMs = 60_000L, cooldownMs = 0L)
        gate.accept(timestampMs = 0L, candleMinute = 0L)

        // Runtime order: shadowSimulation.onTick(tick) first...
        gate.settle(timestampMs = 60_000L)
        // ...then syntheticCandleEngine.onNewTick(tick).
        assertTrue(gate.canConsider(timestampMs = 60_000L, candleMinute = 1L))
    }

    @Test
    fun endOfStreamClosureDoesNotPretendSettlementOccurred() {
        val gate = CausalReplayGate(tradeDurationMs = 60_000L, cooldownMs = 0L)
        gate.accept(timestampMs = 10_000L, candleMinute = 0L)
        assertTrue(gate.hasOpenTrade)

        gate.closeWithoutSettlement()
        assertFalse(gate.hasOpenTrade)
    }

    @Test
    fun externalGapCooldownIsIndependentFromCandidateVetoes() {
        val gate = CausalReplayGate(tradeDurationMs = 60_000L, cooldownMs = 0L)
        assertFalse(gate.canConsider(100_000L, candleMinute = 1L, externalCooldownUntilTs = 120_000L))
        assertTrue(gate.canConsider(120_000L, candleMinute = 2L, externalCooldownUntilTs = 120_000L))
    }
}
