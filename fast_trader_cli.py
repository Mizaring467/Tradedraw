import sys
import time
import json
import urllib.request
import urllib.error
import subprocess
import os

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE_URL = "http://localhost:8080"

def ensure_adb_forward():
    """Configura automáticamente el túnel de puerto TCP 8080 hacia el dispositivo Android."""
    try:
        res = subprocess.run("adb devices", shell=True, capture_output=True, text=True)
        lines = [l for l in res.stdout.splitlines() if "\tdevice" in l]
        if not lines:
            print("[!] No hay dispositivos ADB en estado 'device'.")
            return False
        dev = lines[0].split()[0]
        subprocess.run(f"adb -s {dev} forward tcp:8080 tcp:8080", shell=True, capture_output=True)
        return True
    except Exception as e:
        print(f"[!] Error configurando adb forward: {e}")
        return False

def http_get(path, timeout=2.0):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url)
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = resp.read()
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return resp.status, data, elapsed_ms

def http_post(path, data=b"", timeout=2.0):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url, data=data, method="POST")
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        res_data = resp.read()
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return resp.status, res_data, elapsed_ms

def cmd_status():
    ensure_adb_forward()
    try:
        status, data, ms = http_get("/status")
        obj = json.loads(data.decode("utf-8"))
        print("=" * 60)
        print(f"  TRADEDRAW HTTP BRIDGE - ESTADO EN VIVO ({ms:.1f} ms)")
        print("=" * 60)
        print(f"  Estrategia   : {obj.get('strategy')} | Modo: {obj.get('mode')}")
        print(f"  Accesibilidad: {'ACTIVA [OK]' if obj.get('accessibilityActive') else 'INACTIVA [X]'}")
        print(f"  Saldo Broker : {obj.get('balance')}")
        
        ana = obj.get("analysis", {})
        print(f"\n  [ANALISIS DE GRAFICO]")
        print(f"  Precio Actual: {ana.get('currentPriceY')} px")
        print(f"  Resistencia  : {ana.get('dynamicResistanceY')} px (Techo)")
        print(f"  Soporte      : {ana.get('dynamicSupportY')} px (Piso)")
        print(f"  Poder CALL   : {ana.get('callPower')}% | Poder PUT: {ana.get('putPower')}%")
        print(f"  Tendencia    : {ana.get('trend')} | Lateral/Dojis: {ana.get('isMarketSideways')}")
        print(f"  Racha Velas  : {ana.get('streak')} ({ana.get('candleCount')} velas)")
        
        risk = obj.get("risk", {})
        print(f"\n  [GESTION DE RIESGO]")
        print(f"  Score W/L    : W: {risk.get('totalWins')} | L: {risk.get('totalLosses')} (Winrate: {risk.get('winRate'):.1f}%)")
        print(f"  Martingala   : {risk.get('martingaleStatus')} | Inversion: ${risk.get('investmentAmount')}")
        print(f"  Trade Activo : {risk.get('hasPendingTrade')} | Cooldown: {risk.get('remainingCooldown')}s")

        sig = obj.get("activeSignal")
        if sig:
            print(f"\n  [SENAL ACTIVA] ⚡ {sig.get('action')} - {sig.get('title')}")
            print(f"  Motivo: {sig.get('reason')}")
        print("=" * 60)
    except Exception as e:
        print(f"[!] Error consultando /status: {e}")

def cmd_frame(filename="live_frame.jpg"):
    ensure_adb_forward()
    try:
        status, data, ms = http_get("/frame")
        with open(filename, "wb") as f:
            f.write(data)
        kb = len(data) / 1024.0
        print(f"[OK] Frame guardado en '{filename}' ({kb:.1f} KB) extraido directamente de RAM en {ms:.1f} ms")
    except Exception as e:
        print(f"[!] Error extrayendo /frame: {e}")

def cmd_trade(action="BUY"):
    ensure_adb_forward()
    try:
        status, data, ms = http_post(f"/trade?action={action.upper()}")
        print(f"[OK] Orden {action.upper()} despachada en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error ejecutando orden: {e}")

def cmd_strategy(name):
    ensure_adb_forward()
    try:
        status, data, ms = http_post(f"/strategy?name={name.upper()}")
        print(f"[OK] Estrategia cambiada a {name.upper()} en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error configurando estrategia: {e}")

