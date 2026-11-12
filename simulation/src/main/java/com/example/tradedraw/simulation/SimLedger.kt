package com.example.tradedraw.simulation

import com.example.tradedraw.core.TradeAction
import java.util.concurrent.atomic.AtomicInteger

data class SimMetadata(
    val strategy: String = "SIMULATION",
    val submode: String = "",
    val confidence: Float = 0f,
    val trend: String = "SIDEWAYS",
    val reason: String = "",
    val strategyVersion: String = "r29c-frozen-4fdb646",
    val policyContextJson: String? = null
)
data class OpenSimulatedTrade(val tradeId: String, val decisionId: String, val action: TradeAction,
    val entryPrice: Double, val stake: Double, val entryTimestampMs: Long,
    val asset: String = "", val metadata: SimMetadata = SimMetadata())
data class SettledSimulatedTrade(val tradeId: String, val decisionId: String, val action: TradeAction,
    val entryPrice: Double, val exitPrice: Double, val stake: Double, val result: String,
    val entryTimestampMs: Long, val exitTimestampMs: Long, val baseBalance: Double, val settledBalance: Double,
    val asset: String = "", val metadata: SimMetadata = SimMetadata())
data class SimSnapshot(val initialBalance: Double, val payoutRate: Double, val balance: Double,
    val open: List<OpenSimulatedTrade> = emptyList(), val settled: List<SettledSimulatedTrade> = emptyList(),
    val journaled: Set<String> = emptySet(), val watermarks: Map<String, Long> = emptyMap())
interface SimSnapshotStore { fun load(): SimSnapshot?; fun save(snapshot: SimSnapshot) }
class MemorySimSnapshotStore : SimSnapshotStore {
    private var state: SimSnapshot? = null
    override fun load() = state
    override fun save(snapshot: SimSnapshot) { state = snapshot }
}

