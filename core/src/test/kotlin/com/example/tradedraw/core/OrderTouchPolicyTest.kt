package com.example.tradedraw.core

import org.junit.Assert.*
import org.junit.Test

class OrderTouchPolicyTest {
    @Test fun blocksOrdersInBothOrientationsAndPreservesNavigation() {
        assertTrue(OrderTouchPolicy.isOrderRegion(220f, 2440f, 1220, 2712))
        assertTrue(OrderTouchPolicy.isOrderRegion(2400f, 900f, 2712, 1220))
        assertFalse(OrderTouchPolicy.isOrderRegion(600f, 800f, 1220, 2712))
        assertFalse(OrderTouchPolicy.isOrderRegion(700f, 600f, 2712, 1220))
        assertTrue(OrderTouchPolicy.crossesOrderRegion(220f, 2440f, 220f, 800f, 1220, 2712))
        assertFalse(OrderTouchPolicy.crossesOrderRegion(220f, 1000f, 500f, 800f, 1220, 2712))
        assertTrue(OrderTouchPolicy.isOrderRegion(Float.NaN, 1f, 1220, 2712))
    }
}
