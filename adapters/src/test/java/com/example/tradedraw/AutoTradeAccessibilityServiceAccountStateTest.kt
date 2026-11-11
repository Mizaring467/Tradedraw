package com.example.tradedraw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trasladada desde `AutonomousSafetyAndAssetScaleTest` (app) al extraer
 * `AutoTradeAccessibilityService` a `:adapters`: el setter de `isDemoAccount` es `internal`, por lo
 * que la prueba que lo ejerce debe vivir en el módulo que lo declara. El resto de esa suite sigue en
 * app porque ejercita `TradingEngine`.
 */
class AutoTradeAccessibilityServiceAccountStateTest {

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
}
