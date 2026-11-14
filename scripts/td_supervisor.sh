#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# td_supervisor.sh — Supervisor del bot TradeDraw (Poco X6 Pro / HyperOS)
#
# Que hace:
#   - Sondea el bridge HTTP del bot ([::1]:8080) cada POLL_S segundos.
#     Cada peticion alimenta el watchdog R26: el modo AUTONOMOUS armado por
#     bridge NO caduca mientras este supervisor viva.
#   - Si el bridge no responde, detecta si el proceso sigue vivo y ejecuta la
#     secuencia de recuperacion verificada el 2026-11-06:
#       1. Toggle del setting de accesibilidad (reaplicar el mismo valor NO
#          fuerza re-bind en HyperOS; quitar y volver a anadir, si).
#       2. Abrir MainActivity y tocar el boton de inicio del servicio
#          (resource-id btn_headless, "Iniciar con WebSocket" desde el
#          candidato R30 v22; OverlayService no es exportable: solo se puede
#          arrancar desde la UI de la app).
#       3. Esperar el puerto 8080.
#       4. Traer Binomo (com.marketly.trading) al frente: la lectura de balance
#          corre en eventos TYPE_WINDOW_STATE_CHANGED cada 5 s.
#       5. Esperar balance fresco (> 0) en /status.
#       6. Si AUTO_REARM=1, re-armar AUTONOMOUS por bridge.
#   - Log con rotacion manual en $LOG.
#
# Uso:
#   td_supervisor.sh           # bucle continuo
#   td_supervisor.sh --once    # un solo ciclo (check o recuperacion)
#
# Variables:
#   POLL_S      segundos entre sondas (defecto 30)
#   AUTO_REARM  1 = re-armar AUTONOMOUS tras revival; 0 = dejar DISABLED
#
# Notas de entorno verificadas (Poco X6 Pro, Android 16, HyperOS V816):
#   - adb real en $PREFIX/bin/adb (servidor 5040, transporte emulator-5554).
#   - El bridge escucha SOLO en IPv6 loopback: curl "http://[::1]:8080/...".
#     IPv4 127.0.0.1 devuelve vacio sin error.
#   - APK debuggable: run-as com.example.tradedraw funciona (kill suave).
#   - HyperOS des-enlaza la accesibilidad cuando el proceso muere, aunque el
#     setting siga listando el servicio (queda en Crashed services).
#   - uiautomator dump falla sobre Binomo ("could not get idle state") pero
#     funciona sobre la UI propia de TradeDraw.
# ============================================================================
set -u

POLL_S="${POLL_S:-30}"
AUTO_REARM="${AUTO_REARM:-1}"
LOG="${LOG:-$HOME/Tradedraw/scripts/td_supervisor.log}"
MAX_LOG_BYTES=$((2 * 1024 * 1024))
LAST_MODE=""   # ultimo modo observado por el propio supervisor (para re-arm fail-closed)

A11Y_IN="com.omkar.voiceflow/.VoiceFlowAccessibilityService:af.shizuku.plus.api/af.shizuku.manager.automation.AICorePlusService:com.example.tradedraw/com.example.tradedraw.AutoTradeAccessibilityService"
A11Y_OUT="com.omkar.voiceflow/.VoiceFlowAccessibilityService:af.shizuku.plus.api/af.shizuku.manager.automation.AICorePlusService"
PKG_TD="com.example.tradedraw"
PKG_BROKER="com.marketly.trading"
STATUS_URL="http://[::1]:8080/status"

log() { echo "[$(date -u +%FT%TZ)] $*" >> "$LOG"; }

log_rotate() {
  [ -f "$LOG" ] || return 0
  SIZE=$(wc -c < "$LOG" 2>/dev/null || echo 0)
  if [ "${SIZE:-0}" -gt "$MAX_LOG_BYTES" ]; then
    tail -c 524288 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
    log "ROTATE: log truncado a 512KB (superaba 2MB)"
  fi
}

# El JSON de /status mezcla claves con y sin espacio tras los dos puntos
# ("app": "TradeDraw" vs "state":"READY"): todos los grep usan [[:space:]]*.
bridge_ok() {
  curl -s --max-time 8 "$STATUS_URL" | grep -qE '"app":[[:space:]]*"TradeDraw"'
}

bridge_field() {  # bridge_field <regex> -> imprime el valor crudo
  curl -s --max-time 8 "$STATUS_URL" | grep -oE "$1" | head -1
}

screen_awake() {
  adb shell dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"
}

