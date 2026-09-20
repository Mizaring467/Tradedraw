package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLDecoder

/**
 * Actividad dedicada a la autenticación segura en Binomo para capturar cookies de sesión,
 * tokens y alimentar el streaming WebSocket Headless a 0ms sin requerir grabación de pantalla.
 */
class BinomoAuthActivity : AppCompatActivity() {

    private val TAG = "BinomoAuthActivity"
    private lateinit var webViewContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var etAuthToken: EditText
    private lateinit var btnSaveToken: Button
    private lateinit var btnLinkSession: Button
    private lateinit var btnLoginEmail: Button
    private lateinit var btnReload: Button
    private lateinit var btnClose: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isTokenCaptured = false

    // Script JavaScript inyectado para interceptar WebSocket y reenviar ticks en tiempo real a Kotlin
    private val snifferJs = """
        (function() {
            if (window.__td_sniffer_hooked) return;
            window.__td_sniffer_hooked = true;
            console.log('[TradeDraw] JS WebSocket Sniffer instalado exitosamente');

            var OriginalWS = window.WebSocket;
            window.WebSocket = function(url, protocols) {
                console.log('[TD_WS_OPEN] URL=' + url);
                try {
                    if (window.TradeDrawBridge && window.TradeDrawBridge.onWsUrl) {
                        window.TradeDrawBridge.onWsUrl(url.toString());
                    }
                } catch(e) {}

                var ws = protocols ? new OriginalWS(url, protocols) : new OriginalWS(url);

                ws.addEventListener('message', function(ev) {
                    try {
                        var data = ev.data;
                        if (data instanceof ArrayBuffer) {
                            data = new TextDecoder().decode(data);
                        } else if (typeof Blob !== 'undefined' && data instanceof Blob) {
                            var reader = new FileReader();
                            reader.onload = function() {
                                var text = reader.result;
                                if (typeof text === 'string' && (text.includes('rate') || text.includes('price') || text.includes('tick') || text.includes('assets') || text.includes('ric'))) {
                                    if (window.TradeDrawBridge && window.TradeDrawBridge.onTick) {
                                        window.TradeDrawBridge.onTick(text);
                                    }
                                }
                            };
                            reader.readAsText(data);
                            return;
                        }
                        if (typeof data === 'string' && (data.includes('rate') || data.includes('price') || data.includes('tick') || data.includes('assets') || data.includes('ric'))) {
                            if (window.TradeDrawBridge && window.TradeDrawBridge.onTick) {
                                window.TradeDrawBridge.onTick(data);
                            }
                        }
                    } catch(e) {}
                });
                return ws;
            };
            window.WebSocket.prototype = OriginalWS.prototype;
        })();
    """.trimIndent()

    inner class TradeDrawBridge {
        @JavascriptInterface
        fun onWsUrl(url: String) {
            Log.i(TAG, "🎯 WebSocket URL interceptada desde Binomo Web: $url")
            val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)
            prefs.edit().putString("ws_endpoint_url", url).apply()
            OverlayService.instance?.binomoWebSocketClient?.wsUrl = url
        }

        @JavascriptInterface
        fun onTick(rawJson: String) {
            OverlayService.instance?.binomoWebSocketClient?.processIncomingMessage(rawJson)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binomo_auth)

