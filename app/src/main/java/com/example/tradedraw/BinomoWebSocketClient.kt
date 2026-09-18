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

    fun updateActiveAsset(newAsset: String) {
        val clean = newAsset.trim()
        if (clean.isNotBlank() && activeAsset != clean) {
            Log.i(TAG, "Sincronizando activo activo de WebSocket: $activeAsset -> $clean")
            activeAsset = clean
            val ws = webSocket
            if (ws != null && isConnected) {
                subscribeToAsset(ws, clean)
            }
        }
    }

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
    private val isConnectedFlag = AtomicBoolean(false)

    private val workerThread = HandlerThread("BinomoWSWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    var currentState: WebSocketState = WebSocketState.DISCONNECTED
        private set

    /** true si el socket con el broker está abierto y suscrito. */
    @get:JvmName("isConnected")
    val isConnected: Boolean get() = isConnectedFlag.get()

    /**
     * Antigüedad en milisegundos del último tick recibido.
     * Devuelve [Long.MAX_VALUE] si aún no ha llegado ningún tick (feed nunca visto).
     */
    val lastTickAgeMs: Long
        get() {
            val ts = latestTick?.timestampMs ?: return Long.MAX_VALUE
            return (System.currentTimeMillis() - ts).coerceAtLeast(0L)
        }

    /**
     * true si el feed WebSocket está vivo y con un tick de antigüedad < [freshnessThresholdMs].
     * Fuente única de frescura: no existe respaldo por captura de pantalla.
     */
    fun isFeedFresh(freshnessThresholdMs: Long = DEFAULT_FRESHNESS_MS): Boolean =
        isConnected && lastTickAgeMs < freshnessThresholdMs

    /**
     * Rango relativo observado en la ventana reciente de ticks:
     * (maxPrecio - minPrecio) / minPrecio. Es 0.0 con precio perfectamente plano.
     *
     * Sirve para distinguir un feed *vivo pero congelado* (el emisor emite ticks a
     * ritmo normal pero el precio no se mueve, p. ej. el índice sintético pegado
     * `Z-CRY/IDX`, que oscila ~5e-10 en relativo) de un feed sano. Sin esta señal,
     * `isFeedFresh()` da `true` y el motor decide CALL/PUT sobre una línea plana.
     */
    fun recentPriceRangeRatio(): Double {
        val prices = synchronized(recentTicks) { recentTicks.map { it.price } }
        return MarketTickFilters.priceRangeRatio(prices)
    }

    /**
     * true si el emisor entrega ticks a ritmo normal pero el precio está
     * esencialmente inmóvil (rango relativo < [frozenRangeRatio]).
     *
     * `frozenRangeRatio` por defecto 1e-6 (0,0001%): un activo real líquido supera
     * ese rango en pocos segundos, mientras que `Z-CRY/IDX` se queda en ~5e-10.
     */
    fun isPriceFrozen(frozenRangeRatio: Double = MarketTickFilters.FROZEN_RANGE_RATIO): Boolean {
        if (!isFeedFresh()) return false // feed muerto: es otro diagnóstico
        val prices = synchronized(recentTicks) { recentTicks.map { it.price } }
        // Para índices sintéticos como Z-CRY/IDX cuyo paso de tick natural es sub-micro (~1e-9),
        // solo clasificar como congelado si el precio está 100% plano (delta == 0.0 o ratio < 1e-11)
        val threshold = if (activeAsset.contains("IDX", ignoreCase = true)) 1e-11 else frozenRangeRatio
        return MarketTickFilters.isPriceFrozen(prices, threshold)
    }

    var onTickListener: ((MarketTick) -> Unit)? = null
    var onStateChangeListener: ((WebSocketState, String?) -> Unit)? = null

    // Tracking de micro-velocidad de ticks
    private var lastPrice: Double = 0.0
    private var lastPriceTimeMs: Long = 0L
    private var smoothedVelocity: Float = 0f
    private val recentTicks = ArrayDeque<MarketTick>(16)

    var latestTick: MarketTick? = null
        private set

    /**
     * Origen del último tick aceptado: "socket" si llegó por el WebSocket propio,
     * "headless" si lo inyectó el WebView puente vía [processIncomingMessage].
     * Sirve para diagnosticar sin ambigüedad qué vía alimenta el feed.
     */
    var lastTickSource: String = "none"
        private set

    /** Nº de mensajes crudos recibidos por el socket propio (incluye rechazos phx_reply). */
    var rawSocketMessages: Long = 0L
        private set

    /** Nº de ticks que el socket propio ha convertido en precio válido. */
    var socketTicks: Long = 0L
        private set

    /**
     * DIAGNÓSTICO TEMPORAL: últimas tramas crudas recibidas (truncadas), para
     * comparar dos frames consecutivos y ver qué campo cambia realmente.
     * Se expone en el bloque "feed" del bridge HTTP.
     */
    private val rawFrameRing = ArrayDeque<String>(8)
    val rawFrames: List<String> get() = synchronized(rawFrameRing) { rawFrameRing.toList() }

    private fun recordRawFrame(text: String) {
        synchronized(rawFrameRing) {
            rawFrameRing.addLast(text.take(600))
            while (rawFrameRing.size > 3) rawFrameRing.removeFirst()
        }
    }

    /** Contador de referencias `ref` de Phoenix Channels (1..N, nunca 0). */
    private var phoenixRef = 0

    private fun nextRef(): Int {
        phoenixRef++
        if (phoenixRef <= 0) phoenixRef = 1
        return phoenixRef
    }

    private var reconnectAttempts = 0
    private val MAX_RECONNECT_DELAY_MS = 15000L

    private val reconnectRunnable = Runnable {
        if (!isConnectedFlag.get() && isEnabled) {
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
            isConnectedFlag.set(false)
            isConnecting.set(false)
            updateState(WebSocketState.DISCONNECTED, "Desconectado")
        }
    }

    private fun startConnectionInternal() {
        if (isConnectedFlag.get() || isConnecting.get()) return

        isConnecting.set(true)
        updateState(WebSocketState.CONNECTING, "Conectando a $wsUrl")

        try {
            var targetUrl = wsUrl
            val token = authToken
            val devId = deviceId

            // Phoenix Channels exige los parámetros de handshake v2 en la query.
            // El WebView de Binomo los usa (wss://ws.binomo.com/?v=2&vsn=2.0.0):
            // sin ellos el servidor acepta el socket pero no emite ningún canal.
            if (!targetUrl.contains("vsn=")) {
                val sep = if (targetUrl.contains("?")) "&" else "?"
                targetUrl = "$targetUrl${sep}v=2&vsn=2.0.0"
            }

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
                    isConnectedFlag.set(true)
                    reconnectAttempts = 0
                    updateState(WebSocketState.CONNECTED, "Conexión activa con broker")
                    Log.d(TAG, "WebSocket conectado exitosamente: $wsUrl")

                    // Suscripción al activo configurado (ej. Z-CRY/IDX)
                    subscribeToAsset(ws, activeAsset)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    rawSocketMessages++
                    recordRawFrame(text)
                    // Traza de los primeros frames crudos: sin esto es imposible distinguir
                    // "el servidor no emite" de "emite en un formato que no parseamos".
                    if (rawSocketMessages <= 10) {
                        Log.d(TAG, "Frame WS crudo #$rawSocketMessages: ${text.take(300)}")
                    }
                    // Heartbeat de Phoenix v2: `[join_ref, ref, "phoenix", "heartbeat", {}]`.
                    // El cliente DEBE responder con phx_reply; si no, el servidor cierra el
                    // canal por timeout y deja de emitir cotizaciones.
                    if (text.contains("heartbeat")) {
                        val ref = Regex(""""ref"\s*:\s*"?(\d+)"""")
                            .find(text)?.groupValues?.getOrNull(1)
                            ?: Regex("""(?:\[|,)"(\d+)","?phoenix""").find(text)?.groupValues?.getOrNull(1)
                        if (ref != null) {
                            ws.send(
                                JSONArray().apply {
                                    put(JSONObject.NULL)
                                    put(ref)
                                    put("phoenix")
                                    put("phx_reply")
                                    put(JSONObject().put("status", "ok").put("response", JSONObject()))
                                }.toString()
                            )
                        }
                    }
                    processIncomingMessage(text, source = "socket")
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket cerrando: code=$code, reason=$reason")
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    isConnecting.set(false)
                    isConnectedFlag.set(false)
                    updateState(WebSocketState.DISCONNECTED, "Cerrado: $reason")
                    scheduleReconnect()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    isConnecting.set(false)
                    isConnectedFlag.set(false)
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
            isConnectedFlag.set(false)
            Log.e(TAG, "Excepción al iniciar conexión WebSocket", e)
            updateState(WebSocketState.ERROR, e.message)
            scheduleReconnect()
        }
    }

    /**
     * Suscripción al activo.
     *
     * Hechos verificados sobre el backend de Binomo (as.binomo.com y ws.binomo.com
     * resuelven a las MISMAS IPs Cloudflare, es el mismo servicio):
     *
     * - El socket de cotizaciones espera `{"action":"subscribe","rics":["RIC"]}`:
     *   clave **`rics` en plural**, array de STRINGS. La forma previa
     *   `{"action":"subscribe","data":[{"ric":...}]}` no la entiende: el servidor
     *   acepta la conexión y descarta el mensaje sin cerrarla. Ese es exactamente
     *   el síntoma "conecta pero nunca llega un tick".
     * - El socket de trading habla Phoenix Channels v2 (`?v=2&vsn=2.0.0`), cuyo
     *   serializador JSON v2 usa **arrays posicionales**, no objetos:
     *   `[join_ref, ref, topic, event, payload]`.
     *
     * Se envían ambas variantes: la primera es la que produce ticks, la segunda
     * cubre el caso de que el despliegue activo sirva el canal Phoenix.
     */
    private fun subscribeToAsset(ws: WebSocket, asset: String) {
        try {
            // 1. Socket de cotizaciones: clave `rics` plural con array de strings.
            ws.send(
                JSONObject().apply {
                    put("action", "subscribe")
                    put("rics", JSONArray().apply { put(asset) })
                }.toString()
            )
            // 2. Topic por activo (Phoenix v2, array posicional): sin él algunos
            //    despliegues no emiten nada aunque el subscribe anterior se acepte.
            ws.send(phoenixJoin("asset:$asset", JSONObject()))
            // 3. Topic global de cotizaciones con el ric en el payload.
            ws.send(phoenixJoin("rates", JSONObject().put("ric", asset)))
            Log.d(TAG, "Suscripción enviada para activo: $asset (rics + phoenix, ref=$phoenixRef)")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando suscripción", e)
        }
    }

    /**
     * Frame de Phoenix Channels con el serializador **v2 JSON**, que usa arrays
     * posicionales `[join_ref, ref, topic, event, payload]` en lugar de un objeto
     * con claves. Enviar `{"topic":...,"event":"phx_join"}` a un socket `vsn=2.0.0`
     * hace que el servidor lo ignore por formato inválido.
     */
    private fun phoenixJoin(topic: String, payload: JSONObject): String {
        val ref = nextRef().toString()
        return JSONArray().apply {
            put(ref)      // join_ref
            put(ref)      // ref
            put(topic)
            put("phx_join")
            put(payload)
        }.toString()
    }

    /**
     * Procesa y parsea las tramas de texto recibidas por WebSocket o bridge Headless.
     * Soporta múltiples formatos comunes de cotización (JSON de ticks, arrays, socket.io, etc.)
     */
    fun processIncomingMessage(rawText: String, source: String = "headless") {
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
                // Frame Phoenix v2 posicional: [join_ref, ref, topic, event, payload].
                // El precio viaja en payload["response"]["data"][*]["assets"][*].
                if (parsedPrice == null && arr.length() >= 5) {
                    val phxPayload = arr.optJSONObject(4)
                    val phxData = phxPayload?.optJSONArray("data")
                        ?: phxPayload?.optJSONObject("response")?.optJSONArray("data")
                    if (phxData != null) {
                        for (i in 0 until phxData.length()) {
                            val item = phxData.optJSONObject(i) ?: continue
                            val assets = item.optJSONArray("assets") ?: continue
                            for (j in 0 until assets.length()) {
                                val a = assets.optJSONObject(j) ?: continue
                                val r = if (a.has("rate")) a.optDouble("rate") else a.optDouble("price", Double.NaN)
                                val ric = a.optString("ric", "")
                                if (!r.isNaN() && r > 0.0 && (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true))) {
                                    parsedPrice = r
                                    if (ric.isNotEmpty()) assetName = ric
                                }
                            }
                        }
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
                lastTickSource = source
                if (source == "socket") socketTicks++
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
            val dtMs = (timestampMs - lastPriceTimeMs).coerceAtLeast(10L)
            val dtSec = dtMs / 1000f
            val dp = (price - lastPrice)
            velocity = (dp / dtSec).toFloat()

            // Si el tiempo transcurrido es grande (>3.5s), reiniciar la inercia previa
            if (dtMs > 3500L) {
                smoothedVelocity = velocity
            } else {
                // Filtro EMA de velocidad suavizada para evitar que colapse a 0 en ticks planos momentáneos
                val alpha = 0.45f
                smoothedVelocity = (alpha * velocity) + ((1f - alpha) * smoothedVelocity)
            }

            // Umbrales de micro-impulso: evalúa tanto el impulso instantáneo como la velocidad suavizada
            val effectiveVel = if (Math.abs(velocity) > 0.0001f) velocity else smoothedVelocity
            if (effectiveVel > 0.0003f) isBullish = true
            else if (effectiveVel < -0.0003f) isBearish = true
        } else {
            smoothedVelocity = 0f
        }

        lastPrice = price
        lastPriceTimeMs = timestampMs

        val tick = MarketTick(
            asset = asset,
            price = price,
            timestampMs = timestampMs,
            velocity = velocity,
            isBullishImpulse = isBullish,
            isBearishImpulse = isBearish,
            smoothedVelocity = smoothedVelocity
        )

        latestTick = tick

        synchronized(recentTicks) {
            if (recentTicks.size >= 16) recentTicks.removeFirst()
            recentTicks.addLast(tick)
        }

        workerHandler.post {
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

    companion object {
        /** Umbral único de frescura del feed WebSocket: 5000 ms. */
        const val DEFAULT_FRESHNESS_MS = 5000L
    }
}
