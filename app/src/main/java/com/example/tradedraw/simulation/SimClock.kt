package com.example.tradedraw.simulation

/**
 * Puerto de reloj para simulaciones de mercado y backtests.
 */
interface SimClock {
    fun nowMs(): Long
    fun set(nowMs: Long)
    fun advance(deltaMs: Long)
}

/**
 * Implementación manual de SimClock para tests y reproducción paso a paso.
 */
class ManualSimClock(
    @Volatile private var currentTimeMs: Long = 0L
) : SimClock {
    override fun nowMs(): Long = currentTimeMs

    override fun set(nowMs: Long) {
        currentTimeMs = nowMs
    }

    override fun advance(deltaMs: Long) {
        currentTimeMs += deltaMs
    }
}

/**
 * Implementación de SimClock basada en el reloj del sistema.
 */
class SystemSimClock : SimClock {
    @Volatile private var offsetMs: Long = 0L

    override fun nowMs(): Long = System.currentTimeMillis() + offsetMs

    override fun set(nowMs: Long) {
        offsetMs = nowMs - System.currentTimeMillis()
    }

    override fun advance(deltaMs: Long) {
        offsetMs += deltaMs
    }
}
