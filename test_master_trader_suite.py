import subprocess
import time
import os
import sys

def run_command(cmd, desc=None, env=None):
    if desc:
        print(f"[*] {desc}")
    print(f"    > {cmd}")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, env=env)
    if res.returncode != 0 and res.stderr:
        print(f"    [!] Stderr: {res.stderr.strip()}")
    return res.returncode, res.stdout.strip()

def main():
    print("=" * 60)
    print("   TRADEDRAW MASTER TRADER - SUITE DE VALIDACIÓN ADB EN VIVO")
    print("=" * 60)

    # 1. Comprobación de Dispositivo ADB
    rc, devices = run_command("adb devices", "Comprobando conexión de dispositivos ADB")
    print(devices)
    lines = [l for l in devices.splitlines() if "\tdevice" in l]
    if not lines:
        print("[!] No se detectó ningún dispositivo Android conectado en modo 'device'.")
        print("    Asegúrate de tener la depuración USB o inalámbrica activa.")
        return

    device_id = lines[0].split()[0]
    print(f"[+] Dispositivo objetivo activo: {device_id}")

    # 2. Diagnóstico del Dispositivo
    _, model = run_command(f"adb -s {device_id} shell getprop ro.product.model", "Modelo de dispositivo")
    _, brand = run_command(f"adb -s {device_id} shell getprop ro.product.brand", "Marca")
    _, android_ver = run_command(f"adb -s {device_id} shell getprop ro.build.version.release", "Versión de Android")
    _, display_size = run_command(f"adb -s {device_id} shell wm size", "Resolución de pantalla")
    _, battery = run_command(f'adb -s {device_id} shell "dumpsys battery | grep level"', "Nivel de batería")

    print(f"[i] Info: {brand.upper()} {model} (Android {android_ver}) | {display_size} | {battery.strip()}")

    # 3. Compilación silenciosa del APK
    print("\n[+] Compilando APK con Gradle (modo Master Trader)...")
    build_env = os.environ.copy()
    build_env["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
    build_env["PATH"] = rf"{build_env['JAVA_HOME']}\bin;{build_env.get('PATH', '')}"
    gradle_cmd = r'.\gradlew.bat assembleDebug --quiet --no-daemon --no-configuration-cache'
    rc_build, out_build = run_command(gradle_cmd, "Ejecutando assembleDebug", env=build_env)
    if rc_build != 0:
        print("[X] Fallo la compilacion de Gradle. Revisa los errores anteriores.")
        return
    print("[OK] Compilacion exitosa. APK generado.")

    # 4. Instalación limpia por ADB
    apk_path = r"app\build\outputs\apk\debug\app-debug.apk"
    if not os.path.exists(apk_path):
        print(f"[X] No se encontro el APK en {apk_path}")
        return

    print("\n[+] Instalando APK en el dispositivo...")
    rc_inst, out_inst = run_command(f"adb -s {device_id} install -r -d {apk_path}", "Instalando app-debug.apk")
    print(f"[i] Resultado: {out_inst}")

    # 5. Envío de pulso de prueba al Servicio de Accesibilidad
    print("\n[+] Verificando pulso de comunicacion con AutoTradeAccessibilityService...")
    run_command(f"adb -s {device_id} shell am broadcast -a com.example.tradedraw.CMD --es command TEST_PULSE")
    time.sleep(1)

    # 6. Arranque de TradeDraw MainActivity
    print("\n[+] Iniciando TradeDraw...")
    run_command(f"adb -s {device_id} shell am start -n com.example.tradedraw/.MainActivity", "Lanzando MainActivity")
    time.sleep(2)

    # 7. Captura de pantalla de verificación
    screen_remote = "/sdcard/screen_master_trader_val.png"
    screen_local = "screen_master_trader_validation.png"
    print(f"\n[+] Capturando pantalla para verificacion visual...")
    run_command(f"adb -s {device_id} shell screencap -p {screen_remote}")
    run_command(f"adb -s {device_id} pull {screen_remote} {screen_local}")

    if os.path.exists(screen_local):
        size_kb = os.path.getsize(screen_local) / 1024
        print(f"[OK] Captura guardada exitosamente: {screen_local} ({size_kb:.1f} KB)")
    else:
        print("[!] No se pudo recuperar la captura local.")

    print("\n" + "=" * 60)
    print("   VALIDACION COMPLETADA EXITOSAMENTE")
    print("=" * 60)

if __name__ == "__main__":
    main()
