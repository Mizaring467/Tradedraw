package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R23F (REQ 1 + REQ 3): Validación estricta del umbral de similitud en ADAPTIVE_BLOCK.
 *
 * Hallazgo empírico R23E:
 * - Bucket 98% (sim < 0.99f): WR = 66.67% (+325k COP) -> SOBRE-RESTRICTIVO. Debe permitirse (Allow).
 * - Bucket 99% (sim >= 0.99f): WR = 47.83% (-290k COP) -> PROTECTOR. Debe bloquearse (Block).
 *
 * Este test verifica:
 * 1. La constante ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD es exactamente 0.99f.
 * 2. Señales con similitud al 98.x% devuelven AdaptiveDecision.Allow.
 * 3. Señales con similitud al 99.x% y 100% devuelven AdaptiveDecision.Block.
 * 4. calculateSignatureSimilarity calcula con precisión matemática continua los valores esperados.
 */
class AdaptiveBlockThresholdTest {

    private lateinit var engine: AdaptiveLearningEngine

    @Before
    fun setUp() {
        engine = AdaptiveLearningEngine()
        engine.reset()
    }

    @Test
    fun constanteUmbral_esExactamenteNoventaYNuevePorciento() {
        assertEquals(0.99f, AdaptiveLearningEngine.ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD, 0.0001f)
    }

    @Test
    fun calculoSimilitud_diferenciaConPrecision98y99() {
        val baseLoss = TradeContextSignature(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            distanceToSupportRatio = 0.50f,
            distanceToResistanceRatio = 0.50f,
            isNearSupportZone = false,
            isNearResistanceZone = false,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = true,
            isBearishImpulse = false,
            isDojiOrLowVolume = false,
            isConsolidationTight = false,
            isMarketSideways = false,
            strategyName = "MT_REJECTION",
            candleSecond = 30
        )

        // Firma idéntica -> 100% (1.0f)
        val simIdentica = engine.calculateSignatureSimilarity(baseLoss, baseLoss)
        assertEquals(1.0f, simIdentica, 0.001f)
        assertEquals(100, (simIdentica * 100).toInt())

        // Firma con S/R diferiendo en 0.01 (0.51f vs 0.50f):
        // srSimilarity = ((1 - 0.01) + (1 - 0.01)) / 2 = 0.99
        // score = 0.20 + 0.20 + (0.99 * 0.30) + 0.15 + 0.15 = 0.997f -> 99%
        val sig99 = baseLoss.copy(
            distanceToSupportRatio = 0.51f,
            distanceToResistanceRatio = 0.51f
        )
        val sim99 = engine.calculateSignatureSimilarity(sig99, baseLoss)
        assertTrue("Debe ser >= 0.99f: $sim99", sim99 >= 0.99f)
        assertEquals(99, (sim99 * 100).toInt())

        // Firma con S/R diferiendo en 0.05 (0.55f vs 0.50f):
        // srSimilarity = ((1 - 0.05) + (1 - 0.05)) / 2 = 0.95
        // score = 0.20 + 0.20 + (0.95 * 0.30) + 0.15 + 0.15 = 0.985f -> 98%
        val sig98 = baseLoss.copy(
            distanceToSupportRatio = 0.55f,
            distanceToResistanceRatio = 0.55f
        )
        val sim98 = engine.calculateSignatureSimilarity(sig98, baseLoss)
        assertTrue("Debe ser < 0.99f: $sim98", sim98 < 0.99f)
        assertTrue("Debe ser >= 0.98f: $sim98", sim98 >= 0.98f)
        assertEquals(98, (sim98 * 100).toInt())
    }

