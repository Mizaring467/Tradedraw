package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

class TapTransportParityTest {

    private class RecordingEventSink : EventSink {
        val events = mutableListOf<String>()
        override fun add(kind: String, detail: String, decisionId: String?, tradeId: String?) {
            events.add("$kind:$detail")
        }
    }

    private class MockTransport(
        override val name: String,
        var available: Boolean = true,
        var shouldSucceed: Boolean = true,
        var errorMessage: String? = null
    ) : TapTransport {
        var dispatchCount = 0
        var lastRequest: OrderRequest? = null

        override fun isAvailable(): Boolean = available

        override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
            dispatchCount++
            lastRequest = request
            if (shouldSucceed) {
                onResult(OrderReceipt.Success(name, 15L, 30))
            } else {
                onResult(OrderReceipt.Failure(errorMessage ?: "$name failed", name))
            }
        }
    }

    @Test
    fun modoDisabledOShadowRechazaDespachoYEmiteEvento() {
        val eventSink = RecordingEventSink()
        val guard = StandardDemoAccountGuard()
        guard.recordAccountObservation(AccountType.DEMO)

        val root = MockTransport("ROOT", available = true)
        val gesture = MockTransport("GESTURE", available = true)
        val bridge = MockTransport("BRIDGE", available = true)

        val gateway = DefaultOrderGateway(
            demoAccountGuard = guard,
            eventSink = eventSink,
            rootTransport = root,
            gestureTransport = gesture,
            bridgeTransport = bridge
        )

        var receiptDisabled: OrderReceipt? = null
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.DISABLED)) {
            receiptDisabled = it
        }
        assertTrue(receiptDisabled is OrderReceipt.Vetoed)
        assertEquals("TRADE_DISPATCH_REJECTED_MODE_DISABLED", (receiptDisabled as OrderReceipt.Vetoed).reason)
        assertEquals(0, root.dispatchCount)

        var receiptShadow: OrderReceipt? = null
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.SHADOW)) {
            receiptShadow = it
        }
        assertTrue(receiptShadow is OrderReceipt.Vetoed)
        assertEquals("TRADE_DISPATCH_REJECTED_MODE_SHADOW", (receiptShadow as OrderReceipt.Vetoed).reason)
        assertEquals(0, root.dispatchCount)

        assertTrue(eventSink.events.any { it.contains("TRADE_ENDPOINT_MODE_REJECTED mode=DISABLED") })
        assertTrue(eventSink.events.any { it.contains("TRADE_ENDPOINT_MODE_REJECTED mode=SHADOW") })
    }

    @Test
    fun cuentaNoConfirmadaOExpiradaBloqueaDespacho() {
        val eventSink = RecordingEventSink()
        var testTime = 1_000_000L
        val guard = StandardDemoAccountGuard(clock = { testTime }, maxAgeMs = 10_000L)

        val root = MockTransport("ROOT", available = true)
        val gateway = DefaultOrderGateway(
            demoAccountGuard = guard,
            eventSink = eventSink,
            rootTransport = root
        )

        // 1. Estado inicial desconocido
        var receipt: OrderReceipt? = null
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.AUTONOMOUS)) {
            receipt = it
        }
        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals("SEGURIDAD_CLICK_NO_DEMO_NOT_CONFIRMED", (receipt as OrderReceipt.Vetoed).reason)
        assertEquals(0, root.dispatchCount)

        // 2. Se confirma DEMO fresca
        guard.recordAccountObservation(AccountType.DEMO, timestampMs = testTime)
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.AUTONOMOUS)) {
            receipt = it
        }
        assertTrue(receipt is OrderReceipt.Success)
        assertEquals(1, root.dispatchCount)

        // 3. Pasa el tiempo y expira (>10s)
        testTime += 15_000L
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.AUTONOMOUS)) {
            receipt = it
        }
        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals("SEGURIDAD_CLICK_NO_DEMO_NOT_CONFIRMED", (receipt as OrderReceipt.Vetoed).reason)
        assertEquals(1, root.dispatchCount)
    }

    @Test
    fun cuentaRealBloqueadaSinConfirmacionDePropietario() {
        val eventSink = RecordingEventSink()
        val guard = StandardDemoAccountGuard()
        guard.recordAccountObservation(AccountType.REAL)

        val root = MockTransport("ROOT", available = true)
        val gateway = DefaultOrderGateway(
            demoAccountGuard = guard,
            eventSink = eventSink,
            rootTransport = root
        )

        var receipt: OrderReceipt? = null
        gateway.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, mode = AutoTradeMode.AUTONOMOUS)) {
            receipt = it
        }
        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals("SEGURIDAD_CLICK_NO_DEMO", (receipt as OrderReceipt.Vetoed).reason)
        assertEquals(0, root.dispatchCount)
        assertTrue(eventSink.events.any { it.contains("SEGURIDAD_CLICK_NO_DEMO") })
    }

    @Test
    fun transicionDeDemoARealYReinicioBloquean() {
        val guard = StandardDemoAccountGuard()
        guard.recordAccountObservation(AccountType.DEMO)
        assertTrue(guard.isDispatchAllowed())

        // Transición a REAL
        guard.recordAccountObservation(AccountType.REAL)
        assertFalse(guard.isDispatchAllowed())

        // Volver a DEMO
        guard.recordAccountObservation(AccountType.DEMO)
        assertTrue(guard.isDispatchAllowed())

        // Reinicio
        guard.reset()
        assertFalse(guard.isDispatchAllowed())
        assertEquals(AccountType.UNKNOWN, guard.getAccountType())
    }

    @Test
    fun cascadaRootPrimarioYFallbackAGesture() {
        val guard = StandardDemoAccountGuard()
        guard.recordAccountObservation(AccountType.DEMO)

        val root = MockTransport("ROOT", available = false)
        val gesture = MockTransport("GESTURE", available = true)

        val gateway = DefaultOrderGateway(
            demoAccountGuard = guard,
            rootTransport = root,
            gestureTransport = gesture
        )

        var receipt: OrderReceipt? = null
        gateway.dispatch(OrderRequest(TradeAction.SELL, 500f, 900f, mode = AutoTradeMode.AUTONOMOUS)) {
            receipt = it
        }

        assertTrue(receipt is OrderReceipt.Success)
        assertEquals("GESTURE", (receipt as OrderReceipt.Success).transport)
        assertEquals(0, root.dispatchCount)
        assertEquals(1, gesture.dispatchCount)
    }

    @Test
    fun bridgeDirectTransportDespacha() {
        var tappedX = 0f
        var tappedY = 0f
        val transport = BridgeDirectTransport(clickRunner = { x, y, cb ->
            tappedX = x
            tappedY = y
            cb?.invoke(true, null)
        })

        assertTrue(transport.isAvailable())
        var receipt: OrderReceipt? = null
        transport.dispatch(OrderRequest(TradeAction.BUY, 123f, 456f)) {
            receipt = it
        }
        assertTrue(receipt is OrderReceipt.Success)
        assertEquals(123f, tappedX, 0.01f)
        assertEquals(456f, tappedY, 0.01f)
    }
}
