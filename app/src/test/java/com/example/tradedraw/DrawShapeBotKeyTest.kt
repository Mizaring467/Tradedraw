package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 11 (T8): la clave interna de una figura del bot llegaba a pintarse en pantalla.
 *
 * `addOrUpdateBotShape` hacia `shape.labelText = key`, y el lienzo pinta `labelText`. Resultado:
 * el identificador tecnico "BOT_STRIKE_PRICE" aparecia escrito sobre el grafico (evidencia:
 * loops/screenshots/r11/t8_landscape_03.png). La clave va ahora en `botKey`.
 */
class DrawShapeBotKeyTest {

    @Test
    fun botKeyNoSePintaComoEtiqueta() {
        val shape = DrawShape(
            tool = TradingTool.STRIKE_PRICE_LINE,
            color = 0,
            strokeWidth = 1f,
            labelText = "PUT [ITM $1.20]"
        )
        shape.botKey = "BOT_STRIKE_PRICE"
        shape.isBotDrawn = true

        assertEquals("La etiqueta visible debe ser la de negocio", "PUT [ITM $1.20]", shape.labelText)
        assertEquals("La clave tecnica vive en botKey", "BOT_STRIKE_PRICE", shape.botKey)
        assertFalse(
            "El identificador tecnico NO debe estar en el texto que se pinta",
            shape.labelText.contains("BOT_")
        )
    }

    @Test
    fun botKeySobreviveAlCicloJson() {
        val shape = DrawShape(
            tool = TradingTool.STRIKE_PRICE_LINE,
            color = 0x00FF00,
            strokeWidth = 4f,
            labelText = "CALL [ITM $1.20]"
        )
        shape.botKey = "BOT_STRIKE_PRICE"
        shape.isBotDrawn = true

        val restored = DrawShape.fromJson(shape.toJson())
        assertEquals("botKey debe persistir", "BOT_STRIKE_PRICE", restored.botKey)
        assertEquals("labelText debe persistir", "CALL [ITM $1.20]", restored.labelText)
        assertTrue(restored.isBotDrawn)
    }
}
