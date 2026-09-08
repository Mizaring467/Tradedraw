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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("TradeDraw", "AutoTradeAccessibilityService connected")

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null && packageName != "com.example.tradedraw" && packageName != "com.android.systemui") {
                BrokerDetector.currentPackageName = packageName
            }

            // Escaneo pasivo continuo de saldo en cada cambio visual
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
        return super.onUnbind(intent)
    }

    /**
     * Simula un toque en las coordenadas dadas en pantalla (x, y).
     * El controlador de IA usará esto para hacer click en "Sube" o "Baja".
     */
    fun performClickAt(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("TradeDraw", "Click at ($x, $y) completed")
                onGestureClickListener?.invoke(x, y)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d("TradeDraw", "Click at ($x, $y) cancelled")
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
     */
    fun readCurrentBalance(): Double? {
        val root = try { rootInActiveWindow } catch (e: Exception) { null }
        val found = if (root != null) findBalanceInNode(root) else null
        if (found != null && found > 0.0) {
            latestObservedBalance = found
            return found
        }
        return if (latestObservedBalance > 0.0) latestObservedBalance else null
    }

    private fun findBalanceInNode(node: android.view.accessibility.AccessibilityNodeInfo?): Double? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        if (text.isNotBlank()) {
            val parsed = parseBalanceString(text)
            if (parsed != null && parsed > 0.0) return parsed
        }
        for (i in 0 until node.childCount) {
            val childResult = findBalanceInNode(node.getChild(i))
            if (childResult != null) return childResult
        }
        return null
    }

    private fun parseBalanceString(text: String): Double? {
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
