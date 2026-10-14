package com.example.tradedraw

/**
 * Ronda 14 (B1): detector de "mercado inoperable" sobre el activo activo.
 *
 * ## Que mide y por que asi
 *
 * Compara el rango relativo de los ultimos 16 ticks (`R`) contra la mediana de ese mismo rango
 * en los 60 minutos previos DEL PROPIO ACTIVO (`M`, referencia auto-relativa). Declara inoperable
 * cuando `R < K * M` de forma sostenida `N` segundos.
 *
 * Se llego a esta definicion midiendo, antes de escribir el codigo, por que NO valian las
 * alternativas (ver `workflows/PREREGISTRO_R14.md`):
 *
 * - **Umbral absoluto** (`R < 1e-6`): las escalas difieren cuatro ordenes de magnitud entre
 *   activos (ETH 3,4e-04 vs Z-CRY 2,6e-10) y no produce rachas sostenidas.
 * - **`R < 1e-3`**: si hay rachas, pero marcaria ETH inoperable el 95,5 % del tiempo.
 * - **Precio congelado**: con feed vivo el precio nunca queda quieto 1 min (racha maxima 33 s en
 *   ETH). Ese caso ya lo cubre `FEED_MUDO`, que es otra cosa.
 *
 * ## Lo que este detector NO hace
 *
 * - **No cambia de activo.** Ni sugiere uno concreto: emite un aviso textual y contadores.
 * - **No toca los vetos V1/V2** ni relaja ningun umbral de trading.
 * - **No confunde feed caido con mercado muerto.** Si el feed no esta fresco, el detector no
 *   opina: de eso avisa `FEED_MUDO`.
 *
 * Objeto puro y sin estado global: se puede alimentar con series sinteticas o con un replay real.
 */
