package com.example.tradedraw

import com.example.tradedraw.core.*

/**
 * Implementación canónica de OrderGateway con salvaguarda DEMO positiva y fresca,
 * bloqueo por modo (DISABLED / SHADOW), y cascada de 3 transportes (Root -> Gesto -> Bridge/Fallback).
 */
class DefaultOrderGateway(
    val demoAccountGuard: DemoAccountGuard = StandardDemoAccountGuard(),
    val eventSink: EventSink = RingBufferEventSink(),
    val rootTransport: TapTransport = RootTapTransport(eventSink),
    val gestureTransport: TapTransport = GestureTapTransport(eventSink),
    val bridgeTransport: TapTransport = BridgeDirectTransport(eventSink)
) : OrderGateway {

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        // 1. Guard de modo: DISABLED y SHADOW nunca despachan taps al broker
        if (request.mode == AutoTradeMode.DISABLED || request.mode == AutoTradeMode.SHADOW) {
            val msg = "TRADE_DISPATCH_REJECTED_MODE_${request.mode}"
            eventSink.add(
                TradeEventLog.Kind.VETO,
                "TRADE_ENDPOINT_MODE_REJECTED mode=${request.mode} action=${request.action}",
                decisionId = request.decisionId,
                tradeId = request.tradeId
            )
            onResult(OrderReceipt.Vetoed(msg))
            return
        }

        // 2. Guard de confirmación DEMO positiva y fresca
        if (!demoAccountGuard.isDispatchAllowed()) {
            val reason = if (demoAccountGuard.getAccountType() == AccountType.REAL) {
                "SEGURIDAD_CLICK_NO_DEMO"
            } else {
                "SEGURIDAD_CLICK_NO_DEMO_NOT_CONFIRMED"
            }
            eventSink.add(
                TradeEventLog.Kind.VETO,
                reason,
                decisionId = request.decisionId,
                tradeId = request.tradeId
            )
            onResult(OrderReceipt.Vetoed(reason))
            return
        }

        // Once a transport starts, failure may mean a delivered tap with a lost callback.
        // Never retry another transport until the broker outcome has been reconciled.
        val transport = listOf(rootTransport, gestureTransport, bridgeTransport).firstOrNull { it.isAvailable() }
        if (transport == null) {
            onResult(OrderReceipt.Failure("No hay transporte de tap disponible", requiresReconciliation = false))
            return
        }
        val terminal = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            transport.dispatch(request) { receipt ->
                if (terminal.compareAndSet(false, true)) onResult(receipt)
            }
        } catch (e: Exception) {
            if (terminal.compareAndSet(false, true)) {
                onResult(OrderReceipt.Failure(e.message ?: "Transporte interrumpido", transport.name))
            }
        }
    }
}

/**
 * Transporte vía ROOT (`su -c input tap`).
 */
class RootTapTransport(
    private val eventSink: EventSink = RingBufferEventSink(),
    var hudPort: HudPort? = null,
    private val rootCheck: () -> Boolean = { AutoTradeAccessibilityService.hasRootAccess() },
    private val rootTapExecutor: (Float, Float, String?, String?, ((Boolean, String?) -> Unit)?, Boolean) -> Unit = { x, y, did, tid, cb, bypass ->
        AutoTradeAccessibilityService.executeRootTap(x, y, did, tid, cb, bypass, orderAuthorized = true)
    }
) : TapTransport {
    override val name: String = "ROOT"

    override fun isAvailable(): Boolean = rootCheck()

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val overlay = hudPort ?: OverlayService.instance
        val bypassNeeded = overlay != null && overlay.isPointInsideHUD(request.x, request.y)
        val startTime = System.currentTimeMillis()

        val executeBlock = {
            rootTapExecutor(request.x, request.y, request.decisionId, request.tradeId, { success, err ->
                val latency = System.currentTimeMillis() - startTime
                val candleSec = ((System.currentTimeMillis() / 1000L) % 60L).toInt()
                if (success) {
                    onResult(OrderReceipt.Success(name, latency, candleSec))
                } else {
                    onResult(OrderReceipt.Failure(err ?: "Root tap fallo", name))
                }
            }, bypassNeeded)
        }

        if (bypassNeeded && overlay != null) {
            overlay.temporarilyBypassHUD(350L) {
                executeBlock()
            }
        } else {
            executeBlock()
        }
    }
}

/**
 * Transporte vía Accesibilidad de Android (`dispatchGesture`).
 */
class GestureTapTransport(
    private val eventSink: EventSink = RingBufferEventSink(),
    private val instanceSupplier: () -> AutoTradeAccessibilityService? = { AutoTradeAccessibilityService.instance }
) : TapTransport {
    override val name: String = "GESTURE"

    override fun isAvailable(): Boolean = instanceSupplier() != null

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val inst = instanceSupplier()
        if (inst == null) {
            onResult(OrderReceipt.Failure("Instancia de accesibilidad inactiva", name))
            return
        }
        val startTime = System.currentTimeMillis()
        inst.performOrderClickAt(request.x, request.y) { success, err ->
            val latency = System.currentTimeMillis() - startTime
            val candleSec = ((System.currentTimeMillis() / 1000L) % 60L).toInt()
            if (success) {
                onResult(OrderReceipt.Success(name, latency, candleSec))
            } else {
                onResult(OrderReceipt.Failure(err ?: "Gesto no confirmado", name))
            }
        }
    }
}

/**
 * Transporte directo para HTTP Bridge y entornos de prueba.
 */
class BridgeDirectTransport(
    private val eventSink: EventSink = RingBufferEventSink(),
    private val clickRunner: ((Float, Float, ((Boolean, String?) -> Unit)?) -> Unit)? = null
) : TapTransport {
    override val name: String = "BRIDGE_DIRECT"

    override fun isAvailable(): Boolean = clickRunner != null

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val startTime = System.currentTimeMillis()
        if (clickRunner != null) {
            clickRunner.invoke(request.x, request.y) { success, err ->
                val latency = System.currentTimeMillis() - startTime
                val candleSec = ((System.currentTimeMillis() / 1000L) % 60L).toInt()
                if (success) {
                    onResult(OrderReceipt.Success(name, latency, candleSec))
                } else {
                    onResult(OrderReceipt.Failure(err ?: "Bridge tap fallo", name))
                }
            }
        } else {
            onResult(OrderReceipt.Failure("Runner no configurado", name))
        }
    }
}
