#!/usr/bin/env python3
"""Read-only R29-C inventory. Unknown provenance is never promoted by filename.
Output is a derived snapshot, not independent market evidence. Stdlib only.
"""
import argparse
import collections
import csv
import datetime as dt
import hashlib
import io
import json
import math
import os
from pathlib import Path

EXCLUDED = {'.git', 'build', '.gradle', '__pycache__', 'node_modules', 'graft', '.venv', 'venv', '.cache', '.agents', '.codex'}
EXTENSIONS = {'.csv', '.json', '.jsonl', '.ndjson', '.parquet', '.sqlite', '.sqlite3', '.db', '.log'}
TIME_KEYS = ('ts', 'timestamp', 'timestamp_ms', 'atMs', 'decision_ts', 'entryTimestampMs', 'entry_ts')
ASSET_KEYS = ('ric', 'asset', 'assetRic', 'symbol')
PRICE_KEYS = ('price', 'entry_price', 'price_at_signal', 'entryPrice', 'price_y')


def first(row, keys):
    return next((row[k] for k in keys if row.get(k) not in (None, '')), None)


def event_ms(value):
    if value is None:
        return None
    try:
        n = float(value)
        if not math.isfinite(n) or n < 0:
            return None
        # Keep original units documented; epoch milliseconds inferred only by magnitude.
        return int(n * 1000) if 1e9 <= n < 1e11 else int(n)
    except (TypeError, ValueError):
        try:
            d = dt.datetime.fromisoformat(str(value).replace('Z', '+00:00'))
            return int(d.timestamp() * 1000) if d.tzinfo else None
        except ValueError:
            return None


def unpack(obj):
    if isinstance(obj, list):
        return obj
    if isinstance(obj, dict):
        # Preserve wrapper metadata separately; count actual records, not only wrappers.
        for key in ('ticks', 'events', 'records', 'trades', 'decisions', 'samples', 'rows'):
            if isinstance(obj.get(key), list):
                return obj[key]
        return [obj]
    return [obj]


def parse(path, raw):
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    text = raw.decode(encoding, errors='strict')
    if path.suffix == '.csv':
        reader = csv.DictReader(io.StringIO(text))
        rows = list(reader)
        errors = sum(None in r or any(v is None for v in r.values()) for r in rows)
        return rows, reader.fieldnames or [], encoding, errors
    if path.suffix in {'.json', '.jsonl', '.ndjson'}:
        try:
            obj = json.loads(text)
            return unpack(obj), list(obj) if isinstance(obj, dict) else [], encoding, 0
        except json.JSONDecodeError:
            rows, errors = [], 0
            for line in text.splitlines():
                if not line.strip():
                    continue
                try:
                    rows.extend(unpack(json.loads(line)))
                except json.JSONDecodeError:
                    errors += 1
            return rows, [], encoding, errors
    return [], [], encoding, 0


