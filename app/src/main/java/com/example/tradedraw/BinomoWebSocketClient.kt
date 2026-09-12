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
            val request = Request.Builder()
                .url(wsUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Origin", "https://binomo.com")
                .build()

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
     * Procesa y parsea las tramas de texto recibidas por WebSocket.
     * Soporta múltiples formatos comunes de cotización (JSON de ticks, arrays, socket.io, etc.)
     */
    private fun processIncomingMessage(rawText: String) {
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

                // 1. Formato Binomo: {"action":"tick", "data":{"ric":"Z-CRY/IDX", "rate":4238.125}}
                if (json.has("data")) {
                    val dataObj = json.optJSONObject("data")
                    if (dataObj != null) {
                        if (dataObj.has("rate")) parsedPrice = dataObj.getDouble("rate")
                        else if (dataObj.has("price")) parsedPrice = dataObj.getDouble("price")
                        if (dataObj.has("ric")) assetName = dataObj.getString("ric")
                    } else {
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null && dataArr.length() > 0) {
                            val first = dataArr.getJSONObject(0)
                            if (first.has("rate")) parsedPrice = first.getDouble("rate")
                            else if (first.has("price")) parsedPrice = first.getDouble("price")
                            if (first.has("ric")) assetName = first.getString("ric")
                        }
                    }
                }

                // 2. Formatos directos: {"rate": ...} o {"price": ...}
                if (parsedPrice == null) {
                    if (json.has("rate")) parsedPrice = json.getDouble("rate")
                    else if (json.has("price")) parsedPrice = json.getDouble("price")
                    else if (json.has("close")) parsedPrice = json.getDouble("close")
                }
            } else if (payload.startsWith("[")) {
                val arr = JSONArray(payload)
                if (arr.length() >= 2 && arr.optString(0).contains("tick", ignoreCase = true)) {
                    val tickObj = arr.optJSONObject(1)
                    if (tickObj != null) {
                        parsedPrice = tickObj.optDouble("rate", tickObj.optDouble("price", 0.0))
                    }
                }
            }

            if (parsedPrice != null && parsedPrice > 0.0) {
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
