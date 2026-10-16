package com.example.tradedraw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R23A Iteracion 3: sintaxis estricta del JSON de `/status` (RFC 8259).
 *
 * REGRESION QUE LO MOTIVA. En la iteracion 2 se introdujeron comentarios `//` explicativos DENTRO del
 * literal multilinea (`""" ... """`) que construye la respuesta de `/status`. En Kotlin esos `//` NO
 * son comentarios: son texto del string y se emitieron literalmente sobre el HTTP, de modo que la
 * respuesta dejo de ser JSON valido y `json.loads`/`jq`/`ConvertFrom-Json` fallaban con
 * "Expecting property name enclosed in double quotes". Ninguna suite lo detecto porque NADIE parseaba
 * el literal completo: los tests del bridge comprobaban campos sueltos, nunca el documento entero.
 *
 * QUE HACE ESTE TEST AL RESPECTO. No se limita a buscar el texto `//` (eso solo cubre el caso que ya
 * ocurrio, no la clase de problema). EXTRAE el literal multilinea del fuente, sustituye las
 * interpolaciones `${...}` y `$var` por valores JSON validos, y lo mete en un parser real. Asi falla
 * ante CUALQUIER cosa que invalide el documento: comentarios, comas colgantes, llaves sin cerrar,
 * comillas sin escapar... que es la clase entera de regresiones, no solo una instancia.
 *
 * POR QUE SE LEE EL FUENTE Y NO SE LLAMA A `buildStatusJson()`. Es una funcion privada de instancia y
 * `TradeDrawHttpBridge` exige un `OverlayService` (Android) en el constructor: no hay forma de
 * invocarla en un test JVM de unidad. La extraccion del literal es el sustituto honesto: prueba
 * exactamente el texto que se emitira, que es donde vivia el fallo. Mismo enfoque que
 * `ModeTransitionAuditTest` para sus invariantes estructurales.
 */
class StatusJsonStrictSyntaxTest {

    /** Ruta del fuente, relativa al directorio de trabajo de los tests (`app/`). */
    private val fuente = File("src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt")

    /**
     * Extrae el interpolated-string que `buildStatusJson()` devuelve y lo convierte en un documento
     * JSON concreto: `"${expr}"` -> `"P"` (string) y `${expr}` / `$var` -> `0` (numero).
     *
     * El orden de las sustituciones importa: primero las interpolaciones entre comillas (que son
     * strings) y solo despues las sueltas, o una comilla de cierre se comeria el resto.
     */
    private fun renderStatusJson(): String {
        val texto = fuente.readText()
        val desdeFuncion = texto.substring(texto.indexOf(FUNCTION_SIGNATURE))
        val marca = "return \"\"\""
        val inicio = desdeFuncion.indexOf(marca) + marca.length
        val literal = desdeFuncion.substring(inicio, desdeFuncion.indexOf("\"\"\"", inicio))

        return literal
            .replace(Regex("""\"\$\{[^}]*\}\""""), "\"P\"")
            .replace(Regex("""\"\$[A-Za-z_][\w.]*\""""), "\"P\"")
            .replace(Regex("""\$\{[^}]*\}"""), "0")
            .replace(Regex("""\$[A-Za-z_][\w.]*"""), "0")
    }

    @Test
    fun elStatusJsonParseaConUnParserEstricto() {
        assumeTrue("Sin fuentes disponibles: se omite, no se declara PASS", fuente.isFile)

        // Si el literal trae un comentario `//` (la regresion de la iter 2) o cualquier otro defecto
        // de sintaxis, esta linea lanza JSONException y el test falla con el mensaje del parser.
        val obj = JSONObject(renderStatusJson())

        assertTrue("el documento debe ser un objeto JSON", obj.length() > 0)
    }

    @Test
    fun elStatusJsonNoLlevaComentariosDentroDelLiteral() {
        assumeTrue("Sin fuentes disponibles: se omite", fuente.isFile)

        // Comprobacion directa sobre el literal: en Kotlin un `//` dentro de `"""` viaja al cliente.
        // Se revisa linea a linea y se ignoran las URLs (`http://`), que si son JSON valido.
        val lineas = renderStatusJson().split("\n")
        val conComentario = lineas.filter { linea ->
            val sinUrl = linea.replace(Regex("""\w+://"""), "")
            sinUrl.contains("//")
        }

        assertTrue(
            "Ningun `//` puede quedar dentro del literal JSON. Culpables:\n" +
                conComentario.joinToString("\n") { "  ${it.trim()}" },
            conComentario.isEmpty()
        )
    }

