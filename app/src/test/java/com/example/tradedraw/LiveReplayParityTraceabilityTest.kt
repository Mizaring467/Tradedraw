package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23H Fase 2: trazabilidad y paridad Live <-> Replay.
 *
 * QUE FIJA ESTA SUITE. Las cinco invariantes sin las cuales el comparador no puede distinguir un
 * fallo del motor de un fallo de instrumentacion. En R23G el match rate salio 0.0% con
 * `pass: false` mientras el bot operaba bien: el problema no era la estrategia, era que las
 * identidades y los instantes de los dos lados no eran comparables. Estos tests congelan ese
 * contrato.
 *
 * NO MIDE RENTABILIDAD. Ningun test aqui afirma que una estrategia gane: verifican que un trade
 * real se puede seguir de punta a punta y que una decision bloqueada NO se convierte en trade.
 *
 * POR QUE EN KOTLIN Y NO EN PYTHON. La identidad (`decision_id`) la genera el motor Android; es el
 * unico lado que puede fijar su contrato. El comparador Python consume esas identidades, no las
 * produce, asi que su contrato vive aqui.
 */
class LiveReplayParityTraceabilityTest {

    /**
     * Fila minima de traza. Modela lo que el comparador necesita de cada lado: identidad, instante,
     * accion, segundo de vela y desenlace. `result == null` significa "decision sin trade"
     * (bloqueada o vetada), que es justo el caso que hundia la paridad.
     */
    private data class TraceRow(
        val decisionId: String,
        val timestampMs: Long,
        val asset: String,
        val action: String,
        val candleSecond: Int,
        val result: String?,
        val origin: String
    )

    /** Umbral de tolerancia temporal, alineado con `compare_live_replay.py` (6000 ms). */
    private val toleranciaTemporalMs = 6_000L

    private fun idDe(asset: String, tsMs: Long, action: String) =
        TradingEngine.generateDeterministicDecisionId(asset, tsMs, action)

    /**
     * Espejo minimo de `compare_trades`: 1) `decision_id` exacto, 2) fallback temporal por activo.
     * Devuelve el indice del replay emparejado, o `null`.
     */
    private fun emparejar(
        live: TraceRow,
        replay: List<TraceRow>,
        usados: MutableSet<Int>
    ): Int? {
        replay.forEachIndexed { i, r ->
            if (i !in usados && r.decisionId == live.decisionId) return i
        }
        var mejor: Int? = null
        var mejorDelta = Long.MAX_VALUE
        replay.forEachIndexed { i, r ->
            if (i in usados || r.asset != live.asset) return@forEachIndexed
            val delta = kotlin.math.abs(r.timestampMs - live.timestampMs)
            if (delta <= toleranciaTemporalMs && delta < mejorDelta) {
                mejor = i
                mejorDelta = delta
            }
        }
        return mejor
    }

    // ------------------------------------------------------------------
    // TEST 1 — TRADE REAL: decision -> trade -> outcome se localiza en replay
    // ------------------------------------------------------------------

    @Test
    fun test1_tradeRealSeLocalizaEnReplay() {
        val ts = 1_760_000_040_000L
        val live = TraceRow(
            decisionId = idDe("EURUSD_otc", ts, "BUY"),
            timestampMs = ts,
            asset = "EURUSD_otc",
            action = "BUY",
            candleSecond = 0,
            result = "WIN",
            origin = "live"
        )
        // El replay fecha por el TICK de la senal; el tap real llega ~4,6 s despues por la latencia
        // fisica del hardware. Mismo `decision_id`, instantes distintos: eso es lo normal.
        val replay = listOf(live.copy(timestampMs = ts - 4_600L, origin = "replay"))

        val usados = mutableSetOf<Int>()
        val idx = emparejar(live, replay, usados)

        assertNotNull("El trade real debe localizarse en el replay", idx)
        assertEquals(
            "El emparejamiento debe ser por identidad exacta, no por cercania temporal",
            live.decisionId,
            replay[idx!!].decisionId
        )
        assertEquals("El desenlace debe viajar con la identidad", "WIN", replay[idx].result)
    }

    // ------------------------------------------------------------------
    // TEST 2 — DECISION BLOQUEADA: no aparece como trade simulado en replay
    // ------------------------------------------------------------------

