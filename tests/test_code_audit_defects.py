"""
Regression tests reproducing the defects identified in FASE 1 & FASE 3 of TradeDraw audit:
1. SyntheticCandleEngine.kt: TFI update order bug (deltaP = tick.price - lastTickPrice after lastTickPrice was updated).
2. RiskManager.kt: YOLO takeProfitWins bypass and null subMode fallback bug.
"""

import unittest


class TestSyntheticCandleEngineTFIDefect(unittest.TestCase):
    """
    Reproduces and verifies the fix for tickFlowImbalance (TFI) calculation order bug.
    """

    def simulate_tfi_old_logic(self, ticks):
        """
        Old logic from SyntheticCandleEngine.kt:
        lastTickPrice was updated at line 147 BEFORE deltaP was calculated at line 168.
        """
        last_tick_price = 0.0
        window_up_ticks = 0
        window_down_ticks = 0
        tfi = 0.0

        for tick_price, sec in ticks:
            # Line 147: premature update
            last_tick_price = tick_price

            # Line 167-174:
            if 40 <= sec <= 59 and last_tick_price > 0.0:
                delta_p = tick_price - last_tick_price  # Always 0.0!
                eps = last_tick_price * 0.0000005
                if delta_p > eps:
                    window_up_ticks += 1
                elif delta_p < -eps:
                    window_down_ticks += 1
                total = window_up_ticks + window_down_ticks
                tfi = (window_up_ticks - window_down_ticks) / total if total >= 4 else 0.0

        return tfi, window_up_ticks, window_down_ticks

    def simulate_tfi_fixed_logic(self, ticks):
        """
        Fixed logic:
        Preserve prev_price before updating last_tick_price.
        """
        last_tick_price = 0.0
        window_up_ticks = 0
        window_down_ticks = 0
        tfi = 0.0

        for tick_price, sec in ticks:
            prev_price = last_tick_price
            last_tick_price = tick_price

            if 40 <= sec <= 59 and prev_price > 0.0:
                delta_p = tick_price - prev_price
                eps = prev_price * 0.0000005
                if delta_p > eps:
                    window_up_ticks += 1
                elif delta_p < -eps:
                    window_down_ticks += 1
                total = window_up_ticks + window_down_ticks
                tfi = (window_up_ticks - window_down_ticks) / total if total >= 4 else 0.0

        return tfi, window_up_ticks, window_down_ticks

    def test_reproduce_tfi_always_zero_in_old_logic(self):
        # 10 strongly bullish ticks between sec 40 and 50
        ticks = [(100.0 + i * 0.5, 40 + i) for i in range(10)]

        tfi_old, up_old, down_old = self.simulate_tfi_old_logic(ticks)
        # In old logic, deltaP is identically 0.0, so no ticks are registered
        self.assertEqual(up_old, 0)
        self.assertEqual(down_old, 0)
        self.assertEqual(tfi_old, 0.0)

        # In fixed logic, 9 upward movements are registered and TFI is +1.0 (100% buy pressure)
        tfi_fixed, up_fixed, down_fixed = self.simulate_tfi_fixed_logic(ticks)
        self.assertEqual(up_fixed, 9)
        self.assertEqual(down_fixed, 0)
        self.assertAlmostEqual(tfi_fixed, 1.0, places=4)


