package com.example.tradedraw

import java.util.Locale

enum class CandleTimeframe(val seconds: Int, val label: String) {
    M1(60, "1m"),
    M5(300, "5m");

    val durationMs: Long get() = seconds * 1000L
    val periodMs: Long get() = durationMs

    fun getCycleSecond(timestampMs: Long = System.currentTimeMillis()): Int =
        ((timestampMs / 1000L) % seconds).toInt()

    fun isSniperTimingWindow(timestampMs: Long = System.currentTimeMillis()): Boolean {
        val s = getCycleSecond(timestampMs)
        return s in (seconds - 2)..(seconds - 1)
    }

    fun isStandardTimingWindow(timestampMs: Long = System.currentTimeMillis()): Boolean {
        val s = getCycleSecond(timestampMs)
        return s in (seconds - 3)..(seconds - 1) || s in 0..5
    }
}
