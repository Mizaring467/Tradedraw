package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

enum class BrokerProfile {
    BINOMO,
    QUOTEX,
    POCKET_OPTION,
    CUSTOM
}

class CalibrationManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("TradeDraw_Calibration", Context.MODE_PRIVATE)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    var activeProfile: BrokerProfile = BrokerProfile.valueOf(
        prefs.getString("active_profile", BrokerProfile.BINOMO.name) ?: BrokerProfile.BINOMO.name
    )
        set(value) {
            field = value
            prefs.edit().putString("active_profile", value.name).apply()
        }

    private var calibrationContainerView: View? = null

    fun isCalibrated(): Boolean {
        return getBuyCoordinates().first > 0f && getSellCoordinates().first > 0f
    }

    fun getBuyCoordinates(): Pair<Float, Float> {
        val p = activeProfile.name
        val x = prefs.getFloat("${p}_buy_x", -1f)
        val y = prefs.getFloat("${p}_buy_y", -1f)
        if (x >= 0 && y >= 0) return Pair(x, y)

        // Valores por defecto según resolución
        val dm = context.resources.displayMetrics
        return when (activeProfile) {
            BrokerProfile.BINOMO -> Pair(dm.widthPixels * 0.25f, dm.heightPixels * 0.88f)
            BrokerProfile.QUOTEX -> Pair(dm.widthPixels * 0.85f, dm.heightPixels * 0.55f)
            BrokerProfile.POCKET_OPTION -> Pair(dm.widthPixels * 0.88f, dm.heightPixels * 0.52f)
            BrokerProfile.CUSTOM -> Pair(dm.widthPixels * 0.25f, dm.heightPixels * 0.85f)
        }
    }

    fun getSellCoordinates(): Pair<Float, Float> {
        val p = activeProfile.name
        val x = prefs.getFloat("${p}_sell_x", -1f)
        val y = prefs.getFloat("${p}_sell_y", -1f)
        if (x >= 0 && y >= 0) return Pair(x, y)

        val dm = context.resources.displayMetrics
        return when (activeProfile) {
            BrokerProfile.BINOMO -> Pair(dm.widthPixels * 0.75f, dm.heightPixels * 0.88f)
            BrokerProfile.QUOTEX -> Pair(dm.widthPixels * 0.85f, dm.heightPixels * 0.70f)
            BrokerProfile.POCKET_OPTION -> Pair(dm.widthPixels * 0.88f, dm.heightPixels * 0.68f)
            BrokerProfile.CUSTOM -> Pair(dm.widthPixels * 0.75f, dm.heightPixels * 0.85f)
        }
    }

    fun saveBuyCoordinates(x: Float, y: Float) {
        prefs.edit()
            .putFloat("${activeProfile.name}_buy_x", x)
            .putFloat("${activeProfile.name}_buy_y", y)
            .apply()
    }

    fun saveSellCoordinates(x: Float, y: Float) {
        prefs.edit()
            .putFloat("${activeProfile.name}_sell_x", x)
            .putFloat("${activeProfile.name}_sell_y", y)
            .apply()
    }

    /**
     * Muestra en pantalla los pines interactivos arrastrables para calibrar los botones.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun startInteractiveCalibration(onFinished: () -> Unit) {
        dismissCalibration()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#44000000"))
        }

        val (buyX, buyY) = getBuyCoordinates()
        val (sellX, sellY) = getSellCoordinates()

        val buyPin = createPinView("SUBE", Color.parseColor("#22c55e"))
        val sellPin = createPinView("BAJA", Color.parseColor("#ef4444"))

        val pinSize = (80 * context.resources.displayMetrics.density).toInt()

        val buyParams = FrameLayout.LayoutParams(pinSize, pinSize).apply {
            leftMargin = (buyX - pinSize / 2).toInt().coerceAtLeast(0)
            topMargin = (buyY - pinSize / 2).toInt().coerceAtLeast(0)
        }
        val sellParams = FrameLayout.LayoutParams(pinSize, pinSize).apply {
            leftMargin = (sellX - pinSize / 2).toInt().coerceAtLeast(0)
            topMargin = (sellY - pinSize / 2).toInt().coerceAtLeast(0)
        }

        setupDrag(buyPin, buyParams, root) { x, y ->
            saveBuyCoordinates(x + pinSize / 2f, y + pinSize / 2f)
        }
        setupDrag(sellPin, sellParams, root) { x, y ->
            saveSellCoordinates(x + pinSize / 2f, y + pinSize / 2f)
        }

        root.addView(buyPin, buyParams)
        root.addView(sellPin, sellParams)

        // Panel superior con instrucciones y botón Guardar
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#EE0a0a0f"))
            setPadding(24, 16, 24, 16)
        }
        val txtInfo = TextView(context).apply {
            text = "🎯 Arrastra los pines sobre los botones de tu broker:"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val btnSave = Button(context).apply {
            text = "✓ GUARDAR"
            setBackgroundColor(Color.parseColor("#7c3aed"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                dismissCalibration()
                onFinished()
            }
        }
        banner.addView(txtInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        banner.addView(btnSave)

        val bannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = 50
        }
        root.addView(banner, bannerParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(root, windowParams)
            calibrationContainerView = root
        } catch (e: Exception) {
            android.util.Log.e("CalibrationManager", "Error inflando calibración", e)
        }
    }

    private fun createPinView(label: String, color: Int): View {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundColor(color)
            elevation = 20f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: View, params: FrameLayout.LayoutParams, root: FrameLayout, onPositionSaved: (Int, Int) -> Unit) {
        var startX = 0f
        var startY = 0f
        var initialMarginX = 0
        var initialMarginY = 0

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialMarginX = params.leftMargin
                    initialMarginY = params.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    params.leftMargin = (initialMarginX + dx).coerceIn(0, root.width - v.width)
                    params.topMargin = (initialMarginY + dy).coerceIn(0, root.height - v.height)
                    v.layoutParams = params
                    onPositionSaved(params.leftMargin, params.topMargin)
                    true
                }
                else -> false
            }
        }
    }

    fun dismissCalibration() {
        calibrationContainerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        calibrationContainerView = null
    }
}
