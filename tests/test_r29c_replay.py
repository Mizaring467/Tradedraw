import tempfile
import unittest
from pathlib import Path
from scripts.backtest import replay


class ReplaySettlementParityTest(unittest.TestCase):
    def test_settlement_boundary_vectors(self):
        cases = [([(69999, 99)], 'NO_DATA'), ([(70000, 101)], 'WIN'),
                 ([(70001, 101)], 'WIN'), ([(700000, 99)], 'LOSS'),
                 ([(70000, 100)], 'TIE'), ([], 'NO_DATA')]
        for future, expected in cases:
            with self.subTest(future=future):
                ticks=[(10000,100)]+future
                result=replay.resolve(100,replay.price_at(ticks,70000),'BUY')[0]
                self.assertEqual(expected,result)

    def test_multi_asset_input_is_rejected_not_merged(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'mixed.csv'
            p.write_text('ts,ric,price\n10000,A,100\n10000,B,200\n')
            with self.assertRaisesRegex(ValueError,'MIXED_ASSETS'):
                replay.load_ticks(p)

    def test_arrival_order_duplicate_and_stale_match_runtime(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'ticks.csv'
            p.write_text('ts,ric,price\n10000,A,100\n70001,A,99\n70000,A,101\n70001,A,102\n')
            ticks,ignored=replay.load_ticks(p)
            self.assertEqual([(10000,100),(70001,99)],ticks)
            self.assertEqual(2,ignored)
            self.assertEqual('LOSS',replay.run(ticks,[(10000,'BUY',100)])[0]['result'])

    def test_evaluator_rejects_missing_settlement_instead_of_deleting_it(self):
        from scripts.backtest import evaluate
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'trades.csv'
            p.write_text('timestamp,result\n10000,WIN\n20000,NO_DATA\n')
            with self.assertRaisesRegex(ValueError,'UNSCORABLE_RESULT'):
                evaluate.load_trades(p,'result','timestamp')