        val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)

        webViewContainer = findViewById(R.id.webview_container)
        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        etAuthToken = findViewById(R.id.et_auth_token)
        btnSaveToken = findViewById(R.id.btn_save_token)
        btnLinkSession = findViewById(R.id.btn_link_session)
        btnLoginEmail = findViewById(R.id.btn_login_email)
        btnReload = findViewById(R.id.btn_reload)
        btnClose = findViewById(R.id.btn_close)

        val existingToken = prefs.getString("ws_auth_token", "") ?: ""
        if (existingToken.isNotEmpty()) {
            etAuthToken.setText(existingToken)
        }

        btnClose.setOnClickListener { finish() }

        btnReload.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            webView.reload()
        }

        btnLoginEmail.setOnClickListener {
            loadTradingLoginUrl()
        }

        btnLinkSession.setOnClickListener {
            linkCurrentSession()
        }

        btnSaveToken.setOnClickListener {
            val token = etAuthToken.text.toString().trim()
            if (token.isNotEmpty()) {
                saveTokenAndNotify(token)
            } else {
                Toast.makeText(this, "Por favor ingresa un token válido", Toast.LENGTH_SHORT).show()
            }
        }

        setupMainWebView()
        loadTradingLoginUrl()
    }

    private fun loadTradingLoginUrl() {
        progressBar.visibility = View.VISIBLE
        // Carga la URL oficial en español
        webView.loadUrl("https://binomo.com/es/trading")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMainWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        webView.addJavascriptInterface(TradeDrawBridge(), "TradeDrawBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("BinomoWebConsole", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                return true
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                Log.d(TAG, "Creando ventana emergente (popup) para OAuth...")
                val popupWebView = WebView(this@BinomoAuthActivity).apply {
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    this.settings.setSupportMultipleWindows(true)
                    this.settings.userAgentString = settings.userAgentString

                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView?, url: String?) {
                            super.onPageFinished(v, url)
                            url?.let { inspectUrlAndCookies(it) }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView?) {
                            Log.d(TAG, "Ventana emergente cerrada. Recargando página principal...")
                            (window?.parent as? ViewGroup)?.removeView(window)
                            window?.destroy()
                            loadTradingLoginUrl()
                        }
                    }
                }

                webViewContainer.addView(popupWebView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                Log.d(TAG, "Llamada a window.close() en WebView principal. Redirigiendo a formulario...")
                loadTradingLoginUrl()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(snifferJs, null)
                url?.let { inspectUrlAndCookies(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(snifferJs, null)
                url?.let { inspectUrlAndCookies(it) }
                checkLocalStorageForToken(view)
            }
        }
    }

    private fun inspectUrlAndCookies(url: String) {
        // 1. Detección en parámetros de URL
        if (url.contains("authtoken=") || url.contains("token=")) {
            val tokenRegex = Regex("""[?&](?:authtoken|token)=([^&#]+)""")
            tokenRegex.find(url)?.let { match ->
                val extracted = URLDecoder.decode(match.groupValues[1], "UTF-8").trim()
                if (extracted.isNotEmpty()) {
                    Log.d(TAG, "Token detectado en URL: $extracted")
                    mainHandler.post { etAuthToken.setText(extracted) }
                }
            }
        }

        // 2. Detección en CookieManager
        try {
            val cookieManager = CookieManager.getInstance()
            val cookieHeader = cookieManager.getCookie(url) ?: cookieManager.getCookie("https://binomo.com") ?: ""

            // Detección de error password_required en cookie oauth_result
            if (cookieHeader.contains("password_required")) {
                Log.w(TAG, "Binomo reportó password_required: La cuenta requiere contraseña")
                mainHandler.post {
                    Toast.makeText(this, "⚠️ Tu cuenta requiere contraseña para acceder.\nCargando formulario...", Toast.LENGTH_SHORT).show()
                }
                if (url.contains("oauth") || url.contains("callback") || url == "about:blank") {
                    mainHandler.postDelayed({ loadTradingLoginUrl() }, 500)
                }
            }

            // Buscar cookie authtoken
            val regex = Regex("""authtoken=([^;]+)""")
            val match = regex.find(cookieHeader)
            if (match != null) {
                val token = match.groupValues[1].trim()
                if (token.isNotEmpty()) {
                    mainHandler.post { etAuthToken.setText(token) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspeccionando cookies", e)
        }

        // 3. Recuperación preventiva si se queda en blanco
        if (url == "about:blank" || (url.contains("oauth_result.js") && !isTokenCaptured)) {
            Log.w(TAG, "Detectada página en blanco o oauth_result huérfana. Redirigiendo...")
            mainHandler.postDelayed({ loadTradingLoginUrl() }, 800)
        }
    }

    private fun checkLocalStorageForToken(view: WebView?) {
        if (view == null) return

        val jsCode = """
            (function() {
                try {
                    var token = localStorage.getItem('authtoken') || 
                                localStorage.getItem('token') || 
                                sessionStorage.getItem('authtoken');
                    if (!token) {
                        var userStr = localStorage.getItem('user');
                        if (userStr) {
                            var u = JSON.parse(userStr);
                            token = u.token || u.authtoken || '';
                        }
                    }
                    return token || '';
                } catch(e) { return ''; }
            })()
        """.trimIndent()

        view.evaluateJavascript(jsCode) { result ->
            val clean = result?.trim('"', ' ', '\'', '\\') ?: ""
            if (clean.isNotEmpty() && clean != "null" && clean != "undefined") {
                Log.d(TAG, "Token en LocalStorage: $clean")
                mainHandler.post { etAuthToken.setText(clean) }
            }
        }
    }

    /**
     * Vincula la sesión actual extrayendo todas las cookies activas de Binomo
     * y arrancando el motor Headless Broker en OverlayService.
     */
    private fun linkCurrentSession() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val cookies = cookieManager.getCookie("https://binomo.com") ?: ""

        val regexToken = Regex("""authtoken=([^;]+)""")
        val tokenMatch = regexToken.find(cookies)
        val token = tokenMatch?.groupValues?.get(1)?.trim() ?: etAuthToken.text.toString().trim()

        val regexDevice = Regex("""device_id=([^;]+)""")
        val devMatch = regexDevice.find(cookies)
        val devId = devMatch?.groupValues?.get(1)?.trim() ?: ""

        val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (cookies.isNotEmpty()) editor.putString("ws_cookie_header", cookies)
        if (token.isNotEmpty()) editor.putString("ws_auth_token", token)
        if (devId.isNotEmpty()) editor.putString("ws_device_id", devId)
        editor.apply()

        Log.d(TAG, "Sesión vinculada: token=$token, devId=$devId, cookiesLen=${cookies.length}")

        // Notificar al WebSocket client en OverlayService
        try {
            val wsClient = OverlayService.instance?.binomoWebSocketClient
            if (wsClient != null) {
                if (token.isNotEmpty()) wsClient.authToken = token
                if (devId.isNotEmpty()) wsClient.deviceId = devId
                if (cookies.isNotEmpty()) wsClient.cookieHeader = cookies
                wsClient.stop()
                wsClient.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notificando al WebSocket", e)
        }

        Toast.makeText(this, "✅ ¡Sesión vinculada con éxito!\nCotizaciones activas a 0ms sin captura de pantalla", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun saveTokenAndNotify(token: String) {
        val prefs = getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)
        prefs.edit().putString("ws_auth_token", token).apply()

        linkCurrentSession()
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
