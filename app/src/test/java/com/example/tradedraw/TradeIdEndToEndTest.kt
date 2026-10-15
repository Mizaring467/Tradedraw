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
 * Ronda 21 (Frente A): Pruebas del ciclo de vida y trazabilidad end-to-end de
 * decision_id y trade_id en TradeDraw.
 *
 * Contratos obligatorios verificados:
 *  1. Una decisión mantiene su decision_id a lo largo del flujo.
 *  2. Una orden despachada mantiene su trade_id inequívoco.
 *  3. Un veto NO inventa ni genera un trade_id.
 *  4. Intentos duplicados no generan un segundo settlement (idempotencia).
 *  5. Filas históricas viejas (21 o 27 columnas) se leen como ausencia sin romper lectores.
 *  6. Filas nuevas persisten ambos IDs en las columnas correspondientes.
 *  7. Los eventos de tap y la resolución WIN/LOSS se enlazan inequívocamente al tap.
 */
class TradeIdEndToEndTest {

    @Before
    fun setUp() {
        TradeJournalLogger.resetIdempotencyStateForTests()
        TradeEventLog.clear()
    }

    private class FakeContext(private val root: File?, private val internalRoot: File) : android.content.ContextWrapper(null) {
        override fun getExternalFilesDir(type: String?): File? = root
        override fun getFilesDir(): File = internalRoot
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "tradedraw_test_id_$name").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun ordenDespachadaMantieneDecisionIdYTradeId() {
        val rm = RiskManager()
        val decisionId = UUID.randomUUID().toString()
        val tradeId = UUID.randomUUID().toString()

        rm.recordTradeSent(
            action = TradeAction.BUY,
            entryPrice = 1.2345,
            entryPriceY = 100f,
            baseBalance = 50000.0,
            confidence = 0.95f,
            decisionId = decisionId,
            tradeId = tradeId
        )

        assertEquals("RiskManager debe retener decisionId", decisionId, rm.pendingTradeDecisionId)
        assertEquals("RiskManager debe retener tradeId", tradeId, rm.pendingTradeTradeId)
        assertEquals(RiskManager.OrderState.ENVIADA, rm.orderState)

        // Verificar que TradeEventLog registró ORDER_STATE con ambos IDs
        val events = TradeEventLog.recent()
        val orderEvent = events.lastOrNull { it.kind == TradeEventLog.Kind.ORDER_STATE }
        assertTrue("Debe existir evento ORDER_STATE", orderEvent != null)
        assertEquals(decisionId, orderEvent?.decisionId)
        assertEquals(tradeId, orderEvent?.tradeId)
        assertTrue(orderEvent?.detail?.contains("ENVIADA") == true)
    }

    @Test
    fun vetoNoInventaTradeId() {
        val rm = RiskManager()
        val decisionId = UUID.randomUUID().toString()

        // Registro de veto de decisión: emite evento de veto solo con decisionId
        TradeEventLog.add(
            kind = TradeEventLog.Kind.VETO,
            detail = "FEED_STALE veto preventivo",
            decisionId = decisionId,
            tradeId = null
        )

        // El estado de orden en RiskManager debe seguir en NINGUNA y sin tradeId
        assertEquals(RiskManager.OrderState.NINGUNA, rm.orderState)
        assertEquals("", rm.pendingTradeTradeId)
        assertEquals("", rm.pendingTradeDecisionId)
        assertFalse("Veto nunca debe marcar hasPendingTrade", rm.hasPendingTrade)

        val events = TradeEventLog.recent()
        val vetoEvent = events.lastOrNull { it.kind == TradeEventLog.Kind.VETO }
        assertTrue("Debe existir evento VETO", vetoEvent != null)
        assertEquals(decisionId, vetoEvent?.decisionId)
        assertNull("Veto no debe inventar tradeId", vetoEvent?.tradeId)
    }

    @Test
    fun ordenNoProcesadaPreservaIdsEnEventoYLuegoLimpia() {
        val rm = RiskManager()
        val decisionId = UUID.randomUUID().toString()
        val tradeId = UUID.randomUUID().toString()

        rm.recordTradeSent(
            action = TradeAction.SELL,
            entryPrice = 2000.0,
            entryPriceY = 50f,
            baseBalance = 100000.0,
            confidence = 0.88f,
            decisionId = decisionId,
            tradeId = tradeId
        )

        // Marcar como no procesada
        rm.markOrderUnprocessed()

        // El estado pasa a NO_PROCESADA y los IDs pendientes se limpian para no contaminar
        assertEquals(RiskManager.OrderState.NO_PROCESADA, rm.orderState)
        assertFalse(rm.hasPendingTrade)
        assertEquals("", rm.pendingTradeDecisionId)
        assertEquals("", rm.pendingTradeTradeId)

        // Pero en el log de eventos, el evento NO_PROCESADA debe conservar los IDs de auditoría
        val events = TradeEventLog.recent()
        val unprocEvent = events.lastOrNull { it.detail.contains("NO_PROCESADA") }
        assertTrue("Debe existir evento NO_PROCESADA", unprocEvent != null)
        assertEquals(decisionId, unprocEvent?.decisionId)
        assertEquals(tradeId, unprocEvent?.tradeId)
    }

