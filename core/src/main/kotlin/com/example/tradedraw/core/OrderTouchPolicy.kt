package com.example.tradedraw.core

/** Conservative order region. Raw navigation must not reach broker order controls. */
object OrderTouchPolicy {
    fun isOrderRegion(x: Float, y: Float, width: Int, height: Int): Boolean {
        if (!x.isFinite() || !y.isFinite() || width <= 0 || height <= 0) return true
        return if (width > height) x >= width * 0.80f else y >= height * 0.80f
    }
    fun crossesOrderRegion(x1: Float, y1: Float, x2: Float, y2: Float, width: Int, height: Int): Boolean =
        isOrderRegion(x1, y1, width, height) || isOrderRegion(x2, y2, width, height)
}
