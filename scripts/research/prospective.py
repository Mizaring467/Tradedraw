#!/usr/bin/env python3
"""Append-only prospective evidence capture. GET-only loopback monitor; never trades.
Raw transport evidence is kept even when prediction metadata is incomplete.
Canonical predictions need a structured SHADOW_PREDICTION event; legacy events
are retained as unscorable rather than reconstructed using post-outcome data.
"""
import argparse
import collections
import fcntl
import hashlib
import json
import math
import os
import time
import urllib.request
from pathlib import Path

from scripts.research.active_asset_timeline import build_timeline_manifest, parse_asset_event, timeline_summary
from scripts.research.policy_context_coverage import coverage_summary, observed_context_from_status, observation_summary

REQUIRED = {'decisionId','timestamp','asset','strategyVersion','action','confidence','reason','regime','observed_payout','git_sha','entry_price'}
ZERO='0'*64


def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False).encode()


def validate_chain(lines):
    records=[];previous=ZERO
    for i,line in enumerate(lines,1):
        record=json.loads(line)
        digest=record.pop('sha256')
        if record.get('sequence')!=i or record.get('previous_sha256')!=previous or hashlib.sha256(canonical(record)).hexdigest()!=digest:
            raise ValueError(f'JOURNAL_INTEGRITY_FAILURE at {i}')
        record['sha256']=digest;records.append(record);previous=digest
    return records


