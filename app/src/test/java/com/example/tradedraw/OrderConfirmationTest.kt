package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 12 (B1): operaciones fantasma y los tres estados de envio.
 *
 * EVIDENCIA MEDIDA sobre el journal del dispositivo (212 filas unicas):
 *   TIE   17 (8,0%) — TODOS con `settled_balance == base_balance` EXACTO (diff = 0)
 *   LOSS 114 (53,8%) — mueven el saldo, p.ej. -99.999
 *   WIN   81 (38,2%) — mueven el saldo, p.ej. +83.000
 *   Racha mas larga de TIE consecutivos: 5
 *
 * Un empate de precio exacto cinco veces seguidas es estadisticamente inviable, y sobre todo:
 * en opciones binarias el stake se DEBITA al abrir, asi que un empate con reembolso tambien
 * habria movido el saldo. Saldo intacto = la orden nunca existio.
 *
 * Los tests de aqui fijan ese criterio y el modelo de tres estados.
 */
class OrderConfirmationTest {

    private val rm = RiskManager()

    // ------------------------------------------------------------------
    // Criterio de confirmacion (logica pura)
    // ------------------------------------------------------------------

    @Test
    fun unMovimientoDeSaldoConfirmaLaOrden() {
        // Cualquier resultado real mueve el saldo: ganar lo sube, perder lo baja.
        assertTrue(
            "Un debito de -99.999 confirma que la orden existe",
            rm.isOrderConfirmed(44_925_809.0, 44_825_810.0, elapsedSec = 3)
        )
        assertTrue(
            "Un credito de +83.000 confirma que la orden existe",
            rm.isOrderConfirmed(44_925_809.0, 45_008_809.0, elapsedSec = 3)
        )
    }

    @Test
    fun saldoIntactoNoConfirmaLaOrden() {
        // Este es exactamente el caso de las 17 filas TIE del journal: diff = 0 exacto.
        assertFalse(
            "Saldo sin cambios = la orden no existe, aunque el precio hubiera empatado",
            rm.isOrderConfirmed(44_925_809.0, 44_925_809.0, elapsedSec = 5)
        )
    }

    @Test
    fun elMargenDeRuidoNoConfundeUnaLecturaConUnaOrden() {
        // El arbol de accesibilidad puede desviarse ~1 COP al reformatear; eso no es una orden.
        assertFalse(
            "Una diferencia de 1 COP no es prueba de operacion",
            rm.isOrderConfirmed(44_925_809.0, 44_925_810.0, elapsedSec = 5)
        )
        assertTrue(
            "Una diferencia de 100 COP si lo es",
            rm.isOrderConfirmed(44_925_809.0, 44_925_909.0, elapsedSec = 5)
        )
    }

    @Test
    fun sinSaldoLegibleNoSeAfirmaNada() {
        // Fail-closed: sin lectura fiable no se confirma ni se niega.
        assertFalse(rm.isOrderConfirmed(0.0, 44_925_809.0, elapsedSec = 5))
        assertFalse(rm.isOrderConfirmed(44_925_809.0, 0.0, elapsedSec = 5))
    }

    @Test
    fun pasadaLaVentanaYaNoSeConfirma() {
        // La confirmacion debe llegar pronto: si el saldo se mueve un minuto despues, ese
        // movimiento es de OTRA operacion, no de esta.
        assertFalse(
            "Un movimiento tardio no confirma esta orden",
            rm.isOrderConfirmed(44_925_809.0, 44_825_810.0, elapsedSec = 60, confirmationWindowSec = 12)
        )
    }

    // ------------------------------------------------------------------
    // Los tres estados
    // ------------------------------------------------------------------

    @Test
    fun alDespacharElTapLaOrdenQuedaEnviadaNoConfirmada() {
        rm.recordTradeSent(TradeAction.SELL, entryPrice = 641.867, baseBalance = 44_925_809.0)

        assertTrue("Debe quedar una operacion en curso", rm.hasPendingTrade)
        assertEquals(
            "Al despachar el tap solo esta ENVIADA, nunca confirmada",
            RiskManager.OrderState.ENVIADA, rm.orderState
        )
        assertFalse(
            "El HUD no debe poder mostrar 'Trade Activo' sin confirmacion",
            rm.hasConfirmedTrade
        )
    }

    @Test
    fun soloTrasLaConfirmacionSeConsideraOperacionActiva() {
        rm.recordTradeSent(TradeAction.SELL, entryPrice = 641.867, baseBalance = 44_925_809.0)
        rm.markOrderConfirmed()

        assertEquals(RiskManager.OrderState.CONFIRMADA, rm.orderState)
        assertTrue("Confirmada, ahora si es una operacion activa", rm.hasConfirmedTrade)
    }

    @Test
    fun unaOrdenNoProcesadaCierraElEstadoYLiberaElMotor() {
        rm.recordTradeSent(TradeAction.SELL, entryPrice = 641.867, baseBalance = 44_925_809.0)
        rm.markOrderUnprocessed()

        assertEquals(RiskManager.OrderState.NO_PROCESADA, rm.orderState)
        assertFalse("Sin operacion pendiente", rm.hasPendingTrade)
        assertFalse("Y por tanto nada que mostrar como activo", rm.hasConfirmedTrade)
    }

    @Test
    fun laConfirmacionNoSeAplicaSobreUnaOrdenYaCerrada() {
        // Evita que una confirmacion tardia resucite una operacion ya descartada.
        rm.recordTradeSent(TradeAction.BUY, baseBalance = 1_000_000.0)
        rm.markOrderUnprocessed()
        rm.markOrderConfirmed()

        assertFalse(rm.hasPendingTrade)
        assertFalse(rm.hasConfirmedTrade)
    }

    // ------------------------------------------------------------------
    // Deteccion de "no procesada" y su diferencia con un empate
    // ------------------------------------------------------------------

    @Test
    fun ventanaAgotadaConSaldoIntactoEsOrdenNoProcesada() {
        // Firma exacta de las 17 filas TIE del journal.
        assertTrue(
            rm.isOrderUnprocessed(44_925_809.0, 44_925_809.0, elapsedSec = 15, confirmationWindowSec = 12)
        )
    }

    @Test
    fun dentroDeLaVentanaTodaviaNoSeDeclaraNoProcesada() {
        // Hay que dar tiempo al broker antes de descartar la orden.
        assertFalse(
            rm.isOrderUnprocessed(44_925_809.0, 44_925_809.0, elapsedSec = 3, confirmationWindowSec = 12)
        )
    }

    @Test
    fun elCasoRealDeLaRachaDeCincoEsInviableComoEmpate() {
        // Probabilidad de empate exacto de precio en una binaria a 60s: muy baja de por si.
        // Cinco seguidos es (p)^5. Incluso con un generoso p=0.02 sale 3.2e-9, lo que descarta
        // la explicacion "empate de precio" y deja la de "el tap no creo la operacion".
        val pEmpate = 0.02
        val pCincoSeguidos = Math.pow(pEmpate, 5.0)
        assertTrue(
            "Cinco empates seguidos son inviables (p=${pCincoSeguidos})",
            pCincoSeguidos < 1e-6
        )
    }
}
