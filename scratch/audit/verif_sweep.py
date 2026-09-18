"""Verificacion independiente del punto 7/8: dedup y umbrales.

Solo lectura. No importa la logica de segmentacion de edge_analysis salvo
clasificar/tramo/wald_p/benjamini_hochberg (que es lo verificado).
"""
import sys
import itertools
from collections import defaultdict

sys.path.insert(0, "scripts")
import edge_analysis as ea  # noqa: E402


def enr(f):
    eff = ea.clasificar(f)[0]
    f["_e"] = eff
    f["_stake"] = ea.to_float(f.get("stake"), 1.0)
    f["_conf"] = ea.to_float(f.get("confidence"))
    f["_dur"] = ea.to_float(f.get("duration_sec"))
    f["_hora"] = (f.get("iso_date") or "")[11:13]
    return f


DIMS = {
    "action": lambda f: (f.get("action") or "?").strip().upper(),
    "strategy": lambda f: (f.get("strategy") or "?").strip(),
    "submode": lambda f: (f.get("submode") or "?").strip(),
    "stake": lambda f: "%.2f" % f["_stake"],
    "confidence": lambda f: ea.tramo(f["_conf"], [
        (0.0, 0.60, "a"), (0.60, 0.70, "b"), (0.70, 0.80, "c"),
        (0.80, 0.90, "d"), (0.90, 99.0, "e")]),
    "duration_sec": lambda f: ea.tramo(f["_dur"], [
        (0.0, 60.0, "a"), (60.0, 62.0, "b"), (62.0, 70.0, "c"),
        (70.0, 1e9, "d")]),
    "hora_del_dia": lambda f: (f["_hora"] + "h") if f["_hora"].isdigit() else "??",
    "hora_por_tercios": lambda f: (
        ea.tramo(int(f["_hora"]), [(0, 8, "a"), (8, 16, "b"), (16, 24, "c")])
        if f["_hora"].isdigit() else "??"),
    "dia": lambda f: (f.get("iso_date") or "")[:10],
}


def segs(res):
    s = []
    for nm, fn in DIMS.items():
        g = defaultdict(lambda: [0, 0])
        for f in res:
            k = fn(f)
            g[k][1] += 1
            if f["_e"] == "WIN":
                g[k][0] += 1
        for k, (w, n) in g.items():
            s.append((nm + "=" + str(k), w, n))
    for a, b in itertools.combinations(sorted(DIMS), 2):
        g = defaultdict(lambda: [0, 0])
        for f in res:
            k = (DIMS[a](f), DIMS[b](f))
            g[k][1] += 1
            if f["_e"] == "WIN":
                g[k][0] += 1
        for k, (w, n) in g.items():
            s.append((a + "=" + str(k[0]) + " x " + b + "=" + str(k[1]), w, n))
    return s


def informe(nombre, res):
    S = segs(res)
    print("%s: resueltos=%d segmentos=%d" % (nombre, len(res), len(S)))
    for u in (1, 5, 10, 30):
        ev = [(e, w, n) for e, w, n in S if n >= u]
        pv = [ea.wald_p(w, n) for _, w, n in ev]
        bh = ea.benjamini_hochberg(pv)
        ok = sum(1 for i, (e, w, n) in enumerate(ev) if bh[i] < 0.05)
        surv = sum(1 for i, (e, w, n) in enumerate(ev)
                   if bh[i] < 0.05 and w / n > ea.BREAKEVEN_WR / 100.0)
        print("   umbral n>=%-3d m=%-5d pBH<0.05=%-4d sobreviven(con WR>BE)=%d"
              % (u, len(ev), ok, surv))
    return S


filas, traza, dup, colisiones = ea.unificar(ea.FUENTES)
print("colisiones de timestamp (payload distinto):", colisiones)
res_dedup = [enr(f) for f in filas]
res_dedup = [f for f in res_dedup if f["_e"] in ("WIN", "LOSS")]
S = informe("DEDUP", res_dedup)

# Variante sin deduplicar: recalcular clasificacion y segmentar igual.
todas = []
for p, e in ea.FUENTES:
    h, fs = ea.cargar_fuente(p, e)
    if h is None:
        continue
    todas.extend(fs)
res_nodedup = [enr(f) for f in todas]
res_nodedup = [f for f in res_nodedup if f["_e"] in ("WIN", "LOSS")]
informe("SIN_DEDUP", res_nodedup)

# Sitio mas extremo: el mejor p de todo el conjunto evaluable.
ev = [(e, w, n) for e, w, n in S if n >= 5]
pv = [ea.wald_p(w, n) for _, w, n in ev]
bh = ea.benjamini_hochberg(pv)
i = min(range(len(pv)), key=lambda j: pv[j])
print("\np MINIMO: %s n=%d w=%d p=%.4e pBH=%.4f WR=%.1f%%"
      % (ev[i][0], ev[i][2], ev[i][1], pv[i], bh[i], 100.0 * ev[i][1] / ev[i][2]))
print("p que haria falta para pBH<0.05 siendo el minimo de m=%d: %.3e"
      % (len(pv), 0.05 / len(pv)))
print("p minimo entre n>=30: ", end="")
ev30 = [(e, w, n) for e, w, n in S if n >= 30]
pv30 = [ea.wald_p(w, n) for _, w, n in ev30]
bh30 = ea.benjamini_hochberg(pv30)
j = min(range(len(pv30)), key=lambda k: pv30[k])
print("%s n=%d p=%.4e pBH=%.4f WR=%.1f%%" % (ev30[j][0], ev30[j][2], pv30[j], bh30[j],
                                            100.0 * ev30[j][1] / ev30[j][2]))