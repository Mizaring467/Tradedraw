#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# loops/r31_live/observer_v3.sh — poller de alta frecuencia R31 (7s)
#
# Evolución de loops/r30_live/observer_session_002.sh (v2). SOLO LECTURA del
# bridge (GET /status). No toca el dispositivo.
#
# Mejoras v3:
#   - JSONL append-only con rotación de firma (tamaño).
#   - Parse completo: risk.orderState, risk.hasPendingTrade, risk.effectiveStake,
#     risk.totalWins/totalLosses, health.state/reasons, feed.lastTickAgeMs/
#     connected, engine.signalsGenerated/evaluatedCount, balance, mode,
#     isDemoAccount, versionCode.
#   - trade_transition: cuando orderState sale de NINGUNA → objeto enriquecido
#     {ts, from_sig, to_sig, stake, balance}.
#   - trade_settled: cuando orderState vuelve a NINGUNA con W/L cambiado →
#     {result, pnl_approx = balance_post - balance_pre}.
#
# Entorno: OUT/LOG/ROTATE_MB sobreescribibles; URL por R31_STATUS_URL.
# ============================================================================
set -u

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${R31_OBSERVER_OUT:-${DIR}/observer_v3.jsonl}"
LOG="${R31_OBSERVER_LOG:-${DIR}/observer_v3.log}"
ROTATE_BYTES="${R31_ROTATE_MB:-20}000000"   # ~20 MB por defecto
URL="${R31_STATUS_URL:-http://127.0.0.1:18080/status}"
POLL_S="${R31_POLL_S:-7}"

rotate_if_needed() {
  if [ -f "$OUT" ]; then
    local sz
    sz=$(wc -c < "$OUT" 2>/dev/null || echo 0)
    if [ "$sz" -ge "$ROTATE_BYTES" ] 2>/dev/null; then
      local stamp; stamp=$(date -u +%Y%m%dT%H%M%SZ)
      mv -f "$OUT" "${OUT}.${stamp}"
      echo "{\"rotation_utc\":\"${stamp}\"}" >> "$LOG"
    fi
  fi
}

PREV_SIG=""; PREV_ORDER="NINGUNA"; PREV_W=""; PREV_L=""
PREV_BALANCE=""; LAST_BALANCE_BEFORE=""
CYCLE=0

echo "{\"observer_start_utc\":\"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",\"observer\":\"v3\",\"url\":\"${URL}\",\"poll_s\":${POLL_S}}" >> "$LOG"

while true; do
  CYCLE=$((CYCLE + 1))
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
  BODY=$(curl --noproxy '*' -s -m 3 "${URL}" 2>/dev/null)

  if [ -z "$BODY" ]; then
    SIG="HTTP_FAIL"
    ORDER=""; PENDING=""; STAKE=""; W=""; L=""; BAL=""
  else
    PARSED=$(printf '%s' "$BODY" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    r=d.get('risk') or {}
    h=d.get('health') or {}
    f=d.get('feed') or {}
    e=d.get('engine') or {}
    print('|'.join(str(x) for x in [
        d.get('mode'), d.get('isDemoAccount'), h.get('state'),
        ','.join(h.get('reasons') or []),
        r.get('orderState'), r.get('hasPendingTrade'), r.get('effectiveStake'),
        r.get('totalWins'), r.get('totalLosses'),
        d.get('balance'), f.get('lastTickAgeMs'), f.get('connected'),
        e.get('signalsGenerated'), e.get('evaluatedCount'),
        d.get('versionCode')
    ]))
except Exception:
    print('PARSE_FAIL')")
    SIG="$PARSED"
    ORDER=$(printf '%s' "$SIG"  | awk -F'|' '{print $5}')
    PENDING=$(printf '%s' "$SIG" | awk -F'|' '{print $6}')
    STAKE=$(printf '%s' "$SIG"  | awk -F'|' '{print $7}')
    W=$(printf '%s' "$SIG"      | awk -F'|' '{print $8}')
    L=$(printf '%s' "$SIG"      | awk -F'|' '{print $9}')
    BAL=$(printf '%s' "$SIG"    | awk -F'|' '{print $10}')
    [ "$SIG" = "PARSE_FAIL" ] && { ORDER=""; PENDING=""; STAKE=""; W=""; L=""; BAL=""; }
  fi

  JQ() { printf '%s' "$1" | tr -d '"' ; }

  # --- detección de transición de operación (SOLO con ordenes conocidas)
  if [ -n "$ORDER" ] && [ -n "$PREV_ORDER" ] && [ "$ORDER" != "$PREV_ORDER" ]; then
    if [ "$PREV_ORDER" = "NINGUNA" ] && [ "$ORDER" != "NINGUNA" ]; then
      # salida de NINGUNA → trade_transition enriquecido
      printf '{"ts_utc":"%s","type":"trade_transition","cycle":%s,"from_sig":"%s","to_sig":"%s","orderState_from":"%s","orderState_to":"%s","stake":"%s","balance":"%s","hasPendingTrade":"%s"}\n' \
        "$TS" "$CYCLE" "$(JQ "$PREV_SIG")" "$(JQ "$SIG")" "$PREV_ORDER" "$ORDER" "${STAKE:-}" "${BAL:-${PREV_BALANCE:-}}" "${PENDING:-}" >> "$OUT"
      LAST_BALANCE_BEFORE="$BAL"
    elif [ "$PREV_ORDER" != "NINGUNA" ] && [ "$ORDER" = "NINGUNA" ]; then
      # regreso a NINGUNA → settle si W/L cambió
      RESULT="UNKNOWN"
      if [ -n "$W" ] && [ -n "$L" ] && [ -n "$PREV_W" ] && [ -n "$PREV_L" ]; then
        if [ "$W" != "$PREV_W" ]; then RESULT="WIN"
        elif [ "$L" != "$PREV_L" ]; then RESULT="LOSS"
        else RESULT="TIE_OR_UNKNOWN"; fi
      fi
      PNL=""
      if [ -n "$BAL" ] && [ -n "$LAST_BALANCE_BEFORE" ]; then
        PNL=$(python3 -c "print(round((${BAL:-0}) - (${LAST_BALANCE_BEFORE:-0}), 2))" 2>/dev/null || echo "")
      fi
      printf '{"ts_utc":"%s","type":"trade_settled","cycle":%s,"from_sig":"%s","to_sig":"%s","result":"%s","pnl_approx":%s,"balance_post":"%s","balance_pre":"%s","totalWins":"%s","totalLosses":"%s"}\n' \
        "$TS" "$CYCLE" "$(JQ "$PREV_SIG")" "$(JQ "$SIG")" "$RESULT" "${PNL:-null}" "${BAL:-}" "${LAST_BALANCE_BEFORE:-}" "${W:-}" "${L:-}" >> "$OUT"
      LAST_BALANCE_BEFORE="$BAL"
    fi
  fi

  # --- latido: cada cambio de firma o cada 12 ciclos (~84s)
  if [ "$SIG" != "$PREV_SIG" ] || [ $((CYCLE % 12)) -eq 0 ]; then
    rotate_if_needed
    printf '{"ts_utc":"%s","cycle":%s,"sig":"%s"}\n' "$TS" "$CYCLE" "$(JQ "$SIG")" >> "$OUT"
  fi

  PREV_SIG="$SIG"; PREV_ORDER="$ORDER"; PREV_W="$W"; PREV_L="$L"; PREV_BALANCE="$BAL"
  sleep "$POLL_S"
done
