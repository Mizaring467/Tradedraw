package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.execution.ExecutionContext
import com.example.tradedraw.execution.ExecutionPolicy
import com.example.tradedraw.execution.ExecutionPolicyResult
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * Suite de equivalencia veto-a-veto para la cascada formal de ejecución (T-3.2).
 *
 * CARVE-OUT DOCUMENTADO:
 * El sitio de veto en TradingEngine:1867 (dentro de handleSignal) emite BuyExhaustion sobre
 * señales directas y vive fuera de la cascada de 13 vetos de executeHeadlessTrade.
 * Queda fuera del inventario de ExecutionPolicy por contención de alcance.
 */
class VetoCascadeEquivalenceTest {

    private val policy = ExecutionPolicy()

    private fun baseContext(): ExecutionContext = ExecutionContext(
        action = TradeAction.BUY,
        mode = AutoTradeMode.AUTONOMOUS,
        autonomousSubMode = AutonomousSubMode.CONSERVATIVE,
        strategy = AutoTradeStrategy.MT_MASTER_COMBO,
        currentDecisionId = "test-dec-123",
        tickAgeMs = 500L,
        isFeedFresh = true,
        feedMaxAgeMs = 5000L,
        observedAsset = "Crypto IDX",
        feedAsset = "Z-CRY/IDX",
        rangoRelativo = 0.05,
        isPriceFrozen = false,
        isSyntheticOrOtc = false,
        sec = 2, // en ventana entry window (:58-:05)
        isChoppinessDetected = false,
        reasonDescription = "MT_REJECTION toque soporte",
        lastTradeResolutionTime = 0L,
        currentLossStreak = 0,
        currentTimeMs = 100_000L,
        trend = TrendDirection.UPTREND,
        callPower = 50,
        adaptiveDecision = AdaptiveDecision.Allow(TradeAction.BUY),
        capitalPolicyCanExecute = Pair(true, ""),
        srFilterStatus = "SOPORTE_VALIDO",
        actionPlan = "CALL",
        currentCandleEpochMinute = 1000L,
        isCandleAlreadyExecuted = false
    )

    @Test
    fun veto01FeedRancioDisparaConMensajeExacto() {
        // Caso A: tickAgeMs >= 5000ms
        val ctxA = baseContext().copy(tickAgeMs = 6000L)
        val resA = policy.evaluate(ctxA)
        assertTrue(resA is ExecutionPolicyResult.Vetoed)
        val vA = resA as ExecutionPolicyResult.Vetoed
        assertEquals(1, vA.vetoIndex)
        assertEquals("FEED_RANCIO ageMs=6000 activo=Crypto IDX", vA.message)
        assertNull(vA.secondaryEventKind)

        // Caso B: tickAgeMs == null -> feed mudo
        val ctxB = baseContext().copy(tickAgeMs = null)
        val resB = policy.evaluate(ctxB)
        assertTrue(resB is ExecutionPolicyResult.Vetoed)
        val vB = resB as ExecutionPolicyResult.Vetoed
        assertEquals(1, vB.vetoIndex)
        assertEquals("FEED_RANCIO ageMs=-1 activo=Crypto IDX", vB.message)
        assertEquals("FEED_MUDO", vB.secondaryEventKind)
        assertEquals("activo=Crypto IDX sin_ningun_tick", vB.secondaryEventMessage)
    }

    @Test
    fun veto02PrecioCongeladoV2DisparaConFormatoExacto() {
        val asset = "Crypto IDX"
        val umbral = TradingEngine.rangoRelativoMinimoParaActivo(asset) // 1e-11
        val rango = 1e-12
        val ctx = baseContext().copy(observedAsset = asset, rangoRelativo = rango)
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(2, v.vetoIndex)
        val expected = "V2 rango=%.3e umbral=%.0e activo=%s".format(Locale.US, rango, umbral, asset)
        assertEquals(expected, v.message)
    }

    @Test
    fun veto03PrecioCongeladoDeclaradoPorWS() {
        val ctx = baseContext().copy(isPriceFrozen = true)
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(3, v.vetoIndex)
        assertEquals("CONGELADO activo=Crypto IDX", v.message)
    }

    @Test
    fun veto04DesajustePantallaVsFeed() {
        val ctx = baseContext().copy(observedAsset = "EUR/USD", feedAsset = "Crypto IDX")
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(4, v.vetoIndex)
        assertTrue(v.message.startsWith("DESAJUSTE"))
        assertTrue(v.incrementVetoActivoDesajustado)
    }

    @Test
    fun veto05VelaYaOperada() {
        val ctx = baseContext().copy(isCandleAlreadyExecuted = true, currentCandleEpochMinute = 12345L)
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(5, v.vetoIndex)
        assertEquals("VELA_YA_OPERADA minuto=12345", v.message)
    }