class TestRiskManagerDefects(unittest.TestCase):
    """
    Reproduces and verifies the fix for:
    1. YOLO bypassing takeProfitWins
    2. subMode=null ignoring currentSubMode
    3. Double count on WIN / LOSS resolution race condition (e.g. 7 -> 9 instead of 7 -> 8)
    """

    class OldRiskManagerWithRaceCondition:
        """
        Old implementation before R24/B1 (7be78e8 / 6fc51f4):
        Manual resolution (HUD/bridge) called recordTradeWins(force=true) and cleared pending trade,
        but did NOT mark the trade as liquidated.
        When async handler.post evaluated, resolvePendingTradeWin() returned False,
        and fallback unconditionally invoked recordTradeWins(1, force=true),
        causing total_wins to jump by 2 (e.g. 7 -> 9 instead of 7 -> 8).
        """
        def __init__(self, initial_wins=7):
            self.total_wins = initial_wins
            self.has_pending_trade = False
            self.pending_trade_id = None

        def record_trade_sent(self, trade_id):
            self.has_pending_trade = True
            self.pending_trade_id = trade_id

        def settle_manual_old(self, win=True):
            if win:
                self.total_wins += 1
            self.has_pending_trade = False
            # Bug: did not mark trade_id as liquidated!
            return True

        def engine_post_fallback_old(self, captured_trade_id, win=True):
            if not self.has_pending_trade:
                # Fallback in old code called recordTradeWin(force=True) unconditionally
                if win:
                    self.total_wins += 1
                return True
            return False

    class FixedRiskManagerWithIdempotency:
        """
        Fixed implementation with settleTradeOnce and settleManual (7be78e8):
        Tracks liquidated trade keys in a set and ensures each tradeId/startMs is only settled once.
        """
        def __init__(self, initial_wins=7):
            self.total_wins = initial_wins
            self.has_pending_trade = False
            self.pending_trade_id = None
            self.liquidated_keys = set()
            self.manual_wins = 0

        def record_trade_sent(self, trade_id):
            self.has_pending_trade = True
            self.pending_trade_id = trade_id

        def mark_liquidated(self, trade_id):
            key = trade_id if trade_id else "ts:0"
            if key in self.liquidated_keys:
                return False
            self.liquidated_keys.add(key)
            return True

        def settle_trade_once(self, trade_id, win=True):
            if not self.mark_liquidated(trade_id):
                return False  # Already liquidated, deduplicate!
            if win:
                self.total_wins += 1
            return True

        def settle_manual(self, trade_id, win=True):
            if trade_id:
                self.mark_liquidated(trade_id)
            if win:
                self.total_wins += 1
                self.manual_wins += 1
            self.has_pending_trade = False
            return True

        def engine_post_fallback(self, captured_trade_id, win=True):
            if not self.has_pending_trade:
                return self.settle_trade_once(captured_trade_id, win=win)
            return False

    class OldRiskManager:
        def __init__(self, current_sub_mode="NORMAL", take_profit_wins=3):
            self.current_sub_mode = current_sub_mode
            self.take_profit_wins = take_profit_wins
            self.current_wins = 0

        def can_execute_trade(self, sub_mode=None):
            # Old implementation: checked sub_mode directly without fallback
            if sub_mode == "YOLO":
                # Returned immediately without checking take_profit_wins!
                return True, "🚀 MODO YOLO: Operativa continua sin límites"

            if self.take_profit_wins > 0 and self.current_wins >= self.take_profit_wins:
                return False, f"Take Profit alcanzado ({self.take_profit_wins} victorias)"

            return True, "Listo para operar"

    class FixedRiskManager:
        def __init__(self, current_sub_mode="NORMAL", take_profit_wins=3):
            self.current_sub_mode = current_sub_mode
            self.take_profit_wins = take_profit_wins
            self.current_wins = 0

        def can_execute_trade(self, sub_mode=None):
            # Fixed implementation:
            # 1. Resolve effective sub mode
            effective_sub_mode = sub_mode if sub_mode is not None else self.current_sub_mode

            # 2. Check session limits (takeProfitWins) before mode-specific bypass
            if self.take_profit_wins > 0 and self.current_wins >= self.take_profit_wins:
                return False, f"Take Profit alcanzado ({self.take_profit_wins} victorias)"

            if effective_sub_mode == "YOLO":
                return True, "🚀 MODO YOLO: Operativa continua sin límites"

            return True, "Listo para operar"

    def test_reproduce_yolo_bypasses_take_profit_in_old(self):
        old_rm = self.OldRiskManager(take_profit_wins=3)
        old_rm.current_wins = 3

        # Old logic in YOLO returns True despite reaching target
        allowed, msg = old_rm.can_execute_trade(sub_mode="YOLO")
        self.assertTrue(allowed, "Old logic erroneously allowed trade")
        self.assertIn("MODO YOLO", msg)

        # Fixed logic properly stops at take profit
        fixed_rm = self.FixedRiskManager(take_profit_wins=3)
        fixed_rm.current_wins = 3
        allowed_fixed, msg_fixed = fixed_rm.can_execute_trade(sub_mode="YOLO")
        self.assertFalse(allowed_fixed, "Fixed logic properly halts on Take Profit")
        self.assertIn("Take Profit", msg_fixed)

    def test_reproduce_null_submode_bug_in_old(self):
        old_rm = self.OldRiskManager(current_sub_mode="YOLO")
        # When sub_mode is not passed (None), old logic fell through to normal mode
        allowed, msg = old_rm.can_execute_trade(sub_mode=None)
        self.assertEqual(msg, "Listo para operar")  # Missed YOLO logic entirely!

        # Fixed logic uses effective_sub_mode
        fixed_rm = self.FixedRiskManager(current_sub_mode="YOLO")
        allowed_fixed, msg_fixed = fixed_rm.can_execute_trade(sub_mode=None)
        self.assertIn("MODO YOLO", msg_fixed)

    def test_reproduce_double_count_anomaly_in_old(self):
        """
        Demuestra la anomalía donde una victoria duplicaba el contador: 7 -> 9 en vez de 7 -> 8.
        """
        rm = self.OldRiskManagerWithRaceCondition(initial_wins=7)
        trade_id = "trade-xyz"
        rm.record_trade_sent(trade_id)

        # 1. Operador o bridge liquida manualmente (HUD o /win):
        rm.settle_manual_old(win=True)
        self.assertEqual(rm.total_wins, 8)

        # 2. Asíncronamente, el handler.post del motor ejecuta el fallback:
        rm.engine_post_fallback_old(captured_trade_id=trade_id, win=True)

        # En el código antiguo, total_wins saltaba a 9 (anomalía 7 -> 9)
        self.assertEqual(rm.total_wins, 9)

    def test_verify_double_count_anomaly_fixed_in_idempotent(self):
        """
        Verifica que con la idempotencia por tradeId (settleManual + settleTradeOnce),
        la victoria única incrementa exactamente a 8 (7 -> 8) y el fallback queda descartado.
        """
        rm = self.FixedRiskManagerWithIdempotency(initial_wins=7)
        trade_id = "trade-xyz"
        rm.record_trade_sent(trade_id)

        # 1. Operador o bridge liquida manualmente:
        rm.settle_manual(trade_id=trade_id, win=True)
        self.assertEqual(rm.total_wins, 8)

        # 2. Motor ejecuta fallback asíncrono con el mismo trade_id:
        fallback_counted = rm.engine_post_fallback(captured_trade_id=trade_id, win=True)

        # El fallback es rechazado por clave duplicada
        self.assertFalse(fallback_counted)
        self.assertEqual(rm.total_wins, 8)  # Se mantiene exactamente en 8, NO salta a 9



if __name__ == "__main__":
    unittest.main()
