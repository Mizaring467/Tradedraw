package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 14 (B3): tests del detector de mercado inoperable.
 *
 * Cubren lo comprometido en `PREREGISTRO_R14.md`: series sinteticas, histeresis, no-spam,
 * feed caido y el caso "activo sin ticks jamas se sugiere".
 *
 * Nota de vocabulario de estos tests: "alternante" = el precio oscila cada tick (mercado vivo);
 * "plano" = el precio no cambia. El detector usa una ventana de [WINDOW_TICKS] ticks, asi que
 * **un tramo plano necesita al menos 16 ticks solo para vaciar la ventana**, antes de que el
 * reloj de sostenimiento empiece a contar. Los tests respetan esa aritmetica.
 */
class MarketInoperabilityDetectorTest {

    private val W = MarketInoperabilityDetector.WINDOW_TICKS

    private fun alternante(n: Int, base: Double = 100.0, delta: Double = 0.2) =
        (0 until n).map { if (it % 2 == 0) base else base + delta }

    private fun plano(n: Int, valor: Double = 100.0) = (0 until n).map { valor }

    /** Alimenta la serie con un tick por segundo y devuelve los instantes de ENCENDIDO. */
    private fun feed(
        d: MarketInoperabilityDetector,
        prices: List<Double>,
        startMs: Long = 0L,
        feedFresh: Boolean = true
    ): List<Long> {
        val flancos = mutableListOf<Long>()
        prices.forEachIndexed { i, p ->
            val now = startMs + i * 1000L
            if (d.onTick(p, now, feedFresh)) flancos.add(now)
        }
        return flancos
    }

    @Test
    fun elCambioDeActivoLimpiaLaReferencia() {
        // Ronda 15 / B2. La referencia es la mediana de rango del PROPIO activo. Sin limpiarla, el
        // activo nuevo se compara contra el rango del anterior. Medido en dispositivo: Z-CRY/IDX
        // ronda 2.5e-10 y ETHUSD-OTC 5e-4, cuatro ordenes de magnitud de diferencia.
        val d = MarketInoperabilityDetector()
        feed(d, alternante(200, base = 100.0, delta = 0.2))
        assertTrue("La referencia debe estar construida antes del cambio", d.lastReference > 0.0)

        d.onAssetChanged()

        assertEquals("La referencia debe quedar limpia", -1.0, d.lastReference, 1e-12)
        assertEquals("El ratio debe quedar limpio", -1.0, d.lastRatio, 1e-12)
        assertFalse("No puede quedar encendido tras limpiar", d.isInoperable)

        // Con la serie limpia, un activo de OTRA escala no debe heredar el veredicto del anterior.
        // ETH ronda 5e-4: si la referencia vieja (de escala 0.2 sobre 100) sobreviviera, este
        // activo perfectamente vivo se clasificaria contra ella.
        val flancos = feed(d, alternante(200, base = 3000.0, delta = 1.5))
        assertTrue("Un activo vivo de otra escala no debe encender el aviso", flancos.isEmpty())
        assertFalse(d.isInoperable)
    }

    @Test
    fun mercadoAmplioNoDisparaNada() {
        val d = MarketInoperabilityDetector()
        val flancos = feed(d, alternante(200))
        assertTrue("Con rango amplio no debe encenderse", flancos.isEmpty())
        assertFalse(d.isInoperable)
        assertEquals(0L, d.activationCount)
    }

    @Test
    fun rangoPlanoSostenidoEnciendeElAviso() {
        val d = MarketInoperabilityDetector()
        // 120 s de mercado vivo + suficiente tramo plano para vaciar la ventana y sostener 30 s.
        val flancos = feed(d, alternante(120) + plano(W + 35))
        assertTrue("Debe encenderse", flancos.isNotEmpty())
        assertTrue(d.isInoperable)
        assertEquals(1L, d.activationCount)
    }

    @Test
    fun laHisteresisNoEnciendeAntesDeLosSegundosExigidos() {
        // El encendido exige W ticks para vaciar la ventana + holdSeconds sostenidos.
        val insuficiente = W + 29   // 29 s < 30
        val suficiente = W + 31     // 31 s >= 30

        val d1 = MarketInoperabilityDetector(holdSeconds = 30)
        feed(d1, alternante(120) + plano(insuficiente))
        assertFalse("Con 29 s sostenidos no debe encenderse todavia", d1.isInoperable)

        val d2 = MarketInoperabilityDetector(holdSeconds = 30)
        feed(d2, alternante(120) + plano(suficiente))
        assertTrue("Con 31 s sostenidos ya debe encenderse", d2.isInoperable)
    }

