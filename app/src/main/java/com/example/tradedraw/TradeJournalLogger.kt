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
 * Escribe en formato CSV parseable para auditoría por ADB, análisis cuantitativo y backtesting real.
 */
object TradeJournalLogger {
    private const val TAG = "TradeJournalLogger"
    private const val HEADER = "timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,settled_balance,duration_sec,trend,dist_support,dist_resistance,tick_velocity,impulse,market_regime,candle_second,adaptive_status,reason\n"
    private val lock = Any()

    /**
     * Escrituras exitosas desde el arranque del proceso. Diagnóstico expuesto por el bridge HTTP.
     */
    @Volatile
    var writeCount: Long = 0L
        private set

    /** Timestamp de la última escritura exitosa; 0L si nunca se ha escrito. */
    @Volatile
    var lastWriteMs: Long = 0L
        private set

    /** Último error de escritura (vacío si ninguno). Se expone para diagnostico externo. */
    @Volatile
    var lastError: String = ""
        private set

    /** Tamaño máximo del journal antes de rotar a trade_journal_<timestamp>.csv (2 MB). */
    private const val MAX_JOURNAL_BYTES = 2L * 1024L * 1024L

    private const val JOURNAL_NAME = "trade_journal.csv"

    /** Firma del último trade registrado: "<action>|<epochMs del trade>". Evita el doble registro. */
    private var lastSignature: String? = null

    /** Firma en curso, fijada como última solo si la escritura en disco tiene éxito. */
    private var pendingSignature: String? = null

    /**
     * Escrituras rechazadas por la guarda de idempotencia. Si crece con una sesión real,
     * significa que alguna ruta de liquidación sigue disparándose dos veces: es la señal de alarma.
     */
    @Volatile
    var duplicateRejections: Long = 0L
        private set

    /** Rotaciones ejecutadas desde el arranque del proceso. */
    @Volatile
    var rotationCount: Long = 0L
        private set

    /**
     * Limpia el estado de la guarda de idempotencia. Uso exclusivo en tests: entre casos de prueba
     * el singleton conserva la última firma, lo que contaminaría el siguiente caso de la misma JVM.
     */
    internal fun resetIdempotencyStateForTests() {
        synchronized(lock) {
            lastSignature = null
            pendingSignature = null
        }
    }

    /** Antigüedad en ms de la última escritura; -1 si nunca se ha escrito. */
    val lastWriteAgeMs: Long
        get() {
            val ts = lastWriteMs
            return if (ts <= 0L) -1L else (System.currentTimeMillis() - ts).coerceAtLeast(0L)
        }

