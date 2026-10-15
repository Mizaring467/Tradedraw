package com.example.tradedraw

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TradeAction {
    BUY, SELL
}

enum class AutoTradeMode {
    AUTONOMOUS,      // Ejecuta clics automáticos
    SEMIAUTOMATIC,   // Alerta y prepara dibujos, usuario opera
    DISABLED,        // Apagado
    SHADOW           // Ronda 21 (Frente E): Análisis y decisiones reales con decision_id, sin taps ni alteración de saldo/W-L
}

enum class AutonomousSubMode {
    CONSERVATIVE,    // Con gestión de riesgo estándar (Stop Loss, Take Profit, Cooldown)
    YOLO,            // Sin Stop Loss ni límites, operativa continua y desatendida
    SNIPER           // Modo Francotirador: Stake plano (M0), SL 2 derrotas, TP 2 victorias, máx 3 ops por sesión
}

class TradingEngine(
    private val context: Context,
    private val drawingView: CustomDrawingView,
    val riskManager: RiskManager,
    var calibrationManager: CalibrationManager? = null,
    val aiClient: AIClient = AIClient(context)
) {
    val chartViewportController = ChartViewportController(context, riskManager)
    val agentController = AutonomousAgentController(context, this, riskManager, chartViewportController)

    /**
     * Modo del motor. **Solo lectura desde fuera**: la unica forma de cambiarlo es
     * [setTradingMode], que registra quien lo pidio. Es deliberado que el setter sea privado:
     * en R14 hubo un rearme a `AUTONOMOUS` en una ronda desatendida y hubo que reconstruirlo a
     * posteriori desde un volcado de eventos. Un campo publico escribible obliga a ese trabajo
     * forense; un metodo con `source` obligatorio lo convierte en un dato.
     */
    var mode: AutoTradeMode = AutoTradeMode.DISABLED
        private set

    /**
     * Ultimo cambio de modo, para `/status`: `prev -> next | source=<caller>`. Cadena vacia si
     * nunca hubo transicion. NO contiene datos sensibles: solo nombres de modo y de metodo.
     */
    @Volatile
    var lastModeChange: String = ""
        private set

    /** Instante (ms epoch) del ultimo cambio de modo. 0 si nunca hubo transicion. */
    @Volatile
    var lastModeChangeTs: Long = 0L
        private set

    /**
     * Cambia el modo del motor registrando el origen. **Unico punto de escritura.**
     *
     * `source` es obligatorio y debe nombrar la ruta que lo pide (p. ej.
     * `"overlayCommandReceiver(action=AUTO)"`, `"bridge/mode"`, `"ui:showAutonomousSubModeDialog"`).
     * El `ORIGEN` de la pila se sigue guardando ademas del `source`, porque un `source` se escribe
     * a mano y una pila no: si los dos discrepan, el dato interesante es que discrepan.
     *
     * Devuelve `true` si el modo cambio de valor, `false` si era el mismo (en ese caso no se emite
     * evento, para no llenar el buffer de transiciones que no lo son).
     */
    /**
     * Siembra el estado inicial del modo para `/status`, sin cambiar su valor y sin emitir evento.
     *
     * Necesario porque el modo nace `DISABLED` en el inicializador de la propiedad y nunca pasa por
     * [setTradingMode]: sin esta marca, un motor recien creado reporta `lastModeChangeSource` vacio
     * y no se puede distinguir "arranco limpio" de "no se registro la transicion".
     */
    fun marcarModoInicial(source: String) {
        lastModeChange = "$mode -> $mode | source=$source"
        lastModeChangeTs = System.currentTimeMillis()
    }

    fun setTradingMode(newMode: AutoTradeMode, source: String): Boolean {        val anterior = mode
        if (newMode == anterior) return false

        mode = newMode
        val origen = modeChangeOrigin()
        lastModeChange = "$anterior -> $newMode | source=$source"
        lastModeChangeTs = System.currentTimeMillis()

        // Solo observabilidad: registrar el cambio NO lo altera. El ORIGEN es lo unico que permite
        // atribuir un rearme espontaneo: sin el, un cambio automatico y un toque humano son
        // indistinguibles.
        TradeEventLog.add(
            TradeEventLog.Kind.MODE_CHANGE,
            "de=$anterior a=$newMode source=$source origen=$origen"
        )

        if (newMode == AutoTradeMode.AUTONOMOUS) {
            agentController.start()
        } else {
            agentController.stop()
        }
        return true
    }

    /**
     * R15-A: pila de llamadas corta y legible para el ORIGEN de un cambio de modo.
     *
     * Se saltan los frames de la propia instrumentacion y los del runtime, y se devuelven los
     * primeros 5 utiles. Nunca devuelve cadena vacia: si todo falla, devuelve "desconocido"
     * explicito, porque un ORIGEN vacio se leeria como "no hay origen" en vez de "no se pudo
     * capturar" — y esa diferencia es justo lo que esta ronda intenta medir.
     */
    private fun modeChangeOrigin(): String {
        return try {
            val frames = Thread.currentThread().stackTrace
                .drop(1)
                .filterNot { it.className.startsWith("java.") || it.className.startsWith("kotlin.") }
                .filterNot { it.methodName.startsWith("access$") }
                // El propio cambiador no es un origen: quitar el frame evita que el primer
                // elemento util de la pila sea siempre el mismo metodo.
                .filterNot { it.className.endsWith("TradingEngine") && it.methodName == "setTradingMode" }
                .take(5)
                .map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            if (frames.isEmpty()) "desconocido" else frames.joinToString("<")
        } catch (e: Exception) {
            "desconocido"
        }
    }

    var autonomousSubMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE
        set(value) {
            field = value
            syntheticCandleEngine.subMode = value
        }

    var timeframe: CandleTimeframe = CandleTimeframe.M1
        set(value) {
            field = value
            syntheticCandleEngine.timeframe = value
            riskManager.timeframe = value
        }

    @Volatile
    var sniperShutdownReason: String? = null

    fun checkSniperSessionLimits() {
        if (autonomousSubMode == AutonomousSubMode.SNIPER) {
            val (canTrade, reason) = riskManager.canExecuteTrade(autonomousSubMode)
            if (!canTrade && (reason.contains("alcanzado", ignoreCase = true) || reason.contains("finalizada", ignoreCase = true) || reason.contains("Límite", ignoreCase = true))) {
                setTradingMode(AutoTradeMode.DISABLED, "engine:sniper-limite(checkSniperSessionLimits)")
                sniperShutdownReason = reason
                handler.post {
                    Toast.makeText(context, "🛑 $reason", Toast.LENGTH_LONG).show()
                    OverlayService.instance?.updateHUDView()
                }
            }
        }
    }
    val syntheticCandleEngine = SyntheticCandleEngine()
    val adaptiveLearningEngine = AdaptiveLearningEngine()

    var strategy: AutoTradeStrategy = AutoTradeStrategy.AUTO_ADAPTIVE
        set(value) {
            field = value
            syntheticCandleEngine.currentStrategy = value
        }
    var debugModeEnabled: Boolean = false

    val visionAnalyzer = VisionAnalyzer()
    private val autoDrawEngine = AutoDrawEngine(drawingView)
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 1000L

    private var pendingTradeHasObservedWin: Boolean = false
    private val isTradeResolving = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastFeedbackEmitTime: Long = 0L
    private var lastTradeResolutionTime: Long = 0L

    var latestAnalysisResult: VisionAnalysisResult? = null
        private set
    var latestAIResult: AIAnalysisResult? = null
        private set
    var latestAITimestamp: Long = 0L
        private set
    var framesAnalyzedCount: Long = 0L
        private set

    var onSignalListener: ((TradeAction, String) -> Unit)? = null
    var onTradeExecutedListener: ((TradeAction, Boolean) -> Unit)? = null
    var onFrameProcessedListener: ((VisionAnalysisResult) -> Unit)? = null

    init {
        syntheticCandleEngine.currentStrategy = strategy
        // Limpiar cualquier figura residual previa del bot (soportes/resistencias automáticas)
        drawingView.clearBotShapes()

        // Conectar callback: cuando el usuario arrastra una línea del bot, bloquearla
        drawingView.onBotShapeDragged = { key ->
            autoDrawEngine.lockLine(key)
            handler.post {
                Toast.makeText(context, "Línea $key bloqueada [Manual]", Toast.LENGTH_SHORT).show()
            }
        }

        // Conectar señales generadas matemáticamente por el motor Headless WebSocket.
        //
        // R23 Iter 3: el `decisionId` es DETERMINISTA (antes `UUID.randomUUID()`), para que el
        // comparador Live <-> Replay pueda emparejar la misma decision en ambos motores. La clave
        // usa el RIC normalizado y el MILISEGUNDO del tick que disparo la senal, no un reloj
        // leido en el callback: `System.currentTimeMillis()` aqui desalinearia el id respecto al
        // instante real de la senal y el replay (que usa el ts del tick) no coincidiria.
        syntheticCandleEngine.onSignalGenerated = { action, reason ->
            val signalTsMs = latestMarketTick?.timestampMs ?: System.currentTimeMillis()
            val assetName = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val decisionId = generateDeterministicDecisionId(assetName, signalTsMs, action.name)
            if (mode == AutoTradeMode.AUTONOMOUS || mode == AutoTradeMode.SHADOW) {
                executeHeadlessTrade(action, reason, decisionId)
            } else if (mode == AutoTradeMode.SEMIAUTOMATIC) {
                currentActiveSignal = ActiveSignal(
                    action = action,
                    title = "Señal Sniper WebSocket",
                    reason = reason,
                    timestamp = System.currentTimeMillis(),
                    decisionId = decisionId
                )
                handler.post {
                    onSignalListener?.invoke(action, reason)
                }
            }
        }
    }

    /** Desbloquea todas las líneas para que la IA las recalcule */
    fun unlockAllLines() {
        autoDrawEngine.unlockAllLines()
    }

    fun hasLockedLines(): Boolean = autoDrawEngine.hasLockedLines()

    data class ActiveSignal(
        val action: TradeAction,
        val title: String,
        val reason: String,
        val timestamp: Long,
        val expirySeconds: Int = 30,
        val decisionId: String = ""
    )

    data class EngineReasoning(
        val patternName: String,
        val probabilityPct: Int,
        val srFilterStatus: String,
        val actionPlan: String,
        val isFavorable: Boolean
    )

    data class PendingTradeSnapshot(
        val action: TradeAction,
        val startTime: Long,
        /**
         * Ronda 11 (T3): STRIKE en dominio de precio (no en píxeles). La liquidación compara este
         * valor contra el precio del tick WS. Antes era una Y de visión (0.0 en headless), así que
         * la comparación `latestPrice > entryY` era siempre cierta y el resultado era aleatorio.
         */
        val entryPrice: Double,
        val baseBalance: Double,
        val confidence: Float,
        val decisionId: String = "",
        val tradeId: String = ""
    )

    /** Minuto de época (epochMs / 60000) de la última orden ejecutada. Garantiza máx 1 trade por vela. */
    @Volatile
    var lastExecutedCandleEpochMinute: Long = -1L
        internal set

    var currentActiveSignal: ActiveSignal? = null
        private set

    fun clearActiveSignal() {
        currentActiveSignal = null
    }

    var latestMarketTick: MarketTick? = null
        private set

    /**
     * Umbral de frescura del feed WebSocket (ms). Un tick más viejo que esto se considera rancio
     * y veta la operación: nunca se degrada a otra fuente de datos.
     */
    private val FEED_MAX_AGE_MS = BinomoWebSocketClient.DEFAULT_FRESHNESS_MS

    /**
     * Proveedor de frescura del feed. Lo inyecta OverlayService con el cliente WebSocket real.
     * Sin proveedor (= null) o con resultado false, el motor veta por fail-closed.
     */
    var feedFreshnessProvider: (() -> Boolean)? = null

    /** true si el feed WebSocket está conectado y con tick reciente (<5000 ms). */
    fun isFeedFresh(): Boolean = feedFreshnessProvider?.invoke() ?: false

    /**
     * Proveedor de congelación del precio. Lo inyecta OverlayService con el cliente real.
     *
     * Cubre el caso "el feed está fresco pero el emisor no mueve el precio" (índice
     * sintético pegado como `Z-CRY/IDX`, rango relativo ~5e-10). Sin esta guarda el bot
     * decide CALL/PUT sobre una línea plana, que es equivalente a lanzar una moneda.
     *
     * Fail-closed al revés que la frescura: `null` (sin proveedor) NO veta, porque en
     * tests y en modo headless simulado no hay cliente WebSocket real.
     */
    var priceFrozenProvider: (() -> Boolean)? = null

    /** true si el emisor entrega ticks a ritmo normal pero el precio está inmóvil. */
    fun isPriceFrozen(): Boolean = priceFrozenProvider?.invoke() ?: false

    /**
     * Ronda 12 (B2, veto V2): rango relativo del precio en la ventana reciente.
     *
     * Es la señal que distingue un activo operable de uno plano. `null` (sin proveedor) devuelve
     * -1.0, que queda FUERA del rango de veto: en tests y en headless simulado no hay feed real
     * y no debe vetarse por una medida que no existe.
     */
    var priceRangeRatioProvider: (() -> Double)? = null

    /** Rango relativo reciente, o -1.0 si no hay feed del que medirlo. */
    fun recentPriceRangeRatio(): Double = priceRangeRatioProvider?.invoke() ?: -1.0

    /**
     * Ronda 20: proveedor del cliente WebSocket real. Lo inyecta OverlayService.
     *
     * Es un proveedor y no una referencia directa por el mismo motivo que el resto de proveedores
     * de esta clase: el motor debe poder construirse en tests sin socket. De aqui salen tanto el
     * activo que sirve el feed (para el guard de desajuste) como las recuperaciones por falta de
     * ticks (para `/status`), que son dos lecturas del MISMO objeto.
     */
    var binomoWebSocketClientRef: (() -> BinomoWebSocketClient?)? = null

    /** Activo que el feed esta sirviendo, o "" si no hay feed del que saberlo. */
    fun feedAsset(): String = binomoWebSocketClientRef?.invoke()?.activeAsset?.trim().orEmpty()

    /**
     * Ronda 20: disparos vetados por desajuste entre el activo de pantalla y el del feed.
     *
     * Es el contador que hace observable el guard. Si es 0 en una sesion donde el grafico y el
     * feed coincidian, el guard no intervino (criterio B.3 del PREREGISTRO_R20).
     */
    @Volatile
    var vetoActivoDesajustado: Long = 0L
        private set

    /** Ronda 21 (Frente E): órdenes simuladas autorizadas en modo SHADOW (cero taps, cero saldo alterado). */
    @Volatile
    var shadowOrdersGenerated: Long = 0L
        private set

    /** Ronda 20: recuperaciones de activo sin feed, para `/status`. */
    fun recuperacionesPorSinTicks(): Long = binomoWebSocketClientRef?.invoke()?.recuperacionesPorSinTicks ?: 0L

    /**
     * Ronda 12 (B4): limpieza del estado POR ACTIVO al detectar un cambio de activo.
     *
     * `SyntheticCandleEngine` y `BinomoWebSocketClient` ya limpian sus propios buffers al ver el
     * activo nuevo en el tick. Aqui se limpia lo que vive en el motor y pertenece al activo
     * anterior: la senal vigente (que se calculo sobre otro precio y otro canal) y el ultimo
     * analisis. NO se toca ninguna racha, contador de sesion ni el trade pendiente: esos son de
     * la CUENTA, no del activo, y deben sobrevivir por diseno.
     */
    fun onAssetChanged() {
        synchronized(this) {
            currentActiveSignal = null
            lastSignalReason = ""
            lastExecutedCandleEpochMinute = 0L
        }
        latestAnalysisResult = null
        latestAIResult = null
        latestAITimestamp = 0L
        // Ronda 15 / B2: la referencia de inoperabilidad es POR ACTIVO. Sin limpiarla, el activo
        // nuevo se compara contra la mediana de rango del anterior, que puede diferir en ordenes
        // de magnitud (medido: Z-CRY/IDX 2.5e-10 frente a ETHUSD-OTC 5e-4).
        inoperabilityDetector.onAssetChanged()
    }

    /**
     * Ronda 14 (B1): detector de mercado inoperable sobre el activo activo.
     *
     * AVISO, no decision: alimenta un banner y contadores. Nunca cambia de activo ni participa
     * en la decision de entrada. Ver `MarketInoperabilityDetector`.
     */
    val inoperabilityDetector = MarketInoperabilityDetector()

    /**
     * R23 Iter 4: buffer circular de los ultimos ticks VALIDADOS, expuesto por `GET /ticks`.
     *
     * Se alimenta en [onMarketTick], que es el punto donde el tick ya paso todos los filtros del
     * feed (frescura, salto implausible, activo) y va a entrar al motor. Registrar antes de esos
     * filtros volcaria ticks que el motor nunca vio y el replay no podria reproducir nada.
     */
    internal val tickBuffer = TickRingBuffer(TICK_BUFFER_CAPACITY)

    /**
     * Invocado cuando llega un nuevo micro-tick en tiempo real vía WebSocket.
     */
    fun onMarketTick(tick: MarketTick) {
        latestMarketTick = tick
        // El `asset` del MarketTick ya es el RIC del feed; se guarda tal cual para que el id
        // determinista y el replay coincidan sin pasar por el mapeo de nombre visible.
        tickBuffer.record(tick.timestampMs, tick.asset, tick.price)
        syntheticCandleEngine.subMode = autonomousSubMode
        syntheticCandleEngine.onNewTick(tick)

        // R14/B1: el aviso se evalua con cada tick. Solo registra evento en el FLANCO de subida
        // (cuando el aviso se enciende), o `/events` se llenaria con el mismo aviso repetido.
        // La frescura la aporta el propio tick: si el feed estuviera caido no llegaria ninguno.
        if (inoperabilityDetector.onTick(tick.price, tick.timestampMs, feedFresh = true)) {
            TradeEventLog.add(
                TradeEventLog.Kind.INOPERABLE,
                "INOPERABLE activo=${tick.asset} segundos=${inoperabilityDetector.holdSeconds} " +
                    "ratio=%.3e".format(Locale.US, inoperabilityDetector.lastRatio)
            )
            android.util.Log.w("TradingEngine", "⚠️ Mercado inoperable en ${tick.asset}")
        }

        // El análisis se publica desde el motor sintético de ticks (fuente única WebSocket).
        // Sin esto, latestAnalysisResult solo se poblaba en onNewFrame() (visión retirada por
        // consumo de batería) y el HUD/bridge reportaban ceros aunque el feed estuviera vivo.
        val analysis = buildAnalysisFromSyntheticEngine(tick)
        latestAnalysisResult = analysis
        onFrameProcessedListener?.invoke(analysis)

        // En modo Headless (sin frames de pantalla capturados), resolver trade por tiempo y balance de Binomo
        if (framesAnalyzedCount == 0L && riskManager.hasPendingTrade) {
            checkHeadlessTradeResolution()
        }
    }

    /**
     * Construye el análisis a partir del motor sintético de ticks y del último micro-tick.
     * Fuente única de verdad en modo WebSocket puro: no depende de la captura de pantalla.
     */
    private fun buildAnalysisFromSyntheticEngine(tick: MarketTick): VisionAnalysisResult {
        val distSup = syntheticCandleEngine.distanceToSupportRatio
        val distRes = syntheticCandleEngine.distanceToResistanceRatio
        val trend = syntheticCandleEngine.detectedTrend
        val rsi = syntheticCandleEngine.syntheticTickRsi
        val isChoppy = syntheticCandleEngine.cachedChoppiness || trend == TrendDirection.SIDEWAYS

        // Cálculo dinámico de probabilidades para termómetro del HUD y telemetría
        var callPower = 50
        var putPower = 50
        when (trend) {
            TrendDirection.UPTREND -> {
                callPower = 68
                putPower = 32
                if (distSup <= 0.25f) callPower += 10
                if (rsi in 40.0..68.0) callPower += 8
                if (tick.isBullishImpulse) callPower += 6
            }
            TrendDirection.DOWNTREND -> {
                callPower = 32
                putPower = 68
                if (distRes <= 0.25f) putPower += 10
                if (rsi in 32.0..60.0) putPower += 8
                if (tick.isBearishImpulse) putPower += 6
            }
            TrendDirection.SIDEWAYS -> {
                if (distSup <= 0.20f) { callPower = 62; putPower = 38 }
                else if (distRes <= 0.20f) { callPower = 38; putPower = 62 }
            }
        }
        callPower = callPower.coerceIn(15, 95)
        putPower = putPower.coerceIn(15, 95)
        // Termometro coherente: CALL + PUT = 100. Antes cada lado se ajustaba por separado y sumaban
        // hasta 118% (p.ej. 32% CALL / 86% PUT). El lado dominante manda; el otro es su complemento.
        if (callPower >= putPower) putPower = 100 - callPower else callPower = 100 - putPower

        val streak = when {
            syntheticCandleEngine.consecutiveUpTicks >= 2 -> "${syntheticCandleEngine.consecutiveUpTicks}T 🟢"
            syntheticCandleEngine.consecutiveDownTicks >= 2 -> "${syntheticCandleEngine.consecutiveDownTicks}T 🔴"
            trend == TrendDirection.UPTREND -> "TREND 🟢"
            trend == TrendDirection.DOWNTREND -> "TREND 🔴"
            else -> "1D ⚪"
        }

        return VisionAnalysisResult(
            trend = trend,
            distanceToSupportRatio = distSup,
            distanceToResistanceRatio = distRes,
            touchesSupport = distSup <= 0.15f,
            touchesResistance = distRes <= 0.15f,
            isNearSupportZone = distSup <= 0.30f,
            isNearResistanceZone = distRes <= 0.30f,
            tickVelocityNormalized = tick.velocity,
            isBullishImpulse = tick.isBullishImpulse,
            isBearishImpulse = tick.isBearishImpulse,
            candleSecond = tick.candleSecond,
            signalPowerCall = callPower,
            signalPowerPut = putPower,
            confluenceScoreCall = callPower,
            confluenceScorePut = putPower,
            isMarketSideways = isChoppy,
            streakBadge = streak
        )
    }

    fun onNewFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < PROCESS_INTERVAL_MS) return
        lastProcessTime = now
        framesAnalyzedCount++

        // 1. Obtener niveles de soporte/resistencia dibujados en TradeDraw
        val (supports, resistances) = drawingView.getSupportResistanceYLevels()

        // 2. Analizar frame visual con visión HSV (precio actual, velas, tendencia)
        val analysis = visionAnalyzer.analyzeChart(bitmap, supports, resistances, context, debugModeEnabled)
        latestAnalysisResult = analysis
        syntheticCandleEngine.updateVisionTrend(analysis.trend)

        // 3. Auto-dibujar escenario técnico en TradeDraw según la estrategia
        handler.post {
            autoDrawEngine.updateTechnicalDrawings()
            onFrameProcessedListener?.invoke(analysis)
        }

        // 4. Si hay una operación abierta en curso, supervisar el ciclo de vida y liquidación con Binomo
        if (riskManager.hasPendingTrade) {
            val elapsedSec = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000

            // Actualizar en vivo la línea de entrada (Verde si va ganando ITM, Roja si va perdiendo OTM).
            // Ronda 11 (T3): la comparación es en DOMINIO DE PRECIO (tick WS vs strike de entrada), no
            // en píxeles de visión. `analysis.currentPriceY` viene de la ruta de visión retirada y
            // traía ceros, así que el estado ITM/OTM era arbitrario.
            val livePrice = latestMarketTick?.price
            if (livePrice != null && livePrice > 0.0) {
                handler.post {
                    autoDrawEngine.updateTradeEntryLiveStatus(livePrice)
                }
            }

            // Ronda 11 (T3): el journal guarda el STRIKE REAL en dominio de precio. El parámetro se
            // llama `priceY` por historia, pero su contenido siempre fueron precios (1121.50, 641.87).
            val entryY = riskManager.pendingTradeEntryPrice.toFloat()
            val action = riskManager.pendingTradeAction
            val baseBalance = riskManager.pendingTradeBaseBalance
            val pendingConfidence = riskManager.pendingTradeConfidence
            val tradeStartMs = riskManager.pendingTradeStartTime

            // Ventana de resolución estricta al EXPIRAR la vela (según timeframe: 60s o 300s + ventana de liquidación del broker)
            val expDurationSec = timeframe.seconds
            val isExpired = elapsedSec >= (expDurationSec + 2)
            if (isExpired) {
                var isWin: Boolean? = null
                var isTie: Boolean = false
                var method = ""

                val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance() 
                    ?: AutoTradeAccessibilityService.latestObservedBalance

                // 1. Verificación por Saldo Real Inmutable de Binomo
                if (baseBalance > 0.0 && currentBal > 0.0) {
                    val diff = currentBal - baseBalance
                    if (diff > 10.0 && diff <= baseBalance * 0.5) {
                        // Binomo acreditó las ganancias (+1 W) de forma inmediata
                        isWin = true
                        method = "SALDO (+) Ganancia acreditada por Binomo: Diff=+$diff COP (${elapsedSec}s)"
                    } else if (diff < -10.0 && elapsedSec >= (expDurationSec + 12)) {
                        // Solo confirmar pérdida tras ventana completa de liquidación para no confundir retrasos del broker
                        isWin = false
                        method = "SALDO (-) Pérdida confirmada tras liquidación: Diff=$diff COP (${elapsedSec}s)"
                    } else if (Math.abs(diff) <= 10.0 && elapsedSec >= (expDurationSec + 15)) {
                        // Saldo inalterado tras liquidación: La orden NUNCA fue procesada por Binomo (clic no recibido)
                        isTie = true
                        method = "ORDEN NO PROCESADA (Diff=$diff -> Saldo inalterado tras ${elapsedSec}s)"
                    }
                } else if (elapsedSec >= (expDurationSec + 5)) {
                    // 2. Fallback de resolución por Acción de Precio (si el saldo no es legible por UI).
                    // Ronda 11 (T3): se resuelve en DOMINIO DE PRECIO con el tick WS. El código previo
                    // comparaba Y de visión con signo invertido (`isCall -> exitY < entryY`) y con
                    // `entryY` en 0 fuera de la ruta de visión, así que el fallback era inoperante.
                    val entryPrice = riskManager.pendingTradeEntryPrice
                    val exitPrice = latestMarketTick?.price ?: 0.0
                    if (entryPrice > 0.0 && exitPrice > 0.0 && action != null) {
                        when (StrikeCalculator.moneyness(action, entryPrice, exitPrice)) {
                            TradeMoneyness.TIE -> {
                                isTie = true
                                method = "PRECIO (=) Empate técnico: Entry=$entryPrice vs Exit=$exitPrice (${elapsedSec}s)"
                            }
                            TradeMoneyness.ITM -> {
                                isWin = true
                                method = "PRECIO (+) Resolución por acción de precio: Entry=$entryPrice vs Exit=$exitPrice (${elapsedSec}s)"
                            }
                            TradeMoneyness.OTM -> {
                                isWin = false
                                method = "PRECIO (-) Resolución por acción de precio: Entry=$entryPrice vs Exit=$exitPrice (${elapsedSec}s)"
                            }
                        }
                    } else if (elapsedSec >= (expDurationSec + 15)) {
                        isTie = true
                        method = "TIMEOUT ${expDurationSec + 15}s (Sin saldo legible -> Cancelación preventiva)"
                    }
                }

                if (isWin != null || isTie) {
                    // Bloqueo Atómico: Solo UN frame puede liquidar la operación activa.
                    if (!isTradeResolving.compareAndSet(false, true)) {
                        return
                    }
                    android.util.Log.d("TradingEngine", "Liquidación atómica de trade único: Win=$isWin, Tie=$isTie [$method]")
                    val finalWin = isWin ?: false
                    handler.post {
                        pendingTradeHasObservedWin = false
                        val curTrend = analysis.trend.name
                        val supDist = analysis.distanceToSupportRatio
                        val resDist = analysis.distanceToResistanceRatio
                        val tickVel = analysis.tickVelocityNormalized
                        val imp = if (analysis.isBullishImpulse) "BULLISH" else if (analysis.isBearishImpulse) "BEARISH" else "NEUTRAL"
                        val reg = if (analysis.isMarketSideways) "SIDEWAYS" else if (analysis.isConsolidationTight) "TIGHT" else if (analysis.isDojiOrLowVolume) "DOJI" else "TRENDING"
                        val sec = analysis.candleSecond

                        if (isTie) {
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getEffectiveStake(), baseBalance, "TIE", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "TIE", tradeStartMs, decisionId = riskManager.pendingTradeDecisionId, tradeId = riskManager.pendingTradeTradeId)
                            riskManager.recordTradeVoid()
                            autoDrawEngine.clearTradeEntry()
                            Toast.makeText(context, "⚪ EMPATE EN BINOMO (Reembolso de capital)", Toast.LENGTH_LONG).show()
                            OverlayService.instance?.updateHUDView()
                        } else if (finalWin) {
                            val resolved = riskManager.resolvePendingTradeWin()
                            if (!resolved) {
                                riskManager.recordTradeWin(force = true)
                            }
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getEffectiveStake(), baseBalance, "WIN", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "WIN", tradeStartMs, decisionId = riskManager.pendingTradeDecisionId, tradeId = riskManager.pendingTradeTradeId)
                            adaptiveLearningEngine.recordTradeOutcome(true, context)
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, true)
                        } else {
                            val resolved = riskManager.resolvePendingTradeLoss()
                            if (!resolved) {
                                riskManager.recordTradeLoss(force = true)
                            }
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getEffectiveStake(), baseBalance, "LOSS", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "LOSS", tradeStartMs, decisionId = riskManager.pendingTradeDecisionId, tradeId = riskManager.pendingTradeTradeId)
                            adaptiveLearningEngine.recordTradeOutcome(false, context)
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "⚠️ OPERACIÓN PERDIDA (+1 L)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, false)
                        }
                        isTradeResolving.set(false)
                        lastTradeResolutionTime = System.currentTimeMillis()
                        checkSniperSessionLimits()
                    }
                }
            }
        }

        val timeSinceLastResolution = System.currentTimeMillis() - lastTradeResolutionTime
        // Espaciado inteligente: Si la última operación fue derrota, enfriamiento de 45s para no repetir en la misma micro-tendencia
        val minSpacingMs = if (riskManager.currentLossStreak > 0) 45000L else 12000L
        val isSpacingCooldown = lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs

        // 5. Evaluar señal de trading puramente local y cuantitativa (0ms latencia, sin llamadas remotas lentas)
        if (!riskManager.hasPendingTrade && !isSpacingCooldown && mode != AutoTradeMode.DISABLED) {
            val localSignal = evaluateStrategySignal(strategy, analysis)

            if (localSignal != null && !riskManager.hasPendingTrade) {
                val sec = analysis.candleSecond
                // R14/A3: la ventana se consulta a traves del flag `entryEarliestSecond`, cuyo
                // valor por defecto (1) reproduce exactamente el `sec in 1..5` de siempre.
                val isTimingVetoed = !MarketTickFilters.isInEntryWindow(sec) || analysis.isTimingVetoed
                val isStrictTimingWindow = MarketTickFilters.isInEntryWindow(sec)
                val isChoppy = syntheticCandleEngine.isChoppinessDetected() || analysis.isMicroRangeChoppy
                val isTickVelocityConfirmed = when (localSignal) {
                    TradeAction.BUY -> !analysis.isBearishImpulse || analysis.tickVelocityNormalized >= -0.08f
                    TradeAction.SELL -> !analysis.isBullishImpulse || analysis.tickVelocityNormalized <= 0.08f
                }

                if (isChoppy) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal suprimida por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
                } else if (isTimingVetoed) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal bloqueada por VETO de timing :15-:55 (⏱ ${sec}s)")
                } else if (!isStrictTimingWindow) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal pospuesta: fuera de ventana sniper :00 (:58-:03) (⏱ ${sec}s)")
                } else if (!isTickVelocityConfirmed) {
                    android.util.Log.d("TradingEngine", "Señal local $localSignal bloqueada por velocidad de tick adversa (velNorm=${analysis.tickVelocityNormalized}, velY=${analysis.tickVelocityY})")
                } else {
                    val description = if (lastSignalReason.isNotBlank()) lastSignalReason else "Estrategia Local (${strategy.name})"
                    val localConfidence = when {
                        analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut -> 0.95f
                        analysis.isRejectionCall || analysis.isRejectionPut || analysis.hasBottomRejectionWick || analysis.hasTopRejectionWick -> 0.90f
                        analysis.isEngulfingCall || analysis.isEngulfingPut -> 0.85f
                        analysis.isChoqueCall || analysis.isChoquePut || analysis.isChoquePullbackCall || analysis.isChoquePullbackPut -> 0.80f
                        analysis.is3VelasCall || analysis.is3VelasPut || analysis.isExhaustion3CandlesCall || analysis.isExhaustion3CandlesPut -> 0.75f
                        localSignal == TradeAction.BUY -> Math.max(analysis.confluenceScoreCall, Math.max(analysis.signalPowerCall, if (analysis.isCallSignal) analysis.signalScore else 50)) / 100f
                        localSignal == TradeAction.SELL -> Math.max(analysis.confluenceScorePut, Math.max(analysis.signalPowerPut, if (analysis.isPutSignal) analysis.signalScore else 50)) / 100f
                        else -> 0.80f
                    }
                    handleSignal(localSignal, analysis, bitmap, description, localConfidence)
                }
            }
        }
    }

    var lastSignalReason: String = ""
        private set

    fun evaluateStrategySignal(
        strategy: AutoTradeStrategy = this.strategy,
        analysis: VisionAnalysisResult
    ): TradeAction? {
        val (action, reason) = evaluateStrategySignalWithReason(
            strategy = strategy,
            analysis = analysis,
            syntheticEngine = syntheticCandleEngine,
            subMode = autonomousSubMode,
            latestTick = latestMarketTick
        )
        lastSignalReason = reason
        return action
    }

    companion object {
        /**
         * Ronda 12 (B2, veto V2): rango relativo minimo para considerar que un feed tiene
         * volatilidad medible. Congelado en `workflows/PREREGISTRO_R12.md` ANTES de medir su
         * efecto. Referencias medidas: ETHUSD-OTC 4,164e-02 (3 ordenes por encima), Z-CRY/IDX
         * 2,547e-09 (4 ordenes por debajo). El umbral separa ambos casos con holgura amplia.
         *
         * R23A Iter 1: SIGUE VALIENDO PARA TODO ACTIVO QUE NO SEA `IDX`. No se ha relajado. La
         * clasificacion por familia vive en [rangoRelativoMinimoParaActivo], que devuelve este
         * mismo valor para cualquier activo no sintetico.
         */
        const val RANGO_RELATIVO_MINIMO = 1e-5

        /**
         * R23A Iter 1: umbral de volatilidad minima para activos sinteticos (`IDX`).
         *
         * POR QUE EXISTE. RANGO_RELATIVO_MINIMO=1e-5 se congelo en PREREGISTRO_R12 para separar
         * ETHUSD-OTC (4,164e-02) de Z-CRY/IDX (2,547e-09). Ese umbral NO es un error para los
         * activos para los que se calibro, pero aplicado al indice sintetico deja el veto V2
         * matematicamente imposible de superar: en las 4 iteraciones de R23 se midieron 468 muestras
         * de rango relativo con mediana ~2,9e-10 y maximo 6,8e-10, o sea 4 ordenes por debajo de
         * 1e-5. El resultado observado es 244/244 decisiones vetadas por V2_RANGE y cero decisiones
         * operables.
         *
         * POR QUE 1e-11 Y NO UN VALOR INVENTADO. `BinomoWebSocketClient.isPriceFrozen` ya usa
         * exactamente este umbral para la misma familia de activos (misma clase de problema: paso de
         * tick sub-micro), asi que el valor NO es nuevo: se reutiliza un umbral ya medido y en
         * produccion para el mismo activo, en vez de calibrar uno a ojo.
         *
         * MARGEN. Con los ticks reales de `loops/r23/iter_4/live_ticks.csv` (2000 ticks,
         * Z-CRY/IDX): rango relativo global 1,885e-09, y el rango de una ventana de 16 ticks ronda
         * 2,9e-10 (mediana en vivo). Ambos quedan ~1,5-2 ordenes POR ENCIMA de 1e-11, asi que el
         * activo pasa el veto con holgura y sin rozar el ruido de redondeo (0,0 exacto o el caso
         * degenerado ~5e-12 siguen vetados).
         *
         * ADVERTENCIA DE PREREGISTRO (ver §3 del reporte de la iteracion). PREREGISTRO_R12 §2 dice
         * literalmente que V2 "no se aplica a un activo concreto: es un veto por condicion de feed,
         * no por nombre", y que "cualquier cambio posterior exige un preregistro nuevo". Este cambio
         * SI introduce un umbral por familia de activo. La clasificacion esta tecnicamente fundada
         * (el precio del indice no es comparable en escala con el de un par OTC) y reutiliza un
         * umbral ya existente, pero MIENTRAS NO EXISTA UN PREREGISTRO_R23A FIRMADO, las muestras
         * recogidas con este umbral NO son comparables con las de R12 ni validas para confirmar edge.
         */
        const val RANGO_RELATIVO_MINIMO_IDX = 1e-11

        /**
         * Umbral de volatilidad aplicable a [asset]: 1e-11 para sinteticos (`IDX`), 1e-5 para el
         * resto. La discriminacion es por subcadena `IDX` (misma convencion que
         * `BinomoWebSocketClient.isPriceFrozen` y `AssetRicMapper`), asi que sirve tanto con el
         * nombre visible ("Crypto IDX") como con el RIC ("Z-CRY/IDX").
         */
        fun rangoRelativoMinimoParaActivo(asset: String): Double =
            if (asset.contains("IDX", ignoreCase = true)) RANGO_RELATIVO_MINIMO_IDX
            else RANGO_RELATIVO_MINIMO

        /**
         * R23A Iter 1: autorizacion de operacion autonoma. Funcion pura para poder fijarla por test.
         *
         * Fail-closed: solo el valor EXPLICITAMENTE demo autoriza. Se aisla en su propia funcion (en
         * vez de un `if` suelto) para que la regla quede congelada por test y no se diluya en futuras
         * ediciones del guard.
         */
        fun puedeOperarAutonomoEnDemo(esDemo: Boolean): Boolean = esDemo

        /**
         * R23 Iter 4: capacidad del buffer circular de ticks expuesto por `GET /ticks`.
         *
         * 2000 entradas cubren ~20-30 min al ritmo medido del feed (1-2 tick/s), que es exactamente
         * la ventana de validacion live de R23. Es un tope FIJO a proposito: el buffer vive en un
         * servicio permanente, y sin tope seria una fuga de memoria en vez de una mejora.
         */
        const val TICK_BUFFER_CAPACITY = 2000

        /**
         * R23 Iter 3: `decision_id` determinista para poder emparejar Live <-> Replay.
         *
         * Antes el id vivo era `UUID.randomUUID()`: la paridad resultaba "NO EVALUABLE" (0 pares)
         * porque no habia forma de reconocer la misma decision en los dos lados.
         *
         * DOS DEFECTOS DE LA CLAVE INGENUA `asset-epochSec-action`, medidos sobre las 17 filas de
         * `loops/r23/iter_2/decisions.csv` (agrupacion por (asset, epochSec, action)): solo 9
         * llaves unicas, 8 filas colisionadas. Es decir, colapsar la clave a ese triple fusionaria
         * decisiones distintas. Dos causas:
         *
         *  1. **Varios vetos comparten (activo, segundo, accion) en la misma vela.** El motor
         *     evalua la misma vela en ticks sucesivos, asi que el trio se repite y el id repetido
         *     haria que el comparador empareje una decision con la de otro tick.
         *  2. **El activo se nombra distinto en cada lado.** En vivo llega el nombre VISIBLE de
         *     pantalla (`"Crypto IDX"`, via `latestObservedAsset`); en replay, el RIC del feed
         *     (`"Z-CRY/IDX"`). Hashear los dos tal cual da ids distintos para la MISMA decision.
         *
         * Correcciones aplicadas aqui:
         *  - El activo se normaliza a RIC con [AssetRicMapper.ricParaActivoVisible], que es
         *    idempotente sobre sus propias salidas: `"Crypto IDX"` y `"Z-CRY/IDX"` colapsan al
         *    mismo RIC, y pasar un RIC ya canonico lo deja igual.
         *  - La clave lleva el timestamp en MILISEGUNDOS ([signalTimestampMs]), no en segundos:
         *    los distintos ticks de una misma vela colisionaban al agrupar por segundo (los 8
         *    solapes medidos arriba). Aceptar ms no rompe al llamador: si recibe un valor menor a
         *    1e11 se asume que viene en segundos y se escala, de modo que la firma de la spec
         *    (`... , epochSecond, ...`) sigue funcionando.
         *
         * LIMITE CONOCIDO Y ACEPTADO: dos decisiones del mismo activo en el mismo MILISEGUNDO y
         * con la misma accion comparten id. A 1-2 ticks/s el caso es practicamente imposible, y el
         * comparador agrupa por id en vez de por posicion, asi que una colision solo pierde una
         * fila, no corrompe el emparejamiento. Se documenta en vez de inventar un contador de
         * secuencia, que no seria reproducible entre los dos motores.
         */
        fun generateDeterministicDecisionId(asset: String, signalTimestampMs: Long, action: String): String {
            val ric = AssetRicMapper.ricParaActivoVisible(asset.trim())
            // Acepta segundos (valor < 1e11) o milisegundos; la clave siempre sale en ms.
            val nonceMs = if (signalTimestampMs > 100_000_000_000L) signalTimestampMs else signalTimestampMs * 1000L
            val key = "${ric}_${nonceMs}_${action.trim().uppercase()}"
            return java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
        }

        internal fun tieJournalResult(isTie: Boolean, saldoFresco: Boolean): String? =
            if (!isTie) null else if (saldoFresco) "TIE" else "TIE_INCIERTO"

        internal fun isBalanceFresh(instanceAvailable: Boolean, lastReadMs: Long, nowMs: Long): Boolean =
            instanceAvailable && nowMs - lastReadMs < 3000L

        internal fun recordTieVoidIfFresh(riskManager: RiskManager, saldoFresco: Boolean) {
            if (saldoFresco) riskManager.recordTradeVoid()
        }

        /**
         * Ronda 20: decide si se debe VETAR por desajuste entre el activo del feed y el visible en
         * pantalla. Devuelve el motivo del veto, o `null` si no procede vetar.
         *
         * Funcion pura a proposito: es la unica forma de fijar por test el comportamiento
         * fail-closed sin montar medio motor.
         *
         * Se compara RIC contra RIC. `activoPantallaVisible` es el nombre VISIBLE
         * (`"Aave (OTC)"`), asi que se pasa por el MISMO mapeo que usa la suscripcion: compararlo
         * en crudo daria desajuste SIEMPRE y el bot no dispararia jamas.
         *
         * FAIL-CLOSED (criterio B.4 del PREREGISTRO_R20): si el feed sirve un activo CONOCIDO y la
         * pantalla no lo confirma, se veta. Sin dato de pantalla no se autoriza. Es coherente con
         * el resto del sistema: sin servicio de accesibilidad el bot tampoco puede pulsar, asi que
         * exigir la confirmacion de pantalla no quita ninguna capacidad real.
         */
        internal fun motivoVetoPorDesajusteDeActivo(activoPantallaVisible: String, activoFeed: String): String? {
            val visible = activoPantallaVisible.trim()
            val feed = activoFeed.trim()
            // Si ni siquiera se sabe que sirve el feed, no hay nada que comparar: se abstiene. El
            // guard de feed rancio y el veto V2 ya cubren ese caso. En produccion `activoFeed`
            // nunca esta vacio (el cliente siempre tiene un activo activo).
            if (feed.isBlank()) return null
            if (visible.isBlank()) return "DESAJUSTE pantalla=DESCONOCIDA feed=\"$feed\""
            val ricPantalla = AssetRicMapper.ricParaActivoVisible(visible)
            return if (ricPantalla.equals(feed, ignoreCase = true)) null
            else "DESAJUSTE pantalla=\"$visible\" ric=$ricPantalla feed=\"$feed\""
        }

        fun evaluateStrategySignal(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine? = null,
            subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
            latestTick: MarketTick? = null
        ): TradeAction? {
            val (action, _) = evaluateStrategySignalWithReason(strategy, analysis, syntheticEngine, subMode, latestTick)
            return action
        }

        fun evaluateSniperConfluences(
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine?,
            latestTick: MarketTick?
        ): Pair<TradeAction?, String> {
            // Confluencia 1: Activo Forex Real válido (Veto estricto a Sintéticos y OTC)
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic) {
                return Pair(null, "⚠️ Veto Francotirador: Activo sintético/OTC ($currentAsset) prohibido para dinero real. Selecciona un par Forex real en Binomo")
            }
            val classification = AutoTradeAccessibilityService.classifyAsset(currentAsset)
            if (classification != AssetClassification.FOREX_REAL) {
                return Pair(null, "⚠️ Francotirador: Activo ($currentAsset) no es un par Forex real válido. Selecciona EUR/USD, GBP/USD, etc.")
            }

            // Confluencia 4 (Timing): Entrada estricta en el segundo :58s-:59s (o fin de ciclo de vela según timeframe)
            val tf = syntheticEngine?.timeframe ?: CandleTimeframe.M1
            val ts = latestTick?.timestampMs ?: System.currentTimeMillis()
            val inTimingWindow = if (analysis.candleSecond > 0 && tf == CandleTimeframe.M1) {
                MarketTickFilters.isInEntryWindow(analysis.candleSecond)
            } else {
                tf.isSniperTimingWindow(ts)
            }
            if (!inTimingWindow) {
                val cycleSec = tf.getCycleSecond(ts)
                val cycleRem = tf.seconds - cycleSec
                val remMin = cycleRem / 60
                val remSec = cycleRem % 60
                val remStr = if (tf == CandleTimeframe.M5) "%d:%02d".format(Locale.US, remMin, remSec) else ":%02ds".format(Locale.US, remSec)
                return Pair(null, "⏳ Francotirador en espera: timing estricto :01s-:05s [${tf.label}] (restante: $remStr)")
            }

            val candleList = analysis.candleList
            val c0 = candleList.firstOrNull()
            val c1 = if (candleList.size >= 2) candleList[1] else null

            val synCurrent = syntheticEngine?.currentCandle
            val synPrev = syntheticEngine?.closedCandles?.lastOrNull()

            // Confluencia 3: Vela previa o actual con mecha de rechazo >= 40% del rango total en dirección contraria al nivel
            val hasBottomRejection40 = (c0 != null && c0.bottomWickRatio >= 0.40f) ||
                (c1 != null && c1.bottomWickRatio >= 0.40f) ||
                (synCurrent != null && synCurrent.lowerWickRatio >= 0.40f) ||
                (synPrev != null && synPrev.lowerWickRatio >= 0.40f) ||
                analysis.hasBottomRejectionWick
            val hasTopRejection40 = (c0 != null && c0.topWickRatio >= 0.40f) ||
                (c1 != null && c1.topWickRatio >= 0.40f) ||
                (synCurrent != null && synCurrent.upperWickRatio >= 0.40f) ||
                (synPrev != null && synPrev.upperWickRatio >= 0.40f) ||
                analysis.hasTopRejectionWick

            // Confluencia 2: Rebote claro en nivel de Soporte (para CALL) o Resistencia (para PUT) validado
            val touchesSupport = analysis.touchesSupport || analysis.isNearSupportZone ||
                (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.15f)
            val touchesResistance = analysis.touchesResistance || analysis.isNearResistanceZone ||
                (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.15f)

            // Confluencia 4 (Micro-velocidad a favor y sin vela sobreextendida):
            val velNorm = analysis.tickVelocityNormalized
            val tickVel = latestTick?.velocity ?: 0f
            // Ronda 7-fix: umbral relativo a la escala del precio (IDX ~641, ETH ~1049).
            val tickPriceRef = latestTick?.price ?: 0.0
            val vThresh = if (tickPriceRef > 0.0) (tickPriceRef * 0.00004).toFloat() else 0.0001f
            val velCallFavor = (velNorm > vThresh || tickVel > vThresh || analysis.isBullishImpulse) &&
                !analysis.isBearishImpulse && velNorm >= -vThresh && tickVel >= -vThresh
            val velPutFavor = (velNorm < -vThresh || tickVel < -vThresh || analysis.isBearishImpulse) &&
                !analysis.isBullishImpulse && velNorm <= vThresh && tickVel <= vThresh

            val isOverextendedCall = (syntheticEngine?.isBullishOverextended == true) ||
                (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN)
            val isOverextendedPut = (syntheticEngine?.isBearishOverextended == true) ||
                (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED)

            // CALL: Soporte validado + Mecha inferior >= 40% + Micro-velocidad alcista + No sobreextendido
            if (touchesSupport && hasBottomRejection40 && velCallFavor && !isOverextendedCall) {
                return Pair(TradeAction.BUY, "🎯 FRANCOTIRADOR [CALL ${tf.label}]: Soporte validado + Mecha rechazo ≥40% + Micro-velocidad alcista")
            }

            // PUT: Resistencia validada + Mecha superior >= 40% + Micro-velocidad bajista + No sobreextendido
            if (touchesResistance && hasTopRejection40 && velPutFavor && !isOverextendedPut) {
                return Pair(TradeAction.SELL, "🎯 FRANCOTIRADOR [PUT ${tf.label}]: Resistencia validada + Mecha rechazo ≥40% + Micro-velocidad bajista")
            }

            return Pair(null, "🎯 Francotirador: Confluencias incompletas (S/R, Mecha ≥40%, Vel, :58-:59s)")
        }

        fun evaluateStrategySignalWithReason(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine? = null,
            subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
            latestTick: MarketTick? = null
        ): Pair<TradeAction?, String> {
            // MODO FRANCOTIRADOR: Gatillo de 4 Confluencias Simultáneas
            if (subMode == AutonomousSubMode.SNIPER) {
                return evaluateSniperConfluences(analysis, syntheticEngine, latestTick)
            }
            // Filtro Anti-Choppy / Micro-Rango Cuantitativo (<0.05% con alternancia de ticks sin dirección clara)
            val isChoppy = analysis.isMicroRangeChoppy || (syntheticEngine != null && syntheticEngine.isChoppinessDetected())
            if (isChoppy) {
                return Pair(null, "⚠️ Veto Anti-Choppy: Micro-rango (<0.05%) con alternancia de ticks sin dirección clara")
            }

            // Filtro anti-mercado lateral / Dojis / Consolidación estrecha
            if (analysis.isMarketSideways) {
                return Pair(null, "⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad")
            }
            if (analysis.isConsolidationTight) {
                return Pair(null, "⏳ Rango estrecho / Sin volatilidad: Esperando expansión")
            }

            val sec = analysis.candleSecond
            val isStrictTimingWindow = MarketTickFilters.isStrictTimingWindow(sec) // :58 a :03
            val isTimingVetoed = MarketTickFilters.isTimingVetoed(sec) // :06 a :57
            val isInstitutionalTrap = analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut
            val isSniperPullbackTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalCall || analysis.isPullbackAgainstSignalPut || analysis.isPullbackSniperCall || analysis.isPullbackSniperPut)

            // Veto universal de entrada tardía (:06 a :57)
            if (isTimingVetoed && !isInstitutionalTrap) {
                return Pair(null, "⏳ Entrada tardía - Veto Timing Estricto: Fuera de ventana sniper :01-:05 (⏱ ${sec}s)")
            }

            // Veto de entrada si está fuera de la ventana estricta :01-:05 (salvo trampas institucionales)
            if (!isStrictTimingWindow && !isInstitutionalTrap && !isSniperPullbackTrigger) {
                return Pair(null, "⏳ Entrada tardía (${sec}s): Fuera de ventana sniper :01-:05")
            }

            val isSniperPullbackCallTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalCall || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.GREEN && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY >= analysis.candleList[1].bodyTopY - 3f && analysis.currentPriceY <= analysis.candleList[1].bodyTopY + analysis.candleList[1].bodyHeight * 0.55f)) &&
                !analysis.isDojiOrLowVolume

            val isSniperPullbackPutTrigger = (MarketTickFilters.isInEntryWindow(sec) || analysis.isSniperPullbackWindow) &&
                (analysis.isPullbackAgainstSignalPut || (analysis.candleList.size >= 2 && analysis.candleList[1].type == CandleType.RED && analysis.candleList[1].bodyHeight >= 15f && analysis.currentPriceY <= analysis.candleList[1].bodyBottomY + 3f && analysis.currentPriceY >= analysis.candleList[1].bodyBottomY - analysis.candleList[1].bodyHeight * 0.55f)) &&
                !analysis.isDojiOrLowVolume

            val rawResult = when (strategy) {
                AutoTradeStrategy.AUTO_ADAPTIVE -> {
                    // Modo Automático por Detección de Régimen de Mercado (Regime Engine en 2 Etapas)
                    val regimeResult = MarketRegimeClassifier.classify(analysis, syntheticEngine)
                    val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                    val inUptrend = analysis.trend == TrendDirection.UPTREND

                    when (regimeResult.regime) {
                        MarketRegime.CHOPPY_NOISE -> {
                            Pair(null, "⏸ Auto Standby [${regimeResult.reason}] -> Sin trade")
                        }

                        MarketRegime.RANGING_CHANNEL -> {
                            // En Rango S/R: Solo se operan Reversiones en extremos (Trampas, Sobreextensión RSI, Mechas de Rechazo, Envolventes en S/R y Agotamiento)
                            when {
                                // 1. Trampa Institucional / Falso Rompimiento en S/R (95% confluencia)
                                !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isFalseBreakoutCall ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Trampa en Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && analysis.isFalseBreakoutPut ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Trampa en Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                                // 2. Reversión Cuantitativa por Sobreextensión en Extremos S/R
                                !inUptrend && (analysis.isNearResistanceZone || analysis.touchesResistance || analysis.distanceToResistanceRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.20f)) &&
                                (syntheticEngine?.isBullishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN) || analysis.isPriceNearTop) &&
                                (!analysis.hasStrongMomentumUp && !analysis.isValidBreakoutCall && (analysis.tickVelocityNormalized <= 0.05f || analysis.isBearishImpulse || analysis.hasTopRejectionWick || analysis.isRejectionPut || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))) -> {
                                    val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                                    Pair(TradeAction.SELL, "🎯 Auto [Reversión por Sobreextensión en Resistencia$rsiStr | Rango S/R | ⏱ ${sec}s] -> PUT")
                                }

                                !inDowntrend && (analysis.isNearSupportZone || analysis.touchesSupport || analysis.distanceToSupportRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.20f)) &&
                                (syntheticEngine?.isBearishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED) || analysis.isPriceNearBottom) &&
                                (!analysis.hasStrongMomentumDown && !analysis.isValidBreakoutPut && (analysis.tickVelocityNormalized >= -0.05f || analysis.isBullishImpulse || analysis.hasBottomRejectionWick || analysis.isRejectionCall || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))) -> {
                                    val rsiStr = if (syntheticEngine != null) " | RSI: ${syntheticEngine.syntheticTickRsi.toInt()}" else ""
                                    Pair(TradeAction.BUY, "🎯 Auto [Reversión por Sobreextensión en Soporte$rsiStr | Rango S/R | ⏱ ${sec}s] -> CALL")
                                }

                                // 3. Mechas de Rechazo y Absorción en S/R (MT_REJECTION)
                                !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) &&
                                    (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Mecha Rechazo Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) &&
                                    (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Mecha Rechazo Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                                // 4. Vela Envolvente en Zona S/R (85% confluencia)
                                !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isEngulfingCall &&
                                    (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && analysis.isEngulfingPut &&
                                    (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                                // 5. Agotamiento de 3 Velas Consecutivas (MT_3_VELAS)
                                !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Agotamiento 3 Rojas Alcista | Rango S/R | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Agotamiento 3 Verdes Bajista | Rango S/R | ⏱ ${sec}s] -> PUT")

                                // 6. Rebote Clásico con confirmación de timing
                                !inDowntrend && !analysis.hasStrongMomentumDown && analysis.touchesSupport && analysis.isPullbackSniperCall ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Rebote en Soporte | Rango S/R | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && analysis.touchesResistance && analysis.isPullbackSniperPut ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Rebote en Resistencia | Rango S/R | ⏱ ${sec}s] -> PUT")

                                else -> Pair(null, "⏳ Auto [Rango S/R]: Esperando aproximación a extremo con rechazo")
                            }
                        }

                        MarketRegime.STRONG_TREND_UP -> {
                            // En Tendencia Alcista: Únicamente entradas a favor de tendencia (Pullback Sniper o Vela Envolvente)
                            when {
                                !analysis.hasStrongMomentumDown && isSniperPullbackCallTrigger ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Sniper Pullback :0${sec}s | Tendencia Alcista] -> CALL")
                                !analysis.hasStrongMomentumDown && analysis.isEngulfingCall && (analysis.isPullbackSniperCall || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Vela Envolvente Soporte | Tendencia Alcista | ⏱ ${sec}s] -> CALL")
                                !analysis.hasStrongMomentumDown && (analysis.confluenceScoreCall >= 85 || analysis.signalPowerCall >= 85) -> {
                                    if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                        Pair(null, "⚠️ Veto: Tendencia alcista en techo de canal sin retroceso")
                                    } else {
                                        val score = Math.max(analysis.confluenceScoreCall, analysis.signalPowerCall)
                                        Pair(TradeAction.BUY, "🎯 Auto [Confluencia Fuerte Alcista ($score%) | ⏱ ${sec}s] -> CALL")
                                    }
                                }
                                else -> Pair(null, "⏳ Auto [Tendencia Alcista]: Esperando retroceso pullback o envolvente a favor")
                            }
                        }

                        MarketRegime.STRONG_TREND_DOWN -> {
                            // En Tendencia Bajista: Únicamente entradas a favor de tendencia (Pullback Sniper o Vela Envolvente)
                            when {
                                !analysis.hasStrongMomentumUp && isSniperPullbackPutTrigger ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Sniper Pullback :0${sec}s | Tendencia Bajista] -> PUT")
                                !analysis.hasStrongMomentumUp && analysis.isEngulfingPut && (analysis.isPullbackSniperPut || analysis.isSniperTimingWindow) ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Vela Envolvente Resistencia | Tendencia Bajista | ⏱ ${sec}s] -> PUT")
                                !analysis.hasStrongMomentumUp && (analysis.confluenceScorePut >= 85 || analysis.signalPowerPut >= 85) -> {
                                    if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                        Pair(null, "⚠️ Veto: Tendencia bajista en piso de canal sin retroceso")
                                    } else {
                                        val score = Math.max(analysis.confluenceScorePut, analysis.signalPowerPut)
                                        Pair(TradeAction.SELL, "🎯 Auto [Confluencia Fuerte Bajista ($score%) | ⏱ ${sec}s] -> PUT")
                                    }
                                }
                                else -> Pair(null, "⏳ Auto [Tendencia Bajista]: Esperando retroceso pullback o envolvente a favor")
                            }
                        }

                        MarketRegime.BREAKOUT_RETEST -> {
                            // En Rompimiento y Retest: Choque con nivel roto o Rompimiento Validado
                            when {
                                !inDowntrend && !analysis.hasStrongMomentumDown && (analysis.isChoqueCall || analysis.isChoquePullbackCall) ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Choque / Pullback Alcista | Rompimiento/Retest | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && (analysis.isChoquePut || analysis.isChoquePullbackPut) ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Choque / Pullback Bajista | Rompimiento/Retest | ⏱ ${sec}s] -> PUT")
                                !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isValidBreakoutCall && !analysis.isDojiOrLowVolume ->
                                    Pair(TradeAction.BUY, "🎯 Auto [Rompimiento Válido Resistencia | ⏱ ${sec}s] -> CALL")
                                !inUptrend && !analysis.hasStrongMomentumUp && analysis.isValidBreakoutPut && !analysis.isDojiOrLowVolume ->
                                    Pair(TradeAction.SELL, "🎯 Auto [Rompimiento Válido Soporte | ⏱ ${sec}s] -> PUT")
                                else -> Pair(null, "⏳ Auto [Rompimiento/Retest]: Esperando confirmación de choque o ruptura limpia")
                            }
                        }
                    }
                }
                AutoTradeStrategy.MT_MASTER_COMBO -> {
                    // Jerarquía Master Traders con filtro macro de confluencia
                    val inDowntrend = analysis.trend == TrendDirection.DOWNTREND
                    val inUptrend = analysis.trend == TrendDirection.UPTREND

                    when {
                        // 1. Falso Rompimiento / Trampa en S/R
                        !inDowntrend && analysis.isFalseBreakoutCall -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Trampa / Falso Rompimiento de Soporte -> CALL")
                        }
                        !inUptrend && analysis.isFalseBreakoutPut -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Trampa / Falso Rompimiento de Resistencia -> PUT")
                        }
                        // 1b. Reversión Contra-Tendencia Cuantitativa por Sobreextensión en Zonas Clave
                        !inUptrend && (analysis.isNearResistanceZone || analysis.touchesResistance || analysis.distanceToResistanceRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToResistanceRatio <= 0.20f)) &&
                        (syntheticEngine?.isBullishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.GREEN) || analysis.isPriceNearTop) &&
                        (!analysis.hasStrongMomentumUp && !analysis.isValidBreakoutCall && (analysis.tickVelocityNormalized <= 0.05f || analysis.isBearishImpulse || analysis.hasTopRejectionWick || analysis.isRejectionPut || (syntheticEngine != null && syntheticEngine.consecutiveDownTicks >= 2))) -> {
                            val rsiStr = if (syntheticEngine != null) " (RSI ${syntheticEngine.syntheticTickRsi.toInt()})" else ""
                            Pair(TradeAction.SELL, "🎯 MT Combo: Reversión por Sobreextensión en Resistencia$rsiStr -> PUT")
                        }
                        !inDowntrend && (analysis.isNearSupportZone || analysis.touchesSupport || analysis.distanceToSupportRatio <= 0.20f || (syntheticEngine != null && syntheticEngine.distanceToSupportRatio <= 0.20f)) &&
                        (syntheticEngine?.isBearishOverextended == true || (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.25f) || (analysis.consecutiveCount >= 4 && analysis.lastCandles.firstOrNull() == CandleType.RED) || analysis.isPriceNearBottom) &&
                        (!analysis.hasStrongMomentumDown && !analysis.isValidBreakoutPut && (analysis.tickVelocityNormalized >= -0.05f || analysis.isBullishImpulse || analysis.hasBottomRejectionWick || analysis.isRejectionCall || (syntheticEngine != null && syntheticEngine.consecutiveUpTicks >= 2))) -> {
                            val rsiStr = if (syntheticEngine != null) " (RSI ${syntheticEngine.syntheticTickRsi.toInt()})" else ""
                            Pair(TradeAction.BUY, "🎯 MT Combo: Reversión por Sobreextensión en Soporte$rsiStr -> CALL")
                        }
                        // 2. Mechas de Rechazo en S/R (Filtradas por tendencia)
                        !inDowntrend && (analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick)) -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Mecha de Rechazo en Soporte -> CALL")
                        }
                        !inUptrend && (analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick)) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Mecha de Rechazo en Resistencia -> PUT")
                        }
                        // 3. Patrón Envolvente en S/R
                        !inDowntrend && analysis.isEngulfingCall -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Vela Envolvente en Soporte -> CALL")
                        }
                        !inUptrend && analysis.isEngulfingPut -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Vela Envolvente en Resistencia -> PUT")
                        }
                        // 4. Choque / Retest (Breakout + Retest)
                        !inDowntrend && (analysis.isChoqueCall || analysis.isChoquePullbackCall) -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Choque / Pullback tras Rompimiento -> CALL")
                        }
                        !inUptrend && (analysis.isChoquePut || analysis.isChoquePullbackPut) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Choque / Pullback tras Rompimiento -> PUT")
                        }
                        // 5. Agotamiento de 3 Velas
                        !inDowntrend && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall) -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Agotamiento 3 Velas Rojas -> CALL")
                        }
                        !inUptrend && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Agotamiento 3 Velas Verdes -> PUT")
                        }
                        // Sniper Pullback Entry (:01s-:05s) tras vela de señal fuerte
                        !inDowntrend && !analysis.hasStrongMomentumDown && isSniperPullbackCallTrigger -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Sniper Pullback (:0${sec}s) tras vela fuerte -> CALL")
                        }
                        !inUptrend && !analysis.hasStrongMomentumUp && isSniperPullbackPutTrigger -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Sniper Pullback (:0${sec}s) tras vela fuerte -> PUT")
                        }
                        // Rompimiento Válido S/R (>50% cuerpo fuera, mecha opuesta <20%)
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isValidBreakoutCall && !analysis.isDojiOrLowVolume -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Rompimiento Válido de Resistencia -> CALL")
                        }
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.isValidBreakoutPut && !analysis.isDojiOrLowVolume -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Rompimiento Válido de Soporte -> PUT")
                        }
                        // Soporte / Resistencia Clásico
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.touchesSupport && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || analysis.isPullbackSniperCall) -> {
                            Pair(TradeAction.BUY, "🎯 MT Combo: Rebote Confirmado en Soporte -> CALL")
                        }
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.touchesResistance && (analysis.lastCandles.firstOrNull() == CandleType.RED || analysis.isPullbackSniperPut) -> {
                            Pair(TradeAction.SELL, "🎯 MT Combo: Rebote Confirmado en Resistencia -> PUT")
                        }
                        // 6. Termómetro de Señal / Tendencia Alta Probabilidad >= 80%
                        !inDowntrend && !analysis.hasStrongMomentumDown && analysis.isSniperTimingWindow && (analysis.signalPowerCall >= 80 || (analysis.isCallSignal && analysis.signalScore >= 80)) -> {
                            if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                val score = if (analysis.signalPowerCall >= 80) analysis.signalPowerCall else analysis.signalScore
                                Pair(TradeAction.BUY, "🎯 MT Combo: Tendencia Alta ($score%) -> CALL")
                            }
                        }
                        !inUptrend && !analysis.hasStrongMomentumUp && analysis.isSniperTimingWindow && (analysis.signalPowerPut >= 80 || (analysis.isPutSignal && analysis.signalScore >= 80)) -> {
                            if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                val score = if (analysis.signalPowerPut >= 80) analysis.signalPowerPut else analysis.signalScore
                                Pair(TradeAction.SELL, "🎯 MT Combo: Tendencia Baja ($score%) -> PUT")
                            }
                        }
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.MT_FALSE_BREAKOUT -> {
                    when {
                        analysis.isFalseBreakoutCall -> Pair(TradeAction.BUY, "🎯 MT Falso Rompimiento: Trampa en Soporte -> CALL")
                        analysis.isFalseBreakoutPut -> Pair(TradeAction.SELL, "🎯 MT Falso Rompimiento: Trampa en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.MT_ENGULFING_SR -> {
                    when {
                        analysis.isEngulfingCall -> Pair(TradeAction.BUY, "🎯 MT Envolvente: Vela Verde en Soporte -> CALL")
                        analysis.isEngulfingPut -> Pair(TradeAction.SELL, "🎯 MT Envolvente: Vela Roja en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.MT_REJECTION -> {
                    when {
                        analysis.isRejectionCall || (analysis.touchesSupport && analysis.hasBottomRejectionWick) ->
                            Pair(TradeAction.BUY, "🎯 MT Mecha Rechazo: Absorción en Soporte -> CALL")
                        analysis.isRejectionPut || (analysis.touchesResistance && analysis.hasTopRejectionWick) ->
                            Pair(TradeAction.SELL, "🎯 MT Mecha Rechazo: Absorción en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                    when {
                        isSniperPullbackCallTrigger ->
                            Pair(TradeAction.BUY, "🎯 MT Choque Pullback: Sniper Pullback (:0${sec}s) -> CALL")
                        isSniperPullbackPutTrigger ->
                            Pair(TradeAction.SELL, "🎯 MT Choque Pullback: Sniper Pullback (:0${sec}s) -> PUT")
                        analysis.isChoqueCall || analysis.isChoquePullbackCall ->
                            Pair(TradeAction.BUY, "🎯 MT Choque Pullback: Retest en Soporte -> CALL")
                        analysis.isChoquePut || analysis.isChoquePullbackPut ->
                            Pair(TradeAction.SELL, "🎯 MT Choque Pullback: Retest en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                    when {
                        analysis.is3VelasCall || analysis.isExhaustion3CandlesCall ->
                            Pair(TradeAction.BUY, "🎯 MT 3 Velas Agotamiento: Reversión Alcista -> CALL")
                        analysis.is3VelasPut || analysis.isExhaustion3CandlesPut ->
                            Pair(TradeAction.SELL, "🎯 MT 3 Velas Agotamiento: Reversión Bajista -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.COLOR_TREND -> {
                    when {
                        analysis.isDojiOrLowVolume ->
                            Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
                        analysis.trend == TrendDirection.UPTREND && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || isSniperPullbackCallTrigger) -> {
                            if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                Pair(TradeAction.BUY, "🎯 Color Trend: Continuación Alcista -> CALL")
                            }
                        }
                        analysis.trend == TrendDirection.DOWNTREND && (analysis.lastCandles.firstOrNull() == CandleType.RED || isSniperPullbackPutTrigger) -> {
                            if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                Pair(TradeAction.SELL, "🎯 Color Trend: Continuación Bajista -> PUT")
                            }
                        }
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.STRIKE_BREAKOUT -> {
                    when {
                        analysis.isValidBreakoutCall ->
                            Pair(TradeAction.BUY, "🎯 Strike Breakout: Rompimiento Válido de Resistencia (>50% cuerpo, mecha <20%) -> CALL")
                        analysis.isValidBreakoutPut ->
                            Pair(TradeAction.SELL, "🎯 Strike Breakout: Rompimiento Válido de Soporte (>50% cuerpo, mecha <20%) -> PUT")
                        analysis.isFakeoutRiskCall || analysis.isFakeoutRiskPut || analysis.isFakeoutRisk ->
                            Pair(null, "⚠️ Veto: Riesgo de Falso Rompimiento / Fakeout (Cuerpo <=50% fuera de S/R o mecha opuesta >=20%)")
                        analysis.touchesSupport || analysis.isChoqueCall || analysis.isChoquePullbackCall ->
                            Pair(TradeAction.BUY, "🎯 Strike Breakout: Rebote/Ruptura de Soporte -> CALL")
                        analysis.touchesResistance || analysis.isChoquePut || analysis.isChoquePullbackPut ->
                            Pair(TradeAction.SELL, "🎯 Strike Breakout: Rebote/Ruptura de Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.AI_REMOTE -> {
                    Pair(null, "🧠 IA Remota: Esperando análisis multimodal...")
                }
                AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                    when {
                        analysis.touchesSupport -> Pair(TradeAction.BUY, "🎯 S/R: Toque en Soporte -> CALL")
                        analysis.touchesResistance -> Pair(TradeAction.SELL, "🎯 S/R: Toque en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.CANDLE_PATTERNS -> {
                    when {
                        analysis.consecutiveCount >= 3 && analysis.lastCandles.firstOrNull() == CandleType.RED ->
                            Pair(TradeAction.BUY, "🎯 Patrón Velas: Reversión 3 Rojas -> CALL")
                        analysis.consecutiveCount >= 3 && analysis.lastCandles.firstOrNull() == CandleType.GREEN ->
                            Pair(TradeAction.SELL, "🎯 Patrón Velas: Reversión 3 Verdes -> PUT")
                        analysis.isHammer ->
                            Pair(TradeAction.BUY, "🎯 Patrón Velas: Martillo Detectado -> CALL")
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.TREND_FOLLOWING -> {
                    when {
                        analysis.isDojiOrLowVolume ->
                            Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
                        analysis.trend == TrendDirection.UPTREND && (analysis.lastCandles.firstOrNull() == CandleType.GREEN || isSniperPullbackCallTrigger) -> {
                            if (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.isPriceNearTop) {
                                Pair(null, "⚠️ Veto: Continuación alcista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                Pair(TradeAction.BUY, "🎯 Tendencia: Continuación Alcista -> CALL")
                            }
                        }
                        analysis.trend == TrendDirection.DOWNTREND && (analysis.lastCandles.firstOrNull() == CandleType.RED || isSniperPullbackPutTrigger) -> {
                            if (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.isPriceNearBottom) {
                                Pair(null, "⚠️ Veto: Continuación bajista sin retroceso (Vela en extremo opuesto del rango)")
                            } else {
                                Pair(TradeAction.SELL, "🎯 Tendencia: Continuación Bajista -> PUT")
                            }
                        }
                        else -> Pair(null, "")
                    }
                }
                AutoTradeStrategy.COMBINED -> {
                    when {
                        analysis.touchesSupport && analysis.lastCandles.firstOrNull() != CandleType.RED ->
                            Pair(TradeAction.BUY, "🎯 Combinada: Confirmación en Soporte -> CALL")
                        analysis.touchesResistance && analysis.lastCandles.firstOrNull() != CandleType.GREEN ->
                            Pair(TradeAction.SELL, "🎯 Combinada: Confirmación en Resistencia -> PUT")
                        else -> Pair(null, "")
                    }
                }
            }

            val (action, reason) = rawResult
            if (action == null) return rawResult

            // 1. Filtro de Proximidad S/R Anti-Suicidio Universal:
            // Prohibido vender sobre Soporte (salvo rompimiento válido bajista o rechazo en resistencia)
            if (action == TradeAction.SELL && !analysis.isValidBreakoutPut &&
                !analysis.isRejectionPut && !(analysis.touchesResistance && analysis.hasTopRejectionWick) &&
                (analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.15f || analysis.touchesSupport)) {
                return Pair(null, "⚠️ Veto: Prohibido vender sobre Soporte (Riesgo de Rebote)")
            }
            // Prohibido comprar sobre Resistencia (salvo rompimiento válido alcista o rechazo en soporte)
            if (action == TradeAction.BUY && !analysis.isValidBreakoutCall &&
                !analysis.isRejectionCall && !(analysis.touchesSupport && analysis.hasBottomRejectionWick) &&
                (analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.15f || analysis.touchesResistance)) {
                return Pair(null, "⚠️ Veto: Prohibido comprar sobre Resistencia (Riesgo de Rechazo)")
            }

            // 2. Exigir Retroceso (Pullback) en Continuación y Bloquear Sobreextensión / Trampas en S/R para estrategias de tendencia
            val isTrendContinuation = strategy == AutoTradeStrategy.TREND_FOLLOWING ||
                strategy == AutoTradeStrategy.COLOR_TREND ||
                reason.contains("Continuación") ||
                reason.contains("Tendencia") ||
                reason.contains("Impulso") ||
                reason.contains("Confluencia Fuerte")

            if (isTrendContinuation) {

                // Filtro Anti-Continuación por Agotamiento de 3 Velas (c3 < c2 < c1 en S/R):
                if (action == TradeAction.SELL && (analysis.is3VelasCall || analysis.isExhaustion3CandlesCall)) {
                    return Pair(null, "⛔ Veto: Agotamiento 3 Velas Rojas en Soporte detectado. Prohibido vender en suelo")
                }
                if (action == TradeAction.BUY && (analysis.is3VelasPut || analysis.isExhaustion3CandlesPut)) {
                    return Pair(null, "⛔ Veto: Agotamiento 3 Velas Verdes en Resistencia detectado. Prohibido comprar en techo")
                }

                // Filtro Anti-Continuación por Mecha de Rechazo (>=45% en S/R):
                if (action == TradeAction.SELL && (analysis.isRejectionCall || analysis.hasBottomRejectionWick)) {
                    return Pair(null, "⛔ Veto: Mecha de Rechazo Inferior (≥45%) en Soporte. Prohibido vender en suelo")
                }
                if (action == TradeAction.BUY && (analysis.isRejectionPut || analysis.hasTopRejectionWick)) {
                    return Pair(null, "⛔ Veto: Mecha de Rechazo Superior (≥45%) en Resistencia. Prohibido comprar en techo")
                }

                if (analysis.isDojiOrLowVolume) {
                    return Pair(null, "⚠️ Veto: Continuación descalificada por Doji / Micro-rango (Cuerpo < 15px)")
                }
            }

            // 2. Filtro Anti-Sobreextensión de Racha y Ticks:
            val isGreenStreak = analysis.consecutiveCount >= 4 &&
                (analysis.lastCandles.firstOrNull() == CandleType.GREEN || analysis.streakBadge.contains("V"))
            val isRedStreak = analysis.consecutiveCount >= 4 &&
                (analysis.lastCandles.firstOrNull() == CandleType.RED || analysis.streakBadge.contains("R"))

            val isWsOverbought = syntheticEngine?.isBullishOverextended == true ||
                (syntheticEngine != null && syntheticEngine.syntheticTickRsi >= 78.0 && syntheticEngine.distanceToResistanceRatio <= 0.20f)
            val isWsOversold = syntheticEngine?.isBearishOverextended == true ||
                (syntheticEngine != null && syntheticEngine.syntheticTickRsi <= 22.0 && syntheticEngine.distanceToSupportRatio <= 0.20f)

            if (isTrendContinuation) {
                if (action == TradeAction.BUY && isGreenStreak) {
                    return Pair(null, "⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso")
                }
                if (action == TradeAction.SELL && isRedStreak) {
                    return Pair(null, "⚠️ Veto: Racha sobreextendida (>=4 velas). Esperando retroceso")
                }
                if (action == TradeAction.BUY && (isWsOverbought || analysis.isNearResistanceZone || analysis.distanceToResistanceRatio < 0.12f || analysis.isPriceNearTop)) {
                    val detail = if (syntheticEngine != null && isWsOverbought) " [RSI Ticks: ${syntheticEngine.syntheticTickRsi.toInt()}, Ticks Up: ${syntheticEngine.consecutiveUpTicks}]" else ""
                    return Pair(null, "⚠️ Veto: Continuación alcista sin retroceso$detail (Vela en extremo opuesto del rango)")
                }
                if (action == TradeAction.SELL && (isWsOversold || analysis.isNearSupportZone || analysis.distanceToSupportRatio < 0.12f || analysis.isPriceNearBottom)) {
                    val detail = if (syntheticEngine != null && isWsOversold) " [RSI Ticks: ${syntheticEngine.syntheticTickRsi.toInt()}, Ticks Down: ${syntheticEngine.consecutiveDownTicks}]" else ""
                    return Pair(null, "⚠️ Veto: Continuación bajista sin retroceso$detail (Vela en extremo opuesto del rango)")
                }
            }

            return rawResult
        }
    }

    /**
     * Construye la instantánea inmutable que pinta el HUD (Ronda 11, T4).
     *
     * Fuente única: feed WebSocket + motor sintético + RiskManager. NUNCA `latestAnalysisResult`,
     * que viene de la ruta de visión retirada y en headless queda con los valores por defecto
     * (termómetro 50/50, racha vacía) disfrazados de medición real.
     *
     * Devuelve `null` en los campos de medida cuando no hay dato: el HUD pinta "—", no un valor
     * por defecto. `hasFreshTick` es la puerta: sin tick fresco no hay termómetro, ni racha, ni
     * velocidad viva.
     */
    fun buildHudSnapshot(ws: BinomoWebSocketClient?): HudSnapshot {
        val tick = latestMarketTick
        val assetLabel = ws?.activeAsset ?: "Z-CRY/IDX"
        val friendly = if (assetLabel.contains("CRY")) "Crypto IDX" else assetLabel

        val connected = ws?.isConnected ?: false
        val tickAgeMs = ws?.lastTickAgeMs ?: Long.MAX_VALUE
        val hasFreshTick = connected && tick != null && tickAgeMs < BinomoWebSocketClient.DEFAULT_FRESHNESS_MS

        // Solo se derivan probabilidades/racha con tick fresco: un valor cacheado de hace minutos
        // no es una medición, es un recuerdo.
        val freshAnalysis = if (hasFreshTick && tick != null) buildAnalysisFromSyntheticEngine(tick) else null
        val velocityLive = hasFreshTick && tickAgeMs < VELOCITY_LIVE_WINDOW_MS

        val pendingAction = if (riskManager.hasPendingTrade) riskManager.pendingTradeAction else null
        val entryPrice = riskManager.pendingTradeEntryPrice
        val moneyness = if (pendingAction != null && entryPrice > 0.0 && hasFreshTick && tick != null) {
            StrikeCalculator.moneyness(pendingAction, entryPrice, tick.price)
        } else null

        return HudSnapshot(
            assetLabel = assetLabel,
            friendlyAsset = friendly,
            hasFreshTick = hasFreshTick,
            price = tick?.price ?: 0.0,
            tickAgeMs = if (tickAgeMs == Long.MAX_VALUE) -1L else tickAgeMs,
            wsState = ws?.currentState ?: WebSocketState.DISCONNECTED,
            tickSource = ws?.lastTickSource ?: "none",
            rejectedForeignAssetTicks = ws?.rejectedForeignAssetTicks ?: 0L,
            rejectedSanityJumpTicks = ws?.rejectedSanityJumpTicks ?: 0L,
            trend = syntheticCandleEngine.detectedTrend,
            streakBadge = freshAnalysis?.streakBadge?.takeIf { it.isNotBlank() },
            callPct = freshAnalysis?.signalPowerCall,
            putPct = freshAnalysis?.signalPowerPut,
            velocity = tick?.velocity ?: 0f,
            smoothedVelocity = tick?.smoothedVelocity ?: 0f,
            velocityIsLive = velocityLive,
            patternName = "",
            probabilityPct = -1,
            srFilterStatus = "",
            actionPlan = "",
            totalWins = riskManager.totalWins,
            totalLosses = riskManager.totalLosses,
            totalVoids = riskManager.totalVoids,
            winRate = riskManager.getWinRate().toDouble(),
            martingaleBadge = riskManager.getMartingaleStatusBadge(autonomousSubMode),
            cooldownRemainingSec = riskManager.getRemainingCooldown(autonomousSubMode).toLong(),
            hasPendingTrade = riskManager.hasPendingTrade,
            orderState = riskManager.orderState,
            pendingAction = pendingAction,
            pendingElapsedSec = if (riskManager.hasPendingTrade)
                (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000 else 0L,
            pendingEntryPrice = entryPrice,
            pendingMoneyness = moneyness
        )
    }

    /** Ventana en la que una micro-velocidad sigue considerándose actual (alineada con el feed). */
    private val VELOCITY_LIVE_WINDOW_MS = 3200L

    fun getEngineReasoning(): EngineReasoning {
        val tick = latestMarketTick
        // Ronda 11 (T4): el razonamiento se deriva del tick ACTUAL, no de `latestAnalysisResult`
        // (ruta de visión retirada que en headless queda con los valores por defecto). Si no hay
        // tick todavía, se cae al último análisis disponible para no dejar el panel vacío.
        val analysis = if (tick != null) buildAnalysisFromSyntheticEngine(tick) else latestAnalysisResult
        val wsTrend = syntheticCandleEngine.detectedTrend
        val sup = syntheticCandleEngine.dynamicSupportPrice
        val res = syntheticCandleEngine.dynamicResistancePrice

        // Ronda 3: en Headless (WebSocket puro) la ÚNICA fuente de verdad es el motor sintético.
        // `latestAnalysisResult` proviene de la ruta de visión (retirada) y puede quedar STALE,
        // mostrando una tendencia/patrón que ya no coincide con el feed WS ni con Binomo.
        // Se deriva la tendencia SIEMPRE del motor sintético para que el HUD sea coherente.

        // 1. Régimen de Mercado y Patrón Detectado
        val regimeResult = MarketRegimeClassifier.classify(analysis, syntheticCandleEngine)
        val basePattern = when {
            wsTrend == TrendDirection.UPTREND -> "📈 Flujo Continuo Alcista (Higher Highs)"
            wsTrend == TrendDirection.DOWNTREND -> "📉 Flujo Continuo Bajista (Lower Lows)"
            syntheticCandleEngine.cachedChoppiness || wsTrend == TrendDirection.SIDEWAYS -> "📊 Rango Lateral Cuantitativo / Esperando Expansión"
            else -> "🔍 Monitoreando Acción del Precio"
        }
        val pattern = "${regimeResult.regime.hudBadge} | $basePattern"

        // 2. Probabilidad Estimada (derivada del motor WS, coherente con el termómetro)
        val prob = when (wsTrend) {
            TrendDirection.UPTREND -> 86
            TrendDirection.DOWNTREND -> 86
            else -> {
                val distS = syntheticCandleEngine.distanceToSupportRatio
                val distR = syntheticCandleEngine.distanceToResistanceRatio
                when {
                    distS <= 0.20f -> 62
                    distR <= 0.20f -> 62
                    else -> 50
                }
            }
        }

        // 3. Estado del Filtro S/R
        // Ronda 11 (T7): fuente ÚNICA y normalización ÚNICA. Antes este bloque calculaba la
        // distancia como posición dentro del canal `(res - sup)`, mientras el plan de acción, los
        // vetos y `distanceToSupportRatio` la calculaban dividiendo por el ATR. Dos métricas
        // distintas con el mismo nombre: con ATR pequeño el HUD podía anunciar a la vez
        // "Toque Directo en Soporte [CALL Óptimo]" (5% del canal) y "Cerca de Resistencia (7%):
        // Buscando Mecha Rechazo PUT" (distancia/ATR). Contradicción observada en dispositivo.
        // Ahora se usan los ratios del motor sintético, los MISMOS que deciden el trading.
        val distSRatio = syntheticCandleEngine.distanceToSupportRatio
        val distRRatio = syntheticCandleEngine.distanceToResistanceRatio
        val trendNow = wsTrend
        val srStatus = if (sup > 0.0 && res > 0.0 && tick != null) {
            when {
                distSRatio <= 0.15f -> "🟢 Toque Directo en Soporte [CALL Óptimo]"
                distRRatio <= 0.15f -> "🔴 Toque Directo en Resistencia [PUT Óptimo]"
                distSRatio <= 0.30f -> "🟢 Cerca de Soporte (${(distSRatio * 100).toInt()}%)"
                distRRatio <= 0.30f -> "🔴 Cerca de Resistencia (${(distRRatio * 100).toInt()}%)"
                else -> "⚪ Centro del Canal S/R (Neutral)"
            }
        } else {
            "—"
        }

        // 4. Plan de Acción Táctico
        val plan = when {
            riskManager.hasPendingTrade -> {
                // Ronda 11 (T6): cuenta atrás REAL de expiración, sustituyendo el literal
                // "Liquidando en vela :00s" que se mostraba fijo durante toda la operación y no
                // correspondía a ningún instante concreto.
                val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
                val remaining = (timeframe.seconds - elapsed).coerceAtLeast(0L)
                val act = if (riskManager.pendingTradeAction == TradeAction.BUY) "CALL ▲" else "PUT ▼"
                val entry = riskManager.pendingTradeEntryPrice
                val entryStr = if (entry > 0.0) StrikeCalculator.formatPrice(entry) else "—"
                "⏳ Trade Activo: $act · Strike $entryStr · Hasta exp: ${remaining}s"
            }
            (syntheticCandleEngine.cachedChoppiness && autonomousSubMode != AutonomousSubMode.YOLO) -> {
                val distS = syntheticCandleEngine.distanceToSupportRatio
                val distR = syntheticCandleEngine.distanceToResistanceRatio
                if (distS <= 0.25f) "🛡️ Anti-Chop Activo · Cerca de Soporte (${(distS * 100).toInt()}%): Buscando Mecha Rechazo CALL"
                else if (distR <= 0.25f) "🛡️ Anti-Chop Activo · Cerca de Resistencia (${(distR * 100).toInt()}%): Buscando Mecha Rechazo PUT"
                else "🛡️ Filtro Anti-Chop Activo: Consolidación/CHOP elevado. Esperando extremos S/R o ruptura."
            }
            trendNow == TrendDirection.DOWNTREND && syntheticCandleEngine.distanceToSupportRatio <= 0.15f ->
                "⛔ Venta vetada sobre soporte: esperando rebote o ruptura confirmada."
            trendNow == TrendDirection.UPTREND && syntheticCandleEngine.distanceToResistanceRatio <= 0.15f ->
                "⛔ Compra vetada bajo resistencia: esperando rechazo o ruptura confirmada."
            trendNow == TrendDirection.UPTREND -> {
                "🎯 Plan: Buscar retroceso leve a soporte para ejecutar CALL en la ventana :01s - :05s."
            }
            trendNow == TrendDirection.DOWNTREND -> {
                "🎯 Plan: Buscar retroceso leve a resistencia para ejecutar PUT en la ventana :01s - :05s."
            }
            else -> {
                val distS = syntheticCandleEngine.distanceToSupportRatio
                val distR = syntheticCandleEngine.distanceToResistanceRatio
                when {
                    distS <= 0.30f -> "🎯 Plan: Cerca de Soporte (${(distS * 100).toInt()}%). Esperando gatillo CALL en la ventana :01s - :05s."
                    distR <= 0.30f -> "🎯 Plan: Cerca de Resistencia (${(distR * 100).toInt()}%). Esperando gatillo PUT en la ventana :01s - :05s."
                    else -> "🎯 Plan: Esperando confirmación de nivel S/R o impulso direccional de ticks."
                }
            }
        }

        val isFavorable = !riskManager.hasPendingTrade && !syntheticCandleEngine.cachedChoppiness && analysis?.isMarketSideways != true
        return EngineReasoning(pattern, prob, srStatus, plan, isFavorable)
    }

    fun getStrategyStatusHint(): String {
        val isAccessOk = AutoTradeAccessibilityService.instance != null || AutoTradeAccessibilityService.isAccessibilityPermissionGranted(context)
        if (!isAccessOk && mode == AutoTradeMode.AUTONOMOUS) {
            return "⚠️ Accesibilidad DESACTIVADA (Clics bloqueados en Android)"
        }

        if (sniperShutdownReason != null) {
            return "🛑 $sniperShutdownReason · Toca [MODO] para nueva sesión"
        }

        val (canTradeStatus, blockReason) = riskManager.canExecuteTrade(autonomousSubMode)
        if (!canTradeStatus && !riskManager.hasPendingTrade && mode == AutoTradeMode.AUTONOMOUS) {
            val requiresManualResume = blockReason.contains("Stop Loss", ignoreCase = true) || blockReason.contains("Take Profit", ignoreCase = true) || blockReason.contains("Límite", ignoreCase = true) || blockReason.contains("Trailing", ignoreCase = true) || blockReason.contains("VOID", ignoreCase = true)
            return if (requiresManualResume) "🛑 $blockReason · Toca [MODO] para reanudar" else "⏳ $blockReason"
        }

        if (riskManager.hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
            val actionName = if (riskManager.pendingTradeAction == TradeAction.BUY) "COMPRA" else "VENTA"
            return "⏳ Operación en curso ($actionName ${elapsed}s) · Esperando resultado..."
        }

        if (aiClient.isEnabled) {
            val ai = latestAIResult
            val modelShort = aiClient.model.substringAfterLast('/')
            return if (ai != null && ai.isSuccess) {
                val pct = (ai.confidence * 100).toInt()
                val act = ai.action?.name ?: "WAIT"
                "🧠 IA [$modelShort]: $act ($pct%) · ${ai.reason.take(28)}"
            } else if (ai != null && !ai.isSuccess) {
                "⚠️ IA: ${ai.errorMessage ?: "Esperando conexión..."}"
            } else {
                "🧠 IA [$modelShort]: Analizando pantalla..."
            }
        }

        val analysis = latestAnalysisResult

        if (analysis != null && analysis.isMarketSideways) {
            return "⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad"
        }

        return when (strategy) {
            AutoTradeStrategy.AUTO_ADAPTIVE -> {
                "🤖 Auto Adaptativo: Vigilando todas las estrategias en tiempo real..."
            }
            AutoTradeStrategy.SUPPORT_RESISTANCE -> {
                "🔍 Vigilando rebote en Soporte / Resistencia en vivo..."
            }
            AutoTradeStrategy.MT_REJECTION -> {
                if (analysis != null && (analysis.hasTopRejectionWick || analysis.hasBottomRejectionWick || analysis.isRejectionCall || analysis.isRejectionPut)) {
                    "⚡ Mecha de rechazo detectada! Evaluando entrada..."
                } else {
                    "🔍 MT: Buscando mechas de rechazo en niveles..."
                }
            }
            AutoTradeStrategy.MT_CHOQUE_PULLBACK -> {
                if (analysis != null && (analysis.isChoqueCall || analysis.isChoquePut || analysis.isChoquePullbackCall || analysis.isChoquePullbackPut)) {
                    "⚡ Choque con nivel roto detectado! Preparando trade..."
                } else {
                    "🔍 MT: Buscando choque de máximos/mínimos..."
                }
            }
            AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> {
                if (analysis != null && (analysis.is3VelasCall || analysis.is3VelasPut || analysis.isExhaustion3CandlesCall || analysis.isExhaustion3CandlesPut || analysis.consecutiveCount >= 2)) {
                    "📊 MT: Racha ${analysis.consecutiveCount} velas. Vigilando agotamiento..."
                } else {
                    "🔍 MT: Esperando patrón de 3 velas..."
                }
            }
            AutoTradeStrategy.MT_ENGULFING_SR -> {
                if (analysis != null && (analysis.isEngulfingCall || analysis.isEngulfingPut)) {
                    "⚡ Vela envolvente en nivel clave! Evaluando entrada..."
                } else {
                    "🔍 MT: Buscando patrón envolvente en S/R..."
                }
            }
            AutoTradeStrategy.MT_FALSE_BREAKOUT -> {
                if (analysis != null && (analysis.isFalseBreakoutCall || analysis.isFalseBreakoutPut)) {
                    "⚡ Trampa institucional / Fakeout detectado!"
                } else {
                    "🔍 MT: Vigilando falsos rompimientos de nivel..."
                }
            }
            AutoTradeStrategy.MT_MASTER_COMBO -> {
                "🔥 MT Master Combo: Vigilando Rechazo, Trampa, Envolvente y Choque..."
            }
            AutoTradeStrategy.COLOR_TREND -> {
                "🎨 Color Trend: Vigilando continuidad de color y tendencia..."
            }
            AutoTradeStrategy.STRIKE_BREAKOUT -> {
                "💥 Strike Breakout: Vigilando ruptura de strike..."
            }
            AutoTradeStrategy.AI_REMOTE -> {
                "🧠 IA Remota: Esperando análisis multimodal..."
            }
            AutoTradeStrategy.CANDLE_PATTERNS -> {
                if (analysis != null && analysis.consecutiveCount > 0) {
                    "📊 Racha: ${analysis.consecutiveCount} ${analysis.lastCandles.firstOrNull()?.name ?: ""} (Obj: 3)"
                } else {
                    "🔍 Contando velas en vivo..."
                }
            }
            AutoTradeStrategy.TREND_FOLLOWING -> {
                if (analysis != null) "📈 Tendencia: ${analysis.trend.name}" else "🔍 Detectando tendencia..."
            }
            AutoTradeStrategy.COMBINED -> {
                "🔍 Esperando confirmación doble..."
            }
        }
    }

    private fun handleSignal(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        bitmap: Bitmap,
        reasonDescription: String,
        confidence: Float? = null
    ) {
        val sec = analysis.candleSecond
        val isTimingVetoed = MarketTickFilters.isTimingVetoed(sec) || analysis.isTimingVetoed
        val isChoppy = analysis.isMicroRangeChoppy || syntheticCandleEngine.isChoppinessDetected()

        if (isChoppy) {
            android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
            return
        }

        if (isTimingVetoed && !analysis.isFalseBreakoutCall && !analysis.isFalseBreakoutPut) {
            android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Timing Estricto (:15-:55) (⏱ ${sec}s)")
            return
        }

        // Evaluación por el Motor de Autoaprendizaje Adaptativo de Errores
        val adaptiveDecision = adaptiveLearningEngine.evaluateSignalSuitability(
            candidateAction = action,
            analysis = analysis,
            tick = latestMarketTick,
            strategyName = strategy.name
        )

        val finalAction: TradeAction
        val finalReason: String
        val adaptiveModifier: Float

        when (adaptiveDecision) {
            is AdaptiveDecision.Block -> {
                android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por Autoaprendizaje: ${adaptiveDecision.reason}")
                handler.post {
                    Toast.makeText(context, "⛔ [Autoaprendizaje] Entrada bloqueada: ${adaptiveDecision.reason}", Toast.LENGTH_SHORT).show()
                }
                return
            }
            is AdaptiveDecision.Invert -> {
                finalAction = adaptiveDecision.invertedAction
                finalReason = "$reasonDescription [🔄 Invertida por Autoaprendizaje: ${adaptiveDecision.reason}]"
                adaptiveModifier = 1.15f
            }
            is AdaptiveDecision.Allow -> {
                finalAction = adaptiveDecision.action
                finalReason = reasonDescription
                adaptiveModifier = adaptiveDecision.confidenceModifier
            }
        }

        val baseConfidence = confidence ?: when {
            finalAction == TradeAction.BUY && analysis.signalPowerCall > 0 -> analysis.signalPowerCall / 100f
            finalAction == TradeAction.SELL && analysis.signalPowerPut > 0 -> analysis.signalPowerPut / 100f
            else -> 1.0f
        }
        val signalConfidence = (baseConfidence * adaptiveModifier).coerceIn(0.1f, 1.0f)
        val (canTrade, reason) = riskManager.canExecuteTrade(autonomousSubMode, signalConfidence)
        val actionText = if (finalAction == TradeAction.BUY) "COMPRA / CALL (Sube)" else "VENTA / PUT (Baja)"
        val emoji = if (finalAction == TradeAction.BUY) "🟢 ▲" else "🔴 ▼"

        currentActiveSignal = ActiveSignal(
            action = finalAction,
            title = "$emoji $actionText",
            reason = finalReason,
            timestamp = System.currentTimeMillis()
        )

        handler.post {
            onSignalListener?.invoke(finalAction, "$actionText · $finalReason")
            val now = System.currentTimeMillis()
            if (now - lastFeedbackEmitTime >= 3500L && canTrade) {
                lastFeedbackEmitTime = now
                emitHapticAndAudioFeedback()
            }
        }

        if (mode == AutoTradeMode.SEMIAUTOMATIC) {
            handler.post {
                autoDrawEngine.drawTradeEntry(
                    finalAction,
                    latestMarketTick?.price ?: 0.0,
                    context.resources.displayMetrics.widthPixels.toFloat(),
                    // R23A Iter 2: la etiqueta de stake sobre el grafico tambien debe decir lo que el
                    // broker debita, no el calculo interno. Mismo desacoplamiento que el journal.
                    riskManager.getEffectiveStake(autonomousSubMode),
                    riskManager.sorosPayoutRate.toDouble()
                )
                Toast.makeText(context, "🔔 SEÑAL: $actionText\n$finalReason", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (mode == AutoTradeMode.AUTONOMOUS) {
            if (!canTrade) {
                if (autonomousSubMode == AutonomousSubMode.SNIPER && (reason.contains("alcanzado", ignoreCase = true) || reason.contains("finalizada", ignoreCase = true) || reason.contains("Límite", ignoreCase = true))) {
                    setTradingMode(AutoTradeMode.DISABLED, "engine:sniper-limite(seguimiento)")
                    sniperShutdownReason = reason
                    handler.post {
                        Toast.makeText(context, "🛑 $reason", Toast.LENGTH_LONG).show()
                        OverlayService.instance?.updateHUDView()
                    }
                }
                return
            }

            // Registrar firma de entrada en el motor adaptativo
            adaptiveLearningEngine.recordTradeOpened(finalAction, analysis, latestMarketTick, strategy.name)
            executeAutonomousTrade(finalAction, analysis, bitmap, finalReason, signalConfidence)
        }
    }

    private fun executeAutonomousTrade(
        action: TradeAction,
        analysis: VisionAnalysisResult,
        bitmap: Bitmap,
        reasonDescription: String,
        confidence: Float = 0.0f
    ) {
        val calibration = calibrationManager
        val (screenW, screenH) = CalibrationManager.getRealScreenDimensions(context)
        val isLand = screenW > screenH

        // 1. Prioridad Máxima: Detección visual en tiempo real sobre el frame activo
        val visionCoords = visionAnalyzer.findBrokerButtonCoordinates(bitmap, action == TradeAction.BUY)

        // 2. Respaldo: Calibración guardada o fórmulas geométricas precisas
        val (rawX, rawY) = if (visionCoords != null) {
            visionCoords
        } else if (calibration != null && calibration.isCalibrated()) {
            if (action == TradeAction.BUY) calibration.getBuyCoordinates() else calibration.getSellCoordinates()
        } else {
            if (isLand) {
                if (action == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                // Centro exacto del área táctil de los botones en Binomo vertical (90.3% de la pantalla)
                if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.903f)
                else Pair(screenW * 0.75f, screenH * 0.903f)
            }
        }

        // Sanitización estricta: en modo vertical, nunca permitir clics por encima del 86.5% (fila de Hora / Cantidad)
        val (x, y) = if (!isLand && rawY < screenH * 0.865f) {
            android.util.Log.w("TradingEngine", "⚠️ Coordenada Y=$rawY cae sobre selector de Hora/Cantidad (<86.5%). Corrigiendo a botón real (${screenH * 0.903f}).")
            Pair(rawX, screenH * 0.903f)
        } else {
            Pair(rawX, rawY)
        }

        android.util.Log.d("TradingEngine", "executeAutonomousTrade: $action hacia ($x, $y) [Vision:${visionCoords != null}] Motivo: $reasonDescription")

        val accessibility = AutoTradeAccessibilityService.instance
        val observed = accessibility?.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
        val baseBal = if (observed > 0.0) observed else AutoTradeAccessibilityService.latestObservedBalance
        isTradeResolving.set(false)
        // Ronda 11 (T3): el strike se registra en dominio de PRECIO. `analysis.currentPriceY` era
        // una Y de visión (0 en headless): guardarla como entryPrice dejaba la liquidación sin
        // referencia real.
        riskManager.recordTradeSent(action, latestMarketTick?.price ?: 0.0, 0f, baseBal, confidence, analysis.candleSecond, reasonDescription)
        // Despacho táctil único con verificación y fallback ROOT transparente
        AutoTradeAccessibilityService.performClick(x, y) { success, error ->
            if (!success) {
                triggerManualFallback(action, error ?: "Auto-clic falló en 300ms")
            } else {
                // R14/A2: el tap ya termino: se completa la instrumentacion de latencia con sus
                // instantes REALES. Se hace en el callback (no antes) porque `executeRootTap`
                // corre en su propio hilo y hasta que no vuelve no se sabe cuando acabo.
                riskManager.captureTapLatency(
                    tapStartTs = AutoTradeAccessibilityService.lastTapStartTs,
                    tapEndTs = AutoTradeAccessibilityService.lastTapEndTs,
                    candleSecondAtTapEnd = AutoTradeAccessibilityService.lastTapCandleSecond,
                    priceAtTapEnd = latestMarketTick?.price ?: 0.0
                )
            }
        }

        handler.post {
            drawingView.triggerClickAnimation(x, y)
            // Ronda 11 (T3): la STRIKE_PRICE_LINE se sitúa por PRECIO de entrada (no por la Y del
            // tap). Antes se hacía `y.coerceIn(screenH*0.35, screenH*0.65)`: una coordenada de
            // pulsación sin relación con ningún precio, de ahí la línea fantasma a ~640 px.
            autoDrawEngine.drawTradeEntry(
                action = action,
                entryPrice = latestMarketTick?.price ?: 0.0,
                screenWidth = screenW,
                stake = riskManager.getEffectiveStake(autonomousSubMode),
                payoutRate = riskManager.sorosPayoutRate.toDouble()
            )
            saveAuditScreenshot(bitmap, action)
            val stake = riskManager.getEffectiveStake(autonomousSubMode)
            Toast.makeText(context, "🤖 BOT OPERÓ: $action ($$$stake)\n$reasonDescription", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerManualFallback(action: TradeAction, reason: String) {
        Log.w("TradingEngine", "🚨 Activando MODO HÍBRIDO Fallback: $reason. Alerta manual para $action")
        emitHapticWarning()
        OverlayService.instance?.showManualFallbackAlert(action, 2500L)
        handler.post {
            val dir = if (action == TradeAction.BUY) "CALL ▲ (SUBE)" else "PUT ▼ (BAJA)"
            Toast.makeText(context, "🚨 ¡PULSA MANUAL: $dir!\n$reason", Toast.LENGTH_SHORT).show()
        }
    }

    private fun emitHapticWarning() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(350)
                }
            }
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error en vibración háptica", e)
        }
    }

    /**
     * Prueba los clics de SUBE y BAJA para verificar la conexión de Accesibilidad.
     */
    fun testAccessibilityClicks() {
        val accessibility = AutoTradeAccessibilityService.instance
        if (accessibility == null) {
            Toast.makeText(context, "❌ Accesibilidad NO conectada.\nAbriendo Ajustes de Accesibilidad...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("TradingEngine", "Error abriendo ajustes de accesibilidad", e)
            }
            return
        }

        val calibration = calibrationManager
        val (buyX, buyY) = calibration?.getBuyCoordinates() ?: Pair(200f, 600f)
        val (sellX, sellY) = calibration?.getSellCoordinates() ?: Pair(600f, 600f)

        Toast.makeText(context, "👉 Probando clic en SUBE...", Toast.LENGTH_SHORT).show()
        accessibility.performClickAt(buyX, buyY)

        handler.postDelayed({
            Toast.makeText(context, "👉 Probando clic en BAJA...", Toast.LENGTH_SHORT).show()
            accessibility.performClickAt(sellX, sellY)
        }, 1200)
    }

    private fun emitHapticAndAudioFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (notificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error en feedback háptico/audio", e)
        }
    }

    private fun saveAuditScreenshot(bitmap: Bitmap, action: TradeAction) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.getExternalFilesDir(null), "TradeDraw_Audits")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "TRADE_${action.name}_$timeStamp.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            Log.d("TradingEngine", "Screenshot guardado en: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("TradingEngine", "Error guardando screenshot de auditoría", e)
        }
    }

    /**
     * Ejecuta una orden en modo 100% Headless (sin frames ni capturas de pantalla).
     * Utiliza las cotizaciones puras del WebSocket y pulsa las coordenadas calibradas con Accesibilidad.
     */
    fun executeHeadlessTrade(
        action: TradeAction,
        reasonDescription: String,
        decisionId: String = java.util.UUID.randomUUID().toString()
    ) {
        val currentDecisionId = decisionId
        Log.d("TradingEngine", "➡️ executeHeadlessTrade INICIO: $action | $reasonDescription | decisionId=$currentDecisionId")

        // R23A Iter 1: GUARD DE SEGURIDAD FAIL-CLOSED (cuenta DEMO exclusiva).
        //
        // El modo AUTONOMOUS es el unico que despacha ordenes sin confirmacion humana, asi que es el
        // unico que exige esta puerta. Cualquier otro modo (SHADOW, SEMIAUTOMATIC) sigue con su
        // comportamiento actual: SHADOW no despacha taps por diseño y no debe verse afectado.
        //
        // SOBRE EL BALANCE. La spec proponia exigir ademas `latestObservedBalance >= 500_000.0`. NO
        // se implementa como condicion de bloqueo, por dos razones concretas:
        //  1. Duplicaria la logica de `RiskManager.canExecuteTrade`, que YA resuelve exactamente esa
        //     pregunta (`currentBal >= 1e6 || (isDemoAccount && currentBal > 5e5)`) y es quien tiene
        //     el suelo de equity y el resto de la politica de riesgo. Dos fuentes de verdad para el
        //     mismo criterio divergen con el tiempo.
        //  2. El balance es un DATO DEL DISPOSITIVO que puede llegar a 0.0 transitoriamente (lectura
        //     aun no realizada, servicio de accesibilidad recien arrancado). Meterlo en un guard de
        //     seguridad que apaga el motor a DISABLED convertiria un fallo de LECTURA en un apagon.
        // La seguridad de "no operar en real" la da `isDemoAccount`, que es el dato del que depende
        // toda la distincion demo/real y que ya no puede mentir por diseño fail-closed.
        if (mode == AutoTradeMode.AUTONOMOUS && !puedeOperarAutonomoEnDemo(AutoTradeAccessibilityService.isDemoAccount)) {
            Log.e("TradingEngine", "⛔ BLOQUEO CRÍTICO DE SEGURIDAD: Cuenta NO DEMO detectada. Abortando trade autónomo.")
            TradeEventLog.add(
                TradeEventLog.Kind.VETO,
                "SEGURIDAD_NO_DEMO cuenta_real_bloqueada",
                decisionId = currentDecisionId
            )
            setTradingMode(AutoTradeMode.DISABLED, "seguridad:no-demo")
            return
        }

        // GUARD DE FRESCURA DEL FEED (fail-closed, fuente única = WebSocket).
        // Sin tick reciente (<5000 ms) o sin conexión, se veta la operación: no se degrada
        // a ninguna otra fuente (la captura de pantalla fue retirada por consumo de batería).
        val tickAgeMs = latestMarketTick?.let { System.currentTimeMillis() - it.timestampMs }
        if (tickAgeMs == null || tickAgeMs >= FEED_MAX_AGE_MS || !isFeedFresh()) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO por feed rancio: " +
                    "ageMs=${tickAgeMs ?: -1} (umbral=${FEED_MAX_AGE_MS}ms) " +
                    "fresh=${isFeedFresh()} → no se degrada a otra fuente"
            )
            // R13/A2d + A5: el caso "activo activo sin feed" (Aave(OTC): socket vivo, 0 ticks)
            // YA queda cubierto por este guard, porque sin tick su edad es null o >= umbral.
            // Se registra para que sea observable por `/events` en vez de solo por logcat, que
            // en este dispositivo no funciona. No se anade un veto nuevo: seria duplicar este.
            TradeEventLog.add(
                TradeEventLog.Kind.VETO,
                "FEED_RANCIO ageMs=${tickAgeMs ?: -1} activo=" +
                    AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "?" },
                decisionId = currentDecisionId
            )
            if (tickAgeMs == null) {
                TradeEventLog.add(
                    TradeEventLog.Kind.FEED_MUDO,
                    "activo=${AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "?" }} sin_ningun_tick",
                    decisionId = currentDecisionId
                )
            }
            return
        }

        // GUARD DE PRECIO CONGELADO / FEED SIN VOLATILIDAD MEDIBLE (veto V2 de B2).
        // El feed puede estar fresco (l llegan ticks) y aun asi no aportar nada operable: el
        // emisor entrega marcas de precio que caben en el error de redondeo. En ese estado
        // cualquier decision CALL/PUT es equivalente a lanzar una moneda.
        //
        // Ronda 12: antes habia una EXCEPCION explicita para los activos "IDX", que anulaba
        // justo este veto en el unico activo que lo necesitaba. Medido en `data/ticks`:
        // Z-CRY/IDX tiene rango relativo 2,547e-09 frente a 4,164e-02 de ETHUSD-OTC (8 ordenes
        // de magnitud). El veto pasa a ser POR CONDICION DE FEED, no por nombre de activo:
        // se aplica a cualquiera que no se mueva, con el umbral congelado en PREREGISTRO_R12.
        val rangoRelativo = recentPriceRangeRatio()
        val currentObservedAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
        // R23A Iter 1: el umbral se clasifica por familia de activo (`IDX` -> 1e-11, resto -> 1e-5)
        // en vez de ser universal. Razon medida: el indice sintetico mueve 2,9e-10 de mediana, o sea
        // 4 ordenes por debajo de 1e-5, asi que con el umbral universal V2 vetaba el 100% de las
        // decisiones y el activo era inoperable por construccion. Ver el KDoc de la constante.
        val umbralMinimo = rangoRelativoMinimoParaActivo(currentObservedAsset)
        if (rangoRelativo >= 0.0 && rangoRelativo < umbralMinimo) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO (V2) por feed sin volatilidad medible: " +
                    "rangoRelativo=$rangoRelativo < $umbralMinimo (activo=$currentObservedAsset)"
            )
            // R13/A5: sin logd no hay observabilidad por logcat, asi que el veto se registra
            // ademas en el buffer consultable por `/events`.
            TradeEventLog.add(
                TradeEventLog.Kind.VETO,
                "V2 rango=%.3e umbral=%.0e activo=%s".format(
                    java.util.Locale.US, rangoRelativo, umbralMinimo, currentObservedAsset
                ),
                decisionId = currentDecisionId
            )
            return
        }

        // Veto complementario: el feed declara congelacion (metodo del cliente WebSocket).
        // Se mantiene ademas del anterior porque cubre el caso de precio identico tick a tick,
        // donde el rango relativo es exactamente 0 y ya lo captura V2.
        if (isPriceFrozen()) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO por precio congelado (activo=$currentObservedAsset)"
            )
            TradeEventLog.add(
                TradeEventLog.Kind.VETO,
                "CONGELADO activo=$currentObservedAsset",
                decisionId = currentDecisionId
            )
            return
        }

        // GUARD DE COINCIDENCIA PANTALLA <-> FEED (Ronda 20, fail-closed).
        //
        // Por que existe. La recuperacion de activo sin feed (PREREGISTRO_R20 §4.1) puede dejar el
        // feed apuntando a un RIC DISTINTO del grafico que el usuario tiene abierto en el broker.
        // Medido: con el grafico en `Aave (OTC)`, el feed sano disponible es `ETHUSD-OTC`.
        //
        // Sin este guard, el bot decidiria CALL/PUT con el precio de un subyacente y pulsaria los
        // botones del grafico de OTRO. En opciones binarias eso no es un fallo de calidad: es una
        // orden a ciegas sobre un activo distinto.
        //
        // Antes de esta ronda NO existia ninguna comprobacion de este tipo (verificado leyendo
        // `executeHeadlessTrade`: los guards eran feed rancio, V2, congelado, una-por-vela, veto
        // francotirador, ventana sniper, anti-chop, cooldown y decision adaptativa; ninguno
        // comparaba activos). Este guard solo ANADE una restriccion: no relaja ninguna.
        //
        // FAIL-CLOSED: sin confirmacion de pantalla no se autoriza (criterio B.4 del PREREGISTRO_
        // R20). La logica vive en una funcion pura para poder fijarla por test.
        val motivoDesajuste = motivoVetoPorDesajusteDeActivo(
            activoPantallaVisible = AutoTradeAccessibilityService.latestObservedAsset,
            activoFeed = feedAsset()
        )
        if (motivoDesajuste != null) {
            vetoActivoDesajustado++
            Log.w("TradingEngine", "⛔ Headless Trade $action VETADO por $motivoDesajuste")
            TradeEventLog.add(TradeEventLog.Kind.VETO, motivoDesajuste, decisionId = currentDecisionId)
            return
        }

        val currentCandleEpochMinute = (latestMarketTick?.timestampMs ?: System.currentTimeMillis()) / 60000L
        synchronized(this) {
            if (lastExecutedCandleEpochMinute == currentCandleEpochMinute) {
                Log.d("TradingEngine", "⛔ Headless Trade $action bloqueado: Vela de minuto $currentCandleEpochMinute ya fue operada (1 orden por vela máx).")
                TradeEventLog.add(TradeEventLog.Kind.VETO, "VELA_YA_OPERADA minuto=$currentCandleEpochMinute", decisionId = currentDecisionId)
                return
            }
        }

        val sec = latestMarketTick?.candleSecond ?: (((System.currentTimeMillis() / 1000L) % 60L).toInt())
        val isYolo = (autonomousSubMode == AutonomousSubMode.YOLO)
        val isSniper = (autonomousSubMode == AutonomousSubMode.SNIPER)

        if (isSniper) {
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic && strategy != AutoTradeStrategy.MT_MASTER_COMBO && strategy != AutoTradeStrategy.AUTO_ADAPTIVE && strategy != AutoTradeStrategy.SUPPORT_RESISTANCE && strategy != AutoTradeStrategy.MT_REJECTION) {
                Log.d("TradingEngine", "Headless bloqueado por Veto Francotirador: Activo sintético/OTC ($currentAsset)")
                TradeEventLog.add(TradeEventLog.Kind.VETO, "FRANCOTIRADOR activo=$currentAsset", decisionId = currentDecisionId)
                return
            }
        }

        // Ventana Sniper Quirúrgica conforme a master_traders_skill (inicio por A3, default :01)
        val inWindow = MarketTickFilters.isInEntryWindow(sec)
        if (!inWindow) {
            Log.d("TradingEngine", "Headless bloqueado fuera de ventana timing sniper (⏱ ${sec}s | SNIPER=$isSniper | YOLO=$isYolo)")
            TradeEventLog.add(TradeEventLog.Kind.VETO, "FUERA_DE_VENTANA sec=$sec", decisionId = currentDecisionId)
            return
        }
        val isRejectionOrBounce = reasonDescription.contains("MT_REJECTION") ||
                reasonDescription.contains("MT_RANGE") ||
                reasonDescription.contains("MT_3_VELAS") ||
                reasonDescription.contains("MT_REVERSAL") ||
                reasonDescription.contains("MT_CONFIRM") ||
                reasonDescription.contains("MT_ENGULFING") ||
                reasonDescription.contains("QUANT_CRYPTO")
        if (syntheticCandleEngine.isChoppinessDetected() && !isRejectionOrBounce) {
            Log.d("TradingEngine", "Headless bloqueado por Filtro Anti-Choppy (<0.05% y ticks alternantes)")
            TradeEventLog.add(TradeEventLog.Kind.VETO, "CHOP", decisionId = currentDecisionId)
            return
        }

        // 1. Espaciado y Cooldown post-resolución
        val timeSinceLastResolution = System.currentTimeMillis() - lastTradeResolutionTime
        val minSpacingMs = when {
            isYolo -> 8000L // 8 segundos en YOLO para no trabar la siguiente vela
            riskManager.currentLossStreak > 0 -> 35000L
            else -> 12000L
        }
        if (lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs) {
            Log.d("TradingEngine", "Headless bloqueado por Cooldown post-resolución (${timeSinceLastResolution / 1000}s < ${minSpacingMs / 1000}s)")
            TradeEventLog.add(TradeEventLog.Kind.VETO, "COOLDOWN ${timeSinceLastResolution}ms", decisionId = currentDecisionId)
            return
        }

        // 2. Evaluación por Motor de Autoaprendizaje Adaptativo
        // El análisis se construye SIEMPRE desde el motor sintético de ticks WebSocket.
        // Ya no se reutiliza latestAnalysisResult (visión): sus mechas/S-R/choppiness son
        // ceros en modo WebSocket puro y vetaban señales sobre datos nulos.
        val effectiveAnalysis = buildAnalysisFromSyntheticEngine(latestMarketTick ?: return)

        val adaptiveDecision = adaptiveLearningEngine.evaluateSignalSuitability(
            candidateAction = action,
            analysis = effectiveAnalysis,
            tick = latestMarketTick,
            strategyName = strategy.name
        )
        val finalAction: TradeAction
        val finalReason: String
        val adaptiveModifier: Float

        when (adaptiveDecision) {
            is AdaptiveDecision.Block -> {
                Log.w("TradingEngine", "⛔ Headless Trade $action VETADO por Autoaprendizaje: ${adaptiveDecision.reason}")
                TradeEventLog.add(TradeEventLog.Kind.VETO, "ADAPTIVE_BLOCK ${adaptiveDecision.reason}", decisionId = currentDecisionId)
                handler.post {
                    Toast.makeText(context, "⛔ [Autoaprendizaje] Entrada bloqueada: ${adaptiveDecision.reason}", Toast.LENGTH_SHORT).show()
                }
                return
            }
            is AdaptiveDecision.Invert -> {
                finalAction = adaptiveDecision.invertedAction
                finalReason = "$reasonDescription [🔄 Invertida por Autoaprendizaje]"
                adaptiveModifier = 1.15f
            }
            is AdaptiveDecision.Allow -> {
                finalAction = adaptiveDecision.action
                finalReason = reasonDescription
                adaptiveModifier = adaptiveDecision.confidenceModifier
            }
        }

        val headlessConfidence = (0.85f * adaptiveModifier).coerceIn(0.1f, 1.0f)
        val (canTrade, riskReason) = riskManager.canExecuteTrade(autonomousSubMode, headlessConfidence)
        if (!canTrade) {
            Log.d("TradingEngine", "Headless bloqueado por riesgo: $riskReason")
            TradeEventLog.add(TradeEventLog.Kind.VETO, "RISK_LIMIT $riskReason", decisionId = currentDecisionId)
            if (isSniper && (riskReason.contains("alcanzado", ignoreCase = true) || riskReason.contains("finalizada", ignoreCase = true) || riskReason.contains("Límite", ignoreCase = true))) {
                setTradingMode(AutoTradeMode.DISABLED, "engine:sniper-limite(headless)")
                sniperShutdownReason = riskReason
                handler.post {
                    Toast.makeText(context, "🛑 $riskReason", Toast.LENGTH_LONG).show()
                    OverlayService.instance?.updateHUDView()
                }
            }
            return
        }

        // VETO V1 (B2): exclusividad mutua S/R <-> anti-chop.
        // El filtro S/R y el plan de accion pueden pedir direcciones OPUESTAS; es una
        // contradiccion interna del analisis, no una senal debil. Caso real capturado en R11
        // (`clock_03.png`): "Toque Directo en Resistencia [PUT Optimo]" frente a
        // "Cerca de Soporte (15%) ... CALL". Hasta ahora solo se ETIQUETABA en el HUD
        // (`HudCoherence.actionConflict` se usaba unicamente para pintar el aviso) y la orden
        // se disparaba igual. Ahora veta, con el umbral congelado en PRAGEREGISTRO_R12.
        val reasoning = getEngineReasoning()
        if (HudCoherence.actionConflict(reasoning.srFilterStatus, reasoning.actionPlan)) {
            Log.w(
                "TradingEngine",
                "⛔ Headless Trade $action VETADO (V1) por conflicto S/R<->Plan: " +
                    "SR=\"${reasoning.srFilterStatus}\" PLAN=\"${reasoning.actionPlan}\""
            )
            // R13/A5: el veto queda observable desde `/events` sin depender de logcat.
            TradeEventLog.add(
                TradeEventLog.Kind.VETO,
                "V1 SR=\"${reasoning.srFilterStatus}\" PLAN=\"${reasoning.actionPlan}\"",
                decisionId = currentDecisionId
            )
            return
        }

        val (screenW, screenH) = CalibrationManager.getRealScreenDimensions(context)
        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || screenW > screenH

        val calibCoords = if (calibrationManager != null && calibrationManager!!.isCalibrated()) {
            if (finalAction == TradeAction.BUY) calibrationManager!!.getBuyCoordinates()
            else calibrationManager!!.getSellCoordinates()
        } else null

        val (rawX, rawY) = if (calibCoords != null) {
            calibCoords
        } else {
            if (isLand) {
                if (finalAction == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                if (finalAction == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.903f)
                else Pair(screenW * 0.75f, screenH * 0.903f)
            }
        }

        // Sanitización estricta: en modo vertical, nunca permitir clics por encima del 80.0% (fila de gráfico/indicadores)
        val (x, y) = if (!isLand && rawY < screenH * 0.80f) {
            Log.w("TradingEngine", "⚠️ Coordenada Y corregida de $rawY a ${screenH * 0.838f} para tocar botones de orden")
            Pair(rawX, screenH * 0.838f)
        } else {
            Pair(rawX, rawY)
        }

        // Ronda 21 (Frente E): SHADOW MODE FORMAL
        // Si el motor está en SHADOW, todas las defensas y cálculos corren exactamente igual,
        // pero NUNCA se despacha un tap al broker ni se altera el saldo ni el W/L real.
        if (mode == AutoTradeMode.SHADOW) {
            shadowOrdersGenerated++
            val currentPrice = latestMarketTick?.price ?: 0.0
            Log.i("TradingEngine", "👻 [SHADOW MODE] Orden simulada autorizada: $finalAction @ $currentPrice (segundo=$sec, decisionId=$currentDecisionId) - Cero taps, saldo intacto")
            TradeEventLog.add(
                TradeEventLog.Kind.SHADOW_DECISION,
                "SHADOW $finalAction @ $currentPrice conf=$headlessConfidence strat=${strategy.name} sub=${autonomousSubMode.name}",
                decisionId = currentDecisionId,
                tradeId = null
            )
            TradeJournalLogger.logShadowTrade(
                context = context,
                strategy = strategy.name,
                submode = autonomousSubMode.name,
                action = finalAction.name,
                confidence = headlessConfidence,
                priceY = currentPrice.toFloat(),
                trend = syntheticCandleEngine.detectedTrend.name,
                candleSecond = sec,
                decisionId = currentDecisionId,
                reason = "SHADOW_AUTORIZADA: pasó todos los vetos"
            )
            synchronized(this) {
                lastExecutedCandleEpochMinute = currentCandleEpochMinute
            }
            return
        }

        val accessibility = AutoTradeAccessibilityService.instance
        val observed = accessibility?.readCurrentBalance() ?: AutoTradeAccessibilityService.latestObservedBalance
        val baseBal = if (observed > 0.0) observed else AutoTradeAccessibilityService.latestObservedBalance
        val currentTradeId = java.util.UUID.randomUUID().toString()
        // H9: telemetría del segundo REAL de entrada (no el de liquidación del journal). Permite
        // correlacionar el segundo de disparo con el resultado, la métrica que el journal no captura.
        Log.i("TradingEngine", "🎯 ENTRADA despachada | ${finalAction} | segundo=$sec | estrategia=${strategy.name} | decisionId=$currentDecisionId | tradeId=$currentTradeId | $finalReason")
        isTradeResolving.set(false)
        riskManager.recordTradeSent(
            action = finalAction,
            entryPrice = latestMarketTick?.price ?: 0.0,
            entryPriceY = 0f,
            baseBalance = baseBal,
            confidence = headlessConfidence,
            entryCandleSecond = sec,
            reason = finalReason,
            decisionId = currentDecisionId,
            tradeId = currentTradeId
        )
        synchronized(this) {
            lastExecutedCandleEpochMinute = currentCandleEpochMinute
        }
        adaptiveLearningEngine.recordTradeOpened(
            action = finalAction,
            analysis = effectiveAnalysis,
            tick = latestMarketTick,
            strategyName = strategy.name
        )
        // Despacho táctil único con verificación y fallback ROOT transparente
        AutoTradeAccessibilityService.performClick(x, y, decisionId = currentDecisionId, tradeId = currentTradeId) { success, error ->
            if (!success) {
                triggerManualFallback(finalAction, error ?: "Auto-clic headless falló en 300ms")
            } else {
                riskManager.captureTapLatency(
                    tapStartTs = AutoTradeAccessibilityService.lastTapStartTs,
                    tapEndTs = AutoTradeAccessibilityService.lastTapEndTs,
                    candleSecondAtTapEnd = AutoTradeAccessibilityService.lastTapCandleSecond,
                    priceAtTapEnd = latestMarketTick?.price ?: 0.0
                )
            }
        }

        handler.post {
            drawingView.triggerClickAnimation(x, y)
            // Ronda 11 (T3): strike por PRECIO de entrada del tick WS, no por la Y del tap.
            autoDrawEngine.drawTradeEntry(
                action = finalAction,
                entryPrice = latestMarketTick?.price ?: 0.0,
                screenWidth = screenW,
                stake = riskManager.getEffectiveStake(autonomousSubMode),
                payoutRate = riskManager.sorosPayoutRate.toDouble()
            )
            emitHapticAndAudioFeedback()
            val stake = riskManager.getEffectiveStake(autonomousSubMode)
            Toast.makeText(context, "⚡ [${strategy.name}] BOT OPERÓ: $finalAction ($$$stake)\n$finalReason", Toast.LENGTH_LONG).show()
            onTradeExecutedListener?.invoke(finalAction, true)
        }
    }

    /**
     * Ronda 12 (B1): ventana en la que se exige que el broker confirme la orden.
     *
     * Con stake de opciones binarias el saldo se debita al ABRIR, asi que un movimiento del
     * saldo es prueba de que la orden existe. Si pasada la ventana el saldo sigue identico al
     * de apertura, la orden NO se registro: es una operacion fantasma, no un empate.
     */
    private fun checkOrderConfirmation() {
        val snapshot = synchronized(riskManager) {
            if (!riskManager.hasPendingTrade || riskManager.orderState != RiskManager.OrderState.ENVIADA) null
            else Triple(riskManager.pendingTradeStartTime, riskManager.pendingTradeBaseBalance, riskManager.pendingTradeAction)
        } ?: return

        val (startMs, baseBalance, action) = snapshot
        if (startMs <= 0L) return
        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000

        val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance()
            ?: AutoTradeAccessibilityService.latestObservedBalance

        if (riskManager.isOrderConfirmed(baseBalance, currentBal, elapsedSec)) {
            riskManager.markOrderConfirmed()
            Log.i(
                "TradingEngine",
                "✅ ORDEN CONFIRMADA por movimiento de saldo | $action | base=$baseBalance ahora=$currentBal (${elapsedSec}s)"
            )
            handler.post { OverlayService.instance?.updateHUDView() }
            return
        }

        if (riskManager.isOrderUnprocessed(baseBalance, currentBal, elapsedSec)) {
            // La orden nunca llego al broker. Se cierra el estado pendiente para que el HUD deje
            // de mostrar "Trade Activo" y el motor pueda volver a operar; se registra el fallo
            // en el journal como fallo de ENVIO, no como empate de precio.
            riskManager.markOrderUnprocessed()
            Log.w(
                "TradingEngine",
                "⛔ ORDEN NO PROCESADA: el saldo no se movio en ${elapsedSec}s " +
                    "(base=$baseBalance ahora=$currentBal) | $action | el tap no creo operacion"
            )
            handler.post {
                Toast.makeText(
                    context,
                    "⛔ Orden NO procesada: el tap no llego al broker (saldo sin cambios)",
                    Toast.LENGTH_LONG
                ).show()
                OverlayService.instance?.updateHUDView()
            }
            return
        }
    }

    private fun checkHeadlessTradeResolution() {
        checkOrderConfirmation()
        val snapshot = synchronized(riskManager) {
            if (!riskManager.hasPendingTrade) null
            else {
                val startMs = riskManager.pendingTradeStartTime
                if (startMs <= 0L) {
                    Log.w("TradingEngine", "Liquidación abortada: pendiente sin timestamp de apertura")
                    null
                } else {
                    PendingTradeSnapshot(
                        action = riskManager.pendingTradeAction ?: TradeAction.BUY,
                        startTime = startMs,
                        entryPrice = riskManager.pendingTradeEntryPrice,
                        baseBalance = riskManager.pendingTradeBaseBalance,
                        confidence = riskManager.pendingTradeConfidence,
                        decisionId = riskManager.pendingTradeDecisionId,
                        tradeId = riskManager.pendingTradeTradeId
                    )
                }
            }
        } ?: return

        val elapsedSec = (System.currentTimeMillis() - snapshot.startTime) / 1000
        val expDurationSec = timeframe.seconds
        val isExpired = elapsedSec >= (expDurationSec + 2)
        if (!isExpired) return

        val baseBalance = snapshot.baseBalance
        val saldoFresco = isBalanceFresh(
            AutoTradeAccessibilityService.instance != null,
            AutoTradeAccessibilityService.lastBalanceReadMs,
            System.currentTimeMillis()
        )
        val currentBal = AutoTradeAccessibilityService.instance?.readCurrentBalance()
            ?: AutoTradeAccessibilityService.latestObservedBalance

        var isWin: Boolean? = null
        var isTie = false
        var method = ""

        if (baseBalance > 0.0 && currentBal > 0.0) {
            val diff = currentBal - baseBalance
            if (diff > 10.0) {
                isWin = true
                method = "SALDO (+) Ganancia acreditada por Binomo: Diff=+$diff COP (${elapsedSec}s)"
            } else if (diff < -10.0 && elapsedSec >= (expDurationSec + 12)) {
                isWin = false
                method = "SALDO (-) Pérdida confirmada tras liquidación: Diff=$diff COP (${elapsedSec}s)"
            } else if (Math.abs(diff) <= 10.0 && elapsedSec >= (expDurationSec + 15)) {
                isTie = true
                method = "ORDEN NO PROCESADA (Diff=$diff -> Saldo inalterado tras ${elapsedSec}s)"
            }
        } else if (elapsedSec >= (expDurationSec + 5)) {
            // Ronda 11 (T3): comparación en DOMINIO DE PRECIO, con la misma función que usa el HUD
            // para pintar ITM/OTM. Una sola definición de "va ganando" en todo el sistema.
            val pendingEntry = snapshot.entryPrice
            val latestPrice = latestMarketTick?.price ?: 0.0
            if (pendingEntry > 0.0 && latestPrice > 0.0) {
                val moneyness = StrikeCalculator.moneyness(snapshot.action, pendingEntry, latestPrice)
                when (moneyness) {
                    TradeMoneyness.TIE -> {
                        isTie = true
                        method = "PRECIO (=) Empate técnico en tick WS: Entry=$pendingEntry vs Close=$latestPrice (${elapsedSec}s)"
                    }
                    TradeMoneyness.ITM -> {
                        isWin = true
                        method = "PRECIO (+) Tick WS: Entry=$pendingEntry vs Close=$latestPrice (${elapsedSec}s)"
                    }
                    TradeMoneyness.OTM -> {
                        isWin = false
                        method = "PRECIO (-) Tick WS: Entry=$pendingEntry vs Close=$latestPrice (${elapsedSec}s)"
                    }
                }
            } else if (elapsedSec >= (expDurationSec + 15)) {
                isTie = true
                method = "TIMEOUT ${expDurationSec + 15}s (Sin saldo legible -> Cancelación preventiva)"
            }
        }

        if (isWin != null || isTie) {
            if (!isTradeResolving.compareAndSet(false, true)) return

            val finalWin = isWin ?: false
            val pendingAction = snapshot.action
            val pendingEntry = snapshot.entryPrice
            val pendingConfidence = snapshot.confidence
            val pendingStake = riskManager.getEffectiveStake()
            val pendingTradeStartMs = snapshot.startTime
            val sec = latestMarketTick?.candleSecond ?: (((System.currentTimeMillis() / 1000L) % 60L).toInt())
            val wsAnalysis = VisionAnalysisResult(
                trend = syntheticCandleEngine.detectedTrend,
                distanceToSupportRatio = syntheticCandleEngine.distanceToSupportRatio,
                distanceToResistanceRatio = syntheticCandleEngine.distanceToResistanceRatio,
                candleSecond = sec
            )
            val journalResult = tieJournalResult(isTie, saldoFresco) ?: if (finalWin) "WIN" else "LOSS"

            handler.post {
                autoDrawEngine.clearTradeEntry()

                TradeJournalLogger.logTrade(
                    context = context,
                    strategy = strategy.name,
                    submode = autonomousSubMode.name,
                    action = pendingAction.name,
                    confidence = pendingConfidence,
                    priceY = pendingEntry.toFloat(),
                    stake = pendingStake,
                    baseBalance = baseBalance,
                    result = journalResult,
                    settledBalance = currentBal,
                    durationSec = elapsedSec,
                    reason = method,
                    trend = wsAnalysis.trend.name,
                    distSupportRatio = wsAnalysis.distanceToSupportRatio,
                    distResistanceRatio = wsAnalysis.distanceToResistanceRatio,
                    tickVelocity = latestMarketTick?.velocity ?: 0f,
                    impulse = when {
                        latestMarketTick?.isBullishImpulse == true -> "BULLISH"
                        latestMarketTick?.isBearishImpulse == true -> "BEARISH"
                        else -> "NEUTRAL"
                    },
                    marketRegime = if (syntheticCandleEngine.isChoppinessDetected()) "CHOP" else "TRENDING",
                    // H10: se persiste el segundo REAL de ENTRADA (capturado al enviar la orden),
                    // no el de liquidación. Si por alguna ruta no se capturó (-1), se usa el actual.
                    candleSecond = if (riskManager.pendingTradeEntryCandleSecond >= 0)
                        riskManager.pendingTradeEntryCandleSecond else sec,
                    adaptiveStatus = journalResult,
                    tradeStartMs = pendingTradeStartMs,
                    // Ronda 14 (A2): instrumentacion de latencia. Se persiste lo que realmente
                    // midio la ruta de tap, no una reconstruccion: el journal guardaba solo el
                    // segundo de la DECISION, que con 255-1205 ms de latencia puede no ser el
                    // segundo en que el broker ve el toque.
                    latency = TradeJournalLogger.LatencyRecord(
                        signalTs = riskManager.pendingTradeSignalTs,
                        tapStartTs = riskManager.pendingTradeTapStartTs,
                        tapEndTs = riskManager.pendingTradeTapEndTs,
                        candleSecondAtTapEnd = riskManager.pendingTradeCandleSecondAtTapEnd,
                        priceAtSignal = riskManager.pendingTradePriceAtSignal,
                        priceAtTapEnd = riskManager.pendingTradePriceAtTapEnd
                    ),
                    decisionId = snapshot.decisionId,
                    tradeId = snapshot.tradeId
                )

                if (isTie) {
                    recordTieVoidIfFresh(riskManager, saldoFresco)
                    val mensaje = if (saldoFresco) "[HEADLESS] ⚪ Empate / Orden cancelada" else "[HEADLESS] ⚪ Empate incierto / saldo no fresco"
                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                    OverlayService.instance?.updateHUDView()
                } else if (finalWin) {
                    if (riskManager.resolvePendingTradeWin()) {
                        adaptiveLearningEngine.recordTradeOutcome(true, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] 🎉 GANADA (+1 W) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, true)
                    } else {
                        riskManager.recordTradeWin(force = true)
                        adaptiveLearningEngine.recordTradeOutcome(true, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] 🎉 GANADA (+1 W) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, true)
                    }
                } else {
                    if (riskManager.resolvePendingTradeLoss()) {
                        adaptiveLearningEngine.recordTradeOutcome(false, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] ⚠️ PERDIDA (+1 L) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, false)
                    } else {
                        riskManager.recordTradeLoss(force = true)
                        adaptiveLearningEngine.recordTradeOutcome(false, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] ⚠️ PERDIDA (+1 L) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, false)
                    }
                }
                lastTradeResolutionTime = System.currentTimeMillis()
                isTradeResolving.set(false)
                checkSniperSessionLimits()
            }
        } else {
            isTradeResolving.set(false)
        }
    }

    fun stop() {
        setTradingMode(AutoTradeMode.DISABLED, "engine:stop()")
        autoDrawEngine.clearAutoDrawings()
    }
}
