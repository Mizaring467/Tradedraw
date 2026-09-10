package com.example.tradedraw

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registrador estructurado de operaciones para TradeDraw (Paso 0 - Profitability Loop).
 * Escribe en formato CSV parseable para auditoría por ADB y backtesting real.
 */
object TradeJournalLogger {
    private const val TAG = "TradeJournalLogger"
    private const val HEADER = "timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,reason\n"
    private val lock = Any()

    fun logTrade(
        context: Context,
        strategy: String,
        submode: String,
        action: String,
        confidence: Float,
        priceY: Float,
        stake: Number,
        baseBalance: Number,
        result: String,
        settledBalance: Number,
        durationSec: Long,
        reason: String
    ) {
        try {
            val dir = File(context.getExternalFilesDir(null), "TradeDraw_Audits")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "trade_journal.csv")

            synchronized(lock) {
                val writeHeader = !file.exists() || file.length() == 0L
                FileWriter(file, true).use { writer ->
                    if (writeHeader) {
                        writer.write(HEADER)
                    }
                    val now = System.currentTimeMillis()
                    val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now))
                    val cleanReason = reason.replace(",", ";").replace("\n", " ").take(120)
                    val line = String.format(
                        Locale.US,
                        "%d,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%d,\"%s\"\n",
                        now,
                        isoDate,
                        strategy,
                        submode,
                        action,
                        confidence,
                        priceY,
                        stake.toDouble(),
                        baseBalance.toDouble(),
                        result,
                        settledBalance.toDouble(),
                        durationSec,
                        cleanReason
                    )
                    writer.write(line)
                }
            }
            Log.d(TAG, "Trade journal registrado: $action -> $result ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo en trade_journal.csv", e)
        }
    }
}
