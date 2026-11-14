#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# r31_watchdog.sh — LIVE WATCHDOG daemon (FASE 1 R31) — Watchdog V2
#
# Sucesor drop-in de scripts/r30_watchdog.sh (que queda INTACTO como rollback).
# Corrige los 5 defectos demostrados en R30:
#
#   D1 (congelación por diálogo desconocido): UNKNOWN → avanza a L2 (visual)
#      en el MISMO ciclo. Jamás se queda detenido; jamás tap a ciego.
#   D2 (latch ESCALATE sticky): escalate() ya NO suprime acciones; solo
#      notifica (ESCALATE_NOTIFY) y el bucle SIGUE rotando L1..L5. El clear
#      del latch ocurre SIEMPRE: (a) al boot con --reset-escalate; (b) en la
#      rama INIT→CONNECTED (EL fix del defecto R30); (c) en el gate
#      VERIFYING→CONNECTED. purge_escalate() es pura y testeable.
#   D3 (falso positivo order_late con trade en vuelo): si
#      risk.hasPendingTrade=true Y tickAge < umbral → TRADE_IN_FLIGHT: log
#      informativo, SIN cambio de FSM, sin recovery. Un order_late NO puede
#      producir DEGRADED/DISCONNECTED.
#   D4 (dependencia de uiautomator sobre Binomo): L1 es best-effort; dump
#      vacío/ilegible/ambiguo → L2 (visual fallback) EN EL MISMO ciclo.
#   D5 (isDemoAccount=false fuera de foco): tri-estado DEMO_CONFIRMED /
#      DEMO_UNKNOWN / REAL_CONFIRMED. Solo REAL_CONFIRMED alarma (DEMO_FLIP).
#
# FSM: CONNECTED → DEGRADED → DISCONNECTED → RECOVERING_L1..L5 → VERIFYING → CONNECTED
#
# MULTIMODAL por ciclo: (1) GET /status bridge; (2) pidof TD+broker vía adb;
# (3) foco vía dumpsys window; (4) batería vía dumpsys battery; (5) lastTickAge;
# (6) hasPendingTrade; (7) modo.
#
# NIVELES DE RECUPERACIÓN (una acción por ciclo; NUNCA abandonar):
#   L1 dump+whitelist ["Reintentar","Reconectar","Retry"] bounds únicos; si
#      dump vacío/ilegible/ambiguo → L2 EN EL MISMO ciclo.
#   L2 visual task: $VISUAL_TASK_DIR/pending + task_<ts>.json; el LIVE GUARDIAN
#      (agente externo) escribe approved_<incident>.json {x,y,incident_id} o
#      rejected_<incident>.json. El watchdog consume, tap único y archiva.
#   L3 Binomo congelado: sin trade pendiente → monkey LAUNCHER (jamás
#      force-stop); con trade pendiente → NO relanzar, esperar settle.
#   L4 TradeDraw/bridge: proceso muerto → monkey TD + btn_headless con dump
#      fresco; bridge sin respuesta ≥3 ciclos → evento BRIDGE_HUNG + visual
#      task (decisión externa).
#   L5 estado incierto: evidencia + reconciliación con journal (conteo de
#      filas vía adb shell cat) + rotar L1→L2→L3 con backoff.
#
# REGLAS DE ORO: jamás tap a ciego ni con coordenadas históricas; jamás tap si
# >1 botón ambiguo o pantalla no-Binomo; jamás --allow-disarm ni POST /mode ni
# tocar montos; jamás re-enviar decision_id/trade_id; NUNCA am force-stop.
#
# USO (CLI compatible con r30 + extensiones):
#   scripts/r31_watchdog.sh [--interval 20] [--tick-max-age-ms 5000]
#     [--battery-min 25] [--max-recover 5] [--backoff-base 30]
#     [--recheck-wait 15] [--serial emulator-5554] [--once] [--max-cycles N]
#     [--evidence-dir DIR] [--state-file FILE] [--events-file FILE]
#     [--visual-task-dir DIR] [--reset-escalate] [--health-event-s 300]
#     [--fast-interval 7] [--help]
#
# MODO TESTEABLE (no invasivo):
#   R31_DRY_RUN=1      → taps/monkey/screencap NO se ejecutan; se registran en
#                        <visual-task-dir>/actions.log
#   R31_ADB_STUB=cmd   → sustituye el binario adb (tests con fixtures)
#   R31_CURL_STUB=cmd  → sustituye curl: cmd <url> <outfile> → imprime código
# ============================================================================
set -u
# set -e omitido a propósito: los fallos de curl/adb son señales, no aborts.

TMPDIR="${TMPDIR:-$HOME}"
[ -d "$TMPDIR" ] && [ -w "$TMPDIR" ] || TMPDIR="$HOME"

# ----------------------------------------------------------------- constantes
NS_UUID="6b1f2a55-0d4e-4a17-9d0e-2b6c8a11f0e1"
COMPONENT="WATCHDOG"
SCHEMA_VERSION="1.1.0"
SRC_REL="scripts/r31_watchdog.sh"
PKG_TD="com.example.tradedraw"
PKG_BROKER="com.marketly.trading"
URL_PRIMARY="http://127.0.0.1:18080"
URL_FALLBACK="http://[::1]:8080"
# (BTN_LABEL_RE se define DENTRO del bloque de funciones puras, marcado abajo,
#  para que la extracción de test sea autocontenida.)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ----------------------------------------------------------------- defaults
INTERVAL=20
TICK_MAX_AGE_MS=5000
BATTERY_MIN=25
MAX_RECOVER=5
BACKOFF_BASE=30
RECHECK_WAIT=15
FAST_INTERVAL=7
HEALTH_EVENT_S=300
SERIAL="emulator-5554"
ONCE=0
MAX_CYCLES=0            # 0 = ilimitado
RESET_ESCALATE=0
EVID_DIR="${ROOT}/loops/r31_live/watchdog_evidence"
STATE_FILE="${ROOT}/loops/r31_live/watchdog_state.json"
EVENTS_FILE="${ROOT}/loops/r31_live/watchdog_events.jsonl"
VISUAL_TASK_DIR="${ROOT}/loops/r31_live/visual_tasks"
DEMO_STALE_S=120
TASK_TIMEOUT_S=600   # visual task sin respuesta del guardian >10min → rotar sin borrar

usage() { sed -n '2,60p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [ $# -gt 0 ]; do
  case "$1" in
    --interval)        INTERVAL="$2"; shift 2;;
    --tick-max-age-ms) TICK_MAX_AGE_MS="$2"; shift 2;;
    --battery-min)     BATTERY_MIN="$2"; shift 2;;
    --max-recover)     MAX_RECOVER="$2"; shift 2;;
    --backoff-base)    BACKOFF_BASE="$2"; shift 2;;
    --recheck-wait)    RECHECK_WAIT="$2"; shift 2;;
    --serial)          SERIAL="$2"; shift 2;;
    --once)            ONCE=1; shift;;
    --max-cycles)      MAX_CYCLES="$2"; shift 2;;
    --reset-escalate)  RESET_ESCALATE=1; shift;;
    --evidence-dir)    EVID_DIR="$2"; shift 2;;
    --state-file)      STATE_FILE="$2"; shift 2;;
    --events-file)     EVENTS_FILE="$2"; shift 2;;
    --visual-task-dir) VISUAL_TASK_DIR="$2"; shift 2;;
    --fast-interval)   FAST_INTERVAL="$2"; shift 2;;
    --health-event-s)  HEALTH_EVENT_S="$2"; shift 2;;
    -h|--help)         usage;;
    *) echo "r31_watchdog: flag desconocido: $1" >&2; exit 2;;
  esac
done

mkdir -p "$EVID_DIR" "$(dirname "$STATE_FILE")" "$(dirname "$EVENTS_FILE")" "$VISUAL_TASK_DIR"

# ------------------------------------------------------- modo testeable
DRY_RUN="${R31_DRY_RUN:-0}"
ADB_STUB="${R31_ADB_STUB:-}"
CURL_STUB="${R31_CURL_STUB:-}"

# ----------------------------------------------------------------- utilidades
now_ms() { local t; t=$(date +%s%3N 2>/dev/null); case "$t" in *N) date +%s000;; *) echo "$t"; esac; }
iso_utc() { date -u -d "@$(( $1 / 1000 ))" '+%Y-%m-%dT%H:%M:%S.000Z' 2>/dev/null || date -u '+%Y-%m-%dT%H:%M:%SZ'; }
jesc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr '\n\t\r' '   '; }

