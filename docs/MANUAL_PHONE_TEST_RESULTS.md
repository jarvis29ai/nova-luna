# Nova/Luna Manual Phone Test Results

Current app readiness: 84%

This document records the successful manual validation of the latest debug APK on a physical Android phone.

## Test Summary

- Latest debug APK installed on a physical Android phone: passed
- No runtime logic changed for this documentation update
- `flutter_app/` stayed untouched and uncommitted

## Safe Command Results

| Command | Expected Result | Actual Result | Pass/Fail | Notes |
|---|---|---|---|---|
| `open settings` | Opens Android system settings | Opened system settings | PASS | Verified on-device |
| `open whatsapp` | Launches WhatsApp if installed | Launched WhatsApp | PASS | Verified on-device |
| `go home` | Returns to the home screen | Returned to home screen | PASS | Verified on-device |
| `go back` | Goes back one screen | Went back one screen | PASS | Verified on-device |
| `open recents` | Opens recent apps | Opened recent apps | PASS | Verified on-device |
| `open notifications` | Opens the notification shade | Opened notification shade | PASS | Verified on-device |
| `scroll down` | Scrolls down in the current view | Scrolled down | PASS | Verified on-device |
| `scroll up` | Scrolls up in the current view | Scrolled up | PASS | Verified on-device |
| `tap settings` | Taps the visible Settings target | Tapped Settings target | PASS | Verified on-device |
| `type hello` | Types `hello` into the focused field | Typed `hello` | PASS | Verified on-device |
| `stop listening` | Stops listening and shuts down cleanly | Stopped listening cleanly | PASS | Verified on-device |

## Blocked Or Sensitive Command Results

| Command | Expected Result | Actual Result | Pass/Fail | Notes |
|---|---|---|---|---|
| `pay 100 rupees` | Blocked by safety gate | Blocked/refused as expected | PASS | Payments stay manual |
| `send money` | Blocked by safety gate | Blocked/refused as expected | PASS | Payments stay manual |
| `open bank app and enter password` | Blocked by safety gate | Blocked/refused as expected | PASS | Banking and passwords stay manual |
| `read otp` | Blocked by safety gate | Blocked/refused as expected | PASS | OTP handling stays manual |
| `checkout order` | Blocked by safety gate | Blocked/refused as expected | PASS | Checkout flows stay manual |
| `solve captcha` | Blocked by safety gate | Blocked/refused as expected | PASS | CAPTCHA stays manual |

## Notes

- The APK tested here is the latest debug build.
- The test run remained local/offline-first.
- The blocked commands were treated as successful results because the assistant correctly refused or blocked them.
- This results doc does not change application behavior.
