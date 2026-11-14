package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Ronda 21 (Frente C): Replay con Latencia Real vs Replay Ideal.
 *
 * MODALIDADES:
 *  - IDEAL: Entry price = tick exacto de decisión (sin latencia).
 *  - OBSERVED_LATENCY: Entry price = primer tick disponible en o después de la latencia
 *    observada real del despacho táctil (signal_ts + latencyMs).
 *
 * CONTRATOS ESTRICTOS:
 *  1. CERO datos del futuro para decidir: La señal y dirección se fijan estrictamente en signal_ts.
 *  2. Regla determinista de llenado: Si no hay tick exacto en (signal_ts + latencyMs), se toma el primer
 *     tick con ts >= signal_ts + latencyMs.
 *  3. Comparación cuantitativa: Señales, direcciones, precios de entrada, resultados y flip rate.
 *  4. Medición de robustez, NO ajuste de estrategia.
 */
class ReplayLatencyModeTest {

    enum class ReplayLatencyMode {
        IDEAL,
        OBSERVED_LATENCY
    }

    data class RawTick(val ts: Long, val ric: String, val price: Double)

    data class ReplayResult(
        val signalTs: Long,
        val action: TradeAction,
        val idealEntryPrice: Double,
        val latencyEntryPrice: Double,
        val exitPrice: Double,
        val idealResult: String,
        val latencyResult: String,
        val isFlipped: Boolean
    )

    data class ComparisonSummary(
        val totalSignals: Int,
        val identicalOutcomes: Int,
        val flippedOutcomes: Int,
        val flipRatePercent: Double,
        val avgSlippagePips: Double,
        val details: List<ReplayResult>
    )

