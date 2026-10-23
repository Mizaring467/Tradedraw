package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R24/B4: diagnostico de `NO_PROCESADA`.
 *
 * QUE FIJA. El criterio V4 del preregistro R24 exige que cada ocurrencia quede registrada con una
 * causa de la taxonomia CERRADA de §3 V4 y con la duracion maxima de la condicion. Antes de esta
 * instrumentacion la unica informacion era un literal fijo y ni siquiera habia duracion.
 *
 * Se prueba la funcion de clasificacion (pura) y el ciclo entrada/salida del cronometro. El
 * contador y el cronometro son monotonos: no se reinician con `resetStats`, porque V4 mide deltas
 * dentro de una ventana.
 */
class NoProcesadaDiagnosticsTest {

    private fun rm() = RiskManager()

    // -----------------------------------------------------------------------
    // Taxonomia cerrada
    // -----------------------------------------------------------------------

    @Test
    fun laTaxonomiaTieneExactamenteLasCincoEtiquetasDelPreregistro() {
        assertEquals(
            listOf(
                "SIN_MOVIMIENTO_SALDO",
                "TAP_NO_DESPACHADO",
                "TAP_RECHAZADO_BROKER",
                "FEED_MUDO",
                "NO ATRIBUIDA"
            ),
            RiskManager.NoProcesadaCausa.TODAS
        )
    }

    @Test
    fun tapNoDespachadoGanaSobreCualquierOtraCausa() {
        // Si el gesto no salio, el saldo no podia moverse: atribuirlo al saldo describiria el
        // sintoma, no la causa.
        assertEquals(
            "TAP_NO_DESPACHADO",
            rm().clasificarCausaNoProcesada(tapDespachado = false, feedFresco = false, saldoObservable = false)
        )
    }

    @Test
    fun feedMudoSeClasificaAntesQueElSaldo() {
        assertEquals(
            "FEED_MUDO",
            rm().clasificarCausaNoProcesada(tapDespachado = true, feedFresco = false, saldoObservable = true)
        )
    }

    @Test
    fun sinLecturaDeSaldoNoSeAtribuyeAlSaldo() {
        assertEquals(
            "NO ATRIBUIDA",
            rm().clasificarCausaNoProcesada(tapDespachado = true, feedFresco = true, saldoObservable = false)
        )
    }

    @Test
    fun conTapFeedYSaldoElCasoEsSinMovimientoDeSaldo() {
        assertEquals(
            "SIN_MOVIMIENTO_SALDO",
            rm().clasificarCausaNoProcesada(tapDespachado = true, feedFresco = true, saldoObservable = true)
        )
    }

    // -----------------------------------------------------------------------
    // Cronometro y contador
    // -----------------------------------------------------------------------

    @Test
    fun sinOcurrenciaLaDuracionEsCero() {
        val r = rm()
        assertEquals(0L, r.noProcesadaDuracionMaxMs)
        assertEquals(0L, r.noProcesadaDuracionActualMs)
        assertEquals(0, r.noProcesadaOcurrencias)
        assertEquals("", r.noProcesadaCausa)
    }

    @Test
    fun laEntradaArrancaElCronometroYCuentaLaOcurrencia() {
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")

        assertEquals(1, r.noProcesadaOcurrencias)
        assertEquals("SIN_MOVIMIENTO_SALDO", r.noProcesadaCausa)
        assertTrue("el cronometro debe estar abierto", r.noProcesadaInicioMs > 0L)
    }

    @Test
    fun laSalidaCierraElCronometroYFijaElMaximo() {
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")
        r.cerrarNoProcesada()

        assertEquals("el cronometro queda cerrado", 0L, r.noProcesadaInicioMs)
        assertEquals("sin ocurrencia activa", 0L, r.noProcesadaDuracionActualMs)
        assertTrue("la duracion medida no puede ser negativa", r.noProcesadaDuracionMaxMs >= 0L)
    }

    @Test
    fun cerrarSinEntradaPreviaNoInventaDuracion() {
        // Tras reiniciar el proceso no hay entrada abierta: una salida del estado no puede
        // fabricar una duracion que no se midio.
        val r = rm()
        r.cerrarNoProcesada()
        assertEquals(0L, r.noProcesadaDuracionMaxMs)
    }

    @Test
    fun unaCausaFueraDeLaTaxonomiaSeRegistraComoNoAtribuida() {
        val r = rm()
        r.registrarNoProcesada("CAUSA_INVENTADA")
        assertEquals("NO ATRIBUIDA", r.noProcesadaCausa)
    }

    @Test
    fun elMaximoNoDecreceEntreOcurrencias() {
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")
        Thread.sleep(5)
        r.cerrarNoProcesada()
        val primero = r.noProcesadaDuracionMaxMs

        r.registrarNoProcesada("FEED_MUDO")
        r.cerrarNoProcesada()

        assertTrue("el maximo es monotono", r.noProcesadaDuracionMaxMs >= primero)
        assertEquals("la causa es la de la ultima ocurrencia", "FEED_MUDO", r.noProcesadaCausa)
        assertEquals(2, r.noProcesadaOcurrencias)
    }

    @Test
    fun elContadorSobreviveAlReinicioDelMarcador() {
        // V4 mide ocurrencias dentro de una ventana: si el contador se reiniciara con el marcador,
        // el denominador cambiaria a mitad de la medicion.
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")
        r.resetStats()
        assertEquals("resetStats no puede borrar la evidencia de V4", 1, r.noProcesadaOcurrencias)
    }

    @Test
    fun laDuracionMaximaIncluyeLaOcurrenciaTodaviaAbierta() {
        // Caso W17: la ventana termina con el motor AUN en NO_PROCESADA (>= 590 s medidos). Si el
        // maximo solo se fijara al cerrar, /status reportaria 0 ms con la condicion activa.
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")
        Thread.sleep(20)

        assertTrue(
            "con la ocurrencia abierta el maximo no puede ser 0",
            r.noProcesadaDuracionMaxMs >= 15L
        )
        assertTrue(
            "el maximo debe cubrir al menos la duracion actual",
            r.noProcesadaDuracionMaxMs >= r.noProcesadaDuracionActualMs
        )
    }

    @Test
    fun elMaximoAbiertoNoSePierdeAlCerrar() {
        val r = rm()
        r.registrarNoProcesada("SIN_MOVIMIENTO_SALDO")
        Thread.sleep(20)
        val conAbierta = r.noProcesadaDuracionMaxMs
        r.cerrarNoProcesada()

        assertTrue("cerrar no puede reducir el maximo", r.noProcesadaDuracionMaxMs >= conAbierta)
        assertEquals("ya cerrada, la duracion actual es 0", 0L, r.noProcesadaDuracionActualMs)
    }
}
