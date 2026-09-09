package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences

class RiskManager(context: Context? = null) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences("TradeDraw_RiskConfig", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

   companion object {
       const val MAX_PENDING_TRADE_TIMEOUT_SEC = 75L
       const val DEFAULT_COOLDOWN_SECONDS = 10
        const val DEFAULT_LOSS_COOLDOWN_SECONDS = 60
       const val DEFAULT_STOP_LOSS_STREAK = 3
       const val DEFAULT_TAKE_PROFIT_WINS = 20 // 20 victorias por bloque (0 = Ilimitado)
        const val DEFAULT_MAX_MARTINGALE_LEVEL = 2
    }

    @Volatile
    var maxMartingaleLevel: Int = prefs?.getInt("max_martingale", DEFAULT_MAX_MARTINGALE_LEVEL) ?: DEFAULT_MAX_MARTINGALE_LEVEL
        set(value) {
            field = value
            prefs?.edit()?.putInt("max_martingale", value)?.apply()
        }

    @Volatile
    var lossCooldownSeconds: Int = prefs?.getInt("loss_cooldown_sec", DEFAULT_LOSS_COOLDOWN_SECONDS) ?: DEFAULT_LOSS_COOLDOWN_SECONDS
        set(value) {
            field = value
            prefs?.edit()?.putInt("loss_cooldown_sec", value)?.apply()
        }

    @Volatile
    var stopLossStreak: Int = prefs?.getInt("sl_streak", DEFAULT_STOP_LOSS_STREAK) ?: DEFAULT_STOP_LOSS_STREAK
        set(value) {
            field = value
            prefs?.edit()?.putInt("sl_streak", value)?.apply()
        }

    @Volatile
    var takeProfitWins: Int = prefs?.getInt("tp_wins", DEFAULT_TAKE_PROFIT_WINS) ?: DEFAULT_TAKE_PROFIT_WINS
        set(value) {
            field = value
            prefs?.edit()?.putInt("tp_wins", value)?.apply()
        }

    @Volatile
    var cooldownSeconds: Int = prefs?.getInt("cooldown_sec", DEFAULT_COOLDOWN_SECONDS) ?: DEFAULT_COOLDOWN_SECONDS
        set(value) {
            field = value
            prefs?.edit()?.putInt("cooldown_sec", value)?.apply()
        }

    @Volatile
    var martingaleEnabled: Boolean = prefs?.getBoolean("martingale_on", false) ?: false
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("martingale_on", value)?.apply()
        }

    @Volatile
    var martingaleMultiplier: Float = prefs?.getFloat("martingale_mult", 2.0f) ?: 2.0f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("martingale_mult", value)?.apply()
        }

    @Volatile
    var baseAmount: Float = prefs?.getFloat("base_amount", 1.0f) ?: 1.0f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("base_amount", value)?.apply()
        }

    // Estado en vivo de la sesión (Inicia limpio en 0 para cada nueva sesión de trading)
    @Volatile
    var currentLossStreak: Int = 0

    var consecutiveLosses: Int
        get() = currentLossStreak
        set(value) { currentLossStreak = value }

    val currentMartingaleIndex: Int
        get() = currentLossStreak

    @Volatile
    var currentWins: Int = 0

    @Volatile
    var totalWins: Int = 0

    @Volatile
    var totalLosses: Int = 0

    @Volatile
    var lastTradeTime: Long = 0L
        internal set

    val lastTradeResultTimestamp: Long
        get() = lastTradeTime

    // Control de trade activo en curso (Sincronizado y protegido contra sobreoperativa)
    @Volatile
    var hasPendingTrade: Boolean = false
        internal set

    @Volatile
    var pendingTradeAction: TradeAction? = null
        internal set

    @Volatile
    var pendingTradeStartTime: Long = 0L
        internal set

    val tradeOpenedTimestamp: Long
        get() = pendingTradeStartTime

    @Volatile
    var pendingTradeEntryPriceY: Float = 0f
        internal set

    @Volatile
    var pendingTradeBaseBalance: Double = 0.0
        internal set

    @Volatile
    var sessionStartBalance: Double = 0.0

    @Volatile
    var unitTradeAmount: Double = 80000.0 // Monto de inversión base en Binomo (Col$80,000)

    @Synchronized
    fun getRemainingCooldown(): Int {
        if (lastTradeTime == 0L) return 0
        val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
        val requiredCooldown = if (currentLossStreak > 0) lossCooldownSeconds else cooldownSeconds
        val remaining = requiredCooldown - elapsed
        return if (remaining > 0) remaining.toInt() else 0
    }

    @Synchronized
    fun canExecuteTrade(mode: AutoTradeMode? = null, subMode: AutonomousSubMode? = null): Pair<Boolean, String> {
        if (hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - pendingTradeStartTime) / 1000
            // Timeout de seguridad: las operaciones de 1m en Binomo duran entre 45s y 75s
            if (elapsed >= MAX_PENDING_TRADE_TIMEOUT_SEC) {
                clearPendingTrade()
            } else {
                return Pair(false, "Operación abierta en curso (${elapsed}s)")
            }
        }
        // En SUBMODO YOLO: Sin restricciones de Stop Loss, Take Profit ni pausas de cooldown
        if (subMode == AutonomousSubMode.YOLO) {
            return Pair(true, "🚀 MODO YOLO: Operativa continua sin límites")
        }
        if (stopLossStreak > 0 && currentLossStreak >= stopLossStreak) {
            return Pair(false, "Stop Loss alcanzado ($stopLossStreak derrotas)")
        }
        if (takeProfitWins > 0 && currentWins >= takeProfitWins) {
            return Pair(false, "Take Profit alcanzado ($takeProfitWins victorias)")
        }
        val remaining = getRemainingCooldown()
        if (remaining > 0) {
            return Pair(false, "Pausa de Cooldown: ${remaining}s")
        }
        return Pair(true, "Listo para operar")
    }

    @Synchronized
    fun canTrade(): Boolean = canExecuteTrade().first

    /**
     * Reanuda la operativa tras alcanzar Stop Loss o Take Profit sin borrar el historial general (totalWins/totalLosses).
     */
    @Synchronized
    fun resetStreakOnly() {
        currentLossStreak = 0
        currentWins = 0
        lastTradeTime = 0L
        clearPendingTrade()
    }

    @Synchronized
    fun resumeAfterTarget() {
        resetStreakOnly()
    }

    @Synchronized
    fun recordTradeSent(action: TradeAction, entryPriceY: Float = 0f, baseBalance: Double = 0.0) {
        val now = System.currentTimeMillis()
        lastTradeTime = now
        hasPendingTrade = true
        pendingTradeAction = action
        pendingTradeStartTime = now
        pendingTradeEntryPriceY = entryPriceY
        pendingTradeBaseBalance = baseBalance
    }

    @Synchronized
    fun notifyTradePlaced(action: TradeAction, entryPriceY: Float = 0f, baseBalance: Double = 0.0) {
        recordTradeSent(action, entryPriceY, baseBalance)
    }

    @Synchronized
    fun clearPendingTrade() {
        hasPendingTrade = false
        pendingTradeAction = null
        pendingTradeStartTime = 0L
        pendingTradeEntryPriceY = 0f
        pendingTradeBaseBalance = 0.0
    }

    @Synchronized
    fun recordTradeWins(count: Int = 1) {
        if (!hasPendingTrade) {
            android.util.Log.w("RiskManager", "recordTradeWins ignorado: No hay trade pendiente (llamada duplicada bloqueada)")
            return
        }
        val safeCount = count.coerceAtLeast(1)
        currentWins += safeCount
        totalWins += safeCount
        currentLossStreak = 0
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
    }

    @Synchronized
    fun recordTradeLosses(count: Int = 1) {
        if (!hasPendingTrade) {
            android.util.Log.w("RiskManager", "recordTradeLosses ignorado: No hay trade pendiente (llamada duplicada bloqueada)")
            return
        }
        val safeCount = count.coerceAtLeast(1)
        totalLosses += safeCount
        currentLossStreak += safeCount
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
    }

    @Synchronized
    fun recordTradeWin() = recordTradeWins(1)

    @Synchronized
    fun recordTradeLoss() = recordTradeLosses(1)

    @Synchronized
    fun resetStats() {
        totalWins = 0
        totalLosses = 0
        currentWins = 0
        currentLossStreak = 0
    }

    @Synchronized
    fun setStats(wins: Int, losses: Int) {
        totalWins = wins.coerceAtLeast(0)
        totalLosses = losses.coerceAtLeast(0)
        currentWins = wins.coerceAtLeast(0)
        currentLossStreak = 0
    }

    @Synchronized
    fun correctStats(wins: Int, losses: Int) {
        setStats(wins, losses)
        OverlayService.instance?.updateHUDView()
        android.util.Log.i("RiskManager", "Marcador corregido a: $wins W | $losses L")
    }

    @Synchronized
    fun getWinRate(): Float {
        val total = totalWins + totalLosses
        if (total == 0) return 0f
        return (totalWins.toFloat() / total) * 100f
    }

    @Synchronized
    fun getCurrentInvestmentAmount(): Float {
        if (!martingaleEnabled || currentLossStreak == 0) return baseAmount
        val effectiveLevel = currentLossStreak.coerceAtMost(maxMartingaleLevel)
        var amount = baseAmount
        for (i in 0 until effectiveLevel) {
            amount *= martingaleMultiplier
        }
        return amount
    }

    @Synchronized
    fun getMartingaleStatusBadge(): String {
        if (!martingaleEnabled) return "[OFF]"
        val level = "M$currentLossStreak"
        val amt = getCurrentInvestmentAmount()
        return "[$level | $$amt]"
    }

    @Synchronized
    fun resetSession(startBal: Double = 0.0) {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        lastTradeTime = 0L
        if (startBal > 0.0) sessionStartBalance = startBal
        clearPendingTrade()
    }
}
