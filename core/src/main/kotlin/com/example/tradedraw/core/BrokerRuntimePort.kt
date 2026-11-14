package com.example.tradedraw.core

/** Platform observation/refresh boundary. Account identity remains governed by the positive guard. */
interface BrokerRuntimePort : BrokerSensor, BalanceSource {
    val demoAccountGuard: DemoAccountGuard
    fun refreshBalance(useLastObservation: Boolean = true): Double
    fun isAccessibilityPermissionGranted(): Boolean
}
