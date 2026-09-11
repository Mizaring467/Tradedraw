package com.example.tradedraw

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Servicio maestro con submenús inteligentes que detectan los límites de la pantalla.
 * Implementa el borrado prioritario de elementos seleccionados.
 */
class OverlayService : Service() {

    companion object {
        private const val ACTION_STOP = "com.example.tradedraw.STOP"
        @Volatile
        var instance: OverlayService? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var canvasView: View
    internal lateinit var drawingView: CustomDrawingView
    private lateinit var canvasParams: WindowManager.LayoutParams
    
    private lateinit var menuView: View
    private lateinit var categoryContainer: View
    private lateinit var menuParams: WindowManager.LayoutParams

    private lateinit var submenuWindowView: View
    private lateinit var submenuContainer: LinearLayout
    private lateinit var submenuParams: WindowManager.LayoutParams
    
    private lateinit var templateManager: TemplateManager

    private var screenCaptureManager: ScreenCaptureManager? = null
    var httpBridge: TradeDrawHttpBridge? = null
        private set
    lateinit var riskManager: RiskManager
        private set
    lateinit var calibrationManager: CalibrationManager
        private set
    lateinit var tradingEngine: TradingEngine
        private set

    private var hudView: View? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var isHudVisible = false
    private var hudAlpha: Float = 0.70f
    private var isHudCollapsed: Boolean = true
    private var agentChatOverlay: AgentChatOverlay? = null

    fun openAgentChat() {
        mainHandler.post {
            if (agentChatOverlay == null) {
                agentChatOverlay = AgentChatOverlay(this, tradingEngine)
            }
            agentChatOverlay?.show()
        }
    }

    private var isMenuExpanded = false
    private var isDrawingMode = false
    private var currentActiveCategory: Int = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    private val accessibilityCheckRunnable = object : Runnable {
        override fun run() {
            if (AutoTradeAccessibilityService.instance == null) {
                Log.w("TradeDraw", "AutoTradeAccessibilityService desconectado. Notificando HUD.")
                mainHandler.post { updateHUDView() }
            }
            mainHandler.postDelayed(this, 10000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val overlayCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra("action")?.uppercase() ?: ""
            Log.d("TradeDraw", "ADB overlay command: $action")
            when (action) {
                "AUTO" -> {
                    tradingEngine.autonomousSubMode = AutonomousSubMode.CONSERVATIVE
                    setTradingMode(AutoTradeMode.AUTONOMOUS)
                }
                "YOLO" -> {
                    tradingEngine.autonomousSubMode = AutonomousSubMode.YOLO
                    setTradingMode(AutoTradeMode.AUTONOMOUS)
                }
                "SEMI" -> setTradingMode(AutoTradeMode.SEMIAUTOMATIC)
                "STOP" -> setTradingMode(AutoTradeMode.DISABLED)
                "DEB" -> showDebugDialog()
                "HUD" -> toggleHUDVisibility()
                "TEST" -> {
                    val frame = screenCaptureManager?.latestFrame
                    val visionCoords = if (frame != null) tradingEngine.visionAnalyzer.findBrokerButtonCoordinates(frame, true) else null
                    val (bx, by) = visionCoords ?: calibrationManager.getBuyCoordinates()
                    Log.d("TradeDraw", "TEST SUBE hacia ($bx, $by) [Visión=${visionCoords != null}]")
                    AutoTradeAccessibilityService.instance?.performClickAt(bx, by)
                }
                "TEST_SELL" -> {
                    val frame = screenCaptureManager?.latestFrame
                    val visionCoords = if (frame != null) tradingEngine.visionAnalyzer.findBrokerButtonCoordinates(frame, false) else null
                    val (sx, sy) = visionCoords ?: calibrationManager.getSellCoordinates()
                    Log.d("TradeDraw", "TEST BAJA hacia ($sx, $sy) [Visión=${visionCoords != null}]")
                    AutoTradeAccessibilityService.instance?.performClickAt(sx, sy)
                }
                "CLEAR_BOT" -> drawingView.clearBotShapes()
                "RESET_STATS" -> {
                    riskManager.resetStats()
                    updateHUDView()
                    Toast.makeText(this@OverlayService, "Contador W/L reiniciado", Toast.LENGTH_SHORT).show()
                }
                "SYNC_STATS" -> {
                    val w = intent?.getIntExtra("wins", 0) ?: 0
                    val l = intent?.getIntExtra("losses", 0) ?: 0
                    riskManager.setStats(w, l)
                    updateHUDView()
                    Toast.makeText(this@OverlayService, "Sincronizado: W:$w | L:$l", Toast.LENGTH_SHORT).show()
                }
                "UNLOCK_LINES" -> {
                    tradingEngine.unlockAllLines()
                    Toast.makeText(this@OverlayService, "Líneas desbloqueadas → IA recalculando", Toast.LENGTH_SHORT).show()
                }
                "STRATEGY" -> {
                    val stratName = intent?.getStringExtra("name")?.uppercase() ?: ""
                    try {
                        val st = AutoTradeStrategy.valueOf(stratName)
                        tradingEngine.strategy = st
                        updateHUDView()
                        Toast.makeText(this@OverlayService, "Estrategia cambiada: ${st.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("TradeDraw", "Estrategia desconocida: $stratName", e)
                    }
                }
                "OPEN_CHAT" -> openAgentChat()
                "RECENTER" -> {
                    tradingEngine.agentController.recenterChart("Comando Remoto")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startTradeDrawForeground()

        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("EXTRA_MEDIA_PROJECTION_DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("EXTRA_MEDIA_PROJECTION_DATA") as Intent?
        }
        if (dataIntent != null) {
            try {
                screenCaptureManager?.destroy()
            } catch (e: Exception) {
                Log.e("TradeDraw", "Error reciclando ScreenCaptureManager previo", e)
            }
            val scm = ScreenCaptureManager(this, dataIntent)
            screenCaptureManager = scm
            scm.startCapture { bitmap ->
                httpBridge?.latestFrame = bitmap
                tradingEngine.onNewFrame(bitmap)
            }
            Log.d("TradeDraw", "ScreenCaptureManager reiniciado con nuevo token y procesando frames")
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        CrashLogger.showPending(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        templateManager = TemplateManager(this)

        startTradeDrawForeground()
        setupCanvasWindow()

        riskManager = RiskManager(this)
        riskManager.resetSession() // Sesión limpia en 0W / 0L para cada nuevo inicio de TradeDraw
        calibrationManager = CalibrationManager(this)
        tradingEngine = TradingEngine(this, drawingView, riskManager, calibrationManager)

        // Cargar estrategia guardada previamente (por defecto AUTO_ADAPTIVE)
        val savedStrat = getSharedPreferences("TradeDraw_Config", Context.MODE_PRIVATE)
            .getString("saved_strategy", AutoTradeStrategy.AUTO_ADAPTIVE.name)
        tradingEngine.strategy = try {
            AutoTradeStrategy.valueOf(savedStrat ?: AutoTradeStrategy.AUTO_ADAPTIVE.name)
        } catch (e: Exception) {
            AutoTradeStrategy.AUTO_ADAPTIVE
        }

        AutoTradeAccessibilityService.onGestureClickListener = { x, y ->
            drawingView.triggerClickAnimation(x, y)
        }

        tradingEngine.onSignalListener = { _: TradeAction, _: String -> updateHUDView() }
        tradingEngine.onTradeExecutedListener = { _: TradeAction, _: Boolean -> updateHUDView() }
        tradingEngine.onFrameProcessedListener = { _ -> updateHUDView() }

        setupMenuWindow()
        setupHUDWindow()
        bringMenuToFront()
        mainHandler.post(accessibilityCheckRunnable)

        // Iniciar Micro-Servidor HTTP local de ultra-baja latencia para supervisión y pruebas
        httpBridge = TradeDrawHttpBridge(this, 8080).apply { start() }

        val cmdFilter = IntentFilter("com.example.tradedraw.CMD")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(overlayCommandReceiver, cmdFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(overlayCommandReceiver, cmdFilter)
            }
        } catch (e: Exception) {
            Log.e("TradeDraw", "Error registrando overlayCommandReceiver", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::drawingView.isInitialized) {
            drawingView.clearBotShapes()
        }
        screenCaptureManager?.refreshVirtualDisplay()
        mainHandler.postDelayed({
            // 1. Redimensionar el lienzo flotante para cubrir la pantalla completa en la nueva orientación
            if (::canvasParams.isInitialized && ::canvasView.isInitialized) {
                canvasParams.width = WindowManager.LayoutParams.MATCH_PARENT
                canvasParams.height = WindowManager.LayoutParams.MATCH_PARENT
                windowManager.updateViewLayout(canvasView, canvasParams)
            }

            // 2. Reposicionar menú dentro de los nuevos bordes de pantalla
            if (::menuParams.isInitialized && ::menuView.isInitialized) {
                val metrics = resources.displayMetrics
                if (menuParams.x > metrics.widthPixels - 100) menuParams.x = (metrics.widthPixels - 200).coerceAtLeast(0)
                if (menuParams.y > metrics.heightPixels - 100) menuParams.y = (metrics.heightPixels - 200).coerceAtLeast(0)
                windowManager.updateViewLayout(menuView, menuParams)
            }

            // 3. Reposicionar HUD dentro de los nuevos bordes
            hudParams?.let { params ->
                val view = hudView ?: return@let
                val metrics = resources.displayMetrics
                if (params.x > metrics.widthPixels - 100) params.x = 40
                if (params.y > metrics.heightPixels - 100) params.y = 120
                windowManager.updateViewLayout(view, params)
            }
        }, 350)
    }

    private fun setupCanvasWindow() {
        canvasView = LayoutInflater.from(this).inflate(R.layout.overlay_canvas, null)
        drawingView = canvasView.findViewById(R.id.custom_drawing_view)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        )
        // Iniciar en modo NAVEGACIÓN: el lienzo no captura toques, se usa la app subyacente.
        if (!isDrawingMode) canvasParams.flags = canvasParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.addView(canvasView, canvasParams)

        val saved = templateManager.loadLocal("AUTO")
        if (saved.isNotEmpty()) drawingView.setShapes(saved)
        drawingView.onShapesChange = { templateManager.saveLocal("AUTO", drawingView.getShapes()) }
    }

    private fun setupMenuWindow() {
        menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
        categoryContainer = menuView.findViewById(R.id.category_scroll)
        val btnMainBubble = menuView.findViewById<ImageView>(R.id.btn_main_bubble)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = 600; menuParams.y = 300

        // Ventana SEPARADA para el submenú: así el menú principal nunca se mueve.
        submenuWindowView = LayoutInflater.from(this).inflate(R.layout.overlay_submenu, null)
        submenuContainer = submenuWindowView.findViewById(R.id.submenu_container)
        submenuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        )
        submenuParams.gravity = Gravity.TOP or Gravity.START
        submenuWindowView.visibility = View.GONE

        // Listeners de Categorías
        menuView.findViewById<View>(R.id.btn_cat_view).setOnClickListener { handleCategoryClick(0) { showViewSubmenu() } }
        menuView.findViewById<View>(R.id.btn_cat_edit).setOnClickListener { handleCategoryClick(1) { showEditSubmenu() } }
        menuView.findViewById<View>(R.id.btn_cat_lines).setOnClickListener { handleCategoryClick(2) { showLinesSubmenu() } }
        menuView.findViewById<View>(R.id.btn_cat_shapes).setOnClickListener { handleCategoryClick(3) { showShapesSubmenu() } }
        menuView.findViewById<View>(R.id.btn_cat_files).setOnClickListener { handleCategoryClick(4) { showFilesSubmenu() } }
        menuView.findViewById<View>(R.id.btn_cat_ai)?.setOnClickListener { handleCategoryClick(5) { showAISubmenu() } }

        // ACCIONES DIRECTAS
        menuView.findViewById<View>(R.id.btn_undo_direct).setOnClickListener { drawingView.undo() }
        menuView.findViewById<View>(R.id.btn_redo_direct).setOnClickListener { drawingView.redo() }
        menuView.findViewById<View>(R.id.btn_color_direct).setOnClickListener { showColorPicker() }
        menuView.findViewById<View>(R.id.btn_clear_direct).setOnClickListener { 
            // NUEVO: Borrar seleccionado prioritario
            drawingView.deleteSelectedOrLast()
        }
        menuView.findViewById<View>(R.id.btn_cerrar_global).setOnClickListener { stopSelf() }

        btnMainBubble.setOnClickListener { toggleMenu() }
        setupMenuMovement(btnMainBubble)
        windowManager.addView(menuView, menuParams)
        windowManager.addView(submenuWindowView, submenuParams)
    }

    /**
     * Posiciona la ventana del submenú al lado del menú principal.
     */
    private fun positionSubmenuWindow() {
        if (!::submenuWindowView.isInitialized || !::submenuParams.isInitialized) return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        submenuWindowView.post {
            try {
                val w = submenuWindowView.width
                val h = submenuWindowView.height
                if (w > 0 && h > 0) {
                    val bubbleWidth = menuView.width
                    if (menuParams.x + bubbleWidth + w <= screenW) {
                        // Hay espacio a la derecha -> submenú a la derecha
                        submenuParams.x = menuParams.x + bubbleWidth
                    } else {
                        // Si no cabe a la derecha -> submenú a la izquierda
                        submenuParams.x = (menuParams.x - w).coerceAtLeast(0)
                    }
                    submenuParams.y = menuParams.y.coerceIn(0, (screenH - h).coerceAtLeast(0))
                    windowManager.updateViewLayout(submenuWindowView, submenuParams)
                } else {
                    mainHandler.postDelayed({ positionSubmenuWindow() }, 60)
                }
            } catch (e: Exception) {
                android.util.Log.e("TradeDraw", "positionSubmenuWindow fallo", e)
            }
        }
    }

    private fun handleCategoryClick(catId: Int, showSubmenuAction: () -> Unit) {
        if (currentActiveCategory == catId) {
            hideSubmenu()
        } else {
            showSubmenuAction()
            currentActiveCategory = catId
            showSubmenu()
        }
    }

    private fun showSubmenu() {
        if (!::submenuWindowView.isInitialized) return
        submenuWindowView.visibility = View.VISIBLE
        positionSubmenuWindow()
    }

    private fun hideSubmenu() {
        if (!::submenuWindowView.isInitialized) return
        submenuWindowView.visibility = View.GONE
        currentActiveCategory = -1
    }

    /**
     * Re-posiciona la ventana del menú para que quede completamente dentro de la pantalla.
     * Necesario porque al mostrar el submenú la ventana crece y, si la burbuja está
     * cerca de un borde, puede desbordarse fuera de la pantalla y volverse inalcanzable.
     */
    private fun keepMenuOnScreen(attempt: Int = 0) {
        if (!::menuView.isInitialized || !::menuParams.isInitialized) return
        if (attempt > 5) return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        menuView.post {
            try {
                val w = menuView.width
                val h = menuView.height
                if (w > 0 && h > 0) {
                    menuParams.x = menuParams.x.coerceIn(0, (screenW - w).coerceAtLeast(0))
                    menuParams.y = menuParams.y.coerceIn(0, (screenH - h).coerceAtLeast(0))
                    windowManager.updateViewLayout(menuView, menuParams)
                } else {
                    // El layout aún no midió la ventana; reintentar en un momento.
                    mainHandler.postDelayed({ keepMenuOnScreen(attempt + 1) }, 60)
                }
            } catch (e: Exception) {
                android.util.Log.e("TradeDraw", "keepMenuOnScreen fallo", e)
            }
        }
    }

    private fun showViewSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(if (drawingView.isCanvasVisible()) R.drawable.ic_visibility else R.drawable.ic_visibility_off, "VISTA", Color.parseColor("#38bdf8")) {
            drawingView.toggleCanvasVisibility(); showViewSubmenu()
        }
        addItemToSubmenu(if (isDrawingMode) R.drawable.ic_lock_closed else R.drawable.ic_lock_open, "LOCK", Color.parseColor("#facc15")) {
            toggleLock(); showViewSubmenu()
        }
    }

    private fun showEditSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(R.drawable.ic_tool_brush, "PINCEL", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.FREE_BRUSH) }
        addItemToSubmenu(R.drawable.ic_tool_pointer, "ELEGIR", Color.parseColor("#38bdf8")) { selectTool(TradingTool.SELECT_TOUCH) }
        addItemToSubmenu(R.drawable.ic_measure, "MEDIR", Color.parseColor("#facc15")) { selectTool(TradingTool.MEASURE) }
        addItemToSubmenu(R.drawable.ic_tool_text, "TEXTO", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.TEXT_LABEL) }
        addItemToSubmenu(R.drawable.ic_tool_eraser, "BORRADOR", Color.parseColor("#f43f5e")) { selectTool(TradingTool.ERASER_TOUCH) }
        addItemToSubmenu(R.drawable.ic_trash_delete, "LIMPIAR", Color.parseColor("#ef4444")) {
            confirmClearAll()
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("¿Limpiar Lienzo?")
            .setMessage("Borrar todos los trazos permanentemente.")
            .setPositiveButton("Sí") { _, _ -> drawingView.clearCanvas() }
            .setNegativeButton("No", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showLinesSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(R.drawable.ic_trend_line, "LÍNEA", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.TREND_LINE) }
        addItemToSubmenu(R.drawable.ic_ray, "RAYO", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.RAY) }
        addItemToSubmenu(R.drawable.ic_horizontal_line, "HORIZ", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.HORIZONTAL_LINE) }
        addItemToSubmenu(R.drawable.ic_vertical_line, "VERT", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.VERTICAL_LINE) }
        addItemToSubmenu(R.drawable.ic_channel, "CANAL", Color.parseColor("#38bdf8")) { selectTool(TradingTool.CHANNEL) }
        addItemToSubmenu(R.drawable.ic_tool_support, "SOPORTE", Color.parseColor("#ef4444")) { selectTool(TradingTool.SUPPORT_LINE) }
        addItemToSubmenu(R.drawable.ic_tool_resistance, "RESIST", Color.parseColor("#22c55e")) { selectTool(TradingTool.RESISTANCE_LINE) }
        addItemToSubmenu(R.drawable.ic_tool_fibonacci, "FIBO", Color.parseColor("#facc15")) { selectTool(TradingTool.FIB_RETRACEMENT) }
    }

