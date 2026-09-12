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

    @Test
    fun testSelectiveMartingaleM1_requiresAPlusSetup() {
        riskManager.maxMartingaleLevel = 1
        riskManager.martingaleEnabled = true
        riskManager.cooldownSeconds = 0
        riskManager.lossCooldownSeconds = 0
        riskManager.lastTradeTime = 0L

        // En M0 (racha 0): Cualquier señal válida opera normalmente
        assertTrue("M0 con 70% debe permitir operar", riskManager.canTrade(0.70f))
        val (canTradeM0, _) = riskManager.canExecuteTrade(confidence = 0.70f)
        assertTrue("M0 debe permitir trade", canTradeM0)

        // 1ra Pérdida -> Entra en Martingala Nivel 1 (M1)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(1, riskManager.currentLossStreak)
        riskManager.lastTradeTime = 0L // Simular cooldown ya transcurrido

        // En M1 con confianza < 85% (ej. 80%, 84%) -> DEBE BLOQUEARSE (exige setup A+)
        val (canTrade80, reason80) = riskManager.canExecuteTrade(confidence = 0.80f)
        assertFalse("M1 con 80% debe bloquearse por no ser A+", canTrade80)
        assertTrue("Razón debe especificar requisito A+ de M1", reason80.contains("Martingala M1 requiere setup A+"))
        assertFalse("canTrade(0.80f) debe retornar false en M1", riskManager.canTrade(0.80f))
        assertFalse("canTrade(0.84f) debe retornar false en M1", riskManager.canTrade(0.84f))
        assertFalse("canTrade(84f) normalizado debe retornar false en M1", riskManager.canTrade(84f))

        // En M1 con confianza >= 85% (setup A+ / A+ Setup) -> DEBE PERMITIR OPERAR
        val (canTrade85, reason85) = riskManager.canExecuteTrade(confidence = 0.85f)
        assertTrue("M1 con 85% es A+ y debe permitirse", canTrade85)
        assertEquals("Listo para operar", reason85)
        assertTrue("canTrade(0.85f) debe retornar true en M1", riskManager.canTrade(0.85f))
        assertTrue("canTrade(0.95f) debe retornar true en M1", riskManager.canTrade(0.95f))
        assertTrue("canTrade(90f) normalizado debe retornar true en M1", riskManager.canTrade(90f))

        // Si la Martingala está desactivada, el filtro M1 no debe bloquear
        riskManager.martingaleEnabled = false
        val (canTradeDisabled, _) = riskManager.canExecuteTrade(confidence = 0.70f)
        assertTrue("Sin martingala no aplica restricción M1", canTradeDisabled)
    }

    @Test
    fun testSelectiveMartingaleM1_inYoloMode() {
        riskManager.maxMartingaleLevel = 1
        riskManager.martingaleEnabled = true
        riskManager.yoloLossCooldownSeconds = 35

        // Pérdida -> M1
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(1, riskManager.currentLossStreak)
        // Simular que el cooldown de pérdida YOLO ya transcurrió (40s atrás)
        riskManager.lastTradeTime = System.currentTimeMillis() - 40_000L

        // En YOLO con M1 y confianza 75% -> Bloqueado por filtro selectivo M1
        val (canTradeYoloLow, reasonYoloLow) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO, confidence = 0.75f)
        assertFalse("M1 en YOLO con 75% debe ser bloqueado", canTradeYoloLow)
        assertTrue("Razón debe ser requisito A+ de M1", reasonYoloLow.contains("Martingala M1 requiere setup A+"))

        // En YOLO con M1 y confianza 88% -> Permitido operar
        val (canTradeYoloHigh, reasonYoloHigh) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO, confidence = 0.88f)
        assertTrue("M1 en YOLO con 88% debe ser permitido", canTradeYoloHigh)
        assertTrue("Razón debe confirmar modo YOLO", reasonYoloHigh.contains("MODO YOLO"))
    }

    @Test
    fun testYoloAutoResetsLossStreakAfterCooldown() {
        riskManager.maxMartingaleLevel = 1
        riskManager.martingaleEnabled = true
        riskManager.yoloLossCooldownSeconds = 35

        // Trade 1: Pérdida -> M1 (racha = 1)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(1, riskManager.currentLossStreak)

        // Trade 2 (M1): Pérdida -> Fallo Martingala (racha = 2 > maxMartingaleLevel)
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(2, riskManager.currentLossStreak)
        assertEquals(22.0f, riskManager.getCurrentInvestmentAmount(), 0.01f) // Capped at M1 multiplier (10.0 * 2.2)

        // 1. Durante la pausa anti-tilt (15s transcurridos < 35s):
        riskManager.lastTradeTime = System.currentTimeMillis() - 15_000L
        val (canTradeDuring, reasonDuring) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO)
        assertFalse("Debe bloquearse durante la pausa anti-tilt en YOLO", canTradeDuring)
        assertTrue("Razón debe indicar Pausa Anti-Tilt", reasonDuring.contains("Pausa Anti-Tilt"))
        assertEquals("La racha se mantiene en 2 durante la pausa", 2, riskManager.currentLossStreak)

        // 2. Al expirar la pausa anti-tilt (40s transcurridos > 35s):
        riskManager.lastTradeTime = System.currentTimeMillis() - 40_000L
        val (canTradeAfter, reasonAfter) = riskManager.canExecuteTrade(subMode = AutonomousSubMode.YOLO)
        assertTrue("Debe permitir operar tras finalizar el cooldown anti-tilt en YOLO", canTradeAfter)
        assertTrue("Razón debe indicar modo YOLO continuo", reasonAfter.contains("MODO YOLO"))
        assertEquals("En YOLO la racha debe reiniciarse automáticamente a 0 (M0)", 0, riskManager.currentLossStreak)
        assertEquals("El monto de inversión debe reiniciarse al monto base ($10.0)", 10.0f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("El badge de martingala debe volver a M0", "[M0 | $10.0]", riskManager.getMartingaleStatusBadge())
    }
}
