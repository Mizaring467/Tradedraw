package com.example.tradedraw

import com.example.tradedraw.core.OrderState
import org.junit.Assert.*
import org.junit.Test

/**
 * Ronda 13/A4: el estado de reposo no es un fallo.
 *
 * `clearPendingTrade()` dejaba `NO_PROCESADA`, que significa "se intento y el broker no lo
 * registro". Eso hacia que `/status` y el HUD reportasen un fallo permanente cuando no habia
 * pasado nada. El estado de reposo es `NINGUNA`.
 */
class OrderStateReposoTest {

    @Test
    fun elEstadoInicialEsNingunaNoNoProcesada() {
        val rm = RiskManager()
        assertEquals(
            "Sin operaciones aun, el estado debe ser NINGUNA (reposo), no un fallo",
            OrderState.NINGUNA, rm.orderState
        )
        assertFalse(rm.hasPendingTrade)
        assertFalse(rm.hasConfirmedTrade)
    }

    @Test
    fun noProcesadaSigueSiendoUnFalloDistinguible() {
        // La guarda importante: NO_PROCESADA debe seguir existiendo y ser distinta de NINGUNA,
        // o se perderia la capacidad de contar ordenes no registradas (A4).
        assertTrue(
            "NO_PROCESADA y NINGUNA no pueden colapsarse: una es fallo y otra es reposo",
            OrderState.NO_PROCESADA != OrderState.NINGUNA
        )
        @Suppress("UNUSED_VARIABLE")
        val todos = OrderState.values().toList()
        assertTrue("Existen los cuatro estados", todos.size == 4)
        assertNotNull(todos.find { it == OrderState.ENVIADA })
        assertNotNull(todos.find { it == OrderState.CONFIRMADA })
    }
}
