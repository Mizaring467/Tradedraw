# -*- coding: utf-8 -*-
import os, csv, io, statistics, datetime, json

FILES = [
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_device_all.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\live\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\scratch\device_pull\trade_journal_20260917.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_device.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_v2.csv",
]

def load(f):
    raw = open(f, "rb").read()
    for e in ("utf-8-sig", "utf-16", "cp1252", "latin-1"):
        try:
            txt = raw.decode(e)
            if "timestamp" in txt[:200].lower():
                break
        except Exception:
            continue
    # normalizar saltos
    rdr = csv.reader(io.StringIO(txt))
    rows = [r for r in rdr if r and any(x.strip() for x in r)]
    if not rows:
        return [], []
    hdr = [h.strip() for h in rows[0]]
    recs = []
    for r in rows[1:]:
        if len(r) != len(hdr):
            continue
        recs.append(dict(zip(hdr, r)))
    return hdr, recs

for f in FILES:
    if not os.path.exists(f):
        print("NO EXISTE:", f); continue
    hdr, recs = load(f)
    print("=" * 90)
    print(f)
    print("cols(%d): %s" % (len(hdr), hdr))
    print("registros parseados: %d" % len(recs))
    if recs:
        print("rango fechas: %s -> %s" % (recs[0].get("iso_date"), recs[-1].get("iso_date")))
        print("estrategias:", sorted(set(r.get("strategy","") for r in recs)))
        print("submodes:", sorted(set(r.get("submode","") for r in recs)))
        print("acciones:", sorted(set(r.get("action","") for r in recs)))
        print("resultados:", sorted(set(r.get("result","") for r in recs)))
        print("stakes:", sorted(set(r.get("stake","") for r in recs)))