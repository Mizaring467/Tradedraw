package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.execution.ExecutionPolicy
import com.example.tradedraw.execution.ExecutionPolicyHooks

/** Android adapters are assembled here; execution never looks up application singletons. */
fun createAndroidOrderGateway(hudPort: HudPort?, events: EventSink = RingBufferEventSink()): DefaultOrderGateway =
    DefaultOrderGateway(
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

/** Frozen predicates remain single implementations; mandatory hooks prevent an unconfigured policy. */
fun createExecutionPolicy() = ExecutionPolicy(object : ExecutionPolicyHooks {
    override fun rangeThreshold(asset: String) = TradingEngine.rangoRelativoMinimoParaActivo(asset)
    override fun assetMismatch(activoPantallaVisible: String, activoFeed: String) = TradingEngine.motivoVetoPorDesajusteDeActivo(activoPantallaVisible, activoFeed)
    override fun adaptiveDetail(reason: String, signal: String) = buildAdaptiveBlockDetail(reason, signal)
    override fun buyExhaustion(action: TradeAction, trend: TrendDirection, power: Int) = motivoVetoPorAgotamientoDeCompra(action, trend, power)
    override fun actionConflict(srStatus: String, actionPlan: String) = HudCoherence.actionConflict(srStatus, actionPlan)
})
