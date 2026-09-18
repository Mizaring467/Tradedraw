# -*- coding: utf-8 -*-
"""Desglose ejecucion vs mercado + analisis de martingala."""
import io, csv, os, re, statistics
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
    out = []
    for l in read_text(f).splitlines():
        if not l.strip():
            continue
        r = next(csv.reader([l]))
        if len(r) < 13:
            continue
        r = [x.replace('\ufeff', '') for x in r]
        if not r[0].strip().isdigit():
            continue
        d = dict(zip(BASE13, [x.strip() for x in r[:13]]))
        d['reason'] = ','.join(r[12:]).strip().strip('"')
        out.append(d)
    return out

uni = {}
for name, f in FILES:
    if not os.path.exists(f):
        continue
    for r in norm_rows(f):
        if r['timestamp'] not in uni or len(r['reason']) > len(uni[r['timestamp']]['reason']):
            uni[r['timestamp']] = r
ALL = sorted(uni.values(), key=lambda r: int(r['timestamp']))

DIF = re.compile(r"Diff=([+-]?[0-9.]+)")
def dif(r):
    m = DIF.search(r['reason'])
    return float(m.group(1)) if m else None

# -------- clasificacion causa --------
def causa(r):
    rs = r['reason']
    if 'SIN ACREDITACI' in rs:
        return 'EXEC_SIN_ACREDITACION'
    if 'Diff=0.0' in rs:
        return 'EXEC_DIFF_CERO'
    if 'confirmada tras liquidaci' in rs:
        return 'MERCADO_CONFIRMADO'
    if 'debitada por Binomo' in rs:
        return 'MERCADO_DEBITADA'
    if 'Ganancia acreditada' in rs:
        return 'MERCADO_GANANCIA'
    return 'OTRO:' + rs[:60]

c = Counter(causa(r) for r in ALL)
print("### CAUSA (por texto de reason) -- %d trades" % len(ALL))
for k, v in c.most_common():
    print("  %-26s %4d" % (k, v))

print("\n### RECONCILIACION DE SALDO")
bad = []
for r in ALL:
    bb, sb, d, res = float(r['base_balance']), float(r['settled_balance']), dif(r), r['result']
    obs = sb - bb
    if d is None:
        bad.append((r, 'sin diff', obs)); continue
    if abs(obs - d) > 1.0:
        bad.append((r, 'diff!=saldo', d, obs))
print("filas donde settled-base != Diff:", len(bad))
for b in bad[:12]:
    r = b[0]
    print("   %s %s %s base=%.2f set=%.2f diff=%s obs=%.2f | %s" % (
        r['iso_date'], r['result'], r['stake'], float(r['base_balance']),
        float(r['settled_balance']), b[2] if len(b) > 2 else '?', b[-1], r['reason'][:50]))

# -------- ejecucion vs mercado --------
EXEC = [r for r in ALL if causa(r).startswith('EXEC')]
MKT = [r for r in ALL if causa(r).startswith('MERCADO')]
print("\n### SEPARACION EJECUCION vs MERCADO")
for lbl, rows in (("EXEC (fallo de ejecucion)", EXEC), ("MERCADO (liquidado real)", MKT)):
    n = len(rows); w = sum(1 for r in rows if r['result'] == 'WIN')
    l = sum(1 for r in rows if r['result'] == 'LOSS')
    t = sum(1 for r in rows if r['result'] == 'TIE')
    d = w + l
    print("%-28s n=%3d W=%3d L=%3d TIE=%3d WR=%5.1f%%" % (lbl, n, w, l, t, (w / d * 100) if d else 0))

# WR solo sobre trades con liquidacion real
n, w, l = len(MKT), sum(1 for r in MKT if r['result'] == 'WIN'), sum(1 for r in MKT if r['result'] == 'LOSS')
WRm = w / (w + l) * 100
print("\nWR sobre MERCADO real (excl. fallos exec): %.2f%%  (n=%d)" % (WRm, w + l))
print("WR sobre TODO: %.2f%%" % (88 / (88 + 118) * 100))

