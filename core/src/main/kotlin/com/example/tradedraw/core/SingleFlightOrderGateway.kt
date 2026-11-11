package com.example.tradedraw.core

/** Persist before dispatch. An unresolved transport survives process death and blocks new orders. */
interface PendingDispatchStore {
    fun load(): String?
    fun save(requestId: String?)
    fun wasDispatched(requestId: String): Boolean = false
    fun remember(requestId: String) {}
}

class MemoryPendingDispatchStore : PendingDispatchStore {
    private var id: String? = null
    private val dispatched = mutableSetOf<String>()
    override fun wasDispatched(requestId: String): Boolean = requestId in dispatched
    override fun remember(requestId: String) { dispatched.add(requestId) }
    override fun load(): String? = id
    override fun save(requestId: String?) { id = requestId }
}

/** Delivery acknowledgements are distinct from broker reconciliation. */
class SingleFlightOrderGateway(
    private val delegate: OrderGateway,
    private val store: PendingDispatchStore = MemoryPendingDispatchStore()
) : OrderGateway {
    private var pendingId: String? = store.load()
    private val terminal = mutableMapOf<String, OrderReceipt>()

    @Synchronized
    fun pendingRequestId(): String? = pendingId

    @Synchronized
    fun wasDispatched(requestId: String): Boolean = store.wasDispatched(requestId)

    /** Call only with positive evidence of this request's broker outcome. */
    @Synchronized
    fun reconcile(requestId: String): Boolean {
        if (requestId != pendingId) return false
        store.save(null)
        pendingId = null
        return true
    }

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val id = request.tradeId ?: request.decisionId
        if (id.isNullOrBlank()) {
            onResult(OrderReceipt.Vetoed("REQUEST_ID_REQUIRED"))
            return
        }
        synchronized(this) {
            terminal[id]?.let { onResult(it); return }
            if (store.wasDispatched(id)) {
                onResult(OrderReceipt.Vetoed("REQUEST_ALREADY_DISPATCHED"))
                return
            }
            if (pendingId != null) {
                onResult(OrderReceipt.Vetoed("ORDER_PENDING_RECONCILIATION"))
                return
            }
            // A failed durable reservation must never reach the transport.
            try { store.save(id); pendingId = id; store.remember(id) } catch (e: Exception) {
                onResult(OrderReceipt.Vetoed("DISPATCH_PERSISTENCE_FAILED"))
                return
            }
            pendingId = id
        }
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun complete(receipt: OrderReceipt) {
            if (!completed.compareAndSet(false, true)) return
            synchronized(this) {
                terminal[id] = receipt
                if (receipt is OrderReceipt.Vetoed ||
                    receipt is OrderReceipt.Failure && !receipt.requiresReconciliation) {
                    // If clearing fails the durable reservation remains fail-closed.
                    try { store.save(null); pendingId = null } catch (_: Exception) { }
                }
            }
            onResult(receipt)
        }
        try { delegate.dispatch(request, ::complete) } catch (e: Exception) {
            complete(OrderReceipt.Failure(e.message ?: "DISPATCH_EXCEPTION"))
        }
    }
}
