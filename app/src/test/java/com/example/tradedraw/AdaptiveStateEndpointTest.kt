package com.example.tradedraw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R24/B3: contrato del endpoint `GET /adaptive_state`.
 *
 * QUE FIJA. El validador necesita el volcado de la memoria adaptativa al cierre de cada ventana
 * (criterio V3 del preregistro R24) sin sacar el archivo por ADB. El contrato que importa:
 *
 *  1. La respuesta es JSON ESTRICTAMENTE valido en TODOS los casos, incluido archivo corrupto.
 *  2. `firmas` = longitud de `lossSignatures`, que es lo que V3 contrasta contra la fuente.
 *  3. Archivo ausente = 200 con `exists:false`, no un error: "no hay memoria" es un dato.
 *  4. El contenido se devuelve TAL CUAL (no normalizado), porque el endpoint es solo lectura.
 */
class AdaptiveStateEndpointTest {

    private val estadoValido = """
        {"consecutiveContinuationLosses":2,"consecutiveReversionLosses":0,
         "totalBlockedAntiPatterns":7,"totalInvertedAntiPatterns":1,
         "reversionBonusWeight":1.15,"continuationPenaltyWeight":0.9,
         "lossSignatures":[
           {"action":"BUY","trend":"DOWNTREND","distSup":0.05,"distRes":0.25,"sec":2},
           {"action":"SELL","trend":"UPTREND","distSup":0.75,"distRes":0.01,"sec":5}
         ]}
    """.trimIndent()

    @Test
    fun volcadoValidoSeIncrustaComoObjetoYCuentaFirmas() {
        val obj = JSONObject(buildAdaptiveStateJson(estadoValido, "/data/adaptive_learning_state.json"))

        assertTrue("success", obj.getBoolean("success"))
        assertTrue("exists", obj.getBoolean("exists"))
        assertTrue("parseable", obj.getBoolean("parseable"))
        assertEquals("firmas = longitud de lossSignatures", 2, obj.getInt("firmas"))
        assertEquals(
            "el estado viaja como objeto, no como string",
            2,
            obj.getJSONObject("state").getJSONArray("lossSignatures").length()
        )
    }

    @Test
    fun elContenidoNoSeNormaliza() {
        // Los pesos y el orden de claves se devuelven tal cual estaban en disco.
        val obj = JSONObject(buildAdaptiveStateJson(estadoValido))
        val estado = obj.getJSONObject("state")
        assertEquals(1.15, estado.getDouble("reversionBonusWeight"), 1e-9)
        assertEquals(7, estado.getInt("totalBlockedAntiPatterns"))
    }

    @Test
    fun archivoAusenteDevuelveContratoVacioValido() {
        val obj = JSONObject(buildAdaptiveStateJson(null, "/data/adaptive_learning_state.json"))

        assertTrue("success debe ser true sin archivo", obj.getBoolean("success"))
        assertFalse("exists debe ser false", obj.getBoolean("exists"))
        assertEquals("sin archivo no hay firmas", 0, obj.getInt("firmas"))
        assertTrue("state debe ser JSON null", obj.isNull("state"))
    }

    @Test
    fun archivoCorruptoNoRompeElJsonDeRespuesta() {
        // Un archivo truncado no puede dejar la respuesta sin parsear: el validador se quedaria
        // ciego justo cuando mas falta hace saber que el estado esta roto.
        val obj = JSONObject(buildAdaptiveStateJson("{esto no es json", "/data/x.json"))

        assertTrue("success", obj.getBoolean("success"))
        assertTrue("exists", obj.getBoolean("exists"))
        assertFalse("parseable debe ser false", obj.getBoolean("parseable"))
        assertEquals("firmas 0 cuando no parsea", 0, obj.getInt("firmas"))
        assertTrue("el texto crudo se conserva escapado", obj.getString("stateRaw").contains("esto no es json"))
    }

    @Test
    fun estadoSinLossSignaturesCuentaCero() {
        val obj = JSONObject(buildAdaptiveStateJson("""{"reversionBonusWeight":1.0}"""))
        assertTrue("parseable", obj.getBoolean("parseable"))
        assertEquals("sin arreglo de firmas el conteo es 0", 0, obj.getInt("firmas"))
    }
}
