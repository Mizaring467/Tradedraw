# Frontend coordination — ChatGPT / Desktop Commander
User assigned this session frontend, broker selection and agent setup on 2026-11-12.
Worktree: Tradedraw-frontend; branch assistant/frontend-brokers-agent.
Reserved files: MainActivity.kt, AgentConnectionSettings.kt, BrokerCatalog.kt, activity_main.xml, bg_home_card.xml, home_strings.xml, BrokerCatalogTest.kt.
Do not edit TradingEngine, ExecutionPolicy, instrumentation, simulation or prospective evidence here. Instrumentation session owns those files and its pending tests.
The scope guard reports app because it collapses individual paths to module scope. Explicit user division of frontend vs concurrent research is applied with disjoint file ownership. Recheck exact collisions before integration. No installation over another session runtime without checking current activity and test ownership.

## Implemented
- Scrollable startup dashboard with explicit broker availability and persistent selection.
- Binomo linking retained; unavailable brokers cannot launch from home.
- No automatic overlay start when opening the chooser; explicit start retains the requested permission flow.
- Agent settings use the same AIConfig as the chat, mask existing credentials, require fresh credentials on endpoint changes, require HTTPS except loopback, and test an actual model response without following redirects or displaying raw provider errors.
- Existing strategy, risk, calibration and production adapter logic unchanged.

## Validation and delivery
- App unit gate: 359 tests, 0 failures/errors/skips; normal APK assembled.
- Preview gate after strict response-type hardening: 359 tests, 0 failures/errors/skips; APK assembled with independent applicationId com.example.tradedraw.frontend.
- Independent tester reviewed compatibility, contrast, lifecycle, exact file ownership, and connection validation; findings corrected.
- No direct file collisions with instrumentation worktree. Shared module app is known and intentionally partitioned by files per user-assigned frontend scope.
- Device installation attempted and rejected with INSTALL_FAILED_USER_RESTRICTED. Visual/runtime QA remains PENDING, not PASS.
- Preview APK copied to /storage/emulated/0/Download/TradeDraw-Frontend-Preview.apk. Preview uses separate preferences; it does not configure credentials in the original app.
- Default local gateway at 127.0.0.1:20128 did not respond. No active provider credential was verified or installed. User must configure a reachable endpoint/model/key through the form.
- Do not merge or replace instrumentation APK until coordinating its runtime evidence capture.
