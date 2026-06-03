# Nova/Luna Progress Checkpoint

Current app readiness: 40%

## Verified Command Families

- Open app:
  - `open app whatsapp`
  - `launch whatsapp`
  - `start whatsapp`
  - `open whatsapp`
- Tap / click / press:
  - `tap settings`
  - `click settings`
  - `press settings`
  - `tap on settings`
  - `click on settings`
- Scroll / swipe / move:
  - Down aliases: `scroll down`, `swipe down`, `move down`
  - Up aliases: `scroll up`, `swipe up`, `move up`
- Settings:
  - General settings: `open settings`, `launch settings`, `open phone settings`
  - Accessibility settings: `open accessibility settings`, `open nova accessibility settings`
  - Usage access settings: `open usage access settings`, `open usage settings`, `open app usage settings`, `open usage permission`, `open app usage permission`
- Type / write / enter / input text:
  - `type hello`
  - `write hello`
  - `enter hello`
  - `type message hello`
  - `write message hello`
  - `input hello`

## Safety Notes

- Deterministic JVM tests only.
- Mocked `NovaAccessibilityService` and mocked launcher/settings dependencies are used in tests.
- No real screen taps, typing, settings launches, or permission grants happen in tests.
- `flutter_app/` is untouched.
- Usage-access settings remains explicit and safety-aware.

## Verified Test Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon
```

## Checkpoint Notes

- The root Android command brain is the active implementation path.
- Verified command coverage is focused on parser -> intent resolver -> safety gate -> executor routing.
- This checkpoint captures only facts already verified by code and tests.
