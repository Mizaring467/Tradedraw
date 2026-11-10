package com.example.tradedraw.core

/**
 * Seam E4: Resuelve coordenadas en pantalla para las acciones de trading (BUY / SELL).
 * Desacopla la lógica de coordenadas y orientación de Android Context.
 */
interface TapCoordinateResolver {
    fun resolve(
        action: TradeAction,
        isLandscape: Boolean,
        screenW: Float,
        screenH: Float
    ): Pair<Float, Float>
}
