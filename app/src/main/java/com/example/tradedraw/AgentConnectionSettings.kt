package com.example.tradedraw

import android.app.AlertDialog
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.URI

/** Uses the same configuration as the running chat, without exposing saved credentials. */
class AgentConnectionSettings(private val activity: AppCompatActivity, private val changed: () -> Unit) {
    private val client by lazy { AIClient(activity.applicationContext) }
    private var initialized = false
    private var dialog: AlertDialog? = null
    private var testing = false
    private var generation = 0
    fun summary(): String {
        initialized = true
        return if (client.apiKey.isBlank()) "Sin clave · Configura el agente para conversar"
        else "Credencial guardada · Usa Probar conexión para verificarla"
    }
    fun show() {
        initialized = true
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, pad / 2) }
        fun field(label: String, value: String, secret: Boolean = false): EditText {
            content.addView(TextView(activity).apply { text = label })
            return EditText(activity).apply {
                setSingleLine(true)
                inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(value)
                if (secret) { hint = "Nueva clave (vacío conserva la actual)"; if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
                content.addView(this)
            }
        }
        val url = field("Endpoint compatible con OpenAI", client.baseUrl)
        val model = field("Modelo efectivo", client.model)
        val key = field("API key", "", true)
        val status = TextView(activity).apply { text = "La prueba envía un mensaje breve al modelo. No ejecuta órdenes."; setPadding(0, pad / 2, 0, pad / 2) }
        content.addView(status)
        val test = Button(activity).apply { text = "Guardar y probar conexión" }
        content.addView(test)
        fun save(): Boolean {
            val endpoint = url.text.toString().trim().trimEnd('/')
            val uri = try { URI(endpoint) } catch (_: Exception) { null }
            val local = uri?.host in listOf("localhost", "127.0.0.1", "[::1]", "::1")
            if (uri?.host.isNullOrBlank() || uri?.rawUserInfo != null || uri?.rawQuery != null || uri?.rawFragment != null ||
                !(uri?.scheme == "https" || (uri?.scheme == "http" && local))) {
                url.error = "Usa HTTPS; HTTP solo para localhost"; return false
            }
            if (model.text.toString().trim().isEmpty()) { model.error = "Indica un modelo"; return false }
            if (endpoint != client.baseUrl && key.text.toString().isBlank() && client.apiKey.isNotBlank()) {
                key.error = "Introduce la clave del nuevo endpoint"; return false
            }
            if (key.text.toString().isBlank() && client.apiKey.isBlank()) { key.error = "Falta la API key"; return false }
            client.baseUrl = endpoint
            client.model = model.text.toString().trim()
            if (key.text.toString().isNotBlank()) client.apiKey = key.text.toString()
            key.setText("")
            // Show the effective model in case the existing adapter maps an unsupported alias.
            model.setText(client.model)
            changed()
            return true
        }
        val scroll = ScrollView(activity).apply { addView(content) }
        val current = AlertDialog.Builder(activity).setTitle("Conectar agente IA").setView(scroll)
            .setPositiveButton("Guardar", null).setNegativeButton("Cerrar", null)
            .setNeutralButton("Eliminar clave", null).create()
        dialog = current
        current.setOnShowListener {
            current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (!testing && save()) current.dismiss() }
            current.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!testing) {
                    client.apiKey = ""; key.setText(""); status.text = "Clave eliminada. El chat necesita una credencial válida."; changed()
                }
            }
        }
        test.setOnClickListener {
            if (!testing && save()) {
                testing = true
                current.setCancelable(false)
                current.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                val requestGeneration = ++generation
                test.isEnabled = false; url.isEnabled = false; model.isEnabled = false; key.isEnabled = false
                status.text = "Probando el modelo…"
                probe(client.baseUrl, client.model, client.apiKey) { ok, message ->
                    if (requestGeneration == generation && !activity.isDestroyed && !activity.isFinishing) {
                        testing = false
                        current.setCancelable(true)
                        current.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                        test.isEnabled = true; url.isEnabled = true; model.isEnabled = true; key.isEnabled = true
                        status.text = when {
                            ok -> "Conexión verificada · "+client.model
                            message.contains("401") || message.contains("403") -> "Credencial rechazada. Revisa su vigencia y permisos."
                            message.contains("429") -> "Cuota o límite de solicitudes alcanzado."
                            message.contains("404") -> "Endpoint o modelo no disponible."
                            else -> "No se pudo verificar. Revisa servidor, conexión y modelo."
                        }
                    }
                }
            }
        }
        current.setOnDismissListener { generation++; testing = false; dialog = null }
        current.show()
    }
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private fun probe(endpoint: String, model: String, key: String, result: (Boolean, String) -> Unit) {
        executor.execute {
            var connection: java.net.HttpURLConnection? = null
            val outcome = try {
                connection = java.net.URL("$endpoint/chat/completions").openConnection() as java.net.HttpURLConnection
                connection.apply {
                    requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000
                    instanceFollowRedirects = false; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val payload = org.json.JSONObject().put("model", model).put("max_tokens", 64)
                    .put("messages", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", "Responde solamente: OK")))
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                val code = connection.responseCode
                if (code in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val content = org.json.JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.opt("content") as? String
                    (!content.isNullOrBlank()) to "response"
                } else false to code.toString()
            } catch (_: Exception) { false to "network" }
            finally { connection?.disconnect() }
            activity.runOnUiThread { result(outcome.first, outcome.second) }
        }
    }
    fun destroy() { executor.shutdownNow(); generation++; dialog?.dismiss(); if (initialized) client.destroy() }
}
