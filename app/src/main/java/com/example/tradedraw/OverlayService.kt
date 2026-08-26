package com.example.tradedraw

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

/**
 * Servicio maestro con submenús inteligentes que detectan los límites de la pantalla.
 * Implementa el borrado prioritario de elementos seleccionados.
 */
class OverlayService : Service() {

    companion object {
        private const val ACTION_STOP = "com.example.tradedraw.STOP"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var canvasView: View
    private lateinit var drawingView: CustomDrawingView
    private lateinit var canvasParams: WindowManager.LayoutParams
    
    private lateinit var menuView: View
    private lateinit var categoryContainer: View
    private lateinit var menuParams: WindowManager.LayoutParams

    private lateinit var submenuWindowView: View
    private lateinit var submenuContainer: LinearLayout
    private lateinit var submenuParams: WindowManager.LayoutParams
    
    private lateinit var templateManager: TemplateManager

    private var isMenuExpanded = false
    private var isDrawingMode = false
    private var currentActiveCategory: Int = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        CrashLogger.showPending(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        templateManager = TemplateManager(this)
        startTradeDrawForeground()
        setupCanvasWindow()
        setupMenuWindow()
        // Asegurar que el menú quede encima del lienzo desde el inicio
        bringMenuToFront()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.postDelayed({
            if (!::menuParams.isInitialized) return@postDelayed
            val metrics = resources.displayMetrics
            if (menuParams.x > metrics.widthPixels) menuParams.x = metrics.widthPixels - 200
            if (menuParams.y > metrics.heightPixels) menuParams.y = metrics.heightPixels - 200
            if (::menuView.isInitialized) windowManager.updateViewLayout(menuView, menuParams)
        }, 500)
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
        addItemToSubmenu(if (drawingView.isCanvasVisible()) R.drawable.ic_visibility else R.drawable.ic_visibility_off, "VISTA") {
            drawingView.toggleCanvasVisibility(); showViewSubmenu()
        }
        addItemToSubmenu(if (isDrawingMode) R.drawable.ic_lock_closed else R.drawable.ic_lock_open, "LOCK") {
            toggleLock(); showViewSubmenu()
        }
    }

    private fun showEditSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(android.R.drawable.ic_menu_edit, "LAPIZ") { selectTool(TradingTool.FREE_BRUSH) }
        addItemToSubmenu(android.R.drawable.ic_menu_directions, "ELEGIR") { selectTool(TradingTool.SELECT_TOUCH) }
        addItemToSubmenu(android.R.drawable.ic_menu_close_clear_cancel, "BORRAR") { selectTool(TradingTool.ERASER_TOUCH) }
        addItemToSubmenu(android.R.drawable.ic_menu_delete, "LIMPIAR", Color.RED) {
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
        addItemToSubmenu(R.drawable.ic_trend_line, "LÍNEA") { selectTool(TradingTool.TREND_LINE) }
        addItemToSubmenu(android.R.drawable.ic_menu_more, "SOPORT", Color.RED) { selectTool(TradingTool.SUPPORT_LINE) }
        addItemToSubmenu(android.R.drawable.ic_menu_more, "RESIST", Color.GREEN) { selectTool(TradingTool.RESISTANCE_LINE) }
        addItemToSubmenu(android.R.drawable.ic_menu_sort_by_size, "FIBO") { selectTool(TradingTool.FIB_RETRACEMENT) }
    }

    private fun showShapesSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(android.R.drawable.ic_menu_crop, "ZONA") { selectTool(TradingTool.RECTANGLE) }
        addItemToSubmenu(android.R.drawable.ic_input_add, "LONG", Color.GREEN) { selectTool(TradingTool.LONG_POSITION) }
        addItemToSubmenu(android.R.drawable.ic_delete, "SHORT", Color.RED) { selectTool(TradingTool.SHORT_POSITION) }
    }

    private fun showFilesSubmenu() {
        prepareSubmenu()
        addItemToSubmenu(android.R.drawable.ic_menu_save, "SAVE") { saveTemplate() }
        addItemToSubmenu(android.R.drawable.ic_menu_recent_history, "LOAD") { loadTemplate() }
        addItemToSubmenu(android.R.drawable.ic_menu_share, "EXPORT") { shareTemplate() }
    }

    private fun prepareSubmenu() {
        submenuContainer.removeAllViews()
    }

    private fun addItemToSubmenu(iconRes: Int, text: String, tint: Int? = null, onClick: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(20, 10, 20, 10)
            isClickable = true; isFocusable = true; setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60); setImageResource(iconRes); tint?.let { setColorFilter(it) } ?: setColorFilter(Color.WHITE)
        }
        val txt = TextView(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 9f }
        item.addView(img); item.addView(txt); submenuContainer.addView(item)
    }

    private fun selectTool(tool: TradingTool) {
        drawingView.setTool(tool)
        if (!isDrawingMode) toggleLock()
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
        val colors = intArrayOf(Color.parseColor("#00FF00"), Color.RED, Color.CYAN, Color.YELLOW, Color.WHITE, Color.MAGENTA)
        val names = arrayOf("Verde", "Rojo", "Cian", "Amarillo", "Blanco", "Magenta")
        AlertDialog.Builder(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog))
            .setTitle("Paleta").setItems(names) { _, which -> drawingView.setColor(colors[which]) }
            .create().apply {
                window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                show()
            }
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
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenW = metrics.widthPixels; val screenH = metrics.heightPixels
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = menuParams.x; initialY = menuParams.y; initialTouchX = event.rawX; initialTouchY = event.rawY; isMove = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt(); val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) isMove = true
                    menuParams.x = (initialX + dx).coerceIn(0, screenW - 120)
                    menuParams.y = (initialY + dy).coerceIn(0, screenH - 120)
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
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            if (::canvasView.isInitialized) windowManager.removeView(canvasView)
            if (::menuView.isInitialized) windowManager.removeView(menuView)
            if (::submenuWindowView.isInitialized) windowManager.removeView(submenuWindowView)
        } catch (e: Exception) {}
    }
}
