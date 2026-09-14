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

        // Inicializar 3 velas base cerradas para establecer soporte (100.0) y resistencia (200.0)
        for (m in 0 until 3) {
            val baseTime = (now - (4 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 200.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime + 58000L))
        }

        // Vela previa (minuto now - 60000L) que intenta testear la resistencia (200.0) y sufre fuerte rechazo institucional
        val prevCandleBase = now - 60000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 180.0, timestampMs = prevCandleBase)) // Open: 180
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 200.0, timestampMs = prevCandleBase + 20000L)) // High: 200
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 178.0, timestampMs = prevCandleBase + 40000L)) // Low: 178
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 184.0, timestampMs = prevCandleBase + 59000L)) // Close: 184 -> UpperWick = 16 / 22 = 72%

        var lastSignalAction: TradeAction? = null
        var lastSignalReason: String = ""
        engine.onSignalGenerated = { action, reason ->
            lastSignalAction = action
            lastSignalReason = reason
        }

        // En la vela actual, ticks bajistas confirmando rechazo cerca de la resistencia en segundo 59 (:59s)
        val sniperTime = now + 59000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 185.0, timestampMs = sniperTime - 2000L))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 184.0, timestampMs = sniperTime - 1000L))
        val tickTurn = MarketTick(
            asset = "CRYPTO_IDX",
            price = 183.0,
            timestampMs = sniperTime,
            velocity = -0.05f,
            isBearishImpulse = true
        )

        engine.onNewTick(tickTurn)

        assertEquals("Debe gatillar reversión bajista PUT en rechazo de resistencia", TradeAction.SELL, lastSignalAction)
        assertTrue("Razón debe contener mención a MT_REJECTION", lastSignalReason.contains("MT_REJECTION"))
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

    @Test
    fun testIndependentDistanceRatios() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 15L
        
        var price = 100.0
        for (m in 0 until 15) {
            val baseTime = (now - (15 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price + 10.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price + 5.0, timestampMs = baseTime + 59000L))
            price += 1.0
        }
        
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price + 30.0, timestampMs = now + 1000L))
        
        val distS1 = engine.distanceToSupportRatio
        val distR1 = engine.distanceToResistanceRatio
        assertTrue("distS y distR deben ser > 0", distS1 >= 0.0f && distR1 >= 0.0f)
        assertTrue("distS y distR NO deben sumar 1", Math.abs((distS1 + distR1) - 1.0f) > 0.1f)
    }

    @Test
    fun testPivotDetectionAndBrokenSupport() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 30L
        
        for (m in 0 until 20) {
            val baseTime = (now - (20 - m) * 60000L)
            val isBounce = m % 4 == 0
            val lowPrice = if (isBounce) 100.0 else 110.0 + m
            val highPrice = 130.0
            
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = (highPrice + lowPrice)/2, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = highPrice, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = lowPrice, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = (highPrice + lowPrice)/2, timestampMs = baseTime + 59000L))
        }
        
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 90.0, timestampMs = now + 1000L))
        
        assertTrue("El precio dinámico de soporte debe ser válido", engine.dynamicSupportPrice > 0)
    }

    @Test
    fun testChoppinessInYoloMode() {
        val engine = SyntheticCandleEngine()
        engine.subMode = AutonomousSubMode.YOLO
        val now = 60000L * 15L

        for (m in 0 until 5) {
            val baseTime = (now - (5 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.00, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.12, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 499.98, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.05, timestampMs = baseTime + 59000L))
        }

        val ticks = listOf(500.05, 500.10, 500.04, 500.11, 500.05, 500.09, 500.04, 500.10, 500.05, 500.11, 500.04)
        for ((i, price) in ticks.withIndex()) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }

        assertTrue("Choppiness debe estar activo incluso en YOLO si hay micro-rango y alternancia", engine.isChoppinessDetected())
    }

    @Test
    fun testSmoothUptrendDetectionLowVolatility() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 641.860

        // Simular 9 velas de baja volatilidad (Crypto IDX) con subida suave < 0.01% total (0.005%)
        for (m in 0 until 9) {
            val baseTime = now + (m * 60000L)
            val open = price
            val high = price + 0.005
            val low = price - 0.001
            val close = price + 0.004
            
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = open, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = high, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = low, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = close, timestampMs = baseTime + 59000L))
            price = close
        }

        // Movimiento total: de 641.860 a ~641.896 (~0.0056% en 9 min)
        val totalPctMove = (price - 641.860) / 641.860 * 100.0
        assertTrue("El movimiento total debe ser < 0.01% para probar tendencia suave ($totalPctMove%)", totalPctMove < 0.01)
        assertNull("visionTrend debe ser null en headless", engine.visionTrend)
        assertEquals("Debe detectar UPTREND autónomo incluso con subida suave < 0.01%", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testAutonomousTrendWithoutVisionNull() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 100.0

        for (m in 0 until 8) {
            val baseTime = now + (m * 60000L)
            engine.onNewTick(MarketTick(asset = "BTCUSDT", price = price, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "BTCUSDT", price = price + 2.0, timestampMs = baseTime + 30000L))
            engine.onNewTick(MarketTick(asset = "BTCUSDT", price = price + 1.5, timestampMs = baseTime + 59000L))
            price += 1.5
        }

        assertNull("visionTrend debe ser null permanentemente", engine.visionTrend)
        assertEquals("Debe determinar UPTREND sin depender de visionTrend", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testGenuineSidewaysDetection() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        val basePrice = 641.860

        // Velas oscilando en rango horizontal puro sin pendiente
        for (m in 0 until 9) {
            val baseTime = now + (m * 60000L)
            val isEven = m % 2 == 0
            val open = if (isEven) basePrice - 0.003 else basePrice + 0.003
            val high = basePrice + 0.006
            val low = basePrice - 0.006
            val close = if (isEven) basePrice + 0.002 else basePrice - 0.002

            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = open, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = high, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = low, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = close, timestampMs = baseTime + 59000L))
        }

        assertEquals("Serie sin pendiente debe ser SIDEWAYS", TrendDirection.SIDEWAYS, engine.detectedTrend)
    }

    @Test
    fun testSmoothDowntrendDetection() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 641.890

        for (m in 0 until 9) {
            val baseTime = now + (m * 60000L)
            val open = price
            val high = price + 0.001
            val low = price - 0.005
            val close = price - 0.004

            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = open, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = high, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = low, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = close, timestampMs = baseTime + 59000L))
            price = close
        }

        assertEquals("Debe detectar DOWNTREND con caída suave", TrendDirection.DOWNTREND, engine.detectedTrend)
    }

    @Test
    fun testScaleInvarianceOfNormalizedTrend() {
        val engineBase = SyntheticCandleEngine()
        val engineScaled = SyntheticCandleEngine()
        val now = 60000L * 10L

        var pBase = 641.860
        var pScaled = 6418.60 // Escala x10

        for (m in 0 until 9) {
            val baseTime = now + (m * 60000L)
            
            // Base
            engineBase.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = pBase, timestampMs = baseTime))
            engineBase.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = pBase + 0.005, timestampMs = baseTime + 30000L))
            engineBase.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = pBase + 0.004, timestampMs = baseTime + 59000L))
            pBase += 0.004

            // Scaled x10
            engineScaled.onNewTick(MarketTick(asset = "SCALED_IDX", price = pScaled, timestampMs = baseTime))
            engineScaled.onNewTick(MarketTick(asset = "SCALED_IDX", price = pScaled + 0.050, timestampMs = baseTime + 30000L))
            engineScaled.onNewTick(MarketTick(asset = "SCALED_IDX", price = pScaled + 0.040, timestampMs = baseTime + 59000L))
            pScaled += 0.040
        }

        assertEquals("Base debe ser UPTREND", TrendDirection.UPTREND, engineBase.detectedTrend)
        assertEquals("Scaled x10 debe ser exactamente igual UPTREND", TrendDirection.UPTREND, engineScaled.detectedTrend)
        assertEquals("Normalized slope debe ser idéntico independientemente de escala",
            engineBase.calculateNormalizedTrendSlope(9),
            engineScaled.calculateNormalizedTrendSlope(9),
            0.05
        )
    }

    @Test
    fun testJournalReplayTrendChanges() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 100L
        var price = 640.0
        val trendHistory = mutableListOf<TrendDirection>()

        // 1. Fase Lateral (10 velas)
        for (m in 0 until 10) {
            val isEven = m % 2 == 0
            val p = if (isEven) 640.05 else 639.95
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p + 0.05, timestampMs = now + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = p, timestampMs = now + 59000L))
            trendHistory.add(engine.detectedTrend)
            now += 60000L
        }

        // 2. Fase Alcista (10 velas)
        for (m in 0 until 10) {
            price += 0.20
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price + 0.15, timestampMs = now + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price + 0.10, timestampMs = now + 59000L))
            trendHistory.add(engine.detectedTrend)
            now += 60000L
        }

        // 3. Fase Bajista (10 velas)
        for (m in 0 until 10) {
            price -= 0.30
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price - 0.10, timestampMs = now + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price - 0.20, timestampMs = now + 59000L))
            trendHistory.add(engine.detectedTrend)
            now += 60000L
        }

        val uniqueTrends = trendHistory.distinct()
        assertTrue("detectedTrend DEBE cambiar y contener UPTREND, DOWNTREND y SIDEWAYS durante replay",
            uniqueTrends.contains(TrendDirection.UPTREND) &&
            uniqueTrends.contains(TrendDirection.DOWNTREND) &&
            uniqueTrends.contains(TrendDirection.SIDEWAYS)
        )
        val trendTransitions = trendHistory.zipWithNext().count { (a, b) -> a != b }
        assertTrue("Debe registrar al menos 2 transiciones de régimen de mercado ($trendTransitions transiciones)", trendTransitions >= 2)
    }
}
