package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * R24/B1: idempotencia de liquidacion por `tradeId` + contador monotono de LOSS sin evidencia.
 *
 * MOTIVO. La liquidacion del motor corre dentro de `handler.post`, es decir, asincrona. Si otra
 * ruta liquido antes (botones del HUD, `/win` y `/loss` del bridge), `resolvePendingTradeWin/Loss`
 * devuelve `false` y el fallback `recordTradeWin/Loss(force = true)` incrementaba un trade YA
 * contado. El fallback ahora pasa por `settleTradeOnce`, que deduplica por `tradeId`.
 *
 * Estos tests viven en JVM puro: `RiskManager(null)` no toca SharedPreferences y
 * `OverlayService.instance` es null, asi que `updateHUDView()` es un no-op.
 */
class LiquidationIdempotencyTest {

    private lateinit var riskManager: RiskManager

    @Before
    fun setUp() {
        riskManager = RiskManager(null)
        riskManager.baseAmount = 10.0f
        riskManager.resetStats()
        AutoTradeAccessibilityService.latestObservedBalance = 0.0
        AutoTradeAccessibilityService.observedOrderAmount = 0.0
    }

    /**
     * Test 1: dos hilos liquidando el MISMO `tradeId` a la vez.
     *
     * Es la carrera real: dos frames de vision (o vision + headless) llegan al fallback con el
     * pendiente ya resuelto. Solo una llamada puede incrementar el marcador.
     */
    @Test
    fun dosHilosMismoTradeIdIncrementanUnaSolaVez() {
        val tradeId = "trade-concurrente-1"
        val startMs = 1_700_000_000_000L
        val hilos = 2
        val listos = CountDownLatch(hilos)
        val disparo = CountDownLatch(1)
        val ganadores = AtomicInteger(0)
        val errores = AtomicInteger(0)

        val workers = (0 until hilos).map {
            Thread {
                try {
                    listos.countDown()
                    disparo.await(5, TimeUnit.SECONDS)
                    if (riskManager.settleTradeOnce(tradeId, startMs, win = true)) {
                        ganadores.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errores.incrementAndGet()
                }
            }
        }
        workers.forEach { it.start() }
        assertTrue("Los hilos deben arrancar", listos.await(5, TimeUnit.SECONDS))
        disparo.countDown()
        workers.forEach { it.join(5_000L) }

        assertEquals("Ningun hilo debe fallar", 0, errores.get())
        assertEquals("Solo una liquidacion puede ganar la carrera", 1, ganadores.get())
        assertEquals("totalWins debe incrementar exactamente 1", 1, riskManager.totalWins)
    }

    /** Test 2: flujo normal, una sola llamada, incrementa 1 y devuelve true. */
    @Test
    fun flujoNormalIncrementaUnaVez() {
        val gano = riskManager.settleTradeOnce("trade-normal-1", 1_700_000_000_100L, win = true)

        assertTrue("La primera liquidacion debe ganar", gano)
        assertEquals("totalWins debe ser 1", 1, riskManager.totalWins)
        assertEquals("totalLosses no debe moverse", 0, riskManager.totalLosses)
    }

    /** Test 3: `tradeId` distinto no se bloquea: dos trades legitimos cuentan 2. */
    @Test
    fun tradeIdDistintoIncrementaDosVeces() {
        val primera = riskManager.settleTradeOnce("trade-A", 1_700_000_000_200L, win = true)
        val segunda = riskManager.settleTradeOnce("trade-B", 1_700_000_000_260L, win = true)

        assertTrue("El primer trade debe contar", primera)
        assertTrue("El segundo trade es legitimo y debe contar", segunda)
        assertEquals("Dos trades distintos incrementan 2", 2, riskManager.totalWins)
    }

    /**
     * Test 4: `tradesLossSinEvidencia` es MONOTONO.
     *
     * No se reinicia en `resetStats()` (ni en setStats/correctStats/resetSession/
     * resetSniperSession). Si se reiniciara con el marcador, el ratio "cuanto del marcador se
     * apoya en saldo real" se recalcularia sobre un denominador distinto a mitad de la medicion.
     */
    @Test
    fun tradesLossSinEvidenciaNoSeReiniciaConResetStats() {
        riskManager.recordLossSinEvidencia()
        riskManager.recordLossSinEvidencia()
        assertEquals("Debe acumular 2", 2, riskManager.tradesLossSinEvidencia)

        riskManager.resetStats()

        assertEquals("El marcador si se reinicia", 0, riskManager.totalWins)
        assertEquals("El contador monotono NO se reinicia", 2, riskManager.tradesLossSinEvidencia)

        // Tampoco en el resto de resets del marcador.
        riskManager.setStats(5, 3)
        assertEquals("setStats no lo toca", 2, riskManager.tradesLossSinEvidencia)
        riskManager.correctStats(1, 1)
        assertEquals("correctStats no lo toca", 2, riskManager.tradesLossSinEvidencia)
        riskManager.resetSession(1000.0)
        assertEquals("resetSession no lo toca", 2, riskManager.tradesLossSinEvidencia)
        riskManager.resetSniperSession()
        assertEquals("resetSniperSession no lo toca", 2, riskManager.tradesLossSinEvidencia)
    }

    /**
     * Complemento de B1.c: un incremento manual se etiqueta y se contabiliza aparte, para que V1
     * pueda restarlo del lado `/status`. Sin etiqueta, el incremento seria NO EVALUABLE.
     */
    @Test
    fun incrementoManualSeEtiquetaYSeContabilizaAparte() {
        riskManager.markManualIncrement("MANUAL_BRIDGE", "WIN")
        riskManager.markManualIncrement("MANUAL_HUD", "LOSS")

        assertEquals("Un WIN manual", 1, riskManager.manualWins)
        assertEquals("Un LOSS manual", 1, riskManager.manualLosses)

        val evento = TradeEventLog.lastOf(TradeEventLog.Kind.MANUAL_INCREMENT)
        assertTrue("Debe quedar constancia del origen", evento?.detail?.contains("MANUAL_HUD") == true)

        // Los manuales son una DESAGREGACION del marcador: si sobrevivieran al reset, la resta de
        // V1 (`totalWins - manualWins`) quedaria negativa y el contraste seria invalido.
        riskManager.resetStats()
        assertEquals("resetStats limpia los manuales WIN", 0, riskManager.manualWins)
        assertEquals("resetStats limpia los manuales LOSS", 0, riskManager.manualLosses)
    }

    /**
     * La clave de idempotencia cae al timestamp de apertura cuando no hay `tradeId`: sin ese
     * respaldo todos los trades sin id colapsarian en una sola clave y solo el primero contaria.
     */
    @Test
    fun sinTradeIdSeUsaElTimestampDeApertura() {
        assertTrue("Primer trade sin id cuenta", riskManager.settleTradeOnce("", 1_700_000_000_300L, win = false))
        assertFalse("El mismo trade no cuenta dos veces", riskManager.settleTradeOnce("", 1_700_000_000_300L, win = false))
        assertTrue("Otro trade sin id si cuenta", riskManager.settleTradeOnce("", 1_700_000_000_360L, win = false))

        assertEquals("Dos trades distintos", 2, riskManager.totalLosses)
    }
}
