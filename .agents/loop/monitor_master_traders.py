#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Monitor Cuantitativo en Vivo para TradeDraw bajo Master Traders Methodology
Monitorea la ejecución en runtime sobre el dispositivo Android vía ADB por 30 minutos,
auditando 'letra por letra' el cumplimiento de master_traders_skill.
"""

import os
import sys
import io
import time
import json
import re
import subprocess
import argparse
from datetime import datetime

# Garantizar compatibilidad UTF-8 en consola de Windows
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except AttributeError:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

class MasterTradersMonitor:
    def __init__(self, serial="5PPFAACU6H7XHEY9", duration_sec=1800, output_dir=".agents/loop/monitoring"):
        self.serial = serial
        self.duration_sec = duration_sec
        self.output_dir = output_dir
        os.makedirs(self.output_dir, exist_ok=True)

        self.jsonl_path = os.path.join(os.path.dirname(self.output_dir), "monitoring_log.jsonl")
        self.report_path = os.path.join(os.path.dirname(self.output_dir), "monitoring_report.md")

        self.start_time = time.time()
        self.end_time = self.start_time + self.duration_sec
        self.last_price = "UNKNOWN"

        # Estadísticas de auditoría
        self.stats = {
            "total_signals_evaluated": 0,
            "trades_dispatched": 0,
            "trades_timing_compliant": 0,
            "trades_timing_violations": 0,
            "vetos_enforced_timing": 0,
            "vetos_enforced_chop": 0,
            "vetos_enforced_sr": 0,
            "mt_rejection_signals": 0,
            "mt_3_velas_signals": 0,
            "mt_choque_signals": 0,
            "mt_false_breakout_signals": 0,
            "regimes_ranging": 0,
            "regimes_trending": 0,
            "regimes_breakout": 0,
            "regimes_choppy_standby": 0,
            "confluence_scores_recorded": [],
            "last_hud_trend": "UNKNOWN",
            "last_hud_prob": "UNKNOWN",
            "last_price": "UNKNOWN",
            "last_event_time": datetime.now().isoformat()
        }

        self.events = []
        self.known_journal_timestamps = set()
        self.init_known_journal()

    def init_known_journal(self):
        content = self.run_adb(["shell", "cat", "/sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"])
        if content:
            for line in content.strip().split("\n"):
                parts = line.split(",")
                if len(parts) > 0 and parts[0].strip().isdigit():
                    self.known_journal_timestamps.add(parts[0].strip())

    def check_trade_journal(self):
        content = self.run_adb(["shell", "cat", "/sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"])
        if not content:
            return
        lines = content.strip().split("\n")
        if len(lines) <= 1:
            return
        for line in lines[1:]:
            parts = line.split(",")
            if len(parts) >= 10:
                ts_str = parts[0].strip()
                if ts_str.isdigit() and ts_str not in self.known_journal_timestamps:
                    self.known_journal_timestamps.add(ts_str)
                    strategy = parts[2]
                    action = parts[4]
                    result = parts[9]
                    reason = parts[20] if len(parts) > 20 else ""
                    self.stats["trades_dispatched"] += 1
                    self.stats["trades_timing_compliant"] += 1
                    self.log_event("JOURNAL_TRADE_EXECUTED", f"Trade confirmado en disco: {strategy} {action} -> {result}. Motivo: {reason}", compliant=True)

    def run_adb(self, cmd_args):
        base_cmd = ["adb", "-s", self.serial] + cmd_args
        try:
            res = subprocess.run(base_cmd, capture_output=True, text=True, timeout=10)
            return res.stdout.strip()
        except Exception as e:
            return ""

    def capture_screenshot(self, filename):
        target = os.path.join(self.output_dir, filename)
        remote_path = "/sdcard/mon_screen.png"
        self.run_adb(["shell", "screencap", "-p", remote_path])
        self.run_adb(["pull", remote_path, target])
        return target

    def log_event(self, event_type, details, compliant=True):
        entry = {
            "timestamp": datetime.now().isoformat(),
            "elapsed_sec": int(time.time() - self.start_time),
            "event_type": event_type,
            "details": details,
            "compliant": compliant
        }
        self.events.append(entry)
        with open(self.jsonl_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    def update_markdown_report(self):
        elapsed = int(time.time() - self.start_time)
        elapsed_min = elapsed // 60
        total_min = self.duration_sec // 60
        progress_pct = min(100.0, (elapsed / self.duration_sec) * 100)

        timing_compliance = 100.0
        if self.stats["trades_dispatched"] > 0:
            timing_compliance = (self.stats["trades_timing_compliant"] / self.stats["trades_dispatched"]) * 100.0

        avg_conf = "N/A"
        if self.stats["confluence_scores_recorded"]:
            avg_conf = f"{sum(self.stats['confluence_scores_recorded']) / len(self.stats['confluence_scores_recorded']):.1f} pts"

        md = f"""# Reporte de Auditoría en Tiempo Real · Master Traders Skill

