package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regla de presentación de la micro-velocidad (Ronda 11, T5).
 *
 * El HUD llegó a mostrar `-13403.387/s ▲ Impulso Alcista`: la flecha venía de
 * `MarketTick.isBullishImpulse` y el número de `velocity`, con signos opuestos. La etiqueta
 * direccional debe derivarse SIEMPRE del signo de la velocidad, que es la única magnitud
 * medida en ese renglón.
 */
class TickVelocityLabelTest {

    @Test
    fun velocidadNegativa_noPuedeEtiquetarseComoAlcista() {
        assertEquals(TickVelocityLabel.BEARISH, TickVelocityLabel.of(-13403.387f))
    }

    @Test
    fun velocidadPositiva_esAlcista() {
        assertEquals(TickVelocityLabel.BULLISH, TickVelocityLabel.of(10922.404f))
    }

    @Test
    fun velocidadCero_esNeutra() {
        assertEquals(TickVelocityLabel.NEUTRAL, TickVelocityLabel.of(0f))
    }

    @Test
    fun velocidadNegativaMinuscula_esBajista() {
        // El índice sintético se mueve en el orden de 1e-9: el signo debe sobrevivir a la magnitud.
        assertEquals(TickVelocityLabel.BEARISH, TickVelocityLabel.of(-1.0e-9f))
    }

    @Test
    fun formatoUsaPuntoDecimal_noComa() {
        // Locale.US obligatorio: con locale español el separador decimal es ',' y el parseo del
        // texto del HUD se rompe (ya ocurrió en R11 con los precios).
        // 0.641 / 641.0 * 100 = 0.10 %/s exacto.
        assertEquals("+0.10 %/s", TickVelocityLabel.format(0.641f, 641.0))
    }

    @Test
    fun formatoAdaptaNotacionSegunMagnitud() {
        // Índice sintético: paso sub-micro -> notación científica legible.
        assertEquals("-1.56e-12 %/s", TickVelocityLabel.format(-1.0e-11f, 641.0))
        // Cripto real: magnitud apreciable -> formato fijo.
        assertEquals("+1.00 %/s", TickVelocityLabel.format(1.0f, 100.0))
    }
}
