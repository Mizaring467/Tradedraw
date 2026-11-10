package com.example.tradedraw.persistence

import com.example.tradedraw.core.JournalReader
import java.io.File

class FileJournalReader(private val file: File) : JournalReader {
    override fun readRecentLines(maxLines: Int): List<String> {
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            file.readLines().takeLast(maxLines)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
