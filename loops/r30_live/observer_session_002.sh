#!/data/data/com.termux/files/usr/bin/bash
# observer_session_002.sh v2 (S1-FIX 21:43Z) — poller de alta frecuencia para R30_LIVE_SESSION_002
# FIX: orderState/hasPendingTrade viven bajo risk.* (v1 los leia del nivel superior -> None;None ciego a S1).
# v2 anade risk.effectiveStake al sig (evidencia de stake en cada transicion).
# SOLO LECTURA (curl /status). No toca el dispositivo.
set -u
cd /data/data/com.termux/files/home/Tradedraw
OUT="loops/r30_live/observer_session_002.jsonl"
LOG="loops/r30_live/observer_session_002.log"
PREV_SIG=""; CYCLE=0
echo "{\"observer_start_utc\":\"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",\"session\":\"R30_LIVE_SESSION_002\",\"build\":\"R30-LIVE-FUSION c9e52e9 v22\",\"observer\":\"v2-S1fix-risk-parse\"}" >> "$LOG"

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
    r=d.get('risk',{}) or {}
    h=d.get('health',{}) or {}
    f=d.get('feed',{}) or {}
    print('|'.join(str(x) for x in [
        d.get('mode'), d.get('isDemoAccount'), h.get('state'),
        ','.join(h.get('reasons') or []),
        r.get('orderState'), r.get('hasPendingTrade'), r.get('effectiveStake'),
        d.get('balance'), f.get('lastTickAgeMs'), f.get('connected'),
        d.get('versionCode')
    ]))
except Exception:
    print('PARSE_FAIL')")
    SIG="$PARSED"
  fi
  # Registrar solo en cambio de firma (transicion) o cada 12 ciclos (1/min latido)
  if [ "$SIG" != "$PREV_SIG" ] || [ $((CYCLE % 12)) -eq 0 ]; then
    printf '{"ts_utc":"%s","cycle":%s,"sig":"%s"}\n' "$TS" "$CYCLE" "$(printf '%s' "$SIG" | tr -d '"' | tr '|' ';')" >> "$OUT"
    if [ "$SIG" != "$PREV_SIG" ] && [ -n "$PREV_SIG" ]; then
      echo "{\"transition_utc\":\"$TS\",\"from\":\"$(printf '%s' "$PREV_SIG" | tr -d '"' | tr '|' ';')\",\"to\":\"$(printf '%s' "$SIG" | tr -d '"' | tr '|' ';')\"}" >> "$LOG"
    fi
    PREV_SIG="$SIG"
  fi
  sleep 7
done
