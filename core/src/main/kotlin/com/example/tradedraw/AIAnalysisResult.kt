package com.example.tradedraw

import com.example.tradedraw.core.TradeAction

data class AIAnalysisResult(
    val action: TradeAction?,
    val confidence: Float,
    val reason: String,
    val isSuccess: Boolean,
    val rawResponse: String? = null,
    val errorMessage: String? = null
)

