# Work and Process Report

## Development Phases

1. Phone app skeleton
2. Speech input and local TTS
3. Rule-based command parsing
4. Safety gate
5. Accessibility execution layer
6. Persistence and audit logs
7. Wear OS companion scaffold
8. Hardening and permission workflows
9. Optional local AI upgrade path

## Week-by-Week Plan

### Week 1

- Open the project in Android Studio
- Sync Gradle
- Build a debug APK
- Install on a physical phone
- Confirm permissions and foreground service behavior

### Week 2

- Validate open app commands
- Validate go home/back/recents
- Validate tap and scroll commands on safe test apps
- Confirm stop/cancel always wins

### Week 3

- Add custom rule persistence
- Add notification parsing improvements
- Add better command feedback messages

### Week 4

- Improve watch relay
- Add command history UI
- Add audit log browsing

## Build Steps

1. Sync the project in Android Studio.
2. Install SDK 34 if prompted.
3. Build the debug APK.
4. Install on a test device.
5. Enable accessibility manually.
6. Grant microphone and notification permissions.

## Testing Checklist

- App opens without crashes
- Voice profile selection persists
- Start Listening launches a foreground notification
- Stop Listening stops the service
- Open app command launches a safe installed app
- Navigation commands trigger global actions
- Tap and scroll work only when a compatible node is visible
- Blocked payment commands are rejected
- Notification reading returns a setup message when permissions are missing

## Manual Permission Setup Checklist

- Grant `RECORD_AUDIO`
- Grant `POST_NOTIFICATIONS` on Android 13+
- Enable `NovaAccessibilityService`
- Grant usage access
- Confirm the app remains visible in the foreground notification tray while listening

## Debug Checklist

- Check `Logcat` for `NovaLuna`
- Verify the accessibility service is connected
- Confirm `rootInActiveWindow` is not null on the target app
- Confirm the package name is visible to the app launcher
- Check that the stop command is handled before any executor routing

## APK Testing Plan

1. Install debug APK on a physical Android phone.
2. Reboot once to confirm no unsafe auto-start happens by default.
3. Start listening manually.
4. Test a small safe command set:
   - open app
   - go home
   - go back
   - scroll down
   - stop listening
5. Verify the service notification remains visible the entire time the mic is active.

## Safety Testing Plan

- Try blocked words:
  - pay
  - send money
  - order
  - buy
  - checkout
  - bank
  - upi
  - password
  - otp
  - captcha
- Confirm every blocked request returns a clear refusal message.
- Confirm screenshot and settings commands do not silently perform sensitive actions.
- Confirm no automatic checkout or banking path exists.

## Known Android Limitations

- Accessibility behavior differs by OEM and app.
- `FLAG_SECURE` screens cannot be read or bypassed.
- Speech recognition quality depends on device support.
- Some apps intentionally block accessibility interaction.
- Package visibility on Android 11+ can affect app launcher enumeration.

## Next Tasks for a Solo Developer

1. Add a command history screen.
2. Add custom rule creation and editing.
3. Add a more robust phone/watch sync protocol.
4. Add local wake-word options if the device supports them.
5. Add a local intent classifier after the rule engine is stable.

