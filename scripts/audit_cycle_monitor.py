import subprocess
import time
import json
import urllib.request
import re
import sys
import os

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
    except Exception as e:
        # Re-forward port if needed
        subprocess.run(["adb", "-s", SERIAL, "forward", "tcp:8080", "tcp:8080"], capture_output=True)
        return None

def main():
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Iniciando monitor de ciclo de auditoria en dispositivo {SERIAL}...")
    subprocess.run(["adb", "-s", SERIAL, "forward", "tcp:8080", "tcp:8080"], capture_output=True)
    
    # Iniciar lectura de logcat en subproceso
    logcat_proc = subprocess.Popen(
        ["adb", "-s", SERIAL, "logcat", "-v", "time", "-T", "1",
         "TradingEngine:V", "SyntheticCandleEngine:V", "RiskManager:V",
         "TradeDraw:V", "AutoTradeAccessibilityService:V", "*:S"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        errors="replace"
    )

    collected_logs = []
    
    trade_active = False
    trade_info = {}
    settlement_info = {}
    
    print("Esperando aparicion de proxima senal y ejecucion de trade...")

    start_wait = time.time()
    
    while True:
        # Leer líneas pendientes de logcat de forma no bloqueante
        while True:
            import select
            if sys.platform != "win32":
                r, _, _ = select.select([logcat_proc.stdout], [], [], 0)
                if not r: break
                line = logcat_proc.stdout.readline()
                if not line: break
                collected_logs.append(line.strip())
            else:
                # En Windows, leemos tras cada ciclo si logcat avanza o verificamos via logcat -d si se necesita
                break

        status = get_status()
        now_str = time.strftime('%Y-%m-%d %H:%M:%S')

        if status:
            has_pending = status.get("risk", {}).get("hasPendingTrade", False)
            balance = status.get("balance", 0.0)
            analysis = status.get("analysis", {})
            active_sig = status.get("activeSignal")

            if not trade_active:
                if has_pending:
                    # ¡TRADE RECIEN DETECTADO!
                    trade_active = True
                    trade_info["detect_time"] = now_str
                    trade_info["status_snapshot"] = status
                    print(f"\n⚡ [{now_str}] ¡NUEVO TRADE DETECTADO EN EJECUCION!")
                    print(f"   Saldo antes: {balance}")
                    print(f"   Analisis: {analysis.get('trend')} | Prob: {analysis.get('callPower')}% CALL / {analysis.get('putPower')}% PUT")
                    
                    # Esperar 2 segundos para capturar pantalla con orden activa en Binomo
                    time.sleep(2.0)
                    adb_cmd(["shell", "screencap -p /sdcard/audit_trade_placed.png"])
                    adb_cmd(["pull", "/sdcard/audit_trade_placed.png", "audit_trade_placed.png"])
                    print("   Captura audit_trade_placed.png guardada con éxito.")
            else:
                # Esperando liquidación
                if not has_pending:
                    # ¡TRADE LIQUIDADO!
                    settlement_info["settle_time"] = now_str
                    settlement_info["final_status"] = status
                    print(f"\n🏁 [{now_str}] ¡TRADE LIQUIDADO!")
                    print(f"   Saldo despues: {balance}")
                    
                    # Esperar 2 segundos y capturar pantalla de resultado
                    time.sleep(2.0)
                    adb_cmd(["shell", "screencap -p /sdcard/audit_trade_settled.png"])
                    adb_cmd(["pull", "/sdcard/audit_trade_settled.png", "audit_trade_settled.png"])
                    print("   Captura audit_trade_settled.png guardada con éxito.")
                    break
        
        # Timeout de seguridad si pasan más de 12 minutos
        if time.time() - start_wait > 720:
            print("Timeout de espera (12 min sin ejecucion).")
            break

        time.sleep(0.5)

    logcat_proc.terminate()

    # Volcar ultimos logs detallados de la ejecucion
    recent_logs = adb_cmd(["logcat", "-d", "-v", "time", "-s", "TradingEngine", "SyntheticCandleEngine", "RiskManager", "TradeDraw", "AutoTradeAccessibilityService"])
    with open("audit_logs_full.txt", "w", encoding="utf-8") as f:
        f.write(recent_logs)

    # Volcar últimas 5 líneas del journal
    journal_tail = adb_cmd(["shell", "tail -n 5 /sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"])
    with open("audit_journal_tail.txt", "w", encoding="utf-8") as f:
        f.write(journal_tail)

    result_data = {
        "trade_info": trade_info,
        "settlement_info": settlement_info,
        "journal_tail": journal_tail
    }
    with open("audit_result.json", "w", encoding="utf-8") as f:
        json.dump(result_data, f, indent=2)

    print("\nAuditoria completada. Datos guardados en audit_result.json, audit_logs_full.txt y audit_journal_tail.txt.")

if __name__ == "__main__":
    main()
