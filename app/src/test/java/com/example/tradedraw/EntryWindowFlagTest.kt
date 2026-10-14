package com.example.tradedraw

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 14 (A3): contrato del flag `entryEarliestSecond`.
 *
 * El flag existe para poder contrastar en el futuro la variante A (ventana actual) contra la
 * variante B (`:03-:08`). Lo que estos tests blindan es lo importante: **con el valor por
 * defecto, el comportamiento es EXACTAMENTE el de siempre**. Si alguien cambia el default a 3
 * "para probar", estos tests fallan y el cambio no pasa desapercibido.
 */
class EntryWindowFlagTest {

    @After
    fun restaurar() {
        MarketTickFilters.resetEntryEarliestSecondForTests()
    }

    @Test
    fun elValorPorDefectoEsElComportamientoHistorico() {
        assertEquals("El default debe seguir siendo :01",
            MarketTickFilters.ENTRY_WINDOW_FIRST_SECOND_DEFAULT,
            MarketTickFilters.entryEarliestSecond)
        // Réplica literal del `sec in 1..5` de todas las rondas anteriores.
        for (s in 0..59) {
            assertEquals("segundo $s con default", s in 1..5, MarketTickFilters.isInEntryWindow(s))
        }
    }

    @Test
    fun laVentanaPorDefectoEsDe1A5() {
        assertFalse(MarketTickFilters.isInEntryWindow(0))
        for (s in 1..5) assertTrue("segundo $s debe estar dentro", MarketTickFilters.isInEntryWindow(s))
        for (s in 6..59) assertFalse("segundo $s debe estar fuera", MarketTickFilters.isInEntryWindow(s))
    }

    @Test
    fun conLaVarianteBLaVentanaEmpiezaEn3() {
        MarketTickFilters.entryEarliestSecond = 3
        assertFalse(":01 queda fuera en la variante B", MarketTickFilters.isInEntryWindow(1))
        assertFalse(":02 queda fuera en la variante B", MarketTickFilters.isInEntryWindow(2))
        assertTrue(MarketTickFilters.isInEntryWindow(3))
        assertTrue(MarketTickFilters.isInEntryWindow(5))
        assertFalse(":06 sigue vetado: el fin de ventana no se mueve", MarketTickFilters.isInEntryWindow(6))
    }

    @Test
    fun elFinDeVentanaNuncaSeDesplaza() {
        // Sólo el INICIO es configurable: si se moviera el fin, la variante B dejaría de ser
        // comparable con la A y el preregistro quedaría invalidado.
        assertEquals(5, MarketTickFilters.ENTRY_WINDOW_LAST_SECOND)
    }

    @Test
    fun losFiltrosDerivadosSiguenAlFlag() {
        assertTrue(MarketTickFilters.isStrictTimingWindow(1))
        assertFalse(MarketTickFilters.isTimingVetoed(1))
        assertTrue(MarketTickFilters.isTimingVetoed(10))

        MarketTickFilters.entryEarliestSecond = 3
        assertFalse("Con B, :01 ya no es ventana estricta", MarketTickFilters.isStrictTimingWindow(1))
        assertTrue("Con B, :01 pasa a estar vetado", MarketTickFilters.isTimingVetoed(1))
        assertTrue(MarketTickFilters.isStrictTimingWindow(3))
    }

    @Test
    fun marketTickRespetaElFlag() {
        val t = MarketTick(asset = "ETHUSD-OTC", price = 100.0, timestampMs = 61_000L) // segundo 1
        assertEquals(1, t.candleSecond)
        assertTrue("Con default, el tick en :01 es ventana estricta", t.isStrictTimingWindow)

        MarketTickFilters.entryEarliestSecond = 3
        assertFalse("Con la variante B, el mismo tick ya no lo es", t.isStrictTimingWindow)
        assertTrue(t.isTimingVetoed)
    }

    @Test
    fun candleTimeframeUsaLaMismaVentana() {
        val tsSegundo1 = 61_000L // % 60 = 1
        assertTrue(CandleTimeframe.M1.isSniperTimingWindow(tsSegundo1))
        MarketTickFilters.entryEarliestSecond = 3
        assertFalse(CandleTimeframe.M1.isSniperTimingWindow(tsSegundo1))
        assertTrue(CandleTimeframe.M1.isSniperTimingWindow(63_000L)) // % 60 = 3
    }
}
