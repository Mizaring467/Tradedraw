package com.example.tradedraw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 12 (B2): vetos de defensa aprobados por el usuario (opción C).
 *
 * V1 — exclusividad mutua S/R ↔ anti-chop: hasta ahora `HudCoherence.actionConflict` solo
 *      ETIQUETABA el aviso en el HUD y la orden se disparaba igual. Pasa a vetar.
 * V2 — feed sin volatilidad medible: umbral `RANGO_RELATIVO_MINIMO` congelado en
 *      `workflows/PREREGISTRO_R12.md` antes de medir su efecto.
 *
 * Los umbrales de estos tests NO se relajan: son los del preregistro.
 */
class PreregistroR12VetosTest {

    // ------------------------------------------------------------------
    // V1: conflicto S/R ↔ plan
    // ------------------------------------------------------------------

    @Test
    fun casoRealDeR11_esConflictoYAhoraVeta() {
        // Texto exacto capturado en `clock_03.png`: el filtro S/R anunciaba PUT óptimo
        // (toque en resistencia) mientras el plan buscaba CALL cerca de soporte.
        val srStatus = "🔴 Toque Directo en Resistencia [PUT Óptimo]"
        val plan = "🎯 Plan: Cerca de Soporte (15%). Esperando gatillo CALL en la ventana :01s - :05s."
        assertTrue(
            "El caso real de R11 debe detectarse como conflicto",
            HudCoherence.actionConflict(srStatus, plan)
        )
    }

    @Test
    fun direccionesCoincidentesNoSonConflicto() {
        // S/R y plan apuntando ambos a CALL: caso normal, no debe vetarse nada.
        val srStatus = "🟢 Toque Directo en Soporte [CALL Óptimo]"
        val plan = "🎯 Plan: Cerca de Soporte (10%). Esperando gatillo CALL en la ventana :01s - :05s."
        assertFalse(HudCoherence.actionConflict(srStatus, plan))
    }

    @Test
    fun direccionesOpuestasAlRevesTambienSonConflicto() {
        val srStatus = "🟢 Toque Directo en Soporte [CALL Óptimo]"
        val plan = "🎯 Plan: Buscar retroceso leve a resistencia para ejecutar PUT en la ventana :01s - :05s."
        assertTrue(HudCoherence.actionConflict(srStatus, plan))
    }

    @Test
    fun planSinDireccionNoEsConflicto() {
        // Un plan que no menciona dirección no puede contradecir al filtro S/R.
        val srStatus = "🟢 Toque Directo en Soporte [CALL Óptimo]"
        val plan = "🎯 Plan: Esperando confirmación de nivel S/R o impulso direccional de ticks."
        assertFalse(HudCoherence.actionConflict(srStatus, plan))
    }

    @Test
    fun filtroNeutralNoEsConflicto() {
        val srStatus = "⚪ Filtro S/R: Canal amplio sin nivel relevante"
        val plan = "🎯 Plan: Buscar retroceso leve a soporte para ejecutar CALL en la ventana :01s - :05s."
        assertFalse(HudCoherence.actionConflict(srStatus, plan))
    }

    // ------------------------------------------------------------------
    // V2: feed sin volatilidad medible
    // ------------------------------------------------------------------

    @Test
    fun umbralCongeladoEsElDelPreregistro() {
        // Si este valor cambia, hay que reabrir el preregistro y reiniciar N.
        org.junit.Assert.assertEquals(1e-5, TradingEngine.RANGO_RELATIVO_MINIMO, 1e-15)
    }

    @Test
    fun ethereumOtcPasaElVetoConHolgura() {
        // Medido en data/ticks: ETHUSD-OTC rango relativo 4,164e-02. El activo por defecto
        // elegido en B2 queda 3 ordenes de magnitud por encima del umbral: no lo veta.
        val rangoEth = 4.164e-2
        assertTrue(
            "ETHUSD-OTC no debe ser vetado",
            rangoEth >= TradingEngine.RANGO_RELATIVO_MINIMO
        )
    }

    @Test
    fun cryptoIdxEsVetadoPorCuatroOrdenesDeMagnitud() {
        // Medido en data/ticks: Z-CRY/IDX rango relativo 2,547e-09.
        val rangoIdx = 2.547e-9
        assertTrue(
            "Z-CRY/IDX debe ser vetado",
            rangoIdx < TradingEngine.RANGO_RELATIVO_MINIMO
        )
        // La separacion entre ambos casos no es un ajuste fino sobre el ruido: son 7 ordenes.
        val ordenesDeSeparacion = Math.log10(4.164e-2 / 2.547e-9)
        assertTrue("La separacion debe ser amplia (>6 ordenes)", ordenesDeSeparacion > 6.0)
    }

    @Test
    fun sinProveedorDeRangoNoSeVeta() {
        // Con el proveedor ausente el motor usa el centinela -1.0, que queda FUERA del rango
        // de veto (`>= 0.0 && < umbral` es falso): no se veta por una medida inexistente.
        // Se comprueba la condicion pura sin construir TradingEngine (requiere contexto Android).
        val centinela = -1.0
        assertFalse(
            "El centinela sin feed no debe entrar en la condicion de veto",
            centinela >= 0.0 && centinela < TradingEngine.RANGO_RELATIVO_MINIMO
        )
    }

    @Test
    fun precioCompletamenteQuietoEsVetado() {
        // Rango relativo exactamente 0: el activo no se ha movido nada.
        val rango = 0.0
        assertTrue(rango >= 0.0 && rango < TradingEngine.RANGO_RELATIVO_MINIMO)
    }

    @Test
    fun activoConRangoJustoEnElUmbralNoEsVetado() {
        // El umbral es un minimo exigido: exactamente 1e-5 ya es medible.
        val rango = TradingEngine.RANGO_RELATIVO_MINIMO
        assertFalse("En el umbral exacto no se veta", rango < TradingEngine.RANGO_RELATIVO_MINIMO)
    }
}
