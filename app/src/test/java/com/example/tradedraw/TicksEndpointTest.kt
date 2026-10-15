package com.example.tradedraw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R23 Iteracion 4: contrato de `GET /ticks` (buffer circular de ticks validados).
 *
 * La razon de ser de este endpoint es cerrar el hallazgo de la Iter 3: la paridad Live <-> Replay
 * quedo "NO EVALUABLE" (0 pares) por falta de una fuente con los ticks EXACTOS de la sesion viva.
 * El pipeline descarga el CSV y lo alimenta al replay, asi que lo que se congela aqui no es cosmetico:
 *
 *  1. El CSV debe ser PARSEABLE tal cual por `loadTicks` del replay (formato `ts,ric,price`).
 *  2. La PRECISION del precio debe sobrevivir el viaje. El activo del ring (`Z-CRY/IDX`) tiene un
 *     paso por tick de ~1,6e-06 sobre ~641,87: su rango relativo entero (2,5e-09) vive en el ultimo
 *     decimal del sexto digito. Un `%.6f` lo borraria y el replay no reproduciria la anomalia.
 *  3. Es un buffer CIRCULAR: al llenarse debe descartar lo mas viejo, no crecer sin tope.
 *  4. Solo lectura: registrar en el buffer no altera ningun guard ni estado de trading.
 */
class TicksEndpointTest {

    private fun mostrar(b: TickRingBuffer) = b.tail(b.capacity)

    @Test
    fun bufferVacioDevuelveContratoVacioValido() {
        val obj = JSONObject(buildTicksJson(emptyList(), totalEnBuffer = 0))

        assertTrue("success true aunque no haya ticks", obj.getBoolean("success"))
        assertEquals("total", 0, obj.getInt("total"))
        assertEquals("totalEnBuffer", 0, obj.getInt("totalEnBuffer"))
        assertEquals("ticks vacio", 0, obj.getJSONArray("ticks").length())
    }

    @Test
    fun jsonExponeTsRicYPricePorTick() {
        val b = TickRingBuffer(10)
        b.record(1792095485676L, "Z-CRY/IDX", 641.867302)
        b.record(1792095485999L, "ETHUSD-OTC", 2450.5)

        val arr = JSONObject(buildTicksJson(b.tail(10), b.size)).getJSONArray("ticks")

        assertEquals("dos ticks", 2, arr.length())
        val primero = arr.getJSONObject(0)
        assertEquals("ts", 1792095485676L, primero.getLong("ts"))
        assertEquals("ric", "Z-CRY/IDX", primero.getString("ric"))
        assertEquals("price", 641.867302, primero.getDouble("price"), 0.0)
        assertEquals("segundo ric", "ETHUSD-OTC", arr.getJSONObject(1).getString("ric"))
    }

    @Test
    fun csvTieneCabeceraEsperadaPorElReplay() {
        val b = TickRingBuffer(10)
        b.record(1792095485676L, "Z-CRY/IDX", 641.867302)

        val lineas = buildTicksCsv(b.tail(10)).trim().split("\n")

        assertEquals("cabecera exacta que consume loadTicks", "ts,ric,price", lineas[0])
        assertEquals("una fila de datos", 2, lineas.size)
        assertEquals("fila", "1792095485676,Z-CRY/IDX,641.867302", lineas[1])
    }

    @Test
    fun csvConservaLaVariacionSubMicroDelIndice() {
        // El caso que motiva el endpoint: dos ticks consecutivos de Z-CRY/IDX separados por ~1,6e-06.
        // Con `%.6f` ambos saldrian "641.867302" y la variacion -el objeto de estudio de R23-
        // desapareceria del archivo.
        val b = TickRingBuffer(10)
        val delta = 641.867302 * 2.547e-9
        b.record(1L, "Z-CRY/IDX", 641.867302)
        b.record(2L, "Z-CRY/IDX", 641.867302 + delta)

        val filas = buildTicksCsv(b.tail(10)).trim().split("\n").drop(1)
        val p0 = filas[0].substringAfterLast(',').toDouble()
        val p1 = filas[1].substringAfterLast(',').toDouble()

        assertFalse("los dos precios NO deben colapsar al mismo texto", filas[0] == filas[1])
        assertTrue("la diferencia sobrevive al round-trip", p1 > p0)
        // La asercion es sobre el ROUND-TRIP EXACTO, no sobre el valor del delta: el delta
        // (1,63e-06 sobre 641,87) NO es representable en binario, asi que exigir su valor decimal
        // mediria el error de la aritmetica de la prueba, no el del endpoint. Lo que debe cumplirse
        // es que el precio que sale del CSV sea bit a bit el que entro.
        assertEquals("precio 0 intacto (round-trip exacto)", 641.867302, p0, 0.0)
        assertEquals("precio 1 intacto (round-trip exacto)", 641.867302 + delta, p1, 0.0)
    }

