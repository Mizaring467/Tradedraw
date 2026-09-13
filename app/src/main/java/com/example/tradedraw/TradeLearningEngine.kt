package com.example.tradedraw

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import kotlin.math.abs

/**
 * Representa los factores contextuales al momento de ejecutar un trade.
 */
data class TradeContext(
    val topWickRatio: Float,
    val bottomWickRatio: Float,
    val consecutiveCount: Int,
    val touchesSupport: Boolean,
    val touchesResistance: Boolean,
    val isCall: Boolean,
    var won: Boolean? = null
)

/**
 * Motor de autoaprendizaje para registrar y analizar factores de operaciones.
 * Predice la fiabilidad basada en el historial de operaciones similares.
 */
object TradeLearningEngine {
    private const val TAG = "TradeLearningEngine"
    private const val MIN_SAMPLES = 5
    private val history = mutableListOf<TradeContext>()

    /**
     * Extrae el contexto a partir del análisis visual.
     */
    private fun extractContext(analysis: VisionAnalysisResult, action: TradeAction): TradeContext {
        var topWickRatio = 0f
        var bottomWickRatio = 0f
        val latestCandle = analysis.candleList.firstOrNull()
        if (latestCandle != null) {
            topWickRatio = latestCandle.topWickRatio
            bottomWickRatio = latestCandle.bottomWickRatio
        }

        return TradeContext(
            topWickRatio = topWickRatio,
            bottomWickRatio = bottomWickRatio,
            consecutiveCount = analysis.consecutiveCount,
            touchesSupport = analysis.touchesSupport,
            touchesResistance = analysis.touchesResistance,
            isCall = action == TradeAction.BUY
        )
    }

    /**
     * Registra un trade para aprendizaje futuro.
     * @param won true si se ganó, false si se perdió.
     */
    fun recordTrade(analysis: VisionAnalysisResult, action: TradeAction, won: Boolean, context: Context? = null) {
        val ctx = extractContext(analysis, action).apply { this.won = won }
        history.add(ctx)
        Log.d(TAG, "Trade registrado: $ctx")
        if (context != null) {
            persistTrade(context, ctx)
        }
    }

    /**
     * Predice la fiabilidad (0.0 a 1.0) de un trade comparándolo con el historial.
     */
    fun predictReliability(analysis: VisionAnalysisResult, action: TradeAction): Float {
        if (history.size < MIN_SAMPLES) return 0.5f // Neutral si hay pocos datos

        val currentCtx = extractContext(analysis, action)

        var matchCount = 0
        var winCount = 0

        for (pastCtx in history) {
            if (pastCtx.won == null) continue

            // Condiciones de similitud
            val actionMatch = pastCtx.isCall == currentCtx.isCall
            val consecutiveMatch = pastCtx.consecutiveCount == currentCtx.consecutiveCount
            val supportMatch = pastCtx.touchesSupport == currentCtx.touchesSupport
            val resistanceMatch = pastCtx.touchesResistance == currentCtx.touchesResistance
            val topWickSimilar = abs(pastCtx.topWickRatio - currentCtx.topWickRatio) < 0.2f
            val bottomWickSimilar = abs(pastCtx.bottomWickRatio - currentCtx.bottomWickRatio) < 0.2f

            // Consideramos una coincidencia si la mayoría de los factores clave se alinean
            var similarityScore = 0
            if (actionMatch) similarityScore++
            if (consecutiveMatch) similarityScore++
            if (supportMatch) similarityScore++
            if (resistanceMatch) similarityScore++
            if (topWickSimilar) similarityScore++
            if (bottomWickSimilar) similarityScore++

            if (similarityScore >= 4) { // Umbral de similitud
                matchCount++
                if (pastCtx.won == true) {
                    winCount++
                }
            }
        }

        if (matchCount < 3) return 0.5f // Muy pocas similitudes

        val reliability = winCount.toFloat() / matchCount.toFloat()
        Log.d(TAG, "Fiabilidad predicha: $reliability (Wins: $winCount / Matches: $matchCount)")
        return reliability
    }

    private fun persistTrade(context: Context, trade: TradeContext) {
        try {
            val dir = File(context.getExternalFilesDir(null), "TradeDraw_Learning")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "learning_history.csv")

            val writeHeader = !file.exists() || file.length() == 0L
            FileWriter(file, true).use { writer ->
                if (writeHeader) {
                    writer.write("isCall,won,topWickRatio,bottomWickRatio,consecutiveCount,touchesSupport,touchesResistance\n")
                }
                writer.write("${trade.isCall},${trade.won},${trade.topWickRatio},${trade.bottomWickRatio},${trade.consecutiveCount},${trade.touchesSupport},${trade.touchesResistance}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error persistiendo historial de aprendizaje", e)
        }
    }
}
