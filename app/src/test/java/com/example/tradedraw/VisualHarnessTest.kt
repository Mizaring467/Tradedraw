package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Harness de pruebas instantáneo en JVM pura (< 100 ms).
 * Permite validar escenarios complejos de mercado de opciones binarias
 * (Rechazo en S/R, Falsos Rompimientos, Agotamiento de 3 Velas, Filtro de Dojis/Sideways,
 * y Gestión de Riesgo / Martingala) sin requerir emulador ni dispositivo físico.
 */
class VisualHarnessTest {

    private lateinit var analyzer: VisionAnalyzer
    private lateinit var riskManager: RiskManager

    @Before
    fun setUp() {
        analyzer = VisionAnalyzer()
        riskManager = RiskManager(null)
    }

    @Test
    fun benchmark_instantCandleAnalysisUnder50ms() {
        val startTime = System.nanoTime()

        // Simular 50 velas consecutivas de un gráfico real de Binomo
        val candles = mutableListOf<CandleData>()
        for (i in 0 until 50) {
            val type = if (i % 2 == 0) CandleType.GREEN else CandleType.RED
            candles.add(
                analyzer.createCandle(
                    type = type,
                    x = 800f - (i * 15f),
                    topY = 400f + (i * 2f),
                    bottomY = 600f + (i * 2f),
                    bodyTopY = 450f + (i * 2f),
                    bodyBottomY = 550f + (i * 2f)
                )
            )
        }

        val result = analyzer.evaluateCandlePatterns(candles)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        assertNotNull(result)
        assertEquals(50, result.candleList.size)
        assertTrue("El análisis en JVM debe ejecutarse en menos de 50 ms (tomó ${elapsedMs}ms)", elapsedMs < 50.0)
    }

    @Test
    fun testScenario_rejectionAtSupport_triggersInstantBuy() {
        // Vela con 45% de mecha inferior rebotando en soporte
        val candles = listOf(
            analyzer.createCandle(
                type = CandleType.GREEN,
                x = 800f,
                topY = 500f,
                bottomY = 700f,
                bodyTopY = 500f,
                bodyBottomY = 590f // Mecha inferior: 700 - 590 = 110px (55% de 200px)
            ),
            analyzer.createCandle(
                type = CandleType.RED,
                x = 780f,
                topY = 480f,
                bottomY = 680f,
                bodyTopY = 500f,
                bodyBottomY = 660f
            )
        )

        val analysis = analyzer.evaluateCandlePatterns(
            candleList = candles,
            supportLinesY = listOf(700f)
        )

        assertTrue(analysis.hasBottomRejectionWick)
        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysis
        )

        assertEquals(TradeAction.BUY, action)
        assertTrue(reason.contains("Mecha de Rechazo"))
    }

    @Test
    fun testScenario_sidewaysMarket_strictlyBlocksTrades() {
        // Velas doji / minúsculas en rango lateral
        val candles = List(10) { i ->
            analyzer.createCandle(
                type = CandleType.DOJI,
                x = 800f - (i * 15f),
                topY = 500f,
                bottomY = 520f,
                bodyTopY = 508f,
                bodyBottomY = 512f // Cuerpo de 4px (< 15px)
            )
        }

        val analysis = analyzer.evaluateCandlePatterns(candles)
        assertTrue("Debe clasificar el mercado como lateral", analysis.isMarketSideways)

        val (action, _) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysis
        )
        assertEquals("Ninguna orden debe ejecutarse en mercado lateral", null, action)
    }

    @Test
    fun testScenario_martingaleRiskSessionProgression() {
        riskManager.martingaleEnabled = true
        riskManager.baseAmount = 10f
        riskManager.martingaleMultiplier = 2.0f
        riskManager.maxMartingaleLevel = 2
        riskManager.stopLossStreak = 3

        assertEquals(10f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[M0 | $10.0]", riskManager.getMartingaleStatusBadge())

        // 1ª Pérdida
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(20f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[M1 | $20.0]", riskManager.getMartingaleStatusBadge())

        // 2ª Pérdida
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals(40f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[M2 | $40.0]", riskManager.getMartingaleStatusBadge())

        // Victoria -> Resetea a M0
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeWin()
        assertEquals(10f, riskManager.getCurrentInvestmentAmount(), 0.01f)
        assertEquals("[M0 | $10.0]", riskManager.getMartingaleStatusBadge())
        assertEquals(1, riskManager.totalWins)
        assertEquals(2, riskManager.totalLosses)
    }
}
