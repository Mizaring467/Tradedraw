package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ronda 20: tests del mapeo nombre visible -> RIC del feed.
 *
 * Este mapeo ya causo un fallo real (R13): AAVE no tenia rama, caia al `else` que hacia
 * `replace(" ", "")` y producia **`Aave(OTC)` con parentesis**, un RIC que el feed no reconoce.
 * El activo quedaba sin ticks y el HUD pintaba "(0ms)" sin precio, lo que se atribuyo primero al
 * parser y luego al feed. Los casos de abajo fijan esa regresion.
 */
class AssetRicMapperTest {

    private fun ric(visible: String) = AssetRicMapper.ricParaActivoVisible(visible)

    @Test
    fun elNombreVisibleConParentesisNuncaProduceUnRicConParentesis() {
        // Es EL bug de R13. `else` hacia replace(" ", ""), asi que "Aave (OTC)" -> "Aave(OTC)".
        val r = ric("Aave (OTC)")
        assertEquals("AAVEUSD-OTC", r)
        // Invariante general: ningun RIC puede contener parentesis ni espacios.
        for (visible in listOf("Aave (OTC)", "Ethereum (OTC)", "Bitcoin (OTC)", "Oro (OTC)")) {
            val generado = ric(visible)
            assertEquals(
                "El RIC de '$visible' no puede contener parentesis: $generado",
                false, generado.contains("(") || generado.contains(")")
            )
            assertEquals(
                "El RIC de '$visible' no puede contener espacios: $generado",
                false, generado.contains(" ")
            )
        }
    }

    @Test
    fun losActivosConRamaExplicitaMapeanAlRicEsperado() {
        assertEquals("Z-CRY/IDX", ric("Crypto IDX"))
        assertEquals("ETHUSD-OTC", ric("Ethereum (OTC)"))
        assertEquals("BTCUSD-OTC", ric("Bitcoin (OTC)"))
        assertEquals("BCHUSD-OTC", ric("Bitcoin Cash (OTC)"))
        assertEquals("SOLUSD-OTC", ric("Solana (OTC)"))
        assertEquals("XRPUSD-OTC", ric("Ripple (OTC)"))
        assertEquals("ADAUSD-OTC", ric("Cardano (OTC)"))
        assertEquals("DOGEUSD-OTC", ric("Dogecoin (OTC)"))
        assertEquals("AAVEUSD-OTC", ric("Aave (OTC)"))
        assertEquals("XAU/USD", ric("Oro (OTC)"))
        assertEquals("Z-EUR/USD2", ric("EUR/USD"))
        assertEquals("Z-GBP/USD2", ric("GBP/USD"))
        assertEquals("Z-USD/JPY2", ric("USD/JPY"))
    }

    @Test
    fun losNombresCompletosMapeanAunqueElSimboloCortoNoEsteContenido() {
        // "BITCOIN" no contiene "BTC" y "CARDANO" no contiene "ADA": si solo se miraran los
        // simbolos cortos, caerian al `else` y producirian RICs inexistentes.
        assertEquals("BTCUSD-OTC", ric("Bitcoin"))
        assertEquals("ADAUSD-OTC", ric("Cardano"))
        assertEquals("LTCUSD-OTC", ric("Litecoin"))
        assertEquals("DOTUSD-OTC", ric("Polkadot"))
        assertEquals("LINKUSD-OTC", ric("Chainlink"))
    }

    @Test
    fun bitcoinCashNoSeConfundeConBitcoin() {
        // El orden de las ramas importa: la de BCH exige "CASH" y va ANTES que la de BTC.
        // Invertirlas mandaria Bitcoin Cash a BTCUSD-OTC, que es otro subyacente.
        assertEquals("BCHUSD-OTC", ric("Bitcoin Cash (OTC)"))
        assertEquals("BTCUSD-OTC", ric("Bitcoin (OTC)"))
    }

    @Test
    fun unActivoDesconocidoDerivaElPatronRealDeBinomo() {
        // El `else` no adivina: normaliza a <BASE>USD-OTC.
        assertEquals("FOOUSD-OTC", ric("Foo (OTC)"))
        assertEquals("MYCOINUSD-OTC", ric("My Coin (OTC)"))
    }

    @Test
    fun elMapeoEsInsensibleAMayusculasYMinusculas() {
        // La pantalla del broker no siempre entrega el mismo casing.
        assertEquals(ric("Aave (OTC)"), ric("aave (otc)"))
        assertEquals(ric("Ethereum (OTC)"), ric("ETHEREUM (OTC)"))
    }

    @Test
    fun losSegundosSinTicksNuncaSalenComoMaxValue() {
        // Defecto medido en dispositivo: con `lastTickAgeMs == Long.MAX_VALUE` (ningun tick desde
        // el arranque) el evento salia como `segundos=9223372036854775`. -1 significa "ninguno".
        assertEquals(-1L, BinomoWebSocketClient.segundosSinTicksLegibles(Long.MAX_VALUE))
        assertEquals(0L, BinomoWebSocketClient.segundosSinTicksLegibles(0L))
        assertEquals(21L, BinomoWebSocketClient.segundosSinTicksLegibles(21_500L))
        assertEquals(59L, BinomoWebSocketClient.segundosSinTicksLegibles(59_999L))
    }
}
