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

    @Test
    fun testAthBreakoutPolarity() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 20L

        // Crear 10 velas en rango 100.0 - 150.0 para fijar pivotes previos
        for (m in 0 until 10) {
            val high = 150.0
            val low = 100.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = (high + low) / 2, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = high, timestampMs = now + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = low, timestampMs = now + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 125.0, timestampMs = now + 59000L))
            now += 60000L
        }

        // Simular breakout a un nuevo ATH en 250.0 (muy por encima de 150.0)
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 250.0, timestampMs = now + 1000L))

        assertTrue("dynamicResistancePrice debe estar SIEMPRE por encima o igual al precio actual en ATH",
            engine.dynamicResistancePrice >= 250.0
        )
        assertEquals("dynamicSupportPrice debe convertirse en el pivote roto (150.0) por polaridad",
            150.0, engine.dynamicSupportPrice, 0.1
        )
        assertTrue("distanceToSupportRatio NO debe ser 0 ni cercano a 0 cuando el precio está 100 unidades sobre el soporte",
            engine.distanceToSupportRatio > 0.40f
        )
    }

    @Test
    fun testUniversalAntiExhaustionBlocksBuyOnThreeConsecutiveGreenCandles() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 30L

        // 1. Simular 5 velas base normales
        for (m in 0 until 5) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 105.0, timestampMs = now + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 102.0, timestampMs = now + 59000L))
            now += 60000L
        }

        // 2. Simular 3 velas VERDES consecutivas y sólidas (Agotamiento alcista)
        for (m in 0 until 3) {
            val openP = 102.0 + (m * 10.0)
            val closeP = openP + 8.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = openP, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = closeP + 1.0, timestampMs = now + 30000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = closeP, timestampMs = now + 59000L))
            now += 60000L
        }

        var signalEmittedAction: TradeAction? = null
        var signalEmittedReason: String = ""
        engine.onSignalGenerated = { action, reason ->
            signalEmittedAction = action
            signalEmittedReason = reason
        }

        // 3. Enviar tick alcista en segundo 00 (:00s) al inicio de la 4ª vela
        val tickSniper = MarketTick(
            asset = "CRYPTO_IDX",
            price = 132.0,
            timestampMs = now, // segundo :00
            velocity = 0.08f,
            isBullishImpulse = true
        )
        engine.onNewTick(tickSniper)

        // NO debe emitir orden BUY de continuación en el techo tras 3 velas verdes consecutivas
        assertNotEquals("PROHIBIDO comprar (CALL) tras racha de 3 velas verdes consecutivas (Agotamiento)",
            TradeAction.BUY, signalEmittedAction
        )
    }

    @Test
    fun testChoppinessIndex_IdentifiesConsolidationAndDojiNoise() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 30L

        // 1. Simular 12 velas doji / micro-cuerpo que se solapan completamente en un rango estrecho
        for (i in 0 until 12) {
            val baseP = 600.0 + if (i % 2 == 0) 0.05 else -0.05
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = baseP, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = baseP + 0.50, timestampMs = now + 20000L)) // High
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = baseP - 0.50, timestampMs = now + 40000L)) // Low
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = baseP + 0.01, timestampMs = now + 59000L)) // Close doji
            now += 60000L
        }
        // Tick en nuevo minuto para cerrar la vela 11
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 600.0, timestampMs = now))

        val chop = engine.calculateChoppinessIndex(10)
        assertTrue("El índice de Choppiness debe ser alto (>= 58.0) en mercado de dojis solapados: $chop", chop >= 58.0)
        assertTrue("isChoppinessDetected() debe retornar true en consolidación de dojis", engine.isChoppinessDetected())
    }

    @Test
    fun testRangeBounce_RejectsNarrowCompressedChannels() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 25L

        // 1. Crear velas con canal muy comprimido
        for (i in 0 until 8) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.05, timestampMs = now + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 99.95, timestampMs = now + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.01, timestampMs = now + 59000L))
            now += 60000L
        }

        var signalEmitted: TradeAction? = null
        engine.onSignalGenerated = { action, _ -> signalEmitted = action }

        // Tick en segundo :00 intentando rebotar en canal comprimido
        val tick = MarketTick(
            asset = "CRYPTO_IDX",
            price = 99.96,
            timestampMs = now,
            isBullishImpulse = true,
            velocity = 0.02f
        )
        engine.onNewTick(tick)

        assertNull("No debe disparar rebote en un canal comprimido no operable", signalEmitted)
    }

    @Test
    fun testRejectionAtSupport_ExecutesEvenWithElevatedChoppiness() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 20L

        // 1. Simular 10 velas en rango lateral amplio para fijar soporte en 100.0 y resistencia en 150.0
        for (m in 0 until 10) {
            val isEven = m % 2 == 0
            val openP = if (isEven) 105.0 else 140.0
            val closeP = if (isEven) 135.0 else 110.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = openP, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = now + 20000L)) // High
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now + 40000L)) // Low
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = closeP, timestampMs = now + 59000L))
            now += 60000L
        }

        // 2. Vela previa testeando soporte 100.0 con fuerte rechazo institucional (mecha inferior 60%)
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 108.0, timestampMs = now)) // Open: 108
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 110.0, timestampMs = now + 20000L)) // High: 110
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now + 40000L)) // Low: 100
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 106.0, timestampMs = now + 59000L)) // Close: 106 -> Lower wick: 6 / 10 = 60%
        now += 60000L

        var signalEmitted: TradeAction? = null
        var signalReason = ""
        engine.onSignalGenerated = { action, reason ->
            signalEmitted = action
            signalReason = reason
        }

        // 3. Tick en ventana sniper (:59s) confirmando rebote alcista en soporte
        val sniperTime = now + 59000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 104.0, timestampMs = sniperTime - 2000L))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 104.5, timestampMs = sniperTime - 1000L))
        val sniperTick = MarketTick(
            asset = "CRYPTO_IDX",
            price = 105.0,
            timestampMs = sniperTime,
            velocity = 0.05f,
            isBullishImpulse = true
        )
        engine.onNewTick(sniperTick)

        assertEquals("Debe emitir orden BUY (CALL) por MT_REJECTION en soporte a pesar del contexto de rango",
            TradeAction.BUY, signalEmitted)
        assertTrue("Razón debe ser MT_REJECTION", signalReason.contains("MT_REJECTION"))
    }

    @Test
    fun testYoloMode_AllowsPermissiveSniperSetups() {
        val engine = SyntheticCandleEngine()
        engine.subMode = AutonomousSubMode.YOLO
        var now = 60000L * 20L

        // 1. Simular velas base para fijar soporte en 100.0 y resistencia en 150.0
        for (m in 0 until 5) {
            val baseTime = now - (6 - m) * 60000L
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime + 25000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 120.0, timestampMs = baseTime + 59000L))
        }

        // 2. Vela previa con mecha del 35% y distancia al soporte del 24%
        // (En modo conservador se descartaría por wick < 38% o dist > 22%, pero en YOLO es válida)
        val prevBase = now - 60000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 108.0, timestampMs = prevBase)) // Open
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 120.0, timestampMs = prevBase + 20000L)) // High: 120
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 101.0, timestampMs = prevBase + 40000L)) // Low: 101
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 107.65, timestampMs = prevBase + 59000L)) // Close: 107.65 -> Wick lower: 6.65 / 19 = 35%

        var signalEmitted: TradeAction? = null
        engine.onSignalGenerated = { action, _ -> signalEmitted = action }

        // 3. Tick en segundo :01s confirmando giro alcista
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 107.0, timestampMs = now))
        val tickSniper = MarketTick(
            asset = "CRYPTO_IDX",
            price = 108.0,
            timestampMs = now + 1000L, // segundo :01
            velocity = 0.04f,
            isBullishImpulse = true
        )
        engine.onNewTick(tickSniper)

        assertEquals("En modo YOLO debe aceptar rechazos con mecha >= 32% y dist <= 26%",
            TradeAction.BUY, signalEmitted)
    }

    @Test
    fun testExtremeMicroRange_BlocksAllSignalsEvenInYolo() {
        val engine = SyntheticCandleEngine()
        engine.subMode = AutonomousSubMode.YOLO
        val now = 60000L * 25L

        // Micro-rango absoluto (< 0.05%) con alternancia errática de ticks
        for (m in 0 until 5) {
            val baseTime = (now - (5 - m) * 60000L)
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.00, timestampMs = baseTime))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.05, timestampMs = baseTime + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 499.98, timestampMs = baseTime + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 500.02, timestampMs = baseTime + 59000L))
        }

        val ticks = listOf(500.02, 500.05, 500.01, 500.06, 500.02, 500.05, 500.01, 500.06, 500.02, 500.05, 500.01)
        for ((i, price) in ticks.withIndex()) {
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = price, timestampMs = now + (i * 1000L)))
        }

        var signalEmitted: TradeAction? = null
        engine.onSignalGenerated = { action, _ -> signalEmitted = action }

        // Tick en segundo 00 (:00s)
        val tick = MarketTick(
            asset = "CRYPTO_IDX",
            price = 500.04,
            timestampMs = now + 60000L,
            isBullishImpulse = true
        )
        engine.onNewTick(tick)

        assertNull("En micro-rango estático sin spread debe bloquear 100% de señales incluso en YOLO", signalEmitted)
    }

    @Test
    fun testConfirmBounceSignal_TriggersCallOnSecondCandleAfterSupportRejection() {
        val engine = SyntheticCandleEngine()
        engine.subMode = AutonomousSubMode.YOLO
        val now = 60000L * 30L

        // 1. Establecer canal S/R: soporte en 100.0 y resistencia pivote alta en 200.0 con velas rojas bajistas
        for (m in 0 until 4) {
            val baseTime = (now - (5 - m) * 60000L)
            val highP = if (m == 2) 200.0 else 160.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = baseTime)) // Open 150
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = highP, timestampMs = baseTime + 20000L)) // High
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = baseTime + 40000L)) // Low 100
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 105.0, timestampMs = baseTime + 59000L)) // Close 105 (roja)
        }

        // 2. Vela previa (hace 1 minuto): cerró VERDE rebotando en el soporte con mecha del 22% (< 30% de Rejection)
        val prevBase = now - 60000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 104.0, timestampMs = prevBase)) // Open: 104
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 101.0, timestampMs = prevBase + 15000L)) // Low: 101 (soporte)
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 115.0, timestampMs = prevBase + 45000L)) // High: 115
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 114.0, timestampMs = prevBase + 59000L)) // Close: 114 (verde, mecha lower: 3/14 = 21.4%)

        var signalEmitted: TradeAction? = null
        var signalReason: String? = null
        engine.onSignalGenerated = { action, reason ->
            signalEmitted = action
            signalReason = reason
        }

        // 3. Tick sniper en segundo :01s confirmando despegue alcista (segunda vela de giro)
        val tick = MarketTick(
            asset = "CRYPTO_IDX",
            price = 114.5,
            timestampMs = now + 1000L, // segundo :01
            velocity = 0.05f,
            isBullishImpulse = true,
            smoothedVelocity = 0.05f
        )
        engine.onNewTick(tick)

        assertEquals("Debe emitir señal de COMPRA (CALL) en segunda vela de rebote confirmado", TradeAction.BUY, signalEmitted)
        assertTrue("El motivo debe indicar MT_CONFIRM_BOUNCE", signalReason?.contains("MT_CONFIRM_BOUNCE") == true)
    }

    @Test
    fun testRealisticUptrendWithPullbacksDetectedAsUptrend() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 10L
        var price = 100.0

        // Secuencia realista de 8 velas: 6 verdes y 2 rojas (pullbacks), con mínimos y máximos crecientes
        val deltas = listOf(
            Pair(1.0, 0.8),   // Vela 1 verde: range 1.0, close +0.8
            Pair(1.2, 0.9),   // Vela 2 verde: range 1.2, close +0.9
            Pair(0.8, -0.3),  // Vela 3 roja (pullback): range 0.8, close -0.3
            Pair(1.5, 1.1),   // Vela 4 verde: range 1.5, close +1.1
            Pair(1.1, 0.8),   // Vela 5 verde: range 1.1, close +0.8
            Pair(0.7, -0.2),  // Vela 6 roja (pullback): range 0.7, close -0.2
            Pair(1.3, 1.0),   // Vela 7 verde: range 1.3, close +1.0
            Pair(1.0, 0.7)    // Vela 8 verde: range 1.0, close +0.7
        )

        for ((range, closeDelta) in deltas) {
            val open = price
            val high = open + range * 0.7
            val low = open - range * 0.3
            val close = open + closeDelta
            engine.onNewTick(MarketTick("CRYPTO_IDX", open, now))
            engine.onNewTick(MarketTick("CRYPTO_IDX", high, now + 20000L))
            engine.onNewTick(MarketTick("CRYPTO_IDX", low, now + 40000L))
            engine.onNewTick(MarketTick("CRYPTO_IDX", close, now + 59000L))
            price = close
            now += 60000L
        }

        assertEquals("Tendencia alcista realista con pullbacks debe clasificarse como UPTREND", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testRealisticDowntrendWithPullbacksDetectedAsDowntrend() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 10L
        var price = 200.0

        // Secuencia realista de 8 velas bajistas con pullbacks alcistas
        val deltas = listOf(
            Pair(1.0, -0.8),
            Pair(1.2, -0.9),
            Pair(0.8, 0.3),  // pullback alcista
            Pair(1.5, -1.1),
            Pair(1.1, -0.8),
            Pair(0.7, 0.2),  // pullback alcista
            Pair(1.3, -1.0),
            Pair(1.0, -0.7)
        )

        for ((range, closeDelta) in deltas) {
            val open = price
            val high = open + range * 0.3
            val low = open - range * 0.7
            val close = open + closeDelta
            engine.onNewTick(MarketTick("CRYPTO_IDX", open, now))
            engine.onNewTick(MarketTick("CRYPTO_IDX", high, now + 20000L))
            engine.onNewTick(MarketTick("CRYPTO_IDX", low, now + 40000L))
            engine.onNewTick(MarketTick("CRYPTO_IDX", close, now + 59000L))
            price = close
            now += 60000L
        }

        assertEquals("Tendencia bajista realista con pullbacks debe clasificarse como DOWNTREND", TrendDirection.DOWNTREND, engine.detectedTrend)
    }

    @Test
    fun testEarlyTickMomentumUptrendWithoutClosedCandles() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 500.0

        // Inyectar 15 ticks ascendentes consecutivos al inicio de la sesión
        for (i in 0 until 15) {
            price += 0.4
            engine.onNewTick(MarketTick("CRYPTO_IDX", price, now + (i * 1000L)))
        }

        assertEquals("Ticks alcistas tempranos deben detectar UPTREND sin requerir 3 velas cerradas", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testEarlyTickMomentumDowntrendWithoutClosedCandles() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 500.0

        // Inyectar 15 ticks descendentes consecutivos al inicio de la sesión
        for (i in 0 until 15) {
            price -= 0.4
            engine.onNewTick(MarketTick("CRYPTO_IDX", price, now + (i * 1000L)))
        }

        assertEquals("Ticks bajistas tempranos deben detectar DOWNTREND sin requerir 3 velas cerradas", TrendDirection.DOWNTREND, engine.detectedTrend)
    }

    @Test
    fun testCryptoIdxMicroPrecisionUptrendDetected() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 10L
        var price = 641.8673950000

        // 6 velas ascendentes con micro-deltas realistas de Crypto IDX (variaciones en 5ª y 6ª decimal)
        for (i in 0 until 6) {
            val open = price
            val high = open + 0.000005
            val low = open - 0.000001
            val close = open + 0.000003
            engine.onNewTick(MarketTick("Z-CRY/IDX", open, now))
            engine.onNewTick(MarketTick("Z-CRY/IDX", high, now + 20000L))
            engine.onNewTick(MarketTick("Z-CRY/IDX", low, now + 40000L))
            engine.onNewTick(MarketTick("Z-CRY/IDX", close, now + 59000L))
            price = close
            now += 60000L
        }

        assertEquals("Activo de alta precisión como Crypto IDX debe clasificar subida sostenida como UPTREND", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testCryptoIdxMicroPrecisionDowntrendDetected() {
        val engine = SyntheticCandleEngine()
        var now = 60000L * 10L
        var price = 641.8674030000

        // 6 velas descendentes con micro-deltas realistas de Crypto IDX
        for (i in 0 until 6) {
            val open = price
            val high = open + 0.000001
            val low = open - 0.000005
            val close = open - 0.000003
            engine.onNewTick(MarketTick("Z-CRY/IDX", open, now))
            engine.onNewTick(MarketTick("Z-CRY/IDX", high, now + 20000L))
            engine.onNewTick(MarketTick("Z-CRY/IDX", low, now + 40000L))
            engine.onNewTick(MarketTick("Z-CRY/IDX", close, now + 59000L))
            price = close
            now += 60000L
        }

        assertEquals("Activo de alta precisión como Crypto IDX debe clasificar caída sostenida como DOWNTREND", TrendDirection.DOWNTREND, engine.detectedTrend)
    }

    @Test
    fun testCryptoIdxEarlyTicksUptrendDetected() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 641.8673950000

        // 12 ticks tempranos con tendencia ascendente y micro-oscilación
        for (i in 0 until 12) {
            val step = if (i % 3 == 0) -0.0000005 else +0.0000015
            price += step
            engine.onNewTick(MarketTick("Z-CRY/IDX", price, now + (i * 1000L)))
        }

        assertEquals("Ticks tempranos de Crypto IDX con avance neto deben detectar UPTREND", TrendDirection.UPTREND, engine.detectedTrend)
    }

    @Test
    fun testCryptoIdxEarlyTicksDowntrendDetected() {
        val engine = SyntheticCandleEngine()
        val now = 60000L * 10L
        var price = 641.8674030000

        // 12 ticks tempranos con tendencia descendente y micro-oscilación
        for (i in 0 until 12) {
            val step = if (i % 3 == 0) +0.0000005 else -0.0000015
            price += step
            engine.onNewTick(MarketTick("Z-CRY/IDX", price, now + (i * 1000L)))
        }

        assertEquals("Ticks tempranos de Crypto IDX con retroceso neto deben detectar DOWNTREND", TrendDirection.DOWNTREND, engine.detectedTrend)
    }

    @Test
    fun testSupportResistanceStrategy_ExecutesMtRejectionAtSupport() {
        val engine = SyntheticCandleEngine()
        engine.currentStrategy = AutoTradeStrategy.SUPPORT_RESISTANCE
        var now = 60000L * 20L

        // 1. Simular velas para establecer soporte en 100.0 y resistencia en 150.0
        for (m in 0 until 10) {
            val isEven = m % 2 == 0
            val openP = if (isEven) 110.0 else 140.0
            val closeP = if (isEven) 135.0 else 115.0
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = openP, timestampMs = now))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 150.0, timestampMs = now + 20000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now + 40000L))
            engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = closeP, timestampMs = now + 59000L))
            now += 60000L
        }

        // 2. Vela previa testeando soporte con mecha de rechazo inferior (60%)
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 108.0, timestampMs = now))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 110.0, timestampMs = now + 20000L))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 100.0, timestampMs = now + 40000L))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 106.0, timestampMs = now + 59000L))
        now += 60000L

        var signalEmitted: TradeAction? = null
        var signalReason = ""
        engine.onSignalGenerated = { action, reason ->
            signalEmitted = action
            signalReason = reason
        }

        // 3. Tick en ventana sniper :59s confirmando rechazo
        val sniperTime = now + 59000L
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 104.0, timestampMs = sniperTime - 2000L))
        engine.onNewTick(MarketTick(asset = "CRYPTO_IDX", price = 104.5, timestampMs = sniperTime - 1000L))
        val sniperTick = MarketTick(
            asset = "CRYPTO_IDX",
            price = 105.0,
            timestampMs = sniperTime,
            velocity = 0.05f,
            isBullishImpulse = true
        )
        engine.onNewTick(sniperTick)

        assertEquals("Bajo estrategia SUPPORT_RESISTANCE debe ejecutarse MT_REJECTION en soporte", TradeAction.BUY, signalEmitted)
        assertTrue("La señal debe ser MT_REJECTION", signalReason.contains("MT_REJECTION"))
    }
}

