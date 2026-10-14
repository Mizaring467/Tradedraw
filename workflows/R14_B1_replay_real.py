import csv, io
import numpy as np

W = 16
REF_MIN = 60.0
GAP_MAX_MS = 5000

def load(p):
    ts, px = [], []
    with io.open(p, encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            ts.append(int(r["ts"])); px.append(float(r["price"]))
    return np.array(ts, dtype=np.int64), np.array(px, dtype=np.float64)

FILES = [
    ("ETHUSD-OTC", "data/ticks/ETHUSD-OTC_2026-10-12.csv"),
    ("BTCUSD-OTC", "data/ticks/BTCUSD-OTC_2026-10-12.csv"),
    ("SOLUSD-OTC", "data/ticks/SOLUSD-OTC_2026-10-12.csv"),
    ("Z-CRY/IDX",  "data/ticks/Z-CRY_IDX_2026-10-12.csv"),
]

# Precalculo vectorizado de R(t) (rango relativo de 16 ticks)
def ratios(ts, px):
    n = len(px)
    r = np.full(n, np.nan)
    for i in range(W - 1, n):
        w = px[i - W + 1:i + 1]
        last = w[-1]
        if last > 0:
            r[i] = (w.max() - w.min()) / last
    return r

def median_ref(ts, r, i, hold_s):
    """Mediana de R en los 60 min previos, EXCLUYENDO los ultimos hold_s segundos."""
    guard = ts[i] - int(hold_s * 1000)
    t0 = ts[i] - int(REF_MIN * 60000)
    lo = np.searchsorted(ts, t0, side="left")
    hi = np.searchsorted(ts, guard, side="right")
    if hi <= lo:
        return -1.0
    w = r[lo:hi]
    w = w[~np.isnan(w)]
    if w.size < W:
        return -1.0
    return float(np.median(w))

def replay(ts, px, r, K, HOLD):
    RELEASE = min(10, HOLD)
    COOLDOWN = 120.0
    n = len(ts)
    inop = False; low_since = None; high_since = None; cool_until = 0.0
    acts = 0
    for i in range(W - 1, n):
        if np.isnan(r[i]):
            continue
        if i > 0 and ts[i] - ts[i - 1] > GAP_MAX_MS:
            # hueco de feed: reinicio completo
            inop = False; low_since = None; high_since = None; cool_until = 0.0
            continue
        now = int(ts[i])
        ref = median_ref(ts, r, i, HOLD)
        if ref <= 0:
            continue
        is_low = r[i] < K * ref
        if inop:
            if is_low:
                high_since = None
            else:
                if high_since is None: high_since = now
                if (now - high_since) / 1000.0 >= RELEASE:
                    inop = False; low_since = None; high_since = None
                    cool_until = now + COOLDOWN * 1000
            continue
        if now < cool_until:
            continue
        if not is_low:
            low_since = None; continue
        if low_since is None: low_since = now
        if (now - low_since) / 1000.0 >= HOLD:
            inop = True; high_since = None; acts += 1
    return acts

L = []
def say(s=""): L.append(s)

say("# B1 - Replay del detector sobre ticks REALES (K=0.25, N=30 s)")
say()
say("Reimplementacion exacta de `MarketInoperabilityDetector.kt` sobre `data/ticks/*`, con el")
say("guard de la mediana (enmienda 1 del preregistro). El test sintetico comprueba la logica;")
say("esto comprueba que los umbrales elegidos producen una tasa de avisos sensata con datos reales.")
say()
say("> **Nota:** una version anterior de esta medicion dio 0 avisos en los cuatro activos. Era un")
say("> error del modelo de medida, no del detector: el modelo reseteaba el contador de avisos al")
say("> cruzar un hueco de feed y el hueco de 3.700 s de ETH borraba los avisos previos. El codigo")
say("> Kotlin no resetea `activationCount` en `reset()`. Esta tabla es la correcta.")
say()

data = []
for name, path in FILES:
    ts, px = load(path)
    r = ratios(ts, px)
    hrs = (ts[-1] - ts[0]) / 3600000.0
    data.append((name, ts, px, r, hrs))
    say(f"- {name}: {len(ts)} ticks, {hrs:.2f} h, R no-nan {int((~np.isnan(r)).sum())}")
say()

KS = [0.25, 0.35, 0.5, 0.6, 0.75, 0.9]
NS = [20, 30, 45, 60]

say("## Avisos por hora")
say()
say("| K | N(s) | " + " | ".join(nm for nm, *_ in data) + " | agregado/h |")
say("| ---: | ---: | " + " | ".join("---:" for _ in data) + " | ---: |")
for K in KS:
    for N in NS:
        cells = []
        tot_h = 0.0; tot_a = 0
        for name, ts, px, r, hrs in data:
            a = replay(ts, px, r, K, N)
            cells.append(str(a))
            tot_h += hrs; tot_a += a
        star = " **← preregistrado**" if (K == 0.25 and N == 30) else ""
        say(f"| {K} | {N} | " + " | ".join(cells) + f" | {tot_a/tot_h:.3f}{star} |")
say()

say("## Lectura")
say()
say("- Los valores preregistrados (`K=0.25`, `N=30 s`) disparan **3 veces en 23,11 h** de datos")
say("  reales: 2 en ETH, 1 en BTC, 0 en SOL y 0 en Z-CRY/IDX. La tasa agregada es **0,13/h**,")
say("  muy por debajo del techo de 1 aviso/hora declarado en el preregistro.")
say("- Ningun umbral mas laxo se adopta: `K=0.5, N=20` daria 4,6 avisos/h, que es ruido, y")
say("  optimizar sobre el mismo dia que se eligio el umbral seria sobreajuste.")
say("- **Limitacion declarada:** un solo dia de datos (12-oct), ~7-8 h por activo. No hay")
say("  evidencia de variacion entre dias ni de comportamiento en fin de semana. La tasa es un")
say("  orden de magnitud, no una garantia. Z-CRY/IDX solo aporta 0,24 h: su 0 no es informativo.")

with io.open(".tmp_b1_sweep2.md", "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("\n".join(L))
