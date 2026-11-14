package com.example.tradedraw

import com.example.tradedraw.core.*

/**
 * Ronda 2 (E→F): composición Android con PREFLIGHT fail-closed de presencia del broker.
 *
 * La cascada congelada (ExecutionPolicy —13 vetos— y el cuerpo de DefaultOrderGateway)
 * NO se modifica: aquí solo se ENVUELVE el gateway compuesto con
 * [BrokerPresenceGateTransport], que veta con `BROKER_APP_ABSENT` cuando no hay ventana
 * de broker fresca ANTES de que ningún transporte toque la UI del broker.
 */
fun createAndroidOrderGateway(hudPort: HudPort?, events: EventSink = RingBufferEventSink()): OrderGateway {
    val inner = DefaultOrderGateway(
        demoAccountGuard = AutoTradeAccessibilityService.demoAccountGuard,
        eventSink = events,
        rootTransport = RootTapTransport(events, hudPort,
            rootCheck = { AutoTradeAccessibilityService.hasRootAccess() },
            rootTapExecutor = { x, y, did, tid, callback, bypass ->
                AutoTradeAccessibilityService.executeRootTap(x, y, did, tid, callback, bypass, orderAuthorized = true)
            }),
        gestureTransport = GestureTapTransport(events, instanceSupplier = {
            AutoTradeAccessibilityService.instance?.let { service ->
                object : OrderGestureDriver {
                    override fun performOrderClickAt(x: Float, y: Float, callback: (Boolean, String?) -> Unit) {
                        service.performOrderClickAt(x, y, callback)
                    }
                }
            }
        })
    )
    // Preflight de presencia: GESTURE/ROOT tocan la UI del broker; sin ventana fresca del
    // broker no hay despacho (fail-closed). SHADOW/sim y transportes no-UI no pasan por aquí.
    return BrokerPresenceGateTransport(inner, BrokerProcessPresenceSensor.fromDetector, events)
}
