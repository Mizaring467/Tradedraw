package com.example.tradedraw

object BrokerDetector {
    var currentPackageName: String = ""
        set(value) {
            if (field != value) {
                lastPackageChangeAtMs = System.currentTimeMillis()
            }
            field = value
            onBrokerChanged?.invoke(currentBroker)
        }

    /**
     * Ronda 2 (E→F): epoch ms del último cambio real de [currentPackageName]. Sirve de
     * frescura de ventana para [BrokerProcessPresenceSensor]: el evento que escribe aquí
     * solo puede existir si el broker tuvo ventana en primer plano recientemente.
     */
    @Volatile
    var lastPackageChangeAtMs: Long = 0L
        private set

    var onBrokerChanged: ((Broker) -> Unit)? = null

    val currentBroker: Broker
        get() = currentBrokerFor(currentPackageName)

    /** Ronda 2: clasificación pura y reutilizable (consumida por el sensor de presencia). */
    fun currentBrokerFor(packageName: String): Broker {
        return when {
            packageName.contains("binomo") -> Broker.BINOMO
            packageName.contains("marketly.trading") -> Broker.BINOMO // Binomo APK package
            packageName.contains("iqoption") -> Broker.IQ_OPTION
            packageName.contains("quotex") -> Broker.QUOTEX
            packageName.contains("pocket") -> Broker.POCKET_OPTION
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
