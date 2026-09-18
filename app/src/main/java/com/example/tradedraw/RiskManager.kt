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
        const val MAX_PENDING_TRADE_TIMEOUT_SEC = 85L
        const val DEFAULT_COOLDOWN_SECONDS = 10
        const val DEFAULT_LOSS_COOLDOWN_SECONDS = 180
        const val DEFAULT_YOLO_LOSS_COOLDOWN_SECONDS = 35 // Cooldown en modo continuo/YOLO (30 a 45s max)
        const val EXTENDED_LOSS_COOLDOWN_SECONDS = 180 // Pausa de 3m tras 2 derrotas consecutivas para salir de rango
        const val DEFAULT_STOP_LOSS_STREAK = 3
        const val DEFAULT_TAKE_PROFIT_WINS = 20 // 20 victorias por bloque (0 = Ilimitado)
        const val DEFAULT_MAX_MARTINGALE_LEVEL = 1
        const val DEFAULT_MARTINGALE_MULTIPLIER = 2.0f
        const val SELECTIVE_MARTINGALE_M1_MIN_CONFIDENCE = 0.85f // Umbral A+ para Martingala M1 (85%)
        const val SNIPER_MAX_LOSSES = 2
        const val SNIPER_TAKE_PROFIT_WINS = 2
        const val SNIPER_MAX_SESSION_TRADES = 3
    }

    @Volatile
    var sessionPeakBalance: Double = 0.0

    @Volatile
    var trailingProfitLockTriggered: Boolean = false

    @Volatile
    var trailingProfitRetracementRatio: Float = prefs?.getFloat("trailing_profit_retrace", 0.40f) ?: 0.40f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("trailing_profit_retrace", value)?.apply()
        }

    @Volatile
    var minPeakProfitToLock: Double = prefs?.getFloat("min_peak_profit_lock", 80000f)?.toDouble() ?: 80000.0
        set(value) {
            field = value
            prefs?.edit()?.putFloat("min_peak_profit_lock", value.toFloat())?.apply()
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
    var sessionMaxLossRatio: Float = prefs?.getFloat("session_max_loss_ratio", 0.03f) ?: 0.03f
        set(value) {
            field = value
            prefs?.edit()?.putFloat("session_max_loss_ratio", value)?.apply()
        }

    @Volatile
    var absoluteEquityFloor: Double = prefs?.getFloat("absolute_equity_floor", 20000000f)?.toDouble() ?: 20000000.0
        set(value) {
            field = value
            prefs?.edit()?.putFloat("absolute_equity_floor", value.toFloat())?.apply()
        }

    @Volatile
    var consecutiveVoids: Int = 0

    @Volatile
    var totalVoids: Int = 0

    @Volatile
    var timeframe: CandleTimeframe = CandleTimeframe.M1

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
    var totalWins: Int = prefs?.getInt("session_total_wins", 0) ?: 0
        set(value) {
            field = value
            prefs?.edit()?.putInt("session_total_wins", value)?.apply()
        }

    @Volatile
    var totalLosses: Int = prefs?.getInt("session_total_losses", 0) ?: 0
        set(value) {
            field = value
            prefs?.edit()?.putInt("session_total_losses", value)?.apply()
        }

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
    var pendingTradeConfidence: Float = 0.0f
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
        val requiredLossCooldown = when {
            effectiveSubMode == AutonomousSubMode.YOLO -> yoloLossCooldownSeconds
            currentLossStreak >= 2 -> EXTENDED_LOSS_COOLDOWN_SECONDS
            else -> lossCooldownSeconds
        }
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
        val currentBal = AutoTradeAccessibilityService.latestObservedBalance
        val isDemo = currentBal >= 1_000_000.0 || (AutoTradeAccessibilityService.isDemoAccount && currentBal > 500_000.0)
        if (currentBal > 0.0) {
            if (isDemo) {
                if (currentBal < absoluteEquityFloor) {
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Equity Demo () por debajo del suelo absoluto ()")
                    return Pair(false, "Stop Equity Absoluto Alcanzado ( < )")
                }
            } else {
                // Cuenta Real: suelo mínimo operativo para Binomo (mínimo ~4,000 COP por orden)
                val minBrokerStake = 4000.0
                if (currentBal < minBrokerStake) {
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Saldo real insuficiente para operar ($currentBal < $minBrokerStake)")
                    return Pair(false, "Saldo Real Insuficiente (< Col$$minBrokerStake)")
                }

                // Si la orden configurada en pantalla supera el saldo disponible, avisar claramente
                val observedStake = AutoTradeAccessibilityService.observedOrderAmount
                if (observedStake > 0.0 && observedStake > currentBal) {
                    val stakeInt = observedStake.toInt()
                    val balInt = currentBal.toInt()
                    android.util.Log.e("RiskManager", "CRITICAL STOP: Inversión en Binomo ($stakeInt COP) supera saldo real ($balInt COP)")
                    return Pair(false, "Cantidad ($stakeInt COP) supera saldo ($balInt COP). Ajusta a Col$4,000")
                }
            }

            if (sessionStartBalance == 0.0) {
                sessionStartBalance = currentBal
                sessionPeakBalance = currentBal
            } else if (currentBal > sessionPeakBalance) {
                sessionPeakBalance = currentBal
            }

            if (sessionStartBalance > 0.0) {
                val isConsistentSession = (isDemo && sessionStartBalance > 1_000_000.0) || (!isDemo && sessionStartBalance <= 1_000_000.0)
                if (isConsistentSession) {
                    val minLossAllowance = if (isDemo) 100000.0 else 8000.0
                    val maxLossAmt = maxOf(sessionStartBalance * sessionMaxLossRatio, minLossAllowance)
                    val sessionFloor = sessionStartBalance - maxLossAmt
                    if (currentBal < sessionFloor) {
                        android.util.Log.e("RiskManager", "CRITICAL STOP: Pérdida máxima de sesión alcanzada. Balance actual: $currentBal, Suelo de sesión: $sessionFloor")
                        return Pair(false, "Stop Loss Sesion Alcanzado ($currentBal < $sessionFloor)")
                    }

                    // Trailing Profit Lock: Proteger beneficios de la sesión si hubo ganancia pico significativa
                    val peakProfit = sessionPeakBalance - sessionStartBalance
                    if (peakProfit >= minPeakProfitToLock) {
                        val allowedRetracement = peakProfit * trailingProfitRetracementRatio
                        val trailingFloor = sessionPeakBalance - allowedRetracement
                        if (currentBal < trailingFloor) {
                            trailingProfitLockTriggered = true
                            android.util.Log.w("RiskManager", "TRAILING PROFIT LOCK: Beneficio asegurado. Pico: +${peakProfit.toInt()} COP, Suelo: $trailingFloor")
                            return Pair(false, "Trailing Profit Lock: Ganancia asegurada (+${peakProfit.toInt()} COP pico)")
                        }
                    }
                }
            }
        }
        
        if (consecutiveVoids >= 3) {
            android.util.Log.e("RiskManager", "CRITICAL STOP: 3 trades VOID consecutivos. Ejecución no confiable.")
            return Pair(false, "Parada: 3 trades VOID seguidos (ejecución fallida)")
        }

        if (stopLossStreak > 0 && currentLossStreak >= stopLossStreak) {
            return Pair(false, "Stop Loss alcanzado ($stopLossStreak derrotas)")
        }

        if (hasPendingTrade) {
            val elapsed = (System.currentTimeMillis() - pendingTradeStartTime) / 1000
            // Timeout de seguridad según timeframe: 85s para 1m, 330s para 5m
            val timeoutSec = if (timeframe == CandleTimeframe.M5) 330L else MAX_PENDING_TRADE_TIMEOUT_SEC
            if (elapsed >= timeoutSec) {
                clearPendingTrade()
            } else {
                return Pair(false, "Operación abierta en curso (${elapsed}s)")
            }
        }
        // Si falló el nivel máximo de martingala (MG1 fallido -> pérdidas consecutivas > maxMartingaleLevel),
        // forzamos pausa de enfriamiento breve incluso en YOLO para proteger la cuenta contra tilt,
        // pero con pausa extendida de 180s (3m) si ya van >= 2 derrotas consecutivas
        if (martingaleEnabled && currentLossStreak > maxMartingaleLevel) {
            val elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000
            val effectiveLossCooldown = if (subMode == AutonomousSubMode.YOLO) yoloLossCooldownSeconds else if (currentLossStreak >= 2) EXTENDED_LOSS_COOLDOWN_SECONDS else lossCooldownSeconds
            val remaining = effectiveLossCooldown - elapsed
            if (remaining > 0) {
                return Pair(false, "Pausa Anti-Tilt tras fallo Martingala (${remaining}s)")
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

        // En SUBMODO FRANCOTIRADOR (SNIPER):
        // 1. Veto a activos sintéticos / OTC y validación estricta de par Forex real
        // 2. Stop Loss diario estricto: máximo 2 pérdidas consecutivas o acumuladas en la sesión
        // 3. Take Profit diario: 2 victorias
        // 4. Máximo 3 operaciones ejecutadas por sesión diaria (acumulando W + L + Voids)
        if (subMode == AutonomousSubMode.SNIPER) {
            val currentAsset = AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = AutoTradeAccessibilityService.isSyntheticOrOTC ||
                currentAsset.contains("IDX", ignoreCase = true) ||
                currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic) {
                return Pair(false, "⚠️ Veto Francotirador: Activo sintético/OTC ($currentAsset) prohibido para dinero real. Selecciona un par Forex real en Binomo")
            }
            val classification = AutoTradeAccessibilityService.classifyAsset(currentAsset)
            if (classification != AssetClassification.FOREX_REAL) {
                return Pair(false, "⚠️ Francotirador: Activo ($currentAsset) no es un par Forex real válido. Selecciona EUR/USD, GBP/USD, etc.")
            }

            if (currentLossStreak >= SNIPER_MAX_LOSSES || totalLosses >= SNIPER_MAX_LOSSES) {
                return Pair(false, "🛑 Stop Loss Francotirador alcanzado ($totalLosses derrotas). Sesión finalizada.")
            }

            if (totalWins >= SNIPER_TAKE_PROFIT_WINS || currentWins >= SNIPER_TAKE_PROFIT_WINS) {
                return Pair(false, "🎯 Take Profit Francotirador alcanzado ($totalWins victorias). Meta diaria cumplida.")
            }

            val sessionTrades = totalWins + totalLosses + totalVoids
            if (sessionTrades >= SNIPER_MAX_SESSION_TRADES) {
                return Pair(false, "🛑 Límite diario Francotirador alcanzado ($sessionTrades/3 operaciones). Sesión finalizada.")
            }

            val remaining = getRemainingCooldown(subMode)
            if (remaining > 0) {
                return Pair(false, "Pausa de Cooldown Francotirador: ${remaining}s")
            }

            return Pair(true, "🎯 MODO FRANCOTIRADOR: Listo para disparo de alta precisión")
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
    fun recordTradeSent(action: TradeAction, entryPriceY: Float = 0f, baseBalance: Double = 0.0, confidence: Float = 0.0f) {
        val now = System.currentTimeMillis()
        lastTradeTime = now
        hasPendingTrade = true
        pendingTradeAction = action
        pendingTradeStartTime = now
        pendingTradeEntryPriceY = entryPriceY
        pendingTradeBaseBalance = baseBalance
        pendingTradeConfidence = confidence
    }

    @Synchronized
    fun notifyTradePlaced(action: TradeAction, entryPriceY: Float = 0f, baseBalance: Double = 0.0, confidence: Float = 0.0f) {
        recordTradeSent(action, entryPriceY, baseBalance, confidence)
    }

    @Synchronized
    fun clearPendingTrade() {
        hasPendingTrade = false
        pendingTradeAction = null
        pendingTradeStartTime = 0L
        pendingTradeEntryPriceY = 0f
        pendingTradeBaseBalance = 0.0
        pendingTradeConfidence = 0.0f
    }

    @Synchronized
    fun recordTradeVoid(count: Int = 1) {
        val safeCount = count.coerceAtLeast(1)
        consecutiveVoids += safeCount
        totalVoids += safeCount
        lastTradeTime = System.currentTimeMillis()
        clearPendingTrade()
    }

    @Synchronized
    fun recordTradeWins(count: Int = 1) {
        if (!hasPendingTrade) {
            android.util.Log.w("RiskManager", "recordTradeWins ignorado: No hay trade pendiente (llamada duplicada bloqueada)")
            return
        }
        
        val currentBal = AutoTradeAccessibilityService.latestObservedBalance
        if (pendingTradeBaseBalance > 0.0 && currentBal > 0.0) {
            val diff = Math.abs(currentBal - pendingTradeBaseBalance)
            if (diff <= 10.0) {
                android.util.Log.w("RiskManager", "Interceptado WIN falso (Diff=$diff). Convirtiendo a VOID.")
                recordTradeVoid(count)
                return
            }
        }

        consecutiveVoids = 0
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
        
        val currentBal = AutoTradeAccessibilityService.latestObservedBalance
        if (pendingTradeBaseBalance > 0.0 && currentBal > 0.0) {
            val diff = Math.abs(currentBal - pendingTradeBaseBalance)
            if (diff <= 10.0) {
                android.util.Log.w("RiskManager", "Interceptado LOSS falso (Diff=$diff). Convirtiendo a VOID.")
                recordTradeVoid(count)
                return
            }
        }

        consecutiveVoids = 0
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
        consecutiveVoids = 0
        totalVoids = 0
    }

    @Synchronized
    fun setStats(wins: Int, losses: Int) {
        totalWins = wins.coerceAtLeast(0)
        totalLosses = losses.coerceAtLeast(0)
        currentWins = wins.coerceAtLeast(0)
        currentLossStreak = 0
        consecutiveVoids = 0
        totalVoids = 0
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
    fun getCurrentInvestmentAmount(subMode: AutonomousSubMode? = null): Float {
        val effectiveSubMode = subMode ?: currentSubMode
        if (effectiveSubMode == AutonomousSubMode.SNIPER) {
            return baseAmount // 100% libre de Martingala (M0 plano)
        }
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
    fun getMartingaleStatusBadge(subMode: AutonomousSubMode? = null): String {
        val effectiveSubMode = subMode ?: currentSubMode
        if (effectiveSubMode == AutonomousSubMode.SNIPER) {
            val amt = getCurrentInvestmentAmount(subMode)
            return "[🎯 M0 Plano | $$amt]"
        }
        if (moneyManagementMode == MoneyManagementMode.SOROS_COMPOUNDING) {
            val amt = getCurrentInvestmentAmount(subMode)
            return "[Soros S$currentSorosStep/$sorosCycleTarget | $$amt]"
        }
        if (!martingaleEnabled) return "[OFF]"
        val level = "M$currentLossStreak"
        val amt = getCurrentInvestmentAmount(subMode)
        return "[$level | $$amt]"
    }

    @Synchronized
    fun resetSession(startBal: Double = 0.0) {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        consecutiveVoids = 0
        totalVoids = 0
        lastTradeTime = 0L
        if (startBal > 0.0) {
            sessionStartBalance = startBal
            sessionPeakBalance = startBal
        } else {
            sessionStartBalance = 0.0
            sessionPeakBalance = 0.0
        }
        trailingProfitLockTriggered = false
        clearPendingTrade()
    }

    @Synchronized
    fun resetSniperSession() {
        currentLossStreak = 0
        currentWins = 0
        totalWins = 0
        totalLosses = 0
        totalVoids = 0
        consecutiveVoids = 0
        lastTradeTime = 0L
        clearPendingTrade()
    }
}
