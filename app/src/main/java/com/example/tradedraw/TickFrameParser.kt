package com.example.tradedraw

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tick de cotización ya atribuido a un activo concreto.
 *
 * @param asset RIC del activo al que pertenece el precio. NUNCA es el RIC activo por defecto:
 *   si la trama no identifica el activo, [TickFrameParser.parse] devuelve null.
 * @param price Cotización instantánea.
 */
data class ParsedTick(val asset: String, val price: Double)

/**
 * Parser puro (sin Android, sin estado) de las tramas de cotización de Binomo.
 *
 * Ronda 11 — integridad de datos: la versión anterior aceptaba el PRIMER activo con rate>0
 * aunque no fuera el `activeAsset` (`if (parsedPrice == null || ric.equals(activeAsset))`),
 * y el fallback regex terminaba en `?: payload`, es decir, buscaba el precio en TODA la trama.
 * Con tramas multi-activo (el socket emite `rates` globales) eso metía el precio de otro activo
 * (p. ej. 1.166 de un par FX) en un HUD de Crypto IDX (~641.86) y envenenaba la velocidad con
 * un delta de ~640 ⇒ ±1e4/s. Aquí el activo activo es condición NECESARIA, no preferencia.
 */
object TickFrameParser {

    /** Tope de profundidad del recorrido: las tramas reales anidan 4 niveles. */
    private const val MAX_DEPTH = 8

    private val RATE_REGEX = Regex(""""rate"\s*:\s*([0-9.]+)""")
    private val PRICE_REGEX = Regex(""""price"\s*:\s*([0-9.]+)"""")

    /**
     * Resultado del análisis de una trama.
     *
     * @param tick precio atribuido al activo activo, o null si la trama no lo contiene.
     * @param hadForeignAsset true si la trama traía precios de OTROS activos. Distingue una
     *   contaminación real (hay activos ajenos y ninguno es el mío) de una trama informativa
     *   (heartbeat, phx_reply) que simplemente no lleva precios: solo lo primero es un incidente.
     */
    data class FrameAnalysis(val tick: ParsedTick?, val hadForeignAsset: Boolean)

    /**
     * Atribuye un precio de [rawText] a [activeAsset].
     *
     * Reglas (fail-closed):
     *  1. Si la trama identifica activos explícitamente (`ric`), se exige coincidencia EXACTA
     *     (sin distinguir mayúsculas) con [activeAsset]. Solo se emite ese precio.
     *  2. Si la trama no tiene ningún `ric` en ningún nivel, se acepta únicamente una trama
     *     escalar (un solo precio distinto) y se atribuye a [activeAsset]: el socket está suscrito por RIC.
     *  3. Si la trama trae varios precios sin `ric` (contenedor ambiguo), se descarta.
     *
     * @return null si el activo activo no aparece en la trama, si el precio es inválido o
     *   si la trama es ambigua. Nunca se emite el precio de un activo ajeno.
     */
    fun parse(rawText: String, activeAsset: String): ParsedTick? = analyze(rawText, activeAsset).tick

    /** Igual que [parse] pero informa además si la trama contenía activos ajenos. */
    fun analyze(rawText: String, activeAsset: String): FrameAnalysis {
        val active = activeAsset.trim()
        if (active.isEmpty()) return FrameAnalysis(null, false)

        val payload = rawText.trim().removePrefixIfSocketIo()
        if (payload.isEmpty()) return FrameAnalysis(null, false)

        val candidates = mutableListOf<ParsedTick>()
        val anonymousPrices = mutableListOf<Double>()

        val root: Any? = when {
            payload.startsWith("{") -> runCatching { JSONObject(payload) }.getOrNull()
            payload.startsWith("[") -> runCatching { JSONArray(payload) }.getOrNull()
            else -> null
        } ?: return FrameAnalysis(null, false)

        collectCandidates(root, candidates, anonymousPrices, 0)

        candidates.firstOrNull { it.asset.equals(active, ignoreCase = true) }?.let {
            return FrameAnalysis(it, candidates.size > 1)
        }

        // Multi-activo sin el activo propio: NO se emite el precio ajeno.
        if (candidates.isNotEmpty()) return FrameAnalysis(null, true)

        // Sin ningún `ric` en la trama: solo se acepta si hay UN ÚNICO precio. Dos o más precios
        // anónimos son un contenedor ambiguo (p. ej. el topic global `rates`) y no se pueden atribuir.
        if (anonymousPrices.isNotEmpty()) {
            val distinct = anonymousPrices.distinct()
            return if (distinct.size == 1) FrameAnalysis(ParsedTick(active, distinct.first()), false)
            else FrameAnalysis(null, true)
        }

        // Último recurso: regex restringida al bloque que declare el RIC activo. Sin bloque propio
        // NO se emite nada (antes caía a `?: payload`, la trama entera: origen del precio ajeno).
        val activeBlock = findActiveBlock(payload, active) ?: return FrameAnalysis(null, false)
        val matched = RATE_REGEX.find(activeBlock) ?: PRICE_REGEX.find(activeBlock)
            ?: return FrameAnalysis(null, false)
        val price = matched.groupValues[1].toDoubleOrNull() ?: return FrameAnalysis(null, false)
        if (price <= 0.0) return FrameAnalysis(null, false)
        return FrameAnalysis(ParsedTick(active, price), false)
    }

    /** Bloque `{...}` que declara el RIC activo; null si la trama no lo contiene. */
    private fun findActiveBlock(payload: String, active: String): String? {
        val escaped = Regex.escape(active)
        return Regex("""\{[^{}]*"ric"\s*:\s*"$escaped"[^{}]*\}""").find(payload)?.value
    }

    private fun String.removePrefixIfSocketIo(): String =
        if (startsWith("42")) substring(2) else this

    private fun firstPositive(vararg values: Double): Double? =
        values.firstOrNull { !it.isNaN() && it > 0.0 }

    private fun collectCandidates(
        node: Any?,
        named: MutableList<ParsedTick>,
        anonymous: MutableList<Double>,
        depth: Int
    ) {
        if (node == null || depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> {
                val ric = node.optString("ric", "")
                val rate = firstPositive(
                    node.optDouble("rate", Double.NaN),
                    node.optDouble("price", Double.NaN)
                )
                if (rate != null) {
                    if (ric.isNotEmpty()) named.add(ParsedTick(ric, rate)) else anonymous.add(rate)
                }
                for (key in node.keys()) {
                    val value = node.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        collectCandidates(value, named, anonymous, depth + 1)
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectCandidates(node.opt(i), named, anonymous, depth + 1)
            }
        }
    }
}
