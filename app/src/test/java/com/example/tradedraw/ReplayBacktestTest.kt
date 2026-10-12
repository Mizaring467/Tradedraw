package com.example.tradedraw

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReplayBacktestTest — Reproducción fiel de ticks grabados en CSV contra el motor real (Paso 3, Ronda 9).
 *
 * SUPUESTOS Y ALCANCE DECLARADOS:
 * 1. Estrategia principal: AUTO_ADAPTIVE (activa allowAll = true, evaluando concurrentemente las 11
 *    estrategias cuantitativas y de acción de precio con su matriz de confluencia real >= 80 pts).
 * 2. Veredicto: Expiración a 60 segundos fijos (CandleTimeframe.M1.seconds).
 *    CALL gana si precio_final > precio_entrada; PUT gana si precio_final < precio_entrada;
 *    empate exacto = TIE.
 * 3. Ejecución: Sin slippage, fills instantáneos en el precio exacto del tick de entrada.
 * 4. Rutas configurables por variables de entorno:
 *    - BACKTEST_CSV: Ruta al archivo CSV con columnas `ts,ric,price`. Si no está definido, el test se auto-salta.
 *    - BACKTEST_OUT: Ruta al archivo CSV de salida simulado (por defecto: `build/backtest_out.csv`).
 */
class ReplayBacktestTest {

    data class RawTick(val ts: Long, val ric: String, val price: Double)

    data class SignalSnapshot(
        val tick: RawTick,
        val action: TradeAction,
        val reason: String,
        val trend: String,
        val distSupportRatio: Float,
        val distResistanceRatio: Float,
        val isChop: Boolean
    )

    data class SimulatedTrade(
        val timestamp: Long,
        val isoDate: String,
        val strategy: String,
        val submode: String,
        val action: TradeAction,
        val confidence: Float,
        val entryPrice: Double,
        val stake: Double,
        val baseBalance: Double,
        val result: String,
        val settledBalance: Double,
        val durationSec: Long,
        val trend: String,
        val distSupportRatio: Float,
        val distResistanceRatio: Float,
        val tickVelocity: Float,
        val impulse: String,
        val marketRegime: String,
        val candleSecond: Int,
        val adaptiveStatus: String,
        val reason: String,
        val source: String = ""
    )

    private fun loadTicks(file: File): List<RawTick> {
        val rawTicks = mutableListOf<RawTick>()
        file.forEachLine { line ->
            val trim = line.trim()
            if (trim.isNotEmpty() && !trim.startsWith("ts") && !trim.startsWith("#")) {
                val parts = trim.split(",")
                if (parts.size >= 3) {
                    val ts = parts[0].toLongOrNull()
                    val ric = parts[1].trim()
                    val price = parts[2].toDoubleOrNull()
                    if (ts != null && price != null && price > 0.0) {
                        rawTicks.add(RawTick(ts, ric, price))
                    }
                }
            }
        }
        val sorted = rawTicks.sortedBy { it.ts }
        // Deduplicar timestamps conservando solo el primer tick de cada ts
        val deduplicated = mutableListOf<RawTick>()
        var lastTs = Long.MIN_VALUE
        for (t in sorted) {
            if (t.ts != lastTs) {
                deduplicated.add(t)
                lastTs = t.ts
            }
        }
        return deduplicated
    }