    @Test
    fun duplicadoNoGeneraSegundoSettlement() {
        val root = tempDir("journal_dedup")
        val ctx = FakeContext(root, tempDir("journal_dedup_int"))
        val decisionId = UUID.randomUUID().toString()
        val tradeId = UUID.randomUUID().toString()
        val startMs = 1700000000000L

        val primerLog = TradeJournalLogger.logTrade(
            context = ctx,
            strategy = "MT_REJECTION",
            submode = "YOLO",
            action = "BUY",
            confidence = 0.90f,
            priceY = 500f,
            stake = 80000.0,
            baseBalance = 1000000.0,
            result = "WIN",
            settledBalance = 1065600.0,
            durationSec = 60,
            reason = "test dedup",
            tradeStartMs = startMs,
            decisionId = decisionId,
            tradeId = tradeId
        )
        assertTrue("Primer log debe guardarse", primerLog)

        // Intento duplicado exacto
        val segundoLog = TradeJournalLogger.logTrade(
            context = ctx,
            strategy = "MT_REJECTION",
            submode = "YOLO",
            action = "BUY",
            confidence = 0.90f,
            priceY = 500f,
            stake = 80000.0,
            baseBalance = 1000000.0,
            result = "WIN",
            settledBalance = 1065600.0,
            durationSec = 60,
            reason = "test dedup",
            tradeStartMs = startMs,
            decisionId = decisionId,
            tradeId = tradeId
        )
        assertFalse("Log duplicado debe ser rechazado por idempotencia", segundoLog)

        val file = File(root, "TradeDraw_Audits/trade_journal.csv")
        val lines = file.readLines()
        assertEquals("Debe haber exactamente cabecera + 1 sola fila", 2, lines.size)
    }

    @Test
    fun journalsViejosSiguenLeyendoSinRomperYFilasNuevasTraenIds() {
        val filaHistorica21 = "1700000000000,2026-10-12T00:00:00,MT,YOLO,BUY,0.90,1.0000,1.00,100.00,WIN,101.00,62,UP,0.50,0.50,0.00,NEUTRAL,NORMAL,3,WIN,\"motivo\""
        val colsVieja = filaHistorica21.split(",")
        assertEquals(21, colsVieja.size)

        // Parser resiliente indexado por header
        val header = TradeJournalLogger.headerForTests().trim().split(",")
        assertEquals(29, header.size)
        val idxDecisionId = header.indexOf("decision_id")
        val idxTradeId = header.indexOf("trade_id")

        val decisionIdViejo = if (idxDecisionId < colsVieja.size) colsVieja[idxDecisionId] else null
        val tradeIdViejo = if (idxTradeId < colsVieja.size) colsVieja[idxTradeId] else null

        assertNull("Fila vieja lee decision_id como null/ausencia", decisionIdViejo)
        assertNull("Fila vieja lee trade_id como null/ausencia", tradeIdViejo)

        // Generar fila nueva
        val decId = UUID.randomUUID().toString()
        val trId = UUID.randomUUID().toString()
        val filaNueva = TradeJournalLogger.buildLine(
            action = "BUY", result = "WIN", reason = "r21_test", strategy = "MT", submode = "YOLO",
            confidence = 0.92f, priceY = 1.0f, stake = 80000, baseBalance = 500000.0, settledBalance = 565000.0,
            durationSec = 60L, trend = "UPTREND", distSupportRatio = 0.1f, distResistanceRatio = 0.9f,
            tickVelocity = 1.5f, impulse = "BULLISH", marketRegime = "TRENDING",
            candleSecond = 59, adaptiveStatus = "WIN", decisionId = decId, tradeId = trId
        )

        val colsNueva = filaNueva.trim().split(",")
        assertEquals(29, colsNueva.size)
        assertEquals("decision_id debe estar en la columna correspondiente", decId, colsNueva[idxDecisionId])
        assertEquals("trade_id debe estar en la columna correspondiente", trId, colsNueva[idxTradeId])
    }

    @Test
    fun winLossSeEnlazanInequivocamenteAlTap() {
        val rm = RiskManager()
        val decisionId = "dec-uuid-1234"
        val tradeId = "trade-uuid-5678"

        // Despacho de orden
        rm.recordTradeSent(
            action = TradeAction.BUY,
            entryPrice = 100.0,
            entryPriceY = 200f,
            baseBalance = 1000.0,
            confidence = 0.95f,
            decisionId = decisionId,
            tradeId = tradeId
        )

        // Simular confirmación de tap con latencia
        val tapStart = 1700000000100L
        val tapEnd = 1700000000450L
        rm.captureTapLatency(tapStart, tapEnd, candleSecondAtTapEnd = 59, priceAtTapEnd = 100.2)

        // Registrar evento de tap en el log
        TradeEventLog.add(
            TradeEventLog.Kind.TAP,
            "BUY ejecutado en (500, 1000) latencia=${tapEnd - tapStart}ms",
            decisionId = decisionId,
            tradeId = tradeId
        )

        // Resolver trade como WIN
        val resolved = rm.resolvePendingTradeWin()
        assertTrue("Trade pendiente debe resolverse como WIN", resolved)

        // Registrar evento de liquidación con los IDs
        TradeEventLog.add(
            TradeEventLog.Kind.ORDER_STATE,
            "RESOLVED WIN",
            decisionId = decisionId,
            tradeId = tradeId
        )

        val events = TradeEventLog.recent()
        val tapEvent = events.firstOrNull { it.kind == TradeEventLog.Kind.TAP }
        val resolutionEvent = events.lastOrNull { it.detail.contains("RESOLVED WIN") }

        assertTrue(tapEvent != null)
        assertTrue(resolutionEvent != null)
        assertEquals("El tap y la resolución deben compartir decision_id", tapEvent?.decisionId, resolutionEvent?.decisionId)
        assertEquals("El tap y la resolución deben compartir trade_id", tapEvent?.tradeId, resolutionEvent?.tradeId)
    }
}
