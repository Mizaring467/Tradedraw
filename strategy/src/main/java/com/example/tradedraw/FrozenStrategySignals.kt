package com.example.tradedraw

import com.example.tradedraw.core.*
import java.util.Locale

/** Frozen selection rules; broker observations are explicit inputs. */
object FrozenStrategySignals {
    fun evaluateStrategySignal(
        strategy: AutoTradeStrategy,
        analysis: VisionAnalysisResult,
        syntheticEngine: SyntheticCandleEngine? = null,
        subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
        latestTick: MarketTick? = null,
        brokerState: BrokerStateView = BrokerStateView(observedAsset = "", isDemoAccount = false)
    ): TradeAction? {
        val (action, _) = evaluateStrategySignalWithReason(strategy, analysis, syntheticEngine, subMode, latestTick, brokerState)
        return action
    }

    fun evaluateSniperConfluences(
        analysis: VisionAnalysisResult,
        syntheticEngine: SyntheticCandleEngine?,
        latestTick: MarketTick?,
        brokerState: BrokerStateView = BrokerStateView(observedAsset = "", isDemoAccount = false)
    ): Pair<TradeAction?, String> {
        // Confluencia 1: Activo Forex Real válido (Veto estricto a Sintéticos y OTC)
        val currentAsset = brokerState.rawObservedAsset.ifBlank { "Crypto IDX" }
        val isSynthetic = brokerState.isSyntheticOrOTC ||
            currentAsset.contains("IDX", ignoreCase = true) ||
            currentAsset.contains("OTC", ignoreCase = true)
        if (isSynthetic) {
            return Pair(null, "⚠️ Veto Francotirador: Activo sintético/OTC ($currentAsset) prohibido para dinero real. Selecciona un par Forex real en Binomo")
        }
        val classification = com.example.tradedraw.core.AssetClassifier.classifyAsset(currentAsset)
        if (classification != AssetClassification.FOREX_REAL) {
            return Pair(null, "⚠️ Francotirador: Activo ($currentAsset) no es un par Forex real válido. Selecciona EUR/USD, GBP/USD, etc.")
        }

        // Confluencia 4 (Timing): Entrada estricta en el segundo :58s-:59s (o fin de ciclo de vela según timeframe)
        val tf = syntheticEngine?.timeframe ?: CandleTimeframe.M1
        val ts = latestTick?.timestampMs ?: System.currentTimeMillis()
        val inTimingWindow = if (analysis.candleSecond > 0 && tf == CandleTimeframe.M1) {
            MarketTickFilters.isInEntryWindow(analysis.candleSecond)
        } else {
            tf.isSniperTimingWindow(ts)
        }
        if (!inTimingWindow) {
            val cycleSec = tf.getCycleSecond(ts)
            val cycleRem = tf.seconds - cycleSec
            val remMin = cycleRem / 60
            val remSec = cycleRem % 60
            val remStr = if (tf == CandleTimeframe.M5) "%d:%02d".format(Locale.US, remMin, remSec) else ":%02ds".format(Locale.US, remSec)
            return Pair(null, "⏳ Francotirador en espera: timing estricto :01s-:05s [${tf.label}] (restante: $remStr)")
        }

        val candleList = analysis.candleList
        val c0 = candleList.firstOrNull()
        val c1 = if (candleList.size >= 2) candleList[1] else null

        val synCurrent = syntheticEngine?.currentCandle
        val synPrev = syntheticEngine?.closedCandles?.lastOrNull()

        // Confluencia 3: Vela previa o actual con mecha de rechazo >= 40% del rango total en dirección contraria al nivel
        val hasBottomRejection40 = (c0 != null && c0.bottomWickRatio >= 0.40f) ||
            (c1 != null && c1.bottomWickRatio >= 0.40f) ||
            (synCurrent != null && synCurrent.lowerWickRatio >= 0.40f) ||
            (synPrev != null && synPrev.lowerWickRatio >= 0.40f) ||
            analysis.hasBottomRejectionWick
        val hasTopRejection40 = (c0 != null && c0.topWickRatio >= 0.40f) ||
            (c1 != null && c1.topWickRatio >= 0.40f) ||
            (synCurrent != null && synCurrent.upperWickRatio >= 0.40f) ||
            (synPrev != null && synPrev.upperWickRatio >= 0.40f) ||
            analysis.hasTopRejectionWick

        // Confluencia 2: Rebote claro en nivel de Soporte (para CALL) o Resistencia (para PUT) validado
        val touchesSupport = analysis.touchesSupport || analysis.isNearSupportZone ||
            (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.15f)
        val touchesResistance = analysis.touchesResistance || analysis.isNearResistanceZone ||
            (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.15f)

        // Confluencia 4 (Micro-velocidad a favor y sin vela sobreextendida):
        val velNorm = analysis.tickVelocityNormalized
        val tickVel = latestTick?.velocity ?: 0f
        // Ronda 7-fix: umbral relativo a la escala del precio (IDX ~641, ETH ~1049).
        val tickPriceRef = latestTick?.price ?: 0.0
        val vThresh = if (tickPriceRef > 0.0) (tickPriceRef * 0.00004).toFloat() else 0.0001f
        val velCallFavor = (velNorm > vThresh || tickVel > vThresh || analysis.isBullishImpulse) &&
            !analysis.isBearishImpulse && velNorm >= -vThresh && tickVel >= -vThresh
        val velPutFavor = (velNorm < -vThresh || tickVel < -vThresh || analysis.isBearishImpulse) &&
            !analysis.isBullishImpulse && velNorm <= vThresh && tickVel <= vThresh

        val isOverextendedCall = (syntheticEngine?.isBullishOverextended == true) ||
            (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN)
        val isOverextendedPut = (syntheticEngine?.isBearishOverextended == true) ||
            (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED)

        // CALL: Soporte validado + Mecha inferior >= 40% + Micro-velocidad alcista + No sobreextendido
        if (touchesSupport && hasBottomRejection40 && velCallFavor && !isOverextendedCall) {
            return Pair(TradeAction.BUY, "🎯 FRANCOTIRADOR [CALL ${tf.label}]: Soporte validado + Mecha rechazo ≥40% + Micro-velocidad alcista")
        }

        // PUT: Resistencia validada + Mecha superior >= 40% + Micro-velocidad bajista + No sobreextendido
        if (touchesResistance && hasTopRejection40 && velPutFavor && !isOverextendedPut) {
            return Pair(TradeAction.SELL, "🎯 FRANCOTIRADOR [PUT ${tf.label}]: Resistencia validada + Mecha rechazo ≥40% + Micro-velocidad bajista")
        }

        return Pair(null, "🎯 Francotirador: Confluencias incompletas (S/R, Mecha ≥40%, Vel, :58-:59s)")
    }

