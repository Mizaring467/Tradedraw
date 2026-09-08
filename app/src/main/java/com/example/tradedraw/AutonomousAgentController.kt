package com.example.tradedraw

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Agente Autónomo de TradeDraw:
 * - Supervisa la coherencia financiera entre el broker (Binomo) y el HUD en tiempo real.
 * - Auto-corrige discrepancias de balance, victorias y derrotas de forma proactiva.
 * - Auto-adapta estrategias y soportes/resistencias cuando detecta rachas de pérdidas.
 * - Reanuda la operación autónoma sin requerir interacción manual del usuario.
 */
class AutonomousAgentController(
    private val context: Context,
    private val tradingEngine: TradingEngine,
    private val riskManager: RiskManager
) {
    companion object {
        private const val TAG = "AutonomousAgent"
        private const val AUDIT_INTERVAL_MS = 2500L
        private const val BALANCE_DIFF_THRESHOLD = 50.0 // Mínimo cambio en COP para considerar transacción real
    }

    private val thread = HandlerThread("AgentControllerThread").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isRunning = false
        private set

    @Volatile
    private var lastAuditedBalance: Double = 0.0

    @Volatile
    private var consecutiveStagnantCycles = 0

    private val auditRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                try {
                    auditFinancialSync()
                    auditEngineHealth()
                    auditStrategyAdaptation()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en ciclo de auditoría del agente", e)
                }
                handler.postDelayed(this, AUDIT_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastAuditedBalance = AutoTradeAccessibilityService.instance?.readCurrentBalance() ?: 0.0
        handler.post(auditRunnable)
        Log.i(TAG, "Agente Autónomo iniciado con éxito")
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Agente Autónomo detenido")
    }

    /**
     * 1. Reconciliación Financiera Automática:
     * Compara el saldo real de Binomo con el estado de TradeDraw.
     * Si Binomo acreditó una ganancia que la app no registró, la auto-corrige inmediatamente como WIN.
     * Si hubo una deducción no contada, auto-corrige como LOSS y ajusta Martingala.
     */
    private fun auditFinancialSync() {
        val accessibility = AutoTradeAccessibilityService.instance ?: return
        val currentBal = accessibility.readCurrentBalance() ?: return
        if (currentBal <= 0.0) return

        if (lastAuditedBalance <= 0.0) {
            lastAuditedBalance = currentBal
            return
        }

        // Solo reconciliar si no hay un trade en ventana de resolución activa (55s a 70s)
        if (!riskManager.hasPendingTrade) {
            val diff = currentBal - lastAuditedBalance

            // Caso A: Saldo subió significativamente fuera de un trade registrado
            if (diff > BALANCE_DIFF_THRESHOLD) {
                Log.w(TAG, "Reconciliación: Ganancia no contabilizada en Binomo (+$$diff). Auto-corrigiendo a +1 WIN.")
                mainHandler.post {
                    riskManager.recordTradeWin()
                    OverlayService.instance?.updateHUDView()
                    Toast.makeText(context, "🤖 AGENTE: Victoria detectada en broker (+$$diff). Saldo y HUD sincronizados.", Toast.LENGTH_SHORT).show()
                }
                lastAuditedBalance = currentBal
            }
            // Caso B: Saldo disminuyó significativamente fuera de un trade registrado
            else if (diff < -BALANCE_DIFF_THRESHOLD) {
                Log.w(TAG, "Reconciliación: Deducción no contabilizada en Binomo ($$diff). Auto-corrigiendo a +1 LOSS.")
                mainHandler.post {
                    riskManager.recordTradeLoss()
                    OverlayService.instance?.updateHUDView()
                    Toast.makeText(context, "🤖 AGENTE: Deducción registrada en broker ($$diff). Martingala auto-ajustada.", Toast.LENGTH_SHORT).show()
                }
                lastAuditedBalance = currentBal
            } else {
                lastAuditedBalance = currentBal
            }
        }
    }

    /**
     * 2. Auto-Adaptación de Estrategia:
     * Si la estrategia actual acumula 2 pérdidas consecutivas, el agente conmuta de forma proactiva
     * a una estrategia más segura y solicita recálculo dinámico de S/R.
     */
    private fun auditStrategyAdaptation() {
        if (tradingEngine.mode != AutoTradeMode.AUTONOMOUS) return

        val lossStreak = riskManager.currentLossStreak
        if (lossStreak >= 2) {
            if (tradingEngine.strategy != AutoTradeStrategy.AUTO_ADAPTIVE) {
                Log.i(TAG, "Racha de derrotas ($lossStreak). Conmutando estrategia automáticamente a AUTO_ADAPTIVE.")
                mainHandler.post {
                    tradingEngine.strategy = AutoTradeStrategy.AUTO_ADAPTIVE
                    tradingEngine.unlockAllLines()
                    OverlayService.instance?.updateHUDView()
                    Toast.makeText(context, "🤖 AGENTE: Racha negativa. Conmutando automáticamente a AUTO_ADAPTIVE y recalculando niveles.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 3. Supervisión de Estados Colgados:
     * Si la app quedó en pausa o cooldown innecesario, asegurar que el motor siga activo vigilando.
     */
    private fun auditEngineHealth() {
        if (tradingEngine.mode == AutoTradeMode.AUTONOMOUS && !riskManager.hasPendingTrade) {
            consecutiveStagnantCycles++
            // Cada 60 segundos de inactividad en rango, forzar refresco de soportes/resistencias
            if (consecutiveStagnantCycles >= 24) {
                consecutiveStagnantCycles = 0
                mainHandler.post {
                    tradingEngine.unlockAllLines()
                    Log.d(TAG, "Auto-refresco periódico de niveles de S/R por inactividad")
                }
            }
        } else {
            consecutiveStagnantCycles = 0
        }
    }
}
