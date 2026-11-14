package com.example.tradedraw.simulation

import android.content.Context
import com.example.tradedraw.core.*
import com.example.tradedraw.persistence.SimulationResultFileJournal
import java.io.File

/** Android composition only. Storage failures stay observable and never stop the real feed worker. */
class ShadowSimulationRuntime(context: Context, private val events: EventSink) {
    private var initializationError: String? = null
    val simulation: ShadowSimulation? = try {
        ShadowSimulation(SimulatedOrderGateway(
            simLedger = SimLedger(store = FileSimSnapshotStore(File(context.filesDir, "simulation/ledger.bin"))), eventSink = events),
            SimulationResultFileJournal(File(context.filesDir, "TradeDraw_Audits"))).also { it.flushResults() }
    } catch (e: Exception) { initializationError = e.message ?: "SIM_STATE_UNREADABLE"; null }
    var enabled: Boolean
        get() = simulation?.enabled == true
        set(value) { check(!value || simulation != null) { errorMessage.orEmpty() }; simulation?.enabled = value }
    val errorMessage: String? get() = initializationError ?: simulation?.lastError
    fun onTick(tick: MarketTick) { simulation?.onTick(tick); errorMessage?.let { events.add("SIMULATION_ERROR", it) } }
    fun open(request: OrderRequest, tick: MarketTick, metadata: SimMetadata, callback: (OrderReceipt) -> Unit) {
        simulation?.open(request, tick, metadata, callback) ?: callback(OrderReceipt.Vetoed(errorMessage ?: "SIM_UNAVAILABLE"))
    }
}
