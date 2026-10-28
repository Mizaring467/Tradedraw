package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23E (REQ 1 + REQ 3): formato y consistencia del `detail` del veto `ADAPTIVE_BLOCK`.
 *
 * REGRESION QUE LO MOTIVA. Hasta R23D el evento salia como `ADAPTIVE_BLOCK <razon>`, con la
 * similitud embebida en prosa y sin el score de confluencia. El pipeline contrafactual de la
 * Fase 3 necesita ambos como campos parseables para segmentar por los buckets preregistrados
 * (similitud 90-93/93-96/96-98/98-100 y score 75-79/80-84/>=85). Sin esto la auditoria
 * contrafactual no se puede segmentar y el preregistro queda incumplido.
 *
 * QUE CONGELA. El contrato de tres formas del detalle, no una instancia:
 *  1. Con similitud y confluencia -> ambos prefijos presentes y en el orden documentado.
 *  2. Solo similitud (bloqueo por anti-patron, senal sin paso por la matriz).
 *  3. Sin ninguno de los dos (bloqueo por continuacion/reversion, que no mide similitud).
 *
 * El caso 3 es el importante: si el helper escribiera un `0` por defecto, el consumidor lo
 * contaria como "similitud medida y nula" y envenenaria la segmentacion. Debe OMITIRSE.
 */
class AdaptiveBlockTelemetryTest {

    @Test
    fun detailConSimilitudYConfluencia_llevaAmbosPrefijos() {
        val reason = "Anti-patrón perdedor recurrente (98% similitud en contexto S/R=41%) - inversión deshabilitada"
        val signalReason = "MT_REJECTION [Confluencia: 85pts]"

        val detalle = buildAdaptiveBlockDetail(reason, signalReason)

        assertTrue("Debe empezar por el token que indexa el contador de vetos",
            detalle.startsWith("ADAPTIVE_BLOCK "))
        assertTrue("Debe llevar la similitud como prefijo parseable: $detalle",
            detalle.contains("[sim:98%]"))
        assertTrue("Debe llevar el score real de confluencia: $detalle",
            detalle.contains("[Confluencia: 85pts]"))
        assertTrue("El orden especificado es [sim] antes de [Confluencia]: $detalle",
            detalle.indexOf("[sim:") < detalle.indexOf("[Confluencia:"))
        assertTrue("La razon original no debe perderse: $detalle", detalle.contains("Anti-patrón"))
    }

    @Test
    fun detailSoloConSimilitud_omiteConfluencia() {
        val reason = "Anti-patrón perdedor recurrente (94% similitud en contexto S/R=12%)"
        val signalReason = "MT_RANGE sin marcador de matriz"

        val detalle = buildAdaptiveBlockDetail(reason, signalReason)

        assertTrue("Debe llevar la similitud", detalle.contains("[sim:94%]"))
        assertTrue("Sin marcador no debe inventarse un score: $detalle",
            !detalle.contains("[Confluencia:"))
    }

    @Test
    fun detailSinSimilitudNiConfluencia_omiteAmbosPrefijos() {
        // Rama de bloqueo por continuacion contra nivel extremo: no mide similitud.
        val reason = "⛔ Autoaprendizaje: Bloqueada continuación (3 pérdidas previas) contra nivel S/R extremo"

        val detalle = buildAdaptiveBlockDetail(reason, "MT_CONFIRM")

        assertTrue("No debe fabricar una similitud inexistente: $detalle", !detalle.contains("[sim:"))
        assertTrue("No debe fabricar un score inexistente: $detalle", !detalle.contains("[Confluencia:"))
        assertEquals("ADAPTIVE_BLOCK $reason", detalle)
    }

    @Test
    fun extractSimPct_leeLaFormaRealDelMotor() {
        assertEquals(98, extractAdaptiveSimPct("Anti-patrón perdedor recurrente (98% similitud en contexto S/R=41%)"))
        assertEquals(90, extractAdaptiveSimPct("... (90% similitud ...)"))
        assertEquals(100, extractAdaptiveSimPct("... (100% similitud ...)"))
        assertNull("Sin marcador de similitud debe devolver null, no 0",
            extractAdaptiveSimPct("Bloqueada continuación contra nivel S/R extremo"))
        assertNull("Cadena vacia", extractAdaptiveSimPct(""))
    }

    @Test
    fun elDetailEsParseablePorElMonitorPython() {
        // Congela el CONTRATO que consume `extract_adaptive_block_fields` del monitor: los dos
        // regex del consumidor deben casar contra la salida real del productor. Si alguien cambia
        // el formato de un lado, este test lo caza sin necesidad de un dispositivo.
        val detalle = buildAdaptiveBlockDetail(
            "Anti-patrón perdedor recurrente (97% similitud en contexto S/R=8%)",
            "MT_REJECTION [Confluencia: 82pts]"
        )

        val reSim = Regex("""\[sim:\s*(\d+(?:\.\d+)?)%\]""")
        val reScore = Regex("""\[Confluencia:\s*(\d+(?:\.\d+)?)pts\]""")

        assertEquals("97", reSim.find(detalle)?.groupValues?.get(1))
        assertEquals("82", reScore.find(detalle)?.groupValues?.get(1))
    }
}
