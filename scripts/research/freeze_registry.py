#!/usr/bin/env python3
"""Snapshot production source identities from frozen baseline, never edit strategy bytes."""
import hashlib
import json
import re
import subprocess
from pathlib import Path
from scripts.research.manifests import write_once

BASE='4fdb6466d5005499789d801d64916ea9c15421a3'


def main():
    paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'strategy/src/main','core/src/main','market/src/main','risk/src/main','execution/src/main','app/src/main/java/com/example/tradedraw/TradingEngine.kt'],text=True).splitlines()
    hashes={p:hashlib.sha256(subprocess.check_output(['git','show',BASE+':'+p])).hexdigest() for p in paths if p.endswith('.kt')}
    bundle=hashlib.sha256(json.dumps(hashes,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    enum=subprocess.check_output(['git','show',BASE+':core/src/main/kotlin/com/example/tradedraw/core/AutoTradeStrategy.kt'],text=True)
    ids=re.findall(r'^\s+([A-Z][A-Z_0-9]+)\b',enum,re.M)
    registry=[]
    for sid in ids:
        registry.append(dict(strategy_id=sid,name=sid,version='r29c-frozen-4fdb646',git_sha=BASE,
            source_file='strategy/src/main/java/com/example/tradedraw/FrozenStrategySignals.kt',
            supporting_sources=hashes,parameter_hash=bundle,
            parameter_hash_scope='SHA256 of canonical path->full-source-SHA map; conservative superset of parameters, includes policy and engine',
            status='FROZEN',research_eligible=False,
            limitation='AI remote model/provider identity unpinned' if sid=='AI_REMOTE' else 'Research data and full execution parity gates not passed'))
    write_once(Path('loops/r29c/evidence/strategy_registry_v2.json'),registry)
    assert all(hashlib.sha256(Path(p).read_bytes()).hexdigest()==h for p,h in hashes.items()),'Frozen production bytes changed'
    print('FROZEN',len(registry),'modes',len(hashes),'source hashes',bundle)

if __name__=='__main__':main()
