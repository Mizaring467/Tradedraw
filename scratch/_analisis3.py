# -*- coding: utf-8 -*-
import io, csv, os, re, statistics, sys, math
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
from collections import Counter, defaultdict

FILES = [
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_device_all.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\live\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\scratch\device_pull\trade_journal_20260917.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_device.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_v2.csv",
]
BASE13 = ['timestamp','iso_date','strategy','submode','action','confidence','price_y',
          'stake','base_balance','result','settled_balance','duration_sec','reason']

def read_text(f):
    raw = open(f, "rb").read()
    for e in ("utf-8-sig", "utf-16", "cp1252", "latin-1"):
        try:
            t = raw.decode(e)
            if 'timestamp' in t[:300].lower(): return t
        except Exception: pass
    return raw.decode("utf-8", "replace")

def norm(f):
    out = []
    for l in read_text(f).splitlines():
        if not l.strip(): continue
        r = next(csv.reader([l]))
        if len(r) < 13: continue
        r = [x.replace('\ufeff','').strip() for x in r]
        if not r[0].isdigit(): continue
        d = dict(zip(BASE13, r[:13]))
        d['reason'] = ','.join(r[12:]).strip().strip('"')
        out.append(d)
    return out

uni = {}
for f in FILES:
    if os.path.exists(f):
        for r in norm(f):
            if r['timestamp'] not in uni or len(r['reason']) > len(uni[r['timestamp']]['reason']):
                uni[r['timestamp']] = r
ALL = sorted(uni.values(), key=lambda r: int(r['timestamp']))

DIF = re.compile(r"Diff=([+-]?[0-9.]+)")
def dif(r):
    m = DIF.search(r['reason'])
    return float(m.group(1)) if m else None

print("### TEST HIPOTESIS SESGO INVERSO (CALL<->PUT)")
for a in ('BUY','SELL'):
    rows=[r for r in ALL if r['action']==a]
    w=sum(1 for r in rows if r['result']=='WIN'); l=sum(1 for r in rows if r['result']=='LOSS')
    t=sum(1 for r in rows if r['result']=='TIE')
    wr=w/(w+l)*100 if w+l else 0
    # p-valor binomial una cola vs 0.5 (azar puro)
    n=w+l; z=(w/n-0.5)/math.sqrt(0.25/n) if n else 0
    print("%s n=%3d W=%3d L=%3d TIE=%3d WR=%5.2f%%  z_vs_50%%=%+.3f" % (a,n,w,l,t,wr,z))
    print("   si se INVIERTE la senal -> WR=%5.2f%%" % (l/n*100 if n else 0))

print("\n### price_y por resultado (test 'vende en el suelo')")
for a in ('BUY','SELL'):
    for res in ('WIN','LOSS'):
        v=[float(r['price_y']) for r in ALL if r['action']==a and r['result']==res and float(r['price_y'])>0]
        if v:
            print("%s %-4s n=%3d  price_y med=%.1f  min=%.1f max=%.1f" % (a,res,len(v),statistics.median(v),min(v),max(v)))

print("\n### FALLOS DE EJECUCION (TIE / Diff=0.0)")
ties=[r for r in ALL if r['result']=='TIE']
print("TIE n=%d por submode:" % len(ties), Counter(r['submode'] for r in ties))
print("TIE por stake:", Counter(r['stake'] for r in ties))
print("TIE por dur_sec:", Counter(r['duration_sec'] for r in ties))
zero=[r for r in ALL if dif(r) is not None and abs(dif(r))<1e-9]
print("Diff=0.0 n=%d  de ellos TIE=%d" % (len(zero), sum(1 for r in zero if r['result']=='TIE')))
print("TIE con precio valido (price_y>0):", sum(1 for r in ties if float(r['price_y'])>0))
print("TIE con price_y=0.00 (senal no emitida):", sum(1 for r in ties if float(r['price_y'])==0))

print("\n### CONFIDENCE constante?")
print("valores:", Counter(r['confidence'] for r in ALL).most_common(6))

print("\n### WINRATE POR BLOQUE DE 40 TRADES (drift temporal)")
seq=sorted(ALL,key=lambda r:int(r['timestamp']))
for i in range(0,len(seq),40):
    blk=seq[i:i+40]
    w=sum(1 for r in blk if r['result']=='WIN'); l=sum(1 for r in blk if r['result']=='LOSS')
    print("trades %3d-%3d (%s a %s): W=%2d L=%2d WR=%5.1f%%  PnL=%9.0f" % (
        i+1,i+len(blk),blk[0]['iso_date'][5:16],blk[-1]['iso_date'][5:16],
        w,l,w/(w+l)*100 if w+l else 0, sum(dif(r) or 0 for r in blk)))

print("\n### CONSERVATIVE vs YOLO (todos los trades, no solo union)")
for f in FILES:
    if not os.path.exists(f): continue
    rows=norm(f)
    print("%-46s CONSERVATIVE=%d YOLO=%d" % (os.path.basename(f),
        sum(1 for r in rows if r['submode']=='CONSERVATIVE'), sum(1 for r in rows if r['submode']=='YOLO')))