package com.example.tradedraw.core

enum class AccountType {
    UNKNOWN,
    DEMO,
    REAL
}

interface DemoAccountGuard {
    fun isDemoConfirmedFresh(maxAgeMs: Long = 120_000L): Boolean
    fun recordAccountObservation(accountType: AccountType, timestampMs: Long = System.currentTimeMillis())
    fun getAccountType(): AccountType
    fun isDispatchAllowed(): Boolean
    fun reset()
}

class StandardDemoAccountGuard(
    private val clock: () -> Long = { System.currentTimeMillis() },
    var maxAgeMs: Long = 120_000L
) : DemoAccountGuard {

    @Volatile
    private var observedType: AccountType = AccountType.UNKNOWN

    @Volatile
    private var lastObservedTs: Long = 0L

    @Volatile
    var ownerRealAccountAuthorized: Boolean = false

    override fun isDemoConfirmedFresh(maxAgeMs: Long): Boolean {
        val now = clock()
        val age = now - lastObservedTs
        return observedType == AccountType.DEMO && age in 0..maxAgeMs
    }

    override fun recordAccountObservation(accountType: AccountType, timestampMs: Long) {
        observedType = accountType
        lastObservedTs = timestampMs
    }

    override fun getAccountType(): AccountType = observedType

    override fun isDispatchAllowed(): Boolean {
        if (observedType == AccountType.REAL) {
            return ownerRealAccountAuthorized
        }
        return isDemoConfirmedFresh(maxAgeMs)
    }

    override fun reset() {
        observedType = AccountType.UNKNOWN
        lastObservedTs = 0L
    }
}

data class OrderRequest(
    val action: TradeAction,
    val x: Float,
    val y: Float,
    val decisionId: String? = null,
    val tradeId: String? = null,
    val bypassHud: Boolean = false,
    val mode: AutoTradeMode = AutoTradeMode.AUTONOMOUS
)

sealed class OrderReceipt {
    data class Success(
        val transport: String,
        val latencyMs: Long,
        val candleSecond: Int
    ) : OrderReceipt()

    data class Failure(
        val reason: String,
        val transport: String? = null
    ) : OrderReceipt()

    data class Vetoed(
        val reason: String
    ) : OrderReceipt()
}

interface TapTransport {
    val name: String
    fun isAvailable(): Boolean
    fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit)
}

interface OrderGateway {
    fun dispatch(request: OrderRequest, onResult: (OrderReceipt) -> Unit)
}
