import unittest
from scripts.validation.shadow_journal_reader import parse_shadow_rows


class ShadowJournalReaderTest(unittest.TestCase):
    def test_real_bridge_tail_mixed_headers_and_precise_v2(self):
        lines = ["timestamp,iso_date,strategy,submode,action,confidence,price_y,trend,candle_second,decision_id,reason",
                 '1,old,S,M,BUY,0.8,100,T,0,d1,"historic"',
                 '2,new,S,M,SELL,0.8,1.00000000001,T,0,d2,"tradeId=t2;asset=A",2,TIE,1.00000000001,1000.0']
        rows = parse_shadow_rows(lines)
        self.assertEqual([1, 2], [r["schema_version"] for r in rows])
        self.assertEqual(1.00000000001, rows[1]["sim_exit_price"])
        self.assertIn("tradeId=t2", rows[1]["reason"])

    def test_future_version_is_explicitly_rejected(self):
        with self.assertRaises(ValueError):
            parse_shadow_rows(['2,new,S,M,BUY,0.8,1,T,0,d,"x",3,WIN,2,1000'])