    fun evaluateStrategySignalWithReason(
        strategy: AutoTradeStrategy,
        analysis: VisionAnalysisResult,
        syntheticEngine: SyntheticCandleEngine? = null,
        subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
        latestTick: MarketTick? = null,
        brokerState: BrokerStateView = BrokerStateView(observedAsset = "", isDemoAccount = false)
    ): Pair<TradeAction?, String> {
        // MODO FRANCOTIRADOR: Gatillo de 4 Confluencias Simultáneas
        if (subMode == AutonomousSubMode.SNIPER) {
            return evaluateSniperConfluences(analysis, syntheticEngine, latestTick, brokerState)
        }
        // Filtro Anti-Choppy / Micro-Rango Cuantitativo (<0.05% con alternancia de ticks sin dirección clara)
        val isChoppy = analysis.isMicroRangeChoppy || (syntheticEngine != null && syntheticEngine.isChoppinessDetected())
        if (isChoppy) {
            return Pair(null, "⚠️ Veto Anti-Choppy: Micro-rango (<0.05%) con alternancia de ticks sin dirección clara")
        }

        // Filtro anti-mercado lateral / Dojis / Consolidación estrecha
        if (analysis.isMarketSideways) {
            return Pair(null, "⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad")
        }
        if (analysis.isConsolidationTight) {
            return Pair(null, "⏳ Rango estrecho / Sin volatilidad: Esperando expansión")
        }

        val sec = analysis.candleSecond
        val isStrictTimingWindow = MarketTickFilters.isStrictTimingWindow(sec) // :58 a :03
        val isTimingVetoed = MarketTickFilters.isTimingVetoed(sec) // :06 a :57
        val isInstitutionalTrap = analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut
        val isSniperPullbackTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
            (analysis.isPullbackAgainstSignalCall || analysis.isPullbackAgainstSignalPut || analysis.isPullbackSniperCall || analysis.isPullbackSniperPut)

        // Veto universal de entrada tardía (:06 a :57)
        if (isTimingVetoed && !isInstitutionalTrap) {
            return Pair(null, "⏳ Entrada tardía - Veto Timing Estricto: Fuera de ventana sniper :01-:05 (⏱ ${sec}s)")
        }

        // Veto de entrada si está fuera de la ventana estricta :01-:05 (salvo trampas institucionales)
        if (!isStrictTimingWindow && !isInstitutionalTrap && !isSniperPullbackTrigger) {
            return Pair(null, "⏳ Entrada tardía (${sec}s): Fuera de ventana sniper :01-:05")
        }

        val isSniperPullbackCallTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
            (analysis.isPullbackAgainstSignalCall || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.GREEN && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY >= analysis.candleList[1].bodyTopY - 3f && analysis.currentPriceY <= analysis.candleList[1].bodyTopY + analysis.candleList[1].bodyHeight * 0.55f)) &&
            !analysis.isDojiOrLowVolume

