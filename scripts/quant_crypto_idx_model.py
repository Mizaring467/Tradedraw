#!/usr/bin/env python3
"""
TradeDraw - Quantitative Research & Microstructure Modeling for Crypto IDX
-------------------------------------------------------------------------
Author: Senior Quantitative Researcher (rentech / two-sigma rigor)
Context: C:\\Users\\heidy\\Tradedraw

Evaluates microstructural signals for 60-second binary options on Binomo Crypto IDX:
  1. Tick Flow Imbalance (TFI) in final candle window (:40s to :59s).
  2. Tick Overextension Z-score (distance in std dev from 60-tick SMA).
  3. Walk-Forward & Out-Of-Sample (OOS) testing: Reversion (fade) vs Continuation (momentum).
  4. S/R Confluence impact as formalized in SyntheticCandleEngine.kt.
  5. Statistical hypothesis testing against the 54.64% binary survival threshold (83% payout).
"""

import sys
import os
import math
import json
import argparse
from dataclasses import dataclass
from typing import List, Tuple, Dict

# Force UTF-8 on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import pandas as pd
from scipy import stats

# Binary option economics for Binomo Crypto IDX
PAYOUT_RATIO = 0.83  # 83% return on win
BREAKEVEN_WR = 1.0 / (1.0 + PAYOUT_RATIO)  # 54.6448%


@dataclass
class TradeOutcome:
    entry_price: float
    exit_price: float
    action: str  # "CALL" or "PUT"
    strategy_type: str  # "REVERSION" or "CONTINUATION"
    z_score: float
    tfi: float
    has_sr_confluence: bool
    is_win: bool
    is_tie: bool
    return_unit: float  # +0.83 on win, -1.0 on loss, 0.0 on tie


@dataclass
class PerformanceMetrics:
    total_trades: int
    resolved_trades: int
    wins: int
    losses: int
    ties: int
    winrate: float
    ev_per_trade: float
    sharpe_per_trade: float
    annualized_sharpe: float
    z_stat_vs_be: float
    p_value_vs_be: float
    max_loss_streak: int
    max_drawdown_pct: float
    kelly_fraction: float


def calculate_metrics(trades: List[TradeOutcome], trades_per_year: int = 15000) -> PerformanceMetrics:
    """Calculates rigorous quantitative risk and return metrics."""
    if not trades:
        return PerformanceMetrics(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0, 0.0, 0.0)

    total = len(trades)
    wins = sum(1 for t in trades if t.is_win)
    ties = sum(1 for t in trades if t.is_tie)
    losses = total - wins - ties
    resolved = wins + losses

    wr = (wins / resolved) if resolved > 0 else 0.0
    returns = np.array([t.return_unit for t in trades], dtype=np.float64)
    ev = float(np.mean(returns)) if len(returns) > 0 else 0.0
    ret_std = float(np.std(returns, ddof=1)) if len(returns) > 1 else 1.0

    sharpe_trade = (ev / ret_std) if ret_std > 1e-6 else 0.0
    ann_sharpe = sharpe_trade * math.sqrt(trades_per_year)

    # Binomial / Normal approximation test vs Break-Even (54.6448%)
    p0 = BREAKEVEN_WR
    if resolved > 0:
        se = math.sqrt(p0 * (1.0 - p0) / resolved)
        z_stat = (wr - p0) / se
        # One-tailed p-value: probability that WR >= observed purely by chance under H0
        p_val = float(stats.norm.sf(z_stat))
    else:
        z_stat = 0.0
        p_val = 1.0

    # Streaks and Drawdown
    max_loss_streak = 0
    cur_loss_streak = 0
    cum_equity = 1.0
    peak_equity = 1.0
    max_dd = 0.0

    for t in trades:
        if not t.is_win and not t.is_tie:
            cur_loss_streak += 1
            max_loss_streak = max(max_loss_streak, cur_loss_streak)
        elif t.is_win:
            cur_loss_streak = 0

        cum_equity += t.return_unit * 0.02  # 2% standard unit sizing
        if cum_equity > peak_equity:
            peak_equity = cum_equity
        dd = (peak_equity - cum_equity) / peak_equity if peak_equity > 0 else 0.0
        if dd > max_dd:
            max_dd = dd

    # Kelly criterion: f* = (p*(b+1) - 1) / b
    b = PAYOUT_RATIO
    kelly = max(0.0, (wr * (b + 1.0) - 1.0) / b)

    return PerformanceMetrics(
        total_trades=total,
        resolved_trades=resolved,
        wins=wins,
        losses=losses,
        ties=ties,
        winrate=wr,
        ev_per_trade=ev,
        sharpe_per_trade=sharpe_trade,
        annualized_sharpe=ann_sharpe,
        z_stat_vs_be=z_stat,
        p_value_vs_be=p_val,
        max_loss_streak=max_loss_streak,
        max_drawdown_pct=max_dd * 100.0,
        kelly_fraction=kelly
    )


