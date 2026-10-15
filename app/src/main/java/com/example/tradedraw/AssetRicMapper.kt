package com.example.tradedraw

/**
 * Ronda 20: mapeo del nombre de activo VISIBLE en pantalla al RIC real que habla el feed.
 *
 * Vive en su propio objeto por dos razones medidas, no por gusto de estructura:
 *
 * 1. **Se necesita en dos sitios que antes no coincidian.** El mapeo nacio dentro de
 *    `AutoTradeAccessibilityService.traverseScreenContext`, y el guard de desajuste de la Ronda
 *    20 tiene que comparar el activo del FEED contra el de PANTALLA. `latestObservedAsset`
 *    guarda el nombre VISIBLE (`"Aave (OTC)"`), no el RIC (`"AAVEUSD-OTC"`): comparar esas dos
 *    cadenas tal cual daria desajuste SIEMPRE y el bot no dispararia nunca. Comparar exige pasar
 *    antes por este mapeo.
 * 2. **Es una funcion pura y por tanto testeable.** El fallo historico de este mapeo (R13: AAVE
 *    caia al `else` y producia `Aave(OTC)` CON parentesis, un RIC que el feed no reconoce) es
 *    exactamente la clase de bug que un test unitario fija para siempre.
 *
 * ALCANCE: que un RIC sea SINTACTICAMENTE correcto no prueba que el servidor lo sirva. Medido en
 * la Ronda 20: `AAVEUSD-OTC` es un RIC bien formado y el servidor NO emite ni un tick para el
 * (responde `success:true` y luego silencio). Eso NO se puede arreglar desde aqui; lo cubre la
 * recuperacion por ticks de `BinomoWebSocketClient`.
 */
object AssetRicMapper {

    /**
     * Devuelve el RIC del feed para un nombre de activo visible, aplicando el patron real de
     * Binomo (`<BASE>USD-OTC`).
     *
     * El `else` normaliza en vez de concatenar a ciegas: quita el sufijo `(OTC)`/`OTC`/`(CASH)`,
     * elimina barras y todo lo que no sea alfanumerico, y anade el sufijo correcto. Sin esa
     * limpieza, `"Aave (OTC)"` producia `"Aave(OTC)"` con parentesis.
     */
    fun ricParaActivoVisible(assetCandidate: String): String {
        val upper = assetCandidate.uppercase()
        return when {
            upper.contains("IDX") -> "Z-CRY/IDX"
            upper.contains("ETH") || upper.contains("ETHEREUM") -> "ETHUSD-OTC"
            (upper.contains("BTC") || upper.contains("BITCOIN")) && upper.contains("CASH") -> "BCHUSD-OTC"
            upper.contains("BTC") || upper.contains("BITCOIN") -> "BTCUSD-OTC"
            upper.contains("SOL") -> "SOLUSD-OTC"
            upper.contains("LTC") || upper.contains("LITECOIN") -> "LTCUSD-OTC"
            upper.contains("XRP") || upper.contains("RIPPLE") -> "XRPUSD-OTC"
            upper.contains("ADA") || upper.contains("CARDANO") -> "ADAUSD-OTC"
            upper.contains("DOT") || upper.contains("POLKADOT") -> "DOTUSD-OTC"
            upper.contains("LINK") || upper.contains("CHAINLINK") -> "LINKUSD-OTC"
            upper.contains("DOGE") || upper.contains("DOGECOIN") -> "DOGEUSD-OTC"
            upper.contains("AAVE") -> "AAVEUSD-OTC"
            // "Oro" es el nombre en espanol del mismo activo; sin esta linea caia al `else` y
            // producia el RIC `OROUS-OTC`.
            upper.contains("XAU") || upper.contains("GOLD") || upper.contains("ORO") -> "XAU/USD"
            upper.contains("EUR/USD") -> "Z-EUR/USD2"
            upper.contains("GBP/USD") -> "Z-GBP/USD2"
            upper.contains("USD/JPY") -> "Z-USD/JPY2"
            else -> {
                val base = upper
                    .replace("(OTC)", "")
                    .replace("OTC", "")
                    .replace("(CASH)", "")
                    .replace("/", "")
                    .filter { it.isLetterOrDigit() }
                if (base.isBlank()) assetCandidate.replace(" ", "")
                else "${base}USD-OTC"
            }
        }
    }
}
