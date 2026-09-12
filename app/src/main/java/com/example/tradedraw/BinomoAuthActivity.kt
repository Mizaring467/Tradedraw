package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad para vincular la sesión de Binomo:
 * 1. Permite iniciar sesión directamente en un WebView seguro y captura la cookie 'authtoken' automáticamente.
 * 2. O permite ingresar manualmente el authtoken si el usuario ya lo posee.
 */
class BinomoAuthActivity : AppCompatActivity() {

    private val TAG = "BinomoAuthActivity"
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var etAuthToken: EditText
    private lateinit var btnSaveToken: Button
    private lateinit var btnClose: Button

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binomo_auth)

        val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)

        etAuthToken = findViewById(R.id.et_auth_token)
        btnSaveToken = findViewById(R.id.btn_save_token)
        btnClose = findViewById(R.id.btn_close)
        progressBar = findViewById(R.id.progress_bar)
        webView = findViewById(R.id.web_view)

        val existingToken = prefs.getString("ws_auth_token", "") ?: ""
        if (existingToken.isNotEmpty()) {
            etAuthToken.setText(existingToken)
        }

        btnClose.setOnClickListener { finish() }

        btnSaveToken.setOnClickListener {
            val token = etAuthToken.text.toString().trim()
            if (token.isNotEmpty()) {
                saveTokenAndNotify(token)
            } else {
                Toast.makeText(this, "Por favor ingresa un token válido", Toast.LENGTH_SHORT).show()
            }
        }

        // Configuración de WebView para captura de cookies
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { checkCookiesForToken(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { checkCookiesForToken(it) }
            }
        }

        webView.loadUrl("https://binomo.com/")
    }

    private fun checkCookiesForToken(url: String) {
        try {
            val cookieManager = CookieManager.getInstance()
            val cookieHeader = cookieManager.getCookie(url) ?: return

            // Buscar patrón de authtoken en las cookies de sesión
            val regex = Regex("""authtoken=([^;]+)""")
            val match = regex.find(cookieHeader)
            if (match != null) {
                val token = match.groupValues[1].trim()
                if (token.isNotEmpty()) {
                    Log.d(TAG, "¡Token de Binomo detectado automáticamente en cookies de sesión!")
                    saveTokenAndNotify(token)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspeccionando cookies de sesión", e)
        }
    }

    private fun saveTokenAndNotify(token: String) {
        val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)
        prefs.edit().putString("ws_auth_token", token).apply()

        // Actualizar el cliente WebSocket en memoria si el OverlayService está vivo
        try {
            val wsClient = OverlayService.instance?.binomoWebSocketClient
            if (wsClient != null) {
                wsClient.authToken = token
                wsClient.stop()
                wsClient.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notificando al WebSocket", e)
        }

        Toast.makeText(this, "✅ ¡Sesión de Binomo vinculada con éxito!\nEl WebSocket ya puede recibir cotizaciones a 0ms", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destruyendo WebView", e)
        }
        super.onDestroy()
    }
}
