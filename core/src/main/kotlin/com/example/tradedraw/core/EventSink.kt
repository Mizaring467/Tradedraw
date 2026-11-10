package com.example.tradedraw.core

/**
 * Puerto de Observabilidad P0: Sumidero de eventos en memoria.
 * Desacopla a los productores de eventos de la implementación concreta [TradeEventLog].
 */
interface EventSink {
    fun add(
        kind: String,
        detail: String,
        decisionId: String? = null,
        tradeId: String? = null
    )

    fun add(
        kind: Any,
        detail: String,
        decisionId: String? = null,
        tradeId: String? = null
    ) = add(kind.toString(), detail, decisionId, tradeId)
}
