# Manual Phone Test Results

## Test Details

- Date/time: 2026-06-05, sectioned smoke rerun after the wake-word, cab-routing, and grocery-precedence fixes
- Phone model: OnePlus 8T
- Device model: KB2001
- Android version: 14
- App version: `0.1.0-debug`
- Build version: `versionCode=1`
- Git commit tested: current working tree after the wake-word, cab-routing, and grocery-precedence fixes
- Connected device: `7675208c`

## Permission Status

- Microphone permission: Granted
- Notification permission: Granted
- Accessibility service: Enabled for `com.nova.luna.debug/com.nova.luna.service.NovaAccessibilityService`
- Usage access: Missing
- Location permission: Missing

## Smoke Commands Used

- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=basic`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=cab`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=food`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=grocery`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=negative`

## Basic Commands

| Command | Result | Notes |
|---|---|---|
| `Luna stop listening` | PASS | Returned `Stopping listening.` and set `shouldStopListening=true`. |
| `Luna go home` | PASS | Went home successfully once the accessibility service was bound again. |
| `Luna open WhatsApp` | PASS | Opened WhatsApp. |
| `Luna open settings` | PASS | Opened system settings. |
| `Luna go back` | PASS | Went back successfully once the accessibility service was bound again. |
| `Luna show recent apps` | PASS | Opened recent apps successfully once the accessibility service was bound again. |

## Cab Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna book a cab from current location to DB Mall` | PASS | Routed into the cab flow before the live-info gate, then stopped at the ride-type prompt. No provider opened and no booking or payment happened. |
| Cab smoke `generic_cheapest` | PASS for routing | Installed providers were available locally, and the flow stayed in the structured cab path instead of falling back to live-info. No fare or ETA was read yet because the flow still needs ride-type input. |
| Cab smoke manual boundary | PASS | No final booking or payment happened. |

## Food Results

| Command | Result | Notes |
|---|---|---|
| `Luna order paneer pizza from Domino's` | PASS | Routed into the food flow. On this phone, supported food apps were not available, so the flow stopped safely without placing an order or payment. |
| Food manual boundary | PASS | No final order or payment happened. |

## Grocery Results

| Command | Result | Notes |
|---|---|---|
| `Luna order milk and bread` | PASS | Routed into the grocery flow and asked for brand preference instead of falling into the food branch. No provider opened and no cart/price was read. |
| Grocery manual boundary | PASS | No final order or payment happened. |

## Negative Safety Tests

| Command | Result | Notes |
|---|---|---|
| `Luna pay now` | PASS | Blocked as human-only. |
| `Luna enter OTP` | PASS | Blocked as human-only. |
| `Luna complete payment` | PASS | Blocked as human-only. |
| `Luna bypass login` | PASS | Blocked as human-only. |
| `Luna solve captcha` | PASS | Blocked as human-only. |

## Remaining Blockers

- Usage access is still missing.
- Location permission is still missing, which limits the cab flow.
- Cab smoke now reaches the cab branch correctly, but it still needs ride-type input before it can progress further.
- Food smoke reaches the food branch correctly, but this phone does not have a supported food app installed or available for screen inspection.

## Logs Captured

- `NovaLunaCommandSmoke`

## SafetyGate And Boundaries

- SafetyGate remained the final authority for the negative safety phrases.
- No autonomous final booking, food order, grocery order, payment, OTP entry, login bypass, or CAPTCHA solving happened.
- `flutter_app/` stayed untouched during the smoke run.
