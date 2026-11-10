package com.example.tradedraw.core

enum class AssetClassification {
    FOREX_REAL, SYNTHETIC_OTC, UNKNOWN
}

object AssetClassifier {
    fun classifyAsset(assetName: String): AssetClassification {
        val upper = assetName.uppercase().trim()
        if (upper.isEmpty()) return AssetClassification.UNKNOWN
        if (upper.contains("IDX") || upper.contains("OTC")) {
            return AssetClassification.SYNTHETIC_OTC
        }
        val forexRegex = Regex("^[A-Z]{3}\\s*/\\s*[A-Z]{3}$")
        if (forexRegex.matches(upper)) {
            return AssetClassification.FOREX_REAL
        }
        val currencies = listOf("EUR", "USD", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")
        val count = currencies.count { upper.contains(it) }
        if (count >= 2 && upper.contains("/")) {
            return AssetClassification.FOREX_REAL
        }
        return AssetClassification.UNKNOWN
    }
}
