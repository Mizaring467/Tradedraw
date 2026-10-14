package com.example.tradedraw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tradedraw.R

class MainActivity : AppCompatActivity() {
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startFloatingService()
        } else {
            Toast.makeText(this, "Permiso denegado. No se puede iniciar el overlay.", Toast.LENGTH_SHORT).show()
        }
    }
    private lateinit var btnAccessibility: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titulo = findViewById<TextView>(R.id.titulo)
        titulo.text = "📊 TradeDraw"

        val boton = findViewById<Button>(R.id.boton)
        boton.text = "Iniciar Overlay (con Visión IA)"

        btnStop = findViewById(R.id.btn_stop)
        btnStop.setOnClickListener {
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = "com.example.tradedraw.STOP"
            }
            startService(stopIntent)
            Toast.makeText(this, "Overlay detenido", Toast.LENGTH_SHORT).show()
        }
        
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Busca 'TradeDraw' y activa el servicio de accesibilidad", Toast.LENGTH_LONG).show()
        }

        val btnHeadless = findViewById<Button>(R.id.btn_headless)
        btnHeadless.setOnClickListener {
            if (checkOverlayPermission()) {
                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtra("EXTRA_HEADLESS_MODE", true)
                }
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(this, "🚀 Modo WebSocket Puro Activo (0% captura de pantalla)", Toast.LENGTH_LONG).show()
                finish()
            } else {
                requestOverlayPermission()
            }
        }

        val btnLinkBinomo = findViewById<Button>(R.id.btn_link_binomo)
        btnLinkBinomo.setOnClickListener {
            val intent = Intent(this, BinomoAuthActivity::class.java)
            startActivity(intent)
        }

        boton.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        // Ronda 15 / B1: si el permiso de overlay YA estaba concedido, arrancar el servicio sin
        // esperar a que alguien pulse el boton. Motivo medido: `adb install -r` mata el proceso y
        // abrir la app no ejecutaba ningun listener, asi que el bridge :8080 quedaba caido y no
        // habia forma de leer /status en modo desatendido. No se concede ningun permiso nuevo: se
        // automatiza un arranque que el usuario ya habia autorizado. No se toca el modo.
        //
        // La condicion es SOLO `canDrawOverlays`, tal y como fija el preregistro. NO se exige el
        // servicio de accesibilidad: el bridge y el HUD no lo necesitan, y atarlo a el dejaria el
        // bridge caido justo cuando la accesibilidad esta desactivada (medido: bloqueo espurio).
        if (checkOverlayPermission()) {
            launchService(null)
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityButtonState()
        updateBinomoTokenState()
    }

    private fun updateBinomoTokenState() {
        val btnLinkBinomo = findViewById<Button>(R.id.btn_link_binomo) ?: return
        val prefs = getSharedPreferences("TradeDraw_WSConfig", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("ws_auth_token", "") ?: ""
        if (token.isNotEmpty()) {
            btnLinkBinomo.text = "🔑 Sesión Binomo: VINCULADA ✓"
            btnLinkBinomo.setTextColor(android.graphics.Color.parseColor("#22c55e"))
        } else {
            btnLinkBinomo.text = "🔑 Vincular Cuenta Binomo (Token WebSocket)"
            btnLinkBinomo.setTextColor(android.graphics.Color.parseColor("#38bdf8"))
        }
    }

    private fun updateAccessibilityButtonState() {
        val isConnected = AutoTradeAccessibilityService.instance != null || AutoTradeAccessibilityService.isAccessibilityPermissionGranted(this)
        if (isConnected) {
            btnAccessibility.text = "Accesibilidad: ACTIVA ✓"
            btnAccessibility.setBackgroundColor(android.graphics.Color.parseColor("#15803d"))
        } else {
            btnAccessibility.text = "Activar Accesibilidad (Auto-Trading)"
            btnAccessibility.setBackgroundColor(android.graphics.Color.parseColor("#334155"))
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            Toast.makeText(this, "Por favor, permite que TradeDraw se muestre sobre otras apps", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFloatingService() {
        // Modo WebSocket Puro: NO se solicita MediaProjection (la grabación de pantalla agota
        // la batería del teléfono y lo ralentiza). Se arranca el overlay directamente.
        launchService(null)
    }

    private fun launchService(data: Intent?) {
        val intent = Intent(this, OverlayService::class.java)
        if (data != null) intent.putExtra("EXTRA_MEDIA_PROJECTION_DATA", data)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Overlay iniciado en Modo WebSocket Puro (sin grabación)", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Permiso overlay denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
