package com.example.tradedraw

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.widget.Toast

/**
 * Captura excepciones no controladas para diagnóstico.
 * Guarda el último crash en SharedPreferences y, en el siguiente arranque,
 * lo muestra al usuario para poder reportarlo.
 */
object CrashLogger {

    private const val PREFS = "TradeDraw_Crash"
    private const val KEY_CRASH = "last_crash"

    fun install(context: Context) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.append("Thread: ").append(thread.name).append('\n')
                sb.append("Time: ").append(System.currentTimeMillis()).append('\n')
                sb.append(android.util.Log.getStackTraceString(throwable))
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_CRASH, sb.toString()).apply()
                android.util.Log.e("TradeDraw", "CRASH capturado", throwable)
            } catch (e: Exception) {
                android.util.Log.e("TradeDraw", "No se pudo guardar el crash", e)
            }
            // Dejar que Android maneje el crash normalmente
        }
    }

    fun reportPending(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crash = prefs.getString(KEY_CRASH, null) ?: return null
        prefs.edit().remove(KEY_CRASH).apply()
        return crash
    }

    fun showPending(context: Context) {
        val crash = reportPending(context) ?: return
        val msg = "⚠️ Último error de TradeDraw:\n${crash.take(300)}"
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } else {
                Looper.getMainLooper().post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            }
        } catch (e: Exception) {
            android.util.Log.e("TradeDraw", "No se pudo mostrar el crash", e)
        }
    }
}
