package com.example.tradedraw

import com.example.tradedraw.core.*

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
        val isChop: Boolean,
        val tickVelocity: Float,
        val isBullishImpulse: Boolean,
        val isBearishImpulse: Boolean,
        /**
         * R23 Iter 3: RIC del tick que disparo la senal. Se arrastra hasta la fila del CSV porque
         * de el sale el `decision_id` determinista: el motor vivo usa el mismo RIC normalizado, y
         * sin este dato el replay volveria a emitir ids incomparables.
         */
        val assetRic: String
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
        /**
         * R23 Iter 3: id determinista de la decision. Se calcula con
         * [DecisionIds.generateDeterministicDecisionId] sobre el RIC del tick de entrada y el
         * MILISEGUNDO de la senal — la misma formula y las mismas unidades que el motor vivo, que
         * es lo que permite al comparador emparejar ambos lados.
         */
        val decisionId: String,
        /**
         * R23A Iter 1: RIC del activo de la entrada. Se emite como columna `asset` en el CSV de
         * replay para que el comparador Live <-> Replay pueda leer el mismo subyacente en ambos
         * lados: sin esta columna el comparador caia a su heuristica de `source`/`reason` y emitia
         * una divergencia espuria de `asset` (hallazgo de OpenCode en la Iter 4).
         */
        val assetRic: String = "",
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
                    if (ts != null && price != null && price.isFinite() && price > 0.0 && ts >= 0 && ric.isNotBlank()) {
                        rawTicks.add(RawTick(ts, ric, price))
                    }
                }
            }
        }
        // First observation per exact broker RIC + timestamp; no source sequence is available.
        // Equal timestamps on DIFFERENT assets are independent events.
        val watermarks = mutableMapOf<String, Long>()
        return rawTicks.distinctBy { it.ric to it.ts }.filter { tick ->
            val accepted = tick.ts > (watermarks[tick.ric] ?: -1L)
            if (accepted) watermarks[tick.ric] = tick.ts
            accepted
        }.sortedBy { it.ts }
    }

    /** First same-asset eligible event at/after expiry. Caller supplies one asset only. */
    private fun priceAt(ticks: List<RawTick>, targetMs: Long): Double? {
        require(ticks.map { it.ric }.distinct().size <= 1) { "MIXED_ASSET_SETTLEMENT" }
        var lo = 0
        var hi = ticks.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (ticks[mid].ts < targetMs) lo = mid + 1 else hi = mid
        }
        return ticks.getOrNull(lo)?.price
    }

    /**
     * Resuelve el resultado de la operación a 60 segundos de expiración.
     */
    private fun resolveTrade(entryPrice: Double, exitPrice: Double?, action: TradeAction): Pair<String, String> {
        if (exitPrice == null) {
            return Pair("NO_DATA", "NO_SETTLEMENT_DATA: no eligible same-asset future tick")
        }
        if (exitPrice == entryPrice) {
            return Pair("TIE", String.format(Locale.US, "PRECIO (=) Empate técnico: Entry=%.4f vs Close=%.4f", entryPrice, exitPrice))
        }
        val won = if (action == TradeAction.BUY) exitPrice > entryPrice else exitPrice < entryPrice
        return if (won) Pair("WIN", "GANANCIA por acción del precio a 60s") else Pair("LOSS", "PÉRDIDA por acción del precio a 60s")
    }

    /**
     * R23J Fase B: construye la firma contextual del replay con los MISMOS campos que
     * `AdaptiveLearningEngine.recordTradeOpened`. El replay no tiene `MarketTick` ni
     * `VisionAnalysisResult` completos, asi que los campos sin equivalente se fijan a su valor
     * neutro y se declara la limitacion en el informe de B3.
     */
    private fun firmaDeContexto(
        action: TradeAction,
        trend: TrendDirection,
        distSupportRatio: Float,
        distResistanceRatio: Float,
        isMarketSideways: Boolean,
        isConsolidationTight: Boolean,
        tickVelocity: Float,
        isBullishImpulse: Boolean,
        isBearishImpulse: Boolean,
        strategy: AutoTradeStrategy,
        candleSecond: Int
    ): TradeContextSignature = TradeContextSignature(
        action = action,
        trend = trend,
        distanceToSupportRatio = distSupportRatio,
        distanceToResistanceRatio = distResistanceRatio,
        isNearSupportZone = distSupportRatio <= 0.30f,
        isNearResistanceZone = distResistanceRatio <= 0.30f,
        tickVelocityNormalized = tickVelocity,
        isBullishImpulse = isBullishImpulse,
        isBearishImpulse = isBearishImpulse,
        isDojiOrLowVolume = false,
        isConsolidationTight = isConsolidationTight,
        isMarketSideways = isMarketSideways,
        strategyName = strategy.name,
        candleSecond = candleSecond,
        timestampMs = 0L,
        isContinuationTrade = false,
        isReversionTrade = false
    )

    /**
     * R23J Fase B (B2): bloqueos `ADAPTIVE_BLOCK` que el replay habria aplicado, con el instante y la
     * similitud observada. Se acumulan por estrategia para poder contrastarlos contra los vetos
     * reales de `decisions.csv`. Solo diagnostico: no altera el journal ni el comparador congelado.
     */
    data class BloqueoAdaptativo(
        val ts: Long,
        val action: TradeAction,
        val similitud: Float,
        val simPct: Int,
        val strategy: String
    )

    private val bloqueosRegistrados = mutableListOf<BloqueoAdaptativo>()

    private fun runBacktestOnStrategy(
        ticks: List<RawTick>,
        strategy: AutoTradeStrategy,
        subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE
    ): List<SimulatedTrade> {
        // Independent per-asset diagnostic portfolios; never feed another asset into candle/adaptive state.
        val groups = ticks.groupBy { it.ric }
        if (groups.size > 1) return groups.values.flatMap { runBacktestOnStrategy(it, strategy, subMode) }.sortedBy { it.timestamp }
        if (ticks.isEmpty()) return emptyList()
        var cooldownUntilTs = 0L
        val cooldownMs = if (subMode == AutonomousSubMode.YOLO) YOLO_COOLDOWN_MS else CONSERVATIVE_COOLDOWN_MS
        val causalGate = CausalReplayGate(TRADE_DURATION_MS, cooldownMs)
        var openSignal: SignalSnapshot? = null
        val trades = mutableListOf<SimulatedTrade>()
        var balance = 1000.0
        val stake = 1.0

        // R23H Fase 2: impulso del tick que esta siendo procesado, expuesto al callback de senal.
        // El callback se dispara DENTRO de `onNewTick`, asi que estas variables ya tienen el valor
        // del tick actual cuando se leen. Se usan para reproducir el `callPower` exacto del motor.
        var tickImpulseBullish = false
        var tickImpulseBearish = false
        var currentTickVelocity = 0f

        // La memoria precargada modela el estado que SHADOW hereda al iniciar. Los resultados
        // simulados de SHADOW NO alimentan AdaptiveLearningEngine en produccion, por lo que esta
        // memoria permanece inmutable durante una corrida causal de research.
        val memoriaPerdidas = mutableListOf<TradeContextSignature>()
        var bloqueosAdaptativos = 0

        // R23J Fase B (B2): en vivo la memoria de anti-patrones PERSISTE entre ventanas
        // (`files/adaptive_learning_state.json`, 19 firmas al momento de este analisis). Un replay
        // que arranque con memoria vacia no puede reproducir un bloqueo que depende de perdidas de
        // dias anteriores. Si el operador exporta ese JSON y pasa su ruta en ADAPTIVE_STATE_JSON,
        // el arnes arranca con la MISMA memoria que tenia el telefono.
        System.getenv("ADAPTIVE_STATE_JSON")?.takeIf { it.isNotBlank() }?.let { ruta ->
            runCatching {
                val texto = java.io.File(ruta).readText(Charsets.UTF_8)
                val raiz = org.json.JSONObject(texto)
                validateAdaptiveState(raiz, ticks.first().ts, ticks.first().ric)
                val arr = raiz.optJSONArray("lossSignatures")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        memoriaPerdidas.add(
                            TradeContextSignature(
                                action = runCatching { TradeAction.valueOf(o.getString("action")) }.getOrDefault(TradeAction.BUY),
                                trend = runCatching { TrendDirection.valueOf(o.getString("trend")) }.getOrDefault(TrendDirection.SIDEWAYS),
                                distanceToSupportRatio = o.optDouble("distSup", 0.0).toFloat(),
                                distanceToResistanceRatio = o.optDouble("distRes", 0.0).toFloat(),
                                isNearSupportZone = o.optBoolean("nearSup", false),
                                isNearResistanceZone = o.optBoolean("nearRes", false),
                                tickVelocityNormalized = o.optDouble("tickVelNorm", 0.0).toFloat(),
                                isBullishImpulse = o.optBoolean("bullishImp", false),
                                isBearishImpulse = o.optBoolean("bearishImp", false),
                                isDojiOrLowVolume = o.optBoolean("doji", false),
                                isConsolidationTight = o.optBoolean("tight", false),
                                isMarketSideways = o.optBoolean("sideways", false),
                                strategyName = o.optString("strat", "AUTO_ADAPTIVE"),
                                candleSecond = o.optInt("sec", 0),
                                timestampMs = o.optLong("ts", 0L),
                                isContinuationTrade = o.optBoolean("continuation", false),
                                isReversionTrade = o.optBoolean("reversion", false)
                            )
                        )
                    }
                }
                println("Memoria de anti-patrones precargada desde $ruta: ${memoriaPerdidas.size} firmas")
            }.getOrElse { throw IllegalArgumentException("ADAPTIVE_STATE_REJECTED: ${it.message}", it) }
        }

        fun settleOpen(exitPrice: Double?) {
            val sig = openSignal ?: return
            val entryTick = sig.tick
            val action = sig.action
            val (result, _) = resolveTrade(entryTick.price, exitPrice, action)
            val settledBalance = when (result) {
                "WIN" -> balance + (stake * 0.82)
                "LOSS" -> balance - stake
                else -> balance
            }
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(entryTick.ts))
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
                    tickVelocity = sig.tickVelocity,
                    impulse = when {
                        sig.isBullishImpulse -> "BULLISH"
                        sig.isBearishImpulse -> "BEARISH"
                        else -> "NEUTRAL"
                    },
                    marketRegime = if (sig.isChop) "SIDEWAYS" else "TRENDING",
                    candleSecond = candleSec,
                    adaptiveStatus = result,
                    reason = sig.reason,
                    decisionId = DecisionIds.generateDeterministicDecisionId(sig.assetRic, entryTick.ts, action.name),
                    assetRic = sig.assetRic
                )
            )
            balance = settledBalance
            openSignal = null
        }

        fun createEngine(): SyntheticCandleEngine {
            val eng = SyntheticCandleEngine()
            eng.currentStrategy = strategy
            eng.subMode = subMode
            eng.onSignalGenerated = senal@{ action, reason ->
                // R23A Iter 2: el tick de la senal se busca por el TIMESTAMP exacto que el motor
                // registro al emitirla (`lastSignalTimestampMs`). Si no aparece —posible porque el
                // motor puede emitir en un tick que el replay no conservo tras deduplicar por ts— el
                // fallback anterior era `ticks.first()`, que es el PRIMER tick de TODO el archivo.
                // Eso colocaba la senal en el segundo de vela 0 de la serie en vez de en el suyo, y
                // contaminaba `candle_second`, `candleSecond` y el id determinista con un instante
                // arbitrario. Ahora se cae al tick de la MISMA vela (el mas cercano hacia atras),
                // que es el unico fallback que no inventa un instante.
                val lastTick = ticks.lastOrNull { it.ts == eng.lastSignalTimestampMs }
                    ?: ticks.lastOrNull { it.ts <= eng.lastSignalTimestampMs }
                    ?: ticks.first()
                val candleMinute = lastTick.ts / 60_000L
                // R23C Fase 2 (REQ 2.1): ventana sniper. En vivo `executeHeadlessTrade` veta toda
                // senal fuera de :00-:05 y :58-:59, asi que el replay debe hacer lo mismo o genera
                // trades que el motor real nunca habria despachado.
                val sigCandleSec = ((lastTick.ts / 1000L) % 60L).toInt()
                val inSniperWindow = sigCandleSec in 0..5 || sigCandleSec in 58..59
                // Descartar si: la señal cae fuera de la ventana sniper, o estamos dentro de una
                // operacion abierta / su cooldown, o el tick cae en el enfriamiento de 120s tras un
                // hueco > 10s, o la vela de este minuto ya fue operada (1 orden por vela máx).
                if (inSniperWindow && causalGate.canConsider(lastTick.ts, candleMinute, cooldownUntilTs)) {
                    // R23H Fase 2 (paridad live<->replay): el motor vivo aplica
                    // `VETO_BUY_EXHAUSTION_CLIMAX` antes de despachar. Sin replicarlo aqui, el replay
                    // simulaba compras en techo que en vivo nunca se ejecutaron, y el comparador las
                    // contaba como faltantes del lado live. El `callPower` NO se recalcula a mano: sale
                    // de `calcularPoderesDeMercado`, la misma funcion pura que alimenta al motor real.
                    val (callPowerEnSenal, _) = StrategyReplayPort.calcularPoderesDeMercado(
                        trend = eng.detectedTrend,
                        distSupportRatio = eng.distanceToSupportRatio,
                        distResistanceRatio = eng.distanceToResistanceRatio,
                        rsi = eng.syntheticTickRsi,
                        isBullishImpulse = tickImpulseBullish,
                        isBearishImpulse = tickImpulseBearish
                    )
                    if (StrategyReplayPort.motivoVetoPorAgotamientoDeCompra(action, eng.detectedTrend, callPowerEnSenal) != null) {
                        return@senal
                    }

                    // SHADOW evalua adaptacion ANTES de abrir la simulacion. Un veto no consume
                    // vela/cooldown: candleRateLimiter solo registra una ejecucion tras open SUCCESS.
                    val firmaCandidata = firmaDeContexto(
                        action = action,
                        trend = eng.detectedTrend,
                        distSupportRatio = eng.distanceToSupportRatio,
                        distResistanceRatio = eng.distanceToResistanceRatio,
                        isMarketSideways = eng.isChoppinessDetected() || eng.detectedTrend == TrendDirection.SIDEWAYS,
                        isConsolidationTight = eng.isChoppinessDetected(),
                        tickVelocity = currentTickVelocity,
                        isBullishImpulse = tickImpulseBullish,
                        isBearishImpulse = tickImpulseBearish,
                        strategy = strategy,
                        candleSecond = sigCandleSec
                    )
                    val (simMax, coincidencia) =
                        AdaptiveReplayPort.mejorCoincidenciaEnMemoria(firmaCandidata, memoriaPerdidas)
                    if (AdaptiveReplayPort.motivoBloqueoPorAntiPatron(firmaCandidata, coincidencia, simMax) != null) {
                        bloqueosAdaptativos++
                        synchronized(bloqueosRegistrados) {
                            bloqueosRegistrados.add(
                                BloqueoAdaptativo(
                                    ts = lastTick.ts,
                                    action = action,
                                    similitud = simMax,
                                    simPct = (simMax * 100).toInt(),
                                    strategy = strategy.name
                                )
                            )
                        }
                        return@senal
                    }

                    causalGate.accept(lastTick.ts, candleMinute, cooldownUntilTs)
                    openSignal = SignalSnapshot(
                        tick = lastTick,
                        action = action,
                        reason = reason,
                        trend = eng.detectedTrend.name,
                        distSupportRatio = eng.distanceToSupportRatio,
                        distResistanceRatio = eng.distanceToResistanceRatio,
                        isChop = eng.isChoppinessDetected(),
                        tickVelocity = currentTickVelocity,
                        isBullishImpulse = tickImpulseBullish,
                        isBearishImpulse = tickImpulseBearish,
                        assetRic = lastTick.ric
                    )
                }
            }
            return eng
        }

        var currentEngine = createEngine()
        var prevTs = 0L
        var prevPrice = 0.0

        for (raw in ticks) {
            // Igual que TradingEngine.onMarketTick: SHADOW liquida primero y solo despues el
            // mismo tick entra a SyntheticCandleEngine. No hay escaneo de ticks futuros.
            if (openSignal != null && causalGate.isSettlementDue(raw.ts)) {
                causalGate.settle(raw.ts)
                settleOpen(raw.price)
            }

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

            // Asignar ANTES de onNewTick: el callback de senal se dispara dentro de esa llamada y
            // necesita los flags del tick actual, no los del anterior.
            tickImpulseBullish = isBullish
            tickImpulseBearish = isBearish
            currentTickVelocity = velocity
            currentEngine.onNewTick(tick)
            prevTs = raw.ts
            prevPrice = raw.price
        }

        // Si termina el stream con una operacion abierta, el resultado es NO_DATA. No se
        // inventa TIE ni se busca un tick futuro fuera de la secuencia observada.
        if (openSignal != null) {
            causalGate.closeWithoutSettlement()
            settleOpen(null)
        }

        return trades
    }

    private fun writeJournalCsv(trades: List<SimulatedTrade>, outFile: File) {
        outFile.parentFile?.mkdirs()
        FileWriter(outFile, false).use { writer ->
            // R23 Iter 3: cabecera ampliada con `decision_id` (penultima columna, antes de `source`).
            // El orden replica el de la spec de la iteracion para que el comparador Live <-> Replay
            // lea la misma columna en ambos lados.
            // R23A Iter 1: se anade `asset` como PRIMERA columna despues de `timestamp`. El
            // comparador Live <-> Replay la lee directamente (`row.get("asset")`) y sin ella caia a
            // su heuristica por `source`/`reason`/nombre, que producia una divergencia espuria de
            // activo en la Iter 4. El valor es el RIC, igual que en el lado live.
            writer.write("timestamp,asset,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason,decision_id,source\n")
            for (t in trades) {
                val cleanReason = t.reason.replace(",", ";").replace("\n", " ").take(140)
                val line = String.format(
                    Locale.US,
                    "%d,%s,%s,%s,%s,%s,%.2f,%.17g,%.2f,%.2f,%s,%.2f,%d,%s,%.2f,%.2f,%.2f,%s,%s,%d,%s,\"%s\",%s,%s\n",
                    t.timestamp, t.assetRic, t.isoDate, t.strategy, t.submode, t.action.name, t.confidence,
                    t.entryPrice, t.stake, t.baseBalance, t.result, t.settledBalance, t.durationSec,
                    t.trend, t.distSupportRatio, t.distResistanceRatio, t.tickVelocity, t.impulse,
                    t.marketRegime, t.candleSecond, t.adaptiveStatus, cleanReason, t.decisionId, t.source
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

        println("=== DIAGNOSTIC_REPLAY_ONLY: full execution-policy/adaptive scheduling parity NOT established; no edge inference ===")
        println("Archivos a procesar: ${filesToProcess.size}")

        val allTrades = mutableListOf<SimulatedTrade>()
        for ((idx, f) in filesToProcess.withIndex()) {
            val allTicks = loadTicks(f)
            val ticks = if (t0Val != null && t0Val > 0L) allTicks.filter { it.ts <= t0Val } else allTicks
            if (ticks.size < 10) {
                println("DATASET_TOO_SHORT ${f.absolutePath}: ${ticks.size} ticks; no performance inference")
                continue
            }

            val trades = runBacktestOnStrategy(ticks, AutoTradeStrategy.AUTO_ADAPTIVE, submodoDeEntorno())
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
        val noData = allTrades.count { it.result == "NO_DATA" }
        println("NO_SETTLEMENT_DATA=$noData; excluded from WIN/LOSS, never counted as TIE")
        val nonTies = wins + losses
        val wr = if (nonTies > 0) (wins.toDouble() / nonTies * 100.0) else 0.0

        println(String.format(Locale.US, "Resultado: W=%d, L=%d, TIE=%d | WR=%.2f%%", wins, losses, ties, wr))
        // R23J Fase B (B2): volcar los bloqueos adaptativos para poder contrastarlos con los vetos
        // reales. Se escribe junto al journal, con sufijo propio, para no tocar el contrato del CSV
        // que consume el comparador congelado.
        val outBloqueos = File(outFile.parentFile ?: File("."), "adaptive_blocks_replay.csv")
        synchronized(bloqueosRegistrados) {
            FileWriter(outBloqueos, false).use { w ->
                w.write("timestamp,action,similarity,sim_pct,strategy\n")
                for (b in bloqueosRegistrados) {
                    w.write("${b.ts},${b.action},${b.similitud},${b.simPct},${b.strategy}\n")
                }
            }
        }
        println("Bloqueos ADAPTIVE_BLOCK del replay: ${bloqueosRegistrados.size} -> ${outBloqueos.absolutePath}")
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
            val trades = runBacktestOnStrategy(ticks, strategy, submodoDeEntorno())
            val wins = trades.count { it.result == "WIN" }
            val losses = trades.count { it.result == "LOSS" }
            val wr = if (wins + losses > 0) (wins.toDouble() / (wins + losses) * 100.0) else 0.0
            println(String.format(Locale.US, "%-28s -> Señales: %2d | W: %2d, L: %2d | WR: %5.1f%%", strategy.name, trades.size, wins, losses, wr))
        }
    }

    /** Open-time ts alone cannot establish when the loss became known. Reject unknown snapshots. */
    private fun validateAdaptiveState(root: org.json.JSONObject, windowStart: Long, asset: String) {
        val captured = root.optLong("captured_at_ms", -1L)
        require(captured in 0 until windowStart) { "SNAPSHOT_NOT_PRE_WINDOW" }
        val signatures = root.getJSONArray("lossSignatures")
        for (i in 0 until signatures.length()) {
            val loss = signatures.getJSONObject(i)
            val settled = loss.optLong("settled_at_ms", -1L)
            val opened = loss.optLong("ts", -1L)
            require(opened >= 0L && settled >= opened + 60_000L && settled <= captured) { "LOSS_NOT_KNOWN_PRE_WINDOW" }
            require(loss.optString("asset") == asset) { "ADAPTIVE_ASSET_MISMATCH" }
        }
    }

    @Test fun r29cFutureAdaptiveSnapshotMustFail() {
        val contaminated = org.json.JSONObject("{\"captured_at_ms\":200000,\"lossSignatures\":[]}")
        try {
            validateAdaptiveState(contaminated, 100000, "A")
            org.junit.Assert.fail("Future adaptive state was accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun r29cLossKnownAfterStartMustFail() {
        val contaminated = org.json.JSONObject("{\"captured_at_ms\":90000,\"lossSignatures\":[{\"ts\":0,\"settled_at_ms\":110000,\"asset\":\"A\"}]}")
        try {
            validateAdaptiveState(contaminated, 100000, "A")
            org.junit.Assert.fail("Future loss was accepted")
        } catch (_: IllegalArgumentException) { }
    }

    // R29-C adversarial regressions. Fixtures diagnose infrastructure, never edge.
    @Test fun r29cTimestampCollisionPreservesBothAssets() {
        val file = File.createTempFile("r29c-assets", ".csv")
        try {
            file.writeText("ts,ric,price\n1000,A,100.0\n1000,B,200.0\n1000,A,100.0\n")
            org.junit.Assert.assertEquals(2, loadTicks(file).size)
        } finally { file.delete() }
    }

    @Test fun r29cExpiryUsesFirstFutureTick() {
        val ticks = listOf(RawTick(59_999, "A", 99.0), RawTick(60_001, "A", 101.0))
        org.junit.Assert.assertEquals(101.0, priceAt(ticks, 60_000)!!, 0.0)
    }

    @Test fun r29cMissingSettlementIsNotTie() {
        org.junit.Assert.assertEquals("NO_DATA", resolveTrade(100.0, null, TradeAction.BUY).first)
    }

    private companion object {
        /**
         * R23C Fase 2 (REQ 1): submodo del replay. Por defecto CONSERVATIVE (fail-safe: reproduce la
         * politica mas restrictiva). En vivo R23C corre YOLO, asi que la invocacion de paridad debe
         * exportar `BACKTEST_SUBMODE=YOLO` o el replay suprimiria señales que el motor real si emitio.
         */
        fun submodoDeEntorno(): AutonomousSubMode {
            val raw = System.getenv("BACKTEST_SUBMODE") ?: System.getProperty("BACKTEST_SUBMODE") ?: "CONSERVATIVE"
            return try {
                AutonomousSubMode.valueOf(raw.trim().uppercase())
            } catch (_: Exception) {
                AutonomousSubMode.CONSERVATIVE
            }
        }

        /** Duracion de una operacion binaria, en ms. Coincide con `durationSec = 60` de la fila. */
        const val TRADE_DURATION_MS = 60_000L

        /** Cooldown post-trade en YOLO (`yoloLossCooldownSeconds` = 35 s no aplica sin racha). */
        const val YOLO_COOLDOWN_MS = 8_000L

        /** Cooldown post-trade en CONSERVATIVE. */
        const val CONSERVATIVE_COOLDOWN_MS = 35_000L
    }
}
