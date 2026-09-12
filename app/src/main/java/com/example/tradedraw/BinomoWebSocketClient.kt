package com.example.tradedraw

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cliente WebSocket nativo para recepción de micro-ticks de cotización en tiempo real.
 * Se conecta de forma asíncrona mediante OkHttp y procesa cotizaciones sin necesidad de captura de pantalla.
 */
class BinomoWebSocketClient(private val context: Context) {

    private val TAG = "BinomoWebSocketClient"

    private val prefs = context.getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)

    var wsUrl: String
        get() = prefs.getString("ws_endpoint_url", "wss://ws.binomo.com/") ?: "wss://ws.binomo.com/"
        set(value) = prefs.edit().putString("ws_endpoint_url", value.trim()).apply()

    var activeAsset: String
        get() = prefs.getString("ws_active_asset", "Z-CRY/IDX") ?: "Z-CRY/IDX"
        set(value) = prefs.edit().putString("ws_active_asset", value.trim()).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean("ws_client_enabled", true)
        set(value) = prefs.edit().putBoolean("ws_client_enabled", value).apply()

    var authToken: String
        get() = prefs.getString("ws_auth_token", "") ?: ""
        set(value) = prefs.edit().putString("ws_auth_token", value.trim()).apply()

    var deviceId: String
        get() = prefs.getString("ws_device_id", "") ?: ""
        set(value) = prefs.edit().putString("ws_device_id", value.trim()).apply()

    var cookieHeader: String
        get() = prefs.getString("ws_cookie_header", "") ?: ""
        set(value) = prefs.edit().putString("ws_cookie_header", value.trim()).apply()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sin timeout de lectura para websockets continuos
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnecting = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)

    private val workerThread = HandlerThread("BinomoWSWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    var currentState: WebSocketState = WebSocketState.DISCONNECTED
        private set

    var onTickListener: ((MarketTick) -> Unit)? = null
    var onStateChangeListener: ((WebSocketState, String?) -> Unit)? = null

    // Tracking de micro-velocidad de ticks
    private var lastPrice: Double = 0.0
    private var lastPriceTimeMs: Long = 0L
    private val recentTicks = ArrayDeque<MarketTick>(16)

    var latestTick: MarketTick? = null
        private set

    private var reconnectAttempts = 0
    private val MAX_RECONNECT_DELAY_MS = 15000L

    private val reconnectRunnable = Runnable {
        if (!isConnected.get() && isEnabled) {
            Log.d(TAG, "Reintentando conexión WebSocket (intento #$reconnectAttempts)...")
            startConnectionInternal()
        }
    }

    fun start() {
        if (!isEnabled) {
            Log.d(TAG, "WebSocket desactivado por configuración.")
            return
        }
        workerHandler.post { startConnectionInternal() }
    }

    fun stop() {
        workerHandler.post {
            reconnectAttempts = 0
            workerHandler.removeCallbacks(reconnectRunnable)
            try {
                webSocket?.close(1000, "Cierre voluntario TradeDraw")
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando WebSocket", e)
            }
            webSocket = null
            isConnected.set(false)
            isConnecting.set(false)
            updateState(WebSocketState.DISCONNECTED, "Desconectado")
        }
    }

    private fun startConnectionInternal() {
        if (isConnected.get() || isConnecting.get()) return

        isConnecting.set(true)
        updateState(WebSocketState.CONNECTING, "Conectando a $wsUrl")

        try {
            var targetUrl = wsUrl
            val token = authToken
            val devId = deviceId

            if (token.isNotEmpty() && !targetUrl.contains("authtoken=")) {
                val sep = if (targetUrl.contains("?")) "&" else "?"
                targetUrl = "$targetUrl${sep}authtoken=$token"
            }

            val cookies = if (cookieHeader.isNotEmpty()) cookieHeader else if (token.isNotEmpty()) "authtoken=$token" else ""
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Origin", "https://binomo.com")

            if (cookies.isNotEmpty()) {
                reqBuilder.header("Cookie", cookies)
            }
            if (token.isNotEmpty()) {
                reqBuilder.header("authtoken", token)
            }
            if (devId.isNotEmpty()) {
                reqBuilder.header("device-id", devId)
            }

            val request = reqBuilder.build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    isConnecting.set(false)
                    isConnected.set(true)
                    reconnectAttempts = 0
                    updateState(WebSocketState.CONNECTED, "Conexión activa con broker")
                    Log.d(TAG, "WebSocket conectado exitosamente: $wsUrl")

                    // Suscripción al activo configurado (ej. Z-CRY/IDX)
                    subscribeToAsset(ws, activeAsset)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    processIncomingMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket cerrando: code=$code, reason=$reason")
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    isConnecting.set(false)
                    isConnected.set(false)
                    updateState(WebSocketState.DISCONNECTED, "Cerrado: $reason")
                    scheduleReconnect()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    isConnecting.set(false)
                    isConnected.set(false)
                    val errorMsg = t.message ?: "Fallo de red"
                    val is401 = response?.code == 401 || errorMsg.contains("401")
                    if (is401) {
                        Log.w(TAG, "Fallo WebSocket 401 Unauthorized: Requiere token de sesión de Binomo")
                        updateState(WebSocketState.UNAUTHORIZED, "401 Unauthorized: Requiere Token/Login de Binomo")
                        return
                    }
                    Log.w(TAG, "Fallo en conexión WebSocket: $errorMsg")
                    updateState(WebSocketState.ERROR, errorMsg)
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            isConnecting.set(false)
            isConnected.set(false)
            Log.e(TAG, "Excepción al iniciar conexión WebSocket", e)
            updateState(WebSocketState.ERROR, e.message)
            scheduleReconnect()
        }
    }

    private fun subscribeToAsset(ws: WebSocket, asset: String) {
        try {
            // Handshake estándar de suscripción de ticks Binomo / broker
            val subscribePayload = JSONObject().apply {
                put("action", "subscribe")
                put("data", JSONArray().apply {
                    put(JSONObject().apply {
                        put("ric", asset)
                    })
                })
            }
            ws.send(subscribePayload.toString())
            Log.d(TAG, "Suscripción enviada para activo: $asset")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando suscripción", e)
        }
    }

    /**
     * Procesa y parsea las tramas de texto recibidas por WebSocket o bridge Headless.
     * Soporta múltiples formatos comunes de cotización (JSON de ticks, arrays, socket.io, etc.)
     */
    fun processIncomingMessage(rawText: String) {
        try {
            var payload = rawText.trim()

            // Manejo de prefijos Socket.IO (ej. 42["tick", {...}])
            if (payload.startsWith("42")) {
                payload = payload.substring(2)
            } else if (payload == "2") {
                // Heartbeat ping de Socket.IO -> responder pong "3"
                webSocket?.send("3")
                return
            }

            var parsedPrice: Double? = null
            var assetName: String = activeAsset
            val nowMs = System.currentTimeMillis()

            if (payload.startsWith("{")) {
                val json = JSONObject(payload)

                // 1. Formatos con contenedor "data" (Objetos o Arrays con "assets" o directos)
                if (json.has("data")) {
                    val dataObj = json.optJSONObject("data")
                    if (dataObj != null) {
                        val assetsArr = dataObj.optJSONArray("assets")
                        if (assetsArr != null && assetsArr.length() > 0) {
                            for (i in 0 until assetsArr.length()) {
                                val assetObj = assetsArr.optJSONObject(i) ?: continue
                                val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                                val ric = assetObj.optString("ric", "")
                                if (!r.isNaN() && r > 0.0) {
                                    if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                        parsedPrice = r
                                        if (ric.isNotEmpty()) assetName = ric
                                        if (ric.equals(activeAsset, ignoreCase = true)) break
                                    }
                                }
                            }
                        } else {
                            if (dataObj.has("rate")) parsedPrice = dataObj.getDouble("rate")
                            else if (dataObj.has("price")) parsedPrice = dataObj.getDouble("price")
                            if (dataObj.has("ric")) assetName = dataObj.getString("ric")
                        }
                    } else {
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null && dataArr.length() > 0) {
                            for (i in 0 until dataArr.length()) {
                                val item = dataArr.optJSONObject(i) ?: continue
                                val assetsArr = item.optJSONArray("assets")
                                if (assetsArr != null && assetsArr.length() > 0) {
                                    for (j in 0 until assetsArr.length()) {
                                        val assetObj = assetsArr.optJSONObject(j) ?: continue
                                        val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                                        val ric = assetObj.optString("ric", "")
                                        if (!r.isNaN() && r > 0.0) {
                                            if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                                parsedPrice = r
                                                if (ric.isNotEmpty()) assetName = ric
                                                if (ric.equals(activeAsset, ignoreCase = true)) break
                                            }
                                        }
                                    }
                                } else {
                                    val r = if (item.has("rate")) item.optDouble("rate") else if (item.has("price")) item.optDouble("price") else Double.NaN
                                    val ric = item.optString("ric", "")
                                    if (!r.isNaN() && r > 0.0) {
                                        if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                            parsedPrice = r
                                            if (ric.isNotEmpty()) assetName = ric
                                            if (ric.equals(activeAsset, ignoreCase = true)) break
                                        }
                                    }
                                }
                                if (parsedPrice != null && assetName.equals(activeAsset, ignoreCase = true)) break
                            }
                        }
                    }
                }

                // 2. Formato con "assets" en raíz
                if (parsedPrice == null && json.has("assets")) {
                    val assetsArr = json.optJSONArray("assets")
                    if (assetsArr != null && assetsArr.length() > 0) {
                        for (i in 0 until assetsArr.length()) {
                            val assetObj = assetsArr.optJSONObject(i) ?: continue
                            val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                            val ric = assetObj.optString("ric", "")
                            if (!r.isNaN() && r > 0.0) {
                                if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                    parsedPrice = r
                                    if (ric.isNotEmpty()) assetName = ric
                                    if (ric.equals(activeAsset, ignoreCase = true)) break
                                }
                            }
                        }
                    }
                }

                // 3. Formatos directos en la raíz: {"rate": ...} o {"price": ...} o {"close": ...}
                if (parsedPrice == null) {
                    if (json.has("rate")) parsedPrice = json.getDouble("rate")
                    else if (json.has("price")) parsedPrice = json.getDouble("price")
                    else if (json.has("close")) parsedPrice = json.getDouble("close")
                    if (json.has("ric")) assetName = json.getString("ric")
                }
            } else if (payload.startsWith("[")) {
                val arr = JSONArray(payload)
                if (arr.length() >= 2 && arr.optString(0).contains("tick", ignoreCase = true)) {
                    val tickObj = arr.optJSONObject(1)
                    if (tickObj != null) {
                        parsedPrice = tickObj.optDouble("rate", tickObj.optDouble("price", 0.0))
                        if (tickObj.has("ric")) assetName = tickObj.getString("ric")
                    }
                }
            }

            // 4. Extractor Regex de respaldo si parsedPrice sigue en null
            if (parsedPrice == null || parsedPrice <= 0.0) {
                // Si el payload contiene el activo activo (ej. Z-CRY/IDX), buscar preferentemente el bloque asociado
                val activeBlock = Regex("""\{[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(payload)?.value
                    ?: Regex("""\{[^{}]*"rate"\s*:\s*[0-9.]+[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(payload)?.value
                    ?: payload

                val rateMatch = Regex(""""rate"\s*:\s*([0-9.]+)""").find(activeBlock)
                val priceMatch = if (rateMatch == null) Regex(""""price"\s*:\s*([0-9.]+)""").find(activeBlock) else null
                val matchedVal = rateMatch?.groupValues?.getOrNull(1) ?: priceMatch?.groupValues?.getOrNull(1)
                if (matchedVal != null) {
                    val p = matchedVal.toDoubleOrNull()
                    if (p != null && p > 0.0) {
                        parsedPrice = p
                    }
                }

                val ricMatch = Regex(""""ric"\s*:\s*"([^"]+)"""").find(activeBlock)
                if (ricMatch != null) {
                    val matchedRic = ricMatch.groupValues[1]
                    if (matchedRic.isNotEmpty()) {
                        assetName = matchedRic
                    }
                }
            }

            if (parsedPrice != null && parsedPrice > 0.0) {
                if (currentState != WebSocketState.CONNECTED) {
                    updateState(WebSocketState.CONNECTED, "Conexión activa con broker (0ms)")
                }
                emitTick(assetName, parsedPrice, nowMs)
            }
        } catch (e: Exception) {
            // Mensaje informativo o frame no relacionado a precios
        }
    }

    private fun emitTick(asset: String, price: Double, timestampMs: Long) {
        var velocity = 0f
        var isBullish = false
        var isBearish = false

        if (lastPrice > 0.0 && lastPriceTimeMs > 0L) {
            val dtSec = ((timestampMs - lastPriceTimeMs).coerceAtLeast(10L)) / 1000f
            val dp = (price - lastPrice)
            velocity = (dp / dtSec).toFloat()

            // Umbrales de micro-impulso instantáneo
            if (velocity > 0.0005f) isBullish = true
            else if (velocity < -0.0005f) isBearish = true
        }

        lastPrice = price
        lastPriceTimeMs = timestampMs

        val tick = MarketTick(
            asset = asset,
            price = price,
            timestampMs = timestampMs,
            velocity = velocity,
            isBullishImpulse = isBullish,
            isBearishImpulse = isBearish
        )

        latestTick = tick

        synchronized(recentTicks) {
            if (recentTicks.size >= 16) recentTicks.removeFirst()
            recentTicks.addLast(tick)
        }

        mainHandler.post {
            onTickListener?.invoke(tick)
        }
    }

    private fun scheduleReconnect() {
        if (!isEnabled) return
        reconnectAttempts++
        val delay = (reconnectAttempts * 2000L).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        updateState(WebSocketState.RECONNECTING, "Reconectando en ${delay / 1000}s...")
        workerHandler.removeCallbacks(reconnectRunnable)
        workerHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun updateState(newState: WebSocketState, details: String?) {
        currentState = newState
        mainHandler.post {
            onStateChangeListener?.invoke(newState, details)
        }
    }

    fun destroy() {
        stop()
        workerThread.quitSafely()
    }
}
