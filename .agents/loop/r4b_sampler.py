import subprocess, time, json, urllib.request, os

ADB = ["adb", "-s", "UGU8KFUKM7E6CE8D"]
URL = "http://127.0.0.1:8080/status"
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r4b_samples.jsonl"
IMGDIR = r"C:\Users\heidy\Tradedraw\.agents\loop"
N = 16
INTERVAL = 65

def now(): return time.strftime("%Y-%m-%d %H:%M:%S")

def get_status():
    try:
        with urllib.request.urlopen(URL, timeout=6) as r:
            return json.load(r)
    except Exception as e:
        return {"error": str(e)[:80]}

def logcat_entradas():
    try:
        out = subprocess.run(ADB + ["logcat", "-d"], capture_output=True, text=True, errors="replace", timeout=20).stdout
    except Exception as e:
        return {"err": str(e)[:80], "lines": [], "segs": []}
    lines = [l for l in out.splitlines() if "ENTRADA despachada" in l]
    segs = []
    for l in lines:
        for tok in l.split():
            if tok.startswith("segundo="):
                segs.append(tok)
    return {"lines": lines[-20:], "segs": segs}

for n in range(1, N + 1):
    ts = now()
    st = get_status()
    png = os.path.join(IMGDIR, f"r4b_hud_{n}.png")
    with open(png, "wb") as f:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.DEVNULL)
    lg = logcat_entradas()
    rec = {"n": n, "ts": ts, "png": png,
           "status": {k: st.get(k) for k in ["balance"]},
           "analysis": {k: (st.get("analysis") or {}).get(k) for k in ["trend", "callPower", "putPower", "currentPriceY"]},
           "engine": st.get("engine"),
           "risk": {k: (st.get("risk") or {}).get(k) for k in ["totalWins", "totalLosses", "hasPendingTrade"]},
           "entradas_count": len(lg["segs"]), "entradas_segs": lg["segs"],
           "error": st.get("error")}
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(f"[{n}] {ts} bal={rec['status']['balance']} trend={rec['analysis']['trend']} "
          f"c/p={rec['analysis']['callPower']}/{rec['analysis']['putPower']} "
          f"sig={(rec['engine'] or {}).get('signalsGenerated')} W/L={rec['risk']['totalWins']}/{rec['risk']['totalLosses']} "
          f"entradas={len(lg['segs'])}", flush=True)
    if n < N:
        time.sleep(INTERVAL)
print("R4B SAMPLER DONE", flush=True)
