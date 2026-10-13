package com.example.tradedraw

/**
 * Estado ITM/OTM de una opción binaria, calculado SIEMPRE en dominio de precio.
 *
 * Contrato de opciones binarias: CALL gana si el precio de cierre está estrictamente por encima
 * del strike de entrada; PUT gana si está estrictamente por debajo. El empate exacto no gana.
 */
enum class TradeMoneyness { ITM, OTM, TIE }

/**
 * Mapea precio del broker ↔ coordenada Y de pantalla para dibujar el STRIKE_PRICE_LINE.
 *
 * Ronda 11 — causa raíz: el motor pasaba a `drawTradeEntry` la Y del TAP con un `coerceIn` al
 * centro de la pantalla, y `updateTradeEntryLiveStatus` comparaba Y de visión (retirada). Resultado:
 * una línea punteada verde a ~640 px del último precio, sin corresponder a ningún precio real, y un
 * PUT marcado [ITM] con el precio por encima del strike (imposible en opciones binarias).
 *
 * La calibración se aprende de pares (precio, Y) reales observados en el eje del gráfico. Sin al
 * menos dos puntos de escalas distintas NO se inventa una Y: se informa `isCalibrated = false` y el
 * llamador dibuja un indicador anclado al borde.
 */
object PriceViewportMapper {

    /** Puntos de calibración (precio, Y de pantalla). Se conservan los dos más separados. */
    @Volatile
    private var pointA: Pair<Double, Float>? = null

    @Volatile
    private var pointB: Pair<Double, Float>? = null

    /**
     * Rango de precios observado (mín/máx de los últimos ticks) y viewport donde se proyecta.
     * ponytail: el eje de precios del gráfico NO aparece en el árbol de accesibilidad de Binomo
     * (verificado con `uiautomator dump`), así que no existe calibración precio↔Y exacta en headless.
     * Hasta que haya calibración real ([observe]), se usa este proxy: el rango de precios reciente se
     * proyecta sobre la altura del gráfico. No es pixel-exacto, pero es monótono y derivado del
     * precio, así que la línea del strike ya no aparece a cientos de píxeles del precio real.
     * Sustituir por calibración del eje cuando exista una fuente de pares (precio, Y).
     */
    @Volatile
    private var rangeMin: Double = 0.0

    @Volatile
    private var rangeMax: Double = 0.0

    @Volatile
    private var viewportTopY: Float = 0f

    @Volatile
    private var viewportBottomY: Float = 0f

    /** Separación mínima en Y (px) entre dos puntos para que la interpolación sea fiable. */
    private const val MIN_Y_SEPARATION_PX = 60f

    val isCalibrated: Boolean
        get() {
            val a = pointA ?: return false
            val b = pointB ?: return false
            return Math.abs(a.second - b.second) >= MIN_Y_SEPARATION_PX && Math.abs(a.first - b.first) > 1e-9
        }

    /**
     * Fija el rango de precios visible y el viewport vertical donde se proyecta. Es el fallback
     * cuando no hay calibración real del eje.
     */
    @Synchronized
    fun setVisiblePriceRange(minPrice: Double, maxPrice: Double, topY: Float, bottomY: Float) {
        if (minPrice <= 0.0 || maxPrice <= minPrice || bottomY <= topY) return
        rangeMin = minPrice
        rangeMax = maxPrice
        viewportTopY = topY
        viewportBottomY = bottomY
    }

    /**
     * Registra un par (precio, Y) observado en el eje del gráfico. Descarta observaciones
     * degeneradas (precio no positivo, Y no finita) y conserva los dos puntos más separados en Y.
     */
    @Synchronized
    fun observe(price: Double, screenY: Float) {
        if (price <= 0.0 || !screenY.isFinite()) return
        val candidate = price to screenY
        val a = pointA
        val b = pointB
        when {
            a == null -> pointA = candidate
            b == null -> {
                if (Math.abs(a.second - screenY) >= MIN_Y_SEPARATION_PX) pointB = candidate else pointA = candidate
            }
            Math.abs(screenY - a.second) > Math.abs(b.second - a.second) -> {
                // El nuevo punto amplía la escala: sustituye al más cercano en Y.
                if (Math.abs(screenY - a.second) >= Math.abs(screenY - b.second)) pointB = candidate
                else pointA = candidate
            }
        }
    }

    /**
     * Convierte [price] a Y de pantalla. Devuelve null si no hay calibración válida: el llamador
     * NO debe inventar una Y (ese fue el defecto que produjo la línea fantasma).
     */
    fun priceToY(price: Double): Float? {
        val a = pointA
        val b = pointB
        val dy = if (a != null && b != null) b.second - a.second else 0f
        if (a != null && b != null && Math.abs(dy) >= MIN_Y_SEPARATION_PX) {
            val dp = b.first - a.first
            if (Math.abs(dp) > 1e-9) {
                val slope = dy / dp.toFloat()
                if (slope.isFinite()) return a.second + ((price - a.first).toFloat() * slope)
            }
        }
        // Fallback: proyección lineal del rango visible. Precio mayor => Y menor (arriba).
        if (rangeMax <= rangeMin || viewportBottomY <= viewportTopY) return null
        val ratio = ((rangeMax - price) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0)
        return viewportTopY + (ratio * (viewportBottomY - viewportTopY)).toFloat()
    }

