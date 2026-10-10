import subprocess
import time
import json
import urllib.request
import re
import sys
import os

# Fix Windows console UTF-8 encoding
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

SERIAL = "UGU8KFUKM7E6CE8D"
STATUS_URL = "http://127.0.0.1:8080/status"
SESSION_DURATION_SEC = 25 * 60  # 25 minutos = 1500 segundos

def adb_cmd(args):
    cmd = ["adb", "-s", SERIAL] + args
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def ensure_port_forward():
    subprocess.run(["adb", "-s", SERIAL, "forward", "tcp:8080", "tcp:8080"], capture_output=True)

def get_status():
    try:
        req = urllib.request.Request(STATUS_URL, headers={"User-Agent": "Monitor25m"})
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            return json.loads(resp.read().decode())
    except Exception:
        ensure_port_forward()
        return None

def capture_screenshot(filename):
    remote_path = f"/sdcard/{filename}"
    adb_cmd(["shell", f"screencap -p {remote_path}"])
    adb_cmd(["pull", remote_path, filename])

def main():
    print("=" * 70)
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] INICIANDO SESION DE MONITOREO EN VIVO (25 MINUTOS)")
    print(f"Dispositivo: {SERIAL} | Duración objetivo: 25 min (1500s)")
    print(f"Estrategia activa: Opción D (Anti-Contra-Tendencia + Matriz Confluencia >= 80 pts)")
    print("=" * 70)

    ensure_port_forward()
    start_time = time.time()
    end_time = start_time + SESSION_DURATION_SEC

    # Estado inicial
    initial_status = get_status()
    if not initial_status:
        print("Esperando conexión con TradeDraw...")
        time.sleep(2)
        initial_status = get_status()

    initial_balance = initial_status.get("balance", 0.0) if initial_status else 0.0
    initial_wins = initial_status.get("risk", {}).get("totalWins", 0) if initial_status else 0
    initial_losses = initial_status.get("risk", {}).get("totalLosses", 0) if initial_status else 0

    print(f"Saldo inicial: {initial_balance:,.2f} COP")
    print(f"Marcador base: {initial_wins}W / {initial_losses}L")
    capture_screenshot("live_25m_session_start.png")
    print("Captura inicial guardada: live_25m_session_start.png")
    print("-" * 70)

    trades_recorded = []
    trade_in_flight = False
    current_trade = {}
    last_heartbeat_minute = -1
    last_logcat_dump_time = start_time

    while time.time() < end_time:
        elapsed_sec = int(time.time() - start_time)
        remaining_sec = int(end_time - time.time())
        status = get_status()
        now_str = time.strftime('%Y-%m-%d %H:%M:%S')

        if not status:
            time.sleep(1.0)
            continue

        risk = status.get("risk", {})
        has_pending = risk.get("hasPendingTrade", False)
        balance = status.get("balance", 0.0)
        analysis = status.get("analysis", {})
        feed = status.get("feed", {})

        current_min = int(time.strftime('%M'))
        current_sec = int(time.strftime('%S'))

        # Heartbeat cada 60s o en ventana sniper (:58)
        if current_sec == 58 and current_min != last_heartbeat_minute:
            last_heartbeat_minute = current_min
            trend = analysis.get("trend", "UNKNOWN")
            c_power = analysis.get("callPower", 0)
            p_power = analysis.get("putPower", 0)
            cur_p = analysis.get("currentPriceY", 0.0)
            res_p = analysis.get("dynamicResistancePrice", 0.0)
            sup_p = analysis.get("dynamicSupportPrice", 0.0)
            rem_m = remaining_sec // 60
            rem_s = remaining_sec % 60
            print(f"⏱ [{now_str} :58s] Trend: {trend} (Call:{c_power}% Put:{p_power}%) | Price:{cur_p:.6f} [S:{sup_p:.6f} R:{res_p:.6f}] | Bal:{balance:,.0f} | Restante:{rem_m}m{rem_s:02d}s")

        # DETECCIÓN DE ENTRADA DE TRADE
        if not trade_in_flight and has_pending:
            trade_in_flight = True
            trade_num = len(trades_recorded) + 1
            print(f"\n" + "⚡" * 35)
            print(f"[{now_str}] ¡TRADE #{trade_num} EJECUTADO EN VIVO!")
            print(f"   Saldo antes: {balance:,.2f} COP")

            # Esperar 1.5s para asentar logs y captura
            time.sleep(1.5)
            screenshot_placed = f"live_25m_trade_{trade_num}_placed.png"
            capture_screenshot(screenshot_placed)

            # Extraer log reciente de TradingEngine y SyntheticCandleEngine
            recent_logs = adb_cmd(["logcat", "-d", "-v", "time", "-s", "TradingEngine", "SyntheticCandleEngine", "AutoTradeAccessibilityService"])
            signal_match = re.search(r"🎯 ([A-Z0-9_]+): ([^\n]+)", recent_logs)
            signal_desc = signal_match.group(0) if signal_match else "Señal no parseada en logcat"
            confluence_match = re.search(r"\[Confluencia: (\d+)pts\]", recent_logs)
            confluence_score = int(confluence_match.group(1)) if confluence_match else None

            print(f"   Señal: {signal_desc}")
            if confluence_score:
                print(f"   Confluencia registrada: {confluence_score} pts")
            print(f"   Captura: {screenshot_placed}")

            current_trade = {
                "trade_num": trade_num,
                "entry_time": now_str,
                "entry_epoch": time.time(),
                "balance_before": balance,
                "signal": signal_desc,
                "confluence_score": confluence_score,
                "analysis": analysis,
                "screenshot_placed": screenshot_placed
            }

        # DETECCIÓN DE LIQUIDACIÓN / SETTLEMENT
        elif trade_in_flight and not has_pending:
            trade_in_flight = False
            trade_num = current_trade.get("trade_num", 1)
            entry_epoch = current_trade.get("entry_epoch", time.time() - 60)
            duration_sec = int(time.time() - entry_epoch)

            # Esperar 2 segundos para actualizar saldo en Binomo
            time.sleep(2.0)
            final_status = get_status()
            balance_after = final_status.get("balance", balance) if final_status else balance
            balance_before = current_trade.get("balance_before", balance_after)
            diff = balance_after - balance_before

            screenshot_settled = f"live_25m_trade_{trade_num}_settled.png"
            capture_screenshot(screenshot_settled)

            journal_tail = adb_cmd(["shell", "tail -n 1 /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"])

            if diff > 10.0:
                result = "WIN"
            elif diff < -10.0:
                result = "LOSS"
            else:
                result = "TIE/VOID"

            print(f"\n" + "🏁" * 35)
            print(f"[{now_str}] ¡TRADE #{trade_num} LIQUIDADO TRAS {duration_sec}s!")
            print(f"   Saldo antes:  {balance_before:,.2f} COP")
            print(f"   Saldo después: {balance_after:,.2f} COP")
            print(f"   Diff:         {diff:+,.2f} COP")
            print(f"   Resultado:    {result}")
            print(f"   Journal tail: {journal_tail}")
            print(f"   Captura:      {screenshot_settled}")
            print("🏁" * 35 + "\n")

            current_trade["settle_time"] = now_str
            current_trade["settle_epoch"] = time.time()
            current_trade["duration_sec"] = duration_sec
            current_trade["balance_after"] = balance_after
            current_trade["diff"] = diff
            current_trade["result"] = result
            current_trade["journal_row"] = journal_tail
            current_trade["screenshot_settled"] = screenshot_settled

            trades_recorded.append(dict(current_trade))

        time.sleep(0.8)

    # FINALIZACIÓN DE LA SESIÓN DE 25 MINUTOS
    print("\n" + "=" * 70)
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] SESIÓN DE MONITOREO DE 25 MINUTOS COMPLETADA")
    print("=" * 70)

    final_status = get_status()
    final_balance = final_status.get("balance", 0.0) if final_status else 0.0
    final_wins = final_status.get("risk", {}).get("totalWins", 0) if final_status else 0
    final_losses = final_status.get("risk", {}).get("totalLosses", 0) if final_status else 0

    capture_screenshot("live_25m_session_final.png")

    session_wins = sum(1 for t in trades_recorded if t.get("result") == "WIN")
    session_losses = sum(1 for t in trades_recorded if t.get("result") == "LOSS")
    session_voids = sum(1 for t in trades_recorded if t.get("result") == "TIE/VOID")
    session_net_pnl = final_balance - initial_balance

    report = {
        "session_start": time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(start_time)),
        "session_end": time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(end_time)),
        "duration_minutes": 25,
        "initial_balance": initial_balance,
        "final_balance": final_balance,
        "net_pnl": session_net_pnl,
        "session_trades_count": len(trades_recorded),
        "session_wins": session_wins,
        "session_losses": session_losses,
        "session_voids": session_voids,
        "session_win_rate": (session_wins / max(1, session_wins + session_losses)) * 100.0,
        "trades": trades_recorded
    }

    os.makedirs("loops/journal/live", exist_ok=True)
    with open("loops/journal/live/live_session_25m_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"Resumen de la Sesión:")
    print(f"  - Operaciones ejecutadas: {len(trades_recorded)}")
    print(f"  - Victorias (WIN):        {session_wins}")
    print(f"  - Derrotas (LOSS):        {session_losses}")
    print(f"  - Neutro (VOID):          {session_voids}")
    if (session_wins + session_losses) > 0:
        print(f"  - Win Rate de sesión:     {report['session_win_rate']:.1f}%")
    print(f"  - Saldo inicial:          {initial_balance:,.2f} COP")
    print(f"  - Saldo final:            {final_balance:,.2f} COP")
    print(f"  - P&L Neto sesión:        {session_net_pnl:+,.2f} COP")
    print(f"Reporte detallado guardado en: loops/journal/live/live_session_25m_report.json")
    print("=" * 70)

if __name__ == "__main__":
    main()
