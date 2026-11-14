#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R23K C3 — COMPARADOR v2: LIVE vs REPLAY, TICK A TICK.

QUE CAMBIA RESPECTO AL v1. El v1 compara TRADES agregados: empareja órdenes confirmadas contra el
journal del replay por `decision_id` o por cercanía temporal. Eso funciona para contar trades, pero
no puede responder "¿el motor decidió lo mismo en cada instante?".

El v2 compara DECISIONES: alinea `decisions.csv` del Live contra `decisions_replay.csv` del replay
por timestamp del frame del socket, con tolerancia de ±50 ms, y contrasta el TIPO de decisión
(entrada / bloqueo con su tipo / abstención).

ASIMETRÍA ESTRUCTURAL QUE EL COMPARADOR DEBE RESPETAR. El Live NO registra una fila por tick: el
motor solo emite evento cuando algo ocurre (veta o entra). El replay emite una fila por tick
evaluado. Comparar fila contra fila daría un desajuste masivo que no significa nada. El comparador,
por tanto, mide:

  - **Cobertura**: qué fracción de las decisiones Live tiene contraparte en el replay.
  - **Concordancia de tipo**: de las Live con contraparte, cuántas coinciden en tipo de bloqueo.
  - **Entradas**: si el replay entra donde el Live entró.

CRITERIOS DE B5 (preregistro R23K §5.2), que este script reporta:
  (i)   Reproducción de entradas  >= 90% sobre >= 20 trades Live, misma acción en ±6 s
  (ii)  Reproducción del TIPO de bloqueo >= 90% sobre >= 200 decisiones comunes
  (iii) Cobertura de decisiones Live con contraparte >= 70%

DETERMINISMO. Mismo par de CSV -> mismo informe, byte a byte. Sin red, sin azar, sin reloj.

Uso:
    python scripts/validation/compare_live_replay_v2.py --live <decisions.csv> --replay <decisions_replay.csv>
    python scripts/validation/compare_live_replay_v2.py --self-test
    python scripts/validation/compare_live_replay_v2.py --sweep   # W05..W11