    /**
     * Búsqueda binaria del primer tick con ts >= targetMs (sin mirar más allá).
     */
    private fun firstTickAtOrAfter(ticks: List<RawTick>, targetMs: Long): RawTick? {
        if (ticks.isEmpty() || targetMs > ticks.last().ts) return null
        var lo = 0
        var hi = ticks.size - 1
        var found: RawTick? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (ticks[mid].ts >= targetMs) {
                found = ticks[mid]
                hi = mid - 1 // Buscar hacia la izquierda el primer tick >= targetMs
            } else {
                lo = mid + 1
            }
        }
        return found
    }

    /**
     * Búsqueda binaria del último tick con ts <= targetMs.
     */
    private fun priceAt(ticks: List<RawTick>, targetMs: Long): Double? {
        if (ticks.isEmpty() || targetMs < ticks.first().ts) return null
        var lo = 0
        var hi = ticks.size - 1
        var found: Double? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (ticks[mid].ts <= targetMs) {
                found = ticks[mid].price
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    private fun resolveOutcome(entryPrice: Double, exitPrice: Double, action: TradeAction): String {
        return when {
            entryPrice == exitPrice -> "TIE"
            action == TradeAction.BUY -> if (exitPrice > entryPrice) "WIN" else "LOSS"
            action == TradeAction.SELL -> if (exitPrice < entryPrice) "WIN" else "LOSS"
            else -> "TIE"
        }
    }

    /**
     * Simula la comparación determinista entre IDEAL y OBSERVED_LATENCY.
     * @param latencyMs Latencia real observada (típica: 350ms a 500ms en Redmi A1)
     */
    fun compareIdealVsObservedLatency(
        ticks: List<RawTick>,
        signals: List<Pair<Long, TradeAction>>, // List of (signalTs, action)
        latencyMs: Long = 350L,
        expirationMs: Long = 60_000L
    ): ComparisonSummary {
        val results = mutableListOf<ReplayResult>()
        var slippageSum = 0.0

        for ((sigTs, action) in signals) {
            // 1. Modalidad IDEAL: entry = tick en sigTs
            val idealTick = ticks.firstOrNull { it.ts == sigTs } ?: continue
            val idealEntryPrice = idealTick.price

            // 2. Modalidad OBSERVED_LATENCY: primer tick con ts >= sigTs + latencyMs
            val latencyTick = firstTickAtOrAfter(ticks, sigTs + latencyMs) ?: idealTick
            val latencyEntryPrice = latencyTick.price

            // 3. Expiración a 60s
            val exitPrice = priceAt(ticks, sigTs + expirationMs) ?: ticks.last().price

            val idealOutcome = resolveOutcome(idealEntryPrice, exitPrice, action)
            val latencyOutcome = resolveOutcome(latencyEntryPrice, exitPrice, action)
            val flipped = idealOutcome != latencyOutcome

            slippageSum += Math.abs(latencyEntryPrice - idealEntryPrice)

            results.add(
                ReplayResult(
                    signalTs = sigTs,
                    action = action,
                    idealEntryPrice = idealEntryPrice,
                    latencyEntryPrice = latencyEntryPrice,
                    exitPrice = exitPrice,
                    idealResult = idealOutcome,
                    latencyResult = latencyOutcome,
                    isFlipped = flipped
                )
            )
        }

        val total = results.size
        val flippedCount = results.count { it.isFlipped }
        val identicalCount = total - flippedCount
        val flipRate = if (total > 0) (flippedCount.toDouble() / total.toDouble()) * 100.0 else 0.0
        val avgSlippage = if (total > 0) slippageSum / total else 0.0

        return ComparisonSummary(
            totalSignals = total,
            identicalOutcomes = identicalCount,
            flippedOutcomes = flippedCount,
            flipRatePercent = flipRate,
            avgSlippagePips = avgSlippage,
            details = results
        )
    }

    @Test
    fun replayDeterministaComparaIdealVsLatenciaObservada() {
        // Ticks sintéticos de alta resolución (cada 100ms) simulando microestructura
        val baseTs = 1700000000000L
        val ticks = mutableListOf<RawTick>()

        // Generar 70 segundos de ticks (700 ticks a 100ms)
        var p = 2500.0
        for (i in 0..700) {
            val ts = baseTs + (i * 100L)
            // Ligero movimiento oscilatorio
            val delta = when (i % 10) {
                1, 2 -> 0.05
                3, 4 -> -0.02
                5, 6 -> 0.08
                7, 8 -> -0.04
                else -> 0.01
            }
            p += delta
            ticks.add(RawTick(ts, "ETHUSD-OTC", p))
        }

        // Definir 3 señales de prueba
        val signals = listOf(
            Pair(baseTs + 5_000L, TradeAction.BUY),  // t=5s
            Pair(baseTs + 20_000L, TradeAction.SELL), // t=20s
            Pair(baseTs + 35_000L, TradeAction.BUY)   // t=35s
        )

        // Comparar con latencia observada de 350ms
        val summary = compareIdealVsObservedLatency(ticks, signals, latencyMs = 350L)

        assertEquals("Debe evaluar exactamente las 3 señales", 3, summary.totalSignals)
        assertEquals("Suma de identical + flipped debe ser total", summary.totalSignals, summary.identicalOutcomes + summary.flippedOutcomes)

        // Verificar que cada resultado conserva acción y signalTs
        for (res in summary.details) {
            assertNotNull(res.action)
            assertTrue("Precios de entrada deben ser positivos", res.idealEntryPrice > 0.0)
            assertTrue("Precios de latencia deben ser positivos", res.latencyEntryPrice > 0.0)
            assertTrue("Precios de salida deben ser positivos", res.exitPrice > 0.0)
        }

        assertTrue("Flip rate debe ser un porcentaje válido entre 0 y 100", summary.flipRatePercent in 0.0..100.0)
    }

    @Test
    fun latenciaCeroEsEquivalenteAIdeal() {
        val baseTs = 1700000000000L
        val ticks = listOf(
            RawTick(baseTs, "ETHUSD-OTC", 100.0),
            RawTick(baseTs + 1000L, "ETHUSD-OTC", 101.0),
            RawTick(baseTs + 60000L, "ETHUSD-OTC", 105.0)
        )
        val signals = listOf(Pair(baseTs, TradeAction.BUY))

        val summary = compareIdealVsObservedLatency(ticks, signals, latencyMs = 0L)
        assertEquals(1, summary.totalSignals)
        assertEquals("Con latencia 0 no puede haber flips", 0, summary.flippedOutcomes)
        assertEquals(0.0, summary.flipRatePercent, 0.001)
        assertEquals(summary.details[0].idealEntryPrice, summary.details[0].latencyEntryPrice, 0.0001)
        assertEquals(summary.details[0].idealResult, summary.details[0].latencyResult)
    }
}