    /**
     * Búsqueda binaria del último precio registrado en o antes de targetMs (espejo de replay.py).
     */
    private fun priceAt(ticks: List<RawTick>, targetMs: Long): Double? {
        if (ticks.isEmpty() || targetMs < ticks.first().ts) return null
        var lo = 0
        var hi = ticks.size - 1
        var found: Double? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (ticks[mid].ts <= targetMs) {
                found = ticks[mid].price
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /**
     * Resuelve el resultado de la operación a 60 segundos de expiración.
     */
    private fun resolveTrade(entryPrice: Double, exitPrice: Double?, action: TradeAction): Pair<String, String> {
        if (exitPrice == null) {
            return Pair("TIE", "TIMEOUT: sin tick posterior a la expiración")
        }
        if (exitPrice == entryPrice) {
            return Pair("TIE", String.format(Locale.US, "PRECIO (=) Empate técnico: Entry=%.4f vs Close=%.4f", entryPrice, exitPrice))
        }
        val won = if (action == TradeAction.BUY) exitPrice > entryPrice else exitPrice < entryPrice
        return if (won) Pair("WIN", "GANANCIA por acción del precio a 60s") else Pair("LOSS", "PÉRDIDA por acción del precio a 60s")
    }

    private fun runBacktestOnStrategy(
        ticks: List<RawTick>,
        strategy: AutoTradeStrategy,
        subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE
    ): List<SimulatedTrade> {
        val pendingSignals = mutableListOf<SignalSnapshot>()
        var lastExecutedCandleMinute = -1L
        var cooldownUntilTs = 0L

        fun createEngine(): SyntheticCandleEngine {
            val eng = SyntheticCandleEngine()
            eng.currentStrategy = strategy
            eng.subMode = subMode
            eng.onSignalGenerated = { action, reason ->
                val lastTick = ticks.lastOrNull { it.ts == eng.lastSignalTimestampMs } ?: ticks.first()
                val candleMinute = lastTick.ts / 60_000L
                // Descartar si el tick cae en el enfriamiento de 120s tras un hueco > 10s
                // o si la vela de este minuto ya fue operada (1 orden por vela máx)
                if (lastTick.ts >= cooldownUntilTs && candleMinute != lastExecutedCandleMinute) {
                    lastExecutedCandleMinute = candleMinute
                    pendingSignals.add(
                        SignalSnapshot(
                            tick = lastTick,
                            action = action,
                            reason = reason,
                            trend = eng.detectedTrend.name,
                            distSupportRatio = eng.distanceToSupportRatio,
                            distResistanceRatio = eng.distanceToResistanceRatio,
                            isChop = eng.isChoppinessDetected()
                        )
                    )
                }
            }
            return eng
        }

        var currentEngine = createEngine()
        var prevTs = 0L
        var prevPrice = 0.0

        for (raw in ticks) {
            // Detectar hueco > 10 s entre el tick previo y el actual
            if (prevTs > 0L && (raw.ts - prevTs) > 10_000L) {
                // Reiniciar el motor y aplicar veto de entrada en los 120s posteriores al final del hueco
                cooldownUntilTs = raw.ts + 120_000L
                currentEngine = createEngine()
                prevPrice = 0.0
            }

            val dtMs = if (prevTs > 0L) raw.ts - prevTs else 0L
            val dtSec = dtMs / 1000.0
            val velocity = if (dtSec > 0.0 && prevPrice > 0.0) ((raw.price - prevPrice) / dtSec).toFloat() else 0f
            val isIdx = raw.ric.contains("IDX", ignoreCase = true)
            val scale = if (prevPrice > 0.0) prevPrice else raw.price
            val impulseThreshold = if (isIdx) 0.000008f else (scale * 0.00008).toFloat()
            val isBullish = velocity > impulseThreshold
            val isBearish = velocity < -impulseThreshold

            val tick = MarketTick(
                asset = raw.ric,
                price = raw.price,
                timestampMs = raw.ts,
                velocity = velocity,
                isBullishImpulse = isBullish,
                isBearishImpulse = isBearish,
                smoothedVelocity = velocity
            )

            currentEngine.onNewTick(tick)
            prevTs = raw.ts
            prevPrice = raw.price
        }

        val trades = mutableListOf<SimulatedTrade>()
        var balance = 1000.0
        val stake = 1.0

        for (sig in pendingSignals) {
            val entryTick = sig.tick
            val action = sig.action
            val reason = sig.reason
            val exitPrice = priceAt(ticks, entryTick.ts + 60_000L)
            val (result, _) = resolveTrade(entryTick.price, exitPrice, action)

            val settledBalance = when (result) {
                "WIN" -> balance + (stake * 0.82)
                "LOSS" -> balance - stake
                else -> balance
            }

            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(entryTick.ts))
            val candleSec = ((entryTick.ts / 1000L) % 60L).toInt()

            trades.add(
                SimulatedTrade(
                    timestamp = entryTick.ts,
                    isoDate = isoDate,
                    strategy = strategy.name,
                    submode = subMode.name,
                    action = action,
                    confidence = 0.85f,
                    entryPrice = entryTick.price,
                    stake = stake,
                    baseBalance = balance,
                    result = result,
                    settledBalance = settledBalance,
                    durationSec = 60L,
                    trend = sig.trend,
                    distSupportRatio = sig.distSupportRatio,
                    distResistanceRatio = sig.distResistanceRatio,
                    tickVelocity = entryTick.price.toFloat(),
                    impulse = "NEUTRAL",
                    marketRegime = if (sig.isChop) "CHOP" else "TRENDING",
                    candleSecond = candleSec,
                    adaptiveStatus = result,
                    reason = reason
                )
            )
            balance = settledBalance
        }

        return trades
    }

    private fun writeJournalCsv(trades: List<SimulatedTrade>, outFile: File) {
        outFile.parentFile?.mkdirs()
        FileWriter(outFile, false).use { writer ->
            writer.write("timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason,source\n")
            for (t in trades) {
                val cleanReason = t.reason.replace(",", ";").replace("\n", " ").take(140)
                val line = String.format(
                    Locale.US,
                    "%d,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%d,%s,%.2f,%.2f,%.2f,%s,%s,%d,%s,\"%s\",%s\n",
                    t.timestamp, t.isoDate, t.strategy, t.submode, t.action.name, t.confidence,
                    t.entryPrice, t.stake, t.baseBalance, t.result, t.settledBalance, t.durationSec,
                    t.trend, t.distSupportRatio, t.distResistanceRatio, t.tickVelocity, t.impulse,
                    t.marketRegime, t.candleSecond, t.adaptiveStatus, cleanReason, t.source
                )
                writer.write(line)
            }
        }
    }

    private fun resolveFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        if (f.exists()) return f
        val parent = File("..", path)
        if (parent.exists()) return parent
        val currentDir = File(".").canonicalFile
        if (currentDir.name == "app") {
            return File("..", path)
        }
        return f
    }

