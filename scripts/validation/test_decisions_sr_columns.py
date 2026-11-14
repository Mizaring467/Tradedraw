#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R24/B2 — TESTS DE NO-REGRESION DEL ESQUEMA DE `decisions.csv`.

QUE FIJA. `decisions.csv` gana dos columnas S/R AL FINAL
(`distanceToSupportRatio`, `distanceToResistanceRatio`). Lo que este test protege es la regla
solo-append: las 14 columnas historicas no se mueven, no se renombran y conservan su contenido
campo a campo. Un lector viejo (`csv.DictReader` por nombre) debe seguir leyendo un CSV nuevo sin
cambios, y un lector nuevo debe leer un CSV viejo sin excepcion.

POR QUE AQUI Y NO EN EL MONITOR. Son funciones puras de formato; se prueban mejor aisladas, sin
dispositivo, sin red y sin Gradle:

    python scripts/validation/test_decisions_sr_columns.py

Ejecutable tambien con pytest.
"""

import csv
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from live_monitor_r23_autonomous import ratio_celda  # noqa: E402

# Cabecera REAL de una ventana previa (window_17, build 8cdb61f). No es una suposicion: es el
# contrato que hay que preservar.
CABECERA_PREVIA = [
    "decision_id", "trade_id", "timestamp", "asset", "price_at_signal",
    "candle_second", "market_regime", "trend", "action", "score",
    "action_plan", "kind", "detail", "veto_reason",
]

CABECERA_NUEVA = CABECERA_PREVIA + [
    "distanceToSupportRatio", "distanceToResistanceRatio",
]


def _csv(header, filas):
    buf = io.StringIO()
    w = csv.DictWriter(buf, fieldnames=header)
    w.writeheader()
    for f in filas:
        w.writerow(f)
    buf.seek(0)
    return buf


def _fila_base(**extra):
    fila = {
        "decision_id": "abc", "trade_id": "", "timestamp": "1792402505170",
        "asset": "Z-CRY/IDX", "price_at_signal": "641.8673", "candle_second": "5",
        "market_regime": "TRENDING", "trend": "DOWNTREND", "action": "BUY",
        "score": "75.0", "action_plan": "VETO BUY", "kind": "VETO",
        "detail": "ADAPTIVE_BLOCK [sim:99%]", "veto_reason": "OTRO",
    }
    fila.update(extra)
    return fila


# ---------------------------------------------------------------------------
# Solo-append: las 14 columnas historicas NO cambian
# ---------------------------------------------------------------------------


def test_las_14_columnas_previas_coinciden_campo_a_campo():
    """Parsear un CSV previo y uno nuevo: las 14 primeras columnas deben ser identicas."""
    previo = list(csv.DictReader(_csv(CABECERA_PREVIA, [_fila_base()])))
    nuevo = list(csv.DictReader(_csv(CABECERA_NUEVA, [_fila_base(
        distanceToSupportRatio="0.0500", distanceToResistanceRatio="0.2500",
    )])))

    assert len(previo) == len(nuevo) == 1
    for col in CABECERA_PREVIA:
        assert previo[0][col] == nuevo[0][col], f"columna alterada: {col}"


def test_la_cabecera_nueva_es_la_previa_mas_dos_al_final():
    """No se reordena ni se renombra: prefijo exacto + dos columnas al final."""
    lector = csv.reader(_csv(CABECERA_NUEVA, [_fila_base()]))
    cabecera = next(lector)
    assert cabecera[:14] == CABECERA_PREVIA
    assert cabecera[14:] == ["distanceToSupportRatio", "distanceToResistanceRatio"]


def test_lector_viejo_ignora_las_columnas_nuevas():
    """Un consumidor que solo pide las 14 historicas sigue obteniendo lo mismo."""
    filas = list(csv.DictReader(_csv(CABECERA_NUEVA, [_fila_base(
        distanceToSupportRatio="0.1000", distanceToResistanceRatio="0.2000",
    )])))
    solo_viejas = {k: filas[0][k] for k in CABECERA_PREVIA}
    assert solo_viejas == _fila_base()


def test_lector_nuevo_sobre_csv_viejo_no_lanza():
    """Un CSV previo no tiene las claves nuevas: se obtiene None, no excepcion."""
    filas = list(csv.DictReader(_csv(CABECERA_PREVIA, [_fila_base()])))
    assert filas[0].get("distanceToSupportRatio") is None
    assert filas[0].get("distanceToResistanceRatio") is None


# ---------------------------------------------------------------------------
# Celdas: "no medido" NUNCA se escribe como 0.0
# ---------------------------------------------------------------------------


def test_centinela_menos_uno_es_celda_vacia():
    assert ratio_celda(-1) == ""
    assert ratio_celda(-1.0) == ""
    assert ratio_celda("-1") == ""


def test_ausente_o_no_numerico_es_celda_vacia():
    assert ratio_celda(None) == ""
    assert ratio_celda("") == ""
    assert ratio_celda("n/a") == ""


def test_cero_real_se_conserva_como_medicion():
    """0.0 es un nivel pegado: es un dato, no una ausencia."""
    assert ratio_celda(0.0) == "0.0000"
    assert ratio_celda("0") == "0.0000"


def test_valor_normal_se_formatea_con_4_decimales():
    assert ratio_celda(0.05) == "0.0500"
    assert ratio_celda(1) == "1.0000"


def test_nan_e_infinito_son_celda_vacia():
    """`float('nan') < 0.0` es False: sin guard de finitud se escribia 'nan' en el CSV."""
    for valor in (float("nan"), float("inf"), float("-inf"), "nan", "inf", "-inf", "NaN"):
        assert ratio_celda(valor) == "", f"{valor!r} deberia ser celda vacia"


# ---------------------------------------------------------------------------
# El writer LEGACY (`live_monitor_r23.py`) declara el mismo contrato
# ---------------------------------------------------------------------------


def test_writer_legacy_declara_las_16_columnas():
    """Si el legacy se queda en 14, sus filas de 16 claves rompen `DictWriter`."""
    from live_monitor_r23 import DECISION_FIELDNAMES

    assert DECISION_FIELDNAMES == CABECERA_NUEVA


def test_el_writer_legacy_acepta_una_fila_completa():
    """`DictWriter` con el header legacy debe escribir una fila de 16 claves sin excepcion."""
    from live_monitor_r23 import DECISION_FIELDNAMES

    fila = _fila_base(distanceToSupportRatio="", distanceToResistanceRatio="0.5000")
    salida = list(csv.DictReader(_csv(DECISION_FIELDNAMES, [fila])))
    assert salida[0]["distanceToResistanceRatio"] == "0.5000"
    assert salida[0]["distanceToSupportRatio"] == ""


if __name__ == "__main__":
    fallos = 0
    for nombre, fn in sorted(globals().items()):
        if nombre.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"PASS {nombre}")
            except AssertionError as e:
                fallos += 1
                print(f"FAIL {nombre}: {e}")
    sys.exit(1 if fallos else 0)
