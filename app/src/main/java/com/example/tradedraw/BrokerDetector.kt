package com.example.tradedraw

object BrokerDetector {
    var currentPackageName: String = ""
        set(value) {
            field = value
            onBrokerChanged?.invoke(currentBroker)
        }

    var onBrokerChanged: ((Broker) -> Unit)? = null

    val currentBroker: Broker
        get() {
            return when {
                currentPackageName.contains("binomo") -> Broker.BINOMO
                currentPackageName.contains("iqoption") -> Broker.IQ_OPTION
                currentPackageName.contains("quotex") -> Broker.QUOTEX
                currentPackageName.contains("pocket") -> Broker.POCKET_OPTION
                currentPackageName.contains("marketly.trading") -> Broker.BINOMO // Binomo sometimes uses marketly
                else -> Broker.UNKNOWN
            }
        }
}

enum class Broker {
    BINOMO,
    IQ_OPTION,
    QUOTEX,
    POCKET_OPTION,
    UNKNOWN
}