class MarketInoperabilityDetector(
    /** Factor sobre la mediana de referencia: por debajo de `K * M` el rango es anormalmente bajo. */
    val k: Double = K_DEFAULT,
    /** Segundos que debe sostenerse la condicion para declarar inoperable. */
    val holdSeconds: Int = HOLD_SECONDS_DEFAULT,
    /** Segundos que debe sostenerse la recuperacion para retirar el aviso (histeresis). */
    val releaseSeconds: Int = RELEASE_SECONDS_DEFAULT,
    /** Segundos de enfriamiento tras retirar el aviso, antes de poder volver a encenderlo. */
    val cooldownSeconds: Int = COOLDOWN_SECONDS_DEFAULT,
    /** Ventana de ticks del rango. */
    private val windowTicks: Int = WINDOW_TICKS
) {
    companion object {
        /** K elegido a priori: 0,25 produce 0,26 avisos/hora agregados en el dia de referencia. */
        const val K_DEFAULT = 0.25
        /** N elegido a priori: 30 s sostenidos. */
        const val HOLD_SECONDS_DEFAULT = 30
        /** Histéresis: recuperar exige solo 10 s, apagar exigio 30 s. */
        const val RELEASE_SECONDS_DEFAULT = 10
        /** Enfriamiento anti-spam tras apagar. */
        const val COOLDOWN_SECONDS_DEFAULT = 120
        /** Ventana del rango relativo, la misma que el veto V2. */
        const val WINDOW_TICKS = 16
        /** Minutos de historia propia para fijar la mediana de referencia. */
        const val REFERENCE_MINUTES = 60
        /** Maximo intervalo entre ticks para considerar el feed vivo, en ms. */
        const val FEED_ALIVE_MAX_MS = 5000L
    }

    private val prices = ArrayDeque<Double>()
    private val priceTimestamps = ArrayDeque<Long>()

    /** Muestras de `R` con su instante, para la mediana de referencia de los ultimos 60 min. */
    private val ratioSamples = ArrayDeque<Pair<Long, Double>>()

    /** `true` mientras la condicion de rango bajo se esta cumpliendo. */
    private var lowSinceMs: Long = 0L
    /** `true` mientras la condicion de recuperacion se esta cumpliendo. */
    private var highSinceMs: Long = 0L

    private var cooldownUntilMs: Long = 0L

    /** Estado visible: `true` si ahora mismo hay aviso de inoperable. */
    var isInoperable: Boolean = false
        private set

    /** Numero de veces que el aviso se ha ENCENDIDO (no de evaluaciones). */
    var activationCount: Long = 0L
        private set

    /** Ultimo `R` medido; `-1.0` si aun no hay ventana completa. */
    var lastRatio: Double = -1.0
        private set

    /** Ultima mediana de referencia; `-1.0` si aun no hay historia suficiente. */
    var lastReference: Double = -1.0
        private set

    /** Segundos que lleva el aviso encendido; 0 si esta apagado. */
    fun inoperableSeconds(nowMs: Long): Int =
        if (!isInoperable || lowSinceMs <= 0L) 0 else ((nowMs - lowSinceMs) / 1000L).toInt()

    /**
     * Alimenta un tick y evalua el estado.
     *
     * @param feedFresh si el feed esta fresco. Con `false` NO se declara inoperable: un feed caido
     *   es `FEED_MUDO`, no un mercado lateral, y mezclarlos produciria avisos falsos.
     * @return `true` en el instante exacto en que el aviso se ENCIENDE (flanco de subida). El
     *   llamador usa eso para registrar un evento y no inundar `/events` en cada tick.
     */
    fun onTick(price: Double, nowMs: Long, feedFresh: Boolean = true): Boolean {
        if (price <= 0.0) return false

        // Un salto de activo no es mercado: la serie se reinicia entera.
        if (priceTimestamps.isNotEmpty() && nowMs - priceTimestamps.last() > FEED_ALIVE_MAX_MS) {
            reset()
            priceTimestamps.addLast(nowMs)
            prices.addLast(price)
            return false
        }

        priceTimestamps.addLast(nowMs)
        prices.addLast(price)
        while (prices.size > windowTicks) {
            prices.removeFirst()
            priceTimestamps.removeFirst()
        }
        if (prices.size < windowTicks) return false

        val min = prices.min()
        val last = prices.last()
        if (last <= 0.0) return false
        val ratio = (prices.max() - min) / last
        lastRatio = ratio
        ratioSamples.addLast(nowMs to ratio)
        // Se conserva solo la historia util: algo mas de la ventana de referencia.
        val cutoff = nowMs - (REFERENCE_MINUTES * 60_000L)
        while (ratioSamples.isNotEmpty() && ratioSamples.first().first < cutoff) {
            ratioSamples.removeFirst()
        }

        if (!feedFresh) {
            // Feed caido: no es mercado inoperable. Se mantiene el estado actual sin alimentarlo.
            return false
        }

        val reference = medianReference(nowMs)
        lastReference = reference
        if (reference <= 0.0) return false

        val isLow = ratio < k * reference

        if (isInoperable) {
            // Para APAGAR hace falta que el rango se recupere de forma sostenida.
            if (isLow) {
                highSinceMs = 0L
            } else {
                if (highSinceMs == 0L) highSinceMs = nowMs
                if ((nowMs - highSinceMs) / 1000L >= releaseSeconds) {
                    isInoperable = false
                    lowSinceMs = 0L
                    highSinceMs = 0L
                    cooldownUntilMs = nowMs + cooldownSeconds * 1000L
                }
            }
            return false
        }

        // Para ENCENDER hace falta rango bajo sostenido, sin enfriamiento pendiente.
        if (nowMs < cooldownUntilMs) return false
        if (!isLow) {
            lowSinceMs = 0L
            return false
        }
        if (lowSinceMs == 0L) lowSinceMs = nowMs
        if ((nowMs - lowSinceMs) / 1000L >= holdSeconds) {
            isInoperable = true
            highSinceMs = 0L
            activationCount++
            return true
        }
        return false
    }

    /**
     * Mediana de las muestras de `R` en la ventana de referencia. `-1.0` si no hay suficientes.
     *
     * **Las muestras de los ultimos [holdSeconds] se excluyen a proposito.** Sin esa exclusion el
     * propio estancamiento se filtra en su referencia: al cabo de unos segundos de rango plano la
     * mediana cae a cero, `K * M` pasa a valer cero, la condicion `R < K*M` deja de cumplirse y el
     * detector se apaga solo justo cuando deberia avisar. La referencia describe "como se movia
     * esto ANTES", no "como se mueve ahora".
     */
    private fun medianReference(nowMs: Long): Double {
        val guardMs = nowMs - holdSeconds * 1000L
        val validas = ratioSamples.filter { it.first <= guardMs }.map { it.second }
        if (validas.size < windowTicks) return -1.0
        val sorted = validas.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    private fun reset() {
        prices.clear()
        priceTimestamps.clear()
        ratioSamples.clear()
        isInoperable = false
        lowSinceMs = 0L
        highSinceMs = 0L
        cooldownUntilMs = 0L
        lastRatio = -1.0
        lastReference = -1.0
    }

    /**
     * Reinicia la serie porque el ACTIVO ha cambiado. Ronda 15 / B2.
     *
     * La referencia es la mediana de los ultimos [REFERENCE_MINUTES] del propio activo. Si no se
     * limpia al cambiar de activo, la mediana mezcla dos ESCALAS distintas (medido: `Z-CRY/IDX`
     * ronda 2.5e-10 y `ETHUSD-OTC` 5e-4, cuatro ordenes de magnitud) y el detector compara peras
     * con manzanas: el activo nuevo queda clasificado contra el rango del viejo.
     *
     * Es el mismo fallo que R12/B4 corrigio para `recentTicks` en el cliente WebSocket: el estado
     * que describe "como se movia esto" es POR ACTIVO y no sobrevive a un cambio de activo.
     */
    fun onAssetChanged() = reset()
}