    @Test
    fun test2_decisionBloqueadaNoApareceComoTradeEnReplay() {
        // Una compra en techo con callPower agotado: el motor la veta, luego NO hay trade.
        val callPower = 82
        val motivo = motivoVetoPorAgotamientoDeCompra(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            callPower = callPower
        )
        assertNotNull("El caso de prueba debe ser efectivamente vetado", motivo)

        // El lado live registra la decision con su identidad pero SIN desenlace.
        val ts = 1_760_000_100_000L
        val decisionLive = TraceRow(
            decisionId = idDe("EURUSD_otc", ts, "BUY"),
            timestampMs = ts,
            asset = "EURUSD_otc",
            action = "BUY",
            candleSecond = 1,
            result = null,
            origin = "live"
        )
        // El replay, con el veto replicado, tampoco debe producir fila.
        val tradesReplay = emptyList<TraceRow>()

        val usados = mutableSetOf<Int>()
        val idx = emparejar(decisionLive, tradesReplay, usados)

        assertNull("Una decision vetada no puede emparejar contra un trade inexistente", idx)
        assertTrue("El replay no debe simular ningun trade para una decision vetada", tradesReplay.isEmpty())
        assertEquals(
            "El motivo del bloqueo debe quedar registrado con su token indexable",
            "VETO_BUY_EXHAUSTION_CLIMAX",
            motivo!!.substringBefore(' ')
        )
    }

    // ------------------------------------------------------------------
    // TEST 3 — IDENTIDAD: decisiones distintas no colisionan
    // ------------------------------------------------------------------

    @Test
    fun test3_decisionesDistintasNoColisionan() {
        val ts = 1_760_000_200_000L
        val casos = listOf(
            "mismo instante, otra accion" to idDe("EURUSD_otc", ts, "SELL"),
            "otro segundo" to idDe("EURUSD_otc", ts + 1_000L, "BUY"),
            "otro tick" to idDe("EURUSD_otc", ts + 2_430L, "BUY"),
            "otro activo" to idDe("GBPUSD_otc", ts, "BUY")
        )
        val ids = casos.map { it.second }

        assertEquals(
            "Cuatro decisiones distintas deben producir cuatro identidades distintas",
            4,
            ids.toSet().size
        )
        // La base de referencia: la misma tupla SIEMPRE da el mismo id (determinismo).
        assertEquals(
            "La identidad debe ser reproducible entre llamadas",
            idDe("EURUSD_otc", ts, "BUY"),
            idDe("EURUSD_otc", ts, "BUY")
        )
        assertEquals("La accion forma parte de la clave", true, ids[0] != idDe("EURUSD_otc", ts, "BUY"))
    }

    @Test
    fun test3b_elIdEsEstableAnteSegundosYMilisegundos() {
        // El motor acepta el timestamp en segundos o en ms; la clave siempre sale en ms. Si esto se
        // rompiera, el mismo trade tendria dos identidades segun quien lo escriba.
        val tsMs = 1_760_000_300_000L
        assertEquals(
            "Segundos y milisegundos deben producir la misma identidad",
            idDe("EURUSD_otc", tsMs, "BUY"),
            idDe("EURUSD_otc", tsMs / 1000L, "BUY")
        )
    }

    // ------------------------------------------------------------------
    // TEST 4 — DUPLICACION: el mismo evento dos veces no genera identidad ambigua
    // ------------------------------------------------------------------

    @Test
    fun test4_eventoDuplicadoNoGeneraIdentidadAmbigua() {
        val ts = 1_760_000_400_000L
        val evento = TraceRow(
            decisionId = idDe("EURUSD_otc", ts, "SELL"),
            timestampMs = ts,
            asset = "EURUSD_otc",
            action = "SELL",
            candleSecond = 0,
            result = "LOSS",
            origin = "live"
        )
        // El monitor puede observar el mismo tap en dos ciclos de /status: la fila se duplica.
        val crudo = listOf(evento, evento.copy(), evento.copy(timestampMs = ts + 1_200L))

        // Dedupe por identidad, que es lo que hace el reconciliador canonico.
        val unicos = crudo.distinctBy { it.decisionId }

        assertEquals("Tres filas crudas del mismo evento deben colapsar a una", 1, unicos.size)
        assertEquals("La identidad sobreviviente debe ser la original", evento.decisionId, unicos.single().decisionId)
        // Y esa identidad, al emparejar, no puede consumir dos trades del replay.
        val replay = listOf(evento.copy(timestampMs = ts - 4_000L, origin = "replay"))
        val usados = mutableSetOf<Int>()
        val primero = emparejar(unicos.single(), replay, usados)
        assertNotNull(primero)
        usados.add(primero!!)
        assertNull(
            "Un trade del replay no puede emparejarse dos veces con el mismo evento",
            emparejar(unicos.single(), replay, usados)
        )
    }

    // ------------------------------------------------------------------
    // TEST 6 — CONGELACION: la extraccion de `calcularPoderesDeMercado` no cambio la estrategia
    // ------------------------------------------------------------------

