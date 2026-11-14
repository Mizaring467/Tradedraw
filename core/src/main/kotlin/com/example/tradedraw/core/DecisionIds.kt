package com.example.tradedraw.core

import java.util.UUID

object DecisionIds {
    /**
     * Ronda 21 (Frente A): Identificador determinista de decision.
     *
     * Permite correlacionar la operacion que decidio el bot con la que el broker termino
     * ejecutando, aun si la red se corta antes de recibir el `trade_id` real.
     *
     * Invariante de reproducibilidad: `UUID.nameUUIDFromBytes("${ric}_${nonceMs}_${ACTION}")`.
     */
    fun generateDeterministicDecisionId(asset: String, signalTimestampMs: Long, action: String): String {
        val ric = AssetRicMapper.ricParaActivoVisible(asset.trim())
        // Acepta segundos (valor < 1e11) o milisegundos; la clave siempre sale en ms.
        val nonceMs = if (signalTimestampMs > 100_000_000_000L) signalTimestampMs else signalTimestampMs * 1000L
        val key = "${ric}_${nonceMs}_${action.trim().uppercase()}"
        return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
    }
}
