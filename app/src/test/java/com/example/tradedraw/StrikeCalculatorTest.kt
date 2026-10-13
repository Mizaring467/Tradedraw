package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de strike y moneyness (Ronda 11, T3).
 *
 * Regresión del incidente: el HUD mostraba `▼ PUT [ITM +$$$]` con el precio por encima del strike.
 * En opciones binarias un PUT con el precio por encima del strike está OTM, no ITM: la comparación
 * anterior usaba coordenadas de pantalla (menor Y = mayor precio) que nadie garantizaba que
 * correspondieran a la escala real del gráfico.
 */
class StrikeCalculatorTest {

    // --- Moneyness ---------------------------------------------------------------------------

    @Test
    fun call_ganaConPrecioPorEncimaDelStrike() {
        assertEquals(TradeMoneyness.ITM, StrikeCalculator.moneyness(TradeAction.BUY, 641.0, 642.0))
    }

    @Test
    fun call_pierdeConPrecioPorDebajoDelStrike() {
        assertEquals(TradeMoneyness.OTM, StrikeCalculator.moneyness(TradeAction.BUY, 641.0, 640.0))
    }

    @Test
    fun put_ganaConPrecioPorDebajoDelStrike() {
        assertEquals(TradeMoneyness.ITM, StrikeCalculator.moneyness(TradeAction.SELL, 641.0, 640.0))
    }

    @Test
    fun put_pierdeConPrecioPorEncimaDelStrike() {
        // Este es exactamente el caso imposible que mostraba el HUD.
        assertEquals(TradeMoneyness.OTM, StrikeCalculator.moneyness(TradeAction.SELL, 641.0, 642.0))
    }

    @Test
    fun precioIgualAlStrike_esEmpateYNoGanaNadie() {
        assertEquals(TradeMoneyness.TIE, StrikeCalculator.moneyness(TradeAction.BUY, 641.0, 641.0))
        assertEquals(TradeMoneyness.TIE, StrikeCalculator.moneyness(TradeAction.SELL, 641.0, 641.0))
    }

    @Test
    fun preciosInvalidos_noDeclaranVictoria() {
        assertEquals(TradeMoneyness.TIE, StrikeCalculator.moneyness(TradeAction.BUY, 0.0, 642.0))
        assertEquals(TradeMoneyness.TIE, StrikeCalculator.moneyness(TradeAction.BUY, 641.0, 0.0))
    }

    // --- P&L ---------------------------------------------------------------------------------

    @Test
    fun pnlItm_esStakePorPayout() {
        assertEquals(8.5, StrikeCalculator.estimatedPnl(TradeMoneyness.ITM, 10.0, 0.85), 1e-9)
    }

    @Test
    fun pnlOtm_esMenosStake() {
        assertEquals(-10.0, StrikeCalculator.estimatedPnl(TradeMoneyness.OTM, 10.0, 0.85), 1e-9)
    }

    @Test
    fun pnlEmpate_esCero() {
        assertEquals(0.0, StrikeCalculator.estimatedPnl(TradeMoneyness.TIE, 10.0, 0.85), 1e-9)
    }

    // --- Etiqueta ----------------------------------------------------------------------------

    @Test
    fun etiquetaItm_llevaPrecioYGananciaReal() {
        val label = StrikeCalculator.strikeLabel(TradeAction.BUY, TradeMoneyness.ITM, 641.867319, 8.5)
        assertTrue("Debe indicar ITM: $label", label.contains("ITM"))
        assertTrue("Debe mostrar el strike: $label", label.contains("641.8673"))
        assertTrue("Debe mostrar la ganancia real: $label", label.contains("+8.50"))
        assertFalse("No debe quedar el literal de relleno: $label", label.contains("$$$"))
    }

    @Test
    fun etiquetaOtm_llevaPerdidaRealYNoCeroGenerico() {
        val label = StrikeCalculator.strikeLabel(TradeAction.SELL, TradeMoneyness.OTM, 641.867319, -10.0)
        assertTrue("Debe indicar OTM: $label", label.contains("OTM"))
        assertTrue("Debe mostrar la pérdida real: $label", label.contains("-10.00"))
        assertFalse("No debe quedar el literal de relleno: $label", label.contains("\$0"))
    }

    @Test
    fun etiquetaSinStrike_usaGuionYNoCero() {
        val label = StrikeCalculator.strikeLabel(TradeAction.BUY, TradeMoneyness.TIE, 0.0, 0.0)
        assertTrue("Sin strike debe mostrar guion: $label", label.contains("—"))
    }

    // --- Proyección precio -> Y ---------------------------------------------------------------

    @Test
    fun precioMasAltoSeDibujaMasArriba() {
        PriceViewportMapper.reset()
        PriceViewportMapper.setVisiblePriceRange(640.0, 660.0, 0f, 1000f)
        val yAlto = PriceViewportMapper.priceToY(655.0)
        val yBajo = PriceViewportMapper.priceToY(645.0)
        assertNotNull(yAlto)
        assertNotNull(yBajo)
        assertTrue("Mayor precio debe tener menor Y", yAlto!! < yBajo!!)
    }

    @Test
    fun sinRangoNiCalibracion_noInventaUnaY() {
        PriceViewportMapper.reset()
        assertNull("Sin calibración no se debe devolver una Y", PriceViewportMapper.priceToY(641.0))
    }

    @Test
    fun calibracionPorPuntos_tienePrioridadSobreElRango() {
        PriceViewportMapper.reset()
        PriceViewportMapper.setVisiblePriceRange(640.0, 660.0, 0f, 1000f)
        PriceViewportMapper.setCalibrationForTests(700.0, 100f, 600.0, 900f)
        // Interpolación exacta: precio 650 -> punto medio -> y = 500
        assertEquals(500f, PriceViewportMapper.priceToY(650.0)!!, 0.5f)
        assertTrue(PriceViewportMapper.isCalibrated)
    }

    // --- Geometría del strike (anclaje al borde) ----------------------------------------------

    @Test
    fun strikeDentroDelViewport_noSeAncla() {
        val geo = StrikeCalculator.strikeGeometry(650.0, { 500f }, 0f, 1000f)
        assertNotNull(geo)
        assertEquals(500f, geo!!.y, 0.01f)
        assertFalse(geo.clamped)
        assertEquals("", geo.arrow)
    }

    @Test
    fun strikePorEncimaDelViewport_seAnclaArribaConFlecha() {
        val geo = StrikeCalculator.strikeGeometry(700.0, { -50f }, 0f, 1000f)
        assertNotNull(geo)
        assertEquals(0f, geo!!.y, 0.01f)
        assertTrue(geo.clamped)
        assertEquals("↑", geo.arrow)
    }

    @Test
    fun strikePorDebajoDelViewport_seAnclaAbajoConFlecha() {
        val geo = StrikeCalculator.strikeGeometry(600.0, { 1500f }, 0f, 1000f)
        assertNotNull(geo)
        assertEquals(1000f, geo!!.y, 0.01f)
        assertTrue(geo.clamped)
        assertEquals("↓", geo.arrow)
    }

    @Test
    fun viewportDegenerado_noDevuelveGeometria() {
        assertNull(StrikeCalculator.strikeGeometry(650.0, { 500f }, 1000f, 0f))
        assertNull(StrikeCalculator.strikeGeometry(0.0, { 500f }, 0f, 1000f))
    }
}
