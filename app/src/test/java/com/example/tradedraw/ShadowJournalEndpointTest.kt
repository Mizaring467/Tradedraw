package com.example.tradedraw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23 Iteracion 2: contrato del endpoint `/shadow_journal`.
 *
 * El validador independiente (OpenCode) consume esta respuesta por HTTP para auditar las
 * decisiones de SHADOW MODE sin sacar el archivo por ADB. El contrato que importa:
 *
 *  1. JSON ESTRICTAMENTE valido (se parsea con `org.json`, el mismo motor que usa el runtime).
 *  2. Comillas y controles de cada linea cruda del CSV escapados: el CSV de shadow incluye un
 *     campo `reason` entrecomillado (`"...\"message\"..."`), asi que sin escapado el JSON rompe.
 *  3. Caso vacio = 200 con `success:true`, `total:0`, `lines:[]` (no un error).
 *  4. `totalEnArchivo` permite distinguir "sin decisiones" de "hay mas de `max`".
 */
class ShadowJournalEndpointTest {

    private val cabecera =
        "timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason"

    @Test
    fun archivoVacioDevuelveContratoVacioValido() {
        val json = buildShadowJournalJson(emptyList())
        val obj = JSONObject(json)

        assertTrue("success debe ser true incluso sin archivo", obj.getBoolean("success"))
        assertEquals("total en caso vacio", 0, obj.getInt("total"))
        assertEquals("lines vacio", 0, obj.getJSONArray("lines").length())
    }

    @Test
    fun conservaCabeceraYLineasEnOrden() {
        val l1 = "1750000000000,2026-10-15T10:00:00,MASTER_TRADERS,M1,CALL,72.50,1.234567,UP,17,abc123,\"confluencia ok\""
        val l2 = "1750000001000,2026-10-15T10:00:01,MASTER_TRADERS,M1,PUT,68.10,1.234000,DOWN,42,def456,\"velocidad alta\""
        val lines = listOf(cabecera, l1, l2)

        val arr = JSONObject(buildShadowJournalJson(lines)).getJSONArray("lines")

        assertEquals("deben viajar las 3 lineas", 3, arr.length())
        assertEquals("la cabecera va primera", cabecera, arr.getString(0))
        assertEquals("orden preservado (l1)", l1, arr.getString(1))
        assertEquals("orden preservado (l2)", l2, arr.getString(2))
    }

    @Test
    fun escapaComillasDelCampoReasonSinRomperElJson() {
        // El logger escribe `reason` como "\"texto\"" (comillas literales dentro del CSV).
        val linea = "1750000000000,2026-10-15T10:00:00,MASTER_TRADERS,M1,CALL,72.50,1.234567,SAFE,1,id1,\"veto no: rango 2.5e-09\""

        // Sin escapado esto lanzaria JSONException: es el fallo que la prueba congela.
        val arr = JSONObject(buildShadowJournalJson(listOf(cabecera, linea))).getJSONArray("lines")

        assertEquals("la linea sale intacta tras el round-trip", linea, arr.getString(1))
    }

    @Test
    fun escapaBackslashYControles() {
        val cruda = "a\\b\tc\rd"

        val escapada = escapeJsonForJournal(cruda)

        assertTrue("barra invertida doblada", escapada.contains("\\\\"))
        assertTrue("tab escapado", escapada.contains("\\t"))
        assertTrue("CR escapado", escapada.contains("\\r"))
        assertFalse("sin CR literal", escapada.contains("\r"))
        // Round-trip completo a traves de org.json.
        val vuelta = JSONObject(buildShadowJournalJson(listOf(cruda))).getJSONArray("lines").getString(0)
        assertEquals("round-trip estable", cruda, vuelta)
    }

    @Test
    fun totalEnArchivoInformaElTamanoRealNoElRecortado() {
        val todas = List(120) { i -> "$i,x,y,z" }
        val tail = todas.takeLast(50)

        val obj = JSONObject(buildShadowJournalJson(tail, totalEnArchivo = todas.size))

        assertEquals("total = lineas devueltas", 50, obj.getInt("total"))
        assertEquals("totalEnArchivo = lineas en disco", 120, obj.getInt("totalEnArchivo"))
        assertEquals("primer elemento del tail", "70,x,y,z", obj.getJSONArray("lines").getString(0))
    }
}
