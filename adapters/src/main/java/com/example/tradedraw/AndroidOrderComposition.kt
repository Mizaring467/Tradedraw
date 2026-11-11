package com.example.tradedraw

import com.example.tradedraw.core.*

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

