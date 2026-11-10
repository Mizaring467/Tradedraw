package com.example.tradedraw.health

import com.example.tradedraw.AutoTradeAccessibilityService
import com.example.tradedraw.BinomoWebSocketClient
import com.example.tradedraw.RiskManager
import com.example.tradedraw.TradingEngine

/**
 * Ronda 21 (Frente D): Health Supervisor Unificado.
 *
 * Agrega el estado técnico existente del sistema en tres niveles:
 *  - READY: Invariantes técnicos mínimos satisfechos para análisis y ejecución segura.
 *  - DEGRADED: Problemas observables que no invalidan necesariamente el análisis (ej. cooldown, payout discrepante, mercado inoperable).
 *  - BLOCKED: Condición crítica que impide terminantemente operar (feed rancio, mismatch pantalla/feed, accesibilidad caída, saldo no legible, precio congelado, etc.).
 *
 * REGLAS FUNDAMENTALES:
 *  1. HealthSupervisor NO decide BUY/SELL.
 *  2. Solo agrega estado técnico existente; NO duplica guards ni crea fuentes de verdad paralelas.
 *
 * RONDA 27 — correcciones de falsos positivos (ver `loops/r27_validation/R27_CAUSAS_RAIZ.md`):
 *  El supervisor es CONSUMIDO SOLO POR DIAGNÓSTICO (`GET /status`). Ningún camino de trading lo
 *  consulta — verificado con `grep`: solo `OverlayService.kt:114` y `TradeDrawHttpBridge.kt:619`.
 *  Por eso estas correcciones no pueden habilitar una operación: acotan el diagnóstico a lo que
 *  el modo de datos realmente permite observar. Los guards que sí vetan viven en `TradingEngine`
 *  y `RiskManager`, y NO se tocan.
 *
 *  A) SCREEN_FEED_MISMATCH solo puede evaluarse si EXISTE una fuente de pantalla. En modo
 *     WebSocket puro la captura está deshabilitada por decisión de producto, así que comparar
 *     pantalla contra feed reportaba desajuste de forma estructural y permanente. Se marca como
 *     NO EVALUABLE en lugar de inventar una pantalla o relajar el guard del motor.
 *
 *  C) El balance distingue ausente / no disponible / inválido / obsoleto. Un dato correcto leído
 *     hace un instante ya no se reporta como rancio.
 *
 * DISEÑO: la decisión vive en [evaluateFromSnapshot], función **pura** y por tanto testeable sin
 * Android. [evaluate] solo toma la foto de las fuentes reales y delega. Antes la lógica no se
 * podía probar porque exigía un `BinomoWebSocketClient` (que necesita `Context`), así que los
 * tests existentes nunca llegaron a ejercitar `READY` ni `DEGRADED`.
 */
class HealthSupervisor {

    enum class State { READY, DEGRADED, BLOCKED }

    /**
     * R27: de dónde salen los datos de mercado. El diagnóstico debe ser coherente con el modo
     * configurado: en [WEBSOCKET_PURO] no hay captura de pantalla, así que las señales que
     * dependen de ver la pantalla no son evaluables.
     */
    enum class DataSource(val etiqueta: String) {
        /** Feed WebSocket + captura de pantalla activa (modo histórico). */
        WEBSOCKET_CON_PANTALLA("WebSocket + captura"),

        /** Solo feed WebSocket. La captura de pantalla está deshabilitada (modo vigente). */
        WEBSOCKET_PURO("WebSocket puro"),

        /** Solo captura de pantalla, sin feed. */
        SOLO_PANTALLA("Solo captura");

        val hayPantalla: Boolean get() = this != WEBSOCKET_PURO
    }

    /**
     * Foto inmutable de todas las señales que el supervisor necesita.
     *
     * Existe para que la máquina de estados se pueda probar sin montar Android. Los valores por
     * defecto describen un sistema sano, de modo que cada test declara solo la señal que investiga.
     */
    data class Snapshot(
        val accesibilidadEnlazada: Boolean = true,
        val wsConnected: Boolean = true,
        val feedFresh: Boolean = true,
        val socketTicks: Long = 1L,
        val priceFrozen: Boolean = false,
        val activoSinFeed: String = "",
        val activeAsset: String = "Z-CRY/IDX",
        val latestObservedAsset: String = "",
        val balance: Double = 0.0,
        val lastBalanceReadMs: Long = 0L,
        val hasPendingTrade: Boolean = false,
        val payoutDiscrepant: Boolean = false,
        val marketInoperable: Boolean = false,
        val cooldownSec: Int = 0
    )

