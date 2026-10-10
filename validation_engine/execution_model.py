#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: ExecutionModel
-----------------------------------------------------
Modela las condiciones realistas de ejecución en broker de opciones binarias:
  signal_time -> execution_delay -> entry_price -> expiry_time -> settlement_price -> result

Soporta:
  - Latencia configurable (red + interfaz de accesibilidad en Android).
  - Slippage de micro-tick entre la señal y el llenado de la orden.
  - Modo de expiración fija al minuto (:00) vs duración exacta (60s).
  - Reglas de empate (TIE / VOID con retorno de stake vs pérdida).
  - Tasa de rechazo / requote del broker (fill_rate).
"""

from dataclasses import dataclass
from typing import List, Tuple, Optional
import math
import numpy as np


@dataclass
class SimulatedTradeResult:
    signal_time_ms: float
    execution_time_ms: float
    expiry_time_ms: float
    signal_price: float
    entry_price: float
    settlement_price: float
    action: str  # "CALL" o "PUT"
    result: str  # "WIN", "LOSS", "TIE", "REJECTED"
    return_unit: float
    slippage: float
    latency_ms: float
    is_executed: bool


class ExecutionModel:
    """
    Simulador realista de ejecución de órdenes en brokers OTC/Binary.
    """

    def __init__(
        self,
        latency_ms: float = 400.0,
        slippage_std: float = 0.00005,
        payout_ratio: float = 0.83,
        tie_rule: str = "VOID",  # "VOID" (0.0) o "LOSS" (-1.0)
        expiry_mode: str = "FIXED_MINUTE",  # "FIXED_MINUTE" o "DURATION_60S"
        fill_rate: float = 1.0,
        seed: Optional[int] = 42
    ):
        self.latency_ms = latency_ms
        self.slippage_std = slippage_std
        self.payout_ratio = payout_ratio
        self.tie_rule = tie_rule
        self.expiry_mode = expiry_mode
        self.fill_rate = fill_rate
        self.rng = np.random.RandomState(seed)

    def simulate_order(
        self,
        signal_time_ms: float,
        signal_price: float,
        action: str,
        ticks_timestamps_ms: np.ndarray,
        ticks_prices: np.ndarray
    ) -> SimulatedTradeResult:
        # 1. Simulación de tasa de llenado / rechazo de orden
        if self.rng.rand() > self.fill_rate:
            return SimulatedTradeResult(
                signal_time_ms=signal_time_ms,
                execution_time_ms=signal_time_ms + self.latency_ms,
                expiry_time_ms=0.0,
                signal_price=signal_price,
                entry_price=0.0,
                settlement_price=0.0,
                action=action,
                result="REJECTED",
                return_unit=0.0,
                slippage=0.0,
                latency_ms=self.latency_ms,
                is_executed=False
            )

        # 2. Latencia de ejecución
        jitter = self.rng.normal(0, self.latency_ms * 0.15)
        effective_latency = max(50.0, self.latency_ms + jitter)
        exec_time_ms = signal_time_ms + effective_latency

        # Encontrar tick más cercano en exec_time_ms
        idx_exec = np.searchsorted(ticks_timestamps_ms, exec_time_ms)
        if idx_exec >= len(ticks_prices):
            idx_exec = len(ticks_prices) - 1

        fill_base_price = float(ticks_prices[idx_exec])
        slippage = float(self.rng.normal(0, self.slippage_std * fill_base_price))
        entry_price = fill_base_price + slippage

        # 3. Determinación del tiempo de expiración
        if self.expiry_mode == "FIXED_MINUTE":
            # Expiración al final del minuto actual o siguiente si entró después de :58
            cycle_sec = (exec_time_ms / 1000.0) % 60.0
            if cycle_sec <= 58.0:
                expiry_time_ms = math.ceil(exec_time_ms / 60000.0) * 60000.0
            else:
                expiry_time_ms = (math.floor(exec_time_ms / 60000.0) + 2) * 60000.0
        else:
            # Duración fija exacta de 60 segundos
            expiry_time_ms = exec_time_ms + 60000.0

        # Encontrar tick en expiry_time_ms
        idx_exp = np.searchsorted(ticks_timestamps_ms, expiry_time_ms)
        if idx_exp >= len(ticks_prices):
            idx_exp = len(ticks_prices) - 1

        settlement_price = float(ticks_prices[idx_exp])

        # 4. Resolución del resultado
        delta = settlement_price - entry_price
        is_tie = abs(delta) < 1e-8

        if is_tie:
            res = "TIE"
            ret = 0.0 if self.tie_rule == "VOID" else -1.0
        elif action.upper() == "CALL" or action.upper() == "BUY":
            win = delta > 0.0
            res = "WIN" if win else "LOSS"
            ret = self.payout_ratio if win else -1.0
        else:  # PUT o SELL
            win = delta < 0.0
            res = "WIN" if win else "LOSS"
            ret = self.payout_ratio if win else -1.0

        return SimulatedTradeResult(
            signal_time_ms=signal_time_ms,
            execution_time_ms=exec_time_ms,
            expiry_time_ms=expiry_time_ms,
            signal_price=signal_price,
            entry_price=entry_price,
            settlement_price=settlement_price,
            action=action,
            result=res,
            return_unit=ret,
            slippage=slippage,
            latency_ms=effective_latency,
            is_executed=True
        )
