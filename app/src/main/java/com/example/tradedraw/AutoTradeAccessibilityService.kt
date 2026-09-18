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

        val filter = android.content.IntentFilter().apply {
            addAction("com.example.tradedraw.TAP")
            addAction("com.example.tradedraw.SWIPE")
            addAction("com.example.tradedraw.BACK")
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter)
            }
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
            return found
        }
        return if (latestObservedBalance > 0.0) latestObservedBalance else null
    }

    private fun scanScreenContext(root: android.view.accessibility.AccessibilityNodeInfo?) {
        if (root == null) return
        try {
            traverseScreenContext(root)
        } catch (e: Exception) {}
    }

    private fun traverseScreenContext(node: android.view.accessibility.AccessibilityNodeInfo?) {
        if (node == null) return
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
                latestObservedAsset = assetCandidate
                val upper = assetCandidate.uppercase()
                val isSyn = upper.contains("IDX") || upper.contains("OTC")
                isSyntheticOrOTC = isSyn
                onAssetUpdatedListener?.invoke(assetCandidate, isSyn)
                try {
                    OverlayService.instance?.binomoWebSocketClient?.let { ws ->
                        val formattedRic = when {
                            upper.contains("IDX") -> "Z-CRY/IDX"
                            upper.contains("EUR/USD") -> "EUR/USD"
                            upper.contains("GBP/USD") -> "GBP/USD"
                            upper.contains("USD/JPY") -> "USD/JPY"
                            else -> assetCandidate.replace(" ", "")
                        }
                        ws.updateActiveAsset(formattedRic)
                    }
                } catch (e: Exception) {}
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

    private fun findBalanceInNode(node: android.view.accessibility.AccessibilityNodeInfo?): Double? {
        if (node == null) return null
        
        // Descartar nodos fuera de la cabecera de saldo superior
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val displayH = resources.displayMetrics.heightPixels
        val maxTopRatio = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 0.18f else 0.12f
        if (rect.top > (displayH * maxTopRatio)) {
            return null // Saldo de Binomo siempre está en la barra superior (top < 12% portrait / 18% landscape)
        }

        val text = node.text?.toString() ?: ""
        if (text.isNotBlank()) {
            val lower = text.lowercase()
            if (lower.contains("cuenta real") || lower == "real") {
                isDemoAccount = false
            } else if (lower.contains("cuenta demo") || lower == "demo") {
                isDemoAccount = true
            }
            val parsed = parseBalanceString(text)
            if (parsed != null && parsed > 0.0) return parsed
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
            lower.contains("crypto") || lower.contains("idx") || lower.contains("otc")) {
            return null // Descartar botones y banners flotantes de ganancias del activo
        }
        if (!text.contains("$") && !text.contains("€") && !text.contains("£") && !text.contains("Col") && !text.contains("USD")) {
            return null
        }
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
}