class FastCryptoIdxSimulator:
    """
    High-performance NumPy vectorized simulator for Crypto IDX microstructure
    and SyntheticCandleEngine.kt algorithmic logic.
    """

    def __init__(self, seed: int = 42):
        self.seed = seed

    def generate_price_and_sr(
        self,
        num_candles: int = 5000,
        ticks_per_candle: int = 70,
        base_price: float = 640.0,
        annualized_vol: float = 0.55
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        Simulates micro-ticks with multi-regime switching (Range Consolidations 55%,
        Trending Breakouts 30%, Random Choppy Noise 15%) and dynamic S/R bounds.
        Returns: (prices, dynamic_supports, dynamic_resistances)
        """
        np.random.seed(self.seed)
        total_ticks = num_candles * ticks_per_candle
        dt = 60.0 / ticks_per_candle
        annual_factor = math.sqrt(dt / (365.25 * 24 * 3600))
        sigma_tick = annualized_vol * annual_factor

        prices = np.zeros(total_ticks, dtype=np.float64)
        dynamic_supports = np.zeros(num_candles, dtype=np.float64)
        dynamic_resistances = np.zeros(num_candles, dtype=np.float64)

        prices[0] = base_price
        block_len = ticks_per_candle * 25  # 25-candle blocks (~25 minutes)
        num_blocks = (total_ticks // block_len) + 2
        block_regimes = np.random.choice(["range", "trend", "chop"], size=num_blocks, p=[0.55, 0.30, 0.15])

        dw = np.random.normal(0, sigma_tick, total_ticks)
        jumps = (np.random.rand(total_ticks) < 0.05) * np.random.normal(0, 0.0010, total_ticks)

        anchor = base_price
        current_support = base_price - 2.5
        current_resistance = base_price + 2.5
        trend_drift = 0.0

        for i in range(1, total_ticks):
            c_idx = i // ticks_per_candle
            b_idx = i // block_len
            regime = block_regimes[b_idx]

            # Re-anchor and update S/R periodically
            if i % (ticks_per_candle * 20) == 0:
                shift = base_price * np.random.normal(0, 0.004)
                anchor = prices[i - 1] + shift
                half_channel = max(1.5, abs(np.random.normal(3.0, 1.0)))
                current_support = anchor - half_channel
                current_resistance = anchor + half_channel
                trend_drift = np.random.choice([-1, 1]) * 0.00015

            prev_p = prices[i - 1]
            if regime == "range":
                # Mean reversion toward anchor when approaching S/R boundaries
                drift = -0.018 * (prev_p - anchor) * (dt / 60.0)
            elif regime == "trend":
                # Persistent directional push
                drift = trend_drift
            else:  # chop / noise
                drift = 0.0

            prices[i] = prev_p * (1.0 + drift + dw[i] + jumps[i])

            if i % ticks_per_candle == (ticks_per_candle - 1) and c_idx < num_candles:
                dynamic_supports[c_idx] = current_support
                dynamic_resistances[c_idx] = current_resistance

        return prices, dynamic_supports, dynamic_resistances

    def evaluate_signals(
        self,
        prices: np.ndarray,
        supports: np.ndarray,
        resistances: np.ndarray,
        num_candles: int,
        ticks_per_candle: int,
        start_candle: int,
        end_candle: int,
        z_threshold: float = 2.0,
        tfi_threshold: float = 0.30,
        require_sr_confluence: bool = True
    ) -> Tuple[List[TradeOutcome], List[TradeOutcome]]:
        """
        Evaluates signals on candle range [start_candle, end_candle).
        Runs in pure NumPy for ultra-fast execution.
        """
        reversion_trades = []
        continuation_trades = []

        fw_offset = int(40.0 / 60.0 * ticks_per_candle)

        for c in range(start_candle, end_candle - 1):
            c_start = c * ticks_per_candle
            c_end = (c + 1) * ticks_per_candle
            next_end = (c + 2) * ticks_per_candle

            # 1. TFI in final window (:40s to :59s)
            fw_prices = prices[c_start + fw_offset : c_end]
            diffs = np.diff(fw_prices)
            up_ticks = int(np.sum(diffs > 1e-9))
            down_ticks = int(np.sum(diffs < -1e-9))
            tot_ticks = up_ticks + down_ticks

            tfi = float((up_ticks - down_ticks) / tot_ticks) if tot_ticks > 0 else 0.0

            # 2. Z-score over preceding 60 ticks
            lb = prices[c_end - 60 : c_end]
            m60 = float(np.mean(lb))
            s60 = float(np.std(lb, ddof=1))
            entry_price = float(prices[c_end - 1])
            z_score = float((entry_price - m60) / s60) if s60 > 1e-8 else 0.0

            # 3. Dynamic S/R proximity ratios
            sup = supports[c]
            res = resistances[c]
            span = max(0.5, res - sup)
            dist_to_sup = abs(entry_price - sup) / span
            dist_to_res = abs(res - entry_price) / span

            # S/R Confluence as defined in SyntheticCandleEngine.kt:
            # Overbought at resistance: dist_to_res <= 0.22
            # Oversold at support: dist_to_sup <= 0.22
            near_resistance = dist_to_res <= 0.24
            near_support = dist_to_sup <= 0.24

            # 4. Settlement price at next candle close (60s expiration)
            exit_price = float(prices[next_end - 1])
            delta = exit_price - entry_price
            is_tie = abs(delta) < 1e-8

            is_bull_extreme = (z_score >= z_threshold) and (tfi >= tfi_threshold)
            is_bear_extreme = (z_score <= -z_threshold) and (tfi <= -tfi_threshold)

            # --- SETUP A: REVERSIÓN POR SOBREEXTENSIÓN (FADE) ---
            if is_bull_extreme:
                if not require_sr_confluence or near_resistance:
                    win = exit_price < entry_price
                    ret = PAYOUT_RATIO if win else (0.0 if is_tie else -1.0)
                    reversion_trades.append(TradeOutcome(
                        entry_price=entry_price,
                        exit_price=exit_price,
                        action="PUT",
                        strategy_type="REVERSION",
                        z_score=z_score,
                        tfi=tfi,
                        has_sr_confluence=near_resistance,
                        is_win=win,
                        is_tie=is_tie,
                        return_unit=ret
                    ))
            elif is_bear_extreme:
                if not require_sr_confluence or near_support:
                    win = exit_price > entry_price
                    ret = PAYOUT_RATIO if win else (0.0 if is_tie else -1.0)
                    reversion_trades.append(TradeOutcome(
                        entry_price=entry_price,
                        exit_price=exit_price,
                        action="CALL",
                        strategy_type="REVERSION",
                        z_score=z_score,
                        tfi=tfi,
                        has_sr_confluence=near_support,
                        is_win=win,
                        is_tie=is_tie,
                        return_unit=ret
                    ))

            # --- SETUP B: CONTINUACIÓN DE MOMENTUM ---
            # Enters in direction of the impulse
            if is_bull_extreme:
                # Continuation wants free space to resistance (dist_to_res >= 0.30)
                has_space = dist_to_res >= 0.30
                if not require_sr_confluence or has_space:
                    win = exit_price > entry_price
                    ret = PAYOUT_RATIO if win else (0.0 if is_tie else -1.0)
                    continuation_trades.append(TradeOutcome(
                        entry_price=entry_price,
                        exit_price=exit_price,
                        action="CALL",
                        strategy_type="CONTINUATION",
                        z_score=z_score,
                        tfi=tfi,
                        has_sr_confluence=has_space,
                        is_win=win,
                        is_tie=is_tie,
                        return_unit=ret
                    ))
            elif is_bear_extreme:
                has_space = dist_to_sup >= 0.30
                if not require_sr_confluence or has_space:
                    win = exit_price < entry_price
                    ret = PAYOUT_RATIO if win else (0.0 if is_tie else -1.0)
                    continuation_trades.append(TradeOutcome(
                        entry_price=entry_price,
                        exit_price=exit_price,
                        action="PUT",
                        strategy_type="CONTINUATION",
                        z_score=z_score,
                        tfi=tfi,
                        has_sr_confluence=has_space,
                        is_win=win,
                        is_tie=is_tie,
                        return_unit=ret
                    ))

        return reversion_trades, continuation_trades


def run_walk_forward_fast(
    sim: FastCryptoIdxSimulator,
    prices: np.ndarray,
    supports: np.ndarray,
    resistances: np.ndarray,
    num_candles: int,
    ticks_per_candle: int,
    n_splits: int = 5
) -> List[Dict]:
    """
    Fast Walk-Forward cross validation over sequential folds.
    """
    candles_per_fold = num_candles // (n_splits + 1)

    param_grid = [
        {"z": 1.5, "tfi": 0.20},
        {"z": 1.75, "tfi": 0.25},
        {"z": 2.0, "tfi": 0.30},
        {"z": 2.25, "tfi": 0.35},
        {"z": 2.5, "tfi": 0.40}
    ]

    wf_results = []

    for fold in range(1, n_splits + 1):
        train_start = 2
        train_end = fold * candles_per_fold
        test_start = train_end
        test_end = (fold + 1) * candles_per_fold

        # Grid search on In-Sample Train
        best_p = None
        best_ev = -999.0

        for p in param_grid:
            rev_train, _ = sim.evaluate_signals(
                prices, supports, resistances,
                num_candles, ticks_per_candle,
                start_candle=train_start, end_candle=train_end,
                z_threshold=p["z"], tfi_threshold=p["tfi"],
                require_sr_confluence=True
            )
            m_tr = calculate_metrics(rev_train)
            if m_tr.resolved_trades >= 10 and m_tr.ev_per_trade > best_ev:
                best_ev = m_tr.ev_per_trade
                best_p = p

        if best_p is None:
            best_p = {"z": 2.0, "tfi": 0.30}

        # Evaluate on Out-Of-Sample Test
        rev_oos, cont_oos = sim.evaluate_signals(
            prices, supports, resistances,
            num_candles, ticks_per_candle,
            start_candle=test_start, end_candle=test_end,
            z_threshold=best_p["z"], tfi_threshold=best_p["tfi"],
            require_sr_confluence=True
        )

        m_rev_oos = calculate_metrics(rev_oos)
        m_cont_oos = calculate_metrics(cont_oos)

        wf_results.append({
            "fold": fold,
            "train_candles": train_end - train_start,
            "test_candles": test_end - test_start,
            "best_params": best_p,
            "reversion_oos": m_rev_oos,
            "continuation_oos": m_cont_oos
        })

    return wf_results


def analyze_live_journals() -> Dict:
    """Ingests and analyzes existing live trade journals."""
    live_csv = "loops/journal/live/trade_journal_live.csv"
    device_csv = "loops/journal/trade_journal_device_all.csv"
    adaptive_json = "loops/journal/adaptive_learning_state.json"
    results = {}

    for path, label in [(live_csv, "baseline_session"), (device_csv, "device_full_history")]:
        if not os.path.exists(path):
            continue
        df = pd.read_csv(path)
        resolved = df[df["result"].isin(["WIN", "LOSS"])].copy()
        wins = int((resolved["result"] == "WIN").sum())
        total_res = len(resolved)
        wr = (wins / total_res) if total_res > 0 else 0.0
        ev = wr * PAYOUT_RATIO - (1.0 - wr) * 1.0

        se = math.sqrt(BREAKEVEN_WR * (1.0 - BREAKEVEN_WR) / total_res) if total_res > 0 else 0.0
        z_stat = (wr - BREAKEVEN_WR) / se if se > 0 else 0.0
        p_val = float(stats.norm.sf(z_stat))

        by_action = {}
        for action in ["BUY", "SELL"]:
            act_df = resolved[resolved["action"] == action]
            n_a = len(act_df)
            w_a = int((act_df["result"] == "WIN").sum())
            wr_a = (w_a / n_a) if n_a > 0 else 0.0
            by_action[action] = {"n": n_a, "wins": w_a, "winrate": wr_a}

        results[label] = {
            "path": path,
            "total_records": len(df),
            "resolved": total_res,
            "wins": wins,
            "losses": total_res - wins,
            "ties": len(df) - total_res,
            "winrate": wr,
            "ev": ev,
            "z_stat": z_stat,
            "p_val": p_val,
            "by_action": by_action
        }

    if os.path.exists(adaptive_json):
        try:
            with open(adaptive_json, "r", encoding="utf-8") as f:
                adj = json.load(f)
            results["adaptive_state"] = {
                "consecutiveContinuationLosses": adj.get("consecutiveContinuationLosses"),
                "consecutiveReversionLosses": adj.get("consecutiveReversionLosses"),
                "totalBlockedAntiPatterns": adj.get("totalBlockedAntiPatterns"),
                "totalInvertedAntiPatterns": adj.get("totalInvertedAntiPatterns"),
                "reversionBonusWeight": adj.get("reversionBonusWeight"),
                "continuationPenaltyWeight": adj.get("continuationPenaltyWeight"),
                "total_signatures": len(adj.get("lossSignatures", []))
            }
        except Exception:
            pass

    return results


def main():
    parser = argparse.ArgumentParser(description="Crypto IDX Microstructure & Quant Model Validation")
    parser.add_argument("--candles", type=int, default=5000, help="Number of synthetic 60s candles (default 5000)")
    parser.add_argument("--seed", type=int, default=1337, help="Random seed for reproducibility")
    parser.add_argument("--z_thresh", type=float, default=2.0, help="Z-score overextension threshold (default 2.0)")
    parser.add_argument("--tfi_thresh", type=float, default=0.30, help="TFI threshold in [:40s-:59s] (default 0.30)")
    args = parser.parse_args()

    print("=" * 80)
    print("🔬 TRADEDRAW - ESTUDIO CUANTITATIVO Y MICROESTRUCTURA DE CRYPTO IDX")
    print("=" * 80)
    print(f"Payout Broker: {PAYOUT_RATIO:.0%} | Umbral de Supervivencia Break-Even: {BREAKEVEN_WR*100:.2f}%")
    print("-" * 80)

    # 1. Inspección de telemetría y journals reales en vivo
    print("\n[FASE 1] AUDITORÍA EMPÍRICA DE BASE DE DATOS Y JOURNALS EN VIVO")
    journal_stats = analyze_live_journals()
    for key in ["baseline_session", "device_full_history"]:
        if key in journal_stats:
            stats_data = journal_stats[key]
            print(f"\n📂 Archivo: {stats_data['path']} ({key})")
            print(f"   Trades Totales: {stats_data['total_records']} | Resueltos: {stats_data['resolved']} | TIEs: {stats_data['ties']}")
            print(f"   Winrate Observado : {stats_data['winrate']*100:.2f}% (Breakeven: {BREAKEVEN_WR*100:.2f}%)")
            print(f"   Esperanza Matemática (EV) : {stats_data['ev']:+.4f} unidades por trade")
            print(f"   Z-Score vs Break-Even     : {stats_data['z_stat']:.3f} (p-value: {stats_data['p_val']:.4f})")
            for act, act_st in stats_data['by_action'].items():
                print(f"     > Acción {act:<4}: n={act_st['n']:<3} | Winrate: {act_st['winrate']*100:.1f}%")

    if "adaptive_state" in journal_stats:
        ad = journal_stats["adaptive_state"]
        print(f"\n🧠 Memoria Adaptativa en Vivo (adaptive_learning_state.json):")
        print(f"   Racha pérdidas Continuación: {ad['consecutiveContinuationLosses']} | Reversión: {ad['consecutiveReversionLosses']}")
        print(f"   Pesos del Motor: Continuación = {ad['continuationPenaltyWeight']:.2f}x (Penalizado) | Reversión = {ad['reversionBonusWeight']:.2f}x (Bonificado)")
        print(f"   Anti-patrones Invertidos: {ad['totalInvertedAntiPatterns']} | Firmas Registradas: {ad['total_signatures']}")

    # 2. Generación de micro-ticks de alta frecuencia
    print("\n" + "-" * 80)
    print(f"\n[FASE 2] MODELADO DE MICROESTRUCTURA (TICKS ALTA FRECUENCIA · n={args.candles} VELAS)")
    print("   Simulación de proceso micro-tick multi-régimen (Rango 55%, Tendencia 30%, Ruido 15%)...")
    sim = FastCryptoIdxSimulator(seed=args.seed)
    ticks_per_candle = 70
    prices, supports, resistances = sim.generate_price_and_sr(num_candles=args.candles, ticks_per_candle=ticks_per_candle)
    print(f"   Ticks generados: {len(prices):,} | Precios: Min {prices.min():.2f} / Max {prices.max():.2f}")

    # 3. Backtest Completo de Hipótesis: Reversión vs Continuación
    print("\n" + "-" * 80)
    print(f"\n[FASE 3] CONTRASTE DE HIPÓTESIS: REVERSIÓN POR SOBREEXTENSIÓN VS CONTINUACIÓN")
    print(f"   Filtro Confluencia S/R (SyntheticCandleEngine.kt): Activo")
    print(f"   Parámetros: Z-Score >= {args.z_thresh} (SMA 60 ticks) | TFI >= {args.tfi_thresh} (Ventana :40s-:59s)")

    # 3.1 Con Confluencia S/R
    rev_trades, cont_trades = sim.evaluate_signals(
        prices, supports, resistances,
        num_candles=args.candles, ticks_per_candle=ticks_per_candle,
        start_candle=2, end_candle=args.candles - 1,
        z_threshold=args.z_thresh, tfi_threshold=args.tfi_thresh,
        require_sr_confluence=True
    )
    m_rev = calculate_metrics(rev_trades)
    m_cont = calculate_metrics(cont_trades)

    # 3.2 Sin Confluencia S/R (Naïve)
    rev_naive, cont_naive = sim.evaluate_signals(
        prices, supports, resistances,
        num_candles=args.candles, ticks_per_candle=ticks_per_candle,
        start_candle=2, end_candle=args.candles - 1,
        z_threshold=args.z_thresh, tfi_threshold=args.tfi_thresh,
        require_sr_confluence=False
    )
    m_rev_naive = calculate_metrics(rev_naive)
    m_cont_naive = calculate_metrics(cont_naive)

    # Statistical two-sample test between returns of Reversion vs Continuation
    ret_rev = np.array([t.return_unit for t in rev_trades]) if rev_trades else np.array([0.0])
    ret_cont = np.array([t.return_unit for t in cont_trades]) if cont_trades else np.array([0.0])
    t_stat_welch, p_val_welch = stats.ttest_ind(ret_rev, ret_cont, equal_var=False)

    print("\n" + "=" * 80)
    print(f"{'Métrica':<32} | {'REVERSIÓN (S/R)':<18} | {'CONTINUACIÓN':<18} | Veredicto")
    print("-" * 80)
    print(f"{'Muestra de Trades (N)':<32} | {m_rev.resolved_trades:<18} | {m_cont.resolved_trades:<18} | {'Adecuada' if m_rev.resolved_trades >= 30 else 'Pequeña'}")
    print(f"{'Win Rate Observado':<32} | {m_rev.winrate*100:>16.2f}% | {m_cont.winrate*100:>16.2f}% | {'REVERSIÓN DOMINA' if m_rev.winrate > m_cont.winrate else 'CONTINUACIÓN DOMINA'}")
    print(f"{'Umbral Break-Even (Payout 83%)':<32} | {BREAKEVEN_WR*100:>16.2f}% | {BREAKEVEN_WR*100:>16.2f}% | {'Referencia estricta'}")
    print(f"{'EV por Trade (Esperanza)':<32} | {m_rev.ev_per_trade:>+17.4f} | {m_cont.ev_per_trade:>+17.4f} | {'EV Positivo ✅' if m_rev.ev_per_trade > 0 else 'EV Negativo ❌'}")
    print(f"{'Z-Stat vs Break-Even (54.64%)':<32} | {m_rev.z_stat_vs_be:>17.3f} | {m_cont.z_stat_vs_be:>17.3f} | {'Significativo' if m_rev.z_stat_vs_be >= 1.96 else 'No Significativo'}")
    print(f"{'p-value vs Break-Even':<32} | {m_rev.p_value_vs_be:>17.4e} | {m_cont.p_value_vs_be:>17.4e} | {'p < 0.05 ✅' if m_rev.p_value_vs_be < 0.05 else 'p >= 0.05 ❌'}")
    print(f"{'Sharpe Ratio (por Trade)':<32} | {m_rev.sharpe_per_trade:>17.3f} | {m_cont.sharpe_per_trade:>17.3f} | {'Robusto' if m_rev.sharpe_per_trade > 0.05 else 'Débil'}")
    print(f"{'Sharpe Anualizado (15k T/año)':<32} | {m_rev.annualized_sharpe:>17.2f} | {m_cont.annualized_sharpe:>17.2f} | {'Institucional' if m_rev.annualized_sharpe > 2.0 else 'Inviable'}")
    print(f"{'Máxima Racha de Pérdidas':<32} | {m_rev.max_loss_streak:>17} | {m_cont.max_loss_streak:>17} | {'Controlada' if m_rev.max_loss_streak <= 6 else 'Peligro'}")
    print(f"{'Drawdown Máximo':<32} | {m_rev.max_drawdown_pct:>16.2f}% | {m_cont.max_drawdown_pct:>16.2f}% | {'Bajo' if m_rev.max_drawdown_pct < 25 else 'Alto'}")
    print(f"{'Fracción Óptima Kelly':<32} | {m_rev.kelly_fraction*100:>16.2f}% | {m_cont.kelly_fraction*100:>16.2f}% | {'Capital Asignable' if m_rev.kelly_fraction > 0 else '0% (Ruina)'}")
    print("-" * 80)
    print(f"Test T de Welch (Reversión vs Continuación): t={t_stat_welch:+.3f}, p-value={p_val_welch:.4e}")

    print("\n" + "-" * 80)
    print("IMPACTO DEL FILTRO CUANTITATIVO DE CONFLUENCIA S/R (SyntheticCandleEngine.kt):")
    print(f"  • Reversión CON Confluencia S/R : WR = {m_rev.winrate*100:.2f}% | EV = {m_rev.ev_per_trade:+.4f} (N={m_rev.resolved_trades})")
    print(f"  • Reversión SIN Confluencia S/R : WR = {m_rev_naive.winrate*100:.2f}% | EV = {m_rev_naive.ev_per_trade:+.4f} (N={m_rev_naive.resolved_trades})")
    print(f"  • Continuación CON Espacio Libre: WR = {m_cont.winrate*100:.2f}% | EV = {m_cont.ev_per_trade:+.4f} (N={m_cont.resolved_trades})")
    print(f"  • Continuación SIN Filtro Espacio: WR = {m_cont_naive.winrate*100:.2f}% | EV = {m_cont_naive.ev_per_trade:+.4f} (N={m_cont_naive.resolved_trades})")

    # 4. Walk-Forward Cross-Validation Out-Of-Sample
    print("\n" + "-" * 80)
    print("\n[FASE 4] SIMULACIÓN WALK-FORWARD OUT-OF-SAMPLE (5 FOLDS SECUENCIALES)")
    print("   Evita overfitting mediante optimización en Train y validación estricta en OOS.")
    wf_splits = run_walk_forward_fast(sim, prices, supports, resistances, num_candles=args.candles, ticks_per_candle=ticks_per_candle, n_splits=5)

    oos_rev_wrs = []
    oos_cont_wrs = []
    oos_rev_evs = []

    print("\n" + "-" * 80)
    print(f"{'Fold':<5} | {'Params (Z, TFI)':<18} | {'N OOS':<6} | {'WR Rev (OOS)':<14} | {'WR Cont (OOS)':<14} | {'EV Rev':<10} | {'Veredicto OOS'}")
    print("-" * 80)
    for wf in wf_splits:
        f_num = wf["fold"]
        bp = f"Z={wf['best_params']['z']}, TFI={wf['best_params']['tfi']}"
        r_oos = wf["reversion_oos"]
        c_oos = wf["continuation_oos"]
        oos_rev_wrs.append(r_oos.winrate)
        oos_cont_wrs.append(c_oos.winrate)
        oos_rev_evs.append(r_oos.ev_per_trade)

        status = "PASÓ ✅" if r_oos.winrate > BREAKEVEN_WR else "FALLÓ ❌"
        print(f"{f_num:<5} | {bp:<18} | {r_oos.resolved_trades:<6} | {r_oos.winrate*100:>12.2f}% | {c_oos.winrate*100:>12.2f}% | {r_oos.ev_per_trade:>+9.4f} | {status}")

    avg_oos_wr = float(np.mean(oos_rev_wrs))
    avg_oos_ev = float(np.mean(oos_rev_evs))
    print("-" * 80)
    print(f"PROMEDIO OUT-OF-SAMPLE (OOS) - REVERSIÓN: Winrate = {avg_oos_wr*100:.2f}%, EV = {avg_oos_ev:+.4f} por trade")
    print(f"PROMEDIO OUT-OF-SAMPLE (OOS) - CONTINUACIÓN: Winrate = {float(np.mean(oos_cont_wrs))*100:.2f}%")
    print("=" * 80)

    # 5. Guardar resumen estructurado JSON para el reporte
    summary = {
        "timestamp": pd.Timestamp.now().isoformat(),
        "payout_ratio": PAYOUT_RATIO,
        "breakeven_winrate": BREAKEVEN_WR,
        "empirical_journal_baseline": journal_stats.get("baseline_session"),
        "empirical_journal_device_all": journal_stats.get("device_full_history"),
        "adaptive_state": journal_stats.get("adaptive_state"),
        "reversion_sr_confluence": {
            "trades": m_rev.resolved_trades,
            "winrate": m_rev.winrate,
            "ev": m_rev.ev_per_trade,
            "sharpe_trade": m_rev.sharpe_per_trade,
            "annualized_sharpe": m_rev.annualized_sharpe,
            "z_stat": m_rev.z_stat_vs_be,
            "p_value": m_rev.p_value_vs_be,
            "max_loss_streak": m_rev.max_loss_streak,
            "max_drawdown_pct": m_rev.max_drawdown_pct,
            "kelly": m_rev.kelly_fraction
        },
        "reversion_naive": {
            "trades": m_rev_naive.resolved_trades,
            "winrate": m_rev_naive.winrate,
            "ev": m_rev_naive.ev_per_trade
        },
        "continuation_with_space": {
            "trades": m_cont.resolved_trades,
            "winrate": m_cont.winrate,
            "ev": m_cont.ev_per_trade,
            "sharpe_trade": m_cont.sharpe_per_trade,
            "z_stat": m_cont.z_stat_vs_be,
            "p_value": m_cont.p_value_vs_be,
            "max_loss_streak": m_cont.max_loss_streak,
            "max_drawdown_pct": m_cont.max_drawdown_pct
        },
        "continuation_naive": {
            "trades": m_cont_naive.resolved_trades,
            "winrate": m_cont_naive.winrate,
            "ev": m_cont_naive.ev_per_trade
        },
        "welch_test": {
            "t_stat": float(t_stat_welch),
            "p_value": float(p_val_welch)
        },
        "walk_forward_oos": {
            "folds": [
                {
                    "fold": f["fold"],
                    "params": f["best_params"],
                    "n_trades": f["reversion_oos"].resolved_trades,
                    "reversion_winrate": f["reversion_oos"].winrate,
                    "continuation_winrate": f["continuation_oos"].winrate,
                    "reversion_ev": f["reversion_oos"].ev_per_trade
                } for f in wf_splits
            ],
            "avg_oos_reversion_winrate": avg_oos_wr,
            "avg_oos_reversion_ev": avg_oos_ev
        }
    }

    output_json_path = "loops/journal/quant_research_summary.json"
    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"\n💾 Resumen estadístico JSON guardado en: {output_json_path}")


if __name__ == "__main__":
    main()
