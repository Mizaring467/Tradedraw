package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 21 (Frente F - C1): Pruebas de sanitización de credenciales y tokens.
 * Asegura que ningún token ni secreto sensible se exponga íntegramente en logcat.
 */
class BinomoAuthSanitizationTest {

    @Test
    fun maskSecretConTokenLargoEnmascaraPrefijoYSufijo() {
        val realToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sometokenpayload1234567890"
        val masked = BinomoAuthActivity.maskSecret(realToken)

        assertFalse("El token completo no debe aparecer en la cadena enmascarada", masked.contains(realToken))
        assertTrue("Debe empezar con los primeros 4 caracteres", masked.startsWith("eyJh..."))
        assertTrue("Debe terminar con los últimos 4 caracteres", masked.contains("...7890"))
        assertTrue("Debe reportar la longitud", masked.contains("len=${realToken.length}"))
    }

    @Test
    fun maskSecretConTokenCortoOcultaContenido() {
        val shortToken = "secret123"
        val masked = BinomoAuthActivity.maskSecret(shortToken)

        assertFalse("El token corto no debe exponerse", masked.contains(shortToken))
        assertEquals("[PRESENTE len=9]", masked)
    }

    @Test
    fun maskSecretConCadenaVaciaIndicaVacio() {
        assertEquals("[VACÍO]", BinomoAuthActivity.maskSecret(""))
    }
}
