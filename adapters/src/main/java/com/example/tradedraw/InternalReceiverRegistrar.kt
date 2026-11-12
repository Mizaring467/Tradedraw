package com.example.tradedraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/**
 * Registers internal command receivers without exposing them to third-party apps.
 *
 * The signature permission protects pre-33 devices. Android 13+ additionally
 * requires an explicit exported/not-exported flag for non-system broadcasts.
 */
object InternalReceiverRegistrar {
    const val PERMISSION = "com.example.tradedraw.permission.INTERNAL_COMMAND"

    internal fun receiverFlagForSdk(sdkInt: Int): Int? =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else null

    fun register(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
    ) {
        val flag = receiverFlagForSdk(Build.VERSION.SDK_INT)
        if (flag != null) {
            context.registerReceiver(
                receiver,
                filter,
                PERMISSION,
                null,
                flag,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                filter,
                PERMISSION,
                null,
            )
        }
    }
}
