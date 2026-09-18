package com.example.tradedraw

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TradeAction {
    BUY, SELL
}

enum class AutoTradeMode {
    AUTONOMOUS,      // Ejecuta clics automáticos
    SEMIAUTOMATIC,   // Alerta y prepara dibujos, usuario opera
    DISABLED         // Apagado
}

enum class AutonomousSubMode {
    CONSERVATIVE,    // Con gestión de riesgo estándar (Stop Loss, Take Profit, Cooldown)
    YOLO,            // Sin Stop Loss ni límites, operativa continua y desatendida
    SNIPER           // Modo Francotirador: Stake plano (M0), SL 2 derrotas, TP 2 victorias, máx 3 ops por sesión
}

class TradingEngine(
    private val context: Context,
    private val drawingView: CustomDrawingView,
    val riskManager: RiskManager,
    var calibrationManager: CalibrationManager? = null,
    val aiClient: AIClient = AIClient(context)
) {
    val chartViewportController = ChartViewportController(context, riskManager)
    val agentController = AutonomousAgentController(context, this, riskManager, chartViewportController)

    var mode: AutoTradeMode = AutoTradeMode.DISABLED
        set(value) {
            field = value
            if (value == AutoTradeMode.AUTONOMOUS) {
                agentController.start()
            } else {
                agentController.stop()
            }
        }

    var autonomousSubMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE
        set(value) {
            field = value
            syntheticCandleEngine.subMode = value
        }

    var timeframe: CandleTimeframe = CandleTimeframe.M1
        set(value) {
            field = value
            syntheticCandleEngine.timeframe = value
            riskManager.timeframe = value
        }

    @Volatile
    var sniperShutdownReason: String? = null

    fun checkSniperSessionLimits() {
        if (autonomousSubMode == AutonomousSubMode.SNIPER) {
            val (canTrade, reason) = riskManager.canExecuteTrade(mode, autonomousSubMode)
            if (!canTrade && (reason.contains("alcanzado", ignoreCase = true) || reason.contains("finalizada", ignoreCase = true) || reason.contains("Límite", ignoreCase = true))) {
                mode = AutoTradeMode.DISABLED
                sniperShutdownReason = reason
                handler.post {
                    Toast.makeText(context, "🛑 $reason", Toast.LENGTH_LONG).show()
                    OverlayService.instance?.updateHUDView()
                }
            }
        }
    }
    val syntheticCandleEngine = SyntheticCandleEngine()
    val adaptiveLearningEngine = AdaptiveLearningEngine()

    var strategy: AutoTradeStrategy = AutoTradeStrategy.AUTO_ADAPTIVE
        set(value) {
            field = value
            syntheticCandleEngine.currentStrategy = value
        }
    var debugModeEnabled: Boolean = false

    val visionAnalyzer = VisionAnalyzer()
    private val autoDrawEngine = AutoDrawEngine(drawingView)
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 1000L

    private var pendingTradeHasObservedWin: Boolean = false
    private var pendingTradeRecordedCandleCloseY: Float = 0f
    private val isTradeResolving = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastFeedbackEmitTime: Long = 0L
    private var lastTradeResolutionTime: Long = 0L

    var latestAnalysisResult: VisionAnalysisResult? = null
        private set
    var latestAIResult: AIAnalysisResult? = null
        private set
    var latestAITimestamp: Long = 0L
        private set
    var framesAnalyzedCount: Long = 0L
        private set

    var onSignalListener: ((TradeAction, String) -> Unit)? = null
    var onTradeExecutedListener: ((TradeAction, Boolean) -> Unit)? = null
    var onFrameProcessedListener: ((VisionAnalysisResult) -> Unit)? = null

    init {
        syntheticCandleEngine.currentStrategy = strategy
        // Limpiar cualquier figura residual previa del bot (soportes/resistencias automáticas)
        drawingView.clearBotShapes()

        // Conectar callback: cuando el usuario arrastra una línea del bot, bloquearla
        drawingView.onBotShapeDragged = { key ->
            autoDrawEngine.lockLine(key)
            handler.post {
                Toast.makeText(context, "Línea $key bloqueada [Manual]", Toast.LENGTH_SHORT).show()
            }
        }

        // Conectar señales generadas matemáticamente por el motor Headless WebSocket
        syntheticCandleEngine.onSignalGenerated = { action, reason ->
            if (mode == AutoTradeMode.AUTONOMOUS) {
                executeHeadlessTrade(action, reason)
            } else if (mode == AutoTradeMode.SEMIAUTOMATIC) {
                currentActiveSignal = ActiveSignal(
                    action = action,
                    title = "Señal Sniper WebSocket",
                    reason = reason,
                    timestamp = System.currentTimeMillis()
                )
                handler.post {
                    onSignalListener?.invoke(action, reason)
                }
            }
        }
    }

    /** Desbloquea todas las líneas para que la IA las recalcule */
    fun unlockAllLines() {
        autoDrawEngine.unlockAllLines()
    }

    fun hasLockedLines(): Boolean = autoDrawEngine.hasLockedLines()

    data class ActiveSignal(
        val action: TradeAction,
        val title: String,
        val reason: String,
        val timestamp: Long,
        val expirySeconds: Int = 30
    )

    data class EngineReasoning(
        val patternName: String,
        val probabilityPct: Int,
        val srFilterStatus: String,
        val actionPlan: String,
        val isFavorable: Boolean
    )

    var currentActiveSignal: ActiveSignal? = null
        private set

    fun clearActiveSignal() {
        currentActiveSignal = null
    }

    var latestMarketTick: MarketTick? = null
        private set

    /**
     * Umbral de frescura del feed WebSocket (ms). Un tick más viejo que esto se considera rancio
     * y veta la operación: nunca se degrada a otra fuente de datos.
     */
    private val FEED_MAX_AGE_MS = BinomoWebSocketClient.DEFAULT_FRESHNESS_MS

    /**
     * Proveedor de frescura del feed. Lo inyecta OverlayService con el cliente WebSocket real.
     * Sin proveedor (= null) o con resultado false, el motor veta por fail-closed.
     */
    var feedFreshnessProvider: (() -> Boolean)? = null

    /** true si el feed WebSocket está conectado y con tick reciente (<5000 ms). */
    fun isFeedFresh(): Boolean = feedFreshnessProvider?.invoke() ?: false

    /**
     * Proveedor de congelación del precio. Lo inyecta OverlayService con el cliente real.
     *
     * Cubre el caso "el feed está fresco pero el emisor no mueve el precio" (índice
     * sintético pegado como `Z-CRY/IDX`, rango relativo ~5e-10). Sin esta guarda el bot
     * decide CALL/PUT sobre una línea plana, que es equivalente a lanzar una moneda.
     *
     * Fail-closed al revés que la frescura: `null` (sin proveedor) NO veta, porque en
     * tests y en modo headless simulado no hay cliente WebSocket real.
     */
    var priceFrozenProvider: (() -> Boolean)? = null

    /** true si el emisor entrega ticks a ritmo normal pero el precio está inmóvil. */
    fun isPriceFrozen(): Boolean = priceFrozenProvider?.invoke() ?: false

    /**
     * Invocado cuando llega un nuevo micro-tick en tiempo real vía WebSocket.
     */
    fun onMarketTick(tick: MarketTick) {
        latestMarketTick = tick
        syntheticCandleEngine.subMode = autonomousSubMode
        syntheticCandleEngine.onNewTick(tick)

        // El análisis se publica desde el motor sintético de ticks (fuente única WebSocket).
        // Sin esto, latestAnalysisResult solo se poblaba en onNewFrame() (visión retirada por
        // consumo de batería) y el HUD/bridge reportaban ceros aunque el feed estuviera vivo.
        latestAnalysisResult = buildAnalysisFromSyntheticEngine(tick)

        // En modo Headless (sin frames de pantalla capturados), resolver trade por tiempo y balance de Binomo
        if (framesAnalyzedCount == 0L && riskManager.hasPendingTrade) {
            checkHeadlessTradeResolution(tick)
        }
    }

    /**
     * Construye el análisis a partir del motor sintético de ticks y del último micro-tick.
     * Fuente única de verdad en modo WebSocket puro: no depende de la captura de pantalla.
     */
    private fun buildAnalysisFromSyntheticEngine(tick: MarketTick): VisionAnalysisResult {
        val distSup = syntheticCandleEngine.distanceToSupportRatio
        val distRes = syntheticCandleEngine.distanceToResistanceRatio
        return VisionAnalysisResult(
            trend = syntheticCandleEngine.detectedTrend,
            distanceToSupportRatio = distSup,
            distanceToResistanceRatio = distRes,
            touchesSupport = distSup <= 0.15f,
            touchesResistance = distRes <= 0.15f,
            isNearSupportZone = distSup <= 0.30f,
            isNearResistanceZone = distRes <= 0.30f,
            tickVelocityNormalized = tick.velocity,
            isBullishImpulse = tick.isBullishImpulse,
            isBearishImpulse = tick.isBearishImpulse,
            candleSecond = tick.candleSecond
        )
    }

    fun onNewFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < PROCESS_INTERVAL_MS) return
        lastProcessTime = now
        framesAnalyzedCount++

        // 1. Obtener niveles de soporte/resistencia dibujados en TradeDraw
        val (supports, resistances) = drawingView.getSupportResistanceYLevels()

        // 2. Analizar frame visual con visión HSV (precio actual, velas, tendencia)
        val analysis = visionAnalyzer.analyzeChart(bitmap, supports, resistances, context, debugModeEnabled)
        latestAnalysisResult = analysis
        syntheticCandleEngine.updateVisionTrend(analysis.trend)

        // 3. Auto-dibujar escenario técnico en TradeDraw según la estrategia
        handler.post {
            autoDrawEngine.updateTechnicalDrawings(strategy, analysis)
            onFrameProcessedListener?.invoke(analysis)
        }

        // 4. Si hay una operación abierta en curso, supervisar el ciclo de vida y liquidación con Binomo
        if (riskManager.hasPendingTrade) {
            val elapsedSec = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000

            // Actualizar en vivo la línea de entrada (Verde si va ganando ITM, Roja si va perdiendo OTM)
            handler.post {
                autoDrawEngine.updateTradeEntryLiveStatus(analysis.currentPriceY)
            }

            val entryY = riskManager.pendingTradeEntryPriceY
            val exitY = analysis.currentPriceY
            val action = riskManager.pendingTradeAction
            val baseBalance = riskManager.pendingTradeBaseBalance
            val pendingConfidence = riskManager.pendingTradeConfidence
            val tradeStartMs = riskManager.pendingTradeStartTime

            // Capturar el precio de cierre en la ventana de expiración (:58 - :02) tras al menos 40s de trade
            if (elapsedSec >= 40 && (analysis.candleSecond in 0..2 || analysis.candleSecond in 58..59)) {
                if (pendingTradeRecordedCandleCloseY == 0f && analysis.currentPriceY > 0f) {
                    pendingTradeRecordedCandleCloseY = analysis.currentPriceY
                }
            }

            // Capturar el precio de cierre al expirar la vela (:58-:02) tras al menos 45s de trade
            if (elapsedSec >= 45 && (analysis.candleSecond in 0..2 || analysis.candleSecond in 58..59)) {
                if (pendingTradeRecordedCandleCloseY == 0f && analysis.currentPriceY > 0f) {
                    pendingTradeRecordedCandleCloseY = analysis.currentPriceY
                }
            }

            // Ventana de resolución estricta al EXPIRAR la vela (según timeframe: 60s o 300s + ventana de liquidación del broker)
            val expDurationSec = timeframe.seconds
            val isExpired = elapsedSec >= (expDurationSec + 2)
            if (isExpired) {
                var isWin: Boolean? = null
                var isTie: Boolean = false
                var method = ""

                val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance() 
                    ?: AutoTradeAccessibilityService.latestObservedBalance

                // 1. Verificación por Saldo Real Inmutable de Binomo
                if (baseBalance > 0.0 && currentBal > 0.0) {
                    val diff = currentBal - baseBalance
                    if (diff > 10.0 && diff <= baseBalance * 0.5) {
                        // Binomo acreditó las ganancias (+1 W) de forma inmediata
                        isWin = true
                        method = "SALDO (+) Ganancia acreditada por Binomo: Diff=+$diff COP (${elapsedSec}s)"
                    } else if (diff < -10.0 && elapsedSec >= (expDurationSec + 12)) {
                        // Solo confirmar pérdida tras ventana completa de liquidación para no confundir retrasos del broker
                        isWin = false
                        method = "SALDO (-) Pérdida confirmada tras liquidación: Diff=$diff COP (${elapsedSec}s)"
                    } else if (Math.abs(diff) <= 10.0 && elapsedSec >= (expDurationSec + 15)) {
                        // Saldo inalterado tras liquidación: La orden NUNCA fue procesada por Binomo (clic no recibido)
                        isTie = true
                        method = "ORDEN NO PROCESADA (Diff=$diff -> Saldo inalterado tras ${elapsedSec}s)"
                    }
                } else if (elapsedSec >= (expDurationSec + 15)) {
                    // Si no hubo saldo legible tras timeout, cancelar como empate preventivo sin alterar equity
                    isTie = true
                    method = "TIMEOUT ${expDurationSec + 15}s (Sin saldo legible -> Cancelación preventiva)"
                }

                if (isWin != null || isTie) {
                    // Bloqueo Atómico: Solo UN frame puede liquidar la operación activa.
                    if (!isTradeResolving.compareAndSet(false, true)) {
                        return
                    }
                    android.util.Log.d("TradingEngine", "Liquidación atómica de trade único: Win=$isWin, Tie=$isTie [$method]")
                    val finalWin = isWin ?: false
                    handler.post {
                        pendingTradeHasObservedWin = false
                        pendingTradeRecordedCandleCloseY = 0f
                        val curTrend = analysis.trend.name
                        val supDist = analysis.distanceToSupportRatio
                        val resDist = analysis.distanceToResistanceRatio
                        val tickVel = analysis.tickVelocityNormalized
                        val imp = if (analysis.isBullishImpulse) "BULLISH" else if (analysis.isBearishImpulse) "BEARISH" else "NEUTRAL"
                        val reg = if (analysis.isMarketSideways) "SIDEWAYS" else if (analysis.isConsolidationTight) "TIGHT" else if (analysis.isDojiOrLowVolume) "DOJI" else "TRENDING"
                        val sec = analysis.candleSecond

                        if (isTie) {
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getCurrentInvestmentAmount(), baseBalance, "TIE", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "TIE", tradeStartMs)
                            riskManager.recordTradeVoid()
                            autoDrawEngine.clearTradeEntry()
                            Toast.makeText(context, "⚪ EMPATE EN BINOMO (Reembolso de capital)", Toast.LENGTH_LONG).show()
                        } else if (finalWin) {
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getCurrentInvestmentAmount(), baseBalance, "WIN", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "WIN", tradeStartMs)
                            riskManager.recordTradeWin()
                            adaptiveLearningEngine.recordTradeOutcome(true, context)
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, true)
                        } else {
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getCurrentInvestmentAmount(), baseBalance, "LOSS", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "LOSS", tradeStartMs)
                            riskManager.recordTradeLoss()
                            adaptiveLearningEngine.recordTradeOutcome(false, context)
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "⚠️ OPERACIÓN PERDIDA (+1 L)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, false)
                        }
                        isTradeResolving.set(false)
                        lastTradeResolutionTime = System.currentTimeMillis()
                        checkSniperSessionLimits()
                    }
                }
            }
        }

        val timeSinceLastResolution = System.currentTimeMillis() - lastTradeResolutionTime
        // Espaciado inteligente: Si la última operación fue derrota, enfriamiento de 45s para no repetir en la misma micro-tendencia
        val minSpacingMs = if (riskManager.currentLossStreak > 0) 45000L else 12000L
        val isSpacingCooldown = lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs

        // 5. Evaluar señal de trading puramente local y cuantitativa (0ms latencia, sin llamadas remotas lentas)
        if (!riskManager.hasPendingTrade && !isSpacingCooldown && mode != AutoTradeMode.DISABLED) {
            val localSignal = evaluateStrategySignal(strategy, analysis, supports.isNotEmpty() || resistances.isNotEmpty())

            if (localSignal != null && !riskManager.hasPendingTrade) {
                val sec = analysis.candleSecond
                val isTimingVetoed = sec in 15..55 || analysis.isTimingVetoed
                val isStrictTimingWindow = sec in 58..59 || sec in 0..3 || analysis.isSniperTimingWindow || analysis.isSniperZeroSecondWindow
                val isChoppy = syntheticCandleEngine.isChoppinessDetected() || analysis.isMicroRangeChoppy
                val isTickVelocityConfirmed = when (localSignal) {
                    TradeAction.BUY -> !analysis.isBearishImpulse || analysis.tickVelocityNormalized >= -0.08f
                    TradeAction.SELL -> !analysis.isBullishImpulse || analysis.tickVelocityNormalized <= 0.08f
                }

                if (isChoppy) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal suprimida por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
                } else if (isTimingVetoed) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal bloqueada por VETO de timing :15-:55 (⏱ ${sec}s)")
                } else if (!isStrictTimingWindow) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal pospuesta: fuera de ventana sniper :00 (:58-:03) (⏱ ${sec}s)")
                } else if (!isTickVelocityConfirmed) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal bloqueada por velocidad de tick adversa (velNorm=${analysis.tickVelocityNormalized}, velY=${analysis.tickVelocityY})")
                } else {
                    val description = if (lastSignalReason.isNotBlank()) lastSignalReason else "Estrategia Local (${strategy.name})"
                    val localConfidence = when {
                        analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut -> 0.95f
                        analysis.isRejectionCall || analysis.isRejectionPut || analysis.hasBottomRejectionWick || analysis.hasTopRejectionWick -> 0.90f
                        analysis.isEngulfingCall || analysis.isEngulfingPut -> 0.85f
                        analysis.isChoqueCall || analysis.isChoquePut || analysis.isChoquePullbackCall || analysis.isChoquePullbackPut -> 0.80f
                        analysis.is3VelasCall || analysis.is3VelasPut || analysis.isExhaustion3CandlesCall || analysis.isExhaustion3CandlesPut -> 0.75f
                        localSignal == TradeAction.BUY -> Math.max(analysis.confluenceScoreCall, Math.max(analysis.signalPowerCall, if (analysis.isCallSignal) analysis.signalScore else 50)) / 100f
                        localSignal == TradeAction.SELL -> Math.max(analysis.confluenceScorePut, Math.max(analysis.signalPowerPut, if (analysis.isPutSignal) analysis.signalScore else 50)) / 100f
                        else -> 0.80f
                    }
                    handleSignal(localSignal, analysis, bitmap, description, localConfidence)
                }
            }
        }
    }

    var lastSignalReason: String = ""
        private set

    fun evaluateStrategySignal(
        strategy: AutoTradeStrategy = this.strategy,
        analysis: VisionAnalysisResult,
        hasDrawnLines: Boolean = false
    ): TradeAction? {
        val (action, reason) = evaluateStrategySignalWithReason(
            strategy = strategy,
            analysis = analysis,
            hasDrawnLines = hasDrawnLines,
            syntheticEngine = syntheticCandleEngine,
            subMode = autonomousSubMode,
            latestTick = latestMarketTick
        )
        lastSignalReason = reason
        return action
    }

    companion object {
        fun evaluateStrategySignal(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            hasDrawnLines: Boolean = false,
            syntheticEngine: SyntheticCandleEngine? = null,
            subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
            latestTick: MarketTick? = null
        ): TradeAction? {
            val (action, _) = evaluateStrategySignalWithReason(strategy, analysis, hasDrawnLines, syntheticEngine, subMode, latestTick)
            return action
        }

        fun evaluateSniperConfluences(
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine?,
            latestTick: MarketTick?
        ): Pair<TradeAction?, String> {
            // Confluencia 1: Activo Forex Real válido (Veto estricto a Sintéticos y OTC)
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic) {
                return Pair(null, "⚠️ Veto Francotirador: Activo sintético/OTC ($currentAsset) prohibido para dinero real. Selecciona un par Forex real en Binomo")
            }
            val classification = AutoTradeAccessibilityService.classifyAsset(currentAsset)
            if (classification != AssetClassification.FOREX_REAL) {
                return Pair(null, "⚠️ Francotirador: Activo ($currentAsset) no es un par Forex real válido. Selecciona EUR/USD, GBP/USD, etc.")
            }

            // Confluencia 4 (Timing): Entrada estricta en el segundo :58s-:59s (o fin de ciclo de vela según timeframe)
            val tf = syntheticEngine?.timeframe ?: CandleTimeframe.M1
            val ts = latestTick?.timestampMs ?: System.currentTimeMillis()
            val inTimingWindow = tf.isSniperTimingWindow(ts) || (analysis.candleSecond in 58..59 && tf == CandleTimeframe.M1)
            if (!inTimingWindow) {
                val cycleSec = tf.getCycleSecond(ts)
                val cycleRem = tf.seconds - cycleSec
                val remMin = cycleRem / 60
                val remSec = cycleRem % 60
                val remStr = if (tf == CandleTimeframe.M5) "%d:%02d".format(Locale.US, remMin, remSec) else ":%02ds".format(Locale.US, remSec)
                return Pair(null, "⏳ Francotirador en espera: timing estricto :58s-:59s [${tf.label}] (restante: $remStr)")
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
            val velCallFavor = (velNorm > 0.0001f || tickVel > 0.0001f || analysis.isBullishImpulse) &&
                !analysis.isBearishImpulse && velNorm >= -0.0001f && tickVel >= -0.0001f
            val velPutFavor = (velNorm < -0.0001f || tickVel < -0.0001f || analysis.isBearishImpulse) &&
                !analysis.isBullishImpulse && velNorm <= 0.0001f && tickVel <= 0.0001f

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
            hasDrawnLines: Boolean = false,
            syntheticEngine: SyntheticCandleEngine? = null,
            subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
            latestTick: MarketTick? = null
        ): Pair<TradeAction?, String> {
            // MODO FRANCOTIRADOR: Gatillo de 4 Confluencias Simultáneas
            if (subMode == AutonomousSubMode.SNIPER) {
                return evaluateSniperConfluences(analysis, syntheticEngine, latestTick)
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
            val isTimingVetoed = MarketTickFilters.isTimingVetoed(sec) // :15 a :55
            val isInstitutionalTrap = analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut
            val isSniperPullbackTrigger = (sec in 1..3 || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalCall || analysis.isPullbackAgainstSignalPut || analysis.isPullbackSniperCall || analysis.isPullbackSniperPut)

            // Veto universal de entrada tardía (:15 a :55)
            if (isTimingVetoed && !isInstitutionalTrap) {
                return Pair(null, "⏳ Entrada tardía - Veto Timing Estricto (:15-:55): Fuera de ventana sniper :00 (:58-:03) (⏱ ${sec}s)")
            }

            // Veto de entrada si está fuera de la ventana estricta :58-:03 (salvo trampas institucionales)
            if (!isStrictTimingWindow && !isInstitutionalTrap && !isSniperPullbackTrigger) {
                return Pair(null, "⏳ Entrada tardía (${sec}s): Fuera de ventana sniper :00 (:58-:03)")
            }

            val isSniperPullbackCallTrigger = (sec in 1..3 || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalCall || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.GREEN && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY >= analysis.candleList[1].bodyTopY - 3f && analysis.currentPriceY <= analysis.candleList[1].bodyTopY + analysis.candleList[1].bodyHeight * 0.55f)) &&
                !analysis.isDojiOrLowVolume

            val isSniperPullbackPutTrigger = (sec in 1..3 || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalPut || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.RED && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY <= analysis.candleList[1].bodyBottomY + 3f && analysis.currentPriceY >= analysis.candleList[1].bodyBottomY - analysis.candleList[1].bodyHeight * 0.55f)) &&
                !analysis.isDojiOrLowVolume

            val rawResult = when (strategy) {
                AutoTradeStrategy.AUTO_ADAPTIVE -> {
                    // Modo Automático Total: Confluencia Multi-Factor + Sniping de Entrada
                    val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                    val inUptrend = analysis.trend == TrendDirection.UPTREND
                    val isSideways = analysis.trend == TrendDirection.SIDEWAYS || analysis.isMarketSideways || analysis.isConsolidationTight

                    when {
                        // 1. Prioridad Máxima: Falso Rompimiento / Trampa Institucional en S/R (95% confluencia)
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isFalseBreakoutCall -> Pair(TradeAction.BUY, "🎯 Auto [Trampa en Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.isFalseBreakoutPut -> Pair(TradeAction.SELL, "🎯 Auto [Trampa en Resistencia | ⏱ ${sec}s] -> PUT")

                        // 1b. Reversión Contra-Tendencia Cuantitativa por Sobreextensión en Zonas Clave S/R
                        !inUptrend && (analysis.isNearResistanceZone || analysis.touchesResistance || analysis.distanceToResistanceRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.20f)) &&
                        (syntheticEngine?.isBullishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN) || analysis.isPriceNearTop) &&
                        (!analysis.hasStrongMomentumUp && !analysis.isValidBreakoutCall && (analysis.tickVelocityNormalized <= 0.05f || analysis.isBearishImpulse || analysis.hasTopRejectionWick || analysis.isRejectionPut || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))) -> {
                            val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                            Pair(TradeAction.SELL, "🎯 Auto [Reversión por Sobreextensión en Resistencia$rsiStr | ⏱ ${sec}s] -> PUT")
                        }
                        !inDowntrend && (analysis.isNearSupportZone || analysis.touchesSupport || analysis.distanceToSupportRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.20f)) &&
                        (syntheticEngine?.isBearishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED) || analysis.isPriceNearBottom) &&
                        (!analysis.hasStrongMomentumDown && !analysis.isValidBreakoutPut && (analysis.tickVelocityNormalized >= -0.05f || analysis.isBullishImpulse || analysis.hasBottomRejectionWick || analysis.isRejectionCall || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))) -> {
                            val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                            Pair(TradeAction.BUY, "🎯 Auto [Reversión por Sobreextensión en Soporte$rsiStr | ⏱ ${sec}s] -> CALL")
                        }

                        // 2. Sniper Pullback Entry Timing (:01s-:05s tras vela de señal fuerte)
                        !inDowntrend && !analysis.hasStrongMomentumDown && isSniperPullbackCallTrigger ->
                            Pair(TradeAction.BUY, "🎯 Auto [Sniper Pullback :0${sec}s | Retroceso tras vela fuerte] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && isSniperPullbackPutTrigger ->
                            Pair(TradeAction.SELL, "🎯 Auto [Sniper Pullback :0${sec}s | Retroceso tras vela fuerte] -> PUT")

                        // 3. Rompimiento Válido S/R (>50% cuerpo fuera, mecha opuesta <20%)
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isValidBreakoutCall && !analysis.isDojiOrLowVolume ->
                            Pair(TradeAction.BUY, "🎯 Auto [Rompimiento Válido Resistencia | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.isValidBreakoutPut && !analysis.isDojiOrLowVolume ->
                            Pair(TradeAction.SELL, "🎯 Auto [Rompimiento Válido Soporte | ⏱ ${sec}s] -> PUT")

                        // 2. Mechas de Rechazo y Absorción en S/R con Sniping de Pullback (90% confluencia)
                        !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) &&
                            (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                            Pair(TradeAction.BUY, "🎯 Auto [Mecha Rechazo Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) &&
                            (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                            Pair(TradeAction.SELL, "🎯 Auto [Mecha Rechazo Resistencia | ⏱ ${sec}s] -> PUT")

                        // 3. Patrón Vela Envolvente en S/R (85% confluencia) con sniping de entrada
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isEngulfingCall &&
                            (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) -> Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.isEngulfingPut &&
                            (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) -> Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | ⏱ ${sec}s] -> PUT")

                        // 4. Choque / Retest tras Rompimiento (80% confluencia)
                        !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isChoqueCall || analysis.isChoquePullbackCall) -> Pair(TradeAction.BUY, "🎯 Auto [Choque / Pullback Alcista | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isChoquePut || analysis.isChoquePullbackPut) -> Pair(TradeAction.SELL, "🎯 Auto [Choque / Pullback Bajista | ⏱ ${sec}s] -> PUT")

                        // 5. Agotamiento de 3 Velas Consecutivas (75% confluencia)
                        !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) -> Pair(TradeAction.BUY, "🎯 Auto [Agotamiento 3 Rojas Alcista | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) -> Pair(TradeAction.SELL, "🎯 Auto [Agotamiento 3 Verdes Bajista | ⏱ ${sec}s] -> PUT")

                        // 6. Rebote S/R Clásico con Sniping de Entrada (70% confluencia)
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.touchesSupport && analysis.isPullbackSniperCall && !isSideways -> Pair(TradeAction.BUY, "🎯 Auto [Rebote en Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.touchesResistance && analysis.isPullbackSniperPut && !isSideways -> Pair(TradeAction.SELL, "🎯 Auto [Rebote en Resistencia | ⏱ ${sec}s] -> PUT")

                        // 7. Impulso y Confluencia Cuantitativa Alta >= 80% — estrictamente a favor de tendencia confirmada
                        inUptrend && !analysis.hasStrongMomentumDown && (analysis.confluenceScoreCall >= 80 || analysis.signalPowerCall >= 80 || (analysis.isCallSignal && analysis.signalScore >= 80)) -> {
                            if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                val score = Math.max(analysis.confluenceScoreCall, analysis.signalPowerCall)
                                Pair(TradeAction.BUY, "🎯 Auto [Confluencia Fuerte Alcista ($score%) | ⏱ ${sec}s] -> CALL")
                            }
                        }
                        inDowntrend && !analysis.hasStrongMomentumUp && (analysis.confluenceScorePut >= 80 || analysis.signalPowerPut >= 80 || (analysis.isPutSignal && analysis.signalScore >= 80)) -> {
                            if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                val score = Math.max(analysis.confluenceScorePut, analysis.signalPowerPut)
                                Pair(TradeAction.SELL, "🎯 Auto [Confluencia Fuerte Bajista ($score%) | ⏱ ${sec}s] -> PUT")
                            }
                        }
                        else -> Pair(null, "")
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

    fun getEngineReasoning(): EngineReasoning {
        val analysis = latestAnalysisResult
        val tick = latestMarketTick
        val wsTrend = syntheticCandleEngine.detectedTrend
        val sup = syntheticCandleEngine.dynamicSupportPrice
        val res = syntheticCandleEngine.dynamicResistancePrice

        // 1. Patrón Detectado
        val pattern = when {
            analysis?.isFalseBreakoutCall == true || analysis?.isFalseBreakoutPut == true -> "⚡ Trampa Institucional / Fakeout S/R"
            analysis?.isRejectionCall == true || analysis?.isRejectionPut == true || analysis?.hasBottomRejectionWick == true || analysis?.hasTopRejectionWick == true -> "🕯️ Mecha de Rechazo en Nivel S/R"
            analysis?.isEngulfingCall == true || analysis?.isEngulfingPut == true -> "⚡ Vela Envolvente de Reversión"
            analysis?.is3VelasCall == true || analysis?.is3VelasPut == true || analysis?.isExhaustion3CandlesCall == true || analysis?.isExhaustion3CandlesPut == true -> "📉 Agotamiento de 3 Velas Consecutivas"
            analysis?.isChoqueCall == true || analysis?.isChoquePut == true || analysis?.isChoquePullbackCall == true || analysis?.isChoquePullbackPut == true -> "🎯 Choque con Nivel Roto (Pullback / Retest)"
            analysis?.isValidBreakoutCall == true || analysis?.isValidBreakoutPut == true -> "💥 Breakout Validado (>50% fuera de nivel)"
            analysis?.trend == TrendDirection.UPTREND -> "📈 Flujo Continuo Alcista (Higher Highs)"
            analysis?.trend == TrendDirection.DOWNTREND -> "📉 Flujo Continuo Bajista (Lower Lows)"
            wsTrend == TrendDirection.UPTREND -> "📈 Tendencia Alcista en Velas Sintéticas 1m"
            wsTrend == TrendDirection.DOWNTREND -> "📉 Tendencia Bajista en Velas Sintéticas 1m"
            analysis?.isMarketSideways == true -> "📊 Rango Lateral / Dojis de Indecisión"
            wsTrend == TrendDirection.SIDEWAYS -> "📊 Rango Lateral Cuantitativo / Esperando Expansión"
            else -> "🔍 Monitoreando Acción del Precio"
        }

        // 2. Probabilidad Estimada
        val prob = when {
            analysis != null -> {
                val base = if (analysis.trend == TrendDirection.UPTREND) analysis.signalPowerCall else if (analysis.trend == TrendDirection.DOWNTREND) analysis.signalPowerPut else 50
                val conf = Math.max(analysis.confluenceScoreCall, analysis.confluenceScorePut)
                Math.max(base, conf).coerceIn(50, 96)
            }
            tick != null -> {
                val vel = Math.abs(tick.velocity)
                val trendBonus = if (wsTrend != TrendDirection.SIDEWAYS) 12 else 0
                val baseVel = if (vel > 0.0003f) 78 else if (vel > 0.0001f) 68 else 58
                (baseVel + trendBonus).coerceIn(55, 92)
            }
            else -> 65
        }

        // 3. Estado del Filtro S/R
        val srStatus = when {
            analysis != null -> {
                if (analysis.touchesSupport) "🟢 Toque Directo en Soporte [CALL Óptimo]"
                else if (analysis.touchesResistance) "🔴 Toque Directo en Resistencia [PUT Óptimo]"
                else if (analysis.isNearSupportZone) "🟢 Cerca de Soporte (${(analysis.distanceToSupportRatio * 100).toInt()}% canal)"
                else if (analysis.isNearResistanceZone) "🔴 Cerca de Resistencia (${(analysis.distanceToResistanceRatio * 100).toInt()}% canal)"
                else "⚪ Centro del Canal S/R (Neutral)"
            }
            sup > 0.0 && res > 0.0 && tick != null -> {
                val range = (res - sup).coerceAtLeast(0.00001)
                val distS = ((tick.price - sup) / range).coerceIn(0.0, 1.0)
                if (distS <= 0.30) "🟢 Cerca de Soporte Cuantitativo (${(distS * 100).toInt()}%)"
                else if (distS >= 0.70) "🔴 Cerca de Resistencia Cuantitativa (${((1.0 - distS) * 100).toInt()}%)"
                else "⚪ Rango Central (Dist S: ${(distS * 100).toInt()}%)"
            }
            else -> "⚪ Calculando niveles S/R..."
        }

        // 4. Plan de Acción Táctico
        val plan = when {
            riskManager.hasPendingTrade -> {
                val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
                val act = if (riskManager.pendingTradeAction == TradeAction.BUY) "CALL ▲" else "PUT ▼"
                "⏳ Trade Activo: $act (${elapsed}s transcurridos) · Liquidando en vela :00s"
            }
            (syntheticCandleEngine.cachedChoppiness && autonomousSubMode != AutonomousSubMode.YOLO) -> {
                "🛡️ Filtro Anti-Chop Activo: Consolidación/CHOP elevado. Esperando ruptura limpia con volumen."
            }
            analysis?.trend == TrendDirection.UPTREND || wsTrend == TrendDirection.UPTREND -> {
                "🎯 Plan: Buscar retroceso leve a soporte para ejecutar CALL al segundo :58s - :01s."
            }
            analysis?.trend == TrendDirection.DOWNTREND || wsTrend == TrendDirection.DOWNTREND -> {
                "🎯 Plan: Buscar retroceso leve a resistencia para ejecutar PUT al segundo :58s - :01s."
            }
            else -> {
                val distS = syntheticCandleEngine.distanceToSupportRatio
                val distR = syntheticCandleEngine.distanceToResistanceRatio
                when {
                    distS <= 0.30f -> "🎯 Plan: Cerca de Soporte (${(distS * 100).toInt()}%). Esperando gatillo CALL al segundo :58s - :03s."
                    distR <= 0.30f -> "🎯 Plan: Cerca de Resistencia (${(distR * 100).toInt()}%). Esperando gatillo PUT al segundo :58s - :03s."
                    else -> "🎯 Plan: Esperando confirmación de nivel S/R o impulso direccional de ticks."
                }
            }
        }

        val isFavorable = !riskManager.hasPendingTrade && (autonomousSubMode == AutonomousSubMode.YOLO || (!syntheticCandleEngine.cachedChoppiness && analysis?.isMarketSideways != true))
        return EngineReasoning(pattern, prob, srStatus, plan, isFavorable)
    }

    fun getStrategyStatusHint(): String {
        val isAccessOk = AutoTradeAccessibilityService.instance != null || AutoTradeAccessibilityService.isAccessibilityPermissionGranted(context)
        if (!isAccessOk && mode == AutoTradeMode.AUTONOMOUS) {
            return "⚠️ Accesibilidad DESACTIVADA (Clics bloqueados en Android)"
        }

        if (sniperShutdownReason != null) {
            return "🛑 $sniperShutdownReason · Toca [MODO] para nueva sesión"
        }

        val (canTradeStatus, blockReason) = riskManager.canExecuteTrade(mode, autonomousSubMode)
        if (!canTradeStatus && !riskManager.hasPendingTrade && mode == AutoTradeMode.AUTONOMOUS) {
            val requiresManualResume = blockReason.contains("Stop Loss", ignoreCase = true) || blockReason.contains("Take Profit", ignoreCase = true) || blockReason.contains("Límite", ignoreCase = true)
            return if (requiresManualResume) "🛑 $blockReason · Toca [MODO] para reanudar" else "⏳ $blockReason"
        }

        if (riskManager.hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
            val actionName = if (riskManager.pendingTradeAction == TradeAction.BUY) "COMPRA" else "VENTA"
            return "⏳ Operación en curso ($actionName ${elapsed}s) · Esperando resultado..."
        }

        if (aiClient.isEnabled) {
            val ai = latestAIResult
            val modelShort = aiClient.model.substringAfterLast('/')
            return if (ai != null && ai.isSuccess) {
                val pct = (ai.confidence * 100).toInt()
                val act = ai.action?.name ?: "WAIT"
                "🧠 IA [$modelShort]: $act ($pct%) · ${ai.reason.take(28)}"
            } else if (ai != null && !ai.isSuccess) {
                "⚠️ IA: ${ai.errorMessage ?: "Esperando conexión..."}"
            } else {
                "🧠 IA [$modelShort]: Analizando pantalla..."
            }
        }

        val (supports, resistances) = drawingView.getSupportResistanceYLevels()
        val analysis = latestAnalysisResult

        if (analysis != null && analysis.isMarketSideways) {
            return "⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad"
        }

        return when (strategy) {
            AutoTradeStrategy.AUTO_ADAPTIVE -> {
                "🤖 Auto Adaptativo: Vigilando todas las estrategias en tiempo real..."
            }
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                "🔍 Vigilando rebote en Soporte / Resistencia en vivo..."
            }
            AutoTradeStrategy.MT_REJECTION -> {
                if (analysis != null && (analysis.hasTopRejectionWick || analysis.hasBottomRejectionWick || analysis.isRejectionCall || analysis.isRejectionPut)) {
                    "⚡ Mecha de rechazo detectada! Evaluando entrada..."
                } else {
                    "🔍 MT: Buscando mechas de rechazo en niveles..."
                }
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                if (analysis != null && (analysis.isChoqueCall || analysis.isChoquePut || analysis.isChoquePullbackCall || analysis.isChoquePullbackPut)) {
                    "⚡ Choque con nivel roto detectado! Preparando trade..."
                } else {
                    "🔍 MT: Buscando choque de máximos/mínimos..."
                }
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                if (analysis != null && (analysis.is3VelasCall || analysis.is3VelasPut || analysis.isExhaustion3CandlesCall || analysis.isExhaustion3CandlesPut || analysis.consecutiveCount >= 2)) {
                    "📊 MT: Racha ${analysis.consecutiveCount} velas. Vigilando agotamiento..."
                } else {
                    "🔍 MT: Esperando patrón de 3 velas..."
                }
            }
            AutoTradeStrategy.MT_ENGULFING_SR -> {
                if (analysis != null && (analysis.isEngulfingCall || analysis.isEngulfingPut)) {
                    "⚡ Vela envolvente en nivel clave! Evaluando entrada..."
                } else {
                    "🔍 MT: Buscando patrón envolvente en S/R..."
                }
            }
            AutoTradeStrategy.MT_FALSE_BREAKOUT -> {
                if (analysis != null && (analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut)) {
                    "⚡ Trampa institucional / Fakeout detectado!"
                } else {
                    "🔍 MT: Vigilando falsos rompimientos de nivel..."
                }
            }
            AutoTradeStrategy.MT_MASTER_COMBO -> {
                "🔥 MT Master Combo: Vigilando Rechazo, Trampa, Envolvente y Choque..."
            }
            AutoTradeStrategy.COLOR_TREND -> {
                "🎨 Color Trend: Vigilando continuidad de color y tendencia..."
            }
            AutoTradeStrategy.STRIKE_BREAKOUT -> {
                "💥 Strike Breakout: Vigilando ruptura de strike..."
            }
            AutoTradeStrategy.AI_REMOTE -> {
                "🧠 IA Remota: Esperando análisis multimodal..."
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                if (analysis != null && analysis.consecutiveCount > 0) {
                    "📊 Racha: ${analysis.consecutiveCount} ${analysis.lastCandles.firstOrNull()?.name ?: ""} (Obj: 3)"
                } else {
                    "🔍 Contando velas en vivo..."
                }
            }
            AutoTradeStrategy.TREND_FOLLOWING -> {
                if (analysis != null) "📈 Tendencia: ${analysis.trend.name}" else "🔍 Detectando tendencia..."
            }
            AutoTradeStrategy.COMBINED -> {
                "🔍 Esperando confirmación doble..."
            }
        }
    }

    private fun handleSignal(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        bitmap: Bitmap,
        reasonDescription: String,
        confidence: Float? = null
    ) {
        val sec = analysis.candleSecond
        val isTimingVetoed = MarketTickFilters.isTimingVetoed(sec) || analysis.isTimingVetoed
        val isChoppy = analysis.isMicroRangeChoppy || syntheticCandleEngine.isChoppinessDetected()

        if (isChoppy) {
            android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
            return
        }

        if (isTimingVetoed && !analysis.isFalseBreakoutCall && !analysis.isFalseBreakoutPut) {
            android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Timing Estricto (:15-:55) (⏱ ${sec}s)")
            return
        }

        // Evaluación por el Motor de Autoaprendizaje Adaptativo de Errores
        val adaptiveDecision = adaptiveLearningEngine.evaluateSignalSuitability(
            candidateAction = action,
            analysis = analysis,
            tick = latestMarketTick,
            strategyName = strategy.name
        )

        val finalAction: TradeAction
        val finalReason: String
        val adaptiveModifier: Float

        when (adaptiveDecision) {
            is AdaptiveDecision.Block -> {
                android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Autoaprendizaje: ${adaptiveDecision.reason}")
                handler.post {
                    Toast.makeText(context, "⛔ [Autoaprendizaje] Entrada bloqueada: ${adaptiveDecision.reason}", Toast.LENGTH_SHORT).show()
                }
                return
            }
            is AdaptiveDecision.Invert -> {
                finalAction = adaptiveDecision.invertedAction
                finalReason = "$reasonDescription [🔄 Invertida por Autoaprendizaje: ${adaptiveDecision.reason}]"
                adaptiveModifier = 1.15f
            }
            is AdaptiveDecision.Allow -> {
                finalAction = adaptiveDecision.action
                finalReason = reasonDescription
                adaptiveModifier = adaptiveDecision.confidenceModifier
            }
        }

        val baseConfidence = confidence ?: when {
            finalAction == TradeAction.BUY && analysis.signalPowerCall > 0 -> analysis.signalPowerCall / 100f
            finalAction == TradeAction.SELL && analysis.signalPowerPut > 0 -> analysis.signalPowerPut / 100f
            else -> 1.0f
        }
        val signalConfidence = (baseConfidence * adaptiveModifier).coerceIn(0.1f, 1.0f)
        val (canTrade, reason) = riskManager.canExecuteTrade(mode, autonomousSubMode, signalConfidence)
        val actionText = if (finalAction == TradeAction.BUY) "COMPRA / CALL (Sube)" else "VENTA / PUT (Baja)"
        val emoji = if (finalAction == TradeAction.BUY) "🟢 ▲" else "🔴 ▼"

        currentActiveSignal = ActiveSignal(
            action = finalAction,
            title = "$emoji $actionText",
            reason = finalReason,
            timestamp = System.currentTimeMillis()
        )

        handler.post {
            onSignalListener?.invoke(finalAction, "$actionText · $finalReason")
            val now = System.currentTimeMillis()
            if (now - lastFeedbackEmitTime >= 3500L && canTrade) {
                lastFeedbackEmitTime = now
                emitHapticAndAudioFeedback()
            }
        }

        if (mode == AutoTradeMode.SEMIAUTOMATIC) {
            handler.post {
                autoDrawEngine.drawTradeEntry(finalAction, analysis.currentPriceY, context.resources.displayMetrics.widthPixels.toFloat())
                Toast.makeText(context, "🔔 SEÑAL: $actionText\n$finalReason", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (mode == AutoTradeMode.AUTONOMOUS) {
            if (!canTrade) {
                if (autonomousSubMode == AutonomousSubMode.SNIPER && (reason.contains("alcanzado", ignoreCase = true) || reason.contains("finalizada", ignoreCase = true) || reason.contains("Límite", ignoreCase = true))) {
                    mode = AutoTradeMode.DISABLED
                    sniperShutdownReason = reason
                    handler.post {
                        Toast.makeText(context, "🛑 $reason", Toast.LENGTH_LONG).show()
                        OverlayService.instance?.updateHUDView()
                    }
                }
                return
            }

            // Registrar firma de entrada en el motor adaptativo
            adaptiveLearningEngine.recordTradeOpened(finalAction, analysis, latestMarketTick, strategy.name)
            executeAutonomousTrade(finalAction, analysis, bitmap, finalReason, signalConfidence)
        }
    }

    private fun executeAutonomousTrade(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        bitmap: Bitmap,
        reasonDescription: String,
        confidence: Float = 0.0f
    ) {
        val calibration = calibrationManager
        val (screenW, screenH) = CalibrationManager.getRealScreenDimensions(context)
        val isLand = screenW > screenH

        // 1. Prioridad Máxima: Detección visual en tiempo real sobre el frame activo
        val visionCoords = visionAnalyzer.findBrokerButtonCoordinates(bitmap, action == TradeAction.BUY)

        // 2. Respaldo: Calibración guardada o fórmulas geométricas precisas
        val (rawX, rawY) = if (visionCoords != null) {
            visionCoords
        } else if (calibration != null && calibration.isCalibrated()) {
            if (action == TradeAction.BUY) calibration.getBuyCoordinates() else calibration.getSellCoordinates()
        } else {
            if (isLand) {
                if (action == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                // Centro exacto del área táctil de los botones en Binomo vertical (90.3% de la pantalla)
                if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.903f)
                else Pair(screenW * 0.75f, screenH * 0.903f)
            }
        }

        // Sanitización estricta: en modo vertical, nunca permitir clics por encima del 86.5% (fila de Hora / Cantidad)
        val (x, y) = if (!isLand && rawY < screenH * 0.865f) {
            android.util.Log.w("TradingEngine", "⚠️ Coordenada Y=$rawY cae sobre selector de Hora/Cantidad (<86.5%). Corrigiendo a botón real (${screenH * 0.903f}).")
            Pair(rawX, screenH * 0.903f)
        } else {
            Pair(rawX, rawY)
        }

        android.util.Log.d("TradingEngine", "executeAutonomousTrade: $action hacia ($x, $y) [Vision:${visionCoords != null}] Motivo: $reasonDescription")

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            val observed = accessibility.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
            val baseBal = if (observed > 0.0) observed else AutoTradeAccessibilityService.latestObservedBalance
            isTradeResolving.set(false)
            riskManager.recordTradeSent(action, analysis.currentPriceY, baseBal, confidence)
            // Despacho táctil único con verificación de 300ms y fallback híbrido
            accessibility.performClickAt(x, y) { success, error ->
                if (!success) {
                    triggerManualFallback(action, error ?: "Auto-clic falló en 300ms")
                }
            }

            handler.post {
                drawingView.triggerClickAnimation(x, y)
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, screenW)
                saveAuditScreenshot(bitmap, action)
                val stake = riskManager.getCurrentInvestmentAmount(autonomousSubMode)
                Toast.makeText(context, "🤖 BOT OPERÓ: $action ($$$stake)\n$reasonDescription", Toast.LENGTH_LONG).show()
            }
        } else {
            triggerManualFallback(action, "Accesibilidad no conectada")
            handler.post {
                Toast.makeText(context, "⚠️ Clic cancelado: Activa el Servicio de Accesibilidad en Ajustes para Auto-Trading", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun triggerManualFallback(action: TradeAction, reason: String) {
        Log.w("TradingEngine", "🚨 Activando MODO HÍBRIDO Fallback: $reason. Alerta manual para $action")
        emitHapticWarning()
        OverlayService.instance?.showManualFallbackAlert(action, 2500L)
        handler.post {
            val dir = if (action == TradeAction.BUY) "CALL ▲ (SUBE)" else "PUT ▼ (BAJA)"
            Toast.makeText(context, "🚨 ¡PULSA MANUAL: $dir!\n$reason", Toast.LENGTH_SHORT).show()
        }
    }

    private fun emitHapticWarning() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(350)
                }
            }
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error en vibración háptica", e)
        }
    }

    /**
     * Prueba los clics de SUBE y BAJA para verificar la conexión de Accesibilidad.
     */
    fun testAccessibilityClicks() {
        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility == null) {
            Toast.makeText(context, "❌ Accesibilidad NO conectada.\nAbriendo Ajustes de Accesibilidad...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("TradingEngine", "Error abriendo ajustes de accesibilidad", e)
            }
            return
        }

        val calibration = calibrationManager
        val (buyX, buyY) = calibration?.getBuyCoordinates() ?: Pair(200f, 600f)
        val (sellX, sellY) = calibration?.getSellCoordinates() ?: Pair(600f, 600f)

        Toast.makeText(context, "👉 Probando clic en SUBE...", Toast.LENGTH_SHORT).show()
        accessibility.performClickAt(buyX, buyY)

        handler.postDelayed({
            Toast.makeText(context, "👉 Probando clic en BAJA...", Toast.LENGTH_SHORT).show()
            accessibility.performClickAt(sellX, sellY)
        }, 1200)
    }

    private fun emitHapticAndAudioFeedback() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (notificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error en feedback háptico/audio", e)
        }
    }

    private fun saveAuditScreenshot(bitmap: Bitmap, action: TradeAction) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.getExternalFilesDir(null), "TradeDraw_Audits")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "TRADE_${action.name}_$timeStamp.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            Log.d("TradingEngine", "Screenshot guardado en: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error guardando screenshot de auditoría", e)
        }
    }

    /**
     * Ejecuta una orden en modo 100% Headless (sin frames ni capturas de pantalla).
     * Utiliza las cotizaciones puras del WebSocket y pulsa las coordenadas calibradas con Accesibilidad.
     */
    fun executeHeadlessTrade(action: TradeAction, reasonDescription: String) {
        // GUARD DE FRESCURA DEL FEED (fail-closed, fuente única = WebSocket).
        // Sin tick reciente (<5000 ms) o sin conexión, se veta la operación: no se degrada
        // a ninguna otra fuente (la captura de pantalla fue retirada por consumo de batería).
        val tickAgeMs = latestMarketTick?.let { System.currentTimeMillis() - it.timestampMs }
        if (tickAgeMs == null || tickAgeMs >= FEED_MAX_AGE_MS || !isFeedFresh()) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO por feed rancio: " +
                    "ageMs=${tickAgeMs ?: -1} (umbral=${FEED_MAX_AGE_MS}ms) " +
                    "fresh=${isFeedFresh()} → no se degrada a otra fuente"
            )
            return
        }

        // GUARD DE PRECIO CONGELADO: el feed está fresco pero el emisor no mueve el precio.
        // En ese estado cualquier decisión CALL/PUT es equivalente a lanzar una moneda.
        val currentObservedAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
        if (isPriceFrozen() && !currentObservedAsset.contains("IDX", ignoreCase = true)) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO por precio congelado: el emisor entrega " +
                    "ticks pero el rango del precio es ~0 (activo plano)"
            )
            return
        }

        val sec = latestMarketTick?.candleSecond ?: (((System.currentTimeMillis() / 1000L) % 60L).toInt())
        val isYolo = (autonomousSubMode == AutonomousSubMode.YOLO)
        val isSniper = (autonomousSubMode == AutonomousSubMode.SNIPER)

        if (isSniper) {
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic) {
                Log.d("TradingEngine", "Headless bloqueado por Veto Francotirador: Activo sintético/OTC ($currentAsset)")
                return
            }
        }

        // Ventana Sniper Quirúrgica: :58-:59 en SNIPER, :57-:05 en otros modos
        val inWindow = if (isSniper) sec in 58..59 else (sec in 57..59 || sec in 0..5)
        if (!inWindow) {
            Log.d("TradingEngine", "Headless bloqueado fuera de ventana timing sniper (⏱ ${sec}s | SNIPER=$isSniper | YOLO=$isYolo)")
            return
        }
        val isRejectionOrBounce = reasonDescription.contains("MT_REJECTION") ||
                reasonDescription.contains("MT_RANGE") ||
                reasonDescription.contains("MT_3_VELAS") ||
                reasonDescription.contains("MT_REVERSAL") ||
                reasonDescription.contains("MT_CONFIRM")
        if (syntheticCandleEngine.isChoppinessDetected() && !isRejectionOrBounce && !isYolo) {
            Log.d("TradingEngine", "Headless bloqueado por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
            return
        }

        // 1. Espaciado y Cooldown post-resolución
        val timeSinceLastResolution = System.currentTimeMillis() - lastTradeResolutionTime
        val minSpacingMs = when {
            isYolo -> 8000L // 8 segundos en YOLO para no trabar la siguiente vela
            riskManager.currentLossStreak > 0 -> 35000L
            else -> 12000L
        }
        if (lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs) {
            Log.d("TradingEngine", "Headless bloqueado por Cooldown post-resolución (${timeSinceLastResolution / 1000}s < ${minSpacingMs / 1000}s)")
            return
        }

        // 2. Evaluación por Motor de Autoaprendizaje Adaptativo
        // El análisis se construye SIEMPRE desde el motor sintético de ticks WebSocket.
        // Ya no se reutiliza latestAnalysisResult (visión): sus mechas/S-R/choppiness son
        // ceros en modo WebSocket puro y vetaban señales sobre datos nulos.
        val effectiveAnalysis = buildAnalysisFromSyntheticEngine(latestMarketTick ?: return)

        val adaptiveDecision = adaptiveLearningEngine.evaluateSignalSuitability(
            candidateAction = action,
            analysis = effectiveAnalysis,
            tick = latestMarketTick,
            strategyName = strategy.name
        )
        val finalAction: TradeAction
        val finalReason: String
        val adaptiveModifier: Float

        when (adaptiveDecision) {
            is AdaptiveDecision.Block -> {
                Log.w("TradingEngine", "⛔ Headless Trade $action VETADO por Autoaprendizaje: ${adaptiveDecision.reason}")
                handler.post {
                    Toast.makeText(context, "⛔ [Autoaprendizaje] Entrada bloqueada: ${adaptiveDecision.reason}", Toast.LENGTH_SHORT).show()
                }
                return
            }
            is AdaptiveDecision.Invert -> {
                finalAction = adaptiveDecision.invertedAction
                finalReason = "$reasonDescription [🔄 Invertida por Autoaprendizaje]"
                adaptiveModifier = 1.15f
            }
            is AdaptiveDecision.Allow -> {
                finalAction = adaptiveDecision.action
                finalReason = reasonDescription
                adaptiveModifier = adaptiveDecision.confidenceModifier
            }
        }

        val headlessConfidence = (0.85f * adaptiveModifier).coerceIn(0.1f, 1.0f)
        val (canTrade, riskReason) = riskManager.canExecuteTrade(mode, autonomousSubMode, headlessConfidence)
        if (!canTrade) {
            Log.d("TradingEngine", "Headless bloqueado por riesgo: $riskReason")
            if (isSniper && (riskReason.contains("alcanzado", ignoreCase = true) || riskReason.contains("finalizada", ignoreCase = true) || riskReason.contains("Límite", ignoreCase = true))) {
                mode = AutoTradeMode.DISABLED
                sniperShutdownReason = riskReason
                handler.post {
                    Toast.makeText(context, "🛑 $riskReason", Toast.LENGTH_LONG).show()
                    OverlayService.instance?.updateHUDView()
                }
            }
            return
        }

        val (screenW, screenH) = CalibrationManager.getRealScreenDimensions(context)
        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || screenW > screenH

        val calibCoords = if (calibrationManager != null && calibrationManager!!.isCalibrated()) {
            if (finalAction == TradeAction.BUY) calibrationManager!!.getBuyCoordinates()
            else calibrationManager!!.getSellCoordinates()
        } else null

        val (rawX, rawY) = if (calibCoords != null) {
            calibCoords
        } else {
            if (isLand) {
                if (finalAction == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                if (finalAction == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.903f)
                else Pair(screenW * 0.75f, screenH * 0.903f)
            }
        }

        // Sanitización estricta: en modo vertical, nunca permitir clics por encima del 86.5% (fila de Hora / Cantidad)
        val (x, y) = if (!isLand && rawY < screenH * 0.865f) {
            Log.w("TradingEngine", "⚠️ Coordenada Y corregida de $rawY a ${screenH * 0.903f} para no tocar fila de tiempo")
            Pair(rawX, screenH * 0.903f)
        } else {
            Pair(rawX, rawY)
        }

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            val observed = accessibility.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
            val baseBal = if (observed > 0.0) observed else AutoTradeAccessibilityService.latestObservedBalance
            isTradeResolving.set(false)
            riskManager.recordTradeSent(finalAction, latestMarketTick?.price?.toFloat() ?: 0f, baseBal, headlessConfidence)
            adaptiveLearningEngine.recordTradeOpened(
                action = finalAction,
                analysis = effectiveAnalysis,
                tick = latestMarketTick,
                strategyName = strategy.name
            )
            // Despacho con callback y fallback híbrido
            accessibility.performClickAt(x, y) { success, error ->
                if (!success) {
                    triggerManualFallback(finalAction, error ?: "Auto-clic headless falló en 300ms")
                }
            }

            handler.post {
                drawingView.triggerClickAnimation(x, y)
                // En modo Headless, situar la STRIKE_PRICE_LINE en la altura estimada del gráfico
                autoDrawEngine.drawTradeEntry(finalAction, y.coerceIn(screenH * 0.35f, screenH * 0.65f), screenW)
                emitHapticAndAudioFeedback()
                val stake = riskManager.getCurrentInvestmentAmount(autonomousSubMode)
                Toast.makeText(context, "⚡ [${strategy.name}] BOT OPERÓ: $finalAction ($$$stake)\n$finalReason", Toast.LENGTH_LONG).show()
                onTradeExecutedListener?.invoke(finalAction, true)
            }
        } else {
            triggerManualFallback(finalAction, "Accesibilidad no conectada")
            handler.post {
                Toast.makeText(context, "⚠️ Clic Headless cancelado: Activa Accesibilidad en Ajustes", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkHeadlessTradeResolution(tick: MarketTick) {
        // Punto ÚNICO de entrada de la liquidación (onMarketTick y onNewFrame): valida el pendiente aquí.
        if (!riskManager.hasPendingTrade) return

        // Sin timestamp de apertura no hay trade identificable: escribir aquí produciría filas huérfanas
        val tradeStartMs = riskManager.pendingTradeStartTime
        if (tradeStartMs <= 0L) {
            Log.w("TradingEngine", "Liquidación abortada: pendiente sin timestamp de apertura")
            return
        }

        val elapsedSec = (System.currentTimeMillis() - tradeStartMs) / 1000
        val expDurationSec = timeframe.seconds
        val isExpired = elapsedSec >= (expDurationSec + 2)
        if (!isExpired) return

        val baseBalance = riskManager.pendingTradeBaseBalance
        val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance()
            ?: AutoTradeAccessibilityService.latestObservedBalance

        var isWin: Boolean? = null
        var isTie = false
        var method = ""

        if (baseBalance > 0.0 && currentBal > 0.0) {
            val diff = currentBal - baseBalance
            if (diff > 10.0) {
                isWin = true
                method = "SALDO (+) Ganancia acreditada por Binomo: Diff=+$diff COP (${elapsedSec}s)"
            } else if (diff < -10.0 && elapsedSec >= (expDurationSec + 12)) {
                isWin = false
                method = "SALDO (-) Pérdida confirmada tras liquidación: Diff=$diff COP (${elapsedSec}s)"
            } else if (Math.abs(diff) <= 10.0 && elapsedSec >= (expDurationSec + 15)) {
                isTie = true
                method = "ORDEN NO PROCESADA (Diff=$diff -> Saldo inalterado tras ${elapsedSec}s)"
            }
        } else if (elapsedSec >= (expDurationSec + 15)) {
            isTie = true
            method = "TIMEOUT ${expDurationSec + 15}s (Sin saldo legible -> Cancelación preventiva)"
        }

        if (isWin != null || isTie) {
            if (!isTradeResolving.compareAndSet(false, true)) return
            val finalWin = isWin ?: false
            val pendingAction = riskManager.pendingTradeAction ?: TradeAction.BUY

            val pendingEntry = riskManager.pendingTradeEntryPriceY
            val pendingConfidence = riskManager.pendingTradeConfidence
            val pendingStake = riskManager.getCurrentInvestmentAmount()
            val pendingTradeStartMs = tradeStartMs
            val sec = latestMarketTick?.candleSecond ?: (((System.currentTimeMillis() / 1000L) % 60L).toInt())
            val wsAnalysis = VisionAnalysisResult(
                trend = syntheticCandleEngine.detectedTrend,
                distanceToSupportRatio = syntheticCandleEngine.distanceToSupportRatio,
                distanceToResistanceRatio = syntheticCandleEngine.distanceToResistanceRatio,
                candleSecond = sec
            )
            val journalResult = if (isTie) "TIE" else if (finalWin) "WIN" else "LOSS"

            handler.post {
                autoDrawEngine.clearTradeEntry()

                TradeJournalLogger.logTrade(
                    context = context,
                    strategy = strategy.name,
                    submode = autonomousSubMode.name,
                    action = pendingAction.name,
                    confidence = pendingConfidence,
                    priceY = pendingEntry,
                    stake = pendingStake,
                    baseBalance = baseBalance,
                    result = journalResult,
                    settledBalance = currentBal,
                    durationSec = elapsedSec,
                    reason = method,
                    trend = wsAnalysis.trend.name,
                    distSupportRatio = wsAnalysis.distanceToSupportRatio,
                    distResistanceRatio = wsAnalysis.distanceToResistanceRatio,
                    tickVelocity = latestMarketTick?.velocity ?: 0f,
                    impulse = when {
                        latestMarketTick?.isBullishImpulse == true -> "BULLISH"
                        latestMarketTick?.isBearishImpulse == true -> "BEARISH"
                        else -> "NEUTRAL"
                    },
                    marketRegime = if (syntheticCandleEngine.isChoppinessDetected()) "CHOP" else "TRENDING",
                    candleSecond = sec,
                    adaptiveStatus = journalResult,
                    tradeStartMs = pendingTradeStartMs
                )

                if (isTie) {
                    riskManager.recordTradeVoid()
                    Toast.makeText(context, "[HEADLESS] ⚪ Empate / Orden cancelada", Toast.LENGTH_SHORT).show()
                } else if (finalWin) {
                    riskManager.recordTradeWin()
                    adaptiveLearningEngine.recordTradeOutcome(true, context)
                    emitHapticAndAudioFeedback()
                    Toast.makeText(context, "[HEADLESS] 🎉 GANADA (+1 W) [$method]", Toast.LENGTH_SHORT).show()
                    onTradeExecutedListener?.invoke(pendingAction, true)
                } else {
                    riskManager.recordTradeLoss()
                    adaptiveLearningEngine.recordTradeOutcome(false, context)
                    emitHapticAndAudioFeedback()
                    Toast.makeText(context, "[HEADLESS] ⚠️ PERDIDA (+1 L) [$method]", Toast.LENGTH_SHORT).show()
                    onTradeExecutedListener?.invoke(pendingAction, false)
                }
                lastTradeResolutionTime = System.currentTimeMillis()
                isTradeResolving.set(false)
                checkSniperSessionLimits()
            }
        } else {
            isTradeResolving.set(false)
        }
    }

    fun stop() {
        mode = AutoTradeMode.DISABLED
        autoDrawEngine.clearAutoDrawings()
    }
}
