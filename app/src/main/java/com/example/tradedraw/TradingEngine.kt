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
import com.example.tradedraw.core.AutoTradeMode
import com.example.tradedraw.core.AutonomousSubMode
import com.example.tradedraw.core.CapitalPolicy
import com.example.tradedraw.core.DecisionIds
import com.example.tradedraw.core.EventSink
import com.example.tradedraw.core.MarketTick
import com.example.tradedraw.core.TapCoordinateResolver
import com.example.tradedraw.core.OrderGateway
import com.example.tradedraw.core.OrderRequest
import com.example.tradedraw.core.OrderReceipt
import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.execution.CandleRateLimiter
import com.example.tradedraw.execution.ExecutionContext
import com.example.tradedraw.execution.ExecutionPolicyResult
import com.example.tradedraw.execution.ExecutionPolicy

class TradingEngine(
    private val context: Context,
    private val drawingView: CustomDrawingView,
    val riskManager: RiskManager,
    var calibrationManager: CalibrationManager? = null,
    val aiClient: com.example.tradedraw.execution.AIAdvisor = AIClient(context),
    val brokerRuntime: com.example.tradedraw.core.BrokerRuntimePort = AndroidBrokerRuntime(context),
    val eventSink: EventSink = RingBufferEventSink(),
    val capitalPolicy: CapitalPolicy = riskManager,
    var tapCoordinateResolver: TapCoordinateResolver = CalibrationTapCoordinateResolver { calibrationManager },
    val candleRateLimiter: CandleRateLimiter = CandleRateLimiter(),
    var hudPort: com.example.tradedraw.core.HudPort? = null,
    val orderGateway: OrderGateway = com.example.tradedraw.core.SingleFlightOrderGateway(
        createAndroidOrderGateway(hudPort = hudPort),
        PersistentPendingDispatchStore(context))
) {
    val executionPolicy: ExecutionPolicy = createExecutionPolicy()
    val shadowSimulation = com.example.tradedraw.simulation.ShadowSimulationRuntime(context, eventSink)
    var shadowSimulationEnabled: Boolean
        get() = shadowSimulation.enabled
        set(value) { shadowSimulation.enabled = value }
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
        eventSink.add(
            TradeEventLog.Kind.MODE_CHANGE,
            "de=$anterior a=$newMode source=$source origen=$origen"
        )

        if (newMode == AutoTradeMode.AUTONOMOUS) {
            // R26: el armado por bridge abre la ventana de caducidad; el armado por HUD no.
            autonomousWatchdog.arm(System.currentTimeMillis(), source.startsWith("bridge:"))
            agentController.start()
        } else {
            autonomousWatchdog.disarm()
            agentController.stop()
        }
        return true
    }

    /**
     * R26: senal de vida del host que supervisa. Cualquier peticion al bridge la emite; sin
     * peticiones durante [AUTONOMOUS_HEARTBEAT_TIMEOUT_MS] el motor se desarma solo.
     */
    fun noteBridgeHeartbeat() {
        autonomousWatchdog.heartbeat(System.currentTimeMillis())
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
            // R23D Fase 1 (REQ 3): sincronizacion del submodo EFECTIVO hacia el RiskManager.
            // `RiskManager.currentSubMode` es el fallback de `getRemainingCooldown()` /
            // `canExecuteTrade()` cuando el caller no pasa argumento explicito. Su default es
            // CONSERVATIVE (fail-safe), asi que sin esta linea HealthSupervisor y el endpoint
            // `/status` calculaban el cooldown con la politica de perdidas de CONSERVATIVE (180s)
            // aunque el motor estuviera en YOLO (35s), publicando un `COOLDOWN_ACTIVE: 178s`
            // espurio. El motor es la fuente de verdad del submodo; el RiskManager solo lo espeja.
            riskManager.currentSubMode = value
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
                    hudPort?.updateHUDView()
                }
            }
        }
    }
    private val liveStrategyEvaluator = LiveStrategyEvaluator(brokerRuntime)
    val syntheticCandleEngine = SyntheticCandleEngine()
    val adaptiveLearningEngine = AdaptiveLearningEngine()

    var strategy: AutoTradeStrategy = AutoTradeStrategy.AUTO_ADAPTIVE
        set(value) {
            field = value
            syntheticCandleEngine.currentStrategy = value
        }
    var debugModeEnabled: Boolean = false

    val visionAnalyzer = VisionAnalyzer()
    private val autoDrawEngine = AutoDrawEngine(drawingView) { syntheticCandleEngine.recentPriceBounds() }
    private val handler = Handler(Looper.getMainLooper())

    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 1000L

    /**
     * R26: caducidad de `AUTONOMOUS` armado desde el bridge cuando el host deja de dar senal.
     * Ver [AutonomousWatchdog] para el alcance y el porque de excluir el armado por HUD.
     */
    private val autonomousWatchdog = AutonomousWatchdog(AUTONOMOUS_HEARTBEAT_TIMEOUT_MS)

    private val autonomousWatchdogTick = object : Runnable {
        override fun run() {
            try {
                if (mode == AutoTradeMode.AUTONOMOUS &&
                    autonomousWatchdog.shouldExpire(System.currentTimeMillis())
                ) {
                    // `setTradingMode` ya registra el MODE_CHANGE; el source permite atribuirlo.
                    setTradingMode(AutoTradeMode.DISABLED, "engine:autonomous-expiry")
                }
            } catch (e: Exception) {
                Log.e("TradingEngine", "Fallo en el watchdog de AUTONOMOUS", e)
            } finally {
                handler.postDelayed(this, AUTONOMOUS_WATCHDOG_TICK_MS)
            }
        }
    }

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
            val assetName = brokerRuntime.getBrokerState().rawObservedAsset.ifBlank { "Crypto IDX" }
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

        // R26: arranca el vigilante de caducidad. Es un tick barato (una comparacion de relojes)
        // y arranca con el motor, no con el armado: asi una sesion armada antes de este codigo
        // tambien queda cubierta.
        handler.postDelayed(autonomousWatchdogTick, AUTONOMOUS_WATCHDOG_TICK_MS)
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
    var lastExecutedCandleEpochMinute: Long
        get() = candleRateLimiter.lastExecutedCandleEpochMinute
        set(value) {
            candleRateLimiter.lastExecutedCandleEpochMinute = value
        }

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
        shadowSimulation.onTick(tick)
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
            eventSink.add(
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

        // Cálculo dinámico de probabilidades para termómetro del HUD y telemetría.
        // R23H Fase 2: extraido a `calcularPoderesDeMercado` para que el arnes de replay
        // (`ReplayBacktestTest`) pueda reproducir el MISMO callPower sin duplicar la formula.
        // Duplicarla habria hecho que replay y vivo divergieran en silencio al primer ajuste.
        val (callPower, putPower) = calcularPoderesDeMercado(
            trend = trend,
            distSupportRatio = distSup,
            distResistanceRatio = distRes,
            rsi = rsi,
            isBullishImpulse = tick.isBullishImpulse,
            isBearishImpulse = tick.isBearishImpulse
        )

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

                val currentBal = brokerRuntime.refreshBalance()

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
                    // R24/B1.a: la clave de idempotencia se captura ANTES del `post`. Dentro del
                    // hilo principal otra ruta (HUD, bridge) puede haber limpiado el pendiente y
                    // `pendingTradeTradeId` ya estaria vacio: la clave se perderia justo cuando
                    // hace falta.
                    val settleTradeId = riskManager.pendingTradeTradeId
                    val settleStartMs = riskManager.pendingTradeStartTime
                    // R24/B1.b: sin saldo legible no hay evidencia de saldo; la LOSS se resolvio
                    // por accion de precio. Es exactamente el caso que el contador monotono mide.
                    val lossSinEvidencia = !finalWin && !isTie && !(baseBalance > 0.0 && currentBal > 0.0)
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
                            hudPort?.updateHUDView()
                        } else if (finalWin) {
                            val resolved = riskManager.resolvePendingTradeWin()
                            if (!resolved) {
                                riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = true)
                            }
                            TradeJournalLogger.logTrade(context, strategy.name, autonomousSubMode.name, action?.name ?: "UNKNOWN", pendingConfidence, entryY, riskManager.getEffectiveStake(), baseBalance, "WIN", currentBal, elapsedSec, method, curTrend, supDist, resDist, tickVel, imp, reg, sec, "WIN", tradeStartMs, decisionId = riskManager.pendingTradeDecisionId, tradeId = riskManager.pendingTradeTradeId)
                            adaptiveLearningEngine.recordTradeOutcome(true, context)
                            autoDrawEngine.clearTradeEntry()
                            emitHapticAndAudioFeedback()
                            Toast.makeText(context, "🎉 OPERACIÓN GANADA (+1 W)", Toast.LENGTH_LONG).show()
                            onTradeExecutedListener?.invoke(action ?: TradeAction.BUY, true)
                        } else {
                            val resolved = riskManager.resolvePendingTradeLoss()
                            val counted = if (resolved) true
                                else riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = false)
                            // Solo cuenta si ESTA llamada registro el LOSS: si otra ruta ya lo hizo,
                            // sumar aqui dejaria el contador por encima de totalLosses.
                            if (counted && lossSinEvidencia) riskManager.recordLossSinEvidencia()
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
        val (action, reason) = liveStrategyEvaluator.evaluate(
            strategy = strategy,
            analysis = analysis,
            syntheticEngine = syntheticCandleEngine,
            subMode = autonomousSubMode,
            latestTick = latestMarketTick
        )
        lastSignalReason = reason
        return action
    }

    init {
        riskManager.onPendingTradeSettled = { tradeId ->
            handler.post {
                val flight = orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway
                if (flight?.pendingRequestId() == tradeId && !flight.isRecovered() && !flight.requiresReview()) {
                    try {
                        check(flight.reconcile(tradeId)) { "BROKER_RESERVATION_RELEASE_FAILED" }
                    } catch (e: Exception) {
                        eventSink.add("DISPATCH_RECOVERY", "SETTLED_RESERVATION_REQUIRES_REVIEW ${e.message}", tradeId = tradeId)
                    }
                }
            }
        }
        handler.post {
            val flight = orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway
            if (flight?.isRecovered() == true) flight.reservation()?.let {
                riskManager.holdForBrokerReconciliation(it)
                eventSink.add("DISPATCH_RECOVERY", "RECOVERED_UNCERTAIN requires=broker_history_review", it.decisionId, it.requestId)
            }
        }
    }

    fun reconcileBrokerDispatch(evidence: com.example.tradedraw.core.BrokerDispatchEvidence, callback: (Boolean) -> Unit) {
        handler.post {
            val flight = orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway
            brokerRuntime.refreshBalance()
            val freshDemo = brokerRuntime.demoAccountGuard.getAccountType() == com.example.tradedraw.core.AccountType.DEMO &&
                brokerRuntime.demoAccountGuard.isDemoConfirmedFresh(10_000L)
            val pending = flight?.reservation()
            val ok = flight?.reconcileWithBrokerEvidence(evidence, freshDemo) == true
            if (ok && pending != null) {
                riskManager.holdForBrokerReconciliation(pending)
                riskManager.applyBrokerRecovery(pending.requestId, evidence.outcome)
                eventSink.add("DISPATCH_RECOVERY", "${evidence.outcome} source=${evidence.source} reference=${evidence.brokerReference}", pending.decisionId, pending.requestId)
            }
            callback(ok)
        }
    }

    companion object {
        /**
         * R23H Fase 1: umbral de `callPower` a partir del cual una compra en UPTREND se considera
         * agotamiento de liquidez compradora en techo, y NO fuerza de continuacion.
         *
         * Origen: `workflows/R23H_BUY_FEATURE_ANALYSIS.md` §5. En la muestra R23G reconciliada el
         * bucket `BUY + UPTREND + callPower >= 70` fue el unico con poder discriminante (ratio 6:1
         * perdidas/ganancias). El umbral se congela en 70 y NO se recalibra dentro de esta fase: es
         * la hipotesis a medir en vivo, no una optimizacion cerrada.
         */
        const val VETO_BUY_UPTREND_MAX_CALL_POWER = StrategyReplayPort.VETO_BUY_UPTREND_MAX_CALL_POWER

        /**
         * R26: silencio maximo tolerado con `AUTONOMOUS` armado desde el bridge antes de
         * desarmarlo solo.
         *
         * El preregistro R25 exige sondeo de `/status` cada <= 2 min durante una ventana; 10 min
         * son 5x esa cadencia, asi que una ventana bien supervisada no puede caducar por error.
         * Los dos incidentes de rearme desatendido habrian quedado cortados (R24: 3 h 15 min;
         * R25/W02: ~5 min). Congelado en `workflows/PREREGISTRO_R26.md` antes de medir nada.
         */
        const val AUTONOMOUS_HEARTBEAT_TIMEOUT_MS = 10 * 60 * 1000L

        /** Cadencia del vigilante. Barato: solo compara relojes cuando el modo es AUTONOMOUS. */
        const val AUTONOMOUS_WATCHDOG_TICK_MS = 30 * 1000L

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
        fun generateDeterministicDecisionId(asset: String, signalTimestampMs: Long, action: String): String =
            DecisionIds.generateDeterministicDecisionId(asset, signalTimestampMs, action)

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
            latestTick: MarketTick? = null,
    brokerState: com.example.tradedraw.core.BrokerStateView = com.example.tradedraw.core.BrokerStateView(observedAsset = "", isDemoAccount = false)
        ): TradeAction?=
            FrozenStrategySignals.evaluateStrategySignalWithReason(strategy, analysis, syntheticEngine, subMode, latestTick, brokerState).first

        fun evaluateSniperConfluences(
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine?,
            latestTick: MarketTick?,
    brokerState: com.example.tradedraw.core.BrokerStateView = com.example.tradedraw.core.BrokerStateView(observedAsset = "", isDemoAccount = false)
        ): Pair<TradeAction?, String> =
            FrozenStrategySignals.evaluateSniperConfluences(analysis, syntheticEngine, latestTick, brokerState)

        fun evaluateStrategySignalWithReason(
            strategy: AutoTradeStrategy,
            analysis: VisionAnalysisResult,
            syntheticEngine: SyntheticCandleEngine? = null,
            subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE,
            latestTick: MarketTick? = null,
    brokerState: com.example.tradedraw.core.BrokerStateView = com.example.tradedraw.core.BrokerStateView(observedAsset = "", isDemoAccount = false)
        ): Pair<TradeAction?, String> =
            FrozenStrategySignals.evaluateStrategySignalWithReason(strategy, analysis, syntheticEngine, subMode, latestTick, brokerState)
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
        val isAccessOk = brokerRuntime.read().isAvailable || brokerRuntime.isAccessibilityPermissionGranted()
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

        // R23H Fase 1: VETO_BUY_EXHAUSTION_CLIMAX en la ruta de vision.
        // `executeHeadlessTrade` ya lo aplica, pero esta es la OTRA puerta que acaba en un tap
        // real (via `executeAutonomousTrade`). Sin cubrir ambas, el veto seria un bypase parcial:
        // una señal nacida del analisis de imagen en AUTONOMOUS seguiria comprando en el techo.
        // Se usa `analysis.signalPowerCall`, que es el mismo campo que esta ruta propaga al HUD.
        // Sin `decisionId`: esta ruta (vision) no tiene uno asignado todavia; el evento se emite
        // sin el para no inventar un id que luego no case con el trade.
        motivoVetoPorAgotamientoDeCompra(
            action = action,
            trend = analysis.trend,
            callPower = analysis.signalPowerCall
        )?.let { motivoVeto ->
            android.util.Log.w("TradingEngine", "⛔ Señal $action VETADA por agotamiento de compra: $motivoVeto")
            eventSink.add(TradeEventLog.Kind.VETO, motivoVeto)
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
                        hudPort?.updateHUDView()
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

        dispatchBrokerOrder(action, x, y, confidence = confidence, reason = reasonDescription) { receipt ->
            if (receipt !is OrderReceipt.Success) return@dispatchBrokerOrder

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
    }

    /** Shared entry for engine, bridge and manual CALL/PUT. Gesture completion is not broker confirmation. */
    fun dispatchBrokerOrder(
        action: TradeAction,
        x: Float,
        y: Float,
        decisionId: String = java.util.UUID.randomUUID().toString(),
        tradeId: String = java.util.UUID.randomUUID().toString(),
        confidence: Float = 0.85f,
        reason: String = "MANUAL_ORDER",
        onResult: (OrderReceipt) -> Unit = {}
    ) {
        handler.post {
            val balance = brokerRuntime.refreshBalance(useLastObservation = false)
            val coordinator = orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway
            coordinator?.cachedReceipt(tradeId)?.let { onResult(it); return@post }
            if (riskManager.hasPendingTrade || coordinator?.pendingRequestId() != null || coordinator?.wasDispatched(tradeId) == true) {
                onResult(OrderReceipt.Vetoed("ORDER_PENDING_RECONCILIATION"))
                return@post
            }
            // Reserve risk only for an allowed account/mode; the gateway rechecks at dispatch.
            if (mode == AutoTradeMode.DISABLED || mode == AutoTradeMode.SHADOW ||
                !brokerRuntime.demoAccountGuard.isDispatchAllowed()) {
                orderGateway.dispatch(OrderRequest(action, x, y, decisionId, tradeId, mode = mode), onResult)
                return@post
            }
            isTradeResolving.set(false)
            riskManager.recordTradeSent(action, latestMarketTick?.price ?: 0.0, 0f, balance,
                confidence, ((System.currentTimeMillis() / 1000L) % 60).toInt(), reason, decisionId, tradeId)
            val terminal = java.util.concurrent.atomic.AtomicBoolean(false)
            val recovery = com.example.tradedraw.core.BrokerRecoveryContext(System.currentTimeMillis(), balance,
                latestMarketTick?.price ?: 0.0, confidence, reason)
            orderGateway.dispatch(OrderRequest(action, x, y, decisionId, tradeId, mode = mode, recoveryContext = recovery)) { receipt ->
                if (terminal.compareAndSet(false, true)) {
                    handler.post {
                        when (receipt) {
                            is OrderReceipt.Vetoed -> riskManager.clearPendingTrade()
                            is OrderReceipt.Failure -> {
                                if (!receipt.requiresReconciliation) riskManager.markOrderUnprocessed(tapDespachado = false)
                                else (orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway)?.reservation()?.let {
                                    riskManager.holdForBrokerReconciliation(it)
                                    eventSink.add("DISPATCH_RECOVERY", "UNCERTAIN requires=broker_history_review", it.decisionId, it.requestId)
                                }
                                // Uncertain delivery remains pending; never ask for a second manual tap.
                            }
                            is OrderReceipt.Success -> {
                                val endTs = System.currentTimeMillis()
                                riskManager.captureTapLatency(endTs - receipt.latencyMs, endTs,
                                    receipt.candleSecond, latestMarketTick?.price ?: 0.0)
                            }
                        }
                        onResult(receipt)
                    }
                }
            }
        }
    }

    fun triggerManualFallback(action: TradeAction, reason: String) {
        Log.w("TradingEngine", "🚨 Activando MODO HÍBRIDO Fallback: $reason. Alerta manual para $action")
        emitHapticWarning()
        hudPort?.showManualFallbackAlert(action, 2500L)
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
        val accessibilityActive = brokerRuntime.read().isAvailable
        if (!accessibilityActive) {
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
        dispatchBrokerOrder(TradeAction.BUY, buyX, buyY)

        handler.postDelayed({
            Toast.makeText(context, "👉 Probando clic en BAJA...", Toast.LENGTH_SHORT).show()
            dispatchBrokerOrder(TradeAction.SELL, sellX, sellY)
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
        if (mode == AutoTradeMode.AUTONOMOUS) brokerRuntime.refreshBalance()
        if (mode == AutoTradeMode.AUTONOMOUS && !puedeOperarAutonomoEnDemo(brokerRuntime.getBrokerState().isDemoAccount)) {
            Log.e("TradingEngine", "⛔ BLOQUEO CRÍTICO DE SEGURIDAD: Cuenta NO DEMO detectada. Abortando trade autónomo.")
            eventSink.add(
                TradeEventLog.Kind.VETO,
                "SEGURIDAD_NO_DEMO cuenta_real_bloqueada",
                decisionId = currentDecisionId
            )
            setTradingMode(AutoTradeMode.DISABLED, "seguridad:no-demo")
            return
        }

        val now = System.currentTimeMillis()
        val currentCandleEpochMinute = (latestMarketTick?.timestampMs ?: now) / 60000L
        val sec = latestMarketTick?.candleSecond ?: ((now / 1000L) % 60L).toInt()
        // Preserve legacy short-circuiting: no analysis/adaptive/capital work before its veto stage.
        val effectiveAnalysis by lazy { buildAnalysisFromSyntheticEngine(requireNotNull(latestMarketTick)) }
        val executionContext = ExecutionContext(
            action = action, mode = mode, autonomousSubMode = autonomousSubMode,
            strategy = strategy, currentDecisionId = currentDecisionId,
            tickAgeMs = latestMarketTick?.let { now - it.timestampMs },
            isFeedFresh = isFeedFresh(), feedMaxAgeMs = FEED_MAX_AGE_MS,
            observedAsset = brokerRuntime.getBrokerState().rawObservedAsset,
            feedAsset = feedAsset(), rangoRelativo = recentPriceRangeRatio(),
            isPriceFrozen = isPriceFrozen(), isSyntheticOrOtc = brokerRuntime.getBrokerState().isSyntheticOrOTC,
            sec = sec, isChoppinessDetected = syntheticCandleEngine.isChoppinessDetected(),
            reasonDescription = reasonDescription, lastTradeResolutionTime = lastTradeResolutionTime,
            currentLossStreak = riskManager.currentLossStreak, currentTimeMs = now,
            currentCandleEpochMinute = currentCandleEpochMinute,
            isCandleAlreadyExecuted = synchronized(this) {
                candleRateLimiter.isCandleAlreadyExecuted(currentCandleEpochMinute, mode)
            }
        )
        val evidenceCapture = ExecutionPolicyEvidenceCapture()
        val policyResult = executionPolicy.evaluate(
            executionContext,
            adaptiveDecisionSupplier = {
                evidenceCapture.recordAdaptive(
                    adaptiveLearningEngine.evaluateSignalSuitability(
                        action, effectiveAnalysis, latestMarketTick, strategy.name
                    )
                )
            },
            capitalDecisionSupplier = { confidence ->
                evidenceCapture.recordCapital(
                    capitalPolicy.canExecuteTrade(autonomousSubMode, confidence)
                )
            },
            srPlanSupplier = {
                val reasoning = getEngineReasoning()
                evidenceCapture.recordSrPlan(reasoning.srFilterStatus to reasoning.actionPlan)
            },
            buyExhaustionSupplier = {
                evidenceCapture.recordBuyInputs(effectiveAnalysis.trend, effectiveAnalysis.signalPowerCall)
                motivoVetoPorAgotamientoDeCompra(
                    action, effectiveAnalysis.trend, effectiveAnalysis.signalPowerCall
                )
            }
        )
        if (policyResult is ExecutionPolicyResult.Vetoed) {
            if (policyResult.incrementVetoActivoDesajustado) vetoActivoDesajustado++
            eventSink.add(TradeEventLog.Kind.VETO, policyResult.message, decisionId = currentDecisionId)
            policyResult.secondaryEventKind?.let {
                eventSink.add(it, policyResult.secondaryEventMessage.orEmpty(), decisionId = currentDecisionId)
            }
            policyResult.disableModeReason?.let {
                setTradingMode(AutoTradeMode.DISABLED, it)
                sniperShutdownReason = policyResult.message.removePrefix("RISK_LIMIT ")
            }
            policyResult.toastMessage?.let { message ->
                handler.post {
                    Toast.makeText(context, message, if (policyResult.isLongToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    if (policyResult.disableModeReason != null) hudPort?.updateHUDView()
                }
            }
            return
        }
        val allowed = policyResult as ExecutionPolicyResult.Allowed
        val finalAction = allowed.finalAction
        val finalReason = allowed.finalReason
        val headlessConfidence = allowed.headlessConfidence
        val exactPolicyContextJson = if (mode == AutoTradeMode.SHADOW) {
            evidenceCapture.exactJsonOrNull(executionContext)
        } else null

        val (screenW, screenH) = CalibrationManager.getRealScreenDimensions(context)
        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || screenW > screenH
        val (x, y) = tapCoordinateResolver.resolve(finalAction, isLand, screenW, screenH)

        // Ronda 21 (Frente E): SHADOW MODE FORMAL
        // Si el motor está en SHADOW, todas las defensas y cálculos corren exactamente igual,
        // pero NUNCA se despacha un tap al broker ni se altera el saldo ni el W/L real.
        if (mode == AutoTradeMode.SHADOW) {
            val currentPrice = latestMarketTick?.price ?: 0.0
            if (shadowSimulationEnabled) {
                val tick = latestMarketTick ?: return
                shadowSimulation.open(OrderRequest(finalAction, x, y, currentDecisionId, "sim-$currentDecisionId", mode = mode),
                    tick, com.example.tradedraw.simulation.SimMetadata(
                        strategy = strategy.name,
                        submode = autonomousSubMode.name,
                        confidence = headlessConfidence,
                        trend = syntheticCandleEngine.detectedTrend.name,
                        reason = finalReason,
                        strategyVersion = "r29c-instrumented-policy-v1",
                        policyContextJson = exactPolicyContextJson
                    )) { receipt ->
                    if (receipt is OrderReceipt.Success) {
                        shadowOrdersGenerated++
                        synchronized(this) { candleRateLimiter.recordExecution(currentCandleEpochMinute, mode) }
                    } else eventSink.add("SIMULATION_ERROR", receipt.toString(), decisionId = currentDecisionId)
                }
                return
            }
            shadowOrdersGenerated++
            Log.i("TradingEngine", "👻 [SHADOW MODE] Orden simulada autorizada: $finalAction @ $currentPrice (segundo=$sec, decisionId=$currentDecisionId) - Cero taps, saldo intacto")
            eventSink.add(
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
                candleRateLimiter.recordExecution(currentCandleEpochMinute, mode)
            }
            return
        }

        val currentTradeId = java.util.UUID.randomUUID().toString()
        // H9: telemetría del segundo REAL de entrada (no el de liquidación del journal). Permite
        // correlacionar el segundo de disparo con el resultado, la métrica que el journal no captura.
        Log.i("TradingEngine", "🎯 ENTRADA despachada | ${finalAction} | segundo=$sec | estrategia=${strategy.name} | decisionId=$currentDecisionId | tradeId=$currentTradeId | $finalReason")
        dispatchBrokerOrder(finalAction, x, y, currentDecisionId, currentTradeId,
            headlessConfidence, finalReason) { receipt ->
            if (receipt !is OrderReceipt.Success) return@dispatchBrokerOrder
            synchronized(this) {
                candleRateLimiter.recordExecution(currentCandleEpochMinute, mode)
            }
            adaptiveLearningEngine.recordTradeOpened(
                action = finalAction, analysis = effectiveAnalysis,
                tick = latestMarketTick, strategyName = strategy.name)

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
    }

    /**
     * Ronda 12 (B1): ventana en la que se exige que el broker confirme la orden.
     *
     * Con stake de opciones binarias el saldo se debita al ABRIR, asi que un movimiento del
     * saldo es prueba de que la orden existe. Si pasada la ventana el saldo sigue identico al
     * de apertura, la orden NO se registro: es una operacion fantasma, no un empate.
     */
    private fun checkOrderConfirmation() {
        val coordinator = orderGateway as? com.example.tradedraw.core.SingleFlightOrderGateway
        if (coordinator?.isRecovered() == true || riskManager.requiresBrokerReconciliation) return
        val snapshot = synchronized(riskManager) {
            if (!riskManager.hasPendingTrade || riskManager.orderState != OrderState.ENVIADA) null
            else Triple(riskManager.pendingTradeStartTime, riskManager.pendingTradeBaseBalance, riskManager.pendingTradeAction)
        } ?: return

        val (startMs, baseBalance, action) = snapshot
        if (startMs <= 0L) return
        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000

        val currentBal = brokerRuntime.refreshBalance()

        if (!brokerRuntime.demoAccountGuard.isDemoConfirmedFresh(10_000L)) return
        if (riskManager.isOrderConfirmed(baseBalance, currentBal, elapsedSec)) {
            riskManager.markOrderConfirmed()
            Log.i(
                "TradingEngine",
                "✅ ORDEN CONFIRMADA por movimiento de saldo | $action | base=$baseBalance ahora=$currentBal (${elapsedSec}s)"
            )
            handler.post { hudPort?.updateHUDView() }
            return
        }

        if (riskManager.isOrderUnprocessed(baseBalance, currentBal, elapsedSec)) {
            coordinator?.markUncertain("BROKER_CONFIRMATION_TIMEOUT")
            coordinator?.reservation()?.let { riskManager.holdForBrokerReconciliation(it) }
            eventSink.add("DISPATCH_RECOVERY", "BROKER_CONFIRMATION_TIMEOUT requires=broker_history_review",
                riskManager.pendingTradeDecisionId, riskManager.pendingTradeTradeId)
        }
    }

    private fun checkHeadlessTradeResolution() {
        checkOrderConfirmation()
        if (riskManager.requiresBrokerReconciliation) return
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
            brokerRuntime.read().isAvailable,
            brokerRuntime.read().lastReadMs,
            System.currentTimeMillis()
        )
        val currentBal = brokerRuntime.refreshBalance()

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
            // R24/B1.a: clave capturada ANTES del `post`, igual que en la ruta de vision. El
            // pendiente puede limpiarse dentro del hilo principal y perder el tradeId.
            val settleTradeId = snapshot.tradeId
            val settleStartMs = snapshot.startTime
            // R24/B1.b: LOSS resuelto sin saldo legible = sin evidencia de saldo.
            val lossSinEvidencia = !finalWin && !isTie && !(baseBalance > 0.0 && currentBal > 0.0)

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
                    hudPort?.updateHUDView()
                } else if (finalWin) {
                    if (riskManager.resolvePendingTradeWin()) {
                        adaptiveLearningEngine.recordTradeOutcome(true, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] 🎉 GANADA (+1 W) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, true)
                    } else {
                        riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = true)
                        adaptiveLearningEngine.recordTradeOutcome(true, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] 🎉 GANADA (+1 W) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, true)
                    }
                } else {
                    if (riskManager.resolvePendingTradeLoss()) {
                        if (lossSinEvidencia) riskManager.recordLossSinEvidencia()
                        adaptiveLearningEngine.recordTradeOutcome(false, context)
                        emitHapticAndAudioFeedback()
                        Toast.makeText(context, "[HEADLESS] ⚠️ PERDIDA (+1 L) [$method]", Toast.LENGTH_SHORT).show()
                        onTradeExecutedListener?.invoke(pendingAction, false)
                    } else {
                        val counted = riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = false)
                        if (counted && lossSinEvidencia) riskManager.recordLossSinEvidencia()
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

/**
 * R23E (REQ 1): extrae la similitud porcentual del `reason` de un `AdaptiveDecision.Block`.
 *
 * POR QUE EXISTE. El veto `ADAPTIVE_BLOCK` es el guard que concentra el 96.8% de los vetos de la
 * campana R23D, y hasta R23D su `detail` solo llevaba el texto libre de la razon. El pipeline
 * contrafactual (Fase 3) necesita `similarity` como NUMERO para poder segmentar por buckets
 * preregistrados (90-93%, 93-96%, 96-98%, 98-100%), asi que la similitud tiene que viajar de
 * forma parseable y no embebida en prosa.
 *
 * Acepta las dos formas que emite `AdaptiveLearningEngine`: la del anti-patron por similitud
 * (`... (98% similitud en contexto S/R=41%)`) y la de las ramas de continuacion/reversion, que no
 * miden similitud. Devuelve `None` en ese segundo caso: un `0` seria indistinguible de "similitud
 * medida y nula", que es justo el dato que no existe.
 */
internal fun extractAdaptiveSimPct(reason: String): Int? {
    if (reason.isBlank()) return null
    val m = Regex("""(\d{1,3})\s*%\s*similitud""").find(reason)
    return m?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * R23E (REQ 1): compone el `detail` del evento de veto `ADAPTIVE_BLOCK`.
 *
 * Formato: `ADAPTIVE_BLOCK [sim:98%] [Confluencia: 85pts] <razon>`
 *
 * `sim` y `[Confluencia: Npts]` son OPCIONALES y solo aparecen cuando el dato existe de verdad:
 * - La confluencia llega en la `reason` de la senal, que `SyntheticCandleEngine.dispatchSniperSignal`
 *   enriquece al final del pipeline con `[Confluencia: Npts]`. Se propaga aqui para que el score
 *   REAL llegue al log de eventos (en R23D el monitor no tenia de donde sacarlo: ver el hallazgo
 *   de la Fase 1 de R23D, donde las 529 decisiones salian sin score).
 * - Cuando no hay marcador, el prefijo se omite en vez de escribir un valor por defecto. Un
 *   `[Confluencia: 0pts]` inventado envenenaria la segmentacion por score del preregistro.
 */
internal fun buildAdaptiveBlockDetail(reason: String, signalReason: String): String {
    val sb = StringBuilder("ADAPTIVE_BLOCK")
    val sim = extractAdaptiveSimPct(reason)
    if (sim != null) sb.append(" [sim:").append(sim).append("%]")
    val m = Regex("""\[Confluencia:\s*(\d+(?:\.\d+)?)pts\]""").find(signalReason)
    if (m != null) sb.append(" [Confluencia: ").append(m.groupValues[1]).append("pts]")
    sb.append(" ").append(reason)
    return sb.toString()
}

/**
 * R23H Fase 2: calcula el par (callPower, putPower) del termometro de mercado.
 *
 * Extraido de `buildAnalysisFromSyntheticEngine` SIN cambiar una sola constante: mismos valores
 * base, mismos incrementos, mismos umbrales, mismo `coerceIn` y misma normalizacion a 100. La
 * formula es la MISMA que decide en vivo; sacarla a funcion pura solo permite que el arnes de
 * replay (`ReplayBacktestTest`) la invoque para reproducir el veto por agotamiento con identico
 * `callPower`. Duplicar el calculo en el test habria garantizado divergencia silenciosa al primer
 * ajuste de estrategia.
 *
 * CONGELADO: esta funcion NO es un punto de calibracion. Cualquier cambio de valores aqui altera la
 * estrategia y queda fuera del alcance de una correccion de infraestructura.
 */
internal fun calcularPoderesDeMercado(
    trend: TrendDirection, distSupportRatio: Float, distResistanceRatio: Float,
    rsi: Double, isBullishImpulse: Boolean, isBearishImpulse: Boolean
): Pair<Int, Int> = StrategyReplayPort.calcularPoderesDeMercado(trend, distSupportRatio, distResistanceRatio, rsi, isBullishImpulse, isBearishImpulse)

/**
 * R23H Fase 1: decide si una compra debe vetarse por AGOTAMIENTO DE CLIMAX en techo.
 *
 * HIPOTESIS QUE IMPLEMENTA (no optimizacion validada). `workflows/R23H_BUY_FEATURE_ANALYSIS.md`
 * §5 aisla `BUY + UPTREND + callPower >= 70` como el unico bucket de compra con poder
 * discriminante en la muestra R23G reconciliada: 6 perdidas evitadas por 1 victoria cedida. El
 * fundamento microestructural es que un `callPower` extremo en pleno UPTREND mide liquidez
 * compradora agotandose en resistencia, no fuerza para continuar. Es la unica hipotesis de la fase
 * y se mide en vivo; el umbral queda congelado en [TradingEngine.VETO_BUY_UPTREND_MAX_CALL_POWER].
 *
 * POR QUE ES UNA FUNCION PURA Y NO UN `if` INLINE. El veto tiene que poder auditarse sin levantar
 * el motor entero (que exige `Context`, `Handler`, `RiskManager` y el servicio de accesibilidad).
 * Aislado aqui, el contrato completo — dispara en UPTREND+>=70, NO dispara por debajo, NO dispara
 * en DOWNTREND/SIDEWAYS, NO toca SELL — se verifica en tests JVM puros.
 *
 * ALCANCE DELIBERADAMENTE MINIMO. Solo mira `BUY`. SELL, `ADAPTIVE_BLOCK` (0.99f), stake y payout
 * quedan fuera: no se leen ni se escriben. Este veto se evalua ANTES del motor adaptativo para que
 * un bloqueo por agotamiento no consuma ni contamine la firma de aprendizaje.
 *
 * @return el `detail` del evento de veto si procede vetar, o `null` si la orden sigue adelante.
 */
internal fun motivoVetoPorAgotamientoDeCompra(action: TradeAction, trend: TrendDirection, callPower: Int): String? =
    StrategyReplayPort.motivoVetoPorAgotamientoDeCompra(action, trend, callPower)
