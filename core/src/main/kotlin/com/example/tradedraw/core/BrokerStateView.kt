package com.example.tradedraw.core

data class BrokerStateView(
    val balance: Double = 0.0,
    val lastBalanceReadMs: Long = 0L,
    val isDemoAccount: Boolean = true,
    val observedAsset: String = "Crypto IDX",
    val observedPayout: Double? = null,
    val observedOrderAmount: Double = 0.0,
    val isSyntheticOrOTC: Boolean = false
)

interface BrokerSensor {
    fun getBrokerState(): BrokerStateView
}
