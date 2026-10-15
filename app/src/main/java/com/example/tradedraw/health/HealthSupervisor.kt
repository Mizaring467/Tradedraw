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
 */
class HealthSupervisor {

    enum class State { READY, DEGRADED, BLOCKED }

    data class Report(
        val state: State,
        val reasons: List<String>,
        val sinceMs: Long
    ) {
        fun toJson(): String {
            val reasonsJson = reasons.joinToString(",") { "\"${it.replace("\"", "'")}\"" }
            return """{"state":"${state.name}","reasons":[$reasonsJson],"sinceMs":$sinceMs}"""
        }
    }

    @Volatile
    private var currentState: State? = null

    @Volatile
    private var stateSinceMs: Long = 0L

    /**
     * Evalúa el estado agregado del sistema a partir de las fuentes de verdad existentes.
     */
    @Synchronized
    fun evaluate(
        tradingEngine: TradingEngine?,
        riskManager: RiskManager?,
        wsClient: BinomoWebSocketClient?,
        accessibilityService: AutoTradeAccessibilityService? = AutoTradeAccessibilityService.instance,
        nowMs: Long = System.currentTimeMillis()
    ): Report {
        val blockedReasons = mutableListOf<String>()
        val degradedReasons = mutableListOf<String>()

        // 1. Accesibilidad (crítica para clicks y lectura de UI)
        if (accessibilityService == null) {
            blockedReasons.add("ACCESSIBILITY_DOWN")
        }

        // 2. WebSocket y Feed
        if (wsClient == null || !wsClient.isConnected) {
            blockedReasons.add("WS_DISCONNECTED")
        } else {
            val feedFresh = wsClient.isFeedFresh()
            if (!feedFresh) {
                blockedReasons.add("FEED_STALE")
            }
            if (wsClient.socketTicks == 0L) {
                blockedReasons.add("NO_TICKS")
            }
            if (wsClient.isPriceFrozen()) {
                blockedReasons.add("PRICE_FROZEN")
            }
            if (wsClient.activoSinFeed.isNotBlank()) {
                blockedReasons.add("ACTIVO_SIN_FEED")
            }
        }

        // 3. Mismatch pantalla <-> feed
        val pantalla = AutoTradeAccessibilityService.latestObservedAsset.trim()
        val feed = wsClient?.activeAsset?.trim() ?: ""
        val mismatch = TradingEngine.motivoVetoPorDesajusteDeActivo(pantalla, feed)
        if (mismatch != null) {
            blockedReasons.add("SCREEN_FEED_MISMATCH: $mismatch")
        }

        // 4. Saldo
        val balance = AutoTradeAccessibilityService.latestObservedBalance
        if (balance <= 0.0) {
            blockedReasons.add("BALANCE_UNREADABLE")
        }

        // 5. Orden pendiente
        if (riskManager?.hasPendingTrade == true) {
            blockedReasons.add("ORDER_PENDING")
        }

        // 6. Condiciones degradadas (no bloquean por completo pero indican estado subóptimo)
        if (AutoTradeAccessibilityService.observedPayout.discrepant) {
            degradedReasons.add("PAYOUT_DISCREPANT")
        }
        if (tradingEngine?.inoperabilityDetector?.isInoperable == true) {
            degradedReasons.add("MARKET_INOPERABLE")
        }
        if (riskManager != null && riskManager.getRemainingCooldown() > 0) {
            degradedReasons.add("COOLDOWN_ACTIVE: ${riskManager.getRemainingCooldown()}s")
        }
        val isBalanceFresh = AutoTradeAccessibilityService.lastBalanceReadMs > 0L &&
                (nowMs - AutoTradeAccessibilityService.lastBalanceReadMs) <= 5000L
        if (balance > 0.0 && !isBalanceFresh) {
            degradedReasons.add("BALANCE_NOT_FRESH")
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
            sinceMs = stateSinceMs
        )
    }

    /** Reset para pruebas unitarias deterministas. */
    fun resetForTests(initialState: State? = null, initialSinceMs: Long = 0L) {
        currentState = initialState
        stateSinceMs = initialSinceMs
    }
}