recover() {
  log "RECUPERACION: inicio (causa: bridge sin respuesta; modo previo: ${LAST_MODE:-DESCONOCIDO})"
  MODE_AT_RECOVER_START="$LAST_MODE"
  PID=$(adb shell pidof "$PKG_TD" 2>/dev/null | tr -d '\r')
  if [ -n "$PID" ]; then
    log "RECUPERACION: proceso vivo ($PID) pero bridge caido; kill suave via run-as"
    adb shell run-as "$PKG_TD" kill "$PID" 2>/dev/null
    sleep 3
  fi

  # -- Paso 1: toggle de accesibilidad (out -> in) --
  adb shell settings put secure enabled_accessibility_services "$A11Y_OUT" 2>/dev/null
  sleep 3
  adb shell settings put secure enabled_accessibility_services "$A11Y_IN" 2>/dev/null
  adb shell settings put secure accessibility_enabled 1 2>/dev/null
  sleep 6
  if ! adb shell dumpsys accessibility 2>/dev/null | grep -q "Bound services:{Service\[label=TradeDraw"; then
    log "FALLO: accesibilidad no re-enlazada (bound sin TradeDraw)"
    return 1
  fi
  log "RECUPERACION: accesibilidad re-enlazada"

  # -- Paso 2: pantalla despierta (los taps no llegan con pantalla bloqueada) --
  if ! screen_awake; then
    log "ALERTA: pantalla no despierta; no se intenta desbloquear. Reintento en el proximo ciclo."
    return 1
  fi

  # -- Paso 3: abrir la app --
  adb shell am start -n "$PKG_TD/.MainActivity" >/dev/null 2>&1
  sleep 6

  # -- Paso 4: tocar el boton de inicio del servicio (localizar por jerarquia) --
  # R30 (v22): home re-hecha (ScrollView + cards). El boton de arranque del
  # servicio es btn_headless ("Iniciar con WebSocket", home_strings.xml:19).
  # "Abrir herramientas flotantes" (boton, antes "INICIAR OVERLAY") sigue en el
  # layout pero MainActivity lo oculta (visibility=GONE, MainActivity.kt:50).
  # Localizar por resource-id+text; si la jerarquia no es legible -> FAIL-CLOSED
  # (el fallback fijo (610,1738) era del layout antiguo y con el nuevo puede
  # pulsar un control equivocado). Requiere dump fresco verificado en FASE 3.
  adb shell uiautomator dump /sdcard/td_sup.xml >/dev/null 2>&1
  sleep 2
  BOUNDS=$(adb shell cat /sdcard/td_sup.xml 2>/dev/null \
    | tr '<' '\n<' | grep 'resource-id="com.example.tradedraw:id/btn_headless"' \
    | grep -i 'Iniciar con WebSocket' \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  if [ -n "$BOUNDS" ]; then
    COORDS=$(echo "$BOUNDS" | grep -oE '[0-9]+' | tr '\n' ' ')
    set -- $COORDS
    CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
    log "RECUPERACION: boton localizado en ($CX,$CY) desde jerarquia"
  else
    log "FALLO: boton btn_headless no localizado en jerarquia; ABORTO sin tap (fail-closed)"
    return 1
  fi
  adb shell input tap "$CX" "$CY"
  sleep 7

  # -- Paso 5: esperar bridge arriba (max ~30 s) --
  UP=0
  for i in $(seq 1 10); do
    if bridge_ok; then UP=1; break; fi
    sleep 3
  done
  [ "$UP" = "1" ] || { log "FALLO: bridge no subio tras el tap"; return 1; }
  log "RECUPERACION: bridge arriba"

  # -- Paso 6: Binomo al frente para eventos de ventana --
  adb shell monkey -p "$PKG_BROKER" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 8

  # -- Paso 7: esperar balance fresco (max ~40 s; escaneo por eventos cada 5 s) --
  BAL_OK=0
  for i in $(seq 1 8); do
    if bridge_field '"balance":[[:space:]]*[1-9]' >/dev/null; then BAL_OK=1; break; fi
    sleep 5
  done
  if [ "$BAL_OK" = "1" ]; then
    log "RECUPERACION: balance fresco ($(bridge_field '"balance":[[:space:]]*[0-9.]+'))"
  else
    log "ALERTA: bridge arriba pero balance sin leer; el escaneo lo traera al haber eventos"
  fi

  # -- Paso 8: re-armar modo --
  # R30: restauracion fail-closed del estado previo. Solo se re-arma AUTONOMOUS
  # si el propio supervisor LO VIO armado (AUTONOMOUS) justo antes de la caida:
  # nunca se escala desde DISABLED/MANUAL ni desde estado desconocido.
  if [ "$AUTO_REARM" = "1" ] && [ "$MODE_AT_RECOVER_START" = "AUTONOMOUS" ]; then
    RES=$(curl -s --max-time 8 "http://[::1]:8080/mode?name=AUTONOMOUS" 2>/dev/null)
    log "RECUPERACION: re-arm -> ${RES:-SIN_RESPUESTA}"
  elif [ "$AUTO_REARM" = "1" ]; then
    log "RECUPERACION: AUTO_REARM=1 pero modo previo ${MODE_AT_RECOVER_START:-DESCONOCIDO} != AUTONOMOUS; no se re-arma (fail-closed)"
  else
    log "RECUPERACION: AUTO_REARM=0; modo dejado en DISABLED"
  fi
  log "RECUPERACION: fin OK"
}

# ---------------------------------------------------------------- singleton
# Evita instancias duplicadas (daemon manual + script de boot). Con flock:
# si otro supervisor vivo sostiene el candado, esta instancia sale.
LOCK="$HOME/Tradedraw/scripts/td_supervisor.lock"
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "td_supervisor: otra instancia ya esta corriendo (lock: $LOCK)" >&2
  exit 0
fi

# ---------------------------------------------------------------- main loop
log "SUPERVISOR: arranque (pid $$, POLL_S=$POLL_S, AUTO_REARM=$AUTO_REARM)"
while true; do
  log_rotate
  if bridge_ok; then
    MODE=$(bridge_field '"mode":[[:space:]]*"[^"]+"')
    MODE="${MODE#*:}"
    MODE="${MODE//\"/}"
    MODE="${MODE// /}"
    LAST_MODE="$MODE"
    STATE=$(bridge_field '"state":[[:space:]]*"[A-Z]+"' | head -1)
    log "OK ${MODE} ${STATE}"
  else
    recover || true
  fi
  [ "${1:-}" = "--once" ] && break
  sleep "$POLL_S"
done