/** Every mutation is persisted before publication. Stake is reserved, PnL is booked at settlement. */
class SimLedger(val initialBalance: Double = 1000.0, val payoutRate: Double = 0.82,
    private val store: SimSnapshotStore = MemorySimSnapshotStore()) {
    private var state = store.load() ?: SimSnapshot(initialBalance, payoutRate, initialBalance)
    init {
        require(initialBalance.isFinite() && initialBalance > 0 && payoutRate.isFinite() && payoutRate >= 0)
        require(state.initialBalance == initialBalance && state.payoutRate == payoutRate) { "SIM_CONFIG_MISMATCH" }
    }
    val totalWins = AtomicInteger(state.settled.count { it.result == "WIN" })
    val totalLosses = AtomicInteger(state.settled.count { it.result == "LOSS" })
    val totalTies = AtomicInteger(state.settled.count { it.result == "TIE" })
    val currentBalance: Double @Synchronized get() = state.balance
    val availableBalance: Double @Synchronized get() = state.balance - state.open.sumOf { it.stake }
    val totalTrades: Int @Synchronized get() = state.settled.size
    val winRate: Double @Synchronized get() = if (totalTrades == 0) 0.0 else totalWins.get() * 100.0 / totalTrades
    @Synchronized fun snapshot(): SimSnapshot = state
    private fun publish(next: SimSnapshot) {
        try { store.save(next); state = next } catch (e: Exception) {
            // Includes a write that committed durably and lost its acknowledgement.
            state = store.load() ?: state
            refreshCounters()
            throw e
        }
        refreshCounters()
    }
    private fun refreshCounters() {
        totalWins.set(state.settled.count { it.result == "WIN" })
        totalLosses.set(state.settled.count { it.result == "LOSS" })
        totalTies.set(state.settled.count { it.result == "TIE" })
    }
    @Synchronized fun findByIds(tradeId: String, decisionId: String): Pair<OpenSimulatedTrade?, SettledSimulatedTrade?> {
        val open = state.open.firstOrNull { it.tradeId == tradeId || it.decisionId == decisionId }
        val settled = state.settled.firstOrNull { it.tradeId == tradeId || it.decisionId == decisionId }
        require(open == null || open.tradeId == tradeId && open.decisionId == decisionId) { "SIM_ID_CONFLICT" }
        require(settled == null || settled.tradeId == tradeId && settled.decisionId == decisionId) { "SIM_ID_CONFLICT" }
        return open to settled
    }
    @Synchronized fun openTrade(tradeId: String, decisionId: String, action: TradeAction, entryPrice: Double,
        stake: Double, timestampMs: Long, asset: String = "", metadata: SimMetadata = SimMetadata()): OpenSimulatedTrade {
        require(tradeId.isNotBlank() && decisionId.isNotBlank() && tradeId.length <= 256 && decisionId.length <= 256)
        require(entryPrice.isFinite() && entryPrice > 0 && stake.isFinite() && stake > 0 && timestampMs >= 0)
        val (existing, settled) = findByIds(tradeId, decisionId)
        if (existing != null) {
            require(existing.action == action && existing.asset == asset && existing.stake == stake) { "SIM_RETRY_CONFLICT" }
            return existing
        }
        require(settled == null) { "SIM_ALREADY_SETTLED" }
        require(stake <= availableBalance) { "SIM_INSUFFICIENT_BALANCE" }
        val open = OpenSimulatedTrade(tradeId, decisionId, action, entryPrice, stake, timestampMs, asset, metadata)
        publish(state.copy(open = state.open + open))
        return open
    }
    private fun settle(open: OpenSimulatedTrade, price: Double, timestamp: Long, balance: Double): SettledSimulatedTrade {
        val result = if (price == open.entryPrice) "TIE" else if ((price > open.entryPrice) == (open.action == TradeAction.BUY)) "WIN" else "LOSS"
        val next = balance + when (result) { "WIN" -> open.stake * payoutRate; "LOSS" -> -open.stake; else -> 0.0 }
        return SettledSimulatedTrade(open.tradeId, open.decisionId, open.action, open.entryPrice, price, open.stake,
            result, open.entryTimestampMs, timestamp, balance, next, open.asset, open.metadata)
    }
    @Synchronized fun resolveTrade(tradeId: String, exitPrice: Double, exitTimestampMs: Long): SettledSimulatedTrade? {
        require(exitPrice.isFinite() && exitPrice > 0)
        val open = state.open.firstOrNull { it.tradeId == tradeId } ?: return null
        if (exitTimestampMs - open.entryTimestampMs < 60_000L) return null
        val settled = settle(open, exitPrice, exitTimestampMs, state.balance)
        publish(state.copy(balance = settled.settledBalance, open = state.open - open, settled = state.settled + settled))
        return settled
    }
    /** First accepted tick for the SAME asset at/after entry+60s. Older/equal event times are ignored. */
    @Synchronized fun onTick(asset: String, price: Double, timestamp: Long): List<SettledSimulatedTrade> {
        if (!price.isFinite() || price <= 0 || timestamp < 0 || timestamp <= (state.watermarks[asset] ?: -1L)) return emptyList()
        if (state.open.none { it.asset == asset }) return emptyList()
        val due = state.open.filter { it.asset == asset && timestamp - it.entryTimestampMs >= 60_000L }
            .sortedWith(compareBy<OpenSimulatedTrade> { it.entryTimestampMs }.thenBy { it.tradeId })
        var balance = state.balance
        val results = due.map { settle(it, price, timestamp, balance).also { s -> balance = s.settledBalance } }
        val ids = results.map { it.tradeId }.toSet()
        publish(state.copy(balance = balance, open = state.open.filterNot { it.tradeId in ids },
            settled = state.settled + results, watermarks = state.watermarks + (asset to timestamp)))
        return results
    }
    @Synchronized fun getOpenTrades() = state.open.toList()
    @Synchronized fun getSettledTrades() = state.settled.toList()
    @Synchronized fun pendingJournal() = state.settled.filterNot { it.tradeId in state.journaled }
    @Synchronized fun acknowledgeJournal(tradeId: String) {
        require(state.settled.any { it.tradeId == tradeId })
        if (tradeId !in state.journaled) publish(state.copy(journaled = state.journaled + tradeId))
    }
    @Synchronized fun reset() { require(state.open.isEmpty() && pendingJournal().isEmpty()) { "SIM_PENDING_WORK" }; publish(SimSnapshot(initialBalance, payoutRate, initialBalance)) }
}
