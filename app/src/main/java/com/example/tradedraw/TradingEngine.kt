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
    var calibrationManager: CalibrationManager? = null
) {
    var mode: AutoTradeMode = AutoTradeMode.DISABLED
    var strategy: AutoTradeStrategy = AutoTradeStrategy.SUPPORT_RESISTANCE

    private val visionAnalyzer = VisionAnalyzer()
    private val autoDrawEngine = AutoDrawEngine(drawingView)
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 1000L

    var latestAnalysisResult: VisionAnalysisResult? = null
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
            val outcome = visionAnalyzer.detectTradeOutcome(bitmap)
            if (outcome != null) {
                handler.post {
                    if (outcome == TradeOutcome.WIN) {
                        riskManager.recordTradeWin()
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                        onTradeExecutedListener?.invoke(TradeAction.BUY, true)
                    } else {
                        riskManager.recordTradeLoss()
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "⚠️ OPERACIÓN PERDIDA (+1 L)", Toast.LENGTH_LONG).show()
                        onTradeExecutedListener?.invoke(TradeAction.BUY, false)
                    }
                }
            }
        }

        // 2. Obtener niveles de soporte/resistencia dibujados en TradeDraw
        val (supports, resistances) = drawingView.getSupportResistanceYLevels()

        // 3. Analizar frame visual con visión HSV
        val analysis = visionAnalyzer.analyzeChart(bitmap, supports, resistances)
        latestAnalysisResult = analysis

        // 4. Auto-dibujar escenario técnico en TradeDraw según la estrategia
        handler.post {
            autoDrawEngine.updateTechnicalDrawings(strategy, analysis)
            onFrameProcessedListener?.invoke(analysis)
        }

        // 5. Evaluar señal de trading solo si no hay trade abierto
        if (!riskManager.hasPendingTrade) {
            val signal = evaluateStrategySignal(strategy, analysis, supports.isNotEmpty() || resistances.isNotEmpty())
            if (signal != null) {
                handleSignal(signal, analysis, bitmap)
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
        if (riskManager.hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
            val actionName = if (riskManager.pendingTradeAction == TradeAction.BUY) "COMPRA" else "VENTA"
            return "⏳ Operación en curso ($actionName ${elapsed}s) · Esperando resultado..."
        }

        val remaining = riskManager.getRemainingCooldown()
        if (remaining > 0) return "⏳ Cooldown: ${remaining}s"

        val (supports, resistances) = drawingView.getSupportResistanceYLevels()
        val analysis = latestAnalysisResult

        return when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                if (supports.isEmpty() && resistances.isEmpty()) {
                    "✏️ Dibuja un Soporte o Resistencia"
                } else {
                    "🔍 Vigilando rebote en S/R..."
                }
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

    private fun handleSignal(action: TradeAction, analysis: VisionAnalysisResult, bitmap: Bitmap) {
        val (canTrade, reason) = riskManager.canExecuteTrade()
        val actionText = if (action == TradeAction.BUY) "COMPRA (Sube)" else "VENTA (Baja)"

        handler.post {
            onSignalListener?.invoke(action, actionText)
            emitHapticAndAudioFeedback()
        }

        if (mode == AutoTradeMode.SEMIAUTOMATIC) {
            handler.post {
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, context.resources.displayMetrics.widthPixels.toFloat())
                Toast.makeText(context, "🔔 SEÑAL [Semiauto]: $actionText", Toast.LENGTH_SHORT).show()
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

            executeAutonomousTrade(action, analysis, bitmap)
        }
    }

    private fun executeAutonomousTrade(action: TradeAction, analysis: VisionAnalysisResult, bitmap: Bitmap) {
        val calibration = calibrationManager
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()

        val (x, y) = if (calibration != null && calibration.isCalibrated()) {
            if (action == TradeAction.BUY) calibration.getBuyCoordinates() else calibration.getSellCoordinates()
        } else {
            if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.85f)
            else Pair(screenW * 0.75f, screenH * 0.85f)
        }

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            riskManager.recordTradeSent(action)
            accessibility.performClickAt(x, y)

            handler.post {
                autoDrawEngine.drawTradeEntry(action, analysis.currentPriceY, screenW)
                saveAuditScreenshot(bitmap, action)
                onTradeExecutedListener?.invoke(action, true)
                Toast.makeText(context, "🤖 BOT OPERÓ: $action a $${riskManager.getCurrentInvestmentAmount()}", Toast.LENGTH_LONG).show()
            }
        } else {
            handler.post {
                Toast.makeText(context, "Accesibilidad requerida para clic autónomo", Toast.LENGTH_SHORT).show()
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