def cmd_benchmark():
    ensure_adb_forward()
    print("=" * 65)
    print("   BENCHMARK COMPARATIVO: METODO ADB TRADICIONAL vs HTTP BRIDGE")
    print("=" * 65)
    
    # 1. Medir HTTP /status
    http_status_times = []
    for _ in range(10):
        try:
            _, _, ms = http_get("/status")
            http_status_times.append(ms)
        except Exception:
            pass
        time.sleep(0.02)
    avg_http_status = sum(http_status_times) / len(http_status_times) if http_status_times else 0

    # 2. Medir HTTP /frame (RAM directa)
    http_frame_times = []
    for _ in range(5):
        try:
            _, data, ms = http_get("/frame")
            http_frame_times.append(ms)
        except Exception:
            pass
        time.sleep(0.05)
    avg_http_frame = sum(http_frame_times) / len(http_frame_times) if http_frame_times else 0

    # 3. Medir ADB screencap tradicional
    res = subprocess.run("adb devices", shell=True, capture_output=True, text=True)
    lines = [l for l in res.stdout.splitlines() if "\tdevice" in l]
    dev = lines[0].split()[0] if lines else None

    adb_screencap_times = []
    if dev:
        for _ in range(3):
            t0 = time.perf_counter()
            subprocess.run(f"adb -s {dev} shell screencap -p /sdcard/bench_temp.png", shell=True, capture_output=True)
            subprocess.run(f"adb -s {dev} pull /sdcard/bench_temp.png bench_temp.png", shell=True, capture_output=True)
            adb_ms = (time.perf_counter() - t0) * 1000.0
            adb_screencap_times.append(adb_ms)
        if os.path.exists("bench_temp.png"):
            os.remove("bench_temp.png")
    avg_adb_screencap = sum(adb_screencap_times) / len(adb_screencap_times) if adb_screencap_times else 0

    speedup_status = (avg_adb_screencap / avg_http_status) if avg_http_status > 0 else 0
    speedup_frame = (avg_adb_screencap / avg_http_frame) if avg_http_frame > 0 else 0

    print(f"\n  1. Consulta de Estado (JSON):")
    print(f"     - Micro-API HTTP (/status) : {avg_http_status:.1f} ms  [RAPIDO]")
    print(f"     - ADB Dump Tradicional     : {avg_adb_screencap:.1f} ms")
    print(f"     ➔ Aceleracion              : {speedup_status:.1f}x mas rapido")

    print(f"\n  2. Extraccion de Imagen / Frame:")
    print(f"     - Micro-API HTTP (/frame)  : {avg_http_frame:.1f} ms  (Buffer RAM)")
    print(f"     - ADB Screencap + Pull     : {avg_adb_screencap:.1f} ms  (Disco flash)")
    print(f"     ➔ Aceleracion              : {speedup_frame:.1f}x mas rapido")
    print("=" * 65)

def cmd_mode(name):
    ensure_adb_forward()
    try:
        status, data, ms = http_post(f"/mode?name={name.upper()}")
        print(f"[OK] Modo cambiado a {name.upper()} en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error configurando modo: {e}")

def cmd_resume():
    ensure_adb_forward()
    try:
        status, data, ms = http_post("/resume")
        print(f"[OK] Operativa reanudada en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error reanudando: {e}")

def cmd_reset():
    ensure_adb_forward()
    try:
        status, data, ms = http_post("/reset_stats")
        print(f"[OK] Estadisticas reseteadas en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error reseteando: {e}")

def cmd_stats(wins, losses):
    ensure_adb_forward()
    try:
        status, data, ms = http_post(f"/sync_stats?wins={wins}&losses={losses}")
        print(f"[OK] Estadisticas sincronizadas W:{wins} | L:{losses} en {ms:.1f} ms: {data.decode('utf-8')}")
    except Exception as e:
        print(f"[!] Error sincronizando estadisticas: {e}")

def main():
    if len(sys.argv) < 2:
        print("Uso: python fast_trader_cli.py [status | frame | buy | sell | strategy <NAME> | mode <AUTO/SEMI/OFF> | resume | reset | stats <W> <L> | bench]")
        cmd_status()
        return

    cmd = sys.argv[1].lower()
    if cmd == "status":
        cmd_status()
    elif cmd == "frame":
        out = sys.argv[2] if len(sys.argv) > 2 else "live_frame.jpg"
        cmd_frame(out)
    elif cmd == "buy":
        cmd_trade("BUY")
    elif cmd == "sell":
        cmd_trade("SELL")
    elif cmd == "strategy":
        if len(sys.argv) > 2:
            cmd_strategy(sys.argv[2])
        else:
            print("Debes especificar la estrategia: e.g. AUTO_ADAPTIVE, MT_MASTER_COMBO")
    elif cmd == "mode":
        if len(sys.argv) > 2:
            cmd_mode(sys.argv[2])
        else:
            print("Debes especificar el modo: AUTONOMOUS, SEMIAUTOMATIC, DISABLED")
    elif cmd == "resume":
        cmd_resume()
    elif cmd == "reset":
        cmd_reset()
    elif cmd == "stats":
        if len(sys.argv) > 3:
            w = int(sys.argv[2])
            l = int(sys.argv[3])
            cmd_stats(w, l)
        else:
            print("Debes especificar: python fast_trader_cli.py stats <ganadas> <perdidas>")
    elif cmd == "bench":
        cmd_benchmark()
    else:
        print(f"Comando desconocido: {cmd}")

if __name__ == "__main__":
    main()
