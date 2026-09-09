package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VisionAnalyzerTest {

    private lateinit var analyzer: VisionAnalyzer

    @Before
    fun setUp() {
        analyzer = VisionAnalyzer()
    }

    @Test
    fun testRejectionWickCalculation_lowerAndUpperWicks() {
        // Vela con mecha inferior larga (Rechazo bajista / Absorción de compradores)
        // topY = 100, bottomY = 200 (totalH = 100)
        // bodyTopY = 110, bodyBottomY = 155 (bodyH = 45)
        // topWick = 10 (10%), bottomWick = 45 (45% >= 35%)
        val rejectionBottomCandle = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 500f,
            topY = 100f,
            bottomY = 200f,
            bodyTopY = 110f,
            bodyBottomY = 155f
        )

        assertEquals(100f, rejectionBottomCandle.totalHeight, 0.01f)
        assertEquals(45f, rejectionBottomCandle.bodyHeight, 0.01f)
        assertEquals(0.10f, rejectionBottomCandle.topWickRatio, 0.01f)
        assertEquals(0.45f, rejectionBottomCandle.bottomWickRatio, 0.01f)

        // Evaluar rechazo en soporte (Support = 200f)
        val resultCall = analyzer.evaluateCandlePatterns(
            candleList = listOf(rejectionBottomCandle),
            supportLinesY = listOf(200f),
            resistanceLinesY = listOf(50f)
        )

        assertTrue("Debe detectar mecha de rechazo inferior", resultCall.hasBottomRejectionWick)
        assertTrue("Debe activar señal isRejectionCall en soporte", resultCall.isRejectionCall)
        assertFalse("No debe activar isRejectionPut", resultCall.isRejectionPut)

        // Vela con mecha superior larga (Rechazo alcista / Vendedores absorben)
        // topY = 100, bottomY = 200 (totalH = 100)
        // bodyTopY = 145, bodyBottomY = 190 (bodyH = 45)
        // topWick = 45 (45% >= 35%), bottomWick = 10 (10%)
        val rejectionTopCandle = analyzer.createCandle(
            type = CandleType.RED,
            x = 500f,
            topY = 100f,
            bottomY = 200f,
            bodyTopY = 145f,
            bodyBottomY = 190f
        )

        assertEquals(0.45f, rejectionTopCandle.topWickRatio, 0.01f)
        assertEquals(0.10f, rejectionTopCandle.bottomWickRatio, 0.01f)

        // Evaluar rechazo en resistencia (Resistance = 100f)
        val resultPut = analyzer.evaluateCandlePatterns(
            candleList = listOf(rejectionTopCandle),
            supportLinesY = listOf(300f),
            resistanceLinesY = listOf(100f)
        )

        assertTrue("Debe detectar mecha de rechazo superior", resultPut.hasTopRejectionWick)
        assertTrue("Debe activar señal isRejectionPut en resistencia", resultPut.isRejectionPut)
        assertFalse("No debe activar isRejectionCall", resultPut.isRejectionCall)
    }

    @Test
    fun testEngulfingCandleDetection_atSupportAndResistance() {
        // Envolvente Alcista en Soporte (isEngulfingCall)
        // c1 (previa): Roja pequeña (body=20px, bodyTop=470, bodyBottom=490)
        // c0 (actual): Verde envolvente (body=35px >= 1.3 * 20 = 26px, bodyTop=460, bodyBottom=495)
        // Soporte en Y = 500f
        val prevRed = analyzer.createCandle(
            type = CandleType.RED,
            x = 480f,
            topY = 465f,
            bottomY = 495f,
            bodyTopY = 470f,
            bodyBottomY = 490f
        )
        val currGreen = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 500f,
            topY = 455f,
            bottomY = 500f,
            bodyTopY = 460f,
            bodyBottomY = 495f
        )

        val resultEngulfingCall = analyzer.evaluateCandlePatterns(
            candleList = listOf(currGreen, prevRed),
            supportLinesY = listOf(500f),
            resistanceLinesY = listOf(200f)
        )

        assertTrue("Debe detectar envolvente alcista en soporte", resultEngulfingCall.isEngulfingCall)
        assertFalse("No debe detectar envolvente bajista", resultEngulfingCall.isEngulfingPut)

        // Envolvente Bajista en Resistencia (isEngulfingPut)
        // c1 (previa): Verde pequeña (body=20px, bodyTop=210, bodyBottom=230)
        // c0 (actual): Roja envolvente (body=35px >= 1.3 * 20 = 26px, bodyTop=205, bodyBottom=240)
        // Resistencia en Y = 200f
        val prevGreen = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 480f,
            topY = 205f,
            bottomY = 235f,
            bodyTopY = 210f,
            bodyBottomY = 230f
        )
        val currRed = analyzer.createCandle(
            type = CandleType.RED,
            x = 500f,
            topY = 200f,
            bottomY = 245f,
            bodyTopY = 205f,
            bodyBottomY = 240f
        )

        val resultEngulfingPut = analyzer.evaluateCandlePatterns(
            candleList = listOf(currRed, prevGreen),
            supportLinesY = listOf(500f),
            resistanceLinesY = listOf(200f)
        )

        assertTrue("Debe detectar envolvente bajista en resistencia", resultEngulfingPut.isEngulfingPut)
        assertFalse("No debe detectar envolvente alcista", resultEngulfingPut.isEngulfingCall)
    }

    @Test
    fun test3VelasExhaustionDetection() {
        // Agotamiento bajista: 3 velas rojas consecutivas con cuerpos decrecientes
        // c2 (V1 inicial): cuerpo = 50px
        // c1 (V2 media): cuerpo = 28px (< V1)
        // c0 (V3 reciente): cuerpo = 12px (< V2 y <= 0.40 * 50 = 20px)
        val c2Red = analyzer.createCandle(CandleType.RED, 460f, 300f, 360f, 305f, 355f) // body = 50
        val c1Red = analyzer.createCandle(CandleType.RED, 480f, 355f, 390f, 360f, 388f) // body = 28
        val c0Red = analyzer.createCandle(CandleType.RED, 500f, 388f, 410f, 392f, 404f) // body = 12

        val result3VelasCall = analyzer.evaluateCandlePatterns(
            candleList = listOf(c0Red, c1Red, c2Red)
        )

        assertTrue("Debe detectar agotamiento de 3 velas rojas (is3VelasCall)", result3VelasCall.is3VelasCall)
        assertTrue("isExhaustion3CandlesCall debe coincidir", result3VelasCall.isExhaustion3CandlesCall)
        assertFalse("No debe detectar agotamiento alcista", result3VelasCall.is3VelasPut)

        // Agotamiento alcista: 3 velas verdes consecutivas con cuerpos decrecientes
        // c2 (V1 inicial): cuerpo = 60px
        // c1 (V2 media): cuerpo = 32px (< V1)
        // c0 (V3 reciente): cuerpo = 15px (< V2 y <= 0.40 * 60 = 24px)
        val c2Green = analyzer.createCandle(CandleType.GREEN, 460f, 400f, 470f, 405f, 465f) // body = 60
        val c1Green = analyzer.createCandle(CandleType.GREEN, 480f, 360f, 400f, 365f, 397f) // body = 32
        val c0Green = analyzer.createCandle(CandleType.GREEN, 500f, 340f, 365f, 345f, 360f) // body = 15

        val result3VelasPut = analyzer.evaluateCandlePatterns(
            candleList = listOf(c0Green, c1Green, c2Green)
        )

        assertTrue("Debe detectar agotamiento de 3 velas verdes (is3VelasPut)", result3VelasPut.is3VelasPut)
        assertTrue("isExhaustion3CandlesPut debe coincidir", result3VelasPut.isExhaustion3CandlesPut)
        assertFalse("No debe detectar agotamiento bajista", result3VelasPut.is3VelasCall)
    }

    @Test
    fun testFalseBreakout_institutionalTrap() {
        // Trampa Bajista (Falso Rompimiento de Soporte):
        // Soporte = 500f
        // La mecha perfora hacia abajo hasta Y = 515f (perforación = 15px >= 8px)
        // El cuerpo cierra arriba del soporte: bodyBottomY = 495f (cerró por encima)
        // bottomWickRatio = (515 - 495) / (515 - 460) = 20 / 55 = 0.363 >= 35%
        val bearTrapCandle = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 500f,
            topY = 460f,
            bottomY = 515f,
            bodyTopY = 470f,
            bodyBottomY = 495f
        )

        val resultTrapCall = analyzer.evaluateCandlePatterns(
            candleList = listOf(bearTrapCandle),
            supportLinesY = listOf(500f),
            resistanceLinesY = listOf(200f)
        )

        assertTrue("Debe detectar trampa institucional en soporte (isFalseBreakoutCall)", resultTrapCall.isFalseBreakoutCall)
        assertFalse("No debe detectar trampa alcista", resultTrapCall.isFalseBreakoutPut)

        // Trampa Alcista (Falso Rompimiento de Resistencia):
        // Resistencia = 200f
        // La mecha perfora hacia arriba hasta Y = 185f (perforación = 15px >= 8px)
        // El cuerpo cierra por debajo de la resistencia: bodyTopY = 205f (cerró por debajo en pantalla)
        // topWickRatio = (205 - 185) / (240 - 185) = 20 / 55 = 0.363 >= 35%
        val bullTrapCandle = analyzer.createCandle(
            type = CandleType.RED,
            x = 500f,
            topY = 185f,
            bottomY = 240f,
            bodyTopY = 205f,
            bodyBottomY = 230f
        )

        val resultTrapPut = analyzer.evaluateCandlePatterns(
            candleList = listOf(bullTrapCandle),
            supportLinesY = listOf(500f),
            resistanceLinesY = listOf(200f)
        )

        assertTrue("Debe detectar trampa institucional en resistencia (isFalseBreakoutPut)", resultTrapPut.isFalseBreakoutPut)
        assertFalse("No debe detectar trampa bajista", resultTrapPut.isFalseBreakoutCall)
    }

    @Test
    fun testSidewaysAndDojiMarketFilter() {
        // Caso 1: Mercado lateral por cuerpos promedio pequeños (< 15px)
        val tinyCandles = (1..10).map { i ->
            analyzer.createCandle(
                type = if (i % 2 == 0) CandleType.GREEN else CandleType.RED,
                x = (400 + i * 15).toFloat(),
                topY = 300f,
                bottomY = 320f,
                bodyTopY = 305f,
                bodyBottomY = 315f // bodyHeight = 10px (< 15px)
            )
        }

        val resultTiny = analyzer.evaluateCandlePatterns(candleList = tinyCandles)
        assertTrue("Debe identificar mercado lateral por cuerpos < 15px", resultTiny.isMarketSideways)

        // Caso 2: Mercado con >= 50% de velas Doji
        val dojiCandles = (1..10).map { i ->
            if (i <= 6) {
                // Doji: bodyHeight = 3px
                analyzer.createCandle(
                    type = CandleType.DOJI,
                    x = (400 + i * 15).toFloat(),
                    topY = 300f,
                    bottomY = 340f,
                    bodyTopY = 318f,
                    bodyBottomY = 321f
                )
            } else {
                // Vela normal
                analyzer.createCandle(
                    type = CandleType.GREEN,
                    x = (400 + i * 15).toFloat(),
                    topY = 300f,
                    bottomY = 360f,
                    bodyTopY = 310f,
                    bodyBottomY = 350f
                )
            }
        }

        val resultDoji = analyzer.evaluateCandlePatterns(candleList = dojiCandles)
        assertTrue("Debe identificar mercado lateral por >= 50% Dojis", resultDoji.isMarketSideways)

        // Caso 3: Mercado en tendencia con velas sanas (> 30px)
        val trendingCandles = (1..10).map { i ->
            analyzer.createCandle(
                type = CandleType.GREEN,
                x = (400 + i * 15).toFloat(),
                topY = (500 - i * 20).toFloat(),
                bottomY = (560 - i * 20).toFloat(),
                bodyTopY = (510 - i * 20).toFloat(),
                bodyBottomY = (550 - i * 20).toFloat() // bodyHeight = 40px
            )
        }

        val resultTrending = analyzer.evaluateCandlePatterns(candleList = trendingCandles)
        assertFalse("Mercado en tendencia no debe ser marcado como lateral", resultTrending.isMarketSideways)
    }

    @Test
    fun testContiguousVerticalRunFilter_filtersNoise() {
        // 1 muestra a paso 2px = 2px (< 6px) -> Ruido / Media móvil fina
        assertFalse("1 muestra (2px) debe ser descartada", analyzer.isValidContiguousRun(1, 2))
        // 2 muestras a paso 2px = 4px (< 6px) -> Ruido
        assertFalse("2 muestras (4px) debe ser descartada", analyzer.isValidContiguousRun(2, 2))
        // 3 muestras a paso 2px = 6px (>= 6px) -> Vela válida
        assertTrue("3 muestras (6px) debe ser aceptada", analyzer.isValidContiguousRun(3, 2))
        // 5 muestras a paso 2px = 10px (>= 6px) -> Vela válida
        assertTrue("5 muestras (10px) debe ser aceptada", analyzer.isValidContiguousRun(5, 2))
    }

    @Test
    fun testDynamicSupportAndResistanceExtraction() {
        val candle1 = analyzer.createCandle(CandleType.GREEN, 100f, 150f, 350f, 180f, 320f)
        val candle2 = analyzer.createCandle(CandleType.RED, 120f, 120f, 380f, 140f, 360f) // Extremo superior 120f, extremo inferior 380f
        val candle3 = analyzer.createCandle(CandleType.GREEN, 140f, 160f, 340f, 170f, 330f)

        val result = analyzer.evaluateCandlePatterns(
            candleList = listOf(candle3, candle2, candle1)
        )

        // Resistencia = min Y (120f), Soporte = max Y (380f)
        assertEquals(120f, result.dynamicResistanceY, 0.1f)
        assertEquals(380f, result.dynamicSupportY, 0.1f)
    }

    @Test
    fun testCandleClusteringAndMerging() {
        // Simular 3 columnas verticales pertenecientes a una misma vela ancha verde de 12px
        val col1 = analyzer.createCandle(CandleType.GREEN, 500f, 200f, 300f, 220f, 280f)
        val col2 = analyzer.createCandle(CandleType.GREEN, 504f, 195f, 305f, 218f, 282f)
        val col3 = analyzer.createCandle(CandleType.GREEN, 508f, 202f, 298f, 222f, 278f)

        // Otra vela roja separada
        val col4 = analyzer.createCandle(CandleType.RED, 530f, 250f, 350f, 260f, 340f)

        val merged = analyzer.clusterAndMergeCandleColumns(listOf(col1, col2, col3, col4), minSpacingPx = 10f)

        assertEquals("Debe consolidar las 3 columnas en 1 sola vela + 1 vela roja = 2 velas en total", 2, merged.size)
        assertEquals(CandleType.GREEN, merged[0].type)
        assertEquals(CandleType.RED, merged[1].type)
        assertEquals(195f, merged[0].topY, 0.1f) // Captura el extremo más alto
        assertEquals(305f, merged[0].bottomY, 0.1f) // Captura el extremo más bajo
    }

    @Test
    fun testFractalSwingLevelsDetection() {
        // Crear 7 velas formando un Swing High en la vela central (índice 3)
        // y un Swing Low en la vela 5
        val c0 = analyzer.createCandle(CandleType.GREEN, 100f, 300f, 400f, 320f, 380f)
        val c1 = analyzer.createCandle(CandleType.GREEN, 120f, 250f, 420f, 270f, 400f)
        val c2 = analyzer.createCandle(CandleType.GREEN, 140f, 220f, 410f, 240f, 390f)
        val c3 = analyzer.createCandle(CandleType.GREEN, 160f, 180f, 430f, 200f, 410f) // Swing High: topY = 180f (menor que vecinos)
        val c4 = analyzer.createCandle(CandleType.RED, 180f, 230f, 440f, 250f, 420f)
        val c5 = analyzer.createCandle(CandleType.RED, 200f, 280f, 500f, 300f, 480f) // Swing Low: bottomY = 500f (mayor que vecinos)
        val c6 = analyzer.createCandle(CandleType.GREEN, 220f, 260f, 450f, 280f, 430f)

        val (supports, resistances) = analyzer.detectFractalLevels(listOf(c0, c1, c2, c3, c4, c5, c6))

        assertTrue("Debe detectar al menos una resistencia fractal", resistances.isNotEmpty())
        assertEquals(180f, resistances[0], 0.1f)
    }

    @Test
    fun testConsolidationTightFilterAndConfluenceScore() {
        // Velas microscópicas con cuerpo menor a 10px (compresión sin volumen)
        val tightCandles = List(5) { i ->
            analyzer.createCandle(CandleType.DOJI, 200f + i * 15f, 300f, 315f, 305f, 310f)
        }

        val result = analyzer.evaluateCandlePatterns(
            candleList = tightCandles,
            supportLinesY = emptyList(),
            resistanceLinesY = emptyList(),
            isLandscape = true
        )

        assertTrue("Debe marcar consolidación estrecha cuando los cuerpos son minúsculos", result.isConsolidationTight)
        assertTrue("El score de confluencia CALL debe penalizarse ante consolidación", result.confluenceScoreCall <= 50)
        assertTrue("El score de confluencia PUT debe penalizarse ante consolidación", result.confluenceScorePut <= 50)
    }
}
