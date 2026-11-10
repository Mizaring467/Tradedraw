package com.example.tradedraw

import com.example.tradedraw.core.*

typealias MarketTick = com.example.tradedraw.core.MarketTick
typealias WebSocketState = com.example.tradedraw.core.WebSocketState
typealias CandleTimeframe = com.example.tradedraw.core.CandleTimeframe
typealias TradeAction = com.example.tradedraw.core.TradeAction
typealias AutoTradeMode = com.example.tradedraw.core.AutoTradeMode
typealias AutonomousSubMode = com.example.tradedraw.core.AutonomousSubMode
typealias OrderState = com.example.tradedraw.core.OrderState
typealias AutoTradeStrategy = com.example.tradedraw.core.AutoTradeStrategy
typealias ActiveInstance<T> = com.example.tradedraw.core.ActiveInstance<T>

val AssetRicMapper = com.example.tradedraw.core.AssetRicMapper
val MarketTickFilters = com.example.tradedraw.core.MarketTickFilters
val DecisionIds = com.example.tradedraw.core.DecisionIds

fun formatDynamicPrice(price: Double): String = com.example.tradedraw.core.formatDynamicPrice(price)

