package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23H Fase 1: veto `VETO_BUY_EXHAUSTION_CLIMAX`.
 *
 * QUE CONGELA. El contrato exacto de la hipotesis ganadora de `R23H_BUY_FEATURE_ANALYSIS.md` §5:
 * vetar compras en UPTREND con `callPower >= 70`, y NADA MAS. La mitad de los tests son guardas de
 * alcance, porque el riesgo real de este cambio no es que el veto no dispare — es que dispare de
 * mas y arrastre el brazo SELL (que en la muestra reconciliada de R23H es el unico con PnL
 * positivo) o que se cuele en el camino de `ADAPTIVE_BLOCK`, stake o payout.
 *
 * ESTADO EPISTEMOLOGICO. El veto implementa una hipotesis a medir en vivo, no una optimizacion
 * cerrada: en la muestra R23G reconciliada (N=33) son 6 perdidas evitadas contra 1 victoria cedida,
 * sin intervalo de confianza calculado sobre el subconjunto. Estos tests fijan el COMPORTAMIENTO
 * pedido, no su eficacia.
 *
 * POR QUE FUNCION PURA. `motivoVetoPorAgotamientoDeCompra` no toca `Context`, `Handler` ni
 * `RiskManager`, asi que el contrato completo se verifica en JVM puro sin instrumentacion.
 */
class BuyExhaustionVetoTest {

    // ------------------------------------------------------------------
    // REQ: dispara en UPTREND + callPower >= 70
    // ------------------------------------------------------------------

    @Test
    fun disparaEnUptrendConCallPower70() {
        // 70 es el borde INCLUSIVO del umbral: si alguien cambia `>=` por `>`, este test cae.
        val motivo = motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 70)

        assertNotNull("UPTREND con callPower=70 debe vetar (borde inclusivo)", motivo)
        assertTrue(
            "El detail debe empezar por el token que indexa el contador de vetos: $motivo",
            motivo!!.startsWith("VETO_BUY_EXHAUSTION_CLIMAX ")
        )
        assertTrue(
            "El detail debe nombrar la condicion real medida: $motivo",
            motivo.contains("UPTREND con callPower >= 70%")
        )
        assertTrue(
            "El detail debe llevar el callPower observado para poder auditar el disparo: $motivo",
            motivo.contains("callPower=70")
        )
    }

    @Test
    fun disparaPorEncimaDelUmbral() {
        for (power in listOf(71, 85, 90, 95)) {
            val motivo = motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, power)
            assertNotNull("UPTREND con callPower=$power debe vetar", motivo)
            assertTrue(
                "El detail debe reflejar el callPower real medido ($power): $motivo",
                motivo!!.contains("callPower=$power")
            )
        }
    }

    // ------------------------------------------------------------------
    // REQ: NO vetar si callPower < 70
    // ------------------------------------------------------------------

    @Test
    fun noVetaPorDebajoDelUmbral() {
        // 69 es el borde inferior que NO debe disparar: un off-by-one aqui desactivaria compras
        // legitimas de continuacion en tendencia, que es justo lo que el motor debe seguir haciendo.
        for (power in listOf(0, 15, 50, 68, 69)) {
            assertNull(
                "UPTREND con callPower=$power NO debe vetarse (umbral 70)",
                motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, power)
            )
        }
    }

    // ------------------------------------------------------------------
    // REQ: NO vetar en DOWNTREND (ni en SIDEWAYS)
    // ------------------------------------------------------------------

    @Test
    fun noVetaEnDowntrend() {
        // En DOWNTREND el callPower es estructuralmente bajo, pero incluso con un valor alto
        // (contra-impulso alcista dentro de bajista) el veto por agotamiento NO aplica: la
        // hipotesis es especifica de techo en UPTREND y no debe generalizarse.
        for (power in listOf(0, 50, 70, 85, 95)) {
            assertNull(
                "DOWNTREND con callPower=$power NO debe vetarse: el veto es solo de UPTREND",
                motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.DOWNTREND, power)
            )
        }
    }

    @Test
    fun noVetaEnSidways() {
        // SIDEWAYS no es tendencia: el marco causal (liquidez agotandose en techo de tendencia)
        // no existe, y el motor ya tiene su propio filtro anti-chop para ese regimen.
        for (power in listOf(50, 70, 95)) {
            assertNull(
                "SIDEWAYS con callPower=$power NO debe vetarse por agotamiento",
                motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.SIDEWAYS, power)
            )
        }
    }

    // ------------------------------------------------------------------
    // REQ: SELL intacto
    // ------------------------------------------------------------------

    @Test
    fun sellQuedaIntactoEnCualquierRegimenYCallPower() {
        // GUARDA DE ALCANCE CENTRAL. El veto es exclusivo de BUY: SELL no debe ver alterado su
        // camino en ningun regimen ni con ningun callPower. Si alguien quitara el primer `if`,
        // este test cae en la combinacion UPTREND + callPower alto.
        for (trend in TrendDirection.values()) {
            for (power in listOf(0, 50, 69, 70, 71, 95)) {
                assertNull(
                    "SELL debe quedar intacto (trend=$trend, callPower=$power)",
                    motivoVetoPorAgotamientoDeCompra(TradeAction.SELL, trend, power)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Guardas del contrato: umbral congelado y alcance minimo
    // ------------------------------------------------------------------

    @Test
    fun elUmbralEstaCongeladoEn70() {
        // El valor viene del §5 del analisis y de la muestra reconciliada. Si alguien lo mueve,
        // este assert lo caza antes de que la fase 2 se analice contra un umbral distinto al
        // preregistrado.
        assertEquals(
            "VETO_BUY_UPTREND_MAX_CALL_POWER debe quedarse en 70",
            70,
            TradingEngine.VETO_BUY_UPTREND_MAX_CALL_POWER
        )
    }

    @Test
    fun elVetoEsDeterministaYNoDependeDeEstado() {
        // Funcion pura: la misma entrada da siempre la misma salida, sin estado compartido entre
        // llamadas (importante porque el motor la invoca en el camino caliente de cada senal).
        val primera = motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 80)
        val segunda = motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 80)
        assertEquals("Misma entrada debe dar identico detail", primera, segunda)

        // Y una llamada que NO veta no debe "recordar" nada de una anterior que si veto.
        motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 90)
        assertNull(
            "Una llamada previa vetada no debe contaminar la siguiente",
            motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 50)
        )
    }

    @Test
    fun elTokenDeVetoEsElPrimerTokenDelDetail() {
        // `TradeEventLog.add` indexa los contadores de VETO por el PRIMER TOKEN del detalle. Si el
        // detail empezara por otra palabra, los contadores de agotamiento quedarian inertes y la
        // fase 2 no podria medir cuantas ordenes veto este guard.
        val motivo = motivoVetoPorAgotamientoDeCompra(TradeAction.BUY, TrendDirection.UPTREND, 88)
        assertNotNull(motivo)
        assertEquals(
            "El token que indexa el contador debe ser exactamente VETO_BUY_EXHAUSTION_CLIMAX",
            "VETO_BUY_EXHAUSTION_CLIMAX",
            motivo!!.trim().substringBefore(' ')
        )
    }
}
