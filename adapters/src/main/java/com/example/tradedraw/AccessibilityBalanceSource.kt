package com.example.tradedraw

import android.util.Log
import com.example.tradedraw.core.BalanceReading
import com.example.tradedraw.core.BalanceSource

/**
 * Implementación de [BalanceSource] que lee desde [AutoTradeAccessibilityService]
 * aplicando el protocolo de mitigación dual-read con registro de divergencias (R29-B T-1.2).
 */
class AccessibilityBalanceSource(
    private val provider: () -> BalanceReading = {
        BalanceReading(
            balance = AutoTradeAccessibilityService.latestObservedBalance,
            lastReadMs = AutoTradeAccessibilityService.lastBalanceReadMs,
            isAvailable = AutoTradeAccessibilityService.instance != null
        )
    }
) : BalanceSource {

    companion object {
        @Volatile
        var dualReadEnabled: Boolean = true

        @Volatile
        var divergenceCount: Int = 0
            private set

        fun resetDivergenceCount() {
            divergenceCount = 0
        }
    }

    override fun read(): BalanceReading {
        val reading = provider()
        if (dualReadEnabled) {
            val staticBal = AutoTradeAccessibilityService.latestObservedBalance
            if (reading.balance != staticBal) {
                divergenceCount++
                Log.w("BalanceSourceDualRead", "Divergencia detectada: puerto=${reading.balance} static=$staticBal")
            }
        }
        return reading
    }
}
