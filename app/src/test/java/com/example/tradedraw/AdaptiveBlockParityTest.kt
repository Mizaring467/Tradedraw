package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23J Fase B — EQUIVALENCIA Y DETERMINISMO de la logica de `ADAPTIVE_BLOCK`.
 *
 * QUE PROTEGE. El veto por anti-patron recurrente vivia encerrado en metodos de instancia de
 * `AdaptiveLearningEngine`, asi que el arnes de replay no podia evaluarlo: no lo replicaba en
 * absoluto. En R23J Fase B se extrajo a funciones puras del archivo para que Live y Replay invoquen
 * LA MISMA pieza.
 *
 * COMO SE VERIFICA LA EQUIVALENCIA. Con oraculos independientes que copian la logica ORIGINAL tal
 * como estaba antes de la extraccion. No llaman a las funciones bajo prueba: si lo hicieran,
 * compararian la funcion consigo misma.
 *
 * QUE NO PRUEBA. No dice nada sobre si el 0.99f es un buen umbral. Eso es estrategia, esta congelado
 * desde R23F y no se toca aqui.
 */
class AdaptiveBlockParityTest {

    // ------------------------------------------------------------------
    // ORACULOS: copia literal de la logica de HEAD antes de la extraccion
    // ------------------------------------------------------------------

    private fun oraculoSimilitud(a: TradeContextSignature, b: TradeContextSignature): Float {
        var score = 0f
        if (a.action == b.action) score += 0.20f
        if (a.trend == b.trend) score += 0.20f
        val distSupDiff = Math.abs(a.distanceToSupportRatio - b.distanceToSupportRatio)
        val distResDiff = Math.abs(a.distanceToResistanceRatio - b.distanceToResistanceRatio)
        val srSimilarity = ((1.0f - distSupDiff) + (1.0f - distResDiff)) / 2.0f
        score += (srSimilarity.coerceIn(0f, 1f) * 0.30f)
        if (a.isBullishImpulse == b.isBullishImpulse && a.isBearishImpulse == b.isBearishImpulse) {
            score += 0.15f
        } else {
            val velDiff = Math.abs(a.tickVelocityNormalized - b.tickVelocityNormalized)
            val velSim = (1.0f - (velDiff / 4.0f)).coerceIn(0f, 1f)
            score += (velSim * 0.15f)
        }
        if (a.isMarketSideways == b.isMarketSideways && a.isConsolidationTight == b.isConsolidationTight) {
            score += 0.15f
        }
        return score.coerceIn(0f, 1f)
    }

    private fun oraculoCompuerta(a: TradeContextSignature, b: TradeContextSignature): Boolean {
        if (a.action != b.action) return false
        if (a.trend != b.trend) return false
        if (a.isMarketSideways != b.isMarketSideways) return false
        val ds = Math.abs(a.distanceToSupportRatio - b.distanceToSupportRatio)
        val dr = Math.abs(a.distanceToResistanceRatio - b.distanceToResistanceRatio)
        return ds <= 0.10f && dr <= 0.10f
    }

    private fun firma(
        action: TradeAction = TradeAction.BUY,
        trend: TrendDirection = TrendDirection.UPTREND,
        ds: Float = 0.50f,
        dr: Float = 0.50f,
        vel: Float = 0f,
        bull: Boolean = false,
        bear: Boolean = false,
        sideways: Boolean = false,
        tight: Boolean = false
    ) = TradeContextSignature(
        action = action,
        trend = trend,
        distanceToSupportRatio = ds,
        distanceToResistanceRatio = dr,
        isNearSupportZone = ds <= 0.30f,
        isNearResistanceZone = dr <= 0.30f,
        tickVelocityNormalized = vel,
        isBullishImpulse = bull,
        isBearishImpulse = bear,
        isDojiOrLowVolume = false,
        isConsolidationTight = tight,
        isMarketSideways = sideways,
        strategyName = "AUTO_ADAPTIVE",
        candleSecond = 1,
        timestampMs = 0L
    )

    // ------------------------------------------------------------------
    // 1. EQUIVALENCIA de la similitud sobre una rejilla amplia
    // ------------------------------------------------------------------

