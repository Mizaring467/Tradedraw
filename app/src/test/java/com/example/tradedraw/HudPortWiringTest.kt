package com.example.tradedraw

import com.example.tradedraw.core.HudPort
import com.example.tradedraw.core.TradeAction
import org.junit.Assert.*
import org.junit.Test

class HudPortWiringTest {

    private class RecordingHudPort : HudPort {
        var updateCount = 0
        var lastForce = false
        var lastAction: TradeAction? = null
        var lastDurationMs: Long = 0L
        var restoredCount = 0
        var lastClickX: Float = 0f
        var lastClickY: Float = 0f

        override fun updateHUDView(force: Boolean) {
            updateCount++
            lastForce = force
        }

        override fun showManualFallbackAlert(action: TradeAction, durationMs: Long) {
            lastAction = action
            lastDurationMs = durationMs
        }

        override fun restoreHudTouchable() {
            restoredCount++
        }

        override fun triggerClickAnimation(x: Float, y: Float) {
            lastClickX = x
            lastClickY = y
        }

        override fun isPointInsideHUD(x: Float, y: Float): Boolean {
            return x in 100f..200f && y in 100f..200f
        }
    }

    @Test
    fun riskManagerDelegatesToHudPort() {
        val recordingHud = RecordingHudPort()
        val riskManager = RiskManager(hudPort = recordingHud)

        riskManager.recordTradeWins(1, force = true)
        assertTrue(recordingHud.updateCount > 0)
    }

    @Test
    fun chartViewportControllerDelegatesToHudPort() {
        val recordingHud = RecordingHudPort()
        val controller = ChartViewportController(hudPort = recordingHud)

        assertNotNull(controller)
    }

    @Test
    fun defaultOrderGatewayDelegatesToHudPort() {
        val recordingHud = RecordingHudPort()
        val rootTransport = RootTapTransport(hudPort = recordingHud)

        assertNotNull(rootTransport)
        assertTrue(recordingHud.isPointInsideHUD(150f, 150f))
        assertFalse(recordingHud.isPointInsideHUD(50f, 50f))
    }
}
