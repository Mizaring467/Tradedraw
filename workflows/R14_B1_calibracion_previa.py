import csv, io, math
import numpy as np

WIN = 16
FILES = [
    ("ETHUSD-OTC", "data/ticks/ETHUSD-OTC_2026-10-12.csv"),
    ("BTCUSD-OTC", "data/ticks/BTCUSD-OTC_2026-10-12.csv"),
    ("SOLUSD-OTC", "data/ticks/SOLUSD-OTC_2026-10-12.csv"),
    ("Z-CRY/IDX",  "data/ticks/Z-CRY_IDX_2026-10-12.csv"),
]
GAP_MAX_MS = 5000        # por encima: feed callado, no mercado muerto
REF_MIN = 60.0           # ventana de referencia de "lo normal" del propio activo
KS = [0.50, 0.25, 0.10]
NS = [2, 3, 5]

def load(p):
    ts, px = [], []
    with io.open(p, encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            ts.append(int(r["ts"])); px.append(float(r["price"]))
    return np.array(ts, dtype=np.int64), np.array(px, dtype=np.float64)

def ratio_series(px, win=WIN):
    n = len(px)
    out = np.full(n, np.nan)
    for i in range(win - 1, n):
        w = px[i - win + 1:i + 1]
        last = w[-1]
        if last == 0: continue
        out[i] = (w.max() - w.min()) / abs(last)
    return out

L = []
def say(s=""): L.append(s)

say("# B1 - Detector auto-relativo (sin constante por activo)")
say()
say("## Hechos medidos (12-oct) que descartan alternativas")
say()
say("1. **Umbral absoluto de rango relativo: descartado.** Escalas incomparables (ETH 3.4e-04 vs")
say("   Z-CRY 2.6e-10, cuatro ordenes de magnitud). Ningun valor absoluto produce rachas")
say("   sostenidas: con `T=1e-6` la racha maxima es 0.3 min en ETH y 0.1 min en BTC; con")
say("   `T=1e-3` si hay rachas largas pero ETH queda 'inoperable' el 95.5 % del tiempo.")
say("2. **Estancamiento de precio: descartado.** Con feed vivo (intervalo <= 5 s), el precio")
say("   nunca permanece quieto 1 minuto: racha maxima **33 s** en ETH, **5 s** en BTC,")
say("   **43 s** en SOL, **1 s** en Z-CRY. Un aviso por precio congelado no se dispararia jamas")
say("   en este dia. (El 'estancamiento' de 61 min que aparecia antes era un hueco de feed sin")
say("   ticks, que es `FEED_MUDO`, otro caso ya instrumentado.)")
say()
say("## Definicion adoptada")
say()
say("El rango relativo se compara **contra la historia reciente del propio activo**, no contra")
say("una constante. Sea `R(t)` el rango relativo de 16 ticks y `M(t)` su mediana en los ultimos")
say(f"{REF_MIN:.0f} minutos de ese mismo activo. El mercado se declara inoperable cuando")
say("`R(t) < K * M(t)` de forma **sostenida** durante **N minutos** con el feed vivo.")
say()
say("Ventajas: sin constantes por activo, comparable entre activos, y no toca V2. Ademas solo")
say("mide actividad, no direccion: no crea sesgo ni edge.")
say()
say("## Tasa esperada de avisos en el dia de referencia")
say()
say(f"{'activo':12s} " + " ".join(f"K={K} N={N}".rjust(13) for K in KS for N in NS))
rows = {}
for name, path in FILES:
    ts, px = load(path)
    r = ratio_series(px)
    n = len(ts)
    span_min = (ts[-1] - ts[0]) / 60000.0
    hrs = span_min / 60.0
    for K in KS:
        # M(t): mediana de R en los ultimos REF_MIN minutos (por indice temporal)
        low = np.zeros(n, dtype=bool)
        for i in range(n):
            if np.isnan(r[i]): continue
            t0 = ts[i] - int(REF_MIN * 60000)
            j0 = np.searchsorted(ts, t0, side="left")
            w = r[j0:i + 1]
            w = w[~np.isnan(w)]
            if len(w) < WIN: continue
            med = np.median(w)
            if med <= 0: continue
            low[i] = r[i] < K * med
        # exigir feed vivo
        alive = np.zeros(n, dtype=bool)
        alive[1:] = np.diff(ts) <= GAP_MAX_MS
        low = low & alive
        # rachas en minutos
        for N in NS:
            durs = []
            i = 0
            while i < n:
                if low[i]:
                    j = i
                    while j + 1 < n and low[j + 1]: j += 1
                    durs.append((ts[j] - ts[i]) / 60000.0)
                    i = j + 1
                else:
                    i += 1
            hits = sum(1 for d in durs if d >= N)
            rows[(name, K, N)] = (hits, hits / hrs if hrs else 0)
    say(f"## {name}")
    say()
    say(f"- Ventana {span_min/60:.2f} h")
    say()
    say("| K | N | avisos | avisos/hora |")
    say("| ---: | ---: | ---: | ---: |")
    for K in KS:
        for N in NS:
            h, rate = rows[(name, K, N)]
            say(f"| {K} | {N} min | {h} | {rate:.2f}/h |")
    say()

say("## Veredicto")
say()
say("| K | N | avisos/hora agregados (4 activos) | lectura |")
say("| ---: | ---: | ---: | :--- |")
for K in KS:
    for N in NS:
        tot = sum(rows[(nm, K, N)][1] for nm, _ in FILES)
        say(f"| {K} | {N} min | {tot:.2f}/h | {'ruidoso' if tot > 1.0 else ('razonable' if tot > 0.05 else 'casi nunca')} |")
say()

with io.open(".tmp_b1_auto.md", "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("OK")
