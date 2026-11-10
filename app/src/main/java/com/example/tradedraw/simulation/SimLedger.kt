package com.example.tradedraw.simulation

import com.example.tradedraw.core.TradeAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class OpenSimulatedTrade(
    val tradeId: String,
    val decisionId: String,
    val action: TradeAction,
    val entryPrice: Double,
    val stake: Double,
    val entryTimestampMs: Long
)

data class SettledSimulatedTrade(
    val tradeId: String,
    val decisionId: String,
    val action: TradeAction,
    val entryPrice: Double,
    val exitPrice: Double,
    val stake: Double,
    val result: String,
    val entryTimestampMs: Long,
    val exitTimestampMs: Long,
    val baseBalance: Double,
    val settledBalance: Double
)

/**
 * Libro contable para operaciones simuladas en modo SHADOW y backtests.
 * Gestiona saldo simulado, cálculo de PnL a 60s con payout y estadísticas.
 */
class SimLedger(
    val initialBalance: Double = 1000.0,
    val payoutRate: Double = 0.82
) {
    @Volatile
    var currentBalance: Double = initialBalance
        private set

    private val openTrades = ConcurrentHashMap<String, OpenSimulatedTrade>()
    private val settledTradesList = mutableListOf<SettledSimulatedTrade>()

    val totalWins = AtomicInteger(0)
    val totalLosses = AtomicInteger(0)
    val totalTies = AtomicInteger(0)

    val totalTrades: Int
        get() = totalWins.get() + totalLosses.get() + totalTies.get()

    val winRate: Double
        get() {
            val total = totalTrades
            return if (total == 0) 0.0 else (totalWins.get().toDouble() / total) * 100.0
        }

    fun openTrade(
        tradeId: String,
        decisionId: String,
        action: TradeAction,
        entryPrice: Double,
        stake: Double,
        timestampMs: Long
    ): OpenSimulatedTrade {
        val trade = OpenSimulatedTrade(
            tradeId = tradeId,
            decisionId = decisionId,
            action = action,
            entryPrice = entryPrice,
            stake = stake,
            entryTimestampMs = timestampMs
        )
        openTrades[tradeId] = trade
        return trade
    }

    fun resolveTrade(
        tradeId: String,
        exitPrice: Double,
        exitTimestampMs: Long
    ): SettledSimulatedTrade? {
        val open = openTrades.remove(tradeId) ?: return null
        synchronized(this) {
            val baseBal = currentBalance
            val result: String
            val settledBal: Double

            if (exitPrice == open.entryPrice) {
                result = "TIE"
                settledBal = baseBal
                totalTies.incrementAndGet()
            } else {
                val won = if (open.action == TradeAction.BUY) {
                    exitPrice > open.entryPrice
                } else {
                    exitPrice < open.entryPrice
                }

                if (won) {
                    result = "WIN"
                    settledBal = baseBal + (open.stake * payoutRate)
                    totalWins.incrementAndGet()
                } else {
                    result = "LOSS"
                    settledBal = baseBal - open.stake
                    totalLosses.incrementAndGet()
                }
            }

            currentBalance = settledBal
            val settled = SettledSimulatedTrade(
                tradeId = open.tradeId,
                decisionId = open.decisionId,
                action = open.action,
                entryPrice = open.entryPrice,
                exitPrice = exitPrice,
                stake = open.stake,
                result = result,
                entryTimestampMs = open.entryTimestampMs,
                exitTimestampMs = exitTimestampMs,
                baseBalance = baseBal,
                settledBalance = settledBal
            )
            settledTradesList.add(settled)
            return settled
        }
    }

    fun getSettledTrades(): List<SettledSimulatedTrade> {
        synchronized(this) {
            return settledTradesList.toList()
        }
    }

    fun reset() {
        synchronized(this) {
            currentBalance = initialBalance
            openTrades.clear()
            settledTradesList.clear()
            totalWins.set(0)
            totalLosses.set(0)
            totalTies.set(0)
        }
    }
}
