#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# r30_watchdog.sh — LIVE WATCHDOG daemon (FASE 4) — Ronda 4 prep
#
# Materialización ejecutable de loops/r30_live/R30_WATCHDOG_RUNBOOK.md.
# Corre como daemon DURANTE la sesión live; lo lanza el coordinador tras READY.
# NO commitear todavía: queda sin rastrear; el commit vendrá con evidencia.
#
# QUÉ HACE (por ciclo):
#   (a) Lee /status del bridge por el transporte correcto:
#       PRIMARIO   http://127.0.0.1:18080  (forward tcp:18080, verificado en
#       baseline desde Termux; BASELINE_DEVICE_R30.md)
#       FALLBACK   http://[::1]:8080       (SOLO válido si el supervisor creó
#       el forward tcp:8080; baseline: exit 7 sin él). El intento es siempre
#       primario→fallback; si ambos fallan, bridge=DOWN en ese ciclo.
#   (b) Máquina de estados (runbook §3):
#       CONNECTED / DEGRADED / DISCONNECTED / RECOVERING / VERIFYING.
#       CONNECTED→DISCONNECTED exige 2 ciclos malos consecutivos (anti-flap).
#   (c) Ante DISCONNECTED de Binomo: screencap+timestamp a
#       loops/r30_live/watchdog_evidence/, intenta identificar diálogo de
#       recuperación (uiautomator dump SI es legible; whitelist estricta de
#       etiquetas "Reintentar"/"Reconectar"/"Reconnect"/"Retry" + bounds
#       únicos + paquete correcto; JAMÁS tap a ciego; OK/Aceptar/Continuar
#       NUNCA se tocan). Si identifica: 1 tap → espera → verifica (contenido,
#       WS fresco, ventana activa, DEMO) → registra tiempo de recuperación →
#       VERIFYING→CONNECTED. Backoff limitado min(2^(n-1)*base,300 s),
#       máx --max-recover intentos/incidente. Si falla: marca ESCALATE y NO
#       toca nada más (solo observa).
#   (d) Batería: level cada ciclo; < --battery-min (def 25) → ESCALATE_BATTERY.
#       Si el modo es AUTONOMOUS y SIN --allow-disarm: escribe marcador
#       PAUSE_REQUESTED_*.json (la pausa la decide el coordinador). Con
#       --allow-disarm: POST /mode?name=DISABLED + verificación inmediata.
#   (e) Idempotencia post-corte: NUNCA re-dispatch ni re-arm por su cuenta. Su
#       ÚNICO POST posible es DISABLED (y solo con --allow-disarm). Tras cada
#       recuperación consulta GET /dispatch_recovery (solo lectura) y registra
#       UNCERTAIN/pendingRequestId si los hay (no actúa).
#   (f) Escribe loops/r30_live/watchdog_state.json (heartbeat: mtime se
#       actualiza CADA ciclo) + append watchdog_events.jsonl con esquema
#       compatible con loops/nights_2026-11-13/telemetry/TELEMETRY_SCHEMA.md
#       v1.0.0 (event_id UUID5, timestamp_utc/local, event_type, component,
#       prev_state/new_state, data, verification_result, source).
#
# DEDUCCIÓN DEL POST /mode EXACTO (fuente leída, no supuesta):
#   - app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt:205-214:
#       "/mode" -> { val modeStr = params["name"]?.uppercase() ?: params["mode"]?.uppercase() ?: ""
#                    ... setTradingMode(m, "bridge:/mode") ... 200 {"success":true,"mode":...}
#                    catch -> 400 {"success":false,"error":"Modo desconocido: ..."} }
#     ⇒ El modo viaja como PARÁMETRO DE QUERY `name` (o alias `mode`), NO como
#       body JSON obligatorio. `params` se llena en :74-88 desde el query
#       string; para POST con body, :103-125 hace params.putAll(parseBridgeBody)
#       (parseBridgeBody en :1138-1146 acepta JSON plano o form-urlencoded —
#       ambas formas terminarían en `params`), pero la forma MÍNIMA y ya
#       documentada en el runbook §7 es query:  /mode?name=DISABLED
#   - El `source` del cambio NO es elegible por el cliente: la llamada en
#       TradeDrawHttpBridge.kt:209 fija setTradingMode(m, "bridge:/mode").
#       Cualquier cambio por bridge queda atribuido a source=bridge:/mode en
#       lastModeChange y en files/mode_changes.jsonl (verificable: run-as tail -n 1).
#   - TradingEngine.kt:108-133: setTradingMode(m, s) con s.startsWith("bridge:")
#       → autonomousWatchdog.arm(..., bridge=true) (:126). UN POST AUTONOMOUS
#       desde este script RE-ARMARÍA el motor → POR ESO este script jamás envía
#       otro valor: su único POST es name=DISABLED (disarma :129-131).
#   - TradeDrawHttpBridge.kt:87 + TradingEngine.kt:139-143 y :857: CUALQUIER
#       request (incl. GET /status) alimenta el heartbeat R26; AUTONOMOUS por
#       bridge caduca a 10 min sin polls.
#
# LLAMADA EXACTA DE DESARME (la única que este script puede emitir):
#   curl --noproxy '*' -s -m 5 -w '%{http_code}' -X POST \
#        -o <tmp> "http://127.0.0.1:18080/mode?name=DISABLED"
#   Verificación inmediata (mismo ciclo): GET /status → "mode":"DISABLED"
#   (grep con [[:space:]]*: el espaciado del JSON es inconsistente campo a
#   campo — TradeDrawHttpBridge.kt:211 sin espacio vs :765 con espacio).
#
# USO:
#   scripts/r30_watchdog.sh [--interval 30] [--tick-max-age-ms 5000]
#     [--battery-min 25] [--max-recover 3] [--backoff-base 30]
#     [--recheck-wait 15] [--fast-interval 15] [--health-event-s 300]
#     [--serial emulator-5554] [--allow-disarm] [--once] [--max-cycles N]
#     [--evidence-dir DIR] [--state-file FILE] [--events-file FILE]
#     [--reset-escalate] [--help]
#
# REGLAS DE ORO (runbook §0/§4/§6): nunca tap a ciego; nunca reenviar
# decision_id/trade_id tras corte; jamás tap a OK/Aceptar/Continuar;
# ESCALATE = dejar de tocar, seguir observando.
# ============================================================================

set -u

# F5: TMPDIR escribible o fallback $HOME (Termux: /tmp literal NO es escribible;
# los cuerpos temporales de /status, /mode y /dispatch_recovery se escriben en TMPDIR).
TMPDIR="${TMPDIR:-$HOME}"
[ -d "$TMPDIR" ] && [ -w "$TMPDIR" ] || TMPDIR="$HOME"