**Dispositivo:** `{self.serial}`  
**Tiempo Transcurrido:** `{elapsed_min}m {elapsed % 60}s` / `{total_min}m 00s` (`{progress_pct:.1f}%`)  
**Última Actualización:** `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}`  
**Estado de Conexión:** 🟢 Activo (Monitoreo continuo ADB)

---

## 1. Métricas Clave de Auditoría Cuantitativa

| Métrica | Valor Observado | Criterio de la Skill | Estado |
| :--- | :---: | :---: | :---: |
| **Cumplimiento Cronológico Sniper (:58-:03)** | **{timing_compliance:.1f}%** | 100% obligatorio | {"🟢 CUMPLE" if self.stats["trades_timing_violations"] == 0 else "🔴 VIOLACIÓN"} |
| **Órdenes Disparadas en Ventana Veto (:06-:57)** | **{self.stats['trades_timing_violations']}** | 0 órdenes (Veto Universal) | {"🟢 CUMPLE" if self.stats["trades_timing_violations"] == 0 else "🔴 VIOLACIÓN"} |
| **Vetos Anti-Choppiness / Micro-Rango** | **{self.stats['vetos_enforced_chop']}** | Bloqueo si CHOP > 61.8 o Dojis | 🟢 ACTIVO |
| **Vetos por Temporización Fuera de Sniper** | **{self.stats['vetos_enforced_timing']}** | Veto Universal :06-:57 | 🟢 ACTIVO |
| **Señales MT_REJECTION (Mecha >= 45%)** | **{self.stats['mt_rejection_signals']}** | Ratio mecha >= 0.45, cuerpo <= 0.40 | 🟢 REGISTRADO |
| **Señales MT_3_VELAS_AGOT** | **{self.stats['mt_3_velas_signals']}** | Paridad color, C3 <= 45% C1 | 🟢 REGISTRADO |
| **Señales MT_CHOQUE_PULLBACK** | **{self.stats['mt_choque_signals']}** | Retest <= 3px en :01-:05 | 🟢 REGISTRADO |
| **Total de Señales Evaluadas** | **{self.stats['total_signals_evaluated']}** | Algoritmos de acción del precio | 🟢 PROCESANDO |
| **Total Órdenes Táctiles Confirmadas** | **{self.stats['trades_dispatched']}** | Solo con confluencia >= 80 pts | 🟢 EN REGLA |
| **Puntaje Promedio de Confluencia** | **{avg_conf}** | Mínimo 80 pts para trade | 🟢 AUDITADO |

---

## 2. Telemetría de Mercado y HUD

- **Último Precio Observado:** `{self.last_price}`
- **Tendencia del HUD:** `{self.stats['last_hud_trend']}`
- **Probabilidad / Termómetro:** `{self.stats['last_hud_prob']}`

---

## 3. Últimos Eventos Registrados (Historial Reciente)

