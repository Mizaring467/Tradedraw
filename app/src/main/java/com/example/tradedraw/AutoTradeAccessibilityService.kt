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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("TradeDraw", "AutoTradeAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null && packageName != "com.example.tradedraw" && packageName != "com.android.systemui") {
                BrokerDetector.currentPackageName = packageName
            }
        }
    }

    override fun onInterrupt() {
        Log.d("TradeDraw", "AutoTradeAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance == this) {
            instance = null
        }
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
}
