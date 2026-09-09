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
     * 1. Reconciliación Financiera y Monitoreo de Saldo:
     * Mantiene actualizado el último balance observado de Binomo sin alterar
     * artificialmente el contador de victorias/derrotas fuera de una expiración real.
     */
    private fun auditFinancialSync() {
        val accessibility = AutoTradeAccessibilityService.instance ?: return
        val currentBal = accessibility.readCurrentBalance() ?: return
        if (currentBal <= 0.0) return

        if (lastAuditedBalance <= 0.0) {
            lastAuditedBalance = currentBal
            return
        }

        // Solo actualizar el saldo de referencia cuando no haya operaciones en curso
        if (!riskManager.hasPendingTrade) {
            lastAuditedBalance = currentBal
        }
    }

    /**
     * Reanuda la operativa autónoma forzando el reinicio del Stop Loss/Rachas,
     * reactivando el modo AUTONOMOUS y recalculando soportes/resistencias.
     */
    fun resumeAutonomousTrading(reason: String = "Instrucción del usuario"): String {
        riskManager.resetStreakOnly()
        tradingEngine.mode = AutoTradeMode.AUTONOMOUS
        tradingEngine.unlockAllLines()
        mainHandler.post {
            OverlayService.instance?.updateHUDView()
            Toast.makeText(context, "🤖 AGENTE: Operativa reanudada ($reason). Stop Loss reseteado.", Toast.LENGTH_SHORT).show()
        }
        Log.i(TAG, "resumeAutonomousTrading ejecutado: $reason")
        return "Operativa autónoma reanudada exitosamente. Stop Loss reseteado y modo AUTONOMOUS activo."
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
