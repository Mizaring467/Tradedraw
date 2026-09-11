package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiskManagerTest {

    private lateinit var riskManager: RiskManager

    @Before
    fun setUp() {
        riskManager = RiskManager(null)
        riskManager.baseAmount = 10.0f
        riskManager.martingaleMultiplier = 2.2f
        riskManager.martingaleEnabled = true
        riskManager.stopLossStreak = 3
        riskManager.takeProfitWins = 5
        riskManager.cooldownSeconds = 10
        riskManager.resetSession()
    }

    @Test
    fun testInitialState() {
        assertEquals("Total wins should be 0", 0, riskManager.totalWins)
        assertEquals("Total losses should be 0", 0, riskManager.totalLosses)
        assertEquals("Current loss streak should be 0", 0, riskManager.currentLossStreak)
        assertFalse("Should not have pending trade", riskManager.hasPendingTrade)
        assertTrue("Should be able to execute trade initially", riskManager.canTrade())
        assertEquals("Initial investment should be baseAmount", 10.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
    }

    @Test
    fun testTradePendingBlocksDuplicateExecution() {
        riskManager.recordTradeSent(TradeAction.BUY, entryPriceY = 250f, baseBalance = 50000.0)
        assertTrue("Trade pending should be true", riskManager.hasPendingTrade)
        assertEquals("Action should be BUY", TradeAction.BUY, riskManager.pendingTradeAction)
        assertEquals("Entry price Y should match", 250f, riskManager.pendingTradeEntryPriceY, 0.01f)
        assertEquals("Base balance should match", 50000.0, riskManager.pendingTradeBaseBalance, 0.01)

        val (canTrade, reason) = riskManager.canExecuteTrade()
        assertFalse("Cannot execute trade while pending trade is active", canTrade)
        assertTrue("Reason should mention open operation", reason.contains("Operación abierta"))
    }

    @Test
    fun testMartingaleProgressionAndWinReset() {
        riskManager.maxMartingaleLevel = 2
        assertEquals("Initial Martingale status should be M0", "[M0 | $10.0]", riskManager.getMartingaleStatusBadge())

        // 1st Loss -> M1
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals("Loss streak should be 1", 1, riskManager.currentLossStreak)
        assertEquals("Total losses should be 1", 1, riskManager.totalLosses)
        assertFalse("Pending trade should be cleared on loss", riskManager.hasPendingTrade)
        assertEquals("M1 amount should be 10 * 2.2 = 22.0", 22.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)

        // 2nd Loss -> M2
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals("Loss streak should be 2", 2, riskManager.currentLossStreak)
        assertEquals("Total losses should be 2", 2, riskManager.totalLosses)
        assertEquals("M2 amount should be 22 * 2.2 = 48.4", 48.4f, riskManager.getCurrentInvestmentAmount(), 0.01f)

        // Win -> Reset to M0
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin()
        assertEquals("Loss streak should reset to 0", 0, riskManager.currentLossStreak)
        assertEquals("Current wins should be 1", 1, riskManager.currentWins)
        assertEquals("Total wins should be 1", 1, riskManager.totalWins)
        assertEquals("Amount should reset to baseAmount", 10.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("Martingale status should reset to M0", "[M0 | $10.0]", riskManager.getMartingaleStatusBadge())
    }

    @Test
    fun testMaxMartingaleCappedAtM1ForRealMoneySafety() {
        riskManager.maxMartingaleLevel = 1
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss() // 1 loss -> M1
        assertEquals("1st loss is M1 (10 * 2.2 = 22.0)", 22.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)

        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss() // 2 losses -> Capped at M1 for real money protection
        assertEquals("2nd loss should be capped at M1 (22.0)", 22.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
    }

    @Test
    fun testAntiTiltCooldownAfterMG1Failure() {
        riskManager.maxMartingaleLevel = 1
        riskManager.martingaleEnabled = true
        riskManager.lossCooldownSeconds = 120

        // 1ra derrota -> Nivel 1 permitido
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(1, riskManager.currentLossStreak)

        // 2da derrota -> MG1 falló (> maxMartingaleLevel = 1), debe forzar pausa anti-tilt incluso en modo YOLO
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(2, riskManager.currentLossStreak)

        val (canTradeNormal, reasonNormal) = riskManager.canExecuteTrade()
        assertFalse("Debe bloquearse por pausa anti-tilt tras fallo MG1", canTradeNormal)
        assertTrue("Razón debe mencionar pausa anti-tilt", reasonNormal.contains("Pausa Anti-Tilt"))

        val (canTradeYolo, reasonYolo) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO)
        assertFalse("Incluso en YOLO debe bloquearse tras fallo MG1", canTradeYolo)
        assertTrue("Razón YOLO debe mencionar pausa anti-tilt", reasonYolo.contains("Pausa Anti-Tilt"))
    }

    @Test
    fun testStopLossAndTakeProfitLimits() {
        // Deshabilitamos martingala para testear puramente el stop loss general
        riskManager.martingaleEnabled = false
        riskManager.cooldownSeconds = 0
        riskManager.lossCooldownSeconds = 0

        // Test Stop Loss
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss() // 3 consecutive losses
        assertEquals(3, riskManager.currentLossStreak)

        val (canTradeSL, reasonSL) = riskManager.canExecuteTrade()
        assertFalse("Should be blocked by Stop Loss", canTradeSL)
        assertTrue("Reason should mention Stop Loss", reasonSL.contains("Stop Loss alcanzado"))

        // Reset and Test Take Profit
        riskManager.resetSession()
        repeat(5) {
            riskManager.recordTradeSent(TradeAction.BUY)
            riskManager.recordTradeWin()
        }
        assertEquals(5, riskManager.currentWins)

        val (canTradeTP, reasonTP) = riskManager.canExecuteTrade()
        assertFalse("Should be blocked by Take Profit", canTradeTP)
        assertTrue("Reason should mention Take Profit", reasonTP.contains("Take Profit alcanzado"))
    }

    @Test
    fun testWinRateCalculation() {
        riskManager.setStats(wins = 8, losses = 2)
        assertEquals("Winrate for 8W-2L should be 80%", 80.0f, riskManager.getWinRate(), 0.01f)
    }
    @Test
    fun testSorosCompoundingProgressionAndReset() {
        riskManager.moneyManagementMode = MoneyManagementMode.SOROS_COMPOUNDING
        riskManager.baseAmount = 1.0f
        riskManager.sorosPayoutRate = 0.85f
        riskManager.sorosCycleTarget = 3
        riskManager.resetSession()

        // Paso 0: Monto base $1.0
        assertEquals(1.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[Soros S0/3 | $1.0]", riskManager.getMartingaleStatusBadge())

        // 1er Win -> Paso 1 ($1.85)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin()
        assertEquals(1, riskManager.currentSorosStep)
        assertEquals(1.85f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[Soros S1/3 | $1.85]", riskManager.getMartingaleStatusBadge())

        // 2do Win -> Paso 2 ($3.42)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin()
        assertEquals(2, riskManager.currentSorosStep)
        assertEquals(3.42f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[Soros S2/3 | $3.42]", riskManager.getMartingaleStatusBadge())

        // 3er Win -> Completa Ciclo (Target 3) -> Reset a Paso 0 y 1 ciclo completado
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin()
        assertEquals(0, riskManager.currentSorosStep)
        assertEquals(1, riskManager.completedSorosCycles)
        assertEquals(1.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)

        // Pérdida en Paso 1 -> Reset a Paso 0 (Pérdida máxima limitada a $1)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin() // A Paso 1
        assertEquals(1, riskManager.currentSorosStep)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss() // Pérdida
        assertEquals(0, riskManager.currentSorosStep)
        assertEquals(1.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
    }

    @Test
    fun testYoloLossCooldown_boundedBetween30And45Seconds() {
        assertEquals("Default YOLO cooldown must be 35s", 35, riskManager.yoloLossCooldownSeconds)

        riskManager.yoloLossCooldownSeconds = 10
        assertEquals("YOLO cooldown below 30s must be coerced to 30s", 30, riskManager.yoloLossCooldownSeconds)

        riskManager.yoloLossCooldownSeconds = 100
        assertEquals("YOLO cooldown above 45s must be coerced to 45s", 45, riskManager.yoloLossCooldownSeconds)

        riskManager.yoloLossCooldownSeconds = 35
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()

        val remainingYolo = riskManager.getRemainingCooldown(AutonomousSubMode.YOLO)
        assertTrue("YOLO cooldown must be between 30 and 35 seconds", remainingYolo in 30..35)
        assertTrue("YOLO cooldown must not freeze for 120-180 seconds", remainingYolo <= 45)

        val (canTradeYolo, reasonYolo) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO)
        assertFalse("Debe esperar el cooldown corto en YOLO", canTradeYolo)
        assertTrue("Razón debe ser cooldown YOLO", reasonYolo.contains("Pausa de Cooldown YOLO"))
    }
}
