import os
import sys
import io
import time
import subprocess
import re
from datetime import datetime

# Force UTF-8 on stdout/stderr to avoid Windows charmap errors
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

APK_PATH = os.path.abspath("app/build/outputs/apk/debug/app-debug.apk")
LOG_DIR = os.path.abspath("loops/journal/live")
SCOREBOARD_FILE = os.path.abspath("loops/scoreboard.md")
DURATION_SECONDS = 30 * 60  # 30 minutes
KILL_SWITCH_EQUITY = 40000000.0  # 40.0M COP
MAX_LOSS_STREAK = 4

def get_connected_device():
    try:
        out = subprocess.check_output(["adb", "devices"], text=True)
        lines = [line.strip() for line in out.splitlines() if line.strip()]
        devices = []
        for line in lines[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
        return devices[0] if devices else None
    except Exception as e:
        print(f"Error checking adb devices: {e}")
        return None

def run_adb(cmd, device=None):
    base = ["adb"]
    if device:
        base.extend(["-s", device])
    base.extend(cmd)
    try:
        res = subprocess.run(base, capture_output=True, text=True, timeout=30)
        return res.stdout.strip(), res.returncode
    except Exception as e:
        return str(e), -1

def kill_switch(device, reason):
    print(f"\n[ALERTA KILL SWITCH]: {reason}")
    print("Deteniendo de inmediato TradeDraw...")
    run_adb(["shell", "am", "force-stop", "com.example.tradedraw"], device)
    print("TradeDraw detenido con exito.")

def main():
    print("=" * 70)
    print("TRADEDRAW - MONITOR DE PRUEBA EN VIVO 30 MINUTOS (FASE 2)")
    print("=" * 70)
    print(f"Fecha/Hora inicio: {datetime.now().isoformat()}")
    print(f"Duracion configurada: {DURATION_SECONDS // 60} minutos")
    print(f"Suelo de Equity Kill Switch: {KILL_SWITCH_EQUITY:,.2f} COP")
    print(f"Racha Maxima Perdidas Kill Switch: {MAX_LOSS_STREAK} LOSS")
    print("-" * 70)

    device = get_connected_device()
    if not device:
        print("Esperando conexion de dispositivo Android (USB o Wi-Fi)...")
        wait_start = time.time()
        while time.time() - wait_start < 600:
            device = get_connected_device()
            if device:
                break
            time.sleep(2)

    if not device:
        print("ERROR: No se detecto ningun dispositivo Android conectado.")
        sys.exit(1)

    print(f"Dispositivo conectado detectado: {device}")

    # 2. Instalar el APK de depuración
    print(f"\nInstalando APK: {APK_PATH}...")
    if not os.path.exists(APK_PATH):
        print(f"APK no encontrado en {APK_PATH}")
        sys.exit(1)

    stdout, ret = run_adb(["install", "-r", APK_PATH], device)
    print(f"Resultado de instalacion: {stdout}")
    if ret != 0:
        print(f"Error al instalar APK: {stdout}")
        sys.exit(1)
    print("APK instalado correctamente.")

    # 3. Asegurar pantalla encendida
    run_adb(["shell", "input", "keyevent", "KEYCODE_WAKEUP"], device)
    run_adb(["shell", "wm", "dismiss-keyguard"], device)

    # 4. Limpiar logcat previo
    run_adb(["logcat", "-c"], device)

    # 5. Iniciar o verificar TradeDraw
    print("\nIniciando MainActivity de TradeDraw...")
    run_adb(["shell", "am", "start", "-n", "com.example.tradedraw/.MainActivity"], device)
    time.sleep(3)

    # 6. Lanzar proceso de logcat en background
    os.makedirs(LOG_DIR, exist_ok=True)
    session_ts = int(time.time())
    session_log_path = os.path.join(LOG_DIR, f"live_session_{session_ts}.log")
    print(f"Guardando registro detallado en: {session_log_path}")

    logcat_cmd = [
        "adb", "-s", device, "logcat", "-v", "time",
        "-s", "TradeDraw:D", "TradingEngine:D", "RiskManager:D",
        "SyntheticCandleEngine:D", "AdaptiveLearningEngine:D", "BinomoWebSocketClient:D"
    ]

    log_file = open(session_log_path, "w", encoding="utf-8")
    logcat_proc = subprocess.Popen(logcat_cmd, stdout=log_file, stderr=subprocess.STDOUT, text=True)

    print("\n" + "=" * 70)
    print(f"MONITOREO ACTIVO DURANTE 30 MINUTOS ({DURATION_SECONDS} segundos)...")
    print("=" * 70)

    start_time = time.time()
    last_report_time = start_time
    trades_recorded = []
    consecutive_losses = 0
    void_count = 0
    last_known_balance = None

    try:
        while time.time() - start_time < DURATION_SECONDS:
            elapsed = int(time.time() - start_time)
            remaining = DURATION_SECONDS - elapsed

            # Revisar logcat generado
            log_file.flush()
            if os.path.exists(session_log_path):
                with open(session_log_path, "r", encoding="utf-8", errors="ignore") as rf:
                    lines = rf.readlines()
                
                # Parsear eventos recientes
                for line in lines[-50:]:
                    # Balance check (debe ser el balance total de la cuenta, >= 30,000,000 COP)
                    balance_match = re.search(r"(?:Balance|Saldo|equity)[^\d]*([\d.,]+)", line, re.IGNORECASE)
                    if balance_match and "ingreso" not in line.lower():
                        raw_val = balance_match.group(1).replace(".", "").replace(",", ".")
                        try:
                            val = float(raw_val)
                            if val > 30000000.0:  # Sensible balance in COP (30M+)
                                last_known_balance = val
                                if val < KILL_SWITCH_EQUITY:
                                    kill_switch(device, f"Equity {val:,.2f} COP cayo bajo suelo seguro ({KILL_SWITCH_EQUITY:,.2f} COP)")
                                    return
                        except:
                            pass

                    # Trade win/loss
                    if "RESULTADO OPERACION: WIN" in line or "Trade WIN" in line:
                        if line not in trades_recorded:
                            trades_recorded.append(line)
                            consecutive_losses = 0
                            print(f"[{elapsed}s] WIN DETECTADO!")
                    elif "RESULTADO OPERACION: LOSS" in line or "Trade LOSS" in line:
                        if line not in trades_recorded:
                            trades_recorded.append(line)
                            consecutive_losses += 1
                            print(f"[{elapsed}s] LOSS DETECTADO! Racha: {consecutive_losses}")
                            if consecutive_losses >= MAX_LOSS_STREAK:
                                kill_switch(device, f"Racha critica de perdidas alcanzada ({consecutive_losses} LOSS)")
                                return
                    elif "VOID" in line or "ORDEN NO PROCESADA" in line:
                        if line not in trades_recorded:
                            trades_recorded.append(line)
                            void_count += 1
                            print(f"[{elapsed}s] VOID / ORDEN NO PROCESADA (Contador: {void_count})")
                            if void_count >= 3:
                                kill_switch(device, f"3 VOID consecutivos detectados (Broker no responde a clics)")
                                return

            # Reporte periódico cada 60s
            if time.time() - last_report_time >= 60:
                last_report_time = time.time()
                print(f"Progreso: {elapsed // 60}m / 30m | Restante: {remaining // 60}m | Eventos: {len(trades_recorded)} | Racha L: {consecutive_losses} | Ultimo Saldo: {last_known_balance}")

            time.sleep(5)

    except KeyboardInterrupt:
        print("\nMonitoreo interrumpido por el usuario.")
    finally:
        logcat_proc.terminate()
        log_file.close()

    print("\n" + "=" * 70)
    print("SESION DE 30 MINUTOS COMPLETADA CON EXITO")
    print(f"Total eventos registrados: {len(trades_recorded)}")
    print(f"Racha final de perdidas: {consecutive_losses}")
    print(f"Ultimo saldo observado: {last_known_balance}")
    print(f"Registro guardado en: {session_log_path}")
    print("=" * 70)

if __name__ == "__main__":
    main()
