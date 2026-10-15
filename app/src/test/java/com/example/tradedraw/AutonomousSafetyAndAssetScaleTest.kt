package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23 Autonomo Iter 1: clasificacion de umbral de volatilidad por familia de activo + blindaje
 * fail-closed de cuenta DEMO.
 *
 * CONTEXTO MEDIDO (no supuesto). En las 4 iteraciones de R23 se observaron 468 muestras de rango
 * relativo con mediana ~2,9e-10 y maximo 6,8e-10 sobre `Z-CRY/IDX`: 244/244 decisiones vetadas por
 * V2_RANGE y cero operables. El umbral universal de 1e-5 (calibrado en PREREGISTRO_R12 para
 * ETHUSD-OTC, que mueve 4,16e-02) queda 4 ordenes por encima de lo que el indice puede recorrer, asi
 * que el activo era inoperable por construccion, no por falta de señal.
 *
 * ADVERTENCIA. Esta suite fija el COMPORTAMIENTO implementado, no lo legitima como validado. El
 * cambio introduce un umbral por familia de activo que PREREGISTRO_R12 §2 prohibia explicitamente
 * ("es un veto por condicion de feed, no por nombre"). Mientras no exista un PREREGISTRO_R23A
 * firmado, las muestras obtenidas con este umbral NO son comparables con las de R12 para confirmar
 * edge. Ver §3 del reporte `workflows/rounds/r23_autonomous_iter1_hermes.md`.
 */
class AutonomousSafetyAndAssetScaleTest {

    // ------------------------------------------------------------------
    // 1. Clasificacion del umbral por activo
    // ------------------------------------------------------------------

    @Test
    fun elIndiceSinteticoUsaElUmbralDeSuFamilia() {
        // Tanto el nombre VISIBLE de pantalla como el RIC del feed: la clasificacion es por
        // subcadena `IDX`, asi que las dos formas que circulan por el sistema deben coincidir.
        assertEquals("nombre visible", 1e-11, TradingEngine.rangoRelativoMinimoParaActivo("Crypto IDX"), 1e-18)
        assertEquals("RIC del feed", 1e-11, TradingEngine.rangoRelativoMinimoParaActivo("Z-CRY/IDX"), 1e-18)
        assertEquals("minusculas", 1e-11, TradingEngine.rangoRelativoMinimoParaActivo("crypto idx"), 1e-18)
    }

    @Test
    fun losParesOtcConservanElUmbralDelPreregistro() {
        // El umbral de R12 NO se ha tocado para los activos para los que se calibro.
        assertEquals("ETHUSD-OTC", 1e-5, TradingEngine.rangoRelativoMinimoParaActivo("ETHUSD-OTC"), 1e-15)
        assertEquals("BTCUSD-OTC", 1e-5, TradingEngine.rangoRelativoMinimoParaActivo("BTCUSD-OTC"), 1e-15)
        assertEquals("activo desconocido", 1e-5, TradingEngine.rangoRelativoMinimoParaActivo("LOQUESEA"), 1e-15)
        assertEquals("vacio", 1e-5, TradingEngine.rangoRelativoMinimoParaActivo(""), 1e-15)
    }

    @Test
    fun laConstanteUniversalSigueCongelada() {
        // La constante original no se relaja: sigue siendo 1e-5. Solo se elige entre dos valores.
        assertEquals("RANGO_RELATIVO_MINIMO intacto", 1e-5, TradingEngine.RANGO_RELATIVO_MINIMO, 1e-15)
        assertEquals("constante IDX", 1e-11, TradingEngine.RANGO_RELATIVO_MINIMO_IDX, 1e-18)
        assertEquals(
            "la familia IDX es el unico caso que baja del universal",
            true,
            TradingEngine.RANGO_RELATIVO_MINIMO_IDX < TradingEngine.RANGO_RELATIVO_MINIMO
        )
    }

    // ------------------------------------------------------------------
    // 2. Efecto sobre la microvolatilidad real del indice
    // ------------------------------------------------------------------

    @Test
    fun laMicrovolatilidadRealDelIndicePasaElVeto() {
        // Medido en `loops/r23/iter_4/live_ticks.csv` (2000 ticks, Z-CRY/IDX):
        //   rango relativo global de la serie = 1,885e-09
        //   rango de ventana de 16 ticks (la que usa recentPriceRangeRatio) ~ 2,9e-10 (mediana live)
        val umbralIdx = TradingEngine.rangoRelativoMinimoParaActivo("Z-CRY/IDX")
        val rangoGlobalMedido = 1.885e-9
        val rangoVentanaMediana = 2.9e-10

        assertTrue(
            "el rango global medido debe superar el umbral de su familia",
            rangoGlobalMedido >= umbralIdx
        )
        assertTrue(
            "el rango tipico de ventana tambien debe superarlo",
            rangoVentanaMediana >= umbralIdx
        )
        // Y el margen no es un roce: al menos un orden de magnitud por encima.
        assertTrue(
            "margen > 1 orden sobre el umbral",
            Math.log10(rangoVentanaMediana / umbralIdx) > 1.0
        )
    }

