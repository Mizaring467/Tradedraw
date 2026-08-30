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

    var onSignalListener: ((TradeAction, String) -> Unit)? = null
    var onTradeExecutedListener: ((TradeAction, Boolean) -> Unit)? = null

    fun onNewFrame(bitmap: Bitmap) {
        if (mode == AutoTradeMode.DISABLED) return

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < PROCESS_INTERVAL_MS) return
        lastProcessTime = now

        // 1. Obtener niveles de soporte/resistencia dibujados en TradeDraw
        val (supports, resistances) = drawingView.getSupportResistanceYLevels()

        // 2. Analizar frame visual
        val analysis = visionAnalyzer.analyzeChart(bitmap, supports, resistances)

        // 3. Auto-dibujar escenario técnico en TradeDraw según la estrategia
        handler.post {
            autoDrawEngine.updateTechnicalDrawings(strategy, analysis)
        }

        // 4. Evaluar señal de trading
        val signal = evaluateStrategySignal(strategy, analysis)

        if (signal != null) {
            handleSignal(signal, analysis, bitmap)
        }
    }

    private fun evaluateStrategySignal(
        strategy: AutoTradeStrategy,
        analysis: VisionAnalysisResult
    ): TradeAction? {
        return when (strategy) {
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                if (analysis.touchesSupport) {
                    TradeAction.BUY // Rebote en soporte -> Compra
                } else if (analysis.touchesResistance) {
                    TradeAction.SELL // Rebote en resistencia -> Venta
                } else null
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                // Estrategia de 3 velas consecutivas o martillo
                if (analysis.consecutiveCount >= 3) {
                    if (analysis.lastCandles.firstOrNull() == CandleType.RED) {
                        TradeAction.BUY // 3 velas rojas -> posible reversión alcista
                    } else if (analysis.lastCandles.firstOrNull() == CandleType.GREEN) {
                        TradeAction.SELL // 3 velas verdes -> posible reversión bajista
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
                // Confirmación doble: Soporte + Vela o Tendencia + Vela
                if (analysis.touchesSupport && analysis.lastCandles.firstOrNull() != CandleType.RED) {
                    TradeAction.BUY
                } else if (analysis.touchesResistance && analysis.lastCandles.firstOrNull() != CandleType.GREEN) {
                    TradeAction.SELL
                } else null
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
                Toast.makeText(context, "🔔 SEÑAL [Semiautomático]: $actionText\n(Estrategia: ${strategy.name})", Toast.LENGTH_SHORT).show()
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

            // Ejecutar trade automático
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
            // Coordenadas de respaldo (típicas en binarias móvil)
            if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.85f)
            else Pair(screenW * 0.75f, screenH * 0.85f)
        }

        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility != null) {
            riskManager.recordTradeSent()
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
