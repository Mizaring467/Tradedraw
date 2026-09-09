package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class TradingEngineTest {

    @Test
    fun testMasterComboPrioritization_falseBreakoutOverAll() {
        // Cuando coexisten un patrón débil (3 velas) y una trampa institucional (Falso Rompimiento),
        // MT_MASTER_COMBO debe priorizar Falso Rompimiento.
        val analysisCall = VisionAnalysisResult(
            isFalseBreakoutCall = true,
            isRejectionPut = true,
            isEngulfingPut = true,
            isChoquePut = true,
            is3VelasPut = true,
            signalPowerPut = 85
        )

        val actionCall = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisCall
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Falso Rompimiento CALL", TradeAction.BUY, actionCall)

        val analysisPut = VisionAnalysisResult(
            isFalseBreakoutPut = true,
            isRejectionCall = true,
            isEngulfingCall = true,
            isChoqueCall = true,
            is3VelasCall = true,
            signalPowerCall = 85
        )

        val actionPut = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisPut
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Falso Rompimiento PUT", TradeAction.SELL, actionPut)
    }

    @Test
    fun testMasterComboPrioritization_rejectionOverEngulfingAndChoque() {
        val analysisCall = VisionAnalysisResult(
            isRejectionCall = true,
            isEngulfingPut = true,
            isChoquePut = true,
            is3VelasPut = true
        )

        val actionCall = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisCall
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Mecha de Rechazo CALL sobre patrones inferiores", TradeAction.BUY, actionCall)

        val analysisPut = VisionAnalysisResult(
            isRejectionPut = true,
            isEngulfingCall = true,
            isChoqueCall = true,
            is3VelasCall = true
        )

        val actionPut = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisPut
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Mecha de Rechazo PUT sobre patrones inferiores", TradeAction.SELL, actionPut)
    }

    @Test
    fun testMasterComboPrioritization_engulfingOverChoqueAnd3Velas() {
        val analysisCall = VisionAnalysisResult(
            isEngulfingCall = true,
            isChoquePut = true,
            is3VelasPut = true
        )

        val action = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisCall
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Vela Envolvente CALL sobre Choque/3Velas", TradeAction.BUY, action)
    }

    @Test
    fun testMasterComboPrioritization_choqueOver3Velas() {
        val analysisCall = VisionAnalysisResult(
            isChoqueCall = true,
            is3VelasPut = true
        )

        val action = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisCall
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Choque CALL sobre 3 Velas", TradeAction.BUY, action)
    }

    @Test
    fun testMasterComboPrioritization_3velasOverSignalThermometer() {
        val analysisCall = VisionAnalysisResult(
            is3VelasCall = true,
            signalPowerPut = 80
        )

        val action = TradingEngine.evaluateStrategySignal(
            AutoTradeStrategy.MT_MASTER_COMBO,
            analysisCall
        )
        assertEquals("MT_MASTER_COMBO debe priorizar Agotamiento 3 Velas CALL sobre termómetro", TradeAction.BUY, action)
    }

    @Test
    fun testMasterComboSignalThermometerThreshold() {
        // Termómetro con timing sniper >= 75% dispara señal si no hay patrones previos
        val analysisCallHigh = VisionAnalysisResult(signalPowerCall = 75, isSniperTimingWindow = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisCallHigh))

        val analysisPutHigh = VisionAnalysisResult(signalPowerPut = 80, isSniperTimingWindow = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisPutHigh))

        val analysisWeak = VisionAnalysisResult(signalPowerCall = 65, signalPowerPut = 60)
        assertNull(TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisWeak))
    }

    @Test
    fun testIndividualStrategiesDirectEvaluation() {
        // MT_FALSE_BREAKOUT
        val fbCall = VisionAnalysisResult(isFalseBreakoutCall = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_FALSE_BREAKOUT, fbCall))
        val fbPut = VisionAnalysisResult(isFalseBreakoutPut = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_FALSE_BREAKOUT, fbPut))

        // MT_ENGULFING_SR
        val engCall = VisionAnalysisResult(isEngulfingCall = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_ENGULFING_SR, engCall))
        val engPut = VisionAnalysisResult(isEngulfingPut = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_ENGULFING_SR, engPut))

        // MT_REJECTION
        val rejCall = VisionAnalysisResult(isRejectionCall = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_REJECTION, rejCall))
        val rejPut = VisionAnalysisResult(isRejectionPut = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_REJECTION, rejPut))

        // MT_CHOQUE_PULLBACK
        val choCall = VisionAnalysisResult(isChoqueCall = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_CHOQUE_PULLBACK, choCall))
        val choPut = VisionAnalysisResult(isChoquePut = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_CHOQUE_PULLBACK, choPut))

        // MT_3_VELAS_AGOTAMIENTO
        val v3Call = VisionAnalysisResult(is3VelasCall = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO, v3Call))
        val v3Put = VisionAnalysisResult(is3VelasPut = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO, v3Put))

        // COLOR_TREND
        val trendUp = VisionAnalysisResult(trend = TrendDirection.UPTREND, lastCandles = listOf(CandleType.GREEN))
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.COLOR_TREND, trendUp))
        val trendDown = VisionAnalysisResult(trend = TrendDirection.DOWNTREND, lastCandles = listOf(CandleType.RED))
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.COLOR_TREND, trendDown))
    }

    @Test
    fun testSidewaysMarketFilterBlocksAllTrades() {
        // En mercado lateral / con Dojis, todas las señales deben ser bloqueadas
        val sidewaysAnalysis = VisionAnalysisResult(
            isMarketSideways = true,
            isFalseBreakoutCall = true,
            isRejectionCall = true,
            isEngulfingCall = true,
            isChoqueCall = true,
            is3VelasCall = true,
            signalPowerCall = 95
        )

        AutoTradeStrategy.values().forEach { strat ->
            val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(strat, sidewaysAnalysis)
            assertNull("Estrategia $strat debe retornar null en mercado lateral", action)
            assertEquals("⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad", reason)
        }
    }

    @Test
    fun testSignalReasonFormatting() {
        val fbCall = VisionAnalysisResult(isFalseBreakoutCall = true)
        val (_, reason) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.MT_MASTER_COMBO, fbCall)
        assertTrue("La razón debe describir el patrón", reason.contains("Trampa / Falso Rompimiento de Soporte"))
    }

    @Test
    fun testAutoAdaptiveMultiStrategySelection() {
        // 1. Trampa institucional -> Prioridad máxima
        val trampaCall = VisionAnalysisResult(isFalseBreakoutCall = true)
        val (action1, reason1) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, trampaCall)
        assertEquals(TradeAction.BUY, action1)
        assertTrue(reason1.contains("Auto [Trampa en Soporte"))

        // 2. Mecha de rechazo en resistencia -> PUT
        val mechaPut = VisionAnalysisResult(isRejectionPut = true, isPullbackSniperPut = true)
        val (action2, reason2) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, mechaPut)
        assertEquals(TradeAction.SELL, action2)
        assertTrue(reason2.contains("Auto [Mecha Rechazo Resistencia"))

        // 3. Vela envolvente en soporte -> CALL
        val envolventeCall = VisionAnalysisResult(isEngulfingCall = true)
        val (action3, reason3) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, envolventeCall)
        assertEquals(TradeAction.BUY, action3)
        assertTrue(reason3.contains("Auto [Vela Envolvente Soporte"))

        // 4. Choque / Retest -> PUT
        val choquePut = VisionAnalysisResult(isChoquePut = true)
        val (action4, reason4) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, choquePut)
        assertEquals(TradeAction.SELL, action4)
        assertTrue(reason4.contains("Auto [Choque / Pullback"))

        // 5. Agotamiento 3 velas rojas -> CALL
        val agotamientoCall = VisionAnalysisResult(is3VelasCall = true)
        val (action5, reason5) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, agotamientoCall)
        assertEquals(TradeAction.BUY, action5)
        assertTrue(reason5.contains("Auto [Agotamiento 3 Rojas"))
    }

    @Test
    fun testSniperPullbackAndTimingWindows() {
        // En AUTO_ADAPTIVE, una mecha de rechazo requiere confirmación de Pullback Sniper o Timing Window
        val mechaWithoutPullback = VisionAnalysisResult(
            isRejectionCall = true,
            isPullbackSniperCall = false,
            isSniperTimingWindow = false
        )
        val (actionNoPullback, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, mechaWithoutPullback)
        assertNull("Mecha sin pullback ni timing window debe esperar mejor precio", actionNoPullback)

        val mechaWithPullback = VisionAnalysisResult(
            isRejectionCall = true,
            isPullbackSniperCall = true,
            candleSecond = 58
        )
        val (actionWithPullback, reason) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, mechaWithPullback)
        assertEquals("Mecha con pullback sniper debe gatillar CALL", TradeAction.BUY, actionWithPullback)
        assertTrue("La razón debe incluir el segundo 58s", reason.contains("58s"))
    }

    @Test
    fun testConsolidationTightStrictlyBlocksTrade() {
        val tightAnalysis = VisionAnalysisResult(
            isConsolidationTight = true,
            isFalseBreakoutCall = true,
            signalPowerCall = 90
        )
        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, tightAnalysis)
        assertNull("Debe bloquear operaciones ante consolidación estrecha", action)
        assertEquals("⏳ Rango estrecho / Sin volatilidad: Esperando expansión", reason)
    }
}
