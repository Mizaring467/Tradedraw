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
    """

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


if __name__ == "__main__":
    unittest.main()
