# Nova/Luna Project Short Summary

## What Nova/Luna Is

Nova/Luna is a phone-first Android assistant that is meant to work local-first and offline-first by default. Nova is the male voice profile, Luna is the female voice profile, and the user can choose which one to hear. The current active implementation in this repo is the native Android app plus a Wear OS scaffold and shared constants module. There is no `flutter_app/` directory in this snapshot.

## Current Architecture

- Voice input runs through `VoiceCommandService` and Android `SpeechRecognizer`.
- `CommandBrain` handles wake-word stripping, stop/cancel, confirmations, and active sessions.
- `BrainService` and `BrainRouter` turn text into a candidate `BrainAction`.
- `BrainActionValidator` and `SafetyGate` block dangerous or malformed output.
- `ActionExecutor` runs safe actions through Android navigation, tapping, typing, settings, and provider orchestrators.
- `TextToSpeechManager` speaks the response locally.
- Room stores command history and DataStore stores preferences.

## Main Completed Features

- Local speech input.
- Local text-to-speech output.
- Nova and Luna voice profile selection.
- Open app commands.
- Go home, go back, recents, and notifications commands.
- Tap, scroll, and type commands.
- Safety blocking for payment, OTP, CAPTCHA, login, banking, password, and checkout.
- Cab, food, and grocery session orchestration.
- Local command history and preference storage.
- Debug smoke hooks for brain, cab, and command validation.
- Wear OS relay scaffold.

## Main Blockers

- Current-location cab pickup is blocked on the latest test phone because location permission is missing.
- Manual cab pickup still depends on provider foreground access and readable destination fields.
- Food provider screens are installed, but search/cart controls were not accessible on the latest test phone.
- Usage access is still missing on the latest test phone.
- Real phone-local Gemma inference is scaffolded but not wired to a backend yet.
- Online shopping, entertainment, and social app flows are still planned.

## Next 10 Steps

1. Improve cab provider foreground detection and destination-field discovery.
2. Make food provider search/cart discovery more resilient.
3. Keep grocery flows green and stable across cancel and follow-up commands.
4. Wire a real local model path for the phone brain.
5. Add stronger read-only screen understanding.
6. Expand persona behavior beyond TTS pitch/rate.
7. Add online shopping comparison and trust checks.
8. Add entertainment and social app control flows.
9. Improve the Wear OS relay and phone/watch UX.
10. Package the assistant with OEM-ready boundaries and documentation.

## Demo Readiness

- The assistant core is demoable for safe navigation and simple commands.
- Grocery is the strongest live domain flow.
- Cab and food are still partial because of real-device provider and permission blockers.
- The app is not yet ready for a public release or OEM distribution.
- The negative safety boundaries are a major strength and should stay green.
