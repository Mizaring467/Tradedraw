package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class BridgeBodyParsingTest {
    @Test fun jsonSellAndRequestIdArePreserved() {
        val body = parseBridgeBody("""{"action":"SELL","requestId":"retry-123"}""")
        assertEquals("SELL", body["action"])
        assertEquals("retry-123", body["requestId"])
    }
    @Test fun historicalFormStillWorks() {
        assertEquals(mapOf("action" to "PUT", "requestId" to "a b"), parseBridgeBody("action=PUT&requestId=a%20b"))
    }
    @Test(expected = org.json.JSONException::class) fun malformedJsonFailsExplicitly() {
        parseBridgeBody("{broken")
    }
}