    @Test
    fun elStatusExponeObservedStakeYEffectiveStakeComoNumeros() {
        assumeTrue("Sin fuentes disponibles: se omite", fuente.isFile)

        val risk = JSONObject(renderStatusJson()).getJSONObject("risk")

        // Deben existir y ser NUMEROS, no cadenas: el validador los compara numericamente y una
        // cadena pasaria desapercibida hasta romper la comparacion en el lado del pipeline.
        assertTrue("observedStake presente", risk.has("observedStake"))
        assertTrue("effectiveStake presente", risk.has("effectiveStake"))
        assertFalse("observedStake no puede ser cadena", risk.get("observedStake") is String)
        assertFalse("effectiveStake no puede ser cadena", risk.get("effectiveStake") is String)

        // Y el resto del contrato que las iteraciones 1-2 fijaron, para que no se pierda por accidente
        // al editar el literal.
        assertTrue("investmentAmount sigue expuesto", risk.has("investmentAmount"))
        assertTrue("martingaleStatus sigue expuesto", risk.has("martingaleStatus"))
    }

    @Test
    fun elStatusConservaLosCamposDeTrazabilidadDeBuild() {
        assumeTrue("Sin fuentes disponibles: se omite", fuente.isFile)

        // El criterio de la Seccion 5 de R23 (codigo candidato = APK = proceso) depende de estos
        // campos: si el JSON se corrompe, el validador pierde la capacidad de certificar la APK.
        val raiz = JSONObject(renderStatusJson())

        assertEquals("cadena", "P", raiz.getString("gitSha"))
        assertEquals("cadena", "P", raiz.getString("buildId"))
        assertTrue("versionCode presente", raiz.has("versionCode"))
        assertTrue("versionName presente", raiz.has("versionName"))
        assertTrue("isDemoAccount presente", raiz.has("isDemoAccount"))
    }

    @Test
    fun ningunOtroLiteralJsonDelPuenteLlevaComentarios() {
        assumeTrue("Sin fuentes disponibles: se omite", fuente.isFile)

        // La iteracion 2 metio el comentario solo en `/status`, pero el bug es de CLASE: cualquier
        // literal `"""` del puente puede repetirlo. Se auditan todos los del archivo.
        val texto = fuente.readText()
        val culpables = mutableListOf<String>()
        var i = 0
        while (i < texto.length) {
            val abre = texto.indexOf("\"\"\"", i)
            if (abre < 0) break
            val cierra = texto.indexOf("\"\"\"", abre + 3)
            if (cierra < 0) break
            val literal = texto.substring(abre + 3, cierra)
            if (literal.trimStart().startsWith("{")) {
                literal.split("\n").forEachIndexed { n, linea ->
                    val sinUrl = linea.replace(Regex("""\w+://"""), "")
                    if (sinUrl.contains("//")) {
                        val numero = texto.substring(0, abre).count { it == '\n' } + n + 1
                        culpables.add("linea ~$numero: ${linea.trim()}")
                    }
                }
            }
            i = cierra + 3
        }

        assertTrue(
            "Ningun literal JSON del puente puede contener `//`. Culpables:\n" +
                culpables.joinToString("\n"),
            culpables.isEmpty()
        )
    }

    @Test
    fun elStatusExponeElSubmodoActivo() {
        assumeTrue("Sin fuentes disponibles: se omite", fuente.isFile)

        // R23B Iter 1: `/status` debe exponer el submodo para que la telemetria pueda auditar de forma
        // continua que submodo esta activo (CONSERVATIVE / YOLO / SNIPER). Sin este campo, el
        // experimento de YOLO no es atribuible: no se puede demostrar que la ventana corrio en YOLO.
        val raiz = JSONObject(renderStatusJson())

        assertTrue("submode presente", raiz.has("submode"))
        // `submode` ES una cadena (nombre del enum), a diferencia de los campos numericos.
        assertEquals("submode es cadena", "P", raiz.getString("submode"))
        assertTrue("mode sigue presente", raiz.has("mode"))
        assertTrue("strategy sigue presente", raiz.has("strategy"))
    }

    private companion object {
        const val FUNCTION_SIGNATURE = "private fun buildStatusJson(): String {"
    }
}
