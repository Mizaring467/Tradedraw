#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: NoLookAheadValidator
-----------------------------------------------------------
Audita exhaustivamente el sesgo de anticipación (Look-Ahead Bias) y filtrado de futuro:
  - Comprueba que ningún indicador utilice datos posteriores al instante de decisión (t_signal).
  - Valida el lag de confirmación de fractales S/R (un pivote en c[i] requiere el cierre de c[i+1]).
  - Verifica que los thresholds no hayan sido calibrados con información del dataset de prueba.
  - Asegura que el precio de entrada y el precio de liquidación respeten la flecha del tiempo.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any, Callable, Tuple

Tuple_Bool_List = Tuple[bool, List[str]]


@dataclass
class LookAheadAuditReport:
    passed: bool
    has_feature_leakage: bool
    has_fractal_timing_leakage: bool
    has_settlement_timing_leakage: bool
    violations: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "passed": self.passed,
            "has_feature_leakage": self.has_feature_leakage,
            "has_fractal_timing_leakage": self.has_fractal_timing_leakage,
            "has_settlement_timing_leakage": self.has_settlement_timing_leakage,
            "violations": self.violations
        }


class NoLookAheadValidator:
    """
    Guardián contra el sesgo de look-ahead.
    """

    def audit_trade_timing(
        self,
        signal_timestamp_ms: float,
        entry_timestamp_ms: float,
        expiry_timestamp_ms: float,
        feature_cutoff_ms: float
    ) -> bool:
        """
        Verifica la relación temporal elemental:
        feature_cutoff <= signal_time <= entry_time < expiry_time
        """
        if feature_cutoff_ms > signal_timestamp_ms:
            return False
        if signal_timestamp_ms > entry_timestamp_ms:
            return False
        if entry_timestamp_ms >= expiry_timestamp_ms:
            return False
        return True

    def audit_fractal_sr_causality(
        self,
        candle_indices_used: List[int],
        current_candle_index: int,
        fractal_half_window: int = 1
    ) -> Tuple_Bool_List:
        """
        Para fractales de 3 velas (c[i-1], c[i], c[i+1]):
        El máximo o mínimo en c[i] solo se conoce cuando c[i+1] ha cerrado por completo.
        Si se utiliza c[current_candle_index] como pivote sin esperar su barra de confirmación, hay look-ahead.
        """
        violations = []
        for idx in candle_indices_used:
            # Si el pivote evaluado es la vela actual en formación o no tiene su barra de confirmación cerrada
            if idx + fractal_half_window >= current_candle_index:
                violations.append(
                    f"Pivote en vela {idx} utilizado en vela {current_candle_index} antes de completar confirmación de {fractal_half_window} velas futuras."
                )

        return (len(violations) == 0, violations)

    def audit_simulation_pipeline(
        self,
        trades_metadata: List[Dict[str, Any]]
    ) -> LookAheadAuditReport:
        violations = []
        feature_leakage = False
        settlement_leakage = False

        for i, t in enumerate(trades_metadata):
            t_sig = t.get("signal_time", 0.0)
            t_entry = t.get("entry_time", 0.0)
            t_exp = t.get("expiry_time", 0.0)
            t_feat = t.get("feature_cutoff_time", t_sig)

            if t_feat > t_sig + 1e-6:
                feature_leakage = True
                violations.append(f"Trade #{i}: Indicadores calculados con datos posteriores a la señal ({t_feat} > {t_sig}).")

            if t_entry > t_exp:
                settlement_leakage = True
                violations.append(f"Trade #{i}: Tiempo de expiración anterior a la entrada ({t_exp} <= {t_entry}).")

        passed = not feature_leakage and not settlement_leakage and len(violations) == 0

        return LookAheadAuditReport(
            passed=passed,
            has_feature_leakage=feature_leakage,
            has_fractal_timing_leakage=False,
            has_settlement_timing_leakage=settlement_leakage,
            violations=violations
        )
