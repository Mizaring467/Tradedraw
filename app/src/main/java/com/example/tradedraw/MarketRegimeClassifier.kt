package com.example.tradedraw

/**
 * Regímenes de mercado detectados para la toma de decisiones determinista.
 * Clasifica el contexto macro del gráfico antes de autorizar cualquier estrategia.
 */
enum class MarketRegime(val label: String, val hudBadge: String) {
    RANGING_CHANNEL("Canal de Rango S/R", "⚖️ RANGO S/R"),
    STRONG_TREND_UP("Tendencia Alcista Fuerte", "📈 TENDENCIA CALL"),
    STRONG_TREND_DOWN("Tendencia Bajista Fuerte", "📉 TENDENCIA PUT"),
    BREAKOUT_RETEST("Rompimiento y Retest", "💥 ROMPIMIENTO/RETEST"),
    CHOPPY_NOISE("Ruido / Choppy (Standby)", "⏸ STANDBY (RUIDO)")
}

/**
 * Resultado estructurado del clasificador de régimen.
 */
data class MarketRegimeResult(
    val regime: MarketRegime,
    val reason: String,
    val confidence: Int = 80,
    val authorizedStrategies: List<String> = emptyList()
)

/**
 * Motor de Selección y Clasificación de Régimen de Mercado.
 * Asegura que el bot no opere de forma errática ni en cada señal aislada,
 * sino que solo ejecute la estrategia cuantitativa óptima para el entorno actual.
 */
object MarketRegimeClassifier {