hex2bin() { local h="$1" out="" i; for ((i=0; i<${#h}; i+=2)); do out+="\\x${h:i:2}"; done; printf "$out"; }
uuid5() { # uuid5 <namespace> <name> (paridad con python uuid5, heredada de r30)
  local ns="${1//-/}" name="$2" d var
  d=$( { hex2bin "$ns"; printf '%s' "$name"; } | sha1sum | cut -d' ' -f1 )
  var=$(( (16#${d:16:1} & 3) | 8 ))
  printf '%s-%s-%s-%x%s-%s' "${d:0:8}" "${d:8:4}" "5${d:13:3}" "$var" "${d:17:3}" "${d:20:12}"
}

WD_SESSION_SEED="$(now_ms)-${SERIAL}-r31"
WD_SESSION_ID="sess-$(uuid5 "$NS_UUID" "watchdog-session:${WD_SESSION_SEED}" | tr -d '-' | cut -c1-8)"

emit_event() { # emit_event <event_type> <verification_result> <detail> <prev> <new> <data>
  local et="$1" vr="$2" vd="$3" ps="$4" ns="$5" dj="$6" ts eid srcline natural line
  ts=$(now_ms)
  srcline=$(caller 0 | awk '{print $1}')
  natural="{\"event_type\":\"$(jesc "$et")\",\"component\":\"$(jesc "$COMPONENT")\",\"ts_ms\":${ts},\"file\":\"${SRC_REL}\",\"line\":${srcline}}"
  eid=$(uuid5 "$NS_UUID" "$natural")
  line=$(printf '{"event_id":"%s","timestamp_utc":"%s","event_type":"%s","component":"%s","prev_state":%s,"new_state":%s,"data":%s,"session_id":"%s","verification_result":"%s","verification_detail":"%s","schema_version":"%s","source":{"file":"%s","line":%s}}' \
    "$eid" "$(iso_utc "$ts")" "$et" "$COMPONENT" "$ps" "$ns" "$dj" \
    "$WD_SESSION_ID" "$vr" "$(jesc "$vd")" "$SCHEMA_VERSION" "$SRC_REL" "$srcline")
  printf '%s\n' "$line" >> "$EVENTS_FILE"
}

# ------------------------------------------------------- capas I/O stubbeables
adb_raw() {
  if [ -n "$ADB_STUB" ]; then "$ADB_STUB" "$@"; return $?; fi
  adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'
}
screencap_raw() {
  if [ -n "$ADB_STUB" ]; then "$ADB_STUB" exec-out screencap -p; return $?; fi
  adb -s "$SERIAL" exec-out screencap -p 2>/dev/null
}
curl_raw() { # curl_raw <url> <outfile> → imprime código http
  local url="$1" out="$2" code
  if [ -n "$CURL_STUB" ]; then
    code=$("$CURL_STUB" "$url" "$out" 2>/dev/null) || code=000
    echo "${code:-000}"; return 0
  fi
  code=$(curl --noproxy '*' -s -m 5 -w '%{http_code}' -o "$out" "$url" 2>/dev/null)
  echo "${code:-000}"
}

fetch_status() { # → 0 si 200 y body con app=TradeDraw NO exigido aquí (lo exige el llamador)
  local code
  STATUS_BODY="${TMPDIR}/r31_wd_status_body.json"
  TRANSPORT_USED="$URL_PRIMARY"
  code=$(curl_raw "${URL_PRIMARY}/status" "$STATUS_BODY")
  if [ "$code" = "200" ] && [ -s "$STATUS_BODY" ]; then return 0; fi
  TRANSPORT_USED="$URL_FALLBACK"
  code=$(curl_raw "${URL_FALLBACK}/status" "$STATUS_BODY")
  if [ "$code" = "200" && -s "$STATUS_BODY" ]; then return 0; fi
  TRANSPORT_USED="none"
  return 1
}

jstr() { grep -oE "$1[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$2" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*"//; s/"$//'; }
jbool() { grep -oE "$1[[:space:]]*:[[:space:]]*(true|false)" "$2" 2>/dev/null | head -1 | grep -oE '(true|false)$'; }
jnum() { grep -oE "$1[[:space:]]*:[[:space:]]*(-?[0-9]+)" "$2" 2>/dev/null | head -1 | grep -oE -- '-?[0-9]+$'; }

read_status_fields() { # parsea STATUS_BODY → S_* (risk.*, feed.*, engine.*, health.*, balance)
  S_MODE=$(jstr '"mode"' "$STATUS_BODY")
  S_DEMO=$(jbool '"isDemoAccount"' "$STATUS_BODY")
  S_HEALTH=$(jstr '"state"' "$STATUS_BODY")
  S_TICK=$(jnum '"lastTickAgeMs"' "$STATUS_BODY")
  S_CONNECTED=$(jbool '"connected"' "$STATUS_BODY")
  S_PENDING=$(jbool '"hasPendingTrade"' "$STATUS_BODY")
  S_ORDER=$(jstr '"orderState"' "$STATUS_BODY")
  S_STAKE=$(jnum '"effectiveStake"' "$STATUS_BODY")
  S_TWINS=$(jnum '"totalWins"' "$STATUS_BODY")
  S_TLOSSES=$(jnum '"totalLosses"' "$STATUS_BODY")
  S_SIG_GEN=$(jnum '"signalsGenerated"' "$STATUS_BODY")
  S_SIG_EVAL=$(jnum '"evaluatedCount"' "$STATUS_BODY")
  S_BALANCE=$(grep -oE '"balance":[[:space:]]*[^,}]+' "$STATUS_BODY" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*//')
  S_COOLDOWN=$(jnum '"remainingCooldown"' "$STATUS_BODY")
  S_ACCTYPE=$(jstr '"accountType"' "$STATUS_BODY")
  : "${S_COOLDOWN:=0}"; : "${S_ACCTYPE:=UNKNOWN}"
  APP_VERSION="$(jstr '"versionName"' "$STATUS_BODY")"
}

get_td_pid()     { adb_raw pidof "$PKG_TD" | awk '{print $1}'; }
get_broker_pid() { adb_raw pidof "$PKG_BROKER" | awk '{print $1}'; }
get_focus()      { adb_raw dumpsys window | grep -m1 'mCurrentFocus'; }
focus_is_broker() { printf '%s' "$1" | grep -q "$PKG_BROKER"; }

get_battery() { # → "level powered" (powered 0/1; dumpsys battery SOLO vía adb)
  local out level powered=0
  out=$(adb_raw dumpsys battery)
  level=$(printf '%s\n' "$out" | grep -E '^ *[Ll]evel:' | grep -oE '[0-9]+' | head -1)
  printf '%s\n' "$out" | grep -Eq 'AC powered: true' && powered=1
  echo "${level:--1} $powered"
}

evidence_shot() { # evidence_shot <kind> → ruta del png (screencap ANTES de decidir)
  local f ts
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  f="${EVID_DIR}/wd_${ts}_${1}.png"
  screencap_raw > "$f" 2>/dev/null
  echo "$f"
}

ui_dump() { # dump uiautomator best-effort ('' si Binomo lo rechaza: anti-automation)
  adb_raw uiautomator dump /sdcard/r31_wd_dump.xml > /dev/null 2>&1
  local x
  x=$(adb_raw cat /sdcard/r31_wd_dump.xml 2>/dev/null)
  adb_raw rm -f /sdcard/r31_wd_dump.xml > /dev/null 2>&1
  printf '%s' "$x"
}

# ==================================================================
# INICIO BLOQUE DE FUNCIONES PURAS (marcador de extracción de tests; NO borrar)
# Todo lo que sigue hasta FIN BLOQUE no toca adb/curl/sleep ni archivos.
# ==================================================================
BTN_LABEL_RE='(Reintentar|Reintentar conexión|reintentar conexión|Reconectar|Reconnect|Retry)'

# --------------------------------------- D5: tri-estado DEMO (pura)
classify_demo_state() { # <isDemo:''|true|false> <focus_broker:0|1> <fresh_ok:0|1> [account_type:''|DEMO|REAL|UNKNOWN]
  local demo="$1" fb="$2" fresh="$3" acctype="${4:-}"
  if [ "$acctype" = "REAL" ]; then
    echo "REAL_CONFIRMED"
  elif [ "$demo" = "true" ] || [ "$acctype" = "DEMO" ]; then
    if [ "$fb" = "1" ] && [ "$fresh" = "1" ]; then
      echo "DEMO_CONFIRMED"
    else
      echo "DEMO_UNKNOWN"
    fi
  else
    # Si isDemoAccount=false o acctype=UNKNOWN por glitch de accesibilidad transitorio,
    # NO es evidencia explícita de cuenta REAL: es DEMO_UNKNOWN.
    echo "DEMO_UNKNOWN"
  fi
}

# ------------------ D3: order_late-aware / TRADE_IN_FLIGHT (pura)
classify_order_late() { # <order_late:0|1> <hasPendingTrade> <tickAge>=-1> <tick_max_ms>
  local late="$1" pend="$2" tick="$3" tmax="$4"
  [ "$late" = "1" ] || { echo "NONE"; return 0; }
  if [ "$pend" = "true" ] && [ "$tick" -ge 0 ] 2>/dev/null && [ "$tick" -lt "$tmax" ] 2>/dev/null; then
    echo "TRADE_IN_FLIGHT"
  else
    echo "ORDER_LATE"
  fi
}

# ------------------------------------- D2: latch ESCALATE (pura)
purge_escalate() { # <escalado:0|1|2> <checks_ok:0|1> → nuevo valor (0|1); SIEMPRE limpia si checks_ok
  local esc="$1" ok="$2"
  if [ "$ok" = "1" ]; then echo 0; else echo "$esc"; fi
}

# ------------------------------------- backoff (pura)
backoff_delay() { # reintento n cuesta min(2^(n-1)*base, 300)
  local n="$1" d
  d=$(( (1 << (n - 1)) * BACKOFF_BASE )); [ "$d" -gt 300 ] && d=300
  echo "$d"
}

# ------------------------------- L1: clasificación de diálogo (pura, evolución de r30)
classify_dialogue() { # <xml> <package> → "TAP <cx> <cy> <label>" | "UNKNOWN <motivo>"
  local xml="$1" pkg="$2" nodes nb nlabel label bounds l t r b2
  [ -n "$xml" ] || { echo "UNKNOWN dump_vacio_o_ilegible"; return 0; }
  printf '%s' "$xml" | grep -q 'could not get idle state' && { echo "UNKNOWN uiautomator_ilegible"; return 0; }
  nodes=$(printf '%s\n' "$xml" | grep -oE '<node [^>]*>' \
            | grep "package=\"$pkg\"" \
            | grep -iE "text=\"[^\"]*$BTN_LABEL_RE[^\"]*\"" || true)
  [ -n "$nodes" ] || { echo "UNKNOWN sin_dialogo_conocido"; return 0; }
  nb=$(printf '%s\n' "$nodes" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | sort -u | wc -l)
  nlabel=$(printf '%s\n' "$nodes" | grep -oiE "$BTN_LABEL_RE" | tr '[:upper:]' '[:lower:]' | sort -u | wc -l)
  if [ "$nb" -ne 1 ] || [ "$nlabel" -ne 1 ]; then echo "UNKNOWN ambiguo(${nb}bounds,${nlabel}labels)"; return 0; fi
  label=$(printf '%s\n' "$nodes" | grep -oiE "$BTN_LABEL_RE" | head -1)
  bounds=$(printf '%s\n' "$nodes" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  b="${bounds#bounds=\"}"; b="${b%\"}"
  [[ "$b" =~ \[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\] ]] || { echo "UNKNOWN bounds_ilegibles"; return 0; }
  l="${BASH_REMATCH[1]}"; t="${BASH_REMATCH[2]}"; r="${BASH_REMATCH[3]}"; b2="${BASH_REMATCH[4]}"
  echo "TAP $(( (l + r) / 2 )) $(( (t + b2) / 2 )) $label"
}

# ------------------- L2: contrato de archivos visual (decisión pura, I/O en $VISUAL_TASK_DIR)
visual_check_response() { # <incident_id> → "APPROVE x y" | "REJECT" | ""
  local inc="$1" f line x y
  # 1) Decisión formal del guardian: approved_<incident_id>.json {x,y,incident_id}
  f="${VISUAL_TASK_DIR}/approved_${inc}.json"
  if [ -f "$f" ]; then
    x=$(jnum '"x"' "$f"); y=$(jnum '"y"' "$f")
    if [ -n "$x" ] && [ -n "$y" ] && [ "$x" -ge 0 ] 2>/dev/null && [ "$y" -ge 0 ] 2>/dev/null; then
      echo "APPROVE $x $y"; return 0
    fi
    echo "REJECT"; return 0   # approval malformado → rechazo conservador
  fi
  # 2) Compat: approved_<ts>.json con incident_id dentro (p.ej. guardian responde por ts)
  for f in "$VISUAL_TASK_DIR"/approved_*.json; do
    [ -f "$f" ] || break
    line=$(cat "$f" 2>/dev/null) || continue
    printf '%s' "$line" | grep -q "\"incident_id\"[[:space:]]*:[[:space:]]*\"${inc}\"" || continue
    x=$(jnum '"x"' "$f"); y=$(jnum '"y"' "$f")
    if [ -n "$x" ] && [ -n "$y" ] && [ "$x" -ge 0 ] 2>/dev/null && [ "$y" -ge 0 ] 2>/dev/null; then
      echo "APPROVE $x $y"; return 0
    fi
    echo "REJECT"; return 0
  done
  # 3) Rechazo formal
  for f in "$VISUAL_TASK_DIR"/rejected_*.json; do
    [ -f "$f" ] || break
    line=$(cat "$f" 2>/dev/null) || continue
    printf '%s' "$line" | grep -q "\"incident_id\"[[:space:]]*:[[:space:]]*\"${inc}\"" && { echo "REJECT"; return 0; }
  done
  echo ""
}

visual_create_task() { # <incident_id> <reason> → imprime ts; crea screencap + task_<ts>.json
  local inc="$1" reason="$2" ts png
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  png="${EVID_DIR}/pending_${ts}.png"
  screencap_raw > "$png" 2>/dev/null
  local tmp="${VISUAL_TASK_DIR}/task_${ts}.json.tmp"
  printf '{"type":"visual_task","ts":"%s","png":"%s","incident_id":"%s","reason":"%s"}\n' \
    "$ts" "$(jesc "$png")" "$(jesc "$inc")" "$(jesc "$reason")" > "$tmp"
  mv -f "$tmp" "${VISUAL_TASK_DIR}/task_${ts}.json"
  echo "$ts"
}

visual_archive_task() { # <incident_id> <ts> <resolved:0|1> — saca la task del buzón activo
  local inc="$1" ts="$2" ok="$3" dst status f
  dst="${VISUAL_TASK_DIR}/archive"; mkdir -p "$dst"
  status="unresolved"; [ "$ok" = "1" ] && status="resolved"
  for f in "${VISUAL_TASK_DIR}/task_${ts}.json" \
           "${VISUAL_TASK_DIR}/approved_${inc}.json" "${VISUAL_TASK_DIR}/approved_${ts}.json" \
           "${VISUAL_TASK_DIR}/rejected_${inc}.json" "${VISUAL_TASK_DIR}/rejected_${ts}.json"; do
    [ -f "$f" ] && mv -f "$f" "${dst}/${status}_${f##*/}" 2>/dev/null
  done
}

# ------------------ métricas de incidente (MTTD/MTTR; definidas en el bloque puro)

incident_record() { # incident_record <recovered:0|1> — línea JSONL con MTTD/MTTR
  local ok="$1" mttd=0 mttr=0
  [ "${INC_T_RECO:-0}" -gt 0 ] 2>/dev/null && mttd=$(( INC_T_RECO - INC_T_DISC ))
  [ "${INC_T_VER:-0}" -gt 0 ] 2>/dev/null && mttr=$(( INC_T_VER - INC_T_DISC ))
  [ "$mttd" -lt 0 ] 2>/dev/null && mttd=0
  [ "$mttr" -lt 0 ] 2>/dev/null && mttr=0
  printf '{"event":"incident_summary","incident_id":"%s","ts_utc":"%s","T_disconnect_detected":%s,"T_recovery_started":%s,"T_action":%s,"T_verified_connected":%s,"mttd_ms":%s,"mttr_ms":%s,"recovered":%s}\n' \
    "$(jesc "${INC_ID:-}")" "$(iso_utc "$(now_ms)")" "${INC_T_DISC:-0}" "${INC_T_RECO:-0}" "${INC_T_ACT:-0}" "${INC_T_VER:-0}" "$mttd" "$mttr" "$ok" >> "$(incident_jsonl_path)"
}

incident_reset() { INC_T_DISC=0; INC_T_RECO=0; INC_T_ACT=0; INC_T_VER=0; INC_ID=""; }

# ----------------- helpers de métricas de incidente (I/O ligado a $STATE_FILE)
incident_state_path() { printf '%s/incident_state.json' "$(dirname "$STATE_FILE")"; }
incident_jsonl_path() { printf '%s/incident_metrics.jsonl' "$(dirname "$STATE_FILE")"; }

incident_load() { # carga best-effort desde incident_state.json
  local f; f=$(incident_state_path)
  INC_T_DISC=$(grep -oE '"T_disconnect_detected":[[:space:]]*[0-9]+' "$f" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  INC_T_RECO=$(grep -oE '"T_recovery_started":[[:space:]]*[0-9]+' "$f" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  INC_T_ACT=$(grep -oE '"T_action":[[:space:]]*[0-9]+' "$f" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  INC_T_VER=$(grep -oE '"T_verified_connected":[[:space:]]*[0-9]+' "$f" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  INC_LEVEL=$(grep -oE '"fsm_level":"[^"]*"' "$f" 2>/dev/null | sed -E 's/.*:"//; s/"$//' | tail -1)
  INC_ID=$(grep -oE '"incident_id":"[^"]*"' "$f" 2>/dev/null | sed -E 's/.*:"//; s/"$//' | tail -1)
  : "${INC_T_DISC:=0}"; : "${INC_T_RECO:=0}"; : "${INC_T_ACT:=0}"; : "${INC_T_VER:=0}"
  : "${INC_LEVEL:=L0}"; : "${INC_ID:=}"
}

incident_save() { # persiste (atomic write)
  local tmp
  tmp="$(incident_state_path).tmp"
  printf '{"incident_id":"%s","T_disconnect_detected":%s,"T_recovery_started":%s,"T_action":%s,"T_verified_connected":%s,"fsm_level":"%s"}\n' \
    "$(jesc "$INC_ID")" "${INC_T_DISC:-0}" "${INC_T_RECO:-0}" "${INC_T_ACT:-0}" "${INC_T_VER:-0}" "${INC_LEVEL:-L0}" > "$tmp"
  mv -f "$tmp" "$(incident_state_path)"
}

# ----------------- buzón visual (contrato con el LIVE GUARDIAN)
visual_task_pending() { # → 0 si hay task visual SIN consumir en el buzón activo
  local f
  for f in "$VISUAL_TASK_DIR"/task_*.json; do [ -f "$f" ] && return 0; done
  return 1
}

visual_task_stale() { # → 0 si la task más vieja supera TASK_TIMEOUT_S sin respuesta
  local f fts now age
  now=$(date -u +%s)
  for f in "$VISUAL_TASK_DIR"/task_*.json; do
    [ -f "$f" ] || return 1
    fts="${f##*/task_}"; fts="${fts%.json}"
    age=$(( now - $(date -u -d "${fts:0:4}-${fts:4:2}-${fts:6:2}T${fts:9:2}:${fts:11:2}:${fts:13:2}Z" +%s 2>/dev/null || echo "$now") ))
    [ "$age" -gt "$TASK_TIMEOUT_S" ] && return 0
  done
  return 1
}

# ================ FIN BLOQUE DE FUNCIONES PURAS (marcador de extracción de tests)

# =========================================== FSM + estado persistente
STATE="INIT"; STATE_SINCE="$(now_ms)"
C_RECO_OK=0; C_RECO_FAIL=0; C_DISCONNECT_S=0; C_CYCLES=0
ESCALATED=0; ESCALATE_REASON=""
if [ -f "$STATE_FILE" ]; then
  C_RECO_OK=$(grep -oE '"reconexiones_ok":[[:space:]]*[0-9]+' "$STATE_FILE" | grep -oE '[0-9]+' | tail -1)
  C_RECO_FAIL=$(grep -oE '"reconexiones_fail":[[:space:]]*[0-9]+' "$STATE_FILE" | grep -oE '[0-9]+' | tail -1)
  C_DISCONNECT_S=$(grep -oE '"tiempo_desconectado_acum_s":[[:space:]]*[0-9]+' "$STATE_FILE" | grep -oE '[0-9]+' | tail -1)
  C_CYCLES=$(grep -oE '"ciclos":[[:space:]]*[0-9]+' "$STATE_FILE" | grep -oE '[0-9]+' | tail -1)
  ESCALATED=$(grep -oE '"escalate":[[:space:]]*(true|false)' "$STATE_FILE" | grep -oE '(true|false)' | tail -1)
  [ "$ESCALATED" = "true" ] && ESCALATED=1 || ESCALATED=0
  ESCALATE_REASON=$(grep -oE '"escalate_reason":[[:space:]]*"[^"]*"' "$STATE_FILE" | sed -E 's/.*:[[:space:]]*"//; s/"$//')
  : "${C_RECO_OK:=0}"; : "${C_RECO_FAIL:=0}"; : "${C_DISCONNECT_S:=0}"; : "${C_CYCLES:=0}"
fi
# D2 (parte boot): --reset-escalate limpia el latch heredado.
[ "$RESET_ESCALATE" = "1" ] && { ESCALATED=0; ESCALATE_REASON=""; }

LAST_PREV_STATE="INIT"
set_state() {
  [ "$1" = "$STATE" ] && return 0
  LAST_PREV_STATE="$STATE"; STATE="$1"; STATE_SINCE="$(now_ms)"
  printf '[r31_watchdog] estado %s -> %s\n' "$LAST_PREV_STATE" "$STATE" >&2
}

# ------------------------------------ continuidad del FSM vía state file
# Si el state file heredado trae un FSM en curso (DISCONNECTED/RECOVERING_*/VERIFYING),
# se RETOMA el nivel exacto de recuperación (L1..L5) y el incident_id para que el
# contrato visual (approved_<incident>.json) sobreviva a un relanzamiento del daemon.
# NOTA de compat r30: los consumers antiguos solo leen counters/escalate/last_status,
# por eso el nivel viaja en "fsm_level" y "state" (mismo esquema de claves planas).
if [ -f "$STATE_FILE" ]; then
  SAVED_STATE=$(grep -oE '"state":"[^"]*"' "$STATE_FILE" | head -1 | sed -E 's/.*:"//; s/"$//')
  case "$SAVED_STATE" in
    DISCONNECTED|RECOVERING_L1|RECOVERING_L2|RECOVERING_L3|RECOVERING_L4|RECOVERING_L5|VERIFYING)
      STATE="$SAVED_STATE"
      printf '[r31_watchdog] FSM retomado del state file: %s\n' "$STATE" >&2
      ;;
  esac
fi

# =========================================== I/O de acciones (dry-run safe)
do_tap() { # ÚNICO punto de tap del watchdog
  local x="$1" y="$2" label="$3"
  if [ "$DRY_RUN" = "1" ]; then
    printf '{"action":"tap","x":%s,"y":%s,"label":"%s","ts_utc":"%s"}\n' \
      "$x" "$y" "$(jesc "$label")" "$(iso_utc "$(now_ms)")" >> "${VISUAL_TASK_DIR}/actions.log"
    return 0
  fi
  adb_raw input tap "$x" "$y"
}
do_monkey_broker() {
  if [ "$DRY_RUN" = "1" ]; then
    printf '{"action":"monkey_broker","ts_utc":"%s"}\n' "$(iso_utc "$(now_ms)")" >> "${VISUAL_TASK_DIR}/actions.log"
    return 0
  fi
  adb_raw monkey -p "$PKG_BROKER" -c android.intent.category.LAUNCHER 1
}
do_monkey_td() {
  if [ "$DRY_RUN" = "1" ]; then
    printf '{"action":"monkey_td","ts_utc":"%s"}\n' "$(iso_utc "$(now_ms)")" >> "${VISUAL_TASK_DIR}/actions.log"
    return 0
  fi
  adb_raw monkey -p "$PKG_TD" -c android.intent.category.LAUNCHER 1
}

# =========================================== verificación post-acción
full_recheck() { # → 0 si bridge 200 + foco Binomo + tick fresco + DEMO_CONFIRMED + orderState limpio
  sleep "$RECHECK_WAIT"
  fetch_status || return 1
  read_status_fields
  local td bp focus
  td=$(get_td_pid); bp=$(get_broker_pid); focus=$(get_focus)
  [ -n "$td" ] && [ -n "$bp" ] || return 1
  focus_is_broker "$focus" || return 1
  [ "$(classify_demo_state "${S_DEMO:-}" 1 1 "${S_ACCTYPE:-}")" = "DEMO_CONFIRMED" ] || return 1
  { [ -n "$S_TICK" ] && [ "$S_TICK" -ge 0 ] 2>/dev/null && [ "$S_TICK" -le "$TICK_MAX_AGE_MS" ] 2>/dev/null; } || return 1
  [ "$S_CONNECTED" = "true" ] || return 1
  { [ "$S_HEALTH" = "READY" ] || [ "${S_COOLDOWN:-0}" -gt 0 ]; } || return 1
  return 0
}

# =========================================== L1..L5 (una acción por ciclo)
recover_L1() { # dump uiautomator → whitelist → tap único → verificar. Fallback inmediato a L2.
  local focus shot xml cls cx cy label
  focus=$(get_focus)
  shot=$(evidence_shot "l1_a${INCIDENT_ATTEMPTS}")
  [ -s "$shot" ] || { emit_event "RECOVERY_FAIL" "UNVERIFIED" "L1: screencap vacío (sin evidencia NO hay decisión); pasa a L2" \
    '{"watchdog":"RECOVERING_L1"}' '{"watchdog":"RECOVERING_L2"}' '{}'; return 2; }
  if ! focus_is_broker "$focus"; then
    emit_event "RECOVERY_FAIL" "UNVERIFIED" "L1: foco NO es Binomo (${focus:-∅}); tap PROHIBIDO; pasa a L2" \
      '{"watchdog":"RECOVERING_L1"}' '{"watchdog":"RECOVERING_L2"}' '{}'
    return 2
  fi
  xml=$(ui_dump)   # best-effort: Binomo suele devolver dump vacío (anti-automatización, D4)
  cls=$(classify_dialogue "$xml" "$PKG_BROKER")
  case "$cls" in
    TAP\ *)
      read -r _ cx cy label <<< "$cls"
      emit_event "RECOVERY_TAP" "UNVERIFIED" "L1: diálogo inequívoco ('${label}', bounds únicos, paquete Binomo, evidencia ${shot}); tap ÚNICO" \
        '{"watchdog":"RECOVERING_L1"}' '{"watchdog":"RECOVERING_L1","tapped":true}' \
        "{\"attempt\":${INCIDENT_ATTEMPTS},\"x\":${cx},\"y\":${cy},\"label\":\"$(jesc "$label")\"}"
      do_tap "$cx" "$cy" "$label"
      [ "${INC_T_ACT:-0}" = "0" ] && { INC_T_ACT="$(now_ms)"; incident_save; }
      return 0
      ;;
    *)
      # FIX D1/D4: dump vacío/ilegible/ambiguo → L2 EN EL MISMO ciclo (rc=2)
      emit_event "RECOVERY_FAIL" "UNVERIFIED" "L1: classify_dialogue=${cls}; pasa a L2 (visual) en el mismo ciclo" \
        '{"watchdog":"RECOVERING_L1"}' '{"watchdog":"RECOVERING_L2"}' \
        "{\"attempt\":${INCIDENT_ATTEMPTS},\"classify\":\"$(jesc "$cls")\",\"screencap\":\"$(jesc "$shot")\"}"
      return 2
      ;;
  esac
}

recover_L2() { # visual fallback: consume approved/rejected; si no hay, crea task
  local resp ts x y
  if [ -n "${INC_ID:-}" ]; then
    resp=$(visual_check_response "$INC_ID")
    case "$resp" in
      APPROVE\ *)
        read -r _ x y <<< "$resp"
        if ! focus_is_broker "$(get_focus)"; then
          emit_event "RECOVERY_FAIL" "UNVERIFIED" "L2: guardian aprobó (${x},${y}) pero foco NO es Binomo; tap PROHIBIDO" \
            '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"RECOVERING_L2","tap_refused":true}' "{}"
          return 1
        fi
        emit_event "RECOVERY_TAP" "UNVERIFIED" "L2: guardian aprobó tap (${x},${y}) para ${INC_ID}; tap ÚNICO" \
          '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"RECOVERING_L2","tapped":true}' \
          "{\"attempt\":${INCIDENT_ATTEMPTS},\"x\":${x},\"y\":${y},\"incident_id\":\"$(jesc "$INC_ID")\"}"
        do_tap "$x" "$y" "guardian_approved"
        [ "${INC_T_ACT:-0}" = "0" ] && { INC_T_ACT="$(now_ms)"; incident_save; }
        visual_archive_task "$INC_ID" "${INC_L2_TS:-}" 1
        return 0
        ;;
      REJECT)
        emit_event "RECOVERY_ROTATE" "UNVERIFIED" "L2: guardian RECHAZÓ ${INC_ID}; escalando a L3" \
          '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"RECOVERING_L3"}' "{\"incident_id\":\"$(jesc "$INC_ID")\"}"
        visual_archive_task "$INC_ID" "${INC_L2_TS:-}" 0
        return 3
        ;;
    esac
  fi
  ts=$(visual_create_task "${INC_ID:-unknown}" "${ESCALATE_REASON:-degraded_bridge_or_ui}")
  INC_L2_TS="$ts"
  emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L2: visual task creada (task_${ts}.json); esperando decisión del LIVE GUARDIAN" \
    '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"RECOVERING_L2","task_created":true}' \
    "{\"task_ts\":\"${ts}\",\"incident_id\":\"$(jesc "${INC_ID:-unknown}")\"}"
  return 1
}

recover_L3() { # Binomo congelado: pendiente → NO relanzar; sin pendiente → monkey relaunch
  if [ "${S_PENDING:-}" = "true" ]; then
    emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L3: operación PENDIENTE (hasPendingTrade=true): NO se relanza Binomo (esperar settle); se reintenta después" \
      '{"watchdog":"RECOVERING_L3"}' '{"watchdog":"RECOVERING_L3","deferred":true}' '{}'
    return 1
  fi
  emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L3: sin operación pendiente; relanzamiento controlado de Binomo vía monkey LAUNCHER (jamás kill del proceso)" \
    '{"visit:RECOVERING_L3":1}' '{"watchdog":"RECOVERING_L3","monkey_broker":true}' '{}'
  do_monkey_broker
  [ "${INC_T_ACT:-0}" = "0" ] && { INC_T_ACT="$(now_ms)"; incident_save; }
  return 0
}

recover_L4() { # TradeDraw/bridge caído
  local td coords cx cy
  td=$(get_td_pid)
  if [ -z "$td" ]; then
    emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L4: proceso TradeDraw MUERTO; monkey LAUNCHER + btn_headless con dump FRESCO (uiautomator SÍ funciona sobre TD)" \
      '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","monkey_td":true}' '{}'
    do_monkey_td
    [ "${INC_T_ACT:-0}" = "0" ] && { INC_T_ACT="$(now_ms)"; incident_save; }
    sleep 2
    if coords=$(find_btn_headless); then
      read -r cx cy <<< "$coords"
      emit_event "RECOVERY_TAP" "UNVERIFIED" "L4: tap ÚNICO btn_headless (${cx},${cy}) desde dump FRESCO de la UI propia TD" \
        '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","tapped":"btn_headless"}' "{\"x\":${cx},\"y\":${cy}}"
      do_tap "$cx" "$cy" "btn_headless"
      [ "${INC_T_ACT:-0}" = "0" ] && { INC_T_ACT="$(now_ms)"; incident_save; }
    else
      emit_event "RECOVERY_FAIL" "UNVERIFIED" "L4: btn_headless no localizable en dump; NO se tapa a ciego" \
        '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","btn_not_found":true}' '{}'
    fi
    return 0
  fi
  if fetch_status; then
    L4_HUNG_COUNT=0
    emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L4: proceso TD vivo y bridge responde; re-verificar próximo ciclo" \
      '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","bridge_ok":true}' '{}'
    return 0
  fi
  L4_HUNG_COUNT=$((L4_HUNG_COUNT + 1))
  if [ "$L4_HUNG_COUNT" -ge 3 ]; then
    L4_HUNG_COUNT=0
    emit_event "BRIDGE_HUNG" "UNVERIFIED" "L4: bridge no responde ≥3 ciclos con proceso TD vivo; visual task para decisión EXTERNA del guardian" \
      '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","bridge_hung":true}' '{}'
    visual_create_task "${INC_ID:-unknown}" "BRIDGE_HUNG"
    return 1
  fi
  emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "L4: proceso vivo, bridge sin responder (${L4_HUNG_COUNT}/3): esperar 1 ciclo" \
    '{"watchdog":"RECOVERING_L4"}' '{"watchdog":"RECOVERING_L4","bridge_wait":true}' '{}'
  return 1
}

recover_L5() { # estado incierto: evidencia + reconciliación journal + rotar
  local shot xml jrows=0 jout dfile
  shot=$(evidence_shot "l5_uncertain")
  xml=$(ui_dump)
  dfile="${EVID_DIR}/l5_status_snapshot_$(date -u +%Y%m%dT%H%M%SZ).json"
  [ -f "${STATUS_BODY:-/nonexistent}" ] && cp -f "$STATUS_BODY" "$dfile" 2>/dev/null
  jout=$(adb_raw cat "$JOURNAL_DEVICE_PATH" 2>/dev/null | wc -l)
  [ -n "$jout" ] && jrows=$jout
  emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" \
    "L5: evidencia preservada (${shot}); reconciliación con journal: ${jrows} filas (incl. header); JAMÁS re-enviar decision_id/trade_id; rotando L1→L2→L3" \
    '{"watchdog":"RECOVERING_L5"}' '{"watchdog":"RECOVERING_L5","rotating":true}' \
    "{\"journal_rows\":${jrows},\"screencap\":\"$(jesc "$shot")\"}"
  return 5
}

find_btn_headless() { # coordenadas de btn_headless desde dump FRESCO (UI propia TD)
  local xml l b
  xml=$(ui_dump)
  l=$(printf '%s\n' "$xml" | grep "package=\"$PKG_TD\"" | grep "resource-id=\"$PKG_TD:id/btn_headless\"" | head -1)
  [ -n "$l" ] || return 1
  b=$(printf '%s\n' "$l" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  [ -n "$b" ] || return 1
  b="${b#bounds=\"}"; b="${b%\"}"
  [[ "$b" =~ \[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\] ]] || return 1
  echo "$(( (BASH_REMATCH[1] + BASH_REMATCH[3]) / 2 )) $(( (BASH_REMATCH[2] + BASH_REMATCH[4]) / 2 ))"
}

next_level() { # rotación L1→L2→L3→L4→L5→L1 (NUNCA abandona)
  case "$1" in
    L1) echo "L2";; L2) echo "L3";; L3) echo "L4";; L4) echo "L5";; *) echo "L1";;
  esac
}

# =========================================== arranque de incidente
JOURNAL_DEVICE_PATH="/sdcard/Android/data/com.example.tradedraw/files/TradeDraw_Audits/trade_journal.csv"

run_recovery_level() { # ejecuta la acción del nivel actual (dispatch de recover_L*)
  INC_LEVEL="$1"
  case "$1" in
    L1) recover_L1;;
    L2) recover_L2;;
    L3) recover_L3;;
    L4) recover_L4;;
    L5) recover_L5;;
    *)  recover_L1;;   # nivel desconocido: volver al inicio de la escalera
  esac
}

