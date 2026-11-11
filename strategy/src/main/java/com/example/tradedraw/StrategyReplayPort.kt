package com.example.tradedraw

import com.example.tradedraw.core.*

/** Shared frozen rules for production and replay. No copies of strategy formulas. */
object StrategyReplayPort {
    const val VETO_BUY_UPTREND_MAX_CALL_POWER = 70
    fun calcularPoderesDeMercado(trend: TrendDirection, distSupportRatio: Float, distResistanceRatio: Float,
        rsi: Double, isBullishImpulse: Boolean, isBearishImpulse: Boolean) =
        com.example.tradedraw.calcularPoderesDeMercado(trend, distSupportRatio, distResistanceRatio, rsi, isBullishImpulse, isBearishImpulse)
    fun motivoVetoPorAgotamientoDeCompra(action: TradeAction, trend: TrendDirection, callPower: Int) =
        com.example.tradedraw.motivoVetoPorAgotamientoDeCompra(action, trend, callPower)
}
