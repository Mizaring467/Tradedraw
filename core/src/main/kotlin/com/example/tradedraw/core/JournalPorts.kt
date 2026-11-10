package com.example.tradedraw.core

interface JournalWriter {
    fun logTrade(csvLine: String)
    fun logShadow(csvLine: String)
}

interface JournalReader {
    fun readRecentLines(maxLines: Int): List<String>
}
