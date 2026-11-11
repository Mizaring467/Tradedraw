package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 12 (A2): lectura del payout real de Binomo.
 *
 * El HUD usaba `sorosPayoutRate` (0.85) mientras Binomo muestra 82-83%. Estos tests fijan
 * los dos metodos de lectura, su cruce y la regla de rotulado `(est.)`.
 */
class PayoutReaderTest {

    // --- Metodo 1: etiqueta `NN%` junto al activo ---

    @Test
    fun leePorcentajeSimpleDelNodoDelActivo() {
        assertEquals(0.83, PayoutReader.parsePercentLabel("83%")!!, 1e-9)
        assertEquals(0.82, PayoutReader.parsePercentLabel("82%")!!, 1e-9)
    }

    @Test
    fun leePorcentajeConEspacio() {
        assertEquals(0.83, PayoutReader.parsePercentLabel("83 %")!!, 1e-9)
        assertEquals(0.83, PayoutReader.parsePercentLabel("  83%  ")!!, 1e-9)
    }

    @Test
    fun rechazaTextosQueNoSonUnPorcentajePuro() {
        // El nodo del activo trae el nombre Y el porcentaje: eso lo recorta el servicio
        // con la regex antes de llamar aqui. Este parser solo acepta el porcentaje suelto.
        assertNull(PayoutReader.parsePercentLabel("Crypto IDX 83%"))
        assertNull(PayoutReader.parsePercentLabel("Crypto IDX"))
        assertNull(PayoutReader.parsePercentLabel(""))
        assertNull(PayoutReader.parsePercentLabel(null))
        assertNull(PayoutReader.parsePercentLabel("83"))
    }

    @Test
    fun rechazaPorcentajesFueraDeRangoPlausible() {
        // Un 5% o un 300% no son payouts: son ruido de UI (comisiones, progreso...).
        assertNull(PayoutReader.parsePercentLabel("5%"))
        assertNull(PayoutReader.parsePercentLabel("300%"))
        assertNull(PayoutReader.parsePercentLabel("0%"))
    }

    // --- Metodo 2: monto del boton / cantidad - 1 ---

    @Test
    fun deduceElPayoutDelCocienteDeLosImportes() {
        // Caso real medido en pantalla: boton Col$182,000.00 sobre Cantidad Col$100,000.
        assertEquals(0.82, PayoutReader.deriveFromButtonRatio(182_000.0, 100_000.0)!!, 1e-9)
        assertEquals(0.83, PayoutReader.deriveFromButtonRatio(183_000.0, 100_000.0)!!, 1e-9)
    }

    @Test
    fun descartaElSaldoDeLaCuentaPorqueSuCocienteNoEsPlausible() {
        // El saldo (37 millones) tambien es un importe visible; su cociente respecto a la
        // cantidad cae fuera del rango y debe descartarse solo.
        assertNull(PayoutReader.deriveFromButtonRatio(37_850_735.0, 100_000.0))
    }

    @Test
    fun descartaImportesInvalidos() {
        assertNull(PayoutReader.deriveFromButtonRatio(null, 100_000.0))
        assertNull(PayoutReader.deriveFromButtonRatio(182_000.0, null))
        assertNull(PayoutReader.deriveFromButtonRatio(0.0, 100_000.0))
        assertNull(PayoutReader.deriveFromButtonRatio(182_000.0, 0.0))
    }

    // --- Cruce de los dos metodos ---

    @Test
    fun losDosMetodosConcordantesNoMarcanDiscrepancia() {
        val r = PayoutReader.reconcile(0.83, 0.83)
        assertFalse(r.discrepant)
        assertEquals(0.83, r.resolved!!, 1e-9)
        assertTrue(r.isUsable)
    }

    @Test
    fun unaDiferenciaDeUnPuntoPorcentualNoEsDiscrepancia() {
        // 82% vs 83% puede ser redondeo del broker; la tolerancia es 2 puntos.
        val r = PayoutReader.reconcile(0.82, 0.83)
        assertFalse(r.discrepant)
        assertEquals(0.82, r.resolved!!, 1e-9)
    }

    @Test
    fun unaDiferenciaGrandeSiMarcaDiscrepancia() {
        val r = PayoutReader.reconcile(0.83, 0.60)
        assertTrue(r.discrepant)
        // Ante discrepancia se prefiere la etiqueta declarada por el broker, no la deduccion.
        assertEquals(0.83, r.resolved!!, 1e-9)
    }

    @Test
    fun conUnSoloMetodoUsaElDisponible() {
        val soloEtiqueta = PayoutReader.reconcile(0.82, null)
        assertFalse(soloEtiqueta.discrepant)
        assertEquals(0.82, soloEtiqueta.resolved!!, 1e-9)

        val soloBoton = PayoutReader.reconcile(null, 0.83)
        assertFalse(soloBoton.discrepant)
        assertEquals(0.83, soloBoton.resolved!!, 1e-9)
    }

    @Test
    fun sinNingunaLecturaNoEsUtilizable() {
        val r = PayoutReader.reconcile(null, null)
        assertNull(r.resolved)
        assertFalse(r.isUsable)
        assertEquals("est.", r.sourceLabel)
    }

    // --- Regla de rotulado `(est.)` ---

    @Test
    fun sinLecturaUsaElConfiguradoYLoMarcaComoEstimado() {
        val (payout, estimado) = PayoutReader.effectivePayout(PayoutReader.reconcile(null, null), 0.85)
        assertEquals(0.85, payout, 1e-9)
        assertTrue("sin lectura valida el P&L debe rotularse (est.)", estimado)
    }

    @Test
    fun conLecturaUsaLaDelBrokerYNoLoMarcaComoEstimado() {
        // Este es el caso que corrige el defecto: el P&L debe dejar de usar 0.85 y usar 0.82.
        val (payout, estimado) = PayoutReader.effectivePayout(PayoutReader.reconcile(0.82, 0.82), 0.85)
        assertEquals("debe usar el payout real, no el configurado", 0.82, payout, 1e-9)
        assertFalse("con lectura real no lleva (est.)", estimado)
    }

    @Test
    fun laDesviacionDelPayoutConfiguradoEsLaQueSeCorrige() {
        // Cuantifica el defecto de A2: con stake 1.0 y payout real 0.82 frente a 0.85
        // configurado, el P&L del HUD se desviaba 0.03 (3 puntos porcentuales).
        val real = 0.82
        val configurado = 0.85
        assertEquals(0.03, configurado - real, 1e-9)
        val (usado, _) = PayoutReader.effectivePayout(PayoutReader.reconcile(real, null), configurado)
        assertEquals(real, usado, 1e-9)
    }

    @Test
    fun formateaElPayoutConPuntoDecimalYPorcentaje() {
        assertEquals("82.0%", PayoutReader.format(0.82))
        assertEquals("83.0%", PayoutReader.format(0.83))
        assertEquals("85.0%", PayoutReader.format(0.85))
    }
}