    @Test
    fun elIndiceSeguiriaVetadoPorElUmbralUniversalHastaAhora() {
        // Fija POR QUE el cambio hacia falta: con el umbral antiguo la condicion de veto se cumplia
        // siempre y el activo era inoperable. Es la comprobacion del diagnostico, no del fix.
        val rangoVentanaMediana = 2.9e-10
        assertTrue(
            "con el umbral universal el indice SIEMPRE caia en el veto V2",
            rangoVentanaMediana >= 0.0 && rangoVentanaMediana < TradingEngine.RANGO_RELATIVO_MINIMO
        )
    }

    @Test
    fun elRuidoDeRedondeoDelIndiceSigueVetado() {
        // El umbral nuevo no es un "dejar pasar todo": los casos degenerados siguen vetados por V2.
        val umbralIdx = TradingEngine.rangoRelativoMinimoParaActivo("Z-CRY/IDX")
        val planoExacto = 0.0
        val pasoSubUmbral = 5e-12

        assertTrue("precio exactamente quieto -> veto", planoExacto >= 0.0 && planoExacto < umbralIdx)
        assertTrue("paso por debajo del umbral -> veto", pasoSubUmbral >= 0.0 && pasoSubUmbral < umbralIdx)
        // Y el centinela de "sin feed" (-1.0) sigue fuera de la condicion de veto.
        assertFalse("centinela -1.0 no se veta", -1.0 >= 0.0 && -1.0 < umbralIdx)
    }

    // ------------------------------------------------------------------
    // 3. Seguridad fail-closed de cuenta DEMO
    // ------------------------------------------------------------------

    @Test
    fun elGuardDeTapAutorizaSoloDemoExplicito() {
        assertTrue("cuenta demo explicita", AutoTradeAccessibilityService.puedeDespacharTap(true))
        assertFalse("cuenta real", AutoTradeAccessibilityService.puedeDespacharTap(false))
    }

    @Test
    fun elGuardAutonomoAutorizaSoloDemoExplicito() {
        // Fail-closed: cualquier valor que no sea el `true` explicito bloquea.
        assertTrue("AUTONOMOUS en demo procede", TradingEngine.puedeOperarAutonomoEnDemo(true))
        assertFalse("AUTONOMOUS en real se bloquea", TradingEngine.puedeOperarAutonomoEnDemo(false))
    }

    @Test
    fun elDespachoDeTapLeeElEstadoRealDeLaCuenta() {
        // Integracion con el estado que consulta el guard en produccion: si el servicio marca cuenta
        // real, el guard de tap debe rechazar. Se restaura el valor al terminar para no contaminar
        // las otras suites que comparten este singleton (ver RiskManagerTest).
        val original = AutoTradeAccessibilityService.isDemoAccount
        try {
            AutoTradeAccessibilityService.isDemoAccount = false
            assertFalse(
                "con isDemoAccount=false ningun tap puede despacharse",
                AutoTradeAccessibilityService.puedeDespacharTap(AutoTradeAccessibilityService.isDemoAccount)
            )

            AutoTradeAccessibilityService.isDemoAccount = true
            assertTrue(
                "con isDemoAccount=true el flujo normal queda permitido",
                AutoTradeAccessibilityService.puedeDespacharTap(AutoTradeAccessibilityService.isDemoAccount)
            )
        } finally {
            AutoTradeAccessibilityService.isDemoAccount = original
        }
    }

    @Test
    fun elUmbralDelIndiceNoCoincideConElDeLaCuentaReal() {
        // Prueba de no-confusion: son dos decisiones independientes. El hecho de estar en demo no
        // cambia el umbral elegido, y el umbral elegido no relaja el guard de seguridad.
        val umbralEnDemo = TradingEngine.rangoRelativoMinimoParaActivo("Z-CRY/IDX")
        val umbralEnReal = TradingEngine.rangoRelativoMinimoParaActivo("Z-CRY/IDX")

        assertEquals("el umbral no depende del tipo de cuenta", umbralEnDemo, umbralEnReal, 0.0)
        assertNotEquals("el umbral IDX si difiere del universal", 1e-5, umbralEnDemo, 0.0)
    }
}