    @Test
    fun senalConSimilitud98Porciento_esAprobadaPorAutoaprendizaje() {
        val lossSig = TradeContextSignature(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            distanceToSupportRatio = 0.50f,
            distanceToResistanceRatio = 0.50f,
            isNearSupportZone = false,
            isNearResistanceZone = false,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = true,
            isBearishImpulse = false,
            isDojiOrLowVolume = false,
            isConsolidationTight = false,
            isMarketSideways = false,
            strategyName = "MT_REJECTION",
            candleSecond = 30
        )
        engine.addLossSignatureForTesting(lossSig)

        // Candidata con 98.5% de similitud (< 0.99f)
        val candidateAnalysis = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            distanceToSupportRatio = 0.55f,
            distanceToResistanceRatio = 0.55f,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = true,
            isBearishImpulse = false,
            isMarketSideways = false,
            isConsolidationTight = false
        )

        val decision = engine.evaluateSignalSuitability(
            candidateAction = TradeAction.BUY,
            analysis = candidateAnalysis,
            tick = null,
            strategyName = "MT_REJECTION"
        )

        assertTrue(
            "Una señal con 98% de similitud DEBE ser aprobada (Allow) en R23F, fue: $decision",
            decision is AdaptiveDecision.Allow
        )
    }

    @Test
    fun senalConSimilitud99Porciento_esBloqueadaPorAntiPatron() {
        val lossSig = TradeContextSignature(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            distanceToSupportRatio = 0.50f,
            distanceToResistanceRatio = 0.50f,
            isNearSupportZone = false,
            isNearResistanceZone = false,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = true,
            isBearishImpulse = false,
            isDojiOrLowVolume = false,
            isConsolidationTight = false,
            isMarketSideways = false,
            strategyName = "MT_REJECTION",
            candleSecond = 30
        )
        engine.addLossSignatureForTesting(lossSig)

        // Candidata con 99.7% de similitud (>= 0.99f)
        val candidateAnalysis = VisionAnalysisResult(
            trend = TrendDirection.UPTREND,
            distanceToSupportRatio = 0.51f,
            distanceToResistanceRatio = 0.51f,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = true,
            isBearishImpulse = false,
            isMarketSideways = false,
            isConsolidationTight = false
        )

        val decision = engine.evaluateSignalSuitability(
            candidateAction = TradeAction.BUY,
            analysis = candidateAnalysis,
            tick = null,
            strategyName = "MT_REJECTION"
        )

        assertTrue(
            "Una señal con 99% de similitud DEBE ser bloqueada (Block) en R23F, fue: $decision",
            decision is AdaptiveDecision.Block
        )
        val block = decision as AdaptiveDecision.Block
        assertTrue("El mensaje debe contener 99% similitud", block.reason.contains("99% similitud"))
    }

    @Test
    fun senalConSimilitud100Porciento_esBloqueadaPorAntiPatron() {
        val lossSig = TradeContextSignature(
            action = TradeAction.SELL,
            trend = TrendDirection.DOWNTREND,
            distanceToSupportRatio = 0.20f,
            distanceToResistanceRatio = 0.80f,
            isNearSupportZone = true,
            isNearResistanceZone = false,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = false,
            isBearishImpulse = true,
            isDojiOrLowVolume = false,
            isConsolidationTight = false,
            isMarketSideways = false,
            strategyName = "MT_REJECTION",
            candleSecond = 45
        )
        engine.addLossSignatureForTesting(lossSig)

        val candidateAnalysis = VisionAnalysisResult(
            trend = TrendDirection.DOWNTREND,
            distanceToSupportRatio = 0.20f,
            distanceToResistanceRatio = 0.80f,
            tickVelocityNormalized = 1.0f,
            isBullishImpulse = false,
            isBearishImpulse = true,
            isMarketSideways = false,
            isConsolidationTight = false
        )

        val decision = engine.evaluateSignalSuitability(
            candidateAction = TradeAction.SELL,
            analysis = candidateAnalysis,
            tick = null,
            strategyName = "MT_REJECTION"
        )

        assertTrue(
            "Una señal con 100% de similitud DEBE ser bloqueada (Block) en R23F, fue: $decision",
            decision is AdaptiveDecision.Block
        )
        val block = decision as AdaptiveDecision.Block
        assertTrue("El mensaje debe contener 100% similitud", block.reason.contains("100% similitud"))
    }
}
