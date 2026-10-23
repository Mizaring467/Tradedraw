package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class MoneyManagementMode {
    MARTINGALE,
    SOROS_COMPOUNDING,
    FIXED_AMOUNT
}

class RiskManager(context: Context? = null) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences("TradeDraw_RiskConfig", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

    init {
        try {
            prefs?.edit()?.remove("session_total_wins")?.remove("session_total_losses")?.apply()
        } catch (_: Exception) {}
    }

    companion object {
        const val MAX_PENDING_TRADE_TIMEOUT_SEC = 85L
        const val DEFAULT_COOLDOWN_SECONDS = 10

        /**
         * R23A Iter 2: stake EFECTIVO de la orden, en la misma unidad que el broker.
         *
         * HALLAZGO QUE LO MOTIVA (OpenCode, iter 1). `/status.risk` reportaba
         * `investmentAmount = 1.0` y martingala `[M0 | $1.0]` mientras la UI de Binomo tenia
         * `observedOrderAmount = 100.000 COP`. El bot pulsa los botones BUY/SELL sin tocar el campo
         * de inversion, asi que el broker debita y liquida con la cantidad de PANTALLA. Resultado
         * medido: perdida de -100.000 COP y ganancia de +82.000 COP, coherentes con 100.000 COP por
         * operacion, no con 1.0. El modelado de riesgo y el journal quedaban desacoplados de la
         * fisica del trade.
         *
         * Criterio: si la pantalla declara una cantidad (`observedOrderAmount > 0`), ESA es la
         * cantidad que se opera. `observedOrderAmount <= 0` significa "aun no leida" (el sondeo de
         * nodos puede no haber pasado) y NO "stake cero", asi que se cae al valor calculado por la
         * politica de money management. Un cero contable aqui desactivaria el modelo de riesgo.
         *
         * `observedOrderAmount` se redondea a entero porque Binomo maneja la cantidad como entero de
         * COP: en el caso medido el valor llega como 100000.0 exacto y no queremos arrastrar ruido de
         * coma flotante al journal.
         */
        fun stakeEfectivo(observedOrderAmount: Double, stakeCalculado: Double): Double =
            if (observedOrderAmount > 0.0) observedOrderAmount else stakeCalculado

        const val DEFAULT_LOSS_COOLDOWN_SECONDS = 180
        const val DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS = 35 // Cooldown en modo continuo/YOLO (30 a 45s max)
        const val EXTENDED_LOSS_COOLDOWN_SECONDS = 180 // Pausa de 3m tras 2 derrotas consecutivas para salir de rango
        const val DEFAULT_STOP_LOSS_STREAK = 3
        const val DEFAULT_TAKE_PROFIT_WINS = 20 // 20 victorias por bloque (0 = Ilimitado)
        const val DEFAULT_MAX_MARTINGALE_LEVEL = 1
        const val DEFAULT_MARTINGALE_MULTIPLIER = 2.0f
        const val SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE = 0.85f // Umbral A+ para Martingala M1 (85%)
        const val SNIPER_MAX_LOSSES = 2
        const val SNIPER_TAKE_PROFIT_WINS = 2
        const val SNIPER_MAX_SESSION_TRADES = 3

        /**
         * R24/B1.a: cota del registro de tradeIds liquidados. A 1 trade/minuto son ~8 horas de
         * sesion continua; al alcanzarla se vacia entero, que es aceptable porque el riesgo real
         * (doble conteo) ocurre en la ventana de segundos de una misma liquidacion.
         */
        const val MAX_LIQUIDATED_TRADE_IDS = 512
    }

    @Volatile
    var sessionPeakBalance: Double = 0.0

    @Volatile
    var trailingProfitLockTriggered: Boolean = false

    @Volatile
    var trailingProfitRetracementRatio: Float = prefs?.getFloat("trailing_profit_retrace", 0.40f) ?: 0.40f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("trailing_profit_retrace", value)?.apply()
        }

    @Volatile
    var minPeakProfitToLock: Double = prefs?.getFloat("min_peak_profit_lock", 80000f)?.toDouble() ?: 80000.0
        set(value) {
            field = value
            prefs?.edit()?.putFloat("min_peak_profit_lock", value.toFloat())?.apply()
        }

    @Volatile
    var selectiveM1MinConfidence: Float = prefs?.getFloat("selective_m1_min_confidence", SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE) ?: SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE
        set(value) {
            field = value
            prefs?.edit()?.putFloat("selective_m1_min_confidence", value)?.apply()
        }

    @Volatile
    var maxMartingaleLevel: Int = prefs?.getInt("max_martingale", DEFAULT_MAX_MARTINGALE_LEVEL) ?: DEFAULT_MAX_MARTINGALE_LEVEL
        set(value) {
            field = value
            prefs?.edit()?.putInt("max_martingale", value)?.apply()
        }

    @Volatile
    var lossCooldownSeconds: Int = prefs?.getInt("loss_cooldown_sec", DEFAULT_LOSS_COOLDOWN_SECONDS) ?: DEFAULT_LOSS_COOLDOWN_SECONDS
        set(value) {
            field = value
            prefs?.edit()?.putInt("loss_cooldown_sec", value)?.apply()
        }

    @Volatile
    var yoloLossCooldownSeconds: Int = prefs?.getInt("yolo_loss_cooldown_sec", DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS) ?: DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS
        set(value) {
            field = value.coerceIn(30, 45)
            prefs?.edit()?.putInt("yolo_loss_cooldown_sec", field)?.apply()
        }

    @Volatile
    var sessionMaxLossRatio: Float = prefs?.getFloat("session_max_loss_ratio", 0.03f) ?: 0.03f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("session_max_loss_ratio", value)?.apply()
        }

    @Volatile
    var absoluteEquityFloor: Double = prefs?.getFloat("absolute_equity_floor", 20000000f)?.toDouble() ?: 20000000.0
        set(value) {
            field = value
            prefs?.edit()?.putFloat("absolute_equity_floor", value.toFloat())?.apply()
        }

    @Volatile
    var consecutiveVoids: Int = 0

    @Volatile
    var totalVoids: Int = 0

    @Volatile
    var timeframe: CandleTimeframe = CandleTimeframe.M1

    @Volatile
    var stopLossStreak: Int = prefs?.getInt("sl_streak", DEFAULT_STOP_LOSS_STREAK) ?: DEFAULT_STOP_LOSS_STREAK
        set(value) {
            field = value
            prefs?.edit()?.putInt("sl_streak", value)?.apply()
        }

    @Volatile
    var takeProfitWins: Int = prefs?.getInt("tp_wins", DEFAULT_TAKE_PROFIT_WINS) ?: DEFAULT_TAKE_PROFIT_WINS
        set(value) {
            field = value
            prefs?.edit()?.putInt("tp_wins", value)?.apply()
        }

    @Volatile
    var cooldownSeconds: Int = prefs?.getInt("cooldown_sec", DEFAULT_COOLDOWN_SECONDS) ?: DEFAULT_COOLDOWN_SECONDS
        set(value) {
            field = value
            prefs?.edit()?.putInt("cooldown_sec", value)?.apply()
        }

    @Volatile
    var martingaleEnabled: Boolean = prefs?.getBoolean("martingale_on", true) ?: true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("martingale_on", value)?.apply()
        }

    @Volatile
    var martingaleMultiplier: Float = prefs?.getFloat("martingale_mult", DEFAULT_MARTINGALE_MULTIPLIER) ?: DEFAULT_MARTINGALE_MULTIPLIER
        set(value) {
            field = value
            prefs?.edit()?.putFloat("martingale_mult", value)?.apply()
        }

    @Volatile
    var baseAmount: Float = prefs?.getFloat("base_amount", 1.0f) ?: 1.0f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("base_amount", value)?.apply()
        }

    @Volatile
    var moneyManagementMode: MoneyManagementMode = try {
        MoneyManagementMode.valueOf(prefs?.getString("mm_mode", MoneyManagementMode.MARTINGALE.name) ?: MoneyManagementMode.MARTINGALE.name)
    } catch (e: Exception) {
        MoneyManagementMode.MARTINGALE
    }
        set(value) {
            field = value
            prefs?.edit()?.putString("mm_mode", value.name)?.apply()
        }

    @Volatile
    var sorosCycleTarget: Int = prefs?.getInt("soros_cycle_target", 4) ?: 4
        set(value) {
            field = value
            prefs?.edit()?.putInt("soros_cycle_target", value)?.apply()
        }

    @Volatile
    var sorosPayoutRate: Float = prefs?.getFloat("soros_payout", 0.85f) ?: 0.85f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("soros_payout", value)?.apply()
        }

    @Volatile
    var currentSorosStep: Int = 0

    @Volatile
    var completedSorosCycles: Int = 0

    // Estado en vivo de la sesión (Inicia limpio en 0 para cada nueva sesión de trading)
    @Volatile
    var currentLossStreak: Int = 0

    var consecutiveLosses: Int
        get() = currentLossStreak
        set(value) { currentLossStreak = value }

    val currentMartingaleIndex: Int
        get() = currentLossStreak

    @Volatile
    var currentWins: Int = 0

    @Volatile
    var totalWins: Int = 0

    @Volatile
    var totalLosses: Int = 0

    @Volatile
    var lastTradeTime: Long = 0L
        internal set

    val lastTradeResultTimestamp: Long
        get() = lastTradeTime

    // Control de trade activo en curso (Sincronizado y protegido contra sobreoperativa)
    @Volatile
    var hasPendingTrade: Boolean = false
        internal set

    /**
     * Ronda 12 (B1): estado de envio de la operacion en curso.
     *
     * DEFECTO QUE CORRIGE: `recordTradeSent` marcaba `hasPendingTrade = true` ANTES de que el
     * tap se hubiera confirmado, asi que el HUD mostraba `Trade Activo: PUT` aunque la orden
     * no hubiera llegado nunca al broker. Evidencia medida sobre el journal del dispositivo
     * (212 filas unicas): 17 TIE (8,0%) con `settled_balance == base_balance` EXACTO, mientras
     * las 114 LOSS y 81 WIN mueven +-100.000, y una racha de 5 TIE CONSECUTIVOS. Un empate de
     * precio 5 veces seguidas es estadisticamente inviable: el saldo inmovil prueba que el tap
     * no creo operacion, no que el precio empatara.
     *
     * Cuatro estados posibles, y el HUD nunca debe mostrar "Trade Activo" sin confirmacion:
     *  - NINGUNA     : no se ha enviado nada en esta sesion (estado de reposo). R13/A4: antes
     *                  este caso se representaba con NO_PROCESADA, que significa "se intento y
     *                  el broker no lo registro", es decir se etiquetaba un estado INACTIVO como
     *                  un FALLO. Eso hacia imposible distinguir "no ha pasado nada" de "algo
     *                  fallo" tanto en `/status` como en el HUD.
     *  - ENVIADA     : el tap se despacho, pero aun no hay prueba de que el broker lo registro.
     *  - CONFIRMADA  : el broker movio el saldo, o la operacion aparece registrada.
     *  - NO_PROCESADA: se agoto el tiempo sin ninguna prueba de que la orden existiera.
     */
    enum class OrderState { NINGUNA, ENVIADA, CONFIRMADA, NO_PROCESADA }

    @Volatile
    var orderState: OrderState = OrderState.NINGUNA
        internal set

    @Volatile
    var pendingTradeDecisionId: String = ""

    @Volatile
    var pendingTradeTradeId: String = ""

    /**
     * R24/B1.a: tradeIds YA liquidados. La liquidacion del motor corre en `handler.post`, asi que
     * dos frames pueden llegar al fallback `if (!resolved)` y contar el MISMO trade dos veces.
     *
     * `ConcurrentHashMap.newKeySet()` porque el acceso es desde el hilo principal y el de vision:
     * `add` es atomico, asi que exactamente una llamada gana la liquidacion.
     */
    private val liquidatedTradeIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * R24/B1.b: LOSS resueltos SIN evidencia de saldo (fallback de accion de precio).
     *
     * MONOTONO POR DISENO: no se reinicia en resetStats/setStats/correctStats/resetSession/
     * resetSniperSession. Un contador que se reinicia con el marcador no sirve para medir cuanto
     * del marcador se apoya en evidencia: el denominador cambiaria a mitad de la medicion.
     * `AtomicInteger` y no `Int` porque el incremento ocurre fuera de `@Synchronized`.
     */
    private val lossSinEvidenciaCounter = AtomicInteger(0)

    /** Lectura del contador monotono de LOSS sin evidencia. Ver [lossSinEvidenciaCounter]. */
    val tradesLossSinEvidencia: Int
        get() = lossSinEvidenciaCounter.get()

    /** R24/B1.b: incrementa el contador monotono de LOSS sin evidencia de saldo. */
    fun recordLossSinEvidencia() {
        lossSinEvidenciaCounter.incrementAndGet()
    }

    /**
     * R24/B1.c: incrementos manuales del marcador, separados por resultado.
     *
     * V1 los necesita desglosados: restar un total unico no permite contrastar `totalWins` contra
     * las filas WIN del journal y `totalLosses` contra las LOSS por separado.
     */
    private val manualWinsCounter = AtomicInteger(0)
    private val manualLossesCounter = AtomicInteger(0)

    /**
     * R24/B1.c: etiqueta el ORIGEN de un incremento MANUAL del marcador.
     *
     * El journal CSV no tiene columna `method` y estas rutas no escriben fila en el, asi que la
     * etiqueta va al `detail` del buffer de eventos (`/events`), que es lo que V1 puede leer.
     *
     * Regla de exclusion de V1: todo incremento etiquetado aqui se RESTA del lado `/status`
     * (`totalWins - manualWins`, `totalLosses - manualLosses`); un incremento manual sin etiqueta
     * es NO EVALUABLE. Sin esta regla, cualquier `/win` manual incrementa el contador sin dejar
     * fila en el journal y el contraste contra el ledger daria discrepancia por diseno.
     */
    fun markManualIncrement(origin: String, result: String) {
        if (result == "WIN") manualWinsCounter.incrementAndGet() else manualLossesCounter.incrementAndGet()
        TradeEventLog.add(
            TradeEventLog.Kind.MANUAL_INCREMENT,
            "[$origin] $result",
            decisionId = pendingTradeDecisionId.ifBlank { null },
            tradeId = pendingTradeTradeId.ifBlank { null }
        )
    }

    /**
     * R24/B4: taxonomia CERRADA de causas de `NO_PROCESADA` (preregistro R24 §3 V4).
     *
     * Anadir una etiqueta despues de la primera ventana invalida el criterio V4, asi que la lista
     * vive aqui y el clasificador solo puede devolver una de estas.
     */
    object NoProcesadaCausa {
        /** Ventana agotada y el saldo no se movio: la causa que ya existia. */
        const val SIN_MOVIMIENTO_SALDO = "SIN_MOVIMIENTO_SALDO"
        /** El gesto de accesibilidad no llego a despacharse. */
        const val TAP_NO_DESPACHADO = "TAP_NO_DESPACHADO"
        /** El gesto se despacho pero el broker no registro orden. */
        const val TAP_RECHAZADO_BROKER = "TAP_RECHAZADO_BROKER"
        /** Sin ticks frescos durante la ventana de envio. */
        const val FEED_MUDO = "FEED_MUDO"
        /** Ocurrencia registrada sin causa determinable. */
        const val NO_ATRIBUIDA = "NO ATRIBUIDA"

        /** Las cinco etiquetas validas, en el orden del preregistro. */
        val TODAS = listOf(
            SIN_MOVIMIENTO_SALDO, TAP_NO_DESPACHADO, TAP_RECHAZADO_BROKER, FEED_MUDO, NO_ATRIBUIDA
        )
    }

    /**
     * R24/B4: clasifica la causa de una `NO_PROCESADA` a partir de la evidencia disponible.
     *
     * Funcion PURA para poder probarla en JVM sin dispositivo. El orden importa: si el tap no se
     * despacho, el saldo no podia moverse y atribuirlo a `SIN_MOVIMIENTO_SALDO` describiria el
     * sintoma, no la causa. El feed mudo tampoco explica por si solo un fallo de envio, asi que va
     * despues de las causas de tap.
     *
     * @param tapDespachado el gesto de accesibilidad termino con exito
     * @param feedFresco habia tick fresco al cerrar la ventana
     * @param saldoObservable se pudo leer el saldo (sin lectura no hay veredicto posible)
     */
    fun clasificarCausaNoProcesada(
        tapDespachado: Boolean,
        feedFresco: Boolean,
        saldoObservable: Boolean
    ): String = when {
        !tapDespachado -> NoProcesadaCausa.TAP_NO_DESPACHADO
        !feedFresco -> NoProcesadaCausa.FEED_MUDO
        !saldoObservable -> NoProcesadaCausa.NO_ATRIBUIDA
        else -> NoProcesadaCausa.SIN_MOVIMIENTO_SALDO
    }

    /**
     * R24/B4: instante en que se entro en `NO_PROCESADA`; 0 si no esta activo.
     *
     * La duracion se mide desde aqui hasta la salida del estado, que es lo que pide V4 ("duracion
     * maxima de la condicion"). Medir solo el instante no dice cuanto tiempo estuvo el motor
     * bloqueado.
     */
    @Volatile
    var noProcesadaInicioMs: Long = 0L
        private set

    /** R24/B4: duracion MAXIMA observada del estado `NO_PROCESADA`, en ms. Monotona. */
    @Volatile
    var noProcesadaDuracionMaxMs: Long = 0L
        private set

    /** R24/B4: causa de la ULTIMA ocurrencia, de la taxonomia de [NoProcesadaCausa]. */
    @Volatile
    var noProcesadaCausa: String = ""
        private set

    /** R24/B4: ocurrencias de `NO_PROCESADA` desde el arranque del proceso. */
    private val noProcesadaCounter = AtomicInteger(0)

    /** R24/B4: ocurrencias totales de `NO_PROCESADA`. */
    val noProcesadaOcurrencias: Int
        get() = noProcesadaCounter.get()

    /**
     * R24/B4: registra la ENTRADA en `NO_PROCESADA` con su causa.
     *
     * Monotono por diseno: ni `resetStats` ni `resetSession` lo tocan, porque V4 mide ocurrencias
     * dentro de una ventana y un contador que se reinicia con el marcador cambiaria el denominador
     * a mitad de la medicion.
     */
    internal fun registrarNoProcesada(causa: String) {
        val etiqueta = if (causa in NoProcesadaCausa.TODAS) causa else NoProcesadaCausa.NO_ATRIBUIDA
        noProcesadaCausa = etiqueta
        noProcesadaInicioMs = System.currentTimeMillis()
        noProcesadaCounter.incrementAndGet()
    }

    /**
     * R24/B4: cierra la medicion de duracion al SALIR de `NO_PROCESADA`.
     *
     * Actualiza el maximo solo si habia una entrada abierta: una salida sin entrada previa (p.ej.
     * tras reiniciar el proceso) no puede inventar una duracion.
     */
    internal fun cerrarNoProcesada() {
        val inicio = noProcesadaInicioMs
        if (inicio <= 0L) return
        val duracion = (System.currentTimeMillis() - inicio).coerceAtLeast(0L)
        if (duracion > noProcesadaDuracionMaxMs) noProcesadaDuracionMaxMs = duracion
        noProcesadaInicioMs = 0L
    }

    /** R24/B4: duracion de la ocurrencia EN CURSO; 0 si no hay ninguna activa. */
    val noProcesadaDuracionActualMs: Long
        get() {
            val inicio = noProcesadaInicioMs
            return if (inicio <= 0L) 0L else (System.currentTimeMillis() - inicio).coerceAtLeast(0L)
        }

    /** R24/B1.c: incrementos manuales de victorias. V1 los resta de `totalWins`. */
    val manualWins: Int
        get() = manualWinsCounter.get()

    /** R24/B1.c: incrementos manuales de derrotas. V1 los resta de `totalLosses`. */
    val manualLosses: Int
        get() = manualLossesCounter.get()

    /**
     * R24/B1.a: clave de idempotencia de una liquidacion.
     *
     * El `tradeId` es la clave natural. Si viene vacio (rutas antiguas o trades sin id) se usa el
     * timestamp de apertura, que tambien es unico por operacion: sin ese respaldo, todos los trades
     * sin id colapsarian en una sola clave y solo el primero se contaria.
     */
    internal fun liquidationKey(tradeId: String, tradeStartMs: Long): String =
        if (tradeId.isNotBlank()) tradeId else "ts:$tradeStartMs"

    /**
     * Marca un trade como liquidado. Devuelve true SOLO la primera vez (la llamada que gana).
     *
     * Sin identificador fiable (`ts:0`) no se deduplica: bloquear por una clave compartida
     * perderia trades legitimos, que es peor que un doble conteo ocasional.
     */
    private fun markLiquidated(tradeId: String, tradeStartMs: Long): Boolean {
        val key = liquidationKey(tradeId, tradeStartMs)
        if (key == "ts:0") return true
        // ponytail: el set no se poda por tiempo; a 1 trade/minuto tarda ~8h en llegar al tope y
        // entonces se vacia entero. Si alguna vez importa la ventana exacta, sustituir por un
        // cache LRU acotado por tiempo en vez de por tamano.
        if (liquidatedTradeIds.size >= MAX_LIQUIDATED_TRADE_IDS) liquidatedTradeIds.clear()
        return liquidatedTradeIds.add(key)
    }

    /**
     * R24/B1.a: liquida un trade UNA sola vez por `tradeId`, sin pasar por el pendiente.
     *
     * Es el sustituto exacto del fallback `recordTradeWin/Loss(force = true)`: mismo incremento,
     * pero idempotente. Devuelve true si ESTA llamada fue la que registro el resultado.
     */
    @Synchronized
    fun settleTradeOnce(tradeId: String, tradeStartMs: Long, win: Boolean): Boolean {
        if (!markLiquidated(tradeId, tradeStartMs)) return false
        if (win) recordTradeWins(1, force = true) else recordTradeLosses(1, force = true)
        return true
    }

    /**
     * R24/B1.a (fix): liquidacion MANUAL del operador (HUD o bridge), idempotente contra el motor.
     *
     * DEFECTO QUE CIERRA. La ruta manual llamaba a `recordTradeWin/Loss(force = true)` y NO marcaba
     * la clave de liquidacion. Secuencia real: el motor captura `settleTradeId` antes del
     * `handler.post`; el operador pulsa WIN; `recordTradeWins` incrementa Y limpia el pendiente;
     * dentro del `post` `resolvePendingTradeWin()` devuelve false y el fallback `settleTradeOnce`
     * halla la clave LIBRE e incrementa otra vez. Con `SNIPER_MAX_LOSSES = 2` una sola perdida real
     * cerraba la sesion.
     *
     * SIN PENDIENTE (`pendingTradeTradeId` vacio) se incrementa igual: es la correccion manual del
     * operador sin trade abierto, y bloquearla romperia el uso legitimo del HUD.
     *
     * El contador manual se anota DESPUES de ganar la clave, no antes: si esta llamada no incrementa
     * el marcador, tampoco puede incrementar `manualWins`/`manualLosses`, o la resta de V1
     * (`totalWins - manualWins`) quedaria negativa.
     */
    @Synchronized
    fun settleManual(origin: String, win: Boolean): Boolean {
        val id = pendingTradeTradeId
        val start = pendingTradeStartTime
        // Se usa `markLiquidated` SIEMPRE, no solo cuando hay `tradeId`: con un trade abierto sin id
        // la clave cae al timestamp de apertura, que es exactamente la que usaria el motor
        // (`settleTradeOnce("", start)`). Sin eso quedaria abierto el mismo doble conteo para los
        // trades sin id. Sin pendiente, `start` es 0 y `markLiquidated` devuelve true sin marcar
        // nada: la correccion manual del operador sigue incrementando.
        if (!markLiquidated(id, start)) {
            // Ya liquidado por la ruta automatica: no sumar. Se cierra el pendiente igual para que
            // el HUD no quede mostrando "Trade Activo".
            clearPendingTrade()
            OverlayService.instance?.updateHUDView()
            return false
        }
        markManualIncrement(origin, if (win) "WIN" else "LOSS")
        if (win) recordTradeWins(1, force = true) else recordTradeLosses(1, force = true)
        return true
    }

    /** Cambia el estado y lo registra en el buffer observable (R13/A5, R21/Frente A). */
    internal fun setOrderState(nuevo: OrderState, motivo: String) {
        if (orderState == nuevo) return
        // R24/B4: al SALIR de NO_PROCESADA se cierra la medicion de duracion. Va aqui y no en cada
        // llamador porque este es el unico punto por el que pasa toda transicion: cualquier ruta
        // futura que saque al motor del estado queda medida sin tocar esa ruta.
        if (orderState == OrderState.NO_PROCESADA) {
            cerrarNoProcesada()
        }
        orderState = nuevo
        // R23B Iter 2: la direccion de la orden se inyecta en el detalle para que los parsers del
        // validador la extraigan de forma deterministica. Antes el evento era "CONFIRMADA saldo
        // movido" y `trades.csv` acababa con `action = UNKNOWN`, obligando al parser a adivinar.
        val actionPrefix = pendingTradeAction?.name?.let { "$it " } ?: ""
        TradeEventLog.add(
            TradeEventLog.Kind.ORDER_STATE,
            "${nuevo.name} $actionPrefix$motivo".trim(),
            decisionId = pendingTradeDecisionId.ifBlank { null },
            tradeId = pendingTradeTradeId.ifBlank { null }
        )
    }

    /** true solo si hay operacion Y el broker la ha confirmado. Es lo que el HUD debe exigir. */
    val hasConfirmedTrade: Boolean
        get() = hasPendingTrade && orderState == OrderState.CONFIRMADA

    /**
     * Ronda 12 (B1): comprobacion PURA y testeable de si el broker registro la operacion.
     *
     * Criterio: la orden existe si el saldo se movio respecto al de apertura. Un stake de
     * opciones binarias se debita al abrir, asi que CUALQUIER resultado real (ganar o perder)
     * altera el saldo; solo la ausencia de orden lo deja identico. Se admite un margen de
     * 1.0 COP para el ruido de lectura del arbol de accesibilidad.
     *
     * @param baseBalance saldo leido al despachar el tap (0 si no se pudo leer)
     * @param currentBalance saldo leido ahora (0 si no se pudo leer)
     * @param elapsedSec segundos desde el envio
     * @param confirmationWindowSec ventana en la que se exige ver movimiento de saldo
     */
    fun isOrderConfirmed(
        baseBalance: Double,
        currentBalance: Double,
        elapsedSec: Long,
        confirmationWindowSec: Long = 12L
    ): Boolean {
        if (baseBalance <= 0.0 || currentBalance <= 0.0) return false
        if (elapsedSec > confirmationWindowSec) return false
        return Math.abs(currentBalance - baseBalance) > 1.0
    }

    /**
     * Ronda 12 (B1): la orden no existe si paso la ventana de confirmacion sin que el saldo se
     * moviera. Distinto de "empate": un empate real habria debitado el stake al abrir.
     */
    fun isOrderUnprocessed(
        baseBalance: Double,
        currentBalance: Double,
        elapsedSec: Long,
        confirmationWindowSec: Long = 12L
    ): Boolean {
        if (elapsedSec < confirmationWindowSec) return false
        if (baseBalance <= 0.0 || currentBalance <= 0.0) return false
        return Math.abs(currentBalance - baseBalance) <= 1.0
    }

    @Volatile
    var pendingTradeAction: TradeAction? = null
        internal set

    @Volatile
    var pendingTradeStartTime: Long = 0L
        internal set

    val tradeOpenedTimestamp: Long
        get() = pendingTradeStartTime

    @Volatile
    var pendingTradeEntryPriceY: Float = 0f
        internal set

    /**
     * Ronda 11 (T3): precio REAL de entrada del broker, en dominio de precio (no en píxeles).
     * Es el strike con el que se calcula ITM/OTM y con el que se compara la liquidación.
     * `pendingTradeEntryPriceY` queda como campo heredado (píxeles de visión retirada) y NO debe
     * usarse para decidir nada: se conserva para compatibilidad del journal histórico.
     */
    @Volatile
    var pendingTradeEntryPrice: Double = 0.0
        internal set

    @Volatile
    var pendingTradeBaseBalance: Double = 0.0
        internal set

    @Volatile
    var pendingTradeConfidence: Float = 0.0f

    // H10 (Ronda 6): segundo del reloj (0-59) en que se ENVIÓ la orden. El journal guardaba el
    // segundo de LIQUIDACIÓN (sesgado); este campo permite auditar el timing real de entrada.
    var pendingTradeEntryCandleSecond: Int = -1
    // H10: etiqueta de la estrategia/razón que originó la señal (para correlacionar setup -> resultado).
    var pendingTradeReason: String = ""
        internal set

    /**
     * Ronda 14 (A2): instrumentación de latencia del trade pendiente.
     *
     * Hasta R13 no había forma de saber CUÁNDO se decidió la señal frente a CUÁNDO se ejecutó el
     * tap, y el journal sólo guardaba el segundo de vela de la DECISIÓN. Con un tap de 255-1205 ms
     * medidos en este dispositivo, la diferencia entre ambos instantes es justo el dato que faltaba
     * para investigar los TIE. `pendingTradeSignalTs` es el instante de la señal;
     * `pendingTradeTapStartTs`/`pendingTradeTapEndTs` los del despacho táctil real;
     * `pendingTradeCandleSecondAtTapEnd` el segundo de vela medido AL TERMINAR el tap.
     */
    @Volatile
    var pendingTradeSignalTs: Long = 0L
        internal set

    @Volatile
    var pendingTradeTapStartTs: Long = 0L
        internal set

    @Volatile
    var pendingTradeTapEndTs: Long = 0L
        internal set

    @Volatile
    var pendingTradeCandleSecondAtTapEnd: Int = -1
        internal set

    @Volatile
    var pendingTradePriceAtSignal: Double = 0.0
        internal set

    @Volatile
    var pendingTradePriceAtTapEnd: Double = 0.0
        internal set

    @Volatile
    var sessionStartBalance: Double = 0.0

    @Volatile
    var unitTradeAmount: Double = 80000.0 // Monto de inversión base en Binomo (Col$80,000)

    @Volatile
    var currentSubMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE

    @Synchronized
    fun getRemainingCooldown(subMode: AutonomousSubMode? = null): Int {
        if (lastTradeTime == 0L) return 0
        val effectiveSubMode = subMode ?: currentSubMode
        val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
        val requiredLossCooldown = when {
            effectiveSubMode == AutonomousSubMode.YOLO -> yoloLossCooldownSeconds
            currentLossStreak >= 2 -> EXTENDED_LOSS_COOLDOWN_SECONDS
            else -> lossCooldownSeconds
        }
        val requiredCooldown = if (currentLossStreak > 0) requiredLossCooldown else cooldownSeconds
        val remaining = requiredCooldown - elapsed
        return if (remaining > 0) remaining.toInt() else 0
    }

    @Synchronized
    fun canExecuteTrade(
        subMode: AutonomousSubMode? = null,
        confidence: Float = 1.0f
    ): Pair<Boolean, String> {
        // R23B Iter 1: submodo EFECTIVO. Los callers de produccion siempre pasan el submodo explicito
        // (`canExecuteTrade(autonomousSubMode)`), pero `canTrade(confidence)` no. El fallback es
        // `currentSubMode`, que por defecto es CONSERVATIVE (fail-safe: sin argumento se aplican TODOS
        // los guards). Antes su default era YOLO, que con la bifurcacion de abajo habria convertido
        // cualquier llamada sin argumento en "sin limites de sesion" — un fail-open latente.
        val effectiveSubMode = subMode ?: currentSubMode
        val currentBal = AutoTradeAccessibilityService.latestObservedBalance
        val isDemo = currentBal >= 1_000_000.0 || (AutoTradeAccessibilityService.isDemoAccount && currentBal > 500_000.0)
        if (currentBal > 0.0) {
            if (isDemo) {
                if (currentBal < absoluteEquityFloor) {
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Equity Demo () por debajo del suelo absoluto ()")
                    return Pair(false, "Stop Equity Absoluto Alcanzado ( < )")
                }
            } else {
                // Cuenta Real: suelo mínimo operativo para Binomo (mínimo ~4,000 COP por orden)
                val minBrokerStake = 4000.0
                if (currentBal < minBrokerStake) {
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Saldo real insuficiente para operar ($currentBal < $minBrokerStake)")
                    return Pair(false, "Saldo Real Insuficiente (< Col$$minBrokerStake)")
                }

                // Si la orden configurada en pantalla supera el saldo disponible, avisar claramente
                val observedStake = AutoTradeAccessibilityService.observedOrderAmount
                if (observedStake > 0.0 && observedStake > currentBal) {
                    val stakeInt = observedStake.toInt()
                    val balInt = currentBal.toInt()
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Inversión en Binomo ($stakeInt COP) supera saldo real ($balInt COP)")
                    return Pair(false, "Cantidad ($stakeInt COP) supera saldo ($balInt COP). Ajusta a Col$4,000")
                }
            }

            if (sessionStartBalance == 0.0) {
                sessionStartBalance = currentBal
                sessionPeakBalance = currentBal
            } else if (currentBal > sessionPeakBalance) {
                sessionPeakBalance = currentBal
            }

            if (sessionStartBalance > 0.0) {
                val isConsistentSession = (isDemo && sessionStartBalance > 1_000_000.0) || (!isDemo && sessionStartBalance <= 1_000_000.0)
                // R23B Iter 1: en YOLO la operativa es CONTINUA. `sessionFloor` y `Trailing Profit Lock`
                // son limites de SESION: miden el saldo acumulado de la ronda, no la calidad del setup
                // actual. Medido por OpenCode en el baseline: de 74 señales, 75/101 vetos fueron
                // `Trailing Profit Lock` (74,3%) por un pico previo en la cuenta demo, dejando la
                // frecuencia del submodo INOBSERVABLE. Eso es contaminacion de la medicion, no riesgo:
                // el stake por orden esta acotado y el suelo de equity absoluto (abajo) sigue en pie.
                // NOTA IMPORTANTE: esto NO relaja el guard en produccion de forma accidental. El modo
                // YOLO no se selecciona en ningun flujo automatico; el submodo activo lo elige el
                // operador, y `currentSubMode` (el fallback cuando no se pasa argumento) tiene default
                // YOLO. Consecuencia: `canTrade(confidence)` sin submodo explicito tambien queda sin
                // limites de sesion. Es la letra de la especificacion, pero debe quedar declarado.
                val esYolo = effectiveSubMode == AutonomousSubMode.YOLO
                if (isConsistentSession && !esYolo) {
                    val minLossAllowance = if (isDemo) 100000.0 else 8000.0
                    val maxLossAmt = maxOf(sessionStartBalance * sessionMaxLossRatio, minLossAllowance)
                    val sessionFloor = sessionStartBalance - maxLossAmt
                    if (currentBal < sessionFloor) {
                        android.util.Log.e("RiskManager", "CRITICAL STOP: Pérdida máxima de sesión alcanzada. Balance actual: $currentBal, Suelo de sesión: $sessionFloor")
                        return Pair(false, "Stop Loss Sesion Alcanzado ($currentBal < $sessionFloor)")
                    }

                    // Trailing Profit Lock: Proteger beneficios de la sesión si hubo ganancia pico significativa
                    val peakProfit = sessionPeakBalance - sessionStartBalance
                    if (peakProfit >= minPeakProfitToLock) {
                        val allowedRetracement = peakProfit * trailingProfitRetracementRatio
                        val trailingFloor = sessionPeakBalance - allowedRetracement
                        if (currentBal < trailingFloor) {
                            // Loguear solo en la transicion: canExecuteTrade se consulta desde el HUD cada ~400ms
                            if (!trailingProfitLockTriggered) {
                                android.util.Log.w("RiskManager", "TRAILING PROFIT LOCK: Beneficio asegurado. Pico: +${peakProfit.toInt()} COP, Suelo: $trailingFloor")
                            }
                            trailingProfitLockTriggered = true
                            return Pair(false, "Trailing Profit Lock: Ganancia asegurada (+${peakProfit.toInt()} COP pico)")
                        }
                    }
                }
            }
        }
        
        if (consecutiveVoids >= 3) {
            val cooldownMs = 120_000L
            val elapsedSinceVoid = System.currentTimeMillis() - lastTradeTime
            if (elapsedSinceVoid < cooldownMs) {
                val remSec = ((cooldownMs - elapsedSinceVoid) / 1000).coerceAtLeast(1)
                return Pair(false, "Parada: 3 trades VOID seguidos (ejecución fallida, ${remSec}s cooldown)")
            } else {
                android.util.Log.i("RiskManager", "Auto-recuperación de parada por VOID tras 2 minutos de enfriamiento")
                consecutiveVoids = 0
            }
        }

        // R23B Iter 1: `stopLossStreak` es un limite de SESION (pausa terminal tras N derrotas), no una
        // medida de la calidad del setup actual. En YOLO se omite para que la operativa sea continua.
        // ALCANCE DECLARADO: esto REVIERTE una decision anterior del repo. Los tests
        // `testFix1_canTradeReturnsFalseWhenLossStreakReachesStopLossInYolo` y
        // `testBug1_StopLossIsTerminal` afirmaban textualmente "stop loss must be terminal and block
        // trading even in YOLO"; quedan actualizados a la politica nueva. Lo que sigue protegiendo la
        // cuenta en YOLO: el suelo de equity absoluto (arriba, se evalua antes que todo), la pausa
        // anti-tilt tras fallo de martingala (abajo), el cooldown YOLO y el guard DEMO fail-closed.
        if (effectiveSubMode != AutonomousSubMode.YOLO && stopLossStreak > 0 && currentLossStreak >= stopLossStreak) {
            return Pair(false, "Stop Loss alcanzado ($stopLossStreak derrotas)")
        }

        if (hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - pendingTradeStartTime) / 1000
            // Timeout de seguridad según timeframe: 85s para 1m, 330s para 5m
            val timeoutSec = if (timeframe == CandleTimeframe.M5) 330L else MAX_PENDING_TRADE_TIMEOUT_SEC
            if (elapsed >= timeoutSec) {
                clearPendingTrade()
            } else {
                return Pair(false, "Operación abierta en curso (${elapsed}s)")
            }
        }
        // Si falló el nivel máximo de martingala (MG1 fallido -> pérdidas consecutivas > maxMartingaleLevel),
        // forzamos pausa de enfriamiento breve incluso en YOLO para proteger la cuenta contra tilt,
        // pero con pausa extendida de 180s (3m) si ya van >= 2 derrotas consecutivas
        if (martingaleEnabled && currentLossStreak > maxMartingaleLevel) {
            val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
            val effectiveLossCooldown = if (effectiveSubMode == AutonomousSubMode.YOLO) yoloLossCooldownSeconds else if (currentLossStreak >= 2) EXTENDED_LOSS_COOLDOWN_SECONDS else lossCooldownSeconds
            val remaining = effectiveLossCooldown - elapsed
            if (remaining > 0) {
                return Pair(false, "Pausa Anti-Tilt tras fallo Martingala (${remaining}s)")
            }
        }

        // En SUBMODO YOLO: Cooldown controlado tras pérdida de 30-45s máximo para permitir tomar
        // la operación Martingala M1 en la siguiente vela sin perder el impulso del mercado
        if (effectiveSubMode == AutonomousSubMode.YOLO) {
            val remaining = getRemainingCooldown(subMode)
            if (remaining > 0) {
                return Pair(false, "Pausa de Cooldown YOLO: ${remaining}s")
            }
            // Martingala Selectiva M1: En nivel 1 de Martingala (M1), exigir setup A+ (confianza >= 85%)
            val normalizedConfidence = if (confidence > 1.0f) confidence / 100f else confidence
            if (martingaleEnabled && currentLossStreak == 1 && normalizedConfidence < selectiveM1MinConfidence) {
                val pct = (normalizedConfidence * 100).toInt()
                val minPct = (selectiveM1MinConfidence * 100).toInt()
                return Pair(false, "Martingala M1 requiere setup A+ (Confianza $pct% < $minPct%)")
            }
            // En YOLO opera continuamente sin detenerse permanentemente por stop loss de racha
            return Pair(true, "🚀 MODO YOLO: Operativa continua sin límites")
        }

        // En SUBMODO FRANCOTIRADOR (SNIPER):
        // 1. Veto a activos sintéticos / OTC y validación estricta de par Forex real
        // 2. Stop Loss diario estricto: máximo 2 pérdidas consecutivas o acumuladas en la sesión
        // 3. Take Profit diario: 2 victorias
        // 4. Máximo 3 operaciones ejecutadas por sesión diaria (acumulando W + L + Voids)
        if (effectiveSubMode == AutonomousSubMode.SNIPER) {
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic) {
                return Pair(false, "⚠️ Veto Francotirador: Activo sintético/OTC ($currentAsset) prohibido para dinero real. Selecciona un par Forex real en Binomo")
            }
            val classification = AutoTradeAccessibilityService.classifyAsset(currentAsset)
            if (classification != AssetClassification.FOREX_REAL) {
                return Pair(false, "⚠️ Francotirador: Activo ($currentAsset) no es un par Forex real válido. Selecciona EUR/USD, GBP/USD, etc.")
            }

            if (currentLossStreak >= SNIPER_MAX_LOSSES || totalLosses >= SNIPER_MAX_LOSSES) {
                return Pair(false, "🛑 Stop Loss Francotirador alcanzado ($totalLosses derrotas). Sesión finalizada.")
            }

            if (totalWins >= SNIPER_TAKE_PROFIT_WINS || currentWins >= SNIPER_TAKE_PROFIT_WINS) {
                return Pair(false, "🎯 Take Profit Francotirador alcanzado ($totalWins victorias). Meta diaria cumplida.")
            }

            val sessionTrades = totalWins + totalLosses + totalVoids
            if (sessionTrades >= SNIPER_MAX_SESSION_TRADES) {
                return Pair(false, "🛑 Límite diario Francotirador alcanzado ($sessionTrades/3 operaciones). Sesión finalizada.")
            }

            val remaining = getRemainingCooldown(subMode)
            if (remaining > 0) {
                return Pair(false, "Pausa de Cooldown Francotirador: ${remaining}s")
            }

            return Pair(true, "🎯 MODO FRANCOTIRADOR: Listo para disparo de alta precisión")
        }

        if (takeProfitWins > 0 && currentWins >= takeProfitWins) {
            return Pair(false, "Take Profit alcanzado ($takeProfitWins victorias)")
        }
        val remaining = getRemainingCooldown(subMode)
        if (remaining > 0) {
            return Pair(false, "Pausa de Cooldown: ${remaining}s")
        }

        // Martingala Selectiva M1: En nivel 1 de Martingala (M1), exigir setup A+ (confianza >= 85%)
        val normalizedConfidence = if (confidence > 1.0f) confidence / 100f else confidence
        if (martingaleEnabled && currentLossStreak == 1 && normalizedConfidence < selectiveM1MinConfidence) {
            val pct = (normalizedConfidence * 100).toInt()
            val minPct = (selectiveM1MinConfidence * 100).toInt()
            return Pair(false, "Martingala M1 requiere setup A+ (Confianza $pct% < $minPct%)")
        }

        return Pair(true, "Listo para operar")
    }

    @Synchronized
    fun canTrade(confidence: Float = 1.0f): Boolean = canExecuteTrade(confidence = confidence).first

    /**
     * Reanuda la operativa tras alcanzar Stop Loss o Take Profit sin borrar el historial general (totalWins/totalLosses).
     */
    @Synchronized
    fun resetStreakOnly() {
        currentLossStreak = 0
        currentWins = 0
        lastTradeTime = 0L
        consecutiveVoids = 0
        // Reanudar = nueva linea base de sesion. Sin esto, el Trailing Profit Lock y el Stop Loss de
        // sesion (calculados contra sessionStart/Peak) seguian bloqueando aunque la UI dijera "reanudada".
        val bal = AutoTradeAccessibilityService.latestObservedBalance
        sessionStartBalance = if (bal > 0.0) bal else 0.0
        sessionPeakBalance = sessionStartBalance
        trailingProfitLockTriggered = false
        clearPendingTrade()
    }

    @Synchronized
    fun resumeAfterTarget() {
        resetStreakOnly()
    }

    /**
     * Registra la orden enviada.
     *
     * @param entryPrice Precio REAL del broker en el momento del disparo (tick WS). Es el strike
     *   de la opción binaria: ITM/OTM y la liquidación se calculan contra él.
     * @param entryPriceY Coordenada de pantalla heredada (visión retirada). Solo informativa.
     */
    @Synchronized
    fun recordTradeSent(
        action: TradeAction,
        entryPrice: Double = 0.0,
        entryPriceY: Float = 0f,
        baseBalance: Double = 0.0,
        confidence: Float = 0.0f,
        entryCandleSecond: Int = -1,
        reason: String = "",
        decisionId: String = "",
        tradeId: String = ""
    ) {
        val now = System.currentTimeMillis()
        lastTradeTime = now
        hasPendingTrade = true
        pendingTradeDecisionId = decisionId
        pendingTradeTradeId = tradeId
        // Ronda 12 (B1): al despachar el tap la orden solo esta ENVIADA, nunca confirmada.
        // Antes se marcaba como operacion en curso directamente y el HUD lo presentaba como
        // "Trade Activo" aunque el broker no hubiera registrado nada.
        // R23B Iter 2: `pendingTradeAction` se asigna ANTES de `setOrderState` para que el evento
        // ENVIADA tambien lleve la direccion ("ENVIADA BUY tap despachado"). Al reves, este evento
        // salia sin accion y era el unico hueco que quedaba en la telemetria de la orden.
        pendingTradeAction = action
        setOrderState(OrderState.ENVIADA, "tap despachado")
        pendingTradeStartTime = now
        pendingTradeEntryPrice = entryPrice
        pendingTradeEntryPriceY = entryPriceY
        pendingTradeBaseBalance = baseBalance
        pendingTradeConfidence = confidence
        pendingTradeEntryCandleSecond = entryCandleSecond
        pendingTradeReason = reason
        // R14/A2: la senal y la ejecucion son instantes distintos. `now` es el de la senal
        // (esta funcion se llama justo antes de despachar el tap); los del tap los completa
        // `captureTapLatency` cuando el tap termina.
        pendingTradeSignalTs = now
        pendingTradePriceAtSignal = entryPrice
        pendingTradeTapStartTs = 0L
        pendingTradeTapEndTs = 0L
        pendingTradeCandleSecondAtTapEnd = -1
        pendingTradePriceAtTapEnd = 0.0
    }

    /**
     * Ronda 14 (A2): completa la instrumentacion de latencia del trade pendiente con los instantes
     * REALES del tap y el precio observado al terminarlo. Si no se llama (ruta sin tap, o el tap
     * aun no ha terminado), el journal escribe celdas VACIAS: "no medido" nunca se disfraza de 0.
     */
    fun captureTapLatency(tapStartTs: Long, tapEndTs: Long, candleSecondAtTapEnd: Int, priceAtTapEnd: Double) {
        if (!hasPendingTrade) return
        pendingTradeTapStartTs = tapStartTs
        pendingTradeTapEndTs = tapEndTs
        pendingTradeCandleSecondAtTapEnd = candleSecondAtTapEnd
        pendingTradePriceAtTapEnd = priceAtTapEnd
    }

    @Synchronized
    fun notifyTradePlaced(action: TradeAction, entryPrice: Double = 0.0, baseBalance: Double = 0.0, confidence: Float = 0.0f) {
        recordTradeSent(action, entryPrice, 0f, baseBalance, confidence)
    }

    /**
     * Ronda 12 (B1): el broker confirmo la orden (se movio el saldo). Solo desde aqui el HUD
     * puede mostrar la operacion como activa.
     */
    @Synchronized
    fun markOrderConfirmed() {
        if (hasPendingTrade && orderState == OrderState.ENVIADA) {
            setOrderState(OrderState.CONFIRMADA, "saldo movido")
        }
    }

    /**
     * Ronda 12 (B1): se agoto la ventana sin prueba de que la orden existiera. Cierra el estado
     * pendiente (para no bloquear el motor ni dejar el HUD en "Trade Activo" indefinidamente) y
     * cuenta el fallo como VOID de envio, que es lo que realmente ocurrio.
     */
    @Synchronized
    fun markOrderUnprocessed(
        tapDespachado: Boolean = true,
        feedFresco: Boolean = true,
        saldoObservable: Boolean = true
    ) {
        if (!hasPendingTrade) return
        // R13/A4: se limpia primero (lo que deja el estado en NINGUNA) y se marca NO_PROCESADA
        // despues, para que quede registrado el RESULTADO del intento. Al reves, `clearPendingTrade`
        // pisaba el NO_PROCESADA con NINGUNA y el fallo se perdia.
        val decId = pendingTradeDecisionId
        val trId = pendingTradeTradeId
        // R23B Iter 2: la accion tambien se preserva por la misma razon que los ids. `clearPendingTrade`
        // la deja en null, asi que sin esto el evento NO_PROCESADA (el unico que registra el FALLO de
        // envio) saldria sin direccion — justo el caso que mas interesa auditar.
        val accionPendiente = pendingTradeAction
        // R24/B4: la causa se clasifica ANTES de limpiar, porque depende del estado del pendiente.
        val causa = clasificarCausaNoProcesada(tapDespachado, feedFresco, saldoObservable)
        clearPendingTrade()
        pendingTradeDecisionId = decId
        pendingTradeTradeId = trId
        pendingTradeAction = accionPendiente
        // R24/B4: se registra la ENTRADA (instante + causa) antes de la transicion, para que la
        // duracion se mida desde el instante real de entrada y no desde que el evento se escribe.
        registrarNoProcesada(causa)
        setOrderState(OrderState.NO_PROCESADA, "ventana agotada sin movimiento de saldo causa=$causa")
        pendingTradeDecisionId = ""
        pendingTradeTradeId = ""
        lastTradeTime = System.currentTimeMillis()
        OverlayService.instance?.updateHUDView()
    }

    @Synchronized
    fun clearPendingTrade() {
        hasPendingTrade = false
        // Ronda 12 (B1): sin operacion en curso no hay estado de envio que mostrar. El HUD
        // solo debe pintar "Trade Activo" mientras haya una orden viva.
        // R13/A4: pasa a NINGUNA (reposo). Antes ponia NO_PROCESADA, que significa "se intento
        // y fallo": eso hacia que `/status` reportase un fallo permanente en reposo.
        setOrderState(OrderState.NINGUNA, "sin operacion en curso")
        pendingTradeAction = null
        pendingTradeStartTime = 0L
        pendingTradeEntryPrice = 0.0
        pendingTradeEntryPriceY = 0f
        pendingTradeBaseBalance = 0.0
        pendingTradeConfidence = 0.0f
        pendingTradeEntryCandleSecond = -1
        pendingTradeReason = ""
        pendingTradeDecisionId = ""
        pendingTradeTradeId = ""
        // R14/A2: la instrumentacion de latencia pertenece al trade cerrado; si se dejara,
        // una operacion posterior sin tap heredaria los instantes de la anterior.
        pendingTradeSignalTs = 0L
        pendingTradeTapStartTs = 0L
        pendingTradeTapEndTs = 0L
        pendingTradeCandleSecondAtTapEnd = -1
        pendingTradePriceAtSignal = 0.0
        pendingTradePriceAtTapEnd = 0.0
    }

    @Synchronized
    fun recordTradeVoid(count: Int = 1) {
        val safeCount = count.coerceAtLeast(1)
        consecutiveVoids += safeCount
        totalVoids += safeCount
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
        OverlayService.instance?.updateHUDView()
    }

    @Synchronized
    fun recordTradeWins(count: Int = 1, force: Boolean = false) {
        if (!force && !hasPendingTrade) {
            android.util.Log.w("RiskManager", "recordTradeWins ignorado: No hay trade pendiente (llamada duplicada bloqueada)")
            return
        }
        
        if (!force) {
            val currentBal = AutoTradeAccessibilityService.latestObservedBalance
            if (pendingTradeBaseBalance > 0.0 && currentBal > 0.0) {
                val diff = Math.abs(currentBal - pendingTradeBaseBalance)
                if (diff <= 10.0) {
                    android.util.Log.w("RiskManager", "Interceptado WIN falso (Diff=$diff). Convirtiendo a VOID.")
                    recordTradeVoid(count)
                    return
                }
            }
        }

        consecutiveVoids = 0
        val safeCount = count.coerceAtLeast(1)
        currentWins += safeCount
        totalWins += safeCount
        currentLossStreak = 0
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            currentSorosStep += safeCount
            if (currentSorosStep >= sorosCycleTarget) {
                completedSorosCycles++
                currentSorosStep = 0
            }
        }
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
        OverlayService.instance?.updateHUDView()
    }

    @Synchronized
    fun recordTradeLosses(count: Int = 1, force: Boolean = false) {
        if (!force && !hasPendingTrade) {
            android.util.Log.w("RiskManager", "recordTradeLosses ignorado: No hay trade pendiente (llamada duplicada bloqueada)")
            return
        }
        
        if (!force) {
            val currentBal = AutoTradeAccessibilityService.latestObservedBalance
            if (pendingTradeBaseBalance > 0.0 && currentBal > 0.0) {
                val diff = Math.abs(currentBal - pendingTradeBaseBalance)
                if (diff <= 10.0) {
                    android.util.Log.w("RiskManager", "Interceptado LOSS falso (Diff=$diff). Convirtiendo a VOID.")
                    recordTradeVoid(count)
                    return
                }
            }
        }

        consecutiveVoids = 0
        val safeCount = count.coerceAtLeast(1)
        totalLosses += safeCount
        currentLossStreak += safeCount
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            currentSorosStep = 0
        }
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
        OverlayService.instance?.updateHUDView()
    }

    @Synchronized
    fun recordTradeWin(force: Boolean = false) = recordTradeWins(1, force)

    @Synchronized
    fun recordTradeLoss(force: Boolean = false) = recordTradeLosses(1, force)

    @Synchronized
    fun resolvePendingTradeWin(): Boolean {
        if (!hasPendingTrade) return false
        recordTradeWins(1, force = true)
        return true
    }

    @Synchronized
    fun resolvePendingTradeLoss(): Boolean {
        if (!hasPendingTrade) return false
        recordTradeLosses(1, force = true)
        return true
    }

    @Synchronized
    fun resetStats() {
        totalWins = 0
        totalLosses = 0
        currentWins = 0
        currentLossStreak = 0
        consecutiveVoids = 0
        totalVoids = 0
        // R24/B1.b: `tradesLossSinEvidencia` NO se reinicia aqui A PROPOSITO. Es un contador
        // monotono de calidad de evidencia, no parte del marcador: si se pusiera a cero junto con
        // totalWins/totalLosses, la medicion de "cuanto del marcador se apoya en saldo real" se
        // reiniciaria justo cuando el operador corrige el marcador, y el ratio quedaria inflado o
        // dividido por cero. Solo se reinicia al reiniciar el proceso.
        //
        // R24/B1.c: los contadores MANUALES si se reinician, y deben hacerlo: son una desagregacion
        // de totalWins/totalLosses. Si sobrevivieran al reset, la resta de V1
        // (`totalWins - manualWins`) quedaria NEGATIVA y el contraste seria invalido.
        resetManualCounters()
    }

    /** R24/B1.c: pone a cero los contadores de incrementos manuales. Ver [resetStats]. */
    private fun resetManualCounters() {
        manualWinsCounter.set(0)
        manualLossesCounter.set(0)
    }

    @Synchronized
    fun setStats(wins: Int, losses: Int, lossStreak: Int? = null) {
        totalWins = wins.coerceAtLeast(0)
        totalLosses = losses.coerceAtLeast(0)
        currentWins = wins.coerceAtLeast(0)
        currentLossStreak = lossStreak ?: if (wins == 0) losses.coerceAtLeast(0) else 0
        consecutiveVoids = 0
        totalVoids = 0
        // R24/B1.b: `tradesLossSinEvidencia` NO se toca. Sincronizar el marcador con el broker no
        // invalida la evidencia ya perdida: el contador mide un defecto historico del proceso.
        // R24/B1.c: los manuales SI se reinician: setStats fija el marcador en absoluto, asi que
        // los incrementos manuales anteriores ya no forman parte de totalWins/totalLosses.
        resetManualCounters()
    }

    @Synchronized
    fun correctStats(wins: Int, losses: Int, lossStreak: Int? = null) {
        // R24/B1.b: `tradesLossSinEvidencia` NO se reinicia aqui (delega en setStats, que tampoco
        // lo toca). Corregir el marcador a mano no puede borrar la cuenta de liquidaciones sin
        // evidencia: es justo la correccion manual la que hace sospechoso el marcador.
        setStats(wins, losses, lossStreak)
        OverlayService.instance?.updateHUDView()
        android.util.Log.i("RiskManager", "Marcador corregido a: $wins W | $losses L (Racha=$currentLossStreak)")
    }

    @Synchronized
    fun getWinRate(): Float {
        val total = totalWins + totalLosses
        if (total == 0) return 0f
        return (totalWins.toFloat() / total) * 100f
    }

    @Synchronized
    fun getCurrentInvestmentAmount(subMode: AutonomousSubMode? = null): Float {
        val effectiveSubMode = subMode ?: currentSubMode
        if (effectiveSubMode == AutonomousSubMode.SNIPER) {
            return baseAmount // 100% libre de Martingala (M0 plano)
        }
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            var amt = baseAmount
            for (i in 0 until currentSorosStep) {
                amt *= (1f + sorosPayoutRate)
            }
            return Math.round(amt * 100f) / 100f
        }
        if (moneyManagementMode == MoneyManagementMode.FIXED_AMOUNT || !martingaleEnabled || currentLossStreak == 0) {
            return baseAmount
        }
        val effectiveLevel = currentLossStreak.coerceAtMost(maxMartingaleLevel)
        var amount = baseAmount
        for (i in 0 until effectiveLevel) {
            amount *= martingaleMultiplier
        }
        return amount
    }

    /**
     * R23A Iter 2: stake efectivo en unidades del broker. Ver [stakeEfectivo] en el companion.
     *
     * Es el valor que el broker REALMENTE debita: la cantidad visible en pantalla si existe, y si no
     * el calculado por la politica de money management. Todo registro de journal y toda validacion de
     * dimensionamiento deben usar esto, no `getCurrentInvestmentAmount()` a secas.
     */
    @Synchronized
    fun getEffectiveStake(subMode: AutonomousSubMode? = null): Double =
        stakeEfectivo(AutoTradeAccessibilityService.observedOrderAmount, getCurrentInvestmentAmount(subMode).toDouble())

    @Synchronized
    fun getMartingaleStatusBadge(subMode: AutonomousSubMode? = null): String {
        val effectiveSubMode = subMode ?: currentSubMode
        if (effectiveSubMode == AutonomousSubMode.SNIPER) {
            val amt = getCurrentInvestmentAmount(subMode)
            return "[🎯 M0 Plano | $$amt]"
        }
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            val amt = getCurrentInvestmentAmount(subMode)
            return "[Soros S$currentSorosStep/$sorosCycleTarget | $$amt]"
        }
        if (!martingaleEnabled) return "[OFF]"
        val level = "M$currentLossStreak"
        val amt = getCurrentInvestmentAmount(subMode)
        return "[$level | $$amt]"
    }

    @Synchronized
    fun resetSession(startBal: Double = 0.0) {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        consecutiveVoids = 0
        totalVoids = 0
        lastTradeTime = 0L
        if (startBal > 0.0) {
            sessionStartBalance = startBal
            sessionPeakBalance = startBal
        } else {
            sessionStartBalance = 0.0
            sessionPeakBalance = 0.0
        }
        trailingProfitLockTriggered = false
        // R24/B1.b: `tradesLossSinEvidencia` NO se reinicia al abrir sesion nueva. Es un contador
        // de proceso: reiniciar la sesion no devuelve la evidencia de saldo que ya se perdio.
        // R24/B1.c: los manuales SI, porque totalWins/totalLosses vuelven a 0.
        resetManualCounters()
        clearPendingTrade()
    }

    @Synchronized
    fun resetSniperSession() {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        totalVoids = 0
        consecutiveVoids = 0
        lastTradeTime = 0L
        // Misma linea base que resetStreakOnly(): sin esto el Trailing Lock sobrevive al reinicio
        val bal = AutoTradeAccessibilityService.latestObservedBalance
        sessionStartBalance = if (bal > 0.0) bal else 0.0
        sessionPeakBalance = sessionStartBalance
        trailingProfitLockTriggered = false
        // R24/B1.b: `tradesLossSinEvidencia` NO se reinicia en la sesion Francotirador. El
        // submodo cambia la politica de stake, no la calidad de la evidencia ya registrada.
        // R24/B1.c: los manuales SI, porque totalWins/totalLosses vuelven a 0.
        resetManualCounters()
        clearPendingTrade()
    }
}
