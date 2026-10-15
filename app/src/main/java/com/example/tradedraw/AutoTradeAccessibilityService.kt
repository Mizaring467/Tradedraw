package com.example.tradedraw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

enum class AssetClassification {
    FOREX_REAL,
    SYNTHETIC_OTC,
    UNKNOWN
}

class AutoTradeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoTradeAccessibilityService? = null
            private set
        var onGestureClickListener: ((Float, Float) -> Unit)? = null

        @Volatile
        var latestObservedBalance: Double = 0.0
            internal set

        @Volatile
        var lastBalanceReadMs: Long = 0L
            internal set

        @Volatile
        var isDemoAccount: Boolean = true
            internal set

        @Volatile
        var observedOrderAmount: Double = 0.0
            internal set

        @Volatile
        var latestObservedAsset: String = ""
            internal set

        @Volatile
        var isSyntheticOrOTC: Boolean = false
            internal set

        /**
         * Ronda 12 (A2): payout real leido del arbol de accesibilidad de Binomo.
         * `null` si no se ha podido leer todavia (el HUD cae entonces al valor configurado
         * y lo rotula como estimado). Se publica como `PayoutReader.PayoutReading` para que
         * el consumidor vea tambien si los dos metodos de lectura discrepan.
         */
        @Volatile
        var observedPayout: PayoutReader.PayoutReading = PayoutReader.reconcile(null, null)
            internal set

        var onBalanceUpdatedListener: ((Double) -> Unit)? = null
        var onAssetUpdatedListener: ((String, Boolean) -> Unit)? = null

        fun classifyAsset(assetName: String): AssetClassification {
            val upper = assetName.uppercase().trim()
            if (upper.isEmpty()) return AssetClassification.UNKNOWN
            if (upper.contains("IDX") || upper.contains("OTC")) {
                return AssetClassification.SYNTHETIC_OTC
            }
            val forexRegex = Regex("^[A-Z]{3}\\s*/\\s*[A-Z]{3}$")
            if (forexRegex.matches(upper)) {
                return AssetClassification.FOREX_REAL
            }
            val currencies = listOf("EUR", "USD", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")
            val count = currencies.count { upper.contains(it) }
            if (count >= 2 && upper.contains("/")) {
                return AssetClassification.FOREX_REAL
            }
            return AssetClassification.UNKNOWN
        }

        fun extractAssetCandidate(rawText: String): String? {
            val clean = rawText.trim()
            if (clean.length < 3 || clean.length > 35) return null
            val upper = clean.uppercase()
            if (upper.contains("CUENTA") || upper.contains("SALDO") || upper.contains("CANTIDAD") ||
                upper.contains("DEPOSITO") || upper.contains("HISTORIAL") || upper.contains("AJUSTES") ||
                upper.contains("TIEMPO") || upper.contains("INVERSIÓN") || upper.contains("INVERSION") ||
                upper.contains("REAL") || upper.contains("DEMO") || upper.contains("$") || upper.contains("COL")) {
                return null
            }
            val name = clean.replace(Regex("(?i)\\s*\\d{1,3}%\\s*"), "").trim()
            if (name.isEmpty()) return null
            val nameUpper = name.uppercase()
            if (nameUpper.contains("IDX") || nameUpper.contains("OTC")) {
                return name
            }
            val forexRegex = Regex("^[A-Z]{3}\\s*/\\s*[A-Z]{3}(\\s*\\(OTC\\)|\\s*OTC)?$", RegexOption.IGNORE_CASE)
            if (forexRegex.matches(name)) {
                return name
            }
            val currencies = listOf("EUR", "USD", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")
            if (currencies.count { nameUpper.contains(it) } >= 2 && nameUpper.contains("/")) {
                return name
            }
            return null
        }

        fun isAccessibilityPermissionGranted(context: android.content.Context): Boolean {
            if (instance != null) return true
            try {
                val accessibilityEnabled = android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0
                )
                if (accessibilityEnabled == 1) {
                    val enabledServices = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    val expectedFull = "${context.packageName}/${AutoTradeAccessibilityService::class.java.name}"
                    val expectedShort = "${context.packageName}/.AutoTradeAccessibilityService"
                    if (enabledServices.contains(expectedFull) || enabledServices.contains(expectedShort) || enabledServices.contains(context.packageName)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e("TradeDraw", "Error leyendo ENABLED_ACCESSIBILITY_SERVICES", e)
            }
            val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (s in enabledServices) {
                val sInfo = s.resolveInfo?.serviceInfo
                if (sInfo?.packageName == context.packageName && (sInfo?.name == AutoTradeAccessibilityService::class.java.name || sInfo?.name?.endsWith("AutoTradeAccessibilityService") == true)) {
                    return true
                }
            }
            return false
        }

        fun performClick(
            x: Float,
            y: Float,
            onResult: ((Boolean, String?) -> Unit)? = null
        ) {
            performClick(x, y, decisionId = null, tradeId = null, onResult = onResult)
        }

        fun performClick(
            x: Float,
            y: Float,
            decisionId: String?,
            tradeId: String?,
            onResult: ((Boolean, String?) -> Unit)? = null
        ) {
            // Ronda 3: en este dispositivo (root) Binomo FILTRA los eventos sintéticos de
            // dispatchGesture (el tap se "confirma" pero la orden no se registra → VOID),
            // mientras que `input tap` vía root SÍ coloca la orden. Por eso el root tap es
            // la vía PRIMARIA y la accesibilidad el fallback (antes era al revés).
            if (hasRootAccess()) {
                Log.i("TradeDraw", "Tap primario vía ROOT en ($x, $y) — dispositivo root detectado")
                // H8: el HUD flotante cubre los botones de Binomo; `input tap` a nivel sistema
                // aterrizaría en el overlay. Hay que volver el HUD NO-TÁCTIL igual que hace la
                // ruta de accesibilidad, o la orden no se registra (VOID).
                val overlay = OverlayService.instance
                if (overlay != null && overlay.isPointInsideHUD(x, y)) {
                    overlay.temporarilyBypassHUD(350L) {
                        executeRootTap(x, y, decisionId, tradeId, onResult, bypassHud = true)
                    }
                } else {
                    executeRootTap(x, y, decisionId, tradeId, onResult)
                }
                return
            }
            val inst = instance
            if (inst != null) {
                inst.performClickAt(x, y) { success, err ->
                    if (!success) {
                        Log.w("TradeDraw", "⚠️ Click por accesibilidad falló ($err). Probando fallback ROOT...")
                        executeRootTap(x, y, decisionId, tradeId, onResult)
                    } else {
                        onResult?.invoke(true, null)
                    }
                }
            } else {
                Log.w("TradeDraw", "⚠️ AutoTradeAccessibilityService inactivo. Ejecutando tap con fallback ROOT...")
                executeRootTap(x, y, decisionId, tradeId, onResult)
                autoHealAccessibility()
            }
        }

        @Volatile private var rootAccessKnown: Boolean? = null

        /**
         * Ronda 14 (A2): instantes del ultimo tap despachado por root, para instrumentar la
         * latencia real de ejecucion. Antes solo existia el instante de la DECISION; con
         * 255-1205 ms medidos de tap, ambos momentos pueden caer en segundos de vela distintos,
         * que es justo lo que hay que poder auditar. `lastTapCandleSecond` se mide AL TERMINAR
         * el tap, no al decidir.
         */
        @Volatile var lastTapStartTs: Long = 0L
            private set
        @Volatile var lastTapEndTs: Long = 0L
            private set
        @Volatile var lastTapCandleSecond: Int = -1
            private set

        /**
         * Ronda 3: detecta (una sola vez, cacheado) si el dispositivo tiene `su` disponible.
         * En dispositivos root se prioriza `input tap` porque Binomo bloquea los gestos de accesibilidad.
         */
        fun hasRootAccess(): Boolean {
            rootAccessKnown?.let { return it }
            val result = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
                p.waitFor()
                out.contains("uid=0")
            } catch (e: Exception) {
                false
            }
            rootAccessKnown = result
            Log.i("TradeDraw", "Detección de root: $result")
            return result
        }

        fun executeRootTap(
            x: Float,
            y: Float,
            onResult: ((Boolean, String?) -> Unit)? = null,
            bypassHud: Boolean = false
        ) {
            executeRootTap(x, y, decisionId = null, tradeId = null, onResult = onResult, bypassHud = bypassHud)
        }

        fun executeRootTap(
            x: Float,
            y: Float,
            decisionId: String?,
            tradeId: String?,
            onResult: ((Boolean, String?) -> Unit)? = null,
            bypassHud: Boolean = false
        ) {
            Thread {
                val inicioMs = System.currentTimeMillis()
                lastTapStartTs = inicioMs
                TradeEventLog.add(
                    TradeEventLog.Kind.TAP,
                    "START ($x,$y) bypassHud=$bypassHud",
                    decisionId = decisionId,
                    tradeId = tradeId
                )
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap ${x.toInt()} ${y.toInt()}"))
                    val exit = p.waitFor()
                    val duracionMs = System.currentTimeMillis() - inicioMs
                    // R14/A2: instante y segundo de vela AL TERMINAR el tap. Es el dato que faltaba:
                    // el journal guardaba el segundo de la decision, no el de la ejecucion.
                    lastTapEndTs = System.currentTimeMillis()
                    lastTapCandleSecond = ((lastTapEndTs / 1000L) % 60L).toInt()
                    TradeEventLog.add(
                        TradeEventLog.Kind.TAP,
                        "END root ($x,$y) exit=$exit durMs=$duracionMs bypassHud=$bypassHud " +
                            "startTs=$inicioMs endTs=$lastTapEndTs csTapEnd=$lastTapCandleSecond",
                        decisionId = decisionId,
                        tradeId = tradeId
                    )
                    if (exit == 0) {
                        Log.i("TradeDraw", "⚡ Tap ejecutado con éxito vía ROOT en ($x, $y) en ${duracionMs}ms")
                        // Ronda 3: el callback puede tocar vistas (HUD); debe correr en el hilo
                        // principal o Android lanza CalledFromWrongThreadException.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try { onGestureClickListener?.invoke(x, y) } catch (e: Exception) {}
                        }
                        onResult?.invoke(true, null)
                    } else {
                        Log.w("TradeDraw", "Root tap falló con código $exit")
                        onResult?.invoke(false, "Root tap exit=$exit")
                    }
                } catch (e: Exception) {
                    Log.e("TradeDraw", "Excepción ejecutando root tap", e)
                    TradeEventLog.add(
                        TradeEventLog.Kind.TAP,
                        "root ($x,$y) EXCEPCION ${e.message ?: "?"}"
                    )
                    onResult?.invoke(false, e.message)
                } finally {
                    // R13/A4: liberar el bypass del HUD SOLO cuando el tap ha terminado.
                    // Antes lo liberaba un temporizador fijo de 350 ms que el tap real
                    // (141-327 ms medidos, sin cota con carga alta) rebasaba, dejando el HUD
                    // tactil sobre el boton: el overlay se comia el toque y la orden no se
                    // registraba. Este `finally` es lo que cierra la causa raiz.
                    try { OverlayService.instance?.restoreHudTouchable() } catch (e: Exception) {}
                }
            }.start()
        }

        fun autoHealAccessibility() {
            Thread {
                try {
                    // Ronda 3: un simple "put" del mismo valor NO hace que MIUI reenlace el servicio
                    // (el valor ya estaba puesto y el sistema lo ignora). Se fuerza un TOGGLE REAL:
                    // primero se vacía/desactiva y luego se re-registra, lo que dispara el rebind.
                    val p = Runtime.getRuntime().exec(arrayOf(
                        "su", "-c",
                        "settings put secure enabled_accessibility_services ''; " +
                        "settings put secure accessibility_enabled 0; " +
                        "sleep 1; " +
                        "settings put secure enabled_accessibility_services com.example.tradedraw/.AutoTradeAccessibilityService; " +
                        "settings put secure accessibility_enabled 1"
                    ))
                    p.waitFor()
                    Log.i("TradeDraw", "Auto-heal accessibility invocado vía root (toggle real)")
                } catch (e: Exception) {
                    Log.e("TradeDraw", "Error en autoHealAccessibility", e)
                }
            }.start()
        }
    }

    private val commandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.example.tradedraw.TAP" -> {
                    val x = intent.getFloatExtra("x", 0f)
                    val y = intent.getFloatExtra("y", 0f)
                    Log.d("TradeDraw", "ADB TAP recibido en ($x, $y)")
                    performClickAt(x, y)
                }
                "com.example.tradedraw.SWIPE" -> {
                    val x1 = intent.getFloatExtra("x1", 0f)
                    val y1 = intent.getFloatExtra("y1", 0f)
                    val x2 = intent.getFloatExtra("x2", 0f)
                    val y2 = intent.getFloatExtra("y2", 0f)
                    val dur = intent.getLongExtra("duration", 300L)
                    Log.d("TradeDraw", "ADB SWIPE recibido de ($x1, $y1) a ($x2, $y2)")
                    performSwipe(x1, y1, x2, y2, dur)
                }
                "com.example.tradedraw.BACK" -> {
                    Log.d("TradeDraw", "ADB BACK global recibido")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private var lastBalanceScanTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("TradeDraw", "AutoTradeAccessibilityService connected")
        try {
            OverlayService.instance?.updateHUDView(true)
        } catch (e: Exception) {}

        // Ronda 16 (A): este receptor estaba registrado RECEIVER_EXPORTED y sus acciones
        // TAP/SWIPE/BACK ejecutan performClickAt y performSwipe, es decir taps y gestos REALES
        // contra el broker. Cualquier app del dispositivo podia provocarlos, esquivando el modo,
        // los vetos y RiskManager: un bot en DISABLED no estaba protegido frente a esto.
        //
        // Correcto de la ronda: RECEIVER_NOT_EXPORTED existe SOLO desde API 33 y el dispositivo
        // de pruebas es API 31, asi que la rama `else` de 2 argumentos habria dejado el receptor
        // EXPORTADO: el parche habria sido un no-op justo en el telefono a proteger. Se usa la
        // sobrecarga de 4 argumentos con un permiso de firma (existe desde API 1), que restringe
        // la emision a las apps firmadas con esta misma clave en TODAS las versiones.
        // La app NO emite estos broadcasts en ningun punto (no hay sendBroadcast), asi que esto no
        // rompe ninguna funcionalidad interna.
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.tradedraw.TAP")
            addAction("com.example.tradedraw.SWIPE")
            addAction("com.example.tradedraw.BACK")
        }
        try {
            registerReceiver(
                commandReceiver,
                filter,
                "com.example.tradedraw.permission.INTERNAL_COMMAND",
                null
            )
        } catch (e: Exception) {
            Log.e("TradeDraw", "Error registrando commandReceiver", e)
        }
    }

    override fun onRebind(intent: android.content.Intent?) {
        super.onRebind(intent)
        instance = this
        Log.i("TradeDraw", "AutoTradeAccessibilityService rebinded exitosamente")
        try {
            OverlayService.instance?.updateHUDView(true)
        } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()
        if (packageName != null && packageName != "com.example.tradedraw" && packageName != "com.android.systemui") {
            BrokerDetector.currentPackageName = packageName
        }

        // Throttle escaneo pasivo de saldo: máximo 1 vez cada 5s para proteger búfer Binder
        val now = System.currentTimeMillis()
        if (now - lastBalanceScanTime >= 5000L) {
            lastBalanceScanTime = now
            try {
                val sourceNode = event.source
                val bal = if (sourceNode != null) findBalanceInNode(sourceNode) else null
                val finalBal = bal ?: readCurrentBalance()
                if (finalBal != null && finalBal > 0.0) {
                    latestObservedBalance = finalBal
                    onBalanceUpdatedListener?.invoke(finalBal)
                }
            } catch (e: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.d("TradeDraw", "AutoTradeAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.w("TradeDraw", "AutoTradeAccessibilityService unbinded transitoriamente, retornando true para rebind")
        try {
            OverlayService.instance?.updateHUDView(true)
        } catch (e: Exception) {}
        return true
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    /**
     * Simula un toque en las coordenadas dadas en pantalla (x, y).
     * El controlador de IA usará esto para hacer click en "Sube" o "Baja".
     */
    fun performClickAt(
        x: Float,
        y: Float,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        val overlay = OverlayService.instance
        if (overlay != null && overlay.isPointInsideHUD(x, y)) {
            overlay.temporarilyBypassHUD(250L) {
                dispatchClickGesture(x, y, onResult)
            }
        } else {
            dispatchClickGesture(x, y, onResult)
        }
    }

    private fun dispatchClickGesture(
        x: Float,
        y: Float,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(x, y)
            // Ronda 3: un Path con solo moveTo puede no registrar un TAP real en algunos
            // brokers (MIUI/Binomo). Se añade un lineTo al mismo punto para forzar un
            // "press-and-release" determinista en las coordenadas exactas.
            lineTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val startTime = System.currentTimeMillis()
        val isConfirmed = java.util.concurrent.atomic.AtomicBoolean(false)

        val timeoutRunnable = Runnable {
            if (isConfirmed.compareAndSet(false, true)) {
                Log.w("TradeDraw", "⚠️ Gesto táctil en ($x, $y) no confirmado en 300ms (Timeout de Accesibilidad)")
                onResult?.invoke(false, "Timeout 300ms sin confirmación")
            }
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.postDelayed(timeoutRunnable, 300L)

        val dispatched = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val latency = System.currentTimeMillis() - startTime
                    if (isConfirmed.compareAndSet(false, true)) {
                        Log.i("TradeDraw", "⚡ Gesto táctil en ($x, $y) despachado y confirmado en ${latency}ms")
                        onGestureClickListener?.invoke(x, y)
                        onResult?.invoke(true, null)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val latency = System.currentTimeMillis() - startTime
                    if (isConfirmed.compareAndSet(false, true)) {
                        Log.w("TradeDraw", "⚠️ Click en ($x, $y) cancelado por el sistema Android tras ${latency}ms")
                        onResult?.invoke(false, "Gesto cancelado por Android")
                    }
                }
            }, null)
        } catch (e: Exception) {
            Log.e("TradeDraw", "Excepción al despachar gesto", e)
            false
        }

        if (!dispatched) {
            mainHandler.removeCallbacks(timeoutRunnable)
            if (isConfirmed.compareAndSet(false, true)) {
                Log.w("TradeDraw", "⚠️ dispatchGesture rechazado por el sistema Android")
                onResult?.invoke(false, "dispatchGesture rechazado por el sistema")
            }
        }
    }

    /**
     * Simula un gesto de arrastre o swipe entre dos puntos.
     */
    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("TradeDraw", "Swipe from ($x1, $y1) to ($x2, $y2) completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("TradeDraw", "Swipe cancelled")
            }
        }, null)
    }

    /**
     * Lee el saldo actual del broker desde la jerarquía accesible de Android.
     * Soporta formatos: "Col$50,112,911.36", "$1,250.50", "50 112 911,36", etc.
     * Excluye etiquetas de órdenes en el gráfico o botones de la parte inferior.
     */
    fun readCurrentBalance(): Double? {
        val root = try { rootInActiveWindow } catch (e: Exception) { null }
        if (root != null) {
            scanScreenContext(root)
        }
        val found = if (root != null) findBalanceInNode(root) else null
        if (found != null && found > 0.0) {
            // Anti-glitch: descartamos lecturas < 500k si el saldo previo era de millones (> 1M),
            // protegiendo contra anomalías de UI (ej. "1.00") tanto en cuenta demo como real.
            if (latestObservedBalance > 1_000_000.0 && found < 500_000.0) {
                Log.w("TradeDraw", "Lectura de balance descartada por glitch (valor: $found vs previo: $latestObservedBalance)")
                return latestObservedBalance
            }
            latestObservedBalance = found
            lastBalanceReadMs = System.currentTimeMillis()
            return found
        }
        return if (latestObservedBalance > 0.0) latestObservedBalance else null
    }

    private fun scanScreenContext(root: android.view.accessibility.AccessibilityNodeInfo?) {
        if (root == null) return
        try {
            traverseScreenContext(root)
        } catch (e: Exception) {}
        // Ronda 12 (A2): segundo metodo de lectura del payout. Los botones CALL/PUT muestran
        // `cantidad * (1 + payout)` como texto (p.ej. Col$182,000.00 sobre Cantidad Col$100,000
        // => payout 0.82). Se recorre el arbol recogiendo importes y se deduce el cociente.
        // Se hace DESPUES del recorrido porque `observedOrderAmount` (la cantidad) puede
        // aparecer despues de los botones en el arbol.
        try {
            updatePayoutFromButtonRatio(root)
        } catch (e: Exception) {}
    }

    /**
     * Deduce el payout comparando cada importe visible con la cantidad apostada. El saldo de la
     * cuenta tambien es un importe, pero su cociente respecto a la cantidad (cientos de veces)
     * cae fuera del rango plausible y `PayoutReader` lo descarta solo.
     */
    private fun updatePayoutFromButtonRatio(node: android.view.accessibility.AccessibilityNodeInfo?) {
        val stake = observedOrderAmount
        if (stake <= 0.0) return
        var candidato: Double? = null
        val pendientes = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        node?.let { pendientes.addLast(it) }
        while (pendientes.isNotEmpty()) {
            val n = pendientes.removeLast()
            val t = n.text?.toString()
            if (!t.isNullOrBlank() && (t.contains("$") || t.contains("Col"))) {
                val importe = parseAmountString(t)
                PayoutReader.deriveFromButtonRatio(importe, stake)?.let { p -> candidato = p }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.addLast(it) }
        }
        if (candidato != null) {
            observedPayout = PayoutReader.reconcile(observedPayout.fromPercentLabel, candidato)
        }
    }

    private fun traverseScreenContext(node: android.view.accessibility.AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.packageName?.toString() == packageName) return
        val text = node.text?.toString() ?: ""
        if (text.isNotBlank()) {
            val lower = text.lowercase()
            if (lower.contains("cuenta real") || lower == "real") {
                isDemoAccount = false
            } else if (lower.contains("cuenta demo") || lower == "demo") {
                isDemoAccount = true
            }

            val assetCandidate = extractAssetCandidate(text)
            if (assetCandidate != null) {
                val isActiveTab = isTabExplicitlyActive(node)
                // Solo actualizar si la pestaña está explícitamente seleccionada o si aún no hay ningún activo detectado
                if (isActiveTab || latestObservedAsset.isBlank()) {
                    latestObservedAsset = assetCandidate
                    // Ronda 12 (A2) / Ronda 13 (A3): el payout se intenta rescatar del MISMO nodo
                    // (`Crypto IDX 83%`), porque `extractAssetCandidate` descarta ese sufijo.
                    // HALLAZGO R13: en el dispositivo este camino NUNCA produce valor
                    // (`payoutFromPercentLabel = null` de forma persistente): Binomo expone el
                    // porcentaje en un nodo distinto del nombre del activo, y no hay relacion
                    // padre/hermano explotable (el dump de la pantalla de trading sale vacio:
                    // Binomo bloquea `uiautomator`). Se mantiene el intento porque es gratis y
                    // cubre el caso de que el broker vuelva a fusionar los nodos, pero el metodo
                    // 1 NO se declara validado en dispositivo. El metodo 2 (ratio del boton) es
                    // el unico confirmado.
                    PayoutReader.parsePercentLabel(
                        Regex("(\\d{1,3}\\s*%)").find(text)?.value
                    )?.let { pct ->
                        val actual = observedPayout
                        observedPayout = PayoutReader.reconcile(pct, actual.fromButtonRatio)
                        // R13/A3: si el metodo 1 acaba funcionando, el cruce con el metodo 2 pasa
                        // a ser informativo de verdad. Se registra para que `/events` lo muestre.
                        if (actual.fromButtonRatio != null &&
                            Math.abs(pct - actual.fromButtonRatio) > PayoutReader.TOLERANCIA
                        ) {
                            TradeEventLog.add(
                                TradeEventLog.Kind.PAYOUT_DISCREPANTE,
                                "etiqueta=%.3f boton=%.3f".format(java.util.Locale.US, pct, actual.fromButtonRatio)
                            )
                        }
                    }
                    val upper = assetCandidate.uppercase()
                    val isSyn = upper.contains("IDX") || upper.contains("OTC")
                    isSyntheticOrOTC = isSyn
                    onAssetUpdatedListener?.invoke(assetCandidate, isSyn)
                    try {
                        OverlayService.instance?.binomoWebSocketClient?.let { ws ->
                            // Ronda 20: el mapeo se extrajo a `AssetRicMapper` porque el guard de
                            // desajuste pantalla<->feed del motor necesita el MISMO mapeo, y
                            // alguna clase tiene que ser la unica fuente de verdad.
                            val formattedRic = AssetRicMapper.ricParaActivoVisible(assetCandidate)
                            ws.updateActiveAsset(formattedRic)
                            // R13 (adenda 3): los cambios de activo deben quedar en `/events`.
                            // Sin esto no habia forma de ver QUE RIC se estaba suscribiendo, que
                            // es justo lo que hacia falta para diagnosticar el activo sin ticks.
                            TradeEventLog.add(
                                TradeEventLog.Kind.ASSET,
                                "visible=\"$assetCandidate\" ric=\"$formattedRic\""
                            )
                        }
                    } catch (e: Exception) {}
                }
            }

            if (lower.contains("cantidad")) {
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sib = parent.getChild(i)
                        val sibText = sib?.text?.toString() ?: ""
                        if (sibText != text && (sibText.contains("$") || sibText.contains("Col"))) {
                            val parsed = parseAmountString(sibText)
                            if (parsed != null && parsed > 0.0) {
                                observedOrderAmount = parsed
                            }
                        }
                    }
                }
            }
        }
        for (i in 0 until node.childCount) {
            traverseScreenContext(node.getChild(i))
        }
    }

    private fun isTabExplicitlyActive(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        var curr: android.view.accessibility.AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (curr != null && depth < 3) {
            if (curr.isSelected) return true
            val resId = curr.viewIdResourceName ?: ""
            if (resId.contains("SelectedAssetTab", ignoreCase = true)) return true
            for (i in 0 until curr.childCount) {
                val child = curr.getChild(i)
                val cRes = child?.viewIdResourceName ?: ""
                if (cRes.contains("CloseButton", ignoreCase = true)) {
                    return true
                }
            }
            curr = curr.parent
            depth++
        }
        return false
    }

    private fun findBalanceInNode(node: android.view.accessibility.AccessibilityNodeInfo?): Double? {
        if (node == null) return null
        
        // Si el nodo proviene de TradeDraw, solo permitir si NO es parte del HUD flotante o controles de superposición
        if (node.packageName?.toString() == packageName) {
            val resId = node.viewIdResourceName ?: ""
            if (resId.contains("hud", ignoreCase = true) || 
                resId.contains("overlay", ignoreCase = true) || 
                resId.contains("bubble", ignoreCase = true) ||
                resId.contains("control", ignoreCase = true) ||
                resId.contains("btn_", ignoreCase = true) ||
                resId.contains("chat", ignoreCase = true) ||
                resId.contains("et_auth_token", ignoreCase = true)) {
                return null
            }
        }

        // Prioridad máxima: nodo oficial de saldo de Binomo (BalanceText)
        val resId = node.viewIdResourceName ?: ""
        if (resId.contains("BalanceText", ignoreCase = true)) {
            val text = node.text?.toString() ?: ""
            val parsed = parseBalanceString(text)
            if (parsed != null && parsed > 0.0) return parsed
        }
        
        // Descartar nodos fuera de la cabecera de saldo superior (en BinomoAuthActivity el header web está hasta 30% de la pantalla)
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val displayH = resources.displayMetrics.heightPixels
        val maxTopRatio = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 0.35f else 0.30f
        if (rect.top > (displayH * maxTopRatio)) {
            return null
        }

        val text = node.text?.toString() ?: ""
        if (text.isNotBlank()) {
            val lower = text.lowercase()
            if (lower.contains("cuenta real") || lower == "real") {
                isDemoAccount = false
            } else if (lower.contains("cuenta demo") || lower == "demo") {
                isDemoAccount = true
            }
            if (!lower.contains("m0") && !lower.contains("m1") && !lower.contains("m2") && !lower.contains("strat") && !lower.contains("auto") && !lower.contains("crypto") && !lower.contains("idx")) {
                val parsed = parseBalanceString(text)
                if (parsed != null && parsed > 0.0) return parsed
            }
        }
        for (i in 0 until node.childCount) {
            val childResult = findBalanceInNode(node.getChild(i))
            if (childResult != null) return childResult
        }
        return null
    }

    private fun parseAmountString(text: String): Double? {
        if (!text.contains("$") && !text.contains("Col") && !text.contains("USD")) return null
        val clean = text.replace("[^0-9.,]".toRegex(), "")
        if (clean.isBlank()) return null
        return try {
            val standard = if (clean.contains(",") && clean.contains(".")) {
                if (clean.lastIndexOf(".") > clean.lastIndexOf(",")) {
                    clean.replace(",", "")
                } else {
                    clean.replace(".", "").replace(",", ".")
                }
            } else if (clean.contains(",")) {
                if (clean.length - clean.lastIndexOf(",") == 3) {
                    clean.replace(",", ".")
                } else {
                    clean.replace(",", "")
                }
            } else clean
            standard.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseBalanceString(text: String): Double? {
        val lower = text.lowercase()
        if (lower.contains("cantidad") || lower.contains("ingreso") || lower.contains("deposito") || lower.contains("depositar") ||
            lower.contains("crypto") || lower.contains("idx") || lower.contains("otc") || lower.contains("cuenta demo") || lower.contains("cuenta real")) {
            return null // Descartar botones y banners flotantes de ganancias del activo
        }
        val hasCurrencySign = text.contains("$") || text.contains("€") || text.contains("£") || text.contains("Col") || text.contains("USD")
        val clean = text.replace("[^0-9.,]".toRegex(), "")
        if (clean.isBlank()) return null

        // Si no tiene signo monetario, debe tener estructura de miles/saldo (ej: "39.642.536" o "39,642,536.00")
        if (!hasCurrencySign) {
            val isThousandsFormatted = clean.contains(".") || clean.contains(",")
            val digitsCount = clean.replace("[.,]".toRegex(), "").length
            if (!isThousandsFormatted || digitsCount < 4) {
                return null
            }
        }

        return try {
            val standard = if (clean.contains(",") && clean.contains(".")) {
                if (clean.lastIndexOf(".") > clean.lastIndexOf(",")) {
                    clean.replace(",", "")
                } else {
                    clean.replace(".", "").replace(",", ".")
                }
            } else if (clean.contains(",")) {
                if (clean.length - clean.lastIndexOf(",") == 3) {
                    clean.replace(",", ".")
                } else {
                    clean.replace(",", "")
                }
            } else if (clean.contains(".")) {
                val parts = clean.split(".")
                if (parts.size > 2 || (parts.size == 2 && parts[1].length == 3)) {
                    clean.replace(".", "")
                } else {
                    clean
                }
            } else clean

            standard.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
