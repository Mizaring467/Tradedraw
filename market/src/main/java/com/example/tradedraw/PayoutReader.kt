package com.example.tradedraw

import java.util.Locale

/**
 * Lectura del payout real de Binomo desde los textos del árbol de accesibilidad.
 *
 * Ronda 12 (A2): el HUD usaba `RiskManager.sorosPayoutRate` (0.85 por defecto) mientras
 * Binomo muestra el payout real en pantalla (82 %, 83 %...). El P&L del HUD salía desviado
 * ~3 puntos. El dato SÍ es legible por accesibilidad: el payout viaja como `NN%` junto al
 * nombre del activo, y como cociente `monto_botón / cantidad − 1` en los botones CALL/PUT.
 *
 * Se implementan los DOS métodos por separado y se cruzan, para poder detectar
 * discrepancias en lugar de elegir uno a ciegas.
 *
 * Objeto puro y sin dependencias de Android para poder testearlo con JUnit.
 */
object PayoutReader {

    /** Diferencia máxima tolerada entre los dos métodos antes de marcar discrepancia. */
    const val TOLERANCIA = 0.02

    /** Rango de payouts plausibles en opciones binarias. Fuera de esto, es ruido de UI. */
    const val MIN_PLAUSIBLE = 0.50
    const val MAX_PLAUSIBLE = 1.50

    /** Resultado de cruzar los dos métodos de lectura. */
    data class PayoutReading(
        /** Payout leído del nodo `NN%` junto al activo. */
        val fromPercentLabel: Double?,
        /** Payout deducido de `monto_boton / cantidad - 1`. */
        val fromButtonRatio: Double?,
        /** Valor a usar: el consenso si ambos concuerdan, o el único disponible. */
        val resolved: Double?,
        /** true si los dos métodos dieron valores y NO concuerdan dentro de [TOLERANCIA]. */
        val discrepant: Boolean
    ) {
        /** true si hay un payout utilizable. */
        val isUsable: Boolean get() = resolved != null

        /** Etiqueta de origen para el HUD: `real` si se leyó, `est.` si no. */
        val sourceLabel: String get() = if (isUsable) "real" else "est."
    }

    private val PERCENT_REGEX = Regex("^(\\d{1,3})\\s*%$")

    /**
     * Extrae el payout de un texto tipo `82%` o `83 %`.
     * Devuelve null si el texto no es un porcentaje simple o está fuera de rango plausible.
     */
    fun parsePercentLabel(raw: String?): Double? {
        val t = raw?.trim() ?: return null
        val m = PERCENT_REGEX.matchEntire(t) ?: return null
        val pct = m.groupValues[1].toIntOrNull() ?: return null
        val ratio = pct / 100.0
        return ratio.takeIf { it in MIN_PLAUSIBLE..MAX_PLAUSIBLE }
    }

    /**
     * Deduce el payout de los dos importes visibles en el botón: `montoBotón` es
     * `cantidad * (1 + payout)`, así que `payout = montoBotón / cantidad - 1`.
     *
     * Devuelve null si algún importe no es positivo o el resultado no es plausible.
     */
    fun deriveFromButtonRatio(buttonAmount: Double?, stakeAmount: Double?): Double? {
        val btn = buttonAmount ?: return null
        val stake = stakeAmount ?: return null
        if (btn <= 0.0 || stake <= 0.0) return null
        val ratio = btn / stake - 1.0
        return ratio.takeIf { it in MIN_PLAUSIBLE..MAX_PLAUSIBLE }
    }

    /**
     * Cruza los dos métodos. `resolved` es el consenso si ambos coinciden; si discrepan,
     * se prefiere el valor de la etiqueta `NN%` (es el número que Binomo declara, no una
     * deducción) pero se marca `discrepant` para que el HUD lo pueda advertir.
     */
    fun reconcile(fromPercentLabel: Double?, fromButtonRatio: Double?): PayoutReading {
        val a = fromPercentLabel
        val b = fromButtonRatio
        val discrepant = a != null && b != null && Math.abs(a - b) > TOLERANCIA
        // Con ambos valores se prefiere SIEMPRE la etiqueta `NN%`: es el numero que Binomo
        // declara, no una deduccion aritmetica de dos importes que pueden tener redondeo.
        val resolved = a ?: b
        return PayoutReading(a, b, resolved, discrepant)
    }

    /**
     * Valor a usar para calcular P&L: el payout leído si es utilizable, o el configurado.
     * Devuelve también si hay que rotular como estimado.
     *
     * @param reading lectura del árbol de accesibilidad (puede estar vacía)
     * @param configured payout configurado en la app (p.ej. 0.85)
     * @return par (payout a usar, seUsaValorEstimado)
     */
    fun effectivePayout(reading: PayoutReading, configured: Double): Pair<Double, Boolean> {
        val leido = reading.resolved
        return if (leido != null) Pair(leido, false) else Pair(configured, true)
    }

    /** Formatea el payout para el HUD: `82.0%`. */
    fun format(payout: Double): String = String.format(Locale.US, "%.1f%%", payout * 100.0)
}
