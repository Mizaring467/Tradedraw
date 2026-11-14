package com.example.tradedraw.legacy

import com.example.tradedraw.AdaptiveDecision
import com.example.tradedraw.HudCoherence
import com.example.tradedraw.TradingEngine
import com.example.tradedraw.TrendDirection
import com.example.tradedraw.buildAdaptiveBlockDetail
import com.example.tradedraw.core.*
import com.example.tradedraw.motivoVetoPorAgotamientoDeCompra
import java.util.Locale

import com.example.tradedraw.execution.ExecutionContext
import com.example.tradedraw.execution.ExecutionPolicyResult

// Frozen oracle from 82afad5 (pre-integration evaluator, matching archived inline cascade).
// Non-null assertion only adapts Kotlin cross-module smart casting; the preceding null guard is unchanged.
class LegacyExecutionPolicyOracle {

    /**
     * Evalúa los 13 vetos congelados en estricto orden secuencial con mensajes byte-a-byte idénticos.
     *
     * CARVE-OUT DOCUMENTADO: El sitio :1867 (`handleSignal` en TradingEngine) queda fuera de esta
     * cascada por contención de alcance. Esta política gobierna la cascada formal de ejecución (13 vetos).
     */
    fun evaluate(ctx: ExecutionContext): ExecutionPolicyResult {
        // 1. FEED RANCIO (:2210) + FEED_MUDO (:2216)
        if (ctx.tickAgeMs == null || ctx.tickAgeMs!! >= ctx.feedMaxAgeMs || !ctx.isFeedFresh) {
            val asset = ctx.observedAsset.ifBlank { "?" }
            val msg = "FEED_RANCIO ageMs=${ctx.tickAgeMs ?: -1} activo=$asset"
            val secondaryMsg = if (ctx.tickAgeMs == null) "activo=$asset sin_ningun_tick" else null
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 1,
                message = msg,
                secondaryEventKind = if (ctx.tickAgeMs == null) "FEED_MUDO" else null,
                secondaryEventMessage = secondaryMsg
            )
        }

        // 2. PRECIO CONGELADO V2 (:2251)
        val currentObservedAsset = ctx.observedAsset.ifBlank { "Crypto IDX" }
        val umbralMinimo = TradingEngine.rangoRelativoMinimoParaActivo(currentObservedAsset)
        if (ctx.rangoRelativo >= 0.0 && ctx.rangoRelativo < umbralMinimo) {
            val msg = "V2 rango=%.3e umbral=%.0e activo=%s".format(
                Locale.US, ctx.rangoRelativo, umbralMinimo, currentObservedAsset
            )
            return ExecutionPolicyResult.Vetoed(vetoIndex = 2, message = msg)
        }