    /**
     * Registra una operación cerrada con todas sus características cuantitativas y contextuales.
     * Devuelve true si la evidencia quedó persistida en disco.
     *
     * @param tradeStartMs Timestamp de apertura del trade (pendingTradeStartTime). Junto con la acción
     *   forma la firma de idempotencia: un reintento de liquidación del MISMO trade se descarta.
     */
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
        reason: String,
        trend: String = "SIDEWAYS",
        distSupportRatio: Float = 0.5f,
        distResistanceRatio: Float = 0.5f,
        tickVelocity: Float = 0f,
        impulse: String = "NEUTRAL",
        marketRegime: String = "NORMAL",
        candleSecond: Int = 0,
        adaptiveStatus: String = "ALLOWED",
        tradeStartMs: Long = 0L
    ): Boolean {
        try {
            // getExternalFilesDir puede devolver null (almacenamiento externo no montado):
            // en ese caso la evidencia de auditoría se perdería en silencio, así que se
            // escala al almacenamiento interno antes de rendirse.
            val externalDir = context.getExternalFilesDir(null)
            val dir = if (externalDir != null) {
                File(externalDir, "TradeDraw_Audits")
            } else {
                Log.e(TAG, "getExternalFilesDir(null) devolvió null: usando almacenamiento interno")
                File(context.filesDir, "TradeDraw_Audits")
            }
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "No se pudo crear el directorio de auditoría: ${dir.absolutePath}")
            }

            synchronized(lock) {
                // Guarda de idempotencia: el mismo trade y acción no puede registrarse dos veces aunque
                // la liquidación se dispare duplicada.
                //
                // La firma EXIGE tradeStartMs: sin el timestamp del trade no hay forma de distinguir un
                // reintento de liquidación (duplicado) de un trade nuevo con la misma acción y resultado.
                // La liquidación siempre lo aporta (TradingEngine captura pendingTradeStartTime).
                if (tradeStartMs > 0L) {
                    val signature = "$action|$tradeStartMs"
                    if (signature == lastSignature) {
                        duplicateRejections++
                        lastError = "DUPLICADO RECHAZADO: $signature"
                        Log.w(TAG, "Registro duplicado rechazado (mismo trade y acción): $signature")
                        return false
                    }
                    // La firma se fija tras persistir en disco: si la escritura falla, el reintento
                    // del mismo trade debe seguir permitido o la evidencia se perdería para siempre.
                    pendingSignature = signature
                } else {
                    // Sin timestamp fiable (llamador que no lo aporta) la guarda se desactiva y se avisa.
                    Log.w(TAG, "Registro sin tradeStartMs: guarda de idempotencia desactivada para $action")
                }

                val file = File(dir, JOURNAL_NAME)
                rotateIfNeeded(dir, file)

                val writeHeader = !file.exists() || file.length() == 0L
                FileWriter(file, true).use { writer ->
                    if (writeHeader) {
                        writer.write(HEADER)
                    }
                    val now = System.currentTimeMillis()
                    val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now))
                    val cleanReason = reason.replace(",", ";").replace("\n", " ").take(140)
                    val line = String.format(
                        Locale.US,
                        "%d,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%d,%s,%.2f,%.2f,%.2f,%s,%s,%d,%s,\"%s\"\n",
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
                        trend,
                        distSupportRatio,
                        distResistanceRatio,
                        tickVelocity,
                        impulse,
                        marketRegime,
                        candleSecond,
                        adaptiveStatus,
                        cleanReason
                    )
                    writer.write(line)
                    writer.flush()
                }
                // La firma se fija SOLO tras persistir en disco: si la escritura falla, el reintento
                // del mismo trade debe seguir permitido o la evidencia se perdería para siempre.
                pendingSignature?.let {
                    lastSignature = it
                    pendingSignature = null
                }
                writeCount++
                lastWriteMs = System.currentTimeMillis()
                lastError = ""
            }
            Log.d(TAG, "Trade journal registrado: $action -> $result ($reason) [Trend=$trend, Sup=$distSupportRatio, Res=$distResistanceRatio, Vel=$tickVelocity]")
            return true
        } catch (e: Exception) {
            // La evidencia de auditoría NUNCA falla en silencio: sin journal no hay veredicto de edge.
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "FALLO AL PERSISTIR EVIDENCIA DE AUDITORÍA (trade $action $result): $lastError", e)
            return false
        }
    }

    /**
     * Rota el journal cuando supera MAX_JOURNAL_BYTES: renombra a trade_journal_<timestamp>.csv
     * y deja que el siguiente registro cree uno nuevo con cabecera.
     *
     * Los rotados NUNCA se borran ni comprimen: son evidencia de auditoría. Si el rename falla
     * (p. ej. fichero bloqueado), se sigue escribiendo en el journal activo en lugar de perder la fila.
     * Debe invocarse dentro del bloque `synchronized(lock)`.
     */
    private fun rotateIfNeeded(dir: File, file: File) {
        if (!file.exists() || file.length() < MAX_JOURNAL_BYTES) return

        val rotated = File(dir, "trade_journal_${System.currentTimeMillis()}.csv")
        if (file.renameTo(rotated)) {
            rotationCount++
            Log.i(TAG, "Journal rotado a ${rotated.name} (${rotated.length()} bytes)")
        } else {
            Log.e(TAG, "No se pudo rotar el journal (${file.length()} bytes): se continúa en el fichero activo")
        }
    }
}
