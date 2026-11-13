#!/usr/bin/env python3
"""
update_progress.py — progreso en vivo de R31 (FASE 1).

Lee el trade_journal.csv (29 columnas, header de TradeJournalLogger.kt:22,
decision_id/trade_id al final) y el estado de infraestructura (watchdog state
+ events + session_start) y escribe loops/r31_live/R31_PROGRESS.json con el
schema EXACTO:

  {"target":50,"resolved":N,"wins":N,"losses":N,"ties":N,"pending":N,
   "runtime_minutes":N,"disconnects":N,"recoveries_ok":N,"recoveries_failed":N}

Fuentes del CSV (ambas soportadas, en este orden):
  1. --csv RUTA (CSV provisto como argumento, p.ej. pull hecho por coordinador)
  2. --device (lectura remota vía `adb shell cat` del path canónico)
  3. default: R31_JOURNAL_CSV si está exportado; si no, intenta adb.

Fuentes de infraestructura:
  --watchdog-state RUTA  (watchdog_state.json: counters.reconexiones_* etc.)
  --events RUTA          (watchdog_events.jsonl / incident_metrics.jsonl)
  --session-start RUTA   (session_start.json con start_ts_utc ISO8601)

Idempotente: N ejecuciones sobre los mismos inputs → mismo output.
Solo lectura de todas las fuentes.
"""
import argparse
import csv
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

TARGET = 50
DEFAULT_DEVICE_CSV = (
    "/sdcard/Android/data/com.example.tradedraw/files/"
    "TradeDraw_Audits/trade_journal.csv"
)
SESSION_START = Path(__file__).resolve().parent / "session_start.json"
OUTPUT = Path(__file__).resolve().parent / "R31_PROGRESS.json"


def valid_row(row: dict) -> bool:
    """Fila válida = completa y con decision_id/trade_id presentes y únicos."""
    did = (row.get("decision_id") or "").strip()
    tid = (row.get("trade_id") or "").strip()
    if not did or not tid:
        return False
    # debe tener al menos action y result no vacíos
    if not (row.get("action") or "").strip():
        return False
    if not (row.get("result") or "").strip():
        return False
    # descartar filas obviamente truncadas: result debe ser uno de los conocidos
    if (row.get("result") or "").strip().upper() not in {"WIN", "LOSS", "TIE", "TIE_INCIERTO"}:
        return False
    return True


def load_rows(csv_path: Path):
    rows, seen = [], set()
    dup = 0
    with open(csv_path, newline="", encoding="utf-8", errors="replace") as fh:
        reader = csv.DictReader(fh)
        for raw in reader:
            # tolerar BOM / espacios en nombres de columna
            row = {(k or "").strip().lstrip("\ufeff"): (v or "").strip()
                   for k, v in raw.items()}
            did = row.get("decision_id", "")
            tid = row.get("trade_id", "")
            if not valid_row(row):
                continue
            key = (did, tid)
            if key in seen:
                dup += 1
                continue  # fila duplicada (decision_id/trade_id no únicos) → no cuenta
            seen.add(key)
            rows.append(row)
    return rows, dup


def classify(rows):
    w = l = t = 0
    for r in rows:
        res = (r.get("result") or "").strip().upper()
        if res == "WIN":
            w += 1
        elif res == "LOSS":
            l += 1
        else:
            t += 1  # TIE / TIE_INCIERTO
    return w, l, t


def pending_from_csv(csv_path: Path) -> int:
    """Operaciones marcadas como en curso: filas PENDING o con trade_id vacío
    tras una decision válida no contable (best-effort; el journal v22 escribe
    al settle, así que pending habitualmente = 0)."""
    n = 0
    try:
        with open(csv_path, newline="", encoding="utf-8", errors="replace") as fh:
            for raw in csv.DictReader(fh):
                row = {(k or "").strip().lstrip("\ufeff"): (v or "").strip()
                       for k, v in raw.items()}
                res = (row.get("result") or "").strip().upper()
                if res == "PENDING":
                    n += 1
    except FileNotFoundError:
        pass
    return n


def pending_from_rows(rows) -> int:
    """Igual que pending_from_csv pero sobre filas ya cargadas/segmentadas
    (usar con new_rows tras restar el baseline)."""
    n = 0
    for row in rows:
        if (row.get("result") or "").strip().upper() == "PENDING":
            n += 1
    return n


def fetch_device_csv(serial: str, dest: Path) -> Path:
    out = subprocess.run(
        ["adb", "-s", serial, "shell", "cat", DEFAULT_DEVICE_CSV],
        capture_output=True, text=True, timeout=30,
    )
    if out.returncode != 0 or not out.stdout.strip():
        raise RuntimeError(f"adb shell cat falló (rc={out.returncode}): {out.stderr[:200]}")
    dest.write_text(out.stdout, encoding="utf-8")
    return dest