INCIDENT_TS=""; INCIDENT_ATTEMPTS=0; BACKOFF_UNTIL=0; L4_HUNG_COUNT=0; INC_L2_TS=""

start_incident() {
  INCIDENT_TS="$(now_ms)"; INCIDENT_ATTEMPTS=0; BACKOFF_UNTIL=0; L4_HUNG_COUNT=0
  INC_ID="inc-${INCIDENT_TS}-$((RANDOM % 9000 + 1000))"
  INC_T_DISC="$(now_ms)"; INC_T_RECO=0; INC_T_ACT=0; INC_T_VER=0; INC_LEVEL="L0"
  incident_save
  emit_event "WS_DISCONNECT" "UNVERIFIED" \
    "Incidente ${INC_ID} abierto: 2+ ciclos malos consecutivos (anti-flap); recovery L1..L5 en el próximo ciclo; jamás tap a ciego" \
    "{\"watchdog\":\"${LAST_PREV_STATE}\"}" '{"watchdog":"DISCONNECTED"}' \
    "{\"bridge_ok\":$([ "${BRIDGE_OK:-0}" = "1" ] && echo true || echo false),\"td_pid\":\"${TD_PID:-}\",\"broker_pid\":\"${BROKER_PID:-}\",\"hasPendingTrade\":\"${S_PENDING:-}\",\"no_duplication_watch\":true}"
}