    private fun showShapesSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(R.drawable.ic_tool_rectangle, "ZONA", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.RECTANGLE) }
        addItemToSubmenu(R.drawable.ic_zone, "Z-FILL", Color.parseColor("#38bdf8")) { selectTool(TradingTool.ZONE) }
        addItemToSubmenu(R.drawable.ic_circle, "CIRCULO", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.CIRCLE) }
        addItemToSubmenu(R.drawable.ic_triangle, "TRIANG", Color.parseColor("#f1f5f9")) { selectTool(TradingTool.TRIANGLE) }
        addItemToSubmenu(R.drawable.ic_tool_long, "LONG", Color.parseColor("#22c55e")) { selectTool(TradingTool.LONG_POSITION) }
        addItemToSubmenu(R.drawable.ic_tool_short, "SHORT", Color.parseColor("#ef4444")) { selectTool(TradingTool.SHORT_POSITION) }
    }

    private fun showFilesSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(R.drawable.ic_template_save, "GUARDAR", Color.parseColor("#10b981")) { saveTemplate() }
        addItemToSubmenu(R.drawable.ic_template_load, "CARGAR", Color.parseColor("#38bdf8")) { loadTemplate() }
        addItemToSubmenu(R.drawable.ic_template_share, "EXPORTAR", Color.parseColor("#a855f7")) { shareTemplate() }
    }

    private fun showAISubmenu() {
        prepareSubmenu()
        val currentMode = tradingEngine.mode
        val subMode = tradingEngine.autonomousSubMode
        val (modeIcon, modeText, modeColor) = when (currentMode) {
            AutoTradeMode.AUTONOMOUS -> {
                if (subMode == AutonomousSubMode.YOLO) {
                    Triple(R.drawable.ic_ai_chip, "AUTO: YOLO 🚀", Color.parseColor("#ec4899"))
                } else {
                    Triple(R.drawable.ic_ai_chip, "AUTO: CONSERVADOR", Color.GREEN)
                }
            }
            AutoTradeMode.SEMIAUTOMATIC -> Triple(R.drawable.ic_ai_chip, "SEMIAUTO", Color.YELLOW)
            AutoTradeMode.DISABLED -> Triple(R.drawable.ic_ai_chip, "MODO: OFF", Color.WHITE)
        }

        addItemToSubmenu(modeIcon, modeText, modeColor) {
            showModeDialog()
        }

        addItemToSubmenu(R.drawable.ic_ai_strategy, "ESTRAT", Color.CYAN) {
            showStrategyDialog()
        }

        addItemToSubmenu(R.drawable.ic_ai_test_click, "TEST CLIC", Color.parseColor("#38bdf8")) {
            tradingEngine.testAccessibilityClicks()
        }

        addItemToSubmenu(R.drawable.ic_ai_calibrate, "CALIBRAR", Color.MAGENTA) {
            showCalibrationDialog()
        }

        addItemToSubmenu(R.drawable.ic_ai_risk, "RIESGO", Color.parseColor("#fb923c")) {
            showRiskConfigDialog()
        }

        val aiColor = if (tradingEngine.aiClient.isEnabled) Color.parseColor("#a855f7") else Color.GRAY
        addItemToSubmenu(R.drawable.ic_ai_chip, if (tradingEngine.aiClient.isEnabled) "IA: ON" else "IA: OFF", aiColor) {
            showOmniRouteConfigDialog()
        }

        val debugColor = if (tradingEngine.debugModeEnabled) Color.CYAN else Color.GRAY
        addItemToSubmenu(R.drawable.ic_ai_debug, if (tradingEngine.debugModeEnabled) "DEBUG: ON" else "DEBUG: OFF", debugColor) {
            showDebugDialog()
        }

        val hudIcon = if (isHudVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        addItemToSubmenu(hudIcon, if (isHudVisible) "HUD: ON" else "HUD: OFF", Color.parseColor("#38bdf8")) {
            toggleHUDVisibility()
            showAISubmenu()
        }

        addItemToSubmenu(R.drawable.ic_visibility, "TRANSP ${(hudAlpha * 100).toInt()}%", Color.parseColor("#38bdf8")) {
            showHUDOpacityDialog()
        }

        addItemToSubmenu(R.drawable.ic_lock_open, "RECALCULAR IA", Color.parseColor("#34d399")) {
            tradingEngine.unlockAllLines()
            Toast.makeText(this, "Líneas desbloqueadas → IA recalculando niveles", Toast.LENGTH_SHORT).show()
        }

        addItemToSubmenu(R.drawable.ic_ai_chip, "✥ ACOMODAR GRÁFICO", Color.parseColor("#c084fc")) {
            tradingEngine.agentController.recenterChart("Menú IA")
        }
    }

    private fun showCalibrationDialog() {
        val options = arrayOf(
            "1. 🎯 Arrastrar Pines sobre Botones (Interactivo)",
            "2. 🏢 Cambiar Perfil de Broker [Actual: ${calibrationManager.activeProfile.name}]",
            "3. 📍 Ver Coordenadas Guardadas"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Calibración de Botones")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> calibrationManager.startInteractiveCalibration {
                        Toast.makeText(this, "Calibración guardada", Toast.LENGTH_SHORT).show()
                        showAISubmenu()
                    }
                    1 -> showBrokerProfilePicker()
                    2 -> {
                        val (bx, by) = calibrationManager.getBuyCoordinates()
                        val (sx, sy) = calibrationManager.getSellCoordinates()
                        Toast.makeText(this, "Sube: (${bx.toInt()}, ${by.toInt()})\nBaja: (${sx.toInt()}, ${sy.toInt()})", Toast.LENGTH_LONG).show()
                        showCalibrationDialog()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showBrokerProfilePicker() {
        val profiles = BrokerProfile.values().map { it.name }.toTypedArray()
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Seleccionar Broker")
            .setItems(profiles) { _, which ->
                calibrationManager.activeProfile = BrokerProfile.values()[which]
                Toast.makeText(this, "Broker activo: ${calibrationManager.activeProfile.name}", Toast.LENGTH_SHORT).show()
                showCalibrationDialog()
            }
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showOmniRouteConfigDialog() {
        val ai = tradingEngine.aiClient
        val keyDisplay = if (ai.apiKey.isNotBlank()) "••••" + ai.apiKey.takeLast(4) else "Sin configurar"
        val options = arrayOf(
            "1. ⚡ IA Remota: ${if (ai.isEnabled) "ACTIVADA [ON]" else "DESACTIVADA [OFF]"}",
            "2. 🚀 Presets Rápidos (OmniRoute / B.AI)",
            "3. 🌐 Endpoint Base: ${ai.baseUrl}",
            "4. 🔑 API Key: $keyDisplay",
            "5. 🧠 Modelo: ${ai.model}",
            "6. 🎯 Umbral Confianza: ${(ai.confidenceThreshold * 100).toInt()}%",
            "7. 🧪 Probar Conexión con OmniRoute"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Configurar IA (OmniRoute / OpenAI)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        ai.isEnabled = !ai.isEnabled
                        Toast.makeText(this, "IA Remota: ${if (ai.isEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                        showOmniRouteConfigDialog()
                        updateHUDView()
                    }
                    1 -> showServerPresetsDialog()
                    2 -> promptTextInput("Endpoint Base URL", ai.baseUrl) { newUrl ->
                        ai.baseUrl = newUrl
                        Toast.makeText(this, "URL guardada", Toast.LENGTH_SHORT).show()
                        showOmniRouteConfigDialog()
                    }
                    3 -> promptTextInput("API Key de OmniRoute", ai.apiKey) { newKey ->
                        ai.apiKey = newKey
                        Toast.makeText(this, "API Key guardada", Toast.LENGTH_SHORT).show()
                        showOmniRouteConfigDialog()
                    }
                    4 -> showModelPicker()
                    5 -> promptNumberAdjustment("Umbral de Confianza (%)", (ai.confidenceThreshold * 100).toInt(), 30, 95) { pct ->
                        ai.confidenceThreshold = pct / 100f
                        showOmniRouteConfigDialog()
                        updateHUDView()
                    }
                    6 -> {
                        Toast.makeText(this, "Probando conexión con OmniRoute...", Toast.LENGTH_SHORT).show()
                        ai.testConnection { success, msg ->
                            Toast.makeText(this, if (success) "✓ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showServerPresetsDialog() {
        val ai = tradingEngine.aiClient
        val presets = arrayOf(
            "📱 OmniRoute Termux (Móvil Local)\nhttp://localhost:20128/v1 · auto/best-vision ⭐",
            "📶 OmniRoute Termux (Wi-Fi Celular)\nhttp://192.168.1.185:20128/v1 · auto/best-vision",
            "💻 OmniRoute Wi-Fi PC (Red Local)\nhttp://192.168.1.245:20128/v1 · Gemini 3.7",
            "⚡ B.AI Remoto (Cloud)\nhttps://api.b.ai/v1 · DeepSeek Vision"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Seleccionar Proveedor / Preset")
            .setItems(presets) { _, which ->
                when (which) {
                    0 -> {
                        ai.baseUrl = "http://localhost:20128/v1"
                        ai.apiKey = "sk-5f238e76072d7926-95c3e9-7cd7ecb1"
                        ai.model = "auto/best-vision"
                        Toast.makeText(this, "Preset: OmniRoute Termux Móvil activado", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        ai.baseUrl = "http://192.168.1.185:20128/v1"
                        ai.apiKey = "sk-5f238e76072d7926-95c3e9-7cd7ecb1"
                        ai.model = "auto/best-vision"
                        Toast.makeText(this, "Preset: OmniRoute Termux Wi-Fi activado", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        ai.baseUrl = "http://192.168.1.245:20128/v1"
                        ai.apiKey = "sk-5f238e76072d7926-95c3e9-7cd7ecb1"
                        ai.model = "antigravity/gemini-3.7-flash-high"
                        Toast.makeText(this, "Preset: OmniRoute Wi-Fi PC activado", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        ai.baseUrl = "https://api.b.ai/v1"
                        ai.apiKey = "sk-9lt4tdgldm7tt48ylqkf693nouje0spi"
                        ai.model = "deepseek-v4-flash-vision-exp"
                        Toast.makeText(this, "Preset: B.AI Cloud activado", Toast.LENGTH_SHORT).show()
                    }
                }
                showOmniRouteConfigDialog()
            }
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showModelPicker() {
        val ai = tradingEngine.aiClient
        ModelPickerDialog(this, ai.model) { chosenModel ->
            ai.model = chosenModel
            Toast.makeText(this, "Modelo seleccionado: ${ai.model}", Toast.LENGTH_SHORT).show()
            showOmniRouteConfigDialog()
        }.show()
    }

    private fun promptTextInput(title: String, currentValue: String, onTextSaved: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(currentValue)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                onTextSaved(input.text.toString().trim())
            }
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showModeDialog() {
        val modes = arrayOf(
            "🟢 Modo Autónomo (Elegir submodo...)",
            "🟡 Modo Semiautomático (Bot te avisa y dibuja)",
            "⚪ Desactivado (Manual)"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Modo de Trading")
            .setItems(modes) { _, which ->
                when (which) {
                    0 -> showAutonomousSubModeDialog()
                    1 -> setTradingMode(AutoTradeMode.SEMIAUTOMATIC)
                    2 -> setTradingMode(AutoTradeMode.DISABLED)
                }
                showAISubmenu()
            }
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showAutonomousSubModeDialog() {
        val subModes = arrayOf(
            "🛡️ Submodo Conservador (Gestión de Riesgo normal, Stop Loss & Cooldowns)",
            "🚀 Submodo YOLO (Sin Stop Loss ni límites de pérdidas, opera continuo)"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Submodo Autónomo")
            .setItems(subModes) { _, which ->
                when (which) {
                    0 -> {
                        tradingEngine.autonomousSubMode = AutonomousSubMode.CONSERVATIVE
                        setTradingMode(AutoTradeMode.AUTONOMOUS)
                        Toast.makeText(this, "🤖 Modo Autónomo: Conservador", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        tradingEngine.autonomousSubMode = AutonomousSubMode.YOLO
                        setTradingMode(AutoTradeMode.AUTONOMOUS)
                        Toast.makeText(this, "🚀 Modo Autónomo: YOLO", Toast.LENGTH_SHORT).show()
                    }
                }
                showAISubmenu()
            }
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun setTradingMode(newMode: AutoTradeMode) {
        tradingEngine.mode = newMode
        if (newMode != AutoTradeMode.DISABLED) {
            if (screenCaptureManager != null) {
                screenCaptureManager?.startCapture { bitmap ->
                    tradingEngine.onNewFrame(bitmap)
                }
                Toast.makeText(this, "Modo: ${newMode.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sin permisos de captura. Reinicia TradeDraw.", Toast.LENGTH_LONG).show()
            }
        } else {
            tradingEngine.stop()
            Toast.makeText(this, "Trading detenido", Toast.LENGTH_SHORT).show()
        }
        updateHUDView()
    }

    private fun showStrategyDialog() {
        val strategies = arrayOf(
            "🤖 1. AUTO: Modo Automático Total (Multi-Estrategia)",
            "🔥 2. MT: Combo Acción del Precio",
            "⚡ 3. MT: Mechas de Rechazo en S/R",
            "🎯 4. MT: Choque de Máximos/Mínimos (Pullback)",
            "📊 5. MT: Agotamiento de 3 Velas",
            "🌊 6. MT: Vela Envolvente en S/R",
            "🪤 7. MT: Falso Rompimiento (Trampa Institucional)",
            "8. Soportes y Resistencias (Clásico)",
            "9. Patrón de Velas y Martillo",
            "10. Seguidor de Tendencia",
            "11. Doble Confirmación"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Seleccionar Estrategia (Master Traders)")
            .setItems(strategies) { _, which ->
                tradingEngine.strategy = when (which) {
                    0 -> AutoTradeStrategy.AUTO_ADAPTIVE
                    1 -> AutoTradeStrategy.MT_MASTER_COMBO
                    2 -> AutoTradeStrategy.MT_REJECTION
                    3 -> AutoTradeStrategy.MT_CHOQUE_PULLBACK
                    4 -> AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO
                    5 -> AutoTradeStrategy.MT_ENGULFING_SR
                    6 -> AutoTradeStrategy.MT_FALSE_BREAKOUT
                    7 -> AutoTradeStrategy.SUPPORT_RESISTANCE
                    8 -> AutoTradeStrategy.CANDLE_PATTERNS
                    9 -> AutoTradeStrategy.TREND_FOLLOWING
                    else -> AutoTradeStrategy.COMBINED
                }
                getSharedPreferences("TradeDraw_Config", Context.MODE_PRIVATE)
                    .edit()
                    .putString("saved_strategy", tradingEngine.strategy.name)
                    .apply()

                Toast.makeText(this, "Estrategia: ${tradingEngine.strategy.name}", Toast.LENGTH_SHORT).show()
                updateHUDView()
                showAISubmenu()
            }
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showDebugDialog() {
        val summary = DebugVisualizer.lastSummary
        val isSaving = tradingEngine.debugModeEnabled
        val lastPath = DebugVisualizer.lastSavedPath ?: "Ninguno guardado aún"

        val message = "$summary\n\n📁 Almacenamiento:\n$lastPath"
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("🛠️ Diagnóstico Visual (Debug)")
            .setMessage(message)
            .setPositiveButton(if (isSaving) "Desactivar Guardado" else "Activar Guardar Frames") { _, _ ->
                tradingEngine.debugModeEnabled = !tradingEngine.debugModeEnabled
                Toast.makeText(this, if (tradingEngine.debugModeEnabled) "Guardado de frames activado" else "Guardado desactivado", Toast.LENGTH_SHORT).show()
                showAISubmenu()
            }
            .setNeutralButton("Cerrar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun showRiskConfigDialog() {
        val items = arrayOf(
            "Stop Loss (Racha pérdidas): ${riskManager.stopLossStreak}",
            "Take Profit (Ganancias objetivo): ${riskManager.takeProfitWins}",
            "Cooldown entre trades: ${riskManager.cooldownSeconds}s",
            "Martingala en Demo: ${if (riskManager.martingaleEnabled) "ACTIVA (${riskManager.martingaleMultiplier}x)" else "DESACTIVADA"}",
            "Reiniciar Estadísticas de Sesión"
        )
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Gestión de Riesgo")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> promptNumberAdjustment("Stop Loss (Derrotas consecutivas)", riskManager.stopLossStreak, 1, 10) {
                        riskManager.stopLossStreak = it
                        showRiskConfigDialog()
                    }
                    1 -> promptNumberAdjustment("Take Profit (Victorias objetivo)", riskManager.takeProfitWins, 1, 20) {
                        riskManager.takeProfitWins = it
                        showRiskConfigDialog()
                    }
                    2 -> promptNumberAdjustment("Cooldown en segundos", riskManager.cooldownSeconds, 5, 300) {
                        riskManager.cooldownSeconds = it
                        showRiskConfigDialog()
                    }
                    3 -> {
                        riskManager.martingaleEnabled = !riskManager.martingaleEnabled
                        Toast.makeText(this, "Martingala: ${if (riskManager.martingaleEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                        showRiskConfigDialog()
                    }
                    4 -> {
                        riskManager.resetSession()
                        updateHUDView()
                        Toast.makeText(this, "Sesión reiniciada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun promptNumberAdjustment(title: String, current: Int, min: Int, max: Int, onValueChosen: (Int) -> Unit) {
        var value = current
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }
        val btnMinus = Button(this).apply { text = "-"; setOnClickListener { if (value > min) value--; layout.findViewById<TextView>(1001).text = "$value" } }
        val txtVal = TextView(this).apply { id = 1001; text = "$value"; textSize = 22f; setTextColor(Color.WHITE); setPadding(32, 0, 32, 0) }
        val btnPlus = Button(this).apply { text = "+"; setOnClickListener { if (value < max) value++; layout.findViewById<TextView>(1001).text = "$value" } }
        layout.addView(btnMinus)
        layout.addView(txtVal)
        layout.addView(btnPlus)

        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ -> onValueChosen(value) }
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun setHUDOpacity(alpha: Float) {
        hudAlpha = alpha.coerceIn(0.15f, 1.0f)
        getSharedPreferences("TradeDraw_HUDConfig", Context.MODE_PRIVATE)
            .edit()
            .putFloat("hud_alpha", hudAlpha)
            .apply()
        hudView?.alpha = hudAlpha
        updateHUDView()
    }

    private fun cycleHUDOpacity() {
        val nextAlpha = when {
            hudAlpha > 0.90f -> 0.75f
            hudAlpha > 0.65f -> 0.50f
            hudAlpha > 0.40f -> 0.25f
            else -> 1.00f
        }
        setHUDOpacity(nextAlpha)
        Toast.makeText(this, "Transparencia HUD: ${(hudAlpha * 100).toInt()}%", Toast.LENGTH_SHORT).show()
    }

    private fun showHUDOpacityDialog() {
        val options = arrayOf(
            "100% (Sólido)",
            "85% (Recomendado)",
            "70% (Translúcido)",
            "50% (Semi-transparente)",
            "35% (Muy transparente)",
            "20% (Ultra discreto)"
        )
        val values = floatArrayOf(1.0f, 0.85f, 0.70f, 0.50f, 0.35f, 0.20f)
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Transparencia del HUD")
            .setItems(options) { _, which ->
                setHUDOpacity(values[which])
                Toast.makeText(this, "Opacidad: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHUDWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        hudView = LayoutInflater.from(this).inflate(R.layout.layout_trading_hud, null)
        hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 50
        }

        val hudPrefs = getSharedPreferences("TradeDraw_HUDConfig", Context.MODE_PRIVATE)
        hudAlpha = hudPrefs.getFloat("hud_alpha", 0.70f)
        isHudCollapsed = hudPrefs.getBoolean("hud_collapsed", true)
        hudView?.alpha = hudAlpha

        val sliderContainer = hudView?.findViewById<View>(R.id.hud_opacity_slider_container)
        val seekOpacity = hudView?.findViewById<SeekBar>(R.id.hud_seek_opacity)
        val txtOpacityVal = hudView?.findViewById<TextView>(R.id.hud_txt_opacity_value)

        val currentPct = (hudAlpha * 100).toInt().coerceIn(20, 100)
        seekOpacity?.progress = currentPct - 20
        txtOpacityVal?.text = "$currentPct%"

        seekOpacity?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = (progress + 20).coerceIn(20, 100)
                hudAlpha = pct / 100f
                hudView?.alpha = hudAlpha
                txtOpacityVal?.text = "$pct%"
                hudView?.findViewById<TextView>(R.id.hud_btn_opacity)?.text = " 👁️ $pct% "
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                hudPrefs.edit().putFloat("hud_alpha", hudAlpha).apply()
            }
        })

        hudView?.findViewById<TextView>(R.id.hud_btn_opacity)?.apply {
            text = " 👁️ $currentPct% "
            setOnClickListener {
                if (sliderContainer != null) {
                    sliderContainer.visibility = if (sliderContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
            setOnLongClickListener {
                showHUDOpacityDialog()
                true
            }
        }

        val detailsContainer = hudView?.findViewById<View>(R.id.hud_details_container)
        val btnCollapse = hudView?.findViewById<TextView>(R.id.hud_btn_collapse)
        detailsContainer?.visibility = if (isHudCollapsed) View.GONE else View.VISIBLE
        btnCollapse?.text = if (isHudCollapsed) "▼" else "▲"

        btnCollapse?.setOnClickListener {
            isHudCollapsed = !isHudCollapsed
            hudPrefs.edit().putBoolean("hud_collapsed", isHudCollapsed).apply()
            detailsContainer?.visibility = if (isHudCollapsed) View.GONE else View.VISIBLE
            btnCollapse.text = if (isHudCollapsed) "▼" else "▲"
        }

        hudView?.findViewById<TextView>(R.id.hud_btn_chat)?.setOnClickListener {
            openAgentChat()
        }

        hudView?.findViewById<LinearLayout>(R.id.hud_signal_card)?.setOnClickListener {
            if (tradingEngine.mode == AutoTradeMode.AUTONOMOUS) {
                tradingEngine.autonomousSubMode = if (tradingEngine.autonomousSubMode == AutonomousSubMode.YOLO) {
                    AutonomousSubMode.CONSERVATIVE
                } else {
                    AutonomousSubMode.YOLO
                }
                updateHUDView()
                val subName = if (tradingEngine.autonomousSubMode == AutonomousSubMode.YOLO) "🚀 YOLO (Continuo)" else "🟢 Conservador"
                Toast.makeText(this, "Submodo: $subName", Toast.LENGTH_SHORT).show()
            } else {
                showModeDialog()
            }
        }

        hudView?.findViewById<Button>(R.id.hud_btn_win)?.setOnClickListener {
            riskManager.recordTradeWin()
            updateHUDView()
        }
        hudView?.findViewById<Button>(R.id.hud_btn_loss)?.setOnClickListener {
            riskManager.recordTradeLoss()
            updateHUDView()
        }
        hudView?.findViewById<Button>(R.id.hud_btn_recalc_ai)?.setOnClickListener {
            tradingEngine.unlockAllLines()
            updateHUDView()
            Toast.makeText(this, "↺ S/R restablecido: IA recalculando en vivo", Toast.LENGTH_SHORT).show()
        }

        val showStatsManager = View.OnLongClickListener {
            val options = arrayOf(
                "🔄 Reiniciar Contador a 0 | 0",
                "🎯 Sincronizar Manualmente con Binomo",
                "📊 Resumen de Sesión"
            )
            AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
                .setTitle("Gestión de Contador W/L")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            riskManager.resetStats()
                            updateHUDView()
                            Toast.makeText(this, "Contador reiniciado a 0 | 0", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            promptNumberAdjustment("Total Ganadas (W)", riskManager.totalWins, 0, 999) { newWins ->
                                promptNumberAdjustment("Total Perdidas (L)", riskManager.totalLosses, 0, 999) { newLosses ->
                                    riskManager.setStats(newWins, newLosses)
                                    updateHUDView()
                                    Toast.makeText(this, "Sincronizado: W:$newWins | L:$newLosses", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        2 -> {
                            val winrate = "%.1f%%".format(Locale.US, riskManager.getWinRate())
                            Toast.makeText(this, "Sesión: ${riskManager.totalWins}W - ${riskManager.totalLosses}L ($winrate)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .create().apply {
                    window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                    show()
                }
            true
        }
        hudView?.findViewById<Button>(R.id.hud_btn_win)?.setOnLongClickListener(showStatsManager)
        hudView?.findViewById<Button>(R.id.hud_btn_loss)?.setOnLongClickListener(showStatsManager)

        hudView?.let { v ->
            var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f; var isMove = false
            v.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = hudParams?.x ?: 0
                        initY = hudParams?.y ?: 0
                        touchX = event.rawX
                        touchY = event.rawY
                        isMove = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMove = true
                        hudParams?.let { p ->
                            val metrics = resources.displayMetrics
                            val screenW = metrics.widthPixels
                            val screenH = metrics.heightPixels
                            val hudW = v.width.takeIf { it > 0 } ?: 400
                            val hudH = v.height.takeIf { it > 0 } ?: 200
                            // Libertad total de movimiento confinado estrictamente a los límites visibles de la pantalla
                            p.x = (initX + dx).coerceIn(0, screenW - hudW)
                            p.y = (initY + dy).coerceIn(0, screenH - hudH)
                            windowManager.updateViewLayout(v, p)
                        }
                        true
                    }
                    else -> false
                }
            }
            windowManager.addView(v, hudParams)
            v.visibility = View.GONE
            isHudVisible = false
            startHUDTimerLoop()
        }
    }

    fun isPointInsideHUD(x: Float, y: Float): Boolean {
        if (!isHudVisible) return false
        val p = hudParams ?: return false
        val v = hudView ?: return false
        val w = v.width.takeIf { it > 0 } ?: 400
        val h = v.height.takeIf { it > 0 } ?: 200
        return x >= p.x && x <= (p.x + w) && y >= p.y && y <= (p.y + h)
    }

    /**
     * Vuelve el HUD temporalmente no-táctil por [durationMs] para permitir que los clics
     * de trading de Accesibilidad atraviesen limpiamente hacia el broker sin importar dónde esté el HUD.
     */
    fun temporarilyBypassHUD(durationMs: Long = 250L, onBypassed: (() -> Unit)? = null) {
        val v = hudView ?: run {
            onBypassed?.invoke()
            return
        }
        val p = hudParams ?: run {
            onBypassed?.invoke()
            return
        }
        mainHandler.post {
            v.visibility = View.INVISIBLE
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try { windowManager.updateViewLayout(v, p) } catch (e: Exception) {}
            // Esperar 30ms a que WindowManager aplique el cambio antes de emitir el toque
            mainHandler.postDelayed({
                onBypassed?.invoke()
            }, 30L)
            mainHandler.postDelayed({
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                v.visibility = View.VISIBLE
                try { windowManager.updateViewLayout(v, p) } catch (e: Exception) {}
            }, durationMs)
        }
    }


    private val hudTimerRunnable = object : Runnable {
        override fun run() {
            if (isHudVisible) updateHUDView()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun startHUDTimerLoop() {
        mainHandler.removeCallbacks(hudTimerRunnable)
        mainHandler.post(hudTimerRunnable)
    }

    private fun toggleHUDVisibility() {
        isHudVisible = !isHudVisible
        if (isHudVisible) {
            // Antes de mostrar, garantizar que el HUD esté dentro de los bordes visibles de la pantalla
            hudParams?.let { p ->
                val view = hudView ?: return@let
                val metrics = resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val hudW = view.width.takeIf { it > 0 } ?: 400
                val hudH = view.height.takeIf { it > 0 } ?: 200
                // Si está fuera de los límites de la pantalla, reposicionar a zona segura
                val outOfBounds = p.x < 0 || p.x > screenW - 40 ||
                                  p.y < 0 || p.y > screenH - 80
                if (outOfBounds) {
                    p.x = 40
                    p.y = 200
                    windowManager.updateViewLayout(view, p)
                }
            }
            hudView?.visibility = View.VISIBLE
            updateHUDView()
        } else {
            hudView?.visibility = View.GONE
        }
    }

    fun updateHUDView() {
        mainHandler.post {
            val v = hudView ?: return@post
            val txtMode = v.findViewById<TextView>(R.id.hud_mode)
            val txtStrat = v.findViewById<TextView>(R.id.hud_strategy)
            val txtStats = v.findViewById<TextView>(R.id.hud_stats)
            val txtWinrate = v.findViewById<TextView>(R.id.hud_winrate)
            val txtStatus = v.findViewById<TextView>(R.id.hud_status)
            val txtDiag = v.findViewById<TextView>(R.id.hud_diag)
            val txtHint = v.findViewById<TextView>(R.id.hud_hint)
            val txtStreak = v.findViewById<TextView>(R.id.hud_streak_badge)
            val txtMartingale = v.findViewById<TextView>(R.id.hud_martingale_badge)
            val txtTimer = v.findViewById<TextView>(R.id.hud_timer)
            val txtPower = v.findViewById<TextView>(R.id.hud_power_bar)
            val btnOpacity = v.findViewById<TextView>(R.id.hud_btn_opacity)
            val btnCollapse = v.findViewById<TextView>(R.id.hud_btn_collapse)
            val btnChat = v.findViewById<TextView>(R.id.hud_btn_chat)
            val detailsContainer = v.findViewById<View>(R.id.hud_details_container)

            btnChat?.setOnClickListener { openAgentChat() }
            btnOpacity?.text = " 👁️ ${(hudAlpha * 100).toInt()}% "
            btnCollapse?.text = if (isHudCollapsed) "▼" else "▲"
            detailsContainer?.visibility = if (isHudCollapsed) View.GONE else View.VISIBLE
            v.alpha = hudAlpha

            val isAccessConnected = AutoTradeAccessibilityService.instance != null
            val (canTradeStatus, blockReason) = riskManager.canExecuteTrade(tradingEngine.mode, tradingEngine.autonomousSubMode)

            when (tradingEngine.mode) {
                AutoTradeMode.AUTONOMOUS -> {
                    if (!isAccessConnected) {
                        txtMode.text = "[SIN ACCESO]"
                        txtMode.setTextColor(Color.RED)
                    } else if (tradingEngine.autonomousSubMode == AutonomousSubMode.YOLO) {
                        txtMode.text = "[YOLO 🚀]"
                        txtMode.setTextColor(Color.parseColor("#ec4899"))
                    } else if (!canTradeStatus && !riskManager.hasPendingTrade) {
                        if (riskManager.stopLossStreak > 0 && riskManager.currentLossStreak >= riskManager.stopLossStreak) {
                            txtMode.text = "[PAUSA SL]"
                            txtMode.setTextColor(Color.parseColor("#f87171"))
                        } else if (riskManager.takeProfitWins > 0 && riskManager.currentWins >= riskManager.takeProfitWins) {
                            txtMode.text = "[PAUSA TP]"
                            txtMode.setTextColor(Color.parseColor("#facc15"))
                        } else {
                            txtMode.text = "[COOLDOWN]"
                            txtMode.setTextColor(Color.parseColor("#fb923c"))
                        }
                    } else {
                        txtMode.text = "[AUTO]"
                        txtMode.setTextColor(Color.GREEN)
                    }
                }
                AutoTradeMode.SEMIAUTOMATIC -> {
                    txtMode.text = "[SEMI]"
                    txtMode.setTextColor(Color.YELLOW)
                }
                AutoTradeMode.DISABLED -> {
                    txtMode.text = "[OFF]"
                    txtMode.setTextColor(Color.GRAY)
                }
            }

            txtMode.setOnClickListener {
                if (AutoTradeAccessibilityService.instance == null) {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {}
                } else if (!canTradeStatus && !riskManager.hasPendingTrade && tradingEngine.mode == AutoTradeMode.AUTONOMOUS) {
                    riskManager.resetStreakOnly()
                    updateHUDView()
                    Toast.makeText(this@OverlayService, "▶️ Operativa reanudada (Límites reseteados)", Toast.LENGTH_SHORT).show()
                } else {
                    showModeDialog()
                }
            }

            txtStrat.text = "Estrat: " + when (tradingEngine.strategy) {
                AutoTradeStrategy.AUTO_ADAPTIVE -> "Auto"
                AutoTradeStrategy.MT_MASTER_COMBO -> "MT Combo"
                AutoTradeStrategy.MT_REJECTION -> "MT Rechazo"
                AutoTradeStrategy.MT_CHOQUE_PULLBACK -> "MT Choque"
                AutoTradeStrategy.MT_3_VELAS_AGOTAMIENTO -> "MT 3 Velas"
                AutoTradeStrategy.MT_ENGULFING_SR -> "MT Envolvente"
                AutoTradeStrategy.MT_FALSE_BREAKOUT -> "MT Trampa"
                AutoTradeStrategy.COLOR_TREND -> "Color Trend"
                AutoTradeStrategy.STRIKE_BREAKOUT -> "Strike Break"
                AutoTradeStrategy.AI_REMOTE -> "IA Remota"
                AutoTradeStrategy.SUPPORT_RESISTANCE -> "S/R"
                AutoTradeStrategy.CANDLE_PATTERNS -> "Velas"
                AutoTradeStrategy.TREND_FOLLOWING -> "Tendencia"
                AutoTradeStrategy.COMBINED -> "Combinada"
            }

            val analysis = tradingEngine.latestAnalysisResult

            // Radar de Racha
            txtStreak.text = "· " + (analysis?.streakBadge ?: "1D ⚪")

            // Stats & Martingala
            txtStats.text = "W: ${riskManager.totalWins} | L: ${riskManager.totalLosses}"
            txtWinrate.text = " (%.1f%%)".format(Locale.US, riskManager.getWinRate())
            txtMartingale.text = " " + riskManager.getMartingaleStatusBadge()

            // Temporizador de Vela 60s
            val sec = java.util.Calendar.getInstance().get(java.util.Calendar.SECOND)
            val remainingSec = (60 - sec) % 60
            txtTimer.text = " ⏱️ :%02ds".format(remainingSec)
            txtTimer.setTextColor(if (remainingSec in 0..5 || remainingSec in 28..32) Color.parseColor("#4ade80") else Color.parseColor("#facc15"))

            // Termómetro de Señal (% CALL vs % PUT)
            if (analysis != null) {
                val callPct = analysis.signalPowerCall
                val putPct = analysis.signalPowerPut
                val bars = (callPct / 10).coerceIn(1, 9)
                val visualBar = "█".repeat(bars) + "░".repeat(10 - bars)
                txtPower.text = "[ $callPct% CALL $visualBar $putPct% PUT ]"
                txtPower.setTextColor(if (callPct >= 62) Color.parseColor("#22c55e") else if (putPct >= 62) Color.parseColor("#ef4444") else Color.parseColor("#38bdf8"))
            } else {
                txtPower.text = "[ 50% CALL █████░░░░░ 50% PUT ]"
                txtPower.setTextColor(Color.parseColor("#94a3b8"))
            }

            val frames = screenCaptureManager?.totalFramesCaptured ?: 0L
            val diagStr = if (frames == 0L && screenCaptureManager == null) {
                "⚠️ Visión inactiva (Toca aquí para iniciar)"
            } else {
                analysis?.diagnosticSummary ?: "Visión: Esperando frame..."
            }
            txtDiag.text = "📷 Frames: $frames | $diagStr"
            if (frames == 0L) {
                txtDiag.setTextColor(Color.parseColor("#facc15"))
            } else {
                txtDiag.setTextColor(Color.parseColor("#94a3b8"))
            }

            txtDiag.setOnClickListener {
                try {
                    val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    Toast.makeText(this@OverlayService, "Toca 'INICIAR OVERLAY' y elige 'Toda la pantalla'", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("TradeDraw", "Error abriendo MainActivity", e)
                }
            }

            txtHint.text = tradingEngine.getStrategyStatusHint()

            // Botón de estado S/R (Manual vs Auto)
            val btnRecalc = v.findViewById<Button>(R.id.hud_btn_recalc_ai)
            if (tradingEngine.hasLockedLines()) {
                btnRecalc?.text = "↺ S/R [Manual]"
                btnRecalc?.setTextColor(Color.parseColor("#facc15"))
            } else {
                btnRecalc?.text = "✓ S/R [Auto]"
                btnRecalc?.setTextColor(Color.parseColor("#34d399"))
            }

            // Bloque didáctico: Tendencia Explicada y Próximo Movimiento Planeado
            val txtTrendBadge = v.findViewById<TextView>(R.id.hud_trend_badge)
            val txtTrendReason = v.findViewById<TextView>(R.id.hud_trend_reason)
            val txtPlannedAction = v.findViewById<TextView>(R.id.hud_planned_action)

            if (analysis != null) {
                val isSideways = analysis.isMarketSideways
                val trend = analysis.trend
                val callPower = analysis.signalPowerCall
                val putPower = analysis.signalPowerPut

                if (isSideways) {
                    txtTrendBadge?.text = "📊 Tendencia: LATERAL / RANGO (50/50)"
                    txtTrendBadge?.setTextColor(Color.parseColor("#facc15"))
                    txtTrendReason?.text = "Mercado indeciso con velas doji. El agente filtra entradas para proteger capital."
                    txtPlannedAction?.text = "🎯 Plan: Esperar ruptura limpia de soporte o resistencia con volumen."
                } else if (trend == TrendDirection.UPTREND) {
                    txtTrendBadge?.text = "📈 Tendencia: ALCISTA ($callPower% Poder CALL)"
                    txtTrendBadge?.setTextColor(Color.parseColor("#4ade80"))
                    txtTrendReason?.text = "Máximos y mínimos crecientes sobre medias móviles dinámicas."
                    txtPlannedAction?.text = "🎯 Plan: Buscar confirmación de rebote en soporte para entrar CALL al segundo :58s."
                } else {
                    txtTrendBadge?.text = "📉 Tendencia: BAJISTA ($putPower% Poder PUT)"
                    txtTrendBadge?.setTextColor(Color.parseColor("#f87171"))
                    txtTrendReason?.text = "Presión de venta dominante con rechazo en resistencias."
                    txtPlannedAction?.text = "🎯 Plan: Buscar retroceso/pullback a resistencia para entrar PUT al segundo :58s."
                }
            }

            // Tarjeta de Señal Operativa (Semiautomático / Autónomo)
            val cardSignal = v.findViewById<LinearLayout>(R.id.hud_signal_card)
            val txtSignalTitle = v.findViewById<TextView>(R.id.hud_signal_title)
            val txtSignalDesc = v.findViewById<TextView>(R.id.hud_signal_desc)
            val txtSignalCountdown = v.findViewById<TextView>(R.id.hud_signal_countdown)

            val activeSignal = tradingEngine.currentActiveSignal
            if (activeSignal != null) {
                val elapsed = (System.currentTimeMillis() - activeSignal.timestamp) / 1000
                val remaining = activeSignal.expirySeconds - elapsed
                if (remaining > 0) {
                    val isBuy = activeSignal.action == TradeAction.BUY
                    cardSignal?.setBackgroundResource(if (isBuy) R.drawable.bg_hud_signal_buy else R.drawable.bg_hud_signal_sell)
                    val modeTag = if (tradingEngine.mode == AutoTradeMode.SEMIAUTOMATIC) " [¡OPERAR EN BINOMO!]" else ""
                    txtSignalTitle?.text = "${activeSignal.title}$modeTag"
                    txtSignalTitle?.setTextColor(if (isBuy) Color.parseColor("#4ade80") else Color.parseColor("#f87171"))
                    txtSignalDesc?.text = "${activeSignal.reason} · Expira: 1 Minuto"
                    txtSignalDesc?.setTextColor(Color.parseColor("#f8fafc"))
                    txtSignalCountdown?.visibility = View.VISIBLE
                    txtSignalCountdown?.text = "⏱️ Ventana de Entrada: ${remaining}s restantes"
                } else {
                    tradingEngine.clearActiveSignal()
                    renderIdleSignalCard(cardSignal, txtSignalTitle, txtSignalDesc, txtSignalCountdown)
                }
            } else {
                renderIdleSignalCard(cardSignal, txtSignalTitle, txtSignalDesc, txtSignalCountdown)
            }

            if (riskManager.hasPendingTrade) {
                val elapsed = (System.currentTimeMillis() - riskManager.pendingTradeStartTime) / 1000
                txtStatus.text = "🤖 Operación en curso (${elapsed}s) · Esperando resultado..."
                txtStatus.setTextColor(Color.parseColor("#38bdf8"))
            } else {
                val remaining = riskManager.getRemainingCooldown()
                if (remaining > 0) {
                    txtStatus.text = "⏳ Cooldown: ${remaining}s"
                    txtStatus.setTextColor(Color.parseColor("#fb923c"))
                } else {
                    txtStatus.text = if (tradingEngine.mode != AutoTradeMode.DISABLED) "🟢 Analizando en vivo" else "⚪ En espera"
                    txtStatus.setTextColor(Color.parseColor("#cbd5e1"))
                }
            }
        }
    }

    private fun renderIdleSignalCard(
        card: LinearLayout?,
        title: TextView?,
        desc: TextView?,
        countdown: TextView?
    ) {
        card?.setBackgroundResource(R.drawable.bg_hud_signal_idle)
        countdown?.visibility = View.GONE
        when (tradingEngine.mode) {
            AutoTradeMode.AUTONOMOUS -> {
                if (tradingEngine.autonomousSubMode == AutonomousSubMode.YOLO) {
                    title?.text = "🚀 AUTO: SUBMODO YOLO (Continuo)"
                    title?.setTextColor(Color.parseColor("#ec4899"))
                    desc?.text = "Operativa continua sin Stop Loss ni pausas. Opera ante cada setup."
                    desc?.setTextColor(Color.parseColor("#f472b6"))
                } else {
                    title?.text = "🟢 AUTO: SUBMODO CONSERVADOR"
                    title?.setTextColor(Color.parseColor("#4ade80"))
                    desc?.text = "Gestión de riesgo activa (Stop Loss, Take Profit y Cooldowns)."
                    desc?.setTextColor(Color.parseColor("#94a3b8"))
                }
            }
            AutoTradeMode.SEMIAUTOMATIC -> {
                title?.text = "🟡 MODO SEMIAUTO: Vigilando Entrada"
                title?.setTextColor(Color.parseColor("#facc15"))
                desc?.text = "El bot te indicará cuándo y hacia dónde operar."
                desc?.setTextColor(Color.parseColor("#94a3b8"))
            }
            AutoTradeMode.DISABLED -> {
                title?.text = "⚪ MODO OFF: En Espera"
                title?.setTextColor(Color.parseColor("#94a3b8"))
                desc?.text = "Selecciona Autónomo o Semiauto en el menú de IA para operar."
                desc?.setTextColor(Color.parseColor("#64748b"))
            }
        }
    }

    private fun prepareSubmenu() {
        submenuContainer.removeAllViews()
    }

    private fun addItemToSubmenu(iconRes: Int, text: String, tint: Int? = null, onClick: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.bg_button_circle_ripple)
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(54, 54)
            setImageResource(iconRes)
            tint?.let { setColorFilter(it) } ?: setColorFilter(Color.WHITE)
        }
        val txt = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 9.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        item.addView(img)
        item.addView(txt)
        submenuContainer.addView(item)
    }

    private fun selectTool(tool: TradingTool) {
        if (tool == TradingTool.TEXT_LABEL) {
            promptLabelText()
            return
        }
        drawingView.setTool(tool)
        if (!isDrawingMode) toggleLock()
    }

    private fun promptLabelText() {
        val input = android.widget.EditText(this).apply {
            hint = "Escribe la etiqueta..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Texto / Etiqueta")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                drawingView.setLabelText(input.text.toString())
                drawingView.setTool(TradingTool.TEXT_LABEL)
                if (!isDrawingMode) toggleLock()
            }
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
    }

    private fun toggleLock() {
        isDrawingMode = !isDrawingMode
        if (isDrawingMode) canvasParams.flags = canvasParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else canvasParams.flags = canvasParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(canvasView, canvasParams)
        } catch (e: Exception) {
            android.util.Log.e("TradeDraw", "updateViewLayout(canvas) fallo", e)
        }
        // Asegurar que el menú quede SIEMPRE encima del lienzo.
        // Algunos dispositivos re-ordenan las ventanas overlay al hacer
        // updateViewLayout del lienzo, dejando el menú debajo (no responde).
        bringMenuToFront()
    }

    private fun bringMenuToFront() {
        if (!::menuView.isInitialized || !::menuParams.isInitialized) return
        // Posteado para no re-entrar durante el dispatch del toque actual.
        mainHandler.post {
            try {
                windowManager.removeView(menuView)
                windowManager.addView(menuView, menuParams)
                if (::submenuWindowView.isInitialized && submenuWindowView.visibility == View.VISIBLE) {
                    windowManager.removeView(submenuWindowView)
                    windowManager.addView(submenuWindowView, submenuParams)
                }
            } catch (e: Exception) {
                android.util.Log.e("TradeDraw", "bringMenuToFront fallo", e)
            }
        }
    }

    private fun showColorPicker() {
        val wheel = ColorWheelView(this)
        val size = (240 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(16, 16, 16, 8)
            addView(wheel, FrameLayout.LayoutParams(size, size))
        }
        val dialog = AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Color del gráfico")
            .setView(container)
            .setPositiveButton("Aplicar", null)
            .setNegativeButton("Cancelar", null)
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
        wheel.onColorChanged = { color -> drawingView.setColor(color) }
    }

    private fun saveTemplate() {
        templateManager.saveLocal("ULTIMA_PLANTILLA", drawingView.getShapes())
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
    }

    private fun loadTemplate() {
        val shapes = templateManager.loadLocal("ULTIMA_PLANTILLA")
        if (shapes.isNotEmpty()) { drawingView.setShapes(shapes); Toast.makeText(this, "Cargado", Toast.LENGTH_SHORT).show() }
    }

    private fun shareTemplate() {
        val json = templateManager.serialize(drawingView.getShapes())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, json); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(intent, "Exportar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toggleMenu() {
        isMenuExpanded = !isMenuExpanded
        categoryContainer.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
        if (!isMenuExpanded) hideSubmenu()
        if (isMenuExpanded) keepMenuOnScreen()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMenuMovement(bubble: View) {
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f; var isMove = false
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = menuParams.x; initialY = menuParams.y; initialTouchX = event.rawX; initialTouchY = event.rawY; isMove = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt(); val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) isMove = true
                    // Métricas EN VIVO: se actualizan al rotar la pantalla (vertical/horizontal)
                    val dm = resources.displayMetrics
                    menuParams.x = (initialX + dx).coerceIn(0, dm.widthPixels - 120)
                    menuParams.y = (initialY + dy).coerceIn(0, dm.heightPixels - 120)
                    windowManager.updateViewLayout(menuView, menuParams)
                    if (::submenuWindowView.isInitialized && submenuWindowView.visibility == View.VISIBLE) {
                        positionSubmenuWindow()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!isMove) bubble.performClick(); true }
                else -> false
            }
        }
    }

    private fun startTradeDrawForeground() {
        val channelId = "trade_draw_main"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TradeDraw Pro", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val closeIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
        val closePending = PendingIntent.getService(this, 0, closeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TradeDraw Pro").setContentText("Interfaz profesional activa.").setSmallIcon(R.mipmap.ic_launcher)
            .addAction(0, "Cerrar", closePending)
            .setOngoing(true).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14+ requiere especificar el flag de tipo de servicio para MediaProjection
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            startForeground(1001, notification, type)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        httpBridge?.stop()
        httpBridge = null
        try {
            unregisterReceiver(overlayCommandReceiver)
        } catch (e: Exception) {}
        screenCaptureManager?.destroy()
        if (::tradingEngine.isInitialized) {
            tradingEngine.stop()
            tradingEngine.aiClient.destroy()
        }
        if (::calibrationManager.isInitialized) {
            calibrationManager.dismissCalibration()
        }
        agentChatOverlay?.dismiss()
        agentChatOverlay = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            if (::canvasView.isInitialized) windowManager.removeView(canvasView)
            if (::menuView.isInitialized) windowManager.removeView(menuView)
            if (::submenuWindowView.isInitialized) windowManager.removeView(submenuWindowView)
            hudView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
    }
}
