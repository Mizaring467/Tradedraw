package com.example.tradedraw

/**
 * Invariantes de coherencia del HUD (Ronda 11, T7).
 *
 * El HUD llegó a mostrar combinaciones mutuamente excluyentes:
 *  - `Tendencia: ALCISTA 86%` junto a un termómetro de mayoría PUT,
 *  - `Filtro S/R: Toque Directo en Soporte [CALL Óptimo]` con el precio en el centro del canal,
 *  - `Patrón: STANDBY (RUIDO)` junto a `Trade Activo: PUT ▼`.
 *
 * Estas funciones definen qué es coherente, para que el HUD pueda marcar el conflicto en lugar de
 * presentarlo como análisis. Son puras y sin estado: se prueban en `HudSnapshotTest`.
 */
object HudCoherence {

    /** Porcentaje a partir del cual el termómetro se considera direccional. */
    private const val DIRECTIONAL_PCT = 55

    /** Distancia máxima al borde del canal para poder afirmar "toque directo". */
    const val TOUCH_RATIO_LIMIT = 0.15f

    /**
     * true si la instantánea contiene una contradicción interna que el HUD debe avisar.
     *
     * Comprobaciones:
     *  1. Tendencia alcista con mayoría PUT (o bajista con mayoría CALL) en el termómetro.
     *  2. Posición abierta cuya dirección contradice la tendencia declarada.
     */
    fun isIncoherent(hud: HudSnapshot): Boolean {
        val call = hud.callPct
        val put = hud.putPct
        if (call != null && put != null) {
            when (hud.trend) {
                TrendDirection.UPTREND -> if (put >= DIRECTIONAL_PCT && put > call) return true
                TrendDirection.DOWNTREND -> if (call >= DIRECTIONAL_PCT && call > put) return true
                TrendDirection.SIDEWAYS -> Unit
            }
        }
        // Posición abierta contra una tendencia declarada y direccional.
        if (hud.hasPendingTrade && hud.callPct != null && hud.putPct != null) {
            when (hud.trend) {
                TrendDirection.UPTREND -> if (hud.pendingAction == TradeAction.SELL) return true
                TrendDirection.DOWNTREND -> if (hud.pendingAction == TradeAction.BUY) return true
                TrendDirection.SIDEWAYS -> Unit
            }
        }
        return false
    }

    /**
     * true si el texto de estado S/R es compatible con la distancia real al soporte.
     *
     * Un "toque directo" solo es válido dentro del [TOUCH_RATIO_LIMIT] del canal. Cualquier otro
     * texto (neutral, cerca, guion) no exige validación.
     */
    fun srStatusIsCoherent(srStatus: String, distToSupportRatio: Float): Boolean {
        val claimsTouch = srStatus.contains("Toque Directo", ignoreCase = true)
        if (!claimsTouch) return true
        return distToSupportRatio <= TOUCH_RATIO_LIMIT
    }

    /** Marca textual de conflicto para el HUD; vacía si no hay conflicto. */
    fun conflictBadge(hud: HudSnapshot): String =
        if (isIncoherent(hud)) "⚠ contra-tendencia" else ""
}