    @Test
    fun similitudCoincideConElOraculoEnTodaLaRejilla() {
        val acciones = listOf(TradeAction.BUY, TradeAction.SELL)
        val tendencias = TrendDirection.values().toList()
        val distancias = listOf(0.0f, 0.05f, 0.10f, 0.20f, 0.25f, 0.50f, 0.75f, 1.0f)
        val velocidades = listOf(0f, 0.5f, 2f, 4f, -3f)
        val flags = listOf(false to false, true to false, false to true, true to true)

        var n = 0
        val fallos = mutableListOf<String>()
        for (accA in acciones) for (accB in acciones)
        for (tA in tendencias) for (tB in tendencias)
        for (dsA in distancias) for (drA in distancias)
        for (velA in velocidades)
        for ((bull, bear) in flags) {
            val a = firma(accA, tA, dsA, drA, velA, bull, bear)
            val b = firma(accB, tB, dsA, drA, 0f, false, false)
            n++
            val esp = oraculoSimilitud(a, b)
            val got = similitudDeFirmas(a, b)
            if (esp != got && fallos.size < 5) fallos.add("esp=$esp got=$got a=$a b=$b")
        }
        assertTrue("La rejilla no puede estar vacia", n > 1000)
        assertTrue("Divergencias de similitud: $fallos", fallos.isEmpty())
        println("Similitud verificada en $n combinaciones.")
    }

    // ------------------------------------------------------------------
    // 2. EQUIVALENCIA de la compuerta estricta
    // ------------------------------------------------------------------

    @Test
    fun compuertaCoincideConElOraculo() {
        val acciones = listOf(TradeAction.BUY, TradeAction.SELL)
        val tendencias = TrendDirection.values().toList()
        // Bordes exactos de la tolerancia +-0.10
        val distancias = listOf(0.0f, 0.0999f, 0.10f, 0.1001f, 0.15f, 0.30f, 0.50f)
        val regimen = listOf(false, true)

        var n = 0
        for (accA in acciones) for (accB in acciones)
        for (tA in tendencias) for (tB in tendencias)
        for (dsA in distancias) for (dsB in distancias)
        for (sw in regimen) {
            val a = firma(accA, tA, dsA, 0.5f, 0f, false, false, sw, false)
            val b = firma(accB, tB, dsB, 0.5f, 0f, false, false, sw, false)
            n++
            assertEquals(
                "Compuerta divergente (a=$a, b=$b)",
                oraculoCompuerta(a, b),
                contextoEstrictamenteCoincidente(a, b)
            )
        }
        println("Compuerta verificada en $n combinaciones.")
    }

    // ------------------------------------------------------------------
    // 3. DETERMINISMO: misma entrada, misma salida, sin estado
    // ------------------------------------------------------------------

    @Test
    fun laFuncionEsDeterministaYNoGuardaEstado() {
        val candidata = firma(ds = 0.40f, dr = 0.60f)
        val perdida = firma(ds = 0.42f, dr = 0.61f)

        val primera = similitudDeFirmas(candidata, perdida)
        repeat(50) {
            assertEquals("La similitud debe ser estable entre llamadas", primera, similitudDeFirmas(candidata, perdida))
        }
        assertEquals(
            "La similitud debe ser simetrica",
            similitudDeFirmas(candidata, perdida),
            similitudDeFirmas(perdida, candidata)
        )
        // Una llamada con otra firma no puede contaminar la siguiente.
        similitudDeFirmas(perdida, perdida)
        assertEquals("Sin contaminacion entre llamadas", primera, similitudDeFirmas(candidata, perdida))
    }

    // ------------------------------------------------------------------
    // 4. LA DECISION de bloqueo: umbral, compuerta y motivo
    // ------------------------------------------------------------------

    @Test
    fun noBloqueaSinMemoriaDePerdidas() {
        val candidata = firma()
        assertNull(
            "Sin perdidas previas no puede haber anti-patron",
            motivoBloqueoPorAntiPatron(candidata, null, 0f)
        )
    }

    @Test
    fun noBloqueaPorDebajoDelUmbral() {
        val candidata = firma()
        val perdida = firma()
        // Similitud real alta, pero declarada por debajo de 0.99f.
        assertNull(
            "0.98f no debe bloquear: el umbral es 0.99f",
            motivoBloqueoPorAntiPatron(candidata, perdida, 0.98f)
        )
        assertNull("0.9899f tampoco", motivoBloqueoPorAntiPatron(candidata, perdida, 0.9899f))
    }