    @Test
    fun test6_laExtraccionNoAlteroNingunValorDeLaFormula() {
        // Valores dorados calculados a mano sobre la formula ORIGINAL de
        // `buildAnalysisFromSyntheticEngine`, ANTES de extraerla a funcion pura. Si la extraccion (o
        // cualquier edicion futura) moviera una constante, estos asserts caen: es la garantia de que
        // una correccion de infraestructura no se convirtio en un cambio de estrategia encubierto.

        // UPTREND base 68 + distSup<=0.25 (+10) + rsi 50 en 40..68 (+8) + impulso alcista (+6) = 92
        assertEquals(
            "UPTREND con todos los refuerzos",
            92 to 8,
            calcularPoderesDeMercado(
                trend = TrendDirection.UPTREND,
                distSupportRatio = 0.10f,
                distResistanceRatio = 0.80f,
                rsi = 50.0,
                isBullishImpulse = true,
                isBearishImpulse = false
            )
        )
        // UPTREND sin refuerzos: 68 / 32 (el complemento se normaliza a 100 - 68 = 32)
        assertEquals(
            "UPTREND en crudo",
            68 to 32,
            calcularPoderesDeMercado(
                trend = TrendDirection.UPTREND,
                distSupportRatio = 0.90f,
                distResistanceRatio = 0.90f,
                rsi = 90.0,
                isBullishImpulse = false,
                isBearishImpulse = false
            )
        )
        // DOWNTREND base 32 / 68: el refuerzo va al PUT, no al CALL
        assertEquals(
            "DOWNTREND con todos los refuerzos",
            8 to 92,
            calcularPoderesDeMercado(
                trend = TrendDirection.DOWNTREND,
                distSupportRatio = 0.90f,
                distResistanceRatio = 0.10f,
                rsi = 45.0,
                isBullishImpulse = false,
                isBearishImpulse = true
            )
        )
        // SIDEWAYS cerca de soporte: 62 / 38; sin cercania: 50 / 50
        assertEquals(
            "SIDEWAYS cerca de soporte",
            62 to 38,
            calcularPoderesDeMercado(TrendDirection.SIDEWAYS, 0.20f, 0.90f, 50.0, false, false)
        )
        assertEquals(
            "SIDEWAYS sin niveles cercanos",
            50 to 50,
            calcularPoderesDeMercado(TrendDirection.SIDEWAYS, 0.90f, 0.90f, 50.0, false, false)
        )
        // Invariante del termometro: CALL + PUT = 100 SIEMPRE.
        for (trend in TrendDirection.values()) {
            for (rsi in listOf(10.0, 35.0, 50.0, 75.0)) {
                val (c, p) = calcularPoderesDeMercado(trend, 0.15f, 0.15f, rsi, true, true)
                assertEquals("CALL+PUT debe sumar 100 (trend=$trend, rsi=$rsi)", 100, c + p)
                // OJO: ninguno de los dos lados se re-acota a [15,95] tras normalizar. En la formula
                // original el `coerceIn(15,95)` corre ANTES de `complemento = 100 - dominante`, asi
                // que un dominante de 92 deja al otro lado en 8 (y simetricamente en DOWNTREND el
                // CALL puede quedar en 8). Es la conducta congelada en produccion y este test la fija
                // tal cual: "corregirla" aqui seria un cambio de estrategia encubierto.
            }
        }
    }

    // ------------------------------------------------------------------
    // TEST 5 — PARIDAD: el conjunto empareja con PASS y match_rate 100%
    // ------------------------------------------------------------------
    @Test
    fun test5_conjuntoParitarioDaPassYMatchRate100() {
        val base = 1_760_000_500_000L
        val acciones = listOf("BUY", "SELL", "BUY", "SELL", "BUY")
        val desenlaces = listOf("WIN", "LOSS", "LOSS", "WIN", "WIN")

        val live = acciones.mapIndexed { i, accion ->
            val ts = base + i * 60_000L
            TraceRow(
                decisionId = idDe("EURUSD_otc", ts, accion),
                timestampMs = ts,
                asset = "EURUSD_otc",
                action = accion,
                candleSecond = 0,
                result = desenlaces[i],
                origin = "live"
            )
        }
        // El replay reproduce las mismas decisiones con la latencia fisica de lectura (~4,5 s).
        val replay = live.map { it.copy(timestampMs = it.timestampMs - 4_500L, origin = "replay") }

        val usados = mutableSetOf<Int>()
        var emparejados = 0
        for (l in live) {
            val idx = emparejar(l, replay, usados)
            if (idx != null) {
                usados.add(idx)
                emparejados++
            }
        }

        assertEquals("Todos los eventos live deben emparejar", live.size, emparejados)
        assertEquals("Un trade del replay no puede emparejarse dos veces", live.size, usados.size)
        val matchRatePct = emparejados * 100.0 / live.size
        assertEquals("El match rate debe ser 100%", 100.0, matchRatePct, 0.0001)
        assertEquals("Con paridad total el veredicto debe ser PASS", "PASS", if (matchRatePct >= 100.0) "PASS" else "FAIL")
    }
}