        // 3. CONGELACIÓN DECLARADA POR WS (:2269)
        if (ctx.isPriceFrozen) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 3,
                message = "CONGELADO activo=$currentObservedAsset"
            )
        }

        // 4. DESAJUSTE PANTALLA <-> FEED (:2300)
        val motivoDesajuste = TradingEngine.motivoVetoPorDesajusteDeActivo(
            activoPantallaVisible = ctx.observedAsset,
            activoFeed = ctx.feedAsset
        )
        if (motivoDesajuste != null) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 4,
                message = motivoDesajuste,
                incrementVetoActivoDesajustado = true
            )
        }

        // 5. 1 ORDEN POR VELA (:2308)
        if (ctx.isCandleAlreadyExecuted) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 5,
                message = "VELA_YA_OPERADA minuto=${ctx.currentCandleEpochMinute}"
            )
        }

        // 6. FRANCOTIRADOR (:2324)
        val isYolo = (ctx.autonomousSubMode == AutonomousSubMode.YOLO)
        val isSniper = (ctx.autonomousSubMode == AutonomousSubMode.SNIPER)
        if (isSniper) {
            val currentAsset = ctx.observedAsset.ifBlank { "Crypto IDX" }
            val isSynthetic = ctx.isSyntheticOrOtc ||
                    currentAsset.contains("IDX", ignoreCase = true) ||
                    currentAsset.contains("OTC", ignoreCase = true)
            if (isSynthetic &&
                ctx.strategy != AutoTradeStrategy.MT_MASTER_COMBO &&
                ctx.strategy != AutoTradeStrategy.AUTO_ADAPTIVE &&
                ctx.strategy != AutoTradeStrategy.SUPPORT_RESISTANCE &&
                ctx.strategy != AutoTradeStrategy.MT_REJECTION
            ) {
                return ExecutionPolicyResult.Vetoed(
                    vetoIndex = 6,
                    message = "FRANCOTIRADOR activo=$currentAsset"
                )
            }
        }

        // 7. FUERA DE VENTANA TIMING SNIPER (:2333)
        val inWindow = MarketTickFilters.isInEntryWindow(ctx.sec)
        if (!inWindow) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 7,
                message = "FUERA_DE_VENTANA sec=${ctx.sec}"
            )
        }

        // 8. ANTI-CHOPPY (:2345)
        val isRejectionOrBounce = ctx.reasonDescription.contains("MT_REJECTION") ||
                ctx.reasonDescription.contains("MT_RANGE") ||
                ctx.reasonDescription.contains("MT_3_VELAS") ||
                ctx.reasonDescription.contains("MT_REVERSAL") ||
                ctx.reasonDescription.contains("MT_CONFIRM") ||
                ctx.reasonDescription.contains("MT_ENGULFING") ||
                ctx.reasonDescription.contains("QUANT_CRYPTO")
        if (ctx.isChoppinessDetected && !isRejectionOrBounce) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 8,
                message = "CHOP"
            )
        }

        // 9. COOLDOWN POST-RESOLUCIÓN (:2358)
        val timeSinceLastResolution = ctx.currentTimeMs - ctx.lastTradeResolutionTime
        val minSpacingMs = when {
            isYolo -> 8000L
            ctx.currentLossStreak > 0 -> 35000L
            else -> 12000L
        }
        if (ctx.lastTradeResolutionTime > 0L && timeSinceLastResolution < minSpacingMs) {
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 9,
                message = "COOLDOWN ${timeSinceLastResolution}ms"
            )
        }

        // 10. AGOTAMIENTO DE COMPRA R23H (:2380)
        motivoVetoPorAgotamientoDeCompra(
            action = ctx.action,
            trend = ctx.trend,
            callPower = ctx.callPower
        )?.let { motivoVeto ->
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 10,
                message = motivoVeto
            )
        }

        // 11. BLOQUEO ADAPTATIVO (:2402)
        val finalAction: TradeAction
        val finalReason: String
        val adaptiveModifier: Float
        when (val decision = ctx.adaptiveDecision) {
            is AdaptiveDecision.Block -> {
                val detail = buildAdaptiveBlockDetail(decision.reason, ctx.reasonDescription)
                return ExecutionPolicyResult.Vetoed(
                    vetoIndex = 11,
                    message = detail,
                    toastMessage = "⛔ [Autoaprendizaje] Entrada bloqueada: ${decision.reason}"
                )
            }
            is AdaptiveDecision.Invert -> {
                finalAction = decision.invertedAction
                finalReason = "${ctx.reasonDescription} [🔄 Invertida por Autoaprendizaje]"
                adaptiveModifier = 1.15f
            }
            is AdaptiveDecision.Allow -> {
                finalAction = decision.action
                finalReason = ctx.reasonDescription
                adaptiveModifier = decision.confidenceModifier
            }
        }

        // 12. RIESGO CAPITALPOLICY (:2427)
        val headlessConfidence = (0.85f * adaptiveModifier).coerceIn(0.1f, 1.0f)
        val (canTrade, riskReason) = ctx.capitalPolicyCanExecute
        if (!canTrade) {
            val isLimitReached = isSniper && (
                riskReason.contains("alcanzado", ignoreCase = true) ||
                riskReason.contains("finalizada", ignoreCase = true) ||
                riskReason.contains("Límite", ignoreCase = true)
            )
            return ExecutionPolicyResult.Vetoed(
                vetoIndex = 12,
                message = "RISK_LIMIT $riskReason",
                disableModeReason = if (isLimitReached) "engine:sniper-limite(headless)" else null,
                toastMessage = if (isLimitReached) "🛑 $riskReason" else null,
                isLongToast = true
            )
        }

        // 13. CONFLICTO S/R <-> PLAN V1 (:2455)
        if (HudCoherence.actionConflict(ctx.srFilterStatus, ctx.actionPlan)) {
            val msg = "V1 SR=\"${ctx.srFilterStatus}\" PLAN=\"${ctx.actionPlan}\""
            return ExecutionPolicyResult.Vetoed(vetoIndex = 13, message = msg)
        }

        return ExecutionPolicyResult.Allowed(
            finalAction = finalAction,
            headlessConfidence = headlessConfidence,
            finalReason = finalReason
        )
    }
}
