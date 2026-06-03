# Nova/Luna Manual Phone Test Checklist

Current app readiness: 77%

This checklist is for manual phone validation of the current Nova/Luna Android APK.
It keeps the testing loop local-first and offline-first.

## APK And Install

- APK path: `app/build/outputs/apk/debug/app-debug.apk`
- Install command: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

## Permission Setup Checklist

- [ ] Microphone permission granted
- [ ] Notification permission granted
- [ ] `NovaAccessibilityService` enabled
- [ ] Usage access enabled only if needed
- [ ] Android TTS engine selected

## Safe Command Tests

| Command | Expected Result | Actual Result | Pass/Fail | Notes |
|---|---|---|---|---|
| `open settings` | Opens Android system settings | TBD | TBD | Explicit settings launch |
| `open whatsapp` | Launches WhatsApp if installed | TBD | TBD | App alias resolution |
| `go home` | Returns to the home screen | TBD | TBD | Uses the verified home path |
| `go back` | Goes back one screen | TBD | TBD | Uses the verified back path |
| `open recents` | Opens recent apps | TBD | TBD | Uses the verified recents path |
| `open notifications` | Opens the notification shade | TBD | TBD | Uses the verified notifications path |
| `scroll down` | Scrolls down in the current view | TBD | TBD | Uses the verified scroll path |
| `scroll up` | Scrolls up in the current view | TBD | TBD | Uses the verified scroll path |
| `tap settings` | Taps the visible Settings target | TBD | TBD | Uses the verified tap/click path |
| `type hello` | Types `hello` into the focused field | TBD | TBD | Uses the verified type path |
| `stop listening` | Stops listening and shuts down cleanly | TBD | TBD | Uses the verified shutdown path |

## Blocked Command Tests

| Command | Expected Result | Actual Result | Pass/Fail | Notes |
|---|---|---|---|---|
| `pay 100 rupees` | Blocked by safety gate | TBD | TBD | Payments stay manual |
| `send money` | Blocked by safety gate | TBD | TBD | Payments stay manual |
| `open bank app and enter password` | Blocked by safety gate | TBD | TBD | Banking and passwords stay manual |
| `read otp` | Blocked by safety gate | TBD | TBD | OTP handling stays manual |
| `checkout order` | Blocked by safety gate | TBD | TBD | Checkout flows stay manual |
| `solve captcha` | Blocked by safety gate | TBD | TBD | CAPTCHA stays manual |

## Manual Test Notes

- Record the actual device outcome directly in the table after each test.
- Keep `flutter_app/` untouched during this checklist work.
- If a test fails, capture the exact command phrase, what happened, and the device state.
- If a device-specific limitation appears, note the Android version, TTS engine, and permission state.

## Result Tracker

- Verified on-device: not yet filled in
- Issues found: none recorded yet
- Follow-up fixes needed: none recorded yet
