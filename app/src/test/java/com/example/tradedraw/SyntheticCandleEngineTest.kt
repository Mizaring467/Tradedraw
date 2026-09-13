package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class SyntheticCandleEngineTest {

    @Test
    fun testConsecutiveTicksCounting() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 5L

        var price = 1000.0
        // Tick base inicial
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now))

        // Enviar 6 ticks ascendentes consecutivos
        for (i in 1..6) {
            price += 0.5
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }

        assertEquals("Debe contar 6 ticks consecutivos alcistas", 6, engine.consecutiveUpTicks)
        assertEquals("Ticks bajistas deben ser 0", 0, engine.consecutiveDownTicks)

        // Enviar 1 tick hacia abajo
        price -= 0.5
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + 7000L))

        assertEquals("Ticks alcistas activos deben resetearse a 0 tras giro", 0, engine.consecutiveUpTicks)
        assertEquals("Debe registrar la racha previa completada", 6, engine.lastCompletedUpStreak)
        assertEquals("Debe contar 1 tick bajista", 1, engine.consecutiveDownTicks)
    }

    @Test
    fun testSyntheticTickRsiCalculation() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 5L

        // Inicial: sin ticks suficientes, debe retornar neutro (50.0)
        assertEquals(50.0, engine.syntheticTickRsi, 0.01)

        // 15 ticks alcistas continuos -> RSI debe ser 100.0
        var price = 500.0
        for (i in 0..15) {
            price += 1.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }
        assertEquals("15 ticks alcistas deben dar RSI 100.0", 100.0, engine.syntheticTickRsi, 0.01)

        // 15 ticks bajistas continuos -> RSI debe colapsar a 0.0
        for (i in 16..31) {
            price -= 2.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }
        assertEquals("15 ticks bajistas deben dar RSI 0.0", 0.0, engine.syntheticTickRsi, 0.01)
    }

    @Test
    fun testOverextensionDetectionFlags() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L

        // Crear velas base para fijar S/R dinámico entre 100.0 y 200.0
        for (m in 0 until 5) {
            val baseTime = (now - (5 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0 + (m * 20.0), timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 200.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime + 59000L))
        }

        // Ticks alcistas empujando contra el techo 200.0
        var p = 185.0
        for (i in 0..10) {
            p += 1.2
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (i * 1000L)))
        }

        assertTrue("Debe detectar proximidad a la resistencia (dist <= 22%)", engine.distanceToResistanceRatio <= 0.22f)
        assertTrue("Debe marcar sobreextensión alcista", engine.isBullishOverextended)
        assertFalse("No debe marcar sobreextensión bajista", engine.isBearishOverextended)
    }

    @Test
    fun testSniperOpportunityTriggersReversalAtResistance() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L

        // Inicializar velas base para establecer soporte (100.0) y resistencia (200.0)
        for (m in 0 until 4) {
            val baseTime = (now - (4 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 200.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime + 58000L))
        }

        var lastSignalAction: TradeAction? = null
        var lastSignalReason: String = ""
        engine.onSignalGenerated = { action, reason ->
            lastSignalAction = action
            lastSignalReason = reason
        }

        // Subir con racha alcista de ticks hasta 198.0 (pegado a la resistencia de 200.0)
        var p = 180.0
        for (i in 2..15) {
            p += 1.2
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (i * 1000L)))
        }

        // En el segundo 59 (:59s), llega un micro-giro bajista contra la resistencia
        val sniperTime = now + 59000L
        val tickTurn = MarketTick(
            asset = "CRYPTO_IDX",
            price = p - 0.5,
            timestampMs = sniperTime,
            velocity = -0.05f,
            isBearishImpulse = true
        )

        engine.onNewTick(tickTurn)

        assertEquals("Debe gatillar reversión bajista PUT en resistencia sobreextendida", TradeAction.SELL, lastSignalAction)
        assertTrue("Razón debe contener mención a Reversión Anti-Sobreextensión", lastSignalReason.contains("Reversión Anti-Sobreextensión en Resistencia"))
    }

    @Test
    fun testSniperOpportunityBlocksContinuationWhenOverextended() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L

        // Inicializar velas base para establecer niveles
        for (m in 0 until 4) {
            val baseTime = (now - (4 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 200.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime + 58000L))
        }

        var lastSignalAction: TradeAction? = null
        engine.onSignalGenerated = { action, _ ->
            lastSignalAction = action
        }

        // Ticks alcistas masivos hasta 199.0
        var p = 175.0
        for (i in 2..16) {
            p += 1.5
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + (i * 1000L)))
        }

        // Limpiar señales previas emitidas durante el flujo
        lastSignalAction = null

        // En el segundo 00 (:00s), tick sigue empujando al alza pero ya está sobreextendido en la resistencia
        val sniperTime = now + 60000L
        val tickContinuation = MarketTick(
            asset = "CRYPTO_IDX",
            price = p + 0.5,
            timestampMs = sniperTime,
            velocity = 0.05f,
            isBullishImpulse = true
        )

        engine.onNewTick(tickContinuation)

        // NO debe comprar en el techo (la continuación alcista sobreextendida debe ser vetada)
        assertNull("NO debe emitir BUY de continuación cuando el mercado está sobreextendido en el techo", lastSignalAction)
    }

    @Test
    fun testSyntheticEngineChoppinessAndStrictTiming() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 15L

        // 1. Simular velas en micro-rango (< 0.05% de variación total)
        for (m in 0 until 5) {
            val baseTime = (now - (5 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.00, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.12, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 499.98, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.05, timestampMs = baseTime + 59000L))
        }

        // Ticks alternando sin dirección
        val ticks = listOf(500.05, 500.10, 500.04, 500.11, 500.05, 500.09, 500.04, 500.10)
        for ((i, price) in ticks.withIndex()) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }

        assertTrue("Rango relativo debe ser < 0.05%", engine.isMicroRange(5, 0.05))
        assertTrue("Ticks deben detectarse como alternantes", engine.isTickAlternatingWithoutDirection(8))
        assertTrue("Choppiness debe estar activo", engine.isChoppinessDetected())

        var signalEmitted = false
        engine.onSignalGenerated = { _, _ -> signalEmitted = true }

        // Enviar tick en segundo 00 (:00s) durante choppiness
        val tickSniper = MarketTick(
            asset = "CRYPTO_IDX",
            price = 500.08,
            timestampMs = now + 60000L, // segundo 00
            isBullishImpulse = true
        )
        engine.onNewTick(tickSniper)

        assertFalse("Señal debe ser suprimida por choppiness", signalEmitted)
    }
}
