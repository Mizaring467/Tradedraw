package com.example.tradedraw

import java.io.File

/**
 * Ronda 18: persistencia SOLO-APPEND de los cambios de modo en JSONL.
 *
 * MOTIVO. El ring de `TradeEventLog` es memoria viva: un reinicio del servicio lo vacia, y el
 * rearme de R14 sobrevivio precisamente a reinicios. Sin un registro en disco, un cambio de modo
 * que ocurre antes de que el observador mire deja de existir. Este diario lo conserva.
 *
 * GARANTIAS.
 * - **Solo-append**: nunca se reescribe ni se trunca el fichero vivo. La rotacion RENOMBRA.
 * - **Nunca propaga**: cualquier fallo de E/S se traga. El diario no puede impedir ni alterar un
 *   cambio de modo: es observabilidad, no control.
 * - **Acotado**: al superar [TOPE_BYTES] el fichero se mueve a `.1` y se empieza uno nuevo, asi que
 *   el espacio en disco queda limitado a ~2x[TOPE_BYTES]. El Redmi A1 tiene poco espacio.
 * - **Sin dependencias**: `File` + `appendText`. Nada de librerias de logging.
 *
 * NO registra datos sensibles: el detalle de un `MODE_CHANGE` son dos nombres de modo y una pila
 * de llamadas. Nunca tokens ni cookies.
 */
class ModeEventJournal(private val archivo: File, private val topeBytes: Long = TOPE_BYTES) {

    /**
     * Anade una linea. Devuelve `true` si se escribio, `false` si fallo (y el motivo se descarta:
     * el llamante no debe ramificar por esto).
     */
    fun append(linea: String): Boolean = try {
        archivo.parentFile?.mkdirs()
        rotarSiHaceFalta()
        archivo.appendText(linea + "\n")
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Rotacion simple: si el fichero supera el tope, se mueve a `<nombre>.1` (sobrescribiendo la
     * rotacion anterior) y se deja que la siguiente escritura cree uno nuevo.
     *
     * Se comprueba el tope CON holgura para no rotar en cada linea: sin ella, cada append tras
     * alcanzar el tope renombraria el fichero y el diario se perderia a si mismo.
     */
    private fun rotarSiHaceFalta() {
        if (!archivo.exists() || archivo.length() < topeBytes) return
        val rotado = File(archivo.absolutePath + ".1")
        if (rotado.exists()) rotado.delete()
        archivo.renameTo(rotado)
    }

    /** Lee las ultimas [max] lineas del fichero vivo. Vacio si no existe o falla. */
    fun tail(max: Int): List<String> = try {
        if (!archivo.exists()) emptyList()
        else archivo.readLines().takeLast(max.coerceAtLeast(1))
    } catch (e: Exception) {
        emptyList()
    }

    /** Numero de lineas del fichero vivo, o 0. */
    fun lineas(): Int = try {
        if (archivo.exists()) archivo.readLines().size else 0
    } catch (e: Exception) {
        0
    }

    companion object {
        /** Tope del fichero vivo. Con la rotacion, el uso en disco queda por debajo de ~512 KB. */
        const val TOPE_BYTES = 256L * 1024L

        /** Nombre del diario dentro de `filesDir`. */
        const val NOMBRE = "mode_changes.jsonl"

        /**
         * Enlaza el diario al `TradeEventLog`. Idempotente: llamarlo dos veces no duplica el
         * sumidero, y llamarlo con rutas distintas sustituye el anterior.
         */
        fun instalar(directorio: File): ModeEventJournal {
            val diario = ModeEventJournal(File(directorio, NOMBRE))
            TradeEventLog.persistSink = { linea -> diario.append(linea) }
            return diario
        }
    }
}