# =========================================== estado json (heartbeat)
write_state() {
  local now age tmp
  now=$(now_ms); age=$(( (now - STATE_SINCE) / 1000 ))
  tmp="${STATE_FILE}.tmp"
  printf '{"watchdog":"r31_watchdog","timestamp_utc":"%s","session_id":"%s","cycle":%s,"state":"%s","state_since_utc":"%s","state_age_s":%s,"transport":"%s","counters":{"reconexiones_ok":%s,"reconexiones_fail":%s,"tiempo_desconectado_acum_s":%s,"ciclos":%s},"escalate":%s,"escalate_reason":"%s","fsm_level":"%s","incident":{"attempts":%s,"max":%s,"id":"%s","mttd_ms":%s,"mttr_ms":%s},"demo_state":"%s","trade_in_flight":%s,"battery":{"level":%s,"powered":%s},"last_status":{"mode":"%s","isDemoAccount":"%s","orderState":"%s","lastTickAgeMs":%s,"connected":"%s","hasPendingTrade":"%s","balance":%s,"versionCode":%s},"pids":{"td":"%s","broker":"%s"}}\n' \
    "$(iso_utc "$now")" "$WD_SESSION_ID" "$C_CYCLES" "$STATE" "$(iso_utc "$STATE_SINCE")" "$age" "${TRANSPORT_USED:-none}" \
    "$C_RECO_OK" "$C_RECO_FAIL" "$C_DISCONNECT_S" "$C_CYCLES" \
    "$( [ "$ESCALATED" != "0" ] && echo true || echo false )" "$(jesc "$ESCALATE_REASON")" "${INC_LEVEL:-L0}" \
    "$INCIDENT_ATTEMPTS" "$MAX_RECOVER" "$(jesc "${INC_ID:-}")" "${INC_MTTD:-0}" "${INC_MTTR:-0}" \
    "${DEMO_STATE:-DEMO_UNKNOWN}" "$( [ "${TRADE_IN_FLIGHT:-0}" = "1" ] && echo true || echo false )" \
    "${BATT_LEVEL:--1}" "${BATT_POWERED:-0}" \
    "${S_MODE:-}" "${S_DEMO:-}" "${S_ORDER:-}" "${S_TICK:--1}" "${S_CONNECTED:-}" "${S_PENDING:-}" "${S_BALANCE:-null}" "${S_VCODE:-null}" \
    "${TD_PID:-}" "${BROKER_PID:-}" > "$tmp"
  mv -f "$tmp" "$STATE_FILE"
}

