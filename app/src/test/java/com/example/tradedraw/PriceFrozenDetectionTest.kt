package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

/**
 * Regresión de la guarda de precio congelado.
 *
 * Motivo real: el bot operaba `Z-CRY/IDX` recibiendo ~2,5 ticks/s (feed "fresco")
 * pero con el precio inmóvil, así que `isFeedFresh()` daba true y el motor decidía
 * CALL/PUT sobre una línea plana. Los valores de abajo son los medidos en el
 * dispositivo, no inventados.
 */
class PriceFrozenDetectionTest {

    /** Serie real de `Z-CRY/IDX`: 8 dígitos significativos que oscilan ~5e-10. */
    private val frozenIdxTicks = listOf(
        641.867393985,
        641.867393995,
        641.867394035,
        641.867394055,
        641.867394075,
        641.867394085,
        641.867394095,
        641.867394105
    )

    /** Serie real de `BTCUSD`: miles de unidades con movimiento de 2 decimales. */
    private val liveBtcTicks = listOf(
        76521.9345,
        76521.9355,
        76521.9285,
        76521.9410,
        76531.4905,
        76534.4185
    )

    @Test
    fun `indice sintetico pegado se detecta como congelado`() {
        val ratio = MarketTickFilters.priceRangeRatio(frozenIdxTicks)
        assertTrue("rangeRatio debe ser minusculo, fue $ratio", ratio > 0.0 && ratio < 1e-6)
        assertTrue(
            "Z-CRY/IDX debe clasificarse como congelado",
            MarketTickFilters.isPriceFrozen(frozenIdxTicks)
        )
    }

    @Test
    fun `activo real con movimiento no se detecta como congelado`() {
        val ratio = MarketTickFilters.priceRangeRatio(liveBtcTicks)
        assertTrue("BTCUSD debe superar el umbral, fue $ratio", ratio > 1e-6)
        assertFalse(
            "Un activo con movimiento real no debe vetarse",
            MarketTickFilters.isPriceFrozen(liveBtcTicks)
        )
    }

    @Test
    fun `precio perfectamente constante cuenta como congelado`() {
        assertTrue(MarketTickFilters.isPriceFrozen(List(10) { 641.8674 }))
    }

    @Test
    fun `sin muestra suficiente el estado es desconocido y no veta`() {
        assertEquals(-1.0, MarketTickFilters.priceRangeRatio(listOf(641.8)), 0.0)
        assertEquals(-1.0, MarketTickFilters.priceRangeRatio(emptyList()), 0.0)
        assertFalse(MarketTickFilters.isPriceFrozen(listOf(641.8, 641.9)))
    }

    @Test
    fun `matches los valores reales medidos en el dispositivo`() {
        // 22 muestras unicas medidas: min y max reales del pegado.
        val measured = listOf(641.867393735, 641.867394055)
        val ratio = MarketTickFilters.priceRangeRatio(measured + 641.8673939)
        assertEquals(4.99e-10, ratio, 1e-10)
    }
}