# ----------------------------------------------------------------- constantes
NS_UUID="6b1f2a55-0d4e-4a17-9d0e-2b6c8a11f0e1"   # namespace TELEMETRY_SCHEMA.md §1
COMPONENT="WATCHDOG"      # NOTE: TELEMETRY_SCHEMA.md §7 reserva `supervisor`/
                          # `ws_transport` para estos tipos; el encargo Ronda 4
                          # fija component=WATCHDOG. Cambiar AQUÍ si el
                          # coordinador prefiere el enum del schema.
SCHEMA_VERSION="1.0.0"
SRC_REL="scripts/r30_watchdog.sh"
PKG_TD="com.example.tradedraw"
PKG_BROKER="com.marketly.trading"
URL_PRIMARY="http://127.0.0.1:18080"
URL_FALLBACK="http://[::1]:8080"
LABEL_RE='(Reintentar|Reintentar conexión|reintentar conexión|Reconectar|Reconnect|Retry)'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ----------------------------------------------------------------- defaults
INTERVAL=30
TICK_MAX_AGE_MS=5000
BATTERY_MIN=25
MAX_RECOVER=3
BACKOFF_BASE=30
RECHECK_WAIT=15
FAST_INTERVAL=15
HEALTH_EVENT_S=300
SERIAL="emulator-5554"
ALLOW_DISARM=0
ONCE=0
MAX_CYCLES=0            # 0 = ilimitado
RESET_ESCALATE=0
EVID_DIR="${ROOT}/loops/r30_live/watchdog_evidence"
STATE_FILE="${ROOT}/loops/r30_live/watchdog_state.json"
EVENTS_FILE="${ROOT}/loops/r30_live/watchdog_events.jsonl"

usage() { sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [ $# -gt 0 ]; do
  case "$1" in
    --interval)        INTERVAL="$2"; shift 2;;
    --tick-max-age-ms) TICK_MAX_AGE_MS="$2"; shift 2;;
    --battery-min)     BATTERY_MIN="$2"; shift 2;;
    --max-recover)     MAX_RECOVER="$2"; shift 2;;
    --backoff-base)    BACKOFF_BASE="$2"; shift 2;;
    --recheck-wait)    RECHECK_WAIT="$2"; shift 2;;
    --fast-interval)   FAST_INTERVAL="$2"; shift 2;;
    --health-event-s)  HEALTH_EVENT_S="$2"; shift 2;;
    --serial)          SERIAL="$2"; shift 2;;
    --allow-disarm)    ALLOW_DISARM=1; shift;;
    --once)            ONCE=1; shift;;
    --max-cycles)      MAX_CYCLES="$2"; shift 2;;
    --reset-escalate)  RESET_ESCALATE=1; shift;;
    --evidence-dir)    EVID_DIR="$2"; shift 2;;
    --state-file)      STATE_FILE="$2"; shift 2;;
    --events-file)     EVENTS_FILE="$2"; shift 2;;
    -h|--help)         usage;;
    *) echo "r30_watchdog: flag desconocido: $1" >&2; exit 2;;
  esac
done

mkdir -p "$EVID_DIR" "$(dirname "$STATE_FILE")" "$(dirname "$EVENTS_FILE")"

# ----------------------------------------------------------------- utilidades
now_ms() { local t; t=$(date +%s%3N 2>/dev/null); case "$t" in *N) date +%s000;; *) echo "$t"; esac; }
iso_utc()   { date -u -d "@$(( $1 / 1000 ))" '+%Y-%m-%dT%H:%M:%S.000Z' 2>/dev/null || date -u '+%Y-%m-%dT%H:%M:%SZ'; }
iso_local() { # offset fijo -05:00 (dispositivo UTC-5, TELEMETRY_SCHEMA.md §1/§3)
  date -u -d "@$(( $1 / 1000 - 18000 ))" '+%Y-%m-%dT%H:%M:%S-05:00' 2>/dev/null || date -u '+%Y-%m-%dT%H:%M:%S-05:00'; }
jesc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr '\n\t\r' '   '; }

