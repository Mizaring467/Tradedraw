#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R23K C1 — TESTS DE NO-REGRESIÓN DE LA TELEMETRÍA DE ACCIÓN EN FILAS VETO.

QUE FIJA. `decisions.csv` registraba las filas `VETO` con `action = NONE`, lo que hacía imposible
cualquier análisis de bloqueos por dirección: no se podía saber si un veto había actuado sobre una
compra o sobre una venta. El campo se rellena con `extract_action`, que saca la dirección del texto
del evento con fallback a `engine.lastSignalAction`.

POR QUE UN TEST APARTE Y NO DENTRO DEL MONITOR. El monitor ya tiene su suite `--test` (cierre
seguro). Estas funciones son de extracción de texto, se prueban mejor aisladas y con casos raros que
no aparecen en una captura normal. Se ejecutan con pytest o directamente:

    python scripts/validation/test_action_telemetry.py

Los tests corren sin dispositivo, sin red y sin Gradle.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from live_monitor_r23_autonomous import extract_action  # noqa: E402


# ---------------------------------------------------------------------------
# Comportamiento que NO puede cambiar (no-regresión)
# ---------------------------------------------------------------------------


def test_accion_explicita_en_el_texto():
    """Los detalles que nombran la dirección deben seguir resolviéndola."""
    assert extract_action("VETO_BUY_EXHAUSTION_CLIMAX UPTREND con callPower >= 70%") == "BUY"
    assert extract_action("CONFIRMADA SELL saldo movido") == "SELL"
    assert extract_action("CALL óptimo en soporte") == "BUY"
    assert extract_action("PUT en resistencia") == "SELL"


def test_veto_de_compra_por_agotamiento():
    """Caso central de R23H: el token lleva BUY y debe resolverse como BUY."""
    assert extract_action("VETO_BUY_EXHAUSTION_CLIMAX UPTREND con callPower >= 70% (callPower=86)") == "BUY"


def test_fallback_al_motor_cuando_el_texto_no_trae_direccion():
    """ADAPTIVE_BLOCK y compañía no nombran la dirección: se usa la del motor."""
    assert extract_action("ADAPTIVE_BLOCK [sim:99%] Anti-patrón perdedor", "SELL") == "SELL"
    assert extract_action("ADAPTIVE_BLOCK [sim:99%] Anti-patrón perdedor", "buy") == "BUY"
    assert extract_action("RISK_LIMIT Operación abierta en curso (3s)", "BUY") == "BUY"


def test_sin_texto_ni_motor_devuelve_none():
    """Sin información no se inventa una dirección: NONE es el valor honesto."""
    assert extract_action("") == "NONE"
    assert extract_action("RISK_LIMIT Operación abierta en curso") == "NONE"
    assert extract_action("", None) == "NONE"


def test_motor_con_valor_invalido_cae_a_none():
    """Un `lastSignalAction` corrupto no debe propagarse al CSV."""
    assert extract_action("RISK_LIMIT", "UNKNOWN") == "NONE"
    assert extract_action("RISK_LIMIT", "") == "NONE"
    assert extract_action("RISK_LIMIT", None) == "NONE"


def test_siempre_devuelve_un_valor_del_dominio():
    """El campo alimenta un CSV consumido por analítica: dominio cerrado."""
    entradas = [
        "", "BUY", "SELL", "CALL", "PUT", "buy", "sell",
        "ADAPTIVE_BLOCK", "VETO V2 rango insuficiente", "DESAJUSTE pantalla=X feed=Y",
        "FEED_RANCIO", "SEGURIDAD NO DEMO", "texto sin sentido", "12345",
    ]
    for e in entradas:
        for motor in (None, "BUY", "SELL", "BASURA"):
            r = extract_action(e, motor)
            assert r in ("BUY", "SELL", "NONE"), f"dominio roto con ({e!r}, {motor!r}) -> {r!r}"


# ---------------------------------------------------------------------------
# Casos límite que documentan una limitación conocida
# ---------------------------------------------------------------------------


def test_texto_ambiguo_con_ambas_palabras_resuelve_a_buy():
    """
    LIMITACIÓN CONOCIDA Y DOCUMENTADA, no un contrato deseable.

    `extract_action` evalúa "BUY" antes que "SELL", así que un detalle que contenga ambas palabras
    resuelve a BUY. Hasta la fecha ningún detalle real del motor mezcla las dos direcciones, pero el
    comportamiento queda fijado aquí para que un cambio futuro no pase inadvertido.

    Si algún día un detalle real mezcla dirección y contra-dirección (p.ej. "SELL vetado, antes BUY"),
    este test falla y obliga a decidir explícitamente en vez de heredar el orden accidental del `if`.
    """
    assert extract_action("SELL bloqueado porque BUY estaba activo") == "BUY"


def test_minusculas_y_espacios_no_alteran_el_resultado():
    """El motor emite en mayúsculas, pero el CSV no debe depender de ello."""
    assert extract_action("  buy  ") == "BUY"
    assert extract_action("SeLl confirmada") == "SELL"


# ---------------------------------------------------------------------------
# Runner mínimo sin pytest
# ---------------------------------------------------------------------------


def _ejecutar() -> int:
    pruebas = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    fallos = []
    print("=" * 70)
    print("R23K C1 — TESTS DE TELEMETRÍA DE ACCIÓN (no-regresión)")
    print("=" * 70)
    for fn in pruebas:
        try:
            fn()
            print(f"  [OK]    {fn.__name__}")
        except AssertionError as e:
            fallos.append((fn.__name__, str(e)))
            print(f"  [FALLA] {fn.__name__}: {e}")
        except Exception as e:
            fallos.append((fn.__name__, f"{type(e).__name__}: {e}"))
            print(f"  [ERROR] {fn.__name__}: {type(e).__name__}: {e}")
    print("-" * 70)
    if fallos:
        print(f"RESULTADO: FALLA ({len(fallos)}/{len(pruebas)})")
        return 1
    print(f"RESULTADO: PASS ({len(pruebas)}/{len(pruebas)})")
    return 0


if __name__ == "__main__":
    sys.exit(_ejecutar())
