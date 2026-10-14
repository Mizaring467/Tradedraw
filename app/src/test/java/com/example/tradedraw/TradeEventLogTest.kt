package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ronda 13: pruebas del trabajo nuevo de observabilidad y de los hallazgos de A2/A4/A5.
 *
 * Contexto: `logcat` NO funciona en el dispositivo (`init.svc.logd = stopped`, sin root para
 * levantarlo; falla igual por USB y por Wi-Fi). Toda la observabilidad depende de
 * `TradeEventLog`, asi que su contrato importa: capacidad acotada, contadores que sobreviven
 * al recorte y JSON valido para el bridge.
 */
class TradeEventLogTest {

    @Before
    fun limpiar() {
        TradeEventLog.clear()
    }

    @Test
    fun registraYCuentaEventosPorTipo() {
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V1")
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V1")
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V2")
        TradeEventLog.add(TradeEventLog.Kind.TAP, "root (360,1450)")

        assertEquals("Dos vetos V1", 2, TradeEventLog.countOf(TradeEventLog.Kind.VETO, "V1"))
        assertEquals("Un veto V2", 1, TradeEventLog.countOf(TradeEventLog.Kind.VETO, "V2"))
        assertEquals("Total de vetos", 3, TradeEventLog.countOf(TradeEventLog.Kind.VETO))
        assertEquals("Un tap", 1, TradeEventLog.countOf(TradeEventLog.Kind.TAP))
    }

    @Test
    fun elBufferEsCircularYNoCreceSinLimite() {
        repeat(TradeEventLog.CAPACIDAD + 50) { i ->
            TradeEventLog.add(TradeEventLog.Kind.TAP, "tap-$i")
        }
        assertEquals(
            "El buffer no debe superar su capacidad",
            TradeEventLog.CAPACIDAD, TradeEventLog.recent(TradeEventLog.CAPACIDAD).size
        )
        // Lo mas reciente primero.
        assertEquals("tap-249", TradeEventLog.recent(1).first().detail)
    }

    @Test
    fun losContadoresSobrevivenAlRecorteDelBuffer() {
        repeat(TradeEventLog.CAPACIDAD + 10) {
            TradeEventLog.add(TradeEventLog.Kind.VETO, "V2")
        }
        // El buffer solo guarda los ultimos 200, pero el contador acumula todos: es lo que
        // exige A5 (tasa de vetos observable) y por lo que los contadores NO viven en la lista.
        assertEquals(TradeEventLog.CAPACIDAD + 10, TradeEventLog.countOf(TradeEventLog.Kind.VETO, "V2"))
    }

    @Test
    fun lastOfDevuelveElUltimoDeSuTipo() {
        TradeEventLog.add(TradeEventLog.Kind.TAP, "primero")
        TradeEventLog.add(TradeEventLog.Kind.VETO, "veto-medio")
        TradeEventLog.add(TradeEventLog.Kind.TAP, "ultimo")

        assertEquals("ultimo", TradeEventLog.lastOf(TradeEventLog.Kind.TAP)?.detail)
        assertEquals("veto-medio", TradeEventLog.lastOf(TradeEventLog.Kind.VETO)?.detail)
        assertNull(TradeEventLog.lastOf(TradeEventLog.Kind.ASSET))
    }

    @Test
    fun elJsonDelBridgeEsValidoYEscapaComillas() {
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V1 SR=\"Toque Directo\" PLAN=\"CALL\"")
        val json = TradeEventLog.toJson()

        assertTrue("Debe declarar exito", json.contains("\"success\":true"))
        assertTrue("Debe exponer el contador V1", json.contains("\"vetoV1\":1"))
        assertTrue("Debe exponer el contador V2", json.contains("\"vetoV2\":0"))
        assertFalse(
            "Las comillas internas no deben romper el JSON",
            json.contains("SR=\"Toque")
        )
        assertTrue("Se conserva el contenido escapado", json.contains("SR='Toque Directo'"))
    }

    @Test
    fun elJsonTieneComillasBalanceadas() {
        TradeEventLog.add(TradeEventLog.Kind.ORDER_STATE, "ENVIADA tap despachado")
        val json = TradeEventLog.toJson()
        // Chequeo estructural minimo: sin esto, un `detail` con comillas rompia `/status`
        // (ya paso en R12) y el bridge devolvia JSON invalido.
        val comillas = json.count { it == '"' }
        assertEquals("Comillas balanceadas", 0, comillas % 2)

        val abre = json.count { it == '{' }
        val cierra = json.count { it == '}' }
        assertEquals("Llaves balanceadas", abre, cierra)
    }

    @Test
    fun clearDejaElBufferVacio() {
        TradeEventLog.add(TradeEventLog.Kind.TAP, "x")
        TradeEventLog.clear()
        assertEquals(0, TradeEventLog.countOf(TradeEventLog.Kind.TAP))
        assertNull(TradeEventLog.lastOf(TradeEventLog.Kind.TAP))
    }
}

