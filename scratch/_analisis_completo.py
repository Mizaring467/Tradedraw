# -*- coding: utf-8 -*-
"""Analisis cuantitativo journal TradeDraw. Solo lectura."""
import io, csv, os, re, json, statistics
from collections import Counter, defaultdict

FILES = [
 ("device_all", r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_device_all.csv"),
 ("live_root",  r"C:\Users\heidy\Tradedraw\loops\journal\trade_journal_live.csv"),
 ("live_sub",   r"C:\Users\heidy\Tradedraw\loops\journal\live\trade_journal_live.csv"),
 ("pull_0917",  r"C:\Users\heidy\Tradedraw\scratch\device_pull\trade_journal_20260917.csv"),
 ("dev_scratch",r"C:\Users\heidy\Tradedraw\scratch\journal_device.csv"),
 ("v2",         r"C:\Users\heidy\Tradedraw\scratch\journal_v2.csv"),
]

BASE13 = ['timestamp','iso_date','strategy','submode','action','confidence','price_y',
          'stake','base_balance','result','settled_balance','duration_sec','reason']
EXT24 = ['regime','r1','r2','r3','trend','vola','streak_n','outcome_tag']

def read_text(f):
    raw = open(f, "rb").read()
    for e in ("utf-8-sig", "utf-16", "cp1252", "latin-1"):
        try:
            t = raw.decode(e)
            if 'timestamp' in t[:300].lower():
                return t
        except Exception:
            pass
    return raw.decode("utf-8", "replace")

def norm_rows(f):
    """Devuelve dicts normalizados (13 campos base) + campos extra si len==21."""
    t = read_text(f)
    out = []
    for l in t.splitlines():
        if not l.strip():
            continue
        r = next(csv.reader([l]))
        if len(r) < 13:
            continue
        r = [x.replace('\ufeff', '').strip() for x in r]
        if r[0].lower() == 'timestamp' or not r[0].isdigit():
            continue
        d = dict(zip(BASE13, [x.strip() for x in r[:13]]))
        # reason es el ultimo campo (puede tener comas) -> re-join
        d['reason'] = ','.join(r[12:]).strip().strip('"')
        if len(r) == 21:
            for k, v in zip(EXT24, r[13:21]):
                d[k] = v
        out.append(d)
    return out

DATA = {}
for name, f in FILES:
    if not os.path.exists(f):
        print("NO EXISTE:", f); continue
    DATA[name] = norm_rows(f)

print("### CONTEOS Y DUPLICADOS")
for name, rows in DATA.items():
    keys = [(r['timestamp'], r['iso_date']) for r in rows]
    print("%-12s filas=%-4d unicas(ts+iso)=%-4d dup=%d" % (
        name, len(rows), len(set(keys)), len(rows) - len(set(keys))))

# --- conjunto canonico: union deduplicada por timestamp ---
uni = {}
for name, rows in DATA.items():
    for r in rows:
        uni.setdefault(r['timestamp'], r)
ALL = sorted(uni.values(), key=lambda r: int(r['timestamp']))
print("\n### UNION DEDUPLICADA por timestamp: %d trades unicos" % len(ALL))
print("rango:", ALL[0]['iso_date'], "->", ALL[-1]['iso_date'])

# ---------- parseo de economia desde reason ----------
DIF = re.compile(r"Diff=([+-]?[0-9.]+)")
def diff_of(r):
    m = DIF.search(r['reason'])
    return float(m.group(1)) if m else None

res = Counter(r['result'] for r in ALL)
print("\n### RESULTADOS (union):", dict(res))

# ---------- payout implicito: ganancia / stake en COP ----------
# stake en unidades del journal ('1.00','2.00'); el saldo esta en COP y diff en COP.
# Balance inicial de referencia
bals = [float(r['base_balance']) for r in ALL]
print("base_balance min/max:", min(bals), max(bals))

rows_eco = []
for r in ALL:
    d = diff_of(r)
    st = None
    try:
        st = float(r['stake'])
    except Exception:
        pass
    rows_eco.append((r, d, st))

# payout observado = ratio |diff_win| / |diff_loss|  (mismo stake -> payout puro)
win_d = [abs(d) for r, d, s in rows_eco if r['result'] == 'WIN' and d is not None]
loss_d = [abs(d) for r, d, s in rows_eco if r['result'] == 'LOSS' and d is not None]
print("\n### COP por trade")
print("WIN  n=%d  median=%.2f  mean=%.2f" % (len(win_d), statistics.median(win_d), statistics.mean(win_d)))
print("LOSS n=%d  median=%.2f  mean=%.2f" % (len(loss_d), statistics.median(loss_d), statistics.mean(loss_d)))
if loss_d and win_d:
    med_loss = statistics.median(loss_d)
    med_win = statistics.median(win_d)
    print("payout implicito (mediana gan/perd) = %.4f" % (med_win / med_loss))
    print("payout implicito (media gan/perd)   = %.4f" % (statistics.mean(win_d) / statistics.mean(loss_d)))
    pw = Counter(round(abs(d)) for r, d, s in rows_eco if r['result'] == 'WIN' and d)
    pl = Counter(round(abs(d)) for r, d, s in rows_eco if r['result'] == 'LOSS' and d)
    print("moda COP ganancia:", pw.most_common(4))
    print("moda COP perdida :", pl.most_common(4))

# ---------- agregado del conjunto con datos completos ----------
def stats(rows):
    n = len(rows); w = sum(1 for r in rows if r['result'] == 'WIN')
    l = sum(1 for r in rows if r['result'] == 'LOSS')
    t = sum(1 for r in rows if r['result'] == 'TIE')
    d = sum(1 for r in rows if r['result'] in ('WIN', 'LOSS'))
    wr = (w / d * 100) if d else 0
    return n, w, l, t, wr

n, w, l, t, wr = stats(ALL)
print("\n### GLOBAL union")
print("n=%d win=%d loss=%d tie=%d  WR(bruto s/decididos)=%.2f%%" % (n, w, l, t, wr))

# pool de payout: usar mediana de ganancias / mediana de perdidas por stake 1
stake1 = [(r, d) for r, d, s in rows_eco if s == 1.0]
w1 = [abs(d) for r, d in stake1 if r['result'] == 'WIN' and d]
l1 = [abs(d) for r, d in stake1 if r['result'] == 'LOSS' and d]
print("stake=1: n=%d  med_win=%.1f  med_loss=%.1f" % (len(stake1), statistics.median(w1) if w1 else 0, statistics.median(l1) if l1 else 0))
P = statistics.median(w1) / statistics.median(l1) if (w1 and l1) else None
print("PAYOUT OBSERVADO p = %.4f" % P)
print("BREAKEVEN requerido = 1/(1+p) = %.2f%%" % (100 / (1 + P)))
print("EDGE = WR - breakeven = %.2f pp" % (wr - 100 / (1 + P)))

# ---------- P&L en COP ----------
def pnl(rows):
    tot = 0.0
    for r in rows:
        d = diff_of(r)
        if d is None:
            continue
        # loss con Diff=0.0 (sin acreditacion) no debita -> usar variacion de saldo
        if abs(d) < 1e-9:
            continue
        tot += d
    return tot
bal0 = float(ALL[0]['base_balance']); balN = float(ALL[-1]['settled_balance'])
print("\n### P&L")
print("base_balance primero=%.2f  settled_balance ultimo=%.2f  delta=%.2f COP" % (bal0, balN, balN - bal0))
print("delta %% sobre saldo inicial = %.4f%%" % ((balN - bal0) / bal0 * 100))
print("suma Diff (excl. 0.0) = %.2f COP" % pnl(ALL))
print("stake unit = 100000 COP (1 unidad journal); saldo inicial = %.1f unidades" % (bal0 / 100000))

# ---------- desglose ----------
def tab(keyf, rows=None, label="grupo"):
    rows = rows or ALL
    g = defaultdict(list)
    for r in rows:
        g[keyf(r)].append(r)
    print("\n--- por %s ---" % label)
    print("%-14s %6s %5s %5s %6s %8s %14s" % (label, "n", "W", "L", "WR%", "breakeven?", "PnL_COP"))
    for k in sorted(g, key=lambda x: (str(x))):
        rr = g[k]
        nn, ww, ll, tt, wrr = stats(rr)
        pp = pnl(rr)
        print("%-14s %6d %5d %5d %6.1f %8s %14.0f" % (k, nn, ww, ll, wrr, "", pp))

tab(lambda r: r['action'], label="accion")
tab(lambda r: r['submode'], label="submode")
tab(lambda r: r['strategy'], label="strategy")
for k in ('regime', 'trend', 'vola', 'outcome_tag'):
    if any(k in r for r in ALL):
        tab(lambda r, k=k: r.get(k, '-'), label=k)
tab(lambda r: r['iso_date'][:10], label="fecha")
tab(lambda r: r['iso_date'][11:13], label="horaUTC")
tab(lambda r: r['duration_sec'], label="dur_sec")
tab(lambda r: r['stake'], label="stake")

# ---------- rachas ----------
def rachas(rows):
    seq = [r['result'] for r in sorted(rows, key=lambda r: int(r['timestamp'])) if r['result'] in ('WIN', 'LOSS')]
    mxw = mxl = cw = cl = 0
    for s in seq:
        if s == 'WIN':
            cw += 1; cl = 0
        else:
            cl += 1; cw = 0
        mxw = max(mxw, cw); mxl = max(mxl, cl)
    return mxw, mxl, seq
mxw, mxl, seq = rachas(ALL)
print("\n### RACHAS (union, orden temporal)")
print("max victorias consecutivas =", mxw)
print("max derrotas consecutivas  =", mxl)
print("secuencia (primeras 60):", ''.join('W' if s == 'WIN' else 'L' for s in seq[:60]))