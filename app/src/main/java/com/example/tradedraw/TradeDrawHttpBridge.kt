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
 *
 * Ronda 13 (B1): enlazado ESTRICTAMENTE a loopback. Antes `ServerSocket(port)` escuchaba en
 * `[::]` (todas las interfaces), asi que con el telefono en Wi-Fi cualquiera en la misma red
 * podia leer `/status` y, peor, disparar `/trade`, que ejecuta taps reales en el broker.
 * El acceso sigue siendo posible porque `adb forward tcp:8080 tcp:8080` conecta desde dentro
 * del dispositivo; la diferencia es que ya no se puede llegar por la LAN.
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
                // R13/B1: solo loopback. Ver el comentario de clase.
                val addr = java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port)
                serverSocket = ServerSocket()
                serverSocket?.bind(addr)
                Log.d("TradeDrawHttp", "Micro-servidor HTTP iniciado en loopback:$port")
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
                            overlayService.tradingEngine.setTradingMode(m, "bridge:/mode")
                            overlayService.updateHUDView()
                            sendJsonResponse(output, 200, """{"success":true,"mode":"${m.name}"}""")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Modo desconocido: $modeStr"}""")
                        }
                    }
                    "/events" -> {
                        // R13/A5+adenda: fuente de observabilidad alternativa a logcat, que en
                        // este dispositivo no funciona (`init.svc.logd = stopped`, sin root para
                        // levantarlo). Expone vetos con motivo, transiciones de orderState, taps
                        // despachados y cambios de activo.
                        //
                        // Ronda 18: formato INTACTO a proposito. Los cambios de modo estan
                        // dominados por ASSET en este ring, asi que se exponen en /mode_log.
                        val max = params["max"]?.toIntOrNull() ?: 60
                        sendJsonResponse(output, 200, TradeEventLog.toJson(max))
                    }
                    "/mode_log" -> {
                        // Ronda 18: ring DEDICADO a MODE_CHANGE, independiente del buffer de 200.
                        // Existe porque en R15-A el volcado de /events devolvio 0 MODE_CHANGE: los
                        // eventos de activo los habian desplazado. Aqui no compiten.
                        val max = params["max"]?.toIntOrNull() ?: TradeEventLog.MODE_CAPACIDAD
                        sendJsonResponse(output, 200, TradeEventLog.modesToJson(max))
                    }
                    "/mode_journal" -> {
                        // Ronda 18: las ultimas transiciones LEIDAS DEL DISCO. A diferencia de
                        // /mode_log, sobrevive al reinicio del servicio, que es justo el escenario
                        // del rearme de R14.
                        val max = params["max"]?.toIntOrNull() ?: 50
                        val diario = overlayService.modeJournal
                        val lineas = diario?.tail(max) ?: emptyList()
                        val cuerpo = lineas.joinToString(",")
                        sendJsonResponse(
                            output, 200,
                            """{"success":true,"total":${lineas.size},"events":[$cuerpo]}"""
                        )
                    }
                    "/shadow_journal" -> {
                        // R23 Iter 2: decisiones persistidas en disco por SHADOW MODE. Antes solo
                        // eran legibles via `adb pull`; el validador necesita leerlas por HTTP sin
                        // tocar ADB. Solo LECTURA: no escribe, no altera el journal ni el estado.
                        val max = params["max"]?.toIntOrNull() ?: 50
                        val (lineas, totalEnArchivo) = readShadowJournalTail(max)
                        sendJsonResponse(output, 200, buildShadowJournalJson(lineas, totalEnArchivo))
                    }
                    "/ticks" -> {
                        // R23 Iter 4: los ticks EXACTOS que entraron al motor durante la ventana viva.
                        // Cierra el hallazgo de la Iter 3 (paridad Live<->Replay "NO EVALUABLE" por
                        // falta de la fuente de ticks). Solo LECTURA: no altera estado ni guards.
                        val max = params["max"]?.toIntOrNull() ?: 500
                        val formato = params["format"]?.lowercase() ?: "json"
                        val buffer = overlayService.tradingEngine.tickBuffer
                        val ticks = buffer.tail(max)
                        if (formato == "csv") {
                            // `text/plain` y no `text/csv` a proposito: el consumidor lo baja con
                            // curl y lo vuelca a un archivo que alimenta `loadTicks` del replay.
                            sendCsvResponse(output, 200, buildTicksCsv(ticks))
                        } else {
                            sendJsonResponse(output, 200, buildTicksJson(ticks, buffer.size))
                        }
                    }
                    "/diag_multiric" -> {
                        // Ronda 15 / Frente C: experimento B0 (suscripcion multi-RIC) medido en el
                        // dispositivo. Existe SOLO para medir y tiene dos cierres de seguridad:
                        //
                        //  1. Rechaza la operacion si el motor NO esta en DISABLED. Suscribir un
                        //     RIC ajeno mientras el bot pudiera operar es exactamente la condicion
                        //     que esta ronda debe evitar.
                        //  2. `accion=restaurar` devuelve el socket a un solo RIC. Es obligatorio
                        //     llamarlo al terminar: dejar dos RICs suscritos cambiaria el estado de
                        //     partida de la ronda.
                        //
                        // NO cambia el activo activo, NO cambia el modo y NO deja que los precios
                        // del RIC ajeno entren en el motor: solo se cuentan.
                        val ws = overlayService.binomoWebSocketClient
                        val modoActual = overlayService.tradingEngine.mode
                        val accion = params["accion"]?.lowercase() ?: "suscribir"
                        when {
                            ws == null ->
                                sendJsonResponse(output, 503, """{"success":false,"error":"sin cliente WebSocket"}""")
                            modoActual != AutoTradeMode.DISABLED ->
                                sendJsonResponse(
                                    output, 409,
                                    """{"success":false,"error":"solo en DISABLED","mode":"${modoActual.name}"}"""
                                )
                            accion == "restaurar" -> {
                                ws.restaurarSuscripcionUnica()
                                sendJsonResponse(output, 200, """{"success":true,"accion":"restaurar"}""")
                            }
                            else -> {
                                val ric = params["ric"] ?: ""
                                if (ric.isBlank()) {
                                    sendJsonResponse(output, 400, """{"success":false,"error":"falta ric"}""")
                                } else {
                                    val ok = ws.suscribirRicSecundario(ric)
                                    sendJsonResponse(
                                        output, if (ok) 200 else 409,
                                        """{"success":$ok,"ric":"${escapeJson(ric)}","principal":"${escapeJson(ws.activeAsset)}"}"""
                                    )
                                }
                            }
                        }
                    }
                    "/diag_inoperable" -> {
                        // Ronda 15 / B2: verificacion del pintado del banner `Mercado inoperable`.
                        //
                        // POR QUE HACE FALTA INYECTAR. El detector compara el rango actual contra la
                        // MEDIANA de los ultimos 60 min del propio activo, excluyendo solo los
                        // ultimos 30 s. Si el activo lleva mas de 30 s plano, sus propias muestras
                        // planas YA entran en la mediana `M`, `0.25*M` se va a cero y la condicion
                        // `R < 0.25*M` deja de cumplirse: el detector no se enciende por mucho que
                        // el mercado este muerto. Medido en el dispositivo: 14 min de rango 2.5e-10
                        // en Z-CRY/IDX con `inoperableNow=false` y `reference` cayendo.
                        //
                        // QUE TOCA Y QUE NO. Alimenta UNICAMENTE `inoperabilityDetector`, que es
                        // AVISO y no decision (no cambia de activo, no entra en la decision de
                        // entrada, no toca el modo). NO se inyecta ni un tick en el motor, en el
                        // motor sintetico ni en el feed del broker. Se declara como INYECCION en el
                        // informe: no se finge que fue un mercado real.
                        //
                        // `accion=limpiar` es obligatorio al terminar y deja el detector en blanco.
                        val det = overlayService.tradingEngine.inoperabilityDetector
                        when (params["accion"]?.lowercase() ?: "encender") {
                            "limpiar" -> {
                                det.onAssetChanged()
                                sendJsonResponse(output, 200, """{"success":true,"accion":"limpiar"}""")
                            }
                            else -> {
                                // Serie sintetica en DOS fases, con el espaciado dictado por el
                                // propio detector (medido, no supuesto):
                                //
                                //  - `medianReference` exige >= 16 muestras de `R` con antiguedad
                                //    mayor que `holdSeconds`. Las muestras de `R` solo empiezan a
                                //    registrarse cuando la ventana de 16 precios esta LLENA, asi que
                                //    hacen falta mas de 32 ticks solo para tener referencia.
                                //  - Un hueco entre ticks > `FEED_ALIVE_MAX_MS` (5 s) resetea toda la
                                //    serie: el espaciado se mantiene por debajo de ese tope.
                                //
                                // Fase 1 (60 ticks, 1 s de separacion): rango amplio -> construye M.
                                // Fase 2 (40 ticks, 2 s): precio clavado -> R=0, muy por debajo de
                                // 0.25*M, y 78 s de baja sostenida > holdSeconds (30 s).
                                val ahora = System.currentTimeMillis()
                                val inicioRef = ahora - 31_000L - 59 * 1_000L
                                for (i in 0 until 60) {
                                    det.onTick(1000.0 + i * 2.0, inicioRef + i * 1_000L, true)
                                }
                                val refConstruida = det.lastReference
                                var activado = false
                                for (i in 0 until 40) {
                                    if (det.onTick(1000.0, ahora - 30_000L + i * 2_000L, true)) {
                                        activado = true
                                    }
                                }
                                TradeEventLog.add(
                                    TradeEventLog.Kind.INOPERABLE,
                                    "INOPERABLE activo=INYECTADO(prueba B2) segundos=${det.holdSeconds} " +
                                        "ratio=%.3e".format(java.util.Locale.US, det.lastRatio)
                                )
                                sendJsonResponse(
                                    output, 200,
                                    """{"success":true,"activado":$activado,"inoperableNow":${det.isInoperable},"referenceConstruida":$refConstruida,"ratio":${det.lastRatio},"reference":${det.lastReference}}"""
                                )
                            }
                        }
                    }
                    "/reset_stats" -> {
                        overlayService.riskManager.resetStats()
                        // Ronda 3: limpiar también el estado adaptativo persistido (firmas de
                        // anti-patrón y rachas). Sin esto, sesiones previas dejan bloqueado el
                        // motor por "continuación con pérdidas previas" aunque las stats se reseteen.
                        overlayService.tradingEngine.adaptiveLearningEngine.reset(overlayService)
                        overlayService.updateHUDView()
                        sendJsonResponse(output, 200, """{"success":true,"message":"Estadísticas y aprendizaje reseteados"}""")
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
                        if (params.containsKey("wins") || params.containsKey("losses") || params.containsKey("streak")) {
                            val wins = params["wins"]?.toIntOrNull() ?: overlayService.riskManager.totalWins
                            val losses = params["losses"]?.toIntOrNull() ?: overlayService.riskManager.totalLosses
                            val streak = params["streak"]?.toIntOrNull()
                            overlayService.riskManager.correctStats(wins, losses, streak)
                            val wr = overlayService.riskManager.getWinRate()
                            val curStreak = overlayService.riskManager.currentLossStreak
                            sendJsonResponse(output, 200, """{"success":true,"wins":$wins,"losses":$losses,"lossStreak":$curStreak,"winRate":$wr}""")
                        } else {
                            val wins = overlayService.riskManager.totalWins
                            val losses = overlayService.riskManager.totalLosses
                            val curStreak = overlayService.riskManager.currentLossStreak
                            val wr = overlayService.riskManager.getWinRate()
                            sendJsonResponse(output, 200, """{"wins":$wins,"losses":$losses,"lossStreak":$curStreak,"winRate":$wr}""")
                        }
                    }
                    "/win" -> {
                        // R24/B1.c: origen MANUAL_BRIDGE. V1 resta estos incrementos del lado
                        // `/status`; sin etiqueta el incremento seria NO EVALUABLE.
                        // R24/B1.a (fix): `settleManual` marca la clave de liquidacion del motor,
                        // asi que un WIN manual seguido del fallback del motor cuenta UNA vez.
                        overlayService.riskManager.settleManual("MANUAL_BRIDGE", win = true)
                        val wins = overlayService.riskManager.totalWins
                        val losses = overlayService.riskManager.totalLosses
                        val wr = overlayService.riskManager.getWinRate()
                        sendJsonResponse(output, 200, """{"success":true,"action":"win","wins":$wins,"losses":$losses,"winRate":$wr}""")
                    }
                    "/loss" -> {
                        overlayService.riskManager.settleManual("MANUAL_BRIDGE", win = false)
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
                            // R23D Fase 1 (REQ 3): el setter del motor ya espeja el submodo al
                            // RiskManager, pero se reafirma aqui de forma explicita para que la ruta
                            // HTTP no dependa de que ese efecto se mantenga en el futuro.
                            overlayService.riskManager.currentSubMode = sm
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
                    "/asset" -> {
                        // Ronda 7: cambia el activo del WebSocket en caliente (SOLUSD-OTC, XAU/USD-HSFX2...).
                        // El setter usa SharedPreferences de la app, así que no choca con SELinux.
                        val name = params["name"]?.trim() ?: ""
                        if (name.isBlank()) {
                            sendJsonResponse(output, 400, """{"success":false,"error":"Parámetro 'name' requerido"}""")
                        } else {
                            // Ronda 20: `/asset` es una orden EXPLICITA, asi que puede forzar un RIC
                            // marcado sin feed (la via automatica de pantalla no puede). Sin esto la
                            // proteccion contra el RIC muerto bloquearia su propio diagnostico.
                            overlayService.binomoWebSocketClient?.forzarActivo(name)
                            sendJsonResponse(output, 200, """{"success":true,"asset":"$name"}""")
                        }
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

    /**
     * R23A Iter 3: este string NO admite comentarios `//` dentro del literal multilínea. Los `//` se
     * emiten literalmente sobre el HTTP y rompen el JSON (RFC 8259 prohibe comentarios), lo que hacía
     * fallar a `json.loads`/`jq`/`ConvertFrom-Json`. La explicación va aquí, fuera del literal.
     *
     * `observedStake` es lo que dice la pantalla de Binomo; `effectiveStake` es lo que el broker
     * realmente debita y con lo que se registra el journal. Con stake 1.0 y pantalla 100.000 COP,
     * `investmentAmount` coincidia con `effectiveStake` y ambos mentían: por eso se exponen los dos
     * por separado. Deliberadamente NO se añade campo de comentario en el JSON: un campo `_comment`
     * sería válido pero ensuciaría el contrato que consumen los validadores.
     */
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
        // R24/B2: ratios S/R REALES del instante. Son los mismos que `AdaptiveLearningEngine` usa
        // como `distSup`/`distRes` al construir la firma de contexto, asi que el monitor los
        // escribe en `decisions.csv` sin recalcular: la columna refleja lo que el motor vio.
        //
        // Sin velas cerradas el motor deja el campo en su valor por defecto (0.5), que NO es una
        // medicion. Se emite -1 como centinela de "no medido" y el monitor lo escribe como celda
        // vacia: inventar 0.5 seria indistinguible de un soporte realmente equidistante.
        val distSupRatio = synthetic.distanceToSupportRatio
        val distResRatio = synthetic.distanceToResistanceRatio
        val hayVelasParaRatios = candleCount > 0
        val distSupRatioJson = if (hayVelasParaRatios) distSupRatio.toString() else "-1"
        val distResRatioJson = if (hayVelasParaRatios) distResRatio.toString() else "-1"
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

        val healthReport = overlayService.healthSupervisor.evaluate(
            tradingEngine = te,
            riskManager = rm,
            wsClient = ws,
            accessibilityService = AutoTradeAccessibilityService.instance
        )

        return """{
            "app": "TradeDraw",
            "versionCode": ${BuildConfig.VERSION_CODE},
            "versionName": "${BuildConfig.VERSION_NAME}",
            "gitSha": "${BuildConfig.GIT_SHA}",
            "buildId": "${BuildConfig.BUILD_ID}",
            "buildTimestamp": ${BuildConfig.BUILD_TIMESTAMP},
            "isDemoAccount": ${AutoTradeAccessibilityService.isDemoAccount},
            "health": ${healthReport.toJson()},
            "mode": "$mode",
            "submode": "${te.autonomousSubMode.name}",
            "lastModeChangeSource": "${escapeJson(te.lastModeChange)}",
            "lastModeChangeTs": ${te.lastModeChangeTs},
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
                "distanceToSupportRatio": $distSupRatioJson,
                "distanceToResistanceRatio": $distResRatioJson,
                "streak": "${escapeJson(streak)}"
            },
            "risk": {
                "totalWins": ${rm.totalWins},
                "totalLosses": ${rm.totalLosses},
                "winRate": ${rm.getWinRate()},
                "currentLossStreak": ${rm.currentLossStreak},
                "martingaleStatus": "${escapeJson(rm.getMartingaleStatusBadge())}",
                "investmentAmount": ${rm.getCurrentInvestmentAmount()},
                "observedStake": ${AutoTradeAccessibilityService.observedOrderAmount},
                "effectiveStake": ${rm.getEffectiveStake()},
                "hasPendingTrade": ${rm.hasPendingTrade},
                "orderState": "${rm.orderState.name}",
                "hasConfirmedTrade": ${rm.hasConfirmedTrade},
                "remainingCooldown": ${rm.getRemainingCooldown(te.autonomousSubMode)},
                "vetoV1": ${TradeEventLog.countOf(TradeEventLog.Kind.VETO, "V1")},
                "vetoV2": ${TradeEventLog.countOf(TradeEventLog.Kind.VETO, "V2")},
                "vetoTotal": ${TradeEventLog.countOf(TradeEventLog.Kind.VETO)},
                "ultimoVeto": "${escapeJson(TradeEventLog.lastOf(TradeEventLog.Kind.VETO)?.detail ?: "")}",
                "tapsDespachados": ${TradeEventLog.countOf(TradeEventLog.Kind.TAP)},
                "inoperableCount": ${TradeEventLog.countOf(TradeEventLog.Kind.INOPERABLE)},
                "inoperableNow": ${te.inoperabilityDetector.isInoperable},
                "inoperableSeconds": ${te.inoperabilityDetector.inoperableSeconds(System.currentTimeMillis())},
                "inoperableRatio": ${te.inoperabilityDetector.lastRatio},
                "inoperableReference": ${te.inoperabilityDetector.lastReference},
                "ultimoInoperable": "${escapeJson(TradeEventLog.lastOf(TradeEventLog.Kind.INOPERABLE)?.detail ?: "")}",
                "vetoActivoDesajustado": ${te.vetoActivoDesajustado},
                "recuperacionesPorSinTicks": ${te.recuperacionesPorSinTicks()},
                "shadowOrders": ${te.shadowOrdersGenerated},
                "tradesLossSinEvidencia": ${rm.tradesLossSinEvidencia},
                "manualWins": ${rm.manualWins},
                "manualLosses": ${rm.manualLosses},
                "manualIncrements": ${TradeEventLog.countOf(TradeEventLog.Kind.MANUAL_INCREMENT)},
                "manualIncrementsDetalle": "${escapeJson(TradeEventLog.lastOf(TradeEventLog.Kind.MANUAL_INCREMENT)?.detail ?: "")}"
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
                "rangoRelativoMinimo": ${TradingEngine.rangoRelativoMinimoParaActivo(ws?.activeAsset ?: "")},
                "rangoRelativoActual": ${ws?.recentPriceRangeRatio() ?: -1.0},
                "volatilidadSuficiente": ${if (ws != null) ws.recentPriceRangeRatio() >= TradingEngine.rangoRelativoMinimoParaActivo(ws.activeAsset) else false},
                "payoutFromPercentLabel": ${AutoTradeAccessibilityService.observedPayout.fromPercentLabel ?: "null"},
                "payoutFromButtonRatio": ${AutoTradeAccessibilityService.observedPayout.fromButtonRatio ?: "null"},
                "payoutResolved": ${AutoTradeAccessibilityService.observedPayout.resolved ?: "null"},
                "payoutDiscrepant": ${AutoTradeAccessibilityService.observedPayout.discrepant},
                "payoutConfigured": ${overlayService.riskManager.sorosPayoutRate.toDouble()},
                "rawFrames": ${rawFramesJson(ws)},
                "acceptedTicks": ${ws?.acceptedTicks ?: 0L},
                "rejectedForeignAssetTicks": ${ws?.rejectedForeignAssetTicks ?: 0L},
                "ultimoRicConTicks": "${escapeJson(ws?.ultimoRicConTicks ?: "")}",
                "activoSinFeed": "${escapeJson(ws?.activoSinFeed ?: "")}",
                "segundosSinTicks": ${ws?.segundosSinTicks ?: 0L},
                "diagMultiRic": {
                    "ricSecundario": "${escapeJson(ws?.ricSecundario ?: "")}",
                    "ticksRicSecundario": ${ws?.ticksRicSecundario ?: 0L},
                    "ultimoTickRicSecundarioMs": ${ws?.ultimoTickRicSecundarioMs ?: 0L},
                    "desconexiones": ${ws?.desconexiones ?: 0L},
                    "reconexiones": ${ws?.reconexiones ?: 0L}
                }
            },
            "engine": {
                "signalsGenerated": ${synthetic.signalsGenerated},
                "lastSignalTimestampMs": ${synthetic.lastSignalTimestampMs},
                "lastSignalAction": "${escapeJson(synthetic.lastSignalAction)}",
                "evaluatedCount": ${synthetic.evaluatedCount},
                "suppressedByConfluence": ${synthetic.suppressedByConfluence},
                "suppressedByChop": ${synthetic.suppressedByChop},
                "suppressedByMicroCompression": ${synthetic.suppressedByMicroCompression}
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

    /** R23 Iter 4: respuesta `text/plain` para `/ticks?format=csv`. Mismo contrato que sendJsonResponse. */
    private fun sendCsvResponse(output: OutputStream, statusCode: Int, csv: String) {
        val bytes = csv.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
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

    /**
     * R23 Iter 2: lee las ultimas [max] lineas de `shadow_journal.csv` tal como estan en disco.
     *
     * Solo LECTURA. Replica el orden de resolucion de directorios de [TradeJournalLogger]
     * (externo `TradeDraw_Audits` si es escribible, interno `filesDir/TradeDraw_Audits` como
     * respaldo) porque el logger lo mantiene privado. Se revisan AMBAS rutas y se prefiere la que
     * realmente contenga el archivo, para no devolver `total:0` cuando el journal vive en un
     * directorio distinto del que el logger consideraria escribible ahora mismo.
     *
     * Se devuelven las lineas crudas (cabecera incluida) para que la auditoria vea exactamente
     * lo persistido. [max] se acota a [1, 2000] para no materializar respuestas sin limite.
     *
     * Devuelve `(tail, totalEnArchivo)`. Ver [buildShadowJournalJson] para el porque del segundo.
     */
    private fun readShadowJournalTail(max: Int): Pair<List<String>, Int> {
        val limite = max.coerceIn(1, 2000)
        return try {
            val interno = java.io.File(overlayService.filesDir, "TradeDraw_Audits")
            val externo = overlayService.getExternalFilesDir(null)?.let { java.io.File(it, "TradeDraw_Audits") }
            val candidatos = listOfNotNull(externo, interno)
            val archivo = candidatos.firstOrNull { java.io.File(it, "shadow_journal.csv").isFile }
                ?.let { java.io.File(it, "shadow_journal.csv") }
                ?: return emptyList<String>() to 0
            val todas = archivo.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            todas.takeLast(limite) to todas.size
        } catch (e: Exception) {
            Log.e("TradeDrawHttp", "Error leyendo shadow_journal.csv", e)
            emptyList<String>() to 0
        }
    }
}

/**
 * R23 Iter 2: serializa el tail del journal SHADOW a JSON valido.
 *
 * `total` = numero de lineas devueltas (0 cuando no hay archivo). `totalEnArchivo` se anade para
 * que el validador distinga "no hay decisiones" de "hay mas de `max`": `total` solo no lo dice.
 * Se conserva `total` con el sentido del caso vacio documentado en la spec.
 */
internal fun buildShadowJournalJson(lines: List<String>, totalEnArchivo: Int = lines.size): String {
    val cuerpo = lines.joinToString(",") { "\"${escapeJsonForJournal(it)}\"" }
    return """{"success":true,"total":${lines.size},"totalEnArchivo":$totalEnArchivo,"lines":[$cuerpo]}"""
}

/** Escapado de comillas/controles para incrustar cada linea cruda del CSV en el JSON de respuesta. */
internal fun escapeJsonForJournal(str: String): String {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

/**
 * R23 Iter 4: buffer circular, thread-safe y de capacidad FIJA de los ticks ya validados.
 *
 * Existe para cerrar el hallazgo de la Iter 3: la paridad Live <-> Replay seguia en "NO EVALUABLE"
 * (0 pares) porque no habia forma de extraer del dispositivo los ticks EXACTOS que entraron durante
 * la ventana viva, y por tanto el motor offline no podia reproducir las mismas decisiones. Con esta
 * fuente, el pipeline de validacion se baja los ticks por HTTP y reejecuta el replay determinista
 * sobre los mismos datos.
 *
 * Capacidad FIJA de [capacity] entradas: al llenarse se descarta la mas antigua. Es un buffer de
 * observabilidad, no un historico: 2000 ticks cubren ~20-30 min al ritmo real del feed (1-2 tick/s),
 * que es la ventana de validacion de R23. Un buffer sin tope en un servicio permanente es una fuga
 * de memoria, no una mejora.
 *
 * Se guardan los valores TAL CUAL los recibio el motor. NO se redondean: el activo del ring
 * (`Z-CRY/IDX`) tiene un paso por tick de ~1,6e-06 sobre ~641,87, o sea que su rango relativo entero
 * (2,5e-09) vive en los ultimos decimales del sexto digito. Cualquier redondeo a 6 decimales lo
 * borraria y el replay dejaria de reproducir la anomalia que R23 esta investigando.
 */
internal class TickRingBuffer(val capacity: Int = 2000) {

    data class Entry(val ts: Long, val ric: String, val price: Double)

    private val buffer = ArrayDeque<Entry>(minOf(capacity, 64))

    /** Numero de entradas retenidas ahora mismo (<= [capacity]). */
    val size: Int get() = synchronized(buffer) { buffer.size }

    /** Total de ticks aceptados desde el arranque, incluidos los ya descartados por el borde. */
    @Volatile
    var totalRecibidos: Long = 0L
        private set

    /**
     * Registra un tick validado. Ignora precios no finitos: un NaN o Infinity incrustado en el JSON
     * lo invalidaria, y el feed ya filtra estos casos antes de llegar aqui.
     */
    fun record(ts: Long, ric: String, price: Double) {
        if (!price.isFinite()) return
        synchronized(buffer) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(Entry(ts, ric, price))
        }
        totalRecibidos++
    }

    /**
     * Ultimas [max] entradas, en orden cronologico ASCENDENTE (la ultima es la mas reciente), que es
     * el orden que espera `loadTicks` del replay. [max] se acota a `[1, capacity]`.
     */
    fun tail(max: Int): List<Entry> {
        val limite = max.coerceIn(1, capacity)
        return synchronized(buffer) { buffer.toList().takeLast(limite) }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }
}

/**
 * R23 Iter 4: serializa el tail de ticks a JSON.
 *
 * `total` = ticks devueltos. `totalEnBuffer` se anade por la misma razon que en `/shadow_journal`
 * (precedente aceptado en la Iter 2): con `?max=500` sobre 2000 en buffer, `total` solo no distingue
 * "no hay ticks" de "hay mas de los que pediste".
 */
internal fun buildTicksJson(ticks: List<TickRingBuffer.Entry>, totalEnBuffer: Int = ticks.size): String {
    val cuerpo = ticks.joinToString(",") { t ->
        """{"ts":${t.ts},"ric":"${escapeJsonForJournal(t.ric)}","price":${t.price}}"""
    }
    return """{"success":true,"total":${ticks.size},"totalEnBuffer":$totalEnBuffer,"ticks":[$cuerpo]}"""
}

/**
 * R23 Iter 4: serializa el tail de ticks a CSV con cabecera `ts,ric,price`.
 *
 * El formato de precio es el round-trip EXACTO de Kotlin (`Double.toString`), no `%.6f`: ver la nota
 * de precision en [TickRingBuffer]. Ademas coincide con lo que `loadTicks` del replay espera leer
 * (`toDoubleOrNull`), asi que el archivo se puede volcar a disco y alimentar el replay sin tocar el
 * parser existente.
 */
internal fun buildTicksCsv(ticks: List<TickRingBuffer.Entry>): String {
    val sb = StringBuilder("ts,ric,price\n")
    for (t in ticks) sb.append(t.ts).append(',').append(t.ric).append(',').append(t.price).append('\n')
    return sb.toString()
}
