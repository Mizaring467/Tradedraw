package com.example.tradedraw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TradeDraw_Home", MODE_PRIVATE) }
    private var pendingHeadless: Boolean? = null
    private lateinit var agentSettings: AgentConnectionSettings
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val requestedMode = pendingHeadless
        pendingHeadless = null
        if (requestedMode != null && Settings.canDrawOverlays(this)) startOverlay(requestedMode)
        else Toast.makeText(this, R.string.home_overlay_required, Toast.LENGTH_SHORT).show()
        refresh()
    }
    private fun selected() = prefs.getString("broker", "binomo")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingHeadless = if (savedInstanceState?.containsKey("pendingHeadless") == true)
            savedInstanceState.getBoolean("pendingHeadless") else null
        agentSettings = AgentConnectionSettings(this) { refresh() }
        val spinner = findViewById<Spinner>(R.id.broker_selector)
        spinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            BrokerCatalog.profiles.map { it.label + if (it.available) "" else " · Próximamente" }) {
            private fun tint(view: View): View = view.apply { (this as? TextView)?.setTextColor(android.graphics.Color.rgb(225, 236, 248)) }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = tint(super.getView(position, convertView, parent))
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = tint(super.getDropDownView(position, convertView, parent))
        }
        spinner.setSelection(BrokerCatalog.profiles.indexOfFirst { it.id == selected() }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("broker", BrokerCatalog.profiles[position].id).apply()
                refresh()
            }
        }
        findViewById<Button>(R.id.btn_link_binomo).setOnClickListener {
            if (BrokerCatalog.canStart(selected())) startActivity(Intent(this, BinomoAuthActivity::class.java))
        }
        findViewById<Button>(R.id.btn_headless).setOnClickListener { requestStart(true) }
        findViewById<Button>(R.id.boton).visibility = View.GONE
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_agent_settings).setOnClickListener { agentSettings.show() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            startService(Intent(this, OverlayService::class.java).apply { action = "com.example.tradedraw.STOP" })
            Toast.makeText(this, R.string.home_stopped, Toast.LENGTH_SHORT).show()
        }
        // Opening the broker chooser must not launch Binomo or start an overlay implicitly.
        refresh()
    }
    override fun onResume() { super.onResume(); if (::agentSettings.isInitialized) refresh() }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingHeadless?.let { outState.putBoolean("pendingHeadless", it) }
        super.onSaveInstanceState(outState)
    }
    private fun refresh() {
        val available = BrokerCatalog.canStart(selected())
        val linked = com.example.tradedraw.core.SharedPreferencesCredentialStore.fromContext(this).getAuthToken().isNotBlank()
        findViewById<TextView>(R.id.broker_status).setText(when {
            !available -> R.string.home_broker_pending
            linked -> R.string.home_broker_saved
            else -> R.string.home_broker_missing
        })
        for (id in listOf(R.id.btn_headless, R.id.boton, R.id.btn_link_binomo)) {
            findViewById<Button>(id).apply { isEnabled = available; alpha = if (available) 1f else 0.45f }
        }
        findViewById<Button>(R.id.btn_link_binomo).setText(if (linked) R.string.home_relink else R.string.home_link)
        val access = AutoTradeAccessibilityService.instance != null || AutoTradeAccessibilityService.isAccessibilityPermissionGranted(this)
        findViewById<TextView>(R.id.permission_status).text = getString(
            R.string.home_permissions,
            getString(if (Settings.canDrawOverlays(this)) R.string.home_ready else R.string.home_pending),
            getString(if (access) R.string.home_ready else R.string.home_pending))
        findViewById<TextView>(R.id.agent_status).text = agentSettings.summary()
    }
    private fun requestStart(headless: Boolean) {
        if (!BrokerCatalog.canStart(selected())) return
        if (Settings.canDrawOverlays(this)) startOverlay(headless)
        else {
            pendingHeadless = headless
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
    private fun startOverlay(headless: Boolean) {
        if (!BrokerCatalog.canStart(selected())) return
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
            putExtra("EXTRA_HEADLESS_MODE", headless)
        })
        Toast.makeText(this, R.string.home_started, Toast.LENGTH_SHORT).show()
    }
    override fun onDestroy() { if (::agentSettings.isInitialized) agentSettings.destroy(); super.onDestroy() }
}
