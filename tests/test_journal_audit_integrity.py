#!/usr/bin/env python3
"""
TradeDraw - Tests de Integridad y Saneamiento de Journals
--------------------------------------------------------
Verifica la detección y aislamiento de anomalías en los CSVs reales:
  1. Filas con saldos absurdos (base_balance == 1.00 o settled_balance == 1.00).
  2. Filas declaradas LOSS sin cambio de saldo real (Diff == 0.0).
  3. Separación estricta: RAW DATA -> VALIDATION -> CLEAN DATASET + EXCLUDED RECORDS con motivo.
"""

import os
import csv
import unittest


def sanitize_journal(csv_path):
    """
    Lee un journal crudo y separa registros válidos de registros excluidos con motivo explícito.
    """
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Archivo no encontrado: {csv_path}")

    clean_records = []
    excluded_records = []

    with open(csv_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader):
            # Parsear valores numéricos
            try:
                base_bal = float(row.get("base_balance", 0.0))
                settled_bal = float(row.get("settled_balance", 0.0))
                stake = float(row.get("stake", 0.0))
                duration = int(row.get("duration_sec", 0))
            except (ValueError, TypeError) as e:
                excluded_records.append({
                    "row_index": idx,
                    "row": row,
                    "reason": f"ERROR_PARSING_NUMERIC: {e}"
                })
                continue

            result = (row.get("result") or "").strip().upper()
            delta_balance = settled_bal - base_bal

            # 1. Glitch de accesibilidad: balance == 1.00 o < 1000 COP en cuenta real/demo
            if base_bal < 100.0 or settled_bal < 100.0:
                excluded_records.append({
                    "row_index": idx,
                    "row": row,
                    "reason": f"CORRUPT_BALANCE_UI_GLITCH: base={base_bal}, settled={settled_bal}"
                })
                continue

            # 2. Inconsistencia: LOSS declarado pero delta == 0.0 (orden sin ejecutar o void)
            if result == "LOSS" and abs(delta_balance) < 2.0:
                excluded_records.append({
                    "row_index": idx,
                    "row": row,
                    "reason": f"DECLARED_LOSS_WITHOUT_BALANCE_CHANGE: delta={delta_balance}"
                })
                continue

            # Registro válido
            clean_records.append({
                "row_index": idx,
                "timestamp": row.get("timestamp"),
                "iso_date": row.get("iso_date"),
                "action": row.get("action"),
                "strategy": row.get("strategy"),
                "submode": row.get("submode"),
                "stake": stake,
                "base_balance": base_bal,
                "settled_balance": settled_bal,
                "result": result,
                "effective_result": "WIN" if delta_balance > 2.0 else ("LOSS" if delta_balance < -2.0 else "TIE"),
                "duration_sec": duration,
                "delta_balance": delta_balance,
                "reason": row.get("reason", "")
            })

    return clean_records, excluded_records


class TestJournalAuditIntegrity(unittest.TestCase):

    def setUp(self):
        self.device_all_path = "loops/journal/trade_journal_device_all.csv"

    def test_detection_of_corrupt_records(self):
        """
        Demuestra la detección de los 5 registros corruptos con balance 1.00 y los 2 sin movimiento.
        """
        clean, excluded = sanitize_journal(self.device_all_path)

        corrupt_balance_count = sum(1 for e in excluded if "CORRUPT_BALANCE_UI_GLITCH" in e["reason"])
        zero_diff_loss_count = sum(1 for e in excluded if "DECLARED_LOSS_WITHOUT_BALANCE_CHANGE" in e["reason"])

        print(f"\n[EVIDENCIA JOURNAL] Total leídos: {len(clean) + len(excluded)}")
        print(f"                   Registros limpios: {len(clean)}")
        print(f"                   Registros excluidos: {len(excluded)}")
        print(f"                     - Saldo corrupto 1.00 COP: {corrupt_balance_count}")
        print(f"                     - Pérdida sin cambio de saldo: {zero_diff_loss_count}")

        self.assertEqual(corrupt_balance_count, 5, "Deben detectarse exactamente 5 registros corruptos de balance")
        self.assertEqual(zero_diff_loss_count, 2, "Deben detectarse exactamente 2 registros de LOSS con delta=0")
        self.assertEqual(len(clean), 205, "El dataset limpio debe contener 205 registros de los 212 originales")

    def test_every_excluded_record_preserves_explicit_reason(self):
        """
        Verifica que ningún registro sea descartado silenciosamente y conserve su razón.
        """
        clean, excluded = sanitize_journal(self.device_all_path)
        for e in excluded:
            self.assertIn("reason", e)
            self.assertGreater(len(e["reason"]), 5)
            self.assertIn("row_index", e)
            self.assertIn("row", e)


if __name__ == "__main__":
    unittest.main()
