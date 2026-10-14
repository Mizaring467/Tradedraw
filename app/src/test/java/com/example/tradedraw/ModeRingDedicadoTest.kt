package com.example.tradedraw

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ronda 18: ring DEDICADO de `MODE_CHANGE` y persistencia solo-append en disco.
 *
 * MOTIVO. En R15-A el buffer de 200 se lleno de `ASSET` y el volcado con `max=60` devolvio CERO
 * `MODE_CHANGE`: la evidencia de modo casi se perdio. Y el ring es memoria viva, asi que un cambio
 * de modo anterior a un reinicio del servicio desaparecia, que es exactamente el escenario del
 * rearme observado en R14.
 *
 * Comprueba OBSERVABILIDAD. No comprueba la politica de modo ni toca ninguna decision de trading.
 */
class ModeRingDedicadoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun limpiar() {
        TradeEventLog.persistSink = null
        TradeEventLog.clear()
    }

    // ---------- R18.2: el ring dedicado no es desplazado ----------

    @Test
    fun quinientosAssetNoDesplazanElRingDeModo() {
        TradeEventLog.clear()
        // Traza de modo que DEBE sobrevivir.
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=test")

        repeat(500) { TradeEventLog.add(TradeEventLog.Kind.ASSET, "activo=Z-CRY/IDX #$it") }

        val modos = TradeEventLog.recentModes()
        assertEquals("El ring dedicado debe conservar la traza de modo", 1, modos.size)
        assertTrue("El detalle debe seguir ahi", modos[0].detail.contains("a=AUTONOMOUS"))
    }

    @Test
    fun elRingDeModoEsIndependienteDelGeneral() {
        TradeEventLog.clear()
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")

        // El buffer general se llena hasta rebosar con ASSET.
        repeat(TradeEventLog.CAPACIDAD + 50) { TradeEventLog.add(TradeEventLog.Kind.ASSET, "x$it") }

        // El general ya perdio el evento de modo...
        assertTrue(
            "El buffer general SI debe haberlo perdido (es el bug que se corrige)",
            TradeEventLog.recent(TradeEventLog.CAPACIDAD).none { it.kind == TradeEventLog.Kind.MODE_CHANGE }
        )
        // ...pero el dedicado no.
        assertEquals("El dedicado NO debe perderlo", 1, TradeEventLog.recentModes().size)
    }

    @Test
    fun elRingDeModoRespetaSuPropiaCapacidad() {
        TradeEventLog.clear()
        repeat(TradeEventLog.MODE_CAPACIDAD + 25) {
            TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=A a=B origen=$it")
        }
        assertEquals(
            "Debe recortarse a su capacidad, no mas",
            TradeEventLog.MODE_CAPACIDAD,
            TradeEventLog.recentModes().size
        )
        // Los mas recientes sobreviven; el mas antiguo se fue.
        val detalles = TradeEventLog.recentModes().map { it.detail }
        assertTrue("El ultimo debe estar", detalles.any { it.endsWith("origen=124") })
        assertFalse("El primero debe haberse ido", detalles.any { it.endsWith("origen=0") })
    }

    // ---------- R18.2: compatibilidad de /events ----------

    @Test
    funelJsonDeEventsNoCambiaDeForma() {
        TradeEventLog.clear()
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V1 motivo")
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V2 rango=1.2")
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")

        val json = TradeEventLog.toJson(50)
        // Campos que ya consumian R14/R15: deben seguir con el mismo significado.
        assertTrue(json.contains("\"success\":true"))
        assertTrue(json.contains("\"total\":"))
        assertTrue(json.contains("\"vetoV1\":1"))
        assertTrue(json.contains("\"vetoV2\":1"))
        assertTrue(json.contains("\"events\":["))
    }

    @Test
    fun elJsonDeModeLogEsUnEndpointSeparado() {
        TradeEventLog.clear()
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")

        val json = TradeEventLog.modesToJson()
        assertTrue(json.contains("\"success\":true"))
        assertTrue(json.contains("\"capacidad\":${TradeEventLog.MODE_CAPACIDAD}"))
        assertTrue(json.contains("MODE_CHANGE"))
        // El contador por tipo se conserva.
        assertEquals(1, TradeEventLog.countOf(TradeEventLog.Kind.MODE_CHANGE))
    }

    // ---------- R18.3: persistencia en disco ----------

    @Test
    fun elDiarioEscribeYReleeUnaLinea() {
        val diario = ModeEventJournal.instalar(tmp.root)
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")

        val lineas = diario.tail(10)
        assertEquals("Debe haber una linea persistida", 1, lineas.size)
        assertTrue("Debe incluir el tipo", lineas[0].contains("MODE_CHANGE"))
        assertTrue("Debe incluir el detalle con origen", lineas[0].contains("origen=A"))
    }

    @Test
    fun elDiarioSoloPersisteModeChange() {
        val diario = ModeEventJournal.instalar(tmp.root)
        TradeEventLog.add(TradeEventLog.Kind.ASSET, "activo=X")
        TradeEventLog.add(TradeEventLog.Kind.VETO, "V1 x")
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=A a=B origen=Z")

        val lineas = diario.tail(50)
        assertEquals("Solo el cambio de modo debe persistirse", 1, lineas.size)
    }

    @Test
    fun elDiarioSobreviveAlClearDelRing() {
        // Es el punto de R18.3: el ring se vacia, el disco no.
        val diario = ModeEventJournal.instalar(tmp.root)
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=DISABLED a=AUTONOMOUS origen=A")

        TradeEventLog.clear() // simula la rotacion / el reinicio del ring

        assertEquals("El ring queda vacio", 0, TradeEventLog.recentModes().size)
        assertEquals("El diario NO", 1, diario.tail(10).size)
    }

    @Test
    fun laRotacionNoPierdeLoReciente() {
        // Tope minusculo para forzar la rotacion sin escribir 256 KB.
        val archivo = java.io.File(tmp.root, "rot.jsonl")
        val diario = ModeEventJournal(archivo, topeBytes = 200)

        var escritas = 0
        repeat(50) { i ->
            if (diario.append("{\"n\":$i}")) escritas++
        }

        assertTrue("Debe haber rotado al menos una vez", escritas > 0)
        // El fichero vivo debe existir y seguir siendo legible.
        assertTrue("El fichero vivo debe existir", archivo.exists())
        // Tras la rotacion, la ultima linea escrita debe estar en el fichero vivo.
        val ultima = diario.tail(1).firstOrNull()
        assertTrue(
            "La ultima linea debe estar en el fichero vivo (no se pierde lo reciente)",
            ultima != null && ultima.contains("\"n\":49")
        )
        // Y debe existir la rotacion.
        assertTrue("Debe existir el fichero rotado", java.io.File(tmp.root, "rot.jsonl.1").exists())
    }

    @Test
    fun unFalloDeDiscoNoPropagaNiPierdeElEvento() {
        // Directorio imposible: el sumidero debe tragarse el fallo.
        val imposible = java.io.File(tmp.root, "no\0valido")
        val diario = ModeEventJournal(imposible)
        assertFalse("Debe devolver false, no lanzar", diario.append("x"))

        // Y el evento SIGUE en el ring: un fallo de disco no puede impedir un cambio de modo.
        TradeEventLog.persistSink = { linea -> diario.append(linea) }
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=A a=B origen=C")
        assertEquals("El ring debe tenerlo igualmente", 1, TradeEventLog.recentModes().size)
    }

    @Test
    fun instalarEsIdempotenteRespectoAlFichero() {
        val d1 = ModeEventJournal.instalar(tmp.root)
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=A a=B origen=1")
        val d2 = ModeEventJournal.instalar(tmp.root) // reinstala sobre el mismo directorio
        TradeEventLog.add(TradeEventLog.Kind.MODE_CHANGE, "de=B a=C origen=2")

        assertEquals("No debe duplicar ni perder lineas", 2, d2.tail(10).size)
        assertEquals("Mismo fichero", d1.tail(10).size, d2.tail(10).size)
    }
}
