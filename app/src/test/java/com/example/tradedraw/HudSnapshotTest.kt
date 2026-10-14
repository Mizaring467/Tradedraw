package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariantes de coherencia del HUD (Ronda 11, T4/T7).
 *
 * Reproduce las contradicciones observadas en dispositivo y exige que la instantánea las haga
 * IMPOSIBLES, no improbables:
 *  - termómetro 92% CALL con "Trade Activo: PUT ▼",
 *  - "Toque Directo en Soporte [CALL Óptimo]" con "Tendencia: LATERAL" y plan PUT,
 *  - velocidad negativa con flecha "▲ Impulso Alcista",
 *  - racha/termómetro pintados desde la ruta de visión retirada (50/50 por defecto).
 */
class HudSnapshotTest {

    private fun snapshot(
        hasFreshTick: Boolean = true,
        callPct: Int? = 68,
        putPct: Int? = 32,
        trend: TrendDirection = TrendDirection.UPTREND,
        streak: String? = "3T 🟢",
        pending: Boolean = false,
        pendingAction: TradeAction? = null,
        entry: Double = 0.0,
        moneyness: TradeMoneyness? = null,
        velocityLive: Boolean = true
    ) = HudSnapshot(
        assetLabel = "Z-CRY/IDX",
        friendlyAsset = "Crypto IDX",
        hasFreshTick = hasFreshTick,
        price = 641.86,
        tickAgeMs = if (hasFreshTick) 120L else 9000L,
        wsState = WebSocketState.CONNECTED,
        tickSource = "socket",
        rejectedForeignAssetTicks = 0L,
        rejectedSanityJumpTicks = 0L,
        trend = trend,
        streakBadge = streak,
        callPct = callPct,
        putPct = putPct,
        velocity = 0.01f,
        smoothedVelocity = 0.01f,
        velocityIsLive = velocityLive,
        patternName = "📈 Flujo Continuo Alcista",
        probabilityPct = 86,
        srFilterStatus = "🟢 Cerca de Soporte (12% canal)",
        actionPlan = "🎯 Plan: CALL",
        totalWins = 3,
        totalLosses = 1,
        totalVoids = 0,
        winRate = 75.0,
        martingaleBadge = "M0",
        cooldownRemainingSec = 0L,
        hasPendingTrade = pending,
        // Ronda 12 (B1): los tests existentes describen operaciones ya en curso, que es el
        // estado CONFIRMADA. El caso ENVIADA (sin confirmar) se cubre en OrderConfirmationTest.
        orderState = if (pending) RiskManager.OrderState.CONFIRMADA else RiskManager.OrderState.NO_PROCESADA,
        pendingAction = pendingAction,
        pendingElapsedSec = if (pending) 20L else 0L,
        pendingEntryPrice = entry,
        pendingMoneyness = moneyness
    )

    @Test
    fun sinTickFresco_noHayTermometro() {
        val hud = snapshot(hasFreshTick = false, callPct = null, putPct = null, streak = null, velocityLive = false)
        assertNull("Sin tick fresco no se debe fabricar un termómetro", hud.thermometroTexto)
        assertNull("Sin tick fresco no se debe fabricar una racha", hud.streakBadge)
        assertFalse(hud.velocityIsLive)
    }

    @Test
    fun termometroSiempreSuma100() {
        val hud = snapshot(callPct = 68, putPct = 32)
        assertTrue(hud.thermometroTexto!!.contains("68% CALL"))
        assertTrue(hud.thermometroTexto!!.contains("32% PUT"))
    }

    @Test
    fun termometroParcial_noSePintaMitadInventada() {
        val hud = snapshot(callPct = 68, putPct = null)
        assertNull("Con un solo lado no hay termómetro válido", hud.thermometroTexto)
    }

