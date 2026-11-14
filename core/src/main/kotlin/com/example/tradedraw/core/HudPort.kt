package com.example.tradedraw.core

interface HudPort {
    fun updateHUDView(force: Boolean = false)
    fun showManualFallbackAlert(action: TradeAction, durationMs: Long = 2500L)
    fun restoreHudTouchable()
    fun triggerClickAnimation(x: Float, y: Float)
    fun isPointInsideHUD(x: Float, y: Float): Boolean = false
    fun temporarilyBypassHUD(durationMs: Long = 1500L, onBypassed: (() -> Unit)? = null) {
        onBypassed?.invoke()
    }
}