def profile(path, raw):
    sha = hashlib.sha256(raw).hexdigest()
    result = dict(dataset_id='ds_' + hashlib.sha256(str(path).encode()).hexdigest()[:16], path=str(path),
                  sha256=sha, size_bytes=len(raw), classification='UNKNOWN', provenance='UNVERIFIED',
                  quality_status='INSUFFICIENT_METADATA', rows=None, schema=[], assets=[], timezone='UNKNOWN',
                  timestamp_resolution_ms=None, timestamp_min_ms=None, timestamp_max_ms=None,
                  monotonicity='NOT_APPLICABLE', duplicate_rows=None, duplicate_asset_timestamp=None,
                  gaps_over_10s=None, invalid_prices=None, invalid_rows=None, result_decision_fields=[],
                  parent_candidates=[], independence='UNVERIFIED')
    if path.suffix in {'.sqlite', '.sqlite3', '.db', '.parquet'}:
        result['parse_status'] = 'REQUIRES_FORMAT_READER'
        return result, []
    try:
        rows, wrapper, encoding, errors = parse(path, raw)
    except (UnicodeError, csv.Error) as e:
        result.update(parse_status=type(e).__name__, quality_status='REJECTED')
        return result, []
    result.update(encoding=encoding, rows=len(rows), invalid_rows=errors, parse_status='PARSED')
    if path.suffix == '.log':
        result.update(rows=len(raw.splitlines()), parse_status='UNSTRUCTURED_LOG', schema=['text_line'])
        return result, []
    schema = sorted({str(k) for row in rows if isinstance(row, dict) for k in row if k is not None})
    result['schema'] = schema
    result['wrapper_schema'] = wrapper
    result['result_decision_fields'] = [k for k in schema if any(s in k.lower() for s in ('result', 'decision', 'action', 'outcome', 'settle', 'payout'))]
    signatures = [json.dumps({str(k):v for k,v in r.items()} if isinstance(r,dict) else r, sort_keys=True, ensure_ascii=True) for r in rows]
    result['duplicate_rows'] = len(signatures) - len(set(signatures))
    ticks, times, assets, bad_prices, bad_times = [], [], set(), 0, 0
    for row in rows:
        if not isinstance(row, dict):
            continue
        t = first(row, TIME_KEYS)
        ts = event_ms(t)
        asset = first(row, ASSET_KEYS)
        if asset is not None:
            assets.add(str(asset))
        if t is not None:
            if ts is None:
                bad_times += 1
            else:
                times.append(ts)
        price = first(row, PRICE_KEYS)
        if price is not None:
            try:
                p = float(price)
                if not math.isfinite(p) or p <= 0:
                    raise ValueError('invalid price')
                if ts is not None and asset is not None and 'ts' in row and 'price' in row:
                    ticks.append((ts, str(asset), p))
            except (ValueError, TypeError):
                bad_prices += 1
    result.update(assets=sorted(assets), invalid_prices=bad_prices, invalid_timestamps=bad_times)
    if times:
        unique = sorted(set(times))
        deltas = [b-a for a,b in zip(unique, unique[1:])]
        result.update(timestamp_min_ms=min(times), timestamp_max_ms=max(times),
                      timezone='UTC_EPOCH (numeric magnitude inference)' if all(isinstance(first(r,TIME_KEYS),(int,float)) or str(first(r,TIME_KEYS)).replace('.','').isdigit() for r in rows if isinstance(r,dict) and first(r,TIME_KEYS) is not None) else 'EXPLICIT_OFFSET_OR_UNKNOWN',
                      timestamp_resolution_ms=math.gcd(*deltas) if deltas else None,
                      monotonicity='NONDECREASING' if times == sorted(times) else 'OUT_OF_ORDER',
                      gaps_over_10s=sum(d > 10000 for d in deltas))
    if ticks:
        keys = [(a,t) for t,a,p in ticks]
        by_time = collections.defaultdict(set)
        for t,a,p in ticks:
            by_time[t].add(a)
        per_asset = collections.defaultdict(list)
        for t,a,p in ticks:
            per_asset[a].append(t)
        result.update(tick_rows=len(ticks), duplicate_asset_timestamp=len(keys)-len(set(keys)),
                      timestamp_only_drop_rows=len(ticks)-len(set(t for t,a,p in ticks)),
                      cross_asset_collision_timestamps=sum(len(a)>1 for a in by_time.values()),
                      cross_asset_unique_keys_lost=sum(len(a)-1 for a in by_time.values()),
                      per_asset_gaps_over_10s={a:sum(y-x>10000 for x,y in zip(sorted(set(ts)),sorted(set(ts))[1:])) for a,ts in per_asset.items()})
    # Classification is deliberately NOT inferred from path. Manual evidence overlays are versioned.
    return result, signatures


def inventory(root):
    records, rowsets = [], {}
    for directory, dirs, files in os.walk(root):
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED and not (Path(directory)/d).is_symlink())
        for name in sorted(files):
            full = Path(directory)/name
            rel = full.relative_to(root)
            if full.is_symlink() or rel.parts[:2] in {('loops','r29c'), ('datasets','manifests')} or rel.parts[0] == 'experiments':
                continue
            if full.suffix.lower() not in EXTENSIONS:
                continue
            record, signatures = profile(rel, full.read_bytes())
            records.append(record)
            if signatures:
                rowsets[str(rel)] = set(signatures)
    by_hash = collections.defaultdict(list)
    for r in records:
        by_hash[r['sha256']].append(r['path'])
    duplicates = [v for v in by_hash.values() if len(v)>1]
    for r in records:
        r['byte_identical_paths'] = [p for p in by_hash[r['sha256']] if p != r['path']]
        if r['byte_identical_paths']:
            r['independence'] = 'NOT_INDEPENDENT_BYTE_COPY'
    # Exact record overlap is diagnostic, not proof of which file is parent.
    overlaps = []
    csv_paths = [r['path'] for r in records if r['path'].endswith('.csv') and r['path'] in rowsets]
    for i,a in enumerate(csv_paths):
        for b in csv_paths[i+1:]:
            intersection = len(rowsets[a] & rowsets[b])
            if intersection:
                overlaps.append(dict(left=a,right=b,shared_unique_records=intersection,left_unique=len(rowsets[a]),right_unique=len(rowsets[b])))
    return records, duplicates, overlaps


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    args=parser.parse_args()
    root=Path.cwd()
    records, duplicates, overlaps=inventory(root)
    out=Path(args.out)
    out.mkdir(parents=True,exist_ok=True)
    for name,obj in [('inventory_v1.json',records),('exact_duplicates_v1.json',duplicates),('record_overlaps_v1.json',overlaps)]:
        target=out/name
        payload=json.dumps(obj,indent=2,ensure_ascii=False)+'\n'
        if target.exists() and target.read_text()!=payload:
            raise SystemExit(f'Refusing to overwrite immutable snapshot {target}; choose new output directory')
        target.write_text(payload)
    print(json.dumps(dict(files=len(records),bytes=sum(r['size_bytes'] for r in records),exact_duplicate_groups=len(duplicates),overlap_pairs=len(overlaps),tick_datasets=sum('tick_rows' in r for r in records),parse_failures=sum(r['parse_status'] not in {'PARSED','UNSTRUCTURED_LOG'} for r in records))))

if __name__=='__main__':
    main()
