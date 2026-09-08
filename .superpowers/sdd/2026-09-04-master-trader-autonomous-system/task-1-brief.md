# Task 1 Brief: Master Trader Price Action Detection Core (VisionAnalyzer.kt)

## Objective
Implement and verify all Master Trader price action strategies (False Breakout / S/R Traps, Engulfing at S/R, Rejection Wicks, Choque/Pullback, 3-Candle Exhaustion) and Sideways / Doji market filters in `VisionAnalyzer.kt`. Add comprehensive unit tests in `VisionAnalyzerTest.kt`.

## Requirements
1. **Contiguous Run Color Segmentation**: Segment green (`#00E676` / `#22C55E`, Hue 120°-160°) and red (`#FF5252` / `#EF4444`, Hue 345°-15°) pixels requiring vertical runs $\ge 6\text{px}$ to filter out 1-2px moving average indicator lines.
2. **Dynamic Support & Resistance Extraction**: Compute `minPriceY` (Resistance) from the lowest Y among confirmed candle tops and `maxPriceY` (Support) from the highest Y among confirmed candle bottoms.
3. **Master Trader Strategy Flags in `AnalysisResult`**:
   - `isFalseBreakoutCall`: Lower wick penetrates support by $\ge 8\text{px}$, but candle close retreats strictly above support.
   - `isFalseBreakoutPut`: Upper wick penetrates resistance by $\ge 8\text{px}$, but candle close retreats strictly below resistance.
   - `isEngulfingCall`: Green candle body $\ge 1.3\times$ previous red candle body, closing above previous open, occurring at support ($\le 30\text{px}$).
   - `isEngulfingPut`: Red candle body $\ge 1.3\times$ previous green candle body, closing below previous open, occurring at resistance ($\le 30\text{px}$).
   - `isRejectionCall` / `isRejectionPut`: Lower/upper wick $\ge 35\%$ of total candle range bouncing off S/R.
   - `isChoqueCall` / `isChoquePut`: Retest touch on previously broken support/resistance level.
   - `is3VelasCall` / `is3VelasPut`: 3 consecutive decreasing bodies ($V1 > V2 > V3$, $V3 < 0.4\times V1$) indicating exhaustion.
   - `isMarketSideways`: Average body of last 10 candles $< 15\text{px}$ or $\ge 50\%$ dojis.
4. **Unit Test Coverage**: Create `app/src/test/java/com/example/tradedraw/VisionAnalyzerTest.kt` verifying all pattern detection algorithms.

## Target Files
- Modify: `app/src/main/java/com/example/tradedraw/VisionAnalyzer.kt`
- Create: `app/src/test/java/com/example/tradedraw/VisionAnalyzerTest.kt`
