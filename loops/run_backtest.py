#!/usr/bin/env python3
import json
import os
import sys

def evaluate_candle_strategy(fixture):
    candles = fixture["candles"]
    strategy = fixture.get("strategy", "AUTO_ADAPTIVE")
    supports = fixture.get("supports", [])
    resistances = fixture.get("resistances", [])
    
    if not candles:
        return None, "No candles"
        
    last_candle = candles[-1]
    
    # Sideways filter check
    avg_body = sum(c["bodyHeight"] for c in candles) / len(candles)
    doji_count = sum(1 for c in candles if c["bodyHeight"] < 10 or c["type"] == "DOJI")
    if avg_body < 12.0 or (doji_count / len(candles)) >= 0.50:
        return None, "Mercado lateral / dojis filtrado"

    threshold = 25.0
    latest_price = (last_candle["bodyTopY"] + last_candle["bodyBottomY"]) / 2.0
    
    has_bottom_rejection = last_candle.get("bottomWickRatio", 0.0) >= 0.40
    has_top_rejection = last_candle.get("topWickRatio", 0.0) >= 0.40
    
    is_near_support = any(abs(latest_price - s) <= threshold for s in supports) or (has_bottom_rejection and any(abs(last_candle["bottomY"] - s) <= threshold for s in supports))
    is_near_resistance = any(abs(latest_price - r) <= threshold for r in resistances) or (has_top_rejection and any(abs(last_candle["topY"] - r) <= threshold for r in resistances))
    
    touches_support = is_near_support and (has_bottom_rejection or last_candle["type"] == "GREEN")
    touches_resistance = is_near_resistance and (has_top_rejection or last_candle["type"] == "RED")

    # 1. False Breakout Trap
    if has_bottom_rejection and any(last_candle["bottomY"] >= s + 8.0 and last_candle["bodyBottomY"] <= s + 6.0 for s in supports):
        return "BUY", "Trampa en soporte"
    if has_top_rejection and any(last_candle["topY"] <= r - 8.0 and last_candle["bodyTopY"] >= r - 6.0 for r in resistances):
        return "SELL", "Trampa en resistencia"

    # 2. Rejection Wicks
    if has_bottom_rejection and is_near_support:
        return "BUY", "Mecha rechazo en soporte"
    if has_top_rejection and is_near_resistance:
        return "SELL", "Mecha rechazo en resistencia"

    # 3. Choque Pullback
    if len(candles) >= 3:
        c0, c1, c2 = candles[-1], candles[-2], candles[-3]
        if c2["bodyBottomY"] > c1["bodyTopY"] and abs(c0["bottomY"] - c2["bodyTopY"]) <= threshold:
            return "BUY", "Choque pullback alcista"
        if c2["bodyTopY"] < c1["bodyBottomY"] and abs(c0["topY"] - c2["bodyBottomY"]) <= threshold:
            return "SELL", "Choque pullback bajista"

    # 4. Exhaustion
    if len(candles) >= 3:
        c0, c1, c2 = candles[-1], candles[-2], candles[-3]
        all_red = all(c["type"] == "RED" for c in [c0, c1, c2])
        all_green = all(c["type"] == "GREEN" for c in [c0, c1, c2])
        v1, v2, v3 = c2["bodyHeight"], c1["bodyHeight"], c0["bodyHeight"]
        decaying = (v1 > v2 > v3) and (v3 <= v1 * 0.45)
        if all_red and decaying:
            return "BUY", "Agotamiento 3 rojas"
        if all_green and decaying:
            return "SELL", "Agotamiento 3 verdes"

    # 5. Simple Bounce
    if touches_support and last_candle["type"] == "GREEN":
        return "BUY", "Rebote confirmado soporte"
    if touches_resistance and last_candle["type"] == "RED":
        return "SELL", "Rebote confirmado resistencia"

    return None, "Sin confluencia suficiente"

def run_backtest(fixtures_path=None):
    if fixtures_path is None:
        base_dir = os.path.dirname(os.path.abspath(__file__))
        fixtures_path = os.path.join(base_dir, "fixtures", "fixtures.json")

    if not os.path.exists(fixtures_path):
        print(f"Error: Fixtures no encontrados en {fixtures_path}")
        sys.exit(1)
        
    with open(fixtures_path, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    fixtures = data.get("fixtures", [])
    
    total_trades = 0
    wins = 0
    losses = 0
    initial_balance = 1000000.0
    balance = initial_balance
    peak_balance = initial_balance
    max_drawdown = 0.0
    current_loss_streak = 0
    max_loss_streak = 0
    stake = 80000.0
    payout = 0.83
    
    print("=" * 65)
    print("           TRADEDRAW LOCAL STRATEGY BACKTESTER")
    print("=" * 65)
    
    for idx, fix in enumerate(fixtures):
        name = fix["name"]
        expected = fix.get("expected_action")
        outcome_candle = fix.get("outcome_future_candle")
        
        action, reason = evaluate_candle_strategy(fix)
        
        if action is not None:
            total_trades += 1
            # Check outcome
            is_win = (action == "BUY" and outcome_candle == "GREEN") or (action == "SELL" and outcome_candle == "RED")
            if is_win:
                wins += 1
                profit = stake * payout
                balance += profit
                current_loss_streak = 0
                res_str = "WIN (+%.0f COP)" % profit
            else:
                losses += 1
                balance -= stake
                current_loss_streak += 1
                if current_loss_streak > max_loss_streak:
                    max_loss_streak = current_loss_streak
                res_str = "LOSS (-%.0f COP)" % stake
                
            if balance > peak_balance:
                peak_balance = balance
            dd = (peak_balance - balance) / peak_balance * 100.0
            if dd > max_drawdown:
                max_drawdown = dd
                
            print(f"[{idx+1:02d}] {name:<32} | {action:<4} -> {res_str:<18} ({reason})")
        else:
            print(f"[{idx+1:02d}] {name:<32} | NO TRADE (Filtrado: {reason})")
            
    win_rate = (wins / total_trades * 100.0) if total_trades > 0 else 0.0
    net_profit = balance - initial_balance
    
    print("-" * 65)
    print(f"RESULTADOS: Trades: {total_trades} | Wins: {wins} | Losses: {losses} | WR: {win_rate:.1f}%")
    print(f"P&L Neto: {net_profit:+,.2f} COP | Max Drawdown: {max_drawdown:.2f}% | Racha Max L: {max_loss_streak}")
    print("=" * 65)
    
    return {
        "trades": total_trades,
        "wins": wins,
        "losses": losses,
        "win_rate": win_rate,
        "net_profit": net_profit,
        "max_drawdown": max_drawdown,
        "max_loss_streak": max_loss_streak
    }

if __name__ == "__main__":
    run_backtest()
