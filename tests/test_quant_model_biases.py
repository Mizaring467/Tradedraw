#!/usr/bin/env python3
"""
TradeDraw - Tests de Reproducción de Sesgos Cuantitativos y Metodológicos
-----------------------------------------------------------------------
Verifica y demuestra formalmente los sesgos documentados en AUDIT_REPORT.md:
  1. Sesgo Circular en FastCryptoIdxSimulator (drift determinista hacia anchor).
  2. Colapso de tamaño de muestra en Walk-Forward OOS (N < 30 en cada fold).
  3. Fuga de oráculo en S/R sintéticos vs cálculo causal de fractales.
"""

import os
import json
import math
import unittest
import numpy as np


class TestQuantModelBiases(unittest.TestCase):

    def test_circular_mean_reversion_generator_bias(self):
        """
        Demuestra matemáticamente que FastCryptoIdxSimulator tiene sesgo circular:
        En régimen 'range', la deriva drift = -0.018 * (prev_p - anchor) * (dt / 60.0)
        obliga al precio a retornar al anchor cuando se aproxima a la resistencia.
        """
        base_price = 640.0
        anchor = 640.0
        half_channel = 3.0
        resistance = anchor + half_channel
        dt = 60.0 / 70.0

        # Si el precio está cerca de la resistencia (sobrecompra):
        prev_p = resistance  # 643.0
        drift = -0.018 * (prev_p - anchor) * (dt / 60.0)

        # La deriva esperada es estrictamente negativa
        self.assertLess(drift, 0.0, "La deriva en resistencia DEBE ser negativa por construcción del generador")
        
        # Simulamos 10,000 pasos de micro-tick desde la resistencia bajo la fórmula del generador
        sigma_tick = 0.55 * math.sqrt(dt / (365.25 * 24 * 3600))
        np.random.seed(42)
        steps = 10000
        dw = np.random.normal(0, sigma_tick, steps)
        
        # En cada paso, calculamos el retorno con y sin la deriva forzada
        p_with_drift = prev_p * (1.0 + drift + dw)
        
        # La proporción de veces que el precio cae (fading win rate artificial)
        wins_reversion = np.mean(p_with_drift < prev_p)
        print(f"\n[EVIDENCIA 1] Proporción de ticks bajistas forzados en resistencia: {wins_reversion*100:.2f}%")
        self.assertGreater(wins_reversion, 0.50, "El generador favorece artificialmente la caída del precio en resistencia")

    def test_walk_forward_insufficient_sample_size(self):
        """
        Demuestra que el Walk-Forward de quant_crypto_idx_model.py colapsa por tamaño de muestra:
        Todos los folds OOS tienen N < 30 (de hecho, N <= 9).
        """
        summary_path = "loops/journal/quant_research_summary.json"
        self.assertTrue(os.path.exists(summary_path), f"Debe existir el resumen: {summary_path}")

        with open(summary_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        folds = data["walk_forward_oos"]["folds"]
        self.assertEqual(len(folds), 5, "Deben existir 5 folds")

        min_required_n = 30
        insufficient_folds = 0
        total_oos_trades = 0

        for f in folds:
            n = f["n_trades"]
            total_oos_trades += n
            if n < min_required_n:
                insufficient_folds += 1

        print(f"\n[EVIDENCIA 2] Folds OOS con N < 30: {insufficient_folds}/5. Total trades OOS: {total_oos_trades}")
        self.assertEqual(insufficient_folds, 5, "Los 5 folds OOS poseen muestras estadísticamente insuficientes (N < 30)")
        self.assertLessEqual(total_oos_trades, 30, "El conjunto OOS total sumado es menor o igual a 30 trades")

    def test_unweighted_average_winrate_distortion(self):
        """
        Demuestra la distorsión matemática de promediar porcentajes OOS no ponderados.
        """
        summary_path = "loops/journal/quant_research_summary.json"
        with open(summary_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        folds = data["walk_forward_oos"]["folds"]
        reported_avg_wr = data["walk_forward_oos"]["avg_oos_reversion_winrate"]

        total_trades = sum(f["n_trades"] for f in folds)
        total_wins = sum(round(f["n_trades"] * f["reversion_winrate"]) for f in folds)
        true_weighted_wr = total_wins / total_trades if total_trades > 0 else 0.0

        print(f"\n[EVIDENCIA 3] Win Rate OOS reportado (promedio simple): {reported_avg_wr*100:.2f}%")
        print(f"               Win Rate OOS real (ponderado por trades): {true_weighted_wr*100:.2f}%")
        self.assertNotAlmostEqual(reported_avg_wr, true_weighted_wr, places=2,
                                 msg="El promedio no ponderado distorsiona la métrica real")


if __name__ == "__main__":
    unittest.main()