"""

import argparse
import csv
import json
import math
import os
import sys
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# PARÁMETROS CONGELADOS
# ---------------------------------------------------------------------------

TOLERANCIA_ALINEACION_MS = 50       # alineación por timestamp del frame del socket
TOLERANCIA_ENTRADA_MS = 6_000       # criterio B5(i): misma acción dentro de ±6 s

# Umbrales de B5
B5_ENTRADAS_PCT = 90.0
B5_ENTRADAS_MIN = 20
B5_TIPOS_PCT = 90.0
B5_TIPOS_MIN = 200
B5_COBERTURA_PCT = 70.0

# Tipos de bloqueo que se distinguen (B5 §5.2).
TIPOS_BLOQUEO = ("ADAPTIVE_BLOCK", "VETO_BUY_EXHAUSTION_CLIMAX", "V1", "V2", "RISK_LIMIT")


# ---------------------------------------------------------------------------
# NORMALIZACIÓN DE TIPO
# ---------------------------------------------------------------------------


def clasificar_tipo(fila: Dict[str, Any]) -> str:
    """
    Reduce una fila de decisión a su TIPO canónico.

    Se mira `detail` y no `kind`, porque `kind` solo distingue VETO/ORDER_STATE/TAP y el criterio
    B5(ii) exige distinguir el MOTIVO del bloqueo. El orden de comprobación importa: `V2` es
    subcadena de otros textos, así que se evalúa antes que las comparaciones genéricas.
    """
    kind = (fila.get("kind") or "").strip().upper()
    detail = (fila.get("detail") or "").upper()

    if kind in ("SKIP",):
        return "SKIP"
    if kind == "TAP" or "CONFIRMADA" in detail:
        return "ENTRADA"
    if kind == "ORDER_STATE":
        return "ENTRADA"

    if "EXHAUSTION_CLIMAX" in detail:
        return "VETO_BUY_EXHAUSTION_CLIMAX"
    if "ADAPTIVE_BLOCK" in detail:
        return "ADAPTIVE_BLOCK"
    if detail.startswith("V2") or " V2" in detail or "V2_RANGE" in detail:
        return "V2"
    if detail.startswith("V1") or " V1" in detail:
        return "V1"
    if "RISK_LIMIT" in detail or "OPERACI" in detail and "ABIERTA" in detail:
        return "RISK_LIMIT"
    if "COOLDOWN" in detail:
        return "COOLDOWN"
    if "CHOP" in detail:
        return "CHOP"
    if "DESAJUSTE" in detail:
        return "DESAJUSTE"
    if "FEED_RANCIO" in detail:
        return "FEED_RANCIO"
    if kind == "VETO":
        return "OTRO_VETO"
    if kind == "NONE" or not kind:
        return "NONE"
    return "OTRO"


def extraer_decisiones(ruta: str, incluir_skip: bool = False) -> List[Dict[str, Any]]:
    """
    Lee un CSV de decisiones y devuelve filas normalizadas.

    `incluir_skip=False` descarta las filas SKIP del replay (fuera de ventana sniper): no son
    decisiones, son la ausencia de evaluación. Incluirlas inflaría el denominador de cobertura.
    """
    if not os.path.exists(ruta):
        return []
    salida: List[Dict[str, Any]] = []
    with open(ruta, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            try:
                ts = int(row.get("timestamp") or 0)
            except (TypeError, ValueError):
                continue
            tipo = clasificar_tipo(row)
            if tipo == "SKIP" and not incluir_skip:
                continue
            salida.append(
                {
                    "ts": ts,
                    "tipo": tipo,
                    "action": (row.get("action") or "NONE").strip().upper(),
                    "trend": (row.get("trend") or "").strip().upper(),
                    "candle_second": row.get("candle_second") or "",
                    "detail": row.get("detail") or "",
                    "decision_id": row.get("decision_id") or "",
                }
            )
    salida.sort(key=lambda x: x["ts"])
    return salida


# ---------------------------------------------------------------------------
# ALINEACIÓN
# ---------------------------------------------------------------------------


def alinear(
    live: List[Dict[str, Any]],
    replay: List[Dict[str, Any]],
    tolerancia_ms: int = TOLERANCIA_ALINEACION_MS,
) -> List[Dict[str, Any]]:
    """
    Empareja cada decisión Live con la decisión Replay más cercana en el tiempo.

    Algoritmo: para cada fila Live se busca, entre las Replay NO consumidas, la de menor |Δt| que
    quepa en la tolerancia. Consumo sin reutilización: una decisión del replay no puede justificar
    dos decisiones Live distintas.

    La búsqueda es lineal sobre las no consumidas. Con ventanas de ~100 decisiones Live y ~400
    replay son 40.000 comparaciones: irrelevante. Optimizarlo con índices añadiría código que hay
    que auditar a cambio de milisegundos que nadie va a notar.
    """
    consumidas = set()
    pares: List[Dict[str, Any]] = []

    for lv in live:
        mejor_idx, mejor_delta = None, None
        for idx, rp in enumerate(replay):
            if idx in consumidas:
                continue
            delta = abs(rp["ts"] - lv["ts"])
            if delta <= tolerancia_ms and (mejor_delta is None or delta < mejor_delta):
                mejor_idx, mejor_delta = idx, delta

        if mejor_idx is None:
            pares.append({"live": lv, "replay": None, "delta_ms": None, "concuerda": False})
            continue

        consumidas.add(mejor_idx)
        rp = replay[mejor_idx]
        pares.append(
            {
                "live": lv,
                "replay": rp,
                "delta_ms": rp["ts"] - lv["ts"],
                "concuerda": lv["tipo"] == rp["tipo"],
            }
        )
    return pares


def alinear_entradas(
    live_entradas: List[Dict[str, Any]],
    replay_entradas: List[Dict[str, Any]],
    tolerancia_ms: int = TOLERANCIA_ENTRADA_MS,
) -> Dict[str, Any]:
    """
    Criterio B5(i): empareja ENTRADAS por acción y ventana de ±6 s.

    Se separa de `alinear` porque el criterio es distinto: una entrada Live y su equivalente en
    replay pueden caer en ticks separados por segundos (la decisión se reevalúa) y eso sigue siendo
    la misma entrada. Con ±50 ms se perdería sistemáticamente.
    """
    consumidas = set()
    aciertos = 0
    detalles = []
    for lv in live_entradas:
        match = None
        for idx, rp in enumerate(replay_entradas):
            if idx in consumidas or rp["action"] != lv["action"]:
                continue
            if abs(rp["ts"] - lv["ts"]) <= tolerancia_ms:
                match = idx
                break
        if match is not None:
            consumidas.add(match)
            aciertos += 1
            detalles.append({"live": lv, "replay": replay_entradas[match], "ok": True})
        else:
            detalles.append({"live": lv, "replay": None, "ok": False})

    total = len(live_entradas)
    return {
        "total_live": total,
        "reproducidas": aciertos,
        "pct": (aciertos / total * 100.0) if total else None,
        "cumple_minimo": total >= B5_ENTRADAS_MIN,
        "cumple_pct": (total > 0 and (aciertos / total * 100.0) >= B5_ENTRADAS_PCT),
        "detalle": detalles,
    }


# ---------------------------------------------------------------------------
# COMPARACIÓN COMPLETA
# ---------------------------------------------------------------------------


def comparar(
    live: List[Dict[str, Any]],
    replay: List[Dict[str, Any]],
    tolerancia_ms: int = TOLERANCIA_ALINEACION_MS,
) -> Dict[str, Any]:
    """Compara dos conjuntos de decisiones y devuelve el informe completo."""
    pares = alinear(live, replay, tolerancia_ms)
    con_par = [p for p in pares if p["replay"] is not None]
    concordantes = [p for p in con_par if p["concuerda"]]

    # Distribución de tipos LIVE y su reproducción
    tipos_live: Dict[str, int] = {}
    tipos_ok: Dict[str, int] = {}
    for p in pares:
        t = p["live"]["tipo"]
        tipos_live[t] = tipos_live.get(t, 0) + 1
        if p["replay"] is not None and p["concuerda"]:
            tipos_ok[t] = tipos_ok.get(t, 0) + 1

    # Cobertura: qué fracción de decisiones Live tiene contraparte
    cobertura_pct = (len(con_par) / len(live) * 100.0) if live else None
    # Concordancia: de las que tienen par, cuántas coinciden en tipo
    concordancia_pct = (len(concordantes) / len(con_par) * 100.0) if con_par else None

    # Entradas (B5 i) y divergencias de acción
    live_entradas = [d for d in live if d["tipo"] == "ENTRADA"]
    replay_entradas = [d for d in replay if d["tipo"] == "ENTRADA"]
    entradas = alinear_entradas(live_entradas, replay_entradas)

    divergencias_accion = [
        p for p in con_par
        if p["live"]["action"] != p["replay"]["action"]
        and p["live"]["action"] not in ("NONE", "")
        and p["replay"]["action"] not in ("NONE", "")
    ]

    return {
        "total_live": len(live),
        "total_replay": len(replay),
        "con_par": len(con_par),
        "sin_par": len(live) - len(con_par),
        "cobertura_pct": cobertura_pct,
        "concordancia_pct": concordancia_pct,
        "tipos_live": tipos_live,
        "tipos_ok": tipos_ok,
        "entradas": entradas,
        "divergencias_accion": len(divergencias_accion),
        "detalle_divergencias_accion": divergencias_accion[:20],
        "pares": pares,
    }


# ---------------------------------------------------------------------------
# VEREDICTOS B5
# ---------------------------------------------------------------------------


def veredicto_b5(res: Dict[str, Any]) -> Dict[str, Any]:
    """
    Aplica los tres criterios de B5 y devuelve el estado de cada uno.

    Sin muestra suficiente un criterio queda `NO EVALUABLE`, no `FALLA`: son cosas distintas y
    confundirlas es exactamente el error que el preregistro intenta evitar.
    """
    ent = res["entradas"]
    v1 = {
        "criterio": "B5(i) reproduccion de entradas",
        "valor": ent["pct"],
        "umbral": f">={B5_ENTRADAS_PCT}% sobre >={B5_ENTRADAS_MIN} trades Live",
        "estado": (
            "NO EVALUABLE" if not ent["cumple_minimo"]
            else ("CUMPLE" if ent["cumple_pct"] else "FALLA")
        ),
    }

    con_par = res["con_par"]
    concordancia = res["concordancia_pct"]
    v2 = {
        "criterio": "B5(ii) reproduccion del tipo de bloqueo",
        "valor": concordancia,
        "umbral": f">={B5_TIPOS_PCT}% sobre >={B5_TIPOS_MIN} decisiones comunes",
        "estado": (
            "NO EVALUABLE" if con_par < B5_TIPOS_MIN
            else ("CUMPLE" if (concordancia or 0) >= B5_TIPOS_PCT else "FALLA")
        ),
    }

    cob = res["cobertura_pct"]
    v3 = {
        "criterio": "B5(iii) cobertura de decisiones Live",
        "valor": cob,
        "umbral": f">={B5_COBERTURA_PCT}%",
        "estado": (
            "NO EVALUABLE" if cob is None
            else ("CUMPLE" if cob >= B5_COBERTURA_PCT else "FALLA")
        ),
    }
    return {"i": v1, "ii": v2, "iii": v3}


# ---------------------------------------------------------------------------
# REPORTE
# ---------------------------------------------------------------------------


def fmt(v: Optional[float], sufijo: str = "%") -> str:
    return "n/a" if v is None else f"{v:.2f}{sufijo}"


def informe_md(
    nombre: str,
    res: Dict[str, Any],
    veredicto: Dict[str, Any],
    etiqueta: str = "",
) -> str:
    L: List[str] = []
    L.append(f"# PARIDAD v2 — {nombre}")
    L.append("")
    if etiqueta:
        L.append(f"> **{etiqueta}**")
        L.append("")
    L.append("## Resumen")
    L.append("")
    L.append("| Métrica | Valor |")
    L.append("|---|---|")
    L.append(f"| Decisiones Live | {res['total_live']} |")
    L.append(f"| Decisiones Replay (evaluadas) | {res['total_replay']} |")
    L.append(f"| Con contraparte (±{TOLERANCIA_ALINEACION_MS} ms) | {res['con_par']} |")
    L.append(f"| Sin contraparte | {res['sin_par']} |")
    L.append(f"| **Cobertura** | **{fmt(res['cobertura_pct'])}** |")
    L.append(f"| **Concordancia de tipo** | **{fmt(res['concordancia_pct'])}** |")
    L.append(f"| Divergencias de ACCIÓN | {res['divergencias_accion']} |")
    L.append("")
    L.append("## Criterios B5")
    L.append("")
    L.append("| Criterio | Valor | Umbral | Estado |")
    L.append("|---|---|---|---|")
    for k in ("i", "ii", "iii"):
        v = veredicto[k]
        L.append(f"| {v['criterio']} | {fmt(v['valor'])} | {v['umbral']} | **{v['estado']}** |")
    L.append("")
    L.append("## Reproducción por tipo de decisión")
    L.append("")
    L.append("| Tipo Live | Ocurrencias | Reproducidas | % |")
    L.append("|---|---|---|---|")
    for t, n in sorted(res["tipos_live"].items(), key=lambda x: -x[1]):
        ok = res["tipos_ok"].get(t, 0)
        pct = (ok / n * 100.0) if n else 0.0
        L.append(f"| `{t}` | {n} | {ok} | {pct:.1f}% |")
    L.append("")
    return "\n".join(L)


# ---------------------------------------------------------------------------
# SELF-TEST
# ---------------------------------------------------------------------------


def self_test() -> int:
    print("=" * 70)
    print("SELF-TEST — compare_live_replay_v2")
    print("=" * 70)
    fallos = []

    def check(nombre, cond, detalle=""):
        if cond:
            print(f"  [OK]    {nombre}")
        else:
            fallos.append(nombre)
            print(f"  [FALLA] {nombre} {detalle}")

    def fila(ts, kind, detail, action="NONE"):
        return {"ts": ts, "kind": kind, "detail": detail, "action": action}

    # --- clasificar_tipo
    check("VETO de agotamiento", clasificar_tipo(fila(0, "VETO", "VETO_BUY_EXHAUSTION_CLIMAX UPTREND")) == "VETO_BUY_EXHAUSTION_CLIMAX")
    check("ADAPTIVE_BLOCK", clasificar_tipo(fila(0, "VETO", "ADAPTIVE_BLOCK [sim:99%]")) == "ADAPTIVE_BLOCK")
    check("RISK_LIMIT", clasificar_tipo(fila(0, "VETO", "RISK_LIMIT Operación abierta en curso (3s)")) == "RISK_LIMIT")
    check("COOLDOWN", clasificar_tipo(fila(0, "VETO", "COOLDOWN 3950ms")) == "COOLDOWN")
    check("ORDER_STATE -> ENTRADA", clasificar_tipo(fila(0, "ORDER_STATE", "algo")) == "ENTRADA")
    check("TAP -> ENTRADA", clasificar_tipo(fila(0, "TAP", "algo")) == "ENTRADA")
    check("SKIP", clasificar_tipo(fila(0, "SKIP", "FUERA_VENTANA_SNIPER")) == "SKIP")
    check("V1 no se confunde con V2", clasificar_tipo(fila(0, "VETO", "V1 exclusividad S/R")) == "V1")
    check("V2 detectado", clasificar_tipo(fila(0, "VETO", "V2 rango insuficiente")) == "V2")

    # --- alineación exacta
    live = [
        {"ts": 1000, "tipo": "ADAPTIVE_BLOCK", "action": "BUY", "trend": "", "candle_second": "", "detail": "", "decision_id": "a"},
        {"ts": 2000, "tipo": "RISK_LIMIT", "action": "SELL", "trend": "", "candle_second": "", "detail": "", "decision_id": "b"},
    ]
    replay = [
        {"ts": 1020, "tipo": "ADAPTIVE_BLOCK", "action": "BUY", "trend": "", "candle_second": "", "detail": "", "decision_id": "x"},
        {"ts": 2010, "tipo": "RISK_LIMIT", "action": "SELL", "trend": "", "candle_second": "", "detail": "", "decision_id": "y"},
    ]
    pares = alinear(live, replay, 50)
    check("alinea 2 pares dentro de 50ms", len([p for p in pares if p["replay"]]) == 2)
    check("ambos concuerdan en tipo", all(p["concuerda"] for p in pares if p["replay"]))

    # --- fuera de tolerancia
    replay_lejos = [{"ts": 1200, "tipo": "ADAPTIVE_BLOCK", "action": "BUY", "trend": "", "candle_second": "", "detail": "", "decision_id": "x"}]
    pares2 = alinear([live[0]], replay_lejos, 50)
    check("fuera de 50ms no alinea", pares2[0]["replay"] is None)

    # --- no reutiliza el mismo par
    live_doble = [live[0], {**live[0], "ts": 1030}]
    replay_uno = [replay[0]]
    pares3 = alinear(live_doble, replay_uno, 50)
    con_par = [p for p in pares3 if p["replay"]]
    check("una decision replay no justifica dos Live", len(con_par) == 1)

    # --- veredictos: NO EVALUABLE por muestra insuficiente
    res_chico = {
        "total_live": 3, "total_replay": 10, "con_par": 3, "sin_par": 0,
        "cobertura_pct": 100.0, "concordancia_pct": 100.0,
        "tipos_live": {"ADAPTIVE_BLOCK": 3}, "tipos_ok": {"ADAPTIVE_BLOCK": 3},
        "entradas": {"total_live": 3, "reproducidas": 3, "pct": 100.0,
                     "cumple_minimo": False, "cumple_pct": True, "detalle": []},
        "divergencias_accion": 0, "detalle_divergencias_accion": [], "pares": [],
    }
    v = veredicto_b5(res_chico)
    check("3 entradas -> NO EVALUABLE, no FALLA", v["i"]["estado"] == "NO EVALUABLE", v["i"]["estado"])
    check("3 comunes -> NO EVALUABLE en (ii)", v["ii"]["estado"] == "NO EVALUABLE", v["ii"]["estado"])
    check("100% cobertura -> CUMPLE en (iii)", v["iii"]["estado"] == "CUMPLE", v["iii"]["estado"])

    # --- cobertura baja falla
    res_cob = {**res_chico, "cobertura_pct": 50.0}
    check("50% cobertura -> FALLA en (iii)", veredicto_b5(res_cob)["iii"]["estado"] == "FALLA")

    # --- entradas con muestra suficiente
    res_ent = {
        **res_chico,
        "entradas": {"total_live": 25, "reproducidas": 20, "pct": 80.0,
                     "cumple_minimo": True, "cumple_pct": False, "detalle": []},
    }
    check("80% sobre 25 entradas -> FALLA", veredicto_b5(res_ent)["i"]["estado"] == "FALLA")
    res_ent_ok = {
        **res_chico,
        "entradas": {"total_live": 25, "reproducidas": 24, "pct": 96.0,
                     "cumple_minimo": True, "cumple_pct": True, "detalle": []},
    }
    check("96% sobre 25 entradas -> CUMPLE", veredicto_b5(res_ent_ok)["i"]["estado"] == "CUMPLE")

    # --- alineación de entradas con ±6s
    lv_e = [{"ts": 10_000, "tipo": "ENTRADA", "action": "BUY", "trend": "", "candle_second": "", "detail": "", "decision_id": "e1"}]
    rp_e = [{"ts": 14_000, "tipo": "ENTRADA", "action": "BUY", "trend": "", "candle_second": "", "detail": "", "decision_id": "e2"}]
    ent = alinear_entradas(lv_e, rp_e)
    check("entrada a 4s si se reproduce con ±6s", ent["reproducidas"] == 1)
    ent2 = alinear_entradas(lv_e, [{**rp_e[0], "action": "SELL"}])
    check("entrada con accion distinta NO se reproduce", ent2["reproducidas"] == 0)

    print("-" * 70)
    if fallos:
        print(f"RESULTADO: FALLA ({len(fallos)})")
        return 1
    print("RESULTADO: PASS")
    return 0


# ---------------------------------------------------------------------------
# BARRIDO W05..W11
# ---------------------------------------------------------------------------

VENTANAS_SWEEP = [
    ("W05", "loops/r23h_validation/window_05_clean"),
    ("W06", "loops/r23i_validation/window_06_clean"),
    ("W07", "loops/r23j_validation/window_07_clean"),
    ("W08", "loops/r23j_validation/window_08_clean"),
    ("W09", "loops/r23j_validation/window_09_clean"),
    ("W10", "loops/r23j_validation/window_10_clean"),
    ("W11", "loops/r23j_validation/window_11_clean"),
]


def sweep(salida_md: str) -> int:
    """
    Reejecuta el comparador v2 sobre W05..W11.

    ETIQUETA OBLIGATORIA. Este barrido es **retrospectivo y no independiente**: usa ventanas que ya
    sirvieron para construir la memoria de anti-patrones del teléfono y para calibrar el propio
    arnés. Que el replay "reproduzca" estas ventanas no valida el arnés; solo detecta divergencias
    groseras. La validación real exige ventanas posteriores a la construcción del v2.
    """
    raiz = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    os.chdir(raiz)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from replay_v2_tick import cargar_muestras_contexto, cargar_ticks, ejecutar_replay  # noqa

    lineas = [
        "# R23K C4 — PARIDAD v2 TICK A TICK: SWEEP W05–W11",
        "",
        "> **RETROSPECTIVO Y NO INDEPENDIENTE.** Estas ventanas ya se usaron para construir la memoria",
        "> de anti-patrones y para calibrar el arnés. El resultado detecta divergencias groseras; no",
        "> valida el arnés.",
        "",
        "| Ventana | Live | Replay | Con par | Cobertura | Concordancia | B5(i) | B5(ii) | B5(iii) |",
        "|---|---|---|---|---|---|---|---|---|",
    ]

    for nombre, ruta in VENTANAS_SWEEP:
        dec_live = os.path.join(ruta, "decisions.csv")
        ticks = os.path.join(ruta, "ticks_live.csv")
        samples = os.path.join(ruta, "live_samples.jsonl")
        if not (os.path.exists(dec_live) and os.path.exists(ticks)):
            lineas.append(f"| {nombre} | — | — | — | n/a | n/a | — | — | — |")
            continue

        out_tmp = os.path.join(os.path.dirname(ticks), "_v2_tmp")
        ejecutar_replay(
            cargar_ticks(ticks), out_tmp, submode="YOLO",
            muestras=cargar_muestras_contexto(samples) or None,
        )
        rep = extraer_decisiones(os.path.join(out_tmp, "decisions_replay.csv"))
        liv = extraer_decisiones(dec_live)
        res = comparar(liv, rep)
        v = veredicto_b5(res)
        lineas.append(
            f"| {nombre} | {res['total_live']} | {res['total_replay']} | {res['con_par']} | "
            f"{fmt(res['cobertura_pct'])} | {fmt(res['concordancia_pct'])} | "
            f"{v['i']['estado']} | {v['ii']['estado']} | {v['iii']['estado']} |"
        )
        print(
            f"{nombre}: live={res['total_live']} replay={res['total_replay']} "
            f"par={res['con_par']} cob={fmt(res['cobertura_pct'])} conc={fmt(res['concordancia_pct'])}"
        )

    with open(salida_md, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lineas) + "\n")
    print(f"\nInforme: {salida_md}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Comparador v2 tick-a-tick (R23K C3)")
    parser.add_argument("--live", help="decisions.csv del lado Live")
    parser.add_argument("--replay", help="decisions_replay.csv del replay v2")
    parser.add_argument("--out-md", default="", help="Ruta del informe Markdown")
    parser.add_argument("--sweep", action="store_true", help="Barrido W05..W11 (retrospectivo)")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if args.sweep:
        return sweep(args.out_md or "workflows/rounds/R23K_C4_PARIDAD_V2_SWEEP.md")

    if not (args.live and args.replay):
        parser.error("--live y --replay son obligatorios (o usa --self-test / --sweep)")

    liv = extraer_decisiones(args.live)
    rep = extraer_decisiones(args.replay)
    res = comparar(liv, rep)
    v = veredicto_b5(res)

    print(f"Live:   {res['total_live']} decisiones")
    print(f"Replay: {res['total_replay']} decisiones evaluadas")
    print(f"Con par: {res['con_par']} | sin par: {res['sin_par']}")
    print(f"Cobertura:     {fmt(res['cobertura_pct'])}")
    print(f"Concordancia:  {fmt(res['concordancia_pct'])}")
    print(f"Divergencias de accion: {res['divergencias_accion']}")
    print()
    for k in ("i", "ii", "iii"):
        print(f"  {v[k]['criterio']}: {fmt(v[k]['valor'])} -> {v[k]['estado']}")

    if args.out_md:
        md = informe_md(os.path.basename(os.path.dirname(args.live)), res, v,
                        etiqueta="RETROSPECTIVO Y NO INDEPENDIENTE")
        with open(args.out_md, "w", encoding="utf-8", newline="\n") as f:
            f.write(md)
        print(f"\nInforme: {args.out_md}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
