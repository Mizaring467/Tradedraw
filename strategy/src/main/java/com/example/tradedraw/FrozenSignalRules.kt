package com.example.tradedraw

import com.example.tradedraw.core.*

internal fun calcularPoderesDeMercado(
    trend: TrendDirection,
    distSupportRatio: Float,
    distResistanceRatio: Float,
    rsi: Double,
    isBullishImpulse: Boolean,
    isBearishImpulse: Boolean
): Pair<Int, Int> {
    var callPower = 50
    var putPower = 50
    when (trend) {
        TrendDirection.UPTREND -> {
            callPower = 68
            putPower = 32
            if (distSupportRatio <= 0.25f) callPower += 10
            if (rsi in 40.0..68.0) callPower += 8
            if (isBullishImpulse) callPower += 6
        }
        TrendDirection.DOWNTREND -> {
            callPower = 32
            putPower = 68
            if (distResistanceRatio <= 0.25f) putPower += 10
            if (rsi in 32.0..60.0) putPower += 8
            if (isBearishImpulse) putPower += 6
        }
        TrendDirection.SIDEWAYS -> {
            if (distSupportRatio <= 0.20f) { callPower = 62; putPower = 38 }
            else if (distResistanceRatio <= 0.20f) { callPower = 38; putPower = 62 }
        }
    }
    callPower = callPower.coerceIn(15, 95)
    putPower = putPower.coerceIn(15, 95)
    // Termometro coherente: CALL + PUT = 100. El lado dominante manda; el otro es su complemento.
    if (callPower >= putPower) putPower = 100 - callPower else callPower = 100 - putPower
    return callPower to putPower
}

internal fun motivoVetoPorAgotamientoDeCompra(
    action: TradeAction,
    trend: TrendDirection,
    callPower: Int
): String? {
    if (action != TradeAction.BUY) return null
    if (trend != TrendDirection.UPTREND) return null
    if (callPower < StrategyReplayPort.VETO_BUY_UPTREND_MAX_CALL_POWER) return null
    return "VETO_BUY_EXHAUSTION_CLIMAX UPTREND con callPower >= 70% (callPower=$callPower)"
}