# payout solo de trades de mercado
w1 = [abs(dif(r)) for r in MKT if r['result'] == 'WIN' and dif(r) and abs(dif(r)) > 1000]
l1 = [abs(dif(r)) for r in MKT if r['result'] == 'LOSS' and dif(r) and abs(dif(r)) > 1000]
P = statistics.median(w1) / statistics.median(l1)
print("payout mercado: med_win=%.0f med_loss=%.0f p=%.4f  breakeven=%.2f%%" % (
    statistics.median(w1), statistics.median(l1), P, 100 / (1 + P)))
print("EDGE mercado = %.2f pp" % (WRm - 100 / (1 + P)))

# -------- MARTINGALA --------
print("\n### MARTINGALA")
# stake 2.00 == martingala. Reconstruir secuencia por timestamp
seq = sorted(ALL, key=lambda r: int(r['timestamp']))
prev = None
m_delta = 0.0; base_delta = 0.0
m_n = 0; m_w = 0; m_l = 0
mw_delta = 0.0; ml_delta = 0.0
for r in seq:
    st = float(r['stake']); d = dif(r)
    if d is None or abs(d) < 1e-9:
        d = 0.0
    if st >= 2.0:
        m_n += 1; m_delta += d
        if r['result'] == 'WIN': m_w += 1; mw_delta += d
        if r['result'] == 'LOSS': m_l += 1; ml_delta += d
    else:
        base_delta += d
print("trades stake=1 (base): n=%d  PnL=%.0f COP" % (len(seq) - m_n, base_delta))
print("trades stake=2 (M1)  : n=%d  W=%d L=%d WR=%.1f%%  PnL=%.0f COP" % (
    m_n, m_w, m_l, (m_w / (m_w + m_l) * 100) if (m_w + m_l) else 0, m_delta))
print("  de stake=2: ganancias=%.0f  perdidas=%.0f" % (mw_delta, ml_delta))
print("P&L TOTAL = %.0f COP" % (base_delta + m_delta))
print("Contribucion martingala al drawdown = %.1f%%" % (m_delta / (base_delta + m_delta) * 100))

# cuantas veces el trade previo fue LOSS y se entro en M1
cnt_after_loss = 0
for i in range(1, len(seq)):
    if float(seq[i]['stake']) >= 2.0 and seq[i - 1]['result'] == 'LOSS':
        cnt_after_loss += 1
print("entradas M1 precedidas por LOSS: %d de %d" % (cnt_after_loss, m_n))

# rachas y drawdown en COP
print("\n### DRAWDOWN")
eq = 44961812.48; peak = eq; maxdd = 0
for r in seq:
    sb = float(r['settled_balance']); 
    if sb > peak: peak = sb
    dd = peak - sb
    if dd > maxdd: maxdd = dd
print("equity final = %.0f  peak = %.0f  max drawdown = %.0f COP (%.2f%% del peak)" % (
    eq + sum(dif(r) or 0 for r in seq), peak, maxdd, maxdd / peak * 100))
print("saldo inicial 44961812.48 -> saldo final registrado = %.2f" % float(seq[-1]['settled_balance']))

# -------- test binomial --------
import math
def z_binom(k, n, p0):
    if n == 0: return 0
    ph = k / n
    se = math.sqrt(p0 * (1 - p0) / n)
    return (ph - p0) / se
nd = 88 + 118
print("\n### SIGNIFICANCIA (todos los trades)")
print("n decididos=%d  WR=%.4f  breakeven=%.4f  z=%.3f  p-valor(una cola)=%.4f" % (
    nd, 88 / nd, 0.5464, z_binom(88, nd, 0.5464), 1 - 0.5 * (1 + math.erf(z_binom(88, nd, 0.5464) / math.sqrt(2)))))
nm = w + l
print("solo mercado n=%d WR=%.4f z=%.3f" % (nm, w / nm, z_binom(w, nm, 0.5464)))

# tamano muestral
z = (1.96 + 0.84) ** 2
p = 0.55
for delta in (0.03, 0.05, 0.08):
    print("n para detectar edge %.2f pp (80%% pot., p=0.55): n = %.0f" % (delta * 100, z * p * (1 - p) / delta ** 2))
print("n para detectar edge 3pp con p=0.4272: %.0f" % (z * 0.4272 * (1 - 0.4272) / 0.03 ** 2))