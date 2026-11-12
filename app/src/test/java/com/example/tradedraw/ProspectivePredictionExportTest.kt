package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.simulation.OpenSimulatedTrade
import com.example.tradedraw.simulation.SimMetadata
import org.junit.Assert.*
import org.junit.Test

class ProspectivePredictionExportTest {
    @Test fun exportsActualOpenDecisionIdentityAndMetadata() {
        val open = OpenSimulatedTrade("trade", "dec", TradeAction.SELL, 641.867249075, 10.0,
            1793077444955L, "Z-CRY/IDX", SimMetadata("MT_REJECTION", "CONSERVATIVE", 85f, "DOWNTREND", "original reason"))
        val json = ProspectivePredictionExport.json(open, "frozen-build")
        assertEquals(open.decisionId, json.getString("decisionId"))
        assertEquals(open.entryTimestampMs, json.getLong("timestamp"))
        assertEquals(open.entryPrice, json.getDouble("entry_price"), 0.0)
        assertEquals(open.asset, json.getString("asset"))
        assertEquals("SELL", json.getString("action"))
        assertEquals("original reason", json.getString("reason"))
        assertEquals("MT_REJECTION@r29c-frozen-4fdb646", json.getString("strategyVersion"))
        assertEquals("frozen-build", json.getString("git_sha"))
        assertFalse(json.has("result"))
    }
    @Test fun neverInventsObservedPayoutOrDecisionRegime() {
        val open = OpenSimulatedTrade("trade", "dec", TradeAction.BUY, 100.0, 1.0, 10000L, "A")
        val json = ProspectivePredictionExport.json(open, "observer")
        assertTrue(json.isNull("observed_payout"))
        assertEquals("UNKNOWN_NOT_PERSISTED_AT_DECISION", json.getString("regime"))
        assertEquals("OBSERVER_BUILD_REQUIRES_CONTINUOUS_CAPTURE_EPOCH", json.getString("sha_attribution"))
    }
}
