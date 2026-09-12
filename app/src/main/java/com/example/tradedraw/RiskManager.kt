package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences

enum class MoneyManagementMode {
    MARTINGALE,
    SOROS_COMPOUNDING,
    FIXED_AMOUNT
}

class RiskManager(context: Context? = null) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences("TradeDraw_RiskConfig", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_PENDING_TRADE_TIMEOUT_SEC = 75L
        const val DEFAULT_COOLDOWN_SECONDS = 10
        const val DEFAULT_LOSS_COOLDOWN_SECONDS = 180
        const val DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS = 35 // Cooldown en modo continuo/YOLO (30 a 45s max)
        const val DEFAULT_STOP_LOSS_STREAK = 3
        const val DEFAULT_TAKE_PROFIT_WINS = 20 // 20 victorias por bloque (0 = Ilimitado)
        const val DEFAULT_MAX_MARTINGALE_LEVEL = 1
        const val DEFAULT_MARTINGALE_MULTIPLIER = 2.0f
        const val SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE = 0.85f // Umbral A+ para Martingala M1 (85%)
    }

    @Volatile
    var selectiveM1MinConfidence: Float = prefs?.getFloat("selective_m1_min_confidence", SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE) ?: SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE
        set(value) {
            field = value
            prefs?.edit()?.putFloat("selective_m1_min_confidence", value)?.apply()
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
    var yoloLossCooldownSeconds: Int = prefs?.getInt("yolo_loss_cooldown_sec", DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS) ?: DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS
        set(value) {
            field = value.coerceIn(30, 45)
            prefs?.edit()?.putInt("yolo_loss_cooldown_sec", field)?.apply()
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
    var martingaleEnabled: Boolean = prefs?.getBoolean("martingale_on", true) ?: true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("martingale_on", value)?.apply()
        }

    @Volatile
    var martingaleMultiplier: Float = prefs?.getFloat("martingale_mult", DEFAULT_MARTINGALE_MULTIPLIER) ?: DEFAULT_MARTINGALE_MULTIPLIER
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

    @Volatile
    var moneyManagementMode: MoneyManagementMode = try {
        MoneyManagementMode.valueOf(prefs?.getString("mm_mode", MoneyManagementMode.MARTINGALE.name) ?: MoneyManagementMode.MARTINGALE.name)
    } catch (e: Exception) {
        MoneyManagementMode.MARTINGALE
    }
        set(value) {
            field = value
            prefs?.edit()?.putString("mm_mode", value.name)?.apply()
        }

    @Volatile
    var sorosCycleTarget: Int = prefs?.getInt("soros_cycle_target", 4) ?: 4
        set(value) {
            field = value
            prefs?.edit()?.putInt("soros_cycle_target", value)?.apply()
        }

    @Volatile
    var sorosPayoutRate: Float = prefs?.getFloat("soros_payout", 0.85f) ?: 0.85f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("soros_payout", value)?.apply()
        }

    @Volatile
    var currentSorosStep: Int = 0

    @Volatile
    var completedSorosCycles: Int = 0

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

    @Volatile
    var currentSubMode: AutonomousSubMode = AutonomousSubMode.YOLO

    @Synchronized
    fun getRemainingCooldown(subMode: AutonomousSubMode? = null): Int {
        if (lastTradeTime == 0L) return 0
        val effectiveSubMode = subMode ?: currentSubMode
        val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
        val requiredLossCooldown = if (effectiveSubMode == AutonomousSubMode.YOLO) yoloLossCooldownSeconds else lossCooldownSeconds
        val requiredCooldown = if (currentLossStreak > 0) requiredLossCooldown else cooldownSeconds
        val remaining = requiredCooldown - elapsed
        return if (remaining > 0) remaining.toInt() else 0
    }

    @Synchronized
    fun canExecuteTrade(
        mode: AutoTradeMode? = null,
        subMode: AutonomousSubMode? = null,
        confidence: Float = 1.0f
    ): Pair<Boolean, String> {
        if (hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - pendingTradeStartTime) / 1000
            // Timeout de seguridad: las operaciones de 1m en Binomo duran entre 45s y 75s
            if (elapsed >= MAX_PENDING_TRADE_TIMEOUT_SEC) {
                clearPendingTrade()
            } else {
                return Pair(false, "Operación abierta en curso (${elapsed}s)")
            }
        }
        // Si falló el nivel máximo de martingala (MG1 fallido -> pérdidas consecutivas > maxMartingaleLevel),
        // forzamos pausa de enfriamiento breve incluso en YOLO para proteger la cuenta contra tilt,
        // pero en modo continuo/YOLO reducida a 30-45s máximo para no congelar al bot por 120-140s
        if (martingaleEnabled && currentLossStreak > maxMartingaleLevel) {
            val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
            val effectiveLossCooldown = if (subMode == AutonomousSubMode.YOLO) yoloLossCooldownSeconds else lossCooldownSeconds
            val remaining = effectiveLossCooldown - elapsed
            if (remaining > 0) {
                return Pair(false, "Pausa Anti-Tilt tras fallo Martingala (${remaining}s)")
            } else if (subMode == AutonomousSubMode.YOLO) {
                // En YOLO: al terminar la pausa breve de 35s, reiniciar automáticamente la racha a M0
                // para continuar operando de forma 100% autónoma sin requerir interacción táctil
                currentLossStreak = 0
            }
        }

        // En SUBMODO YOLO: Cooldown controlado tras pérdida de 30-45s máximo para permitir tomar
        // la operación Martingala M1 en la siguiente vela sin perder el impulso del mercado
        if (subMode == AutonomousSubMode.YOLO) {
            val remaining = getRemainingCooldown(subMode)
            if (remaining > 0) {
                return Pair(false, "Pausa de Cooldown YOLO: ${remaining}s")
            }
            // Martingala Selectiva M1: En nivel 1 de Martingala (M1), exigir setup A+ (confianza >= 85%)
            val normalizedConfidence = if (confidence > 1.0f) confidence / 100f else confidence
            if (martingaleEnabled && currentLossStreak == 1 && normalizedConfidence < selectiveM1MinConfidence) {
                val pct = (normalizedConfidence * 100).toInt()
                val minPct = (selectiveM1MinConfidence * 100).toInt()
                return Pair(false, "Martingala M1 requiere setup A+ (Confianza $pct% < $minPct%)")
            }
            // En YOLO opera continuamente sin detenerse permanentemente por stop loss de racha
            return Pair(true, "🚀 MODO YOLO: Operativa continua sin límites")
        }

        if (stopLossStreak > 0 && currentLossStreak >= stopLossStreak) {
            return Pair(false, "Stop Loss alcanzado ($stopLossStreak derrotas)")
        }
        if (takeProfitWins > 0 && currentWins >= takeProfitWins) {
            return Pair(false, "Take Profit alcanzado ($takeProfitWins victorias)")
        }
        val remaining = getRemainingCooldown(subMode)
        if (remaining > 0) {
            return Pair(false, "Pausa de Cooldown: ${remaining}s")
        }

        // Martingala Selectiva M1: En nivel 1 de Martingala (M1), exigir setup A+ (confianza >= 85%)
        val normalizedConfidence = if (confidence > 1.0f) confidence / 100f else confidence
        if (martingaleEnabled && currentLossStreak == 1 && normalizedConfidence < selectiveM1MinConfidence) {
            val pct = (normalizedConfidence * 100).toInt()
            val minPct = (selectiveM1MinConfidence * 100).toInt()
            return Pair(false, "Martingala M1 requiere setup A+ (Confianza $pct% < $minPct%)")
        }

        return Pair(true, "Listo para operar")
    }

    @Synchronized
    fun canTrade(confidence: Float = 1.0f): Boolean = canExecuteTrade(confidence = confidence).first

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
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            currentSorosStep += safeCount
            if (currentSorosStep >= sorosCycleTarget) {
                completedSorosCycles++
                currentSorosStep = 0
            }
        }
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
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            currentSorosStep = 0
        }
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
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            var amt = baseAmount
            for (i in 0 until currentSorosStep) {
                amt *= (1f + sorosPayoutRate)
            }
            return Math.round(amt * 100f) / 100f
        }
        if (moneyManagementMode == MoneyManagementMode.FIXED_AMOUNT || !martingaleEnabled || currentLossStreak == 0) {
            return baseAmount
        }
        val effectiveLevel = currentLossStreak.coerceAtMost(maxMartingaleLevel)
        var amount = baseAmount
        for (i in 0 until effectiveLevel) {
            amount *= martingaleMultiplier
        }
        return amount
    }

    @Synchronized
    fun getMartingaleStatusBadge(): String {
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            val amt = getCurrentInvestmentAmount()
            return "[Soros S$currentSorosStep/$sorosCycleTarget | $$amt]"
        }
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