    @Test
    fun laHisteresisExigeRecuperacionSostenidaParaApagar() {
        val d = MarketInoperabilityDetector(holdSeconds = 5, releaseSeconds = 10, cooldownSeconds = 0)
        feed(d, alternante(120) + plano(W + 10))
        assertTrue("Debe estar encendido", d.isInoperable)

        // Recuperacion: basta con que vuelva el rango + 10 s SOSTENIDOS de rango recuperado.
        // Con 5 s de recuperacion no puede haberse liberado (releaseSeconds = 10).
        // El reloj es CONTINUO: un hueco > 5 s se interpretaria como feed caido y reiniciaria
        // la serie, con lo que el test no estaria midiendo la histeresis.
        val inicio = 146_000L
        feed(d, alternante(5), startMs = inicio)
        assertTrue("5 s de recuperacion no bastan para apagar", d.isInoperable)

        // Con 30 s sostenidos si debe apagarse.
        feed(d, alternante(30), startMs = inicio + 5_000L)
        assertFalse("La recuperacion sostenida debe apagar el aviso", d.isInoperable)
    }

    @Test
    fun elEnfriamientoEvitaElParpadeo() {
        val d = MarketInoperabilityDetector(holdSeconds = 2, releaseSeconds = 2, cooldownSeconds = 120)
        feed(d, alternante(120) + plano(W + 10))
        assertTrue(d.isInoperable)
        // Recuperacion suficiente para apagar -> arranca el enfriamiento. Reloj continuo.
        var t = 146_000L
        feed(d, alternante(30), startMs = t)
        assertFalse("Debe haberse apagado", d.isInoperable)

        val encendidosAntes = d.activationCount
        // Vuelve a estar plano el tiempo de sobra, pero dentro del enfriamiento de 120 s.
        t += 30_000L
        feed(d, plano(W + 40), startMs = t)
        assertEquals("El enfriamiento debe impedir un nuevo encendido",
            encendidosAntes, d.activationCount)
        assertFalse(d.isInoperable)
    }

    @Test
    fun feedCaidoNoEsMercadoInoperable() {
        val d = MarketInoperabilityDetector(holdSeconds = 5, releaseSeconds = 1, cooldownSeconds = 0)
        feed(d, alternante(120))
        // Precio clavado pero feed marcado como NO fresco: es FEED_MUDO, otro caso distinto.
        val flancos = feed(d, plano(120), startMs = 121_000L, feedFresh = false)
        assertTrue("Un feed caido no puede declararse inoperable", flancos.isEmpty())
        assertFalse(d.isInoperable)
    }

    @Test
    fun unHuecoLargoDeFeedReiniciaLaSerie() {
        val d = MarketInoperabilityDetector()
        feed(d, alternante(120))
        // Diez minutos sin ticks: no es mercado lateral, es un hueco de feed.
        d.onTick(999.0, 800_000L, true)
        assertEquals("Tras un hueco la serie se reinicia", -1.0, d.lastRatio, 1e-12)
        assertFalse(d.isInoperable)
    }

    @Test
    fun laReferenciaNoSeContaminaConElPropioEstancamiento() {
        // Regresion del bug detectado en estos mismos tests: si la mediana de referencia incluyera
        // las muestras del tramo plano, M caeria a 0, K*M valdria 0 y la condicion `R < K*M` se
        // volveria imposible justo cuando el aviso tiene que dispararse.
        val d = MarketInoperabilityDetector()
        feed(d, alternante(120) + plano(W + 35))
        assertTrue("El aviso debe haber disparado", d.isInoperable)
        assertTrue("La referencia debe seguir describiendo el mercado vivo, no el plano",
            d.lastReference > 0.0)
    }

    @Test
    fun elAvisoNuncaNombraUnActivoCandidato() {
        // B2b: sin acumulador de otros activos, el aviso no puede sugerir ninguno. El detector ni
        // siquiera conoce nombres de activos: es una garantia estructural, no una convencion.
        val campos = MarketInoperabilityDetector::class.java.declaredFields.map { it.name.lowercase() }
        for (campo in campos) {
            // Se compara por token completo: "prices" contiene la subcadena "ric" pero no es un RIC.
            val tokens = campo.split("_", "-").toSet()
            assertFalse("El detector no debe conocer activos: $campo",
                tokens.any { it == "asset" || it == "ric" || it == "candidate" || it == "symbol" })
        }
    }

    @Test
    fun losValoresPorDefectoSonLosDelPreregistro() {
        val d = MarketInoperabilityDetector()
        assertEquals("K", 0.25, d.k, 1e-12)
        assertEquals("N", 30, d.holdSeconds)
        assertEquals("histéresis de recuperación", 10, d.releaseSeconds)
        assertEquals("enfriamiento", 120, d.cooldownSeconds)
    }

    @Test
    fun precioInvalidoSeIgnora() {
        val d = MarketInoperabilityDetector()
        assertFalse(d.onTick(0.0, 1000L, true))
        assertFalse(d.onTick(-5.0, 2000L, true))
    }
}
