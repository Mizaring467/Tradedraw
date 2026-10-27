#!/data/data/com.termux/files/usr/bin/bash
# Watchdog del transporte ADB para ventanas de validacion R24/R25.
#
# POR QUE EXISTE: la causa raiz de las ventanas ciegas W01 y W02 no fue la app ni el bridge,
# sino la caida del reenvio `adb forward tcp:8080 tcp:8080`. El bridge del telefono sigue
# sirviendo en loopback; si el reenvio desaparece (reconexion Wi-Fi de adbd, reinicio del
# servidor adb, caida momentanea del enlace), el host deja de ver 127.0.0.1:8080 y el monitor
# marca `status=null` hasta el cierre: ventana ciega.
#
# Este proceso NO toca la app, NO cambia el modo del motor y NO altera el preregistro: solo
# repone el transporte. Se ejecuta en paralelo al monitor, durante la ventana.
#
# Uso:
#   SERIAL=192.168.1.196:5555 scripts/validation/adb_forward_watchdog.sh [intervalo_seg] [log]
#
# Evidencia de cada reposicion queda en el log indicado (por defecto ~/forward_watchdog.log).

set -u

SERIAL="${SERIAL:-192.168.1.196:5555}"
INTERVAL="${1:-5}"
LOG="${2:-$HOME/forward_watchdog.log}"
LOCAL_PORT="${LOCAL_PORT:-8080}"
REMOTE_PORT="${REMOTE_PORT:-8080}"

FWD_SPEC="tcp:${LOCAL_PORT} tcp:${REMOTE_PORT}"

echo "$(date '+%F %T') watchdog inicio serial=$SERIAL intervalo=${INTERVAL}s spec=$FWD_SPEC" | tee -a "$LOG"

while true; do
  state="$(adb -s "$SERIAL" get-state 2>/dev/null | tr -d '\r')"
  if [ "$state" != "device" ]; then
    echo "$(date '+%F %T') ADB estado=$state -> reconectando" >> "$LOG"
    adb connect "$SERIAL" >/dev/null 2>&1
    sleep 1
  fi

  if ! adb -s "$SERIAL" forward --list 2>/dev/null | grep -q "$FWD_SPEC"; then
    if adb -s "$SERIAL" forward "tcp:${LOCAL_PORT}" "tcp:${REMOTE_PORT}" >/dev/null 2>&1; then
      echo "$(date '+%F %T') REPUESTO forward $FWD_SPEC" | tee -a "$LOG"
    else
      echo "$(date '+%F %T') FALLO al reponer forward $FWD_SPEC" >> "$LOG"
    fi
  fi

  sleep "$INTERVAL"
done
