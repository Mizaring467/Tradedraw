# VETO PROFILER REPORT — R31 THROUGHPUT ARCHITECTURE

## 1. Executive Summary & Objective
During R31 live operation, TradeDraw demonstrated live DEMO execution capability but suffered from severely depressed trade frequency (~2 trades resolved in 21 minutes, or ~5.7 trades/hour). To understand why TradeDraw rejects opportunities without silently degrading strategy alpha, we implemented **VetoProfiler**: an in-memory, zero-allocation telemetry engine featuring a closed veto taxonomy, multi-stage conversion funnel accounting, and real-time HTTP diagnostic endpoints.

## 2. Closed Veto Taxonomy
Every signal evaluation is classified into exactly one of 14 deterministic categories:

| Category | Stage | Description & Trigger Condition |
| :--- | :--- | :--- |
| `TIMING` | Funnel Step 3 | Rejected by `MarketTickFilters.isInEntryWindow()` (candle second outside :01–:05). |
| `COOLDOWN` | Funnel Step 6 | Rejected by `RiskManager.isCooldownActive`, `isLossCooldownActive`, or spacing. |
| `CANDLE_ALREADY_EXECUTED` | Funnel Step 6 | Rejected by `CandleRateLimiter` (already traded on this candle ID). |
| `ADAPTIVE_BLOCK` | Funnel Step 5 | Blocked by `AdaptiveLearningEngine.predictLossProbability() >= threshold` (0.99). |
| `CHOP` | Funnel Step 4 | Filtered by regime classification (e.g., ADX < 20, ranging volatility compression). |
| `RISK_LIMIT` | Funnel Step 6 | Maximum daily drawdown or max loss streak exceeded. |
| `ASSET_MISMATCH` | Funnel Step 2 | Tick symbol does not match target asset configuration. |
| `BROKER_ABSENT` | Funnel Step 6 | Broker accessibility service not bound or UI not in foreground. |
| `WS_STALE` | Funnel Step 1 | WebSocket tick stream latency > threshold (stale market data). |
| `NO_DEMO` | Funnel Step 6 | Demo guard tri-state: blocked when account is `DEMO_UNKNOWN` or `REAL_CONFIRMED`. |
| `PENDING_TRADE` | Funnel Step 6 | Rejected because an active trade is awaiting resolution (`MAX_PENDING_TRADES = 1`). |
| `SR_CONFLICT` | Funnel Step 4 | Signal clashes with immediate Support/Resistance barrier. |
| `BUY_EXHAUSTION` | Funnel Step 4 | Price overbought/oversold relative to local Bollinger/Donchian band. |
| `OTHER` | Fallback | Unclassified or custom rule rejection. |

## 3. Conversion Funnel Metrics
The conversion funnel tracks signals through 8 discrete operational phases:

$$\text{TICKS} \longrightarrow \text{EVALUATIONS} \longrightarrow \text{CANDIDATE SIGNALS} \longrightarrow \text{PASSED TIMING} \longrightarrow \text{PASSED STRATEGY} \longrightarrow \text{PASSED ADAPTIVE} \longrightarrow \text{PASSED RISK} \longrightarrow \text{AUTHORIZED} \longrightarrow \text{TAP} \longrightarrow \text{SETTLED}$$

### Funnel Mathematical Formulations:
1. **Evaluation Rate**: $\frac{\text{evaluations}}{\text{ticks}}$
2. **Candidate Rate**: $\frac{\text{candidate\_signals}}{\text{evaluations}}$
3. **Timing Pass Rate**: $\frac{\text{passed\_timing}}{\text{candidate\_signals}}$
4. **Strategy Pass Rate**: $\frac{\text{passed\_strategy}}{\text{passed\_timing}}$
5. **Adaptive Pass Rate**: $\frac{\text{passed\_adaptive}}{\text{passed\_strategy}}$
6. **Risk Pass Rate**: $\frac{\text{passed\_risk}}{\text{passed\_adaptive}}$
7. **Execution Rate**: $\frac{\text{taps}}{\text{authorized}}$
8. **Conversion Rate**: $\frac{\text{authorized}}{\text{evaluations}}$

## 4. Telemetry Schema & Payload Structure
The telemetry structure captures every candidate evaluation with full decision context:

```json
{
  "timestamp": 1700000000000,
  "asset": "CRYPTO_IDX",
  "strategy": "AUTO_ADAPTIVE",
  "submode": "CONSERVATIVE",
  "action": "BUY",
  "confidence": 0.88,
  "candleSecond": 3,
  "marketRegime": "TRENDING_UP",
  "signalScore": 0.92,
  "vetoIndex": 2,
  "vetoReason": "COOLDOWN:loss_streak_active",
  "allowed": false
}
```

## 5. Diagnostic HTTP Endpoints
Implemented in `TradeDrawHttpBridge.kt`:

1. **`GET /diagnostics/veto_stats`**:
   Exposes live totals, candidate counts, authorized trades, conversion rate, full veto breakdown dictionary, and funnel progression metrics.
2. **`GET /diagnostics/no_trade`**:
   Exposes the operational state when no trade has executed for > 180 seconds, detailing active blockers (TIMING %, COOLDOWN %, ADAPTIVE %), remaining cooldown in seconds, entry window status, WebSocket health, and DEMO verification status.

## 6. Implementation Verification
- Code location: `observability/src/main/java/com/example/tradedraw/VetoProfiler.kt`.
- Integration points: `TradingEngine.kt` (`onMarketTick`, `executeHeadlessTrade`, `dispatchBrokerOrder`, `checkHeadlessTradeResolution`).
- Unit tests: `observability/src/test/java/com/example/tradedraw/VetoProfilerTest.kt` verifying all 14 categories, funnel counters, ratio calculations, and JSON serialization. Passed in Gradle test suite.
