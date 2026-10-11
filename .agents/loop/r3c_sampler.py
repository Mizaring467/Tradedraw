import subprocess, sys, json, re, time, urllib.request, os

ADB = ["adb", "-s", "UGU8KFUKM7E6CE8D"]
OUT = r"C:\Users\heidy\Tradedraw\.agents\loop\r3c_hud_samples.jsonl"
IMGDIR = r"C:\Users\heidy\Tradedraw\.agents\loop"
START = int(sys.argv[1]) if len(sys.argv) > 1 else 1
N = int(sys.argv[2]) if len(sys.argv) > 2 else 13
INTERVAL = int(sys.argv[3]) if len(sys.argv) > 3 else 75

from paddleocr import PaddleOCR
ocr = PaddleOCR(lang='en', enable_mkldnn=False)

def capture(n):
    p = os.path.join(IMGDIR, f"r3c_hud_{n}.png")
    with open(p, "wb") as f:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.DEVNULL)
    return p

def run_ocr(p):
    res = ocr.predict(p)
    lines = []
    for r in res:
        rt = getattr(r, 'rec_texts', None)
        if rt is None and isinstance(r, dict):
            rt = r.get('rec_texts')
        for t in (rt or []):
            lines.append(str(t))
    return lines

def parse(lines):
    blob = "\n".join(lines)
    d = {}
    m = re.search(r'Tendencia:\s*([A-Za-zÁÉÍÓÚáéíóúñ]+)', blob); d['tendencia'] = m.group(1) if m else None
    m = re.search(r'Patr[oó]n:\s*([^\n|]+)', blob); d['patron'] = m.group(1).strip() if m else None
    m = re.search(r'(\d+)%\s*CALL', blob); d['call_pct'] = int(m.group(1)) if m else None
    m = re.search(r'(\d+)%\s*PUT', blob); d['put_pct'] = int(m.group(1)) if m else None
    m = re.search(r'[W1lI]:\s*(\d+)\s*\|\s*L:\s*(\d+)\s*\(([\d.]+)%\)', blob)
    if m: d['wins'], d['losses'], d['winrate'] = int(m.group(1)), int(m.group(2)), float(m.group(3))
    else: d['wins'] = d['losses'] = d['winrate'] = None
    m = re.search(r'WS Activo\s*\(([^)]+)\):\s*([\d.]+)', blob)
    if m: d['ws_asset'], d['ws_price'] = m.group(1), float(m.group(2))
    else: d['ws_asset'] = d['ws_price'] = None
    m = re.search(r'Col\$\s*([\d.,]+)', blob)
    d['balance_hud'] = m.group(1) if m else None
    m = re.search(r'Prob:\s*(\d+)%', blob); d['prob'] = int(m.group(1)) if m else None
    m = re.search(r'Estrat:\s*([^\n\]]+)', blob); d['plan_estrat'] = m.group(1).strip() if m else None
    veto = [l for l in lines if re.search(r've[tad]|rechazo|ruptura|esperando', l, re.I)]
    d['plan_veto'] = " | ".join(veto)[:200] if veto else None
    d['raw'] = lines
    return d

def get_status():
    try:
        with urllib.request.urlopen("http://127.0.0.1:8080/status", timeout=6) as r:
            s = json.load(r)
    except Exception as e:
        return {"error": str(e)}
    a = s.get('analysis', {}) or {}
    e = s.get('engine', {}) or {}
    f = s.get('feed', {}) or {}
    return {
        "trend": a.get('trend'), "callPower": a.get('callPower'), "putPower": a.get('putPower'),
        "price_y": a.get('currentPriceY'), "streak": a.get('streak'),
        "balance": s.get('balance'), "signalsGenerated": e.get('signalsGenerated'),
        "lastSignalAction": e.get('lastSignalAction'), "evaluatedCount": e.get('evaluatedCount'),
        "ws_asset": f.get('ws_active_asset'), "ws_connected": f.get('connected'),
        "tickAgeMs": f.get('lastTickAgeMs'),
    }

for i in range(START, START + N):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    png = capture(i)
    lines = run_ocr(png)
    hud = parse(lines)
    st = get_status()
    rec = {"n": i, "ts": ts, "png": png, "hud": hud, "status": st}
    with open(OUT, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(f"[{i}] {ts} HUD(trend={hud.get('tendencia')} call={hud.get('call_pct')} put={hud.get('put_pct')} W/L={hud.get('wins')}/{hud.get('losses')} ws={hud.get('ws_price')} bal={hud.get('balance_hud')}) STATUS(trend={st.get('trend')} call={st.get('callPower')} put={st.get('putPower')} sig={st.get('signalsGenerated')} bal={st.get('balance')})", flush=True)
    if i < START + N - 1:
        time.sleep(INTERVAL)
print("DONE", flush=True)
