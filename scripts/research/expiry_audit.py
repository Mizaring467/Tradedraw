#!/usr/bin/env python3
"""Infrastructure sensitivity only: grid entries are NOT strategy trades/edge evidence."""
import bisect
import collections
import json
from pathlib import Path
from scripts.research.inventory import parse
from scripts.research.manifests import write_once


def outcome(entry, exit_price, action):
    if exit_price is None: return 'NO_DATA'
    if entry == exit_price: return 'TRUE_TIE'
    return 'WIN' if ((exit_price > entry) == (action == 'BUY')) else 'LOSS'


def audit(record):
    path=Path(record['path'])
    rows,_,_,_=parse(path,path.read_bytes())
    ticks=[]
    for r in rows:
        try: ticks.append((int(r['ts']),r['ric'],float(r['price'])))
        except (KeyError,TypeError,ValueError): continue
    # Compare the historical sorted-first key with sorted SAME-asset future lookup.
    # Arrival-order discrepancies are counted separately, not silently erased.
    accepted=collections.defaultdict(list);watermarks={};ignored=0
    for t,a,p in ticks:
        if t<=watermarks.get(a,-1): ignored+=1;continue
        watermarks[a]=t;accepted[a].append((t,p))
    global_first={}
    for t,a,p in sorted(ticks,key=lambda x:x[0]): global_first.setdefault(t,(a,p))
    ts=sorted(global_first);ps=[global_first[t][1] for t in ts]
    trans=collections.Counter(); no_data=0
    for a,series in accepted.items():
        ats=[x[0] for x in series];aps=[x[1] for x in series]
        for t,entry in series:
            target=t+60_000
            left=bisect.bisect_right(ts,target)-1
            right=bisect.bisect_left(ats,target)
            old=ps[left] if left>=0 else None
            new=aps[right] if right<len(aps) else None
            for action in ('BUY','SELL'):
                trans[outcome(entry,old,action)+'->'+outcome(entry,new,action)]+=1
            if new is None:no_data+=1
    trades=[]
    replay_path=path.parent/'replay_journal.csv'
    if path.suffix=='.csv' and replay_path.exists():
        rr,_,_,_=parse(replay_path,replay_path.read_bytes())
        for row in rr:
            try:t=int(row['timestamp']);a=row['asset'];action=row['action']
            except (ValueError,KeyError):continue
            series=accepted.get(a,[]);ats=[s[0] for s in series]
            i=bisect.bisect_left(ats,t)
            if i>=len(ats) or ats[i]!=t:
                trades.append(dict(timestamp=t,status='NO_EXACT_ENTRY_TICK'));continue
            entry=series[i][1];j=bisect.bisect_left(ats,t+60000)
            exit_price=series[j][1] if j<len(series) else None
            old_i=bisect.bisect_right(ts,t+60000)-1
            old_result=outcome(entry,ps[old_i] if old_i>=0 else None,action)
            new_result=outcome(entry,exit_price,action)
            trades.append(dict(timestamp=t,asset=a,action=action,recorded_result=row['result'],legacy_recomputed=old_result,
                               runtime_result=new_result,exit_timestamp=series[j][0] if j<len(series) else None))
    return dict(dataset_id=record['dataset_id'],path=str(path),sha256=record['sha256'],
                purpose='DIAGNOSTIC_GRID_NOT_STRATEGY_PERFORMANCE',accepted_ticks=sum(map(len,accepted.values())),
                ignored_duplicate_or_stale=ignored,grid_transitions=dict(trans),grid_entries_without_future=no_data,
                historical_replay_rows=trades,claim_status='INVALID_UNDER_RUNTIME_SEMANTICS')


def main():
    records=json.loads(Path('loops/r29c/evidence/c0/inventory_v1.json').read_text())
    reports=[audit(r) for r in records if 'tick_rows' in r]
    write_once(Path('loops/r29c/evidence/expiry_audit_v1.json'),reports)
    total=collections.Counter()
    changes=collections.Counter()
    for r in reports:
        total.update(r['grid_transitions'])
        for t in r['historical_replay_rows']:
            if 'runtime_result' in t:changes[t['legacy_recomputed']+'->'+t['runtime_result']]+=1
    print('GRID',dict(total));print('RECORDED_REPLAY',dict(changes))

if __name__=='__main__': main()
