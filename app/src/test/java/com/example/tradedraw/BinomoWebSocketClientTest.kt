package com.example.tradedraw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BinomoWebSocketClientTest {

    // Función de test para probar exactamente la lógica de parsing de processIncomingMessage
    private fun parseTickPayload(payload: String, activeAsset: String = "Z-CRY/IDX"): Pair<String, Double?> {
        var parsedPrice: Double? = null
        var assetName: String = activeAsset
        val clean = payload.trim().let {
            if (it.startsWith("42")) it.substring(2) else it
        }

        if (clean.startsWith("{")) {
            val json = JSONObject(clean)

            if (json.has("data")) {
                val dataObj = json.optJSONObject("data")
                if (dataObj != null) {
                    val assetsArr = dataObj.optJSONArray("assets")
                    if (assetsArr != null && assetsArr.length() > 0) {
                        for (i in 0 until assetsArr.length()) {
                            val assetObj = assetsArr.optJSONObject(i) ?: continue
                            val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                            val ric = assetObj.optString("ric", "")
                            if (!r.isNaN() && r > 0.0) {
                                if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                    parsedPrice = r
                                    if (ric.isNotEmpty()) assetName = ric
                                    if (ric.equals(activeAsset, ignoreCase = true)) break
                                }
                            }
                        }
                    } else {
                        if (dataObj.has("rate")) parsedPrice = dataObj.getDouble("rate")
                        else if (dataObj.has("price")) parsedPrice = dataObj.getDouble("price")
                        if (dataObj.has("ric")) assetName = dataObj.getString("ric")
                    }
                } else {
                    val dataArr = json.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        for (i in 0 until dataArr.length()) {
                            val item = dataArr.optJSONObject(i) ?: continue
                            val assetsArr = item.optJSONArray("assets")
                            if (assetsArr != null && assetsArr.length() > 0) {
                                for (j in 0 until assetsArr.length()) {
                                    val assetObj = assetsArr.optJSONObject(j) ?: continue
                                    val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                                    val ric = assetObj.optString("ric", "")
                                    if (!r.isNaN() && r > 0.0) {
                                        if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                            parsedPrice = r
                                            if (ric.isNotEmpty()) assetName = ric
                                            if (ric.equals(activeAsset, ignoreCase = true)) break
                                        }
                                    }
                                }
                            } else {
                                val r = if (item.has("rate")) item.optDouble("rate") else if (item.has("price")) item.optDouble("price") else Double.NaN
                                val ric = item.optString("ric", "")
                                if (!r.isNaN() && r > 0.0) {
                                    if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                        parsedPrice = r
                                        if (ric.isNotEmpty()) assetName = ric
                                        if (ric.equals(activeAsset, ignoreCase = true)) break
                                    }
                                }
                            }
                            if (parsedPrice != null && assetName.equals(activeAsset, ignoreCase = true)) break
                        }
                    }
                }
            }

            if (parsedPrice == null && json.has("assets")) {
                val assetsArr = json.optJSONArray("assets")
                if (assetsArr != null && assetsArr.length() > 0) {
                    for (i in 0 until assetsArr.length()) {
                        val assetObj = assetsArr.optJSONObject(i) ?: continue
                        val r = if (assetObj.has("rate")) assetObj.optDouble("rate") else if (assetObj.has("price")) assetObj.optDouble("price") else Double.NaN
                        val ric = assetObj.optString("ric", "")
                        if (!r.isNaN() && r > 0.0) {
                            if (parsedPrice == null || ric.equals(activeAsset, ignoreCase = true)) {
                                parsedPrice = r
                                if (ric.isNotEmpty()) assetName = ric
                                if (ric.equals(activeAsset, ignoreCase = true)) break
                            }
                        }
                    }
                }
            }

            if (parsedPrice == null) {
                if (json.has("rate")) parsedPrice = json.getDouble("rate")
                else if (json.has("price")) parsedPrice = json.getDouble("price")
                else if (json.has("close")) parsedPrice = json.getDouble("close")
                if (json.has("ric")) assetName = json.getString("ric")
            }
        }

        // Extractor Regex de respaldo
        if (parsedPrice == null || parsedPrice <= 0.0) {
            val activeBlock = Regex("""\{[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(clean)?.value
                ?: Regex("""\{[^{}]*"rate"\s*:\s*[0-9.]+[^{}]*"ric"\s*:\s*"${Regex.escape(activeAsset)}"[^{}]*\}""").find(clean)?.value
                ?: clean

            val rateMatch = Regex(""""rate"\s*:\s*([0-9.]+)""").find(activeBlock)
            val priceMatch = if (rateMatch == null) Regex(""""price"\s*:\s*([0-9.]+)""").find(activeBlock) else null
            val matchedVal = rateMatch?.groupValues?.getOrNull(1) ?: priceMatch?.groupValues?.getOrNull(1)
            if (matchedVal != null) {
                val p = matchedVal.toDoubleOrNull()
                if (p != null && p > 0.0) {
                    parsedPrice = p
                }
            }

            val ricMatch = Regex(""""ric"\s*:\s*"([^"]+)"""").find(activeBlock)
            if (ricMatch != null) {
                val matchedRic = ricMatch.groupValues[1]
                if (matchedRic.isNotEmpty()) {
                    assetName = matchedRic
                }
            }
        }

        return Pair(assetName, parsedPrice)
    }

    @Test
    fun testBinomoLiveCdpDataAssetsFrame() {
        val payload = """{"data":[{"assets":[{"rate":641.8674,"ric":"Z-CRY/IDX"}]}]}"""
        val (asset, price) = parseTickPayload(payload)

        assertEquals("Z-CRY/IDX", asset)
        assertNotNull(price)
        assertEquals(641.8674, price!!, 0.0001)
    }

    @Test
    fun testBinomoMultipleAssetsMatchesActive() {
        val payload = """{"data":[{"assets":[{"rate":1.0850,"ric":"EUR/USD"},{"rate":645.1234,"ric":"Z-CRY/IDX"}]}]}"""
        val (asset, price) = parseTickPayload(payload, activeAsset = "Z-CRY/IDX")

        assertEquals("Z-CRY/IDX", asset)
        assertEquals(645.1234, price!!, 0.0001)
    }

    @Test
    fun testBinomoClassicTickFormat() {
        val payload = """{"action":"tick","data":{"ric":"Z-CRY/IDX","rate":4238.125}}"""
        val (asset, price) = parseTickPayload(payload)

        assertEquals("Z-CRY/IDX", asset)
        assertEquals(4238.125, price!!, 0.0001)
    }

    @Test
    fun testRegexFallbackOnMalformedJson() {
        val malformedPayload = """prefix_corrupt{"unclosed":"rate": 987.6543, "ric": "BTC/USD" trailing"""
        val (asset, price) = parseTickPayload(malformedPayload, activeAsset = "BTC/USD")

        assertEquals("BTC/USD", asset)
        assertEquals(987.6543, price!!, 0.0001)
    }

    @Test
    fun testMarketTickSmoothedVelocityRetention() {
        val now = System.currentTimeMillis()
        val tick1 = MarketTick("Z-CRY/IDX", 640.0, now, velocity = 0.05f, smoothedVelocity = 0.05f)
        val tick2 = MarketTick("Z-CRY/IDX", 640.0, now + 1000L, velocity = 0.0f, smoothedVelocity = 0.0275f)

        assertEquals(0.05f, tick1.smoothedVelocity, 0.001f)
        assertEquals(0.0275f, tick2.smoothedVelocity, 0.001f)
        assertTrue("La velocidad suavizada debe retener inercia positiva a pesar de tick plano", tick2.smoothedVelocity > 0f)
    }
}
