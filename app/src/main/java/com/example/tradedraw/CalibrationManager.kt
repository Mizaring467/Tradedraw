package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
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

    companion object {
        fun getRealScreenDimensions(context: Context): Pair<Float, Float> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            return if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.maximumWindowMetrics.bounds
                    Pair(bounds.width().toFloat(), bounds.height().toFloat())
                } else {
                    val dm = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getRealMetrics(dm)
                    Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }
            } else {
                val dm = context.resources.displayMetrics
                Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
            }
        }
    }

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

    private fun getOrientationKey(): String {
        val (w, h) = getRealScreenDimensions(context)
        return if (w > h) "land" else "port"
    }

    fun isCalibrated(): Boolean {
        return getBuyCoordinates().first > 0f && getSellCoordinates().first > 0f
    }

    fun getBuyCoordinates(): Pair<Float, Float> {
        val (w, h) = getRealScreenDimensions(context)
        val isLandscape = w > h

        val p = activeProfile.name
        val o = getOrientationKey()
        val x = prefs.getFloat("${p}_${o}_buy_x", -1f)
        val y = prefs.getFloat("${p}_${o}_buy_y", -1f)
        if (x >= 0 && y >= 0) {
            val isValid = if (isLandscape) (x >= w * 0.80f) else (y >= h * 0.865f)
            if (isValid) return Pair(x, y)
        }

        return if (isLandscape) {
            // Horizontal (Landscape): Botones a la derecha
            when (activeProfile) {
                BrokerProfile.BINOMO -> Pair(w * 0.881f, h * 0.735f) // Botón SUBE verde arriba de Baja
                BrokerProfile.QUOTEX -> Pair(w * 0.88f, h * 0.55f)
                BrokerProfile.POCKET_OPTION -> Pair(w * 0.88f, h * 0.52f)
                BrokerProfile.CUSTOM -> Pair(w * 0.881f, h * 0.735f)
            }
        } else {
            // Vertical (Portrait): Botones abajo en franja inferior limpia (90.3% de la pantalla física)
            when (activeProfile) {
                BrokerProfile.BINOMO -> Pair(w * 0.25f, h * 0.903f)
                BrokerProfile.QUOTEX -> Pair(w * 0.25f, h * 0.88f)
                BrokerProfile.POCKET_OPTION -> Pair(w * 0.25f, h * 0.88f)
                BrokerProfile.CUSTOM -> Pair(w * 0.25f, h * 0.903f)
            }
        }
    }

    fun getSellCoordinates(): Pair<Float, Float> {
        val (w, h) = getRealScreenDimensions(context)
        val isLandscape = w > h

        val p = activeProfile.name
        val o = getOrientationKey()
        val x = prefs.getFloat("${p}_${o}_sell_x", -1f)
        val y = prefs.getFloat("${p}_${o}_sell_y", -1f)
        if (x >= 0 && y >= 0) {
            val isValid = if (isLandscape) (x >= w * 0.80f) else (y >= h * 0.865f)
            if (isValid) return Pair(x, y)
        }

        return if (isLandscape) {
            // Horizontal (Landscape): Botón BAJA rojo abajo de Sube
            when (activeProfile) {
                BrokerProfile.BINOMO -> Pair(w * 0.881f, h * 0.844f) // Botón BAJA rojo
                BrokerProfile.QUOTEX -> Pair(w * 0.88f, h * 0.70f)
                BrokerProfile.POCKET_OPTION -> Pair(w * 0.88f, h * 0.68f)
                BrokerProfile.CUSTOM -> Pair(w * 0.881f, h * 0.844f)
            }
        } else {
            // Vertical (Portrait): Botón BAJA abajo a la derecha (90.3% de la pantalla)
            when (activeProfile) {
                BrokerProfile.BINOMO -> Pair(w * 0.75f, h * 0.903f)
                BrokerProfile.QUOTEX -> Pair(w * 0.75f, h * 0.88f)
                BrokerProfile.POCKET_OPTION -> Pair(w * 0.75f, h * 0.88f)
                BrokerProfile.CUSTOM -> Pair(w * 0.75f, h * 0.903f)
            }
        }
    }

    fun saveBuyCoordinates(x: Float, y: Float) {
        val o = getOrientationKey()
        prefs.edit()
            .putFloat("${activeProfile.name}_${o}_buy_x", x)
            .putFloat("${activeProfile.name}_${o}_buy_y", y)
            .apply()
    }

    fun saveSellCoordinates(x: Float, y: Float) {
        val o = getOrientationKey()
        prefs.edit()
            .putFloat("${activeProfile.name}_${o}_sell_x", x)
            .putFloat("${activeProfile.name}_${o}_sell_y", y)
            .apply()
    }

    fun resetCalibrationToDefaults() {
        val o = getOrientationKey()
        prefs.edit()
            .remove("${activeProfile.name}_${o}_buy_x")
            .remove("${activeProfile.name}_${o}_buy_y")
            .remove("${activeProfile.name}_${o}_sell_x")
            .remove("${activeProfile.name}_${o}_sell_y")
            .apply()
    }

    /**
     * Muestra en pantalla los pines interactivos compactos arrastrables para calibrar los botones.
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

        val density = context.resources.displayMetrics.density
        val pinSize = (38 * density).toInt()

        val buyPin = createPinView("▲\nSUBE", Color.parseColor("#22c55e"))
        val sellPin = createPinView("▼\nBAJA", Color.parseColor("#ef4444"))

        val buyParams = FrameLayout.LayoutParams(pinSize, pinSize).apply {
            leftMargin = (buyX - pinSize / 2).toInt().coerceAtLeast(0)
            topMargin = (buyY - pinSize / 2).toInt().coerceAtLeast(0)
        }
        val sellParams = FrameLayout.LayoutParams(pinSize, pinSize).apply {
            leftMargin = (sellX - pinSize / 2).toInt().coerceAtLeast(0)
            topMargin = (sellY - pinSize / 2).toInt().coerceAtLeast(0)
        }

        setupDrag(buyPin, buyParams, root) { x, y ->
            saveBuyCoordinates(x, y)
        }
        setupDrag(sellPin, sellParams, root) { x, y ->
            saveSellCoordinates(x, y)
        }

        root.addView(buyPin, buyParams)
        root.addView(sellPin, sellParams)

        val oStr = if (getOrientationKey() == "land") "Horizontal" else "Vertical"
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F00a0a0f"))
            setPadding(20, 12, 20, 12)
        }
        val txtInfo = TextView(context).apply {
            text = "🎯 Calibrar [$oStr]: Arrastra cada mira sobre el botón de tu broker:"
            setTextColor(Color.WHITE)
            textSize = 11f
        }
        val btnReset = Button(context).apply {
            text = "↺ RESET"
            textSize = 10f
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                resetCalibrationToDefaults()
                dismissCalibration()
                startInteractiveCalibration(onFinished)
            }
        }
        val btnSave = Button(context).apply {
            text = "✓ LISTO"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#7c3aed"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                dismissCalibration()
                onFinished()
            }
        }
        banner.addView(txtInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val resetParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 12
        }
        banner.addView(btnReset, resetParams)
        banner.addView(btnSave)

        val bannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = (30 * density).toInt()
        }
        root.addView(banner, bannerParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager.addView(root, windowParams)
            calibrationContainerView = root
        } catch (e: Exception) {
            android.util.Log.e("CalibrationManager", "Error inflando calibración", e)
        }
    }

    private fun createPinView(label: String, color: Int): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(190, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(3, Color.WHITE)
        }
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 9f
            gravity = Gravity.CENTER
            background = bg
            elevation = 25f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: View, params: FrameLayout.LayoutParams, root: FrameLayout, onPositionSaved: (Float, Float) -> Unit) {
        var startX = 0f
        var startY = 0f
        var initialMarginX = 0
        var initialMarginY = 0

        fun saveAbsolutePosition() {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val screenX = loc[0] + view.width / 2f
            val screenY = loc[1] + view.height / 2f
            onPositionSaved(screenX, screenY)
        }

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
                    val maxW = (root.width - v.width).coerceAtLeast(0)
                    val maxH = (root.height - v.height).coerceAtLeast(0)
                    params.leftMargin = if (maxW > 0) (initialMarginX + dx).coerceIn(0, maxW) else (initialMarginX + dx).coerceAtLeast(0)
                    params.topMargin = if (maxH > 0) (initialMarginY + dy).coerceIn(0, maxH) else (initialMarginY + dy).coerceAtLeast(0)
                    v.layoutParams = params
                    saveAbsolutePosition()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    saveAbsolutePosition()
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
