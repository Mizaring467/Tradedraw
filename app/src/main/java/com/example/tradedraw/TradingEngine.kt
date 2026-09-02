package com.example.tradedraw

import android.content.Context
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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

class TradingEngine(
    private val context: Context,
    private val drawingView: CustomDrawingView,
    val riskManager: RiskManager,
    var calibrationManager: CalibrationManager? = null,
    val aiClient: AIClient = AIClient(context)
) {
    var mode: AutoTradeMode = AutoTradeMode.DISABLED
    var strategy: AutoTradeStrategy = AutoTradeStrategy.SUPPORT_RESISTANCE
    var debugModeEnabled: Boolean = false

    private val visionAnalyzer = VisionAnalyzer()
    private val autoDrawEngine = AutoDrawEngine(drawingView)
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 1000L

    var latestAnalysisResult: VisionAnalysisResult? = null
        private set
    var latestAIResult: AIAnalysisResult? = null
        private set
    var framesAnalyzedCount: Long = 0L
        private set

    var onSignalListener: ((TradeAction, String) -> Unit)? = null
    var onTradeExecutedListener: ((TradeAction, Boolean) -> Unit)? = null
    var onFrameProcessedListener: ((VisionAnalysisResult) -> Unit)? = null

    fun onNewFrame(bitmap: Bitmap) {
        if (mode == AutoTradeMode.DISABLED) return

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < PROCESS_INTERVAL_MS) return
        lastProcessTime = now
        framesAnalyzedCount++

        // 1. Si hay una operación abierta en curso, comprobar si apareció el resultado (Win / Loss)
        if (riskManager.hasPendingTrade) {
            val elapsedSec = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
            val outcome = visionAnalyzer.detectTradeOutcome(bitmap)
            if (outcome != null) {
                handler.post {
                    if (outcome == TradeOutcome.WIN) {
                        riskManager.recordTradeWin()
                        autoDrawEngine.clearTradeEntry()
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                        onTradeExecutedListener?.invoke(TradeAction.BUY, true)
                    } else {
                        riskManager.recordTradeLoss()
                        autoDrawEngine.clearTradeEntry()
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "⚠️ OPERACIÓN PERDIDA (+1 L)", Toast.LENGTH_LONG).show()
                        onTradeExecutedListener?.invoke(TradeAction.BUY, false)
                    }
                }
            } else if (elapsedSec >= 65) {
                // Timeout de seguridad: En Binomo las operaciones duran 60s. Si pasaron 65s sin banner, desbloquear
                handler.post {
                    riskManager.clearPendingTrade()
                    autoDrawEngine.clearTradeEntry()
                    Toast.makeText(context, "⏱️ Operación finalizada por tiempo (65s)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Obtener niveles de soporte/resistencia dibujados en TradeDraw
        val (supports, resistances) = drawingView.getSupportResistanceYLevels()

        // 3. Analizar frame visual con visión HSV
        val analysis = visionAnalyzer.analyzeChart(bitmap, supports, resistances, context, debugModeEnabled)
        latestAnalysisResult = analysis

        // 4. Auto-dibujar escenario técnico en TradeDraw según la estrategia
        handler.post {
            autoDrawEngine.updateTechnicalDrawings(strategy, analysis)
            if (riskManager.hasPendingTrade) {
                autoDrawEngine.updateTradeEntryLiveStatus(analysis.currentPriceY)
            }
            onFrameProcessedListener?.invoke(analysis)
        }

        // 5. Evaluar señal de trading solo si no hay trade abierto
        if (!riskManager.hasPendingTrade) {
            val localSignal = evaluateStrategySignal(strategy, analysis, supports.isNotEmpty() || resistances.isNotEmpty())

            if (aiClient.isEnabled && aiClient.apiKey.isNotBlank()) {
                // Mantener el análisis de IA continuo en vivo (OmniRoute / Gemini)
                aiClient.analyzeFrame(bitmap) { aiResult ->
                    latestAIResult = aiResult
                    handler.post { onFrameProcessedListener?.invoke(analysis) }

                    if (aiResult.isSuccess && aiResult.action != null && aiResult.confidence >= aiClient.confidenceThreshold) {
                        if (!riskManager.hasPendingTrade) {
                            val pct = (aiResult.confidence * 100).toInt()
                            handleSignal(aiResult.action, analysis, bitmap, "IA ($pct%): ${aiResult.reason}")
                        }
                    } else if (localSignal != null && !riskManager.hasPendingTrade) {
                        // Si la IA no tiene señal concluyente pero las reglas técnicas sí, operar
                        handleSignal(localSignal, analysis, bitmap, "Reglas Técnicas Locales")
                    }
                }
            } else if (localSignal != null) {
                // Ruta 100% Local (sin IA)
                handleSignal(localSignal, analysis, bitmap, "Reglas Técnicas Locales")
            }
        }
    }

    private fun evaluateStrategySignal(
        strategy: AutoTradeStrategy,
        analysis: VisionAnalysisResult,
        hasDrawnLines: Boolean
    ): TradeAction? {
        return when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                if (analysis.touchesSupport) {
                    TradeAction.BUY
                } else if (analysis.touchesResistance) {
                    TradeAction.SELL
                } else null
            }
            AutoTradeStrategy.MT_REJECTION -> {
                // Estrategia Master Traders 1: Mechas de Rechazo en niveles clave
                if (analysis.hasBottomRejectionWick || (analysis.touchesSupport && analysis.lastCandles.firstOrNull() == CandleType.RED)) {
                    TradeAction.BUY // Rechazo por abajo (compradores absorben) -> COMPRA
                } else if (analysis.hasTopRejectionWick || (analysis.touchesResistance && analysis.lastCandles.firstOrNull() == CandleType.GREEN)) {
                    TradeAction.SELL // Rechazo por arriba (vendedores absorben) -> VENTA
                } else null
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                // Estrategia Master Traders 2: Choque de Máximos y Mínimos (Breakout + Retest)
                if (analysis.isChoquePullbackCall) {
                    TradeAction.BUY // Retest a resistencia rota convertida en soporte -> COMPRA
                } else if (analysis.isChoquePullbackPut) {
                    TradeAction.SELL // Retest a soporte roto convertido en resistencia -> VENTA
                } else null
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                // Estrategia Master Traders 3: Agotamiento de 3 Velas consecutivas
                if (analysis.isExhaustion3CandlesCall) {
                    TradeAction.BUY // 3 rojas con cuerpo decreciente -> Reversión COMPRA
                } else if (analysis.isExhaustion3CandlesPut) {
                    TradeAction.SELL // 3 verdes con cuerpo decreciente -> Reversión VENTA
                } else null
            }
            AutoTradeStrategy.MT_MASTER_COMBO -> {
                // Combo Master Traders: Prioriza Rechazo > Choque > Agotamiento > Fuerza de Señal
                if (analysis.hasBottomRejectionWick || analysis.touchesSupport) {
                    TradeAction.BUY
                } else if (analysis.hasTopRejectionWick || analysis.touchesResistance) {
                    TradeAction.SELL
                } else if (analysis.isChoquePullbackCall) {
                    TradeAction.BUY
                } else if (analysis.isChoquePullbackPut) {
                    TradeAction.SELL
                } else if (analysis.isExhaustion3CandlesCall) {
                    TradeAction.BUY
                } else if (analysis.isExhaustion3CandlesPut) {
                    TradeAction.SELL
                } else if (analysis.signalPowerCall >= 68) {
                    TradeAction.BUY
                } else if (analysis.signalPowerPut >= 68) {
                    TradeAction.SELL
                } else null
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                if (analysis.consecutiveCount >= 3) {
                    if (analysis.lastCandles.firstOrNull() == CandleType.RED) {
                        TradeAction.BUY
                    } else if (analysis.lastCandles.firstOrNull() == CandleType.GREEN) {
                        TradeAction.SELL
                    } else null
                } else if (analysis.isHammer) {
                    TradeAction.BUY
                } else null
            }
            AutoTradeStrategy.TREND_FOLLOWING -> {
                if (analysis.trend == TrendDirection.UPTREND && analysis.lastCandles.firstOrNull() == CandleType.GREEN) {
                    TradeAction.BUY
                } else if (analysis.trend == TrendDirection.DOWNTREND && analysis.lastCandles.firstOrNull() == CandleType.RED) {
                    TradeAction.SELL
                } else null
            }
            AutoTradeStrategy.COMBINED -> {
                if (analysis.touchesSupport && analysis.lastCandles.firstOrNull() != CandleType.RED) {
                    TradeAction.BUY
                } else if (analysis.touchesResistance && analysis.lastCandles.firstOrNull() != CandleType.GREEN) {
                    TradeAction.SELL
                } else null
            }
        }
    }

    fun getStrategyStatusHint(): String {
        if (AutoTradeAccessibilityService.instance == null && mode == AutoTradeMode.AUTONOMOUS) {
            return "⚠️ Accesibilidad DESACTIVADA (Clics bloqueados en Android)"
        }

        if (riskManager.hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
            val actionName = if (riskManager.pendingTradeAction == TradeAction.BUY) "COMPRA" else "VENTA"
            return "⏳ Operación en curso ($actionName ${elapsed}s) · Esperando resultado..."
        }

        val remaining = riskManager.getRemainingCooldown()
        if (remaining > 0) return "⏳ Cooldown: ${remaining}s"

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

        return when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                "🔍 Vigilando rebote en Soporte / Resistencia en vivo..."
            }
            AutoTradeStrategy.MT_REJECTION -> {
                if (analysis != null && (analysis.hasTopRejectionWick || analysis.hasBottomRejectionWick)) {
                    "⚡ Mecha de rechazo detectada! Evaluando entrada..."
                } else {
                    "🔍 MT: Buscando mechas de rechazo en niveles..."
                }
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                if (analysis != null && (analysis.isChoquePullbackCall || analysis.isChoquePullbackPut)) {
                    "⚡ Choque con nivel roto detectado! Preparando trade..."
                } else {
                    "🔍 MT: Buscando choque de máximos/mínimos..."
                }
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                if (analysis != null && analysis.consecutiveCount >= 2) {
                    "📊 MT: Racha ${analysis.consecutiveCount} velas. Vigilando agotamiento..."
                } else {
                    "🔍 MT: Esperando patrón de 3 velas..."
                }
            }
            AutoTradeStrategy.MT_MASTER_COMBO -> {
                "🔥 MT Master Combo: Vigilando Rechazo, Choque y Agotamiento..."
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
        val (canTrade, reason) = riskManager.canExecuteTrade()
        val actionText = if (action == TradeAction.BUY) "COMPRA (Sube)" else "VENTA (Baja)"

        handler.post {
            onSignalListener?.invoke(action, "$actionText · $reasonDescription")
            emitHapticAndAudioFeedback()
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
                handler.post {
                    Toast.makeText(context, "IA Pausada: $reason", Toast.LENGTH_SHORT).show()
                }
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

        val (x, y) = if (calibration != null && calibration.isCalibrated()) {
            if (action == TradeAction.BUY) calibration.getBuyCoordinates() else calibration.getSellCoordinates()
        } else {
            val isLand = screenW > screenH
            if (isLand) {
                if (action == TradeAction.BUY) Pair(screenW * 0.88f, screenH * 0.72f)
                else Pair(screenW * 0.88f, screenH * 0.86f)
            } else {
                if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.88f)
                else Pair(screenW * 0.75f, screenH * 0.88f)
            }
        }

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            riskManager.recordTradeSent(action)
            accessibility.performClickAt(x, y)

            handler.post {
                drawingView.triggerClickAnimation(x, y)
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, screenW)
                saveAuditScreenshot(bitmap, action)
                onTradeExecutedListener?.invoke(action, true)
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
            Toast.makeText(context, "❌ Servicio de Accesibilidad NO conectado. Actívalo en Ajustes.", Toast.LENGTH_LONG).show()
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
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
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