    /** Invalida la calibración (cambio de activo: la escala de precio cambia por completo). */
    @Synchronized
    fun reset() {
        pointA = null
        pointB = null
        rangeMin = 0.0
        rangeMax = 0.0
        viewportTopY = 0f
        viewportBottomY = 0f
    }

    /** Solo para tests: fija dos puntos de calibración conocidos. */
    @Synchronized
    internal fun setCalibrationForTests(priceTop: Double, yTop: Float, priceBottom: Double, yBottom: Float) {
        pointA = priceTop to yTop
        pointB = priceBottom to yBottom
    }
}

/**
 * Resuelve la geometría del strike a partir del precio de entrada y del viewport calibrado.
 *
 * @param y Y de pantalla donde dibujar la línea del strike.
 * @param clamped true si el precio de entrada está fuera del viewport y la línea se ancla al borde.
 * @param arrow "↑" si el strike real está por encima del borde visible, "↓" si está por debajo, "" si no.
 */
data class StrikeGeometry(val y: Float, val clamped: Boolean, val arrow: String)

/**
 * Cálculos puros de strike y moneyness, en dominio de precio. Sin Android, testeable.
 */
object StrikeCalculator {

    /**
     * ¿La posición va ganando? Comparación estricta de precio, como liquida el broker.
     */
    fun moneyness(action: TradeAction, entryPrice: Double, currentPrice: Double): TradeMoneyness {
        if (entryPrice <= 0.0 || currentPrice <= 0.0) return TradeMoneyness.TIE
        if (currentPrice > entryPrice) return if (action == TradeAction.BUY) TradeMoneyness.ITM else TradeMoneyness.OTM
        if (currentPrice < entryPrice) return if (action == TradeAction.BUY) TradeMoneyness.OTM else TradeMoneyness.ITM
        return TradeMoneyness.TIE
    }

    /**
     * P&L estimado real de la posición: stake × payout si ITM, −stake si OTM, 0 en empate.
     * Reemplaza los literales "+$$$" y "$0" del lienzo.
     */
    fun estimatedPnl(moneyness: TradeMoneyness, stake: Double, payoutRate: Double): Double = when (moneyness) {
        TradeMoneyness.ITM -> stake * payoutRate
        TradeMoneyness.OTM -> -stake
        TradeMoneyness.TIE -> 0.0
    }

    /**
     * Etiqueta del strike con cifras reales. Reemplaza los literales "+$$$" y "$0" que mostraba el
     * lienzo (no eran dinero, eran marcadores de posición).
     *
     * @param pnl resultado estimado ya calculado por [estimatedPnl].
     * @param entryPrice precio de entrada, que se muestra siempre para poder auditar la línea.
     */
    fun strikeLabel(
        action: TradeAction,
        moneyness: TradeMoneyness,
        entryPrice: Double,
        pnl: Double
    ): String {
        val side = if (action == TradeAction.BUY) "CALL ▲" else "PUT ▼"
        val state = when (moneyness) {
            TradeMoneyness.ITM -> "ITM"
            TradeMoneyness.OTM -> "OTM"
            TradeMoneyness.TIE -> "ABIERTO"
        }
        val entry = if (entryPrice > 0.0) formatPrice(entryPrice) else "—"
        val money = when {
            moneyness == TradeMoneyness.TIE -> "—"
            pnl >= 0.0 -> "+${formatMoney(pnl)}"
            else -> "-${formatMoney(-pnl)}"
        }
        return "$side [$state] $entry · $money"
    }

    /**
     * Formatea un precio conservando la precisión útil del activo (BTC ~2, IDX ~6 decimales).
     * Locale.US obligatorio: con locale español el separador decimal sería "," y la etiqueta del
     * gráfico dejaría de ser legible/parseable.
     */
    fun formatPrice(price: Double): String = when {
        price >= 1000.0 -> String.format(java.util.Locale.US, "%.2f", price)
        price >= 1.0 -> String.format(java.util.Locale.US, "%.4f", price)
        else -> String.format(java.util.Locale.US, "%.6f", price)
    }

    /** Formatea un importe sin símbolo de moneda (la unidad la fija el broker). */
    fun formatMoney(amount: Double): String = String.format(java.util.Locale.US, "%.2f", amount)

    /**
     * Sitúa la línea del strike dentro del viewport visible.
     *
     * Si el precio de entrada cae fuera del rango visible, la línea NO se dibuja en un precio falso:
     * se ancla al borde correspondiente y se marca con una flecha que apunta hacia el strike real.
     */
    fun strikeGeometry(
        entryPrice: Double,
        priceToY: (Double) -> Float?,
        viewportTop: Float,
        viewportBottom: Float
    ): StrikeGeometry? {
        if (entryPrice <= 0.0 || viewportBottom <= viewportTop) return null
        val y = priceToY(entryPrice) ?: return null
        return when {
            y < viewportTop -> StrikeGeometry(viewportTop, clamped = true, arrow = "↑")
            y > viewportBottom -> StrikeGeometry(viewportBottom, clamped = true, arrow = "↓")
            else -> StrikeGeometry(y, clamped = false, arrow = "")
        }
    }
}