    @Test
    fun posicionPutConPrecioPorEncima_marcaOtmNoItm() {
        // Caso observado: chip "▼ PUT [ITM +$$$]" con el precio por encima del strike.
        val hud = snapshot(
            hasFreshTick = true,
            pending = true,
            pendingAction = TradeAction.SELL,
            entry = 641.0,
            moneyness = StrikeCalculator.moneyness(TradeAction.SELL, 641.0, 642.0)
        )
        assertEquals(TradeMoneyness.OTM, hud.pendingMoneyness)
    }

    @Test
    fun posicionCallConPrecioPorEncima_marcaItm() {
        val hud = snapshot(
            pending = true,
            pendingAction = TradeAction.BUY,
            entry = 641.0,
            moneyness = StrikeCalculator.moneyness(TradeAction.BUY, 641.0, 642.0)
        )
        assertEquals(TradeMoneyness.ITM, hud.pendingMoneyness)
    }

    @Test
    fun sinPrecioDeEntrada_noSeInventaMoneyness() {
        val hud = snapshot(pending = true, pendingAction = TradeAction.BUY, entry = 0.0, moneyness = null)
        assertNull("Sin strike no hay ITM/OTM que mostrar", hud.pendingMoneyness)
    }

    @Test
    fun tendenciaAlcistaYTermometroPutMayoritario_esContradiccion() {
        // Invariante T7: esta combinación NO debe poder generarse desde el motor.
        // buildAnalysisFromSyntheticEngine es coherente por construcción; aquí se comprueba que el
        // contrato del snapshot la detecta como incoherente para que el HUD pueda avisar.
        val hud = snapshot(trend = TrendDirection.UPTREND, callPct = 20, putPct = 80)
        assertTrue(
            "Un HUD con tendencia ALCISTA y termómetro PUT>50 es incoherente",
            HudCoherence.isIncoherent(hud)
        )
    }

    @Test
    fun tendenciaAlcistaYTermometroCallMayoritario_esCoherente() {
        val hud = snapshot(trend = TrendDirection.UPTREND, callPct = 68, putPct = 32)
        assertFalse(HudCoherence.isIncoherent(hud))
    }

    @Test
    fun toqueDeSoporteConPrecioLejosDelSoporte_esContradiccion() {
        // Invariante T7: "Toque Directo en Soporte" exige que el precio esté DENTRO del 15% del canal.
        assertTrue(HudCoherence.srStatusIsCoherent("🟢 Toque Directo en Soporte [CALL Óptimo]", distToSupportRatio = 0.10f))
        assertFalse(HudCoherence.srStatusIsCoherent("🟢 Toque Directo en Soporte [CALL Óptimo]", distToSupportRatio = 0.60f))
    }

    @Test
    fun estadoNeutralSiempreEsCoherente() {
        assertTrue(HudCoherence.srStatusIsCoherent("⚪ Centro del Canal S/R (Neutral)", distToSupportRatio = 0.50f))
        assertTrue(HudCoherence.srStatusIsCoherent("—", distToSupportRatio = 0.50f))
    }

    @Test
    fun toqueDeSoporteYBusquedaDeRechazoPut_esContradiccion() {
        // Caso observado en dispositivo (R11): el filtro S/R anunciaba
        // "Toque Directo en Soporte [CALL Óptimo]" mientras el plan pedía mecha de rechazo PUT.
        // Origen: dos normalizaciones distintas de la misma distancia (canal vs ATR).
        assertTrue(
            "CALL sobre soporte y PUT sobre resistencia no pueden coexistir",
            HudCoherence.actionConflict(
                "🟢 Toque Directo en Soporte [CALL Óptimo]",
                "🛡️ Anti-Chop Activo · Cerca de Resistencia (7%): Buscando Mecha Rechazo PUT"
            )
        )
    }

    @Test
    fun toqueDeSoporteYBusquedaDeRechazoCall_esCoherente() {
        assertFalse(
            HudCoherence.actionConflict(
                "🟢 Toque Directo en Soporte [CALL Óptimo]",
                "🛡️ Anti-Chop Activo · Cerca de Soporte (5%): Buscando Mecha Rechazo CALL"
            )
        )
    }
}
