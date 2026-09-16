package com.example.tradedraw

/**
 * Representa un micro-tick de cotización recibido en tiempo real por WebSocket.
 *
 * @param asset Símbolo o identificador del activo (ej. "CRYPTO_IDX", "Z-CRY/IDX", "BTCUSDT")
 * @param price Precio de cotización instantáneo
 * @param timestampMs Marca de tiempo en milisegundos en que se emitió el tick
 * @param velocity Derivada instantánea del precio (Delta P / Delta t) respecto a ticks previos
 * @param isBullishImpulse Indica si el tick representa una subida rápida de precio
 * @param isBearishImpulse Indica si el tick representa una caída rápida de precio
 */
data class MarketTick(
    val asset: String,
    val price: Double,
    val timestampMs: Long = System.currentTimeMillis(),
    val velocity: Float = 0f,
    val isBullishImpulse: Boolean = false,
    val isBearishImpulse: Boolean = false,
    val smoothedVelocity: Float = velocity
) {
    /** Segundo actual dentro del ciclo de vela de 60s (0..59) */
    val candleSecond: Int
        get() = ((timestampMs / 1000L) % 60L).toInt()

    /** Ventana estricta de entrada al segundo :00 (:57 a :05) */
    val isStrictTimingWindow: Boolean
        get() = candleSecond in 57..59 || candleSecond in 0..5

    /** Veto estricto de entrada a mitad de ciclo de vela (:15 a :55) */
    val isTimingVetoed: Boolean
        get() = candleSecond in 15..55
}

enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
    UNAUTHORIZED
}

/**
 * Filtro Cuantitativo de Timing Estricto y Detección de Micro-Rango / Choppiness (Binomo 60s).
 */
object MarketTickFilters {
    /** Umbral máximo de rango para clasificar como micro-rango ruidoso / choppy (0.05%) */
    const val MAX_CHOPPY_RANGE_PERCENT = 0.05 // 0.05%

    /**
     * Evalúa si el segundo actual está dentro de la ventana estricta (:57 a :05).
     */
    fun isStrictTimingWindow(second: Int): Boolean = second in 57..59 || second in 0..5

    /**
     * Evalúa si el segundo actual cae dentro del veto estricto (:15 a :55).
     */
    fun isTimingVetoed(second: Int): Boolean = second in 15..55

    /**
     * Calcula el rango porcentual de las últimas velas: ((maxHigh - minLow) / refPrice) * 100.
     * Retorna si el rango es inferior al 0.05%.
     */
    fun isMicroRange(highPrices: List<Double>, lowPrices: List<Double>, refPrice: Double): Boolean {
        if (highPrices.isEmpty() || lowPrices.isEmpty() || refPrice <= 0.0) return false
        val maxHigh = highPrices.maxOrNull() ?: return false
        val minLow = lowPrices.minOrNull() ?: return false
        val rangePct = ((maxHigh - minLow) / refPrice) * 100.0
        return rangePct < MAX_CHOPPY_RANGE_PERCENT
    }

    /**
     * Detecta si la secuencia de ticks recientes está alternando sin dirección clara (whipsaw / ruido lateral).
     * Evalúa cambios frecuentes de dirección (+/-) y ausencia de desplazamiento direccional sostenido.
     */
    fun isTickAlternatingWithoutDirection(recentPrices: List<Double>): Boolean {
        if (recentPrices.size < 4) return false
        var directionChanges = 0
        var prevDirection = 0 // +1: sube, -1: baja, 0: plano
        var upCount = 0
        var downCount = 0

        for (i in 1 until recentPrices.size) {
            val delta = recentPrices[i] - recentPrices[i - 1]
            val epsilon = recentPrices[i - 1] * 0.000002
            val currentDirection = when {
                delta > epsilon -> { upCount++; 1 }
                delta < -epsilon -> { downCount++; -1 }
                else -> 0
            }
            if (currentDirection != 0 && prevDirection != 0 && currentDirection != prevDirection) {
                directionChanges++
            }
            if (currentDirection != 0) {
                prevDirection = currentDirection
            }
        }

        val totalTransitions = recentPrices.size - 1
        val alternationRatio = if (totalTransitions > 0) directionChanges.toFloat() / totalTransitions else 0f
        val firstPrice = recentPrices.first()
        val netDisplacement = if (firstPrice > 0.0) Math.abs(recentPrices.last() - firstPrice) / firstPrice else 0.0

        // Alternancia de dirección (>= 35% de giros) y desplazamiento neto mínimo (< 0.03%)
        val hasAlternation = directionChanges >= 2 && alternationRatio >= 0.35f
        val hasNoClearDirection = Math.abs(upCount - downCount) <= 2 && netDisplacement < 0.0003

        return hasAlternation || (hasNoClearDirection && directionChanges >= 2)
    }

    /**
     * Filtro Anti-Choppy / Micro-Rango Cuantitativo:
     * Si el rango de las últimas velas es inferior al 0.05% Y los ticks alternan sin dirección clara.
     */
    fun isChoppyMicroRange(
        highPrices: List<Double>,
        lowPrices: List<Double>,
        refPrice: Double,
        recentTicks: List<Double>
    ): Boolean {
        val microRange = isMicroRange(highPrices, lowPrices, refPrice)
        val alternating = isTickAlternatingWithoutDirection(recentTicks)
        return microRange && alternating
    }
}

fun formatDynamicPrice(price: Double): String {
    if (price <= 0.0) return "0.00"
    val raw = String.format(java.util.Locale.US, "%.8f", price).trimEnd('0')
    val parts = raw.split(".")
    val decimals = if (parts.size > 1) parts[1].length else 0
    val targetDecimals = decimals.coerceIn(2, 6)
    return String.format(java.util.Locale.US, "%.${targetDecimals}f", price)
}
