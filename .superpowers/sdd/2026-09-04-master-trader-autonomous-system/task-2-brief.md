# Task 2 Brief: Master Trader Signal Hierarchy & Trading Strategy Engine (TradingEngine.kt & AutoDrawEngine.kt)

## Objective
Implement hierarchical evaluation of Master Trader strategies in `TradingEngine.kt`, handle all 6 Master Trader strategies (`MT_MASTER_COMBO`, `MT_FALSE_BREAKOUT`, `MT_ENGULFING_SR`, `MT_REJECTION`, `MT_CHOQUE_PULLBACK`, `MT_3_VELAS_AGOTAMIENTO`), filter out trades in sideways markets, and synchronize strategy definitions with `AutoDrawEngine.kt`.

## Target Files
- Modify: `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
- Modify: `app/src/main/java/com/example/tradedraw/AutoDrawEngine.kt`
- Create: `app/src/test/java/com/example/tradedraw/TradingEngineTest.kt`

## Requirements
1. **`AutoTradeStrategy` Enum Definition**:
   Ensure `AutoTradeStrategy` in `AutoDrawEngine.kt` has the full set:
   - `MT_MASTER_COMBO`
   - `MT_FALSE_BREAKOUT`
   - `MT_ENGULFING_SR`
   - `MT_REJECTION`
   - `MT_CHOQUE_PULLBACK`
   - `MT_3_VELAS_AGOTAMIENTO`
   - `COLOR_TREND`
   - `STRIKE_BREAKOUT`
   - `AI_REMOTE`

2. **Hierarchical Strategy Evaluation in `TradingEngine.kt`**:
   - `MT_MASTER_COMBO`:
     1. False Breakout Trap in S/R (`isFalseBreakoutCall` -> BUY, `isFalseBreakoutPut` -> SELL)
     2. Rejection Wick in S/R (`isRejectionCall` -> BUY, `isRejectionPut` -> SELL)
     3. Engulfing Candle in S/R (`isEngulfingCall` -> BUY, `isEngulfingPut` -> SELL)
     4. Choque / Retest (`isChoqueCall` -> BUY, `isChoquePut` -> SELL)
     5. 3-Candle Exhaustion (`is3VelasCall` -> BUY, `is3VelasPut` -> SELL)
     6. High-Probability Trend (score $\ge 68\%$)
   - Sideways Market Gate: If `analysis.isMarketSideways == true`, suspend trades and output reason: `"⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad"`.
   - Prevent duplicate trade execution while `riskManager.hasPendingTrade` is true.

3. **Unit Tests in `TradingEngineTest.kt`**:
   - Test `MT_MASTER_COMBO` prioritization of False Breakout.
   - Test `MT_ENGULFING_SR` and `MT_REJECTION` signal firing.
   - Test sideways market rejection.

4. **Verification**:
   Run `./gradlew testDebugUnitTest --tests com.example.tradedraw.TradingEngineTest` and `./gradlew assembleDebug`.
