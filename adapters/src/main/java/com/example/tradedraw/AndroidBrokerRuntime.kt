package com.example.tradedraw

import android.content.Context
import com.example.tradedraw.core.*

/** Canonical Android implementation; business consumers receive only BrokerRuntimePort. */
class AndroidBrokerRuntime(private val context: Context) : BrokerRuntimePort {
    override val demoAccountGuard: DemoAccountGuard get() = AutoTradeAccessibilityService.demoAccountGuard
    override fun getBrokerState() = AutoTradeAccessibilityService.getBrokerState()
    override fun read() = BalanceReading(AutoTradeAccessibilityService.latestObservedBalance,
        AutoTradeAccessibilityService.lastBalanceReadMs, AutoTradeAccessibilityService.instance != null)
    override fun refreshBalance(useLastObservation: Boolean): Double =
        AutoTradeAccessibilityService.instance?.readCurrentBalance()
            ?: if (useLastObservation) AutoTradeAccessibilityService.latestObservedBalance else 0.0
    override fun isAccessibilityPermissionGranted() = AutoTradeAccessibilityService.isAccessibilityPermissionGranted(context)
}

fun bindBrokerUi(hud: HudPort, assetSink: (String) -> Unit) = AutoTradeAccessibilityService.bindUi(hud, assetSink)
fun unbindBrokerUi(hud: HudPort) = AutoTradeAccessibilityService.unbindUi(hud)
