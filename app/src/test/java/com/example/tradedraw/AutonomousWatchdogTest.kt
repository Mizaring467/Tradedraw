package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R26: caducidad de `AUTONOMOUS` armado desde el bridge.
 *
 * El caso que cierra es el de R25/W02: motor armado por bridge, host sin red, sin sondeo. El
 * motor debe desarmarse solo pasado el tope, y no antes.
 */
class AutonomousWatchdogTest {

    private val timeoutMs = 10 * 60 * 1000L
    private lateinit var watchdog: AutonomousWatchdog

    @Before
    fun setUp() {
        watchdog = AutonomousWatchdog(timeoutMs)
    }

    @Test
    fun armaPorBridgeYExpiraPasadoElTope() {
        val armado = 1_000_000L
        watchdog.arm(armado, byBridge = true)

        assertFalse("Justo al armar no puede caducar", watchdog.shouldExpire(armado))
        assertFalse("Un segundo antes del tope sigue vivo", watchdog.shouldExpire(armado + timeoutMs - 1))
        assertTrue("Alcanzado el tope debe caducar", watchdog.shouldExpire(armado + timeoutMs))
    }

    @Test
    fun elHeartbeatReiniciaElContador() {
        val armado = 1_000_000L
        watchdog.arm(armado, byBridge = true)

        val ultimoContacto = armado + (timeoutMs / 2)
        watchdog.heartbeat(ultimoContacto)

        assertFalse(
            "El silencio se mide desde el ultimo contacto, no desde el armado",
            watchdog.shouldExpire(ultimoContacto + timeoutMs - 1)
        )
        assertTrue(
            "Pasado el tope desde el ultimo contacto si caduca",
            watchdog.shouldExpire(ultimoContacto + timeoutMs)
        )
        assertEquals("El silencio reportado debe ser el real", timeoutMs, watchdog.silenceMs(ultimoContacto + timeoutMs))
    }

    @Test
    fun armadoDesdeElHudNoCaducaNunca() {
        watchdog.arm(1_000_000L, byBridge = false)
        watchdog.heartbeat(1_000_000L)

        assertFalse(
            "Sin dependencia de un host externo no se caduca una sesion del operador",
            watchdog.shouldExpire(1_000_000L + timeoutMs * 100)
        )
        assertEquals("No hay silencio que medir", 0L, watchdog.silenceMs(1_000_000L + timeoutMs * 100))
    }

    @Test
    fun desarmarDesactivaLaCaducidad() {
        watchdog.arm(1_000_000L, byBridge = true)
        watchdog.disarm()

        assertFalse(
            "Tras desarmar no puede quedar una caducidad pendiente",
            watchdog.shouldExpire(1_000_000L + timeoutMs * 2)
        )
    }

    @Test
    fun heartbeatSinArmarNoActivaLaCaducidad() {
        watchdog.heartbeat(5_000_000L)
        assertFalse("Sin armado previo no hay caducidad", watchdog.shouldExpire(5_000_000L + timeoutMs * 2))
    }
}
