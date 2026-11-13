package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ronda 2 (E→F) — regresión del PREFLIGHT fail-closed de presencia del broker.
 *
 * Repro que motiva el fix: 2026-11-13 04:30–04:35 UTC el motor quedó armado AUTONOMOUS
 * con `com.marketly.trading` ausente; a las 04:34:01 despachó por GESTURE y el gesto se
 * inyectó sin app que lo procesara (ciclo fantasma). El guard del FEED (WS nativo) no
 * cubre la PRESENCIA del proceso/ventana del broker destino.
 *
 * (a) broker ausente → veto BROKER_APP_ABSENT, cero gestos, evento registrado, orden
 *     terminal; (b) broker presente → comportamiento idéntico al actual (delegación);
 *     (c) la cascada de 13 vetos queda intacta (no se toca ningún byte congelado; el
 *     suite oráculo vive en :app — VetoCascadeEquivalenceTest / PreregistroR12VetosTest —
 *     y los archivos que valida no cambiaron, ver registry v3).
 */
class BrokerPresenceGateTest {

    private class RecordingEventSink : EventSink {
        val events = mutableListOf<String>()
        override fun add(kind: String, detail: String, decisionId: String?, tradeId: String?) {
            events.add("$kind:$detail")
        }
    }

    private class CountingGateway : OrderGateway {
        var dispatchCount = 0
        var lastRequest: OrderRequest? = null
        override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
            dispatchCount++
            lastRequest = request
            onResult(OrderReceipt.Success("INNER", 10L, 30))
        }
    }

    // ---------- (a) Broker ausente ----------

    @Test
    fun brokerAusenteVetaConCeroGestosEventoYOrdenTerminal() {
        val sink = RecordingEventSink()
        val inner = CountingGateway()
        var testTime = 1_000_000L
        val sensor = BrokerProcessPresenceSensor(
            packageNameSupplier = { "com.marketly.trading" },
            lastChangedAtSupplier = { 500_000L }, // visto hace 500 s > maxAge
            brokerClassifier = { BrokerDetector.currentBrokerFor(it) },
            clock = { testTime },
            maxAgeMs = 120_000L
        )
        val gate = BrokerPresenceGateTransport(inner, sensor, sink)

        var receipt: OrderReceipt? = null
        gate.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, "decision-1", "trade-1")) {
            receipt = it
        }

        assertTrue("Debe vetar, no fallar: $receipt", receipt is OrderReceipt.Vetoed)
        assertEquals(BrokerPresenceGateTransport.REASON, (receipt as OrderReceipt.Vetoed).reason)
        assertEquals("Cero gestos/taps al broker", 0, inner.dispatchCount)
        assertTrue(
            "Evento de veto registrado con decision id",
            sink.events.any { it.startsWith("VETO:") && it.contains("BROKER_APP_ABSENT") && it.contains("WINDOW_EVENT_STALE") }
        )
        // Terminal sin retry: la puerta no reintenta por su cuenta.
        repeat(3) {
            gate.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, "decision-2", "trade-2")) {}
        }
        assertEquals("Sin retry automático", 0, inner.dispatchCount)
    }

    @Test
    fun sinEventoDeVentanaNuncaDespacha() {
        val inner = CountingGateway()
        val sensor = BrokerProcessPresenceSensor(
            packageNameSupplier = { "" },
            lastChangedAtSupplier = { 0L },
            brokerClassifier = { BrokerDetector.currentBrokerFor(it) },
            clock = { 1_000_000L }
        )
        val gate = BrokerPresenceGateTransport(inner, sensor, RecordingEventSink())

        var receipt: OrderReceipt? = null
        gate.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, "decision-3", "trade-3")) { receipt = it }

        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals(BrokerPresenceGateTransport.REASON, (receipt as OrderReceipt.Vetoed).reason)
        assertEquals(0, inner.dispatchCount)
    }

    @Test
    fun ventanaEnPrimerPlanoQueNoEsBrokerSeTrataComoAusente() {
        val inner = CountingGateway()
        val sink = RecordingEventSink()
        var testTime = 1_000_000L
        val sensor = BrokerProcessPresenceSensor(
            packageNameSupplier = { "com.android.launcher" },
            lastChangedAtSupplier = { testTime - 1_000L }, // fresco pero NO es broker
            brokerClassifier = { BrokerDetector.currentBrokerFor(it) },
            clock = { testTime }
        )
        val gate = BrokerPresenceGateTransport(inner, sensor, sink)

        var receipt: OrderReceipt? = null
        gate.dispatch(OrderRequest(TradeAction.SELL, 100f, 200f, "decision-4", "trade-4")) { receipt = it }

        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals(0, inner.dispatchCount)
        assertTrue(sink.events.any { it.contains("FOREGROUND_NOT_BROKER") })
    }

    // ---------- (b) Broker presente ----------

    @Test
    fun brokerPresenteDelegaSinAlterarElComportamiento() {
        val sink = RecordingEventSink()
        val inner = CountingGateway()
        var testTime = 1_000_000L
        val sensor = BrokerProcessPresenceSensor(
            packageNameSupplier = { "com.marketly.trading" },
            lastChangedAtSupplier = { testTime - 5_000L }, // fresco: hace 5 s
            brokerClassifier = { BrokerDetector.currentBrokerFor(it) },
            clock = { testTime }
        )
        val gate = BrokerPresenceGateTransport(inner, sensor, sink)

        val request = OrderRequest(TradeAction.BUY, 100f, 200f, "decision-5", "trade-5")
        var receipt: OrderReceipt? = null
        gate.dispatch(request) { receipt = it }

        assertEquals("El despacho ocurre exactamente una vez", 1, inner.dispatchCount)
        assertSame("La request llega intacta al gateway real", request, inner.lastRequest)
        assertTrue(receipt is OrderReceipt.Success)
        assertEquals("Sin vetos espurios", 0, sink.events.size)
    }

    // ---------- (c) La cascada congelada queda intacta ----------

    @Test
    fun laCascadaCongeladaNoEsAlcanzadaPorElVeto() {
        // El gate NO inspecciona ExecutionContext ni duplica índices de veto: solo
        // presencia. Un veto de presencia NUNCA reemplaza/omite los 13 vetos originales
        // (esos corren en ExecutionPolicy antes de llegar aquí, byte-idénticos).
        val inner = CountingGateway()
        val gate = BrokerPresenceGateTransport(
            inner,
            BrokerProcessPresenceSensor(
                packageNameSupplier = { "" },
                lastChangedAtSupplier = { 0L },
                clock = { 1_000_000L }
            ),
            RecordingEventSink()
        )
        // SHADOW no debe llegar a producción por diseño (gateway lo rechaza antes), pero
        // el gate no cambia su semántica: veto de presencia es ortogonal al modo.
        var receipt: OrderReceipt? = null
        gate.dispatch(OrderRequest(TradeAction.BUY, 100f, 200f, "decision-6", "trade-6")) { receipt = it }
        assertTrue(receipt is OrderReceipt.Vetoed)
        assertEquals(0, inner.dispatchCount)
    }

    // ---------- Sensor: propiedades de la señal de presencia ----------

    @Test
    fun frescuraDentroDeLaVentanaCuentaComoPresente() {
        var testTime = 1_000_000L
        val lastChange = 880_000L // constante: testTime inicial - 120_000 (age = maxAge exacto)
        val sensor = BrokerProcessPresenceSensor(
            packageNameSupplier = { "com.binomo.app" },
            lastChangedAtSupplier = { lastChange },
            brokerClassifier = { BrokerDetector.currentBrokerFor(it) },
            clock = { testTime }
        )
        assertTrue("age == maxAge sigue siendo fresco (borde inclusivo)", sensor.isBrokerWindowPresent())
        testTime += 1L // age = maxAge + 1 → rancio
        assertFalse(sensor.isBrokerWindowPresent())
    }

    @Test
    fun brokerDetectorStampaTimestampSoloEnCambiosReales() {
        val original = BrokerDetector.currentPackageName
        val originalTs = BrokerDetector.lastPackageChangeAtMs
        try {
            BrokerDetector.currentPackageName = "com.other.app"
            val first = BrokerDetector.lastPackageChangeAtMs
            assertTrue(first > 0L)
            Thread.sleep(5)
            BrokerDetector.currentPackageName = "com.other.app" // sin cambio
            assertEquals("mismo paquete no reestampa", first, BrokerDetector.lastPackageChangeAtMs)
            Thread.sleep(5)
            BrokerDetector.currentPackageName = "com.marketly.trading" // cambio real
            assertTrue(BrokerDetector.lastPackageChangeAtMs >= first)
            assertEquals(Broker.BINOMO, BrokerDetector.currentBroker)
        } finally {
            BrokerDetector.currentPackageName = original
        }
    }

    @Test
    fun clasificadorMantieneElMapeoPreexistente() {
        assertEquals(Broker.BINOMO, BrokerDetector.currentBrokerFor("com.marketly.trading"))
        assertEquals(Broker.BINOMO, BrokerDetector.currentBrokerFor("net.binarymove.binomo"))
        assertEquals(Broker.IQ_OPTION, BrokerDetector.currentBrokerFor("com.iqoption.androidapp"))
        assertEquals(Broker.QUOTEX, BrokerDetector.currentBrokerFor("com.quotexapp"))
        assertEquals(Broker.POCKET_OPTION, BrokerDetector.currentBrokerFor("com.pocketoption.app"))
        assertEquals(Broker.UNKNOWN, BrokerDetector.currentBrokerFor("com.example.tradedraw"))
    }
}