        val isSniperPullbackPutTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
            (analysis.isPullbackAgainstSignalPut || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.RED && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY <= analysis.candleList[1].bodyBottomY + 3f && analysis.currentPriceY >= analysis.candleList[1].bodyBottomY - analysis.candleList[1].bodyHeight * 0.55f)) &&
            !analysis.isDojiOrLowVolume

        val rawResult = when (strategy) {
            AutoTradeStrategy.AUTO_ADAPTIVE -> {
                // Modo Automático por Detección de Régimen de Mercado (Regime Engine en 2 Etapas)
                val regimeResult = MarketRegimeClassifier.classify(analysis, syntheticEngine)
                val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                val inUptrend = analysis.trend == TrendDirection.UPTREND

                when (regimeResult.regime) {
                    MarketRegime.CHOPPY_NOISE -> {
                        Pair(null, "⏸ Auto Standby [${regimeResult.reason}] -> Sin trade")
                    }

                    MarketRegime.RANGING_CHANNEL -> {
                        // En Rango S/R: Solo se operan Reversiones en extremos (Trampas, Sobreextensión RSI, Mechas de Rechazo, Envolventes en S/R y Agotamiento)
                        when {
                            // 1. Trampa Institucional / Falso Rompimiento en S/R (95% confluencia)
                            !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isFalseBreakoutCall ->
                                Pair(TradeAction.BUY, "🎯 Auto [Trampa en Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && analysis.isFalseBreakoutPut ->
                                Pair(TradeAction.SELL, "🎯 Auto [Trampa en Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                            // 2. Reversión Cuantitativa por Sobreextensión en Extremos S/R
                            !inUptrend && (analysis.isNearResistanceZone || analysis.touchesResistance || analysis.distanceToResistanceRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.20f)) &&
                            (syntheticEngine?.isBullishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN) || analysis.isPriceNearTop) &&
                            (!analysis.hasStrongMomentumUp && !analysis.isValidBreakoutCall && (analysis.tickVelocityNormalized <= 0.05f || analysis.isBearishImpulse || analysis.hasTopRejectionWick || analysis.isRejectionPut || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))) -> {
                                val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                                Pair(TradeAction.SELL, "🎯 Auto [Reversión por Sobreextensión en Resistencia$rsiStr | Rango S/R | ⏱ ${sec}s] -> PUT")
                            }

                            !inDowntrend && (analysis.isNearSupportZone || analysis.touchesSupport || analysis.distanceToSupportRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.20f)) &&
                            (syntheticEngine?.isBearishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED) || analysis.isPriceNearBottom) &&
                            (!analysis.hasStrongMomentumDown && !analysis.isValidBreakoutPut && (analysis.tickVelocityNormalized >= -0.05f || analysis.isBullishImpulse || analysis.hasBottomRejectionWick || analysis.isRejectionCall || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))) -> {
                                val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                                Pair(TradeAction.BUY, "🎯 Auto [Reversión por Sobreextensión en Soporte$rsiStr | Rango S/R | ⏱ ${sec}s] -> CALL")
                            }

                            // 3. Mechas de Rechazo y Absorción en S/R (MT_REJECTION)
                            !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) &&
                                (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.BUY, "🎯 Auto [Mecha Rechazo Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) &&
                                (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.SELL, "🎯 Auto [Mecha Rechazo Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                            // 4. Vela Envolvente en Zona S/R (85% confluencia)
                            !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isEngulfingCall &&
                                (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && analysis.isEngulfingPut &&
                                (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                            // 5. Agotamiento de 3 Velas Consecutivas (MT_3_VELAS)
                            !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) ->
                                Pair(TradeAction.BUY, "🎯 Auto [Agotamiento 3 Rojas Alcista | Rango S/R | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) ->
                                Pair(TradeAction.SELL, "🎯 Auto [Agotamiento 3 Verdes Bajista | Rango S/R | ⏱ ${sec}s] -> PUT")

                            // 6. Rebote Clásico con confirmación de timing
                            !inDowntrend && !analysis.hasStrongMomentumDown && analysis.touchesSupport && analysis.isPullbackSniperCall ->
                                Pair(TradeAction.BUY, "🎯 Auto [Rebote en Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && analysis.touchesResistance && analysis.isPullbackSniperPut ->
                                Pair(TradeAction.SELL, "🎯 Auto [Rebote en Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                            else -> Pair(null, "⏳ Auto [Rango S/R]: Esperando aproximación a extremo con rechazo")
                        }
                    }

                    MarketRegime.STRONG_TREND_UP -> {
                        // En Tendencia Alcista: Únicamente entradas a favor de tendencia (Pullback Sniper o Vela Envolvente)
                        when {
                            !analysis.hasStrongMomentumDown && isSniperPullbackCallTrigger ->
                                Pair(TradeAction.BUY, "🎯 Auto [Sniper Pullback :0${sec}s | Tendencia Alcista] -> CALL")
                            !analysis.hasStrongMomentumDown && analysis.isEngulfingCall && (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | Tendencia Alcista | ⏱ ${sec}s] -> CALL")
                            !analysis.hasStrongMomentumDown && (analysis.confluenceScoreCall >= 85 || analysis.signalPowerCall >= 85) -> {
                                if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                    Pair(null, "⚠️ Veto: Tendencia alcista en techo de canal sin retroceso")
                                } else {
                                    val score = Math.max(analysis.confluenceScoreCall, analysis.signalPowerCall)
                                    Pair(TradeAction.BUY, "🎯 Auto [Confluencia Fuerte Alcista ($score%) | ⏱ ${sec}s] -> CALL")
                                }
                            }
                            else -> Pair(null, "⏳ Auto [Tendencia Alcista]: Esperando retroceso pullback o envolvente a favor")
                        }
                    }

                    MarketRegime.STRONG_TREND_DOWN -> {
                        // En Tendencia Bajista: Únicamente entradas a favor de tendencia (Pullback Sniper o Vela Envolvente)
                        when {
                            !analysis.hasStrongMomentumUp && isSniperPullbackPutTrigger ->
                                Pair(TradeAction.SELL, "🎯 Auto [Sniper Pullback :0${sec}s | Tendencia Bajista] -> PUT")
                            !analysis.hasStrongMomentumUp && analysis.isEngulfingPut && (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | Tendencia Bajista | ⏱ ${sec}s] -> PUT")
                            !analysis.hasStrongMomentumUp && (analysis.confluenceScorePut >= 85 || analysis.signalPowerPut >= 85) -> {
                                if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                    Pair(null, "⚠️ Veto: Tendencia bajista en piso de canal sin retroceso")
                                } else {
                                    val score = Math.max(analysis.confluenceScorePut, analysis.signalPowerPut)
                                    Pair(TradeAction.SELL, "🎯 Auto [Confluencia Fuerte Bajista ($score%) | ⏱ ${sec}s] -> PUT")
                                }
                            }
                            else -> Pair(null, "⏳ Auto [Tendencia Bajista]: Esperando retroceso pullback o envolvente a favor")
                        }
                    }

                    MarketRegime.BREAKOUT_RETEST -> {
                        // En Rompimiento y Retest: Choque con nivel roto o Rompimiento Validado
                        when {
                            !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isChoqueCall || analysis.isChoquePullbackCall) ->
                                Pair(TradeAction.BUY, "🎯 Auto [Choque / Pullback Alcista | Rompimiento/Retest | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isChoquePut || analysis.isChoquePullbackPut) ->
                                Pair(TradeAction.SELL, "🎯 Auto [Choque / Pullback Bajista | Rompimiento/Retest | ⏱ ${sec}s] -> PUT")
                            !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isValidBreakoutCall && !analysis.isDojiOrLowVolume ->
                                Pair(TradeAction.BUY, "🎯 Auto [Rompimiento Válido Resistencia | ⏱ ${sec}s] -> CALL")
                            !inUptrend && !analysis.hasStrongMomentumUp && analysis.isValidBreakoutPut && !analysis.isDojiOrLowVolume ->
                                Pair(TradeAction.SELL, "🎯 Auto [Rompimiento Válido Soporte | ⏱ ${sec}s] -> PUT")
                            else -> Pair(null, "⏳ Auto [Rompimiento/Retest]: Esperando confirmación de choque o ruptura limpia")
                        }
                    }
                }
            }
            AutoTradeStrategy.MT_MASTER_COMBO -> {
                // Jerarquía Master Traders con filtro macro de confluencia
                val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                val inUptrend = analysis.trend == TrendDirection.UPTREND

                when {
                    // 1. Falso Rompimiento / Trampa en S/R
                    !inDowntrend && analysis.isFalseBreakoutCall -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Trampa / Falso Rompimiento de Soporte -> CALL")
                    }
                    !inUptrend && analysis.isFalseBreakoutPut -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Trampa / Falso Rompimiento de Resistencia -> PUT")
                    }
                    // 1b. Reversión Contra-Tendencia Cuantitativa por Sobreextensión en Zonas Clave
                    !inUptrend && (analysis.isNearResistanceZone || analysis.touchesResistance || analysis.distanceToResistanceRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.20f)) &&
                    (syntheticEngine?.isBullishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN) || analysis.isPriceNearTop) &&
                    (!analysis.hasStrongMomentumUp && !analysis.isValidBreakoutCall && (analysis.tickVelocityNormalized <= 0.05f || analysis.isBearishImpulse || analysis.hasTopRejectionWick || analysis.isRejectionPut || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))) -> {
                        val rsiStr = if (syntheticEngine != null) " (RSI ${syntheticEngine.syntheticTickRsi.toInt()})" else ""
                        Pair(TradeAction.SELL, "🎯 MT Combo: Reversión por Sobreextensión en Resistencia$rsiStr -> PUT")
                    }
                    !inDowntrend && (analysis.isNearSupportZone || analysis.touchesSupport || analysis.distanceToSupportRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.20f)) &&
                    (syntheticEngine?.isBearishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED) || analysis.isPriceNearBottom) &&
                    (!analysis.hasStrongMomentumDown && !analysis.isValidBreakoutPut && (analysis.tickVelocityNormalized >= -0.05f || analysis.isBullishImpulse || analysis.hasBottomRejectionWick || analysis.isRejectionCall || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))) -> {
                        val rsiStr = if (syntheticEngine != null) " (RSI ${syntheticEngine.syntheticTickRsi.toInt()})" else ""
                        Pair(TradeAction.BUY, "🎯 MT Combo: Reversión por Sobreextensión en Soporte$rsiStr -> CALL")
                    }
                    // 2. Mechas de Rechazo en S/R (Filtradas por tendencia)
                    !inDowntrend && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Mecha de Rechazo en Soporte -> CALL")
                    }
                    !inUptrend && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Mecha de Rechazo en Resistencia -> PUT")
                    }
                    // 3. Patrón Envolvente en S/R
                    !inDowntrend && analysis.isEngulfingCall -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Vela Envolvente en Soporte -> CALL")
                    }
                    !inUptrend && analysis.isEngulfingPut -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Vela Envolvente en Resistencia -> PUT")
                    }
                    // 4. Choque / Retest (Breakout + Retest)
                    !inDowntrend && (analysis.isChoqueCall || analysis.isChoquePullbackCall) -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Choque / Pullback tras Rompimiento -> CALL")
                    }
                    !inUptrend && (analysis.isChoquePut || analysis.isChoquePullbackPut) -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Choque / Pullback tras Rompimiento -> PUT")
                    }
                    // 5. Agotamiento de 3 Velas
                    !inDowntrend && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Agotamiento 3 Velas Rojas -> CALL")
                    }
                    !inUptrend && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Agotamiento 3 Velas Verdes -> PUT")
                    }
                    // Sniper Pullback Entry (:01s-:05s) tras vela de señal fuerte
                    !inDowntrend && !analysis.hasStrongMomentumDown && isSniperPullbackCallTrigger -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Sniper Pullback (:0${sec}s) tras vela fuerte -> CALL")
                    }
                    !inUptrend && !analysis.hasStrongMomentumUp && isSniperPullbackPutTrigger -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Sniper Pullback (:0${sec}s) tras vela fuerte -> PUT")
                    }
                    // Rompimiento Válido S/R (>50% cuerpo fuera, mecha opuesta <20%)
                    !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isValidBreakoutCall && !analysis.isDojiOrLowVolume -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Rompimiento Válido de Resistencia -> CALL")
                    }
                    !inUptrend && !analysis.hasStrongMomentumUp && analysis.isValidBreakoutPut && !analysis.isDojiOrLowVolume -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Rompimiento Válido de Soporte -> PUT")
                    }
                    // Soporte / Resistencia Clásico
                    !inDowntrend && !analysis.hasStrongMomentumDown && analysis.touchesSupport && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || analysis.isPullbackSniperCall) -> {
                        Pair(TradeAction.BUY, "🎯 MT Combo: Rebote Confirmado en Soporte -> CALL")
                    }
                    !inUptrend && !analysis.hasStrongMomentumUp && analysis.touchesResistance && (analysis.lastCandles.firstOrNull() == CandleType.RED || analysis.isPullbackSniperPut) -> {
                        Pair(TradeAction.SELL, "🎯 MT Combo: Rebote Confirmado en Resistencia -> PUT")
                    }
                    // 6. Termómetro de Señal / Tendencia Alta Probabilidad >= 80%
                    !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isSniperTimingWindow && (analysis.signalPowerCall >= 80 || (analysis.isCallSignal && analysis.signalScore >= 80)) -> {
                        if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                            Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            val score = if (analysis.signalPowerCall >= 80) analysis.signalPowerCall else analysis.signalScore
                            Pair(TradeAction.BUY, "🎯 MT Combo: Tendencia Alta ($score%) -> CALL")
                        }
                    }
                    !inUptrend && !analysis.hasStrongMomentumUp && analysis.isSniperTimingWindow && (analysis.signalPowerPut >= 80 || (analysis.isPutSignal && analysis.signalScore >= 80)) -> {
                        if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                            Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            val score = if (analysis.signalPowerPut >= 80) analysis.signalPowerPut else analysis.signalScore
                            Pair(TradeAction.SELL, "🎯 MT Combo: Tendencia Baja ($score%) -> PUT")
                        }
                    }
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.MT_FALSE_BREAKOUT -> {
                when {
                    analysis.isFalseBreakoutCall -> Pair(TradeAction.BUY, "🎯 MT Falso Rompimiento: Trampa en Soporte -> CALL")
                    analysis.isFalseBreakoutPut -> Pair(TradeAction.SELL, "🎯 MT Falso Rompimiento: Trampa en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.MT_ENGULFING_SR -> {
                when {
                    analysis.isEngulfingCall -> Pair(TradeAction.BUY, "🎯 MT Envolvente: Vela Verde en Soporte -> CALL")
                    analysis.isEngulfingPut -> Pair(TradeAction.SELL, "🎯 MT Envolvente: Vela Roja en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.MT_REJECTION -> {
                when {
                    analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick) ->
                        Pair(TradeAction.BUY, "🎯 MT Mecha Rechazo: Absorción en Soporte -> CALL")
                    analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick) ->
                        Pair(TradeAction.SELL, "🎯 MT Mecha Rechazo: Absorción en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                when {
                    isSniperPullbackCallTrigger ->
                        Pair(TradeAction.BUY, "🎯 MT Choque Pullback: Sniper Pullback (:0${sec}s) -> CALL")
                    isSniperPullbackPutTrigger ->
                        Pair(TradeAction.SELL, "🎯 MT Choque Pullback: Sniper Pullback (:0${sec}s) -> PUT")
                    analysis.isChoqueCall || analysis.isChoquePullbackCall ->
                        Pair(TradeAction.BUY, "🎯 MT Choque Pullback: Retest en Soporte -> CALL")
                    analysis.isChoquePut || analysis.isChoquePullbackPut ->
                        Pair(TradeAction.SELL, "🎯 MT Choque Pullback: Retest en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                when {
                    analysis.is3VelasCall || analysis.isExhaustion3CandlesCall ->
                        Pair(TradeAction.BUY, "🎯 MT 3 Velas Agotamiento: Reversión Alcista -> CALL")
                    analysis.is3VelasPut || analysis.isExhaustion3CandlesPut ->
                        Pair(TradeAction.SELL, "🎯 MT 3 Velas Agotamiento: Reversión Bajista -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.COLOR_TREND -> {
                when {
                    analysis.isDojiOrLowVolume ->
                        Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
                    analysis.trend == TrendDirection.UPTREND && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || isSniperPullbackCallTrigger) -> {
                        if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                            Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            Pair(TradeAction.BUY, "🎯 Color Trend: Continuación Alcista -> CALL")
                        }
                    }
                    analysis.trend == TrendDirection.DOWNTREND && (analysis.lastCandles.firstOrNull() == CandleType.RED || isSniperPullbackPutTrigger) -> {
                        if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                            Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            Pair(TradeAction.SELL, "🎯 Color Trend: Continuación Bajista -> PUT")
                        }
                    }
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.STRIKE_BREAKOUT -> {
                when {
                    analysis.isValidBreakoutCall ->
                        Pair(TradeAction.BUY, "🎯 Strike Breakout: Rompimiento Válido de Resistencia (>50% cuerpo, mecha <20%) -> CALL")
                    analysis.isValidBreakoutPut ->
                        Pair(TradeAction.SELL, "🎯 Strike Breakout: Rompimiento Válido de Soporte (>50% cuerpo, mecha <20%) -> PUT")
                    analysis.isFakeoutRiskCall || analysis.isFakeoutRiskPut || analysis.isFakeoutRisk ->
                        Pair(null, "⚠️ Veto: Riesgo de Falso Rompimiento / Fakeout (Cuerpo <=50% fuera de S/R o mecha opuesta >=20%)")
                    analysis.touchesSupport || analysis.isChoqueCall || analysis.isChoquePullbackCall ->
                        Pair(TradeAction.BUY, "🎯 Strike Breakout: Rebote/Ruptura de Soporte -> CALL")
                    analysis.touchesResistance || analysis.isChoquePut || analysis.isChoquePullbackPut ->
                        Pair(TradeAction.SELL, "🎯 Strike Breakout: Rebote/Ruptura de Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.AI_REMOTE -> {
                Pair(null, "🧠 IA Remota: Esperando análisis multimodal...")
            }
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                when {
                    analysis.touchesSupport -> Pair(TradeAction.BUY, "🎯 S/R: Toque en Soporte -> CALL")
                    analysis.touchesResistance -> Pair(TradeAction.SELL, "🎯 S/R: Toque en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                when {
                    analysis.consecutiveCount >= 3 && analysis.lastCandles.firstOrNull() == CandleType.RED ->
                        Pair(TradeAction.BUY, "🎯 Patrón Velas: Reversión 3 Rojas -> CALL")
                    analysis.consecutiveCount >= 3 && analysis.lastCandles.firstOrNull() == CandleType.GREEN ->
                        Pair(TradeAction.SELL, "🎯 Patrón Velas: Reversión 3 Verdes -> PUT")
                    analysis.isHammer ->
                        Pair(TradeAction.BUY, "🎯 Patrón Velas: Martillo Detectado -> CALL")
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.TREND_FOLLOWING -> {
                when {
                    analysis.isDojiOrLowVolume ->
                        Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
                    analysis.trend == TrendDirection.UPTREND && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || isSniperPullbackCallTrigger) -> {
                        if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                            Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            Pair(TradeAction.BUY, "🎯 Tendencia: Continuación Alcista -> CALL")
                        }
                    }
                    analysis.trend == TrendDirection.DOWNTREND && (analysis.lastCandles.firstOrNull() == CandleType.RED || isSniperPullbackPutTrigger) -> {
                        if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                            Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                        } else {
                            Pair(TradeAction.SELL, "🎯 Tendencia: Continuación Bajista -> PUT")
                        }
                    }
                    else -> Pair(null, "")
                }
            }
            AutoTradeStrategy.COMBINED -> {
                when {
                    analysis.touchesSupport && analysis.lastCandles.firstOrNull() != CandleType.RED ->
                        Pair(TradeAction.BUY, "🎯 Combinada: Confirmación en Soporte -> CALL")
                    analysis.touchesResistance && analysis.lastCandles.firstOrNull() != CandleType.GREEN ->
                        Pair(TradeAction.SELL, "🎯 Combinada: Confirmación en Resistencia -> PUT")
                    else -> Pair(null, "")
                }
            }
        }

        val (action, reason) = rawResult
        if (action == null) return rawResult

        // 1. Filtro de Proximidad S/R Anti-Suicidio Universal:
        // Prohibido vender sobre Soporte (salvo rompimiento válido bajista o rechazo en resistencia)
        if (action == TradeAction.SELL && !analysis.isValidBreakoutPut &&
            !analysis.isRejectionPut && !(analysis.touchesResistance && analysis.hasTopRejectionWick) &&
            (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.touchesSupport)) {
            return Pair(null, "⚠️ Veto: Prohibido vender sobre Soporte (Riesgo de Rebote)")
        }
        // Prohibido comprar sobre Resistencia (salvo rompimiento válido alcista o rechazo en soporte)
        if (action == TradeAction.BUY && !analysis.isValidBreakoutCall &&
            !analysis.isRejectionCall && !(analysis.touchesSupport && analysis.hasBottomRejectionWick) &&
            (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.touchesResistance)) {
            return Pair(null, "⚠️ Veto: Prohibido comprar sobre Resistencia (Riesgo de Rechazo)")
        }

        // 2. Exigir Retroceso (Pullback) en Continuación y Bloquear Sobreextensión / Trampas en S/R para estrategias de tendencia
        val isTrendContinuation = strategy == AutoTradeStrategy.TREND_FOLLOWING ||
            strategy == AutoTradeStrategy.COLOR_TREND ||
            reason.contains("Continuación") ||
            reason.contains("Tendencia") ||
            reason.contains("Impulso") ||
            reason.contains("Confluencia Fuerte")

        if (isTrendContinuation) {

            // Filtro Anti-Continuación por Agotamiento de 3 Velas (c3 < c2 < c1 en S/R):
            if (action == TradeAction.SELL && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall)) {
                return Pair(null, "⛔ Veto: Agotamiento 3 Velas Rojas en Soporte detectado. Prohibido vender en suelo")
            }
            if (action == TradeAction.BUY && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut)) {
                return Pair(null, "⛔ Veto: Agotamiento 3 Velas Verdes en Resistencia detectado. Prohibido comprar en techo")
            }

            // Filtro Anti-Continuación por Mecha de Rechazo (>=45% en S/R):
            if (action == TradeAction.SELL && (analysis.isRejectionCall || analysis.hasBottomRejectionWick)) {
                return Pair(null, "⛔ Veto: Mecha de Rechazo Inferior (≥45%) en Soporte. Prohibido vender en suelo")
            }
            if (action == TradeAction.BUY && (analysis.isRejectionPut || analysis.hasTopRejectionWick)) {
                return Pair(null, "⛔ Veto: Mecha de Rechazo Superior (≥45%) en Resistencia. Prohibido comprar en techo")
            }

            if (analysis.isDojiOrLowVolume) {
                return Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
            }
        }

        // 2. Filtro Anti-Sobreextensión de Racha y Ticks:
        val isGreenStreak = analysis.consecutiveCount >= 4 &&
            (analysis.lastCandles.firstOrNull() == CandleType.GREEN || analysis.streakBadge.contains("V"))
        val isRedStreak = analysis.consecutiveCount >= 4 &&
            (analysis.lastCandles.firstOrNull() == CandleType.RED || analysis.streakBadge.contains("R"))

        val isWsOverbought = syntheticEngine?.isBullishOverextended == true ||
            (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.20f)
        val isWsOversold = syntheticEngine?.isBearishOverextended == true ||
            (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.20f)

        if (isTrendContinuation) {
            if (action == TradeAction.BUY && isGreenStreak) {
                return Pair(null, "⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso")
            }
            if (action == TradeAction.SELL && isRedStreak) {
                return Pair(null, "⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso")
            }
            if (action == TradeAction.BUY && (isWsOverbought || analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.12f || analysis.isPriceNearTop)) {
                val detail = if (syntheticEngine != null && isWsOverbought) " [RSI Ticks: ${syntheticEngine.syntheticTickRsi.toInt()}, Ticks Up: ${syntheticEngine.consecutiveUpTicks}]" else ""
                return Pair(null, "⚠️ Veto: Continuación alcista sin retroceso$detail (Vela en extremo opuesto del rango)")
            }
            if (action == TradeAction.SELL && (isWsOversold || analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.12f || analysis.isPriceNearBottom)) {
                val detail = if (syntheticEngine != null && isWsOversold) " [RSI Ticks: ${syntheticEngine.syntheticTickRsi.toInt()}, Ticks Down: ${syntheticEngine.consecutiveDownTicks}]" else ""
                return Pair(null, "⚠️ Veto: Continuación bajista sin retroceso$detail (Vela en extremo opuesto del rango)")
            }
        }

        return rawResult
    }
}

class LiveStrategyEvaluator(private val brokerSensor: BrokerSensor) {
    fun evaluate(strategy: AutoTradeStrategy, analysis: VisionAnalysisResult, syntheticEngine: SyntheticCandleEngine?,
        subMode: AutonomousSubMode, latestTick: MarketTick?) =
        FrozenStrategySignals.evaluateStrategySignalWithReason(strategy, analysis, syntheticEngine, subMode, latestTick, brokerSensor.getBrokerState())
}
