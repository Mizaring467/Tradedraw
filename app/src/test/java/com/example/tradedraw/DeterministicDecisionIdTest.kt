package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * R23 Iteracion 3: `decision_id` determinista y paridad Live <-> Replay.
 *
 * El hallazgo bloqueante de la Iteracion 2 fue que la paridad resulto "NO EVALUABLE" (0 pares)
 * porque el id vivo era `UUID.randomUUID()`. Esta prueba fija las propiedades que hacen el
 * emparejamiento posible, incluidos los dos defectos que una clave ingenua
 * `asset-epochSec-action` SI tendria (medidos sobre `loops/r23/iter_2/decisions.csv`: 17 filas ->
 * 9 llaves unicas, 8 colisionadas).
 */
class DeterministicDecisionIdTest {

    private fun det(asset: String, ts: Long, action: String) =
        TradingEngine.generateDeterministicDecisionId(asset, ts, action)

    @Test
    fun esEstableEntreLlamadas() {
        // Sin esto no hay comparacion posible: el id debe ser puro (mismo input -> mismo output).
        val a = det("Z-CRY/IDX", 1792095485676L, "BUY")
        val b = det("Z-CRY/IDX", 1792095485676L, "BUY")
        assertEquals("el id debe ser reproducible", a, b)
        assertEquals("y ser un UUID valido", a, UUID.fromString(a).toString())
    }

    @Test
    fun elMismoNombreVisibleYElMismoRicProducenElMismoId() {
        // Defecto 2 de la clave ingenua: en vivo se usa el nombre de PANTALLA, en replay el RIC del
        // feed. Sin normalizar, la MISMA decision recibe ids distintos y el par nunca se forma.
        val desdeVivo = det("Crypto IDX", 1792095485676L, "BUY")
        val desdeReplay = det("Z-CRY/IDX", 1792095485676L, "BUY")

        assertEquals("nombre visible y RIC deben colapsar al mismo id", desdeVivo, desdeReplay)
    }

    @Test
    fun laNormalizacionEsIdempotenteSobreRics() {
        // El lado replay vuelve a pasar un RIC por la normalizacion; si eso no fuera idempotente, el
        // id del replay se desviaria del vivo aunque la formula fuera la misma.
        val unaVez = det("Z-CRY/IDX", 1792095485676L, "BUY")
        val dosVeces = det(AssetRicMapper.ricParaActivoVisible("Z-CRY/IDX"), 1792095485676L, "BUY")

        assertEquals("normalizar un RIC ya canonico no debe cambiarlo", unaVez, dosVeces)
    }

    @Test
    fun segundosYMilisegundosDelMismoInstanteDanElMismoId() {
        // La spec pedia firmar con epochSecond y el replay dispone de milisegundos. Ambas entradas
        // deben converger, o los dos lados no se emparejarian por un factor 1000.
        assertEquals(
            "epochSecond y sus ms equivalentes deben coincidir",
            det("Z-CRY/IDX", 1792095485L, "BUY"),
            det("Z-CRY/IDX", 1792095485000L, "BUY")
        )
    }

    @Test
    fun ticksDistintosDeLaMismaVelaNoColisionan() {
        // Defecto 1 de la clave ingenua: el motor evalua la misma vela en ticks sucesivos, asi que
        // varios vetos comparten (activo, segundo, accion). Con resolucion de segundo se fusionan.
        val t1 = det("Z-CRY/IDX", 1792095485676L, "NONE")
        val t2 = det("Z-CRY/IDX", 1792095485000L, "NONE")

        assertNotEquals("mismo segundo, ticks distintos -> ids distintos", t1, t2)
    }

    @Test
    fun idYAccionDistintosDanIdsDistintos() {
        assertNotEquals(
            "acciones distintas",
            det("Z-CRY/IDX", 1792095485676L, "BUY"),
            det("Z-CRY/IDX", 1792095485676L, "SELL")
        )
        assertNotEquals(
            "activos distintos",
            det("Z-CRY/IDX", 1792095485676L, "BUY"),
            det("ETHUSD-OTC", 1792095485676L, "BUY")
        )
    }

    @Test
    fun laAccionNoDistingueMayusculasNiEspacios() {
        assertEquals(
            "la accion se normaliza a mayusculas sin espacios",
            det("Z-CRY/IDX", 1792095485676L, "buy"),
            det("Z-CRY/IDX", 1792095485676L, " BUY ")
        )
    }

    @Test
    fun esV3YNoV4ComoElIdAleatorioAnterior() {
        // El id anterior era UUID v4 (aleatorio). El determinista es v3 (MD5 de un nombre): que la
        // version sea 3 es la comprobacion mas barata de que NADIE reintrodujo el random.
        val id = det("Z-CRY/IDX", 1792095485676L, "BUY")

        assertEquals("debe ser un UUID derivado de nombre (v3)", 3, id[14].digitToInt())
        assertTrue("v3 estable ante reformateo", UUID.nameUUIDFromBytes("x".toByteArray()).version() == 3)
    }
}
