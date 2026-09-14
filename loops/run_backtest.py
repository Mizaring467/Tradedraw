#!/usr/bin/env python3
import csv
import math
import os
import sys

def calculate_statistical_power():
    print("\n" + "=" * 65)
    print("      POTENCIA ESTADISTICA (Confianza 95% para superar 54.9% WR)")
    print("=" * 65)
    print(" Trades | Min. WR Obs. | Horas (1.1 T/H) | Significado")
    print("-" * 65)
    
    # We want observed_wr - 1.96 * sqrt(observed_wr * (1-observed_wr) / N) > 0.549
    # Let's show required observed WR for various N
    target_wr = 0.549
    n_list = [10, 30, 50, 100, 200, 500, 1000]
    for n in n_list:
        # Just assume p ~ 0.55 for margin of error
        margin = 1.96 * math.sqrt(0.55 * 0.45 / n)
        req_wr = target_wr + margin
        if req_wr >= 1.0:
            req_wr = 0.999
        hours = n / 1.1
        print(f" {n:<6d} | {req_wr*100:>11.1f}% | {hours:>13.1f} | {'Ruido' if n < 100 else 'Significativo'}")
    print("=" * 65)


def process_journal(csv_path):
    valid_trades = []
    
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 1. Isolar fila corrupta
            if row["timestamp"] == "1789080600323" or float(row["base_balance"]) < 10:
                continue
            
            # 2. Clasificar TIE como VOID
            if row["result"] == "TIE":
                continue
                
            valid_trades.append(row)
            
    return valid_trades


def run_evaluation(trades, title):
    total_trades = len(trades)
    wins = 0
    losses = 0
    
    base_stake = 100000.0
    payout_rate = 0.8225
    
    balance = 0.0
    peak_balance = 0.0
    max_dd = 0.0
    current_streak = 0
    max_streak = 0
    
    for row in trades:
        # Diff is in reason: Diff=-100003.83, Diff=+82001.92
        reason = row["reason"]
        
        # Determine actual P&L from reason if possible, else standard
        if "Diff=" in reason:
            diff_str = reason.split("Diff=")[1].split(" ")[0]
            try:
                pnl = float(diff_str)
            except ValueError:
                pnl = 0.0
        else:
            pnl = base_stake * payout_rate if row["result"] == "WIN" else -base_stake
            
        if row["result"] == "WIN":
            wins += 1
            current_streak = 0
        elif row["result"] == "LOSS":
            losses += 1
            current_streak += 1
            if current_streak > max_streak:
                max_streak = current_streak
        
        balance += pnl
        if balance > peak_balance:
            peak_balance = balance
        dd = peak_balance - balance
        if dd > max_dd:
            max_dd = dd

    wr = (wins / total_trades) if total_trades > 0 else 0
    
    # Esperada matematica (EV) real medida (normalizado a stake base 100000)
    avg_win = base_stake * payout_rate
    avg_loss = base_stake
    ev = (wr * avg_win) - ((1 - wr) * avg_loss)
    
    print(f"\n[{title.upper()}] (N={total_trades})")
    print("-" * 65)
    print(f" Wins: {wins} | Losses: {losses} | WR: {wr*100:.1f}% (Breakeven: 54.9%)")
    print(f" P&L Neto: {balance:,.2f} COP")
    print(f" Max Drawdown (COP): {max_dd:,.2f} | Racha Max Perdedora: {max_streak}")
    print(f" ESPERANZA MATEMATICA (EV) por trade (base 100k): {ev:,.2f} COP")


def run_sanity_and_walk_forward():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    csv_path = os.path.join(base_dir, "journal", "live", "trade_journal_live.csv")
    
    if not os.path.exists(csv_path):
        print(f"No se encontró el journal: {csv_path}")
        return
        
    print("=" * 65)
    print("       TEST DE CORDURA Y WALK-FORWARD (REPLAY REAL)")
    print("=" * 65)
    
    trades = process_journal(csv_path)
    
    # 1. Total (Test de Cordura Baseline)
    run_evaluation(trades, "Test de Cordura (Baseline Total)")
    
    # 2. Walk-Forward
    # 60% In-Sample, 40% Out-Of-Sample
    split_idx = int(len(trades) * 0.6)
    in_sample = trades[:split_idx]
    out_of_sample = trades[split_idx:]
    
    run_evaluation(in_sample, "Walk-Forward: IN-SAMPLE (Calibracion)")
    run_evaluation(out_of_sample, "Walk-Forward: OUT-OF-SAMPLE (Validacion)")
    
    print("\nNota: El backtester asume ejecución perfecta en el replay.")
    print("Los trades fantasma (14% de la muestra real) o 'TIE' han sido filtrados.")
    print("No modela slippage, latencia de clic, ni rechazo de orden del broker en simulaciones futuras.")
    
    calculate_statistical_power()

if __name__ == "__main__":
    run_sanity_and_walk_forward()
