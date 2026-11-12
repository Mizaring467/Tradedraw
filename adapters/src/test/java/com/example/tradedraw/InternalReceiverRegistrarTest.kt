package com.example.tradedraw

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalReceiverRegistrarTest {
    @Test
    fun preTiramisuUsesSignaturePermissionWithoutModernFlag() {
        assertNull(InternalReceiverRegistrar.receiverFlagForSdk(32))
        assertEquals(
            "com.example.tradedraw.permission.INTERNAL_COMMAND",
            InternalReceiverRegistrar.PERMISSION,
        )
    }

    @Test
    fun tiramisuAndNewerAreExplicitlyNotExported() {
        assertEquals(
            Context.RECEIVER_NOT_EXPORTED,
            InternalReceiverRegistrar.receiverFlagForSdk(33),
        )
        assertEquals(
            Context.RECEIVER_NOT_EXPORTED,
            InternalReceiverRegistrar.receiverFlagForSdk(36),
        )
    }
}
