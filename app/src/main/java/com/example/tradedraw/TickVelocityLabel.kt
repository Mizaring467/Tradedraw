package com.example.tradedraw

import java.util.Locale

/**
 * Presentación de la micro-velocidad de tick (Ronda 11, T5).
 *
 * Se extrae del HUD para poder probar la regla sin Android: la dirección que se muestra debe
 * derivarse SIEMPRE del signo de la velocidad medida. El caso real que motivó esto fue
 * `-13403.387/s ▲ Impulso Alcista`, donde la flecha venía de `MarketTick.isBullishImpulse`
 * (estado heredado de la ruta de visión) y el número de `velocity` (signo opuesto).
 */
enum class TickVelocityLabel {
    BULLISH,
    BEARISH,
    NEUTRAL;

    companion object {
        /** Deriva la etiqueta del signo de la velocidad. Cero es neutro. */
        fun of(velocity: Float): TickVelocityLabel = when {
            velocity > 0f -> BULLISH
            velocity < 0f -> BEARISH
            else -> NEUTRAL
        }

        /** Flecha + texto para el HUD. */
        fun text(label: TickVelocityLabel): String = when (label) {
            BULLISH -> "▲ Alcista"
            BEARISH -> "▼ Bajista"
            NEUTRAL -> "━ Neutro"
        }

        /**
         * Velocidad como porcentaje del precio por segundo.
         *
         * Normalizar es imprescindible para comparar activos: `Z-CRY/IDX` cotiza ~641 con paso de
         * tick ~1e-9, mientras un cripto real se mueve en el orden de 1e-4 relativo. Un formato
         * fijo dejaba el indicador ilegible en uno de los dos casos, así que se usa notación
         * científica por debajo del 0,01 %/s.
         */
        fun format(velocity: Float, priceRef: Double): String {
            val ref = if (priceRef > 0.0) priceRef else 1.0
            val pctPerSec = (velocity / ref) * 100.0
            return if (Math.abs(pctPerSec) >= 0.01) {
                String.format(Locale.US, "%+.2f %%/s", pctPerSec)
            } else {
                String.format(Locale.US, "%+.2e %%/s", pctPerSec)
            }
        }
    }
}