    @Test
    fun testReplayBacktestAutoAdaptive() {
        val dirEnv = System.getenv("BACKTEST_DIR") ?: System.getProperty("BACKTEST_DIR")
        val csvEnv = System.getenv("BACKTEST_CSV") ?: System.getProperty("BACKTEST_CSV")
        assumeTrue("BACKTEST_CSV o BACKTEST_DIR debe estar definido", !dirEnv.isNullOrBlank() || !csvEnv.isNullOrBlank())

        val filesToProcess = mutableListOf<File>()
        if (!dirEnv.isNullOrBlank()) {
            val dir = resolveFile(dirEnv)
            assumeTrue("Directorio BACKTEST_DIR no existe: ${dir.absolutePath}", dir.exists() && dir.isDirectory)
            val csvFiles = dir.listFiles { _, name -> name.endsWith(".csv", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()
            assumeTrue("No hay archivos CSV en ${dir.absolutePath}", csvFiles.isNotEmpty())
            filesToProcess.addAll(csvFiles)
        } else {
            val csvFile = resolveFile(csvEnv!!)
            assumeTrue("Archivo BACKTEST_CSV no existe (${csvFile.absolutePath}): omitiendo replay", csvFile.exists())
            filesToProcess.add(csvFile)
        }

        val t0Env = System.getenv("BACKTEST_T0") ?: System.getProperty("BACKTEST_T0")
        val t0Val = t0Env?.toLongOrNull()
        val outEnv = System.getenv("BACKTEST_OUT") ?: System.getProperty("BACKTEST_OUT") ?: "build/backtest_out.csv"
        val outFile = resolveFile(outEnv)

        println("=== INICIANDO BACKTEST REPLAY FIEL (AUTO_ADAPTIVE) ===")
        println("Archivos a procesar: ${filesToProcess.size}")

        val allTrades = mutableListOf<SimulatedTrade>()
        for ((idx, f) in filesToProcess.withIndex()) {
            val allTicks = loadTicks(f)
            val ticks = if (t0Val != null && t0Val > 0L) allTicks.filter { it.ts <= t0Val } else allTicks
            if (ticks.size < 10) continue

            val trades = runBacktestOnStrategy(ticks, AutoTradeStrategy.AUTO_ADAPTIVE)
            val labeledTrades = trades.map { it.copy(source = f.name) }
            allTrades.addAll(labeledTrades)
            if (filesToProcess.size > 1 && (idx + 1) % 25 == 0) {
                println("Procesados ${idx + 1}/${filesToProcess.size} archivos (trades acumulados: ${allTrades.size})...")
            }
        }

        writeJournalCsv(allTrades, outFile)

        println("Señales generadas (Motor Kotlin real): ${allTrades.size}")
        val wins = allTrades.count { it.result == "WIN" }
        val losses = allTrades.count { it.result == "LOSS" }
        val ties = allTrades.count { it.result == "TIE" }
        val nonTies = wins + losses
        val wr = if (nonTies > 0) (wins.toDouble() / nonTies * 100.0) else 0.0

        println(String.format(Locale.US, "Resultado: W=%d, L=%d, TIE=%d | WR=%.2f%%", wins, losses, ties, wr))
        println("Archivo de salida simulado: ${outFile.absolutePath}")

        if (filesToProcess.size <= 5) {
            for ((idx, t) in allTrades.withIndex()) {
                println("Trade #${idx + 1}: ${t.isoDate} | ${t.action} @ ${t.entryPrice} -> ${t.result} (${t.reason}) [${t.source}]")
            }
        }
    }

    @Test
    fun testReplayBacktestAllStrategiesReport() {
        val csvEnv = System.getenv("BACKTEST_CSV") ?: System.getProperty("BACKTEST_CSV")
        assumeTrue("BACKTEST_CSV no está definida: omitiendo reporte multiespecífico", !csvEnv.isNullOrBlank())
        val csvFile = resolveFile(csvEnv!!)
        assumeTrue("Archivo no existe: omitiendo reporte", csvFile.exists())

        val t0Env = System.getenv("BACKTEST_T0") ?: System.getProperty("BACKTEST_T0")
        val t0Val = t0Env?.toLongOrNull()
        val allTicks = loadTicks(csvFile)
        val ticks = if (t0Val != null && t0Val > 0L) allTicks.filter { it.ts <= t0Val } else allTicks
        println("=== REPORTE MULTIESTRATEGIA DE REPLAY KOTLIN ===" + (if (t0Val != null) " (filtrado hasta T0=$t0Val)" else ""))
        for (strategy in AutoTradeStrategy.values()) {
            val trades = runBacktestOnStrategy(ticks, strategy)
            val wins = trades.count { it.result == "WIN" }
            val losses = trades.count { it.result == "LOSS" }
            val wr = if (wins + losses > 0) (wins.toDouble() / (wins + losses) * 100.0) else 0.0
            println(String.format(Locale.US, "%-28s -> Señales: %2d | W: %2d, L: %2d | WR: %5.1f%%", strategy.name, trades.size, wins, losses, wr))
        }
    }
}
