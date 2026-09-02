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
        // DeepSeek
        ModelItem("deepseek-v4-flash-vision-exp", "DeepSeek-V4-Flash-Vision-Exp", "⚡ DeepSeek", "Visión Flash · Recomendado"),
        ModelItem("deepseek-v4-flash", "DeepSeek-V4-Flash", "⚡ DeepSeek", "Flash Rápido"),
        ModelItem("deepseek-v4-pro", "DeepSeek-V4-Pro", "⚡ DeepSeek", "Razonamiento Avanzado"),
        ModelItem("deepseek-chat", "DeepSeek-Chat", "⚡ DeepSeek", "Estándar"),

        // Gemini (Antigravity & OmniRoute)
        ModelItem("gemini-3.7-flash-high", "Gemini 3.7 Flash High (Antigravity)", "♊ Google Gemini", "Alta Precisión · Recomendado"),
        ModelItem("gemini-3.7-flash-medium", "Gemini 3.7 Flash Medium (Antigravity)", "♊ Google Gemini", "Equilibrado"),
        ModelItem("gemini-3.7-flash-low", "Gemini 3.7 Flash Low (Antigravity)", "♊ Google Gemini", "Baja Latencia"),
        ModelItem("gemini-3.6-flash", "Gemini 3.6 Flash", "♊ Google Gemini", "Rápido"),
        ModelItem("gemini-3.5-flash", "Gemini 3.5 Flash", "♊ Google Gemini", "Ultra Rápido"),

        // Claude
        ModelItem("claude-3-7-sonnet-latest", "Claude Sonnet 4.6 (Antigravity)", "🤖 Anthropic Claude", "Alta Inteligencia"),
        ModelItem("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "🤖 Anthropic Claude", "Visión Sonnet"),
        ModelItem("claude-3-haiku-20240307", "Claude 3 Haiku", "🤖 Anthropic Claude", "Respuesta Veloz"),

        // OpenAI / OmniRoute
        ModelItem("gpt-4o", "GPT-4o", "🌐 OpenAI / OmniRoute", "Omni Visión"),
        ModelItem("gpt-4o-mini", "GPT-4o Mini", "🌐 OpenAI / OmniRoute", "Ligero y Rápido"),
        ModelItem("o3-mini", "O3 Mini", "🌐 OpenAI / OmniRoute", "Razonamiento")
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
