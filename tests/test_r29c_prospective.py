import json
import tempfile
import unittest
from pathlib import Path
from scripts.research.prospective import Journal,collector_bundle_sha256,export_active_asset_timeline,ingest_asset_observation,ingest_policy_observation,ingest_prediction,ingest_settlement,scorecard,unique_epoch_code_sha


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


    def test_timeline_export_is_sealed_versioned_and_exclusive_create(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"journal.jsonl")
            j.append("TRANSPORT",{"endpoint":"/status","response":{"gitSha":"abc123"}},1000)
            ingest_asset_observation(
                j,
                {"atMs":1100,"kind":"ASSET","detail":'visible="Crypto IDX" ric="Z-CRY/IDX"'},
                epoch_start_ms=1000,
            )
            out=Path(tmp)/"timeline.json"
            manifest=export_active_asset_timeline(j,out,2500)
            self.assertEqual(1,manifest["schema_version"])
            self.assertEqual("abc123",manifest["code_sha"])
            self.assertEqual(2500,manifest["max_age_ms"])
            self.assertEqual(collector_bundle_sha256(),manifest["collector_sha256"])
            self.assertEqual("Z-CRY/IDX",manifest["observations"][0]["ric"])
            self.assertTrue(out.exists())
            with self.assertRaises(FileExistsError):
                export_active_asset_timeline(j,out,2500)

    def test_asset_observation_dedup_is_scoped_by_epoch(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"journal.jsonl")
            event={"atMs":1500,"kind":"ASSET","detail":'visible="Crypto IDX" ric="Z-CRY/IDX"'}
            a=ingest_asset_observation(j,event,epoch_start_ms=1000,epoch_id="epoch-a")
            b=ingest_asset_observation(j,event,epoch_start_ms=1000,epoch_id="epoch-b")
            self.assertNotEqual(a["sha256"],b["sha256"])
            observations=[r for r in j.records() if r["kind"]=="ACTIVE_ASSET_OBSERVATION"]
            self.assertEqual(["epoch-a","epoch-b"],[r["payload"]["epoch_id"] for r in observations])

    def test_timeline_export_requires_one_complete_epoch_and_never_mixes_sessions(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"journal.jsonl")

            def append_epoch(epoch_id,start_ms,code_sha,ric):
                j.append(
                    "CAPTURE_EPOCH_START",
                    {"epoch_id":epoch_id,"started_at_ms":start_ms,"collector_sha256":"fixture"},
                    start_ms,
                )
                j.append(
                    "TRANSPORT",
                    {"epoch_id":epoch_id,"endpoint":"/status","response":{"gitSha":code_sha}},
                    start_ms+1,
                )
                ingest_asset_observation(
                    j,
                    {"atMs":start_ms+2,"kind":"ASSET","detail":f'visible="{ric}" ric="{ric}"'},
                    epoch_start_ms=start_ms,
                    epoch_id=epoch_id,
                )
                j.append(
                    "CAPTURE_EPOCH_END",
                    {"epoch_id":epoch_id,"status":"COMPLETE","ended_at_ms":start_ms+3},
                    start_ms+3,
                )

            append_epoch("epoch-a",1000,"sha-a","A")
            append_epoch("epoch-b",2000,"sha-b","B")

            with self.assertRaisesRegex(ValueError,"NO_UNIQUE_COMPLETE_CAPTURE_EPOCH"):
                export_active_asset_timeline(j,Path(tmp)/"ambiguous.json",2500)

            first=export_active_asset_timeline(
                j,Path(tmp)/"a.json",2500,epoch_id="epoch-a"
            )
            second=export_active_asset_timeline(
                j,Path(tmp)/"b.json",2500,epoch_id="epoch-b"
            )
            self.assertEqual("epoch-a",first["capture_epoch_id"])
            self.assertEqual("sha-a",first["code_sha"])
            self.assertEqual(["A"],[row["ric"] for row in first["observations"]])
            self.assertEqual("epoch-b",second["capture_epoch_id"])
            self.assertEqual("sha-b",second["code_sha"])
            self.assertEqual(["B"],[row["ric"] for row in second["observations"]])

    def test_timeline_export_rejects_incomplete_explicit_epoch(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"journal.jsonl")
            j.append(
                "CAPTURE_EPOCH_START",
                {"epoch_id":"epoch-a","started_at_ms":1000,"collector_sha256":"fixture"},
                1000,
            )
            j.append(
                "TRANSPORT",
                {"epoch_id":"epoch-a","endpoint":"/status","response":{"gitSha":"sha-a"}},
                1001,
            )
            with self.assertRaisesRegex(ValueError,"CAPTURE_EPOCH_NOT_COMPLETE"):
                export_active_asset_timeline(
                    j,Path(tmp)/"incomplete.json",2500,epoch_id="epoch-a"
                )

    def test_timeline_export_rejects_mixed_build_epoch(self):
        with tempfile.TemporaryDirectory() as tmp:
            j=Journal(Path(tmp)/"journal.jsonl")
            j.append("TRANSPORT",{"endpoint":"/status","response":{"gitSha":"a"}},1000)
            j.append("TRANSPORT",{"endpoint":"/status","response":{"gitSha":"b"}},1001)
            self.assertRaisesRegex(ValueError,"NO_UNIQUE_EPOCH_CODE_SHA",unique_epoch_code_sha,j.records())
