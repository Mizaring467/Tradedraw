package com.example.tradedraw

import com.example.tradedraw.core.BalanceReading
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * T-1.2: Validación del puerto [BalanceSource] y verificación de cero divergencias en dual-read.
 */
class BalanceSourceDualReadTest {

    @Before
    fun setUp() {
        AccessibilityBalanceSource.resetDivergenceCount()
        AutoTradeAccessibilityService.latestObservedBalance = 20_000_000.0
        AutoTradeAccessibilityService.lastBalanceReadMs = System.currentTimeMillis()
    }

    @Test
    fun lecturaCoincidenteConStaticProduceCeroDivergencias() {
        val source = AccessibilityBalanceSource()
        val reading = source.read()

        assertEquals(20_000_000.0, reading.balance, 0.001)
        assertEquals(0, AccessibilityBalanceSource.divergenceCount)
    }

    @Test
    fun inyeccionDeBalanceSourceEnRiskManager() {
        val mockReading = BalanceReading(balance = 25_000_000.0, lastReadMs = System.currentTimeMillis(), isAvailable = true)
        val customSource = object : com.example.tradedraw.core.BalanceSource {
            override fun read(): BalanceReading = mockReading
        }

        val rm = RiskManager(balanceSource = customSource)
        rm.resetStreakOnly()

        assertEquals(25_000_000.0, rm.sessionStartBalance, 0.001)
    }

    @Test
    fun divergenciaSimuladaIncrementaContador() {
        val divergentSource = AccessibilityBalanceSource(provider = {
            BalanceReading(balance = 10_000_000.0, lastReadMs = System.currentTimeMillis(), isAvailable = true)
        })

        divergentSource.read()
        assertEquals(1, AccessibilityBalanceSource.divergenceCount)
    }
}
