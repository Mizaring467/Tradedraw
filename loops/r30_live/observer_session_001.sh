#!/data/data/com.termux/files/usr/bin/bash
# observer_session_001.sh — poller de alta frecuencia para R30_LIVE_SESSION_001
# Captura transiciones de estado del engine a JSONL (evidencia por operación).
# SOLO LECTURA (curl /status). No toca el dispositivo.
set -u
cd /data/data/com.termux/files/home/Tradedraw
OUT="loops/r30_live/observer_session_001.jsonl"
LOG="loops/r30_live/observer_session_001.log"
PREV_SIG=""; CYCLE=0
echo "{\"observer_start_utc\":\"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",\"session\":\"R30_LIVE_SESSION_001\",\"build\":\"R30-LIVE-FUSION c9e52e9 v22\"}" >> "$LOG"

while true; do
  CYCLE=$((CYCLE+1))
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
  BODY=$(curl -s -m 3 http://127.0.0.1:18080/status 2>/dev/null)
  if [ -z "$BODY" ]; then
    SIG="HTTP_FAIL"
  else
    PARSED=$(printf '%s' "$BODY" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    h=d.get('health',{}) or {}
    f=d.get('feed',{}) or {}
    print('|'.join(str(x) for x in [
        d.get('mode'), d.get('isDemoAccount'), h.get('state'),
        ','.join(h.get('reasons') or []),
        d.get('orderState'), d.get('hasPendingTrade'),
        d.get('balance'), f.get('lastTickAgeMs'), f.get('connected'),
        d.get('versionCode')
    ]))
except Exception:
    print('PARSE_FAIL')")
    SIG="$PARSED"
  fi
  # Registrar solo en cambio de firma (transición) o cada 12 ciclos (1/min latido)
  if [ "$SIG" != "$PREV_SIG" ] || [ $((CYCLE % 12)) -eq 0 ]; then
    printf '{"ts_utc":"%s","cycle":%s,"sig":"%s"}\n' "$TS" "$CYCLE" "$(printf '%s' "$SIG" | tr -d '"' | tr '|' ';')" >> "$OUT"
    if [ "$SIG" != "$PREV_SIG" ] && [ -n "$PREV_SIG" ]; then
      echo "{\"transition_utc\":\"$TS\",\"from\":\"$(printf '%s' "$PREV_SIG" | tr -d '"' | tr '|' ';')\",\"to\":\"$(printf '%s' "$SIG" | tr -d '"' | tr '|' ';')\"}" >> "$LOG"
    fi
    PREV_SIG="$SIG"
  fi
  sleep 7
done
