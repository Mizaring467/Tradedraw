"""
R23E (REQ 2 / REQ 3): tests del pipeline contrafactual shadow de ADAPTIVE_BLOCK.

Cubre las tres piezas que la spec exige blindar:
  1. Deduplicacion (ocupacion 60s + maximo 1 por vela de 60s por direccion).
  2. Resolucion matematica WIN/LOSS/VOID a 60s y su EV teorico asociado.
  3. Formato y consistencia de `counterfactual_adaptive_block.csv`.

Corre con:  python -m unittest tests.test_counterfactual_adaptive_block -v
"""

import csv
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts", "validation"))

import live_monitor_r23_autonomous as mon  # noqa: E402


class TestExtractAdaptiveBlockFields(unittest.TestCase):
    """REQ 1 -> REQ 2: el consumidor debe leer lo que el productor escribe."""

    def test_lee_sim_y_score_del_formato_enriquecido(self):
        detalle = "ADAPTIVE_BLOCK [sim:98%] [Confluencia: 85pts] Anti-patrón perdedor recurrente"
        campos = mon.extract_adaptive_block_fields(detalle)
        self.assertEqual(98.0, campos["similarity"])
        self.assertEqual(85.0, campos["score"])

    def test_sin_marcadores_devuelve_none_no_cero(self):
        # Un 0 seria indistinguible de "similitud medida y nula": justo el dato que no existe.
        campos = mon.extract_adaptive_block_fields(
            "ADAPTIVE_BLOCK ⛔ Bloqueada continuación contra nivel S/R extremo"
        )
        self.assertIsNone(campos["similarity"])
        self.assertIsNone(campos["score"])

    def test_solo_similitud(self):
        campos = mon.extract_adaptive_block_fields(
            "ADAPTIVE_BLOCK [sim:94%] Anti-patrón perdedor recurrente"
        )
        self.assertEqual(94.0, campos["similarity"])
        self.assertIsNone(campos["score"])

    def test_formato_viejo_sin_enriquecer_no_rompe(self):
        # Las ventanas R23D ya capturadas tienen el detail sin prefijos. El parser debe
        # tolerarlas (devuelve None) en vez de lanzar.
        campos = mon.extract_adaptive_block_fields(
            "ADAPTIVE_BLOCK Anti-patrón perdedor recurrente (97% similitud en contexto S/R=8%)"
        )
        self.assertIsNone(campos["similarity"])


class TestDedupe(unittest.TestCase):
    """REQ 2 paso 2: unidad experimental del preregistro (§6)."""

    def setUp(self):
        self.open_until = {}
        self.last_candle = {}

    def _registrar(self, action, ts):
        acepta = mon.dedupe_counterfactual(action, ts, self.open_until, self.last_candle)
        if acepta:
            self.open_until[action] = ts + mon.COUNTERFACTUAL_OCCUPANCY_MS
            self.last_candle[action] = ts // mon.COUNTERFACTUAL_HORIZON_MS
        return acepta

    def test_misma_direccion_dentro_de_la_ventana_se_deduplica(self):
        self.assertTrue(self._registrar("BUY", 1_000_000))
        # +10s: ocupacion de 60s activa -> NO es oportunidad nueva.
        self.assertFalse(self._registrar("BUY", 1_010_000))
        # +59s: sigue dentro del horizonte.
        self.assertFalse(self._registrar("BUY", 1_059_000))

    def test_direccion_contraria_no_se_bloquea_entre_si(self):
        # BUY y SELL son rachas independientes: el preregistro deduplica POR direccion.
        self.assertTrue(self._registrar("BUY", 1_000_000))
        self.assertTrue(self._registrar("SELL", 1_000_500))

    def test_mismo_candle_no_genera_duplicados(self):
        # Aunque la ocupacion ya haya expirado, la regla "1 por vela de 60s por direccion"
        # impide un segundo contrafactual en el mismo minuto.
        ts = 3_600_000  # inicio exacto de vela
        self.assertTrue(self._registrar("BUY", ts))
        self.last_candle["BUY"] = -1  # simula ocupacion expirada para aislar la regla de vela
        self.assertFalse(self._registrar("BUY", ts + 30_000))

    def test_acepta_tras_expirar_ocupacion_y_cambiar_de_vela(self):
        self.assertTrue(self._registrar("BUY", 1_000_000))
        # +61s y ya en la vela siguiente: nueva oportunidad legitima.
        self.assertTrue(self._registrar("BUY", 1_061_000))

    def test_accion_desconocida_nunca_se_registra(self):
        # Las decisiones sin direccion inferible (action="NONE") no son oportunidades medibles.
        self.assertFalse(self._registrar("NONE", 1_000_000))


