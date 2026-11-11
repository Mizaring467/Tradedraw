package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.simulation.ManualSimClock
import com.example.tradedraw.simulation.SimLedger
import org.junit.Assert.*
import org.junit.Test

class SimLedgerTest {

    @Test
    fun saldoInicialYMetricasCero() {
        val ledger = SimLedger(initialBalance = 5000.0, payoutRate = 0.85)
        assertEquals(5000.0, ledger.currentBalance, 0.001)
        assertEquals(0, ledger.totalTrades)
        assertEquals(0.0, ledger.winRate, 0.001)
    }

    @Test
    fun tradeGanadorAumentaSaldoConPayout() {
        val ledger = SimLedger(initialBalance = 1000.0, payoutRate = 0.82)
        ledger.openTrade("t1", "d1", TradeAction.BUY, 2500.0, 10.0, 1000L)
        val settled = ledger.resolveTrade("t1", 2501.0, 61000L)

        assertNotNull(settled)
        assertEquals("WIN", settled?.result)
        assertEquals(1008.2, ledger.currentBalance, 0.001)
        assertEquals(1, ledger.totalWins.get())
        assertEquals(0, ledger.totalLosses.get())
        assertEquals(100.0, ledger.winRate, 0.001)
    }

    @Test
    fun tradePerdedorReduceSaldoExactamentePorStake() {
        val ledger = SimLedger(initialBalance = 1000.0, payoutRate = 0.82)
        ledger.openTrade("t1", "d1", TradeAction.SELL, 2500.0, 20.0, 1000L)
        val settled = ledger.resolveTrade("t1", 2502.0, 61000L) // Sube en SELL -> LOSS

        assertNotNull(settled)
        assertEquals("LOSS", settled?.result)
        assertEquals(980.0, ledger.currentBalance, 0.001)
        assertEquals(0, ledger.totalWins.get())
        assertEquals(1, ledger.totalLosses.get())
        assertEquals(0.0, ledger.winRate, 0.001)
    }

    @Test
    fun tradeEmpateMantieneSaldoInalterado() {
        val ledger = SimLedger(initialBalance = 1000.0, payoutRate = 0.82)
        ledger.openTrade("t1", "d1", TradeAction.BUY, 2500.0, 15.0, 1000L)
        val settled = ledger.resolveTrade("t1", 2500.0, 61000L) // Mismo precio -> TIE

        assertNotNull(settled)
        assertEquals("TIE", settled?.result)
        assertEquals(1000.0, ledger.currentBalance, 0.001)
        assertEquals(1, ledger.totalTies.get())
        assertEquals(1, ledger.totalTrades)
    }

    @Test
    fun simClockAvanceYSet() {
        val clock = ManualSimClock(1000L)
        assertEquals(1000L, clock.nowMs())

        clock.advance(500L)
        assertEquals(1500L, clock.nowMs())

        clock.set(100_000L)
        assertEquals(100_000L, clock.nowMs())
    }
}
