#!/data/data/com.termux/files/usr/bin/bash
set -u
OUT=/data/data/com.termux/files/home/Tradedraw/loops/r26_validation
ADB="adb -s 192.168.1.196:5555"
: > "$OUT/P2_beats.txt"
for i in 1 2 3 4 5; do
  sleep 120
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  r=$(curl -s --max-time 5 http://127.0.0.1:8080/ping)
  printf 'beat_%s=%s t=%s\n' "$i" "$r" "$(date +%s)" >> "$OUT/P2_beats.txt"
done
sleep 60
$ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
r=$(curl -s --max-time 5 http://127.0.0.1:8080/ping)
printf 'beat_6=%s t=%s\n' "$r" "$(date +%s)" >> "$OUT/P2_beats.txt"
curl -s --max-time 5 http://127.0.0.1:8080/status > "$OUT/P2_status_final.json"
curl -s --max-time 5 http://127.0.0.1:8080/mode_log > "$OUT/P2_mode_log.json"
printf 'T_FINAL=%s\n' "$(date -Iseconds)" >> "$OUT/P2_beats.txt"
echo LISTO_P2
