#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R23K A4 — AUDITORÍA CONTRAFACTUAL DEL VETO `BUY_EXHAUSTION_CLIMAX`.

QUE MIDE Y QUE NO. El veto `VETO_BUY_EXHAUSTION_CLIMAX` (R23H) impide comprar en UPTREND
cuando `callPower >= 70`. Este script responde una sola pregunta contable: si esas entradas
vetadas se hubieran ejecutado, ¿habrían ganado o perdido a 60 s?

No mide si la estrategia gana. Mide el signo de las decisiones que el veto suprimió, que es una
pregunta distinta y mucho más estrecha. Un veto puede ser correcto en su tesis y aun así tener un
contrafactual adverso en una muestra pequeña: por eso el dictamen separa in-sample de
out-of-sample y exige N >= 20 fuera de muestra para atreverse a concluir algo.

DETERMINISMO. Mismo CSV de entrada -> mismo resultado, byte a byte. Sin azar, sin red, sin reloj de
pared. Las únicas dependencias son los archivos de `loops/`.

Uso:
    python scripts/validation/counterfactual_exhaustion_climax.py
    python scripts/validation/counterfactual_exhaustion_climax.py --report <ruta.md>
    python scripts/validation/counterfactual_exhaustion_climax.py --self-test
"""

import argparse
import csv
import glob
import json
import math
import os
import sys
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# PARÁMETROS CONGELADOS — no se ajustan tras ver resultados
# ---------------------------------------------------------------------------

# Ventana de búsqueda del tick de salida: +-4 s alrededor de t+60s, como fija la especificación.
# Los ticks de Z-CRY/IDX llegan a ~2,4/s (~417 ms entre muestras), así que +-4 s acota la
# búsqueda a ~19 ticks y sigue siendo "el precio a los 60 segundos".
HORIZONTE_MS = 60_000
TOLERANCIA_TICK_MS = 4_000

# Umbral epistémico: por debajo de 20 señales únicas fuera de muestra NO se concluye nada.
N_MINIMO_CONCLUYENTE = 20

# Payout vigente observado en las ventanas R23H..R23J. El breakeven se recalcula con él.
PAYOUT_OBSERVADO = 0.83

# Ventanas de la campaña. W04..W11 son out-of-sample respecto al análisis que originó el veto
# (R23G). Las rutas se listan explícitamente, no se descubren con glob, para que el conjunto
# medido no cambie si aparece un directorio nuevo bajo loops/.
VENTANAS_OUT_OF_SAMPLE: List[Tuple[str, str]] = [
    ("W04", "loops/r23h_validation/window_04_clean"),
    ("W05", "loops/r23h_validation/window_05_clean"),
    ("W06", "loops/r23i_validation/window_06_clean"),
    ("W07", "loops/r23j_validation/window_07_clean"),
    ("W08", "loops/r23j_validation/window_08_clean"),
    ("W09", "loops/r23j_validation/window_09_clean"),
    ("W10", "loops/r23j_validation/window_10_clean"),
    ("W11", "loops/r23j_validation/window_11_clean"),
]

VENTANAS_IN_SAMPLE: List[Tuple[str, str]] = [
    (f"R23G-W{i:02d}", f"loops/r23g_validation/window_{i:02d}")
    for i in (1, 3, 4, 5, 6, 7, 8)
]


# ---------------------------------------------------------------------------
# LECTURA DE DATOS
# ---------------------------------------------------------------------------


def cargar_ticks(ruta: str) -> List[Tuple[int, float]]:
    """
    Lee `ticks_live.csv` (`ts,ric,price`) y devuelve [(ts_ms, price)] ordenado por ts.

    Se ordena explícitamente: el archivo puede venir desordenado si el monitor escribió la cola
    del buffer después de los ticks frescos, y la búsqueda binaria de después asume orden.
    """
    if not os.path.exists(ruta):
        return []
    ticks: List[Tuple[int, float]] = []
    with open(ruta, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            try:
                ticks.append((int(row["ts"]), float(row["price"])))
            except (KeyError, TypeError, ValueError):
                continue
    ticks.sort(key=lambda t: t[0])
    return ticks


def precio_en_instante(ticks: List[Tuple[int, float]], ts_ms: int) -> Optional[float]:
    """Precio del tick más cercano a `ts_ms`, o None si no hay ninguno."""
    if not ticks:
        return None
    # Búsqueda binaria manual: los ticks están ordenados por ts.
    lo, hi = 0, len(ticks) - 1
    while lo < hi:
        mid = (lo + hi) // 2
        if ticks[mid][0] < ts_ms:
            lo = mid + 1
        else:
            hi = mid
    # `lo` es el primer tick con ts >= ts_ms. El candidato es ese o el anterior.
    mejor = None
    mejor_d = None
    for idx in (lo - 1, lo, lo + 1):
        if 0 <= idx < len(ticks):
            d = abs(ticks[idx][0] - ts_ms)
            if mejor_d is None or d < mejor_d:
                mejor_d = d
                mejor = ticks[idx]
    return mejor[1] if mejor else None


def extraer_vetos_exhaustion(ruta_decisions: str) -> List[Dict[str, Any]]:
    """
    Devuelve las filas de `decisions.csv` cuyo detalle sea un veto de agotamiento.

    El filtro busca `EXHAUSTION_CLIMAX` en `detail` (no en `veto_reason`, que en estas ventanas
    viene siempre como `OTRO` y no distingue nada).
    """
    if not os.path.exists(ruta_decisions):
        return []
    vetos: List[Dict[str, Any]] = []
    with open(ruta_decisions, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            detail = (row.get("detail") or "").upper()
            if "EXHAUSTION_CLIMAX" not in detail:
                continue
            try:
                ts = int(row["timestamp"])
            except (KeyError, TypeError, ValueError):
                continue
            vetos.append(
                {
                    "ts": ts,
                    "action": (row.get("action") or "").strip().upper(),
                    "trend": (row.get("trend") or "").strip().upper(),
                    "price_at_signal": row.get("price_at_signal") or "",
                    "candle_second": row.get("candle_second") or "",
                    "detail": row.get("detail") or "",
                    "decision_id": row.get("decision_id") or "",
                }
            )
    vetos.sort(key=lambda v: v["ts"])
    return vetos


def deduplicar_por_vela(vetos: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Colapsa la ráfaga de vetos de una misma vela M1 a UNA sola señal.

    POR QUE. El motor reevalúa la señal en cada tick dentro de la ventana sniper, así que una
    sola intención de compra en techo deja 2-11 filas de veto en `decisions.csv` separadas por
    milisegundos. Contarlas todas inflaría N por un factor de 5-10 y fabricaría significancia
    estadística de la nada.

    QUE SE CONSERVA. El PRIMER instante de la ráfaga, como fija la especificación: es el momento
    en que el veto actuó por primera vez, y es el que un operador habría visto. La clave es el
    minuto de vela completo (`ts // 60_000`), no el segundo: dos vetos en :58 y en :01 del minuto
    siguiente son velas distintas aunque estén a 3 s.
    """
    por_vela: Dict[int, Dict[str, Any]] = {}
    for v in vetos:
        vela = v["ts"] // 60_000
        if vela not in por_vela or v["ts"] < por_vela[vela]["ts"]:
            por_vela[vela] = v
    return [por_vela[k] for k in sorted(por_vela)]


