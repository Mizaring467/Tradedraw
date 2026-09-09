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
    YOLO             // Sin Stop Loss ni límites, operativa continua y desatendida
}

class TradingEngine(
    private val context: Context,
    private val drawingView: CustomDrawingView,
    val riskManager: RiskManager,
    var calibrationManager: CalibrationManager? = null,
    val aiClient: AIClient = AIClient(context)
) {
    val agentController = AutonomousAgentController(context, this, riskManager)

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
    var strategy: AutoTradeStrategy = AutoTradeStrategy.AUTO_ADAPTIVE
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
        // Conectar callback: cuando el usuario arrastra una línea del bot, bloquearla
        drawingView.onBotShapeDragged = { key ->
            autoDrawEngine.lockLine(key)
            handler.post {
                Toast.makeText(context, "Línea $key bloqueada [Manual]", Toast.LENGTH_SHORT).show()
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

    var currentActiveSignal: ActiveSignal? = null
        private set

    fun clearActiveSignal() {
        currentActiveSignal = null
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

            // Ventana de resolución estricta al EXPIRAR la vela de 1 minuto
            // Espera a que la vela cierre (:00) y Binomo liquide el trade en pantalla (segundo 2..8 tras al menos 52s, o timeout a los 62s)
            val isExpired = elapsedSec >= 62 || (elapsedSec >= 52 && analysis.candleSecond in 2..8)
            if (isExpired) {
                var isWin: Boolean? = null
                var isTie: Boolean = false
                var method = ""

                val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance() 
                    ?: AutoTradeAccessibilityService.latestObservedBalance

                // 1. Verificación por Saldo Real Inmutable de Binomo
                if (baseBalance > 0.0 && currentBal > 0.0) {
                    val diff = currentBal - baseBalance
                    if (diff > 10.0) {
                        // Binomo acreditó las ganancias (+1 W)
                        isWin = true
                        method = "SALDO (+) Ganancia acreditada por Binomo: Diff=+$diff COP"
                    } else if (diff < -10.0) {
                        // Binomo debitó la inversión sin retorno (+1 L)
                        isWin = false
                        method = "SALDO (-) Pérdida debitada por Binomo: Diff=$diff COP"
                    } else if (elapsedSec >= 62) {
                        // Saldo sin incremento tras 62 segundos completos: en opciones binarias es pérdida
                        isWin = false
                        method = "SIN ACREDITACIÓN TRAS 62s (Diff=$diff -> Pérdida)"
                    }
                } else if (elapsedSec >= 65) {
                    // Si no hubo saldo legible tras 65 segundos, NUNCA asumir victoria
                    isWin = false
                    method = "TIMEOUT 65s (Sin saldo legible -> Loss preventivo)"
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
                        if (isTie) {
                            riskManager.clearPendingTrade()
                            autoDrawEngine.clearTradeEntry()
                            Toast.makeText(context, "⚪ EMPATE EN BINOMO (Reembolso de capital)", Toast.LENGTH_LONG).show()
                        } else if (finalWin) {
                            riskManager.recordTradeWin()
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, true)
                        } else {
                            riskManager.recordTradeLoss()
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "⚠️ OPERACIÓN PERDIDA (+1 L)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, false)
                        }
                        isTradeResolving.set(false)
                        lastTradeResolutionTime = System.currentTimeMillis()
                    }
                }
            }
        }

        val timeSinceLastResolution = System.currentTimeMillis() - lastTradeResolutionTime
        // Espaciado inteligente: Si la última operación fue derrota, enfriamiento de 45s para no repetir en la misma micro-tendencia
        val minSpacingMs = if (riskManager.currentLossStreak > 0) 45000L else 12000L
        val isSpacingCooldown = lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs

        // 5. Evaluar señal de trading solo si no hay trade abierto, sin cooldown de espaciado y el modo está activo
        if (!riskManager.hasPendingTrade && !isSpacingCooldown && mode != AutoTradeMode.DISABLED) {
            val localSignal = evaluateStrategySignal(strategy, analysis, supports.isNotEmpty() || resistances.isNotEmpty())

            // 5a. Mantener la IA analizando en segundo plano si está activa (sin bloquear señales locales)
            if (aiClient.isEnabled && aiClient.apiKey.isNotBlank()) {
                aiClient.analyzeFrame(bitmap) { aiResult ->
                    latestAIResult = aiResult
                    latestAITimestamp = System.currentTimeMillis()
                    handler.post { onFrameProcessedListener?.invoke(analysis) }

                    // Si la IA emite señal directa con confianza suficiente, ejecutar sincronizada con reloj sniper
                    if (aiResult.isSuccess && aiResult.action != null && aiResult.confidence >= aiClient.confidenceThreshold) {
                        if (!riskManager.hasPendingTrade && mode != AutoTradeMode.DISABLED) {
                            val sec = analysis.candleSecond
                            val isTimingValid = analysis.isSniperTimingWindow || (sec in 57..59 || sec in 0..7)
                            val isMarketUnfavorable = analysis.isMarketSideways || analysis.isConsolidationTight
                            val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                            val inUptrend = analysis.trend == TrendDirection.UPTREND
                            val aiTrendConflict = (aiResult.action == TradeAction.BUY && inDowntrend && !analysis.isFalseBreakoutCall) ||
                                                  (aiResult.action == TradeAction.SELL && inUptrend && !analysis.isFalseBreakoutPut)

                            if (isMarketUnfavorable) {
                                android.util.Log.d("TradingEngine", "Señal IA ${aiResult.action} bloqueada: Mercado lateral / consolidación")
                            } else if (!isTimingValid) {
                                android.util.Log.d("TradingEngine", "Señal IA ${aiResult.action} pospuesta: fuera de ventana sniper (⏱ ${sec}s)")
                            } else if (aiTrendConflict) {
                                android.util.Log.d("TradingEngine", "Señal IA ${aiResult.action} bloqueada: conflicto con tendencia ${analysis.trend}")
                            } else {
                                val pct = (aiResult.confidence * 100).toInt()
                                handleSignal(aiResult.action, analysis, bitmap, "IA ($pct% | ⏱ ${sec}s): ${aiResult.reason}")
                            }
                        }
                    }
                }
            }

            // 5b. Ejecución de Señal Técnica Local en Tiempo Real
            if (localSignal != null && !riskManager.hasPendingTrade) {
                val ai = latestAIResult
                val isAIFresh = (System.currentTimeMillis() - latestAITimestamp) <= 12000L // Máximo 12s de validez para IA
                // Filtro inteligente: No operar solo si la IA FRESCA contradice con alta certeza la señal local
                val isConflicted = if (isAIFresh && ai != null && ai.isSuccess && ai.confidence >= 0.80f) {
                    (localSignal == TradeAction.BUY && ai.action == TradeAction.SELL) ||
                    (localSignal == TradeAction.SELL && ai.action == TradeAction.BUY)
                } else false

                if (!isConflicted) {
                    val aiTag = if (isAIFresh && ai != null && ai.isSuccess && ai.action == localSignal) " + IA Confirmada" else ""
                    val description = if (lastSignalReason.isNotBlank()) "$lastSignalReason$aiTag" else "Estrategia Local (${strategy.name})$aiTag"
                    handleSignal(localSignal, analysis, bitmap, description)
                } else {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal omitida por conflicto con análisis reciente de IA (${ai?.action})")
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
        val (action, reason) = evaluateStrategySignalWithReason(strategy, analysis, hasDrawnLines)
        lastSignalReason = reason
        return action
    }

    companion object {
        fun evaluateStrategySignal(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            hasDrawnLines: Boolean = false
        ): TradeAction? {
            val (action, _) = evaluateStrategySignalWithReason(strategy, analysis, hasDrawnLines)
            return action
        }

        fun evaluateStrategySignalWithReason(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            hasDrawnLines: Boolean = false
        ): Pair<TradeAction?, String> {
            // Tarea 3: Filtro anti-mercado lateral / Dojis
            if (analysis.isMarketSideways) {
                return Pair(null, "⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad")
            }
            if (analysis.isConsolidationTight) {
                return Pair(null, "⏳ Rango estrecho / Sin volatilidad: Esperando expansión")
            }

            val sec = analysis.candleSecond
            val isLate = analysis.isLateTimingForbidden

            // Veto universal de entrada tardía para opciones binarias a 1 minuto
            // (Permitido únicamente en trampas institucionales / falsos rompimientos con confirmación)
            if (isLate && !analysis.isFalseBreakoutCall && !analysis.isFalseBreakoutPut) {
                return Pair(null, "⏳ Entrada tardía (${sec}s): Fuera de ventana sniper (:57-:07)")
            }

            return when (strategy) {
                AutoTradeStrategy.AUTO_ADAPTIVE -> {
                    // Modo Automático Total: Confluencia Multi-Factor + Sniping de Entrada
                    val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                    val inUptrend = analysis.trend == TrendDirection.UPTREND

                    when {
                        // 1. Prioridad Máxima: Falso Rompimiento / Trampa Institucional (95% confluencia)
                        analysis.isFalseBreakoutCall -> Pair(TradeAction.BUY, "🎯 Auto [Trampa en Soporte | ⏱ ${sec}s] -> CALL")
                        analysis.isFalseBreakoutPut -> Pair(TradeAction.SELL, "🎯 Auto [Trampa en Resistencia | ⏱ ${sec}s] -> PUT")

                        // Veto de entrada tardía para el resto de setups
                        isLate -> Pair(null, "⏳ Entrada tardía (${sec}s): Esperando apertura de vela")

                        // 2. Mechas de Rechazo y Absorción en S/R con Sniping de Pullback (90% confluencia)
                        !inDowntrend && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) &&
                            (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                            Pair(TradeAction.BUY, "🎯 Auto [Mecha Rechazo Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) &&
                            (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                            Pair(TradeAction.SELL, "🎯 Auto [Mecha Rechazo Resistencia | ⏱ ${sec}s] -> PUT")

                        // 3. Patrón Vela Envolvente en S/R (85% confluencia)
                        analysis.isEngulfingCall -> Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | ⏱ ${sec}s] -> CALL")
                        analysis.isEngulfingPut -> Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | ⏱ ${sec}s] -> PUT")

                        // 4. Choque / Retest tras Rompimiento (80% confluencia) — solo a favor de tendencia
                        !inDowntrend && (analysis.isChoqueCall || analysis.isChoquePullbackCall) -> Pair(TradeAction.BUY, "🎯 Auto [Choque / Pullback | ⏱ ${sec}s] -> CALL")
                        !inUptrend && (analysis.isChoquePut || analysis.isChoquePullbackPut) -> Pair(TradeAction.SELL, "🎯 Auto [Choque / Pullback | ⏱ ${sec}s] -> PUT")

                        // 5. Agotamiento de 3 Velas Consecutivas (75% confluencia) — solo a favor de tendencia
                        !inDowntrend && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) -> Pair(TradeAction.BUY, "🎯 Auto [Agotamiento 3 Rojas | ⏱ ${sec}s] -> CALL")
                        !inUptrend && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) -> Pair(TradeAction.SELL, "🎯 Auto [Agotamiento 3 Verdes | ⏱ ${sec}s] -> PUT")

                        // 6. Rebote S/R Clásico con Sniping de Entrada (70% confluencia) — solo a favor de tendencia
                        !inDowntrend && analysis.touchesSupport && analysis.isPullbackSniperCall -> Pair(TradeAction.BUY, "🎯 Auto [Rebote en Soporte | ⏱ ${sec}s] -> CALL")
                        !inUptrend && analysis.touchesResistance && analysis.isPullbackSniperPut -> Pair(TradeAction.SELL, "🎯 Auto [Rebote en Resistencia | ⏱ ${sec}s] -> PUT")

                        // 7. Impulso y Confluencia Cuantitativa Alta >= 75% — solo a favor de tendencia
                        !inDowntrend && (analysis.confluenceScoreCall >= 75 || analysis.signalPowerCall >= 75 || (analysis.isCallSignal && analysis.signalScore >= 75)) -> {
                            val score = Math.max(analysis.confluenceScoreCall, analysis.signalPowerCall)
                            Pair(TradeAction.BUY, "🎯 Auto [Confluencia Fuerte ($score%) | ⏱ ${sec}s] -> CALL")
                        }
                        !inUptrend && (analysis.confluenceScorePut >= 75 || analysis.signalPowerPut >= 75 || (analysis.isPutSignal && analysis.signalScore >= 75)) -> {
                            val score = Math.max(analysis.confluenceScorePut, analysis.signalPowerPut)
                            Pair(TradeAction.SELL, "🎯 Auto [Confluencia Fuerte ($score%) | ⏱ ${sec}s] -> PUT")
                        }
                        else -> Pair(null, "")
                    }
                }
               AutoTradeStrategy.MT_MASTER_COMBO -> {
                   // Jerarquía Master Traders con filtro macro de confluencia
                   val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                   val inUptrend = analysis.trend == TrendDirection.UPTREND

                   when {
                       // 1. Falso Rompimiento / Trampa en S/R (Permitido incluso contra tendencia)
                       analysis.isFalseBreakoutCall -> {
                           Pair(TradeAction.BUY, "🎯 MT Combo: Trampa / Falso Rompimiento de Soporte -> CALL")
                       }
                       analysis.isFalseBreakoutPut -> {
                           Pair(TradeAction.SELL, "🎯 MT Combo: Trampa / Falso Rompimiento de Resistencia -> PUT")
                       }
                        isLate -> Pair(null, "⏳ Entrada tardía (${sec}s): Esperando apertura de vela")
                       // 2. Mechas de Rechazo en S/R (Filtradas por tendencia)
                       !inDowntrend && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) -> {
                           Pair(TradeAction.BUY, "🎯 MT Combo: Mecha de Rechazo en Soporte -> CALL")
                       }
                        !inUptrend && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Mecha de Rechazo en Resistencia -> PUT")
                        }
                        // 3. Patrón Envolvente en S/R
                        analysis.isEngulfingCall -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Vela Envolvente en Soporte -> CALL")
                        }
                        analysis.isEngulfingPut -> {
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
                        // Soporte / Resistencia Clásico
                        !inDowntrend && analysis.touchesSupport && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || analysis.isPullbackSniperCall) -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Rebote Confirmado en Soporte -> CALL")
                        }
                        !inUptrend && analysis.touchesResistance && (analysis.lastCandles.firstOrNull() == CandleType.RED || analysis.isPullbackSniperPut) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Rebote Confirmado en Resistencia -> PUT")
                        }
                       // 6. Termómetro de Señal / Tendencia Alta Probabilidad >= 75%
                        !inDowntrend && analysis.isSniperTimingWindow && (analysis.signalPowerCall >= 75 || (analysis.isCallSignal && analysis.signalScore >= 75)) -> {
                            val score = if (analysis.signalPowerCall >= 75) analysis.signalPowerCall else analysis.signalScore
                           Pair(TradeAction.BUY, "🎯 MT Combo: Tendencia Alta ($score%) -> CALL")
                       }
                        !inUptrend && analysis.isSniperTimingWindow && (analysis.signalPowerPut >= 75 || (analysis.isPutSignal && analysis.signalScore >= 75)) -> {
                            val score = if (analysis.signalPowerPut >= 75) analysis.signalPowerPut else analysis.signalScore
                           Pair(TradeAction.SELL, "🎯 MT Combo: Tendencia Baja ($score%) -> PUT")
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
                        analysis.trend == TrendDirection.UPTREND && analysis.lastCandles.firstOrNull() == CandleType.GREEN ->
                            Pair(TradeAction.BUY, "🎯 Color Trend: Continuación Alcista -> CALL")
                        analysis.trend == TrendDirection.DOWNTREND && analysis.lastCandles.firstOrNull() == CandleType.RED ->
                            Pair(TradeAction.SELL, "🎯 Color Trend: Continuación Bajista -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.STRIKE_BREAKOUT -> {
                    when {
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
                        analysis.trend == TrendDirection.UPTREND && analysis.lastCandles.firstOrNull() == CandleType.GREEN ->
                            Pair(TradeAction.BUY, "🎯 Tendencia: Continuación Alcista -> CALL")
                        analysis.trend == TrendDirection.DOWNTREND && analysis.lastCandles.firstOrNull() == CandleType.RED ->
                            Pair(TradeAction.SELL, "🎯 Tendencia: Continuación Bajista -> PUT")
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
        }
    }

    fun getStrategyStatusHint(): String {
        if (AutoTradeAccessibilityService.instance == null && mode == AutoTradeMode.AUTONOMOUS) {
            return "⚠️ Accesibilidad DESACTIVADA (Clics bloqueados en Android)"
        }

        val (canTradeStatus, blockReason) = riskManager.canExecuteTrade(mode, autonomousSubMode)
        if (!canTradeStatus && !riskManager.hasPendingTrade && mode == AutoTradeMode.AUTONOMOUS) {
            return "🛑 $blockReason · Toca [MODO] para reanudar"
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
        reasonDescription: String
    ) {
        val (canTrade, reason) = riskManager.canExecuteTrade(mode, autonomousSubMode)
        val actionText = if (action == TradeAction.BUY) "COMPRA / CALL (Sube)" else "VENTA / PUT (Baja)"
        val emoji = if (action == TradeAction.BUY) "🟢 ▲" else "🔴 ▼"

        currentActiveSignal = ActiveSignal(
            action = action,
            title = "$emoji $actionText",
            reason = reasonDescription,
            timestamp = System.currentTimeMillis()
        )

        handler.post {
            onSignalListener?.invoke(action, "$actionText · $reasonDescription")
            val now = System.currentTimeMillis()
            if (now - lastFeedbackEmitTime >= 3500L && canTrade) {
                lastFeedbackEmitTime = now
                emitHapticAndAudioFeedback()
            }
        }

        if (mode == AutoTradeMode.SEMIAUTOMATIC) {
            handler.post {
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, context.resources.displayMetrics.widthPixels.toFloat())
                Toast.makeText(context, "🔔 SEÑAL: $actionText\n$reasonDescription", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (mode == AutoTradeMode.AUTONOMOUS) {
            if (!canTrade) {
                return
            }

            executeAutonomousTrade(action, analysis, bitmap, reasonDescription)
        }
    }

    private fun executeAutonomousTrade(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        bitmap: Bitmap,
        reasonDescription: String
    ) {
        val calibration = calibrationManager
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val isLand = screenW > screenH

        // 1. Prioridad Máxima: Detección visual en tiempo real sobre el frame activo
        val visionCoords = visionAnalyzer.findBrokerButtonCoordinates(bitmap, action == TradeAction.BUY)

        // 2. Respaldo: Calibración guardada o fórmulas geométricas precisas
        val (x, y) = if (visionCoords != null) {
            visionCoords
        } else if (calibration != null && calibration.isCalibrated()) {
            if (action == TradeAction.BUY) calibration.getBuyCoordinates() else calibration.getSellCoordinates()
        } else {
            if (isLand) {
                if (action == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                // Centro exacto del área táctil de los botones en Binomo vertical (89.2% de la pantalla)
                if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.892f)
                else Pair(screenW * 0.75f, screenH * 0.892f)
            }
        }

        android.util.Log.d("TradingEngine", "executeAutonomousTrade: $action disparado hacia ($x, $y) [Visión detectada: ${visionCoords != null}]")

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            val observed = accessibility.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
            val baseBal = if (observed > 0.0) observed else AutoTradeAccessibilityService.latestObservedBalance
            isTradeResolving.set(false)
            riskManager.recordTradeSent(action, analysis.currentPriceY, baseBal)
            // Despacho táctil único e inequívoco (sin repeticiones artificiales)
            accessibility.performClickAt(x, y)

            handler.post {
                drawingView.triggerClickAnimation(x, y)
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, screenW)
                saveAuditScreenshot(bitmap, action)
                Toast.makeText(context, "🤖 BOT OPERÓ: $action ($$${riskManager.getCurrentInvestmentAmount()})\n$reasonDescription", Toast.LENGTH_LONG).show()
            }
        } else {
            handler.post {
                Toast.makeText(context, "⚠️ Clic cancelado: Activa el Servicio de Accesibilidad en Ajustes para Auto-Trading", Toast.LENGTH_LONG).show()
            }
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

    fun stop() {
        mode = AutoTradeMode.DISABLED
        autoDrawEngine.clearAutoDrawings()
    }
}
