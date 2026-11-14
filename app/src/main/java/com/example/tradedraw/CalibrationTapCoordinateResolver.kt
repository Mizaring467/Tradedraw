package com.example.tradedraw

import com.example.tradedraw.core.TapCoordinateResolver
import com.example.tradedraw.core.TradeAction

/**
 * Implementación de TapCoordinateResolver que consulta CalibrationManager si está disponible
 * y calibrado; si no, calcula coordenadas estándar según orientación y sanea Y en vertical.
 */
class CalibrationTapCoordinateResolver(
    private val calibrationSupplier: () -> CalibrationManager?
) : TapCoordinateResolver {

    override fun resolve(
        action: TradeAction,
        isLandscape: Boolean,
        screenW: Float,
        screenH: Float
    ): Pair<Float, Float> {
        val calib = calibrationSupplier()
        val calibCoords = if (calib != null && calib.isCalibrated()) {
            if (action == TradeAction.BUY) calib.getBuyCoordinates()
            else calib.getSellCoordinates()
        } else null

        val (rawX, rawY) = if (calibCoords != null) {
            calibCoords
        } else {
            if (isLandscape) {
                if (action == TradeAction.BUY) Pair(screenW * 0.881f, screenH * 0.735f)
                else Pair(screenW * 0.881f, screenH * 0.844f)
            } else {
                if (action == TradeAction.BUY) Pair(screenW * 0.25f, screenH * 0.903f)
                else Pair(screenW * 0.75f, screenH * 0.903f)
            }
        }

        // Sanitización estricta: en modo vertical, nunca permitir clics por encima del 80.0% (fila de gráfico/indicadores)
        return if (!isLandscape && rawY < screenH * 0.80f) {
            Pair(rawX, screenH * 0.838f)
        } else {
            Pair(rawX, rawY)
        }
    }
}
