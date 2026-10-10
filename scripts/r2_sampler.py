import json, time, urllib.request, os, sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

STATUS_URL = "http://127.0.0.1:8080/status"
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r2_engine_samples.jsonl"
DURATION = 25 * 60
INTERVAL = 20

os.makedirs(os.path.dirname(OUT), exist_ok=True)
start = time.time()
n = 0
with open(OUT, "w", encoding="utf-8") as f:
    while time.time() - start < DURATION + 30:
        try:
            req = urllib.request.Request(STATUS_URL, headers={"User-Agent": "SamplerR2"})
            with urllib.request.urlopen(req, timeout=2) as resp:
                d = json.loads(resp.read().decode())
            row = {
                "t": round(time.time() - start, 2),
                "ts": time.strftime('%H:%M:%S'),
                "engine": d.get("engine"),
                "feed": {k: d.get("feed", {}).get(k) for k in ("connected","isFresh","lastTickAgeMs","rawSocketMessages","socketTicks","activeAsset")},
                "analysis": {k: d.get("analysis", {}).get(k) for k in ("trend","callPower","putPower","candleCount","isMarketSideways")},
                "pending": d.get("risk", {}).get("hasPendingTrade"),
            }
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            f.flush()
            n += 1
        except Exception as e:
            f.write(json.dumps({"t": round(time.time()-start,2), "error": str(e)}, ensure_ascii=False) + "\n")
            f.flush()
        time.sleep(INTERVAL)
print(f"Sampler done: {n} muestras -> {OUT}")