/**
 * Ronda 13/A2a: el mapeo de nombre visible -> RIC del feed.
 *
 * La causa raiz de "activo sin ticks" (Aave(OTC)) era el `else` de ese mapeo, que hacia
 * `replace(" ", "")` y producia `Aave(OTC)` CON parentesis: un RIC que el feed no reconoce.
 * Se replica la funcion para poder probarla sin Android.
 */
class AssetRicMappingTest {

    /** Copia exacta de la logica de `AutoTradeAccessibilityService` (R13/A2a). */
    private fun ric(assetCandidate: String): String {
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

    @Test
    fun aaveOtcProduceUnRicSinParentesis() {
        // Es EL caso del fallo: el RIC invalido `Aave(OTC)` dejaba el feed mudo.
        assertEquals("AAVEUSD-OTC", ric("Aave(OTC)"))
        assertEquals("AAVEUSD-OTC", ric("Aave (OTC)"))
        assertEquals("AAVEUSD-OTC", ric("Aave"))
    }

    @Test
    fun ningunRicConservaLosParentesis() {
        // Invariante: el feed solo acepta RICs sin parentesis ni espacios.
        val candidatos = listOf(
            "Aave(OTC)", "Ethereum (OTC)", "Crypto IDX", "Bitcoin", "Solana (OTC)",
            "Cardano (OTC)", "Polkadot (OTC)", "Chainlink (OTC)", "Dogecoin (OTC)",
            "Oro", "EUR/USD OTC", "GBP/USD OTC", "USD/JPY OTC", "Desconocido (OTC)"
        )
        for (c in candidatos) {
            val r = ric(c)
            assertFalse("$c -> $r no debe contener parentesis", r.contains("(") || r.contains(")"))
            assertFalse("$c -> $r no debe contener espacios", r.contains(" "))
            assertTrue("$c -> $r no debe estar vacio", r.isNotBlank())
        }
    }

    @Test
    fun losMapeosConocidosNoCambian() {
        // Guarda de no-regresion: el mapeo explicito ya estaba bien y no debe tocarse.
        assertEquals("Z-CRY/IDX", ric("Crypto IDX"))
        assertEquals("ETHUSD-OTC", ric("Ethereum (OTC)"))
        assertEquals("BTCUSD-OTC", ric("Bitcoin"))
        assertEquals("XAU/USD", ric("Oro"))
        assertEquals("Z-EUR/USD2", ric("EUR/USD OTC"))
    }
}

/**
 * Ronda 13/A4: el estado de reposo no es un fallo.
 *
 * `clearPendingTrade()` dejaba `NO_PROCESADA`, que significa "se intento y el broker no lo
 * registro". Eso hacia que `/status` y el HUD reportasen un fallo permanente cuando no habia
 * pasado nada. El estado de reposo es `NINGUNA`.
 */
class OrderStateReposoTest {

    @Test
    fun elEstadoInicialEsNingunaNoNoProcesada() {
        val rm = RiskManager()
        assertEquals(
            "Sin operaciones aun, el estado debe ser NINGUNA (reposo), no un fallo",
            RiskManager.OrderState.NINGUNA, rm.orderState
        )
        assertFalse(rm.hasPendingTrade)
        assertFalse(rm.hasConfirmedTrade)
    }

    @Test
    fun noProcesadaSigueSiendoUnFalloDistinguible() {
        // La guarda importante: NO_PROCESADA debe seguir existiendo y ser distinta de NINGUNA,
        // o se perderia la capacidad de contar ordenes no registradas (A4).
        assertTrue(
            "NO_PROCESADA y NINGUNA no pueden colapsarse: una es fallo y otra es reposo",
            RiskManager.OrderState.NO_PROCESADA != RiskManager.OrderState.NINGUNA
        )
        @Suppress("UNUSED_VARIABLE")
        val todos = RiskManager.OrderState.values().toList()
        assertTrue("Existen los cuatro estados", todos.size == 4)
        assertNotNull(todos.find { it == RiskManager.OrderState.ENVIADA })
        assertNotNull(todos.find { it == RiskManager.OrderState.CONFIRMADA })
    }
}
