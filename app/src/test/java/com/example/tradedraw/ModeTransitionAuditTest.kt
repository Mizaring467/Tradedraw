package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Ronda 15 / Frente A2: auditoria del cambio de modo.
 *
 * En R14 el motor aparecio en `AUTONOMOUS` en una ronda desatendida y hubo que reconstruir el
 * origen a posteriori leyendo codigo, porque el campo `mode` era publico y escribible desde 13
 * sitios distintos. Esta clase fija por test dos invariantes que impiden que eso vuelva a pasar:
 *
 * 1. **`mode` NO es escribible desde fuera.** Solo `setTradingMode(newMode, source)` lo cambia.
 * 2. **No ha vuelto a aparecer ninguna asignacion directa** en el codigo de produccion.
 *
 * NO comprueba la POLITICA de modo (quien puede pasar a que): eso vive en `ModeChangeEventTest` y
 * en el propio motor. Aqui se audita la ESTRUCTURA que hace auditable el cambio.
 */
class ModeTransitionAuditTest {

    /** Ruta del codigo de produccion, relativa al directorio de trabajo de los tests (`app/`). */
    private val fuenteMain = File("src/main/java/com/example/tradedraw")

    @Test
    fun elModoNoSePuedeEscribirDesdeFuera() {
        val campo = TradingEngine::class.java.getDeclaredField("mode")
        assertNotNull("El campo mode debe existir", campo)
        assertTrue("mode debe ser privado", Modifier.isPrivate(campo.modifiers))

        // Kotlin puede omitir el accesor `setMode` si solo se usa dentro de la clase. Si existe,
        // TIENE que ser privado: uno publico reabriria los 13 escritores que cerro esta ronda.
        val setter = TradingEngine::class.java.declaredMethods.firstOrNull { it.name == "setMode" }
        if (setter != null) {
            assertTrue("El setter de mode no puede ser publico", Modifier.isPrivate(setter.modifiers))
        }
    }

    @Test
    fun existeElCambiadorTrazadoConFuenteObligatoria() {
        // Firma exacta: sin el `String`, el cambio deja de ser atribuible.
        val m = TradingEngine::class.java.getDeclaredMethod(
            "setTradingMode",
            AutoTradeMode::class.java,
            String::class.java
        )
        assertTrue("setTradingMode debe ser publico (es la unica puerta)", Modifier.isPublic(m.modifiers))
        assertEquals("Debe devolver Boolean: si devolviera Unit no se puede saber si cambio", java.lang.Boolean.TYPE, m.returnType)
    }

    @Test
    fun elUltimoCambioSeExponeParaStatus() {
        val campo = TradingEngine::class.java.getDeclaredField("lastModeChange")
        assertTrue("lastModeChange debe ser privado con lectura publica", Modifier.isPrivate(campo.modifiers))
        val ts = TradingEngine::class.java.getDeclaredField("lastModeChangeTs")
        assertTrue("lastModeChangeTs debe ser privado con lectura publica", Modifier.isPrivate(ts.modifiers))
        assertEquals("lastModeChangeTs es epoch ms", java.lang.Long.TYPE, ts.type)
    }

    @Test
    fun noQuedanAsignacionesDirectasDeModoEnProduccion() {
        assumeTrue("Sin fuentes disponibles: se omite, no se declara PASS", fuenteMain.isDirectory)

        // Asignacion directa del campo, sin pasar por el cambiador trazado. El unico permitido es
        // el interior de setTradingMode en TradingEngine.kt.
        val asignacion = Regex("""(?<![\w.])mode\s*=\s*(AutoTradeMode\.|newMode)""")
        val culpables = mutableListOf<String>()

        fuenteMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, linea ->
                    if (!asignacion.containsMatchIn(linea)) return@forEachIndexed
                    val permitido = f.name == "TradingEngine.kt" && linea.trim() == "mode = newMode"
                    if (!permitido) culpables.add("${f.name}:${i + 1}: ${linea.trim()}")
                }
            }

        assertTrue(
            "Toda escritura de modo debe pasar por setTradingMode(_, source). Culpables:\n" +
                culpables.joinToString("\n"),
            culpables.isEmpty()
        )
    }

    @Test
    fun overlayServiceYChatRegistranSuFuente() {
        // El setter de OverlayService es el envoltorio de UI: tambien debe exigir `source`.
        val f = File(fuenteMain, "OverlayService.kt")
        assumeTrue("Sin fuentes disponibles: se omite", f.isFile)
        val texto = f.readText()
        assertTrue(
            "setTradingMode de OverlayService debe recibir source",
            texto.contains("private fun setTradingMode(newMode: AutoTradeMode, source: String)")
        )
        assertFalse(
            "No debe quedar la firma vieja sin source",
            texto.contains("private fun setTradingMode(newMode: AutoTradeMode) {")
        )
    }
}
