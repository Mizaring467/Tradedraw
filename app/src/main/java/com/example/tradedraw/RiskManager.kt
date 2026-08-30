package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences

class RiskManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("TradeDraw_RiskConfig", Context.MODE_PRIVATE)

    var stopLossStreak: Int = prefs.getInt("sl_streak", 3)
        set(value) { field = value; prefs.edit().putInt("sl_streak", value).apply() }

    var takeProfitWins: Int = prefs.getInt("tp_wins", 5)
        set(value) { field = value; prefs.edit().putInt("tp_wins", value).apply() }

    var cooldownSeconds: Int = prefs.getInt("cooldown_sec", 60)
        set(value) { field = value; prefs.edit().putInt("cooldown_sec", value).apply() }

    var martingaleEnabled: Boolean = prefs.getBoolean("martingale_on", false)
        set(value) { field = value; prefs.edit().putBoolean("martingale_on", value).apply() }

    var martingaleMultiplier: Float = prefs.getFloat("martingale_mult", 2.0f)
        set(value) { field = value; prefs.edit().putFloat("martingale_mult", value).apply() }

    var baseAmount: Float = prefs.getFloat("base_amount", 1.0f)
        set(value) { field = value; prefs.edit().putFloat("base_amount", value).apply() }

    // Estado en vivo de la sesión
    var currentLossStreak: Int = 0
        private set
    var currentWins: Int = 0
        private set
    var totalWins: Int = 0
        private set
    var totalLosses: Int = 0
        private set
    var lastTradeTime: Long = 0L
        private set

    fun getRemainingCooldown(): Int {
        if (lastTradeTime == 0L) return 0
        val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
        val remaining = cooldownSeconds - elapsed
        return if (remaining > 0) remaining.toInt() else 0
    }

    fun canExecuteTrade(): Pair<Boolean, String> {
        val remaining = getRemainingCooldown()
        if (remaining > 0) {
            return Pair(false, "Pausa de Cooldown activa: ${remaining}s")
        }
        if (currentLossStreak >= stopLossStreak) {
            return Pair(false, "Stop Loss alcanzado ($stopLossStreak pérdidas)")
        }
        if (currentWins >= takeProfitWins) {
            return Pair(false, "Take Profit alcanzado ($takeProfitWins ganancias)")
        }
        return Pair(true, "Listo para operar")
    }

    fun recordTradeSent() {
        lastTradeTime = System.currentTimeMillis()
    }

    fun recordTradeWin() {
        currentWins++
        totalWins++
        currentLossStreak = 0
    }

    fun recordTradeLoss() {
        totalLosses++
        currentLossStreak++
    }

    fun getWinRate(): Float {
        val total = totalWins + totalLosses
        if (total == 0) return 0f
        return (totalWins.toFloat() / total) * 100f
    }

    fun getCurrentInvestmentAmount(): Float {
        if (!martingaleEnabled || currentLossStreak == 0) return baseAmount
        var amount = baseAmount
        for (i in 0 until currentLossStreak) {
            amount *= martingaleMultiplier
        }
        return amount
    }

    fun resetSession() {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        lastTradeTime = 0L
    }
}
