package com.example.tradedraw

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

data class ModelItem(
    val id: String,
    val name: String,
    val category: String,
    val badge: String
)

class ModelPickerDialog(
    private val context: Context,
    private val currentModel: String,
    private val onModelSelected: (String) -> Unit
) {

    private val allModels = listOf(
        // Antigravity Gemini 3.8 Flash (NUEVO)
        ModelItem("antigravity/gemini-3.8-flash-high", "Gemini 3.8 Flash High ⭐", "♊ Antigravity Gemini 3.8", "Nuevo Lanzamiento · Máxima Precisión"),
        ModelItem("antigravity/gemini-3.8-flash-medium", "Gemini 3.8 Flash Medium", "♊ Antigravity Gemini 3.8", "Nuevo Lanzamiento · Equilibrado"),
        ModelItem("antigravity/gemini-3.8-flash-low", "Gemini 3.8 Flash Low", "♊ Antigravity Gemini 3.8", "Nuevo Lanzamiento · Ultra Rápido"),
        ModelItem("antigravity/gemini-3.8-flash-tiered", "Gemini 3.8 Flash Tiered", "♊ Antigravity Gemini 3.8", "Pensamiento Adaptativo Dinámico"),

        // Antigravity Gemini 3.7 & Pro
        ModelItem("antigravity/gemini-3.7-flash-high", "Gemini 3.7 Flash High", "♊ Antigravity Gemini", "Alta Precisión Visión"),
        ModelItem("antigravity/gemini-3.7-flash-medium", "Gemini 3.7 Flash Medium", "♊ Antigravity Gemini", "Equilibrado"),
        ModelItem("antigravity/gemini-3.7-flash-low", "Gemini 3.7 Flash Low", "♊ Antigravity Gemini", "Mínima Latencia"),
        ModelItem("antigravity/gemini-pro-agent", "Gemini 3.1 Pro Agent", "♊ Antigravity Gemini", "Razonamiento Máximo"),

        // Claude (Anthropic en OmniRoute / Antigravity)
        ModelItem("claude-sonnet-4-6", "Claude Sonnet 4.6 ⭐", "🤖 Anthropic Claude", "Antigravity Upstream"),
        ModelItem("claude-opus-4-6-thinking", "Claude Opus 4.6 Thinking", "🤖 Anthropic Claude", "Razonamiento Profundo"),
        ModelItem("auto/claude-sonnet", "Claude Sonnet (Auto)", "🤖 Anthropic Claude", "OmniRoute Smart"),
        ModelItem("kiro/claude-sonnet-4.5", "Claude Sonnet 4.5", "🤖 Anthropic Claude", "Kiro Sonnet"),
        ModelItem("kiro/claude-haiku-4.5", "Claude Haiku 4.5", "🤖 Anthropic Claude", "Kiro Haiku Rápido"),

        // B.AI (Cloud)
        ModelItem("deepseek-v4-flash-vision-exp", "DeepSeek-V4 Flash Vision ⭐", "⚡ B.AI Cloud", "Visión Especializada"),
        ModelItem("deepseek-v4-flash", "DeepSeek-V4 Flash", "⚡ B.AI Cloud", "Ultra Rápido"),
        ModelItem("deepseek-v4-pro", "DeepSeek-V4 Pro", "⚡ B.AI Cloud", "Razonamiento Avanzado")
    )

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_picker, null)
        val etSearch = view.findViewById<EditText>(R.id.et_search_model)
        val containerList = view.findViewById<LinearLayout>(R.id.container_models_list)
        val btnClose = view.findViewById<TextView>(R.id.btn_close_model_picker)
        val btnCustom = view.findViewById<Button>(R.id.btn_custom_model)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }

        fun populateList(query: String = "") {
            containerList.removeAllViews()
            val filtered = if (query.isBlank()) {
                allModels
            } else {
                allModels.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.badge.contains(query, ignoreCase = true)
                }
            }

            if (filtered.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "No se encontraron modelos para \"$query\""
                    setTextColor(Color.parseColor("#71717a"))
                    textSize = 13f
                    setPadding(16, 24, 16, 24)
                }
                containerList.addView(emptyTv)
                return
            }

            var currentCategory = ""
            for (model in filtered) {
                if (model.category != currentCategory) {
                    currentCategory = model.category
                    val categoryTv = TextView(context).apply {
                        text = currentCategory
                        setTextColor(Color.parseColor("#a1a1aa"))
                        textSize = 12f
                        setPadding(8, 18, 8, 8)
                    }
                    containerList.addView(categoryTv)
                }

                val isSelected = model.id.equals(currentModel, ignoreCase = true) ||
                        currentModel.contains(model.id, ignoreCase = true)

                val itemView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 16, 20, 16)
                    val bgDrawable = GradientDrawable().apply {
                        cornerRadius = 12f
                        if (isSelected) {
                            setColor(Color.parseColor("#1e3a8a"))
                            setStroke(2, Color.parseColor("#3b82f6"))
                        } else {
                            setColor(Color.parseColor("#27272a"))
                        }
                    }
                    background = bgDrawable
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                    layoutParams = lp
                }

                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val titleTv = TextView(context).apply {
                    text = model.name
                    setTextColor(if (isSelected) Color.parseColor("#60a5fa") else Color.parseColor("#f4f4f5"))
                    textSize = 14f
                }

                val badgeTv = TextView(context).apply {
                    text = model.badge
                    setTextColor(Color.parseColor("#9ca3af"))
                    textSize = 11f
                }

                textContainer.addView(titleTv)
                textContainer.addView(badgeTv)

                val checkTv = TextView(context).apply {
                    text = if (isSelected) "✓" else ""
                    setTextColor(Color.parseColor("#60a5fa"))
                    textSize = 16f
                    setPadding(12, 0, 0, 0)
                }

                itemView.addView(textContainer)
                itemView.addView(checkTv)

                itemView.setOnClickListener {
                    onModelSelected(model.id)
                    dialog.dismiss()
                }

                containerList.addView(itemView)
            }
        }

        populateList()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClose.setOnClickListener { dialog.dismiss() }

        btnCustom.setOnClickListener {
            dialog.dismiss()
            showCustomInputDialog()
        }

        dialog.show()
    }

    private fun showCustomInputDialog() {
        val input = EditText(context).apply {
            hint = "Nombre del modelo (ej: gpt-4o-mini)"
            setText(currentModel)
        }
        val customDialog = AlertDialog.Builder(context)
            .setTitle("✏️ Modelo Personalizado")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val chosen = input.text.toString().trim()
                if (chosen.isNotBlank()) {
                    onModelSelected(chosen)
                } else {
                    Toast.makeText(context, "El nombre del modelo no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            customDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            customDialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }
        customDialog.show()
    }
}
