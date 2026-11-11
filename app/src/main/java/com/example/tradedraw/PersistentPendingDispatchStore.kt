package com.example.tradedraw

import android.content.Context
import com.example.tradedraw.core.PendingDispatchStore

class PersistentPendingDispatchStore(context: Context) : PendingDispatchStore {
    private val prefs = context.getSharedPreferences("TradeDraw_Dispatch", Context.MODE_PRIVATE)
    override fun wasDispatched(requestId: String): Boolean =
        requestId in prefs.getStringSet("dispatchedIds", emptySet()).orEmpty()
    override fun remember(requestId: String) {
        val ids = prefs.getStringSet("dispatchedIds", emptySet()).orEmpty().toMutableSet()
        ids.add(requestId)
        check(prefs.edit().putStringSet("dispatchedIds", ids).commit())
    }
    override fun load(): String? = prefs.getString("pendingRequestId", null)
    override fun save(requestId: String?) {
        check(prefs.edit().putString("pendingRequestId", requestId).commit()) { "Dispatch state write failed" }
    }
}