# =========================================== señales + init (set -u safe)
on_exit() {
  write_state
  echo "r31_watchdog: salida en estado $STATE (ciclos=$C_CYCLES) incidente=${INC_ID:-∅}" >&2
  exit 0
}
trap on_exit INT TERM

TRANSPORT_USED="none"; BATT_LEVEL=-1; BATT_POWERED=0
S_MODE=""; S_DEMO=""; S_HEALTH=""; S_TICK=""; S_CONNECTED=""; S_PENDING=""; S_ORDER=""
S_STAKE=""; S_TWINS=0; S_TLOSSES=0; S_SIG_GEN=0; S_SIG_EVAL=0; S_BALANCE=""
S_VCODE=""; S_VNAME=""; APP_VERSION="unknown"
TD_PID=""; BROKER_PID=""; FOCUS=""
DEMO_STATE="DEMO_UNKNOWN"; TRADE_IN_FLIGHT=0; DEMO_ALERTED=0; BATT_ALERTED=0
LAST_HEALTH_EVENT=0; BAD_STREAK=0; DEGR_STREAK=0; PREV_SLEEP=0
CYCLES_THIS_SESSION=0   # ciclos de ESTA sesión (MAX_CYCLES no cuenta los heredados)
LAST_DEMO_FRESH_TS=0
INC_T_DISC=0; INC_T_RECO=0; INC_T_ACT=0; INC_T_VER=0; INC_ID=""; INC_LEVEL="L0"
INC_MTTD=0; INC_MTTR=0
incident_load
# Si había un incidente ABIERTO (con recovery ya iniciado), retomarlo para que el
# guardian pueda responder approved_<incident>.json aunque el daemon se relance.
if [ -n "$INC_ID" ] && [ "${INC_T_RECO:-0}" != "0" ] && [ "${INC_T_VER:-0}" = "0" ]; then
  INCIDENT_TS="${INC_T_DISC}"; INCIDENT_ATTEMPTS=1; INC_L2_TS=""
  if [ "$INC_LEVEL" != "L0" ]; then
    STATE="RECOVERING_${INC_LEVEL}"
  elif [ "$STATE" != "CONNECTED" ] && [ "$STATE" != "VERIFYING" ]; then
    STATE="RECOVERING_L1"
  fi
  BACKOFF_UNTIL=0
  printf '[r31_watchdog] incidente ABIERTO retomado: %s (nivel %s, FSM=%s)\n' "$INC_ID" "$INC_LEVEL" "$STATE" >&2
