package com.example.tradedraw

/**
 * Instantánea inmutable de todo lo que el HUD necesita pintar.
 *
 * Ronda 11 (T4) — causa raíz: `OverlayService` leía `tradingEngine.latestAnalysisResult` (resultado
 * de la ruta de VISIÓN, retirada por batería) para pintar racha, termómetro y filtro S/R. En modo
 * WebSocket puro ese objeto quedaba con los valores por defecto (`signalPowerCall=50`,
 * `signalPowerPut=50`, `streakBadge=""`), así que el HUD mostraba un termómetro 50/50 y una racha
 * inventada como si fueran análisis reales.
 *
 * El motor construye esta instantánea con la fuente viva (feed WebSocket + motor sintético +
 * RiskManager) y el HUD SOLO pinta. Cada campo indica explícitamente si hay dato; el HUD muestra
 * "—" cuando no lo hay, nunca un valor por defecto disfrazado de medición.
 */
data class HudSnapshot(
    // --- Feed WebSocket (fuente única en headless) ---
    val assetLabel: String,
    val friendlyAsset: String,
    /** true si hay tick y es fresco (dentro de la ventana de frescura del feed). */
    val hasFreshTick: Boolean,
    val price: Double,
    val tickAgeMs: Long,
    val wsState: WebSocketState,
    val tickSource: String,
    /** Ticks descartados por pertenecer a otro activo (integridad de datos, T1). */
    val rejectedForeignAssetTicks: Long,
    val rejectedSanityJumpTicks: Long,

    // --- Motor sintético ---
    val trend: TrendDirection,
    /** Racha real; null si no hay dato suficiente (el HUD pinta "—"). */
    val streakBadge: String?,
    /** Termómetro CALL/PUT; null si no hay dato (NUNCA 50/50 por defecto). */
    val callPct: Int?,
    val putPct: Int?,
    val velocity: Float,
    val smoothedVelocity: Float,
    /** false si el tick está viejo o no hay tick: la velocidad no se muestra como actual. */
    val velocityIsLive: Boolean,

    // --- Razonamiento ---
    val patternName: String,
    val probabilityPct: Int,
    val srFilterStatus: String,
    val actionPlan: String,

    // --- Riesgo ---
    val totalWins: Int,
    val totalLosses: Int,
    val totalVoids: Int,
    val winRate: Double,
    val martingaleBadge: String,
    val cooldownRemainingSec: Long,
    val hasPendingTrade: Boolean,
    val pendingAction: TradeAction?,
    val pendingElapsedSec: Long,
    val pendingEntryPrice: Double,
    /** Moneyness real de la posición abierta; null si no hay posición. */
    val pendingMoneyness: TradeMoneyness?
) {
    /** Termómetro listo para pintar, o null si no hay medición. */
    val thermometroTexto: String?
        get() {
            val call = callPct ?: return null
            val put = putPct ?: return null
            val bars = (call / 10).coerceIn(1, 9)
            return "[ $call% CALL ${"█".repeat(bars)}${"░".repeat(10 - bars)} $put% PUT ]"
        }
}
