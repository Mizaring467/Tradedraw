@echo off
echo === ADB FORWARD ===
adb forward tcp:8080 tcp:8080
echo === BATERIA ===
adb shell dumpsys battery
echo === CURRENT FOCUS ===
adb shell "dumpsys window | grep mCurrentFocus"
echo === APP STATUS ===
curl.exe -s http://127.0.0.1:8080/status
echo.
