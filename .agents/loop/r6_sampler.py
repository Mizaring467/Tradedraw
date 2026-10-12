import subprocess, time, json, urllib.request, os

ADB = ["adb", "-s", "UGU8KFUKM7E6CE8D"]
URL = "http://127.0.0.1:8080/status"
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r6_samples.jsonl"
IMGDIR = r"C:\Users\heidy\Tradedraw\.agents\loop"
STREAM = r"C:\Users\heidy\Tradedraw\loops\journal\live\r6_logcat_stream.log"
N = 17
INTERVAL = 62

def now(): return time.strftime("%Y-%m-%d %H:%M:%S")

def sh(args, su=False):
    cmd = ADB + (["shell", "su -c '" + args + "'"] if su else ["shell", args])
    try:
        return subprocess.run(cmd, capture_output=True, text=True, errors="replace", timeout=25).stdout.strip()
    except Exception as e:
        return "ERR:" + str(e)[:80]

def get_status():
    try:
        with urllib.request.urlopen(URL, timeout=6) as r:
            return json.load(r)
    except Exception as e:
        return {"error": str(e)[:80]}

def h9_entradas():
    try:
        out = subprocess.run(ADB + ["logcat", "-d"], capture_output=True, text=True, errors="replace", timeout=30).stdout
    except Exception:
        return []
    res = []
    for l in out.splitlines():
        if "ENTRADA despachada" in l:
            res.append(l)
    return res

def journal_rows():
    txt = sh("cat /data/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv", su=True)
    return txt

for n in range(1, N + 1):
    ts = now()
    st = get_status()
    png = os.path.join(IMGDIR, f"r6_hud_{n}.png")
    with open(png, "wb") as f:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.DEVNULL)
    h9 = h9_entradas()
    jr = journal_rows()
    jlines = jr.splitlines()
    stream_sz = os.path.getsize(STREAM) if os.path.exists(STREAM) else -1
    rec = {"n": n, "ts": ts, "png": png,
           "balance": st.get("balance"),
           "analysis": {k: (st.get("analysis") or {}).get(k) for k in ["trend", "callPower", "putPower"]},
           "engine": st.get("engine"),
           "risk": {k: (st.get("risk") or {}).get(k) for k in ["totalWins", "totalLosses", "hasPendingTrade"]},
           "h9_count": len(h9), "h9_lines": [x[-160:] for x in h9[-6:]],
           "journal_rows": len(jlines) - 1 if jlines else 0,
           "journal_tail": jlines[-2:] if len(jlines) >= 2 else jlines,
           "stream_size": stream_sz}
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(f"[{n}] {ts} bal={rec['balance']} sig={(rec['engine'] or {}).get('signalsGenerated')} "
          f"W/L={rec['risk']['totalWins']}/{rec['risk']['totalLosses']} pend={rec['risk']['hasPendingTrade']} "
          f"h9={rec['h9_count']} jrows={rec['journal_rows']} stream={stream_sz}", flush=True)
    if n < N:
        time.sleep(INTERVAL)
print("R6 SAMPLER DONE", flush=True)
