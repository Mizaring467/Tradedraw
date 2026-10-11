import time, json, urllib.request, sys, subprocess

URL = "http://127.0.0.1:8080/status"
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r4_engine_samples.jsonl"
DUR = 25 * 60
start = time.time()
n = 0
while time.time() - start < DUR:
    t = time.strftime("%Y-%m-%d %H:%M:%S")
    try:
        with urllib.request.urlopen(URL, timeout=4) as r:
            s = json.load(r)
        e = s.get('engine', {}) or {}
        a = s.get('analysis', {}) or {}
        rec = {
            "n": n, "ts": t, "elapsed": int(time.time() - start),
            "signalsGenerated": e.get('signalsGenerated'),
            "evaluatedCount": e.get('evaluatedCount'),
            "suppressedByConfluence": e.get('suppressedByConfluence'),
            "lastSignalAction": e.get('lastSignalAction'),
            "balance": s.get('balance'),
            "trend": a.get('trend'),
            "hasPendingTrade": (s.get('risk', {}) or {}).get('hasPendingTrade'),
        }
    except Exception as ex:
        rec = {"n": n, "ts": t, "elapsed": int(time.time() - start), "error": str(ex)[:80]}
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(rec, flush=True)
    n += 1
    time.sleep(20)
print("SAMPLER DONE n=", n, flush=True)
