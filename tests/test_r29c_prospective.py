import json
import tempfile
import unittest
from pathlib import Path
from scripts.research.prospective import Journal,ingest_asset_observation,ingest_policy_observation,ingest_prediction,ingest_settlement,scorecard


class ProspectiveJournalTest(unittest.TestCase):
    def prediction(self):
        return dict(decisionId='D',timestamp=10000,asset='A',strategyVersion='frozen-v1',action='BUY',confidence=85,
                    reason='pre-outcome',regime='TRENDING',observed_payout=None,git_sha='frozen',entry_price=100)

    def test_immutable_prediction_then_candidate_settlement(self):
        with tempfile.TemporaryDirectory() as d:
            j=Journal(Path(d)/'capture.jsonl');p=self.prediction()
            first=ingest_prediction(j,p,11000)
            self.assertEqual(first,ingest_prediction(j,p,12000))
            p['action']='SELL';rejected=ingest_prediction(j,p,12000)
            self.assertEqual('PREDICTION_REJECTED',rejected['kind'])
            self.assertEqual('BUY',j.records()[0]['payload']['action'])
            settlement=dict(decisionId='D',asset='A',timestamp=70001,exit_price=101)
            result=ingest_settlement(j,settlement,71000)
            self.assertEqual('WIN',result['payload']['result'])
            self.assertEqual(result,ingest_settlement(j,settlement,72000))
            self.assertIsNone(scorecard(j)['WR'])  # imported candidate never becomes approved evidence

    def test_late_prediction_and_missing_metadata_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            j=Journal(Path(d)/'capture.jsonl')
            self.assertEqual('PREDICTION_REJECTED',ingest_prediction(j,self.prediction(),70000)['kind'])
            self.assertEqual('PREDICTION_REJECTED',ingest_prediction(j,{},11000)['kind'])
            self.assertEqual(0,scorecard(j)['precommitted_decisions'])

    def test_settlement_requires_same_asset_and_maturity(self):
        with tempfile.TemporaryDirectory() as d:
            j=Journal(Path(d)/'capture.jsonl');ingest_prediction(j,self.prediction(),11000)
            for settlement in [dict(decisionId='D',asset='B',timestamp=70001,exit_price=101),
                               dict(decisionId='D',asset='A',timestamp=69999,exit_price=101)]:
                with self.assertRaises(ValueError):ingest_settlement(j,settlement,71000)

    def test_tampering_breaks_chain(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'capture.jsonl';j=Journal(path)
            ingest_prediction(j,self.prediction(),11000)
            obj=json.loads(path.read_text());obj['payload']['action']='SELL'
            path.write_text(json.dumps(obj)+'\n')
            with self.assertRaisesRegex(ValueError,'JOURNAL_INTEGRITY_FAILURE'):j.records()


    def test_asset_observation_is_causal_deduplicated_and_not_replay_ready(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"j.jsonl")
            event={"atMs":1500,"kind":"ASSET","detail":'visible="Crypto IDX" ric="Z-CRY/IDX"'}
            first=ingest_asset_observation(j,event,epoch_start_ms=1000)
            second=ingest_asset_observation(j,event,epoch_start_ms=1000)
            self.assertEqual(first["sha256"],second["sha256"])
            self.assertIsNone(ingest_asset_observation(j,{**event,"atMs":900},epoch_start_ms=1000))
            card=scorecard(j)
            self.assertEqual(1,card["active_asset_timeline"]["observations"])
            self.assertEqual(["Z-CRY/IDX"],card["active_asset_timeline"]["assets"])
            self.assertFalse(card["active_asset_timeline"]["supports_mixed_asset_replay"])
