package com.example.tradedraw

import android.graphics.Path
import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clase que representa un objeto dibujado en el lienzo de TradeDraw.
 */
data class DrawShape(
    val tool: TradingTool,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var endX: Float = 0f,
    var endY: Float = 0f,
    var color: Int,
    var strokeWidth: Float,
    var isSelected: Boolean = false,
    var thirdX: Float = 0f,
    var thirdY: Float = 0f,
    var text: String = "",
    var lineStyle: Int = 0,
    var labelVisible: Boolean = true,
    var labelText: String = "",
    val pathPoints: MutableList<PointF> = mutableListOf()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("tool", tool.name)
        json.put("startX", startX.toDouble())
        json.put("startY", startY.toDouble())
        json.put("endX", endX.toDouble())
        json.put("endY", endY.toDouble())
        json.put("color", color)
        json.put("strokeWidth", strokeWidth.toDouble())
        json.put("thirdX", thirdX.toDouble())
        json.put("thirdY", thirdY.toDouble())
        json.put("text", text)
        json.put("lineStyle", lineStyle)
        json.put("labelVisible", labelVisible)
        json.put("labelText", labelText)
        if (pathPoints.isNotEmpty()) {
            val pointsArray = JSONArray()
            for (point in pathPoints) {
                val p = JSONObject()
                p.put("x", point.x.toDouble())
                p.put("y", point.y.toDouble())
                pointsArray.put(p)
            }
            json.put("pathPoints", pointsArray)
        }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): DrawShape {
            val tool = TradingTool.valueOf(json.getString("tool"))
            val shape = DrawShape(
                tool = tool,
                startX = json.getDouble("startX").toFloat(),
                startY = json.getDouble("startY").toFloat(),
                endX = json.getDouble("endX").toFloat(),
                endY = json.getDouble("endY").toFloat(),
                color = json.getInt("color"),
                strokeWidth = json.getDouble("strokeWidth").toFloat(),
                thirdX = json.optDouble("thirdX", 0.0).toFloat(),
                thirdY = json.optDouble("thirdY", 0.0).toFloat(),
                text = json.optString("text", ""),
                lineStyle = json.optInt("lineStyle", 0),
                labelVisible = json.optBoolean("labelVisible", true),
                labelText = json.optString("labelText", "")
            )
            if (json.has("pathPoints")) {
                val pointsArray = json.getJSONArray("pathPoints")
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.getJSONObject(i)
                    shape.pathPoints.add(PointF(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                }
            }
            return shape
        }
    }

    fun createPath(): Path {
        val p = Path()
        if (pathPoints.isNotEmpty()) {
            p.moveTo(pathPoints[0].x, pathPoints[0].y)
            for (i in 1 until pathPoints.size) {
                p.lineTo(pathPoints[i].x, pathPoints[i].y)
            }
        }
        return p
    }
}
