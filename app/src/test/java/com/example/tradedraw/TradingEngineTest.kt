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
        // Termómetro con timing sniper >= 80% dispara señal si no hay patrones previos
        val analysisCallHigh = VisionAnalysisResult(signalPowerCall = 80, isSniperTimingWindow = true)
        assertEquals(TradeAction.BUY, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisCallHigh))

        val analysisPutHigh = VisionAnalysisResult(signalPowerPut = 85, isSniperTimingWindow = true)
        assertEquals(TradeAction.SELL, TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisPutHigh))

        val analysisBelow80 = VisionAnalysisResult(signalPowerCall = 75, isSniperTimingWindow = true)
        assertNull("75% debe ser filtrado ahora que el umbral A+ es 80%", TradingEngine.evaluateStrategySignal(AutoTradeStrategy.MT_MASTER_COMBO, analysisBelow80))

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

        // 3. Vela envolvente en soporte -> CALL (requiere ventana sniper)
        val envolventeCall = VisionAnalysisResult(isEngulfingCall = true, isSniperTimingWindow = true)
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

    @Test
    fun testLateTimingUniversalVeto() {
        // Vela en el segundo 25 (isLateTimingForbidden = true): debe ser rechazada para evitar entradas perdedoras
        val lateAnalysis = VisionAnalysisResult(
            candleSecond = 25,
            isLateTimingForbidden = true,
            isRejectionCall = true,
            isPullbackSniperCall = true
        )
        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, lateAnalysis)
        assertNull("Entrada tardía debe bloquearse", action)
        assertTrue("Razón debe advertir entrada tardía", reason.contains("Entrada tardía"))

        // Salvo falso rompimiento institucional
        val lateTrapAnalysis = VisionAnalysisResult(
            candleSecond = 25,
            isLateTimingForbidden = true,
            isFalseBreakoutCall = true
        )
        val (actionTrap, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, lateTrapAnalysis)
        assertEquals("Trampa institucional permite entrada", TradeAction.BUY, actionTrap)
    }

    @Test
    fun testMarketSidewaysUniversalVeto() {
        val sidewaysAnalysis = VisionAnalysisResult(
            isMarketSideways = true,
            isRejectionCall = true,
            isPullbackSniperCall = true
        )
        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, sidewaysAnalysis)
        assertNull("Mercado lateral debe vetar trade", action)
        assertTrue("Debe informar mercado lateral / dojis", reason.contains("Mercado Lateral"))
    }

    @Test
    fun testProhibitedZoneBlocksSellAtSupportAndBuyAtResistance() {
        // Venta (SELL) cuando el precio toca directamente el soporte debe ser bloqueada
        val sellAtSupport = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            isChoquePut = true,
            touchesSupport = true
        )
        val (actionSell, reasonSell) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, sellAtSupport)
        assertNull("Venta sobre soporte debe ser bloqueada para evitar rebotes", actionSell)
        assertTrue("Razón debe advertir veto sobre soporte", reasonSell.contains("Prohibido vender sobre Soporte"))

        // Compra (BUY) cuando el precio toca directamente la resistencia debe ser bloqueada
        val buyAtResistance = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            isChoqueCall = true,
            touchesResistance = true
        )
        val (actionBuy, reasonBuy) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, buyAtResistance)
        assertNull("Compra bajo resistencia debe ser bloqueada para evitar rebotes", actionBuy)
        assertTrue("Razón debe advertir veto sobre resistencia", reasonBuy.contains("Prohibido comprar sobre Resistencia"))
    }

    @Test
    fun testAntiSuicideSRProximityFilter_vetoByDistanceRatio() {
        // Canal S/R: Resistencia Y=100, Soporte Y=500. Altura = 400px.
        // Umbral del 15% del canal = 60px.

        // 1. Señal PUT pero precio actual está a 20px del soporte (Y=480) -> ratio = 20/400 = 0.05 (< 0.15)
        val putNearSupport = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            isChoquePut = true,
            currentPriceY = 480f,
            dynamicResistanceY = 100f,
            dynamicSupportY = 500f
        )
        val (actionPut, reasonPut) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, putNearSupport)
        assertNull("PUT cerca de soporte debe ser vetado", actionPut)
        assertEquals("⚠️ Veto: Prohibido vender sobre Soporte (Riesgo de Rebote)", reasonPut)

        // 2. Señal CALL pero precio actual está a 20px de la resistencia (Y=120) -> ratio = 20/400 = 0.05 (< 0.15)
        val callNearResistance = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            isChoqueCall = true,
            currentPriceY = 120f,
            dynamicResistanceY = 100f,
            dynamicSupportY = 500f
        )
        val (actionCall, reasonCall) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.AUTO_ADAPTIVE, callNearResistance)
        assertNull("CALL cerca de resistencia debe ser vetado", actionCall)
        assertEquals("⚠️ Veto: Prohibido comprar sobre Resistencia (Riesgo de Rechazo)", reasonCall)
    }

    @Test
    fun testStreakAntiOverextensionFilter() {
        // Racha de 4 velas verdes (4V): Prohibir continuación alcista (BUY)
        val greenStreak4 = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            consecutiveCount = 4,
            lastCandles = listOf(CandleType.GREEN, CandleType.GREEN, CandleType.GREEN, CandleType.GREEN),
            streakBadge = "4V 🟢"
        )
        val (actionBuy, reasonBuy) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.COLOR_TREND, greenStreak4)
        assertNull("Continuación alcista tras 4 velas verdes debe ser vetada", actionBuy)
        assertEquals("⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso", reasonBuy)

        // Racha de 4 velas rojas (4R): Prohibir continuación bajista (SELL)
        val redStreak4 = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            consecutiveCount = 4,
            lastCandles = listOf(CandleType.RED, CandleType.RED, CandleType.RED, CandleType.RED),
            streakBadge = "4R 🔴"
        )
        val (actionSell, reasonSell) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.COLOR_TREND, redStreak4)
        assertNull("Continuación bajista tras 4 velas rojas debe ser vetada", actionSell)
        assertEquals("⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso", reasonSell)

        // En cambio, reversión por agotamiento (MT_3_VELAS_AGOTAMIENTO) ante racha roja genera BUY, NO es continuación
        val exhaustionCall = VisionAnalysisResult(
            consecutiveCount = 4,
            lastCandles = listOf(CandleType.RED, CandleType.RED, CandleType.RED, CandleType.RED),
            streakBadge = "4R 🔴",
            is3VelasCall = true
        )
        val (actionRev, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO, exhaustionCall)
        assertEquals("Reversión contra racha no debe ser vetada", TradeAction.BUY, actionRev)
    }

    @Test
    fun testRequirePullbackInTrendContinuation() {
        // Continuación alcista (Trend Following) pero precio en extremo superior del gráfico (sin retroceso)
        val upNearTop = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            lastCandles = listOf(CandleType.GREEN),
            isPriceNearTop = true
        )
        val (actionUp, reasonUp) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.TREND_FOLLOWING, upNearTop)
        assertNull("Continuación sin pullback en extremo superior debe ser vetada", actionUp)
        assertTrue("Razón debe advertir falta de retroceso", reasonUp.contains("sin retroceso"))

        // Continuación bajista (Trend Following) pero precio en extremo inferior del gráfico (sin retroceso)
        val downNearBottom = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            lastCandles = listOf(CandleType.RED),
            isPriceNearBottom = true
        )
        val (actionDown, reasonDown) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.TREND_FOLLOWING, downNearBottom)
        assertNull("Continuación sin pullback en extremo inferior debe ser vetada", actionDown)
        assertTrue("Razón debe advertir falta de retroceso", reasonDown.contains("sin retroceso"))
    }

    @Test
    fun testRejectionWick45PercentThreshold() {
        // Mecha del 40% (inferior al nuevo umbral estricto del 45%): NO debe disparar rechazo
        val analysis40 = VisionAnalysisResult(
            hasBottomRejectionWick = false,
            isRejectionCall = false,
            isPullbackSniperCall = true
        )
        val (act40, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.MT_REJECTION, analysis40)
        assertNull("Mecha sin alcanzar umbral 45% debe ser ignorada", act40)

        // Mecha del 45%+ confirmada en soporte
        val analysis45 = VisionAnalysisResult(
            hasBottomRejectionWick = true,
            isRejectionCall = true,
            touchesSupport = true
        )
        val (act45, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.MT_REJECTION, analysis45)
        assertEquals("Mecha >=45% en Soporte debe disparar CALL de reversión", TradeAction.BUY, act45)
    }

    @Test
    fun test3CandleExhaustionDecayingBodiesPattern() {
        // Agotamiento 3 velas rojas decrecientes c3 < c2 < c1 en soporte
        val exhaustCall = VisionAnalysisResult(
            is3VelasCall = true,
            isExhaustion3CandlesCall = true,
            touchesSupport = true
        )
        val (actCall, _) = TradingEngine.evaluateStrategySignalWithReason(AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO, exhaustCall)
        assertEquals("Agotamiento en soporte debe disparar CALL en la 4ta vela", TradeAction.BUY, actCall)

        // Intentar continuacion bajista (SELL) ante agotamiento 3 velas rojas debe ser vetado
        val (vetoSell, reasonVeto) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.TREND_FOLLOWING,
            VisionAnalysisResult(
                trend = TrendDirection.DOWNTREND,
                lastCandles = listOf(CandleType.RED),
                is3VelasCall = true
            )
        )
        assertNull("Continuacion bajista ante agotamiento en soporte debe ser vetada", vetoSell)
        assertTrue("Razon debe advertir veto por agotamiento", reasonVeto.contains("Agotamiento 3 Velas Rojas"))
    }

    @Test
    fun testQuantitativeOverextensionBlocksContinuation() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L // minuto 10 exacto

        // Simular 8 ticks alcistas consecutivos hacia resistencia
        var p = 100.0
        for (i in 0..15) {
            p += 0.5
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (i * 1000L)))
        }

        assertTrue("Debe detectar ticks alcistas consecutivos >= 5", engine.consecutiveUpTicks >= 5)
        assertTrue("RSI sintético debe ser sobrecomprado (> 70)", engine.syntheticTickRsi >= 70.0)

        // Señal de continuación alcista intentando comprar en el techo
        val trendUpResult = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            lastCandles = listOf(CandleType.GREEN),
            distanceToResistanceRatio = 0.10f
        )

        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.TREND_FOLLOWING,
            trendUpResult,
            syntheticEngine = engine
        )

        assertNull("La continuación alcista sobreextendida debe ser vetada", action)
        assertTrue("Razón debe advertir sobreextensión", reason.contains("sin retroceso") || reason.contains("sobreextendida"))
    }

    @Test
    fun testQuantitativeOverextensionReversalAtResistanceAndSupport() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L

        // Alimentar ticks alcistas consecutivos hasta sobrecompra
        var p = 100.0
        for (i in 0..15) {
            p += 0.5
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (i * 1000L)))
        }

        val resAnalysis = VisionAnalysisResult(
            isNearResistanceZone = true,
            distanceToResistanceRatio = 0.08f,
            consecutiveCount = 3,
            lastCandles = listOf(CandleType.GREEN),
            tickVelocityNormalized = -0.02f, // Micro-desaceleración / giro bajista
            candleSecond = 59
        )

        val (actionPut, reasonPut) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.AUTO_ADAPTIVE,
            resAnalysis,
            syntheticEngine = engine
        )

        assertEquals("Debe gatillar reversión PUT contra sobreextensión en Resistencia", TradeAction.SELL, actionPut)
        assertTrue("Razón debe describir reversión por sobreextensión", reasonPut.contains("Reversión por Sobreextensión en Resistencia"))
    }

    @Test
    fun testStrictTimingWindowAndVetoFilters() {
        // 1. Probar ventana estricta :58 a :03
        assertTrue("Segundo 58 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(58))
        assertTrue("Segundo 59 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(59))
        assertTrue("Segundo 00 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(0))
        assertTrue("Segundo 01 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(1))
        assertTrue("Segundo 02 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(2))
        assertTrue("Segundo 03 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(3))
        assertFalse("Segundo 04 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(4))
        assertFalse("Segundo 30 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(30))
        assertFalse("Segundo 57 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(57))

        // 2. Probar veto estricto :15 a :55
        assertTrue("Segundo 15 debe estar vetado", MarketTickFilters.isTimingVetoed(15))
        assertTrue("Segundo 30 debe estar vetado", MarketTickFilters.isTimingVetoed(30))
        assertTrue("Segundo 55 debe estar vetado", MarketTickFilters.isTimingVetoed(55))
        assertFalse("Segundo 00 no debe estar vetado", MarketTickFilters.isTimingVetoed(0))
        assertFalse("Segundo 59 no debe estar vetado", MarketTickFilters.isTimingVetoed(59))
        assertFalse("Segundo 02 no debe estar vetado", MarketTickFilters.isTimingVetoed(2))

        // 3. Probar evaluación en TradingEngine con segundo vetado (:30s)
        val vetoAnalysis = VisionAnalysisResult(
            candleSecond = 30,
            isRejectionCall = true,
            touchesSupport = true
        )
        val (actionVeto, reasonVeto) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.AUTO_ADAPTIVE,
            vetoAnalysis
        )
        assertNull("Operación en segundo :30 debe ser vetada", actionVeto)
        assertTrue("Razón debe indicar veto de timing :15-:55", reasonVeto.contains("Veto Timing Estricto") || reasonVeto.contains("Entrada tardía"))

        // 4. Probar evaluación en TradingEngine con segundo permitido (:59s)
        val validAnalysis = VisionAnalysisResult(
            candleSecond = 59,
            isRejectionCall = true,
            touchesSupport = true
        )
        val (actionValid, _) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.AUTO_ADAPTIVE,
            validAnalysis
        )
        assertEquals("Operación en segundo :59 debe ser permitida", TradeAction.BUY, actionValid)
    }

    @Test
    fun testAntiChoppyFilterSuppressesSignalsInMicroRangeWithAlternatingTicks() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L

        // Crear 5 velas en micro-rango < 0.05% (precios entre 1000.0 y 1000.30 -> rango 0.03%)
        for (m in 0 until 5) {
            val baseTime = (now - (5 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 1000.10, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 1000.30, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 1000.00, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 1000.15, timestampMs = baseTime + 59000L))
        }

        // Enviar ticks alternantes (sube, baja, sube, baja)
        val alternatingPrices = listOf(1000.10, 1000.20, 1000.10, 1000.22, 1000.12, 1000.20, 1000.11, 1000.18)
        for ((idx, p) in alternatingPrices.withIndex()) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (idx * 1000L)))
        }

        assertTrue("Debe detectar micro-rango < 0.05%", engine.isMicroRange(5, 0.05))
        assertTrue("Debe detectar alternancia de ticks sin dirección clara", engine.isTickAlternatingWithoutDirection(8))
        assertTrue("Debe activar flag de choppiness", engine.isChoppinessDetected())

        // Evaluar señal en TradingEngine ante mercado choppy: debe ser suprimida
        val analysis = VisionAnalysisResult(
            candleSecond = 0,
            isRejectionCall = true,
            touchesSupport = true
        )
        val (action, reason) = TradingEngine.evaluateStrategySignalWithReason(
            AutoTradeStrategy.AUTO_ADAPTIVE,
            analysis,
            syntheticEngine = engine
        )

        assertNull("Señal debe ser suprimida por el Filtro Anti-Choppy", action)
        assertTrue("Razón debe advertir veto por micro-rango / choppiness", reason.contains("Anti-Choppy"))
    }
}

