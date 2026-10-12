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
            // getExternalFilesDir puede devolver null (almacenamiento externo no montado) o apuntar a
            // un directorio heredado de una instalación anterior cuyo UID ya no es el nuestro: en
            // ambos casos la evidencia de auditoría se perdería en silencio, así que se escala al
            // almacenamiento interno antes de rendirse.
            val internalDir = File(context.filesDir, "TradeDraw_Audits")
            val externalRoot = context.getExternalFilesDir(null)
            val externalDir = externalRoot?.let { File(it, "TradeDraw_Audits") }
            val externalUsable = externalDir != null && isWritableDir(externalDir)
            val dir = if (externalUsable) {
                externalDir!!
            } else {
                if (externalDir == null) {
                    Log.e(TAG, "getExternalFilesDir(null) devolvió null: usando almacenamiento interno")
                } else {
                    Log.e(TAG, "Externo no escribible (${externalDir.absolutePath}): usando almacenamiento interno")
                }
                internalDir
            }

            synchronized(lock) {
                // Permisos externos recuperados: vaciar primero lo acumulado internamente.
                if (externalUsable) syncInternalToExternal(internalDir, dir)
                // Guarda de idempotencia: el mismo trade y acción no puede registrarse dos veces aunque
                // la liquidación se dispare duplicada.
                //
                // La firma EXIGE tradeStartMs: sin el timestamp del trade no hay forma de distinguir un
                // reintento de liquidación (duplicado) de un trade nuevo con la misma acción y resultado.
                // La liquidación siempre lo aporta (TradingEngine captura pendingTradeStartTime).
                if (tradeStartMs > 0L) {
                    val signature = "$tradeStartMs"
                    if (signature == lastSignature) {
                        duplicateRejections++
                        lastError = "DUPLICADO RECHAZADO: $signature"
                        Log.w(TAG, "Registro duplicado rechazado (mismo trade): $signature")
                        return false
                    }
                    // La firma se fija tras persistir en disco: si la escritura falla, el reintento
                    // del mismo trade debe seguir permitido o la evidencia se perdería para siempre.
                    pendingSignature = signature
                } else {
                    // Sin timestamp fiable (llamador que no lo aporta) la guarda se desactiva y se avisa.
                    Log.w(TAG, "Registro sin tradeStartMs: guarda de idempotencia desactivada para $action")
                }

                try {
                    val file = File(dir, JOURNAL_NAME)
                    val line = buildLine(action, result, reason, strategy, submode, confidence, priceY,
                        stake, baseBalance, settledBalance, durationSec, trend, distSupportRatio,
                        distResistanceRatio, tickVelocity, impulse, marketRegime, candleSecond,
                        adaptiveStatus)
                    // La escritura en el externo puede fallar aunque canWrite() dijera true: el directorio
                    // heredado de una instalación previa cambia de propietario con el UID nuevo de Android
                    // y FileWriter lanza Permission denied. Reintento inmediato en interno para no perder
                    // la evidencia (ni la firma de idempotencia, que solo se fija al persistir).
                    try {
                        writeRow(dir, file, line)
                    } catch (e: Exception) {
                        if (e !is java.io.IOException && e !is SecurityException) throw e
                        if (dir.canonicalPath != internalDir.canonicalPath) {
                            Log.e(TAG, "Fallo al escribir en ${file.absolutePath} (${e.javaClass.simpleName}): reintentando en almacenamiento interno", e)
                            writeRow(internalDir, File(internalDir, JOURNAL_NAME), line)
                        } else {
                            throw e
                        }
                    }
                    // La firma se fija SOLO tras persistir en disco: si la escritura falla, el reintento
                    // del mismo trade debe seguir permitido o la evidencia se perdería para siempre.
                    pendingSignature?.let {
                        lastSignature = it
                    }
                    writeCount++
                    lastWriteMs = System.currentTimeMillis()
                    lastError = ""
                } finally {
                    pendingSignature = null
                }
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
     * Comprueba que el directorio de auditoría es realmente escribible: debe existir (o poder crearse),
     * ser un directorio y admitir escritura. Un directorio heredado de una instalación anterior
     * retiene el UID antiguo y `canWrite()` devuelve false aunque la ruta pertenezca a la app.
     */
    private fun isWritableDir(dir: File): Boolean = runCatching {
        if (dir.exists()) dir.isDirectory && dir.canWrite()
        else dir.mkdirs() && dir.canWrite()
    }.getOrDefault(false)

    /**
     * Sincroniza los registros acumulados internamente hacia el almacenamiento externo cuando los
     * permisos se recuperan. El interno se anexa al final del externo y se borra solo tras confirmar
     * que la copia quedó persistida: en ningún caso se puede perder la evidencia de auditoría.
     */
    private fun syncInternalToExternal(internalDir: File, externalDir: File) {
        if (internalDir.canonicalPath == externalDir.canonicalPath) {
            Log.w(TAG, "syncInternalToExternal omitido: directorios interno y externo coinciden (${internalDir.canonicalPath})")
            return
        }
        val source = File(internalDir, JOURNAL_NAME)
        if (!source.exists() || source.length() == 0L) return
        try {
            val target = File(externalDir, JOURNAL_NAME)
            val text = source.readText()
            val firstLine = text.lineSequence().first()
            // Si el destino no existe (o está vacío) se copia también la cabecera del origen.
            val payload = if (!target.exists() || target.length() == 0L) text else {
                text.removePrefix(firstLine).removePrefix("\n")
            }
            if (payload.isEmpty()) return
            FileWriter(target, true).use { it.write(payload) }
            if (source.delete()) {
                Log.i(TAG, "Registros internos sincronizados al almacenamiento externo (${payload.length} bytes)")
            } else {
                // delete() falló (p.ej. archivo abierto): truncar para no duplicar en el siguiente sync.
                try {
                    FileWriter(source, false).use { }
                    Log.w(TAG, "source no borrado: truncado a 0 bytes para evitar duplicados")
                } catch (e2: Exception) {
                    Log.e(TAG, "No se pudo truncar el journal interno: ${e2.message}", e2)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo sincronizar el journal interno al externo: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /** Construye la fila CSV con el esquema fijo del HEADER. */
    private fun buildLine(
        action: String, result: String, reason: String, strategy: String, submode: String,
        confidence: Float, priceY: Float, stake: Number, baseBalance: Number,
        settledBalance: Number, durationSec: Long, trend: String, distSupportRatio: Float,
        distResistanceRatio: Float, tickVelocity: Float, impulse: String, marketRegime: String,
        candleSecond: Int, adaptiveStatus: String
    ): String {
        val now = System.currentTimeMillis()
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now))
        val cleanReason = reason.replace(",", ";").replace("\n", " ").take(140)
        return String.format(
            Locale.US,
            "%d,%s,%s,%s,%s,%.2f,%s,%.2f,%.2f,%s,%.2f,%d,%s,%.2f,%.2f,%.2f,%s,%s,%d,%s,\"%s\"\n",
            now, isoDate, strategy, submode, action, confidence, formatDynamicPrice(priceY.toDouble()), stake.toDouble(),
            baseBalance.toDouble(), result, settledBalance.toDouble(), durationSec, trend,
            distSupportRatio, distResistanceRatio, tickVelocity, impulse, marketRegime,
            candleSecond, adaptiveStatus, cleanReason
        )
    }

    /** Rota si procede y escribe la fila. Lanza IOException/SecurityException si el disco la rechaza. */
    private fun writeRow(dir: File, file: File, line: String) {
        if (!dir.exists()) dir.mkdirs()
        rotateIfNeeded(dir, file)
        val writeHeader = !file.exists() || file.length() == 0L
        FileWriter(file, true).use { writer ->
            if (writeHeader) writer.write(HEADER)
            writer.write(line)
            writer.flush()
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
