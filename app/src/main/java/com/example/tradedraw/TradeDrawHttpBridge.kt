package com.example.tradedraw

import android.graphics.Bitmap
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Micro-servidor HTTP local de ultra-baja latencia (10-25 ms).
 * Permite supervisión en vivo, extracción de frames en RAM y ejecución de órdenes
 * a través de ADB port forwarding (`adb forward tcp:8080 tcp:8080`) sin escribir en disco.
 */
class TradeDrawHttpBridge(
    private val overlayService: OverlayService,
    private val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private val threadPool = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false

    @Volatile var latestFrame: Bitmap? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d("TradeDrawHttp", "Micro-servidor HTTP iniciado en puerto $port")
                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        threadPool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e("TradeDrawHttp", "Error en accept()", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("TradeDrawHttp", "No se pudo iniciar el servidor en puerto $port", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        threadPool.shutdownNow()
        Log.d("TradeDrawHttp", "Micro-servidor HTTP detenido")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 3000
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = s.getOutputStream()

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")
                val queryString = fullPath.substringAfter("?", "")

                // Parse query parameters
                val params = mutableMapOf<String, String>()
                if (queryString.isNotEmpty()) {
                    for (pair in queryString.split("&")) {
                        val kv = pair.split("=", limit = 2)
                        if (kv.isNotEmpty()) {
                            val key = URLDecoder.decode(kv[0], "UTF-8")
                            val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                            params[key] = value
                        }
                    }
                }

                // Read headers & optional content-length for POST body
                var contentLength = 0
                var line: String? = input.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = input.readLine()
                }

                if (method == "POST" && contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = input.read(bodyChars, readTotal, contentLength - readTotal)
                        if (r < 0) break
                        readTotal += r
                    }
                    val bodyStr = String(bodyChars, 0, readTotal)
                    for (pair in bodyStr.split("&")) {
                        val kv = pair.split("=", limit = 2)
                        if (kv.isNotEmpty()) {
                            val key = URLDecoder.decode(kv[0], "UTF-8")
                            val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                            params[key] = value
                        }
                    }
                }

                when (path) {
                    "/ping" -> {
                        sendJsonResponse(output, 200, """{"status":"ok","app":"TradeDraw","port":$port}""")
                    }
                    "/status" -> {
                        val json = buildStatusJson()
                        sendJsonResponse(output, 200, json)
                    }
                    "/frame" -> {
                        sendFrameResponse(output)
                    }
                    "/trade" -> {
                        val actionStr = params["action"]?.uppercase() ?: "BUY"
                        val action = if (actionStr == "SELL" || actionStr == "PUT") TradeAction.SELL else TradeAction.BUY
                        val access = AutoTradeAccessibilityService.instance
                        if (access != null) {
                            val cal = overlayService.tradingEngine.calibrationManager
                            val (x, y) = if (action == TradeAction.BUY) {
                                cal?.getBuyCoordinates() ?: Pair(200f, 600f)
                            } else {
                                cal?.getSellCoordinates() ?: Pair(600f, 600f)
                            }
                            access.performClickAt(x, y)
                            sendJsonResponse(output, 200, """{"success":true,"action":"$action","targetX":$x,"targetY":$y}""")
                        } else {
                            sendJsonResponse(output, 503, """{"success":false,"error":"Servicio de Accesibilidad inactivo"}""")
                        }
                    }
                    "/strategy" -> {
                        val stratName = params["name"]?.uppercase() ?: ""
                        try {
                            val st = AutoTradeStrategy.valueOf(stratName)
                            overlayService.tradingEngine.strategy = st
                            overlayService.updateHUDView()
                            sendJsonResponse(output, 200, """{"success":true,"strategy":"${st.name}"}""")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Estrategia desconocida: $stratName"}""")
                        }
                    }
                    "/mode" -> {
                        val modeStr = params["name"]?.uppercase() ?: params["mode"]?.uppercase() ?: ""
                        try {
                            val m = AutoTradeMode.valueOf(modeStr)
                            overlayService.tradingEngine.mode = m
                            overlayService.updateHUDView()
                            sendJsonResponse(output, 200, """{"success":true,"mode":"${m.name}"}""")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Modo desconocido: $modeStr"}""")
                        }
                    }
                    "/reset_stats" -> {
                        overlayService.riskManager.resetStats()
                        overlayService.updateHUDView()
                        sendJsonResponse(output, 200, """{"success":true,"message":"Estadísticas reseteadas"}""")
                    }
                    "/resume" -> {
                        overlayService.riskManager.resetStreakOnly()
                        overlayService.updateHUDView()
                        sendJsonResponse(output, 200, """{"success":true,"message":"Operativa reanudada"}""")
                    }
                    "/sync_stats" -> {
                        val wins = params["wins"]?.toIntOrNull() ?: 0
                        val losses = params["losses"]?.toIntOrNull() ?: 0
                        overlayService.riskManager.setStats(wins, losses)
                        overlayService.updateHUDView()
                        sendJsonResponse(output, 200, """{"success":true,"wins":$wins,"losses":$losses}""")
                    }
                    "/stats" -> {
                        if (params.containsKey("wins") || params.containsKey("losses")) {
                            val wins = params["wins"]?.toIntOrNull() ?: overlayService.riskManager.totalWins
                            val losses = params["losses"]?.toIntOrNull() ?: overlayService.riskManager.totalLosses
                            overlayService.riskManager.correctStats(wins, losses)
                            val wr = overlayService.riskManager.getWinRate()
                            sendJsonResponse(output, 200, """{"success":true,"wins":$wins,"losses":$losses,"winRate":$wr}""")
                        } else {
                            val wins = overlayService.riskManager.totalWins
                            val losses = overlayService.riskManager.totalLosses
                            val wr = overlayService.riskManager.getWinRate()
                            sendJsonResponse(output, 200, """{"wins":$wins,"losses":$losses,"winRate":$wr}""")
                        }
                    }
                    "/win" -> {
                        overlayService.riskManager.recordTradeWin(force = true)
                        val wins = overlayService.riskManager.totalWins
                        val losses = overlayService.riskManager.totalLosses
                        val wr = overlayService.riskManager.getWinRate()
                        sendJsonResponse(output, 200, """{"success":true,"action":"win","wins":$wins,"losses":$losses,"winRate":$wr}""")
                    }
                    "/loss" -> {
                        overlayService.riskManager.recordTradeLoss(force = true)
                        val wins = overlayService.riskManager.totalWins
                        val losses = overlayService.riskManager.totalLosses
                        val wr = overlayService.riskManager.getWinRate()
                        sendJsonResponse(output, 200, """{"success":true,"action":"loss","wins":$wins,"losses":$losses,"winRate":$wr}""")
                    }
                    "/click" -> {
                        val x = params["x"]?.toFloatOrNull()
                        val y = params["y"]?.toFloatOrNull()
                        if (x == null || y == null) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Parámetros 'x' e 'y' requeridos"}""")
                        } else {
                            AutoTradeAccessibilityService.performClick(x, y) { success, err ->
                                Log.d("TradeDrawHttp", "Click en ($x, $y): success=$success err=$err")
                            }
                            sendJsonResponse(output, 200, """{"success":true,"x":$x,"y":$y}""")
                        }
                    }
                    "/swipe" -> {
                        val x1 = params["x1"]?.toFloatOrNull()
                        val y1 = params["y1"]?.toFloatOrNull()
                        val x2 = params["x2"]?.toFloatOrNull()
                        val y2 = params["y2"]?.toFloatOrNull()
                        val dur = params["duration"]?.toLongOrNull() ?: 300L
                        if (x1 == null || y1 == null || x2 == null || y2 == null) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Parámetros 'x1', 'y1', 'x2', 'y2' requeridos"}""")
                        } else {
                            val access = AutoTradeAccessibilityService.instance
                            if (access != null) {
                                access.performSwipe(x1, y1, x2, y2, dur)
                                sendJsonResponse(output, 200, """{"success":true,"x1":$x1,"y1":$y1,"x2":$x2,"y2":$y2,"duration":$dur}""")
                            } else {
                                sendJsonResponse(output, 503, """{"success":false,"error":"Servicio de Accesibilidad inactivo"}""")
                            }
                        }
                    }
                    "/submode" -> {
                        val subStr = params["name"]?.uppercase() ?: params["submode"]?.uppercase() ?: ""
                        try {
                            val sm = AutonomousSubMode.valueOf(subStr)
                            overlayService.tradingEngine.autonomousSubMode = sm
                            overlayService.updateHUDView()
                            sendJsonResponse(output, 200, """{"success":true,"submode":"${sm.name}"}""")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Submodo desconocido: $subStr"}""")
                        }
                    }
                    "/calibrate" -> {
                        val cal = overlayService.tradingEngine.calibrationManager
                        if (cal == null) {
                            sendJsonResponse(output, 503, """{"success":false,"error":"CalibrationManager no inicializado"}""")
                        } else {
                            val buyX = params["buyX"]?.toFloatOrNull()
                            val buyY = params["buyY"]?.toFloatOrNull()
                            val sellX = params["sellX"]?.toFloatOrNull()
                            val sellY = params["sellY"]?.toFloatOrNull()
                            if (buyX != null && buyY != null) cal.saveBuyCoordinates(buyX, buyY)
                            if (sellX != null && sellY != null) cal.saveSellCoordinates(sellX, sellY)
                            val (curBuyX, curBuyY) = cal.getBuyCoordinates()
                            val (curSellX, curSellY) = cal.getSellCoordinates()
                            sendJsonResponse(output, 200, """{"success":true,"buy":{"x":$curBuyX,"y":$curBuyY},"sell":{"x":$curSellX,"y":$curSellY}}""")
                        }
                    }
                    "/hud" -> {
                        val visibleParam = params["visible"]?.toBooleanStrictOrNull()
                        val collapseParam = params["collapse"]?.toBooleanStrictOrNull()
                        val recenterParam = params["recenter"]?.toBooleanStrictOrNull()
                        val xParam = params["x"]?.toIntOrNull()
                        val yParam = params["y"]?.toIntOrNull()

                        if (recenterParam == true || xParam != null || yParam != null) {
                            overlayService.recenterHUD(xParam, yParam)
                        }
                        if (visibleParam != null) {
                            overlayService.setHUDVisibility(visibleParam)
                        }
                        if (collapseParam != null) {
                            overlayService.setHUDCollapsed(collapseParam)
                        }
                        sendJsonResponse(output, 200, """{"success":true,"hudVisible":${overlayService.isHudVisible},"hudCollapsed":${overlayService.isHudCollapsed}}""")
                    }
                    "/overlay" -> {
                        val visibleParam = params["visible"]?.toBooleanStrictOrNull()
                        val expandParam = params["expand"]?.toBooleanStrictOrNull()
                        if (visibleParam != null) {
                            overlayService.setOverlayVisible(visibleParam)
                        }
                        if (expandParam != null) {
                            overlayService.setMenuExpanded(expandParam)
                        }
                        sendJsonResponse(output, 200, """{"success":true,"menuExpanded":${overlayService.isMenuExpanded}}""")
                    }
                    "/nodes" -> {
                        val access = AutoTradeAccessibilityService.instance
                        if (access != null) {
                            val bal = access.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
                            val asset = AutoTradeAccessibilityService.latestObservedAsset
                            val isDemo = AutoTradeAccessibilityService.isDemoAccount
                            val isSyn = AutoTradeAccessibilityService.isSyntheticOrOTC
                            val amount = AutoTradeAccessibilityService.observedOrderAmount
                            sendJsonResponse(output, 200, """{
                                "success": true,
                                "balance": $bal,
                                "activeAsset": "${escapeJson(asset)}",
                                "isDemoAccount": $isDemo,
                                "isSyntheticOrOTC": $isSyn,
                                "observedOrderAmount": $amount
                            }""".trimIndent())
                        } else {
                            sendJsonResponse(output, 503, """{"success":false,"error":"Servicio de Accesibilidad inactivo"}""")
                        }
                    }
                    else -> {
                        sendJsonResponse(output, 404, """{"error":"Ruta no encontrada: $path"}""")
                    }
                }
            }
        } catch (e: Exception) {
            // Error silencioso por desconexión de cliente
        }
    }

    private fun buildStatusJson(): String {
        val te = overlayService.tradingEngine
        val rm = overlayService.riskManager
        val analysis = te.latestAnalysisResult
        val bal = AutoTradeAccessibilityService.instance?.readCurrentBalance() ?: 0.0

        // Fuente viva: el motor sintético de ticks WebSocket. `latestAnalysisResult` viene de la
        // ruta de visión (retirada por batería) y trae ceros en precio/velas, así que no sirve
        // como indicador de salud del feed.
        val synthetic = te.syntheticCandleEngine
        val currentPriceY = te.latestMarketTick?.price?.toFloat() ?: 0f
        // `dynamicResistanceY`/`dynamicSupportY` son coordenadas en PÍXELES que solo produce la
        // ruta de visión (retirada). En modo WebSocket puro no hay imagen, así que se quedan en 0
        // y no deben rellenarse con precios: se exponen los niveles reales del motor sintético en
        // campos propios (`dynamicSupportPrice`/`dynamicResistancePrice`), en unidades de precio.
        val resY = analysis?.dynamicResistanceY ?: 0f
        val supY = analysis?.dynamicSupportY ?: 0f
        val supPrice = synthetic.dynamicSupportPrice
        val resPrice = synthetic.dynamicResistancePrice
        val callPct = analysis?.signalPowerCall ?: 50
        val putPct = analysis?.signalPowerPut ?: 50
        val isSideways = synthetic.isChoppinessDetected()
        val candleCount = synthetic.closedCandles.size
        val streak = analysis?.streakBadge ?: ""
        val trend = synthetic.detectedTrend.name
        val strat = te.strategy.name
        val mode = te.mode.name
        val accessConnected = AutoTradeAccessibilityService.instance != null

        val activeSig = te.currentActiveSignal
        val signalJson = if (activeSig != null) {
            """{"action":"${activeSig.action.name}","title":"${escapeJson(activeSig.title)}","reason":"${escapeJson(activeSig.reason)}","timestamp":${activeSig.timestamp}}"""
        } else {
            "null"
        }

        // Fuente única de datos: feed WebSocket del broker (la captura de pantalla fue retirada por consumo de batería).
        val ws = overlayService.binomoWebSocketClient
        val wsAge = ws?.lastTickAgeMs ?: Long.MAX_VALUE
        val wsConnected = ws?.isConnected ?: false
        val wsAsset = ws?.activeAsset ?: ""
        val wsFresh = ws?.isFeedFresh() ?: false
        val wsSource = ws?.lastTickSource ?: "none"
        val wsRawMsgs = ws?.rawSocketMessages ?: 0L
        val wsSocketTicks = ws?.socketTicks ?: 0L
        // Antigüedad infinita no es serializable en JSON: se emite como -1 (sin tick recibido nunca).
        val wsAgeJson = if (wsAge == Long.MAX_VALUE) -1L else wsAge

        return """{
            "app": "TradeDraw",
            "mode": "$mode",
            "strategy": "$strat",
            "accessibilityActive": $accessConnected,
            "balance": $bal,
            "analysis": {
                "currentPriceY": $currentPriceY,
                "dynamicResistanceY": $resY,
                "dynamicSupportY": $supY,
                "dynamicResistancePrice": $resPrice,
                "dynamicSupportPrice": $supPrice,
                "callPower": $callPct,
                "putPower": $putPct,
                "isMarketSideways": $isSideways,
                "trend": "$trend",
                "candleCount": $candleCount,
                "streak": "${escapeJson(streak)}"
            },
            "risk": {
                "totalWins": ${rm.totalWins},
                "totalLosses": ${rm.totalLosses},
                "winRate": ${rm.getWinRate()},
                "currentLossStreak": ${rm.currentLossStreak},
                "martingaleStatus": "${escapeJson(rm.getMartingaleStatusBadge())}",
                "investmentAmount": ${rm.getCurrentInvestmentAmount()},
                "hasPendingTrade": ${rm.hasPendingTrade},
                "remainingCooldown": ${rm.getRemainingCooldown()}
            },
            "activeSignal": $signalJson,
            "feed": {
                "source": "websocket",
                "lastTickAgeMs": $wsAgeJson,
                "activeAsset": "${escapeJson(wsAsset)}",
                "ws_active_asset": "${escapeJson(wsAsset)}",
                "connected": $wsConnected,
                "isFresh": $wsFresh,
                "tickSource": "$wsSource",
                "rawSocketMessages": $wsRawMsgs,
                "socketTicks": $wsSocketTicks,
                "journalWrites": ${TradeJournalLogger.writeCount},
                "lastJournalWriteMs": ${TradeJournalLogger.lastWriteAgeMs},
                "journalError": "${escapeJson(TradeJournalLogger.lastError)}",
                "journalDuplicatesRejected": ${TradeJournalLogger.duplicateRejections},
                "journalRotations": ${TradeJournalLogger.rotationCount},
                "priceFrozen": ${ws?.isPriceFrozen() ?: false},
                "recentPriceRangeRatio": ${ws?.recentPriceRangeRatio() ?: -1.0},
                "rawFrames": ${rawFramesJson(ws)}
            }
        }""".trimIndent()
    }

    private fun sendFrameResponse(output: OutputStream) {
        val bmp = latestFrame
        if (bmp == null || bmp.isRecycled) {
            sendJsonResponse(output, 404, """{"error":"No hay frame en memoria"}""")
            return
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val bytes = baos.toByteArray()

        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    // DIAGNÓSTICO TEMPORAL: últimas tramas crudas del WebSocket, para identificar
    // qué campo del frame cambia entre ticks (precio vivo vs apertura de vela).
    private fun rawFramesJson(ws: com.example.tradedraw.BinomoWebSocketClient?): String {
        val frames = ws?.rawFrames ?: emptyList()
        if (frames.isEmpty()) return "[]"
        return frames.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
