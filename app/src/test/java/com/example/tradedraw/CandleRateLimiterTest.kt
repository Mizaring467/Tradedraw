package com.example.tradedraw

import com.example.tradedraw.core.AutoTradeMode
import com.example.tradedraw.execution.CandleRateLimiter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleRateLimiterTest {

    @Test
    fun flagOffPorDefectoShadowConsumeVelaReal() {
        val limiter = CandleRateLimiter(separateShadowRateLimit = false)
        val candleMin = 29000000L

        assertFalse(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))
        assertFalse(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.SHADOW))

        // SHADOW opera
        limiter.recordExecution(candleMin, AutoTradeMode.SHADOW)

        // Ambos quedan bloqueados en la misma vela
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.SHADOW))
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))

        // La siguiente vela no está bloqueada
        assertFalse(limiter.isCandleAlreadyExecuted(candleMin + 1, AutoTradeMode.AUTONOMOUS))
    }

    @Test
    fun flagOnSeparaShadowDeOrdenReal() {
        val limiter = CandleRateLimiter(separateShadowRateLimit = true)
        val candleMin = 29000000L

        // SHADOW opera
        limiter.recordExecution(candleMin, AutoTradeMode.SHADOW)

        // SHADOW queda bloqueado para esa vela
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.SHADOW))

        // Pero AUTONOMOUS NO queda bloqueado
        assertFalse(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))

        // Ahora AUTONOMOUS opera
        limiter.recordExecution(candleMin, AutoTradeMode.AUTONOMOUS)
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))
    }

    @Test
    fun resetLimpiaAmbosEstados() {
        val limiter = CandleRateLimiter(separateShadowRateLimit = true)
        val candleMin = 29000000L

        limiter.recordExecution(candleMin, AutoTradeMode.SHADOW)
        limiter.recordExecution(candleMin, AutoTradeMode.AUTONOMOUS)
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.SHADOW))
        assertTrue(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))

        limiter.reset()
        assertFalse(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.SHADOW))
        assertFalse(limiter.isCandleAlreadyExecuted(candleMin, AutoTradeMode.AUTONOMOUS))
    }
}