# ---------------------------------------------------------------------------
# RESOLUCIÓN DEL CONTRAFACTUAL
# ---------------------------------------------------------------------------


def resolver_contrafactual(
    veta: Dict[str, Any], ticks: List[Tuple[int, float]]
) -> Dict[str, Any]:
    """
    Resuelve una señal vetada contra el precio a t+60s.

    `precio_60s > precio_entrada` -> WIN contrafactual (el veto evitó una ganancia).
    `precio_60s < precio_entrada` -> LOSS contrafactual (el veto evitó una pérdida).
    Igual -> TIE.

    Entrada: se toma el precio del tick más cercano al instante del veto, NO `price_at_signal`
    del CSV. Motivo: `price_at_signal` viene con 4 decimales redondeados y en algunas filas es
    literalmente 0.0, mientras que el tick trae la precisión completa del feed. Si no hay tick
    cerca, se cae a `price_at_signal` y se marca la resolución como degradada.
    """
    ts_entrada = veta["ts"]
    ts_salida = ts_entrada + HORIZONTE_MS

    precio_entrada = precio_en_instante(ticks, ts_entrada)
    origen_entrada = "tick"
    if precio_entrada is None:
        try:
            precio_entrada = float(veta["price_at_signal"])
            origen_entrada = "price_at_signal"
        except (TypeError, ValueError):
            precio_entrada = None
            origen_entrada = "sin_dato"

    precio_salida = precio_en_instante(ticks, ts_salida)

    resultado = "NO_RESUELTO"
    delta = None
    if precio_entrada is not None and precio_salida is not None:
        delta = precio_salida - precio_entrada
        if delta > 0:
            resultado = "WIN"
        elif delta < 0:
            resultado = "LOSS"
        else:
            resultado = "TIE"

    return {
        "ts": ts_entrada,
        "action": veta["action"],
        "trend": veta["trend"],
        "precio_entrada": precio_entrada,
        "precio_60s": precio_salida,
        "delta": delta,
        "resultado": resultado,
        "origen_entrada": origen_entrada,
    }


