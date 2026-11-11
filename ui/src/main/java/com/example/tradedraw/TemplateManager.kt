package com.example.tradedraw

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor de plantillas para TradeDraw.
 * Permite guardar análisis técnicos localmente y exportarlos como JSON.
 */
class TemplateManager(context: Context) {

    private val prefs = context.getSharedPreferences("TradeDraw_Templates", Context.MODE_PRIVATE)

    /**
     * Serializa la lista de figuras a un String JSON.
     */
    fun serialize(shapes: List<DrawShape>): String {
        val jsonArray = JSONArray()
        for (shape in shapes) {
            jsonArray.put(shape.toJson())
        }
        return jsonArray.toString()
    }

    /**
     * Deserializa un String JSON a una lista de figuras.
     */
    fun deserialize(json: String): List<DrawShape> {
        val shapes = mutableListOf<DrawShape>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                shapes.add(DrawShape.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return shapes
    }

    /**
     * Guarda la plantilla actual en el almacenamiento local.
     */
    fun saveLocal(name: String, shapes: List<DrawShape>) {
        val json = serialize(shapes)
        prefs.edit().putString(name, json).apply()
    }

    /**
     * Carga una plantilla guardada localmente.
     */
    fun loadLocal(name: String): List<DrawShape> {
        val json = prefs.getString(name, null)
        return if (json != null) deserialize(json) else emptyList()
    }

    /**
     * Obtiene los nombres de todas las plantillas guardadas.
     */
    fun getSavedTemplateNames(): List<String> {
        return prefs.all.keys.toList()
    }
}
