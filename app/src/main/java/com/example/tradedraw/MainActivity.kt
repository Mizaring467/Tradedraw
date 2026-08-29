package com.example.tradedraw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tradedraw.R

class MainActivity : AppCompatActivity() {
    private val REQUEST_MEDIA_PROJECTION = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titulo = findViewById<TextView>(R.id.titulo)
        titulo.text = "📊 TradeDraw"

        val boton = findViewById<Button>(R.id.boton)
        boton.text = "Iniciar Overlay"
        
        boton.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
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
            startActivityForResult(intent, 123)
            Toast.makeText(this, "Por favor, permite que TradeDraw se muestre sobre otras apps", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFloatingService() {
        // Solicitamos permiso para grabar la pantalla antes de iniciar el servicio
        val mediaProjectionManager = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    private fun launchService(data: Intent) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("EXTRA_MEDIA_PROJECTION_DATA", data)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Overlay iniciado con ScreenCapture", Toast.LENGTH_SHORT).show()
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
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                launchService(data)
            } else {
                Toast.makeText(this, "Permiso de grabación de pantalla denegado", Toast.LENGTH_SHORT).show()
                // Iniciamos igual el servicio sin captura? Para que la app original no se rompa
                launchService(Intent())
            }
        }
    }
}