    @Test
    fun elBufferEsCircularYDescartaLoMasViejo() {
        val b = TickRingBuffer(capacity = 5)
        for (i in 1..8) b.record(i.toLong(), "Z-CRY/IDX", i.toDouble())

        assertEquals("no crece mas alla de la capacidad", 5, b.size)
        val ts = b.tail(5).map { it.ts }
        assertEquals("solo los 5 mas recientes, en orden ascendente", listOf(4L, 5L, 6L, 7L, 8L), ts)
        assertEquals("el total si cuenta todos los recibidos", 8L, b.totalRecibidos)
    }

    @Test
    fun tailDevuelveLasUltimasNEnOrdenCronologicoAscendente() {
        val b = TickRingBuffer(100)
        for (i in 1..10) b.record(i.toLong(), "Z-CRY/IDX", i.toDouble())

        val ultimas3 = b.tail(3).map { it.ts }

        assertEquals("las 3 mas recientes, ascendente (la ultima es la mas nueva)", listOf(8L, 9L, 10L), ultimas3)
    }

    @Test
    fun maxSeAcotaAlRangoValido() {
        val b = TickRingBuffer(capacity = 5)
        for (i in 1..5) b.record(i.toLong(), "Z-CRY/IDX", i.toDouble())

        assertEquals("max=0 no devuelve vacio, se acota a 1", 1, b.tail(0).size)
        assertEquals("max negativo se acota a 1", 1, b.tail(-99).size)
        assertEquals("max enorme se acota a la capacidad", 5, b.tail(999_999).size)
    }

    @Test
    fun ignoraPreciosNoFinitosParaNoInvalidarElJson() {
        // Un NaN/Infinity incrustado en el JSON lo invalidaria (JSON no los admite). El feed ya los
        // filtra antes, pero el buffer no debe ser el punto por donde se cuelen.
        val b = TickRingBuffer(10)
        b.record(1L, "Z-CRY/IDX", 641.0)
        b.record(2L, "Z-CRY/IDX", Double.NaN)
        b.record(3L, "Z-CRY/IDX", Double.POSITIVE_INFINITY)
        b.record(4L, "Z-CRY/IDX", 642.0)

        assertEquals("solo los finitos", 2, b.size)
        assertEquals("no se cuentan como recibidos", 2L, b.totalRecibidos)
        // Y el JSON sigue siendo parseable.
        assertEquals(2, JSONObject(buildTicksJson(b.tail(10), b.size)).getJSONArray("ticks").length())
    }

    @Test
    fun totalEnBufferDistingueNoHayTicksDeHayMasDeLosPedidos() {
        val b = TickRingBuffer(2000)
        for (i in 1..1200) b.record(i.toLong(), "Z-CRY/IDX", i.toDouble())

        val obj = JSONObject(buildTicksJson(b.tail(500), b.size))

        assertEquals("total = los devueltos", 500, obj.getInt("total"))
        assertEquals("totalEnBuffer = los retenidos", 1200, obj.getInt("totalEnBuffer"))
    }

    @Test
    fun elCsvQueEmiteElEndpointLoParseaElReplay() {
        // Prueba de integracion del contrato: se vuelca el CSV a disco y se verifica que sus filas
        // son exactamente lo que `ReplayBacktestTest.loadTicks` espera (3 columnas, ts y price
        // numericos, se descarta la cabecera por empezar con "ts").
        val b = TickRingBuffer(10)
        b.record(1792095485676L, "Z-CRY/IDX", 641.867302)
        b.record(1792095486000L, "Z-CRY/IDX", 641.868936)

        val tmp = File.createTempFile("ticks_r23_iter4", ".csv")
        try {
            tmp.writeText(buildTicksCsv(b.tail(10)))
            val parseadas = tmp.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("ts") && !it.startsWith("#") }
                .map { it.split(",") }
                .filter { it.size >= 3 }

            assertEquals("2 filas de ticks tras descartar cabecera", 2, parseadas.size)
            assertEquals("ts parseable", 1792095485676L, parseadas[0][0].toLongOrNull())
            assertEquals("ric", "Z-CRY/IDX", parseadas[0][1])
            assertEquals("price parseable", 641.867302, parseadas[0][2].toDoubleOrNull()!!, 0.0)
        } finally {
            tmp.delete()
        }
    }
}
