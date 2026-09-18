# -*- coding: utf-8 -*-
import os, json, sys
FILES = [
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_device_all.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\loops\journal\live\trade_journal_live.csv",
 r"C:\Users\heidy\Tradedraw\scratch\device_pull\trade_journal_20260917.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_device.csv",
 r"C:\Users\heidy\Tradedraw\scratch\journal_v2.csv",
]
out = []
for f in FILES:
    out.append("===== " + f + " =====")
    if not os.path.exists(f):
        out.append("NO EXISTE"); continue
    out.append("SIZE=%d" % os.path.getsize(f))
    with open(f, "rb") as fh:
        raw = fh.read()
    enc = None
    for e in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            txt = raw.decode(e); enc = e; break
        except Exception:
            pass
    out.append("ENC=%s" % enc)
    lines = txt.splitlines()
    out.append("NLINES=%d" % len(lines))
    for i, l in enumerate(lines[:4]):
        out.append("L%d| %s" % (i + 1, l[:400]))
    if len(lines) > 4:
        out.append("LAST| %s" % lines[-1][:400])
    out.append("")
print("\n".join(out))