class Journal:
    def __init__(self,path):
        self.path=Path(path);self.path.parent.mkdir(parents=True,exist_ok=True)

    def records(self):
        if not self.path.exists():return []
        with self.path.open('rb') as handle:
            fcntl.flock(handle,fcntl.LOCK_SH)
            return validate_chain(handle.readlines())

    def append(self,kind,payload,recorded_at_ms=None):
        with self.path.open('a+b') as handle:
            fcntl.flock(handle,fcntl.LOCK_EX)
            handle.seek(0);records=validate_chain(handle.readlines())
            record=dict(sequence=len(records)+1,previous_sha256=records[-1]['sha256'] if records else ZERO,
                        kind=kind,recorded_at_ms=time.time_ns()//1_000_000 if recorded_at_ms is None else recorded_at_ms,payload=payload)
            record['sha256']=hashlib.sha256(canonical(record)).hexdigest()
            handle.seek(0,2);handle.write(canonical(record)+b'\n');handle.flush();os.fsync(handle.fileno())
            return record


def prediction_errors(prediction,received_ms):
    errors=[]
    missing=sorted(REQUIRED-set(prediction))
    if missing:return ['MISSING_FIELDS:'+','.join(missing)]
    try:
        ts=prediction['timestamp'];price=prediction['entry_price'];conf=prediction['confidence']
        if not isinstance(ts,int) or not 0<=received_ms-ts<60_000:errors.append('NOT_OBSERVED_BEFORE_EXPIRY')
        if prediction['action'] not in {'BUY','SELL'}:errors.append('INVALID_ACTION')
        if not isinstance(price,(int,float)) or not math.isfinite(price) or price<=0:errors.append('INVALID_PRICE')
        if not isinstance(conf,(int,float)) or not math.isfinite(conf):errors.append('INVALID_CONFIDENCE')
        if not prediction['asset'] or not prediction['decisionId'] or not prediction['strategyVersion'] or not prediction['git_sha']:errors.append('MISSING_IDENTITY')
        if str(prediction['asset']).startswith('TEST/') or str(prediction['decisionId']).startswith('probe-'):errors.append('SYNTHETIC_PROBE')
        payout=prediction['observed_payout']
        if payout is not None and (not isinstance(payout,(int,float)) or not math.isfinite(payout) or not 0<=payout<=1):errors.append('INVALID_OBSERVED_PAYOUT')
    except (KeyError,TypeError,ValueError):errors.append('INVALID_SCHEMA')
    return errors


def ingest_prediction(journal,prediction,received_ms=None):
    received_ms=time.time_ns()//1_000_000 if received_ms is None else received_ms
    errors=prediction_errors(prediction,received_ms)
    records=journal.records()
    previous=[r for r in records if r['kind']=='PREDICTION' and r['payload'].get('decisionId')==prediction.get('decisionId')]
    if previous:
        if previous[0]['payload']==prediction:return previous[0]
        errors.append('CONFLICTING_IMMUTABLE_DECISION')
    if errors:return journal.append('PREDICTION_REJECTED',dict(prediction=prediction,errors=errors),received_ms)
    return journal.append('PREDICTION',prediction,received_ms)


def ingest_asset_observation(journal,event,epoch_start_ms):
    parsed=parse_asset_event(event)
    if parsed is None or parsed["atMs"] < epoch_start_ms:
        return None
    records=journal.records()
    for row in records:
        if row["kind"]=="ACTIVE_ASSET_OBSERVATION":
            payload=row["payload"]
            if payload.get("atMs")==parsed["atMs"] and payload.get("ric")==parsed["ric"] and payload.get("visibleAsset")==parsed["visibleAsset"]:
                return row
    return journal.append("ACTIVE_ASSET_OBSERVATION",parsed,parsed["atMs"])


def ingest_policy_observation(journal,prediction,status,received_ms=None):
    received_ms=time.time_ns()//1_000_000 if received_ms is None else received_ms
    decision_id=prediction.get('decisionId')
    if not decision_id:return None
    records=journal.records()
    existing=[r for r in records if r['kind']=='POLICY_CONTEXT_OBSERVATION' and r['payload'].get('decisionId')==decision_id]
    if existing:return existing[0]
    payload=observed_context_from_status(prediction,status,received_ms)
    return journal.append('POLICY_CONTEXT_OBSERVATION',payload,received_ms)


def ingest_settlement(journal,settlement,received_ms=None):
    received_ms=time.time_ns()//1_000_000 if received_ms is None else received_ms
    records=journal.records()
    decisions=[r for r in records if r['kind']=='PREDICTION' and r['payload']['decisionId']==settlement.get('decisionId')]
    if len(decisions)!=1:raise ValueError('NO_UNIQUE_PRECOMMITTED_DECISION')
    decision=decisions[0];p=decision['payload']
    if settlement.get('asset')!=p['asset']:raise ValueError('ASSET_MISMATCH')
    ts=settlement.get('timestamp');price=settlement.get('exit_price')
    if not isinstance(ts,int) or ts<p['timestamp']+60_000 or received_ms<ts or decision['recorded_at_ms']>=ts:
        raise ValueError('INELIGIBLE_SETTLEMENT_TIME')
    if not isinstance(price,(int,float)) or not math.isfinite(price) or price<=0:raise ValueError('INVALID_EXIT_PRICE')
    # Imported settlement is retained but not scored until first-eligible-tick evidence is reconciled.
    result='TRUE_TIE' if price==p['entry_price'] else ('WIN' if ((price>p['entry_price'])==(p['action']=='BUY')) else 'LOSS')
    payload=dict(settlement,result=result,verification='REQUIRES_RAW_TICK_PARITY_RECONCILIATION')
    old=[r for r in records if r['kind']=='SETTLEMENT_CANDIDATE' and r['payload']['decisionId']==p['decisionId']]
    if old:
        if old[0]['payload']==payload:return old[0]
        raise ValueError('CONFLICTING_SETTLEMENT')
    return journal.append('SETTLEMENT_CANDIDATE',payload,received_ms)


def scorecard(journal):
    records=journal.records();counts=collections.Counter(r['kind'] for r in records)
    predictions=[r['payload'] for r in records if r['kind']=='PREDICTION']
    observations=[r['payload'] for r in records if r['kind']=='POLICY_CONTEXT_OBSERVATION']
    asset_observations=[r['payload'] for r in records if r['kind']=='ACTIVE_ASSET_OBSERVATION']
    n=len(predictions)
    policy_coverage=coverage_summary(predictions)
    observed_coverage=observation_summary(observations)
    asset_timeline=timeline_summary(asset_observations)
    return dict(evidence_class='PROSPECTIVE_SHADOW',records=len(records),precommitted_decisions=n,
                settlement_candidates=counts['SETTLEMENT_CANDIDATE'],rejected_predictions=counts['PREDICTION_REJECTED'],
                data_quality_failures=counts['CAPTURE_ERROR'],verified_settlements=0,WIN=None,LOSS=None,TRUE_TIE=None,
                NO_DATA=None,WR=None,EV=None,max_drawdown=None,longest_loss_streak=None,rejection_rate=None,
                runtime_discrepancies=None,execution_policy_context=policy_coverage,
                policy_context_observations=observed_coverage,active_asset_timeline=asset_timeline,
                status='INSUFFICIENT_EVIDENCE',
                limitation='No automatic performance approval: reconcile raw ticks, lineage, registry, payout, full ExecutionPolicy context and runtime settlement first. Status snapshots are partial pre-outcome observations, never exact decision context. Missing settlements remain pending, never ties.')


def unique_epoch_code_sha(records):
    shas=set()
    for row in records:
        if row.get("kind")!="TRANSPORT":
            continue
        payload=row.get("payload")
        if not isinstance(payload,dict) or payload.get("endpoint")!="/status":
            continue
        response=payload.get("response")
        if isinstance(response,dict):
            sha=response.get("gitSha")
            if isinstance(sha,str) and sha.strip():
                shas.add(sha.strip())
    if len(shas)!=1:
        raise ValueError("NO_UNIQUE_EPOCH_CODE_SHA")
    return next(iter(shas))


def collector_bundle_sha256():
    paths=[
        Path(__file__),
        Path(__file__).with_name("active_asset_timeline.py"),
        Path(__file__).with_name("policy_context_coverage.py"),
    ]
    digest=hashlib.sha256()
    for path in sorted(paths,key=lambda item:item.name):
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def export_active_asset_timeline(journal,out_path,max_age_ms):
    records=journal.records()
    manifest=build_timeline_manifest(
        records,
        max_age_ms=max_age_ms,
        code_sha=unique_epoch_code_sha(records),
        collector_sha256=collector_bundle_sha256(),
    )
    out=Path(out_path)
    out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("x",encoding="utf-8") as handle:
        json.dump(manifest,handle,sort_keys=True,separators=(",",":"))
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    return manifest


def capture(journal,samples,interval):
    # Explicitly fixed loopback; redirect handling disabled to prevent leaving ADB tunnel.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs):return None
    opener=urllib.request.build_opener(NoRedirect())
    seen=set()
    epoch_start_ms=time.time_ns()//1_000_000
    initial_status=None
    latest_status=None
    for i in range(samples):
        for endpoint in ('/status','/simulation','/events','/ticks?max=2000'):
            try:
                with opener.open('http://127.0.0.1:8080'+endpoint,timeout=5) as response:
                    value=json.loads(response.read())
                journal.append('TRANSPORT',dict(endpoint=endpoint,response=value))
                if endpoint=='/status':
                    latest_status=value
                    if initial_status is None:
                        initial_status=value
                    elif value.get('gitSha')!=initial_status.get('gitSha'):
                        journal.append('CAPTURE_ERROR',dict(reason='BUILD_CHANGED_DURING_EPOCH'))
                        return
                if endpoint=='/simulation':
                    for prediction in value.get('pendingDecisions',[]):
                        if prediction.get('timestamp',-1)<epoch_start_ms:
                            continue  # never relabel previously open/recovered orders as a new cohort
                        if not initial_status or initial_status.get('mode')!='SHADOW' or not value.get('enabled'):
                            journal.append('CAPTURE_ERROR',dict(reason='NOT_ACTIVE_SHADOW',prediction=prediction))
                            continue
                        if prediction.get('git_sha')!=initial_status.get('gitSha'):
                            journal.append('CAPTURE_ERROR',dict(reason='BUILD_IDENTITY_MISMATCH',prediction=prediction))
                            continue
                        accepted=ingest_prediction(journal,prediction)
                        if accepted.get('kind')=='PREDICTION' and latest_status is not None:
                            ingest_policy_observation(journal,prediction,latest_status)
                if endpoint=='/events':
                    for event in value.get('events',[]):
                        key=hashlib.sha256(canonical(event)).hexdigest()
                        if key in seen:continue
                        seen.add(key)
                        if event.get('kind')=='ASSET':
                            if initial_status and initial_status.get('mode')=='SHADOW':
                                ingest_asset_observation(journal,event,epoch_start_ms)
                        elif event.get('kind')=='SHADOW_PREDICTION':
                            try:ingest_prediction(journal,json.loads(event['detail']))
                            except (ValueError,TypeError):journal.append('CAPTURE_ERROR',dict(reason='INVALID_PREDICTION_EVENT',event=event))
                        elif event.get('kind')=='SHADOW_DECISION':
                            journal.append('UNSCORABLE_LEGACY_DECISION',dict(event=event,reason='No canonical pre-outcome metadata; do not infer after settlement'))
            except Exception as error:
                journal.append('CAPTURE_ERROR',dict(endpoint=endpoint,error=type(error).__name__,detail=str(error)))
        if i+1<samples:time.sleep(interval)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=['capture','scorecard','export-timeline'])
    p.add_argument('--journal',required=True)
    p.add_argument('--samples',type=int,default=1)
    p.add_argument('--interval',type=float,default=2.0)
    p.add_argument('--timeline-out')
    p.add_argument('--max-age-ms',type=int)
    args=p.parse_args()
    if args.samples<1 or not 0<args.interval<=60:p.error('samples >=1, interval in (0,60]')
    j=Journal(args.journal)
    if args.mode=='capture':
        capture(j,args.samples,args.interval)
        print(json.dumps(scorecard(j),indent=2))
    elif args.mode=='scorecard':
        print(json.dumps(scorecard(j),indent=2))
    else:
        if not args.timeline_out or args.max_age_ms is None or args.max_age_ms<0:
            p.error('export-timeline requires --timeline-out and --max-age-ms >= 0')
        manifest=export_active_asset_timeline(j,args.timeline_out,args.max_age_ms)
        print(json.dumps(manifest,indent=2))

if __name__=='__main__':main()
