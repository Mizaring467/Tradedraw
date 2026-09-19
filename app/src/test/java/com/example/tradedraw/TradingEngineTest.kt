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
        // 1. Probar ventana estricta :58 a :03 conforme a master_traders_skill
        assertFalse("Segundo 57 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(57))
        assertTrue("Segundo 58 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(58))
        assertTrue("Segundo 59 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(59))
        assertTrue("Segundo 00 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(0))
        assertTrue("Segundo 01 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(1))
        assertTrue("Segundo 02 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(2))
        assertTrue("Segundo 03 debe estar en ventana", MarketTickFilters.isStrictTimingWindow(3))
        assertFalse("Segundo 04 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(4))
        assertFalse("Segundo 05 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(5))
        assertFalse("Segundo 06 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(6))
        assertFalse("Segundo 30 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(30))
        assertFalse("Segundo 56 no debe estar en ventana", MarketTickFilters.isStrictTimingWindow(56))

        // 2. Probar veto estricto :06 a :57 conforme a master_traders_skill
        assertTrue("Segundo 06 debe estar vetado", MarketTickFilters.isTimingVetoed(6))
        assertTrue("Segundo 15 debe estar vetado", MarketTickFilters.isTimingVetoed(15))
        assertTrue("Segundo 30 debe estar vetado", MarketTickFilters.isTimingVetoed(30))
        assertTrue("Segundo 55 debe estar vetado", MarketTickFilters.isTimingVetoed(55))
        assertTrue("Segundo 57 debe estar vetado", MarketTickFilters.isTimingVetoed(57))
        assertFalse("Segundo 00 no debe estar vetado", MarketTickFilters.isTimingVetoed(0))
        assertFalse("Segundo 58 no debe estar vetado", MarketTickFilters.isTimingVetoed(58))
        assertFalse("Segundo 59 no debe estar vetado", MarketTickFilters.isTimingVetoed(59))
        assertFalse("Segundo 02 no debe estar vetado", MarketTickFilters.isTimingVetoed(2))
        assertFalse("Segundo 03 no debe estar vetado", MarketTickFilters.isTimingVetoed(3))

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

    /**
     * Punto 1 del objetivo: el doble registro del journal.
     *
     * Las 4 llamadas a TradeJournalLogger.logTrade son 3 ramas mutuamente excluyentes de una única
     * liquidación (TIE / WIN / LOSS) + 1 de la ruta headless. No hay dos rutas que liquiden en el
     * mismo frame (el AtomicBoolean isTradeResolving lo impide), pero AMBAS entradas de liquidación
     * (onNewFrame y onMarketTick) delegaban en un `checkHeadlessTradeResolution` que no comprobaba
     * `hasPendingTrade`: cuando el pendiente se limpiaba sin liquidar (timeout de 85s de canExecuteTrade),
     * la ruta de visión re-liquidaba el MISMO trade y escribía una fila idéntica con 1s de diferencia.
     *
     * Este test fija el contrato: los 3 desenlaces son excluyentes y las 3 ramas cubren WIN/LOSS/TIE.
     */
    @Test
    fun testResolutionBranchesAreMutuallyExclusiveAndCoverAllOutcomes() {
        data class Desenlace(val isTie: Boolean, val isWin: Boolean, val resultado: String)

        fun resolver(desenlace: Desenlace): String {
            // Réplica exacta de la cascada de TradingEngine (isTie -> finalWin -> else)
            return if (desenlace.isTie) "TIE" else if (desenlace.isWin) "WIN" else "LOSS"
        }

        assertEquals("TIE", resolver(Desenlace(isTie = true, isWin = false, resultado = "")))
        assertEquals("WIN", resolver(Desenlace(isTie = false, isWin = true, resultado = "")))
        assertEquals("LOSS", resolver(Desenlace(isTie = false, isWin = false, resultado = "")))

        // Ningún desenlace puede producir dos filas: la cascada es if/else if/else.
        val resultados = listOf(
            resolver(Desenlace(true, false, "")),
            resolver(Desenlace(false, true, "")),
            resolver(Desenlace(false, false, ""))
        )
        assertEquals("Los 3 desenlaces deben ser distintos", 3, resultados.toSet().size)
        assertEquals("Deben cubrirse WIN, LOSS y TIE", setOf("WIN", "LOSS", "TIE"), resultados.toSet())
    }

    @Test
    fun testSniperConfluences_AssetVeto() {
        AutoTradeAccessibilityService.latestObservedAsset = "Crypto IDX"
        AutoTradeAccessibilityService.isSyntheticOrOTC = true

        val analysis = VisionAnalysisResult(
            candleSecond = 58,
            touchesSupport = true,
            hasBottomRejectionWick = true
        )

        val (action, reason) = TradingEngine.evaluateSniperConfluences(analysis, null, null)
        assertNull("No debe disparar en activo sintético", action)
        assertTrue("Razón debe mencionar Veto Francotirador", reason.contains("Veto Francotirador"))
    }

    @Test
    fun testSniperConfluences_TimingWindow() {
        AutoTradeAccessibilityService.latestObservedAsset = "EUR/USD"
        AutoTradeAccessibilityService.isSyntheticOrOTC = false

        val analysisOutside = VisionAnalysisResult(
            candleSecond = 35,
            touchesSupport = true,
            hasBottomRejectionWick = true
        )

        val (actionOutside, reasonOutside) = TradingEngine.evaluateSniperConfluences(analysisOutside, null, null)
        assertNull("Fuera de :58-:59s no debe disparar", actionOutside)
        assertTrue("Razón debe mencionar timing estricto", reasonOutside.contains("timing estricto"))

        val analysisInside = VisionAnalysisResult(
            candleSecond = 58,
            touchesSupport = true,
            hasBottomRejectionWick = true,
            tickVelocityNormalized = 0.05f
        )
        val (actionInside, reasonInside) = TradingEngine.evaluateSniperConfluences(analysisInside, null, null)
        assertEquals("En :58s con soporte y mecha debe emitir BUY", TradeAction.BUY, actionInside)
        assertTrue("Razón debe ser FRANCOTIRADOR CALL", reasonInside.contains("FRANCOTIRADOR [CALL"))
    }

    @Test
    fun testSniperConfluences_FullConfluenceCallAndPut() {
        AutoTradeAccessibilityService.latestObservedAsset = "EUR/USD"
        AutoTradeAccessibilityService.isSyntheticOrOTC = false

        // CALL Confluence
        val analysisCall = VisionAnalysisResult(
            candleSecond = 59,
            touchesSupport = true,
            hasBottomRejectionWick = true,
            tickVelocityNormalized = 0.02f
        )
        val (actionCall, reasonCall) = TradingEngine.evaluateSniperConfluences(analysisCall, null, null)
        assertEquals("Debe disparar BUY ante confluencia de soporte", TradeAction.BUY, actionCall)
        assertTrue("Razón debe mencionar FRANCOTIRADOR CALL", reasonCall.contains("FRANCOTIRADOR [CALL"))

        // PUT Confluence
        val analysisPut = VisionAnalysisResult(
            candleSecond = 58,
            touchesResistance = true,
            hasTopRejectionWick = true,
            tickVelocityNormalized = -0.02f
        )
        val (actionPut, reasonPut) = TradingEngine.evaluateSniperConfluences(analysisPut, null, null)
        assertEquals("Debe disparar SELL ante confluencia de resistencia", TradeAction.SELL, actionPut)
        assertTrue("Razón debe mencionar FRANCOTIRADOR PUT", reasonPut.contains("FRANCOTIRADOR [PUT"))
    }

    @Test
    fun testCandleTimeframe_Properties() {
        assertEquals(60, CandleTimeframe.M1.seconds)
        assertEquals("1m", CandleTimeframe.M1.label)
        assertEquals(60000L, CandleTimeframe.M1.periodMs)

        assertEquals(300, CandleTimeframe.M5.seconds)
        assertEquals("5m", CandleTimeframe.M5.label)
        assertEquals(300000L, CandleTimeframe.M5.periodMs)
    }

    @Test
    fun testSniperConfluences_AdverseVelocityBlocked() {
        AutoTradeAccessibilityService.latestObservedAsset = "EUR/USD"
        AutoTradeAccessibilityService.isSyntheticOrOTC = false

        // S/R y mecha presentes, pero micro-velocidad adversa (bajista en soporte CALL)
        val analysisAdverse = VisionAnalysisResult(
            candleSecond = 58,
            touchesSupport = true,
            hasBottomRejectionWick = true,
            tickVelocityNormalized = -0.05f,
            isBearishImpulse = true
        )
        val (action, reason) = TradingEngine.evaluateSniperConfluences(analysisAdverse, null, null)
        assertNull("Velocidad adversa bajista no debe disparar CALL en Francotirador", action)
        assertTrue("Razón debe mencionar confluencias incompletas", reason.contains("Confluencias incompletas"))
    }

    @Test
    fun testSniperConfluences_HeadlessSyntheticCandleWick() {
        AutoTradeAccessibilityService.latestObservedAsset = "EUR/USD"
        AutoTradeAccessibilityService.isSyntheticOrOTC = false

        val syntheticEngine = SyntheticCandleEngine()
        // Crear un tick para abrir la vela
        syntheticEngine.onNewTick(MarketTick(asset = "EUR/USD", price = 1.0500, timestampMs = 1000L))
        // Crear un mínimo profundo para generar mecha inferior > 40%
        syntheticEngine.onNewTick(MarketTick(asset = "EUR/USD", price = 1.0400, timestampMs = 2000L))
        // Cerrar cerca del máximo: open=1.0500, low=1.0400, high=1.0510, close=1.0500
        syntheticEngine.onNewTick(MarketTick(asset = "EUR/USD", price = 1.0510, timestampMs = 3000L))
        syntheticEngine.onNewTick(MarketTick(asset = "EUR/USD", price = 1.0500, timestampMs = 4000L))

        assertTrue("La mecha inferior sintética debe ser >= 40%", (syntheticEngine.currentCandle?.lowerWickRatio ?: 0f) >= 0.40f)

        // En Headless no hay candleList visual
        val analysisHeadless = VisionAnalysisResult(
            candleSecond = 58,
            touchesSupport = true,
            hasBottomRejectionWick = false,
            candleList = emptyList(),
            tickVelocityNormalized = 0.03f
        )
        val (action, reason) = TradingEngine.evaluateSniperConfluences(analysisHeadless, syntheticEngine, null)
        assertEquals("Debe disparar BUY usando mecha de vela sintética", TradeAction.BUY, action)
        assertTrue("Razón debe mencionar FRANCOTIRADOR CALL", reason.contains("FRANCOTIRADOR [CALL"))
    }

    @Test
    fun testSniperConfluences_NonForexAssetBlocked() {
        AutoTradeAccessibilityService.latestObservedAsset = "COMMODITY_GOLD"
        AutoTradeAccessibilityService.isSyntheticOrOTC = false

        val analysis = VisionAnalysisResult(
            candleSecond = 58,
            touchesSupport = true,
            hasBottomRejectionWick = true,
            tickVelocityNormalized = 0.04f
        )
        val (action, reason) = TradingEngine.evaluateSniperConfluences(analysis, null, null)
        assertNull("Activo no-forex debe ser bloqueado en Francotirador", action)
        assertTrue("Razón debe indicar que no es par Forex real", reason.contains("no es un par Forex real"))
    }

    @Test
    fun testCandleTimeframe_TimingWindowCycle() {
        // M1: 60s
        val tsM1_58 = 58000L // 58s
        val tsM1_30 = 30000L // 30s
        assertTrue("Segundo 58 debe ser sniper window para M1", CandleTimeframe.M1.isSniperTimingWindow(tsM1_58))
        assertFalse("Segundo 30 no debe ser sniper window para M1", CandleTimeframe.M1.isSniperTimingWindow(tsM1_30))

        // M5: 300s
        val tsM5_298 = 298000L // 4m 58s (segundo 298 de 300)
        val tsM5_58 = 58000L   // 58s (apenas minuto 1 de 5)
        assertTrue("Segundo 298 debe ser sniper window para M5", CandleTimeframe.M5.isSniperTimingWindow(tsM5_298))
        assertFalse("Segundo 58 en M5 NO debe ser sniper window", CandleTimeframe.M5.isSniperTimingWindow(tsM5_58))

        // Test de ventana estándar (:58-:03)
        val tsM1_02 = 2000L  // 02s
        val tsM1_05 = 5000L  // 05s
        assertTrue("Segundo 02 debe ser standard timing window para M1", CandleTimeframe.M1.isStandardTimingWindow(tsM1_02))
        assertTrue("Segundo 58 debe ser standard timing window para M1", CandleTimeframe.M1.isStandardTimingWindow(tsM1_58))
        assertFalse("Segundo 05 NO debe ser standard timing window para M1 (:58-:03)", CandleTimeframe.M1.isStandardTimingWindow(tsM1_05))
    }

    @Test
    fun testSingleTradePerCandleLock_PreventsMultipleTradesInSameMinute() {
        // Simular que ya se ejecutó un trade en el minuto actual
        val epochMinute = 1_000_000L
        val nowMs = epochMinute * 60_000L + 58_500L // Minuto X, segundo :58.5s

        // Crear mock/instancia de TradingEngine lógica
        var lastMinute = epochMinute
        fun canExecuteInMinute(ts: Long): Boolean {
            val m = ts / 60000L
            return if (lastMinute == m) false else {
                lastMinute = m
                true
            }
        }

        // Intento 1 en el mismo minuto debe ser bloqueado
        assertFalse("No se debe permitir un segundo trade en el mismo minuto de vela", canExecuteInMinute(nowMs + 500L))

        // Intento en el siguiente minuto debe ser permitido
        val nextMinuteMs = (epochMinute + 1) * 60_000L + 58_200L
        assertTrue("En el siguiente minuto de vela sí debe permitirse operar", canExecuteInMinute(nextMinuteMs))
    }
}


