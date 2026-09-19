package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class MarketRegimeClassifierTest {

    @Test
    fun testClassifyNullAnalysis_returnsPreliminaryOrChoppy() {
        val engine = SyntheticCandleEngine()
        val result = MarketRegimeClassifier.classify(null, engine)
        // Without frames or trend, defaults to CHOPPY_NOISE standby
        assertEquals(MarketRegime.CHOPPY_NOISE, result.regime)
        assertTrue(result.authorizedStrategies.isEmpty())
    }

    @Test
    fun testClassifyChoppyNoise_whenMicroRangeChoppy() {
        val analysis = VisionAnalysisResult(
            isMicroRangeChoppy = true,
            trend = TrendDirection.UPTREND
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar CHOPPY_NOISE ante micro-rango", MarketRegime.CHOPPY_NOISE, result.regime)
        assertTrue("No debe autorizar ninguna estrategia en ruido", result.authorizedStrategies.isEmpty())
    }

    @Test
    fun testClassifyChoppyNoise_whenDojiOrLowVolume() {
        val analysis = VisionAnalysisResult(
            isDojiOrLowVolume = true,
            trend = TrendDirection.UPTREND
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar CHOPPY_NOISE ante dojis", MarketRegime.CHOPPY_NOISE, result.regime)
        assertTrue(result.authorizedStrategies.isEmpty())
    }

    @Test
    fun testClassifyBreakoutRetest_whenValidBreakoutCall() {
        val analysis = VisionAnalysisResult(
            isValidBreakoutCall = true,
            isMicroRangeChoppy = false,
            isDojiOrLowVolume = false
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar BREAKOUT_RETEST ante ruptura confirmada", MarketRegime.BREAKOUT_RETEST, result.regime)
        assertTrue(result.authorizedStrategies.contains("MT_CHOQUE_PULLBACK"))
    }

    @Test
    fun testClassifyBreakoutRetest_whenChoqueCall() {
        val analysis = VisionAnalysisResult(
            isChoqueCall = true,
            isMicroRangeChoppy = false,
            isDojiOrLowVolume = false
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar BREAKOUT_RETEST ante choque", MarketRegime.BREAKOUT_RETEST, result.regime)
    }

    @Test
    fun testClassifyStrongTrendUp_whenCleanUptrend() {
        val analysis = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            signalPowerCall = 75,
            hasStrongMomentumDown = false,
            isMicroRangeChoppy = false,
            isDojiOrLowVolume = false
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar STRONG_TREND_UP ante tendencia alcista limpia", MarketRegime.STRONG_TREND_UP, result.regime)
        assertTrue(result.authorizedStrategies.contains("MT_PULLBACK_SNIPER"))
    }

    @Test
    fun testClassifyStrongTrendDown_whenCleanDowntrend() {
        val analysis = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            signalPowerPut = 70,
            hasStrongMomentumUp = false,
            isMicroRangeChoppy = false,
            isDojiOrLowVolume = false
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar STRONG_TREND_DOWN ante tendencia bajista limpia", MarketRegime.STRONG_TREND_DOWN, result.regime)
        assertTrue(result.authorizedStrategies.contains("MT_PULLBACK_SNIPER"))
    }

    @Test
    fun testClassifyRangingChannel_whenSRLevelsAreClear() {
        val analysis = VisionAnalysisResult(
            dynamicSupportY = 500f,
            dynamicResistanceY = 400f, // Altura 100px
            touchesSupport = true,
            isMicroRangeChoppy = false,
            isDojiOrLowVolume = false,
            trend = TrendDirection.SIDEWAYS
        )
        val result = MarketRegimeClassifier.classify(analysis)
        assertEquals("Debe clasificar RANGING_CHANNEL cuando hay canal S/R claro y toque", MarketRegime.RANGING_CHANNEL, result.regime)
        assertTrue(result.authorizedStrategies.contains("MT_REJECTION"))
    }
}
