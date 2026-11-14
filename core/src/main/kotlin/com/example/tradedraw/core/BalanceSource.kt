package com.example.tradedraw.core

data class BalanceReading(
    val balance: Double,
    val lastReadMs: Long,
    val isAvailable: Boolean
)

/**
 * Puerto de Saldo P0: Desacopla la lectura de balance de los estáticos de AutoTradeAccessibilityService.
 */
interface BalanceSource {
    fun read(): BalanceReading

    fun readCurrentBalance(): Double = read().balance
}
