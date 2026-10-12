import subprocess, time, json, urllib.request, os

ADB = ["adb", "-s", "UGU8KFUKM7E6CE8D"]
URL = "http://127.0.0.1:8080/status"
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r5_samples.jsonl"
IMGDIR = r"C:\Users\heidy\Tradedraw\.agents\loop"
N = 16
INTERVAL = 65
EXT = "/sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/"
INT = "/data/data/com.example.tradedraw/files/TradeDraw_Audits/"

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

def logcat_entradas():
    try:
        out = subprocess.run(ADB + ["logcat", "-d"], capture_output=True, text=True, errors="replace", timeout=25).stdout
    except Exception:
        return {"segs": [], "count": 0}
    segs = []
    for l in out.splitlines():
        if "ENTRADA despachada" in l:
            for tok in l.split():
                if tok.startswith("segundo="):
                    segs.append(tok)
    return {"segs": segs, "count": len(segs)}

def journal_traces():
    try:
        out = subprocess.run(ADB + ["logcat", "-d"], capture_output=True, text=True, errors="replace", timeout=25).stdout
    except Exception:
        return []
    keys = ("TradeJournalLogger", "FALLO", "registrar", "sincroniz", "fallback", "fallback interno")
    res = []
    for l in out.splitlines():
        if any(k.lower() in l.lower() for k in keys):
            res.append(l)
    return res[-10:]

for n in range(1, N + 1):
    ts = now()
    st = get_status()
    png = os.path.join(IMGDIR, f"r5_hud_{n}.png")
    with open(png, "wb") as f:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.DEVNULL)
    lg = logcat_entradas()
    jext = sh("ls -la " + EXT)
    jint = sh("ls -la " + INT, su=True)
    jtr = journal_traces()
    rec = {"n": n, "ts": ts, "png": png,
           "balance": st.get("balance"),
           "analysis": {k: (st.get("analysis") or {}).get(k) for k in ["trend", "callPower", "putPower"]},
           "engine": st.get("engine"),
           "risk": {k: (st.get("risk") or {}).get(k) for k in ["totalWins", "totalLosses", "hasPendingTrade"]},
           "journalWrites": (st.get("feed") or {}).get("journalWrites"),
           "journalError": (st.get("feed") or {}).get("journalError"),
           "ext_ls": jext[-400:], "int_ls": jint[-400:],
           "entradas_segs": lg["segs"], "entradas_count": lg["count"],
           "journal_log": jtr}
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(f"[{n}] {ts} bal={rec['balance']} sig={(rec['engine'] or {}).get('signalsGenerated')} "
          f"W/L={rec['risk']['totalWins']}/{rec['risk']['totalLosses']} pend={rec['risk']['hasPendingTrade']} "
          f"jw={rec['journalWrites']} entr={rec['entradas_count']} int_exists={'trade_journal.csv' in jint}", flush=True)
    if n < N:
        time.sleep(INTERVAL)
print("R5 SAMPLER DONE", flush=True)
