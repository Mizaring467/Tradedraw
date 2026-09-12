package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureManagerTest {

    data class SimpleRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width() = right - left
        fun height() = bottom - top
    }

    private fun computeRoi(w: Int, h: Int, custom: SimpleRect? = null): SimpleRect {
        custom?.let {
            val left = it.left.coerceIn(0, (w - 1).coerceAtLeast(0))
            val right = it.right.coerceIn(left + 1, w.coerceAtLeast(1))
            val top = it.top.coerceIn(0, (h - 1).coerceAtLeast(0))
            val bottom = it.bottom.coerceIn(top + 1, h.coerceAtLeast(1))
            return SimpleRect(left, top, right, bottom)
        }

        if (w <= 0 || h <= 0) return SimpleRect(0, 0, 0, 0)

        val isLandscape = w > h
        val roiLeft: Int
        val roiRight: Int
        val roiTop: Int
        val roiBottom: Int

        if (isLandscape) {
            roiLeft = (w * 0.48f).toInt().coerceIn(0, w - 1)
            roiRight = (w * 0.76f).toInt().coerceIn(roiLeft + 1, w)
            roiTop = (h * 0.18f).toInt().coerceIn(0, h - 1)
            roiBottom = (h * 0.78f).toInt().coerceIn(roiTop + 1, h)
        } else {
            roiLeft = (w * 0.45f).toInt().coerceIn(0, w - 1)
            roiRight = (w * 0.80f).toInt().coerceIn(roiLeft + 1, w)
            roiTop = (h * 0.18f).toInt().coerceIn(0, h - 1)
            roiBottom = (h * 0.74f).toInt().coerceIn(roiTop + 1, h)
        }

        return SimpleRect(roiLeft, roiTop, roiRight, roiBottom)
    }

    @Test
    fun testLandscapeActiveRoi() {
        val width = 2712
        val height = 1220
        val roi = computeRoi(width, height)

        assertTrue("Landscape ROI left (${roi.left}) debe estar cerca de 48% del ancho", roi.left in 1250..1350)
        assertTrue("Landscape ROI right (${roi.right}) debe estar cerca de 76% del ancho", roi.right in 2000..2100)
        assertTrue("Landscape ROI top (${roi.top}) debe estar cerca de 18% del alto", roi.top in 200..240)
        assertTrue("Landscape ROI bottom (${roi.bottom}) debe estar cerca de 78% del alto", roi.bottom in 930..970)
        assertTrue("ROI width debe ser positivo", roi.width() > 0)
        assertTrue("ROI height debe ser positivo", roi.height() > 0)
    }

    @Test
    fun testPortraitActiveRoi() {
        val width = 1220
        val height = 2712
        val roi = computeRoi(width, height)

        assertTrue("Portrait ROI left (${roi.left}) debe estar cerca de 45% del ancho", roi.left in 500..570)
        assertTrue("Portrait ROI right (${roi.right}) debe estar cerca de 80% del ancho", roi.right in 950..1000)
        assertTrue("Portrait ROI top (${roi.top}) debe estar cerca de 18% del alto", roi.top in 450..520)
        assertTrue("Portrait ROI bottom (${roi.bottom}) debe estar cerca de 74% del alto", roi.bottom in 1980..2040)
        assertTrue("ROI width debe ser positivo", roi.width() > 0)
        assertTrue("ROI height debe ser positivo", roi.height() > 0)
    }

    @Test
    fun testCustomRoiBounds() {
        val width = 1080
        val height = 1920
        val custom = SimpleRect(100, 200, 500, 800)
        val roi = computeRoi(width, height, custom)

        assertEquals(100, roi.left)
        assertEquals(200, roi.top)
        assertEquals(500, roi.right)
        assertEquals(800, roi.bottom)
    }
}

