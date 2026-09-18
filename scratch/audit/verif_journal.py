#!/usr/bin/env python3
"""Verificacion INDEPENDIENTE del CSV. No importa analyze_journal.py."""
import csv, re, math, json, sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
P = "loops/journal/trade_journal_device_all.csv"
RE_DIFF = re.compile(r"Diff=\s*([+-]?\d+(?:[.,]\d+)?)")


def f(v, d=0.0):
    try:
        return float(str(v).strip())
    except Exception:
        return d


rows = list(csv.DictReader(open(P, encoding="utf-8", errors="replace", newline="")))

out = {}
out["total_filas"] = len(rows)

# --- conteo por `result` declarado
decl = {}
for r in rows:
    k = (r.get("result") or "").strip().upper()
    decl[k] = decl.get(k, 0) + 1
out["result_declarado"] = decl
w, l, t = decl.get("WIN", 0), decl.get("LOSS", 0), decl.get("TIE", 0)
out["wr_resueltos_declarado_pct"] = round(100.0 * w / (w + l), 1)

# --- conteo por delta de saldo, |delta| < 2 => TIE
eff = []
for r in rows:
    d = f(r.get("settled_balance")) - f(r.get("base_balance"))
    eff.append("TIE" if abs(d) < 2.0 else ("WIN" if d > 0 else "LOSS"))
out["delta_saldo"] = {k: eff.count(k) for k in ("WIN", "LOSS", "TIE")}
ew = eff.count("WIN")
out["wr_delta_pct"] = round(100.0 * ew / (ew + eff.count("LOSS")), 1)


def max_streak(seq, tgt):
    best = cur = 0
    for x in seq:
        cur = cur + 1 if x == tgt else 0
        best = max(best, cur)
    return best


out["racha_max_LOSS_global_delta"] = max_streak(eff, "LOSS")
seq_decl = [(r.get("result") or "").strip().upper() for r in rows]
out["racha_max_LOSS_global_declarado"] = max_streak(seq_decl, "LOSS")
out["racha_max_WIN_global_delta"] = max_streak(eff, "WIN")

# --- por accion usando `result` declarado
by = {}
for r, e in zip(rows, eff):
    a = (r.get("action") or "?").strip().upper()
    g = by.setdefault(a, {"decl": {}, "eff": {}, "seq_decl": [], "seq_eff": [], "n": 0})
    g["n"] += 1
    dv = (r.get("result") or "").strip().upper()
    g["decl"][dv] = g["decl"].get(dv, 0) + 1
    g["eff"][e] = g["eff"].get(e, 0) + 1
    g["seq_decl"].append(dv)
    g["seq_eff"].append(e)
acc = {}
for a, g in by.items():
    dw, dl = g["decl"].get("WIN", 0), g["decl"].get("LOSS", 0)
    acc[a] = {
        "n": g["n"],
        "decl": g["decl"],
        "wr_decl_pct": round(100.0 * dw / (dw + dl), 1) if dw + dl else None,
        "eff": g["eff"],
        "racha_max_LOSS_decl": max_streak(g["seq_decl"], "LOSS"),
        "racha_max_LOSS_delta": max_streak(g["seq_eff"], "LOSS"),
    }
out["por_accion"] = acc

# --- stakes
st = {}
for r in rows:
    k = f"{f(r.get('stake')):.2f}"
    st[k] = st.get(k, 0) + 1
out["stakes"] = st

# --- diffs del campo reason
dp = dn = dz = dnone = 0
pos, neg = [], []
for r in rows:
    m = RE_DIFF.search(r.get("reason") or "")
    if not m:
        dnone += 1
        continue
    v = float(m.group(1).replace(",", "."))
    if v > 0:
        dp += 1
        pos.append(v)
    elif v < 0:
        dn += 1
        neg.append(v)
    else:
        dz += 1
out["diffs"] = {"positivos": dp, "negativos": dn, "ceros": dz, "sin_diff": dnone}
if pos and neg:
    out["payout_medido_pct"] = round(100.0 * (sum(pos) / len(pos)) / abs(sum(neg) / len(neg)), 2)
    out["media_pos"] = round(sum(pos) / len(pos), 2)
    out["media_neg"] = round(sum(neg) / len(neg), 2)

# --- Wilson independiente (implementacion 1: formula cerrada)
def wilson(wins, n, z=1.959963984540054):
    if n <= 0:
        return (0.0, 0.0)
    p = wins / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return (100 * max(0.0, c - m), 100 * min(1.0, c + m))


# --- Wilson independiente (implementacion 2: solve cuadratico (p-p0)^2 = z^2 p(1-p)/n)
def wilson_quad(wins, n, z=1.959963984540054):
    if n <= 0:
        return (0.0, 0.0)
    p = wins / n
    a = n + z * z
    b = -(2 * n * p + z * z)
    cc = n * p * p
    disc = math.sqrt(b * b - 4 * a * cc)
    lo = (-b - disc) / (2 * a)
    hi = (-b + disc) / (2 * a)
    return (100 * lo, 100 * hi)


out["wilson_50_100_formula_cerrada"] = [round(x, 2) for x in wilson(50, 100)]
out["wilson_50_100_cuadratica"] = [round(x, 2) for x in wilson_quad(50, 100)]
out["wilson_193_81"] = [round(x, 2) for x in wilson(81, 193)]
out["wilson_declarado_193_81"] = [round(x, 2) for x in wilson(81, 81 + 114)]
out["wilson_ventana2_38_11"] = [round(x, 2) for x in wilson(11, 38)]
# sanity: n=10, w=10 debe dar limite sup = 100
out["wilson_10_10"] = [round(x, 2) for x in wilson(10, 10)]

# --- drawdown / saldos
saldos = [f(r.get("settled_balance")) for r in rows if f(r.get("settled_balance")) >= 1e6]
pico = saldos[0]
peor = 0.0
for s in saldos:
    pico = max(pico, s)
    peor = max(peor, pico - s)
out["drawdown_abs"] = round(peor, 2)
out["drawdown_pct"] = round(100.0 * peor / max(saldos), 2)
out["saldo_neto"] = round(saldos[-1] - saldos[0], 2)

# --- ventana 2026-09-12
winr = [r for r in rows if (r.get("iso_date") or "")[:10] >= "2026-09-12"]
weff = []
for r in winr:
    d = f(r.get("settled_balance")) - f(r.get("base_balance"))
    weff.append("TIE" if abs(d) < 2 else ("WIN" if d > 0 else "LOSS"))
out["ventana_0912"] = {
    "n": len(winr),
    "delta": {k: weff.count(k) for k in ("WIN", "LOSS", "TIE")},
    "fechas_presentes": sorted({(r.get("iso_date") or "")[:10] for r in rows}),
    "racha_max_LOSS": max_streak(weff, "LOSS"),
}

print(json.dumps(out, indent=2, ensure_ascii=False))