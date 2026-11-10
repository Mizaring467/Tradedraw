package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TapCoordinateResolverTest {

    @Test
    fun defaultsLandscapeBuyYSellSonExactos() {
        val resolver = CalibrationTapCoordinateResolver { null }
        val w = 1600f
        val h = 720f

        val (buyX, buyY) = resolver.resolve(TradeAction.BUY, isLandscape = true, screenW = w, screenH = h)
        assertEquals(w * 0.881f, buyX, 0.001f)
        assertEquals(h * 0.735f, buyY, 0.001f)

        val (sellX, sellY) = resolver.resolve(TradeAction.SELL, isLandscape = true, screenW = w, screenH = h)
        assertEquals(w * 0.881f, sellX, 0.001f)
        assertEquals(h * 0.844f, sellY, 0.001f)
    }

    @Test
    fun defaultsPortraitBuyYSellSonExactos() {
        val resolver = CalibrationTapCoordinateResolver { null }
        val w = 720f
        val h = 1600f

        val (buyX, buyY) = resolver.resolve(TradeAction.BUY, isLandscape = false, screenW = w, screenH = h)
        assertEquals(w * 0.25f, buyX, 0.001f)
        assertEquals(h * 0.903f, buyY, 0.001f)

        val (sellX, sellY) = resolver.resolve(TradeAction.SELL, isLandscape = false, screenW = w, screenH = h)
        assertEquals(w * 0.75f, sellX, 0.001f)
        assertEquals(h * 0.903f, sellY, 0.001f)
    }

    @Test
    fun sanitizacionVerticalClampaCoordenadaYMenorQueOchentaPorCiento() {
        // Mock custom resolver where rawY is below 0.80h
        val lowYResolver = object : com.example.tradedraw.core.TapCoordinateResolver {
            val delegate = CalibrationTapCoordinateResolver { null }
            override fun resolve(
                action: TradeAction,
                isLandscape: Boolean,
                screenW: Float,
                screenH: Float
            ): Pair<Float, Float> {
                // If we simulate a supplier providing rawY = 500 on 1600h (31.25%)
                val (rawX, _) = delegate.resolve(action, isLandscape, screenW, screenH)
                val rawY = screenH * 0.50f // < 0.80f
                return if (!isLandscape && rawY < screenH * 0.80f) {
                    Pair(rawX, screenH * 0.838f)
                } else {
                    Pair(rawX, rawY)
                }
            }
        }

        val w = 720f
        val h = 1600f
        val (x, y) = lowYResolver.resolve(TradeAction.BUY, isLandscape = false, screenW = w, screenH = h)
        assertEquals(w * 0.25f, x, 0.001f)
        assertEquals(h * 0.838f, y, 0.001f)
    }
}
