#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: DataIntegrityValidator
------------------------------------------------------------
Valida la integridad estricta de datasets de mercado (ticks y velas):
  - Detección de timestamps duplicados o fuera de orden.
  - Precios inválidos, NaNs, infinitos o <= 0.
  - Consistencia OHLC en velas (high >= max(open, close), low <= min(open, close)).
  - Detección de gaps anómalos o intervalos temporales imposibles.
  - Ticks idénticos duplicados.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional
import math
import numpy as np


@dataclass
class DataIntegrityReport:
    is_valid: bool
    total_records: int
    clean_records: int
    corrupt_records: int
    error_count: int
    warning_count: int
    issues: List[str] = field(default_factory=list)
    stats: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_valid": self.is_valid,
            "total_records": self.total_records,
            "clean_records": self.clean_records,
            "corrupt_records": self.corrupt_records,
            "error_count": self.error_count,
            "warning_count": self.warning_count,
            "issues": self.issues,
            "stats": self.stats
        }


class DataIntegrityValidator:
    """
    Validador de integridad física y matemática de datos crudos.
    """

    def __init__(self, max_gap_seconds: float = 300.0):
        self.max_gap_seconds = max_gap_seconds

    def validate_ticks(self, timestamps: List[float], prices: List[float]) -> DataIntegrityReport:
        """
        Valida una serie de micro-ticks (timestamp_ms, precio).
        """
        issues = []
        n = len(timestamps)
        if n == 0 or len(prices) != n:
            return DataIntegrityReport(
                is_valid=False,
                total_records=n,
                clean_records=0,
                corrupt_records=n,
                error_count=1,
                warning_count=0,
                issues=["Dataset de ticks vacío o longitud discordante entre timestamp y precio"]
            )

        corrupt_indices = set()
        duplicate_ts_count = 0
        out_of_order_count = 0
        nan_inf_count = 0
        non_positive_price_count = 0
        gaps_count = 0

        seen_ts = set()
        prev_ts = None

        for i in range(n):
            ts = timestamps[i]
            p = prices[i]

            # 1. NaN / Inf en timestamp
            if math.isnan(ts) or math.isinf(ts):
                nan_inf_count += 1
                corrupt_indices.add(i)
                continue

            # 2. Timestamps duplicados y orden cronológico
            if ts in seen_ts:
                duplicate_ts_count += 1
            seen_ts.add(ts)

            if prev_ts is not None:
                if ts < prev_ts:
                    out_of_order_count += 1
                    corrupt_indices.add(i)
                elif (ts - prev_ts) > (self.max_gap_seconds * 1000.0):
                    gaps_count += 1

            prev_ts = ts

            # 3. NaN / Inf / Precios no positivos
            if math.isnan(p) or math.isinf(p):
                nan_inf_count += 1
                corrupt_indices.add(i)
                continue

            if p <= 0.0:
                non_positive_price_count += 1
                corrupt_indices.add(i)

        if nan_inf_count > 0:
            issues.append(f"Se encontraron {nan_inf_count} valores NaN o Infinito en la serie.")
        if non_positive_price_count > 0:
            issues.append(f"Se encontraron {non_positive_price_count} precios <= 0.0.")
        if duplicate_ts_count > 0:
            issues.append(f"Se encontraron {duplicate_ts_count} timestamps duplicados.")
        if out_of_order_count > 0:
            issues.append(f"Se encontraron {out_of_order_count} ticks fuera de orden cronológico.")
        if gaps_count > 0:
            issues.append(f"Se detectaron {gaps_count} brechas temporales (gaps > {self.max_gap_seconds}s).")

        error_count = nan_inf_count + non_positive_price_count + out_of_order_count
        warning_count = duplicate_ts_count + gaps_count
        is_valid = error_count == 0

        clean_count = n - len(corrupt_indices)

        return DataIntegrityReport(
            is_valid=is_valid,
            total_records=n,
            clean_records=clean_count,
            corrupt_records=len(corrupt_indices),
            error_count=error_count,
            warning_count=warning_count,
            issues=issues,
            stats={
                "min_price": float(np.min(prices)) if n > 0 else 0.0,
                "max_price": float(np.max(prices)) if n > 0 else 0.0,
                "duration_ms": (timestamps[-1] - timestamps[0]) if n > 1 else 0.0
            }
        )

    def validate_candles(self, candles: List[Dict[str, float]]) -> DataIntegrityReport:
        """
        Valida consistencia de velas OHLC:
        open, high, low, close, open_time_ms.
        """
        issues = []
        n = len(candles)
        if n == 0:
            return DataIntegrityReport(is_valid=False, total_records=0, clean_records=0, corrupt_records=0, error_count=1, warning_count=0, issues=["Dataset de velas vacío"])

        ohlc_inconsistent_count = 0
        corrupt_indices = set()
        prev_time = None
        out_of_order = 0

        for i, c in enumerate(candles):
            o = c.get("open", 0.0)
            h = c.get("high", 0.0)
            l = c.get("low", 0.0)
            cl = c.get("close", 0.0)
            t = c.get("open_time_ms", 0.0)

            # Inconsistencias OHLC
            if h < max(o, cl) or l > min(o, cl) or h < l or o <= 0.0 or cl <= 0.0:
                ohlc_inconsistent_count += 1
                corrupt_indices.add(i)

            if prev_time is not None and t <= prev_time:
                out_of_order += 1
                corrupt_indices.add(i)

            prev_time = t

        if ohlc_inconsistent_count > 0:
            issues.append(f"Se encontraron {ohlc_inconsistent_count} velas con relación OHLC matemáticamente inconsistente.")
        if out_of_order > 0:
            issues.append(f"Se encontraron {out_of_order} velas fuera de secuencia temporal.")

        error_count = ohlc_inconsistent_count + out_of_order
        is_valid = error_count == 0

        return DataIntegrityReport(
            is_valid=is_valid,
            total_records=n,
            clean_records=n - len(corrupt_indices),
            corrupt_records=len(corrupt_indices),
            error_count=error_count,
            warning_count=0,
            issues=issues
        )

    def validate_and_clean_journal(self, csv_path: str):
        """
        Lee y audita un journal crudo de TradeDraw.
        Separa registros limpios de registros corruptos con motivo de exclusión.
        """
        import os
        import csv

        if not os.path.exists(csv_path):
            raise FileNotFoundError(f"Archivo no encontrado: {csv_path}")

        clean_records = []
        excluded_records = []

        with open(csv_path, "r", encoding="utf-8", errors="replace") as f:
            reader = csv.DictReader(f)
            for idx, row in enumerate(reader):
                try:
                    base_bal = float(row.get("base_balance", 0.0))
                    settled_bal = float(row.get("settled_balance", 0.0))
                    stake = float(row.get("stake", 0.0))
                    duration = int(row.get("duration_sec", 0))
                except (ValueError, TypeError) as e:
                    excluded_records.append({
                        "row_index": idx,
                        "record": row,
                        "reason": f"ERROR_PARSING_NUMERIC: {e}"
                    })
                    continue

                result = (row.get("result") or "").strip().upper()
                delta_balance = settled_bal - base_bal

                # 1. Glitch de UI / Accesibilidad: balance corrupto (1.00 COP)
                if base_bal < 100.0 or settled_bal < 100.0:
                    excluded_records.append({
                        "row_index": idx,
                        "record": row,
                        "reason": f"CORRUPT_BALANCE_UI_GLITCH: base={base_bal}, settled={settled_bal}"
                    })
                    continue

                # 2. Inconsistencia: LOSS declarado con Diff == 0.0 (orden sin ejecutar o void)
                if result == "LOSS" and abs(delta_balance) < 2.0:
                    excluded_records.append({
                        "row_index": idx,
                        "record": row,
                        "reason": f"DECLARED_LOSS_WITHOUT_BALANCE_CHANGE: delta={delta_balance}"
                    })
                    continue

                # Registro normalizado
                norm_row = dict(row)
                norm_row["Status"] = result
                norm_row["Action"] = (row.get("action") or "").strip().upper()
                norm_row["BaseBalance"] = base_bal
                norm_row["SettledBalance"] = settled_bal
                norm_row["DeltaBalance"] = delta_balance
                norm_row["DurationSec"] = duration
                norm_row["TradeNum"] = idx + 1
                clean_records.append(norm_row)

        return clean_records, excluded_records