    @Test
    fun bloqueaEnElUmbralExactoConContextoEstricto() {
        // Firmas identicas: similitud 1.0 y contexto estricto.
        val candidata = firma()
        val perdida = firma()
        val motivo = motivoBloqueoPorAntiPatron(
            candidata, perdida, AdaptiveLearningEngine.ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD
        )
        assertNotNull("En el umbral exacto (>=) debe bloquear", motivo)
        assertTrue("El motivo debe indexar el anti-patron: $motivo", motivo!!.contains("Anti-patron perdedor recurrente"))
        assertTrue("El motivo debe llevar el porcentaje: $motivo", motivo.contains("99%"))
    }

    @Test
    fun noBloqueaSiElContextoNoEsEstrictamenteCoincidente() {
        // Similitud por encima del umbral pero S/R fuera de +-0.10, o distinta accion.
        val candidata = firma(ds = 0.10f, dr = 0.50f)
        val perdidaDsLejos = firma(ds = 0.30f, dr = 0.50f)
        assertNull(
            "S/R fuera de tolerancia no debe bloquear aunque la similitud sea alta",
            motivoBloqueoPorAntiPatron(candidata, perdidaDsLejos, 0.995f)
        )
        val perdidaOtraAccion = firma(action = TradeAction.SELL, ds = 0.10f, dr = 0.50f)
        assertNull(
            "Distinta accion no debe bloquear",
            motivoBloqueoPorAntiPatron(candidata, perdidaOtraAccion, 0.995f)
        )
        val perdidaOtroRegimen = firma(ds = 0.10f, dr = 0.50f, sideways = true)
        assertNull(
            "Distinto regimen lateral no debe bloquear",
            motivoBloqueoPorAntiPatron(candidata, perdidaOtroRegimen, 0.995f)
        )
    }

    // ------------------------------------------------------------------
    // 5. La busqueda en memoria: ultimas 20 y maximo estricto
    // ------------------------------------------------------------------

    @Test
    fun laBusquedaSoloMiraLasUltimasVeintePerdidas() {
        val candidata = firma(ds = 0.50f, dr = 0.50f)
        // 25 perdidas identicas a la candidata, pero las 5 primeras son las unicas identicas y
        // quedan fuera de la ventana de 20.
        val memoria = mutableListOf<TradeContextSignature>()
        repeat(5) { memoria.add(firma(ds = 0.50f, dr = 0.50f)) }        // fuera de ventana
        repeat(20) { memoria.add(firma(ds = 0.20f, dr = 0.80f)) }      // dentro, distintas

        val (sim, mejor) = mejorCoincidenciaEnMemoria(candidata, memoria)
        assertNotNull(mejor)
        assertTrue(
            "Debe comparar contra las ultimas 20, no contra todas: sim=$sim",
            sim < 0.99f
        )
    }

    @Test
    fun laBusquedaDevuelveElMaximoEstricto() {
        val candidata = firma(ds = 0.50f, dr = 0.50f)
        val muyParecida = firma(ds = 0.51f, dr = 0.50f)
        val menosParecida = firma(ds = 0.60f, dr = 0.50f)
        val (sim, mejor) = mejorCoincidenciaEnMemoria(candidata, listOf(menosParecida, muyParecida))
        assertEquals("Debe elegir la mas parecida", muyParecida, mejor)
        assertEquals("La similitud debe ser la de la elegida", similitudDeFirmas(candidata, muyParecida), sim)
    }

    @Test
    fun conMemoriaVaciaLaSimilitudEsCero() {
        val (sim, mejor) = mejorCoincidenciaEnMemoria(firma(), emptyList())
        assertEquals("Sin memoria la similitud maxima es 0", 0f, sim)
        assertNull("Sin memoria no hay coincidencia", mejor)
    }

    // ------------------------------------------------------------------
    // 6. El umbral sigue congelado
    // ------------------------------------------------------------------

    @Test
    fun elUmbralSigueEn099() {
        assertEquals(
            "ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD debe seguir en 0.99f",
            0.99f,
            AdaptiveLearningEngine.ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD
        )
    }
}
