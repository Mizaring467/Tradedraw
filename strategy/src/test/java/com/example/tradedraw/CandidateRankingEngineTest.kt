package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import org.junit.Assert.*
import org.junit.Test

class CandidateRankingEngineTest {

    private val engine = CandidateRankingEngine()

    @Test
    fun testSelectBestCandidate_highestScoreWins() {
        val cand1 = CandidateSignal(
            asset = "CRYPTO_IDX",
            action = TradeAction.BUY,
            confidence = 0.90f,
            confluenceScore = 95,
            payoutRate = 0.85,
            marketRegime = "TRENDING",
            tickAgeMs = 50L
        )

        val cand2 = CandidateSignal(
            asset = "EUR_USD_OTC",
            action = TradeAction.SELL,
            confidence = 0.75f,
            confluenceScore = 80,
            payoutRate = 0.80,
            marketRegime = "SIDEWAYS",
            tickAgeMs = 200L
        )

        val result = engine.selectBestCandidate(listOf(cand2, cand1))
        assertNotNull("Must select a winner", result.winner)
        assertEquals("CRYPTO_IDX should win on composite score", "CRYPTO_IDX", result.winner?.asset)
        assertEquals(1, result.superseded.size)
        assertEquals("EUR_USD_OTC", result.superseded[0].candidate.asset)
        assertEquals("RANKING_SUPERSEDED:selected_CRYPTO_IDX", result.superseded[0].reason)
    }

    @Test
    fun testSelectBestCandidate_pendingTradeVetoesAll() {
        val cand1 = CandidateSignal(
            asset = "CRYPTO_IDX",
            action = TradeAction.BUY,
            confidence = 0.95f,
            confluenceScore = 95,
            payoutRate = 0.85
        )
        val cand2 = CandidateSignal(
            asset = "GBP_USD_OTC",
            action = TradeAction.SELL,
            confidence = 0.90f,
            confluenceScore = 90,
            payoutRate = 0.82
        )

        val result = engine.selectBestCandidate(listOf(cand1, cand2), hasPendingTrade = true)
        assertNull("Winner must be null when trade is pending", result.winner)
        assertEquals("All candidates must be marked superseded", 2, result.superseded.size)
        assertTrue(result.superseded.all { it.reason == "PENDING_TRADE:active_trade_in_flight" })
    }

    @Test
    fun testSelectBestCandidate_tieBreakingByPayout() {
        // Identical parameters except payout
        val cand1 = CandidateSignal(
            asset = "EUR_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.80,
            marketRegime = "TRENDING",
            tickAgeMs = 100L
        )
        val cand2 = CandidateSignal(
            asset = "GBP_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.85, // Higher payout
            marketRegime = "TRENDING",
            tickAgeMs = 100L
        )

        val result = engine.selectBestCandidate(listOf(cand1, cand2))
        assertEquals("GBP_USD_OTC should win due to higher payout", "GBP_USD_OTC", result.winner?.asset)
    }

    @Test
    fun testSelectBestCandidate_tieBreakingByLatency() {
        // Identical score and payout, different tickAgeMs
        val cand1 = CandidateSignal(
            asset = "EUR_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.85,
            marketRegime = "TRENDING",
            tickAgeMs = 300L
        )
        val cand2 = CandidateSignal(
            asset = "BTC_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.85,
            marketRegime = "TRENDING",
            tickAgeMs = 50L // Lower latency
        )

        val result = engine.selectBestCandidate(listOf(cand1, cand2))
        assertEquals("BTC_USD_OTC should win due to lower tick latency", "BTC_USD_OTC", result.winner?.asset)
    }

    @Test
    fun testSelectBestCandidate_tieBreakingDeterministicSymbol() {
        // Exactly identical candidates except asset symbol
        val cand1 = CandidateSignal(
            asset = "EUR_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.85,
            marketRegime = "TRENDING",
            tickAgeMs = 50L
        )
        val cand2 = CandidateSignal(
            asset = "BTC_USD_OTC",
            action = TradeAction.BUY,
            confidence = 0.85f,
            confluenceScore = 85,
            payoutRate = 0.85,
            marketRegime = "TRENDING",
            tickAgeMs = 50L
        )

        val result = engine.selectBestCandidate(listOf(cand1, cand2))
        // "BTC_USD_OTC" < "EUR_USD_OTC" lexicographically
        assertEquals("BTC_USD_OTC should win lexicographical tie-break", "BTC_USD_OTC", result.winner?.asset)
    }

    @Test
    fun testSelectBestCandidate_emptyList() {
        val result = engine.selectBestCandidate(emptyList())
        assertNull(result.winner)
        assertEquals(0, result.superseded.size)
    }
}
