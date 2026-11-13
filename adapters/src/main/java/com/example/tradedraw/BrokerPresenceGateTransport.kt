package com.example.tradedraw

import com.example.tradedraw.core.OrderGateway
import com.example.tradedraw.core.OrderReceipt
import com.example.tradedraw.core.OrderRequest

/**
 * Ronda 2 (E→F) — PREFLIGHT fail-closed de presencia del broker, SEPARADO de la cascada
 * congelada de 13 vetos (que queda byte-idéntica; este decorador ni la lee ni la altera).
 *
 * MOTIVACIÓN (repro 2026-11-13 04:30–04:35 UTC): motor armado AUTONOMOUS con
 * `com.marketly.trading` ausente; a las 04:34:01 se despachó por GESTURE y el gesto se
 * inyectó sin app que lo procesara → ciclo fantasma. Los guards preexistentes validan el
 * FEED de TradeDraw (WS nativo, este proceso), no el proceso/ventana del broker destino.
 *
 * ALCANCE EXACTO: SOLO transportes que tocan la UI del broker (`GESTURE`, `ROOT`).
 * SHADOW/simulación y transportes no-UI NO se ven afectados. Sin retry automático: tras
 * un veto la orden queda TERMINAL no despachada (coherente con «sin retry de entrega
 * incierta»); un ciclo futuro evalúa de nuevo.
 *
 * UBICACIÓN: vive en la COMPOSICIÓN Android porque `DefaultOrderGateway.kt` está
 * byte-congelado en loops/r29c/evidence/strategy_registry_v3.json — cualquier cambio allí
 * rompe el oráculo congelado. Envolver la composición añade el veto sin tocar un byte del
 * archivo congelado (verificado: el hash del registry sigue cuadrando).
 */
class BrokerPresenceGateTransport(
    private val delegate: OrderGateway,
    private val presenceSensor: BrokerProcessPresenceSensor,
    private val eventSink: com.example.tradedraw.core.EventSink =
        com.example.tradedraw.core.NoOpEventSink()
) : OrderGateway {

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val snapshot = presenceSensor.snapshot()
        if (snapshot.fresh) {
            delegate.dispatch(request, onResult)
            return
        }
        // Veto fail-closed: NADA llega al transporte. Evento con timestamp + decisión id,
        // luego receipt terminal; el SingleFlight que envuelve a este gateway limpia la
        // reserva pendiente al ver un Vetoed.
        eventSink.add(
            TradeEventLog.Kind.VETO,
            "$REASON sensor=${snapshot.reason} pkg=${snapshot.pkg} transport=${delegate::class.simpleName}",
            decisionId = request.decisionId,
            tradeId = request.tradeId
        )
        onResult(OrderReceipt.Vetoed(REASON))
    }

    companion object {
        /** Reason nuevo, ortogonal a los 13 índices de la cascada congelada. */
        const val REASON = "BROKER_APP_ABSENT"
    }
}
