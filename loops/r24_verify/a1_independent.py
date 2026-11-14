"""Calculo independiente A1 (no usa recompute_cubes.py)."""
import csv, glob, json, math, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
C2 = [("W04","loops/r23h_validation/window_04_clean"),("W05","loops/r23h_validation/window_05_clean"),
      ("W06","loops/r23i_validation/window_06_clean")]
for i in range(7,12): C2.append(("W%02d"%i,"loops/r23j_validation/window_%02d_clean"%i))
for i in range(12,23): C2.append(("W%02d"%i,"loops/r23k_validation/window_%02d"%i))
C1 = [w for w,_ in C2 if int(w[1:])>=7]

print("== conteo crudo por ventana (trades.csv) ==")
tot=0; per={}
for w,rel in C2:
    p=os.path.join(ROOT,rel,"trades.csv")
    rows=list(csv.DictReader(open(p,encoding="utf-8"))) if os.path.exists(p) else []
    per[w]=rows; tot+=len(rows)
    print(f"{w}: {len(rows)} filas  {rel}")
print("suma filas cubo2:", tot)

# dedupe por trade_id
seen=set(); dup=[]
uniq=[]
for w,_ in C2:
    for r in per[w]:
        t=(r.get("trade_id") or "").strip()
        if not t: continue
        if t in seen: dup.append((w,t)); continue
        seen.add(t); uniq.append((w,r))
print("unicos:", len(uniq), "duplicados:", dup)

# Wilson
def wilson(k,n,z=1.959963984540054):
    if n<=0: return (0,0,0)
    p=k/n; z2=z*z; d=1+z2/n
    c=(p+z2/(2*n))/d
    m=(z/d)*math.sqrt(p*(1-p)/n+z2/(4*n*n))
    return (max(0,c-m),min(1,c+m),c)

print("\n== Wilson escenario N=80, +20 trades extra todos ganadores ==")
# estado actual cubo2: W wins de R resueltos
W=30; R=59
print("actual: %d/%d = %.4f  Wilson=[%.4f,%.4f]"%(W,R,W/R,*wilson(W,R)[:2]))
for extra in (20,):
    n2=R+extra; w2=W+extra
    lo,hi,c=wilson(w2,n2)
    print("N=%d, W=%d, WR=%.4f, Wilson=[%.4f, %.4f]"%(n2,w2,w2/n2,lo,hi))
    print("  criterio Wilson_inf > 0.5464 ->", lo>0.5464, " (faltan %.4f)"%(0.5464-lo))
# cuantos wins extra hacen falta con N=80
print("\n== con N=80 exacto: wins necesarios para Wilson_inf > 0.5464 ==")
for k in range(40,81):
    lo=wilson(k,80)[0]
    if lo>0.5464:
        print("primer k que cumple:", k, "WR=%.4f"%((k)/80), "Wilson_inf=%.4f"%lo)
        print("  k-1:", k-1, "Wilson_inf=%.4f"%wilson(k-1,80)[0])
        break
# tambien N=80 con 20 extra: implica W actual 30 -> 50/80
print("  escenario pedido 50/80 Wilson_inf=%.4f -> alcanzable=%s"%(wilson(50,80)[0], wilson(50,80)[0]>0.5464))

print("\n== cobertura Cubo 1: pares / (Live U Replay) ==")
pairs=0; live=set(); rep=set(); div=0; match=0; missing=[]
for w in C1:
    rel=dict(C2)[w]
    p=os.path.join(ROOT,rel,"replay_comparison.json")
    if not os.path.exists(p):
        missing.append(w); continue
    d=json.load(open(p,encoding="utf-8"))
    for pr in d.get("pairs") or []:
        st=str(pr.get("status","")).upper()
        lv=pr.get("live") or {}; rp=pr.get("replay") or {}
        if lv.get("trade_id"): live.add(lv["trade_id"])
        if rp.get("trade_id"): rep.add(rp["trade_id"])
        if st=="MATCH": match+=1; pairs+=1
        elif st=="DIVERGENT": div+=1; pairs+=1
print("ventanas sin archivo:", missing)
print("pares evaluables:", pairs, "matches:", match, "div:", div)
print("live trade_ids:", len(live), "replay trade_ids:", len(rep), "union:", len(live|rep))
print("cobertura = %d/%d = %.4f"%(pairs, len(live|rep), pairs/len(live|rep)))
