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
    val isBearishImpulse: Boolean = false
)

enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