    data class Report(
        val state: State,
        val reasons: List<String>,
        val sinceMs: Long,
        val dataSource: DataSource = DataSource.WEBSOCKET_PURO,
        /** Señales que el modo actual no permite evaluar. Vacío en el caso normal. */
        val notEvaluable: List<String> = emptyList()
    ) {
        fun toJson(): String {
            val reasonsJson = reasons.joinToString(",") { "\"${it.replace("\"", "'")}\"" }
            val notEvalJson = notEvaluable.joinToString(",") { "\"${it.replace("\"", "'")}\"" }
            return """{"state":"${state.name}","reasons":[$reasonsJson],"sinceMs":$sinceMs,""" +
                """"dataSource":"${dataSource.name}","notEvaluable":[$notEvalJson]}"""
        }
    }

    @Volatile
    private var currentState: State? = null

    @Volatile
    private var stateSinceMs: Long = 0L

    /**
     * Evalúa el estado agregado del sistema a partir de las fuentes de verdad existentes.
     *
     * @param dataSource modo de datos real. Por defecto WebSocket puro, que es el único modo que
     *   el producto arranca hoy (`OverlayService.kt:237` fuerza `hasMediaProjection = false`).
     */
    @Synchronized
    fun evaluate(
        tradingEngine: TradingEngine?,
        riskManager: RiskManager?,
        wsClient: BinomoWebSocketClient?,
        accessibilityService: AutoTradeAccessibilityService? = AutoTradeAccessibilityService.instance,
        dataSource: DataSource = DataSource.WEBSOCKET_PURO,
        nowMs: Long = System.currentTimeMillis()
    ): Report {
        val snapshot = Snapshot(
            accesibilidadEnlazada = accessibilityService != null,
            wsConnected = wsClient?.isConnected == true,
            feedFresh = wsClient?.isFeedFresh() == true,
            socketTicks = wsClient?.socketTicks ?: 0L,
            priceFrozen = wsClient?.isPriceFrozen() == true,
            activoSinFeed = wsClient?.activoSinFeed ?: "",
            activeAsset = wsClient?.activeAsset?.trim() ?: "",
            latestObservedAsset = AutoTradeAccessibilityService.latestObservedAsset.trim(),
            balance = AutoTradeAccessibilityService.latestObservedBalance,
            lastBalanceReadMs = AutoTradeAccessibilityService.lastBalanceReadMs,
            hasPendingTrade = riskManager?.hasPendingTrade == true,
            payoutDiscrepant = AutoTradeAccessibilityService.observedPayout.discrepant,
            marketInoperable = tradingEngine?.inoperabilityDetector?.isInoperable == true,
            // R23D Fase 1 (REQ 3): se consulta el submodo EFECTIVO del RiskManager (espejo del
            // motor), no la llamada sin argumento. El no-arg usa el fallback `currentSubMode`,
            // que si no se sincroniza queda en CONSERVATIVE y reporta el cooldown de perdidas de
            // 180s en YOLO. R27 conserva esta forma literal: `CooldownSubmodeSyncTest` la vigila.
            cooldownSec = if (riskManager != null) {
                riskManager.getRemainingCooldown(riskManager.currentSubMode)
            } else 0
        )
        return evaluateFromSnapshot(snapshot, dataSource, nowMs)
    }

    /** Máquina de estados pura. Sin Android, sin I/O, sin efectos. */
    @Synchronized
    fun evaluateFromSnapshot(
        snapshot: Snapshot,
        dataSource: DataSource = DataSource.WEBSOCKET_PURO,
        nowMs: Long = System.currentTimeMillis()
    ): Report {
        val blockedReasons = mutableListOf<String>()
        val degradedReasons = mutableListOf<String>()
        val notEvaluable = mutableListOf<String>()

        // 1. Accesibilidad (crítica para clicks y lectura de UI)
        if (!snapshot.accesibilidadEnlazada) {
            blockedReasons.add("ACCESSIBILITY_DOWN")
        }

        // 2. WebSocket y Feed
        if (!snapshot.wsConnected) {
            blockedReasons.add("WS_DISCONNECTED")
        } else {
            if (!snapshot.feedFresh) blockedReasons.add("FEED_STALE")
            if (snapshot.socketTicks == 0L) blockedReasons.add("NO_TICKS")
            if (snapshot.priceFrozen) blockedReasons.add("PRICE_FROZEN")
            if (snapshot.activoSinFeed.isNotBlank()) blockedReasons.add("ACTIVO_SIN_FEED")
        }

        // 3. Mismatch pantalla <-> feed
        //    R27 (A): solo evaluable si hay fuente de pantalla Y feed conectado. En WebSocket
        //    puro comparar no mide nada: falta la mitad "pantalla" del contrato.
        if (!dataSource.hayPantalla || !snapshot.wsConnected) {
            notEvaluable.add("SCREEN_FEED_MISMATCH")
        } else {
            val mismatch = TradingEngine.motivoVetoPorDesajusteDeActivo(
                snapshot.latestObservedAsset,
                snapshot.activeAsset
            )
            if (mismatch != null) blockedReasons.add("SCREEN_FEED_MISMATCH: $mismatch")
        }

        // 4. Saldo — R27 (C): cuatro casos distintos, no uno.
        //    Sin accesibilidad no hay fuente de balance, y eso ya lo reporta ACCESSIBILITY_DOWN;
        //    duplicarlo como "ilegible" haría creer que el dato existe y está mal.
        if (snapshot.accesibilidadEnlazada) {
            val motivo = when {
                snapshot.balance > 0.0 -> null
                snapshot.lastBalanceReadMs <= 0L -> "AUSENTE"
                else -> "INVALIDO"
            }
            if (motivo != null) blockedReasons.add("BALANCE_UNREADABLE: $motivo")
        }

        // 5. Orden pendiente
        if (snapshot.hasPendingTrade) blockedReasons.add("ORDER_PENDING")

        // 6. Condiciones degradadas (no bloquean por completo pero indican estado subóptimo)
        if (snapshot.payoutDiscrepant) degradedReasons.add("PAYOUT_DISCREPANT")
        if (snapshot.marketInoperable) degradedReasons.add("MARKET_INOPERABLE")
        if (snapshot.cooldownSec > 0) degradedReasons.add("COOLDOWN_ACTIVE: ${snapshot.cooldownSec}s")

        // R27 (C.2): frescura con margen sobre la cadencia real de escaneo pasivo (5000 ms).
        // Con umbral == cadencia el chequeo fallaba por construcción. Es holgura de medición, no
        // relajación del guard: el guard de trading usa `TradingEngine.isBalanceFresh` (3000 ms)
        // y sigue intacto.
        val balanceFresco = snapshot.lastBalanceReadMs > 0L &&
            (nowMs - snapshot.lastBalanceReadMs) <= BALANCE_FRESH_WINDOW_MS
        if (snapshot.balance > 0.0 && !balanceFresco) {
            degradedReasons.add("BALANCE_STALE")
        }

        val evaluatedState = when {
            blockedReasons.isNotEmpty() -> State.BLOCKED
            degradedReasons.isNotEmpty() -> State.DEGRADED
            else -> State.READY
        }

        val finalReasons = if (evaluatedState == State.BLOCKED) blockedReasons else degradedReasons

        if (evaluatedState != currentState || stateSinceMs == 0L) {
            currentState = evaluatedState
            stateSinceMs = nowMs
        }

        return Report(
            state = evaluatedState,
            reasons = finalReasons,
            sinceMs = stateSinceMs,
            dataSource = dataSource,
            notEvaluable = notEvaluable
        )
    }

    companion object {
        /**
         * Ventana de frescura del balance para diagnóstico.
         *
         * Cadencia real de la ruta pasiva: 5000 ms (`AutoTradeAccessibilityService`,
         * `lastBalanceScanTime`). Se añade un ciclo de margen (×2) para que un dato leído dentro
         * de la última ventana de escaneo no se reporte como rancio por milisegundos.
         *
         * NO confundir con `TradingEngine.isBalanceFresh` (3000 ms), que decide si un TIE puede
         * atribuirse con certeza. Son preguntas distintas y por eso son constantes distintas.
         */
        const val BALANCE_FRESH_WINDOW_MS = 10_000L

        /** Cadencia del escaneo pasivo de balance, espejo del throttle de los eventos. */
        const val BALANCE_SCAN_CADENCE_MS = 5_000L
    }

    /** Reset para pruebas unitarias deterministas. */
    fun resetForTests(initialState: State? = null, initialSinceMs: Long = 0L) {
        currentState = initialState
        stateSinceMs = initialSinceMs
    }
}
