package com.example.tradedraw

/** Read-only boundary for replay consumers; delegates to the frozen strategy implementation. */
object AdaptiveReplayPort {
    fun mejorCoincidenciaEnMemoria(candidata: TradeContextSignature, memoriaPerdidas: List<TradeContextSignature>) =
        com.example.tradedraw.mejorCoincidenciaEnMemoria(candidata, memoriaPerdidas)
    fun motivoBloqueoPorAntiPatron(candidata: TradeContextSignature, firmaPerdedoraMasSimilar: TradeContextSignature?, similitudMaxima: Float) =
        com.example.tradedraw.motivoBloqueoPorAntiPatron(candidata, firmaPerdedoraMasSimilar, similitudMaxima)
}