R30_LIVE = Path(__file__).resolve().parent.parent / "r30_live"
R30_EVENTS = R30_LIVE / "watchdog_events.jsonl"
R31_EVENTS = Path(__file__).resolve().parent / "watchdog_events.jsonl"


def parse_iso(ts: str) -> float:
    ts = ts.strip().replace("Z", "+00:00")
    return datetime.fromisoformat(ts).timestamp()


def runtime_minutes(session_start: Path) -> int:
    if not session_start.exists():
        return 0
    try:
        data = json.loads(session_start.read_text(encoding="utf-8"))
        # session_start.json de R31 usa "start_utc" (ISO8601); aceptar variantes.
        start = data.get("start_utc") or data.get("start_ts_utc") or data.get("start_ts")
        if not start:
            return 0
        start = str(start)
        if re.fullmatch(r"\d{10,13}", start):  # epoch s o ms
            v = int(start)
            if v > 10**12:
                v //= 1000
            start_epoch = v
        else:
            start_epoch = parse_iso(start)
        now = datetime.now(timezone.utc).timestamp()
        return max(0, int((now - start_epoch) // 60))
    except Exception:
        return 0


def infra_counters(watchdog_state: Path, since_epoch: float):
    """Contadores de infraestructura SOLO de la ventana R31.

    Los counters del watchdog_state.json (reconexiones_ok/fail, disconnects)
    son CUMULATIVOS de la vida del daemon r30 (desde 17:39Z, pre-R31) y NO
    son filtrables por tiempo: se IGNORAN cuando hay ventana temporal.
    La fuente verificable son los EVENTS JSONL (r30_live + r31_live)
    filtrados por timestamp_utc >= since_epoch. Con since_epoch<=0 se
    conserva el comportamiento legacy (counters del state + events sin
    filtro) para compatibilidad.
    """
    if since_epoch <= 0:
        return _infra_counters_legacy(watchdog_state)
    return load_events(since_epoch)


def _infra_counters_legacy(watchdog_state: Path):
    """Comportamiento anterior (sin ventana temporal): counters del state
    (cumulativos del daemon) combinados con events SIN filtro temporal."""
    disconnects = recoveries_ok = recoveries_failed = 0
    if watchdog_state.exists():
        try:
            data = json.loads(watchdog_state.read_text(encoding="utf-8"))
            cnt = data.get("counters") or {}
            recoveries_ok = int(cnt.get("reconexiones_ok") or 0)
            recoveries_failed = int(cnt.get("reconexiones_fail") or 0)
            disconnects = int(cnt.get("disconnects") or 0)
        except Exception:
            pass
    # events: contar WS_DISCONNECT (incidentes) y RECOVERY_OK/RECOVERY_FAIL
    ev_disc = ev_ok = ev_fail = 0
    seen_incidents = set()
    for events_path in (R30_EVENTS, R31_EVENTS):
        if not events_path.exists():
            continue
        with open(events_path, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except Exception:
                    continue
                et = ev.get("event_type") or ev.get("event") or ""
                if et == "WS_DISCONNECT":
                    iid = ((ev.get("data") or {}).get("incident_id")
                           or ev.get("event_id") or "")
                    if iid not in seen_incidents:
                        seen_incidents.add(iid)
                        ev_disc += 1
                elif et == "RECOVERY_OK":
                    ev_ok += 1
                elif et == "RECOVERY_FAIL":
                    ev_fail += 1
    if ev_disc:
        disconnects = ev_disc
    if ev_ok or ev_fail:
        recoveries_ok = max(recoveries_ok, ev_ok)
        recoveries_failed = max(recoveries_failed, ev_fail)
    return disconnects, recoveries_ok, recoveries_failed


def load_events(since_epoch: float):
    """Lee los events JSONL (r30_live + r31_live) y devuelve
    (disconnects, recoveries_ok, recoveries_failed) contando SOLO eventos
    con timestamp_utc >= since_epoch. Desconexiones deduplicadas por
    incident_id/event_id."""
    disc = ok = fail = 0
    seen_incidents = set()
    for events_path in (R30_EVENTS, R31_EVENTS):
        if not events_path.exists():
            continue
        try:
            with open(events_path, encoding="utf-8", errors="replace") as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        ev = json.loads(line)
                    except Exception:
                        continue
                    ts = ev.get("timestamp_utc") or ev.get("ts_utc") or ""
                    try:
                        t_epoch = parse_iso(ts)
                    except Exception:
                        continue  # sin timestamp parseable: no cuenta
                    if t_epoch < since_epoch:
                        continue  # evento pre-R31: NO cuenta (anti-R30-skew)
                    et = ev.get("event_type") or ev.get("event") or ""
                    if et == "WS_DISCONNECT":
                        iid = ((ev.get("data") or {}).get("incident_id")
                               or ev.get("event_id") or "")
                        if iid not in seen_incidents:
                            seen_incidents.add(iid)
                            disc += 1
                    elif et == "RECOVERY_OK":
                        ok += 1
                    elif et == "RECOVERY_FAIL":
                        fail += 1
        except Exception:
            continue
    return disc, ok, fail


def main(argv=None):
    ap = argparse.ArgumentParser(description="Progreso R31 (idempotente)")
    ap.add_argument("--csv", help="CSV provisto localmente (pull del coordinador)")
    ap.add_argument("--device", action="store_true",
                    help="leer el journal vía adb shell cat (path canónico)")
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--watchdog-state",
                    default=str(R30_LIVE / "watchdog_state.json"),
                    help="state del watchdog (solo usado en modo legacy, since<=0)")
    ap.add_argument("--events", default="",
                    help="obsoleto: los events se leen de r30_live+r31_live; "
                         "el argumento se conserva por compatibilidad y se ignora")
    ap.add_argument("--since", default=None,
                    help="ISO8601 UTC: contar solo eventos posteriores "
                         "(default: start_utc de session_start.json)")
    ap.add_argument("--session-start", default=str(SESSION_START))
    ap.add_argument("--out", default=str(OUTPUT))
    args = ap.parse_args(argv)

    # Ventana temporal R31: start_utc de session_start.json (fallback: epoch 0
    # = comportamiento legacy). FIX anti-doble-conteo de ops R30.
    since_epoch = 0.0
    try:
        ss = json.loads(Path(args.session_start).read_text(encoding="utf-8"))
        baseline_rows = int(ss.get("baseline_journal_rows") or 0)
        since_epoch = parse_iso(ss.get("start_utc") or "")
    except Exception:
        baseline_rows = 0
    if args.since:
        since_epoch = parse_iso(args.since)

    csv_path = None
    if args.csv:
        csv_path = Path(args.csv)
    elif args.device:
        csv_path = Path(args.out).with_suffix(".device.csv")
        fetch_device_csv(args.serial, csv_path)
    else:
        env_default = Path(__file__).resolve().parent / "trade_journal_r31.csv"
        if env_default.exists():
            csv_path = env_default
        else:
            csv_path = Path(args.out).with_suffix(".device.csv")
            fetch_device_csv(args.serial, csv_path)

    rows_all, dup = load_rows(csv_path)
    # FIX bug (a): segmentar por baseline — el journal contiene TODO el
    # historial (R30 inclusive); R31 cuenta SOLO filas nuevas. Corte
    # PRIMARIO = temporal (signal_ts >= start_utc del armado, robusto contra
    # off-by-one de conteo de líneas vs data-rows — el baseline real son 32
    # data rows: evidencia r30_live/evidence/trade_journal_pulled_20261113T2149Z.csv);
    # FALLBACK = conteo baseline_journal_rows si el timestamp no es parseable.
    if baseline_rows > 0 and since_epoch > 0:
        def _sig(r):
            v = (r.get("signal_ts") or r.get("timestamp") or "").strip()
            try:
                f = float(v)
            except ValueError:
                return None
            if f > 1e12:  # milisegundos → segundos (signal_ts es epoch ms)
                f /= 1000.0
            return f
        rows = [r for r in rows_all if (_sig(r) is not None and _sig(r) >= since_epoch)
                or _sig(r) is None]
        n_pre = len(rows_all) - len(rows)
        if n_pre:
            print(f"[update_progress] Corte temporal: {n_pre} filas pre-R31 "
                  f"excluidas (signal_ts < {args.since or 'start_utc'}).",
                  file=sys.stderr)
    elif baseline_rows > 0 and len(rows_all) > baseline_rows:
        rows = rows_all[baseline_rows:]
    else:
        rows = rows_all
        if baseline_rows > 0 and len(rows_all) <= baseline_rows:
            print(f"[update_progress] Aviso: {len(rows_all)} filas válidas <= "
                  f"baseline {baseline_rows}; 0 ops R31 nuevas aún.",
                  file=sys.stderr)
    wins, losses, ties = classify(rows)
    pending = pending_from_rows(rows)
    resolved = wins + losses + ties

    disconnects, recoveries_ok, recoveries_failed = infra_counters(
        Path(args.watchdog_state), since_epoch)
    runtime = runtime_minutes(Path(args.session_start))

    progress = {
        "target": TARGET,
        "resolved": resolved,
        "wins": wins,
        "losses": losses,
        "ties": ties,
        "pending": pending,
        "runtime_minutes": runtime,
        "disconnects": disconnects,
        "recoveries_ok": recoveries_ok,
        "recoveries_failed": recoveries_failed,
    }
    out_path = Path(args.out)
    out_path.write_text(json.dumps(progress, indent=1) + "\n", encoding="utf-8")
    print(json.dumps(progress))
    if dup:
        print(f"[update_progress] {dup} filas duplicadas ignoradas "
              f"(decision_id/trade_id repetidos)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
