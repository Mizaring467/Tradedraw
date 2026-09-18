package com.example.tradedraw

enum class CandleTimeframe(val seconds: Int, val label: String) {
    M1(60, "1m"),
    M5(300, "5m");

    val durationMs: Long get() = seconds * 1000L
    val periodMs: Long get() = durationMs
}