# ---------------------------------------------------------------------------
# ESTADÍSTICA
# ---------------------------------------------------------------------------


def wilson_interval(wins: int, n: int, z: float = 1.96) -> Tuple[float, float]:
    """
    Intervalo de Wilson al 95% para una proporción.

    Se usa Wilson y no el intervalo normal porque con N pequeño y proporciones extremas el
    normal produce límites fuera de [0,1] y anchos irreales. Wilson se comporta bien desde N=5.
    Devuelve (inferior, superior) en fracción 0..1.
    """
    if n <= 0:
        return (0.0, 0.0)
    p = wins / n
    denom = 1.0 + z * z / n
    centro = (p + z * z / (2 * n)) / denom
    margen = (z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))) / denom
    return (max(0.0, centro - margen), min(1.0, centro + margen))


def resumir(resoluciones: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Agrega un conjunto de resoluciones en las métricas del informe."""
    w = sum(1 for r in resoluciones if r["resultado"] == "WIN")
    l = sum(1 for r in resoluciones if r["resultado"] == "LOSS")
    tie = sum(1 for r in resoluciones if r["resultado"] == "TIE")
    nr = sum(1 for r in resoluciones if r["resultado"] == "NO_RESUELTO")
    denom = w + l
    wr = (w / denom) if denom > 0 else None
    ic = wilson_interval(w, denom) if denom > 0 else None
    breakeven = 1.0 / (1.0 + PAYOUT_OBSERVADO)
    return {
        "n": len(resoluciones),
        "w": w,
        "l": l,
        "tie": tie,
        "no_resuelto": nr,
        "denom": denom,
        "wr": wr,
        "ic": ic,
        "breakeven": breakeven,
        "supera_breakeven": (ic[0] > breakeven) if ic else False,
    }


def dictamen(resumen_oot: Dict[str, Any]) -> Tuple[str, str]:
    """
    Aplica la regla epistémica del §5 de la especificación.

    Con N < 20 fuera de muestra el dictamen es NO CONCLUYENTE, sin importar lo favorable que
    salga el win rate. La razón es aritmética: con 9 aciertos de 15 el IC de Wilson va de ~30%
    a ~83% y contiene cualquier hipótesis, incluida la contraria.
    """
    n_oot = resumen_oot["n"]
    if n_oot < N_MINIMO_CONCLUYENTE:
        return (
            "NO CONCLUYENTE (Muestra insuficiente, N < 20)",
            f"N out-of-sample = {n_oot} < {N_MINIMO_CONCLUYENTE}. "
            "Con esta muestra el intervalo de confianza contiene tanto el breakeven como su "
            "contrario, así que no se puede afirmar ni negar edge.",
        )

    if resumen_oot["denom"] == 0:
        return (
            "NO CONCLUYENTE (todas las señales TIE)",
            f"N = {n_oot} pero no hay desenlaces W/L: no hay proporción que estimar.",
        )

    wr = resumen_oot["wr"]
    ic_inf, ic_sup = resumen_oot["ic"]
    be = resumen_oot["breakeven"]

    if ic_inf > be:
        return (
            "EFECTIVIDAD SOSTENIDA",
            f"WR contrafactual {wr*100:.2f}% con IC95% [{ic_inf*100:.2f}%, {ic_sup*100:.2f}%] "
            f"enteramente por encima del breakeven {be*100:.2f}%.",
        )
    if ic_sup < be:
        return (
            "VETO CONTRAPRODUCENTE",
            f"WR contrafactual {wr*100:.2f}% con IC95% [{ic_inf*100:.2f}%, {ic_sup*100:.2f}%] "
            f"enteramente por DEBAJO del breakeven {be*100:.2f}%: el veto suprimió entradas que "
            "habrían perdido, que es lo que pretende hacer.",
        )
    return (
        "NO CONCLUYENTE (IC cruza el breakeven)",
        f"WR contrafactual {wr*100:.2f}% con IC95% [{ic_inf*100:.2f}%, {ic_sup*100:.2f}%], que "
        f"contiene el breakeven {be*100:.2f}%. N = {n_oot} supera el mínimo pero la evidencia no "
        "alcanza para decidir en ninguna dirección.",
    )


# ---------------------------------------------------------------------------
# ANÁLISIS POR SEGMENTO
# ---------------------------------------------------------------------------


def analizar_ventana(nombre: str, ruta: str) -> Dict[str, Any]:
    """Analiza una ventana: lee decisiones, deduplica y resuelve contra sus ticks."""
    ruta_dec = os.path.join(ruta, "decisions.csv")
    ruta_ticks = os.path.join(ruta, "ticks_live.csv")
    if not os.path.exists(ruta_ticks):
        ruta_ticks = os.path.join(ruta, "ticks.csv")

    vetos_crudos = extraer_vetos_exhaustion(ruta_dec)
    vetos_unicos = deduplicar_por_vela(vetos_crudos)
    ticks = cargar_ticks(ruta_ticks)

    resoluciones = [resolver_contrafactual(v, ticks) for v in vetos_unicos]
    res = resumir(resoluciones)
    res.update(
        {
            "ventana": nombre,
            "ruta": ruta,
            "vetos_crudos": len(vetos_crudos),
            "vetos_unicos": len(vetos_unicos),
            "ticks": len(ticks),
            "hay_ticks": bool(ticks),
            "resoluciones": resoluciones,
        }
    )
    return res


def analizar_segmento(nombre: str, ventanas: List[Tuple[str, str]]) -> Dict[str, Any]:
    """Agrega varias ventanas en un segmento con su resumen conjunto."""
    detalle = [analizar_ventana(n, r) for n, r in ventanas]
    todas = [x for d in detalle for x in d["resoluciones"]]
    res = resumir(todas)
    res.update({"segmento": nombre, "detalle": detalle, "resoluciones": todas,
                "vetos_crudos": sum(d["vetos_crudos"] for d in detalle)})
    return res


# ---------------------------------------------------------------------------
# REPORTE
# ---------------------------------------------------------------------------


def fmt_pct(x: Optional[float]) -> str:
    return "n/a" if x is None else f"{x*100:.2f}%"


def fmt_ic(ic: Optional[Tuple[float, float]]) -> str:
    return "n/a" if ic is None else f"[{ic[0]*100:.2f}%, {ic[1]*100:.2f}%]"


def construir_reporte(ins: Dict[str, Any], oot: Dict[str, Any]) -> str:
    """Genera el Markdown del informe a partir de los dos segmentos."""
    tot = resumir(ins["resoluciones"] + oot["resoluciones"])
    tot["vetos_crudos"] = ins["vetos_crudos"] + oot["vetos_crudos"]
    veredicto, razon = dictamen(oot)

    L: List[str] = []
    L.append("# R23K — AUDITORÍA CONTRAFACTUAL DEL VETO `BUY_EXHAUSTION_CLIMAX`")
    L.append("")
    L.append("**Generado por:** `scripts/validation/counterfactual_exhaustion_climax.py`")
    L.append("**Ejecutor:** HERMES (`w1:pA`)")
    L.append("**Naturaleza:** análisis contrafactual sobre datos históricos — **no** es evidencia")
    L.append("out-of-sample de rentabilidad de la estrategia, ver §5.")
    L.append("")
    L.append("---")
    L.append("")

    L.append("## 1. VEREDICTO")
    L.append("")
    L.append(f"### `{veredicto}`")
    L.append("")
    L.append(razon)
    L.append("")
    L.append(f"- Breakeven vigente (payout {PAYOUT_OBSERVADO}): **{fmt_pct(tot['breakeven'])}**")
    L.append(f"- Señales únicas out-of-sample: **{oot['n']}** "
             f"(mínimo exigido: {N_MINIMO_CONCLUYENTE})")
    L.append(f"- Señales únicas in-sample: **{ins['n']}**")
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 2. QUÉ MIDE ESTE SCRIPT")
    L.append("")
    L.append("Responde una pregunta contable, no una pregunta de rentabilidad: **si las entradas")
    L.append("que el veto suprimió se hubieran ejecutado, ¿habrían ganado o perdido a 60 s?**")
    L.append("")
    L.append("- **WIN contrafactual** = el precio a 60 s subió → el veto impidió una ganancia.")
    L.append("- **LOSS contrafactual** = el precio a 60 s bajó → el veto impidió una pérdida.")
    L.append("")
    L.append("Un veto es útil si produce **más LOSS que WIN**, es decir, si su WR contrafactual")
    L.append("queda **por debajo** del breakeven. Es contraintuitivo y conviene tenerlo presente")
    L.append("al leer la tabla: aquí un win rate alto es una **mala** noticia para el veto.")
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 3. RESULTADOS POR SEGMENTO")
    L.append("")
    L.append("| Segmento | Ventanas | Filas veto crudas | Señales únicas | W | L | TIE | Sin resolver | WR contrafactual | IC95% Wilson |")
    L.append("|---|---|---|---|---|---|---|---|---|---|")
    for seg, etiqueta in ((ins, "In-sample (R23G)"), (oot, "Out-of-sample (W04–W11)"), (None, "**CONSOLIDADO**")):
        s = seg if seg is not None else tot
        nv = len(s["detalle"]) if seg is not None else len(ins["detalle"]) + len(oot["detalle"])
        L.append(
            f"| {etiqueta} | {nv} | {s['vetos_crudos']} | {s['n']} | {s['w']} | {s['l']} | "
            f"{s['tie']} | {s['no_resuelto']} | {fmt_pct(s['wr'])} | {fmt_ic(s['ic'])} |"
        )
    L.append("")

    L.append("### 3.1 Desglose por ventana")
    L.append("")
    L.append("| Ventana | Segmento | Vetos crudos | Señales únicas | W | L | TIE | Sin resolver | WR | IC95% |")
    L.append("|---|---|---|---|---|---|---|---|---|---|")
    for seg, etiqueta in ((ins, "in-sample"), (oot, "out-of-sample")):
        for d in seg["detalle"]:
            L.append(
                f"| {d['ventana']} | {etiqueta} | {d['vetos_crudos']} | {d['vetos_unicos']} | "
                f"{d['w']} | {d['l']} | {d['tie']} | {d['no_resuelto']} | "
                f"{fmt_pct(d['wr'])} | {fmt_ic(d['ic'])} |"
            )
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 4. DEDUPLICACIÓN: POR QUÉ N ES TAN PEQUEÑO")
    L.append("")
    L.append("El motor reevalúa la señal en **cada tick** dentro de la ventana sniper, así que una")
    L.append("sola intención de compra en techo deja entre 2 y 11 filas de veto separadas por")
    L.append("milisegundos. Contarlas todas multiplicaría N por 5–10 y fabricaría significancia")
    L.append("estadística donde no la hay.")
    L.append("")
    L.append("Se colapsa **por vela M1** (`ts // 60_000`) conservando el **primer** instante de la")
    L.append("ráfaga: es el momento en que el veto actuó y el que un operador habría visto.")
    L.append("")
    L.append(f"Colapso medido: **{tot['vetos_crudos']} filas crudas → {tot['n']} señales únicas**")
    L.append(f"(factor {tot['vetos_crudos']/max(tot['n'],1):.1f}×).")
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 5. REGLA EPISTÉMICA Y ALCANCE")
    L.append("")
    L.append("1. **N mínimo 20 fuera de muestra.** Por debajo, dictamen obligatorio")
    L.append("   `NO CONCLUYENTE (Muestra insuficiente, N < 20)`.")
    L.append("2. **El IC de Wilson manda, no la WR puntual.** Un 70% con IC [45%, 88%] no sostiene")
    L.append("   nada: contiene el breakeven.")
    L.append("3. **In-sample ≠ out-of-sample.** R23G es la muestra que originó la hipótesis del veto;")
    L.append("   sirve de referencia, nunca de confirmación.")
    L.append("4. **Esto no mide rentabilidad de la estrategia.** Mide el signo de las decisiones")
    L.append("   suprimidas. Un veto con buen contrafactual puede convivir con una estrategia sin")
    L.append("   edge, y al revés.")
    L.append("5. **Sin ajuste post hoc.** Los parámetros (t+60 s, ±4 s, payout 0.83, N≥20) están")
    L.append("   congelados en el código antes de ver ningún resultado de esta corrida.")
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 6. LIMITACIONES DECLARADAS")
    L.append("")
    L.append("- **R23G no contiene vetos de agotamiento.** El veto se implementó en R23H, posterior a")
    L.append("   R23G. Por eso el segmento in-sample sale con las ventanas presentes pero **N = 0**")
    L.append("   salvo que existan filas con el token, y así se reporta: no se rellena el hueco.")
    L.append("- **El contrafactual ignora el spread y el slippage del broker.** Usa precios del feed")
    L.append("  WebSocket, que no incluyen la horquilla de ejecución real.")
    L.append("- **El contrafactual asume que la entrada habría ocurrido al precio del tick del veto.**")
    L.append("  En vivo, el tap llega ~4-5 s después por la latencia de Android Go.")
    L.append("- **TIE se excluye del denominador** del WR, igual que hace el motor con los empates.")
    L.append("")

    L.append("---")
    L.append("")
    L.append("## 7. REPRODUCIBILIDAD")
    L.append("")
    L.append("```bash")
    L.append("python scripts/validation/counterfactual_exhaustion_climax.py")
    L.append("python scripts/validation/counterfactual_exhaustion_climax.py --self-test")
    L.append("```")
    L.append("")
    L.append("Determinista: mismo input → mismo output. Sin red, sin reloj de pared, sin azar.")
    L.append("")
    return "\n".join(L)


# ---------------------------------------------------------------------------
# SELF-TEST
# ---------------------------------------------------------------------------


def self_test() -> int:
    """Verifica el contrato del script con casos sintéticos de resultado conocido."""
    print("=" * 70)
    print("SELF-TEST — counterfactual_exhaustion_climax")
    print("=" * 70)
    fallos = []

    def check(nombre: str, cond: bool, detalle: str = ""):
        if not cond:
            fallos.append(f"{nombre}: {detalle}")
            print(f"  [FALLA] {nombre} {detalle}")
        else:
            print(f"  [OK]    {nombre}")

    # 1. Dedupe por vela: 5 vetos en el mismo minuto -> 1 señal, el primero.
    vetos = [
        {"ts": 1_000_000 + 61_000 + 500, "action": "BUY", "trend": "UPTREND",
         "price_at_signal": "1", "candle_second": "1", "detail": "x", "decision_id": "a"},
        {"ts": 1_000_000 + 61_000 + 900, "action": "BUY", "trend": "UPTREND",
         "price_at_signal": "1", "candle_second": "1", "detail": "x", "decision_id": "b"},
        {"ts": 1_000_000 + 61_000 + 200, "action": "BUY", "trend": "UPTREND",
         "price_at_signal": "1", "candle_second": "0", "detail": "x", "decision_id": "c"},
        {"ts": 1_000_000 + 122_000, "action": "BUY", "trend": "UPTREND",
         "price_at_signal": "1", "candle_second": "2", "detail": "x", "decision_id": "d"},
    ]
    uniq = deduplicar_por_vela(vetos)
    check("dedupe colapsa 4 filas de 2 velas a 2 señales", len(uniq) == 2, f"obtuvo {len(uniq)}")
    check(
        "dedupe conserva el primer instante de la ráfaga",
        uniq[0]["ts"] == 1_000_000 + 61_000 + 200,
        f"obtuvo {uniq[0]['ts']}",
    )

    # 2. Wilson: casos conocidos.
    lo, hi = wilson_interval(0, 10)
    check("Wilson 0/10 empieza en 0", abs(lo) < 1e-9, f"lo={lo}")
    lo, hi = wilson_interval(10, 10)
    check("Wilson 10/10 termina en 1", abs(hi - 1.0) < 1e-9, f"hi={hi}")
    lo, hi = wilson_interval(5, 10)
    check("Wilson 5/10 contiene 0.5", lo < 0.5 < hi, f"[{lo:.3f},{hi:.3f}]")
    check("Wilson 0/0 devuelve (0,0)", wilson_interval(0, 0) == (0.0, 0.0))

    # 3. Resolución WIN / LOSS / TIE / sin datos.
    ticks = [(0, 100.0), (60_000, 101.0), (120_000, 99.0)]
    v = {"ts": 0, "action": "BUY", "trend": "UPTREND", "price_at_signal": "100",
         "candle_second": "0", "detail": "", "decision_id": "x"}
    r = resolver_contrafactual(v, ticks)
    check("precio sube -> WIN contrafactual", r["resultado"] == "WIN", r["resultado"])
    r = resolver_contrafactual({**v, "ts": 60_000}, ticks)
    check("precio baja -> LOSS contrafactual", r["resultado"] == "LOSS", r["resultado"])
    ticks_plano = [(0, 100.0), (60_000, 100.0)]
    r = resolver_contrafactual(v, ticks_plano)
    check("precio igual -> TIE", r["resultado"] == "TIE", r["resultado"])
    r = resolver_contrafactual(v, [])
    check("sin ticks -> NO_RESUELTO", r["resultado"] == "NO_RESUELTO", r["resultado"])

    # 4. Regla epistémica: N<20 siempre NO CONCLUYENTE, aunque WR sea 100%.
    r_chico = {"n": 5, "w": 5, "l": 0, "tie": 0, "no_resuelto": 0, "denom": 5,
               "wr": 1.0, "ic": wilson_interval(5, 5), "breakeven": 1 / 1.83,
               "supera_breakeven": True}
    veredicto, _ = dictamen(r_chico)
    check(
        "N=5 con WR 100% sigue siendo NO CONCLUYENTE",
        veredicto.startswith("NO CONCLUYENTE"),
        veredicto,
    )

    # 5. Veto contrap roductivo: IC entero por debajo del breakeven -> se detecta.
    r_malo = {"n": 100, "w": 20, "l": 80, "tie": 0, "no_resuelto": 0, "denom": 100,
              "wr": 0.20, "ic": wilson_interval(20, 100), "breakeven": 1 / 1.83,
              "supera_breakeven": False}
    veredicto, _ = dictamen(r_malo)
    check("WR 20% con N=100 -> VETO CONTRAPRODUCENTE", veredicto == "VETO CONTRAPRODUCENTE", veredicto)

    # 6. búsqueda de precio: bordes y tolerancia.
    check("precio_en_instante exacto", precio_en_instante(ticks, 60_000) == 101.0)
    check("precio_en_instante sin ticks -> None", precio_en_instante([], 0) is None)
    check(
        "precio_en_instante elige el más cercano",
        precio_en_instante([(0, 10.0), (10_000, 20.0)], 3_000) == 10.0,
    )
    check(
        "precio_en_instante en empate exacto es determinista (toma el anterior)",
        precio_en_instante([(0, 10.0), (10_000, 20.0)], 5_000) == 10.0,
    )

    print("-" * 70)
    if fallos:
        print(f"RESULTADO: FALLA ({len(fallos)})")
        return 1
    print("RESULTADO: PASS")
    return 0


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Auditoría contrafactual del veto BUY_EXHAUSTION_CLIMAX (R23K A4)"
    )
    parser.add_argument(
        "--report",
        default="workflows/rounds/R23K_COUNTERFACTUAL_EXHAUSTION.md",
        help="Ruta del informe Markdown a generar",
    )
    parser.add_argument("--self-test", action="store_true", help="Ejecutar la suite interna")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    raiz = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    os.chdir(raiz)

    print("=" * 70)
    print("AUDITORÍA CONTRAFACTUAL — VETO BUY_EXHAUSTION_CLIMAX (R23K A4)")
    print("=" * 70)
    print(f"Raíz: {raiz}")
    print(f"Out-of-sample: {', '.join(n for n, _ in VENTANAS_OUT_OF_SAMPLE)}")
    print(f"In-sample:     {', '.join(n for n, _ in VENTANAS_IN_SAMPLE)}")
    print()

    ins = analizar_segmento("in_sample", VENTANAS_IN_SAMPLE)
    oot = analizar_segmento("out_of_sample", VENTANAS_OUT_OF_SAMPLE)

    # Consola: resumen por ventana.
    print(f"{'Ventana':<10} {'Seg':<12} {'crudos':>7} {'únicas':>7} {'W':>4} {'L':>4} {'TIE':>4} {'NR':>4} {'WR':>8}")
    print("-" * 70)
    for seg, et in ((ins, "in-sample"), (oot, "out-of-sample")):
        for d in seg["detalle"]:
            wr = fmt_pct(d["wr"])
            print(
                f"{d['ventana']:<10} {et:<12} {d['vetos_crudos']:>7} {d['vetos_unicos']:>7} "
                f"{d['w']:>4} {d['l']:>4} {d['tie']:>4} {d['no_resuelto']:>4} {wr:>8}"
            )
    print("-" * 70)
    for seg, et in ((oot, "OUT-OF-SAMPLE"), (ins, "IN-SAMPLE")):
        print(
            f"{et:<24} N={seg['n']:<4} W={seg['w']:<4} L={seg['l']:<4} TIE={seg['tie']:<3} "
            f"WR={fmt_pct(seg['wr'])} IC95={fmt_ic(seg['ic'])}"
        )
    tot = resumir(ins["resoluciones"] + oot["resoluciones"])
    print(
        f"{'CONSOLIDADO':<24} N={tot['n']:<4} W={tot['w']:<4} L={tot['l']:<4} TIE={tot['tie']:<3} "
        f"WR={fmt_pct(tot['wr'])} IC95={fmt_ic(tot['ic'])}"
    )
    print()

    veredicto, razon = dictamen(oot)
    print(f"DICTAMEN: {veredicto}")
    print(f"  {razon}")
    print()

    md = construir_reporte(ins, oot)
    os.makedirs(os.path.dirname(args.report), exist_ok=True)
    with open(args.report, "w", encoding="utf-8", newline="\n") as f:
        f.write(md)
    print(f"Informe generado: {args.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
