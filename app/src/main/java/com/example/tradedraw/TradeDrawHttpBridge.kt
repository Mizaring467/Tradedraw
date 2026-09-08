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

        val currentPriceY = analysis?.currentPriceY ?: 0f
        val resY = analysis?.dynamicResistanceY ?: 0f
        val supY = analysis?.dynamicSupportY ?: 0f
        val callPct = analysis?.signalPowerCall ?: 50
        val putPct = analysis?.signalPowerPut ?: 50
        val isSideways = analysis?.isMarketSideways ?: false
        val candleCount = analysis?.candleList?.size ?: 0
        val streak = analysis?.streakBadge ?: ""
        val trend = analysis?.trend?.name ?: "SIDEWAYS"
        val strat = te.strategy.name
        val mode = te.mode.name
        val accessConnected = AutoTradeAccessibilityService.instance != null

        val activeSig = te.currentActiveSignal
        val signalJson = if (activeSig != null) {
            """{"action":"${activeSig.action.name}","title":"${escapeJson(activeSig.title)}","reason":"${escapeJson(activeSig.reason)}","timestamp":${activeSig.timestamp}}"""
        } else {
            "null"
        }

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
            "activeSignal": $signalJson
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

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
