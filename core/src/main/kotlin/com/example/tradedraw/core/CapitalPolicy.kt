package com.example.tradedraw.core

/**
 * Puerto de Política de Capital P0: Evaluación de límites de riesgo de capital.
 * Desacopla la decisión de despacho de órdenes de la implementación interna de RiskManager.
 */
interface CapitalPolicy {
    fun canExecuteTrade(
        subMode: AutonomousSubMode? = null,
        confidence: Float = 1.0f
    ): Pair<Boolean, String>
}
