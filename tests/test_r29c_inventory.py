import unittest
from pathlib import Path
from scripts.research.inventory import profile


class ResearchInventoryTest(unittest.TestCase):
    def test_utf16_and_timestamp_collision_preserve_observations(self):
        raw='ts,ric,price\n1000,A,100\n1000,B,200\n1000,A,100\n'.encode('utf-16')
        result,_=profile(Path('capture.csv'),raw)
        self.assertEqual(3,result['rows'])
        self.assertEqual(1,result['cross_asset_unique_keys_lost'])
        self.assertEqual(1,result['duplicate_asset_timestamp'])
        self.assertEqual(2,result['timestamp_only_drop_rows'])
        self.assertEqual('UNKNOWN',result['classification'])

    def test_wrapped_jsonl_counts_ticks_and_flags_bad_prices(self):
        raw=b'{"ticks":[{"ts":1000,"ric":"A","price":100},{"ts":900,"ric":"A","price":-1}]}\n'
        result,_=profile(Path('ticks.jsonl'),raw)
        self.assertEqual(2,result['rows'])
        self.assertEqual(1,result['invalid_prices'])
        self.assertEqual('OUT_OF_ORDER',result['monotonicity'])

    def test_malformed_csv_is_counted_without_crashing(self):
        result,_=profile(Path('bad.csv'),b'ts,ric,price\n1,A,2,unexpected\n')
        self.assertEqual(1,result['invalid_rows'])

    def test_all_frozen_production_sources_match_registered_bytes(self):
        import hashlib
        import json
        registry=json.loads(Path('loops/r29c/evidence/strategy_registry_v4.json').read_text())
        self.assertIn('COMBINED',{r['strategy_id'] for r in registry})
        for path,expected in registry[0]['supporting_sources'].items():
            with self.subTest(path=path):
                self.assertEqual(expected,hashlib.sha256(Path(path).read_bytes()).hexdigest())