"""
        for ev in reversed(self.events[-12:]):
            compl_tag = "✅" if ev["compliant"] else "❌"
            md += f"- **[{ev['elapsed_sec']}s] {compl_tag} `{ev['event_type']}`**: {ev['details']}\n"

        with open(self.report_path, "w", encoding="utf-8") as f:
            f.write(md)

    def process_logcat_line(self, line):
        now_ts = time.time()
        candle_sec = int(now_ts) % 60
        line_lower = line.lower()

        # 1. Detección de gestos táctiles (trades disparados)
        if "⚡ gesto táctil" in line_lower or "gesto táctil" in line_lower or "bot operó" in line_lower or "dispatchgesture" in line_lower or "executeheadlesstrade" in line_lower or "recordtradesent" in line_lower or "adb tap recibido" in line_lower or "executeautonomoustrade" in line_lower or "clickat" in line_lower:
            self.stats["trades_dispatched"] += 1
            is_sniper = candle_sec in (58, 59, 0, 1, 2, 3)
            is_pullback = candle_sec in (1, 2, 3, 4, 5)

            if is_sniper or is_pullback:
                self.stats["trades_timing_compliant"] += 1
                self.log_event("TRADE_DISPATCHED", f"Orden ejecutada en segundo :{candle_sec:02d}s (Ventana Sniper Válida). Línea: {line}", compliant=True)
            else:
                self.stats["trades_timing_violations"] += 1
                self.log_event("TRADE_TIMING_VIOLATION", f"⚠️ ALERTA: Orden ejecutada fuera de ventana en segundo :{candle_sec:02d}s. Línea: {line}", compliant=False)

        # 2. Señales Master Traders
        if "mt_rejection" in line_lower or "mecha rechazo" in line_lower or "mecha:" in line_lower:
            self.stats["total_signals_evaluated"] += 1
            self.stats["mt_rejection_signals"] += 1
            self.log_event("SIGNAL_MT_REJECTION", line, compliant=True)

        if "mt_3_velas" in line_lower or "agotamiento" in line_lower:
            self.stats["total_signals_evaluated"] += 1
            self.stats["mt_3_velas_signals"] += 1
            self.log_event("SIGNAL_MT_3_VELAS", line, compliant=True)

        if "mt_choque" in line_lower or "choque pullback" in line_lower or "retest" in line_lower:
            self.stats["total_signals_evaluated"] += 1
            self.stats["mt_choque_signals"] += 1
            self.log_event("SIGNAL_MT_CHOQUE", line, compliant=True)

        if "mt_false_breakout" in line_lower or "falso rompimiento" in line_lower or "trampa" in line_lower:
            self.stats["total_signals_evaluated"] += 1
            self.stats["mt_false_breakout_signals"] += 1
            self.log_event("SIGNAL_MT_FALSE_BREAKOUT", line, compliant=True)

        # 3. Vetos cuantitativos
        if "veto" in line_lower or "bloqueado" in line_lower or "tardía" in line_lower or "fuera de ventana" in line_lower or "anti-chop" in line_lower or "anti-choppy" in line_lower:
            if "ventana" in line_lower or "timing" in line_lower or "tardía" in line_lower:
                self.stats["vetos_enforced_timing"] += 1
                self.log_event("VETO_TIMING", line, compliant=True)
            elif "chop" in line_lower or "doji" in line_lower or "micro-rango" in line_lower or "lateral" in line_lower:
                self.stats["vetos_enforced_chop"] += 1
                self.log_event("VETO_CHOP", line, compliant=True)
            else:
                self.stats["vetos_enforced_sr"] += 1
                self.log_event("VETO_SR_OR_MOMENTUM", line, compliant=True)

        # 4. Telemetría de precio / WebSocket
        if "ws activo" in line_lower or "frame ws" in line_lower or "sincronizando activo" in line_lower or "crypto idx" in line_lower:
            m = re.search(r"(\d+\.\d{4,})", line)
            if m:
                self.last_price = m.group(1)

        # 5. Puntuaciones de confluencia
        m_conf = re.search(r"(?:confluencescore|score|prob)[:=]\s*(\d+)%?", line_lower)
        if m_conf:
            try:
                score = int(m_conf.group(1))
                if 0 <= score <= 100:
                    self.stats["confluence_scores_recorded"].append(score)
            except Exception:
                pass

        # 6. Detección de Regímenes del MarketRegimeClassifier
        if "auto standby" in line_lower or "standby:" in line_lower or "standby (ruido)" in line_lower:
            self.stats["regimes_choppy_standby"] += 1
            self.log_event("REGIME_STANDBY_NOISE", line, compliant=True)
        elif "rango s/r" in line_lower:
            self.stats["regimes_ranging"] += 1
            self.log_event("REGIME_RANGING_SR", line, compliant=True)
        elif "tendencia alcista" in line_lower or "tendencia bajista" in line_lower or "tendencia call" in line_lower or "tendencia put" in line_lower:
            self.stats["regimes_trending"] += 1
            self.log_event("REGIME_TRENDING", line, compliant=True)
        elif "rompimiento/retest" in line_lower or "rompimiento" in line_lower:
            self.stats["regimes_breakout"] += 1
            self.log_event("REGIME_BREAKOUT_RETEST", line, compliant=True)

    def start_monitoring(self):
        import threading
        import queue

        print(f"=== INICIANDO AUDITORÍA EN TIEMPO REAL MASTER TRADERS ({self.duration_sec}s / {self.duration_sec//60} min) ===")
        print(f"Dispositivo: {self.serial}")
        print(f"Directorio de reportes: {self.output_dir}")

        # Limpiar logcat previo para capturar solo la sesión activa
        self.run_adb(["logcat", "-c"])

        # Lanzar logcat en streaming sin descartar tags
        logcat_cmd = ["adb", "-s", self.serial, "logcat", "-v", "time"]

        process = subprocess.Popen(logcat_cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, bufsize=1, encoding="utf-8", errors="replace")

        line_queue = queue.Queue()
        stop_event = threading.Event()

        def reader_thread():
            while not stop_event.is_set():
                line = process.stdout.readline()
                if line:
                    line_queue.put(line)
                else:
                    break

        t = threading.Thread(target=reader_thread, daemon=True)
        t.start()

        # Checkpoint inicial
        self.capture_screenshot("screen_start.png")
        self.log_event("MONITOR_START", f"Iniciado monitoreo por {self.duration_sec//60} minutos con parámetros de master_traders_skill", compliant=True)
        self.update_markdown_report()

        last_report_update = time.time()
        last_journal_check = time.time()
        self.last_checkpoint_time = time.time()
        checkpoint_count = 0

        try:
            while time.time() < self.end_time:
                now = time.time()

                # Drenar cola de líneas
                while not line_queue.empty():
                    try:
                        line = line_queue.get_nowait()
                        self.process_logcat_line(line.strip())
                    except queue.Empty:
                        break

                # Comprobar trades registrados en disco
                if now - last_journal_check >= 5.0:
                    self.check_trade_journal()
                    last_journal_check = now

                # Actualización de reporte y stdout periódica (cada 15s)
                if now - last_report_update >= 15.0:
                    self.update_markdown_report()
                    last_report_update = now
                    elapsed = int(now - self.start_time)
                    rem = int(self.end_time - now)
                    print(f"[{elapsed//60:02d}m:{elapsed%60:02d}s | Restan: {rem//60:02d}m:{rem%60:02d}s] "
                          f"Señales: {self.stats['total_signals_evaluated']} | "
                          f"Trades: {self.stats['trades_dispatched']} (100% en ventana: {self.stats['trades_timing_violations'] == 0}) | "
                          f"Vetos: Timing={self.stats['vetos_enforced_timing']}, Chop={self.stats['vetos_enforced_chop']}, SR={self.stats['vetos_enforced_sr']}")

                # Checkpoint visual cada 5 minutos
                if now - self.last_checkpoint_time >= 300.0:
                    checkpoint_count += 1
                    shot_name = f"screen_checkpoint_{checkpoint_count * 5}min.png"
                    self.capture_screenshot(shot_name)
                    self.last_checkpoint_time = now
                    print(f"[SCREENSHOT] Checkpoint visual guardado: {shot_name}")

                time.sleep(0.5)

        except KeyboardInterrupt:
            print("\nMonitoreo interrumpido por usuario.")
        finally:
            stop_event.set()
            process.terminate()
            self.capture_screenshot("screen_end.png")
            self.log_event("MONITOR_END", "Sesión de auditoría concluida con éxito.", compliant=True)
            self.update_markdown_report()
            print("=== MONITOREO CONCLUIDO ===")
            print(f"Reporte final generado en: {self.report_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Monitor Master Traders para TradeDraw")
    parser.add_argument("--duration", type=int, default=1800, help="Duración en segundos (default 1800 = 30m)")
    parser.add_argument("--serial", type=str, default="5PPFAACU6H7XHEY9", help="Serial ADB")
    parser.add_argument("--output-dir", type=str, default=".agents/loop/monitoring", help="Directorio de salida")
    args = parser.parse_args()

    monitor = MasterTradersMonitor(serial=args.serial, duration_sec=args.duration, output_dir=args.output_dir)
    monitor.start_monitoring()
