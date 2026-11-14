package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class VetoProfilerTest {

    private lateinit var profiler: VetoProfiler

    @Before
    fun setUp() {
        profiler = VetoProfiler(maxRecentRecords = 100)
        profiler.reset()
    }

    @Test
    fun testClassifyVeto_allCategoriesCovered() {
        assertEquals(VetoCategory.TIMING, VetoProfiler.classifyVeto(7, "FUERA_DE_VENTANA sec=24"))
        assertEquals(VetoCategory.COOLDOWN, VetoProfiler.classifyVeto(9, "COOLDOWN 15000ms"))
        assertEquals(VetoCategory.COOLDOWN, VetoProfiler.classifyVeto(12, "Pausa Anti-Tilt tras fallo Martingala (180s)"))
        assertEquals(VetoCategory.CANDLE_ALREADY_EXECUTED, VetoProfiler.classifyVeto(5, "VELA_YA_OPERADA minuto=29847102"))
        assertEquals(VetoCategory.ADAPTIVE_BLOCK, VetoProfiler.classifyVeto(11, "⛔ Autoaprendizaje: Bloqueada continuación"))
        assertEquals(VetoCategory.CHOP, VetoProfiler.classifyVeto(8, "CHOP"))
        assertEquals(VetoCategory.CHOP, VetoProfiler.classifyVeto(2, "V2 rango=1.2e-7 umbral=1e-6"))
        assertEquals(VetoCategory.RISK_LIMIT, VetoProfiler.classifyVeto(12, "RISK_LIMIT Stop Loss alcanzado"))
        assertEquals(VetoCategory.ASSET_MISMATCH, VetoProfiler.classifyVeto(4, "DESAJUSTE Crypto IDX vs BTCUSDT"))
        assertEquals(VetoCategory.WS_STALE, VetoProfiler.classifyVeto(1, "FEED_RANCIO ageMs=6000"))
        assertEquals(VetoCategory.BROKER_ABSENT, VetoProfiler.classifyVeto(1, "FEED_MUDO activo=Z-CRY/IDX"))
        assertEquals(VetoCategory.NO_DEMO, VetoProfiler.classifyVeto(0, "SEGURIDAD_NO_DEMO cuenta_real_bloqueada"))
        assertEquals(VetoCategory.PENDING_TRADE, VetoProfiler.classifyVeto(0, "PENDING_TRADE orden_en_vuelo"))
        assertEquals(VetoCategory.SR_CONFLICT, VetoProfiler.classifyVeto(13, "V1 SR=\"RESISTENCIA\" PLAN=\"COMPRA\""))
        assertEquals(VetoCategory.BUY_EXHAUSTION, VetoProfiler.classifyVeto(10, "VETO_BUY_EXHAUSTION_CLIMAX"))
    }

    @Test
    fun testRecordEvaluation_allowedAndVetoedFunnel() {
        profiler.recordTick()
        profiler.recordTick()
        profiler.recordTick()

        // 1 allowed
        profiler.recordEvaluation(
            asset = "Z-CRY/IDX",
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.85f,
            candleSecond = 2,
            marketRegime = "UPTREND",
            signalScore = 85,
            vetoIndex = 0,
            vetoReason = "",
            allowed = true
        )

        // 1 timing veto
        profiler.recordEvaluation(
            asset = "Z-CRY/IDX",
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "SELL",
            confidence = 0.70f,
            candleSecond = 25,
            marketRegime = "DOWNTREND",
            signalScore = 80,
            vetoIndex = 7,
            vetoReason = "FUERA_DE_VENTANA sec=25",
            allowed = false
        )

        // 1 cooldown veto
        profiler.recordEvaluation(
            asset = "Z-CRY/IDX",
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.80f,
            candleSecond = 3,
            marketRegime = "UPTREND",
            signalScore = 85,
            vetoIndex = 9,
            vetoReason = "COOLDOWN 18000ms",
            allowed = false
        )

        val json = profiler.buildVetoStatsJson()
        assertTrue("JSON must contain evaluations", json.contains("\"evaluations\": 3"))
        assertTrue("JSON must contain candidate_signals", json.contains("\"candidate_signals\": 3"))
        assertTrue("JSON must contain authorized", json.contains("\"authorized\": 1"))
        assertTrue("JSON must contain TIMING veto", json.contains("\"TIMING\": 1"))
        assertTrue("JSON must contain COOLDOWN veto", json.contains("\"COOLDOWN\": 1"))
        assertTrue("JSON must contain funnel", json.contains("\"funnel\":"))
        assertTrue("JSON must contain conversion_rate", json.contains("\"conversion_rate\": 0.3333"))
    }

    @Test
    fun testNoTradeDiagnostic() {
        profiler.lastTradeTimestampMs = System.currentTimeMillis() - 200_000L // 200s ago

        // Record some vetos
        for (i in 1..4) {
            profiler.recordEvaluation(
                asset = "Z-CRY/IDX",
                strategy = "AUTO_ADAPTIVE",
                submode = "CONSERVATIVE",
                action = "BUY",
                confidence = 0.8f,
                candleSecond = 35,
                marketRegime = "SIDEWAYS",
                signalScore = 80,
                vetoIndex = 7,
                vetoReason = "FUERA_DE_VENTANA sec=35",
                allowed = false
            )
        }
        profiler.recordEvaluation(
            asset = "Z-CRY/IDX",
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "SELL",
            confidence = 0.8f,
            candleSecond = 2,
            marketRegime = "DOWNTREND",
            signalScore = 80,
            vetoIndex = 9,
            vetoReason = "COOLDOWN 12000ms",
            allowed = false
        )

        val diagJson = profiler.buildNoTradeDiagnosticJson(
            remainingCooldownSeconds = 45,
            entryWindow = ":01-:05",
            hasPendingTrade = false,
            isWsHealthy = true,
            demoAccountStatus = "confirmed"
        )

        assertTrue(diagJson.contains("\"no_trade_duration_seconds\":"))
        assertTrue(diagJson.contains("\"top_blockers\":"))
        assertTrue(diagJson.contains("\"TIMING\": \"80.0%\""))
        assertTrue(diagJson.contains("\"COOLDOWN\": \"20.0%\""))
        assertTrue(diagJson.contains("\"remaining_cooldown_seconds\": 45"))
        assertTrue(diagJson.contains("\"demo_status\": \"confirmed\""))
    }

    @Test
    fun testPersistToFile() {
        val tmpFile = File.createTempFile("veto_test", ".json")
        try {
            profiler.recordEvaluation(
                asset = "Z-CRY/IDX",
                strategy = "AUTO_ADAPTIVE",
                submode = "CONSERVATIVE",
                action = "BUY",
                confidence = 0.85f,
                candleSecond = 2,
                marketRegime = "UPTREND",
                signalScore = 85,
                vetoIndex = 0,
                vetoReason = "",
                allowed = true
            )
            profiler.persistToFile(tmpFile)
            assertTrue(tmpFile.exists())
            assertTrue(tmpFile.length() > 20)
            val content = tmpFile.readText()
            assertTrue(content.contains("\"authorized\": 1"))
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun testFunnelMonotonicityAndExecutionRate() {
        profiler.reset()
        profiler.recordTick()
        profiler.recordTick()
        profiler.recordEvaluationTick()
        profiler.recordEvaluationTick()
        profiler.recordCandidateSignal()

        // 1 allowed candidate
        profiler.recordEvaluation(
            asset = "CRYPTO_IDX",
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.90f,
            candleSecond = 2,
            marketRegime = "TRENDING",
            signalScore = 90,
            vetoIndex = 0,
            vetoReason = "",
            allowed = true
        )
        profiler.recordTapDispatched()
        profiler.recordTradeSettled()

        val json = profiler.buildVetoStatsJson()
        assertTrue(json.contains("\"taps_dispatched\": 1"))
        assertTrue(json.contains("\"settled\": 1"))
        assertTrue(json.contains("\"execution_rate\": 1.0000"))
    }
}
