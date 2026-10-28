package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ronda 23D Fase 1 (REQ 3): sincronizacion del cooldown con el submodo EFECTIVO.
 *
 * REGRESION QUE LO MOTIVA. `RiskManager.currentSubMode` nace en `CONSERVATIVE` y, antes de esta
 * fase, nadie lo actualizaba al cambiar de submodo. Como `HealthSupervisor` y el endpoint
 * `/status` llamaban a `getRemainingCooldown()` SIN argumento, el fallback resolvia a
 * CONSERVATIVE y aplicaban la pausa de perdidas de ESE modo (180s) aunque el motor estuviera
 * en YOLO (35s): en runtime se publicaba un `COOLDOWN_ACTIVE: 178s` espurio.
 *
 * QUE CONGELA ESTE TEST. Dos invariantes, no una instancia concreta:
 *  1. El cooldown depende del submodo EFECTIVO: YOLO -> YOLO (35s), CONSERVATIVE -> 180s.
 *  2. El motor es la fuente de verdad: asignar `autonomousSubMode` espeja el valor al
 *     RiskManager. Si alguien vuelve a romper el puente, el test 2 falla aunque el 1 siga verde.
 *
 * NO se instancia `TradingEngine` (exige Context y CustomDrawingView de Android): el puente se
 * audita leyendo el FUENTE, mismo enfoque que `ModeTransitionAuditTest`. Es el sustituto honesto
 * porque lo que puede romperse es la LINEA que sincroniza, no la aritmetica del cooldown.
 */
class CooldownSubmodeSyncTest {

    private lateinit var riskManager: RiskManager

    private val fuenteTradingEngine =
        java.io.File("src/main/java/com/example/tradedraw/TradingEngine.kt")
    private val fuenteBridge =
        java.io.File("src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt")
    private val fuenteHealth =
        java.io.File("src/main/java/com/example/tradedraw/health/HealthSupervisor.kt")

    @Before
    fun setUp() {
        riskManager = RiskManager(null)
        riskManager.resetSession()
        riskManager.yoloLossCooldownSeconds = RiskManager.DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS
        riskManager.lossCooldownSeconds = RiskManager.DEFAULT_LOSS_COOLDOWN_SECONDS
        AutoTradeAccessibilityService.latestObservedBalance = 0.0
    }

    /**
     * Prepara un estado con racha de perdida = 1 y un trade recien despachado, que es exactamente
     * el escenario donde divergen las dos politicas (35s YOLO vs 180s CONSERVATIVE).
     */
    private fun conUnaPerdidaReciente() {
        riskManager.recordTradeSent(TradeAction.BUY)
        riskManager.recordTradeLoss()
        assertEquals("Precondicion: racha de perdida = 1", 1, riskManager.currentLossStreak)
    }

    @Test
    fun cooldownConRacha1EnYoloSeBasaEn35Segundos() {
        conUnaPerdidaReciente()
        riskManager.currentSubMode = AutonomousSubMode.YOLO

        val remaining = riskManager.getRemainingCooldown()

        assertTrue("YOLO con racha 1 debe basarse en 35s, no en los 180s de CONSERVATIVE", remaining in 30..35)
        assertTrue("No debe acercarse a la pausa larga de CONSERVATIVE", remaining < 180)
    }

    @Test
    fun cooldownConRacha1EnConservativeSeBasaEn180Segundos() {
        conUnaPerdidaReciente()
        riskManager.currentSubMode = AutonomousSubMode.CONSERVATIVE

        val remaining = riskManager.getRemainingCooldown()

        assertTrue(
            "CONSERVATIVE con racha 1 debe usar la pausa de perdidas larga",
            remaining > 170 && remaining <= 180
        )
    }

    @Test
    fun elMismoEstadoProduceCooldownsDistintosSegunElSubmodoEfectivo() {
        conUnaPerdidaReciente()

        val enYolo = riskManager.getRemainingCooldown(AutonomousSubMode.YOLO)
        val enConservative = riskManager.getRemainingCooldown(AutonomousSubMode.CONSERVATIVE)

        assertTrue("El cooldown de YOLO debe ser mucho menor que el de CONSERVATIVE", enYolo < enConservative)
    }

    @Test
    fun elSetterDelMotorEspejaElSubmodoAlRiskManager() {
        // Invariante del puente: el setter de `autonomousSubMode` debe asignar el submodo al
        // RiskManager. Se audita el FUENTE porque el constructor de TradingEngine exige Android.
        val texto = fuenteTradingEngine.readText()
        val inicioSetter = texto.indexOf("var autonomousSubMode")
        assertTrue("Debe existir la propiedad autonomousSubMode en TradingEngine", inicioSetter > 0)

        val bloqueSetter = texto.substring(inicioSetter, minOf(inicioSetter + 1200, texto.length))

        assertTrue(
            "El setter de autonomousSubMode debe sincronizar riskManager.currentSubMode",
            bloqueSetter.contains("riskManager.currentSubMode = value")
        )
    }

    @Test
    fun laRutaHttpDeSubmodoActualizaTambienElRiskManager() {
        val texto = fuenteBridge.readText()
        val idx = texto.indexOf("AutonomousSubMode.valueOf(subStr)")
        assertTrue("Debe existir la rama POST /submode", idx > 0)

        val bloque = texto.substring(idx, minOf(idx + 900, texto.length))

        assertTrue(
            "POST /submode debe espejar el submodo al RiskManager de forma explicita",
            bloque.contains("riskManager.currentSubMode = sm")
        )
    }

    @Test
    fun statusYRiesgoReportanElCooldownDelSubmodoEfectivo() {
        // `/status` debe pasar el submodo del motor, no invocar el no-arg (que dependia del
        // fallback sin sincronizar). HealthSupervisor debe consultar `riskManager.currentSubMode`.
        val bridge = fuenteBridge.readText()
        assertTrue(
            "remainingCooldown debe calcularse con el submodo del motor",
            bridge.contains("rm.getRemainingCooldown(te.autonomousSubMode)")
        )

        val health = fuenteHealth.readText()
        assertTrue(
            "HealthSupervisor debe consultar el submodo efectivo del RiskManager",
            health.contains("getRemainingCooldown(riskManager.currentSubMode)")
        )
    }
}
