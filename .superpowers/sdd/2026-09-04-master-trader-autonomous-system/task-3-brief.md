# Task 3 Brief: Thread-Safe Risk & Session State Manager (RiskManager.kt)

## Objective
Implement thread-safe session state tracking with `@Volatile` and `@Synchronized` locks, Martingale progression (M0 -> M1 -> M2), Stop Loss / Take Profit bounds, 10s cooldown enforcement, and 50s-75s trade lifecycle protection in `RiskManager.kt`. Add comprehensive unit tests in `RiskManagerTest.kt`.

## Target Files
- Modify: `app/src/main/java/com/example/tradedraw/RiskManager.kt`
- Create: `app/src/test/java/com/example/tradedraw/RiskManagerTest.kt`

## Requirements
1. **Thread-Safe State**:
   - Fields: `totalWins`, `totalLosses`, `consecutiveLosses`, `currentMartingaleIndex`, `hasPendingTrade`, `tradeOpenedTimestamp`, `lastTradeResultTimestamp`.
   - Protect all state mutations (`recordTradeWin()`, `recordTradeLoss()`, `notifyTradePlaced()`, `resetSession()`, `canTrade()`) with `@Synchronized`.
2. **Martingale Management**:
   - Progression: Base stake $S$, M1 ($S \times 2.2$), M2 ($S \times 2.2^2$).
   - Win resets to M0; loss advances M0 -> M1 -> M2 -> M0.
3. **Safety Locks & Cooldowns**:
   - Disallow trading when `hasPendingTrade == true` unless $> 50\text{s}$ elapsed (orphan trade cleanup).
   - Enforce minimum 10s cooldown following trade completion.
   - Stop Loss trigger when `consecutiveLosses >= maxLossStreak`.
   - Take Profit trigger when `totalWins >= targetWins`.
4. **Unit Tests**:
   - Create `app/src/test/java/com/example/tradedraw/RiskManagerTest.kt` verifying:
     - Anti-duplicate trade lock when `hasPendingTrade == true`.
     - Martingale progression on losses and reset on win.
     - Stop Loss streak triggering.
     - Session reset functionality.