hex2bin() { # $1 = hex string → bytes por stdout
  local h="$1" out="" i
  for ((i=0; i<${#h}; i+=2)); do out+="\\x${h:i:2}"; done
  printf "$out"
}
uuid5() { # uuid5 <namespace-uuid> <name>  (RFC 4122 §4.3, SHA-1; paridad con python3 uuid.uuid5 verificada en harness)
  local ns="${1//-/}" name="$2" d var
  d=$( { hex2bin "$ns"; printf '%s' "$name"; } | sha1sum | cut -d' ' -f1 )
  var=$(( (16#${d:16:1} & 3) | 8 ))
  # OJO formato: %x consume $var (4ª conversión); los dos %s siguientes toman
  # ${d:17:3} y ${d:20:12}. Un %s extra aquí corrompe el UUID (bug ronda 4 corregido).
  printf '%s-%s-%s-%x%s-%s' "${d:0:8}" "${d:8:4}" "5${d:13:3}" "$var" "${d:17:3}" "${d:20:12}"
}

WD_SESSION_SEED="$(now_ms)-${SERIAL}"
WD_SESSION_ID="sess-$(uuid5 "$NS_UUID" "watchdog-session:${WD_SESSION_SEED}" | tr -d '-' | cut -c1-8)"

emit_event() { # emit_event <event_type> <verification_result> <verification_detail> <prev_state_obj> <new_state_obj> <data_obj>
  local et="$1" vr="$2" vd="$3" ps="$4" ns="$5" dj="$6" ts eid natural srcline line
  ts=$(now_ms)
  srcline=$(caller 0 | awk '{print $1}')
  natural="{\"event_type\":\"$(jesc "$et")\",\"component\":\"$(jesc "$COMPONENT")\",\"ts_ms\":${ts},\"file\":\"${SRC_REL}\",\"line\":${srcline}}"
  eid=$(uuid5 "$NS_UUID" "$natural")
  line=$(printf '{"event_id":"%s","timestamp_utc":"%s","timestamp_local":"%s","event_type":"%s","component":"%s","prev_state":%s,"new_state":%s,"data":%s,"app_version":"%s","session_id":"%s","verification_result":"%s","verification_detail":"%s","schema_version":"%s","source":{"file":"%s","line":%s}}' \
    "$eid" "$(iso_utc "$ts")" "$(iso_local "$ts")" "$et" "$COMPONENT" "$ps" "$ns" "$dj" \
    "$(jesc "${APP_VERSION:-unknown}")" "$WD_SESSION_ID" "$vr" "$(jesc "$vd")" "$SCHEMA_VERSION" "$SRC_REL" "$srcline")
  printf '%s\n' "$line" >> "$EVENTS_FILE"
}

# ----------------------------------------------------------------- estado persistente
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
[ "$RESET_ESCALATE" = "1" ] && { ESCALATED=0; ESCALATE_REASON=""; }

LAST_PREV_STATE="INIT"
set_state() { # set_state <nuevo_estado> — deja LAST_PREV_STATE para el evento de transición
  [ "$1" = "$STATE" ] && return 0
  local prev="$STATE"
  STATE="$1"; STATE_SINCE="$(now_ms)"
  LAST_PREV_STATE="$prev"
  printf '[r30_watchdog] estado %s -> %s\n' "$prev" "$STATE" >&2
}

# ----------------------------------------------------------------- dispositivo
adb_sh() { adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

fetch_status() { # → 0 si 200; setea STATUS_BODY + TRANSPORT_USED
  local code
  STATUS_BODY="${TMPDIR:-/tmp}/r30_wd_status_body.json"
  TRANSPORT_USED="$URL_PRIMARY"
  code=$(curl --noproxy '*' -s -m 5 -w '%{http_code}' -o "$STATUS_BODY" "${URL_PRIMARY}/status" 2>/dev/null)
  if [ "$code" = "200" ]; then return 0; fi
  TRANSPORT_USED="$URL_FALLBACK"
  code=$(curl --noproxy '*' -s -m 5 -w '%{http_code}' -o "$STATUS_BODY" "${URL_FALLBACK}/status" 2>/dev/null)
  if [ "$code" = "200" ]; then return 0; fi
  TRANSPORT_USED="none"
  return 1
}

jstr() { # jstr <"clave"> <archivo> → valor string sin comillas ('' si ausente)
  grep -oE "$1[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$2" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*"//; s/"$//'
}
jbool() { grep -oE "$1[[:space:]]*:[[:space:]]*(true|false)" "$2" 2>/dev/null | head -1 | grep -oE '(true|false)$'; }
jnum()  { grep -oE "$1[[:space:]]*:[[:space:]]*(-?[0-9]+)" "$2" 2>/dev/null | head -1 | grep -oE -- '-?[0-9]+$'; }

read_status_fields() { # parsea STATUS_BODY → variables S_*
  S_MODE=$(jstr '"mode"' "$STATUS_BODY")
  S_DEMO=$(jbool '"isDemoAccount"' "$STATUS_BODY")
  S_ORDER=$(jstr '"orderState"' "$STATUS_BODY")
  # F1: S_ORDER_OK = health.state del /status (HealthSupervisor.kt:42 → READY|DEGRADED|BLOCKED).
  # NOTA de evidencia: "CLEAR" NO existe en /status — CLEAR/AWAITING_BROKER_CONFIRMATION/
  # UNCERTAIN son del /dispatch_recovery (TradeDrawHttpBridge.kt:277, ruta ":247). Capturas
  # reales loops/r30_live/status_t1|t2.json: "health":{"state":"READY"} + orderState NINGUNA.
  # El gate de salud queda ORTOGONAL al enum OrderState del engine (solo telemetría).
  S_ORDER_OK=$(jstr '"state"' "$STATUS_BODY")
  S_TICK=$(jnum '"lastTickAgeMs"' "$STATUS_BODY")
  S_CONNECTED=$(jbool '"connected"' "$STATUS_BODY")
  S_PENDING=$(jbool '"hasPendingTrade"' "$STATUS_BODY")
  S_BALANCE=$(grep -oE '"balance":[[:space:]]*[^,}]+' "$STATUS_BODY" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*//')
  S_VCODE=$(jnum '"versionCode"' "$STATUS_BODY")
  S_VNAME=$(jstr '"versionName"' "$STATUS_BODY")
  APP_VERSION="${S_VNAME:-unknown}"
}

get_td_pid()     { adb_sh pidof "$PKG_TD" | awk '{print $1}'; }
get_broker_pid() { adb_sh pidof "$PKG_BROKER" | awk '{print $1}'; }
get_focus()      { adb_sh dumpsys window | grep -m1 'mCurrentFocus'; }

get_battery() { # → "level powered" (powered: 0/1) — patrón sin tubería escapada (runbook corrección r3 #2)
  local out level powered
  out=$(adb_sh dumpsys battery)
  level=$(printf '%s\n' "$out" | grep -E 'level' | grep -oE '[0-9]+' | head -1)
  powered=0
  printf '%s\n' "$out" | grep -Eq 'powered: true' && powered=1
  echo "${level:--1} $powered"
}

evidence_shot() { # evidence_shot <kind>
  local f ts; ts=$(date -u +%Y%m%dT%H%M%SZ)
  f="${EVID_DIR}/wd_${ts}_${1}.png"
  adb -s "$SERIAL" exec-out screencap -p > "$f" 2>/dev/null
  echo "$f"
}

ui_dump() { # → XML por stdout ('' si Binomo lo rechaza: anti-automatización, runbook §1)
  adb_sh uiautomator dump /sdcard/r30_wd_dump.xml > /dev/null 2>&1
  local x; x=$(adb_sh cat /sdcard/r30_wd_dump.xml 2>/dev/null)
  adb_sh rm -f /sdcard/r30_wd_dump.xml > /dev/null 2>&1
  printf '%s' "$x"
}

classify_dialogue() { # classify_dialogue <xml> <package>
  # → stdout: "TAP <cx> <cy> <label>"  |  "UNKNOWN <motivo>"
  # Criterios inequívocos del runbook §4: etiqueta en whitelist + bounds ÚNICOS
  # + paquete correcto. Nunca OK/Aceptar/Continuar. Sin texto legible → UNKNOWN.
  # BUG ronda 4 corregido: los dumps uiautomator (reales y del harness) pueden
  # ser UNA sola línea → el grep por línea arrastraba el documento entero y el
  # chequeo de unicidad contaba los bounds del root+"Network error"+botón
  # (ambiguo(3bounds,1labels) falso-positivo). Ahora se extraen TAGS <node ...>
  # individuales y la whitelist se aplica SOLO al atributo text= del nodo.
  local xml="$1" pkg="$2" nodes nb nlabel label bounds l t r b
  [ -n "$xml" ] || { echo "UNKNOWN dump_vacio_o_ilegible"; return 0; }
  printf '%s' "$xml" | grep -q 'could not get idle state' && { echo "UNKNOWN uiautomator_ilegible"; return 0; }
  nodes=$(printf '%s\n' "$xml" | grep -oE '<node [^>]*>' \
            | grep "package=\"$pkg\"" \
            | grep -iE "text=\"[^\"]*$LABEL_RE[^\"]*\"" || true)
  [ -n "$nodes" ] || { echo "UNKNOWN sin_dialogo_conocido"; return 0; }
  nb=$(printf '%s\n' "$nodes" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | sort -u | wc -l)
  nlabel=$(printf '%s\n' "$nodes" | grep -oiE "$LABEL_RE" | tr '[:upper:]' '[:lower:]' | sort -u | wc -l)
  if [ "$nb" -ne 1 ] || [ "$nlabel" -ne 1 ]; then echo "UNKNOWN ambiguo(${nb}bounds,${nlabel}labels)"; return 0; fi
  label=$(printf '%s\n' "$nodes" | grep -oiE "$LABEL_RE" | head -1)
  bounds=$(printf '%s\n' "$nodes" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  b="${bounds#bounds=\"}"; b="${b%\"}"
  [[ "$b" =~ \[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\] ]] || { echo "UNKNOWN bounds_ilegibles"; return 0; }
  l="${BASH_REMATCH[1]}"; t="${BASH_REMATCH[2]}"; r="${BASH_REMATCH[3]}"; b2="${BASH_REMATCH[4]}"
  echo "TAP $(( (l + r) / 2 )) $(( (t + b2) / 2 )) $label"
}

find_btn_headless() { # localiza btn_headless en dump de la UI PROPIA (legible, runbook §5.2)
  local xml l b
  xml=$(ui_dump)
  l=$(printf '%s\n' "$xml" | grep "package=\"$PKG_TD\"" | grep 'resource-id="'"$PKG_TD"':id/btn_headless"' | head -1)
  [ -n "$l" ] || return 1
  b=$(printf '%s\n' "$l" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  [ -n "$b" ] || return 1
  b="${b#bounds=\"}"; b="${b%\"}"
  [[ "$b" =~ \[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\] ]] || return 1
  echo "$(( (BASH_REMATCH[1] + BASH_REMATCH[3]) / 2 )) $(( (BASH_REMATCH[2] + BASH_REMATCH[4]) / 2 ))"
}

post_mode_disabled() { # ÚNICO POST posible; requiere ALLOW_DISARM=1. → 0 si verificado
  [ "$ALLOW_DISARM" = "1" ] || { echo "REFUSED_NO_ALLOW_DISARM" >&2; return 1; }
  local url body code
  for url in "${URL_PRIMARY}" "${URL_FALLBACK}"; do
    body="${TMPDIR:-/tmp}/r30_wd_mode_resp.json"
    code=$(curl --noproxy '*' -s -m 5 -w '%{http_code}' -X POST -o "$body" "${url}/mode?name=DISABLED" 2>/dev/null)
    [ "$code" = "200" ] || continue
    # Verificación inmediata (runbook §7): el /status debe decir DISABLED
    if fetch_status; then
      read_status_fields
      if [ "$S_MODE" = "DISABLED" ]; then
        emit_event "HEALTH_CHECK" "VERIFIED" \
          "POST /mode?name=DISABLED aceptado (source=bridge:/mode fijado por el servidor, TradeDrawHttpBridge.kt:209); /status verificado mode=DISABLED" \
          '{"mode":"AUTONOMOUS"}' '{"mode":"DISABLED"}' \
          "{\"action\":\"watchdog_disarm\",\"allow_disarm\":true,\"endpoint\":\"${url}/mode?name=DISABLED\",\"http_code\":${code}}"
        return 0
      fi
      emit_event "ERROR" "FAILED" \
        "POST /mode?name=DISABLED devolvió 200 pero /status NO refleja DISABLED (mode=${S_MODE:-?})" \
        '{"mode":"AUTONOMOUS"}' '{"mode":"unknown"}' "{\"action\":\"watchdog_disarm\",\"verify\":\"failed\",\"endpoint\":\"${url}/mode?name=DISABLED\"}"
      return 1
    fi
    return 1
  done
  emit_event "ERROR" "FAILED" \
    "POST /mode?name=DISABLED no pudo enviarse (bridge no alcanzable por ningún transporte)" \
    '{"mode":"AUTONOMOUS"}' '{"mode":"AUTONOMOUS"}' '{"action":"watchdog_disarm","verify":"unreachable"}'
  return 1
}

escalate() { # escalate <reason> — marca ESCALATE y congela ACCIONES (solo observa)
  if [ "$ESCALATED" != "1" ]; then
    ESCALATED=1; ESCALATE_REASON="$1"
    emit_event "ERROR" "UNVERIFIED" "ESCALATE: ${1}. El watchdog NO toca nada más (runbook §4/§5, encargo Ronda 4c); continúa observando." \
      '{"escalate":false}' '{"escalate":true}' "{\"reason\":\"$(jesc "$1")\",\"incident_ts\":\"$(iso_utc "$(now_ms)")\"}"
  fi
}

check_dispatch_recovery() { # solo lectura; registra incertidumbre (runbook §6); JAMÁS re-dispatch
  local body="${TMPDIR:-/tmp}/r30_wd_drec.json" code st pid_
  code=$(curl --noproxy '*' -s -m 5 -w '%{http_code}' -o "$body" "${URL_PRIMARY}/dispatch_recovery" 2>/dev/null)
  [ "$code" = "200" ] || return 0
  st=$(jstr '"state"' "$body"); pid_=$(jstr '"pendingRequestId"' "$body")
  if [ "$st" = "UNCERTAIN" ] || [ -n "$pid_" ]; then
    emit_event "ERROR" "UNVERIFIED" \
      "Despacho sin resolver tras corte (state=${st:-?} pendingRequestId=${pid_:-∅}). REGLA §6: esperar liquidación/evidencia del broker; NINGÚN reenvío automático." \
      '{"dispatch":"pending"}' "{\"dispatch\":\"${st:-unknown}\"}" \
      "{\"pendingRequestId\":\"$(jesc "${pid_:-}")\",\"state\":\"$(jesc "${st:-}")\",\"action\":\"none\"}"
  fi
}

# ----------------------------------------------------------------- verificación post-acción
full_recheck() { # tras tap/relanzamiento: contenido, WS fresco, ventana activa → 0 si todo ok
  sleep "$RECHECK_WAIT"
  fetch_status || return 1
  read_status_fields
  local td bp focus
  td=$(get_td_pid); bp=$(get_broker_pid); focus=$(get_focus)
  [ -n "$td" ] && [ -n "$bp" ] || return 1
  printf '%s' "$focus" | grep -q "$PKG_BROKER" || return 1
  [ "$S_DEMO" = "true" ] || return 1
  { [ -n "$S_TICK" ] && [ "$S_TICK" -ge 0 ] 2>/dev/null && [ "$S_TICK" -le "$TICK_MAX_AGE_MS" ] 2>/dev/null; } || return 1
  [ "$S_CONNECTED" = "true" ] || return 1
  return 0
}

# ----------------------------------------------------------------- recuperación (runbook §5)
INCIDENT_TS=""; INCIDENT_ATTEMPTS=0; BACKOFF_UNTIL=0; ORDER_NOT_READY_SINCE=0

backoff_delay() { # reintento n cuesta min(2^(n-1)*base, 300)
  local n="$1" d; d=$(( (1 << (n - 1)) * BACKOFF_BASE )); [ "$d" -gt 300 ] && d=300; echo "$d"
}

recovery_attempt() { # una acción de recuperación por ciclo. → 0 = recheck OK
  local td bp focus shot
  INCIDENT_ATTEMPTS=$((INCIDENT_ATTEMPTS + 1))
  if [ "$INCIDENT_ATTEMPTS" -gt "$MAX_RECOVER" ]; then
    C_RECO_FAIL=$((C_RECO_FAIL + 1))
    escalate "MAX_RECOVERY_EXHAUSTED (${MAX_RECOVER} intentos, incidente $(iso_utc "$INCIDENT_TS"))"
    return 1
  fi
  td=$(get_td_pid); bp=$(get_broker_pid); focus=$(get_focus)
  shot=$(evidence_shot "disconnect_a${INCIDENT_ATTEMPTS}")

  # Rama 1 — proceso TradeDraw muerto (runbook §5.2): am start + botón propio por dump FRESCO (uiautomator SÍ legible en UI propia)
  if [ -z "$td" ]; then
    emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "TradeDraw sin proceso (pidof vacío); am start + localización de btn_headless en dump fresco de la UI propia (jamás coordenada antigua)" \
      "{\"watchdog\":\"${STATE}\"}" '{"watchdog":"RECOVERING","action":"am_start_td"}' \
      "{\"attempt\":${INCIDENT_ATTEMPTS},\"screencap\":\"$(jesc "$shot")\"}"
    adb_sh am start -n "${PKG_TD}/.MainActivity" > /dev/null 2>&1
    local coords
    if coords=$(find_btn_headless); then
      adb_sh input tap $coords
      emit_event "RECOVERY_TAP" "UNVERIFIED" "Tap ÚNICO en btn_headless (resource-id localizado en dump fresco; coordenadas ${coords})" \
        '{"watchdog":"RECOVERING"}' '{"watchdog":"RECOVERING","tapped":"btn_headless"}' \
        "{\"attempt\":${INCIDENT_ATTEMPTS},\"coords\":\"${coords}\"}"
      if full_recheck; then return 0; fi
    else
      emit_event "RECOVERY_FAIL" "UNVERIFIED" "btn_headless no localizable en dump (UI propia no legible o layout distinto); NO se tapa a ciego" \
        '{"watchdog":"RECOVERING"}' '{"watchdog":"RECOVERING","action":"btn_not_found"}' "{\"attempt\":${INCIDENT_ATTEMPTS}}"
    fi
    return 1
  fi

  # Rama 2 — Binomo muerto (runbook §5.3): monkey relaunch (no es tap), backoff en el bucle
  if [ -z "$bp" ]; then
    emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "Binomo sin proceso (pidof vacío); relanzamiento via monkey LAUNCHER (runbook §5.3)" \
      "{\"watchdog\":\"${STATE}\"}" '{"watchdog":"RECOVERING","action":"monkey_broker"}' \
      "{\"attempt\":${INCIDENT_ATTEMPTS},\"screencap\":\"$(jesc "$shot")\"}"
    adb_sh monkey -p "$PKG_BROKER" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    if full_recheck; then return 0; fi
    return 1
  fi

  # Rama 3 — bridge colgado con procesos vivos (runbook §5.1): esperar 1 ciclo; si persiste, ESCALATE (kill suave = destructivo, requiere OK del coordinador)
  if ! fetch_status; then
    if [ "$INCIDENT_ATTEMPTS" -ge 2 ]; then
      escalate "BRIDGE_HUNG (procesos vivos, bridge no responde tras ${INCIDENT_ATTEMPTS} ciclos; recuperación destructiva requiere decisión del coordinador)"
    else
      emit_event "RECOVERY_ATTEMPT" "UNVERIFIED" "Solo bridge caído (pidof vivo + focus OK): esperar 1 ciclo antes de escalar (runbook §5.1); sin acciones destructivas" \
        "{\"watchdog\":\"${STATE}\"}" '{"watchdog":"RECOVERING","action":"observe_bridge"}' \
        "{\"attempt\":${INCIDENT_ATTEMPTS},\"screencap\":\"$(jesc "$shot")\"}"
    fi
    return 1
  fi
  read_status_fields

  # Rama 4 — Binomo vivo pero feed/ventana rotas: diálogo de recuperación (runbook §4)
  # F3: screencap-PRIMERO — la evidencia debe existir en disco ANTES de cualquier
  # decisión; uiautomator dump queda como best-effort SOLO para clasificar con la
  # whitelist estricta de classify_dialogue: sin texto legible → UNKNOWN → ESCALATE
  # (jamás tap). El dump NO decide "reintentar" por sí solo: un TAP exige etiqueta en
  # whitelist + bounds únicos + paquete correcto + screencap de evidencia no vacío.
  local cls xml
  if [ ! -s "$shot" ]; then
    escalate "F3 screencap-primero: evidencia vacía/no disponible (${shot}); SIN evidencia NO hay decisión ni tap (runbook §4: PROHIBIDO tap a ciego)"
    return 1
  fi
  xml=$(ui_dump)   # best-effort: Binomo puede rechazarlo (anti-automatización, runbook §1)
  cls=$(classify_dialogue "$xml" "$PKG_BROKER")
  case "$cls" in
    TAP\ *)
      local cx cy label
      read -r _ cx cy label <<< "$cls"
      emit_event "RECOVERY_TAP" "UNVERIFIED" "Diálogo de recuperación identificado INEQUÍVOCAMENTE (etiqueta '${label}', bounds únicos, paquete Binomo; F3 screencap-primero: ${shot}); tap ÚNICO en (${cx},${cy}). Jamás a ciego." \
        '{"watchdog":"RECOVERING"}' '{"watchdog":"RECOVERING","tapped":"dialogue"}' \
        "{\"attempt\":${INCIDENT_ATTEMPTS},\"x\":${cx},\"y\":${cy},\"label\":\"$(jesc "$label")\",\"screencap\":\"$(jesc "$shot")\"}"
      adb_sh input tap "$cx" "$cy"
      if full_recheck; then return 0; fi
      return 1
      ;;
    *)
      escalate "DIALOGO_DESCONOCIDO_EN_BINOMO (${cls}; F3 screencap-primero: ${shot}; dump best-effort; runbook §4: PROHIBIDO tap)"
      return 1
      ;;
  esac
}

# ----------------------------------------------------------------- estado json (heartbeat)
write_state() {
  local now age tmp
  now=$(now_ms); age=$(( (now - STATE_SINCE) / 1000 ))
  tmp="${STATE_FILE}.tmp"
  printf '{"watchdog":"r30_watchdog","timestamp_utc":"%s","session_id":"%s","cycle":%s,"state":"%s","state_since_utc":"%s","state_age_s":%s,"transport":"%s","counters":{"reconexiones_ok":%s,"reconexiones_fail":%s,"tiempo_desconectado_acum_s":%s,"ciclos":%s},"escalate":%s,"escalate_reason":"%s","incident":{"attempts":%s,"max":%s},"battery":{"level":%s,"powered":%s},"last_status":{"mode":"%s","isDemoAccount":"%s","orderState":"%s","lastTickAgeMs":%s,"connected":"%s","hasPendingTrade":"%s","balance":%s,"versionCode":%s},"pids":{"td":"%s","broker":"%s"}}\n' \
    "$(iso_utc "$now")" "$WD_SESSION_ID" "$C_CYCLES" "$STATE" "$(iso_utc "$STATE_SINCE")" "$age" "${TRANSPORT_USED:-none}" \
    "$C_RECO_OK" "$C_RECO_FAIL" "$C_DISCONNECT_S" "$C_CYCLES" \
    "$( [ "$ESCALATED" = "1" ] && echo true || echo false )" "$(jesc "$ESCALATE_REASON")" \
    "$INCIDENT_ATTEMPTS" "$MAX_RECOVER" "${BATT_LEVEL:--1}" "${BATT_POWERED:-0}" \
    "${S_MODE:-}" "${S_DEMO:-}" "${S_ORDER:-}" "${S_TICK:--1}" "${S_CONNECTED:-}" "${S_PENDING:-}" "${S_BALANCE:-null}" "${S_VCODE:-null}" \
    "${TD_PID:-}" "${BROKER_PID:-}" > "$tmp"
  mv -f "$tmp" "$STATE_FILE"
}

# ----------------------------------------------------------------- señales
on_exit() { write_state; echo "r30_watchdog: salida en estado $STATE (ciclos=$C_CYCLES)" >&2; exit 0; }
trap on_exit INT TERM

# ----------------------------------------------------------------- bucle principal
LAST_HEALTH_EVENT=0; BATT_ALERTED=0; DEMO_ALERTED=0; BAD_STREAK=0; DEGR_STREAK=0
PREV_SLEEP=0   # F4: dwell — intervalo real transcurrido (sleep) que precede a este ciclo
TRANSPORT_USED="none"; BATT_LEVEL=-1; BATT_POWERED=0
# Campos de /status inicializados VACÍOS: con set -u, un bridge caído en el
# ciclo 1 (read_status_fields nunca llamado) no debe abortar el daemon
# (bug ronda 4 corregido: 'S_TICK: unbound variable' en el bloque de señales).
S_MODE=""; S_DEMO=""; S_ORDER=""; S_ORDER_OK=""; S_TICK=""; S_CONNECTED=""; S_PENDING=""; S_BALANCE=""
S_VCODE=""; S_VNAME=""; APP_VERSION="unknown"
TD_PID=""; BROKER_PID=""; FOCUS=""

echo "r30_watchdog: arranque (interval=${INTERVAL}s serial=${SERIAL} allow_disarm=${ALLOW_DISARM} evidence=${EVID_DIR})" >&2

while :; do
  C_CYCLES=$((C_CYCLES + 1))
  CYCLE_START=$(now_ms)

  # ---- (a) bridge /status
  BRIDGE_OK=0
  if fetch_status; then
    read_status_fields
    if [ "$(jstr '"app"' "$STATUS_BODY")" = "TradeDraw" ]; then BRIDGE_OK=1; fi
  fi
  if [ "$BRIDGE_OK" = "1" ]; then
    TD_PID=$(get_td_pid); BROKER_PID=$(get_broker_pid)
    FOCUS=$(get_focus)
    read -r BATT_LEVEL BATT_POWERED <<< "$(get_battery)"
  else
    TD_PID=$(get_td_pid 2>/dev/null || true); BROKER_PID=$(get_broker_pid 2>/dev/null || true)
    FOCUS=""; read -r BATT_LEVEL BATT_POWERED <<< "$(get_battery)" || true
  fi

  # ---- señales de salud
  PROCS_OK=1; { [ -n "$TD_PID" ] && [ -n "$BROKER_PID" ]; } || PROCS_OK=0
  FOCUS_OK=1; printf '%s' "$FOCUS" | grep -q "$PKG_BROKER" || FOCUS_OK=0
  TICK_FRESH=1
  { [ -z "$S_TICK" ] || [ "$S_TICK" -lt 0 ] 2>/dev/null || [ "$S_TICK" -gt "$TICK_MAX_AGE_MS" ] 2>/dev/null; } && TICK_FRESH=0
  [ "$S_CONNECTED" = "true" ] || TICK_FRESH=0
  DEMO_OK=1; [ "$S_DEMO" = "true" ] || DEMO_OK=0
  # F1: gate de salud sobre health.state (fuente real: HealthSupervisor State enum;
  # NO reutiliza S_ORDER/"orderState" — ese sigue siendo telemetría para last_status JSON).
  ORDER_READY=1; [ "$S_ORDER_OK" = "READY" ] || ORDER_READY=0

  now_s=$(( $(now_ms) / 1000 ))
  if [ "$ORDER_READY" = "1" ]; then ORDER_NOT_READY_SINCE=0
  elif [ "$ORDER_NOT_READY_SINCE" = "0" ]; then ORDER_NOT_READY_SINCE=$now_s; fi
  ORDER_LATE=0
  { [ "$ORDER_READY" = "0" ] && [ "$ORDER_NOT_READY_SINCE" != "0" ] && [ $(( now_s - ORDER_NOT_READY_SINCE )) -gt 60 ]; } && ORDER_LATE=1
  BALANCE_OK=1; { [ -z "$S_BALANCE" ] || [ "$S_BALANCE" = "null" ]; } && BALANCE_OK=0

  # ---- (b) máquina de estados
  CANDIDATE="CONNECTED"
  { [ "$BRIDGE_OK" = "1" ] && [ "$PROCS_OK" = "1" ]; } || CANDIDATE="DISCONNECTED"
  if [ "$CANDIDATE" = "CONNECTED" ]; then
    { [ "$TICK_FRESH" = "0" ] || [ "$ORDER_LATE" = "1" ] || [ "$BALANCE_OK" = "0" ]; } && CANDIDATE="DEGRADED"
    [ "$FOCUS_OK" = "1" ] || CANDIDATE="DEGRADED"
  fi

  if [ "$CANDIDATE" = "DISCONNECTED" ]; then
    BAD_STREAK=$((BAD_STREAK + 1))
    { [ "$BAD_STREAK" -ge 2 ] && [ "$STATE" != "DISCONNECTED" ] && [ "$STATE" != "RECOVERING" ]; } && {
      set_state "DISCONNECTED"
      INCIDENT_TS=$(now_ms); INCIDENT_ATTEMPTS=0; BACKOFF_UNTIL=0
      emit_event "WS_DISCONNECT" "UNVERIFIED" \
        "DISCONNECTED tras ${BAD_STREAK} ciclos malos: bridge=$([ "$BRIDGE_OK" = "1" ] && echo ok || echo FALLO) td_pid=${TD_PID:-∅} broker_pid=${BROKER_PID:-∅} focus_ok=${FOCUS_OK} tick_fresh=${TICK_FRESH}. Inicio de incidente; protocolo de recuperación en el próximo ciclo (jamás tap a ciego)." \
        "{\"watchdog\":\"${LAST_PREV_STATE}\"}" '{"watchdog":"DISCONNECTED"}' \
        "{\"bridge_ok\":$([ "$BRIDGE_OK" = "1" ] && echo true || echo false),\"td_pid\":\"${TD_PID:-}\",\"broker_pid\":\"${BROKER_PID:-}\",\"hasPendingTrade\":\"${S_PENDING:-}\",\"no_duplication_watch\":true}"
    }
    DEGR_STREAK=0
  elif [ "$CANDIDATE" = "DEGRADED" ]; then
    DEGR_STREAK=$((DEGR_STREAK + 1))
    BAD_STREAK=0
    { [ "$DEGR_STREAK" -ge 2 ] && [ "$STATE" != "DISCONNECTED" ] && [ "$STATE" != "RECOVERING" ]; } && {
      set_state "DISCONNECTED"
      INCIDENT_TS=$(now_ms); INCIDENT_ATTEMPTS=0; BACKOFF_UNTIL=0
      emit_event "WS_DISCONNECT" "UNVERIFIED" "DEGRADED persistente (${DEGR_STREAK} ciclos) → DISCONNECTED (runbook §3: síntoma persistente). tick_fresh=${TICK_FRESH} order_late=${ORDER_LATE} balance_ok=${BALANCE_OK} focus_ok=${FOCUS_OK}" \
        '{"watchdog":"DEGRADED"}' '{"watchdog":"DISCONNECTED"}' "{\"degraded_cycles\":${DEGR_STREAK},\"no_duplication_watch\":true}"
    }
    { [ "$DEGR_STREAK" -lt 2 ] && [ "$STATE" != "DISCONNECTED" ] && [ "$STATE" != "RECOVERING" ]; } && set_state "DEGRADED"
  else
    BAD_STREAK=0; DEGR_STREAK=0
    if [ "$STATE" = "DISCONNECTED" ] || [ "$STATE" = "RECOVERING" ]; then
      # auto-sanación del sistema (p.ej. bridge volvió solo): registrar y verificar
      emit_event "RECOVERY_OK" "VERIFIED" "Chequeos sanos de nuevo sin acción del watchdog (self-heal); tiempo de desconexión registrado" \
        "{\"watchdog\":\"${LAST_PREV_STATE}\"}" '{"watchdog":"VERIFYING"}' \
        "{\"action\":\"self_heal\",\"outage_s\":$(( ( $(now_ms) - ${INCIDENT_TS:-$(now_ms)} ) / 1000 ))}"
      C_RECO_OK=$((C_RECO_OK + 1))
      [ -n "$INCIDENT_TS" ] && C_DISCONNECT_S=$(( C_DISCONNECT_S + ( $(now_ms) - INCIDENT_TS ) / 1000 ))
      set_state "VERIFYING"
    elif [ "$STATE" = "VERIFYING" ]; then
      if [ "$TICK_FRESH" = "1" ] && [ "$DEMO_OK" = "1" ] && [ "$FOCUS_OK" = "1" ] && [ "$ORDER_READY" = "1" ]; then
        set_state "CONNECTED"
        emit_event "HEALTH_CHECK" "VERIFIED" "VERIFYING→CONNECTED: DEMO re-verificado, ventana activa Binomo, WS fresco, orderState CLEAN (health.state=READY; guard DEMO del runbook §3/§6 completo)" \
          '{"watchdog":"VERIFYING"}' '{"watchdog":"CONNECTED"}' \
          "{\"reconexiones_ok\":${C_RECO_OK},\"tick_ms\":${S_TICK:--1},\"mode\":\"${S_MODE:-}\"}"
        [ "$ESCALATED" = "1" ] && { ESCALATED=0; ESCALATE_REASON=""; emit_event "HEALTH_CHECK" "VERIFIED" "ESCALATE limpiado por auto-recuperación completa a CONNECTED (los eventos ERROR previos quedan en el JSONL para auditoría)" '{"escalate":true}' '{"escalate":false}' '{}'; }
      else
        set_state "DISCONNECTED"; INCIDENT_TS=$(now_ms); INCIDENT_ATTEMPTS=0
        emit_event "WS_DISCONNECT" "UNVERIFIED" "VERIFYING falló la re-verificación completa → DISCONNECTED de nuevo" \
          '{"watchdog":"VERIFYING"}' '{"watchdog":"DISCONNECTED"}' '{}'
      fi
    elif [ "$STATE" != "CONNECTED" ]; then
      set_state "CONNECTED"
    fi
  fi

  # ---- (c) recuperación (una acción por ciclo, con presupuesto y backoff)
  if [ "$STATE" = "DISCONNECTED" ]; then
    now_s=$(( $(now_ms) / 1000 ))
    if [ "$ESCALATED" = "1" ]; then
      : # ESCALATE: NO toca nada más; solo observa (encargo Ronda 4c)
    elif [ "$now_s" -lt "$BACKOFF_UNTIL" ]; then
      : # backoff: esperar antes del siguiente intento
    else
      set_state "RECOVERING"
      if recovery_attempt; then
        C_RECO_OK=$((C_RECO_OK + 1))
        [ -n "$INCIDENT_TS" ] && C_DISCONNECT_S=$(( C_DISCONNECT_S + ( $(now_ms) - INCIDENT_TS ) / 1000 ))
        check_dispatch_recovery
        set_state "VERIFYING"
        emit_event "RECOVERY_OK" "VERIFIED" "Recuperación verificada (contenido, WS fresco, ventana activa, DEMO). Tiempo de desconexión registrado; VERIFYING→CONNECTED en el próximo ciclo si todo se mantiene. SIN re-arm ni re-dispatch (idempotencia §6)." \
          '{"watchdog":"RECOVERING"}' '{"watchdog":"VERIFYING"}' \
          "{\"attempt\":${INCIDENT_ATTEMPTS},\"outage_s\":$(( ( $(now_ms) - ${INCIDENT_TS:-$(now_ms)} ) / 1000 )),\"reconexiones_ok\":${C_RECO_OK}}"
      else
        LOCAL_DELAY=$(backoff_delay "$INCIDENT_ATTEMPTS")
        BACKOFF_UNTIL=$(( $(now_ms) / 1000 + LOCAL_DELAY ))
        [ "$STATE" = "RECOVERING" ] && set_state "DISCONNECTED"
      fi
    fi
  fi

  # ---- guard DEMO (runbook §3/§7: cualquier estado + isDemoAccount:false → desarme + escalar)
  # F2: parse no-vacío requerido — body truncado (soTimeout=3000) da S_DEMO vacío → NO se
  # cuenta como fallo DEMO (falso positivo demo-flip); se marca DEGRADED un ciclo explícito.
  PARSE_OK=1; [ -z "$S_DEMO" ] && PARSE_OK=0
  if [ "$BRIDGE_OK" = "1" ] && [ "$PARSE_OK" = "0" ]; then
    DEGR_STREAK=$((DEGR_STREAK + 1)); BAD_STREAK=0
    [ "$STATE" = "CONNECTED" ] && set_state "DEGRADED"
    emit_event "HEALTH_CHECK" "UNVERIFIED" "F2: parse /status no concluyente (isDemoAccount ausente/vacío en body truncado, soTimeout=3000); guard DEMO OMITIDO este ciclo (no se cuenta fallo DEMO); estado=DEGRADED" \
      "{\"watchdog\":\"${STATE}\"}" "{\"watchdog\":\"DEGRADED\",\"parse\":\"incomplete\"}" \
      "{\"bridge_ok\":true,\"isDemoAccount\":\"$(jesc "${S_DEMO:-}")\",\"parse_degraded\":true,\"no_duplication_watch\":true}"
  fi
  if [ "$BRIDGE_OK" = "1" ] && [ "$DEMO_OK" = "0" ] && [ "$PARSE_OK" = "1" ] && [ "$DEMO_ALERTED" = "0" ]; then
    DEMO_ALERTED=1
    emit_event "ERROR" "FAILED" "isDemoAccount=false detectado (posible cuenta REAL). Escalado inmediato; posible desarme según --allow-disarm (único POST permitido)" \
      '{"isDemoAccount":"true"}' '{"isDemoAccount":"false"}' "{\"mode\":\"${S_MODE:-}\",\"allow_disarm\":$([ "$ALLOW_DISARM" = "1" ] && echo true || echo false)}"
    escalate "DEMO_FLIP isDemoAccount=false"
    if [ "$S_MODE" = "AUTONOMOUS" ]; then
      if [ "$ALLOW_DISARM" = "1" ]; then post_mode_disabled
      else
        printf '{"suggested_action":"POST /mode?name=DISABLED","reason":"DEMO_FLIP","decided_by":"coordinador","ts_utc":"%s"}\n' "$(iso_utc "$(now_ms)")" > "${EVID_DIR}/PAUSE_REQUESTED_$(date -u +%Y%m%dT%H%M%SZ)_demo.json"
      fi
    fi
  fi
  [ "$DEMO_OK" = "1" ] && DEMO_ALERTED=0

  # ---- (d) batería
  if [ "${BATT_LEVEL:--1}" -ge 0 ] 2>/dev/null && [ "$BATT_LEVEL" -lt "$BATTERY_MIN" ] && [ "$BATT_ALERTED" = "0" ]; then
    BATT_ALERTED=1
    emit_event "ERROR" "VERIFIED" "Batería ${BATT_LEVEL}% < umbral ${BATTERY_MIN}% (ESCALATE_BATTERY)" \
      "{\"battery\":{\"level_ge\":\"${BATTERY_MIN}\"}}" "{\"battery\":{\"level\":${BATT_LEVEL}}}" \
      "{\"level\":${BATT_LEVEL},\"powered\":${BATT_POWERED},\"mode\":\"${S_MODE:-}\"}"
    if [ "$S_MODE" = "AUTONOMOUS" ]; then
      if [ "$ALLOW_DISARM" = "1" ]; then
        post_mode_disabled
      else
        # marcador de sugerencia: la PAUSA la decide el coordinador (encargo Ronda 4d)
        printf '{"suggested_action":"POST /mode?name=DISABLED","reason":"BATTERY_LOW","level":%s,"decided_by":"coordinador","ts_utc":"%s"}\n' \
          "$BATT_LEVEL" "$(iso_utc "$(now_ms)")" > "${EVID_DIR}/PAUSE_REQUESTED_$(date -u +%Y%m%dT%H%M%SZ)_battery.json"
        emit_event "ERROR" "UNVERIFIED" "Pausa AUTONOMOUS SUGERIDA vía marcador PAUSE_REQUESTED (sin --allow-disarm el script NO desarma por decisión propia; el coordinador decide)" \
          '{"suggestion":"none"}' '{"suggestion":"pause_requested"}' "{\"level\":${BATT_LEVEL},\"marker\":\"PAUSE_REQUESTED\"}"
      fi
    fi
  fi
  [ "${BATT_LEVEL:--1}" -ge $(( BATTERY_MIN + 5 )) ] 2>/dev/null && BATT_ALERTED=0

  # ---- F4: dwell DISCONNECTED — suma a tiempo_desconectado_acum_s el intervalo REAL
  # transcurrido (el sleep que precedió a este ciclo: INTERVAL o FAST_INTERVAL) en TODOS
  # los ciclos con STATE=DISCONNECTED (antes solo se sumaba al salir: :594/:564 → cortes
  # nunca recuperados no acumulaban). Colocado antes de write_state para que el heartbeat
  # del ciclo que entra a DISCONNECTED ya refleje el dwell.
  if [ "$STATE" = "DISCONNECTED" ]; then
    C_DISCONNECT_S=$(( C_DISCONNECT_S + PREV_SLEEP ))
  fi

  # ---- (f/g) heartbeat + HEALTH_CHECK periódico
  NOW_MS_V=$(now_ms)
  if [ "$STATE" = "CONNECTED" ] && [ $(( NOW_MS_V - LAST_HEALTH_EVENT )) -ge $(( HEALTH_EVENT_S * 1000 )) ]; then
    LAST_HEALTH_EVENT=$NOW_MS_V
    emit_event "HEALTH_CHECK" "VERIFIED" "Ciclo estable: bridge 200, proceso vivo, Binomo en focus, feed fresco, DEMO, orderState CLEAN (health.state=READY)" \
      "{\"watchdog\":\"${STATE}\"}" "{\"watchdog\":\"${STATE}\"}" \
      "{\"cycle\":${C_CYCLES},\"tick_ms\":${S_TICK:--1},\"battery\":${BATT_LEVEL:--1},\"mode\":\"${S_MODE:-}\",\"transport\":\"${TRANSPORT_USED:-none}\"}"
  fi
  write_state

  # ---- fin de ciclo
  [ "$ONCE" = "1" ] && break
  { [ "$MAX_CYCLES" -gt 0 ] 2>/dev/null && [ "$C_CYCLES" -ge "$MAX_CYCLES" ]; } && break
  NEXT_SLEEP="$INTERVAL"
  [ "$S_PENDING" = "true" ] && NEXT_SLEEP="$FAST_INTERVAL"   # aceleración dinámica (runbook §2)
  ELAPSED=$(( ( $(now_ms) - CYCLE_START ) / 1000 ))
  REMAIN=$(( NEXT_SLEEP - ELAPSED ))
  [ "$REMAIN" -gt 0 ] 2>/dev/null && sleep "$REMAIN" || true
  PREV_SLEEP=$NEXT_SLEEP   # F4: intervalo real programado (se consuma sleep o no)
done
on_exit
