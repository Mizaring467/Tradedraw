package com.example.tradedraw

import android.graphics.PointF

enum class CandleType {
    GREEN, RED, DOJI
}

typealias TrendDirection = com.example.tradedraw.core.TrendDirection

enum class TradeOutcome {
    WIN, LOSS
}

data class CandleData(
    val type: CandleType,
    val x: Float,
    val topY: Float,
    val bottomY: Float,
    val bodyTopY: Float,
    val bodyBottomY: Float,
    val totalHeight: Float,
    val bodyHeight: Float,
    val topWickRatio: Float,
    val bottomWickRatio: Float
)

typealias AnalysisResult = VisionAnalysisResult

data class VisionAnalysisResult(
    val currentPriceY: Float = 0f,
    val highestPoint: PointF? = null,
    val lowestPoint: PointF? = null,
    val trend: TrendDirection = TrendDirection.SIDEWAYS,
    val lastCandles: List<CandleType> = emptyList(),
    val candleList: List<CandleData> = emptyList(),
    val consecutiveCount: Int = 0,
    val streakBadge: String = "",
    val signalPowerCall: Int = 50,
    val signalPowerPut: Int = 50,
    val isMarketSideways: Boolean = false,
    val isHammer: Boolean = false,
    val isEngulfing: Boolean = false,
    val isEngulfingCall: Boolean = false,
    val isEngulfingPut: Boolean = false,
    val isFalseBreakoutCall: Boolean = false,
    val isFalseBreakoutPut: Boolean = false,
    val isRejectionCall: Boolean = false,
    val isRejectionPut: Boolean = false,
    val touchesSupport: Boolean = false,
    val touchesResistance: Boolean = false,
    val hasTopRejectionWick: Boolean = false,
    val hasBottomRejectionWick: Boolean = false,
    val isChoqueCall: Boolean = false,
    val isChoquePut: Boolean = false,
    val isChoquePullbackCall: Boolean = false,
    val isChoquePullbackPut: Boolean = false,
    val is3VelasCall: Boolean = false,
    val is3VelasPut: Boolean = false,
    val isExhaustion3CandlesCall: Boolean = false,
    val isExhaustion3CandlesPut: Boolean = false,
    val dynamicResistanceY: Float = 0f,
    val dynamicSupportY: Float = 0f,
    val greenPixelsDetected: Int = 0,
    val redPixelsDetected: Int = 0,
    val diagnosticSummary: String = "",
    val isCallSignal: Boolean = false,
    val isPutSignal: Boolean = false,
    val signalScore: Int = 50,
    val candleSecond: Int = 2,
    val isSniperTimingWindow: Boolean = true,
    val isLateTimingForbidden: Boolean = false,
    val isTimingVetoed: Boolean = false,
    val isMicroRangeChoppy: Boolean = false,
    val isPullbackSniperCall: Boolean = false,
    val isPullbackSniperPut: Boolean = false,
    val isSniperPullbackWindow: Boolean = false,
    val isPullbackAgainstSignalCall: Boolean = false,
    val isPullbackAgainstSignalPut: Boolean = false,
    val isConsolidationTight: Boolean = false,
    val isDojiOrLowVolume: Boolean = false,
    val avgBodyHeightLast5: Float = 0f,
    val isValidBreakoutCall: Boolean = false,
    val isValidBreakoutPut: Boolean = false,
    val isFakeoutRiskCall: Boolean = false,
    val isFakeoutRiskPut: Boolean = false,
    val isFakeoutRisk: Boolean = isFakeoutRiskCall || isFakeoutRiskPut,
    val confluenceScoreCall: Int = 50,
    val confluenceScorePut: Int = 50,
    val latestCandleX: Float = 0f,
    val isChartOffCenterRight: Boolean = false,
    val isChartOffCenterLeft: Boolean = false,
    val isPriceNearBottom: Boolean = false,
    val isPriceNearTop: Boolean = false,
    val hasStrongMomentumDown: Boolean = false,
    val hasStrongMomentumUp: Boolean = false,
    val distanceToSupportRatio: Float = if (Math.abs(dynamicSupportY - dynamicResistanceY) > 1f) {
        (Math.abs(currentPriceY - dynamicSupportY) / Math.abs(dynamicSupportY - dynamicResistanceY)).coerceIn(0f, 1f)
    } else 0.5f,
    val distanceToResistanceRatio: Float = if (Math.abs(dynamicSupportY - dynamicResistanceY) > 1f) {
        (Math.abs(currentPriceY - dynamicResistanceY) / Math.abs(dynamicSupportY - dynamicResistanceY)).coerceIn(0f, 1f)
    } else 0.5f,
    val isNearSupportZone: Boolean = touchesSupport || distanceToSupportRatio < 0.15f,
    val isNearResistanceZone: Boolean = touchesResistance || distanceToResistanceRatio < 0.15f,
    val tickVelocityY: Float = 0f,
    val tickVelocityNormalized: Float = 0f,
    val isBullishImpulse: Boolean = false,
    val isBearishImpulse: Boolean = false,
    val isSniperZeroSecondWindow: Boolean = true
)