    fun classify(
        analysis: VisionAnalysisResult?,
        syntheticEngine: SyntheticCandleEngine? = null
    ): MarketRegimeResult {
        if (analysis == null) {
            val wsTrend = syntheticEngine?.detectedTrend ?: TrendDirection.SIDEWAYS
            return when (wsTrend) {
                TrendDirection.UPTREND -> MarketRegimeResult(
                    regime = MarketRegime.STRONG_TREND_UP,
                    reason = "Tendencia alcista preliminar por ticks sintéticos",
                    confidence = 70,
                    authorizedStrategies = listOf("MT_PULLBACK_SNIPER")
                )
                TrendDirection.DOWNTREND -> MarketRegimeResult(
                    regime = MarketRegime.STRONG_TREND_DOWN,
                    reason = "Tendencia bajista preliminar por ticks sintéticos",
                    confidence = 70,
                    authorizedStrategies = listOf("MT_PULLBACK_SNIPER")
                )
                else -> MarketRegimeResult(
                    regime = MarketRegime.CHOPPY_NOISE,
                    reason = "Esperando primer frame de análisis visual",
                    confidence = 60,
                    authorizedStrategies = emptyList()
                )
            }
        }

        // 1. Prioridad: Detección de Ruido / Mercado Choppy / Indecisión -> Standby Incondicional
        val isChoppy = analysis.isMicroRangeChoppy ||
            (syntheticEngine != null && syntheticEngine.isChoppinessDetected()) ||
            analysis.isConsolidationTight ||
            analysis.isDojiOrLowVolume

        if (isChoppy) {
            val detail = when {
                syntheticEngine?.isChoppinessDetected() == true -> "CHOP elevado cuantitativo (micro-rango/alternancia)"
                analysis.isMicroRangeChoppy -> "Micro-rango visual <0.05%"
                analysis.isConsolidationTight -> "Consolidación estrecha / sin volatilidad"
                analysis.isDojiOrLowVolume -> "Velas Doji o bajo volumen"
                else -> "Compresión sin dirección clara"
            }
            return MarketRegimeResult(
                regime = MarketRegime.CHOPPY_NOISE,
                reason = "Standby: $detail",
                confidence = 90,
                authorizedStrategies = emptyList()
            )
        }

        // 2. Detección de Rompimiento y Retest (Breakout Retest)
        val isBreakoutOrRetest = analysis.isValidBreakoutCall || analysis.isValidBreakoutPut ||
            analysis.isChoqueCall || analysis.isChoquePut ||
            analysis.isChoquePullbackCall || analysis.isChoquePullbackPut

        if (isBreakoutOrRetest) {
            val direction = if (analysis.isValidBreakoutCall || analysis.isChoqueCall || analysis.isChoquePullbackCall) "Alcista" else "Bajista"
            return MarketRegimeResult(
                regime = MarketRegime.BREAKOUT_RETEST,
                reason = "Ruptura o Retest $direction en nivel clave",
                confidence = 85,
                authorizedStrategies = listOf("MT_CHOQUE_PULLBACK", "STRIKE_BREAKOUT")
            )
        }

        // 3. Detección de Canal de Rango S/R (Consolidación Operable entre Soporte y Resistencia o Toque de Extremos)
        val channelHeight = Math.abs(analysis.dynamicSupportY - analysis.dynamicResistanceY)
        val hasClearSR = analysis.touchesSupport || analysis.touchesResistance ||
            analysis.isNearSupportZone || analysis.isNearResistanceZone ||
            analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut ||
            analysis.isRejectionCall || analysis.isRejectionPut ||
            analysis.isEngulfingCall || analysis.isEngulfingPut ||
            analysis.is3VelasCall || analysis.is3VelasPut ||
            analysis.isExhaustion3CandlesCall || analysis.isExhaustion3CandlesPut ||
            (analysis.trend == TrendDirection.SIDEWAYS && channelHeight >= 15f)

        if (hasClearSR) {
            return MarketRegimeResult(
                regime = MarketRegime.RANGING_CHANNEL,
                reason = "Canal de Rango S/R o interacción con extremos",
                confidence = 85,
                authorizedStrategies = listOf("MT_REJECTION", "MT_FALSE_BREAKOUT", "SUPPORT_RESISTANCE", "MT_3_VELAS_AGOTAMIENTO")
            )
        }

        // 4. Detección de Tendencia Fuerte y Limpia (Higher Highs o Lower Lows)
        val wsTrend = syntheticEngine?.detectedTrend ?: TrendDirection.SIDEWAYS
        val isCleanUptrend = (analysis.trend == TrendDirection.UPTREND || (analysis.trend == TrendDirection.SIDEWAYS && wsTrend == TrendDirection.UPTREND)) &&
            !analysis.hasStrongMomentumDown &&
            (analysis.signalPowerCall >= 55 || analysis.consecutiveCount >= 2 || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))

        val isCleanDowntrend = (analysis.trend == TrendDirection.DOWNTREND || (analysis.trend == TrendDirection.SIDEWAYS && wsTrend == TrendDirection.DOWNTREND)) &&
            !analysis.hasStrongMomentumUp &&
            (analysis.signalPowerPut >= 55 || analysis.consecutiveCount >= 2 || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))

        if (isCleanUptrend && !isCleanDowntrend) {
            return MarketRegimeResult(
                regime = MarketRegime.STRONG_TREND_UP,
                reason = "Estructura de Tendencia Alcista limpia",
                confidence = 85,
                authorizedStrategies = listOf("MT_PULLBACK_SNIPER", "MT_ENGULFING_SR", "COLOR_TREND")
            )
        }

        if (isCleanDowntrend && !isCleanUptrend) {
            return MarketRegimeResult(
                regime = MarketRegime.STRONG_TREND_DOWN,
                reason = "Estructura de Tendencia Bajista limpia",
                confidence = 85,
                authorizedStrategies = listOf("MT_PULLBACK_SNIPER", "MT_ENGULFING_SR", "COLOR_TREND")
            )
        }

        // Si no cumple ningún criterio claro, pasar a Standby por precaución
        return MarketRegimeResult(
            regime = MarketRegime.CHOPPY_NOISE,
            reason = "Sin patrón estructural claro en el gráfico (Standby)",
            confidence = 75,
            authorizedStrategies = emptyList()
        )
    }
}
