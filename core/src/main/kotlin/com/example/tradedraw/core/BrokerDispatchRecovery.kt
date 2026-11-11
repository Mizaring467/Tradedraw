package com.example.tradedraw.core

data class BrokerRecoveryContext(val createdAtMs: Long, val baseBalance: Double, val entryPrice: Double,
    val confidence: Float, val reason: String)
data class DispatchReservation(val requestId: String, val decisionId: String = "", val action: TradeAction = TradeAction.BUY,
    val context: BrokerRecoveryContext? = null)
enum class BrokerRecoveredOutcome { CLOSED_WIN, CLOSED_LOSS, CLOSED_TIE, NOT_PLACED }
/** Explicit operator attestation from broker history; never inferred from a timeout or missing tick. */
data class BrokerDispatchEvidence(val requestId: String, val brokerReference: String, val outcome: BrokerRecoveredOutcome,
    val observedAtMs: Long, val source: String = "MANUAL_BROKER_HISTORY")
