package com.example.tradedraw.execution

import com.example.tradedraw.core.AutoTradeMode

/**
 * Controla el límite de frecuencia de operaciones por vela (1 orden por vela máx).
 * En comportamiento estándar (flag off), tanto órdenes reales como SHADOW consumen la vela.
 * Con separateShadowRateLimit = true (D-7), las decisiones de SHADOW no bloquean la vela para órdenes reales.
 */
class CandleRateLimiter(
    @Volatile var separateShadowRateLimit: Boolean = false
) {
    @Volatile
    var lastExecutedCandleEpochMinute: Long = -1L

    @Volatile
    var lastShadowCandleEpochMinute: Long = -1L

    /**
     * Retorna true si la vela actual ya fue operada para el modo dado.
     */
    fun isCandleAlreadyExecuted(currentCandleEpochMinute: Long, mode: AutoTradeMode): Boolean {
        synchronized(this) {
            return if (separateShadowRateLimit && mode == AutoTradeMode.SHADOW) {
                lastShadowCandleEpochMinute == currentCandleEpochMinute
            } else {
                lastExecutedCandleEpochMinute == currentCandleEpochMinute
            }
        }
    }

    /**
     * Registra la ejecución de una orden en la vela actual.
     */
    fun recordExecution(currentCandleEpochMinute: Long, mode: AutoTradeMode) {
        synchronized(this) {
            if (separateShadowRateLimit && mode == AutoTradeMode.SHADOW) {
                lastShadowCandleEpochMinute = currentCandleEpochMinute
            } else {
                lastExecutedCandleEpochMinute = currentCandleEpochMinute
            }
        }
    }

    /**
     * Resetea el estado del limitador.
     */
    fun reset() {
        synchronized(this) {
            lastExecutedCandleEpochMinute = -1L
            lastShadowCandleEpochMinute = -1L
        }
    }
}
