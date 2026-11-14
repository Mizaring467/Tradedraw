package com.example.tradedraw.core

/** Persist before dispatch. An unresolved transport survives process death and blocks new orders. */
interface PendingDispatchStore {
    fun load(): String?
    fun save(requestId: String?)
    fun wasDispatched(requestId: String): Boolean = false
    fun remember(requestId: String) {}
    fun reserve(request: OrderRequest) { save(request.tradeId ?: request.decisionId); remember(requireNotNull(request.tradeId ?: request.decisionId)) }
    fun reservation(): DispatchReservation? = load()?.let { DispatchReservation(it) }
    fun readReceipt(requestId: String): OrderReceipt? = null
    fun writeReceipt(requestId: String, receipt: OrderReceipt) {}
    fun lastEvidence(): BrokerDispatchEvidence? = null
    fun reviewReason(): String? = null
    fun setReviewReason(reason: String?) {}
    fun resolve(evidence: BrokerDispatchEvidence) { save(null) }
}

class MemoryPendingDispatchStore : PendingDispatchStore {
    private var id: String? = null
    private val dispatched = mutableSetOf<String>()
    private var reserved: DispatchReservation? = null
    private val receipts = mutableMapOf<String, OrderReceipt>()
    private var evidence: BrokerDispatchEvidence? = null
    private var review: String? = null
    override fun reviewReason() = review
    override fun setReviewReason(reason: String?) { review = reason }
    override fun reserve(request: OrderRequest) { val key = requireNotNull(request.tradeId ?: request.decisionId); save(key); remember(key); reserved = DispatchReservation(key, request.decisionId.orEmpty(), request.action, request.recoveryContext) }
    override fun reservation() = if (id == null) null else reserved ?: DispatchReservation(requireNotNull(id))
    override fun readReceipt(requestId: String) = receipts[requestId]
    override fun writeReceipt(requestId: String, receipt: OrderReceipt) { receipts[requestId] = receipt }
    override fun lastEvidence() = evidence
    override fun resolve(evidence: BrokerDispatchEvidence) { this.evidence = evidence; save(null) }
    override fun wasDispatched(requestId: String): Boolean = requestId in dispatched
    override fun remember(requestId: String) { dispatched.add(requestId) }
    override fun load(): String? = id
    override fun save(requestId: String?) { id = requestId; if (id == null) review = null }
}

/** Delivery acknowledgements are distinct from broker reconciliation. */
class SingleFlightOrderGateway(
    private val delegate: OrderGateway,
    private val store: PendingDispatchStore = MemoryPendingDispatchStore()
) : OrderGateway {
    private var pendingId: String? = store.load()
    private val terminal = mutableMapOf<String, OrderReceipt>()
    private var recovered = pendingId != null
    @Synchronized fun cachedReceipt(requestId: String): OrderReceipt? = terminal[requestId] ?: store.readReceipt(requestId)
    @Synchronized fun reservation(): DispatchReservation? = store.reservation()
    @Synchronized fun isRecovered(): Boolean = recovered && pendingId != null
    @Synchronized fun requiresReview(): Boolean = isRecovered() || store.reviewReason() != null || pendingId?.let { store.readReceipt(it) is OrderReceipt.Failure } == true
    @Synchronized fun lastEvidence(): BrokerDispatchEvidence? = store.lastEvidence()
    @Synchronized fun markUncertain(reason: String) {
        if (pendingId != null) store.setReviewReason(reason)
    }
    @Synchronized fun reconcileWithBrokerEvidence(evidence: BrokerDispatchEvidence, freshDemo: Boolean): Boolean {
        if (!freshDemo || evidence.brokerReference.isBlank() || evidence.observedAtMs <= 0) return false
        val previous = store.lastEvidence()
        if (pendingId == null) return previous == evidence
        if (evidence.requestId != pendingId) return false
        val created = store.reservation()?.context?.createdAtMs ?: 0L
        if (evidence.observedAtMs < created || evidence.outcome == BrokerRecoveredOutcome.NOT_PLACED && created > 0 && evidence.observedAtMs - created < 120_000L) return false
        store.resolve(evidence)
        pendingId = null; recovered = false
        return true
    }

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
        recovered = false
        return true
    }

    override fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit) {
        val id = request.tradeId ?: request.decisionId
        if (id.isNullOrBlank()) {
            onResult(OrderReceipt.Vetoed("REQUEST_ID_REQUIRED"))
            return
        }
        synchronized(this) {
            (terminal[id] ?: store.readReceipt(id))?.let { onResult(it); return }
            if (store.wasDispatched(id)) {
                onResult(OrderReceipt.Vetoed("REQUEST_ALREADY_DISPATCHED"))
                return
            }
            if (pendingId != null) {
                onResult(OrderReceipt.Vetoed("ORDER_PENDING_RECONCILIATION"))
                return
            }
            // A failed durable reservation must never reach the transport.
            try { store.reserve(request); pendingId = id } catch (e: Exception) {
                pendingId = store.load()
                onResult(OrderReceipt.Vetoed("DISPATCH_PERSISTENCE_FAILED"))
                return
            }
            pendingId = id
        }
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun complete(receipt: OrderReceipt) {
            if (!completed.compareAndSet(false, true)) return
            synchronized(this) {
                try { store.writeReceipt(id, receipt) } catch (_: Exception) { recovered = true }
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
