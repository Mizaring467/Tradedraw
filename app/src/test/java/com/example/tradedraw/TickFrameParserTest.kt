package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contrato de integridad de datos del parser de tramas (Ronda 11).
 *
 * Regresión exacta del incidente observado en dispositivo: con `activeAsset = "Z-CRY/IDX"`
 * (precio ~641.86) el HUD llegó a mostrar `1.16603`, el precio de otro activo, porque el parser
 * aceptaba el primer RIC con rate>0 de la trama. Aquí se exige que eso NUNCA ocurra.
 */
class TickFrameParserTest {

    private val idx = "Z-CRY/IDX"
    private val eth = "ETHUSD-OTC"

    @Test
    fun activoPrimeroEnTramaMultiAsset_devuelveElActivo() {
        val frame = """{"data":{"assets":[
            {"ric":"$idx","rate":641.867319},
            {"ric":"$eth","rate":1049.51}
        ]}}"""

        val tick = TickFrameParser.parse(frame, idx)

        assertNotNull("Debe emitir el activo propio", tick)
        assertEquals(idx, tick!!.asset)
        assertEquals(641.867319, tick.price, 1e-9)
    }

    @Test
    fun activoSegundoEnTramaMultiAsset_devuelveElActivoYNoElPrimero() {
        // Caso real del incidente: el activo ajeno venía primero y se emitía su precio.
        val frame = """{"data":{"assets":[
            {"ric":"EUR/USD","rate":1.16603},
            {"ric":"$idx","rate":641.867319}
        ]}}"""

        val tick = TickFrameParser.parse(frame, idx)

        assertNotNull(tick)
        assertEquals(idx, tick!!.asset)
        assertEquals(641.867319, tick.price, 1e-9)
    }

    @Test
    fun activoAusenteEnTramaMultiAsset_noEmiteNada() {
        // Si el activo propio no está en la trama, NO se emite el precio de otro (antes sí).
        val frame = """{"data":{"assets":[
            {"ric":"EUR/USD","rate":1.16603},
            {"ric":"$eth","rate":1049.51}
        ]}}"""

        assertNull("Nunca debe emitirse el precio de un activo ajeno", TickFrameParser.parse(frame, idx))
    }

    @Test
    fun formatoPhoenixV2Posicional_respetaElActivo() {
        val frame = """[null,"3","rates","rates",{"response":{"data":[
            {"assets":[{"ric":"$idx","rate":641.8673}]}
        ]}}]"""

        val tick = TickFrameParser.parse(frame, idx)

        assertNotNull(tick)
        assertEquals(idx, tick!!.asset)
        assertEquals(641.8673, tick.price, 1e-9)
    }

    @Test
    fun formatoPhoenixV2_conActivoAjeno_noEmiteNada() {
        val frame = """[null,"4","rates","rates",{"response":{"data":[
            {"assets":[{"ric":"EUR/USD","rate":1.16603}]}
        ]}}]"""

        assertNull(TickFrameParser.parse(frame, idx))
    }

    @Test
    fun tramaEscalarSinRic_seAtribuyeAlActivoActivo() {
        // El socket está suscrito por RIC: una trama escalar pertenece al activo activo.
        val tick = TickFrameParser.parse("""{"rate":641.867319}""", idx)

        assertNotNull(tick)
        assertEquals(idx, tick!!.asset)
        assertEquals(641.867319, tick.price, 1e-9)
    }

    @Test
    fun tramaConVariosPreciosSinRic_esAmbiguaYNoEmite() {
        // Contenedor anónimo (topic global `rates`): no se puede atribuir a ningún activo.
        val frame = """{"data":[{"rate":641.86},{"rate":1.166}]}"""

        assertNull(TickFrameParser.parse(frame, idx))
    }

    @Test
    fun prefijoSocketIo_seProcesaCorrectamente() {
        val frame = """42["tick",{"ric":"$idx","rate":641.8673}]"""

        val tick = TickFrameParser.parse(frame, idx)

        assertNotNull(tick)
        assertEquals(idx, tick!!.asset)
        assertEquals(641.8673, tick.price, 1e-9)
    }

    @Test
    fun precioCeroONegativo_noEmite() {
        assertNull(TickFrameParser.parse("""{"ric":"$idx","rate":0}""", idx))
        assertNull(TickFrameParser.parse("""{"ric":"$idx","price":-5}""", idx))
    }

    @Test
    fun activoVacio_noEmite() {
        assertNull(TickFrameParser.parse("""{"ric":"$idx","rate":641.8}""", "   "))
    }

    @Test
    fun coincidenciaDeActivoIgnoraMayusculas() {
        val tick = TickFrameParser.parse("""{"ric":"z-cry/idx","rate":641.867319}""", "Z-CRY/IDX")

        assertNotNull(tick)
        assertEquals(641.867319, tick!!.price, 1e-9)
    }

    @Test
    fun assetReportsOneMillionNeverContaminatesIdxSession() {
        // Guarda explícita pedida en el plan: 1.166 jamás se emite con activeAsset = Z-CRY/IDX.
        val frames = listOf(
            """{"data":{"assets":[{"ric":"EUR/USD","rate":1.16603},{"ric":"Z-CRY/IDX","rate":641.867319}]}}""",
            """{"assets":[{"ric":"EUR/USD","rate":1.16603}]}""",
            """[null,"9","rates","rates",{"response":{"data":[{"assets":[{"ric":"EUR/USD","rate":1.16603}]}]}}]"""
        )

        for (frame in frames) {
            val tick = TickFrameParser.parse(frame, "Z-CRY/IDX")
            if (tick != null) {
                assertEquals("Solo el activo propio puede emitirse", "Z-CRY/IDX", tick.asset)
                assertEquals("El precio debe ser de escala IDX", true, tick.price > 100.0)
            }
        }
    }
}