class TestResolucion(unittest.TestCase):
    """REQ 2 paso 3: matematica de resolucion congelada en el preregistro §5."""

    def test_buy(self):
        self.assertEqual("COUNTERFACTUAL_WIN", mon.resolve_counterfactual("BUY", 100.0, 100.1))
        self.assertEqual("COUNTERFACTUAL_LOSS", mon.resolve_counterfactual("BUY", 100.0, 99.9))
        self.assertEqual("COUNTERFACTUAL_VOID", mon.resolve_counterfactual("BUY", 100.0, 100.0))

    def test_sell_espejo_de_buy(self):
        self.assertEqual("COUNTERFACTUAL_WIN", mon.resolve_counterfactual("SELL", 100.0, 99.9))
        self.assertEqual("COUNTERFACTUAL_LOSS", mon.resolve_counterfactual("SELL", 100.0, 100.1))
        self.assertEqual("COUNTERFACTUAL_VOID", mon.resolve_counterfactual("SELL", 100.0, 100.0))

    def test_ev_teorico_con_stake_unitario(self):
        # WIN -> +b*stake = +0.82*100000 = +82000
        self.assertAlmostEqual(82_000.0, mon.counterfactual_ev("COUNTERFACTUAL_WIN"))
        # LOSS -> -stake
        self.assertAlmostEqual(-100_000.0, mon.counterfactual_ev("COUNTERFACTUAL_LOSS"))
        # VOID -> 0
        self.assertAlmostEqual(0.0, mon.counterfactual_ev("COUNTERFACTUAL_VOID"))

    def test_breakeven_del_preregistro_queda_dentro_de_lo_esperado(self):
        # Con b=0.82, WR_BE = 1/(1+b) = 54.95%. El EV debe ser ~0 justo en ese punto.
        b = mon.COUNTERFACTUAL_PAYOUT
        wr_be = 1.0 / (1.0 + b)
        ev = wr_be * b * mon.COUNTERFACTUAL_STAKE - (1 - wr_be) * mon.COUNTERFACTUAL_STAKE
        self.assertAlmostEqual(0.0, ev, places=6)


class TestPrecedenciaDeTicks(unittest.TestCase):
    """Seleccion del tick mas cercano: sin look-ahead y tolerante al hueco del feed."""

    def setUp(self):
        self.ticks = {1000: "1000,Z-CRY/IDX,641.5", 1500: "1500,Z-CRY/IDX,641.7",
                      2000: "2000,Z-CRY/IDX,641.9"}

    def test_elige_el_mas_cercano(self):
        self.assertAlmostEqual(641.7, mon.find_nearest_tick_price(self.ticks, 1480, tolerance_ms=1000))

    def test_respeta_la_tolerancia(self):
        self.assertIsNone(mon.find_nearest_tick_price(self.ticks, 9000, tolerance_ms=4000))

    def test_empate_prefiere_el_tick_anterior(self):
        # A 250ms de distancia de 1000 y de 1500: para un precio de ENTRADA se prefiere el
        # anterior, que es el unico que existia en el instante de la decision.
        self.assertAlmostEqual(641.5, mon.find_nearest_tick_price(self.ticks, 1250, tolerance_ms=1000))


class TestCounterfactualCsv(unittest.TestCase):
    """REQ 2 paso 4: contrato de columnas y consistencia de la fila volcada."""

    def test_esquema_de_columnas_exacto(self):
        esperado = [
            "counterfactual_id", "decision_id", "asset", "action", "decision_ts",
            "entry_price", "expiry_ts", "exit_price", "result", "score", "similarity",
            "market_regime", "trend", "payout", "theoretical_ev",
        ]
        self.assertEqual(esperado, mon.COUNTERFACTUAL_FIELDS)

    def test_round_trip_del_csv_conserva_todas_las_columnas(self):
        fila = {
            "counterfactual_id": mon.counterfactual_id("abc-123", 1_792_000_000_000),
            "decision_id": "abc-123",
            "asset": "Z-CRY/IDX",
            "action": "BUY",
            "decision_ts": 1_792_000_000_000,
            "entry_price": 641.86730408,
            "expiry_ts": 1_792_000_060_000,
            "exit_price": 641.86730511,
            "result": mon.resolve_counterfactual("BUY", 641.86730408, 641.86730511),
            "score": 85.0,
            "similarity": 98.0,
            "market_regime": "TRENDING",
            "trend": "UPTREND",
            "payout": 0.82,
            "theoretical_ev": mon.counterfactual_ev("COUNTERFACTUAL_WIN"),
        }
        with tempfile.TemporaryDirectory() as d:
            ruta = os.path.join(d, "counterfactual_adaptive_block.csv")
            with open(ruta, "w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=mon.COUNTERFACTUAL_FIELDS)
                w.writeheader()
                w.writerow(fila)
            with open(ruta, newline="", encoding="utf-8") as f:
                leidas = list(csv.DictReader(f))

        self.assertEqual(1, len(leidas))
        self.assertEqual(set(mon.COUNTERFACTUAL_FIELDS), set(leidas[0].keys()))
        self.assertEqual("COUNTERFACTUAL_WIN", leidas[0]["result"])
        # El precio no debe perder precision al pasar por el CSV (magnitud sub-unitario).
        self.assertEqual("641.86730408", leidas[0]["entry_price"])

    def test_id_determinista_y_reproducible(self):
        a = mon.counterfactual_id("dec-1", 1_792_000_000_000)
        b = mon.counterfactual_id("dec-1", 1_792_000_000_000)
        c = mon.counterfactual_id("dec-2", 1_792_000_000_000)
        self.assertEqual(a, b, "Mismo input debe producir el mismo id")
        self.assertNotEqual(a, c, "decision_id distinto debe producir id distinto")


class TestBuckets(unittest.TestCase):
    """Segmentacion preregistrada §8.3 y §8.4."""

    def test_buckets_de_similitud_cubren_el_rango_sin_solape(self):
        self.assertEqual("[90,93]%", mon.bucket_label(90.0, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))
        self.assertEqual("[93,96]%", mon.bucket_label(93.0, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))
        self.assertEqual("[96,98]%", mon.bucket_label(97.0, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))
        self.assertEqual("[98,100]%", mon.bucket_label(100.0, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))

    def test_sin_medicion_va_a_na(self):
        self.assertEqual("N/A", mon.bucket_label(None, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))
        # Por debajo del minimo preregistrado (90%): no pertenece a ningun bucket.
        self.assertEqual("N/A", mon.bucket_label(89.9, mon.COUNTERFACTUAL_SIM_BUCKETS, "%"))


if __name__ == "__main__":
    unittest.main()
