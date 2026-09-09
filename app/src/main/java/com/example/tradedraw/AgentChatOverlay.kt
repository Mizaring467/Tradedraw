package com.example.tradedraw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(val text: String, val isUser: Boolean)

class AgentChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<AgentChatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userContainer: View = view.findViewById(R.id.chat_user_container)
        val userText: TextView = view.findViewById(R.id.chat_user_text)
        val botContainer: View = view.findViewById(R.id.chat_bot_container)
        val botText: TextView = view.findViewById(R.id.chat_bot_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_agent_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) {
            holder.userContainer.visibility = View.VISIBLE
            holder.botContainer.visibility = View.GONE
            holder.userText.text = msg.text
        } else {
            holder.userContainer.visibility = View.GONE
            holder.botContainer.visibility = View.VISIBLE
            holder.botText.text = msg.text
        }
    }

    override fun getItemCount(): Int = messages.size
}

class AgentChatOverlay(
    private val context: Context,
    private val tradingEngine: TradingEngine
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var chatView: View? = null
    private var chatParams: WindowManager.LayoutParams? = null
    var isVisible = false
        private set

    private val messages = mutableListOf<ChatMessage>()
    private var adapter: AgentChatAdapter? = null

    init {
        // Mensaje de bienvenida inicial
        messages.add(ChatMessage("¡Hola! Soy tu Agente Autónomo de TradeDraw 🤖. Estoy vigilando el mercado en vivo. ¿En qué te ayudo?", false))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isVisible) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        chatView = LayoutInflater.from(context).inflate(R.layout.layout_agent_chat, null)
        val metrics = context.resources.displayMetrics
        val chatW = (320 * metrics.density).toInt().coerceAtMost(metrics.widthPixels - 40)
        val chatH = (380 * metrics.density).toInt().coerceAtMost(metrics.heightPixels - 100)

        chatParams = WindowManager.LayoutParams(
            chatW, chatH,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - chatW) / 2
            y = 180
        }

        val v = chatView!!
        val p = chatParams!!

        // Drag en la cabecera
        val header = v.findViewById<View>(R.id.chat_header)
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = p.x
                    initY = p.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    p.x = (initX + dx).coerceIn(0, metrics.widthPixels - chatW)
                    p.y = (initY + dy).coerceIn(0, metrics.heightPixels - chatH)
                    windowManager.updateViewLayout(v, p)
                    true
                }
                else -> false
            }
        }

        // Botón cerrar
        v.findViewById<View>(R.id.chat_btn_close)?.setOnClickListener {
            dismiss()
        }

        // RecyclerView
        val recycler = v.findViewById<RecyclerView>(R.id.chat_recycler_view)
        adapter = AgentChatAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // Input & Botón Enviar
        val editInput = v.findViewById<EditText>(R.id.chat_input_text)
        val btnSend = v.findViewById<Button>(R.id.chat_btn_send)

        val sendMessage = {
            val text = editInput.text.toString().trim()
            if (text.isNotEmpty()) {
                editInput.setText("")
                postUserMessage(text)
            }
        }

        btnSend.setOnClickListener { sendMessage() }

        // Chips de acciones rápidas
        v.findViewById<View>(R.id.chip_what_do_you_see)?.setOnClickListener {
            postUserMessage("¿Qué ves en el gráfico en este momento?")
        }
        v.findViewById<View>(R.id.chip_why_not_trading)?.setOnClickListener {
            postUserMessage("¿Por qué no estás operando ahora mismo?")
        }
        v.findViewById<View>(R.id.chip_recalc_sr)?.setOnClickListener {
            postUserMessage("Recalcula los soportes y resistencias de la IA")
        }
        v.findViewById<View>(R.id.chip_resume_trading)?.setOnClickListener {
            postUserMessage("Continúa operando, resetea el Stop Loss y reanuda el modo autónomo")
        }
        v.findViewById<View>(R.id.chip_safe_mode)?.setOnClickListener {
            postUserMessage("Activa el modo conservador y opera solo a favor de tendencia")
        }
        v.findViewById<View>(R.id.chip_yolo_mode)?.setOnClickListener {
            postUserMessage("Activa el modo YOLO y opera continuamente sin parar")
        }
        v.findViewById<View>(R.id.chip_stop_yolo)?.setOnClickListener {
            postUserMessage("Para el bot de inmediato")
        }
        v.findViewById<View>(R.id.chip_reset_wl)?.setOnClickListener {
            postUserMessage("Resetea el marcador de victorias y derrotas a 0")
        }

        windowManager.addView(v, p)
        isVisible = true
    }

    fun dismiss() {
        if (!isVisible) return
        chatView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        chatView = null
        chatParams = null
        isVisible = false
    }

    private fun postUserMessage(text: String) {
        messages.add(ChatMessage(text, true))
        adapter?.notifyItemInserted(messages.size - 1)
        chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)

        val statusIndicator = chatView?.findViewById<TextView>(R.id.chat_status_indicator)
        statusIndicator?.text = "● Escribiendo..."
        statusIndicator?.setTextColor(android.graphics.Color.parseColor("#facc15"))

        val lower = text.lowercase()

        // 1. Detección inmediata de reseteo completo de marcador
        if (lower.contains("reset wl") || lower.contains("resetea marcador") || lower.contains("reinicia marcador") || 
            lower.contains("borra las victorias") || lower.contains("marcador a cero") || lower.contains("marcador a 0")) {
            tradingEngine.riskManager.correctStats(0, 0)
            val botMsg = "🎯 Marcador W/L reiniciado a 0 Victorias y 0 Derrotas en el HUD."
            messages.add(ChatMessage(botMsg, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#22c55e"))
            return
        }

        // 2. Detección inmediata de corrección manual de estadísticas (ej. "corrige a 1 victoria y 0 derrotas", "1w 0l")
        val matchWL = Regex("""(?:corrige|ajusta|pon|marcador|son|lleva|cuenta|fija|actualiza)?\D*?(\d+)\s*(?:victoria|victorias|w|ganada|ganadas)\D*?(\d+)\s*(?:derrota|derrotas|l|perdida|perdidas)""", RegexOption.IGNORE_CASE).find(lower)
        val matchLW = if (matchWL == null) {
            Regex("""(?:corrige|ajusta|pon|marcador|son|lleva|cuenta|fija|actualiza)?\D*?(\d+)\s*(?:derrota|derrotas|l|perdida|perdidas)\D*?(\d+)\s*(?:victoria|victorias|w|ganada|ganadas)""", RegexOption.IGNORE_CASE).find(lower)
        } else null

        if (matchWL != null || matchLW != null) {
            val wins = if (matchWL != null) matchWL.groupValues[1].toInt() else matchLW!!.groupValues[2].toInt()
            val losses = if (matchWL != null) matchWL.groupValues[2].toInt() else matchLW!!.groupValues[1].toInt()
            tradingEngine.riskManager.correctStats(wins, losses)
            val botMsg = "🎯 ¡Entendido! He sincronizado y corregido el marcador en el HUD a $wins Victorias y $losses Derrotas."
            messages.add(ChatMessage(botMsg, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#22c55e"))
            return
        }

        // 3. Detección inmediata de detención del bot
        if (lower.contains("detén yolo") || lower.contains("para yolo") || lower.contains("desactiva yolo") || 
            lower.contains("para el bot") || lower.contains("detén el bot") || lower.contains("deten el bot") ||
            lower == "para" || lower == "stop" || lower == "pausa" || lower.contains("detente") || lower.contains("para ya")) {
            tradingEngine.mode = AutoTradeMode.DISABLED
            OverlayService.instance?.updateHUDView()
            val botMsg = "🛑 Trading detenido. El bot ha quedado pausado de forma segura."
            messages.add(ChatMessage(botMsg, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#94a3b8"))
            return
        }

        // 4. Detección de SUBMODO YOLO
        if (lower.contains("activa modo yolo") || lower.contains("modo yolo") || lower == "yolo" || lower.contains("activa yolo") || lower.contains("pon modo yolo") || lower.contains("submodo yolo")) {
            tradingEngine.autonomousSubMode = AutonomousSubMode.YOLO
            tradingEngine.mode = AutoTradeMode.AUTONOMOUS
            OverlayService.instance?.updateHUDView()
            val botMsg = "🚀 ¡SUBMODO YOLO ACTIVADO! Operando de forma autónoma continua sin Stop Loss ni límites de pérdidas. Escribe 'para' o toca '🛑 Parar Bot' cuando quieras pausar."
            messages.add(ChatMessage(botMsg, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#ec4899"))
            return
        }

        // 5. Detección de SUBMODO CONSERVADOR
        if (lower.contains("modo conservador") || lower.contains("submodo conservador") || lower.contains("modo seguro") || lower.contains("conservador")) {
            tradingEngine.autonomousSubMode = AutonomousSubMode.CONSERVATIVE
            tradingEngine.mode = AutoTradeMode.AUTONOMOUS
            tradingEngine.riskManager.resetStreakOnly()
            OverlayService.instance?.updateHUDView()
            val botMsg = "🛡️ ¡SUBMODO CONSERVADOR ACTIVADO! Operando de forma autónoma con gestión de riesgo estricta (Stop Loss, Take Profit y Cooldowns activos)."
            messages.add(ChatMessage(botMsg, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#22c55e"))
            return
        }

        // 6. Detección de reactivación normal / reset SL
        if (lower.contains("continua") || lower.contains("continúa") || lower.contains("sigue") || 
            lower.contains("reanuda") || lower.contains("reset sl") || lower.contains("ignora stop")) {
            executeAgentCommand("RESUME_TRADING")
        }

        // Construir contexto en vivo del mercado
        val analysis = tradingEngine.latestAnalysisResult
        val balance = AutoTradeAccessibilityService.instance?.readCurrentBalance() ?: 0.0
        val risk = tradingEngine.riskManager
        val contextData = """
            - Saldo Broker: $$balance COP
            - Modo: ${tradingEngine.mode} | Estrategia: ${tradingEngine.strategy}
            - Marcador W/L: ${risk.totalWins} W / ${risk.totalLosses} L | Martingala: ${risk.getMartingaleStatusBadge()}
            - Tendencia: ${analysis?.trend ?: "Desconocida"} | Lateral: ${analysis?.isMarketSideways ?: false}
            - Poder: CALL ${analysis?.signalPowerCall ?: 50}% / PUT ${analysis?.signalPowerPut ?: 50}%
            - Operación activa: ${risk.hasPendingTrade} | Cooldown: ${risk.getRemainingCooldown()}s
        """.trimIndent()

        tradingEngine.aiClient.sendChatMessage(text, contextData) { reply, cmdTag ->
            statusIndicator?.text = "● En vivo"
            statusIndicator?.setTextColor(android.graphics.Color.parseColor("#22c55e"))

            messages.add(ChatMessage(reply, false))
            adapter?.notifyItemInserted(messages.size - 1)
            chatView?.findViewById<RecyclerView>(R.id.chat_recycler_view)?.scrollToPosition(messages.size - 1)

            // Ejecutar comando emitido por la IA si corresponde
            if (cmdTag != null) {
                executeAgentCommand(cmdTag)
            }
        }
    }

    private fun executeAgentCommand(cmd: String) {
        if (cmd.startsWith("CORRECT_STATS:")) {
            val parts = cmd.removePrefix("CORRECT_STATS:").split(":")
            if (parts.size >= 2) {
                val w = parts[0].toIntOrNull() ?: 0
                val l = parts[1].toIntOrNull() ?: 0
                tradingEngine.riskManager.correctStats(w, l)
                Toast.makeText(context, "🎯 Marcador corregido a $w W / $l L", Toast.LENGTH_SHORT).show()
                return
            }
        }

        when (cmd) {
            "RESUME_TRADING" -> {
                tradingEngine.agentController.resumeAutonomousTrading("Chat de Usuario")
            }
            "MODE_AUTONOMOUS" -> {
                tradingEngine.autonomousSubMode = AutonomousSubMode.CONSERVATIVE
                tradingEngine.mode = AutoTradeMode.AUTONOMOUS
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🤖 Modo: AUTÓNOMO (Conservador)", Toast.LENGTH_SHORT).show()
            }
            "MODE_YOLO" -> {
                tradingEngine.autonomousSubMode = AutonomousSubMode.YOLO
                tradingEngine.mode = AutoTradeMode.AUTONOMOUS
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🚀 Modo: AUTÓNOMO (Submodo YOLO)", Toast.LENGTH_SHORT).show()
            }
            "MODE_SEMIAUTO" -> {
                tradingEngine.mode = AutoTradeMode.SEMIAUTOMATIC
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🤖 Modo cambiado a SEMIAUTOMÁTICO", Toast.LENGTH_SHORT).show()
            }
            "MODE_DISABLED", "STOP_TRADING" -> {
                tradingEngine.mode = AutoTradeMode.DISABLED
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🛑 Trading pausado", Toast.LENGTH_SHORT).show()
            }
            "STRAT_AUTO" -> {
                tradingEngine.strategy = AutoTradeStrategy.AUTO_ADAPTIVE
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🤖 Estrategia: AUTO_ADAPTIVE", Toast.LENGTH_SHORT).show()
            }
            "STRAT_TREND" -> {
                tradingEngine.strategy = AutoTradeStrategy.TREND_FOLLOWING
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🤖 Estrategia: SEGUIR TENDENCIA", Toast.LENGTH_SHORT).show()
            }
            "RECALC_SR" -> {
                tradingEngine.unlockAllLines()
                Toast.makeText(context, "🤖 Recalculando niveles de S/R...", Toast.LENGTH_SHORT).show()
            }
            "RESET_STATS" -> {
                tradingEngine.riskManager.resetStats()
                OverlayService.instance?.updateHUDView()
                Toast.makeText(context, "🤖 Marcador W/L reseteado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
