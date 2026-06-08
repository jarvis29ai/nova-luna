# Final Phone Smoke Test Report

Date: 2026-06-08

## Scope

Real-device smoke test and targeted grocery-flow bug fix for Nova / Luna on Android. The goal was to validate the installed debug build on a physical phone, reproduce the grocery compare failure, fix it, and re-verify the final APK.

## Environment

| Item | Value |
| --- | --- |
| Device model | KB2001 |
| Android version | 14 |
| SDK level | 34 |
| Connected device | `7675208c` |
| App package | `com.nova.luna.debug` |
| APK tested | `C:\Users\cricv\Desktop\nova-luna\app\build\outputs\apk\debug\app-debug.apk` |
| Branch | `main` |
| Push status | Not pushed |

## Build, Test, and Install

| Check | Command | Result |
| --- | --- | --- |
| Unit tests | `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | PASS |
| Debug APK build | `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` | PASS |
| APK install | `adb install -r app\build\outputs\apk\debug\app-debug.apk` | SUCCESS |

## Real Phone Smoke Results

### Passes

| Scenario | Result | Evidence |
| --- | --- | --- |
| Launch app on device | PASS | `am start -W -n com.nova.luna.debug/com.nova.luna.MainActivity` reached the Nova / Luna home screen. |
| Onboarding / home screen | PASS | Home UI showed the voice profile selector, Tap to Speak, listening controls, settings shortcuts, and Demo Mode. |
| Demo command: `Luna open YouTube` | PASS | Validated during the initial real-device smoke run; the assistant launched YouTube and summarized the action as `Opening YouTube.` |
| Demo command: `Luna order pizza` | PASS | Validated during the initial real-device smoke run; the app returned the expected clarification prompt: `Which restaurant should I search for?` |
| Demo command: `Luna compare milk prices` after fix | PASS | Re-verified on the rebuilt APK; the grocery flow no longer failed from idle and prompted: `What quantity do you want for milk prices?` |

### Initial Failure Reproduced

| Scenario | Result | Evidence |
| --- | --- | --- |
| Demo command: `Luna compare milk prices` before fix | FAIL | Popup showed: `I could not complete the grocery flow: no active grocery booking session.` |

## Bug Found and Fixed

| Bug | Root Cause | Fix | Verification |
| --- | --- | --- | --- |
| Grocery compare commands could fail immediately when no grocery session was active. | `ActionExecutor.handleGroceryBookingText(...)` always forwarded compare-style grocery commands into `groceryOrchestrator.handleUserInput(...)`, which required an existing session and returned the `no active grocery booking session` failure when invoked from idle. | Bootstrapped a fresh grocery session for initial grocery-booking commands before handing off to the orchestrator. | The final APK was reinstalled on the device and `Luna compare milk prices` advanced into the grocery flow instead of failing. |

## Files Changed

| File | Change |
| --- | --- |
| `app/src/main/java/com/nova/luna/executor/ActionExecutor.kt` | Added idle-state grocery session bootstrap for compare-style grocery commands. |
| `app/src/test/java/com/nova/luna/executor/ActionExecutorGroceryTest.kt` | Added a regression test covering grocery compare bootstrap from an idle session. |

## Validation Notes

* `git diff --check` reported no formatting errors.
* The repository remained on `main` and was not pushed.
* The smoke run used the physical device directly rather than an emulator.
* Tap-to-speak listening was observed, but there was no injected live speech sample during this pass, so the transcript area still showed the generic recognition fallback. That did not affect the demo-mode smoke coverage.
* A later cleanup pass fixed the communication cancel result mapping in `ActionExecutor` and reverified the cancel path on the phone; the remaining phone smoke matrix is documented separately in `docs/FINAL_REMAINING_PHONE_SMOKE_TEST_REPORT.md`.

## Completion Status

Complete. The grocery compare idle-session failure was reproduced, fixed, regression-tested, rebuilt, reinstalled, and re-verified on the phone. No push was performed.
