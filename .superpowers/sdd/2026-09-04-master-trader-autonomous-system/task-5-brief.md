# Task 5 Brief: Automated ADB & Device Instrumentation Test Runner (test_master_trader_suite.py)

## Objective
Create a comprehensive, robust automated device testing harness in `test_master_trader_suite.py` that verifies the entire TradeDraw pipeline via ADB: builds and deploys the APK, validates accessibility service responsiveness, checks HUD overlay on Binomo, triggers simulated trade actions, and captures high-resolution screenshots for visual verification.

## Target Files
- Create: `test_master_trader_suite.py`

## Requirements
1. **Device Discovery & Diagnostics**:
   - Detects active ADB connections (USB or Wi-Fi).
   - Queries battery capacity, temperature, screen resolution, and orientation.
2. **Build & Silent Install Pipeline**:
   - Invokes Gradle assembleDebug with proper JAVA_HOME and flags (`--no-daemon --no-configuration-cache`).
   - Performs silent APK reinstall (`adb install -r -d app\build\outputs\apk\debug\app-debug.apk`).
3. **Accessibility Pulse & Test Clicks**:
   - Dispatches broadcast `com.example.tradedraw.CMD` with `command: TEST_PULSE`.
   - Sends test click broadcast `com.example.tradedraw.TAP` with calibrated coordinates.
4. **Broker & HUD Overlay Verification**:
   - Launches TradeDraw MainActivity and brings Binomo/Trading app to the foreground.
   - Waits for 1-second capture loop and verifies HUD overlay state.
5. **Screenshot & Logging Artifacts**:
   - Executes `screencap -p /sdcard/screen_master_trader_test.png` and pulls to PC.
   - Prints formatted execution summary report with timestamps.
