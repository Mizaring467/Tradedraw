#!/data/data/com.termux/files/usr/bin/bash
set -u
OUT=/data/data/com.termux/files/home/Tradedraw/loops/r26_validation
ADB="adb -s 192.168.1.196:5555"
end=$(( $(date +%s) + 770 ))
while [ "$(date +%s)" -lt "$end" ]; do
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  sleep 45
done
printf 'T_FINAL=%s\n' "$(date -Iseconds)" > "$OUT/P1_timeline.txt"
curl -s --max-time 5 http://127.0.0.1:8080/status > "$OUT/P1_status_final.json"
curl -s --max-time 5 http://127.0.0.1:8080/mode_log > "$OUT/P1_mode_log.json"
printf 'T_STATUS=%s\n' "$(date -Iseconds)" >> "$OUT/P1_timeline.txt"
echo LISTO_P1
