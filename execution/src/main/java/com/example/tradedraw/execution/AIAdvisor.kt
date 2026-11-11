package com.example.tradedraw.execution

import android.graphics.Bitmap
import com.example.tradedraw.AIAnalysisResult

interface AIAdvisor {
    var isEnabled: Boolean
    var baseUrl: String
    var apiKey: String
    var model: String
    var confidenceThreshold: Float
    fun analyzeFrame(bitmap: Bitmap, onResult: (AIAnalysisResult) -> Unit)
    fun testConnection(onResult: (Boolean, String) -> Unit)
    fun sendChatMessage(userMessage: String, marketContext: String, onResponse: (String, String?) -> Unit)
    fun destroy()
}
