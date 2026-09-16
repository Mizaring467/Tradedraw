package com.example.tradedraw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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

        var onBalanceUpdatedListener: ((Double) -> Unit)? = null
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
        if (instance == this) {
            instance = null
        }
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {}
        Log.w("TradeDraw", "AutoTradeAccessibilityService unbinded, returning true to allow rebind")
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
    fun performClickAt(x: Float, y: Float) {
        val overlay = OverlayService.instance
        if (overlay != null && overlay.isPointInsideHUD(x, y)) {
            overlay.temporarilyBypassHUD(250L) {
                dispatchClickGesture(x, y)
            }
        } else {
            dispatchClickGesture(x, y)
        }
    }

    private fun dispatchClickGesture(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val startTime = System.currentTimeMillis()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                val latency = System.currentTimeMillis() - startTime
                Log.i("TradeDraw", "⚡ Gesto táctil en ($x, $y) despachado en ${latency}ms")
                onGestureClickListener?.invoke(x, y)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w("TradeDraw", "⚠️ Click en ($x, $y) cancelado por el sistema Android")
            }
        }, null)
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
            // Anti-glitch: solo en cuenta demo descartamos lecturas < 500k si el saldo previo era de millones.
            // Si la cuenta es real (isDemoAccount == false) o el usuario cambió de cuenta, se acepta de inmediato.
            if (isDemoAccount && latestObservedBalance > 1_000_000.0 && found < 500_000.0) {
                Log.w("TradeDraw", "Lectura de balance descartada por glitch en demo (valor: $found vs previo: $latestObservedBalance)")
                return latestObservedBalance
            }
            if (latestObservedBalance > 1_000_000.0 && found < 500_000.0 && !isDemoAccount) {
                Log.i("TradeDraw", "Transición Demo -> Real confirmada: $found COP")
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