fi

# =========================================== bucle principal
echo "r31_watchdog: arranque (interval=${INTERVAL}s serial=${SERIAL} reset_escalate=${RESET_ESCALATE} visual=${VISUAL_TASK_DIR} dry_run=${R31_DRY_RUN:-0})" >&2

while :; do
  C_CYCLES=$((C_CYCLES + 1))
  CYCLE_START=$(now_ms)

  # ---- (1) multimodal: bridge + pids + foco + batería
  BRIDGE_OK=0
  if fetch_status && [ "$(jstr '"app"' "$STATUS_BODY")" = "TradeDraw" ]; then
    read_status_fields
    BRIDGE_OK=1
  fi
  TD_PID=$(get_td_pid); BROKER_PID=$(get_broker_pid)
  FOCUS=$(get_focus)
  read -r BATT_LEVEL BATT_POWERED <<< "$(get_battery)" || true

  # ---- (2) señales
  PROCS_OK=1; { [ -n "$TD_PID" ] && [ -n "$BROKER_PID" ]; } || PROCS_OK=0
  FOCUS_OK=1; focus_is_broker "$FOCUS" || FOCUS_OK=0
  TICK_FRESH=1
  { [ -z "$S_TICK" ] || [ "$S_TICK" -lt 0 ] 2>/dev/null || [ "$S_TICK" -gt "$TICK_MAX_AGE_MS" ] 2>/dev/null; } && TICK_FRESH=0
  [ "$S_CONNECTED" = "true" ] || TICK_FRESH=0

  # ---- (3) D5 tri-estado DEMO
  DEMO_FRESH=0
  { [ "$FOCUS_OK" = "1" ] && [ -n "$S_DEMO" ]; } && DEMO_FRESH=1
  [ "$DEMO_FRESH" = "1" ] && LAST_DEMO_FRESH_TS=$(( $(now_ms) / 1000 ))
  DEMO_AGE=$(( ( $(now_ms) / 1000 ) - LAST_DEMO_FRESH_TS ))
  DEMO_FRESH_OK=0; [ "$DEMO_AGE" -le "$DEMO_STALE_S" ] && DEMO_FRESH_OK=1
  DEMO_STATE=$(classify_demo_state "${S_DEMO:-}" "$FOCUS_OK" "$DEMO_FRESH_OK" "${S_ACCTYPE:-}")
  DEMO_OK=1; [ "$DEMO_STATE" = "DEMO_CONFIRMED" ] || DEMO_OK=0

  # ---- (4) D3 order_late-aware + Cooldown Normal (cooldown normal ≠ desconexión)
  now_s=$(( $(now_ms) / 1000 ))
  IS_COOLDOWN=0
  [ "${S_COOLDOWN:-0}" -gt 0 ] 2>/dev/null && IS_COOLDOWN=1
  ORDER_READY=1
  { [ "$S_HEALTH" = "READY" ] || [ "$IS_COOLDOWN" = "1" ]; } || ORDER_READY=0
  ORDER_LATE=0
  if [ "$ORDER_READY" = "0" ] && [ "${S_PENDING:-}" != "true" ] && [ "$IS_COOLDOWN" = "0" ]; then
    if [ "${ORDER_NOT_READY_SINCE:-0}" = "0" ]; then ORDER_NOT_READY_SINCE=$now_s; fi
    [ $(( now_s - ORDER_NOT_READY_SINCE )) -gt 60 ] && ORDER_LATE=1
  else
    ORDER_NOT_READY_SINCE=0
  fi
  # D3 REGLA CENTRAL: orden en curso (pendiente + tick fresco) = TRADE_IN_FLIGHT,
  # JAMÁS produce DEGRADED/DISCONNECTED — sea health tarde o no.
  TRADE_CLASS="NONE"
  if [ "${S_PENDING:-}" = "true" ] && [ "$TICK_FRESH" = "1" ]; then
    TRADE_CLASS="TRADE_IN_FLIGHT"
  elif [ "$ORDER_LATE" = "1" ]; then
    TRADE_CLASS="ORDER_LATE"
  fi
  TRADE_IN_FLIGHT=0
  [ "$TRADE_CLASS" = "TRADE_IN_FLIGHT" ] && TRADE_IN_FLIGHT=1
  # D3 ampliado: una orden REALMENTE en curso (orderState != NINGUNA con pendiente
  # y tick fresco) es reconciliation-in-flight aunque health no la marque tarde:
  # jamás produce DEGRADED/DISCONNECTED.
  if [ "${S_PENDING:-}" = "true" ] && [ "$TICK_FRESH" = "1" ] && [ -n "${S_ORDER:-}" ] && [ "${S_ORDER:-}" != "NINGUNA" ]; then
    TRADE_IN_FLIGHT=1
  fi
  [ "$TRADE_IN_FLIGHT" = "1" ] && emit_event "HEALTH_CHECK" "UNVERIFIED" \
    "TRADE_IN_FLIGHT: orden en curso con hasPendingTrade=true y tick fresco — reconciliación en vuelo; SIN cambio de FSM (fix falso-positivo order_late R30)" \
    "{\"watchdog\":\"${STATE}\"}" "{\"watchdog\":\"${STATE}\",\"trade\":\"in_flight\"}" \
    "{\"orderState\":\"$(jesc "${S_ORDER:-}")\",\"stake\":\"${S_STAKE:-}\",\"tick_ms\":${S_TICK:--1},\"class\":\"${TRADE_CLASS}\"}"

  # ---- (5) FSM
  CANDIDATE="CONNECTED"
  { [ "$BRIDGE_OK" = "1" ] && [ "$PROCS_OK" = "1" ]; } || CANDIDATE="DISCONNECTED"
  if [ "$CANDIDATE" = "CONNECTED" ] && [ "$TRADE_IN_FLIGHT" = "0" ]; then
    { [ "$TICK_FRESH" = "0" ] || [ "$ORDER_LATE" = "1" ]; } && CANDIDATE="DEGRADED"
    [ "$FOCUS_OK" = "1" ] || CANDIDATE="DEGRADED"
  fi

  if [ "$CANDIDATE" = "DISCONNECTED" ]; then
    BAD_STREAK=$((BAD_STREAK + 1)); DEGR_STREAK=0
    { [ "$BAD_STREAK" -ge 2 ] && [ "$STATE" != "DISCONNECTED" ] && [[ "$STATE" != RECOVERING_* ]]; } && {
      set_state "DISCONNECTED"; start_incident
    }
  elif [ "$CANDIDATE" = "DEGRADED" ]; then
    DEGR_STREAK=$((DEGR_STREAK + 1)); BAD_STREAK=0
    [ "$STATE" = "CONNECTED" ] && set_state "DEGRADED"
    if [ "$DEGR_STREAK" -ge 2 ]; then
      # anti-flap: 2 ciclos DEGRADED consecutivos → DISCONNECTED (desde CUALQUIER
      # estado no-CONNECTED, incl. INIT/VERIFYING; fix del atasco INIT+DEGRADED)
      [ "$STATE" != "CONNECTED" ] || set_state "DEGRADED"
      set_state "DISCONNECTED"
      start_incident
    fi
  else
    BAD_STREAK=0; DEGR_STREAK=0
    if [[ "$STATE" == RECOVERING_* ]] || [ "$STATE" = "DISCONNECTED" ]; then
      # self-heal o acción previa funcionó: registrar y verificar (gates arriba)
      # REGLA de custodia L2: si hay visual task sin respuesta del guardian, NO
      # marcar RECOVERY_OK todavía — solo transitar a VERIFYING sin cerrar nada
      # (el incidente queda abierto hasta el gate VERIFYING→CONNECTED).
      if [ "$STATE" = "DISCONNECTED" ] && visual_task_pending && ! visual_task_stale; then
        emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "Chequeos sanos pero visual task PENDIENTE del guardian: no se cierra el incidente; VERIFYING condicionado" \
          '{"watchdog":"DISCONNECTED"}' '{"watchdog":"VERIFYING","pending_approval":true}' '{}'
        set_state "VERIFYING"
        [ "$STATE" = "VERIFYING" ] || true
        PREV_SLEEP="$NEXT_SLEEP"
        continue
      fi
      emit_event "RECOVERY_OK" "VERIFIED" "Chequeos sanos de nuevo; VERIFYING (gate completo antes de CONNECTED)" \
        "{\"watchdog\":\"${STATE}\"}" '{"watchdog":"VERIFYING"}' \
        "{\"outage_s\":$(( ( $(now_ms) - ${INCIDENT_TS:-$(now_ms)} ) / 1000 ))}"
      [ "$STATE" = "DISCONNECTED" ] || C_RECO_OK=$((C_RECO_OK + 1))
      [ -n "$INCIDENT_TS" ] && C_DISCONNECT_S=$(( C_DISCONNECT_S + ( $(now_ms) - INCIDENT_TS ) / 1000 ))
      [ "${INC_T_VER:-0}" = "0" ] && { INC_T_VER="$(now_ms)"; incident_save; }
      set_state "VERIFYING"
    elif [ "$STATE" = "VERIFYING" ]; then
      if [ "$TICK_FRESH" = "1" ] && [ "$DEMO_STATE" = "DEMO_CONFIRMED" ] && [ "$FOCUS_OK" = "1" ] && { [ "$ORDER_READY" = "1" ] || [ "$IS_COOLDOWN" = "1" ]; }; then
        # ---- gate VERIFYING→CONNECTED + clear escalate SIEMPRE
        if [ "$ESCALATED" != "0" ]; then
          ESCALATED=0; ESCALATE_REASON=""
          emit_event "HEALTH_CHECK" "VERIFIED" "ESCALATE limpiado en VERIFYING→CONNECTED (fix latch R30)" \
            '{"escalate":true}' '{"escalate":false}' '{}'
        fi
        set_state "CONNECTED"
        emit_event "HEALTH_CHECK" "VERIFIED" "VERIFYING→CONNECTED: gate completo (bridge 200 + foco Binomo + tick fresco + DEMO_CONFIRMED + orderState limpio)" \
          '{"watchdog":"VERIFYING"}' '{"watchdog":"CONNECTED"}' \
          "{\"reconexiones_ok\":${C_RECO_OK},\"tick_ms\":${S_TICK:--1},\"mode\":\"${S_MODE:-}\",\"demo_state\":\"${DEMO_STATE}\"}"
        if [ -n "$INCIDENT_TS" ]; then
          [ "${INC_T_VER:-0}" = "0" ] && INC_T_VER="$(now_ms)"
          incident_save
          INC_MTTD=$(( ${INC_T_RECO:-0} > 0 ? INC_T_RECO - INC_T_DISC : 0 ))
          INC_MTTR=$(( ${INC_T_VER:-0} - ${INC_T_DISC:-0} ))
          [ "$INC_MTTD" -lt 0 ] 2>/dev/null && INC_MTTD=0
          [ "$INC_MTTR" -lt 0 ] 2>/dev/null && INC_MTTR=0
          incident_record 1
          incident_reset
          INCIDENT_TS=""; INCIDENT_ATTEMPTS=0; INC_LEVEL="L0"; INC_MTTD=0; INC_MTTR=0
        fi
      else
        set_state "DISCONNECTED"; start_incident
      fi
    elif [ "$STATE" != "CONNECTED" ]; then
      # ---- FIX DEL DEFECTO R30 (D2): INIT→CONNECTED DEBE limpiar escalate
      if [ "$ESCALATED" != "0" ]; then
        ESCALATED=0; ESCALATE_REASON=""
        emit_event "HEALTH_CHECK" "VERIFIED" "INIT→CONNECTED con checks sanos: ESCALATE heredado del state file LIMPIADO (fix defecto R30; --reset-escalate no requerido)" \
          '{"escalate":true}' '{"escalate":false}' '{}'
      fi
      set_state "CONNECTED"
      emit_event "HEALTH_CHECK" "VERIFIED" "INIT→CONNECTED: checks sanos en arranque" \
        "{\"watchdog\":\"${LAST_PREV_STATE}\"}" '{"watchdog":"CONNECTED"}' '{}'
    fi
  fi

  # ---- (6) recovery L1..L5 (una acción por ciclo; ESCALATE no suprime, solo notifica)
  # GATE DE ENTRADA: DISCONNECTED con backoff vencido → RECOVERING_L1 (arranque de escalera)
  if [ "$STATE" = "DISCONNECTED" ]; then
    if [ "$(( $(now_ms) / 1000 ))" -ge "$BACKOFF_UNTIL" ]; then
      set_state "RECOVERING_L1"
      emit_event "RECOVERY_ROTATE" "UNVERIFIED" "DISCONNECTED → RECOVERING_L1 (inicio de escalera de recuperación; jamás tap a ciego)" \
        '{"watchdog":"DISCONNECTED"}' '{"watchdog":"RECOVERING_L1"}' '{}'
    fi
  fi
  if [[ "$STATE" == RECOVERING_* ]]; then
    if [ "$ESCALATED" != "0" ]; then
      emit_event "ESCALATE_NOTIFY" "UNVERIFIED" "ESCALATE activo (${ESCALATE_REASON:-}): requiere estrategia superior; el watchdog SIGUE rotando L1..L5 (no abandona)" \
        '{"escalate":true}' '{"escalate":true,"notified":true}' '{}'
      ESCALATED=2   # 2 = notificado; no re-notificar cada ciclo
    fi
    if [ "$(( $(now_ms) / 1000 ))" -ge "$BACKOFF_UNTIL" ]; then
      LEVEL="${STATE#RECOVERING_}"
      INCIDENT_ATTEMPTS=$((INCIDENT_ATTEMPTS + 1))
      [ "${INC_T_RECO:-0}" = "0" ] && { INC_T_RECO="$(now_ms)"; incident_save; }
      # límite de seguridad: MAX_RECOVER intentos → ESCALATE_NOTIFY y ROTACIÓN CONTINUA
      # (D2: escalate ya NO congela; solo anuncia "requiere estrategia superior")
      if [ "$INCIDENT_ATTEMPTS" -gt "$MAX_RECOVER" ]; then
        if [ "$ESCALATED" = "0" ]; then
          ESCALATED=1; ESCALATE_REASON="MAX_RECOVERY_EXHAUSTED (${MAX_RECOVER} intentos por ciclo; incidente ${INC_ID:-?}); rotación continúa"
          emit_event "ESCALATE_NOTIFY" "UNVERIFIED" "Presupuesto de intentos agotado para el incidente ${INC_ID:-?}: ESCALATE=requiere estrategia superior; el watchdog SIGUE rotando (jamás abandona)" \
            '{"escalate":false}' '{"escalate":true}' "{\"attempts\":${INCIDENT_ATTEMPTS}}"
        fi
      fi
      run_recovery_level "$LEVEL"
      RC=$?
      case "$RC" in
        0)
          # acción ejecutada → verificar AHORA (full_recheck); VERIFYING→CONNECTED en próximo ciclo
          if full_recheck; then
            C_RECO_OK=$((C_RECO_OK + 1))
            [ -n "$INCIDENT_TS" ] && C_DISCONNECT_S=$(( C_DISCONNECT_S + ( $(now_ms) - INCIDENT_TS ) / 1000 ))
            set_state "VERIFYING"
            emit_event "RECOVERY_OK" "VERIFIED" "Recovery ${LEVEL} verificada (bridge+focus+tick+DEMO+health); VERIFYING" \
              "{\"watchdog\":\"RECOVERING_${LEVEL}\"}" '{"watchdog":"VERIFYING"}' \
              "{\"attempt\":${INCIDENT_ATTEMPTS},\"outage_s\":$(( ( $(now_ms) - ${INCIDENT_TS:-$(now_ms)} ) / 1000 ))}"
          else
            set_state "DISCONNECTED"
            emit_event "RECOVERY_FAIL" "UNVERIFIED" "Recovery ${LEVEL} ejecutada pero re-check falló; DISCONNECTED para rotar en próximo ciclo" \
              "{\"watchdog\":\"RECOVERING_${LEVEL}\"}" '{"watchdog":"DISCONNECTED"}' "{\"attempt\":${INCIDENT_ATTEMPTS}}"
          fi
          LOCAL_DELAY=$(backoff_delay "$INCIDENT_ATTEMPTS")
          BACKOFF_UNTIL=$(( $(now_ms) / 1000 + LOCAL_DELAY ))
          ;;
        2)
          # FIX D1/D4: fallback L1→L2 EN EL MISMO CICLO (el dump falló; la escalera no se detiene)
          emit_event "RECOVERY_ROTATE" "UNVERIFIED" "Recovery L1 sin acción eficaz (rc=2); ejecutando L2 (visual) EN EL MISMO ciclo" \
            '{"watchdog":"RECOVERING_L1"}' '{"watchdog":"RECOVERING_L2"}' "{\"attempt\":${INCIDENT_ATTEMPTS},\"rc\":${RC}}"
          set_state "RECOVERING_L2"
          run_recovery_level "L2"
          RC2=$?
          case "$RC2" in
            0)
              if full_recheck; then
                C_RECO_OK=$((C_RECO_OK + 1))
                set_state "VERIFYING"
                emit_event "RECOVERY_OK" "VERIFIED" "Recovery L2 (mismo ciclo que L1) verificada; VERIFYING" \
                  '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"VERIFYING"}' "{\"attempt\":${INCIDENT_ATTEMPTS}}"
              else
                set_state "DISCONNECTED"
                emit_event "RECOVERY_FAIL" "UNVERIFIED" "Recovery L2 (mismo ciclo) ejecutada; re-check falló" \
                  '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"DISCONNECTED"}' "{\"attempt\":${INCIDENT_ATTEMPTS}}"
              fi
              ;;
            3)
              visual_archive_task "${INC_ID:-unknown}" "${INC_L2_TS:-}" 0
              set_state "RECOVERING_L3"
              emit_event "RECOVERY_ROTATE" "UNVERIFIED" "Guardian RECHAZÓ (mismo ciclo); L2→L3 inmediato" \
                '{"watchdog":"RECOVERING_L2"}' '{"watchdog":"RECOVERING_L3"}' "{\"attempt\":${INCIDENT_ATTEMPTS}}"
              ;;
            *) : ;;   # L2 creó task o espera: ciclo terminado; backoff normal abajo
          esac
          LOCAL_DELAY=$(backoff_delay 1)
          BACKOFF_UNTIL=$(( $(now_ms) / 1000 + LOCAL_DELAY ))
          ;;
        *)
          # sin acción eficaz: rotar nivel (rc=5 → L1) y backoff
          NEXTL=$(next_level "$LEVEL")
          [ "$RC" = "5" ] && NEXTL="L1"
          LOCAL_DELAY=$(backoff_delay "$INCIDENT_ATTEMPTS")
          BACKOFF_UNTIL=$(( $(now_ms) / 1000 + LOCAL_DELAY ))
          set_state "RECOVERING_${NEXTL}"
          emit_event "RECOVERY_ROTATE" "UNVERIFIED" "Recovery ${LEVEL} sin éxito (rc=${RC}); rotando a ${NEXTL} con backoff ${LOCAL_DELAY}s" \
            "{\"watchdog\":\"RECOVERING_${LEVEL}\"}" "{\"watchdog\":\"RECOVERING_${NEXTL}\"}" "{\"attempt\":${INCIDENT_ATTEMPTS},\"rc\":${RC}}"
          ;;
      esac
    fi
  fi

  # ---- (7) D5: alarma REAL_CONFIRMED (única que alarma)
  if [ "$DEMO_STATE" = "REAL_CONFIRMED" ] && [ "$DEMO_ALERTED" = "0" ]; then
    DEMO_ALERTED=1
    if [ "$ESCALATED" = "0" ]; then
      ESCALATED=1; ESCALATE_REASON="DEMO_FLIP isDemoAccount=false con Binomo EN foreground y lectura fresca (REAL_CONFIRMED)"
    fi
    emit_event "DEMO_FLIP" "FAILED" "REAL_CONFIRMED: isDemoAccount=false con foco Binomo y lectura fresca — alarma maxima de cuenta REAL (sin opcion de desarme: la pausa la decide el coordinador)" \
      "{\"demo\":\"${DEMO_STATE}\"}" '{"demo":"REAL_CONFIRMED"}' "{\"mode\":\"${S_MODE:-}\"}"
  fi
  [ "$DEMO_STATE" = "REAL_CONFIRMED" ] || DEMO_ALERTED=0

  # ---- (8) batería
  if [ "${BATT_LEVEL:--1}" -ge 0 ] 2>/dev/null && [ "$BATT_LEVEL" -lt "$BATTERY_MIN" ] && [ "$BATT_ALERTED" = "0" ]; then
    BATT_ALERTED=1
    emit_event "ERROR" "VERIFIED" "Batería ${BATT_LEVEL}% < umbral ${BATTERY_MIN}% (ESCALATE_BATTERY; la pausa la decide el coordinador)" \
      "{\"battery\":{\"level_ge\":\"${BATTERY_MIN}\"}}" "{\"battery\":{\"level\":${BATT_LEVEL}}}" \
      "{\"level\":${BATT_LEVEL},\"powered\":${BATT_POWERED},\"mode\":\"${S_MODE:-}\"}"
    printf '{"suggested_action":"POST /mode?name=DISABLED","reason":"BATTERY_LOW","level":%s,"decided_by":"coordinador","ts_utc":"%s"}\n' \
      "$BATT_LEVEL" "$(iso_utc "$(now_ms)")" > "${EVID_DIR}/PAUSE_REQUESTED_$(date -u +%Y%m%dT%H%M%SZ)_battery.json"
  fi
  [ "${BATT_LEVEL:--1}" -ge $(( BATTERY_MIN + 5 )) ] 2>/dev/null && BATT_ALERTED=0

  # ---- dwell DISCONNECTED con intervalo real (F4 heredado)
  [ "$STATE" = "DISCONNECTED" ] && C_DISCONNECT_S=$(( C_DISCONNECT_S + PREV_SLEEP ))

  # ---- heartbeat + HEALTH_CHECK periódico
  NOW_MS_V=$(now_ms)
  if [ "$STATE" = "CONNECTED" ] && [ $(( NOW_MS_V - LAST_HEALTH_EVENT )) -ge $(( HEALTH_EVENT_S * 1000 )) ]; then
    LAST_HEALTH_EVENT=$NOW_MS_V
    emit_event "HEALTH_CHECK" "VERIFIED" "Ciclo estable: bridge 200, procesos vivos, Binomo en focus, feed fresco, ${DEMO_STATE}, orderState CLEAN" \
      "{\"watchdog\":\"${STATE}\"}" "{\"watchdog\":\"${STATE}\"}" \
      "{\"cycle\":${C_CYCLES},\"tick_ms\":${S_TICK:--1},\"battery\":${BATT_LEVEL:--1},\"mode\":\"${S_MODE:-}\",\"transport\":\"${TRANSPORT_USED:-none}\",\"fsm_level\":\"${INC_LEVEL}\"}"
  fi
  write_state

  # ---- fin de ciclo
  # NOTA: MAX_CYCLES cuenta ciclos DE ESTA SESIÓN (no del contador heredado del
  # state file, que es la vida completa del daemon) — fix de testeabilidad.
  CYCLES_THIS_SESSION=$(( CYCLES_THIS_SESSION + 1 ))
  [ "$ONCE" = "1" ] && break
  { [ "$MAX_CYCLES" -gt 0 ] 2>/dev/null && [ "$CYCLES_THIS_SESSION" -ge "$MAX_CYCLES" ]; } && break
  NEXT_SLEEP="$INTERVAL"
  [ "${S_PENDING:-}" = "true" ] && NEXT_SLEEP="$FAST_INTERVAL"
  ELAPSED=$(( ( $(now_ms) - CYCLE_START ) / 1000 ))
  REMAIN=$(( NEXT_SLEEP - ELAPSED ))
  [ "$REMAIN" -gt 0 ] 2>/dev/null && sleep "$REMAIN" || true
  PREV_SLEEP=$NEXT_SLEEP
done
on_exit
