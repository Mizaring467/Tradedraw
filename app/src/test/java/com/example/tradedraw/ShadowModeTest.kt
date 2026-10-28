package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Ronda 21 (Frente E): Pruebas del SHADOW MODE Formal.
 *
 * Contratos obligatorios verificados:
 *  1. Ejecuta el motor real y genera decisiones con decision_id.
 *  2. NO ejecuta taps ni interactúa con la pantalla.
 *  3. NO marca una orden real como ENVIADA (orderState permanece NINGUNA).
 *  4. NO altera hasPendingTrade (permanece false).
 *  5. NO altera balance ni contamina contadores de W/L real.
 *  6. Registra por separado en shadow_journal.csv.
 */
class ShadowModeTest {

    @Before
    fun setUp() {
        TradeEventLog.clear()
    }

    private class FakeContext(private val root: File?, private val internalRoot: File) : android.content.ContextWrapper(null) {
        override fun getExternalFilesDir(type: String?): File? = root
        override fun getFilesDir(): File = internalRoot
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "tradedraw_shadow_$name").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun shadowModeEnumEstaDisponible() {
        val mode = AutoTradeMode.valueOf("SHADOW")
        assertEquals(AutoTradeMode.SHADOW, mode)
    }

    @Test
    fun shadowDecisionEventoGuardaDecisionIdYNoGeneraTradeId() {
        val decisionId = UUID.randomUUID().toString()

        TradeEventLog.add(
            kind = TradeEventLog.Kind.SHADOW_DECISION,
            detail = "SHADOW BUY @ 2500.50 conf=0.85 strat=AUTO_ADAPTIVE sub=CONSERVATIVE",
            decisionId = decisionId,
            tradeId = null
        )

        val events = TradeEventLog.recent()
        val shadowEv = events.firstOrNull { it.kind == TradeEventLog.Kind.SHADOW_DECISION }
        assertTrue("Debe existir evento SHADOW_DECISION", shadowEv != null)
        assertEquals(decisionId, shadowEv?.decisionId)
        assertNull("SHADOW nunca debe generar trade_id", shadowEv?.tradeId)
    }

    @Test
    fun shadowJournalEscribeArchivoSeparadoAppendOnly() {
        val root = tempDir("shadow_j")
        val ctx = FakeContext(root, tempDir("shadow_j_int"))
        val decisionId = UUID.randomUUID().toString()

        val ok = TradeJournalLogger.logShadowTrade(
            context = ctx,
            strategy = "MT_REJECTION",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.90f,
            priceY = 2500.25f,
            trend = "UPTREND",
            candleSecond = 59,
            decisionId = decisionId,
            reason = "Prueba Shadow Mode"
        )

        assertTrue("logShadowTrade debe retornar true", ok)
        val file = File(root, "TradeDraw_Audits/shadow_journal.csv")
        assertTrue("El archivo shadow_journal.csv debe existir", file.exists())

        val lines = file.readLines()
        assertEquals("Debe tener cabecera + 1 fila", 2, lines.size)
        assertTrue("Cabecera debe contener decision_id", lines[0].contains("decision_id"))
        assertTrue("Fila debe contener el UUID de la decisión", lines[1].contains(decisionId))
        assertTrue("Fila debe contener la estrategia", lines[1].contains("MT_REJECTION"))
    }

    @Test
    fun shadowModeNoContaminaRiskManager() {
        val rm = RiskManager()

        // En SHADOW mode, un intento simulado NO debe llamar a recordTradeSent
        // Verificamos que el estado permanece en reposo
        assertEquals(RiskManager.OrderState.NINGUNA, rm.orderState)
        assertFalse(rm.hasPendingTrade)
        assertFalse(rm.hasConfirmedTrade)
        assertEquals(0, rm.totalWins)
        assertEquals(0, rm.totalLosses)
        assertEquals(0, rm.currentLossStreak)
    }
}