    @Test
    fun veto06FrancotiradorActivoSintetico() {
        val ctx = baseContext().copy(
            autonomousSubMode = AutonomousSubMode.SNIPER,
            observedAsset = "Crypto IDX",
            strategy = AutoTradeStrategy.COLOR_TREND // No exceptuada
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(6, v.vetoIndex)
        assertEquals("FRANCOTIRADOR activo=Crypto IDX", v.message)
    }

    @Test
    fun veto07FueraDeVentanaTimingSniper() {
        val ctx = baseContext().copy(sec = 35) // Fuera de ventana (:58-:03)
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(7, v.vetoIndex)
        assertEquals("FUERA_DE_VENTANA sec=35", v.message)
    }

    @Test
    fun veto08AntiChoppy() {
        val ctx = baseContext().copy(
            isChoppinessDetected = true,
            reasonDescription = "OTRA_ESTRATEGIA_SIN_EXCEPCION"
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(8, v.vetoIndex)
        assertEquals("CHOP", v.message)
    }

    @Test
    fun veto09CooldownPostResolucion() {
        val now = 100_000L
        val ctx = baseContext().copy(
            currentTimeMs = now,
            lastTradeResolutionTime = now - 5000L, // 5s < 12s minSpacing
            currentLossStreak = 0,
            autonomousSubMode = AutonomousSubMode.CONSERVATIVE
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(9, v.vetoIndex)
        assertEquals("COOLDOWN 5000ms", v.message)
    }

    @Test
    fun veto10AgotamientoDeCompraR23H() {
        val ctx = baseContext().copy(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            callPower = 95 // Exhaustion climax >= 90
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(10, v.vetoIndex)
        assertTrue(v.message.contains("BUY_EXHAUSTION_CLIMAX"))
    }

    @Test
    fun veto11BloqueoAdaptativo() {
        val ctx = baseContext().copy(
            adaptiveDecision = AdaptiveDecision.Block(
                reason = "Firma_Perdedora_123"
            ),
            reasonDescription = "MT_REJECTION [Confluencia: 85pts]"
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(11, v.vetoIndex)
        assertEquals("ADAPTIVE_BLOCK [Confluencia: 85pts] Firma_Perdedora_123", v.message)
        assertNotNull(v.toastMessage)
    }

    @Test
    fun veto12RiesgoCapitalPolicy() {
        val ctx = baseContext().copy(
            capitalPolicyCanExecute = Pair(false, "STOP_LOSS_ALCANZADO"),
            autonomousSubMode = AutonomousSubMode.SNIPER
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(12, v.vetoIndex)
        assertEquals("RISK_LIMIT STOP_LOSS_ALCANZADO", v.message)
        assertEquals("engine:sniper-limite(headless)", v.disableModeReason)
    }

    @Test
    fun veto13ConflictoSROpuestoAPlanV1() {
        // Caso de conflicto: Resistencia con CALL
        val ctx = baseContext().copy(
            srFilterStatus = "Toque Directo en Resistencia [PUT Óptimo]",
            actionPlan = "CALL Fuerte"
        )
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Vetoed)
        val v = res as ExecutionPolicyResult.Vetoed
        assertEquals(13, v.vetoIndex)
        assertEquals("V1 SR=\"Toque Directo en Resistencia [PUT Óptimo]\" PLAN=\"CALL Fuerte\"", v.message)
    }

    @Test
    fun todosLosVetosPasanGeneraAllowed() {
        val ctx = baseContext()
        val res = policy.evaluate(ctx)
        assertTrue(res is ExecutionPolicyResult.Allowed)
        val allowed = res as ExecutionPolicyResult.Allowed
        assertEquals(TradeAction.BUY, allowed.finalAction)
        assertEquals(0.85f, allowed.headlessConfidence, 0.001f)
    }

    @Test
    fun precedenciaVeto1CortaAntesDeVeto2YVeto10AntesDe11() {
        // Simular condiciones simultáneas de Veto 1 y Veto 2
        val ctx1y2 = baseContext().copy(
            tickAgeMs = 9999L, // Veto 1
            rangoRelativo = 1e-15 // Veto 2
        )
        val res1y2 = policy.evaluate(ctx1y2) as ExecutionPolicyResult.Vetoed
        assertEquals("Veto 1 debe cortar antes que Veto 2", 1, res1y2.vetoIndex)

        // Simular condiciones simultáneas de Veto 10 y Veto 11
        val ctx10y11 = baseContext().copy(
            action = TradeAction.BUY,
            trend = TrendDirection.UPTREND,
            callPower = 95, // Veto 10
            adaptiveDecision = AdaptiveDecision.Block("Bloqueo_Memoria") // Veto 11
        )
        val res10y11 = policy.evaluate(ctx10y11) as ExecutionPolicyResult.Vetoed
        assertEquals("Veto 10 debe cortar antes que Veto 11", 10, res10y11.vetoIndex)
    }
}
