package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AIAnalysisResult(
    val action: TradeAction?,
    val confidence: Float,
    val reason: String,
    val isSuccess: Boolean,
    val rawResponse: String? = null,
    val errorMessage: String? = null
)

class AIClient(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("TradeDraw_AIConfig", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", true) // Activado por defecto
        set(value) = prefs.edit().putBoolean("ai_enabled", value).apply()

    var baseUrl: String
        get() = prefs.getString("ai_base_url", "https://api.b.ai/v1") ?: "https://api.b.ai/v1"
        set(value) = prefs.edit().putString("ai_base_url", value.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString("ai_api_key", "sk-9lt4tdgldm7tt48ylqkf693nouje0spi") ?: "sk-9lt4tdgldm7tt48ylqkf693nouje0spi"
        set(value) = prefs.edit().putString("ai_api_key", value.trim()).apply()

    var model: String
        get() = prefs.getString("ai_model", "deepseek-v4-flash-vision-exp") ?: "deepseek-v4-flash-vision-exp"
        set(value) = prefs.edit().putString("ai_model", value.trim()).apply()

    var confidenceThreshold: Float
        get() = prefs.getFloat("ai_confidence", 0.60f)
        set(value) = prefs.edit().putFloat("ai_confidence", value).apply()

    private val workerThread = HandlerThread("AIClientWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isRequestInProgress = false

    fun analyzeFrame(bitmap: Bitmap, onResult: (AIAnalysisResult) -> Unit) {
        if (!isEnabled || apiKey.isBlank()) {
            onResult(AIAnalysisResult(null, 0f, "IA no configurada", false, errorMessage = "Sin API Key"))
            return
        }

        if (isRequestInProgress) {
            return
        }

        isRequestInProgress = true

        workerHandler.post {
            try {
                // 1. Redimensionar y comprimir bitmap a JPEG base64 (ancho máx 640px para respuesta en milisegundos)
                val base64Image = bitmapToBase64Jpeg(bitmap, 640, 75)

                // 2. Construir payload compatible con OpenAI / B.AI
                val jsonPayload = buildVisionPayload(base64Image, model)

                // 3. Ejecutar petición HTTP POST
                val urlString = "$baseUrl/chat/completions"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonPayload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseAIResponse(responseText)
                    mainHandler.post { onResult(parsed) }
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    Log.e("AIClient", "Error API ($responseCode): $errorText")
                    mainHandler.post {
                        onResult(AIAnalysisResult(null, 0f, "Error HTTP $responseCode", false, errorMessage = errorText))
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("AIClient", "Excepción al consultar IA", e)
                mainHandler.post {
                    onResult(AIAnalysisResult(null, 0f, "Fallo de conexión", false, errorMessage = e.message))
                }
            } finally {
                isRequestInProgress = false
            }
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        if (apiKey.isBlank()) {
            onResult(false, "La API Key está vacía")
            return
        }

        workerHandler.post {
            try {
                val urlString = "$baseUrl/chat/completions"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                val payload = JSONObject().apply {
                    put("model", model)
                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Responde exactamente con: {\"status\":\"ok\"}")
                        })
                    }
                    put("messages", messages)
                    put("max_tokens", 250)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    mainHandler.post { onResult(true, "Conexión exitosa con B.AI ($model)") }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    mainHandler.post { onResult(false, "Error $responseCode: $err") }
                }
                conn.disconnect()
            } catch (e: Exception) {
                mainHandler.post { onResult(false, "Error de red: ${e.localizedMessage}") }
            }
        }
    }

    private fun bitmapToBase64Jpeg(source: Bitmap, targetWidth: Int, quality: Int): String {
        val aspect = source.height.toFloat() / source.width.toFloat()
        val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun buildVisionPayload(dataUri: String, modelName: String): JSONObject {
        val payload = JSONObject()
        payload.put("model", modelName)
        payload.put("temperature", 0.15)
        payload.put("max_tokens", 500)

        val messages = JSONArray()

        val systemMsg = JSONObject().apply {
            put("role", "system")
            put("content", "Eres un asistente de trading experto en análisis de velas para opciones binarias a 1 minuto en Binomo. Analiza la captura del gráfico y responde OBLIGATORIAMENTE en formato JSON con esta estructura exacta:\n{\"action\": \"BUY\"|\"SELL\"|\"WAIT\", \"confidence\": 0.0 a 1.0, \"reason\": \"motivo breve en español (máximo 12 palabras)\"}\nNo agregues texto fuera del JSON.")
        }
        messages.put(systemMsg)

        val userMsg = JSONObject().apply {
            put("role", "user")
            val contentArray = JSONArray()

            val textPart = JSONObject().apply {
                put("type", "text")
                put("text", "Analiza la tendencia actual, la última vela y posibles rebotes o rompimientos en soporte/resistencia. Responde solo con el JSON requerido.")
            }
            contentArray.put(textPart)

            val imagePart = JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", dataUri)
                })
            }
            contentArray.put(imagePart)

            put("content", contentArray)
        }
        messages.put(userMsg)

        payload.put("messages", messages)
        return payload
    }

    private fun parseAIResponse(rawJson: String): AIAnalysisResult {
        try {
            val root = JSONObject(rawJson)
            val choices = root.getJSONArray("choices")
            if (choices.length() == 0) {
                return AIAnalysisResult(null, 0f, "Respuesta vacía", false, rawResponse = rawJson)
            }

            val messageObj = choices.getJSONObject(0).getJSONObject("message")
            val contentText = messageObj.getString("content").trim()

            // Extraer JSON si el modelo lo encerró en bloques ```json ... ```
            val cleanedJson = if (contentText.contains("{") && contentText.contains("}")) {
                val start = contentText.indexOf('{')
                val end = contentText.lastIndexOf('}')
                contentText.substring(start, end + 1)
            } else {
                contentText
            }

            val parsedContent = JSONObject(cleanedJson)
            val actionStr = parsedContent.optString("action", "WAIT").uppercase()
            val confidence = parsedContent.optDouble("confidence", 0.5).toFloat()
            val reason = parsedContent.optString("reason", "Análisis de IA completado")

            val action = when (actionStr) {
                "BUY", "COMPRA", "UP", "SUBE" -> TradeAction.BUY
                "SELL", "VENTA", "DOWN", "BAJA" -> TradeAction.SELL
                else -> null
            }

            return AIAnalysisResult(
                action = action,
                confidence = confidence,
                reason = reason,
                isSuccess = true,
                rawResponse = contentText
            )
        } catch (e: Exception) {
            Log.e("AIClient", "Error parseando respuesta de IA: $rawJson", e)
            return AIAnalysisResult(null, 0f, "Error parseando JSON", false, rawResponse = rawJson, errorMessage = e.message)
        }
    }

    fun destroy() {
        try {
            workerThread.quitSafely()
        } catch (e: Exception) {}
    }
}
