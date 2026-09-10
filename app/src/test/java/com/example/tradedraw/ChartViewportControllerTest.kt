package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChartViewportControllerTest {

    private lateinit var riskManager: RiskManager
    private lateinit var controller: ChartViewportController

    @Before
    fun setUp() {
        riskManager = RiskManager(null)
        riskManager.resetSession()
        controller = ChartViewportController(null, riskManager)
        controller.lastAdjustmentTime = 0L
    }

    @Test
    fun testCanPerformGesture_blockedDuringPendingTrade() {
        riskManager.recordTradeSent(TradeAction.BUY)
        assertTrue("Trade debe estar pendiente", riskManager.hasPendingTrade)

        val (canGesture, reason) = controller.canPerformGesture(candleSecond = 25)
        assertFalse("No se debe permitir mover el gráfico durante un trade activo", canGesture)
        assertTrue("Razón debe advertir operación abierta", reason.contains("Operación abierta"))
    }

    @Test
    fun testCanPerformGesture_blockedDuringSniperWindow() {
        // En los segundos 50..59 y 0..6 el bot está analizando o disparando órdenes
        val (blocked55, reason55) = controller.canPerformGesture(candleSecond = 55)
        assertFalse("Debe bloquearse en segundo 55", blocked55)
        assertTrue(reason55.contains("Ventana sniper"))

        val (blocked02, reason02) = controller.canPerformGesture(candleSecond = 2)
        assertFalse("Debe bloquearse en segundo 02", blocked02)
        assertTrue(reason02.contains("Ventana sniper"))

        // En segundo 25 (fuera de ventana sniper), debe permitirse
        val (allowed25, reason25) = controller.canPerformGesture(candleSecond = 25)
        assertTrue("Debe permitirse en segundo 25", allowed25)
        assertEquals("Seguro para acomodar", reason25)
    }

    @Test
    fun testCanPerformGesture_respectsCooldown() {
        // Primer movimiento
        controller.lastAdjustmentTime = System.currentTimeMillis()

        val (blockedCooldown, reasonCooldown) = controller.canPerformGesture(candleSecond = 25)
        assertFalse("Debe bloquearse por cooldown si acaba de moverse", blockedCooldown)
        assertTrue("Razón debe mencionar cooldown", reasonCooldown.contains("Cooldown de acomodado"))

        // Simular paso del tiempo (25 segundos después)
        controller.lastAdjustmentTime = System.currentTimeMillis() - 25000L
        val (allowedAfterCooldown, _) = controller.canPerformGesture(candleSecond = 25)
        assertTrue("Debe permitirse tras pasar el cooldown", allowedAfterCooldown)
    }

    @Test
    fun testCanPerformGesture_blockedWhenDisabled() {
        controller.isEnabled = false
        val (canGesture, reason) = controller.canPerformGesture(candleSecond = 25)
        assertFalse("Debe bloquearse si el controlador está desactivado", canGesture)
        assertTrue(reason.contains("desactivado"))
    }

    @Test
    fun testVisionAnalysisResult_detectsOffCenterRightAndLeft() {
        val analyzer = VisionAnalyzer()

        // 1. Velas desplazadas a la derecha (latestCandleX = 850f en un ancho de 1000f, > 88%)
        val candleFarRight = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 920f,
            topY = 400f,
            bottomY = 500f,
            bodyTopY = 420f,
            bodyBottomY = 480f
        )
        val resultRight = analyzer.evaluateCandlePatterns(
            candleList = listOf(candleFarRight),
            startX = 0f,
            endX = 1000f,
            startY = 0f,
            endY = 1000f
        )
        assertTrue("Debe detectar gráfico desplazado a la derecha", resultRight.isChartOffCenterRight)
        assertFalse("No debe marcar desplazado a la izquierda", resultRight.isChartOffCenterLeft)

        // 2. Velas atrasadas a la izquierda (latestCandleX = 300f en un ancho de 1000f, < 45%)
        val candleFarLeft = analyzer.createCandle(
            type = CandleType.RED,
            x = 300f,
            topY = 400f,
            bottomY = 500f,
            bodyTopY = 420f,
            bodyBottomY = 480f
        )
        val resultLeft = analyzer.evaluateCandlePatterns(
            candleList = listOf(candleFarLeft),
            startX = 0f,
            endX = 1000f,
            startY = 0f,
            endY = 1000f
        )
        assertTrue("Debe detectar gráfico atrasado en el pasado (izquierda)", resultLeft.isChartOffCenterLeft)
        assertFalse("No debe marcar desplazado a la derecha", resultLeft.isChartOffCenterRight)
    }

    @Test
    fun testVisionAnalysisResult_detectsPriceNearTopAndBottom() {
        val analyzer = VisionAnalyzer()

        // Precio cerca del fondo (Y = 960 en un alto de 1000)
        val candleBottom = analyzer.createCandle(
            type = CandleType.RED,
            x = 600f,
            topY = 940f,
            bottomY = 980f,
            bodyTopY = 950f,
            bodyBottomY = 970f
        )
        val resultBottom = analyzer.evaluateCandlePatterns(
            candleList = listOf(candleBottom),
            startX = 0f,
            endX = 1000f,
            startY = 0f,
            endY = 1000f
        )
        assertTrue("Debe detectar precio tocando límite inferior", resultBottom.isPriceNearBottom)
        assertFalse("No debe detectar precio cerca del techo", resultBottom.isPriceNearTop)

        // Precio cerca del techo (Y = 40 en un alto de 1000)
        val candleTop = analyzer.createCandle(
            type = CandleType.GREEN,
            x = 600f,
            topY = 20f,
            bottomY = 60f,
            bodyTopY = 30f,
            bodyBottomY = 50f
        )
        val resultTop = analyzer.evaluateCandlePatterns(
            candleList = listOf(candleTop),
            startX = 0f,
            endX = 1000f,
            startY = 0f,
            endY = 1000f
        )
        assertTrue("Debe detectar precio tocando límite superior", resultTop.isPriceNearTop)
        assertFalse("No debe detectar precio cerca del fondo", resultTop.isPriceNearBottom)
    }
}
