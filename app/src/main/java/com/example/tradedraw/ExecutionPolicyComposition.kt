package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.execution.ExecutionPolicy
import com.example.tradedraw.execution.ExecutionPolicyHooks

/** Frozen predicates remain single implementations; mandatory hooks prevent an unconfigured policy. */
fun createExecutionPolicy() = ExecutionPolicy(object : ExecutionPolicyHooks {
    override fun rangeThreshold(asset: String) = TradingEngine.rangoRelativoMinimoParaActivo(asset)
    override fun assetMismatch(activoPantallaVisible: String, activoFeed: String) = TradingEngine.motivoVetoPorDesajusteDeActivo(activoPantallaVisible, activoFeed)
    override fun adaptiveDetail(reason: String, signal: String) = buildAdaptiveBlockDetail(reason, signal)
    override fun buyExhaustion(action: TradeAction, trend: TrendDirection, power: Int) = motivoVetoPorAgotamientoDeCompra(action, trend, power)
    override fun actionConflict(srStatus: String, actionPlan: String) = HudCoherence.actionConflict(srStatus, actionPlan)
})
