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

def adb_cmd(args):
    cmd = ["adb", "-s", SERIAL] + args
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def get_status():
    try:
        req = urllib.request.Request(STATUS_URL, headers={"User-Agent": "AuditScript"})
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            return json.loads(resp.read().decode())
    except Exception:
        subprocess.run(["adb", "-s", SERIAL, "forward", "tcp:8080", "tcp:8080"], capture_output=True)
        return None

def main():
    target_cycles = 2  # Capturar 2 ciclos nuevos completos adicionales
    captured_cycles = []
    
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Iniciando Live Cycle Auditor para {target_cycles} ciclos en {SERIAL}...")
    subprocess.run(["adb", "-s", SERIAL, "forward", "tcp:8080", "tcp:8080"], capture_output=True)

    trade_in_progress = False
    current_cycle = {}
    cycle_counter = 0

    start_monitor_time = time.time()
    max_duration_seconds = 1800  # 30 minutos máx

    while cycle_counter < target_cycles and (time.time() - start_monitor_time < max_duration_seconds):
        status = get_status()
        now_ts = time.time()
        now_str = time.strftime('%Y-%m-%d %H:%M:%S')

        if not status:
            time.sleep(0.5)
            continue

        risk = status.get("risk", {})
        has_pending = risk.get("hasPendingTrade", False)
        balance = status.get("balance", 0.0)
        analysis = status.get("analysis", {})

        if not trade_in_progress:
            if has_pending:
                # ¡ORDEN DETECTADA!
                trade_in_progress = True
                cycle_counter += 1
                
                # Obtener logcat reciente para extraer datos exactos de la señal y clic
                time.sleep(1.2)  # permitir que el log se asiente
                recent_logs = adb_cmd(["logcat", "-d", "-v", "time", "-s", "TradingEngine", "TradeDraw", "AutoTradeAccessibilityService", "RiskManager"])
                
                screenshot_placed = f"audit_cycle_{cycle_counter}_placed.png"
                adb_cmd(["shell", f"screencap -p /sdcard/{screenshot_placed}"])
                adb_cmd(["pull", f"/sdcard/{screenshot_placed}", screenshot_placed])

                current_cycle = {
                    "cycle_num": cycle_counter,
                    "detect_timestamp": now_str,
                    "detect_epoch_ms": int(now_ts * 1000),
                    "balance_before": balance,
                    "analysis": analysis,
                    "screenshot_placed": screenshot_placed,
                    "logs_at_trigger": recent_logs[-2500:] if recent_logs else ""
                }
                print(f"\n[EVENTO #{cycle_counter}] ORDEN DETECTADA a las {now_str}")
                print(f"   Saldo al inicio: {balance}")
                print(f"   Analisis: {analysis.get('trend')} | Call: {analysis.get('callPower')}% | Put: {analysis.get('putPower')}%")
                print(f"   Captura guardada: {screenshot_placed}")
        else:
            if not has_pending:
                # ¡ORDEN LIQUIDADA!
                trade_in_progress = False
                time.sleep(1.5)
                
                screenshot_settled = f"audit_cycle_{cycle_counter}_settled.png"
                adb_cmd(["shell", f"screencap -p /sdcard/{screenshot_settled}"])
                adb_cmd(["pull", f"/sdcard/{screenshot_settled}", screenshot_settled])

                journal_tail = adb_cmd(["shell", "tail -n 2 /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"])
                recent_logs_settle = adb_cmd(["logcat", "-d", "-v", "time", "-s", "TradingEngine", "TradeDraw", "AutoTradeAccessibilityService", "RiskManager"])

                current_cycle["settle_timestamp"] = now_str
                current_cycle["settle_epoch_ms"] = int(now_ts * 1000)
                current_cycle["balance_after"] = balance
                current_cycle["screenshot_settled"] = screenshot_settled
                current_cycle["journal_row"] = journal_tail
                current_cycle["logs_at_settle"] = recent_logs_settle[-2500:] if recent_logs_settle else ""

                captured_cycles.append(current_cycle)
                print(f"[EVENTO #{cycle_counter}] SETTLEMENT DETECTADO a las {now_str}")
                print(f"   Saldo final: {balance}")
                print(f"   Fila Journal:\n   {journal_tail}")
                print(f"   Captura liquidacion guardada: {screenshot_settled}\n")

                # Pausa antes de esperar la siguiente señal
                time.sleep(5.0)

        time.sleep(0.4)

    # Guardar resumen en JSON
    with open("live_audit_cycles_report.json", "w", encoding="utf-8") as f:
        json.dump(captured_cycles, f, indent=2, ensure_ascii=False)

    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Auditoria concluida. Se capturaron {len(captured_cycles)} ciclos completos.")

if __name__ == "__main__":
    main()
